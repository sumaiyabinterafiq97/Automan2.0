package com.automan.backend.service

/**
 * Pure POL uniqueness helpers for [RixoMappingService] / controller validation.
 * One stock location should have at most one distinct non-blank POL going forward.
 */
object RixoMappingPolRules {
    data class PolConflict(
        val stockLocation: String,
        val pols: List<String>,
        val blankPolRowCount: Int,
    )

    fun normalizePol(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    fun isBlankStock(raw: String?): Boolean {
        val t = raw?.trim().orEmpty()
        return t.isEmpty() || t == "-"
    }

    fun distinctPols(rawPols: Collection<String?>): List<String> =
        rawPols
            .mapNotNull { normalizePol(it) }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** Returns the POL only when exactly one distinct non-blank value exists. */
    fun resolveUnique(rawPols: Collection<String?>): String? {
        val distinct = distinctPols(rawPols)
        return if (distinct.size == 1) distinct[0] else null
    }

    /**
     * Hard-map fallback for RPM autofill: only when [derivePol] yields a single token
     * (e.g. AQUA LOGISTICS → YOKOHAMA). Multi-token results like KLC are skipped.
     */
    fun singleTokenDerivedPol(stockLocation: String, derivePol: (String) -> String?): String? {
        if (isBlankStock(stockLocation)) return null
        val derived = derivePol(stockLocation)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (derived.contains(';') || derived.contains(',')) return null
        return derived
    }

    /**
     * Reject adding a *new* distinct POL when the stock already has a different one.
     * Same POL (case-insensitive) is allowed. Blank requested POL → no check.
     */
    fun rejectSecondPol(existingPols: Collection<String?>, requestedPol: String?): String? {
        val requested = normalizePol(requestedPol) ?: return null
        val existing = distinctPols(existingPols)
        if (existing.isEmpty()) return null
        if (existing.any { it.equals(requested, ignoreCase = true) }) return null
        val shown = existing.joinToString(", ")
        return "This stock location already has POL $shown. Only one POL per stock location is allowed."
    }

    /**
     * @param rows pairs of (stockLocation, pol) — blank/`-` stocks ignored
     */
    fun conflictStocks(rows: Collection<Pair<String?, String?>>): List<PolConflict> {
        data class Acc(val displayName: String, val pols: MutableSet<String>, var blankCount: Int)
        val byKey = linkedMapOf<String, Acc>()
        for ((stockRaw, polRaw) in rows) {
            if (isBlankStock(stockRaw)) continue
            val stock = stockRaw!!.trim()
            val key = stock.lowercase()
            val acc = byKey.getOrPut(key) { Acc(displayName = stock, pols = linkedSetOf(), blankCount = 0) }
            val pol = normalizePol(polRaw)
            if (pol == null) acc.blankCount++
            else if (acc.pols.none { it.equals(pol, ignoreCase = true) }) acc.pols.add(pol)
        }
        return byKey.values
            .filter { it.pols.size > 1 }
            .map {
                PolConflict(
                    stockLocation = it.displayName,
                    pols = it.pols.sortedBy { p -> p.lowercase() },
                    blankPolRowCount = it.blankCount,
                )
            }
            .sortedBy { it.stockLocation.lowercase() }
    }
}
