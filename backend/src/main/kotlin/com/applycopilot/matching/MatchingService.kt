package com.applycopilot.matching

import com.applycopilot.core.Preferences
import com.applycopilot.db.Db
import com.applycopilot.db.MatchRun
import com.applycopilot.db.MatchRuns
import com.applycopilot.db.Matches
import com.applycopilot.db.PreferencesTable
import com.applycopilot.db.Profiles
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds a user's feed: hard filter → lexical shortlist → fit score.
 * Claude scores the top of the shortlist up to a daily budget; the rule-based scorer covers the rest.
 */
class MatchingService(
    private val db: Db,
    private val claude: ClaudeJobScorer?,
    private val shortlistSize: Int = 50,
    private val dailyLlmLimit: Int = 50,
    private val batchSize: Int = 8,
    private val parallelBatches: Int = 3,
) {
    private val log = LoggerFactory.getLogger(MatchingService::class.java)
    private val locks = ConcurrentHashMap<UUID, Mutex>()

    /** Runs matching for one user. Returns null when the user has no profile yet. */
    suspend fun refresh(userId: UUID): MatchRun? = locks.computeIfAbsent(userId) { Mutex() }.withLock {
        val setup = db.tx { c ->
            val stored = Profiles.latest(c, userId) ?: return@tx null
            Setup(
                profileVersion = stored.version,
                profile = stored.profile,
                prefs = PreferencesTable.find(c, userId) ?: Preferences(),
                runId = MatchRuns.start(c, userId),
                candidates = com.applycopilot.db.Jobs.candidatesFor(c, userId),
                llmUsed = Matches.llmScoredSince(c, userId, Instant.now().minus(Duration.ofHours(24))),
            )
        } ?: return@withLock null

        try {
            val verdicts = setup.candidates.map { it to HardFilter.evaluate(it, setup.profile, setup.prefs) }
            val passed = verdicts.filter { it.second.pass }.map { it.first }
            val filtered = verdicts.filterNot { it.second.pass }.groupingBy { filterCategory(it.second.reason) }.eachCount()
            val suggestions = Suggestions.compute(verdicts.filterNot { it.second.pass }.map { it.first }, setup.profile, setup.prefs)
            val shortlist = LexicalRanker.rank(passed, setup.profile).take(shortlistSize)
            val budget = if (claude == null) 0 else (dailyLlmLimit - setup.llmUsed).coerceAtLeast(0)
            val forLlm = shortlist.take(budget)
            val llmScores = scoreWithClaude(setup, forLlm.map { it.first })

            var llmCount = 0
            val scored = shortlist.map { (job, similarity) ->
                val fromLlm = llmScores[job.id]
                if (fromLlm != null) llmCount++
                Triple(job, fromLlm ?: HeuristicScorer.score(job, setup.profile, setup.prefs, similarity), if (fromLlm != null) "claude" else "heuristic")
            }

            db.tx { c ->
                scored.forEach { (job, fit, scorer) -> Matches.insert(c, userId, job.id, fit, scorer, setup.profileVersion) }
                MatchRuns.finish(c, setup.runId, setup.candidates.size, passed.size, scored.size, llmCount, filtered = filtered, suggestions = suggestions)
            }
            log.info("Matching for {}: {} candidates, {} passed, {} scored ({} by Claude)", userId, setup.candidates.size, passed.size, scored.size, llmCount)
        } catch (e: Exception) {
            log.error("Matching failed for {}", userId, e)
            db.tx { c -> MatchRuns.finish(c, setup.runId, setup.candidates.size, 0, 0, 0, e.message ?: "failed") }
        }
        db.tx { c -> MatchRuns.latest(c, userId) }
    }

    private suspend fun scoreWithClaude(setup: Setup, jobs: List<JobForMatching>): Map<UUID, FitScore> {
        val scorer = claude ?: return emptyMap()
        if (jobs.isEmpty()) return emptyMap()
        val gate = Semaphore(parallelBatches)
        return coroutineScope {
            jobs.chunked(batchSize).map { batch ->
                async {
                    gate.withPermit {
                        try {
                            scorer.score(setup.profile, setup.prefs, batch)
                        } catch (e: Exception) {
                            // That batch falls back to the rule-based scorer.
                            log.warn("Claude scoring failed for a batch of {}", batch.size, e)
                            emptyMap()
                        }
                    }
                }
            }.awaitAll().fold(emptyMap<UUID, FitScore>()) { acc, m -> acc + m }
        }
    }

    /** Groups filter reasons into the buckets the feed explains to the user. */
    private fun filterCategory(reason: String): String = when {
        reason == "location" -> "location"
        reason == "role" -> "role"
        reason == "company on skip list" -> "skippedCompany"
        reason == "entry-level role" || reason == "junior role" -> "seniority"
        reason.startsWith("needs") || reason.startsWith("asks") -> "experience"
        else -> "other"
    }

    private data class Setup(
        val profileVersion: Int,
        val profile: com.applycopilot.core.Profile,
        val prefs: Preferences,
        val runId: UUID,
        val candidates: List<JobForMatching>,
        val llmUsed: Int,
    )
}
