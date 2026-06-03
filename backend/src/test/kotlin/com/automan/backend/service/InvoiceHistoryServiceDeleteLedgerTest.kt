package com.automan.backend.service

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class InvoiceHistoryServiceDeleteLedgerTest {

    @Mock private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Mock private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Mock private lateinit var purchaseService: PurchaseService
    @Mock private lateinit var pdfService: PdfService
    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var clientService: ClientService
    @Mock private lateinit var eventService: EventService

    private lateinit var service: InvoiceHistoryService

    @BeforeEach
    fun setUp() {
        service = InvoiceHistoryService(
            invoiceHistoryRepository = invoiceHistoryRepository,
            invoiceHistoryLineRepository = invoiceHistoryLineRepository,
            purchaseService = purchaseService,
            pdfService = pdfService,
            clientRepository = clientRepository,
            clientService = clientService,
            eventService = eventService,
        )
    }

    @Test
    fun `deleteByInvoiceNumbers reverses the active ledger client before deleting history`() {
        val header = InvoiceHistory(
            id = 10L,
            invoiceNumber = "INV-900",
            vessel = "PACIFIC STAR",
            clientName = "Ambiguous Client",
            shippingDate = LocalDate.of(2026, 5, 20),
        )
        val lines = listOf(
            InvoiceHistoryLine(invoiceHistoryId = 10L, chassis = "ABC123", lineAmount = "¥100,000"),
        )
        `when`(invoiceHistoryRepository.findAllByInvoiceNumberIn(listOf("INV-900"))).thenReturn(listOf(header))
        `when`(invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(10L)).thenReturn(lines)
        `when`(eventService.findActiveInvoiceLedgerClientIds("INV-900")).thenReturn(listOf(77L))
        `when`(invoiceHistoryLineRepository.countByNormalizedChassis("ABC123")).thenReturn(0L)

        val deleted = service.deleteByInvoiceNumbers(listOf("INV-900"))

        assertEquals(1, deleted)
        verify(eventService).reverseActiveInvoiceLedger(
            clientId = 77L,
            invoiceNumber = "INV-900",
            eventDate = LocalDate.of(2026, 5, 20),
            vessel = "PACIFIC STAR",
        )
        verify(invoiceHistoryLineRepository).deleteByInvoiceHistoryId(10L)
        verify(invoiceHistoryRepository).deleteAll(listOf(header))
        verify(purchaseService).unmarkInvoiceConfirmedForChassis(listOf("ABC123"))
    }

    @Test
    fun `deleteByInvoiceNumbers cancels delete when active ledger clients are ambiguous`() {
        val header = InvoiceHistory(id = 10L, invoiceNumber = "INV-901")
        `when`(invoiceHistoryRepository.findAllByInvoiceNumberIn(listOf("INV-901"))).thenReturn(listOf(header))
        `when`(invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(10L)).thenReturn(emptyList())
        `when`(eventService.findActiveInvoiceLedgerClientIds("INV-901")).thenReturn(listOf(77L, 88L))

        assertThrows(IllegalStateException::class.java) {
            service.deleteByInvoiceNumbers(listOf("INV-901"))
        }

        verify(eventService, never()).reverseActiveInvoiceLedger(
            anyLong(),
            anyString(),
            any(LocalDate::class.java),
            nullable(String::class.java),
        )
        verify(invoiceHistoryLineRepository, never()).deleteByInvoiceHistoryId(10L)
        verify(invoiceHistoryRepository, never()).deleteAll(listOf(header))
    }
}
