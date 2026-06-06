package com.automan.backend.repository

import com.automan.backend.model.InvoiceHistoryLine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InvoiceHistoryLineRepository : JpaRepository<InvoiceHistoryLine, Long> {
    fun findByInvoiceHistoryIdOrderBySortOrderAsc(invoiceHistoryId: Long): List<InvoiceHistoryLine>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM InvoiceHistoryLine l WHERE l.invoiceHistoryId = :hid")
    fun deleteByInvoiceHistoryId(@Param("hid") invoiceHistoryId: Long)

    /** Returns true if at least one invoice_history_line exists for the given chassis (case-sensitive). */
    fun existsByChassis(chassis: String): Boolean

    /**
     * Count of lines whose chassis equals [chassis] ignoring case and outer whitespace.
     * Used after invoice history deletes to see if a chassis is still covered by another invoice.
     */
    @Query(
        "SELECT COUNT(l) FROM InvoiceHistoryLine l WHERE " +
            "LOWER(TRIM(COALESCE(l.chassis, ''))) = LOWER(TRIM(COALESCE(:chassis, '')))",
    )
    fun countByNormalizedChassis(@Param("chassis") chassis: String): Long

    /** Invoice numbers whose saved lines share any normalized chassis token. */
    @Query(
        """
        SELECT DISTINCT h.invoiceNumber FROM InvoiceHistory h
        INNER JOIN InvoiceHistoryLine l ON l.invoiceHistoryId = h.id
        WHERE LOWER(TRIM(COALESCE(l.chassis, ''))) IN :chassisKeys
        """,
    )
    fun findDistinctInvoiceNumbersByNormalizedChassisIn(
        @Param("chassisKeys") chassisKeys: Collection<String>,
    ): List<String>
}
