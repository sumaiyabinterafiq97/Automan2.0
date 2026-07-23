package com.automan.backend.controller

import com.automan.backend.service.ShippingChargeMapService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/shipping-charge-map")
class ShippingChargeMapController(
    private val shippingChargeMapService: ShippingChargeMapService,
) {

    @GetMapping("/mappings")
    fun listAll(): ResponseEntity<Map<String, Any>> {
        val data = shippingChargeMapService.findAllAsMaps()
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to data,
                "count" to data.size,
            ),
        )
    }

    /**
     * Paginated browse for Shipping Charge Map (no search text). Prefer this over [listAll] for UI.
     */
    @GetMapping("/mappings/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(shippingChargeMapService.listPage(page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/mappings/page-search")
    fun pageSearch(
        @RequestParam q: String,
        @RequestParam(defaultValue = "all") field: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(shippingChargeMapService.searchPage(q, field, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @PostMapping("/mappings/add")
    fun add(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val stock = body["stockLocation"]?.toString()?.trim().orEmpty()
            val cars = parsePositiveInt(body["carsPerContainer"])
            val price = parseBigDecimal(body["shippingPricePerCar"])
            val saved = shippingChargeMapService.create(stock, cars, price)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Shipping charge row added",
                    "data" to mapOf(
                        "id" to (saved.id ?: 0L),
                        "stockLocation" to saved.stockLocation,
                        "carsPerContainer" to saved.carsPerContainer,
                        "shippingPricePerCar" to saved.shippingPricePerCar,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to add"))
        }
    }

    @PutMapping("/mappings/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val stock = body["stockLocation"]?.toString()?.trim().orEmpty()
            val cars = parsePositiveInt(body["carsPerContainer"])
            val price = parseBigDecimal(body["shippingPricePerCar"])
            val saved = shippingChargeMapService.update(id, stock, cars, price)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Shipping charge row updated",
                    "data" to mapOf(
                        "id" to (saved.id ?: 0L),
                        "stockLocation" to saved.stockLocation,
                        "carsPerContainer" to saved.carsPerContainer,
                        "shippingPricePerCar" to saved.shippingPricePerCar,
                    ),
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to update"))
        }
    }

    @DeleteMapping("/mappings/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Map<String, Any>> {
        return try {
            shippingChargeMapService.delete(id)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Deleted"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to delete"))
        }
    }

    /**
     * Replace all tiers for one stock location (used by grouped Shipping Charge Map UI).
     * Body: `{ "stockLocation": "KLC", "previousStockLocation": "KLC"|null, "tiers": [ {"carsPerContainer":2,"shippingPricePerCar":17000}, ... ] }`
     */
    @PutMapping("/mappings/replace-tiers")
    fun replaceTiers(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val stock = body["stockLocation"]?.toString()?.trim().orEmpty()
            val prev = body["previousStockLocation"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val tiersAny = body["tiers"] ?: throw IllegalArgumentException("tiers is required")
            val tiersList = tiersAny as? List<*> ?: throw IllegalArgumentException("tiers must be an array")
            val pairs = tiersList.mapIndexed { idx, row ->
                val m = row as? Map<*, *> ?: throw IllegalArgumentException("Invalid tier at index $idx")
                val cars = parsePositiveInt(m["carsPerContainer"])
                val price = parseBigDecimal(m["shippingPricePerCar"])
                cars to price
            }
            val saved = shippingChargeMapService.replaceTiersRenamingStockIfNeeded(prev, stock, pairs)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Shipping charge map saved",
                    "data" to saved,
                    "count" to saved.size,
                ),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to save"))
        }
    }

    @GetMapping("/mappings/by-stock-location")
    fun listByStockLocation(@RequestParam stockLocation: String): ResponseEntity<Map<String, Any>> {
        val data = shippingChargeMapService.findAsMapsByStockLocation(stockLocation)
        return ResponseEntity.ok(mapOf("success" to true, "data" to data, "count" to data.size))
    }

    @DeleteMapping("/mappings/by-stock-location")
    fun deleteByStockLocation(@RequestParam stockLocation: String): ResponseEntity<Map<String, Any>> {
        return try {
            shippingChargeMapService.deleteAllForStockLocation(stockLocation)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Deleted"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to delete"))
        }
    }

    private fun errorBody(message: String) = mapOf("success" to false, "message" to message)

    private fun parsePositiveInt(raw: Any?): Int {
        val n = when (raw) {
            null -> throw IllegalArgumentException("carsPerContainer is required")
            is Number -> raw.toInt()
            else -> raw.toString().trim().toIntOrNull()
                ?: throw IllegalArgumentException("carsPerContainer must be a number")
        }
        require(n > 0) { "carsPerContainer must be positive" }
        return n
    }

    private fun parseBigDecimal(raw: Any?): BigDecimal {
        val s = when (raw) {
            null -> throw IllegalArgumentException("shippingPricePerCar is required")
            is BigDecimal -> return raw.stripTrailingZeros()
            is Number -> raw.toString()
            else -> raw.toString().trim().replace(",", "").replace("¥", "").trim()
        }
        require(s.isNotEmpty()) { "shippingPricePerCar is required" }
        return try {
            BigDecimal(s).stripTrailingZeros()
        } catch (_: Exception) {
            throw IllegalArgumentException("shippingPricePerCar must be a number")
        }
    }
}
