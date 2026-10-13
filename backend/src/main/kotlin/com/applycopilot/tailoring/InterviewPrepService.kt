package com.applycopilot.tailoring

import com.applycopilot.api.ApiException
import com.applycopilot.db.Applications
import com.applycopilot.db.Db
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.db.InterviewPreps
import com.applycopilot.db.Jobs
import com.applycopilot.db.Profiles
import com.applycopilot.tailoring.claude.ClaudeInterviewPrep
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Builds interview prep for a job: Claude when configured (rule-based otherwise or on failure), then the guard. */
class InterviewPrepService(private val db: Db, private val claude: ClaudeInterviewPrep?, private val dailyLimit: Int = 10) {
    private val log = LoggerFactory.getLogger(InterviewPrepService::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun find(c: java.sql.Connection, userId: UUID, jobId: UUID): Pair<InterviewPrep, String>? =
        InterviewPreps.find(c, userId, jobId)?.let { (generator, content) -> json.decodeFromString(InterviewPrep.serializer(), content) to generator }

    suspend fun generate(userId: UUID, jobId: UUID): Pair<InterviewPrep, String> {
        val (profile, job) = db.tx { c ->
            if (InterviewPreps.countSince(c, userId, Instant.now().minus(Duration.ofHours(24))) >= dailyLimit) {
                throw ApiException(HttpStatusCode.TooManyRequests, "You've prepared $dailyLimit interviews today. Try again tomorrow.")
            }
            val profile = Profiles.latest(c, userId)?.profile ?: throw ApiException(HttpStatusCode.Conflict, "Save your profile first")
            val job = Jobs.find(c, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
            // A full description the user pasted for this application is better material than a summary.
            val description = Applications.find(c, userId, jobId)?.jobDescription ?: job.description
            profile to JobForTailoring(job.title, job.company, description)
        }
        val fromClaude = claude?.let {
            try {
                it.draft(profile, job).takeIf { d -> d.isNotEmpty() }
            } catch (e: Exception) {
                log.warn("Claude interview prep failed, using the rule-based one", e)
                null
            }
        }
        val generator = if (fromClaude != null) "claude" else "heuristic"
        val prep = InterviewPrepGuard.apply(fromClaude ?: HeuristicInterviewPrep.draft(profile, job), profile, job)
        db.tx { c ->
            InterviewPreps.save(c, userId, jobId, generator, json.encodeToString(InterviewPrep.serializer(), prep))
            Events.record(c, userId, EventTypes.INTERVIEW_PREP, jobId, Events.data("generator" to generator, "questions" to prep.questions.size))
        }
        return prep to generator
    }
}
