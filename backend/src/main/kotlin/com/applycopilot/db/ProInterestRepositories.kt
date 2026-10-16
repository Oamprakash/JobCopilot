package com.applycopilot.db

import java.sql.Connection
import java.time.Instant
import java.util.UUID

data class ProInterestRow(val userId: UUID, val email: String, val choice: String, val note: String?, val updatedAt: Instant)

/** Answers to "what would you pay for unlimited tailoring?" (no payment is taken). */
object ProInterest {
    /** month_199: ₹199 a month. pass_499: ₹499 for 3 months. year_999: ₹999 a year. no: wouldn't pay. */
    val CHOICES = setOf("month_199", "pass_499", "year_999", "no")

    fun upsert(c: Connection, userId: UUID, choice: String, note: String?) {
        c.prepareStatement(
            """
            INSERT INTO pro_interest (user_id, choice, note) VALUES (?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET choice = EXCLUDED.choice, note = coalesce(EXCLUDED.note, pro_interest.note), updated_at = now()
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setString(2, choice)
            st.setString(3, note)
            st.executeUpdate()
        }
    }

    fun find(c: Connection, userId: UUID): Pair<String, String?>? =
        c.prepareStatement("SELECT choice, note FROM pro_interest WHERE user_id = ?").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) to rs.getString(2) else null }
        }

    /** Count per choice, in [CHOICES] order. */
    fun counts(c: Connection): Map<String, Int> {
        val found = c.prepareStatement("SELECT choice, count(*) FROM pro_interest GROUP BY choice").use { st ->
            st.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) } }
        }
        return CHOICES.associateWith { found[it] ?: 0 }
    }

    /** Latest answers that came with a note. */
    fun recentNotes(c: Connection, limit: Int = 20): List<ProInterestRow> =
        c.prepareStatement(
            """
            SELECT p.user_id, u.email, p.choice, p.note, p.updated_at FROM pro_interest p JOIN users u ON u.id = p.user_id
            WHERE p.note IS NOT NULL ORDER BY p.updated_at DESC LIMIT ?
            """.trimIndent(),
        ).use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(ProInterestRow(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant()))
                }
            }
        }

    /** Users who reached the free monthly limit at least once in the last [days] days. */
    fun limitReachedUsers(c: Connection, days: Int): Int =
        c.prepareStatement(
            "SELECT count(DISTINCT user_id) FROM events WHERE type = '${EventTypes.TAILOR_LIMIT_REACHED}' AND created_at > now() - make_interval(days => ?)",
        ).use { st ->
            st.setInt(1, days)
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
}
