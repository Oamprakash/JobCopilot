package com.applycopilot.tailoring

import com.applycopilot.core.Experience
import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FitCheckTest {
    private val profile = Profile(
        fullName = "Oamprakash R",
        headline = "Lead Android Developer",
        totalExperienceYears = 12.0,
        skills = listOf("Firebase", "Kotlin", "Jetpack Compose", "Coroutines", "MVVM"),
        experience = listOf(
            Experience(
                title = "Lead Android Developer", company = "Movate", startDate = "2020-07", current = true,
                bullets = listOf(
                    "Set up CI with Gradle and Firebase App Distribution",
                    "Lead a team of 20+ Android engineers across 4 apps",
                    "Built Compose screens in Kotlin with Coroutines",
                ),
            ),
        ),
    )
    private val jd = """
        Senior Android Engineer
        Build our Android app in Kotlin with Jetpack Compose and Coroutines. Experience with GraphQL and Kotlin Multiplatform is a plus.
        You'll mentor engineers and ship features weekly. 8+ years of Android experience.
    """.trimIndent()

    @Test
    fun scoresTheJobAndShowsTheLinesThatLead() {
        val title = FitCheck.titleFrom(null, jd)
        assertEquals("Senior Android Engineer", title)
        val draft = HeuristicTailor.tailor(profile, Preferences(), JobForTailoring(title, "", jd), emptyList())
        val guarded = TailorGuard.apply(draft, profile, Preferences(), JobForTailoring(title, "", jd), emptyList()).application
        val result = FitCheck.evaluate(profile, title, jd, guarded, "heuristic")

        assertTrue(result.score in 40..100, "a strong match scores well, got ${result.score}")
        assertTrue(result.matchedSkills.containsAll(listOf("Kotlin", "Jetpack Compose", "Coroutines")))
        assertFalse(result.matchedSkills.contains("MVVM"))
        assertTrue(result.missingSkills.any { it.contains("GraphQL", ignoreCase = true) })
        assertEquals(3, result.bullets.size)
        assertEquals("Built Compose screens in Kotlin with Coroutines", result.bullets.first().original, "the most relevant line leads")
        assertTrue(result.bullets.none { it.reworded })
        assertEquals("heuristic", result.rewordedBy)
    }

    @Test
    fun rewordedLinesComeFirstAndKeepTheirOriginal() {
        val guarded = TailoredApplication(
            TailoredResume(
                experience = listOf(
                    TailoredExperience(
                        0,
                        bullets = listOf(
                            TailoredBullet("Lead a team of 20+ Android engineers across 4 apps", 1),
                            TailoredBullet("Built Jetpack Compose screens in Kotlin with Coroutines", 2, Change.REWORDED, "Built Compose screens in Kotlin with Coroutines"),
                        ),
                    ),
                ),
            ),
        )
        val result = FitCheck.evaluate(profile, "Senior Android Engineer", jd, guarded, "claude")
        assertEquals(FitCheckBullet("Built Compose screens in Kotlin with Coroutines", "Built Jetpack Compose screens in Kotlin with Coroutines", true), result.bullets.first())
        assertEquals("claude", result.rewordedBy)
        assertEquals("This job", FitCheck.titleFrom("  ", "x\n\n" + "a".repeat(200)))
    }
}
