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
