package com.automan.backend.dto

import java.time.LocalDate

data class CreateTransactionRequest(
    val clientId: Long,
    val eventDate: String,
    val eventDescription: String,
    val quantity: Int? = null,
    val billNumber: String? = null,
    val transactionPrice: Double? = null,
    val paymentReceived: Double? = null
)
