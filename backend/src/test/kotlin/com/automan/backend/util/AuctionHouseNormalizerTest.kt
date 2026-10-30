package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class AuctionHouseNormalizerTest {

    @Test
    fun normalize_nullBecomesEmpty() {
        assertEquals("", AuctionHouseNormalizer.normalize(null))
    }

    @Test
    fun normalize_emptyAndSpacesOnly() {
        assertEquals("", AuctionHouseNormalizer.normalize(""))
        assertEquals("", AuctionHouseNormalizer.normalize("   "))
    }

    @Test
    fun normalize_asciiCaseAndRegularSpaces() {
        assertEquals("JU AICHI", AuctionHouseNormalizer.normalize("  ju aichi  "))
        assertEquals("USS NAGOYA", AuctionHouseNormalizer.normalize("USS NAGOYA"))
    }

    @Test
    fun normalize_tabEdgesNotTrimmedLikeMysql() {
        val withTabs = "\tJU AICHI\t"
        assertEquals("\tJU AICHI\t".uppercase(), AuctionHouseNormalizer.normalize(withTabs))
        assertNotEquals("JU AICHI", AuctionHouseNormalizer.normalize(withTabs))
    }

    @Test
    fun normalize_nbspEdgesNotTrimmedLikeMysql() {
        val nbsp = "\u00A0JU AICHI\u00A0"
        assertEquals(nbsp.uppercase(), AuctionHouseNormalizer.normalize(nbsp))
    }

    @Test
    fun normalize_semicolonWholeString() {
        assertEquals("JU AICHI; USS NAGOYA", AuctionHouseNormalizer.normalize("  ju aichi; uss nagoya  "))
    }

    @Test
    fun normalize_unicodeEszettMatchesMysqlUpper() {
        assertEquals("ß", AuctionHouseNormalizer.normalize("ß"))
    }

    @Test
    fun mysqlSpaceTrim_matchesAsciiSpacesOnly() {
        assertEquals("ab", AuctionHouseNormalizer.mysqlSpaceTrim("  ab  "))
        assertEquals("\tab", AuctionHouseNormalizer.mysqlSpaceTrim("\tab  "))
    }
}
