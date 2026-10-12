package com.applycopilot.db

import com.applycopilot.core.Tokens
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Duration
import java.time.Instant
import java.util.UUID

object Stages {
    val all = listOf("saved", "applied", "interview", "offer", "rejected", "withdrawn")
    /** Stages where a follow-up still makes sense. */
    val open = setOf("saved", "applied", "interview")
}

data class TrackerItem(
    val jobId: UUID,
    val title: String,
    val company: String,
    val location: String,
    val applyUrl: String,
    val source: String,
    val closed: Boolean,
    val stage: String,
    val appliedAt: Instant?,
    val followUpAt: Instant?,
    val notes: String,
    val updatedAt: Instant,
    val createdAt: Instant,
    /** The tailored application's status for this job, if one exists. */
    val applicationStatus: String?,
)

data class DueFollowUp(val userId: UUID, val email: String, val items: List<Pair<String, String>>)

object Tracker {
    private const val SELECT = """
        SELECT t.job_id, j.title, coalesce(j.employer, co.name), j.location, j.apply_url, j.source, j.closed_at IS NOT NULL,
               t.stage, t.applied_at, t.follow_up_at, t.notes, t.updated_at, t.created_at, a.status
        FROM tracker t JOIN jobs j ON j.id = t.job_id JOIN companies co ON co.id = j.company_id
        LEFT JOIN applications a ON a.user_id = t.user_id AND a.job_id = t.job_id
    """

    fun list(c: Connection, userId: UUID): List<TrackerItem> =
        c.prepareStatement("$SELECT WHERE t.user_id = ? ORDER BY t.updated_at DESC").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toItem()) } }
        }

    fun find(c: Connection, userId: UUID, jobId: UUID): TrackerItem? =
        c.prepareStatement("$SELECT WHERE t.user_id = ? AND t.job_id = ?").use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.executeQuery().use { rs -> if (rs.next()) rs.toItem() else null }
        }

    /** Starts tracking a job at [stage] if it isn't tracked yet; returns false when it already was. */
    fun add(c: Connection, userId: UUID, jobId: UUID, stage: String = "saved"): Boolean =
        c.prepareStatement("INSERT INTO tracker (user_id, job_id, stage) VALUES (?, ?, ?) ON CONFLICT DO NOTHING").use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.setString(3, stage)
            st.executeUpdate() > 0
        }

    /**
     * Marks a job applied (tracking it if needed): sets applied_at once and, unless a follow-up is already set,
     * schedules one [followUpAfter] later.
     */
    fun markApplied(c: Connection, userId: UUID, jobId: UUID, followUpAfter: Duration = Duration.ofDays(7)) {
        c.prepareStatement(
            """
            INSERT INTO tracker (user_id, job_id, stage, applied_at, follow_up_at) VALUES (?, ?, 'applied', now(), ?)
            ON CONFLICT (user_id, job_id) DO UPDATE SET
                stage = CASE WHEN tracker.stage IN ('saved', 'applied') THEN 'applied' ELSE tracker.stage END,
                applied_at = coalesce(tracker.applied_at, now()),
                follow_up_at = coalesce(tracker.follow_up_at, EXCLUDED.follow_up_at),
                updated_at = now()
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.setTimestamp(3, Timestamp.from(Instant.now().plus(followUpAfter)))
            st.executeUpdate()
        }
    }

    /** Partial update: null leaves a field as it is; [clearFollowUp] removes the follow-up date. */
    fun update(
        c: Connection,
        userId: UUID,
        jobId: UUID,
        stage: String?,
        followUpAt: Instant?,
        clearFollowUp: Boolean,
        notes: String?,
    ): Boolean =
        c.prepareStatement(
            """
            UPDATE tracker SET
                stage = coalesce(?, stage),
                applied_at = CASE WHEN ? IN ('applied', 'interview', 'offer') THEN coalesce(applied_at, now()) ELSE applied_at END,
                follow_up_at = CASE WHEN ? THEN NULL ELSE coalesce(?, follow_up_at) END,
                reminded_at = CASE WHEN ? OR ?::timestamptz IS NOT NULL THEN NULL ELSE reminded_at END,
                notes = coalesce(?, notes),
                updated_at = now()
            WHERE user_id = ? AND job_id = ?
            """.trimIndent(),
        ).use { st ->
            st.setString(1, stage)
            st.setString(2, stage)
            st.setBoolean(3, clearFollowUp)
            if (followUpAt == null) st.setNull(4, Types.TIMESTAMP_WITH_TIMEZONE) else st.setTimestamp(4, Timestamp.from(followUpAt))
            st.setBoolean(5, clearFollowUp)
            if (followUpAt == null) st.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE) else st.setTimestamp(6, Timestamp.from(followUpAt))
            st.setString(7, notes)
            st.setObject(8, userId)
            st.setObject(9, jobId)
            st.executeUpdate() > 0
        }

    fun remove(c: Connection, userId: UUID, jobId: UUID): Boolean =
        c.prepareStatement("DELETE FROM tracker WHERE user_id = ? AND job_id = ?").use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.executeUpdate() > 0
        }

    /** Users with follow-ups now due that haven't been reminded about yet, with "title at company" lines. */
    fun dueFollowUps(c: Connection): List<DueFollowUp> =
        c.prepareStatement(
            """
            SELECT t.user_id, u.email, j.title, coalesce(j.employer, co.name)
            FROM tracker t JOIN users u ON u.id = t.user_id JOIN jobs j ON j.id = t.job_id JOIN companies co ON co.id = j.company_id
            WHERE t.follow_up_at <= now() AND t.stage IN ('saved', 'applied', 'interview')
              AND (t.reminded_at IS NULL OR t.reminded_at < t.follow_up_at)
            ORDER BY t.user_id, t.follow_up_at
            """.trimIndent(),
        ).use { st ->
            st.executeQuery().use { rs ->
                val rows = buildList { while (rs.next()) add(Triple(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3) to rs.getString(4))) }
                rows.groupBy { it.first to it.second }.map { (key, items) -> DueFollowUp(key.first, key.second, items.map { it.third }) }
            }
        }

    fun markReminded(c: Connection, userId: UUID) {
        c.prepareStatement(
            "UPDATE tracker SET reminded_at = now() WHERE user_id = ? AND follow_up_at <= now() AND stage IN ('saved', 'applied', 'interview')",
        ).use { st ->
            st.setObject(1, userId)
            st.executeUpdate()
        }
    }

    private fun ResultSet.toItem() = TrackerItem(
        jobId = getObject(1, UUID::class.java),
        title = getString(2),
        company = getString(3),
        location = getString(4),
        applyUrl = getString(5),
        source = getString(6),
        closed = getBoolean(7),
        stage = getString(8),
        appliedAt = getTimestamp(9)?.toInstant(),
        followUpAt = getTimestamp(10)?.toInstant(),
        notes = getString(11),
        updatedAt = getTimestamp(12).toInstant(),
        createdAt = getTimestamp(13).toInstant(),
        applicationStatus = getString(14),
    )
}

/** Jobs the user saved from a page or typed in, kept under the inactive "Saved jobs" company. */
object ManualJobs {
    fun upsert(c: Connection, url: String, title: String, company: String, location: String, description: String): UUID {
        // V12 creates this row; recreated here if it was ever removed.
        c.prepareStatement(
            "INSERT INTO companies (id, name, ats, board_token, active) VALUES (?, 'Saved jobs', 'manual', 'saved', false) ON CONFLICT (ats, board_token) DO NOTHING",
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.executeUpdate()
        }
        val companyId = c.prepareStatement("SELECT id FROM companies WHERE ats = 'manual' AND board_token = 'saved'").use { st ->
            st.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java) }
        }
        return c.prepareStatement(
            """
            INSERT INTO jobs (id, company_id, source, source_job_id, title, location, remote, description, apply_url, employer, description_partial)
            VALUES (?, ?, 'manual', ?, ?, ?, false, ?, ?, ?, ?)
            ON CONFLICT (source, source_job_id) DO UPDATE SET
                title = EXCLUDED.title, location = EXCLUDED.location, employer = EXCLUDED.employer,
                description = CASE WHEN length(EXCLUDED.description) > length(jobs.description) THEN EXCLUDED.description ELSE jobs.description END,
                description_partial = EXCLUDED.description_partial AND jobs.description_partial,
                last_seen_at = now(), closed_at = NULL
            RETURNING id
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.setObject(2, companyId)
            st.setString(3, Tokens.sha256Hex(url.trim()))
            st.setString(4, title.take(300))
            st.setString(5, location.take(300))
            st.setString(6, description.take(30_000))
            st.setString(7, url.take(1000))
            st.setString(8, company.take(200))
            st.setBoolean(9, description.length < 500)
            st.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java) }
        }
    }

    /** A job any source already has for this page, so saving it from the page reuses it. */
    fun findByUrl(c: Connection, candidates: (String) -> Boolean, likeHost: String): UUID? =
        c.prepareStatement("SELECT id, apply_url FROM jobs WHERE apply_url ILIKE ? AND closed_at IS NULL LIMIT 500").use { st ->
            st.setString(1, "%$likeHost%")
            st.executeQuery().use { rs ->
                while (rs.next()) if (candidates(rs.getString(2))) return@use rs.getObject(1, UUID::class.java)
                null
            }
        }
}

/** Pairing codes and long-lived tokens for the browser extension. Only hashes are stored. */
object ExtensionAuth {
    private val CODE_TTL: Duration = Duration.ofMinutes(10)
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** A new 8-character code for this user, replacing any earlier unused one. */
    fun newPairCode(c: Connection, userId: UUID): Pair<String, Instant> {
        val random = java.security.SecureRandom()
        val code = (1..8).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        val expires = Instant.now().plus(CODE_TTL)
        c.prepareStatement("DELETE FROM extension_pair_codes WHERE user_id = ? OR expires_at < now()").use { st ->
            st.setObject(1, userId)
            st.executeUpdate()
        }
        c.prepareStatement("INSERT INTO extension_pair_codes (code_hash, user_id, expires_at) VALUES (?, ?, ?)").use { st ->
            st.setString(1, Tokens.sha256Hex(code))
            st.setObject(2, userId)
            st.setTimestamp(3, Timestamp.from(expires))
            st.executeUpdate()
        }
        return code to expires
    }

    /** Exchanges a code (once) for a new token. Null when the code is unknown or expired. */
    fun pair(c: Connection, code: String): Pair<UUID, String>? {
        val normalized = code.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        val userId = c.prepareStatement("DELETE FROM extension_pair_codes WHERE code_hash = ? AND expires_at > now() RETURNING user_id").use { st ->
            st.setString(1, Tokens.sha256Hex(normalized))
            st.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java) else null }
        } ?: return null
        val token = Tokens.newToken()
        c.prepareStatement("INSERT INTO extension_tokens (token_hash, user_id) VALUES (?, ?)").use { st ->
            st.setString(1, Tokens.sha256Hex(token))
            st.setObject(2, userId)
            st.executeUpdate()
        }
        return userId to token
    }

    fun userFor(c: Connection, token: String): UUID? =
        c.prepareStatement("UPDATE extension_tokens SET last_used_at = now() WHERE token_hash = ? RETURNING user_id").use { st ->
            st.setString(1, Tokens.sha256Hex(token))
            st.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java) else null }
        }

    fun connectedCount(c: Connection, userId: UUID): Int =
        c.prepareStatement("SELECT count(*) FROM extension_tokens WHERE user_id = ?").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    fun revokeAll(c: Connection, userId: UUID): Int =
        c.prepareStatement("DELETE FROM extension_tokens WHERE user_id = ?").use { st ->
            st.setObject(1, userId)
            st.executeUpdate()
        }
}
