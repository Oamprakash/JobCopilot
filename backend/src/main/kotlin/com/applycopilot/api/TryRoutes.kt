package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.resume.TextExtractor
import com.applycopilot.tailoring.FitCheckResult
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import java.io.IOException

private const val TRY_MULTIPART_LIMIT = 6L * 1024 * 1024
private const val MIN_DESCRIPTION = 200
private const val MAX_DESCRIPTION = 30_000

/** The visitor's address: the first X-Forwarded-For hop (Render, then the web app, put it there), else the socket's. */
internal fun ApplicationCall.clientIp(): String? =
    request.headers["X-Forwarded-For"]?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null } ?: request.origin.remoteHost

/**
 * POST /try/fit-check (multipart: "file" = resume, "description" = the job's text, optional "title"). No sign-in.
 * Returns how the resume fits the job and a few of its lines tailored for it. Nothing is stored.
 */
fun Route.tryRoutes(deps: Deps) {
    post("/try/fit-check") {
        call.requireSameOrigin(deps.config)
        if (!deps.fitCheck.allow(call.clientIp())) {
            throw ApiException(HttpStatusCode.TooManyRequests, "You've tried a few jobs already. Sign in to keep going, or try again in an hour.")
        }
        var bytes: ByteArray? = null
        var title: String? = null
        var description: String? = null
        try {
            call.receiveMultipart(formFieldLimit = TRY_MULTIPART_LIMIT).forEachPart { part ->
                when {
                    part is PartData.FileItem && part.name == "file" && bytes == null ->
                        bytes = part.provider().readRemaining(TextExtractor.MAX_BYTES.toLong() + 1).readByteArray()
                    part is PartData.FormItem && part.name == "title" -> title = part.value
                    part is PartData.FormItem && part.name == "description" -> description = part.value
                }
                part.dispose()
            }
        } catch (e: IOException) {
            throw ApiException(HttpStatusCode.PayloadTooLarge, "Resumes must be 5 MB or smaller")
        }
        val data = bytes ?: throw ApiException(HttpStatusCode.BadRequest, "Add your resume (PDF or Word)")
        val jd = description?.trim()?.replace(Regex("\r\n?"), "\n").orEmpty()
        if (jd.length < MIN_DESCRIPTION) {
            throw ApiException(HttpStatusCode.BadRequest, "Paste the whole job description: what the role does and what it asks for.")
        }
        val text = withContext(Dispatchers.IO) { TextExtractor.extract(data) }.text
        val result: FitCheckResult = deps.fitCheck.check(text, title, jd.take(MAX_DESCRIPTION))
        call.respond(result)
    }
}
