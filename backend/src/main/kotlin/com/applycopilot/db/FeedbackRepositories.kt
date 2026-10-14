package com.applycopilot.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

data class FeedbackRow(
    val id: UUID,
    val userId: UUID,
    val email: String,
    val kind: String,
    val message: String,
    val page: String?,
    val status: String,
    val createdAt: Instant,
)

object Feedback {
    val KINDS = setOf("idea", "problem", "praise", "other")

    fun insert(c: Connection, userId: UUID, kind: String, message: String, page: String?): UUID {
        val id = UUID.randomUUID()
        c.prepareStatement("INSERT INTO feedback (id, user_id, kind, message, page) VALUES (?, ?, ?, ?, ?)").use { st ->
            st.setObject(1, id)
            st.setObject(2, userId)
            st.setString(3, kind)
            st.setString(4, message)
            st.setString(5, page)
            st.executeUpdate()
        }
        return id
    }

    fun countSince(c: Connection, userId: UUID, since: Instant): Int =
        c.prepareStatement("SELECT count(*) FROM feedback WHERE user_id = ? AND created_at > ?").use { st ->
            st.setObject(1, userId)
            st.setTimestamp(2, Timestamp.from(since))
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    /** Newest first; [status] null means all. */
    fun list(c: Connection, status: String?, limit: Int): List<FeedbackRow> =
        c.prepareStatement(
            """
            SELECT f.id, f.user_id, u.email, f.kind, f.message, f.page, f.status, f.created_at
            FROM feedback f JOIN users u ON u.id = f.user_id
            WHERE (CAST(? AS text) IS NULL OR f.status = ?)
            ORDER BY f.created_at DESC LIMIT ?
            """.trimIndent(),
        ).use { st ->
            st.setString(1, status)
            st.setString(2, status)
            st.setInt(3, limit)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        FeedbackRow(
                            rs.getObject(1, UUID::class.java), rs.getObject(2, UUID::class.java), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getString(7), rs.getTimestamp(8).toInstant(),
                        ),
                    )
                }
            }
        }

    fun countNew(c: Connection): Int =
        c.prepareStatement("SELECT count(*) FROM feedback WHERE status = 'new'").use { st ->
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    fun setStatus(c: Connection, id: UUID, status: String): Boolean =
        c.prepareStatement("UPDATE feedback SET status = ? WHERE id = ?").use { st ->
            st.setString(1, status)
            st.setObject(2, id)
            st.executeUpdate() > 0
        }
}
