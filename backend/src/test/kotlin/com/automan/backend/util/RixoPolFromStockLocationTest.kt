package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Stock yard → POL hub from the documented map in [RixoPolFromStockLocation].
 */
class RixoPolFromStockLocationTest {

    @Test
    fun mapsKnownYardsAndJoinsMultiplePortsInOrder() {
        assertEquals("YOKOHAMA", RixoPolFromStockLocation.derivePol("global kawasaki"))
        assertEquals("NAGOYA", RixoPolFromStockLocation.derivePol("GLOBAL NAGOYA"))
        assertEquals("NAGOYA", RixoPolFromStockLocation.derivePol("FLASHRISE"))
        assertEquals("HAKATA", RixoPolFromStockLocation.derivePol("GLOBAL HAKATA"))
        assertEquals("KOBE", RixoPolFromStockLocation.derivePol("ECL KOBE"))
        assertEquals("OSAKA;SENBOKU;KOBE", RixoPolFromStockLocation.derivePol("KLC"))
    }

    @Test
    fun unmappedBlankOrPlaceholderStockHasNoPol() {
        assertNull(RixoPolFromStockLocation.derivePol(""))
        assertNull(RixoPolFromStockLocation.derivePol("-"))
        assertNull(RixoPolFromStockLocation.derivePol("BARAKI PARKING"))
        assertNull(RixoPolFromStockLocation.derivePol("LOCAL"))
        assertNull(RixoPolFromStockLocation.derivePol("UNKNOWN YARD"))
    }

    @Test
    fun combinesSemicolonOrCommaStocksAndDedupesPorts() {
        assertEquals(
            "YOKOHAMA;NAGOYA",
            RixoPolFromStockLocation.derivePol("GLOBAL KAWASAKI; GLOBAL NAGOYA"),
        )
        assertEquals(
            "YOKOHAMA",
            RixoPolFromStockLocation.derivePol("GLOBAL KAWASAKI, AQUA LOGISTICS"),
        )
    }
}
