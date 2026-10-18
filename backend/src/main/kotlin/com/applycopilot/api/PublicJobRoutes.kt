package com.applycopilot.api

import com.applycopilot.Deps
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** Public, read-only job pages: GET /public/jobs (every page with enough jobs) and GET /public/jobs/{slug}. No sign-in. */
fun Route.publicJobRoutes(deps: Deps) {
    get("/public/jobs") { call.respond(deps.publicJobs.index()) }

    get("/public/jobs/{slug}") {
        val slug = call.parameters["slug"].orEmpty().lowercase()
        if (!Regex("^[a-z0-9-]{3,80}$").matches(slug)) throw ApiException(HttpStatusCode.NotFound, "No such page")
        call.respond(deps.publicJobs.page(slug) ?: throw ApiException(HttpStatusCode.NotFound, "No such page"))
    }
}
