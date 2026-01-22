package com.automan.backend.model.dto

/**
 * Lightweight car info DTO for car search / booking assignment flows.
 * Note: This is intentionally decoupled from any deleted `bookings` table/entities.
 */
data class CarInfo(
    val id: Long,
    val chassis: String? = null,
    val carName: String? = null,
    val carModelYear: String? = null,
    val brand: String? = null
)

