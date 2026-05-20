package com.automan.backend.service

import com.automan.backend.dto.CreateEventRequest
import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class EventServiceBalanceTest {

    @Mock private lateinit var eventRepository: EventRepository
    @Mock private lateinit var clientRepository: ClientRepository
    @InjectMocks private lateinit var eventService: EventService

    @Test
    fun `createEventFromDto applies Option A balance as payments minus charges`() {
        val client = Client(id = 1L, clientNumber = "C1", clientName = "Test Buyer")
        `when`(clientRepository.findById(1L)).thenReturn(Optional.of(client))
        `when`(eventRepository.calculateTotalPaymentsByClientId(1L)).thenReturn(1_000_000.0)
        `when`(eventRepository.calculateTotalTransactionPricesByClientId(1L)).thenReturn(850_000.0)
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { it.arguments[0] as Client }
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { inv ->
            val e = inv.arguments[0] as Event
            e.copy(id = 99L)
        }

        val saved = eventService.createEventFromDto(
            CreateEventRequest(
                clientId = 1L,
                eventDate = LocalDate.of(2026, 5, 20),
                eventType = EventType.PAYMENT_RECEIVED,
                eventDescription = "TT received",
                paymentReceived = 200_000.0,
            ),
        )

        // previous 1_000_000 - 850_000 = 150_000; +200_000 payment = 350_000
        assertEquals(350_000.0, saved.runningBalance, 0.01)

        val clientCaptor = ArgumentCaptor.forClass(Client::class.java)
        verify(clientRepository).save(clientCaptor.capture())
        assertEquals(350_000.0, clientCaptor.value.currentBalance, 0.01)
    }
}
