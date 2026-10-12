package com.applycopilot.jobs

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtsClientTest {
    private val requested = mutableListOf<String>()
    private val client = AtsClient(
        HttpClient(
            MockEngine { request ->
                requested += request.url.toString()
                val body = when (request.url.host) {
                    "boards-api.greenhouse.io" -> BoardFixtures.greenhouse()
                    "api.lever.co" -> BoardFixtures.lever
                    "api.ashbyhq.com" -> BoardFixtures.ashby
                    else -> error("unexpected host")
                }
                if (request.url.encodedPath.contains("missing")) respond("Not found", HttpStatusCode.NotFound)
                else respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ),
    )

    @Test
    fun parsesGreenhouseBoards() = runBlocking {
        val jobs = client.fetch(Ats.GREENHOUSE, "acme")
        assertEquals("https://boards-api.greenhouse.io/v1/boards/acme/jobs?content=true", requested.single())
        assertEquals(3, jobs.size)
        val android = jobs.first()
        assertEquals("acme:101", android.sourceJobId)
        assertEquals("Bengaluru, India", android.location)
        assertFalse(android.remote)
        assertTrue(android.description.startsWith("Build our Android app with Kotlin"), android.description)
        assertTrue(android.description.contains("• 8+ years of experience"))
        assertEquals(BoardFixtures.publishedAt, android.postedAt)
        assertTrue(jobs[2].remote, "\"Remote from the US\" is a remote job")
    }

    @Test
    fun parsesLeverPostings() = runBlocking {
        val job = client.fetch(Ats.LEVER, "beta").single()
        assertEquals("https://api.lever.co/v0/postings/beta?mode=json", requested.single())
        assertEquals("beta:lv-1", job.sourceJobId)
        assertEquals("Chennai, India", job.location)
        assertFalse(job.remote)
        assertEquals("https://jobs.lever.co/beta/lv-1", job.applyUrl)
        assertTrue(job.description.contains("Lead our Android team."))
        assertTrue(job.description.contains("Kotlin, MVVM and Firebase"))
        assertTrue(job.description.contains("• Kotlin Multiplatform"))
        assertEquals(BoardFixtures.publishedAt, job.postedAt)
    }

    @Test
    fun parsesAshbyBoardsAndSkipsUnlisted() = runBlocking {
        val jobs = client.fetch(Ats.ASHBY, "gamma")
        assertEquals("https://api.ashbyhq.com/posting-api/job-board/gamma?includeCompensation=false", requested.single())
        assertEquals(listOf("gamma:ash-1", "gamma:ash-3"), jobs.map { it.sourceJobId })
        assertEquals("Remote, India", jobs[0].location)
        assertTrue(jobs[0].remote)
        assertFalse(jobs[1].remote, "Ashby hybrid roles carry isRemote=true; workplaceType wins")
        assertEquals("TypeScript", jobs[1].description)
        assertEquals(BoardFixtures.publishedAt, jobs[0].postedAt)
    }

    @Test
    fun failsOnMissingBoardAndRejectsBadTokens() = runBlocking {
        assertFailsWith<AtsException> { client.fetch(Ats.LEVER, "missing") }
        assertFailsWith<IllegalArgumentException> { client.fetch(Ats.LEVER, "../admin") }
        Unit
    }
}
