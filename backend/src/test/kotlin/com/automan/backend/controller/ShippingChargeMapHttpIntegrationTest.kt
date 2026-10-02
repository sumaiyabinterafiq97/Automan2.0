package com.automan.backend.controller

import com.automan.backend.repository.ShippingChargeMapRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

/**
 * Shipping charge configuration HTTP: a stock location's tiers are stored, replaced, and deleted
 * without changing another stock location.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingChargeMapHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var shippingChargeMapRepository: ShippingChargeMapRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val stocks = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            stocks.toList().forEach { stock ->
                shippingChargeMapRepository.deleteByStockLocationIgnoreCase(stock)
            }
        }
        stocks.clear()
    }

    @Test
    fun addAndLookupReturnTheRequestedStockAndLeaveAnotherStockAlone() {
        val stock = track("P0STK${token()}")
        val other = track("P0OTH${token()}")
        add(stock, 2, "17000")
        add(other, 4, "9000")

        mockMvc.perform(get("/shipping-charge-map/mappings/by-stock-location").param("stockLocation", stock))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.data[0].stockLocation").value(stock))
            .andExpect(jsonPath("$.data[0].carsPerContainer").value(2))
            .andExpect(jsonPath("$.data[0].shippingPricePerCar").value(17000))

        mockMvc.perform(get("/shipping-charge-map/mappings/by-stock-location").param("stockLocation", other))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.data[0].carsPerContainer").value(4))
            .andExpect(jsonPath("$.data[0].shippingPricePerCar").value(9000))

        mockMvc.perform(
            post("/shipping-charge-map/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stockLocation":"$stock","carsPerContainer":2,"shippingPricePerCar":1}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("A row already exists for this stock location and cars-per-container."))

        assertEquals(1, shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(stock).size)
    }

    @Test
    fun replaceTiersReplacesThatStockWithoutDuplicatingOrTouchingAnother() {
        val stock = track("P0REP${token()}")
        val other = track("P0REPOTH${token()}")
        add(stock, 2, "17000")
        add(other, 3, "8000")

        mockMvc.perform(
            put("/shipping-charge-map/mappings/replace-tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"stockLocation":"$stock","tiers":[{"carsPerContainer":5,"shippingPricePerCar":21000}]}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.data[0].carsPerContainer").value(5))
            .andExpect(jsonPath("$.data[0].shippingPricePerCar").value(21000))

        val replaced = shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(stock)
        assertEquals(1, replaced.size)
        assertEquals(5, replaced.single().carsPerContainer)
        assertEquals(0, BigDecimal("21000.00").compareTo(replaced.single().shippingPricePerCar))
        val untouched = shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(other)
        assertEquals(1, untouched.size)
        assertEquals(3, untouched.single().carsPerContainer)
    }

    @Test
    fun deleteByIdAndByStockLocationRemoveOnlyTheRequestedRows() {
        val stock = track("P0DEL${token()}")
        val other = track("P0DELOTH${token()}")
        val firstId = add(stock, 2, "1000")
        add(stock, 4, "2000")
        add(other, 2, "3000")

        mockMvc.perform(delete("/shipping-charge-map/mappings/$firstId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        val afterIdDelete = shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(stock)
        assertEquals(listOf(4), afterIdDelete.map { it.carsPerContainer })
        assertEquals(1, shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(other).size)

        mockMvc.perform(delete("/shipping-charge-map/mappings/by-stock-location").param("stockLocation", stock))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertTrue(shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(stock).isEmpty())
        assertEquals(1, shippingChargeMapRepository.findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(other).size)
    }

    private fun add(stock: String, cars: Int, price: String): Long {
        val json = mockMvc.perform(
            post("/shipping-charge-map/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stockLocation":"$stock","carsPerContainer":$cars,"shippingPricePerCar":$price}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong()
        return requireNotNull(id)
    }

    private fun track(stock: String): String {
        stocks.add(stock)
        return stock
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
