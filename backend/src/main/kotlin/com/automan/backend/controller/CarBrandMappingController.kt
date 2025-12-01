package com.automan.backend.controller

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.repository.CarBrandMappingRepository
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/car-brand-mapping")
class CarBrandMappingController(
    private val carBrandMappingRepository: CarBrandMappingRepository
) {
    
    @GetMapping("/brand/{brandName}")
    fun getMappingsByBrand(@PathVariable brandName: String): ResponseEntity<Map<String, Any?>> {
        val mappings = carBrandMappingRepository.findByCarBrand(brandName)
        
        if (mappings.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "mappings" to emptyList<Map<String, Any>>(),
                "firstRow" to null as Any?,
                "dropdowns" to mapOf(
                    "chassis" to emptyList<String>(),
                    "carName" to emptyList<String>(),
                    "fuel" to emptyList<String>(),
                    "wd" to emptyList<String>(),
                    "shift" to emptyList<String>(),
                    "cc" to emptyList<String>(),
                    "door" to emptyList<String>(),
                    "grade" to emptyList<String>()
                )
            ))
        }
        
        // Get first row for auto-fill
        val firstRow = mappings.first()
        val firstRowData = mapOf(
            "chassis" to (firstRow.chassis ?: ""),
            "carName" to (firstRow.carName ?: ""),
            "fuel" to (firstRow.fuel ?: ""),
            "wd" to (firstRow.wd ?: ""),
            "shift" to (firstRow.shift ?: ""),
            "cc" to (firstRow.cc ?: 0),
            "door" to (firstRow.door ?: 0),
            "grade" to (firstRow.grade ?: "")
        )
        
        // Get unique values for dropdowns
        val chassisList = carBrandMappingRepository.findDistinctChassisByCarBrand(brandName)
        val carNameList = carBrandMappingRepository.findDistinctCarNameByCarBrand(brandName)
        val fuelList = carBrandMappingRepository.findDistinctFuelByCarBrand(brandName)
        val wdList = carBrandMappingRepository.findDistinctWdByCarBrand(brandName)
        val shiftList = carBrandMappingRepository.findDistinctShiftByCarBrand(brandName)
        val ccList = carBrandMappingRepository.findDistinctCcByCarBrand(brandName)
        val doorList = carBrandMappingRepository.findDistinctDoorByCarBrand(brandName)
        val gradeList = carBrandMappingRepository.findDistinctGradeByCarBrand(brandName)
        
        return ResponseEntity.ok(mapOf(
            "mappings" to mappings.map { mapping ->
                mapOf(
                    "id" to mapping.id,
                    "chassis" to (mapping.chassis ?: ""),
                    "carName" to (mapping.carName ?: ""),
                    "fuel" to (mapping.fuel ?: ""),
                    "wd" to (mapping.wd ?: ""),
                    "shift" to (mapping.shift ?: ""),
                    "cc" to (mapping.cc ?: 0),
                    "door" to (mapping.door ?: 0),
                    "grade" to (mapping.grade ?: "")
                )
            },
            "firstRow" to firstRowData,
            "dropdowns" to mapOf(
                "chassis" to chassisList,
                "carName" to carNameList,
                "fuel" to fuelList,
                "wd" to wdList,
                "shift" to shiftList,
                "cc" to ccList.map { it.toString() },
                "door" to doorList.map { it.toString() },
                "grade" to gradeList
            )
        ))
    }
    
    @GetMapping("/brand/{brandName}/match")
    fun getMappingByBrandAndChassisOrCarName(
        @PathVariable brandName: String,
        @RequestParam(required = false) chassis: String?,
        @RequestParam(required = false) carName: String?
    ): ResponseEntity<Map<String, Any?>> {
        val mappings = when {
            !chassis.isNullOrBlank() && !carName.isNullOrBlank() -> {
                // Both provided - try exact match first
                carBrandMappingRepository.findByCarBrandAndChassisAndCarName(brandName, chassis, carName)
                    .ifEmpty { carBrandMappingRepository.findByCarBrandAndChassis(brandName, chassis) }
            }
            !chassis.isNullOrBlank() -> {
                carBrandMappingRepository.findByCarBrandAndChassis(brandName, chassis)
            }
            !carName.isNullOrBlank() -> {
                carBrandMappingRepository.findByCarBrandAndCarName(brandName, carName)
            }
            else -> emptyList()
        }
        
        if (mappings.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "found" to false,
                "data" to null as Any?
            ))
        }
        
        val firstMatch = mappings.first()
        val data = mapOf(
            "carName" to (firstMatch.carName ?: ""),
            "fuel" to (firstMatch.fuel ?: ""),
            "wd" to (firstMatch.wd ?: ""),
            "shift" to (firstMatch.shift ?: ""),
            "cc" to (firstMatch.cc?.toString() ?: ""), // Convert to string, empty if null
            "door" to (firstMatch.door?.toString() ?: ""), // Convert to string, empty if null
            "grade" to (firstMatch.grade ?: "")
        )
        
        return ResponseEntity.ok(mapOf(
            "found" to true,
            "data" to data
        ))
    }
    
    @GetMapping("/brand/{brandName}/car-name/{carName}")
    fun getMappingsByBrandAndCarName(
        @PathVariable brandName: String,
        @PathVariable carName: String
    ): ResponseEntity<Map<String, Any?>> {
        val mappings = carBrandMappingRepository.findByCarBrandAndCarName(brandName, carName)
        
        if (mappings.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "found" to false,
                "chassisList" to emptyList<String>(),
                "firstRow" to null as Any?
            ))
        }
        
        // Get distinct chassis for this car name
        val chassisList = carBrandMappingRepository.findDistinctChassisByBrandAndCarName(brandName, carName)
        
        // Get first row for auto-fill (prioritize rows with non-null fuel and carName)
        val firstRow = mappings.first()
        val firstRowData = mapOf(
            "chassis" to (firstRow.chassis ?: ""),
            "fuel" to (firstRow.fuel ?: ""),
            "wd" to (firstRow.wd ?: ""),
            "shift" to (firstRow.shift ?: ""),
            "cc" to (firstRow.cc?.toString() ?: ""), // Convert to string, empty if null
            "door" to (firstRow.door?.toString() ?: ""), // Convert to string, empty if null
            "grade" to (firstRow.grade ?: "")
        )
        
        return ResponseEntity.ok(mapOf(
            "found" to true,
            "chassisList" to chassisList,
            "firstRow" to firstRowData
        ))
    }
    
    @GetMapping("/brand/{brandName}/mappings")
    fun getAllMappingsByBrand(@PathVariable brandName: String): ResponseEntity<Map<String, Any>> {
        return try {
            val mappings = carBrandMappingRepository.findByCarBrand(brandName)
            
            val mappingsData = mappings.map { mapping ->
                mapOf(
                    "id" to (mapping.id ?: 0),
                    "chassis" to (mapping.chassis ?: ""),
                    "carName" to (mapping.carName ?: ""),
                    "fuel" to (mapping.fuel ?: ""),
                    "wd" to (mapping.wd ?: ""),
                    "shift" to (mapping.shift ?: ""),
                    "cc" to (mapping.cc ?: 0),
                    "door" to (mapping.door ?: 0),
                    "grade" to (mapping.grade ?: "")
                )
            }
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to mappingsData
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to load mappings: ${e.message}",
                "data" to emptyList<Map<String, Any>>()
            ))
        }
    }
    
    @PostMapping("/mappings")
    @Transactional
    fun createMapping(@RequestBody request: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        try {
            println("➕ [CREATE] Received create request")
            println("➕ [CREATE] Request body type: ${request.javaClass.name}")
            println("➕ [CREATE] Request body: $request")
            println("➕ [CREATE] Request keys: ${request.keys}")
            
            // Extract carBrand with better handling
            val carBrandValue = request["carBrand"]
            println("➕ [CREATE] carBrand raw value: $carBrandValue (type: ${carBrandValue?.javaClass?.name})")
            
            val carBrand = when {
                carBrandValue == null -> {
                    println("❌ [CREATE] carBrand is null")
                    null
                }
                carBrandValue is String -> {
                    println("➕ [CREATE] carBrand is String: $carBrandValue")
                    carBrandValue
                }
                else -> {
                    val str = carBrandValue.toString()
                    println("➕ [CREATE] carBrand converted to String: $str")
                    str
                }
            }
            
            if (carBrand.isNullOrBlank()) {
                println("❌ [CREATE] carBrand is missing or blank after conversion")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "carBrand is required"
                ))
            }
            
            val chassisStr = request["chassis"]?.toString()
            val carNameStr = request["carName"]?.toString()
            val fuelStr = request["fuel"]?.toString()
            val wdStr = request["wd"]?.toString()
            val shiftStr = request["shift"]?.toString()
            val gradeStr = request["grade"]?.toString()
            
            // Handle cc and door conversion
            val ccValue = when (val cc = request["cc"]) {
                null -> null
                is Number -> cc.toInt().takeIf { it > 0 }
                is String -> if (cc.isBlank()) null else cc.toIntOrNull()
                else -> null
            }
            
            val doorValue = when (val door = request["door"]) {
                null -> null
                is Number -> door.toInt().takeIf { it > 0 }
                is String -> if (door.isBlank()) null else door.toIntOrNull()
                else -> null
            }
            
            println("➕ [CREATE] Parsed values: carBrand=$carBrand, chassis=$chassisStr, carName=$carNameStr, fuel=$fuelStr, wd=$wdStr, shift=$shiftStr, cc=$ccValue, door=$doorValue, grade=$gradeStr")
            
            val mapping = CarBrandMapping(
                carBrand = carBrand,
                chassis = if (chassisStr.isNullOrBlank()) null else chassisStr,
                carName = if (carNameStr.isNullOrBlank()) null else carNameStr,
                fuel = if (fuelStr.isNullOrBlank()) null else fuelStr,
                wd = if (wdStr.isNullOrBlank()) null else wdStr,
                shift = if (shiftStr.isNullOrBlank()) null else shiftStr,
                cc = ccValue,
                door = doorValue,
                grade = if (gradeStr.isNullOrBlank()) null else gradeStr
            )
            
            println("➕ [CREATE] Created mapping entity: $mapping")
            
            val saved = carBrandMappingRepository.save(mapping)
            
            println("➕ [CREATE] Saved mapping with ID: ${saved.id}")
            
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping created successfully",
                "data" to mapOf(
                    "id" to saved.id,
                    "chassis" to (saved.chassis ?: ""),
                    "carName" to (saved.carName ?: ""),
                    "fuel" to (saved.fuel ?: ""),
                    "wd" to (saved.wd ?: ""),
                    "shift" to (saved.shift ?: ""),
                    "cc" to (saved.cc ?: 0),
                    "door" to (saved.door ?: 0),
                    "grade" to (saved.grade ?: "")
                )
            ))
        } catch (e: Exception) {
            println("❌ [CREATE] Error creating mapping: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to create mapping: ${e.message}"
            ))
        }
    }
    
    @PutMapping("/mappings/{id}")
    @Transactional
    fun updateMapping(
        @PathVariable id: Long,
        @RequestBody request: Map<String, Any?>
    ): ResponseEntity<Map<String, Any>> {
        try {
            println("🔄 [UPDATE] Received update request for mapping ID: $id")
            println("🔄 [UPDATE] Request body: $request")
            
            val existing = carBrandMappingRepository.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
            
            println("🔄 [UPDATE] Existing mapping: id=${existing.id}, carBrand=${existing.carBrand}, chassis=${existing.chassis}, carName=${existing.carName}, fuel=${existing.fuel}")
            
            // Note: carBrand should not be changed in updates, use existing value
            val chassisStr = request["chassis"]?.toString()
            val carNameStr = request["carName"]?.toString()
            val fuelStr = request["fuel"]?.toString()
            val wdStr = request["wd"]?.toString()
            val shiftStr = request["shift"]?.toString()
            val gradeStr = request["grade"]?.toString()
            
            // Use new value if provided and not blank, otherwise keep existing
            val chassisValue = if (!chassisStr.isNullOrBlank()) chassisStr else existing.chassis
            val carNameValue = if (!carNameStr.isNullOrBlank()) carNameStr else existing.carName
            val fuelValue = if (!fuelStr.isNullOrBlank()) fuelStr else existing.fuel
            val wdValue = if (!wdStr.isNullOrBlank()) wdStr else existing.wd
            val shiftValue = if (!shiftStr.isNullOrBlank()) shiftStr else existing.shift
            val gradeValue = if (!gradeStr.isNullOrBlank()) gradeStr else existing.grade
            
            val ccValue = when (val cc = request["cc"]) {
                null -> existing.cc
                is Number -> {
                    val intValue = cc.toInt()
                    if (intValue > 0) intValue else null
                }
                is String -> if (cc.isBlank()) null else cc.toIntOrNull()
                else -> existing.cc
            }
            
            val doorValue = when (val door = request["door"]) {
                null -> existing.door
                is Number -> {
                    val intValue = door.toInt()
                    if (intValue > 0) intValue else null
                }
                is String -> if (door.isBlank()) null else door.toIntOrNull()
                else -> existing.door
            }
            
            println("🔄 [UPDATE] New values: chassis=$chassisValue, carName=$carNameValue, fuel=$fuelValue")
            
            val updated = existing.copy(
                id = existing.id, // Explicitly preserve ID
                carBrand = existing.carBrand, // Preserve carBrand
                chassis = chassisValue,
                carName = carNameValue,
                fuel = fuelValue,
                wd = wdValue,
                shift = shiftValue,
                cc = ccValue,
                door = doorValue,
                grade = gradeValue,
                createdAt = existing.createdAt, // Preserve createdAt
                updatedAt = LocalDateTime.now()
            )
            
            println("🔄 [UPDATE] Updated entity: chassis=${updated.chassis}, carName=${updated.carName}, fuel=${updated.fuel}")
            
            val saved = carBrandMappingRepository.save(updated)
            
            println("🔄 [UPDATE] Saved entity: id=${saved.id}, chassis=${saved.chassis}, carName=${saved.carName}, fuel=${saved.fuel}")
            
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping updated successfully",
                "data" to mapOf(
                    "id" to saved.id,
                    "chassis" to (saved.chassis ?: ""),
                    "carName" to (saved.carName ?: ""),
                    "fuel" to (saved.fuel ?: ""),
                    "wd" to (saved.wd ?: ""),
                    "shift" to (saved.shift ?: ""),
                    "cc" to (saved.cc ?: 0),
                    "door" to (saved.door ?: 0),
                    "grade" to (saved.grade ?: "")
                )
            ))
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to update mapping: ${e.message}"
            ))
        }
    }
    
    @DeleteMapping("/mappings/{id}")
    fun deleteMapping(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        try {
            if (!carBrandMappingRepository.existsById(id)) {
                return ResponseEntity.notFound().build()
            }
            
            carBrandMappingRepository.deleteById(id)
            
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping deleted successfully"
            ))
        } catch (e: Exception) {
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to delete mapping: ${e.message}"
            ))
        }
    }
}

