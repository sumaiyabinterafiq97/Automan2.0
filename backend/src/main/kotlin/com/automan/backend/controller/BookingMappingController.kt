package com.automan.backend.controller

import com.automan.backend.model.BookingMapping
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class ApiResponse<T>(val success: Boolean, val data: T? = null, val message: String? = null)

@RestController
@RequestMapping(value = ["/booking/mappings", "/api/booking/mappings"])
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
class BookingMappingController(
    private val repo: BookingMappingRepository
) {
    @GetMapping
    fun getAll(): ResponseEntity<ApiResponse<List<BookingMapping>>> {
        return try {
            Logger.debug("BookingMappingController.getAll() called")
            val items = repo.findAll()
            Logger.debug("Found ${items.size} booking mappings")
            ResponseEntity.ok(ApiResponse(true, items))
        } catch (e: Exception) {
            Logger.error("Error in BookingMappingController.getAll(): ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }
    
    @GetMapping("/by-country/{country}")
    fun byCountry(@PathVariable country: String): ResponseEntity<ApiResponse<List<BookingMapping>>> {
        return try {
            Logger.debug("BookingMappingController.byCountry called with country: $country")
            val items = repo.findByCountryIgnoreCase(country)
            Logger.debug("Found ${items.size} mappings for country: $country")
            ResponseEntity.ok(ApiResponse(true, items))
        } catch (e: Exception) {
            Logger.error("Error in BookingMappingController.byCountry: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }

    /** Returns list of POL (Port of Loading) strings for a stock location from booking_mappings. Uses any row that has this stock_location (canonical STOCK_LOCATION_POL or client-specific rows). Comma-separated pols are split into separate items. */
    @GetMapping("/pols-by-stock-location")
    fun polsByStockLocation(@RequestParam stockLocation: String): ResponseEntity<ApiResponse<List<String>>> {
        return try {
            if (stockLocation.isBlank()) {
                return ResponseEntity.ok(ApiResponse(true, emptyList()))
            }
            val mappings = repo.findByStockLocationIgnoreCase(stockLocation.trim())
            val pols = mappings
                .flatMap { m -> (m.pols?.split(',') ?: emptyList()).map { it.trim() }.filter { it.isNotEmpty() } }
                .distinct()
            Logger.debug("POLs for stock location '$stockLocation': $pols")
            ResponseEntity.ok(ApiResponse(true, pols))
        } catch (e: Exception) {
            Logger.error("Error in polsByStockLocation: ${e.message}")
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }

    @PostMapping("/add")
    fun add(@RequestBody payload: BookingMapping): ResponseEntity<ApiResponse<BookingMapping>> {
        val saved = repo.save(payload.copy(id = 0))
        return ResponseEntity.ok(ApiResponse(true, saved))
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody payload: BookingMapping): ResponseEntity<ApiResponse<BookingMapping>> {
        val existing = repo.findById(id)
        if (existing.isEmpty) return ResponseEntity.ok(ApiResponse(false, message = "Not found"))
        val saved = repo.save(payload.copy(id = id))
        return ResponseEntity.ok(ApiResponse(true, saved))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<ApiResponse<Unit>> {
        return try {
            repo.deleteById(id)
            ResponseEntity.ok(ApiResponse(true))
        } catch (e: Exception) {
            ResponseEntity.ok(ApiResponse(false, message = e.message))
        }
    }
}


