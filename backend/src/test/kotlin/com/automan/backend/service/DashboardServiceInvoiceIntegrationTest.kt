package com.automan.backend.service

import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceInvoiceIntegrationTest {

    @Autowired
    private lateinit var dashboardService: DashboardService

    @Autowired
    private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository

    @Autowired
    private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository

    @BeforeEach
    fun clearInvoices() {
        invoiceHistoryLineRepository.deleteAll()
        invoiceHistoryRepository.deleteAll()
    }

    @Test
    fun salesByClient_emptyWhenNoInvoicesInPeriod() {
        val response = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30")
        assertTrue(response.charts.salesByClient.isEmpty())
    }

    @Test
    fun salesByClient_includesShippingDateAndCreatedAtFallback() {
        saveInvoice(
            clientName = "Alpha",
            shippingDate = LocalDate.of(2026, 6, 10),
            createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
            lineAmount = "1,000",
        )
        saveInvoice(
            clientName = "Beta",
            shippingDate = LocalDate.of(2024, 12, 1),
            createdAt = LocalDateTime.of(2026, 6, 20, 14, 30),
            lineAmount = "500",
        )
        saveInvoice(
            clientName = "Outside",
            shippingDate = LocalDate.of(2026, 7, 1),
            createdAt = LocalDateTime.of(2026, 7, 1, 10, 0),
            lineAmount = "9,999",
        )

        val sales = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30")
            .charts.salesByClient
            .associate { it.name to it.value }

        assertEquals(1000.0, sales["Alpha"])
        assertEquals(500.0, sales["Beta"])
        assertEquals(null, sales["Outside"])
    }

    @Test
    fun salesByClient_groupsMultipleLinesAndUsesUnknownForBlankClient() {
        val header = invoiceHistoryRepository.save(
            InvoiceHistory(
                invoiceNumber = invNo(),
                clientName = "  ",
                shippingDate = LocalDate.of(2026, 6, 15),
            ),
        )
        invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(invoiceHistoryId = header.id!!, chassis = "A", lineAmount = "100", sortOrder = 1),
        )
        invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(invoiceHistoryId = header.id!!, chassis = "B", lineAmount = "250", sortOrder = 2),
        )
        saveInvoice(
            clientName = "Gamma",
            shippingDate = LocalDate.of(2026, 6, 16),
            createdAt = LocalDateTime.of(2026, 6, 16, 8, 0),
            lineAmount = "50",
        )

        val sales = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30")
            .charts.salesByClient
            .associate { it.name to it.value }

        assertEquals(350.0, sales["Unknown"])
        assertEquals(50.0, sales["Gamma"])
    }

    @Test
    fun salesByClient_respectsInclusivePeriodBoundaries() {
        saveInvoice(
            clientName = "Start",
            shippingDate = LocalDate.of(2026, 6, 1),
            createdAt = LocalDateTime.of(2026, 5, 1, 0, 0),
            lineAmount = "10",
        )
        saveInvoice(
            clientName = "End",
            shippingDate = LocalDate.of(2026, 6, 30),
            createdAt = LocalDateTime.of(2026, 5, 1, 0, 0),
            lineAmount = "20",
        )
        saveInvoice(
            clientName = "Before",
            shippingDate = LocalDate.of(2026, 5, 31),
            createdAt = LocalDateTime.of(2026, 5, 31, 23, 59),
            lineAmount = "100",
        )

        val sales = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30")
            .charts.salesByClient
            .associate { it.name to it.value }

        assertEquals(10.0, sales["Start"])
        assertEquals(20.0, sales["End"])
        assertEquals(null, sales["Before"])
    }

    @Test
    fun avgMonthlyRevenue_usesInvoiceLinesInPeriod() {
        saveInvoice(
            clientName = "Client",
            shippingDate = LocalDate.of(2026, 6, 15),
            createdAt = LocalDateTime.of(2026, 6, 15, 0, 0),
            lineAmount = "3,000",
        )

        val kpis = dashboardService.getDashboard("custom", "2026-06-01", "2026-06-30").kpis
        val revenue = kpis.first { it.id == "avg_monthly_revenue" }
        assertEquals(3000.0, revenue.value, 0.001)
    }

    private fun saveInvoice(
        clientName: String,
        shippingDate: LocalDate,
        createdAt: LocalDateTime,
        lineAmount: String,
    ) {
        val header = invoiceHistoryRepository.save(
            InvoiceHistory(
                invoiceNumber = invNo(),
                clientName = clientName,
                shippingDate = shippingDate,
                createdAt = createdAt,
            ),
        )
        invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(
                invoiceHistoryId = header.id!!,
                chassis = "CH-${token()}",
                lineAmount = lineAmount,
                sortOrder = 1,
            ),
        )
    }

    private fun invNo(): String = "DASH-INV-${token()}"

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
