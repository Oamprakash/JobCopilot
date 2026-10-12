package com.applycopilot

import com.applycopilot.auth.ConsoleMailer
import com.applycopilot.auth.JwtService
import com.applycopilot.auth.Mailer
import com.applycopilot.auth.ResendMailer
import com.applycopilot.config.AppConfig
import com.applycopilot.db.Db
import com.applycopilot.jobs.AdzunaClient
import com.applycopilot.jobs.AtsClient
import com.applycopilot.jobs.IngestionService
import com.applycopilot.matching.AutocompleteService
import com.applycopilot.matching.ClaudeJobScorer
import com.applycopilot.matching.MatchingService
import com.applycopilot.resume.ClaudeResumeParser
import com.applycopilot.tailoring.TailorService
import com.applycopilot.tailoring.claude.ClaudeTailor
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
    val ingestion: IngestionService,
    val matching: MatchingService,
    val tailor: TailorService,
    private val onClose: List<AutoCloseable> = emptyList(),
) : AutoCloseable {
    val autocomplete = AutocompleteService(db)

    override fun close() = onClose.forEach { runCatching { it.close() } }

    companion object {
        /** [http] is injectable so tests can serve job boards and the Claude API from a mock engine. */
        fun create(config: AppConfig, http: HttpClient = defaultHttpClient()): Deps {
            val db = Db(config)
            val claude = config.anthropicApiKey?.let { ClaudeResumeParser(http, it, config.claudeModel) }
            val scorer = config.anthropicApiKey?.let { ClaudeJobScorer(http, it, config.claudeScoringModel) }
            return Deps(
                config = config,
                db = db,
                storage = LocalFileStorage(config.storageDir),
                mailer = config.mail?.let { ResendMailer(http, it) } ?: ConsoleMailer(),
                jwt = JwtService(config.jwtSecret),
                parsing = ResumeParsingService(claude),
                ingestion = IngestionService(
                    db,
                    AtsClient(http),
                    adzuna = config.adzuna?.let { AdzunaClient(http, it) },
                    adzunaDailyCalls = config.adzuna?.dailyCalls ?: 0,
                ),
                matching = MatchingService(db, scorer),
                tailor = TailorService(db, config.anthropicApiKey?.let { ClaudeTailor(http, it, config.claudeModel) }, config.freeTailorLimit),
                onClose = listOf(db, http),
            )
        }

        fun defaultHttpClient() = HttpClient(CIO) {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 90_000
            }
        }
    }
}
