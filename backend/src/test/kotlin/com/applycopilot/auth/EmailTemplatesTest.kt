package com.applycopilot.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailTemplatesTest {
    @Test
    fun signInEmailHasTheButtonAndAPlainLinkFallback() {
        val link = "https://trulyapply.com/auth/verify?token=abc_123-XYZ"
        val email = EmailTemplates.magicLink("TrulyApply", link)
        assertEquals("Your TrulyApply sign-in link", email.subject)
        assertTrue(email.html.contains("""href="$link""""))
        assertTrue(email.html.contains("Sign in to TrulyApply"))
        assertTrue(email.html.contains("trulyapply.com</a>"), "footer links to the site")
        assertTrue(email.text.contains(link))
    }

    @Test
    fun matchEmailEscapesJobTextAndOffersUnsubscribe() {
        val items = listOf(DigestItem("Android <Lead>", "R&D \"Labs\"", "Chennai", 80, "https://trulyapply.com/apply/1"))
        val email = EmailTemplates.digest("TrulyApply", items, 4, "https://trulyapply.com/feed", "https://trulyapply.com/api/digest/unsubscribe?u=1&t=2")
        assertEquals("80 fit · Android <Lead> at R&D \"Labs\" + 3 more", email.subject)
        assertTrue(email.html.contains("Tailor this one"))
        assertTrue(email.text.startsWith("Your best match today: Android <Lead> at R&D \"Labs\", fit 80"))
        assertTrue(email.html.contains("Android &lt;Lead&gt;"))
        assertTrue(email.html.contains("R&amp;D &quot;Labs&quot;"))
        assertFalse(email.html.contains("<Lead>"))
        assertTrue(email.html.contains("See all 4 matches"))
        assertTrue(email.html.contains("unsubscribe?u=1&amp;t=2"))
        assertTrue(email.text.contains("Unsubscribe: https://trulyapply.com/api/digest/unsubscribe?u=1&t=2"))
    }

    @Test
    fun followUpEmailListsEachApplication() {
        val email = EmailTemplates.followUps("TrulyApply", listOf("Senior Android Engineer" to "Acme"), "https://trulyapply.com/tracker")
        assertEquals("Time to follow up: Senior Android Engineer at Acme", email.subject)
        assertTrue(email.html.contains("Senior Android Engineer") && email.html.contains("Open your tracker"))
    }

    @Test
    fun subjectLeadsWithTheBestMatchAndStaysShort() {
        val long = DigestItem("Staff Software Engineer, Android Platform and Developer Experience", "Flipkart", "Bengaluru", 86, "https://x/apply/1")
        assertEquals("86 fit · Staff Software Engineer, Android Platform and… at Flipkart", digestSubject(listOf(long), 1))
        assertEquals("1 new job match for you today", digestSubject(emptyList(), 1))
    }
}
