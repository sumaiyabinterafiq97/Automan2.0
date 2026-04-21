package com.automan.backend.service

import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceHistoryRowDto
import com.automan.backend.model.InvoiceHistory
import com.automan.backend.repository.InvoiceHistoryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeParseException

@Service
class InvoiceHistoryService(
    private val invoiceHistoryRepository: InvoiceHistoryRepository,
    private val purchaseService: PurchaseService,
    private val pdfService: PdfService,
) {

    fun listAllRows(): List<InvoiceHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "createdAt")
        return invoiceHistoryRepository.findAll(sort).map { e ->
            InvoiceHistoryRowDto(
                invoiceNumber = e.invoiceNumber,
                vessel = e.vessel,
                clientName = e.clientName,
                shippingDate = e.shippingDate?.toString(),
                lcNo = e.lcNo,
                bank = e.bank,
                messages = e.messages,
                chassis = e.chassis,
                createdAt = e.createdAt.toString(),
            )
        }
    }

    @Transactional
    fun confirmAndDownload(request: InvoiceConfirmAndDownloadRequest): ByteArray {
        val pdf = request.pdf
        val inv = pdf.invoiceNumber.trim()
        if (inv.isEmpty()) {
            throw IllegalArgumentException("Invoice number is required")
        }
        if (invoiceHistoryRepository.existsByInvoiceNumber(inv)) {
            throw IllegalArgumentException("Invoice number already exists: $inv")
        }

        val shipDate: LocalDate? = request.shippingDateIso?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            try {
                LocalDate.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        val row = InvoiceHistory(
            invoiceNumber = inv,
            vessel = pdf.vessel.trim().takeIf { it.isNotEmpty() },
            clientName = pdf.clientName.trim().takeIf { it.isNotEmpty() },
            shippingDate = shipDate,
            lcNo = pdf.lcNumber?.trim()?.takeIf { it.isNotEmpty() },
            bank = pdf.bankAccount?.trim()?.takeIf { it.isNotEmpty() },
            messages = pdf.message?.trim()?.takeIf { it.isNotEmpty() },
            chassis = request.chassisJoined.trim().takeIf { it.isNotEmpty() },
        )
        invoiceHistoryRepository.save(row)

        if (request.purchaseIds.isNotEmpty()) {
            purchaseService.markPurchasesAsInvoiceConfirmed(request.purchaseIds)
        }

        return pdfService.generateInvoicePdf(pdf)
    }
}
