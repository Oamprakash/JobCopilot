package com.applycopilot

import com.applycopilot.api.ErrorResponse
import com.applycopilot.api.HealthResponse
import com.applycopilot.api.UserPrincipal
import com.applycopilot.api.accountRoutes
import com.applycopilot.api.authRoutes
import com.applycopilot.api.installErrorHandling
import com.applycopilot.api.resumeRoutes
import com.applycopilot.auth.JwtService
import com.applycopilot.config.AppConfig
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
        get("/health") {
            deps.db.tx { c -> c.prepareStatement("SELECT 1").use { it.executeQuery().use { rs -> rs.next() } } }
            call.respond(HealthResponse("ok"))
        }
        authRoutes(deps)
        authenticate(SESSION_AUTH) {
            accountRoutes(deps)
            resumeRoutes(deps)
        }
    }

    monitor.subscribe(ApplicationStopped) { deps.close() }
}
