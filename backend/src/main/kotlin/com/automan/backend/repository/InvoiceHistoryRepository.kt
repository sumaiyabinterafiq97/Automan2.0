package com.automan.backend.repository

import com.automan.backend.model.InvoiceHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime
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

    /**
     * Invoice headers for Home dashboard revenue KPIs and sales-by-client chart.
     * Period rule: shipping_date in [from, to] (inclusive) OR created_at calendar day in [from, to].
     */
    @Query(
        """
        SELECT h.id AS id, h.shippingDate AS shippingDate, h.createdAt AS createdAt, h.clientName AS clientName
        FROM InvoiceHistory h WHERE
        (
            h.shippingDate IS NOT NULL AND h.shippingDate >= :from AND h.shippingDate <= :to
        ) OR (
            h.createdAt >= :createdFrom AND h.createdAt < :createdToExclusive
        )
        """,
    )
    fun findDashboardHeadersInPeriod(
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
        @Param("createdFrom") createdFrom: LocalDateTime,
        @Param("createdToExclusive") createdToExclusive: LocalDateTime,
    ): List<InvoiceHistoryDashboardHeaderProjection>
}

/** Fields required for dashboard invoice aggregations (avoids loading full invoice_history rows). */
interface InvoiceHistoryDashboardHeaderProjection {
    fun getId(): Long?
    fun getShippingDate(): LocalDate?
    fun getCreatedAt(): LocalDateTime?
    fun getClientName(): String?
}
