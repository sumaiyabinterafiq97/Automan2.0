package com.automan.backend.controller

import com.automan.backend.model.Client
import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID

/**
 * Invoice HTTP/document contract. Ledger amounts and reversals stay in
 * [com.automan.backend.service.InvoiceConfirmLedgerIntegrationTest].
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceDocumentHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Autowired private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Autowired private lateinit var eventRepository: EventRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val invoiceNumbers = mutableListOf<String>()
    private val purchaseIds = mutableListOf<Long>()
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
            for (id in purchaseIds.distinct()) {
                if (purchaseRepository.existsById(id)) purchaseRepository.deleteById(id)
            }
            for (clientId in clientIds.distinct()) {
                if (clientRepository.existsById(clientId)) clientRepository.deleteById(clientId)
            }
        }
        invoiceNumbers.clear()
        purchaseIds.clear()
        clientIds.clear()
    }

    @Test
    fun confirmMarksOnlyTheRequestedPurchase() {
        val target = savePurchase("Target Client")
        val other = savePurchase("Other Client")

        mockMvc.perform(
            post("/purchases/invoice/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseIds":[]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("No purchase IDs provided"))

        mockMvc.perform(
            post("/purchases/invoice/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseIds":[${target.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.updatedCount").value(1))

        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(target.id!!).orElseThrow().workflowStatus)
        assertNotEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(other.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun savePersistsThatInvoiceAndRejectsABlankNumber() {
        val target = savePurchase("Save Client")
        val other = savePurchase("Save Other")
        val number = trackInvoice("INV-SAVE")

        mockMvc.perform(
            post("/purchases/invoice/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(target.id!!, target.chassis, "  ", "100000")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invoice number is required"))
        assertNotEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(target.id!!).orElseThrow().workflowStatus)

        mockMvc.perform(
            post("/purchases/invoice/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(target.id!!, target.chassis, number, "100000")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Invoice saved successfully"))

        val header = invoiceHistoryRepository.findByInvoiceNumber(number).orElseThrow()
        val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(header.id!!)
        assertEquals(listOf(target.chassis), lines.map { it.chassis })
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(target.id!!).orElseThrow().workflowStatus)
        assertNotEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(other.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun confirmAndDownloadReturnsAPdfAndRejectsABlankNumber() {
        val target = savePurchase("Pdf Client")
        val number = trackInvoice("INV-PDF")

        mockMvc.perform(
            post("/purchases/invoice/confirm-and-download")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(target.id!!, target.chassis, "", "100000")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invoice number is required"))

        val result = mockMvc.perform(
            post("/purchases/invoice/confirm-and-download")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invoiceBody(target.id!!, target.chassis, number, "100000")),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("Final_Invoice")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("Pdf_Client")))
            .andReturn()

        val pdf = result.response.contentAsByteArray
        assertTrue(pdf.size > 5)
        assertEquals("%PDF", String(pdf, 0, 4, Charsets.US_ASCII))
        assertTrue(pdfText(pdf).contains(target.chassis))
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(target.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun batchConfirmSavesOneInvoiceAndSkipsABlankNumber() {
        val kept = savePurchase("Batch Client")
        val skipped = savePurchase("Batch Other")
        val number = trackInvoice("INV-BATCH")

        mockMvc.perform(
            post("/purchases/invoice/batch-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoices":[]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("No invoices provided"))

        mockMvc.perform(
            post("/purchases/invoice/batch-confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"invoices":[
                      ${invoiceBody(kept.id!!, kept.chassis, number, "50000")},
                      ${invoiceBody(skipped.id!!, skipped.chassis, " ", "50000")}
                    ]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(1))
            .andExpect(jsonPath("$.invoiceNumbers[0]").value(number))
            .andExpect(jsonPath("$.skipped[0]").value("Invoice number is required"))

        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(kept.id!!).orElseThrow().workflowStatus)
        assertNotEquals(WorkflowStatus.INVOICE_CONFIRMED, purchaseRepository.findById(skipped.id!!).orElseThrow().workflowStatus)
        assertTrue(invoiceHistoryRepository.findByInvoiceNumber(number).isPresent)
    }

    private fun savePurchase(clientName: String): Purchase {
        val token = UUID.randomUUID().toString().take(8)
        val client = clientRepository.save(
            Client(
                clientNumber = "CL-P0DOC-$token",
                clientName = clientName,
                currentBalance = 0.0,
                creditLimit = 50_000_000.0,
            ),
        )
        clientIds.add(client.id!!)
        val purchase = purchaseRepository.save(
            Purchase(
                chassis = "P0INV-$token-1",
                carName = "Invoice Car",
                clientId = client.id,
                clientName = clientName,
                workflowStatus = WorkflowStatus.BOOKING_REQUESTED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
        purchaseIds.add(purchase.id!!)
        return purchase
    }

    private fun trackInvoice(prefix: String): String {
        val number = "$prefix-${UUID.randomUUID().toString().take(8)}"
        invoiceNumbers.add(number)
        return number
    }

    private fun invoiceBody(purchaseId: Long, chassis: String, invoiceNumber: String, amount: String): String =
        """
        {
          "purchaseIds": [$purchaseId],
          "chassisJoined": "$chassis",
          "shippingDateIso": "2026-05-20",
          "pdf": {
            "invoiceNumber": "$invoiceNumber",
            "invoiceDate": "2026-05-20",
            "clientName": "Pdf Client",
            "vessel": "PACIFIC",
            "shippingDate": "2026-05-20",
            "from": "NAGOYA",
            "to": "MOMBASA",
            "priceType": "C&F",
            "items": [{"unit": 1, "description": "$chassis", "amount": "$amount", "chassisNo": "$chassis"}],
            "totalAmount": "$amount"
          }
        }
        """.trimIndent()

    private fun pdfText(bytes: ByteArray): String {
        com.itextpdf.kernel.pdf.PdfReader(java.io.ByteArrayInputStream(bytes)).use { reader ->
            com.itextpdf.kernel.pdf.PdfDocument(reader).use { doc ->
                return (1..doc.numberOfPages).joinToString("\n") { page ->
                    com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(page))
                }
            }
        }
    }
}
