package com.automan.backend.controller

import com.automan.backend.dto.InvoiceHistoryRowDto
import com.automan.backend.service.InvoiceHistoryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/invoice-history")
class InvoiceHistoryController(
    private val invoiceHistoryService: InvoiceHistoryService,
) {
    @GetMapping
    fun list(): List<InvoiceHistoryRowDto> = invoiceHistoryService.listAllRows()
}
