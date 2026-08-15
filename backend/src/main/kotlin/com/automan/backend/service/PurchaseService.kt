package com.automan.backend.service

import com.automan.backend.dto.PurchasePageFilterClause
import com.automan.backend.dto.PurchasePageFilterRequest
import com.automan.backend.dto.PurchasePageResponse
import com.automan.backend.model.Purchase
import com.automan.backend.model.ImportResponse
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.model.BookingMapping
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.PurchaseIdDateProjection
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.util.CarModelYearUtils
import com.automan.backend.util.Logger
import com.automan.backend.util.PurchaseDateParseUtils
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

@Service
class PurchaseService(
    private val purchaseRepository: PurchaseRepository,
    private val pdfService: PdfService,
    private val bookingMappingRepository: BookingMappingRepository,
    private val shippingHistoryRepository: ShippingHistoryRepository,
    private val purchaseWorkflowService: PurchaseWorkflowService,
    private val purchaseChangeHistoryService: PurchaseChangeHistoryService,
    private val purchaseCostLineService: PurchaseCostLineService,
    private val purchaseVehicleOverrideService: PurchaseVehicleOverrideService,
    private val purchaseExtendedAttributesService: PurchaseExtendedAttributesService,
    private val shippingSnapshotService: ShippingSnapshotService,
    private val localPurchaseSanitizer: LocalPurchaseSanitizer,
    private val clientRepository: ClientRepository,
) {
    companion object {
        const val DUPLICATE_CHASSIS_MESSAGE = "the chassis number already exist"
    }

    /** Links [Purchase.clientId] when [clientName] matches exactly one row in clients (case-insensitive). */
    private fun resolveClientIdFromName(clientName: String?): Long? {
        val name = clientName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val matches = clientRepository.findByClientNameIgnoreCase(name)
        return if (matches.size == 1) matches.first().id else null
    }

    /**
     * Phase 2–4 write sync + read adapters for API responses.
     * @param snapshotSpecs when true (create and edit/Update), non-empty spec values are stored as
     * overrides even when they equal the car_brand_mapping baseline. This freezes form specs onto
     * the purchase so Edit/Summary show saved values instead of only map defaults.
     */
    private fun finalizePurchaseWrite(purchase: Purchase, snapshotSpecs: Boolean = false): Purchase {
        val sanitized = localPurchaseSanitizer.apply(purchase)
        val withWorkflow = purchaseWorkflowService.applyWorkflowWrite(sanitized)
        purchaseCostLineService.syncFromPurchase(withWorkflow)
        purchaseVehicleOverrideService.syncFromPurchase(withWorkflow, snapshotSpecs = snapshotSpecs)
        // Shipping fields are @Transient on Purchase; sync before extended-attributes JPA save.
        shippingSnapshotService.syncFromPurchase(withWorkflow)
        val withExtended = purchaseExtendedAttributesService.syncFromPurchase(withWorkflow)
        return applyReadAdapters(withExtended)
    }

    private fun applyReadAdapters(purchase: Purchase): Purchase {
        val withExtended = purchaseExtendedAttributesService.applyForRead(purchase)
        val withVehicle = purchaseVehicleOverrideService.applyForRead(withExtended)
        val withShipping = shippingSnapshotService.applyForRead(withVehicle)
        val withWorkflow = purchaseWorkflowService.applyForRead(withShipping)
        return purchaseCostLineService.applyForRead(withWorkflow)
    }

    private fun applyReadAdapterOrNull(purchase: Purchase?): Purchase? =
        purchase?.let { applyReadAdapters(it) }

    private fun applyReadAdapters(purchases: List<Purchase>): List<Purchase> {
        if (purchases.isEmpty()) return purchases
        val withExtended = purchases.map { purchaseExtendedAttributesService.applyForRead(it) }
        val withVehicle = withExtended.map { purchaseVehicleOverrideService.applyForRead(it) }
        val withShipping = shippingSnapshotService.applyForReadBatch(withVehicle)
        val withWorkflow = withShipping.map { purchaseWorkflowService.applyForRead(it) }
        return purchaseCostLineService.applyForReadBatch(withWorkflow)
    }

    private fun persistPurchase(purchase: Purchase): Purchase =
        purchaseRepository.save(
            purchaseWorkflowService.applyWorkflowWrite(localPurchaseSanitizer.apply(purchase)),
        )

    private fun persistPurchaseAndFlush(purchase: Purchase): Purchase =
        purchaseRepository.saveAndFlush(
            purchaseWorkflowService.applyWorkflowWrite(localPurchaseSanitizer.apply(purchase)),
        )

    /** POL from stock_location mapping (same mapping used in Rixo import). */
    private fun polFromStockLocation(stockLocation: String?): String? {
        val s = stockLocation?.trim() ?: return null
        return when {
            s.startsWith("GLOBAL KAWASAKI", ignoreCase = true) -> "YOKOHAMA"
            s.equals("AQUA LOGISTICS", ignoreCase = true) -> "YOKOHAMA"
            s.startsWith("GLOBAL NAGOYA", ignoreCase = true) -> "NAGOYA"
            s.equals("FLASHRISE", ignoreCase = true) -> "NAGOYA"
            s.equals("KLC", ignoreCase = true) -> "OSAKA"
            s.startsWith("GLOBAL HAKATA", ignoreCase = true) -> "HAKATA"
            s.equals("BARAKI PARKING", ignoreCase = true) -> "---"
            s.equals("LOCAL", ignoreCase = true) -> "---"
            else -> null
        }
    }

    private fun effectivePol(pol: String?, stockLocation: String?): String? {
        val rawPol = pol?.trim().orEmpty()
        if (rawPol.isNotBlank()) return rawPol
        val derived = polFromStockLocation(stockLocation)
        val d = derived?.trim().orEmpty()
        if (d.isBlank() || d == "---") return null
        return d
    }
    
    /**
     * Validates Production Date (carModelYear): YYYY-MM with month 00–12; year in 1900–2100.
     * Format should be YYYY-MM (e.g., "2013-12"). Year must be exactly 4 digits in project range.
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
        val monthStr = parts[1].trim()
        if (yearStr.length != 4) {
            return "Production year must be exactly 4 digits. Got: $yearStr"
        }
        if (!yearStr.all { it.isDigit() }) {
            return "Production year must be 4 digits only. Got: $yearStr"
        }
        val year = yearStr.toIntOrNull()
        if (year == null || year < 1900 || year > 2100) {
            return "Production year must be between 1900 and 2100. Got: $yearStr"
        }
        val month = monthStr.toIntOrNull()
        if (month == null || month !in 0..12) {
            return "Invalid Production Date month. Use 00–12 (00 = year only)."
        }
        
        return null // Valid
    }

    private fun normalizeManufactureYear(raw: String?): String? {
        val t = raw?.trim().orEmpty()
        return t.ifEmpty { null }
    }

    /**
     * Validates Manufacture Year: optional; if set must be exactly 4 digits between 1900 and 2100.
     */
    private fun validateManufactureYear(manufactureYear: String?): String? {
        val t = manufactureYear?.trim().orEmpty()
        if (t.isEmpty()) return null
        if (t.length != 4 || !t.all { it.isDigit() }) {
            return "Manufacture year must be exactly 4 digits (YYYY)."
        }
        val year = t.toIntOrNull()
        if (year == null || year < 1900 || year > 2100) {
            return "Manufacture year must be between 1900 and 2100."
        }
        return null
    }
    
    fun getAllPurchases(): List<Purchase> {
        return applyReadAdapters(purchaseRepository.findAll())
    }

    /** Hydrates purchases for export (cost lines, vehicle overrides, shipping, workflow, extended JSON). */
    fun hydratePurchasesForExport(purchases: List<Purchase>): List<Purchase> =
        applyReadAdapters(purchases)

    /**
     * Unique purchase dates from [Purchase.date] as ISO [yyyy-MM-dd], newest first, for Rixo Buying Date.
     * Only includes dates that still have at least one purchase with rixo_requested not TRUE/1
     * (same pending rule as [getPurchasesForRixo]).
     * Unparseable date strings are skipped.
     */
    fun getDistinctPurchaseDatesIso(): List<String> {
        return purchaseRepository.findDateAndWorkflowPairs().asSequence()
            .filter { pair ->
                // Same pending rule as applyForRead: rixoRequested is TRUE only for RIXO_REQUESTED+.
                val ws = pair.getWorkflowStatus()
                ws == null || ws == com.automan.backend.model.WorkflowStatus.PURCHASED
            }
            .mapNotNull { PurchaseDateParseUtils.parseToLocalDate(it.getDate()?.trim().orEmpty()) }
            .distinct()
            .sortedDescending()
            .map { it.toString() }
            .toList()
    }

    /**
     * Scoped purchase list for Rixo Generator / Updater.
     *
     * Filter rules match the frontend:
     * - [dateIso] → purchase.date parses to that LocalDate (same as weekday-label equality)
     * - pending Rixo only (rixoRequested empty / not 1 / not true) unless [includeNonPending]
     * - optional [rixoCompany]: blank/Undefined sentinel → blank company; else exact trim match
     * - optional [chassis]: semicolon/comma tokens; purchase chassis must equal a token (case-insensitive trim)
     *
     * Only matching rows are hydrated (applyReadAdapters), not the full catalog.
     */
    fun getPurchasesForRixo(
        dateIso: String?,
        rixoCompany: String?,
        chassis: String?,
        includeNonPending: Boolean = false,
    ): List<Purchase> {
        val targetDate = dateIso?.trim()?.takeIf { it.isNotEmpty() }?.let {
            try {
                java.time.LocalDate.parse(it)
            } catch (_: Exception) {
                throw IllegalArgumentException("Invalid dateIso: $it (expected yyyy-MM-dd)")
            }
        }
        val chassisTokens = parseRixoChassisTokens(chassis)
        val companyFilter = rixoCompany?.trim()
        val undefinedCompany =
            companyFilter.isNullOrEmpty() ||
                companyFilter.equals("__RIXO_COMPANY_UNDEFINED__", ignoreCase = true) ||
                companyFilter.equals("Undefined", ignoreCase = true)

        val base: List<Purchase> = when {
            chassisTokens.isNotEmpty() -> {
                chassisTokens
                    .flatMap { token -> purchaseRepository.findByChassisToken(token) }
                    .distinctBy { it.id }
            }
            targetDate != null -> {
                // Match ISO or weekday labels (e.g. "June 29, 2026 (Monday)") via parse,
                // same equality as getDistinctPurchaseDatesIso / Purchase List date filters.
                // ISO LIKE alone misses labeled dates and empties Rixo company + cars.
                val matchingIds = filterIdDatePairsByRange(
                    purchaseRepository.findIdAndDateAll(),
                    targetDate,
                    targetDate,
                )
                if (matchingIds.isEmpty()) {
                    emptyList()
                } else {
                    purchaseRepository.findAllById(matchingIds).toList()
                }
            }
            else -> purchaseRepository.findAll()
        }

        val candidates = base.asSequence().filter { p ->
            if (targetDate != null) {
                val parsed = PurchaseDateParseUtils.parseToLocalDate(p.date?.trim().orEmpty())
                if (parsed != targetDate) return@filter false
            }
            if (!includeNonPending) {
                // rixoRequested is @Transient; pending = not yet RIXO_REQUESTED+ (see applyForRead).
                val ws = p.workflowStatus
                if (ws != null && ws != com.automan.backend.model.WorkflowStatus.PURCHASED) {
                    return@filter false
                }
            }
            if (companyFilter != null) {
                val raw = p.rixoCompany?.trim().orEmpty()
                val ok = if (undefinedCompany) {
                    raw.isEmpty()
                } else {
                    raw.equals(companyFilter, ignoreCase = true)
                }
                if (!ok) return@filter false
            }
            if (chassisTokens.isNotEmpty()) {
                val ch = p.chassis?.trim().orEmpty()
                if (ch.isEmpty() || chassisTokens.none { token -> rixoChassisTokenMatchesPurchase(token, ch) }) {
                    return@filter false
                }
            }
            true
        }.toList()

        return applyReadAdapters(candidates)
    }

    private fun parseRixoChassisTokens(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(';', ',', '\n', '\r')
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }
            .toSet()
    }

    /** Same rules as frontend [rixoPrefillChassisTokenMatchesPurchase]. */
    private fun rixoChassisTokenMatchesPurchase(token: String, purchaseChassis: String): Boolean {
        val t = token.trim()
        val ch = purchaseChassis.trim()
        if (t.isEmpty() || ch.isEmpty()) return false
        if (ch.equals(t, ignoreCase = true)) return true
        val head = ch.substringBefore('-').trim()
        if (head.equals(t, ignoreCase = true)) return true
        if (ch.startsWith(t, ignoreCase = true) &&
            (ch.length == t.length || ch.getOrNull(t.length) == '-')
        ) {
            return true
        }
        return false
    }

    /** Same pending rule as frontend [isRixoRequestedPendingForTransportGenerator]. */
    private fun isRixoRequestedPendingForTransport(raw: String?): Boolean {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isEmpty()) return true
        return s != "1" && s != "true"
    }
    
    fun getPurchaseById(id: Long): Purchase? {
        return applyReadAdapterOrNull(purchaseRepository.findById(id).orElse(null))
    }

    /** All purchases sharing the same shipping booking id (car booking search). */
    fun getPurchasesByBookingId(bookingId: Long): List<Purchase> {
        return applyReadAdapters(purchaseRepository.findByBookingId(bookingId))
    }
    
    private fun firstSemicolonToken(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return ""
        val parts = s.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.firstOrNull() ?: s
    }

    /**
     * Full chassis identity = chassis code + chassis number (`CODE-NUMBER`).
     * Code-only values (e.g. `AAHH45`) are not unique and must not trigger duplicate errors.
     */
    fun isFullChassisIdentity(chassis: String?): Boolean {
        val c = chassis?.trim().orEmpty()
        if (c.isEmpty()) return false
        val dash = c.indexOf('-')
        if (dash <= 0 || dash >= c.length - 1) return false
        val code = c.substring(0, dash).trim()
        val number = c.substring(dash + 1).trim()
        return code.isNotEmpty() && number.isNotEmpty()
    }

    fun findDuplicatePurchase(
        chassis: String?,
        excludeId: Long?,
    ): Purchase? {
        val c = chassis?.trim().orEmpty()
        if (c.isEmpty()) return null
        // Only enforce uniqueness for full chassis (code-number). Code-only is allowed to repeat.
        if (!isFullChassisIdentity(c)) return null

        val candidates = purchaseRepository.findByChassisIgnoreCaseTrim(c)
        return candidates.firstOrNull { row ->
            excludeId == null || row.id != excludeId
        }
    }

    fun duplicatePurchaseErrorMessage(@Suppress("UNUSED_PARAMETER") duplicate: Purchase): String =
        DUPLICATE_CHASSIS_MESSAGE

    private fun assertNoDuplicatePurchase(
        chassis: String?,
        excludeId: Long?,
    ) {
        val duplicate = findDuplicatePurchase(chassis, excludeId)
        if (duplicate != null) {
            throw IllegalArgumentException(duplicatePurchaseErrorMessage(duplicate))
        }
    }

    @Transactional
    fun createPurchase(purchase: Purchase): Purchase {
        // Validate carModelYear (Production Date)
        val yearError = validateCarModelYear(purchase.carModelYear)
        if (yearError != null) {
            throw IllegalArgumentException(yearError)
        }
        validateManufactureYear(purchase.manufactureYear)?.let { throw IllegalArgumentException(it) }

        assertNoDuplicatePurchase(purchase.chassis, excludeId = null)
        
        // Ensure shaken has a value (default to false if null)
        // Explicitly convert to boolean to ensure proper database storage
        val shakenValue = when {
            purchase.shaken == true -> true
            purchase.shaken == false -> false
            else -> false
        }
        val negotiateValue = when {
            purchase.negotiate == true -> true
            purchase.negotiate == false -> false
            else -> false
        }
        val purchaseToSave = purchase.copy(
            shaken = shakenValue,
            negotiate = negotiateValue,
            local = purchase.local,
            manufactureYear = normalizeManufactureYear(purchase.manufactureYear),
            clientId = purchase.clientId ?: resolveClientIdFromName(purchase.clientName),
        )
        
        Logger.debug("Creating purchase - received shaken=${purchase.shaken}, saving shaken=${purchaseToSave.shaken}")
        val savedPurchase = persistPurchase(purchaseToSave)
        Logger.debug("Saved purchase - shaken=${savedPurchase.shaken}")
        val newId = savedPurchase.id
        if (newId != null) {
            purchaseWorkflowService.recomputeByPurchaseId(newId)
            val persisted = purchaseRepository.findById(newId).orElse(savedPurchase)
            return finalizePurchaseWrite(
                purchaseToSave.copy(
                    id = persisted.id,
                    createdAt = persisted.createdAt,
                    workflowStatus = persisted.workflowStatus,
                    workflowStatusUpdatedAt = persisted.workflowStatusUpdatedAt,
                    bookingRequested = persisted.bookingRequested,
                    invoiceConfirmed = persisted.invoiceConfirmed,
                    bookingId = persisted.bookingId,
                    updatedAt = persisted.updatedAt,
                ),
                // Create (Add + Quick Purchase): freeze non-empty form specs onto the purchase so
                // Edit/Summary show saved values, not only Chassis Map defaults.
                snapshotSpecs = true,
            )
        }
        return finalizePurchaseWrite(purchaseToSave, snapshotSpecs = true)
    }
    
    @Transactional
    fun updatePurchase(id: Long, purchase: Purchase): Purchase? {
        Logger.debug("🔍 [Service] Updating purchase ID: $id")
        Logger.debug("🔍 [Service] Purchase data received: $purchase")
        
        val existingPurchase = purchaseRepository.findById(id).orElse(null)?.let { applyReadAdapters(it) }
        if (existingPurchase != null) {
            Logger.debug("🔍 [Service] Found existing purchase: $existingPurchase")
            validateManufactureYear(purchase.manufactureYear)?.let { throw IllegalArgumentException(it) }

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
                pol = purchase.pol ?: existingPurchase.pol,
                pod = purchase.pod ?: existingPurchase.pod,
                rixoCompany = purchase.rixoCompany ?: existingPurchase.rixoCompany,
                clientName = purchase.clientName ?: existingPurchase.clientName,
                clientId = purchase.clientId
                    ?: resolveClientIdFromName(purchase.clientName ?: existingPurchase.clientName)
                    ?: existingPurchase.clientId,
                consignee = purchase.consignee ?: existingPurchase.consignee,
                country = purchase.country ?: existingPurchase.country,
                price = purchase.price ?: existingPurchase.price,
                auctionFee = purchase.auctionFee ?: existingPurchase.auctionFee,
                auctionPenaltyFee = purchase.auctionPenaltyFee ?: existingPurchase.auctionPenaltyFee,
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
                vessel = purchase.vessel ?: existingPurchase.vessel,
                bookingRequested = purchase.bookingRequested,
                invoiceConfirmed = purchase.invoiceConfirmed ?: existingPurchase.invoiceConfirmed,
                workflowStatus = existingPurchase.workflowStatus,
                workflowStatusUpdatedAt = existingPurchase.workflowStatusUpdatedAt,
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
                negotiate = when {
                    purchase.negotiate == true -> true
                    purchase.negotiate == false -> false
                    else -> existingPurchase.negotiate
                },
                local = purchase.local,
                manufactureYear = normalizeManufactureYear(purchase.manufactureYear) ?: existingPurchase.manufactureYear,
                repairCompany = purchase.repairCompany ?: existingPurchase.repairCompany,
                repairCharges = purchase.repairCharges ?: existingPurchase.repairCharges,
                updatedAt = java.time.LocalDateTime.now()
            )
            
            Logger.debug("🔍 [Service] Saving updated purchase: $updatedPurchase")
            val savedPurchase = persistPurchase(updatedPurchase)
            Logger.log("✅ [Service] Successfully saved purchase: $savedPurchase")
            purchaseWorkflowService.recomputeByPurchaseId(id)
            val refreshed = purchaseRepository.findById(id).orElse(savedPurchase)
            return finalizePurchaseWrite(
                updatedPurchase.copy(
                    id = refreshed.id,
                    createdAt = refreshed.createdAt,
                    workflowStatus = refreshed.workflowStatus,
                    workflowStatusUpdatedAt = refreshed.workflowStatusUpdatedAt,
                    bookingRequested = refreshed.bookingRequested,
                    invoiceConfirmed = refreshed.invoiceConfirmed,
                    bookingId = refreshed.bookingId,
                    updatedAt = refreshed.updatedAt,
                ),
            )
        } else {
            Logger.error("Purchase with ID $id not found")
        }
        return null
    }
    
    @Transactional
    fun updatePurchasePartial(id: Long, updateData: Map<String, Any>): Purchase? {
        Logger.debug("🔍 [Service] Updating purchase ID: $id with partial data")
        Logger.debug("🔍 [Service] Update data received: $updateData")
        val auditChangedFields = PurchaseChangeHistoryService.extractAuditChangedFields(updateData)
        
        // Validate carModelYear if provided
        val carModelYearValue = updateData["carModelYear"] as? String
        if (carModelYearValue != null) {
            val yearError = validateCarModelYear(carModelYearValue)
            if (yearError != null) {
                throw IllegalArgumentException(yearError)
            }
        }
        if (updateData.containsKey("manufactureYear")) {
            validateManufactureYear(updateData["manufactureYear"] as? String)?.let { throw IllegalArgumentException(it) }
        }
        
        val existingPurchase = purchaseRepository.findById(id).orElse(null)?.let { applyReadAdapters(it) }
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
                pol = updateData["pol"] as? String ?: existingPurchase.pol,
                pod = run {
                    val hasPod = updateData.containsKey("pod")
                    val hasDest = updateData.containsKey("destination")
                    if (!hasPod && !hasDest) {
                        existingPurchase.pod
                    } else {
                        val raw: Any? = when {
                            hasPod -> updateData["pod"]
                            else -> updateData["destination"]
                        }
                        when (raw) {
                            null -> null
                            is String -> raw.trim().ifEmpty { null }
                            else -> raw.toString().trim().ifEmpty { null }
                        }
                    }
                },
                rixoCompany = updateData["rixoCompany"] as? String ?: existingPurchase.rixoCompany,
                clientName = updateData["clientName"] as? String ?: existingPurchase.clientName,
                clientId = run {
                    val explicit = (updateData["clientId"] as? Number)?.toLong()
                    if (explicit != null) {
                        explicit
                    } else {
                        val name = updateData["clientName"] as? String ?: existingPurchase.clientName
                        resolveClientIdFromName(name) ?: existingPurchase.clientId
                    }
                },
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
                auctionPenaltyFee = updateData["auctionPenaltyFee"] as? String ?: existingPurchase.auctionPenaltyFee,
                recycleFee = updateData["recycleFee"] as? String ?: existingPurchase.recycleFee,
                roadTax = updateData["roadTax"] as? String ?: existingPurchase.roadTax,
                taxTotal = updateData["taxTotal"] as? String ?: existingPurchase.taxTotal,
                totalPrice = updateData["totalPrice"] as? String ?: existingPurchase.totalPrice,
                paymentDate = updateData["paymentDate"] as? String ?: existingPurchase.paymentDate,
                rixoRequested = updateData["rixoRequested"] as? String ?: existingPurchase.rixoRequested,
                rixoConfirmed = updateData["rixoConfirmed"] as? String ?: existingPurchase.rixoConfirmed,
                notes = updateData["notes"] as? String ?: existingPurchase.notes,
                shipmentDate = run {
                    if (!updateData.containsKey("shipmentDate")) {
                        existingPurchase.shipmentDate
                    } else {
                        updateData["shipmentDate"] as? String
                    }
                },
                blNo = run {
                    if (!updateData.containsKey("blNo")) {
                        existingPurchase.blNo
                    } else {
                        updateData["blNo"] as? String
                    }
                },
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
                negotiate = run {
                    val negotiateValue = updateData["negotiate"]
                    when {
                        negotiateValue is Boolean -> negotiateValue
                        negotiateValue is String -> negotiateValue.toBoolean()
                        negotiateValue == true -> true
                        negotiateValue == false -> false
                        negotiateValue == null -> existingPurchase.negotiate
                        else -> existingPurchase.negotiate
                    }
                },
                local = run {
                    val localValue = updateData["local"]
                    when {
                        localValue is Boolean -> localValue
                        localValue is String -> localValue.toBoolean()
                        localValue is Number -> localValue.toInt() != 0
                        else -> existingPurchase.local
                    }
                },
                manufactureYear = run {
                    if (!updateData.containsKey("manufactureYear")) {
                        existingPurchase.manufactureYear
                    } else {
                        normalizeManufactureYear(updateData["manufactureYear"] as? String)
                    }
                },
                repairCompany = updateData["repairCompany"] as? String ?: existingPurchase.repairCompany,
                repairCharges = updateData["repairCharges"] as? String ?: existingPurchase.repairCharges,
                carPictures = run {
                    // Frontend may send camelCase or snake_case. Accept both.
                    val carPicturesData = updateData["carPictures"] ?: updateData["car_pictures"]
                    if (carPicturesData == null) {
                        existingPurchase.carPictures
                    } else {
                        // Convert car pictures array (or already-JSON string) to JSON string
                        val jsonString = when (carPicturesData) {
                            is String -> carPicturesData
                            else -> com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(carPicturesData)
                        }
                        Logger.debug("📷 [Service] Saving car pictures data (len=${jsonString.length}): ${jsonString.take(200)}")
                        jsonString
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
                    val hasVessel = updateData.containsKey("vessel")
                    val hasVesselNo = updateData.containsKey("vesselNo")
                    if (!hasVessel && !hasVesselNo) {
                        existingPurchase.vessel
                    } else {
                        val raw: Any? = if (hasVessel) updateData["vessel"] else updateData["vesselNo"]
                        Logger.debug("Processing vessel - raw = $raw (hasVessel=$hasVessel hasVesselNo=$hasVesselNo)")
                        when {
                            raw == null -> null
                            raw is String -> raw
                            else -> raw.toString()
                        }
                    }
                },
                bookingRequested = run {
                    val raw = when {
                        updateData.containsKey("bookingRequested") -> updateData["bookingRequested"]
                        updateData.containsKey("booking_requested") -> updateData["booking_requested"]
                        else -> null
                    }
                    when {
                        raw is Boolean -> raw
                        raw is String -> raw.toBoolean()
                        raw is Number -> raw.toInt() != 0
                        else -> existingPurchase.bookingRequested
                    }
                },
                invoiceConfirmed = run {
                    val raw = if (updateData.containsKey("invoiceConfirmed")) {
                        updateData["invoiceConfirmed"]
                    } else {
                        updateData["invoice_confirmed"]
                    }
                    when {
                        raw is Boolean -> raw
                        raw is String -> raw.toBoolean()
                        raw is Number -> raw.toInt() != 0
                        else -> existingPurchase.invoiceConfirmed
                    }
                },
                workflowStatus = existingPurchase.workflowStatus,
                workflowStatusUpdatedAt = existingPurchase.workflowStatusUpdatedAt,
                updatedAt = java.time.LocalDateTime.now()
            )
            
            Logger.debug("Saving updated purchase - shipmentDate: ${updatedPurchase.shipmentDate}, bookingId: ${updatedPurchase.bookingId}, vessel: ${updatedPurchase.vessel}")

            assertNoDuplicatePurchase(
                updatedPurchase.chassis,
                excludeId = id,
            )
            
            // CRITICAL: Ensure the entity ID is set correctly for JPA to recognize it as an update
            val purchaseToSave = if (updatedPurchase.id != null) {
                updatedPurchase
            } else {
                updatedPurchase.copy(id = id)
            }
            
            Logger.debug("Entity ID before save: ${purchaseToSave.id}")
            purchaseChangeHistoryService.recordPurchasePartialEdit(
                existingPurchase,
                purchaseToSave,
                onlyFields = auditChangedFields,
            )
            val savedPurchase = persistPurchaseAndFlush(purchaseToSave)
            // CRITICAL: Fetch fresh entity from database to ensure we get the actual saved value
            // This prevents JPA from returning a cached/stale entity
            val freshPurchase = purchaseRepository.findById(id).orElse(null)
            val purchaseToReturn = freshPurchase ?: savedPurchase
            
            Logger.debug("Successfully saved purchase - ID: ${purchaseToReturn.id}, shipmentDate: ${purchaseToReturn.shipmentDate}, bookingId: ${purchaseToReturn.bookingId}")
            
            // Verify the saved value matches what we intended
            if (purchaseToReturn.bookingId != updatedPurchase.bookingId) {
                Logger.warn("WARNING: Saved bookingId (${purchaseToReturn.bookingId}) does not match intended value (${updatedPurchase.bookingId}). This indicates a potential JPA entity state issue!")
            } else {
                Logger.debug("Verified: Saved bookingId matches intended value")
            }

            purchaseWorkflowService.recomputeByPurchaseId(id)
            val refreshed = purchaseRepository.findById(id).orElse(purchaseToSave)
            return finalizePurchaseWrite(
                purchaseToSave.copy(
                    id = refreshed.id,
                    createdAt = refreshed.createdAt,
                    workflowStatus = refreshed.workflowStatus,
                    workflowStatusUpdatedAt = refreshed.workflowStatusUpdatedAt,
                    bookingRequested = refreshed.bookingRequested,
                    invoiceConfirmed = refreshed.invoiceConfirmed,
                    bookingId = refreshed.bookingId,
                    updatedAt = refreshed.updatedAt,
                ),
                // Edit/Update: snapshot mapping-inherited specs onto the purchase so they persist.
                snapshotSpecs = true,
            )
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
    fun markPurchasesAsBookingRequested(purchaseIds: List<Long>): List<Purchase> {
        Logger.log("Marking ${purchaseIds.size} purchases as booking_requested: $purchaseIds")
        val updatedPurchases = mutableListOf<Purchase>()
        
        for (id in purchaseIds) {
            val existingPurchase = purchaseRepository.findById(id).orElse(null)
            if (existingPurchase != null) {
                val updatedPurchase = existingPurchase.copy(
                    bookingRequested = true,
                    updatedAt = java.time.LocalDateTime.now()
                )
                val savedPurchase = persistPurchase(updatedPurchase)
                updatedPurchases.add(savedPurchase)
                Logger.debug("Marked purchase $id as booking_requested")
            } else {
                Logger.warn("Purchase $id not found, skipping")
            }
        }
        
        Logger.debug("Successfully marked ${updatedPurchases.size} purchases as booking_requested")
        return applyReadAdapters(
            updatedPurchases.map { up ->
                val oid = up.id ?: return@map up
                purchaseRepository.findById(oid).orElse(up)
            },
        )
    }

    /**
     * True when any purchase matching [chassisValues] is Sold ([WorkflowStatus.INVOICE_CONFIRMED]).
     * Token match is trim + case-insensitive ([PurchaseRepository.findByChassisToken]).
     */
    fun findSoldChassisTokens(chassisValues: List<String>): List<String> {
        if (chassisValues.isEmpty()) return emptyList()
        val sold = linkedSetOf<String>()
        for (raw in chassisValues) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            for (p in purchaseRepository.findByChassisToken(token)) {
                if (p.workflowStatus == com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED) {
                    sold.add(p.chassis.trim().ifEmpty { token })
                }
            }
        }
        return sold.toList()
    }

    /**
     * Clears booking_requested by stepping [WorkflowStatus.BOOKING_REQUESTED] → [WorkflowStatus.RIXO_CONFIRMED]
     * for matching chassis. Does not touch Sold / [WorkflowStatus.INVOICE_CONFIRMED] purchases
     * (shipping remove must reject those separately). Uses trim+ignore-case chassis match.
     */
    @Transactional
    fun unmarkBookingRequestedForChassis(chassisValues: List<String>): Int {
        if (chassisValues.isEmpty()) return 0
        var count = 0
        val seenIds = mutableSetOf<Long>()
        for (raw in chassisValues) {
            val token = raw.trim()
            if (token.isEmpty()) continue
            for (p in purchaseRepository.findByChassisToken(token)) {
                val id = p.id ?: continue
                if (!seenIds.add(id)) continue
                if (p.workflowStatus != com.automan.backend.model.WorkflowStatus.BOOKING_REQUESTED) continue
                purchaseWorkflowService.setWorkflowStatus(
                    p,
                    com.automan.backend.model.WorkflowStatus.RIXO_CONFIRMED,
                )
                count++
            }
        }
        Logger.log("Unset booking_requested for $count purchase(s) across ${chassisValues.size} chassis values")
        return count
    }

    /** Same as [unmarkBookingRequestedForChassis] but keyed by purchase ids (preferred when UI has them). */
    @Transactional
    fun unmarkBookingRequestedForPurchaseIds(purchaseIds: List<Long>): Int {
        if (purchaseIds.isEmpty()) return 0
        var count = 0
        for (id in purchaseIds.toSet()) {
            val p = purchaseRepository.findById(id).orElse(null) ?: continue
            if (p.workflowStatus != com.automan.backend.model.WorkflowStatus.BOOKING_REQUESTED) continue
            purchaseWorkflowService.setWorkflowStatus(
                p,
                com.automan.backend.model.WorkflowStatus.RIXO_CONFIRMED,
            )
            count++
        }
        Logger.log("Unset booking_requested for $count purchase(s) by id")
        return count
    }

    /** Sets [Purchase.clientId] and [Purchase.clientName] on invoice purchases (Phase 2b). */
    @Transactional
    fun linkPurchasesToClient(purchaseIds: List<Long>, clientId: Long, clientName: String) {
        val name = clientName.trim()
        for (id in purchaseIds) {
            val existing = purchaseRepository.findById(id).orElse(null) ?: continue
            val updated = existing.copy(
                clientId = clientId,
                clientName = if (name.isNotEmpty()) name else existing.clientName,
                updatedAt = java.time.LocalDateTime.now(),
            )
            purchaseRepository.save(updated)
        }
    }

    fun markPurchasesAsInvoiceConfirmed(purchaseIds: List<Long>): List<Purchase> {
        Logger.log("Marking ${purchaseIds.size} purchases as invoice_confirmed: $purchaseIds")
        val updatedPurchases = mutableListOf<Purchase>()

        for (id in purchaseIds) {
            val existingPurchase = purchaseRepository.findById(id).orElse(null)
            if (existingPurchase != null) {
                val updatedPurchase = existingPurchase.copy(
                    invoiceConfirmed = true,
                    updatedAt = java.time.LocalDateTime.now()
                )
                val savedPurchase = persistPurchase(updatedPurchase)
                updatedPurchases.add(savedPurchase)
                Logger.debug("Marked purchase $id as invoice_confirmed")
            } else {
                Logger.warn("Purchase $id not found, skipping invoice_confirmed update")
            }
        }

        Logger.debug("Successfully marked ${updatedPurchases.size} purchases as invoice_confirmed")
        return applyReadAdapters(
            updatedPurchases.map { up ->
                val oid = up.id ?: return@map up
                purchaseRepository.findById(oid).orElse(up)
            },
        )
    }

    /**
     * Marks all purchases whose chassis matches any value in [chassisValues] as invoice_confirmed.
     * Used by batch invoice creation when purchase IDs are not available.
     */
    @Transactional
    fun markPurchasesAsInvoiceConfirmedByChassis(chassisValues: List<String>) {
        if (chassisValues.isEmpty()) return
        val markedIds = mutableListOf<Long>()
        for (chassis in chassisValues) {
            val purchases = purchaseRepository.findByChassis(chassis)
            for (p in purchases) {
                if (p.workflowStatus == com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED) continue
                purchaseWorkflowService.setWorkflowStatus(p, com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED)
                p.id?.let { markedIds.add(it) }
                Logger.debug("Marked purchase (chassis=$chassis) as invoice_confirmed")
            }
        }
        Logger.log("Marked ${markedIds.size} purchase(s) as invoice_confirmed by chassis")
    }

    /**
     * Sets [Purchase.invoiceConfirmed] to false for purchases matching each chassis in [chassisValues]
     * ([PurchaseRepository.findByChassis]). Only updates rows currently true.
     * Called when invoice history rows for those chassis are removed and no other invoice line references them.
     */
    @Transactional
    fun unmarkInvoiceConfirmedForChassis(chassisValues: List<String>): Int {
        if (chassisValues.isEmpty()) return 0
        val affectedIds = mutableListOf<Long>()
        var updatedRows = 0
        for (raw in chassisValues) {
            val chassis = raw.trim()
            if (chassis.isEmpty()) continue
            val purchases = purchaseRepository.findByChassis(chassis)
            for (p in purchases) {
                if (p.workflowStatus != com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED) continue
                val next = if (p.workflowStatus == com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED) {
                    com.automan.backend.model.WorkflowStatus.BOOKING_REQUESTED
                } else {
                    p.workflowStatus ?: com.automan.backend.model.WorkflowStatus.PURCHASED
                }
                val saved = purchaseWorkflowService.setWorkflowStatus(p, next)
                updatedRows++
                saved.id?.let { affectedIds.add(it) }
            }
        }
        Logger.log("Cleared invoice_confirmed on $updatedRows purchase row(s) for ${chassisValues.size} chassis token(s)")
        return updatedRows
    }

    @Transactional
    fun markPurchasesAsRixoRequestedTrue(purchaseIds: List<Long>): List<Purchase> {
        Logger.log("Marking ${purchaseIds.size} purchases as rixo_requested=TRUE: $purchaseIds")
        val updatedPurchases = mutableListOf<Purchase>()

        for (id in purchaseIds) {
            val existingPurchase = purchaseRepository.findById(id).orElse(null)
            if (existingPurchase != null) {
                val status = existingPurchase.workflowStatus
                if (status in PurchaseWorkflowService.WORKFLOW_RIXO_CONFIRMED_OR_LATER) {
                    updatedPurchases.add(existingPurchase)
                    Logger.debug("Purchase $id already $status; leaving workflow unchanged")
                    continue
                }
                val savedPurchase = purchaseWorkflowService.setWorkflowStatus(
                    existingPurchase,
                    WorkflowStatus.RIXO_REQUESTED,
                )
                updatedPurchases.add(savedPurchase)
                Logger.debug("Marked purchase $id as rixo_requested=TRUE")
            } else {
                Logger.warn("Purchase $id not found, skipping rixo_requested update")
            }
        }

        Logger.debug("Successfully marked ${updatedPurchases.size} purchases as rixo_requested=TRUE")
        return applyReadAdapters(
            updatedPurchases.map { up ->
                val oid = up.id ?: return@map up
                purchaseRepository.findById(oid).orElse(up)
            },
        )
    }
    
    fun searchPurchases(searchTerm: String): List<Purchase> {
        return if (searchTerm.isBlank()) {
            getAllPurchases()
        } else {
            applyReadAdapters(purchaseRepository.searchPurchases(searchTerm))
        }
    }

    /**
     * Paginated browse for purchase list UI (no search text). Newest [id] first by default.
     * Optional [sortField]/[sortOrder] for column headers (whitelist only).
     * Optional [dateFrom]/[dateTo] (ISO yyyy-MM-dd) filter purchase.date labels via parse.
     */
    fun listPurchasesPage(
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): PurchasePageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val from = parseIsoLocalDateOrThrow(dateFrom, "dateFrom")
        val to = parseIsoLocalDateOrThrow(dateTo, "dateTo")
        // Purchase.date is a label string (e.g. "July 4, 2026(Saturday)"); never SQL-ORDER BY it.
        if (isPurchaseDateSortField(sortField)) {
            val pairs = if (from == null && to == null) {
                purchaseRepository.findIdAndDateAll()
            } else {
                validateDateRangeBounds(from, to)
                filterIdDatePairsAsPairs(purchaseRepository.findIdAndDateAll(), from, to)
            }
            return pagePurchasesByIdDatePairs(pairs, pageIdx, size, sortOrder)
        }
        if (from == null && to == null) {
            val pageable = PageRequest.of(pageIdx, size, resolvePurchaseListSort(sortField, sortOrder))
            val pg = purchaseRepository.findAll(pageable)
            return PurchasePageResponse(
                content = applyReadAdapters(pg.content),
                totalElements = pg.totalElements,
                totalPages = pg.totalPages,
                page = pg.number,
                size = pg.size,
            )
        }
        validateDateRangeBounds(from, to)
        val matchingIds = filterIdDatePairsByRange(purchaseRepository.findIdAndDateAll(), from, to)
        return pagePurchasesByIds(matchingIds, pageIdx, size, sortField, sortOrder)
    }

    /**
     * Paginated search for purchase list UI (chassis, car name, brand, client, supplier).
     * [field]: `all`, `chassis` (prefix match, index-friendly), `carName`, `brand`, `clientName`, `supplier`.
     * Optional [dateFrom]/[dateTo] applied after search narrowing (label-date parse).
     */
    fun searchPurchasesPage(
        rawQuery: String,
        rawField: String,
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
    ): PurchasePageResponse {
        val q = sanitizePurchaseListSearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val field = rawField.trim().lowercase().ifEmpty { "all" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val from = parseIsoLocalDateOrThrow(dateFrom, "dateFrom")
        val to = parseIsoLocalDateOrThrow(dateTo, "dateTo")
        // Chronological date sort must use parsed LocalDate, not SQL string ORDER BY date.
        if (isPurchaseDateSortField(sortField)) {
            val pairs = if (from == null && to == null) {
                searchPurchasesIdDate(q, field)
            } else {
                validateDateRangeBounds(from, to)
                filterIdDatePairsAsPairs(searchPurchasesIdDate(q, field), from, to)
            }
            return pagePurchasesByIdDatePairs(pairs, pageIdx, size, sortOrder)
        }
        if (from == null && to == null) {
            val pageable = PageRequest.of(pageIdx, size, resolvePurchaseListSort(sortField, sortOrder))
            val pg: Page<Purchase> = searchPurchasesPageEntity(q, field, pageable)
            return PurchasePageResponse(
                content = applyReadAdapters(pg.content),
                totalElements = pg.totalElements,
                totalPages = pg.totalPages,
                page = pg.number,
                size = pg.size,
            )
        }
        validateDateRangeBounds(from, to)
        val pairs = searchPurchasesIdDate(q, field)
        val matchingIds = filterIdDatePairsByRange(pairs, from, to)
        return pagePurchasesByIds(matchingIds, pageIdx, size, sortField, sortOrder)
    }

    /**
     * Unified filtered page: search + date range + advanced filter chips.
     * DB-column filters narrow by ID queries; Transient fields hydrate candidates then filter in memory.
     */
    fun filterPurchasesPage(request: PurchasePageFilterRequest): PurchasePageResponse {
        val pageIdx = request.page.coerceAtLeast(0)
        val size = request.size.coerceIn(1, 100)
        val from = parseIsoLocalDateOrThrow(request.dateFrom, "dateFrom")
        val to = parseIsoLocalDateOrThrow(request.dateTo, "dateTo")
        if (from != null || to != null) validateDateRangeBounds(from, to)

        val q = request.q?.let { sanitizePurchaseListSearchToken(it) }.orEmpty()
        val searchField = request.field?.trim()?.lowercase().orEmpty().ifEmpty { "all" }
        val clauses = request.filters
            .map { it.copy(field = it.field.trim(), operator = it.operator.trim().ifEmpty { "contains" }, value = it.value.trim()) }
            .filter { it.field.isNotEmpty() && it.value.isNotEmpty() }

        val (dbClauses, transientClauses) = clauses.partition { isDbColumnFilterField(it.field) }

        // 1) Candidate id+date (search-scoped or all)
        val pairs: List<PurchaseIdDateProjection> = if (q.isNotEmpty()) {
            searchPurchasesIdDate(q, searchField)
        } else {
            purchaseRepository.findIdAndDateAll()
        }

        // 2) Date range on labeled purchase.date
        var candidateIds: Set<Long> = if (from != null || to != null) {
            filterIdDatePairsByRange(pairs, from, to).toSet()
        } else {
            pairs.mapNotNull { it.getId() }.toSet()
        }
        if (candidateIds.isEmpty()) {
            return emptyPurchasePage(pageIdx, size)
        }

        // 3) DB-column advanced filters (ID intersection)
        for (clause in dbClauses) {
            candidateIds = applyDbColumnFilter(candidateIds, clause)
            if (candidateIds.isEmpty()) return emptyPurchasePage(pageIdx, size)
        }

        // 4) Transient filters → hydrate remaining candidates, filter in memory
        if (transientClauses.isNotEmpty()) {
            val hydrated = applyReadAdapters(purchaseRepository.findAllById(candidateIds))
            val filtered = hydrated.filter { p ->
                transientClauses.all { clause -> purchaseMatchesAdvancedFilter(p, clause) }
            }
            return pagePurchasesInMemory(filtered, pageIdx, size, request.sort, request.order)
        }

        // 5) DB-only path: chronological date sort via id+date pairs; other sorts via findByIdIn + Sort
        if (isPurchaseDateSortField(request.sort)) {
            val datePairs = pairs.filter { it.getId() != null && it.getId() in candidateIds }
            return pagePurchasesByIdDatePairs(datePairs, pageIdx, size, request.order)
        }
        return pagePurchasesByIds(candidateIds.toList(), pageIdx, size, request.sort, request.order)
    }

    private fun searchPurchasesPageEntity(q: String, field: String, pageable: PageRequest): Page<Purchase> =
        when (field) {
            "chassis" -> purchaseRepository.searchPurchasesChassisPrefixPage(q, pageable)
            "carname", "car_name" -> purchaseRepository.searchPurchasesCarNameContainsPage(q, pageable)
            "brand" -> purchaseRepository.searchPurchasesBrandContainsPage(q, pageable)
            "clientname", "client_name", "client" -> purchaseRepository.searchPurchasesClientNameContainsPage(q, pageable)
            "supplier", "suppliername", "auctionhouse", "auction_house" ->
                purchaseRepository.searchPurchasesSupplierContainsPage(q, pageable)
            "all" -> purchaseRepository.searchPurchasesKeyFieldsContains(q, pageable)
            else -> throw IllegalArgumentException(
                "Invalid search field: $field. Use all, chassis, carName, brand, clientName, or supplier.",
            )
        }

    private fun searchPurchasesIdDate(q: String, field: String): List<PurchaseIdDateProjection> =
        when (field) {
            "chassis" -> purchaseRepository.searchPurchasesChassisPrefixIdDate(q)
            "carname", "car_name" -> purchaseRepository.searchPurchasesCarNameContainsIdDate(q)
            "brand" -> purchaseRepository.searchPurchasesBrandContainsIdDate(q)
            "clientname", "client_name", "client" -> purchaseRepository.searchPurchasesClientNameContainsIdDate(q)
            "supplier", "suppliername", "auctionhouse", "auction_house" ->
                purchaseRepository.searchPurchasesSupplierContainsIdDate(q)
            "all" -> purchaseRepository.searchPurchasesKeyFieldsIdDate(q)
            else -> throw IllegalArgumentException(
                "Invalid search field: $field. Use all, chassis, carName, brand, clientName, or supplier.",
            )
        }

    private fun parseIsoLocalDateOrThrow(raw: String?, label: String): LocalDate? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t)
        } catch (_: DateTimeParseException) {
            throw IllegalArgumentException("Invalid $label: $t (expected yyyy-MM-dd)")
        }
    }

    private fun parseIsoLocalDateOrNull(raw: String?): LocalDate? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun validateDateRangeBounds(from: LocalDate?, to: LocalDate?) {
        if (from != null && to != null) {
            if (from.isAfter(to)) {
                throw IllegalArgumentException("dateFrom must be on or before dateTo")
            }
            if (ChronoUnit.DAYS.between(from, to) > 366) {
                throw IllegalArgumentException("Date range must be at most 366 days")
            }
        }
    }

    private fun filterIdDatePairsByRange(
        pairs: List<PurchaseIdDateProjection>,
        from: LocalDate?,
        to: LocalDate?,
    ): List<Long> = filterIdDatePairsAsPairs(pairs, from, to).mapNotNull { it.getId() }

    private fun filterIdDatePairsAsPairs(
        pairs: List<PurchaseIdDateProjection>,
        from: LocalDate?,
        to: LocalDate?,
    ): List<PurchaseIdDateProjection> {
        return pairs.filter { pair ->
            if (pair.getId() == null) return@filter false
            val parsed = PurchaseDateParseUtils.parseToLocalDate(pair.getDate()?.trim().orEmpty())
                ?: return@filter false
            if (from != null && parsed.isBefore(from)) return@filter false
            if (to != null && parsed.isAfter(to)) return@filter false
            true
        }
    }

    private fun isPurchaseDateSortField(sortField: String?): Boolean =
        sortField?.trim()?.equals("date", ignoreCase = true) == true

    /**
     * Page purchases sorted by real calendar date (not lexicographic label text).
     * Unparseable dates sort last (asc) / first after reverse (desc); ties break by id.
     */
    private fun pagePurchasesByIdDatePairs(
        pairs: List<PurchaseIdDateProjection>,
        pageIdx: Int,
        size: Int,
        sortOrder: String?,
    ): PurchasePageResponse {
        if (pairs.isEmpty()) return emptyPurchasePage(pageIdx, size)
        val asc = sortOrder?.trim().equals("asc", ignoreCase = true) == true
        val sortedPairs = pairs.sortedWith { a, b ->
            val cmp = compareIdDatePairsChronological(a, b)
            if (asc) cmp else -cmp
        }
        val orderedIds = sortedPairs.mapNotNull { it.getId() }
        return pagePurchasesPreservingIdOrder(orderedIds, pageIdx, size)
    }

    private fun compareIdDatePairsChronological(
        a: PurchaseIdDateProjection,
        b: PurchaseIdDateProjection,
    ): Int {
        val aDate = PurchaseDateParseUtils.parseToLocalDate(a.getDate()?.trim().orEmpty())
        val bDate = PurchaseDateParseUtils.parseToLocalDate(b.getDate()?.trim().orEmpty())
        val dateCmp = when {
            aDate == null && bDate == null -> 0
            aDate == null -> 1
            bDate == null -> -1
            else -> aDate.compareTo(bDate)
        }
        if (dateCmp != 0) return dateCmp
        return (a.getId() ?: 0L).compareTo(b.getId() ?: 0L)
    }

    /** Hydrate a page of purchases in the exact order of [orderedIds]. */
    private fun pagePurchasesPreservingIdOrder(
        orderedIds: List<Long>,
        pageIdx: Int,
        size: Int,
    ): PurchasePageResponse {
        val total = orderedIds.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        val fromIdx = (pageIdx * size).coerceAtMost(orderedIds.size)
        val toIdx = (fromIdx + size).coerceAtMost(orderedIds.size)
        val pageIds = if (fromIdx >= orderedIds.size) emptyList() else orderedIds.subList(fromIdx, toIdx)
        if (pageIds.isEmpty()) {
            return PurchasePageResponse(
                content = emptyList(),
                totalElements = total,
                totalPages = totalPages,
                page = pageIdx,
                size = size,
            )
        }
        val byId = purchaseRepository.findAllById(pageIds).associateBy { it.id }
        val content = applyReadAdapters(pageIds.mapNotNull { byId[it] })
        return PurchasePageResponse(
            content = content,
            totalElements = total,
            totalPages = totalPages,
            page = pageIdx,
            size = size,
        )
    }

    private fun pagePurchasesByIds(
        matchingIds: List<Long>,
        pageIdx: Int,
        size: Int,
        sortField: String?,
        sortOrder: String?,
    ): PurchasePageResponse {
        if (matchingIds.isEmpty()) return emptyPurchasePage(pageIdx, size)
        // Safety net: if any caller still passes sort=date with bare ids, chronologically re-order.
        if (isPurchaseDateSortField(sortField)) {
            val idSet = matchingIds.toSet()
            val pairs = purchaseRepository.findIdAndDateAll().filter { it.getId() in idSet }
            return pagePurchasesByIdDatePairs(pairs, pageIdx, size, sortOrder)
        }
        val pageable = PageRequest.of(pageIdx, size, resolvePurchaseListSort(sortField, sortOrder))
        val pg = purchaseRepository.findByIdIn(matchingIds, pageable)
        return PurchasePageResponse(
            content = applyReadAdapters(pg.content),
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    private fun pagePurchasesInMemory(
        purchases: List<Purchase>,
        pageIdx: Int,
        size: Int,
        sortField: String?,
        sortOrder: String?,
    ): PurchasePageResponse {
        val sorted = sortPurchasesForList(purchases, sortField, sortOrder)
        val total = sorted.size.toLong()
        val totalPages = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        val fromIdx = (pageIdx * size).coerceAtMost(sorted.size)
        val toIdx = (fromIdx + size).coerceAtMost(sorted.size)
        val slice = if (fromIdx >= sorted.size) emptyList() else sorted.subList(fromIdx, toIdx)
        return PurchasePageResponse(
            content = slice,
            totalElements = total,
            totalPages = totalPages,
            page = pageIdx,
            size = size,
        )
    }

    private fun emptyPurchasePage(pageIdx: Int, size: Int): PurchasePageResponse =
        PurchasePageResponse(
            content = emptyList(),
            totalElements = 0,
            totalPages = 0,
            page = pageIdx,
            size = size,
        )

    /**
     * Whitelisted JPA sort for purchase list page/search.
     * [date] is intentionally omitted — label strings must not be SQL-ordered; use
     * [pagePurchasesByIdDatePairs] / [sortPurchasesForList] instead.
     */
    private fun resolvePurchaseListSort(sortField: String?, sortOrder: String?): Sort {
        val dir = if (sortOrder?.trim().equals("asc", ignoreCase = true) == true) {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        val prop = when (sortField?.trim()?.lowercase()) {
            null, "", "id", "date" -> "id"
            "chassis" -> "chassis"
            "carname", "car_name" -> "carName"
            "brand" -> "brand"
            "clientname", "client_name", "client" -> "clientName"
            "auctionhouse", "auction_house", "supplier", "suppliername" -> "auctionHouse"
            "stocklocation", "stock_location" -> "stockLocation"
            "rixocompany", "rixo_company" -> "rixoCompany"
            "country" -> "country"
            "repaircompany", "repair_company" -> "repairCompany"
            else -> "id"
        }
        return Sort.by(dir, prop)
    }

    private fun sortPurchasesForList(
        purchases: List<Purchase>,
        sortField: String?,
        sortOrder: String?,
    ): List<Purchase> {
        val asc = sortOrder?.trim().equals("asc", ignoreCase = true) == true
        val key = sortField?.trim()?.lowercase().orEmpty()
        fun cmp(a: String?, b: String?): Int {
            val left = a?.trim().orEmpty()
            val right = b?.trim().orEmpty()
            return left.compareTo(right, ignoreCase = true)
        }
        fun cmpPurchaseDate(a: Purchase, b: Purchase): Int {
            val aDate = PurchaseDateParseUtils.parseToLocalDate(a.date?.trim().orEmpty())
            val bDate = PurchaseDateParseUtils.parseToLocalDate(b.date?.trim().orEmpty())
            val dateCmp = when {
                aDate == null && bDate == null -> cmp(a.date, b.date)
                aDate == null -> 1
                bDate == null -> -1
                else -> aDate.compareTo(bDate)
            }
            if (dateCmp != 0) return dateCmp
            return (a.id ?: 0L).compareTo(b.id ?: 0L)
        }
        val sorted = when (key) {
            "", "id" -> purchases.sortedBy { it.id ?: 0L }
            "chassis" -> purchases.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.chassis })
            "date" -> purchases.sortedWith { a, b -> cmpPurchaseDate(a, b) }
            "carname", "car_name" -> purchases.sortedWith { a, b -> cmp(a.carName, b.carName) }
            "brand" -> purchases.sortedWith { a, b -> cmp(a.brand, b.brand) }
            "clientname", "client_name", "client" -> purchases.sortedWith { a, b -> cmp(a.clientName, b.clientName) }
            "auctionhouse", "auction_house", "supplier", "suppliername" ->
                purchases.sortedWith { a, b -> cmp(a.auctionHouse, b.auctionHouse) }
            "stocklocation", "stock_location" -> purchases.sortedWith { a, b -> cmp(a.stockLocation, b.stockLocation) }
            "rixocompany", "rixo_company" -> purchases.sortedWith { a, b -> cmp(a.rixoCompany, b.rixoCompany) }
            "country" -> purchases.sortedWith { a, b -> cmp(a.country, b.country) }
            "repaircompany", "repair_company" -> purchases.sortedWith { a, b -> cmp(a.repairCompany, b.repairCompany) }
            else -> purchases.sortedBy { it.id ?: 0L }
        }
        return if (asc) sorted else sorted.asReversed()
    }

    private fun sanitizePurchaseListSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    private fun normalizeFilterFieldKey(raw: String): String =
        raw.trim().lowercase().replace("_", "")

    /** Persistable `@Column` fields safe for JPQL contains narrowing. */
    private fun isDbColumnFilterField(field: String): Boolean =
        when (normalizeFilterFieldKey(field)) {
            "chassis", "brand", "carname",
            "auctionhouse", "supplier", "suppliername",
            "stocklocation", "pol", "pod", "destination",
            "rixocompany", "clientname", "client",
            "consignee", "country", "totalprice",
            "repaircompany", "bookingid", "manufactureyear",
            "workflowstatus",
            "date", // handled via label parse (equals day), not LIKE
            -> true
            else -> false
        }

    private fun applyDbColumnFilter(candidateIds: Set<Long>, clause: PurchasePageFilterClause): Set<Long> {
        if (candidateIds.isEmpty()) return candidateIds
        val key = normalizeFilterFieldKey(clause.field)
        val value = clause.value.trim()
        if (value.isEmpty()) return candidateIds

        if (key == "date") {
            val day = parseIsoLocalDateOrNull(value)
                ?: PurchaseDateParseUtils.parseToLocalDate(value)
                ?: return emptySet()
            val pairs = purchaseRepository.findIdAndDateAll()
            val dayIds = filterIdDatePairsByRange(pairs, day, day).toSet()
            return candidateIds.intersect(dayIds)
        }

        val matchingIds: Set<Long> = when (key) {
            "chassis" -> purchaseRepository.findIdsChassisContains(value).toSet()
            "brand" -> purchaseRepository.findIdsBrandContains(value).toSet()
            "carname" -> purchaseRepository.findIdsCarNameContains(value).toSet()
            "auctionhouse", "supplier", "suppliername" ->
                purchaseRepository.findIdsAuctionHouseContains(value).toSet()
            "stocklocation" -> purchaseRepository.findIdsStockLocationContains(value).toSet()
            "pol" -> purchaseRepository.findIdsPolContains(value).toSet()
            "pod", "destination" -> purchaseRepository.findIdsPodContains(value).toSet()
            "rixocompany" -> purchaseRepository.findIdsRixoCompanyContains(value).toSet()
            "clientname", "client" -> purchaseRepository.findIdsClientNameContains(value).toSet()
            "consignee" -> purchaseRepository.findIdsConsigneeContains(value).toSet()
            "country" -> purchaseRepository.findIdsCountryContains(value).toSet()
            "totalprice" -> purchaseRepository.findIdsTotalPriceContains(value).toSet()
            "repaircompany" -> purchaseRepository.findIdsRepairCompanyContains(value).toSet()
            "manufactureyear" -> purchaseRepository.findIdsManufactureYearContains(value).toSet()
            "bookingid" -> purchaseRepository.findIdsBookingIdContains(value).toSet()
            "workflowstatus" -> purchaseRepository.findIdsWorkflowStatusContains(value).toSet()
            else -> {
                Logger.warn("Ignoring unsupported DB filter field: ${clause.field}")
                return candidateIds
            }
        }
        return candidateIds.intersect(matchingIds)
    }

    private val moneyFilterFields: Set<String> = setOf(
        "price", "totalprice", "auctionfee", "auctionpenaltyfee", "recyclefee", "roadtax", "rixoprice",
        "shipmentcharges", "freight", "storagecharges", "misccharges", "inspectionfee", "commission",
        "repaircharges", "taxtotal", "profit",
    )

    private val plainIntFilterFields: Set<String> = setOf("cc", "seat", "door")
    private val commaIntFilterFields: Set<String> = setOf("distance")
    private val dateFilterFields: Set<String> = setOf("date", "paymentdate", "shipmentdate", "carmodelyear")

    /**
     * Mirrors FE [purchaseMatchesFilter]: date → day/month equals; money/int → numeric or digit contains;
     * else text contains (case-insensitive).
     */
    private fun purchaseMatchesAdvancedFilter(p: Purchase, clause: PurchasePageFilterClause): Boolean {
        val key = normalizeFilterFieldKey(clause.field)
        val v = clause.value.trim()
        if (v.isEmpty()) return true

        if (key in dateFilterFields) {
            if (key == "carmodelyear") {
                val rowYm = normalizeYearMonth(purchaseFieldRaw(p, "carModelYear")) ?: return false
                val filterYm = normalizeYearMonth(v) ?: return false
                return rowYm == filterYm
            }
            val rowDate = PurchaseDateParseUtils.parseToLocalDate(purchaseFieldRaw(p, clause.field))
                ?: return false
            val filterDate = parseIsoLocalDateOrNull(v)
                ?: PurchaseDateParseUtils.parseToLocalDate(v)
                ?: return false
            return rowDate == filterDate
        }

        if (key in moneyFilterFields || key in plainIntFilterFields || key in commaIntFilterFields) {
            return purchaseNumericFilterMatch(p, clause.field, v)
        }

        val text = purchaseFieldRaw(p, clause.field).lowercase()
        return text.contains(v.lowercase())
    }

    private fun purchaseNumericFilterMatch(p: Purchase, field: String, filterValue: String): Boolean {
        val filterNum = parseLooseNumber(filterValue)
        if (filterNum != null) {
            val rowNum = parseLooseNumber(purchaseFieldRaw(p, field))
            if (rowNum != null && rowNum == filterNum) return true
        }
        val rowDigits = purchaseFieldRaw(p, field).replace(Regex("[^0-9]"), "")
        val filterDigits = filterValue.replace(Regex("[^0-9]"), "")
        if (filterDigits.isEmpty()) return false
        return rowDigits.contains(filterDigits)
    }

    private fun parseLooseNumber(raw: String): Double? {
        val cleaned = raw.replace(",", "").replace(Regex("[^0-9.\\-]"), "").trim()
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null
        return cleaned.toDoubleOrNull()
    }

    private fun normalizeYearMonth(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        if (t.matches(Regex("^\\d{4}-\\d{2}$"))) return t
        val slash = Regex("^(\\d{1,2})/(\\d{4})$").matchEntire(t)
        if (slash != null) {
            val m = slash.groupValues[1].toIntOrNull() ?: return null
            val y = slash.groupValues[2]
            if (m in 1..12) return "$y-${m.toString().padStart(2, '0')}"
        }
        // Fallback: yyyy-MM embedded or year-only → reject for month-equals filter
        if (t.contains("-")) {
            val parts = t.split("-")
            if (parts.size >= 2) {
                val y = parts[0].trim()
                val m = parts[1].trim().take(2)
                if (y.length == 4 && y.all { it.isDigit() } && m.length == 2 && m.all { it.isDigit() }) {
                    return "$y-$m"
                }
            }
        }
        return null
    }

    private fun purchaseFieldRaw(p: Purchase, field: String): String {
        val key = normalizeFilterFieldKey(field)
        return when (key) {
            "date" -> p.date.orEmpty()
            "chassis" -> p.chassis
            "brand" -> p.brand.orEmpty()
            "carname" -> p.carName.orEmpty()
            "carmodelyear" -> p.carModelYear.orEmpty()
            "shipmentsize", "vehicletype" -> p.shipmentSize.orEmpty()
            "grade" -> p.grade.orEmpty()
            "rank" -> p.rank.orEmpty()
            "color" -> p.color.orEmpty()
            "fuel" -> p.fuel.orEmpty()
            "seat" -> p.seat.orEmpty()
            "door" -> p.door.orEmpty()
            "distance" -> p.distance.orEmpty()
            "options" -> p.options.orEmpty()
            "cc" -> p.cc?.toString().orEmpty()
            "shift" -> p.shift.orEmpty()
            "wd" -> p.wd.orEmpty()
            "drivetype" -> p.driveType.orEmpty()
            "auctionno" -> p.auctionNo.orEmpty()
            "auctionhouse", "supplier", "suppliername" -> p.auctionHouse.orEmpty()
            "stocklocation" -> p.stockLocation.orEmpty()
            "pol" -> p.pol.orEmpty()
            "pod", "destination" -> p.pod.orEmpty()
            "rixocompany" -> p.rixoCompany.orEmpty()
            "clientname", "client" -> p.clientName.orEmpty()
            "consignee" -> p.consignee.orEmpty()
            "clientid" -> p.clientId?.toString().orEmpty()
            "country" -> p.country.orEmpty()
            "price" -> p.price.orEmpty()
            "auctionfee" -> p.auctionFee.orEmpty()
            "auctionpenaltyfee" -> p.auctionPenaltyFee.orEmpty()
            "recyclefee" -> p.recycleFee.orEmpty()
            "roadtax" -> p.roadTax.orEmpty()
            "taxtotal" -> p.taxTotal.orEmpty()
            "totalprice" -> p.totalPrice.orEmpty()
            "paymentdate" -> p.paymentDate.orEmpty()
            "rixorequested" -> p.rixoRequested.orEmpty()
            "rixoconfirmed" -> p.rixoConfirmed.orEmpty()
            "notes" -> p.notes.orEmpty()
            "shipmentdate" -> p.shipmentDate.orEmpty()
            "blno" -> p.blNo.orEmpty()
            "vessel", "vesselno" -> p.vessel.orEmpty()
            "bookingrequested" -> p.bookingRequested.toString()
            "invoiceconfirmed" -> p.invoiceConfirmed?.toString().orEmpty()
            "workflowstatus" -> p.workflowStatus?.name.orEmpty()
            "shipmentcharges" -> p.shipmentCharges.orEmpty()
            "freight" -> p.freight.orEmpty()
            "storagecharges" -> p.storageCharges.orEmpty()
            "misccharges" -> p.miscCharges.orEmpty()
            "inspectionfee" -> p.inspectionFee.orEmpty()
            "commission" -> p.commission.orEmpty()
            "rixoprice" -> p.rixoPrice.orEmpty()
            "venueid" -> p.venueId.orEmpty()
            "numbercut" -> p.numberCut.orEmpty()
            "shaken" -> p.shaken?.toString().orEmpty()
            "negotiate" -> p.negotiate?.toString().orEmpty()
            "local" -> p.local.toString()
            "manufactureyear" -> p.manufactureYear.orEmpty()
            "repaircompany" -> p.repairCompany.orEmpty()
            "repaircharges" -> p.repairCharges.orEmpty()
            "profit" -> p.profit?.toPlainString().orEmpty()
            "ispackagemode" -> p.isPackageMode?.toString().orEmpty()
            "bookingid" -> p.bookingId?.toString().orEmpty()
            "carpictures" -> p.carPictures.orEmpty()
            else -> ""
        }
    }

    /**
     * Car booking "SEARCH CHASSIS": prefix/substring on chassis for rows that are Rixo-confirmed
     * (`rixoConfirmed` `1`/`TRUE`) and booking not requested yet.
     */
    fun searchChassisForBooking(rawQuery: String, maxResults: Int = 50): List<Purchase> {
        val q = sanitizeChassisSearchToken(rawQuery)
        if (q.isEmpty()) return emptyList()
        val cap = maxResults.coerceIn(1, 100)
        val prefix = purchaseRepository.searchByChassisPrefix(q, PageRequest.of(0, cap))
        if (prefix.size >= cap) return applyReadAdapters(prefix)
        val need = cap - prefix.size
        val prefixIds = prefix.mapNotNull { it.id }.toSet()
        val contains = purchaseRepository.searchByChassisContains(q, PageRequest.of(0, need + 20))
            .filter { p -> p.id != null && p.id !in prefixIds }
            .take(need)
        return applyReadAdapters(prefix + contains)
    }

    private fun sanitizeChassisSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(100)
    
    fun sortPurchases(field: String, order: String): List<Purchase> {
        val allPurchases = getAllPurchases()
        fun cmpPurchaseDate(a: Purchase, b: Purchase): Int {
            val aDate = PurchaseDateParseUtils.parseToLocalDate(a.date?.trim().orEmpty())
            val bDate = PurchaseDateParseUtils.parseToLocalDate(b.date?.trim().orEmpty())
            return when {
                aDate == null && bDate == null ->
                    (a.date ?: "").compareTo(b.date ?: "", ignoreCase = true)
                aDate == null -> 1
                bDate == null -> -1
                else -> aDate.compareTo(bDate)
            }
        }
        return when (field) {
            "date" -> if (order == "asc") {
                allPurchases.sortedWith { a, b -> cmpPurchaseDate(a, b) }
            } else {
                allPurchases.sortedWith { a, b -> cmpPurchaseDate(b, a) }
            }
            "chassis" -> if (order == "asc") allPurchases.sortedBy { it.chassis } else allPurchases.sortedByDescending { it.chassis }
            "carName" -> if (order == "asc") allPurchases.sortedBy { it.carName } else allPurchases.sortedByDescending { it.carName }
            "auctionHouse" -> if (order == "asc") allPurchases.sortedBy { it.auctionHouse } else allPurchases.sortedByDescending { it.auctionHouse }
            "stockLocation" -> if (order == "asc") allPurchases.sortedBy { it.stockLocation } else allPurchases.sortedByDescending { it.stockLocation }
            "rixoCompany" -> if (order == "asc") allPurchases.sortedBy { it.rixoCompany } else allPurchases.sortedByDescending { it.rixoCompany }
            "clientName" -> if (order == "asc") allPurchases.sortedBy { it.clientName } else allPurchases.sortedByDescending { it.clientName }
            "country" -> if (order == "asc") allPurchases.sortedBy { it.country } else allPurchases.sortedByDescending { it.country }
            "brand" -> if (order == "asc") allPurchases.sortedBy { it.brand } else allPurchases.sortedByDescending { it.brand }
            "repairCompany" -> if (order == "asc") allPurchases.sortedBy { it.repairCompany } else allPurchases.sortedByDescending { it.repairCompany }
            else -> allPurchases
        }
    }
    
    fun filterByCarName(carName: String): List<Purchase> {
        return applyReadAdapters(purchaseRepository.findByCarNameContainingIgnoreCase(carName))
    }

    fun filterByAuctionHouse(auctionHouse: String): List<Purchase> {
        return applyReadAdapters(purchaseRepository.findByAuctionHouseContainingIgnoreCase(auctionHouse))
    }

    fun filterByClientName(clientName: String): List<Purchase> {
        return applyReadAdapters(purchaseRepository.findByClientNameContainingIgnoreCase(clientName))
    }

    fun filterByDate(date: String): List<Purchase> {
        return applyReadAdapters(purchaseRepository.findByDateContainingIgnoreCase(date))
    }

    fun filterByConsigneeAndVesselAndShipmentDate(consignee: String?, vessel: String?, shipmentDate: String?): List<Purchase> {
        val candidates = purchaseRepository.findInvoiceFilterCandidatesByConsignee(consignee)
        return filterInvoiceCandidates(candidates, vessel, shipmentDate)
    }

    fun filterByClientNameAndVesselAndShipmentDate(clientName: String?, vessel: String?, shipmentDate: String?): List<Purchase> {
        val candidates = purchaseRepository.findInvoiceFilterCandidatesByClientName(clientName)
        return filterInvoiceCandidates(candidates, vessel, shipmentDate)
    }

    /** Phase 4b: vessel/shipmentDate compared after shipping_history read adapter. */
    private fun filterInvoiceCandidates(
        candidates: List<Purchase>,
        vessel: String?,
        shipmentDate: String?,
    ): List<Purchase> {
        val resolved = applyReadAdapters(candidates)
        return resolved.filter { purchase ->
            matchesInvoiceVessel(purchase, vessel) && matchesInvoiceShipmentDate(purchase, shipmentDate)
        }
    }

    private fun matchesInvoiceVessel(purchase: Purchase, vessel: String?): Boolean {
        if (vessel.isNullOrBlank()) return true
        return purchase.vessel?.trim()?.equals(vessel.trim(), ignoreCase = true) == true
    }

    private fun matchesInvoiceShipmentDate(purchase: Purchase, shipmentDate: String?): Boolean {
        if (shipmentDate.isNullOrBlank()) return true
        return purchase.shipmentDate?.trim() == shipmentDate.trim()
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
                        // Upsert behavior:
                        // If this chassis already exists, update only the key fields that are often missing in imports:
                        // - date (from "Purchase Date")
                        // - auctionHouse (from "Supplier Name" / "AUCTION HOUSE")
                        // - country (from "Target Country")
                        // This prevents "re-import" from leaving those columns blank.
                        val existing = purchaseRepository.findByChassis(purchase.chassis).firstOrNull()
                        val resolvedClientId = purchase.clientId ?: resolveClientIdFromName(purchase.clientName)
                        val purchaseToPersist = purchase.copy(clientId = resolvedClientId)
                        val persisted = if (existing != null) {
                            val updated = existing.copy(
                                date = if (!purchase.date.isNullOrBlank()) purchase.date else existing.date,
                                auctionHouse = if (!purchase.auctionHouse.isNullOrBlank()) purchase.auctionHouse else existing.auctionHouse,
                                country = if (!purchase.country.isNullOrBlank()) purchase.country else existing.country,
                                clientName = if (!purchase.clientName.isNullOrBlank()) purchase.clientName else existing.clientName,
                                clientId = if (!purchase.clientName.isNullOrBlank()) {
                                    resolveClientIdFromName(purchase.clientName) ?: existing.clientId
                                } else {
                                    existing.clientId
                                },
                            )
                            persistPurchase(updated)
                        } else {
                            persistPurchase(purchaseToPersist)
                        }
                        savedPurchases.add(
                            finalizePurchaseWrite(
                                purchaseToPersist.copy(
                                    id = persisted.id,
                                    createdAt = persisted.createdAt,
                                    updatedAt = persisted.updatedAt,
                                    bookingId = persisted.bookingId,
                                    bookingRequested = persisted.bookingRequested,
                                    invoiceConfirmed = persisted.invoiceConfirmed,
                                    workflowStatus = persisted.workflowStatus,
                                    workflowStatusUpdatedAt = persisted.workflowStatusUpdatedAt,
                                ),
                            ),
                        )
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
                purchaseWorkflowService.recomputeByPurchaseIds(savedPurchases.mapNotNull { it.id })
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
            val hasDate = headers.contains("DATE") ||
                headers.contains("PURCHASE DATE") ||
                headers.contains("PURCHASE_DATE") ||
                headers.contains("PURCHASEDATE")
            val hasCarName = headers.contains("CAR NAME") || headers.contains("CARNAME")
            val hasAuction = headers.contains("AUCTION HOUSE") ||
                headers.contains("AUCTION NAME") ||
                headers.contains("AUCTION") ||
                headers.contains("SUPPLIER NAME")
            
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
                    "SUPPLIER NAME" -> mapping["AUCTION HOUSE"] = index
                    "RXO CONFIRMED" -> mapping["RIXO CONFIRMED"] = index
                    "RIXO CHARGES" -> mapping["RIXO PRICE"] = index
                    "B/L NO" -> mapping["B/L NO."] = index
                    "VESSEL NO" -> mapping["VESSEL NO."] = index
                    "AUCTION NO" -> mapping["AUCTION NO."] = index
                    "TARGET COUNTRY" -> mapping["COUNTRY"] = index
                    "TARGET_COUNTRY" -> mapping["COUNTRY"] = index
                    "PURCHASE DATE" -> mapping["DATE"] = index
                    "PURCHASE_DATE" -> mapping["DATE"] = index
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
            date = getColumnValue(
                values,
                columnMapping,
                "DATE",
                "PURCHASE DATE",
                "PURCHASE_DATE",
                "PURCHASEDATE"
            )?.let { convertJapaneseDateToEnglish(it) }?.take(255) ?: "",
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
            // Your UI calls this column "Supplier Name", but the DB column is `auction_house`.
            auctionHouse = getColumnValue(
                values,
                columnMapping,
                "AUCTION HOUSE",
                "AUCTION NAME",
                "AUCTION",
                "SUPPLIER NAME",
                "SUPPLIER"
            )?.take(255) ?: "",
            stockLocation = getColumnValue(values, columnMapping, "STOCK LOCATION")?.take(255) ?: "",
            pol = getColumnValue(values, columnMapping, "POL")?.take(255) ?: null,
            rixoCompany = getColumnValue(values, columnMapping, "RIXO COMPANY")?.take(255) ?: "",
            clientName = getColumnValue(values, columnMapping, "CLIENT NAME")?.take(255) ?: "",
            // Your UI calls this column "Target Country", but the DB column is `country`.
            country = getColumnValue(values, columnMapping, "COUNTRY", "TARGET COUNTRY", "TARGET_COUNTRY")?.take(50) ?: "",
            price = getColumnValue(values, columnMapping, "PRICE")?.take(50) ?: "",
            auctionFee = getColumnValue(values, columnMapping, "AUCTION FEE")?.take(10) ?: "",
            auctionPenaltyFee = getColumnValue(values, columnMapping, "AUCTION PENALTY FEE")?.take(10) ?: null,
            recycleFee = getColumnValue(values, columnMapping, "RECYCLE FEE")?.take(10) ?: "",
            roadTax = getColumnValue(values, columnMapping, "ROAD TAX")?.take(10) ?: "",
            totalPrice = getColumnValue(values, columnMapping, "TOTAL PRICE")?.take(10) ?: "",
            paymentDate = getColumnValue(values, columnMapping, "PAYMENT DATE")?.take(10) ?: "",
            rixoRequested = getColumnValue(values, columnMapping, "RIXO REQUESTED")?.take(50) ?: "",
            rixoConfirmed = getColumnValue(values, columnMapping, "RIXO CONFIRMED", "RXO CONFIRMED")?.replace("\t", "")?.replace(" ", "")?.take(50) ?: "",
            rixoPrice = getColumnValue(values, columnMapping, "RIXO PRICE", "RIXO CHARGES")?.take(10) ?: "",
            shipmentDate = getColumnValue(values, columnMapping, "SHIPMENT DATE")?.take(10) ?: "",
            blNo = getColumnValue(values, columnMapping, "B/L NO", "B/L NO.", "BL NO")?.take(10) ?: "",
            vessel = getColumnValue(values, columnMapping, "VESSEL NO", "VESSEL NO.", "VESSEL")?.take(255) ?: "",
            shipmentCharges = getColumnValue(values, columnMapping, "SHIPMENT CHARGES")?.take(10) ?: "",
            freight = getColumnValue(values, columnMapping, "FREIGHT")?.take(10) ?: "",
            storageCharges = getColumnValue(values, columnMapping, "STORAGE CHARGES")?.take(10) ?: "",
            miscCharges = getColumnValue(values, columnMapping, "MISC CHARGES")?.take(10) ?: "",
            inspectionFee = getColumnValue(values, columnMapping, "INSPECTION FEE")?.take(10) ?: "",
            commission = getColumnValue(values, columnMapping, "COMMISSION")?.take(10) ?: "",
            repairCompany = getColumnValue(values, columnMapping, "REPAIR COMPANY")?.take(10) ?: "",
            repairCharges = getColumnValue(values, columnMapping, "REPAIR CHARGES")?.take(10) ?: "",
            notes = run {
                val n = getColumnValue(values, columnMapping, "NOTES")?.let { convertJapaneseNotesToEnglish(it) }?.take(1000) ?: ""
                val dest = getColumnValue(values, columnMapping, "DESTINATION")?.trim()?.take(500) ?: ""
                when {
                    dest.isNotEmpty() && n.isNotEmpty() -> "$n | POD: $dest"
                    dest.isNotEmpty() -> "POD: $dest"
                    else -> n
                }
            },
            shaken = false,
            negotiate = false,
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
            // Hydrate canonical stores (extended JSON, vehicle overrides, shipping) for PDF fields.
            val purchases = applyReadAdapters(
                selectedIds.mapNotNull { id -> purchaseRepository.findById(id).orElse(null) },
            )
            
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
                        "pol" -> updatedPurchase.copy(pol = value)
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
            val purchases = applyReadAdapters(
                selectedIds.mapNotNull { id -> purchaseRepository.findById(id).orElse(null) },
            )
            
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
                        pol = formData["pol"] as? String ?: purchase.pol,
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
            val purchases = applyReadAdapters(
                selectedIds.mapNotNull { id -> purchaseRepository.findById(id).orElse(null) },
            )
            
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
        return applyReadAdapterOrNull(purchaseRepository.findByChassis(chassis).firstOrNull())
    }
    
    /** Used by Car Booking country dropdown: at least one chassis Rixo-confirmed and booking not requested yet. */
    fun getUniqueCountries(): List<String> {
        return purchaseRepository.findDistinctCountriesWithPendingBooking()
    }
    
    fun getUniqueStockLocations(): List<String> {
        return purchaseRepository.findDistinctStockLocations()
    }
    
    /**
     * Distinct stock locations for Booking dropdown: only stocks that still have
     * bookable cars (Rixo confirmed, booking not requested) in [country].
     * Matches [getFilteredPurchasesByCountryAndStocks] eligibility — excludes shipped-only stocks.
     */
    fun getStockLocationsByCountry(country: String): List<String> {
        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        val out = mutableListOf<String>()
        val seen = HashSet<String>()
        for (p in purchases) {
            val stock = p.stockLocation?.trim().orEmpty()
            if (stock.isEmpty() || stock == "-") continue
            val key = stock.lowercase()
            if (seen.add(key)) out.add(stock)
        }
        return out.sortedBy { it.lowercase() }
    }
    
    /**
     * Distinct POL values for purchases eligible for booking (Rixo confirmed, booking not requested) in [country],
     * in **first-seen order** (table order is by chassis), so the first entry matches the first qualifying row — not alphabetical.
     */
    fun getPolByCountry(country: String): List<String> {
        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        val out = mutableListOf<String>()
        val seen = HashSet<String>()
        for (p in purchases) {
            val pol = effectivePol(p.pol, p.stockLocation)?.trim() ?: continue
            if (pol.isEmpty()) continue
            val key = pol.lowercase()
            if (seen.add(key)) out.add(pol)
        }
        return out
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
        val desiredPol = polPort.trim()
        if (desiredPol.isBlank()) return emptyList()

        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        return purchases
            .asSequence()
            .mapNotNull { p ->
                val resolved = effectivePol(p.pol, p.stockLocation)
                if (resolved != null && resolved.equals(desiredPol, ignoreCase = true)) p.chassis else null
            }
            .filter { !it.isNullOrBlank() }
            .distinct()
            .sorted()
            .toList()
    }

    @Transactional(readOnly = true)
    fun getFilteredPurchasesByCountryAndPol(country: String, polPort: String): List<Purchase> {
        val desiredPol = polPort.trim()
        if (desiredPol.isBlank()) return emptyList()

        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        return applyReadAdapters(
            purchases
                .filter { p ->
                    effectivePol(p.pol, p.stockLocation)?.equals(desiredPol, ignoreCase = true) == true
                }
                .sortedBy { it.chassis },
        )
    }

    /** Parse comma- or semicolon-separated stock location tokens (blank/`-` ignored). */
    private fun parseStockLocationFilters(raw: String?): Set<String> =
        raw.orEmpty()
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "-" }
            .map { it.lowercase() }
            .toSet()

    private fun purchaseMatchesAnyStock(p: Purchase, stockKeys: Set<String>): Boolean {
        if (stockKeys.isEmpty()) return false
        val stock = p.stockLocation?.trim().orEmpty()
        if (stock.isEmpty() || stock == "-") return false
        return stock.lowercase() in stockKeys
    }

    @Transactional(readOnly = true)
    fun getFilteredPurchasesByCountryAndStocks(country: String, stockLocations: String): List<Purchase> {
        val stockKeys = parseStockLocationFilters(stockLocations)
        if (stockKeys.isEmpty()) return emptyList()
        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        return applyReadAdapters(
            purchases
                .filter { purchaseMatchesAnyStock(it, stockKeys) }
                .sortedBy { it.chassis },
        )
    }

    @Transactional(readOnly = true)
    fun getFilteredChassisByCountryAndStocks(country: String, stockLocations: String): List<String> {
        val stockKeys = parseStockLocationFilters(stockLocations)
        if (stockKeys.isEmpty()) return emptyList()
        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        return purchases
            .asSequence()
            .filter { purchaseMatchesAnyStock(it, stockKeys) }
            .mapNotNull { it.chassis?.trim()?.takeIf { c -> c.isNotEmpty() } }
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Distinct POL options for booking after stock selection:
     * effectivePol from matching purchases, then single-token hard-map from selected stock tokens.
     */
    @Transactional(readOnly = true)
    fun getPolsForStocks(country: String, stockLocations: String): List<String> {
        val stockKeys = parseStockLocationFilters(stockLocations)
        if (stockKeys.isEmpty()) return emptyList()
        val out = mutableListOf<String>()
        val seen = HashSet<String>()
        fun addPol(raw: String?) {
            val pol = raw?.trim()?.takeIf { it.isNotEmpty() && it != "---" } ?: return
            if (seen.add(pol.lowercase())) out.add(pol)
        }
        val purchases = purchaseRepository.findUnshippedPurchasesByCountryForPolFiltering(country)
        for (p in purchases) {
            if (!purchaseMatchesAnyStock(p, stockKeys)) continue
            addPol(effectivePol(p.pol, p.stockLocation))
        }
        // Hard-map fallback for selected stock tokens with no purchase-derived POL yet.
        for (token in stockLocations.orEmpty().split(',', ';').map { it.trim() }.filter { it.isNotEmpty() && it != "-" }) {
            addPol(polFromStockLocation(token))
        }
        return out
    }
    
    fun getChassisWithoutBookingRequestByPol(polPort: String): List<String> {
        return purchaseRepository.findUnshippedChassisByPolPort(polPort)
    }

    @Transactional(readOnly = true)
    fun getCostDetailsByChassis(chassis: String): Map<String, Any>? {
        val purchase = getPurchaseByChassis(chassis) ?: return null
        return purchaseCostLineService.buildCostsByChassisApiMap(purchase)
    }
    
    @Transactional
    fun saveCarCostDetails(
        chassis: String,
        carPrice: Double,
        auctionFee: Double,
        auctionPenaltyFee: Double,
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
            existingPurchases.forEach { rawPurchase ->
                // Hydrate Transient vehicle specs (e.g. carModelYear) before finalize/sync so cost-only
                // writes do not wipe purchase_vehicle_overrides.
                val existingPurchase = applyReadAdapters(rawPurchase)
                val updatedPurchase = existingPurchase.copy(
                    price = carPrice.toString(),
                    auctionFee = auctionFee.toString(),
                    auctionPenaltyFee = auctionPenaltyFee.toString(),
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
                persistPurchase(updatedPurchase)
                finalizePurchaseWrite(updatedPurchase)
            }
            purchaseWorkflowService.recomputeByPurchaseIds(existingPurchases.mapNotNull { it.id })
            Logger.debug("Updated cost details for chassis: $chassis (${existingPurchases.size} purchase(s))")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }

    @Transactional
    fun saveFobCarCostDetails(
        chassis: String,
        carPrice: Double,
        auctionFee: Double,
        auctionPenaltyFee: Double,
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
            existingPurchases.forEach { rawPurchase ->
                val existingPurchase = applyReadAdapters(rawPurchase)
                val updatedPurchase = existingPurchase.copy(
                    price = carPrice.toString(),
                    auctionFee = auctionFee.toString(),
                    auctionPenaltyFee = auctionPenaltyFee.toString(),
                    rixoPrice = rixoPrice.toString(),
                    shipmentCharges = shippingCharge.toString(),
                    inspectionFee = inspectionFee.toString(),
                    repairCharges = repairFee.toString(),
                    miscCharges = mscCharges.toString(),
                    profit = java.math.BigDecimal(profit),
                    updatedAt = java.time.LocalDateTime.now()
                )
                persistPurchase(updatedPurchase)
                finalizePurchaseWrite(updatedPurchase)
            }
            purchaseWorkflowService.recomputeByPurchaseIds(existingPurchases.mapNotNull { it.id })
            Logger.debug("Updated FOB cost details for chassis: $chassis (${existingPurchases.size} purchase(s))")
        } else {
            throw RuntimeException("Purchase not found for chassis: $chassis")
        }
    }

    private fun splitBookingMappingTokens(raw: String?): List<String> =
        raw.orEmpty().split(Regex("[;,\\n]")).map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Resolves consignee address from `booking_mappings` (Consignee Map) for PDF output.
     * When multiple rows share the same consignee name, uses [country] and [pod] from the booking form to pick a row.
     */
    private fun resolveConsigneeAddressFromBookingMappings(
        consigneeName: String,
        country: String?,
        pod: String?
    ): String {
        val name = consigneeName.trim()
        if (name.isEmpty()) return ""
        val rows = bookingMappingRepository.findAllByConsigneeNameIgnoreCaseOrderByIdAsc(name)
        if (rows.isEmpty()) return ""
        if (rows.size == 1) return rows.first().consigneeAddress?.trim().orEmpty()
        val countryQ = country?.trim()?.lowercase().orEmpty()
        val podQ = pod?.trim()?.lowercase().orEmpty()
        fun matchesCountry(m: BookingMapping): Boolean {
            if (countryQ.isEmpty()) return false
            return splitBookingMappingTokens(m.country).any { it.equals(countryQ, ignoreCase = true) }
        }
        fun matchesPod(m: BookingMapping): Boolean {
            if (podQ.isEmpty()) return false
            return splitBookingMappingTokens(m.pod).any { t ->
                t.equals(podQ, ignoreCase = true) ||
                    podQ.contains(t, ignoreCase = true) ||
                    t.contains(podQ, ignoreCase = true)
            }
        }
        val both = rows.filter { matchesCountry(it) && matchesPod(it) }
        if (both.isNotEmpty()) return both.first().consigneeAddress?.trim().orEmpty()
        val byCountry = rows.filter { matchesCountry(it) }
        if (byCountry.isNotEmpty()) return byCountry.first().consigneeAddress?.trim().orEmpty()
        val byPod = rows.filter { matchesPod(it) }
        if (byPod.isNotEmpty()) return byPod.first().consigneeAddress?.trim().orEmpty()
        return rows.first().consigneeAddress?.trim().orEmpty()
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
        
        Logger.debug("ConsigneeName from request: '${request.consigneeName}'")
        
        val consigneeNameValue = request.consigneeName?.trim() ?: ""
        val addressFromMap = resolveConsigneeAddressFromBookingMappings(
            consigneeNameValue,
            request.consigneeCountry?.trim(),
            request.pod?.trim()
        )
        val addressFallback = request.consigneeAddress?.trim().orEmpty()
        val consigneeDetails = com.automan.backend.dto.ConsigneeDetailsDto(
            name = consigneeNameValue,
            address = addressFromMap.ifEmpty { addressFallback }
        )
        
        // Fetch car details for each chassis — price column prefers FOB/C&F calculator totals from the client,
        // then shipping_history.amount, then sums from purchase fields.
        // Apply read adapters so carModelYear (registration date) resolves from overrides / chassis map
        // after V53 dropped purchases.car_model_year.
        val carList = request.chassisNumbers.mapIndexed { index, chassisRaw ->
            val chassis = chassisRaw.trim()
            val frontendYen = resolveFrontendYenForShippingPdf(request.frontendTotalYenByChassis, chassis)
            val historyRow = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)
            val rawPurchases = purchaseRepository.findByChassisIgnoreCaseTrim(chassis)
                .ifEmpty { purchaseRepository.findByChassis(chassis) }
            val purchase = applyReadAdapterOrNull(rawPurchases.firstOrNull())

            fun carDto(priceYen: String): com.automan.backend.dto.CarPdfDto {
                val name = purchase?.carName ?: "Unknown"
                return com.automan.backend.dto.CarPdfDto(
                    no = index + 1,
                    name = name,
                    chassisNumber = purchase?.chassis ?: chassis,
                    year = purchase?.let { CarModelYearUtils.extractYearFromCarModelYear(it.carModelYear) }.orEmpty(),
                    cnfPrice = priceYen,
                    maker = purchase?.brand?.trim()?.takeIf { it.isNotEmpty() },
                    model = purchase?.carName?.trim()?.takeIf { it.isNotEmpty() },
                )
            }

            if (frontendYen != null) {
                carDto("¥${frontendYen.toInt()}")
            } else if (purchase != null) {
                val totalCnfPrice = if (historyRow != null) {
                    historyRow.amount
                } else {
                    val carPrice = try { java.math.BigDecimal(purchase.price ?: "0") } catch (e: Exception) { java.math.BigDecimal.ZERO }
                    val isPackageMode = purchase.isPackageMode ?: false

                    if (isPackageMode) {
                        carPrice
                    } else {
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

                carDto("¥${totalCnfPrice.toInt()}")
            } else {
                val amountFromHistory = historyRow?.amount
                carDto(if (amountFromHistory != null) "¥${amountFromHistory.toInt()}" else "¥0")
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
            calculationMode = request.calculationMode,
            carrier = request.carrier?.trim()?.takeIf { it.isNotEmpty() },
            cyCutDate = request.cyCutDate?.trim()?.takeIf { it.isNotEmpty() },
            eta = request.eta?.trim()?.takeIf { it.isNotEmpty() },
            finalDestination = request.finalDestination?.trim()?.takeIf { it.isNotEmpty() },
            notifyParty = request.notifyParty?.trim()?.takeIf { it.isNotEmpty() },
            inTransitClause = request.inTransitClause?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Lookup yen total from FOB/C&F calculator map (case-insensitive chassis match). */
    private fun resolveFrontendYenForShippingPdf(
        map: Map<String, java.math.BigDecimal>?,
        chassis: String,
    ): java.math.BigDecimal? {
        if (map.isNullOrEmpty()) return null
        val c = chassis.trim()
        map[c]?.let { return it }
        return map.entries.firstOrNull { it.key.trim().equals(c, ignoreCase = true) }?.value
    }
    
}
