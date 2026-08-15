package com.automan.backend.controller

import com.automan.backend.service.StockLocationMapService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/stock-location-map", "/api/stock-location-map"])
class StockLocationMapController(
    private val stockLocationMapService: StockLocationMapService,
) {

    @GetMapping("/mappings")
    fun listAll(): ResponseEntity<Map<String, Any>> {
        val data = stockLocationMapService.findAllAsMaps()
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to data,
                "count" to data.size,
            ),
        )
    }

    @GetMapping("/mappings/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(stockLocationMapService.listPage(page, size, sort, order))
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
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(stockLocationMapService.searchPage(q, field, page, size, sort, order))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @PostMapping("/mappings/add")
    fun add(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any>> {
        return try {
            val stock = body["stockLocation"]?.toString().orEmpty()
            val pol = body["pol"]?.toString()
            val address = body["address"]?.toString()
            val saved = stockLocationMapService.create(stock, pol, address)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Stock location map row added",
                    "data" to mapOf(
                        "id" to (saved.id ?: 0L),
                        "stockLocation" to saved.stockLocation,
                        "pol" to (saved.pol ?: ""),
                        "address" to (saved.address ?: ""),
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
            val stock = body["stockLocation"]?.toString().orEmpty()
            val pol = body["pol"]?.toString()
            val address = body["address"]?.toString()
            val saved = stockLocationMapService.update(id, stock, pol, address)
            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Stock location map row updated",
                    "data" to mapOf(
                        "id" to (saved.id ?: 0L),
                        "stockLocation" to saved.stockLocation,
                        "pol" to (saved.pol ?: ""),
                        "address" to (saved.address ?: ""),
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
            stockLocationMapService.delete(id)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Deleted"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Invalid request"))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(errorBody(e.message ?: "Failed to delete"))
        }
    }

    private fun errorBody(message: String) = mapOf("success" to false, "message" to message)
}
