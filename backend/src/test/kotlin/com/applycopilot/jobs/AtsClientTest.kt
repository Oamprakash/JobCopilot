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
                    "api.smartrecruiters.com" ->
                        if (request.url.encodedPath.endsWith("/postings")) BoardFixtures.smartRecruitersList else BoardFixtures.smartRecruitersDetail
                    "apply.workable.com" -> BoardFixtures.workable
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
    fun parsesSmartRecruitersWithDetailsOnlyForIndia() = runBlocking {
        val jobs = client.fetch(Ats.SMARTRECRUITERS, "grab")
        assertEquals(
            listOf(
                "https://api.smartrecruiters.com/v1/companies/grab/postings?limit=100&offset=0",
                "https://api.smartrecruiters.com/v1/companies/grab/postings/744000001",
            ),
            requested,
            "only the India posting gets a detail call",
        )
        val android = jobs.first { it.sourceJobId == "grab:744000001" }
        assertEquals("Bangalore, KA, India", android.location)
        assertEquals("https://jobs.smartrecruiters.com/grab/744000001-senior-software-engineer-mobile-android", android.applyUrl)
        assertEquals("Build our Android app in Kotlin.\n• 6+ years of Android", android.description)
        assertEquals(BoardFixtures.publishedAt, android.postedAt)
        val other = jobs.first { it.sourceJobId == "grab:744000002" }
        assertEquals("", other.description)
        assertEquals("https://jobs.smartrecruiters.com/grab/744000002", other.applyUrl)
    }

    @Test
    fun parsesWorkableWidget() = runBlocking {
        val jobs = client.fetch(Ats.WORKABLE, "apna")
        assertEquals("https://apply.workable.com/api/v1/widget/accounts/apna?details=true", requested.single())
        assertEquals(2, jobs.size)
        val android = jobs[0]
        assertEquals("apna:5211B21BC2", android.sourceJobId)
        assertEquals("Bengaluru, Karnataka, India", android.location)
        assertFalse(android.remote)
        assertEquals("Kotlin and Jetpack Compose .\n5+ years.", android.description)
        assertEquals("https://apply.workable.com/j/5211B21BC2", android.applyUrl)
        assertEquals(BoardFixtures.publishedAt.toString().take(10), android.postedAt.toString().take(10))
        assertTrue(jobs[1].remote)
        assertEquals("India; Remote", jobs[1].location)
    }

    @Test
    fun failsOnMissingBoardAndRejectsBadTokens() = runBlocking {
        assertFailsWith<AtsException> { client.fetch(Ats.LEVER, "missing") }
        assertFailsWith<IllegalArgumentException> { client.fetch(Ats.LEVER, "../admin") }
        Unit
    }

    @Test
    fun parsesAdzunaSearchResults() = runBlocking {
        val urls = mutableListOf<io.ktor.http.Url>()
        val adzuna = AdzunaClient(
            HttpClient(
                MockEngine { request ->
                    urls += request.url
                    respond(BoardFixtures.adzuna, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ),
            AdzunaConfig("id1", "key1"),
        )
        val page = adzuna.search(SearchQuery("android", "chennai"), page = 2)
        val url = urls.single()
        assertEquals("/v1/api/jobs/in/search/2", url.encodedPath)
        assertEquals("id1", url.parameters["app_id"])
        assertEquals("key1", url.parameters["app_key"])
        assertEquals("android", url.parameters["what"])
        assertEquals("chennai", url.parameters["where"])
        assertEquals("50", url.parameters["results_per_page"])
        assertEquals(3, page.total)
        assertEquals(3, page.postings.size, "a result without a link is dropped")

        val flipkart = page.postings.first()
        assertEquals("in:4810001", flipkart.sourceJobId)
        assertEquals("Senior Android Developer", flipkart.title)
        assertEquals("Flipkart", flipkart.employer)
        assertEquals("Bengaluru, Karnataka, India", flipkart.location)
        assertTrue(flipkart.partial)
        assertTrue(flipkart.description.endsWith("experience…"), flipkart.description)
        assertEquals("https://www.adzuna.in/land/ad/4810001?se=x&amp;v=1", flipkart.applyUrl)
        assertEquals(BoardFixtures.publishedAt, flipkart.postedAt)
        assertFalse(flipkart.remote)

        val sales = page.postings[2]
        assertEquals("Employer not named", sales.employer)
        assertEquals("India", sales.location)
        assertTrue(sales.remote)

        // Searching the whole country sends no "where".
        adzuna.search(SearchQuery("android", ""))
        assertEquals(null, urls.last().parameters["where"])

        // Each search goes to its own country's endpoint.
        val sg = adzuna.search(SearchQuery("android", "singapore", "sg"))
        assertEquals("/v1/api/jobs/sg/search/1", urls.last().encodedPath)
        assertEquals("sg:4810001", sg.postings.first().sourceJobId)
    }

    @Test
    fun adzunaKeyErrorsAreExplained() = runBlocking {
        val adzuna = AdzunaClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.Unauthorized) }), AdzunaConfig("id1", "bad"))
        val e = assertFailsWith<AtsException> { adzuna.search(SearchQuery("android", "")) }
        assertTrue(e.message!!.contains("app id or key"), e.message)
    }
}
