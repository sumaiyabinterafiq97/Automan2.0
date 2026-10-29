package com.automan.backend.util

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
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

    /**
     * Loose SQL LIKE pattern for English month/day/year labels (e.g. `June%29%2026`).
     * Used as a DB prefilter only; [parseToLocalDate] remains authoritative.
     */
    fun sqlLabelDateCandidatePattern(targetDate: LocalDate): String {
        val month = targetDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "$month%${targetDate.dayOfMonth}%${targetDate.year}"
    }

    /** Loose month+year LIKE fragment for range prefilters (e.g. `%June%2026%`). */
    fun sqlLabelMonthYearLikePattern(yearMonth: YearMonth): String {
        val month = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return "%$month%${yearMonth.year}%"
    }

    /** Inclusive calendar months touched by [from]..[to]. */
    fun yearMonthsInInclusiveRange(from: LocalDate, to: LocalDate): List<YearMonth> {
        val start = YearMonth.from(from)
        val end = YearMonth.from(to)
        val out = mutableListOf<YearMonth>()
        var ym = start
        while (!ym.isAfter(end)) {
            out.add(ym)
            ym = ym.plusMonths(1)
        }
        return out
    }
}
