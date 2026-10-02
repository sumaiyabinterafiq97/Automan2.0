package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Booking assignment and shipping-history batch creation over HTTP.
 * Assigning cars sets `purchases.booking_id` for the submitted ids only.
 * Submitting the same cars again updates that assignment; it does not reject
 * and it does not create another purchase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingShippingWorkflowIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var shippingHistoryRepository: ShippingHistoryRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisNumbers = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            chassisNumbers.distinct().forEach { chassis ->
                shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)?.let {
                    shippingHistoryRepository.delete(it)
                }
                purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        chassisNumbers.clear()
    }

    @Test
    fun bookingSelectedCarsAssignsOnlyThoseChassis() {
        val selectedA = savePurchase("BK-SEL-A")
        val selectedB = savePurchase("BK-SEL-B")
        val unselected = savePurchase("BK-SEL-SKIP")
        val bookingId = 770_011L

        mockMvc.perform(
            post("/api/booking-cars")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"bookingId":$bookingId,"carIds":[${selectedA.id},${selectedB.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Cars assigned to booking successfully"))
            .andExpect(jsonPath("$.updatedCount").value(2))

        val assigned = purchaseRepository.findByBookingId(bookingId).map { it.chassis }.toSet()
        assertEquals(setOf(selectedA.chassis, selectedB.chassis), assigned)
        assertEquals(bookingId, purchaseRepository.findById(selectedA.id!!).orElseThrow().bookingId)
        assertEquals(bookingId, purchaseRepository.findById(selectedB.id!!).orElseThrow().bookingId)
        assertNull(purchaseRepository.findById(unselected.id!!).orElseThrow().bookingId)
    }

    @Test
    fun submittingTheSameCarsAgainReusesTheBookingAssignment() {
        val selected = savePurchase("BK-AGAIN")
        val unselected = savePurchase("BK-AGAIN-SKIP")
        val bookingId = 770_012L
        val body = """{"bookingId":$bookingId,"carIds":[${selected.id}]}"""

        mockMvc.perform(
            post("/api/booking-cars").contentType(MediaType.APPLICATION_JSON).content(body),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/booking-cars").contentType(MediaType.APPLICATION_JSON).content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.updatedCount").value(1))

        assertEquals(1, purchaseRepository.findByChassis(selected.chassis).size)
        assertEquals(listOf(selected.chassis), purchaseRepository.findByBookingId(bookingId).map { it.chassis })
        assertEquals(bookingId, purchaseRepository.findById(selected.id!!).orElseThrow().bookingId)
        assertNull(purchaseRepository.findById(unselected.id!!).orElseThrow().bookingId)
    }

    @Test
    fun shippingHistoryBatchPersistsSubmittedChassisAndLeavesOtherRows() {
        val first = savePurchase("SH-BATCH-1", clientName = "Client One")
        val second = savePurchase("SH-BATCH-2", clientName = "Client Two")
        val unrelatedChassis = track("SH-BATCH-KEEP")
        val unrelated = shippingHistoryRepository.save(
            ShippingHistory(
                chassis = unrelatedChassis,
                vessel = "KEEP-VESSEL",
                clientName = "Keep Client",
                amount = BigDecimal("10.00"),
                shipmentDate = LocalDate.of(2026, 1, 1),
                bookingId = "KEEP-BOOKING",
            ),
        )

        mockMvc.perform(
            post("/shipping-history/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "vessel": "P0-VESSEL",
                      "shipmentDate": "2026-06-15",
                      "bookingId": "P0-BOOK-1",
                      "items": [
                        {"chassis": "${first.chassis}", "clientName": "Client One", "amount": 250000},
                        {"chassis": "${second.chassis}", "clientName": "Client Two", "amount": 180000}
                      ]
                    }
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.saved").value(2))

        val firstRow = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(first.chassis)!!
        val secondRow = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(second.chassis)!!
        assertEquals("P0-VESSEL", firstRow.vessel)
        assertEquals(LocalDate.of(2026, 6, 15), firstRow.shipmentDate)
        assertEquals("Client One", firstRow.clientName)
        assertEquals("P0-BOOK-1", firstRow.bookingId)
        assertEquals(BigDecimal("250000.00"), firstRow.amount)
        assertEquals("P0-VESSEL", secondRow.vessel)
        assertEquals("Client Two", secondRow.clientName)
        assertEquals(second.chassis, secondRow.chassis)

        val kept = shippingHistoryRepository.findById(unrelated.id!!).orElseThrow()
        assertEquals(unrelatedChassis, kept.chassis)
        assertEquals("KEEP-VESSEL", kept.vessel)
        assertEquals("Keep Client", kept.clientName)
        assertEquals(BigDecimal("10.00"), kept.amount)
        assertEquals(LocalDate.of(2026, 1, 1), kept.shipmentDate)
        assertEquals("KEEP-BOOKING", kept.bookingId)
    }

    private fun savePurchase(label: String, clientName: String? = null): Purchase {
        val chassis = track(label)
        return purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Booking Car",
                clientName = clientName,
            ),
        )
    }

    private fun track(label: String): String {
        val chassis = "$label-${UUID.randomUUID().toString().take(8)}"
        chassisNumbers.add(chassis)
        return chassis
    }
}
