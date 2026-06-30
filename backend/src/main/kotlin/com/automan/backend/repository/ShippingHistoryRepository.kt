package com.automan.backend.repository

import com.automan.backend.model.ShippingHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ShippingHistoryRepository : JpaRepository<ShippingHistory, Long> {

    /** Single row per chassis (see migration V19); latest id if duplicates exist pre-migration. */
    fun findFirstByChassisOrderByIdDesc(chassis: String): ShippingHistory?

    fun findByChassisIn(chassis: Collection<String>): List<ShippingHistory>

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
}
