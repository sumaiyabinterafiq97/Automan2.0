package com.automan.backend.dto

data class InvoiceItem(
    val unit: Int, // Row number (1, 2, 3, ...)
    val description: String, // Combined legacy description (kept for compatibility)
    val amount: String, // Formatted as "¥XXX,XXX"
    val maker: String? = null,
    val model: String? = null,
    val chassisNo: String? = null,
    val year: String? = null,
)

data class InvoicePdfRequest(
    val invoiceNumber: String,
    val invoiceDate: String, // Current date when PDF is created
    val lcNumber: String?,
    val clientName: String,
    val clientAddress: String?,
    val vessel: String,
    val shippingDate: String,
    val from: String,
    val to: String,
    val priceType: String, // "C&F" or "FOB"
    val items: List<InvoiceItem>,
    val totalAmount: String, // Formatted as "¥XXX,XXX"
    val bankAccount: String?,
    val message: String?,
    /** Optional shipping/booking extras from shipping_history (MEMON-style invoice). */
    val consignee: String? = null,
    /** Resolved from Consignee Map (`booking_mappings`) for PDF; optional on request. */
    val consigneeAddress: String? = null,
    val notifyParty: String? = null,
    val bookingNo: String? = null,
    val carrier: String? = null,
    val cyCutDate: String? = null,
    val eta: String? = null,
    val finalDestination: String? = null,
)

