package com.automan.backend.repository

import com.automan.backend.model.RixoMapping
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RixoMappingRepository : JpaRepository<RixoMapping, Long> {

    @Query(
        """
        SELECT DISTINCT r.auctionName
        FROM RixoMapping r
        WHERE r.auctionName IS NOT NULL
          AND TRIM(r.auctionName) <> ''
        ORDER BY r.auctionName ASC
        """
    )
    fun findDistinctAuctionNamesOrdered(): List<String>

    @Query(
        """
        SELECT DISTINCT r.rixoCompany
        FROM RixoMapping r
        WHERE r.rixoCompany IS NOT NULL
          AND TRIM(r.rixoCompany) <> ''
        ORDER BY r.rixoCompany ASC
        """
    )
    fun findDistinctRixoCompaniesOrdered(): List<String>

    @Query(
        """
        SELECT DISTINCT r.stockLocation
        FROM RixoMapping r
        WHERE r.stockLocation IS NOT NULL
          AND TRIM(r.stockLocation) <> ''
          AND TRIM(r.stockLocation) <> '-'
        ORDER BY r.stockLocation ASC
        """
    )
    fun findDistinctStockLocationsOrdered(): List<String>

    @Query(
        """
        SELECT rm
        FROM RixoMapping rm
        WHERE UPPER(TRIM(rm.stockLocation)) = UPPER(TRIM(:stockLocation))
          AND UPPER(TRIM(rm.rixoCompany)) = UPPER(TRIM(:rixoCompany))
          AND UPPER(TRIM(COALESCE(rm.auctionName, ''))) = UPPER(TRIM(:auctionName))
          AND (
                (:supportedVehicleType IS NULL AND (rm.supportedVehicleType IS NULL OR TRIM(rm.supportedVehicleType) = ''))
             OR (:supportedVehicleType IS NOT NULL AND UPPER(TRIM(COALESCE(rm.supportedVehicleType, ''))) = UPPER(TRIM(:supportedVehicleType)))
          )
        ORDER BY rm.id ASC
        """
    )
    fun findExactMatch(
        @Param("auctionName") auctionName: String,
        @Param("stockLocation") stockLocation: String,
        @Param("rixoCompany") rixoCompany: String,
        @Param("supportedVehicleType") supportedVehicleType: String?,
    ): List<RixoMapping>

    @Query(
        """
        SELECT rm
        FROM RixoMapping rm
        WHERE UPPER(TRIM(rm.stockLocation)) = UPPER(TRIM(:stockLocation))
          AND UPPER(TRIM(rm.rixoCompany)) = UPPER(TRIM(:rixoCompany))
          AND UPPER(TRIM(COALESCE(rm.auctionName, ''))) = UPPER(TRIM(:auctionName))
          AND (rm.supportedVehicleType IS NULL OR TRIM(rm.supportedVehicleType) = '')
        ORDER BY rm.id ASC
        """
    )
    fun findMatchWithNullVehicleType(
        @Param("auctionName") auctionName: String,
        @Param("stockLocation") stockLocation: String,
        @Param("rixoCompany") rixoCompany: String,
    ): List<RixoMapping>

    @Query(
        """
        SELECT rm FROM RixoMapping rm
        WHERE rm.auctionName IS NOT NULL
          AND UPPER(TRIM(rm.auctionName)) = UPPER(TRIM(:auctionName))
          AND UPPER(TRIM(rm.stockLocation)) = UPPER(TRIM(:stockLocation))
          AND UPPER(TRIM(rm.rixoCompany)) = UPPER(TRIM(:rixoCompany))
        ORDER BY rm.id ASC
        """
    )
    fun findByAuctionStockRixo(
        @Param("auctionName") auctionName: String,
        @Param("stockLocation") stockLocation: String,
        @Param("rixoCompany") rixoCompany: String,
    ): List<RixoMapping>

    fun findByAuctionNameIgnoreCase(auctionName: String): List<RixoMapping>

    fun findFirstByAuctionNameIgnoreCase(auctionName: String): RixoMapping?

    @Query(
        value = """
            SELECT r FROM RixoMapping r WHERE
            r.auctionName IS NOT NULL AND (
            LOWER(r.auctionName) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
        countQuery = """
            SELECT count(r) FROM RixoMapping r WHERE
            r.auctionName IS NOT NULL AND (
            LOWER(r.auctionName) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%')))
            """
    )
    fun searchSupplierMapAllFields(@Param("q") q: String, pageable: Pageable): Page<RixoMapping>

    @Query(
        value = """
            SELECT r FROM RixoMapping r
            WHERE r.auctionName IS NOT NULL
              AND LOWER(r.auctionName) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(r) FROM RixoMapping r
            WHERE r.auctionName IS NOT NULL
              AND LOWER(r.auctionName) LIKE LOWER(CONCAT('%', :q, '%'))
            """
    )
    fun searchSupplierMapAuctionHouseContains(@Param("q") q: String, pageable: Pageable): Page<RixoMapping>

    @Query(
        value = """
            SELECT r FROM RixoMapping r
            WHERE LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(r) FROM RixoMapping r
            WHERE LOWER(r.stockLocation) LIKE LOWER(CONCAT('%', :q, '%'))
            """
    )
    fun searchSupplierMapStockLocationContains(@Param("q") q: String, pageable: Pageable): Page<RixoMapping>

    @Query(
        value = """
            SELECT r FROM RixoMapping r
            WHERE LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(r) FROM RixoMapping r
            WHERE LOWER(r.rixoCompany) LIKE LOWER(CONCAT('%', :q, '%'))
            """
    )
    fun searchSupplierMapRixoCompanyContains(@Param("q") q: String, pageable: Pageable): Page<RixoMapping>
}
