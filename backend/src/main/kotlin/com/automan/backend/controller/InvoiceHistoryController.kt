package com.automan.backend.controller

import com.automan.backend.dto.InvoiceHistoryRowDto
import com.automan.backend.service.InvoiceHistoryService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/invoice-history")
class InvoiceHistoryController(
    private val invoiceHistoryService: InvoiceHistoryService,
) {
    @GetMapping
    fun list(): List<InvoiceHistoryRowDto> = invoiceHistoryService.listAllRows()

    /** Download PDF for a saved invoice (same layout as Create/Recreate Invoice PDF). */
    @GetMapping("/{invoiceNumber}/pdf")
    fun downloadPdf(@PathVariable invoiceNumber: String): ResponseEntity<ByteArray> {
        val pdfBytes = invoiceHistoryService.generatePdfForInvoiceNumber(invoiceNumber)
        val safeName = invoiceNumber.trim().replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_PDF
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice_${safeName}.pdf\"")
        return ResponseEntity.ok().headers(headers).body(pdfBytes)
    }

    data class BatchDeleteRequest(val invoiceNumbers: List<String> = emptyList())
    data class BatchDeleteResponse(val deleted: Int)

    @DeleteMapping("/batch-delete")
    fun batchDelete(@RequestBody req: BatchDeleteRequest): BatchDeleteResponse =
        batchDeleteInternal(req.invoiceNumbers)

    /** POST mirror for clients/proxies that drop DELETE request bodies. */
    @PostMapping("/batch-delete")
    fun batchDeletePost(@RequestBody req: BatchDeleteRequest): BatchDeleteResponse =
        batchDeleteInternal(req.invoiceNumbers)

    private fun batchDeleteInternal(invoiceNumbers: List<String>): BatchDeleteResponse {
        val count = invoiceHistoryService.deleteByInvoiceNumbers(invoiceNumbers)
        return BatchDeleteResponse(deleted = count)
    }
}
