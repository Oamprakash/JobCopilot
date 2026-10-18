package com.applycopilot.jobs

import com.applycopilot.db.Db
import com.applycopilot.matching.Html
import com.applycopilot.matching.JobPages
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Serializable
data class PublicPageDto(val slug: String, val title: String, val role: String, val roleSlug: String, val place: String, val placeSlug: String, val count: Int)

@Serializable
data class PublicJobDto(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val remote: Boolean,
    val postedAt: String?,
    val applyUrl: String,
    /** The start of the description, as plain text. */
    val snippet: String,
)

@Serializable
data class PublicIndexDto(val updatedAt: String, val totalJobs: Int, val pages: List<PublicPageDto>)

@Serializable
data class PublicPageDetailDto(
    val page: PublicPageDto,
    val updatedAt: String,
    val jobs: List<PublicJobDto>,
    /** The same role in other places, and other roles in the same place. */
    val related: List<PublicPageDto>,
)

/**
 * The public job pages (for search engines and people who haven't signed up). Only jobs from companies' own public
 * job boards (Greenhouse, Lever, Ashby, Workable, SmartRecruiters), which publish them for exactly this kind of reuse,
 * and only from switched-on boards. Aggregator jobs (Adzuna, Himalayas) stay inside the app, where their terms are
 * clear. Rebuilt at most every [ttl].
 */
class PublicJobs(private val db: Db, private val ttl: Duration = Duration.ofHours(1)) {
    private class Row(val listing: JobPages.Listing, val dto: PublicJobDto, val sortKey: Instant)
    private class Snapshot(val at: Instant, val rows: Map<String, Row>, val pages: Map<String, List<Row>>, val total: Int)

    @Volatile private var snapshot: Snapshot? = null

    private suspend fun current(): Snapshot {
        snapshot?.takeIf { it.at.isAfter(Instant.now().minus(ttl)) }?.let { return it }
        val rows = db.tx { c ->
            c.prepareStatement(
                """
                SELECT j.id, j.title, coalesce(j.employer, co.name), j.location, j.remote, j.posted_at, j.first_seen_at, j.apply_url, left(j.description, 600)
                FROM jobs j JOIN companies co ON co.id = j.company_id
                WHERE j.closed_at IS NULL AND co.active AND co.ats IN ('greenhouse', 'lever', 'ashby', 'workable', 'smartrecruiters')
                """.trimIndent(),
            ).use { st ->
                st.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            val id = rs.getObject(1, UUID::class.java).toString()
                            val posted = rs.getTimestamp(6)?.toInstant()
                            val seen = rs.getTimestamp(7).toInstant()
                            val dto = PublicJobDto(
                                id = id, title = rs.getString(2), company = rs.getString(3), location = rs.getString(4),
                                remote = rs.getBoolean(5), postedAt = posted?.toString(), applyUrl = rs.getString(8),
                                snippet = snippet(rs.getString(9).orEmpty()),
                            )
                            add(Row(JobPages.Listing(id, dto.title, dto.location, dto.remote), dto, posted ?: seen))
                        }
                    }
                }
            }
        }
        val byId = rows.associateBy { it.listing.id }
        val grouped = JobPages.group(rows.map { it.listing })
            .filterValues { it.size >= JobPages.MIN_JOBS }
            .mapValues { (_, listings) -> listings.mapNotNull { byId[it.id] }.sortedByDescending { it.sortKey } }
        return Snapshot(Instant.now(), byId, grouped, rows.size).also { snapshot = it }
    }

    private fun pageDto(slug: String, count: Int): PublicPageDto? {
        val p = JobPages.page(slug) ?: return null
        return PublicPageDto(slug, p.title, p.role.name, p.role.slug, p.place.name, p.place.slug, count)
    }

    suspend fun index(): PublicIndexDto {
        val s = current()
        val pages = s.pages.mapNotNull { (slug, rows) -> pageDto(slug, rows.size) }.sortedByDescending { it.count }
        return PublicIndexDto(s.at.toString(), s.total, pages)
    }

    /** One page with up to [limit] of its newest jobs; null when it doesn't exist or has too few jobs. */
    suspend fun page(slug: String, limit: Int = 50): PublicPageDetailDto? {
        val s = current()
        val rows = s.pages[slug] ?: return null
        val page = pageDto(slug, rows.size) ?: return null
        val related = s.pages
            .filterKeys { it != slug }
            .mapNotNull { (k, v) -> pageDto(k, v.size) }
            .filter { (it.roleSlug == page.roleSlug) != (it.placeSlug == page.placeSlug) }
            .sortedByDescending { it.count }
            .take(12)
        return PublicPageDetailDto(page, s.at.toString(), rows.take(limit).map { it.dto }, related)
    }

    /** A public job's title and full description, for the home page's fit check. */
    suspend fun forFitCheck(id: String): Pair<String, String>? {
        val known = current().rows[id] ?: return null
        val uuid = runCatching { UUID.fromString(known.listing.id) }.getOrNull() ?: return null
        val description = db.tx { c ->
            c.prepareStatement("SELECT description FROM jobs WHERE id = ? AND closed_at IS NULL").use { st ->
                st.setObject(1, uuid)
                st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        } ?: return null
        return known.dto.title to "${known.dto.title} at ${known.dto.company}\n$description"
    }

    companion object {
        internal fun snippet(raw: String): String {
            val text = Html.toText(raw).replace(Regex("\\s+"), " ").trim()
            if (text.length <= 200) return text
            val cut = text.take(200)
            return cut.substring(0, cut.lastIndexOf(' ').takeIf { it > 120 } ?: cut.length).trimEnd(',', '.', ';', ':') + "…"
        }
    }
}
