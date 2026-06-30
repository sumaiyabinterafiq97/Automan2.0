package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseCostLine
import com.automan.backend.repository.PurchaseCostLineRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Phase 5: canonical cost store is [purchase_cost_lines]; flat API keys are hydrated via [applyForRead].
 * Writes sync in-memory [Purchase] fee fields → cost lines only ([syncFromPurchase]).
 */
@Service
class PurchaseCostLineService(
    private val purchaseCostLineRepository: PurchaseCostLineRepository,
) {
    data class CostFieldMapping(
        val costCode: String,
        val sortOrder: Int,
        val stringValue: (Purchase) -> String?,
        val decimalValue: (Purchase) -> BigDecimal? = { null },
        val apply: (Purchase, BigDecimal) -> Purchase,
    )

    companion object {
        /** Registry-aligned cost codes (see purchase_field_registry / CSV). */
        val COST_FIELD_MAPPINGS: List<CostFieldMapping> = listOf(
            CostFieldMapping("PRICE", 1, { it.price }, apply = { p, a -> p.copy(price = formatMoney(a)) }),
            CostFieldMapping("AUCTION_FEE", 2, { it.auctionFee }, apply = { p, a -> p.copy(auctionFee = formatMoney(a)) }),
            CostFieldMapping("AUCTION_PENALTY_FEE", 3, { it.auctionPenaltyFee }, apply = { p, a -> p.copy(auctionPenaltyFee = formatMoney(a)) }),
            CostFieldMapping("RECYCLE_FEE", 4, { it.recycleFee }, apply = { p, a -> p.copy(recycleFee = formatMoney(a)) }),
            CostFieldMapping("ROAD_TAX", 5, { it.roadTax }, apply = { p, a -> p.copy(roadTax = formatMoney(a)) }),
            CostFieldMapping("TAX_TOTAL", 6, { it.taxTotal }, apply = { p, a -> p.copy(taxTotal = formatMoney(a)) }),
            CostFieldMapping("TOTAL_PRICE", 7, { it.totalPrice }, apply = { p, a -> p.copy(totalPrice = formatMoney(a)) }),
            CostFieldMapping("SHIPMENT_CHARGES", 8, { it.shipmentCharges }, apply = { p, a -> p.copy(shipmentCharges = formatMoney(a)) }),
            CostFieldMapping("FREIGHT", 9, { it.freight }, apply = { p, a -> p.copy(freight = formatMoney(a)) }),
            CostFieldMapping("STORAGE_CHARGES", 10, { it.storageCharges }, apply = { p, a -> p.copy(storageCharges = formatMoney(a)) }),
            CostFieldMapping("MISC_CHARGES", 11, { it.miscCharges }, apply = { p, a -> p.copy(miscCharges = formatMoney(a)) }),
            CostFieldMapping("INSPECTION_FEE", 12, { it.inspectionFee }, apply = { p, a -> p.copy(inspectionFee = formatMoney(a)) }),
            CostFieldMapping("COMMISSION", 13, { it.commission }, apply = { p, a -> p.copy(commission = formatMoney(a)) }),
            CostFieldMapping("RIXO_PRICE", 14, { it.rixoPrice }, apply = { p, a -> p.copy(rixoPrice = formatMoney(a)) }),
            CostFieldMapping("REPAIR_CHARGES", 15, { it.repairCharges }, apply = { p, a -> p.copy(repairCharges = formatMoney(a)) }),
            CostFieldMapping("PROFIT", 16, { null }, { it.profit }, apply = { p, a -> p.copy(profit = a) }),
        )

        /** Dropped purchase columns — hydrate from lines only (total_price stays on purchases). */
        private val READ_MAPPINGS: List<CostFieldMapping> =
            COST_FIELD_MAPPINGS.filter { it.costCode != "TOTAL_PRICE" }

        fun parseMoneyString(raw: String?): BigDecimal? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
            if (cleaned.isEmpty()) return null
            return try {
                BigDecimal(cleaned)
            } catch (_: NumberFormatException) {
                null
            }
        }

        fun amountFromPurchase(mapping: CostFieldMapping, purchase: Purchase): BigDecimal? {
            mapping.decimalValue(purchase)?.let { return it }
            return parseMoneyString(mapping.stringValue(purchase))
        }

        private fun formatMoney(amount: BigDecimal): String = amount.stripTrailingZeros().toPlainString()
    }

    @Transactional(readOnly = true)
    fun applyForRead(purchase: Purchase): Purchase {
        val purchaseId = purchase.id ?: return purchase
        return applyForReadWithLines(purchase, loadLineAmounts(purchaseId))
    }

    @Transactional(readOnly = true)
    fun applyForReadBatch(purchases: List<Purchase>): List<Purchase> {
        if (purchases.isEmpty()) return purchases
        val ids = purchases.mapNotNull { it.id }
        if (ids.isEmpty()) return purchases
        val linesByPurchaseId = purchaseCostLineRepository.findByPurchaseIdIn(ids)
            .groupBy { it.purchaseId }
            .mapValues { (_, lines) -> lines.associate { it.costCode to it.amount } }
        return purchases.map { purchase ->
            val purchaseId = purchase.id
            if (purchaseId == null) purchase
            else applyForReadWithLines(purchase, linesByPurchaseId[purchaseId] ?: emptyMap())
        }
    }

    @Transactional
    fun syncFromPurchase(purchase: Purchase) {
        val purchaseId = purchase.id ?: return
        val existingByCode = purchaseCostLineRepository.findByPurchaseIdOrderBySortOrderAsc(purchaseId)
            .associateBy { it.costCode }
        val now = LocalDateTime.now()
        val desiredCodes = mutableSetOf<String>()
        val toSave = mutableListOf<PurchaseCostLine>()

        for (mapping in COST_FIELD_MAPPINGS) {
            val amount = amountFromPurchase(mapping, purchase) ?: continue
            desiredCodes.add(mapping.costCode)
            val existing = existingByCode[mapping.costCode]
            toSave.add(
                if (existing != null) {
                    existing.copy(
                        amount = amount,
                        sortOrder = mapping.sortOrder,
                        updatedAt = now,
                    )
                } else {
                    PurchaseCostLine(
                        purchaseId = purchaseId,
                        costCode = mapping.costCode,
                        amount = amount,
                        sortOrder = mapping.sortOrder,
                        createdAt = now,
                        updatedAt = now,
                    )
                },
            )
        }

        val toDelete = existingByCode.values.filter { it.costCode !in desiredCodes }
        if (toDelete.isNotEmpty()) {
            purchaseCostLineRepository.deleteAll(toDelete)
        }
        if (toSave.isNotEmpty()) {
            purchaseCostLineRepository.saveAll(toSave)
        }
    }

    @Transactional
    fun syncFromPurchases(purchases: Iterable<Purchase>) {
        for (p in purchases) {
            syncFromPurchase(p)
        }
    }

    /** API shape for GET /purchases/costs-by-chassis/{chassis} — unchanged keys. */
    @Transactional(readOnly = true)
    fun buildCostsByChassisApiMap(purchase: Purchase): Map<String, Any> {
        val merged = applyForRead(purchase)
        val lineAmounts = loadLineAmounts(purchase.id)
        return mapOf(
            "id" to (purchase.id ?: 0L),
            "chassis" to purchase.chassis,
            "carPrice" to resolveApiAmount("PRICE", lineAmounts) { parseMoneyString(merged.price) ?: BigDecimal.ZERO },
            "auctionFee" to resolveApiAmount("AUCTION_FEE", lineAmounts) { parseMoneyString(merged.auctionFee) ?: BigDecimal.ZERO },
            "auctionPenaltyFee" to resolveApiAmount("AUCTION_PENALTY_FEE", lineAmounts) {
                parseMoneyString(merged.auctionPenaltyFee) ?: BigDecimal.ZERO
            },
            "rixoPrice" to resolveApiAmount("RIXO_PRICE", lineAmounts) { parseMoneyString(merged.rixoPrice) ?: BigDecimal.ZERO },
            "shippingCharge" to resolveApiAmount("SHIPMENT_CHARGES", lineAmounts) {
                parseMoneyString(merged.shipmentCharges) ?: BigDecimal.ZERO
            },
            "freight" to resolveApiAmount("FREIGHT", lineAmounts) { parseMoneyString(merged.freight) ?: BigDecimal.ZERO },
            "inspectionFee" to resolveApiAmount("INSPECTION_FEE", lineAmounts) {
                parseMoneyString(merged.inspectionFee) ?: BigDecimal.ZERO
            },
            "repairFee" to resolveApiAmount("REPAIR_CHARGES", lineAmounts) {
                parseMoneyString(merged.repairCharges) ?: BigDecimal.ZERO
            },
            "mscCharges" to resolveApiAmount("MISC_CHARGES", lineAmounts) { parseMoneyString(merged.miscCharges) ?: BigDecimal.ZERO },
            "profit" to resolveApiAmount("PROFIT", lineAmounts) { merged.profit ?: BigDecimal.ZERO },
        )
    }

    private fun applyForReadWithLines(purchase: Purchase, lineAmounts: Map<String, BigDecimal>): Purchase {
        var merged = clearCostFields(purchase)
        if (lineAmounts.isEmpty()) return merged
        for (mapping in READ_MAPPINGS) {
            val amount = lineAmounts[mapping.costCode] ?: continue
            merged = mapping.apply(merged, amount)
        }
        return merged
    }

    private fun clearCostFields(purchase: Purchase): Purchase =
        purchase.copy(
            price = null,
            auctionFee = null,
            auctionPenaltyFee = null,
            recycleFee = null,
            roadTax = null,
            taxTotal = null,
            shipmentCharges = null,
            freight = null,
            storageCharges = null,
            miscCharges = null,
            inspectionFee = null,
            commission = null,
            rixoPrice = null,
            repairCharges = null,
            profit = null,
        )

    private fun loadLineAmounts(purchaseId: Long?): Map<String, BigDecimal> {
        if (purchaseId == null) return emptyMap()
        return purchaseCostLineRepository.findByPurchaseIdOrderBySortOrderAsc(purchaseId)
            .associate { it.costCode to it.amount }
    }

    private fun resolveApiAmount(
        costCode: String,
        lineAmounts: Map<String, BigDecimal>,
        mergedFallback: () -> BigDecimal,
    ): BigDecimal = lineAmounts[costCode] ?: mergedFallback()
}
