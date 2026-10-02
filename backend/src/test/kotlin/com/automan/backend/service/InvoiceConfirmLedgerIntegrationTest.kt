package com.automan.backend.service

import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceItem
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.model.Client
import com.automan.backend.model.EventType
import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime

/**
 * Invoice confirm, credit block, re-save, amount change, and delete against H2.
 * Each service call commits on its own so a thrown credit-limit error can be
 * checked after the service transaction rolls back.
 */
@SpringBootTest
@ActiveProfiles("test")
class InvoiceConfirmLedgerIntegrationTest {

    @Autowired private lateinit var invoiceHistoryService: InvoiceHistoryService
    @Autowired private lateinit var eventService: EventService
    @Autowired private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Autowired private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var eventRepository: EventRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }

    private val invoiceNumbers = mutableListOf<String>()
    private val chassisNumbers = mutableListOf<String>()
    private val clientIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            for (number in invoiceNumbers.distinct()) {
                val header = invoiceHistoryRepository.findByInvoiceNumber(number).orElse(null) ?: continue
                val headerId = header.id ?: continue
                invoiceHistoryLineRepository.deleteByInvoiceHistoryId(headerId)
                invoiceHistoryRepository.delete(header)
            }
            for (clientId in clientIds.distinct()) {
                eventRepository.findByClientIdOrderByEventDateDesc(clientId).forEach { eventRepository.delete(it) }
            }
            for (chassis in chassisNumbers.distinct()) {
                purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
            }
            for (clientId in clientIds.distinct()) {
                if (clientRepository.existsById(clientId)) {
                    clientRepository.deleteById(clientId)
                }
            }
        }
        invoiceNumbers.clear()
        chassisNumbers.clear()
        clientIds.clear()
    }

    @Test
    fun confirmInvoicePostsOneChargeAndMarksPurchaseSold() {
        val fixture = seed(
            chassis = "NZE141-100",
            clientNumber = "CL-LEDGER-100",
            clientName = "Crown Eagle Ledger",
            creditLimit = 50_000_000.0,
        )

        val result = invoiceHistoryService.confirmAndDownload(
            request(fixture, "INV-100", "850000"),
        )

        assertTrue(result.ledger.posted)
        assertFalse(result.ledger.reversed)
        assertTrue(result.pdfBytes.isNotEmpty())

        val header = invoiceHistoryRepository.findByInvoiceNumber("INV-100").orElseThrow()
        val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(header.id!!)
        assertEquals(1, lines.size)
        assertEquals("NZE141-100", lines[0].chassis)

        val purchase = purchaseRepository.findById(fixture.purchaseId).orElseThrow()
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchase.workflowStatus)

        val issued = eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(fixture.clientId, "INV-100")
            .filter { it.eventType == EventType.INVOICE_ISSUED }
        assertEquals(1, issued.size)
        assertEquals(850_000.0, issued[0].transactionPrice ?: 0.0, 0.01)
        assertEquals(-850_000.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun zeroTotalInvoiceIsSavedWithoutLedgerCharge() {
        val fixture = seed(
            chassis = "NZE141-ZERO",
            clientNumber = "CL-LEDGER-ZERO",
            clientName = "Zero Total Client",
            creditLimit = 50_000_000.0,
        )

        val result = invoiceHistoryService.confirmAndDownload(
            request(fixture, "INV-ZERO", "0"),
        )

        assertFalse(result.ledger.posted)
        assertTrue(result.ledger.warning.orEmpty().contains("Invoice total is zero"))
        assertNotNull(invoiceHistoryRepository.findByInvoiceNumber("INV-ZERO").orElse(null))
        assertEquals(
            WorkflowStatus.INVOICE_CONFIRMED,
            purchaseRepository.findById(fixture.purchaseId).orElseThrow().workflowStatus,
        )
        assertTrue(eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(fixture.clientId, "INV-ZERO").isEmpty())
        assertEquals(0.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun overCreditLimitWritesNothing() {
        val fixture = seed(
            chassis = "NZE141-OVER",
            clientNumber = "CL-LEDGER-OVER",
            clientName = "Over Limit Client",
            creditLimit = 100_000.0,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            invoiceHistoryService.confirmAndDownload(
                request(fixture, "INV-OVER", "150000"),
            )
        }
        assertTrue(error.message.orEmpty().contains("credit limit"))

        assertTrue(invoiceHistoryRepository.findByInvoiceNumber("INV-OVER").isEmpty)
        assertTrue(eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(fixture.clientId, "INV-OVER").isEmpty())
        assertEquals(
            WorkflowStatus.BOOKING_REQUESTED,
            purchaseRepository.findById(fixture.purchaseId).orElseThrow().workflowStatus,
        )
        assertEquals(0.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun confirmingSameAmountAgainDoesNotDoubleCharge() {
        val fixture = seed(
            chassis = "NZE141-SAME",
            clientNumber = "CL-LEDGER-SAME",
            clientName = "Same Amount Client",
            creditLimit = 50_000_000.0,
        )
        invoiceHistoryService.confirmAndDownload(request(fixture, "INV-SAME", "850000"))

        val second = invoiceHistoryService.confirmAndDownload(request(fixture, "INV-SAME", "850000"))

        assertFalse(second.ledger.posted)
        val issued = eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(fixture.clientId, "INV-SAME")
            .filter { it.eventType == EventType.INVOICE_ISSUED }
        assertEquals(1, issued.size)
        assertEquals(-850_000.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun changedInvoiceAmountReplacesOpenCharge() {
        val fixture = seed(
            chassis = "NZE141-CHG",
            clientNumber = "CL-LEDGER-CHG",
            clientName = "Changed Amount Client",
            creditLimit = 50_000_000.0,
        )
        invoiceHistoryService.confirmAndDownload(request(fixture, "INV-CHG", "500000"))

        val second = invoiceHistoryService.confirmAndDownload(request(fixture, "INV-CHG", "800000"))

        assertTrue(second.ledger.posted)
        assertTrue(second.ledger.reversed)
        assertEquals(800_000.0, eventService.openInvoiceLedgerCharge(fixture.clientId, "INV-CHG"), 0.01)
        assertEquals(-800_000.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun deletingInvoiceRestoresBalanceAndSecondDeleteDoesNotReverseAgain() {
        val fixture = seed(
            chassis = "NZE141-DEL",
            clientNumber = "CL-LEDGER-DEL",
            clientName = "Delete Invoice Client",
            creditLimit = 50_000_000.0,
        )
        invoiceHistoryService.confirmAndDownload(request(fixture, "INV-DEL", "850000"))

        val deleted = invoiceHistoryService.deleteByInvoiceNumbers(listOf("INV-DEL"))

        assertEquals(1, deleted.deleted)
        assertEquals(1, deleted.ledgerReversed)
        assertEquals(0.0, eventService.openInvoiceLedgerCharge(fixture.clientId, "INV-DEL"), 0.01)
        assertEquals(0.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
        assertEquals(
            WorkflowStatus.BOOKING_REQUESTED,
            purchaseRepository.findById(fixture.purchaseId).orElseThrow().workflowStatus,
        )
        val reversals = eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(fixture.clientId, "INV-DEL")
            .count { it.eventType == EventType.INVOICE_REVERSAL }
        assertEquals(1, reversals)

        val again = invoiceHistoryService.deleteByInvoiceNumbers(listOf("INV-DEL"))
        assertEquals(0, again.deleted)
        assertEquals(0, again.ledgerReversed)
        val reversalsAfter = eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(fixture.clientId, "INV-DEL")
            .count { it.eventType == EventType.INVOICE_REVERSAL }
        assertEquals(1, reversalsAfter)
        assertEquals(0.0, clientRepository.findById(fixture.clientId).orElseThrow().currentBalance, 0.01)
    }

    private data class Fixture(val clientId: Long, val purchaseId: Long, val chassis: String, val clientName: String)

    private fun seed(
        chassis: String,
        clientNumber: String,
        clientName: String,
        creditLimit: Double,
    ): Fixture {
        chassisNumbers.add(chassis)
        val client = clientRepository.save(
            Client(
                clientNumber = clientNumber,
                clientName = clientName,
                currentBalance = 0.0,
                creditLimit = creditLimit,
            ),
        )
        val clientId = client.id ?: error("client id")
        clientIds.add(clientId)
        val purchase = purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Test Car",
                clientId = clientId,
                clientName = clientName,
                workflowStatus = WorkflowStatus.BOOKING_REQUESTED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
        val purchaseId = purchase.id ?: error("purchase id")
        return Fixture(clientId, purchaseId, chassis, clientName)
    }

    private fun request(fixture: Fixture, invoiceNumber: String, amount: String): InvoiceConfirmAndDownloadRequest {
        invoiceNumbers.add(invoiceNumber)
        return InvoiceConfirmAndDownloadRequest(
            purchaseIds = listOf(fixture.purchaseId),
            chassisJoined = fixture.chassis,
            shippingDateIso = "2026-05-20",
            pdf = InvoicePdfRequest(
                invoiceNumber = invoiceNumber,
                invoiceDate = "2026-05-20",
                lcNumber = null,
                clientName = fixture.clientName,
                clientAddress = null,
                vessel = "PACIFIC",
                shippingDate = "2026-05-20",
                from = "NAGOYA",
                to = "MOMBASA",
                priceType = "C&F",
                items = listOf(
                    InvoiceItem(
                        unit = 1,
                        description = fixture.chassis,
                        amount = amount,
                        chassisNo = fixture.chassis,
                    ),
                ),
                totalAmount = amount,
                bankAccount = null,
                message = null,
            ),
        )
    }
}
