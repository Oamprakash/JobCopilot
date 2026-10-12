package com.applycopilot.matching

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import kotlinx.serialization.Serializable

/**
 * A value to offer while the user types a preference, with how many open jobs it would match.
 * [aliases] are other names that should find it while typing (Bangalore → Bengaluru).
 */
@Serializable
data class AutocompleteOption(val value: String, val count: Int, val aliases: List<String> = emptyList())

@Serializable
data class AutocompleteOptions(val locations: List<AutocompleteOption>, val roles: List<AutocompleteOption>)

/** The parts of an open job that location and role suggestions need. */
data class OpenJob(val title: String, val location: String)

/**
 * Typeahead options for the Locations and Target roles inputs. Counts use the same rules as [HardFilter]:
 * a city counts the jobs whose location names it (any alias), a role counts the jobs whose title would pass
 * the role filter if it were the only target role. The web app filters these by what's typed.
 */
object Autocomplete {
    val roles: List<String> = listOf(
        "Android Engineer", "Senior Android Engineer", "Lead Android Engineer", "Android Developer",
        "iOS Engineer", "Senior iOS Engineer", "Mobile Engineer", "Flutter Developer", "React Native Developer",
        "Software Engineer", "Senior Software Engineer", "Staff Software Engineer", "Principal Engineer",
        "Backend Engineer", "Senior Backend Engineer", "Frontend Engineer", "Senior Frontend Engineer",
        "Full Stack Engineer", "Java Developer", "Python Developer", "Kotlin Developer", "Golang Developer",
        "Engineering Manager", "Technical Lead", "Solutions Architect",
        "Platform Engineer", "DevOps Engineer", "Site Reliability Engineer", "Cloud Engineer", "Security Engineer",
        "Data Engineer", "Data Scientist", "Data Analyst", "Machine Learning Engineer", "AI Engineer",
        "QA Engineer", "SDET", "Embedded Engineer",
        "Product Manager", "Technical Program Manager", "Product Designer", "UX Designer",
    )

    fun options(jobs: List<OpenJob>): AutocompleteOptions {
        val citiesPerJob = jobs.map { Locations.citiesIn(it.location).toSet() }
        val locations = Locations.cities
            .map { city -> AutocompleteOption(city, citiesPerJob.count { city in it }, Locations.aliasesOf(city)) }
            .sortedByDescending { it.count }

        val profile = Profile()
        val titles = jobs.groupingBy { it.title }.eachCount()
        val roleOptions = roles
            .map { role ->
                val prefs = Preferences(roles = listOf(role))
                AutocompleteOption(role, titles.entries.sumOf { (title, n) -> if (HardFilter.titleMatchesRole(title, prefs, profile)) n else 0 })
            }
            .sortedByDescending { it.count }

        return AutocompleteOptions(locations, roleOptions)
    }
}
