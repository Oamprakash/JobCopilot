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
import java.time.Instant
import java.time.OffsetDateTime

data class AdzunaConfig(val appId: String, val appKey: String, val country: String = "in", val dailyCalls: Int = 60)

data class SearchPage(val postings: List<JobPosting>, val total: Int)

/**
 * Adzuna's job search API (developer.adzuna.com), used under its API terms with our own app id and key.
 * Adzuna returns a summary of each description, not the full text, so postings are marked [JobPosting.partial].
 * Links go through Adzuna's redirect_url, which leads to the employer's or board's posting.
 */
class AdzunaClient(
    private val http: HttpClient,
    private val config: AdzunaConfig,
    private val base: String = "https://api.adzuna.com",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Where searches without a known country go (ADZUNA_COUNTRY, default India). */
    val homeCountry: String get() = config.country

    /** Searches [query] in its own country (a Singapore search goes to Adzuna Singapore). */
    suspend fun search(query: SearchQuery, page: Int = 1): SearchPage {
        val country = query.country
        require(Regex("^[a-z]{2}$").matches(country)) { "Country must be a two-letter code" }
        val response = http.get("$base/v1/api/jobs/$country/search/$page") {
            parameter("app_id", config.appId)
            parameter("app_key", config.appKey)
            parameter("results_per_page", PAGE_SIZE)
            parameter("what", query.what)
            if (query.where.isNotBlank()) parameter("where", query.where)
            parameter("max_days_old", MAX_DAYS_OLD)
            parameter("sort_by", "date")
            parameter("content-type", "application/json")
        }
        if (!response.status.isSuccess()) {
            throw AtsException(
                when (response.status.value) {
                    401, 403 -> "Adzuna rejected the app id or key (${response.status.value})"
                    429 -> "Adzuna rate limit reached (429)"
                    else -> "Adzuna returned ${response.status.value}"
                },
            )
        }
        val body = try {
            json.parseToJsonElement(response.bodyAsText())
        } catch (e: Exception) {
            throw AtsException("Adzuna returned invalid JSON")
        }
        return parse(country, body)
    }

    companion object {
        const val PAGE_SIZE = 50
        const val MAX_DAYS_OLD = 30

        private val remoteWords = Regex("\\b(remote|work from home|wfh)\\b", RegexOption.IGNORE_CASE)
        private val countryNames = mapOf(
            "in" to "India", "sg" to "Singapore", "gb" to "United Kingdom", "us" to "United States", "au" to "Australia",
            "ca" to "Canada", "de" to "Germany", "nl" to "Netherlands", "nz" to "New Zealand",
        )

        internal fun parse(country: String, body: JsonElement): SearchPage {
            val o = body as? JsonObject ?: throw AtsException("Adzuna returned an unexpected shape")
            val countryName = countryNames[country] ?: country.uppercase()
            val postings = (o["results"] as? JsonArray).orEmpty().mapNotNull { el ->
                val r = el as? JsonObject ?: return@mapNotNull null
                val id = r.str("id") ?: return@mapNotNull null
                // Adzuna highlights search words with <strong>; titles and summaries are HTML fragments.
                val title = Html.toText(r.str("title") ?: return@mapNotNull null).replace(Regex("\\s+"), " ").trim()
                val url = r.str("redirect_url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
                val description = Html.toText(r.str("description").orEmpty()).trim()
                val place = (r["location"] as? JsonObject)?.str("display_name").orEmpty()
                val location = if (place.contains(countryName, ignoreCase = true) || place.isBlank()) place.ifBlank { countryName } else "$place, $countryName"
                JobPosting(
                    sourceJobId = "$country:$id",
                    title = title,
                    location = location,
                    remote = remoteWords.containsMatchIn(title) || remoteWords.containsMatchIn(place),
                    description = description.take(20_000),
                    applyUrl = url,
                    postedAt = r.str("created")?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull() },
                    employer = (r["company"] as? JsonObject)?.str("display_name")?.trim()?.takeIf { it.isNotEmpty() } ?: "Employer not named",
                    partial = true,
                )
            }
            val total = (o["count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: postings.size
            return SearchPage(postings, total)
        }

        private fun JsonObject.str(key: String): String? = this[key]?.let { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
    }
}
