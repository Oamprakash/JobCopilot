package com.applycopilot.api

import com.applycopilot.db.Events
import com.applycopilot.db.EventTypes
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
import com.applycopilot.jobs.IngestionService
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
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = LoggerFactory.getLogger("com.applycopilot.api.FeedRoutes")

/** Unreviewed matches below this score stay out of the New tab unless the user asks for weaker matches. */
const val MIN_FEED_SCORE = 40
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
    /** Where the job came from: a board ("greenhouse", ...) or an aggregator ("adzuna", "himalayas"). Aggregator jobs carry its attribution. */
    val source: String = "",
    /** True when only a summary of the description is available. */
    val descriptionPartial: Boolean = false,
    /** What the description says about visa sponsorship: "offered", "not_offered", or null. */
    val visa: String? = null,
    val relocation: Boolean = false,
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

/**
 * [hiddenWeak] is how many new matches score below [minScore] and are left out because weaker matches weren't
 * asked for (always 0 for Saved and Skipped, which show every score).
 */
@Serializable
data class FeedResponse(
    val jobs: List<FeedJobDto>,
    val lastRun: MatchRunDto?,
    val needsRefresh: Boolean,
    val minScore: Int = MIN_FEED_SCORE,
    val hiddenWeak: Int = 0,
    /** Jobs on each tab ("new", "saved", "skipped"); New follows the same weak-match setting as [jobs]. */
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class StatusRequest(val status: String)

@Serializable
data class ClientEventRequest(val type: String, val jobId: String? = null)

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
    val source: String = "",
    val descriptionPartial: Boolean = false,
    val visa: String? = null,
    val relocation: Boolean = false,
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

/**
 * An aggregator (Adzuna, Himalayas) as admins see it. [configured]: anything it needs is set on the server
 * (Adzuna's keys; Himalayas needs none). [enabled]: admins have it switched on. Off means no calls to it, and
 * its jobs stop appearing as new matches.
 */
@Serializable
data class SourceStatusDto(
    val configured: Boolean,
    val enabled: Boolean,
    val callsToday: Int,
    val dailyLimit: Int,
    val openJobs: Int,
    val lastSyncedAt: String?,
    val lastError: String?,
)

@Serializable
data class EnabledRequest(val enabled: Boolean)

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
    source = job.source,
    descriptionPartial = job.partial,
    visa = job.visa,
    relocation = job.relocation,
)

private fun MatchRun.toDto() = MatchRunDto(startedAt.toString(), finishedAt?.toString(), considered, passedFilter, scored, llmScored, error, filtered, suggestions)

private fun Company.toDto() = CompanyDto(id.toString(), name, ats, boardToken, active, lastSyncedAt?.toString(), lastError, openJobs)

private fun SyncResult.toDto() = SyncResultDto(company.toDto(), fetched, inserted, closed, error)

private fun uuidParam(raw: String?, what: String): UUID =
    raw?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "$what not found")

/** `?weak=true` on the New tab also returns matches scoring below [MIN_FEED_SCORE]. */
private fun ApplicationCall.wantsWeak(): Boolean = request.queryParameters["weak"] == "true"

private suspend fun feedResponse(deps: Deps, userId: UUID, status: String, weak: Boolean = false): FeedResponse = deps.db.tx { c ->
    val lastRun = MatchRuns.latest(c, userId)
    val changedAt = MatchRuns.inputsChangedAt(c, userId)
    val hidesWeak = status == "new" && !weak
    FeedResponse(
        jobs = Matches.feed(c, userId, status, if (hidesWeak) MIN_FEED_SCORE else 0).map { it.toDto() },
        lastRun = lastRun?.toDto(),
        needsRefresh = changedAt != null && (lastRun == null || lastRun.startedAt.isBefore(changedAt)),
        hiddenWeak = if (hidesWeak) Matches.countNewBelow(c, userId, MIN_FEED_SCORE) else 0,
        counts = Matches.tabCounts(c, userId, if (weak) 0 else MIN_FEED_SCORE),
    )
}

/** The daily feed. Mount inside authenticate("session"). */
fun Route.feedRoutes(deps: Deps) {
    // Locations and roles to suggest while the user types preferences, with open-job counts.
    get("/autocomplete") {
        call.userId()
        call.respond(deps.autocomplete.options())
    }

    get("/feed") {
        val status = call.request.queryParameters["status"] ?: "new"
        if (status !in setOf("new", "saved", "skipped")) throw ApiException(HttpStatusCode.BadRequest, "Unknown status")
        call.respond(feedResponse(deps, call.userId(), status, call.wantsWeak()))
    }

    // Rebuilds the feed now. Throttled unless the profile or preferences changed since the last run.
    post("/feed/refresh") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val weak = call.wantsWeak()
        val current = feedResponse(deps, userId, "new", weak)
        val last = current.lastRun?.finishedAt?.let { Instant.parse(it) }
        val recent = last != null && last.isAfter(Instant.now().minus(REFRESH_COOLDOWN))
        if (!current.needsRefresh && recent) {
            call.respond(current)
            return@post
        }
        // Fetch this user's aggregator searches first if they haven't run today, so new preferences show results now.
        runCatching { deps.ingestion.syncAggregatorFor(userId) }.onFailure { log.warn("Aggregator search failed for {}", userId, it) }
        val run = deps.matching.refresh(userId) ?: throw ApiException(HttpStatusCode.Conflict, "Save your profile first")
        val response = feedResponse(deps, userId, "new", weak)
        deps.db.tx { c ->
            Events.record(
                c, userId, EventTypes.FEED_REFRESHED,
                data = Events.data("considered" to run.considered, "passedFilter" to run.passedFilter, "shown" to response.jobs.size),
            )
        }
        call.respond(response)
    }

    put("/feed/{jobId}/status") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val jobId = uuidParam(call.parameters["jobId"], "Job")
        val status = call.receive<StatusRequest>().status
        if (status !in setOf("new", "saved", "skipped")) throw ApiException(HttpStatusCode.BadRequest, "Unknown status")
        val updated = deps.db.tx { c ->
            Matches.setStatus(c, userId, jobId, status).also { ok ->
                val type = when (status) {
                    "saved" -> EventTypes.JOB_SAVED
                    "skipped" -> EventTypes.JOB_SKIPPED
                    else -> EventTypes.JOB_RESTORED
                }
                if (ok) Events.record(c, userId, type, jobId)
            }
        }
        if (!updated) throw ApiException(HttpStatusCode.NotFound, "Job not found")
        call.respond(OkResponse())
    }

    // Clicks the browser reports: opening a job's posting, and going to the employer's application page.
    post("/events") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val request = call.receive<ClientEventRequest>()
        if (request.type !in EventTypes.fromClient) throw ApiException(HttpStatusCode.BadRequest, "Unknown event type")
        val jobId = uuidParam(request.jobId, "Job")
        deps.db.tx { c ->
            Jobs.find(c, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
            Events.record(c, userId, request.type, jobId)
        }
        call.respond(OkResponse())
    }

    get("/jobs/{jobId}") {
        call.userId()
        val jobId = uuidParam(call.parameters["jobId"], "Job")
        val job = deps.db.tx { c -> Jobs.find(c, jobId) } ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
        call.respond(
            JobDetailDto(
                job.id.toString(), job.title, job.company, job.location, job.remote, job.applyUrl, job.postedAt?.toString(),
                job.description, job.closed, job.source, job.partial, job.visa, job.relocation,
            ),
        )
    }
}

/** Lets only ADMIN_EMAILS through; returns the admin's user id. */
internal suspend fun ApplicationCall.requireAdmin(deps: Deps): UUID {
    val userId = userId()
    val email = deps.db.tx { c -> Users.find(c, userId)?.email }
    if (email == null || email !in deps.config.adminEmails) throw ApiException(HttpStatusCode.Forbidden, "Admins only")
    return userId
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
            val result = deps.ingestion.sync(company)
            deps.autocomplete.invalidate()
            call.respond(result.toDto())
        }

        put("/companies/{id}/active") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val id = uuidParam(call.parameters["id"], "Company")
            val active = call.receive<ActiveRequest>().active
            if (!deps.db.tx { c -> Companies.setActive(c, id, active) }) throw ApiException(HttpStatusCode.NotFound, "Company not found")
            call.respond(OkResponse())
        }

        get("/sources/{source}") {
            call.requireAdmin(deps)
            call.respond(deps.sourceStatus(sourceParam(call.parameters["source"])))
        }

        put("/sources/{source}") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val source = sourceParam(call.parameters["source"])
            val enabled = call.receive<EnabledRequest>().enabled
            if (enabled && source == IngestionService.ADZUNA && deps.config.adzuna == null) {
                throw ApiException(HttpStatusCode.Conflict, "Set ADZUNA_APP_ID and ADZUNA_APP_KEY on the server first")
            }
            deps.db.tx { c ->
                val row = Companies.all(c).firstOrNull { it.ats == source }
                    ?: Companies.insert(c, SOURCE_NAMES.getValue(source), source, SOURCE_TOKENS.getValue(source))
                    ?: error("$source source row missing")
                Companies.setActive(c, row.id, enabled)
            }
            deps.autocomplete.invalidate()
            LoggerFactory.getLogger("Admin").info("Admin {} switched {} {}", call.userId(), source, if (enabled) "on" else "off")
            call.respond(deps.sourceStatus(source))
        }

        post("/ingest") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val results = deps.ingestion.syncAll()
            deps.autocomplete.invalidate()
            call.respond(results.map { it.toDto() })
        }
    }
}

private val SOURCE_NAMES = mapOf(IngestionService.ADZUNA to "Adzuna", IngestionService.HIMALAYAS to "Himalayas")
private val SOURCE_TOKENS = mapOf(IngestionService.ADZUNA to "in", IngestionService.HIMALAYAS to "remote")

private fun sourceParam(raw: String?): String =
    raw?.lowercase()?.takeIf { it in IngestionService.AGGREGATORS } ?: throw ApiException(HttpStatusCode.NotFound, "Unknown job source")

private suspend fun Deps.sourceStatus(source: String): SourceStatusDto = db.tx { c ->
    val row = Companies.all(c).firstOrNull { it.ats == source }
    val configured = source != IngestionService.ADZUNA || config.adzuna != null
    SourceStatusDto(
        configured = configured,
        enabled = row?.active == true && configured,
        callsToday = com.applycopilot.db.AggregatorQueries.callsToday(c, source),
        dailyLimit = if (source == IngestionService.ADZUNA) config.adzuna?.dailyCalls ?: 0 else config.himalayasDailyCalls,
        openJobs = row?.openJobs ?: 0,
        lastSyncedAt = row?.lastSyncedAt?.toString(),
        lastError = row?.lastError,
    )
}
