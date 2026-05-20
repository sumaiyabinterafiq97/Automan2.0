package com.automan.backend.repository

import com.automan.backend.model.InvoiceHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface InvoiceHistoryRepository : JpaRepository<InvoiceHistory, Long> {
    fun findByInvoiceNumber(invoiceNumber: String): Optional<InvoiceHistory>
    fun findAllByInvoiceNumberIn(invoiceNumbers: Collection<String>): List<InvoiceHistory>
}
