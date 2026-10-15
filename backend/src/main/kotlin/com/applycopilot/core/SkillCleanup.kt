package com.applycopilot.core

/**
 * Tidies the skills read from a resume: drops what isn't a skill (category labels, sentence fragments, dates,
 * "5 years"), merges duplicates, and keeps the [MAX_PARSED_SKILLS] most-used ones (see [SkillRanking]).
 * Only parsing uses this; skills the user adds by hand are kept as they are.
 */
object SkillCleanup {
    /** A real resume lists 10 to 30 skills; past that it's usually keywords the reader picked up. */
    const val MAX_PARSED_SKILLS = 30

    private val labels = setOf(
        "skills", "technical skills", "key skills", "core skills", "languages", "programming languages", "frameworks",
        "libraries", "tools", "tools and technologies", "technologies", "databases", "database", "platforms", "others",
        "other", "misc", "miscellaneous", "cloud", "testing", "architecture", "methodologies", "soft skills", "ide", "ides",
        "version control", "operating systems", "os", "web", "mobile", "frontend", "backend", "devops",
    )
    private val sentenceWords = setOf("the", "of", "with", "for", "to", "in", "on", "at", "from", "by", "using", "and", "a", "an", "my", "i", "we", "our")
    private val dateLike = Regex("""^(?:\d{1,2}[/-])?\d{4}$|^\d+(?:\.\d+)?\+?\s*(?:years?|yrs?|months?)\b.*""", RegexOption.IGNORE_CASE)

    fun isSkill(raw: String): Boolean {
        val s = raw.trim().trim('.', ',', ';', ':', '-', '–', '•', '*')
        if (s.isEmpty() || s.length > ProfileRules.MAX_SKILL_LENGTH) return false
        if (s.length == 1 && !s[0].isUpperCase()) return false
        if (s.lowercase() in labels) return false
        if (raw.trim().endsWith(":")) return false
        if (s.all { it.isDigit() || !it.isLetterOrDigit() }) return false
        if (dateLike.matches(s)) return false
        val words = s.split(Regex("\\s+"))
        if (words.size > 4) return false
        if (words.size >= 3 && words.any { it.lowercase() in sentenceWords }) return false
        // A lowercase start on a multi-word item is usually the middle of a wrapped sentence.
        if (words.size >= 2 && s[0].isLowerCase()) return false
        return true
    }

    fun clean(profile: Profile): Profile {
        val tidy = profile.skills.map { it.trim().trim('.', ',', ';', '•', '*').trim() }
            .filter(::isSkill)
            .distinctBy { it.lowercase().replace(Regex("[\\s.-]"), "") }
        val ranked = SkillRanking.rank(profile.copy(skills = tidy))
        return profile.copy(skills = ranked.take(MAX_PARSED_SKILLS))
    }
}
