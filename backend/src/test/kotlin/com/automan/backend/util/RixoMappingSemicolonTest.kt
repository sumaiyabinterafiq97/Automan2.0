package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RixoMappingSemicolonTest {

    @Test
    fun normalizesUnicodeSemicolonsAndDetectsThem() {
        assertEquals("A;B", RixoMappingSemicolon.normalizeSemicolons("A\uFF1BB"))
        assertEquals("A;B", RixoMappingSemicolon.normalizeSemicolons("A\uFE55B"))
        assertEquals("", RixoMappingSemicolon.normalizeSemicolons(null))
        assertTrue(RixoMappingSemicolon.containsSemicolon("A;B"))
        assertTrue(RixoMappingSemicolon.containsSemicolon(" A\uFF1BB "))
        assertFalse(RixoMappingSemicolon.containsSemicolon(null))
        assertFalse(RixoMappingSemicolon.containsSemicolon("  "))
        assertFalse(RixoMappingSemicolon.containsSemicolon("YOKOHAMA"))
    }

    @Test
    fun splitTokensTrimsAndDropsBlanks() {
        assertEquals(listOf("YOKOHAMA", "NAGOYA"), RixoMappingSemicolon.splitTokens(" YOKOHAMA ; ; NAGOYA "))
        assertEquals(emptyList<String>(), RixoMappingSemicolon.splitTokens(null))
        assertEquals(emptyList<String>(), RixoMappingSemicolon.splitTokens(" ; "))
    }
}
