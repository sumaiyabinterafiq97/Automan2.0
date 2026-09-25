package com.automan.backend.repository

import com.automan.backend.model.InvoiceHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface InvoiceHistoryRepository : JpaRepository<InvoiceHistory, Long>, JpaSpecificationExecutor<InvoiceHistory> {
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

    @Query(
        "SELECT DISTINCT TRIM(h.clientName) FROM InvoiceHistory h " +
            "WHERE h.clientName IS NOT NULL AND TRIM(h.clientName) <> '' ORDER BY TRIM(h.clientName)",
    )
    fun findDistinctClientNames(): List<String>

    @Query(
        "SELECT DISTINCT TRIM(h.vessel) FROM InvoiceHistory h " +
            "WHERE h.vessel IS NOT NULL AND TRIM(h.vessel) <> '' ORDER BY TRIM(h.vessel)",
    )
    fun findDistinctVessels(): List<String>

    @Query(
        "SELECT DISTINCT TRIM(h.bookingNo) FROM InvoiceHistory h " +
            "WHERE h.bookingNo IS NOT NULL AND TRIM(h.bookingNo) <> '' ORDER BY TRIM(h.bookingNo)",
    )
    fun findDistinctBookingNumbers(): List<String>
}
