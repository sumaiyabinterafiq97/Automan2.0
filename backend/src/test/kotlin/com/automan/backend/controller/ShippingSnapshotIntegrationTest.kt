package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShippingSnapshotIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var shippingHistoryRepository: ShippingHistoryRepository

    @BeforeEach
    fun setUp() {
        shippingHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun `GET purchase returns canonical vessel and shipmentDate from shipping_history`() {
        val saved = purchaseRepository.save(basePurchase("SHP-SNAP-1"))
        shippingHistoryRepository.save(
            ShippingHistory(
                chassis = "SHP-SNAP-1",
                vessel = "CANONICAL-VESSEL",
                shipmentDate = LocalDate.parse("2026-07-20"),
                blNo = "BL-CANON",
                amount = BigDecimal("1000.00"),
            ),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.vessel").value("CANONICAL-VESSEL"))
            .andExpect(jsonPath("$.shipmentDate").value("2026-07-20"))
            .andExpect(jsonPath("$.blNo").value("BL-CANON"))
    }

    @Test
    fun `PUT purchase persists shipping snapshot to shipping_history`() {
        val saved = purchaseRepository.save(basePurchase("SHP-PUT-1"))

        mockMvc.perform(
            put("/purchases/${saved.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "chassis":"SHP-PUT-1",
                      "blNo":"BL-123",
                      "vessel":"VESSEL-A",
                      "shipmentDate":"June 22, 2026 (Monday)"
                    }""".trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.blNo").value("BL-123"))
            .andExpect(jsonPath("$.vessel").value("VESSEL-A"))
            .andExpect(jsonPath("$.shipmentDate").value("2026-06-22"))

        val row = shippingHistoryRepository.findFirstByChassisOrderByIdDesc("SHP-PUT-1")
        assertEquals("BL-123", row?.blNo)
        assertEquals("VESSEL-A", row?.vessel)
        assertEquals(LocalDate.parse("2026-06-22"), row?.shipmentDate)
    }

    @Test
    fun `GET purchase returns empty vessel when no shipping_history row`() {
        val saved = purchaseRepository.save(
            basePurchase("SHP-FB-1"),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.vessel").value(""))
    }

    @Test
    fun `invoice filter matches vessel on shipping_history when purchase column empty`() {
        purchaseRepository.save(
            basePurchase("SHP-INV-1").copy(
                clientName = "Test Client A",
                bookingRequested = false,
                invoiceConfirmed = false,
            ),
        )
        shippingHistoryRepository.save(
            ShippingHistory(
                chassis = "SHP-INV-1",
                clientName = "Test Client A",
                vessel = "FILTER-VESSEL",
                shipmentDate = LocalDate.parse("2026-08-01"),
                amount = BigDecimal.ZERO,
            ),
        )

        mockMvc.perform(
            get("/purchases/filter/invoice")
                .param("clientName", "Test Client A")
                .param("vessel", "FILTER-VESSEL")
                .param("shipmentDate", "2026-08-01"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].chassis").value("SHP-INV-1"))
            .andExpect(jsonPath("$[0].vessel").value("FILTER-VESSEL"))
    }

    private fun basePurchase(chassis: String): Purchase = Purchase(
        chassis = chassis,
        carName = "Test Car",
        carModelYear = "2020",
        brand = "Toyota",
        country = "Japan",
        clientName = "Client",
        color = "White",
        fuel = "Gasoline",
        price = "10000.00",
        totalPrice = "11000.00",
        paymentDate = "2026-06-01",
        rixoRequested = "0",
        rixoConfirmed = "1",
        shipmentDate = "",
        vessel = "",
        blNo = "",
        shipmentCharges = "0",
        freight = "0",
        storageCharges = "0",
        miscCharges = "0",
        inspectionFee = "0",
        commission = "0",
        repairCompany = "",
        repairCharges = "0",
        notes = "",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        bookingRequested = false,
        shaken = false,
        negotiate = false,
    )
}
