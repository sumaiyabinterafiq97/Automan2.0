package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Singleton claim for the first Admin. Only id 1 is valid.
 * The primary key is the concurrency control for public setup.
 */
@Entity
@Table(name = "auth_bootstrap")
data class AuthBootstrap(
    @Id
    val id: Byte = 1.toByte(),
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
)
