package com.automan.backend.dto

import com.automan.backend.model.BookingMapping

/** Paginated Consignee Map search (booking_mappings). */
data class ConsigneeMapPageResponse(
    val content: List<BookingMapping>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
