package com.applycopilot.api

import com.applycopilot.core.Profile
import kotlinx.serialization.Serializable

@Serializable
data class MagicLinkRequest(val email: String)

/** [devLink] is only filled outside prod, so local testing works without an email provider. */
@Serializable
data class MagicLinkResponse(val ok: Boolean, val devLink: String? = null)

@Serializable
data class VerifyRequest(val token: String)

@Serializable
data class MeResponse(val id: String, val email: String, val plan: String, val hasProfile: Boolean, val isAdmin: Boolean = false)

@Serializable
data class OkResponse(val ok: Boolean = true)

@Serializable
data class ResumeFileDto(val id: String, val fileName: String)

@Serializable
data class ResumeUploadResponse(
    val resumeFile: ResumeFileDto,
    val parser: String,
    val profile: Profile,
    val removed: List<String>,
)

@Serializable
data class SaveProfileRequest(val profile: Profile, val resumeFileId: String? = null)

@Serializable
data class ProfileResponse(val version: Int, val profile: Profile, val resumeFile: ResumeFileDto?, val updatedAt: String)

@Serializable
data class HealthResponse(val status: String)

@Serializable
data class RootResponse(val message: String, val web: String)
