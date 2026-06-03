package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class EventServiceInvoiceLedgerTest {

    @Mock private lateinit var eventRepository: EventRepository
    @Mock private lateinit var clientRepository: ClientRepository
    @InjectMocks private lateinit var eventService: EventService

    private val client = Client(id = 2L, clientNumber = "C2", clientName = "Crown Eagle")

    @Test
    fun `openInvoiceLedgerCharge subtracts reversals from issued`() {
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-100"),
        ).thenReturn(
            listOf(
                Event(id = 1L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 500_000.0, runningBalance = -500_000.0),
                Event(id = 2L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_REVERSAL, paymentReceived = 200_000.0, runningBalance = -300_000.0),
            ),
        )

        assertEquals(300_000.0, eventService.openInvoiceLedgerCharge(2L, "INV-100"), 0.01)
    }

    @Test
    fun `findActiveInvoiceLedgerClientIds returns only clients with unreversed invoice charges`() {
        `when`(
            eventRepository.findByInvoiceNumberOrderByIdDesc("INV-200"),
        ).thenReturn(
            listOf(
                Event(id = 4L, clientId = 4L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_REVERSAL, paymentReceived = 50_000.0, runningBalance = 0.0),
                Event(id = 3L, clientId = 4L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 50_000.0, runningBalance = -50_000.0),
                Event(id = 2L, clientId = 3L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 125_000.0, runningBalance = -125_000.0),
                Event(id = 1L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.OTHER, transactionPrice = 999_000.0, runningBalance = -999_000.0),
            ),
        )

        assertEquals(listOf(3L), eventService.findActiveInvoiceLedgerClientIds(" INV-200 "))
    }

    @Test
    fun `findActiveInvoiceLedgerClientIds returns all clients with open charges for ambiguous deletes`() {
        `when`(
            eventRepository.findByInvoiceNumberOrderByIdDesc("INV-300"),
        ).thenReturn(
            listOf(
                Event(id = 2L, clientId = 3L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 80_000.0, runningBalance = -80_000.0),
                Event(id = 1L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 120_000.0, runningBalance = -120_000.0),
            ),
        )

        assertEquals(listOf(3L, 2L), eventService.findActiveInvoiceLedgerClientIds("INV-300"))
    }

    @Test
    fun `syncInvoiceLedger skips when already balanced to target`() {
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-100"),
        ).thenReturn(
            listOf(
                Event(id = 1L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 850_000.0, runningBalance = -850_000.0),
            ),
        )

        val result = eventService.syncInvoiceLedger(
            clientId = 2L,
            invoiceNumber = "INV-100",
            eventDate = LocalDate.of(2026, 5, 20),
            transactionPriceTotal = 850_000.0,
            lineCount = 3,
            vessel = "PACIFIC STAR",
        )

        assertFalse(result.posted)
        assertFalse(result.reversed)
        assertEquals(2L, result.clientId)
        verify(eventRepository, never()).save(any(Event::class.java))
    }

    @Test
    fun `reverseActiveInvoiceLedger posts credit for open charge`() {
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-100"),
        ).thenReturn(
            listOf(
                Event(id = 1L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 850_000.0, runningBalance = -850_000.0),
            ),
        )
        `when`(clientRepository.findById(2L)).thenReturn(Optional.of(client))
        `when`(eventRepository.calculateTotalPaymentsByClientId(2L)).thenReturn(0.0)
        `when`(eventRepository.calculateTotalTransactionPricesByClientId(2L)).thenReturn(850_000.0)
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { it.arguments[0] as Client }
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { inv ->
            val e = inv.arguments[0] as Event
            e.copy(id = 99L)
        }

        val saved = eventService.reverseActiveInvoiceLedger(
            clientId = 2L,
            invoiceNumber = "INV-100",
            eventDate = LocalDate.of(2026, 5, 20),
            vessel = "PACIFIC STAR",
        )

        assertNotNull(saved)
        assertEquals(EventType.INVOICE_REVERSAL, saved?.eventType)
        assertEquals(850_000.0, saved?.paymentReceived ?: 0.0, 0.01)
    }

    @Test
    fun `reverseActiveInvoiceLedger is idempotent when nothing open`() {
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-100"),
        ).thenReturn(emptyList())

        val saved = eventService.reverseActiveInvoiceLedger(
            clientId = 2L,
            invoiceNumber = "INV-100",
            eventDate = LocalDate.of(2026, 5, 20),
            vessel = null,
        )

        assertNull(saved)
        verify(eventRepository, never()).save(any(Event::class.java))
    }

    @Test
    fun `syncInvoiceLedger reverses and reposts when total changes`() {
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-100"),
        ).thenReturn(
            listOf(
                Event(id = 1L, clientId = 2L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 500_000.0, runningBalance = -500_000.0),
            ),
        )
        `when`(clientRepository.findById(2L)).thenReturn(Optional.of(client))
        `when`(eventRepository.calculateTotalPaymentsByClientId(2L)).thenReturn(0.0, 500_000.0)
        `when`(eventRepository.calculateTotalTransactionPricesByClientId(2L)).thenReturn(500_000.0, 500_000.0)
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { it.arguments[0] as Client }
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { inv ->
            val e = inv.arguments[0] as Event
            e.copy(id = System.nanoTime())
        }

        val result = eventService.syncInvoiceLedger(
            clientId = 2L,
            invoiceNumber = "INV-100",
            eventDate = LocalDate.of(2026, 5, 20),
            transactionPriceTotal = 900_000.0,
            lineCount = 2,
            vessel = "PACIFIC STAR",
        )

        assertTrue(result.reversed)
        assertTrue(result.posted)
        verify(eventRepository, times(2)).save(any(Event::class.java))
    }
}
