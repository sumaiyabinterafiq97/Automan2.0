package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ShippingHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

/**
 * Shipping history list and invoice/shipment lookups.
 * Invoice lines omit a chassis already marked sold. Shipment-detail lines keep it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingHistoryQueryHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var shippingHistoryRepository: ShippingHistoryRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisNumbers = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            chassisNumbers.toList().forEach { chassis ->
                shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)?.let {
                    shippingHistoryRepository.delete(it)
                }
                purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        chassisNumbers.clear()
    }

    @Test
    fun pageSearchReturnsTheMatchingChassisAndNotAnother() {
        val match = saveHistory("P0SRCH", client = "P0Client${token()}", vessel = "P0V${token()}")
        val other = saveHistory("P0SRCH", client = "P0Client${token()}", vessel = "P0V${token()}")

        val body = mockMvc.perform(
            get("/shipping-history/page-search")
                .param("q", match)
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].chassis").value(match))
            .andReturn().response.contentAsString

        assertFalse(body.contains(other))
    }

    @Test
    fun invoiceLinesOmitSoldChassisAndShipmentDetailsKeepIt() {
        val client = "P0Ship${token()}"
        val vessel = "P0Vessel${token()}"
        val otherVessel = "P0OtherVessel${token()}"
        val open = saveHistory("P0OPEN", client, vessel, WorkflowStatus.BOOKING_REQUESTED)
        val sold = saveHistory("P0SOLD", client, vessel, WorkflowStatus.INVOICE_CONFIRMED)
        val elsewhere = saveHistory("P0ELSE", client, otherVessel, WorkflowStatus.BOOKING_REQUESTED)

        val invoiceBody = mockMvc.perform(
            get("/shipping-history/for-invoice/lines")
                .param("clientName", client)
                .param("vessel", vessel),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.header.vessel").value(vessel))
            .andExpect(jsonPath("$.lines.length()").value(1))
            .andExpect(jsonPath("$.lines[0].chassis").value(open))
            .andReturn().response.contentAsString
        assertFalse(invoiceBody.contains(sold))
        assertFalse(invoiceBody.contains(elsewhere))

        mockMvc.perform(
            get("/shipping-history/for-invoice/vessels").param("clientName", client),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasItem(vessel)))

        val detailBody = mockMvc.perform(
            get("/shipping-history/for-shipment-details/lines")
                .param("clientName", client)
                .param("vessel", vessel),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.lines.length()").value(2))
            .andReturn().response.contentAsString
        assertTrue(detailBody.contains(open))
        assertTrue(detailBody.contains(sold))
        assertFalse(detailBody.contains(elsewhere))
    }

    @Test
    fun clientShipmentDetailsPdfReturnsAPdfForThatClientAndVessel() {
        val client = "P0Pdf${token()}"
        val vessel = "P0PdfV${token()}"
        saveHistory("P0PDF", client, vessel, WorkflowStatus.BOOKING_REQUESTED)

        mockMvc.perform(
            post("/shipping-history/client-shipment-details/pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientName":"$client","vessel":"$vessel"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("ClientBased_ShipmentDetails")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(client)))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(vessel)))
            .andExpect { result ->
                val bytes = result.response.contentAsByteArray
                assertTrue(bytes.size > 5)
                assertEqualsPdf(bytes)
            }

        mockMvc.perform(
            post("/shipping-history/client-shipment-details/pdf")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientName":"","vessel":"$vessel"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("clientName is required"))
    }

    private fun assertEqualsPdf(bytes: ByteArray) {
        assertTrue(bytes.decodeToString(0, 5).startsWith("%PDF"))
    }

    private fun saveHistory(
        prefix: String,
        client: String,
        vessel: String,
        status: WorkflowStatus = WorkflowStatus.BOOKING_REQUESTED,
    ): String {
        val chassis = "$prefix-${token()}"
        chassisNumbers.add(chassis)
        purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Ship Car",
                clientName = client,
                workflowStatus = status,
            ),
        )
        shippingHistoryRepository.save(
            ShippingHistory(
                chassis = chassis,
                clientName = client,
                vessel = vessel,
                amount = BigDecimal("1000.00"),
            ),
        )
        return chassis
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
