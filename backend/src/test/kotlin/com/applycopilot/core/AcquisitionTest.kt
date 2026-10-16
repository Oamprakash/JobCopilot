package com.applycopilot.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcquisitionTest {
    private val own = setOf("trulyapply.com", "jobcopilot-bp0m.onrender.com")

    @Test
    fun campaignTagsWinAndAreCleaned() {
        val a = AcquisitionRules.normalize(
            Acquisition(source = " Instagram ", medium = "Bio Link", campaign = "launch<script>", referrer = "https://l.instagram.com/?u=x", landing = "/?utm_source=instagram"),
            own,
        )
        assertEquals("instagram", a.source)
        assertEquals("bio_link", a.medium)
        assertEquals("launchscript", a.campaign)
        assertEquals("l.instagram.com", a.referrer)
        assertEquals("/", a.landing)
    }

    @Test
    fun referringSitesAreNamed() {
        fun src(ref: String) = AcquisitionRules.normalize(Acquisition(referrer = ref), own).source
        assertEquals("reddit", src("https://out.reddit.com/t3_abc"))
        assertEquals("google", src("https://www.google.co.in/"))
        assertEquals("linkedin", src("https://lnkd.in/xyz"))
        assertEquals("x", src("https://t.co/abc"))
        assertEquals("hackernews", src("news.ycombinator.com"))
        assertEquals("example.org", src("https://blog.example.org/post"))
        assertEquals("example.co.in", src("https://www.example.co.in/"))
    }

    @Test
    fun ownSiteAndJunkCountAsDirect() {
        assertEquals("direct", AcquisitionRules.normalize(Acquisition(referrer = "https://trulyapply.com/feed"), own).source)
        assertNull(AcquisitionRules.normalize(Acquisition(referrer = "https://trulyapply.com/feed"), own).referrer)
        assertEquals("direct", AcquisitionRules.normalize(Acquisition(referrer = "not a url"), own).source)
        assertEquals("direct", AcquisitionRules.normalize(Acquisition(), own).source)
        assertNull(AcquisitionRules.normalize(Acquisition(landing = "//evil.example/x"), own).landing)
    }
}
