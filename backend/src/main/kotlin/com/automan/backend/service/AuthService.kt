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
import java.time.LocalDateTime
import java.util.Hashtable
import java.util.UUID
import javax.naming.directory.InitialDirContext

data class SignupRequest(val email: String, val name: String, val password: String, val role: UserRole, val createdByRole: UserRole? = null)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val id: Long, val email: String, val name: String, val role: UserRole, val token: String)

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val pendingSignupRepository: PendingSignupRepository,
    private val emailService: EmailService,
    @Value("\${app.mail.validate-mx:true}") private val validateMx: Boolean
) {
    private val encoder = BCryptPasswordEncoder()

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
        val randomComponent = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val timestamp = System.currentTimeMillis()
        val userId = user.id ?: 0L
        return "tok_${userId}_${timestamp}_$randomComponent"
    }
}
