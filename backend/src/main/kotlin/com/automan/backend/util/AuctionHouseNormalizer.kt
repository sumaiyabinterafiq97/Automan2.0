package com.automan.backend.util

/**
 * Normalizes supplier / auction-house tokens for indexed equality lookups.
 *
 * Reference (MySQL backfill, future index query):
 * `UPPER(TRIM(COALESCE(auction_house, '')))`
 *
 * **Supported input domain:** values stored in [com.automan.backend.model.Purchase.auctionHouse]
 * — typically ASCII supplier names with ordinary spaces. Trimming matches MySQL's single-argument
 * [TRIM], which removes **U+0020 space** only at both ends (not tab, NBSP, or other Unicode
 * whitespace). Uppercasing follows MySQL 8.0 [UPPER] on utf8mb4 for common cases (including leaving
 * eszett unchanged). Do not truncate; callers must ensure normalized length fits VARCHAR(100).
 */
object AuctionHouseNormalizer {

    fun normalize(raw: String?): String {
        val trimmed = mysqlSpaceTrim(raw ?: "")
        return mysqlUpper(trimmed)
    }

    /**
     * Uppercase matching MySQL `UPPER()` on utf8mb4 for typical supplier strings.
     * MySQL 8.0 leaves eszett unchanged (`UPPER('ß')` → `'ß'`) while [String.uppercase] maps to `SS`.
     */
    internal fun mysqlUpper(value: String): String {
        if (value.isEmpty()) return value
        val sb = StringBuilder(value.length)
        for (ch in value) {
            sb.append(
                when (ch) {
                    'ß', 'ẞ' -> 'ß'
                    else -> ch.uppercaseChar()
                },
            )
        }
        return sb.toString()
    }

    /** MySQL `TRIM(str)` — trim ASCII space (0x20) from both ends only. */
    internal fun mysqlSpaceTrim(value: String): String {
        var start = 0
        var end = value.length
        while (start < end && value[start] == ' ') {
            start++
        }
        while (end > start && value[end - 1] == ' ') {
            end--
        }
        return value.substring(start, end)
    }
}
