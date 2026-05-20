package com.automan.backend.service

import com.automan.backend.model.Client
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class ClientServiceUpdateCreditLimitTest {

    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var eventRepository: EventRepository
    @InjectMocks private lateinit var clientService: ClientService

    @Test
    fun `updateClient clears credit limit when key is present with null`() {
        val existing = Client(
            id = 1L,
            clientNumber = "CL0001",
            clientName = "Test",
            creditLimit = 500.0,
            alertThreshold = 450.0,
        )
        `when`(clientRepository.findById(1L)).thenReturn(Optional.of(existing))
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { inv -> inv.arguments[0] as Client }

        val updated = clientService.updateClient(
            1L,
            mapOf<String, Any?>(
                "creditLimit" to null,
                "alertThreshold" to null,
            ),
        )

        assertNull(updated?.creditLimit)
        assertNull(updated?.alertThreshold)
    }

    @Test
    fun `updateClient sets credit limit and threshold`() {
        val existing = Client(id = 2L, clientNumber = "CL0002", clientName = "DAAVI AUTO")
        `when`(clientRepository.findById(2L)).thenReturn(Optional.of(existing))
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { inv -> inv.arguments[0] as Client }

        val updated = clientService.updateClient(
            2L,
            mapOf(
                "creditLimit" to 50_000_000.0,
                "alertThreshold" to 45_000_000.0,
            ),
        )

        assertEquals(50_000_000.0, updated?.creditLimit)
        assertEquals(45_000_000.0, updated?.alertThreshold)
    }
}
