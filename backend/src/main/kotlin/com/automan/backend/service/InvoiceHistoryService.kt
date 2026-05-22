package com.automan.backend.service

import com.automan.backend.dto.ClientNameLedgerResolution
import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceConfirmResult
import com.automan.backend.dto.InvoiceHistoryRowDto
import com.automan.backend.dto.InvoiceItem
import com.automan.backend.dto.InvoiceLedgerResult
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Purchase
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.util.Logger
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

@Service
class InvoiceHistoryService(
    private val invoiceHistoryRepository: InvoiceHistoryRepository,
    private val invoiceHistoryLineRepository: InvoiceHistoryLineRepository,
    private val purchaseService: PurchaseService,
    private val pdfService: PdfService,
    private val clientRepository: ClientRepository,
    private val clientService: ClientService,
    private val eventService: EventService,
) {

    fun listAllRows(): List<InvoiceHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "createdAt")
        return invoiceHistoryRepository.findAll(sort).map { h ->
            val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(h.id!!)
            val chassisJoined = lines.joinToString(";") { it.chassis.trim() }.trim()
            val amountsJoined = lines.joinToString(";") {
                it.lineAmount?.trim().orEmpty()
            }
            InvoiceHistoryRowDto(
                invoiceNumber = h.invoiceNumber,
                vessel = h.vessel,
                clientName = h.clientName,
                shippingDate = h.shippingDate?.toString(),
                pol = h.pol,
                pod = h.pod,
                lcNo = h.lcNo,
                priceType = h.priceType,
                bank = h.bank,
                messages = h.messages,
                chassis = chassisJoined.ifEmpty { null },
                totalAmount = amountsJoined.ifEmpty { null },
                createdAt = h.createdAt.toString(),
            )
        }
    }

    @Transactional
    fun confirmAndDownload(request: InvoiceConfirmAndDownloadRequest): InvoiceConfirmResult {
        val pdf = request.pdf
        val inv = pdf.invoiceNumber.trim()
        if (inv.isEmpty()) {
            throw IllegalArgumentException("Invoice number is required")
        }

        persistInvoiceHistory(request)
        val ledger = applyLedgerForInvoice(request, pdf)
        return InvoiceConfirmResult(
            pdfBytes = pdfService.generateInvoicePdf(pdf),
            ledger = ledger,
        )
    }

    /**
     * Saves invoice_history + lines, marks purchases as invoice_confirmed, syncs ledger.
     * Does NOT generate a PDF — intended for batch creation from shipping history.
     */
    @Transactional
    fun saveOnly(request: InvoiceConfirmAndDownloadRequest): InvoiceLedgerResult {
        val pdf = request.pdf
        val inv = pdf.invoiceNumber.trim()
        if (inv.isEmpty()) {
            throw IllegalArgumentException("Invoice number is required")
        }

        persistInvoiceHistory(request)
        return applyLedgerForInvoice(request, pdf)
    }

    private fun persistInvoiceHistory(request: InvoiceConfirmAndDownloadRequest) {
        val pdf = request.pdf
        val inv = pdf.invoiceNumber.trim()

        val shipDate: LocalDate? = request.shippingDateIso?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            try {
                LocalDate.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
        }

        val existing = invoiceHistoryRepository.findByInvoiceNumber(inv).orElse(null)
        val createdAt = existing?.createdAt ?: LocalDateTime.now()

        val header = InvoiceHistory(
            id = existing?.id,
            invoiceNumber = inv,
            vessel = pdf.vessel.trim().takeIf { it.isNotEmpty() },
            clientName = pdf.clientName.trim().takeIf { it.isNotEmpty() },
            shippingDate = shipDate,
            pol = pdf.from.trim().takeIf { it.isNotEmpty() },
            pod = pdf.to.trim().takeIf { it.isNotEmpty() },
            lcNo = pdf.lcNumber?.trim()?.takeIf { it.isNotEmpty() },
            priceType = pdf.priceType.trim().takeIf { it.isNotEmpty() },
            bank = pdf.bankAccount?.trim()?.takeIf { it.isNotEmpty() },
            messages = pdf.message?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = createdAt,
        )
        val saved = invoiceHistoryRepository.save(header)
        val headerId = saved.id!!

        invoiceHistoryLineRepository.deleteByInvoiceHistoryId(headerId)

        val chassisTokens = request.chassisJoined.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val items = pdf.items
        val lineIndices = if (request.purchaseIds.isNotEmpty()) {
            request.purchaseIds.indices
        } else {
            chassisTokens.indices
        }
        for (i in lineIndices) {
            val ch = chassisTokens.getOrNull(i) ?: continue
            if (ch.isBlank()) continue
            val amt = items.getOrNull(i)?.amount?.trim()?.takeIf { it.isNotEmpty() }
            invoiceHistoryLineRepository.save(
                InvoiceHistoryLine(
                    invoiceHistoryId = headerId,
                    chassis = ch,
                    lineAmount = amt,
                    sortOrder = i,
                ),
            )
        }

        if (request.purchaseIds.isNotEmpty()) {
            purchaseService.markPurchasesAsInvoiceConfirmed(request.purchaseIds)
        } else if (chassisTokens.isNotEmpty()) {
            purchaseService.markPurchasesAsInvoiceConfirmedByChassis(chassisTokens)
        }
    }

    private fun applyLedgerForInvoice(
        request: InvoiceConfirmAndDownloadRequest,
        pdf: InvoicePdfRequest,
    ): InvoiceLedgerResult {
        val inv = pdf.invoiceNumber.trim()
        val resolved = resolveClientForLedger(request, pdf)
        if (resolved.clientId == null) {
            val warning = resolved.warning ?: "Could not resolve client for ledger."
            Logger.warn("Invoice $inv: $warning")
            return InvoiceLedgerResult(warning = warning)
        }
        val clientId = resolved.clientId
        val clientName = pdf.clientName.trim()
        if (request.purchaseIds.isNotEmpty()) {
            purchaseService.linkPurchasesToClient(request.purchaseIds, clientId, clientName)
        }
        val ledgerDate = resolveLedgerDate(request, pdf)
        val grandTotal = computeInvoiceGrandTotal(pdf)
        val vesselForLedger = pdf.vessel.trim().takeIf { it.isNotEmpty() }
        val sync = eventService.syncInvoiceLedger(
            clientId = clientId,
            invoiceNumber = inv,
            eventDate = ledgerDate,
            transactionPriceTotal = grandTotal,
            lineCount = pdf.items.size,
            vessel = vesselForLedger,
        )
        val info = if (resolved.clientCreated) {
            "Client \"$clientName\" was added to Client Transactions."
        } else {
            null
        }
        return sync.copy(
            clientId = clientId,
            clientCreated = resolved.clientCreated,
            info = info,
        )
    }

    fun previewLedgerClient(clientName: String, purchaseIds: List<Long> = emptyList()): Map<String, Any?> {
        val purchaseClientIds = purchaseIds.mapNotNull { purchaseService.getPurchaseById(it)?.clientId }
        val preview = clientService.previewClientNameForLedger(clientName, purchaseClientIds)
        return mapOf(
            "clientId" to preview.clientId,
            "ledgerResolvable" to preview.ledgerResolvable,
            "willCreateClient" to preview.willCreateClient,
            "warning" to preview.warning,
            "info" to if (preview.willCreateClient) {
                "Client \"${clientName.trim()}\" will be added to Client Transactions when you save this invoice."
            } else {
                null
            },
        )
    }

    private data class LedgerClientResolve(
        val clientId: Long?,
        val clientCreated: Boolean = false,
        val warning: String? = null,
    )

    private fun resolveClientForLedger(
        request: InvoiceConfirmAndDownloadRequest,
        pdf: InvoicePdfRequest,
    ): LedgerClientResolve {
        val fromPurchases = request.purchaseIds.mapNotNull { purchaseService.getPurchaseById(it)?.clientId }.distinct()
        when {
            fromPurchases.size > 1 -> {
                Logger.warn(
                    "Invoice ${pdf.invoiceNumber.trim()}: purchases reference multiple client IDs $fromPurchases; skip ledger",
                )
                return LedgerClientResolve(
                    clientId = null,
                    warning = "Purchases reference multiple clients; ledger entry was not posted. Link purchases to one client.",
                )
            }
            fromPurchases.size == 1 -> return LedgerClientResolve(clientId = fromPurchases.first())
        }
        return when (val resolution = clientService.resolveClientNameForLedger(pdf.clientName)) {
            is ClientNameLedgerResolution.Ok -> LedgerClientResolve(
                clientId = resolution.clientId,
                clientCreated = resolution.created,
            )
            is ClientNameLedgerResolution.Skipped -> LedgerClientResolve(
                clientId = null,
                warning = resolution.warning,
            )
        }
    }

    private fun resolveLedgerDate(request: InvoiceConfirmAndDownloadRequest, pdf: InvoicePdfRequest): LocalDate {
        val shipDate: LocalDate? = request.shippingDateIso?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            try {
                LocalDate.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        return shipDate
            ?: parseFlexibleLocalDate(pdf.shippingDate.trim().takeIf { it.isNotEmpty() })
            ?: LocalDate.now()
    }

    private fun computeInvoiceGrandTotal(pdf: InvoicePdfRequest): Double {
        val lineTotal = pdf.items.sumOf { parseInvoiceYenAmount(it.amount) ?: 0.0 }
        return if (lineTotal > 0.0) {
            lineTotal
        } else {
            parseInvoiceYenAmount(pdf.totalAmount) ?: 0.0
        }
    }

    private fun parseFlexibleLocalDate(s: String?): LocalDate? {
        if (s.isNullOrBlank()) return null
        return try {
            LocalDate.parse(s)
        } catch (_: DateTimeParseException) {
            try {
                LocalDate.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy/M/d"))
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    /**
     * Deletes invoice_history headers (and lines) where **every** line chassis is contained in
     * [chassisCoverTokens] (trimmed, case-insensitive). Invoices that list any chassis **not**
     * in this set are left intact. Delegates to [deleteByInvoiceNumbers] for consistent purchase unmark rules.
     */
    @Transactional
    fun deleteInvoicesFullyCoveredByChassis(chassisCoverTokens: Collection<String>): Int {
        if (chassisCoverTokens.isEmpty()) return 0
        val cover = chassisCoverTokens
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (cover.isEmpty()) return 0

        val sort = Sort.by(Sort.Direction.ASC, "id")
        val headers = invoiceHistoryRepository.findAll(sort)
        val invoiceNumbersToDelete = mutableListOf<String>()
        for (h in headers) {
            val hid = h.id ?: continue
            val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(hid)
            val onInvoice = lines
                .map { it.chassis.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .toSet()
            if (onInvoice.isEmpty()) continue
            if (onInvoice.all { it in cover }) {
                invoiceNumbersToDelete.add(h.invoiceNumber)
            }
        }
        if (invoiceNumbersToDelete.isEmpty()) return 0
        return deleteByInvoiceNumbers(invoiceNumbersToDelete)
    }

    /**
     * Regenerates invoice PDF from saved invoice_history (no DB writes, no ledger).
     * Line descriptions are built from matching purchases when available.
     */
    fun generatePdfForInvoiceNumber(invoiceNumber: String): ByteArray {
        val inv = invoiceNumber.trim()
        if (inv.isEmpty()) {
            throw IllegalArgumentException("Invoice number is required")
        }
        val header = invoiceHistoryRepository.findByInvoiceNumber(inv).orElseThrow {
            IllegalArgumentException("Invoice not found: $inv")
        }
        val headerId = header.id ?: throw IllegalStateException("Invoice header has no id")
        val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(headerId)
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Invoice has no line items: $inv")
        }

        val items = mutableListOf<InvoiceItem>()
        var totalAmount = 0.0
        var unit = 1
        for (line in lines) {
            val chassis = line.chassis.trim()
            if (chassis.isEmpty()) continue
            val purchase = purchaseService.getPurchaseByChassis(chassis)
            val description = if (purchase != null) {
                buildInvoicePdfDescription(purchase)
            } else {
                chassis
            }
            val formattedAmount = normalizeInvoiceYenDisplay(line.lineAmount)
            val parsed = parseInvoiceYenAmount(formattedAmount) ?: 0.0
            totalAmount += parsed
            items.add(
                InvoiceItem(
                    unit = unit,
                    description = description,
                    amount = formattedAmount,
                ),
            )
            unit++
        }
        if (items.isEmpty()) {
            throw IllegalArgumentException("Invoice has no valid line items: $inv")
        }

        val pdf = InvoicePdfRequest(
            invoiceNumber = inv,
            invoiceDate = LocalDate.now().toString(),
            lcNumber = header.lcNo?.trim()?.takeIf { it.isNotEmpty() },
            clientName = header.clientName?.trim().orEmpty().ifEmpty { "-" },
            clientAddress = null,
            vessel = header.vessel?.trim().orEmpty().ifEmpty { "-" },
            shippingDate = formatShippingDateForPdf(header.shippingDate),
            from = header.pol?.trim().orEmpty().ifEmpty { "-" },
            to = header.pod?.trim().orEmpty().ifEmpty { "-" },
            priceType = header.priceType?.trim()?.takeIf { it.isNotEmpty() } ?: "C&F",
            items = items,
            totalAmount = formatInvoiceYenInt(totalAmount),
            bankAccount = header.bank?.trim()?.takeIf { it.isNotEmpty() },
            message = header.messages?.trim()?.takeIf { it.isNotEmpty() },
        )
        return pdfService.generateInvoicePdf(pdf)
    }

    private fun buildInvoicePdfDescription(purchase: Purchase): String {
        val chassis = purchase.chassis.trim()
        val carName = purchase.carName?.trim().orEmpty()
        val grade = purchase.grade?.trim().orEmpty()
        val carModelYear = purchase.carModelYear?.trim().orEmpty()
        val shift = purchase.shift?.trim().orEmpty()
        val door = purchase.door?.trim().orEmpty()
        val seat = purchase.seat?.trim().orEmpty()
        val cc = purchase.cc?.toString()?.trim().orEmpty()
        val color = purchase.color?.trim().orEmpty()
        val distance = purchase.distance?.trim().orEmpty()
        val fuel = purchase.fuel?.trim().orEmpty()

        val line1 = buildString {
            if (chassis.isNotEmpty()) append(chassis)
            if (carName.isNotEmpty()) {
                if (isNotEmpty()) append("   ")
                append(carName)
            }
            if (grade.isNotEmpty()) {
                if (isNotEmpty()) append("   ")
                append(grade)
            }
            if (carModelYear.isNotEmpty()) {
                if (isNotEmpty()) append("     ")
                append(carModelYear)
            }
            if (shift.isNotEmpty()) {
                if (isNotEmpty()) append("     ")
                append(shift)
            }
            val doorSeatParts = mutableListOf<String>()
            if (door.isNotEmpty()) doorSeatParts.add("$door DOOR")
            if (seat.isNotEmpty()) doorSeatParts.add("$seat SEAT")
            if (doorSeatParts.isNotEmpty()) {
                if (isNotEmpty()) append(", ")
                append(doorSeatParts.joinToString(", "))
            }
        }

        val line2 = buildString {
            if (cc.isNotEmpty()) append("${cc}CC")
            if (color.isNotEmpty()) {
                if (isNotEmpty()) append("       ")
                append(color)
            }
            if (distance.isNotEmpty()) {
                if (isNotEmpty()) append("     ")
                append(distance)
            }
            if (fuel.isNotEmpty()) {
                if (isNotEmpty()) append("     ")
                append(fuel)
            }
        }

        return if (line2.isNotEmpty()) "$line1\n$line2" else line1
    }

    private fun formatShippingDateForPdf(date: LocalDate?): String {
        if (date == null) return "-"
        val months = listOf("", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
        val monthName = months.getOrElse(date.monthValue) { date.monthValue.toString() }
        return "${date.dayOfMonth}.$monthName.${date.year}"
    }

    private fun normalizeInvoiceYenDisplay(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return "¥0"
        if (trimmed.startsWith("¥")) return trimmed
        val parsed = parseInvoiceYenAmount(trimmed)
        return if (parsed != null) formatInvoiceYenInt(parsed) else trimmed
    }

    private fun formatInvoiceYenInt(amount: Double): String {
        val amountInt = amount.toInt()
        val amountStr = amountInt.toString()
        val reversed = amountStr.reversed()
        val chunked = reversed.chunked(3)
        val joined = chunked.joinToString(",")
        return "¥${joined.reversed()}"
    }

    /** Strip ¥ / commas like other transaction parsers; returns null if not parseable. */
    private fun parseInvoiceYenAmount(amountStr: String?): Double? {
        if (amountStr.isNullOrBlank()) return null
        return try {
            amountStr
                .replace("¥", "")
                .replace("$", "")
                .replace(",", "")
                .trim()
                .let { raw ->
                    val cleaned = raw.replace(Regex("[^0-9.-]"), "")
                    cleaned.toDouble()
                }
        } catch (_: Exception) {
            null
        }
    }

    @Transactional
    fun deleteByInvoiceNumbers(invoiceNumbers: List<String>): Int {
        if (invoiceNumbers.isEmpty()) return 0
        val rows = invoiceHistoryRepository.findAllByInvoiceNumberIn(invoiceNumbers)
        val chassisAffected = linkedSetOf<String>()
        for (row in rows) {
            val hid = row.id ?: continue
            val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(hid)
            reverseLedgerForDeletedInvoice(row, lines)
            for (line in lines) {
                val c = line.chassis.trim()
                if (c.isNotEmpty()) chassisAffected.add(c)
            }
        }
        for (row in rows) {
            val hid = row.id ?: continue
            invoiceHistoryLineRepository.deleteByInvoiceHistoryId(hid)
        }
        invoiceHistoryRepository.deleteAll(rows)

        val chassisToUnmark = chassisAffected.filter { chassis ->
            invoiceHistoryLineRepository.countByNormalizedChassis(chassis) == 0L
        }
        if (chassisToUnmark.isNotEmpty()) {
            purchaseService.unmarkInvoiceConfirmedForChassis(chassisToUnmark)
        }

        return rows.size
    }

    private fun reverseLedgerForDeletedInvoice(header: InvoiceHistory, lines: List<InvoiceHistoryLine>) {
        val ledgerDate = header.shippingDate ?: LocalDate.now()
        val reversedCount = eventService.reverseOpenInvoiceLedgersForInvoice(
            invoiceNumber = header.invoiceNumber,
            eventDate = ledgerDate,
            vessel = header.vessel,
        )
        if (reversedCount > 0) return
        val clientId = resolveClientIdForDeletedInvoice(header, lines) ?: return
        eventService.reverseActiveInvoiceLedger(
            clientId = clientId,
            invoiceNumber = header.invoiceNumber,
            eventDate = ledgerDate,
            vessel = header.vessel,
        )
    }

    private fun resolveClientIdForDeletedInvoice(header: InvoiceHistory, lines: List<InvoiceHistoryLine>): Long? {
        val fromPurchases = lines
            .mapNotNull { purchaseService.getPurchaseByChassis(it.chassis.trim())?.clientId }
            .distinct()
        when {
            fromPurchases.size == 1 -> return fromPurchases.first()
            fromPurchases.size > 1 -> {
                Logger.warn("Invoice ${header.invoiceNumber}: multiple client IDs on delete; skip ledger reversal")
                return null
            }
        }
        val name = header.clientName?.trim().orEmpty()
        if (name.isEmpty()) return null
        val matches = clientRepository.findByClientNameIgnoreCase(name)
        return when {
            matches.size == 1 -> matches.first().id
            else -> null
        }
    }
}
