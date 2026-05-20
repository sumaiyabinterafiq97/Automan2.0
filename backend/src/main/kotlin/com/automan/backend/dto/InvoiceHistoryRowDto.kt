package com.automan.backend.dto

/** Read model for invoice_history list UI (camelCase JSON). */
data class InvoiceHistoryRowDto(
    val invoiceNumber: String,
    val vessel: String? = null,
    val clientName: String? = null,
    val shippingDate: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val lcNo: String? = null,
    val priceType: String? = null,
    val bank: String? = null,
    val messages: String? = null,
    val chassis: String? = null,
    val totalAmount: String? = null,
    val createdAt: String? = null,
)
