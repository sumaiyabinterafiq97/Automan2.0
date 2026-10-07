package com.automan.backend.dto

/** Confirmed flag and date for one chassis token on a Rixo history row. */
data class RixoHistoryChassisConfirmDto(
    val chassis: String,
    val confirmed: Boolean = false,
    /** That purchase's `updatedAt` when this chassis is confirmed. */
    val confirmedDate: String? = null,
)

data class RixoHistoryRowDto(
    val id: Long,
    val buyingDate: String? = null,
    val rixoCompany: String? = null,
    val message: String? = null,
    val chassis: String? = null,
    /** Strict: every chassis segment matches ≥1 purchase and every matched purchase has Rixo confirmed. */
    val rixoConfirmed: Boolean = false,
    /** Latest [com.automan.backend.model.Purchase.updatedAt] among confirmed cars on the row, even when other cars are still open. */
    val rixoConfirmedDate: String? = null,
    /** True when at least one purchase matched by chassis on this row has [com.automan.backend.model.Purchase.bookingRequested]. */
    val hasBookingRequested: Boolean = false,
    /** Per-chassis confirm state. [rixoConfirmed] stays true only when every entry is confirmed. */
    val chassisConfirms: List<RixoHistoryChassisConfirmDto> = emptyList(),
    /** Purchase List supplier (`auctionHouse`) for each matched car, chassis order, distinct. */
    val supplierNames: List<String> = emptyList(),
)
