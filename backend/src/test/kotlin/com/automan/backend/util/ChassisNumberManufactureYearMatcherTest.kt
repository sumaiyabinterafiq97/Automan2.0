package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChassisNumberManufactureYearMatcherTest {

    @Test
    fun exactMatch_caseInsensitive() {
        val pairs = "67H:2019;t6yg:2020"
        assertEquals("2019", ChassisNumberManufactureYearMatcher.matchYear(pairs, "67h"))
        assertEquals("2020", ChassisNumberManufactureYearMatcher.matchYear(pairs, "T6YG"))
    }

    @Test
    fun rangeMatch_inclusiveNumeric() {
        val pairs = "187892~189709:2023;220002~258217:2023"
        assertEquals("2023", ChassisNumberManufactureYearMatcher.matchYear(pairs, "187892"))
        assertEquals("2023", ChassisNumberManufactureYearMatcher.matchYear(pairs, "189709"))
        assertEquals("2023", ChassisNumberManufactureYearMatcher.matchYear(pairs, "230000"))
        assertNull(ChassisNumberManufactureYearMatcher.matchYear(pairs, "189710"))
        assertNull(ChassisNumberManufactureYearMatcher.matchYear(pairs, "187891"))
    }

    @Test
    fun rangeMatch_leadingZeros() {
        val pairs = "000800~001200:2022"
        assertEquals("2022", ChassisNumberManufactureYearMatcher.matchYear(pairs, "000950"))
        assertEquals("2022", ChassisNumberManufactureYearMatcher.matchYear(pairs, "950"))
    }

    @Test
    fun firstMatchWins() {
        val pairs = "100~200:2020;150~250:2021"
        assertEquals("2020", ChassisNumberManufactureYearMatcher.matchYear(pairs, "180"))
    }

    @Test
    fun mixedExactAndRange() {
        val pairs = "67H:2019;187892~189709:2023"
        assertEquals("2019", ChassisNumberManufactureYearMatcher.matchYear(pairs, "67H"))
        assertEquals("2023", ChassisNumberManufactureYearMatcher.matchYear(pairs, "188000"))
    }

    @Test
    fun extractExactTokens_skipsRanges() {
        val pairs = "67H:2019;187892~189709:2023;t6yg:2020"
        assertEquals(
            listOf("67H", "t6yg"),
            ChassisNumberManufactureYearMatcher.extractExactTokens(pairs),
        )
    }
}
