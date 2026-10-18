package com.automan.backend.security

import com.automan.backend.model.AuthSession
import com.automan.backend.model.User
import com.automan.backend.repository.AuthSessionRepository
import com.automan.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64

@Service
class AuthSessionService(
    private val authSessionRepository: AuthSessionRepository,
    private val userRepository: UserRepository,
) {
    private val random = SecureRandom()

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun createSession(user: User): String = insertSession(user)

    /**
     * Writes the session in the caller's transaction.
     * Portal signup uses this so the insert does not wait on the new user row's foreign key.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    fun createSessionJoined(user: User): String = insertSession(user)

    private fun insertSession(user: User): String {
        val userId = user.id ?: throw IllegalStateException("Cannot open a session for an unsaved user")
        val rawToken = newToken()
        val now = utcNow()
        authSessionRepository.save(
            AuthSession(
                tokenHash = sha256Hex(rawToken),
                userId = userId,
                createdAt = now,
                // The column is required. A past value does not end the login; Log out sets revoked_at.
                expiresAt = now.plusYears(100),
            )
        )
        return rawToken
    }

    @Transactional(readOnly = true)
    fun authenticate(rawToken: String?): AuthPrincipal? {
        val token = rawToken?.trim().orEmpty()
        if (token.length !in 20..200) return null
        val session = authSessionRepository.findByTokenHash(sha256Hex(token)) ?: return null
        if (session.revokedAt != null) return null
        val user = userRepository.findById(session.userId).orElse(null) ?: return null
        val sessionId = session.id ?: return null
        val userId = user.id ?: return null
        return AuthPrincipal(userId = userId, role = user.role, sessionId = sessionId)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revoke(rawToken: String?): Boolean {
        val token = rawToken?.trim().orEmpty()
        if (token.isEmpty()) return false
        val session = authSessionRepository.findByTokenHash(sha256Hex(token)) ?: return false
        if (session.revokedAt != null) return true
        authSessionRepository.save(session.copy(revokedAt = utcNow()))
        return true
    }

    /** Role changes end that user's open sessions so the browser must sign in again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeAllForUser(userId: Long) {
        revokeAllForUsersJoined(listOf(userId))
    }

    /**
     * Joins the caller's transaction.
     * Party-link and consignee-assignment changes use this so a rollback also restores the sessions.
     */
    fun revokeAllForUsersJoined(userIds: Collection<Long>) {
        if (userIds.isEmpty()) return
        val now = utcNow()
        userIds.distinct().forEach { userId ->
            authSessionRepository.findByUserIdAndRevokedAtIsNull(userId).forEach { session ->
                authSessionRepository.save(session.copy(revokedAt = now))
            }
        }
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun utcNow(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
    }
}
