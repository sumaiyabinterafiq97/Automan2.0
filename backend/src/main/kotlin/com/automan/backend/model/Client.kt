package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(
    name = "clients",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["client_number"], name = "uk_client_number")
    ]
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class Client(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    
    @Column(name = "client_number", nullable = false, unique = true)
    val clientNumber: String,
    
    @Column(name = "client_name", nullable = false)
    val clientName: String,
    
    @Column(name = "current_balance")
    val currentBalance: Double = 0.0,
    
    @Column(name = "credit_limit")
    val creditLimit: Double? = null,
    
    @Column(name = "alert_threshold")
    val alertThreshold: Double? = null,
    
    @Column(name = "currency")
    val currency: String = "JPY",
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    val status: ClientStatus = ClientStatus.ACTIVE,
    
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        // This will be called before updating to set the update time
    }
}

enum class ClientStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
