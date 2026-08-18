package com.automan.backend.service

import com.automan.backend.dto.RixoHistoryPageResponse
import com.automan.backend.dto.RixoHistoryRowDto
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.util.Logger
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.PageRequest
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
        for (p in candidates) {
            val id = p.id ?: continue
            if (id !in shouldConfirm && p.workflowStatus == com.automan.backend.model.WorkflowStatus.RIXO_CONFIRMED) {
                purchaseWorkflowService.setWorkflowStatus(p, com.automan.backend.model.WorkflowStatus.RIXO_REQUESTED)
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
        return enrichRows(rixoHistoryRepository.findAll(sort))
    }

    fun listRowsPage(
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): RixoHistoryPageResponse {
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        if (isRixoConfirmedSortField(sortField)) {
            val sorted = sortEnrichedByRixoConfirmed(enrichRows(rixoHistoryRepository.findAll()), sortOrder)
            return pageEnrichedRows(sorted, pageIdx, size)
        }
        val pageable = PageRequest.of(pageIdx, size, resolveRixoHistorySort(sortField, sortOrder))
        val pg = rixoHistoryRepository.findAll(pageable)
        return RixoHistoryPageResponse(
            content = enrichRows(pg.content),
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    fun searchRowsPage(
        rawQuery: String,
        page: Int,
        rawSize: Int,
        sortField: String? = null,
        sortOrder: String? = null,
    ): RixoHistoryPageResponse {
        val q = sanitizeHistorySearchToken(rawQuery)
        require(q.isNotEmpty()) { "Search text is required" }
        val pageIdx = page.coerceAtLeast(0)
        val size = rawSize.coerceIn(1, 100)
        if (isRixoConfirmedSortField(sortField)) {
            val matches = rixoHistoryRepository.searchKeyFields(q, Pageable.unpaged()).content
            val sorted = sortEnrichedByRixoConfirmed(enrichRows(matches), sortOrder)
            return pageEnrichedRows(sorted, pageIdx, size)
        }
        val pageable = PageRequest.of(pageIdx, size, resolveRixoHistorySort(sortField, sortOrder))
        val pg = rixoHistoryRepository.searchKeyFields(q, pageable)
        return RixoHistoryPageResponse(
            content = enrichRows(pg.content),
            totalElements = pg.totalElements,
            totalPages = pg.totalPages,
            page = pg.number,
            size = pg.size,
        )
    }

    /**
     * Whitelist entity columns. Enriched [rixoConfirmedDate] still falls back to `id`.
     * [rixoConfirmed] is sorted in memory after enrich (see [listRowsPage] / [searchRowsPage]).
     */
    private fun resolveRixoHistorySort(sortField: String?, sortOrder: String?): Sort {
        val dir = if (sortOrder?.trim().equals("asc", ignoreCase = true) == true) {
            Sort.Direction.ASC
        } else {
            Sort.Direction.DESC
        }
        val prop = when (sortField?.trim()?.lowercase()) {
            null, "", "id" -> "id"
            "buyingdate", "buying_date" -> "buyingDate"
            "rixocompany", "rixo_company" -> "rixoCompany"
            "message" -> "message"
            "chassis" -> "chassis"
            "createdat", "created_at" -> "createdAt"
            "rixoconfirmed", "rixo_confirmed", "rixoconfirmeddate", "rixo_confirmed_date" -> "id"
            else -> "id"
        }
        return Sort.by(dir, prop)
    }

    private fun isRixoConfirmedSortField(sortField: String?): Boolean =
        isRixoConfirmedSortFieldKey(sortField)

    private fun pageEnrichedRows(
        sorted: List<RixoHistoryRowDto>,
        pageIdx: Int,
        size: Int,
    ): RixoHistoryPageResponse {
        val total = sorted.size.toLong()
        val totalPages = if (total == 0L) 1 else ((total + size - 1) / size).toInt().coerceAtLeast(1)
        val from = (pageIdx.toLong() * size).toInt().coerceAtLeast(0)
        val content = if (from >= sorted.size) {
            emptyList()
        } else {
            sorted.subList(from, (from + size).coerceAtMost(sorted.size))
        }
        return RixoHistoryPageResponse(
            content = content,
            totalElements = total,
            totalPages = totalPages,
            page = pageIdx,
            size = size,
        )
    }

    /**
     * Builds purchase index from ONLY purchases matching chassis tokens on [rows]
     * (via [PurchaseRepository.findByChassisToken] per unique token — not findAll).
     */
    private fun enrichRows(rows: List<RixoHistory>): List<RixoHistoryRowDto> {
        if (rows.isEmpty()) return emptyList()

        val tokens = linkedSetOf<String>()
        for (row in rows) {
            tokens.addAll(parseChassisTokens(row.chassis))
        }

        val byNormalizedChassis = mutableMapOf<String, MutableList<com.automan.backend.model.Purchase>>()
        for (token in tokens) {
            for (p in purchaseRepository.findByChassisToken(token)) {
                val key = p.chassis?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT) ?: continue
                val list = byNormalizedChassis.getOrPut(key) { mutableListOf() }
                if (list.none { it.id == p.id }) list.add(p)
            }
        }

        return rows.map { e ->
            val matched = matchedPurchasesForHistoryRow(e, byNormalizedChassis)
            val rixoConfirmed = isHistoryRowRixoConfirmedStrict(matched)
            RixoHistoryRowDto(
                id = e.id ?: 0L,
                buyingDate = e.buyingDate?.toString(),
                rixoCompany = e.rixoCompany,
                message = e.message,
                chassis = e.chassis,
                rixoConfirmed = rixoConfirmed,
                rixoConfirmedDate = if (rixoConfirmed) historyRowRixoConfirmedAtIso(matched) else null,
                hasBookingRequested = matchedPurchasesHaveBookingRequested(matched),
            )
        }
    }

    private fun sanitizeHistorySearchToken(raw: String): String =
        raw.trim().replace("%", "").replace("_", "").take(120)

    /**
     * Purchases matched by chassis segments for a history row.
     * Empty if no segments, or if any segment matches nothing (same as legacy matchedPurchaseIdsForRixoHistoryRow).
     */
    private fun matchedPurchasesForHistoryRow(
        entity: RixoHistory,
        byNormalizedChassis: Map<String, List<com.automan.backend.model.Purchase>>,
    ): List<com.automan.backend.model.Purchase> {
        val segmentTokens = parseChassisTokens(entity.chassis)
        if (segmentTokens.isEmpty()) return emptyList()
        val allMatched = linkedMapOf<Long, com.automan.backend.model.Purchase>()
        for (segment in segmentTokens) {
            val found = linkedMapOf<Long, com.automan.backend.model.Purchase>()
            for (expanded in expandTokenSet(setOf(segment))) {
                for (p in byNormalizedChassis[expanded].orEmpty()) {
                    p.id?.let { found[it] = p }
                }
            }
            if (found.isEmpty()) return emptyList()
            allMatched.putAll(found)
        }
        return allMatched.values.toList()
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

    private fun isHistoryRowRixoConfirmedStrict(
        matched: List<com.automan.backend.model.Purchase>,
    ): Boolean {
        if (matched.isEmpty()) return false
        return matched.all { PurchaseWorkflowService.isRixoConfirmedForBooking(it) }
    }

    private fun matchedPurchasesHaveBookingRequested(
        matched: List<com.automan.backend.model.Purchase>,
    ): Boolean = matched.any { PurchaseWorkflowService.isBookingRequested(it) }

    private fun historyRowRixoConfirmedAtIso(
        matched: List<com.automan.backend.model.Purchase>,
    ): String? {
        var maxTs: LocalDateTime? = null
        for (p in matched) {
            if (!PurchaseWorkflowService.isRixoConfirmedForBooking(p)) continue
            val u = p.updatedAt
            if (maxTs == null || u.isAfter(maxTs)) maxTs = u
        }
        return maxTs?.toString()
    }

    /**
     * Strict row-level Rixo Confirmed for UI:
     * - Blank / no chassis segments → false.
     * - Each segment’s expanded tokens must match at least one purchase; otherwise false.
     * - Every purchase matched for any segment must have [Purchase.rixoConfirmed] truthy.
     *
     * Kept for unit tests / single-row callers; list path uses the batched in-memory equivalent.
     */
    internal fun computeHistoryRowRixoConfirmedStrict(entity: RixoHistory): Boolean {
        val allMatchedPurchaseIds = matchedPurchaseIdsForRixoHistoryRow(entity)
        if (allMatchedPurchaseIds.isEmpty()) return false

        for (purchaseId in allMatchedPurchaseIds) {
            val p = purchaseRepository.findById(purchaseId).orElse(null) ?: return false
            if (!PurchaseWorkflowService.isRixoConfirmedForBooking(p)) return false
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
            if (PurchaseWorkflowService.isBookingRequested(p)) return true
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
            if (!PurchaseWorkflowService.isRixoConfirmedForBooking(p)) continue
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
        val t0 = System.nanoTime()
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
        val tChassis = System.nanoTime()

        val newTokens = chassisJoined.split(';')
            .mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() }?.uppercase(Locale.ROOT) }
            .toSet()

        val existing = findExistingRowForChassisOverlap(newTokens)
        val oldTokens = existing?.let { parseChassisTokens(it.chassis) }?.toSet() ?: emptySet()
        val tOverlap = System.nanoTime()

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
        val tSave = System.nanoTime()

        // Full-history sync is expensive. Only needed when chassis leave a history row
        // (pure insert / add-only update cannot uncover a previously confirmed purchase).
        val removedTokens = oldTokens - newTokens
        val clearedIds = if (removedTokens.isEmpty()) {
            emptySet()
        } else {
            syncRixoConfirmedWithAllHistory()
        }
        val tSync = System.nanoTime()

        val touchedIds = purchaseIdsForExpandedTokens(expandTokenSet(oldTokens + newTokens))
        purchaseWorkflowService.recomputeByPurchaseIds(touchedIds + clearedIds)
        val tEnd = System.nanoTime()

        fun ms(a: Long, b: Long) = (b - a) / 1_000_000.0
        Logger.log(
            "RixoHistory.saveFromTransport ids=${selectedIds.size} overlap=${existing != null} " +
                "removedTokens=${removedTokens.size} syncRan=${removedTokens.isNotEmpty()} " +
                "ms chassis=${"%.1f".format(ms(t0, tChassis))} " +
                "overlap=${"%.1f".format(ms(tChassis, tOverlap))} " +
                "save=${"%.1f".format(ms(tOverlap, tSave))} " +
                "sync=${"%.1f".format(ms(tSave, tSync))} " +
                "recompute=${"%.1f".format(ms(tSync, tEnd))} " +
                "total=${"%.1f".format(ms(t0, tEnd))}",
        )
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
        for ((_, p) in purchaseById) {
            if (PurchaseWorkflowService.isRixoConfirmedForBooking(p)) continue
            purchaseWorkflowService.setWorkflowStatus(p, com.automan.backend.model.WorkflowStatus.RIXO_CONFIRMED)
            updated += 1
        }

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
                if (PurchaseWorkflowService.isBookingRequested(p)) {
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
        for (id in candidatePurchaseIds) {
            if (id !in stillCover) {
                val p = purchaseRepository.findById(id).orElse(null) ?: continue
                purchaseWorkflowService.setWorkflowStatus(p, com.automan.backend.model.WorkflowStatus.PURCHASED)
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

    companion object {
        internal fun isRixoConfirmedSortFieldKey(sortField: String?): Boolean {
            val k = sortField?.trim()?.lowercase().orEmpty()
            return k == "rixoconfirmed" || k == "rixo_confirmed"
        }

        /** desc / default = confirmed first; asc = unconfirmed first; ties by id descending. */
        internal fun sortEnrichedByRixoConfirmed(
            rows: List<RixoHistoryRowDto>,
            sortOrder: String?,
        ): List<RixoHistoryRowDto> {
            val confirmedFirst = !sortOrder?.trim().equals("asc", ignoreCase = true)
            return rows.sortedWith(
                compareBy<RixoHistoryRowDto> { row ->
                    val unconfirmedRank = if (row.rixoConfirmed) 0 else 1
                    if (confirmedFirst) unconfirmedRank else 1 - unconfirmedRank
                }.thenByDescending { it.id },
            )
        }
    }
}
