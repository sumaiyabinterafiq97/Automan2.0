package com.automan.backend.repository

import com.automan.backend.model.InvoiceHistory
import org.springframework.data.jpa.repository.JpaRepository

interface InvoiceHistoryRepository : JpaRepository<InvoiceHistory, String> {
    fun existsByInvoiceNumber(invoiceNumber: String): Boolean
}
