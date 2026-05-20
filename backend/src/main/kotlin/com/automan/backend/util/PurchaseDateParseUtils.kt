package com.automan.backend.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Parse stored purchase "date" labels (e.g. "April24, 2026(Thursday)" or ISO yyyy-MM-dd) to [LocalDate].
 * Matches the formats used in the front-end [toIsoFromLabel] / formatWithWeekday flow.
 */
object PurchaseDateParseUtils {
    private val monthDayYearNoSpace: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMMd, uuuu", Locale.ENGLISH)
    private val monthDayYearSpace: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH)

    fun parseToLocalDate(raw: String): LocalDate? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            return try {
                LocalDate.parse(t, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
        val base = t.replace(Regex("\\(.*?\\)"), "").trim()
        if (base.isEmpty()) return null
        return try {
            LocalDate.parse(base, monthDayYearNoSpace)
        } catch (e: DateTimeParseException) {
            try {
                LocalDate.parse(base, monthDayYearSpace)
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }
}
