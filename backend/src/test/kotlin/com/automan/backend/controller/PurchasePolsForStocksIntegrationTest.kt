package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.StockLocationMap
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.StockLocationMapRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchasePolsForStocksIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var stockLocationMapRepository: StockLocationMapRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
        stockLocationMapRepository.deleteAll()
    }

    @Test
    fun `GET pols-for-stocks uses stock location map not purchase POL`() {
        stockLocationMapRepository.save(
            StockLocationMap(stockLocation = "GLOBAL KAWASAKI", pol = "Kawasaki"),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "GK-YOK-1",
                country = "SOUTH AFRICA (DURBAN)",
                stockLocation = "GLOBAL KAWASAKI",
                pol = "YOKOHAMA",
                workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
            ),
        )

        mockMvc.perform(
            get("/purchases/pols-for-stocks")
                .param("country", "SOUTH AFRICA (DURBAN)")
                .param("stockLocations", "GLOBAL KAWASAKI"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value("Kawasaki"))
    }

    @Test
    fun `GET pols-for-stocks unions map POLs and skips blank`() {
        stockLocationMapRepository.save(
            StockLocationMap(stockLocation = "AQUA LOGISTICS", pol = "YOKOHAMA"),
        )
        stockLocationMapRepository.save(
            StockLocationMap(stockLocation = "ECL KOBE", pol = "KOBE"),
        )
        stockLocationMapRepository.save(
            StockLocationMap(stockLocation = "KLC", pol = null),
        )

        mockMvc.perform(
            get("/purchases/pols-for-stocks")
                .param("country", "Japan")
                .param("stockLocations", "AQUA LOGISTICS,ECL KOBE,KLC"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0]").value("YOKOHAMA"))
            .andExpect(jsonPath("$[1]").value("KOBE"))
    }
}
