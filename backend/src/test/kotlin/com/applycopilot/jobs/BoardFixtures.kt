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

    /** SmartRecruiters list (no descriptions) and per-posting detail, in their documented shapes. */
    val smartRecruitersList = """
        {"offset": 0, "limit": 100, "totalFound": 2, "content": [
          {"id": "744000001", "name": "Senior Software Engineer, Mobile (Android)", "releasedDate": "$publishedAt",
           "location": {"city": "Bangalore", "region": "KA", "country": "in", "remote": false}},
          {"id": "744000002", "name": "Account Manager", "releasedDate": "$publishedAt",
           "location": {"city": "Singapore", "country": "sg", "remote": false}}
        ]}
    """.trimIndent()

    val smartRecruitersDetail = """
        {"id": "744000001", "name": "Senior Software Engineer, Mobile (Android)",
         "postingUrl": "https://jobs.smartrecruiters.com/grab/744000001-senior-software-engineer-mobile-android",
         "location": {"city": "Bangalore", "region": "KA", "country": "in", "remote": false},
         "jobAd": {"sections": {
           "companyDescription": {"title": "Company", "text": "<p>About us</p>"},
           "jobDescription": {"title": "Job", "text": "<p>Build our Android app in Kotlin.</p>"},
           "qualifications": {"title": "Qualifications", "text": "<ul><li>6+ years of Android</li></ul>"},
           "additionalInformation": {"title": "More", "text": ""}
         }}}
    """.trimIndent()

    val workable = """
        {"name": "Apna", "description": "", "jobs": [
          {"title": "Android Developer", "shortcode": "5211B21BC2", "employment_type": "Full-time", "telecommuting": false,
           "url": "https://apply.workable.com/j/5211B21BC2", "application_url": "https://apply.workable.com/j/5211B21BC2/apply",
           "published_on": "${publishedAt.toString().take(10)}", "country": "India", "city": "Bengaluru", "state": "Karnataka",
           "locations": [{"country": "India", "countryCode": "IN", "city": "Bengaluru", "region": "Karnataka", "hidden": false}],
           "description": "<p>Kotlin and <strong>Jetpack Compose</strong>.<br>5+ years.</p>"},
          {"title": "Growth Lead", "shortcode": "ABC", "telecommuting": true, "url": "https://apply.workable.com/j/ABC",
           "published_on": "${publishedAt.toString().take(10)}", "country": "India", "city": "", "locations": [],
           "description": "<p>Growth</p>"}
        ]}
    """.trimIndent()

    /** One Adzuna search page: a new employer, a copy of Acme's Greenhouse job, and an off-target role. */
    val adzuna = """
        {"count": 3, "mean": 2400000, "results": [
          {"id": "4810001", "title": "Senior <strong>Android</strong> Developer", "created": "${publishedAt}",
           "description": "Build the Flipkart app in Kotlin and Jetpack Compose. 8+ years of Android experience&hellip;",
           "redirect_url": "https://www.adzuna.in/land/ad/4810001?se=x&amp;v=1",
           "company": {"display_name": "Flipkart"}, "location": {"display_name": "Bengaluru, Karnataka", "area": ["India", "Karnataka", "Bengaluru"]},
           "category": {"label": "IT Jobs"}},
          {"id": "4810002", "title": "Senior Android Engineer", "created": "${publishedAt}",
           "description": "Build our Android app with Kotlin.", "redirect_url": "https://www.adzuna.in/land/ad/4810002",
           "company": {"display_name": "Acme"}, "location": {"display_name": "Bangalore"}},
          {"id": "4810003", "title": "Android Sales Executive (Remote)", "created": "${publishedAt}",
           "description": "Sell phones.", "redirect_url": "https://www.adzuna.in/land/ad/4810003",
           "location": {"display_name": "India"}},
          {"id": "4810004", "title": "No link", "description": "Dropped: no redirect_url."}
        ]}
    """.trimIndent()

    /**
     * One Himalayas search page: a job open to India, a worldwide one, a US-only one (dropped), an expired one
     * (dropped) and one without a link (dropped). Dates are Unix milliseconds, as Himalayas sends them.
     */
    val himalayas: String get() {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return """
        {"updatedAt": $now, "offset": 0, "limit": 20, "totalCount": 5, "jobs": [
          {"guid": "https://himalayas.app/companies/nimbus/jobs/senior-android-engineer", "title": "Senior Android Engineer",
           "companyName": "Nimbus", "companySlug": "nimbus", "employmentType": "Full Time", "seniority": ["Senior"],
           "locationRestrictions": [{"alpha2": "IN", "name": "India", "slug": "india"}, {"alpha2": "PH", "name": "Philippines", "slug": "philippines"}],
           "description": "<p>Build our <b>Android</b> app with Kotlin, Jetpack Compose and Coroutines. 8+ years of Android experience.</p>",
           "pubDate": ${now - day}, "expiryDate": ${now + 30 * day},
           "applicationLink": "https://himalayas.app/companies/nimbus/jobs/senior-android-engineer"},
          {"guid": "hm-2", "title": "Android Developer", "companyName": "Orbit", "locationRestrictions": [],
           "description": "<p>Kotlin and Android work, fully remote.</p>", "pubDate": ${now - 2 * day},
           "applicationLink": "https://himalayas.app/companies/orbit/jobs/android-developer"},
          {"guid": "hm-3", "title": "Android Engineer (US only)", "companyName": "Stateside",
           "locationRestrictions": [{"alpha2": "US", "name": "United States", "slug": "united-states"}],
           "description": "<p>US residents only.</p>", "applicationLink": "https://himalayas.app/companies/stateside/jobs/android"},
          {"guid": "hm-4", "title": "Android Lead", "companyName": "Gone", "locationRestrictions": [],
           "description": "<p>Closed.</p>", "expiryDate": ${now - day}, "applicationLink": "https://himalayas.app/companies/gone/jobs/android-lead"},
          {"guid": "hm-5", "title": "No link", "companyName": "Nowhere", "description": "<p>Dropped.</p>"}
        ]}
        """.trimIndent()
    }
}
