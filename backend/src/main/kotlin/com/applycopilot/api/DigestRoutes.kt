package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.Digests
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.jobs.UnsubscribeTokens
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

private suspend fun ApplicationCall.unsubscribe(deps: Deps) {
    val userId = request.queryParameters["u"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    val token = request.queryParameters["t"].orEmpty()
    if (userId == null || !UnsubscribeTokens.verify(deps.config.jwtSecret, userId, token)) {
        respondText(page(deps, "This unsubscribe link isn't valid", "Turn the daily email off under Preferences in the app instead."), ContentType.Text.Html, HttpStatusCode.BadRequest)
        return
    }
    deps.db.tx { c ->
        if (Digests.setEnabled(c, userId, false)) Events.record(c, userId, EventTypes.DIGEST_UNSUBSCRIBED)
    }
    respondText(page(deps, "You're unsubscribed", "You won't get the daily match email any more. You can turn it back on under Preferences."), ContentType.Text.Html)
}

private fun page(deps: Deps, title: String, text: String): String {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${esc(title)} · ${esc(deps.config.appName)}</title></head>
        <body style="font-family:system-ui,sans-serif;max-width:480px;margin:72px auto;padding:0 24px;color:#14213d;line-height:1.5">
        <h1 style="font-size:24px">${esc(title)}</h1><p>${esc(text)}</p>
        <p><a href="${esc(deps.config.webBaseUrl)}/profile" style="color:#1f4fe0">Open your preferences</a></p></body></html>""".trimIndent()
}

/** One-click unsubscribe for the daily match email: GET from the link, POST from mail apps (RFC 8058). */
fun Route.digestRoutes(deps: Deps) {
    route("/digest/unsubscribe") {
        get { call.unsubscribe(deps) }
        post { call.unsubscribe(deps) }
    }
}
