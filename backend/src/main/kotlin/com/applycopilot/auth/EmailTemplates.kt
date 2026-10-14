package com.applycopilot.auth

import java.net.URI

/** A finished email: subject, HTML body and the plain-text alternative. */
data class EmailContent(val subject: String, val html: String, val text: String)

/**
 * The HTML for every email the app sends, on one shared layout: a white card on a light background with the
 * logo, a heading, the message, one button, and a footer. Built from tables with inline styles, which is
 * what email apps (Gmail, Outlook, Apple Mail) render reliably. Pure: no I/O, easy to test and preview.
 */
object EmailTemplates {
    private const val INK = "#14213d"
    private const val MUTED = "#5b6478"
    private const val LINE = "#e3e6ec"
    private const val PRIMARY = "#1f4fe0"
    private const val BG = "#f4f6fa"
    private const val FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"

    fun magicLink(appName: String, link: String): EmailContent {
        val site = siteOf(link)
        val body = heading("Sign in to $appName") +
            paragraph("Click the button below to sign in. The link works once and expires in 15 minutes.") +
            button("Sign in to $appName", link) +
            fallbackLink(link) +
            divider() +
            paragraph("Didn't ask for this? You can safely ignore this email. Nobody can sign in without the link.", small = true)
        return EmailContent(
            subject = "Your $appName sign-in link",
            html = layout(appName, site, preheader = "Your sign-in link. It expires in 15 minutes.", body = body,
                footer = "You're getting this because someone asked to sign in to $appName with this email address."),
            text = """
                Sign in to $appName

                Open this link to sign in. It works once and expires in 15 minutes:
                $link

                Didn't ask for this? You can safely ignore this email. Nobody can sign in without the link.
            """.trimIndent(),
        )
    }

    fun digest(appName: String, items: List<DigestItem>, total: Int, feedLink: String, unsubscribeLink: String): EmailContent {
        val site = siteOf(feedLink)
        val intro = if (total == 1) "One new job fits your profile today." else "$total new jobs fit your profile today. Here are the best ${items.size}."
        val rows = items.joinToString("") { jobRow(it) }
        val body = heading(if (total == 1) "A new match for you" else "Your new matches") +
            paragraph(intro) +
            """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border-collapse:separate;margin:8px 0 4px">$rows</table>""" +
            button(if (total > items.size) "See all $total matches" else "Open your matches", feedLink) +
            paragraph("Tailor an application in a couple of minutes. You review everything, and you always click Submit yourself.", small = true)
        return EmailContent(
            subject = digestSubject(total),
            html = layout(appName, site, preheader = intro, body = body,
                footer = """You get this at most once a day, only when there are new matches. """ +
                    """<a href="${attr(unsubscribeLink)}" style="color:$MUTED;text-decoration:underline">Unsubscribe</a> """ +
                    """or turn it off under Preferences."""),
            text = digestText(items, total, feedLink, unsubscribeLink),
        )
    }

    fun followUps(appName: String, items: List<Pair<String, String>>, trackerLink: String): EmailContent {
        val site = siteOf(trackerLink)
        val intro = if (items.size == 1) "One application is due a follow-up." else "${items.size} applications are due a follow-up."
        val rows = items.joinToString("") { (title, company) ->
            """<tr><td style="padding:12px 16px;border:1px solid $LINE;border-radius:10px;background:#ffffff">""" +
                """<div style="font:600 15px/1.4 $FONT;color:$INK">${esc(title)}</div>""" +
                """<div style="font:400 13px/1.5 $FONT;color:$MUTED">${esc(company)}</div></td></tr>""" +
                """<tr><td style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr>"""
        }
        val body = heading("Time to follow up") +
            paragraph(intro) +
            """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border-collapse:separate;margin:8px 0 4px">$rows</table>""" +
            paragraph("A short, polite note to the recruiter or hiring manager keeps you on their radar. Mention the role, one thing you'd bring, and that you're still keen.") +
            button("Open your tracker", trackerLink)
        return EmailContent(
            subject = if (items.size == 1) "Time to follow up: ${items[0].first} at ${items[0].second}" else "${items.size} applications to follow up on",
            html = layout(appName, site, preheader = intro, body = body,
                footer = "You set these reminders in your $appName tracker. Change or clear them there."),
            text = followUpText(items, trackerLink),
        )
    }

    // ---- Building blocks --------------------------------------------------------------------------------

    private fun layout(appName: String, site: String?, preheader: String, body: String, footer: String): String {
        val siteLink = site?.let { """<a href="${attr(it)}" style="color:$MUTED;text-decoration:none">${esc(URI(it).host)}</a>""" }.orEmpty()
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><meta name="supported-color-schemes" content="light"><title>${esc(appName)}</title>
<style>@media (max-width:480px){.card{padding:24px 20px 20px !important}.outer{padding:20px 12px !important}}</style></head>
<body style="margin:0;padding:0;background:$BG;-webkit-text-size-adjust:100%">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent">${esc(preheader)}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:$BG">
<tr><td align="center" class="outer" style="padding:32px 16px">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px">
<tr><td style="padding:0 4px 16px">
<table role="presentation" cellpadding="0" cellspacing="0"><tr>
<td width="28" height="28" align="center" valign="middle" style="width:28px;height:28px;background:$PRIMARY;border-radius:8px;color:#ffffff;font:700 16px/28px $FONT">&#10003;</td>
<td style="padding-left:10px;font:700 18px/28px $FONT;color:$INK">${esc(appName)}</td>
</tr></table>
</td></tr>
<tr><td class="card" style="background:#ffffff;border:1px solid $LINE;border-radius:14px;padding:32px 32px 28px">
$body
</td></tr>
<tr><td style="padding:20px 8px 0;font:400 12px/1.6 $FONT;color:$MUTED;text-align:center">
$footer<br>${esc(appName)}${if (siteLink.isNotEmpty()) " · $siteLink" else ""}
</td></tr>
</table>
</td></tr>
</table>
</body></html>"""
    }

    private fun heading(text: String) =
        """<h1 style="margin:0 0 12px;font:700 22px/1.3 $FONT;color:$INK;letter-spacing:-0.01em">${esc(text)}</h1>"""

    private fun paragraph(text: String, small: Boolean = false) =
        if (small) """<p style="margin:0 0 12px;font:400 13px/1.6 $FONT;color:$MUTED">${esc(text)}</p>"""
        else """<p style="margin:0 0 16px;font:400 15px/1.6 $FONT;color:$INK">${esc(text)}</p>"""

    /** A button that works in Outlook too: a table cell with the colour, the link inside it. */
    private fun button(label: String, href: String) = """
<table role="presentation" cellpadding="0" cellspacing="0" style="margin:8px 0 20px"><tr>
<td align="center" bgcolor="$PRIMARY" style="border-radius:8px;background:$PRIMARY">
<a href="${attr(href)}" target="_blank" style="display:inline-block;padding:13px 26px;font:600 15px/1 $FONT;color:#ffffff;text-decoration:none;border-radius:8px">${esc(label)}</a>
</td></tr></table>"""

    private fun fallbackLink(href: String) =
        """<p style="margin:0 0 20px;font:400 12px/1.6 $FONT;color:$MUTED">Button not working? Paste this link into your browser:<br>""" +
            """<a href="${attr(href)}" style="color:$PRIMARY;word-break:break-all">${esc(href)}</a></p>"""

    private fun divider() = """<div style="height:1px;background:$LINE;margin:4px 0 16px;line-height:1px;font-size:0">&nbsp;</div>"""

    private fun jobRow(job: DigestItem): String {
        val (fg, bg) = when {
            job.score >= 75 -> "#0e7a4f" to "#e3f5ec"
            job.score >= 55 -> "#8a5100" to "#fff3dc"
            else -> MUTED to "#f0f2f5"
        }
        val meta = listOf(job.company, job.location).filter { it.isNotBlank() }.joinToString(" · ")
        return """<tr><td style="padding:14px 16px;border:1px solid $LINE;border-radius:10px;background:#ffffff">""" +
            """<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr>""" +
            """<td valign="top"><a href="${attr(job.link)}" style="font:600 15px/1.4 $FONT;color:$PRIMARY;text-decoration:none">${esc(job.title)}</a>""" +
            """<div style="font:400 13px/1.5 $FONT;color:$MUTED;padding-top:2px">${esc(meta)}</div></td>""" +
            """<td valign="top" align="right" style="padding-left:12px;white-space:nowrap">""" +
            """<span style="display:inline-block;padding:3px 10px;border-radius:999px;background:$bg;color:$fg;font:700 12px/1.5 $FONT">${job.score} fit</span></td>""" +
            """</tr></table></td></tr>""" +
            """<tr><td style="height:8px;line-height:8px;font-size:0">&nbsp;</td></tr>"""
    }

    private fun siteOf(link: String): String? = runCatching {
        val u = URI(link)
        if (u.scheme == null || u.host == null) null else "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}"
    }.getOrNull()

    internal fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun attr(s: String) = esc(s)
}
