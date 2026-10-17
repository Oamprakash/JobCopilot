package com.applycopilot.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant

/**
 * "Continue with Google": the browser gets an ID token from Google Identity Services and we check it with Google's
 * tokeninfo endpoint, which verifies the signature. We then require that it was issued by Google for our client
 * ([clientId]), hasn't expired and carries a verified email. Only the email is used; nothing else from Google is kept.
 */
class GoogleSignIn(
    private val http: HttpClient,
    val clientId: String,
    private val base: String = "https://oauth2.googleapis.com",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The verified email in [idToken], or null when the token isn't a valid sign-in for this app. */
    suspend fun verifiedEmail(idToken: String, now: Instant = Instant.now()): String? {
        if (idToken.length !in 100..4096) return null
        val response = runCatching { http.get("$base/tokeninfo") { parameter("id_token", idToken) } }.getOrNull() ?: return null
        if (!response.status.isSuccess()) return null
        val claims = runCatching { json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull() ?: return null
        return check(claims, now)
    }

    internal fun check(claims: JsonObject, now: Instant): String? {
        fun str(k: String) = (claims[k] as? JsonPrimitive)?.content
        if (str("aud") != clientId) return null
        if (str("iss") !in setOf("accounts.google.com", "https://accounts.google.com")) return null
        val exp = str("exp")?.toLongOrNull() ?: return null
        if (Instant.ofEpochSecond(exp).isBefore(now)) return null
        if (str("email_verified") != "true") return null
        return str("email")
    }
}
