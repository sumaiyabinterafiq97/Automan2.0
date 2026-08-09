package com.automan.backend.service

import com.automan.backend.model.PendingSignup
import com.automan.backend.model.PendingSignupStatus
import com.automan.backend.model.User
import com.automan.backend.model.UserRole
import com.automan.backend.repository.PendingSignupRepository
import com.automan.backend.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Base64
import java.util.Hashtable
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.naming.directory.InitialDirContext

data class SignupRequest(val email: String, val name: String, val password: String, val role: UserRole, val createdByRole: UserRole? = null)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val id: Long, val email: String, val name: String, val role: UserRole, val token: String)
data class AuthenticatedUser(val id: Long, val email: String, val name: String, val role: UserRole)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val pendingSignupRepository: PendingSignupRepository,
    private val emailService: EmailService,
    @Value("\${app.mail.validate-mx:true}") private val validateMx: Boolean,
    @Value("\${app.auth.token-secret:change-me-automan-token-secret}") private val tokenSecret: String,
    @Value("\${app.auth.token-ttl-hours:24}") private val tokenTtlHours: Long
) {
    private val encoder = BCryptPasswordEncoder()
    private val tokenEncoder = Base64.getUrlEncoder().withoutPadding()
    private val tokenDecoder = Base64.getUrlDecoder()

    /** Check if email is already registered (users) or pending (pending_signups). */
    fun emailExists(email: String): Boolean {
        val normalized = email.trim().lowercase()
        if (userRepository.existsByEmail(normalized)) return true
        if (pendingSignupRepository.existsByEmailAndStatus(normalized, PendingSignupStatus.PENDING)) return true
        return false
    }

    /** Returns true if the email domain has MX records (can receive mail). */
    fun emailDomainExists(email: String): Boolean {
        val at = email.indexOf('@')
        if (at <= 0 || at == email.length - 1) return false
        val domain = email.substring(at + 1).trim().lowercase()
        if (domain.isEmpty()) return false
        return try {
            val env = Hashtable<String, String>().apply {
                put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory")
                put("java.naming.provider.url", "dns:")
            }
            val ctx = InitialDirContext(env)
            val attrs = ctx.getAttributes(domain, arrayOf("MX"))
            val mx = attrs.get("MX") ?: return false
            mx.size() > 0
        } catch (e: Exception) {
            false
        }
    }

    /** Validate strong password: 8+ chars, uppercase, lowercase, digit. Single message for UI. */
    fun validatePasswordStrength(password: String) {
        if (password.length < 8 ||
            !password.any { it.isUpperCase() } ||
            !password.any { it.isLowerCase() } ||
            !password.any { it.isDigit() }) {
            throw IllegalArgumentException("Please use a strong password (at least 8 characters, including uppercase, lowercase, and a number).")
        }
    }

    /** Create pending signup only; send verification email to admin. No token returned. */
    @Transactional
    fun signupPending(email: String, name: String, password: String, role: UserRole) {
        val normalizedEmail = email.trim().lowercase()
        if (emailExists(normalizedEmail)) {
            throw IllegalArgumentException("This email is already registered.")
        }
        if (validateMx && !emailDomainExists(normalizedEmail)) {
            throw IllegalArgumentException("The email does not exist.")
        }
        validatePasswordStrength(password)
        val token = UUID.randomUUID().toString().replace("-", "")
        val expiresAt = LocalDateTime.now().plusHours(48)
        val pending = PendingSignup(
            email = normalizedEmail,
            name = name.trim(),
            passwordHash = encoder.encode(password),
            role = role,
            verificationToken = token,
            status = PendingSignupStatus.PENDING,
            expiresAt = expiresAt
        )
        pendingSignupRepository.save(pending)
        emailService.sendSignupVerificationToAdmin(normalizedEmail, name.trim(), role.name, token)
    }

    /** Approve pending signup: create user, mark pending approved, email user. */
    @Transactional
    fun approvePending(token: String) {
        val pending = pendingSignupRepository.findByVerificationToken(token)
            ?: throw IllegalArgumentException("Invalid or expired verification link")
        if (pending.status != PendingSignupStatus.PENDING) {
            throw IllegalArgumentException("This request has already been processed")
        }
        if (pending.expiresAt != null && pending.expiresAt.isBefore(LocalDateTime.now())) {
            throw IllegalArgumentException("This verification link has expired")
        }
        if (userRepository.existsByEmail(pending.email)) {
            pending.status = PendingSignupStatus.REJECTED
            pendingSignupRepository.save(pending)
            throw IllegalArgumentException("This email is already registered")
        }
        val user = User(
            email = pending.email,
            name = pending.name,
            passwordHash = pending.passwordHash,
            role = pending.role
        )
        userRepository.save(user)
        emailService.sendApprovedToUser(pending.email, pending.name)
        pendingSignupRepository.delete(pending)
    }

    /** Reject pending signup; optionally email user. */
    @Transactional
    fun rejectPending(token: String) {
        val pending = pendingSignupRepository.findByVerificationToken(token)
            ?: throw IllegalArgumentException("Invalid or expired verification link")
        if (pending.status != PendingSignupStatus.PENDING) {
            throw IllegalArgumentException("This request has already been processed")
        }
        emailService.sendRejectedToUser(pending.email, pending.name)
        pendingSignupRepository.delete(pending)
    }

    /** List pending signups for admin UI. */
    fun listPendingSignups(): List<Map<String, Any?>> {
        return pendingSignupRepository.findByStatusOrderByCreatedAtDesc(PendingSignupStatus.PENDING)
            .filter { it.expiresAt == null || !it.expiresAt.isBefore(LocalDateTime.now()) }
            .map { p ->
                mapOf(
                    "id" to p.id,
                    "email" to p.email,
                    "name" to p.name,
                    "role" to p.role.name,
                    "createdAt" to p.createdAt?.toString(),
                    "verificationToken" to p.verificationToken
                )
            }
    }

    @Transactional
    fun signup(req: SignupRequest): AuthResponse {
        if (userRepository.existsByEmail(req.email)) {
            throw IllegalArgumentException("Email already registered")
        }
        validateRoleCreation(req.createdByRole, req.role)
        val user = User(
            email = req.email.trim().lowercase(),
            name = req.name.trim(),
            passwordHash = encoder.encode(req.password),
            role = req.role
        )
        val saved = userRepository.save(user)
        val token = generateMockToken(saved)
        return AuthResponse(saved.id!!, saved.email, saved.name, saved.role, token)
    }

    private fun validateRoleCreation(creatorRole: UserRole?, requestedRole: UserRole) {
        if (creatorRole == null) return
        val allowedRoles = when (creatorRole) {
            UserRole.ADMIN -> listOf(UserRole.VIEWER, UserRole.EDITOR, UserRole.ADMIN)
            UserRole.EDITOR -> listOf(UserRole.VIEWER, UserRole.EDITOR)
            else -> listOf(UserRole.VIEWER)
        }
        if (requestedRole !in allowedRoles) {
            throw IllegalArgumentException("Insufficient permissions to create user with role: $requestedRole")
        }
    }

    @Transactional(readOnly = true)
    fun login(req: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(req.email.trim().lowercase())
            ?: throw IllegalArgumentException("Invalid credentials")
        if (!encoder.matches(req.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid credentials")
        }
        val token = generateMockToken(user)
        return AuthResponse(user.id!!, user.email, user.name, user.role, token)
    }

    fun getUserCount(): Long = userRepository.count()

    private fun generateMockToken(user: User): String {
        val issuedAt = System.currentTimeMillis()
        val ttlMillis = tokenTtlHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val expiresAt = issuedAt + ttlMillis
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val payload = "${user.id ?: 0L}|${user.role.name}|$issuedAt|$expiresAt|$nonce"
        val encodedPayload = tokenEncoder.encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
        val signature = sign(encodedPayload)
        return "automan.$encodedPayload.$signature"
    }

    fun authenticate(authHeader: String?): AuthenticatedUser? {
        val token = authHeader
            ?.trim()
            ?.removePrefix("Bearer")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val parts = token.split(".")
        if (parts.size != 3 || parts[0] != "automan") return null

        val encodedPayload = parts[1]
        val providedSignature = parts[2]
        val expectedSignature = sign(encodedPayload)
        if (!secureEquals(providedSignature, expectedSignature)) return null

        val payload = try {
            String(tokenDecoder.decode(encodedPayload), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        val fields = payload.split("|")
        if (fields.size < 5) return null

        val userId = fields[0].toLongOrNull() ?: return null
        val role = try {
            UserRole.valueOf(fields[1])
        } catch (e: Exception) {
            return null
        }
        val expiresAt = fields[3].toLongOrNull() ?: return null
        if (expiresAt < System.currentTimeMillis()) return null

        val user = userRepository.findById(userId).orElse(null) ?: return null
        if (user.role != role) return null
        return AuthenticatedUser(user.id!!, user.email, user.name, user.role)
    }

    private fun sign(encodedPayload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secret = tokenSecret.ifBlank { "change-me-automan-token-secret" }
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return tokenEncoder.encodeToString(mac.doFinal(encodedPayload.toByteArray(StandardCharsets.UTF_8)))
    }

    private fun secureEquals(left: String, right: String): Boolean {
        return MessageDigest.isEqual(
            left.toByteArray(StandardCharsets.UTF_8),
            right.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
