package com.automan.backend.repository

import com.automan.backend.model.PendingSignup
import com.automan.backend.model.PendingSignupStatus
import org.springframework.data.jpa.repository.JpaRepository

interface PendingSignupRepository : JpaRepository<PendingSignup, Long> {
    fun findByVerificationToken(token: String): PendingSignup?
    fun existsByEmailAndStatus(email: String, status: PendingSignupStatus): Boolean
    fun findByEmailAndStatus(email: String, status: PendingSignupStatus): PendingSignup?
    fun findByStatusOrderByCreatedAtDesc(status: PendingSignupStatus): List<PendingSignup>
}
