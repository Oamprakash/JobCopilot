package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.db.Feedback
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Serializable
data class FeedbackRequest(val kind: String, val message: String, val page: String? = null)

@Serializable
data class FeedbackDto(
    val id: String,
    val userId: String,
    val email: String,
    val kind: String,
    val message: String,
    val page: String?,
    val status: String,
    val createdAt: String,
)

@Serializable
data class FeedbackListResponse(val items: List<FeedbackDto>, val newCount: Int)

@Serializable
data class FeedbackStatusRequest(val status: String)

/** Only one of our own app paths is kept with feedback, e.g. "/apply/123" (no query, no other sites). */
internal fun cleanFeedbackPage(page: String?): String? =
    page?.trim()?.substringBefore('?')?.substringBefore('#')?.takeIf { it.matches(Regex("^/[A-Za-z0-9/_-]{0,120}$")) }

private const val DAILY_LIMIT = 20

/** Users send feedback; admins read it and mark it done. Mount inside authenticate("session"). */
fun Route.feedbackRoutes(deps: Deps) {
    post("/feedback") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val request = call.receive<FeedbackRequest>()
        val kind = request.kind.takeIf { it in Feedback.KINDS } ?: throw ApiException(HttpStatusCode.BadRequest, "Pick what kind of feedback this is")
        val message = request.message.trim()
        if (message.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "Write a few words first")
        if (message.length > 2000) throw ApiException(HttpStatusCode.BadRequest, "Keep it under 2,000 characters")
        deps.db.tx { c ->
            if (Feedback.countSince(c, userId, Instant.now().minus(Duration.ofDays(1))) >= DAILY_LIMIT) {
                throw ApiException(HttpStatusCode.TooManyRequests, "Thanks for all the feedback today. Try again tomorrow.")
            }
            Feedback.insert(c, userId, kind, message, cleanFeedbackPage(request.page))
            Events.record(c, userId, EventTypes.FEEDBACK_SENT, data = Events.data("kind" to kind))
        }
        call.respond(OkResponse())
    }

    route("/admin/feedback") {
        get {
            call.requireAdmin(deps)
            val status = call.request.queryParameters["status"]?.takeIf { it == "new" || it == "done" }
            val (rows, newCount) = deps.db.tx { c -> Feedback.list(c, status, 200) to Feedback.countNew(c) }
            call.respond(
                FeedbackListResponse(
                    rows.map { FeedbackDto(it.id.toString(), it.userId.toString(), it.email, it.kind, it.message, it.page, it.status, it.createdAt.toString()) },
                    newCount,
                ),
            )
        }

        put("/{id}") {
            call.requireSameOrigin(deps.config)
            call.requireAdmin(deps)
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: throw ApiException(HttpStatusCode.NotFound, "Feedback not found")
            val status = call.receive<FeedbackStatusRequest>().status.takeIf { it == "new" || it == "done" }
                ?: throw ApiException(HttpStatusCode.BadRequest, "status must be new or done")
            if (!deps.db.tx { c -> Feedback.setStatus(c, id, status) }) throw ApiException(HttpStatusCode.NotFound, "Feedback not found")
            call.respond(OkResponse())
        }
    }
}
