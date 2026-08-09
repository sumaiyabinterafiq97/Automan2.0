package com.automan.backend.service

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.never
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class InvoiceHistoryServiceDeleteLedgerTest {

    @Mock private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Mock private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Mock private lateinit var purchaseService: PurchaseService
    @Mock private lateinit var pdfService: PdfService
    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var clientService: ClientService
    @Mock private lateinit var eventService: EventService

    @InjectMocks private lateinit var invoiceHistoryService: InvoiceHistoryService

    @Test
    fun `delete returns ledger warning when client cannot be resolved`() {
        val header = InvoiceHistory(
            id = 11L,
            invoiceNumber = "INV-X",
            clientName = "UNKNOWN CLIENT",
        )
        val lines = listOf(
            InvoiceHistoryLine(id = 3L, invoiceHistoryId = 11L, chassis = "ABC123", sortOrder = 0),
        )

        `when`(invoiceHistoryRepository.findAllByInvoiceNumberIn(listOf("INV-X")))
            .thenReturn(listOf(header))
        `when`(invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(11L))
            .thenReturn(lines)
        `when`(clientRepository.findByClientNameIgnoreCase("UNKNOWN CLIENT"))
            .thenReturn(emptyList())
        `when`(purchaseService.getPurchaseByChassis("ABC123")).thenReturn(null)
        `when`(invoiceHistoryLineRepository.countByNormalizedChassis("ABC123")).thenReturn(0L)

        val result = invoiceHistoryService.deleteByInvoiceNumbers(listOf("INV-X"))

        assertEquals(1, result.deleted)
        assertEquals(0, result.ledgerReversed)
        assertTrue(result.ledgerWarnings.any { it.contains("could not resolve client") })
    }

    @Test
    fun `delete reverses only deleted invoice ledger even when other open invoices share chassis`() {
        val ledgerDate = LocalDate.of(2026, 6, 7)
        val header = InvoiceHistory(
            id = 11L,
            invoiceNumber = "INV-OLD",
            clientName = "ACME",
            vessel = "VESSEL-1",
            shippingDate = ledgerDate,
        )
        val siblingHeader = InvoiceHistory(
            id = 12L,
            invoiceNumber = "INV-NEW",
            clientName = "ACME",
        )
        val lines = listOf(
            InvoiceHistoryLine(id = 3L, invoiceHistoryId = 11L, chassis = "ABC123", sortOrder = 0),
        )
        val siblingLines = listOf(
            InvoiceHistoryLine(id = 4L, invoiceHistoryId = 12L, chassis = "ABC123", sortOrder = 0),
        )
        val client = Client(id = 42L, clientNumber = "CL0042", clientName = "ACME")
        val reversal = Event(
            id = 99L,
            clientId = 42L,
            eventDate = ledgerDate,
            eventType = EventType.INVOICE_REVERSAL,
            paymentReceived = 500_000.0,
            runningBalance = 0.0,
        )

        `when`(invoiceHistoryRepository.findAllByInvoiceNumberIn(listOf("INV-OLD")))
            .thenReturn(listOf(header))
        `when`(invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(11L))
            .thenReturn(lines)
        `when`(clientRepository.findByClientNameIgnoreCase("ACME")).thenReturn(listOf(client))
        `when`(
            eventService.reverseActiveInvoiceLedger(
                42L,
                "INV-OLD",
                ledgerDate,
                "VESSEL-1",
            ),
        ).thenReturn(reversal)
        `when`(invoiceHistoryLineRepository.countByNormalizedChassis("ABC123")).thenReturn(1L)

        // These lenient stubs describe the pre-fix corruption path: shared-chassis and orphan
        // open invoice numbers would have been collected and reversed too.
        lenient()
            .`when`(invoiceHistoryLineRepository.findDistinctInvoiceNumbersByNormalizedChassisIn(setOf("abc123")))
            .thenReturn(listOf("INV-OLD", "INV-NEW"))
        lenient().`when`(eventService.findOpenInvoiceNumbers(42L))
            .thenReturn(listOf("INV-OLD", "INV-NEW", "INV-ORPHAN"))
        lenient().`when`(invoiceHistoryRepository.findByInvoiceNumber("INV-NEW"))
            .thenReturn(Optional.of(siblingHeader))
        lenient().`when`(invoiceHistoryRepository.findByInvoiceNumber("INV-ORPHAN"))
            .thenReturn(Optional.empty())
        lenient().`when`(invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(12L))
            .thenReturn(siblingLines)

        val result = invoiceHistoryService.deleteByInvoiceNumbers(listOf("INV-OLD"))

        assertEquals(1, result.deleted)
        assertEquals(1, result.ledgerReversed)
        verify(eventService).reverseActiveInvoiceLedger(42L, "INV-OLD", ledgerDate, "VESSEL-1")
        verify(eventService, never()).reverseActiveInvoiceLedger(42L, "INV-NEW", ledgerDate, "VESSEL-1")
        verify(eventService, never()).reverseActiveInvoiceLedger(42L, "INV-ORPHAN", ledgerDate, "VESSEL-1")
    }
}
