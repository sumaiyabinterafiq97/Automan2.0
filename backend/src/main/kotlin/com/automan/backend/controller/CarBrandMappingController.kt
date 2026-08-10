package com.automan.backend.controller

import com.automan.backend.service.CarBrandMappingService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

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
    private val carBrandMappingService: CarBrandMappingService
) {

    @GetMapping("/brand/{brandName}")
    fun getMappingsByBrand(@PathVariable brandName: String): ResponseEntity<Map<String, Any?>> {
        val result = carBrandMappingService.getMappingsByBrand(brandName)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/brand/{brandName}/match")
    fun getMappingByBrandAndChassisOrCarName(
        @PathVariable brandName: String,
        @RequestParam(required = false) chassis: String?,
        @RequestParam(required = false) carName: String?
    ): ResponseEntity<Map<String, Any?>> {
        val result = carBrandMappingService.getMappingByBrandAndChassisOrCarName(brandName, chassis, carName)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/brand/{brandName}/car-name/{carName}")
    fun getMappingsByBrandAndCarName(
        @PathVariable brandName: String,
        @PathVariable carName: String
    ): ResponseEntity<Map<String, Any?>> {
        val result = carBrandMappingService.getMappingsByBrandAndCarName(brandName, carName)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/chassis/{chassis}")
    fun getMappingByChassis(@PathVariable chassis: String): ResponseEntity<Map<String, Any?>> {
        val result = carBrandMappingService.getMappingByChassis(chassis)
        return ResponseEntity.ok(result)
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
            val result = carBrandMappingService.findMatchingRowByChassis(
                chassis, brand, carName, fuel, wd, shift, cc, door, grade
            )
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            Logger.error("Failed to find matching row: ${e.message}", e)
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
            val result = carBrandMappingService.getAllDistinctChassis()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            Logger.error("Failed to load chassis list: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to load chassis list: ${e.message}",
                "chassisList" to emptyList<String>()
            ))
        }
    }

    /**
     * Look up the recycle fee for a specific production date against a chassis prefix.
     * Query param [productionDate] must be in MM/YYYY or YYYY-MM format.
     * Returns { found: true, fee: "12490" } or { found: false, fee: "" }.
     */
    @GetMapping("/chassis/{chassis}/recycle-fee")
    fun getRecycleFeeForProductionDate(
        @PathVariable chassis: String,
        @RequestParam productionDate: String
    ): ResponseEntity<Map<String, Any?>> {
        return try {
            // Extract prefix (everything before the first hyphen, or the full string if no hyphen)
            val chassisPrefix = chassis.substringBefore("-").trim()
            val fee = carBrandMappingService.getRecycleFeeForProductionDate(chassisPrefix, productionDate)
            if (fee != null) {
                ResponseEntity.ok(mapOf("found" to true, "fee" to fee))
            } else {
                ResponseEntity.ok(mapOf("found" to false, "fee" to ""))
            }
        } catch (e: Exception) {
            Logger.error("Failed to look up recycle fee: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf(
                "found" to false,
                "fee" to "",
                "message" to "Error: ${e.message}"
            ))
        }
    }

    @GetMapping("/chassis/{chassis}/manufacture-year")
    fun getManufactureYearForChassisNumber(
        @PathVariable chassis: String,
        @RequestParam chassisNumber: String
    ): ResponseEntity<Map<String, Any?>> {
        return try {
            val chassisPrefix = chassis.substringBefore("-").trim()
            val year = carBrandMappingService.getManufactureYearForChassisNumber(chassisPrefix, chassisNumber)
            if (year != null) {
                ResponseEntity.ok(mapOf("found" to true, "manufactureYear" to year))
            } else {
                ResponseEntity.ok(mapOf("found" to false, "manufactureYear" to ""))
            }
        } catch (e: Exception) {
            Logger.error("Failed to look up manufacture year: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf(
                "found" to false,
                "manufactureYear" to "",
                "message" to "Error: ${e.message}"
            ))
        }
    }

    @GetMapping("/car-names/distinct")
    fun getAllDistinctCarNames(): ResponseEntity<List<String>> {
        return try {
            ResponseEntity.ok(carBrandMappingService.listAllDistinctCarNames())
        } catch (e: Exception) {
            Logger.error("Failed to load car names list: ${e.message}", e)
            ResponseEntity.status(500).body(emptyList())
        }
    }

    @GetMapping("/mappings")
    fun getAllMappings(): ResponseEntity<Map<String, Any>> {
        return try {
            val result = carBrandMappingService.getAllMappings()
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            Logger.error("Failed to load mappings: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to load mappings: ${e.message}",
                "data" to emptyList<Map<String, Any>>()
            ))
        }
    }

    /**
     * Paginated browse for Car Brands Map (no search text). Prefer this over [getAllMappings] for UI.
     */
    @GetMapping("/mappings/page")
    fun listMappingsPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(carBrandMappingService.listMappingsPage(page, size, sort, order))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /**
     * Paginated search for Car Brands Map (chassis / car brand / car name / all).
     */
    @GetMapping("/mappings/page-search")
    fun searchMappingsPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "all") field: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(carBrandMappingService.searchMappingsPage(q, field, page, size, sort, order))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/brand/{brandName}/mappings")
    fun getAllMappingsByBrand(@PathVariable brandName: String): ResponseEntity<Map<String, Any>> {
        return try {
            val result = carBrandMappingService.getAllMappingsByBrand(brandName)
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            Logger.error("Failed to load mappings: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to load mappings: ${e.message}",
                "data" to emptyList<Map<String, Any>>()
            ))
        }
    }

    @PostMapping("/mappings")
    fun createMapping(@RequestBody request: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val result = carBrandMappingService.createMapping(request)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            Logger.error("Error creating mapping: ${e.message}", e)
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to create mapping: ${e.message}"))
        }
    }

    @PutMapping("/mappings/{id}")
    fun updateMapping(
        @PathVariable id: Long,
        @RequestBody request: Map<String, Any?>
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val result = carBrandMappingService.updateMapping(id, request)
            ResponseEntity.ok(result)
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            Logger.error("Error updating mapping: ${e.message}", e)
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to update mapping: ${e.message}",
                "error" to e.javaClass.simpleName
            ))
        }
    }

    @GetMapping("/mappings/{id}")
    fun getMappingById(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        val result = carBrandMappingService.getMappingById(id)
            ?: return ResponseEntity.notFound().build()
        return try {
            ResponseEntity.ok(result)
        } catch (e: Exception) {
            Logger.error("Failed to load mapping: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to load mapping: ${e.message}",
                "data" to null
            ))
        }
    }

    @DeleteMapping("/mappings/{id}")
    fun deleteMapping(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            if (!carBrandMappingService.deleteMapping(id)) {
                return ResponseEntity.notFound().build()
            }
            ResponseEntity.ok(mapOf("success" to true, "message" to "Mapping deleted successfully"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Failed to delete mapping: ${e.message}"))
        }
    }
}
