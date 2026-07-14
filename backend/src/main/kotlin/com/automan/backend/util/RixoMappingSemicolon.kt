package com.automan.backend.util

/**
 * Shared `;` token helpers for `rixo_mapping` (normalize expand + reject on write).
 */
object RixoMappingSemicolon {
    const val REJECT_MESSAGE =
        "Use a single value only. Do not use \";\" to join multiple values. Add separate mappings instead."

    /** Normalize Unicode fullwidth / small semicolons to ASCII `;`. */
    fun normalizeSemicolons(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace('\uFF1B', ';') // fullwidth semicolon
            .replace('\uFE55', ';') // small semicolon
    }

    fun containsSemicolon(raw: String?): Boolean {
        val n = normalizeSemicolons(raw).trim()
        return n.contains(';')
    }

    /** Split a cell into non-blank tokens on `;`. */
    fun splitTokens(raw: String?): List<String> =
        normalizeSemicolons(raw)
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
