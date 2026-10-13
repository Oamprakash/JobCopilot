package com.applycopilot.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class DigestJob(val jobId: UUID, val title: String, val company: String, val location: String, val score: Int)

data class DigestRecipient(val userId: UUID, val email: String, val lastSentAt: Instant?)

object Digests {
    /** Users with a profile and the daily email on who haven't had one since [notSince]. */
    fun recipients(c: Connection, notSince: Instant): List<DigestRecipient> =
        c.prepareStatement(
            """
            SELECT u.id, u.email, u.digest_sent_at FROM users u
            JOIN preferences p ON p.user_id = u.id
            WHERE p.daily_digest AND EXISTS (SELECT 1 FROM profiles pr WHERE pr.user_id = u.id)
              AND (u.digest_sent_at IS NULL OR u.digest_sent_at < ?)
            """.trimIndent(),
        ).use { st ->
            st.setTimestamp(1, Timestamp.from(notSince))
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(DigestRecipient(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getTimestamp(3)?.toInstant())) }
            }
        }

    /** The best new, open matches scored after [since]. */
    fun newMatches(c: Connection, userId: UUID, since: Instant, minScore: Int, limit: Int): Pair<List<DigestJob>, Int> {
        val where = """
            FROM matches m JOIN jobs j ON j.id = m.job_id JOIN companies co ON co.id = j.company_id
            WHERE m.user_id = ? AND m.status = 'new' AND m.score >= ? AND m.scored_at > ? AND j.closed_at IS NULL
        """
        val total = c.prepareStatement("SELECT count(*) $where").use { st ->
            st.setObject(1, userId)
            st.setInt(2, minScore)
            st.setTimestamp(3, Timestamp.from(since))
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val jobs = c.prepareStatement(
            "SELECT j.id, j.title, coalesce(j.employer, co.name), j.location, m.score $where ORDER BY m.score DESC, j.posted_at DESC NULLS LAST LIMIT ?",
        ).use { st ->
            st.setObject(1, userId)
            st.setInt(2, minScore)
            st.setTimestamp(3, Timestamp.from(since))
            st.setInt(4, limit)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(DigestJob(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5))) }
            }
        }
        return jobs to total
    }

    fun markSent(c: Connection, userId: UUID, at: Instant) {
        c.prepareStatement("UPDATE users SET digest_sent_at = ? WHERE id = ?").use { st ->
            st.setTimestamp(1, Timestamp.from(at))
            st.setObject(2, userId)
            st.executeUpdate()
        }
    }

    fun setEnabled(c: Connection, userId: UUID, enabled: Boolean): Boolean =
        c.prepareStatement("UPDATE preferences SET daily_digest = ?, updated_at = now() WHERE user_id = ?").use { st ->
            st.setBoolean(1, enabled)
            st.setObject(2, userId)
            st.executeUpdate() > 0
        }
}

object InterviewPreps {
    fun find(c: Connection, userId: UUID, jobId: UUID): Pair<String, String>? =
        c.prepareStatement("SELECT generator, content_json FROM interview_prep WHERE user_id = ? AND job_id = ?").use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null }
        }

    fun save(c: Connection, userId: UUID, jobId: UUID, generator: String, contentJson: String) {
        c.prepareStatement(
            """
            INSERT INTO interview_prep (user_id, job_id, generator, content_json) VALUES (?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT (user_id, job_id) DO UPDATE SET generator = EXCLUDED.generator, content_json = EXCLUDED.content_json, created_at = now()
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.setString(3, generator)
            st.setString(4, contentJson)
            st.executeUpdate()
        }
    }

    fun countSince(c: Connection, userId: UUID, since: Instant): Int =
        c.prepareStatement("SELECT count(*) FROM events WHERE user_id = ? AND type = ? AND created_at > ?").use { st ->
            st.setObject(1, userId)
            st.setString(2, EventTypes.INTERVIEW_PREP)
            st.setTimestamp(3, Timestamp.from(since))
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
}
