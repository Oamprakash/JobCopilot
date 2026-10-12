package com.applycopilot.matching

/** Turns job-board HTML (including Greenhouse's entity-escaped HTML) into readable plain text. */
object Html {
    private val named = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "#39" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "bull" to "•", "middot" to "·", "trade" to "™", "reg" to "®", "copy" to "©",
    )
    private val entity = Regex("&(#x[0-9a-fA-F]+|#\\d+|[a-zA-Z]+);")

    fun decodeEntities(s: String): String = entity.replace(s) { m ->
        val code = m.groupValues[1]
        when {
            code.startsWith("#x") || code.startsWith("#X") -> code.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            code.startsWith("#") -> code.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> named[code]
        } ?: m.value
    }

    fun toText(html: String): String {
        // Greenhouse escapes its HTML once ("&lt;p&gt;"); decode first so the tags become real tags.
        var s = if (html.contains("&lt;")) decodeEntities(html) else html
        s = s.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<li[^>]*>"), "\n• ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|h[1-6]|li|ul|ol|tr|section)>"), "\n")
        s = s.replace(Regex("<[^>]+>"), " ")
        s = decodeEntities(s)
        return s.lines()
            .map { it.replace(Regex("[ \\t\\u00a0]+"), " ").trim() }
            .filter { it.isNotEmpty() && it != "•" }
            .joinToString("\n")
    }
}
