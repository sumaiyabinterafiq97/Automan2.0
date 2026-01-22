package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.service.PurchaseService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/upload")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8081", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class FileUploadController(private val purchaseService: PurchaseService) {
    
    @PostMapping("/excel")
    fun uploadExcel(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportResponse> {
        try {
            Logger.debug("FileUploadController: Received file: ${file.originalFilename}")
            Logger.debug("FileUploadController: File size: ${file.size}")
            Logger.debug("FileUploadController: About to call purchaseService.importPurchases")
            val importResponse = purchaseService.importPurchases(file)
            Logger.debug("FileUploadController: ${importResponse.message}")
            return ResponseEntity.ok(importResponse)
        } catch (e: Exception) {
            Logger.error("FileUploadController: Error during import: ${e.message}", e)
            return ResponseEntity.status(500).body(
                ImportResponse(
                    success = false,
                    message = "Import failed: ${e.message}",
                    importedCount = 0,
                    duplicateCount = 0,
                    errorCount = 1,
                    totalProcessed = 0,
                    errorDetails = listOf("FileUploadController error: ${e.message}")
                )
            )
        }
    }
    
    @PostMapping("/simple")
    fun uploadSimple(@RequestParam("test") test: String): ResponseEntity<String> {
        return ResponseEntity.ok("Simple upload working with test: $test")
    }
    
    @PostMapping("/test")
    fun testUpload(): ResponseEntity<String> {
        return ResponseEntity.ok("Upload test endpoint working!")
    }
}
