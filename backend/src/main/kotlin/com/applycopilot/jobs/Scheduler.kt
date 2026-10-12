package com.applycopilot.jobs

import com.applycopilot.db.Events
import com.applycopilot.db.Companies
import com.applycopilot.db.Db
import com.applycopilot.db.MatchRuns
import com.applycopilot.matching.MatchingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.Duration

/** In-process schedule: sync every board, then rebuild every user's feed, every [interval]. */
class Scheduler(
    private val db: Db,
    private val ingestion: IngestionService,
    private val matching: MatchingService,
    private val interval: Duration,
    private val firstRunDelay: Duration = Duration.ofMinutes(1),
    /** Activity events older than this are deleted on each run. */
    private val eventRetentionDays: Int = 180,
) {
    private val log = LoggerFactory.getLogger(Scheduler::class.java)

    fun start(scope: CoroutineScope): Job = scope.launch {
        // A host that sleeps when idle (Render's free tier) restarts the app often; don't resync every wake-up
        // if the last sync is still within the interval.
        val last = runCatching { db.tx { Companies.lastSyncedAt(it) } }.getOrNull()
        val due = last?.plus(interval)
        val wait = if (due == null) firstRunDelay else maxOf(firstRunDelay, Duration.between(java.time.Instant.now(), due))
        if (wait > firstRunDelay) log.info("Next scheduled sync in {} min", wait.toMinutes())
        delay(wait.toMillis())
        while (isActive) {
            runOnce()
            delay(interval.toMillis())
        }
    }

    suspend fun runOnce() {
        try {
            val purged = db.tx { Events.deleteOlderThan(it, eventRetentionDays) }
            if (purged > 0) log.info("Deleted {} activity events older than {} days", purged, eventRetentionDays)
        } catch (e: Exception) {
            log.error("Event retention cleanup failed", e)
        }
        try {
            ingestion.syncAll()
            val users = db.tx { MatchRuns.usersWithProfiles(it) }
            users.forEach { userId ->
                try {
                    matching.refresh(userId)
                } catch (e: Exception) {
                    log.error("Scheduled matching failed for {}", userId, e)
                }
            }
        } catch (e: Exception) {
            log.error("Scheduled run failed", e)
        }
    }
}
