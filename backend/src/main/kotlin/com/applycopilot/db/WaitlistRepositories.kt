package com.applycopilot.db

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class WaitlistEntry(val id: UUID, val email: String, val note: String?, val status: String, val createdAt: Instant, val invitedAt: Instant?)

object Waitlist {
    private const val COLUMNS = "id, email, note, status, created_at, invited_at"

    /** Adds [email] (already normalized) or, if it's there, keeps it as is. Returns the entry. */
    fun join(c: Connection, email: String, note: String?): WaitlistEntry =
        c.prepareStatement(
            """
            INSERT INTO waitlist (id, email, note) VALUES (?, ?, ?)
            ON CONFLICT (email) DO UPDATE SET note = coalesce(waitlist.note, excluded.note)
            RETURNING $COLUMNS
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.setString(2, email)
            st.setString(3, note)
            st.executeQuery().use { rs -> rs.next(); rs.toEntry() }
        }

    fun isInvited(c: Connection, email: String): Boolean =
        c.prepareStatement("SELECT 1 FROM waitlist WHERE email = ? AND status = 'invited'").use { st ->
            st.setString(1, email)
            st.executeQuery().use { it.next() }
        }

    /** Waiting first (oldest first: first come, first served), then invited (latest first). */
    fun list(c: Connection, limit: Int = 500): List<WaitlistEntry> =
        c.prepareStatement(
            "SELECT $COLUMNS FROM waitlist ORDER BY (status = 'invited'), CASE WHEN status = 'waiting' THEN created_at END, invited_at DESC LIMIT ?",
        ).use { st ->
            st.setInt(1, limit)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toEntry()) } }
        }

    fun invite(c: Connection, id: UUID): WaitlistEntry? =
        c.prepareStatement("UPDATE waitlist SET status = 'invited', invited_at = coalesce(invited_at, now()) WHERE id = ? RETURNING $COLUMNS").use { st ->
            st.setObject(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.toEntry() else null }
        }

    fun delete(c: Connection, id: UUID): Boolean =
        c.prepareStatement("DELETE FROM waitlist WHERE id = ?").use { st ->
            st.setObject(1, id)
            st.executeUpdate() > 0
        }

    private fun ResultSet.toEntry() = WaitlistEntry(
        getObject("id", UUID::class.java), getString("email"), getString("note"), getString("status"),
        getTimestamp("created_at").toInstant(), getTimestamp("invited_at")?.toInstant(),
    )
}
