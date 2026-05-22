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
import org.mockito.ArgumentCaptor
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
    fun `syncInvoiceLedger skips when already balanced to target`() {
        `when`(eventRepository.findByInvoiceNumberOrderByIdDesc("INV-100")).thenReturn(emptyList())
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
        `when`(eventRepository.findByInvoiceNumberOrderByIdDesc("INV-100")).thenReturn(emptyList())
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

    @Test
    fun `syncInvoiceLedger zero total reverses invoice charge from previous client`() {
        `when`(eventRepository.findByInvoiceNumberOrderByIdDesc("INV-100")).thenReturn(
            listOf(
                Event(id = 1L, clientId = 3L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 850_000.0, runningBalance = -850_000.0),
            ),
        )
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(3L, "INV-100"),
        ).thenReturn(
            listOf(
                Event(id = 1L, clientId = 3L, eventDate = LocalDate.now(), eventType = EventType.INVOICE_ISSUED, transactionPrice = 850_000.0, runningBalance = -850_000.0),
            ),
        )
        `when`(
            eventRepository.findByClientIdAndInvoiceNumberOrderByIdDesc(2L, "INV-100"),
        ).thenReturn(emptyList())
        `when`(clientRepository.findById(3L)).thenReturn(
            Optional.of(Client(id = 3L, clientNumber = "C3", clientName = "Previous Client")),
        )
        `when`(eventRepository.calculateTotalPaymentsByClientId(3L)).thenReturn(0.0)
        `when`(eventRepository.calculateTotalTransactionPricesByClientId(3L)).thenReturn(850_000.0)
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { it.arguments[0] as Client }
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { inv ->
            val e = inv.arguments[0] as Event
            e.copy(id = 99L)
        }

        val result = eventService.syncInvoiceLedger(
            clientId = 2L,
            invoiceNumber = "INV-100",
            eventDate = LocalDate.of(2026, 5, 20),
            transactionPriceTotal = 0.0,
            lineCount = 1,
            vessel = "PACIFIC STAR",
        )

        assertTrue(result.reversed)
        val captor = ArgumentCaptor.forClass(Event::class.java)
        verify(eventRepository).save(captor.capture())
        assertEquals(3L, captor.value.clientId)
        assertEquals(EventType.INVOICE_REVERSAL, captor.value.eventType)
    }
}
