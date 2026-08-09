package com.automan.backend.service

import com.automan.backend.dto.ClientCreditLimitAssessment
import com.automan.backend.dto.ClientNameLedgerResolution
import com.automan.backend.dto.CreditLimitStatus
import com.automan.backend.dto.InvoiceBatchDeleteResult
import com.automan.backend.dto.InvoiceConfirmAndDownloadRequest
import com.automan.backend.dto.InvoiceConfirmResult
import com.automan.backend.dto.InvoiceHistoryPageResponse
import com.automan.backend.dto.InvoiceHistoryRowDto
import com.automan.backend.dto.InvoiceItem
import com.automan.backend.dto.InvoiceLedgerResult
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.model.InvoiceHistory
import com.automan.backend.model.InvoiceHistoryLine
import com.automan.backend.model.Purchase
import com.automan.backend.repository.ClientMapRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.util.Logger
import org.springframework.data.domain.PageRequest
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
    private val shippingHistoryService: ShippingHistoryService,
    private val bookingMappingService: BookingMappingService,
    private val clientMapRepository: ClientMapRepository,
) {

    fun listAllRows(): List<InvoiceHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "createdAt")
        return invoiceHistoryRepository.findAll(sort).map { toRowDto(it) }
    }

    fun listRowsPage(page: Int, rawSize: Int): InvoiceHistoryPageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pg = invoiceHistoryRepository.findAll(pageable)
        return InvoiceHistoryPageResponse(
            content = pg.content.map { toRowDto(it) },
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    fun searchRowsPage(rawQuery: String, page: Int, rawSize: Int): InvoiceHistoryPageResponse {
        val q = sanitizeHistorySearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        val pageable = PageRequest.of(pageIdx, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        val pg = invoiceHistoryRepository.searchKeyFields(q, pageable)
        return InvoiceHistoryPageResponse(
            content = pg.content.map { toRowDto(it) },
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    private fun toRowDto(h: InvoiceHistory): InvoiceHistoryRowDto {
        val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(h.id!!)
        val chassisJoined = lines.joinToString(";") { it.chassis.trim() }.trim()
        val amountsJoined = lines.joinToString(";") {
            it.lineAmount?.trim().orEmpty()
        }
        return InvoiceHistoryRowDto(
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

    private fun sanitizeHistorySearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    @Transactional
    fun confirmAndDownload(request: InvoiceConfirmAndDownloadRequest): InvoiceConfirmResult {
        val chassisTokens = request.chassisJoined.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val enrichedPdf = finalizeInvoicePdfRequest(request.pdf, chassisTokens)
        val enrichedRequest = request.copy(pdf = enrichedPdf)
        val inv = enrichedPdf.invoiceNumber.trim()
        if (inv.isEmpty()) {
            throw IllegalArgumentException("Invoice number is required")
        }

        val creditAssessment = enforceCreditBeforeSave(enrichedRequest, enrichedPdf)
        val priorLineTotal = computePersistedLineTotal(inv)
        persistInvoiceHistory(enrichedRequest)
        val ledger = applyLedgerForInvoice(enrichedRequest, enrichedPdf, creditAssessment, priorLineTotal)
        return InvoiceConfirmResult(
            pdfBytes = pdfService.generateInvoicePdf(enrichedPdf),
            ledger = ledger,
        )
    }

    /**
     * Saves invoice_history + lines, marks purchases as invoice_confirmed, syncs ledger.
     * Does NOT generate a PDF — intended for batch creation from shipping history.
     */
    @Transactional
    fun saveOnly(request: InvoiceConfirmAndDownloadRequest): InvoiceLedgerResult {
        val chassisTokens = request.chassisJoined.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        val enrichedPdf = finalizeInvoicePdfRequest(request.pdf, chassisTokens)
        val enrichedRequest = request.copy(pdf = enrichedPdf)
        val inv = enrichedPdf.invoiceNumber.trim()
        if (inv.isEmpty()) {
            throw IllegalArgumentException("Invoice number is required")
        }

        val creditAssessment = enforceCreditBeforeSave(enrichedRequest, enrichedPdf)
        val priorLineTotal = computePersistedLineTotal(inv)
        persistInvoiceHistory(enrichedRequest)
        return applyLedgerForInvoice(enrichedRequest, enrichedPdf, creditAssessment, priorLineTotal)
    }

    /**
     * Fills shipping_history extras, Client Map consignee, and Consignee Map address onto an
     * [InvoicePdfRequest] before save or PDF generation.
     */
    fun finalizeInvoicePdfRequest(
        pdf: InvoicePdfRequest,
        chassisTokens: Collection<String> = emptyList(),
    ): InvoicePdfRequest {
        val (fromShipping, country) = shippingHistoryService.enrichInvoicePdfFromShippingHistory(pdf, chassisTokens)
        val clientMapConsignee = firstClientMapConsigneeToken(fromShipping.clientName)
        val consigneeForPdf = clientMapConsignee
            ?: fromShipping.consignee?.trim()?.takeIf { it.isNotEmpty() }
        val consigneeChanged = clientMapConsignee != null &&
            !clientMapConsignee.equals(fromShipping.consignee?.trim().orEmpty(), ignoreCase = true)
        val address = when {
            consigneeForPdf.isNullOrBlank() -> null
            // Prefer address matching Client Map consignee when that name was applied.
            consigneeChanged || fromShipping.consigneeAddress.isNullOrBlank() ->
                bookingMappingService.resolveConsigneeAddress(
                    consigneeForPdf,
                    country,
                    fromShipping.to.takeIf { it.isNotBlank() && it != "-" },
                ).takeIf { it.isNotEmpty() }
                    ?: fromShipping.consigneeAddress?.trim()?.takeIf { it.isNotEmpty() }
            else -> fromShipping.consigneeAddress?.trim()?.takeIf { it.isNotEmpty() }
                ?: bookingMappingService.resolveConsigneeAddress(
                    consigneeForPdf,
                    country,
                    fromShipping.to.takeIf { it.isNotBlank() && it != "-" },
                ).takeIf { it.isNotEmpty() }
        }
        return fromShipping.copy(
            consignee = consigneeForPdf,
            consigneeAddress = address,
        )
    }

    /** First non-empty Client Map consignee token (comma / semicolon / newline lists). */
    private fun firstClientMapConsigneeToken(clientName: String?): String? {
        val name = clientName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val row = clientMapRepository.findByClientNameIgnoreCase(name) ?: return null
        val raw = row.consignee?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Regex("""[,;\n]+""")
            .split(raw)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
    }

    private fun computePersistedLineTotal(invoiceNumber: String): Double? {
        val header = invoiceHistoryRepository.findByInvoiceNumber(invoiceNumber.trim()).orElse(null) ?: return null
        val headerId = header.id ?: return null
        val total = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(headerId)
            .sumOf { parseInvoiceYenAmount(it.lineAmount) ?: 0.0 }
        return total.takeIf { it > 0.0 }
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
            bookingNo = pdf.bookingNo?.trim()?.takeIf { it.isNotEmpty() },
            carrier = pdf.carrier?.trim()?.takeIf { it.isNotEmpty() },
            clientName = pdf.clientName.trim().takeIf { it.isNotEmpty() },
            consignee = pdf.consignee?.trim()?.takeIf { it.isNotEmpty() },
            notifyParty = pdf.notifyParty?.trim()?.takeIf { it.isNotEmpty() },
            shippingDate = shipDate,
            cyCutDate = parseFlexibleLocalDate(pdf.cyCutDate?.trim()?.take(10)),
            eta = parseFlexibleLocalDate(pdf.eta?.trim()?.take(10)),
            pol = pdf.from.trim().takeIf { it.isNotEmpty() },
            pod = pdf.to.trim().takeIf { it.isNotEmpty() },
            finalDestination = pdf.finalDestination?.trim()?.takeIf { it.isNotEmpty() },
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

    /**
     * Phase 3: block or warn before persisting invoice when charge would exceed credit limit.
     * Does not auto-create clients (new buyers have no limit yet).
     */
    private fun enforceCreditBeforeSave(
        request: InvoiceConfirmAndDownloadRequest,
        pdf: InvoicePdfRequest,
    ): ClientCreditLimitAssessment? {
        val inv = pdf.invoiceNumber.trim()
        val grandTotal = computeInvoiceGrandTotal(pdf)
        if (grandTotal <= 0.0) return null
        val clientId = resolveClientIdForCreditCheck(request, pdf) ?: return null
        clientService.enforceInvoiceCreditLimit(clientId, inv, grandTotal)
        return clientService.assessCreditForInvoiceCharge(clientId, inv, grandTotal)
    }

    private fun resolveClientIdForCreditCheck(
        request: InvoiceConfirmAndDownloadRequest,
        pdf: InvoicePdfRequest,
    ): Long? {
        val name = pdf.clientName.trim()
        if (name.isNotEmpty()) {
            val matches = clientRepository.findByClientNameIgnoreCase(name)
            return when {
                matches.size == 1 -> matches.first().id
                else -> null
            }
        }
        val fromPurchases = request.purchaseIds.mapNotNull { purchaseService.getPurchaseById(it)?.clientId }.distinct()
        return when {
            fromPurchases.size > 1 -> null
            fromPurchases.size == 1 -> fromPurchases.first()
            else -> null
        }
    }

    private fun mergeCreditIntoLedgerResult(
        ledger: InvoiceLedgerResult,
        credit: ClientCreditLimitAssessment?,
    ): InvoiceLedgerResult {
        if (credit == null) return ledger
        val creditWarning = when (credit.status) {
            CreditLimitStatus.NEAR_LIMIT, CreditLimitStatus.OVER_LIMIT -> credit.message
            else -> null
        }
        val mergedWarning = listOfNotNull(ledger.warning, creditWarning).joinToString(" ").ifEmpty { null }
        return ledger.copy(
            warning = mergedWarning,
            creditLimit = credit,
        )
    }

    private fun applyLedgerForInvoice(
        request: InvoiceConfirmAndDownloadRequest,
        pdf: InvoicePdfRequest,
        creditAssessment: ClientCreditLimitAssessment? = null,
        priorLineTotal: Double? = null,
    ): InvoiceLedgerResult {
        val inv = pdf.invoiceNumber.trim()
        val resolved = resolveClientForLedger(request, pdf)
        if (resolved.clientId == null) {
            val warning = resolved.warning ?: "Could not resolve client for ledger."
            Logger.warn("Invoice $inv: $warning")
            return mergeCreditIntoLedgerResult(InvoiceLedgerResult(warning = warning), creditAssessment)
        }
        val clientId = resolved.clientId
        val clientName = pdf.clientName.trim()
        if (request.purchaseIds.isNotEmpty()) {
            purchaseService.linkPurchasesToClient(request.purchaseIds, clientId, clientName)
        }
        val ledgerDate = resolveLedgerDate(request, pdf)
        val grandTotal = computeInvoiceGrandTotal(pdf)
        if (grandTotal <= 0.0) {
            return mergeCreditIntoLedgerResult(
                InvoiceLedgerResult(
                    clientId = clientId,
                    clientCreated = resolved.clientCreated,
                    warning = "Invoice total is zero; ledger entry was not posted.",
                ),
                creditAssessment,
            )
        }
        val vesselForLedger = pdf.vessel.trim().takeIf { it.isNotEmpty() }

        // Metadata-only update (message, bank, etc.): skip ledger when amount unchanged and already balanced.
        if (
            priorLineTotal != null &&
            kotlin.math.abs(priorLineTotal - grandTotal) < 0.01 &&
            eventService.isInvoiceLedgerBalancedTo(clientId, inv, grandTotal)
        ) {
            return mergeCreditIntoLedgerResult(
                InvoiceLedgerResult(
                    clientId = clientId,
                    clientCreated = resolved.clientCreated,
                ),
                creditAssessment,
            )
        }

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
        return mergeCreditIntoLedgerResult(
            sync.copy(
                clientId = clientId,
                clientCreated = resolved.clientCreated,
                info = info,
            ),
            creditAssessment,
        )
    }

    fun previewLedgerClient(
        clientName: String,
        purchaseIds: List<Long> = emptyList(),
        invoiceNumber: String? = null,
        invoiceAmount: Double? = null,
    ): Map<String, Any?> {
        val purchaseClientIds = purchaseIds.mapNotNull { purchaseService.getPurchaseById(it)?.clientId }
        val preview = clientService.previewClientNameForLedger(clientName, purchaseClientIds)
        val pdf = InvoicePdfRequest(
            invoiceNumber = invoiceNumber?.trim().orEmpty(),
            invoiceDate = LocalDate.now().toString(),
            lcNumber = null,
            clientName = clientName.trim(),
            clientAddress = null,
            vessel = "",
            shippingDate = "",
            from = "",
            to = "",
            priceType = "C&F",
            items = emptyList(),
            totalAmount = "",
            bankAccount = null,
            message = null,
        )
        val request = InvoiceConfirmAndDownloadRequest(
            purchaseIds = purchaseIds,
            chassisJoined = "",
            pdf = pdf,
        )
        val checkClientId = preview.clientId
            ?: resolveClientIdForCreditCheck(request, pdf)

        val credit = if (
            checkClientId != null &&
            invoiceAmount != null &&
            invoiceAmount > 0.0 &&
            !invoiceNumber.isNullOrBlank()
        ) {
            clientService.assessCreditForInvoiceCharge(
                checkClientId,
                invoiceNumber.trim(),
                invoiceAmount,
            )
        } else {
            null
        }

        val creditWarning = credit?.takeIf {
            it.status == CreditLimitStatus.NEAR_LIMIT || it.status == CreditLimitStatus.OVER_LIMIT
        }?.message

        return buildMap {
            put("clientId", preview.clientId ?: checkClientId)
            put("ledgerResolvable", preview.ledgerResolvable)
            put("willCreateClient", preview.willCreateClient)
            put(
                "warning",
                listOfNotNull(preview.warning, creditWarning).joinToString(" ").ifEmpty { null },
            )
            put(
                "info",
                if (preview.willCreateClient) {
                    "Client \"${clientName.trim()}\" will be added to Client Transactions when you save this invoice."
                } else {
                    null
                },
            )
            credit?.toResponseMap()?.forEach { (k, v) -> put(k, v) }
        }
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
        if (fromPurchases.size > 1) {
            Logger.warn(
                "Invoice ${pdf.invoiceNumber.trim()}: purchases reference multiple client IDs $fromPurchases; skip ledger",
            )
            return LedgerClientResolve(
                clientId = null,
                warning = "Purchases reference multiple clients; ledger entry was not posted. Link purchases to one client.",
            )
        }

        val name = pdf.clientName.trim()
        if (name.isNotEmpty()) {
            return when (val resolution = clientService.resolveClientNameForLedger(name)) {
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

        if (fromPurchases.size == 1) {
            return LedgerClientResolve(clientId = fromPurchases.first())
        }

        return LedgerClientResolve(
            clientId = null,
            warning = "Invoice has no client name; ledger entry was not posted.",
        )
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
        return deleteByInvoiceNumbers(invoiceNumbersToDelete).deleted
    }

    fun clientNameForInvoiceNumber(invoiceNumber: String): String? {
        val header = invoiceHistoryRepository.findByInvoiceNumber(invoiceNumber.trim()).orElse(null) ?: return null
        return header.clientName?.trim()?.takeIf { it.isNotEmpty() }
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
                    maker = purchase?.brand?.trim()?.takeIf { it.isNotEmpty() },
                    model = purchase?.carName?.trim()?.takeIf { it.isNotEmpty() },
                    chassisNo = chassis.takeIf { it.isNotEmpty() },
                    year = purchase?.manufactureYear?.trim()?.takeIf { it.isNotEmpty() }
                        ?: purchase?.carModelYear?.trim()?.takeIf { it.isNotEmpty() },
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
            consignee = header.consignee?.trim()?.takeIf { it.isNotEmpty() },
            notifyParty = header.notifyParty?.trim()?.takeIf { it.isNotEmpty() },
            bookingNo = header.bookingNo?.trim()?.takeIf { it.isNotEmpty() },
            carrier = header.carrier?.trim()?.takeIf { it.isNotEmpty() },
            cyCutDate = header.cyCutDate?.toString(),
            eta = header.eta?.toString(),
            finalDestination = header.finalDestination?.trim()?.takeIf { it.isNotEmpty() },
        )
        val chassisTokens = lines.map { it.chassis.trim() }.filter { it.isNotEmpty() }
        val enriched = finalizeInvoicePdfRequest(pdf, chassisTokens)
        return pdfService.generateInvoicePdf(enriched)
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
    fun deleteByInvoiceNumbers(invoiceNumbers: List<String>): InvoiceBatchDeleteResult {
        if (invoiceNumbers.isEmpty()) return InvoiceBatchDeleteResult(deleted = 0)
        val rows = invoiceHistoryRepository.findAllByInvoiceNumberIn(invoiceNumbers)
        val chassisAffected = linkedSetOf<String>()
        var ledgerReversed = 0
        val ledgerWarnings = mutableListOf<String>()
        for (row in rows) {
            val hid = row.id ?: continue
            val lines = invoiceHistoryLineRepository.findByInvoiceHistoryIdOrderBySortOrderAsc(hid)
            val slice = reverseLedgerForDeletedInvoice(row, lines)
            ledgerReversed += slice.reversedCount
            ledgerWarnings.addAll(slice.warnings)
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

        return InvoiceBatchDeleteResult(
            deleted = rows.size,
            ledgerReversed = ledgerReversed,
            ledgerWarnings = ledgerWarnings.distinct(),
        )
    }

    private data class InvoiceLedgerDeleteSlice(
        val reversedCount: Int = 0,
        val warnings: List<String> = emptyList(),
    )

    private fun reverseLedgerForDeletedInvoice(
        header: InvoiceHistory,
        lines: List<InvoiceHistoryLine>,
    ): InvoiceLedgerDeleteSlice {
        val clientId = resolveClientIdForDeletedInvoice(header, lines)
        if (clientId == null) {
            val name = header.clientName?.trim().orEmpty().ifEmpty { "unknown" }
            return InvoiceLedgerDeleteSlice(
                warnings = listOf(
                    "Invoice ${header.invoiceNumber}: could not resolve client \"$name\" for ledger reversal.",
                ),
            )
        }
        val ledgerDate = header.shippingDate ?: LocalDate.now()
        val numbers = collectInvoiceNumbersToReverseOnDelete(header)
        if (numbers.isEmpty()) {
            return InvoiceLedgerDeleteSlice(
                warnings = listOf(
                    "Invoice ${header.invoiceNumber}: no invoice numbers matched for ledger reversal.",
                ),
            )
        }
        var reversedCount = 0
        for (num in numbers) {
            val reversed = eventService.reverseActiveInvoiceLedger(
                clientId = clientId,
                invoiceNumber = num,
                eventDate = ledgerDate,
                vessel = header.vessel,
            )
            if (reversed != null) reversedCount++
        }
        if (reversedCount == 0) {
            return InvoiceLedgerDeleteSlice(
                warnings = listOf(
                    "Invoice ${header.invoiceNumber}: no open invoice charge was found on the client ledger to reverse.",
                ),
            )
        }
        return InvoiceLedgerDeleteSlice(reversedCount = reversedCount)
    }

    private fun collectInvoiceNumbersToReverseOnDelete(
        header: InvoiceHistory,
    ): Set<String> {
        val numbers = linkedSetOf<String>()
        val headerNum = header.invoiceNumber.trim()
        if (headerNum.isNotEmpty()) numbers.add(headerNum)
        return numbers
    }

    private fun resolveClientIdForDeletedInvoice(header: InvoiceHistory, lines: List<InvoiceHistoryLine>): Long? {
        val name = header.clientName?.trim().orEmpty()
        if (name.isNotEmpty()) {
            val byName = clientRepository.findByClientNameIgnoreCase(name)
            when {
                byName.size == 1 -> return byName.first().id
                byName.size > 1 -> {
                    Logger.warn(
                        "Invoice ${header.invoiceNumber}: multiple clients named \"$name\"; skip ledger reversal",
                    )
                    return null
                }
            }
        }

        val fromPurchases = lines
            .mapNotNull { purchaseService.getPurchaseByChassis(it.chassis.trim())?.clientId }
            .distinct()
        when {
            fromPurchases.size == 1 -> return fromPurchases.first()
            fromPurchases.size > 1 -> {
                Logger.warn(
                    "Invoice ${header.invoiceNumber}: purchases reference multiple client IDs; skip ledger reversal",
                )
                return null
            }
        }

        if (name.isEmpty()) {
            Logger.warn(
                "Invoice ${header.invoiceNumber}: no client name and no purchase client link; skip ledger reversal",
            )
        } else {
            Logger.warn(
                "Invoice ${header.invoiceNumber}: client \"$name\" not found in Client Transactions; skip ledger reversal",
            )
        }
        return null
    }
}
