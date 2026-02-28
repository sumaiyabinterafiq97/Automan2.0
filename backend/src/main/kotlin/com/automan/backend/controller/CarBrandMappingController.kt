package com.automan.backend.controller

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.util.Logger
import jakarta.persistence.EntityManager
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping(value = ["/car-brand-mapping", "/api/car-brand-mapping"])
@CrossOrigin(origins = [
    "http://localhost:8080",
    "http://localhost:8081",
    "http://localhost:8083",
    "http://localhost:8084",
    "http://localhost:8085",
    "http://localhost:8089",
    "http://localhost:8090",
    "http://localhost:9090"
])
class CarBrandMappingController(
    private val carBrandMappingRepository: CarBrandMappingRepository,
    private val entityManager: EntityManager
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
        val firstRow = mappings.firstOrNull() ?: return ResponseEntity.ok(mapOf(
            "found" to false,
            "data" to null as Any?
        ))
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
        
        val firstMatch = mappings.firstOrNull() ?: return ResponseEntity.ok(mapOf(
            "found" to false,
            "data" to null as Any?
        ))
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
        val firstRow = mappings.firstOrNull() ?: return ResponseEntity.ok(mapOf(
            "found" to false,
            "chassisList" to emptyList<String>(),
            "firstRow" to null as Any?
        ))
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
    
    @GetMapping("/chassis/{chassis}")
    fun getMappingByChassis(@PathVariable chassis: String): ResponseEntity<Map<String, Any?>> {
        val mappings = carBrandMappingRepository.findByChassis(chassis)
        
        if (mappings.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "found" to false,
                "brand" to null as Any?,
                "carName" to null as Any?,
                "fuel" to null as Any?,
                "wd" to null as Any?,
                "shift" to null as Any?,
                "cc" to null as Any?,
                "door" to null as Any?,
                "grade" to null as Any?,
                "firstRow" to null as Any?,
                "allRows" to emptyList<Map<String, Any>>(),
                "uniqueValues" to mapOf(
                    "brands" to emptyList<String>(),
                    "carNames" to emptyList<String>(),
                    "fuels" to emptyList<String>(),
                    "wds" to emptyList<String>(),
                    "shifts" to emptyList<String>(),
                    "ccs" to emptyList<String>(),
                    "doors" to emptyList<String>(),
                    "grades" to emptyList<String>()
                )
            ))
        }
        
        // Get first match (prioritized by fuel, carName, then most recent) for auto-fill
        val firstMatch = mappings.firstOrNull() ?: return ResponseEntity.ok(mapOf(
            "found" to false,
            "allRows" to emptyList<Map<String, Any>>(),
            "uniqueValues" to mapOf(
                "brands" to emptyList<String>(),
                "carNames" to emptyList<String>(),
                "fuels" to emptyList<String>(),
                "wds" to emptyList<String>(),
                "shifts" to emptyList<String>(),
                "ccs" to emptyList<String>(),
                "doors" to emptyList<String>(),
                "grades" to emptyList<String>()
            )
        ))
        val firstRowData = mapOf(
            "id" to (firstMatch.id ?: 0),
            "brand" to (firstMatch.carBrand ?: ""),
            "chassis" to (firstMatch.chassis ?: ""),
            "carName" to (firstMatch.carName ?: ""),
            "fuel" to (firstMatch.fuel ?: ""),
            "wd" to (firstMatch.wd ?: ""),
            "shift" to (firstMatch.shift ?: ""),
            "cc" to (firstMatch.cc?.toString() ?: ""),
            "door" to (firstMatch.door?.toString() ?: ""),
            "grade" to (firstMatch.grade ?: "")
        )
        
        // Convert all mappings to map format
        val allRows = mappings.map { mapping ->
            mapOf(
                "id" to (mapping.id ?: 0),
                "brand" to (mapping.carBrand ?: ""),
                "chassis" to (mapping.chassis ?: ""),
                "carName" to (mapping.carName ?: ""),
                "fuel" to (mapping.fuel ?: ""),
                "wd" to (mapping.wd ?: ""),
                "shift" to (mapping.shift ?: ""),
                "cc" to (mapping.cc?.toString() ?: ""),
                "door" to (mapping.door?.toString() ?: ""),
                "grade" to (mapping.grade ?: "")
            )
        }
        
        // Extract unique values for each field across all rows
        val uniqueBrands = mappings.mapNotNull { it.carBrand }.distinct().sorted()
        val uniqueCarNames = mappings.mapNotNull { it.carName }.distinct().sorted()
        val uniqueFuels = mappings.mapNotNull { it.fuel }.distinct().sorted()
        val uniqueWds = mappings.mapNotNull { it.wd }.distinct().sorted()
        val uniqueShifts = mappings.mapNotNull { it.shift }.distinct().sorted()
        val uniqueCcs = mappings.mapNotNull { it.cc?.toString() }.distinct().sorted()
        val uniqueDoors = mappings.mapNotNull { it.door?.toString() }.distinct().sorted()
        val uniqueGrades = mappings.mapNotNull { it.grade }.distinct().sorted()
        
        val data = mapOf(
            "found" to true,
            "brand" to (firstMatch.carBrand ?: ""), // Keep for backward compatibility
            "carName" to (firstMatch.carName ?: ""),
            "fuel" to (firstMatch.fuel ?: ""),
            "wd" to (firstMatch.wd ?: ""),
            "shift" to (firstMatch.shift ?: ""),
            "cc" to (firstMatch.cc?.toString() ?: ""),
            "door" to (firstMatch.door?.toString() ?: ""),
            "grade" to (firstMatch.grade ?: ""),
            "firstRow" to firstRowData,
            "allRows" to allRows,
            "uniqueValues" to mapOf(
                "brands" to uniqueBrands,
                "carNames" to uniqueCarNames,
                "fuels" to uniqueFuels,
                "wds" to uniqueWds,
                "shifts" to uniqueShifts,
                "ccs" to uniqueCcs,
                "doors" to uniqueDoors,
                "grades" to uniqueGrades
            ),
            "mappings" to allRows // Keep for backward compatibility
        )
        
        return ResponseEntity.ok(data)
    }
    
    @GetMapping("/chassis/{chassis}/match")
    fun findMatchingRowByChassis(
        @PathVariable chassis: String,
        @RequestParam(required = false) brand: String?,
        @RequestParam(required = false) carName: String?,
        @RequestParam(required = false) fuel: String?,
        @RequestParam(required = false) wd: String?,
        @RequestParam(required = false) shift: String?,
        @RequestParam(required = false) cc: String?,
        @RequestParam(required = false) door: String?,
        @RequestParam(required = false) grade: String?
    ): ResponseEntity<Map<String, Any?>> {
        return try {
            val allMappings = carBrandMappingRepository.findByChassis(chassis)
            
            if (allMappings.isEmpty()) {
                return ResponseEntity.ok(mapOf(
                    "found" to false,
                    "match" to null as Any?
                ))
            }
            
            // Find matching row based on provided parameters
            val match = allMappings.firstOrNull { mapping ->
                (brand == null || mapping.carBrand?.equals(brand, ignoreCase = true) == true) &&
                (carName == null || mapping.carName?.equals(carName, ignoreCase = true) == true) &&
                (fuel == null || mapping.fuel?.equals(fuel, ignoreCase = true) == true) &&
                (wd == null || mapping.wd?.equals(wd, ignoreCase = true) == true) &&
                (shift == null || mapping.shift?.equals(shift, ignoreCase = true) == true) &&
                (cc == null || mapping.cc?.toString() == cc) &&
                (door == null || mapping.door?.toString() == door) &&
                (grade == null || mapping.grade?.equals(grade, ignoreCase = true) == true)
            }
            
            if (match == null) {
                // If no exact match found, return first row as fallback
                val firstRow = allMappings.firstOrNull() ?: return ResponseEntity.ok(mapOf(
                    "found" to false,
                    "match" to null as Any?,
                    "message" to "No mappings found"
                ))
                val firstRowData = mapOf(
                    "id" to (firstRow.id ?: 0),
                    "brand" to (firstRow.carBrand ?: ""),
                    "chassis" to (firstRow.chassis ?: ""),
                    "carName" to (firstRow.carName ?: ""),
                    "fuel" to (firstRow.fuel ?: ""),
                    "wd" to (firstRow.wd ?: ""),
                    "shift" to (firstRow.shift ?: ""),
                    "cc" to (firstRow.cc?.toString() ?: ""),
                    "door" to (firstRow.door?.toString() ?: ""),
                    "grade" to (firstRow.grade ?: "")
                )
                return ResponseEntity.ok(mapOf(
                    "found" to true,
                    "match" to firstRowData,
                    "isFallback" to true
                ))
            }
            
            val matchData = mapOf(
                "id" to (match.id ?: 0),
                "brand" to (match.carBrand ?: ""),
                "chassis" to (match.chassis ?: ""),
                "carName" to (match.carName ?: ""),
                "fuel" to (match.fuel ?: ""),
                "wd" to (match.wd ?: ""),
                "shift" to (match.shift ?: ""),
                "cc" to (match.cc?.toString() ?: ""),
                "door" to (match.door?.toString() ?: ""),
                "grade" to (match.grade ?: "")
            )
            
            ResponseEntity.ok(mapOf(
                "found" to true,
                "match" to matchData,
                "isFallback" to false
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "found" to false,
                "message" to "Failed to find matching row: ${e.message}",
                "match" to null as Any?
            ))
        }
    }
    
    @GetMapping("/chassis/all")
    fun getAllDistinctChassis(): ResponseEntity<Map<String, Any>> {
        return try {
            val chassisList = carBrandMappingRepository.findDistinctChassisAll()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "chassisList" to chassisList
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to load chassis list: ${e.message}",
                "chassisList" to emptyList<String>()
            ))
        }
    }
    
    @GetMapping("/mappings")
    fun getAllMappings(): ResponseEntity<Map<String, Any>> {
        return try {
            val mappings = carBrandMappingRepository.findAll()
            
            val mappingsData = mappings.map { mapping ->
                mapOf(
                    "id" to (mapping.id ?: 0),
                    "carBrand" to (mapping.carBrand ?: ""),
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
            Logger.debug("➕ [CREATE] Received create request")
            Logger.debug("➕ [CREATE] Request body type: ${request.javaClass.name}")
            Logger.debug("➕ [CREATE] Request body: $request")
            Logger.debug("➕ [CREATE] Request keys: ${request.keys}")
            
            // Extract carBrand with better handling
            val carBrandValue = request["carBrand"]
            Logger.debug("➕ [CREATE] carBrand raw value: $carBrandValue (type: ${carBrandValue?.javaClass?.name})")
            
            val carBrand = when {
                carBrandValue == null -> {
                    Logger.error("❌ [CREATE] carBrand is null")
                    null
                }
                carBrandValue is String -> {
                    Logger.debug("➕ [CREATE] carBrand is String: $carBrandValue")
                    carBrandValue
                }
                else -> {
                    val str = carBrandValue.toString()
                    Logger.debug("➕ [CREATE] carBrand converted to String: $str")
                    str
                }
            }
            
            if (carBrand.isNullOrBlank()) {
                Logger.error("❌ [CREATE] carBrand is missing or blank after conversion")
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
            
            Logger.debug("➕ [CREATE] Parsed values: carBrand=$carBrand, chassis=$chassisStr, carName=$carNameStr, fuel=$fuelStr, wd=$wdStr, shift=$shiftStr, cc=$ccValue, door=$doorValue, grade=$gradeStr")
            
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
            
            Logger.debug("➕ [CREATE] Created mapping entity: $mapping")
            
            val saved = carBrandMappingRepository.save(mapping)
            
            Logger.debug("➕ [CREATE] Saved mapping with ID: ${saved.id}")
            
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
            Logger.error("❌ [CREATE] Error creating mapping: ${e.message}")
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
            Logger.debug("🔄 [UPDATE] Received update request for mapping ID: $id")
            Logger.debug("🔄 [UPDATE] Request body: $request")
            
            val existing = carBrandMappingRepository.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
            
            Logger.debug("🔄 [UPDATE] Existing mapping: id=${existing.id}, carBrand=${existing.carBrand}, chassis=${existing.chassis}, carName=${existing.carName}, fuel=${existing.fuel}")
            
            // IMPORTANT: If a field is explicitly sent as null or empty string, clear it (set to null)
            // If a field is not present in request, keep existing value
            
            // Helper function to safely extract string value, handling null correctly
            fun getStringValue(key: String): String? {
                val value = request[key]
                return when {
                    value == null -> null  // Explicitly null in JSON
                    value is String -> if (value.isBlank()) null else value
                    else -> value.toString().takeIf { it.isNotBlank() }
                }
            }
            
            // Car Brand: allow update from request (required field - fallback to existing if not present or blank)
            val carBrandValue = if (request.containsKey("carBrand")) {
                getStringValue("carBrand") ?: existing.carBrand
            } else {
                existing.carBrand
            }
            if (carBrandValue.isNullOrBlank()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Car Brand is required"
                ))
            }
            
            // If field is present in request (even if null/empty), use the new value (null if blank)
            // If field is not present in request, keep existing value
            val chassisValue = if (request.containsKey("chassis")) {
                getStringValue("chassis")
            } else {
                existing.chassis
            }
            val carNameValue = if (request.containsKey("carName")) {
                getStringValue("carName")
            } else {
                existing.carName
            }
            val fuelValue = if (request.containsKey("fuel")) {
                getStringValue("fuel")
            } else {
                existing.fuel
            }
            val wdValue = if (request.containsKey("wd")) {
                val rawWd = request["wd"]
                Logger.debug("🔄 [UPDATE] Raw 'wd' from request: $rawWd (type: ${rawWd?.javaClass?.simpleName})")
                val result = getStringValue("wd")
                Logger.debug("🔄 [UPDATE] Processed 'wd' value: $result (existing was: ${existing.wd})")
                result
            } else {
                existing.wd
            }
            val shiftValue = if (request.containsKey("shift")) {
                val rawShift = request["shift"]
                Logger.debug("🔄 [UPDATE] Raw 'shift' from request: $rawShift (type: ${rawShift?.javaClass?.simpleName})")
                val result = getStringValue("shift")
                Logger.debug("🔄 [UPDATE] Processed 'shift' value: $result (existing was: ${existing.shift})")
                result
            } else {
                existing.shift
            }
            val gradeValue = if (request.containsKey("grade")) {
                getStringValue("grade")
            } else {
                existing.grade
            }
            
            // For cc and door: if present in request (even if null), use new value; otherwise keep existing
            val ccValue = if (request.containsKey("cc")) {
                when (val cc = request["cc"]) {
                    null -> null  // Explicitly null means clear the field
                    is Number -> {
                        val intValue = cc.toInt()
                        if (intValue > 0) intValue else null
                    }
                    is String -> if (cc.isBlank()) null else cc.toIntOrNull()
                    else -> null
                }
            } else {
                existing.cc  // Field not in request, keep existing
            }
            
            val doorValue = if (request.containsKey("door")) {
                when (val door = request["door"]) {
                    null -> null  // Explicitly null means clear the field
                    is Number -> {
                        val intValue = door.toInt()
                        if (intValue > 0) intValue else null
                    }
                    is String -> if (door.isBlank()) null else door.toIntOrNull()
                    else -> null
                }
            } else {
                existing.door  // Field not in request, keep existing
            }
            
            Logger.debug("🔄 [UPDATE] New values: carBrand=$carBrandValue, chassis=$chassisValue, carName=$carNameValue, fuel=$fuelValue, wd=$wdValue, shift=$shiftValue, cc=$ccValue, door=$doorValue, grade=$gradeValue")
            Logger.debug("🔄 [UPDATE] Fields being cleared: wd=${wdValue == null && existing.wd != null} (wdValue=$wdValue, existing.wd=${existing.wd}), shift=${shiftValue == null && existing.shift != null} (shiftValue=$shiftValue, existing.shift=${existing.shift})")
            
            // CRITICAL: Instead of using .copy() which creates a detached entity,
            // we need to modify the existing managed entity directly or use merge.
            // Since CarBrandMapping is a data class with val properties, we can't modify in place.
            // We'll use copy but ensure JPA recognizes it as an update by checking ID exists.
            
            // Ensure createdAt is never null - use existing value or current time
            val createdAtValue = existing.createdAt ?: LocalDateTime.now()
            
            // Create updated entity with same ID - JPA should recognize this as update
            val updated = existing.copy(
                id = existing.id, // CRITICAL: Explicitly preserve ID
                carBrand = carBrandValue, // Allow car brand to be updated from request
                chassis = chassisValue,
                carName = carNameValue,
                fuel = fuelValue,
                wd = wdValue,
                shift = shiftValue,
                cc = ccValue,
                door = doorValue,
                grade = gradeValue,
                createdAt = createdAtValue, // Preserve createdAt (never null)
                updatedAt = LocalDateTime.now()
            )
            
            Logger.debug("🔄 [UPDATE] Updated entity before save: id=${updated.id}, chassis=${updated.chassis}, carName=${updated.carName}, fuel=${updated.fuel}, wd=${updated.wd}, shift=${updated.shift}, cc=${updated.cc}, door=${updated.door}, grade=${updated.grade}")
            Logger.debug("🔄 [UPDATE] Original entity ID: ${existing.id}, Updated entity ID: ${updated.id}")
            
            // Verify ID is preserved
            if (updated.id != existing.id) {
                Logger.error("❌ [UPDATE] ERROR: ID mismatch! Original: ${existing.id}, Updated: ${updated.id}")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Internal error: ID mismatch during update"
                ))
            }
            
            // CRITICAL FIX: Use EntityManager.merge() instead of repository.save() 
            // to ensure JPA recognizes this as an UPDATE, not a CREATE
            // merge() will attach the detached entity and update it
            val merged = entityManager.merge(updated)
            entityManager.flush() // Force immediate database update
            
            // Cast merged result back to CarBrandMapping
            val saved = merged as CarBrandMapping
            
            Logger.debug("🔄 [UPDATE] Merged entity: id=${saved.id}, chassis=${saved.chassis}, carName=${saved.carName}, fuel=${saved.fuel}, wd=${saved.wd}, shift=${saved.shift}, cc=${saved.cc}, door=${saved.door}, grade=${saved.grade}")
            Logger.debug("🔄 [UPDATE] Comparing saved vs intended: wd saved=${saved.wd} intended=$wdValue, shift saved=${saved.shift} intended=$shiftValue")
            
            // Verify the saved entity has the same ID (not a new one)
            if (saved.id != existing.id) {
                Logger.error("❌ [UPDATE] ERROR: Saved entity has different ID! Original: ${existing.id}, Saved: ${saved.id}")
                Logger.error("❌ [UPDATE] This indicates a new row was created instead of updating!")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Error: Update created a new row instead of updating existing one"
                ))
            }
            
            // Verify fields were cleared correctly
            if (wdValue == null && saved.wd != null) {
                Logger.warn("⚠️ [UPDATE] WARNING: WD field was supposed to be cleared but saved value is: ${saved.wd}")
            }
            if (shiftValue == null && saved.shift != null) {
                Logger.warn("⚠️ [UPDATE] WARNING: Shift field was supposed to be cleared but saved value is: ${saved.shift}")
            }
            
            Logger.debug("✅ [UPDATE] Successfully updated existing mapping with ID: ${saved.id}")
            
            Logger.debug("🔄 [UPDATE] Saved entity: id=${saved.id}, carBrand=${saved.carBrand}, chassis=${saved.chassis}, carName=${saved.carName}, fuel=${saved.fuel}")
            
            return ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping updated successfully",
                "data" to mapOf(
                    "id" to saved.id,
                    "carBrand" to (saved.carBrand ?: ""),
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
            Logger.error("❌ [UPDATE] Error updating mapping: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to update mapping: ${e.message}",
                "error" to e.javaClass.simpleName
            ))
        }
    }
    
    @GetMapping("/mappings/{id}")
    fun getMappingById(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        return try {
            val mapping = carBrandMappingRepository.findById(id).orElse(null)
                ?: return ResponseEntity.notFound().build()
            
            val mappingData = mapOf<String, Any?>(
                "id" to (mapping.id ?: 0),
                "carBrand" to (mapping.carBrand ?: ""),
                "chassis" to (mapping.chassis ?: ""),
                "carName" to (mapping.carName ?: ""),
                "fuel" to (mapping.fuel ?: ""),
                "wd" to (mapping.wd ?: ""),
                "shift" to (mapping.shift ?: ""),
                "cc" to (mapping.cc ?: 0),
                "door" to (mapping.door ?: 0),
                "grade" to (mapping.grade ?: "")
            )
            
            ResponseEntity.ok(mapOf<String, Any?>(
                "success" to true,
                "data" to mappingData
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf<String, Any?>(
                "success" to false,
                "message" to "Failed to load mapping: ${e.message}",
                "data" to null
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

