package com.applycopilot

import com.applycopilot.api.AddCompanyRequest
import com.applycopilot.api.FeedResponse
import com.applycopilot.api.JobDetailDto
import com.applycopilot.api.MagicLinkRequest
import com.applycopilot.api.MagicLinkResponse
import com.applycopilot.api.ResumeUploadResponse
import com.applycopilot.api.SaveProfileRequest
import com.applycopilot.api.StatusRequest
import com.applycopilot.api.SyncResultDto
import com.applycopilot.api.VerifyRequest
import com.applycopilot.config.AppConfig
import com.applycopilot.core.Preferences
import com.applycopilot.core.RemoteMode
import com.applycopilot.db.Companies
import com.applycopilot.jobs.BoardFixtures
import com.applycopilot.matching.AutocompleteOptions
import com.applycopilot.matching.Suggestion
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedApiTest {
    /** Toggle to make the Greenhouse board drop its Android job on the next sync. */
    private var greenhouseHasAndroid = true

    /** When set, answers Claude job-scoring calls; resume parsing calls always fail so the heuristic parser runs. */
    private var claudeScoring: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData)? = null

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun mockHttp() = HttpClient(
        MockEngine { request ->
            when (request.url.host) {
                "boards-api.greenhouse.io" -> respond(BoardFixtures.greenhouse(greenhouseHasAndroid), HttpStatusCode.OK, json)
                "api.lever.co" -> respond(BoardFixtures.lever, HttpStatusCode.OK, json)
                "api.ashbyhq.com" -> respond(BoardFixtures.ashby, HttpStatusCode.OK, json)
                "api.anthropic.com" -> {
                    val body = (request.body as TextContent).text
                    val handler = claudeScoring
                    if (body.contains("score_jobs") && handler != null) handler.invoke(this, request)
                    else respond("""{"type":"error"}""", HttpStatusCode.InternalServerError)
                }
                else -> error("Unexpected request to ${request.url}")
            }
        },
    )

    private fun withApp(extraEnv: Map<String, String> = emptyMap(), block: suspend ApplicationTestBuilder.(Deps) -> Unit) = testApplication {
        val config = AppConfig.fromEnv(
            mapOf(
                "APP_ENV" to "test",
                "DATABASE_URL" to TestDb.postgres.jdbcUrl,
                "DATABASE_USER" to TestDb.postgres.username,
                "DATABASE_PASSWORD" to TestDb.postgres.password,
                "STORAGE_DIR" to Files.createTempDirectory("uploads").toString(),
                "ADMIN_EMAILS" to "admin@example.com",
            ) + extraEnv,
        )
        val deps = Deps.create(config, mockHttp())
        deps.db.migrate()
        deps.db.tx { c -> c.createStatement().use { it.execute("TRUNCATE users, magic_link_tokens, companies CASCADE") } }
        deps.db.tx { c ->
            Companies.insert(c, "Acme", "greenhouse", "acme")
            Companies.insert(c, "Beta", "lever", "beta")
            Companies.insert(c, "Gamma", "ashby", "gamma")
        }
        application { module(deps) }
        block(deps)
    }

    private fun ApplicationTestBuilder.newClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(apiJson) }
        install(HttpCookies)
    }

    private suspend fun HttpClient.signIn(email: String) {
        val link = post("/auth/magic-link") { contentType(ContentType.Application.Json); setBody(MagicLinkRequest(email)) }
            .body<MagicLinkResponse>().devLink!!
        post("/auth/verify") { contentType(ContentType.Application.Json); setBody(VerifyRequest(link.substringAfter("token="))) }
    }

    private suspend fun HttpClient.onboard(prefs: Preferences) {
        signIn("oam@example.com")
        val upload = submitFormWithBinaryData(
            url = "/resume",
            formData = formData {
                append(
                    "file", ApiTest.docx(ApiTest.RESUME),
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"cv.docx\"")
                    },
                )
            },
        ).body<ResumeUploadResponse>()
        put("/profile") { contentType(ContentType.Application.Json); setBody(SaveProfileRequest(upload.profile, upload.resumeFile.id)) }
        put("/preferences") { contentType(ContentType.Application.Json); setBody(prefs) }
    }

    private val prefs = Preferences(
        roles = listOf("Senior Android Engineer"),
        locations = listOf("Bangalore", "Chennai"),
        remoteMode = RemoteMode.ANY,
        experienceYears = 12.0,
    )

    private suspend fun HttpClient.feed(status: String = "new") = get("/feed?status=$status").body<FeedResponse>()

    @Test
    fun ingestsBoardsAndBuildsAFilteredFeed() = withApp { deps ->
        val results = deps.ingestion.syncAll()
        assertEquals(listOf(3, 1, 2), results.sortedBy { it.company.name }.map { it.fetched })
        assertTrue(results.all { it.error == null })

        val client = newClient()
        client.onboard(prefs)

        val before = client.feed()
        assertTrue(before.needsRefresh)
        assertTrue(before.jobs.isEmpty())

        val feed = client.post("/feed/refresh").body<FeedResponse>()
        assertFalse(feed.needsRefresh)
        // Android roles in Bangalore, Chennai and remote-India pass; sales, US-remote and SF jobs don't.
        assertEquals(
            setOf("Senior Android Engineer", "Lead Mobile Engineer (Android)", "Android Developer"),
            feed.jobs.map { it.title }.toSet(),
        )
        assertEquals(feed.jobs.sortedByDescending { it.score }, feed.jobs)
        assertTrue(feed.jobs.all { it.scorer == "heuristic" && it.reasons.isNotEmpty() })
        val senior = feed.jobs.first { it.title == "Senior Android Engineer" }
        assertTrue("Kotlin Multiplatform" in senior.missingSkills, "${senior.missingSkills}")
        assertEquals(6, feed.lastRun!!.considered)
        // The sales job is the wrong role; the US-remote and San Francisco jobs are outside the user's locations.
        assertEquals(mapOf("role" to 1, "location" to 2), feed.lastRun!!.filtered)

        val detail = client.get("/jobs/${senior.jobId}").body<JobDetailDto>()
        assertTrue(detail.description.contains("Jetpack Compose"))
        assertEquals("Acme", detail.company)
    }

    @Test
    fun saveSkipThrottleAndPreferenceChanges() = withApp { deps ->
        deps.ingestion.syncAll()
        val client = newClient()
        client.onboard(prefs)
        val first = client.post("/feed/refresh").body<FeedResponse>()
        val remote = first.jobs.first { it.title == "Android Developer" }
        val chennai = first.jobs.first { it.title == "Lead Mobile Engineer (Android)" }

        client.put("/feed/${chennai.jobId}/status") { contentType(ContentType.Application.Json); setBody(StatusRequest("saved")) }
        client.put("/feed/${remote.jobId}/status") { contentType(ContentType.Application.Json); setBody(StatusRequest("skipped")) }
        assertEquals(listOf("Senior Android Engineer"), client.feed().jobs.map { it.title })
        assertEquals(listOf(chennai.jobId), client.feed("saved").jobs.map { it.jobId })
        assertEquals(listOf(remote.jobId), client.feed("skipped").jobs.map { it.jobId })

        // A second refresh straight away is throttled: same run, nothing rescored.
        val again = client.post("/feed/refresh").body<FeedResponse>()
        assertEquals(first.lastRun!!.startedAt, again.lastRun!!.startedAt)

        // Changing preferences clears unreviewed matches and asks for a rebuild; saved and skipped stay.
        client.put("/preferences") { contentType(ContentType.Application.Json); setBody(prefs.copy(remoteMode = RemoteMode.ONSITE)) }
        val stale = client.feed()
        assertTrue(stale.needsRefresh)
        assertTrue(stale.jobs.isEmpty())
        assertEquals(1, client.feed("saved").jobs.size)
        val rebuilt = client.post("/feed/refresh").body<FeedResponse>()
        assertEquals(listOf("Senior Android Engineer"), rebuilt.jobs.map { it.title })

        assertEquals(HttpStatusCode.BadRequest, client.get("/feed?status=bogus").status)
        assertEquals(HttpStatusCode.NotFound, client.put("/feed/not-a-uuid/status") { contentType(ContentType.Application.Json); setBody(StatusRequest("saved")) }.status)
    }

    @Test
    fun suggestsCitiesThatWouldWidenTheFeed() = withApp { deps ->
        deps.ingestion.syncAll()
        val client = newClient()
        val chennaiOnly = prefs.copy(locations = listOf("Chennai"))
        client.onboard(chennaiOnly)
        val feed = client.post("/feed/refresh").body<FeedResponse>()
        assertFalse(feed.jobs.any { it.title == "Senior Android Engineer" })
        assertEquals(listOf(Suggestion("location", "Bengaluru", 1)), feed.lastRun!!.suggestions)

        // Taking the suggestion brings the Bengaluru job in.
        client.put("/preferences") { contentType(ContentType.Application.Json); setBody(chennaiOnly.copy(locations = listOf("Chennai", "Bengaluru"))) }
        val widened = client.post("/feed/refresh").body<FeedResponse>()
        assertTrue(widened.jobs.any { it.title == "Senior Android Engineer" })
        assertTrue(widened.lastRun!!.suggestions.isEmpty())
    }

    @Test
    fun closedJobsLeaveTheFeed() = withApp { deps ->
        deps.ingestion.syncAll()
        val client = newClient()
        client.onboard(prefs)
        assertTrue(client.post("/feed/refresh").body<FeedResponse>().jobs.any { it.title == "Senior Android Engineer" })

        greenhouseHasAndroid = false
        val acme = deps.ingestion.syncAll().first { it.company.name == "Acme" }
        assertEquals(1, acme.closed)
        assertFalse(client.feed().jobs.any { it.title == "Senior Android Engineer" })
    }

    @Test
    fun claudeScoresAreUsedAndGrounded() = withApp(mapOf("ANTHROPIC_API_KEY" to "test-key")) { deps ->
        claudeScoring = { request ->
            val ids = Regex("<job id=\\\\\"([0-9a-f-]+)\\\\\">").findAll((request.body as TextContent).text).map { it.groupValues[1] }.toList()
            val results = ids.joinToString(",") {
                """{"jobId":"$it","score":91,"reasons":["Strong Kotlin and Compose overlap"],"missingSkills":["Kotlin Multiplatform","Blockchain"]}"""
            }
            respond(
                """{"content":[{"type":"tool_use","id":"t1","name":"score_jobs","input":{"results":[$results]}}],"stop_reason":"tool_use"}""",
                HttpStatusCode.OK,
                json,
            )
        }
        deps.ingestion.syncAll()
        val client = newClient()
        client.onboard(prefs)
        val feed = client.post("/feed/refresh").body<FeedResponse>()
        assertEquals(3, feed.jobs.size)
        assertTrue(feed.jobs.all { it.scorer == "claude" && it.score == 91 })
        assertEquals(3, feed.lastRun!!.llmScored)
        // "Blockchain" appears in no JD, so it is dropped; KMP stays only where the JD mentions it.
        assertFalse(feed.jobs.any { "Blockchain" in it.missingSkills })
        assertEquals(listOf("Kotlin Multiplatform"), feed.jobs.first { it.title == "Senior Android Engineer" }.missingSkills)
        assertTrue(feed.jobs.first { it.title == "Android Developer" }.missingSkills.isEmpty())
    }

    @Test
    fun weakMatchesAreHiddenUntilAskedFor() = withApp(mapOf("ANTHROPIC_API_KEY" to "test-key")) { deps ->
        claudeScoring = { request ->
            val ids = Regex("<job id=\\\\\"([0-9a-f-]+)\\\\\">").findAll((request.body as TextContent).text).map { it.groupValues[1] }.toList()
            val results = ids.joinToString(",") { """{"jobId":"$it","score":25,"reasons":["Little overlap"],"missingSkills":[]}""" }
            respond(
                """{"content":[{"type":"tool_use","id":"t1","name":"score_jobs","input":{"results":[$results]}}],"stop_reason":"tool_use"}""",
                HttpStatusCode.OK,
                json,
            )
        }
        deps.ingestion.syncAll()
        val client = newClient()
        client.onboard(prefs)
        val hidden = client.post("/feed/refresh").body<FeedResponse>()
        assertTrue(hidden.jobs.isEmpty())
        assertEquals(3, hidden.hiddenWeak)
        assertEquals(40, hidden.minScore)

        val weak = client.get("/feed?status=new&weak=true").body<FeedResponse>()
        assertEquals(3, weak.jobs.size)
        assertTrue(weak.jobs.all { it.score == 25 })
        assertEquals(0, weak.hiddenWeak)
        assertEquals(3, client.post("/feed/refresh?weak=true").body<FeedResponse>().jobs.size)

        // Saving a weak match moves it to Saved, which shows every score, and it no longer counts as hidden.
        client.put("/feed/${weak.jobs.first().jobId}/status") { contentType(ContentType.Application.Json); setBody(StatusRequest("saved")) }
        assertEquals(1, client.feed("saved").jobs.size)
        assertEquals(2, client.feed().hiddenWeak)
    }

    @Test
    fun claudeFailureFallsBackToHeuristic() = withApp(mapOf("ANTHROPIC_API_KEY" to "test-key")) { deps ->
        claudeScoring = { respond("""{"type":"error"}""", HttpStatusCode.ServiceUnavailable) }
        deps.ingestion.syncAll()
        val client = newClient()
        client.onboard(prefs)
        val feed = client.post("/feed/refresh").body<FeedResponse>()
        assertEquals(3, feed.jobs.size)
        assertTrue(feed.jobs.all { it.scorer == "heuristic" })
    }

    @Test
    fun adminRoutesNeedAnAdmin() = withApp {
        val user = newClient().also { it.signIn("oam@example.com") }
        assertEquals(HttpStatusCode.Forbidden, user.get("/admin/companies").status)

        val admin = newClient().also { it.signIn("admin@example.com") }
        val added = admin.post("/admin/companies") {
            contentType(ContentType.Application.Json)
            setBody(AddCompanyRequest("Beta Two", "lever", "beta2"))
        }.body<SyncResultDto>()
        assertEquals(1, added.fetched)
        assertEquals(null, added.error)
        assertEquals(1, added.company.openJobs)

        val duplicate = admin.post("/admin/companies") {
            contentType(ContentType.Application.Json)
            setBody(AddCompanyRequest("Beta Two", "lever", "beta2"))
        }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        val badAts = admin.post("/admin/companies") {
            contentType(ContentType.Application.Json)
            setBody(AddCompanyRequest("X", "workday", "x"))
        }
        assertEquals(HttpStatusCode.BadRequest, badAts.status)
        assertEquals(HttpStatusCode.OK, admin.post("/admin/ingest").status)
    }

    @Test
    fun autocompleteOffersCitiesAndRolesWithCounts() = withApp { deps ->
        assertEquals(HttpStatusCode.Unauthorized, newClient().get("/autocomplete").status)
        deps.ingestion.syncAll()
        val client = newClient().also { it.signIn("oam@example.com") }
        val options = client.get("/autocomplete").body<AutocompleteOptions>()
        val city = options.locations.associate { it.value to it.count }
        assertTrue((city["Bengaluru"] ?: 0) >= 1)
        assertTrue((city["Chennai"] ?: 0) >= 1)
        assertTrue(options.roles.first { it.value == "Android Engineer" }.count >= 2)
    }
}
