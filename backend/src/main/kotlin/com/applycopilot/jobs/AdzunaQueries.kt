package com.applycopilot.jobs

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import com.applycopilot.matching.HardFilter
import com.applycopilot.matching.Locations

/**
 * One aggregator search, lowercased: all of [what]'s words, in [where] (a city), or anywhere in [country]
 * (ISO code) when blank.
 */
data class SearchQuery(val what: String, val where: String, val country: String = "in")

/**
 * Turns preferences into a few aggregator searches. Each target role becomes its distinctive words, the same
 * ones the role filter keys on ("Senior Android Engineer" → "android"), so the four Android titles a user
 * might list share one search. Each search runs per city, in that city's country; a country choice ("Malaysia")
 * searches the whole country. Places in countries the aggregator doesn't cover are left to the job boards.
 * Remote-only users, and users with no locations, search the whole of [home].
 */
object SearchQueries {
    const val MAX_ROLES = 3
    const val MAX_CITIES = 5

    /** Countries Adzuna's API covers (developer.adzuna.com). */
    val ADZUNA_COUNTRIES = setOf("at", "au", "be", "br", "ca", "ch", "de", "es", "fr", "gb", "in", "it", "mx", "nl", "nz", "pl", "sg", "us", "za")

    fun forUser(profile: Profile, prefs: Preferences, home: String = "in", supported: Set<String> = ADZUNA_COUNTRIES): List<SearchQuery> {
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
        val wheres: List<Pair<String, String>> = when {
            prefs.remoteMode == RemoteMode.REMOTE || cities.isEmpty() -> listOf("" to home)
            else -> cities.distinctBy { it.lowercase() }.map { place ->
                val country = Locations.countryOf(place) ?: home // a city we don't know is assumed to be at home
                (if (Locations.isCountry(place)) "" else place) to country
            }.filter { (_, country) -> country in supported }.distinct().take(MAX_CITIES)
        }
        return keywords.flatMap { what -> wheres.map { (where, country) -> SearchQuery(what.lowercase(), where.lowercase(), country) } }
    }

    /** Every user's searches, most wanted first, without repeats. */
    fun merge(perUser: List<List<SearchQuery>>): List<SearchQuery> =
        perUser.flatten()
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
}
