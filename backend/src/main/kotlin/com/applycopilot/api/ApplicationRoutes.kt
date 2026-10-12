package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.Applications
import com.applycopilot.db.Jobs
import com.applycopilot.db.Profiles
import com.applycopilot.db.StoredApplication
import com.applycopilot.db.Users
import com.applycopilot.tailoring.JobForTailoring
import com.applycopilot.tailoring.Keywords
import com.applycopilot.tailoring.TailorGuard
import com.applycopilot.tailoring.TailoredApplication
import com.applycopilot.tailoring.TailoredExperience
import com.applycopilot.tailoring.pdf.ResumePdf
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.util.UUID

@Serializable
data class TailorRequest(val questions: List<String>? = null)

@Serializable
data class EditApplicationRequest(val application: TailoredApplication)

@Serializable
data class ApplicationJobDto(val jobId: String, val title: String, val company: String, val location: String, val applyUrl: String, val closed: Boolean)

@Serializable
data class UsageDto(val plan: String, val tailoredThisMonth: Int, val limit: Int?)

@Serializable
data class ApplicationDto(
    val job: ApplicationJobDto,
    val status: String,
    val generator: String,
    val application: TailoredApplication,
    /** What the guardrail removed from the generated draft. */
    val removed: List<String>,
    /** Claims in the user's own edits that the profile doesn't back. Shown, not blocked. */
    val warnings: List<String>,
    val keywordsCovered: List<String>,
    val keywordsMissing: List<String>,
    val score: Int?,
    val usage: UsageDto,
    val updatedAt: String,
    val approvedAt: String?,
)

private fun jobIdParam(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")

private fun usage(c: Connection, deps: Deps, userId: UUID): UsageDto {
    val plan = Users.find(c, userId)?.plan ?: "free"
    return UsageDto(plan, Applications.createdThisMonth(c, userId), if (plan == "free") deps.tailor.freeMonthlyLimit else null)
}

private fun score(c: Connection, userId: UUID, jobId: UUID): Int? =
    c.prepareStatement("SELECT score FROM matches WHERE user_id = ? AND job_id = ?").use { st ->
        st.setObject(1, userId)
        st.setObject(2, jobId)
        st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
    }

private fun toDto(c: Connection, deps: Deps, userId: UUID, stored: StoredApplication): ApplicationDto {
    val job = Jobs.find(c, stored.jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
    val profile = Profiles.latest(c, userId)?.profile
    val forTailoring = JobForTailoring(job.title, job.company, job.description)
    return ApplicationDto(
        job = ApplicationJobDto(job.id.toString(), job.title, job.company, job.location, job.applyUrl, job.closed),
        status = stored.status,
        generator = stored.generator,
        application = stored.content,
        removed = stored.removed,
        warnings = profile?.let { TailorGuard.warnings(stored.content, it) }.orEmpty(),
        keywordsCovered = Keywords.covered(stored.content, forTailoring),
        keywordsMissing = profile?.let { Keywords.missing(it, forTailoring) }.orEmpty(),
        score = score(c, userId, stored.jobId),
        usage = usage(c, deps, userId),
        updatedAt = stored.updatedAt.toString(),
        approvedAt = stored.approvedAt?.toString(),
    )
}

/** Tailoring and review. Mount inside authenticate("session"). */
fun Route.applicationRoutes(deps: Deps) {
    get("/usage") {
        val userId = call.userId()
        call.respond(deps.db.tx { c -> usage(c, deps, userId) })
    }

    route("/applications/{jobId}") {
        get {
            val userId = call.userId()
            val jobId = jobIdParam(call.parameters["jobId"])
            val dto = deps.db.tx { c -> Applications.find(c, userId, jobId)?.let { toDto(c, deps, userId, it) } }
                ?: throw ApiException(HttpStatusCode.NotFound, "Not tailored yet")
            call.respond(dto)
        }

        // Creates or regenerates the tailored draft for this job.
        post("/tailor") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val jobId = jobIdParam(call.parameters["jobId"])
            val request = runCatching { call.receive<TailorRequest>() }.getOrDefault(TailorRequest())
            val stored = deps.tailor.tailor(userId, jobId, request.questions)
            call.respond(deps.db.tx { c -> toDto(c, deps, userId, stored) })
        }

        // The user's own edits. Titles, companies and dates stay the profile's; wording is theirs.
        put {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val jobId = jobIdParam(call.parameters["jobId"])
            val edited = call.receive<EditApplicationRequest>().application
            val dto = deps.db.tx { c ->
                val profile = Profiles.latest(c, userId)?.profile ?: throw ApiException(HttpStatusCode.Conflict, "Save your profile first")
                val fixed = edited.copy(
                    resume = edited.resume.copy(
                        fullName = profile.fullName,
                        experience = edited.resume.experience.mapNotNull { e -> profile.experience.getOrNull(e.sourceIndex)?.let { src -> e.withSource(src) } },
                        education = profile.education,
                    ),
                    coverLetter = edited.coverLetter.take(10_000),
                    answers = edited.answers.take(20).map { it.copy(answer = it.answer.take(3000)) },
                )
                val saved = Applications.saveEdits(c, userId, jobId, fixed) ?: throw ApiException(HttpStatusCode.NotFound, "Not tailored yet")
                toDto(c, deps, userId, saved)
            }
            call.respond(dto)
        }

        post("/approve") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val jobId = jobIdParam(call.parameters["jobId"])
            val dto = deps.db.tx { c -> Applications.approve(c, userId, jobId)?.let { toDto(c, deps, userId, it) } }
                ?: throw ApiException(HttpStatusCode.NotFound, "Not tailored yet")
            call.respond(dto)
        }

        get("/resume.pdf") {
            val userId = call.userId()
            val jobId = jobIdParam(call.parameters["jobId"])
            val (stored, company) = deps.db.tx { c ->
                val app = Applications.find(c, userId, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Not tailored yet")
                app to (Jobs.find(c, jobId)?.company ?: "job")
            }
            val bytes = withContext(Dispatchers.IO) { ResumePdf.render(stored.content.resume) }
            val name = "${stored.content.resume.fullName}_$company".replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "resume" }
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "$name.pdf").toString(),
            )
            call.respondBytes(bytes, ContentType.Application.Pdf)
        }
    }
}

private fun TailoredExperience.withSource(src: com.applycopilot.core.Experience) = copy(
    title = src.title,
    company = src.company,
    startDate = src.startDate,
    endDate = src.endDate,
    current = src.current,
    bullets = bullets.map { it.copy(text = it.text.trim().take(600)) }.filter { it.text.isNotEmpty() }.take(30),
)
