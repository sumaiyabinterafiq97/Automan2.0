package com.automan.backend.repository

import com.automan.backend.model.InvoiceHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface InvoiceHistoryRepository : JpaRepository<InvoiceHistory, Long> {
    fun findByInvoiceNumber(invoiceNumber: String): Optional<InvoiceHistory>
    fun findAllByInvoiceNumberIn(invoiceNumbers: Collection<String>): List<InvoiceHistory>

    @Query(
        value = (
            "SELECT h FROM InvoiceHistory h WHERE " +
                "LOWER(COALESCE(h.invoiceNumber,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.clientName,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.vessel,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pol,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pod,'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
        countQuery = (
            "SELECT count(h) FROM InvoiceHistory h WHERE " +
                "LOWER(COALESCE(h.invoiceNumber,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.clientName,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.vessel,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pol,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pod,'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
    )
    fun searchKeyFields(@Param("q") q: String, pageable: Pageable): Page<InvoiceHistory>
}
