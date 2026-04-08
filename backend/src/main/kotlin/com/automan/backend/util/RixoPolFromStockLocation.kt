package com.automan.backend.util

/**
 * Derives Port of Loading (POL) for `rixo_prices` from semicolon/comma-separated stock locations.
 * Mapping matches business rules (stock yard → POL hub).
 */
object RixoPolFromStockLocation {

    /** Canonical stock location (case-insensitive key) → one or more POL tokens (order preserved per stock). */
    private val stockToPol: Map<String, List<String>> = mapOf(
        "GLOBAL KAWASAKI" to listOf("YOKOHAMA"),
        "AQUA LOGISTICS" to listOf("YOKOHAMA"),
        "GLOBAL NAGOYA" to listOf("NAGOYA"),
        "FLASHRISE" to listOf("NAGOYA"),
        "KLC" to listOf("OSAKA", "SENBOKU", "KOBE"),
        "GLOBAL HAKATA" to listOf("HAKATA"),
        "BARAKI PARKING" to emptyList(),
        "LOCAL" to emptyList(),
        /** Common in seed data alongside other yards; not in the spreadsheet but aligns with Kobe area. */
        "ECL KOBE" to listOf("KOBE"),
    )

    /**
     * Returns POL tokens joined with `;`, deduped case-insensitively (first spelling wins),
     * or null when no mapped stock tokens contribute a POL.
     */
    fun derivePol(stockLocation: String): String? {
        val trimmed = stockLocation.trim()
        if (trimmed.isEmpty() || trimmed == "-") return null
        val polByLower = linkedMapOf<String, String>()
        for (stockToken in tokenizeStock(stockLocation)) {
            val pols = stockToPol.entries.firstOrNull { it.key.equals(stockToken, ignoreCase = true) }?.value
                ?: continue
            for (p in pols) {
                val lk = p.lowercase()
                if (!polByLower.containsKey(lk)) polByLower[lk] = p
            }
        }
        return polByLower.values.joinToString(";").takeIf { it.isNotBlank() }
    }

    fun tokenizeStock(stockLocation: String): List<String> =
        stockLocation.split(';', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "-" }
}
