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
        val supportedVehicleType: String?,
        val rixoPrice: String?
    )

    fun findRixoPrice(
        stockLocation: String,
        rixoCompany: String,
        supportedVehicleType: String?
    ): RixoMapping? {
        if (stockLocation.isBlank() || rixoCompany.isBlank()) return null

        val trimmedVehicleType = supportedVehicleType?.trim()?.takeIf { it.isNotBlank() }

        val matches = rixoMappingRepository.findTopMatch(
            stockLocation = stockLocation.trim(),
            rixoCompany = rixoCompany.trim(),
            supportedVehicleType = trimmedVehicleType
        )

        if (matches.isEmpty()) return null
        return matches.firstOrNull()
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

    /** Distinct non-blank [RixoMapping.auctionName] values for Rixo Price Mapping dropdowns. */
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
        auctionName: String?,
        stockLocation: String?,
        supportedVehicleType: String?,
        rixoPrice: String?,
    ): RixoMapping {
        val created = existing.createdAt
        return when (insertMode.uppercase()) {
            "AUCTION" -> existing.copy(
                auctionName = auctionName!!.trim().takeIf { it.isNotEmpty() },
                createdAt = created,
            )
            "STOCK" -> existing.copy(
                stockLocation = stockLocation!!.trim(),
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
                supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
                rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotEmpty() }
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
            supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotEmpty() },
            rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotEmpty() }
        )
        return rixoMappingRepository.save(updated)
    }

    fun delete(id: Long): Boolean {
        if (!rixoMappingRepository.existsById(id)) return false
        rixoMappingRepository.deleteById(id)
        return true
    }
}

