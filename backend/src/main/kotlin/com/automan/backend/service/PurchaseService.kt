package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val pdfService: PdfService
) {
    
    /**
     * Validates Production Date (carModelYear): 4-digit year only, no range check.
     * Format should be YYYY-MM (e.g., "2013-12"). Year must be exactly 4 digits.
     * Returns null if valid, or error message if invalid.
     */
    private fun validateCarModelYear(carModelYear: String?): String? {
        if (carModelYear.isNullOrBlank()) return null // Empty is allowed
        
        // Expected format: YYYY-MM
        val parts = carModelYear.split("-")
        if (parts.size != 2) {
            return "Invalid Production Date format. Expected YYYY-MM, got: $carModelYear"
        }
        
        val yearStr = parts[0].trim()
        // Limit to 4 digits only: year must be exactly 4 digits and numeric
        if (yearStr.length != 4) {
            return "Production year must be exactly 4 digits. Got: $yearStr"
        }
        if (!yearStr.all { it.isDigit() }) {
            return "Production year must be 4 digits only. Got: $yearStr"
        }
        
        return null // Valid
    }
    
    fun getAllPurchases(): List<Purchase> {
        return purchaseRepository.findAll()
    }
    
    fun getPurchaseById(id: Long): Purchase? {
        return purchaseRepository.findById(id).orElse(null)
    }
    
    @Transactional
    fun createPurchase(purchase: Purchase): Purchase {
        // Validate carModelYear (Production Date)
        val yearError = validateCarModelYear(purchase.carModelYear)
        if (yearError != null) {
            throw IllegalArgumentException(yearError)
        }
        
        // Ensure shaken has a value (default to false if null)
        // Explicitly convert to boolean to ensure proper database storage
        val shakenValue = when {
            purchase.shaken == true -> true
            purchase.shaken == false -> false
            else -> false
        }
        val purchaseToSave = purchase.copy(shaken = shakenValue)
        
        Logger.debug("Creating purchase - received shaken=${purchase.shaken}, saving shaken=${purchaseToSave.shaken}")
        val savedPurchase = purchaseRepository.save(purchaseToSave)
        Logger.debug("Saved purchase - shaken=${savedPurchase.shaken}")
        return savedPurchase
    }
    
    @Transactional
    fun updatePurchase(id: Long, purchase: Purchase): Purchase? {
        Logger.debug("🔍 [Service] Updating purchase ID: $id")
        Logger.debug("🔍 [Service] Purchase data received: $purchase")
        
        val existingPurchase = purchaseRepository.findById(id).orElse(null)
        if (existingPurchase != null) {
            Logger.debug("🔍 [Service] Found existing purchase: $existingPurchase")
            
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
                fuel = purchase.fuel ?: existingPurchase.fuel,
                seat = purchase.seat ?: existingPurchase.seat,
                door = purchase.door ?: existingPurchase.door,
                distance = purchase.distance ?: existingPurchase.distance,
                options = purchase.options ?: existingPurchase.options,
                cc = purchase.cc ?: existingPurchase.cc,
                shift = purchase.shift ?: existingPurchase.shift,
                wd = purchase.wd ?: existingPurchase.wd,
                driveType = purchase.driveType ?: existingPurchase.driveType,
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
                taxTotal = purchase.taxTotal ?: existingPurchase.taxTotal,
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
                        Logger.debug("🔍 [Service] updatePurchase - Setting shaken to true")
                        true
                    }
                    purchase.shaken == false -> {
                        Logger.debug("🔍 [Service] updatePurchase - Setting shaken to false")
                        false
                    }
                    else -> {
                        Logger.debug("🔍 [Service] updatePurchase - Keeping existing shaken: ${existingPurchase.shaken}")
                        existingPurchase.shaken
                    }
                },
                repairCompany = purchase.repairCompany ?: existingPurchase.repairCompany,
                repairCharges = purchase.repairCharges ?: existingPurchase.repairCharges,
                updatedAt = java.time.LocalDateTime.now()
            )
            
            Logger.debug("🔍 [Service] Saving updated purchase: $updatedPurchase")
            val savedPurchase = purchaseRepository.save(updatedPurchase)
            Logger.log("✅ [Service] Successfully saved purchase: $savedPurchase")
            return savedPurchase
        } else {
            Logger.error("Purchase with ID $id not found")
        }
        return null
    }
    
    @Transactional
    fun updatePurchasePartial(id: Long, updateData: Map<String, Any>): Purchase? {
        Logger.debug("🔍 [Service] Updating purchase ID: $id with partial data")
        Logger.debug("🔍 [Service] Update data received: $updateData")
        
        // Validate carModelYear if provided
        val carModelYearValue = updateData["carModelYear"] as? String
        if (carModelYearValue != null) {
            val yearError = validateCarModelYear(carModelYearValue)
            if (yearError != null) {
                throw IllegalArgumentException(yearError)
            }
        }
        
        val existingPurchase = purchaseRepository.findById(id).orElse(null)
        if (existingPurchase != null) {
            Logger.debug("🔍 [Service] Found existing purchase: $existingPurchase")
            
            // Create a new Purchase object with updated fields
            val updatedPurchase = existingPurchase.copy(
                id = id,
                date = updateData["date"] as? String ?: existingPurchase.date,
                chassis = updateData["chassis"] as? String ?: existingPurchase.chassis,
                carModelYear = updateData["carModelYear"] as? String ?: existingPurchase.carModelYear,
                brand = updateData["brand"] as? String ?: existingPurchase.brand,
                carName = updateData["carName"] as? String ?: existingPurchase.carName,
                shipmentSize = run {
                    Logger.debug("DEBUG: shipmentSize mapping - updateData[shipmentSize]=${updateData["shipmentSize"]}, updateData[vehicleType]=${updateData["vehicleType"]}, existing=${existingPurchase.shipmentSize}")
                    (updateData["shipmentSize"] as? String)
                        ?: (updateData["vehicleType"] as? String)
                        ?: existingPurchase.shipmentSize
                },
                grade = updateData["grade"] as? String ?: existingPurchase.grade,
                rank = updateData["rank"] as? String ?: existingPurchase.rank,
                color = updateData["color"] as? String ?: existingPurchase.color,
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
                wd = updateData["wd"] as? String ?: existingPurchase.wd,
                driveType = updateData["driveType"] as? String ?: existingPurchase.driveType,
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
                            Logger.debug("🔍 [Service] Updating consignee to: '$trimmed'")
                            trimmed
                        } else {
                            Logger.debug("🔍 [Service] Consignee value blank, keeping existing: '${existingPurchase.consignee}'")
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
                taxTotal = updateData["taxTotal"] as? String ?: existingPurchase.taxTotal,
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
                    Logger.debug("🔍 DEBUG: venueId received = $receivedVenueId, existing = ${existingPurchase.venueId}")
                    receivedVenueId ?: existingPurchase.venueId
                },
                numberCut = updateData["numberCut"] as? String ?: existingPurchase.numberCut,
                shaken = run {
                    val shakenValue = updateData["shaken"]
                    Logger.debug("🔍 [Service] Processing shaken field - received: $shakenValue (type: ${shakenValue?.javaClass?.simpleName})")
                    val result = when {
                        shakenValue is Boolean -> {
                            Logger.debug("🔍 [Service] Shaken is Boolean: $shakenValue")
                            shakenValue
                        }
                        shakenValue is String -> {
                            val boolValue = shakenValue.toBoolean()
                            Logger.debug("🔍 [Service] Shaken is String '$shakenValue', converted to Boolean: $boolValue")
                            boolValue
                        }
                        shakenValue == true -> {
                            Logger.debug("🔍 [Service] Shaken is true (boxed)")
                            true
                        }
                        shakenValue == false -> {
                            Logger.debug("🔍 [Service] Shaken is false (boxed)")
                            false
                        }
                        shakenValue == null -> {
                            Logger.debug("🔍 [Service] Shaken is null, keeping existing: ${existingPurchase.shaken}")
                            existingPurchase.shaken
                        }
                        else -> {
                            Logger.debug("🔍 [Service] Shaken unknown type: ${shakenValue?.javaClass?.name}, keeping existing: ${existingPurchase.shaken}")
                            existingPurchase.shaken
                        }
                    }
                    Logger.debug("🔍 [Service] Final shaken value to save: $result")
                    result
                },
                repairCompany = updateData["repairCompany"] as? String ?: existingPurchase.repairCompany,
                repairCharges = updateData["repairCharges"] as? String ?: existingPurchase.repairCharges,
                carPictures = run {
                    val carPicturesData = updateData["carPictures"]
                    if (carPicturesData != null) {
                        // Convert car pictures array to JSON string
                        val jsonString = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(carPicturesData)
                        Logger.debug("📷 [Service] Saving car pictures data: $jsonString")
                        jsonString
                    } else {
                        existingPurchase.carPictures
                    }
                },
                bookingId = run {
                    val bookingIdValue = updateData["bookingId"]
                    Logger.debug("🔍 [Service] Processing bookingId - updateData['bookingId'] = $bookingIdValue (type: ${bookingIdValue?.javaClass?.simpleName ?: "null"})")
                    Logger.debug("🔍 [Service] bookingId value class: ${bookingIdValue?.javaClass?.name}")
                    Logger.debug("🔍 [Service] bookingId value toString: ${bookingIdValue?.toString()}")
                    Logger.debug("🔍 [Service] bookingId key exists in updateData: ${updateData.containsKey("bookingId")}")
                    val result = when {
                        // If key exists and value is null, clear the bookingId (user wants to delete it)
                        updateData.containsKey("bookingId") && bookingIdValue == null -> {
                            Logger.debug("🔍 [Service] bookingId is explicitly null (key exists), clearing value")
                            null
                        }
                        // If key doesn't exist, keep existing value (field not provided)
                        !updateData.containsKey("bookingId") -> {
                            Logger.debug("🔍 [Service] bookingId key not in updateData, keeping existing: ${existingPurchase.bookingId}")
                            existingPurchase.bookingId
                        }
                        bookingIdValue == null -> {
                            Logger.debug("bookingId is null, keeping existing: ${existingPurchase.bookingId}")
                            existingPurchase.bookingId
                        }
                        bookingIdValue is Int -> {
                            val converted = bookingIdValue.toLong()
                            Logger.debug("🔍 [Service] bookingId is Int, converted to Long: $converted")
                            converted
                        }
                        bookingIdValue is Long -> {
                            Logger.debug("🔍 [Service] bookingId is Long: $bookingIdValue")
                            bookingIdValue
                        }
                        bookingIdValue is Number -> {
                            val converted = bookingIdValue.toLong()
                            Logger.debug("🔍 [Service] bookingId is Number (${bookingIdValue.javaClass.name}), converted to Long: $converted")
                            converted
                        }
                        bookingIdValue is String -> {
                            val converted = bookingIdValue.toLongOrNull()
                            Logger.debug("🔍 [Service] bookingId is String '$bookingIdValue', converted to Long: $converted")
                            converted
                        }
                        else -> {
                            Logger.warn("bookingId is unknown type: ${bookingIdValue.javaClass.name}, attempting conversion...")
                            try {
                                val converted = bookingIdValue.toString().toLongOrNull()
                                Logger.debug("🔍 [Service] Converted unknown type to Long: $converted")
                                converted
                            } catch (e: Exception) {
                                Logger.error("❌ [Service] Failed to convert bookingId, keeping existing: ${existingPurchase.bookingId}")
                                existingPurchase.bookingId
                            }
                        }
                    }
                    Logger.debug("FINAL bookingId value to save: $result")
                    result
                },
                vessel = run {
                    val vesselValue = updateData["vessel"]
                    Logger.debug("Processing vessel - updateData['vessel'] = $vesselValue")
                    when {
                        vesselValue is String -> {
                            Logger.debug("vessel is String, setting to: '$vesselValue'")
                            vesselValue
                        }
                        vesselValue == null -> {
                            Logger.debug("vessel is null, keeping existing: '${existingPurchase.vessel}'")
                            existingPurchase.vessel
                        }
                        else -> {
                            Logger.debug("vessel is unknown type, converting to string: '$vesselValue'")
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
                totalCnfPrice = run {
                    val totalCnfPriceValue = updateData["totalCnfPrice"]
                    Logger.debug("Processing totalCnfPrice - updateData['totalCnfPrice'] = $totalCnfPriceValue")
                    Logger.debug("Existing totalCnfPrice = ${existingPurchase.totalCnfPrice}")
                    val result = when {
                        totalCnfPriceValue == null -> {
                            Logger.debug("totalCnfPrice is null, keeping existing: ${existingPurchase.totalCnfPrice}")
                            existingPurchase.totalCnfPrice
                        }
                        totalCnfPriceValue is Number -> {
                            val newValue = java.math.BigDecimal(totalCnfPriceValue.toDouble())
                            Logger.debug("totalCnfPrice is Number, converting to BigDecimal: $newValue")
                            newValue
                        }
                        totalCnfPriceValue is String -> {
                            val doubleValue = totalCnfPriceValue.toDoubleOrNull()
                            if (doubleValue != null) {
                                val newValue = java.math.BigDecimal(doubleValue)
                                Logger.debug("totalCnfPrice is String '$totalCnfPriceValue', converted to BigDecimal: $newValue")
                                newValue
                            } else {
                                Logger.debug("totalCnfPrice String conversion failed, keeping existing: ${existingPurchase.totalCnfPrice}")
                                existingPurchase.totalCnfPrice
                            }
                        }
                        else -> {
                            try {
                                val doubleValue = totalCnfPriceValue.toString().toDoubleOrNull()
                                if (doubleValue != null) {
                                    val newValue = java.math.BigDecimal(doubleValue)
                                    Logger.debug("totalCnfPrice converted from '${totalCnfPriceValue}' to BigDecimal: $newValue")
                                    newValue
                                } else {
                                    Logger.debug("totalCnfPrice conversion failed, keeping existing: ${existingPurchase.totalCnfPrice}")
                                    existingPurchase.totalCnfPrice
                                }
                            } catch (e: Exception) {
                                Logger.error("Exception converting totalCnfPrice: ${e.message}, keeping existing: ${existingPurchase.totalCnfPrice}")
                                existingPurchase.totalCnfPrice
                            }
                        }
                    }
                    Logger.debug("FINAL totalCnfPrice value to save: $result")
                    result
                },
                updatedAt = java.time.LocalDateTime.now()
            )
            
            Logger.debug("Saving updated purchase - shipmentDate: ${updatedPurchase.shipmentDate}, bookingId: ${updatedPurchase.bookingId}, vessel: ${updatedPurchase.vessel}")
            
            // CRITICAL: Ensure the entity ID is set correctly for JPA to recognize it as an update
            val purchaseToSave = if (updatedPurchase.id != null) {
                updatedPurchase
            } else {
                updatedPurchase.copy(id = id)
            }
            
            Logger.debug("Entity ID before save: ${purchaseToSave.id}")
            val savedPurchase = purchaseRepository.saveAndFlush(purchaseToSave)
            // CRITICAL: Fetch fresh entity from database to ensure we get the actual saved value
            // This prevents JPA from returning a cached/stale entity
            val freshPurchase = purchaseRepository.findById(id).orElse(null)
            val purchaseToReturn = freshPurchase ?: savedPurchase
            
            Logger.debug("Successfully saved purchase - ID: ${purchaseToReturn.id}, shipmentDate: ${purchaseToReturn.shipmentDate}, bookingId: ${purchaseToReturn.bookingId}")
            // Verify the saved totalCnfPrice matches what we intended to save
            if (updateData.containsKey("totalCnfPrice")) {
                val expectedValue = when (val value = updateData["totalCnfPrice"]) {
                    is Number -> java.math.BigDecimal(value.toDouble())
                    is String -> value.toDoubleOrNull()?.let { java.math.BigDecimal(it) }
                    else -> null
                }
                if (expectedValue != null && purchaseToReturn.totalCnfPrice != expectedValue) {
                    Logger.warn("WARNING: totalCnfPrice mismatch! Expected: $expectedValue, Saved: ${purchaseToReturn.totalCnfPrice}. This may indicate a database constraint or trigger issue")
                } else if (expectedValue != null) {
                    Logger.debug("totalCnfPrice verified: ${purchaseToReturn.totalCnfPrice}")
                }
            }
            
            // Verify the saved value matches what we intended
            if (purchaseToReturn.bookingId != updatedPurchase.bookingId) {
                Logger.warn("WARNING: Saved bookingId (${purchaseToReturn.bookingId}) does not match intended value (${updatedPurchase.bookingId}). This indicates a potential JPA entity state issue!")
            } else {
                Logger.debug("Verified: Saved bookingId matches intended value")
            }
            
            return purchaseToReturn
        } else {
            Logger.error("Purchase with ID $id not found")
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
        Logger.log("Marking ${purchaseIds.size} purchases as shipped: $purchaseIds")
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
                Logger.debug("Marked purchase $id as shipped")
            } else {
                Logger.warn("Purchase $id not found, skipping")
            }
        }
        
        Logger.debug("Successfully marked ${updatedPurchases.size} purchases as shipped")
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
    
    fun filterByConsigneeAndVesselAndShipmentDate(consignee: String?, vessel: String?, shipmentDate: String?): List<Purchase> {
        return purchaseRepository.findByConsigneeAndVesselAndShipmentDate(consignee, vessel, shipmentDate)
    }
    
    fun importPurchases(file: MultipartFile): ImportResponse {
        val purchases = mutableListOf<Purchase>()
        
        try {
            Logger.debug("Starting CSV import process for file: ${file.originalFilename}")
            Logger.debug("Importer version: flexible-column-mapping + chassis-only + index-detection v4")
            Logger.debug("File size: ${file.size} bytes")
            
            // Read CSV file content with proper encoding
            val csvContent = file.inputStream.bufferedReader().use { it.readText() }
            Logger.debug("CSV content length: ${csvContent.length} characters")
            
            // Split into lines and process, handling empty lines and encoding issues
            val lines = csvContent.lines().filter { it.isNotBlank() && it.trim().isNotEmpty() }
            Logger.log("📊 Found ${lines.size} non-empty lines in CSV")
            
            if (lines.isEmpty()) {
                Logger.error("❌ No data found in CSV file")
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
                Logger.error("❌ No valid header row found")
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
            Logger.debug("Column mapping: $columnMapping")
            
            // Process data rows (skip header and any rows before it)
            val dataLines = lines.drop(headerRow + 1)
            Logger.debug("Processing ${dataLines.size} data lines")
            
            for ((index, line) in dataLines.withIndex()) {
                try {
                    Logger.debug("📝 Processing line ${index + 1}: $line")
                    
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
                        Logger.debug("⏭️ Skipping line ${index + 1}: No chassis value")
                        continue
                    }
                    
                    Logger.debug("🔧 Creating purchase object for line ${index + 1} with chassis: $chassisValue")
                    
                    // Create purchase object using flexible column mapping
                    val purchase = createPurchaseFromMappedColumns(paddedParsed, columnMapping, chassisValue)
                    
                    purchases.add(purchase)
                    Logger.debug("✅ Created purchase: ${purchase.carName} (${purchase.date}) - Chassis: ${purchase.chassis}")
                } catch (e: Exception) {
                    Logger.error("❌ Error processing line ${index + 1}: ${e.message}", e)
                }
            }
            
            // Save all purchases to database with duplicate handling
            // Note: Using individual try-catch per purchase to allow partial success
            // For critical errors that should rollback entire transaction, throw exception outside loop
            if (purchases.isNotEmpty()) {
                Logger.log("💾 Attempting to save ${purchases.size} purchases to database...")
                val savedPurchases = mutableListOf<Purchase>()
                val duplicateDetails = mutableListOf<String>()
                val errorDetails = mutableListOf<String>()
                var duplicateCount = 0
                var errorCount = 0
                var criticalError: Exception? = null
                
                for (purchase in purchases) {
                    try {
                        // Try to save the purchase directly - let the database handle unique constraint violations
                        val savedPurchase = purchaseRepository.save(purchase)
                        savedPurchases.add(savedPurchase)
                        Logger.debug("✅ Saved: ${purchase.carName} (Chassis: ${purchase.chassis})")
                    } catch (e: Exception) {
                        // Check if it's a unique constraint violation (duplicate) - this is expected and non-critical
                        if (e.message?.contains("Duplicate entry") == true || 
                            e.message?.contains("uk_chassis") == true ||
                            e.message?.contains("UNIQUE constraint failed") == true) {
                            val duplicateMessage = "⚠️ Duplicate found: Chassis ${purchase.chassis} (${purchase.carName})"
                            Logger.warn(duplicateMessage)
                            duplicateDetails.add(duplicateMessage)
                            duplicateCount++
                        } else {
                            // For non-duplicate errors, log but continue (partial import is acceptable)
                            // If we want to rollback on critical errors, we would throw here
                            val errorMessage = "❌ Error saving purchase ${purchase.carName} (Chassis: ${purchase.chassis}): ${e.message}"
                            Logger.error(errorMessage)
                            errorDetails.add(errorMessage)
                            errorCount++
                            // Store first critical error for potential reporting
                            if (criticalError == null) {
                                criticalError = e
                            }
                        }
                    }
                }
                
                // If all purchases failed with critical errors (not duplicates), consider throwing to rollback
                // For now, we allow partial success which is typically desired for CSV imports
                
                Logger.log("✅ Successfully saved ${savedPurchases.size} purchases to database")
                if (duplicateCount > 0) {
                    Logger.warn("⚠️ Skipped $duplicateCount duplicate purchases")
                }
                if (errorCount > 0) {
                    Logger.error("❌ Failed to save $errorCount purchases due to errors")
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
                Logger.error("No valid purchases found in CSV file")
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
            Logger.error("CSV import process failed: ${e.message}", e)
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
                Logger.debug("Found header row at line ${index + 1}: $headers")
                Logger.debug("Header analysis: Chassis=$hasChassis, Date=$hasDate, CarName=$hasCarName, Auction=$hasAuction")
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
        
        Logger.debug("Column analysis: First column='${parsed.getOrNull(0)}', IsIndexColumn=$isIndexColumn")
        
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
        
        Logger.debug("Column mapping created: $mapping")
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
        Logger.debug("Creating purchase with chassis: $chassis")
        Logger.debug("Available values: ${values.size} columns")
        Logger.debug("Column mapping keys: ${columnMapping.keys}")
        
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
            Logger.warn("Error converting Japanese date: $japaneseDate - ${e.message}")
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
            Logger.warn("Error converting Japanese notes: $japaneseNotes - ${e.message}")
            return japaneseNotes
        }
    }
    
    // Generates Rixo PDF only; does NOT persist rixoRequested. Client sets Rixo Requested manually after faxing.
    fun generateRixoPdf(selectedIds: List<Long>, invoiceData: Map<String, String>, missingRixoData: List<Map<String, String>> = emptyList()): ByteArray {
        Logger.log("Starting Rixo PDF generation for ${selectedIds.size} purchases")
        Logger.debug("Missing Rixo data: $missingRixoData")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            Logger.debug("Found ${purchases.size} purchases to include in PDF")
            
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
            
            Logger.debug("Applied missing Rixo data to ${updatedPurchases.size} purchases")
            
            // Generate PDF using the PDF service
            return pdfService.generateRixoPdf(updatedPurchases, invoiceData)
            
        } catch (e: Exception) {
            Logger.error("Error generating Rixo PDF: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }
    
    // Generates Rixo Transport PDF only; does NOT persist rixoRequested. Client sets Rixo Requested manually after faxing.
    fun generateRixoTransportPdf(selectedIds: List<Long>, transportData: Map<String, String>, purchaseData: List<Map<String, Any>> = emptyList()): ByteArray {
        Logger.log("Starting Rixo Transport PDF generation for ${selectedIds.size} purchases")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            Logger.debug("Found ${purchases.size} purchases to include in Rixo Transport PDF")
            
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
                    Logger.debug("Overriding purchase ${purchase.id} with form data: $formData")
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
            Logger.debug("PurchaseService: transportData before PDF generation: $transportData")
            Logger.debug("PurchaseService: transportData keys: ${transportData.keys}")
            Logger.debug("PurchaseService: transportData values: ${transportData.values}")
            Logger.debug("PurchaseService: buyingDate value: '${transportData["buyingDate"]}'")
            return pdfService.generateRixoTransportPdf(updatedPurchases, transportData)
            
        } catch (e: Exception) {
            Logger.error("Error generating Rixo Transport PDF: ${e.message}", e)
            e.printStackTrace()
            throw e
        }
    }
    
    fun generateRixoText(selectedIds: List<Long>): String {
        Logger.log("Starting Rixo text generation for ${selectedIds.size} purchases")
        
        try {
            // Get selected purchases
            val purchases = selectedIds.mapNotNull { id ->
                purchaseRepository.findById(id).orElse(null)
            }
            
            if (purchases.isEmpty()) {
                throw IllegalArgumentException("No purchases found for the selected IDs")
            }
            
            Logger.debug("Found ${purchases.size} purchases to include in text")
            
            // Create a simple text content
            return createRixoTextContent(purchases)
            
        } catch (e: Exception) {
            Logger.error("Error generating Rixo text: ${e.message}", e)
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
        // Return first purchase with this chassis (since chassis is no longer unique)
        return purchaseRepository.findByChassis(chassis).firstOrNull()
    }
    
    fun getUniqueCountries(): List<String> {
        return purchaseRepository.findDistinctCountries()
    }
    
    fun getUniqueStockLocations(): List<String> {
        return purchaseRepository.findDistinctStockLocations()
    }
    
    fun getStockLocationsByCountry(country: String): List<String> {
        return purchaseRepository.findDistinctStockLocationsByCountry(country)
    }
    
    fun getUniqueRixoCompanies(): List<String> {
        return purchaseRepository.findDistinctRixoCompanies()
    }
    
    fun getUniqueRepairCompanies(): List<String> {
        return purchaseRepository.findDistinctRepairCompanies()
    }
    
    fun getUniqueVenueIds(): List<String> {
        return purchaseRepository.findDistinctVenueIds()
    }
    
    @Transactional(readOnly = true)
    fun getFilteredChassis(country: String, polPort: String): List<String> {
        // Use optimized database query instead of loading all purchases into memory
        // This is much more efficient, especially with large datasets
        return purchaseRepository.findFilteredChassis(country, polPort)
    }
    
    fun getUnshippedChassisByPolPort(polPort: String): List<String> {
        return purchaseRepository.findUnshippedChassisByPolPort(polPort)
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
        isPackageMode: Boolean = false
    ) {
        val existingPurchases = purchaseRepository.findByChassis(chassis)
        if (existingPurchases.isNotEmpty()) {
            // Update all purchases with this chassis (since chassis is no longer unique)
            existingPurchases.forEach { existingPurchase ->
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
                    isPackageMode = isPackageMode,
                    updatedAt = java.time.LocalDateTime.now()
                )
                
                purchaseRepository.save(updatedPurchase)
            }
            Logger.debug("Updated cost details for chassis: $chassis (${existingPurchases.size} purchase(s))")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }

    fun saveTotalCnfPrice(
        chassis: String,
        totalCnfPrice: Double
    ) {
        val existingPurchases = purchaseRepository.findByChassis(chassis)
        // Update all purchases with this chassis (since chassis is no longer unique)
        existingPurchases.forEach { existingPurchase ->
            // Create a new Purchase object with updated total C&F price
            val updatedPurchase = existingPurchase.copy(
                totalCnfPrice = java.math.BigDecimal(totalCnfPrice),
                updatedAt = java.time.LocalDateTime.now()
            )
            
            purchaseRepository.save(updatedPurchase)
        }
        if (existingPurchases.isNotEmpty()) {
            Logger.debug("Updated total C&F price for chassis: $chassis = $totalCnfPrice (${existingPurchases.size} purchase(s))")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }
    
    fun saveTotalCnfPriceByPurchaseIds(
        purchaseIds: List<Long>,
        totalCnfPrice: Double
    ) {
        val purchases = purchaseRepository.findAllById(purchaseIds)
        if (purchases.isEmpty()) {
            throw RuntimeException("No purchases found for IDs: $purchaseIds")
        }
        
        purchases.forEach { purchase ->
            val updatedPurchase = purchase.copy(
                totalCnfPrice = java.math.BigDecimal(totalCnfPrice),
                updatedAt = java.time.LocalDateTime.now()
            )
            purchaseRepository.save(updatedPurchase)
        }
        
        Logger.debug("Updated total C&F price for ${purchases.size} purchase(s): $totalCnfPrice")
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
        val existingPurchases = purchaseRepository.findByChassis(chassis)
        if (existingPurchases.isNotEmpty()) {
            // Update all purchases with this chassis (since chassis is no longer unique)
            existingPurchases.forEach { existingPurchase ->
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
            }
            Logger.debug("Updated FOB cost details for chassis: $chassis (${existingPurchases.size} purchase(s))")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }
    
    // Get shipping schedule PDF data
    fun getShippingSchedulePdfData(request: com.automan.backend.dto.ShippingSchedulePdfRequest): com.automan.backend.dto.ShippingSchedulePdfData {
        Logger.debug("Generating shipping schedule PDF data for booking: ${request.bookingNo}")
        
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
        
        // Use ONLY consigneeName field value - ignore consigneeAddress completely
        // The consigneeName will be split by commas in PDF generation
        Logger.debug("ConsigneeName from request: '${request.consigneeName}'")
        
        val consigneeNameValue = request.consigneeName?.trim() ?: ""
        
        // Create consignee details - use consigneeName as-is, address is empty
        // PDF generation will split consigneeName by commas
        val consigneeDetails = com.automan.backend.dto.ConsigneeDetailsDto(
            name = consigneeNameValue,
            address = "" // Always empty - we only use consigneeName
        )
        
        // Fetch car details for each chassis
        val carList = request.chassisNumbers.mapIndexed { index, chassis ->
            val purchases = purchaseRepository.findByChassis(chassis)
            val purchase = purchases.firstOrNull() // Use first purchase if multiple exist
            if (purchase != null) {
                // Use saved totalCnfPrice if available, otherwise calculate it
                val totalCnfPrice = if (purchase.totalCnfPrice != null) {
                    // Use saved total C&F price
                    purchase.totalCnfPrice!!
                } else {
                    // Fallback: Calculate final C&F price (package_price column removed)
                    val carPrice = try { java.math.BigDecimal(purchase.price ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                    val isPackageMode = purchase.isPackageMode ?: false
                    
                    if (isPackageMode) {
                        // Package mode: C&F = Car Price (package_price column was removed)
                        carPrice
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
            carList = carList,
            calculationMode = request.calculationMode // Pass calculation mode to PDF data
        )
    }
    
}
