package com.applycopilot.matching

/**
 * What a job description says about visa sponsorship and relocation, read from its own words.
 * Most descriptions say nothing, so null ("not stated") is the common answer. A clear "no" wins over a "yes"
 * in the same text, since boilerplate like "we sponsor visas for some roles" often sits beside the real rule.
 */
object Sponsorship {
    const val OFFERED = "offered"
    const val NOT_OFFERED = "not_offered"

    private val no = listOf(
        "(?:no|not|unable to|cannot|can ?not|can't|do not|don't|does not|doesn't|will not|won't|are not able to|is not able to)\\s+(?:currently\\s+)?(?:provide|offer|support|sponsor)(?:ing)?\\s+(?:any\\s+)?(?:work\\s+)?(?:visa|immigration|sponsorship)",
        "(?:visa\\s+)?sponsorship\\s+(?:is\\s+)?(?:not|isn't)\\s+(?:available|offered|provided|possible)",
        "no\\s+(?:visa\\s+)?sponsorship",
        "without\\s+(?:the\\s+need\\s+for\\s+)?(?:current\\s+or\\s+future\\s+)?(?:visa\\s+)?sponsorship",
        "must\\s+(?:already\\s+)?(?:have|hold|possess)\\s+(?:the\\s+|a\\s+|full\\s+|valid\\s+)*(?:right|authori[sz]ation|permission|eligibility)\\s+to\\s+work",
        "(?:applicants|candidates)\\s+must\\s+be\\s+(?:legally\\s+)?(?:eligible|authori[sz]ed)\\s+to\\s+work",
        "not\\s+(?:able|in a position)\\s+to\\s+sponsor",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val yes = listOf(
        "visa\\s+sponsorship\\s+(?:is\\s+)?(?:available|offered|provided|possible|supported)",
        "(?:we|company)\\s+(?:can|will|do|offer to|are able to|is able to)\\s+(?:provide\\s+|offer\\s+|support\\s+)?(?:visa\\s+)?sponsor(?:ship)?",
        "(?:offer|offering|provide|providing|including|includes|with|support for)\\s+(?:full\\s+)?(?:work\\s+)?visa\\s+(?:sponsorship|support)",
        "sponsorship\\s+(?:is\\s+)?available",
        "(?:eu\\s+)?blue\\s+card\\s+(?:sponsorship|support)",
        "(?:highly\\s+skilled\\s+migrant|kennismigrant)",
        "(?:will|can)\\s+sponsor\\s+(?:your\\s+|a\\s+)?(?:work\\s+)?(?:visa|permit)",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val relocationWords = Regex(
        "relocation\\s+(?:support|assistance|package|bonus|allowance|budget|help|costs?)|(?:help|support|assist)\\s+(?:you\\s+)?(?:with\\s+)?(?:your\\s+)?relocat|relocation\\s+(?:is\\s+)?(?:provided|offered|available|covered)",
        RegexOption.IGNORE_CASE,
    )
    private val noRelocation = Regex("no\\s+relocation|relocation\\s+(?:is\\s+)?not\\s+(?:provided|offered|available|supported)", RegexOption.IGNORE_CASE)

    /** [OFFERED], [NOT_OFFERED], or null when the text doesn't say. */
    fun visa(text: String): String? = when {
        no.any { it.containsMatchIn(text) } -> NOT_OFFERED
        yes.any { it.containsMatchIn(text) } -> OFFERED
        else -> null
    }

    fun relocation(text: String): Boolean = !noRelocation.containsMatchIn(text) && relocationWords.containsMatchIn(text)
}
