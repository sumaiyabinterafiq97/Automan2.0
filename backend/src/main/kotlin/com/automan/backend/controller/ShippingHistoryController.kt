package com.automan.backend.controller

import com.automan.backend.dto.ShippingHistoryBatchRequest
import com.automan.backend.dto.ShippingHistoryDeleteBatchRequest
import com.automan.backend.dto.ShippingHistoryRowDto
import com.automan.backend.service.ShippingHistoryService
import com.automan.backend.util.Logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/shipping-history")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class ShippingHistoryController(
    private val shippingHistoryService: ShippingHistoryService,
) {

    @GetMapping
    fun list(): List<ShippingHistoryRowDto> = shippingHistoryService.listAllRows()

    @GetMapping("/for-invoice/client-names")
    fun clientNamesForInvoice(): ResponseEntity<Map<String, Any>> {
        val names = shippingHistoryService.distinctClientNamesForInvoice()
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to names,
            ),
        )
    }

    @GetMapping("/for-invoice/vessels")
    fun vesselsForInvoice(@RequestParam clientName: String): ResponseEntity<Map<String, Any>> {
        val vessels = shippingHistoryService.distinctVesselsForInvoiceClient(clientName)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "data" to vessels,
            ),
        )
    }

    @GetMapping("/for-invoice/lines")
    fun linesForInvoice(
        @RequestParam clientName: String,
        @RequestParam vessel: String,
    ): ResponseEntity<Map<String, Any>> {
        val slice = shippingHistoryService.invoiceSlice(clientName, vessel)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "header" to slice.header,
                "lines" to slice.lines,
            ),
        )
    }

    @PostMapping("/batch")
    fun saveBatch(@RequestBody request: ShippingHistoryBatchRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val saved = shippingHistoryService.saveBatch(request)
            ResponseEntity.ok(mapOf("saved" to saved))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @PostMapping("/remove-chassis")
    fun removeChassis(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        return try {
            val chassisToken = body["chassisToken"]?.toString()?.trim().orEmpty()
            if (chassisToken.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf("error" to "chassisToken is required"))
            }
            val historyId = when (val v = body["historyId"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
            val result = shippingHistoryService.removeChassisTokenFromHistoryRow(historyId, chassisToken)
            ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        } catch (e: Exception) {
            Logger.error("[ShippingHistory] remove-chassis failed: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Internal error")))
        }
    }

    /** Deletes shipping-history rows by ID and resets `booking_requested = false` on their purchases. */
    @DeleteMapping("/batch")
    fun deleteBatch(@RequestBody request: ShippingHistoryDeleteBatchRequest): ResponseEntity<Map<String, Any>> =
        deleteBatchInternal(request.ids)

    /** Same as [deleteBatch]; POST avoids proxies/clients that drop DELETE request bodies. */
    @PostMapping("/delete-batch")
    fun deleteBatchPost(@RequestBody request: ShippingHistoryDeleteBatchRequest): ResponseEntity<Map<String, Any>> =
        deleteBatchInternal(request.ids)

    private fun deleteBatchInternal(ids: List<Long>): ResponseEntity<Map<String, Any>> {
        return try {
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf("error" to "No IDs provided"))
            }
            Logger.log("[ShippingHistory] DELETE batch: $ids")
            val result = shippingHistoryService.deleteAndUnbookByIds(ids)
            ResponseEntity.ok(mapOf("success" to true) + result)
        } catch (e: Exception) {
            Logger.error("[ShippingHistory] DELETE batch failed: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Internal error")))
        }
    }
}
