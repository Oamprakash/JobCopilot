package com.applycopilot.api

import com.applycopilot.db.Events
import com.applycopilot.db.EventTypes
import com.applycopilot.Deps
import com.applycopilot.core.PreferenceRules
import com.applycopilot.core.Preferences
import com.applycopilot.core.ProfileRules
import com.applycopilot.db.Matches
import com.applycopilot.db.PreferencesTable
import com.applycopilot.db.Profiles
import com.applycopilot.db.ResumeFiles
import com.applycopilot.db.Users
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.util.UUID

private val log = LoggerFactory.getLogger("AccountRoutes")

/** Fails with 401 when the session belongs to a deleted account; also serialises writes per user. */
internal fun requireUser(c: Connection, userId: UUID) {
    if (!Users.lock(c, userId)) throw ApiException(HttpStatusCode.Unauthorized, "Not signed in")
}

/** Routes that need a signed-in user. Mount inside authenticate("session"). */
fun Route.accountRoutes(deps: Deps) {
    get("/me") {
        val userId = call.userId()
        val me = deps.db.tx { c -> Users.find(c, userId)?.let { it to Profiles.exists(c, userId) } }
        if (me == null) {
            call.clearSessionCookie(deps.config)
            throw ApiException(HttpStatusCode.Unauthorized, "Not signed in")
        }
        val (user, hasProfile) = me
        call.respond(MeResponse(user.id.toString(), user.email, user.plan, hasProfile, user.email in deps.config.adminEmails))
    }

    // One-step account deletion (DPDP Act): the user row cascades to every table, then files are removed.
    delete("/me") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val keys = deps.db.tx { c ->
            val keys = ResumeFiles.storageKeys(c, userId)
            Users.delete(c, userId)
            keys
        }
        withContext(Dispatchers.IO) {
            keys.forEach { key -> runCatching { deps.storage.delete(key) }.onFailure { log.warn("Could not delete {}", key, it) } }
        }
        call.clearSessionCookie(deps.config)
        call.respond(OkResponse())
    }

    get("/profile") {
        val userId = call.userId()
        val response = deps.db.tx { c ->
            val stored = Profiles.latest(c, userId) ?: return@tx null
            val file = stored.resumeFileId?.let { ResumeFiles.find(c, userId, it) }
            ProfileResponse(stored.version, stored.profile, file?.let { ResumeFileDto(it.id.toString(), it.fileName) }, stored.createdAt.toString())
        } ?: throw ApiException(HttpStatusCode.NotFound, "No profile yet")
        call.respond(response)
    }

    // Every save is a new version; earlier versions stay in the table.
    put("/profile") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val request = call.receive<SaveProfileRequest>()
        val profile = ProfileRules.normalizeAndValidate(request.profile)
        val requestedFileId = request.resumeFileId?.let {
            runCatching { UUID.fromString(it) }.getOrNull() ?: throw ApiException(HttpStatusCode.BadRequest, "Unknown resume file")
        }

        val response = deps.db.tx { c ->
            requireUser(c, userId)
            val file = if (requestedFileId != null) {
                ResumeFiles.find(c, userId, requestedFileId) ?: throw ApiException(HttpStatusCode.BadRequest, "Unknown resume file")
            } else {
                Profiles.latest(c, userId)?.resumeFileId?.let { ResumeFiles.find(c, userId, it) }
            }
            val stored = Profiles.append(c, userId, profile, file?.id)
            Events.record(c, userId, EventTypes.PROFILE_SAVED, data = Events.data("version" to stored.version))
            // Unreviewed matches were scored against the old profile; the next run rescores them.
            Matches.clearNew(c, userId)
            ProfileResponse(stored.version, stored.profile, file?.let { ResumeFileDto(it.id.toString(), it.fileName) }, stored.createdAt.toString())
        }
        call.respond(response)
    }

    get("/preferences") {
        val userId = call.userId()
        call.respond(deps.db.tx { c -> PreferencesTable.find(c, userId) } ?: Preferences())
    }

    put("/preferences") {
        call.requireSameOrigin(deps.config)
        val userId = call.userId()
        val preferences = PreferenceRules.normalizeAndValidate(call.receive<Preferences>())
        deps.db.tx { c ->
            requireUser(c, userId)
            PreferencesTable.upsert(c, userId, preferences)
            Matches.clearNew(c, userId)
            // Each saved search: what roles and places the user asked for, and when.
            Events.record(
                c, userId, EventTypes.PREFERENCES_SAVED,
                data = Events.data(
                    "roles" to preferences.roles, "locations" to preferences.locations,
                    "remoteMode" to preferences.remoteMode.dbValue, "needsVisaSponsorship" to preferences.needsVisaSponsorship,
                ),
            )
        }
        call.respond(preferences)
    }
}
