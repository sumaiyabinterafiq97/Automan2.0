package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ShippingHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.service.PurchaseWorkflowService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShippingHistoryUnbookIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var shippingHistoryRepository: ShippingHistoryRepository

    @Autowired
    private lateinit var purchaseWorkflowService: PurchaseWorkflowService

    @BeforeEach
    fun setUp() {
        shippingHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun `remove-chassis clears bookingRequested for BOOKING_REQUESTED purchase`() {
        val purchase = purchaseRepository.save(
            basePurchase("UNBOOK-001").copy(workflowStatus = WorkflowStatus.BOOKING_REQUESTED),
        )
        val history = shippingHistoryRepository.save(
            ShippingHistory(
                bookingId = "B-UNBOOK-1",
                vessel = "V1",
                chassis = "UNBOOK-001",
                clientName = "client-a",
                amount = BigDecimal("1000.00"),
                shipmentDate = LocalDate.of(2026, 1, 10),
            ),
        )

        mockMvc.perform(
            post("/shipping-history/remove-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"chassisToken":"UNBOOK-001","historyId":${history.id},"purchaseId":${purchase.id}}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unbookedPurchases").value(1))
            .andExpect(jsonPath("$.deletedRow").value(true))

        val updated = purchaseRepository.findById(purchase.id!!).orElseThrow()
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, updated.workflowStatus)
        val read = purchaseWorkflowService.applyForRead(updated)
        assertFalse(read.bookingRequested)
        assertTrue(shippingHistoryRepository.findById(history.id!!).isEmpty)
    }

    @Test
    fun `remove-chassis rejects Sold invoice-confirmed chassis`() {
        val purchase = purchaseRepository.save(
            basePurchase("SOLD-001").copy(workflowStatus = WorkflowStatus.INVOICE_CONFIRMED),
        )
        val history = shippingHistoryRepository.save(
            ShippingHistory(
                bookingId = "B-SOLD-1",
                vessel = "V1",
                chassis = "SOLD-001",
                clientName = "client-a",
                amount = BigDecimal("1000.00"),
            ),
        )

        mockMvc.perform(
            post("/shipping-history/remove-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"chassisToken":"SOLD-001","historyId":${history.id},"purchaseId":${purchase.id}}""",
                ),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())

        val unchanged = purchaseRepository.findById(purchase.id!!).orElseThrow()
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, unchanged.workflowStatus)
        assertTrue(shippingHistoryRepository.findById(history.id!!).isPresent)
    }

    @Test
    fun `delete-batch clears bookingRequested and rejects when Sold present`() {
        val booked = purchaseRepository.save(
            basePurchase("DEL-BOOK-1").copy(workflowStatus = WorkflowStatus.BOOKING_REQUESTED),
        )
        val historyOk = shippingHistoryRepository.save(
            ShippingHistory(
                bookingId = "B-DEL-OK",
                vessel = "V1",
                chassis = "DEL-BOOK-1",
                amount = BigDecimal("100.00"),
            ),
        )

        mockMvc.perform(
            post("/shipping-history/delete-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${historyOk.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unbookedPurchases").value(1))

        assertEquals(
            WorkflowStatus.RIXO_CONFIRMED,
            purchaseRepository.findById(booked.id!!).orElseThrow().workflowStatus,
        )

        val sold = purchaseRepository.save(
            basePurchase("DEL-SOLD-1").copy(workflowStatus = WorkflowStatus.INVOICE_CONFIRMED),
        )
        val historySold = shippingHistoryRepository.save(
            ShippingHistory(
                bookingId = "B-DEL-SOLD",
                vessel = "V1",
                chassis = "DEL-SOLD-1",
                amount = BigDecimal("100.00"),
            ),
        )

        mockMvc.perform(
            post("/shipping-history/delete-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"ids":[${historySold.id}]}"""),
        )
            .andExpect(status().isBadRequest)

        assertEquals(
            WorkflowStatus.INVOICE_CONFIRMED,
            purchaseRepository.findById(sold.id!!).orElseThrow().workflowStatus,
        )
        assertTrue(shippingHistoryRepository.findById(historySold.id!!).isPresent)
    }

    @Test
    fun `remove-chassis drops one token and leaves the other chassis booked`() {
        val removed = purchaseRepository.save(
            basePurchase("AAA-1").copy(workflowStatus = WorkflowStatus.BOOKING_REQUESTED),
        )
        val kept = purchaseRepository.save(
            basePurchase("BBB-2").copy(workflowStatus = WorkflowStatus.BOOKING_REQUESTED),
        )
        val history = shippingHistoryRepository.save(
            ShippingHistory(
                bookingId = "B-MULTI-1",
                vessel = "V1",
                chassis = "AAA-1;BBB-2",
                clientName = "client-a",
                amount = BigDecimal("2000.00"),
                shipmentDate = LocalDate.of(2026, 1, 10),
            ),
        )

        mockMvc.perform(
            post("/shipping-history/remove-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"chassisToken":"AAA-1","historyId":${history.id},"purchaseId":${removed.id}}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedRow").value(false))
            .andExpect(jsonPath("$.remainingChassis").value("BBB-2"))
            .andExpect(jsonPath("$.unbookedPurchases").value(1))

        val row = shippingHistoryRepository.findById(history.id!!).orElseThrow()
        assertEquals("BBB-2", row.chassis)
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(removed.id!!).orElseThrow().workflowStatus)
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, purchaseRepository.findById(kept.id!!).orElseThrow().workflowStatus)
    }

    private fun basePurchase(chassis: String): Purchase = Purchase(
        chassis = chassis,
        carName = "Test Car",
        carModelYear = "2020",
        brand = "Toyota",
        country = "Japan",
        price = "10000.00",
        auctionFee = "500.00",
        recycleFee = "200.00",
        roadTax = "300.00",
        totalPrice = "11000.00",
        paymentDate = LocalDate.now().toString(),
        rixoRequested = "0",
        rixoConfirmed = "0",
        rixoPrice = "0.00",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        bookingRequested = false,
    )
}
