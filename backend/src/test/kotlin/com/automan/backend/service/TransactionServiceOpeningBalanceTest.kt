package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.OpeningBalanceImportRequest
import com.automan.backend.dto.OpeningBalanceImportRowDto
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
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class TransactionServiceOpeningBalanceTest {

    @Mock private lateinit var eventService: EventService
    @Mock private lateinit var clientService: ClientService
    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var eventRepository: EventRepository
    @InjectMocks private lateinit var transactionService: TransactionService

    @Test
    fun `create opening balance rejects duplicate`() {
        val client = Client(id = 1L, clientNumber = "CL0001", clientName = "Test")
        `when`(clientService.getClientById(1L)).thenReturn(client)
        `when`(eventRepository.countByClientIdAndEventType(1L, EventType.OPENING_BALANCE)).thenReturn(1L)

        val result = transactionService.createTransaction(
            CreateTransactionRequest(
                clientId = 1L,
                eventDate = "2026-01-01",
                eventType = EventType.OPENING_BALANCE,
                eventDescription = "Opening",
                paymentReceived = 100.0,
            ),
        )

        assertTrue(!result.success)
        assertTrue(result.message.contains("already has an opening balance"))
    }

    @Test
    fun `import skips unknown client number`() {
        `when`(clientRepository.findByClientNumber("MISSING")).thenReturn(null)

        val result = transactionService.importOpeningBalances(
            OpeningBalanceImportRequest(
                rows = listOf(
                    OpeningBalanceImportRowDto(
                        clientNumber = "MISSING",
                        eventDate = "2026-01-01",
                        amount = 10_000.0,
                    ),
                ),
            ),
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertTrue(result.errors.any { it.contains("not found") })
    }

    @Test
    fun `signed amount maps positive to credit and negative to debit`() {
        val credit = TransactionService.signedAmountToLedger(25_000.0)
        assertEquals(25_000.0, credit.first)
        assertEquals(null, credit.second)

        val debit = TransactionService.signedAmountToLedger(-12_000.0)
        assertEquals(null, debit.first)
        assertEquals(12_000.0, debit.second)
    }

    @Test
    fun `update manual transaction rejects invalid merged payment before saving`() {
        val existing = Event(
            id = 7L,
            clientId = 1L,
            eventDate = LocalDate.of(2026, 1, 1),
            eventType = EventType.PAYMENT_RECEIVED,
            paymentReceived = 100_000.0,
            runningBalance = 100_000.0,
        )
        `when`(eventService.getEventById(7L)).thenReturn(existing)

        val result = transactionService.updateManualTransaction(
            7L,
            mapOf(
                "paymentReceived" to 100_000.0,
                "transactionPrice" to 50_000.0,
            ),
        )

        assertTrue(!result.success)
        assertTrue(result.message.contains("Payment entries cannot include"))
        verify(eventService, never()).updateManualEvent(eq(7L), anyMap())
    }
}
