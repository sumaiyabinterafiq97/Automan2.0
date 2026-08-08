package com.automan.backend.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure helpers for venue uniqueness — mirrors [RixoMappingService] venue resolution logic
 * without needing a Spring context (logic kept in sync via shared object).
 */
class RixoMappingVenueRulesTest {
    @Test
    fun uniqueVenueWhenExactlyOne() {
        assertEquals("22431", RixoMappingVenueRules.resolveUnique(listOf("22431", "22431", null, "")))
    }

    @Test
    fun nullWhenZeroOrMany() {
        assertNull(RixoMappingVenueRules.resolveUnique(emptyList()))
        assertNull(RixoMappingVenueRules.resolveUnique(listOf(null, "")))
        assertNull(RixoMappingVenueRules.resolveUnique(listOf("A", "B")))
    }

    @Test
    fun allowSameVenueRejectDifferent() {
        assertNull(RixoMappingVenueRules.rejectSecondVenue(listOf("22431"), "22431"))
        assertNull(RixoMappingVenueRules.rejectSecondVenue(emptyList(), "22431"))
        val err = RixoMappingVenueRules.rejectSecondVenue(listOf("22431"), "99999")
        assertTrue(err != null && err.contains("22431"))
    }

    @Test
    fun conflictReportGroups() {
        val rows = listOf(
            "ARAI" to "1",
            "ARAI" to "2",
            "OTHER" to "9",
            "OTHER" to "9",
            "BLANK" to null,
        )
        val conflicts = RixoMappingVenueRules.conflictSuppliers(rows)
        assertEquals(1, conflicts.size)
        assertEquals("ARAI", conflicts[0].auctionName)
        assertEquals(listOf("1", "2"), conflicts[0].venues.sorted())
    }
}
