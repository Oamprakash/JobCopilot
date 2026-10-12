package com.applycopilot.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The master profile: the single source of truth that every tailored resume is built from.
 * Dates are "YYYY-MM" or "YYYY". An experience with [Experience.current] = true has no end date.
 */
@Serializable
data class Profile(
    val fullName: String = "",
    val headline: String = "",
    val email: String? = null,
    val phone: String? = null,
    val location: String? = null,
    val totalExperienceYears: Double? = null,
    val summary: String? = null,
    val experience: List<Experience> = emptyList(),
    val education: List<Education> = emptyList(),
    val skills: List<String> = emptyList(),
    val links: List<String> = emptyList(),
)

@Serializable
data class Experience(
    val title: String = "",
    val company: String = "",
    val location: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val current: Boolean = false,
    val bullets: List<String> = emptyList(),
)

@Serializable
data class Education(
    val institution: String = "",
    val degree: String? = null,
    val field: String? = null,
    val endYear: Int? = null,
)

@Serializable
enum class RemoteMode {
    @SerialName("onsite") ONSITE,
    @SerialName("hybrid") HYBRID,
    @SerialName("remote") REMOTE,
    @SerialName("any") ANY;

    val dbValue: String get() = name.lowercase()

    companion object {
        fun fromDb(value: String): RemoteMode = entries.firstOrNull { it.dbValue == value } ?: ANY
    }
}

@Serializable
data class Preferences(
    val roles: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val remoteMode: RemoteMode = RemoteMode.ANY,
    val minCtcLpa: Double? = null,
    val experienceYears: Double? = null,
    val noticePeriodDays: Int? = null,
    val skipCompanies: List<String> = emptyList(),
    /** For jobs outside India: hide the ones whose description says there's no visa sponsorship. */
    val needsVisaSponsorship: Boolean = false,
)
