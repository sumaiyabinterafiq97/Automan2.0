package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.service.PurchaseService
import com.automan.backend.service.ClientService
import com.automan.backend.repository.EventRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.math.BigDecimal

@RestController
@RequestMapping("/purchases")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class PurchaseController(
    private val purchaseService: PurchaseService,
    private val clientService: ClientService,
    private val eventRepository: EventRepository,
    private val pdfService: com.automan.backend.service.PdfService
) {
    
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
    
    @GetMapping("/countries")
    fun getCountries(): ResponseEntity<List<String>> {
        val countries = purchaseService.getUniqueCountries()
        return ResponseEntity.ok(countries)
    }
    
    @GetMapping("/stock-locations")
    fun getStockLocations(): ResponseEntity<List<String>> {
        val stockLocations = purchaseService.getUniqueStockLocations()
        return ResponseEntity.ok(stockLocations)
    }
    
    @GetMapping("/filtered-chassis")
    fun getFilteredChassis(
        @RequestParam country: String,
        @RequestParam polPort: String
    ): ResponseEntity<List<String>> {
        val chassis = purchaseService.getFilteredChassis(country, polPort)
        return ResponseEntity.ok(chassis)
    }
    
    @PutMapping("/save-costs")
    fun saveCarCostDetails(@RequestBody costData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val chassis = costData["chassis"] as String
            val carPrice = (costData["carPrice"] as? Number)?.toDouble() ?: 0.0
            val auctionFee = (costData["auctionFee"] as? Number)?.toDouble() ?: 0.0
            val rixoPrice = (costData["rixoPrice"] as? Number)?.toDouble() ?: 0.0
            val shippingCharge = (costData["shippingCharge"] as? Number)?.toDouble() ?: 0.0
            val freight = (costData["freight"] as? Number)?.toDouble() ?: 0.0
            val inspectionFee = (costData["inspectionFee"] as? Number)?.toDouble() ?: 0.0
            val repairFee = (costData["repairFee"] as? Number)?.toDouble() ?: 0.0
            val mscCharges = (costData["mscCharges"] as? Number)?.toDouble() ?: 0.0
            val profit = (costData["profit"] as? Number)?.toDouble() ?: 0.0
            val packagePrice = (costData["packagePrice"] as? Number)?.toDouble() ?: 0.0
            val isPackageMode = costData["isPackageMode"] as? Boolean ?: false
            
            println("🔍 DEBUG: Received package data - packagePrice: $packagePrice, isPackageMode: $isPackageMode")
            println("🔍 DEBUG: Full costData: $costData")
            
            purchaseService.saveCarCostDetails(
                chassis, carPrice, auctionFee, rixoPrice, shippingCharge, 
                freight, inspectionFee, repairFee, mscCharges, profit, packagePrice, isPackageMode
            )
            
            ResponseEntity.ok(mapOf("message" to "Car cost details saved successfully", "chassis" to chassis))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save car cost details: ${e.message}"))
        }
    }

    @PostMapping("/save-total-cnf")
    fun saveTotalCnfPriceEndpoint(@RequestBody costData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val chassis = costData["chassis"] as String
            val totalCnfPrice = (costData["totalCnfPrice"] as? Number)?.toDouble() ?: 0.0
            
            println("🔍 DEBUG: Saving total C&F price - chassis: $chassis, totalCnfPrice: $totalCnfPrice")
            
            purchaseService.saveTotalCnfPrice(chassis, totalCnfPrice)
            
            ResponseEntity.ok(mapOf("message" to "Total C&F price saved successfully", "chassis" to chassis, "totalCnfPrice" to totalCnfPrice.toString()))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save total C&F price: ${e.message}"))
        }
    }


    @PutMapping("/save-fob-costs")
    fun saveFobCarCostDetails(@RequestBody costData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val chassis = costData["chassis"] as String
            val carPrice = (costData["carPrice"] as? Number)?.toDouble() ?: 0.0
            val auctionFee = (costData["auctionFee"] as? Number)?.toDouble() ?: 0.0
            val rixoPrice = (costData["rixoPrice"] as? Number)?.toDouble() ?: 0.0
            val shippingCharge = (costData["shippingCharge"] as? Number)?.toDouble() ?: 0.0
            val inspectionFee = (costData["inspectionFee"] as? Number)?.toDouble() ?: 0.0
            val repairFee = (costData["repairFee"] as? Number)?.toDouble() ?: 0.0
            val mscCharges = (costData["mscCharges"] as? Number)?.toDouble() ?: 0.0
            val profit = (costData["profit"] as? Number)?.toDouble() ?: 0.0
            
            purchaseService.saveFobCarCostDetails(
                chassis, carPrice, auctionFee, rixoPrice, shippingCharge, 
                inspectionFee, repairFee, mscCharges, profit
            )
            
            ResponseEntity.ok(mapOf("message" to "FOB cost details saved successfully", "chassis" to chassis))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save FOB cost details: ${e.message}"))
        }
    }
    
    @GetMapping("/sort")
    fun sortPurchases(@RequestParam field: String, @RequestParam order: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.sortPurchases(field, order)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/costs-by-chassis/{chassis}")
    fun getCarCostDetails(@PathVariable chassis: String): ResponseEntity<Map<String, Any>> {
        return try {
            println("DEBUG: Looking for chassis: $chassis")
            val purchase = purchaseService.getPurchaseByChassis(chassis)
            println("DEBUG: Purchase result: $purchase")
            if (purchase != null) {
                println("DEBUG: Purchase found - chassis: ${purchase.chassis}, price: ${purchase.price}, shipmentCharges: ${purchase.shipmentCharges}, miscCharges: ${purchase.miscCharges}, repairCharges: ${purchase.repairCharges}")
                
                // Helper function to safely parse price strings to BigDecimal
                fun parsePrice(priceStr: String?): BigDecimal {
                    if (priceStr == null || priceStr.isBlank()) return BigDecimal.ZERO
                    return try {
                        val cleaned = priceStr.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
                        if (cleaned.isEmpty()) BigDecimal.ZERO else BigDecimal(cleaned)
                    } catch (e: Exception) {
                        println("WARNING: Failed to parse price '$priceStr': ${e.message}")
                        BigDecimal.ZERO
                    }
                }
                
                val costDetails: Map<String, Any> = mapOf(
                    "id" to (purchase.id ?: 0L), // Include purchase ID for chassis uniqueness checking
                    "chassis" to (purchase.chassis ?: ""),
                    "carPrice" to parsePrice(purchase.price),
                    "auctionFee" to parsePrice(purchase.auctionFee),
                    "rixoPrice" to parsePrice(purchase.rixoPrice),
                    "shippingCharge" to parsePrice(purchase.shipmentCharges),
                    "freight" to parsePrice(purchase.freight),
                    "inspectionFee" to parsePrice(purchase.inspectionFee),
                    "repairFee" to parsePrice(purchase.repairCharges),
                    "mscCharges" to parsePrice(purchase.miscCharges),
                    "profit" to (purchase.profit ?: BigDecimal.ZERO)
                )
                println("DEBUG: Cost details prepared: $costDetails")
                ResponseEntity.ok(costDetails)
            } else {
                println("DEBUG: Purchase not found for chassis: $chassis")
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            println("ERROR: Exception in getCarCostDetails: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "error" to (e.message ?: "Unknown error"),
                "stackTrace" to e.stackTraceToString()
            ))
        }
    }
    
    @GetMapping("/test-costs")
    fun testCostsEndpoint(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "message" to "Costs endpoint is working",
            "timestamp" to System.currentTimeMillis()
        ))
    }
    
    @GetMapping("/simple-test")
    fun simpleTest(): ResponseEntity<String> {
        return ResponseEntity.ok("Simple test endpoint working")
    }
    
    @GetMapping("/costs-test")
    fun costsTest(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "message" to "Costs test endpoint working",
            "timestamp" to System.currentTimeMillis(),
            "test" to "success"
        ))
    }
    
    @GetMapping("/purchase/{id}")
    fun getPurchaseById(@PathVariable id: Long): ResponseEntity<Purchase> {
        val purchase = purchaseService.getPurchaseById(id)
        println("🔍 [Controller] getPurchaseById - ID: $id, shaken: ${purchase?.shaken} (type: ${purchase?.shaken?.javaClass?.simpleName})")
        return if (purchase != null) {
            ResponseEntity.ok(purchase)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @PostMapping
    fun createPurchase(@RequestBody purchase: Purchase): ResponseEntity<Purchase> {
        println("🔍 [Controller] Creating purchase - received shaken=${purchase.shaken} (type: ${purchase.shaken?.javaClass?.simpleName})")
        val createdPurchase = purchaseService.createPurchase(purchase)
        println("🔍 [Controller] Created purchase - saved shaken=${createdPurchase.shaken}")
        return ResponseEntity.ok(createdPurchase)
    }
    
    
    @PutMapping("/{id}")
    fun updatePurchase(@PathVariable id: Long, @RequestBody updateData: Map<String, Any>): ResponseEntity<Purchase> {
        println("🔍 [Controller] Updating purchase ID: $id")
        println("🔍 [Controller] Update data received: $updateData")
        println("🔍 [Controller] Update data keys: ${updateData.keys}")
        println("🔍 [Controller] Shaken value in updateData: ${updateData["shaken"]} (type: ${updateData["shaken"]?.javaClass?.simpleName})")
        println("🔍 [Controller] shipmentDate: ${updateData["shipmentDate"]} (type: ${updateData["shipmentDate"]?.javaClass?.simpleName})")
        println("🔍 [Controller] bookingId: ${updateData["bookingId"]} (type: ${updateData["bookingId"]?.javaClass?.simpleName})")
        println("🔍 [Controller] vessel: ${updateData["vessel"]} (type: ${updateData["vessel"]?.javaClass?.simpleName})")
        
        val updatedPurchase = purchaseService.updatePurchasePartial(id, updateData)
        return if (updatedPurchase != null) {
            println("✅ [Controller] Purchase updated successfully:")
            println("   - shipmentDate: ${updatedPurchase.shipmentDate}")
            println("   - bookingId: ${updatedPurchase.bookingId}")
            println("   - vessel: ${updatedPurchase.vessel}")
            ResponseEntity.ok(updatedPurchase)
        } else {
            println("❌ [Controller] Purchase not found or update failed")
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
    
    @PostMapping("/ship")
    fun shipPurchases(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        println("🚢 [Controller] Ship purchases request received: $request")
        val purchaseIds = (request["purchaseIds"] as? List<*>)?.mapNotNull { 
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: emptyList()
        
        if (purchaseIds.isEmpty()) {
            println("❌ [Controller] No purchase IDs provided")
            return ResponseEntity.badRequest().body(mapOf("error" to "No purchase IDs provided"))
        }
        
        println("🚢 [Controller] Marking ${purchaseIds.size} purchases as shipped: $purchaseIds")
        val updatedPurchases = purchaseService.markPurchasesAsShipped(purchaseIds)
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Successfully marked ${updatedPurchases.size} purchase(s) as shipped",
            "updatedCount" to updatedPurchases.size,
            "purchaseIds" to purchaseIds
        ))
    }
    
    @PostMapping("/invoice/generate-pdf")
    fun generateInvoicePdf(@RequestBody request: com.automan.backend.dto.InvoicePdfRequest): ResponseEntity<ByteArray> {
        try {
            println("📄 [Controller] Generating invoice PDF for invoice number: ${request.invoiceNumber}")
            
            val pdfBytes = pdfService.generateInvoicePdf(request)
            
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice_${request.invoiceNumber}.pdf\"")
            headers.contentLength = pdfBytes.size.toLong()
            
            println("✅ [Controller] Invoice PDF generated successfully")
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes)
                
        } catch (e: Exception) {
            println("❌ [Controller] Error generating invoice PDF: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.internalServerError().build()
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
            val missingRixoDataRaw = request["missingRixoData"] as? List<Map<String, Any>>
            println("Controller: Raw request body: $request")
            println("Controller: Raw ids: $idsRaw (type: ${idsRaw?.javaClass?.simpleName})")
            println("Controller: Raw invoice data: $invoiceDataRaw")
            println("Controller: Raw missing Rixo data: $missingRixoDataRaw")
            
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
            
            // Process missing Rixo data
            val missingRixoData = missingRixoDataRaw?.map { item ->
                mapOf(
                    "purchaseId" to (item["purchaseId"]?.toString() ?: ""),
                    "field" to (item["field"]?.toString() ?: ""),
                    "value" to (item["value"]?.toString() ?: "")
                )
            } ?: emptyList()
            
            println("Controller: Final selectedIds: $selectedIds (size: ${selectedIds.size})")
            println("Controller: Invoice data: $invoiceData")
            println("Controller: Missing Rixo data: $missingRixoData")
            println("Controller: Generating Rixo PDF for ${selectedIds.size} purchases")
            
            val pdfBytes = purchaseService.generateRixoPdf(selectedIds, invoiceData, missingRixoData)
            
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
    
    @PostMapping("/rixo-transport-pdf")
    fun generateRixoTransportPdf(@RequestBody request: Map<String, Any>): ResponseEntity<ByteArray> {
        try {
            val idsRaw = request["ids"]
            val transportDataRaw = request["transportData"] as? Map<String, Any>
            println("Controller: Raw Rixo Transport request body: $request")
            println("Controller: Raw ids: $idsRaw (type: ${idsRaw?.javaClass?.simpleName})")
            println("Controller: Raw transport data: $transportDataRaw")
            
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
            
            // Process transport data
            val transportData = transportDataRaw?.mapValues { (_, value) -> 
                value?.toString() ?: ""
            } ?: emptyMap()
            
            // Extract purchase data from transport data
            val purchaseData = transportDataRaw?.get("purchaseData") as? List<Map<String, Any>> ?: emptyList()
            
            println("Controller: Final selectedIds: $selectedIds (size: ${selectedIds.size})")
            println("Controller: Transport data: $transportData")
            println("Controller: Purchase data: $purchaseData")
            println("Controller: Generating Rixo Transport PDF for ${selectedIds.size} purchases")
            
            val pdfBytes = purchaseService.generateRixoTransportPdf(selectedIds, transportData, purchaseData)
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"rixo-transport.pdf\"")
                .body(pdfBytes)
        } catch (e: Exception) {
            println("Controller: Error generating Rixo Transport PDF: ${e.message}")
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
    
    @GetMapping("/filter/auction-house")
    fun filterByAuctionHouse(@RequestParam auctionHouse: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.filterByAuctionHouse(auctionHouse)
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
    
    @PostMapping("/transaction")
    fun createTransaction(@RequestBody transactionData: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            // Extract clientId directly from the payload
            val clientId = (transactionData["clientId"] as? Number)?.toLong() 
                ?: throw IllegalArgumentException("Client ID is required")
            
            println("DEBUG: Creating transaction for client $clientId")
            println("DEBUG: Transaction data: $transactionData")
            
            // Verify client exists
            val client = clientService.getClientById(clientId)
                ?: throw IllegalArgumentException("Client not found: $clientId")
            
            println("DEBUG: Client found: ${client.clientName}")
            
            // Calculate running balance based on client's current balance
            val currentBalance = client.currentBalance
            val transactionPrice = (transactionData["transactionPrice"] as? Number)?.toDouble() ?: 0.0
            val paymentReceived = (transactionData["paymentReceived"] as? Number)?.toDouble() ?: 0.0
            val newBalance = currentBalance + paymentReceived - transactionPrice
            
            println("DEBUG: Current balance: $currentBalance, New balance: $newBalance")
            
            // Create Event object
            val event = Event(
                clientId = clientId,
                eventDate = LocalDate.parse(transactionData["eventDate"] as String),
                eventType = EventType.OTHER,
                eventDescription = transactionData["eventDescription"] as? String,
                quantity = (transactionData["quantity"] as? Number)?.toInt(),
                billNumber = transactionData["billNumber"] as? String,
                transactionPrice = transactionPrice,
                paymentReceived = paymentReceived,
                runningBalance = newBalance
            )
            
            // Save event directly using EventRepository
            val savedEvent = eventRepository.save(event)
            println("DEBUG: Event saved with ID: ${savedEvent.id}")
            
            // Update client balance
            clientService.updateClientBalance(clientId, newBalance)
            println("DEBUG: Client balance updated to: $newBalance")
            
            ResponseEntity.ok(mapOf(
                "success" to true,
                "transactionId" to (savedEvent.id ?: 0L),
                "message" to "Transaction created successfully",
                "runningBalance" to newBalance
            ))
        } catch (e: Exception) {
            println("ERROR: Exception in createTransaction: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(500).body(mapOf(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
    
    @PostMapping("/shipping-schedule/generate-pdf")
    fun generateShippingSchedulePdf(@RequestBody request: com.automan.backend.dto.ShippingSchedulePdfRequest): ResponseEntity<ByteArray> {
        try {
            println("📋 Generating PDF for booking: ${request.bookingNo}")
            
            // Get PDF data from service
            val pdfData = purchaseService.getShippingSchedulePdfData(request)
            
            // Generate PDF
            val pdfBytes = generatePdfDocument(pdfData)
            
            // Set response headers
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shipping_schedule_${request.bookingNo}.pdf\"")
            headers.contentLength = pdfBytes.size.toLong()
            
            println("✅ PDF generated successfully for booking: ${request.bookingNo}")
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes)
                
        } catch (e: Exception) {
            println("❌ Error generating PDF: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.internalServerError().build()
        }
    }
    
    @PostMapping("/fob-shipping-schedule/generate-pdf")
    fun generateFobShippingSchedulePdf(@RequestBody request: com.automan.backend.dto.ShippingSchedulePdfRequest): ResponseEntity<ByteArray> {
        try {
            println("📋 Generating FOB PDF for booking: ${request.bookingNo}")
            
            // Get PDF data from service (same as regular shipping schedule)
            val pdfData = purchaseService.getShippingSchedulePdfData(request)
            
            // Generate FOB PDF (same format but with FOB price column)
            val pdfBytes = generateFobPdfDocument(pdfData)
            
            // Set response headers
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fob_shipping_schedule_${request.bookingNo}.pdf\"")
            headers.contentLength = pdfBytes.size.toLong()
            
            println("✅ FOB PDF generated successfully for booking: ${request.bookingNo}")
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes)
                
        } catch (e: Exception) {
            println("❌ Error generating FOB PDF: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.internalServerError().build()
        }
    }
    
    /**
     * Parse consignee address into separate components: P.O.BOX, location, TEL, E-MAIL
     * Example: "P.O.BOX=86338-80100, MOMBASA-KENYA, TEL:+254724666786, E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"
     * Returns: ["P.O.BOX=86338-80100", "MOMBASA-KENYA", "TEL:+254724666786", "E-MAIL:LAKHANIMOTORS.KENYA@YAHOO.COM"]
     */
    private fun parseConsigneeAddress(address: String): List<String> {
        val components = mutableListOf<String>()
        val trimmedAddress = address.trim()
        
        if (trimmedAddress.isEmpty()) {
            return components
        }
        
        // Split by comma, but preserve parts that contain "=" (like P.O.BOX=...)
        val parts = trimmedAddress.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        var poBox: String? = null
        var location: String? = null
        var tel: String? = null
        var email: String? = null
        
        parts.forEach { part ->
            when {
                part.contains("P.O.BOX", ignoreCase = true) || part.contains("P.O. BOX", ignoreCase = true) -> {
                    poBox = part
                }
                part.contains("TEL:", ignoreCase = true) -> {
                    tel = part
                }
                part.contains("E-MAIL:", ignoreCase = true) || part.contains("EMAIL:", ignoreCase = true) || part.contains("@") -> {
                    email = part
                }
                else -> {
                    // This is likely the location (city, country)
                    if (location == null) {
                        location = part
                    } else {
                        // If there's already a location, append it (for multi-part locations)
                        location = "$location, $part"
                    }
                }
            }
        }
        
        // Add components in order: P.O.BOX, location, TEL, E-MAIL
        if (poBox != null) components.add(poBox!!)
        if (location != null) components.add(location!!)
        if (tel != null) components.add(tel!!)
        if (email != null) components.add(email!!)
        
        // If no structured parsing worked, return the original address as a single component
        if (components.isEmpty()) {
            components.add(trimmedAddress)
        }
        
        return components
    }
    
    private fun generatePdfDocument(pdfData: com.automan.backend.dto.ShippingSchedulePdfData): ByteArray {
        println("🔥🔥🔥 GENERATING PDF WITH TABLE FORMAT - UPDATED CODE - ${System.currentTimeMillis()} 🔥🔥🔥")
        val outputStream = java.io.ByteArrayOutputStream()
        val pdfWriter = com.itextpdf.kernel.pdf.PdfWriter(outputStream)
        val pdfDocument = com.itextpdf.kernel.pdf.PdfDocument(pdfWriter)
        val document = com.itextpdf.layout.Document(pdfDocument)
        
        // Set page margins
        document.setMargins(50f, 50f, 50f, 50f)
        
        // Title
        val title = com.itextpdf.layout.element.Paragraph("SHIPPING SCHEDULE (BOOKING CHASIS NO. LIST)")
            .setFontSize(16f)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20f)
        document.add(title)
        
        // Company name
        val companyName = com.itextpdf.layout.element.Paragraph(pdfData.companyName)
            .setFontSize(14f)
            .setBold()
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(30f)
        document.add(companyName)
        
        // Booking details section - using table format with row-spanning SHIPPING DATE cell
        val bookingTable = com.itextpdf.layout.element.Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(floatArrayOf(1f, 2f, 1f, 1f)))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
        
        // Row 1: BOOKING NO, BOOKING NO VALUE, SHIPPING DATE (spans 2 rows), SHIPPING DATE VALUE (spans 2 rows)
        bookingTable.addCell(createCellWithBorder("BOOKING NO:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.bookingNo, false))
        val shippingDateLabelCell = createCellWithBorder("SHIPPING DATE:", true)
        shippingDateLabelCell.setProperty(com.itextpdf.layout.properties.Property.ROWSPAN, 2)
        bookingTable.addCell(shippingDateLabelCell)
        val shippingDateValueCell = createCellWithBorder(pdfData.shippingDate, false)
        shippingDateValueCell.setProperty(com.itextpdf.layout.properties.Property.ROWSPAN, 2)
        bookingTable.addCell(shippingDateValueCell)
        
        // Row 2: VESSEL NAME, VESSEL NAME VALUE (SHIPPING DATE cells continue from row 1)
        bookingTable.addCell(createCellWithBorder("VESSEL NAME:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.vesselName, false))
        
        // Row 3: POL, POL VALUE, POD, POD VALUE
        bookingTable.addCell(createCellWithBorder("POL:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.pol, false))
        bookingTable.addCell(createCellWithBorder("POD:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.pod, false))
        
        document.add(bookingTable)
        
        // Car list table
        val carTable = com.itextpdf.layout.element.Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(floatArrayOf(0.5f, 2f, 2.5f, 1f, 2f)))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // Table headers
        carTable.addHeaderCell(createHeaderCell("No."))
        carTable.addHeaderCell(createHeaderCell("NAME"))
        carTable.addHeaderCell(createHeaderCell("CHASIS NUMBER"))
        carTable.addHeaderCell(createHeaderCell("YEAR"))
        carTable.addHeaderCell(createHeaderCell("C&F PRICE"))
        
        // Add car data rows
        pdfData.carList.forEach { car ->
            carTable.addCell(createCell(car.no.toString(), false))
            carTable.addCell(createCell(car.name, false))
            carTable.addCell(createCell(car.chassisNumber, false))
            carTable.addCell(createCell(car.year, false))
            carTable.addCell(createCell(car.cnfPrice, false))
        }
        
        document.add(carTable)
        
        // Consignee section - horizontal table with label on top, name (bold) and address underneath
        val consigneeTable = com.itextpdf.layout.element.Table(com.itextpdf.layout.properties.UnitValue.createPercentArray(floatArrayOf(1f)))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(70f)) // Wider table to prevent wrapping
            .setMarginTop(30f)
            .setMarginBottom(20f)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
        
        // Add consignee label row (first row) - center aligned
        consigneeTable.addCell(createCellWithBorderCentered("CONSIGNEE:", true))
        
        // Add consignee name row (second row) - bold, center aligned
        val consigneeName = pdfData.consigneeDetails.name ?: ""
        println("🔍 PDF Generation - Consignee Name: '$consigneeName'")
        println("🔍 PDF Generation - Consignee Name length: ${consigneeName.length}")
        println("🔍 PDF Generation - Consignee Name contains newline: ${consigneeName.contains("\n")}")
        
        val consigneeNameText = com.itextpdf.layout.element.Text(consigneeName)
            .setBold()
            .setFontSize(11f)
        val consigneeNameParagraph = com.itextpdf.layout.element.Paragraph()
            .add(consigneeNameText)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
        val consigneeNameCell = com.itextpdf.layout.element.Cell()
            .add(consigneeNameParagraph)
            .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            .setBorderTop(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER) // Remove bottom border to eliminate horizontal line
            .setPadding(8f)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
        consigneeTable.addCell(consigneeNameCell)
        
        // Add consignee address rows - split into separate lines for P.O.BOX, location, TEL, E-MAIL
        val consigneeAddress = pdfData.consigneeDetails.address ?: ""
        println("🔍 PDF Generation - Consignee Address: '$consigneeAddress'")
        println("🔍 PDF Generation - Consignee Address length: ${consigneeAddress.length}")
        println("🔍 PDF Generation - Consignee Address is empty: ${consigneeAddress.isEmpty()}")
        
        if (consigneeAddress.isNotEmpty()) {
            // Parse address into components: P.O.BOX, location, TEL, E-MAIL
            val addressComponents = parseConsigneeAddress(consigneeAddress)
            println("📋 Parsed address components: $addressComponents")
            
            // Add each component as a separate row
            addressComponents.forEach { component ->
                if (component.isNotEmpty()) {
                    val componentText = com.itextpdf.layout.element.Text(component)
                        .setFontSize(10f)
                    val componentParagraph = com.itextpdf.layout.element.Paragraph()
                        .add(componentText)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    val componentCell = com.itextpdf.layout.element.Cell()
                        .add(componentParagraph)
                        .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                        .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                        .setBorderTop(com.itextpdf.layout.borders.Border.NO_BORDER) // Remove top border between rows
                        .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER) // Remove bottom border between rows
                        .setPadding(4f)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    consigneeTable.addCell(componentCell)
                }
            }
            
            // Add bottom border to the last address row
            if (addressComponents.isNotEmpty()) {
                val lastCellIndex = consigneeTable.numberOfRows - 1
                val lastCell = consigneeTable.getCell(lastCellIndex, 0)
                lastCell.setBorderBottom(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            }
        } else {
            println("⚠️ PDF Generation - Consignee address is empty, not adding address row")
        }
        
        document.add(consigneeTable)
        
        document.close()
        return outputStream.toByteArray()
    }
    
    private fun generateFobPdfDocument(pdfData: com.automan.backend.dto.ShippingSchedulePdfData): ByteArray {
        println("🔥🔥🔥 GENERATING FOB PDF WITH TABLE FORMAT - UPDATED CODE - ${System.currentTimeMillis()} 🔥🔥🔥")
        val outputStream = java.io.ByteArrayOutputStream()
        val pdfWriter = com.itextpdf.kernel.pdf.PdfWriter(outputStream)
        val pdfDocument = com.itextpdf.kernel.pdf.PdfDocument(pdfWriter)
        val document = com.itextpdf.layout.Document(pdfDocument)
        
        // Title
        val title = com.itextpdf.layout.element.Paragraph("SHIPPING SCHEDULE (BOOKING CHASIS NO. LIST)")
            .setBold()
            .setFontSize(18f)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(20f)
        document.add(title)
        
        // Company name
        val companyName = com.itextpdf.layout.element.Paragraph("MEMON CO., LTD")
            .setFontSize(14f)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
            .setMarginBottom(30f)
        document.add(companyName)
        
        // Booking Details Table (same format as regular PDF)
        val bookingTable = com.itextpdf.layout.element.Table(floatArrayOf(1f, 2f, 1f, 1f))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
        
        // Row 1
        bookingTable.addCell(createCellWithBorder("BOOKING NO:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.bookingNo, false))
        val shippingDateLabelCell = createCellWithBorder("SHIPPING DATE:", true)
        shippingDateLabelCell.setProperty(com.itextpdf.layout.properties.Property.ROWSPAN, 2)
        bookingTable.addCell(shippingDateLabelCell)
        val shippingDateValueCell = createCellWithBorder(pdfData.shippingDate, false)
        shippingDateValueCell.setProperty(com.itextpdf.layout.properties.Property.ROWSPAN, 2)
        bookingTable.addCell(shippingDateValueCell)
        
        // Row 2
        bookingTable.addCell(createCellWithBorder("VESSEL NAME:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.vesselName, false))
        
        // Row 3
        bookingTable.addCell(createCellWithBorder("POL:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.pol, false))
        bookingTable.addCell(createCellWithBorder("POD:", true))
        bookingTable.addCell(createCellWithBorder(pdfData.pod, false))
        
        document.add(bookingTable)
        
        // Add some space
        document.add(com.itextpdf.layout.element.Paragraph().setMarginBottom(20f))
        
        // Car List Table (FOB version - with FOB PRICE column instead of C&F PRICE)
        val carListTable = com.itextpdf.layout.element.Table(floatArrayOf(1f, 2f, 3f, 1f, 2f))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(100f))
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
        
        // Header row with gray background
        carListTable.addCell(createHeaderCell("No."))
        carListTable.addCell(createHeaderCell("NAME"))
        carListTable.addCell(createHeaderCell("CHASIS NUMBER"))
        carListTable.addCell(createHeaderCell("YEAR"))
        carListTable.addCell(createHeaderCell("FOB PRICE")) // Changed from "C&F PRICE" to "FOB PRICE"
        
        // Data rows
        pdfData.carList.forEachIndexed { index, car ->
            carListTable.addCell(createCellWithBorder((index + 1).toString(), false))
            carListTable.addCell(createCellWithBorder(car.name, false))
            carListTable.addCell(createCellWithBorder(car.chassisNumber, false))
            carListTable.addCell(createCellWithBorder(car.year, false))
            // Remove any existing ¥ symbol and add single ¥ with right alignment
            val priceWithoutSymbol = car.cnfPrice.toString().replace("¥", "").trim()
            val priceCell = createCellWithBorder("¥$priceWithoutSymbol", false)
            priceCell.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
            carListTable.addCell(priceCell) // Single ¥ symbol for FOB price with right alignment
        }
        
        document.add(carListTable)
        
        // Add some space
        document.add(com.itextpdf.layout.element.Paragraph().setMarginBottom(20f))
        
        // Consignee Table (moved after Car List Table)
        val consigneeTable = com.itextpdf.layout.element.Table(floatArrayOf(1f))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(70f)) // Wider table to prevent wrapping
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
        
        consigneeTable.addCell(createCellWithBorderCentered("CONSIGNEE:", true))
        
        // Add consignee name row - bold, center aligned
        val consigneeName = pdfData.consigneeDetails.name ?: ""
        val consigneeNameText = com.itextpdf.layout.element.Text(consigneeName)
            .setBold()
            .setFontSize(11f)
        val consigneeNameParagraph = com.itextpdf.layout.element.Paragraph()
            .add(consigneeNameText)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
        val consigneeNameCell = com.itextpdf.layout.element.Cell()
            .add(consigneeNameParagraph)
            .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            .setBorderTop(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER) // Remove bottom border to eliminate horizontal line
            .setPadding(8f)
            .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
        consigneeTable.addCell(consigneeNameCell)
        
        // Add consignee address rows - split into separate lines for P.O.BOX, location, TEL, E-MAIL
        val consigneeAddress = pdfData.consigneeDetails.address ?: ""
        if (consigneeAddress.isNotEmpty()) {
            // Parse address into components: P.O.BOX, location, TEL, E-MAIL
            val addressComponents = parseConsigneeAddress(consigneeAddress)
            println("📋 FOB PDF - Parsed address components: $addressComponents")
            
            // Add each component as a separate row
            addressComponents.forEach { component ->
                if (component.isNotEmpty()) {
                    val componentText = com.itextpdf.layout.element.Text(component)
                        .setFontSize(10f)
                    val componentParagraph = com.itextpdf.layout.element.Paragraph()
                        .add(componentText)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    val componentCell = com.itextpdf.layout.element.Cell()
                        .add(componentParagraph)
                        .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                        .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                        .setBorderTop(com.itextpdf.layout.borders.Border.NO_BORDER) // Remove top border between rows
                        .setBorderBottom(com.itextpdf.layout.borders.Border.NO_BORDER) // Remove bottom border between rows
                        .setPadding(4f)
                        .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    consigneeTable.addCell(componentCell)
                }
            }
            
            // Add bottom border to the last address row
            if (addressComponents.isNotEmpty()) {
                val lastCellIndex = consigneeTable.numberOfRows - 1
                val lastCell = consigneeTable.getCell(lastCellIndex, 0)
                lastCell.setBorderBottom(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
            }
        }
        
        document.add(consigneeTable)
        
        document.close()
        return outputStream.toByteArray()
    }
    
    private fun createCell(text: String, isBold: Boolean): com.itextpdf.layout.element.Cell {
        val paragraph = if (isBold) {
            com.itextpdf.layout.element.Paragraph(text).setBold().setFontSize(10f)
        } else {
            com.itextpdf.layout.element.Paragraph(text).setFontSize(10f)
        }
        return com.itextpdf.layout.element.Cell().add(paragraph)
    }
    
    private fun createCellWithBorder(text: String, isBold: Boolean): com.itextpdf.layout.element.Cell {
        val paragraph = if (isBold) {
            com.itextpdf.layout.element.Paragraph(text).setBold().setFontSize(10f)
        } else {
            com.itextpdf.layout.element.Paragraph(text).setFontSize(10f)
        }
        return com.itextpdf.layout.element.Cell()
            .add(paragraph)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
            .setPadding(8f)
            .setMargin(0f)
    }
    
    private fun createCellWithBorderCentered(text: String, isBold: Boolean): com.itextpdf.layout.element.Cell {
        val paragraph = if (isBold) {
            com.itextpdf.layout.element.Paragraph(text).setBold().setFontSize(10f).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
        } else {
            com.itextpdf.layout.element.Paragraph(text).setFontSize(10f).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
        }
        return com.itextpdf.layout.element.Cell()
            .add(paragraph)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
            .setPadding(8f)
            .setMargin(0f)
    }
    
    private fun createHeaderCell(text: String): com.itextpdf.layout.element.Cell {
        return com.itextpdf.layout.element.Cell()
            .add(com.itextpdf.layout.element.Paragraph(text).setBold().setFontSize(10f))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
    }
}
