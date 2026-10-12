package com.applycopilot.tailoring.pdf

import com.applycopilot.tailoring.TailoredResume
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream

/** A plain, ATS-friendly one-column resume: real text (not an image), standard fonts, no tables. */
object ResumePdf {
    private val regular = PDType1Font(Standard14Fonts.FontName.HELVETICA)
    private val bold = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
    private const val MARGIN = 50f
    private val ink = floatArrayOf(0.08f, 0.13f, 0.24f)
    private val muted = floatArrayOf(0.36f, 0.39f, 0.47f)

    fun render(resume: TailoredResume): ByteArray = PDDocument().use { doc ->
        val writer = Writer(doc)
        writer.text(resume.fullName, bold, 20f, ink, after = 4f)
        writer.text(resume.headline, regular, 12f, ink, after = 4f)
        writer.text(listOfNotNull(resume.email, resume.phone, resume.location).filter { it.isNotBlank() }.joinToString("  ·  "), regular, 9.5f, muted, after = 2f)
        if (resume.links.isNotEmpty()) writer.text(resume.links.joinToString("  ·  "), regular, 9.5f, muted, after = 2f)

        resume.summary?.takeIf { it.isNotBlank() }?.let {
            writer.heading("Summary")
            writer.text(it, regular, 10.5f, ink)
        }
        if (resume.skills.isNotEmpty()) {
            writer.heading("Skills")
            writer.text(resume.skills.joinToString(" · "), regular, 10.5f, ink)
        }
        if (resume.experience.isNotEmpty()) {
            writer.heading("Experience")
            resume.experience.forEach { e ->
                writer.text("${e.title} · ${e.company}", bold, 11f, ink, after = 1f)
                val dates = listOfNotNull(e.startDate, if (e.current) "Present" else e.endDate).joinToString(" – ")
                if (dates.isNotBlank()) writer.text(dates, regular, 9.5f, muted, after = 3f)
                e.bullets.forEach { writer.text(it.text, regular, 10.5f, ink, after = 2f, bullet = true) }
                writer.space(6f)
            }
        }
        if (resume.education.isNotEmpty()) {
            writer.heading("Education")
            resume.education.forEach { ed ->
                val line = listOfNotNull(ed.degree, ed.field?.let { "in $it" }).joinToString(" ")
                writer.text(listOf(line, ed.institution).filter { it.isNotBlank() }.joinToString(", ") + (ed.endYear?.let { " · $it" } ?: ""), regular, 10.5f, ink)
            }
        }
        writer.close()
        ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
    }

    private class Writer(private val doc: PDDocument) {
        private val page = PDRectangle.A4
        private val width = page.width - 2 * MARGIN
        private var stream: PDPageContentStream? = null
        private var y = 0f

        private fun ensure(height: Float) {
            if (stream == null || y - height < MARGIN) {
                stream?.close()
                val p = PDPage(page)
                doc.addPage(p)
                stream = PDPageContentStream(doc, p)
                y = page.height - MARGIN
            }
        }

        fun space(h: Float) { y -= h }

        fun heading(title: String) {
            space(10f)
            text(title.uppercase(), bold, 10f, muted, after = 2f)
            ensure(4f)
            stream!!.apply {
                setStrokingColor(0.85f, 0.87f, 0.9f)
                setLineWidth(0.6f)
                moveTo(MARGIN, y + 2f)
                lineTo(MARGIN + width, y + 2f)
                stroke()
            }
            space(6f)
        }

        fun text(raw: String, font: PDType1Font, size: Float, color: FloatArray, after: Float = 4f, bullet: Boolean = false) {
            val content = safe(raw, font)
            if (content.isBlank()) return
            val indent = if (bullet) 12f else 0f
            val leading = size * 1.35f
            wrap(content, font, size, width - indent).forEachIndexed { i, line ->
                ensure(leading)
                y -= leading
                stream!!.apply {
                    setNonStrokingColor(color[0], color[1], color[2])
                    if (bullet && i == 0) {
                        beginText(); setFont(font, size); newLineAtOffset(MARGIN, y); showText("•"); endText()
                    }
                    beginText(); setFont(font, size); newLineAtOffset(MARGIN + indent, y); showText(line); endText()
                }
            }
            space(after)
        }

        fun close() {
            ensure(0f)
            stream?.close()
        }

        private fun wrap(text: String, font: PDType1Font, size: Float, maxWidth: Float): List<String> {
            fun w(s: String) = font.getStringWidth(s) / 1000f * size
            val lines = mutableListOf<String>()
            var line = ""
            for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (w(candidate) <= maxWidth) line = candidate
                else {
                    if (line.isNotEmpty()) lines += line
                    line = word
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines
        }

        /** Standard fonts only cover WinAnsi; anything else becomes a close ASCII stand-in or is dropped. */
        private fun safe(text: String, font: PDType1Font): String = buildString {
            for (ch in text.replace("₹", "Rs ")) {
                val s = ch.toString()
                val ok = runCatching { font.encode(s) }.isSuccess
                append(if (ok) s else if (ch.isWhitespace()) " " else "")
            }
        }
    }
}
