package com.applycopilot.db

import java.sql.Connection

/** Live numbers for the home page: open jobs from switched-on sources, the employers behind them, and new in a day. */
data class SiteStatsRow(val openJobs: Int, val companies: Int, val newToday: Int)

object SiteStats {
    fun read(c: Connection): SiteStatsRow =
        c.prepareStatement(
            """
            SELECT count(*),
                   count(DISTINCT lower(coalesce(j.employer, co.name))),
                   count(*) FILTER (WHERE j.first_seen_at > now() - interval '24 hours')
            FROM jobs j JOIN companies co ON co.id = j.company_id
            WHERE j.closed_at IS NULL AND co.active AND co.ats <> 'manual'
            """.trimIndent(),
        ).use { st -> st.executeQuery().use { rs -> rs.next(); SiteStatsRow(rs.getInt(1), rs.getInt(2), rs.getInt(3)) } }
}
