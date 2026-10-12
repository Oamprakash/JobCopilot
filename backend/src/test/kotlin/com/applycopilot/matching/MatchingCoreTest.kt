package com.applycopilot.matching

import com.applycopilot.core.Experience
import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchingCoreTest {
    private val profile = Profile(
        fullName = "Oamprakash R",
        headline = "Lead Android Developer",
        totalExperienceYears = 12.0,
        skills = listOf("Kotlin", "Java", "Jetpack Compose", "Coroutines", "MVVM", "Firebase", "Team leadership"),
        experience = listOf(
            Experience(
                title = "Lead Android Developer", company = "Movate", current = true,
                bullets = listOf("Lead a team of 20+ Android engineers", "Built Jetpack Compose UI with a shared design system"),
            ),
        ),
    )
    private val prefs = Preferences(
        roles = listOf("Senior Android Engineer"),
        locations = listOf("Chennai", "Bangalore"),
        remoteMode = RemoteMode.ANY,
        skipCompanies = listOf("Movate"),
    )

    private fun job(
        title: String,
        location: String = "Bengaluru, India",
        description: String = "Build Android apps with Kotlin and Jetpack Compose. 5+ years of experience.",
        company: String = "Acme",
        remote: Boolean = false,
    ) = JobForMatching(UUID.randomUUID(), company, title, location, remote, description)

    // ---- Html -----------------------------------------------------------------------------

    @Test
    fun htmlToTextHandlesGreenhouseEscapedHtml() {
        val escaped = "&lt;h2&gt;About &amp;amp; you&lt;/h2&gt;&lt;p&gt;We&amp;rsquo;re hiring.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Kotlin&lt;/li&gt;&lt;li&gt;Compose&lt;/li&gt;&lt;/ul&gt;"
        assertEquals("About & you\nWe’re hiring.\n• Kotlin\n• Compose", Html.toText(escaped))
    }

    @Test
    fun htmlToTextHandlesPlainHtmlAndNumericEntities() {
        assertEquals("Line one\nLine two – done", Html.toText("<p>Line&nbsp;one<br/>Line two &#8211; done</p><script>x()</script>"))
    }

    // ---- Locations ------------------------------------------------------------------------

    @Test
    fun cityAliasesMatch() {
        assertTrue(Locations.cityMatches("Bangalore", "Bengaluru-VTP, India"))
        assertTrue(Locations.cityMatches("bengaluru", "Bangalore, Karnataka"))
        assertTrue(Locations.cityMatches("Gurgaon", "Gurugram"))
        assertFalse(Locations.cityMatches("Chennai", "Bengaluru, India"))
        assertFalse(Locations.cityMatches("Pune", "Punekar Street, Seattle"))
    }

    @Test
    fun remoteMustBeOpenToIndia() {
        assertTrue(Locations.remoteForIndia(job("x", "Remote - India")))
        assertTrue(Locations.remoteForIndia(job("x", "Remote, APAC")))
        assertFalse(Locations.remoteForIndia(job("x", "Remote from the US")))
        assertFalse(Locations.remoteForIndia(job("x", "Remote", remote = true)))
        assertFalse(Locations.remoteForIndia(job("x", "Bengaluru, India")))
    }

    // ---- Hard filter ----------------------------------------------------------------------

    @Test
    fun keepsMatchingAndroidRoleInPreferredCity() {
        assertEquals(FilterResult(true, "ok"), HardFilter.evaluate(job("Senior Android Engineer"), profile, prefs))
        assertTrue(HardFilter.evaluate(job("Mobile Engineer (Kotlin)"), profile, prefs).pass, "mobile is a synonym for android")
    }

    @Test
    fun dropsOtherRolesLocationsAndSkippedCompanies() {
        assertEquals("role", HardFilter.evaluate(job("Account Executive"), profile, prefs).reason)
        assertEquals("role", HardFilter.evaluate(job("Backend Engineer"), profile, prefs).reason)
        assertEquals("location", HardFilter.evaluate(job("Android Engineer", "Dublin"), profile, prefs).reason)
        assertEquals("location", HardFilter.evaluate(job("Android Engineer", "Remote from the US"), profile, prefs).reason)
        assertEquals("company on skip list", HardFilter.evaluate(job("Android Engineer", company = "Movate Technologies"), profile, prefs).reason)
    }

    @Test
    fun remoteModeIsRespected() {
        val remoteIndia = job("Android Engineer", "Remote - India")
        assertTrue(HardFilter.evaluate(remoteIndia, profile, prefs).pass)
        assertFalse(HardFilter.evaluate(remoteIndia, profile, prefs.copy(remoteMode = RemoteMode.ONSITE)).pass)
        assertFalse(HardFilter.evaluate(job("Android Engineer"), profile, prefs.copy(remoteMode = RemoteMode.REMOTE)).pass)
        assertTrue(HardFilter.evaluate(remoteIndia, profile, prefs.copy(remoteMode = RemoteMode.REMOTE)).pass)
    }

    @Test
    fun noCitiesMeansAnywhereInIndia() {
        val p = prefs.copy(locations = emptyList(), remoteMode = RemoteMode.HYBRID)
        assertTrue(HardFilter.evaluate(job("Android Engineer", "Hyderabad"), profile, p).pass)
        assertFalse(HardFilter.evaluate(job("Android Engineer", "Berlin, Germany"), profile, p).pass)
    }

    @Test
    fun experienceBand() {
        assertEquals(5 to null, HardFilter.requiredYears("You have 5+ years of experience"))
        assertEquals(3 to 5, HardFilter.requiredYears("3-5 years building apps; 2+ years with Compose"))
        assertNull(HardFilter.requiredYears("Join a 2M user product"))

        assertEquals("needs 15+ years", HardFilter.evaluate(job("Android Architect", description = "15+ years required"), profile, prefs).reason)
        assertEquals("asks for 1–3 years", HardFilter.evaluate(job("Android Developer", description = "1-3 years experience"), profile, prefs).reason)
        assertEquals("entry-level role", HardFilter.evaluate(job("Android Intern"), profile, prefs).reason)
        assertEquals("junior role", HardFilter.evaluate(job("Junior Android Developer"), profile, prefs).reason)
    }

    @Test
    fun genericRolesNeedAGenericWord() {
        val p = prefs.copy(roles = listOf("Software Engineer"))
        assertTrue(HardFilter.evaluate(job("Software Engineer, Payments"), profile, p).pass)
        assertFalse(HardFilter.evaluate(job("Account Executive"), profile, p).pass)
    }

    // ---- Ranking and scoring --------------------------------------------------------------

    @Test
    fun lexicalRankerPrefersCloserJobs() {
        val close = job("Senior Android Engineer", description = "Kotlin, Jetpack Compose, Coroutines, MVVM, Firebase. Lead Android engineers.")
        val far = job("Android Engineer", description = "Java and XML layouts for a banking app.")
        val ranked = LexicalRanker.rank(listOf(far, close), profile)
        assertEquals(close.id, ranked.first().first.id)
        assertTrue(ranked.first().second > ranked.last().second)
        assertTrue(LexicalRanker.rank(emptyList(), profile).isEmpty())
    }

    @Test
    fun heuristicScoreExplainsItself() {
        val j = job(
            "Senior Android Engineer",
            "Chennai, India",
            "We need Kotlin, Jetpack Compose and Coroutines. Kotlin Multiplatform is a plus. 8+ years of experience.",
        )
        val fit = HeuristicScorer.score(j, profile, prefs, similarity = 0.4)
        assertTrue(fit.score in 70..100, "score ${fit.score}")
        assertEquals("Kotlin, Jetpack Compose and Coroutines match your skills", fit.reasons[0])
        assertTrue(fit.reasons.contains("Title fits your target role"), "${fit.reasons}")
        assertTrue(fit.reasons.contains("Asks 8+ years; you have 12"), "${fit.reasons}")
        assertEquals(listOf("Kotlin Multiplatform"), fit.missingSkills)

        val weak = HeuristicScorer.score(job("Android Engineer", description = "Flutter, Dart, Swift and SwiftUI"), profile, prefs, 0.05)
        assertTrue(weak.score < fit.score)
        assertTrue(weak.missingSkills.containsAll(listOf("Flutter", "Dart", "Swift")), "${weak.missingSkills}")
    }

    // ---- Widen-search suggestions ---------------------------------------------------------

    @Test
    fun suggestsCitiesRolesAndRemoteThatWouldAddRelevantJobs() {
        val p = prefs.copy(locations = listOf("Chennai"), remoteMode = RemoteMode.HYBRID, skipCompanies = emptyList())
        val failed = listOf(
            job("Android Engineer", "Hyderabad, India", "Kotlin and Coroutines"),
            job("Senior Android Engineer", "Hyderabad", "Kotlin and Java"),
            job("Android Developer", "Pune, India", "Kotlin, Java"),
            job("Backend Engineer", "Chennai", "Kotlin, Java and Spring"),
            job("Android Engineer", "Remote - India", "Kotlin and Jetpack Compose"),
            job("Sales Manager", "Hyderabad", "Sell things"),
            job("Backend Engineer", "Berlin, Germany", "Kotlin, Java"),
        )
        assertTrue(failed.none { HardFilter.evaluate(it, profile, p).pass })
        assertEquals(
            listOf(
                Suggestion("location", "Hyderabad", 2),
                Suggestion("location", "Pune", 1),
                Suggestion("role", "Backend Engineer", 1),
                Suggestion("remote", "Remote in India", 1),
            ),
            Suggestions.compute(failed, profile, p),
        )
    }

    @Test
    fun noSuggestionsForCitiesAlreadyChosenOrIrrelevantJobs() {
        val p = prefs.copy(locations = listOf("Bangalore"), remoteMode = RemoteMode.ANY, skipCompanies = emptyList())
        val failed = listOf(
            job("Account Executive", "Bengaluru, India", "Kotlin and Java"),
            job("Sales Manager", "Mumbai", "Sell things"),
        )
        // The Bengaluru job fails on role, not location; Mumbai is irrelevant to the profile.
        assertEquals(emptyList<Suggestion>(), Suggestions.compute(failed, profile, p).filter { it.kind == "location" })
        assertEquals(listOf("Bengaluru"), Locations.citiesIn("Bangalore, KA"))
        assertEquals("Backend Engineer", Suggestions.roleFamily("Senior Backend Engineer"))
        assertEquals("Engineering Manager", Suggestions.roleFamily("Engineering Manager - Platform"))
    }

    @Test
    fun autocompleteCountsJobsTheFilterWouldLetThrough() {
        val jobs = listOf(
            OpenJob("Senior Android Engineer", "Bangalore, KA"),
            OpenJob("Mobile Developer (iOS)", "Bengaluru"),
            OpenJob("Backend Engineer", "Chennai, India"),
            OpenJob("Account Executive", "Mumbai"),
        )
        val options = Autocomplete.options(jobs)
        val city = options.locations.associate { it.value to it.count }
        assertEquals(2, city["Bengaluru"])
        assertEquals(1, city["Chennai"])
        assertEquals(0, city["Pune"])
        assertEquals("Bengaluru", options.locations.first().value)
        assertEquals(Locations.cities.size, options.locations.size)
        assertEquals(listOf("Bangalore", "Blr"), options.locations.first().aliases)

        val role = options.roles.associate { it.value to it.count }
        assertEquals(2, role["Android Engineer"]) // android → mobile synonym, as in HardFilter
        assertEquals(1, role["Backend Engineer"])
        assertEquals(0, role["Data Engineer"])
        assertTrue(options.roles.zipWithNext().all { (a, b) -> a.count >= b.count })
    }
}
