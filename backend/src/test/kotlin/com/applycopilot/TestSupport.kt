package com.applycopilot

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/** One Postgres container shared by every integration test class in the run. */
object TestDb {
    val postgres: PostgreSQLContainer<Nothing> by lazy {
        PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).also { it.start() }
    }
}
