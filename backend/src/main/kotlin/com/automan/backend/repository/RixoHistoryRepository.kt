package com.automan.backend.repository

import com.automan.backend.model.RixoHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface RixoHistoryRepository : JpaRepository<RixoHistory, Long> {

    @Query(
        value = (
            "SELECT h FROM RixoHistory h WHERE " +
                "LOWER(COALESCE(h.rixoCompany,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.chassis,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.message,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(CAST(h.buyingDate AS string),'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
        countQuery = (
            "SELECT count(h) FROM RixoHistory h WHERE " +
                "LOWER(COALESCE(h.rixoCompany,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.chassis,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.message,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(CAST(h.buyingDate AS string),'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
    )
    fun searchKeyFields(@Param("q") q: String, pageable: Pageable): Page<RixoHistory>

    @Query(
        "SELECT h FROM RixoHistory h WHERE LOWER(COALESCE(h.chassis, '')) LIKE LOWER(CONCAT('%', :token, '%'))",
    )
    fun findChassisContaining(@Param("token") token: String): List<RixoHistory>

    /** All stored chassis strings for token-union sync (avoids hydrating full [RixoHistory] entities). */
    @Query("SELECT h.chassis FROM RixoHistory h")
    fun findAllChassisValues(): List<String?>

    /**
     * Rows matching buying date and trim+lower [rixoCompany], excluding [excludeId], newest [id] first.
     * Semantics match [com.automan.backend.service.RixoHistoryService.findHistoryRowForDateAndCompany].
     */
    @Query(
        """
        SELECT h FROM RixoHistory h
        WHERE h.id <> :excludeId
        AND ((:buyingDate IS NULL AND h.buyingDate IS NULL) OR h.buyingDate = :buyingDate)
        AND LOWER(TRIM(COALESCE(h.rixoCompany, ''))) = :companyNormalized
        ORDER BY h.id DESC
        """,
    )
    fun findByBuyingDateAndCompanyNormalizedExcludingIdOrderByIdDesc(
        @Param("buyingDate") buyingDate: LocalDate?,
        @Param("companyNormalized") companyNormalized: String,
        @Param("excludeId") excludeId: Long,
    ): List<RixoHistory>
}
