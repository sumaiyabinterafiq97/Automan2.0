package com.automan.backend.controller

import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceItem
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.dto.ShippingHistoryBatchRequest
import com.automan.backend.dto.ShippingHistoryItemRequest
import com.automan.backend.model.Client
import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.service.InvoiceHistoryService
import com.automan.backend.service.PurchaseService
import com.automan.backend.service.RixoHistoryService
import com.automan.backend.service.ShippingHistoryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Purchase → Rixo confirm → booking → shipping history → invoice ledger,
 * then a sold chassis cannot be removed from the shipment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseToLedgerChainIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var purchaseService: PurchaseService
    @Autowired private lateinit var rixoHistoryRepository: RixoHistoryRepository
    @Autowired private lateinit var rixoHistoryService: RixoHistoryService
    @Autowired private lateinit var shippingHistoryService: ShippingHistoryService
    @Autowired private lateinit var shippingHistoryRepository: ShippingHistoryRepository
    @Autowired private lateinit var invoiceHistoryService: InvoiceHistoryService
    @Autowired private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Autowired private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var eventRepository: EventRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }

    private val chassis = "NZE141-200"
    private val invoiceNumber = "INV-CHAIN-200"
    private var clientId: Long? = null
    private var historyId: Long? = null

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            invoiceHistoryRepository.findByInvoiceNumber(invoiceNumber).orElse(null)?.let { header ->
                header.id?.let { invoiceHistoryLineRepository.deleteByInvoiceHistoryId(it) }
                invoiceHistoryRepository.delete(header)
            }
            clientId?.let { id ->
                eventRepository.findByClientIdOrderByEventDateDesc(id).forEach { eventRepository.delete(it) }
                if (clientRepository.existsById(id)) clientRepository.deleteById(id)
            }
            shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)?.let { shippingHistoryRepository.delete(it) }
            historyId?.let { if (rixoHistoryRepository.existsById(it)) rixoHistoryRepository.deleteById(it) }
            purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
        }
    }

    @Test
    fun purchaseReachesInvoiceLedgerAndSoldChassisCannotBeUnbooked() {
        val client = clientRepository.save(
            Client(
                clientNumber = "CL-CHAIN-200",
                clientName = "Chain Ledger Client",
                currentBalance = 0.0,
                creditLimit = 50_000_000.0,
            ),
        )
        clientId = client.id
        val purchase = purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Chain Car",
                clientId = client.id,
                clientName = client.clientName,
                workflowStatus = WorkflowStatus.PURCHASED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
        val purchaseId = purchase.id ?: error("purchase id")

        val rixo = rixoHistoryRepository.save(
            RixoHistory(
                buyingDate = LocalDate.of(2026, 5, 1),
                rixoCompany = "Chain Co",
                chassis = chassis,
            ),
        )
        historyId = rixo.id
        val confirmed = rixoHistoryService.confirmSelectedHistoryRows(listOf(rixo.id!!))
        assertEquals(1, confirmed.updatedPurchases)
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(purchaseId).orElseThrow().workflowStatus)

        purchaseService.markPurchasesAsBookingRequested(listOf(purchaseId))
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, purchaseRepository.findById(purchaseId).orElseThrow().workflowStatus)

        val savedRows = shippingHistoryService.saveBatch(
            ShippingHistoryBatchRequest(
                vessel = "CHAIN-VESSEL",
                shipmentDate = "2026-05-20",
                items = listOf(
                    ShippingHistoryItemRequest(
                        chassis = chassis,
                        clientName = client.clientName,
                        amount = BigDecimal("100000"),
                    ),
                ),
            ),
        )
        assertEquals(1, savedRows)
        assertTrue(shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis) != null)

        val ledger = invoiceHistoryService.confirmAndDownload(
            InvoiceConfirmAndDownloadRequest(
                purchaseIds = listOf(purchaseId),
                chassisJoined = chassis,
                shippingDateIso = "2026-05-20",
                pdf = InvoicePdfRequest(
                    invoiceNumber = invoiceNumber,
                    invoiceDate = "2026-05-20",
                    lcNumber = null,
                    clientName = client.clientName,
                    clientAddress = null,
                    vessel = "CHAIN-VESSEL",
                    shippingDate = "2026-05-20",
                    from = "NAGOYA",
                    to = "MOMBASA",
                    priceType = "C&F",
                    items = listOf(
                        InvoiceItem(unit = 1, description = chassis, amount = "100000", chassisNo = chassis),
                    ),
                    totalAmount = "100000",
                    bankAccount = null,
                    message = null,
                ),
            ),
        )
        assertTrue(ledger.ledger.posted)
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(purchaseId).orElseThrow().workflowStatus)
        assertEquals(-100_000.0, clientRepository.findById(client.id!!).orElseThrow().currentBalance, 0.01)
        val shipping = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)
        assertTrue(shipping != null)

        mockMvc.perform(
            post("/shipping-history/remove-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassisToken":"$chassis","historyId":${shipping!!.id},"purchaseId":$purchaseId}"""),
        ).andExpect(status().isBadRequest)

        assertTrue(shippingHistoryRepository.findById(shipping.id!!).isPresent)
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(purchaseId).orElseThrow().workflowStatus)
    }
}
