package com.automan.backend.controller

import com.automan.backend.model.BookingMapping
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.service.BookingMappingService
import com.automan.backend.service.MasterMenuService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    /** True when new values were merged into an existing row (same consignee name). */
    val merged: Boolean? = null,
)

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
    private val repo: BookingMappingRepository,
    private val bookingMappingService: BookingMappingService,
    private val masterMenuService: MasterMenuService
) {
    /**
     * Paginated browse for Consignee Map (no search text). Prefer this over [getAll] for UI.
     */
    @GetMapping("/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(bookingMappingService.listConsigneeMapPage(page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /**
     * Paginated search for Consignee Map (consignee name / country / all).
     */
    @GetMapping("/page-search")
    fun pageSearch(
        @RequestParam q: String,
        @RequestParam(defaultValue = "all") field: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(bookingMappingService.searchConsigneeMapPage(q, field, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

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

    @GetMapping("/consignee-names")
    fun getDistinctConsigneeNames(): ResponseEntity<ApiResponse<List<String>>> {
        return try {
            val names = bookingMappingService.getDistinctConsigneeNames()
            ResponseEntity.ok(ApiResponse(true, names))
        } catch (e: Exception) {
            Logger.error("Error in BookingMappingController.getDistinctConsigneeNames: ${e.message}")
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }

    @GetMapping("/notify-parties")
    fun getDistinctNotifyParties(): ResponseEntity<ApiResponse<List<String>>> {
        return try {
            val parties = bookingMappingService.getDistinctNotifyParties()
            ResponseEntity.ok(ApiResponse(true, parties))
        } catch (e: Exception) {
            Logger.error("Error in BookingMappingController.getDistinctNotifyParties: ${e.message}")
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }
    
    @GetMapping("/by-country/{country}")
    fun byCountry(@PathVariable country: String): ResponseEntity<ApiResponse<List<BookingMapping>>> {
        return try {
            Logger.debug("BookingMappingController.byCountry called with country: $country")
            val token = country.trim()
            // booking_mappings.country / pod can be stored as comma-separated or semicolon-separated token lists.
            // We need to match the requested country token against any of the stored tokens.
            val items = repo.findAll().filter { m ->
                val raw = (m.country ?: "").trim()
                if (raw.isEmpty()) return@filter false
                val tokens = raw.split(',', ';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                tokens.any { it.equals(token, ignoreCase = true) }
            }
            Logger.debug("Found ${items.size} mappings for country token: '$token'")
            ResponseEntity.ok(ApiResponse(true, items))
        } catch (e: Exception) {
            Logger.error("Error in BookingMappingController.byCountry: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }

    /** POL list from master_menu (`pol` field); booking_mappings no longer stores per-stock POL rows. */
    @GetMapping("/pols-by-stock-location")
    fun polsByStockLocation(@RequestParam stockLocation: String): ResponseEntity<ApiResponse<List<String>>> {
        return try {
            if (stockLocation.isBlank()) {
                return ResponseEntity.ok(ApiResponse(true, emptyList()))
            }
            val pols = masterMenuService.getValues("pol")
            Logger.debug("POLs (master_menu) for request stock location '$stockLocation': $pols")
            ResponseEntity.ok(ApiResponse(true, pols))
        } catch (e: Exception) {
            Logger.error("Error in polsByStockLocation: ${e.message}")
            ResponseEntity.status(500).body(ApiResponse(false, null, "Error: ${e.message}"))
        }
    }

    @PostMapping("/add")
    fun add(@RequestBody payload: BookingMapping): ResponseEntity<ApiResponse<BookingMapping>> {
        return try {
            val result = bookingMappingService.add(payload)
            val msg =
                if (result.mergedIntoExisting) "Values merged into existing consignee"
                else "Created"
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = result.mapping,
                    message = msg,
                    merged = result.mergedIntoExisting,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse(false, message = e.message))
        }
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody payload: BookingMapping): ResponseEntity<ApiResponse<BookingMapping>> {
        return try {
            val result = bookingMappingService.update(id, payload)
            val msg =
                if (result.mergedIntoExisting) "Merged into existing consignee; duplicate row removed"
                else "Updated"
            ResponseEntity.ok(
                ApiResponse(
                    success = true,
                    data = result.mapping,
                    message = msg,
                    merged = result.mergedIntoExisting,
                ),
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.ok(ApiResponse(false, message = "Not found"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(ApiResponse(false, message = e.message))
        }
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


