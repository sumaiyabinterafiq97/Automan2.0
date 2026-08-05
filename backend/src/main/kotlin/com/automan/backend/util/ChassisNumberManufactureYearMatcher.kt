package com.automan.backend.util

/**
 * Matches a purchase chassis suffix against chassis-map `chassis_number` pairs.
 *
 * Stored format (semicolon-delimited):
 * - Exact: `67H:2019`
 * - Inclusive numeric range: `187892~189709:2023`
 */
object ChassisNumberManufactureYearMatcher {

    fun matchYear(pairsRaw: String?, chassisNumber: String?): String? {
        if (pairsRaw.isNullOrBlank() || chassisNumber.isNullOrBlank()) return null
        val normalizedNumber = chassisNumber.trim()
        if (normalizedNumber.isBlank()) return null

        val suffixLong = if (normalizedNumber.matches(Regex("""^\d+$"""))) {
            normalizedNumber.toLongOrNull()
        } else {
            null
        }

        val pairs = pairsRaw.split(";").map { it.trim() }.filter { it.isNotBlank() }
        for (pair in pairs) {
            val colonIdx = pair.lastIndexOf(':')
            if (colonIdx <= 0) continue
            val numberToken = pair.substring(0, colonIdx).trim()
            val yearToken = pair.substring(colonIdx + 1).trim()
            if (!yearToken.matches(Regex("""\d{4}"""))) continue

            val tildeIdx = numberToken.indexOf('~')
            if (tildeIdx > 0) {
                if (suffixLong == null) continue
                val fromStr = numberToken.substring(0, tildeIdx).trim()
                val toStr = numberToken.substring(tildeIdx + 1).trim()
                if (!fromStr.matches(Regex("""^\d+$""")) || !toStr.matches(Regex("""^\d+$"""))) continue
                val from = fromStr.toLongOrNull() ?: continue
                val to = toStr.toLongOrNull() ?: continue
                val lo = minOf(from, to)
                val hi = maxOf(from, to)
                if (suffixLong in lo..hi) return yearToken
            } else if (numberToken.equals(normalizedNumber, ignoreCase = true)) {
                return yearToken
            }
        }
        return null
    }

    /** Exact suffix tokens only — range tokens (`from~to`) are omitted for dropdowns. */
    fun extractExactTokens(pairsRaw: String?): List<String> {
        if (pairsRaw.isNullOrBlank()) return emptyList()
        return pairsRaw.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val colonIdx = pair.lastIndexOf(':')
                val num = if (colonIdx > 0) pair.substring(0, colonIdx).trim() else pair.trim()
                if (num.isBlank() || num.contains('~')) null else num
            }
            .filter { it.isNotBlank() }
    }
}
