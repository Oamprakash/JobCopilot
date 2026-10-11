package com.applycopilot.resume

import com.applycopilot.api.ApiException
import io.ktor.http.HttpStatusCode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.ByteArrayInputStream

enum class ResumeFormat(val contentType: String, val extension: String) {
    PDF("application/pdf", "pdf"),
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
}

data class ExtractedResume(val format: ResumeFormat, val text: String)

object TextExtractor {
    const val MAX_BYTES = 5 * 1024 * 1024
    private const val MAX_CHARS = 50_000
    private const val MIN_CHARS = 80

    /** Detects the format from the file's bytes (never its name) and returns its plain text. */
    fun extract(bytes: ByteArray): ExtractedResume {
        if (bytes.isEmpty()) throw ApiException(HttpStatusCode.BadRequest, "The file is empty")
        if (bytes.size > MAX_BYTES) throw ApiException(HttpStatusCode.PayloadTooLarge, "Resumes must be 5 MB or smaller")

        val format = detect(bytes)
            ?: throw ApiException(HttpStatusCode.UnsupportedMediaType, "Upload a PDF or Word (.docx) file")

        val text = try {
            when (format) {
                ResumeFormat.PDF -> Loader.loadPDF(bytes).use { doc ->
                    if (doc.isEncrypted) throw ApiException(HttpStatusCode.UnprocessableEntity, "This PDF is password-protected")
                    PDFTextStripper().getText(doc)
                }
                // Closing the document is enough; the extractor only wraps it.
                ResumeFormat.DOCX -> XWPFDocument(ByteArrayInputStream(bytes)).use { doc -> XWPFWordExtractor(doc).text }
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException(HttpStatusCode.UnprocessableEntity, "We couldn't open this file. Try saving it again as PDF or DOCX.")
        }

        val cleaned = text.replace("\r\n", "\n").replace('\r', '\n').replace(Regex("\n{3,}"), "\n\n").trim().take(MAX_CHARS)
        if (cleaned.length < MIN_CHARS) {
            throw ApiException(
                HttpStatusCode.UnprocessableEntity,
                "We couldn't read text from this file. It may be a scanned image; upload a text-based PDF or DOCX.",
            )
        }
        return ExtractedResume(format, cleaned)
    }

    fun detect(bytes: ByteArray): ResumeFormat? = when {
        bytes.startsWith("%PDF-".toByteArray()) -> ResumeFormat.PDF
        bytes.startsWith(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) -> ResumeFormat.DOCX
        else -> null
    }

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
