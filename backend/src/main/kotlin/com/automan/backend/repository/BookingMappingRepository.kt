package com.automan.backend.repository

import com.automan.backend.model.BookingMapping
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface BookingMappingRepository : JpaRepository<BookingMapping, Long> {
    fun findByCountryIgnoreCase(country: String): List<BookingMapping>

    /** First row for case-insensitive consignee name (for merge-on-duplicate). */
    fun findFirstByConsigneeNameIgnoreCaseOrderByIdAsc(consigneeName: String): BookingMapping?

    /** All rows for a consignee (same name can appear for different countries/PODs). */
    fun findAllByConsigneeNameIgnoreCaseOrderByIdAsc(consigneeName: String): List<BookingMapping>

    @Query(
        """
        SELECT DISTINCT TRIM(COALESCE(b.consigneeName, ''))
        FROM BookingMapping b
        WHERE COALESCE(TRIM(b.consigneeName), '') <> ''
        ORDER BY TRIM(COALESCE(b.consigneeName, '')) ASC
        """
    )
    fun findDistinctConsigneeNames(): List<String>

    @Query(
        value = """
            SELECT b FROM BookingMapping b WHERE
            LOWER(COALESCE(b.consigneeName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(b.country) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(b) FROM BookingMapping b WHERE
            LOWER(COALESCE(b.consigneeName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(b.country) LIKE LOWER(CONCAT('%', :q, '%'))
            """
    )
    fun searchConsigneeMapAllFields(@Param("q") q: String, pageable: Pageable): Page<BookingMapping>

    @Query(
        value = """SELECT b FROM BookingMapping b WHERE LOWER(COALESCE(b.consigneeName, '')) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(b) FROM BookingMapping b WHERE LOWER(COALESCE(b.consigneeName, '')) LIKE LOWER(CONCAT('%', :q, '%'))"""
    )
    fun searchConsigneeMapConsigneeNameContains(@Param("q") q: String, pageable: Pageable): Page<BookingMapping>

    @Query(
        value = """SELECT b FROM BookingMapping b WHERE LOWER(b.country) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(b) FROM BookingMapping b WHERE LOWER(b.country) LIKE LOWER(CONCAT('%', :q, '%'))"""
    )
    fun searchConsigneeMapCountryContains(@Param("q") q: String, pageable: Pageable): Page<BookingMapping>
}
