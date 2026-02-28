package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class PendingSignupStatus { PENDING, APPROVED, REJECTED }

@Entity
@Table(name = "pending_signups")
data class PendingSignup(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 120)
    val email: String,

    @Column(nullable = false, length = 80)
    val name: String,

    @Column(nullable = false, length = 120)
    val passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val role: UserRole,

    @Column(nullable = false, unique = true, length = 64)
    val verificationToken: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PendingSignupStatus = PendingSignupStatus.PENDING,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = true)
    val expiresAt: LocalDateTime? = null
)
