package com.applycopilot.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Tokens {
    private val random = SecureRandom()

    /** 32 random bytes, URL-safe base64 without padding (43 characters). */
    fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
