package com.automan.backend.service

import com.automan.backend.dto.SupplierMapPageResponse
import com.automan.backend.model.RixoPrice
import com.automan.backend.repository.RixoPriceRepository
import com.automan.backend.util.Logger
import com.automan.backend.util.RixoPolFromStockLocation
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.InputStreamReader

@Service
class RixoImportService(
    private val rixoPriceRepository: RixoPriceRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    
    fun importRixoPricesFromCsv(csvContent: String): ImportResult {
        try {
            val lines = csvContent.trim().split("\n")
            if (lines.isEmpty()) {
                return ImportResult(false, "CSV content is empty", 0, 0)
            }
            
            val header = lines[0].split(",")
            val dataLines = lines.drop(1)
            
            var successCount = 0
            var errorCount = 0
            val errors = mutableListOf<String>()
            
            // Clear existing data
            rixoPriceRepository.deleteAll()
            
            dataLines.forEachIndexed { index, line ->
                try {
                    val values = parseCsvLine(line)
                    // 4 cols: auction, stock, company, venue — OR legacy 5 cols: auction, (vehicle type ignored), stock, company, venue
                    val parsed = when {
                        values.size >= 5 -> ParsedRixoCsvRow(
                            auctionHouse = values[0].trim(),
                            stockLocation = values[2].trim(),
                            rixoCompany = values[3].trim(),
                            venueId = values[4].trim().takeIf { it.isNotEmpty() }
                        )
                        values.size >= 4 -> ParsedRixoCsvRow(
                            auctionHouse = values[0].trim(),
                            stockLocation = values[1].trim(),
                            rixoCompany = values[2].trim(),
                            venueId = values[3].trim().takeIf { it.isNotEmpty() }
                        )
                        else -> null
                    }
                    if (parsed != null) {
                        val rixoPrice = RixoPrice(
                            auctionHouse = parsed.auctionHouse,
                            stockLocation = parsed.stockLocation,
                            rixoCompany = parsed.rixoCompany,
                            venueId = parsed.venueId,
                            pol = RixoPolFromStockLocation.derivePol(parsed.stockLocation)
                        )
                        rixoPriceRepository.save(rixoPrice)
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
                errors = if (errors.isNotEmpty()) errors else null
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
                        // Escaped quote
                        current.append('"')
                        i++ // Skip next quote
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
    fun getAllRixoPrices(): List<RixoPrice> {
        return rixoPriceRepository.findAll()
    }

    /**
     * Supplier map and similar UIs use this path so we never rely on Hibernate-generated SQL for
     * rixo_prices (avoids 500s when the DB was trimmed, e.g. no type_of_vehicle).
     */
    @Transactional(readOnly = true)
    fun getAllRixoPricesAsMaps(): List<Map<String, Any>> {
        val sql = """
            SELECT id, auction_name, stock_location, rixo_company, venue_id, pol
            FROM rixo_prices
            ORDER BY id DESC
        """.trimIndent()
        return jdbcTemplate.query(sql) { rs, _ ->
            buildMap {
                put("id", rs.getLong("id"))
                put("auctionHouse", rs.getString("auction_name").orEmpty())
                put("shipmentSize", "")
                put("stockLocation", rs.getString("stock_location").orEmpty())
                put("rixoCompany", rs.getString("rixo_company").orEmpty())
                put("venueId", rs.getString("venue_id").orEmpty())
                put("pol", rs.getString("pol").orEmpty())
            }
        }
    }

    /**
     * Paginated search for Supplier Map UI (supplier name / stock / rixo company / all).
     * [field]: `all`, `supplierName`, `stockLocation`, `rixoCompany`.
     */
    @Transactional(readOnly = true)
    fun searchSupplierMapPage(rawQuery: String, rawField: String, page: Int, rawSize: Int): SupplierMapPageResponse {
        val q = sanitizeSupplierMapSearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val field = rawField.trim().lowercase().ifEmpty { "all" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "id"))
        val pg = when (field) {
            "suppliername", "auctionhouse", "auction_house" ->
                rixoPriceRepository.searchSupplierMapAuctionHouseContains(q, pageable)
            "stocklocation", "stock_location" ->
                rixoPriceRepository.searchSupplierMapStockLocationContains(q, pageable)
            "rixocompany", "rixo_company" ->
                rixoPriceRepository.searchSupplierMapRixoCompanyContains(q, pageable)
            "all" -> rixoPriceRepository.searchSupplierMapAllFields(q, pageable)
            else -> throw IllegalArgumentException(
                "Invalid search field: $field. Use all, supplierName, stockLocation, or rixoCompany."
            )
        }
        val content = pg.content.map { rixoPriceToSupplierMap(it) }
        return SupplierMapPageResponse(
            content = content,
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    private fun sanitizeSupplierMapSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    private fun rixoPriceToSupplierMap(r: RixoPrice): Map<String, Any> = mapOf(
        "id" to r.id,
        "auctionHouse" to r.auctionHouse,
        "shipmentSize" to "",
        "stockLocation" to r.stockLocation,
        "rixoCompany" to r.rixoCompany,
        "venueId" to (r.venueId ?: ""),
        "pol" to (r.pol ?: ""),
    )
    
    fun getDistinctAuctionHouses(): List<String> {
        return rixoPriceRepository.findDistinctAuctionNames()
    }
    
    fun getDistinctStockLocations(): List<String> {
        return rixoPriceRepository.findDistinctStockLocations()
    }
    
    fun getDistinctRixoCompanies(): List<String> {
        return rixoPriceRepository.findDistinctRixoCompanies()
    }
    
    /** No price column on rixo_prices; vehicle types live in master_menu. */
    fun getDistinctRixoPrices(): List<String> = emptyList()
    
    fun getRixoPricesByAuctionHouse(auctionHouse: String): List<RixoPrice> {
        return rixoPriceRepository.findByAuctionHouse(auctionHouse)
    }
    
    // New CRUD methods for inline mapping management
    fun saveRixoPrice(rixoPrice: RixoPrice): RixoPrice {
        return rixoPriceRepository.save(rixoPrice)
    }
    
    /**
     * Inserts a new row or, if [auctionHouse] already exists (case-insensitive), merges
     * stock / company / venue / POL tokens into the existing row (semicolon/comma-separated, deduped).
     * Explicit non-blank [pol] from the client is merged with existing POL (Supplier Map branches).
     * When the merged POL string is still blank, it falls back to [RixoPolFromStockLocation.derivePol]
     * on the merged stock locations.
     */
    @Transactional
    fun saveRixoPriceWithAuctionHouse(
        auctionHouse: String,
        stockLocation: String,
        rixoCompany: String,
        venueId: String?,
        pol: String? = null
    ): SaveRixoMappingResult {
        val auction = auctionHouse.trim()
        val incomingStock = stockLocation.trim().ifBlank { "-" }
        val incomingRixo = rixoCompany.trim().ifBlank { "-" }
        val incomingVenue = venueId?.trim()?.takeIf { it.isNotBlank() }

        val existing = rixoPriceRepository.findFirstByAuctionHouseIgnoreCase(auction)
            ?: rixoPriceRepository.findByAuctionHouse(auction).firstOrNull()

        if (existing != null) {
            val mergedStock = mergeSemicolonRequired(existing.stockLocation, incomingStock)
            val mergedRixo = mergeSemicolonRequired(existing.rixoCompany, incomingRixo)
            val mergedVenue = mergeSemicolonNullable(existing.venueId, incomingVenue)
            val mergedPol = mergeSemicolonNullable(existing.pol, pol)
            val derivedPol = if (!mergedPol.isNullOrBlank()) mergedPol else RixoPolFromStockLocation.derivePol(mergedStock)
            val updated = existing.copy(
                stockLocation = mergedStock,
                rixoCompany = mergedRixo,
                venueId = mergedVenue,
                pol = derivedPol,
                createdAt = existing.createdAt
            )
            val saved = rixoPriceRepository.save(updated)
            return SaveRixoMappingResult(price = saved, merged = true)
        }

        val saved = rixoPriceRepository.save(
            RixoPrice(
                auctionHouse = auction,
                stockLocation = incomingStock,
                rixoCompany = incomingRixo,
                venueId = incomingVenue,
                pol = if (!pol.isNullOrBlank()) pol else RixoPolFromStockLocation.derivePol(incomingStock)
            )
        )
        return SaveRixoMappingResult(price = saved, merged = false)
    }
    
    fun getRixoPriceById(id: Long): RixoPrice? {
        return rixoPriceRepository.findById(id).orElse(null)
    }
    
    fun deleteRixoPrice(id: Long) {
        rixoPriceRepository.deleteById(id)
    }

    private fun semicolonTokens(raw: String): List<String> =
        raw.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() && it != "-" }

    /** Preserves order: existing tokens first, then new; dedupes case-insensitively. */
    private fun mergeSemicolonRequired(existing: String, incoming: String): String {
        val merged = linkedMapOf<String, String>()
        fun addFrom(s: String) {
            for (t in semicolonTokens(s)) {
                val key = t.lowercase()
                if (!merged.containsKey(key)) merged[key] = t
            }
        }
        addFrom(existing)
        addFrom(incoming)
        val out = merged.values.joinToString(";")
        return out.ifBlank { "-" }
    }

    private fun mergeSemicolonNullable(existing: String?, incoming: String?): String? {
        val merged = linkedMapOf<String, String>()
        fun addFrom(s: String?) {
            if (s.isNullOrBlank()) return
            for (t in semicolonTokens(s)) {
                val key = t.lowercase()
                if (!merged.containsKey(key)) merged[key] = t
            }
        }
        addFrom(existing)
        addFrom(incoming)
        return merged.values.joinToString(";").takeIf { it.isNotBlank() }
    }
}

private data class ParsedRixoCsvRow(
    val auctionHouse: String,
    val stockLocation: String,
    val rixoCompany: String,
    val venueId: String?
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val successCount: Int,
    val errorCount: Int,
    val errors: List<String>? = null
)

data class SaveRixoMappingResult(
    val price: RixoPrice,
    val merged: Boolean
)
