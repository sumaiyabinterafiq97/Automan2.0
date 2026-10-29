package com.automan.backend.controller

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardInvoiceQueryIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @SpyBean
    private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository

    @BeforeEach
    fun seedMinimalInvoice() {
        invoiceHistoryRepository.save(
            InvoiceHistory(
                invoiceNumber = "DASH-Q-${UUID.randomUUID().toString().take(8)}",
                clientName = "Probe",
                shippingDate = LocalDate.of(2026, 6, 12),
                createdAt = LocalDateTime.of(2026, 6, 12, 10, 0),
            ),
        )
    }

    @Test
    fun `GET dashboard uses period-scoped invoice header query not findAll`() {
        mockMvc.perform(
            get("/dashboard")
                .param("period", "custom")
                .param("from", "2026-06-01")
                .param("to", "2026-06-30"),
        )
            .andExpect(status().isOk)

        verify(invoiceHistoryRepository, times(1)).findDashboardHeadersInPeriod(
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30),
            LocalDate.of(2026, 6, 1).atStartOfDay(),
            LocalDate.of(2026, 7, 1).atStartOfDay(),
        )
        verify(invoiceHistoryRepository, times(1)).findDashboardHeadersInPeriod(
            LocalDate.of(2026, 5, 2),
            LocalDate.of(2026, 5, 31),
            LocalDate.of(2026, 5, 2).atStartOfDay(),
            LocalDate.of(2026, 6, 1).atStartOfDay(),
        )
        verify(invoiceHistoryRepository, never()).findAll()
    }
}
