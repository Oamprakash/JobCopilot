package com.applycopilot.jobs

import com.applycopilot.auth.Mailer
import com.applycopilot.db.Db
import com.applycopilot.db.Tracker
import org.slf4j.LoggerFactory

/** Emails each user once per due follow-up: one message listing every tracked job whose follow-up date has passed. */
class FollowUpReminders(private val db: Db, private val mailer: Mailer, private val webBaseUrl: String) {
    private val log = LoggerFactory.getLogger(FollowUpReminders::class.java)

    /** Returns how many users were reminded. */
    suspend fun sendDue(): Int {
        val due = db.tx { Tracker.dueFollowUps(it) }
        var sent = 0
        for (user in due) {
            try {
                mailer.sendFollowUps(user.email, user.items, "$webBaseUrl/tracker")
                db.tx { Tracker.markReminded(it, user.userId) }
                sent++
            } catch (e: Exception) {
                // Left unmarked, so the next run tries again.
                log.warn("Follow-up reminder to user {} failed", user.userId, e)
            }
        }
        if (sent > 0) log.info("Sent follow-up reminders to {} users", sent)
        return sent
    }
}
