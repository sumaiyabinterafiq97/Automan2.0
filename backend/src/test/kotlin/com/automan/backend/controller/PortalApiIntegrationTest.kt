package com.automan.backend.controller

import com.automan.backend.model.Client
import com.automan.backend.model.ClientMap
import com.automan.backend.model.Consignee
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseMedia
import com.automan.backend.model.ShippingHistory
import com.automan.backend.model.UserPartyLink
import com.automan.backend.repository.ClientConsigneeAssignmentRepository
import com.automan.backend.repository.ClientMapRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.ConsigneeRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseMediaRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.repository.UserPartyLinkRepository
import com.automan.backend.repository.UserRepository
import com.fasterxml.jackson.databind.JsonNode
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
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalApiIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var consigneeRepository: ConsigneeRepository
    @Autowired private lateinit var linkRepository: UserPartyLinkRepository
    @Autowired private lateinit var assignmentRepository: ClientConsigneeAssignmentRepository
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var shippingHistoryRepository: ShippingHistoryRepository
    @Autowired private lateinit var invoiceHistoryRepository: InvoiceHistoryRepository
    @Autowired private lateinit var invoiceHistoryLineRepository: InvoiceHistoryLineRepository
    @Autowired private lateinit var eventRepository: EventRepository
    @Autowired private lateinit var purchaseMediaRepository: PurchaseMediaRepository
    @Autowired private lateinit var clientMapRepository: ClientMapRepository

    private val emails = mutableListOf<String>()
    private val clientIds = mutableListOf<Long>()
    private val consigneeIds = mutableListOf<Long>()
    private val purchaseIds = mutableListOf<Long>()
    private val shippingIds = mutableListOf<Long>()
    private val invoiceIds = mutableListOf<Long>()
    private val lineIds = mutableListOf<Long>()
    private val eventIds = mutableListOf<Long>()
    private val mediaIds = mutableListOf<Long>()
    private val clientMapIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        mediaIds.forEach { purchaseMediaRepository.deleteById(it) }
        clientMapIds.forEach { clientMapRepository.deleteById(it) }
        eventIds.forEach { eventRepository.deleteById(it) }
        lineIds.forEach { invoiceHistoryLineRepository.deleteById(it) }
        invoiceIds.forEach { invoiceHistoryRepository.deleteById(it) }
        shippingIds.forEach { shippingHistoryRepository.deleteById(it) }
        purchaseIds.forEach { purchaseRepository.deleteById(it) }
        clientIds.forEach { id ->
            linkRepository.findAll().filter { it.clientId == id || (it.partyType == "CLIENT" && it.partyId == id) }
                .forEach { linkRepository.delete(it) }
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
        purchaseIds.clear()
        shippingIds.clear()
        invoiceIds.clear()
        lineIds.clear()
        eventIds.clear()
        mediaIds.clear()
        clientMapIds.clear()
    }

    @Test
    fun unauthenticatedPortalRequestsAreRejected() {
        for (path in portalPaths(1, 1)) {
            val body = mockMvc.perform(get(path).header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
                .andExpect(status().isUnauthorized)
                .andReturn().response.contentAsString
            assertTrue(body.contains("Authentication required"))
            assertSafe(body)
        }
    }

    @Test
    fun staffAndTheOtherExternalRoleCannotCallPortalApis() {
        val clientToken = login(user("client-role", "CLIENT"))
        val consigneeToken = login(user("consignee-role", "CONSIGNEE"))
        val editorToken = login(user("editor-role", "EDITOR"))
        val viewerToken = login(user("viewer-role", "VIEWER"))
        for (path in portalPaths(9, 9)) {
            mockMvc.perform(get(path)).andExpect(status().isForbidden)
            mockMvc.perform(get(path).header("Authorization", "Bearer $editorToken")).andExpect(status().isForbidden)
            mockMvc.perform(get(path).header("Authorization", "Bearer $viewerToken")).andExpect(status().isForbidden)
            val cross = if (path.startsWith("/portal/client")) consigneeToken else clientToken
            val denied = mockMvc.perform(get(path).header("Authorization", "Bearer $cross"))
                .andExpect(status().isForbidden)
                .andReturn().response.contentAsString
            assertTrue(denied.contains(com.automan.backend.security.AuthPolicy.FORBIDDEN_MESSAGE))
            assertSafe(denied)
        }
        for (path in listOf("/purchases", "/clients", "/shipping-history", "/invoice-history", "/events", "/admin/party-access/clients")) {
            mockMvc.perform(get(path).header("Authorization", "Bearer $clientToken")).andExpect(status().isForbidden)
            mockMvc.perform(get(path).header("Authorization", "Bearer $consigneeToken")).andExpect(status().isForbidden)
        }
        mockMvc.perform(get("/portal/clientele").header("Authorization", "Bearer $clientToken")).andExpect(status().isForbidden)
        mockMvc.perform(get("/portal/consignee-extra").header("Authorization", "Bearer $consigneeToken")).andExpect(status().isForbidden)
    }

    @Test
    fun clientSeesOnlyItsOwnRecordsAndFinancialFieldsStayOut() {
        val fixture = seed()
        val body = mutableListOf<String>()
        body += get("/portal/client/me", fixture.tokenA)
        body += get("/portal/client/dashboard", fixture.tokenA)
        body += get("/portal/client/cars", fixture.tokenA)
        body += get("/portal/client/cars?q=${fixture.chassisB}&clientId=${fixture.clientB}", fixture.tokenA)
        body += get("/portal/client/shipments?bookingId=${fixture.bookingB}", fixture.tokenA)
        body += get("/portal/client/invoices?invoiceNumber=${fixture.invoiceNumber}", fixture.tokenA)
        body += get("/portal/client/transactions?clientId=${fixture.clientB}", fixture.tokenA)

        val me = objectMapper.readTree(body[0])
        assertEquals(true, me.get("linked").asBoolean())
        assertEquals("Portal Client A", me.get("clientName").asText())
        assertFalse(me.has("clientId"))
        val slashed = mockMvc.perform(
            get("/portal/client/me/").header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andReturn()
        if (slashed.response.status == 200) {
            assertEquals("Portal Client A", objectMapper.readTree(slashed.response.contentAsString).get("clientName").asText())
        } else {
            assertEquals(404, slashed.response.status, slashed.response.contentAsString)
            assertTrue(slashed.response.contentAsString.contains("Not found"))
            assertFalse(slashed.response.contentAsString.contains(fixture.chassisB))
            assertFalse(slashed.response.contentAsString.contains("Portal Client A"))
        }

        val dashboard = objectMapper.readTree(body[1])
        assertEquals(3, dashboard.get("purchasedCount").asInt())
        assertEquals(2, dashboard.get("shippedCount").asInt())
        assertEquals(1, dashboard.get("invoicedCount").asInt())
        assertOnlyKeys(body[1], setOf("purchasedCount", "shippedCount", "invoicedCount"))

        val cars = objectMapper.readTree(body[2])
        assertEquals(listOf(fixture.chassisOld, fixture.chassisShip, fixture.chassisYard), textValues(cars, "chassis"))
        assertTrue(cars.toString().contains("\"shipped\":true"))
        assertTrue(cars.toString().contains("\"shipped\":false"))
        assertOnlyKeys(body[2], CAR_KEYS)
        assertFalse(body[2].contains(fixture.chassisB))

        val narrowed = objectMapper.readTree(body[3])
        assertEquals(0, narrowed.size())

        val shipments = objectMapper.readTree(body[4])
        assertEquals(setOf("SHIP-1", "HIST-1"), textValues(shipments, "bookingId").toSet())
        assertFalse(shipments.toString().contains("\"42\""))
        assertTrue(shipments.toString().contains("SOON-VESSEL"))
        assertTrue(shipments.toString().contains("HIST-VESSEL"))
        assertOnlyKeys(body[4], SHIPMENT_KEYS)
        assertFalse(body[4].contains(fixture.chassisB))

        val invoices = objectMapper.readTree(body[5])
        assertEquals(1, invoices.size())
        assertEquals(fixture.invoiceNumber, invoices[0].get("invoiceNumber").asText())
        assertEquals("2026-03-01", invoices[0].get("date").asText())
        assertEquals("C&F", invoices[0].get("priceType").asText())
        assertEquals(listOf(fixture.chassisShip), textValues(invoices[0].get("chassis"), null))
        assertOnlyKeys(body[5], INVOICE_KEYS)

        val transactions = objectMapper.readTree(body[6])
        assertEquals(1, transactions.size())
        assertEquals("PAYMENT_RECEIVED", transactions[0].get("type").asText())
        assertEquals("2026-02-02", transactions[0].get("date").asText())
        assertFalse(transactions[0].has("description"))
        assertFalse(transactions[0].has("billNumber"))
        assertOnlyKeys(body[6], TRANSACTION_KEYS)
        assertFalse(body[6].contains("Mizuho"))
        assertFalse(body[6].contains("1,250.50"))
        assertFalse(body[6].contains("Supplier cost"))
        assertFalse(body[6].contains("BILL-A"))

        val otherClient = get("/portal/client/cars", fixture.tokenB)
        assertTrue(otherClient.contains(fixture.chassisB))
        assertFalse(otherClient.contains(fixture.chassisShip))
        assertPortalShape(otherClient)
        body.forEach { assertPortalShape(it) }

        val missing = mockMvc.perform(
            get("/portal/client/cars/${fixture.purchaseB}/media/${fixture.mediaB}/url")
                .header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andExpect(status().isNotFound).andReturn().response.contentAsString
        assertTrue(missing.contains("Not found"))
        assertFalse(missing.contains(fixture.chassisB))
        assertFalse(missing.contains("secret/file-key-b"))
        assertSafe(missing)
        assertNoFinancialLeak(missing)

        val ownMedia = mockMvc.perform(
            get("/portal/client/cars/${fixture.purchaseShip}/media/999999991/url")
                .header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andExpect(status().isServiceUnavailable).andReturn().response.contentAsString
        assertTrue(ownMedia.contains("Purchase media storage is not enabled"))
        assertFalse(ownMedia.contains(fixture.chassisB))
        assertFalse(ownMedia.contains("secret/file-key-b"))
        assertSafe(ownMedia)
        assertNoFinancialLeak(ownMedia)
    }

    @Test
    fun unlinkedClientReceivesNoBusinessData() {
        val fixture = seed()
        val token = login(user("unlinked", "CLIENT"))
        val me = get("/portal/client/me", token)
        assertEquals(false, objectMapper.readTree(me).get("linked").asBoolean())
        assertEquals("[]", get("/portal/client/cars", token))
        assertEquals("[]", get("/portal/client/shipments", token))
        assertEquals("[]", get("/portal/client/invoices", token))
        assertEquals("[]", get("/portal/client/transactions", token))
        val dashboard = objectMapper.readTree(get("/portal/client/dashboard", token))
        assertEquals(0, dashboard.get("purchasedCount").asInt())
        assertFalse(me.contains(fixture.chassisShip))
        assertFalse(me.contains("Portal Client A"))
        assertPortalShape(me)
        mockMvc.perform(
            get("/portal/client/cars/${fixture.purchaseShip}/media/1/url").header("Authorization", "Bearer $token"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun consigneeSeesAssignedClientsOnlyAndReassignmentAppliesImmediately() {
        val fixture = seed()
        val me = objectMapper.readTree(get("/portal/consignee/me", fixture.tokenAlpha))
        assertEquals(true, me.get("active").asBoolean())
        assertEquals(fixture.alphaName, me.get("legalName").asText())
        assertFalse(me.has("clientName"))

        val dashboard = objectMapper.readTree(get("/portal/consignee/dashboard", fixture.tokenAlpha))
        assertEquals(3, dashboard.get("purchasedCount").asInt())
        assertEquals(2, dashboard.get("shippedCount").asInt())
        assertEquals(1, dashboard.get("invoicedCount").asInt())
        assertEquals(0, dashboard.get("clientCount").asInt())
        assertOnlyKeys(dashboard.toString(), CONSIGNEE_DASHBOARD_KEYS)

        val shipped = get("/portal/consignee/cars/shipped", fixture.tokenAlpha)
        assertEquals(listOf(fixture.chassisOld, fixture.chassisShip), textValues(objectMapper.readTree(shipped), "chassis"))
        assertTrue(shipped.contains("Portal Client A"))
        assertFalse(shipped.contains(fixture.chassisB))
        assertFalse(shipped.contains(fixture.chassisUnassigned))
        assertOnlyKeys(shipped, CAR_KEYS)

        val purchases = get("/portal/consignee/purchases", fixture.tokenAlpha)
        assertTrue(purchases.contains(fixture.chassisShip))
        assertTrue(purchases.contains(fixture.chassisYard))
        assertFalse(purchases.contains(fixture.chassisB))
        assertFalse(purchases.contains(fixture.chassisUnassigned))
        assertFalse(purchases.contains("SECRET-BANK"))
        val ownPurchase = get("/portal/consignee/purchases/${fixture.purchaseShip}", fixture.tokenAlpha)
        assertTrue(ownPurchase.contains(fixture.chassisShip))
        mockMvc.perform(
            get("/portal/consignee/purchases/${fixture.purchaseB}").header("Authorization", "Bearer ${fixture.tokenAlpha}"),
        ).andExpect(status().isNotFound)
        val invoices = get("/portal/consignee/invoices", fixture.tokenAlpha)
        assertTrue(invoices.contains(fixture.invoiceNumber))
        assertTrue(invoices.contains(fixture.chassisShip))
        assertFalse(invoices.contains(fixture.chassisB))
        assertFalse(invoices.contains("SECRET-BANK"))
        assertFalse(invoices.contains("88888"))

        val mappedName = "Mapped ${fixture.alphaName}"
        val otherName = "Other ${fixture.alphaName}"
        val sharedName = "Shared ${fixture.alphaName}"
        val unmappedName = "Unmapped ${fixture.alphaName}"
        clientMapIds.add(clientMapRepository.save(ClientMap(
            clientName = mappedName,
            country = "PAKISTAN",
            pod = "KARACHI-PAKISTAN",
            address = "mapped-address",
            bankInfo = "BANK OF TEST",
            consignee = fixture.alphaName,
            debitLimit = BigDecimal("40000"),
        )).id!!)
        clientMapIds.add(clientMapRepository.save(ClientMap(
            clientName = otherName,
            consignee = "SOMEONE ELSE",
        )).id!!)
        clientMapIds.add(clientMapRepository.save(ClientMap(
            clientName = sharedName,
            consignee = "SOMEONE ELSE, ${fixture.alphaName}",
        )).id!!)
        val mappedChassis = "CL-MAP-${fixture.alphaId}"
        val unmappedChassis = "CL-BARE-${fixture.alphaId}"
        val past = LocalDate.now(ZoneOffset.UTC).minusDays(3)
        purchase(mappedChassis, fixture.clientA, null)
        purchase(unmappedChassis, fixture.clientA, null)
        shippingIds.add(shippingHistoryRepository.save(ShippingHistory(
            chassis = mappedChassis,
            clientName = mappedName,
            consignee = fixture.alphaName,
            eta = past,
        )).id!!)
        shippingIds.add(shippingHistoryRepository.save(ShippingHistory(
            chassis = unmappedChassis,
            clientName = unmappedName,
            consignee = "Prefix, ${fixture.alphaName}",
            eta = past,
        )).id!!)
        shippingIds.add(shippingHistoryRepository.save(ShippingHistory(
            chassis = fixture.chassisUnassigned,
            clientName = otherName,
            consignee = fixture.alphaName,
            eta = past,
        )).id!!)
        shippingIds.add(shippingHistoryRepository.save(ShippingHistory(
            chassis = "CL-OTHER-${fixture.alphaId}",
            clientName = sharedName,
            consignee = fixture.alphaName,
            eta = past,
        )).id!!)
        val maps = objectMapper.readTree(get("/portal/consignee/clients", fixture.tokenAlpha))
        assertEquals(listOf(mappedName, unmappedName), textValues(maps, "clientName"))
        assertTrue(maps.toString().contains("BANK OF TEST"))
        assertTrue(maps.toString().contains("40000"))
        assertFalse(maps.toString().contains(otherName))
        assertFalse(maps.toString().contains(sharedName))
        assertFalse(maps.toString().contains("777.50"))
        assertFalse(maps.toString().contains("totalPrice"))
        assertFalse(maps.toString().contains("creditLimit"))
        assertEquals("[]", get("/portal/consignee/clients", fixture.tokenBeta))
        assertEquals(2, objectMapper.readTree(get("/portal/consignee/dashboard", fixture.tokenAlpha)).get("clientCount").asInt())

        val notShipped = get("/portal/consignee/cars/not-shipped?q=${fixture.chassisB}", fixture.tokenAlpha)
        assertEquals("[]", notShipped)
        val yard = objectMapper.readTree(get("/portal/consignee/cars/not-shipped", fixture.tokenAlpha))
        assertEquals(listOf(fixture.chassisYard), textValues(yard, "chassis"))
        assertFalse(yard.toString().contains("clientName"))
        val looseChassis = "A-LOOSE-${fixture.clientA}"
        purchase(looseChassis, fixture.clientA, null)
        val unbooked = objectMapper.readTree(get("/portal/consignee/cars/not-shipped?booking=none", fixture.tokenAlpha))
        assertEquals(listOf(looseChassis), textValues(unbooked, "chassis"))
        val stillWaiting = textValues(objectMapper.readTree(get("/portal/consignee/cars/not-shipped", fixture.tokenAlpha)), "chassis")
        assertEquals(listOf(looseChassis, fixture.chassisYard), stillWaiting)

        val ongoing = get("/portal/consignee/shipments/ongoing", fixture.tokenAlpha)
        val ongoingTree = objectMapper.readTree(ongoing)
        assertEquals(setOf("42", "SHIP-1"), textValues(ongoingTree, "bookingId").toSet())
        assertTrue(ongoing.contains("Portal Client A"))
        assertTrue(ongoing.contains("NAGOYA"))
        assertFalse(ongoing.contains("HIST-VESSEL"))
        assertFalse(ongoing.contains(fixture.chassisB))
        assertOnlyKeys(ongoing, CONSIGNEE_SHIPMENT_KEYS)

        val history = get("/portal/consignee/shipments/history", fixture.tokenAlpha)
        assertTrue(history.contains("HIST-1"))
        assertTrue(history.contains("HIST-VESSEL"))
        assertFalse(history.contains("SHIP-1"))
        assertFalse(history.contains(fixture.chassisB))
        assertOnlyKeys(history, CONSIGNEE_SHIPMENT_KEYS)
        assertPortalShape(ongoing)
        assertPortalShape(history)

        val hidden = mockMvc.perform(
            get("/portal/consignee/cars/${fixture.purchaseB}/media/${fixture.mediaB}/url")
                .header("Authorization", "Bearer ${fixture.tokenAlpha}"),
        ).andExpect(status().isNotFound).andReturn().response.contentAsString
        assertTrue(hidden.contains("Not found"))
        assertFalse(hidden.contains("secret/file-key-b"))
        assertSafe(hidden)

        val client = clientRepository.findById(fixture.clientA).get()
        clientRepository.save(client.copy(consigneeId = fixture.betaId))

        val afterAlpha = get("/portal/consignee/cars/shipped", fixture.tokenAlpha)
        assertEquals("[]", afterAlpha)
        assertFalse(get("/portal/consignee/purchases", fixture.tokenAlpha).contains(fixture.chassisShip))
        assertFalse(get("/portal/consignee/invoices", fixture.tokenAlpha).contains(fixture.invoiceNumber))
        assertEquals(0, objectMapper.readTree(get("/portal/consignee/dashboard", fixture.tokenAlpha)).get("clientCount").asInt())
        val afterBeta = get("/portal/consignee/cars/shipped", fixture.tokenBeta)
        assertTrue(afterBeta.contains(fixture.chassisShip))
        assertTrue(afterBeta.contains(fixture.chassisB))
        assertFalse(afterBeta.contains(fixture.chassisUnassigned))
        val alphaStillSignedIn = objectMapper.readTree(get("/portal/consignee/me", fixture.tokenAlpha))
        val betaStillSignedIn = objectMapper.readTree(get("/portal/consignee/me", fixture.tokenBeta))
        assertEquals(true, alphaStillSignedIn.get("linked").asBoolean())
        assertEquals(true, betaStillSignedIn.get("linked").asBoolean())
        assertTrue(get("/portal/client/cars", fixture.tokenA).contains(fixture.chassisShip))
        val clientLink = linkRepository.findAll().single { it.clientId == fixture.clientA && it.status == "ACTIVE" }
        assertEquals(fixture.clientA, clientLink.partyId)

        val inactive = consigneeRepository.findById(fixture.betaId).get()
        consigneeRepository.save(inactive.copy(status = "INACTIVE"))
        val inactiveMe = objectMapper.readTree(get("/portal/consignee/me", fixture.tokenBeta))
        assertEquals(true, inactiveMe.get("linked").asBoolean())
        assertEquals(false, inactiveMe.get("active").asBoolean())
        assertEquals("[]", get("/portal/consignee/cars/shipped", fixture.tokenBeta))
        assertEquals("[]", get("/portal/consignee/clients", fixture.tokenBeta))
        assertEquals(0, objectMapper.readTree(get("/portal/consignee/dashboard", fixture.tokenBeta)).get("purchasedCount").asInt())
        assertFalse(get("/portal/consignee/me", fixture.tokenBeta).contains(fixture.chassisShip))
        for (path in consigneeDataPaths()) {
            val body = get(path, fixture.tokenBeta)
            assertFalse(body.contains(fixture.chassisShip), path)
            assertFalse(body.contains(fixture.chassisB), path)
            assertNoFinancialLeak(body)
        }
        val inactiveMedia = mockMvc.perform(
            get("/portal/consignee/cars/${fixture.purchaseB}/media/${fixture.mediaB}/url")
                .header("Authorization", "Bearer ${fixture.tokenBeta}"),
        ).andExpect(status().isNotFound).andReturn().response.contentAsString
        assertTrue(inactiveMedia.contains("Not found"))
        assertFalse(inactiveMedia.contains("secret/file-key-b"))
    }

    @Test
    fun unlinkedConsigneeAndRevokedClientReceiveNoPrivateData() {
        val fixture = seed()
        val consigneeToken = login(user("unlinked-consignee", "CONSIGNEE"))
        val consigneeMe = objectMapper.readTree(get("/portal/consignee/me", consigneeToken))
        assertEquals(false, consigneeMe.get("linked").asBoolean())
        assertEquals(false, consigneeMe.get("active").asBoolean())
        for (path in consigneeDataPaths()) {
            val body = get(path, consigneeToken)
            assertFalse(body.contains(fixture.chassisShip), path)
            assertFalse(body.contains(fixture.chassisB), path)
        }
        mockMvc.perform(
            get("/portal/consignee/cars/${fixture.purchaseB}/media/${fixture.mediaB}/url")
                .header("Authorization", "Bearer $consigneeToken"),
        ).andExpect(status().isNotFound)

        val email = user("revoke-client", "CLIENT")
        val userId = userRepository.findByEmail(email)!!.id!!
        val created = mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$userId,"partyType":"CLIENT","partyId":${fixture.clientA}}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val linkId = objectMapper.readTree(created).get("id").asLong()
        val token = login(email)
        assertTrue(get("/portal/client/cars", token).contains(fixture.chassisShip))

        mockMvc.perform(post("/admin/party-access/links/$linkId/revoke")).andExpect(status().isOk)
        mockMvc.perform(get("/portal/client/me").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/portal/client/cars").header("Authorization", "Bearer $token")).andExpect(status().isUnauthorized)

        val fresh = login(email)
        val me = objectMapper.readTree(get("/portal/client/me?clientId=${fixture.clientB}", fresh))
        assertEquals(false, me.get("linked").asBoolean())
        assertFalse(me.has("clientName"))
        for (path in clientDataPaths()) {
            val body = get("$path?clientId=${fixture.clientB}&chassis=${fixture.chassisShip}", fresh)
            assertFalse(body.contains(fixture.chassisShip), path)
            assertFalse(body.contains(fixture.chassisB), path)
        }
    }

    @Test
    fun oneConsigneeSeesEveryAssignedClientAndLosesAClearedAssignment() {
        val fixture = seed()
        val second = newClient("A2-${UUID.randomUUID().toString().take(6)}", "Portal Client A2", fixture.alphaId)
        val secondChassis = "A2-YARD-${UUID.randomUUID().toString().take(6)}"
        purchase(secondChassis, second, 77L)

        val shipped = get("/portal/consignee/cars/not-shipped", fixture.tokenAlpha)
        assertTrue(shipped.contains(fixture.chassisYard))
        assertTrue(shipped.contains(secondChassis))
        val ongoing = get("/portal/consignee/shipments/ongoing", fixture.tokenAlpha)
        assertTrue(ongoing.contains("Portal Client A2"))
        assertTrue(ongoing.contains("\"77\""))
        assertFalse(ongoing.contains("Portal Client B"))
        assertFalse(ongoing.contains(fixture.chassisB))
        assertFalse(shipped.contains(fixture.chassisUnassigned))
        assertEquals(0, objectMapper.readTree(get("/portal/consignee/dashboard", fixture.tokenAlpha)).get("clientCount").asInt())

        val client = clientRepository.findById(fixture.clientA).get()
        clientRepository.save(client.copy(consigneeId = null))
        val after = get("/portal/consignee/cars/shipped", fixture.tokenAlpha)
        assertFalse(after.contains(fixture.chassisShip))
        assertTrue(get("/portal/client/cars", fixture.tokenA).contains(fixture.chassisShip))
        val stillLinked = linkRepository.findAll().filter { it.clientId == fixture.clientA && it.status == "ACTIVE" }
        assertEquals(1, stillLinked.size)
        assertEquals(fixture.clientA, stillLinked.single().partyId)
    }

    @Test
    fun aMismatchedActiveLinkFailsClosed() {
        val fixture = seed()
        val email = user("mismatched", "CLIENT")
        val userId = userRepository.findByEmail(email)!!.id!!
        linkRepository.save(
            UserPartyLink(
                userId = userId,
                partyType = "CLIENT",
                partyId = fixture.clientA,
                clientId = null,
                status = "ACTIVE",
                createdAt = LocalDateTime.now(),
            ),
        )
        val token = login(email)
        val me = objectMapper.readTree(get("/portal/client/me", token))
        assertEquals(false, me.get("linked").asBoolean())
        assertEquals("[]", get("/portal/client/cars", token))
        assertFalse(get("/portal/client/invoices", token).contains(fixture.invoiceNumber))
        mockMvc.perform(
            get("/portal/client/cars/${fixture.purchaseShip}/media/1/url").header("Authorization", "Bearer $token"),
        ).andExpect(status().isNotFound)
    }

    @Test
    fun duplicateChassisShippingAndInvoicesFailClosed() {
        val fixture = seed()
        val beforeA = objectMapper.readTree(get("/portal/client/dashboard", fixture.tokenA))
        val shared = "DUP-${UUID.randomUUID().toString().take(8)}"
        purchase(shared, fixture.clientA, null)
        purchase(shared, fixture.clientB, null)
        shipping(shared, "LEAK-BOOK", "LEAK-VESSEL", LocalDate.of(2026, 6, 1))
        val invoice = invoiceHistoryRepository.save(
            InvoiceHistory(
                invoiceNumber = "LEAK-INV-${UUID.randomUUID().toString().take(6)}",
                vessel = "LEAK-INV-VESSEL",
                priceType = "C&F",
                bank = "SECRET-BANK",
                createdAt = LocalDateTime.of(2026, 6, 2, 9, 0),
            ),
        )
        invoiceIds.add(invoice.id!!)
        line(invoice.id!!, shared, 0)

        val carsA = get("/portal/client/cars", fixture.tokenA)
        assertTrue(carsA.contains(shared))
        val sharedCar = objectMapper.readTree(carsA).first { it.get("chassis").asText() == shared }
        assertEquals(false, sharedCar.get("shipped").asBoolean())
        assertFalse(get("/portal/client/shipments", fixture.tokenA).contains("LEAK-VESSEL"))
        assertFalse(get("/portal/client/invoices", fixture.tokenA).contains("LEAK-INV"))
        assertFalse(get("/portal/client/shipments", fixture.tokenB).contains("LEAK-VESSEL"))
        assertFalse(get("/portal/client/invoices", fixture.tokenB).contains("LEAK-INV"))
        assertFalse(get("/portal/consignee/shipments/ongoing", fixture.tokenAlpha).contains("LEAK-VESSEL"))
        assertFalse(get("/portal/consignee/shipments/history", fixture.tokenAlpha).contains("LEAK-VESSEL"))
        assertFalse(get("/portal/consignee/cars/shipped", fixture.tokenAlpha).contains(shared))
        assertTrue(get("/portal/consignee/cars/not-shipped", fixture.tokenAlpha).contains(shared))
        val afterA = objectMapper.readTree(get("/portal/client/dashboard", fixture.tokenA))
        assertEquals(beforeA.get("purchasedCount").asInt() + 1, afterA.get("purchasedCount").asInt())
        assertEquals(beforeA.get("shippedCount").asInt(), afterA.get("shippedCount").asInt())
        assertEquals(beforeA.get("invoicedCount").asInt(), afterA.get("invoicedCount").asInt())
        assertTrue(get("/portal/client/shipments", fixture.tokenA).contains("SOON-VESSEL"))
        assertTrue(get("/portal/client/invoices", fixture.tokenA).contains(fixture.invoiceNumber))
    }

    @Test
    fun clientPurchaseListShowsOwnCarsAndRefusesAnotherPurchase() {
        val fixture = seed()
        val list = objectMapper.readTree(get("/portal/client/cars/list", fixture.tokenA))
        assertTrue(list.toString().contains(fixture.chassisShip))
        assertFalse(list.toString().contains(fixture.chassisB))
        val own = list.first { it.get("purchase").get("chassis").asText() == fixture.chassisShip }
        assertEquals("99999", own.get("purchase").get("totalPrice").asText())
        assertEquals(fixture.purchaseShip, own.get("purchase").get("id").asLong())

        val detail = objectMapper.readTree(get("/portal/client/cars/${fixture.purchaseShip}", fixture.tokenA))
        assertEquals(fixture.chassisShip, detail.get("purchase").get("chassis").asText())
        assertFalse(detail.toString().contains("fileKey"))

        val missing = mockMvc.perform(
            get("/portal/client/cars/${fixture.purchaseB}").header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andExpect(status().isNotFound).andReturn().response.contentAsString
        assertTrue(missing.contains("Not found"))
        assertFalse(missing.contains(fixture.chassisB))

        mockMvc.perform(
            get("/portal/client/cars/${fixture.purchaseB}/history").header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andExpect(status().isNotFound)

        mockMvc.perform(
            post("/purchases")
                .header("Authorization", "Bearer ${fixture.tokenA}")
                .contentType(JSON)
                .content("{}"),
        ).andExpect(status().isForbidden)
    }

    @Test
    fun clientLedgerShowsOwnRowsAndCannotChangeCreditLimit() {
        val fixture = seed()
        val ledger = objectMapper.readTree(get("/portal/client/ledger", fixture.tokenA))
        assertEquals("Portal Client A", ledger.get("clientName").asText())
        assertEquals(444444.0, ledger.get("creditLimit").asDouble())
        assertEquals(333333.0, ledger.get("currentBalance").asDouble())
        assertEquals(777777.0, ledger.get("availableCredit").asDouble())
        assertTrue(ledger.get("events").toString().contains("BILL-A"))
        assertFalse(ledger.get("events").toString().contains("BILL-B"))
        assertFalse(ledger.get("events").toString().contains("Other ledger"))

        val old = objectMapper.readTree(get("/portal/client/transactions", fixture.tokenA))
        assertEquals("PAYMENT_RECEIVED", old[0].get("type").asText())
        assertFalse(old[0].has("runningBalance"))
        assertFalse(old[0].has("billNumber"))

        mockMvc.perform(
            put("/portal/client/credit-limit")
                .header("Authorization", "Bearer ${fixture.tokenA}")
                .contentType(JSON)
                .content("""{"creditLimit":1200,"clientName":"Hijack","currentBalance":1}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/portal/client/credit-limit")
                .header("Authorization", "Bearer ${fixture.tokenA}")
                .contentType(JSON)
                .content("""{"creditLimit":1200}"""),
        ).andExpect(status().isForbidden)

        val saved = clientRepository.findById(fixture.clientA).get()
        assertEquals(444444.0, saved.creditLimit)
        assertEquals(333333.0, saved.currentBalance)
        assertEquals("Portal Client A", saved.clientName)

        mockMvc.perform(
            put("/clients/${fixture.clientA}")
                .header("Authorization", "Bearer ${fixture.tokenA}")
                .contentType(JSON)
                .content("""{"creditLimit":9}"""),
        ).andExpect(status().isForbidden)

        mockMvc.perform(
            put("/portal/client/credit-limit")
                .header("Authorization", "Bearer ${fixture.tokenB}")
                .contentType(JSON)
                .content("""{"creditLimit":50}"""),
        ).andExpect(status().isForbidden)
        assertEquals(444444.0, clientRepository.findById(fixture.clientA).get().creditLimit)
        assertEquals(444444.0, clientRepository.findById(fixture.clientB).get().creditLimit)

        mockMvc.perform(
            put("/portal/client/credit-limit")
                .header("Authorization", "Bearer ${fixture.tokenA}")
                .contentType(JSON)
                .content("""{"creditLimit":null}"""),
        ).andExpect(status().isForbidden)
        val unchanged = clientRepository.findById(fixture.clientA).get()
        assertEquals(444444.0, unchanged.creditLimit)
        assertEquals(333333.0, unchanged.currentBalance)

        val csv = get("/portal/client/ledger/export", fixture.tokenA)
        assertTrue(csv.contains("BILL-A"))
        assertFalse(csv.contains("BILL-B"))

        val pdf = mockMvc.perform(
            get("/portal/client/ledger/statement-pdf").header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andExpect(status().isOk).andReturn().response.contentAsByteArray
        assertTrue(pdf.size > 4)
        assertEquals('%'.code.toByte(), pdf[0])
    }

    @Test
    fun portalPathsAndTrailingSlashesStayRoleSpecific() {
        val fixture = seed()
        mockMvc.perform(get("/portal/client/me/").header("Authorization", "Bearer ${fixture.tokenA}"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/portal/consignee/me/").header("Authorization", "Bearer ${fixture.tokenAlpha}"))
            .andExpect(status().isNotFound)
        val missing = mockMvc.perform(
            get("/portal/client/not-a-page").header("Authorization", "Bearer ${fixture.tokenA}"),
        ).andExpect(status().isNotFound).andReturn().response.contentAsString
        assertTrue(missing.contains("Not found"))
        assertFalse(missing.contains(fixture.chassisShip))
        assertSafe(missing)
        mockMvc.perform(get("/portal/client-extra").header("Authorization", "Bearer ${fixture.tokenA}"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/portal/consignee-extra").header("Authorization", "Bearer ${fixture.tokenAlpha}"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/portal/consignee/me").header("Authorization", "Bearer ${fixture.tokenA}"))
            .andExpect(status().isForbidden)
        mockMvc.perform(get("/portal/client/me").header("Authorization", "Bearer ${fixture.tokenAlpha}"))
            .andExpect(status().isForbidden)
        val own = objectMapper.readTree(get("/portal/client/me", fixture.tokenA))
        assertEquals("Portal Client A", own.get("clientName").asText())
    }

    private fun seed(): Fixture {
        val stamp = UUID.randomUUID().toString().take(8)
        val alpha = newConsignee("Alpha $stamp")
        val beta = newConsignee("Beta $stamp")
        val clientA = newClient("A-$stamp", "Portal Client A", alpha)
        val clientB = newClient("B-$stamp", "Portal Client B", beta)
        val clientC = newClient("C-$stamp", "Portal Client C", null)
        val chassisShip = "A-SHIP-$stamp"
        val chassisYard = "A-YARD-$stamp"
        val chassisOld = "A-OLD-$stamp"
        val chassisB = "B-SHIP-$stamp"
        val chassisC = "C-YARD-$stamp"
        val today = LocalDate.now(ZoneOffset.UTC)
        val ship = purchase(chassisShip, clientA, 9L, shippedPrice = true)
        val yard = purchase(chassisYard, clientA, 42L, pol = "NAGOYA", pod = "CHITTAGONG")
        purchase(chassisOld, clientA, 7L, shippedPrice = true)
        val purchaseB = purchase(chassisB, clientB, 100L, shippedPrice = true)
        purchase(chassisC, clientC, null)
        shipping(chassisShip, "SHIP-1", "SOON-VESSEL", today.plusDays(2))
        shipping(chassisOld, "HIST-1", "HIST-VESSEL", today.minusDays(2))
        shipping(chassisB, "OTHER-BOOK", "OTHER-VESSEL", today.minusDays(2))
        val invoiceNumber = "INV-$stamp"
        val invoice = invoiceHistoryRepository.save(
            InvoiceHistory(
                invoiceNumber = invoiceNumber,
                vessel = "INV-VESSEL",
                priceType = "C&F",
                bank = "SECRET-BANK",
                clientName = "BAIT-CLIENT-NAME",
                consignee = "BAIT-CONSIGNEE-TEXT",
                createdAt = LocalDateTime.of(2026, 3, 1, 9, 0),
            ),
        )
        invoiceIds.add(invoice.id!!)
        line(invoice.id!!, chassisShip, 0)
        line(invoice.id!!, chassisB, 1)
        event(
            clientA,
            "BILL-A ¥10,000",
            "Paid ¥1,250.50 and -500.00 via Mizuho. Supplier cost 99999. Balance due.",
        )
        event(clientB, "BILL-B", "Other ledger")
        val media = purchaseMediaRepository.save(
            PurchaseMedia(
                purchaseId = purchaseB,
                chassis = chassisB,
                fileKey = "secret/file-key-b",
                contentType = "image/jpeg",
                fileSize = 10,
            ),
        )
        mediaIds.add(media.id!!)
        val tokenA = linkClient("user-a", clientA)
        val tokenB = linkClient("user-b", clientB)
        val tokenAlpha = linkConsignee("user-alpha", alpha)
        val tokenBeta = linkConsignee("user-beta", beta)
        return Fixture(
            tokenA, tokenB, tokenAlpha, tokenBeta,
            clientA, clientB, alpha, beta, consigneeRepository.findById(alpha).get().legalName,
            chassisShip, chassisYard, chassisOld, chassisB, chassisC,
            ship, yard, purchaseB, media.id!!, invoiceNumber, "OTHER-BOOK",
        )
    }

    private fun purchase(
        chassis: String,
        clientId: Long,
        bookingId: Long?,
        pol: String? = null,
        pod: String? = null,
        shippedPrice: Boolean = false,
    ): Long {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Car $chassis",
                manufactureYear = "2020",
                stockLocation = "Yard",
                country = "Japan",
                clientId = clientId,
                bookingId = bookingId,
                pol = pol,
                pod = pod,
                clientName = "BAIT-PURCHASE-CLIENT",
                consignee = "BAIT-PURCHASE-CONSIGNEE",
                totalPrice = if (shippedPrice) "99999" else null,
            ),
        )
        purchaseIds.add(saved.id!!)
        return saved.id!!
    }

    private fun shipping(chassis: String, bookingId: String, vessel: String, eta: LocalDate) {
        val saved = shippingHistoryRepository.save(
            ShippingHistory(
                chassis = chassis,
                bookingId = bookingId,
                vessel = vessel,
                pol = "YOKOHAMA",
                pod = "CHATTOGRAM",
                shipmentDate = eta.minusDays(20),
                cyCutDate = eta.minusDays(25),
                eta = eta,
                amount = BigDecimal("777.50"),
                clientName = "BAIT-CLIENT-NAME",
                consignee = "BAIT-CONSIGNEE-TEXT",
            ),
        )
        shippingIds.add(saved.id!!)
    }

    private fun line(invoiceId: Long, chassis: String, sort: Int) {
        val saved = invoiceHistoryLineRepository.save(
            InvoiceHistoryLine(invoiceHistoryId = invoiceId, chassis = chassis, lineAmount = "88888", sortOrder = sort),
        )
        lineIds.add(saved.id!!)
    }

    private fun event(clientId: Long, bill: String, description: String) {
        val saved = eventRepository.save(
            Event(
                clientId = clientId,
                eventDate = LocalDate.of(2026, 2, 2),
                eventType = EventType.PAYMENT_RECEIVED,
                eventDescription = description,
                billNumber = bill,
                transactionPrice = 424242.0,
                paymentReceived = 111.0,
                runningBalance = 222.0,
            ),
        )
        eventIds.add(saved.id!!)
    }

    private fun newClient(number: String, name: String, consigneeId: Long?): Long {
        val saved = clientRepository.save(
            Client(
                clientNumber = number,
                clientName = name,
                consigneeId = consigneeId,
                currentBalance = 333333.0,
                creditLimit = 444444.0,
            ),
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

    private fun linkClient(label: String, clientId: Long): String {
        val email = user(label, "CLIENT")
        val userId = userRepository.findByEmail(email)!!.id!!
        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$userId,"partyType":"CLIENT","partyId":$clientId}"""),
        ).andExpect(status().isOk)
        return login(email)
    }

    private fun linkConsignee(label: String, consigneeId: Long): String {
        val email = user(label, "CONSIGNEE")
        val userId = userRepository.findByEmail(email)!!.id!!
        mockMvc.perform(
            post("/admin/party-access/links").contentType(JSON)
                .content("""{"userId":$userId,"partyType":"CONSIGNEE","partyId":$consigneeId}"""),
        ).andExpect(status().isOk)
        return login(email)
    }

    private fun user(label: String, role: String): String {
        val email = "portal-$label-${UUID.randomUUID()}@automan.test"
        emails.add(email)
        mockMvc.perform(
            post("/users").contentType(JSON)
                .content("""{"email":"$email","name":"$label","password":"Password1","role":"$role"}"""),
        ).andExpect(status().isOk)
        return email
    }

    private fun login(email: String): String {
        val body = mockMvc.perform(
            post("/auth/login").contentType(JSON).content("""{"email":"$email","password":"Password1"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        return objectMapper.readTree(body).get("token").asText()
    }

    private fun get(path: String, token: String): String {
        val result: MvcResult = mockMvc.perform(get(path).header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andReturn()
        return result.response.contentAsString
    }

    private fun textValues(node: JsonNode, field: String?): List<String> {
        if (node.isArray && field == null) return node.map { it.asText() }
        if (!node.isArray) return emptyList()
        return node.map { it.get(field).asText() }
    }

    private fun assertOnlyKeys(body: String, allowed: Set<String>) {
        val found = linkedSetOf<String>()
        collectKeys(objectMapper.readTree(body), found)
        assertTrue(found.all { it in allowed }, "unexpected keys ${found - allowed}")
        assertTrue(found.none { it in FORBIDDEN_KEYS }, "forbidden keys ${found.intersect(FORBIDDEN_KEYS)}")
    }

    private fun collectKeys(node: JsonNode, found: MutableSet<String>) {
        when {
            node.isObject -> node.fields().forEach { (key, value) ->
                found += key
                collectKeys(value, found)
            }
            node.isArray -> node.forEach { collectKeys(it, found) }
        }
    }

    private fun assertPortalShape(body: String) {
        assertOnlyKeys(body, ALL_PORTAL_KEYS)
        assertNoFinancialLeak(body)
    }

    private fun assertNoFinancialLeak(body: String) {
        val found = linkedSetOf<String>()
        collectKeys(objectMapper.readTree(body), found)
        assertTrue(found.none { it in FORBIDDEN_KEYS }, "forbidden keys ${found.intersect(FORBIDDEN_KEYS)}")
        for (secret in SECRETS) {
            assertFalse(body.contains(secret), secret)
        }
    }

    private fun assertSafe(body: String) {
        val lower = body.lowercase()
        assertFalse(lower.contains("sql"))
        assertFalse(lower.contains("stack"))
        assertFalse(lower.contains("exception"))
        assertFalse(lower.contains("jdbc"))
    }

    private fun clientDataPaths() = listOf(
        "/portal/client/dashboard",
        "/portal/client/cars",
        "/portal/client/shipments",
        "/portal/client/invoices",
        "/portal/client/transactions",
    )

    private fun consigneeDataPaths() = listOf(
        "/portal/consignee/dashboard",
        "/portal/consignee/shipments/ongoing",
        "/portal/consignee/cars/not-shipped",
        "/portal/consignee/cars/shipped",
        "/portal/consignee/shipments/history",
        "/portal/consignee/invoices",
    )

    private fun portalPaths(purchaseId: Long, mediaId: Long) = listOf(
        "/portal/client/me",
        "/portal/client/dashboard",
        "/portal/client/cars",
        "/portal/client/shipments",
        "/portal/client/invoices",
        "/portal/client/transactions",
        "/portal/client/cars/$purchaseId/media/$mediaId/url",
        "/portal/consignee/me",
        "/portal/consignee/dashboard",
        "/portal/consignee/shipments/ongoing",
        "/portal/consignee/cars/not-shipped",
        "/portal/consignee/cars/shipped",
        "/portal/consignee/shipments/history",
        "/portal/consignee/cars/$purchaseId/media/$mediaId/url",
        "/portal/consignee/clients",
        "/portal/consignee/purchases",
        "/portal/consignee/purchases/$purchaseId",
        "/portal/consignee/invoices",
    )

    private data class Fixture(
        val tokenA: String,
        val tokenB: String,
        val tokenAlpha: String,
        val tokenBeta: String,
        val clientA: Long,
        val clientB: Long,
        val alphaId: Long,
        val betaId: Long,
        val alphaName: String,
        val chassisShip: String,
        val chassisYard: String,
        val chassisOld: String,
        val chassisB: String,
        val chassisUnassigned: String,
        val purchaseShip: Long,
        val purchaseYard: Long,
        val purchaseB: Long,
        val mediaB: Long,
        val invoiceNumber: String,
        val bookingB: String,
    )

    companion object {
        private val JSON = MediaType.APPLICATION_JSON
        private val CAR_KEYS = setOf("purchaseId", "chassis", "carName", "year", "stockLocation", "country", "shipped", "clientName")
        private val SHIPMENT_KEYS = setOf("bookingId", "vessel", "pol", "pod", "shipmentDate", "cyCutDate", "eta", "chassis")
        private val CONSIGNEE_SHIPMENT_KEYS = SHIPMENT_KEYS + "clientNames"
        private val INVOICE_KEYS = setOf("invoiceNumber", "date", "vessel", "chassis", "priceType")
        private val TRANSACTION_KEYS = setOf("date", "type")
        private val CONSIGNEE_DASHBOARD_KEYS = setOf("purchasedCount", "shippedCount", "invoicedCount", "clientCount")
        private val ALL_PORTAL_KEYS = CAR_KEYS + SHIPMENT_KEYS + CONSIGNEE_SHIPMENT_KEYS + INVOICE_KEYS +
            TRANSACTION_KEYS + CONSIGNEE_DASHBOARD_KEYS +
            setOf("linked", "active", "clientNumber", "clientName", "legalName")
        private val FORBIDDEN_KEYS = setOf(
            "totalPrice", "total_price", "price", "profit", "rixoPrice", "rixo_price", "amount",
            "lineAmount", "line_amount", "currentBalance", "current_balance", "transactionPrice",
            "transaction_price", "paymentReceived", "payment_received", "runningBalance", "running_balance",
            "creditLimit", "credit_limit", "debitLimit", "debit_limit", "bank", "supplierCost",
            "supplier_cost", "freight", "fob", "cnf", "margin", "cost", "balance", "payment",
            "unitPrice", "unit_price", "taxTotal", "repairCharges", "fileKey", "file_key",
            "password", "passwordHash", "alertThreshold",
        )
        private val SECRETS = listOf(
            "99999", "777.50", "88888", "SECRET-BANK", "424242", "333333", "444444",
            "BAIT-CLIENT-NAME", "BAIT-CONSIGNEE-TEXT", "BAIT-PURCHASE-CLIENT", "BAIT-PURCHASE-CONSIGNEE",
            "secret/file-key-b",
        )

        @JvmStatic
        @DynamicPropertySource
        fun mail(registry: DynamicPropertyRegistry) {
            registry.add("app.mail.validate-mx") { "false" }
        }
    }
}
