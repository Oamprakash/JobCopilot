package com.applycopilot.core

import kotlinx.serialization.Serializable

/**
 * How someone first found the site, as the browser saw it on their first visit: the campaign tags in the link
 * (utm_source, utm_medium, utm_campaign), the site that linked to us (host only), and the page they landed on
 * (path only). Nothing else about the visit is kept.
 */
@Serializable
data class Acquisition(
    val source: String? = null,
    val medium: String? = null,
    val campaign: String? = null,
    val referrer: String? = null,
    val landing: String? = null,
)

object AcquisitionRules {
    private val tag = Regex("[^a-z0-9._+-]")
    private val hostChars = Regex("^[a-z0-9.-]+$")

    /** Referring hosts we name, longest match first. Anything else is filed under its own domain. */
    private val known = listOf(
        "instagram.com" to "instagram",
        "facebook.com" to "facebook",
        "fb.me" to "facebook",
        "reddit.com" to "reddit",
        "redd.it" to "reddit",
        "linkedin.com" to "linkedin",
        "lnkd.in" to "linkedin",
        "t.co" to "x",
        "x.com" to "x",
        "twitter.com" to "x",
        "news.ycombinator.com" to "hackernews",
        "youtube.com" to "youtube",
        "whatsapp.com" to "whatsapp",
        "wa.me" to "whatsapp",
        "t.me" to "telegram",
        "quora.com" to "quora",
        "teamblind.com" to "blind",
        "producthunt.com" to "producthunt",
        "bing.com" to "bing",
        "duckduckgo.com" to "duckduckgo",
        "chatgpt.com" to "chatgpt",
        "claude.ai" to "claude",
        "perplexity.ai" to "perplexity",
    )

    /** Cleans what the browser sent and fills in [Acquisition.source]: the utm tag, else the referring site, else "direct". */
    fun normalize(raw: Acquisition, ownHosts: Set<String> = emptySet()): Acquisition {
        val referrer = host(raw.referrer)?.takeUnless { h -> ownHosts.any { h == it || h.endsWith(".$it") } }
        val source = cleanTag(raw.source) ?: referrer?.let(::sourceFromHost) ?: "direct"
        return Acquisition(
            source = source,
            medium = cleanTag(raw.medium),
            campaign = cleanTag(raw.campaign),
            referrer = referrer,
            landing = raw.landing?.substringBefore('?')?.substringBefore('#')?.trim()
                ?.takeIf { it.startsWith("/") && !it.startsWith("//") }?.take(100),
        )
    }

    /** "l.instagram.com" → "instagram", "www.google.co.in" → "google", "blog.example.org" → "example.org". */
    fun sourceFromHost(host: String): String {
        val h = host.lowercase().removePrefix("www.")
        known.firstOrNull { (domain, _) -> h == domain || h.endsWith(".$domain") }?.let { return it.second }
        if (Regex("(^|\\.)google\\.[a-z.]+$").containsMatchIn(h)) return "google"
        if (Regex("(^|\\.)yahoo\\.[a-z.]+$").containsMatchIn(h)) return "yahoo"
        val parts = h.split('.')
        // Keep the registrable part: two labels, or three for "co.in"-style endings.
        val keep = if (parts.size >= 3 && parts[parts.size - 2].length <= 3 && parts.last().length == 2) 3 else 2
        return parts.takeLast(keep).joinToString(".").take(60)
    }

    private fun cleanTag(s: String?): String? =
        s?.trim()?.lowercase()?.replace(' ', '_')?.replace(tag, "")?.take(60)?.ifEmpty { null }

    /** A bare host from either a host or a full URL; null for anything that isn't one. */
    private fun host(s: String?): String? {
        val t = s?.trim()?.lowercase()?.ifEmpty { null } ?: return null
        val h = t.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@').substringBefore(':')
        return h.takeIf { it.length in 3..100 && hostChars.matches(it) && it.contains('.') }
    }
}
