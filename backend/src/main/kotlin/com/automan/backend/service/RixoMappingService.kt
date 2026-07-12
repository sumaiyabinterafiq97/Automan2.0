package com.automan.backend.service

import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.RixoMappingRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

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
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun findById(id: Long): RixoMapping? =
        rixoMappingRepository.findById(id).orElse(null)

    /**
     * Fills the next level on an existing partial row (same DB row as user expands company → auction → stock → leaf).
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
        return when (insertMode.uppercase()) {
            "AUCTION" -> existing.copy(
                auctionName = auctionName!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "STOCK" -> existing.copy(
                stockLocation = stockLocation!!.trim(),
                venueId = venueId?.trim()?.takeIf { it.isNotEmpty() } ?: existing.venueId,
                pol = pol?.trim()?.takeIf { it.isNotEmpty() } ?: existing.pol,
                createdAt = created,
            )
            "VENUE" -> existing.copy(
                venueId = venueId!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "POL" -> existing.copy(
                pol = pol!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "RIXO_COMPANY" -> existing.copy(
                rixoCompany = rixoCompany!!.trim(),
                createdAt = created,
            )
            "FULL" -> existing.copy(
                supportedVehicleType = supportedVehicleType!!.trim(),
                rixoPrice = rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            else -> throw IllegalArgumentException("Unsupported merge mode: $insertMode")
        }
    }

    fun saveRow(entity: RixoMapping): RixoMapping =
        rixoMappingRepository.save(entity)

    fun addBulk(rows: List<UpsertInput>): List<RixoMapping> {
        val entities = rows.map { row ->
            RixoMapping(
                rixoCompany = row.rixoCompany.trim(),
                auctionName = row.auctionName?.trim()?.takeIf { it.isNotEmpty() },
                stockLocation = row.stockLocation.trim(),
                venueId = row.venueId?.trim()?.takeIf { it.isNotEmpty() },
                pol = row.pol?.trim()?.takeIf { it.isNotEmpty() },
                supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotEmpty() },
            )
        }
        return rixoMappingRepository.saveAll(entities)
    }

    fun update(id: Long, row: UpsertInput): RixoMapping? {
        val existing = rixoMappingRepository.findById(id).orElse(null) ?: return null
        val updated = existing.copy(
            rixoCompany = row.rixoCompany.trim(),
            auctionName = row.auctionName?.trim()?.takeIf { it.isNotEmpty() },
            stockLocation = row.stockLocation.trim(),
            venueId = row.venueId?.trim()?.takeIf { it.isNotEmpty() },
            pol = row.pol?.trim()?.takeIf { it.isNotEmpty() },
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
}

