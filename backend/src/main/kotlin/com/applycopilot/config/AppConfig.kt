package com.applycopilot.config

import com.applycopilot.jobs.AdzunaConfig

data class MailConfig(val resendApiKey: String, val from: String)

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
    /** Adzuna job search, used when both ADZUNA_APP_ID and ADZUNA_APP_KEY are set. */
    val adzuna: AdzunaConfig? = null,
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

            val mail = env["RESEND_API_KEY"]?.takeIf { it.isNotBlank() }?.let {
                MailConfig(resendApiKey = it, from = env["MAIL_FROM"] ?: "Apply Copilot <login@example.com>")
            }
            if (appEnv == "prod") requireNotNull(mail) { "RESEND_API_KEY is required in prod so magic links can be emailed" }

            return AppConfig(
                env = appEnv,
                port = env["PORT"]?.toInt() ?: 8080,
                databaseUrl = env["DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/applycopilot",
                databaseUser = env["DATABASE_USER"] ?: "applycopilot",
                databasePassword = env["DATABASE_PASSWORD"] ?: "applycopilot",
                jwtSecret = jwtSecret,
                webBaseUrl = (env["WEB_BASE_URL"] ?: "http://localhost:3000").trimEnd('/'),
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
            )
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
