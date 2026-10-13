package com.applycopilot.tailoring

import com.applycopilot.core.Experience
import com.applycopilot.core.Profile
import com.applycopilot.jobs.DigestSchedule
import com.applycopilot.jobs.UnsubscribeTokens
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterviewPrepTest {
    private val profile = Profile(
        fullName = "Oam", headline = "Lead Android Developer", skills = listOf("Kotlin", "Jetpack Compose"),
        experience = listOf(
            Experience("Lead Android Developer", "Movate", current = true, bullets = listOf(
                "Led a team of 20 engineers building Android apps in Kotlin",
                "Reduced crash rate by 40% with Jetpack Compose migration",
            )),
            Experience("Android Developer", "Acme", bullets = listOf("Built payment screens in Java")),
        ),
    )
    private val job = JobForTailoring("Senior Android Engineer", "Flipkart", "We use Kotlin, Jetpack Compose and Kotlin Multiplatform. 8+ years.")

    @Test
    fun ruleBasedPrepUsesTheProfilesOwnBullets() {
        val prep = InterviewPrepGuard.apply(HeuristicInterviewPrep.draft(profile, job), profile, job)
        val kotlin = prep.questions.first { it.question.contains("Kotlin?") || it.question.startsWith("How have you used Kotlin ") }
        assertEquals("skill", kotlin.kind)
        assertEquals("Led a team of 20 engineers building Android apps in Kotlin", kotlin.points.first().text)
        assertEquals("Lead Android Developer at Movate", kotlin.points.first().role)
        val gap = prep.questions.single { it.kind == "gap" }
        assertTrue(gap.question.contains("Kotlin Multiplatform"))
        assertTrue(gap.points.isEmpty(), "no talking points invented for a gap")
        assertEquals(listOf("Kotlin Multiplatform"), prep.gaps)
        assertTrue(prep.questions.any { it.question.contains("Flipkart") })
        assertTrue(prep.questions.any { it.kind == "behavioural" && it.points.any { p -> p.text.startsWith("Led a team") } })
        // Every talking point is an exact profile bullet.
        val bullets = profile.experience.flatMap { it.bullets }.toSet()
        assertTrue(prep.questions.flatMap { it.points }.all { it.text in bullets })
    }

    @Test
    fun guardDropsInventedPointsAndClaims() {
        val prep = InterviewPrepGuard.apply(
            listOf(
                DraftPrepQuestion("Tell me about scaling.", "The job is at scale.", listOf(0 to 0, 9 to 0, 0 to 7), "Mention your 15 years of Rust. Keep it short.", "role"),
                DraftPrepQuestion("  ", "x", emptyList(), "", "role"),
                DraftPrepQuestion("Tell me about scaling.", "duplicate", emptyList(), "", "role"),
            ),
            profile, job,
        )
        val q = prep.questions.single()
        assertEquals(1, q.points.size, "references to missing roles or bullets are dropped")
        assertEquals("Keep it short.", q.tip, "a sentence claiming an unknown number and skill is dropped")
        assertTrue(prep.removed.any { it.contains("Rust") || it.contains("15") })
    }

    @Test
    fun digestSlotAndUnsubscribeTokens() {
        val ist = ZoneId.of("Asia/Kolkata")
        // 07:30 IST: the last slot is yesterday's 08:00; 09:00 IST: today's.
        assertEquals(Instant.parse("2026-10-12T02:30:00Z"), DigestSchedule.latestSlot(Instant.parse("2026-10-13T02:00:00Z"), ist, 8))
        assertEquals(Instant.parse("2026-10-13T02:30:00Z"), DigestSchedule.latestSlot(Instant.parse("2026-10-13T03:30:00Z"), ist, 8))
        val user = UUID.randomUUID()
        val token = UnsubscribeTokens.sign("secret", user)
        assertTrue(UnsubscribeTokens.verify("secret", user, token))
        assertFalse(UnsubscribeTokens.verify("secret", UUID.randomUUID(), token), "a token only works for its own user")
        assertFalse(UnsubscribeTokens.verify("other", user, token))
    }
}
