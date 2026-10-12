package com.applycopilot.tailoring

import com.applycopilot.core.Experience
import com.applycopilot.core.Preferences
import com.applycopilot.core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TailoringTest {
    private val profile = Profile(
        fullName = "Oamprakash R",
        headline = "Lead Android Developer",
        totalExperienceYears = 12.0,
        skills = listOf("Firebase", "Kotlin", "Team leadership", "Jetpack Compose", "Coroutines", "MVVM"),
        experience = listOf(
            Experience(
                title = "Lead Android Developer", company = "Movate", startDate = "2020-07", current = true,
                bullets = listOf(
                    "Set up CI with Gradle and Firebase App Distribution",
                    "Lead a team of 20+ Android engineers across 4 apps",
                    "Built Compose screens in Kotlin with Coroutines",
                ),
            ),
            Experience(
                title = "Senior Android Developer", company = "Acme", startDate = "2016-01", endDate = "2020-06",
                bullets = listOf("Shipped a payments SDK used by 2M users"),
            ),
        ),
    )
    private val prefs = Preferences(noticePeriodDays = 60)
    private val job = JobForTailoring(
        "Senior Android Engineer",
        "Northwind",
        "Kotlin, Jetpack Compose and Coroutines. Experience with MVVM. Mentoring engineers. 8+ years.",
    )
    private val questions = Questions.defaults("Northwind")

    private fun tailorAndGuard(): GuardResult {
        val draft = HeuristicTailor.tailor(profile, prefs, job, questions)
        return TailorGuard.apply(draft, profile, prefs, job, questions)
    }

    @Test
    fun heuristicTailorReordersSkillsAndBulletsTowardTheJob() {
        val resume = tailorAndGuard().application.resume
        assertEquals(listOf("Kotlin", "Jetpack Compose", "Coroutines", "MVVM", "Firebase", "Team leadership"), resume.skills)
        val bullets = resume.experience[0].bullets
        assertEquals(listOf(2, 1, 0), bullets.map { it.sourceIndex })
        assertEquals(listOf(Change.MOVED_UP, Change.UNCHANGED, Change.UNCHANGED), bullets.map { it.change })
        assertEquals("Lead Android Developer with 12 years of experience in Kotlin, Jetpack Compose and Coroutines.", resume.summary)
        assertEquals(listOf("Movate", "Acme"), resume.experience.map { it.company })
    }

    @Test
    fun heuristicOutputPassesTheGuardUntouched() {
        val result = tailorAndGuard()
        assertTrue(result.removed.isEmpty(), "removed: ${result.removed}")
        val letter = result.application.coverLetter
        assertTrue(letter.startsWith("Dear Hiring Team at Northwind,"))
        assertTrue(letter.contains("I'm currently Lead Android Developer at Movate, with 12 years of experience."), letter)
        assertTrue(letter.contains("Kotlin, Jetpack Compose and Coroutines"))
        assertTrue(letter.trimEnd().endsWith("Oamprakash R"))
    }

    @Test
    fun answersNeverGuessPayAndUseTheUsersNoticePeriod() {
        val answers = tailorAndGuard().application.answers.associateBy { it.question }
        assertTrue(answers.getValue("Why do you want to work at Northwind?").answer.contains("Northwind"))
        assertFalse(answers.getValue("Why are you a good fit for this role?").needsUser)
        assertEquals("60 days", answers.getValue("What is your notice period?").answer)
        val pay = answers.getValue("What are your current and expected CTC?")
        assertTrue(pay.needsUser)
        assertEquals("", pay.answer)
        assertTrue(Questions.isSensitive("Will you require visa sponsorship?"))
        assertTrue(Questions.isPay("Expected salary (LPA)"))
    }

    @Test
    fun guardBlocksInventedClaims() {
        val flutterJob = job.copy(title = "Senior Android Engineer (Flutter)")
        val honest = HeuristicTailor.tailor(profile, prefs, flutterJob, questions)
        val draft = honest.copy(
            resume = honest.resume.copy(
                headline = "Principal Engineer",
                skills = honest.resume.skills + "Kubernetes",
                experience = listOf(
                    TailoredExperience(
                        sourceIndex = 0, title = "CTO", company = "Movate",
                        bullets = listOf(
                            TailoredBullet("Lead a team of 50 Android engineers across 4 apps", 1),
                            TailoredBullet("Built Compose screens in Kotlin", 2),
                            TailoredBullet("Grew revenue tenfold with blockchain growth hacks", 0),
                        ),
                    ),
                    TailoredExperience(sourceIndex = 7, title = "VP", company = "Google", bullets = listOf(TailoredBullet("Ran Search", 0))),
                ),
            ),
            coverLetter = "I'm applying for the Senior Android Engineer (Flutter) role.\nI also bring deep Kubernetes expertise. I cut crashes by 90%.",
            answers = honest.answers.map { if (Questions.isPay(it.question)) it.copy(answer = "30 LPA", needsUser = false) else it },
        )
        val result = TailorGuard.apply(draft, profile, prefs, flutterJob, questions)
        val resume = result.application.resume

        assertEquals("Lead Android Developer", resume.headline)
        assertFalse("Kubernetes" in resume.skills)
        assertEquals(listOf("Movate", "Acme"), resume.experience.map { it.company }, "fake role ignored, dropped role restored")
        assertEquals("Lead Android Developer", resume.experience[0].title, "titles always come from the profile")

        val bullets = resume.experience[0].bullets
        assertEquals("Lead a team of 20+ Android engineers across 4 apps", bullets[0].text, "a new number reverts the bullet")
        assertEquals(Change.MOVED_UP, bullets[0].change)
        assertEquals("Built Compose screens in Kotlin", bullets[1].text, "an honest rewording is kept")
        assertEquals(Change.REWORDED, bullets[1].change)
        assertEquals("Built Compose screens in Kotlin with Coroutines", bullets[1].original)
        assertEquals("Set up CI with Gradle and Firebase App Distribution", bullets[2].text, "an invented bullet reverts to its source")
        assertEquals(listOf("Shipped a payments SDK used by 2M users"), resume.experience[1].bullets.map { it.text })

        val letter = result.application.coverLetter
        assertTrue(letter.contains("Senior Android Engineer (Flutter) role"), "the job's own title is not a claim")
        assertFalse(letter.contains("Kubernetes"))
        assertFalse(letter.contains("90%"))

        val pay = result.application.answers.first { Questions.isPay(it.question) }
        assertEquals("", pay.answer)
        assertTrue(pay.needsUser)
        assertTrue(result.removed.size >= 6, "removed: ${result.removed}")
    }

    @Test
    fun warningsFlagUnverifiedUserEdits() {
        val app = tailorAndGuard().application
        val edited = app.copy(
            resume = app.resume.copy(
                experience = app.resume.experience.mapIndexed { i, e ->
                    if (i == 0) e.copy(bullets = e.bullets + TailoredBullet("Cut crash rate by 70%", 0, Change.REWORDED)) else e
                },
            ),
        )
        val warnings = TailorGuard.warnings(edited, profile)
        assertEquals(1, warnings.size, "$warnings")
        assertTrue(warnings[0].contains("70%"))
    }

    @Test
    fun keywordsCoveredAndMissing() {
        val app = tailorAndGuard().application
        assertEquals(listOf("Kotlin", "Android", "Jetpack Compose", "Coroutines", "MVVM"), Keywords.covered(app, job))
        assertEquals(listOf("Mentoring"), Keywords.missing(profile, job))
        assertEquals(listOf("Flutter", "Mentoring"), Keywords.missing(profile, job.copy(description = job.description + " Flutter is a plus.")))
    }
}
