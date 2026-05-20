package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "invoice_history_line")
data class InvoiceHistoryLine(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "invoice_history_id", nullable = false)
    val invoiceHistoryId: Long,

    @Column(nullable = false, length = 512)
    val chassis: String,

    @Column(name = "line_amount", length = 128)
    val lineAmount: String? = null,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,
)
