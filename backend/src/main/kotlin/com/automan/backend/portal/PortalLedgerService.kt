package com.automan.backend.portal

import com.automan.backend.model.Event
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import com.automan.backend.service.ClientService
import com.automan.backend.service.ClientTransactionsReportService
import com.automan.backend.service.EventService
import com.automan.backend.service.PdfService
import com.automan.backend.util.PdfFilenameUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/**
 * The signed-in client's own ledger. The client id comes from [PortalScope], never from the request.
 */
@Service
class PortalLedgerService(
    private val clientRepository: ClientRepository,
    private val events: EventRepository,
    private val clients: ClientService,
    private val reports: ClientTransactionsReportService,
    private val pdf: PdfService,
    private val eventService: EventService,
) {
    @Transactional(readOnly = true)
    fun ledger(scope: PortalScope): PortalLedgerDto = ledgerFor(requireClient(scope))

    @Transactional
    fun updateCreditLimit(scope: PortalScope, raw: Any?): PortalLedgerDto {
        val clientId = requireClient(scope)
        val limit = parseLimit(raw)
        clients.updateCreditLimit(clientId, limit)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        return ledgerFor(clientId)
    }

    @Transactional(readOnly = true)
    fun statementPdf(scope: PortalScope, startDate: LocalDate?, endDate: LocalDate?): ResponseEntity<ByteArray> {
        val clientId = requireClient(scope)
        val statement = try {
            reports.buildClientStatement(clientId, startDate, endDate)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        }
        val filename = PdfFilenameUtils.build("ClientStatement", statement.clientName)
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, PdfFilenameUtils.contentDisposition(filename))
            .body(pdf.generateClientStatementPdf(statement))
    }

    @Transactional(readOnly = true)
    fun exportCsv(scope: PortalScope): ResponseEntity<String> {
        val clientId = requireClient(scope)
        val csv = try {
            eventService.exportClientTransactions(clientId)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "text/csv")
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=client_${clientId}_transactions.csv")
            .body(csv)
    }

    private fun ledgerFor(clientId: Long): PortalLedgerDto {
        val client = clientRepository.findById(clientId).orElse(null)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        val rows = events.findByClientIdOrderByEventDateDesc(clientId)
            .sortedWith(compareByDescending<Event> { it.eventDate }.thenByDescending { it.id ?: 0L })
        val balance = client.currentBalance
        return PortalLedgerDto(
            clientName = client.clientName,
            clientNumber = client.clientNumber,
            status = client.status.name,
            currency = client.currency,
            creditLimit = client.creditLimit,
            alertThreshold = client.alertThreshold,
            currentBalance = balance,
            availableCredit = client.creditLimit?.let { it + balance },
            events = rows.map { event ->
                PortalLedgerEventDto(
                    id = event.id ?: 0L,
                    eventDate = event.eventDate.toString(),
                    eventType = event.eventType.name,
                    eventDescription = event.eventDescription,
                    billNumber = event.billNumber,
                    invoiceNumber = event.invoiceNumber,
                    transactionPrice = event.transactionPrice,
                    paymentReceived = event.paymentReceived,
                    runningBalance = event.runningBalance,
                )
            },
        )
    }

    private fun requireClient(scope: PortalScope): Long {
        if (!scope.active || scope.clientIds.size != 1) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        }
        return scope.clientIds.single()
    }

    private fun parseLimit(raw: Any?): Double? {
        val limit = when (raw) {
            null -> null
            is Number -> raw.toDouble()
            is String -> {
                val text = raw.trim()
                if (text.isEmpty()) null else text.toDoubleOrNull()
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, LIMIT_MESSAGE)
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, LIMIT_MESSAGE)
        }
        if (limit != null && (limit < 0.0 || limit.isNaN())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, LIMIT_MESSAGE)
        }
        return limit
    }

    companion object {
        const val NOT_FOUND = "Not found"
        const val LIMIT_MESSAGE = "Credit limit must be a number ≥ 0."
    }
}
