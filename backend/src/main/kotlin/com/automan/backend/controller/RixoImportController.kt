package com.automan.backend.controller

import com.automan.backend.service.RixoImportService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/rixo")
class RixoImportController(
    private val rixoImportService: RixoImportService
) {
    
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
            val prices = rixoImportService.getAllRixoPrices()
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
}
