package com.applycopilot.resume

import com.applycopilot.core.Grounding
import com.applycopilot.core.HeuristicResumeParser
import com.applycopilot.core.Profile
import com.applycopilot.core.ProfileRules
import org.slf4j.LoggerFactory

data class ParseOutcome(
    val profile: Profile,
    /** What the grounding guardrail removed, shown to the user on the review screen. */
    val removed: List<String>,
    /** "claude" or "heuristic", so the UI and logs show which parser ran. */
    val parser: String,
)

class ResumeParsingService(
    private val claude: ClaudeResumeParser?,
    private val heuristic: HeuristicResumeParser = HeuristicResumeParser(),
) {
    private val log = LoggerFactory.getLogger(ResumeParsingService::class.java)

    suspend fun parse(text: String): ParseOutcome {
        val (raw, parser) = claude?.let {
            try {
                it.parse(text) to "claude"
            } catch (e: Exception) {
                // Never block onboarding on the LLM: fall back and let the user fix the fields.
                log.warn("Claude resume parsing failed, using heuristic parser", e)
                null
            }
        } ?: (heuristic.parse(text) to "heuristic")

        val grounded = Grounding.ground(ProfileRules.normalize(raw), text)
        return ParseOutcome(ProfileRules.normalize(grounded.profile), grounded.removed, parser)
    }
}
