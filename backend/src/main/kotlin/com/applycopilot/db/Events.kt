package com.applycopilot.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.sql.Connection
import java.sql.Types
import java.time.Instant
import java.util.UUID

/** Activity the admin dashboard shows. Recorded server-side, except the two link clicks the browser reports. */
object EventTypes {
    const val SIGNED_IN = "signed_in"
    const val RESUME_UPLOADED = "resume_uploaded"
    const val PROFILE_SAVED = "profile_saved"
    const val PREFERENCES_SAVED = "preferences_saved"
    const val FEED_REFRESHED = "feed_refreshed"
    const val JOB_SAVED = "job_saved"
    const val JOB_SKIPPED = "job_skipped"
    const val JOB_RESTORED = "job_restored"
    const val JOB_OPENED = "job_opened"
    const val APPLICATION_TAILORED = "application_tailored"
    const val APPLICATION_APPROVED = "application_approved"
    const val RESUME_DOWNLOADED = "resume_downloaded"
    const val APPLY_CLICKED = "apply_clicked"
    const val EXTENSION_CONNECTED = "extension_connected"
    const val EXTENSION_AUTOFILL = "extension_autofill"
    const val JOB_SAVED_FROM_PAGE = "job_saved_from_page"
    const val MARKED_APPLIED = "marked_applied"
    const val TRACKER_STAGE = "tracker_stage"
    const val DIGEST_SENT = "digest_sent"
    const val DIGEST_UNSUBSCRIBED = "digest_unsubscribed"
    const val INTERVIEW_PREP = "interview_prep"
    const val FEEDBACK_SENT = "feedback_sent"
    const val TAILOR_LIMIT_REACHED = "tailor_limit_reached"
    const val PRO_INTEREST = "pro_interest"
    /** Audit trail: an admin opened a user's activity. Recorded against the admin. */
    const val ADMIN_VIEWED_USER = "admin_viewed_user"

    /** The only types the browser may report through POST /events. */
    val fromClient = setOf(JOB_OPENED, APPLY_CLICKED)
}

data class EventRow(
    val type: String,
    val at: Instant,
    val jobId: UUID?,
    val jobTitle: String?,
    val company: String?,
    val data: JsonObject,
)

object Events {
    private val json = Json { ignoreUnknownKeys = true }

    /** Builds an event's data from simple values: strings, numbers, booleans and lists of strings. */
    fun data(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
        pairs.mapNotNull { (k, v) ->
            val value: JsonElement = when (v) {
                null -> return@mapNotNull null
                is String -> JsonPrimitive(v.take(500))
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is List<*> -> JsonArray(v.filterIsInstance<String>().take(20).map { JsonPrimitive(it.take(200)) })
                else -> JsonPrimitive(v.toString().take(500))
            }
            k to value
        }.toMap(),
    )

    fun record(c: Connection, userId: UUID, type: String, jobId: UUID? = null, data: JsonObject = JsonObject(emptyMap())) {
        c.prepareStatement("INSERT INTO events (user_id, type, job_id, data) VALUES (?, ?, ?, CAST(? AS jsonb))").use { st ->
            st.setObject(1, userId)
            st.setString(2, type)
            if (jobId == null) st.setNull(3, Types.OTHER) else st.setObject(3, jobId)
            st.setString(4, data.toString())
            st.executeUpdate()
        }
        c.prepareStatement("UPDATE users SET last_seen_at = now() WHERE id = ?").use { st ->
            st.setObject(1, userId)
            st.executeUpdate()
        }
    }

    /** A user's activity, newest first, with the job each event was about. */
    fun timeline(c: Connection, userId: UUID, limit: Int = 200): List<EventRow> =
        c.prepareStatement(
            """
            SELECT e.type, e.created_at, e.job_id, j.title, coalesce(j.employer, co.name), e.data
            FROM events e LEFT JOIN jobs j ON j.id = e.job_id LEFT JOIN companies co ON co.id = j.company_id
            WHERE e.user_id = ? AND e.type <> ?
            ORDER BY e.created_at DESC, e.id DESC
            LIMIT ?
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setString(2, EventTypes.ADMIN_VIEWED_USER)
            st.setInt(3, limit)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        EventRow(
                            type = rs.getString(1),
                            at = rs.getTimestamp(2).toInstant(),
                            jobId = rs.getObject(3, UUID::class.java),
                            jobTitle = rs.getString(4),
                            company = rs.getString(5),
                            data = runCatching { json.parseToJsonElement(rs.getString(6)) as JsonObject }.getOrDefault(JsonObject(emptyMap())),
                        ),
                    )
                }
            }
        }

    fun deleteOlderThan(c: Connection, days: Int): Int =
        c.prepareStatement("DELETE FROM events WHERE created_at < now() - make_interval(days => ?)").use { st ->
            st.setInt(1, days)
            st.executeUpdate()
        }
}
