package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

/**
 * Ensures batched [RixoHistoryService.listAllRows] preserves flag semantics without N+1 lookups.
 */
class RixoHistoryListAllRowsBatchTest {

    private fun service(
        rixoRepo: RixoHistoryRepository,
        purchaseRepo: PurchaseRepository,
    ): RixoHistoryService {
        return RixoHistoryService(
            rixoRepo,
            purchaseRepo,
            PurchaseWorkflowService(purchaseRepo),
            mock(ShippingHistoryService::class.java),
            mock(InvoiceHistoryService::class.java),
        )
    }

    @Test
    fun `listAllRows computes confirmed booking and date from one purchases load`() {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val purchaseRepo = mock(PurchaseRepository::class.java)
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val ts = LocalDateTime.of(2026, 7, 1, 12, 0, 0)
        val p1 = Purchase(id = 1L, chassis = "X-1", rixoConfirmed = "TRUE", bookingRequested = false, updatedAt = ts)
        val p2 = Purchase(id = 2L, chassis = "Y-2", rixoConfirmed = "TRUE", bookingRequested = true, updatedAt = ts.plusHours(1))
        val row = RixoHistory(id = 10L, chassis = "X-1;Y-2", rixoCompany = "Y'S")

        `when`(rixoRepo.findAll(sort)).thenReturn(listOf(row))
        `when`(purchaseRepo.findAll()).thenReturn(listOf(p1, p2))

        val dto = service(rixoRepo, purchaseRepo).listAllRows().single()
        assertTrue(dto.rixoConfirmed)
        assertTrue(dto.hasBookingRequested)
        assertEquals(ts.plusHours(1).toString(), dto.rixoConfirmedDate)
    }

    @Test
    fun `listAllRows not confirmed when any segment missing`() {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val purchaseRepo = mock(PurchaseRepository::class.java)
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val p1 = Purchase(id = 1L, chassis = "X", rixoConfirmed = "TRUE")
        val row = RixoHistory(id = 10L, chassis = "X;MISS")

        `when`(rixoRepo.findAll(sort)).thenReturn(listOf(row))
        `when`(purchaseRepo.findAll()).thenReturn(listOf(p1))

        val dto = service(rixoRepo, purchaseRepo).listAllRows().single()
        assertFalse(dto.rixoConfirmed)
        assertFalse(dto.hasBookingRequested)
    }
}
