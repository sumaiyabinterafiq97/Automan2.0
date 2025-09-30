package com.automan.backend.dto

data class TransactionResponse(
    val success: Boolean,
    val transactionId: Long? = null,
    val message: String,
    val runningBalance: Double? = null
)
