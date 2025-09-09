package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.service.PurchaseService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/purchases")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090"])
class PurchaseController(private val purchaseService: PurchaseService) {
    
    @GetMapping
    fun getAllPurchases(): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.getAllPurchases()
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/search")
    fun searchPurchases(@RequestParam query: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.searchPurchases(query)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/sort")
    fun sortPurchases(@RequestParam field: String, @RequestParam order: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.sortPurchases(field, order)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/{id}")
    fun getPurchaseById(@PathVariable id: Long): ResponseEntity<Purchase> {
        val purchase = purchaseService.getPurchaseById(id)
        return if (purchase != null) {
            ResponseEntity.ok(purchase)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @PostMapping
    fun createPurchase(@RequestBody purchase: Purchase): ResponseEntity<Purchase> {
        val createdPurchase = purchaseService.createPurchase(purchase)
        return ResponseEntity.ok(createdPurchase)
    }
    
    @PutMapping("/{id}")
    fun updatePurchase(@PathVariable id: Long, @RequestBody purchase: Purchase): ResponseEntity<Purchase> {
        val updatedPurchase = purchaseService.updatePurchase(id, purchase)
        return if (updatedPurchase != null) {
            ResponseEntity.ok(updatedPurchase)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @DeleteMapping("/{id}")
    fun deletePurchase(@PathVariable id: Long): ResponseEntity<Void> {
        val deleted = purchaseService.deletePurchase(id)
        return if (deleted) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @PostMapping("/import")
    fun importPurchases(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportResponse> {
        try {
            println("Controller: Received file: ${file.originalFilename}")
            println("Controller: File size: ${file.size}")
            val importResponse = purchaseService.importPurchases(file)
            println("Controller: ${importResponse.message}")
            return ResponseEntity.ok(importResponse)
        } catch (e: Exception) {
            println("Controller: Error during import: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.status(500).body(
                ImportResponse(
                    success = false,
                    message = "Import failed: ${e.message}",
                    importedCount = 0,
                    duplicateCount = 0,
                    errorCount = 1,
                    totalProcessed = 0,
                    errorDetails = listOf("Controller error: ${e.message}")
                )
            )
        }
    }
    
    @PostMapping("/rixo-pdf")
    fun generateRixoPdf(@RequestBody request: Map<String, Any>): ResponseEntity<ByteArray> {
        try {
            val idsRaw = request["ids"]
            val invoiceDataRaw = request["invoiceData"] as? Map<String, Any>
            println("Controller: Raw request body: $request")
            println("Controller: Raw ids: $idsRaw (type: ${idsRaw?.javaClass?.simpleName})")
            println("Controller: Raw invoice data: $invoiceDataRaw")
            
            val selectedIds = when (idsRaw) {
                is List<*> -> {
                    println("Controller: Processing List with ${idsRaw.size} items")
                    idsRaw.mapNotNull { item ->
                        println("Controller: Processing item: $item (type: ${item?.javaClass?.simpleName})")
                        when (item) {
                            is Number -> {
                                val longValue = item.toLong()
                                println("Controller: Converted Number $item to Long $longValue")
                                longValue
                            }
                            is String -> {
                                val longValue = item.toLongOrNull()
                                println("Controller: Converted String '$item' to Long $longValue")
                                longValue
                            }
                            else -> {
                                println("Controller: Unknown item type: ${item?.javaClass?.simpleName}")
                                null
                            }
                        }
                    }
                }
                else -> {
                    println("Controller: idsRaw is not a List, it's: ${idsRaw?.javaClass?.simpleName}")
                    emptyList()
                }
            }
            
            // Process invoice data
            val invoiceData = invoiceDataRaw?.mapValues { (_, value) -> 
                value?.toString() ?: ""
            } ?: emptyMap()
            
            println("Controller: Final selectedIds: $selectedIds (size: ${selectedIds.size})")
            println("Controller: Invoice data: $invoiceData")
            println("Controller: Generating Rixo PDF for ${selectedIds.size} purchases")
            
            val pdfBytes = purchaseService.generateRixoPdf(selectedIds, invoiceData)
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"rixo-purchases.pdf\"")
                .body(pdfBytes)
        } catch (e: Exception) {
            println("Controller: Error generating Rixo PDF: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.status(500).build()
        }
    }
    
    @PostMapping("/test")
    fun testEndpoint(): ResponseEntity<String> {
        return ResponseEntity.ok("Test endpoint working!")
    }
    
    @GetMapping("/test-import-response")
    fun testImportResponse(): ResponseEntity<ImportResponse> {
        return ResponseEntity.ok(
            ImportResponse(
                success = true,
                message = "Test import response",
                importedCount = 5,
                duplicateCount = 2,
                errorCount = 0,
                totalProcessed = 7,
                importedPurchases = emptyList(),
                duplicateDetails = listOf("Test duplicate 1", "Test duplicate 2"),
                errorDetails = emptyList()
            )
        )
    }
    
    @GetMapping("/filter/car-name")
    fun filterByCarName(@RequestParam carName: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.filterByCarName(carName)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/filter/auction-name")
    fun filterByAuctionName(@RequestParam auctionName: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.filterByAuctionName(auctionName)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/filter/client-name")
    fun filterByClientName(@RequestParam clientName: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.filterByClientName(clientName)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/filter/date")
    fun filterByDate(@RequestParam date: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.filterByDate(date)
        return ResponseEntity.ok(purchases)
    }
}
