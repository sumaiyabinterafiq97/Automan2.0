package com.automan.backend.controller

import com.automan.backend.service.RixoImportService
import com.automan.backend.util.RixoPolFromStockLocation
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/rixo")
class RixoImportController(
    private val rixoImportService: RixoImportService
) {
    
    @GetMapping("/test")
    fun test(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf("message" to "RixoImportController is working", "status" to "ok"))
    }
    
    @PostMapping("/import/csv")
    fun importFromCsv(@RequestParam("file") file: MultipartFile): ResponseEntity<Map<String, Any>> {
        return try {
            val result = rixoImportService.importRixoPricesFromFile(file)
            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "message" to result.message,
                "successCount" to result.successCount,
                "errorCount" to result.errorCount,
                "errors" to (result.errors ?: emptyList<String>())
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Import failed: ${e.message}",
                "successCount" to 0,
                "errorCount" to 0,
                "errors" to listOf(e.message ?: "Unknown error")
            ))
        }
    }
    
    @PostMapping("/import/text")
    fun importFromText(@RequestBody request: Map<String, String>): ResponseEntity<Map<String, Any>> {
        return try {
            val csvContent = request["csvContent"] ?: throw IllegalArgumentException("csvContent is required")
            val result = rixoImportService.importRixoPricesFromCsv(csvContent)
            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "message" to result.message,
                "successCount" to result.successCount,
                "errorCount" to result.errorCount,
                "errors" to (result.errors ?: emptyList<String>())
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Import failed: ${e.message}",
                "successCount" to 0,
                "errorCount" to 0,
                "errors" to listOf(e.message ?: "Unknown error")
            ))
        }
    }
    
    @GetMapping("/prices")
    fun getAllRixoPrices(): ResponseEntity<Map<String, Any>> {
        return try {
            Logger.debug("getAllRixoPrices endpoint called")
            val priceData = rixoImportService.getAllRixoPricesAsMaps()
            Logger.debug("Found ${priceData.size} prices (jdbc)")

            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to priceData,
                "count" to priceData.size
            ))
        } catch (e: Exception) {
            Logger.error("ERROR in getAllRixoPrices: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "message" to "Failed to fetch prices: ${e.message}",
                "data" to emptyList<Any>(),
                "count" to 0,
                "error" to (e.stackTraceToString())
            ))
        }
    }

    /**
     * Paginated search for Supplier Map (supplier name / stock location / rixo company / all).
     */
    @GetMapping("/prices/page-search")
    fun searchSupplierMapPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "all") field: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(rixoImportService.searchSupplierMapPage(q, field, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }
    
    @GetMapping("/dropdowns/auction-names")
    fun getAuctionNames(): ResponseEntity<Map<String, Any>> {
        return try {
            val auctionHouses = rixoImportService.getDistinctAuctionHouses()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to auctionHouses
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to fetch auction names: ${e.message}",
                "data" to emptyList<String>()
            ))
        }
    }
    
    @GetMapping("/dropdowns/stock-locations")
    fun getStockLocations(): ResponseEntity<Map<String, Any>> {
        return try {
            val stockLocations = rixoImportService.getDistinctStockLocations()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to stockLocations
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to fetch stock locations: ${e.message}",
                "data" to emptyList<String>()
            ))
        }
    }
    
    @GetMapping("/dropdowns/rixo-companies")
    fun getRixoCompanies(): ResponseEntity<Map<String, Any>> {
        return try {
            val rixoCompanies = rixoImportService.getDistinctRixoCompanies()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to rixoCompanies
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to fetch rixo companies: ${e.message}",
                "data" to emptyList<String>()
            ))
        }
    }
    
    @GetMapping("/dropdowns/rixo-prices")
    fun getRixoPrices(): ResponseEntity<Map<String, Any>> {
        return try {
            val rixoPrices = rixoImportService.getDistinctRixoPrices()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to rixoPrices
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to fetch rixo prices: ${e.message}",
                "data" to emptyList<String>()
            ))
        }
    }
    
    @GetMapping("/prices/by-auction-house/{auctionHouse}")
    fun getPricesByAuctionHouse(@PathVariable auctionHouse: String): ResponseEntity<Map<String, Any>> {
        return try {
            val prices = rixoImportService.getRixoPricesByAuctionHouse(auctionHouse)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to prices,
                "count" to prices.size
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to fetch prices: ${e.message}",
                "data" to emptyList<Any>(),
                "count" to 0
            ))
        }
    }
    
    // New CRUD endpoints for inline mapping management
    @PostMapping("/mappings/add")
    fun addMapping(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            // Validate required fields
            val auctionHouse = request["auctionHouse"] as? String
            val stockLocation = (request["stockLocation"] as? String)?.trim()
            val rixoCompany = (request["rixoCompany"] as? String)?.trim()
            val venueId = (request["venueId"] as? String)?.trim()

            if (auctionHouse.isNullOrBlank()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "auctionHouse is required"
                ))
            }
            
            // Stock Location and Rixo Company are NOT NULL in database, so provide defaults if empty
            val finalStockLocation = stockLocation?.takeIf { it.isNotBlank() } ?: "-"
            val finalRixoCompany = rixoCompany?.takeIf { it.isNotBlank() } ?: "-"
            
            // Use service method that handles auction_house properly
            // Convert empty strings to null for nullable fields to avoid database constraint issues
            val result = rixoImportService.saveRixoPriceWithAuctionHouse(
                auctionHouse = auctionHouse.trim(),
                stockLocation = finalStockLocation,
                rixoCompany = finalRixoCompany,
                venueId = venueId?.takeIf { it.isNotBlank() }
            )

            val message = if (result.merged) {
                "Merged into existing supplier (values combined with ;)"
            } else {
                "Mapping added successfully"
            }

            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to message,
                "merged" to result.merged,
                "data" to result.price
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            var cause: Throwable? = e
            while (cause?.cause != null) { cause = cause.cause }
            val rootMessage = cause?.message ?: e.message
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to add mapping: ${e.message}",
                "error" to e.javaClass.simpleName,
                "rootCause" to (rootMessage?.take(500) ?: "")
            ))
        }
    }
    
    @PutMapping("/mappings/{id}")
    fun updateMapping(@PathVariable id: Long, @RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            val existingMapping = rixoImportService.getRixoPriceById(id)
            if (existingMapping == null) {
                return ResponseEntity.notFound().build()
            }
            
            // Preserve createdAt when copying - ensure it's never null
            val createdAtValue = existingMapping.createdAt ?: java.time.LocalDateTime.now()
            
            val newStock = (request["stockLocation"] as? String)?.trim() ?: existingMapping.stockLocation
            val updatedMapping = existingMapping.copy(
                id = existingMapping.id, // CRITICAL: Explicitly preserve ID
                auctionHouse = request["auctionHouse"] as? String ?: existingMapping.auctionHouse,
                stockLocation = newStock,
                rixoCompany = request["rixoCompany"] as? String ?: existingMapping.rixoCompany,
                venueId = request["venueId"] as? String ?: existingMapping.venueId,
                pol = RixoPolFromStockLocation.derivePol(newStock),
                createdAt = createdAtValue // Preserve createdAt (never null)
            )
            
            Logger.debug("[RIXO UPDATE] Original ID: ${existingMapping.id}, Updated ID: ${updatedMapping.id}")
            
            // Verify ID is preserved
            if (updatedMapping.id != existingMapping.id) {
                Logger.error("[RIXO UPDATE] ERROR: ID mismatch!")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Internal error: ID mismatch during update"
                ))
            }
            
            val savedMapping = rixoImportService.saveRixoPrice(updatedMapping)
            
            // Verify the saved entity has the same ID
            if (savedMapping.id != existingMapping.id) {
                Logger.error("[RIXO UPDATE] ERROR: Saved entity has different ID! Original: ${existingMapping.id}, Saved: ${savedMapping.id}")
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Error: Update created a new row instead of updating existing one"
                ))
            }
            
            Logger.debug("[RIXO UPDATE] Successfully updated existing mapping with ID: ${savedMapping.id}")
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping updated successfully",
                "data" to savedMapping
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to update mapping: ${e.message}"
            ))
        }
    }
    
    @DeleteMapping("/mappings/{id}")
    fun deleteMapping(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            rixoImportService.deleteRixoPrice(id)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping deleted successfully"
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to delete mapping: ${e.message}"
            ))
        }
    }
    
    @GetMapping("/mappings/by-auction/{auctionHouse}")
    fun getMappingsByAuction(@PathVariable auctionHouse: String): ResponseEntity<Map<String, Any>> {
        return try {
            val mappings = rixoImportService.getRixoPricesByAuctionHouse(auctionHouse)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to mappings,
                "count" to mappings.size
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to fetch mappings: ${e.message}",
                "data" to emptyList<Any>(),
                "count" to 0
            ))
        }
    }
}
