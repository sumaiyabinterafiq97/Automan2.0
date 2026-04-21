package com.automan.backend.repository

import com.automan.backend.model.RixoMapping
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
        SELECT rm
        FROM RixoMapping rm
        WHERE UPPER(rm.stockLocation) = UPPER(:stockLocation)
          AND UPPER(rm.rixoCompany) = UPPER(:rixoCompany)
          AND (
                (:supportedVehicleType IS NULL AND rm.supportedVehicleType IS NULL)
             OR (:supportedVehicleType IS NOT NULL AND UPPER(COALESCE(rm.supportedVehicleType, '')) = UPPER(:supportedVehicleType))
          )
        ORDER BY rm.id DESC
        """
    )
    fun findTopMatch(
        @Param("stockLocation") stockLocation: String,
        @Param("rixoCompany") rixoCompany: String,
        @Param("supportedVehicleType") supportedVehicleType: String?
    ): List<RixoMapping>
}

