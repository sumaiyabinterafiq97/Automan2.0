package com.automan.backend.controller

import com.automan.backend.model.UserRole
import com.automan.backend.repository.AuthSessionRepository
import com.automan.backend.repository.PendingSignupRepository
import com.automan.backend.repository.UserRepository
import com.automan.backend.security.AuthSessionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAuthIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var pendingSignupRepository: PendingSignupRepository
    @Autowired private lateinit var authSessionRepository: AuthSessionRepository
    @Autowired private lateinit var authSessionService: AuthSessionService
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val emails = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        emails.toList().forEach { email ->
            userRepository.findByEmail(email)?.let { userRepository.delete(it) }
            pendingSignupRepository.findAll().filter { it.email == email }.forEach { pendingSignupRepository.delete(it) }
        }
        emails.clear()
    }

    @Test
    fun loginCreatesAServerSessionThatCanReadPurchases() {
        val email = user("session-login", "VIEWER")
        val token = login(email)

        val hash = authSessionService.sha256Hex(token)
        val session = authSessionRepository.findByTokenHash(hash)
        assertNotNull(session)
        assertNull(session!!.revokedAt)
        assertTrue(session.expiresAt.isAfter(AuthSessionService.utcNow()))
        assertTrue(authSessionRepository.findAll().none { it.tokenHash == token })

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun wrongPasswordDoesNotCreateASession() {
        val email = user("session-bad-pass", "VIEWER")
        val before = authSessionRepository.count()
        mockMvc.perform(loginRequest(email, "WrongPass1"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.token").doesNotExist())
        assertEquals(before, authSessionRepository.count())
    }

    @Test
    fun malformedAndRandomTokensAreRejected() {
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer not-a-real-token"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Authentication required"))
        mockMvc.perform(get("/purchases").header("Authorization", "Basic abc"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(
            get("/purchases")
                .header(TestStaffAuth.ANONYMOUS_HEADER, "1"),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun expiredAndRevokedSessionsAreRejected() {
        val email = user("session-expire", "EDITOR")
        val token = login(email)
        val session = authSessionRepository.findByTokenHash(authSessionService.sha256Hex(token))
        assertNotNull(session)
        authSessionRepository.save(session!!.copy(expiresAt = AuthSessionService.utcNow().minusMinutes(1)))

        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)

        val live = login(email)
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer $live"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/dashboard").header("Authorization", "Bearer $live"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun anonymousBusinessCallsReturn401IncludingAliasesDownloadsAndMedia() {
        listOf(
            "/purchases",
            "/purchases/chassis/ABC123",
            "/clients",
            "/api/clients",
            "/events",
            "/invoice-history/INV-1/pdf",
            "/purchases/export/xlsx",
            "/shipping-history/export/xlsx",
            "/purchases/1/media",
            "/purchases/1/media/2/url",
            "/dashboard",
            "/users",
            "/auth/pending-signups",
        ).forEach { path ->
            mockMvc.perform(get(path).header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
                .andExpect(status().isUnauthorized)
        }
        mockMvc.perform(
            post("/purchases")
                .header(TestStaffAuth.ANONYMOUS_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"ZZZ1","date":"01/01/2026"}"""),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun viewerCanReadButCannotMutateAndEditorCannotAdminister() {
        val viewer = user("session-viewer", "VIEWER")
        val viewerToken = login(viewer)
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk)
        mockMvc.perform(get("/api/clients").header("Authorization", "Bearer $viewerToken"))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/purchases")
                .header("Authorization", "Bearer $viewerToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"VIEWBLOCK1","date":"01/01/2026"}"""),
        ).andExpect(status().isForbidden)

        val editor = user("session-editor", "EDITOR")
        val editorToken = login(editor)
        mockMvc.perform(
            post("/purchases")
                .header("Authorization", "Bearer $editorToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"ED${UUID.randomUUID().toString().take(6)}","date":"01/01/2026","local":false}"""),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/users")
                .header("Authorization", "Bearer $editorToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"nope-${UUID.randomUUID()}@automan.test","name":"Nope","password":"Password1","role":"ADMIN"}"""),
        ).andExpect(status().isForbidden)
        mockMvc.perform(get("/auth/pending-signups").header("Authorization", "Bearer $editorToken"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun signupReviewRequiresAdminAndDoesNotPublishTheApprovalToken() {
        val email = track("session-pending")
        mockMvc.perform(
            post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"Pending","password":"Password1","role":"VIEWER"}"""),
        ).andExpect(status().isOk)
        val pending = pendingSignupRepository.findByEmailAndStatus(
            email,
            com.automan.backend.model.PendingSignupStatus.PENDING,
        )
        assertNotNull(pending)

        mockMvc.perform(get("/auth/verify-signup").param("token", pending!!.verificationToken).param("action", "approve"))
            .andExpect(status().isMethodNotAllowed)
        assertEquals(1, pendingSignupRepository.findAll().count { it.email == email })
        assertNull(userRepository.findByEmail(email))

        val hidden = mockMvc.perform(get("/auth/pending-signups").header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
            .andExpect(status().isUnauthorized)
            .andReturn()
            .response
            .contentAsString
        assertTrue(!hidden.contains(pending.verificationToken))
        assertTrue(!hidden.contains("verificationToken"))

        val listed = mockMvc.perform(get("/auth/pending-signups"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        assertTrue(!listed.contains(pending.verificationToken))
        assertTrue(!listed.contains("verificationToken"))

        val viewer = user("session-reviewer", "VIEWER")
        mockMvc.perform(
            post("/auth/signup-review")
                .header("Authorization", "Bearer ${login(viewer)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":${pending.id},"action":"approve"}"""),
        ).andExpect(status().isForbidden)
        assertNull(userRepository.findByEmail(email))

        mockMvc.perform(
            post("/auth/signup-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":${pending.id},"action":"approve"}"""),
        ).andExpect(status().isOk)
        assertEquals(UserRole.VIEWER, userRepository.findByEmail(email)?.role)
    }

    @Test
    fun corsPreflightIsNotBlocked() {
        mockMvc.perform(
            options("/purchases")
                .header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "GET")
                .header(TestStaffAuth.ANONYMOUS_HEADER, "1"),
        )
            .andExpect(status().isOk)
            .andExpect(header().exists("Access-Control-Allow-Origin"))
    }

    @Test
    fun anonymousHeaderDoesNotAuthenticateAndDebugRoutesDoNotRun() {
        mockMvc.perform(get("/purchases").header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/auth/users/count").header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/auth/verify-signup").header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
            .andExpect(status().isUnauthorized)
        val probe = mockMvc.perform(get("/auth/check-email").param("email", "admin@automan.com").header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
            .andExpect(status().isUnauthorized)
            .andReturn()
            .response
            .contentAsString
        assertTrue(!probe.contains("exists"))
        assertTrue(!probe.contains("passwordHash"))
        val broken = mockMvc.perform(
            post("/auth/login")
                .header(TestStaffAuth.ANONYMOUS_HEADER, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"),
        ).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertTrue(!broken.contains("com.fasterxml"))
        assertTrue(!broken.contains("stackTrace"))
        assertTrue(!broken.lowercase().contains("sql"))
        mockMvc.perform(get("/purchases/test"))
            .andExpect(status().isNotFound)
        mockMvc.perform(post("/events/test-create").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/purchases/test").header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun roleChangeRevokesThePreviousSession() {
        val email = user("session-role", "EDITOR")
        val token = login(email)
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
        val id = userRepository.findByEmail(email)!!.id
        mockMvc.perform(
            put("/users/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"VIEWER"}"""),
        ).andExpect(status().isOk).andExpect(jsonPath("$.role").value("VIEWER"))
        assertEquals(0, authSessionRepository.findByUserIdAndRevokedAtIsNull(id!!).size)
        mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun clientAndConsigneeSessionsCannotUseStaffApis() {
        for (role in listOf("CLIENT", "CONSIGNEE")) {
            val email = user("external-$role", role)
            val token = login(email)
            for (probe in staffProbes()) {
                mockMvc.perform(probe.header("Authorization", "Bearer $token"))
                    .andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.message").value(com.automan.backend.security.AuthPolicy.FORBIDDEN_MESSAGE))
            }
            mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk)
            mockMvc.perform(get("/purchases").header("Authorization", "Bearer $token"))
                .andExpect(status().isUnauthorized)
        }
        for (probe in staffProbes()) {
            mockMvc.perform(probe.header(TestStaffAuth.ANONYMOUS_HEADER, "1"))
                .andExpect(status().isUnauthorized)
        }
        val signupEmail = track("external-signup")
        mockMvc.perform(
            post("/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$signupEmail","name":"Portal","password":"Password1","role":"CLIENT"}"""),
        ).andExpect(status().isBadRequest)
        assertNull(userRepository.findByEmail(signupEmail))
    }

    @Test
    fun adminStillReadsTheStaffPurchaseList() {
        mockMvc.perform(get("/purchases"))
            .andExpect(status().isOk)
    }

    /**
     * Controller mappings have no /api prefix. The servlet context path /api is stripped
     * before the filter, and AuthPolicy.normalize also accepts a leftover /api prefix.
     */
    private fun staffProbes() = listOf(
        get("/purchases"),
        get("/api/purchases"),
        get("/purchases/page"),
        get("/purchases/chassis/ABC123"),
        post("/purchases/by-chassis-tokens").contentType(MediaType.APPLICATION_JSON).content("[]"),
        get("/clients"),
        get("/clients/1/balance"),
        get("/events/export/all-clients"),
        get("/invoice-history"),
        get("/invoice-history/INV-1/pdf"),
        post("/purchases/invoice/generate-pdf").contentType(MediaType.APPLICATION_JSON).content("{}"),
        get("/shipping-history"),
        get("/shipping-history/export/xlsx"),
        post("/shipping-history/client-shipment-details/pdf").contentType(MediaType.APPLICATION_JSON).content("{}"),
        post("/purchases/export/xlsx").contentType(MediaType.APPLICATION_JSON).content("{}"),
        get("/rixo/prices"),
        get("/rixo-history"),
        post("/purchases/rixo-pdf").contentType(MediaType.APPLICATION_JSON).content("{}"),
        get("/purchases/1/media"),
        get("/purchases/1/media/1/url"),
    )

    private fun user(label: String, role: String): String {
        val email = track(label)
        mockMvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","name":"$label","password":"Password1","role":"$role"}"""),
        ).andExpect(status().isOk)
        return email
    }

    private fun login(email: String): String {
        val body = mockMvc.perform(loginRequest(email, "Password1"))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString
        return objectMapper.readTree(body).get("token").asText()
    }

    private fun loginRequest(email: String, password: String) =
        post("/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"email":"$email","password":"$password"}""")

    private fun track(label: String): String {
        val email = "sess-$label-${UUID.randomUUID()}@automan.test"
        emails.add(email)
        return email
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun registerProps(registry: DynamicPropertyRegistry) {
            registry.add("app.mail.validate-mx") { "false" }
        }
    }
}
