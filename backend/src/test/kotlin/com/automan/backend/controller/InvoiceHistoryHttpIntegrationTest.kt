package com.automan.backend.controller

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

/**
 * Invoice history list and download HTTP. Does not retest ledger posting or invoice delete reversal.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvoiceHistoryHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Autowired private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val invoiceNumbers = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            invoiceNumbers.toList().forEach { number ->
                invoiceHistoryRepository.findByInvoiceNumber(number).ifPresent { header ->
                    val id = header.id
                    if (id != null) {
                        invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(id)
                            .forEach { invoiceHistoryLineRepository.delete(it) }
                    }
                    invoiceHistoryRepository.delete(header)
                }
            }
        }
        invoiceNumbers.clear()
    }

    @Test
    fun pageAndSearchReturnTheRequestedInvoiceAndNotAnother() {
        val client = "P0InvClient${token()}"
        val otherClient = "P0InvOther${token()}"
        val number = saveInvoice(client, "P0Vessel${token()}")
        val other = saveInvoice(otherClient, "P0Vessel${token()}")

        val page = mockMvc.perform(
            get("/invoice-history/page")
                .param("clientName", client)
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].invoiceNumber").value(number))
            .andExpect(jsonPath("$.content[0].clientName").value(client))
            .andReturn().response.contentAsString
        assertFalse(page.contains(other))

        val search = mockMvc.perform(
            get("/invoice-history/page-search")
                .param("q", number)
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].invoiceNumber").value(number))
            .andReturn().response.contentAsString
        assertFalse(search.contains(other))

        mockMvc.perform(get("/invoice-history/filter-options"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clients", org.hamcrest.Matchers.hasItem(client)))
    }

    @Test
    fun pdfByInvoiceNumberReturnsAPdfAndRejectsABadDate() {
        val client = "P0PdfClient${token()}"
        val number = saveInvoice(client, "P0PdfVessel${token()}")

        mockMvc.perform(get("/invoice-history/$number/pdf"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("Final_Invoice")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(client)))
            .andExpect { result ->
                val bytes = result.response.contentAsByteArray
                assertTrue(bytes.size > 5)
                assertTrue(String(bytes, 0, 5, Charsets.US_ASCII).startsWith("%PDF"))
            }

        mockMvc.perform(get("/invoice-history/$number/pdf").param("invoiceDate", "13-13-13"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invoiceDate must be yyyy-MM-dd"))
    }

    @Test
    fun printAllRequiresAFilterAndReturnsAZipForThatClient() {
        mockMvc.perform(get("/invoice-history/print-all"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Choose a client, vessel, booking number, or date range before printing all."))

        val client = "P0Zip${token()}"
        saveInvoice(client, "P0ZipVessel${token()}")

        mockMvc.perform(get("/invoice-history/print-all").param("clientName", client))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/zip"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("invoice-history.zip")))
            .andExpect { result ->
                val bytes = result.response.contentAsByteArray
                assertTrue(bytes.size > 4)
                assertTrue(String(bytes, 0, 2, Charsets.US_ASCII) == "PK")
            }
    }

    private fun saveInvoice(client: String, vessel: String): String {
        val number = "P0INV-${token()}"
        invoiceNumbers.add(number)
        val header = invoiceHistoryRepository.save(
            InvoiceHistory(
                invoiceNumber = number,
                clientName = client,
                vessel = vessel,
                shippingDate = LocalDate.of(2026, 6, 15),
                pol = "YOKOHAMA",
                pod = "CHATTOGRAM",
                priceType = "C&F",
            ),
        )
        invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(
                invoiceHistoryId = header.id!!,
                chassis = "P0CH-${token()}",
                lineAmount = "100000",
                sortOrder = 1,
            ),
        )
        return number
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
