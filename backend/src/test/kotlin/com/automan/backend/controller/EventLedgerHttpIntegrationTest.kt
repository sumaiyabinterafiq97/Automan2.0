package com.automan.backend.controller

import com.automan.backend.model.Client
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventLedgerHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var eventRepository: EventRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val clientIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            clientIds.distinct().forEach { id ->
                eventRepository.findByClientIdOrderByEventDateDesc(id).forEach { eventRepository.delete(it) }
                if (clientRepository.existsById(id)) clientRepository.deleteById(id)
            }
        }
        clientIds.clear()
    }

    @Test
    fun clientEventListExcludesAnotherClient() {
        val owner = saveClient()
        val other = saveClient()
        val own = saveEvent(owner.id!!, LocalDate.of(2026, 3, 1), "Owner payment")
        saveEvent(other.id!!, LocalDate.of(2026, 3, 1), "Other payment")

        mockMvc.perform(get("/events/client/${owner.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(own.id))
            .andExpect(jsonPath("$[0].clientId").value(owner.id))
            .andExpect(jsonPath("$[0].eventDescription").value("Owner payment"))
    }

    @Test
    fun dateRangeReturnsEventsInsideTheRange() {
        val client = saveClient()
        saveEvent(client.id!!, LocalDate.of(2026, 1, 15), "January")
        val start = saveEvent(client.id!!, LocalDate.of(2026, 2, 1), "February start")
        val mid = saveEvent(client.id!!, LocalDate.of(2026, 2, 15), "February mid")
        saveEvent(client.id!!, LocalDate.of(2026, 3, 1), "March")

        mockMvc.perform(
            get("/events/client/${client.id}/date-range")
                .param("startDate", "2026-02-01")
                .param("endDate", "2026-02-28"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(content().string(containsString("February start")))
            .andExpect(content().string(containsString("February mid")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("January"))))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("\"March\""))))

        assertTrue(eventRepository.existsById(start.id!!))
        assertTrue(eventRepository.existsById(mid.id!!))
    }

    @Test
    fun deleteRemovesOnlyTheRequestedEvent() {
        val client = saveClient()
        val remove = saveEvent(client.id!!, LocalDate.of(2026, 4, 1), "Remove me")
        val keep = saveEvent(client.id!!, LocalDate.of(2026, 4, 2), "Keep me")

        mockMvc.perform(delete("/events/${remove.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertFalse(eventRepository.existsById(remove.id!!))
        assertTrue(eventRepository.existsById(keep.id!!))
    }

    @Test
    fun exportReturnsCsvForThatClientsEvents() {
        val client = saveClient()
        saveEvent(client.id!!, LocalDate.of(2026, 5, 2), "Export payment")

        mockMvc.perform(get("/events/export/${client.id}"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", containsString("text/csv")))
            .andExpect(header().string("Content-Disposition", containsString("client_${client.id}_transactions.csv")))
            .andExpect(content().string(containsString("DATE,Event,QUANTITY,BILL. NO,T.S PRICE,PAYMENT RECEIVED,T. BALANCE")))
            .andExpect(content().string(containsString("Export payment")))
            .andExpect(content().string(containsString("2026-05-02")))
    }

    private fun saveClient(): Client {
        val saved = clientRepository.save(
            Client(
                clientNumber = "CL-${UUID.randomUUID().toString().take(8)}",
                clientName = "P0 Event ${UUID.randomUUID().toString().take(8)}",
            ),
        )
        clientIds.add(saved.id!!)
        return saved
    }

    private fun saveEvent(clientId: Long, date: LocalDate, description: String): Event =
        eventRepository.save(
            Event(
                clientId = clientId,
                eventDate = date,
                eventType = EventType.PAYMENT_RECEIVED,
                eventDescription = description,
                paymentReceived = 1_000.0,
                runningBalance = 1_000.0,
            ),
        )
}
