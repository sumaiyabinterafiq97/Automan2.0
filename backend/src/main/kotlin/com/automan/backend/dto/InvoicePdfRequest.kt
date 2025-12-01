package com.automan.backend.dto

data class InvoiceItem(
    val unit: Int, // Row number (1, 2, 3, ...)
    val description: String, // Combined: chassis, name, brand, door, seat, fuel
    val amount: String // Formatted as "¥XXX,XXX"
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
    val message: String?
)

