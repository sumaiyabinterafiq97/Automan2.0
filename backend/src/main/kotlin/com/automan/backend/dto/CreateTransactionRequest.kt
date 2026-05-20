package com.automan.backend.dto

import com.automan.backend.model.EventType
import java.time.LocalDate

data class CreateTransactionRequest(
    val clientId: Long,
    val eventDate: String,
    val eventType: EventType,
    val eventDescription: String? = null,
    val quantity: Int? = null,
    val billNumber: String? = null,
    val transactionPrice: Double? = null,
    val paymentReceived: Double? = null,
) {
    fun toCreateEventRequest(): CreateEventRequest {
        val date = LocalDate.parse(eventDate.trim())
        return CreateEventRequest(
            clientId = clientId,
            eventDate = date,
            eventDescription = eventDescription?.trim()?.takeIf { it.isNotEmpty() },
            quantity = quantity,
            billNumber = billNumber?.trim()?.takeIf { it.isNotEmpty() },
            transactionPrice = transactionPrice,
            paymentReceived = paymentReceived,
            eventType = eventType,
        )
    }
}
