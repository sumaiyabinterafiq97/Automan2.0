package com.automan.backend.dto

/** Request body for Client-Based Shipment Details PDF. */
data class ClientBasedShipmentDetailsPdfRequest(
    val clientName: String,
    val vessel: String,
)

data class ClientBasedShipmentCarDto(
    val no: Int,
    val maker: String,
    val model: String,
    val chassis: String,
    val year: String,
)

/**
 * PDF payload for client-facing SHIPMENT DETAILS (no amounts).
 * Lines are not-yet-invoiced chassis only (same rule as invoice slice).
 */
data class ClientBasedShipmentDetailsPdfData(
    val documentDate: String?,
    val clientName: String,
    val clientAddress: String?,
    val vessel: String?,
    val bookingNo: String?,
    val carrier: String?,
    val etd: String?,
    val eta: String?,
    val pol: String?,
    val pod: String?,
    val finalDestination: String?,
    val cars: List<ClientBasedShipmentCarDto>,
)
