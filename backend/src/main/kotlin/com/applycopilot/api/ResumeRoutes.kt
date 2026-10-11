package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.ResumeFile
import com.applycopilot.db.ResumeFiles
import com.applycopilot.resume.ResumeFormat
import com.applycopilot.resume.TextExtractor
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import java.io.IOException
import java.util.UUID

/** Headroom over the 5 MB resume limit for multipart framing; anything larger is cut off by Ktor. */
private const val MULTIPART_LIMIT = 6L * 1024 * 1024

/**
 * POST /resume (multipart, field "file"): stores the file, extracts its text and returns a parsed,
 * grounded draft profile. Nothing is saved to the profile until the user confirms it with PUT /profile.
 */
fun Route.resumeRoutes(deps: Deps) {
    post("/resume") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()

        var originalName: String? = null
        var bytes: ByteArray? = null
        try {
            call.receiveMultipart(formFieldLimit = MULTIPART_LIMIT).forEachPart { part ->
                if (part is PartData.FileItem && part.name == "file" && bytes == null) {
                    originalName = part.originalFileName
                    // Read one byte past the limit so an oversized file is rejected rather than truncated.
                    bytes = part.provider().readRemaining(TextExtractor.MAX_BYTES.toLong() + 1).readByteArray()
                }
                part.dispose()
            }
        } catch (e: IOException) {
            // Ktor aborts a part bigger than formFieldLimit with an IOException.
            throw ApiException(HttpStatusCode.PayloadTooLarge, "Resumes must be 5 MB or smaller")
        }
        val data = bytes ?: throw ApiException(HttpStatusCode.BadRequest, "Attach your resume as the \"file\" field")

        val extracted = withContext(Dispatchers.IO) { TextExtractor.extract(data) }
        val file = ResumeFile(
            id = UUID.randomUUID(),
            userId = userId,
            storageKey = "",
            fileName = safeFileName(originalName, extracted.format),
            contentType = extracted.format.contentType,
            sizeBytes = data.size,
        ).let { it.copy(storageKey = "resumes/$userId/${it.id}.${extracted.format.extension}") }

        withContext(Dispatchers.IO) { deps.storage.put(file.storageKey, data) }
        try {
            deps.db.tx { c ->
                requireUser(c, userId)
                ResumeFiles.insert(c, file, extracted.text)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.IO) { runCatching { deps.storage.delete(file.storageKey) } }
            throw e
        }

        val outcome = deps.parsing.parse(extracted.text)
        call.respond(
            ResumeUploadResponse(
                resumeFile = ResumeFileDto(file.id.toString(), file.fileName),
                parser = outcome.parser,
                profile = outcome.profile,
                removed = outcome.removed,
            ),
        )
    }
}

/** Keeps the base name, replaces anything unusual, and makes the extension match the real format. */
internal fun safeFileName(original: String?, format: ResumeFormat): String {
    val base = original.orEmpty().substringAfterLast('/').substringAfterLast('\\')
        .substringBeforeLast('.')
        .replace(Regex("[^A-Za-z0-9 ._()-]"), "_")
        .trim()
        .take(100)
        .ifEmpty { "resume" }
    return "$base.${format.extension}"
}
