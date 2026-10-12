package com.applycopilot

import com.applycopilot.api.ApplicationDto
import com.applycopilot.api.EditApplicationRequest
import com.applycopilot.api.FeedResponse
import com.applycopilot.api.MagicLinkRequest
import com.applycopilot.api.MagicLinkResponse
import com.applycopilot.api.ResumeUploadResponse
import com.applycopilot.api.SaveProfileRequest
import com.applycopilot.api.TailorRequest
import com.applycopilot.api.UsageDto
import com.applycopilot.api.VerifyRequest
import com.applycopilot.config.AppConfig
import com.applycopilot.core.Preferences
import com.applycopilot.core.RemoteMode
import com.applycopilot.db.Companies
import com.applycopilot.jobs.BoardFixtures
import com.applycopilot.tailoring.Change
import com.applycopilot.tailoring.TailoredBullet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
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
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TailorApiTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Body of Claude's tailor_application tool call, or null to make that call fail. */
    private var claudeTailorInput: String? = null

    private fun mockHttp() = HttpClient(
        MockEngine { request ->
            when (request.url.host) {
                "boards-api.greenhouse.io" -> respond(BoardFixtures.greenhouse(), HttpStatusCode.OK, json)
                "api.lever.co" -> respond(BoardFixtures.lever, HttpStatusCode.OK, json)
                "api.ashbyhq.com" -> respond(BoardFixtures.ashby, HttpStatusCode.OK, json)
                "api.anthropic.com" -> {
                    val body = (request.body as TextContent).text
                    val input = claudeTailorInput
                    if (body.contains("tailor_application") && input != null) {
                        respond(
                            """{"content":[{"type":"tool_use","id":"t1","name":"tailor_application","input":$input}],"stop_reason":"tool_use"}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    } else respond("""{"type":"error"}""", HttpStatusCode.InternalServerError)
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
        deps.ingestion.syncAll()
        application { module(deps) }
        block(deps)
    }

    private fun ApplicationTestBuilder.newClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(apiJson) }
        install(HttpCookies)
    }

    /** Signs in, uploads the sample resume, saves profile and preferences, and returns the job ids in the feed by title. */
    private suspend fun HttpClient.onboardAndFeed(): Map<String, String> {
        val link = post("/auth/magic-link") { contentType(ContentType.Application.Json); setBody(MagicLinkRequest("oam@example.com")) }
            .body<MagicLinkResponse>().devLink!!
        post("/auth/verify") { contentType(ContentType.Application.Json); setBody(VerifyRequest(link.substringAfter("token="))) }
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
        put("/preferences") {
            contentType(ContentType.Application.Json)
            setBody(Preferences(roles = listOf("Senior Android Engineer"), locations = listOf("Bangalore", "Chennai"), remoteMode = RemoteMode.ANY, noticePeriodDays = 60))
        }
        return post("/feed/refresh").body<FeedResponse>().jobs.associate { it.title to it.jobId }
    }

    private suspend fun HttpClient.tailor(jobId: String, questions: List<String>? = null) = post("/applications/$jobId/tailor") {
        contentType(ContentType.Application.Json)
        setBody(TailorRequest(questions))
    }

    @Test
    fun tailorReviewEditApproveAndDownload() = withApp {
        val client = newClient()
        val jobs = client.onboardAndFeed()
        val jobId = jobs.getValue("Senior Android Engineer")
        assertEquals(HttpStatusCode.NotFound, client.get("/applications/$jobId").status)

        val app = client.tailor(jobId).body<ApplicationDto>()
        assertEquals("draft", app.status)
        assertEquals("heuristic", app.generator)
        assertTrue(app.removed.isEmpty(), "removed: ${app.removed}")
        assertEquals("Acme", app.job.company)
        val resume = app.application.resume
        assertEquals(listOf("Movate", "Acme Mobile", "Beta Soft"), resume.experience.map { it.company })
        assertTrue(app.application.coverLetter.contains("Acme"))
        val answers = app.application.answers.associateBy { it.question }
        assertEquals("60 days", answers.getValue("What is your notice period?").answer)
        assertTrue(answers.getValue("What are your current and expected CTC?").needsUser)
        assertTrue("Kotlin" in app.keywordsCovered)
        assertTrue("Kotlin Multiplatform" in app.keywordsMissing)
        assertEquals(1, app.usage.tailoredThisMonth)

        // The user's own edit is kept but flagged, and titles stay the profile's.
        val edited = app.application.copy(
            resume = resume.copy(
                experience = resume.experience.mapIndexed { i, e ->
                    if (i == 0) e.copy(title = "CTO", bullets = e.bullets + TailoredBullet("Cut crash rate by 70%", 0, Change.REWORDED)) else e
                },
            ),
        )
        val afterEdit = client.put("/applications/$jobId") { contentType(ContentType.Application.Json); setBody(EditApplicationRequest(edited)) }
            .body<ApplicationDto>()
        assertEquals("Lead Android Developer", afterEdit.application.resume.experience[0].title)
        assertTrue(afterEdit.application.resume.experience[0].bullets.any { it.text == "Cut crash rate by 70%" })
        assertEquals(1, afterEdit.warnings.size, "${afterEdit.warnings}")

        val approved = client.post("/applications/$jobId/approve").body<ApplicationDto>()
        assertEquals("approved", approved.status)
        assertTrue(approved.approvedAt != null)

        val pdf = client.get("/applications/$jobId/resume.pdf")
        assertEquals(ContentType.Application.Pdf, pdf.contentType()?.withoutParameters())
        assertTrue(pdf.headers[HttpHeaders.ContentDisposition]!!.contains("Oamprakash_R_Acme.pdf"))
        val text = Loader.loadPDF(pdf.bodyAsBytes()).use { PDFTextStripper().getText(it) }
        assertTrue(text.contains("Oamprakash R"))
        assertTrue(text.contains("Lead Android Developer · Movate"), text)
        assertTrue(text.contains("Cut crash rate by 70%"))

        // Editing after approval sends it back to draft.
        val reEdited = client.put("/applications/$jobId") { contentType(ContentType.Application.Json); setBody(EditApplicationRequest(approved.application)) }
            .body<ApplicationDto>()
        assertEquals("draft", reEdited.status)
        assertNull(reEdited.approvedAt)
    }

    @Test
    fun customQuestionsAndErrors() = withApp {
        val client = newClient()
        val jobs = client.onboardAndFeed()
        val jobId = jobs.getValue("Senior Android Engineer")
        val app = client.tailor(jobId, listOf("Do you need visa sponsorship?", "  ", "Why are you a good fit for this role?")).body<ApplicationDto>()
        assertEquals(listOf("Do you need visa sponsorship?", "Why are you a good fit for this role?"), app.application.answers.map { it.question })
        assertTrue(app.application.answers[0].needsUser)
        assertFalse(app.application.answers[1].needsUser)

        assertEquals(HttpStatusCode.NotFound, client.tailor("00000000-0000-0000-0000-000000000000").status)
        assertEquals(HttpStatusCode.NotFound, client.tailor("not-a-uuid").status)
    }

    @Test
    fun pastedJobDescriptionDrivesTailoring() = withApp {
        val client = newClient()
        val jobId = client.onboardAndFeed().getValue("Senior Android Engineer")
        val first = client.tailor(jobId).body<ApplicationDto>()
        assertEquals(null, first.pastedDescription)
        assertFalse("Rust" in first.keywordsMissing)

        val full = "Senior Android Engineer at Acme. " + "You will build our Android app in Kotlin and write services in Rust. ".repeat(5)
        fun body(description: String?) = TailorRequest(jobDescription = description)
        val pasted = client.post("/applications/$jobId/tailor") { contentType(ContentType.Application.Json); setBody(body(full)) }.body<ApplicationDto>()
        assertEquals(full.trim(), pasted.pastedDescription)
        assertTrue("Rust" in pasted.keywordsMissing, "keywords come from the pasted text: ${pasted.keywordsMissing}")
        assertEquals(pasted.usage.tailoredThisMonth, first.usage.tailoredThisMonth, "regenerating doesn't count again")

        // Regenerating without a description keeps the pasted one; too short is refused; empty goes back to the job's own.
        assertEquals(full.trim(), client.tailor(jobId).body<ApplicationDto>().pastedDescription)
        val short = client.post("/applications/$jobId/tailor") { contentType(ContentType.Application.Json); setBody(body("Android role")) }
        assertEquals(HttpStatusCode.BadRequest, short.status)
        assertEquals(full.trim(), client.get("/applications/$jobId").body<ApplicationDto>().pastedDescription)
        val cleared = client.post("/applications/$jobId/tailor") { contentType(ContentType.Application.Json); setBody(body("")) }.body<ApplicationDto>()
        assertEquals(null, cleared.pastedDescription)
        assertFalse("Rust" in cleared.keywordsMissing)
    }

    @Test
    fun freePlanLimitCountsNewApplicationsOnly() = withApp(mapOf("FREE_TAILOR_LIMIT" to "1")) {
        val client = newClient()
        val jobs = client.onboardAndFeed()
        val first = jobs.getValue("Senior Android Engineer")
        val second = jobs.getValue("Lead Mobile Engineer (Android)")
        assertEquals(HttpStatusCode.OK, client.tailor(first).status)
        assertEquals(HttpStatusCode.PaymentRequired, client.tailor(second).status)
        assertEquals(HttpStatusCode.OK, client.tailor(first).status, "regenerating doesn't count again")
        assertEquals(UsageDto("free", 1, 1), client.get("/usage").body<UsageDto>())
    }

    @Test
    fun claudeDraftIsGuarded() = withApp(mapOf("ANTHROPIC_API_KEY" to "test-key")) {
        claudeTailorInput = """
            {"headline": "Principal Engineer",
             "summary": "Android lead building Kotlin apps. Expert in Kubernetes.",
             "skills": ["Kotlin", "Kubernetes", "Jetpack Compose"],
             "experience": [
               {"sourceIndex": 0, "bullets": [
                 {"sourceIndex": 1, "text": "Built Jetpack Compose UI with a shared design system"},
                 {"sourceIndex": 0, "text": "Led a team of 45 Android engineers across 4 apps"}
               ]}
             ],
             "coverLetter": "Dear Hiring Team at Acme,\nI have shipped Android apps in Kotlin since 2013. I also scaled Kubernetes clusters to 500 nodes.\nBest regards,\nOamprakash R",
             "answers": [{"question": "Why are you a good fit for this role?", "answer": "I lead Android teams using Kotlin."}]}
        """.trimIndent()
        val client = newClient()
        val jobs = client.onboardAndFeed()
        val app = client.tailor(jobs.getValue("Senior Android Engineer")).body<ApplicationDto>()
        assertEquals("claude", app.generator)
        val resume = app.application.resume
        assertEquals("Lead Android Developer", resume.headline)
        assertFalse("Kubernetes" in resume.skills)
        val movate = resume.experience[0].bullets
        assertEquals("Built Jetpack Compose UI with a shared design system", movate[0].text)
        assertEquals("Lead a team of 20+ Android engineers across 4 apps", movate[1].text, "45 isn't in the profile")
        assertEquals(3, resume.experience.size, "roles Claude left out come back")
        assertFalse(app.application.coverLetter.contains("Kubernetes"))
        assertTrue(app.application.coverLetter.contains("since 2013"))
        assertEquals("Android lead building Kotlin apps.", resume.summary)
        assertTrue(app.removed.isNotEmpty())
    }

    @Test
    fun claudeFailureFallsBackToRuleBasedTailor() = withApp(mapOf("ANTHROPIC_API_KEY" to "test-key")) {
        claudeTailorInput = null
        val client = newClient()
        val jobs = client.onboardAndFeed()
        val app = client.tailor(jobs.getValue("Senior Android Engineer")).body<ApplicationDto>()
        assertEquals("heuristic", app.generator)
        assertTrue(app.application.coverLetter.isNotBlank())
    }
}
