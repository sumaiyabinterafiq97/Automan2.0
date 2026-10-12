package com.automan.backend.controller

import com.automan.backend.model.Client
import com.automan.backend.repository.AuthSessionRepository
import com.automan.backend.repository.ClientConsigneeAssignmentRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.ConsigneeRepository
import com.automan.backend.repository.UserPartyLinkRepository
import com.automan.backend.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyAccessIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var consigneeRepository: ConsigneeRepository
    @Autowired private lateinit var linkRepository: UserPartyLinkRepository
    @Autowired private lateinit var assignmentRepository: ClientConsigneeAssignmentRepository
    @Autowired private lateinit var authSessionRepository: AuthSessionRepository

    private val emails = mutableListOf<String>()
    private val clientIds = mutableListOf<Long>()
    private val consigneeIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        clientIds.forEach { id ->
            linkRepository.findAll().filter { it.clientId == id || it.partyId == id }.forEach { linkRepository.delete(it) }
            assignmentRepository.findByClientIdOrderByIdDesc(id).forEach { assignmentRepository.delete(it) }
        }
        consigneeIds.forEach { id ->
            linkRepository.findAll().filter { it.consigneeId == id || (it.partyType == "CONSIGNEE" && it.partyId == id) }
                .forEach { linkRepository.delete(it) }
        }
        clientIds.forEach { clientRepository.deleteById(it) }
        consigneeIds.forEach { consigneeRepository.deleteById(it) }
        emails.forEach { email -> userRepository.findByEmail(email)?.let { userRepository.delete(it) } }
        emails.clear()
        clientIds.clear()
        consigneeIds.clear()
    }

    @Test
    fun adminCanAssignChangeAndClearAConsigneeAndRecordsTheActor() {
        val client = newClient()
        val first = newConsignee("First Legal")
        val second = newConsignee("Second Legal")

        mockMvc.perform(put("/admin/party-access/clients/${client}/consignee").contentType(JSON).content("""{"consigneeId":$first}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.consigneeId").value(first))
        mockMvc.perform(put("/admin/party-access/clients/${client}/consignee").contentType(JSON).content("""{"consigneeId":$second}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.consigneeId").value(second))
        mockMvc.perform(put("/admin/party-access/clients/${client}/consignee").contentType(JSON).content("""{"consigneeId":null}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.clientId").value(client))

        assertNull(clientRepository.findById(client).get().consigneeId)
        val history = assignmentRepository.findByClientIdOrderByIdDesc(client)
        assertEquals(3, history.size)
        assertTrue(history.all { it.changedByUserId > 0 })
        assertEquals(second, history[0].previousConsigneeId)
        assertNull(history[0].consigneeId)
    }

    @Test
    fun invalidIdsAreRejectedAndOtherRolesCannotAssign() {
        val client = newClient()
        val consignee = newConsignee("Known")
        mockMvc.perform(put("/admin/party-access/clients/999999991/consignee").contentType(JSON).content("""{"consigneeId":$consignee}"""))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("No client exists with that id."))
        mockMvc.perform(put("/admin/party-access/clients/$client/consignee").contentType(JSON).content("""{"consigneeId":999999992}"""))
            .andExpect(status().isNotFound)
        assertNull(clientRepository.findById(client).get().consigneeId)

        for (role in listOf("EDITOR", "VIEWER", "CLIENT", "CONSIGNEE")) {
            val token = login(user(role.lowercase(), role))
            val response = mockMvc.perform(
                put("/admin/party-access/clients/$client/consignee")
                    .header("Authorization", "Bearer $token")
                    .contentType(JSON)
                    .content("""{"consigneeId":$consignee}"""),
            ).andExpect(status().isForbidden).andReturn().response.contentAsString
            assertTrue(response.contains(com.automan.backend.security.AuthPolicy.FORBIDDEN_MESSAGE))
            assertNoInternals(response)
        }
        assertNull(clientRepository.findById(client).get().consigneeId)

        val ignored = mockMvc.perform(
            put("/clients/$client").contentType(JSON).content("""{"clientName":"Phase Client","consigneeId":$consignee}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertFalse(ignored.contains("consigneeId"))
        assertNull(clientRepository.findById(client).get().consigneeId)
    }

    @Test
    fun onlyAdminCanLinkAndMismatchesInactiveAndUnassignedClientsAreRejected() {
        val client = newClient()
        val active = newConsignee("Active Legal")
        val inactive = newConsignee("Inactive Legal")
        mockMvc.perform(put("/admin/party-access/consignees/$inactive/status").contentType(JSON).content("""{"status":"INACTIVE"}"""))
            .andExpect(status().isOk)

        val clientUser = userId(user("client-link", "CLIENT"))
        val consigneeUser = userId(user("consignee-link", "CONSIGNEE"))

        val unassigned = mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$clientUser,"partyType":"CLIENT","partyId":$client}"""),
        ).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(unassigned.contains("Assign an active consignee"))
        assertNoInternals(unassigned)
        assertEquals(0, linkRepository.findByUserIdOrderByIdDesc(clientUser).size)
        assertNull(clientRepository.findById(client).get().consigneeId)

        mockMvc.perform(put("/admin/party-access/clients/$client/consignee").contentType(JSON).content("""{"consigneeId":$active}"""))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$clientUser,"partyType":"CONSIGNEE","partyId":$active}"""),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("A client account can only be linked to a client."))

        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$consigneeUser,"partyType":"CLIENT","partyId":$client}"""),
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("A consignee account can only be linked to a consignee."))

        val inactiveBody = mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$consigneeUser,"partyType":"CONSIGNEE","partyId":$inactive}"""),
        ).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(inactiveBody.contains("Inactive consignees"))
        assertNoInternals(inactiveBody)

        mockMvc.perform(put("/admin/party-access/clients/$client/consignee").contentType(JSON).content("""{"consigneeId":$inactive}"""))
            .andExpect(status().isBadRequest)
        assertEquals(active, clientRepository.findById(client).get().consigneeId)

        val editor = login(user("editor-link", "EDITOR"))
        mockMvc.perform(
            post("/admin/party-access/links")
                .header("Authorization", "Bearer $editor")
                .contentType(JSON)
                .content("""{"userId":$clientUser,"partyType":"CLIENT","partyId":$client}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$clientUser,"partyType":"CLIENT","partyId":$client}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("ACTIVE"))
        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$consigneeUser,"partyType":"CONSIGNEE","partyId":$active}"""),
        ).andExpect(status().isOk)

        val duplicate = mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$clientUser,"partyType":"CLIENT","partyId":$client}"""),
        ).andExpect(status().isConflict).andReturn().response.contentAsString
        assertTrue(duplicate.contains("already has an active link"))
        assertNoInternals(duplicate)
        assertEquals(1, linkRepository.findByUserIdAndPartyTypeAndStatus(clientUser, "CLIENT", "ACTIVE").size)
    }

    @Test
    fun revokeAndReassignmentEndAffectedSessionsWithoutMovingLinks() {
        val client = newClient()
        val first = newConsignee("Alpha Legal")
        val second = newConsignee("Beta Legal")
        mockMvc.perform(put("/admin/party-access/clients/$client/consignee").contentType(JSON).content("""{"consigneeId":$first}"""))
            .andExpect(status().isOk)

        val clientEmail = user("portal-client", "CLIENT")
        val alphaEmail = user("portal-alpha", "CONSIGNEE")
        val betaEmail = user("portal-beta", "CONSIGNEE")
        val clientUser = userId(clientEmail)
        val alphaUser = userId(alphaEmail)
        val betaUser = userId(betaEmail)
        val clientToken = login(clientEmail)
        login(alphaEmail)
        login(betaEmail)

        val clientLink = linkId(
            mockMvc.perform(
                post("/admin/party-access/links").contentType(JSON)
                    .content("""{"userId":$clientUser,"partyType":"CLIENT","partyId":$client}"""),
            ).andExpect(status().isOk).andReturn().response.contentAsString,
        )
        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$alphaUser,"partyType":"CONSIGNEE","partyId":$first}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$betaUser,"partyType":"CONSIGNEE","partyId":$second}"""),
        ).andExpect(status().isOk)

        assertTrue(authSessionRepository.findByUserIdAndRevokedAtIsNull(clientUser).isEmpty())
        assertTrue(authSessionRepository.findByUserIdAndRevokedAtIsNull(alphaUser).isEmpty())
        assertTrue(authSessionRepository.findByUserIdAndRevokedAtIsNull(betaUser).isEmpty())
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $clientToken")).andExpect(status().isUnauthorized)

        val freshAlpha = login(alphaEmail)
        val freshBeta = login(betaEmail)
        mockMvc.perform(put("/admin/party-access/clients/$client/consignee").contentType(JSON).content("""{"consigneeId":$second}"""))
            .andExpect(status().isOk)
        assertEquals(second, clientRepository.findById(client).get().consigneeId)
        assertEquals(first, linkRepository.findByUserIdAndPartyTypeAndStatus(alphaUser, "CONSIGNEE", "ACTIVE").single().partyId)
        assertEquals(second, linkRepository.findByUserIdAndPartyTypeAndStatus(betaUser, "CONSIGNEE", "ACTIVE").single().partyId)
        assertEquals(1, linkRepository.findAll().count { it.userId == alphaUser })
        assertEquals(1, linkRepository.findAll().count { it.userId == betaUser })
        assertTrue(authSessionRepository.findByUserIdAndRevokedAtIsNull(alphaUser).isEmpty())
        assertTrue(authSessionRepository.findByUserIdAndRevokedAtIsNull(betaUser).isEmpty())
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $freshAlpha")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $freshBeta")).andExpect(status().isUnauthorized)

        val clear = mockMvc.perform(
            put("/admin/party-access/clients/$client/consignee").contentType(JSON).content("""{"consigneeId":null}"""),
        ).andExpect(status().isConflict).andReturn().response.contentAsString
        assertTrue(clear.contains("Revoke active client portal links"))
        assertEquals(second, clientRepository.findById(client).get().consigneeId)

        val relogged = login(clientEmail)
        mockMvc.perform(post("/admin/party-access/links/$clientLink/revoke")).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("REVOKED"))
            .andExpect(jsonPath("$.revokedAt").isNotEmpty)
        assertEquals("REVOKED", linkRepository.findById(clientLink).get().status)
        assertTrue(authSessionRepository.findByUserIdAndRevokedAtIsNull(clientUser).isEmpty())
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $relogged")).andExpect(status().isUnauthorized)
        assertEquals(1, linkRepository.findByUserIdOrderByIdDesc(clientUser).size)

        val history = mockMvc.perform(get("/admin/party-access/users/$clientUser/links"))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(history.contains("REVOKED"))
        assertFalse(history.contains("password"))
    }

    @Test
    fun signupDoesNotCreateAPartyLink() {
        val beforeLinks = linkRepository.count()
        val beforeAssignments = assignmentRepository.count()
        val email = "party-signup-${UUID.randomUUID()}@automan.test"
        emails.add(email)
        mockMvc.perform(
            post("/auth/signup").contentType(JSON)
                .content("""{"email":"$email","name":"Signup","password":"Password1","role":"VIEWER"}"""),
        ).andExpect(status().isOk)
        assertEquals(beforeLinks, linkRepository.count())
        assertEquals(beforeAssignments, assignmentRepository.count())
    }

    private fun newClient(): Long {
        val saved = clientRepository.save(
            Client(clientNumber = "P2C-${UUID.randomUUID().toString().take(8)}", clientName = "Phase Client"),
        )
        clientIds.add(saved.id!!)
        return saved.id!!
    }

    private fun newConsignee(name: String): Long {
        val body = mockMvc.perform(
            post("/admin/party-access/consignees").contentType(JSON).content("""{"legalName":"$name"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val id = objectMapper.readTree(body).get("id").asLong()
        consigneeIds.add(id)
        return id
    }

    private fun user(label: String, role: String): String {
        val email = "party-$label-${UUID.randomUUID()}@automan.test"
        emails.add(email)
        mockMvc.perform(
            post("/users").contentType(JSON).content("""{"email":"$email","name":"$label","password":"Password1","role":"$role"}"""),
        ).andExpect(status().isOk)
        return email
    }

    private fun userId(email: String) = userRepository.findByEmail(email)!!.id!!

    private fun login(email: String): String {
        val body = mockMvc.perform(
            post("/auth/login").contentType(JSON).content("""{"email":"$email","password":"Password1"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body).get("token").asText()
    }

    private fun linkId(body: String) = objectMapper.readTree(body).get("id").asLong()

    private fun assertNoInternals(body: String) {
        val lower = body.lowercase()
        assertFalse(lower.contains("sql"))
        assertFalse(lower.contains("constraint"))
        assertFalse(lower.contains("stack"))
        assertFalse(lower.contains("exception"))
        assertFalse(body.contains("passwordHash"))
    }

    companion object {
        private val JSON = MediaType.APPLICATION_JSON

        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("app.mail.validate-mx") { "false" }
        }
    }
}
