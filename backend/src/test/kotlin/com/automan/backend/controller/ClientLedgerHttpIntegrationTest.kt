package com.automan.backend.controller

import com.automan.backend.model.Client
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Client and transaction HTTP persistence. Credit math and invoice charges stay in the service tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientLedgerHttpIntegrationTest {

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
    fun searchPageReturnsOnlyTheMatchingClient() {
        val match = saveClient(name = "P0 Search ${token()}", balance = 10.0)
        val other = saveClient(name = "P0 Other ${token()}", balance = 99.0)

        mockMvc.perform(
            get("/clients/page-search")
                .param("q", match.clientNumber)
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(match.id))
            .andExpect(jsonPath("$.content[0].clientNumber").value(match.clientNumber))
            .andExpect(jsonPath("$.content[0].clientName").value(match.clientName))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString(other.clientNumber))))
    }

    @Test
    fun resolveLedgerReturnsTheKnownClient() {
        val client = saveClient(name = "P0 Resolve ${token()}")

        mockMvc.perform(get("/clients/resolve-ledger").param("name", client.clientName))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientId").value(client.id))
            .andExpect(jsonPath("$.ledgerResolvable").value(true))
            .andExpect(jsonPath("$.willCreateClient").value(false))
    }

    @Test
    fun creditCheckReportsOverLimitForTheEstablishedRule() {
        val client = saveClient(name = "P0 Credit ${token()}", balance = -400.0, creditLimit = 500.0)

        mockMvc.perform(
            get("/clients/credit-check")
                .param("clientId", client.id.toString())
                .param("invoiceNumber", "INV-HTTP-${token()}")
                .param("invoiceAmount", "200000"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.creditLimitStatus").value("OVER_LIMIT"))
            .andExpect(jsonPath("$.creditLimitBlocked").value(true))
            .andExpect(jsonPath("$.creditLimitClientId").value(client.id))
    }

    @Test
    fun balanceUpdatePersistsOnlyTheRequestedClient() {
        val target = saveClient(name = "P0 Balance ${token()}", balance = 0.0)
        val other = saveClient(name = "P0 Balance Other ${token()}", balance = 80.0)

        mockMvc.perform(
            put("/clients/${target.id}/balance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"balance":2500.5}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(target.id))
            .andExpect(jsonPath("$.currentBalance").value(2500.5))

        assertEquals(2500.5, clientRepository.findById(target.id!!).orElseThrow().currentBalance, 0.01)
        assertEquals(80.0, clientRepository.findById(other.id!!).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun addTransactionPostsPaymentToThatClientOnly() {
        val target = saveClient(name = "P0 Pay ${token()}")
        val other = saveClient(name = "P0 Pay Other ${token()}", balance = 40.0)

        mockMvc.perform(
            post("/clients/add-transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson(target.id!!, 100_000.0)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.runningBalance").value(100_000.0))

        val events = eventRepository.findByClientIdOrderByEventDateDesc(target.id!!)
        assertEquals(1, events.size)
        assertEquals(target.id, events[0].clientId)
        assertEquals(100_000.0, events[0].paymentReceived ?: 0.0, 0.01)
        assertEquals(100_000.0, clientRepository.findById(target.id!!).orElseThrow().currentBalance, 0.01)
        assertEquals(40.0, clientRepository.findById(other.id!!).orElseThrow().currentBalance, 0.01)
        assertTrue(eventRepository.findByClientIdOrderByEventDateDesc(other.id!!).isEmpty())
    }

    @Test
    fun transactionEndpointPostsPaymentToThatClientOnly() {
        val target = saveClient(name = "P0 Tx ${token()}")
        val other = saveClient(name = "P0 Tx Other ${token()}", balance = 15.0)

        mockMvc.perform(
            post("/api/transactions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentJson(target.id!!, 50_000.0)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.runningBalance").value(50_000.0))

        val events = eventRepository.findByClientIdOrderByEventDateDesc(target.id!!)
        assertEquals(1, events.size)
        assertEquals(target.id, events[0].clientId)
        assertEquals(50_000.0, clientRepository.findById(target.id!!).orElseThrow().currentBalance, 0.01)
        assertEquals(15.0, clientRepository.findById(other.id!!).orElseThrow().currentBalance, 0.01)
    }

    @Test
    fun statementPdfIsReturnedForTheRequestedClient() {
        val client = saveClient(name = "P0Stmt${token()}")

        val result = mockMvc.perform(get("/clients/${client.id}/statement-pdf"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, containsString("ClientStatement")))
            .andExpect(header().string(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, containsString(client.clientName)))
            .andReturn()

        val body = result.response.contentAsByteArray
        assertTrue(body.size > 4)
        assertEquals("%PDF", String(body.copyOfRange(0, 4)))
    }

    @Test
    fun importKeepsValidClientAndCountsARowMissingAName() {
        val number = "CL-${token()}"
        val badNumber = "CL-${token()}"
        mockMvc.perform(
            post("/clients/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"updateExisting":false,"clients":[
                      {"clientNumber":"$number","clientName":"Imported ${token()}"},
                      {"clientNumber":"$badNumber"}
                    ]}
                    """.trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.imported").value(1))
            .andExpect(jsonPath("$.errors").value(1))
            .andExpect(jsonPath("$.total").value(2))

        val saved = clientRepository.findByClientNumber(number)
        assertTrue(saved != null)
        clientIds.add(saved!!.id!!)
        assertNull(clientRepository.findByClientNumber(badNumber))
    }

    @Test
    fun importRejectsABodyThatIsNotAClientList() {
        mockMvc.perform(
            post("/clients/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"clients":"not-a-list"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").exists())
    }

    private fun paymentJson(clientId: Long, amount: Double) =
        """{"clientId":$clientId,"eventDate":"2026-06-01","eventType":"PAYMENT_RECEIVED","eventDescription":"TT received","paymentReceived":$amount}"""

    private fun saveClient(name: String, balance: Double = 0.0, creditLimit: Double? = null): Client {
        val saved = clientRepository.save(
            Client(
                clientNumber = "CL-${token()}",
                clientName = name,
                currentBalance = balance,
                creditLimit = creditLimit,
            ),
        )
        clientIds.add(saved.id!!)
        return saved
    }

    private fun token(): String = UUID.randomUUID().toString().take(8)
}
