package com.applycopilot.jobs

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchQueriesTest {
    private val profile = Profile(headline = "Android Lead")

    @Test
    fun rolesShareASearchWhenTheirDistinctiveWordsMatch() {
        val prefs = Preferences(
            roles = listOf("Lead Android Engineer", "Android Developer", "Android Engineer", "Senior Android Engineer"),
            locations = listOf("Bengaluru", "Chennai", "Hyderabad"),
        )
        assertEquals(
            listOf(SearchQuery("android", "bengaluru"), SearchQuery("android", "chennai"), SearchQuery("android", "hyderabad")),
            SearchQueries.forUser(profile, prefs),
        )
    }

    @Test
    fun genericRolesRemoteModeAndLimits() {
        // A role of only generic words searches as written; remote-only searches the whole country.
        assertEquals(
            listOf(SearchQuery("software engineer", "")),
            SearchQueries.forUser(profile, Preferences(roles = listOf("Software Engineer"), locations = listOf("Pune"), remoteMode = RemoteMode.REMOTE)),
        )
        // No roles: the profile headline stands in. No cities: the whole country.
        assertEquals(listOf(SearchQuery("android", "")), SearchQueries.forUser(profile, Preferences()))
        // "Remote" as a location is not a city.
        assertEquals(listOf(SearchQuery("android", "pune")), SearchQueries.forUser(profile, Preferences(locations = listOf("Remote", "Pune"))))

        val many = Preferences(
            roles = listOf("Android Engineer", "iOS Engineer", "Backend Engineer", "Data Engineer"),
            locations = listOf("A", "B", "C", "D", "E", "F", "a"),
        )
        val queries = SearchQueries.forUser(profile, many)
        assertEquals(SearchQueries.MAX_ROLES * SearchQueries.MAX_CITIES, queries.size)
        assertEquals(listOf("android", "ios", "backend"), queries.map { it.what }.distinct())
    }

    @Test
    fun mergeOrdersByDemand() {
        val a = SearchQuery("android", "chennai")
        val b = SearchQuery("android", "pune")
        assertEquals(listOf(b, a), SearchQueries.merge(listOf(listOf(a, b), listOf(b))))
    }
}
