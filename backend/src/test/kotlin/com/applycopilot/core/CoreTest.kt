package com.applycopilot.core

import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {

    private val resume = """
        Oamprakash R
        Lead Android Developer
        Chennai, India | oam@example.com | +91 98765 43210
        linkedin.com/in/oamprakash

        Summary
        Android lead with 12+ years building Kotlin apps for millions of users.

        Work Experience
        Lead Android Developer at Movate    Jul 2020 – Present
        • Lead a team of 20+ Android engineers across 4 apps
        • Built Jetpack Compose UI with a shared design system
          reused across every product team

        Senior Android Developer | Acme Mobile   Jan 2016 - Jun 2020
        - Shipped a payments SDK used by 2M users
        - Cut crash rate by 40% with Crashlytics triage

        Android Developer
        Beta Soft
        2013 - 2015
        • Built offline sync with Room and WorkManager

        Education
        B.E. in Computer Science, Anna University, 2012

        Skills
        Kotlin, Java, Coroutines, MVVM | Firebase
        Tools: Android Studio, Git
    """.trimIndent()

    private val parser = HeuristicResumeParser(today = { YearMonth.of(2026, 10) })

    // ---- Heuristic parser -----------------------------------------------------------------

    @Test
    fun parsesHeaderFields() {
        val p = parser.parse(resume)
        assertEquals("Oamprakash R", p.fullName)
        assertEquals("oam@example.com", p.email)
        assertEquals("+91 98765 43210", p.phone)
        assertEquals("Chennai", p.location)
        assertEquals(12.0, p.totalExperienceYears)
        assertTrue(p.links.contains("linkedin.com/in/oamprakash"), "links: ${p.links}")
        assertTrue(p.summary!!.startsWith("Android lead with 12+ years"))
    }

    @Test
    fun parsesExperienceInThreeLayouts() {
        val e = parser.parse(resume).experience
        assertEquals(3, e.size, "experience: $e")

        assertEquals("Lead Android Developer", e[0].title)
        assertEquals("Movate", e[0].company)
        assertEquals("2020-07", e[0].startDate)
        assertTrue(e[0].current)
        assertNull(e[0].endDate)
        assertEquals(2, e[0].bullets.size, "bullets: ${e[0].bullets}")
        assertEquals("Built Jetpack Compose UI with a shared design system reused across every product team", e[0].bullets[1])

        assertEquals("Senior Android Developer", e[1].title)
        assertEquals("Acme Mobile", e[1].company)
        assertEquals("2016-01", e[1].startDate)
        assertEquals("2020-06", e[1].endDate)
        assertEquals(2, e[1].bullets.size, "bullets: ${e[1].bullets}")

        assertEquals("Android Developer", e[2].title)
        assertEquals("Beta Soft", e[2].company)
        assertEquals("2013", e[2].startDate)
        assertEquals("2015", e[2].endDate)
        assertEquals(listOf("Built offline sync with Room and WorkManager"), e[2].bullets)
    }

    @Test
    fun headlineComesFromLatestTitle() {
        assertEquals("Lead Android Developer", parser.parse(resume).headline)
    }

    @Test
    fun parsesEducation() {
        val ed = parser.parse(resume).education
        assertEquals(1, ed.size, "education: $ed")
        assertEquals("Anna University", ed[0].institution)
        assertEquals("B.E.", ed[0].degree)
        assertEquals("Computer Science", ed[0].field)
        assertEquals(2012, ed[0].endYear)
    }

    @Test
    fun parsesSkillsFromSectionAndDictionary() {
        val skills = parser.parse(resume).skills
        for (s in listOf("Kotlin", "Java", "Coroutines", "MVVM", "Firebase", "Android Studio", "Git", "Jetpack Compose", "Room", "WorkManager", "Crashlytics")) {
            assertTrue(skills.any { it.equals(s, ignoreCase = true) }, "missing $s in $skills")
        }
        assertEquals(skills.size, skills.distinctBy { it.lowercase() }.size, "duplicates in $skills")
        assertFalse(skills.any { it.startsWith("Tools") }, "label leaked into skills: $skills")
    }

    @Test
    fun computesYearsWhenNotStated() {
        val text = """
            Asha K
            Experience
            Android Developer at Zeta   Jan 2020 - Present
        """.trimIndent()
        assertEquals(6.8, parser.parse(text).totalExperienceYears)
    }

    @Test
    fun ignoresSingleWordSkillsInLowerCaseProse() {
        val text = "Priya S\nSummary\nThere is room to rest and go for a walk in spring."
        val skills = parser.parse(text).skills
        assertFalse(skills.any { it in setOf("Room", "REST", "Spring") }, "false positives: $skills")
    }

    @Test
    fun normalizesDates() {
        assertEquals("2020-07", HeuristicResumeParser.normalizeDate("Jul 2020"))
        assertEquals("2020-07", HeuristicResumeParser.normalizeDate("July 2020"))
        assertEquals("2020-07", HeuristicResumeParser.normalizeDate("07/2020"))
        assertEquals("2020-07", HeuristicResumeParser.normalizeDate("2020-07"))
        assertEquals("2020", HeuristicResumeParser.normalizeDate("2020"))
        assertEquals("2019-09", HeuristicResumeParser.normalizeDate("Sept 2019"))
        assertNull(HeuristicResumeParser.normalizeDate("someday"))
    }

    // ---- Grounding guardrail --------------------------------------------------------------

    @Test
    fun groundingKeepsWhatIsInTheResume() {
        val parsed = parser.parse(resume)
        val result = Grounding.ground(parsed, resume)
        assertEquals(parsed, result.profile, "removed: ${result.removed}")
        assertTrue(result.removed.isEmpty())
    }

    @Test
    fun groundingRemovesInventedClaims() {
        val invented = parser.parse(resume).let { p ->
            p.copy(
                skills = p.skills + listOf("Kubernetes", ".NET"),
                experience = p.experience + Experience(title = "CTO", company = "Google", bullets = listOf("Led everything")),
                education = p.education + Education(institution = "Stanford University", degree = "MS"),
            )
        }
        val result = Grounding.ground(invented, resume + "\nWorked on the internet.")
        val g = result.profile
        assertFalse("Kubernetes" in g.skills)
        assertFalse(".NET" in g.skills, ".NET must not match 'internet'")
        assertFalse(g.experience.any { it.company == "Google" })
        assertFalse(g.education.any { it.institution.startsWith("Stanford") })
        assertEquals(4, result.removed.size, "removed: ${result.removed}")
    }

    @Test
    fun groundingDropsRewrittenBulletsAndUnknownTitles() {
        val p = Profile(
            fullName = "Oamprakash R",
            experience = listOf(
                Experience(
                    title = "Principal Engineer",
                    company = "Movate",
                    bullets = listOf("Lead a team of 20+ Android engineers", "Grew revenue tenfold through blockchain strategy"),
                ),
            ),
        )
        val g = Grounding.ground(p, resume).profile
        assertEquals("", g.experience[0].title)
        assertEquals(listOf("Lead a team of 20+ Android engineers"), g.experience[0].bullets)
    }

    @Test
    fun groundingChecksPhoneByDigits() {
        val g = Grounding.ground(Profile(fullName = "Oamprakash R", phone = "9876543210"), resume).profile
        assertEquals("9876543210", g.phone)
        val bad = Grounding.ground(Profile(fullName = "Oamprakash R", phone = "9000000000"), resume).profile
        assertNull(bad.phone)
    }

    // ---- Validation -----------------------------------------------------------------------

    @Test
    fun normalizeTrimsDedupesAndFixesLinks() {
        val p = ProfileRules.normalize(
            Profile(
                fullName = "  Oam   R ",
                skills = listOf("Kotlin", " kotlin ", "", "Java"),
                links = listOf("github.com/oam", "https://x.dev", ""),
                experience = listOf(Experience(title = "Dev", company = "A", current = true, endDate = "2020", bullets = listOf(" a ", ""))),
            ),
        )
        assertEquals("Oam R", p.fullName)
        assertEquals(listOf("Kotlin", "Java"), p.skills)
        assertEquals(listOf("https://github.com/oam", "https://x.dev"), p.links)
        assertNull(p.experience[0].endDate)
        assertEquals(listOf("a"), p.experience[0].bullets)
    }

    @Test
    fun validationReportsProblems() {
        val problems = ProfileRules.validate(
            Profile(
                fullName = "",
                email = "not-an-email",
                experience = listOf(Experience(title = "Dev", company = "", startDate = "2020-05", endDate = "2019-01")),
            ),
        )
        assertTrue("Full name is required" in problems)
        assertTrue("Email address is not valid" in problems)
        assertTrue("Experience 1: company is required" in problems)
        assertTrue("Experience 1: end date is before start date" in problems)
    }

    @Test
    fun validationAllowsYearOnlyEndInSameYear() {
        val ok = ProfileRules.validate(
            Profile(fullName = "A", experience = listOf(Experience(title = "Dev", company = "A", startDate = "2020-05", endDate = "2020"))),
        )
        assertTrue(ok.isEmpty(), "problems: $ok")
    }

    @Test
    fun preferencesBounds() {
        val problems = PreferenceRules.validate(Preferences(minCtcLpa = -1.0, noticePeriodDays = 400, experienceYears = 70.0))
        assertEquals(3, problems.size, "problems: $problems")
        val normalized = PreferenceRules.normalize(Preferences(roles = listOf(" Android Lead ", "android lead", "")))
        assertEquals(listOf("Android Lead"), normalized.roles)
    }

    @Test
    fun emailNormalization() {
        assertEquals("oam@example.com", Emails.normalize("  Oam@Example.COM "))
        assertNull(Emails.normalize("oam@example"))
        assertNull(Emails.normalize("o am@example.com"))
    }

    // ---- Tokens ---------------------------------------------------------------------------

    @Test
    fun tokensAreRandomAndHashesStable() {
        val a = Tokens.newToken()
        val b = Tokens.newToken()
        assertEquals(43, a.length)
        assertNotEquals(a, b)
        assertEquals(Tokens.sha256Hex(a), Tokens.sha256Hex(a))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Tokens.sha256Hex("abc"))
    }
}
