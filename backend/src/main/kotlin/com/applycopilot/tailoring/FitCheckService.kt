package com.applycopilot.tailoring

import com.applycopilot.api.ApiException
import com.applycopilot.core.Preferences
import com.applycopilot.db.AggregatorQueries
import com.applycopilot.db.Db
import com.applycopilot.resume.ResumeParsingService
import com.applycopilot.tailoring.claude.ClaudeTailor
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * The public "check a job against your resume" demo on the home page. Nothing is stored: the resume text and the
 * job are used for this one answer and dropped. Costs are capped: [dailyLimit] checks a day in all, of which
 * [aiDailyLimit] use Claude (to read the resume and reword lines); the rest use the rule-based parser and tailor,
 * which only reorder lines. Each visitor's network address gets [perIpHourly] checks an hour.
 */
class FitCheckService(
    private val db: Db,
    private val aiParsing: ResumeParsingService,
    private val claude: ClaudeTailor?,
    private val dailyLimit: Int = 300,
    private val aiDailyLimit: Int = 40,
    private val perIpHourly: Int = 5,
) {
    private val log = LoggerFactory.getLogger(FitCheckService::class.java)
    private val heuristicParsing = ResumeParsingService(null)
    private val recent = ConcurrentHashMap<String, ArrayDeque<Instant>>()

    /** False when [ip] has used its checks for the hour. Unknown addresses aren't limited here; the daily cap still applies. */
    fun allow(ip: String?): Boolean {
        if (ip.isNullOrBlank()) return true
        val now = Instant.now()
        val q = recent.computeIfAbsent(ip) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && q.first().isBefore(now.minus(Duration.ofHours(1)))) q.removeFirst()
            if (q.size >= perIpHourly) return false
            q.addLast(now)
        }
        if (recent.size > 10_000) recent.entries.removeIf { (_, v) -> synchronized(v) { v.isEmpty() || v.last().isBefore(now.minus(Duration.ofHours(1))) } }
        return true
    }

    suspend fun check(resumeText: String, givenTitle: String?, description: String): FitCheckResult {
        if (!db.tx { c -> AggregatorQueries.takeCall(c, SOURCE, dailyLimit) }) {
            throw ApiException(HttpStatusCode.TooManyRequests, "So many people tried this today that we've paused it until tomorrow. Sign in to tailor applications instead.")
        }
        val useAi = claude != null && aiDailyLimit > 0 && db.tx { c -> AggregatorQueries.takeCall(c, AI_SOURCE, aiDailyLimit) }
        val parsed = (if (useAi) aiParsing else heuristicParsing).parse(resumeText)
        val profile = parsed.profile
        if (profile.experience.none { it.bullets.isNotEmpty() } && profile.skills.isEmpty()) {
            throw ApiException(
                HttpStatusCode.UnprocessableEntity,
                "We couldn't find work experience or skills in that file. Try a text-based PDF or Word resume (not a scanned image).",
            )
        }
        val title = FitCheck.titleFrom(givenTitle, description)
        val job = JobForTailoring(title, "", description)
        val draft = if (useAi) {
            try {
                claude?.tailor(profile, Preferences(), job, emptyList())
            } catch (e: Exception) {
                log.warn("Claude tailoring failed for a fit check, using the rule-based tailor", e)
                null
            }
        } else null
        val guarded = TailorGuard.apply(draft ?: HeuristicTailor.tailor(profile, Preferences(), job, emptyList()), profile, Preferences(), job, emptyList())
        return FitCheck.evaluate(profile, title, description, guarded.application, if (draft != null) "claude" else "heuristic", parsed.removed)
    }

    companion object {
        const val SOURCE = "fitcheck"
        const val AI_SOURCE = "fitcheck_ai"
    }
}
