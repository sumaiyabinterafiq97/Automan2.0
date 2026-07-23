package com.automan.backend.dto

/**
 * Unified server-side purchase list filter (search + date range + advanced chips).
 * Used by [POST /purchases/page-filter].
 */
data class PurchasePageFilterRequest(
    val page: Int = 0,
    val size: Int = 20,
    val sort: String? = null,
    val order: String? = null,
    /** Optional search text (same as GET /purchases/page-search `q`). */
    val q: String? = null,
    /** Search scope: all | chassis | carName | brand | clientName | supplier */
    val field: String? = null,
    /** Inclusive ISO yyyy-MM-dd */
    val dateFrom: String? = null,
    /** Inclusive ISO yyyy-MM-dd */
    val dateTo: String? = null,
    val filters: List<PurchasePageFilterClause> = emptyList(),
)

data class PurchasePageFilterClause(
    val field: String,
    val operator: String = "contains",
    val value: String = "",
)
