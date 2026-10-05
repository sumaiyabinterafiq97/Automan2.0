package com.automan.backend.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "rixo_email_map",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_rixo_email_map_company_email", columnNames = ["rixo_company", "email"]),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class RixoEmailMap(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "rixo_company", nullable = false, length = 100)
    val rixoCompany: String,

    @Column(name = "email", nullable = false, length = 254)
    val email: String,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
