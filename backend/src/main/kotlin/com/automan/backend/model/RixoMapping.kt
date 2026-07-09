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

    /** Supplier / auction house name from the price sheet (e.g. LUM FUKUOKA, USS TOKYO). */
    @Column(name = "auction_name", nullable = true)
    val auctionName: String? = null,

    @Column(name = "stock_location", nullable = false)
    val stockLocation: String,

    @Column(name = "venue_id", nullable = true)
    val venueId: String? = null,

    @Column(name = "pol", nullable = true)
    val pol: String? = null,

    @Column(name = "supported_vehicle_type", nullable = true)
    val supportedVehicleType: String? = null,

    @Column(name = "rixo_price", nullable = true)
    val rixoPrice: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

