package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTotalCarPriceIntegrationTest {

    @Autowired
    private lateinit var dashboardService: DashboardService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var purchaseCostLineService: PurchaseCostLineService

    @BeforeEach
    fun clearPurchases() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun totalCarPrice_sumsCarPriceInPeriodOnly() {
        saveWithCarPrice("IN-1", date = "2026-06-15", price = "1000")
        saveWithCarPrice("IN-2", date = "2026-06-20", price = "500")
        saveWithCarPrice("OUT-1", date = "2026-05-31", price = "9999")

        val kpis = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30").kpis
        val total = kpis.first { it.id == "total_car_price" }
        assertEquals(1500.0, total.value, 0.001)
        assertEquals("¥1,500", total.displayValue)
        val ids = kpis.map { it.id }
        assertEquals("total_car_price", ids[1])
        assertEquals("cars_bought", ids[0])
        assertTrue(ids.contains("rixo_pending"))
        assertTrue(ids.contains("bookings_pending"))
    }

    @Test
    fun totalCarPrice_includesPriorPeriodForDelta() {
        saveWithCarPrice("CUR-1", date = "2026-06-10", price = "2000")
        saveWithCarPrice("PREV-1", date = "2026-05-15", price = "1000")

        val kpis = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30").kpis
        val total = kpis.first { it.id == "total_car_price" }
        assertEquals(2000.0, total.value, 0.001)
        assertEquals(1000.0, total.previousValue, 0.001)
    }

    private fun saveWithCarPrice(chassis: String, date: String, price: String) {
        val saved = purchaseRepository.save(minimalPurchase(chassis, date))
        purchaseCostLineService.syncFromPurchase(saved.copy(price = price))
    }

    private fun minimalPurchase(chassis: String, date: String): Purchase =
        Purchase(
            chassis = chassis,
            date = date,
            carName = "Test",
            carModelYear = "2020",
            brand = "Toyota",
            country = "Japan",
            color = "White",
            fuel = "Gasoline",
            price = null,
            auctionFee = "0",
            recycleFee = "0",
            roadTax = "0",
            totalPrice = "0",
            paymentDate = date,
            rixoRequested = "0",
            rixoConfirmed = "0",
            rixoPrice = "0",
            shipmentDate = date,
            vessel = "V1",
            shipmentCharges = "0",
            freight = "0",
            storageCharges = "0",
            miscCharges = "0",
            inspectionFee = "0",
            commission = "0",
            repairCompany = "",
            repairCharges = "0",
            notes = "",
            numberCut = "0",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            bookingRequested = false,
            workflowStatus = WorkflowStatus.PURCHASED,
        )
}
