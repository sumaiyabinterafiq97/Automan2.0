package com.automan.backend.dto

/** Paginated shipping history list. */
data class ShippingHistoryPageResponse(
    val content: List<ShippingHistoryRowDto>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
