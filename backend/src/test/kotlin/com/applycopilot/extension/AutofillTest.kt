package com.applycopilot.extension

import com.applycopilot.core.Experience
import com.applycopilot.core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutofillTest {
    @Test
    fun profileFieldsComeFromTheProfile() {
        val p = Profile(
            fullName = " Oam  Prakash R ", email = "", phone = "+91 98400 00000", location = "Chennai",
            links = listOf("linkedin.com/in/oam", "https://github.com/oam", "oam.dev"),
            experience = listOf(Experience("Android Engineer", "Old Co"), Experience("Lead Android Developer", "Movate", current = true)),
        )
        val f = Autofill.profileFields(p, "oam@example.com")
        assertEquals("Oam Prakash R", f.fullName)
        assertEquals("Oam", f.firstName)
        assertEquals("Prakash R", f.lastName)
        assertEquals("oam@example.com", f.email, "falls back to the account email")
        assertEquals("https://linkedin.com/in/oam", f.linkedin)
        assertEquals("https://github.com/oam", f.github)
        assertEquals("https://oam.dev", f.website)
        assertEquals("Lead Android Developer", f.currentTitle)
        assertEquals("Movate", f.currentCompany)

        val bare = Autofill.profileFields(Profile(fullName = "Cher"), "c@example.com")
        assertEquals("Cher", bare.firstName)
        assertEquals("", bare.lastName)
        assertNull(bare.linkedin)
        assertNull(bare.currentTitle)
    }

    @Test
    fun applicationPagesMatchTheirJob() {
        assertTrue(JobUrls.sameJob("https://boards.greenhouse.io/acme/jobs/101", "https://job-boards.greenhouse.io/acme/jobs/101#app"))
        assertTrue(JobUrls.sameJob("https://boards.greenhouse.io/acme/jobs/101", "https://boards.greenhouse.io/acme/jobs/101?gh_src=x"))
        assertTrue(JobUrls.sameJob("https://jobs.lever.co/beta/lv-1", "https://jobs.lever.co/beta/lv-1/apply"))
        assertTrue(JobUrls.sameJob("https://jobs.ashbyhq.com/gamma/ash-1", "https://jobs.ashbyhq.com/Gamma/ash-1/application"))
        assertFalse(JobUrls.sameJob("https://jobs.lever.co/beta/lv-1", "https://jobs.lever.co/beta/lv-10/apply"))
        assertFalse(JobUrls.sameJob("https://jobs.lever.co/beta/lv-1", "https://jobs.lever.co/beta"), "a board page is not the job")
        assertFalse(JobUrls.sameJob("https://jobs.lever.co/beta/lv-1", "not a url"))
        assertEquals("boards.greenhouse.io/acme/jobs/101", JobUrls.normalize("HTTPS://www.Boards.Greenhouse.io/acme/jobs/101/"))
        assertTrue(JobUrls.sameJob("https://boards.greenhouse.io/acme/jobs/101", "https://boards.greenhouse.io/embed/job_app?for=acme&token=101"))
    }
}
