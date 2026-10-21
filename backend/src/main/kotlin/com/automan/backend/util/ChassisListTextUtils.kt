package com.automan.backend.util

import java.util.Locale

object ChassisListTextUtils {
    private val delimiters = charArrayOf(';', ',', '\n', '\r')

    fun parseSegments(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val seen = linkedSetOf<String>()
        val out = mutableListOf<String>()
        for (part in raw.split(*delimiters)) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            if (!seen.add(trimmed.uppercase(Locale.ROOT))) continue
            out.add(trimmed)
        }
        return out
    }

    fun segmentKeys(raw: String?): Set<String> =
        parseSegments(raw).map { it.uppercase(Locale.ROOT) }.toSet()

    /**
     * Replaces the first segment equal to [oldToken] (case-insensitive) with [newToken].
     * If [newToken] is already present, removes [oldToken] only (dedupe).
     * Returns (newJoined, changed).
     */
    fun replaceFirstMatchingSegment(raw: String?, oldToken: String, newToken: String): Pair<String?, Boolean> {
        if (raw.isNullOrBlank()) return Pair(raw, false)
        val wantOld = oldToken.trim()
        val wantNew = newToken.trim()
        if (wantOld.isEmpty() || wantNew.isEmpty()) return Pair(raw, false)
        val parts = raw.split(*delimiters)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        if (parts.isEmpty()) return Pair(null, false)
        val removeIdx = parts.indexOfFirst { it.uppercase(Locale.ROOT) == wantOld.uppercase(Locale.ROOT) }
        if (removeIdx < 0) return Pair(raw, false)
        val newKey = wantNew.uppercase(Locale.ROOT)
        val alreadyHasNew = parts.any { it.uppercase(Locale.ROOT) == newKey }
        parts.removeAt(removeIdx)
        if (!alreadyHasNew) {
            parts.add(removeIdx.coerceAtMost(parts.size), wantNew)
        }
        if (parts.isEmpty()) return Pair(null, true)
        return Pair(parts.joinToString(";"), true)
    }

    fun rowContainsSegment(raw: String?, token: String): Boolean {
        val key = token.trim().uppercase(Locale.ROOT)
        if (key.isEmpty()) return false
        return segmentKeys(raw).contains(key)
    }
}
