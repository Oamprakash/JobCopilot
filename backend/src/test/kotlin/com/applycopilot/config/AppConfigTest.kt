package com.applycopilot.config

import kotlin.test.Test
import kotlin.test.assertEquals

class AppConfigTest {
    @Test
    fun acceptsPostgresUrlsAsHostsGiveThem() {
        assertEquals(
            Triple("jdbc:postgresql://dpg-abc-a:5432/jobcopilot", "jobcopilot_user", "p@ss:word"),
            AppConfig.database(mapOf("DATABASE_URL" to "postgresql://jobcopilot_user:p%40ss%3Aword@dpg-abc-a/jobcopilot")),
        )
        assertEquals(
            Triple("jdbc:postgresql://db.example.com:6543/app?sslmode=require", "u", "pw"),
            AppConfig.database(mapOf("DATABASE_URL" to "postgres://u:pw@db.example.com:6543/app?sslmode=require")),
        )
        assertEquals(
            Triple("jdbc:postgresql://localhost:5432/applycopilot", "applycopilot", "applycopilot"),
            AppConfig.database(emptyMap()),
        )
    }

    @Test
    fun acceptsNeonConnectionStrings() {
        assertEquals(
            Triple("jdbc:postgresql://ep-cool-river-a1b2c3.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require", "neondb_owner", "npg_secret"),
            AppConfig.database(
                mapOf("DATABASE_URL" to "postgresql://neondb_owner:npg_secret@ep-cool-river-a1b2c3.ap-southeast-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"),
            ),
        )
    }

    @Test
    fun webBaseUrlFallsBackToTheHostsPublicAddress() {
        val base = mapOf("APP_ENV" to "test")
        assertEquals("https://jobcopilot.onrender.com", AppConfig.fromEnv(base + ("RENDER_EXTERNAL_URL" to "https://jobcopilot.onrender.com/")).webBaseUrl)
        assertEquals("http://localhost:3000", AppConfig.fromEnv(base).webBaseUrl)
        assertEquals(9090, AppConfig.fromEnv(base + mapOf("PORT" to "10000", "API_PORT" to "9090")).port)
    }

    @Test
    fun r2SettingsAreAllOrNothing() {
        val base = mapOf("APP_ENV" to "test")
        kotlin.test.assertNull(AppConfig.fromEnv(base).objectStorage)
        val r2 = AppConfig.fromEnv(base + mapOf("R2_ACCOUNT_ID" to "abc123", "R2_BUCKET" to "resumes", "R2_ACCESS_KEY_ID" to "k", "R2_SECRET_ACCESS_KEY" to "s")).objectStorage!!
        assertEquals("https://abc123.r2.cloudflarestorage.com", r2.endpoint)
        assertEquals("auto", r2.region)
        kotlin.test.assertFailsWith<IllegalArgumentException> { AppConfig.fromEnv(base + mapOf("R2_BUCKET" to "resumes")) }
    }
}
