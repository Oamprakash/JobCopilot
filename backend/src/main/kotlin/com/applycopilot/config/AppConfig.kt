package com.applycopilot.config

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
            )
        }
    }
}
