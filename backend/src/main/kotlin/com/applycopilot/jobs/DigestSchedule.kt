package com.applycopilot.jobs

import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** When the daily email goes out: the latest [hour] o'clock in [zone] at or before now. */
object DigestSchedule {
    fun latestSlot(now: Instant, zone: ZoneId, hour: Int): Instant {
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(hour, 0).atZone(zone)
        return (if (local.isBefore(today)) today.minusDays(1) else today).toInstant()
    }
}

/** Signs unsubscribe links so they work from the email without signing in, and can't be forged for other users. */
object UnsubscribeTokens {
    fun sign(secret: String, userId: UUID): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return mac.doFinal("digest-unsubscribe:$userId".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    }

    fun verify(secret: String, userId: UUID, token: String): Boolean =
        java.security.MessageDigest.isEqual(sign(secret, userId).toByteArray(), token.toByteArray())
}
