package com.automan.backend.controller

import com.automan.backend.dto.PurchaseChangeHistoryPageRequest
import com.automan.backend.dto.PurchaseChangeHistoryPageResponse
import com.automan.backend.dto.PurchaseChangeHistorySingleRowDto
import com.automan.backend.dto.CreateTransactionRequest
import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceLedgerResult
import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.service.PurchaseExportService
import com.automan.backend.service.PurchaseService
import com.automan.backend.service.PurchaseChangeHistoryService
import com.automan.backend.service.ClientService
import com.automan.backend.service.TransactionService
import com.automan.backend.util.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/purchases")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class PurchaseController(
    private val purchaseService: PurchaseService,
    private val purchaseChangeHistoryService: PurchaseChangeHistoryService,
    private val purchaseExportService: PurchaseExportService,
    private val clientService: ClientService,
    private val transactionService: TransactionService,
    private val pdfService: com.automan.backend.service.PdfService,
    private val invoiceHistoryService: com.automan.backend.service.InvoiceHistoryService,
    private val rixoHistoryService: com.automan.backend.service.RixoHistoryService,
) {
    
    @GetMapping
    fun getAllPurchases(): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.getAllPurchases()
        return ResponseEntity.ok(purchases)
    }

    /** Full-database purchase export as Excel (.xlsx) with raw formula-friendly cell values. */
    @GetMapping("/export/xlsx")
    fun exportPurchasesXlsx(): ResponseEntity<ByteArray> {
        return try {
            val bytes = purchaseExportService.exportAllPurchasesXlsx()
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            ResponseEntity.ok()
                .header(
                    "Content-Disposition",
                    "attachment; filename=\"purchases_export_$ts.xlsx\"",
                )
                .contentType(
                    MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    ),
                )
                .body(bytes)
        } catch (e: Exception) {
            Logger.error("Purchase XLSX export failed: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    /** Unique purchase dates (as ISO yyyy-MM-dd) for Rixo Request Generator "Buying date" select. */
    @GetMapping("/distinct-purchase-dates")
    fun getDistinctPurchaseDates(): ResponseEntity<List<String>> {
        return ResponseEntity.ok(purchaseService.getDistinctPurchaseDatesIso())
    }

    /**
     * Scoped purchases for Rixo Generator / Updater (date ± company ± chassis tokens).
     * Avoids downloading the full hydrated catalog on every date change.
     */
    @GetMapping("/for-rixo")
    fun getPurchasesForRixo(
        @RequestParam(required = false) dateIso: String?,
        @RequestParam(required = false) rixoCompany: String?,
        @RequestParam(required = false) chassis: String?,
        @RequestParam(required = false, defaultValue = "false") includeNonPending: Boolean,
    ): ResponseEntity<Any> {
        if (dateIso.isNullOrBlank() && chassis.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf("error" to "Provide dateIso and/or chassis"),
            )
        }
        return try {
            ResponseEntity.ok(
                purchaseService.getPurchasesForRixo(dateIso, rixoCompany, chassis, includeNonPending),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }
    
    @GetMapping("/search")
    fun searchPurchases(@RequestParam query: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.searchPurchases(query)
        return ResponseEntity.ok(purchases)
    }

    /**
     * Paginated search for the purchase list (bounded result sets).
     * @param q non-blank search text
     * @param field `all` | `chassis` (prefix) | `carName` | `brand` | `clientName` | `supplier`
     */
    /**
     * Paginated audit rows for edits to purchases visible on the current list page.
     * Frontend sends IDs from the rendered page slice; history pagination is independent of purchase pagination.
     */
    @PostMapping("/change-history/page-scope")
    fun purchaseChangeHistoryPageScope(
        @RequestBody body: PurchaseChangeHistoryPageRequest,
    ): ResponseEntity<PurchaseChangeHistoryPageResponse> {
        return ResponseEntity.ok(purchaseChangeHistoryService.pageScoped(body))
    }

    /** Change audit rows for one purchase only (most recent first). */
    @GetMapping("/{id}/change-history")
    fun purchaseChangeHistoryForPurchase(
        @PathVariable id: Long,
    ): ResponseEntity<List<PurchaseChangeHistorySingleRowDto>> {
        if (id <= 0L) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(purchaseChangeHistoryService.listForSinglePurchase(id))
    }

    @GetMapping("/page-search")
    fun searchPurchasesPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "all") field: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(purchaseService.searchPurchasesPage(q, field, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /** Chassis-only search for car booking autocomplete (Rixo-confirmed, booking not requested yet). */
    @GetMapping("/search-chassis")
    fun searchPurchasesByChassisForBooking(@RequestParam query: String): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.searchChassisForBooking(query)
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
    
    /**
     * Distinct countries with at least one purchase eligible for booking: Rixo confirmed (`1`/`TRUE`)
     * and booking not requested yet.
     */
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

    /** Car booking: load LIST rows that share this [bookingId] (`booking_id` column). */
    @GetMapping("/by-booking/{bookingId}")
    fun getPurchasesByBookingId(@PathVariable bookingId: Long): ResponseEntity<List<Purchase>> {
        val purchases = purchaseService.getPurchasesByBookingId(bookingId)
        return ResponseEntity.ok(purchases)
    }
    
    /** Chassis at this POL where `booking_requested` is not true (legacy path: `/unshipped-chassis`). */
    @GetMapping("/unbooked-chassis", "/unshipped-chassis")
    fun getChassisWithoutBookingRequestByPol(
        @RequestParam pol: String
    ): ResponseEntity<List<String>> {
        val chassis = purchaseService.getChassisWithoutBookingRequestByPol(pol)
        return ResponseEntity.ok(chassis)
    }
    
    @PutMapping("/save-costs")
    fun saveCarCostDetails(@RequestBody costData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val chassis = costData["chassis"] as String
            val carPrice = (costData["carPrice"] as? Number)?.toDouble() ?: 0.0
            val auctionFee = (costData["auctionFee"] as? Number)?.toDouble() ?: 0.0
            val auctionPenaltyFee = (costData["auctionPenaltyFee"] as? Number)?.toDouble() ?: 0.0
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
                chassis, carPrice, auctionFee, auctionPenaltyFee, rixoPrice, shippingCharge,
                freight, inspectionFee, repairFee, mscCharges, profit, isPackageMode
            )
            
            ResponseEntity.ok(mapOf("message" to "Car cost details saved successfully", "chassis" to chassis))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to save car cost details: ${e.message}"))
        }
    }

    @PutMapping("/save-fob-costs")
    fun saveFobCarCostDetails(@RequestBody costData: Map<String, Any>): ResponseEntity<Map<String, String>> {
        return try {
            val chassis = costData["chassis"] as String
            val carPrice = (costData["carPrice"] as? Number)?.toDouble() ?: 0.0
            val auctionFee = (costData["auctionFee"] as? Number)?.toDouble() ?: 0.0
            val auctionPenaltyFee = (costData["auctionPenaltyFee"] as? Number)?.toDouble() ?: 0.0
            val rixoPrice = (costData["rixoPrice"] as? Number)?.toDouble() ?: 0.0
            val shippingCharge = (costData["shippingCharge"] as? Number)?.toDouble() ?: 0.0
            val inspectionFee = (costData["inspectionFee"] as? Number)?.toDouble() ?: 0.0
            val repairFee = (costData["repairFee"] as? Number)?.toDouble() ?: 0.0
            val mscCharges = (costData["mscCharges"] as? Number)?.toDouble() ?: 0.0
            val profit = (costData["profit"] as? Number)?.toDouble() ?: 0.0
            
            purchaseService.saveFobCarCostDetails(
                chassis, carPrice, auctionFee, auctionPenaltyFee, rixoPrice, shippingCharge,
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
            val costDetails = purchaseService.getCostDetailsByChassis(chassis)
            if (costDetails != null) {
                Logger.debug("Cost details prepared for chassis: $chassis")
                ResponseEntity.ok(costDetails)
            } else {
                Logger.debug("Purchase not found for chassis: $chassis")
                ResponseEntity.notFound().build()
            }
        } catch (e: Exception) {
            Logger.error("Exception in getCarCostDetails: ${e.message}", e)
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
    
    @GetMapping("/check-duplicate")
    fun checkPurchaseDuplicate(
        @RequestParam chassis: String,
        @RequestParam(required = false) excludeId: Long?,
    ): ResponseEntity<Map<String, Any?>> {
        val duplicate = purchaseService.findDuplicatePurchase(chassis, excludeId)
        return if (duplicate != null) {
            ResponseEntity.ok(
                mapOf(
                    "duplicate" to true,
                    "message" to purchaseService.duplicatePurchaseErrorMessage(duplicate),
                    "existingAuctionHouse" to duplicate.auctionHouse,
                    "existingPurchaseId" to duplicate.id,
                ),
            )
        } else {
            ResponseEntity.ok(mapOf("duplicate" to false))
        }
    }

    @PostMapping
    fun createPurchase(@RequestBody purchase: Purchase): ResponseEntity<Any> {
        Logger.debug("[Controller] Creating purchase - received shaken=${purchase.shaken}")
        return try {
            val createdPurchase = purchaseService.createPurchase(purchase)
            Logger.debug("[Controller] Created purchase - saved shaken=${createdPurchase.shaken}")
            ResponseEntity.ok(createdPurchase)
        } catch (e: IllegalArgumentException) {
            purchaseValidationErrorResponse(e)
        }
    }
    
    
    @PutMapping("/{id}")
    fun updatePurchase(@PathVariable id: Long, @RequestBody updateData: Map<String, Any>): ResponseEntity<Any> {
        Logger.debug("[Controller] Updating purchase ID: $id")
        Logger.debug("[Controller] Update data keys: ${updateData.keys}")
        Logger.debug("[Controller] Update data values: $updateData")
        
        return try {
            val updatedPurchase = purchaseService.updatePurchasePartial(id, updateData)
            if (updatedPurchase != null) {
                Logger.debug("[Controller] Purchase updated successfully - shipmentDate: ${updatedPurchase.shipmentDate}, bookingId: ${updatedPurchase.bookingId}, vessel: ${updatedPurchase.vessel}, auctionHouse: ${updatedPurchase.auctionHouse}")
                ResponseEntity.ok(updatedPurchase)
            } else {
                Logger.error("[Controller] Purchase not found or update failed")
                ResponseEntity.status(404).body(mapOf("error" to "Purchase with ID $id not found"))
            }
        } catch (e: IllegalArgumentException) {
            Logger.error("[Controller] Validation error updating purchase: ${e.message}", e)
            purchaseValidationErrorResponse(e)
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
    
    /** Sets `booking_requested = true` for the given purchases (legacy alias: `POST /ship`). */
    @PostMapping("/booking-requested", "/ship")
    fun setBookingRequested(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        Logger.debug("[Controller] Set booking_requested request received")
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
        
        Logger.debug("[Controller] Marking ${purchaseIds.size} purchases as booking_requested")
        val updatedPurchases = purchaseService.markPurchasesAsBookingRequested(purchaseIds)
        
        return ResponseEntity.ok(mapOf(
            "success" to true,
            "message" to "Successfully marked ${updatedPurchases.size} purchase(s) as booking requested",
            "updatedCount" to updatedPurchases.size,
            "purchaseIds" to purchaseIds
        ))
    }

    @PostMapping("/invoice/confirm")
    fun confirmInvoicePurchases(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        Logger.debug("[Controller] Confirm invoice request received")
        val purchaseIds = (request["purchaseIds"] as? List<*>)?.mapNotNull {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: emptyList()

        if (purchaseIds.isEmpty()) {
            Logger.error("[Controller] No purchase IDs provided for invoice confirm")
            return ResponseEntity.badRequest().body(mapOf("error" to "No purchase IDs provided"))
        }

        Logger.debug("[Controller] Marking ${purchaseIds.size} purchases as invoice_confirmed")
        val updatedPurchases = purchaseService.markPurchasesAsInvoiceConfirmed(purchaseIds)

        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Successfully confirmed invoice for ${updatedPurchases.size} purchase(s)",
                "updatedCount" to updatedPurchases.size,
                "purchaseIds" to purchaseIds
            )
        )
    }
    
    /**
     * Saves [com.automan.backend.model.InvoiceHistory] (insert or update by invoice number),
     * sets invoice_confirmed on purchases, returns PDF.
     */
    @PostMapping("/invoice/confirm-and-download")
    fun confirmAndDownloadInvoice(@RequestBody request: InvoiceConfirmAndDownloadRequest): ResponseEntity<ByteArray> {
        return try {
            val result = invoiceHistoryService.confirmAndDownload(request)
            val headers = org.springframework.http.HttpHeaders()
            headers.contentType = org.springframework.http.MediaType.APPLICATION_PDF
            headers.set(
                org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"invoice_${request.pdf.invoiceNumber}.pdf\"",
            )
            headers.contentLength = result.pdfBytes.size.toLong()
            applyLedgerHeaders(headers, result.ledger)
            ResponseEntity.ok().headers(headers).body(result.pdfBytes)
        } catch (e: IllegalArgumentException) {
            invoiceErrorJsonBytesResponse(e.message ?: "Invalid request")
        }
    }

    private fun applyLedgerHeaders(headers: org.springframework.http.HttpHeaders, ledger: InvoiceLedgerResult) {
        headers.set("X-Ledger-Posted", ledger.posted.toString())
        headers.set("X-Ledger-Reversed", ledger.reversed.toString())
        ledger.clientId?.let { headers.set("X-Ledger-Client-Id", it.toString()) }
        ledger.warning?.let { headers.set("X-Ledger-Warning", it) }
    }

    /**
     * Same persistence as [confirmAndDownloadInvoice] ([InvoiceHistoryService.saveOnly]): invoice_history,
     * marks purchases invoice_confirmed, ledger — **no** PDF generation or download.
     */
    @PostMapping("/invoice/save")
    fun saveInvoice(@RequestBody request: InvoiceConfirmAndDownloadRequest): ResponseEntity<Map<String, Any?>> {
        return try {
            val ledger = invoiceHistoryService.saveOnly(request)
            ResponseEntity.ok(
                linkedMapOf<String, Any?>(
                    "success" to true,
                    "message" to "Invoice saved successfully",
                ).apply { putAll(ledger.toResponseMap()) },
            )
        } catch (e: IllegalArgumentException) {
            val body = invoiceErrorBody(e.message ?: "Invalid request")
            ResponseEntity.status(invoiceErrorHttpStatus(body)).body(body)
        }
    }

    private fun purchaseValidationErrorResponse(e: IllegalArgumentException): ResponseEntity<Any> {
        val message = e.message ?: "Validation error"
        val isDuplicateChassis = message.equals(PurchaseService.DUPLICATE_CHASSIS_MESSAGE, ignoreCase = true) ||
            message.contains("already exists", ignoreCase = true)
        return if (isDuplicateChassis) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                mapOf(
                    "error" to "Conflict",
                    "message" to message,
                    "status" to HttpStatus.CONFLICT.value(),
                ),
            )
        } else {
            ResponseEntity.badRequest().body(
                mapOf(
                    "error" to message,
                    "message" to message,
                    "status" to HttpStatus.BAD_REQUEST.value(),
                ),
            )
        }
    }

    private fun invoiceErrorBody(message: String): LinkedHashMap<String, Any?> {
        val body = linkedMapOf<String, Any?>(
            "success" to false,
            "message" to message,
            "timestamp" to LocalDateTime.now().toString(),
        )
        when {
            message.contains("already exists", ignoreCase = true) -> {
                body["error"] = "Conflict"
                body["status"] = HttpStatus.CONFLICT.value()
            }
            message.contains("credit limit", ignoreCase = true) -> {
                body["error"] = "Credit limit exceeded"
                body["creditLimitBlocked"] = true
                body["status"] = HttpStatus.BAD_REQUEST.value()
            }
            else -> {
                body["error"] = "Bad Request"
                body["status"] = HttpStatus.BAD_REQUEST.value()
            }
        }
        return body
    }

    private fun invoiceErrorHttpStatus(body: Map<String, Any?>): HttpStatus = when (body["status"]) {
        HttpStatus.CONFLICT.value() -> HttpStatus.CONFLICT
        else -> HttpStatus.BAD_REQUEST
    }

    private fun invoiceErrorJsonBytesResponse(message: String): ResponseEntity<ByteArray> {
        val body = invoiceErrorBody(message)
        val json = ObjectMapper().writeValueAsString(body)
        return ResponseEntity.status(invoiceErrorHttpStatus(body))
            .contentType(MediaType.APPLICATION_JSON)
            .body(json.toByteArray(Charsets.UTF_8))
    }

    /**
     * Batch-creates invoices from shipping history — one per client group.
     * No PDF is generated; invoice records are saved directly to invoice_history.
     * Returns { saved: N, invoiceNumbers: [...], skipped: [...] }
     */
    @PostMapping("/invoice/batch-confirm")
    fun batchConfirmInvoices(@RequestBody body: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        @Suppress("UNCHECKED_CAST")
        val rawInvoices = body["invoices"] as? List<Map<String, Any>> ?: emptyList()
        if (rawInvoices.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "No invoices provided"))
        }

        val savedNumbers = mutableListOf<String>()
        val skippedNumbers = mutableListOf<String>()
        val errors = mutableListOf<String>()

        for (raw in rawInvoices) {
            try {
                val purchaseIds = (raw["purchaseIds"] as? List<*>)?.mapNotNull {
                    when (it) {
                        is Number -> it.toLong()
                        is String -> it.toLongOrNull()
                        else -> null
                    }
                } ?: emptyList()
                val chassisJoined = raw["chassisJoined"]?.toString() ?: ""
                val shippingDateIso = raw["shippingDateIso"]?.toString()

                @Suppress("UNCHECKED_CAST")
                val pdfRaw = raw["pdf"] as? Map<String, Any> ?: continue
                val items = (pdfRaw["items"] as? List<Map<String, Any>> ?: emptyList()).mapIndexed { idx, item ->
                    com.automan.backend.dto.InvoiceItem(
                        unit = (item["unit"] as? Number)?.toInt() ?: (idx + 1),
                        description = item["description"]?.toString() ?: "",
                        amount = item["amount"]?.toString() ?: "",
                    )
                }
                val pdfReq = com.automan.backend.dto.InvoicePdfRequest(
                    invoiceNumber = pdfRaw["invoiceNumber"]?.toString() ?: "",
                    invoiceDate = pdfRaw["invoiceDate"]?.toString() ?: "",
                    lcNumber = pdfRaw["lcNumber"]?.toString(),
                    clientName = pdfRaw["clientName"]?.toString() ?: "",
                    clientAddress = pdfRaw["clientAddress"]?.toString(),
                    vessel = pdfRaw["vessel"]?.toString() ?: "",
                    shippingDate = pdfRaw["shippingDate"]?.toString() ?: "",
                    from = pdfRaw["from"]?.toString() ?: "",
                    to = pdfRaw["to"]?.toString() ?: "",
                    priceType = pdfRaw["priceType"]?.toString() ?: "C&F",
                    items = items,
                    totalAmount = pdfRaw["totalAmount"]?.toString() ?: "",
                    bankAccount = pdfRaw["bankAccount"]?.toString(),
                    message = pdfRaw["message"]?.toString(),
                )
                val request = InvoiceConfirmAndDownloadRequest(
                    purchaseIds = purchaseIds,
                    chassisJoined = chassisJoined,
                    shippingDateIso = shippingDateIso,
                    pdf = pdfReq,
                )
                invoiceHistoryService.saveOnly(request)
                savedNumbers.add(pdfReq.invoiceNumber)
            } catch (e: IllegalArgumentException) {
                val msg = e.message ?: "Invalid invoice"
                Logger.warn("[batch-confirm] Skipped: $msg")
                skippedNumbers.add(msg)
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                Logger.error("[batch-confirm] Error saving invoice: $msg", e)
                errors.add(msg)
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "saved" to savedNumbers.size,
                "invoiceNumbers" to savedNumbers,
                "skipped" to skippedNumbers,
                "errors" to errors,
            )
        )
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
    
    /** Returns PDF bytes in the HTTP body only; does not persist or upload to object storage (S3, etc.). */
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

            val persistHistory = when (val p = request["persistHistory"]) {
                is Boolean -> p
                is String -> p.equals("true", ignoreCase = true)
                else -> false
            }
            val generatePdf = when (val g = request["generatePdf"]) {
                is Boolean -> g
                is String -> g.equals("true", ignoreCase = true)
                else -> true
            }
            if (persistHistory && selectedIds.isNotEmpty()) {
                rixoHistoryService.saveFromTransport(selectedIds, transportData)
            }
            if (persistHistory && selectedIds.isNotEmpty()) {
                purchaseService.markPurchasesAsRixoRequestedTrue(selectedIds)
            }

            if (!generatePdf) {
                return ResponseEntity.noContent().build()
            }

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
        @RequestParam(required = false) clientName: String?,
        @RequestParam(required = false) vessel: String?,
        @RequestParam(required = false) shipmentDate: String?
    ): ResponseEntity<List<Purchase>> {
        val purchases = when {
            !clientName.isNullOrBlank() ->
                purchaseService.filterByClientNameAndVesselAndShipmentDate(clientName, vessel, shipmentDate)
            else ->
                purchaseService.filterByConsigneeAndVesselAndShipmentDate(consignee, vessel, shipmentDate)
        }
        return ResponseEntity.ok(purchases)
    }
    
    @PostMapping("/transaction")
    fun createTransaction(@RequestBody transactionData: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            val request = CreateTransactionRequest(
                clientId = (transactionData["clientId"] as? Number)?.toLong()
                    ?: throw IllegalArgumentException("Client ID is required"),
                eventDate = transactionData["eventDate"] as String,
                eventType = TransactionService.parseManualEventType(transactionData),
                eventDescription = transactionData["eventDescription"] as? String ?: "",
                quantity = (transactionData["quantity"] as? Number)?.toInt(),
                billNumber = transactionData["billNumber"] as? String,
                transactionPrice = (transactionData["transactionPrice"] as? Number)?.toDouble(),
                paymentReceived = (transactionData["paymentReceived"] as? Number)?.toDouble(),
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
        
        // Add car data rows (YEAR + price columns right-aligned)
        pdfData.carList.forEach { car ->
            carTable.addCell(createCell(car.no.toString(), false))
            carTable.addCell(createCell(car.name, false))
            carTable.addCell(createCell(car.chassisNumber, false))
            carTable.addCell(
                createCell(car.year, false).setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT),
            )
            carTable.addCell(
                createCell(formatPriceWithCommas(car.cnfPrice), false)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT),
            )
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
        
        val consigneeName = pdfData.consigneeDetails.name ?: ""
        val consigneeAddress = pdfData.consigneeDetails.address ?: ""
        Logger.debug("PDF Generation - Consignee Name: '$consigneeName'")
        Logger.debug("PDF Generation - Consignee Address length: ${consigneeAddress.length}")
        addShippingScheduleConsigneeRows(consigneeTable, consigneeName, consigneeAddress, padding = 6f, minHeight = 20f)
        
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
            val yearCell = createCellWithBorder(car.year, false)
            yearCell.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
            carListTable.addCell(yearCell)
            val priceCell = createCellWithBorder(formatPriceWithCommas(car.cnfPrice), false)
            priceCell.setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT)
            carListTable.addCell(priceCell)
        }
        
        document.add(carListTable)
        
        // Add some space
        document.add(com.itextpdf.layout.element.Paragraph().setMarginBottom(20f))
        
        // Consignee Table (moved after Car List Table)
        val consigneeTable = com.itextpdf.layout.element.Table(floatArrayOf(1f))
            .setWidth(com.itextpdf.layout.properties.UnitValue.createPercentValue(70f)) // Wider table to prevent wrapping
            .setBorder(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 2f))
        
        consigneeTable.addCell(createCellWithBorderCentered("CONSIGNEE:", true))
        
        val consigneeName = pdfData.consigneeDetails.name ?: ""
        val consigneeAddress = pdfData.consigneeDetails.address ?: ""
        Logger.debug("FOB PDF Generation - Consignee Name: '$consigneeName'")
        addShippingScheduleConsigneeRows(consigneeTable, consigneeName, consigneeAddress, padding = 4f, minHeight = 15f)
        
        document.add(consigneeTable)
        
        document.close()
        return outputStream.toByteArray()
    }

    /**
     * Name rows: comma-separated parts of [consigneeName]. Address rows: lines from [consigneeAddress] (Consignee Map), shown below the name.
     */
    private fun addShippingScheduleConsigneeRows(
        consigneeTable: com.itextpdf.layout.element.Table,
        consigneeName: String,
        consigneeAddress: String,
        padding: Float,
        minHeight: Float
    ) {
        val nameParts = consigneeName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val addr = consigneeAddress.trim()
        val addressLines = if (addr.isEmpty()) {
            emptyList()
        } else {
            addr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val rows = mutableListOf<Pair<String, Boolean>>()
        nameParts.forEach { rows.add(it to true) }
        addressLines.forEach { rows.add(it to false) }
        if (rows.isEmpty()) {
            Logger.warn("PDF Generation - Consignee name and address empty, not adding consignee content rows")
            return
        }
        rows.forEachIndexed { index, (text, isNameLine) ->
            val fontSize = if (isNameLine) 10f else 9f
            val partText = com.itextpdf.layout.element.Text(text).setFontSize(fontSize)
            val partParagraph = com.itextpdf.layout.element.Paragraph(partText)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMargin(0f)
            val isLast = index == rows.size - 1
            val partCell = com.itextpdf.layout.element.Cell()
                .add(partParagraph as com.itextpdf.layout.element.IBlockElement)
                .setBorderLeft(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                .setBorderRight(com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
                .setBorderTop(
                    if (index == 0) {
                        com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f)
                    } else {
                        com.itextpdf.layout.borders.Border.NO_BORDER
                    }
                )
                .setBorderBottom(
                    if (isLast) {
                        com.itextpdf.layout.borders.SolidBorder(com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f)
                    } else {
                        com.itextpdf.layout.borders.Border.NO_BORDER
                    }
                )
                .setPadding(padding)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                .setMinHeight(minHeight)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            consigneeTable.addCell(partCell)
        }
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
    
    /**
     * Formats a price (Long) or price string with thousand separators (commas).
     * Examples: 16500 -> "¥16,500", "¥16500" -> "¥16,500", "abc" -> "¥0"
     */
    private fun formatPriceWithCommas(price: Any?): String {
        if (price == null) return "¥0"
        
        val priceStr = price.toString().trim()
            .replace("¥", "")  // Remove existing ¥ symbol
            .replace(",", "")  // Remove existing commas
            .trim()
        
        return try {
            val longValue = priceStr.toLong()
            "¥${String.format("%,d", longValue)}"  // Format with thousand separators
        } catch (e: NumberFormatException) {
            "¥0"  // Default to ¥0 if parsing fails
        }
    }
    
    private fun createHeaderCell(text: String): com.itextpdf.layout.element.Cell {
        return com.itextpdf.layout.element.Cell()
            .add(com.itextpdf.layout.element.Paragraph(text).setBold().setFontSize(10f))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
    }
}
