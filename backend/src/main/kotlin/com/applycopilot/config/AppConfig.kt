package com.applycopilot.config

import com.applycopilot.jobs.AdzunaConfig

/** An S3-compatible bucket for uploaded files, e.g. Cloudflare R2 (endpoint https://<account>.r2.cloudflarestorage.com). */
data class ObjectStorageConfig(val endpoint: String, val bucket: String, val accessKeyId: String, val secretAccessKey: String, val region: String = "auto")

data class MailConfig(val resendApiKey: String, val from: String, val appName: String = "TrulyApply")

data class AppConfig(
    /** "dev", "test" or "prod". Outside prod, magic links are logged and returned in the API response. */
    val env: String,
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val jwtSecret: String,
    /** Origin of the web app, e.g. http://localhost:3000. Magic links point here. */
    val webBaseUrl: String,
    /** Origins allowed to send changes: the web app's address, plus the host's own address (the old onrender.com link). */
    val allowedOrigins: Set<String> = setOf(webBaseUrl),
    val anthropicApiKey: String?,
    val claudeModel: String,
    val storageDir: String,
    val mail: MailConfig?,
    val secureCookies: Boolean,
    /** Emails allowed to use /admin routes (manage boards, trigger ingestion). */
    val adminEmails: Set<String> = emptySet(),
    /** Cheaper model for per-job fit scoring; the parsing model stays on [claudeModel]. */
    val claudeScoringModel: String = "claude-haiku-5-5",
    val ingestIntervalHours: Long = 24,
    val schedulerEnabled: Boolean = false,
    /** Tailored applications a free user may start per calendar month. */
    val freeTailorLimit: Int = 5,
    /**
     * Sharing a dev build publicly (e.g. through a Cloudflare tunnel). Only [inviteEmails] and admins may sign in,
     * and admins' sign-in links are never shown on screen, only logged, so a visitor can't sign in as an admin.
     */
    val publicPreview: Boolean = false,
    /** With [publicPreview]: who besides admins may sign in. Empty means anyone. */
    val inviteEmails: Set<String> = emptySet(),
    /** When set, uploaded files go to this bucket instead of [storageDir]. */
    val objectStorage: ObjectStorageConfig? = null,
    /** The daily match email goes out at this hour (DIGEST_HOUR, 0-23) in [digestTimeZone] (DIGEST_TIMEZONE). */
    val digestHour: Int = 8,
    val digestTimeZone: String = "Asia/Kolkata",
    /** The product's name in emails and messages (APP_NAME). */
    val appName: String = "TrulyApply",
    /** Activity events (admin dashboard) are kept this many days. */
    val eventRetentionDays: Int = 180,
    /** Adzuna job search, used when both ADZUNA_APP_ID and ADZUNA_APP_KEY are set. */
    val adzuna: AdzunaConfig? = null,
    /** Searches a day against Himalayas' free remote-jobs API (HIMALAYAS_DAILY_CALLS). Its admin switch turns it off. */
    val himalayasDailyCalls: Int = 120,
    /** Home-page fit checks a day in all (FIT_CHECK_DAILY), and how many of those may use Claude (FIT_CHECK_AI_DAILY). */
    val fitCheckDailyLimit: Int = 300,
    val fitCheckAiDailyLimit: Int = 40,
    /** OAuth client ID for "Continue with Google" (GOOGLE_CLIENT_ID); unset hides the button. */
    val googleClientId: String? = null,
) {
    val isProd: Boolean get() = env == "prod"

    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val appEnv = env["APP_ENV"] ?: "dev"
            val jwtSecret = env["JWT_SECRET"] ?: if (appEnv == "prod") {
                error("JWT_SECRET is required in prod")
            } else {
                "dev-only-secret-change-me-dev-only-secret"
            }
            require(jwtSecret.length >= 32) { "JWT_SECRET must be at least 32 characters" }

            val appName = env["APP_NAME"]?.trim()?.takeIf { it.isNotEmpty() } ?: "TrulyApply"
            val mail = env["RESEND_API_KEY"]?.takeIf { it.isNotBlank() }?.let {
                MailConfig(resendApiKey = it, from = env["MAIL_FROM"] ?: "$appName <login@trulyapply.com>", appName = appName)
            }
            if (appEnv == "prod") requireNotNull(mail) { "RESEND_API_KEY is required in prod so magic links can be emailed" }

            val db = database(env)
            return AppConfig(
                env = appEnv,
                port = (env["API_PORT"] ?: env["PORT"])?.toInt() ?: 8080,
                databaseUrl = db.first,
                databaseUser = db.second,
                databasePassword = db.third,
                jwtSecret = jwtSecret,
                // On Render, RENDER_EXTERNAL_URL is the service's public https address.
                webBaseUrl = webBaseUrl(env),
                allowedOrigins = setOfNotNull(webBaseUrl(env), env["RENDER_EXTERNAL_URL"]?.takeIf { it.isNotBlank() }?.trimEnd('/')),
                anthropicApiKey = env["ANTHROPIC_API_KEY"]?.takeIf { it.isNotBlank() },
                claudeModel = env["CLAUDE_MODEL"] ?: "claude-sonnet-5-5",
                storageDir = env["STORAGE_DIR"] ?: "./data/uploads",
                mail = mail,
                secureCookies = env["SECURE_COOKIES"]?.toBoolean() ?: (appEnv == "prod"),
                adminEmails = env["ADMIN_EMAILS"].orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
                claudeScoringModel = env["CLAUDE_SCORING_MODEL"] ?: "claude-haiku-5-5",
                ingestIntervalHours = env["INGEST_INTERVAL_HOURS"]?.toLong() ?: 24,
                // Off in tests so nothing calls real job boards; on by default everywhere else.
                schedulerEnabled = env["SCHEDULER_ENABLED"]?.toBoolean() ?: (appEnv != "test"),
                freeTailorLimit = env["FREE_TAILOR_LIMIT"]?.toInt() ?: 5,
                adzuna = adzunaConfig(env),
                googleClientId = env["GOOGLE_CLIENT_ID"]?.trim()?.takeIf { it.endsWith(".apps.googleusercontent.com") },
                fitCheckDailyLimit = env["FIT_CHECK_DAILY"]?.toIntOrNull()?.coerceAtLeast(0) ?: 300,
                fitCheckAiDailyLimit = env["FIT_CHECK_AI_DAILY"]?.toIntOrNull()?.coerceAtLeast(0) ?: 40,
                himalayasDailyCalls = env["HIMALAYAS_DAILY_CALLS"]?.toIntOrNull()?.coerceAtLeast(0) ?: 120,
                appName = appName,
                digestHour = (env["DIGEST_HOUR"]?.toIntOrNull() ?: 8).coerceIn(0, 23),
                digestTimeZone = env["DIGEST_TIMEZONE"]?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata",
                objectStorage = objectStorage(env),
                publicPreview = env["PUBLIC_PREVIEW"]?.toBoolean() ?: false,
                inviteEmails = env["INVITE_EMAILS"].orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet(),
                eventRetentionDays = (env["EVENT_RETENTION_DAYS"]?.toInt() ?: 180).coerceAtLeast(1),
            )
        }

        /**
         * JDBC URL, user and password. DATABASE_URL may be a JDBC URL (user and password then come from
         * DATABASE_USER / DATABASE_PASSWORD) or a postgres://user:password@host:port/db URL as hosts like Render give.
         */
        private fun webBaseUrl(env: Map<String, String>): String =
            (env["WEB_BASE_URL"]?.takeIf { it.isNotBlank() } ?: env["RENDER_EXTERNAL_URL"] ?: "http://localhost:3000").trimEnd('/')

        internal fun database(env: Map<String, String>): Triple<String, String, String> {
            val raw = env["DATABASE_URL"]?.trim().orEmpty()
            if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
                val uri = java.net.URI(raw)
                val (user, password) = (uri.rawUserInfo ?: "").split(":", limit = 2).map { java.net.URLDecoder.decode(it, Charsets.UTF_8) }
                    .let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
                val port = if (uri.port == -1) 5432 else uri.port
                // libpq-only settings the JDBC driver doesn't take (Neon's URLs add channel_binding).
                val query = uri.rawQuery?.split("&")?.filter { it.isNotEmpty() && !it.startsWith("channel_binding=") }
                    ?.takeIf { it.isNotEmpty() }?.joinToString("&", prefix = "?").orEmpty()
                return Triple(
                    "jdbc:postgresql://${uri.host}:$port${uri.rawPath}$query",
                    env["DATABASE_USER"]?.takeIf { it.isNotBlank() } ?: user,
                    env["DATABASE_PASSWORD"]?.takeIf { it.isNotBlank() } ?: password,
                )
            }
            return Triple(
                raw.ifEmpty { "jdbc:postgresql://localhost:5432/applycopilot" },
                env["DATABASE_USER"] ?: "applycopilot",
                env["DATABASE_PASSWORD"] ?: "applycopilot",
            )
        }

        /** R2: R2_ACCOUNT_ID (or R2_ENDPOINT), R2_BUCKET, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY. All or nothing. */
        private fun objectStorage(env: Map<String, String>): ObjectStorageConfig? {
            fun v(k: String) = env[k]?.trim()?.takeIf { it.isNotEmpty() }
            val endpoint = v("R2_ENDPOINT") ?: v("R2_ACCOUNT_ID")?.let { "https://$it.r2.cloudflarestorage.com" }
            val bucket = v("R2_BUCKET")
            val key = v("R2_ACCESS_KEY_ID")
            val secret = v("R2_SECRET_ACCESS_KEY")
            val given = listOf(endpoint, bucket, key, secret)
            if (given.all { it == null }) return null
            require(given.none { it == null }) { "R2 storage needs R2_ACCOUNT_ID (or R2_ENDPOINT), R2_BUCKET, R2_ACCESS_KEY_ID and R2_SECRET_ACCESS_KEY" }
            return ObjectStorageConfig(endpoint!!.trimEnd('/'), bucket!!, key!!, secret!!, v("R2_REGION") ?: "auto")
        }

        private fun adzunaConfig(env: Map<String, String>): AdzunaConfig? {
            val id = env["ADZUNA_APP_ID"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val key = env["ADZUNA_APP_KEY"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return AdzunaConfig(
                appId = id,
                appKey = key,
                country = env["ADZUNA_COUNTRY"]?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "in",
                // The free key allows 250 calls a day and 2,500 a month; 60 a day stays inside both.
                dailyCalls = env["ADZUNA_DAILY_CALLS"]?.toInt() ?: 60,
            )
        }
    }
}
