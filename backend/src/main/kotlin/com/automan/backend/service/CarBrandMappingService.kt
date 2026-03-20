package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.util.Logger
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Business logic for car brand mappings.
 * Controllers delegate here; this layer uses only the repository (and EntityManager for update merge).
 */
@Service
class CarBrandMappingService(
    private val carBrandMappingRepository: CarBrandMappingRepository,
    private val entityManager: EntityManager
) {

    fun getMappingsByBrand(brandName: String): Map<String, Any?> {
        val mappings = carBrandMappingRepository.findByCarBrand(brandName)
        if (mappings.isEmpty()) {
            return mapOf(
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
                "grade" to emptyList<String>(),
                "seats" to emptyList<String>()
                )
            )
        }
        val firstRow = mappings.first()
        val firstRowData = mapOf(
            "chassis" to (firstRow.chassis ?: ""),
            "carName" to (firstRow.carName ?: ""),
            "fuel" to (firstRow.fuel ?: ""),
            "wd" to (firstRow.wd ?: ""),
            "shift" to (firstRow.shift ?: ""),
            "cc" to (firstRow.cc ?: 0),
            "door" to (firstRow.door ?: 0),
            "grade" to (firstRow.grade ?: ""),
            "seat" to (firstRow.seat ?: 0),
            "vehicleType" to (firstRow.vehicleType ?: ""),
            "rank" to (firstRow.rank ?: ""),
            "color" to (firstRow.color ?: ""),
            "driveType" to (firstRow.driveType ?: "")
        )
        val chassisList = carBrandMappingRepository.findDistinctChassisByCarBrand(brandName)
        val carNameList = carBrandMappingRepository.findDistinctCarNameByCarBrand(brandName)
        val fuelList = carBrandMappingRepository.findDistinctFuelByCarBrand(brandName)
        val wdList = carBrandMappingRepository.findDistinctWdByCarBrand(brandName)
        val shiftList = carBrandMappingRepository.findDistinctShiftByCarBrand(brandName)
        val ccList = carBrandMappingRepository.findDistinctCcByCarBrand(brandName)
        val doorList = carBrandMappingRepository.findDistinctDoorByCarBrand(brandName)
        val gradeList = carBrandMappingRepository.findDistinctGradeByCarBrand(brandName)
        val seatList = mappings.mapNotNull { it.seat }.distinct().sorted()
        return mapOf(
            "mappings" to mappings.map { toMap(it) },
            "firstRow" to firstRowData,
            "dropdowns" to mapOf(
                "chassis" to chassisList,
                "carName" to carNameList,
                "fuel" to fuelList,
                "wd" to wdList,
                "shift" to shiftList,
                "cc" to ccList.map { it.toString() },
                "door" to doorList.map { it.toString() },
                "grade" to gradeList,
                "seats" to seatList.map { it.toString() }
            )
        )
    }

    fun getMappingByBrandAndChassisOrCarName(brandName: String, chassis: String?, carName: String?): Map<String, Any?> {
        val mappings = when {
            !chassis.isNullOrBlank() && !carName.isNullOrBlank() ->
                carBrandMappingRepository.findByCarBrandAndChassisAndCarName(brandName, chassis, carName)
                    .ifEmpty { carBrandMappingRepository.findByCarBrandAndChassis(brandName, chassis) }
            !chassis.isNullOrBlank() -> carBrandMappingRepository.findByCarBrandAndChassis(brandName, chassis)
            !carName.isNullOrBlank() -> carBrandMappingRepository.findByCarBrandAndCarName(brandName, carName)
            else -> emptyList()
        }
        if (mappings.isEmpty()) return mapOf("found" to false, "data" to null as Any?)
        val firstMatch = mappings.first()
        val data = mapOf(
            "carName" to (firstMatch.carName ?: ""),
            "fuel" to (firstMatch.fuel ?: ""),
            "wd" to (firstMatch.wd ?: ""),
            "shift" to (firstMatch.shift ?: ""),
            "cc" to (firstMatch.cc?.toString() ?: ""),
            "door" to (firstMatch.door?.toString() ?: ""),
            "grade" to (firstMatch.grade ?: ""),
            "seat" to (firstMatch.seat?.toString() ?: ""),
            "vehicleType" to (firstMatch.vehicleType ?: ""),
            "rank" to (firstMatch.rank ?: ""),
            "color" to (firstMatch.color ?: ""),
            "driveType" to (firstMatch.driveType ?: "")
        )
        return mapOf("found" to true, "data" to data)
    }

    fun getMappingsByBrandAndCarName(brandName: String, carName: String): Map<String, Any?> {
        val mappings = carBrandMappingRepository.findByCarBrandAndCarName(brandName, carName)
        if (mappings.isEmpty()) {
            return mapOf(
                "found" to false,
                "chassisList" to emptyList<String>(),
                "firstRow" to null as Any?
            )
        }
        val chassisList = carBrandMappingRepository.findDistinctChassisByBrandAndCarName(brandName, carName)
        val firstRow = mappings.first()
        val firstRowData = mapOf(
            "chassis" to (firstRow.chassis ?: ""),
            "fuel" to (firstRow.fuel ?: ""),
            "wd" to (firstRow.wd ?: ""),
            "shift" to (firstRow.shift ?: ""),
            "cc" to (firstRow.cc?.toString() ?: ""),
            "door" to (firstRow.door?.toString() ?: ""),
            "grade" to (firstRow.grade ?: ""),
            "seat" to (firstRow.seat?.toString() ?: ""),
            "vehicleType" to (firstRow.vehicleType ?: ""),
            "rank" to (firstRow.rank ?: ""),
            "color" to (firstRow.color ?: ""),
            "driveType" to (firstRow.driveType ?: "")
        )
        return mapOf("found" to true, "chassisList" to chassisList, "firstRow" to firstRowData)
    }

    fun getMappingByChassis(chassis: String): Map<String, Any?> {
        val mappings = carBrandMappingRepository.findByChassis(chassis)
        if (mappings.isEmpty()) {
            return mapOf(
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
                    "grades" to emptyList<String>(),
                    "seats" to emptyList<String>(),
                    "vehicleTypes" to emptyList<String>(),
                    "ranks" to emptyList<String>(),
                    "colors" to emptyList<String>(),
                    "driveTypes" to emptyList<String>()
                )
            )
        }
        val firstMatch = mappings.first()
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
            "grade" to (firstMatch.grade ?: ""),
            "seat" to (firstMatch.seat?.toString() ?: ""),
            "vehicleType" to (firstMatch.vehicleType ?: ""),
            "rank" to (firstMatch.rank ?: ""),
            "color" to (firstMatch.color ?: ""),
            "driveType" to (firstMatch.driveType ?: "")
        )
        val allRows = mappings.map { m -> toMapWithBrand(m) }
        val uniqueValues = mapOf(
            "brands" to mappings.mapNotNull { it.carBrand }.distinct().sorted(),
            "carNames" to mappings.mapNotNull { it.carName }.distinct().sorted(),
            "fuels" to mappings.mapNotNull { it.fuel }.distinct().sorted(),
            "wds" to mappings.mapNotNull { it.wd }.distinct().sorted(),
            "shifts" to mappings.mapNotNull { it.shift }.distinct().sorted(),
            "ccs" to mappings.mapNotNull { it.cc?.toString() }.distinct().sorted(),
            "doors" to mappings.mapNotNull { it.door?.toString() }.distinct().sorted(),
            "grades" to mappings.mapNotNull { it.grade }.distinct().sorted(),
            "seats" to mappings.mapNotNull { it.seat?.toString() }.distinct().sorted(),
            "vehicleTypes" to mappings.mapNotNull { it.vehicleType }.distinct().sorted(),
            "ranks" to mappings.mapNotNull { it.rank }.distinct().sorted(),
            "colors" to mappings.mapNotNull { it.color }.distinct().sorted(),
            "driveTypes" to mappings.mapNotNull { it.driveType }.distinct().sorted()
        )
        return mapOf(
            "found" to true,
            "brand" to (firstMatch.carBrand ?: ""),
            "carName" to (firstMatch.carName ?: ""),
            "fuel" to (firstMatch.fuel ?: ""),
            "wd" to (firstMatch.wd ?: ""),
            "shift" to (firstMatch.shift ?: ""),
            "cc" to (firstMatch.cc?.toString() ?: ""),
            "door" to (firstMatch.door?.toString() ?: ""),
            "grade" to (firstMatch.grade ?: ""),
            "vehicleType" to (firstMatch.vehicleType ?: ""),
            "rank" to (firstMatch.rank ?: ""),
            "color" to (firstMatch.color ?: ""),
            "driveType" to (firstMatch.driveType ?: ""),
            "firstRow" to firstRowData,
            "allRows" to allRows,
            "uniqueValues" to uniqueValues,
            "mappings" to allRows
        )
    }

    fun findMatchingRowByChassis(
        chassis: String,
        brand: String?,
        carName: String?,
        fuel: String?,
        wd: String?,
        shift: String?,
        cc: String?,
        door: String?,
        grade: String?
    ): Map<String, Any?> {
        val allMappings = carBrandMappingRepository.findByChassis(chassis)
        if (allMappings.isEmpty()) return mapOf("found" to false, "match" to null as Any?)
        val match = allMappings.firstOrNull { m ->
            (brand == null || m.carBrand?.equals(brand, ignoreCase = true) == true) &&
            (carName == null || m.carName?.equals(carName, ignoreCase = true) == true) &&
            (fuel == null || m.fuel?.equals(fuel, ignoreCase = true) == true) &&
            (wd == null || m.wd?.equals(wd, ignoreCase = true) == true) &&
            (shift == null || m.shift?.equals(shift, ignoreCase = true) == true) &&
            (cc == null || m.cc?.toString() == cc) &&
            (door == null || m.door?.toString() == door) &&
            (grade == null || m.grade?.equals(grade, ignoreCase = true) == true)
        }
        val row = match ?: allMappings.first()
        val matchData = mapOf(
            "id" to (row.id ?: 0),
            "brand" to (row.carBrand ?: ""),
            "chassis" to (row.chassis ?: ""),
            "carName" to (row.carName ?: ""),
            "fuel" to (row.fuel ?: ""),
            "wd" to (row.wd ?: ""),
            "shift" to (row.shift ?: ""),
            "cc" to (row.cc?.toString() ?: ""),
            "door" to (row.door?.toString() ?: ""),
            "grade" to (row.grade ?: ""),
            "seat" to (row.seat?.toString() ?: ""),
            "vehicleType" to (row.vehicleType ?: ""),
            "rank" to (row.rank ?: ""),
            "color" to (row.color ?: ""),
            "driveType" to (row.driveType ?: "")
        )
        return mapOf("found" to true, "match" to matchData, "isFallback" to (match == null))
    }

    fun getAllDistinctChassis(): Map<String, Any> {
        val chassisList = carBrandMappingRepository.findDistinctChassisAll()
        return mapOf("success" to true, "chassisList" to chassisList)
    }

    fun getAllMappings(): Map<String, Any> {
        val mappings = carBrandMappingRepository.findAll()
        val mappingsData = mappings.map { toMap(it) }
        return mapOf("success" to true, "data" to mappingsData)
    }

    fun getAllMappingsByBrand(brandName: String): Map<String, Any> {
        val mappings = carBrandMappingRepository.findByCarBrand(brandName)
        val mappingsData = mappings.map { toMap(it) }
        return mapOf("success" to true, "data" to mappingsData)
    }

    @Transactional
    fun createMapping(request: Map<String, Any?>): Map<String, Any> {
        val carBrand = parseString(request["carBrand"])
            ?: throw IllegalArgumentException("carBrand is required")
        val chassisStr = request["chassis"]?.toString()
        val carNameStr = request["carName"]?.toString()
        val fuelStr = request["fuel"]?.toString()
        val wdStr = request["wd"]?.toString()
        val shiftStr = request["shift"]?.toString()
        val gradeStr = request["grade"]?.toString()
        val ccValue = parseCcOrDoor(request["cc"])
        val doorValue = parseCcOrDoor(request["door"])
        val seatValue = parseCcOrDoor(request["seat"])
        val mapping = CarBrandMapping(
            carBrand = carBrand,
            chassis = chassisStr?.takeIf { it.isNotBlank() },
            carName = carNameStr?.takeIf { it.isNotBlank() },
            fuel = fuelStr?.takeIf { it.isNotBlank() },
            wd = wdStr?.takeIf { it.isNotBlank() },
            shift = shiftStr?.takeIf { it.isNotBlank() },
            cc = ccValue,
            door = doorValue,
            seat = seatValue,
            grade = gradeStr?.takeIf { it.isNotBlank() }
        )
        val saved = carBrandMappingRepository.save(mapping)
        return mapOf(
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
                "seat" to (saved.seat ?: 0),
                "grade" to (saved.grade ?: ""),
                "vehicleType" to (saved.vehicleType ?: ""),
                "rank" to (saved.rank ?: ""),
                "color" to (saved.color ?: ""),
                "driveType" to (saved.driveType ?: "")
            )
        )
    }

    @Transactional
    fun updateMapping(id: Long, request: Map<String, Any?>): Map<String, Any> {
        val existing = carBrandMappingRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Mapping not found: $id")
        fun getStringValue(key: String): String? {
            val value = request[key]
            return when {
                value == null -> null
                value is String -> if (value.isBlank()) null else value
                else -> value.toString().takeIf { it.isNotBlank() }
            }
        }
        val carBrandValue = if (request.containsKey("carBrand")) getStringValue("carBrand") ?: existing.carBrand else existing.carBrand
        if (carBrandValue.isNullOrBlank()) throw IllegalArgumentException("Car Brand is required")
        val chassisValue = if (request.containsKey("chassis")) getStringValue("chassis") else existing.chassis
        val carNameValue = if (request.containsKey("carName")) getStringValue("carName") else existing.carName
        val fuelValue = if (request.containsKey("fuel")) getStringValue("fuel") else existing.fuel
        val wdValue = if (request.containsKey("wd")) getStringValue("wd") else existing.wd
        val shiftValue = if (request.containsKey("shift")) getStringValue("shift") else existing.shift
        val gradeValue = if (request.containsKey("grade")) getStringValue("grade") else existing.grade
        val vehicleTypeValue = if (request.containsKey("vehicleType")) getStringValue("vehicleType") else existing.vehicleType
        val rankValue = if (request.containsKey("rank")) getStringValue("rank") else existing.rank
        val colorValue = if (request.containsKey("color")) getStringValue("color") else existing.color
        val driveTypeValue = if (request.containsKey("driveType")) getStringValue("driveType") else existing.driveType
        val ccValue = if (request.containsKey("cc")) parseCcOrDoor(request["cc"]) else existing.cc
        val doorValue = if (request.containsKey("door")) parseCcOrDoor(request["door"]) else existing.door
        val seatValue = if (request.containsKey("seat")) parseCcOrDoor(request["seat"]) else existing.seat
        val createdAtValue = existing.createdAt ?: LocalDateTime.now()
        val updated = existing.copy(
            id = existing.id,
            carBrand = carBrandValue,
            chassis = chassisValue,
            carName = carNameValue,
            fuel = fuelValue,
            wd = wdValue,
            shift = shiftValue,
            cc = ccValue,
            door = doorValue,
            seat = seatValue,
            grade = gradeValue,
            vehicleType = vehicleTypeValue,
            rank = rankValue,
            color = colorValue,
            driveType = driveTypeValue,
            createdAt = createdAtValue,
            updatedAt = LocalDateTime.now()
        )
        val merged = entityManager.merge(updated) as CarBrandMapping
        entityManager.flush()
        return mapOf(
            "success" to true,
            "message" to "Mapping updated successfully",
            "data" to mapOf(
                "id" to merged.id,
                "carBrand" to (merged.carBrand ?: ""),
                "chassis" to (merged.chassis ?: ""),
                "carName" to (merged.carName ?: ""),
                "fuel" to (merged.fuel ?: ""),
                "wd" to (merged.wd ?: ""),
                "shift" to (merged.shift ?: ""),
                "cc" to (merged.cc ?: 0),
                "door" to (merged.door ?: 0),
                "seat" to (merged.seat ?: 0),
                "grade" to (merged.grade ?: ""),
                "vehicleType" to (merged.vehicleType ?: ""),
                "rank" to (merged.rank ?: ""),
                "color" to (merged.color ?: ""),
                "driveType" to (merged.driveType ?: "")
            )
        )
    }

    fun getMappingById(id: Long): Map<String, Any?>? {
        val mapping = carBrandMappingRepository.findById(id).orElse(null) ?: return null
        return mapOf(
            "success" to true,
            "data" to mapOf(
                "id" to (mapping.id ?: 0),
                "carBrand" to (mapping.carBrand ?: ""),
                "chassis" to (mapping.chassis ?: ""),
                "carName" to (mapping.carName ?: ""),
                "fuel" to (mapping.fuel ?: ""),
                "wd" to (mapping.wd ?: ""),
                "shift" to (mapping.shift ?: ""),
                "cc" to (mapping.cc ?: 0),
                "door" to (mapping.door ?: 0),
                "seat" to (mapping.seat ?: 0),
                "grade" to (mapping.grade ?: ""),
                "vehicleType" to (mapping.vehicleType ?: ""),
                "rank" to (mapping.rank ?: ""),
                "color" to (mapping.color ?: ""),
                "driveType" to (mapping.driveType ?: "")
            )
        )
    }

    fun deleteMapping(id: Long): Boolean {
        if (!carBrandMappingRepository.existsById(id)) return false
        carBrandMappingRepository.deleteById(id)
        return true
    }

    private fun toMap(m: CarBrandMapping): Map<String, Any> = mapOf(
        "id" to (m.id ?: 0),
        "carBrand" to (m.carBrand ?: ""),
        "chassis" to (m.chassis ?: ""),
        "carName" to (m.carName ?: ""),
        "fuel" to (m.fuel ?: ""),
        "wd" to (m.wd ?: ""),
        "shift" to (m.shift ?: ""),
        "cc" to (m.cc ?: 0),
        "door" to (m.door ?: 0),
        "seat" to (m.seat ?: 0),
        "grade" to (m.grade ?: ""),
        "vehicleType" to (m.vehicleType ?: ""),
        "rank" to (m.rank ?: ""),
        "color" to (m.color ?: ""),
        "driveType" to (m.driveType ?: "")
    )

    private fun toMapWithBrand(m: CarBrandMapping): Map<String, Any> = mapOf(
        "id" to (m.id ?: 0),
        "brand" to (m.carBrand ?: ""),
        "chassis" to (m.chassis ?: ""),
        "carName" to (m.carName ?: ""),
        "fuel" to (m.fuel ?: ""),
        "wd" to (m.wd ?: ""),
        "shift" to (m.shift ?: ""),
        "cc" to (m.cc?.toString() ?: ""),
        "door" to (m.door?.toString() ?: ""),
        "grade" to (m.grade ?: ""),
        "seat" to (m.seat?.toString() ?: ""),
        "vehicleType" to (m.vehicleType ?: ""),
        "rank" to (m.rank ?: ""),
        "color" to (m.color ?: ""),
        "driveType" to (m.driveType ?: "")
    )

    private fun parseString(value: Any?): String? = when {
        value == null -> null
        value is String -> if (value.isBlank()) null else value
        else -> value.toString().takeIf { it.isNotBlank() }
    }

    private fun parseCcOrDoor(value: Any?): Int? = when (value) {
        null -> null
        is Number -> value.toInt().takeIf { it > 0 }
        is String -> if (value.isBlank()) null else value.toIntOrNull()
        else -> null
    }
}
