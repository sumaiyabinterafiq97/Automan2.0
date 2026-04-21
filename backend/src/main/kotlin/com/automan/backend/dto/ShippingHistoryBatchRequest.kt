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
    /** ISO date yyyy-MM-dd from ETD */
    val shipmentDate: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val bookingId: String? = null,
    val vessel: String? = null,
    /** "C&F" or "FOB" */
    val priceType: String? = null,
    val items: List<ShippingHistoryItemRequest> = emptyList(),
)
