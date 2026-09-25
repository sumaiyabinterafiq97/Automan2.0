package com.automan.backend.service

import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.RixoMappingRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class RixoMappingService(
    private val rixoMappingRepository: RixoMappingRepository,
    private val purchaseRixoPriceSyncService: PurchaseRixoPriceSyncService,
) {
    data class UpsertInput(
        val rixoCompany: String,
        val auctionName: String?,
        val stockLocation: String,
        val venueId: String? = null,
        val supportedVehicleType: String?,
        val rixoPrice: String?,
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

    /**
     * Fills the next level on an existing partial row (same DB row as user expands company → stock → supplier → leaf).
     */
    fun mergeIncrementalRow(
        existing: RixoMapping,
        insertMode: String,
        auctionName: String? = null,
        venueId: String? = null,
        stockLocation: String? = null,
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
                    createdAt = created,
                )
            }
            "RPM_STOCK" -> {
                val nextStock = stockLocation!!.trim()
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    stockLocation = nextStock,
                    venueId = healVenue(nextAuction, venueId),
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
                    createdAt = created,
                )
            }
            "VENUE" -> existing.copy(
                venueId = venueId!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "RIXO_COMPANY" -> {
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    rixoCompany = rixoCompany!!.trim(),
                    venueId = healVenue(nextAuction, venueId),
                    createdAt = created,
                )
            }
            "FULL", "RPM_FULL" -> {
                val nextAuction = auctionName ?: existing.auctionName
                existing.copy(
                    supportedVehicleType = supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                    rixoPrice = rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
                    venueId = healVenue(nextAuction, venueId),
                    createdAt = created,
                )
            }
            else -> throw IllegalArgumentException("Unsupported merge mode: $insertMode")
        }
    }

    fun saveRow(entity: RixoMapping, previous: RixoMapping? = null): RixoMapping {
        val saved = rixoMappingRepository.save(entity)
        if (previous != null) {
            purchaseRixoPriceSyncService.syncIfPriceChanged(previous, saved)
        }
        return saved
    }

    fun addBulk(rows: List<UpsertInput>): List<RixoMapping> {
        val entities = rows.map { row ->
            val stock = row.stockLocation.trim()
            val venue = coalesceVenueWithUniqueForAuction(row.auctionName, row.venueId)
            val auction = row.auctionName?.trim()?.takeIf { it.isNotEmpty() }
            RixoMapping(
                rixoCompany = row.rixoCompany.trim(),
                auctionName = auction,
                stockLocation = stock,
                venueId = venue,
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
        val updated = existing.copy(
            rixoCompany = row.rixoCompany.trim(),
            auctionName = row.auctionName?.trim()?.takeIf { it.isNotEmpty() },
            stockLocation = row.stockLocation.trim(),
            venueId = venue,
            supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
            rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
        )
        val saved = rixoMappingRepository.save(updated)
        purchaseRixoPriceSyncService.syncIfPriceChanged(existing, saved)
        return saved
    }

    fun delete(id: Long): Boolean {
        if (!rixoMappingRepository.existsById(id)) return false
        rixoMappingRepository.deleteById(id)
        return true
    }
}

