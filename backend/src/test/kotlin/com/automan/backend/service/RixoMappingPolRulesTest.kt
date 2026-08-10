package com.automan.backend.service

import com.automan.backend.util.RixoPolFromStockLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure helpers for POL uniqueness — mirrors [RixoMappingService] POL resolution logic
 * without needing a Spring context.
 */
class RixoMappingPolRulesTest {
    @Test
    fun uniquePolWhenExactlyOne() {
        assertEquals("YOKOHAMA", RixoMappingPolRules.resolveUnique(listOf("YOKOHAMA", "YOKOHAMA", null, "")))
    }

    @Test
    fun nullWhenZeroOrMany() {
        assertNull(RixoMappingPolRules.resolveUnique(emptyList()))
        assertNull(RixoMappingPolRules.resolveUnique(listOf(null, "")))
        assertNull(RixoMappingPolRules.resolveUnique(listOf("YOKOHAMA", "OSAKA")))
    }

    @Test
    fun allowSamePolRejectDifferent() {
        assertNull(RixoMappingPolRules.rejectSecondPol(listOf("YOKOHAMA"), "YOKOHAMA"))
        assertNull(RixoMappingPolRules.rejectSecondPol(emptyList(), "YOKOHAMA"))
        val err = RixoMappingPolRules.rejectSecondPol(listOf("YOKOHAMA"), "OSAKA")
        assertTrue(err != null && err.contains("YOKOHAMA"))
    }

    @Test
    fun conflictReportGroups() {
        val rows = listOf(
            "AQUA LOGISTICS" to "YOKOHAMA",
            "AQUA LOGISTICS" to "OSAKA",
            "KLC" to "OSAKA",
            "KLC" to "OSAKA",
            "LOCAL" to null,
            "-" to "X",
        )
        val conflicts = RixoMappingPolRules.conflictStocks(rows)
        assertEquals(1, conflicts.size)
        assertEquals("AQUA LOGISTICS", conflicts[0].stockLocation)
        assertEquals(listOf("OSAKA", "YOKOHAMA"), conflicts[0].pols.sorted())
    }

    /** Mirrors coalescePolWithUniqueForStock(stock, null) when DB has blanks + one YOKOHAMA. */
    @Test
    fun coalesceBlankWhenUniqueYokohama() {
        val existingPols = listOf(null, "", "YOKOHAMA", "YOKOHAMA", null)
        val explicit = RixoMappingPolRules.normalizePol(null)
        assertNull(explicit)
        assertEquals("YOKOHAMA", RixoMappingPolRules.resolveUnique(existingPols))
    }

    /** Global multi-POL stock → stock-only resolve is null. */
    @Test
    fun stockOnlyNullWhenGlobalMultiPol() {
        val globalAqua = listOf("HAKATA", "KOBE", "NAGOYA", "YOKOHAMA")
        assertNull(RixoMappingPolRules.resolveUnique(globalAqua))
    }

    /** Same stock+supplier with one YOKOHAMA → unique. */
    @Test
    fun stockAndAuctionUniqueYokohama() {
        val underArai = listOf(null, "", "YOKOHAMA", "YOKOHAMA")
        assertEquals("YOKOHAMA", RixoMappingPolRules.resolveUnique(underArai))
    }

    @Test
    fun singleTokenDerivePolFallbackForAqua() {
        assertEquals(
            "YOKOHAMA",
            RixoMappingPolRules.singleTokenDerivedPol("AQUA LOGISTICS") { RixoPolFromStockLocation.derivePol(it) },
        )
    }

    @Test
    fun multiTokenDerivePolSkippedForKlc() {
        assertNull(
            RixoMappingPolRules.singleTokenDerivedPol("KLC") { RixoPolFromStockLocation.derivePol(it) },
        )
    }

    /** Tiered coalesce mirror: auction scope wins before global / derive. */
    @Test
    fun tieredCoalescePrefersAuctionScope() {
        val explicit = RixoMappingPolRules.normalizePol(null)
        assertNull(explicit)
        val auctionScoped = RixoMappingPolRules.resolveUnique(listOf(null, "YOKOHAMA"))
        assertEquals("YOKOHAMA", auctionScoped)
        // Global would be null — auction path already answered.
        assertNull(RixoMappingPolRules.resolveUnique(listOf("HAKATA", "KOBE", "NAGOYA", "YOKOHAMA")))
    }
}
