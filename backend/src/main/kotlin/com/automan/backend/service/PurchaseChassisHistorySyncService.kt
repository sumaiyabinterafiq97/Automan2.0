package com.automan.backend.service

import com.automan.backend.dto.InvoiceChassisSyncItemDto
import com.automan.backend.dto.PurchaseChassisHistorySyncPreviewDto
import com.automan.backend.dto.RixoChassisSyncItemDto
import com.automan.backend.dto.ShippingChassisSyncItemDto
import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.util.ChassisListTextUtils
import com.automan.backend.util.PurchaseDateParseUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Locale

@Service
class PurchaseChassisHistorySyncService(
    private val purchaseRepository: PurchaseRepository,
    private val rixoHistoryRepository: RixoHistoryRepository,
    private val shippingHistoryRepository: ShippingHistoryRepository,
    private val invoiceHistoryLineRepository: InvoiceHistoryLineRepository,
    private val invoiceHistoryRepository: InvoiceHistoryRepository,
) {
    data class SyncPlan(
        val rixo: List<RixoChassisSyncItemDto>,
        val shipping: List<ShippingChassisSyncItemDto>,
        val invoice: List<InvoiceChassisSyncItemDto>,
        val blockers: List<String>,
        val skippedReason: String?,
    )

    fun preview(purchaseId: Long, newChassisRaw: String, purchaseBeforeEdit: Purchase): PurchaseChassisHistorySyncPreviewDto {
        val plan = buildPlan(purchaseId, purchaseBeforeEdit, newChassisRaw)
        return toPreviewDto(plan)
    }

    @Transactional
    fun apply(purchaseId: Long, oldChassisRaw: String, newChassisRaw: String, purchaseBeforeEdit: Purchase) {
        val plan = buildPlan(purchaseId, purchaseBeforeEdit, newChassisRaw)
        if (plan.blockers.isNotEmpty()) {
            throw IllegalArgumentException(plan.blockers.first())
        }
        if (plan.skippedReason != null) return
        val oldTrim = oldChassisRaw.trim()
        val newTrim = newChassisRaw.trim()
        if (oldTrim.isEmpty() || newTrim.isEmpty()) return
        if (oldTrim.uppercase(Locale.ROOT) == newTrim.uppercase(Locale.ROOT)) return

        for (item in plan.rixo) {
            val row = rixoHistoryRepository.findById(item.historyId).orElse(null) ?: continue
            val (next, changed) = ChassisListTextUtils.replaceFirstMatchingSegment(row.chassis, oldTrim, newTrim)
            if (!changed) continue
            if (next.isNullOrBlank()) continue
            rixoHistoryRepository.save(row.copy(chassis = next, createdAt = row.createdAt))
        }

        for (item in plan.shipping) {
            val row = shippingHistoryRepository.findById(item.historyId).orElse(null) ?: continue
            val (next, changed) = ChassisListTextUtils.replaceFirstMatchingSegment(row.chassis, oldTrim, newTrim)
            if (!changed || next.isNullOrBlank()) continue
            shippingHistoryRepository.save(row.copy(chassis = next, createdAt = row.createdAt))
        }

        for (item in plan.invoice) {
            val line = invoiceHistoryLineRepository.findById(item.lineId).orElse(null) ?: continue
            invoiceHistoryLineRepository.save(line.copy(chassis = newTrim))
        }
    }

    private fun buildPlan(purchaseId: Long, purchaseBeforeEdit: Purchase, newChassisRaw: String): SyncPlan {
        val oldTrim = purchaseBeforeEdit.chassis?.trim().orEmpty()
        val newTrim = newChassisRaw.trim()
        if (oldTrim.isEmpty() || newTrim.isEmpty()) {
            return SyncPlan(emptyList(), emptyList(), emptyList(), emptyList(), null)
        }
        if (oldTrim.uppercase(Locale.ROOT) == newTrim.uppercase(Locale.ROOT)) {
            return SyncPlan(emptyList(), emptyList(), emptyList(), emptyList(), null)
        }

        val skippedReason = singleOwnerSkippedReason(purchaseId, oldTrim, purchaseBeforeEdit)
        if (skippedReason != null) {
            return SyncPlan(emptyList(), emptyList(), emptyList(), emptyList(), skippedReason)
        }

        val rixoItems = planRixoUpdates(purchaseBeforeEdit, oldTrim, newTrim)
        val shippingItems = planShippingUpdates(oldTrim, newTrim)
        val invoiceItems = planInvoiceUpdates(oldTrim, newTrim)
        val blockers = shippingUniqueBlockers(shippingItems)

        return SyncPlan(rixoItems, shippingItems, invoiceItems, blockers, null)
    }

    private fun shippingUniqueBlockers(shippingItems: List<ShippingChassisSyncItemDto>): List<String> {
        val blockers = mutableListOf<String>()
        for (item in shippingItems) {
            val after = item.chassisAfter?.trim().orEmpty()
            if (after.isEmpty()) continue
            val existing = shippingHistoryRepository.findFirstByChassisOrderByIdDesc(after)
            if (existing != null && existing.id != item.historyId) {
                blockers.add(
                    "Shipping history already has chassis \"$after\" on row ${existing.id}. " +
                        "Choose No to save the purchase only, or use a different chassis.",
                )
                return blockers
            }
        }
        return blockers
    }

    private fun planRixoUpdates(
        purchase: Purchase,
        oldTrim: String,
        newTrim: String,
    ): List<RixoChassisSyncItemDto> {
        val token = escapeLikeToken(oldTrim)
        val candidates = rixoHistoryRepository.findChassisContaining(token)
            .filter { ChassisListTextUtils.rowContainsSegment(it.chassis, oldTrim) }
        val out = mutableListOf<RixoChassisSyncItemDto>()
        for (row in candidates) {
            if (!rixoSegmentMatchesPurchase(row, oldTrim, purchase)) continue
            val (after, changed) = ChassisListTextUtils.replaceFirstMatchingSegment(row.chassis, oldTrim, newTrim)
            if (!changed) continue
            val id = row.id ?: continue
            out.add(
                RixoChassisSyncItemDto(
                    historyId = id,
                    buyingDate = row.buyingDate?.toString(),
                    rixoCompany = row.rixoCompany,
                    oldChassis = oldTrim,
                    newChassis = newTrim,
                    chassisAfter = after,
                ),
            )
        }
        return out
    }

    private fun singleOwnerSkippedReason(
        purchaseId: Long,
        oldTrim: String,
        purchaseBeforeEdit: Purchase,
    ): String? {
        if (purchaseBeforeEdit.id != purchaseId) {
            return "Purchase identity mismatch; history will not be updated."
        }
        val beforeChassis = purchaseBeforeEdit.chassis?.trim().orEmpty()
        if (!beforeChassis.equals(oldTrim, ignoreCase = true)) {
            return "Previous chassis mismatch; history will not be updated."
        }
        val owners = purchaseRepository.findByChassisIgnoreCaseTrim(oldTrim)
        return when {
            owners.isEmpty() -> null
            owners.size > 1 ->
                "Multiple purchases share the previous chassis; history will not be updated automatically."
            owners.single().id != purchaseId ->
                "Previous chassis belongs to another purchase; history will not be updated."
            else -> null
        }
    }

    private fun rixoSegmentMatchesPurchase(row: RixoHistory, segment: String, purchase: Purchase): Boolean {
        val purchaseChassis = purchase.chassis?.trim().orEmpty()
        if (!segment.trim().equals(purchaseChassis, ignoreCase = true)) return false
        return purchaseMatchesRixoRowFilters(purchase, row)
    }

    private fun purchaseMatchesRixoRowFilters(
        p: Purchase,
        row: RixoHistory,
    ): Boolean {
        val targetDate = row.buyingDate
        if (targetDate != null) {
            val parsed = PurchaseDateParseUtils.parseToLocalDate(p.date?.trim().orEmpty())
            if (parsed != targetDate) return false
        }
        val companyFilter = row.rixoCompany?.trim()
        val undefinedCompany =
            companyFilter.isNullOrEmpty() ||
                companyFilter.equals("__RIXO_COMPANY_UNDEFINED__", ignoreCase = true) ||
                companyFilter.equals("Undefined", ignoreCase = true)
        val raw = p.rixoCompany?.trim().orEmpty()
        return if (undefinedCompany) {
            raw.isEmpty()
        } else {
            raw.equals(companyFilter, ignoreCase = true)
        }
    }

    private fun planShippingUpdates(oldTrim: String, newTrim: String): List<ShippingChassisSyncItemDto> {
        val token = escapeLikeToken(oldTrim)
        return shippingHistoryRepository.findChassisContaining(token)
            .filter { ChassisListTextUtils.rowContainsSegment(it.chassis, oldTrim) }
            .mapNotNull { row ->
                val (after, changed) = ChassisListTextUtils.replaceFirstMatchingSegment(row.chassis, oldTrim, newTrim)
                if (!changed) return@mapNotNull null
                val id = row.id ?: return@mapNotNull null
                ShippingChassisSyncItemDto(
                    historyId = id,
                    clientName = row.clientName,
                    vessel = row.vessel,
                    bookingId = row.bookingId,
                    oldChassis = oldTrim,
                    newChassis = newTrim,
                    chassisAfter = after,
                )
            }
    }

    private fun planInvoiceUpdates(oldTrim: String, newTrim: String): List<InvoiceChassisSyncItemDto> {
        return invoiceHistoryLineRepository.findByNormalizedChassis(oldTrim).mapNotNull { line ->
            val lineId = line.id ?: return@mapNotNull null
            val header = invoiceHistoryRepository.findById(line.invoiceHistoryId).orElse(null)
            InvoiceChassisSyncItemDto(
                lineId = lineId,
                invoiceNumber = header?.invoiceNumber ?: line.invoiceHistoryId.toString(),
                sortOrder = line.sortOrder,
                oldChassis = line.chassis.trim(),
                newChassis = newTrim,
            )
        }
    }

    private fun toPreviewDto(plan: SyncPlan): PurchaseChassisHistorySyncPreviewDto {
        val hasUpdates = plan.rixo.isNotEmpty() || plan.shipping.isNotEmpty() || plan.invoice.isNotEmpty()
        val canApply = plan.blockers.isEmpty() && plan.skippedReason == null && hasUpdates
        return PurchaseChassisHistorySyncPreviewDto(
            rixo = plan.rixo,
            shipping = plan.shipping,
            invoice = plan.invoice,
            canApply = canApply,
            blockers = plan.blockers,
            skippedReason = plan.skippedReason,
        )
    }

    private fun escapeLikeToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)
}
