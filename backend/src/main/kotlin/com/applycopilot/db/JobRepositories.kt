package com.applycopilot.db

import com.applycopilot.jobs.JobPosting
import com.applycopilot.matching.FitScore
import com.applycopilot.matching.JobForMatching
import com.applycopilot.matching.OpenJob
import com.applycopilot.matching.Suggestion
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

data class Company(
    val id: UUID,
    val name: String,
    val ats: String,
    val boardToken: String,
    val active: Boolean,
    val lastSyncedAt: Instant?,
    val lastError: String?,
    val openJobs: Int,
)

object Companies {
    private const val COLUMNS = "id, name, ats, board_token, active, last_synced_at, last_error, open_jobs"

    fun all(c: Connection): List<Company> =
        c.prepareStatement("SELECT $COLUMNS FROM companies ORDER BY name").use { st ->
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toCompany()) } }
        }

    fun active(c: Connection): List<Company> = all(c).filter { it.active }

    fun find(c: Connection, id: UUID): Company? =
        c.prepareStatement("SELECT $COLUMNS FROM companies WHERE id = ?").use { st ->
            st.setObject(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.toCompany() else null }
        }

    /** Returns null when this board is already registered. */
    fun insert(c: Connection, name: String, ats: String, token: String): Company? =
        c.prepareStatement(
            "INSERT INTO companies (id, name, ats, board_token) VALUES (?, ?, ?, ?) ON CONFLICT (ats, board_token) DO NOTHING RETURNING $COLUMNS",
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.setString(2, name)
            st.setString(3, ats)
            st.setString(4, token)
            st.executeQuery().use { rs -> if (rs.next()) rs.toCompany() else null }
        }

    fun setActive(c: Connection, id: UUID, active: Boolean): Boolean =
        c.prepareStatement("UPDATE companies SET active = ? WHERE id = ?").use { st ->
            st.setBoolean(1, active)
            st.setObject(2, id)
            st.executeUpdate() > 0
        }

    fun recordSuccess(c: Connection, id: UUID) {
        c.prepareStatement(
            """
            UPDATE companies SET last_synced_at = now(), last_error = NULL,
                open_jobs = (SELECT count(*) FROM jobs WHERE company_id = ? AND closed_at IS NULL)
            WHERE id = ?
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, id)
            st.setObject(2, id)
            st.executeUpdate()
        }
    }

    fun recordError(c: Connection, id: UUID, error: String) {
        c.prepareStatement("UPDATE companies SET last_error = ? WHERE id = ?").use { st ->
            st.setString(1, error.take(500))
            st.setObject(2, id)
            st.executeUpdate()
        }
    }

    private fun ResultSet.toCompany() = Company(
        id = getObject("id", UUID::class.java),
        name = getString("name"),
        ats = getString("ats"),
        boardToken = getString("board_token"),
        active = getBoolean("active"),
        lastSyncedAt = getTimestamp("last_synced_at")?.toInstant(),
        lastError = getString("last_error"),
        openJobs = getInt("open_jobs"),
    )
}

data class JobDetail(
    val id: UUID,
    val company: String,
    val title: String,
    val location: String,
    val remote: Boolean,
    val description: String,
    val applyUrl: String,
    val postedAt: Instant?,
    val closed: Boolean,
)

object Jobs {
    /** Inserts or refreshes a posting; returns true when it is new. Reopens a job that had been closed. */
    fun upsert(c: Connection, companyId: UUID, source: String, p: JobPosting): Boolean =
        c.prepareStatement(
            """
            INSERT INTO jobs (id, company_id, source, source_job_id, title, location, remote, description, apply_url, posted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (source, source_job_id) DO UPDATE SET
                title = EXCLUDED.title, location = EXCLUDED.location, remote = EXCLUDED.remote,
                description = EXCLUDED.description, apply_url = EXCLUDED.apply_url,
                posted_at = coalesce(EXCLUDED.posted_at, jobs.posted_at),
                last_seen_at = now(), closed_at = NULL
            RETURNING (xmax = 0) AS inserted
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.setObject(2, companyId)
            st.setString(3, source)
            st.setString(4, p.sourceJobId)
            st.setString(5, p.title.take(300))
            st.setString(6, p.location.take(300))
            st.setBoolean(7, p.remote)
            st.setString(8, p.description)
            st.setString(9, p.applyUrl.take(1000))
            if (p.postedAt == null) st.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE) else st.setTimestamp(10, Timestamp.from(p.postedAt))
            st.executeQuery().use { rs -> rs.next(); rs.getBoolean("inserted") }
        }

    /**
     * Closes this company's open jobs that the current sync did not see. Run in the same transaction as the
     * upserts: now() is the transaction's start time, so every row touched by this sync has last_seen_at = now().
     */
    fun closeMissing(c: Connection, companyId: UUID): Int =
        c.prepareStatement("UPDATE jobs SET closed_at = now() WHERE company_id = ? AND closed_at IS NULL AND last_seen_at < now()").use { st ->
            st.setObject(1, companyId)
            st.executeUpdate()
        }

    /** Open, recent jobs this user has no match row for yet. */
    fun candidatesFor(c: Connection, userId: UUID, limit: Int = 3000): List<JobForMatching> =
        c.prepareStatement(
            """
            SELECT j.id, co.name, j.title, j.location, j.remote, j.description
            FROM jobs j JOIN companies co ON co.id = j.company_id
            WHERE j.closed_at IS NULL AND co.active
              AND (j.posted_at IS NULL OR j.posted_at > now() - interval '60 days')
              AND NOT EXISTS (SELECT 1 FROM matches m WHERE m.user_id = ? AND m.job_id = j.id)
            ORDER BY j.first_seen_at DESC
            LIMIT ?
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setInt(2, limit)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        JobForMatching(
                            id = rs.getObject(1, UUID::class.java),
                            company = rs.getString(2),
                            title = rs.getString(3),
                            location = rs.getString(4),
                            remote = rs.getBoolean(5),
                            description = rs.getString(6),
                        ),
                    )
                }
            }
        }

    /** Title and location of every job a feed could show (open, active board, posted in the last 60 days). */
    fun openTitlesAndLocations(c: Connection): List<OpenJob> =
        c.prepareStatement(
            """
            SELECT j.title, j.location
            FROM jobs j JOIN companies co ON co.id = j.company_id
            WHERE j.closed_at IS NULL AND co.active
              AND (j.posted_at IS NULL OR j.posted_at > now() - interval '60 days')
            """.trimIndent(),
        ).use { st ->
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(OpenJob(rs.getString(1), rs.getString(2))) } }
        }

    fun find(c: Connection, id: UUID): JobDetail? =
        c.prepareStatement(
            """
            SELECT j.id, co.name, j.title, j.location, j.remote, j.description, j.apply_url, j.posted_at, j.closed_at
            FROM jobs j JOIN companies co ON co.id = j.company_id WHERE j.id = ?
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, id)
            st.executeQuery().use { rs ->
                if (!rs.next()) null else JobDetail(
                    id = rs.getObject(1, UUID::class.java),
                    company = rs.getString(2),
                    title = rs.getString(3),
                    location = rs.getString(4),
                    remote = rs.getBoolean(5),
                    description = rs.getString(6),
                    applyUrl = rs.getString(7),
                    postedAt = rs.getTimestamp(8)?.toInstant(),
                    closed = rs.getTimestamp(9) != null,
                )
            }
        }
}

data class FeedRow(
    val job: JobDetail,
    val score: Int,
    val reasons: List<String>,
    val missingSkills: List<String>,
    val scorer: String,
    val status: String,
)

object Matches {
    fun insert(c: Connection, userId: UUID, jobId: UUID, fit: FitScore, scorer: String, profileVersion: Int) {
        c.prepareStatement(
            """
            INSERT INTO matches (user_id, job_id, score, reasons, missing_skills, scorer, profile_version)
            VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, job_id) DO NOTHING
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.setInt(3, fit.score.coerceIn(0, 100))
            st.setArray(4, c.createArrayOf("text", fit.reasons.toTypedArray()))
            st.setArray(5, c.createArrayOf("text", fit.missingSkills.toTypedArray()))
            st.setString(6, scorer)
            st.setInt(7, profileVersion)
            st.executeUpdate()
        }
    }

    fun feed(c: Connection, userId: UUID, status: String, minScore: Int, limit: Int = 100): List<FeedRow> =
        c.prepareStatement(
            """
            SELECT j.id, co.name, j.title, j.location, j.remote, j.apply_url, j.posted_at, j.closed_at,
                   m.score, m.reasons, m.missing_skills, m.scorer, m.status
            FROM matches m JOIN jobs j ON j.id = m.job_id JOIN companies co ON co.id = j.company_id
            WHERE m.user_id = ? AND m.status = ? AND m.score >= ?
              AND (m.status <> 'new' OR j.closed_at IS NULL)
            ORDER BY m.score DESC, j.posted_at DESC NULLS LAST
            LIMIT ?
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setString(2, status)
            st.setInt(3, minScore)
            st.setInt(4, limit)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        FeedRow(
                            job = JobDetail(
                                id = rs.getObject(1, UUID::class.java),
                                company = rs.getString(2),
                                title = rs.getString(3),
                                location = rs.getString(4),
                                remote = rs.getBoolean(5),
                                description = "",
                                applyUrl = rs.getString(6),
                                postedAt = rs.getTimestamp(7)?.toInstant(),
                                closed = rs.getTimestamp(8) != null,
                            ),
                            score = rs.getInt(9),
                            reasons = rs.textArray(10),
                            missingSkills = rs.textArray(11),
                            scorer = rs.getString(12),
                            status = rs.getString(13),
                        ),
                    )
                }
            }
        }

    fun setStatus(c: Connection, userId: UUID, jobId: UUID, status: String): Boolean =
        c.prepareStatement("UPDATE matches SET status = ?, status_changed_at = now() WHERE user_id = ? AND job_id = ?").use { st ->
            st.setString(1, status)
            st.setObject(2, userId)
            st.setObject(3, jobId)
            st.executeUpdate() > 0
        }

    /** Drops unreviewed matches so the next run rescores them against a changed profile or preferences. */
    fun clearNew(c: Connection, userId: UUID): Int =
        c.prepareStatement("DELETE FROM matches WHERE user_id = ? AND status = 'new'").use { st ->
            st.setObject(1, userId)
            st.executeUpdate()
        }

    fun llmScoredSince(c: Connection, userId: UUID, since: Instant): Int =
        c.prepareStatement("SELECT count(*) FROM matches WHERE user_id = ? AND scorer = 'claude' AND scored_at > ?").use { st ->
            st.setObject(1, userId)
            st.setTimestamp(2, Timestamp.from(since))
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun ResultSet.textArray(index: Int): List<String> =
        (getArray(index)?.array as? Array<Any?>)?.mapNotNull { it as String? } ?: emptyList()
}

data class MatchRun(
    val id: UUID,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val considered: Int,
    val passedFilter: Int,
    val scored: Int,
    val llmScored: Int,
    val error: String?,
    /** Jobs the hard filter dropped, by reason: role, location, experience, seniority, skippedCompany. */
    val filtered: Map<String, Int> = emptyMap(),
    val suggestions: List<Suggestion> = emptyList(),
)

object MatchRuns {
    private val json = Json { ignoreUnknownKeys = true }
    private val countsSerializer = MapSerializer(String.serializer(), Int.serializer())
    private val suggestionsSerializer = ListSerializer(Suggestion.serializer())

    fun start(c: Connection, userId: UUID): UUID {
        val id = UUID.randomUUID()
        c.prepareStatement("INSERT INTO match_runs (id, user_id) VALUES (?, ?)").use { st ->
            st.setObject(1, id)
            st.setObject(2, userId)
            st.executeUpdate()
        }
        return id
    }

    fun finish(
        c: Connection,
        id: UUID,
        considered: Int,
        passed: Int,
        scored: Int,
        llm: Int,
        error: String? = null,
        filtered: Map<String, Int> = emptyMap(),
        suggestions: List<Suggestion> = emptyList(),
    ) {
        c.prepareStatement(
            """
            UPDATE match_runs SET finished_at = now(), considered = ?, passed_filter = ?, scored = ?, llm_scored = ?, error = ?,
                filtered = CAST(? AS jsonb), suggestions = CAST(? AS jsonb)
            WHERE id = ?
            """.trimIndent(),
        ).use { st ->
            st.setInt(1, considered)
            st.setInt(2, passed)
            st.setInt(3, scored)
            st.setInt(4, llm)
            st.setString(5, error?.take(500))
            st.setString(6, json.encodeToString(countsSerializer, filtered))
            st.setString(7, json.encodeToString(suggestionsSerializer, suggestions))
            st.setObject(8, id)
            st.executeUpdate()
        }
    }

    fun latest(c: Connection, userId: UUID): MatchRun? =
        c.prepareStatement(
            """
            SELECT id, started_at, finished_at, considered, passed_filter, scored, llm_scored, error, filtered, suggestions
            FROM match_runs WHERE user_id = ? ORDER BY started_at DESC LIMIT 1
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs ->
                if (!rs.next()) null else MatchRun(
                    id = rs.getObject("id", UUID::class.java),
                    startedAt = rs.getTimestamp("started_at").toInstant(),
                    finishedAt = rs.getTimestamp("finished_at")?.toInstant(),
                    considered = rs.getInt("considered"),
                    passedFilter = rs.getInt("passed_filter"),
                    scored = rs.getInt("scored"),
                    llmScored = rs.getInt("llm_scored"),
                    error = rs.getString("error"),
                    filtered = rs.getString("filtered")?.let { runCatching { json.decodeFromString(countsSerializer, it) }.getOrNull() }.orEmpty(),
                    suggestions = rs.getString("suggestions")?.let { runCatching { json.decodeFromString(suggestionsSerializer, it) }.getOrNull() }.orEmpty(),
                )
            }
        }

    /** When the inputs to matching last changed: the newest profile version or preferences save. */
    fun inputsChangedAt(c: Connection, userId: UUID): Instant? =
        c.prepareStatement(
            """
            SELECT greatest(
                (SELECT max(created_at) FROM profiles WHERE user_id = ?),
                (SELECT updated_at FROM preferences WHERE user_id = ?)
            )
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, userId)
            st.executeQuery().use { rs -> rs.next(); rs.getTimestamp(1)?.toInstant() }
        }

    fun usersWithProfiles(c: Connection): List<UUID> =
        c.prepareStatement("SELECT DISTINCT user_id FROM profiles").use { st ->
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getObject(1, UUID::class.java)) } }
        }
}
