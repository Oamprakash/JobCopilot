package com.applycopilot.api

import com.applycopilot.db.Events
import com.applycopilot.db.EventTypes
import com.applycopilot.Deps
import com.applycopilot.core.Emails
import com.applycopilot.core.Tokens
import com.applycopilot.db.MagicLinks
import com.applycopilot.db.Profiles
import com.applycopilot.db.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.time.Duration
import java.time.Instant

private val LINK_TTL: Duration = Duration.ofMinutes(15)
private const val MAX_LINKS_PER_HOUR = 5

/** Passwordless sign-in: request a link by email, then exchange the link's token for a session cookie. */
fun Route.authRoutes(deps: Deps) {
    route("/auth") {
        post("/magic-link") {
            call.requireSameOrigin(deps.config)
            val request = call.receive<MagicLinkRequest>()
            val email = Emails.normalize(request.email)
                ?: throw ApiException(HttpStatusCode.BadRequest, "Enter a valid email address")
            val isAdmin = email in deps.config.adminEmails
            val preview = deps.config.publicPreview
            if (!deps.mayRequestLink(email)) {
                throw ApiException(HttpStatusCode.Forbidden, "${deps.config.appName} is invite-only for now. Join the waitlist and we'll email you when you're in.")
            }

            val token = Tokens.newToken()
            val now = Instant.now()
            val allowed = deps.db.tx { c ->
                if (MagicLinks.countSince(c, email, now.minus(Duration.ofHours(1))) >= MAX_LINKS_PER_HOUR) {
                    false
                } else {
                    MagicLinks.insert(c, Tokens.sha256Hex(token), email, now.plus(LINK_TTL))
                    true
                }
            }
            if (!allowed) throw ApiException(HttpStatusCode.TooManyRequests, "Too many sign-in links requested. Try again in an hour.")

            val link = "${deps.config.webBaseUrl}/auth/verify?token=$token"
            deps.mailer.sendMagicLink(email, link)
            // On screen only outside prod, and never for an admin during a public preview (the API log has it).
            val showLink = !deps.config.isProd && !(preview && isAdmin)
            call.respond(MagicLinkResponse(ok = true, devLink = if (showLink) link else null))
        }

        post("/verify") {
            call.requireSameOrigin(deps.config)
            val request = call.receive<VerifyRequest>()
            val invalid = ApiException(HttpStatusCode.Unauthorized, "This sign-in link has expired or was already used. Request a new one.")
            if (request.token.length !in 20..100) throw invalid

            val (user, hasProfile) = deps.db.tx { c ->
                val email = MagicLinks.consume(c, Tokens.sha256Hex(request.token)) ?: return@tx null
                val user = Users.findOrCreate(c, email)
                Events.record(c, user.id, EventTypes.SIGNED_IN)
                user to Profiles.exists(c, user.id)
            } ?: throw invalid

            call.setSessionCookie(deps.config, deps.jwt.issue(user.id), deps.jwt.lifetimeSeconds)
            call.respond(MeResponse(user.id.toString(), user.email, user.plan, hasProfile, user.email in deps.config.adminEmails))
        }

        post("/logout") {
            call.requireSameOrigin(deps.config)
            call.clearSessionCookie(deps.config)
            call.respond(OkResponse())
        }
    }
}
