package com.automan.backend.dto

data class RixoHistoryRowDto(
    val id: Long,
    val buyingDate: String? = null,
    val rixoCompany: String? = null,
    val message: String? = null,
    val chassis: String? = null,
    /** Strict: every chassis segment matches ≥1 purchase and every matched purchase has Rixo confirmed. */
    val rixoConfirmed: Boolean = false,
    /** Latest [com.automan.backend.model.Purchase.updatedAt] among matched Rixo-confirmed purchases; null if not strictly confirmed. */
    val rixoConfirmedDate: String? = null,
    /** True when at least one purchase matched by chassis on this row has [com.automan.backend.model.Purchase.bookingRequested]. */
    val hasBookingRequested: Boolean = false,
)
