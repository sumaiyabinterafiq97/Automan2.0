package com.automan.backend.repository

import com.automan.backend.model.RixoMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RixoMappingRepository : JpaRepository<RixoMapping, Long> {

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

