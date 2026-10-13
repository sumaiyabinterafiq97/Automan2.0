package com.automan.backend.controller

import com.automan.backend.model.BookingMapping
import com.automan.backend.model.Client
import com.automan.backend.model.PendingSignupStatus
import com.automan.backend.model.Purchase
import com.automan.backend.model.UserRole
import com.automan.backend.repository.AuthBootstrapRepository
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.ConsigneeRepository
import com.automan.backend.repository.PendingSignupRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.UserPartyLinkRepository
import com.automan.backend.repository.UserRepository
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Login, public signup, approval, and first-admin setup.
 * Own H2 database so the empty-table setup case is not affected by other tests.
 * MX lookup is off here; these cases are not about DNS.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSignupIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var authBootstrapRepository: AuthBootstrapRepository
    @Autowired private lateinit var pendingSignupRepository: PendingSignupRepository
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var clientRepository: ClientRepository
    @Autowired private lateinit var consigneeRepository: ConsigneeRepository
    @Autowired private lateinit var bookingMappingRepository: BookingMappingRepository
    @Autowired private lateinit var linkRepository: UserPartyLinkRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val emails = mutableListOf<String>()
    private val purchaseIds = mutableListOf<Long>()
    private val clientIds = mutableListOf<Long>()
    private val consigneeIds = mutableListOf<Long>()
    private val mappingIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            emails.toList().forEach { email ->
                userRepository.findByEmail(email)?.id?.let { userId ->
                    linkRepository.findByUserIdOrderByIdDesc(userId).forEach { linkRepository.delete(it) }
                }
            }
            purchaseIds.toList().forEach { id -> if (purchaseRepository.existsById(id)) purchaseRepository.deleteById(id) }
            mappingIds.toList().forEach { id -> if (bookingMappingRepository.existsById(id)) bookingMappingRepository.deleteById(id) }
            emails.toList().forEach { email ->
                userRepository.findByEmail(email)?.let { userRepository.delete(it) }
                pendingSignupRepository.findAll()
                    .filter { it.email == email }
                    .forEach { pendingSignupRepository.delete(it) }
            }
            clientIds.toList().forEach { id -> if (clientRepository.existsById(id)) clientRepository.deleteById(id) }
            consigneeIds.toList().forEach { id ->
                linkRepository.findByPartyTypeAndPartyIdAndStatus("CONSIGNEE", id, "ACTIVE")
                    .forEach { linkRepository.delete(it) }
                if (consigneeRepository.existsById(id)) consigneeRepository.deleteById(id)
            }
        }
        emails.clear()
        purchaseIds.clear()
        clientIds.clear()
        consigneeIds.clear()
        mappingIds.clear()
    }

    @Test
    fun validLoginReturnsIdentityAndToken() {
        val email = track("login-ok")
        createUser(email, "VIEWER", "Password1", "Ada Login").andExpect(status().isOk)

        mockMvc.perform(login(email, "Password1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.name").value("Ada Login"))
            .andExpect(jsonPath("$.role").value("VIEWER"))
            .andExpect(jsonPath("$.token").value(not("")))
    }

    @Test
    fun wrongPasswordIsInvalidCredentialsWithoutToken() {
        val email = track("login-bad-pass")
        createUser(email).andExpect(status().isOk)

        mockMvc.perform(login(email, "WrongPass1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
            .andExpect(jsonPath("$.token").doesNotExist())
    }

    @Test
    fun unknownEmailIsInvalidCredentialsWithoutRevealingExistence() {
        val email = track("login-missing")

        mockMvc.perform(login(email, "Password1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
            .andExpect(jsonPath("$.token").doesNotExist())
        assertNull(userRepository.findByEmail(email))
    }

    @Test
    fun pendingSignupCannotLogin() {
        val email = track("login-pending")
        signup(email).andExpect(status().isOk)

        mockMvc.perform(login(email, "Password1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
            .andExpect(jsonPath("$.token").doesNotExist())
        assertNull(userRepository.findByEmail(email))
        assertEquals(1, pendingCount(email))
    }

    @Test
    fun validSignupCreatesPendingRowAndNoUserOrToken() {
        val email = track("signup-ok")

        signup(email, name = "New Person")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token").doesNotExist())

        assertNull(userRepository.findByEmail(email))
        assertEquals(1, pendingCount(email))
        assertEquals(PendingSignupStatus.PENDING, pendingSignupRepository.findByEmailAndStatus(email, PendingSignupStatus.PENDING)?.status)
    }

    @Test
    fun duplicateUserEmailIsRejected() {
        val email = track("signup-dup-user")
        createUser(email).andExpect(status().isOk)

        signup(email)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("This email is already registered."))
        assertEquals(1, userCount(email))
        assertEquals(0, pendingCount(email))
    }

    @Test
    fun duplicatePendingEmailIsRejected() {
        val email = track("signup-dup-pending")
        signup(email).andExpect(status().isOk)

        signup(email)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("This email is already registered."))
        assertEquals(0, userCount(email))
        assertEquals(1, pendingCount(email))
    }

    @Test
    fun weakPasswordIsRejected() {
        val email = track("signup-weak")

        signup(email, password = "password")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(
                "Please use a strong password (at least 8 characters, including uppercase, lowercase, and a number)."
            ))
        assertNull(userRepository.findByEmail(email))
        assertEquals(0, pendingCount(email))
    }

    @Test
    fun approvePendingSignupCreatesOneUserWithSameRoleWhoCanLogin() {
        val email = track("approve-ok")
        signup(email, role = "VIEWER", name = "Approved User").andExpect(status().isOk)

        review(pendingId(email), "approve")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertEquals(1, userCount(email))
        val user = userRepository.findByEmail(email)
        assertNotNull(user)
        assertEquals(UserRole.VIEWER, user!!.role)
        assertEquals("Approved User", user.name)
        assertEquals(0, pendingCount(email))

        mockMvc.perform(login(email, "Password1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.role").value("VIEWER"))
            .andExpect(jsonPath("$.token").value(not("")))
    }

    @Test
    fun secondApprovalDoesNotCreateAnotherUser() {
        val email = track("approve-twice")
        signup(email).andExpect(status().isOk)
        val id = pendingId(email)

        review(id, "approve").andExpect(status().isOk)
        review(id, "approve").andExpect(status().isBadRequest)

        assertEquals(1, userCount(email))
    }

    @Test
    fun expiredVerificationTokenDoesNotCreateUser() {
        val email = track("approve-expired")
        signup(email).andExpect(status().isOk)
        val pending = pendingSignupRepository.findByEmailAndStatus(email, PendingSignupStatus.PENDING)
        assertNotNull(pending)
        pendingSignupRepository.save(pending!!.copy(expiresAt = LocalDateTime.now().minusHours(1)))

        review(pending.id!!, "approve").andExpect(status().isBadRequest)
        assertEquals(0, userCount(email))
    }

    @Test
    fun rejectPendingSignupCreatesNoUserAndRemovesPending() {
        val email = track("reject")
        signup(email).andExpect(status().isOk)
        val id = pendingId(email)

        review(id, "reject")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertEquals(0, userCount(email))
        assertEquals(0, pendingCount(email))
    }

    @Test
    fun rejectedEmailCanSignUpAgain() {
        val email = track("reject-again")
        signup(email).andExpect(status().isOk)
        review(pendingId(email), "reject").andExpect(status().isOk)

        signup(email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token").doesNotExist())
        assertEquals(0, userCount(email))
        assertEquals(1, pendingCount(email))
    }

    @Test
    fun publicSignupCannotRequestAdminOrEditor() {
        val adminEmail = track("role-admin")
        signup(adminEmail, role = "ADMIN")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Public signup can only request the Viewer role."))
        assertEquals(0, pendingCount(adminEmail))
        assertEquals(0, userCount(adminEmail))

        val editorEmail = track("role-editor")
        signup(editorEmail, role = "EDITOR")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Public signup can only request the Viewer role."))
        assertEquals(0, pendingCount(editorEmail))
        assertEquals(0, userCount(editorEmail))
    }

    @Test
    fun clientSignupWithUnknownNameIsRejected() {
        val email = track("client-missing")
        signup(email, role = "CLIENT", name = "No Such Client")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("No client with that name was found."))
            .andExpect(jsonPath("$.token").doesNotExist())
        assertEquals(0, userCount(email))
        assertEquals(0, pendingCount(email))
    }

    @Test
    fun consigneeSignupWithUnknownNameIsRejected() {
        val email = track("consignee-missing")
        signup(email, role = "CONSIGNEE", name = "No Such Consignee")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("No consignee with that name was found."))
            .andExpect(jsonPath("$.token").doesNotExist())
        assertEquals(0, userCount(email))
        assertEquals(0, pendingCount(email))
    }

    @Test
    fun clientSignupMatchesPurchaseNameAndSeesOnlyThatPurchase() {
        val stamp = UUID.randomUUID().toString().take(8)
        val matchedName = "Signup Client $stamp"
        val matchedChassis = "SIGNUP-CLIENT-$stamp"
        val otherChassis = "SIGNUP-OTHER-$stamp"
        val heldChassis = "SIGNUP-HELD-$stamp"
        val otherClient = saveClient("Other $stamp", "Other Client $stamp")
        savePurchase(matchedChassis, clientName = matchedName, clientId = null)
        savePurchase(otherChassis, clientName = "Someone Else $stamp", clientId = null)
        val held = savePurchase(heldChassis, clientName = matchedName, clientId = otherClient)
        val email = track("client-match")

        val body = signup(email, role = "CLIENT", name = matchedName.lowercase())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.role").value("CLIENT"))
            .andExpect(jsonPath("$.token").value(not("")))
            .andReturn().response.contentAsString
        val token = tokenOf(body)
        val createdClientId = purchaseRepository.findById(purchaseIds.first()).orElseThrow().clientId
        assertNotNull(createdClientId)
        clientIds.add(createdClientId!!)
        assertEquals(otherClient, purchaseRepository.findById(held).orElseThrow().clientId)

        portalGet("/portal/client/dashboard", token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.purchasedCount").value(1))
        val cars = portalGet("/portal/client/cars", token)
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(cars.contains(matchedChassis))
        assertFalse(cars.contains(otherChassis))
        assertFalse(cars.contains(heldChassis))
        portalGet("/portal/consignee/dashboard", token).andExpect(status().isForbidden)
        portalGet("/purchases", token).andExpect(status().isForbidden)
    }

    @Test
    fun consigneeSignupMatchesMapNameAndDoesNotSeeAnotherConsignee() {
        val stamp = UUID.randomUUID().toString().take(8)
        val matchedName = "Signup Consignee $stamp"
        val matchedChassis = "SIGNUP-CONSIGNEE-$stamp"
        val otherChassis = "SIGNUP-CONSIGNEE-OTHER-$stamp"
        saveMapping(matchedName)
        val client = saveClient("Keep $stamp", "Keep Client $stamp")
        savePurchase(matchedChassis, clientName = "Keep Client $stamp", clientId = client, consignee = matchedName)
        savePurchase(otherChassis, clientName = "Keep Client $stamp", clientId = client, consignee = "Other Consignee $stamp")
        val email = track("consignee-match")

        val body = signup(email, role = "CONSIGNEE", name = "  $matchedName  ")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("CONSIGNEE"))
            .andExpect(jsonPath("$.token").value(not("")))
            .andReturn().response.contentAsString
        consigneeIds.add(consigneeRepository.findAll().single { it.legalName.equals(matchedName, ignoreCase = true) }.id!!)
        assertNull(clientRepository.findById(client).orElseThrow().consigneeId)

        val cars = portalGet("/portal/consignee/cars/not-shipped", tokenOf(body))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertTrue(cars.contains(matchedChassis))
        assertFalse(cars.contains(otherChassis))
        portalGet("/portal/client/dashboard", tokenOf(body)).andExpect(status().isForbidden)
        portalGet("/purchases", tokenOf(body)).andExpect(status().isForbidden)
    }

    @Test
    fun clientSignupRefusesWhenTwoClientRowsShareTheName() {
        val stamp = UUID.randomUUID().toString().take(8)
        val name = "Duplicate Client $stamp"
        saveClient("D1-$stamp", name)
        saveClient("D2-$stamp", name)
        savePurchase("SIGNUP-DUP-$stamp", clientName = name, clientId = null)
        val email = track("client-duplicate")

        signup(email, role = "CLIENT", name = name)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("More than one client uses that name. Ask an administrator to link this account."))
        assertEquals(0, userCount(email))
    }

    @Test
    fun emptyUserTableSetupCreatesExactlyOneAdmin() {
        userRepository.findByEmail(TestStaffAuth.EMAIL)?.let { userRepository.delete(it) }
        authBootstrapRepository.deleteAll()
        assertEquals(0L, userRepository.count(), "auth test database should start empty")
        val email = track("setup-first")

        mockMvc.perform(
            post("/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"First Admin","password":"Password1"}"""),
        ).andExpect(status().isOk)

        assertEquals(1L, userRepository.count())
        val user = userRepository.findByEmail(email)
        assertNotNull(user)
        assertEquals(UserRole.ADMIN, user!!.role)
        assertEquals(email, user.email)
    }

    @Test
    fun concurrentSetupCreatesOnlyOneAdmin() {
        userRepository.findByEmail(TestStaffAuth.EMAIL)?.let { userRepository.delete(it) }
        authBootstrapRepository.deleteAll()
        assertEquals(0L, userRepository.count())
        val ready = CountDownLatch(1)
        val done = CountDownLatch(6)
        val statuses = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val pool = Executors.newFixedThreadPool(6)
        repeat(6) { index ->
            val email = "race-$index-${UUID.randomUUID().toString().take(8)}@automan.test"
            emails.add(email)
            pool.submit {
                try {
                    ready.await(5, TimeUnit.SECONDS)
                    val status = mockMvc.perform(
                        post("/auth/setup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""{"email":"$email","name":"Race Admin","password":"Password1"}"""),
                    ).andReturn().response.status
                    statuses.add(status)
                } finally {
                    done.countDown()
                }
            }
        }
        ready.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(1, statuses.count { it == 200 }, "statuses=$statuses")
        assertEquals(6, statuses.size)
        assertTrue(statuses.all { it == 200 || it == 409 }, "statuses=$statuses")
        assertEquals(1L, userRepository.count())
        assertEquals(UserRole.ADMIN, userRepository.findAll().single().role)
        assertEquals(1L, authBootstrapRepository.count())
    }

    @Test
    fun setupAfterUserExistsDoesNotCreateAnotherUser() {
        val existing = track("setup-existing")
        createUser(existing, role = "EDITOR").andExpect(status().isOk)
        val before = userRepository.count()
        val attempted = track("setup-blocked")

        mockMvc.perform(
            post("/auth/setup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$attempted","name":"Second Admin","password":"Password1"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("System setup already completed"))

        assertEquals(before, userRepository.count())
        assertNull(userRepository.findByEmail(attempted))
        assertEquals(UserRole.EDITOR, userRepository.findByEmail(existing)?.role)
    }

    @Test
    fun userApiResponsesDoNotExposePasswordHash() {
        val email = track("user-hash")
        val created = createUser(email, password = "Password1")
            .andExpect(status().isOk)
            .andReturn()
        assertFalse(created.response.contentAsString.contains("passwordHash"))

        val storedHash = userRepository.findByEmail(email)?.passwordHash
        assertNotNull(storedHash)
        assertTrue(storedHash!!.isNotBlank())
        assertFalse(created.response.contentAsString.contains(storedHash))

        val listBody = mockMvc.perform(get("/users"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        assertFalse(listBody.contains("passwordHash"))
        assertFalse(listBody.contains(storedHash))

        val id = userRepository.findByEmail(email)?.id
        assertNotNull(id)
        val oneBody = mockMvc.perform(get("/users/$id"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        assertFalse(oneBody.contains("passwordHash"))
        assertFalse(oneBody.contains(storedHash))
    }

    private fun track(label: String): String {
        val email = "p0-$label-${UUID.randomUUID()}@automan.test"
        emails.add(email)
        return email
    }

    private fun userCount(email: String) = userRepository.findAll().count { it.email == email }

    private fun pendingCount(email: String) = pendingSignupRepository.findAll().count { it.email == email }

    private fun pendingId(email: String): Long {
        val pending = pendingSignupRepository.findByEmailAndStatus(email, PendingSignupStatus.PENDING)
        assertNotNull(pending)
        return pending!!.id!!
    }

    private fun createUser(
        email: String,
        role: String = "VIEWER",
        password: String = "Password1",
        name: String = "P0 User",
    ) = mockMvc.perform(
        post("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","name":"$name","password":"$password","role":"$role"}"""),
    )

    private fun signup(
        email: String,
        password: String = "Password1",
        role: String = "VIEWER",
        name: String = "P0 User",
    ) = mockMvc.perform(
        post("/auth/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","name":"$name","password":"$password","role":"$role"}"""),
    )

    private fun login(email: String, password: String) =
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}""")

    private fun review(id: Long, action: String) =
        mockMvc.perform(
            post("/auth/signup-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":$id,"action":"$action"}"""),
        )

    private fun portalGet(path: String, token: String) =
        mockMvc.perform(get(path).header("Authorization", "Bearer $token"))

    private fun tokenOf(body: String): String {
        val match = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)
        assertNotNull(match)
        return match!!.groupValues[1]
    }

    private fun saveClient(number: String, name: String): Long {
        val saved = clientRepository.save(Client(clientNumber = number, clientName = name))
        clientIds.add(saved.id!!)
        return saved.id!!
    }

    private fun savePurchase(chassis: String, clientName: String, clientId: Long?, consignee: String? = null): Long {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = chassis,
                clientName = clientName,
                clientId = clientId,
                consignee = consignee,
            ),
        )
        purchaseIds.add(saved.id!!)
        return saved.id!!
    }

    private fun saveMapping(consigneeName: String) {
        val saved = bookingMappingRepository.save(
            BookingMapping(country = "Japan", consigneeName = consigneeName),
        )
        mappingIds.add(saved.id)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                "jdbc:h2:mem:automan_auth_signup;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
            }
            registry.add("app.mail.validate-mx") { "false" }
        }
    }
}
