package com.automan.purchase

import com.automan.purchase.models.PurchaseResponse
import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.Date
import org.w3c.dom.HTMLInputElement

/** Invoice line amount when CNF/FOB totals are not stored on the purchase row. */
fun purchaseInvoiceLineAmountYenFromDynamic(purchase: dynamic): Double {
    val p = purchase
    val tp = p.totalPrice?.toString()?.replace("¥", "")?.replace(",", "")?.trim()?.toDoubleOrNull()
    if (tp != null && tp > 0) return tp
    return p.price?.toString()?.replace("¥", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
}

fun purchaseInvoiceLineAmountYenFromResponse(purchase: PurchaseResponse): Double {
    val tp = purchase.totalPrice?.replace("¥", "")?.replace(",", "")?.trim()?.toDoubleOrNull()
    if (tp != null && tp > 0) return tp
    return purchase.price?.replace("¥", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
}

/**
 * Escape HTML to prevent XSS attacks
 */
fun escapeHtml(text: String?): String {
    if (text == null || text.length == 0) return ""
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;")
        .replace("/", "&#x2F;")
}

/** Split on ';' with dedup by case-insensitive key (e.g. car brand chip fields joined with ';'). */
fun splitSemicolonDistinctTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = HashSet<String>()
    raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
        val key = t.uppercase()
        if (seen.add(key)) out.add(t)
    }
    return out
}

/**
 * Split master-map / list cell text for chip display.
 * Matches stored consignee/supplier lists: DB may use commas, semicolons, or newlines (see normalizeStoredListForChips).
 */
fun splitMultiValueDisplayTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = HashSet<String>()
    if (raw.trim().length == 0) return out
    raw.replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("""[,;\n]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { t ->
            val key = t.uppercase()
            if (seen.add(key)) out.add(t)
        }
    return out
}

/**
 * Bank account strings often contain commas (e.g. `CO., LTD. - SWIFT`).
 * Do not use [normalizeStoredListForChips] here — it splits on commas and creates false extra chips.
 */
fun normalizeBankInfoForChips(raw: String): String {
    if (raw.trim().length == 0) return ""
    return raw.replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("""[;\n]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(";")
}

/** Prepare stored address text for chip UI: join distinct lines split only by `;` or newlines (commas stay inside one address). */
fun normalizeMultilineSemicolonListForChips(raw: String): String {
    if (raw.trim().length == 0) return ""
    val seen = HashSet<String>()
    val out = mutableListOf<String>()
    raw.replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("""[;\n]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { t ->
            val key = t.uppercase()
            if (seen.add(key)) out.add(t)
        }
    return out.joinToString(";")
}

/** Split for display when values may contain commas (e.g. street, city in one line). Only `;` and newlines separate entries. */
fun splitSemicolonOnlyDisplayTokens(raw: String): List<String> {
    val out = mutableListOf<String>()
    val seen = HashSet<String>()
    if (raw.trim().length == 0) return out
    raw.replace("\r\n", "\n")
        .replace('\r', '\n')
        .split(Regex("""[;\n]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { t ->
            val key = t.uppercase()
            if (seen.add(key)) out.add(t)
        }
    return out
}

private fun singleValueChipSpanHtml(escapedText: String): String =
    """<span style="display:inline-block;padding:4px 12px;border-radius:9999px;font-size:13px;font-weight:500;background:#eff6ff;color:#1e40af;line-height:1.35;white-space:nowrap;max-width:100%;overflow:hidden;text-overflow:ellipsis;">$escapedText</span>"""

private fun consigneeMapShadowChipSpanHtml(escapedText: String, wrap: Boolean = false): String {
    val wrapStyle = if (wrap) "white-space:normal;word-break:break-word;" else "white-space:nowrap;overflow:hidden;text-overflow:ellipsis;"
    return """<span style="display:inline-block;padding:4px 12px;border-radius:9999px;font-size:13px;font-weight:500;background:#f8fafc;color:#111827;line-height:1.35;max-width:100%;box-shadow:0 1px 6px rgba(15,23,42,0.18);$wrapStyle">$escapedText</span>"""
}

/** Bank info variant must occupy full cell width so very long lines wrap inside the pill on desktop table view. */
private fun bankInfoShadowChipSpanHtml(escapedText: String): String =
    """<span style="display:block;width:100%;box-sizing:border-box;padding:6px 10px;border-radius:8px;font-size:12px;font-weight:500;background:#f8fafc;color:#111827;line-height:1.4;max-width:100%;white-space:normal !important;word-break:break-word;overflow-wrap:anywhere;overflow:hidden;box-shadow:0 1px 6px rgba(15,23,42,0.18);">$escapedText</span>"""

/** Consignee Map — address column: black text + subtle shadow chips (professional neutral style). */
fun formatConsigneeMapAddressChipHtml(displayText: String): String {
    if (displayText.trim().length == 0) return ""
    val esc = escapeHtml(displayText)
    return consigneeMapShadowChipSpanHtml(esc, wrap = true)
}

/** Consignee Map generic values: black text + subtle shadow for all chips, no multi-color variants. */
fun formatConsigneeMapValueChipHtml(raw: String): String {
    val tokens = splitMultiValueDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return consigneeMapShadowChipSpanHtml(escapeHtml(tokens[0]))
    val inner = tokens.joinToString("") { t ->
        consigneeMapShadowChipSpanHtml(escapeHtml(t))
    }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/** Supplier Map generic values: black text + subtle shadow for all chips, no multi-color variants. */
fun formatSupplierMapValueChipHtml(raw: String): String {
    val tokens = splitMultiValueDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return consigneeMapShadowChipSpanHtml(escapeHtml(tokens[0]))
    val inner = tokens.joinToString("") { t ->
        consigneeMapShadowChipSpanHtml(escapeHtml(t))
    }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/** Car Brands Map generic values: black text + subtle shadow for all chips, no multi-color variants. */
fun formatCarBrandMapValueChipHtml(raw: String): String {
    val tokens = splitMultiValueDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return consigneeMapShadowChipSpanHtml(escapeHtml(tokens[0]))
    val inner = tokens.joinToString("") { t ->
        consigneeMapShadowChipSpanHtml(escapeHtml(t))
    }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/** Format numeric value with commas for display (e.g., 100000 → 100,000). Used for recycle fees and similar fields. */
fun formatNumericWithCommas(raw: String): String {
    if (raw.isBlank()) return ""
    val trimmed = raw.trim()
    val sanitized = trimmed.replace(Regex("[^0-9.]"), "")
    if (sanitized.isEmpty()) return ""
    val parts = sanitized.split(".")
    val intPart = parts[0].replace(Regex("\\B(?=(\\d{3})+(?!\\d))"), ",")
    return if (parts.size > 1) "$intPart.${parts[1]}" else intPart
}

/** Format numeric value with commas and display as chip. Used for recycle fees in Car Brands Map table/cards. */
fun formatNumericValueChipHtml(raw: String): String {
    if (raw.isBlank()) return ""
    val formatted = formatNumericWithCommas(raw)
    if (formatted.isEmpty()) return ""
    return formatConsigneeMapAddressChipHtml(formatted)
}

/** Client Map generic values: black text + subtle shadow for all chips, no multi-color variants. */
fun formatClientMapValueChipHtml(raw: String): String {
    val tokens = splitMultiValueDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return consigneeMapShadowChipSpanHtml(escapeHtml(tokens[0]))
    val inner = tokens.joinToString("") { t ->
        consigneeMapShadowChipSpanHtml(escapeHtml(t))
    }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/** Purchase List — cell values: light blue chip (same as master-map single-token chips). */
fun formatPurchaseListCellChipHtml(raw: String): String {
    if (raw.trim().length == 0) return ""
    val esc = escapeHtml(raw.trim())
    return """<span class="purchase-list-cell-chip" style="display:inline-block;padding:4px 12px;border-radius:9999px;font-size:13px;font-weight:500;background:#eff6ff;color:#1e40af;line-height:1.35;max-width:100%;white-space:normal;word-break:break-word;">$esc</span>"""
}

/** Purchase List neutral chip style (standard-consignee-chip-style visual language). */
fun formatPurchaseListNeutralChipHtml(raw: String): String {
    if (raw.trim().length == 0) return ""
    val esc = escapeHtml(raw.trim())
    return """<span class="purchase-list-cell-chip" style="display:inline-block;padding:4px 12px;border-radius:9999px;font-size:13px;font-weight:500;background:#f8fafc;color:#111827;line-height:1.35;max-width:100%;white-space:normal;word-break:break-word;box-shadow:0 1px 6px rgba(15,23,42,0.18);">$esc</span>"""
}

/** Invoice History chassis column: semicolon-separated values → one neutral chip each. */
fun formatInvoiceHistoryChassisChipsHtml(raw: String): String {
    if (raw.isBlank()) return ""
    val tokens = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return formatPurchaseListNeutralChipHtml(tokens[0])
    val inner = tokens.joinToString("") { formatPurchaseListNeutralChipHtml(it) }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/** Rixo History chassis column: neutral chips only (no per-chassis remove on this page). */
fun formatRixoHistoryChassisChipsHtml(raw: String, @Suppress("UNUSED_PARAMETER") historyId: String): String =
    formatInvoiceHistoryChassisChipsHtml(raw)

/** Distinct values (e.g. grouped Shipping History column) → one neutral chip per value. */
fun formatDistinctValueChipsHtml(values: List<String>): String {
    if (values.isEmpty()) return ""
    if (values.size == 1) return formatPurchaseListNeutralChipHtml(values[0])
    val inner = values.joinToString("") { formatPurchaseListNeutralChipHtml(it) }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/**
 * Renders up to [threshold] chips, then a "See more ▾" toggle button that reveals the rest.
 * The toggle is handled by a single delegated click listener attached in [setupCollapsibleChipsDelegation].
 */
fun formatCollapsibleChipsHtml(values: List<String>, threshold: Int = 3): String {
    if (values.isEmpty()) return ""
    val visible = values.take(threshold)
    val hidden = values.drop(threshold)
    val visibleHtml = visible.joinToString("") { formatPurchaseListNeutralChipHtml(it) }
    if (hidden.isEmpty()) {
        return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$visibleHtml</span>"""
    }
    val hiddenHtml = hidden.joinToString("") { formatPurchaseListNeutralChipHtml(it) }
    return """<span style="display:inline-flex;flex-direction:column;gap:4px;align-items:flex-start;">""" +
        """<span class="chips-visible" style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$visibleHtml</span>""" +
        """<span class="chips-hidden" style="display:none;flex-wrap:wrap;gap:6px;align-items:center;">$hiddenHtml</span>""" +
        """<button type="button" class="chips-toggle-btn" data-chips-expanded="false" """ +
        """style="background:none;border:none;padding:0;margin-top:2px;cursor:pointer;font-size:12px;color:#6366f1;font-weight:600;white-space:nowrap;">""" +
        """See more ▾ (${hidden.size})</button>""" +
        """</span>"""
}

/** Same chip styling as [formatMultiValueChipCellHtml] but splits only on `;`/newlines (for client map addresses). */
fun formatMultiValueChipCellHtmlSemicolonOnly(raw: String): String {
    val tokens = splitSemicolonOnlyDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) {
        return """<div class="client-map-bank-text">${bankInfoShadowChipSpanHtml(escapeHtml(tokens[0]))}</div>"""
    }
    val inner = tokens.joinToString("") { t ->
        bankInfoShadowChipSpanHtml(escapeHtml(t))
    }
    return """<div class="client-map-bank-chips">$inner</div>"""
}

/** Equal column widths: insert immediately after `<table>`, before `<thead>`. */
fun htmlTableColgroupEqualWidth(colCount: Int): String {
    if (colCount <= 0) return ""
    val w = 100.0 / colCount
    return buildString {
        append("<colgroup>")
        repeat(colCount) {
            append("""<col style="width:$w%">""")
        }
        append("</colgroup>")
    }
}

/**
 * Fixed narrow widths for multiple leading columns (actions); remainder share equally.
 */
fun htmlTableColgroupMultipleNarrowActionsEqualRest(totalColumnCount: Int, vararg actionWidthsPx: Int): String {
    if (totalColumnCount <= 0) return ""
    val widths = actionWidthsPx.toList()
    if (widths.isEmpty()) return htmlTableColgroupNarrowActionEqualRest(totalColumnCount)
    val n = widths.size.coerceAtMost(totalColumnCount)
    return buildString {
        append("<colgroup>")
        for (i in 0 until n) {
            val w = widths[i].coerceAtLeast(44)
            append("""<col style="width:${w}px;min-width:${w}px">""")
        }
        repeat(totalColumnCount - n) {
            append("""<col>""")
        }
        append("</colgroup>")
    }
}

/**
 * First column fixed narrow width (e.g. Actions); remaining columns share the rest equally
 * (table must use `table-layout: fixed; width: 100%`).
 */
fun htmlTableColgroupNarrowActionEqualRest(totalColumnCount: Int, actionWidthPx: Int = 88): String {
    if (totalColumnCount <= 0) return ""
    if (totalColumnCount == 1) {
        return """<colgroup><col style="width:${actionWidthPx}px;min-width:${actionWidthPx}px"></colgroup>"""
    }
    return buildString {
        append("<colgroup>")
        append("""<col style="width:${actionWidthPx}px;min-width:${actionWidthPx}px">""")
        repeat(totalColumnCount - 1) {
            append("""<col>""")
        }
        append("</colgroup>")
    }
}

/**
 * Fixed pixel width per column (with horizontal scroll on narrow viewports).
 * If [widthsPx] is shorter than [totalColumnCount], remaining columns default to [fallbackPx].
 */
fun htmlTableColgroupFixedWidthsPx(totalColumnCount: Int, widthsPx: List<Int>, fallbackPx: Int = 120): String {
    if (totalColumnCount <= 0) return ""
    return buildString {
        append("<colgroup>")
        for (i in 0 until totalColumnCount) {
            val w = widthsPx.getOrElse(i) { fallbackPx }.coerceAtLeast(44)
            append("""<col style="width:${w}px;min-width:${w}px">""")
        }
        append("</colgroup>")
    }
}

/** Client Map Bank Info column: wrap long bank strings; stack multiple entries vertically so the column stays narrow. */
fun formatClientMapBankInfoCellHtml(raw: String): String {
    val tokens = splitSemicolonOnlyDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) {
        return """<div class="client-map-bank-text">${bankInfoShadowChipSpanHtml(escapeHtml(tokens[0]))}</div>"""
    }
    val inner = tokens.joinToString("") { t ->
        bankInfoShadowChipSpanHtml(escapeHtml(t))
    }
    return """<div class="client-map-bank-chips">$inner</div>"""
}

/**
 * Client Map Consignee column: same token rules as [formatMultiValueChipCellHtml] but chips wrap
 * inside fixed table cells (avoids overflow into adjacent columns).
 */
fun formatClientMapConsigneeChipCellHtml(raw: String): String {
    val tokens = splitMultiValueDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) {
        return """<span class="client-map-chip-row">${consigneeMapShadowChipSpanHtml(escapeHtml(tokens[0]))}</span>"""
    }
    val inner = tokens.joinToString("") { t ->
        """<span class="client-map-chip-wrap">${consigneeMapShadowChipSpanHtml(escapeHtml(t))}</span>"""
    }
    return """<span class="client-map-chip-row">$inner</span>"""
}

/**
 * Client Map Debit Limit column — display only (DB unchanged).
 * Shows yen prefix and thousands separators, e.g. `40000` → `¥40,000`.
 */
fun formatClientMapDebitLimitCellHtml(raw: String): String {
    if (raw.trim().length == 0) return ""
    val cleaned = raw.replace(",", "").trim()
    val n = cleaned.toDoubleOrNull()
        ?: return formatLightBlueDebitChipSpan(escapeHtml(raw))
    val negative = n < 0.0
    val abs = kotlin.math.abs(n)
    val intPart = kotlin.math.floor(abs + 1e-9).toLong()
    val frac = abs - intPart
    val intGrouped = intPart.toString().reversed().chunked(3).joinToString(",").reversed()
    val body = (if (negative) "-" else "") + "¥" + intGrouped
    val formatted = if (kotlin.math.abs(frac) < 1e-6) {
        body
    } else {
        val cents = kotlin.math.round(frac * 100.0).toInt().coerceIn(0, 99)
        val fracStr = if (cents < 10) "0$cents" else cents.toString()
        "$body.$fracStr"
    }
    return formatLightBlueDebitChipSpan(escapeHtml(formatted))
}

private fun formatLightBlueDebitChipSpan(escapedInner: String): String =
    """<span class="client-map-debit-limit" style="display:inline-block;padding:4px 12px;border-radius:9999px;font-size:13px;font-weight:500;background:#f8fafc;color:#111827;line-height:1.35;box-shadow:0 1px 6px rgba(15,23,42,0.18);">$escapedInner</span>"""

/** Multiple values → pastel pill chips; single value → same light-blue chip as first token in multi-value rows. */
fun formatMultiValueChipCellHtml(raw: String): String {
    val tokens = splitMultiValueDisplayTokens(raw)
    if (tokens.isEmpty()) return ""
    if (tokens.size == 1) return singleValueChipSpanHtml(escapeHtml(tokens[0]))
    val palettes = listOf(
        "#eff6ff" to "#1e40af",
        "#fff7ed" to "#c2410c",
        "#f5f3ff" to "#6d28d9",
        "#ecfdf5" to "#047857",
        "#fdf2f8" to "#be185d",
        "#fef3c7" to "#b45309"
    )
    val inner = tokens.mapIndexed { i, t ->
        val (bg, fg) = palettes[i % palettes.size]
        """<span style="display:inline-block;padding:4px 12px;border-radius:9999px;font-size:13px;font-weight:500;background:$bg;color:$fg;line-height:1.35;white-space:nowrap;max-width:100%;overflow:hidden;text-overflow:ellipsis;">${escapeHtml(t)}</span>"""
    }.joinToString("")
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

/**
 * Safe localStorage wrapper with error handling
 */
fun safeLocalStorageGet(key: String): String? {
    return try {
        window.localStorage.getItem(key)
    } catch (e: dynamic) {
        Logger.warn("localStorage.getItem failed for key '$key': ${e.toString()}")
        null
    }
}

fun safeLocalStorageSet(key: String, value: String): Boolean {
    return try {
        window.localStorage.setItem(key, value)
        true
    } catch (e: dynamic) {
        Logger.warn("localStorage.setItem failed for key '$key': ${e.toString()}")
        false
    }
}

fun safeLocalStorageRemove(key: String): Boolean {
    return try {
        window.localStorage.removeItem(key)
        true
    } catch (e: dynamic) {
        Logger.warn("localStorage.removeItem failed for key '$key': ${e.toString()}")
        false
    }
}

// API base URL - use relative path so nginx can proxy to backend
val API_BASE_URL = "/api"

// Helper function to get API URL
fun apiUrl(path: String): String {
    // Remove leading slash if present - use trimStart to be safe
    val cleanPath = path.trimStart('/')
    val fullUrl = "$API_BASE_URL/$cleanPath"
    Logger.debug("apiUrl() called with path: '$path', fullUrl: '$fullUrl'")
    return fullUrl
}

/**
 * In-app default after leaving a removed or invalid master screen.
 * Do not use `#/` here — that route shows the sign-in page and looks like a logout even when the token is still valid.
 */
fun navigateToAppHome() {
    window.location.hash = "#/purchase"
}

// Helper function to safely extract numeric value from database field (handles strings with ¥, numbers, null, etc.)
fun extractNumericFromDbValue(value: dynamic): String {
    if (value == null || value == js("undefined")) return ""
    val str: String = when {
        value is String -> value
        else -> {
            try {
                val s = value.toString()
                if (s is String) s else ""
            } catch (e: dynamic) {
                ""
            }
        }
    }
    // Use JavaScript-compatible check instead of Kotlin's isEmpty()
    if (str.length == 0) return ""
    // Remove currency symbols (including corrupted "Â¥"), commas, spaces - keep only numbers and decimal point
    // First remove corrupted "Â¥" pattern, then regular "¥", then commas and spaces, then any other non-numeric except decimal point
    return str.replace(Regex("Â¥"), "").replace(Regex("[¥,\\s]"), "").replace(Regex("[^0-9.]"), "")
}

/**
 * Device Detection Utilities for Responsive Design
 */

/**
 * Get current device type based on window width
 * @return "mobile", "tablet", or "desktop"
 */
fun getDeviceType(): String {
    val width = window.innerWidth
    return when {
        width <= AppConstants.MOBILE_MAX_WIDTH -> "mobile"
        width <= AppConstants.TABLET_MAX_WIDTH -> "tablet"
        else -> "desktop"
    }
}

/**
 * Get maximum columns allowed for current device
 * @return Maximum number of columns (4 for mobile, 6 for tablet, 9 for desktop)
 */
fun getMaxColumnsForDevice(deviceType: String? = null): Int {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> AppConstants.MOBILE_MAX_COLUMNS
        "tablet" -> AppConstants.TABLET_MAX_COLUMNS
        "desktop" -> AppConstants.DESKTOP_MAX_COLUMNS
        else -> AppConstants.DESKTOP_MAX_COLUMNS
    }
}

/** Car Brands Map: at most 6 data columns (plus actions) in table and column picker. */
fun getMaxCarBrandMapColumnsForDevice(deviceType: String? = null): Int = 6

/** Consignee Map & Supplier Map: max 6 data columns (plus actions). */
fun getMaxConsigneeSupplierMapColumnsForDevice(deviceType: String? = null): Int = 6

/** Purchase List table: max 8 data columns (plus actions). */
fun getMaxPurchaseListColumnsForDevice(deviceType: String? = null): Int = 8

/**
 * Get default columns for a specific device type
 * @param deviceType Device type ("mobile", "tablet", or "desktop")
 * @return List of default column keys for the device
 */
fun getDefaultColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("date", "chassis", "carName", "price")
        "tablet" -> listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "price")
        "desktop" -> listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "price")
        else -> listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "price")
    }
}

/**
 * Auto-adjust columns when device changes
 * If saved columns exceed device limit, replace with device defaults
 * @return Adjusted list of columns
 */
fun autoAdjustColumnsForDevice(savedColumns: List<String>, deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    val maxColumns = getMaxPurchaseListColumnsForDevice(device)
    val defaultColumns = getDefaultColumnsForDevice(device)
    
    return if (savedColumns.size > maxColumns) {
        // If saved columns exceed limit, use device defaults
        defaultColumns
    } else {
        // If within limit, keep user's selection (but ensure it's valid)
        savedColumns.filter { it.isNotBlank() }.take(maxColumns)
    }
}

/**
 * Purchase list: when "date" and/or "chassis" are selected, keep them as the leftmost data columns
 * (after the row action column). If both are selected: Purchase Date first, then Chassis; other
 * columns keep their relative order.
 */
fun prioritizePurchaseListDateAndChassis(columns: List<String>): List<String> {
    val hasDate = columns.contains("date")
    val hasChassis = columns.contains("chassis")
    val rest = columns.filter { it != "date" && it != "chassis" }
    return buildList {
        if (hasDate) add("date")
        if (hasChassis) add("chassis")
        addAll(rest)
    }
}

/** Purchase list: these keys are always visible and cannot be turned off in the column picker. */
fun purchaseListMandatoryColumnKeys(): Set<String> = setOf("date", "chassis")

/**
 * Forces Purchase Date and Chassis to be present, in that order, then other columns in [columns] order
 * (excluding duplicate pins). Result length is capped at [maxColumns] (minimum 2 so pins always fit).
 */
fun ensurePurchaseListPinnedColumns(columns: List<String>, maxColumns: Int): List<String> {
    val max = maxColumns.coerceAtLeast(2)
    val rest = columns.filter { it != "date" && it != "chassis" }
    val merged = buildList {
        add("date")
        add("chassis")
        addAll(rest)
    }
    return merged.take(max)
}

/**
 * All purchase list column keys and display labels (matches [com.automan.backend.model.Purchase] / `purchases` table).
 * Used by column filter modal and table/card headers.
 */
fun purchaseListColumnLabels(): Map<String, String> {
    return linkedMapOf(
        "date" to "Purchase Date",
        "chassis" to "Chassis",
        "carModelYear" to "Production Date",
        "brand" to "Brand",
        "carName" to "Car Name",
        "shipmentSize" to "Vehicle type",
        "grade" to "Grade",
        "rank" to "Rank",
        "color" to "Color",
        "fuel" to "Fuel",
        "seat" to "Seat",
        "door" to "Door",
        "distance" to "Distance",
        "options" to "Options",
        "cc" to "CC",
        "shift" to "Shift",
        "wd" to "WD",
        "driveType" to "Drive Type",
        "auctionNo" to "Auction No",
        "auctionHouse" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "pol" to "POL",
        "pod" to "POD",
        "rixoCompany" to "Rixo Company",
        "venueId" to "Venue ID",
        "clientName" to "Client Name",
        "consignee" to "Consignee",
        "clientId" to "Client ID",
        "country" to "Target Country",
        "price" to "Car Price",
        "auctionFee" to "Auction Fee",
        "auctionPenaltyFee" to "Auction Penalty Fee",
        "recycleFee" to "Recycle Fee",
        "roadTax" to "Road Tax",
        "taxTotal" to "Tax Total",
        "totalPrice" to "Total Price",
        "paymentDate" to "Payment Date",
        "rixoRequested" to "Rixo Requested",
        "rixoConfirmed" to "Rixo Confirmed",
        "rixoPrice" to "Rixo Price",
        "notes" to "Notes",
        "shipmentDate" to "Shipment Date",
        "blNo" to "BL No",
        "vessel" to "Vessel",
        "vesselNo" to "Vessel",
        "bookingRequested" to "Booking Requested",
        "sold" to "Sold",
        "invoiceConfirmed" to "Sold",
        "shipmentCharges" to "Shipment Charges",
        "freight" to "Freight",
        "storageCharges" to "Storage Charges",
        "miscCharges" to "Misc Charges",
        "inspectionFee" to "Inspection Fee",
        "commission" to "Commission",
        "numberCut" to "Number Cut",
        "shaken" to "SHAKEN",
        "repairCompany" to "Repair Company",
        "repairCharges" to "Repair Charges",
        "profit" to "Profit",
        "isPackageMode" to "Package Mode",
        "bookingId" to "Booking No",
        "carPictures" to "Car Pictures",
        // Legacy UI field (not a DB column); kept for older saved column prefs
        "destination" to "Destination"
    )
}

/** Human-readable label for a purchase field key in change-history rows. */
fun purchaseChangeHistoryFieldLabel(field: String): String {
    val key = field.trim()
    purchaseListColumnLabels()[key]?.let { return it }
    return key
}

/**
 * True when this purchase row should still appear in the Rixo Request Generator as needing a request.
 * Matches backend [com.automan.backend.repository.PurchaseRepository.findDistinctPurchaseDateStrings]:
 * pending if NULL/blank or lowercase trimmed value is not `'1'` nor `'true'`.
 */
fun isRixoRequestedPendingForTransportGenerator(raw: String?): Boolean {
    val s = raw?.trim()?.lowercase() ?: ""
    if (s.isEmpty()) return true
    return s != "1" && s != "true"
}

/**
 * Formats backend ISO local date/time (`yyyy-MM-dd'T'HH:mm:ss`) for display,
 * e.g. `Apr 29, 2026 7:58 PM` (interpreted in the browser local zone).
 */
fun formatPurchaseChangeHistoryDateTime(isoLocalDateTime: String): String {
    if (isoLocalDateTime.isBlank()) return ""
    val normalized = isoLocalDateTime.trim().replace(" ", "T")
    val t = Date.parse(normalized)
    if (t.isNaN() || !t.isFinite()) return isoLocalDateTime.trim()
    val d = Date(t.toLong())
    val months =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthIdx = d.getMonth().toInt().coerceIn(0, 11)
    val day = d.getDate().toInt()
    val year = d.getFullYear().toInt()
    val hRaw = d.getHours().toInt()
    val m = d.getMinutes().toInt()
    val pm = hRaw >= 12
    val h12 = when {
        hRaw == 0 -> 12
        hRaw > 12 -> hRaw - 12
        else -> hRaw
    }
    val ampm = if (pm) "PM" else "AM"
    val minStr = m.toString().padStart(2, '0')
    return "${months[monthIdx]} $day, $year $h12:$minStr $ampm"
}

/**
 * Sold list column: legacy [sold] may be absent after DB migration; UI maps Sold to [invoiceConfirmed].
 * Uses TRUE/FALSE to match other status chips (e.g. Rixo columns).
 */
private fun purchaseSoldOrInvoiceConfirmedCell(p: dynamic): String {
    fun tri(raw: dynamic): String {
        if (raw == null || raw == js("undefined")) return ""
        return when (raw) {
            is Boolean -> if (raw as Boolean) "TRUE" else "FALSE"
            is Number -> if ((raw as Number).toDouble() != 0.0) "TRUE" else "FALSE"
            else -> {
                val s = raw.toString().trim()
                when {
                    s.equals("true", ignoreCase = true) || s == "1" || s == "TRUE" -> "TRUE"
                    s.equals("false", ignoreCase = true) || s == "0" || s == "FALSE" -> "FALSE"
                    else -> s
                }
            }
        }
    }
    val fromSold = tri(p["sold"])
    if (fromSold.isNotEmpty()) return fromSold
    val fromIc = tri(p["invoiceConfirmed"])
    if (fromIc.isNotEmpty()) return fromIc
    return tri(p["invoice_confirmed"])
}

/** Display string for one purchase list cell; keys align with [purchaseListColumnLabels]. */
fun purchaseTableCellValue(purchase: dynamic, columnKey: String): String {
    // Do not call .asDynamic() here: JSON/plain JS objects from the API are already dynamic at
    // runtime and do not expose Kotlin's asDynamic() extension (throws in the browser).
    if (purchase == null || purchase == js("undefined")) return ""
    val p: dynamic = purchase
    fun field(key: String): String {
        val v = p[key]
        if (v == null || v == js("undefined")) return ""
        return when {
            v is Boolean -> if (v as Boolean) "Yes" else "No"
            v is Number -> {
                val d = (v as Number).toDouble()
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            else -> v.toString()
        }
    }
    fun moneyWithYenIfMissing(raw: dynamic): String {
        val s = when (raw) {
            null -> ""
            is String -> raw
            is Number -> raw.toString()
            else -> raw.toString()
        }
        if (s.length == 0) return ""
        return if (s.startsWith("¥")) s else "¥$s"
    }
    return when (columnKey) {
        "date" -> try {
            val dateStr = (p.date?.toString() ?: "").toString()
            if (dateStr.trim().length == 0) "" else formatWithWeekday(dateStr)
        } catch (e: dynamic) {
            ""
        }
        "id" -> {
            val v = p.id
            when {
                v == null || v == js("undefined") -> ""
                v is Number -> (v as Number).toLong().toString()
                else -> v.toString()
            }
        }
        "chassis" -> field("chassis")
        "carName" -> field("carName")
        "auctionHouse" -> field("auctionHouse")
        "stockLocation" -> field("stockLocation")
        "clientName" -> field("clientName")
        "rixoCompany" -> field("rixoCompany")
        "price" -> {
            val priceStr = (p.price as? String) ?: field("price")
            val priceValue = parseCurrency(priceStr)
            if (priceValue > 0.0) formatCurrency(priceValue) else ""
        }
        "carModelYear" -> field("carModelYear")
        "brand" -> field("brand")
        "grade" -> field("grade")
        "rank" -> field("rank")
        "color" -> field("color")
        "fuel" -> field("fuel")
        "seat" -> field("seat")
        "door" -> field("door")
        "distance" -> field("distance")
        "options" -> field("options")
        "cc" -> field("cc")
        "shift" -> field("shift")
        "wd" -> field("wd")
        "driveType" -> field("driveType")
        "auctionNo" -> field("auctionNo")
        "pol" -> field("pol")
        "venueId" -> field("venueId")
        "shipmentSize" -> field("shipmentSize")
        "consignee" -> field("consignee")
        "clientId" -> field("clientId")
        "country" -> field("country")
        "totalPrice" -> {
            val raw = (p.totalPrice as? String) ?: field("totalPrice")
            if (raw.length == 0) "" else moneyWithYenIfMissing(raw)
        }
        "auctionFee" -> {
            val raw = (p.auctionFee as? String) ?: field("auctionFee")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "auctionPenaltyFee" -> {
            val raw = (p.auctionPenaltyFee as? String) ?: field("auctionPenaltyFee")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "recycleFee" -> {
            val raw = (p.recycleFee as? String) ?: field("recycleFee")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "roadTax" -> {
            val raw = (p.roadTax as? String) ?: field("roadTax")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "taxTotal" -> {
            val raw = (p.taxTotal as? String) ?: field("taxTotal")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "paymentDate" -> (p.paymentDate as? String) ?: field("paymentDate")
        "rixoRequested" -> (p.rixoRequested as? String) ?: field("rixoRequested")
        "rixoConfirmed" -> (p.rixoConfirmed as? String) ?: field("rixoConfirmed")
        "rixoPrice" -> {
            val rixoPriceRaw = p.rixoPrice
            val num = when (rixoPriceRaw) {
                null -> 0.0
                is Number -> (rixoPriceRaw as Number).toDouble()
                else -> parseCurrency(rixoPriceRaw.toString())
            }
            if (num > 0.0) "¥" + formatCurrency(num) else "¥0"
        }
        "notes" -> field("notes")
        "shipmentDate" -> (p.shipmentDate as? String) ?: field("shipmentDate")
        "blNo" -> (p.blNo as? String) ?: field("blNo")
        "vesselNo" -> (p.vessel as? String) ?: field("vesselNo")
        "vessel" -> (p.vessel as? String) ?: field("vessel")
        "destination" -> (p.destination as? String) ?: field("destination")
        "bookingRequested" -> field("bookingRequested")
        in setOf("sold", "invoiceConfirmed") -> purchaseSoldOrInvoiceConfirmedCell(p)
        "shipmentCharges" -> {
            val raw = (p.shipmentCharges as? String) ?: field("shipmentCharges")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "freight" -> {
            val raw = (p.freight as? String) ?: field("freight")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "storageCharges" -> {
            val raw = (p.storageCharges as? String) ?: field("storageCharges")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "miscCharges" -> {
            val raw = (p.miscCharges as? String) ?: field("miscCharges")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "inspectionFee" -> {
            val raw = (p.inspectionFee as? String) ?: field("inspectionFee")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "commission" -> {
            val raw = (p.commission as? String) ?: field("commission")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "repairCharges" -> {
            val raw = (p.repairCharges as? String) ?: field("repairCharges")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "profit" -> {
            val raw = (p.profit as? String) ?: field("profit")
            if (raw.isNotEmpty()) {
                val numValue = parseCurrency(raw)
                if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
            } else ""
        }
        "isPackageMode" -> field("isPackageMode")
        "bookingId" -> {
            val primary = field("bookingId")
            if (primary.length == 0) field("booking_id") else primary
        }
        "carPictures" -> field("carPictures")
        "createdAt" -> field("createdAt")
        "updatedAt" -> field("updatedAt")
        "numberCut" -> (p.numberCut as? String) ?: field("numberCut")
        "shaken" -> field("shaken")
        "repairCompany" -> (p.repairCompany as? String) ?: field("repairCompany")
        else -> {
            val v = p[columnKey]
            if (v == null || v == js("undefined")) "" else when {
                v is Boolean -> if (v as Boolean) "Yes" else "No"
                else -> v.toString()
            }
        }
    }
}

/** Escape a string for use inside a double-quoted HTML attribute. */
fun escapeAttr(value: String): String =
    value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

/**
 * Initial value for Cars-to-Rixo inline edit controls (raw / form-friendly, not list display formatting).
 */
fun purchaseInlineEditSeed(purchase: dynamic, columnKey: String): String {
    if (purchase == null || purchase == js("undefined")) return ""
    val p: dynamic = purchase
    fun field(key: String): String {
        val v = p[key]
        if (v == null || v == js("undefined")) return ""
        return when {
            v is Boolean -> if (v as Boolean) "true" else "false"
            v is Number -> {
                val d = (v as Number).toDouble()
                if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
            }
            else -> v.toString()
        }
    }
    return when (columnKey) {
        "date" -> toIsoFromLabel(p.date)
        "chassis" -> field("chassis")
        "carName" -> field("carName")
        "auctionHouse" -> field("auctionHouse")
        "stockLocation" -> field("stockLocation")
        "clientName" -> field("clientName")
        "rixoCompany" -> field("rixoCompany")
        "auctionNo" -> field("auctionNo")
        "pol" -> field("pol")
        "venueId" -> field("venueId")
        "shipmentSize" -> field("shipmentSize")
        "consignee" -> field("consignee")
        "clientId" -> field("clientId")
        "country" -> field("country")
        "brand" -> field("brand")
        "grade" -> field("grade")
        "rank" -> field("rank")
        "color" -> field("color")
        "fuel" -> field("fuel")
        "seat" -> field("seat")
        "door" -> field("door")
        "distance" -> extractNumericFromSuffixedValue(p.distance)
        "options" -> field("options")
        "cc" -> field("cc")
        "shift" -> field("shift")
        "wd" -> field("wd")
        "driveType" -> field("driveType")
        "carModelYear" -> {
            val raw = field("carModelYear")
            carModelYearToMonthInputValue(raw).ifEmpty { raw }
        }
        "paymentDate" -> toIsoFromLabel(p.paymentDate ?: p.payment_date)
        "shipmentDate" -> toIsoFromLabel(p.shipmentDate ?: p.shipment_date ?: p.shippment_date)
        "notes" -> field("notes")
        "blNo" -> field("blNo")
        "vessel" -> (p.vessel as? String) ?: field("vessel")
        "destination" -> (p.destination as? String) ?: field("destination")
        "numberCut" -> field("numberCut")
        "repairCompany" -> field("repairCompany")
        "bookingId" -> {
            val primary = field("bookingId")
            if (primary.isNotEmpty()) primary else field("booking_id")
        }
        "price" -> extractNumericFromDbValue(p.price)
        "auctionFee" -> extractNumericFromDbValue(p.auctionFee)
        "auctionPenaltyFee" -> extractNumericFromDbValue(p.auctionPenaltyFee)
        "recycleFee" -> extractNumericFromDbValue(p.recycleFee)
        "roadTax" -> extractNumericFromDbValue(p.roadTax)
        "taxTotal" -> extractNumericFromDbValue(p.taxTotal)
        "totalPrice" -> extractNumericFromDbValue(p.totalPrice)
        "shipmentCharges" -> extractNumericFromDbValue(p.shipmentCharges)
        "freight" -> extractNumericFromDbValue(p.freight)
        "storageCharges" -> extractNumericFromDbValue(p.storageCharges)
        "miscCharges" -> extractNumericFromDbValue(p.miscCharges)
        "inspectionFee" -> extractNumericFromDbValue(p.inspectionFee)
        "commission" -> extractNumericFromDbValue(p.commission)
        "rixoPrice" -> extractNumericFromDbValue(p.rixoPrice)
        "repairCharges" -> extractNumericFromDbValue(p.repairCharges)
        "profit" -> extractNumericFromDbValue(p.profit)
        "rixoRequested" -> {
            val s = (p.rixoRequested as? String) ?: field("rixoRequested")
            if (s.equals("TRUE", ignoreCase = true)) "TRUE" else "FALSE"
        }
        "rixoConfirmed" -> {
            val s = (p.rixoConfirmed as? String) ?: field("rixoConfirmed")
            if (s.equals("TRUE", ignoreCase = true)) "TRUE" else "FALSE"
        }
        "bookingRequested", "isPackageMode", "shaken" -> field(columnKey)
        "sold", "invoiceConfirmed" -> purchaseSoldOrInvoiceConfirmedCell(p).ifEmpty { field(columnKey) }
        "carPictures" -> field("carPictures")
        else -> {
            val v = p[columnKey]
            if (v == null || v == js("undefined")) "" else v.toString()
        }
    }
}

// Helper function to safely extract numeric value from text fields with suffixes (CC, WD, km)
fun extractNumericFromSuffixedValue(value: dynamic): String {
    if (value == null || value == js("undefined")) return ""
    val str: String = when {
        value is String -> value
        else -> {
            try {
                val s = value.toString()
                if (s is String) s else ""
            } catch (e: dynamic) {
                ""
            }
        }
    }
    // Use JavaScript-compatible check instead of Kotlin's isEmpty()
    if (str.length == 0) return ""
    // Remove all non-numeric characters (including CC, WD, km suffixes)
    return str.replace(Regex("[^0-9]"), "")
}

// Date formatting functions
fun formatWithWeekday(isoDate: String?): String {
    if (isoDate == null || isoDate.trim().length == 0) return ""
    // If already includes weekday, keep as is
    if (isoDate.contains("(") && isoDate.contains(")")) return isoDate
    try {
        val date = js("new Date(isoDate)")
        if (js("isNaN(date)") as Boolean) return isoDate
        val months = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        val month = months[js("date.getMonth()") as Int]
        val dayOfMonth = js("date.getDate()") as Int
        val year = js("date.getFullYear()") as Int
        val weekday = days[js("date.getDay()") as Int]
        return month + dayOfMonth.toString() + ", " + year.toString() + "(" + weekday + ")"
    } catch (e: dynamic) {
        return isoDate
    }
}

fun formatDateForDatabase(isoDate: String?): String {
    if (isoDate == null || isoDate.trim().length == 0) return ""
    try {
        val date = js("new Date(isoDate)")
        if (js("isNaN(date)") as Boolean) return isoDate
        val months = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        val month = months[js("date.getMonth()") as Int]
        val dayOfMonth = js("date.getDate()") as Int
        val year = js("date.getFullYear()") as Int
        val weekday = days[js("date.getDay()") as Int]
        return month + dayOfMonth.toString() + ", " + year.toString() + "(" + weekday + ")"
    } catch (e: dynamic) {
        return isoDate
    }
}

// Formats carModelYear from YYYY-MM or MM/YYYY to "Month YYYY" format
// Examples: "2025-07" -> "July 2025", "07/2025" -> "July 2025", "7/2025" -> "July 2025"
fun formatCarModelYear(yearStr: String?): String {
    if (yearStr == null || yearStr.trim().length == 0) return ""
    
    val months = arrayOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    
    try {
        // Handle YYYY-MM format (from month input)
        if (yearStr.contains("-")) {
            val parts = yearStr.split("-")
            if (parts.size == 2) {
                val year = parts[0].toIntOrNull()
                val month = parts[1].toIntOrNull()
                if (year != null && month != null && month >= 1 && month <= 12) {
                    return "${months[month - 1]} $year"
                }
            }
        }
        
        // Handle MM/YYYY or M/YYYY format (from database)
        if (yearStr.contains("/")) {
            val parts = yearStr.split("/")
            if (parts.size == 2) {
                val month = parts[0].toIntOrNull()
                val year = parts[1].toIntOrNull()
                if (month != null && year != null && month >= 1 && month <= 12) {
                    return "${months[month - 1]} $year"
                }
            }
        }
        
        // If already in readable format, return as is
        return yearStr
    } catch (e: dynamic) {
        return yearStr
    }
}

/** Extracts only the 4-digit year from car_model_year (e.g. "July 2026" -> "2026", "2025-07" -> "2025"). */
fun carModelYearToYearOnly(yearStr: String?): String {
    if (yearStr == null || yearStr.trim().length == 0) return ""
    // YYYY-MM
    if (yearStr.contains("-")) {
        val parts = yearStr.split("-")
        if (parts.isNotEmpty()) {
            val y = parts[0].trim()
            if (y.length == 4 && y.all { it.isDigit() }) return y
        }
    }
    // MM/YYYY or M/YYYY
    if (yearStr.contains("/")) {
        val parts = yearStr.split("/")
        if (parts.size >= 2) {
            val y = parts[1].trim()
            if (y.length == 4 && y.all { it.isDigit() }) return y
        }
    }
    // "Month YYYY" (e.g. July 2026) - take last token if it's 4 digits
    val tokens = yearStr.trim().split(Regex("\\s+"))
    for (t in tokens.reversed()) {
        if (t.length == 4 && t.all { it.isDigit() }) return t
    }
    // Already a single year?
    if (yearStr.length == 4 && yearStr.all { it.isDigit() }) return yearStr
    return yearStr
}

/** Normalizes car_model_year to `YYYY-MM` for `<input type="month">`. */
fun carModelYearToMonthInputValue(raw: String?): String {
    if (raw == null || raw.isBlank()) return ""
    val t = raw.trim()
    if (Regex("^\\d{4}-\\d{2}$").matches(t)) return t
    if (Regex("^\\d{4}$").matches(t)) return "$t-01"
    val yOnly = carModelYearToYearOnly(t)
    if (yOnly.length == 4 && yOnly.all { it.isDigit() }) return "$yOnly-01"
    return ""
}

fun normalizeDateForComparison(dateStr: String?): String {
    if (dateStr == null || dateStr.trim().length == 0) return ""
    
    try {
        // Handle format: "24 Apr, 2025" -> convert to "April24, 2025"
        if (dateStr.contains("Apr") && !dateStr.contains("April")) {
            val parts = dateStr.split(", ")
            if (parts.size == 2) {
                val dayMonth = parts[0].trim()
                val year = parts[1].trim()
                val day = dayMonth.split(" ")[0]
                return "April$day, $year"
            }
        }
        
        // Handle format: "April24, 2025(Thursday)" -> extract "April24, 2025"
        if (dateStr.contains("April") && dateStr.contains("(")) {
            val beforeParen = dateStr.split("(")[0].trim()
            return beforeParen
        }
        
        // Handle format: "April24, 2025" -> return as is
        if (dateStr.contains("April")) {
            return dateStr
        }
        
        // If none of the above, try to parse as ISO date and convert
        val date = js("new Date(dateStr)")
        if (!js("isNaN(date)") as Boolean) {
            val months = arrayOf(
                "January","February","March","April","May","June",
                "July","August","September","October","November","December"
            )
            val month = months[js("date.getMonth()") as Int]
            val dayOfMonth = js("date.getDate()") as Int
            val year = js("date.getFullYear()") as Int
            return month + dayOfMonth.toString() + ", " + year.toString()
        }
        
        return dateStr
    } catch (e: dynamic) {
        return dateStr
    }
}

/** Converts valid ISO yyyy-MM-dd to MM/DD/YYYY for masked text inputs. Empty if not parseable as ISO date. */
fun isoToMmDdYyyy(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val t = iso.trim()
    if (!t.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return ""
    val p = t.split("-")
    if (p.size != 3) return ""
    return "${p[1]}/${p[2]}/${p[0]}"
}

/** Local calendar date as yyyy-MM-dd for `<input type="date">` (not UTC midnight). */
fun todayIsoLocalDate(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    val day = d.getDate() as Int
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

/** Local calendar year-month as yyyy-MM for `<input type="month">` (not UTC). */
fun todayIsoLocalYearMonth(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    return y.toString() + "-" + m.toString().padStart(2, '0')
}

// Converts a stored date label like "June3, 2025(Tuesday)" to ISO yyyy-MM-dd for <input type="date">
fun toIsoFromLabel(dateStr: dynamic): String {
    // Safely convert to string, handling null, undefined, or non-string types
    val dateString: String = when {
        dateStr == null || dateStr == js("undefined") -> ""
        dateStr is String -> dateStr
        else -> {
            try {
                dateStr.toString()
            } catch (e: dynamic) {
                ""
            }
        }
    }
    
    if (dateString.length == 0 || dateString.trim().length == 0) return ""
    
    // If already ISO-like (YYYY-MM-DD), return as-is
    if (dateString.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return dateString
    
    val slashTrimmed = dateString.trim()
    // US-style M/D/YYYY … MM/DD/YYYY from masked text inputs
    if (slashTrimmed.contains("/")) {
        val segs = slashTrimmed.split("/").map { it.filter { ch -> ch.isDigit() } }.filter { it.isNotEmpty() }
        if (segs.size >= 3) {
            val mmRaw = segs[0].take(2).padStart(2, '0')
            val ddRaw = segs[1].take(2).padStart(2, '0')
            val yyyyRaw = segs[2].take(4)
            val mm = mmRaw.toIntOrNull()
            val dd = ddRaw.toIntOrNull()
            val year = yyyyRaw.toIntOrNull()
            if (mm != null && dd != null && year != null &&
                mm in 1..12 && dd in AppConstants.MIN_DAY..AppConstants.MAX_DAY &&
                year in AppConstants.MIN_YEAR..AppConstants.MAX_YEAR && yyyyRaw.length == 4
            ) {
                val monthStr = if (mm < 10) "0$mm" else mm.toString()
                val dayStr = if (dd < 10) "0$dd" else dd.toString()
                return "$yyyyRaw-$monthStr-$dayStr"
            }
        }
    }
    
    // Handle various date formats
    try {
        // Strip weekday part in parentheses (e.g., "January15, 2026(Wednesday)" -> "January15, 2026")
        val base = dateString.replace(Regex("\\(.*?\\)"), "").trim()
        
        // Handle formats like "24 Apr, 2025" -> "April 24, 2025"
        val monthAbbrevMap = mapOf(
            "Jan" to "January", "Feb" to "February", "Mar" to "March", "Apr" to "April",
            "May" to "May", "Jun" to "June", "Jul" to "July", "Aug" to "August",
            "Sep" to "September", "Oct" to "October", "Nov" to "November", "Dec" to "December"
        )
        var processed = base
        for ((abbrev, full) in monthAbbrevMap) {
            if (processed.contains(abbrev) && !processed.contains(full)) {
                processed = processed.replace(Regex("\\b$abbrev\\b"), full)
                break
            }
        }
        
        // Ensure a space between month and day (e.g., "January15, 2026" -> "January 15, 2026")
        // Handle both "January15" (no space) and "January 15" (with space)
        // Also handle formats like "January20, 2026(Tuesday)" - need to add space before day
        val normalized = processed.replace(Regex("^([A-Za-z]+)(\\d+),\\s*(\\d{4})"), "$1 $2, $3")
        
        // Try parsing with JavaScript Date
        val d = js("new Date(normalized)")
        val isValid = js("!isNaN(d.getTime())") as Boolean
        
        if (!isValid) {
            // Fallback: try parsing common formats manually
            val manualParse = try {
                // Ensure we have space between month and day for manual parsing
                val normalizedWithSpace = normalized.replace(Regex("^([A-Za-z]+)(\\d+)"), "$1 $2")
                val parts = normalizedWithSpace.split(Regex("[, ]+"))
                if (parts.size >= 3) {
                    val monthName = parts[0]
                    val day = parts[1].toIntOrNull()
                    val year = parts[2].toIntOrNull()
                    if (day != null && year != null) {
                        val monthNum = when (monthName.lowercase()) {
                            "january", "jan" -> 1
                            "february", "feb" -> 2
                            "march", "mar" -> 3
                            "april", "apr" -> 4
                            "may" -> 5
                            "june", "jun" -> 6
                            "july", "jul" -> 7
                            "august", "aug" -> 8
                            "september", "sep" -> 9
                            "october", "oct" -> 10
                            "november", "nov" -> 11
                            "december", "dec" -> 12
                            else -> null
                        }
                        if (monthNum != null && day in AppConstants.MIN_DAY..AppConstants.MAX_DAY && year in AppConstants.MIN_YEAR..AppConstants.MAX_YEAR) {
                            val monthStr = if (monthNum < 10) "0$monthNum" else monthNum.toString()
                            val dayStr = if (day < 10) "0$day" else day.toString()
                            return "${year}-${monthStr}-${dayStr}"
                        }
                    }
                }
                ""
            } catch (e: dynamic) {
                console.warn("Date parsing error:", e, "for input:", dateString)
                ""
            }
            return manualParse
        }
        
        // Successfully parsed with Date object
        return buildString {
            val y = js("d.getFullYear()") as Int
            val m = (js("d.getMonth()") as Int) + 1
            val day = js("d.getDate()") as Int
            append(y.toString())
            append("-")
            append(if (m < 10) "0$m" else m.toString())
            append("-")
            append(if (day < 10) "0$day" else day.toString())
        }
    } catch (e: dynamic) {
        console.warn("Date parsing error:", e, "for input:", dateString)
        return ""
    }
}

fun parseDateForSorting(dateStr: String): Long? {
    if (dateStr.isBlank()) return null
    
    // Define JavaScript helper function for date parsing (only once)
    js("""
        if (typeof window.parseDateForSorting === 'undefined') {
            window.parseDateForSorting = function(dateStr) {
                if (!dateStr || dateStr.trim() === '') return null;
                
                // If date is formatted like: "March 11, 2026(Wednesday)",
                // strip the "(Weekday)" part so `new Date(...)` can parse it.
                var cleaned = dateStr.replace(/\s*\(.*?\)\s*$/, '').trim();
                
                // Try direct Date parsing
                var date = new Date(cleaned);
                var timestamp = date.getTime();
                if (!isNaN(timestamp) && timestamp > 0) {
                    return timestamp;
                }
                
                // Try yyyy-MM-dd format
                var isoMatch = cleaned.match(/^(\d{4})-(\d{2})-(\d{2})/);
                if (isoMatch) {
                    var year = parseInt(isoMatch[1]);
                    var month = parseInt(isoMatch[2]) - 1;
                    var day = parseInt(isoMatch[3]);
                    date = new Date(year, month, day);
                    timestamp = date.getTime();
                    if (!isNaN(timestamp)) {
                        return timestamp;
                    }
                }
                
                // Try MM/dd/yyyy format
                var usMatch = cleaned.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})/);
                if (usMatch) {
                    var month = parseInt(usMatch[1]) - 1;
                    var day = parseInt(usMatch[2]);
                    var year = parseInt(usMatch[3]);
                    date = new Date(year, month, day);
                    timestamp = date.getTime();
                    if (!isNaN(timestamp)) {
                        return timestamp;
                    }
                }
                
                return null;
            };
        }
    """)
    
    // Call JavaScript function using dynamic typing
    val parseFunc = js("window.parseDateForSorting").unsafeCast<(String) -> dynamic>()
    val result = parseFunc(dateStr).unsafeCast<Double?>()
    
    return if (result != null && !result.isNaN()) {
        result.toLong()
    } else {
        null
    }
}

/**
 * If the booking field looks like `"Country - Consignee Name"`, returns only the name part.
 * Otherwise returns [raw] trimmed (consignee column should store the company name, not country).
 */
fun consigneeNameWithoutCountryPrefix(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    val sep = " - "
    val i = t.indexOf(sep)
    if (i > 0 && i < 160) {
        val after = t.substring(i + sep.length).trim()
        if (after.isNotEmpty()) return after
    }
    return t
}

fun formatConsigneeForUpdate(rawConsignee: String, consigneeCountry: String): String {
    val trimmedRaw = rawConsignee.trim()
    val trimmedCountry = consigneeCountry.trim()

    if (trimmedRaw.isEmpty() && trimmedCountry.isEmpty()) {
        return ""
    }

    // Try to split by newline first
    var parts = trimmedRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    
    // If no newline found, try to detect if it's a concatenated name+address
    // Look for common patterns like company name followed by address (numbers, street names, etc.)
    if (parts.size == 1 && trimmedRaw.length > 50) {
        // Try to split at common separators or patterns
        // Look for patterns like "LTD." or "LTD" followed by address
        val ltdPattern = Regex("(.*?)(LTD\\.?|LIMITED|PVT|PRIVATE)(.*)", RegexOption.IGNORE_CASE)
        val match = ltdPattern.find(trimmedRaw)
        if (match != null) {
            val companyPart = (match.groupValues[1] + match.groupValues[2]).trim()
            val addressPart = match.groupValues[3].trim()
            if (companyPart.isNotEmpty() && addressPart.isNotEmpty()) {
                parts = listOf(companyPart, addressPart)
            }
        }
    }
    
    val name = parts.firstOrNull() ?: ""
    val address = parts.drop(1).joinToString(" ").trim()

    // If consignee already contains country, return formatted name + address
    if (trimmedRaw.contains(trimmedCountry, ignoreCase = true)) {
        return if (address.isNotEmpty()) {
            "$name\n$address"
        } else {
            name
        }
    }

    // Otherwise, prepend country to name
    val formattedName = if (trimmedCountry.isNotEmpty()) {
        "$trimmedCountry - $name"
    } else {
        name
    }

    return if (address.isNotEmpty()) {
        "$formattedName\n$address"
    } else {
        formattedName
    }
}

// Currency formatting functions
fun formatCurrency(amount: Double): String {
    return amount.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
}

fun parseCurrency(currencyString: String): Double {
    // Remove commas and any currency symbols, then parse as double
    val cleanString = currencyString.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
    return cleanString.toDoubleOrNull() ?: 0.0
}

// Input validation and formatting
fun validateAndFormatCurrencyInput(field: HTMLInputElement) {
    val currentValue = field.value
    val numericValue = currentValue.toDoubleOrNull() ?: 0.0
    
    // Validate: only allow positive numbers
    if (numericValue < 0) {
        field.value = "0"
        return
    }
    
    // Format with commas
    field.value = formatCurrency(numericValue)
}

// Message display utility
fun showMessage(message: String, type: String) {
    // Remove existing message
    document.getElementById("message")?.remove()
    
    val messageDiv = document.createElement("div")
    messageDiv.id = "message"
    
    val backgroundColor = when (type) {
        "success" -> "#d4edda"
        "error" -> "#f8d7da"
        "warning" -> "#fff3cd"
        else -> "#d1ecf1"
    }
    
    val color = when (type) {
        "success" -> "#155724"
        "error" -> "#721c24"
        "warning" -> "#856404"
        else -> "#0c5460"
    }
    
    messageDiv.setAttribute("style", "padding: 10px; margin-bottom: 10px; background-color: $backgroundColor; color: $color; border: 1px solid #c3e6cb; border-radius: 4px; position: fixed; top: 20px; right: 20px; z-index: 1001;")
    messageDiv.textContent = message
    
    document.body?.appendChild(messageDiv)
    
    // Auto-remove after configured delay
    window.setTimeout({
        messageDiv.remove()
    }, AppConstants.MESSAGE_AUTO_HIDE_DELAY)
}

