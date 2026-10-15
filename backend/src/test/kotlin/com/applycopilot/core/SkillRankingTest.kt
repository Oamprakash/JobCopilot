package com.applycopilot.core

import com.applycopilot.tailoring.HeuristicTailor
import com.applycopilot.tailoring.JobForTailoring
import com.applycopilot.tailoring.TailorGuard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillRankingTest {
    private val profile = Profile(
        headline = "Lead Android Developer",
        summary = "Android lead building Kotlin apps.",
        skills = listOf("Git", "Jira", "Kotlin", "Java", "Jetpack Compose", "C++", "C", "Agile"),
        experience = listOf(
            Experience(title = "Lead Android Developer", company = "Movate", current = true,
                bullets = listOf("Built Jetpack Compose screens in Kotlin", "Moved the app from Java to Kotlin")),
            Experience(title = "Android Developer", company = "Beta", bullets = listOf("Wrote Java modules and a C++ library")),
        ),
    )

    @Test
    fun mostUsedSkillsComeFirstAndNothingIsLost() {
        val ranked = SkillRanking.rank(profile)
        assertEquals(listOf("Kotlin", "Java", "Jetpack Compose", "C++"), ranked.take(4))
        assertEquals(profile.skills.toSet(), ranked.toSet(), "same skills, only reordered")
        assertEquals(listOf("Git", "Jira", "C", "Agile"), ranked.drop(4), "unmentioned skills keep their order")
    }

    @Test
    fun cPlusPlusDoesNotCountAsC() {
        val ranked = SkillRanking.rank(profile)
        assertTrue(ranked.indexOf("C++") < ranked.indexOf("C"))
    }

    @Test
    fun tailoredResumesListAtMostTwelveSkills() {
        val many = profile.copy(skills = (1..30).map { "Skill$it" } + "Kotlin")
        val app = HeuristicTailor.tailor(many, Preferences(), JobForTailoring("Android Engineer", "Northwind", "Kotlin required."), emptyList())
        assertEquals(TailorGuard.MAX_RESUME_SKILLS, app.resume.skills.size)
        assertEquals("Kotlin", app.resume.skills.first(), "the job's skills lead")
    }

    @Test
    fun cleanupDropsLabelsFragmentsDatesAndDuplicates() {
        val messy = profile.copy(
            skills = listOf(
                "Kotlin", "KOTLIN", "Languages:", "Tools", "5+ years", "2019", "and building apps with the team",
                "worked closely with", "Jetpack Compose", "Jetpack-Compose", "C", "C++", "iOS", "Team leadership", "-",
            ),
        )
        val cleaned = SkillCleanup.clean(messy).skills
        assertEquals(setOf("Kotlin", "Jetpack Compose", "C", "C++", "iOS", "Team leadership"), cleaned.toSet())
        assertEquals("Kotlin", cleaned.first())
    }

    @Test
    fun parsedResumesKeepAtMostThirtySkillsTheMostUsedFirst() {
        val listed = (1..60).joinToString(", ") { "Tool$it" }
        val text = """
            Priya Raman
            Senior Android Developer

            Experience
            Senior Android Developer at Fintech Co    Jan 2020 - Present
            • Built Jetpack Compose screens in Kotlin with Coroutines
            • Moved the payments module to Kotlin

            Skills
            Languages: Kotlin, Java
            Tools: $listed
        """.trimIndent()
        // The same steps ResumeParsingService runs after the rule-based parser.
        val skills = SkillCleanup.clean(ProfileRules.normalize(HeuristicResumeParser().parse(text))).skills
        assertTrue(skills.size <= SkillCleanup.MAX_PARSED_SKILLS, "${skills.size} skills")
        assertEquals("Kotlin", skills.first())
        assertTrue("Jetpack Compose" in skills && "Coroutines" in skills, "skills used in the experience are kept: $skills")
        assertTrue(skills.none { it.endsWith(":") || it == "Tools" || it == "Languages" })
    }
}
