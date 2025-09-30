package com.automan.backend.dto

import java.time.LocalDate

data class CreateEventRequest(
    val clientId: Long,
    val eventDate: LocalDate,
    val eventDescription: String? = null,
    val quantity: Int? = null,
    val billNumber: String? = null,
    val transactionPrice: Double? = null,
    val paymentReceived: Double? = null
)


