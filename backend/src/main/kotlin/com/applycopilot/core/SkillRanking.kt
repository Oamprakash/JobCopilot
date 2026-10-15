package com.applycopilot.core

/**
 * Orders a profile's skills by how central they are to the person's work, so the profile can show the top ones
 * first and tailored resumes can lead with them. A skill counts for each mention in the headline, summary and
 * role titles and bullets, with the most recent roles weighted most. Skills nothing mentions keep their order,
 * after the mentioned ones. Nothing is added or removed.
 */
object SkillRanking {
    fun rank(profile: Profile): List<String> {
        if (profile.skills.size < 2) return profile.skills
        val head = listOfNotNull(profile.headline, profile.summary).joinToString("\n").lowercase()
        val roles = profile.experience.map { e -> (listOf(e.title) + e.bullets).joinToString("\n").lowercase() }
        fun score(skill: String): Int {
            val s = skill.trim().lowercase()
            if (s.isEmpty()) return 0
            val pattern = Regex("(?<![a-z0-9])${Regex.escape(s)}(?![a-z0-9+#])")
            var total = pattern.findAll(head).count() * 3
            roles.forEachIndexed { i, text ->
                val weight = when (i) { 0 -> 3; 1 -> 2; else -> 1 }
                total += pattern.findAll(text).count() * weight
            }
            return total
        }
        return profile.skills.withIndex()
            .sortedWith(compareByDescending<IndexedValue<String>> { score(it.value) }.thenBy { it.index })
            .map { it.value }
    }
}
