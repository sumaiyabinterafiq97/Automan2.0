package com.automan.backend.service

import com.automan.backend.model.StockLocationMap
import com.automan.backend.repository.StockLocationMapRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.Optional

class StockLocationMapServiceTest {

    @Test
    fun normalizePol_splitsTrimsDistinctJoins() {
        assertEquals(
            "YOKOHAMA; NAGOYA; OSAKA",
            StockLocationMapService.normalizePol("YOKOHAMA; NAGOYA, yokohama\nOSAKA"),
        )
        assertEquals("YOKOHAMA; OSAKA", StockLocationMapService.normalizePol("  YOKOHAMA ; OSAKA ; yokohama "))
        assertNull(StockLocationMapService.normalizePol("  ; , \n "))
        assertNull(StockLocationMapService.normalizePol(null))
        assertNull(StockLocationMapService.normalizePol(""))
    }

    @Test
    fun normalizeAddress_blankBecomesNull() {
        assertNull(StockLocationMapService.normalizeAddress("   "))
        assertNull(StockLocationMapService.normalizeAddress(null))
        assertEquals("1 Yard Rd", StockLocationMapService.normalizeAddress("  1 Yard Rd  "))
    }

    @Test
    fun create_rejectsDuplicateStockLocationIgnoreCase() {
        val repo = Mockito.mock(StockLocationMapRepository::class.java)
        `when`(repo.findByStockLocationIgnoreCase("klc")).thenReturn(
            StockLocationMap(id = 1L, stockLocation = "KLC", pol = "YOKOHAMA", address = null),
        )
        val svc = StockLocationMapService(repo)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            svc.create("klc", "OSAKA", "addr")
        }
        assertTrue(ex.message!!.contains("already exists"))
    }

    @Test
    fun create_requiresStockLocation() {
        val repo = Mockito.mock(StockLocationMapRepository::class.java)
        val svc = StockLocationMapService(repo)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            svc.create("  ", "YOKOHAMA", null)
        }
        assertTrue(ex.message!!.contains("required"))
    }

    @Test
    fun update_rejectsDuplicateStockOnOtherRow() {
        val repo = Mockito.mock(StockLocationMapRepository::class.java)
        val existing = StockLocationMap(id = 2L, stockLocation = "AQUA", pol = null, address = null)
        `when`(repo.findById(2L)).thenReturn(Optional.of(existing))
        `when`(repo.findByStockLocationIgnoreCase("KLC")).thenReturn(
            StockLocationMap(id = 1L, stockLocation = "KLC", pol = null, address = null),
        )
        val svc = StockLocationMapService(repo)
        val ex = assertThrows(IllegalArgumentException::class.java) {
            svc.update(2L, "KLC", "YOKOHAMA", null)
        }
        assertTrue(ex.message!!.contains("already exists"))
    }
}
