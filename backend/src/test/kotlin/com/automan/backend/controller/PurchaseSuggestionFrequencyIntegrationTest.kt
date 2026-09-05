package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseSuggestionFrequencyIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `GET suggestion-frequency counts stock and rixo by supplier`() {
        repeat(3) { i ->
            purchaseRepository.save(
                Purchase(
                    chassis = "USS-GN-$i",
                    auctionHouse = "USS NAGOYA",
                    stockLocation = "GLOBAL NAGOYA",
                    rixoCompany = "KLC",
                ),
            )
        }
        purchaseRepository.save(
            Purchase(
                chassis = "USS-KLC-1",
                auctionHouse = "USS NAGOYA",
                stockLocation = "KLC",
                rixoCompany = "LOGICO",
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "OTHER-1",
                auctionHouse = "USS TOKYO",
                stockLocation = "GLOBAL NAGOYA",
                rixoCompany = "KLC",
            ),
        )

        val json = mockMvc.perform(get("/purchases/suggestion-frequency").param("supplier", "uss nagoya"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val tree = objectMapper.readTree(json)
        assertEquals(3, tree.path("stockLocation").path("global nagoya").asInt())
        assertEquals(1, tree.path("stockLocation").path("klc").asInt())
        assertEquals(3, tree.path("rixoCompany").path("klc").asInt())
        assertEquals(1, tree.path("rixoCompany").path("logico").asInt())
        assertEquals(3, tree.path("rixoCompanyByStock").path("global nagoya").path("klc").asInt())
        assertEquals(1, tree.path("rixoCompanyByStock").path("klc").path("logico").asInt())
    }

    @Test
    fun `GET suggestion-frequency unknown supplier returns empty maps`() {
        purchaseRepository.save(
            Purchase(
                chassis = "USS-1",
                auctionHouse = "USS NAGOYA",
                stockLocation = "GLOBAL NAGOYA",
                rixoCompany = "KLC",
            ),
        )
        val json = mockMvc.perform(get("/purchases/suggestion-frequency").param("supplier", "NO SUCH AUCTION"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        val tree = objectMapper.readTree(json)
        assertTrue(tree.path("stockLocation").isEmpty)
        assertTrue(tree.path("rixoCompany").isEmpty)
        assertTrue(tree.path("rixoCompanyByStock").isEmpty)
    }
}
