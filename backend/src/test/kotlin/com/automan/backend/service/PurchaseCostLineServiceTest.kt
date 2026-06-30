package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseCostLine
import com.automan.backend.repository.PurchaseCostLineRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal

class PurchaseCostLineServiceTest {

    private val repository = mock(PurchaseCostLineRepository::class.java)
    private val service = PurchaseCostLineService(repository)

    @Test
    fun parseMoneyString_strips_yen_and_commas() {
        assertEquals(BigDecimal("1234.56"), PurchaseCostLineService.parseMoneyString("¥1,234.56"))
    }

    @Test
    fun syncFromPurchase_writes_cost_lines_for_populated_fields() {
        `when`(repository.findByPurchaseIdOrderBySortOrderAsc(1L)).thenReturn(emptyList())
        val purchase = Purchase(
            id = 1L,
            chassis = "TEST-001",
            price = "100000",
            auctionFee = "5000",
            profit = BigDecimal("2500.00"),
        )
        service.syncFromPurchase(purchase)
        verify(repository).saveAll(anyList())
    }

    @Test
    fun syncFromPurchase_updates_existing_line_instead_of_inserting_duplicate() {
        `when`(repository.findByPurchaseIdOrderBySortOrderAsc(1L)).thenReturn(
            listOf(
                PurchaseCostLine(
                    id = 99L,
                    purchaseId = 1L,
                    costCode = "PRICE",
                    amount = BigDecimal.ONE,
                    sortOrder = 1,
                ),
            ),
        )
        val purchase = Purchase(id = 1L, chassis = "TEST-001", price = "200000")
        service.syncFromPurchase(purchase)
        verify(repository).saveAll(anyList())
    }

    @Test
    fun applyForRead_clears_stale_session_cost_fields_when_no_lines() {
        `when`(repository.findByPurchaseIdOrderBySortOrderAsc(1L)).thenReturn(emptyList())
        val purchase = Purchase(id = 1L, chassis = "X", price = "99999", freight = "1")
        val merged = service.applyForRead(purchase)
        assertEquals(null, merged.price)
        assertEquals(null, merged.freight)
    }

    @Test
    fun applyForRead_hydrates_cost_fields_from_lines() {
        `when`(repository.findByPurchaseIdOrderBySortOrderAsc(1L)).thenReturn(
            listOf(
                PurchaseCostLine(purchaseId = 1L, costCode = "PRICE", amount = BigDecimal("75000"), sortOrder = 1),
                PurchaseCostLine(purchaseId = 1L, costCode = "FREIGHT", amount = BigDecimal("1200"), sortOrder = 9),
                PurchaseCostLine(purchaseId = 1L, costCode = "PROFIT", amount = BigDecimal("500"), sortOrder = 16),
            ),
        )
        val purchase = Purchase(id = 1L, chassis = "X")
        val merged = service.applyForRead(purchase)
        assertEquals("75000", merged.price)
        assertEquals("1200", merged.freight)
        assertEquals(BigDecimal("500"), merged.profit)
    }

    @Test
    fun buildCostsByChassisApiMap_prefers_lines_over_transient_fields() {
        `when`(repository.findByPurchaseIdOrderBySortOrderAsc(1L)).thenReturn(
            listOf(
                com.automan.backend.model.PurchaseCostLine(
                    purchaseId = 1L,
                    costCode = "PRICE",
                    amount = BigDecimal("999"),
                    sortOrder = 1,
                ),
            ),
        )
        val purchase = Purchase(id = 1L, chassis = "X", price = "100")
        val map = service.buildCostsByChassisApiMap(purchase)
        assertEquals(BigDecimal("999"), map["carPrice"])
    }

    @Test
    fun buildCostsByChassisApiMap_returns_zero_when_no_lines() {
        `when`(repository.findByPurchaseIdOrderBySortOrderAsc(1L)).thenReturn(emptyList())
        val purchase = Purchase(id = 1L, chassis = "X", price = "5000", freight = "100")
        val map = service.buildCostsByChassisApiMap(purchase)
        assertEquals(BigDecimal.ZERO, map["carPrice"])
        assertEquals(BigDecimal.ZERO, map["freight"])
    }
}
