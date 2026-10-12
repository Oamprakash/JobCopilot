package com.applycopilot.matching

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import kotlinx.serialization.Serializable

/**
 * One change to the preferences and how many more jobs it would let through.
 * [kind] is "location" (add [value] to locations), "role" (add [value] to roles) or "remote" (include remote work).
 */
@Serializable
data class Suggestion(val kind: String, val value: String, val count: Int)

/**
 * Finds the single preference changes that would bring the most relevant jobs into the feed.
 * Counts are exact: each candidate change is re-run through [HardFilter] against the jobs that failed.
 * "Relevant" means the job mentions at least two of the user's skills, so a broad change such as
 * adding "Software Engineer" is not credited with hundreds of unrelated jobs.
 */
object Suggestions {
    private const val MAX_PER_KIND = 3

    private val families: List<Pair<Regex, String>> = listOf(
        "engineering manager|manager,? (software )?engineering|head of engineering" to "Engineering Manager",
        "full[ -]?stack" to "Full Stack Engineer",
        "back[ -]?end|server[ -]side" to "Backend Engineer",
        "front[ -]?end|\\bui engineer|\\bweb (developer|engineer)" to "Frontend Engineer",
        "\\bios\\b|\\bmobile\\b|\\bandroid\\b|flutter|react native" to "Mobile Engineer",
        "platform engineer" to "Platform Engineer",
        "devops|\\bsre\\b|site reliability" to "DevOps Engineer",
        "data engineer" to "Data Engineer",
        "machine learning|\\bml engineer|\\bai engineer" to "Machine Learning Engineer",
        "software (development )?engineer|\\bsde\\b|software developer" to "Software Engineer",
    ).map { (pattern, label) -> Regex(pattern, RegexOption.IGNORE_CASE) to label }

    fun roleFamily(title: String): String? = families.firstOrNull { it.first.containsMatchIn(title) }?.second

    fun compute(failed: List<JobForMatching>, profile: Profile, prefs: Preferences): List<Suggestion> {
        val needed = minOf(2, profile.skills.size).coerceAtLeast(1)
        val skillPhrases = profile.skills.map { HeuristicScorer.normalizedPhrase(it) }.filter { it.isNotEmpty() }
        val relevant = failed.filter { job ->
            val text = HeuristicScorer.normalizedText(job.title + "\n" + job.description)
            skillPhrases.count { text.contains(" $it ") } >= needed
        }
        if (relevant.isEmpty()) return emptyList()

        fun gain(changed: Preferences) = relevant.count { HardFilter.evaluate(it, profile, changed).pass }

        val knownCities = prefs.locations.map { it.lowercase() }
        // Suggest places in the countries the user already looks in (India when they haven't said), not a move abroad.
        val countries = prefs.locations.mapNotNull { Locations.countryOf(it) }.toSet().ifEmpty { setOf("in") }
        val cityCandidates = relevant.flatMap { Locations.citiesIn(it.location) }
            .filter { city -> Locations.countryOf(city) in countries }
            .filter { city -> knownCities.none { Locations.cityMatches(it, city) } }
            .distinct()
        val locations = cityCandidates
            .map { Suggestion("location", it, gain(prefs.copy(locations = prefs.locations + it))) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .take(MAX_PER_KIND)

        val knownRoles = prefs.roles.map { it.lowercase() }
        val roles = relevant.mapNotNull { roleFamily(it.title) }
            .distinct()
            .filter { it.lowercase() !in knownRoles }
            .map { Suggestion("role", it, gain(prefs.copy(roles = prefs.roles.ifEmpty { listOf(profile.headline) } + it))) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .take(MAX_PER_KIND)

        val remote = if (prefs.remoteMode == RemoteMode.ONSITE || prefs.remoteMode == RemoteMode.HYBRID) {
            listOf(Suggestion("remote", "Remote in India", gain(prefs.copy(remoteMode = RemoteMode.ANY)))).filter { it.count > 0 }
        } else emptyList()

        return (locations + roles + remote).sortedByDescending { it.count }
    }
}
