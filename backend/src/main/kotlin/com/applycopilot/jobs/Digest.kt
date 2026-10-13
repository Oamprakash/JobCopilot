package com.applycopilot.jobs

import com.applycopilot.auth.DigestItem
import com.applycopilot.auth.Mailer
import com.applycopilot.db.Db
import com.applycopilot.db.Digests
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * The morning email with each user's best new matches. Runs hourly; each user gets at most one per daily slot
 * ([hour] o'clock in [zone]), and only when there's something new since the last one.
 */
class DigestService(
    private val db: Db,
    private val mailer: Mailer,
    private val webBaseUrl: String,
    private val secret: String,
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata"),
    private val hour: Int = 8,
    private val minScore: Int = 40,
    private val maxJobs: Int = 5,
) {
    private val log = LoggerFactory.getLogger(DigestService::class.java)

    fun unsubscribeLink(userId: UUID) = "$webBaseUrl/api/digest/unsubscribe?u=$userId&t=${UnsubscribeTokens.sign(secret, userId)}"

    /** Returns how many emails went out. */
    suspend fun sendDue(now: Instant = Instant.now()): Int {
        val slot = DigestSchedule.latestSlot(now, zone, hour)
        val recipients = db.tx { Digests.recipients(it, slot) }
        var sent = 0
        for (r in recipients) {
            val since = r.lastSentAt ?: now.minus(Duration.ofHours(24))
            val (jobs, total) = db.tx { Digests.newMatches(it, r.userId, since, minScore, maxJobs) }
            if (jobs.isEmpty()) continue
            try {
                mailer.sendDigest(
                    r.email,
                    jobs.map { DigestItem(it.title, it.company, it.location, it.score, "$webBaseUrl/apply/${it.jobId}") },
                    total,
                    "$webBaseUrl/feed",
                    unsubscribeLink(r.userId),
                )
                db.tx { c ->
                    Digests.markSent(c, r.userId, now)
                    Events.record(c, r.userId, EventTypes.DIGEST_SENT, data = Events.data("jobs" to total))
                }
                sent++
            } catch (e: Exception) {
                log.warn("Daily match email to user {} failed", r.userId, e)
            }
        }
        if (sent > 0) log.info("Sent {} daily match emails", sent)
        return sent
    }
}
