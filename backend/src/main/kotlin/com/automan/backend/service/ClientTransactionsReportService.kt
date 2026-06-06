package com.automan.backend.service

import com.automan.backend.dto.ClientAgingSummaryDto
import com.automan.backend.dto.ClientStatementDto
import com.automan.backend.dto.ClientStatementLineDto
import com.automan.backend.dto.UnpaidAgingReportDto
import com.automan.backend.dto.UnpaidInvoiceAgingRowDto
import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.EventRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Service
class ClientTransactionsReportService(
    private val clientRepository: ClientRepository,
    private val eventRepository: EventRepository,
) {

    fun buildClientStatement(
        clientId: Long,
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
    ): ClientStatementDto {
        val client = clientRepository.findById(clientId).orElseThrow {
            IllegalArgumentException("Client not found: $clientId")
        }
        val events = loadStatementEvents(clientId, startDate, endDate)
        val lines = events.map { eventToStatementLine(it) }
        val balance = client.currentBalance
        return ClientStatementDto(
            clientNumber = client.clientNumber,
            clientName = client.clientName,
            currency = client.currency,
            creditLimit = client.creditLimit,
            currentBalance = balance,
            availableCredit = client.creditLimit?.let { it + balance },
            periodStart = startDate,
            periodEnd = endDate,
            generatedAt = LocalDate.now(),
            balanceLabel = balanceLabel(balance),
            lines = lines,
        )
    }

    fun buildUnpaidAgingReport(asOf: LocalDate = LocalDate.now()): UnpaidAgingReportDto {
        val invoiceEvents = eventRepository.findByEventTypeInOrderByClientIdAscEventDateAsc(
            listOf(EventType.INVOICE_ISSUED, EventType.INVOICE_REVERSAL),
        )
        val clientsById = clientRepository.findAll().associateBy { it.id!! }
        val rows = mutableListOf<UnpaidInvoiceAgingRowDto>()

        val byClientInvoice = invoiceEvents
            .filter { !it.invoiceNumber.isNullOrBlank() }
            .groupBy { "${it.clientId}|${it.invoiceNumber!!.trim().uppercase()}" }

        for ((_, events) in byClientInvoice) {
            val clientId = events.first().clientId
            val client = clientsById[clientId] ?: continue
            val inv = events.first().invoiceNumber!!.trim()
            var issued = 0.0
            var reversed = 0.0
            var invoiceDate: LocalDate? = null
            for (e in events) {
                when (e.eventType) {
                    EventType.INVOICE_ISSUED -> {
                        issued += e.transactionPrice ?: 0.0
                        if (invoiceDate == null || e.eventDate.isBefore(invoiceDate)) {
                            invoiceDate = e.eventDate
                        }
                    }
                    EventType.INVOICE_REVERSAL -> reversed += e.paymentReceived ?: 0.0
                    else -> { }
                }
            }
            val open = (issued - reversed).coerceAtLeast(0.0)
            if (open < 0.01) continue
            val invDate = invoiceDate ?: events.minOf { it.eventDate }
            val days = ChronoUnit.DAYS.between(invDate, asOf).coerceAtLeast(0)
            rows.add(
                UnpaidInvoiceAgingRowDto(
                    clientId = clientId,
                    clientNumber = client.clientNumber,
                    clientName = client.clientName,
                    invoiceNumber = inv,
                    invoiceDate = invDate,
                    openAmount = open,
                    daysOutstanding = days,
                    agingBucket = agingBucketForDays(days),
                ),
            )
        }

        rows.sortWith(compareBy({ it.clientName.lowercase() }, { it.invoiceDate }, { it.invoiceNumber }))

        val summaries = rows
            .groupBy { it.clientId }
            .map { (cid, clientRows) ->
                val c = clientsById[cid]!!
                ClientAgingSummaryDto(
                    clientId = cid,
                    clientNumber = c.clientNumber,
                    clientName = c.clientName,
                    bucket0to30 = clientRows.filter { it.agingBucket == BUCKET_0_30 }.sumOf { it.openAmount },
                    bucket31to60 = clientRows.filter { it.agingBucket == BUCKET_31_60 }.sumOf { it.openAmount },
                    bucket61to90 = clientRows.filter { it.agingBucket == BUCKET_61_90 }.sumOf { it.openAmount },
                    bucket90Plus = clientRows.filter { it.agingBucket == BUCKET_90_PLUS }.sumOf { it.openAmount },
                    totalOpen = clientRows.sumOf { it.openAmount },
                )
            }
            .sortedBy { it.clientName.lowercase() }

        return UnpaidAgingReportDto(
            asOfDate = asOf,
            rows = rows,
            summaries = summaries,
            totalOpen = rows.sumOf { it.openAmount },
        )
    }

    fun exportUnpaidAgingCsv(report: UnpaidAgingReportDto): String {
        val header = "Client,Client Number,Invoice,Invoice Date,Days Open,Bucket,Open Amount (JPY)\n"
        val body = report.rows.joinToString("\n") { row ->
            listOf(
                csvEscape(row.clientName),
                csvEscape(row.clientNumber),
                csvEscape(row.invoiceNumber),
                row.invoiceDate.toString(),
                row.daysOutstanding.toString(),
                csvEscape(row.agingBucket),
                row.openAmount.toLong().toString(),
            ).joinToString(",")
        }
        return header + body
    }

    private fun loadStatementEvents(
        clientId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): List<Event> {
        val all = eventRepository.findByClientIdOrderByEventDateAscCreatedAtAsc(clientId)
        if (startDate == null && endDate == null) return all
        return all.filter { e ->
            val afterStart = startDate == null || !e.eventDate.isBefore(startDate)
            val beforeEnd = endDate == null || !e.eventDate.isAfter(endDate)
            afterStart && beforeEnd
        }
    }

    private fun eventToStatementLine(event: Event): ClientStatementLineDto {
        val credit = event.paymentReceived?.takeIf { it > 0.0 }
        val debit = event.transactionPrice?.takeIf { it > 0.0 }
        val reference = event.billNumber?.trim()?.takeIf { it.isNotEmpty() }
            ?: event.invoiceNumber?.trim()?.takeIf { it.isNotEmpty() }
        return ClientStatementLineDto(
            date = event.eventDate,
            typeLabel = eventTypeLabel(event.eventType),
            reference = reference,
            description = event.eventDescription?.trim()?.takeIf { it.isNotEmpty() },
            credit = credit,
            debit = debit,
            balance = event.runningBalance,
        )
    }

    private fun eventTypeLabel(type: EventType): String = when (type) {
        EventType.INVOICE_ISSUED -> "Invoice"
        EventType.INVOICE_REVERSAL -> "Reversal"
        EventType.PAYMENT_RECEIVED -> "Payment"
        EventType.ADJUSTMENT -> "Adjustment"
        EventType.OPENING_BALANCE -> "Opening balance"
        EventType.SHIPMENT -> "Shipment"
        EventType.OTHER -> "Other (legacy)"
    }

    private fun balanceLabel(balance: Double): String = when {
        balance > 0.0 -> "Prepaid credit"
        balance < 0.0 -> "Amount owed"
        else -> "Settled"
    }

    private fun agingBucketForDays(days: Long): String = when {
        days <= 30 -> BUCKET_0_30
        days <= 60 -> BUCKET_31_60
        days <= 90 -> BUCKET_61_90
        else -> BUCKET_90_PLUS
    }

    private fun csvEscape(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuote) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    companion object {
        const val BUCKET_0_30 = "0-30 days"
        const val BUCKET_31_60 = "31-60 days"
        const val BUCKET_61_90 = "61-90 days"
        const val BUCKET_90_PLUS = "90+ days"
    }
}
