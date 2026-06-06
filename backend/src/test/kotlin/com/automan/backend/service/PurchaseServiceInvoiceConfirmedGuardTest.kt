package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class PurchaseServiceInvoiceConfirmedGuardTest {

    private val purchaseRepository = mock(PurchaseRepository::class.java)
    private val pdfService = mock(PdfService::class.java)
    private val bookingMappingRepository = mock(BookingMappingRepository::class.java)
    private val shippingHistoryRepository = mock(ShippingHistoryRepository::class.java)
    private val purchaseWorkflowService = mock(PurchaseWorkflowService::class.java)
    private val purchaseChangeHistoryService = mock(PurchaseChangeHistoryService::class.java)
    private val clientRepository = mock(ClientRepository::class.java)
    private val invoiceHistoryLineRepository = mock(InvoiceHistoryLineRepository::class.java)
    private val service = PurchaseService(
        purchaseRepository,
        pdfService,
        bookingMappingRepository,
        shippingHistoryRepository,
        purchaseWorkflowService,
        purchaseChangeHistoryService,
        clientRepository,
        invoiceHistoryLineRepository,
    )

    @Test
    fun updatePurchase_rejectsClearingInvoiceConfirmedWhenInvoiceHistoryStillReferencesChassis() {
        val existing = Purchase(id = 1L, chassis = "ABC-123", invoiceConfirmed = true)
        `when`(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing))
        `when`(invoiceHistoryLineRepository.countByNormalizedChassis("ABC-123")).thenReturn(1L)

        assertThrows(IllegalArgumentException::class.java) {
            service.updatePurchase(1L, Purchase(chassis = "ABC-123", invoiceConfirmed = false))
        }

        verify(purchaseRepository, never()).save(any(Purchase::class.java))
    }

    @Test
    fun updatePurchasePartial_rejectsClearingInvoiceConfirmedWhenInvoiceHistoryStillReferencesChassis() {
        val existing = Purchase(id = 1L, chassis = "ABC-123", invoiceConfirmed = true)
        `when`(purchaseRepository.findById(1L)).thenReturn(Optional.of(existing))
        `when`(invoiceHistoryLineRepository.countByNormalizedChassis("ABC-123")).thenReturn(1L)

        assertThrows(IllegalArgumentException::class.java) {
            service.updatePurchasePartial(1L, mapOf("invoiceConfirmed" to false))
        }

        verify(purchaseRepository, never()).saveAndFlush(any(Purchase::class.java))
    }
}
