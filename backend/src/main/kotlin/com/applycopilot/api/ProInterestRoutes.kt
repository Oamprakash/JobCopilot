package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.db.ProInterest
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.serialization.Serializable

@Serializable
data class ProInterestRequest(val choice: String, val note: String? = null)

/** The user's own answer, or nulls when they haven't answered. */
@Serializable
data class ProInterestDto(val choice: String?, val note: String?)

@Serializable
data class ProInterestNoteDto(val email: String, val choice: String, val note: String, val at: String)

/** For admins: answers per choice (all time), how many hit the free limit lately, and the latest notes. */
@Serializable
data class ProInterestSummaryDto(val windowDays: Int, val limitReachedUsers: Int, val choices: Map<String, Int>, val notes: List<ProInterestNoteDto>)

/**
 * "What would you pay?", asked when a free user reaches the monthly tailoring limit. Nothing is sold or charged:
 * the answers tell us whether a paid plan is wanted and at what price. Mount inside authenticate("session").
 */
fun Route.proInterestRoutes(deps: Deps) {
    get("/pro-interest") {
        val answer = deps.db.tx { c -> ProInterest.find(c, call.userId()) }
        call.respond(ProInterestDto(answer?.first, answer?.second))
    }

    put("/pro-interest") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val request = call.receive<ProInterestRequest>()
        if (request.choice !in ProInterest.CHOICES) throw ApiException(HttpStatusCode.BadRequest, "Pick one of the options")
        val note = request.note?.trim()?.take(500)?.ifEmpty { null }
        deps.db.tx { c ->
            ProInterest.upsert(c, userId, request.choice, note)
            Events.record(c, userId, EventTypes.PRO_INTEREST, data = Events.data("choice" to request.choice))
        }
        call.respond(ProInterestDto(request.choice, note))
    }

    get("/admin/pro-interest") {
        call.requireAdmin(deps)
        val days = (call.request.queryParameters["days"]?.toIntOrNull() ?: 30).coerceIn(1, 90)
        val summary = deps.db.tx { c ->
            ProInterestSummaryDto(
                windowDays = days,
                limitReachedUsers = ProInterest.limitReachedUsers(c, days),
                choices = ProInterest.counts(c),
                notes = ProInterest.recentNotes(c).map { ProInterestNoteDto(it.email, it.choice, it.note.orEmpty(), it.updatedAt.toString()) },
            )
        }
        call.respond(summary)
    }
}
