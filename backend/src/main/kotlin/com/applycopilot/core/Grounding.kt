package com.applycopilot.core

/**
 * The "never invent" guardrail. Anything a parser returns that cannot be traced back to the
 * resume text is removed (or blanked for the user to fill), and listed in [GroundingResult.removed]
 * so the review screen can say what was dropped.
 */
data class GroundingResult(val profile: Profile, val removed: List<String>)

object Grounding {
    private const val BULLET_OVERLAP = 0.7

    fun ground(profile: Profile, sourceText: String): GroundingResult {
        val source = " ${normalize(sourceText)} "
        val sourceTokens = source.split(' ').filter { it.isNotEmpty() }.toSet()
        val removed = mutableListOf<String>()

        // Whole-word containment: ".NET" must not match "internet".
        fun inSource(value: String): Boolean = normalize(value).let { it.isNotEmpty() && source.contains(" $it ") }

        val fullName = if (profile.fullName.isEmpty() || inSource(profile.fullName)) profile.fullName else {
            removed += "Name \"${profile.fullName}\" was not found in the resume"; ""
        }
        val headline = if (profile.headline.isEmpty() || inSource(profile.headline)) profile.headline else {
            removed += "Headline \"${profile.headline}\" was not found in the resume"; ""
        }

        val experience = profile.experience.mapNotNull { e ->
            if (e.company.isNotEmpty() && !inSource(e.company)) {
                removed += "Experience at \"${e.company}\" was not found in the resume"
                return@mapNotNull null
            }
            val title = if (e.title.isEmpty() || inSource(e.title)) e.title else {
                removed += "Title \"${e.title}\" was not found in the resume"; ""
            }
            val bullets = e.bullets.filter { bullet ->
                val ok = overlap(bullet, sourceTokens) >= BULLET_OVERLAP
                if (!ok) removed += "A bullet under \"${e.company.ifEmpty { e.title }}\" did not match the resume"
                ok
            }
            e.copy(title = title, bullets = bullets)
        }

        val education = profile.education.mapNotNull { ed ->
            if (ed.institution.isNotEmpty() && !inSource(ed.institution)) {
                removed += "Education at \"${ed.institution}\" was not found in the resume"
                null
            } else ed
        }

        val skills = profile.skills.filter { skill ->
            val ok = inSource(skill)
            if (!ok) removed += "Skill \"$skill\" was not found in the resume"
            ok
        }

        val email = profile.email?.takeIf { inSource(it) }
        val phone = profile.phone?.takeIf { digitsOf(it).isNotEmpty() && digitsOf(sourceText).contains(digitsOf(it).takeLast(10)) }
        val links = profile.links.filter { link -> inSource(link.removePrefix("https://").removePrefix("http://")) }

        return GroundingResult(
            profile.copy(
                fullName = fullName,
                headline = headline,
                email = email,
                phone = phone,
                experience = experience,
                education = education,
                skills = skills,
                links = links,
            ),
            removed,
        )
    }

    /** Lower-case, keep letters, digits, + and #, collapse everything else to single spaces. */
    internal fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^\\p{L}\\p{N}+#]+"), " ").trim()

    private fun overlap(bullet: String, sourceTokens: Set<String>): Double {
        val tokens = normalize(bullet).split(' ').filter { it.length >= 3 && it !in stopWords }
        if (tokens.isEmpty()) return 1.0
        return tokens.count { it in sourceTokens }.toDouble() / tokens.size
    }

    private fun digitsOf(value: String) = value.filter { it.isDigit() }

    private val stopWords = setOf("the", "and", "for", "with", "into", "from", "that", "this", "using", "over", "across")
}
