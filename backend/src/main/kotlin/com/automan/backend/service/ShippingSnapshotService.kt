package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.util.PurchaseDateParseUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Phase 4b+: shipment snapshot read/write adapter.
 * Canonical source: [shipping_history] (UNIQUE chassis) for [Purchase.vessel], [Purchase.shipmentDate], [Purchase.blNo].
 */
@Service
class ShippingSnapshotService(
    private val shippingHistoryRepository: ShippingHistoryRepository,
) {
    @Transactional(readOnly = true)
    fun applyForRead(purchase: Purchase): Purchase =
        applyForRead(purchase, resolveSnapshot(purchase.chassis))

    @Transactional(readOnly = true)
    fun applyForReadBatch(purchases: List<Purchase>): List<Purchase> {
        if (purchases.isEmpty()) return purchases
        val snapshots = loadSnapshotsByChassis(purchases.map { it.chassis })
        return purchases.map { applyForRead(it, snapshots[it.chassis.trim()]) }
    }

    fun applyForRead(purchase: Purchase, snapshot: ShippingHistory?): Purchase {
        if (snapshot == null) return purchase

        var merged = purchase
        snapshot.vessel?.trim()?.takeIf { it.isNotEmpty() }?.let { merged = merged.copy(vessel = it) }
        snapshot.shipmentDate?.let { merged = merged.copy(shipmentDate = it.toString()) }
        snapshot.blNo?.trim()?.takeIf { it.isNotEmpty() }?.let { merged = merged.copy(blNo = it) }
        return merged
    }

    /** Writes vessel, shipmentDate, blNo to shipping_history when present on purchase (post column drop). */
    @Transactional
    fun syncFromPurchase(purchase: Purchase): Purchase {
        val chassis = purchase.chassis.trim()
        if (chassis.isEmpty()) return purchase

        val vessel = purchase.vessel?.trim()?.takeIf { it.isNotEmpty() }
        val blNo = purchase.blNo?.trim()?.takeIf { it.isNotEmpty() }
        val shipmentDate = parseShipmentDate(purchase.shipmentDate)
        val existing = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(chassis)

        if (existing == null && vessel == null && blNo == null && shipmentDate == null) {
            return purchase
        }

        val clientName = purchase.clientName?.trim()?.takeIf { it.isNotEmpty() }
        val toSave = if (existing != null) {
            existing.copy(
                vessel = vessel ?: existing.vessel,
                blNo = blNo ?: existing.blNo,
                shipmentDate = shipmentDate ?: existing.shipmentDate,
                clientName = clientName ?: existing.clientName,
            )
        } else {
            ShippingHistory(
                chassis = chassis,
                vessel = vessel,
                blNo = blNo,
                shipmentDate = shipmentDate,
                clientName = clientName,
                amount = BigDecimal.ZERO,
            )
        }
        shippingHistoryRepository.save(toSave)
        return purchase
    }

    @Transactional(readOnly = true)
    fun resolveSnapshot(chassis: String): ShippingHistory? {
        val key = chassis.trim()
        if (key.isEmpty()) return null
        return shippingHistoryRepository.findFirstByChassisOrderByIdDesc(key)
    }

    private fun loadSnapshotsByChassis(chassisValues: List<String>): Map<String, ShippingHistory> {
        val keys = chassisValues.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (keys.isEmpty()) return emptyMap()
        return shippingHistoryRepository.findByChassisIn(keys)
            .groupBy { it.chassis.trim() }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.id ?: 0L }!! }
    }

    private fun parseShipmentDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return PurchaseDateParseUtils.parseToLocalDate(s)
    }
}
