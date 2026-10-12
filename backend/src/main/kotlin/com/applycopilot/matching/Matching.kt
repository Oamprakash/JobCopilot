package com.applycopilot.matching

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import com.applycopilot.core.SkillDictionary
import java.util.UUID
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A job as the matcher sees it. [description] is plain text. */
data class JobForMatching(
    val id: UUID,
    val company: String,
    val title: String,
    val location: String,
    val remote: Boolean,
    val description: String,
)

data class FitScore(val score: Int, val reasons: List<String>, val missingSkills: List<String>)

// ---- Locations ------------------------------------------------------------------------------

object Locations {
    private val aliasGroups: List<Set<String>> = listOf(
        setOf("bengaluru", "bangalore", "blr"),
        setOf("mumbai", "bombay", "navi mumbai", "thane"),
        setOf("chennai", "madras"),
        setOf("hyderabad", "secunderabad"),
        setOf("pune"),
        setOf("delhi", "new delhi", "ncr", "delhi ncr"),
        setOf("gurugram", "gurgaon"),
        setOf("noida", "greater noida"),
        setOf("kolkata", "calcutta"),
        setOf("ahmedabad"),
        setOf("coimbatore"),
        setOf("kochi", "cochin"),
        setOf("thiruvananthapuram", "trivandrum"),
        setOf("mysuru", "mysore"),
        setOf("visakhapatnam", "vizag"),
        setOf("jaipur"), setOf("indore"), setOf("chandigarh"), setOf("lucknow"), setOf("nagpur"), setOf("bhubaneswar"),
        setOf("madurai"),
    )

    private val indiaWords: Set<String> = aliasGroups.flatten().toSet() + setOf("india")
    private val remoteRegions = setOf("apac", "asia", "anywhere", "worldwide", "global")

    private fun has(text: String, word: String) = Regex("(?<![a-z])${Regex.escape(word)}(?![a-z])").containsMatchIn(text)

    fun mentionsIndia(location: String): Boolean = location.lowercase().let { l -> indiaWords.any { has(l, it) } }

    fun cityMatches(userCity: String, jobLocation: String): Boolean {
        val u = userCity.trim().lowercase()
        if (u.isEmpty()) return false
        val group = aliasGroups.firstOrNull { u in it } ?: setOf(u)
        val l = jobLocation.lowercase()
        return group.any { has(l, it) }
    }

    fun isRemote(job: JobForMatching): Boolean =
        job.remote || job.location.lowercase().let { has(it, "remote") || it.contains("work from home") }

    /** A remote job an India-based candidate can actually take. */
    fun remoteForIndia(job: JobForMatching): Boolean {
        if (!isRemote(job)) return false
        val l = job.location.lowercase()
        return mentionsIndia(l) || remoteRegions.any { has(l, it) }
    }
}

// ---- Hard filter ----------------------------------------------------------------------------

data class FilterResult(val pass: Boolean, val reason: String)

object HardFilter {
    private val generic = setOf(
        "senior", "sr", "junior", "jr", "lead", "staff", "principal", "head", "chief", "associate", "assistant",
        "engineer", "engineering", "developer", "development", "software", "sde", "specialist", "manager",
        "consultant", "architect", "expert", "member", "technical", "tech", "of", "and", "the", "for", "in", "at",
        "i", "ii", "iii", "iv", "1", "2", "3", "4",
    )
    private val synonyms = mapOf(
        "android" to setOf("android", "mobile"),
        "ios" to setOf("ios", "mobile"),
        "mobile" to setOf("mobile", "android", "ios"),
        "frontend" to setOf("frontend", "front", "ui", "web"),
        "backend" to setOf("backend", "back", "server"),
        "fullstack" to setOf("fullstack", "full"),
        "ml" to setOf("ml", "machine", "ai"),
        "devops" to setOf("devops", "sre", "platform", "infrastructure"),
    )
    private val traineeWords = setOf("intern", "internship", "trainee", "graduate", "fresher", "apprentice")

    fun tokens(s: String): List<String> =
        s.lowercase().replace("front-end", "frontend").replace("back-end", "backend").replace("full-stack", "fullstack")
            .replace("full stack", "fullstack").split(Regex("[^a-z0-9+#]+")).filter { it.isNotEmpty() }

    /** The distinctive words of the target roles: "Senior Android Engineer" → {android}. */
    fun coreRoleTokens(prefs: Preferences, profile: Profile): Set<String> {
        val roles = prefs.roles.ifEmpty { listOf(profile.headline) }
        return roles.flatMap { tokens(it) }.filter { it !in generic }.toSet()
    }

    fun titleMatchesRole(title: String, prefs: Preferences, profile: Profile): Boolean {
        val titleTokens = tokens(title).toSet()
        val core = coreRoleTokens(prefs, profile)
        if (core.isNotEmpty()) return core.any { c -> (synonyms[c] ?: setOf(c)).any { it in titleTokens } }
        // Only generic words like "Software Engineer": require one of them.
        val roleWords = prefs.roles.ifEmpty { listOf(profile.headline) }.flatMap { tokens(it) }
            .filter { it !in setOf("of", "and", "the", "for", "in", "at", "senior", "sr", "junior", "jr", "lead") }
        return roleWords.isEmpty() || roleWords.any { it in titleTokens }
    }

    private val yearsRegex = Regex("(\\d{1,2})\\s*\\+?\\s*(?:(?:-|–|to)\\s*(\\d{1,2})\\s*\\+?\\s*)?(?:years|yrs)", RegexOption.IGNORE_CASE)

    /** The experience a JD asks for, as (min, max?) in years, taking its largest stated minimum. */
    fun requiredYears(description: String): Pair<Int, Int?>? =
        yearsRegex.findAll(description)
            .mapNotNull { m ->
                val lo = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val hi = m.groupValues[2].toIntOrNull()
                if (lo in 1..25 && (hi == null || hi in lo..30)) lo to hi else null
            }
            .maxByOrNull { it.first }

    fun evaluate(job: JobForMatching, profile: Profile, prefs: Preferences): FilterResult {
        val company = job.company.lowercase()
        if (prefs.skipCompanies.any { skip -> skip.lowercase().let { it == company || company.contains(it) || it.contains(company) } }) {
            return FilterResult(false, "company on skip list")
        }

        val cities = prefs.locations.filterNot { it.equals("remote", ignoreCase = true) }
        val acceptsRemote = prefs.remoteMode == RemoteMode.REMOTE || prefs.remoteMode == RemoteMode.ANY ||
            prefs.locations.any { it.equals("remote", ignoreCase = true) }
        val cityMatch = if (cities.isEmpty()) Locations.mentionsIndia(job.location) else cities.any { Locations.cityMatches(it, job.location) }
        val remoteOk = acceptsRemote && Locations.remoteForIndia(job)
        val locationOk = when (prefs.remoteMode) {
            RemoteMode.REMOTE -> remoteOk
            RemoteMode.ONSITE, RemoteMode.HYBRID -> cityMatch || (remoteOk && prefs.locations.any { it.equals("remote", true) })
            RemoteMode.ANY -> cityMatch || remoteOk
        }
        if (!locationOk) return FilterResult(false, "location")

        if (!titleMatchesRole(job.title, prefs, profile)) return FilterResult(false, "role")

        val years = prefs.experienceYears ?: profile.totalExperienceYears
        val titleTokens = tokens(job.title)
        if (years != null) {
            if (years >= 3 && titleTokens.any { it in traineeWords }) return FilterResult(false, "entry-level role")
            if (years >= 6 && titleTokens.any { it == "junior" || it == "jr" }) return FilterResult(false, "junior role")
            requiredYears(job.description)?.let { (lo, hi) ->
                if (lo > years + 1) return FilterResult(false, "needs $lo+ years")
                if (hi != null && years > hi + 6) return FilterResult(false, "asks for $lo–$hi years")
            }
        }
        return FilterResult(true, "ok")
    }
}

// ---- Lexical shortlist ----------------------------------------------------------------------

/** TF-IDF cosine between the profile and each job; cheap enough to run on every candidate. */
object LexicalRanker {
    private val stop = setOf(
        "the", "and", "for", "with", "you", "your", "our", "are", "will", "can", "this", "that", "from", "have", "has",
        "who", "what", "work", "team", "teams", "join", "role", "about", "into", "they", "their", "them", "all", "any",
        "able", "also", "more", "such", "using", "use", "well", "new", "help", "make", "being", "across", "within",
    )

    private fun terms(text: String): List<String> =
        HardFilter.tokens(text).filter { it.length >= 2 && it !in stop }

    private fun profileVector(p: Profile): Map<String, Double> {
        val v = HashMap<String, Double>()
        fun add(text: String, w: Double) = terms(text).forEach { v.merge(it, w, Double::plus) }
        p.skills.forEach { add(it, 3.0) }
        add(p.headline, 2.0)
        p.experience.forEach { add(it.title, 2.0); it.bullets.forEach { b -> add(b, 1.0) } }
        p.summary?.let { add(it, 1.0) }
        return v
    }

    private fun jobTf(j: JobForMatching): Map<String, Double> {
        val counts = HashMap<String, Double>()
        terms(j.title).forEach { counts.merge(it, 3.0, Double::plus) }
        terms(j.description).forEach { counts.merge(it, 1.0, Double::plus) }
        // Dampen long JDs that repeat words.
        return counts.mapValues { 1 + ln(it.value) }
    }

    fun rank(jobs: List<JobForMatching>, profile: Profile): List<Pair<JobForMatching, Double>> {
        if (jobs.isEmpty()) return emptyList()
        val tfs = jobs.map { jobTf(it) }
        val df = HashMap<String, Int>()
        tfs.forEach { tf -> tf.keys.forEach { df.merge(it, 1, Int::plus) } }
        val n = jobs.size
        fun idf(t: String) = ln((n + 1.0) / ((df[t] ?: 0) + 1.0)) + 1.0

        val pv = profileVector(profile).mapValues { (t, w) -> w * idf(t) }
        val pNorm = sqrt(pv.values.sumOf { it * it })
        return jobs.zip(tfs).map { (job, tf) ->
            val jv = tf.mapValues { (t, w) -> w * idf(t) }
            val dot = pv.entries.sumOf { (t, w) -> w * (jv[t] ?: 0.0) }
            val jNorm = sqrt(jv.values.sumOf { it * it })
            job to if (pNorm == 0.0 || jNorm == 0.0) 0.0 else dot / (pNorm * jNorm)
        }.sortedByDescending { it.second }
    }
}

// ---- Heuristic fit score --------------------------------------------------------------------

/** Rule-based fit score used without an API key, past the daily LLM budget, or when the LLM call fails. */
object HeuristicScorer {
    fun containsPhrase(text: String, phrase: String): Boolean {
        val t = " " + HardFilter.tokens(text).joinToString(" ") + " "
        val p = HardFilter.tokens(phrase).joinToString(" ")
        return p.isNotEmpty() && t.contains(" $p ")
    }

    fun score(job: JobForMatching, profile: Profile, prefs: Preferences, similarity: Double): FitScore {
        val jobText = job.title + "\n" + job.description
        val jobSkills = SkillDictionary.findIn(jobText)
        val matched = profile.skills.filter { containsPhrase(jobText, it) }
        // A skill counts as present if the profile mentions it anywhere (a title, a bullet), not only in the skills list.
        val profileText = profileText(profile)
        val missing = jobSkills.filter { !containsPhrase(profileText, it) }

        val coverage = if (jobSkills.isEmpty()) min(1.0, similarity * 2.5)
        else jobSkills.count { js -> profile.skills.any { it.equals(js, true) || containsPhrase(it, js) } }.toDouble() / jobSkills.size
        val titleFit = if (HardFilter.titleMatchesRole(job.title, prefs, profile)) 1.0 else 0.4
        val years = prefs.experienceYears ?: profile.totalExperienceYears
        val required = HardFilter.requiredYears(job.description)
        val expFit = when {
            years == null || required == null -> 0.7
            years >= required.first -> 1.0
            else -> 0.6
        }
        val score = (100 * (0.45 * coverage + 0.25 * titleFit + 0.15 * expFit + 0.15 * min(1.0, similarity * 2.5)))
            .roundToInt().coerceIn(0, 100)

        val reasons = buildList {
            if (matched.isNotEmpty()) add(listSentence(matched.take(3)) + if (matched.size == 1) " matches your skills" else " match your skills")
            if (titleFit == 1.0) add("Title fits your target role")
            if (years != null && required != null && years >= required.first) add("Asks ${required.first}+ years; you have ${formatYears(years)}")
            if (Locations.remoteForIndia(job)) add("Remote, open to India")
            else prefs.locations.firstOrNull { Locations.cityMatches(it, job.location) }?.let { add("In $it, one of your locations") }
        }.take(3)

        return FitScore(score, reasons, missing.take(3))
    }

    fun profileText(p: Profile): String = buildString {
        appendLine(p.headline)
        p.skills.forEach { appendLine(it) }
        p.summary?.let { appendLine(it) }
        p.experience.forEach { e -> appendLine(e.title); e.bullets.forEach { appendLine(it) } }
    }

    private fun listSentence(items: List<String>) = when (items.size) {
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    private fun formatYears(y: Double) = if (y % 1.0 == 0.0) y.toInt().toString() else y.toString()
}
