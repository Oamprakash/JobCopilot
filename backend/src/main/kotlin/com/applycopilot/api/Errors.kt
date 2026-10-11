package com.applycopilot.api

import com.applycopilot.core.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

class ApiException(val status: HttpStatusCode, override val message: String) : RuntimeException(message)

@Serializable
data class ErrorResponse(val error: String, val problems: List<String> = emptyList())

fun Application.installErrorHandling() {
    val log = LoggerFactory.getLogger("ErrorHandling")
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ErrorResponse(e.message)) }
        exception<ValidationException> { call, e ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Please fix the highlighted fields", e.problems))
        }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request")) }
        exception<SerializationException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request")) }
        exception<Throwable> { call, e ->
            log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Something went wrong. Please try again."))
        }
    }
}
