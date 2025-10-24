package com.automan.backend.repository

import com.automan.backend.model.Vessel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface VesselRepository : JpaRepository<Vessel, String> {
    
    fun findByVesselNo(vesselNo: String): Vessel?
    
    fun findAllByOrderByVesselNameAsc(): List<Vessel>
    
    @Query("SELECT v FROM Vessel v WHERE v.company = :company ORDER BY v.vesselName ASC")
    fun findByCompanyOrderByVesselNameAsc(@Param("company") company: String): List<Vessel>
    
    @Query("SELECT v FROM Vessel v WHERE v.vesselName LIKE %:name% ORDER BY v.vesselName ASC")
    fun findByVesselNameContainingIgnoreCaseOrderByVesselNameAsc(@Param("name") name: String): List<Vessel>
    
    @Query("SELECT DISTINCT v.company FROM Vessel v WHERE v.company IS NOT NULL ORDER BY v.company ASC")
    fun findDistinctCompanies(): List<String>
    
    fun existsByVesselNo(vesselNo: String): Boolean
}
