package com.applycopilot.db

import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CountedValue(val value: String, val count: Int)
/** New users from one source in the window, and how many of them went on to make a profile and tailor an application. */
data class SourceFunnel(val source: String, val signups: Int, val profiles: Int, val tailored: Int)
data class DayActivity(val day: LocalDate, val activeUsers: Int, val newUsers: Int, val events: Int)

data class Overview(
    val totalUsers: Int,
    val newUsers: Int,
    val activeUsers7: Int,
    val activeUsers30: Int,
    val days: List<DayActivity>,
    val eventCounts: Map<String, Int>,
    /** Users who ever reached each step: saved a profile, refreshed the feed, tailored, approved, clicked apply. */
    val funnel: Map<String, Int>,
    val topRoles: List<CountedValue>,
    val topLocations: List<CountedValue>,
    val topAppliedCompanies: List<CountedValue>,
    /** Where new users came from ("unknown" when the browser didn't say), most signups first. */
    val sources: List<SourceFunnel> = emptyList(),
    /** Where people joining the waitlist came from. */
    val waitlistSources: List<CountedValue> = emptyList(),
)

data class UserSummary(
    val id: UUID,
    val email: String,
    val plan: String,
    val createdAt: Instant,
    val lastSeenAt: Instant?,
    val name: String?,
    val headline: String?,
    val roles: List<String>,
    val locations: List<String>,
    val refreshes: Int,
    val tailored: Int,
    val approved: Int,
    val applyClicks: Int,
    val source: String? = null,
    val campaign: String? = null,
    val referrer: String? = null,
)

data class UserApplicationRow(val jobId: UUID, val title: String, val company: String, val status: String, val updatedAt: Instant, val applyUrl: String)

/** Read-only queries behind the admin dashboard. Admin views themselves are left out of every count. */
object AdminQueries {
    private const val ACTIVITY = "e.type <> '${EventTypes.ADMIN_VIEWED_USER}'"

    fun overview(c: Connection, windowDays: Int): Overview {
        fun int(sql: String, vararg args: Any): Int = c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        fun counted(sql: String, vararg args: Any): List<CountedValue> = c.prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(CountedValue(rs.getString(1), rs.getInt(2))) } }
        }

        val days = c.prepareStatement(
            """
            SELECT d::date,
                   (SELECT count(DISTINCT e.user_id) FROM events e WHERE $ACTIVITY AND e.created_at >= d AND e.created_at < d + interval '1 day'),
                   (SELECT count(*) FROM users u WHERE u.created_at >= d AND u.created_at < d + interval '1 day'),
                   (SELECT count(*) FROM events e WHERE $ACTIVITY AND e.created_at >= d AND e.created_at < d + interval '1 day')
            FROM generate_series(date_trunc('day', now()) - make_interval(days => ? - 1), date_trunc('day', now()), interval '1 day') AS d
            ORDER BY d
            """.trimIndent(),
        ).use { st ->
            st.setInt(1, windowDays)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(DayActivity(rs.getDate(1).toLocalDate(), rs.getInt(2), rs.getInt(3), rs.getInt(4))) } }
        }

        val eventCounts = c.prepareStatement(
            "SELECT e.type, count(*) FROM events e WHERE $ACTIVITY AND e.created_at > now() - make_interval(days => ?) GROUP BY e.type",
        ).use { st ->
            st.setInt(1, windowDays)
            st.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getString(1), rs.getInt(2)) } }
        }

        fun everDid(type: String) = int("SELECT count(DISTINCT user_id) FROM events WHERE type = ?", type)
        val funnel = linkedMapOf(
            "signedUp" to int("SELECT count(*) FROM users"),
            "profile" to int("SELECT count(DISTINCT user_id) FROM profiles"),
            "refreshed" to everDid(EventTypes.FEED_REFRESHED),
            "tailored" to int("SELECT count(DISTINCT user_id) FROM applications"),
            "approved" to int("SELECT count(DISTINCT user_id) FROM applications WHERE approved_at IS NOT NULL"),
            "applyClicked" to everDid(EventTypes.APPLY_CLICKED),
        )

        // A waitlist entry's source wins: it's from their first visit, before the invite email brought them back.
        val sources = c.prepareStatement(
            """
            SELECT x.src, count(*),
                   count(*) FILTER (WHERE EXISTS (SELECT 1 FROM profiles p WHERE p.user_id = x.id)),
                   count(*) FILTER (WHERE EXISTS (SELECT 1 FROM applications a WHERE a.user_id = x.id))
            FROM (
                SELECT u.id, coalesce(w.acq_source, u.acq_source, 'unknown') AS src
                FROM users u LEFT JOIN waitlist w ON w.email = u.email
                WHERE u.created_at > now() - make_interval(days => ?)
            ) x
            GROUP BY x.src ORDER BY count(*) DESC, x.src LIMIT 15
            """.trimIndent(),
        ).use { st ->
            st.setInt(1, windowDays)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(SourceFunnel(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4))) } }
        }

        return Overview(
            sources = sources,
            waitlistSources = counted(
                """
                SELECT coalesce(acq_source, 'unknown') AS src, count(*) FROM waitlist
                WHERE created_at > now() - make_interval(days => ?) GROUP BY src ORDER BY count(*) DESC, src LIMIT 15
                """.trimIndent(),
                windowDays,
            ),
            totalUsers = int("SELECT count(*) FROM users"),
            newUsers = int("SELECT count(*) FROM users WHERE created_at > now() - make_interval(days => ?)", windowDays),
            activeUsers7 = int("SELECT count(DISTINCT e.user_id) FROM events e WHERE $ACTIVITY AND e.created_at > now() - interval '7 days'"),
            activeUsers30 = int("SELECT count(DISTINCT e.user_id) FROM events e WHERE $ACTIVITY AND e.created_at > now() - interval '30 days'"),
            days = days,
            eventCounts = eventCounts,
            funnel = funnel,
            topRoles = counted(
                "SELECT min(r), count(*) FROM preferences, unnest(roles) AS r GROUP BY lower(r) ORDER BY count(*) DESC, min(r) LIMIT 10",
            ),
            topLocations = counted(
                "SELECT min(l), count(*) FROM preferences, unnest(locations) AS l GROUP BY lower(l) ORDER BY count(*) DESC, min(l) LIMIT 10",
            ),
            topAppliedCompanies = counted(
                """
                SELECT coalesce(j.employer, co.name) AS company, count(*) FROM events e
                JOIN jobs j ON j.id = e.job_id JOIN companies co ON co.id = j.company_id
                WHERE e.type = ? AND e.created_at > now() - make_interval(days => ?)
                GROUP BY company ORDER BY count(*) DESC, company LIMIT 10
                """.trimIndent(),
                EventTypes.APPLY_CLICKED, windowDays,
            ),
        )
    }

    private const val USER_COLUMNS = """
        u.id, u.email, u.plan, u.created_at, u.last_seen_at,
        p.profile_json->>'fullName', p.profile_json->>'headline', pr.roles, pr.locations,
        (SELECT count(*) FROM events e WHERE e.user_id = u.id AND e.type = '${EventTypes.FEED_REFRESHED}'),
        (SELECT count(*) FROM applications a WHERE a.user_id = u.id),
        (SELECT count(*) FROM applications a WHERE a.user_id = u.id AND a.approved_at IS NOT NULL),
        (SELECT count(*) FROM events e WHERE e.user_id = u.id AND e.type = '${EventTypes.APPLY_CLICKED}'),
        coalesce(w.acq_source, u.acq_source), coalesce(w.acq_campaign, u.acq_campaign), coalesce(w.acq_referrer, u.acq_referrer)
    """
    private const val USER_FROM = """
        FROM users u
        LEFT JOIN LATERAL (SELECT profile_json FROM profiles WHERE user_id = u.id ORDER BY version DESC LIMIT 1) p ON true
        LEFT JOIN preferences pr ON pr.user_id = u.id
        LEFT JOIN waitlist w ON w.email = u.email
    """

    /** Users, most recently active first. [query] matches email or name. */
    fun users(c: Connection, query: String, limit: Int, offset: Int): Pair<List<UserSummary>, Int> {
        val q = "%" + query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val where = "WHERE (u.email ILIKE ? OR coalesce(p.profile_json->>'fullName', '') ILIKE ?)"
        val total = c.prepareStatement("SELECT count(*) $USER_FROM $where").use { st ->
            st.setString(1, q)
            st.setString(2, q)
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
        val rows = c.prepareStatement(
            "SELECT $USER_COLUMNS $USER_FROM $where ORDER BY coalesce(u.last_seen_at, u.created_at) DESC LIMIT ? OFFSET ?",
        ).use { st ->
            st.setString(1, q)
            st.setString(2, q)
            st.setInt(3, limit)
            st.setInt(4, offset)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toUserSummary()) } }
        }
        return rows to total
    }

    fun user(c: Connection, id: UUID): UserSummary? =
        c.prepareStatement("SELECT $USER_COLUMNS $USER_FROM WHERE u.id = ?").use { st ->
            st.setObject(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.toUserSummary() else null }
        }

    fun applications(c: Connection, userId: UUID): List<UserApplicationRow> =
        c.prepareStatement(
            """
            SELECT a.job_id, j.title, coalesce(j.employer, co.name), a.status, a.updated_at, j.apply_url
            FROM applications a JOIN jobs j ON j.id = a.job_id JOIN companies co ON co.id = j.company_id
            WHERE a.user_id = ? ORDER BY a.updated_at DESC LIMIT 100
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(
                        UserApplicationRow(rs.getObject(1, UUID::class.java), rs.getString(2), rs.getString(3), rs.getString(4), rs.getTimestamp(5).toInstant(), rs.getString(6)),
                    )
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun ResultSet.textArrayAt(index: Int): List<String> =
        (getArray(index)?.array as? Array<Any?>)?.mapNotNull { it as String? } ?: emptyList()

    private fun ResultSet.toUserSummary() = UserSummary(
        id = getObject(1, UUID::class.java),
        email = getString(2),
        plan = getString(3),
        createdAt = getTimestamp(4).toInstant(),
        lastSeenAt = getTimestamp(5)?.toInstant(),
        name = getString(6)?.takeIf { it.isNotBlank() },
        headline = getString(7)?.takeIf { it.isNotBlank() },
        roles = textArrayAt(8),
        locations = textArrayAt(9),
        refreshes = getInt(10),
        tailored = getInt(11),
        approved = getInt(12),
        applyClicks = getInt(13),
        source = getString(14),
        campaign = getString(15),
        referrer = getString(16),
    )
}
