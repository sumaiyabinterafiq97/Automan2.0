package com.automan.backend.service

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional

class PurchaseChassisHistorySyncServiceTest {

    private val purchaseRepository = mock(PurchaseRepository::class.java)
    private val rixoHistoryRepository = mock(RixoHistoryRepository::class.java)
    private val shippingHistoryRepository = mock(ShippingHistoryRepository::class.java)
    private val invoiceHistoryLineRepository = mock(InvoiceHistoryLineRepository::class.java)
    private val invoiceHistoryRepository = mock(InvoiceHistoryRepository::class.java)

    private val service = PurchaseChassisHistorySyncService(
        purchaseRepository,
        rixoHistoryRepository,
        shippingHistoryRepository,
        invoiceHistoryLineRepository,
        invoiceHistoryRepository,
    )

    @Test
    fun preview_listsRixoShippingAndInvoiceWhenSingleOwner() {
        val purchase = Purchase(
            id = 1L,
            chassis = "OLD-111",
            date = "2026-10-16",
            rixoCompany = "KLC",
        )
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("OLD-111")).thenReturn(listOf(purchase))
        `when`(purchaseRepository.findByChassisToken("OLD-111")).thenReturn(listOf(purchase))

        val rixo = RixoHistory(
            id = 10L,
            buyingDate = LocalDate.of(2026, 10, 16),
            rixoCompany = "KLC",
            chassis = "OLD-111;OTHER-2",
        )
        `when`(rixoHistoryRepository.findChassisContaining(anyString())).thenReturn(listOf(rixo))

        val shipping = ShippingHistory(id = 20L, chassis = "OLD-111", clientName = "Client", vessel = "V1")
        `when`(shippingHistoryRepository.findChassisContaining(anyString())).thenReturn(listOf(shipping))
        `when`(shippingHistoryRepository.findFirstByChassisOrderByIdDesc("NEW-999")).thenReturn(null)

        val line = InvoiceHistoryLine(id = 30L, invoiceHistoryId = 5L, chassis = "OLD-111", sortOrder = 0)
        `when`(invoiceHistoryLineRepository.findByNormalizedChassis("OLD-111")).thenReturn(listOf(line))
        `when`(invoiceHistoryRepository.findById(5L)).thenReturn(
            Optional.of(InvoiceHistory(id = 5L, invoiceNumber = "INV-1")),
        )

        val preview = service.preview(1L, "NEW-999", purchase)

        assertEquals(1, preview.rixo.size)
        assertEquals(1, preview.shipping.size)
        assertEquals(1, preview.invoice.size)
        assertTrue(preview.canApply)
    }

    @Test
    fun preview_skipsWhenMultiplePurchasesShareOldChassis() {
        val purchase = Purchase(id = 1L, chassis = "OLD-111")
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("OLD-111")).thenReturn(
            listOf(purchase, Purchase(id = 2L, chassis = "OLD-111")),
        )

        val preview = service.preview(1L, "NEW-999", purchase)

        assertTrue(preview.rixo.isEmpty())
        assertTrue(preview.skippedReason != null)
        verify(rixoHistoryRepository, never()).findChassisContaining(anyString())
    }
}
