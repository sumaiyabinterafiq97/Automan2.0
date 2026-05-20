package com.automan.backend.util

/** Normalizes stored car_model_year for display as a calendar year only (e.g. "2015-02" → "2015"). */
object CarModelYearUtils {

    /** Extracts only the 4-digit year from car_model_year (e.g. "July 2026" → "2026", "2025-07" → "2025"). */
    fun extractYearFromCarModelYear(yearStr: String?): String {
        if (yearStr == null || yearStr.isBlank()) return ""
        val t = yearStr.trim()
        if (t.contains("-")) {
            val parts = t.split("-")
            if (parts.isNotEmpty()) {
                val y = parts[0].trim()
                if (y.length == 4 && y.all { it.isDigit() }) return y
            }
        }
        if (t.contains("/")) {
            val parts = t.split("/")
            if (parts.size >= 2) {
                val y = parts[1].trim()
                if (y.length == 4 && y.all { it.isDigit() }) return y
            }
        }
        val tokens = t.split(Regex("\\s+"))
        for (token in tokens.reversed()) {
            if (token.length == 4 && token.all { it.isDigit() }) return token
        }
        if (t.length == 4 && t.all { it.isDigit() }) return t
        return t
    }
}
