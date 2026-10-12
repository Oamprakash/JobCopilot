package com.applycopilot.jobs

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import com.applycopilot.matching.HardFilter

/** One aggregator search, lowercased: all of [what]'s words, in [where] (a city), or anywhere in the country when blank. */
data class SearchQuery(val what: String, val where: String)

/**
 * Turns preferences into a few aggregator searches. Each target role becomes its distinctive words, the same
 * ones the role filter keys on ("Senior Android Engineer" → "android"), so the four Android titles a user
 * might list share one search. Each search runs per city; remote-only users search the whole country.
 */
object SearchQueries {
    const val MAX_ROLES = 3
    const val MAX_CITIES = 5

    fun forUser(profile: Profile, prefs: Preferences): List<SearchQuery> {
        val roles = prefs.roles.ifEmpty { listOf(profile.headline) }.filter { it.isNotBlank() }
        val keywords = roles
            .map { role ->
                val core = HardFilter.coreRoleTokens(Preferences(roles = listOf(role)), profile).sorted()
                // A role made only of generic words ("Software Engineer") searches as written.
                core.ifEmpty { HardFilter.tokens(role) }.joinToString(" ")
            }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ROLES)
        val cities = prefs.locations.filterNot { it.equals("remote", ignoreCase = true) }.map { it.trim() }.filter { it.isNotEmpty() }
        val wheres = when {
            prefs.remoteMode == RemoteMode.REMOTE || cities.isEmpty() -> listOf("")
            else -> cities.distinctBy { it.lowercase() }.take(MAX_CITIES)
        }
        return keywords.flatMap { what -> wheres.map { SearchQuery(what.lowercase(), it.lowercase()) } }
    }

    /** Every user's searches, most wanted first, without repeats. */
    fun merge(perUser: List<List<SearchQuery>>): List<SearchQuery> =
        perUser.flatten()
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
}
