package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "rixo_mapping")
data class RixoMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "rixo_company", nullable = false)
    val rixoCompany: String,

    @Column(name = "stock_location", nullable = false)
    val stockLocation: String,

    @Column(name = "supported_vehicle_type", nullable = true)
    val supportedVehicleType: String? = null,

    @Column(name = "rixo_price", nullable = true)
    val rixoPrice: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

