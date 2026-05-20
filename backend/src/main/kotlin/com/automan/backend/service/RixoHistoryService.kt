package com.automan.backend.service

import com.automan.backend.dto.RixoHistoryRowDto
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.util.Locale

@Service
class RixoHistoryService(
    private val rixoHistoryRepository: RixoHistoryRepository,
    private val purchaseRepository: PurchaseRepository,
    private val purchaseWorkflowService: PurchaseWorkflowService,
    private val shippingHistoryService: ShippingHistoryService,
    private val invoiceHistoryService: InvoiceHistoryService,
) {
    data class RixoConfirmResult(
        val selectedRows: Int,
        val updatedPurchases: Int,
        val matchedChassisTokens: Int,
        val skippedRowsWithoutChassis: Int,
    )

    private fun parseChassisTokens(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(';', ',', '\n', '\r')
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.uppercase(Locale.ROOT) }
            .toSet()
    }

    private fun expandTokenSet(tokens: Set<String>): Set<String> {
        return tokens
    }

    private fun expandedTokensFromAllHistoryRows(): Set<String> {
        val tokenSet = linkedSetOf<String>()
        for (row in rixoHistoryRepository.findAll()) {
            for (t in parseChassisTokens(row.chassis)) {
                tokenSet.add(t)
            }
        }
        return tokenSet
    }

    private fun purchaseIdsForExpandedTokens(tokens: Set<String>): Set<Long> {
        val ids = linkedSetOf<Long>()
        for (token in tokens) {
            for (p in purchaseRepository.findByChassisToken(token)) {
                p.id?.let { ids.add(it) }
            }
        }
        return ids
    }

    /**
     * Clears [Purchase.rixoConfirmed] when the chassis no longer appears in any Rixo history row
     * (same token rules as confirm). Returns purchase ids whose `rixo_confirmed` was written.
     */
    fun syncRixoConfirmedWithAllHistory(): Set<Long> {
        val shouldConfirm = purchaseIdsForExpandedTokens(expandedTokensFromAllHistoryRows())
        val candidates = purchaseRepository.findPurchasesWhereRixoConfirmedPositive()
        val changed = mutableSetOf<Long>()
        val now = LocalDateTime.now()
        for (p in candidates) {
            val id = p.id ?: continue
            if (id !in shouldConfirm) {
                purchaseRepository.save(p.copy(rixoConfirmed = "FALSE", updatedAt = now))
                changed.add(id)
            }
        }
        return changed
    }

    /**
     * If any chassis in the new save overlaps an existing history row (same token, case-insensitive),
     * update that row instead of inserting a duplicate.
     */
    private fun findExistingRowForChassisOverlap(newTokens: Set<String>): RixoHistory? {
        if (newTokens.isEmpty()) return null
        val all = rixoHistoryRepository.findAll()
        return all.filter { row ->
            val rowTokens = parseChassisTokens(row.chassis)
            rowTokens.any { it in newTokens }
        }.maxByOrNull { it.id ?: 0L }
    }

    fun listAllRows(): List<RixoHistoryRowDto> {
        val sort = Sort.by(Sort.Direction.DESC, "id")
        return rixoHistoryRepository.findAll(sort).map { e ->
            RixoHistoryRowDto(
                id = e.id ?: 0L,
                buyingDate = e.buyingDate?.toString(),
                rixoCompany = e.rixoCompany,
                message = e.message,
                chassis = e.chassis,
                rixoConfirmed = computeHistoryRowRixoConfirmedStrict(e),
                rixoConfirmedDate = computeHistoryRowRixoConfirmedAtIso(e),
                hasBookingRequested = historyRowHasAnyMatchedPurchaseBookingRequested(e),
            )
        }
    }

    /** All purchase ids matched by any chassis segment on this history row; empty if any segment matches nothing. */
    private fun matchedPurchaseIdsForRixoHistoryRow(entity: RixoHistory): Set<Long> {
        val segmentTokens = parseChassisTokens(entity.chassis)
        if (segmentTokens.isEmpty()) return emptySet()
        val allMatchedPurchaseIds = linkedSetOf<Long>()
        for (segment in segmentTokens) {
            val foundIds = linkedSetOf<Long>()
            for (expanded in expandTokenSet(setOf(segment))) {
                for (p in purchaseRepository.findByChassisToken(expanded)) {
                    p.id?.let { foundIds.add(it) }
                }
            }
            if (foundIds.isEmpty()) return emptySet()
            allMatchedPurchaseIds.addAll(foundIds)
        }
        return allMatchedPurchaseIds
    }

    /**
     * Strict row-level Rixo Confirmed for UI:
     * - Blank / no chassis segments → false.
     * - Each segment’s expanded tokens must match at least one purchase; otherwise false.
     * - Every purchase matched for any segment must have [Purchase.rixoConfirmed] truthy.
     */
    internal fun computeHistoryRowRixoConfirmedStrict(entity: RixoHistory): Boolean {
        val allMatchedPurchaseIds = matchedPurchaseIdsForRixoHistoryRow(entity)
        if (allMatchedPurchaseIds.isEmpty()) return false

        for (purchaseId in allMatchedPurchaseIds) {
            val p = purchaseRepository.findById(purchaseId).orElse(null) ?: return false
            if (!PurchaseWorkflowService.isRixoFlagTrue(p.rixoConfirmed)) return false
        }
        return true
    }

    /**
     * True when at least one purchase matched for any chassis segment on this row has [Purchase.bookingRequested].
     */
    internal fun historyRowHasAnyMatchedPurchaseBookingRequested(entity: RixoHistory): Boolean {
        val purchaseIds = matchedPurchaseIdsForRixoHistoryRow(entity)
        if (purchaseIds.isEmpty()) return false
        for (purchaseId in purchaseIds) {
            val p = purchaseRepository.findById(purchaseId).orElse(null) ?: continue
            if (p.bookingRequested) return true
        }
        return false
    }

    /** ISO-8601 local date-time of the latest [Purchase.updatedAt] among matched Rixo-confirmed purchases, or null. */
    private fun computeHistoryRowRixoConfirmedAtIso(entity: RixoHistory): String? {
        if (!computeHistoryRowRixoConfirmedStrict(entity)) return null
        val allMatchedPurchaseIds = matchedPurchaseIdsForRixoHistoryRow(entity)
        var maxTs: LocalDateTime? = null
        for (purchaseId in allMatchedPurchaseIds) {
            val p = purchaseRepository.findById(purchaseId).orElse(null) ?: continue
            if (!PurchaseWorkflowService.isRixoFlagTrue(p.rixoConfirmed)) continue
            val u = p.updatedAt
            if (maxTs == null || u.isAfter(maxTs)) maxTs = u
        }
        return maxTs?.toString()
    }

    /**
     * Persists one row: [transportData] extraMessage → message; chassis from purchases in [selectedIds] order.
     */
    @Transactional
    fun saveFromTransport(selectedIds: List<Long>, transportData: Map<String, String>) {
        val buyingDateStr = transportData["buyingDate"]?.trim().orEmpty()
        val buyingDate: LocalDate? = if (buyingDateStr.isNotEmpty()) {
            try {
                LocalDate.parse(buyingDateStr)
            } catch (_: DateTimeParseException) {
                null
            }
        } else {
            null
        }

        val rixoCompany = transportData["rixoCompany"]?.trim()?.takeIf { it.isNotEmpty() }
        val message = transportData["extraMessage"]?.trim()?.takeIf { it.isNotEmpty() }

        val chassisJoined = selectedIds.mapNotNull { id ->
            purchaseRepository.findById(id).orElse(null)?.chassis?.trim()?.takeIf { it.isNotEmpty() }
        }.joinToString(";")

        val newTokens = chassisJoined.split(';')
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.uppercase(Locale.ROOT) }
            .toSet()

        val existing = findExistingRowForChassisOverlap(newTokens)
        val oldTokens = existing?.let { parseChassisTokens(it.chassis) }?.toSet() ?: emptySet()

        if (existing != null && existing.id != null) {
            val updated = existing.copy(
                buyingDate = buyingDate,
                rixoCompany = rixoCompany,
                message = message,
                chassis = chassisJoined.takeIf { it.isNotEmpty() },
                createdAt = existing.createdAt,
            )
            rixoHistoryRepository.save(updated)
        } else {
            val row = RixoHistory(
                buyingDate = buyingDate,
                rixoCompany = rixoCompany,
                message = message,
                chassis = chassisJoined.takeIf { it.isNotEmpty() },
            )
            rixoHistoryRepository.save(row)
        }

        val clearedIds = syncRixoConfirmedWithAllHistory()
        val touchedIds = purchaseIdsForExpandedTokens(expandTokenSet(oldTokens + newTokens))
        purchaseWorkflowService.recomputeByPurchaseIds(touchedIds + clearedIds)
    }

    /**
     * Marks `purchases.rixo_confirmed = TRUE` for all cars mapped by chassis tokens in selected history rows.
     * Token match rule: exact chassis or token as head (`TOKEN-*`), case-insensitive.
     */
    @Transactional
    fun confirmSelectedHistoryRows(historyIds: List<Long>): RixoConfirmResult {
        if (historyIds.isEmpty()) {
            return RixoConfirmResult(
                selectedRows = 0,
                updatedPurchases = 0,
                matchedChassisTokens = 0,
                skippedRowsWithoutChassis = 0,
            )
        }

        val rows = rixoHistoryRepository.findAllById(historyIds)
        val tokenSet = linkedSetOf<String>()
        var skippedNoChassis = 0
        for (row in rows) {
            val tokens = parseChassisTokens(row.chassis)
            if (tokens.isEmpty()) skippedNoChassis += 1
            tokenSet.addAll(tokens)
        }
        val expandedTokens = expandTokenSet(tokenSet)

        val purchaseById = linkedMapOf<Long, com.automan.backend.model.Purchase>()
        for (token in expandedTokens) {
            val found = purchaseRepository.findByChassisToken(token)
            for (p in found) {
                val id = p.id ?: continue
                purchaseById[id] = p
            }
        }

        var updated = 0
        val now = LocalDateTime.now()
        for ((_, p) in purchaseById) {
            val old = p.rixoConfirmed?.trim()?.uppercase(Locale.ROOT) ?: ""
            if (old == "TRUE" || old == "1") continue
            purchaseRepository.save(
                p.copy(
                    rixoConfirmed = "TRUE",
                    updatedAt = now,
                ),
            )
            updated += 1
        }

        purchaseWorkflowService.recomputeByPurchaseIds(purchaseById.keys)

        return RixoConfirmResult(
            selectedRows = rows.size,
            updatedPurchases = updated,
            matchedChassisTokens = expandedTokens.size,
            skippedRowsWithoutChassis = skippedNoChassis,
        )
    }

    @Transactional
    fun deleteHistoryRow(id: Long): Boolean {
        val row = rixoHistoryRepository.findById(id).orElse(null) ?: return false
        if (historyRowHasAnyMatchedPurchaseBookingRequested(row)) {
            throw IllegalArgumentException(
                "Cannot delete: at least one car on this Rixo history row is already booking requested.",
            )
        }
        val tokens = expandTokenSet(parseChassisTokens(row.chassis))
        val affectedPurchaseIds = purchaseIdsForExpandedTokens(tokens)
        rixoHistoryRepository.deleteById(id)
        val clearedIds = syncRixoConfirmedWithAllHistory()
        resetPurchasedWorkflowWhenNotCovered(affectedPurchaseIds)
        cascadeDeleteShippingAndInvoiceHistoryFullyCovered(parseChassisTokens(row.chassis))
        purchaseWorkflowService.recomputeByPurchaseIds(affectedPurchaseIds + clearedIds)
        return true
    }

    /**
     * Deletes all given history rows in one transaction.
     * Purchases matched by chassis on those rows are reset to base “Purchased” when no remaining
     * Rixo history row covers them (Rixo flags false, booking / invoice confirmation cleared).
     */
    @Transactional
    fun deleteHistoryRows(historyIds: List<Long>): Int {
        val rows = rixoHistoryRepository.findAllById(historyIds.distinct())
        if (rows.isEmpty()) return 0
        val affectedPurchaseIds = linkedSetOf<Long>()
        for (row in rows) {
            val tokens = expandTokenSet(parseChassisTokens(row.chassis))
            affectedPurchaseIds.addAll(purchaseIdsForExpandedTokens(tokens))
        }
        val idsToDelete = rows.mapNotNull { it.id }
        val chassisCoverFromDeletedRows = rows.flatMap { parseChassisTokens(it.chassis) }.distinct()
        rixoHistoryRepository.deleteAllById(idsToDelete)
        val clearedIds = syncRixoConfirmedWithAllHistory()
        resetPurchasedWorkflowWhenNotCovered(affectedPurchaseIds)
        cascadeDeleteShippingAndInvoiceHistoryFullyCovered(chassisCoverFromDeletedRows)
        purchaseWorkflowService.recomputeByPurchaseIds(affectedPurchaseIds + clearedIds)
        return idsToDelete.size
    }

    /**
     * Removes the first chassis token matching [chassisTokenRaw] from row [historyId] (delimiter: `;`, `,`, newline).
     * If no chassis segments remain, the entire history row is deleted.
     * Purchases mapped to this token are rolled back to “Purchased” when they no longer appear in any row
     * (same rules as sync + full reset when uncovered).
     */
    @Transactional
    fun removeChassisTokenFromHistoryRow(historyId: Long, chassisTokenRaw: String): Map<String, Any> {
        val row = rixoHistoryRepository.findById(historyId).orElse(null)
            ?: throw IllegalArgumentException("Rixo history row not found")
        val tokenClean = chassisTokenRaw.trim()
        if (tokenClean.isEmpty()) throw IllegalArgumentException("chassisToken is required")

        val expandedRemoved = expandTokenSet(setOf(tokenClean.uppercase(Locale.ROOT)))
        val affectedPurchaseIds = linkedSetOf<Long>()
        for (token in expandedRemoved) {
            for (p in purchaseRepository.findByChassisToken(token)) {
                if (p.bookingRequested) {
                    throw IllegalArgumentException(
                        "Cannot remove: this car is already booking requested.",
                    )
                }
                p.id?.let { affectedPurchaseIds.add(it) }
            }
        }

        val (newChassis, removed) = removeFirstMatchingChassisOccurrence(row.chassis, tokenClean)
        if (!removed) throw IllegalArgumentException("Chassis token not found in this history row")

        when {
            newChassis.isNullOrBlank() -> rixoHistoryRepository.deleteById(historyId)
            else -> rixoHistoryRepository.save(row.copy(chassis = newChassis, createdAt = row.createdAt))
        }

        val clearedIds = syncRixoConfirmedWithAllHistory()
        resetPurchasedWorkflowWhenNotCovered(affectedPurchaseIds)
        cascadeDeleteShippingAndInvoiceHistoryFullyCovered(expandedRemoved)
        purchaseWorkflowService.recomputeByPurchaseIds(affectedPurchaseIds + clearedIds)

        return mapOf(
            "deletedRow" to newChassis.isNullOrBlank(),
            "remainingChassis" to (newChassis ?: ""),
        )
    }

    /**
     * Removes shipping_history / invoice_history rows whose chassis lists are fully contained in the set of
     * [chassisCoverTokens] from Rixo history removal (trim/case-insensitive). Rows that still reference other
     * chassis are kept.
     */
    private fun cascadeDeleteShippingAndInvoiceHistoryFullyCovered(chassisCoverTokens: Collection<String>) {
        if (chassisCoverTokens.isEmpty()) return
        shippingHistoryService.deleteShippingRowsFullyCoveredByChassis(chassisCoverTokens)
        invoiceHistoryService.deleteInvoicesFullyCoveredByChassis(chassisCoverTokens)
    }

    /**
     * When chassis no longer appears in any Rixo history row, roll purchases back to “Purchased”:
     * clear Rixo flags and downstream booking / invoice confirmation.
     */
    private fun resetPurchasedWorkflowWhenNotCovered(candidatePurchaseIds: Set<Long>) {
        val stillCover = purchaseIdsForExpandedTokens(expandedTokensFromAllHistoryRows())
        val now = LocalDateTime.now()
        for (id in candidatePurchaseIds) {
            if (id !in stillCover) {
                val p = purchaseRepository.findById(id).orElse(null) ?: continue
                purchaseRepository.save(
                    p.copy(
                        rixoRequested = "FALSE",
                        rixoConfirmed = "FALSE",
                        bookingRequested = false,
                        invoiceConfirmed = false,
                        updatedAt = now,
                    ),
                )
            }
        }
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
