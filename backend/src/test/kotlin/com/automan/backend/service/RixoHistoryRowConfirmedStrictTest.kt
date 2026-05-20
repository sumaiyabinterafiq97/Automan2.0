package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

class RixoHistoryRowConfirmedStrictTest {

    private fun service(purchaseRepository: PurchaseRepository): RixoHistoryService {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val shippingHistoryService = mock(ShippingHistoryService::class.java)
        val invoiceHistoryService = mock(InvoiceHistoryService::class.java)
        return RixoHistoryService(
            rixoRepo,
            purchaseRepository,
            PurchaseWorkflowService(purchaseRepository),
            shippingHistoryService,
            invoiceHistoryService,
        )
    }

    @Test
    fun `blank chassis is not confirmed`() {
        val pr = mock(PurchaseRepository::class.java)
        val row = RixoHistory(id = 1L, chassis = "   ")
        assertFalse(service(pr).computeHistoryRowRixoConfirmedStrict(row))
    }

    @Test
    fun `segment with no matching purchase is not confirmed`() {
        val pr = mock(PurchaseRepository::class.java)
        `when`(pr.findByChassisToken("GHOST")).thenReturn(emptyList())
        val row = RixoHistory(id = 1L, chassis = "GHOST")
        assertFalse(service(pr).computeHistoryRowRixoConfirmedStrict(row))
    }

    @Test
    fun `single segment all matched purchases true is confirmed`() {
        val pr = mock(PurchaseRepository::class.java)
        val p = Purchase(id = 10L, chassis = "ZZE124", rixoConfirmed = "TRUE")
        `when`(pr.findByChassisToken("ZZE124")).thenReturn(listOf(p))
        `when`(pr.findById(10L)).thenReturn(Optional.of(p))
        val row = RixoHistory(id = 1L, chassis = "ZZE124")
        assertTrue(service(pr).computeHistoryRowRixoConfirmedStrict(row))
    }

    @Test
    fun `single segment matched purchase not true is not confirmed`() {
        val pr = mock(PurchaseRepository::class.java)
        val p = Purchase(id = 10L, chassis = "ZZE124", rixoConfirmed = "FALSE")
        `when`(pr.findByChassisToken("ZZE124")).thenReturn(listOf(p))
        `when`(pr.findById(10L)).thenReturn(Optional.of(p))
        val row = RixoHistory(id = 1L, chassis = "ZZE124")
        assertFalse(service(pr).computeHistoryRowRixoConfirmedStrict(row))
    }

    @Test
    fun `two segments require every segment to match`() {
        val pr = mock(PurchaseRepository::class.java)
        val p1 = Purchase(id = 1L, chassis = "A-1", rixoConfirmed = "TRUE")
        `when`(pr.findByChassisToken("A-1")).thenReturn(listOf(p1))
        `when`(pr.findByChassisToken("A")).thenReturn(listOf(p1))
        `when`(pr.findByChassisToken("MISS")).thenReturn(emptyList())
        `when`(pr.findById(1L)).thenReturn(Optional.of(p1))
        val row = RixoHistory(id = 1L, chassis = "A-1;MISS")
        assertFalse(service(pr).computeHistoryRowRixoConfirmedStrict(row))
    }

    @Test
    fun `two segments two purchases both must be true`() {
        val pr = mock(PurchaseRepository::class.java)
        val p1 = Purchase(id = 1L, chassis = "X", rixoConfirmed = "TRUE")
        val p2 = Purchase(id = 2L, chassis = "Y", rixoConfirmed = "FALSE")
        `when`(pr.findByChassisToken("X")).thenReturn(listOf(p1))
        `when`(pr.findByChassisToken("Y")).thenReturn(listOf(p2))
        `when`(pr.findById(1L)).thenReturn(Optional.of(p1))
        `when`(pr.findById(2L)).thenReturn(Optional.of(p2))
        val row = RixoHistory(id = 1L, chassis = "X;Y")
        assertFalse(service(pr).computeHistoryRowRixoConfirmedStrict(row))
    }
}
