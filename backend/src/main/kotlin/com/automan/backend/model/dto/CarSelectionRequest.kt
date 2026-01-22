package com.automan.backend.model.dto

/**
 * Request payload for assigning a set of purchase IDs to a bookingId value.
 * bookingId is a simple grouping field on `purchases.booking_id` (no FK).
 */
data class CarSelectionRequest(
    val bookingId: Long,
    val carIds: List<Long>
)

