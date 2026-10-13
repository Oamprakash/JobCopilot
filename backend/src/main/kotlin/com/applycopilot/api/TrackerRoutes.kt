package com.applycopilot.api

import com.applycopilot.tailoring.InterviewPrep
import com.applycopilot.Deps
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.db.Jobs
import com.applycopilot.db.ManualJobs
import com.applycopilot.db.Stages
import com.applycopilot.db.Tracker
import com.applycopilot.db.TrackerItem
import com.applycopilot.extension.JobUrls
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class TrackerItemDto(
    val jobId: String,
    val title: String,
    val company: String,
    val location: String,
    val applyUrl: String,
    val source: String,
    val closed: Boolean,
    val stage: String,
    val appliedAt: String?,
    val followUpAt: String?,
    val followUpDue: Boolean,
    val notes: String,
    val updatedAt: String,
    val createdAt: String,
    val applicationStatus: String?,
)

@Serializable
data class InterviewPrepResponse(val jobId: String, val generator: String, val prep: InterviewPrep)

@Serializable
data class TrackerResponse(val items: List<TrackerItemDto>, val counts: Map<String, Int>, val dueCount: Int)

@Serializable
data class TrackRequest(val jobId: String, val stage: String = "saved")

@Serializable
data class ManualJobRequest(val title: String, val company: String, val url: String, val location: String = "", val stage: String = "saved")

@Serializable
data class TrackerUpdateRequest(
    val stage: String? = null,
    /** ISO-8601 instant for the next follow-up; use clearFollowUp to remove it. */
    val followUpAt: String? = null,
    val clearFollowUp: Boolean = false,
    val notes: String? = null,
)

private fun TrackerItem.toDto(now: Instant) = TrackerItemDto(
    jobId = jobId.toString(),
    title = title,
    company = company,
    location = location,
    applyUrl = applyUrl,
    source = source,
    closed = closed,
    stage = stage,
    appliedAt = appliedAt?.toString(),
    followUpAt = followUpAt?.toString(),
    followUpDue = followUpAt != null && !followUpAt.isAfter(now) && stage in Stages.open,
    notes = notes,
    updatedAt = updatedAt.toString(),
    createdAt = createdAt.toString(),
    applicationStatus = applicationStatus,
)

private fun jobParam(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")

private fun checkStage(stage: String) {
    if (stage !in Stages.all) throw ApiException(HttpStatusCode.BadRequest, "Unknown stage")
}

/** The application tracker. Mount inside authenticate("session"). */
fun Route.trackerRoutes(deps: Deps) {
    route("/tracker") {
        get {
            val userId = call.userId()
            val now = Instant.now()
            val items = deps.db.tx { c -> Tracker.list(c, userId) }.map { it.toDto(now) }
            call.respond(
                TrackerResponse(
                    items = items,
                    counts = Stages.all.associateWith { s -> items.count { it.stage == s } },
                    dueCount = items.count { it.followUpDue },
                ),
            )
        }

        // Track a job the app already knows (from the feed or a tailored application).
        post {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val r = call.receive<TrackRequest>()
            checkStage(r.stage)
            val jobId = jobParam(r.jobId)
            val item = deps.db.tx { c ->
                Jobs.find(c, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
                if (r.stage == "applied") Tracker.markApplied(c, userId, jobId) else Tracker.add(c, userId, jobId, r.stage)
                if (r.stage == "applied") Events.record(c, userId, EventTypes.MARKED_APPLIED, jobId, Events.data("via" to "web"))
                Tracker.find(c, userId, jobId)!!
            }
            call.respond(item.toDto(Instant.now()))
        }

        // A job from anywhere else (a referral, a careers page), typed in by hand.
        post("/manual") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val r = call.receive<ManualJobRequest>()
            checkStage(r.stage)
            val url = r.url.trim()
            if (JobUrls.normalize(url) == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                throw ApiException(HttpStatusCode.BadRequest, "Enter the job's link, starting with https://")
            }
            val title = r.title.trim().ifEmpty { throw ApiException(HttpStatusCode.BadRequest, "Enter the job title") }
            val company = r.company.trim().ifEmpty { throw ApiException(HttpStatusCode.BadRequest, "Enter the company") }
            val item = deps.db.tx { c ->
                val jobId = ManualJobs.upsert(c, url, title, company, r.location.trim(), "")
                if (r.stage == "applied") Tracker.markApplied(c, userId, jobId) else Tracker.add(c, userId, jobId, r.stage)
                Events.record(c, userId, EventTypes.TRACKER_STAGE, jobId, Events.data("stage" to r.stage, "manual" to true))
                Tracker.find(c, userId, jobId)!!
            }
            call.respond(item.toDto(Instant.now()))
        }

        put("/{jobId}") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val jobId = jobParam(call.parameters["jobId"])
            val r = call.receive<TrackerUpdateRequest>()
            r.stage?.let(::checkStage)
            val followUp = r.followUpAt?.let { runCatching { Instant.parse(it) }.getOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "Follow-up date isn't valid") }
            val notes = r.notes?.take(5000)
            val item = deps.db.tx { c ->
                val before = Tracker.find(c, userId, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Not in your tracker")
                Tracker.update(c, userId, jobId, r.stage, followUp, r.clearFollowUp, notes)
                if (r.stage != null && r.stage != before.stage) {
                    Events.record(c, userId, EventTypes.TRACKER_STAGE, jobId, Events.data("from" to before.stage, "stage" to r.stage))
                }
                Tracker.find(c, userId, jobId)!!
            }
            call.respond(item.toDto(Instant.now()))
        }

        // Interview prep for a job: the saved one, or a new one generated now.
        get("/{jobId}/prep") {
            val userId = call.userId()
            val jobId = jobParam(call.parameters["jobId"])
            val saved = deps.db.tx { c -> deps.interviewPrep.find(c, userId, jobId) } ?: throw ApiException(HttpStatusCode.NotFound, "No interview prep yet")
            call.respond(InterviewPrepResponse(jobId.toString(), saved.second, saved.first))
        }

        post("/{jobId}/prep") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val jobId = jobParam(call.parameters["jobId"])
            val (prep, generator) = deps.interviewPrep.generate(userId, jobId)
            call.respond(InterviewPrepResponse(jobId.toString(), generator, prep))
        }

        delete("/{jobId}") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val jobId = jobParam(call.parameters["jobId"])
            if (!deps.db.tx { c -> Tracker.remove(c, userId, jobId) }) throw ApiException(HttpStatusCode.NotFound, "Not in your tracker")
            call.respond(OkResponse())
        }
    }
}
