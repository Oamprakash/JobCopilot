package com.applycopilot.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID

/** Signs and verifies the session token stored in the HttpOnly "session" cookie. */
class JwtService(secret: String, private val lifetime: Duration = Duration.ofDays(30)) {
    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build()

    val lifetimeSeconds: Int get() = lifetime.seconds.toInt()

    fun issue(userId: UUID, now: Instant = Instant.now()): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(userId.toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(lifetime)))
            .sign(algorithm)

    companion object {
        const val ISSUER = "apply-copilot"
        const val AUDIENCE = "apply-copilot-web"
        const val COOKIE = "session"
    }
}
