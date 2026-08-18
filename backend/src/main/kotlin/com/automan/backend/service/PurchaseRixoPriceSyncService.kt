package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.Logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * When a [rixo_mapping] price changes, update matching purchases' RIXO_PRICE cost line
 * and apply the same delta to [Purchase.totalPrice].
 */
@Service
class PurchaseRixoPriceSyncService(
    private val purchaseRepository: PurchaseRepository,
    private val purchaseCostLineService: PurchaseCostLineService,
    private val purchaseExtendedAttributesService: PurchaseExtendedAttributesService,
    private val purchaseVehicleOverrideService: PurchaseVehicleOverrideService,
) {
    data class SyncResult(val updatedCount: Int)

    @Transactional
    fun syncIfPriceChanged(before: RixoMapping?, after: RixoMapping): SyncResult {
        val newAmount = PurchaseCostLineService.parseMoneyString(after.rixoPrice) ?: return SyncResult(0)
        val oldAmount = PurchaseCostLineService.parseMoneyString(before?.rixoPrice)
        if (oldAmount != null && oldAmount.compareTo(newAmount) == 0) return SyncResult(0)
        return SyncResult(applyMappingPriceToMatchingPurchases(after, newAmount))
    }

    @Transactional
    fun backfillStaleFromMappings(mappings: Iterable<RixoMapping>): Int {
        var updated = 0
        for (mapping in mappings) {
            val amount = PurchaseCostLineService.parseMoneyString(mapping.rixoPrice) ?: continue
            updated += applyMappingPriceToMatchingPurchases(mapping, amount)
        }
        return updated
    }

    private fun applyMappingPriceToMatchingPurchases(mapping: RixoMapping, newAmount: BigDecimal): Int {
        val auction = mapping.auctionName?.trim().orEmpty()
        if (auction.isEmpty()) return 0
        val candidates = purchaseRepository.findByAuctionHouseIgnoreCaseTrim(auction)
        if (candidates.isEmpty()) return 0
        var updated = 0
        for (raw in candidates) {
            val hydrated = hydrateForMatch(raw)
            if (!matchesMapping(hydrated, mapping)) continue
            val oldAmount = PurchaseCostLineService.parseMoneyString(hydrated.rixoPrice)
            if (oldAmount != null && oldAmount.compareTo(newAmount) == 0) continue
            val formattedRixo = formatMoney(newAmount)
            val newTotal = adjustTotal(hydrated.totalPrice, oldAmount ?: BigDecimal.ZERO, newAmount)
            val patched = hydrated.copy(
                rixoPrice = formattedRixo,
                totalPrice = newTotal ?: hydrated.totalPrice,
            )
            purchaseRepository.save(patched)
            purchaseCostLineService.syncFromPurchase(patched)
            updated++
            Logger.debug(
                "Synced RIXO_PRICE for purchase ${patched.id} chassis=${patched.chassis}: " +
                    "${oldAmount ?: BigDecimal.ZERO} → $newAmount",
            )
        }
        return updated
    }

    private fun hydrateForMatch(purchase: Purchase): Purchase {
        val withExtended = purchaseExtendedAttributesService.applyForRead(purchase)
        val withVehicle = purchaseVehicleOverrideService.applyForRead(withExtended)
        return purchaseCostLineService.applyForRead(withVehicle)
    }

    internal fun matchesMapping(purchase: Purchase, mapping: RixoMapping): Boolean {
        if (!tokenMatch(purchase.auctionHouse, mapping.auctionName)) return false
        if (!tokenMatch(purchase.stockLocation, mapping.stockLocation)) return false
        if (!tokenMatch(purchase.rixoCompany, mapping.rixoCompany)) return false
        val mapVenue = mapping.venueId?.trim().orEmpty()
        if (mapVenue.isNotEmpty() && !tokenMatch(purchase.venueId, mapVenue)) return false
        val mapPol = mapping.pol?.trim().orEmpty()
        if (mapPol.isNotEmpty() && !tokenMatch(purchase.pol, mapPol)) return false
        val mapVt = mapping.supportedVehicleType?.trim().orEmpty()
        if (mapVt.isNotEmpty() && mapVt != "-" && !tokenMatch(purchase.shipmentSize, mapVt)) return false
        return true
    }

    internal fun tokenMatch(rowVal: String?, selVal: String?): Boolean {
        val sel = selVal?.trim().orEmpty()
        if (sel.isEmpty()) return true
        val tokens = splitTokens(rowVal)
        val want = sel.lowercase()
        return tokens.any { it.lowercase() == want }
    }

    private fun splitTokens(raw: String?): List<String> {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return emptyList()
        val parts = t.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        return parts.ifEmpty { listOf(t) }
    }

    private fun adjustTotal(totalRaw: String?, oldRixo: BigDecimal, newRixo: BigDecimal): String? {
        val total = PurchaseCostLineService.parseMoneyString(totalRaw) ?: return null
        return formatMoney(total.add(newRixo.subtract(oldRixo)))
    }

    private fun formatMoney(amount: BigDecimal): String = amount.stripTrailingZeros().toPlainString()
}
