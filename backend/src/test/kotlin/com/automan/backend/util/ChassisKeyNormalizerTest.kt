package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChassisKeyNormalizerTest {

    @Test
    fun normalize_trimsAndLowercases() {
        assertEquals("nze141-1234567", ChassisKeyNormalizer.normalize("  NZE141-1234567  "))
    }

    @Test
    fun normalize_nullOrBlankIsEmpty() {
        assertEquals("", ChassisKeyNormalizer.normalize(null))
        assertEquals("", ChassisKeyNormalizer.normalize("   "))
    }

    @Test
    fun normalizeUpperDisplayKey_matchesHistoryTokenCase() {
        assertEquals("AAA", ChassisKeyNormalizer.normalizeUpperDisplayKey(" aaa "))
    }

    @Test
    fun normalize_collapsesCaseOnlyDifference() {
        val a = ChassisKeyNormalizer.normalize("AbC-1")
        val b = ChassisKeyNormalizer.normalize("abc-1")
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
    }
}
