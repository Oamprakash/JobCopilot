package com.applycopilot.jobs

import com.applycopilot.db.Companies
import com.applycopilot.db.Company
import com.applycopilot.db.Db
import com.applycopilot.db.Jobs
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

data class SyncResult(val company: Company, val fetched: Int, val inserted: Int, val closed: Int, val error: String?)

/** Pulls every active company's board and keeps the jobs table in step with it. */
class IngestionService(private val db: Db, private val ats: AtsClient, private val pauseMillis: Long = 500) {
    private val log = LoggerFactory.getLogger(IngestionService::class.java)

    suspend fun syncAll(): List<SyncResult> {
        val companies = db.tx { Companies.active(it) }
        return companies.mapIndexed { i, company ->
            if (i > 0) delay(pauseMillis) // be polite to the board APIs
            sync(company)
        }.also { results ->
            log.info(
                "Ingestion: {} boards, {} jobs fetched, {} new, {} closed, {} errors",
                results.size, results.sumOf { it.fetched }, results.sumOf { it.inserted }, results.sumOf { it.closed },
                results.count { it.error != null },
            )
        }
    }

    suspend fun sync(company: Company): SyncResult {
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

    private suspend fun fail(company: Company, error: String): SyncResult {
        log.warn("Sync failed for {} ({}:{}): {}", company.name, company.ats, company.boardToken, error)
        db.tx { Companies.recordError(it, company.id, error) }
        return SyncResult(company.copy(lastError = error), 0, 0, 0, error)
    }
}
