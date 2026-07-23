package com.automan.backend.dto

/** Paginated invoice history list. */
data class InvoiceHistoryPageResponse(
    val content: List<InvoiceHistoryRowDto>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
