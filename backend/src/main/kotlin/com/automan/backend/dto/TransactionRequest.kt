package com.automan.backend.dto

import java.time.LocalDate

data class TransactionRequest(
    val clientId: Long,
    val eventDate: String,
    val eventType: String = "OTHER",
    val eventDescription: String? = null,
    val quantity: Int? = null,
    val billNumber: String? = null,
    val transactionPrice: Double? = null,
    val paymentReceived: Double? = null,
    val runningBalance: Double = 0.0
)
