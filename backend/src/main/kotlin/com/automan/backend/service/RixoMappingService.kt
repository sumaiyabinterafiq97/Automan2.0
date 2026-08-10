package com.automan.backend.service

import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.RixoMappingRepository
import com.automan.backend.util.RixoMappingSemicolon
import com.automan.backend.util.RixoPolFromStockLocation
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RixoMappingService(
    private val rixoMappingRepository: RixoMappingRepository
) {
    data class UpsertInput(
        val rixoCompany: String,
        val auctionName: String?,
        val stockLocation: String,
        val venueId: String? = null,
        val pol: String? = null,
        val supportedVehicleType: String?,
        val rixoPrice: String?,
    )

    data class NormalizePolResult(
        val dryRun: Boolean,
        val scannedMultiPol: Int,
        val inserted: Int,
        val skippedDuplicates: Int,
        val deletedOriginals: Int,
        val sampleOriginalIds: List<Long>,
    )

    fun findRixoPrice(
        auctionName: String,
        stockLocation: String,
        rixoCompany: String,
        supportedVehicleType: String?,
    ): RixoMapping? {
        if (auctionName.isBlank() || stockLocation.isBlank() || rixoCompany.isBlank()) return null

        val trimmedAuction = auctionName.trim()
        val trimmedStock = stockLocation.trim()
        val trimmedCompany = rixoCompany.trim()
        val trimmedVehicleType = supportedVehicleType?.trim()?.takeIf { it.isNotBlank() }

        val exact = rixoMappingRepository.findExactMatch(
            auctionName = trimmedAuction,
            stockLocation = trimmedStock,
            rixoCompany = trimmedCompany,
            supportedVehicleType = trimmedVehicleType,
        )
        if (exact.isNotEmpty()) return exact.first()

        if (trimmedVehicleType != null) {
            val fallback = rixoMappingRepository.findMatchWithNullVehicleType(
                auctionName = trimmedAuction,
                stockLocation = trimmedStock,
                rixoCompany = trimmedCompany,
            )
            if (fallback.isNotEmpty()) return fallback.first()
        }

        return null
    }

    fun listAllForTree(): List<RixoMapping> =
        rixoMappingRepository.findAll(
            Sort.by(
                Sort.Order.asc("rixoCompany"),
                Sort.Order.asc("auctionName"),
                Sort.Order.asc("stockLocation"),
                Sort.Order.asc("id")
            )
        )

    fun listForTreeByCompany(rixoCompany: String): List<RixoMapping> {
        val c = rixoCompany.trim()
        if (c.isEmpty()) return emptyList()
        return rixoMappingRepository.findByRixoCompanyIgnoreCase(c).sortedWith(
            compareBy(
                { it.rixoCompany },
                { it.stockLocation },
                { it.auctionName ?: "" },
                { it.id ?: 0L },
            )
        )
    }

    fun listForTreeByAuction(auctionName: String): List<RixoMapping> {
        val a = auctionName.trim()
        if (a.isEmpty()) return emptyList()
        return rixoMappingRepository.findByAuctionNameIgnoreCase(a).sortedWith(
            compareBy(
                { it.rixoCompany },
                { it.auctionName ?: "" },
                { it.stockLocation },
                { it.id ?: 0L },
            )
        )
    }

    /** Distinct non-blank [RixoMapping.auctionName] values for supplier map dropdowns. */
    fun listDistinctAuctionNames(): List<String> =
        rixoMappingRepository.findDistinctAuctionNamesOrdered()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun listDistinctRixoCompanies(): List<String> =
        rixoMappingRepository.findDistinctRixoCompaniesOrdered()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "-" && !it.equals("(no company)", ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun findById(id: Long): RixoMapping? =
        rixoMappingRepository.findById(id).orElse(null)

    /** Distinct non-blank venue_id values for a supplier (case-insensitive distinct). */
    fun distinctVenuesForAuction(auctionName: String): List<String> {
        val a = auctionName.trim()
        if (a.isEmpty()) return emptyList()
        return RixoMappingVenueRules.distinctVenues(
            rixoMappingRepository.findByAuctionNameIgnoreCase(a).map { it.venueId },
        )
    }

    /** Venue only when the supplier has exactly one distinct non-blank venue_id. */
    fun resolveUniqueVenueForAuction(auctionName: String): String? {
        val a = auctionName.trim()
        if (a.isEmpty()) return null
        return RixoMappingVenueRules.resolveUnique(
            rixoMappingRepository.findByAuctionNameIgnoreCase(a).map { it.venueId },
        )
    }

    /**
     * If [venueId] is blank and [auctionName] has exactly one known venue, return that venue;
     * otherwise return the trimmed non-blank [venueId] (or null).
     */
    fun coalesceVenueWithUniqueForAuction(auctionName: String?, venueId: String?): String? {
        val explicit = RixoMappingVenueRules.normalizeVenue(venueId)
        if (explicit != null) return explicit
        val auction = auctionName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return resolveUniqueVenueForAuction(auction)
    }

    /** Reject a new distinct venue when the supplier already has a different one. */
    fun rejectSecondVenueForAuction(auctionName: String?, requestedVenue: String?): String? {
        val auction = auctionName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return RixoMappingVenueRules.rejectSecondVenue(
            rixoMappingRepository.findByAuctionNameIgnoreCase(auction).map { it.venueId },
            requestedVenue,
        )
    }

    /** Suppliers with more than one distinct non-blank venue_id (read-only diagnostic). */
    fun listVenueConflicts(): List<RixoMappingVenueRules.VenueConflict> {
        val all = rixoMappingRepository.findAll()
        return RixoMappingVenueRules.conflictSuppliers(all.map { it.auctionName to it.venueId })
    }

    /** Distinct non-blank pol values for a stock location (case-insensitive distinct). */
    fun distinctPolsForStock(stockLocation: String): List<String> {
        val s = stockLocation.trim()
        if (RixoMappingPolRules.isBlankStock(s)) return emptyList()
        return RixoMappingPolRules.distinctPols(
            rixoMappingRepository.findByStockLocationIgnoreCase(s).map { it.pol },
        )
    }

    /** POL only when the stock has exactly one distinct non-blank pol. */
    fun resolveUniquePolForStock(stockLocation: String): String? {
        val s = stockLocation.trim()
        if (RixoMappingPolRules.isBlankStock(s)) return null
        return RixoMappingPolRules.resolveUnique(
            rixoMappingRepository.findByStockLocationIgnoreCase(s).map { it.pol },
        )
    }

    /** Unique POL among rows for the same stock + supplier (auction). */
    fun resolveUniquePolForStockAndAuction(stockLocation: String, auctionName: String): String? {
        val s = stockLocation.trim()
        val a = auctionName.trim()
        if (RixoMappingPolRules.isBlankStock(s) || a.isEmpty() || a == "-") return null
        return RixoMappingPolRules.resolveUnique(
            rixoMappingRepository.findByStockLocationAndAuctionNameIgnoreCase(s, a).map { it.pol },
        )
    }

    /**
     * Tiered POL autofill for RPM / heal:
     * 1) explicit request pol
     * 2) unique pol for stock+auction (when auction known)
     * 3) unique pol for stock globally
     * 4) single-token [RixoPolFromStockLocation.derivePol]
     * 5) null
     */
    fun coalescePolWithUniqueForStock(
        stockLocation: String?,
        pol: String?,
        auctionName: String? = null,
    ): String? {
        val explicit = RixoMappingPolRules.normalizePol(pol)
        if (explicit != null) return explicit
        val stock = stockLocation?.trim()?.takeIf { !RixoMappingPolRules.isBlankStock(it) } ?: return null
        val auction = auctionName?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
        if (auction != null) {
            resolveUniquePolForStockAndAuction(stock, auction)?.let { return it }
        }
        resolveUniquePolForStock(stock)?.let { return it }
        return RixoMappingPolRules.singleTokenDerivedPol(stock) { RixoPolFromStockLocation.derivePol(it) }
    }

    /** Reject a new distinct POL when the stock already has a different one. */
    fun rejectSecondPolForStock(stockLocation: String?, requestedPol: String?): String? {
        val stock = stockLocation?.trim()?.takeIf { !RixoMappingPolRules.isBlankStock(it) } ?: return null
        return RixoMappingPolRules.rejectSecondPol(
            rixoMappingRepository.findByStockLocationIgnoreCase(stock).map { it.pol },
            requestedPol,
        )
    }

    /** Stocks with more than one distinct non-blank pol (read-only diagnostic). */
    fun listPolConflicts(): List<RixoMappingPolRules.PolConflict> {
        val all = rixoMappingRepository.findAll()
        return RixoMappingPolRules.conflictStocks(all.map { it.stockLocation to it.pol })
    }

    /**
     * Fills the next level on an existing partial row (same DB row as user expands company → stock → supplier → leaf).
     */
    fun mergeIncrementalRow(
        existing: RixoMapping,
        insertMode: String,
        auctionName: String? = null,
        venueId: String? = null,
        stockLocation: String? = null,
        pol: String? = null,
        rixoCompany: String? = null,
        supportedVehicleType: String? = null,
        rixoPrice: String? = null,
    ): RixoMapping {
        val created = existing.createdAt
        fun healVenue(auction: String?, requested: String?): String? {
            val kept = RixoMappingVenueRules.normalizeVenue(requested)
                ?: RixoMappingVenueRules.normalizeVenue(existing.venueId)
            if (kept != null) return kept
            return coalesceVenueWithUniqueForAuction(auction ?: existing.auctionName, null)
        }
        fun healPol(requested: String?, stock: String?, auction: String? = null): String? {
            val kept = RixoMappingPolRules.normalizePol(requested)
                ?: RixoMappingPolRules.normalizePol(existing.pol)
            if (kept != null) return kept
            return coalescePolWithUniqueForStock(
                stock ?: existing.stockLocation,
                null,
                auction ?: auctionName ?: existing.auctionName,
            )
        }
        return when (insertMode.uppercase()) {
            "AUCTION" -> existing.copy(
                auctionName = auctionName!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "STOCK" -> {
                val nextStock = stockLocation!!.trim()
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    stockLocation = nextStock,
                    venueId = healVenue(nextAuction, venueId),
                    pol = healPol(pol, nextStock, nextAuction),
                    createdAt = created,
                )
            }
            "RPM_STOCK" -> {
                val nextStock = stockLocation!!.trim()
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    stockLocation = nextStock,
                    venueId = healVenue(nextAuction, venueId),
                    pol = healPol(pol, nextStock, nextAuction),
                    createdAt = created,
                )
            }
            "RPM_SUPPLIER" -> {
                val nextAuction = auctionName!!.trim().takeIf { it.isNotEmpty() }
                val nextStock = stockLocation?.trim()?.takeIf { it.isNotEmpty() } ?: existing.stockLocation
                existing.copy(
                    auctionName = nextAuction,
                    stockLocation = nextStock,
                    venueId = healVenue(nextAuction, venueId),
                    pol = healPol(pol, nextStock, nextAuction),
                    createdAt = created,
                )
            }
            "VENUE" -> existing.copy(
                venueId = venueId!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "POL", "RPM_POL" -> existing.copy(
                pol = pol!!.trim().takeIf { it.isNotEmpty() },
                venueId = healVenue(auctionName ?: existing.auctionName, venueId),
                createdAt = created,
            )
            "RIXO_COMPANY" -> {
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    rixoCompany = rixoCompany!!.trim(),
                    venueId = healVenue(nextAuction, venueId),
                    pol = healPol(pol, stockLocation ?: existing.stockLocation, nextAuction),
                    createdAt = created,
                )
            }
            "FULL", "RPM_FULL" -> {
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    // Vehicle type is optional (nullable DB column); blank clears / leaves null.
                    supportedVehicleType = supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                    rixoPrice = rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
                    venueId = healVenue(nextAuction, venueId),
                    pol = healPol(pol, stockLocation ?: existing.stockLocation, nextAuction),
                    createdAt = created,
                )
            }
            else -> throw IllegalArgumentException("Unsupported merge mode: $insertMode")
        }
    }

    fun saveRow(entity: RixoMapping): RixoMapping =
        rixoMappingRepository.save(entity)

    fun addBulk(rows: List<UpsertInput>): List<RixoMapping> {
        val entities = rows.map { row ->
            val stock = row.stockLocation.trim()
            val venue = coalesceVenueWithUniqueForAuction(row.auctionName, row.venueId)
            val auction = row.auctionName?.trim()?.takeIf { it.isNotEmpty() }
            val pol = coalescePolWithUniqueForStock(stock, row.pol, auction)
            RixoMapping(
                rixoCompany = row.rixoCompany.trim(),
                auctionName = auction,
                stockLocation = stock,
                venueId = venue,
                pol = pol,
                supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return rixoMappingRepository.saveAll(entities)
    }

    fun update(id: Long, row: UpsertInput): RixoMapping? {
        val existing = rixoMappingRepository.findById(id).orElse(null) ?: return null
        // Never clear an existing venue when the request omits/blanks it; heal blank rows when unique.
        val venue = RixoMappingVenueRules.normalizeVenue(row.venueId)
            ?: RixoMappingVenueRules.normalizeVenue(existing.venueId)
            ?: coalesceVenueWithUniqueForAuction(row.auctionName ?: existing.auctionName, null)
        val nextStock = row.stockLocation.trim().ifEmpty { existing.stockLocation }
        val nextAuction = row.auctionName?.trim()?.takeIf { it.isNotEmpty() } ?: existing.auctionName
        // Never clear an existing POL when the request omits/blanks it; heal blank rows when unique.
        val pol = RixoMappingPolRules.normalizePol(row.pol)
            ?: RixoMappingPolRules.normalizePol(existing.pol)
            ?: coalescePolWithUniqueForStock(nextStock, null, nextAuction)
        val updated = existing.copy(
            rixoCompany = row.rixoCompany.trim(),
            auctionName = row.auctionName?.trim()?.takeIf { it.isNotEmpty() },
            stockLocation = row.stockLocation.trim(),
            venueId = venue,
            pol = pol,
            supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
            rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
        )
        return rixoMappingRepository.save(updated)
    }

    fun delete(id: Long): Boolean {
        if (!rixoMappingRepository.existsById(id)) return false
        rixoMappingRepository.deleteById(id)
        return true
    }

    /**
     * Surgical expand: rows whose [RixoMapping.pol] contains `;` become one row per token.
     * Single-POL rows (client-added or otherwise) are never modified.
     * Never truncates the table.
     */
    @Transactional
    fun normalizePolSemicolons(dryRun: Boolean = true): NormalizePolResult {
        val all = rixoMappingRepository.findAll()
        val keySet = all.map { rowIdentityKey(it) }.toMutableSet()
        val multiPol = all.filter { RixoMappingSemicolon.splitTokens(it.pol).size >= 2 }
            .sortedBy { it.id ?: 0L }

        var inserted = 0
        var skippedDuplicates = 0
        var deletedOriginals = 0
        val sampleIds = multiPol.mapNotNull { it.id }.take(20)
        val toInsert = mutableListOf<RixoMapping>()
        val toDeleteIds = mutableListOf<Long>()

        for (source in multiPol) {
            val tokens = RixoMappingSemicolon.splitTokens(source.pol)
            var ensuredTokenRow = false
            for (token in tokens) {
                val clone = source.copy(id = null, pol = token)
                val key = rowIdentityKey(clone)
                if (keySet.contains(key)) {
                    skippedDuplicates++
                    ensuredTokenRow = true
                    continue
                }
                keySet.add(key)
                toInsert.add(clone)
                inserted++
                ensuredTokenRow = true
            }
            val sourceId = source.id
            if (ensuredTokenRow && sourceId != null) {
                toDeleteIds.add(sourceId)
                deletedOriginals++
                // Remove original multi-POL identity so re-runs stay clean
                keySet.remove(rowIdentityKey(source))
            }
        }

        if (!dryRun) {
            if (toInsert.isNotEmpty()) {
                rixoMappingRepository.saveAll(toInsert)
            }
            for (id in toDeleteIds) {
                rixoMappingRepository.deleteById(id)
            }
        }

        return NormalizePolResult(
            dryRun = dryRun,
            scannedMultiPol = multiPol.size,
            inserted = inserted,
            skippedDuplicates = skippedDuplicates,
            deletedOriginals = deletedOriginals,
            sampleOriginalIds = sampleIds,
        )
    }

    /** Case-insensitive identity for dedupe across expand (includes pol + vehicle + price). */
    private fun rowIdentityKey(row: RixoMapping): String {
        fun n(s: String?) = s?.trim()?.lowercase().orEmpty()
        return listOf(
            n(row.rixoCompany),
            n(row.auctionName),
            n(row.stockLocation),
            n(row.venueId),
            n(row.pol),
            n(row.supportedVehicleType),
            n(row.rixoPrice),
        ).joinToString("\u0001")
    }
}

