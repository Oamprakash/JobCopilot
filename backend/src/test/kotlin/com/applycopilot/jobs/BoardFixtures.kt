package com.applycopilot.jobs

import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Trimmed responses in the shapes the real Greenhouse, Lever and Ashby board APIs return (checked Oct 2026). */
object BoardFixtures {
    /** Recent, so the feed's 60-day freshness window keeps these jobs whenever the tests run. */
    val publishedAt: Instant = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
    private val greenhouseTime = publishedAt.atOffset(ZoneOffset.ofHours(-4)).toString()

    fun greenhouse(includeAndroid: Boolean = true): String {
        val android = """
            {"id": 101, "title": "Senior Android Engineer", "updated_at": "$greenhouseTime",
             "first_published": "$greenhouseTime",
             "absolute_url": "https://boards.greenhouse.io/acme/jobs/101",
             "location": {"name": "Bengaluru, India"},
             "content": "&lt;p&gt;Build our Android app with Kotlin, Jetpack Compose and Coroutines.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;8+ years of experience&lt;/li&gt;&lt;li&gt;Kotlin Multiplatform is a plus&lt;/li&gt;&lt;/ul&gt;"}
        """.trimIndent()
        val others = """
            {"id": 102, "title": "Account Executive", "updated_at": "$greenhouseTime",
             "absolute_url": "https://boards.greenhouse.io/acme/jobs/102", "location": {"name": "Bengaluru, India"},
             "content": "&lt;p&gt;Sell to enterprises.&lt;/p&gt;"},
            {"id": 103, "title": "Android Engineer", "updated_at": "$greenhouseTime",
             "absolute_url": "https://boards.greenhouse.io/acme/jobs/103", "location": {"name": "Remote from the US"},
             "content": "&lt;p&gt;Kotlin and Compose. 5+ years.&lt;/p&gt;"}
        """.trimIndent()
        return """{"jobs": [${if (includeAndroid) "$android," else ""} $others], "meta": {"total": ${if (includeAndroid) 3 else 2}}}"""
    }

    val lever = """
        [{"id": "lv-1", "text": "Lead Mobile Engineer (Android)",
          "categories": {"commitment": "Full-time", "location": "Chennai", "team": "Mobile", "allLocations": ["Chennai"]},
          "country": "IN", "workplaceType": "hybrid", "createdAt": ${publishedAt.toEpochMilli()},
          "hostedUrl": "https://jobs.lever.co/beta/lv-1", "applyUrl": "https://jobs.lever.co/beta/lv-1/apply",
          "openingPlain": "Lead our Android team.", "descriptionPlain": "", "descriptionBodyPlain": "Kotlin, MVVM and Firebase. 6+ years.",
          "lists": [{"text": "Requirements", "content": "<li>Jetpack Compose</li><li>Kotlin Multiplatform</li>"}],
          "additionalPlain": ""}]
    """.trimIndent()

    val ashby = """
        {"apiVersion": "1", "jobs": [
          {"id": "ash-1", "title": "Android Developer", "location": "Remote", "secondaryLocations": [],
           "isRemote": true, "workplaceType": "Remote", "isListed": true, "employmentType": "FullTime",
           "address": {"postalAddress": {"addressCountry": "India"}},
           "publishedAt": "$publishedAt",
           "jobUrl": "https://jobs.ashbyhq.com/gamma/ash-1", "applyUrl": "https://jobs.ashbyhq.com/gamma/ash-1/application",
           "descriptionPlain": "Kotlin and Coroutines on Android. 5+ years."},
          {"id": "ash-2", "title": "Android Developer (unlisted)", "location": "Bengaluru", "isListed": false,
           "jobUrl": "https://jobs.ashbyhq.com/gamma/ash-2", "descriptionPlain": "Hidden"},
          {"id": "ash-3", "title": "Software Engineer, Developer Platform", "location": "San Francisco, California",
           "isRemote": true, "workplaceType": "Hybrid", "isListed": true,
           "jobUrl": "https://jobs.ashbyhq.com/gamma/ash-3", "descriptionHtml": "<p>TypeScript</p>"}
        ]}
    """.trimIndent()
}
