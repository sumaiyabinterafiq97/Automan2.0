package com.automan.backend.controller

import com.automan.backend.model.PendingSignupStatus
import com.automan.backend.model.UserRole
import com.automan.backend.repository.PendingSignupRepository
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
    @Autowired private lateinit var pendingSignupRepository: PendingSignupRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val emails = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            emails.toList().forEach { email ->
                userRepository.findByEmail(email)?.let { userRepository.delete(it) }
                pendingSignupRepository.findAll()
                    .filter { it.email == email }
                    .forEach { pendingSignupRepository.delete(it) }
            }
        }
        emails.clear()
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
        val token = pendingToken(email)

        verify(token, "approve")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertEquals(1, userCount(email))
        val user = userRepository.findByEmail(email)
        assertNotNull(user)
        assertEquals(UserRole.VIEWER, user!!.role)
        assertEquals("Approved User", user.name)
        assertNull(pendingSignupRepository.findByVerificationToken(token))

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
        val token = pendingToken(email)

        verify(token, "approve").andExpect(status().isOk)
        verify(token, "approve").andExpect(status().isBadRequest)

        assertEquals(1, userCount(email))
    }

    @Test
    fun expiredVerificationTokenDoesNotCreateUser() {
        val email = track("approve-expired")
        signup(email).andExpect(status().isOk)
        val pending = pendingSignupRepository.findByEmailAndStatus(email, PendingSignupStatus.PENDING)
        assertNotNull(pending)
        pendingSignupRepository.save(pending!!.copy(expiresAt = LocalDateTime.now().minusHours(1)))

        verify(pending.verificationToken, "approve").andExpect(status().isBadRequest)
        assertEquals(0, userCount(email))
    }

    @Test
    fun rejectPendingSignupCreatesNoUserAndRemovesPending() {
        val email = track("reject")
        signup(email).andExpect(status().isOk)
        val token = pendingToken(email)

        verify(token, "reject")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertEquals(0, userCount(email))
        assertNull(pendingSignupRepository.findByVerificationToken(token))
        assertEquals(0, pendingCount(email))
    }

    @Test
    fun rejectedEmailCanSignUpAgain() {
        val email = track("reject-again")
        signup(email).andExpect(status().isOk)
        verify(pendingToken(email), "reject").andExpect(status().isOk)

        signup(email)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.token").doesNotExist())
        assertEquals(0, userCount(email))
        assertEquals(1, pendingCount(email))
    }

    @Test
    fun emptyUserTableSetupCreatesExactlyOneAdmin() {
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
            .andExpect(status().isInternalServerError)
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

    private fun pendingToken(email: String): String {
        val pending = pendingSignupRepository.findByEmailAndStatus(email, PendingSignupStatus.PENDING)
        assertNotNull(pending)
        return pending!!.verificationToken
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

    private fun verify(token: String, action: String) =
        mockMvc.perform(
            get("/auth/verify-signup")
                .param("token", token)
                .param("action", action),
        )

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
