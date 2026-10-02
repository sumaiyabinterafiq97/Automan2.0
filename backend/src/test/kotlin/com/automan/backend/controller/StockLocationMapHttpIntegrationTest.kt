package com.automan.backend.controller

import com.automan.backend.repository.StockLocationMapRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.util.UUID

/**
 * Stock location map HTTP. One stock location is one row.
 * POL token splitting is already covered by StockLocationMapServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StockLocationMapHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var stockLocationMapRepository: StockLocationMapRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val stocks = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            stocks.toList().forEach { stock ->
                stockLocationMapRepository.findByStockLocationIgnoreCase(stock)?.let {
                    stockLocationMapRepository.delete(it)
                }
            }
        }
        stocks.clear()
    }

    @Test
    fun createUpdateAndDeleteAffectOnlyTheRequestedStockLocation() {
        val stock = track("P0STK${token()}")
        val other = track("P0OTH${token()}")
        val id = add(stock, "YOKOHAMA", "Yard A")
        val otherId = add(other, "KOBE", "Yard B")

        val listed = mockMvc.perform(
            get("/stock-location-map/mappings/page-search")
                .param("q", stock)
                .param("field", "stockLocation")
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].stockLocation").value(stock))
            .andExpect(jsonPath("$.content[0].pol").value("YOKOHAMA"))
            .andReturn().response.contentAsString
        assertFalse(listed.contains(other))

        mockMvc.perform(
            put("/stock-location-map/mappings/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stockLocation":"$stock","pol":"NAGOYA","address":"Yard A"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.pol").value("NAGOYA"))

        assertEquals("KOBE", stockLocationMapRepository.findById(otherId).orElseThrow().pol)

        mockMvc.perform(
            post("/stock-location-map/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stockLocation":"${stock.lowercase()}","pol":"OSAKA","address":"Yard C"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("A row already exists for this stock location."))

        assertEquals(stock, stockLocationMapRepository.findByStockLocationIgnoreCase(stock)!!.stockLocation)

        mockMvc.perform(delete("/stock-location-map/mappings/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertFalse(stockLocationMapRepository.existsById(id))
        assertTrue(stockLocationMapRepository.existsById(otherId))
    }

    private fun add(stock: String, pol: String, address: String): Long {
        val json = mockMvc.perform(
            post("/stock-location-map/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stockLocation":"$stock","pol":"$pol","address":"$address"}"""),
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
