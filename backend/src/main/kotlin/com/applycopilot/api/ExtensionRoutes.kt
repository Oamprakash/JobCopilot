package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.Applications
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.db.ExtensionAuth
import com.applycopilot.db.ManualJobs
import com.applycopilot.db.Profiles
import com.applycopilot.db.Tracker
import com.applycopilot.db.Users
import com.applycopilot.extension.Autofill
import com.applycopilot.extension.AutofillProfile
import com.applycopilot.extension.JobUrls
import com.applycopilot.tailoring.pdf.ResumePdf
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PairCodeResponse(val code: String, val expiresAt: String)

@Serializable
data class ExtensionStatusResponse(val connected: Int)

@Serializable
data class PairRequest(val code: String)

@Serializable
data class PairResponse(val token: String, val email: String)

@Serializable
data class ExtMeResponse(val email: String, val name: String?)

@Serializable
data class ExtAnswer(val question: String, val answer: String)

@Serializable
data class ExtApplication(
    val jobId: String,
    val title: String,
    val company: String,
    val status: String,
    val coverLetter: String,
    val answers: List<ExtAnswer>,
    /** Path under the API base (prefix with /api in the browser) to the tailored resume PDF. */
    val resumePath: String,
    val resumeFileName: String,
)

@Serializable
data class ExtApplicationSummary(val jobId: String, val title: String, val company: String, val status: String)

@Serializable
data class AutofillResponse(
    val profile: AutofillProfile,
    /** The application for this page's job, or the one asked for by jobId. Null when none matches. */
    val application: ExtApplication?,
    /** Recent applications to pick from when the page doesn't match one (e.g. it was reached via a redirect). */
    val recent: List<ExtApplicationSummary>,
    val trackerStage: String?,
)

@Serializable
data class SaveJobRequest(val url: String, val title: String, val company: String = "", val location: String = "", val description: String = "")

@Serializable
data class SaveJobResponse(val jobId: String, val stage: String, val alreadyTracked: Boolean)

@Serializable
data class AppliedRequest(val jobId: String)

@Serializable
data class StageResponse(val jobId: String, val stage: String)

private suspend fun ApplicationCall.extensionUserId(deps: Deps): UUID {
    val header = request.headers[HttpHeaders.Authorization].orEmpty()
    val token = header.removePrefix("Bearer ").trim().takeIf { header.startsWith("Bearer ") && it.length in 20..100 }
        ?: throw ApiException(HttpStatusCode.Unauthorized, "Connect the extension from your Apply Copilot profile")
    return deps.db.tx { c -> ExtensionAuth.userFor(c, token) }
        ?: throw ApiException(HttpStatusCode.Unauthorized, "This extension was disconnected. Connect it again from your profile.")
}

private fun uuidOrNotFound(raw: String?): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")

private fun pdfName(fullName: String, company: String) =
    "${fullName}_$company".replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "resume" } + ".pdf"

/** Pairing from the web app (session cookie). Mount inside authenticate("session"). */
fun Route.extensionPairingRoutes(deps: Deps) {
    route("/extension") {
        get {
            val userId = call.userId()
            call.respond(ExtensionStatusResponse(deps.db.tx { c -> ExtensionAuth.connectedCount(c, userId) }))
        }
        post("/pair-code") {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            val (code, expires) = deps.db.tx { c -> ExtensionAuth.newPairCode(c, userId) }
            call.respond(PairCodeResponse(code, expires.toString()))
        }
        delete {
            call.requireSameOrigin(deps.config)
            val userId = call.userId()
            deps.db.tx { c -> ExtensionAuth.revokeAll(c, userId) }
            call.respond(OkResponse())
        }
    }
}

/**
 * The browser extension's API, authenticated by a bearer token from pairing (not the session cookie, so the
 * same-origin check doesn't apply: a bearer token can't be sent by another site). Mount outside authenticate().
 * Nothing here submits anything: the extension only fills fields the user then reviews and submits.
 */
fun Route.extensionRoutes(deps: Deps) {
    route("/ext") {
        post("/pair") {
            val code = call.receive<PairRequest>().code
            if (code.length > 20) throw ApiException(HttpStatusCode.BadRequest, "That code isn't valid")
            val paired = deps.db.tx { c ->
                ExtensionAuth.pair(c, code)?.let { (userId, token) ->
                    Events.record(c, userId, EventTypes.EXTENSION_CONNECTED)
                    token to (Users.find(c, userId)?.email ?: "")
                }
            } ?: throw ApiException(HttpStatusCode.Unauthorized, "That code has expired or was already used. Get a new one from your profile.")
            call.respond(PairResponse(paired.first, paired.second))
        }

        get("/me") {
            val userId = call.extensionUserId(deps)
            val me = deps.db.tx { c ->
                val user = Users.find(c, userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "Account not found")
                ExtMeResponse(user.email, Profiles.latest(c, userId)?.profile?.fullName?.takeIf { it.isNotBlank() })
            }
            call.respond(me)
        }

        // What to fill on the current page: the user's details, plus the tailored application for this job if any.
        get("/autofill") {
            val userId = call.extensionUserId(deps)
            val pageUrl = call.request.queryParameters["url"].orEmpty().take(2000)
            val askedJob = call.request.queryParameters["jobId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val response = deps.db.tx { c ->
                val user = Users.find(c, userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "Account not found")
                val profile = Profiles.latest(c, userId)?.profile ?: throw ApiException(HttpStatusCode.Conflict, "Save your profile in Apply Copilot first")
                val recent = Applications.recent(c, userId)
                val match = askedJob?.let { id -> recent.firstOrNull { it.jobId == id } }
                    ?: recent.firstOrNull { pageUrl.isNotEmpty() && JobUrls.sameJob(it.applyUrl, pageUrl) }
                val application = match?.let { m ->
                    Applications.find(c, userId, m.jobId)?.let { stored ->
                        ExtApplication(
                            jobId = m.jobId.toString(),
                            title = m.title,
                            company = m.company,
                            status = stored.status,
                            coverLetter = stored.content.coverLetter,
                            answers = stored.content.answers.filter { it.answer.isNotBlank() }.map { ExtAnswer(it.question, it.answer) },
                            resumePath = "/ext/applications/${m.jobId}/resume.pdf",
                            resumeFileName = pdfName(stored.content.resume.fullName, m.company),
                        )
                    }
                }
                Events.record(
                    c, userId, EventTypes.EXTENSION_AUTOFILL, match?.jobId,
                    Events.data("host" to (JobUrls.normalize(pageUrl)?.substringBefore('/') ?: ""), "matched" to (application != null)),
                )
                AutofillResponse(
                    profile = Autofill.profileFields(profile, user.email),
                    application = application,
                    recent = recent.take(10).map { ExtApplicationSummary(it.jobId.toString(), it.title, it.company, it.status) },
                    trackerStage = match?.let { Tracker.find(c, userId, it.jobId)?.stage },
                )
            }
            call.respond(response)
        }

        get("/applications/{jobId}/resume.pdf") {
            val userId = call.extensionUserId(deps)
            val jobId = uuidOrNotFound(call.parameters["jobId"])
            val (stored, company) = deps.db.tx { c ->
                val app = Applications.find(c, userId, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Not tailored yet")
                Events.record(c, userId, EventTypes.RESUME_DOWNLOADED, jobId, Events.data("via" to "extension"))
                app to (com.applycopilot.db.Jobs.find(c, jobId)?.company ?: "job")
            }
            val bytes = withContext(Dispatchers.IO) { ResumePdf.render(stored.content.resume) }
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, pdfName(stored.content.resume.fullName, company)).toString(),
            )
            call.respondBytes(bytes, ContentType.Application.Pdf)
        }

        // "Save job" from any job page: reuse the job if a source already has it, otherwise keep it as a saved job.
        post("/save") {
            val userId = call.extensionUserId(deps)
            val r = call.receive<SaveJobRequest>()
            val url = r.url.trim()
            val host = JobUrls.normalize(url)?.substringBefore('/')
            if (host == null || !(url.startsWith("https://") || url.startsWith("http://"))) throw ApiException(HttpStatusCode.BadRequest, "Open the job's page first")
            val title = r.title.trim().ifEmpty { throw ApiException(HttpStatusCode.BadRequest, "The job needs a title") }
            val result = deps.db.tx { c ->
                val existing = ManualJobs.findByUrl(c, { JobUrls.sameJob(it, url) || JobUrls.sameJob(url, it) }, host)
                val jobId = existing ?: ManualJobs.upsert(c, url, title, r.company.trim().ifEmpty { host }, r.location.trim(), r.description.trim())
                val added = Tracker.add(c, userId, jobId, "saved")
                Events.record(c, userId, EventTypes.JOB_SAVED_FROM_PAGE, jobId, Events.data("host" to host, "existing" to (existing != null)))
                SaveJobResponse(jobId.toString(), Tracker.find(c, userId, jobId)?.stage ?: "saved", alreadyTracked = !added)
            }
            call.respond(result)
        }

        // The user says they submitted the application on the employer's site.
        post("/applied") {
            val userId = call.extensionUserId(deps)
            val jobId = uuidOrNotFound(call.receive<AppliedRequest>().jobId)
            val stage = deps.db.tx { c ->
                com.applycopilot.db.Jobs.find(c, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
                Tracker.markApplied(c, userId, jobId)
                Events.record(c, userId, EventTypes.MARKED_APPLIED, jobId, Events.data("via" to "extension"))
                Tracker.find(c, userId, jobId)?.stage ?: "applied"
            }
            call.respond(StageResponse(jobId.toString(), stage))
        }
    }
}
