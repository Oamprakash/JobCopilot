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
}
