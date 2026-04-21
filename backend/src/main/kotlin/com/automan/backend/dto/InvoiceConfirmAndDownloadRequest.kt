package com.automan.backend.dto

/**
 * Saves [InvoiceHistory], marks [purchaseIds] as invoice_confirmed, returns PDF bytes.
 * [shippingDateIso] is yyyy-MM-dd from the date input (stored in DB); [pdf] drives the PDF document.
 */
data class InvoiceConfirmAndDownloadRequest(
    val purchaseIds: List<Long>,
    /** Semicolon-separated chassis list from the invoice LIST table. */
    val chassisJoined: String,
    /** yyyy-MM-dd from shipping date field, or null. */
    val shippingDateIso: String? = null,
    val pdf: InvoicePdfRequest,
)
