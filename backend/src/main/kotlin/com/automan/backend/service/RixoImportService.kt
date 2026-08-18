package com.automan.backend.service

import com.automan.backend.dto.SupplierMapRowDto
import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.RixoMappingRepository
import com.automan.backend.util.RixoPolFromStockLocation
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class RixoImportService(
    private val rixoMappingRepository: RixoMappingRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val purchaseRixoPriceSyncService: PurchaseRixoPriceSyncService,
) {

    fun importRixoPricesFromCsv(csvContent: String): ImportResult {
        try {
            val lines = csvContent.trim().split("\n")
            if (lines.isEmpty()) {
                return ImportResult(false, "CSV content is empty", 0, 0)
            }

            val dataLines = lines.drop(1)

            var successCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()

            dataLines.forEachIndexed { index, line ->
                try {
                    val values = parseCsvLine(line)
                    val parsed = when {
                        values.size >= 5 -> ParsedRixoCsvRow(
                            auctionHouse = values[0].trim(),
                            stockLocation = values[2].trim(),
                            rixoCompany = values[3].trim(),
                            venueId = values[4].trim().takeIf { it.isNotEmpty() },
                        )
                        values.size >= 4 -> ParsedRixoCsvRow(
                            auctionHouse = values[0].trim(),
                            stockLocation = values[1].trim(),
                            rixoCompany = values[2].trim(),
                            venueId = values[3].trim().takeIf { it.isNotEmpty() },
                        )
                        else -> null
                    }
                    if (parsed != null) {
                        saveRixoPriceWithAuctionHouse(
                            auctionHouse = parsed.auctionHouse,
                            stockLocation = parsed.stockLocation,
                            rixoCompany = parsed.rixoCompany,
                            venueId = parsed.venueId,
                            pol = RixoPolFromStockLocation.derivePol(parsed.stockLocation),
                        )
                        successCount++
                    } else {
                        errors.add("Line ${index + 2}: Insufficient columns (expected 4+, got ${values.size})")
                        errorCount++
                    }
                } catch (e: Exception) {
                    errors.add("Line ${index + 2}: ${e.message}")
                    errorCount++
                }
            }

            return ImportResult(
                success = true,
                message = "Import completed. Success: $successCount, Errors: $errorCount",
                successCount = successCount,
                errorCount = errorCount,
                errors = if (errors.isNotEmpty()) errors else null,
            )
        } catch (e: Exception) {
            return ImportResult(false, "Import failed: ${e.message}", 0, 0)
        }
    }

    fun importRixoPricesFromFile(file: MultipartFile): ImportResult {
        try {
            val content = BufferedReader(InputStreamReader(file.inputStream)).use { it.readText() }
            return importRixoPricesFromCsv(content)
        } catch (e: Exception) {
            return ImportResult(false, "File import failed: ${e.message}", 0, 0)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    @Transactional(readOnly = true)
    fun getAllRixoPrices(): List<SupplierMapRowDto> =
        rixoMappingRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
            .filter { !it.auctionName.isNullOrBlank() }
            .map { SupplierMapRowDto.from(it) }

    @Transactional(readOnly = true)
    fun getAllRixoPricesAsMaps(): List<Map<String, Any>> {
        val sql = """
            SELECT id, auction_name, stock_location, rixo_company, venue_id, pol,
                   supported_vehicle_type, rixo_price
            FROM rixo_mapping
            WHERE auction_name IS NOT NULL AND TRIM(auction_name) <> ''
            ORDER BY id DESC
        """.trimIndent()
        return jdbcTemplate.query(sql) { rs, _ ->
            SupplierMapRowDto.toSupplierMapMap(
                SupplierMapRowDto(
                    id = rs.getLong("id"),
                    auctionHouse = rs.getString("auction_name").orEmpty(),
                    stockLocation = rs.getString("stock_location").orEmpty(),
                    rixoCompany = rs.getString("rixo_company").orEmpty(),
                    venueId = rs.getString("venue_id"),
                    pol = rs.getString("pol"),
                    supportedVehicleType = rs.getString("supported_vehicle_type"),
                    rixoPrice = rs.getString("rixo_price"),
                ),
            )
        }
    }

    fun getDistinctAuctionHouses(): List<String> =
        rixoMappingRepository.findDistinctAuctionNamesOrdered()

    fun getDistinctStockLocations(): List<String> =
        rixoMappingRepository.findDistinctStockLocationsOrdered()

    fun getDistinctRixoCompanies(): List<String> =
        rixoMappingRepository.findDistinctRixoCompaniesOrdered()

    fun getDistinctRixoPrices(): List<String> = emptyList()

    fun getRixoPricesByAuctionHouse(auctionHouse: String): List<SupplierMapRowDto> =
        rixoMappingRepository.findByAuctionNameIgnoreCase(auctionHouse.trim())
            .map { SupplierMapRowDto.from(it) }

    fun saveRixoPrice(row: SupplierMapRowDto): SupplierMapRowDto {
        val existing = rixoMappingRepository.findById(row.id).orElseThrow()
        val saved = rixoMappingRepository.save(
            existing.copy(
                auctionName = row.auctionHouse.trim(),
                stockLocation = row.stockLocation.trim(),
                rixoCompany = row.rixoCompany.trim(),
                venueId = row.venueId?.trim()?.takeIf { it.isNotBlank() },
                pol = row.pol?.trim()?.takeIf { it.isNotBlank() },
                supportedVehicleType = row.supportedVehicleType?.trim()?.takeIf { it.isNotBlank() },
                rixoPrice = row.rixoPrice?.trim()?.takeIf { it.isNotBlank() },
                createdAt = existing.createdAt,
            ),
        )
        return SupplierMapRowDto.from(saved)
    }

    @Transactional
    fun saveRixoPriceWithAuctionHouse(
        auctionHouse: String,
        stockLocation: String,
        rixoCompany: String,
        venueId: String?,
        pol: String? = null,
    ): SaveRixoMappingResult {
        val auction = auctionHouse.trim()
        val incomingStock = stockLocation.trim().ifBlank { "-" }
        val incomingRixo = rixoCompany.trim().ifBlank { "-" }
        val incomingVenue = venueId?.trim()?.takeIf { it.isNotBlank() }
        val incomingPol = pol?.trim()?.takeIf { it.isNotBlank() }
            ?: RixoPolFromStockLocation.derivePol(incomingStock)

        val existing = rixoMappingRepository.findByAuctionStockRixo(auction, incomingStock, incomingRixo)
            .firstOrNull()

        if (existing != null) {
            val updated = existing.copy(
                venueId = incomingVenue ?: existing.venueId,
                pol = incomingPol ?: existing.pol,
                createdAt = existing.createdAt,
            )
            val saved = rixoMappingRepository.save(updated)
            return SaveRixoMappingResult(price = SupplierMapRowDto.from(saved), merged = true)
        }

        val saved = rixoMappingRepository.save(
            RixoMapping(
                rixoCompany = incomingRixo,
                auctionName = auction,
                stockLocation = incomingStock,
                venueId = incomingVenue,
                pol = incomingPol,
            ),
        )
        return SaveRixoMappingResult(price = SupplierMapRowDto.from(saved), merged = false)
    }

    fun getRixoPriceById(id: Long): SupplierMapRowDto? =
        rixoMappingRepository.findById(id).orElse(null)?.let { SupplierMapRowDto.from(it) }

    fun deleteRixoPrice(id: Long) {
        rixoMappingRepository.deleteById(id)
    }

    fun toSupplierMapRow(mapping: RixoMapping): SupplierMapRowDto = SupplierMapRowDto.from(mapping)

    fun updateSupplierMapRow(
        id: Long,
        auctionHouse: String?,
        stockLocation: String?,
        rixoCompany: String?,
        venueId: String?,
        pol: String?,
        supportedVehicleType: String? = null,
        rixoPrice: String? = null,
    ): SupplierMapRowDto? {
        val existing = rixoMappingRepository.findById(id).orElse(null) ?: return null
        val newStock = stockLocation?.trim() ?: existing.stockLocation
        val saved = rixoMappingRepository.save(
            existing.copy(
                auctionName = auctionHouse?.trim()?.takeIf { it.isNotBlank() } ?: existing.auctionName,
                stockLocation = newStock,
                rixoCompany = rixoCompany?.trim()?.takeIf { it.isNotBlank() } ?: existing.rixoCompany,
                venueId = venueId?.trim()?.takeIf { it.isNotBlank() },
                pol = pol?.trim()?.takeIf { it.isNotBlank() }
                    ?: RixoPolFromStockLocation.derivePol(newStock)
                    ?: existing.pol,
                supportedVehicleType = supportedVehicleType?.trim()?.takeIf { it.isNotBlank() },
                rixoPrice = rixoPrice?.trim()?.takeIf { it.isNotBlank() },
                createdAt = existing.createdAt,
            ),
        )
        purchaseRixoPriceSyncService.syncIfPriceChanged(existing, saved)
        return SupplierMapRowDto.from(saved)
    }
}

private data class ParsedRixoCsvRow(
    val auctionHouse: String,
    val stockLocation: String,
    val rixoCompany: String,
    val venueId: String?,
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<String>? = null,
)

data class SaveRixoMappingResult(
    val price: SupplierMapRowDto,
    val merged: Boolean,
)
