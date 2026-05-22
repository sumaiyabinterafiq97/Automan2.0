package com.automan.backend.service

import com.automan.backend.dto.ClientNameLedgerResolution
import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceItem
import com.automan.backend.dto.InvoiceLedgerResult
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional

class InvoiceHistoryServiceTest {

    @Test
    fun saveOnly_zeroTotalInvoiceStillSyncsLedgerSoExistingChargeIsReversed() {
        val invoiceHistoryRepository = mock(InvoiceHistoryRepository::class.java)
        val invoiceHistoryLineRepository = mock(InvoiceHistoryLineRepository::class.java)
        val purchaseService = mock(PurchaseService::class.java)
        val pdfService = mock(PdfService::class.java)
        val clientRepository = mock(ClientRepository::class.java)
        val clientService = mock(ClientService::class.java)
        val eventService = mock(EventService::class.java)
        val service = InvoiceHistoryService(
            invoiceHistoryRepository,
            invoiceHistoryLineRepository,
            purchaseService,
            pdfService,
            clientRepository,
            clientService,
            eventService,
        )

        `when`(invoiceHistoryRepository.findByInvoiceNumber("INV-ZERO")).thenReturn(Optional.empty())
        `when`(invoiceHistoryRepository.save(any(InvoiceHistory::class.java))).thenAnswer { inv ->
            val header = inv.arguments[0] as InvoiceHistory
            header.copy(id = 11L)
        }
        `when`(invoiceHistoryLineRepository.save(any(InvoiceHistoryLine::class.java))).thenAnswer { inv ->
            inv.arguments[0] as InvoiceHistoryLine
        }
        `when`(clientService.resolveClientNameForLedger("Crown Eagle")).thenReturn(
            ClientNameLedgerResolution.Ok(clientId = 7L, created = false),
        )
        `when`(
            eventService.syncInvoiceLedger(
                7L,
                "INV-ZERO",
                LocalDate.of(2026, 5, 20),
                0.0,
                1,
                "PACIFIC STAR",
            ),
        ).thenReturn(
            InvoiceLedgerResult(
                reversed = true,
                clientId = 7L,
                warning = "Invoice total is zero; any open ledger charge was reversed.",
            ),
        )

        val request = InvoiceConfirmAndDownloadRequest(
            purchaseIds = emptyList(),
            chassisJoined = "ABC-123",
            shippingDateIso = "2026-05-20",
            pdf = InvoicePdfRequest(
                invoiceNumber = "INV-ZERO",
                invoiceDate = "2026-05-21",
                lcNumber = null,
                clientName = "Crown Eagle",
                clientAddress = null,
                vessel = "PACIFIC STAR",
                shippingDate = "2026-05-20",
                from = "YOKOHAMA",
                to = "CHITTAGONG",
                priceType = "C&F",
                items = listOf(InvoiceItem(unit = 1, description = "ABC-123", amount = "¥0")),
                totalAmount = "¥0",
                bankAccount = null,
                message = null,
            ),
        )

        val result = service.saveOnly(request)

        assertTrue(result.reversed)
        assertEquals(7L, result.clientId)
        verify(eventService).syncInvoiceLedger(
            7L,
            "INV-ZERO",
            LocalDate.of(2026, 5, 20),
            0.0,
            1,
            "PACIFIC STAR",
        )
    }
}
