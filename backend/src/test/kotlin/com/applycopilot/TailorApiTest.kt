package com.applycopilot

import com.applycopilot.api.ApplicationDto
import com.applycopilot.api.AppliedRequest
import com.applycopilot.api.AutofillResponse
import com.applycopilot.api.EditApplicationRequest
import com.applycopilot.api.ExtMeResponse
import com.applycopilot.api.ExtensionStatusResponse
import com.applycopilot.api.FeedResponse
import com.applycopilot.api.InterviewPrepResponse
import com.applycopilot.api.MagicLinkRequest
import com.applycopilot.api.MagicLinkResponse
import com.applycopilot.api.ManualJobRequest
import com.applycopilot.api.PairCodeResponse
import com.applycopilot.api.PairRequest
import com.applycopilot.api.PairResponse
import com.applycopilot.api.ResumeUploadResponse
import com.applycopilot.api.SaveJobRequest
import com.applycopilot.api.SaveJobResponse
import com.applycopilot.api.SaveProfileRequest
import com.applycopilot.api.StageResponse
import com.applycopilot.api.TailorRequest
import com.applycopilot.api.TrackerItemDto
import com.applycopilot.api.TrackerResponse
import com.applycopilot.api.TrackerUpdateRequest
import com.applycopilot.api.UsageDto
import com.applycopilot.api.VerifyRequest
import com.applycopilot.auth.ConsoleMailer
import com.applycopilot.config.AppConfig
import com.applycopilot.core.Preferences
import com.applycopilot.core.RemoteMode
import com.applycopilot.db.Companies
import com.applycopilot.jobs.BoardFixtures
import com.applycopilot.jobs.FollowUpReminders
import com.applycopilot.tailoring.Change
import com.applycopilot.tailoring.TailoredBullet
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
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
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

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
    fun extensionPairsAutofillsSavesJobsAndTracksApplications() = withApp { deps ->
        val client = newClient()
        val jobId = client.onboardAndFeed().getValue("Senior Android Engineer")
        client.tailor(jobId)
        client.post("/applications/$jobId/approve")

        // Pairing: a one-time code from the web app becomes the extension's token.
        assertEquals(0, client.get("/extension").body<ExtensionStatusResponse>().connected)
        val code = client.post("/extension/pair-code").body<PairCodeResponse>().code
        assertEquals(8, code.length)
        val ext = createClient { install(ContentNegotiation) { json(apiJson) } }
        assertEquals(HttpStatusCode.Unauthorized, ext.get("/ext/me").status)
        val token = ext.post("/ext/pair") { contentType(ContentType.Application.Json); setBody(PairRequest(code.lowercase())) }.body<PairResponse>().token
        assertEquals(HttpStatusCode.Unauthorized, ext.post("/ext/pair") { contentType(ContentType.Application.Json); setBody(PairRequest(code)) }.status, "codes work once")
        assertEquals(1, client.get("/extension").body<ExtensionStatusResponse>().connected)
        suspend fun HttpClient.authed(path: String) = get(path) { bearerAuth(token) }
        assertEquals("oam@example.com", ext.authed("/ext/me").body<ExtMeResponse>().email)

        // On the job's Greenhouse form, the approved application is matched by URL.
        val onForm = ext.authed("/ext/autofill?url=https://job-boards.greenhouse.io/acme/jobs/101%23app").body<AutofillResponse>()
        assertEquals("Oamprakash", onForm.profile.firstName)
        assertEquals("R", onForm.profile.lastName)
        val application = onForm.application!!
        assertEquals(jobId, application.jobId)
        assertEquals("approved", application.status)
        assertTrue(application.coverLetter.contains("Acme"))
        assertTrue(application.answers.any { it.question == "What is your notice period?" && it.answer == "60 days" })
        assertFalse(application.answers.any { it.question.contains("CTC") }, "answers left for the user aren't filled")
        val pdf = ext.authed(application.resumePath)
        assertEquals(ContentType.Application.Pdf, pdf.contentType()?.withoutParameters())
        // Elsewhere (e.g. after a redirect), nothing matches, but recent applications can be picked.
        val elsewhere = ext.authed("/ext/autofill?url=https://careers.example.com/apply/9").body<AutofillResponse>()
        assertNull(elsewhere.application)
        assertEquals(listOf(jobId), elsewhere.recent.map { it.jobId })
        assertEquals(jobId, ext.authed("/ext/autofill?url=x&jobId=$jobId").body<AutofillResponse>().application?.jobId)

        // Saving a page: an unknown job is kept as a saved job; a known job's page reuses that job.
        val saved = ext.post("/ext/save") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(SaveJobRequest("https://careers.example.com/jobs/42", "Staff Android Engineer", "Example Co", "Pune", "Build things."))
        }.body<SaveJobResponse>()
        assertEquals("saved", saved.stage)
        assertFalse(saved.alreadyTracked)
        val known = ext.post("/ext/save") {
            bearerAuth(token); contentType(ContentType.Application.Json)
            setBody(SaveJobRequest("https://boards.greenhouse.io/acme/jobs/101", "Senior Android Engineer", "Acme"))
        }.body<SaveJobResponse>()
        assertEquals(jobId, known.jobId)

        // "I submitted it": applied, with a follow-up a week out.
        assertEquals("applied", ext.post("/ext/applied") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(AppliedRequest(jobId)) }.body<StageResponse>().stage)
        var tracker = client.get("/tracker").body<TrackerResponse>()
        assertEquals(mapOf("saved" to 1, "applied" to 1), tracker.counts.filterValues { it > 0 })
        val applied = tracker.items.single { it.jobId == jobId }
        assertEquals("approved", applied.applicationStatus)
        assertTrue(Instant.parse(applied.followUpAt!!).isAfter(Instant.now().plus(Duration.ofDays(6))))
        assertEquals("Example Co", tracker.items.single { it.jobId == saved.jobId }.company)
        // Saved-from-page jobs never enter anyone's feed.
        assertFalse(client.post("/feed/refresh").body<FeedResponse>().jobs.any { it.title == "Staff Android Engineer" })

        // Moving through stages, notes and follow-ups; a due follow-up is emailed once.
        val due = Instant.now().minus(Duration.ofHours(1)).toString()
        val updated = client.put("/tracker/$jobId") {
            contentType(ContentType.Application.Json); setBody(TrackerUpdateRequest(stage = "interview", followUpAt = due, notes = "Recruiter: Priya"))
        }.body<TrackerItemDto>()
        assertEquals("interview", updated.stage)
        assertTrue(updated.followUpDue)
        assertEquals("Recruiter: Priya", updated.notes)
        assertEquals(HttpStatusCode.BadRequest, client.put("/tracker/$jobId") { contentType(ContentType.Application.Json); setBody(TrackerUpdateRequest(stage = "ghosted")) }.status)
        val reminders = FollowUpReminders(deps.db, deps.mailer, deps.config.webBaseUrl)
        assertEquals(1, reminders.sendDue())
        assertEquals(0, reminders.sendDue(), "each due date is reminded once")
        val mail = (deps.mailer as ConsoleMailer).followUps.single()
        assertEquals("oam@example.com", mail.first)
        assertEquals(listOf("Senior Android Engineer" to "Acme"), mail.second)

        val manual = client.post("/tracker/manual") {
            contentType(ContentType.Application.Json); setBody(ManualJobRequest("Mobile Lead", "Referral Co", "https://referral.example.com/job/7", stage = "applied"))
        }.body<TrackerItemDto>()
        assertEquals("applied", manual.stage)
        assertEquals(HttpStatusCode.BadRequest, client.post("/tracker/manual") { contentType(ContentType.Application.Json); setBody(ManualJobRequest("X", "Y", "not a link")) }.status)
        assertEquals(HttpStatusCode.OK, client.delete("/tracker/${manual.jobId}").status)
        tracker = client.get("/tracker").body<TrackerResponse>()
        assertEquals(2, tracker.items.size)
        assertEquals(1, tracker.dueCount)

        // Disconnecting revokes the token.
        client.delete("/extension")
        assertEquals(HttpStatusCode.Unauthorized, ext.authed("/ext/me").status)
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

    @Test
    fun dailyMatchEmailAndUnsubscribe() = withApp { deps ->
        val client = newClient()
        client.onboardAndFeed()
        assertTrue(client.get("/preferences").body<Preferences>().dailyDigest, "on by default")

        val mailer = deps.mailer as ConsoleMailer
        assertEquals(1, deps.digests.sendDue())
        val (email, items, unsubscribe) = mailer.digests.single()
        assertEquals("oam@example.com", email)
        assertTrue(items.isNotEmpty() && items.size <= 5)
        assertTrue(items.all { it.score >= 40 && it.link.contains("/apply/") })
        assertEquals(items.sortedByDescending { it.score }, items, "best first")
        assertEquals(0, deps.digests.sendDue(), "one email per day")

        val path = unsubscribe.substringAfter("/api")
        assertEquals(HttpStatusCode.BadRequest, client.get(path.dropLast(1) + "x").status)
        val page = client.get(path)
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("unsubscribed"))
        assertFalse(client.get("/preferences").body<Preferences>().dailyDigest)
        assertEquals(HttpStatusCode.OK, client.post(path).status, "one-click unsubscribe from mail apps")

        // Back on, but nothing new has been matched since the last email.
        val prefs = client.get("/preferences").body<Preferences>()
        client.put("/preferences") { contentType(ContentType.Application.Json); setBody(prefs.copy(dailyDigest = true)) }
        assertTrue(client.get("/preferences").body<Preferences>().dailyDigest)
        assertEquals(0, deps.digests.sendDue(Instant.now().plus(Duration.ofDays(1))))
    }

    @Test
    fun interviewPrepIsGroundedInTheProfile() = withApp {
        val client = newClient()
        val jobId = client.onboardAndFeed().getValue("Senior Android Engineer")
        assertEquals(HttpStatusCode.NotFound, client.get("/tracker/$jobId/prep").status)

        val made = client.post("/tracker/$jobId/prep").body<InterviewPrepResponse>()
        assertEquals("heuristic", made.generator)
        val prep = made.prep
        assertTrue(prep.questions.size in 4..10, "${prep.questions.map { it.question }}")
        assertTrue(prep.questions.any { it.question.contains("Acme") })
        val bullets = setOf(
            "Lead a team of 20+ Android engineers across 4 apps",
            "Built Jetpack Compose UI with a shared design system",
        )
        val points = prep.questions.flatMap { it.points }
        assertTrue(points.isNotEmpty())
        assertTrue(points.any { it.text in bullets }, "talking points are the user's own bullets: $points")
        assertTrue("Kotlin Multiplatform" in prep.gaps)

        assertEquals(prep, client.get("/tracker/$jobId/prep").body<InterviewPrepResponse>().prep)
    }
}
