package com.automan.backend.controller

import com.automan.backend.dto.InvoiceHistoryRowDto
import com.automan.backend.service.InvoiceHistoryService
import com.automan.backend.util.PdfFilenameUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/invoice-history")
class InvoiceHistoryController(
    private val invoiceHistoryService: InvoiceHistoryService,
) {
    @GetMapping
    fun list(): List<InvoiceHistoryRowDto> = invoiceHistoryService.listAllRows()

    @GetMapping("/filter-options")
    fun filterOptions(): Map<String, List<String>> = invoiceHistoryService.filterOptions()

    @GetMapping("/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(required = false) clientName: String?,
        @RequestParam(required = false) vessel: String?,
        @RequestParam(required = false) bookingNo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(
                invoiceHistoryService.listRowsPage(page, size, sort, order, clientName, vessel, bookingNo, from, to),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    @GetMapping("/page-search")
    fun searchPage(
        @RequestParam q: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(required = false) clientName: String?,
        @RequestParam(required = false) vessel: String?,
        @RequestParam(required = false) bookingNo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(
                invoiceHistoryService.searchRowsPage(q, page, size, sort, order, clientName, vessel, bookingNo, from, to),
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /** Zip of one PDF per invoice matching the current filters. */
    @GetMapping("/print-all")
    fun printAll(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) clientName: String?,
        @RequestParam(required = false) vessel: String?,
        @RequestParam(required = false) bookingNo: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): ResponseEntity<Any> {
        return try {
            val zip = invoiceHistoryService.zipFilteredInvoices(q, clientName, vessel, bookingNo, from, to)
            val headers = HttpHeaders()
            headers.contentType = MediaType.parseMediaType("application/zip")
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-history.zip\"")
            ResponseEntity.ok().headers(headers).body(zip)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /** Download PDF for a saved invoice (same layout as Create/Recreate Invoice PDF). */
    @GetMapping("/{invoiceNumber}/pdf")
    fun downloadPdf(
        @PathVariable invoiceNumber: String,
        @RequestParam(required = false) invoiceDate: String?,
    ): ResponseEntity<*> {
        val override = invoiceDate?.trim()?.takeIf { it.isNotEmpty() }
        val parsedDate = if (override == null) {
            null
        } else {
            try {
                java.time.LocalDate.parse(override)
            } catch (_: java.time.format.DateTimeParseException) {
                return ResponseEntity.badRequest().body(mapOf("error" to "invoiceDate must be yyyy-MM-dd"))
            }
        }
        val pdfBytes = invoiceHistoryService.generatePdfForInvoiceNumber(invoiceNumber, parsedDate)
        val clientName = invoiceHistoryService.clientNameForInvoiceNumber(invoiceNumber)
        val filename = PdfFilenameUtils.build("Final_Invoice", clientName)
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.set(HttpHeaders.CONTENT_DISPOSITION, PdfFilenameUtils.contentDisposition(filename))
        return ResponseEntity.ok().headers(headers).body(pdfBytes)
    }

    data class BatchDeleteRequest(val invoiceNumbers: List<String> = emptyList())
    data class BatchDeleteResponse(
        val deleted: Int,
        val ledgerReversed: Int = 0,
        val ledgerWarnings: List<String> = emptyList(),
    )

    @DeleteMapping("/batch-delete")
    fun batchDelete(@RequestBody req: BatchDeleteRequest): BatchDeleteResponse =
        batchDeleteInternal(req.invoiceNumbers)

    /** POST mirror for clients/proxies that drop DELETE request bodies. */
    @PostMapping("/batch-delete")
    fun batchDeletePost(@RequestBody req: BatchDeleteRequest): BatchDeleteResponse =
        batchDeleteInternal(req.invoiceNumbers)

    private fun batchDeleteInternal(invoiceNumbers: List<String>): BatchDeleteResponse {
        val result = invoiceHistoryService.deleteByInvoiceNumbers(invoiceNumbers)
        return BatchDeleteResponse(
            deleted = result.deleted,
            ledgerReversed = result.ledgerReversed,
            ledgerWarnings = result.ledgerWarnings,
        )
    }
}
