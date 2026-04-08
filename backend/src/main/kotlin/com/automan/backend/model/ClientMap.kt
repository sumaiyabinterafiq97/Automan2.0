package com.automan.backend.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "client_map")
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClientMap(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "client_name", nullable = false)
    val clientName: String,

    @Column(name = "country")
    val country: String? = null,

    /** Port of discharge (or similar POD field from operations). */
    @Column(name = "pod")
    val pod: String? = null,

    @Column(name = "address", columnDefinition = "TEXT")
    val address: String? = null,

    @Column(name = "bank_info", columnDefinition = "TEXT")
    val bankInfo: String? = null,

    @Column(name = "consignee", columnDefinition = "TEXT")
    val consignee: String? = null,

    @Column(name = "debit_limit", precision = 18, scale = 2)
    val debitLimit: BigDecimal? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
