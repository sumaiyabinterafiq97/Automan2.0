package com.automan.backend.service

import com.automan.backend.dto.SupplierMapPageResponse
import com.automan.backend.model.StockLocationMap
import com.automan.backend.repository.StockLocationMapRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class StockLocationMapService(
    private val stockLocationMapRepository: StockLocationMapRepository,
) {

    @Transactional(readOnly = true)
    fun findAllAsMaps(): List<Map<String, Any>> =
        stockLocationMapRepository.findAll(Sort.by(Sort.Direction.ASC, "stockLocation")).map { toDto(it) }

    @Transactional(readOnly = true)
    fun listPage(
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): SupplierMapPageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, resolveSort(sortField, sortOrder))
        val pg = stockLocationMapRepository.findAll(pageable)
        return SupplierMapPageResponse(
            content = pg.content.map { toDto(it) },
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    @Transactional(readOnly = true)
    fun searchPage(
        rawQuery: String,
        rawField: String,
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): SupplierMapPageResponse {
        val q = sanitizeSearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val field = rawField.trim().lowercase().ifEmpty { "all" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, resolveSort(sortField, sortOrder))
        val pg = when (field) {
            "stocklocation", "stock_location" ->
                stockLocationMapRepository.searchStockLocationContains(q, pageable)
            "pol" ->
                stockLocationMapRepository.searchPolContains(q, pageable)
            "address" ->
                stockLocationMapRepository.searchAddressContains(q, pageable)
            "all" ->
                stockLocationMapRepository.searchAllFields(q, pageable)
            else ->
                throw IllegalArgumentException(
                    "Invalid search field: $field. Use all, stockLocation, pol, or address.",
                )
        }
        return SupplierMapPageResponse(
            content = pg.content.map { toDto(it) },
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    @Transactional
    fun create(stockLocation: String, pol: String?, address: String?): StockLocationMap {
        val loc = stockLocation.trim()
        require(loc.isNotEmpty()) { "stockLocation is required" }
        require(stockLocationMapRepository.findByStockLocationIgnoreCase(loc) == null) {
            "A row already exists for this stock location."
        }
        val now = LocalDateTime.now()
        return stockLocationMapRepository.save(
            StockLocationMap(
                stockLocation = loc,
                pol = normalizePol(pol),
                address = normalizeAddress(address),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun update(id: Long, stockLocation: String, pol: String?, address: String?): StockLocationMap {
        val existing = stockLocationMapRepository.findById(id).orElse(null)
            ?: throw IllegalArgumentException("Stock location map row not found")
        val loc = stockLocation.trim()
        require(loc.isNotEmpty()) { "stockLocation is required" }
        val conflict = stockLocationMapRepository.findByStockLocationIgnoreCase(loc)
        if (conflict != null && conflict.id != existing.id) {
            throw IllegalArgumentException("A row already exists for this stock location.")
        }
        val now = LocalDateTime.now()
        return stockLocationMapRepository.save(
            existing.copy(
                stockLocation = loc,
                pol = normalizePol(pol),
                address = normalizeAddress(address),
                createdAt = existing.createdAt,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun delete(id: Long) {
        require(stockLocationMapRepository.existsById(id)) { "Stock location map row not found" }
        stockLocationMapRepository.deleteById(id)
    }

    private fun resolveSort(sortField: String?, sortOrder: String?): Sort {
        val dir = if (sortOrder?.trim().equals("asc", ignoreCase = true) == true) {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        val prop = when (sortField?.trim()?.lowercase()) {
            null, "", "id" -> "id"
            "stocklocation", "stock_location" -> "stockLocation"
            "pol" -> "pol"
            "address" -> "address"
            else -> "id"
        }
        return Sort.by(dir, prop)
    }

    private fun sanitizeSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    private fun toDto(e: StockLocationMap): Map<String, Any> =
        mapOf(
            "id" to (e.id ?: 0L),
            "stockLocation" to e.stockLocation,
            "pol" to (e.pol ?: ""),
            "address" to (e.address ?: ""),
        )

    companion object {
        fun normalizePol(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val seen = LinkedHashSet<String>()
            val out = mutableListOf<String>()
            raw.split(Regex("[;,\n\r]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { token ->
                    val key = token.lowercase()
                    if (seen.add(key)) out.add(token)
                }
            if (out.isEmpty()) return null
            return out.joinToString("; ")
        }

        fun normalizeAddress(raw: String?): String? {
            val trimmed = raw?.trim().orEmpty()
            return trimmed.takeIf { it.isNotEmpty() }
        }
    }
}
