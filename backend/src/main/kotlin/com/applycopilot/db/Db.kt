package com.applycopilot.db

import com.applycopilot.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.io.Closeable
import java.sql.Connection

/** Plain JDBC over a Hikari pool. Every [tx] block runs in one transaction on the IO dispatcher. */
class Db(url: String, user: String, password: String) : Closeable {
    constructor(config: AppConfig) : this(config.databaseUrl, config.databaseUser, config.databasePassword)

    val dataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = url
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
        },
    )

    fun migrate() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
    }

    suspend fun <T> tx(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { c ->
            try {
                val result = block(c)
                c.commit()
                result
            } catch (e: Throwable) {
                c.rollback()
                throw e
            }
        }
    }

    override fun close() = dataSource.close()
}
