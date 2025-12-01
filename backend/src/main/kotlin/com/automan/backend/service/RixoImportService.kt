package com.automan.backend.service

import com.automan.backend.model.RixoPrice
import com.automan.backend.repository.RixoPriceRepository
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class RixoImportService(
    private val rixoPriceRepository: RixoPriceRepository
) {
    
    fun importRixoPricesFromCsv(csvContent: String): ImportResult {
        try {
            val lines = csvContent.trim().split("\n")
            if (lines.isEmpty()) {
                return ImportResult(false, "CSV content is empty", 0, 0)
            }
            
            val header = lines[0].split(",")
            val dataLines = lines.drop(1)
            
            var successCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()
            
            // Clear existing data
            rixoPriceRepository.deleteAll()
            
            dataLines.forEachIndexed { index, line ->
                try {
                    val values = parseCsvLine(line)
                    if (values.size >= 6) {
                        val rixoPrice = RixoPrice(
                            auctionHouse = values[0].trim(),
                            shipmentSize = values[1].trim().takeIf { it.isNotEmpty() },
                            stockLocation = values[2].trim(),
                            rixoCompany = values[3].trim(),
                            venueId = values[4].trim().takeIf { it.isNotEmpty() },
                            rixoPrice = values[5].trim().takeIf { it.isNotEmpty() }
                        )
                        
                        rixoPriceRepository.save(rixoPrice)
                        successCount++
                    } else {
                        errors.add("Line ${index + 2}: Insufficient columns (expected 6, got ${values.size})")
                        errorCount++
                    }
                } catch (e: Exception) {
                    errors.add("Line ${index + 2}: ${e.message}")
                    errorCount++
                }
            }
            
            return ImportResult(
                success = true,
                message = "Import completed. Success: $successCount, Errors: $errorCount",
                successCount = successCount,
                errorCount = errorCount,
                errors = if (errors.isNotEmpty()) errors else null
            )
            
        } catch (e: Exception) {
            return ImportResult(false, "Import failed: ${e.message}", 0, 0)
        }
    }
    
    fun importRixoPricesFromFile(file: MultipartFile): ImportResult {
        try {
            val content = BufferedReader(InputStreamReader(file.inputStream)).use { it.readText() }
            return importRixoPricesFromCsv(content)
        } catch (e: Exception) {
            return ImportResult(false, "File import failed: ${e.message}", 0, 0)
        }
    }
    
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        current.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
    
    fun getAllRixoPrices(): List<RixoPrice> {
        return rixoPriceRepository.findAll()
    }
    
    fun getDistinctAuctionHouses(): List<String> {
        return rixoPriceRepository.findDistinctAuctionNames()
    }
    
    fun getDistinctStockLocations(): List<String> {
        return rixoPriceRepository.findDistinctStockLocations()
    }
    
    fun getDistinctRixoCompanies(): List<String> {
        return rixoPriceRepository.findDistinctRixoCompanies()
    }
    
    fun getDistinctRixoPrices(): List<String> {
        return rixoPriceRepository.findDistinctRixoPrices()
    }
    
    fun getRixoPricesByAuctionHouse(auctionHouse: String): List<RixoPrice> {
        return rixoPriceRepository.findByAuctionHouse(auctionHouse)
    }
    
    // New CRUD methods for inline mapping management
    fun saveRixoPrice(rixoPrice: RixoPrice): RixoPrice {
        return rixoPriceRepository.save(rixoPrice)
    }
    
    fun getRixoPriceById(id: Long): RixoPrice? {
        return rixoPriceRepository.findById(id).orElse(null)
    }
    
    fun deleteRixoPrice(id: Long) {
        rixoPriceRepository.deleteById(id)
    }
}

data class ImportResult(
    val success: Boolean,
    val message: String,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<String>? = null
)
