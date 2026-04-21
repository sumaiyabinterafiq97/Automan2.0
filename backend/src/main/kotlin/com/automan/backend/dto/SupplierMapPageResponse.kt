package com.automan.backend.dto

/** Paginated Supplier Map search (rixo_prices). */
data class SupplierMapPageResponse(
    val content: List<Map<String, Any>>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
