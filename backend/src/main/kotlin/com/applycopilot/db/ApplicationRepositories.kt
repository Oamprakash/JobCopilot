package com.applycopilot.db

import com.applycopilot.tailoring.TailoredApplication
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class StoredApplication(
    val id: UUID,
    val jobId: UUID,
    val profileVersion: Int,
    val status: String,
    val generator: String,
    val content: TailoredApplication,
    val removed: List<String>,
    val questions: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val approvedAt: Instant?,
    /** The full description the user pasted, or null to use the job's own. */
    val jobDescription: String? = null,
)

object Applications {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private const val COLUMNS =
        "id, job_id, profile_version, status, generator, content_json, removed, questions, created_at, updated_at, approved_at, job_description"

    fun find(c: Connection, userId: UUID, jobId: UUID): StoredApplication? =
        c.prepareStatement("SELECT $COLUMNS FROM applications WHERE user_id = ? AND job_id = ?").use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.executeQuery().use { rs -> if (rs.next()) rs.toApplication() else null }
        }

    /** Creates the draft, or replaces a previous one for this job (regenerating sends it back to draft). */
    fun saveDraft(
        c: Connection,
        userId: UUID,
        jobId: UUID,
        profileVersion: Int,
        generator: String,
        content: TailoredApplication,
        removed: List<String>,
        questions: List<String>,
        jobDescription: String?,
    ): StoredApplication =
        c.prepareStatement(
            """
            INSERT INTO applications (id, user_id, job_id, profile_version, generator, content_json, removed, questions, job_description)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?)
            ON CONFLICT (user_id, job_id) DO UPDATE SET
                profile_version = EXCLUDED.profile_version, generator = EXCLUDED.generator,
                content_json = EXCLUDED.content_json, removed = EXCLUDED.removed, questions = EXCLUDED.questions,
                job_description = EXCLUDED.job_description,
                status = 'draft', approved_at = NULL, updated_at = now()
            RETURNING $COLUMNS
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.setObject(2, userId)
            st.setObject(3, jobId)
            st.setInt(4, profileVersion)
            st.setString(5, generator)
            st.setString(6, json.encodeToString(TailoredApplication.serializer(), content))
            st.setArray(7, c.createArrayOf("text", removed.toTypedArray()))
            st.setArray(8, c.createArrayOf("text", questions.toTypedArray()))
            if (jobDescription == null) st.setNull(9, java.sql.Types.VARCHAR) else st.setString(9, jobDescription)
            st.executeQuery().use { rs -> rs.next(); rs.toApplication() }
        }

    /** The user's own edits. Editing an approved application sends it back to draft for another look. */
    fun saveEdits(c: Connection, userId: UUID, jobId: UUID, content: TailoredApplication): StoredApplication? =
        c.prepareStatement(
            """
            UPDATE applications SET content_json = CAST(? AS jsonb), updated_at = now(),
                status = CASE WHEN status = 'approved' THEN 'draft' ELSE status END,
                approved_at = CASE WHEN status = 'approved' THEN NULL ELSE approved_at END
            WHERE user_id = ? AND job_id = ?
            RETURNING $COLUMNS
            """.trimIndent(),
        ).use { st ->
            st.setString(1, json.encodeToString(TailoredApplication.serializer(), content))
            st.setObject(2, userId)
            st.setObject(3, jobId)
            st.executeQuery().use { rs -> if (rs.next()) rs.toApplication() else null }
        }

    fun approve(c: Connection, userId: UUID, jobId: UUID): StoredApplication? =
        c.prepareStatement(
            """
            UPDATE applications SET status = 'approved', approved_at = now(), updated_at = now()
            WHERE user_id = ? AND job_id = ? AND status IN ('draft', 'approved')
            RETURNING $COLUMNS
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setObject(2, jobId)
            st.executeQuery().use { rs -> if (rs.next()) rs.toApplication() else null }
        }

    /** Applications started this calendar month (regenerating one doesn't count again). */
    fun createdThisMonth(c: Connection, userId: UUID): Int =
        c.prepareStatement("SELECT count(*) FROM applications WHERE user_id = ? AND created_at >= date_trunc('month', now())").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    @Suppress("UNCHECKED_CAST")
    private fun ResultSet.textArray(column: String): List<String> =
        (getArray(column)?.array as? Array<Any?>)?.mapNotNull { it as String? } ?: emptyList()

    private fun ResultSet.toApplication() = StoredApplication(
        id = getObject("id", UUID::class.java),
        jobId = getObject("job_id", UUID::class.java),
        profileVersion = getInt("profile_version"),
        status = getString("status"),
        generator = getString("generator"),
        content = json.decodeFromString(TailoredApplication.serializer(), getString("content_json")),
        removed = textArray("removed"),
        questions = textArray("questions"),
        createdAt = getTimestamp("created_at").toInstant(),
        updatedAt = getTimestamp("updated_at").toInstant(),
        approvedAt = getTimestamp("approved_at")?.toInstant(),
        jobDescription = getString("job_description"),
    )
}
