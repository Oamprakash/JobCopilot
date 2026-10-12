package com.applycopilot.jobs

import com.applycopilot.matching.Html
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class Ats(val id: String) {
    GREENHOUSE("greenhouse"), LEVER("lever"), ASHBY("ashby"), SMARTRECRUITERS("smartrecruiters"), WORKABLE("workable");

    companion object {
        fun from(id: String): Ats? = entries.firstOrNull { it.id == id }
    }
}

/** One posting from a public job board, normalized across ATSs. [description] is plain text. */
data class JobPosting(
    val sourceJobId: String,
    val title: String,
    val location: String,
    val remote: Boolean,
    val description: String,
    val applyUrl: String,
    val postedAt: Instant?,
)

class AtsException(message: String) : RuntimeException(message)

/**
 * Reads the public job-board APIs that Greenhouse, Lever, Ashby, Workable and SmartRecruiters publish
 * for career-site and job-board reuse. Nothing here logs in, scrapes, or touches LinkedIn/Naukri/Indeed.
 */
class AtsClient(
    private val http: HttpClient,
    private val greenhouseBase: String = "https://boards-api.greenhouse.io",
    private val leverBase: String = "https://api.lever.co",
    private val ashbyBase: String = "https://api.ashbyhq.com",
    private val smartRecruitersBase: String = "https://api.smartrecruiters.com",
    private val workableBase: String = "https://apply.workable.com",
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(ats: Ats, token: String): List<JobPosting> {
        require(TOKEN.matches(token)) { "Invalid board token" }
        return when (ats) {
            Ats.GREENHOUSE -> greenhouse(token, getJson("$greenhouseBase/v1/boards/$token/jobs?content=true"))
            Ats.LEVER -> lever(token, getJson("$leverBase/v0/postings/$token?mode=json"))
            Ats.ASHBY -> ashby(token, getJson("$ashbyBase/posting-api/job-board/$token?includeCompensation=false"))
            Ats.SMARTRECRUITERS -> smartRecruiters(token)
            Ats.WORKABLE -> workable(token, getJson("$workableBase/api/v1/widget/accounts/$token?details=true"))
        }
    }

    private suspend fun getJson(url: String): JsonElement {
        val response = http.get(url)
        if (!response.status.isSuccess()) throw AtsException("Board returned ${response.status.value}")
        return try {
            json.parseToJsonElement(response.bodyAsText())
        } catch (e: Exception) {
            throw AtsException("Board returned invalid JSON")
        }
    }

    internal fun greenhouse(token: String, body: JsonElement): List<JobPosting> =
        (body.obj()?.get("jobs") as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            val url = o.str("absolute_url") ?: return@mapNotNull null
            val location = o["location"].obj()?.str("name").orEmpty()
            JobPosting(
                sourceJobId = "$token:$id",
                title = title.trim(),
                location = location,
                remote = location.contains("remote", ignoreCase = true),
                description = clip(Html.toText(o.str("content").orEmpty())),
                applyUrl = url,
                postedAt = instant(o.str("first_published")) ?: instant(o.str("updated_at")),
            )
        }

    internal fun lever(token: String, body: JsonElement): List<JobPosting> =
        (body as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val title = o.str("text") ?: return@mapNotNull null
            val url = o.str("hostedUrl") ?: o.str("applyUrl") ?: return@mapNotNull null
            val categories = o["categories"].obj()
            val places = (categories?.get("allLocations") as? JsonArray)?.mapNotNull { it.prim() }?.takeIf { it.isNotEmpty() }
                ?: listOfNotNull(categories?.str("location"))
            val country = o.str("country")?.let { countryNames[it.uppercase()] ?: it }
            val workplace = o.str("workplaceType")
            val description = buildString {
                o.str("openingPlain")?.let { appendLine(it) }
                (o.str("descriptionBodyPlain")?.takeIf { it.isNotBlank() } ?: o.str("descriptionPlain"))?.let { appendLine(it) }
                (o["lists"] as? JsonArray)?.forEach { item ->
                    val list = item.obj() ?: return@forEach
                    list.str("text")?.let { appendLine(it) }
                    list.str("content")?.let { appendLine(Html.toText("<ul>$it</ul>")) }
                }
                o.str("additionalPlain")?.let { appendLine(it) }
            }
            JobPosting(
                sourceJobId = "$token:$id",
                title = title.trim(),
                location = (places + listOfNotNull(country)).distinct().joinToString(", "),
                remote = workplace.equals("remote", ignoreCase = true),
                description = clip(description.trim()),
                applyUrl = url,
                postedAt = (o["createdAt"] as? JsonPrimitive)?.longOrNull?.let { Instant.ofEpochMilli(it) },
            )
        }

    internal fun ashby(token: String, body: JsonElement): List<JobPosting> =
        (body.obj()?.get("jobs") as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            if ((o["isListed"] as? JsonPrimitive)?.booleanOrNull == false) return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            val url = o.str("jobUrl") ?: o.str("applyUrl") ?: return@mapNotNull null
            val country = o["address"].obj()?.get("postalAddress").obj()?.str("addressCountry")
            val secondary = (o["secondaryLocations"] as? JsonArray)?.mapNotNull { it.obj()?.str("location") }.orEmpty()
            val workplace = o.str("workplaceType")
            val isRemote = (o["isRemote"] as? JsonPrimitive)?.booleanOrNull == true
            JobPosting(
                sourceJobId = "$token:$id",
                title = title.trim(),
                location = (listOfNotNull(o.str("location")) + secondary + listOfNotNull(country)).distinct().joinToString(", "),
                // Ashby sets isRemote on hybrid roles too; workplaceType is the reliable field when present.
                remote = if (workplace != null) workplace.equals("remote", ignoreCase = true) else isRemote,
                description = clip(o.str("descriptionPlain")?.takeIf { it.isNotBlank() } ?: Html.toText(o.str("descriptionHtml").orEmpty())),
                applyUrl = url,
                postedAt = instant(o.str("publishedAt")),
            )
        }

    /**
     * SmartRecruiters lists postings without descriptions, so descriptions come from one detail call per
     * posting, made only for postings in India or remote (the ones this product can match), capped per sync.
     */
    private suspend fun smartRecruiters(token: String): List<JobPosting> {
        val items = mutableListOf<JsonObject>()
        var offset = 0
        while (offset < SR_MAX_POSTINGS) {
            val page = getJson("$smartRecruitersBase/v1/companies/$token/postings?limit=100&offset=$offset").obj()
                ?: throw AtsException("Board returned an unexpected shape")
            val content = (page["content"] as? JsonArray).orEmpty().mapNotNull { it.obj() }
            items += content
            offset += content.size
            val total = (page["totalFound"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            if (content.isEmpty() || offset >= total) break
        }
        // An unknown company id and a company with no openings look the same here: zero postings.
        if (items.isEmpty()) return emptyList()

        val gate = Semaphore(4)
        var detailBudget = SR_MAX_DETAILS
        return coroutineScope {
            items.map { item ->
                val wantDetail = srRelevant(item) && detailBudget-- > 0
                async {
                    val detail = if (wantDetail) {
                        gate.withPermit {
                            runCatching { getJson("$smartRecruitersBase/v1/companies/$token/postings/${item.str("id")}").obj() }.getOrNull()
                        }
                    } else null
                    smartRecruitersPosting(token, item, detail)
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun srRelevant(item: JsonObject): Boolean {
        val loc = item["location"].obj()
        return loc?.str("country").equals("in", ignoreCase = true) || (loc?.get("remote") as? JsonPrimitive)?.booleanOrNull == true
    }

    internal fun smartRecruitersPosting(token: String, item: JsonObject, detail: JsonObject?): JobPosting? {
        val id = item.str("id") ?: return null
        val title = item.str("name") ?: return null
        val loc = (detail?.get("location") ?: item["location"]).obj()
        val country = loc?.str("country")?.let { countryNames[it.uppercase()] ?: it }
        val remote = (loc?.get("remote") as? JsonPrimitive)?.booleanOrNull == true
        val location = listOfNotNull(loc?.str("city"), loc?.str("region"), country, if (remote) "Remote" else null)
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
        val sections = detail?.get("jobAd").obj()?.get("sections").obj()
        val description = listOf("jobDescription", "qualifications", "additionalInformation")
            .mapNotNull { key -> sections?.get(key).obj()?.str("text")?.let { Html.toText(it) } }
            .filter { it.isNotBlank() }
            .joinToString("\n")
        return JobPosting(
            sourceJobId = "$token:$id",
            title = title.trim(),
            location = location,
            remote = remote,
            description = clip(description),
            applyUrl = detail?.str("postingUrl") ?: "https://jobs.smartrecruiters.com/$token/$id",
            postedAt = instant(item.str("releasedDate")),
        )
    }

    internal fun workable(token: String, body: JsonElement): List<JobPosting> =
        (body.obj()?.get("jobs") as? JsonArray).orEmpty().mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val code = o.str("shortcode") ?: return@mapNotNull null
            val title = o.str("title") ?: return@mapNotNull null
            val url = o.str("url") ?: o.str("shortlink") ?: return@mapNotNull null
            val places = (o["locations"] as? JsonArray)?.mapNotNull { l ->
                val lo = l.obj() ?: return@mapNotNull null
                if ((lo["hidden"] as? JsonPrimitive)?.booleanOrNull == true) return@mapNotNull null
                listOfNotNull(lo.str("city"), lo.str("region"), lo.str("country")).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null }
            }.orEmpty().ifEmpty {
                listOfNotNull(listOfNotNull(o.str("city"), o.str("state"), o.str("country")).filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null })
            }
            val remote = (o["telecommuting"] as? JsonPrimitive)?.booleanOrNull == true
            JobPosting(
                sourceJobId = "$token:$code",
                title = title.trim(),
                location = (places + listOfNotNull(if (remote) "Remote" else null)).joinToString("; "),
                remote = remote,
                description = clip(Html.toText(o.str("description").orEmpty())),
                applyUrl = url,
                postedAt = o.str("published_on")?.let { d -> runCatching { LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull() },
            )
        }

    companion object {
        private const val SR_MAX_POSTINGS = 1000
        private const val SR_MAX_DETAILS = 300
        val TOKEN = Regex("^[A-Za-z0-9_-]{1,100}$")
        private const val MAX_DESCRIPTION = 20_000
        private val countryNames = mapOf("IN" to "India", "US" to "United States", "GB" to "United Kingdom", "SG" to "Singapore", "DE" to "Germany")

        private fun clip(s: String) = s.take(MAX_DESCRIPTION)
        private fun instant(s: String?): Instant? = s?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
        private fun JsonElement.prim(): String? = (this as? JsonPrimitive)?.takeIf { this !is JsonNull }?.content
        private fun JsonObject.str(key: String): String? = this[key]?.let { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
    }
}
