package com.automan.backend.dto

import java.math.BigDecimal

data class ShippingHistoryItemRequest(
    val chassis: String,
    val clientName: String? = null,
    val amount: BigDecimal? = null,
)

data class ShippingHistoryBatchRequest(
    val country: String? = null,
    val consignee: String? = null,
    /** Optional notify party from booking form. */
    val notifyParty: String? = null,
    /** ISO date yyyy-MM-dd from ETD */
    val shipmentDate: String? = null,
    /** ISO date yyyy-MM-dd from CY CUT */
    val cyCutDate: String? = null,
    /** ISO date yyyy-MM-dd from ETA */
    val eta: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    /** Optional inland / final destination (not always used). */
    val finalDestination: String? = null,
    val bookingId: String? = null,
    val vessel: String? = null,
    /** Carrier from master_menu (booking form). */
    val carrier: String? = null,
    /** Bill of lading number (dual-written to shipping_history.bl_no). */
    val blNo: String? = null,
    /** "C&F" or "FOB" */
    val priceType: String? = null,
    val items: List<ShippingHistoryItemRequest> = emptyList(),
)
