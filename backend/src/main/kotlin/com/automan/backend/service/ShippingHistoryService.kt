package com.automan.backend.service

import com.automan.backend.dto.ShippingHistoryBatchRequest
import com.automan.backend.dto.ShippingHistoryInvoiceHeaderDto
import com.automan.backend.dto.ShippingHistoryInvoiceLineDto
import com.automan.backend.dto.ShippingHistoryInvoiceSliceDto
import com.automan.backend.dto.ShippingHistoryRowDto
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

@Service
class ShippingHistoryService(
    private val shippingHistoryRepository: ShippingHistoryRepository,
    private val purchaseService: PurchaseService,
    private val invoiceHistoryLineRepository: InvoiceHistoryLineRepository,
    private val invoiceHistoryService: InvoiceHistoryService,
) {

    fun listAllRows(): List<ShippingHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        return shippingHistoryRepository.findAll(sort).map { e ->
            ShippingHistoryRowDto(
                id = e.id ?: 0L,
                country = e.country,
                consignee = e.consignee,
                shipmentDate = e.shipmentDate?.toString(),
                pol = e.pol,
                pod = e.pod,
                bookingId = e.bookingId,
                vessel = e.vessel,
                priceType = e.priceType,
                chassis = e.chassis,
                clientName = e.clientName,
                amount = e.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                createdAt = e.createdAt?.toString(),
                invoiceCreated = invoiceHistoryLineRepository.existsByChassis(e.chassis),
            )
        }
    }

    @Transactional
    fun saveBatch(request: ShippingHistoryBatchRequest): Int {
        if (request.items.isEmpty()) {
            throw IllegalArgumentException("items must not be empty")
        }
        val shipmentDate: LocalDate? = request.shipmentDate?.trim()?.takeIf { it.isNotEmpty() }?.let { s ->
            try {
                LocalDate.parse(s)
            } catch (_: DateTimeParseException) {
                null
            }
        }
        val priceType = request.priceType?.trim()?.takeIf { it.isNotEmpty() }
        val bookingKey = request.bookingId?.trim().orEmpty()
        val country = request.country?.trim()?.takeIf { it.isNotEmpty() }
        val consignee = request.consignee?.trim()?.takeIf { it.isNotEmpty() }
        val pol = request.pol?.trim()?.takeIf { it.isNotEmpty() }
        val pod = request.pod?.trim()?.takeIf { it.isNotEmpty() }
        val vessel = request.vessel?.trim()?.takeIf { it.isNotEmpty() }
        val blNo = request.blNo?.trim()?.takeIf { it.isNotEmpty() }
        val bookingIdStored = bookingKey.takeIf { it.isNotEmpty() }

        var count = 0
        for (item in request.items) {
            val chassis = item.chassis.trim()
            if (chassis.isEmpty()) {
                throw IllegalArgumentException("chassis must not be blank")
            }
            val fromPurchase = purchaseService.getPurchaseByChassis(chassis)
                ?.clientName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            val fromRequest = item.clientName?.trim()?.takeIf { it.isNotEmpty() }
            val clientName = fromPurchase ?: fromRequest
            val amount = item.amount ?: BigDecimal.ZERO

            val existing = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)
            val toSave = if (existing != null) {
                existing.copy(
                    country = country,
                    consignee = consignee,
                    shipmentDate = shipmentDate,
                    pol = pol,
                    pod = pod,
                    bookingId = bookingIdStored,
                    vessel = vessel,
                    blNo = blNo ?: existing.blNo,
                    priceType = priceType,
                    chassis = chassis,
                    clientName = clientName,
                    amount = amount,
                    createdAt = existing.createdAt,
                )
            } else {
                ShippingHistory(
                    country = country,
                    consignee = consignee,
                    shipmentDate = shipmentDate,
                    pol = pol,
                    pod = pod,
                    bookingId = bookingIdStored,
                    vessel = vessel,
                    blNo = blNo,
                    priceType = priceType,
                    chassis = chassis,
                    clientName = clientName,
                    amount = amount,
                )
            }
            shippingHistoryRepository.save(toSave)
            count++
        }
        return count
    }

    /**
     * Vessels that still have at least one chassis not yet marked invoiced in [Purchase.invoiceConfirmed]
     * (same rule as [invoiceSlice]). Vessels where every chassis is confirmed are omitted.
     */
    fun distinctVesselsForInvoiceClient(clientName: String): List<String> {
        val key = clientName.trim()
        if (key.isEmpty()) return emptyList()
        val fromDb = shippingHistoryRepository.findDistinctVesselsForInvoiceClient(key)
        return fromDb.filter { vessel -> invoiceSlice(key, vessel).lines.isNotEmpty() }
    }

    fun distinctClientNamesForInvoice(): List<String> =
        shippingHistoryRepository.findDistinctClientNamesForInvoice()

    /**
     * Invoice lines for client + vessel. Excludes chassis whose purchase has invoice_confirmed true
     * (already invoiced). Header is taken from the first included row only.
     */
    fun invoiceSlice(clientName: String, vessel: String): ShippingHistoryInvoiceSliceDto {
        val cn = clientName.trim()
        val v = vessel.trim()
        val rows = shippingHistoryRepository.findInvoiceRowsOrderByIdAsc(cn, v)
        if (rows.isEmpty()) {
            return ShippingHistoryInvoiceSliceDto(
                header = ShippingHistoryInvoiceHeaderDto(),
                lines = emptyList(),
            )
        }
        val included = rows.mapNotNull { row ->
            val p = purchaseService.getPurchaseByChassis(row.chassis)
            if (p?.invoiceConfirmed == true) null else row to p
        }
        if (included.isEmpty()) {
            return ShippingHistoryInvoiceSliceDto(
                header = ShippingHistoryInvoiceHeaderDto(),
                lines = emptyList(),
            )
        }
        val first = included.first().first
        val header = ShippingHistoryInvoiceHeaderDto(
            shipmentDate = first.shipmentDate?.toString(),
            pol = first.pol,
            pod = first.pod,
            priceType = first.priceType,
        )
        val lines = included.map { (row, p) ->
            ShippingHistoryInvoiceLineDto(
                shippingHistoryId = row.id ?: 0L,
                chassis = row.chassis,
                amount = row.amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                carName = p?.carName,
                carModelYear = p?.carModelYear,
                purchaseId = p?.id,
            )
        }
        return ShippingHistoryInvoiceSliceDto(header, lines)
    }

    /**
     * Deletes shipping_history rows where **every** chassis segment is contained in [chassisCoverTokens]
     * (trimmed, case-insensitive). Rows that list any chassis **not** in this cover set are kept.
     * Does not update purchases — use when purchases were already reset elsewhere (e.g. Rixo history removal).
     */
    @Transactional
    fun deleteShippingRowsFullyCoveredByChassis(chassisCoverTokens: Collection<String>): Int {
        if (chassisCoverTokens.isEmpty()) return 0
        val cover = chassisCoverTokens
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .toSet()
        if (cover.isEmpty()) return 0

        val sort = Sort.by(Sort.Direction.ASC, "id")
        val allRows = shippingHistoryRepository.findAll(sort)
        val idsToDelete = mutableListOf<Long>()
        for (row in allRows) {
            val parts = row.chassis.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) continue
            val onRow = parts.map { it.lowercase(Locale.ROOT) }.toSet()
            if (onRow.all { it in cover }) {
                row.id?.let { idsToDelete.add(it) }
            }
        }
        if (idsToDelete.isEmpty()) return 0
        shippingHistoryRepository.deleteAllById(idsToDelete)
        return idsToDelete.size
    }

    /**
     * Deletes shipping-history rows by [ids] and:
     * - sets `booking_requested = false` on purchases for those chassis;
     * - sets `invoice_confirmed = false` (Sold) on those purchases;
     * - removes invoice_history rows whose lines are **only** chassis from this delete set
     *   (invoices that still list other chassis are kept).
     */
    /**
     * Removes one chassis token from a shipping-history row (by [historyId] or latest row for [chassisTokenRaw]).
     * Deletes the row when no chassis segments remain. Resets booking_requested and invoice_confirmed on that chassis.
     */
    @Transactional
    fun removeChassisTokenFromHistoryRow(historyId: Long?, chassisTokenRaw: String): Map<String, Any> {
        val tokenClean = chassisTokenRaw.trim()
        if (tokenClean.isEmpty()) throw IllegalArgumentException("chassisToken is required")

        val row = when (historyId) {
            null -> shippingHistoryRepository.findFirstByChassisOrderByIdDesc(tokenClean)
            else -> shippingHistoryRepository.findById(historyId).orElse(null)
        } ?: throw IllegalArgumentException("Shipping history row not found")

        val (newChassis, removed) = removeFirstMatchingChassisOccurrence(row.chassis, tokenClean)
        if (!removed) throw IllegalArgumentException("Chassis token not found in this shipping history row")

        val rowId = row.id ?: throw IllegalArgumentException("Shipping history row has no id")
        when {
            newChassis.isNullOrBlank() -> shippingHistoryRepository.deleteById(rowId)
            else -> shippingHistoryRepository.save(row.copy(chassis = newChassis, createdAt = row.createdAt))
        }

        val chassisList = listOf(tokenClean)
        purchaseService.unmarkBookingRequestedForChassis(chassisList)
        purchaseService.unmarkInvoiceConfirmedForChassis(chassisList)
        invoiceHistoryService.deleteInvoicesFullyCoveredByChassis(chassisList)

        return mapOf(
            "deletedRow" to newChassis.isNullOrBlank(),
            "remainingChassis" to (newChassis ?: ""),
            "historyId" to rowId,
        )
    }

    @Transactional
    fun deleteAndUnbookByIds(ids: List<Long>): Map<String, Any> {
        if (ids.isEmpty()) {
            return mapOf(
                "deleted" to 0,
                "unbookedPurchases" to 0,
                "invoiceHistoryRemoved" to 0,
            )
        }

        // Collect all chassis values from rows about to be deleted
        val chassisToUnbook = mutableSetOf<String>()
        for (id in ids) {
            val row = shippingHistoryRepository.findById(id).orElse(null) ?: continue
            // chassis may be a single value or semicolon/comma-separated multi-chassis string
            val parts = row.chassis.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }
            chassisToUnbook.addAll(parts)
        }

        val chassisList = chassisToUnbook.toList()

        // Delete the shipping history rows
        shippingHistoryRepository.deleteAllById(ids)

        // Reset booking_requested on matching purchases
        val unbookedCount = purchaseService.unmarkBookingRequestedForChassis(chassisList)

        // Clear Sold / invoice_confirmed for those chassis (workflow recomputed in PurchaseService)
        purchaseService.unmarkInvoiceConfirmedForChassis(chassisList)

        // Drop invoice_history rows that reference only chassis from this shipping delete
        val invoiceHistoryRemoved = invoiceHistoryService.deleteInvoicesFullyCoveredByChassis(chassisList)

        return mapOf(
            "deleted" to ids.size,
            "unbookedPurchases" to unbookedCount,
            "invoiceHistoryRemoved" to invoiceHistoryRemoved,
        )
    }

    /** Remove one segment matching [tokenToRemove]; returns new joined string or null when empty; [removed] iff a segment was removed. */
    private fun removeFirstMatchingChassisOccurrence(raw: String?, tokenToRemove: String): Pair<String?, Boolean> {
        if (raw.isNullOrBlank()) return Pair(raw, false)
        val want = tokenToRemove.trim().uppercase(Locale.ROOT)
        if (want.isEmpty()) return Pair(raw, false)
        val parts = raw.split(';', ',', '\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        if (parts.isEmpty()) return Pair(null, false)
        val removeIdx = parts.indexOfFirst { it.uppercase(Locale.ROOT) == want }
        if (removeIdx < 0) return Pair(raw, false)
        parts.removeAt(removeIdx)
        if (parts.isEmpty()) return Pair(null, true)
        return Pair(parts.joinToString(";"), true)
    }
}
