package com.automan.backend.controller

import com.automan.backend.service.RixoImportService
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
            println("DEBUG: getAllRixoPrices endpoint called")
            val prices = rixoImportService.getAllRixoPrices()
            println("DEBUG: Found ${prices.size} prices")
            
            // Convert to map to avoid serialization issues
            val priceData = prices.map { price ->
                mapOf(
                    "id" to price.id,
                    "auctionHouse" to price.auctionHouse,
                    "shipmentSize" to price.shipmentSize,
                    "stockLocation" to price.stockLocation,
                    "rixoCompany" to price.rixoCompany,
                    "venueId" to price.venueId,
                    "rixoPrice" to price.rixoPrice
                )
            }
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to priceData,
                "count" to prices.size
            ))
        } catch (e: Exception) {
            println("ERROR in getAllRixoPrices: ${e.message}")
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
            val stockLocation = request["stockLocation"] as? String
            val rixoCompany = request["rixoCompany"] as? String
            
            if (auctionHouse.isNullOrBlank()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "auctionHouse is required"
                ))
            }
            
            if (stockLocation.isNullOrBlank()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "stockLocation is required"
                ))
            }
            
            if (rixoCompany.isNullOrBlank()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "rixoCompany is required"
                ))
            }
            
            val rixoPrice = com.automan.backend.model.RixoPrice.create(
                auctionHouse = auctionHouse.trim(),
                shipmentSize = (request["vehicleType"] as? String)?.trim(),
                stockLocation = stockLocation.trim(),
                rixoCompany = rixoCompany.trim(),
                rixoPrice = (request["rixoPrice"] as? String)?.trim(),
                venueId = (request["venueId"] as? String)?.trim()
            )
            
            val savedMapping = rixoImportService.saveRixoPrice(rixoPrice)
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping added successfully",
                "data" to savedMapping
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to "Failed to add mapping: ${e.message}",
                "error" to e.javaClass.simpleName
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
            
            val updatedMapping = existingMapping.copy(
                shipmentSize = request["vehicleType"] as? String ?: existingMapping.shipmentSize,
                stockLocation = request["stockLocation"] as? String ?: existingMapping.stockLocation,
                rixoCompany = request["rixoCompany"] as? String ?: existingMapping.rixoCompany,
                rixoPrice = request["rixoPrice"] as? String ?: existingMapping.rixoPrice,
                venueId = request["venueId"] as? String ?: existingMapping.venueId
            )
            
            val savedMapping = rixoImportService.saveRixoPrice(updatedMapping)
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Mapping updated successfully",
                "data" to savedMapping
            ))
        } catch (e: Exception) {
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
