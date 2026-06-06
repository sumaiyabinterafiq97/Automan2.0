package com.automan.backend.service

import com.automan.backend.config.AppConstants
import com.automan.backend.dto.CreditLimitStatus
import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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
class ClientServiceCreditLimitTest {

    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var eventRepository: EventRepository
    @InjectMocks private lateinit var clientService: ClientService

    @Test
    fun `projected balance replaces open invoice charge`() {
        val client = Client(id = 1L, clientNumber = "C1", clientName = "Crown Eagle", currentBalance = -100_000.0, creditLimit = 500.0)
        `when`(clientRepository.findById(1L)).thenReturn(Optional.of(client))
        `when`(eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(1L, "INV-1")).thenReturn(
            listOf(
                Event(
                    id = 10L,
                    clientId = 1L,
                    eventDate = LocalDate.now(),
                    eventType = EventType.INVOICE_ISSUED,
                    transactionPrice = 50_000.0,
                    runningBalance = -150_000.0,
                ),
            ),
        )

        val projected = clientService.projectedBalanceAfterInvoiceCharge(1L, "INV-1", 80_000.0)

        // -100k - (80k - 50k open) = -130k
        assertEquals(-130_000.0, projected!!, 0.01)
    }

    @Test
    fun `assess returns OVER_LIMIT when projected below negative limit`() {
        val client = Client(id = 1L, clientNumber = "C1", clientName = "Crown Eagle", currentBalance = -400.0, creditLimit = 500.0)
        `when`(clientRepository.findById(1L)).thenReturn(Optional.of(client))
        `when`(eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(1L, "INV-2")).thenReturn(emptyList())

        val assessment = clientService.assessCreditForInvoiceCharge(1L, "INV-2", 200_000.0)!!

        assertEquals(CreditLimitStatus.OVER_LIMIT, assessment.status)
        assertTrue(assessment.blocked == AppConstants.BLOCK_INVOICE_WHEN_OVER_CREDIT_LIMIT)
    }

    @Test
    fun `enforce throws when over limit and block enabled`() {
        val client = Client(id = 1L, clientNumber = "C1", clientName = "Test", currentBalance = 0.0, creditLimit = 1000.0)
        `when`(clientRepository.findById(1L)).thenReturn(Optional.of(client))
        `when`(eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(1L, "INV-3")).thenReturn(emptyList())

        assertThrows(IllegalArgumentException::class.java) {
            clientService.enforceInvoiceCreditLimit(1L, "INV-3", 5000.0)
        }
    }

    @Test
    fun `assess returns NO_LIMIT when client has no limit`() {
        val client = Client(id = 2L, clientNumber = "C2", clientName = "DAAVI AUTO", currentBalance = -24_623.0)
        `when`(clientRepository.findById(2L)).thenReturn(Optional.of(client))
        `when`(eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-4")).thenReturn(emptyList())

        val assessment = clientService.assessCreditForInvoiceCharge(2L, "INV-4", 1_000_000.0)!!

        assertEquals(CreditLimitStatus.NO_LIMIT, assessment.status)
        assertEquals(false, assessment.blocked)
    }
}
