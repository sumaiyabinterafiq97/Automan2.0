package com.automan.backend.util

import java.util.Locale

/**
 * Normalizes purchase/history chassis tokens for indexed equality lookups.
 * Matches legacy JPQL: `LOWER(TRIM(COALESCE(p.chassis, '')))` with NOT NULL [Purchase.chassis].
 */
object ChassisKeyNormalizer {
    fun normalize(raw: String?): String = raw?.trim()?.lowercase(Locale.ROOT) ?: ""

    fun normalizeUpperDisplayKey(raw: String?): String =
        raw?.trim()?.uppercase(Locale.ROOT) ?: ""
}
