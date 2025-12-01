package com.automan.backend.repository

import com.automan.backend.model.CarBrandMapping
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CarBrandMappingRepository : JpaRepository<CarBrandMapping, Long> {
    
    @Query("SELECT c FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand)")
    fun findByCarBrand(@Param("carBrand") carBrand: String): List<CarBrandMapping>
    
    @Query("SELECT DISTINCT c.chassis FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.chassis IS NOT NULL AND c.chassis != '' ORDER BY c.chassis")
    fun findDistinctChassisByCarBrand(@Param("carBrand") carBrand: String): List<String>
    
    @Query("SELECT DISTINCT c.carName FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.carName IS NOT NULL AND c.carName != '' ORDER BY c.carName")
    fun findDistinctCarNameByCarBrand(@Param("carBrand") carBrand: String): List<String>
    
    @Query("SELECT DISTINCT c.fuel FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.fuel IS NOT NULL AND c.fuel != '' ORDER BY c.fuel")
    fun findDistinctFuelByCarBrand(@Param("carBrand") carBrand: String): List<String>
    
    @Query("SELECT DISTINCT c.wd FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.wd IS NOT NULL AND c.wd != '' ORDER BY c.wd")
    fun findDistinctWdByCarBrand(@Param("carBrand") carBrand: String): List<String>
    
    @Query("SELECT DISTINCT c.shift FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.shift IS NOT NULL AND c.shift != '' ORDER BY c.shift")
    fun findDistinctShiftByCarBrand(@Param("carBrand") carBrand: String): List<String>
    
    @Query("SELECT DISTINCT c.cc FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.cc IS NOT NULL ORDER BY c.cc")
    fun findDistinctCcByCarBrand(@Param("carBrand") carBrand: String): List<Int>
    
    @Query("SELECT DISTINCT c.door FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.door IS NOT NULL ORDER BY c.door")
    fun findDistinctDoorByCarBrand(@Param("carBrand") carBrand: String): List<Int>
    
    @Query("SELECT DISTINCT c.grade FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.grade IS NOT NULL AND c.grade != '' ORDER BY c.grade")
    fun findDistinctGradeByCarBrand(@Param("carBrand") carBrand: String): List<String>
    
    // Find by brand and chassis (case-insensitive)
    // Order by: 1) non-null fuel first, 2) non-null carName first, 3) highest ID (most recent) first
    @Query("SELECT c FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.chassis = :chassis ORDER BY CASE WHEN c.fuel IS NOT NULL THEN 0 ELSE 1 END, CASE WHEN c.carName IS NOT NULL THEN 0 ELSE 1 END, c.id DESC")
    fun findByCarBrandAndChassis(@Param("carBrand") carBrand: String, @Param("chassis") chassis: String): List<CarBrandMapping>
    
    // Find by brand and car name (case-insensitive)
    @Query("SELECT c FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.carName = :carName ORDER BY c.id")
    fun findByCarBrandAndCarName(@Param("carBrand") carBrand: String, @Param("carName") carName: String): List<CarBrandMapping>
    
    // Find by brand, chassis, and car name (exact match, case-insensitive)
    @Query("SELECT c FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.chassis = :chassis AND c.carName = :carName ORDER BY c.id")
    fun findByCarBrandAndChassisAndCarName(@Param("carBrand") carBrand: String, @Param("chassis") chassis: String, @Param("carName") carName: String): List<CarBrandMapping>
    
    // Find distinct chassis by brand and car name (case-insensitive)
    @Query("SELECT DISTINCT c.chassis FROM CarBrandMapping c WHERE UPPER(c.carBrand) = UPPER(:carBrand) AND c.carName = :carName AND c.chassis IS NOT NULL AND c.chassis != '' ORDER BY c.chassis")
    fun findDistinctChassisByBrandAndCarName(@Param("carBrand") carBrand: String, @Param("carName") carName: String): List<String>
}

