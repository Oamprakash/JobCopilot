package com.applycopilot.tailoring

import com.applycopilot.core.Profile
import com.applycopilot.core.SkillDictionary
import com.applycopilot.matching.HeuristicScorer
import kotlinx.serialization.Serializable

/** A talking point is one of the user's own profile bullets, quoted exactly, with the role it's from. */
@Serializable
data class TalkingPoint(val role: String, val text: String)

@Serializable
data class PrepQuestion(
    val question: String,
    /** Why an interviewer for this job is likely to ask it. */
    val why: String,
    val points: List<TalkingPoint> = emptyList(),
    val tip: String = "",
    /** "skill", "gap", "behavioural", "company" or "role". */
    val kind: String = "role",
)

@Serializable
data class InterviewPrep(
    val questions: List<PrepQuestion>,
    /** Skills the job asks for that the profile doesn't show: worth preparing an honest answer for. */
    val gaps: List<String> = emptyList(),
    /** What the guard removed from a generated draft. */
    val removed: List<String> = emptyList(),
)

/** A draft question from a generator: talking points refer to profile bullets by role and bullet index. */
data class DraftPrepQuestion(val question: String, val why: String, val refs: List<Pair<Int, Int>>, val tip: String, val kind: String)

object InterviewPrepGuard {
    const val MAX_QUESTIONS = 10

    /** Keeps only real bullets as talking points and grounded advice; drops questions left empty. */
    fun apply(drafts: List<DraftPrepQuestion>, profile: Profile, job: JobForTailoring): InterviewPrep {
        val removed = mutableListOf<String>()
        val questions = drafts.mapNotNull { d ->
            val q = d.question.trim().take(300)
            if (q.isEmpty()) return@mapNotNull null
            val points = d.refs.distinct().mapNotNull { (r, b) ->
                val role = profile.experience.getOrNull(r) ?: return@mapNotNull null.also { removed += "Dropped a talking point that pointed at no role" }
                val bullet = role.bullets.getOrNull(b) ?: return@mapNotNull null.also { removed += "Dropped a talking point that pointed at no bullet" }
                TalkingPoint("${role.title} at ${role.company}", bullet)
            }.take(3)
            PrepQuestion(
                question = q,
                why = TailorGuard.groundedText(d.why.take(400), profile, job, removed, "reasons"),
                points = points,
                tip = TailorGuard.groundedText(d.tip.take(500), profile, job, removed, "tips"),
                kind = d.kind.takeIf { it in setOf("skill", "gap", "behavioural", "company", "role") } ?: "role",
            )
        }.distinctBy { it.question.lowercase() }.take(MAX_QUESTIONS)
        return InterviewPrep(questions, Keywords.missing(profile, job).take(6), removed.distinct())
    }
}

/** Rule-based prep when Claude isn't configured (or fails): questions from the job's skills, gaps and common rounds. */
object HeuristicInterviewPrep {
    private val leadership = Regex("\\b(led|lead|mentor|managed|manager|team of|hired|coached)\\b", RegexOption.IGNORE_CASE)
    private val problemSolving = Regex("\\b(fix|fixed|reduc|improv|optimi[sz]|debug|resolv|migrat|faster|crash|latency|performance)", RegexOption.IGNORE_CASE)
    private val impact = Regex("\\d")

    fun draft(profile: Profile, job: JobForTailoring): List<DraftPrepQuestion> {
        val jdSkills = SkillDictionary.findIn(job.title + "\n" + job.description)
        val profileText = HeuristicScorer.normalizedText(HeuristicScorer.profileText(profile))
        val owned = jdSkills.filter { profileText.contains(" ${HeuristicScorer.normalizedPhrase(it)} ") }
        val gaps = jdSkills.filterNot { it in owned }

        fun refsWhere(test: (String) -> Boolean, limit: Int = 2): List<Pair<Int, Int>> =
            profile.experience.flatMapIndexed { r, e -> e.bullets.mapIndexedNotNull { b, text -> if (test(text)) r to b else null } }.take(limit)

        val drafts = mutableListOf<DraftPrepQuestion>()
        drafts += DraftPrepQuestion(
            "Walk me through your background and why this ${job.title} role.",
            "Almost every interview opens with this.",
            refsWhere({ impact.containsMatchIn(it) }, 2),
            "Keep it to two minutes: your current role, one result you're proud of, and what draws you to this job.",
            "role",
        )
        owned.take(4).forEach { skill ->
            val phrase = HeuristicScorer.normalizedPhrase(skill)
            drafts += DraftPrepQuestion(
                "How have you used $skill in your work? Tell me about a specific example.",
                "The job asks for $skill.",
                refsWhere({ HeuristicScorer.normalizedText(it).contains(" $phrase ") }),
                "Pick one example: what you built, a decision you made, and how it turned out.",
                "skill",
            )
        }
        gaps.take(2).forEach { skill ->
            drafts += DraftPrepQuestion(
                "This role uses $skill. What's your experience with it?",
                "$skill is in the job description but not in your profile.",
                emptyList(),
                "Be straightforward that it's new to you, then show how you'd get up to speed, using something you learned quickly before.",
                "gap",
            )
        }
        if (profile.experience.any { e -> e.bullets.any { leadership.containsMatchIn(it) } } || Regex("\\b(lead|senior|staff|principal|manager)\\b", RegexOption.IGNORE_CASE).containsMatchIn(job.title)) {
            drafts += DraftPrepQuestion(
                "Tell me about a time you led a team or mentored someone through something hard.",
                "The role is senior, so expect questions on leading and mentoring.",
                refsWhere({ leadership.containsMatchIn(it) }),
                "Use situation, action, result: what was hard, what you did, what changed.",
                "behavioural",
            )
        }
        drafts += DraftPrepQuestion(
            "Describe a difficult technical problem you solved.",
            "A standard round for engineering roles.",
            refsWhere({ problemSolving.containsMatchIn(it) }),
            "Explain the problem, the options you considered, why you chose yours, and the result.",
            "behavioural",
        )
        drafts += DraftPrepQuestion(
            "Why do you want to work at ${job.company}?",
            "They'll want to know you chose them on purpose.",
            emptyList(),
            "Read their product, recent news and engineering blog; connect one thing you found to your own work.",
            "company",
        )
        drafts += DraftPrepQuestion(
            "Do you have any questions for us?",
            "Usually the last question.",
            emptyList(),
            "Prepare two: one about the team's current challenges, one about what success looks like in the first six months.",
            "company",
        )
        return drafts
    }
}
