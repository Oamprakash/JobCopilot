package com.applycopilot

import com.applycopilot.api.digestRoutes
import com.applycopilot.api.feedbackRoutes
import com.applycopilot.api.proInterestRoutes
import com.applycopilot.api.waitlistAdminRoutes
import com.applycopilot.api.waitlistPublicRoutes
import com.applycopilot.jobs.FollowUpReminders
import com.applycopilot.api.extensionRoutes
import com.applycopilot.api.extensionPairingRoutes
import com.applycopilot.api.trackerRoutes
import com.applycopilot.api.adminDashboardRoutes
import com.applycopilot.api.ErrorResponse
import com.applycopilot.api.HealthResponse
import com.applycopilot.api.RootResponse
import com.applycopilot.api.UserPrincipal
import com.applycopilot.api.accountRoutes
import com.applycopilot.api.adminRoutes
import com.applycopilot.api.applicationRoutes
import com.applycopilot.api.feedRoutes
import com.applycopilot.api.authRoutes
import com.applycopilot.api.installErrorHandling
import com.applycopilot.api.resumeRoutes
import com.applycopilot.auth.JwtService
import com.applycopilot.config.AppConfig
import com.applycopilot.jobs.Scheduler
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import java.util.UUID

const val SESSION_AUTH = "session"

/** Characters allowed in an Authorization header blob; a JWT always fits. */
private val token68 = Regex("^[A-Za-z0-9._~+/-]+=*$")

val apiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main() {
    val config = AppConfig.fromEnv()
    val deps = Deps.create(config)
    deps.db.migrate()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(deps) }.start(wait = true)
}

fun Application.module(deps: Deps) {
    // Startup summary of optional features, so a setting that didn't load is easy to spot.
    val masked = deps.config.adminEmails.map { e -> e.substringBefore('@').take(2) + "…@" + e.substringAfter('@') }
    environment.log.info(
        "Admins: {} | Adzuna: {} | Claude: {} | Public preview: {}",
        if (masked.isEmpty()) "none (set ADMIN_EMAILS)" else masked.joinToString(", "),
        if (deps.config.adzuna != null) "on" else "off",
        if (deps.config.anthropicApiKey != null) "on" else "off",
        if (!deps.config.publicPreview) "off" else "on, invited: ${deps.config.inviteEmails.size.takeIf { it > 0 } ?: "anyone"}",
    )
    install(ContentNegotiation) { json(apiJson) }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path() != "/health" }
    }
    installErrorHandling()

    install(Authentication) {
        jwt(SESSION_AUTH) {
            realm = JwtService.ISSUER
            // The session lives in an HttpOnly cookie, not an Authorization header.
            authHeader { call ->
                call.request.cookies[JwtService.COOKIE]
                    ?.takeIf { it.isNotBlank() && token68.matches(it) }
                    ?.let { HttpAuthHeader.Single("Bearer", it) }
            }
            verifier(deps.jwt.verifier)
            validate { credential ->
                credential.payload.subject
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?.let { UserPrincipal(it) }
            }
            challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Not signed in")) }
        }
    }

    routing {
        // Opening the API port in a browser lands here; the app itself is the web server on WEB_BASE_URL.
        get("/") {
            call.respond(RootResponse("${deps.config.appName} API is running. Open the app at ${deps.config.webBaseUrl}", deps.config.webBaseUrl))
        }
        get("/health") {
            deps.db.tx { c -> c.prepareStatement("SELECT 1").use { it.executeQuery().use { rs -> rs.next() } } }
            call.respond(HealthResponse("ok"))
        }
        authRoutes(deps)
        // The browser extension authenticates with its own bearer token, not the session cookie.
        extensionRoutes(deps)
        // The daily email's unsubscribe link works without signing in; it's signed per user instead.
        digestRoutes(deps)
        waitlistPublicRoutes(deps)
        authenticate(SESSION_AUTH) {
            accountRoutes(deps)
            resumeRoutes(deps)
            feedRoutes(deps)
            applicationRoutes(deps)
            adminRoutes(deps)
            adminDashboardRoutes(deps)
            feedbackRoutes(deps)
            proInterestRoutes(deps)
            waitlistAdminRoutes(deps)
            trackerRoutes(deps)
            extensionPairingRoutes(deps)
        }
    }

    if (deps.config.schedulerEnabled) {
        Scheduler(
            deps.db, deps.ingestion, deps.matching, java.time.Duration.ofHours(deps.config.ingestIntervalHours),
            eventRetentionDays = deps.config.eventRetentionDays,
            followUps = FollowUpReminders(deps.db, deps.mailer, deps.config.webBaseUrl),
            digests = deps.digests,
        ).start(this)
    }

    monitor.subscribe(ApplicationStopped) { deps.close() }
}
