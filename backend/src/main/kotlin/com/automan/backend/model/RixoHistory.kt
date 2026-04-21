package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "rixo_history")
data class RixoHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "buying_date")
    val buyingDate: LocalDate? = null,

    @Column(name = "rixo_company")
    val rixoCompany: String? = null,

    @Column(columnDefinition = "TEXT")
    val message: String? = null,

    @Column(columnDefinition = "TEXT")
    val chassis: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
