package com.applycopilot.matching

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SponsorshipTest {
    @Test
    fun readsWhatTheDescriptionSays() {
        assertEquals(Sponsorship.OFFERED, Sponsorship.visa("We offer visa sponsorship and relocation support to Berlin."))
        assertEquals(Sponsorship.OFFERED, Sponsorship.visa("Visa sponsorship is available for this role."))
        assertEquals(Sponsorship.OFFERED, Sponsorship.visa("We can sponsor your work permit."))
        assertEquals(Sponsorship.OFFERED, Sponsorship.visa("We are a recognised sponsor for the highly skilled migrant scheme."))
        assertEquals(Sponsorship.NOT_OFFERED, Sponsorship.visa("Unfortunately we are unable to provide visa sponsorship."))
        assertEquals(Sponsorship.NOT_OFFERED, Sponsorship.visa("You must have the right to work in the UK."))
        assertEquals(Sponsorship.NOT_OFFERED, Sponsorship.visa("Candidates must be eligible to work in Canada without sponsorship."))
        assertEquals(Sponsorship.NOT_OFFERED, Sponsorship.visa("Sponsorship is not available for this position."))
        // A clear "no" wins over a general "yes" in the same text.
        assertEquals(Sponsorship.NOT_OFFERED, Sponsorship.visa("We sponsor visas for some teams. This role: no visa sponsorship."))
        assertNull(Sponsorship.visa("Build our Android app in Kotlin and Jetpack Compose."))

        assertTrue(Sponsorship.relocation("Relocation package and visa support included."))
        assertTrue(Sponsorship.relocation("We'll help you relocate to Amsterdam."))
        assertFalse(Sponsorship.relocation("No relocation support is offered."))
        assertFalse(Sponsorship.relocation("Hybrid in London."))
    }

    @Test
    fun abroadJobsThatRuleOutSponsorshipAreFilteredWhenAsked() {
        val profile = Profile(headline = "Android Lead", skills = listOf("Kotlin"))
        fun job(location: String, description: String) = JobForMatching(UUID.randomUUID(), "Acme", "Senior Android Engineer", location, false, description)
        val prefs = Preferences(roles = listOf("Android Engineer"), locations = listOf("Germany", "Bengaluru"), needsVisaSponsorship = true)

        val noVisa = job("Berlin, Germany", "Kotlin. You must hold the right to work in Germany.")
        assertEquals("no visa sponsorship", HardFilter.evaluate(noVisa, profile, prefs).reason)
        assertTrue(HardFilter.evaluate(job("Munich", "Kotlin. Visa sponsorship is available."), profile, prefs).pass)
        assertTrue(HardFilter.evaluate(job("Hamburg", "Kotlin."), profile, prefs).pass, "not stated stays in")
        // At home the visa line doesn't apply; without the preference nothing is filtered for it.
        assertTrue(HardFilter.evaluate(job("Bengaluru, India", "Must have the right to work in India."), profile, prefs).pass)
        assertTrue(HardFilter.evaluate(noVisa, profile, prefs.copy(needsVisaSponsorship = false)).pass)

        assertTrue(Locations.isAbroad("Berlin, Germany"))
        assertTrue(Locations.isAbroad("London"))
        assertFalse(Locations.isAbroad("Bangalore, India"))
        assertFalse(Locations.isAbroad("Remote"))
        assertTrue(Locations.cityMatches("Germany", "München, Bayern"))
        assertTrue(Locations.cityMatches("United Kingdom", "Manchester, UK"))
        assertFalse(Locations.cityMatches("United Kingdom", "Kyiv, Ukraine"))
    }
}
