package com.applycopilot.api

import com.applycopilot.Deps
import com.applycopilot.db.AdminQueries
import com.applycopilot.db.EventTypes
import com.applycopilot.db.Events
import com.applycopilot.db.PreferencesTable
import com.applycopilot.db.UserSummary
import com.applycopilot.core.Preferences
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import java.util.UUID

@Serializable
data class CountedValueDto(val value: String, val count: Int)

@Serializable
data class SourceFunnelDto(val source: String, val signups: Int, val profiles: Int, val tailored: Int)

@Serializable
data class DayActivityDto(val day: String, val activeUsers: Int, val newUsers: Int, val events: Int)

@Serializable
data class AdminOverviewDto(
    val windowDays: Int,
    val totalUsers: Int,
    val newUsers: Int,
    val activeUsers7: Int,
    val activeUsers30: Int,
    val days: List<DayActivityDto>,
    val eventCounts: Map<String, Int>,
    val funnel: Map<String, Int>,
    val topRoles: List<CountedValueDto>,
    val topLocations: List<CountedValueDto>,
    val topAppliedCompanies: List<CountedValueDto>,
    val sources: List<SourceFunnelDto> = emptyList(),
    val waitlistSources: List<CountedValueDto> = emptyList(),
)

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val plan: String,
    val createdAt: String,
    val lastSeenAt: String?,
    val name: String?,
    val headline: String?,
    val roles: List<String>,
    val locations: List<String>,
    val refreshes: Int,
    val tailored: Int,
    val approved: Int,
    val applyClicks: Int,
    val source: String? = null,
    val campaign: String? = null,
    val referrer: String? = null,
)

@Serializable
data class AdminUsersResponse(val users: List<AdminUserDto>, val total: Int)

@Serializable
data class AdminEventDto(val type: String, val at: String, val jobId: String?, val jobTitle: String?, val company: String?, val data: JsonObject)

@Serializable
data class AdminApplicationDto(val jobId: String, val title: String, val company: String, val status: String, val updatedAt: String, val applyUrl: String)

@Serializable
data class AdminUserDetailDto(
    val user: AdminUserDto,
    val preferences: Preferences?,
    val applications: List<AdminApplicationDto>,
    val timeline: List<AdminEventDto>,
)

private fun UserSummary.toDto() = AdminUserDto(
    id.toString(), email, plan, createdAt.toString(), lastSeenAt?.toString(), name, headline, roles, locations,
    refreshes, tailored, approved, applyClicks, source, campaign, referrer,
)

/**
 * The admin dashboard: usage overview, the user list and one user's activity. ADMIN_EMAILS only.
 * Opening a user's activity is itself recorded (against the admin), so access to personal data leaves a trail.
 */
fun Route.adminDashboardRoutes(deps: Deps) {
    route("/admin") {
        get("/overview") {
            call.requireAdmin(deps)
            val days = (call.request.queryParameters["days"]?.toIntOrNull() ?: 30).coerceIn(1, 90)
            val o = deps.db.tx { c -> AdminQueries.overview(c, days) }
            call.respond(
                AdminOverviewDto(
                    windowDays = days,
                    totalUsers = o.totalUsers,
                    newUsers = o.newUsers,
                    activeUsers7 = o.activeUsers7,
                    activeUsers30 = o.activeUsers30,
                    days = o.days.map { DayActivityDto(it.day.toString(), it.activeUsers, it.newUsers, it.events) },
                    eventCounts = o.eventCounts,
                    funnel = o.funnel,
                    topRoles = o.topRoles.map { CountedValueDto(it.value, it.count) },
                    topLocations = o.topLocations.map { CountedValueDto(it.value, it.count) },
                    topAppliedCompanies = o.topAppliedCompanies.map { CountedValueDto(it.value, it.count) },
                    sources = o.sources.map { SourceFunnelDto(it.source, it.signups, it.profiles, it.tailored) },
                    waitlistSources = o.waitlistSources.map { CountedValueDto(it.value, it.count) },
                ),
            )
        }

        get("/users") {
            call.requireAdmin(deps)
            val q = call.request.queryParameters["q"].orEmpty().take(100)
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
            val offset = (call.request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val (users, total) = deps.db.tx { c -> AdminQueries.users(c, q, limit, offset) }
            call.respond(AdminUsersResponse(users.map { it.toDto() }, total))
        }

        get("/users/{id}") {
            val adminId = call.requireAdmin(deps)
            val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: throw ApiException(HttpStatusCode.NotFound, "User not found")
            val detail = deps.db.tx { c ->
                val user = AdminQueries.user(c, id) ?: return@tx null
                Events.record(c, adminId, EventTypes.ADMIN_VIEWED_USER, data = Events.data("viewedUserId" to id.toString()))
                AdminUserDetailDto(
                    user = user.toDto(),
                    preferences = PreferencesTable.find(c, id),
                    applications = AdminQueries.applications(c, id).map {
                        AdminApplicationDto(it.jobId.toString(), it.title, it.company, it.status, it.updatedAt.toString(), it.applyUrl)
                    },
                    timeline = Events.timeline(c, id).map {
                        AdminEventDto(it.type, it.at.toString(), it.jobId?.toString(), it.jobTitle, it.company, it.data)
                    },
                )
            } ?: throw ApiException(HttpStatusCode.NotFound, "User not found")
            call.respond(detail)
        }
    }
}
