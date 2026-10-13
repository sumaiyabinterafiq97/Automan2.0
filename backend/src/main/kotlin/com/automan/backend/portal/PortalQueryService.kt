package com.automan.backend.portal

import com.automan.backend.model.Purchase
import com.automan.backend.repository.ClientMapRepository
import com.automan.backend.repository.PurchaseMediaRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.service.PurchaseChangeHistoryService
import com.automan.backend.service.PurchaseMediaService
import com.automan.backend.service.PurchaseService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Status rules, named because the workflow enum has no shipped or historical state.
 *
 * PURCHASED: a purchases row whose client_id is in the caller's current scope.
 * SHIPPED: that purchase's chassis trim-matches a shipping_history row, and exactly one purchase in the database
 *   has that trimmed chassis. purchases.chassis is not unique and shipping_history has no purchase id, so a shared
 *   chassis fails closed: the car stays on the purchased list and is not marked shipped.
 * NOT_SHIPPED: an in-scope purchase with no unambiguous shipping match.
 * INVOICED: an in-scope purchase whose chassis trim-matches an invoice_history_line, with the same one-purchase rule.
 *   This is not workflow_status.
 *   invoice_history has no client_id, so client_name and consignee text are not used. Only in-scope chassis are returned.
 *   The invoice date is the date portion of invoice_history.created_at. There is no invoice_date column.
 * BOOKED_ONGOING: an in-scope purchase with booking_id set and no shipping_history row. Grouped by that numeric booking id
 *   rendered as a decimal string. Vessel and shipment dates are omitted because V52 removed them from purchases.
 *   POL and POD are still purchase columns and are included when present. They are not authorization keys.
 *   A booking-requested workflow with a null booking_id stays on the not-shipped car list and is not an ongoing shipment.
 * ETA_HISTORY: a scoped shipping_history row whose eta is before today's UTC date.
 * ETA_ONGOING: a scoped shipping_history row whose eta is null or on or after today's UTC date.
 *   Client shipments return both ETA windows. Consignee history and ongoing each return one window.
 *   Rows are grouped by shipping_history.booking_id. A null booking id stays its own group.
 *   purchases.booking_id is never compared with shipping_history.booking_id.
 *
 * An individual resource outside the current scope returns 404 with the message "Not found".
 * List endpoints return an empty list when the account is unlinked, revoked, or the consignee is inactive.
 */
@Service
class PortalQueryService(
    private val reads: PortalReadRepository,
    private val media: PurchaseMediaService,
    private val purchases: PurchaseService,
    private val purchaseRepository: PurchaseRepository,
    private val clientMaps: ClientMapRepository,
    private val shippingHistories: ShippingHistoryRepository,
    private val purchaseMediaRepository: PurchaseMediaRepository,
    private val changeHistory: PurchaseChangeHistoryService,
    private val objectMapper: ObjectMapper,
) {
    private val clock: Clock = Clock.systemUTC()
    @Transactional(readOnly = true)
    fun dashboard(scope: PortalScope): ClientDashboardDto {
        val counts = counts(scope)
        return ClientDashboardDto(counts.purchased, counts.shipped, counts.invoiced)
    }

    @Transactional(readOnly = true)
    fun consigneeDashboard(scope: PortalScope): ConsigneeDashboardDto {
        val counts = counts(scope)
        val clients = consigneeClientMap(scope).map { it.clientName.trim().lowercase() }.distinct().size.toLong()
        return ConsigneeDashboardDto(counts.purchased, counts.shipped, counts.invoiced, clients)
    }

    @Transactional(readOnly = true)
    fun cars(
        scope: PortalScope,
        chassisQuery: String?,
        shipped: Boolean?,
        unbookedOnly: Boolean = false,
    ): List<PortalCarDto> {
        return scopedCars(scope, chassisQuery)
            .filter { shipped == null || it.shipped == shipped }
            .filter { !unbookedOnly || it.bookingId == null }
            .map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun consigneeShipped(scope: PortalScope, chassisQuery: String?): List<PortalCarDto> {
        val rows = scopedCars(scope, chassisQuery).filter { it.shipped }
        val names = reads.clientNames(rows.mapNotNull { it.clientId }.distinct())
        return rows.map { row ->
            row.toDto().copy(clientName = row.clientId?.let { names[it] })
        }
    }

    @Transactional(readOnly = true)
    fun consigneeClientMap(scope: PortalScope): List<PortalClientMapDto> {
        if (!scope.active) return emptyList()
        val wanted = scope.consigneeLegalName?.trim()?.lowercase().orEmpty()
        if (wanted.isEmpty()) return emptyList()
        val allowed = scopedCars(scope, "")
            .map { it.chassis.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val names = linkedMapOf<String, String>()
        shippingHistories.findConsigneeClientNames().forEach { pair ->
            val chassis = (pair[0] as String?)?.trim()?.lowercase().orEmpty()
            val clientName = (pair[2] as String?)?.trim().orEmpty()
            if (chassis.isEmpty() || clientName.isEmpty() || chassis !in allowed) return@forEach
            if (consigneeTokens(pair[1] as String?).none { it.lowercase() == wanted }) return@forEach
            names.putIfAbsent(clientName.lowercase(), clientName)
        }
        if (names.isEmpty()) return emptyList()
        val maps = clientMaps.findByClientNamesIgnoreCase(names.keys).groupBy { it.clientName.trim().lowercase() }
        return names.toSortedMap().flatMap { (key, display) ->
            val rows = maps[key].orEmpty().sortedBy { it.id }
            if (rows.isEmpty()) {
                listOf(PortalClientMapDto(clientName = display))
            } else {
                rows.map { row ->
                    PortalClientMapDto(
                        clientName = row.clientName,
                        country = row.country,
                        pod = row.pod,
                        address = row.address,
                        bankInfo = row.bankInfo,
                        consignee = row.consignee,
                        debitLimit = row.debitLimit?.stripTrailingZeros()?.toPlainString(),
                    )
                }
            }
        }
    }

    private fun consigneeTokens(raw: String?): List<String> =
        raw?.replace("\r\n", "\n")
            ?.replace('\r', '\n')
            ?.split(Regex("[,;\\n]+"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    @Transactional(readOnly = true)
    fun clientShipments(scope: PortalScope): List<ClientShipmentDto> =
        groupShipments(reads.shipments(purchaseScope(scope))).map { it.toClient() }

    @Transactional(readOnly = true)
    fun consigneeOngoing(scope: PortalScope): List<ConsigneeShipmentDto> {
        val names = names(scope)
        val fromShipping = groupShipments(reads.shipments(purchaseScope(scope)).filter { it.eta == null || !it.eta.isBefore(today()) })
        val booked = groupBooked(scopedCars(scope, "").filter { !it.shipped && it.bookingId != null })
        return (booked + fromShipping).map { it.toConsignee(names) }
    }

    @Transactional(readOnly = true)
    fun consigneeHistory(scope: PortalScope): List<ConsigneeShipmentDto> {
        val names = names(scope)
        val today = today()
        return groupShipments(reads.shipments(purchaseScope(scope)).filter { it.eta != null && it.eta.isBefore(today) })
            .map { it.toConsignee(names) }
    }

    @Transactional(readOnly = true)
    fun invoices(scope: PortalScope): List<PortalInvoiceDto> {
        val grouped = linkedMapOf<Long, MutableList<PortalReadRepository.InvoiceRow>>()
        reads.invoices(purchaseScope(scope)).forEach { row ->
            grouped.getOrPut(row.invoiceId) { mutableListOf() }.add(row)
        }
        return grouped.values.map { rows ->
            val first = rows.first()
            PortalInvoiceDto(
                invoiceNumber = first.invoiceNumber,
                date = first.createdAt?.toLocalDate()?.toString(),
                vessel = rows.firstNotNullOfOrNull { it.vessel },
                chassis = rows.map { it.chassis.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
                priceType = rows.firstNotNullOfOrNull { it.priceType },
            )
        }
    }

    @Transactional(readOnly = true)
    fun transactions(scope: PortalScope): List<PortalTransactionDto> =
        reads.transactions(ids(scope)).map {
            PortalTransactionDto(
                date = it.date.toString(),
                type = it.type,
            )
        }

    @Transactional(readOnly = true)
    fun mediaUrl(scope: PortalScope, purchaseId: Long, mediaId: Long): PortalMediaUrlDto {
        if (!reads.purchaseInScope(purchaseId, purchaseScope(scope))) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        }
        return PortalMediaUrlDto(media.presignedUrl(purchaseId, mediaId))
    }

    private fun counts(scope: PortalScope): PortalReadRepository.DashboardCounts =
        reads.dashboard(purchaseScope(scope))

    private fun ids(scope: PortalScope): List<Long> = if (scope.active) scope.clientIds else emptyList()

    private fun purchaseScope(scope: PortalScope): PortalReadRepository.PurchaseScope {
        if (!scope.active) return PortalReadRepository.PurchaseScope()
        return PortalReadRepository.PurchaseScope(
            clientIds = scope.clientIds,
            consigneeName = scope.consigneeLegalName.orEmpty(),
        )
    }

    private fun names(scope: PortalScope): Map<Long, String> = reads.clientNames(ids(scope))

    private fun scopedCars(scope: PortalScope, chassisQuery: String?): List<PortalReadRepository.CarRow> =
        reads.cars(purchaseScope(scope), chassisFilter(chassisQuery))

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))

    private fun chassisFilter(raw: String?): String =
        raw?.trim().orEmpty().take(64).replace("%", "").replace("_", "")

    private data class ShipmentGroup(
        val bookingId: String?,
        val vessel: String?,
        val pol: String?,
        val pod: String?,
        val shipmentDate: String?,
        val cyCutDate: String?,
        val eta: String?,
        val chassis: List<String>,
        val clientIds: List<Long>,
    )

    private fun groupShipments(rows: List<PortalReadRepository.ShipRow>): List<ShipmentGroup> {
        val groups = linkedMapOf<String, MutableList<PortalReadRepository.ShipRow>>()
        rows.sortedWith(compareBy({ it.bookingId ?: "" }, { it.id })).forEach { row ->
            val key = row.bookingId?.takeIf { it.isNotBlank() } ?: "row:${row.id}"
            groups.getOrPut(key) { mutableListOf() }.add(row)
        }
        return groups.values.map { group ->
            ShipmentGroup(
                bookingId = group.first().bookingId?.takeIf { it.isNotBlank() },
                vessel = group.firstNotNullOfOrNull { it.vessel },
                pol = group.firstNotNullOfOrNull { it.pol },
                pod = group.firstNotNullOfOrNull { it.pod },
                shipmentDate = group.firstNotNullOfOrNull { it.shipmentDate }?.toString(),
                cyCutDate = group.firstNotNullOfOrNull { it.cyCutDate }?.toString(),
                eta = group.firstNotNullOfOrNull { it.eta }?.toString(),
                chassis = group.map { it.chassis.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
                clientIds = group.mapNotNull { it.clientId }.distinct(),
            )
        }
    }

    private fun groupBooked(rows: List<PortalReadRepository.CarRow>): List<ShipmentGroup> {
        val groups = linkedMapOf<Long, MutableList<PortalReadRepository.CarRow>>()
        rows.sortedBy { it.purchaseId }.forEach { row ->
            val bookingId = row.bookingId ?: return@forEach
            groups.getOrPut(bookingId) { mutableListOf() }.add(row)
        }
        return groups.map { (bookingId, group) ->
            ShipmentGroup(
                bookingId = bookingId.toString(),
                vessel = null,
                pol = group.firstNotNullOfOrNull { it.pol },
                pod = group.firstNotNullOfOrNull { it.pod },
                shipmentDate = null,
                cyCutDate = null,
                eta = null,
                chassis = group.map { it.chassis.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
                clientIds = group.mapNotNull { it.clientId }.distinct(),
            )
        }
    }

    private fun PortalReadRepository.CarRow.toDto() = PortalCarDto(
        purchaseId = purchaseId,
        chassis = chassis,
        carName = carName,
        year = year,
        stockLocation = stockLocation,
        country = country,
        shipped = shipped,
    )

    private fun ShipmentGroup.toClient() = ClientShipmentDto(
        bookingId = bookingId,
        vessel = vessel,
        pol = pol,
        pod = pod,
        shipmentDate = shipmentDate,
        cyCutDate = cyCutDate,
        eta = eta,
        chassis = chassis,
    )

    @Transactional(readOnly = true)
    fun clientPurchaseList(scope: PortalScope): List<PortalPurchaseListItemDto> {
        if (!scope.active || scope.clientIds.isEmpty()) return emptyList()
        val rows = purchases.hydratePurchasesForExport(
            scope.clientIds.flatMap { purchaseRepository.findByClientId(it) },
        ).sortedWith(compareBy({ it.chassis }, { it.id }))
        val thumbs = firstImageIds(rows.mapNotNull { it.id })
        return rows.map { row ->
            PortalPurchaseListItemDto(
                mediaId = row.id?.let { thumbs[it] },
                purchase = portalPurchaseMap(row),
            )
        }
    }

    @Transactional(readOnly = true)
    fun clientPurchase(scope: PortalScope, purchaseId: Long): PortalPurchaseDetailDto {
        requireInScope(scope, purchaseId)
        val row = purchases.getPurchaseById(purchaseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        val images = purchaseMediaRepository
            .findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchaseId)
            .filter { it.contentType.startsWith("image/") }
            .mapNotNull { it.id }
        return PortalPurchaseDetailDto(portalPurchaseMap(row), images)
    }

    @Transactional(readOnly = true)
    fun clientPurchaseHistory(scope: PortalScope, purchaseId: Long): List<PortalPurchaseHistoryDto> {
        requireInScope(scope, purchaseId)
        return changeHistory.listForSinglePurchase(purchaseId).map {
            PortalPurchaseHistoryDto(
                changedAt = it.changedAt,
                fieldName = it.fieldName,
                oldValue = it.oldValue,
                newValue = it.newValue,
            )
        }
    }

    private fun requireInScope(scope: PortalScope, purchaseId: Long) {
        if (!reads.purchaseInScope(purchaseId, purchaseScope(scope))) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND)
        }
    }

    private fun firstImageIds(purchaseIds: Collection<Long>): Map<Long, Long> {
        if (purchaseIds.isEmpty()) return emptyMap()
        val first = linkedMapOf<Long, Long>()
        purchaseMediaRepository
            .findByPurchaseIdInAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchaseIds)
            .forEach { row ->
                val id = row.id ?: return@forEach
                if (row.contentType.startsWith("image/") && row.purchaseId !in first) {
                    first[row.purchaseId] = id
                }
            }
        return first
    }

    private fun portalPurchaseMap(row: Purchase): Map<String, Any?> {
        val raw = objectMapper.convertValue(row, Map::class.java)
        val out = linkedMapOf<String, Any?>()
        PURCHASE_KEYS.forEach { key ->
            if (raw.containsKey(key)) out[key] = raw[key]
        }
        out["id"] = row.id
        return out
    }

    private fun ShipmentGroup.toConsignee(names: Map<Long, String>) = ConsigneeShipmentDto(
        bookingId = bookingId,
        vessel = vessel,
        pol = pol,
        pod = pod,
        shipmentDate = shipmentDate,
        cyCutDate = cyCutDate,
        eta = eta,
        chassis = chassis,
        clientNames = clientIds.mapNotNull { names[it] }.distinct().sorted(),
    )

    companion object {
        const val NOT_FOUND = "Not found"

        /** Staff purchase-list and edit-screen fields. File keys and audit actors are not included. */
        private val PURCHASE_KEYS = listOf(
            "id", "date", "chassis", "carModelYear", "manufactureYear", "brand", "carName", "shipmentSize",
            "grade", "rank", "color", "fuel", "seat", "door", "distance", "options", "cc", "shift", "wd",
            "driveType", "auctionNo", "auctionHouse", "stockLocation", "pol", "pod", "rixoCompany", "venueId",
            "clientName", "consignee", "country", "price", "auctionFee", "auctionPenaltyFee", "recycleFee",
            "roadTax", "taxTotal", "totalPrice", "paymentDate", "rixoRequested", "rixoConfirmed", "rixoPrice",
            "notes", "shipmentDate", "blNo", "vessel", "bookingRequested", "invoiceConfirmed", "shipmentCharges",
            "freight", "storageCharges", "miscCharges", "inspectionFee", "commission", "numberCut", "shaken",
            "negotiate", "local", "repairCompany", "repairCharges", "profit", "bookingId",
        )
    }
}
