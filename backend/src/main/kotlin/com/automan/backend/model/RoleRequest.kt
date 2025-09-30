package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "role_requests")
data class RoleRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val requestedRole: UserRole,
    
    @Column(length = 500)
    val reason: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RequestStatus = RequestStatus.PENDING,
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    val reviewedBy: User? = null,
    
    @Column(length = 500)
    val reviewComment: String? = null,
    
    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    val reviewedAt: LocalDateTime? = null
) {
    @PrePersist
    fun prePersist() {
        // createdAt is set in constructor with default value
    }
}

enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}
