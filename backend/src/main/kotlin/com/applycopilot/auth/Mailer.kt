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

    /** "You're in": [email] was invited from the waitlist; [loginLink] opens the sign-in page with the email filled in. */
    suspend fun sendInvite(email: String, loginLink: String)

    /** The daily match email: the best [items] of [total] new matches. */
    suspend fun sendDigest(email: String, items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String)
}

/**
 * Leads with the best match, so the subject alone says whether to open it: "86 fit · Senior Android Engineer at
 * Flipkart + 3 more". Long titles are shortened to keep it readable on a phone.
 */
internal fun digestSubject(items: List<DigestItem>, total: Int): String {
    val best = items.firstOrNull() ?: return if (total == 1) "1 new job match for you today" else "$total new job matches for you today"
    val title = if (best.title.length > 48) best.title.take(45).trimEnd() + "…" else best.title
    val company = if (best.company.isBlank()) "" else " at ${best.company.take(30)}"
    val more = (total - 1).takeIf { it > 0 }?.let { " + $it more" }.orEmpty()
    return "${best.score} fit · $title$company$more"
}

internal fun digestText(items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String): String = buildString {
    items.firstOrNull()?.let { best ->
        appendLine("Your best match today: ${best.title} at ${best.company}, fit ${best.score}")
        appendLine("Tailor it: ${best.link}")
        appendLine()
    }
    if (items.size > 1) appendLine(if (total - 1 == 1) "One more new match:" else "${total - 1} more new matches. The best ${items.size - 1}:")
    if (items.size > 1) appendLine()
    items.drop(1).forEach { appendLine("• ${it.title} at ${it.company}${if (it.location.isNotBlank()) " (${it.location})" else ""}, fit ${it.score}\n  ${it.link}") }
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

    val invites = mutableListOf<Pair<String, String>>()

    override suspend fun sendInvite(email: String, loginLink: String) {
        invites += email to loginLink
        log.info("Invite for {}: {}", email, loginLink)
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
    override suspend fun sendMagicLink(email: String, link: String) =
        send(email, EmailTemplates.magicLink(config.appName, link))

    override suspend fun sendDigest(email: String, items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String) =
        // One-click unsubscribe (RFC 8058), which mail apps show as an Unsubscribe button.
        send(
            email,
            EmailTemplates.digest(config.appName, items, total, feedLink, unsubscribeLink),
            headers = mapOf("List-Unsubscribe" to "<$unsubscribeLink>", "List-Unsubscribe-Post" to "List-Unsubscribe=One-Click"),
        )

    override suspend fun sendFollowUps(email: String, items: List<Pair<String, String>>, trackerLink: String) =
        send(email, EmailTemplates.followUps(config.appName, items, trackerLink))

    override suspend fun sendInvite(email: String, loginLink: String) =
        send(email, EmailTemplates.invite(config.appName, loginLink))

    private suspend fun send(to: String, content: EmailContent, headers: Map<String, String> = emptyMap()) {
        val body = buildJsonObject {
            put("from", config.from)
            putJsonArray("to") { add(to) }
            put("subject", content.subject)
            put("html", content.html)
            put("text", content.text)
            if (headers.isNotEmpty()) putJsonObject("headers") { headers.forEach { (k, v) -> put(k, v) } }
        }
        val response = http.post("https://api.resend.com/emails") {
            bearerAuth(config.resendApiKey)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        check(response.status.isSuccess()) { "Resend returned ${response.status}: ${response.bodyAsText().take(300)}" }
    }
}
