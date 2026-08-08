package com.automan.backend.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ASCII-safe PDF download names: `{DocType}_{Key1}_{Key2}.pdf`
 * Matches frontend [buildPdfFilename] / [sanitizePdfFilenameToken].
 */
object PdfFilenameUtils {
    private val nonToken = Regex("[^A-Za-z0-9._-]+")
    private val multiUnderscore = Regex("_+")
    private val ymd = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun sanitizeToken(raw: String?, emptyFallback: String = "unknown"): String {
        val cleaned = (raw ?: "")
            .trim()
            .replace(nonToken, "_")
            .replace(multiUnderscore, "_")
            .trim('_')
        if (cleaned.isEmpty()) return emptyFallback
        return if (cleaned.length <= 40) cleaned else cleaned.take(40).trimEnd('_')
    }

    fun todayYmd(): String = LocalDate.now().format(ymd)

    /** Prefer yyyy-MM-dd or yyyyMMdd → YYYYMMDD; else today. */
    fun dateToken(raw: String?): String {
        val t = (raw ?: "").trim()
        if (t.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return t.replace("-", "")
        if (t.matches(Regex("^\\d{8}$"))) return t
        return todayYmd()
    }

    fun build(docType: String, vararg parts: String?): String {
        val tokens = mutableListOf(sanitizeToken(docType, "Document"))
        for (p in parts) {
            val t = (p ?: "").trim()
            if (t.isEmpty()) continue
            tokens.add(sanitizeToken(t, "unknown"))
        }
        return tokens.joinToString("_") + ".pdf"
    }

    fun contentDisposition(filename: String): String =
        "attachment; filename=\"$filename\""
}
