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

    @GetMapping("/page")
    fun listPage(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(invoiceHistoryService.listRowsPage(page, size))
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
            ResponseEntity.ok(invoiceHistoryService.searchRowsPage(q, page, size))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Bad request")))
        }
    }

    /** Download PDF for a saved invoice (same layout as Create/Recreate Invoice PDF). */
    @GetMapping("/{invoiceNumber}/pdf")
    fun downloadPdf(@PathVariable invoiceNumber: String): ResponseEntity<ByteArray> {
        val pdfBytes = invoiceHistoryService.generatePdfForInvoiceNumber(invoiceNumber)
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
