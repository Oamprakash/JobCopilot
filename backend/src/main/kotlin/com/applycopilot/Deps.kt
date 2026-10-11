package com.applycopilot

import com.applycopilot.auth.ConsoleMailer
import com.applycopilot.auth.JwtService
import com.applycopilot.auth.Mailer
import com.applycopilot.auth.ResendMailer
import com.applycopilot.config.AppConfig
import com.applycopilot.db.Db
import com.applycopilot.resume.ClaudeResumeParser
import com.applycopilot.resume.ResumeParsingService
import com.applycopilot.storage.FileStorage
import com.applycopilot.storage.LocalFileStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout

/** Everything the routes need, built once at startup (tests build their own). */
class Deps(
    val config: AppConfig,
    val db: Db,
    val storage: FileStorage,
    val mailer: Mailer,
    val jwt: JwtService,
    val parsing: ResumeParsingService,
    private val onClose: List<AutoCloseable> = emptyList(),
) : AutoCloseable {

    override fun close() = onClose.forEach { runCatching { it.close() } }

    companion object {
        fun create(config: AppConfig): Deps {
            val http = HttpClient(CIO) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    requestTimeoutMillis = 90_000
                }
            }
            val db = Db(config)
            val claude = config.anthropicApiKey?.let { ClaudeResumeParser(http, it, config.claudeModel) }
            return Deps(
                config = config,
                db = db,
                storage = LocalFileStorage(config.storageDir),
                mailer = config.mail?.let { ResendMailer(http, it) } ?: ConsoleMailer(),
                jwt = JwtService(config.jwtSecret),
                parsing = ResumeParsingService(claude),
                onClose = listOf(db, http),
            )
        }
    }
}
