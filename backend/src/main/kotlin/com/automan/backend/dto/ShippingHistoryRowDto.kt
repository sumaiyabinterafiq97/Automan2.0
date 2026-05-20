package com.automan.backend.dto

data class ShippingHistoryRowDto(
    val id: Long,
    val country: String? = null,
    val consignee: String? = null,
    val shipmentDate: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val bookingId: String? = null,
    val vessel: String? = null,
    val priceType: String? = null,
    val chassis: String,
    val clientName: String? = null,
    val amount: String,
    val createdAt: String? = null,
    /** True when this chassis already has at least one entry in invoice_history_lines. */
    val invoiceCreated: Boolean = false,
)

