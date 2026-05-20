package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@Entity
@Table(name = "events")
@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "client_id", nullable = false)
    val clientId: Long,

    @Column(name = "event_date", nullable = false)
    val eventDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    val eventType: EventType = EventType.OTHER,

    @Column(name = "event_description")
    val eventDescription: String? = null,

    @Column(name = "quantity")
    val quantity: Int? = null,

    @Column(name = "bill_number")
    val billNumber: String? = null,

    @Column(name = "invoice_number", length = 64)
    val invoiceNumber: String? = null,

    @Column(name = "transaction_price")
    val transactionPrice: Double? = null,

    @Column(name = "payment_received")
    val paymentReceived: Double? = null,

    @Column(name = "running_balance", nullable = false)
    val runningBalance: Double,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class EventType {
    PAYMENT_RECEIVED,
    SHIPMENT,
    ADJUSTMENT,
    OTHER,
    /** Customer invoice confirmed (AR); use with [invoiceNumber] for idempotency. */
    INVOICE_ISSUED,
    /** Reverses a prior [INVOICE_ISSUED] when an invoice is deleted or re-saved with a new total. */
    INVOICE_REVERSAL,
}