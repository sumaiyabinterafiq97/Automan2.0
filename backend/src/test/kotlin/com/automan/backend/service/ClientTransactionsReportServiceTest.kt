package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ClientTransactionsReportServiceTest {

    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var eventRepository: EventRepository
    @InjectMocks private lateinit var reportService: ClientTransactionsReportService

    @Test
    fun `aging buckets open invoice by days outstanding`() {
        val client = Client(id = 1L, clientNumber = "CL0001", clientName = "Crown Eagle")
        `when`(clientRepository.findAll()).thenReturn(listOf(client))
        val asOf = LocalDate.of(2026, 5, 20)
        val invoiceDate = LocalDate.of(2026, 3, 1) // 80 days -> 61-90

        `when`(
            eventRepository.findByEventTypeInOrderByClientIdAscEventDateAsc(
                listOf(EventType.INVOICE_ISSUED, EventType.INVOICE_REVERSAL),
            ),
        ).thenReturn(
            listOf(
                Event(
                    id = 1L,
                    clientId = 1L,
                    eventDate = invoiceDate,
                    eventType = EventType.INVOICE_ISSUED,
                    invoiceNumber = "INV-100",
                    transactionPrice = 100_000.0,
                    runningBalance = -100_000.0,
                ),
            ),
        )

        val report = reportService.buildUnpaidAgingReport(asOf)
        assertEquals(1, report.rows.size)
        assertEquals(ClientTransactionsReportService.BUCKET_61_90, report.rows[0].agingBucket)
        assertEquals(100_000.0, report.rows[0].openAmount, 0.01)
        assertEquals(80L, report.rows[0].daysOutstanding)
        assertEquals(100_000.0, report.summaries[0].bucket61to90, 0.01)
    }

    @Test
    fun `aging subtracts reversals from issued amount`() {
        val client = Client(id = 2L, clientNumber = "CL0002", clientName = "DAAVI")
        `when`(clientRepository.findAll()).thenReturn(listOf(client))
        val asOf = LocalDate.of(2026, 5, 20)

        `when`(
            eventRepository.findByEventTypeInOrderByClientIdAscEventDateAsc(
                listOf(EventType.INVOICE_ISSUED, EventType.INVOICE_REVERSAL),
            ),
        ).thenReturn(
            listOf(
                Event(
                    id = 1L,
                    clientId = 2L,
                    eventDate = LocalDate.of(2026, 5, 1),
                    eventType = EventType.INVOICE_ISSUED,
                    invoiceNumber = "INV-200",
                    transactionPrice = 50_000.0,
                    runningBalance = -50_000.0,
                ),
                Event(
                    id = 2L,
                    clientId = 2L,
                    eventDate = LocalDate.of(2026, 5, 10),
                    eventType = EventType.INVOICE_REVERSAL,
                    invoiceNumber = "INV-200",
                    paymentReceived = 20_000.0,
                    runningBalance = -30_000.0,
                ),
            ),
        )

        val report = reportService.buildUnpaidAgingReport(asOf)
        assertEquals(1, report.rows.size)
        assertEquals(30_000.0, report.rows[0].openAmount, 0.01)
        assertEquals(ClientTransactionsReportService.BUCKET_0_30, report.rows[0].agingBucket)
    }

    @Test
    fun `fully reversed invoice excluded from aging`() {
        val client = Client(id = 3L, clientNumber = "CL0003", clientName = "Test Co")
        `when`(clientRepository.findAll()).thenReturn(listOf(client))

        `when`(
            eventRepository.findByEventTypeInOrderByClientIdAscEventDateAsc(
                listOf(EventType.INVOICE_ISSUED, EventType.INVOICE_REVERSAL),
            ),
        ).thenReturn(
            listOf(
                Event(
                    id = 1L,
                    clientId = 3L,
                    eventDate = LocalDate.of(2026, 4, 1),
                    eventType = EventType.INVOICE_ISSUED,
                    invoiceNumber = "INV-300",
                    transactionPrice = 10_000.0,
                    runningBalance = -10_000.0,
                ),
                Event(
                    id = 2L,
                    clientId = 3L,
                    eventDate = LocalDate.of(2026, 4, 2),
                    eventType = EventType.INVOICE_REVERSAL,
                    invoiceNumber = "INV-300",
                    paymentReceived = 10_000.0,
                    runningBalance = 0.0,
                ),
            ),
        )

        val report = reportService.buildUnpaidAgingReport(LocalDate.of(2026, 5, 20))
        assertTrue(report.rows.isEmpty())
        assertEquals(0.0, report.totalOpen, 0.01)
    }

    @Test
    fun `statement filters events by date range`() {
        val client = Client(
            id = 4L,
            clientNumber = "CL0004",
            clientName = "Filter Client",
            currentBalance = 50_000.0,
        )
        `when`(clientRepository.findById(4L)).thenReturn(Optional.of(client))
        `when`(eventRepository.findByClientIdOrderByEventDateAscCreatedAtAsc(4L)).thenReturn(
            listOf(
                event(4L, LocalDate.of(2026, 1, 1), EventType.PAYMENT_RECEIVED, payment = 100_000.0, balance = 100_000.0),
                event(4L, LocalDate.of(2026, 2, 1), EventType.INVOICE_ISSUED, debit = 50_000.0, balance = 50_000.0),
            ),
        )

        val statement = reportService.buildClientStatement(
            4L,
            startDate = LocalDate.of(2026, 2, 1),
            endDate = LocalDate.of(2026, 2, 28),
        )
        assertEquals(1, statement.lines.size)
        assertEquals("Invoice", statement.lines[0].typeLabel)
    }

    private fun event(
        clientId: Long,
        date: LocalDate,
        type: EventType,
        debit: Double? = null,
        payment: Double? = null,
        balance: Double,
    ) = Event(
        clientId = clientId,
        eventDate = date,
        eventType = type,
        transactionPrice = debit,
        paymentReceived = payment,
        runningBalance = balance,
    )
}
