package com.automan.backend.controller

import com.automan.backend.model.RixoMapping
import com.automan.backend.service.RixoMappingService
import com.automan.backend.util.RixoMappingSemicolon
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/rixo-mapping")
class RixoMappingController(
    private val rixoMappingService: RixoMappingService
) {
    data class RixoMappingUpsertRequest(
        /** When set, merge into this existing row instead of inserting (incremental tree build). */
        val id: Long? = null,
        val rixoCompany: String? = null,
        val auctionName: String? = null,
        val stockLocation: String? = null,
        val venueId: String? = null,
        val pol: String? = null,
        val supportedVehicleType: String? = null,
        val rixoPrice: String? = null,
        /** FULL (default): all fields required. COMPANY / AUCTION / STOCK: partial skeleton rows with empty/null for unset columns. */
        val insertMode: String? = null,
    )

    data class RixoMappingBulkRequest(
        val rows: List<RixoMappingUpsertRequest>? = null
    )

    /** Reject `;`-joined cells so clients must add separate mapping rows. */
    private fun rejectSemicolonsInRequest(req: RixoMappingUpsertRequest): String? {
        val fields = listOf(
            "Rixo company" to req.rixoCompany,
            "Supplier name" to req.auctionName,
            "Stock location" to req.stockLocation,
            "Venue ID" to req.venueId,
            "POL" to req.pol,
            "Supported vehicle type" to req.supportedVehicleType,
            "Rixo price" to req.rixoPrice,
        )
        for ((label, value) in fields) {
            if (RixoMappingSemicolon.containsSemicolon(value)) {
                return "$label: ${RixoMappingSemicolon.REJECT_MESSAGE}"
            }
        }
        return null
    }

    /**
     * On PUT, allow unchanged legacy values that still contain `;` (so path renames work
     * before normalize). Reject only when the client is writing/changing a field to a `;` value.
     */
    private fun rejectSemicolonsOnPut(req: RixoMappingUpsertRequest, existing: RixoMapping): String? {
        fun check(label: String, requested: String?, existingVal: String?): String? {
            if (!RixoMappingSemicolon.containsSemicolon(requested)) return null
            if (normEqStr(requested, existingVal)) return null
            return "$label: ${RixoMappingSemicolon.REJECT_MESSAGE}"
        }
        check("Rixo company", req.rixoCompany, existing.rixoCompany)?.let { return it }
        check("Supplier name", req.auctionName, existing.auctionName)?.let { return it }
        check("Stock location", req.stockLocation, existing.stockLocation)?.let { return it }
        check("Venue ID", req.venueId, existing.venueId)?.let { return it }
        check("POL", req.pol, existing.pol)?.let { return it }
        check("Supported vehicle type", req.supportedVehicleType, existing.supportedVehicleType)?.let { return it }
        check("Rixo price", req.rixoPrice, existing.rixoPrice)?.let { return it }
        return null
    }

    private fun validateRixoPriceIfPresent(rixoPrice: String?): String? {
        val price = rixoPrice?.trim().orEmpty()
        if (price.isNotEmpty() && price.toDoubleOrNull() == null) return "Rixo price must be numeric"
        return null
    }

    private fun normEqStr(a: String?, b: String?): Boolean {
        val ta = a?.trim().orEmpty()
        val tb = b?.trim().orEmpty()
        if (ta.isEmpty() && tb.isEmpty()) return true
        return ta.equals(tb, ignoreCase = true)
    }

    /** Supplier Map tree modes start with auction (supplier) first. */
    private fun isSupplierFirstMode(mode: String): Boolean =
        mode in setOf("SUPPLIER", "VENUE", "POL", "RIXO_COMPANY")

    /** Rixo Price Map: company → supplier → stock → pol → leaf. */
    private fun isRpmMode(mode: String): Boolean =
        mode in setOf("RPM_COMPANY", "RPM_SUPPLIER", "RPM_STOCK", "RPM_POL", "RPM_FULL")

    private fun isBlankStock(s: String?): Boolean {
        val t = s?.trim().orEmpty()
        return t.isEmpty() || t == "-"
    }

    private fun isBlankAuction(s: String?): Boolean {
        val t = s?.trim().orEmpty()
        return t.isEmpty() || t == "-"
    }

    /** Ensures merge target row matches the path in the request (incremental fill of one DB row). */
    private fun validateMergePath(existing: RixoMapping, req: RixoMappingUpsertRequest): String? {
        val mode = req.insertMode?.uppercase() ?: "FULL"
        if (isRpmMode(mode)) {
            if (!normEqStr(existing.rixoCompany, req.rixoCompany)) return "Row id does not match company"
            return when (mode) {
                "RPM_COMPANY" -> "Merge is not supported for mode RPM_COMPANY"
                "RPM_SUPPLIER" -> {
                    if (!isBlankAuction(existing.auctionName)) return "Supplier already set on this row"
                    null
                }
                "RPM_STOCK" -> {
                    if (!normEqStr(existing.auctionName, req.auctionName)) {
                        return "Supplier name must match the row being updated"
                    }
                    if (!isBlankStock(existing.stockLocation)) return "Stock location already set on this row"
                    null
                }
                "RPM_POL" -> {
                    if (!normEqStr(existing.auctionName, req.auctionName)) {
                        return "Supplier name must match the row being updated"
                    }
                    if (!normEqStr(existing.stockLocation, req.stockLocation)) {
                        return "Stock location must match the row being updated"
                    }
                    if (!existing.pol.isNullOrBlank()) return "POL already set on this row"
                    null
                }
                "RPM_FULL" -> {
                    if (!normEqStr(existing.auctionName, req.auctionName)) {
                        return "Supplier name must match the row being updated"
                    }
                    if (!normEqStr(existing.stockLocation, req.stockLocation)) {
                        return "Stock location must match the row being updated"
                    }
                    if (!normEqStr(existing.pol, req.pol)) return "POL must match the row being updated"
                    val vt = existing.supportedVehicleType?.trim().orEmpty()
                    val pr = existing.rixoPrice?.trim().orEmpty()
                    if (vt.isNotEmpty() || pr.isNotEmpty()) {
                        return "Vehicle type/price already set; omit id to insert another row for this path"
                    }
                    null
                }
                else -> "Merge is not supported for mode $mode"
            }
        }
        if (isSupplierFirstMode(mode)) {
            if (!normEqStr(existing.auctionName, req.auctionName)) return "Row id does not match supplier"
            return when (mode) {
                "SUPPLIER" -> {
                    val a = existing.auctionName?.trim().orEmpty()
                    if (a.isNotEmpty() && !normEqStr(existing.stockLocation, "-") && !normEqStr(existing.stockLocation, "")) {
                        return "This row already has path data; use Add without merging"
                    }
                    null
                }
                "VENUE" -> {
                    if (!existing.venueId.isNullOrBlank()) return "Venue already set on this row"
                    null
                }
                "STOCK" -> {
                    if (!normEqStr(existing.venueId, req.venueId)) return "Venue id must match the row being updated"
                    if (existing.stockLocation.trim().isNotEmpty() && !normEqStr(existing.stockLocation, "-")) {
                        return "Stock location already set on this row"
                    }
                    null
                }
                "POL" -> {
                    if (!normEqStr(existing.stockLocation, req.stockLocation)) return "Stock location must match the row being updated"
                    if (!existing.pol.isNullOrBlank()) return "POL already set on this row"
                    null
                }
                "RIXO_COMPANY" -> {
                    if (!normEqStr(existing.pol, req.pol)) return "POL must match the row being updated"
                    if (existing.rixoCompany.trim().isNotEmpty() && !normEqStr(existing.rixoCompany, "-")) {
                        return "Rixo company already set on this row"
                    }
                    null
                }
                else -> "Merge is not supported for mode $mode"
            }
        }
        if (!normEqStr(existing.rixoCompany, req.rixoCompany)) return "Row id does not match company"
        return when (mode) {
            "AUCTION" -> {
                val a = existing.auctionName?.trim().orEmpty()
                if (a.isNotEmpty()) return "This row already has an auction; use Add without merging to create another branch"
                null
            }
            "STOCK" -> {
                if (!normEqStr(existing.auctionName, req.auctionName)) return "Auction name must match the row being updated"
                if (existing.stockLocation.trim().isNotEmpty()) return "This row already has a stock location"
                null
            }
            "FULL" -> {
                if (!normEqStr(existing.auctionName, req.auctionName)) return "Auction name must match the row being updated"
                if (!normEqStr(existing.stockLocation, req.stockLocation)) return "Stock location must match the row being updated"
                val vt = existing.supportedVehicleType?.trim().orEmpty()
                val pr = existing.rixoPrice?.trim().orEmpty()
                if (vt.isNotEmpty() || pr.isNotEmpty()) {
                    return "Vehicle type/price already set; omit id to insert another row for this path"
                }
                null
            }
            else -> "Merge is not supported for mode $mode"
        }
    }

    private fun validate(req: RixoMappingUpsertRequest): String? {
        rejectSemicolonsInRequest(req)?.let { return it }
        val mode = req.insertMode?.uppercase() ?: "FULL"
        return when (mode) {
            "RPM_COMPANY" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "RPM_SUPPLIER" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "RPM_STOCK" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "RPM_POL" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                if (req.pol.isNullOrBlank()) return "POL is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "RPM_FULL" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "SUPPLIER" -> {
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "VENUE" -> {
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.venueId.isNullOrBlank()) return "Venue id is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "STOCK" -> {
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "POL" -> {
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                if (req.pol.isNullOrBlank()) return "POL is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "RIXO_COMPANY" -> {
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "FULL" -> {
                // Supplier Map full leaf: vehicle type is optional (nullable DB column).
                if (req.auctionName.isNullOrBlank()) return "Supplier name is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "COMPANY" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "AUCTION" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Auction house is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            else -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Auction house is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
                if (req.supportedVehicleType.isNullOrBlank()) return "Supported vehicle type is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
        }
    }

    private fun toInput(req: RixoMappingUpsertRequest): RixoMappingService.UpsertInput {
        val mode = req.insertMode?.uppercase() ?: "FULL"
        return when (mode) {
            "RPM_COMPANY" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = null,
                stockLocation = "-",
                venueId = null,
                pol = null,
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "RPM_SUPPLIER" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = "-",
                venueId = null,
                pol = null,
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "RPM_STOCK" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                venueId = null,
                pol = null,
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "RPM_POL" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                venueId = null,
                pol = req.pol!!.trim(),
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "RPM_FULL" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                venueId = req.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = req.pol?.trim()?.takeIf { it.isNotEmpty() },
                supportedVehicleType = req.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                rixoPrice = req.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
            )
            "SUPPLIER" -> RixoMappingService.UpsertInput(
                rixoCompany = "-",
                auctionName = req.auctionName!!.trim(),
                stockLocation = "-",
                venueId = null,
                pol = null,
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "VENUE" -> RixoMappingService.UpsertInput(
                rixoCompany = "-",
                auctionName = req.auctionName!!.trim(),
                stockLocation = "-",
                venueId = req.venueId!!.trim(),
                pol = null,
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "STOCK" -> RixoMappingService.UpsertInput(
                rixoCompany = "-",
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                venueId = req.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = null,
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "POL" -> RixoMappingService.UpsertInput(
                rixoCompany = "-",
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                venueId = req.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = req.pol!!.trim(),
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "RIXO_COMPANY" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                venueId = req.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = req.pol?.trim()?.takeIf { it.isNotEmpty() },
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "FULL" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName?.trim()?.takeIf { it.isNotEmpty() },
                stockLocation = req.stockLocation!!.trim(),
                venueId = req.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = req.pol?.trim()?.takeIf { it.isNotEmpty() },
                supportedVehicleType = req.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                rixoPrice = req.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
            )
            "COMPANY" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = null,
                stockLocation = "",
                supportedVehicleType = null,
                rixoPrice = null,
            )
            "AUCTION" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = "",
                supportedVehicleType = null,
                rixoPrice = null,
            )
            else -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName?.trim()?.takeIf { it.isNotEmpty() },
                stockLocation = req.stockLocation!!.trim(),
                venueId = req.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = req.pol?.trim()?.takeIf { it.isNotEmpty() },
                supportedVehicleType = req.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                rixoPrice = req.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
    }

    /** For PUT: fill blank request fields from DB so tree inline renames do not drop optional columns. */
    private fun mergePutWithExisting(req: RixoMappingUpsertRequest, existing: RixoMapping): RixoMappingUpsertRequest {
        fun coalesce(a: String?, b: String?): String? {
            val at = a?.trim().orEmpty()
            if (at.isNotEmpty()) return a?.trim()
            val bt = b?.trim().orEmpty()
            if (bt.isNotEmpty()) return b?.trim()
            return null
        }
        return req.copy(
            rixoCompany = coalesce(req.rixoCompany, existing.rixoCompany) ?: "",
            auctionName = coalesce(req.auctionName, existing.auctionName),
            stockLocation = coalesce(req.stockLocation, existing.stockLocation) ?: "",
            venueId = coalesce(req.venueId, existing.venueId),
            pol = coalesce(req.pol, existing.pol),
            supportedVehicleType = coalesce(req.supportedVehicleType, existing.supportedVehicleType),
            rixoPrice = coalesce(req.rixoPrice, existing.rixoPrice),
            insertMode = req.insertMode,
        )
    }

    /**
     * PUT validation (tree inline edit / any partial update): never use [validate], which requires
     * full path fields. Do not validate [rixoPrice] here — path-only edits (company/auction/stock)
     * must not fail because of unrelated stored price text; leaf price edits are checked on the client.
     * Semicolon check uses [requestBeforeMerge] so legacy multi-token cells coalesce without blocking rename.
     */
    private fun validatePutMerged(
        merged: RixoMappingUpsertRequest,
        requestBeforeMerge: RixoMappingUpsertRequest,
        existing: RixoMapping,
    ): String? {
        rejectSemicolonsOnPut(requestBeforeMerge, existing)?.let { return it }
        if (merged.rixoCompany.isNullOrBlank()) return "Rixo company is required"
        return null
    }

    @GetMapping("/distinct-auction-names")
    fun distinctAuctionNames(): ResponseEntity<List<String>> =
        ResponseEntity.ok(rixoMappingService.listDistinctAuctionNames())

    @GetMapping("/distinct-rixo-companies")
    fun distinctRixoCompanies(): ResponseEntity<List<String>> =
        ResponseEntity.ok(rixoMappingService.listDistinctRixoCompanies())

    /**
     * Expand `pol` cells that contain `;` into one row per token.
     * Does not touch single-POL rows. Pass dryRun=true (default) to preview.
     */
    @PostMapping("/normalize-pol-semicolons")
    fun normalizePolSemicolons(
        @RequestParam(defaultValue = "true") dryRun: Boolean,
    ): ResponseEntity<Map<String, Any?>> {
        val result = rixoMappingService.normalizePolSemicolons(dryRun = dryRun)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to if (dryRun) {
                    "Dry run: would expand ${result.scannedMultiPol} multi-POL row(s) " +
                        "(insert ${result.inserted}, skip ${result.skippedDuplicates}, " +
                        "delete ${result.deletedOriginals} originals)"
                } else {
                    "Expanded ${result.scannedMultiPol} multi-POL row(s): " +
                        "inserted ${result.inserted}, skipped ${result.skippedDuplicates}, " +
                        "deleted ${result.deletedOriginals} originals"
                },
                "data" to mapOf(
                    "dryRun" to result.dryRun,
                    "scannedMultiPol" to result.scannedMultiPol,
                    "inserted" to result.inserted,
                    "skippedDuplicates" to result.skippedDuplicates,
                    "deletedOriginals" to result.deletedOriginals,
                    "sampleOriginalIds" to result.sampleOriginalIds,
                ),
            )
        )
    }

    @GetMapping("/all")
    fun listAll(): ResponseEntity<Map<String, Any?>> {
        val items = rixoMappingService.listAllForTree().map { m ->
            mapOf(
                "id" to m.id,
                "rixoCompany" to m.rixoCompany,
                "auctionName" to m.auctionName,
                "stockLocation" to m.stockLocation,
                "venueId" to m.venueId,
                "pol" to m.pol,
                "supportedVehicleType" to m.supportedVehicleType,
                "rixoPrice" to m.rixoPrice
            )
        }
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to items
            )
        )
    }

    @GetMapping("/lookup")
    fun lookupRixoPrice(
        @RequestParam auctionName: String,
        @RequestParam stockLocation: String,
        @RequestParam rixoCompany: String,
        @RequestParam(required = false) supportedVehicleType: String?,
    ): ResponseEntity<Map<String, Any?>> {
        val match = rixoMappingService.findRixoPrice(
            auctionName = auctionName,
            stockLocation = stockLocation,
            rixoCompany = rixoCompany,
            supportedVehicleType = supportedVehicleType,
        )

        return if (match == null) {
            val vehicleLabel = supportedVehicleType?.trim().orEmpty().ifBlank { "(any)" }
            ResponseEntity.ok(mapOf(
                "success" to false,
                "message" to "No rixo mapping found for Supplier=$auctionName, Rixo Company=$rixoCompany, Stock=$stockLocation, Vehicle type=$vehicleLabel",
                "data" to null
            ))
        } else {
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to mapOf(
                    "rixoPrice" to match.rixoPrice
                )
            ))
        }
    }

    @PostMapping("/bulk")
    fun addBulk(@RequestBody req: RixoMappingBulkRequest): ResponseEntity<Map<String, Any?>> {
        val rows = req.rows ?: emptyList()
        if (rows.isEmpty()) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "At least one row is required"))
        }
        val saved = mutableListOf<RixoMapping>()
        for ((idx, row) in rows.withIndex()) {
            if (row.id != null) {
                val mode = row.insertMode?.uppercase() ?: "FULL"
                if (mode == "COMPANY" || mode == "SUPPLIER" || mode == "RPM_COMPANY") {
                    return ResponseEntity.badRequest().body(
                        mapOf("success" to false, "message" to "Row ${idx + 1}: id is not allowed for $mode insert")
                    )
                }
                val existing = rixoMappingService.findById(row.id)
                    ?: return ResponseEntity.badRequest().body(
                        mapOf("success" to false, "message" to "Row ${idx + 1}: mapping id not found")
                    )
                validateMergePath(existing, row)?.let { msg ->
                    return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Row ${idx + 1}: $msg"))
                }
                val err = validate(row)
                if (err != null) {
                    return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Row ${idx + 1}: $err"))
                }
                val merged = rixoMappingService.mergeIncrementalRow(
                    existing = existing,
                    insertMode = mode,
                    auctionName = row.auctionName,
                    venueId = row.venueId,
                    stockLocation = row.stockLocation,
                    pol = row.pol,
                    rixoCompany = row.rixoCompany,
                    supportedVehicleType = row.supportedVehicleType,
                    rixoPrice = row.rixoPrice,
                )
                saved.add(rixoMappingService.saveRow(merged))
            } else {
                val err = validate(row)
                if (err != null) {
                    return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to "Row ${idx + 1}: $err"))
                }
                saved.addAll(rixoMappingService.addBulk(listOf(toInput(row))))
            }
        }
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Saved ${saved.size} mapping row(s)",
                "data" to saved.map { m ->
                    mapOf(
                        "id" to m.id,
                        "rixoCompany" to m.rixoCompany,
                        "auctionName" to m.auctionName,
                        "stockLocation" to m.stockLocation,
                        "venueId" to m.venueId,
                        "pol" to m.pol,
                        "supportedVehicleType" to m.supportedVehicleType,
                        "rixoPrice" to m.rixoPrice
                    )
                }
            )
        )
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody req: RixoMappingUpsertRequest
    ): ResponseEntity<Map<String, Any?>> {
        val existing = rixoMappingService.findById(id)
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "message" to "Mapping row not found"))
        val merged = mergePutWithExisting(req, existing)
        val fullReq = merged.copy(insertMode = null)
        val err = validatePutMerged(fullReq, req, existing)
        if (err != null) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "message" to err))
        }
        val saved = rixoMappingService.update(id, toInput(fullReq))
            ?: return ResponseEntity.status(404).body(mapOf("success" to false, "message" to "Mapping row not found"))
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Mapping row updated",
                "data" to mapOf(
                    "id" to saved.id,
                    "rixoCompany" to saved.rixoCompany,
                    "auctionName" to saved.auctionName,
                    "stockLocation" to saved.stockLocation,
                    "venueId" to saved.venueId,
                    "pol" to saved.pol,
                    "supportedVehicleType" to saved.supportedVehicleType,
                    "rixoPrice" to saved.rixoPrice
                )
            )
        )
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Map<String, Any?>> {
        val ok = rixoMappingService.delete(id)
        if (!ok) {
            return ResponseEntity.status(404).body(mapOf("success" to false, "message" to "Mapping row not found"))
        }
        return ResponseEntity.ok(mapOf("success" to true, "message" to "Mapping row deleted"))
    }
}

