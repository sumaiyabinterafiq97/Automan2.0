package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarModelYearUtilsTest {

    @Test
    fun extractYear_fromIsoMonth() {
        assertEquals("2026", CarModelYearUtils.extractYearFromCarModelYear("2026-05"))
    }

    @Test
    fun extractYear_fromMmYyyy() {
        assertEquals("2026", CarModelYearUtils.extractYearFromCarModelYear("05/2026"))
    }

    @Test
    fun extractYear_fromMonthName() {
        assertEquals("2026", CarModelYearUtils.extractYearFromCarModelYear("May 2026"))
    }

    @Test
    fun extractYear_blankReturnsEmpty() {
        assertEquals("", CarModelYearUtils.extractYearFromCarModelYear(null))
        assertEquals("", CarModelYearUtils.extractYearFromCarModelYear("   "))
    }

    @Test
    fun displayYear_prefersCarModelYear() {
        assertEquals("2026", CarModelYearUtils.displayYear("2026-05", "2018"))
    }

    @Test
    fun displayYear_fallsBackToManufactureYear() {
        assertEquals("2020", CarModelYearUtils.displayYear(null, "2020"))
        assertEquals("2019", CarModelYearUtils.displayYear("  ", "2019"))
    }

    @Test
    fun displayYear_bothBlankReturnsEmpty() {
        assertEquals("", CarModelYearUtils.displayYear(null, null))
    }
}
