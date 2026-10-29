package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardRecentPurchasesQueryIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @SpyBean
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun seedPurchaseOnTargetDay() {
        purchaseRepository.save(
            Purchase(
                chassis = "RECENT-Q-1",
                date = "June 15, 2026 (Sunday)",
                brand = "TOYOTA",
                carName = "Prius",
            ),
        )
    }

    @Test
    fun `GET recent-purchases uses date candidate query not full projection scan`() {
        mockMvc.perform(
            get("/dashboard/recent-purchases")
                .param("day", "today")
                .param("date", "2026-06-15"),
        )
            .andExpect(status().isOk)

        verify(purchaseRepository).findIdAndDateRixoDateCandidates("2026-06-15", "June%15%2026")
        verify(purchaseRepository, never()).findRecentPurchaseCandidates()
    }
}
