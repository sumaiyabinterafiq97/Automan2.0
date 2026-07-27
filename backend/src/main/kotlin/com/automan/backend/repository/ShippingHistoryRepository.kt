package com.automan.backend.repository

import com.automan.backend.model.ShippingHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShippingHistoryRepository : JpaRepository<ShippingHistory, Long> {

    /** Single row per chassis (see migration V19); latest id if duplicates exist pre-migration. */
    fun findFirstByChassisOrderByIdDesc(chassis: String): ShippingHistory?

    fun findByChassisIn(chassis: Collection<String>): List<ShippingHistory>

    @Query(
        value = (
            "SELECT h FROM ShippingHistory h WHERE " +
                "LOWER(COALESCE(h.bookingId,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.chassis,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.clientName,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.vessel,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.country,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pol,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pod,'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
        countQuery = (
            "SELECT count(h) FROM ShippingHistory h WHERE " +
                "LOWER(COALESCE(h.bookingId,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.chassis,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.clientName,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.vessel,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.country,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pol,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
                "LOWER(COALESCE(h.pod,'')) LIKE LOWER(CONCAT('%',:q,'%'))"
            ),
    )
    fun searchKeyFields(@Param("q") q: String, pageable: Pageable): Page<ShippingHistory>

    @Query(
        value = (
            "SELECT DISTINCT TRIM(client_name) FROM shipping_history " +
                "WHERE client_name IS NOT NULL AND TRIM(client_name) <> '' " +
                "ORDER BY TRIM(client_name) ASC"
        ),
        nativeQuery = true,
    )
    fun findDistinctClientNamesForInvoice(): List<String>

    @Query(
        "SELECT DISTINCT sh.vessel FROM ShippingHistory sh WHERE " +
            "TRIM(COALESCE(sh.clientName, '')) = TRIM(:clientName) AND " +
            "sh.vessel IS NOT NULL AND TRIM(sh.vessel) <> '' " +
            "ORDER BY sh.vessel ASC",
    )
    fun findDistinctVesselsForInvoiceClient(@Param("clientName") clientName: String): List<String>

    @Query(
        "SELECT sh FROM ShippingHistory sh WHERE " +
            "TRIM(COALESCE(sh.clientName, '')) = TRIM(:clientName) AND " +
            "TRIM(COALESCE(sh.vessel, '')) = TRIM(:vessel) " +
            "ORDER BY sh.id ASC",
    )
    fun findInvoiceRowsOrderByIdAsc(
        @Param("clientName") clientName: String,
        @Param("vessel") vessel: String,
    ): List<ShippingHistory>

    /** Shipment dates for dashboard shipping-trend chart. */
    @Query(
        "SELECT sh.shipmentDate FROM ShippingHistory sh WHERE sh.shipmentDate IS NOT NULL " +
            "AND sh.shipmentDate >= :from AND sh.shipmentDate <= :to",
    )
    fun findShipmentDatesBetween(
        @Param("from") from: java.time.LocalDate,
        @Param("to") to: java.time.LocalDate,
    ): List<java.time.LocalDate>
}
