package com.automan.backend.controller

import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.service.PurchaseService
import com.automan.backend.service.ClientService
import com.automan.backend.service.TransactionService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

@RestController
@RequestMapping("/purchases")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class PurchaseController(
    private val purchaseService: PurchaseService,
    private val clientService: ClientService,
    private val transactionService: TransactionService,
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
    
    @GetMapping("/chassis/{chassis}")
    fun getPurchaseByChassis(@PathVariable chassis: String): ResponseEntity<Purchase> {
        return try {
            val purchase = purchaseService.getPurchaseByChassis(chassis)
            if (purchase != null) {
                ResponseEntity.ok(purchase)
            } else {
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            Logger.error("Error fetching purchase by chassis '$chassis': ${e.message}", e)
            ResponseEntity.status(500).build()
        }
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
    
    @GetMapping("/stock-locations-by-country")
    fun getStockLocationsByCountry(@RequestParam country: String): ResponseEntity<List<String>> {
        val stockLocations = purchaseService.getStockLocationsByCountry(country)
        return ResponseEntity.ok(stockLocations)
    }
    
    @GetMapping("/pols-by-country")
    fun getPolsByCountry(@RequestParam country: String): ResponseEntity<List<String>> {
        val pols = purchaseService.getPolByCountry(country)
        return ResponseEntity.ok(pols)
    }
    
    @GetMapping("/rixo-companies")
    fun getRixoCompanies(): ResponseEntity<List<String>> {
        val companies = purchaseService.getUniqueRixoCompanies()
        return ResponseEntity.ok(companies)
    }
    
    @GetMapping("/repair-companies")
    fun getRepairCompanies(): ResponseEntity<List<String>> {
        val companies = purchaseService.getUniqueRepairCompanies()
        return ResponseEntity.ok(companies)
    }
    
    @GetMapping("/venue-ids")
    fun getVenueIds(): ResponseEntity<List<String>> {
        val venueIds = purchaseService.getUniqueVenueIds()
        return ResponseEntity.ok(venueIds)
    }
    
    @GetMapping("/filtered-chassis")
    fun getFilteredChassis(
        @RequestParam country: String,
        @RequestParam polPort: String
    ): ResponseEntity<List<String>> {
        val chassis = purchaseService.getFilteredChassis(country, polPort)
        return ResponseEntity.ok(chassis)
    }

    @GetMapping("/filtered-purchases")
    fun getFilteredPurchases(
        @RequestParam country: String,
        @RequestParam polPort: String
    ): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.getFilteredPurchasesByCountryAndPol(country, polPort)
        return ResponseEntity.ok(purchases)
    }
    
    @GetMapping("/unshipped-chassis")
    fun getUnshippedChassisByPol(
        @RequestParam pol: String
    ): ResponseEntity<List<String>> {
        val chassis = purchaseService.getUnshippedChassisByPolPort(pol)
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
            val isPackageMode = costData["isPackageMode"] as? Boolean ?: false
            
            Logger.debug("Received cost data - isPackageMode: $isPackageMode")
            Logger.debug("Full costData: $costData")
            
            purchaseService.saveCarCostDetails(
                chassis, carPrice, auctionFee, rixoPrice, shippingCharge, 
                freight, inspectionFee, repairFee, mscCharges, profit, isPackageMode
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
            
            Logger.debug("Saving total C&F price - chassis: $chassis, totalCnfPrice: $totalCnfPrice")
            
            purchaseService.saveTotalCnfPrice(chassis, totalCnfPrice)
            
            ResponseEntity.ok(mapOf("message" to "Total C&F price saved successfully", "chassis" to chassis, "totalCnfPrice" to totalCnfPrice.toString()))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save total C&F price: ${e.message}"))
        }
    }
    
    @PostMapping("/save-total-cnf-by-ids")
    fun saveTotalCnfPriceByPurchaseIds(@RequestBody requestData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val purchaseIds = (requestData["purchaseIds"] as? List<*>)?.mapNotNull { 
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull()
                    else -> null
                }
            } ?: emptyList()
            val totalCnfPrice = (requestData["totalCnfPrice"] as? Number)?.toDouble() ?: 0.0
            
            Logger.debug("Saving total C&F price by purchase IDs - purchaseIds: $purchaseIds, totalCnfPrice: $totalCnfPrice")
            
            purchaseService.saveTotalCnfPriceByPurchaseIds(purchaseIds, totalCnfPrice)
            
            ResponseEntity.ok(mapOf("message" to "Total C&F price saved successfully", "count" to purchaseIds.size.toString(), "totalCnfPrice" to totalCnfPrice.toString()))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save total C&F price: ${e.message}"))
        }
    }

    @PostMapping("/save-total-fob-by-ids")
    fun saveTotalFobPriceByPurchaseIds(@RequestBody requestData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val purchaseIds = (requestData["purchaseIds"] as? List<*>)?.mapNotNull {
                when (it) {
                    is Number -> it.toLong()
                    is String -> it.toLongOrNull()
                    else -> null
                }
            } ?: emptyList()
            val totalFobPrice = (requestData["totalFobPrice"] as? Number)?.toDouble() ?: 0.0

            Logger.debug("Saving total FOB price by purchase IDs - purchaseIds: $purchaseIds, totalFobPrice: $totalFobPrice")

            purchaseService.saveTotalFobPriceByPurchaseIds(purchaseIds, totalFobPrice)

            ResponseEntity.ok(
                mapOf(
                    "message" to "Total FOB price saved successfully",
                    "count" to purchaseIds.size.toString(),
                    "totalFobPrice" to totalFobPrice.toString()
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save total FOB price: ${e.message}"))
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
            Logger.debug("Looking for chassis: $chassis")
            val purchase = purchaseService.getPurchaseByChassis(chassis)
            Logger.debug("Purchase result found")
            if (purchase != null) {
                Logger.debug("Purchase found - chassis: ${purchase.chassis}, price: ${purchase.price}")
                
                // Helper function to safely parse price strings to BigDecimal
                fun parsePrice(priceStr: String?): BigDecimal {
                    if (priceStr == null || priceStr.isBlank()) return BigDecimal.ZERO
                    return try {
                        val cleaned = priceStr.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
                        if (cleaned.isEmpty()) BigDecimal.ZERO else BigDecimal(cleaned)
                    } catch (e: Exception) {
                        Logger.warn("Failed to parse price '$priceStr': ${e.message}")
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
                Logger.debug("Cost details prepared")
                ResponseEntity.ok(costDetails)
            } else {
                Logger.debug("Purchase not found for chassis: $chassis")
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            Logger.error("Exception in getCarCostDetails: ${e.message}", e)
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
        Logger.debug("[Controller] getPurchaseById - ID: $id, shaken: ${purchase?.shaken}")
        return if (purchase != null) {
            ResponseEntity.ok(purchase)
        } else {
            ResponseEntity.notFound().build()
        }
    }
    
    @PostMapping
    fun createPurchase(@RequestBody purchase: Purchase): ResponseEntity<Purchase> {
        Logger.debug("[Controller] Creating purchase - received shaken=${purchase.shaken}")
        val createdPurchase = purchaseService.createPurchase(purchase)
        Logger.debug("[Controller] Created purchase - saved shaken=${createdPurchase.shaken}")
        return ResponseEntity.ok(createdPurchase)
    }
    
    
    @PutMapping("/{id}")
    fun updatePurchase(@PathVariable id: Long, @RequestBody updateData: Map<String, Any>): ResponseEntity<Any> {
        Logger.debug("[Controller] Updating purchase ID: $id")
        Logger.debug("[Controller] Update data keys: ${updateData.keys}")
        Logger.debug("[Controller] Update data values: $updateData")
        
        return try {
            val updatedPurchase = purchaseService.updatePurchasePartial(id, updateData)
            if (updatedPurchase != null) {
                Logger.debug("[Controller] Purchase updated successfully - shipmentDate: ${updatedPurchase.shipmentDate}, bookingId: ${updatedPurchase.bookingId}, vessel: ${updatedPurchase.vessel}, totalCnfPrice: ${updatedPurchase.totalCnfPrice}, auctionHouse: ${updatedPurchase.auctionHouse}")
                ResponseEntity.ok(updatedPurchase)
            } else {
                Logger.error("[Controller] Purchase not found or update failed")
                ResponseEntity.status(404).body(mapOf("error" to "Purchase with ID $id not found"))
            }
        } catch (e: IllegalArgumentException) {
            Logger.error("[Controller] Validation error updating purchase: ${e.message}", e)
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Validation error: ${e.javaClass.simpleName}")))
        } catch (e: Exception) {
            Logger.error("[Controller] Error updating purchase: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf("error" to "Failed to update purchase: ${e.message ?: e.javaClass.simpleName}"))
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
        Logger.debug("[Controller] Ship purchases request received")
        val purchaseIds = (request["purchaseIds"] as? List<*>)?.mapNotNull { 
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: emptyList()
        
        if (purchaseIds.isEmpty()) {
            Logger.error("[Controller] No purchase IDs provided")
            return ResponseEntity.badRequest().body(mapOf("error" to "No purchase IDs provided"))
        }
        
        Logger.debug("[Controller] Marking ${purchaseIds.size} purchases as shipped")
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
            Logger.debug("[Controller] Generating invoice PDF for invoice number: ${request.invoiceNumber}")
            
            val pdfBytes = pdfService.generateInvoicePdf(request)
            
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice_${request.invoiceNumber}.pdf\"")
            headers.contentLength = pdfBytes.size.toLong()
            
            Logger.debug("[Controller] Invoice PDF generated successfully")
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes)
                
        } catch (e: Exception) {
            Logger.error("[Controller] Error generating invoice PDF: ${e.message}", e)
            return ResponseEntity.internalServerError().build()
        }
    }
    
    @PostMapping("/import")
    fun importPurchases(@RequestParam("file") file: MultipartFile): ResponseEntity<ImportResponse> {
        try {
            Logger.debug("Controller: Received file: ${file.originalFilename}, size: ${file.size}")
            val importResponse = purchaseService.importPurchases(file)
            Logger.debug("Controller: ${importResponse.message}")
            return ResponseEntity.ok(importResponse)
        } catch (e: Exception) {
            Logger.error("Controller: Error during import: ${e.message}", e)
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
            Logger.debug("Controller: Raw request body: $request")
            Logger.debug("Controller: Raw ids: $idsRaw (type: ${idsRaw?.javaClass?.simpleName})")
            Logger.debug("Controller: Raw invoice data: $invoiceDataRaw")
            Logger.debug("Controller: Raw missing Rixo data: $missingRixoDataRaw")
            
            val selectedIds = when (idsRaw) {
                is List<*> -> {
                    Logger.debug("Controller: Processing List with ${idsRaw.size} items")
                    idsRaw.mapNotNull { item ->
                        Logger.debug("Controller: Processing item: $item (type: ${item?.javaClass?.simpleName})")
                        when (item) {
                            is Number -> {
                                val longValue = item.toLong()
                                Logger.debug("Controller: Converted Number $item to Long $longValue")
                                longValue
                            }
                            is String -> {
                                val longValue = item.toLongOrNull()
                                Logger.debug("Controller: Converted String '$item' to Long $longValue")
                                longValue
                            }
                            else -> {
                                Logger.warn("Controller: Unknown item type: ${item?.javaClass?.simpleName}")
                                null
                            }
                        }
                    }
                }
                else -> {
                    Logger.warn("Controller: idsRaw is not a List, it's: ${idsRaw?.javaClass?.simpleName}")
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
            
            Logger.debug("Controller: Final selectedIds: $selectedIds (size: ${selectedIds.size})")
            Logger.debug("Controller: Invoice data: $invoiceData")
            Logger.debug("Controller: Missing Rixo data: $missingRixoData")
            Logger.debug("Controller: Generating Rixo PDF for ${selectedIds.size} purchases")
            
            val pdfBytes = purchaseService.generateRixoPdf(selectedIds, invoiceData, missingRixoData)
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"rixo-purchases.pdf\"")
                .body(pdfBytes)
        } catch (e: Exception) {
            Logger.error("Controller: Error generating Rixo PDF: ${e.message}", e)
            return ResponseEntity.status(500).build()
        }
    }
    
    @PostMapping("/rixo-transport-pdf")
    fun generateRixoTransportPdf(@RequestBody request: Map<String, Any>): ResponseEntity<ByteArray> {
        try {
            val idsRaw = request["ids"]
            val transportDataRaw = request["transportData"] as? Map<String, Any>
            Logger.debug("Controller: Raw Rixo Transport request body: $request")
            Logger.debug("Controller: Raw ids: $idsRaw (type: ${idsRaw?.javaClass?.simpleName})")
            Logger.debug("Controller: Raw transport data: $transportDataRaw")
            
            val selectedIds = when (idsRaw) {
                is List<*> -> {
                    Logger.debug("Controller: Processing List with ${idsRaw.size} items")
                    idsRaw.mapNotNull { item ->
                        Logger.debug("Controller: Processing item: $item (type: ${item?.javaClass?.simpleName})")
                        when (item) {
                            is Number -> {
                                val longValue = item.toLong()
                                Logger.debug("Controller: Converted Number $item to Long $longValue")
                                longValue
                            }
                            is String -> {
                                val longValue = item.toLongOrNull()
                                Logger.debug("Controller: Converted String '$item' to Long $longValue")
                                longValue
                            }
                            else -> {
                                Logger.warn("Controller: Unknown item type: ${item?.javaClass?.simpleName}")
                                null
                            }
                        }
                    }
                }
                else -> {
                    Logger.warn("Controller: idsRaw is not a List, it's: ${idsRaw?.javaClass?.simpleName}")
                    emptyList()
                }
            }
            
            // Process transport data
            val transportData = transportDataRaw?.mapValues { (_, value) -> 
                value?.toString() ?: ""
            } ?: emptyMap()
            
            // Extract purchase data from transport data
            val purchaseData = transportDataRaw?.get("purchaseData") as? List<Map<String, Any>> ?: emptyList()
            
            Logger.debug("Controller: Final selectedIds: $selectedIds (size: ${selectedIds.size})")
            Logger.debug("Controller: Transport data: $transportData")
            Logger.debug("Controller: Purchase data: $purchaseData")
            Logger.debug("Controller: Generating Rixo Transport PDF for ${selectedIds.size} purchases")
            
            val pdfBytes = purchaseService.generateRixoTransportPdf(selectedIds, transportData, purchaseData)
            
            return ResponseEntity.ok()
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "attachment; filename=\"rixo-transport.pdf\"")
                .body(pdfBytes)
        } catch (e: Exception) {
            Logger.error("Controller: Error generating Rixo Transport PDF: ${e.message}", e)
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
    
    @GetMapping("/filter/invoice")
    fun filterForInvoice(
        @RequestParam(required = false) consignee: String?,
        @RequestParam(required = false) vessel: String?,
        @RequestParam(required = false) shipmentDate: String?
    ): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.filterByConsigneeAndVesselAndShipmentDate(consignee, vessel, shipmentDate)
        return ResponseEntity.ok(purchases)
    }
    
    @PostMapping("/transaction")
    fun createTransaction(@RequestBody transactionData: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            val request = CreateTransactionRequest(
                clientId = (transactionData["clientId"] as? Number)?.toLong()
                    ?: throw IllegalArgumentException("Client ID is required"),
                eventDate = transactionData["eventDate"] as String,
                eventDescription = transactionData["eventDescription"] as? String ?: "",
                quantity = (transactionData["quantity"] as? Number)?.toInt(),
                billNumber = transactionData["billNumber"] as? String,
                transactionPrice = (transactionData["transactionPrice"] as? Number)?.toDouble(),
                paymentReceived = (transactionData["paymentReceived"] as? Number)?.toDouble()
            )
            val response = transactionService.createTransaction(request)
            if (response.success) {
                ResponseEntity.ok(mapOf<String, Any>(
                    "success" to true,
                    "transactionId" to (response.transactionId ?: 0L),
                    "message" to response.message,
                    "runningBalance" to (response.runningBalance ?: 0.0)
                ))
            } else {
                ResponseEntity.status(500).body(mapOf<String, Any>(
                    "success" to false,
                    "error" to response.message
                ))
            }
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf<String, Any>(
                "success" to false,
                "error" to (e.message ?: "Invalid request")
            ))
        } catch (e: Exception) {
            Logger.error("Exception in createTransaction: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf<String, Any>(
                "success" to false,
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
    
    @PostMapping("/shipping-schedule/generate-pdf")
    fun generateShippingSchedulePdf(@RequestBody request: com.automan.backend.dto.ShippingSchedulePdfRequest): ResponseEntity<ByteArray> {
        try {
            Logger.debug("Generating PDF for booking: ${request.bookingNo}")
            
            // Get PDF data from service
            val pdfData = purchaseService.getShippingSchedulePdfData(request)
            
            // Generate PDF
            val pdfBytes = generatePdfDocument(pdfData)
            
            // Set response headers
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shipping_schedule_${request.bookingNo}.pdf\"")
            headers.contentLength = pdfBytes.size.toLong()
            
            Logger.debug("PDF generated successfully for booking: ${request.bookingNo}")
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes)
                
        } catch (e: Exception) {
            Logger.error("Error generating PDF: ${e.message}", e)
            return ResponseEntity.internalServerError().build()
        }
    }
    
    @PostMapping("/fob-shipping-schedule/generate-pdf")
    fun generateFobShippingSchedulePdf(@RequestBody request: com.automan.backend.dto.ShippingSchedulePdfRequest): ResponseEntity<ByteArray> {
        try {
            Logger.debug("Generating FOB PDF for booking: ${request.bookingNo}")
            
            // Get PDF data from service (same as regular shipping schedule)
            val pdfData = purchaseService.getShippingSchedulePdfData(request)
            
            // Generate FOB PDF (same format but with FOB price column)
            val pdfBytes = generateFobPdfDocument(pdfData)
            
            // Set response headers
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fob_shipping_schedule_${request.bookingNo}.pdf\"")
            headers.contentLength = pdfBytes.size.toLong()
            
            Logger.debug("FOB PDF generated successfully for booking: ${request.bookingNo}")
            return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes)
                
        } catch (e: Exception) {
            Logger.error("Error generating FOB PDF: ${e.message}", e)
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
        Logger.debug("GENERATING PDF WITH TABLE FORMAT - UPDATED CODE - ${System.currentTimeMillis()}")
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
        // Use calculationMode to determine column header (C&F PRICE or FOB PRICE)
        val calculationMode = pdfData.calculationMode?.trim()?.uppercase() ?: ""
        Logger.debug("PDF Generation - Calculation Mode: '$calculationMode' (original: '${pdfData.calculationMode}')")
        val priceColumnHeader = when {
            calculationMode == "FOB" -> "FOB PRICE"
            calculationMode == "C&F" || calculationMode == "CNF" -> "C&F PRICE"
            else -> {
                Logger.warn("Unknown calculation mode: '$calculationMode', defaulting to C&F PRICE")
                "C&F PRICE"
            }
        }
        Logger.debug("PDF Generation - Using column header: '$priceColumnHeader'")
        carTable.addHeaderCell(createHeaderCell(priceColumnHeader))
        
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
        
        // Get consignee name value (contains full consignee details)
        val consigneeName = pdfData.consigneeDetails.name ?: ""
        Logger.debug("PDF Generation - Consignee Name: '$consigneeName'")
        Logger.debug("PDF Generation - Consignee Name length: ${consigneeName.length}")
        
        if (consigneeName.isNotEmpty()) {
            // Split consignee name by commas and add each part as a separate row
            val consigneeParts = consigneeName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            Logger.debug("Split consignee into ${consigneeParts.size} parts: $consigneeParts")
            
            // Add each comma-separated part as a separate row (center-aligned, not bold)
            consigneeParts.forEachIndexed { index, part ->
                Logger.debug("Processing consignee part $index: '$part'")
                
                val partText = com.itextpdf.layout.element.Text(part)
                    .setFontSize(10f)
                
                val partParagraph = com.itextpdf.layout.element.Paragraph(partText)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setMargin(0f)
                
                val partCell = com.itextpdf.layout.element.Cell()
                    .add(partParagraph as com.itextpdf.layout.element.IBlockElement)
                    .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                    .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                    .setBorderTop(if (index == 0) com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f) else com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderBottom(if (index == consigneeParts.size - 1) com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f) else com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(6f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setMinHeight(20f) // Increased height to ensure visible separation
                    .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                
                consigneeTable.addCell(partCell)
                Logger.debug("Added consignee part $index to table: '$part'")
            }
            
            Logger.debug("Total rows in consignee table after adding parts: ${consigneeTable.numberOfRows}")
        } else {
            Logger.warn("PDF Generation - Consignee name is empty, not adding consignee rows")
        }
        
        document.add(consigneeTable)
        
        document.close()
        return outputStream.toByteArray()
    }
    
    private fun generateFobPdfDocument(pdfData: com.automan.backend.dto.ShippingSchedulePdfData): ByteArray {
        Logger.debug("GENERATING FOB PDF WITH TABLE FORMAT - UPDATED CODE - ${System.currentTimeMillis()}")
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
        
        // Get consignee name value (contains full consignee details)
        val consigneeName = pdfData.consigneeDetails.name ?: ""
        Logger.debug("FOB PDF Generation - Consignee Name: '$consigneeName'")
        
        if (consigneeName.isNotEmpty()) {
            // Split consignee name by commas and add each part as a separate row
            val consigneeParts = consigneeName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            Logger.debug("FOB PDF - Split consignee into ${consigneeParts.size} parts: $consigneeParts")
            
            // Add each comma-separated part as a separate row (center-aligned, not bold)
            consigneeParts.forEachIndexed { index, part ->
                val partText = com.itextpdf.layout.element.Text(part)
                    .setFontSize(10f)
                val partParagraph = com.itextpdf.layout.element.Paragraph()
                    .add(partText)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setMargin(0f) // Remove paragraph margins to ensure proper line breaks
                
                val partCell = com.itextpdf.layout.element.Cell()
                    .add(partParagraph)
                    .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                    .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                    .setBorderTop(if (index == 0) com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f) else com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setBorderBottom(if (index == consigneeParts.size - 1) com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f) else com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(4f)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setMinHeight(15f) // Ensure minimum height for each row
                
                consigneeTable.addCell(partCell)
                Logger.debug("FOB PDF - Added consignee part $index: '$part'")
            }
        } else {
            Logger.warn("FOB PDF Generation - Consignee name is empty, not adding consignee rows")
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
