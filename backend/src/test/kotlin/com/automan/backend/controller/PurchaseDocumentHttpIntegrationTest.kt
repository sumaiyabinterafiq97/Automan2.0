package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.service.GmailTransportMailService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
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
 * Purchase PDF and Rixo transport email. The mail sender is a mock, so SMTP is never opened.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseDocumentHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @MockBean private lateinit var gmailTransportMailService: GmailTransportMailService

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val purchaseIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            purchaseIds.toSet().forEach { id ->
                if (purchaseRepository.existsById(id)) purchaseRepository.deleteById(id)
            }
        }
        purchaseIds.clear()
    }

    @Test
    fun rixoPdfContainsTheChassisAndDoesNotMarkThePurchaseRequested() {
        val purchase = savePurchase("P0RIXO")
        val other = savePurchase("P0RIXO")

        mockMvc.perform(
            post("/purchases/rixo-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[],"invoiceData":{}}"""),
        )
            .andExpect(status().isInternalServerError)

        val result = mockMvc.perform(
            post("/purchases/rixo-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${purchase.id}],"invoiceData":{"consignee":"Memon Buyer"}}"""),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("RixoRequest")))
            .andReturn()

        val pdf = result.response.contentAsByteArray
        assertPdf(pdf)
        assertTrue(pdfText(pdf).contains(purchase.chassis))
        assertNull(purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
        assertNull(purchaseRepository.findById(other.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun transportPdfContainsTheChassis() {
        val purchase = savePurchase("P0TRN")

        val result = mockMvc.perform(
            post("/purchases/rixo-transport-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"ids":[${purchase.id}],"transportData":{"rixoCompany":"SHAHBAZ","buyingDate":"2026-06-15"}}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("RixoTransport_SHAHBAZ")))
            .andReturn()

        val pdf = result.response.contentAsByteArray
        assertPdf(pdf)
        assertTrue(pdfText(pdf).contains(purchase.chassis))
        assertNull(purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun transportEmailReachesTheMailServiceAndDoesNotSendForABadRecipient() {
        val purchase = savePurchase("P0MAIL")
        `when`(gmailTransportMailService.isConfigured()).thenReturn(true)

        mockMvc.perform(
            post("/purchases/rixo-transport-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"to":"not-an-email","ids":[${purchase.id}],"transportData":{"rixoCompany":"SHAHBAZ"}}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("A valid recipient email is required"))
        verify(gmailTransportMailService, never()).sendRixoTransportPdf(
            anyString() ?: "",
            anyString() ?: "",
            anyString() ?: "",
            anyString() ?: "",
            anyByteArray(),
            anyString() ?: "",
            anyString() ?: "",
        )

        mockMvc.perform(
            post("/purchases/rixo-transport-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"to":"ops@example.com","ids":[${purchase.id}],"emailSubject":"KLC - 2026-09-02","emailBody":"Mail text only","transportData":{"rixoCompany":"SHAHBAZ","buyingDate":"2026-06-15","headMessage":"PDF head stays"}}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Email sent"))

        val pdf = ArgumentCaptor.forClass(ByteArray::class.java)
        verify(gmailTransportMailService).sendRixoTransportPdf(
            eq("ops@example.com") ?: "",
            eq("SHAHBAZ") ?: "",
            eq("2026-06-15") ?: "",
            eq("PDF head stays") ?: "",
            pdf.capture() ?: ByteArray(0),
            eq("KLC - 2026-09-02") ?: "",
            eq("Mail text only") ?: "",
        )
        assertPdf(pdf.value)
        assertTrue(pdfText(pdf.value).contains(purchase.chassis))
        assertNull(purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun shippingSchedulePdfsNameTheBookingAndIncludeTheChassis() {
        val purchase = savePurchase("P0SHIP")
        val body = """
            {
              "bookingNo": "BK-P0-77",
              "vesselName": "PACIFIC STAR",
              "pol": "NAGOYA",
              "pod": "MOMBASA",
              "shippingDate": "2026-06-15",
              "consigneeName": "Harbour Motors",
              "consigneeAddress": "Mombasa",
              "chassisNumbers": ["${purchase.chassis}"],
              "calculationMode": "C&F"
            }
        """.trimIndent()

        val cnf = mockMvc.perform(
            post("/purchases/shipping-schedule/generate-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("ShippingSchedule_BK-P0-77")))
            .andReturn()
            .response
            .contentAsByteArray
        assertPdf(cnf)
        assertTrue(pdfText(cnf).contains(purchase.chassis))

        val fob = mockMvc.perform(
            post("/purchases/fob-shipping-schedule/generate-pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("FOB_ShippingSchedule_BK-P0-77")))
            .andReturn()
            .response
            .contentAsByteArray
        assertPdf(fob)
        assertTrue(pdfText(fob).contains(purchase.chassis))
        assertNull(purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
    }

    private fun savePurchase(prefix: String): Purchase {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = "$prefix-${UUID.randomUUID().toString().take(8)}-1",
                carName = "Document Car",
                brand = "TOYOTA",
                auctionHouse = "USS Tokyo",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
        purchaseIds.add(saved.id!!)
        return saved
    }

    private fun assertPdf(bytes: ByteArray) {
        assertTrue(bytes.size > 5)
        assertEquals("%PDF", String(bytes, 0, 4, Charsets.US_ASCII))
    }

    private fun pdfText(bytes: ByteArray): String {
        com.itextpdf.kernel.pdf.PdfReader(java.io.ByteArrayInputStream(bytes)).use { reader ->
            com.itextpdf.kernel.pdf.PdfDocument(reader).use { doc ->
                return (1..doc.numberOfPages).joinToString("\n") { page ->
                    com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(doc.getPage(page))
                }
            }
        }
    }

    private fun anyByteArray(): ByteArray =
        org.mockito.ArgumentMatchers.any(ByteArray::class.java) ?: ByteArray(0)
}
