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
    /**
     * A place the filter knows by name. [country] is an ISO 3166 code. A country-level place ("Malaysia")
     * matches a job anywhere in that country; a city matches its own names only.
     */
    private data class Place(val display: String, val aliases: List<String>, val country: String, val isCountry: Boolean = false) {
        val names: List<String> = listOf(display.lowercase()) + aliases
    }

    private fun india(display: String, vararg aliases: String) = Place(display, aliases.toList(), "in")

    private val places: List<Place> = listOf(
        india("Bengaluru", "bangalore", "blr"),
        india("Mumbai", "bombay", "navi mumbai", "thane"),
        india("Chennai", "madras"),
        india("Hyderabad", "secunderabad"),
        india("Pune"),
        india("Delhi", "new delhi", "ncr", "delhi ncr"),
        india("Gurugram", "gurgaon"),
        india("Noida", "greater noida"),
        india("Kolkata", "calcutta"),
        india("Ahmedabad"),
        india("Coimbatore"),
        india("Kochi", "cochin"),
        india("Thiruvananthapuram", "trivandrum"),
        india("Mysuru", "mysore"),
        india("Visakhapatnam", "vizag"),
        india("Jaipur"), india("Indore"), india("Chandigarh"), india("Lucknow"), india("Nagpur"), india("Bhubaneswar"),
        india("Madurai"),
        // Abroad: the Gulf and Southeast Asia hubs Indian engineers most often move to.
        Place("Singapore", emptyList(), "sg"),
        Place("Dubai", emptyList(), "ae"),
        Place("Abu Dhabi", emptyList(), "ae"),
        Place("Sharjah", emptyList(), "ae"),
        Place("Kuala Lumpur", emptyList(), "my"),
        Place("Penang", listOf("george town", "bayan lepas"), "my"),
        Place("Cyberjaya", emptyList(), "my"),
        Place("Petaling Jaya", emptyList(), "my"),
        Place("Johor Bahru", emptyList(), "my"),
        // Countries with employer-sponsored routes for skilled tech workers (see the web app's visa guide).
        Place("Berlin", emptyList(), "de"), Place("Munich", listOf("münchen", "muenchen"), "de"), Place("Hamburg", emptyList(), "de"),
        Place("Frankfurt", listOf("frankfurt am main"), "de"), Place("Cologne", listOf("köln", "koeln"), "de"), Place("Stuttgart", emptyList(), "de"),
        Place("Amsterdam", emptyList(), "nl"), Place("Rotterdam", emptyList(), "nl"), Place("Eindhoven", emptyList(), "nl"),
        Place("The Hague", listOf("den haag"), "nl"), Place("Utrecht", emptyList(), "nl"),
        Place("London", emptyList(), "gb"), Place("Manchester", emptyList(), "gb"), Place("Edinburgh", emptyList(), "gb"), Place("Bristol", emptyList(), "gb"),
        Place("Dublin", emptyList(), "ie"), Place("Cork", emptyList(), "ie"),
        Place("Toronto", emptyList(), "ca"), Place("Vancouver", emptyList(), "ca"), Place("Montreal", listOf("montréal"), "ca"), Place("Ottawa", emptyList(), "ca"),
        Place("Sydney", emptyList(), "au"), Place("Melbourne", emptyList(), "au"), Place("Brisbane", emptyList(), "au"),
        Place("Auckland", emptyList(), "nz"), Place("Wellington", emptyList(), "nz"),
        Place("Tokyo", emptyList(), "jp"),
        Place("India", emptyList(), "in", isCountry = true),
        Place("Malaysia", emptyList(), "my", isCountry = true),
        Place("UAE", listOf("united arab emirates"), "ae", isCountry = true),
        Place("Germany", listOf("deutschland"), "de", isCountry = true),
        Place("Netherlands", listOf("the netherlands", "holland"), "nl", isCountry = true),
        Place("United Kingdom", listOf("uk", "england", "scotland"), "gb", isCountry = true),
        Place("Ireland", emptyList(), "ie", isCountry = true),
        Place("Canada", emptyList(), "ca", isCountry = true),
        Place("Australia", emptyList(), "au", isCountry = true),
        Place("New Zealand", emptyList(), "nz", isCountry = true),
        Place("Japan", emptyList(), "jp", isCountry = true),
    )
    private val countryNames: Map<String, List<String>> =
        places.filter { it.isCountry }.associate { it.country to it.names } + ("sg" to listOf("singapore"))

    private val indiaWords: Set<String> = places.filter { it.country == "in" }.flatMap { it.names }.toSet() + setOf("india")

    private val wordRegexes = java.util.concurrent.ConcurrentHashMap<String, Regex>()
    private fun has(text: String, word: String) =
        wordRegexes.computeIfAbsent(word) { Regex("(?<![a-z])${Regex.escape(it)}(?![a-z])") }.containsMatchIn(text)

    private fun titleCase(s: String) = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun placeNamed(name: String): Place? = name.trim().lowercase().let { n -> places.firstOrNull { n in it.names } }

    /** Every place the filter knows by name, as display names: Indian cities first, then abroad. */
    val cities: List<String> get() = places.map { it.display }

    /** Other names the filter accepts for a place ("Bengaluru" → ["Bangalore", "Blr"]). */
    fun aliasesOf(city: String): List<String> = places.firstOrNull { it.display == city }?.aliases?.map(::titleCase).orEmpty()

    /** The country code of a known place name ("Bangalore" → "in", "Dubai" → "ae"), or null for one we don't know. */
    fun countryOf(name: String): String? = placeNamed(name)?.country

    /** True for a whole-country choice such as "Malaysia" or "UAE". */
    fun isCountry(name: String): Boolean = placeNamed(name)?.isCountry == true

    /** Known cities named in a job location, as display names ("Bangalore, KA" → ["Bengaluru"]). */
    fun citiesIn(location: String): List<String> {
        val l = location.lowercase()
        return places.filter { !it.isCountry && it.names.any { n -> has(l, n) } }.map { it.display }
    }

    /** True when a job location names a place we know outside India (and none in India). */
    fun isAbroad(location: String): Boolean {
        val l = location.lowercase()
        if (mentionsIndia(l)) return false
        return places.any { it.country != "in" && it.names.any { n -> has(l, n) } } ||
            countryNames.any { (code, names) -> code != "in" && names.any { has(l, it) } }
    }

    fun mentionsIndia(location: String): Boolean = location.lowercase().let { l -> indiaWords.any { has(l, it) } }

    /**
     * Whether a job location fits one of the user's locations: any name of that city, any place in that country for
     * a country choice, or the words as typed for a place we don't know.
     */
    fun cityMatches(userCity: String, jobLocation: String): Boolean {
        val u = userCity.trim().lowercase()
        if (u.isEmpty()) return false
        val place = places.firstOrNull { u in it.names }
        val names = when {
            place == null -> listOf(u)
            place.isCountry -> places.filter { it.country == place.country }.flatMap { it.names } + countryNames[place.country].orEmpty()
            else -> place.names
        }
        val l = jobLocation.lowercase()
        return names.any { has(l, it) }
    }

    fun isRemote(job: JobForMatching): Boolean =
        job.remote || job.location.lowercase().let { has(it, "remote") || it.contains("work from home") }

    /**
     * Countries people can pick to work remotely from, with the names and regions job listings use for them.
     * Display names are what the feed shows ("Remote, open to people in Canada").
     */
    private class RemoteCountry(val display: String, val names: List<String>, val regions: List<String>)

    private val remoteCountries: Map<String, RemoteCountry> = linkedMapOf(
        "in" to RemoteCountry("India", emptyList(), listOf("apac", "asia", "south asia")),
        "us" to RemoteCountry("the United States", listOf("united states", "usa", "us", "u.s.", "united states of america"), listOf("americas", "north america", "us timezones")),
        "ca" to RemoteCountry("Canada", listOf("canada"), listOf("americas", "north america")),
        "gb" to RemoteCountry("the United Kingdom", listOf("united kingdom", "uk", "england", "scotland", "wales"), listOf("europe", "emea")),
        "ie" to RemoteCountry("Ireland", listOf("ireland"), listOf("europe", "emea", "eu")),
        "de" to RemoteCountry("Germany", listOf("germany", "deutschland"), listOf("europe", "emea", "eu", "dach")),
        "nl" to RemoteCountry("the Netherlands", listOf("netherlands", "the netherlands", "holland"), listOf("europe", "emea", "eu")),
        "fr" to RemoteCountry("France", listOf("france"), listOf("europe", "emea", "eu")),
        "es" to RemoteCountry("Spain", listOf("spain"), listOf("europe", "emea", "eu")),
        "pt" to RemoteCountry("Portugal", listOf("portugal"), listOf("europe", "emea", "eu")),
        "pl" to RemoteCountry("Poland", listOf("poland"), listOf("europe", "emea", "eu")),
        "ae" to RemoteCountry("the UAE", listOf("uae", "united arab emirates", "dubai"), listOf("emea", "middle east", "gcc")),
        "sg" to RemoteCountry("Singapore", listOf("singapore"), listOf("apac", "asia", "southeast asia")),
        "my" to RemoteCountry("Malaysia", listOf("malaysia"), listOf("apac", "asia", "southeast asia")),
        "ph" to RemoteCountry("the Philippines", listOf("philippines"), listOf("apac", "asia", "southeast asia")),
        "jp" to RemoteCountry("Japan", listOf("japan"), listOf("apac", "asia")),
        "au" to RemoteCountry("Australia", listOf("australia"), listOf("apac", "oceania", "anz")),
        "nz" to RemoteCountry("New Zealand", listOf("new zealand"), listOf("apac", "oceania", "anz")),
        "br" to RemoteCountry("Brazil", listOf("brazil", "brasil"), listOf("americas", "latam", "latin america", "south america")),
        "mx" to RemoteCountry("Mexico", listOf("mexico"), listOf("americas", "latam", "latin america", "north america")),
    )

    private val globalWords = listOf("anywhere", "worldwide", "global")

    /** Country codes people can choose for remote work. */
    val remoteCountryCodes: Set<String> get() = remoteCountries.keys

    /** Whether [location] names country [code] (any of its names, or a known city in it). India also counts its cities. */
    fun inCountry(code: String, location: String): Boolean {
        val l = location.lowercase()
        if (code == "in") return mentionsIndia(l)
        val names = remoteCountries[code]?.names.orEmpty() + countryNames[code].orEmpty() + places.filter { it.country == code }.flatMap { it.names }
        return names.any { has(l, it) }
    }

    /** The first of [countries] a remote job is open to, "worldwide" for a job open anywhere, or null if it fits none. */
    fun remoteOpenTo(job: JobForMatching, countries: List<String>): String? {
        if (!isRemote(job)) return null
        val l = job.location.lowercase()
        if (globalWords.any { has(l, it) }) return "worldwide"
        return countries.firstOrNull { code ->
            inCountry(code, l) || remoteCountries[code]?.regions.orEmpty().any { has(l, it) }
        }
    }

    /** "India", "the United States", ...: how the feed names a remote-work country. */
    fun remoteCountryName(code: String): String = remoteCountries[code]?.display ?: code.uppercase()

    /** A remote job an India-based candidate can actually take. */
    fun remoteForIndia(job: JobForMatching): Boolean = remoteOpenTo(job, listOf("in")) != null
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
        val home = prefs.remoteFrom().first()
        val cityMatch = if (cities.isEmpty()) Locations.inCountry(home, job.location) else cities.any { Locations.cityMatches(it, job.location) }
        val remoteOk = acceptsRemote && Locations.remoteOpenTo(job, prefs.remoteFrom()) != null
        val locationOk = when (prefs.remoteMode) {
            RemoteMode.REMOTE -> remoteOk
            RemoteMode.ONSITE, RemoteMode.HYBRID -> cityMatch || (remoteOk && prefs.locations.any { it.equals("remote", true) })
            RemoteMode.ANY -> cityMatch || remoteOk
        }
        if (!locationOk) return FilterResult(false, "location")

        if (!titleMatchesRole(job.title, prefs, profile)) return FilterResult(false, "role")

        if (prefs.needsVisaSponsorship && Locations.isAbroad(job.location) &&
            Sponsorship.visa(job.title + "\n" + job.description) == Sponsorship.NOT_OFFERED
        ) {
            return FilterResult(false, "no visa sponsorship")
        }

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
    /** Tokenized text padded with spaces, for repeated phrase checks against one long JD. */
    fun normalizedText(text: String): String = " " + HardFilter.tokens(text).joinToString(" ") + " "

    fun normalizedPhrase(phrase: String): String = HardFilter.tokens(phrase).joinToString(" ")

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
            val openTo = Locations.remoteOpenTo(job, prefs.remoteFrom())
            if (openTo == "worldwide") add("Remote, open worldwide")
            else if (openTo == "in") add("Remote, open to India")
            else if (openTo != null) add("Remote, open to people in ${Locations.remoteCountryName(openTo)}")
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
