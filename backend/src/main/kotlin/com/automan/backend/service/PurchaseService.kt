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
        // Check for duplicate before creating (chassis must be unique)
        val existingPurchase = purchaseRepository.findByChassis(purchase.chassis)
        
        if (existingPurchase != null) {
            throw IllegalArgumentException("⚠️ Duplicate found: A purchase with Chassis ${purchase.chassis} (${existingPurchase.carName}) already exists.")
        }
        
        // Ensure shaken has a value (default to false if null)
        // Explicitly convert to boolean to ensure proper database storage
        val shakenValue = when {
            purchase.shaken == true -> true
            purchase.shaken == false -> false
            else -> false
        }
        val purchaseToSave = purchase.copy(shaken = shakenValue)
        
        println("🔍 [Service] Creating purchase - received shaken=${purchase.shaken} (type: ${purchase.shaken?.javaClass?.simpleName}), saving shaken=${purchaseToSave.shaken}")
        val savedPurchase = purchaseRepository.save(purchaseToSave)
        println("🔍 [Service] Saved purchase - shaken=${savedPurchase.shaken} (database value: ${if (savedPurchase.shaken == true) 1 else 0})")
        return savedPurchase
    }
    
    @Transactional
    fun updatePurchase(id: Long, purchase: Purchase): Purchase? {
        println("🔍 [Service] Updating purchase ID: $id")
        println("🔍 [Service] Purchase data received: $purchase")
        
        val existingPurchase = purchaseRepository.findById(id).orElse(null)
        if (existingPurchase != null) {
            println("🔍 [Service] Found existing purchase: $existingPurchase")
            
            // Check for duplicates if chassis is being updated (chassis must be unique)
            if (purchase.chassis != null && purchase.chassis != existingPurchase.chassis) {
                val duplicateCheck = purchaseRepository.findByChassis(purchase.chassis)
                
                if (duplicateCheck != null && duplicateCheck.id != id) {
                    throw IllegalArgumentException("⚠️ Duplicate found: A purchase with Chassis ${purchase.chassis} (${duplicateCheck.carName}) already exists.")
                }
            }
            
            // Merge the new data with existing data, keeping existing values for null fields
            val updatedPurchase = existingPurchase.copy(
                id = id,
                date = purchase.date ?: existingPurchase.date,
                chassis = purchase.chassis ?: existingPurchase.chassis,
                carModelYear = purchase.carModelYear ?: existingPurchase.carModelYear,
                brand = purchase.brand ?: existingPurchase.brand,
                carName = purchase.carName ?: existingPurchase.carName,
                shipmentSize = purchase.shipmentSize ?: existingPurchase.shipmentSize,
                grade = purchase.grade ?: existingPurchase.grade,
                rank = purchase.rank ?: existingPurchase.rank,
                color = purchase.color ?: existingPurchase.color,
                displacement = purchase.displacement ?: existingPurchase.displacement,
                fuel = purchase.fuel ?: existingPurchase.fuel,
                seat = purchase.seat ?: existingPurchase.seat,
                door = purchase.door ?: existingPurchase.door,
                distance = purchase.distance ?: existingPurchase.distance,
                options = purchase.options ?: existingPurchase.options,
                cc = purchase.cc ?: existingPurchase.cc,
                shift = purchase.shift ?: existingPurchase.shift,
                steeringWheel = purchase.steeringWheel ?: existingPurchase.steeringWheel,
                wd = purchase.wd ?: existingPurchase.wd,
                auctionNo = purchase.auctionNo ?: existingPurchase.auctionNo,
                auctionHouse = purchase.auctionHouse ?: existingPurchase.auctionHouse,
                stockLocation = purchase.stockLocation ?: existingPurchase.stockLocation,
                rixoCompany = purchase.rixoCompany ?: existingPurchase.rixoCompany,
                clientName = purchase.clientName ?: existingPurchase.clientName,
                consignee = purchase.consignee ?: existingPurchase.consignee,
                country = purchase.country ?: existingPurchase.country,
                price = purchase.price ?: existingPurchase.price,
                auctionFee = purchase.auctionFee ?: existingPurchase.auctionFee,
                recycleFee = purchase.recycleFee ?: existingPurchase.recycleFee,
                roadTax = purchase.roadTax ?: existingPurchase.roadTax,
                totalPrice = purchase.totalPrice ?: existingPurchase.totalPrice,
                paymentDate = purchase.paymentDate ?: existingPurchase.paymentDate,
                rixoRequested = purchase.rixoRequested ?: existingPurchase.rixoRequested,
                rixoConfirmed = purchase.rixoConfirmed ?: existingPurchase.rixoConfirmed,
                notes = purchase.notes ?: existingPurchase.notes,
                shipmentDate = purchase.shipmentDate ?: existingPurchase.shipmentDate,
                blNo = purchase.blNo ?: existingPurchase.blNo,
                vesselNo = purchase.vesselNo ?: existingPurchase.vesselNo,
                destination = purchase.destination ?: existingPurchase.destination,
                shipmentCharges = purchase.shipmentCharges ?: existingPurchase.shipmentCharges,
                freight = purchase.freight ?: existingPurchase.freight,
                storageCharges = purchase.storageCharges ?: existingPurchase.storageCharges,
                miscCharges = purchase.miscCharges ?: existingPurchase.miscCharges,
                inspectionFee = purchase.inspectionFee ?: existingPurchase.inspectionFee,
                commission = purchase.commission ?: existingPurchase.commission,
                rixoPrice = purchase.rixoPrice ?: existingPurchase.rixoPrice,
                venueId = purchase.venueId ?: existingPurchase.venueId,
                numberCut = purchase.numberCut ?: existingPurchase.numberCut,
                shaken = when {
                    purchase.shaken == true -> {
                        println("🔍 [Service] updatePurchase - Setting shaken to true")
                        true
                    }
                    purchase.shaken == false -> {
                        println("🔍 [Service] updatePurchase - Setting shaken to false")
                        false
                    }
                    else -> {
                        println("🔍 [Service] updatePurchase - Keeping existing shaken: ${existingPurchase.shaken}")
                        existingPurchase.shaken
                    }
                },
                repairCompany = purchase.repairCompany ?: existingPurchase.repairCompany,
                repairCharges = purchase.repairCharges ?: existingPurchase.repairCharges,
                updatedAt = java.time.LocalDateTime.now()
            )
            
            println("🔍 [Service] Saving updated purchase: $updatedPurchase")
            val savedPurchase = purchaseRepository.save(updatedPurchase)
            println("✅ [Service] Successfully saved purchase: $savedPurchase")
            return savedPurchase
        } else {
            println("❌ [Service] Purchase with ID $id not found")
        }
        return null
    }
    
    @Transactional
    fun updatePurchasePartial(id: Long, updateData: Map<String, Any>): Purchase? {
        println("🔍 [Service] Updating purchase ID: $id with partial data")
        println("🔍 [Service] Update data received: $updateData")
        
        val existingPurchase = purchaseRepository.findById(id).orElse(null)
        if (existingPurchase != null) {
            println("🔍 [Service] Found existing purchase: $existingPurchase")
            
            // Create a new Purchase object with updated fields
            val updatedPurchase = existingPurchase.copy(
                id = id,
                date = updateData["date"] as? String ?: existingPurchase.date,
                chassis = updateData["chassis"] as? String ?: existingPurchase.chassis,
                carModelYear = updateData["carModelYear"] as? String ?: existingPurchase.carModelYear,
                brand = updateData["brand"] as? String ?: existingPurchase.brand,
                carName = updateData["carName"] as? String ?: existingPurchase.carName,
                shipmentSize = run {
                    println("DEBUG: shipmentSize mapping - updateData[shipmentSize]=${updateData["shipmentSize"]}, updateData[vehicleType]=${updateData["vehicleType"]}, existing=${existingPurchase.shipmentSize}")
                    (updateData["shipmentSize"] as? String)
                        ?: (updateData["vehicleType"] as? String)
                        ?: existingPurchase.shipmentSize
                },
                grade = updateData["grade"] as? String ?: existingPurchase.grade,
                rank = updateData["rank"] as? String ?: existingPurchase.rank,
                color = updateData["color"] as? String ?: existingPurchase.color,
                displacement = updateData["displacement"] as? String ?: existingPurchase.displacement,
                fuel = updateData["fuel"] as? String ?: existingPurchase.fuel,
                seat = updateData["seat"] as? String ?: existingPurchase.seat,
                door = updateData["door"] as? String ?: existingPurchase.door,
                distance = updateData["distance"] as? String ?: existingPurchase.distance,
                options = updateData["options"] as? String ?: existingPurchase.options,
                cc = run {
                    val ccValue = updateData["cc"]
                    when {
                        ccValue is Int -> ccValue
                        ccValue is Number -> ccValue.toInt()
                        ccValue is String -> ccValue.toIntOrNull()
                        ccValue == null -> existingPurchase.cc
                        else -> {
                            try {
                                ccValue.toString().toIntOrNull()
                            } catch (e: Exception) {
                                existingPurchase.cc
                            }
                        }
                    }
                },
                shift = updateData["shift"] as? String ?: existingPurchase.shift,
                steeringWheel = updateData["steeringWheel"] as? String ?: existingPurchase.steeringWheel,
                wd = updateData["wd"] as? String ?: existingPurchase.wd,
                auctionNo = updateData["auctionNo"] as? String ?: existingPurchase.auctionNo,
                auctionHouse = (updateData["auctionHouse"] as? String)
                    ?: (updateData["auctionName"] as? String)
                    ?: existingPurchase.auctionHouse,
                stockLocation = updateData["stockLocation"] as? String ?: existingPurchase.stockLocation,
                rixoCompany = updateData["rixoCompany"] as? String ?: existingPurchase.rixoCompany,
                clientName = updateData["clientName"] as? String ?: existingPurchase.clientName,
                consignee = run {
                    val consigneeValue = updateData["consignee"] as? String
                    if (consigneeValue != null) {
                        val trimmed = consigneeValue.trim()
                        if (trimmed.isNotEmpty()) {
                            println("🔍 [Service] Updating consignee to: '$trimmed'")
                            trimmed
                        } else {
                            println("🔍 [Service] Consignee value blank, keeping existing: '${existingPurchase.consignee}'")
                            existingPurchase.consignee
                        }
                    } else {
                        existingPurchase.consignee
                    }
                },
                country = updateData["country"] as? String ?: existingPurchase.country,
                price = updateData["price"] as? String ?: existingPurchase.price,
                auctionFee = updateData["auctionFee"] as? String ?: existingPurchase.auctionFee,
                recycleFee = updateData["recycleFee"] as? String ?: existingPurchase.recycleFee,
                roadTax = updateData["roadTax"] as? String ?: existingPurchase.roadTax,
                totalPrice = updateData["totalPrice"] as? String ?: existingPurchase.totalPrice,
                paymentDate = updateData["paymentDate"] as? String ?: existingPurchase.paymentDate,
                rixoRequested = updateData["rixoRequested"] as? String ?: existingPurchase.rixoRequested,
                rixoConfirmed = updateData["rixoConfirmed"] as? String ?: existingPurchase.rixoConfirmed,
                notes = updateData["notes"] as? String ?: existingPurchase.notes,
                shipmentDate = updateData["shipmentDate"] as? String ?: existingPurchase.shipmentDate,
                blNo = updateData["blNo"] as? String ?: existingPurchase.blNo,
                vesselNo = updateData["vesselNo"] as? String ?: existingPurchase.vesselNo,
                destination = updateData["destination"] as? String ?: existingPurchase.destination,
                shipmentCharges = updateData["shipmentCharges"] as? String ?: existingPurchase.shipmentCharges,
                freight = updateData["freight"] as? String ?: existingPurchase.freight,
                storageCharges = updateData["storageCharges"] as? String ?: existingPurchase.storageCharges,
                miscCharges = updateData["miscCharges"] as? String ?: existingPurchase.miscCharges,
                inspectionFee = updateData["inspectionFee"] as? String ?: existingPurchase.inspectionFee,
                commission = updateData["commission"] as? String ?: existingPurchase.commission,
                rixoPrice = updateData["rixoPrice"] as? String ?: existingPurchase.rixoPrice,
                venueId = run {
                    val receivedVenueId = updateData["venueId"] as? String
                    println("🔍 DEBUG: venueId received = $receivedVenueId, existing = ${existingPurchase.venueId}")
                    receivedVenueId ?: existingPurchase.venueId
                },
                numberCut = updateData["numberCut"] as? String ?: existingPurchase.numberCut,
                shaken = run {
                    val shakenValue = updateData["shaken"]
                    println("🔍 [Service] Processing shaken field - received: $shakenValue (type: ${shakenValue?.javaClass?.simpleName})")
                    val result = when {
                        shakenValue is Boolean -> {
                            println("🔍 [Service] Shaken is Boolean: $shakenValue")
                            shakenValue
                        }
                        shakenValue is String -> {
                            val boolValue = shakenValue.toBoolean()
                            println("🔍 [Service] Shaken is String '$shakenValue', converted to Boolean: $boolValue")
                            boolValue
                        }
                        shakenValue == true -> {
                            println("🔍 [Service] Shaken is true (boxed)")
                            true
                        }
                        shakenValue == false -> {
                            println("🔍 [Service] Shaken is false (boxed)")
                            false
                        }
                        shakenValue == null -> {
                            println("🔍 [Service] Shaken is null, keeping existing: ${existingPurchase.shaken}")
                            existingPurchase.shaken
                        }
                        else -> {
                            println("🔍 [Service] Shaken unknown type: ${shakenValue?.javaClass?.name}, keeping existing: ${existingPurchase.shaken}")
                            existingPurchase.shaken
                        }
                    }
                    println("🔍 [Service] Final shaken value to save: $result")
                    result
                },
                repairCompany = updateData["repairCompany"] as? String ?: existingPurchase.repairCompany,
                repairCharges = updateData["repairCharges"] as? String ?: existingPurchase.repairCharges,
                carPictures = run {
                    val carPicturesData = updateData["carPictures"]
                    if (carPicturesData != null) {
                        // Convert car pictures array to JSON string
                        val jsonString = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(carPicturesData)
                        println("📷 [Service] Saving car pictures data: $jsonString")
                        jsonString
                    } else {
                        existingPurchase.carPictures
                    }
                },
                bookingId = run {
                    val bookingIdValue = updateData["bookingId"]
                    println("🔍 [Service] Processing bookingId - updateData['bookingId'] = $bookingIdValue (type: ${bookingIdValue?.javaClass?.simpleName ?: "null"})")
                    println("🔍 [Service] bookingId value class: ${bookingIdValue?.javaClass?.name}")
                    println("🔍 [Service] bookingId value toString: ${bookingIdValue?.toString()}")
                    println("🔍 [Service] bookingId key exists in updateData: ${updateData.containsKey("bookingId")}")
                    when {
                        // If key exists and value is null, clear the bookingId (user wants to delete it)
                        updateData.containsKey("bookingId") && bookingIdValue == null -> {
                            println("🔍 [Service] bookingId is explicitly null (key exists), clearing value")
                            null
                        }
                        // If key doesn't exist, keep existing value (field not provided)
                        !updateData.containsKey("bookingId") -> {
                            println("🔍 [Service] bookingId key not in updateData, keeping existing: ${existingPurchase.bookingId}")
                            existingPurchase.bookingId
                        }
                        bookingIdValue == null -> {
                            println("🔍 [Service] bookingId is null, keeping existing: ${existingPurchase.bookingId}")
                            existingPurchase.bookingId
                        }
                        bookingIdValue is Int -> {
                            val result = bookingIdValue.toLong()
                            println("🔍 [Service] bookingId is Int, converted to Long: $result")
                            result
                        }
                        bookingIdValue is Long -> {
                            println("🔍 [Service] bookingId is Long: $bookingIdValue")
                            bookingIdValue
                        }
                        bookingIdValue is Number -> {
                            val result = bookingIdValue.toLong()
                            println("🔍 [Service] bookingId is Number (${bookingIdValue.javaClass.name}), converted to Long: $result")
                            result
                        }
                        bookingIdValue is String -> {
                            val result = bookingIdValue.toLongOrNull()
                            println("🔍 [Service] bookingId is String, converted to Long: $result")
                            result
                        }
                        else -> {
                            println("⚠️ [Service] bookingId is unknown type: ${bookingIdValue.javaClass.name}, attempting conversion...")
                            try {
                                val result = bookingIdValue.toString().toLongOrNull()
                                println("🔍 [Service] Converted unknown type to Long: $result")
                                result
                            } catch (e: Exception) {
                                println("❌ [Service] Failed to convert bookingId, keeping existing: ${existingPurchase.bookingId}")
                                existingPurchase.bookingId
                            }
                        }
                    }
                },
                vessel = run {
                    val vesselValue = updateData["vessel"]
                    println("🔍 [Service] Processing vessel - updateData['vessel'] = $vesselValue (type: ${vesselValue?.javaClass?.simpleName})")
                    when {
                        vesselValue is String -> {
                            println("🔍 [Service] vessel is String, setting to: '$vesselValue'")
                            vesselValue
                        }
                        vesselValue == null -> {
                            println("🔍 [Service] vessel is null, keeping existing: '${existingPurchase.vessel}'")
                            existingPurchase.vessel
                        }
                        else -> {
                            println("🔍 [Service] vessel is unknown type, converting to string: '$vesselValue'")
                            vesselValue.toString()
                        }
                    }
                },
                shipped = run {
                    val shippedValue = updateData["shipped"]
                    when {
                        shippedValue is Boolean -> shippedValue
                        shippedValue is String -> shippedValue.toBoolean()
                        shippedValue is Number -> shippedValue.toInt() != 0
                        else -> existingPurchase.shipped
                    }
                },
                updatedAt = java.time.LocalDateTime.now()
            )
            
            println("🔍 [Service] Saving updated purchase:")
            println("   - shipmentDate: ${updatedPurchase.shipmentDate}")
            println("   - bookingId: ${updatedPurchase.bookingId}")
            println("   - vessel: ${updatedPurchase.vessel}")
            val savedPurchase = purchaseRepository.save(updatedPurchase)
            println("✅ [Service] Successfully saved purchase:")
            println("   - shipmentDate: ${savedPurchase.shipmentDate}")
            println("   - bookingId: ${savedPurchase.bookingId}")
            println("   - vessel: ${savedPurchase.vessel}")
            println("   - shaken: ${savedPurchase.shaken} (type: ${savedPurchase.shaken?.javaClass?.simpleName})")
            println("   - shaken database value: ${if (savedPurchase.shaken == true) 1 else 0}")
            return savedPurchase
        } else {
            println("❌ [Service] Purchase with ID $id not found")
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
    
    @Transactional
    fun markPurchasesAsShipped(purchaseIds: List<Long>): List<Purchase> {
        println("🚢 [Service] Marking ${purchaseIds.size} purchases as shipped: $purchaseIds")
        val updatedPurchases = mutableListOf<Purchase>()
        
        for (id in purchaseIds) {
            val existingPurchase = purchaseRepository.findById(id).orElse(null)
            if (existingPurchase != null) {
                val updatedPurchase = existingPurchase.copy(
                    shipped = true,
                    updatedAt = java.time.LocalDateTime.now()
                )
                val savedPurchase = purchaseRepository.save(updatedPurchase)
                updatedPurchases.add(savedPurchase)
                println("✅ [Service] Marked purchase $id as shipped")
            } else {
                println("⚠️ [Service] Purchase $id not found, skipping")
            }
        }
        
        println("✅ [Service] Successfully marked ${updatedPurchases.size} purchases as shipped")
        return updatedPurchases
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
            "auctionHouse" -> if (order == "asc") allPurchases.sortedBy { it.auctionHouse } else allPurchases.sortedByDescending { it.auctionHouse }
            "stockLocation" -> if (order == "asc") allPurchases.sortedBy { it.stockLocation } else allPurchases.sortedByDescending { it.stockLocation }
            "rixoCompany" -> if (order == "asc") allPurchases.sortedBy { it.rixoCompany } else allPurchases.sortedByDescending { it.rixoCompany }
            "clientName" -> if (order == "asc") allPurchases.sortedBy { it.clientName } else allPurchases.sortedByDescending { it.clientName }
            else -> allPurchases
        }
    }
    
    fun filterByCarName(carName: String): List<Purchase> {
        return purchaseRepository.findByCarNameContainingIgnoreCase(carName)
    }
    
    fun filterByAuctionHouse(auctionHouse: String): List<Purchase> {
        return purchaseRepository.findByAuctionHouseContainingIgnoreCase(auctionHouse)
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
            println("🔎 Importer version: flexible-column-mapping + chassis-only + index-detection v4")
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
            
            // Find header row and detect column mapping
            val headerRow = findHeaderRow(lines)
            if (headerRow == -1) {
                println("❌ No valid header row found")
                return ImportResponse(
                    success = false,
                    message = "No valid header row found in CSV file",
                    importedCount = 0,
                    duplicateCount = 0,
                    errorCount = 0,
                    totalProcessed = 0
                )
            }
            
            val headerLine = lines[headerRow]
            val columnMapping = createColumnMapping(headerLine)
            println("🗺️ Column mapping: $columnMapping")
            
            // Process data rows (skip header and any rows before it)
            val dataLines = lines.drop(headerRow + 1)
            println("🔄 Processing ${dataLines.size} data lines")
            
            for ((index, line) in dataLines.withIndex()) {
                try {
                    println("📝 Processing line ${index + 1}: $line")
                    
                    // Parse CSV line with more robust parsing
                    val parsed = parseCsvLineRobust(line)
                    
                    // Pad parsed line to ensure we have enough columns for mapping
                    val paddedParsed = padCsvLine(parsed, columnMapping.size)
                    
                    // Get chassis value using column mapping and sanitize
                    val chassisValue = getColumnValue(paddedParsed, columnMapping, "CHASSIS", "CHASIS")
                        ?.replace(Regex("[\uFEFF\u200B\u200C\u200D\u2060]"), "")
                        ?.replace("\"", "")
                        ?.trim()
                        ?: ""
                    
                    // Skip rows without chassis or placeholder '-'
                    if (chassisValue.isBlank() || chassisValue == "-") {
                        println("⏭️ Skipping line ${index + 1}: No chassis value")
                        continue
                    }
                    
                    println("🔧 Creating purchase object for line ${index + 1} with chassis: $chassisValue")
                    
                    // Create purchase object using flexible column mapping
                    val purchase = createPurchaseFromMappedColumns(paddedParsed, columnMapping, chassisValue)
                    
                    purchases.add(purchase)
                    println("✅ Created purchase: ${purchase.carName} (${purchase.date}) - Chassis: ${purchase.chassis}")
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
                        println("✅ Saved: ${purchase.carName} (Chassis: ${purchase.chassis})")
                    } catch (e: Exception) {
                        // Check if it's a unique constraint violation (duplicate)
                        if (e.message?.contains("Duplicate entry") == true || 
                            e.message?.contains("uk_chassis") == true ||
                            e.message?.contains("UNIQUE constraint failed") == true) {
                            val duplicateMessage = "⚠️ Duplicate found: Chassis ${purchase.chassis} (${purchase.carName})"
                            println(duplicateMessage)
                            duplicateDetails.add(duplicateMessage)
                            duplicateCount++
                        } else {
                            val errorMessage = "❌ Error saving purchase ${purchase.carName} (Chassis: ${purchase.chassis}): ${e.message}"
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
    
    private fun findHeaderRow(lines: List<String>): Int {
        for ((index, line) in lines.withIndex()) {
            val parsed = parseCsvLineRobust(line)
            val headers = parsed.map { sanitizeHeader(it).uppercase() }
            
            // Look for common header patterns with more variations
            val hasChassis = headers.contains("CHASSIS") || headers.contains("CHASIS")
            val hasDate = headers.contains("DATE")
            val hasCarName = headers.contains("CAR NAME") || headers.contains("CARNAME")
            val hasAuction = headers.contains("AUCTION HOUSE") || headers.contains("AUCTION NAME") || headers.contains("AUCTION")
            
            // Check if this looks like a header row
            if (hasChassis || hasDate || (hasCarName && hasAuction)) {
                println("📋 Found header row at line ${index + 1}: $headers")
                println("🔍 Header analysis: Chassis=$hasChassis, Date=$hasDate, CarName=$hasCarName, Auction=$hasAuction")
                return index
            }
        }
        return -1
    }
    
    private fun createColumnMapping(headerLine: String): Map<String, Int> {
        val parsed = parseCsvLineRobust(headerLine)
        val mapping = mutableMapOf<String, Int>()
        
        // Detect if first column is an index column (contains only numbers or is empty)
        val isIndexColumn = parsed.isNotEmpty() && 
            (parsed[0].trim().matches(Regex("^\\d+$")) || parsed[0].trim().isEmpty())
        
        println("🔍 Column analysis: First column='${parsed.getOrNull(0)}', IsIndexColumn=$isIndexColumn")
        
        for ((index, header) in parsed.withIndex()) {
            val cleanHeader = sanitizeHeader(header).uppercase()
            
            // Skip empty headers
            if (cleanHeader.isNotEmpty()) {
                mapping[cleanHeader] = index
                
                // Add alternative mappings for common variations
                when (cleanHeader) {
                    "CHASIS" -> mapping["CHASSIS"] = index
                    "AUCTION NAME" -> mapping["AUCTION HOUSE"] = index
                    "RXO CONFIRMED" -> mapping["RIXO CONFIRMED"] = index
                    "RIXO CHARGES" -> mapping["RIXO PRICE"] = index
                    "B/L NO" -> mapping["B/L NO."] = index
                    "VESSEL NO" -> mapping["VESSEL NO."] = index
                    "AUCTION NO" -> mapping["AUCTION NO."] = index
                }
            }
        }
        
        println("🗺️ Column mapping created: $mapping")
        return mapping
    }

    /**
     * Removes BOM and zero-width/hidden characters from header values.
     */
    private fun sanitizeHeader(raw: String): String {
        if (raw.isEmpty()) return ""
        // Characters: BOM (FEFF), ZWSP (200B), ZWNJ (200C), ZWJ (200D), WJ (2060)
        val zeroWidthRegex = Regex("[\uFEFF\u200B\u200C\u200D\u2060]")
        return raw
            .replace("\"", "")
            .replace(zeroWidthRegex, "")
            .trim()
    }
    
    private fun getColumnValue(values: List<String>, columnMapping: Map<String, Int>, vararg possibleNames: String): String? {
        for (name in possibleNames) {
            val index = columnMapping[name]
            if (index != null && index < values.size) {
                val value = values[index].trim().replace("\"", "")
                // Return empty string for missing values instead of null
                return if (value.isBlank()) "" else value
            }
        }
        // Return empty string for missing columns instead of null
        return ""
    }
    
    private fun createPurchaseFromMappedColumns(values: List<String>, columnMapping: Map<String, Int>, chassis: String): Purchase {
        println("🔧 Creating purchase with chassis: $chassis")
        println("📊 Available values: ${values.size} columns")
        println("🗺️ Column mapping keys: ${columnMapping.keys}")
        
        return Purchase(
            id = null,
            date = getColumnValue(values, columnMapping, "DATE")?.let { convertJapaneseDateToEnglish(it) }?.take(255) ?: "",
            chassis = chassis.take(255),
            carModelYear = getColumnValue(values, columnMapping, "YEAR")?.take(50) ?: "",
            brand = getColumnValue(values, columnMapping, "BRAND")?.take(20) ?: "",
            carName = getColumnValue(values, columnMapping, "CAR NAME", "CARNAME")?.take(255) ?: "",
            grade = getColumnValue(values, columnMapping, "GRADE")?.take(20) ?: "",
            rank = getColumnValue(values, columnMapping, "RANK")?.take(20) ?: "",
            color = getColumnValue(values, columnMapping, "COLOR")?.take(20) ?: "",
            displacement = getColumnValue(values, columnMapping, "DISPLACEMENT")?.take(20) ?: "",
            fuel = getColumnValue(values, columnMapping, "FUEL")?.take(20) ?: "",
            seat = getColumnValue(values, columnMapping, "SEAT")?.take(20) ?: "",
            door = getColumnValue(values, columnMapping, "DOOR")?.take(20) ?: "",
            distance = getColumnValue(values, columnMapping, "DISTANCE")?.take(20) ?: "",
            options = getColumnValue(values, columnMapping, "OPTIONS")?.take(20) ?: "",
            auctionNo = getColumnValue(values, columnMapping, "AUCTION NO", "AUCTION NO.", "AUCTION")?.take(10) ?: "",
            auctionHouse = getColumnValue(values, columnMapping, "AUCTION HOUSE", "AUCTION NAME", "AUCTION")?.take(255) ?: "",
            stockLocation = getColumnValue(values, columnMapping, "STOCK LOCATION")?.take(255) ?: "",
            rixoCompany = getColumnValue(values, columnMapping, "RIXO COMPANY")?.take(255) ?: "",
            clientName = getColumnValue(values, columnMapping, "CLIENT NAME")?.take(255) ?: "",
            country = getColumnValue(values, columnMapping, "COUNTRY")?.take(50) ?: "",
            price = getColumnValue(values, columnMapping, "PRICE")?.take(50) ?: "",
            auctionFee = getColumnValue(values, columnMapping, "AUCTION FEE")?.take(10) ?: "",
            recycleFee = getColumnValue(values, columnMapping, "RECYCLE FEE")?.take(10) ?: "",
            roadTax = getColumnValue(values, columnMapping, "ROAD TAX")?.take(10) ?: "",
            totalPrice = getColumnValue(values, columnMapping, "TOTAL PRICE")?.take(10) ?: "",
            paymentDate = getColumnValue(values, columnMapping, "PAYMENT DATE")?.take(10) ?: "",
            rixoRequested = getColumnValue(values, columnMapping, "RIXO REQUESTED")?.take(50) ?: "",
            rixoConfirmed = getColumnValue(values, columnMapping, "RIXO CONFIRMED", "RXO CONFIRMED")?.replace("\t", "")?.replace(" ", "")?.take(50) ?: "",
            rixoPrice = getColumnValue(values, columnMapping, "RIXO PRICE", "RIXO CHARGES")?.take(10) ?: "",
            shipmentDate = getColumnValue(values, columnMapping, "SHIPMENT DATE")?.take(10) ?: "",
            blNo = getColumnValue(values, columnMapping, "B/L NO", "B/L NO.", "BL NO")?.take(10) ?: "",
            vesselNo = getColumnValue(values, columnMapping, "VESSEL NO", "VESSEL NO.", "VESSEL")?.take(10) ?: "",
            destination = getColumnValue(values, columnMapping, "DESTINATION")?.take(10) ?: "",
            shipmentCharges = getColumnValue(values, columnMapping, "SHIPMENT CHARGES")?.take(10) ?: "",
            freight = getColumnValue(values, columnMapping, "FREIGHT")?.take(10) ?: "",
            storageCharges = getColumnValue(values, columnMapping, "STORAGE CHARGES")?.take(10) ?: "",
            miscCharges = getColumnValue(values, columnMapping, "MISC CHARGES")?.take(10) ?: "",
            inspectionFee = getColumnValue(values, columnMapping, "INSPECTION FEE")?.take(10) ?: "",
            commission = getColumnValue(values, columnMapping, "COMMISSION")?.take(10) ?: "",
            repairCompany = getColumnValue(values, columnMapping, "REPAIR COMPANY")?.take(10) ?: "",
            repairCharges = getColumnValue(values, columnMapping, "REPAIR CHARGES")?.take(10) ?: "",
            notes = getColumnValue(values, columnMapping, "NOTES")?.let { convertJapaneseNotesToEnglish(it) }?.take(1000) ?: ""
        )
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
    
    private fun padCsvLine(values: List<String>, targetSize: Int): List<String> {
        val padded = values.toMutableList()
        while (padded.size < targetSize) {
            padded.add("")
        }
        return padded
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
    
    fun generateRixoPdf(selectedIds: List<Long>, invoiceData: Map<String, String>, missingRixoData: List<Map<String, String>> = emptyList()): ByteArray {
        println("🚀 Starting Rixo PDF generation for ${selectedIds.size} purchases")
        println("📝 Missing Rixo data: $missingRixoData")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            println("📄 Found ${purchases.size} purchases to include in PDF")
            
            // Apply missing Rixo data to purchases
            val updatedPurchases = purchases.map { purchase ->
                // Find missing data for this purchase
                val purchaseMissingData = missingRixoData.filter { 
                    it["purchaseId"] == purchase.id.toString() 
                }
                
                // Create a new purchase with updated fields
                var updatedPurchase = purchase
                
                // Apply the missing data to the purchase
                for (missingItem in purchaseMissingData) {
                    val field = missingItem["field"]
                    val value = missingItem["value"]
                    
                    updatedPurchase = when (field) {
                        "rixoCompany" -> updatedPurchase.copy(rixoCompany = value)
                        "rixoRequested" -> updatedPurchase.copy(rixoRequested = value)
                        "rixoConfirmed" -> updatedPurchase.copy(rixoConfirmed = value)
                        "rixoPrice" -> updatedPurchase.copy(rixoPrice = value)
                        "clientName" -> updatedPurchase.copy(clientName = value)
                        "carName" -> updatedPurchase.copy(carName = value)
                        "carModelYear" -> updatedPurchase.copy(carModelYear = value)
                        else -> updatedPurchase
                    }
                }
                
                updatedPurchase
            }
            
            println("📝 Applied missing Rixo data to ${updatedPurchases.size} purchases")
            
            // Generate PDF using the PDF service
            return pdfService.generateRixoPdf(updatedPurchases, invoiceData)
            
        } catch (e: Exception) {
            println("❌ Error generating Rixo PDF: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    fun generateRixoTransportPdf(selectedIds: List<Long>, transportData: Map<String, String>, purchaseData: List<Map<String, Any>> = emptyList()): ByteArray {
        println("🚀 Starting Rixo Transport PDF generation for ${selectedIds.size} purchases")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            println("📄 Found ${purchases.size} purchases to include in Rixo Transport PDF")
            
            // Override purchase fields with form data if provided
            val updatedPurchases = purchases.map { purchase ->
                val formData = purchaseData.find { formItem ->
                    val formId = formItem["id"]
                    when (formId) {
                        is Number -> formId.toLong() == purchase.id
                        is Map<*, *> -> {
                            // Handle Kotlin Long object {low_1=397, high_1=0}
                            val low = formId["low_1"] as? Number
                            if (low != null) {
                                low.toLong() == purchase.id
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                }
                if (formData != null) {
                    println("📝 Overriding purchase ${purchase.id} with form data: $formData")
                    // Create a copy with updated fields
                    purchase.copy(
                        carModelYear = formData["carModelYear"] as? String ?: purchase.carModelYear,
                        carName = formData["carName"] as? String ?: purchase.carName,
                        clientName = formData["clientName"] as? String ?: purchase.clientName,
                        stockLocation = formData["stockLocation"] as? String ?: purchase.stockLocation,
                        venueId = formData["venueId"] as? String ?: purchase.venueId,
                        numberCut = formData["numberCut"] as? String ?: purchase.numberCut
                    )
                } else {
                    purchase
                }
            }
            
            // Generate PDF using the PDF service
            println("🔍 PurchaseService: transportData before PDF generation: $transportData")
            println("🔍 PurchaseService: transportData keys: ${transportData.keys}")
            println("🔍 PurchaseService: transportData values: ${transportData.values}")
            println("🔍 PurchaseService: buyingDate value: '${transportData["buyingDate"]}'")
            return pdfService.generateRixoTransportPdf(updatedPurchases, transportData)
            
        } catch (e: Exception) {
            println("❌ Error generating Rixo Transport PDF: ${e.message}")
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
    
    fun getPurchaseByChassis(chassis: String): Purchase? {
        return purchaseRepository.findByChassis(chassis)
    }
    
    fun getUniqueCountries(): List<String> {
        return purchaseRepository.findDistinctCountries()
    }
    
    fun getUniqueStockLocations(): List<String> {
        return purchaseRepository.findDistinctStockLocations()
    }
    
    fun getFilteredChassis(country: String, polPort: String): List<String> {
        return purchaseRepository.findFilteredChassis(country, polPort)
    }
    
    fun saveCarCostDetails(
        chassis: String,
        carPrice: Double,
        auctionFee: Double,
        rixoPrice: Double,
        shippingCharge: Double,
        freight: Double,
        inspectionFee: Double,
        repairFee: Double,
        mscCharges: Double,
        profit: Double,
        packagePrice: Double = 0.0,
        isPackageMode: Boolean = false
    ) {
        val existingPurchase = purchaseRepository.findByChassis(chassis)
        if (existingPurchase != null) {
            // Create a new Purchase object with updated cost details
            val updatedPurchase = existingPurchase.copy(
                price = carPrice.toString(),
                auctionFee = auctionFee.toString(),
                rixoPrice = rixoPrice.toString(),
                shipmentCharges = shippingCharge.toString(),
                freight = freight.toString(),
                inspectionFee = inspectionFee.toString(),
                repairCharges = repairFee.toString(),
                miscCharges = mscCharges.toString(),
                profit = java.math.BigDecimal(profit),
                packagePrice = packagePrice.toString(),
                isPackageMode = isPackageMode,
                updatedAt = java.time.LocalDateTime.now()
            )
            
            purchaseRepository.save(updatedPurchase)
            println("✅ Updated cost details for chassis: $chassis")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }

    fun saveTotalCnfPrice(
        chassis: String,
        totalCnfPrice: Double
    ) {
        val existingPurchase = purchaseRepository.findByChassis(chassis)
        if (existingPurchase != null) {
            // Create a new Purchase object with updated total C&F price
            val updatedPurchase = existingPurchase.copy(
                totalCnfPrice = java.math.BigDecimal(totalCnfPrice),
                updatedAt = java.time.LocalDateTime.now()
            )
            
            purchaseRepository.save(updatedPurchase)
            println("✅ Updated total C&F price for chassis: $chassis = $totalCnfPrice")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }


    fun saveFobCarCostDetails(
        chassis: String,
        carPrice: Double,
        auctionFee: Double,
        rixoPrice: Double,
        shippingCharge: Double,
        inspectionFee: Double,
        repairFee: Double,
        mscCharges: Double,
        profit: Double
    ) {
        val existingPurchase = purchaseRepository.findByChassis(chassis)
        if (existingPurchase != null) {
            // Create a new Purchase object with updated FOB cost details
            val updatedPurchase = existingPurchase.copy(
                price = carPrice.toString(),
                auctionFee = auctionFee.toString(),
                rixoPrice = rixoPrice.toString(),
                shipmentCharges = shippingCharge.toString(),
                inspectionFee = inspectionFee.toString(),
                repairCharges = repairFee.toString(),
                miscCharges = mscCharges.toString(),
                profit = java.math.BigDecimal(profit),
                updatedAt = java.time.LocalDateTime.now()
            )
            
            purchaseRepository.save(updatedPurchase)
            println("✅ Updated FOB cost details for chassis: $chassis")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }
    
    // Get shipping schedule PDF data
    fun getShippingSchedulePdfData(request: com.automan.backend.dto.ShippingSchedulePdfRequest): com.automan.backend.dto.ShippingSchedulePdfData {
        println("📋 Generating shipping schedule PDF data for booking: ${request.bookingNo}")
        
        // Format shipping date as "DD.MON.YYYY"
        val formattedDate = try {
            val date = java.time.LocalDate.parse(request.shippingDate)
            val day = date.dayOfMonth.toString().padStart(2, '0')
            val month = date.month.name.substring(0, 3).uppercase()
            val year = date.year.toString()
            "$day.$month.$year"
        } catch (e: Exception) {
            request.shippingDate // Fallback to original format
        }
        
        // Split consignee name and address if they're combined
        // The frontend may send both name and address in consigneeName (separated by newline)
        // For PDF: Show name (bold) on first line, address (not bold) on second line
        println("📋 Original consigneeName: '${request.consigneeName}'")
        println("📋 Original consigneeAddress: '${request.consigneeAddress}'")
        
        // Extract consignee name and address properly
        // The frontend sends: consigneeName (may contain both name+address) and consigneeAddress (may be empty or duplicate)
        // We need: name = only company name, address = only address
        
        var extractedName = ""
        var extractedAddress = ""
        
        // First, try splitting by newline (if frontend sent them separated)
        val consigneeNameParts = request.consigneeName?.split("\n", limit = 2) ?: emptyList()
        val nameFromSplit = consigneeNameParts.getOrNull(0)?.trim() ?: ""
        val addressFromSplit = consigneeNameParts.getOrNull(1)?.trim() ?: ""
        
        if (nameFromSplit.isNotEmpty() && addressFromSplit.isNotEmpty()) {
            // Frontend sent them separated by newline - use them directly
            extractedName = nameFromSplit
            extractedAddress = addressFromSplit
        } else {
            // Frontend sent them combined - need to split
            val fullConsigneeName = request.consigneeName?.trim() ?: ""
            
            // Split at "LTD." or "LTD " - company name ends here
            val ltdPatterns = listOf("LTD.", "LTD ", "LTD\n")
            var foundSplit = false
            
            for (pattern in ltdPatterns) {
                if (fullConsigneeName.contains(pattern, ignoreCase = true)) {
                    val index = fullConsigneeName.indexOf(pattern, ignoreCase = true)
                    if (index >= 0) {
                        extractedName = fullConsigneeName.substring(0, index + pattern.length).trim()
                        extractedAddress = fullConsigneeName.substring(index + pattern.length).trim()
                        foundSplit = true
                        break
                    }
                }
            }
            
            // If still not split, check if consigneeAddress field has the address
            if (!foundSplit || extractedAddress.isEmpty()) {
                val addressFromField = request.consigneeAddress?.trim() ?: ""
                if (addressFromField.isNotEmpty()) {
                    // Use address from separate field
                    extractedAddress = addressFromField
                    // Name should be everything before the address in consigneeName
                    if (fullConsigneeName.contains(addressFromField)) {
                        val addressIndex = fullConsigneeName.indexOf(addressFromField)
                        if (addressIndex > 0) {
                            extractedName = fullConsigneeName.substring(0, addressIndex).trim()
                        } else {
                            extractedName = fullConsigneeName
                        }
                    } else {
                        extractedName = fullConsigneeName
                    }
                } else {
                    // No address found - use full consigneeName as name
                    extractedName = fullConsigneeName
                    extractedAddress = ""
                }
            }
        }
        
        println("📋 Extracted consignee name: '${extractedName}'")
        println("📋 Extracted consignee address: '${extractedAddress}'")
        println("📋 Name length: ${extractedName.length}, Address length: ${extractedAddress.length}")
        
        // Create consignee details - name (bold) and address (not bold)
        val consigneeDetails = com.automan.backend.dto.ConsigneeDetailsDto(
            name = extractedName,
            address = extractedAddress
        )
        
        // Fetch car details for each chassis
        val carList = request.chassisNumbers.mapIndexed { index, chassis ->
            val purchase = purchaseRepository.findByChassis(chassis)
            if (purchase != null) {
                // Use saved totalCnfPrice if available, otherwise calculate it
                val totalCnfPrice = if (purchase.totalCnfPrice != null) {
                    // Use saved total C&F price
                    purchase.totalCnfPrice!!
                } else {
                    // Fallback: Calculate final C&F price based on package mode
                    val carPrice = try { java.math.BigDecimal(purchase.price ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                    val packagePrice = try { java.math.BigDecimal(purchase.packagePrice ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                    val isPackageMode = purchase.isPackageMode ?: false
                    
                    if (isPackageMode) {
                        // Package mode: C&F = Car Price + Package Price
                        carPrice.add(packagePrice)
                    } else {
                        // Normal mode: C&F = Car Price + All Fees
                        val auctionFee = try { java.math.BigDecimal(purchase.auctionFee ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val rixoPrice = try { java.math.BigDecimal(purchase.rixoPrice ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val shipmentCharges = try { java.math.BigDecimal(purchase.shipmentCharges ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val freight = try { java.math.BigDecimal(purchase.freight ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val inspectionFee = try { java.math.BigDecimal(purchase.inspectionFee ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val repairCharges = try { java.math.BigDecimal(purchase.repairCharges ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val miscCharges = try { java.math.BigDecimal(purchase.miscCharges ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                        val profit = purchase.profit ?: java.math.BigDecimal.ZERO
                        
                        carPrice.add(auctionFee).add(rixoPrice).add(shipmentCharges)
                            .add(freight).add(inspectionFee).add(repairCharges).add(miscCharges).add(profit)
                    }
                }
                
                com.automan.backend.dto.CarPdfDto(
                    no = index + 1,
                    name = purchase.carName ?: "Unknown",
                    chassisNumber = purchase.chassis,
                    year = purchase.carModelYear ?: "Unknown",
                    cnfPrice = "¥${totalCnfPrice.toInt()}"
                )
            } else {
                com.automan.backend.dto.CarPdfDto(
                    no = index + 1,
                    name = "Unknown",
                    chassisNumber = chassis,
                    year = "Unknown",
                    cnfPrice = "¥0"
                )
            }
        }
        
        return com.automan.backend.dto.ShippingSchedulePdfData(
            companyName = "MEMON CO., LTD",
            bookingNo = request.bookingNo,
            vesselName = request.vesselName,
            pol = request.pol,
            pod = request.pod,
            shippingDate = formattedDate,
            consigneeDetails = consigneeDetails,
            carList = carList
        )
    }
    
}
