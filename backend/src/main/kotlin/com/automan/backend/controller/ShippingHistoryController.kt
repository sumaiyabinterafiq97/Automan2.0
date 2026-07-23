package com.automan.backend.controller

import com.automan.backend.dto.ShippingHistoryBatchRequest
import com.automan.backend.dto.ShippingHistoryDeleteBatchRequest
import com.automan.backend.dto.ShippingHistoryRowDto
import com.automan.backend.service.ShippingHistoryExportService
import com.automan.backend.service.ShippingHistoryService
import com.automan.backend.util.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/shipping-history")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8084", "http://localhost:8085", "http://localhost:8089", "http://localhost:8090", "http://localhost:9090"])
class ShippingHistoryController(
    private val shippingHistoryService: ShippingHistoryService,
    private val shippingHistoryExportService: ShippingHistoryExportService,
) {

    @GetMapping
    fun list(): List<ShippingHistoryRowDto> = shippingHistoryService.listAllRows()

    @GetMapping("/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(shippingHistoryService.listRowsPage(page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/page-search")
    fun searchPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(shippingHistoryService.searchRowsPage(q, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /** Full-table shipping history export as Excel (.xlsx). */
    @GetMapping("/export/xlsx")
    fun exportShippingHistoryXlsx(): ResponseEntity<ByteArray> {
        return try {
            val bytes = shippingHistoryExportService.exportAllShippingHistoryXlsx()
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            ResponseEntity.ok()
                .header(
                    "Content-Disposition",
                    "attachment; filename=\"shipping_history_export_$ts.xlsx\"",
                )
                .contentType(
                    MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    ),
                )
                .body(bytes)
        } catch (e: Exception) {
            Logger.error("Shipping History XLSX export failed: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

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
            val purchaseId = when (val v = body["purchaseId"]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
            val result = shippingHistoryService.removeChassisTokenFromHistoryRow(
                historyId,
                chassisToken,
                purchaseId,
            )
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
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        } catch (e: Exception) {
            Logger.error("[ShippingHistory] DELETE batch failed: ${e.message}", e)
            ResponseEntity.status(500).body(mapOf("error" to (e.message ?: "Internal error")))
        }
    }
}
