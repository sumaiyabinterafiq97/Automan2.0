package com.automan.backend.controller

import com.automan.backend.model.RixoMapping
import com.automan.backend.service.RixoMappingService
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
        val supportedVehicleType: String? = null,
        val rixoPrice: String? = null,
        /** FULL (default): all fields required. COMPANY / AUCTION / STOCK: partial skeleton rows with empty/null for unset columns. */
        val insertMode: String? = null,
    )

    data class RixoMappingBulkRequest(
        val rows: List<RixoMappingUpsertRequest>? = null
    )

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

    /** Ensures merge target row matches the path in the request (incremental fill of one DB row). */
    private fun validateMergePath(existing: RixoMapping, req: RixoMappingUpsertRequest): String? {
        if (!normEqStr(existing.rixoCompany, req.rixoCompany)) return "Row id does not match company"
        val mode = req.insertMode?.uppercase() ?: "FULL"
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
        val mode = req.insertMode?.uppercase() ?: "FULL"
        return when (mode) {
            "COMPANY" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "AUCTION" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Auction house is required"
                validateRixoPriceIfPresent(req.rixoPrice)
            }
            "STOCK" -> {
                if (req.rixoCompany.isNullOrBlank()) return "Rixo company is required"
                if (req.auctionName.isNullOrBlank()) return "Auction house is required"
                if (req.stockLocation.isNullOrBlank()) return "Stock location is required"
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
            "STOCK" -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName!!.trim(),
                stockLocation = req.stockLocation!!.trim(),
                supportedVehicleType = null,
                rixoPrice = null,
            )
            else -> RixoMappingService.UpsertInput(
                rixoCompany = req.rixoCompany!!.trim(),
                auctionName = req.auctionName?.trim()?.takeIf { it.isNotEmpty() },
                stockLocation = req.stockLocation!!.trim(),
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
            supportedVehicleType = coalesce(req.supportedVehicleType, existing.supportedVehicleType),
            rixoPrice = coalesce(req.rixoPrice, existing.rixoPrice),
            insertMode = req.insertMode,
        )
    }

    /**
     * PUT validation (tree inline edit / any partial update): never use [validate], which requires
     * full path fields. Do not validate [rixoPrice] here — path-only edits (company/auction/stock)
     * must not fail because of unrelated stored price text; leaf price edits are checked on the client.
     */
    private fun validatePutMerged(merged: RixoMappingUpsertRequest): String? {
        if (merged.rixoCompany.isNullOrBlank()) return "Rixo company is required"
        return null
    }

    @GetMapping("/all")
    fun listAll(): ResponseEntity<Map<String, Any?>> {
        val items = rixoMappingService.listAllForTree().map { m ->
            mapOf(
                "id" to m.id,
                "rixoCompany" to m.rixoCompany,
                "auctionName" to m.auctionName,
                "stockLocation" to m.stockLocation,
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
        @RequestParam stockLocation: String,
        @RequestParam rixoCompany: String,
        @RequestParam(required = false) supportedVehicleType: String?
    ): ResponseEntity<Map<String, Any?>> {
        val match = rixoMappingService.findRixoPrice(
            stockLocation = stockLocation,
            rixoCompany = rixoCompany,
            supportedVehicleType = supportedVehicleType
        )

        return if (match == null) {
            ResponseEntity.ok(mapOf(
                "success" to false,
                "message" to "No rixo mapping found for these values",
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
                if (mode == "COMPANY") {
                    return ResponseEntity.badRequest().body(
                        mapOf("success" to false, "message" to "Row ${idx + 1}: id is not allowed for COMPANY insert")
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
                    stockLocation = row.stockLocation,
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
        val err = validatePutMerged(fullReq)
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

