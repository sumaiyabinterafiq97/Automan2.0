package com.automan.backend.service

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.repository.ClientMapRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class InvoiceHistoryServiceDeleteLedgerTest {

    @Mock private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Mock private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Mock private lateinit var purchaseService: PurchaseService
    @Mock private lateinit var pdfService: PdfService
    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var clientService: ClientService
    @Mock private lateinit var eventService: EventService
    @Mock private lateinit var shippingHistoryService: ShippingHistoryService
    @Mock private lateinit var bookingMappingService: BookingMappingService
    @Mock private lateinit var clientMapRepository: ClientMapRepository

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
}
