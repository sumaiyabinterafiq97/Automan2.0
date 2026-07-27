package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PurchaseDateParseUtilsTest {

    @Test
    fun parse_labelWithWeekday() {
        assertEquals(
            LocalDate.of(2026, 7, 4),
            PurchaseDateParseUtils.parseToLocalDate("July 4, 2026(Saturday)"),
        )
        assertEquals(
            LocalDate.of(2026, 7, 24),
            PurchaseDateParseUtils.parseToLocalDate("July 24, 2026(Friday)"),
        )
    }

    @Test
    fun parse_isoDate() {
        assertEquals(
            LocalDate.of(2026, 6, 27),
            PurchaseDateParseUtils.parseToLocalDate("2026-06-27"),
        )
    }

    @Test
    fun chronological_july4BeforeJuly24() {
        val early = PurchaseDateParseUtils.parseToLocalDate("July 4, 2026(Saturday)")
        val late = PurchaseDateParseUtils.parseToLocalDate("July 24, 2026(Friday)")
        assertNotNull(early)
        assertNotNull(late)
        assertTrue(early!! < late!!)
    }

    @Test
    fun chronological_juneBeforeJuly() {
        val june = PurchaseDateParseUtils.parseToLocalDate("June 29, 2026(Monday)")
        val july = PurchaseDateParseUtils.parseToLocalDate("July 4, 2026(Saturday)")
        assertNotNull(june)
        assertNotNull(july)
        assertTrue(june!! < july!!)
    }
}
