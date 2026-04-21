package com.automan.backend.controller

import com.automan.backend.dto.ShippingHistoryBatchRequest
import com.automan.backend.dto.ShippingHistoryRowDto
import com.automan.backend.service.ShippingHistoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/shipping-history")
class ShippingHistoryController(
    private val shippingHistoryService: ShippingHistoryService,
) {

    @GetMapping
    fun list(): List<ShippingHistoryRowDto> = shippingHistoryService.listAllRows()

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
}
