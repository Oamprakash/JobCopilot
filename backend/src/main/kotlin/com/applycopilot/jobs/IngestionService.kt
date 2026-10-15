package com.applycopilot.jobs

import com.applycopilot.core.Preferences
import com.applycopilot.db.AggregatorQueries
import com.applycopilot.db.Companies
import com.applycopilot.db.Company
import com.applycopilot.db.Db
import com.applycopilot.db.Jobs
import com.applycopilot.db.MatchRuns
import com.applycopilot.db.PreferencesTable
import com.applycopilot.db.Profiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SyncResult(val company: Company, val fetched: Int, val inserted: Int, val closed: Int, val error: String?)

/**
 * Keeps the jobs table in step with every active source: each company board in full, plus the aggregators
 * (Adzuna when configured; Himalayas' free remote-jobs API) for the searches users' preferences call for.
 */
class IngestionService(
    private val db: Db,
    private val ats: AtsClient,
    private val pauseMillis: Long = 500,
    private val adzuna: AdzunaClient? = null,
    private val adzunaDailyCalls: Int = 60,
    private val himalayas: HimalayasClient? = null,
    private val himalayasDailyCalls: Int = 120,
) {
    private val log = LoggerFactory.getLogger(IngestionService::class.java)

    suspend fun syncAll(): List<SyncResult> {
        val companies = db.tx { Companies.active(it) }
        val boards = companies.filter { it.ats !in AGGREGATORS }
        val results = boards.mapIndexed { i, company ->
            if (i > 0) delay(pauseMillis) // be polite to the board APIs
            sync(company)
        }
        val aggregators = companies.filter { it.ats in AGGREGATORS }.mapNotNull { company ->
            val source = sourceFor(company.ats) ?: return@mapNotNull null
            runSearches(company, source, source.queries(allUsersQueries()), maxPages = 2)
        }
        return (results + aggregators).also { all ->
            log.info(
                "Ingestion: {} sources, {} jobs fetched, {} new, {} closed, {} errors",
                all.size, all.sumOf { it.fetched }, all.sumOf { it.inserted }, all.sumOf { it.closed }, all.count { it.error != null },
            )
        }
    }

    /**
     * Runs this user's aggregator searches that haven't run in the last [FRESH] (first visit after changing
     * preferences, say), so their feed doesn't wait for the daily sync. A no-op when no aggregator is set up.
     */
    suspend fun syncAggregatorFor(userId: UUID): List<SyncResult> {
        val companies = db.tx { c -> Companies.active(c).filter { it.ats in AGGREGATORS } }
        if (companies.isEmpty()) return emptyList()
        val wants = db.tx { c ->
            val profile = Profiles.latest(c, userId)?.profile ?: return@tx null
            UserSearch(profile, PreferencesTable.find(c, userId) ?: Preferences())
        } ?: return emptyList()
        return companies.mapNotNull { company ->
            val source = sourceFor(company.ats) ?: return@mapNotNull null
            val queries = source.queries(listOf(wants))
            if (queries.isEmpty()) null else runSearches(company, source, queries, maxPages = 1)
        }
    }

    suspend fun sync(company: Company): SyncResult {
        if (company.ats in AGGREGATORS) {
            val source = sourceFor(company.ats)
                ?: return fail(company, if (company.ats == ADZUNA) "Set ADZUNA_APP_ID and ADZUNA_APP_KEY to use Adzuna" else "${company.name} is not set up")
            return runSearches(company, source, source.queries(allUsersQueries()), maxPages = 2)
        }
        val source = Ats.from(company.ats) ?: return fail(company, "Unknown ATS ${company.ats}")
        val postings = try {
            ats.fetch(source, company.boardToken)
        } catch (e: Exception) {
            // Leave existing jobs open: a failed fetch says nothing about whether they closed.
            return fail(company, e.message ?: e::class.simpleName ?: "fetch failed")
        }
        val (inserted, closed) = db.tx { c ->
            var inserted = 0
            postings.distinctBy { it.sourceJobId }.forEach { if (Jobs.upsert(c, company.id, source.id, it)) inserted++ }
            val closed = Jobs.closeMissing(c, company.id)
            Companies.recordSuccess(c, company.id)
            inserted to closed
        }
        val updated = db.tx { Companies.find(it, company.id) } ?: company
        return SyncResult(updated, postings.size, inserted, closed, null)
    }

    private data class UserSearch(val profile: com.applycopilot.core.Profile, val prefs: Preferences)

    private suspend fun allUsersQueries(): List<UserSearch> = db.tx { c ->
        MatchRuns.usersWithProfiles(c).mapNotNull { userId ->
            val profile = Profiles.latest(c, userId)?.profile ?: return@mapNotNull null
            UserSearch(profile, PreferencesTable.find(c, userId) ?: Preferences())
        }
    }

    /** One aggregator: how it turns users' wishes into searches, runs one, and how many calls it may make a day. */
    private inner class Source(
        val id: String,
        val label: String,
        val dailyCalls: Int,
        val pageSize: Int,
        val toQueries: (UserSearch) -> List<SearchQuery>,
        val search: suspend (SearchQuery, Int) -> SearchPage,
    ) {
        val recentCalls = ArrayDeque<Instant>()
        fun queries(users: List<UserSearch>): List<SearchQuery> = SearchQueries.merge(users.map(toQueries))
    }

    private val adzunaSource = adzuna?.let { client ->
        Source(ADZUNA, "Adzuna", adzunaDailyCalls, AdzunaClient.PAGE_SIZE,
            toQueries = { SearchQueries.forUser(it.profile, it.prefs, home = client.homeCountry) },
            search = { q, page -> client.search(q, page) })
    }

    private val himalayasSource = himalayas?.let { client ->
        Source(HIMALAYAS, "Himalayas", himalayasDailyCalls, HimalayasClient.PAGE_SIZE,
            toQueries = { SearchQueries.remoteFor(it.profile, it.prefs) },
            search = { q, page -> client.search(q, page) })
    }

    private fun sourceFor(ats: String): Source? = when (ats) {
        ADZUNA -> adzunaSource
        HIMALAYAS -> himalayasSource
        else -> null
    }

    // One search at a time across the app, so two feed refreshes can't double-spend a quota.
    private val searchLock = Mutex()

    /**
     * Runs [queries] not run in the last [FRESH], up to [maxPages] each, while today's call budget lasts.
     * Jobs unseen for [UNSEEN_DAYS] are closed: a search returns only part of the listings, so one miss means little.
     */
    private suspend fun runSearches(company: Company, source: Source, queries: List<SearchQuery>, maxPages: Int): SyncResult = searchLock.withLock {
        var fetched = 0
        var inserted = 0
        var errors = 0
        var ran = 0
        var lastError: String? = null
        var budgetSpent = false
        val cutoff = Instant.now().minus(FRESH)

        for (query in queries) {
            if (budgetSpent) break
            val last = db.tx { c -> AggregatorQueries.lastRun(c, source.id, query.country, query.what, query.where) }
            if (last != null && last.isAfter(cutoff)) continue
            ran++
            var total: Int? = null
            var error: String? = null
            for (page in 1..maxPages) {
                if (!db.tx { c -> AggregatorQueries.takeCall(c, source.id, source.dailyCalls) }) {
                    budgetSpent = true
                    break
                }
                throttle(source.recentCalls)
                val result = try {
                    source.search(query, page)
                } catch (e: Exception) {
                    error = e.message ?: e::class.simpleName ?: "search failed"
                    null
                } ?: break
                total = result.total
                fetched += result.postings.size
                inserted += db.tx { c -> result.postings.distinctBy { it.sourceJobId }.count { Jobs.upsert(c, company.id, source.id, it) } }
                if (result.postings.isEmpty() || result.total <= page * source.pageSize) break
            }
            if (error != null) {
                errors++
                lastError = error
                log.warn("{} search '{}' in '{}' ({}) failed: {}", source.label, query.what, query.where, query.country, error)
            }
            // A search cut short by the budget before its first page stays unrecorded, so it runs next time.
            if (error != null || total != null) db.tx { c -> AggregatorQueries.record(c, source.id, query.country, query.what, query.where, total, error) }
        }
        if (budgetSpent) log.info("{}: today's budget of {} calls is spent; remaining searches run tomorrow", source.label, source.dailyCalls)

        val closed = db.tx { c ->
            val closed = Jobs.closeUnseen(c, company.id, UNSEEN_DAYS)
            // Every search failing (a bad key, say) is worth showing in admin; one failed search among many isn't.
            if (ran > 0 && errors == ran) Companies.recordError(c, company.id, lastError ?: "search failed") else Companies.recordSuccess(c, company.id)
            closed
        }
        val updated = db.tx { Companies.find(it, company.id) } ?: company
        SyncResult(updated, fetched, inserted, closed, if (ran > 0 && errors == ran) lastError else null)
    }

    /** Stays under a source's per-minute limit (Adzuna's is 25) by spacing calls when a minute's worth have just gone out. */
    private suspend fun throttle(recentCalls: ArrayDeque<Instant>) {
        val now = Instant.now()
        while (recentCalls.isNotEmpty() && recentCalls.first().isBefore(now.minusSeconds(60))) recentCalls.removeFirst()
        if (recentCalls.size >= PER_MINUTE) {
            val wait = Duration.between(now, recentCalls.first().plusSeconds(60)).toMillis()
            if (wait > 0) delay(wait)
            recentCalls.removeFirst()
        }
        recentCalls.addLast(Instant.now())
    }

    private suspend fun fail(company: Company, error: String): SyncResult {
        log.warn("Sync failed for {} ({}:{}): {}", company.name, company.ats, company.boardToken, error)
        db.tx { Companies.recordError(it, company.id, error) }
        return SyncResult(company.copy(lastError = error), 0, 0, 0, error)
    }

    companion object {
        const val ADZUNA = "adzuna"
        const val HIMALAYAS = "himalayas"
        val AGGREGATORS = setOf(ADZUNA, HIMALAYAS)
        val FRESH: Duration = Duration.ofHours(20)
        const val UNSEEN_DAYS = 21
        const val PER_MINUTE = 20
    }
}
