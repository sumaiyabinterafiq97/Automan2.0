package com.automan.backend.dto

data class InvoiceBatchDeleteResult(
    val deleted: Int,
    val ledgerReversed: Int = 0,
    val ledgerWarnings: List<String> = emptyList(),
)
