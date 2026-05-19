package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.util.Optional

class EventServiceTest {

    private val eventRepository = mock(EventRepository::class.java)
    private val clientRepository = mock(ClientRepository::class.java)
    private val eventService = EventService(eventRepository, clientRepository)

    @Test
    fun updateEventSavesEditedValuesBeforeRecalculatingBalances() {
        val client = Client(
            id = 1L,
            clientNumber = "C-001",
            clientName = "Acme",
            currentBalance = 50.0
        )
        val editedEventBeforeUpdate = Event(
            id = 10L,
            clientId = 1L,
            eventDate = LocalDate.parse("2026-01-10"),
            paymentReceived = 100.0,
            runningBalance = 100.0
        )
        val laterEvent = Event(
            id = 20L,
            clientId = 1L,
            eventDate = LocalDate.parse("2026-01-20"),
            transactionPrice = 50.0,
            runningBalance = 50.0
        )
        var editedEventAfterSave = editedEventBeforeUpdate

        `when`(eventRepository.findById(10L)).thenReturn(Optional.of(editedEventBeforeUpdate))
        `when`(clientRepository.findById(1L)).thenReturn(Optional.of(client))
        `when`(eventRepository.findByClientIdOrderByEventDateAscCreatedAtAsc(1L)).thenAnswer {
            listOf(editedEventAfterSave, laterEvent)
        }
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { invocation ->
            val event = invocation.getArgument<Event>(0)
            if (event.id == 10L) {
                editedEventAfterSave = event
            }
            event
        }

        val updated = eventService.updateEvent(
            10L,
            mapOf("paymentReceived" to 120.0)
        )

        assertEquals(10L, updated?.id)
        assertEquals(120.0, updated?.paymentReceived)
        assertEquals(120.0, updated?.runningBalance)

        val savedEvents = ArgumentCaptor.forClass(Event::class.java)
        verify(eventRepository, times(3)).save(savedEvents.capture())
        val recalculatedById = savedEvents.allValues.drop(1).associateBy { it.id }
        assertEquals(120.0, recalculatedById[10L]?.runningBalance)
        assertEquals(70.0, recalculatedById[20L]?.runningBalance)

        val savedClient = ArgumentCaptor.forClass(Client::class.java)
        verify(clientRepository).save(savedClient.capture())
        assertEquals(70.0, savedClient.value.currentBalance)
    }
}
