package com.applycopilot.extension

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import kotlinx.serialization.Serializable

/** What the browser extension may type into an application form, all taken from the user's own profile. */
@Serializable
data class AutofillProfile(
    val fullName: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val location: String?,
    val linkedin: String?,
    val github: String?,
    val website: String?,
    val currentTitle: String?,
    val currentCompany: String?,
    val totalExperienceYears: Double?,
    /** The profile's skills, in the order the profile lists them (an application's tailored order replaces this). */
    val skills: List<String> = emptyList(),
    /** Only from the user's own preferences; never guessed. */
    val noticePeriodDays: Int? = null,
)

object Autofill {
    fun profileFields(profile: Profile, accountEmail: String, prefs: Preferences? = null): AutofillProfile {
        val name = profile.fullName.trim().replace(Regex("\\s+"), " ")
        val parts = name.split(' ').filter { it.isNotEmpty() }
        val links = profile.links.map { it.trim() }.filter { it.isNotEmpty() }
        fun link(vararg hosts: String) = links.firstOrNull { l -> hosts.any { l.contains(it, ignoreCase = true) } }?.let(::withScheme)
        val linkedin = link("linkedin.com")
        val github = link("github.com")
        val website = links.map(::withScheme).firstOrNull { it != linkedin && it != github }
        val current = profile.experience.firstOrNull { it.current } ?: profile.experience.firstOrNull()
        return AutofillProfile(
            fullName = name,
            firstName = parts.firstOrNull().orEmpty(),
            lastName = if (parts.size > 1) parts.drop(1).joinToString(" ") else "",
            email = profile.email?.takeIf { it.isNotBlank() } ?: accountEmail,
            phone = profile.phone?.takeIf { it.isNotBlank() },
            location = profile.location?.takeIf { it.isNotBlank() },
            linkedin = linkedin,
            github = github,
            website = website,
            currentTitle = current?.title?.takeIf { it.isNotBlank() },
            currentCompany = current?.company?.takeIf { it.isNotBlank() },
            totalExperienceYears = profile.totalExperienceYears,
            skills = profile.skills.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() },
            noticePeriodDays = prefs?.noticePeriodDays,
        )
    }

    private fun withScheme(link: String) = if (link.startsWith("http://") || link.startsWith("https://")) link else "https://$link"
}

/**
 * Whether a page the user is on is the application form for a job we know. ATS application pages live under
 * the job's own URL (Greenhouse /jobs/123 and its #app form, Lever /co/id → /co/id/apply, Ashby /co/id →
 * /co/id/application), so a page matches when the job's normalized URL is a path prefix of the page's.
 */
object JobUrls {
    fun normalize(url: String): String? {
        val trimmed = url.trim()
        val m = Regex("^(?:https?://)?([^/?#]+)([^?#]*)", RegexOption.IGNORE_CASE).find(trimmed) ?: return null
        val host = m.groupValues[1].lowercase().removePrefix("www.").substringBefore(':')
        if (host.isEmpty() || !host.contains('.')) return null
        // Greenhouse serves the same board on boards.greenhouse.io and job-boards.greenhouse.io.
        val canonicalHost = if (host == "job-boards.greenhouse.io") "boards.greenhouse.io" else host
        val path = m.groupValues[2].trimEnd('/').lowercase()
        // Greenhouse's embedded form (on a company's own careers page) names the job in its query string.
        if (canonicalHost == "boards.greenhouse.io" && path == "/embed/job_app") {
            val query = trimmed.substringAfter('?', "").substringBefore('#')
            val params = query.split('&').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            val board = params["for"]
            val token = params["token"]
            if (board != null && token != null) return "boards.greenhouse.io/${board.lowercase()}/jobs/$token"
        }
        return canonicalHost + path
    }

    /** The page is the job's own page or a page under it (its apply form). */
    fun sameJob(jobUrl: String, pageUrl: String): Boolean {
        val job = normalize(jobUrl) ?: return false
        val page = normalize(pageUrl) ?: return false
        return page == job || page.startsWith("$job/")
    }
}
