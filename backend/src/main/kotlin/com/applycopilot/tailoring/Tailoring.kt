package com.applycopilot.tailoring

import com.applycopilot.core.Education
import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.SkillDictionary
import com.applycopilot.matching.HardFilter
import com.applycopilot.matching.HeuristicScorer
import kotlinx.serialization.Serializable

// ---- Models ---------------------------------------------------------------------------------

/** How a tailored bullet relates to the master profile bullet it came from. */
object Change {
    const val UNCHANGED = "unchanged"
    const val MOVED_UP = "moved_up"
    const val REWORDED = "reworded"
}

/** [sourceIndex] points at the bullet in the profile role this came from; [original] is set when reworded. */
@Serializable
data class TailoredBullet(val text: String, val sourceIndex: Int, val change: String = Change.UNCHANGED, val original: String? = null)

/** [sourceIndex] points at the role in [Profile.experience]; title, company and dates always come from there. */
@Serializable
data class TailoredExperience(
    val sourceIndex: Int,
    val title: String = "",
    val company: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val current: Boolean = false,
    val bullets: List<TailoredBullet> = emptyList(),
)

@Serializable
data class TailoredResume(
    val fullName: String = "",
    val headline: String = "",
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val summary: String? = null,
    val skills: List<String> = emptyList(),
    val experience: List<TailoredExperience> = emptyList(),
    val education: List<Education> = emptyList(),
    val links: List<String> = emptyList(),
)

/** [needsUser] = we deliberately left it blank (pay, visa, demographics) or could not answer it. */
@Serializable
data class ScreeningAnswer(val question: String, val answer: String = "", val needsUser: Boolean = false, val note: String? = null)

@Serializable
data class TailoredApplication(val resume: TailoredResume, val coverLetter: String = "", val answers: List<ScreeningAnswer> = emptyList())

/** The job as the tailor sees it. */
data class JobForTailoring(val title: String, val company: String, val description: String)

data class GuardResult(val application: TailoredApplication, val removed: List<String>)

// ---- Questions ------------------------------------------------------------------------------

object Questions {
    fun defaults(company: String) = listOf(
        "Why do you want to work at $company?",
        "Why are you a good fit for this role?",
        "What is your notice period?",
        "What are your current and expected CTC?",
    )

    private val pay = Regex("\\b(ctc|salary|salaries|compensation|pay|package|lpa|stipend|remuneration)\\b", RegexOption.IGNORE_CASE)
    private val sensitive = Regex(
        "\\b(visa|sponsor\\w*|work permit|citizenship|nationality|disabilit\\w*|gender|race|ethnic\\w*|religio\\w*|caste|" +
            "age|date of birth|dob|marital|married|pregnan\\w*|criminal|convicted|arrest\\w*|veteran|sexual orientation)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val notice = Regex("\\bnotice( period)?\\b|\\bhow soon can you (join|start)\\b|\\bjoining\\b", RegexOption.IGNORE_CASE)

    fun isPay(q: String) = pay.containsMatchIn(q)
    fun isSensitive(q: String) = sensitive.containsMatchIn(q)
    fun isNotice(q: String) = notice.containsMatchIn(q)

    /**
     * Answers the questions we must never generate: pay and personal/legal ones stay blank for the user;
     * notice period comes only from what the user entered in preferences. Returns null for ordinary questions.
     */
    fun fixedAnswer(q: String, prefs: Preferences): ScreeningAnswer? = when {
        isPay(q) -> ScreeningAnswer(q, "", needsUser = true, note = "We never fill in pay. Add it yourself when you apply.")
        isSensitive(q) -> ScreeningAnswer(q, "", needsUser = true, note = "Personal or legal question: answer it yourself.")
        isNotice(q) -> prefs.noticePeriodDays?.let { ScreeningAnswer(q, "$it days") }
            ?: ScreeningAnswer(q, "", needsUser = true, note = "Add your notice period in preferences to fill this in.")
        else -> null
    }
}

// ---- Rule-based tailor ----------------------------------------------------------------------

/**
 * Tailoring without an LLM: it reorders, never rewords. Skills and bullets the JD mentions move up;
 * the cover letter and answers are built only from profile facts plus the job's title and company.
 */
object HeuristicTailor {
    fun tailor(profile: Profile, prefs: Preferences, job: JobForTailoring, questions: List<String>): TailoredApplication {
        val jdText = HeuristicScorer.normalizedText(job.title + "\n" + job.description)
        val jdTokens = jdText.split(' ').filter { it.length >= 3 }.toSet()
        fun relevance(text: String): Int {
            val skillHits = profile.skills.count { s -> HeuristicScorer.normalizedPhrase(s).let { it.isNotEmpty() && text.containsPhrase(it) && jdText.contains(" $it ") } }
            val tokenHits = HardFilter.tokens(text).filter { it.length >= 4 }.toSet().count { it in jdTokens }
            return skillHits * 3 + tokenHits
        }

        val skills = profile.skills.withIndex()
            .sortedWith(compareByDescending<IndexedValue<String>> { if (jdText.contains(" ${HeuristicScorer.normalizedPhrase(it.value)} ")) 1 else 0 }.thenBy { it.index })
            .map { it.value }

        val experience = profile.experience.mapIndexed { i, e ->
            TailoredExperience(
                sourceIndex = i,
                bullets = e.bullets.withIndex()
                    .sortedWith(compareByDescending<IndexedValue<String>> { relevance(it.value) }.thenBy { it.index })
                    .map { TailoredBullet(it.value, it.index) },
            )
        }

        val matched = profile.skills.filter { jdText.contains(" ${HeuristicScorer.normalizedPhrase(it)} ") }.take(3)
        val years = profile.totalExperienceYears?.let { formatYears(it) }
        val current = profile.experience.firstOrNull { it.current } ?: profile.experience.firstOrNull()
        val topBullets = experience.firstOrNull()?.bullets?.take(2)?.map { it.text.trimEnd('.') }.orEmpty()

        val summary = profile.summary ?: buildString {
            append(profile.headline.ifEmpty { "Engineer" })
            if (years != null) append(" with $years years of experience")
            if (matched.isNotEmpty()) append(if (years != null) " in " else " experienced in ").append(listSentence(matched))
            append('.')
        }

        val coverLetter = buildString {
            appendLine("Dear Hiring Team at ${job.company},")
            appendLine()
            append("I'm applying for the ${job.title} role.")
            if (current != null) append(" I'm currently ${current.title} at ${current.company}")
            if (years != null) append(if (current != null) ", with $years years of experience" else " I have $years years of experience")
            if (current != null || years != null) append('.')
            appendLine()
            if (topBullets.isNotEmpty()) {
                appendLine()
                appendLine("Recent work I'm proud of: ${topBullets.joinToString("; ")}.")
            }
            if (matched.isNotEmpty()) {
                appendLine()
                appendLine("The role's focus on ${listSentence(matched)} matches what I work with every day.")
            }
            appendLine()
            appendLine("I'd welcome the chance to talk about how I can help ${job.company}.")
            appendLine()
            appendLine("Best regards,")
            append(profile.fullName)
        }

        val answers = questions.map { q ->
            Questions.fixedAnswer(q, prefs) ?: when {
                q.contains("why", ignoreCase = true) && q.contains(job.company, ignoreCase = true) ->
                    ScreeningAnswer(
                        q,
                        buildString {
                            append("The ${job.title} role lines up with my experience")
                            if (matched.isNotEmpty()) append(" in ${listSentence(matched)}")
                            append(", and I'd like to bring that to ${job.company}.")
                        },
                    )
                q.contains("fit", ignoreCase = true) || q.contains("why you", ignoreCase = true) ->
                    ScreeningAnswer(
                        q,
                        buildString {
                            append("I'm a ${profile.headline.ifEmpty { "engineer" }}")
                            if (years != null) append(" with $years years of experience")
                            append('.')
                            topBullets.firstOrNull()?.let { append(" Recently: $it.") }
                        },
                    )
                else -> ScreeningAnswer(q, "", needsUser = true, note = "Write this one yourself, or tailor with Claude enabled.")
            }
        }

        return TailoredApplication(
            resume = TailoredResume(
                fullName = profile.fullName,
                headline = profile.headline,
                email = profile.email,
                phone = profile.phone,
                location = profile.location,
                summary = summary,
                skills = skills,
                experience = experience,
                education = profile.education,
                links = profile.links,
            ),
            coverLetter = coverLetter,
            answers = answers,
        )
    }

    private fun String.containsPhrase(phrase: String) = HeuristicScorer.normalizedText(this).contains(" $phrase ")

    internal fun listSentence(items: List<String>) = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    internal fun formatYears(y: Double) = if (y % 1.0 == 0.0) y.toInt().toString() else y.toString()
}

// ---- Claim guardrail ------------------------------------------------------------------------

/**
 * The tailoring guardrail. Whatever produced the draft (Claude or the rule-based tailor), this decides
 * what may go out: roles, titles, companies and dates always come from the profile; a bullet may be
 * reordered or reworded but not gain numbers or skills the profile doesn't have; skills must be the
 * user's own; sentences in the summary, cover letter and answers that claim unknown numbers or skills
 * are dropped. Pay and personal questions are always left to the user.
 */
object TailorGuard {
    /** A reworded bullet must keep this share of its content words from the original bullet... */
    private const val MIN_SOURCE_OVERLAP = 0.3
    /** ...and draw this share from anywhere in the profile, so rewording can't introduce new facts. */
    private const val MIN_PROFILE_GROUNDING = 0.8
    private val numberRegex = Regex("(?<![A-Za-z])\\d+(?:[.,]\\d+)?\\s*(?:\\+|%|x\\b|k\\b|m\\b|mn\\b|million|lakh|crore|cr\\b|b\\b|bn\\b)?", RegexOption.IGNORE_CASE)
    private val sentenceSplit = Regex("(?<=[.!?])\\s+|\\n+")
    /** Function words and common resume verbs carry no claim on their own, so rewording may use them freely. */
    private val neutral = setOf(
        "the", "and", "for", "with", "into", "from", "that", "this", "using", "over", "across", "our", "their", "via", "while",
        "led", "lead", "leading", "built", "build", "building", "shipped", "ship", "delivered", "deliver", "drove", "drive", "owned",
        "own", "designed", "design", "developed", "develop", "implemented", "implement", "improved", "improve", "created", "create",
        "managed", "manage", "launched", "launch", "worked", "work", "helped", "help", "used", "end", "multiple", "various", "key",
        "new", "high", "quality", "team", "teams", "product", "products", "features", "feature", "apps", "app", "application",
        "applications", "users", "user", "code", "production",
    )

    fun apply(
        draft: TailoredApplication,
        profile: Profile,
        prefs: Preferences,
        job: JobForTailoring,
        questions: List<String>,
    ): GuardResult {
        val removed = mutableListOf<String>()
        val profileText = profileFacts(profile)
        val profileNumbers = numbers(profileText)
        val profileSkills = profileSkills(profile, profileText)
        val jobNumbers = numbers(job.title + "\n" + job.description)
        val profileTokens = HardFilter.tokens(profileText).toSet()
        // The job's own title and company may appear in the letter and answers without counting as claims.
        val exempt = listOf(job.title, job.company).filter { it.isNotBlank() }

        // Roles: always the profile's own, in the profile's order; bullets checked against their source.
        val draftRoles = draft.resume.experience.filter { it.sourceIndex in profile.experience.indices }.associateBy { it.sourceIndex }
        val experience = profile.experience.mapIndexed { i, source ->
            val role = draftRoles[i]
            if (role != null && (role.title.isNotBlank() && !same(role.title, source.title) || role.company.isNotBlank() && !same(role.company, source.company))) {
                removed += "Kept your real title and company for ${source.company}"
            }
            val seen = mutableSetOf<Int>()
            val bullets = (role?.bullets ?: source.bullets.mapIndexed { j, b -> TailoredBullet(b, j) })
                .filter { it.sourceIndex in source.bullets.indices && seen.add(it.sourceIndex) }
                .map { b ->
                    val original = source.bullets[b.sourceIndex]
                    val text = b.text.trim().ifEmpty { original }
                    val problem = claimProblem(text, profileNumbers, profileSkills) ?: groundingProblem(text, original, profileTokens)
                    if (problem != null && !same(text, original)) {
                        removed += "A rewritten bullet at ${source.company} $problem; kept your original wording"
                        TailoredBullet(original, b.sourceIndex)
                    } else TailoredBullet(text, b.sourceIndex)
                }
                .ifEmpty { source.bullets.mapIndexed { j, b -> TailoredBullet(b, j) } }
            TailoredExperience(
                sourceIndex = i,
                title = source.title,
                company = source.company,
                startDate = source.startDate,
                endDate = source.endDate,
                current = source.current,
                bullets = annotate(bullets, source.bullets),
            )
        }

        // Skills: only the user's own, in the draft's order, then any the draft left out.
        val skills = draft.resume.skills.mapNotNull { s ->
            val own = profile.skills.firstOrNull { it.equals(s.trim(), ignoreCase = true) }
            if (own == null) removed += "Skill \"$s\" is not in your profile"
            own
        }.distinct().let { it + profile.skills.filter { s -> s !in it } }

        val headline = draft.resume.headline.takeIf { h ->
            h.isNotBlank() && (same(h, profile.headline) || profile.experience.any { same(h, it.title) })
        } ?: run {
            if (draft.resume.headline.isNotBlank() && !same(draft.resume.headline, profile.headline)) {
                removed += "Headline \"${draft.resume.headline}\" isn't one of your titles; kept \"${profile.headline}\""
            }
            profile.headline
        }

        val summary = draft.resume.summary?.let { filterSentences(it, profileNumbers, profileSkills, "summary", removed) }?.ifBlank { null }
            ?: profile.summary

        // Cover letter and answers may mention numbers from the JD (about the company), never unknown skills.
        val letterNumbers = profileNumbers + jobNumbers
        val coverLetter = filterSentences(draft.coverLetter, letterNumbers, profileSkills, "cover letter", removed, keepLines = true, exempt = exempt)

        val drafted = draft.answers.associateBy { it.question.trim().lowercase() }
        val answers = questions.map { q ->
            Questions.fixedAnswer(q, prefs) ?: drafted[q.trim().lowercase()]?.let { a ->
                val text = filterSentences(a.answer, letterNumbers, profileSkills, "answer", removed, exempt = exempt)
                if (text.isBlank()) ScreeningAnswer(q, "", needsUser = true, note = a.note ?: "Write this one yourself.")
                else ScreeningAnswer(q, text, needsUser = false, note = a.note)
            } ?: ScreeningAnswer(q, "", needsUser = true, note = "Write this one yourself.")
        }

        return GuardResult(
            TailoredApplication(
                resume = TailoredResume(
                    fullName = profile.fullName,
                    headline = headline,
                    email = profile.email,
                    phone = profile.phone,
                    location = profile.location,
                    summary = summary,
                    skills = skills,
                    experience = experience,
                    education = profile.education,
                    links = profile.links,
                ),
                coverLetter = coverLetter,
                answers = answers,
            ),
            removed.distinct(),
        )
    }

    /** Warnings for text the user typed themselves: we don't block their edits, we flag unverified claims. */
    fun warnings(app: TailoredApplication, profile: Profile): List<String> {
        val profileText = profileFacts(profile)
        val numbersOk = numbers(profileText)
        val skillsOk = profileSkills(profile, profileText)
        val texts = app.resume.experience.flatMap { it.bullets.map { b -> b.text } } + listOfNotNull(app.resume.summary)
        return texts.flatMap { t -> sentences(t) }.mapNotNull { s ->
            claimProblem(s, numbersOk, skillsOk)?.let { "\"${s.take(80)}\" $it. Make sure it's true." }
        }.distinct()
    }

    private fun annotate(bullets: List<TailoredBullet>, originals: List<String>): List<TailoredBullet> =
        bullets.mapIndexed { position, b ->
            val original = originals[b.sourceIndex]
            when {
                !same(b.text, original) -> b.copy(change = Change.REWORDED, original = original)
                position < b.sourceIndex -> b.copy(change = Change.MOVED_UP, original = null)
                else -> b.copy(change = Change.UNCHANGED, original = null)
            }
        }

    private fun filterSentences(
        text: String,
        okNumbers: Set<String>,
        okSkills: Set<String>,
        where: String,
        removed: MutableList<String>,
        keepLines: Boolean = false,
        exempt: List<String> = emptyList(),
    ): String {
        if (keepLines) {
            // Keep the letter's paragraph structure: filter sentence by sentence inside each line.
            return text.lines().joinToString("\n") { line ->
                if (line.isBlank()) line else filterSentences(line, okNumbers, okSkills, where, removed, exempt = exempt)
            }.replace(Regex("\n{3,}"), "\n\n").trim()
        }
        return sentences(text).filter { s ->
            val checked = exempt.fold(s) { acc, e -> acc.replace(e, " ", ignoreCase = true) }
            val problem = claimProblem(checked, okNumbers, okSkills)
            if (problem != null) removed += "Removed a sentence from the $where that $problem: \"${s.take(80)}\""
            problem == null
        }.joinToString(" ").trim()
    }

    private fun sentences(text: String) = text.split(sentenceSplit).map { it.trim() }.filter { it.isNotEmpty() }

    /** Why a piece of text claims something the profile can't back, or null when it's fine. */
    private fun claimProblem(text: String, okNumbers: Set<String>, okSkills: Set<String>): String? {
        numbers(text).firstOrNull { it !in okNumbers }?.let { return "added the number $it" }
        SkillDictionary.findIn(text).firstOrNull { it.lowercase() !in okSkills }?.let { return "claimed $it" }
        return null
    }

    private fun numbers(text: String): Set<String> =
        numberRegex.findAll(text).map { it.value.lowercase().replace(" ", "").replace(",", "") }.toSet()

    private fun profileSkills(profile: Profile, profileText: String): Set<String> =
        (profile.skills.map { it.lowercase() } + SkillDictionary.findIn(profileText).map { it.lowercase() }).toSet()

    private fun profileFacts(p: Profile): String = buildString {
        appendLine(HeuristicScorer.profileText(p))
        p.totalExperienceYears?.let { appendLine("${HeuristicTailor.formatYears(it)} years") }
        p.experience.forEach { appendLine("${it.company} ${it.startDate.orEmpty()} ${it.endDate.orEmpty()}") }
        p.education.forEach { appendLine("${it.institution} ${it.degree.orEmpty()} ${it.field.orEmpty()} ${it.endYear ?: ""}") }
    }

    private fun groundingProblem(text: String, original: String, profileTokens: Set<String>): String? {
        val content = HardFilter.tokens(text).filter { it.length >= 3 && it !in neutral }
        if (content.isEmpty()) return null
        val orig = HardFilter.tokens(original).toSet()
        val fromSource = content.count { it in orig }.toDouble() / content.size
        val grounded = content.count { it in profileTokens }.toDouble() / content.size
        return when {
            fromSource < MIN_SOURCE_OVERLAP -> "no longer matched the original"
            grounded < MIN_PROFILE_GROUNDING -> "added details that aren't in your profile"
            else -> null
        }
    }

    private fun same(a: String, b: String) = HeuristicScorer.normalizedPhrase(a) == HeuristicScorer.normalizedPhrase(b)
}

/** JD skills the tailored resume shows, and JD skills the profile lacks: the sidebar on the review screen. */
object Keywords {
    fun covered(app: TailoredApplication, job: JobForTailoring): List<String> {
        val jdSkills = SkillDictionary.findIn(job.title + "\n" + job.description)
        val resumeText = HeuristicScorer.normalizedText(
            (app.resume.skills + app.resume.experience.flatMap { e -> e.bullets.map { it.text } + e.title } + listOfNotNull(app.resume.summary)).joinToString("\n"),
        )
        return jdSkills.filter { resumeText.contains(" ${HeuristicScorer.normalizedPhrase(it)} ") }
    }

    fun missing(profile: Profile, job: JobForTailoring): List<String> {
        val profileText = HeuristicScorer.normalizedText(HeuristicScorer.profileText(profile))
        return SkillDictionary.findIn(job.title + "\n" + job.description).filter { !profileText.contains(" ${HeuristicScorer.normalizedPhrase(it)} ") }
    }
}
