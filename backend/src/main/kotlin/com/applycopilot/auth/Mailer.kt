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
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/** One job in the daily match email. */
data class DigestItem(val title: String, val company: String, val location: String, val score: Int, val link: String)

interface Mailer {
    suspend fun sendMagicLink(email: String, link: String)

    /** One email listing the applications due a follow-up ("title" to "company"), linking to the tracker. */
    suspend fun sendFollowUps(email: String, items: List<Pair<String, String>>, trackerLink: String)

    /** The daily match email: the best [items] of [total] new matches. */
    suspend fun sendDigest(email: String, items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String)
}

internal fun digestSubject(total: Int) = if (total == 1) "1 new job match for you today" else "$total new job matches for you today"

internal fun digestText(items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String): String = buildString {
    appendLine(if (total == 1) "One new job fits your profile:" else "$total new jobs fit your profile. The best ${items.size}:")
    appendLine()
    items.forEach { appendLine("• ${it.title} at ${it.company}${if (it.location.isNotBlank()) " (${it.location})" else ""}, fit ${it.score}\n  ${it.link}") }
    appendLine()
    appendLine("See all your matches: $feedLink")
    appendLine()
    appendLine("Don't want these? Unsubscribe: $unsubscribeLink")
}

internal fun followUpText(items: List<Pair<String, String>>, trackerLink: String): String = buildString {
    appendLine(if (items.size == 1) "One application is due a follow-up:" else "${items.size} applications are due a follow-up:")
    appendLine()
    items.forEach { (title, company) -> appendLine("• $title at $company") }
    appendLine()
    appendLine("A short, polite note to the recruiter or hiring manager keeps you on their radar.")
    appendLine("Update or snooze them in your tracker: $trackerLink")
}

/** Dev and test: the link is logged instead of emailed. */
class ConsoleMailer : Mailer {
    private val log = LoggerFactory.getLogger(ConsoleMailer::class.java)
    val sent = mutableListOf<Pair<String, String>>()

    override suspend fun sendMagicLink(email: String, link: String) {
        sent += email to link
        log.info("Magic link for {}: {}", email, link)
    }

    val followUps = mutableListOf<Pair<String, List<Pair<String, String>>>>()
    val digests = mutableListOf<Triple<String, List<DigestItem>, String>>()

    override suspend fun sendDigest(email: String, items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String) {
        digests += Triple(email, items, unsubscribeLink)
        log.info("Daily match email for {}:\n{}", email, digestText(items, total, feedLink, unsubscribeLink))
    }

    override suspend fun sendFollowUps(email: String, items: List<Pair<String, String>>, trackerLink: String) {
        followUps += email to items
        log.info("Follow-up reminder for {}:\n{}", email, followUpText(items, trackerLink))
    }
}

/** Sends through Resend's HTTP API (https://resend.com/docs/api-reference/emails/send-email). */
class ResendMailer(private val http: HttpClient, private val config: MailConfig) : Mailer {
    override suspend fun sendMagicLink(email: String, link: String) {
        val body = buildJsonObject {
            put("from", config.from)
            putJsonArray("to") { add(email) }
            put("subject", "Your ${config.appName} sign-in link")
            put("text", "Sign in to ${config.appName}:\n\n$link\n\nThe link works once and expires in 15 minutes. If you didn't ask for it, ignore this email.")
            put(
                "html",
                """<p>Sign in to ${config.appName}:</p><p><a href="$link">Sign in</a></p>""" +
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

    override suspend fun sendDigest(email: String, items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val rows = items.joinToString("") { i ->
            """<li style="margin:0 0 12px"><a href="${esc(i.link)}" style="font-weight:600;color:#1f4fe0">${esc(i.title)}</a><br>""" +
                """<span style="color:#5b6478">${esc(i.company)}${if (i.location.isNotBlank()) " · " + esc(i.location) else ""} · fit ${i.score}</span></li>"""
        }
        val body = buildJsonObject {
            put("from", config.from)
            putJsonArray("to") { add(email) }
            put("subject", digestSubject(total))
            put("text", digestText(items, total, feedLink, unsubscribeLink))
            put(
                "html",
                "<p>${if (total == 1) "One new job fits your profile:" else "$total new jobs fit your profile. The best ${items.size}:"}</p>" +
                    """<ul style="padding-left:18px">$rows</ul><p><a href="${esc(feedLink)}">See all your matches</a></p>""" +
                    """<p style="color:#5b6478;font-size:12px">You get this once a day when there are new matches. <a href="${esc(unsubscribeLink)}">Unsubscribe</a> or change it in Preferences.</p>""",
            )
            // One-click unsubscribe (RFC 8058), which mail apps show as an Unsubscribe button.
            putJsonObject("headers") {
                put("List-Unsubscribe", "<$unsubscribeLink>")
                put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click")
            }
        }
        val response = http.post("https://api.resend.com/emails") {
            bearerAuth(config.resendApiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        check(response.status.isSuccess()) { "Resend returned ${response.status}: ${response.bodyAsText().take(300)}" }
    }

    override suspend fun sendFollowUps(email: String, items: List<Pair<String, String>>, trackerLink: String) {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val body = buildJsonObject {
            put("from", config.from)
            putJsonArray("to") { add(email) }
            put("subject", if (items.size == 1) "Time to follow up: ${items[0].first} at ${items[0].second}" else "${items.size} applications to follow up on")
            put("text", followUpText(items, trackerLink))
            put(
                "html",
                "<p>${if (items.size == 1) "One application is" else "${items.size} applications are"} due a follow-up:</p><ul>" +
                    items.joinToString("") { (t, co) -> "<li>${esc(t)} at ${esc(co)}</li>" } +
                    "</ul><p>A short, polite note to the recruiter or hiring manager keeps you on their radar.</p>" +
                    """<p><a href="$trackerLink">Open your tracker</a></p>""",
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
