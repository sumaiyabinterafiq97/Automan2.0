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
}
