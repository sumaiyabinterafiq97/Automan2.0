package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "invoice_history")
data class InvoiceHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "invoice_number", nullable = false, length = 64, unique = true)
    val invoiceNumber: String,

    @Column(name = "vessel")
    val vessel: String? = null,

    @Column(name = "booking_no")
    val bookingNo: String? = null,

    @Column(name = "carrier")
    val carrier: String? = null,

    @Column(name = "client_name")
    val clientName: String? = null,

    @Column(name = "consignee")
    val consignee: String? = null,

    @Column(name = "notify_party")
    val notifyParty: String? = null,

    @Column(name = "shipping_date")
    val shippingDate: LocalDate? = null,

    @Column(name = "cy_cut_date")
    val cyCutDate: LocalDate? = null,

    @Column(name = "eta")
    val eta: LocalDate? = null,

    @Column(name = "pol")
    val pol: String? = null,

    @Column(name = "pod")
    val pod: String? = null,

    @Column(name = "final_destination")
    val finalDestination: String? = null,

    @Column(name = "lc_no")
    val lcNo: String? = null,

    /** C&F or FOB from invoice generator (matches PDF). */
    @Column(name = "price_type", length = 32)
    val priceType: String? = null,

    @Column(name = "bank", columnDefinition = "TEXT")
    val bank: String? = null,

    @Column(name = "messages", columnDefinition = "TEXT")
    val messages: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
