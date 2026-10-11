package com.applycopilot.api

import com.applycopilot.auth.JwtService
import com.applycopilot.config.AppConfig
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal
import io.ktor.util.date.GMTDate
import java.util.UUID

data class UserPrincipal(val userId: UUID)

fun ApplicationCall.userId(): UUID =
    principal<UserPrincipal>()?.userId ?: throw ApiException(HttpStatusCode.Unauthorized, "Not signed in")

/**
 * CSRF defence for state-changing requests, on top of the SameSite=Lax cookie:
 * a browser request that carries an Origin header must come from the web app.
 */
fun ApplicationCall.requireSameOrigin(config: AppConfig) {
    val origin = request.headers[HttpHeaders.Origin] ?: return
    if (origin.trimEnd('/') != config.webBaseUrl) throw ApiException(HttpStatusCode.Forbidden, "Request origin not allowed")
}

fun ApplicationCall.setSessionCookie(config: AppConfig, token: String, maxAgeSeconds: Int) {
    response.cookies.append(
        Cookie(
            name = JwtService.COOKIE,
            value = token,
            encoding = CookieEncoding.RAW,
            maxAge = maxAgeSeconds,
            path = "/",
            secure = config.secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}

fun ApplicationCall.clearSessionCookie(config: AppConfig) {
    response.cookies.append(
        Cookie(
            name = JwtService.COOKIE,
            value = "",
            encoding = CookieEncoding.RAW,
            maxAge = 0,
            expires = GMTDate(0L),
            path = "/",
            secure = config.secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        ),
    )
}
