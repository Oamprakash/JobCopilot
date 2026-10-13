package com.applycopilot.db

import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import com.applycopilot.core.RemoteMode
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

data class User(val id: UUID, val email: String, val plan: String, val createdAt: Instant)

object Users {
    /** Returns the user for this email, creating it on first sign-in. */
    fun findOrCreate(c: Connection, email: String): User =
        c.prepareStatement(
            """
            INSERT INTO users (id, email) VALUES (?, ?)
            ON CONFLICT (email) DO UPDATE SET email = EXCLUDED.email
            RETURNING id, email, plan, created_at
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, UUID.randomUUID())
            st.setString(2, email)
            st.executeQuery().use { rs -> rs.next(); rs.toUser() }
        }

    fun find(c: Connection, id: UUID): User? =
        c.prepareStatement("SELECT id, email, plan, created_at FROM users WHERE id = ?").use { st ->
            st.setObject(1, id)
            st.executeQuery().use { rs -> if (rs.next()) rs.toUser() else null }
        }

    /** Locks the user's row so concurrent profile saves get consecutive version numbers. */
    fun lock(c: Connection, id: UUID): Boolean =
        c.prepareStatement("SELECT 1 FROM users WHERE id = ? FOR UPDATE").use { st ->
            st.setObject(1, id)
            st.executeQuery().use { it.next() }
        }

    /** Deletes the user; profiles, preferences and resume rows go with it (ON DELETE CASCADE). */
    fun delete(c: Connection, id: UUID): Boolean =
        c.prepareStatement("DELETE FROM users WHERE id = ?").use { st ->
            st.setObject(1, id)
            st.executeUpdate() > 0
        }

    private fun ResultSet.toUser() = User(
        id = getObject("id", UUID::class.java),
        email = getString("email"),
        plan = getString("plan"),
        createdAt = getTimestamp("created_at").toInstant(),
    )
}

object MagicLinks {
    fun insert(c: Connection, tokenHash: String, email: String, expiresAt: Instant) {
        c.prepareStatement("INSERT INTO magic_link_tokens (token_hash, email, expires_at) VALUES (?, ?, ?)").use { st ->
            st.setString(1, tokenHash)
            st.setString(2, email)
            st.setTimestamp(3, Timestamp.from(expiresAt))
            st.executeUpdate()
        }
    }

    fun countSince(c: Connection, email: String, since: Instant): Int =
        c.prepareStatement("SELECT count(*) FROM magic_link_tokens WHERE email = ? AND created_at > ?").use { st ->
            st.setString(1, email)
            st.setTimestamp(2, Timestamp.from(since))
            st.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    /** Marks the token used and returns its email, in one statement, so a link works exactly once. */
    fun consume(c: Connection, tokenHash: String): String? =
        c.prepareStatement(
            """
            UPDATE magic_link_tokens SET used_at = now()
            WHERE token_hash = ? AND used_at IS NULL AND expires_at > now()
            RETURNING email
            """.trimIndent(),
        ).use { st ->
            st.setString(1, tokenHash)
            st.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    fun deleteExpired(c: Connection): Int =
        c.prepareStatement("DELETE FROM magic_link_tokens WHERE expires_at < now() - interval '1 day'").use { it.executeUpdate() }
}

data class ResumeFile(
    val id: UUID,
    val userId: UUID,
    val storageKey: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Int,
)

object ResumeFiles {
    fun insert(c: Connection, file: ResumeFile, textContent: String) {
        c.prepareStatement(
            """
            INSERT INTO resume_files (id, user_id, storage_key, file_name, content_type, size_bytes, text_content)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, file.id)
            st.setObject(2, file.userId)
            st.setString(3, file.storageKey)
            st.setString(4, file.fileName)
            st.setString(5, file.contentType)
            st.setInt(6, file.sizeBytes)
            st.setString(7, textContent)
            st.executeUpdate()
        }
    }

    /** Scoped to the owner: another user's file id returns null. */
    fun find(c: Connection, userId: UUID, id: UUID): ResumeFile? =
        c.prepareStatement(
            "SELECT id, user_id, storage_key, file_name, content_type, size_bytes FROM resume_files WHERE id = ? AND user_id = ?",
        ).use { st ->
            st.setObject(1, id)
            st.setObject(2, userId)
            st.executeQuery().use { rs ->
                if (!rs.next()) null else ResumeFile(
                    id = rs.getObject("id", UUID::class.java),
                    userId = rs.getObject("user_id", UUID::class.java),
                    storageKey = rs.getString("storage_key"),
                    fileName = rs.getString("file_name"),
                    contentType = rs.getString("content_type"),
                    sizeBytes = rs.getInt("size_bytes"),
                )
            }
        }

    fun storageKeys(c: Connection, userId: UUID): List<String> =
        c.prepareStatement("SELECT storage_key FROM resume_files WHERE user_id = ?").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }
}

data class StoredProfile(val version: Int, val profile: Profile, val resumeFileId: UUID?, val createdAt: Instant)

object Profiles {
    fun latest(c: Connection, userId: UUID): StoredProfile? =
        c.prepareStatement(
            "SELECT version, profile_json, resume_file_id, created_at FROM profiles WHERE user_id = ? ORDER BY version DESC LIMIT 1",
        ).use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs ->
                if (!rs.next()) null else StoredProfile(
                    version = rs.getInt("version"),
                    profile = json.decodeFromString(Profile.serializer(), rs.getString("profile_json")),
                    resumeFileId = rs.getObject("resume_file_id", UUID::class.java),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            }
        }

    fun exists(c: Connection, userId: UUID): Boolean =
        c.prepareStatement("SELECT 1 FROM profiles WHERE user_id = ? LIMIT 1").use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { it.next() }
        }

    /** Appends a new version. Call inside a transaction that holds [Users.lock] for this user. */
    fun append(c: Connection, userId: UUID, profile: Profile, resumeFileId: UUID?): StoredProfile =
        c.prepareStatement(
            """
            INSERT INTO profiles (user_id, version, profile_json, resume_file_id)
            SELECT CAST(? AS uuid), coalesce(max(version), 0) + 1, CAST(? AS jsonb), CAST(? AS uuid) FROM profiles WHERE user_id = ?
            RETURNING version, created_at
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setString(2, json.encodeToString(Profile.serializer(), profile))
            if (resumeFileId == null) st.setNull(3, Types.OTHER) else st.setObject(3, resumeFileId)
            st.setObject(4, userId)
            st.executeQuery().use { rs ->
                rs.next()
                StoredProfile(rs.getInt("version"), profile, resumeFileId, rs.getTimestamp("created_at").toInstant())
            }
        }
}

object PreferencesTable {
    fun find(c: Connection, userId: UUID): Preferences? =
        c.prepareStatement(
            """
            SELECT roles, locations, remote_mode, min_ctc_lpa, experience_years, notice_period_days, skip_companies,
                   needs_visa_sponsorship, daily_digest
            FROM preferences WHERE user_id = ?
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.executeQuery().use { rs ->
                if (!rs.next()) null else Preferences(
                    roles = rs.textArray("roles"),
                    locations = rs.textArray("locations"),
                    remoteMode = RemoteMode.fromDb(rs.getString("remote_mode")),
                    minCtcLpa = rs.getBigDecimal("min_ctc_lpa")?.toDouble(),
                    experienceYears = rs.getBigDecimal("experience_years")?.toDouble(),
                    noticePeriodDays = rs.getObject("notice_period_days")?.let { (it as Number).toInt() },
                    skipCompanies = rs.textArray("skip_companies"),
                    needsVisaSponsorship = rs.getBoolean("needs_visa_sponsorship"),
                    dailyDigest = rs.getBoolean("daily_digest"),
                )
            }
        }

    fun upsert(c: Connection, userId: UUID, p: Preferences) {
        c.prepareStatement(
            """
            INSERT INTO preferences (user_id, roles, locations, remote_mode, min_ctc_lpa, experience_years, notice_period_days, skip_companies,
                                     needs_visa_sponsorship, daily_digest, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (user_id) DO UPDATE SET
                roles = EXCLUDED.roles,
                locations = EXCLUDED.locations,
                remote_mode = EXCLUDED.remote_mode,
                min_ctc_lpa = EXCLUDED.min_ctc_lpa,
                experience_years = EXCLUDED.experience_years,
                notice_period_days = EXCLUDED.notice_period_days,
                skip_companies = EXCLUDED.skip_companies,
                needs_visa_sponsorship = EXCLUDED.needs_visa_sponsorship,
                daily_digest = EXCLUDED.daily_digest,
                updated_at = now()
            """.trimIndent(),
        ).use { st ->
            st.setObject(1, userId)
            st.setArray(2, c.createArrayOf("text", p.roles.toTypedArray()))
            st.setArray(3, c.createArrayOf("text", p.locations.toTypedArray()))
            st.setString(4, p.remoteMode.dbValue)
            if (p.minCtcLpa == null) st.setNull(5, Types.NUMERIC) else st.setBigDecimal(5, BigDecimal.valueOf(p.minCtcLpa))
            if (p.experienceYears == null) st.setNull(6, Types.NUMERIC) else st.setBigDecimal(6, BigDecimal.valueOf(p.experienceYears))
            if (p.noticePeriodDays == null) st.setNull(7, Types.INTEGER) else st.setInt(7, p.noticePeriodDays)
            st.setArray(8, c.createArrayOf("text", p.skipCompanies.toTypedArray()))
            st.setBoolean(9, p.needsVisaSponsorship)
            st.setBoolean(10, p.dailyDigest)
            st.executeUpdate()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ResultSet.textArray(column: String): List<String> =
        (getArray(column)?.array as? Array<Any?>)?.mapNotNull { it as String? } ?: emptyList()
}
