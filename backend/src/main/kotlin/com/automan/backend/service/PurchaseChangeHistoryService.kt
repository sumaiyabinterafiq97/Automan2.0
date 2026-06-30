package com.automan.backend.service

import com.automan.backend.dto.PurchaseChangeHistoryPageRequest
import com.automan.backend.dto.PurchaseChangeHistoryPageResponse
import com.automan.backend.dto.PurchaseChangeHistoryRowDto
import com.automan.backend.dto.PurchaseChangeHistorySingleRowDto
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseChangeHistory
import com.automan.backend.repository.PurchaseChangeHistoryRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class PurchaseChangeHistoryService(
    private val repository: PurchaseChangeHistoryRepository,
) {
    private val maxStoredLength = 8000

    fun pageScoped(req: PurchaseChangeHistoryPageRequest): PurchaseChangeHistoryPageResponse {
        val ids = req.purchaseIds.distinct().filter { it > 0 }.take(MAX_PURCHASE_IDS)
        val size = req.historySize.coerceIn(1, MAX_HISTORY_SIZE)
        val page = req.historyPage.coerceAtLeast(0)
        if (ids.isEmpty()) {
            return PurchaseChangeHistoryPageResponse(
                content = emptyList(),
                totalElements = 0,
                totalPages = 0,
                number = page,
                size = size,
            )
        }
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "changedAt"))
        val result = repository.findByPurchaseIdIn(ids, pageable)
        val content = result.content.map { row ->
            PurchaseChangeHistoryRowDto(
                id = row.id!!,
                purchaseId = row.purchaseId,
                chassis = row.chassis,
                fieldName = row.fieldName,
                oldValue = row.oldValue,
                newValue = row.newValue,
                changedBy = row.changedBy,
                changedAt = row.changedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            )
        }
        return PurchaseChangeHistoryPageResponse(
            content = content,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            number = result.number,
            size = result.size,
        )
    }

    fun listForSinglePurchase(purchaseId: Long): List<PurchaseChangeHistorySingleRowDto> {
        if (purchaseId <= 0L) return emptyList()
        val pageable =
            PageRequest.of(0, MAX_SINGLE_PURCHASE_HISTORY_ROWS, Sort.by(Sort.Direction.DESC, "changedAt"))
        return repository.findByPurchaseId(purchaseId, pageable).content.map { row ->
            PurchaseChangeHistorySingleRowDto(
                changedAt = row.changedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                fieldName = row.fieldName,
                oldValue = row.oldValue,
                newValue = row.newValue,
            )
        }
    }

    @Transactional
    fun recordPurchasePartialEdit(before: Purchase, after: Purchase, changedBy: String? = null) {
        val purchaseId = before.id ?: return
        if (after.id != null && after.id != purchaseId) return
        
        val fieldNames = mutableListOf<String>()
        val oldValues = mutableListOf<String>()
        val newValues = mutableListOf<String>()
        val now = java.time.LocalDateTime.now()

        fun add(fieldName: String, oldV: String?, newV: String?) {
            if (oldV == newV) return
            fieldNames.add(fieldName)
            oldValues.add(oldV?.take(maxStoredLength) ?: "")
            newValues.add(newV?.take(maxStoredLength) ?: "")
        }

        add("date", str(before.date), str(after.date))
        add("chassis", str(before.chassis), str(after.chassis))
        add("carModelYear", str(before.carModelYear), str(after.carModelYear))
        add("brand", str(before.brand), str(after.brand))
        add("carName", str(before.carName), str(after.carName))
        add("shipmentSize", str(before.shipmentSize), str(after.shipmentSize))
        add("grade", str(before.grade), str(after.grade))
        add("rank", str(before.rank), str(after.rank))
        add("color", str(before.color), str(after.color))
        add("fuel", str(before.fuel), str(after.fuel))
        add("seat", str(before.seat), str(after.seat))
        add("door", str(before.door), str(after.door))
        add("distance", str(before.distance), str(after.distance))
        add("options", str(before.options), str(after.options))
        add("cc", normInt(before.cc), normInt(after.cc))
        add("shift", str(before.shift), str(after.shift))
        add("wd", str(before.wd), str(after.wd))
        add("driveType", str(before.driveType), str(after.driveType))
        add("auctionNo", str(before.auctionNo), str(after.auctionNo))
        add("auctionHouse", str(before.auctionHouse), str(after.auctionHouse))
        add("stockLocation", str(before.stockLocation), str(after.stockLocation))
        add("pol", str(before.pol), str(after.pol))
        add("pod", str(before.pod), str(after.pod))
        add("rixoCompany", str(before.rixoCompany), str(after.rixoCompany))
        add("clientName", str(before.clientName), str(after.clientName))
        add("consignee", str(before.consignee), str(after.consignee))
        add("clientId", normLong(before.clientId), normLong(after.clientId))
        add("country", str(before.country), str(after.country))
        add("price", str(before.price), str(after.price))
        add("auctionFee", str(before.auctionFee), str(after.auctionFee))
        add("auctionPenaltyFee", str(before.auctionPenaltyFee), str(after.auctionPenaltyFee))
        add("recycleFee", str(before.recycleFee), str(after.recycleFee))
        add("roadTax", str(before.roadTax), str(after.roadTax))
        add("taxTotal", str(before.taxTotal), str(after.taxTotal))
        add("totalPrice", str(before.totalPrice), str(after.totalPrice))
        add("paymentDate", str(before.paymentDate), str(after.paymentDate))
        add("rixoRequested", str(before.rixoRequested), str(after.rixoRequested))
        add("rixoConfirmed", str(before.rixoConfirmed), str(after.rixoConfirmed))
        add("notes", str(before.notes), str(after.notes))
        add("shipmentDate", str(before.shipmentDate), str(after.shipmentDate))
        add("blNo", str(before.blNo), str(after.blNo))
        add("vessel", str(before.vessel), str(after.vessel))
        add("bookingRequested", before.bookingRequested.toString(), after.bookingRequested.toString())
        add("invoiceConfirmed", normBool(before.invoiceConfirmed), normBool(after.invoiceConfirmed))
        add("shipmentCharges", str(before.shipmentCharges), str(after.shipmentCharges))
        add("freight", str(before.freight), str(after.freight))
        add("storageCharges", str(before.storageCharges), str(after.storageCharges))
        add("miscCharges", str(before.miscCharges), str(after.miscCharges))
        add("inspectionFee", str(before.inspectionFee), str(after.inspectionFee))
        add("commission", str(before.commission), str(after.commission))
        add("rixoPrice", str(before.rixoPrice), str(after.rixoPrice))
        add("venueId", str(before.venueId), str(after.venueId))
        add("numberCut", str(before.numberCut), str(after.numberCut))
        add("shaken", normBool(before.shaken), normBool(after.shaken))
        add("negotiate", normBool(before.negotiate), normBool(after.negotiate))
        add("repairCompany", str(before.repairCompany), str(after.repairCompany))
        add("repairCharges", str(before.repairCharges), str(after.repairCharges))
        add("bookingId", normLong(before.bookingId), normLong(after.bookingId))
        run {
            val a = str(before.carPictures)
            val b = str(after.carPictures)
            if (a != b) {
                if (a.length > 200 || b.length > 200) {
                    add(
                        "carPictures",
                        "(${a.length} chars)",
                        "(${b.length} chars)",
                    )
                } else {
                    add("carPictures", a, b)
                }
            }
        }

        if (fieldNames.isNotEmpty()) {
            for (i in fieldNames.indices) {
                repository.save(
                    PurchaseChangeHistory(
                        purchaseId = purchaseId,
                        chassis = after.chassis,
                        fieldName = fieldNames[i],
                        oldValue = oldValues[i],
                        newValue = newValues[i],
                        changedBy = changedBy,
                        changedAt = now,
                    ),
                )
            }
        }
    }

    private fun str(s: String?): String = s?.trim()?.ifEmpty { "" } ?: ""

    private fun normInt(v: Int?): String = v?.toString() ?: ""

    private fun normLong(v: Long?): String = v?.toString() ?: ""

    private fun normBool(v: Boolean?): String = when (v) {
        true -> "true"
        false -> "false"
        null -> ""
    }

    companion object {
        const val MAX_PURCHASE_IDS = 500
        const val MAX_HISTORY_SIZE = 100
        const val MAX_SINGLE_PURCHASE_HISTORY_ROWS = 500
    }
}
