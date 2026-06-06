package com.automan.backend.service

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.OpeningBalanceImportRequest
import com.automan.backend.dto.OpeningBalanceImportRowDto
import com.automan.backend.model.Client
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
}
