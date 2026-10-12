package com.automan.backend.repository

import com.automan.backend.model.AuthSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AuthSessionRepository : JpaRepository<AuthSession, Long> {
    fun findByTokenHash(tokenHash: String): AuthSession?

    fun findByUserIdAndRevokedAtIsNull(userId: Long): List<AuthSession>
}
