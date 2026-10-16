package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.core.Emails
import com.applycopilot.db.Waitlist
import com.applycopilot.db.WaitlistEntry
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

/** What the public pages need to know: whether sign-up is open or invite-only. */
@Serializable
data class SiteDto(val inviteOnly: Boolean)

@Serializable
data class JoinWaitlistRequest(val email: String, val note: String? = null, val acquisition: com.applycopilot.core.Acquisition? = null)

/** "waiting": on the list. "invited": this email can sign in now. "open": sign-up isn't invite-only, so just sign in. */
@Serializable
data class JoinWaitlistResponse(val status: String)

@Serializable
data class WaitlistEntryDto(
    val id: String, val email: String, val note: String?, val status: String, val createdAt: String, val invitedAt: String?,
    val source: String? = null,
)

@Serializable
data class WaitlistResponse(val items: List<WaitlistEntryDto>, val waiting: Int, val invited: Int)

private fun WaitlistEntry.toDto() = WaitlistEntryDto(id.toString(), email, note, status, createdAt.toString(), invitedAt?.toString(), source)

/** Hosts of this site, so links from our own pages don't count as a referrer. */
internal fun Deps.ownHosts(): Set<String> =
    config.allowedOrigins.mapNotNull { runCatching { java.net.URI(it).host?.lowercase()?.removePrefix("www.") }.getOrNull() }.toSet()

/** Sign-up is invite-only in preview mode with an invite list (INVITE_EMAILS); preview without one is open to anyone. */
internal fun Deps.inviteOnly() = config.publicPreview && config.inviteEmails.isNotEmpty()

/** Can [email] (normalized) sign in? Admins always; otherwise INVITE_EMAILS or an invite from the waitlist. */
internal suspend fun Deps.mayRequestLink(email: String): Boolean =
    !inviteOnly() || email in config.adminEmails || email in config.inviteEmails || db.tx { c -> Waitlist.isInvited(c, email) }

/** Public: site info and joining the waitlist. Mount outside authenticate. */
fun Route.waitlistPublicRoutes(deps: Deps) {
    get("/site") { call.respond(SiteDto(deps.inviteOnly())) }

    post("/waitlist") {
        call.requireSameOrigin(deps.config)
        val request = call.receive<JoinWaitlistRequest>()
        val email = Emails.normalize(request.email) ?: throw ApiException(HttpStatusCode.BadRequest, "Enter a valid email address")
        val note = request.note?.trim()?.take(300)?.ifEmpty { null }
        if (deps.mayRequestLink(email)) {
            call.respond(JoinWaitlistResponse(if (deps.inviteOnly()) "invited" else "open"))
            return@post
        }
        val acquisition = request.acquisition?.let { com.applycopilot.core.AcquisitionRules.normalize(it, deps.ownHosts()) }
        val entry = deps.db.tx { c -> Waitlist.join(c, email, note, acquisition) }
        call.respond(JoinWaitlistResponse(entry.status))
    }
}

/** Admin: the waitlist, inviting (with an email) and deleting entries. Mount inside authenticate("session"). */
fun Route.waitlistAdminRoutes(deps: Deps) {
    val log = LoggerFactory.getLogger("Waitlist")
    route("/admin/waitlist") {
        get {
            call.requireAdmin(deps)
            val items = deps.db.tx { c -> Waitlist.list(c) }
            call.respond(WaitlistResponse(items.map { it.toDto() }, items.count { it.status == "waiting" }, items.count { it.status == "invited" }))
        }

        post("/{id}/invite") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val entry = deps.db.tx { c -> Waitlist.invite(c, call.entryId()) } ?: throw ApiException(HttpStatusCode.NotFound, "Not on the waitlist")
            val loginLink = "${deps.config.webBaseUrl}/login?email=${java.net.URLEncoder.encode(entry.email, Charsets.UTF_8)}"
            try {
                deps.mailer.sendInvite(entry.email, loginLink)
            } catch (e: Exception) {
                // The invite stands; the person can still sign in. The admin sees that the email didn't go.
                log.warn("Invite email to waitlist entry {} failed", entry.id, e)
                throw ApiException(HttpStatusCode.BadGateway, "Invited, but the email didn't send. They can sign in at ${deps.config.webBaseUrl}/login.")
            }
            call.respond(entry.toDto())
        }

        delete("/{id}") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            if (!deps.db.tx { c -> Waitlist.delete(c, call.entryId()) }) throw ApiException(HttpStatusCode.NotFound, "Not on the waitlist")
            call.respond(OkResponse())
        }
    }
}

private fun ApplicationCall.entryId(): UUID =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "Not on the waitlist")
