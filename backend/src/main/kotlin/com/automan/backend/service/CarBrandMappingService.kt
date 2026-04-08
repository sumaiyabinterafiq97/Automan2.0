package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Business logic for car brand mappings.
 * Controllers delegate here; this layer uses only the repository (and EntityManager for update merge).
 */
@Service
class CarBrandMappingService(
    private val carBrandMappingRepository: CarBrandMappingRepository
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
            "cc" to (firstRow.cc ?: ""),
            "door" to (firstRow.door ?: ""),
            "grade" to (firstRow.grade ?: ""),
            "seat" to (firstRow.seat ?: ""),
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
        val seatList = mappings.mapNotNull { it.seat?.takeIf { s -> s.isNotBlank() } }.distinct().sorted()
        return mapOf(
            "mappings" to mappings.map { toMap(it) },
            "firstRow" to firstRowData,
            "dropdowns" to mapOf(
                "chassis" to chassisList,
                "carName" to carNameList,
                "fuel" to fuelList,
                "wd" to wdList,
                "shift" to shiftList,
                "cc" to ccList,
                "door" to doorList,
                "grade" to gradeList,
                "seats" to seatList
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
            "cc" to (firstMatch.cc ?: ""),
            "door" to (firstMatch.door ?: ""),
            "grade" to (firstMatch.grade ?: ""),
            "seat" to (firstMatch.seat ?: ""),
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
            "cc" to (firstRow.cc ?: ""),
            "door" to (firstRow.door ?: ""),
            "grade" to (firstRow.grade ?: ""),
            "seat" to (firstRow.seat ?: ""),
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
            "cc" to (firstMatch.cc ?: ""),
            "door" to (firstMatch.door ?: ""),
            "grade" to (firstMatch.grade ?: ""),
            "seat" to (firstMatch.seat ?: ""),
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
            "ccs" to mappings.mapNotNull { it.cc?.takeIf { s -> s.isNotBlank() } }.distinct().sorted(),
            "doors" to mappings.mapNotNull { it.door?.takeIf { s -> s.isNotBlank() } }.distinct().sorted(),
            "grades" to mappings.mapNotNull { it.grade }.distinct().sorted(),
            "seats" to mappings.mapNotNull { it.seat?.takeIf { s -> s.isNotBlank() } }.distinct().sorted(),
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
            "cc" to (firstMatch.cc ?: ""),
            "door" to (firstMatch.door ?: ""),
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
            (cc == null || semicolonTokenMatches(m.cc, cc)) &&
            (door == null || semicolonTokenMatches(m.door, door)) &&
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
            "cc" to (row.cc ?: ""),
            "door" to (row.door ?: ""),
            "grade" to (row.grade ?: ""),
            "seat" to (row.seat ?: ""),
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
        val chassis = parseString(request["chassis"])
            ?: throw IllegalArgumentException("Chassis is required")
        val replaceExistingValues = parseBoolean(request["replaceExistingValues"])
        val sameChassisRows = carBrandMappingRepository.findByChassis(chassis)
        val mergedFromRequest = mergeIntoBase(
            base = sameChassisRows.firstOrNull(),
            request = request,
            chassis = chassis,
            replaceExistingValues = replaceExistingValues
        )

        val saved = if (sameChassisRows.isEmpty()) {
            carBrandMappingRepository.save(mergedFromRequest)
        } else {
            val canonical = mergedFromRequest.copy(id = sameChassisRows.first().id)
            val persisted = carBrandMappingRepository.save(canonical)
            val duplicateIds = sameChassisRows.drop(1).mapNotNull { it.id }
            if (duplicateIds.isNotEmpty()) {
                carBrandMappingRepository.deleteAllById(duplicateIds)
            }
            persisted
        }
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
                "cc" to (saved.cc ?: ""),
                "door" to (saved.door ?: ""),
                "seat" to (saved.seat ?: ""),
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
        val requestedChassis = if (request.containsKey("chassis")) parseString(request["chassis"]) else existing.chassis
        val finalChassis = requestedChassis ?: throw IllegalArgumentException("Chassis is required")

        val sameChassisRows = carBrandMappingRepository.findByChassis(finalChassis)
        val baseRow = sameChassisRows.firstOrNull { it.id == id } ?: sameChassisRows.firstOrNull() ?: existing
        val mergedData = mergeIntoBase(
            base = baseRow,
            request = request,
            chassis = finalChassis,
            replaceExistingValues = true
        ).copy(id = baseRow.id)
        val merged = carBrandMappingRepository.save(mergedData)

        val duplicateIds = (sameChassisRows.mapNotNull { it.id } + existing.id)
            .filterNotNull()
            .distinct()
            .filter { it != merged.id }
        if (duplicateIds.isNotEmpty()) {
            carBrandMappingRepository.deleteAllById(duplicateIds)
        }

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
                "cc" to (merged.cc ?: ""),
                "door" to (merged.door ?: ""),
                "seat" to (merged.seat ?: ""),
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
                "cc" to (mapping.cc ?: ""),
                "door" to (mapping.door ?: ""),
                "seat" to (mapping.seat ?: ""),
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
        "cc" to (m.cc ?: ""),
        "door" to (m.door ?: ""),
        "seat" to (m.seat ?: ""),
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
        "cc" to (m.cc ?: ""),
        "door" to (m.door ?: ""),
        "grade" to (m.grade ?: ""),
        "seat" to (m.seat ?: ""),
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

    private fun mergeIntoBase(
        base: CarBrandMapping?,
        request: Map<String, Any?>,
        chassis: String,
        replaceExistingValues: Boolean
    ): CarBrandMapping {
        val now = LocalDateTime.now()

        val requestBrand = parseString(request["carBrand"])
        val requestCarName = parseString(request["carName"])
        val requestFuel = parseString(request["fuel"])
        val requestWd = parseString(request["wd"])
        val requestShift = parseString(request["shift"])
        val requestGrade = parseString(request["grade"])
        val requestVehicleType = parseString(request["vehicleType"])
        val requestRank = parseString(request["rank"])
        val requestColor = parseString(request["color"])
        val requestDriveType = parseString(request["driveType"])
        val requestCc = if (request.containsKey("cc")) parseString(request["cc"]) else null
        val requestDoor = if (request.containsKey("door")) parseString(request["door"]) else null
        val requestSeat = if (request.containsKey("seat")) parseString(request["seat"]) else null

        val carBrand = mergeField(base?.carBrand, requestBrand, replaceExistingValues, request.containsKey("carBrand"))
            ?: throw IllegalArgumentException("carBrand is required")

        return CarBrandMapping(
            id = base?.id,
            carBrand = carBrand,
            chassis = chassis,
            carName = mergeField(base?.carName, requestCarName, replaceExistingValues, request.containsKey("carName")),
            fuel = mergeField(base?.fuel, requestFuel, replaceExistingValues, request.containsKey("fuel")),
            wd = mergeField(base?.wd, requestWd, replaceExistingValues, request.containsKey("wd")),
            shift = mergeField(base?.shift, requestShift, replaceExistingValues, request.containsKey("shift")),
            cc = mergeField(base?.cc, requestCc, replaceExistingValues, request.containsKey("cc")),
            door = mergeField(base?.door, requestDoor, replaceExistingValues, request.containsKey("door")),
            seat = mergeField(base?.seat, requestSeat, replaceExistingValues, request.containsKey("seat")),
            grade = mergeField(base?.grade, requestGrade, replaceExistingValues, request.containsKey("grade")),
            vehicleType = mergeField(base?.vehicleType, requestVehicleType, replaceExistingValues, request.containsKey("vehicleType")),
            rank = mergeField(base?.rank, requestRank, replaceExistingValues, request.containsKey("rank")),
            color = mergeField(base?.color, requestColor, replaceExistingValues, request.containsKey("color")),
            driveType = mergeField(base?.driveType, requestDriveType, replaceExistingValues, request.containsKey("driveType")),
            createdAt = base?.createdAt ?: now,
            updatedAt = now
        )
    }

    private fun mergeField(existing: String?, incoming: String?, replaceMode: Boolean, incomingProvided: Boolean): String? {
        if (replaceMode && incomingProvided) return normalizeSemicolon(incoming)
        return mergeSemicolon(existing, incoming)
    }

    private fun mergeSemicolon(existing: String?, incoming: String?): String? {
        val tokens = linkedSetOf<String>()
        tokenizeSemicolon(existing).forEach { token ->
            if (tokens.none { it.equals(token, ignoreCase = true) }) tokens.add(token)
        }
        tokenizeSemicolon(incoming).forEach { token ->
            if (tokens.none { it.equals(token, ignoreCase = true) }) tokens.add(token)
        }
        return tokens.joinToString(";").takeIf { it.isNotBlank() }
    }

    private fun normalizeSemicolon(value: String?): String? {
        val tokens = linkedSetOf<String>()
        tokenizeSemicolon(value).forEach { token ->
            if (tokens.none { it.equals(token, ignoreCase = true) }) tokens.add(token)
        }
        return tokens.joinToString(";").takeIf { it.isNotBlank() }
    }

    private fun tokenizeSemicolon(value: String?): List<String> =
        value
            ?.split(";")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    /** True if [selected] matches the full stored value or a single semicolon token in [stored]. */
    private fun semicolonTokenMatches(stored: String?, selected: String?): Boolean {
        if (selected.isNullOrBlank()) return true
        if (stored.isNullOrBlank()) return false
        if (stored.equals(selected, ignoreCase = true)) return true
        return tokenizeSemicolon(stored).any { it.equals(selected, ignoreCase = true) }
    }

    private fun parseBoolean(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.equals("true", ignoreCase = true)
        is Number -> value.toInt() != 0
        else -> false
    }
}
