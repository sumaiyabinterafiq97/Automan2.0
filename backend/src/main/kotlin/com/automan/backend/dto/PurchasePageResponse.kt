package com.automan.backend.dto

import com.automan.backend.model.Purchase

/** Paginated purchase list (search or future browse-by-page). */
data class PurchasePageResponse(
    val content: List<Purchase>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
