package com.applycopilot.auth

import com.applycopilot.config.MailConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory

interface Mailer {
    suspend fun sendMagicLink(email: String, link: String)
}

/** Dev and test: the link is logged instead of emailed. */
class ConsoleMailer : Mailer {
    private val log = LoggerFactory.getLogger(ConsoleMailer::class.java)
    val sent = mutableListOf<Pair<String, String>>()

    override suspend fun sendMagicLink(email: String, link: String) {
        sent += email to link
        log.info("Magic link for {}: {}", email, link)
    }
}

/** Sends through Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email). */
class ResendMailer(private val http: HttpClient, private val config: MailConfig) : Mailer {
    override suspend fun sendMagicLink(email: String, link: String) {
        val body = buildJsonObject {
            put("from", config.from)
            putJsonArray("to") { add(email) }
            put("subject", "Your Apply Copilot sign-in link")
            put("text", "Sign in to Apply Copilot:\n\n$link\n\nThe link works once and expires in 15 minutes. If you didn't ask for it, ignore this email.")
            put(
                "html",
                """<p>Sign in to Apply Copilot:</p><p><a href="$link">Sign in</a></p>""" +
                    "<p>The link works once and expires in 15 minutes. If you didn't ask for it, ignore this email.</p>",
            )
        }
        val response = http.post("https://api.resend.com/emails") {
            bearerAuth(config.resendApiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        check(response.status.isSuccess()) { "Resend returned ${response.status}: ${response.bodyAsText().take(300)}" }
    }
}
