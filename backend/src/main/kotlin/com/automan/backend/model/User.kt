package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.PrePersist
import java.time.LocalDateTime

enum class UserRole { VIEWER, EDITOR, ADMIN }

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 120)
    val email: String,

    @Column(nullable = false, length = 80)
    val name: String,

    @Column(nullable = false, length = 120)
    val passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val role: UserRole = UserRole.VIEWER,

    /** JSON blob for UI prefs (e.g. Purchase List sort + columns). Nullable = defaults. */
    @Column(name = "views", columnDefinition = "JSON")
    val views: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    @PrePersist
    fun prePersist() {
        // This will be called before saving to set the creation time
    }
}


