package com.applycopilot.matching

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobPagesTest {
    private fun job(title: String, location: String, remote: Boolean = false) = JobPages.Listing(title + location, title, location, remote)

    @Test
    fun jobsLandOnTheirRoleAndPlacePages() {
        val pages = JobPages.group(
            listOf(
                job("Senior Android Engineer", "Bangalore, Karnataka, India"),
                job("Android / iOS Developer", "Chennai, India"),
                job("Frontend Engineer (React)", "Gurgaon"),
                job("JavaScript Developer", "Pune"),
                job("Senior Java Developer", "Pune, India"),
                job("Android Engineer", "Remote - India", remote = true),
                job("Office Manager", "Chennai"),
            ),
        )
        assertEquals(1, pages["android-developer-jobs-in-bengaluru"]?.size)
        assertEquals(1, pages["ios-developer-jobs-in-chennai"]?.size)
        assertEquals(1, pages["react-developer-jobs-in-delhi-ncr"]?.size)
        assertEquals(1, pages["frontend-developer-jobs-in-delhi-ncr"]?.size)
        assertEquals(1, pages["java-developer-jobs-in-pune"]?.size, "JavaScript isn't Java")
        assertEquals(1, pages["remote-android-developer-jobs"]?.size)
        assertEquals(3, pages["android-developer-jobs-in-india"]?.size, "the India page includes remote jobs open to India")
        assertTrue(pages.keys.none { it.contains("manager") && it.contains("chennai") })
    }

    @Test
    fun slugsAndTitles() {
        val page = JobPages.page("android-developer-jobs-in-chennai")!!
        assertEquals("Android Developer jobs in Chennai", page.title)
        assertEquals("Remote Android Developer jobs", JobPages.page("remote-android-developer-jobs")!!.title)
        assertNull(JobPages.page("plumber-jobs-in-chennai"))
    }
}
