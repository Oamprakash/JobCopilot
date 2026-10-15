package com.applycopilot.core

/** Thrown for input the user must fix; the API turns it into a 400 with the listed problems. */
class ValidationException(val problems: List<String>) : RuntimeException(problems.joinToString("; "))

object Emails {
    private val pattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    /** Lower-cases and trims, then checks the shape. Returns null for an invalid address. */
    fun normalize(raw: String): String? {
        val email = raw.trim().lowercase()
        return if (email.length in 3..254 && pattern.matches(email)) email else null
    }
}

object ProfileRules {
    const val MAX_EXPERIENCE = 30
    const val MAX_BULLETS = 20
    const val MAX_BULLET_LENGTH = 600
    const val MAX_SKILLS = 100
    const val MAX_SKILL_LENGTH = 60
    const val MAX_EDUCATION = 15
    const val MAX_LINKS = 10

    private val datePattern = Regex("^\\d{4}(-(0[1-9]|1[0-2]))?$")

    /** Trims every string, drops blanks, de-duplicates skills case-insensitively and fixes link schemes. */
    fun normalize(p: Profile): Profile = p.copy(
        fullName = p.fullName.clean(),
        headline = p.headline.clean(),
        email = p.email?.let { Emails.normalize(it) ?: it.clean() }?.ifBlank { null },
        phone = p.phone?.clean()?.ifBlank { null },
        location = p.location?.clean()?.ifBlank { null },
        summary = p.summary?.trim()?.ifBlank { null },
        experience = p.experience.map { e ->
            e.copy(
                title = e.title.clean(),
                company = e.company.clean(),
                location = e.location?.clean()?.ifBlank { null },
                startDate = e.startDate?.trim()?.ifBlank { null },
                endDate = if (e.current) null else e.endDate?.trim()?.ifBlank { null },
                bullets = e.bullets.map { it.clean() }.filter { it.isNotEmpty() },
            )
        }.filter { it.title.isNotEmpty() || it.company.isNotEmpty() },
        education = p.education.map { ed ->
            ed.copy(
                institution = ed.institution.clean(),
                degree = ed.degree?.clean()?.ifBlank { null },
                field = ed.field?.clean()?.ifBlank { null },
            )
        }.filter { it.institution.isNotEmpty() || it.degree != null },
        skills = p.skills.map { it.clean() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
        links = p.links.map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it" }
            .distinct(),
    )

    /** Returns every problem found; an empty list means the profile can be saved. */
    fun validate(p: Profile): List<String> {
        val problems = mutableListOf<String>()
        if (p.fullName.isEmpty()) problems += "Full name is required"
        if (p.fullName.length > 120) problems += "Full name is too long"
        if (p.headline.length > 160) problems += "Headline is too long"
        if ((p.summary?.length ?: 0) > 2000) problems += "Summary must be at most 2000 characters"
        p.email?.let { if (Emails.normalize(it) == null) problems += "Email address is not valid" }
        p.totalExperienceYears?.let { if (it < 0 || it > 60) problems += "Total experience must be between 0 and 60 years" }

        if (p.experience.size > MAX_EXPERIENCE) problems += "At most $MAX_EXPERIENCE experience entries"
        p.experience.forEachIndexed { i, e ->
            val label = "Experience ${i + 1}"
            if (e.title.isEmpty()) problems += "$label: title is required"
            if (e.company.isEmpty()) problems += "$label: company is required"
            if (e.title.length > 160 || e.company.length > 160) problems += "$label: title or company is too long"
            e.startDate?.let { if (!datePattern.matches(it)) problems += "$label: start date must be YYYY-MM or YYYY" }
            e.endDate?.let { if (!datePattern.matches(it)) problems += "$label: end date must be YYYY-MM or YYYY" }
            if (e.startDate != null && e.endDate != null && datePattern.matches(e.startDate) &&
                datePattern.matches(e.endDate)
            ) {
                // Compare at the coarser precision so "2020-05" to "2020" is allowed.
                val n = minOf(e.startDate.length, e.endDate.length)
                if (e.endDate.take(n) < e.startDate.take(n)) problems += "$label: end date is before start date"
            }
            if (e.bullets.size > MAX_BULLETS) problems += "$label: at most $MAX_BULLETS bullets"
            if (e.bullets.any { it.length > MAX_BULLET_LENGTH }) problems += "$label: each bullet must be at most $MAX_BULLET_LENGTH characters"
        }

        if (p.education.size > MAX_EDUCATION) problems += "At most $MAX_EDUCATION education entries"
        p.education.forEachIndexed { i, ed ->
            ed.endYear?.let { if (it < 1950 || it > 2100) problems += "Education ${i + 1}: year looks wrong" }
        }

        if (p.skills.size > MAX_SKILLS) problems += "At most $MAX_SKILLS skills"
        if (p.skills.any { it.length > MAX_SKILL_LENGTH }) problems += "Each skill must be at most $MAX_SKILL_LENGTH characters"
        if (p.links.size > MAX_LINKS) problems += "At most $MAX_LINKS links"
        if (p.links.any { it.length > 300 }) problems += "Links must be at most 300 characters"
        return problems
    }

    fun normalizeAndValidate(p: Profile): Profile {
        val normalized = normalize(p)
        val problems = validate(normalized)
        if (problems.isNotEmpty()) throw ValidationException(problems)
        return normalized
    }
}

object PreferenceRules {
    fun normalize(p: Preferences): Preferences = p.copy(
        roles = p.roles.cleanList(),
        locations = p.locations.cleanList(),
        skipCompanies = p.skipCompanies.cleanList(),
        remoteCountries = p.remoteCountries.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct(),
    )

    fun validate(p: Preferences): List<String> {
        val problems = mutableListOf<String>()
        if (p.roles.size > 10) problems += "At most 10 target roles"
        if (p.roles.any { it.length > 80 }) problems += "Each role must be at most 80 characters"
        if (p.locations.size > 15) problems += "At most 15 locations"
        if (p.locations.any { it.length > 80 }) problems += "Each location must be at most 80 characters"
        if (p.skipCompanies.size > 50) problems += "At most 50 companies to skip"
        if (p.skipCompanies.any { it.length > 120 }) problems += "Each company name must be at most 120 characters"
        if (p.remoteCountries.size > 8) problems += "At most 8 countries for remote work"
        if (p.remoteCountries.any { !Regex("^[a-z]{2}$").matches(it) }) problems += "Remote-work countries must be two-letter country codes"
        p.minCtcLpa?.let { if (it < 0 || it > 1000) problems += "Minimum CTC must be between 0 and 1000 LPA" }
        p.experienceYears?.let { if (it < 0 || it > 60) problems += "Experience must be between 0 and 60 years" }
        p.noticePeriodDays?.let { if (it < 0 || it > 365) problems += "Notice period must be between 0 and 365 days" }
        return problems
    }

    fun normalizeAndValidate(p: Preferences): Preferences {
        val normalized = normalize(p)
        val problems = validate(normalized)
        if (problems.isNotEmpty()) throw ValidationException(problems)
        return normalized
    }
}

private fun String.clean(): String = trim().replace(Regex("\\s+"), " ")

private fun List<String>.cleanList(): List<String> =
    map { it.clean() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
