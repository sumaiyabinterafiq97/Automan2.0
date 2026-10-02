package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardRecentPurchasesDateIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `client date wins over the server calendar day`() {
        val serverToday = LocalDate.now().format(ENGLISH_DATE)
        purchaseRepository.save(Purchase(chassis = "LAPTOP-DAY", date = "June 15, 1999", brand = "TOYOTA", carName = "RAV4"))
        purchaseRepository.save(Purchase(chassis = "SERVER-DAY", date = serverToday, brand = "TOYOTA", carName = "RAV4"))

        mockMvc.perform(
            get("/dashboard/recent-purchases")
                .param("day", "today")
                .param("date", "1999-06-15"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.day").value("today"))
            .andExpect(jsonPath("$.rows[*].chassis", hasItem("LAPTOP-DAY")))
            .andExpect(jsonPath("$.rows[*].chassis", not(hasItem("SERVER-DAY"))))
    }

    @Test
    fun `missing date still uses the server day`() {
        val serverToday = LocalDate.now().format(ENGLISH_DATE)
        purchaseRepository.save(Purchase(chassis = "LAPTOP-DAY", date = "June 15, 1999", brand = "TOYOTA", carName = "RAV4"))
        purchaseRepository.save(Purchase(chassis = "SERVER-DAY", date = serverToday, brand = "TOYOTA", carName = "RAV4"))

        mockMvc.perform(get("/dashboard/recent-purchases").param("day", "today"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rows[*].chassis", hasItem("SERVER-DAY")))
            .andExpect(jsonPath("$.rows[*].chassis", not(hasItem("LAPTOP-DAY"))))
    }

    @Test
    fun `invalid date is rejected`() {
        mockMvc.perform(
            get("/dashboard/recent-purchases")
                .param("day", "today")
                .param("date", "yesterday"),
        )
            .andExpect(status().isBadRequest)
    }

    companion object {
        private val ENGLISH_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    }
}
