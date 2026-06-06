package com.automan.backend.service

import com.automan.backend.dto.ClientNameLedgerResolution
import com.automan.backend.model.Client
import com.automan.backend.model.ClientStatus
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class ClientServiceLedgerResolveTest {

    @Mock private lateinit var clientRepository: ClientRepository
    @Mock private lateinit var eventRepository: EventRepository
    @InjectMocks private lateinit var clientService: ClientService

    @Test
    fun `preview marks unknown client as will create`() {
        `when`(clientRepository.findByClientNameIgnoreCase("DAAVI AUTO")).thenReturn(emptyList())

        val preview = clientService.previewClientNameForLedger("DAAVI AUTO")

        assertTrue(preview.ledgerResolvable)
        assertTrue(preview.willCreateClient)
        assertEquals(null, preview.warning)
    }

    @Test
    fun `resolve creates client when name is new`() {
        `when`(clientRepository.findByClientNameIgnoreCase("DAAVI AUTO")).thenReturn(emptyList())
        `when`(clientRepository.count()).thenReturn(5L)
        `when`(clientRepository.existsByClientNumber("CL0006")).thenReturn(false)
        `when`(clientRepository.save(any(Client::class.java))).thenAnswer { inv ->
            val c = inv.arguments[0] as Client
            c.copy(id = 42L)
        }

        val result = clientService.resolveClientNameForLedger("DAAVI AUTO")

        assertTrue(result is ClientNameLedgerResolution.Ok)
        val ok = result as ClientNameLedgerResolution.Ok
        assertEquals(42L, ok.clientId)
        assertTrue(ok.created)
    }

    @Test
    fun `resolve reuses existing client`() {
        `when`(clientRepository.findByClientNameIgnoreCase("Crown Eagle")).thenReturn(
            listOf(Client(id = 2L, clientNumber = "CL0002", clientName = "Crown Eagle")),
        )

        val result = clientService.resolveClientNameForLedger("Crown Eagle")

        assertTrue(result is ClientNameLedgerResolution.Ok)
        val ok = result as ClientNameLedgerResolution.Ok
        assertEquals(2L, ok.clientId)
        assertFalse(ok.created)
    }

    @Test
    fun `preview uses invoice client name over purchase client id`() {
        `when`(clientRepository.findByClientNameIgnoreCase("AUTOHANDLER")).thenReturn(
            listOf(Client(id = 5L, clientNumber = "CL0004", clientName = "AUTOHANDLER", creditLimit = 5_000_000.0)),
        )

        val preview = clientService.previewClientNameForLedger("AUTOHANDLER", listOf(1L))

        assertTrue(preview.ledgerResolvable)
        assertEquals(5L, preview.clientId)
        assertFalse(preview.willCreateClient)
    }

    @Test
    fun `resolve skips duplicate names`() {
        `when`(clientRepository.findByClientNameIgnoreCase("ABC TRADING")).thenReturn(
            listOf(
                Client(id = 1L, clientNumber = "CL0001", clientName = "ABC TRADING"),
                Client(id = 2L, clientNumber = "CL0002", clientName = "ABC TRADING"),
            ),
        )

        val result = clientService.resolveClientNameForLedger("ABC TRADING")

        assertTrue(result is ClientNameLedgerResolution.Skipped)
    }
}
