package com.applycopilot

import com.applycopilot.api.MagicLinkRequest
import com.applycopilot.api.MagicLinkResponse
import com.applycopilot.api.MeResponse
import com.applycopilot.api.ProfileResponse
import com.applycopilot.api.ResumeUploadResponse
import com.applycopilot.api.SaveProfileRequest
import com.applycopilot.api.VerifyRequest
import com.applycopilot.config.AppConfig
import com.applycopilot.core.Preferences
import com.applycopilot.core.RemoteMode
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiTest {
    companion object {
        private val postgres = PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).also { it.start() }

        val RESUME = """
            Oamprakash R
            Lead Android Developer
            Chennai, India | oam@example.com | +91 98765 43210

            Work Experience
            Lead Android Developer at Movate    Jul 2020 – Present
            • Lead a team of 20+ Android engineers across 4 apps
            • Built Jetpack Compose UI with a shared design system

            Senior Android Developer | Acme Mobile   Jan 2016 - Jun 2020
            - Shipped a payments SDK used by 2M users

            Android Developer
            Beta Soft
            2013 - 2015
            • Built offline sync with Room and WorkManager

            Education
            B.E. in Computer Science, Anna University, 2012

            Skills
            Kotlin, Java, Coroutines, MVVM | Firebase
        """.trimIndent()

        fun docx(text: String): ByteArray {
            val out = ByteArrayOutputStream()
            XWPFDocument().use { doc ->
                text.lines().forEach { doc.createParagraph().createRun().setText(it) }
                doc.write(out)
            }
            return out.toByteArray()
        }
    }

    private lateinit var storageDir: Path

    private fun withApp(block: suspend ApplicationTestBuilder.(Deps) -> Unit) = testApplication {
        storageDir = Files.createTempDirectory("uploads")
        val config = AppConfig.fromEnv(
            mapOf(
                "APP_ENV" to "test",
                "DATABASE_URL" to postgres.jdbcUrl,
                "DATABASE_USER" to postgres.username,
                "DATABASE_PASSWORD" to postgres.password,
                "STORAGE_DIR" to storageDir.toString(),
            ),
        )
        val deps = Deps.create(config)
        deps.db.migrate()
        deps.db.tx { c -> c.createStatement().use { it.execute("TRUNCATE users, magic_link_tokens CASCADE") } }
        application { module(deps) }
        block(deps)
    }

    private fun ApplicationTestBuilder.newClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(apiJson) }
        install(HttpCookies)
    }

    private suspend fun HttpClient.requestLink(email: String): HttpResponse = post("/auth/magic-link") {
        contentType(ContentType.Application.Json)
        setBody(MagicLinkRequest(email))
    }

    private suspend fun HttpClient.verify(token: String): HttpResponse = post("/auth/verify") {
        contentType(ContentType.Application.Json)
        setBody(VerifyRequest(token))
    }

    private suspend fun HttpClient.signIn(email: String): MeResponse {
        val link = requestLink(email).body<MagicLinkResponse>().devLink
        assertNotNull(link)
        val response = verify(link.substringAfter("token="))
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return response.body()
    }

    private suspend fun HttpClient.upload(bytes: ByteArray, name: String = "resume.docx"): HttpResponse =
        submitFormWithBinaryData(
            url = "/resume",
            formData = formData {
                append(
                    "file",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"$name\"")
                    },
                )
            },
        )

    // ---- Auth -----------------------------------------------------------------------------

    @Test
    fun magicLinkSignInWorksOnceAndLogoutEndsSession() = withApp {
        val client = newClient()
        val link = client.requestLink("  Oam@Example.com ").body<MagicLinkResponse>().devLink!!
        assertTrue(link.startsWith("http://localhost:3000/auth/verify?token="))
        val token = link.substringAfter("token=")

        val me = client.verify(token).body<MeResponse>()
        assertEquals("oam@example.com", me.email)
        assertFalse(me.hasProfile)
        assertEquals(HttpStatusCode.OK, client.get("/me").status)

        assertEquals(HttpStatusCode.Unauthorized, newClient().verify(token).status, "a link must work only once")

        client.post("/auth/logout")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
    }

    @Test
    fun signingInTwiceReusesTheSameAccount() = withApp {
        val first = newClient().signIn("same@example.com")
        val second = newClient().signIn("SAME@example.com")
        assertEquals(first.id, second.id)
    }

    @Test
    fun rejectsBadEmailAndRateLimits() = withApp {
        val client = newClient()
        assertEquals(HttpStatusCode.BadRequest, client.requestLink("not-an-email").status)
        repeat(5) { assertEquals(HttpStatusCode.OK, client.requestLink("busy@example.com").status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.requestLink("busy@example.com").status)
    }

    @Test
    fun rejectsGarbageTokens() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, newClient().verify("x".repeat(43)).status)
        assertEquals(HttpStatusCode.Unauthorized, newClient().verify("short").status)
    }

    @Test
    fun protectedRoutesNeedASession() = withApp {
        val client = newClient()
        for (path in listOf("/me", "/profile", "/preferences")) {
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status, path)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.upload(docx(RESUME)).status)
    }

    @Test
    fun rejectsCrossSiteOrigin() = withApp {
        val response = newClient().post("/auth/magic-link") {
            header(HttpHeaders.Origin, "https://evil.example")
            contentType(ContentType.Application.Json)
            setBody(MagicLinkRequest("oam@example.com"))
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ---- Resume and profile ---------------------------------------------------------------

    @Test
    fun uploadParsesResumeThenSavesVersionedProfile() = withApp {
        val client = newClient()
        client.signIn("oam@example.com")
        assertEquals(HttpStatusCode.NotFound, client.get("/profile").status)

        val uploadResponse = client.upload(docx(RESUME), "Oam CV.docx")
        assertEquals(HttpStatusCode.OK, uploadResponse.status, uploadResponse.bodyAsText())
        val upload = uploadResponse.body<ResumeUploadResponse>()
        assertEquals("heuristic", upload.parser)
        assertEquals("Oam CV.docx", upload.resumeFile.fileName)
        assertEquals("Oamprakash R", upload.profile.fullName)
        assertEquals(3, upload.profile.experience.size)
        assertTrue("Kotlin" in upload.profile.skills)
        assertTrue(upload.removed.isEmpty(), "removed: ${upload.removed}")
        assertNull(client.get("/profile").takeIf { it.status == HttpStatusCode.OK }, "upload alone must not save a profile")

        val v1 = client.put("/profile") {
            contentType(ContentType.Application.Json)
            setBody(SaveProfileRequest(upload.profile, upload.resumeFile.id))
        }.body<ProfileResponse>()
        assertEquals(1, v1.version)
        assertEquals(upload.resumeFile.id, v1.resumeFile?.id)

        // A second save without a file id keeps the same resume and bumps the version.
        val v2 = client.put("/profile") {
            contentType(ContentType.Application.Json)
            setBody(SaveProfileRequest(upload.profile.copy(headline = "Android Lead")))
        }.body<ProfileResponse>()
        assertEquals(2, v2.version)
        assertEquals(upload.resumeFile.id, v2.resumeFile?.id)

        val current = client.get("/profile").body<ProfileResponse>()
        assertEquals(2, current.version)
        assertEquals("Android Lead", current.profile.headline)
        assertTrue(client.get("/me").body<MeResponse>().hasProfile)
    }

    @Test
    fun invalidProfileIsRejectedWithProblems() = withApp {
        val client = newClient()
        client.signIn("oam@example.com")
        val upload = client.upload(docx(RESUME)).body<ResumeUploadResponse>()
        val response = client.put("/profile") {
            contentType(ContentType.Application.Json)
            setBody(SaveProfileRequest(upload.profile.copy(fullName = "")))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("Full name is required"))
    }

    @Test
    fun rejectsFilesThatAreNotResumes() = withApp {
        val client = newClient()
        client.signIn("oam@example.com")
        assertEquals(HttpStatusCode.UnsupportedMediaType, client.upload("just some text".toByteArray(), "resume.pdf").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.upload("%PDF-1.4 broken".toByteArray(), "resume.pdf").status)
        assertEquals(HttpStatusCode.UnprocessableEntity, client.upload(docx("Too short"), "resume.docx").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.upload(ByteArray(5 * 1024 * 1024 + 10) { 'a'.code.toByte() }).status)
    }

    @Test
    fun cannotAttachAnotherUsersResume() = withApp {
        val alice = newClient().also { it.signIn("alice@example.com") }
        val aliceFile = alice.upload(docx(RESUME)).body<ResumeUploadResponse>()

        val bob = newClient().also { it.signIn("bob@example.com") }
        val response = bob.put("/profile") {
            contentType(ContentType.Application.Json)
            setBody(SaveProfileRequest(aliceFile.profile, aliceFile.resumeFile.id))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ---- Preferences ----------------------------------------------------------------------

    @Test
    fun preferencesDefaultValidateAndSave() = withApp {
        val client = newClient()
        client.signIn("oam@example.com")
        assertEquals(Preferences(), client.get("/preferences").body<Preferences>())

        val bad = client.put("/preferences") {
            contentType(ContentType.Application.Json)
            setBody(Preferences(minCtcLpa = -5.0))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)

        val wanted = Preferences(
            roles = listOf("Senior Android Engineer", " senior android engineer "),
            locations = listOf("Chennai", "Bengaluru"),
            remoteMode = RemoteMode.HYBRID,
            minCtcLpa = 40.5,
            experienceYears = 12.0,
            noticePeriodDays = 60,
            skipCompanies = listOf("Movate"),
        )
        assertEquals(HttpStatusCode.OK, client.put("/preferences") { contentType(ContentType.Application.Json); setBody(wanted) }.status)
        val saved = client.get("/preferences").body<Preferences>()
        assertEquals(wanted.copy(roles = listOf("Senior Android Engineer")), saved)
    }

    // ---- Account deletion -----------------------------------------------------------------

    @Test
    fun deletingAccountRemovesDataAndFiles() = withApp { deps ->
        val client = newClient()
        client.signIn("oam@example.com")
        val upload = client.upload(docx(RESUME)).body<ResumeUploadResponse>()
        client.put("/profile") { contentType(ContentType.Application.Json); setBody(SaveProfileRequest(upload.profile, upload.resumeFile.id)) }
        assertTrue(Files.walk(storageDir).anyMatch { Files.isRegularFile(it) })

        assertEquals(HttpStatusCode.OK, client.delete("/me").status)
        assertFalse(Files.walk(storageDir).anyMatch { Files.isRegularFile(it) }, "resume files must be deleted")
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)

        val rows = deps.db.tx { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT (SELECT count(*) FROM users) + (SELECT count(*) FROM profiles) + (SELECT count(*) FROM resume_files)").use { rs ->
                    rs.next(); rs.getInt(1)
                }
            }
        }
        assertEquals(0, rows)
    }
}
