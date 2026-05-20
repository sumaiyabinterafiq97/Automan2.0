package com.automan.backend.dto

data class InvoiceConfirmResult(
    val pdfBytes: ByteArray,
    val ledger: InvoiceLedgerResult,
)
