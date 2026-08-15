package com.automan.backend.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "stock_location_map",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_stock_location_map_stock", columnNames = ["stock_location"]),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class StockLocationMap(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "stock_location", nullable = false, length = 100)
    val stockLocation: String,

    @Column(name = "pol", columnDefinition = "TEXT")
    val pol: String? = null,

    @Column(name = "address", columnDefinition = "TEXT")
    val address: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
