package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.Companies
import com.applycopilot.db.Company
import com.applycopilot.db.FeedRow
import com.applycopilot.db.Jobs
import com.applycopilot.db.MatchRun
import com.applycopilot.db.MatchRuns
import com.applycopilot.db.Matches
import com.applycopilot.db.Users
import com.applycopilot.jobs.Ats
import com.applycopilot.jobs.AtsClient
import com.applycopilot.jobs.SyncResult
import com.applycopilot.matching.Suggestion
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Unreviewed matches below this score stay out of the feed. */
private const val MIN_FEED_SCORE = 40
private val REFRESH_COOLDOWN: Duration = Duration.ofMinutes(2)

@Serializable
data class FeedJobDto(
    val jobId: String,
    val title: String,
    val company: String,
    val location: String,
    val remote: Boolean,
    val applyUrl: String,
    val postedAt: String?,
    val score: Int,
    val reasons: List<String>,
    val missingSkills: List<String>,
    val scorer: String,
    val status: String,
    val closed: Boolean,
)

@Serializable
data class MatchRunDto(
    val startedAt: String,
    val finishedAt: String?,
    val considered: Int,
    val passedFilter: Int,
    val scored: Int,
    val llmScored: Int,
    val error: String?,
    val filtered: Map<String, Int> = emptyMap(),
    val suggestions: List<Suggestion> = emptyList(),
)

@Serializable
data class FeedResponse(val jobs: List<FeedJobDto>, val lastRun: MatchRunDto?, val needsRefresh: Boolean)

@Serializable
data class StatusRequest(val status: String)

@Serializable
data class JobDetailDto(
    val jobId: String,
    val title: String,
    val company: String,
    val location: String,
    val remote: Boolean,
    val applyUrl: String,
    val postedAt: String?,
    val description: String,
    val closed: Boolean,
)

@Serializable
data class CompanyDto(
    val id: String,
    val name: String,
    val ats: String,
    val boardToken: String,
    val active: Boolean,
    val lastSyncedAt: String?,
    val lastError: String?,
    val openJobs: Int,
)

@Serializable
data class AddCompanyRequest(val name: String, val ats: String, val boardToken: String)

@Serializable
data class ActiveRequest(val active: Boolean)

@Serializable
data class SyncResultDto(val company: CompanyDto, val fetched: Int, val inserted: Int, val closed: Int, val error: String?)

private fun FeedRow.toDto() = FeedJobDto(
    jobId = job.id.toString(),
    title = job.title,
    company = job.company,
    location = job.location,
    remote = job.remote,
    applyUrl = job.applyUrl,
    postedAt = job.postedAt?.toString(),
    score = score,
    reasons = reasons,
    missingSkills = missingSkills,
    scorer = scorer,
    status = status,
    closed = job.closed,
)

private fun MatchRun.toDto() = MatchRunDto(startedAt.toString(), finishedAt?.toString(), considered, passedFilter, scored, llmScored, error, filtered, suggestions)

private fun Company.toDto() = CompanyDto(id.toString(), name, ats, boardToken, active, lastSyncedAt?.toString(), lastError, openJobs)

private fun SyncResult.toDto() = SyncResultDto(company.toDto(), fetched, inserted, closed, error)

private fun uuidParam(raw: String?, what: String): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "$what not found")

private suspend fun feedResponse(deps: Deps, userId: UUID, status: String): FeedResponse = deps.db.tx { c ->
    val lastRun = MatchRuns.latest(c, userId)
    val changedAt = MatchRuns.inputsChangedAt(c, userId)
    FeedResponse(
        jobs = Matches.feed(c, userId, status, if (status == "new") MIN_FEED_SCORE else 0).map { it.toDto() },
        lastRun = lastRun?.toDto(),
        needsRefresh = changedAt != null && (lastRun == null || lastRun.startedAt.isBefore(changedAt)),
    )
}

/** The daily feed. Mount inside authenticate("session"). */
fun Route.feedRoutes(deps: Deps) {
    get("/feed") {
        val status = call.request.queryParameters["status"] ?: "new"
        if (status !in setOf("new", "saved", "skipped")) throw ApiException(HttpStatusCode.BadRequest, "Unknown status")
        call.respond(feedResponse(deps, call.userId(), status))
    }

    // Rebuilds the feed now. Throttled unless the profile or preferences changed since the last run.
    post("/feed/refresh") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val current = feedResponse(deps, userId, "new")
        val last = current.lastRun?.finishedAt?.let { Instant.parse(it) }
        val recent = last != null && last.isAfter(Instant.now().minus(REFRESH_COOLDOWN))
        if (!current.needsRefresh && recent) {
            call.respond(current)
            return@post
        }
        deps.matching.refresh(userId) ?: throw ApiException(HttpStatusCode.Conflict, "Save your profile first")
        call.respond(feedResponse(deps, userId, "new"))
    }

    put("/feed/{jobId}/status") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val jobId = uuidParam(call.parameters["jobId"], "Job")
        val status = call.receive<StatusRequest>().status
        if (status !in setOf("new", "saved", "skipped")) throw ApiException(HttpStatusCode.BadRequest, "Unknown status")
        val updated = deps.db.tx { c -> Matches.setStatus(c, userId, jobId, status) }
        if (!updated) throw ApiException(HttpStatusCode.NotFound, "Job not found")
        call.respond(OkResponse())
    }

    get("/jobs/{jobId}") {
        call.userId()
        val jobId = uuidParam(call.parameters["jobId"], "Job")
        val job = deps.db.tx { c -> Jobs.find(c, jobId) } ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
        call.respond(
            JobDetailDto(job.id.toString(), job.title, job.company, job.location, job.remote, job.applyUrl, job.postedAt?.toString(), job.description, job.closed),
        )
    }
}

private suspend fun ApplicationCall.requireAdmin(deps: Deps) {
    val userId = userId()
    val email = deps.db.tx { c -> Users.find(c, userId)?.email }
    if (email == null || email !in deps.config.adminEmails) throw ApiException(HttpStatusCode.Forbidden, "Admins only")
}

/** Board management for ADMIN_EMAILS. Mount inside authenticate("session"). */
fun Route.adminRoutes(deps: Deps) {
    route("/admin") {
        get("/companies") {
            call.requireAdmin(deps)
            call.respond(deps.db.tx { c -> Companies.all(c) }.map { it.toDto() })
        }

        // Adds a board and syncs it straight away, so a wrong token shows up as an error immediately.
        post("/companies") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val request = call.receive<AddCompanyRequest>()
            val ats = Ats.from(request.ats) ?: throw ApiException(HttpStatusCode.BadRequest, "ats must be greenhouse, lever or ashby")
            val token = request.boardToken.trim()
            if (!AtsClient.TOKEN.matches(token)) throw ApiException(HttpStatusCode.BadRequest, "Board token may only use letters, digits, - and _")
            val name = request.name.trim().take(120).ifEmpty { throw ApiException(HttpStatusCode.BadRequest, "Name is required") }
            val company = deps.db.tx { c -> Companies.insert(c, name, ats.id, token) }
                ?: throw ApiException(HttpStatusCode.Conflict, "That board is already registered")
            call.respond(deps.ingestion.sync(company).toDto())
        }

        put("/companies/{id}/active") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val id = uuidParam(call.parameters["id"], "Company")
            val active = call.receive<ActiveRequest>().active
            if (!deps.db.tx { c -> Companies.setActive(c, id, active) }) throw ApiException(HttpStatusCode.NotFound, "Company not found")
            call.respond(OkResponse())
        }

        post("/ingest") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            call.respond(deps.ingestion.syncAll().map { it.toDto() })
        }
    }
}
