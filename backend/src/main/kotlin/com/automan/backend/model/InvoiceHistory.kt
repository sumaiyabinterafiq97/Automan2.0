package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "invoice_history")
data class InvoiceHistory(
    @Id
    @Column(name = "invoice_number", nullable = false, length = 64)
    val invoiceNumber: String,

    @Column(name = "vessel")
    val vessel: String? = null,

    @Column(name = "client_name")
    val clientName: String? = null,

    @Column(name = "shipping_date")
    val shippingDate: LocalDate? = null,

    @Column(name = "lc_no")
    val lcNo: String? = null,

    @Column(name = "bank", columnDefinition = "TEXT")
    val bank: String? = null,

    @Column(name = "messages", columnDefinition = "TEXT")
    val messages: String? = null,

    @Column(name = "chassis", columnDefinition = "TEXT")
    val chassis: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
