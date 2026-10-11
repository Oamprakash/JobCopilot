package com.applycopilot.core

import java.time.YearMonth

/**
 * Rule-based resume parser. Used when no Claude API key is configured (local dev, tests)
 * and as a fallback when the API call fails. It only copies text that is in the resume;
 * the user confirms or corrects everything on the review screen.
 */
class HeuristicResumeParser(private val today: () -> YearMonth = { YearMonth.now() }) {

    fun parse(text: String): Profile {
        val lines = text.lines().map { it.replace(' ', ' ').trim() }
        val sections = splitSections(lines)
        val header = sections[Section.HEADER].orEmpty()

        val experience = parseExperience(sections[Section.EXPERIENCE].orEmpty())
        val skills = (parseSkillSection(sections[Section.SKILLS].orEmpty()) + dictionarySkills(text))
            .distinctBy { it.lowercase() }
            .take(ProfileRules.MAX_SKILLS)

        return Profile(
            fullName = findName(header),
            headline = experience.firstOrNull()?.title?.takeIf { it.isNotEmpty() } ?: findHeadline(header),
            email = emailRegex.find(text)?.value?.let { Emails.normalize(it) },
            phone = phoneRegex.find(text)?.value?.trim(),
            location = findLocation(header),
            totalExperienceYears = statedYears(text) ?: computedYears(experience),
            summary = sections[Section.SUMMARY]?.filter { it.isNotEmpty() }?.joinToString(" ")?.take(2000)?.ifBlank { null },
            experience = experience,
            education = parseEducation(sections[Section.EDUCATION].orEmpty()),
            skills = skills,
            links = linkRegex.findAll(text).map { it.value.trimEnd('.', ',', ')', ';') }.distinct().take(ProfileRules.MAX_LINKS).toList(),
        )
    }

    // ---- Sections -------------------------------------------------------------------------

    private enum class Section { HEADER, SUMMARY, EXPERIENCE, EDUCATION, SKILLS, OTHER }

    private val headings: List<Pair<Regex, Section>> = listOf(
        "(professional |career )?summary|profile|objective|about me" to Section.SUMMARY,
        "(work |professional )?experience|employment( history)?|work history|career history" to Section.EXPERIENCE,
        "education|academics?|academic (background|qualifications?)|qualifications?" to Section.EDUCATION,
        "(technical |core |key )?skills|technologies|tech stack|tools( and technologies)?|skill set" to Section.SKILLS,
        "projects?|certifications?|achievements?|awards?|publications?|languages|interests|hobbies|personal details|declaration|references" to Section.OTHER,
    ).map { (pattern, section) -> Regex("^(?:$pattern)\\s*:?\\s*(.*)$", RegexOption.IGNORE_CASE) to section }

    private fun splitSections(lines: List<String>): Map<Section, List<String>> {
        val out = linkedMapOf<Section, MutableList<String>>()
        var current = Section.HEADER
        for (line in lines) {
            val heading = if (line.length <= 60) headings.firstNotNullOfOrNull { (regex, section) ->
                regex.matchEntire(line)?.let { section to it.groupValues.last().trim() }
            } else null
            // "Skills: Kotlin, Java" is a heading with content after the colon; a long inline value is still content.
            if (heading != null && (heading.second.isEmpty() || line.contains(':'))) {
                current = heading.first
                if (heading.second.isNotEmpty()) out.getOrPut(current) { mutableListOf() } += heading.second
                continue
            }
            out.getOrPut(current) { mutableListOf() } += line
        }
        return out
    }

    // ---- Header ---------------------------------------------------------------------------

    private fun findName(header: List<String>): String =
        header.filter { it.isNotEmpty() }.take(5).firstOrNull { line ->
            val words = line.split(' ').filter { it.isNotEmpty() }
            words.size in 1..4 && line.length <= 60 && namePattern.matches(line) && words.any { it.length > 1 }
        } ?: ""

    private fun findHeadline(header: List<String>): String {
        val candidates = header.filter { it.isNotEmpty() }.take(6)
        val nameIndex = candidates.indexOfFirst { it == findName(header) }
        return candidates.drop(nameIndex + 1).firstOrNull { line ->
            line.length in 3..100 && !line.any { it.isDigit() } && !line.contains('@') && !linkRegex.containsMatchIn(line)
        } ?: ""
    }

    private fun findLocation(header: List<String>): String? {
        val joined = header.take(8).joinToString(" | ")
        return cities.firstOrNull { Regex("\\b${Regex.escape(it)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(joined) }
    }

    // ---- Experience -----------------------------------------------------------------------

    private fun parseExperience(lines: List<String>): List<Experience> {
        val entries = mutableListOf<Experience>()
        var pending: Experience? = null
        // Plain (non-bullet) lines seen since the last bullet or dated line: candidates for the next header.
        val plainRun = mutableListOf<String>()

        fun flush() {
            pending?.let { if (it.title.isNotEmpty() || it.company.isNotEmpty()) entries += it }
            pending = null
        }

        for (line in lines) {
            if (line.isEmpty()) continue
            val range = rangeRegex.find(line)
            if (range != null) {
                val before = line.substring(0, range.range.first).trim().trimEnd('|', '-', '–', '—', ',', '(').trim()
                val (title, company) = if (before.isNotEmpty()) {
                    splitTitleCompany(before)
                } else {
                    // Dates on their own line: the header is the one or two plain lines just above,
                    // which were provisionally attached to the previous entry and must come back off it.
                    val header = plainRun.takeLast(2)
                    pending = pending?.let { p -> p.copy(bullets = p.bullets.dropLast(header.size.coerceAtMost(p.bullets.size))) }
                    when (header.size) {
                        2 -> header[0] to header[1]
                        1 -> splitTitleCompany(header[0])
                        else -> "" to ""
                    }
                }
                flush()
                plainRun.clear()
                val end = range.groupValues[2]
                val current = presentRegex.matches(end.trim())
                pending = Experience(
                    title = title.take(160),
                    company = company.take(160),
                    startDate = normalizeDate(range.groupValues[1]),
                    endDate = if (current) null else normalizeDate(end),
                    current = current,
                )
                continue
            }

            val bullet = isBullet(line)
            if (bullet) plainRun.clear() else plainRun += line
            val p = pending ?: continue

            when {
                !bullet && p.company.isEmpty() && p.bullets.isEmpty() ->
                    // A plain line straight after the dated header is usually the company.
                    pending = p.copy(company = line.take(160))
                !bullet && p.bullets.isNotEmpty() && line.first().isLowerCase() ->
                    // A wrapped bullet continues on the next line.
                    pending = p.copy(bullets = p.bullets.dropLast(1) + (p.bullets.last() + " " + line).take(ProfileRules.MAX_BULLET_LENGTH))
                p.bullets.size < ProfileRules.MAX_BULLETS ->
                    pending = p.copy(bullets = p.bullets + stripBullet(line).take(ProfileRules.MAX_BULLET_LENGTH))
            }
        }
        flush()
        return entries.take(ProfileRules.MAX_EXPERIENCE)
    }

    private fun splitTitleCompany(text: String): Pair<String, String> {
        val t = text.trim()
        if (t.isEmpty()) return "" to ""
        Regex("\\s+at\\s+|\\s+@\\s+", RegexOption.IGNORE_CASE).find(t)?.let {
            return t.substring(0, it.range.first).trim() to t.substring(it.range.last + 1).trim()
        }
        for (separator in listOf(" | ", " – ", " — ", " - ", ", ")) {
            val i = t.indexOf(separator)
            if (i > 0) return t.substring(0, i).trim() to t.substring(i + separator.length).trim()
        }
        return t to ""
    }

    private fun computedYears(experience: List<Experience>): Double? {
        val starts = experience.mapNotNull { it.startDate?.let(::toYearMonth) }
        if (starts.isEmpty()) return null
        val ends = experience.map { if (it.current) today() else it.endDate?.let(::toYearMonth) ?: today() }
        val months = java.time.temporal.ChronoUnit.MONTHS.between(starts.min(), ends.max())
        return if (months <= 0) null else Math.round(months / 12.0 * 10) / 10.0
    }

    private fun statedYears(text: String): Double? =
        yearsRegex.findAll(text).mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it in 0.5..50.0 }.maxOrNull()

    // ---- Education ------------------------------------------------------------------------

    private fun parseEducation(lines: List<String>): List<Education> {
        val out = mutableListOf<Education>()
        val content = lines.filter { it.isNotEmpty() }
        var i = 0
        while (i < content.size && out.size < ProfileRules.MAX_EDUCATION) {
            val line = stripBullet(content[i])
            val degree = degreeRegex.find(line)
            if (degree == null) { i++; continue }
            val neighbours = listOfNotNull(line, content.getOrNull(i + 1)?.let(::stripBullet))
            val institution = neighbours.firstOrNull { institutionRegex.containsMatchIn(it) }
                ?.let { candidate ->
                    // When degree and institution share a line, keep only the institution part.
                    candidate.split(Regex("\\s[|,–—-]\\s|,\\s")).firstOrNull { institutionRegex.containsMatchIn(it) } ?: candidate
                }?.replace(yearInTextRegex, "")?.trim()?.trimEnd(',', '|', '-', '(')?.trim()
            val year = neighbours.flatMap { n -> yearInTextRegex.findAll(n).map { it.value.toInt() }.toList() }.maxOrNull()
            out += Education(
                institution = institution.orEmpty(),
                degree = degree.value.trim(),
                field = Regex("\\b(?:in|of)\\s+([A-Za-z &]+)").find(line.substring(degree.range.last + 1))?.groupValues?.get(1)?.trim()?.ifBlank { null },
                endYear = year,
            )
            i += if (institution != null && institutionRegex.containsMatchIn(content.getOrNull(i + 1).orEmpty()) && !institutionRegex.containsMatchIn(line)) 2 else 1
        }
        return out
    }

    // ---- Skills ---------------------------------------------------------------------------

    private fun parseSkillSection(lines: List<String>): List<String> =
        lines.filter { it.isNotEmpty() }
            .map { stripBullet(it) }
            // "Languages: Kotlin, Java" → keep only what follows the label.
            .map { line -> line.substringAfter(':', line) }
            .flatMap { it.split(',', '|', '•', ';', '·') }
            .map { it.trim().trimEnd('.') }
            .filter { it.length in 1..ProfileRules.MAX_SKILL_LENGTH && it.split(' ').size <= 5 }

    private fun dictionarySkills(text: String): List<String> =
        skillDictionary.filter { skill ->
            // Single words ("Room", "Spring", "REST") must match case to avoid hits in ordinary prose.
            val options = if (skill.contains(' ')) setOf(RegexOption.IGNORE_CASE) else emptySet()
            Regex("(?<![A-Za-z0-9+#.])${Regex.escape(skill)}(?![A-Za-z0-9+#])", options).containsMatchIn(text)
        }

    // ---- Helpers --------------------------------------------------------------------------

    private fun isBullet(line: String) = line.isNotEmpty() && line.first() in bulletChars
    private fun stripBullet(line: String) = line.trimStart(*bulletChars.toCharArray()).trim()

    companion object {
        private const val MONTH = "(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?"
        private const val DATE = "(?:$MONTH\\s*['’]?\\s*\\d{4}|\\d{1,2}/\\d{4}|\\d{4}-\\d{2}|\\d{4})"
        private const val PRESENT = "present|current|now|till date|to date|ongoing"

        private val rangeRegex = Regex("($DATE)\\s*(?:-|–|—|to|until)\\s*($DATE|$PRESENT)", RegexOption.IGNORE_CASE)
        private val presentRegex = Regex("^(?:$PRESENT)$", RegexOption.IGNORE_CASE)
        private val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val phoneRegex = Regex("(?:\\+?91[\\s-]?)?(?<!\\d)[6-9]\\d{4}[\\s-]?\\d{5}(?!\\d)|\\+\\d{1,3}[\\s-]?\\(?\\d{2,4}\\)?[\\s-]?\\d{3,4}[\\s-]?\\d{3,4}")
        private val linkRegex = Regex("(?:https?://|www\\.)\\S+|(?:linkedin\\.com/in|github\\.com)/[A-Za-z0-9_./-]+", RegexOption.IGNORE_CASE)
        private val yearsRegex = Regex("(\\d{1,2}(?:\\.\\d)?)\\s*\\+?\\s*(?:years|yrs)", RegexOption.IGNORE_CASE)
        private val namePattern = Regex("^[A-Za-z][A-Za-z.'-]*(?: [A-Za-z][A-Za-z.'-]*){0,3}$")
        private val yearInTextRegex = Regex("\\b(19[5-9]\\d|20\\d{2})\\b")
        private val degreeRegex = Regex(
            "\\b(?:B\\.?\\s?E\\.?|B\\.?\\s?Tech|M\\.?\\s?Tech|M\\.?\\s?E\\.?|MCA|BCA|B\\.?\\s?Sc|M\\.?\\s?Sc|MBA|BBA|B\\.?\\s?Com|M\\.?\\s?Com|Ph\\.?\\s?D|Diploma|Bachelor(?:'s)?(?: of [A-Za-z]+)?|Master(?:'s)?(?: of [A-Za-z]+)?|HSC|SSC|Class XII|Class X)(?![A-Za-z])",
        )
        private val institutionRegex = Regex("\\b(?:University|College|Institute|School|Academy|IIT|NIT|IIIT|BITS|Vidyalaya)\\b", RegexOption.IGNORE_CASE)
        private val bulletChars = listOf('•', '-', '*', '▪', '●', '◦', '–', '·', '►', '✓')

        private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        /** "Jan 2020", "01/2020", "2020-01" → "2020-01"; "2020" → "2020". */
        fun normalizeDate(raw: String): String? {
            val s = raw.trim().lowercase().replace("’", "").replace("'", "")
            Regex("^(\\d{4})-(\\d{2})$").matchEntire(s)?.let { return "${it.groupValues[1]}-${it.groupValues[2]}" }
            Regex("^(\\d{1,2})/(\\d{4})$").matchEntire(s)?.let {
                val m = it.groupValues[1].toInt()
                return if (m in 1..12) "${it.groupValues[2]}-${m.toString().padStart(2, '0')}" else it.groupValues[2]
            }
            Regex("^([a-z]+)\\.?\\s*(\\d{4})$").matchEntire(s)?.let {
                val m = months.indexOf(it.groupValues[1].take(3))
                return if (m >= 0) "${it.groupValues[2]}-${(m + 1).toString().padStart(2, '0')}" else it.groupValues[2]
            }
            Regex("^(\\d{4})$").matchEntire(s)?.let { return it.groupValues[1] }
            return null
        }

        fun toYearMonth(date: String): YearMonth? = runCatching {
            if (date.length == 4) YearMonth.of(date.toInt(), 1) else YearMonth.parse(date)
        }.getOrNull()

        private val cities = listOf(
            "Chennai", "Bengaluru", "Bangalore", "Hyderabad", "Mumbai", "Pune", "Delhi", "New Delhi", "Gurugram",
            "Gurgaon", "Noida", "Kolkata", "Ahmedabad", "Coimbatore", "Kochi", "Trivandrum", "Thiruvananthapuram",
            "Indore", "Jaipur", "Chandigarh", "Mysuru", "Madurai", "Vizag", "Visakhapatnam", "Nagpur", "Bhubaneswar",
        )

        /** Common tech skills, matched as whole words; the display form is what gets stored. */
        val skillDictionary = listOf(
            "Kotlin", "Java", "Swift", "Objective-C", "Dart", "Flutter", "React Native", "JavaScript", "TypeScript",
            "Python", "Golang", "Rust", "C++", "C#", "Scala", "Ruby", "PHP", "SQL", "Bash",
            "Android", "Android SDK", "Jetpack Compose", "Coroutines", "Kotlin Flow", "RxJava", "Dagger",
            "Hilt", "Koin", "Retrofit", "OkHttp", "Room", "SQLite", "Realm", "WorkManager", "Navigation Component",
            "MVVM", "MVI", "MVP", "Clean Architecture", "Kotlin Multiplatform", "KMP", "Gradle", "ProGuard", "R8",
            "Firebase", "Crashlytics", "AdMob", "Google Play", "Play Console", "Espresso", "JUnit", "Mockito",
            "MockK", "Robolectric", "iOS", "SwiftUI", "Xcode", "Android Studio",
            "React", "Next.js", "Angular", "Vue", "Node.js", "Spring", "Spring Boot", "Ktor", "Django",
            "Flask", "FastAPI", ".NET", "GraphQL", "REST", "gRPC", "Microservices",
            "PostgreSQL", "MySQL", "MongoDB", "Redis", "Elasticsearch", "Kafka", "RabbitMQ",
            "AWS", "GCP", "Azure", "Docker", "Kubernetes", "Terraform", "Jenkins", "GitHub Actions", "CI/CD",
            "Git", "Linux", "Agile", "Scrum", "JIRA", "Figma", "Machine Learning", "TensorFlow", "PyTorch",
            "Team leadership", "Mentoring", "Code review", "System design",
        )
    }
}
