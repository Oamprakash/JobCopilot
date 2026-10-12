package com.applycopilot.tailoring

import com.applycopilot.api.ApiException
import com.applycopilot.core.Preferences
import com.applycopilot.db.Applications
import com.applycopilot.db.Db
import com.applycopilot.db.Jobs
import com.applycopilot.db.PreferencesTable
import com.applycopilot.db.Profiles
import com.applycopilot.db.StoredApplication
import com.applycopilot.db.Users
import com.applycopilot.tailoring.claude.ClaudeTailor
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Builds a tailored application for one job: Claude drafts it when configured (the rule-based tailor
 * otherwise, or if the call fails), then [TailorGuard] decides what may go out.
 */
class TailorService(
    private val db: Db,
    private val claude: ClaudeTailor?,
    /** Applications a free user may start per calendar month; regenerating one doesn't count again. */
    val freeMonthlyLimit: Int,
) {
    private val log = LoggerFactory.getLogger(TailorService::class.java)

    /**
     * [pastedDescription]: null keeps whatever this application used before; blank goes back to the job's own
     * description; anything else replaces it (for jobs whose source gave only a summary).
     */
    suspend fun tailor(userId: UUID, jobId: UUID, requestedQuestions: List<String>?, pastedDescription: String? = null): StoredApplication {
        val pasted = pastedDescription?.trim()?.replace(Regex("\r\n?"), "\n")
        if (pasted != null && pasted.isNotEmpty() && pasted.length < MIN_PASTED) {
            throw ApiException(HttpStatusCode.BadRequest, "That looks too short for a full job description. Paste the whole posting, or leave it empty.")
        }
        val ctx = db.tx { c ->
            val user = Users.find(c, userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "Not signed in")
            val stored = Profiles.latest(c, userId) ?: throw ApiException(HttpStatusCode.Conflict, "Save your profile first")
            val job = Jobs.find(c, jobId) ?: throw ApiException(HttpStatusCode.NotFound, "Job not found")
            val existing = Applications.find(c, userId, jobId)
            if (existing == null && user.plan == "free" && Applications.createdThisMonth(c, userId) >= freeMonthlyLimit) {
                throw ApiException(
                    HttpStatusCode.PaymentRequired,
                    "The free plan includes $freeMonthlyLimit tailored applications a month. Upgrade to Pro for unlimited tailoring.",
                )
            }
            val description = when {
                pasted == null -> existing?.jobDescription
                pasted.isEmpty() -> null
                else -> pasted.take(MAX_PASTED)
            }
            Ctx(
                stored.version, stored.profile, PreferencesTable.find(c, userId) ?: Preferences(),
                JobForTailoring(job.title, job.company, description ?: job.description), existing, description,
            )
        }

        val questions = requestedQuestions?.map { it.trim().take(300) }?.filter { it.isNotEmpty() }?.distinct()?.take(10)
            ?: ctx.existing?.questions?.takeIf { it.isNotEmpty() }
            ?: Questions.defaults(ctx.job.company)

        val fromClaude = claude?.let {
            try {
                it.tailor(ctx.profile, ctx.prefs, ctx.job, questions)
            } catch (e: Exception) {
                log.warn("Claude tailoring failed, using the rule-based tailor", e)
                null
            }
        }
        val draft = fromClaude ?: HeuristicTailor.tailor(ctx.profile, ctx.prefs, ctx.job, questions)
        val guarded = TailorGuard.apply(draft, ctx.profile, ctx.prefs, ctx.job, questions)

        return db.tx { c ->
            Applications.saveDraft(
                c, userId, jobId, ctx.profileVersion, if (fromClaude != null) "claude" else "heuristic",
                guarded.application, guarded.removed, questions, ctx.pastedDescription,
            )
        }
    }

    private data class Ctx(
        val profileVersion: Int,
        val profile: com.applycopilot.core.Profile,
        val prefs: Preferences,
        val job: JobForTailoring,
        val existing: StoredApplication?,
        val pastedDescription: String?,
    )

    companion object {
        const val MIN_PASTED = 200
        const val MAX_PASTED = 30_000
    }
}
