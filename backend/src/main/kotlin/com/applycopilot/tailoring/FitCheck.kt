package com.applycopilot.tailoring

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.matching.HeuristicScorer
import com.applycopilot.matching.JobForMatching
import com.applycopilot.matching.LexicalRanker
import kotlinx.serialization.Serializable
import java.util.UUID

/** One resume line as the fit check shows it: [tailored] is the reworded text, or the original when it was only moved up. */
@Serializable
data class FitCheckBullet(val original: String, val tailored: String, val reworded: Boolean)

/**
 * The try-before-you-sign-up result: how a resume fits one pasted job. [profile] is what we read from the resume,
 * handed back so the browser can carry it into onboarding instead of asking for the file again; nothing is stored.
 */
@Serializable
data class FitCheckResult(
    val jobTitle: String,
    val score: Int,
    val reasons: List<String>,
    val matchedSkills: List<String>,
    val missingSkills: List<String>,
    val bullets: List<FitCheckBullet>,
    /** "claude" when the lines were reworded by Claude (and checked by the guard), "heuristic" when only reordered. */
    val rewordedBy: String,
    val profile: Profile,
    val removed: List<String> = emptyList(),
)

object FitCheck {
    const val MAX_BULLETS = 3

    /** A job title from the paste: the given one, else its first short line, else a placeholder. */
    fun titleFrom(given: String?, description: String): String =
        given?.trim()?.take(120)?.ifEmpty { null }
            ?: description.lineSequence().map { it.trim() }.firstOrNull { it.length in 4..90 }
            ?: "This job"

    /**
     * Scores [profile] against the job and picks the lines to show from [guarded], a tailored draft that has already
     * been through [TailorGuard]: reworded bullets first, then the bullets the draft moved to the top.
     */
    fun evaluate(profile: Profile, title: String, description: String, guarded: TailoredApplication, rewordedBy: String, removed: List<String> = emptyList()): FitCheckResult {
        val job = JobForMatching(UUID.randomUUID(), "", title, "", false, description)
        val similarity = LexicalRanker.rank(listOf(job), profile).firstOrNull()?.second ?: 0.0
        val fit = HeuristicScorer.score(job, profile, Preferences(), similarity)
        val jdText = HeuristicScorer.normalizedText(title + "\n" + description)
        val matched = profile.skills.filter { s -> HeuristicScorer.normalizedPhrase(s).let { it.isNotEmpty() && jdText.contains(" $it ") } }
        val missing = (fit.missingSkills + Keywords.missing(profile, JobForTailoring(title, "", description))).distinctBy { it.lowercase() }

        val all = guarded.resume.experience.flatMap { e ->
            e.bullets.map { b ->
                val original = b.original ?: profile.experience.getOrNull(e.sourceIndex)?.bullets?.getOrNull(b.sourceIndex) ?: b.text
                FitCheckBullet(original, b.text, b.change == Change.REWORDED && b.text != original)
            }
        }
        // Reworded lines are the point of the demo; top-of-role lines fill in when rewording found little to change.
        val leads = guarded.resume.experience.take(2).flatMapIndexed { i, e ->
            e.bullets.take(if (i == 0) MAX_BULLETS else 2).map { b ->
                val original = b.original ?: profile.experience.getOrNull(e.sourceIndex)?.bullets?.getOrNull(b.sourceIndex) ?: b.text
                FitCheckBullet(original, b.text, b.change == Change.REWORDED && b.text != original)
            }
        }
        val bullets = (all.filter { it.reworded } + leads).distinctBy { it.original }.take(MAX_BULLETS)

        return FitCheckResult(
            jobTitle = title,
            score = fit.score,
            reasons = fit.reasons,
            matchedSkills = matched.take(8),
            missingSkills = missing.take(6),
            bullets = bullets,
            rewordedBy = if (bullets.any { it.reworded }) rewordedBy else "heuristic",
            profile = profile,
            removed = removed,
        )
    }
}
