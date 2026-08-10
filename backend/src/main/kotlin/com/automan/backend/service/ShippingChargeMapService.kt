package com.automan.backend.service

import com.automan.backend.dto.SupplierMapPageResponse
import com.automan.backend.model.ShippingChargeMap
import com.automan.backend.repository.ShippingChargeMapRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ShippingChargeMapService(
    private val shippingChargeMapRepository: ShippingChargeMapRepository,
) {

    @Transactional(readOnly = true)
    fun findAllAsMaps(): List<Map<String, Any>> =
        shippingChargeMapRepository.findAll(Sort.by(Sort.Direction.DESC, "id")).map { toDto(it) }

    @Transactional(readOnly = true)
    fun findAsMapsByStockLocation(stockLocation: String): List<Map<String, Any>> {
        val loc = stockLocation.trim()
        if (loc.isEmpty()) return emptyList()
        return shippingChargeMapRepository
            .findByStockLocationIgnoreCaseOrderByCarsPerContainerAsc(loc)
            .map { toDto(it) }
    }

    /**
     * Paginated browse for Shipping Charge Map UI (no search text). Prefer this over [findAllAsMaps] for UI.
     */
    @Transactional(readOnly = true)
    fun listPage(
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): SupplierMapPageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, resolveShippingChargeMapSort(sortField, sortOrder))
        val pg = shippingChargeMapRepository.findAll(pageable)
        val content = pg.content.map { toDto(it) }
        return SupplierMapPageResponse(
            content = content,
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
        val pageable = PageRequest.of(pageIdx, size, resolveShippingChargeMapSort(sortField, sortOrder))
        val pg = when (field) {
            "stocklocation", "stock_location" ->
                shippingChargeMapRepository.searchStockLocationContains(q, pageable)
            "carspercontainer", "cars_per_container", "cars" ->
                shippingChargeMapRepository.searchCarsPerContainerContains(q, pageable)
            "shippingpricepercar", "shipping_price_per_car", "price" ->
                shippingChargeMapRepository.searchPriceContains(q, pageable)
            "all" ->
                shippingChargeMapRepository.searchAllFields(q, pageable)
            else ->
                throw IllegalArgumentException(
                    "Invalid search field: $field. Use all, stockLocation, carsPerContainer, or shippingPricePerCar.",
                )
        }
        val content = pg.content.map { toDto(it) }
        return SupplierMapPageResponse(
            content = content,
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    private fun resolveShippingChargeMapSort(sortField: String?, sortOrder: String?): Sort {
        val dir = if (sortOrder?.trim().equals("asc", ignoreCase = true) == true) {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        val prop = when (sortField?.trim()?.lowercase()) {
            null, "", "id" -> "id"
            "stocklocation", "stock_location" -> "stockLocation"
            "carspercontainer", "cars_per_container", "cars" -> "carsPerContainer"
            "shippingpricepercar", "shipping_price_per_car", "price" -> "shippingPricePerCar"
            else -> "id"
        }
        return Sort.by(dir, prop)
    }

    @Transactional(readOnly = true)
    fun getById(id: Long): ShippingChargeMap? =
        shippingChargeMapRepository.findById(id).orElse(null)

    @Transactional
    fun create(stockLocation: String, carsPerContainer: Int, shippingPricePerCar: BigDecimal): ShippingChargeMap {
        val loc = stockLocation.trim()
        require(loc.isNotEmpty()) { "stockLocation is required" }
        require(carsPerContainer > 0) { "carsPerContainer must be positive" }
        require(shippingPricePerCar >= BigDecimal.ZERO) { "shippingPricePerCar cannot be negative" }
        val existing = shippingChargeMapRepository.findByStockLocationIgnoreCaseAndCarsPerContainer(loc, carsPerContainer)
        require(existing == null) {
            "A row already exists for this stock location and cars-per-container."
        }
        val now = LocalDateTime.now()
        return shippingChargeMapRepository.save(
            ShippingChargeMap(
                stockLocation = loc,
                carsPerContainer = carsPerContainer,
                shippingPricePerCar = shippingPricePerCar.stripTrailingZeros(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun update(id: Long, stockLocation: String, carsPerContainer: Int, shippingPricePerCar: BigDecimal): ShippingChargeMap {
        val existing = shippingChargeMapRepository.findById(id).orElse(null)
            ?: error("Shipping charge row not found")
        val loc = stockLocation.trim()
        require(loc.isNotEmpty()) { "stockLocation is required" }
        require(carsPerContainer > 0) { "carsPerContainer must be positive" }
        require(shippingPricePerCar >= BigDecimal.ZERO) { "shippingPricePerCar cannot be negative" }
        val conflict = shippingChargeMapRepository.findByStockLocationIgnoreCaseAndCarsPerContainer(loc, carsPerContainer)
        if (conflict != null && conflict.id != existing.id) {
            error("Another row already uses this stock location and cars-per-container.")
        }
        val now = LocalDateTime.now()
        return shippingChargeMapRepository.save(
            existing.copy(
                stockLocation = loc,
                carsPerContainer = carsPerContainer,
                shippingPricePerCar = shippingPricePerCar.stripTrailingZeros(),
                createdAt = existing.createdAt,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    fun delete(id: Long) {
        shippingChargeMapRepository.deleteById(id)
    }

    /**
     * Replace all tiers for [stockLocation]: deletes existing rows for that stock (case-insensitive),
     * then inserts [tiers] sorted by cars count.
     */
    @Transactional
    fun replaceTiersForStockLocation(stockLocation: String, tiers: List<Pair<Int, BigDecimal>>): List<Map<String, Any>> {
        val loc = stockLocation.trim()
        require(loc.isNotEmpty()) { "stockLocation is required" }
        require(tiers.isNotEmpty()) { "At least one cars/price tier is required" }
        val normalized = tiers.map { (c, p) ->
            require(c > 0) { "carsPerContainer must be positive" }
            require(p >= BigDecimal.ZERO) { "shippingPricePerCar cannot be negative" }
            c to p.stripTrailingZeros()
        }
        val carsKeys = normalized.map { it.first }.toSet()
        require(carsKeys.size == normalized.size) { "Duplicate cars-per-container values are not allowed." }

        shippingChargeMapRepository.deleteByStockLocationIgnoreCase(loc)

        val now = LocalDateTime.now()
        val saved = normalized.sortedBy { it.first }.map { (cars, price) ->
            shippingChargeMapRepository.save(
                ShippingChargeMap(
                    stockLocation = loc,
                    carsPerContainer = cars,
                    shippingPricePerCar = price,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return saved.map { toDto(it) }
    }

    /**
     * When editing, user may rename stock: remove rows for [previousStockLocation] (if different from new stock),
     * then replace all rows for [stockLocation] with [tiers].
     */
    @Transactional
    fun replaceTiersRenamingStockIfNeeded(
        previousStockLocation: String?,
        stockLocation: String,
        tiers: List<Pair<Int, BigDecimal>>,
    ): List<Map<String, Any>> {
        val newLoc = stockLocation.trim()
        val prev = previousStockLocation?.trim()?.takeIf { it.isNotEmpty() }
        if (prev != null && !prev.equals(newLoc, ignoreCase = true)) {
            shippingChargeMapRepository.deleteByStockLocationIgnoreCase(prev)
        }
        return replaceTiersForStockLocation(newLoc, tiers)
    }

    @Transactional
    fun deleteAllForStockLocation(stockLocation: String) {
        val loc = stockLocation.trim()
        require(loc.isNotEmpty()) { "stockLocation is required" }
        shippingChargeMapRepository.deleteByStockLocationIgnoreCase(loc)
    }

    private fun sanitizeSearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    private fun toDto(e: ShippingChargeMap): Map<String, Any> =
        mapOf(
            "id" to (e.id ?: 0L),
            "stockLocation" to e.stockLocation,
            "carsPerContainer" to e.carsPerContainer,
            "shippingPricePerCar" to e.shippingPricePerCar,
        )
}
