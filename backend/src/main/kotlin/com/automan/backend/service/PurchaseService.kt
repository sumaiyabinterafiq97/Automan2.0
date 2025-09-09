package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val pdfService: PdfService
) {
    
    fun getAllPurchases(): List<Purchase> {
        return purchaseRepository.findAll()
    }
    
    fun getPurchaseById(id: Long): Purchase? {
        return purchaseRepository.findById(id).orElse(null)
    }
    
    @Transactional
    fun createPurchase(purchase: Purchase): Purchase {
        // Check for duplicate before creating
        val existingPurchases = purchaseRepository.findAllByLotNumberAndChassis(
            purchase.lotNumber,
            purchase.chassis
        )
        
        if (existingPurchases.isNotEmpty()) {
            val existingPurchase = existingPurchases.first()
            throw IllegalArgumentException("⚠️ Duplicate found: A purchase with Lot ${purchase.lotNumber} and Chassis ${purchase.chassis} (${existingPurchase.carName}) already exists.")
        }
        
        return purchaseRepository.save(purchase)
    }
    
    @Transactional
    fun updatePurchase(id: Long, purchase: Purchase): Purchase? {
        val existingPurchase = purchaseRepository.findById(id).orElse(null)
        if (existingPurchase != null) {
            // Check for duplicate before updating (excluding the current record being updated)
            val duplicateCheck = purchaseRepository.findAllByLotNumberAndChassis(
                purchase.lotNumber,
                purchase.chassis
            ).find { it.id != id }
            
            if (duplicateCheck != null) {
                throw IllegalArgumentException("⚠️ Duplicate found: A purchase with Lot ${purchase.lotNumber} and Chassis ${purchase.chassis} (${duplicateCheck.carName}) already exists.")
            }
            
            val updatedPurchase = purchase.copy(id = id)
            return purchaseRepository.save(updatedPurchase)
        }
        return null
    }
    
    @Transactional
    fun deletePurchase(id: Long): Boolean {
        return if (purchaseRepository.existsById(id)) {
            purchaseRepository.deleteById(id)
            true
        } else {
            false
        }
    }
    
    fun searchPurchases(searchTerm: String): List<Purchase> {
        return if (searchTerm.isBlank()) {
            getAllPurchases()
        } else {
            purchaseRepository.searchPurchases(searchTerm)
        }
    }
    
    fun sortPurchases(field: String, order: String): List<Purchase> {
        val allPurchases = getAllPurchases()
        return when (field) {
            "date" -> if (order == "asc") allPurchases.sortedBy { it.date } else allPurchases.sortedByDescending { it.date }
            "carName" -> if (order == "asc") allPurchases.sortedBy { it.carName } else allPurchases.sortedByDescending { it.carName }
            "auctionName" -> if (order == "asc") allPurchases.sortedBy { it.auctionName } else allPurchases.sortedByDescending { it.auctionName }
            "stockLocation" -> if (order == "asc") allPurchases.sortedBy { it.stockLocation } else allPurchases.sortedByDescending { it.stockLocation }
            "rixoCompany" -> if (order == "asc") allPurchases.sortedBy { it.rixoCompany } else allPurchases.sortedByDescending { it.rixoCompany }
            "clientName" -> if (order == "asc") allPurchases.sortedBy { it.clientName } else allPurchases.sortedByDescending { it.clientName }
            else -> allPurchases
        }
    }
    
    fun filterByCarName(carName: String): List<Purchase> {
        return purchaseRepository.findByCarNameContainingIgnoreCase(carName)
    }
    
    fun filterByAuctionName(auctionName: String): List<Purchase> {
        return purchaseRepository.findByAuctionNameContainingIgnoreCase(auctionName)
    }
    
    fun filterByClientName(clientName: String): List<Purchase> {
        return purchaseRepository.findByClientNameContainingIgnoreCase(clientName)
    }
    
    fun filterByDate(date: String): List<Purchase> {
        return purchaseRepository.findByDateContainingIgnoreCase(date)
    }
    
    fun importPurchases(file: MultipartFile): ImportResponse {
        val purchases = mutableListOf<Purchase>()
        
        try {
            println("🚀 Starting CSV import process for file: ${file.originalFilename}")
            println("📁 File size: ${file.size} bytes")
            
            // Read CSV file content with proper encoding
            val csvContent = file.inputStream.bufferedReader().use { it.readText() }
            println("📄 CSV content length: ${csvContent.length} characters")
            
            // Split into lines and process, handling empty lines and encoding issues
            val lines = csvContent.lines().filter { it.isNotBlank() && it.trim().isNotEmpty() }
            println("📊 Found ${lines.size} non-empty lines in CSV")
            
            if (lines.isEmpty()) {
                println("❌ No data found in CSV file")
                return ImportResponse(
                    success = false,
                    message = "No data found in CSV file",
                    importedCount = 0,
                    duplicateCount = 0,
                    errorCount = 0,
                    totalProcessed = 0
                )
            }
            
            // Process each line (skip header if present)
            val dataLines = if (lines.any { it.contains("DATE") || it.contains("Date") || it.contains("LOT NO.") }) {
                println("📋 Skipping header lines")
                lines.dropWhile { 
                    it.contains("DATE") || 
                    it.contains("Date") || 
                    it.contains("LOT NO.") || 
                    it.trim().isEmpty() || 
                    it.trim().all { char -> char == ',' } ||
                    it.trim().startsWith(",") // Skip lines that start with comma (empty first column)
                }
            } else {
                lines
            }
            
            println("🔄 Processing ${dataLines.size} data lines")
            
            for ((index, line) in dataLines.withIndex()) {
                try {
                    println("📝 Processing line ${index + 1}: $line")
                    
                    // Parse CSV line with more robust parsing
                    val values = parseCsvLineRobust(line)
                    println("🔍 Parsed ${values.size} values: ${values.joinToString(" | ")}")
                    
                    if (values.size >= 20) { // Your CSV has many columns, we need at least 20 for valid data
                        // Skip empty rows (rows with mostly empty values)
                        val nonEmptyValues = values.count { it.trim().isNotEmpty() }
                        if (nonEmptyValues < 5) {
                            println("❌ Skipping line ${index + 1}: too many empty values ($nonEmptyValues non-empty)")
                            continue
                        }
                        
                        // Clean and truncate fields to prevent database constraint issues
                        println("🔧 Creating purchase object for line ${index + 1}")
                        val purchase = Purchase(
                            id = null,
                            date = convertJapaneseDateToEnglish(values.getOrNull(0)?.trim()?.replace("\"", "") ?: "").take(255), // DATE column
                            lotNumber = (values.getOrNull(1)?.trim()?.replace("\"", "") ?: "").take(255), // LOT NO. column
                            chassis = (values.getOrNull(2)?.trim()?.replace("\"", "") ?: "").take(255), // CHASSIS column
                            carModelYear = (values.getOrNull(3)?.trim()?.replace("\"", "") ?: "").take(50), // YEAR column
                            brand = (values.getOrNull(4)?.trim()?.replace("\"", "") ?: "").take(20), // BRAND column
                            carName = (values.getOrNull(5)?.trim()?.replace("\"", "") ?: "").take(255), // CAR NAME column
                            grade = (values.getOrNull(6)?.trim()?.replace("\"", "") ?: "").take(20), // GRADE column
                            rank = (values.getOrNull(7)?.trim()?.replace("\"", "") ?: "").take(20), // RANK column
                            color = (values.getOrNull(8)?.trim()?.replace("\"", "") ?: "").take(20), // COLOR column
                            displacement = (values.getOrNull(9)?.trim()?.replace("\"", "") ?: "").take(20), // DISPLACEMENT column
                            fuel = (values.getOrNull(10)?.trim()?.replace("\"", "") ?: "").take(20), // FUEL column
                            seat = (values.getOrNull(11)?.trim()?.replace("\"", "") ?: "").take(20), // SEAT column
                            door = (values.getOrNull(12)?.trim()?.replace("\"", "") ?: "").take(20), // DOOR column
                            distance = (values.getOrNull(13)?.trim()?.replace("\"", "") ?: "").take(20), // DISTANCE column
                            options = (values.getOrNull(14)?.trim()?.replace("\"", "") ?: "").take(20), // OPTIONS column
                            auctionNo = (values.getOrNull(15)?.trim()?.replace("\"", "") ?: "").take(10), // AUCTION NO column
                            auctionName = (values.getOrNull(16)?.trim()?.replace("\"", "") ?: "").take(255), // AUCTION NAME column
                            stockLocation = (values.getOrNull(17)?.trim()?.replace("\"", "") ?: "").take(255), // STOCK LOCATION column
                            rixoCompany = (values.getOrNull(18)?.trim()?.replace("\"", "") ?: "").take(255), // RIXO COMPANY column
                            clientName = (values.getOrNull(19)?.trim()?.replace("\"", "") ?: "").take(255), // CLIENT NAME column
                            country = (values.getOrNull(20)?.trim()?.replace("\"", "") ?: "").take(50), // COUNTRY column
                            price = (values.getOrNull(21)?.trim()?.replace("\"", "") ?: "").take(50), // PRICE column
                            auctionFee = (values.getOrNull(22)?.trim()?.replace("\"", "") ?: "").take(10), // AUCTION FEE column
                            recycleFee = (values.getOrNull(23)?.trim()?.replace("\"", "") ?: "").take(10), // RECYCLE FEE column
                            roadTax = (values.getOrNull(24)?.trim()?.replace("\"", "") ?: "").take(10), // ROAD TAX column
                            totalPrice = (values.getOrNull(25)?.trim()?.replace("\"", "") ?: "").take(10), // TOTAL PRICE column
                            paymentDate = (values.getOrNull(26)?.trim()?.replace("\"", "") ?: "").take(10), // PAYMENT DATE column
                            rixoRequested = (values.getOrNull(27)?.trim()?.replace("\"", "") ?: "").take(50), // RIXO REQUESTED column
                            rixoConfirmed = (values.getOrNull(28)?.trim()?.replace("\t", "")?.replace(" ", "")?.replace("\"", "") ?: "").take(50), // RIXO CONFIRMED column
                            rixoPrice = (values.getOrNull(43)?.trim()?.replace("\"", "") ?: "").take(10), // RIXO PRICE column
                            shipmentDate = (values.getOrNull(30)?.trim()?.replace("\"", "") ?: "").take(10), // SHIPMENT DATE column
                            blNo = (values.getOrNull(31)?.trim()?.replace("\"", "") ?: "").take(10), // B/L NO column
                            vesselNo = (values.getOrNull(32)?.trim()?.replace("\"", "") ?: "").take(10), // VESSEL NO column
                            destination = (values.getOrNull(33)?.trim()?.replace("\"", "") ?: "").take(10), // DESTINATION column
                            shipmentCharges = (values.getOrNull(34)?.trim()?.replace("\"", "") ?: "").take(10), // SHIPMENT CHARGES column
                            freight = (values.getOrNull(35)?.trim()?.replace("\"", "") ?: "").take(10), // FREIGHT column
                            storageCharges = (values.getOrNull(36)?.trim()?.replace("\"", "") ?: "").take(10), // STORAGE CHARGES column
                            miscCharges = (values.getOrNull(37)?.trim()?.replace("\"", "") ?: "").take(10), // MISC CHARGES column
                            inspectionFee = (values.getOrNull(38)?.trim()?.replace("\"", "") ?: "").take(10), // INSPECTION FEE column
                            commission = (values.getOrNull(39)?.trim()?.replace("\"", "") ?: "").take(10), // COMMISSION column
                            repairCompany = (values.getOrNull(43)?.trim()?.replace("\"", "") ?: "").take(10), // REPAIR COMPANY column
                            repairCharges = (values.getOrNull(44)?.trim()?.replace("\"", "") ?: "").take(10), // REPAIR CHARGES column
                            notes = convertJapaneseNotesToEnglish(values.getOrNull(29)?.trim()?.replace("\"", "") ?: "").take(1000) // NOTES column
                        )
                        
                        purchases.add(purchase)
                        println("✅ Created purchase: ${purchase.carName} (${purchase.date}) - Lot: ${purchase.lotNumber}")
                    } else {
                        println("❌ Skipping line ${index + 1}: insufficient data (${values.size} values)")
                    }
                } catch (e: Exception) {
                    println("❌ Error processing line ${index + 1}: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Save all purchases to database with duplicate handling
            if (purchases.isNotEmpty()) {
                println("💾 Attempting to save ${purchases.size} purchases to database...")
                val savedPurchases = mutableListOf<Purchase>()
                val duplicateDetails = mutableListOf<String>()
                val errorDetails = mutableListOf<String>()
                var duplicateCount = 0
                var errorCount = 0
                
                for (purchase in purchases) {
                    try {
                        // Try to save the purchase directly - let the database handle unique constraint violations
                        val savedPurchase = purchaseRepository.save(purchase)
                        savedPurchases.add(savedPurchase)
                        println("✅ Saved: ${purchase.carName} (Lot: ${purchase.lotNumber})")
                    } catch (e: Exception) {
                        // Check if it's a unique constraint violation (duplicate)
                        if (e.message?.contains("Duplicate entry") == true || 
                            e.message?.contains("uk_lot_chasis") == true ||
                            e.message?.contains("uk_lot_chassis") == true ||
                            e.message?.contains("UNIQUE constraint failed") == true) {
                            val duplicateMessage = "⚠️ Duplicate found: Lot ${purchase.lotNumber}, Chassis ${purchase.chassis} (${purchase.carName})"
                            println(duplicateMessage)
                            duplicateDetails.add(duplicateMessage)
                            duplicateCount++
                        } else {
                            val errorMessage = "❌ Error saving purchase ${purchase.carName} (Lot: ${purchase.lotNumber}): ${e.message}"
                            println(errorMessage)
                            errorDetails.add(errorMessage)
                            errorCount++
                        }
                    }
                }
                
                println("✅ Successfully saved ${savedPurchases.size} purchases to database")
                if (duplicateCount > 0) {
                    println("⚠️ Skipped $duplicateCount duplicate purchases")
                }
                if (errorCount > 0) {
                    println("❌ Failed to save $errorCount purchases due to errors")
                }
                
                val message = when {
                    savedPurchases.isNotEmpty() && duplicateCount > 0 -> 
                        "Import completed! ${savedPurchases.size} records imported, ${duplicateCount} duplicates skipped."
                    savedPurchases.isNotEmpty() -> 
                        "Import successful! ${savedPurchases.size} records imported."
                    duplicateCount > 0 -> 
                        "No new records imported. ${duplicateCount} duplicates found and skipped."
                    else -> 
                        "Import failed. No valid records found."
                }
                
                return ImportResponse(
                    success = savedPurchases.isNotEmpty() || duplicateCount > 0,
                    message = message,
                    importedCount = savedPurchases.size,
                    duplicateCount = duplicateCount,
                    errorCount = errorCount,
                    totalProcessed = purchases.size,
                    importedPurchases = savedPurchases,
                    duplicateDetails = duplicateDetails,
                    errorDetails = errorDetails
                )
            } else {
                println("❌ No valid purchases found in CSV file")
                return ImportResponse(
                    success = false,
                    message = "No valid records found in CSV file",
                    importedCount = 0,
                    duplicateCount = 0,
                    errorCount = 0,
                    totalProcessed = 0
                )
            }
            
        } catch (e: Exception) {
            println("❌ CSV import process failed: ${e.message}")
            e.printStackTrace()
            return ImportResponse(
                success = false,
                message = "Import failed: ${e.message}",
                importedCount = 0,
                duplicateCount = 0,
                errorCount = 1,
                totalProcessed = 0,
                errorDetails = listOf("❌ CSV import process failed: ${e.message}")
            )
        }
    }
    
    private fun parseCsvLineRobust(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        // Handle potential encoding issues and normalize the line
        val normalizedLine = line.trim()
        
        while (i < normalizedLine.length) {
            val char = normalizedLine[i]
            
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < normalizedLine.length && normalizedLine[i + 1] == '"') {
                        // Escaped quote
                        current.append('"')
                        i += 2 // Skip both quotes
                    } else {
                        // Toggle quote state
                        inQuotes = !inQuotes
                        i++
                    }
                }
                (char == ',' || char == '\t') && !inQuotes -> {
                    // End of field (comma or tab)
                    result.add(current.toString().trim())
                    current.clear()
                    i++
                }
                else -> {
                    current.append(char)
                    i++
                }
            }
        }
        
        // Add the last field
        result.add(current.toString().trim())
        
        return result
    }
    
    /**
     * Converts Japanese date format to English
     * Example: "6月2日月曜日" -> "June 2, 2025 (Monday)"
     */
    private fun convertJapaneseDateToEnglish(japaneseDate: String): String {
        if (japaneseDate.isBlank()) return ""
        
        try {
            // Japanese month mappings
            val monthMap = mapOf(
                "1月" to "January", "2月" to "February", "3月" to "March", "4月" to "April",
                "5月" to "May", "6月" to "June", "7月" to "July", "8月" to "August",
                "9月" to "September", "10月" to "October", "11月" to "November", "12月" to "December"
            )
            
            // Japanese day of week mappings
            val dayMap = mapOf(
                "月曜日" to "Monday", "火曜日" to "Tuesday", "水曜日" to "Wednesday", "木曜日" to "Thursday",
                "金曜日" to "Friday", "土曜日" to "Saturday", "日曜日" to "Sunday"
            )
            
            var result = japaneseDate
            
            // Replace months
            monthMap.forEach { (japanese, english) ->
                result = result.replace(japanese, english)
            }
            
            // Replace days of week
            dayMap.forEach { (japanese, english) ->
                result = result.replace(japanese, "($english)")
            }
            
            // Add current year if not present
            if (!result.contains("2025") && !result.contains("2024") && !result.contains("2023")) {
                result = result.replace("日", ", 2025")
            } else {
                result = result.replace("日", "")
            }
            
            // Clean up any remaining Japanese characters
            result = result.replace("月", "")
            
            return result.trim()
        } catch (e: Exception) {
            println("⚠️ Error converting Japanese date: $japaneseDate - ${e.message}")
            return japaneseDate
        }
    }
    
    /**
     * Converts Japanese notes to English
     * Example: "書類送付済み" -> "Documents Sent"
     */
    private fun convertJapaneseNotesToEnglish(japaneseNotes: String): String {
        if (japaneseNotes.isBlank()) return ""
        
        try {
            var result = japaneseNotes
            
            // Common Japanese phrases in notes
            val phraseMap = mapOf(
                "書類送付済み" to "Documents Sent",
                "書類発送済み" to "Documents Shipped",
                "車検あり" to "Has Inspection",
                "車検なし" to "No Inspection",
                "再出品" to "Relisted",
                "NEW LOT:" to "NEW LOT:",
                "DELETED ND FROM PORTAL" to "DELETED ND FROM PORTAL",
                "NEGOTIATE" to "NEGOTIATE",
                "WITH BOX" to "WITH BOX",
                "HIRA BODY" to "HIRA BODY",
                "SPARE KEY MISSING" to "SPARE KEY MISSING",
                "NO SPARE KEY" to "NO SPARE KEY",
                "TIT T-5476" to "TIT T-5476",
                "MANUFACTURE IN 2018" to "MANUFACTURE IN 2018",
                "(GOT HIT AT AUCTION)" to "(GOT HIT AT AUCTION)"
            )
            
            // Replace Japanese phrases
            phraseMap.forEach { (japanese, english) ->
                result = result.replace(japanese, english)
            }
            
            return result.trim()
        } catch (e: Exception) {
            println("⚠️ Error converting Japanese notes: $japaneseNotes - ${e.message}")
            return japaneseNotes
        }
    }
    
    fun generateRixoPdf(selectedIds: List<Long>, invoiceData: Map<String, String>): ByteArray {
        println("🚀 Starting Rixo PDF generation for ${selectedIds.size} purchases")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            println("📄 Found ${purchases.size} purchases to include in PDF")
            
            // Generate PDF using the PDF service
            return pdfService.generateRixoPdf(purchases, invoiceData)
            
        } catch (e: Exception) {
            println("❌ Error generating Rixo PDF: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun generateRixoText(selectedIds: List<Long>): String {
        println("🚀 Starting Rixo text generation for ${selectedIds.size} purchases")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            println("📄 Found ${purchases.size} purchases to include in text")
            
            // Create a simple text content
            return createRixoTextContent(purchases)
            
        } catch (e: Exception) {
            println("❌ Error generating Rixo text: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private fun createRixoTextContent(purchases: List<Purchase>): String {
        val content = StringBuilder()
        
        content.appendLine("RIXO PURCHASE REPORT")
        content.appendLine("Generated on: ${java.time.LocalDateTime.now()}")
        content.appendLine("=".repeat(60))
        content.appendLine()
        
        for ((index, purchase) in purchases.withIndex()) {
            content.appendLine("PURCHASE ${index + 1}")
            content.appendLine("-".repeat(40))
            content.appendLine("Date: ${purchase.date}")
            content.appendLine("Lot Number: ${purchase.lotNumber}")
            content.appendLine("Chassis: ${purchase.chassis}")
            content.appendLine("Car Name: ${purchase.carName}")
            content.appendLine("Car Model Year: ${purchase.carModelYear}")
            content.appendLine("Client Name: ${purchase.clientName}")
            content.appendLine("Rixo Company: ${purchase.rixoCompany}")
            content.appendLine("Rixo Requested: ${purchase.rixoRequested}")
            content.appendLine("Rixo Confirmed: ${purchase.rixoConfirmed}")
            content.appendLine("Rixo Price: ${purchase.rixoPrice}")
            content.appendLine()
        }
        
        return content.toString()
    }
    
}
