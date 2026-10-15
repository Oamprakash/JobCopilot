package com.applycopilot.jobs

import com.applycopilot.matching.Html
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Himalayas' free public remote-jobs API (himalayas.app/docs/remote-jobs-api): no key, data refreshed once a day.
 * Its terms ask for a visible link back to himalayas.app wherever the jobs are shown, which the feed's
 * attribution and each job's link (the listing on Himalayas) provide. Every job is remote; we search only
 * jobs open to candidates in [country] (worldwide ones included), so a US-only remote job never shows up for
 * someone in India.
 */
class HimalayasClient(
    private val http: HttpClient,
    private val base: String = "https://himalayas.app",
    val country: String = "IN",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun search(query: SearchQuery, page: Int = 1): SearchPage {
        val response = http.get("$base/jobs/api/search") {
            parameter("q", query.what)
            parameter("country", country)
            parameter("sort", "recent")
            parameter("page", page)
        }
        if (!response.status.isSuccess()) {
            throw AtsException(
                when (response.status.value) {
                    429 -> "Himalayas rate limit reached (429)"
                    400 -> "Himalayas rejected the search (400)"
                    else -> "Himalayas returned ${response.status.value}"
                },
            )
        }
        val body = try {
            json.parseToJsonElement(response.bodyAsText())
        } catch (e: Exception) {
            throw AtsException("Himalayas returned invalid JSON")
        }
        return parse(body, country)
    }

    companion object {
        /** Jobs per search page as Himalayas serves them; used only to tell when there are no more pages. */
        const val PAGE_SIZE = 20

        internal fun parse(body: JsonElement, country: String = "IN", now: Instant = Instant.now()): SearchPage {
            val o = body as? JsonObject ?: throw AtsException("Himalayas returned an unexpected shape")
            val jobs = (o["jobs"] as? JsonArray).orEmpty()
            val postings = jobs.mapNotNull { el ->
                val r = el as? JsonObject ?: return@mapNotNull null
                val title = r.str("title")?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val url = r.str("applicationLink")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                val id = r.str("guid")?.takeIf { it.isNotBlank() } ?: url
                val expires = r.millis("expiryDate")
                if (expires != null && expires.isBefore(now)) return@mapNotNull null

                val places = (r["locationRestrictions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject) }
                val codes = places.mapNotNull { it.str("alpha2")?.uppercase() }
                // Defensive: the search already filters by country, but never show a job the candidate can't take.
                if (codes.isNotEmpty() && country.uppercase() !in codes) return@mapNotNull null
                val names = places.mapNotNull { it.str("name") }
                val location = if (names.isEmpty()) "Remote (worldwide)" else "Remote (${names.take(4).joinToString(", ")}${if (names.size > 4) " +${names.size - 4} more" else ""})"

                val description = Html.toText(r.str("description") ?: r.str("excerpt").orEmpty()).trim()
                JobPosting(
                    sourceJobId = id.take(300),
                    title = title,
                    location = location,
                    remote = true,
                    description = description.take(20_000),
                    applyUrl = url,
                    postedAt = r.millis("pubDate"),
                    employer = r.str("companyName")?.trim()?.takeIf { it.isNotEmpty() } ?: "Employer not named",
                    partial = r.str("description").isNullOrBlank(),
                )
            }
            val total = (o["totalCount"] as? JsonPrimitive)?.content?.toIntOrNull() ?: postings.size
            return SearchPage(postings, total)
        }

        private fun JsonObject.str(key: String): String? = this[key]?.let { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }

        /** Himalayas sends dates as Unix milliseconds; ISO strings are accepted too, in case that changes. */
        private fun JsonObject.millis(key: String): Instant? {
            val p = this[key] as? JsonPrimitive ?: return null
            if (p is JsonNull) return null
            p.longOrNull?.let { return if (it > 100_000_000_000L) Instant.ofEpochMilli(it) else Instant.ofEpochSecond(it) }
            return runCatching { Instant.parse(p.content) }.getOrNull()
        }
    }
}
