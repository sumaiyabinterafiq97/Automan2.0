package com.automan.purchase

import kotlin.js.asDynamic
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.ApiClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await

// Purchase Management Functions

// Global pagination variables
var currentPage = 1
var itemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allPurchases: Array<dynamic> = emptyArray()

/** When true, [allPurchases] is one server page and totals come from search API. */
var purchaseSearchServerMode: Boolean = false
var purchaseSearchServerTotal: Long = 0
var purchaseSearchServerTotalPages: Int = 0
var purchaseSearchServerPageZeroBased: Int = 0
/** API field: all | chassis | carName | brand | clientName | supplier */
var purchaseSearchFieldChoice: String = "all"
private var purchaseSearchDebounceTimer: dynamic = null
private var purchaseSuggestionDebounceTimers: MutableMap<String, Int> = mutableMapOf()
private var purchaseReturnPageFromEdit: Int? = null

private data class PurchaseSuggestionItem(
    val value: String,
    val field: String,
    val fieldLabel: String,
)

private var purchaseSuggestionItems: List<PurchaseSuggestionItem> = emptyList()
private var purchaseSuggestionActiveIndex: Int = -1
private var purchaseSuggestionCurrentInputId: String = ""
private var purchaseSuggestionApplyCallback: ((String) -> Unit)? = null
private var purchaseSuggestionCloseTimer: Int? = null
private val purchaseSuggestionStaticCache: MutableMap<String, List<String>> = mutableMapOf()

// Purchase table sort state (mirrors Car Brands master-list behavior: toggle asc/desc on each click)
// Key: purchase field (e.g. "chassis", "auctionHouse", "date")
var purchaseTableSortOrderByField: MutableMap<String, String> = mutableMapOf()
var purchaseTableSortField: String = "date"

// Purchase date filter state (used only for Purchase List table view)
private var purchaseDateFilterActive: Boolean = false
private var purchasesBeforePurchaseDateFilter: Array<dynamic> = emptyArray()
private var purchaseBaseRows: Array<dynamic> = emptyArray()
private var purchaseSearchQuery: String = ""
private var purchaseFilterSelectedColumns: MutableSet<String> = mutableSetOf()

private data class PurchaseAdvancedFilter(
    val field: String,
    val operator: String,
    val value: String = "",
    val valueTo: String = ""
)

private var purchaseAdvancedFilters: MutableList<PurchaseAdvancedFilter> = mutableListOf()

private var shippingHistoryCachedRows: Array<dynamic> = emptyArray()
private var shippingHistorySortField: String = "country"
private var shippingHistorySortOrder: String = "desc"

private val purchaseColumnCategories: Map<String, List<String>> = linkedMapOf(
    "📅 Date Fields" to listOf("date", "shipmentDate", "paymentDate", "carModelYear"),
    "🏷️ Identification" to listOf("chassis", "blNo", "bookingId", "vessel", "venueId", "clientId", "auctionNo"),
    "🚗 Vehicle Info" to listOf("brand", "carName", "color", "grade", "rank", "cc", "seat", "door", "fuel", "shift"),
    "📦 Vehicle Specs" to listOf("shipmentSize", "driveType", "wd", "options", "distance", "carPictures", "shaken", "numberCut"),
    "💼 Client & Logistics" to listOf("clientName", "consignee", "auctionHouse", "stockLocation", "pol", "country", "destination", "rixoCompany"),
    "💰 Pricing" to listOf("price", "totalPrice", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "taxTotal", "rixoPrice", "profit"),
    "📊 Shipment & Status" to listOf("shipmentCharges", "freight", "storageCharges", "miscCharges", "inspectionFee", "commission", "repairCharges", "shipped", "invoiceConfirmed", "rixoRequested", "rixoConfirmed")
)

private fun purchaseGetCategoryForField(field: String): String {
    for ((category, fields) in purchaseColumnCategories) {
        if (fields.contains(field)) return category
    }
    return "Other"
}

private val purchaseSearchDefaultSuggestionFields: List<String> = listOf(
    "chassis", "carName", "brand", "clientName", "consignee", "auctionHouse", "rixoCompany"
)

private fun purchaseAllSortableFields(): Set<String> =
    purchaseListColumnLabels().keys
        .filter { it != "vesselNo" && it != "id" && it != "createdAt" && it != "updatedAt" }
        .toSet()

private fun purchaseSetEndpointForField(field: String): String? = when (field) {
    "brand" -> "master-menu/car_brands"
    "shipmentSize" -> "master-menu/type_of_vehicle"
    "fuel" -> "master-menu/fuel"
    "shift" -> "master-menu/shift"
    "country" -> "master-menu/country"
    "pol" -> "master-menu/pol"
    "venueId" -> "master-menu/venue_id"
    "stockLocation" -> "master-menu/stock_location"
    else -> null
}

private fun purchaseMappingSuggestionSourceKey(field: String): String? = when (field) {
    "auctionHouse", "supplier", "supplierName" -> "supplier"
    "rixoCompany" -> "rixoCompany"
    "chassis" -> "chassis"
    "consignee" -> "consignee"
    "clientName", "client" -> "client"
    else -> null
}

private fun purchaseSuggestionLabel(field: String): String =
    purchaseListColumnLabels()[field] ?: when (field) {
        "auctionHouse", "supplier", "supplierName" -> "Supplier Name"
        else -> field
    }

private fun normalizeSuggestionToken(raw: String): String {
    val s = raw.trim()
    if (s.length == 0) return ""
    return s.replace(Regex("\\s+"), " ")
}

private suspend fun fetchStringListFromEndpoint(endpoint: String): List<String> {
    return try {
        val response = window.fetch(apiUrl(endpoint)).await()
        if (!response.ok) return emptyList()
        val body = response.json().await()
        val arr = if (js("Array.isArray(body)") as Boolean) {
            body.unsafeCast<Array<dynamic>>().mapNotNull { it?.toString() }
        } else {
            val data = js("body && body.data")
            if (js("Array.isArray(data)") as Boolean) {
                data.unsafeCast<Array<dynamic>>().mapNotNull { it?.toString() }
            } else {
                emptyList()
            }
        }
        arr.map { normalizeSuggestionToken(it) }.filter { it.length > 0 }.distinct()
    } catch (_: dynamic) {
        emptyList()
    }
}

private suspend fun getPurchaseMappingSuggestions(field: String, query: String): List<String> {
    val source = purchaseMappingSuggestionSourceKey(field) ?: return emptyList()
    if (source == "chassis") {
        if (query.trim().length == 0) return emptyList()
        val enc = js("encodeURIComponent")(query.trim()).unsafeCast<String>()
        val rows = fetchStringListFromEndpoint("purchases/search-chassis?query=$enc")
        return rows.take(15)
    }
    val cacheKey = "mapping:$source"
    val cached = purchaseSuggestionStaticCache[cacheKey]
    val base = if (cached != null) {
        cached
    } else {
        val loaded = when (source) {
            "supplier" -> fetchStringListFromEndpoint("master-menu/supplier")
            "rixoCompany" -> fetchStringListFromEndpoint("master-menu/rixo_company")
            "consignee" -> fetchStringListFromEndpoint("booking/mappings/consignee-names")
            "client" -> fetchStringListFromEndpoint("client-map/dropdowns/client-names")
            else -> emptyList()
        }
        purchaseSuggestionStaticCache[cacheKey] = loaded
        loaded
    }
    val q = query.trim().lowercase()
    if (q.length == 0) return base.take(15)
    return base.filter { it.lowercase().contains(q) }.take(15)
}

private suspend fun getPurchaseSetSuggestions(field: String, query: String): List<String> {
    val endpoint = purchaseSetEndpointForField(field) ?: return emptyList()
    val cacheKey = "set:$endpoint"
    val cached = purchaseSuggestionStaticCache[cacheKey]
    val base = if (cached != null) {
        cached
    } else {
        val loaded = fetchStringListFromEndpoint(endpoint)
        purchaseSuggestionStaticCache[cacheKey] = loaded
        loaded
    }
    val q = query.trim().lowercase()
    if (q.length == 0) return base.take(15)
    return base.filter { it.lowercase().contains(q) }.take(15)
}

private fun getPurchaseLocalSuggestions(field: String, query: String): List<String> {
    val rows = purchaseBaseRows
    if (rows.isEmpty()) return emptyList()
    val q = query.trim().lowercase()
    val out = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    for (row in rows) {
        val token = normalizeSuggestionToken(purchaseComparableText(row, field))
        if (token.length == 0) continue
        if (q.length > 0 && !token.lowercase().contains(q)) continue
        val k = token.lowercase()
        if (seen.add(k)) out.add(token)
        if (out.size >= 15) break
    }
    return out
}

private suspend fun getPurchaseSuggestionsForField(field: String, query: String): List<String> {
    val setEndpoint = purchaseSetEndpointForField(field)
    if (setEndpoint != null) {
        val setValues = getPurchaseSetSuggestions(field, query)
        if (setValues.isNotEmpty()) return setValues
    }
    val mappingValues = getPurchaseMappingSuggestions(field, query)
    if (mappingValues.isNotEmpty()) return mappingValues
    return getPurchaseLocalSuggestions(field, query)
}

private fun ensurePurchaseSuggestionDropdown(): HTMLElement {
    val existing = document.getElementById("purchaseSuggestionDropdown") as? HTMLElement
    if (existing != null) return existing
    val el = document.createElement("div") as HTMLElement
    el.id = "purchaseSuggestionDropdown"
    el.style.display = "none"
    el.style.position = "fixed"
    el.style.zIndex = "2147483000"
    el.style.background = "#fff"
    el.style.border = "1px solid #d1d5db"
    el.style.borderRadius = "10px"
    el.style.boxShadow = "0 12px 30px rgba(0,0,0,0.14)"
    el.style.maxHeight = "280px"
    el.style.overflowY = "auto"
    el.style.padding = "4px"
    el.addEventListener("mousedown", { ev: Event ->
        ev.preventDefault()
    })
    document.body?.appendChild(el)
    return el
}

private fun positionPurchaseSuggestionDropdown(anchorInput: HTMLInputElement) {
    val dd = ensurePurchaseSuggestionDropdown()
    val rect = anchorInput.getBoundingClientRect()
    dd.style.left = rect.left.toString() + "px"
    dd.style.top = (rect.bottom + 6).toString() + "px"
    dd.style.width = rect.width.toString() + "px"
}

private fun hidePurchaseSuggestionDropdown() {
    val dd = document.getElementById("purchaseSuggestionDropdown") as? HTMLElement ?: return
    dd.style.display = "none"
    dd.innerHTML = ""
    purchaseSuggestionItems = emptyList()
    purchaseSuggestionActiveIndex = -1
    purchaseSuggestionCurrentInputId = ""
    purchaseSuggestionApplyCallback = null
}

private fun renderPurchaseSuggestionDropdown() {
    val dd = ensurePurchaseSuggestionDropdown()
    if (purchaseSuggestionItems.isEmpty()) {
        hidePurchaseSuggestionDropdown()
        return
    }
    dd.innerHTML = purchaseSuggestionItems.mapIndexed { idx, item ->
        val active = idx == purchaseSuggestionActiveIndex
        val bg = if (active) "#eff6ff" else "transparent"
        val border = if (active) "#bfdbfe" else "transparent"
        val fieldColor = when {
            item.field in listOf("brand", "carName") -> "#3b82f6"
            item.field in listOf("chassis") -> "#8b5cf6"
            item.field in listOf("auctionHouse", "supplier", "supplierName") -> "#10b981"
            item.field in listOf("rixoCompany") -> "#f59e0b"
            item.field in listOf("consignee", "clientName", "client") -> "#ec4899"
            else -> "#6b7280"
        }
        val fieldBadge = item.fieldLabel.take(1).uppercase()
        """
        <div class="purchase-suggestion-item" data-idx="$idx" style="padding:8px 10px;border-radius:12px;cursor:pointer;background:$bg;border:1px solid $border;${if (active) "border-left:3px solid #3b82f6;padding-left:9px;" else ""}display:flex;align-items:center;gap:8px;">
            <span style="display:inline-flex;align-items:center;justify-content:center;width:18px;height:18px;border-radius:4px;background:$fieldColor;color:#fff;font-size:11px;font-weight:600;flex-shrink:0;">$fieldBadge</span>
            <div style="flex:1;min-width:0;">
                <div style="font-size:13px;color:#111827;line-height:1.3;word-break:break-word;">${escapeHtml(item.value)}</div>
            </div>
            <div style="font-size:11px;color:#9ca3af;line-height:1.2;flex-shrink:0;white-space:nowrap;">${escapeHtml(item.fieldLabel)}</div>
        </div>
        """
    }.joinToString("")
    dd.style.display = "block"
    val nodes = dd.querySelectorAll(".purchase-suggestion-item")
    for (i in 0 until nodes.length) {
        val n = nodes.item(i) as? HTMLElement ?: continue
        n.addEventListener("mouseenter", { _: Event ->
            purchaseSuggestionActiveIndex = i
            renderPurchaseSuggestionDropdown()
        })
        n.addEventListener("mousedown", { _: Event ->
            val item = purchaseSuggestionItems.getOrNull(i) ?: return@addEventListener
            purchaseSuggestionApplyCallback?.invoke(item.value)
            hidePurchaseSuggestionDropdown()
        })
    }
}

private fun resolvePurchaseSearchSuggestionFields(): List<String> {
    if (purchaseFilterSelectedColumns.isNotEmpty()) return purchaseFilterSelectedColumns.toList()
    return purchaseSearchDefaultSuggestionFields
}

private suspend fun gatherSearchSuggestions(query: String): List<PurchaseSuggestionItem> {
    val labels = purchaseListColumnLabels()
    val fields = resolvePurchaseSearchSuggestionFields().distinct().take(10)
    val merged = mutableListOf<PurchaseSuggestionItem>()
    val dedupe = mutableSetOf<String>()
    for (field in fields) {
        val values = getPurchaseSuggestionsForField(field, query)
        for (v in values.take(4)) {
            val key = (v.lowercase() + "||" + field.lowercase())
            if (!dedupe.add(key)) continue
            merged.add(PurchaseSuggestionItem(v, field, labels[field] ?: purchaseSuggestionLabel(field)))
            if (merged.size >= 24) return merged
        }
    }
    return merged
}

private fun attachSuggestionHandlersToInput(
    input: HTMLInputElement,
    inputId: String,
    singleField: String?,
    onPick: (String) -> Unit,
) {
    if (input.getAttribute("data-purchase-suggest-bound") == "true") return
    input.setAttribute("data-purchase-suggest-bound", "true")

    fun schedule() {
        val prev = purchaseSuggestionDebounceTimers[inputId]
        if (prev != null) window.clearTimeout(prev)
        val timer = window.setTimeout({
            val q = input.value.trim()
            if (q.length == 0) {
                hidePurchaseSuggestionDropdown()
                return@setTimeout
            }
            val scope = MainScope()
            scope.launch {
                val items = if (singleField != null) {
                    getPurchaseSuggestionsForField(singleField, q).take(20).map {
                        PurchaseSuggestionItem(it, singleField, purchaseSuggestionLabel(singleField))
                    }
                } else {
                    gatherSearchSuggestions(q)
                }
                if (input.value.trim() != q) return@launch
                purchaseSuggestionItems = items
                purchaseSuggestionActiveIndex = -1
                purchaseSuggestionCurrentInputId = inputId
                purchaseSuggestionApplyCallback = onPick
                positionPurchaseSuggestionDropdown(input)
                renderPurchaseSuggestionDropdown()
            }
        }, 220)
        purchaseSuggestionDebounceTimers[inputId] = timer
    }

    input.addEventListener("focus", { _: Event -> schedule() })
    input.addEventListener("input", { _: Event -> schedule() })
    input.addEventListener("keydown", { ev: Event ->
        val kev = ev.asDynamic()
        if (purchaseSuggestionCurrentInputId != inputId || purchaseSuggestionItems.isEmpty()) return@addEventListener
        when (kev.key as String) {
            "ArrowDown" -> {
                ev.preventDefault()
                purchaseSuggestionActiveIndex = (purchaseSuggestionActiveIndex + 1).coerceAtMost(purchaseSuggestionItems.lastIndex)
                renderPurchaseSuggestionDropdown()
            }
            "ArrowUp" -> {
                ev.preventDefault()
                purchaseSuggestionActiveIndex = if (purchaseSuggestionActiveIndex <= 0) 0 else purchaseSuggestionActiveIndex - 1
                renderPurchaseSuggestionDropdown()
            }
            "Enter" -> {
                if (purchaseSuggestionActiveIndex >= 0) {
                    ev.preventDefault()
                    val item = purchaseSuggestionItems.getOrNull(purchaseSuggestionActiveIndex)
                    if (item != null) {
                        onPick(item.value)
                        hidePurchaseSuggestionDropdown()
                    }
                }
            }
            "Escape" -> {
                ev.preventDefault()
                hidePurchaseSuggestionDropdown()
            }
        }
    })
    input.addEventListener("blur", { _: Event ->
        if (purchaseSuggestionCloseTimer != null) {
            window.clearTimeout(purchaseSuggestionCloseTimer!!)
        }
        purchaseSuggestionCloseTimer = window.setTimeout({
            hidePurchaseSuggestionDropdown()
        }, 130)
    })
}

private fun purchaseDateFields(): Set<String> = setOf("date", "paymentDate", "shipmentDate", "carModelYear")
private fun purchaseNumericFields(): Set<String> = setOf(
    "price", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "taxTotal", "totalPrice",
    "shipmentCharges", "freight", "storageCharges", "miscCharges", "inspectionFee", "commission",
    "repairCharges", "profit", "bookingId", "cc", "door", "seat"
)
private fun purchaseBooleanFields(): Set<String> = setOf("shipped", "shaken", "isPackageMode")
private fun purchasePresenceFields(): Set<String> = setOf("carPictures")

private fun purchaseRawFieldValue(p: dynamic, field: String): String {
    if (p == null || p == js("undefined")) return ""
    val dyn: dynamic = p
    val raw: dynamic = when (field) {
        "destination" -> dyn.destination
        "bookingId" -> dyn.bookingId ?: dyn.booking_id
        else -> dyn[field]
    }
    if (raw == null || raw == js("undefined")) return ""
    return raw.toString().trim()
}

private fun purchaseComparableText(p: dynamic, field: String): String {
    val v = when (field) {
        "supplier", "supplierName" -> purchaseRawFieldValue(p, "auctionHouse")
        else -> purchaseTableCellValue(p, field)
    }
    return v.trim()
}

private fun purchaseComparableNumber(p: dynamic, field: String): Double? {
    val raw = purchaseRawFieldValue(p, field)
    if (raw.trim().length == 0) return null
    return parseCurrency(raw).takeIf { !it.isNaN() }
}

private fun purchaseComparableDateTs(p: dynamic, field: String): Long? {
    val raw = purchaseRawFieldValue(p, field)
    if (raw.trim().length == 0) return null
    return parseDateForSorting(raw)
}

private fun purchaseComparableBool(p: dynamic, field: String): Boolean? {
    val raw = purchaseRawFieldValue(p, field).lowercase()
    if (raw.trim().length == 0) return null
    return when (raw) {
        "true", "1", "yes", "y" -> true
        "false", "0", "no", "n" -> false
        else -> null
    }
}

private fun purchaseMatchesFilter(p: dynamic, f: PurchaseAdvancedFilter): Boolean {
    val v = f.value.trim()
    if (v.length == 0) return true

    if (purchaseDateFields().contains(f.field)) {
        val ts = purchaseComparableDateTs(p, f.field) ?: return false
        val fromTs = parseDateForSorting(v) ?: return false
        val toTs = parseDateForSorting(v) ?: return false
        return ts in fromTs..toTs
    }

    val text = purchaseComparableText(p, f.field).lowercase()
    val q = v.lowercase()
    return text.contains(q)
}

private fun applyPurchaseAdvancedFilters(rows: Array<dynamic>): Array<dynamic> {
    if (purchaseAdvancedFilters.isEmpty()) return rows
    return rows.filter { row -> purchaseAdvancedFilters.all { f -> purchaseMatchesFilter(row, f) } }.toTypedArray()
}

private fun applyPurchaseTextSearch(rows: Array<dynamic>): Array<dynamic> {
    val q = purchaseSearchQuery.trim().lowercase()
    if (q.length == 0) return rows
    val fields = if (purchaseFilterSelectedColumns.isNotEmpty()) {
        purchaseFilterSelectedColumns.toList()
    } else {
        purchaseListColumnLabels().keys.filter { it != "vesselNo" }
    }
    return rows.filter { row ->
        fields.any { field ->
            purchaseComparableText(row, field).lowercase().contains(q)
        }
    }.toTypedArray()
}

private fun isoLocalToday(): String {
    // Local (not UTC) yyyy-MM-dd
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    val day = d.getDate() as Int
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

private fun isoLocalOffsetDays(daysOffset: Int): String {
    // Local (not UTC) yyyy-MM-dd
    val d = js("new Date()").unsafeCast<dynamic>()
    d.setDate(d.getDate() + daysOffset)
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    val day = d.getDate() as Int
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

private fun isoLocalThisMonthStart(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-01"
}

private fun isoLocalThisMonthEnd(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val mIndex = d.getMonth() as Int // 0..11
    val tmp = js("new Date()").unsafeCast<dynamic>()
    // day=0 => last day of previous month
    tmp.setFullYear(y, mIndex + 1, 0)
    val lastDay = tmp.getDate() as Int
    val m = mIndex + 1
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + lastDay.toString().padStart(2, '0')
}

private fun isoToLocalDayRangeTimestamps(isoDate: String): Pair<Long, Long>? {
    val trimmed = isoDate.trim()
    if (trimmed.length == 0) return null
    val parts = trimmed.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null

    // start at local 00:00:00.000
    val start = js("new Date()").unsafeCast<dynamic>()
    start.setFullYear(year, month - 1, day)
    start.setHours(0, 0, 0, 0)
    val startTs = (start.getTime() as Double).toLong()

    // end at local 23:59:59.999
    val end = js("new Date()").unsafeCast<dynamic>()
    end.setFullYear(year, month - 1, day)
    end.setHours(23, 59, 59, 999)
    val endTs = (end.getTime() as Double).toLong()
    return startTs to endTs
}

private fun applyPurchaseDateFilterRange(startIso: String, endIso: String) {
    if (purchaseSearchServerMode) {
        showMessage("Clear the search box to use purchase date filters on the full list.", "info")
        return
    }
    val range = isoToLocalDayRangeTimestamps(startIso)?.let { startPair ->
        // Recompute end timestamps from the end iso (in case they're different)
        val endRange = isoToLocalDayRangeTimestamps(endIso) ?: return
        startPair.first to endRange.second
    } ?: return

    if (!purchaseDateFilterActive) {
        purchasesBeforePurchaseDateFilter = allPurchases
        purchaseDateFilterActive = true
    }

    val (startTs, endTs) = range
    val base = purchasesBeforePurchaseDateFilter.toList()

    val filtered = base.filter { purchase ->
        val raw = purchase.date?.toString() ?: ""
        val ts = parseDateForSorting(raw)
        ts != null && ts >= startTs && ts <= endTs
    }.toTypedArray()

    allPurchases = filtered
    currentPage = 1
    displayPurchasesWithPagination()
}

private fun clearPurchaseDateFilter() {
    if (!purchaseDateFilterActive) return
    allPurchases = purchasesBeforePurchaseDateFilter
    purchaseDateFilterActive = false
    purchasesBeforePurchaseDateFilter = emptyArray()
    currentPage = 1
    displayPurchasesWithPagination()
}

// Global variable to track last device type for auto-adjustment
var lastDeviceType: String? = getDeviceType()

private fun sortPurchasesInMemory(rows: Array<dynamic>, field: String, order: String): List<dynamic> {
    val isAsc = order == "asc"
    val list = rows.toList()
    return list.sortedWith { a, b ->
        if (purchaseDateFields().contains(field)) {
            val aTs = purchaseComparableDateTs(a, field)
            val bTs = purchaseComparableDateTs(b, field)
            return@sortedWith when {
                aTs == null && bTs == null -> 0
                aTs == null -> 1
                bTs == null -> -1
                isAsc -> aTs.compareTo(bTs)
                else -> bTs.compareTo(aTs)
            }
        }
        if (purchaseNumericFields().contains(field)) {
            val aNum = purchaseComparableNumber(a, field)
            val bNum = purchaseComparableNumber(b, field)
            return@sortedWith when {
                aNum == null && bNum == null -> 0
                aNum == null -> 1
                bNum == null -> -1
                isAsc -> aNum.compareTo(bNum)
                else -> bNum.compareTo(aNum)
            }
        }
        if (purchaseBooleanFields().contains(field)) {
            val aBool = purchaseComparableBool(a, field)
            val bBool = purchaseComparableBool(b, field)
            return@sortedWith when {
                aBool == null && bBool == null -> 0
                aBool == null -> 1
                bBool == null -> -1
                isAsc -> aBool.compareTo(bBool)
                else -> bBool.compareTo(aBool)
            }
        }
        val aStr = purchaseComparableText(a, field).lowercase()
        val bStr = purchaseComparableText(b, field).lowercase()
        val aBlank = aStr.trim().length == 0
        val bBlank = bStr.trim().length == 0
        when {
            aBlank && bBlank -> 0
            aBlank -> 1
            bBlank -> -1
            isAsc -> aStr.compareTo(bStr)
            else -> bStr.compareTo(aStr)
        }
    }
}

private fun refreshPurchaseRowsFromBase(resetPage: Boolean = true) {
    if (purchaseSearchServerMode) return
    var rows = applyPurchaseAdvancedFilters(purchaseBaseRows)
    rows = applyPurchaseTextSearch(rows)
    val order = purchaseTableSortOrderByField[purchaseTableSortField] ?: "desc"
    rows = sortPurchasesInMemory(rows, purchaseTableSortField, order).toTypedArray()
    allPurchases = rows
    if (resetPage) {
        currentPage = 1
    } else {
        val totalPages = kotlin.math.max(1, kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt())
        if (currentPage < 1) currentPage = 1
        if (currentPage > totalPages) currentPage = totalPages
    }
    displayPurchasesWithPagination()
}

private fun togglePurchaseTableSort(field: String) {
    if (purchaseSearchServerMode) {
        showMessage("Clear the search box to sort the full purchase list.", "info")
        return
    }
    val current = purchaseTableSortOrderByField[field] ?: "desc"
    val next = if (current == "asc") "desc" else "asc"
    purchaseTableSortOrderByField[field] = next
    purchaseTableSortField = field

    // If a date filter is active, keep the "base list" sorted too so clearing the date filter
    // restores the same order the user selected.
    if (purchaseDateFilterActive) {
        val base = purchasesBeforePurchaseDateFilter
        purchasesBeforePurchaseDateFilter = sortPurchasesInMemory(base, field, next).toTypedArray()
    }
    refreshPurchaseRowsFromBase(resetPage = true)
}

fun showPurchaseList() {
    window.location.hash = "#/purchase"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        
        <div id="purchaseList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; background: #fafbfc;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; margin-bottom: 20px;">
                <h2 style="margin: 0;">Purchase List</h2>
                <style>
                    #purchaseList .purchase-search-filter-opt:hover { background: #f3f4f6 !important; }
                    #purchaseList .purchase-search-filter-opt--active { background: #eef2ff !important; font-weight: 600; }
                    #purchaseList .purchase-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
                    #purchaseList #purchaseSearchFilterBtn:hover { background: #e8eaed !important; box-shadow: 0 2px 8px rgba(0,0,0,0.08) !important; }
                    #purchaseList #purchaseSearchFilterBtn:focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
                </style>
                <div style="display: flex; flex-direction: column; align-items: stretch; gap: 10px; flex: 1; min-width: 0; max-width: 640px;">
                    <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                        <div style="position: relative; flex: 1; display: flex; align-items: center; min-width: 0; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.06); transition: all .3s ease;">
                            <span style="position: absolute; left: 16px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #9ca3af; display: flex;" aria-hidden="true">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                            </span>
                            <input type="text" id="purchaseSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search by chassis, brand, supplier…" aria-label="Search purchases" style="width: 100%; box-sizing: border-box; padding: 12px 40px 12px 44px; border: none; font-size: 14px; background: transparent; border-radius: 999px; outline: none; transition: all .2s ease;" />
                            <button type="button" id="purchaseSearchClearBtn" title="Clear search" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: transparent; color: #9ca3af; cursor: pointer; font-size: 20px; line-height: 1; padding: 4px 8px; border-radius: 8px; transition: all .2s ease;">×</button>
                        </div>
                        <div style="position: relative; flex-shrink: 0;">
                            <span id="purchaseSearchFieldLabel" class="purchase-sr-only" aria-live="polite">All fields</span>
                            <button type="button" id="purchaseSearchFilterBtn" title="Filter columns" aria-haspopup="true" aria-expanded="false" aria-label="Open filter columns panel." style="width: 48px; height: 48px; border-radius: 50%; border: 1px solid #e5e7eb; background: #f3f4f6; box-shadow: 0 1px 3px rgba(0,0,0,0.06); cursor: pointer; display: flex; align-items: center; justify-content: center; color: #4b5563; padding: 0; flex-shrink: 0; position: relative; transition: all .2s ease;">
                                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                    <line x1="3" y1="7" x2="21" y2="7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                    <circle cx="8" cy="7" r="2.25" fill="currentColor"/>
                                    <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                    <circle cx="16" cy="12" r="2.25" fill="currentColor"/>
                                    <line x1="3" y1="17" x2="21" y2="17" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                    <circle cx="7" cy="17" r="2.25" fill="currentColor"/>
                                </svg>
                                <span id="purchaseFilterBadge" style="position:absolute;top:-4px;right:-4px;background:#ef4444;color:#fff;border-radius:50%;width:20px;height:20px;display:none;align-items:center;justify-content:center;font-size:11px;font-weight:700;border:2px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,0.1);">0</span>
                            </button>
                        </div>
                    </div>
                </div>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="columnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            <div id="purchaseAdvancedFilterDropdown" style="display:none; margin-top: -6px; margin-bottom: 14px; border:1px solid #e5e7eb; border-radius: 12px; background:#fff; box-shadow: 0 10px 30px rgba(0,0,0,0.08);"></div>
            <div id="purchaseTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">
                    Loading purchases...
                </div>
            </div>
        </div>
    """
    // Ensure no stale selections carry over between navigations
    try {
        selectedPurchases.clear()
        updateRixoButtonVisibility()
    } catch (e: dynamic) {
        Logger.error("Error clearing selected purchases: ${e.toString()}")
    }
    
    // Check for device change and auto-adjust columns
    checkAndAdjustColumnsForDeviceChange()
    
    // Setup window resize listener for device change detection
    setupDeviceChangeListener()
    
    // Hamburger button listener is set up by ensureSidebarPresent()
    
    // Add column filter button event listener
    document.getElementById("columnFilterBtn")?.addEventListener("click", { _: Event ->
        showColumnFilterModal()
    })
    
    // Add quick access to client accounts
    document.getElementById("clientAccountsQuickBtn")?.addEventListener("click", { _: Event ->
        showClientAccountsPage()
    })
    
    // Apply role-based restrictions
    applyRoleBasedRestrictions()
    
    // Ensure sidebar and hamburger button are present
    ensureSidebarPresent()
    
    setupPurchaseSearchBarListeners()
    loadPurchases()
}

fun showShippingHistoryPage() {
    window.location.hash = "#/shipping-history"
    val content = document.getElementById("content") ?: return
    shippingHistoryCachedRows = emptyArray()
    shippingHistorySortField = "country"
    shippingHistorySortOrder = "desc"

    content.innerHTML = """
        <div id="shippingHistoryPage" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; background: #fafbfc;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; margin-bottom: 20px;">
                <h2 style="margin: 0;">Shipping History</h2>
                <div style="display: flex; flex-direction: column; align-items: stretch; gap: 10px; flex: 1; min-width: 0; max-width: 640px;">
                    <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                        <div style="position: relative; flex: 1; display: flex; align-items: center; min-width: 0; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.06); transition: all .3s ease;">
                            <span style="position: absolute; left: 16px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #9ca3af; display: flex;" aria-hidden="true">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                            </span>
                            <input type="text" id="shippingHistorySearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search country, consignee, chassis, client…" aria-label="Search shipping history" style="width: 100%; box-sizing: border-box; padding: 12px 40px 12px 44px; border: none; font-size: 14px; background: transparent; border-radius: 999px; outline: none; transition: all .2s ease;" />
                            <button type="button" id="shippingHistorySearchClearBtn" title="Clear search" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: transparent; color: #9ca3af; cursor: pointer; font-size: 20px; line-height: 1; padding: 4px 8px; border-radius: 8px;">×</button>
                        </div>
                    </div>
                </div>
            </div>
            <div id="shippingHistoryTableWrap">
                <div id="shippingHistoryTable" style="margin-top: 8px;">
                    <div style="text-align: center; color: #666; padding: 40px;">Loading shipping history…</div>
                </div>
            </div>
        </div>
    """

    applyRoleBasedRestrictions()
    ensureSidebarPresent()

    val searchInput = document.getElementById("shippingHistorySearchInput") as? HTMLInputElement
    searchInput?.addEventListener("input", { _: Event ->
        renderShippingHistoryTableFromCache()
    })
    document.getElementById("shippingHistorySearchClearBtn")?.addEventListener("click", { _: Event ->
        searchInput?.value = ""
        renderShippingHistoryTableFromCache()
    })

    val wrap = document.getElementById("shippingHistoryTableWrap")
    if (wrap != null && !wrap.hasAttribute("data-shipping-sort-delegation")) {
        wrap.setAttribute("data-shipping-sort-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? HTMLElement ?: return@addEventListener
            val btn = target.closest("button[data-shipping-sort]") as? HTMLElement ?: return@addEventListener
            e.preventDefault()
            val field = btn.getAttribute("data-shipping-sort") ?: return@addEventListener
            toggleShippingHistorySort(field)
        })
    }

    loadShippingHistory()
}

private fun loadShippingHistory() {
    val tableHost = document.getElementById("shippingHistoryTable") ?: return
    tableHost.innerHTML = """<div style="text-align: center; color: #666; padding: 40px;">Loading shipping history…</div>"""

    MainScope().launch {
        ApiClient.get<Array<dynamic>>("shipping-history").fold(
            onSuccess = { rows ->
                shippingHistoryCachedRows = rows
                renderShippingHistoryTableFromCache()
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load shipping history: $message")
                tableHost.innerHTML = """<div style="text-align: center; color: #c00; padding: 40px;">Could not load shipping history.</div>"""
            }
        )
    }
}

private fun shippingHistoryDisplayColumnKeys(): List<String> = listOf(
    "country", "consignee", "shipmentDate", "pol", "pod",
    "bookingId", "vessel", "priceType", "chassis", "clientName", "amount",
)

private fun shippingHistorySearchColumnKeys(): List<String> =
    listOf("id", "createdAt") + shippingHistoryDisplayColumnKeys()

private fun shippingHistoryColumnLabel(key: String): String = when (key) {
    "country" -> "Country"
    "consignee" -> "Consignee"
    "shipmentDate" -> "Shipment date"
    "pol" -> "POL"
    "pod" -> "POD"
    "bookingId" -> "Booking ID"
    "vessel" -> "Vessel"
    "priceType" -> "Price type"
    "chassis" -> "Chassis"
    "clientName" -> "Client name"
    "amount" -> "Amount"
    else -> key
}

private fun shippingHistoryCell(row: dynamic, key: String): String {
    val d = row
    val v: dynamic = when (key) {
        "id" -> d.id
        "country" -> d.country
        "consignee" -> d.consignee
        "shipmentDate" -> d.shipmentDate
        "pol" -> d.pol
        "pod" -> d.pod
        "bookingId" -> d.bookingId
        "vessel" -> d.vessel
        "priceType" -> d.priceType
        "chassis" -> d.chassis
        "clientName" -> d.clientName
        "amount" -> d.amount
        "createdAt" -> d.createdAt
        else -> null
    }
    if (v == null) return ""
    val undef = js("void 0")
    if (v === undef) return ""
    return v.toString().trim()
}

private fun shippingHistoryRowMatchesQuery(row: dynamic, q: String): Boolean {
    val t = q.trim().lowercase()
    if (t.isEmpty()) return true
    for (key in shippingHistorySearchColumnKeys()) {
        if (shippingHistoryCell(row, key).lowercase().contains(t)) return true
    }
    return false
}

/** Normalize country for grouping: one row per distinct country (blank → "—"). */
private fun shippingHistoryCountryGroupKey(row: dynamic): String {
    val c = shippingHistoryCell(row, "country").trim().lowercase()
    return if (c.isEmpty()) "—" else c
}

private fun shippingHistoryUniqueSortedValues(rows: List<dynamic>, key: String): List<String> {
    return rows.map { shippingHistoryCell(it, key).trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
}

/** Display label for country cell: first non-empty by stable id order. */
private fun shippingHistoryRepresentativeCountry(rows: List<dynamic>): String {
    val sorted = rows.sortedBy { shippingHistoryCell(it, "id").toLongOrNull() ?: 0L }
    val found = sorted.firstOrNull { shippingHistoryCell(it, "country").isNotEmpty() }
    return if (found != null) shippingHistoryCell(found, "country").trim() else "—"
}

private fun compareShippingHistoryGroupRows(
    rowsA: List<dynamic>,
    rowsB: List<dynamic>,
    field: String,
    asc: Boolean,
): Int {
    fun orient(c: Int): Int = if (asc) c else -c
    return when (field) {
        "amount" -> {
            val na = shippingHistoryUniqueSortedValues(rowsA, "amount").mapNotNull { it.toDoubleOrNull() }.minOrNull() ?: 0.0
            val nb = shippingHistoryUniqueSortedValues(rowsB, "amount").mapNotNull { it.toDoubleOrNull() }.minOrNull() ?: 0.0
            orient(na.compareTo(nb))
        }
        "country" -> {
            val sa = shippingHistoryCountryGroupKey(rowsA.first())
            val sb = shippingHistoryCountryGroupKey(rowsB.first())
            orient(sa.compareTo(sb))
        }
        else -> {
            val sa = shippingHistoryUniqueSortedValues(rowsA, field).minOrNull()?.lowercase() ?: ""
            val sb = shippingHistoryUniqueSortedValues(rowsB, field).minOrNull()?.lowercase() ?: ""
            val aBlank = sa.isEmpty()
            val bBlank = sb.isEmpty()
            val c = when {
                aBlank && bBlank -> 0
                aBlank -> 1
                bBlank -> -1
                else -> sa.compareTo(sb)
            }
            orient(c)
        }
    }
}

private fun toggleShippingHistorySort(field: String) {
    if (shippingHistorySortField == field) {
        shippingHistorySortOrder = if (shippingHistorySortOrder == "asc") "desc" else "asc"
    } else {
        shippingHistorySortField = field
        shippingHistorySortOrder = "desc"
    }
    renderShippingHistoryTableFromCache()
}

private fun renderShippingHistoryTableFromCache() {
    val tableHost = document.getElementById("shippingHistoryTable") ?: return
    val q = (document.getElementById("shippingHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""

    if (shippingHistoryCachedRows.isEmpty()) {
        tableHost.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No shipping history records yet.
            </div>
        """
        return
    }

    val rowList = shippingHistoryCachedRows.filter { shippingHistoryRowMatchesQuery(it, q) }.toList()

    if (rowList.isEmpty()) {
        tableHost.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No rows match your search.
            </div>
        """
        return
    }

    val groups = rowList.groupBy { shippingHistoryCountryGroupKey(it) }
        .values
        .map { memberRows ->
            memberRows.sortedBy { shippingHistoryCell(it, "id").toLongOrNull() ?: 0L }
        }
        .sortedWith { a, b ->
            compareShippingHistoryGroupRows(a, b, shippingHistorySortField, shippingHistorySortOrder == "asc")
        }

    val html = StringBuilder()
    html.append("""<div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);"><table class="purchase-list-table" style="width: 100%; border-collapse: collapse;"><thead><tr style="background-color: #f8f9fa;">""")
    for (key in shippingHistoryDisplayColumnKeys()) {
        val label = escapeHtml(shippingHistoryColumnLabel(key))
        val isActive = shippingHistorySortField == key
        val sortOrder = if (isActive) shippingHistorySortOrder else "desc"
        val tooltipRaw = when {
            !isActive -> "Sort by ${shippingHistoryColumnLabel(key)}"
            sortOrder == "asc" -> "Sorted ascending (click for descending)"
            else -> "Sorted descending (click for ascending)"
        }
        val tooltip = escapeHtml(tooltipRaw)
        html.append("""
            <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                <button type="button" data-shipping-sort="$key" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                    <span>$label</span><span style="font-size: 14px;">↕</span>
                </button>
            </th>
        """)
    }
    html.append("</tr></thead><tbody>")

    for (gRows in groups) {
        html.append("<tr>")
        for (key in shippingHistoryDisplayColumnKeys()) {
            val cellHtml = when (key) {
                "country" -> formatPurchaseListNeutralChipHtml(shippingHistoryRepresentativeCountry(gRows))
                else -> formatDistinctValueChipsHtml(shippingHistoryUniqueSortedValues(gRows, key))
            }
            html.append("""<td style="padding: 12px; vertical-align: top;">$cellHtml</td>""")
        }
        html.append("</tr>")
    }
    html.append("</tbody></table></div>")

    tableHost.innerHTML = html.toString()
}

/**
 * Check if device type changed and auto-adjust columns if needed
 */
fun checkAndAdjustColumnsForDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, auto-adjust columns
    if (lastDeviceType != null && lastDeviceType != currentDeviceType) {
        Logger.debug("Device type changed from $lastDeviceType to $currentDeviceType, auto-adjusting columns")
        
        // Get saved columns
        val saved = safeLocalStorageGet("selectedColumns")
        val savedColumns = if (saved != null) {
            try {
                JSON.parse<Array<String>>(saved).toList()
            } catch (e: dynamic) {
                null
            }
        } else {
            null
        }
        
        // Auto-adjust if needed
        if (savedColumns != null) {
            val adjustedColumns = prioritizePurchaseListDateAndChassis(
                autoAdjustColumnsForDevice(savedColumns, currentDeviceType)
            )
            
            // Save adjusted columns if they changed
            if (adjustedColumns != savedColumns) {
                safeLocalStorageSet("selectedColumns", JSON.stringify(adjustedColumns.toTypedArray()))
                Logger.debug("Auto-adjusted columns from ${savedColumns.size} to ${adjustedColumns.size} for $currentDeviceType")
            }
        }
    }
    
    // Update last device type
    lastDeviceType = currentDeviceType
}

/**
 * Setup window resize listener to detect device changes
 */
fun setupDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__deviceChangeListener
    if (existingListener != null) {
        val listenerFunc = existingListener.unsafeCast<((Event) -> Unit)?>()
        window.removeEventListener("resize", listenerFunc)
    }
    
    // Debounce resize events
    var resizeTimeout: dynamic = null
    val resizeListener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) {
            window.clearTimeout(resizeTimeout)
        }
        resizeTimeout = window.setTimeout({
            // Check if device type actually changed
            val newDeviceType = getDeviceType()
            if (lastDeviceType != null && lastDeviceType != newDeviceType) {
                // Device changed - auto-adjust columns and reload if on purchase list page
                checkAndAdjustColumnsForDeviceChange()
                
                // If we're on the purchase list page, reload to show adjusted columns
                if (window.location.hash.contains("#/purchase")) {
                    loadPurchases()
                }
            }
            lastDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__deviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadPurchases() {
    Logger.debug("loadPurchases function called")
    // Sorting dropdowns removed; always load base purchases.
    val endpoint = "purchases"
    
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<Array<dynamic>>(endpoint)
        result.fold(
            onSuccess = { purchases ->
                Logger.debug("Purchases data received: ${purchases.size} items")
                displayPurchases(purchases)
            },
            onError = { message, status ->
                Logger.error("API call failed: $message (status: $status)")
                ErrorHandler.showError("Failed to load purchases: $message")
            }
        )
    }
}

private fun purchaseSearchFieldDisplayLabel(): String = when (purchaseSearchFieldChoice) {
    "chassis" -> "Chassis"
    "carName" -> "Car name"
    "brand" -> "Brand"
    "clientName" -> "Client name"
    "supplier" -> "Supplier name"
    else -> "All fields"
}

/** Syncs filter button tooltips / screen-reader text with [purchaseSearchFieldChoice]. */
private fun refreshPurchaseSearchScopeUi() {
    val scopeLabel = purchaseSearchFieldDisplayLabel()
    val btn = document.getElementById("purchaseSearchFilterBtn") as? HTMLElement
    if (btn != null) {
        btn.setAttribute("title", "Filter — search in: $scopeLabel")
        btn.setAttribute("aria-label", "Open filter for which field to search. Current: $scopeLabel.")
    }
    val sr = document.getElementById("purchaseSearchFieldLabel") as? HTMLElement
    if (sr != null) sr.textContent = scopeLabel
}

private fun closePurchaseSearchFilterMenu() {
    val menu = document.getElementById("purchaseSearchFilterMenu") as? HTMLElement
    menu?.style?.setProperty("display", "none", "important")
    window.asDynamic().__purchaseSearchFilterMenuOpen = false
    val filterBtn = document.getElementById("purchaseSearchFilterBtn") as? HTMLElement
    filterBtn?.setAttribute("aria-expanded", "false")
}

private fun updatePurchaseSearchFilterMenuActive(selected: String) {
    val pairs = listOf(
        "all" to "purchaseSearchOptAll",
        "chassis" to "purchaseSearchOptChassis",
        "carName" to "purchaseSearchOptCarName",
        "brand" to "purchaseSearchOptBrand",
        "clientName" to "purchaseSearchOptClient",
        "supplier" to "purchaseSearchOptSupplier"
    )
    for ((value, id) in pairs) {
        val el = document.getElementById(id) as? HTMLElement ?: continue
        if (value == selected) el.classList.add("purchase-search-filter-opt--active")
        else el.classList.remove("purchase-search-filter-opt--active")
    }
}

private fun updatePurchaseFilterBadge() {
    val badge = document.getElementById("purchaseFilterBadge") as? HTMLElement ?: return
    val count = purchaseAdvancedFilters.size
    if (count == 0) {
        badge.style.display = "none"
    } else {
        badge.style.display = "flex"
        badge.textContent = count.toString()
    }
}

private fun purchaseDefaultOperatorForField(field: String): String = when {
    purchasePresenceFields().contains(field) -> "hasValue"
    purchaseBooleanFields().contains(field) -> "isTrue"
    purchaseDateFields().contains(field) -> "equals"
    purchaseNumericFields().contains(field) -> "equals"
    else -> "contains"
}

private fun purchaseOperatorOptionsForField(field: String): List<Pair<String, String>> = when {
    purchasePresenceFields().contains(field) -> listOf("hasValue" to "Has value", "noValue" to "No value")
    purchaseBooleanFields().contains(field) -> listOf("isTrue" to "TRUE", "isFalse" to "FALSE")
    purchaseDateFields().contains(field) -> listOf("equals" to "Equals", "between" to "Between", "gte" to "On/After", "lte" to "On/Before")
    purchaseNumericFields().contains(field) -> listOf("equals" to "Equals", "gte" to "Greater/Equal", "lte" to "Less/Equal", "between" to "Between")
    else -> listOf("contains" to "Contains", "equals" to "Equals", "startsWith" to "Starts with")
}

private fun showPurchaseAdvancedFilterModal() {
    val panel = document.getElementById("purchaseAdvancedFilterDropdown") as? HTMLElement ?: return
    val isOpen = panel.style.display == "block"
    if (isOpen) {
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
        return
    }
    val labels = purchaseListColumnLabels()
    
    // Build categorized columns HTML
    val categorizedHtml = StringBuilder()
    for ((category, fields) in purchaseColumnCategories) {
        val categoryFields = fields.filter { it in labels && it != "vesselNo" }
        if (categoryFields.isEmpty()) continue
        
        val chipsHtml = categoryFields.joinToString("") { field ->
            val active = purchaseFilterSelectedColumns.contains(field)
            val bg = if (active) "#38bdf8" else "#fff"
            val fg = if (active) "#ffffff" else "#374151"
            val border = if (active) "#38bdf8" else "#d1d5db"
            val label = labels[field] ?: field
            """<button type="button" class="purchase-adv-chip" data-field="$field" style="padding:8px 12px;border-radius:8px;border:1px solid $border;background:$bg;color:$fg;font-size:12px;cursor:pointer;display:inline-flex;align-items:center;gap:6px;transition:all .2s ease;">☐ ${escapeHtml(label)}</button>"""
        }
        
        categorizedHtml.append("""
            <div style="margin-bottom:12px;">
              <div class="purchase-category-header" data-category="$category" style="font-size:13px;font-weight:600;color:#374151;margin-bottom:8px;cursor:pointer;display:flex;align-items:center;gap:6px;user-select:none;">
                <span style="font-size:16px;">▼</span> $category
              </div>
              <div class="purchase-category-chips" style="display:flex;flex-wrap:wrap;gap:6px;padding:8px;border-radius:8px;background:#fafafa;">
                $chipsHtml
              </div>
            </div>
        """)
    }
    
    panel.innerHTML = """
      <div style="display:flex;flex-direction:column;height:100%;max-height:85vh;@media(max-width:768px){max-height:70vh;}">
        <div style="padding:16px 18px;border-bottom:1px solid #e5e7eb;flex-shrink:0;">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:12px;">
            <h3 style="margin:0;font-size:18px;font-weight:700;color:#111827;">🎚️ Advanced Filters</h3>
            <button id="purchaseAdvFilterClose" type="button" style="border:none;background:transparent;font-size:24px;cursor:pointer;color:#6b7280;line-height:1;padding:0;">&times;</button>
          </div>
          <input type="text" id="purchaseAdvFilterSearch" placeholder="🔍 Search columns..." style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;box-sizing:border-box;"/>
        </div>
        
        <div id="purchaseAdvCategoriesWrap" style="flex:1;overflow-y:auto;padding:12px 18px;border-bottom:1px solid #e5e7eb;">
          $categorizedHtml
        </div>
        
        <div id="purchaseAdvValueFilters" style="flex:0 0 auto;max-height:200px;overflow-y:auto;padding:12px 18px;border-bottom:1px solid #e5e7eb;"></div>
        
        <div style="padding:14px 18px;display:flex;justify-content:space-between;align-items:center;gap:10px;flex-shrink:0;@media(max-width:768px){flex-direction:column;}">
          <div style="font-size:12px;color:#6b7280;white-space:nowrap;">
            <strong>${purchaseAdvancedFilters.size}</strong> filter(s) active • Showing <strong id="purchaseAdvResultCount">0</strong> of <strong>${purchaseBaseRows.size}</strong>
          </div>
          <div style="display:flex;gap:8px;@media(max-width:768px){width:100%;}>
            <button id="purchaseAdvFilterClear" type="button" style="padding:9px 14px;border:1px solid #d1d5db;background:#fff;border-radius:8px;cursor:pointer;color:#374151;font-size:13px;transition:all .2s ease;flex:1;">🗑️ Clear</button>
            <button id="purchaseAdvFilterApply" type="button" style="padding:9px 14px;border:none;background:#0ea5e9;color:#fff;border-radius:8px;cursor:pointer;font-size:13px;font-weight:600;transition:all .2s ease;flex:1;">✓ Apply</button>
          </div>
        </div>
      </div>
    """
    panel.style.display = "block"
    (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "true")

    fun renderFilterRows() {
        val selected = purchaseFilterSelectedColumns.toList().sorted()
        val container = document.getElementById("purchaseAdvValueFilters") as? HTMLElement ?: return
        if (selected.size == 0) {
            container.innerHTML = ""
            return
        }
        val html = StringBuilder()
        selected.forEach { field ->
            val label = labels[field] ?: field
            val existing = purchaseAdvancedFilters.find { it.field == field }
            val v = existing?.value ?: ""
            val inputType = if (purchaseDateFields().contains(field)) "date" else "text"
            val icon = when {
                purchaseDateFields().contains(field) -> "📅"
                field in listOf("chassis", "blNo", "bookingId") -> "🏷️"
                purchasePriceFields().contains(field) -> "💰"
                purchaseNumericFields().contains(field) -> "🔢"
                else -> "📝"
            }
            val readOnlyAttr = if (purchaseDateFields().contains(field)) "readonly" else ""
            html.append("""
              <div class="purchase-adv-filter-row" data-field="$field" style="display:grid;grid-template-columns:auto 1fr;gap:12px;align-items:center;padding:10px 12px;border:1px solid #e5e7eb;border-radius:12px;background:#f5f7fa;border-left:4px solid #38bdf8;transition:all .3s ease;">
                <div style="display:flex;align-items:center;gap:8px;min-width:120px;">
                  <span style="font-size:14px;">$icon</span>
                  <div style="font-weight:600;color:#111827;font-size:12px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${escapeHtml(label)}</div>
                </div>
                <input class="purchase-adv-v1" type="$inputType" value="${escapeHtml(v)}" $readOnlyAttr style="display:block;padding:8px 10px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;transition:border-color .2s ease;font-family:inherit;width:100%;cursor:${if (purchaseDateFields().contains(field)) "pointer" else "text"};" placeholder="${if (purchaseDateFields().contains(field)) "mm/dd/yyyy" else "e.g. Toyota"}"/>
              </div>
            """)
        }
        container.innerHTML = html.toString()
        val inputs = container.querySelectorAll(".purchase-adv-v1")
        for (i in 0 until inputs.length) {
            val input = inputs.item(i) as? HTMLInputElement ?: continue
            var row: HTMLElement? = input.parentElement as? HTMLElement
            while (row != null && row.classList.contains("purchase-adv-filter-row").not()) {
                row = row.parentElement as? HTMLElement
            }
            if (row == null) continue
            val field = row.getAttribute("data-field") ?: continue
            val id = "purchase-adv-suggest-$field-$i"
            input.id = id
            attachSuggestionHandlersToInput(
                input = input,
                inputId = id,
                singleField = field,
                onPick = { picked ->
                    input.value = picked
                },
            )
        }
    }

    fun filterCategories(query: String) {
        val q = query.lowercase().trim()
        val categories = panel.querySelectorAll(".purchase-category-header")
        for (i in 0 until categories.length) {
            val header = categories.item(i) as? HTMLElement ?: continue
            val cat = header.getAttribute("data-category") ?: continue
            val chips = header.nextElementSibling as? HTMLElement ?: continue
            val chipNodes = chips.querySelectorAll(".purchase-adv-chip")
            var hasMatch = false
            for (j in 0 until chipNodes.length) {
                val chip = chipNodes.item(j) as? HTMLElement ?: continue
                val text = chip.textContent?.lowercase() ?: ""
                val matches = q.length == 0 || text.contains(q)
                chip.style.display = if (matches) "inline-flex" else "none"
                if (matches) hasMatch = true
            }
            val show = q.length == 0 || hasMatch
            header.style.display = if (show) "flex" else "none"
            chips.style.display = if (show) "flex" else "none"
        }
    }

    renderFilterRows()
    updatePurchaseFilterResultCount()
    
    val searchInput = document.getElementById("purchaseAdvFilterSearch") as? HTMLInputElement
    searchInput?.addEventListener("input", { _: Event ->
        filterCategories(searchInput.value)
    })

    val chipNodes = panel.querySelectorAll(".purchase-adv-chip")
    for (i in 0 until chipNodes.length) {
        val btn = chipNodes.item(i) as? HTMLElement ?: continue
        btn.addEventListener("click", { _: Event ->
            val field = btn.getAttribute("data-field") ?: return@addEventListener
            if (purchaseFilterSelectedColumns.contains(field)) purchaseFilterSelectedColumns.remove(field) else purchaseFilterSelectedColumns.add(field)
            val active = purchaseFilterSelectedColumns.contains(field)
            btn.style.setProperty("background", if (active) "#38bdf8" else "#fff")
            btn.style.setProperty("border-color", if (active) "#38bdf8" else "#d1d5db")
            btn.style.setProperty("color", if (active) "#ffffff" else "#374151")
            btn.textContent = (if (active) "☑" else "☐") + " " + btn.textContent!!.substring(1)
            renderFilterRows()
            updatePurchaseFilterResultCount()
        })
    }

    document.getElementById("purchaseAdvFilterApply")?.addEventListener("click", { _: Event ->
        val rows = panel.querySelectorAll(".purchase-adv-filter-row")
        val next = mutableListOf<PurchaseAdvancedFilter>()
        for (i in 0 until rows.length) {
            val row = rows.item(i) as? HTMLElement ?: continue
            val field = row.getAttribute("data-field") ?: continue
            val v1 = (row.querySelector(".purchase-adv-v1") as? HTMLInputElement)?.value ?: ""
            if (v1.trim().length == 0) continue
            val op = if (purchaseDateFields().contains(field)) "equals" else "contains"
            next.add(PurchaseAdvancedFilter(field = field, operator = op, value = v1))
        }
        purchaseAdvancedFilters = next
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
        updatePurchaseFilterBadge()
        refreshPurchaseRowsFromBase(resetPage = true)
        showMessage("${purchaseAdvancedFilters.size} filter(s) applied", "success")
    })
    document.getElementById("purchaseAdvFilterClear")?.addEventListener("click", { _: Event ->
        purchaseAdvancedFilters.clear()
        purchaseFilterSelectedColumns.clear()
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
        updatePurchaseFilterBadge()
        renderFilterRows()
        updatePurchaseFilterResultCount()
        refreshPurchaseRowsFromBase(resetPage = true)
        showMessage("All filters cleared", "info")
    })
    document.getElementById("purchaseAdvFilterClose")?.addEventListener("click", { _: Event ->
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
    })
}

private fun purchasePriceFields(): Set<String> = setOf(
    "price", "totalPrice", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "taxTotal",
    "rixoPrice", "rixoRequested", "rixoConfirmed"
)

private fun updatePurchaseFilterResultCount() {
    val count = applyPurchaseAdvancedFilters(purchaseBaseRows).size
    val el = document.getElementById("purchaseAdvResultCount")
    el?.textContent = count.toString()
}

fun displayPurchasesFromSearchPage(body: dynamic) {
    purchaseSearchServerMode = true
    clearPurchaseDateFilter()
    val totalEl = js("body.totalElements")
    purchaseSearchServerTotal = when (totalEl) {
        is Number -> totalEl.toLong()
        else -> totalEl?.toString()?.toLongOrNull() ?: 0L
    }
    val tp = js("body.totalPages")
    val tpRaw = when (tp) {
        is Number -> tp.toInt()
        else -> tp?.toString()?.toIntOrNull() ?: 0
    }
    purchaseSearchServerTotalPages = kotlin.math.max(1, tpRaw)
    val num = js("body.page")
    purchaseSearchServerPageZeroBased = when (num) {
        is Number -> num.toInt()
        else -> num?.toString()?.toIntOrNull() ?: 0
    }
    currentPage = purchaseSearchServerPageZeroBased + 1
    allPurchases = try {
        js("Array.from((body && body.content) ? body.content : [])").unsafeCast<Array<dynamic>>()
    } catch (_: dynamic) {
        emptyArray()
    }
    displayPurchasesWithPagination()
}

fun loadPurchasesSearchPage(page0: Int) {
    val input = document.getElementById("purchaseSearchInput") as? HTMLInputElement
    val q = input?.value?.trim() ?: ""
    if (q.length == 0) {
        purchaseSearchServerMode = false
        purchaseSearchServerTotal = 0
        purchaseSearchServerTotalPages = 0
        loadPurchases()
        return
    }
    val scope = MainScope()
    scope.launch {
        val field = purchaseSearchFieldChoice
        val encQ = js("encodeURIComponent")(q).unsafeCast<String>()
        val encF = js("encodeURIComponent")(field).unsafeCast<String>()
        val endpoint = "purchases/page-search?q=$encQ&field=$encF&page=$page0&size=$itemsPerPage"
        val result = ApiClient.get<dynamic>(endpoint)
        result.fold(
            onSuccess = { body ->
                if (body == null) {
                    ErrorHandler.showError("Empty search response")
                    return@fold
                }
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    ErrorHandler.showError(err)
                    return@fold
                }
                displayPurchasesFromSearchPage(body)
            },
            onError = { message, status ->
                Logger.error("Search API failed: $message ($status)")
                ErrorHandler.showError("Search failed: $message")
            }
        )
    }
}

private fun schedulePurchaseSearchDebounced() {
    if (purchaseSearchDebounceTimer != null) {
        window.clearTimeout(purchaseSearchDebounceTimer.unsafeCast<Int>())
        purchaseSearchDebounceTimer = null
    }
    purchaseSearchDebounceTimer = window.setTimeout({
        purchaseSearchDebounceTimer = null
        runPurchaseSearchFromInput()
    }, 420)
}

private fun runPurchaseSearchFromInput() {
    val input = document.getElementById("purchaseSearchInput") as? HTMLInputElement
    val raw = input?.value?.trim() ?: ""
    purchaseSearchQuery = raw
    purchaseSearchServerMode = false
    purchaseSearchServerTotal = 0
    purchaseSearchServerTotalPages = 0
    refreshPurchaseRowsFromBase(resetPage = true)
}

fun setupPurchaseSearchBarListeners() {
    val input = document.getElementById("purchaseSearchInput") as? HTMLInputElement ?: return
    val filterBtn = document.getElementById("purchaseSearchFilterBtn") as? HTMLElement
    val clearBtn = document.getElementById("purchaseSearchClearBtn") as? HTMLElement

    if (!input.hasAttribute("data-purchase-search-bound")) {
        input.setAttribute("data-purchase-search-bound", "true")
        input.addEventListener("input", { _: Event -> schedulePurchaseSearchDebounced() })
        input.addEventListener("keydown", { ev: Event ->
            val kev = ev.asDynamic()
            if (kev.key == "Enter") {
                ev.preventDefault()
                if (purchaseSearchDebounceTimer != null) {
                    window.clearTimeout(purchaseSearchDebounceTimer.unsafeCast<Int>())
                    purchaseSearchDebounceTimer = null
                }
                runPurchaseSearchFromInput()
            }
        })
    }
    attachSuggestionHandlersToInput(
        input = input,
        inputId = "purchaseSearchInput",
        singleField = null,
        onPick = { picked ->
            input.value = picked
            runPurchaseSearchFromInput()
        },
    )

    if (filterBtn != null && !filterBtn.hasAttribute("data-purchase-search-bound")) {
        filterBtn.setAttribute("data-purchase-search-bound", "true")
        filterBtn.addEventListener("click", { e: Event ->
            e.stopPropagation()
            showPurchaseAdvancedFilterModal()
        })
    }

    if (clearBtn != null && !clearBtn.hasAttribute("data-purchase-search-bound")) {
        clearBtn.setAttribute("data-purchase-search-bound", "true")
        clearBtn.addEventListener("click", { _: Event ->
            input.value = ""
            runPurchaseSearchFromInput()
        })
    }

    if (window.asDynamic().__purchaseAdvancedFilterOutsideAttached != true) {
        window.asDynamic().__purchaseAdvancedFilterOutsideAttached = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val panel = document.getElementById("purchaseAdvancedFilterDropdown") as? HTMLElement ?: return@addEventListener
            val btn = document.getElementById("purchaseSearchFilterBtn") as? HTMLElement
            val insidePanel = panel.contains(target)
            val insideBtn = btn != null && btn.contains(target)
            if (!insidePanel && !insideBtn) {
                panel.style.display = "none"
                btn?.setAttribute("aria-expanded", "false")
                hidePurchaseSuggestionDropdown()
            }
        })
    }
}

fun displayPurchases(purchases: dynamic) {
    val table = document.getElementById("purchaseTable")!!

    purchaseSearchServerMode = false
    purchaseSearchServerTotal = 0
    purchaseSearchServerTotalPages = 0

    // New data load => reset any active date filter to avoid stale base arrays.
    purchaseDateFilterActive = false
    purchasesBeforePurchaseDateFilter = emptyArray()
    
    if (js("purchases.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data.
            </div>
        """
        return
    }
    
    // Always sort by ID descending (newest first).
    val purchasesArray = purchases as Array<dynamic>
    val sortedPurchases = purchasesArray.sortedByDescending { purchase ->
            val id = purchase.id
            when {
                id is Number -> id.toDouble()
                id is String -> id.toDoubleOrNull() ?: 0.0
                else -> {
                    val idStr = id?.toString() ?: "0"
                    idStr.toDoubleOrNull() ?: 0.0
                }
            }
        }
    
    // Store base rows, then apply advanced filters + sort + pagination
    purchaseBaseRows = sortedPurchases.toTypedArray()
    val returnPage = purchaseReturnPageFromEdit
    if (returnPage != null && returnPage > 0) {
        currentPage = returnPage
        purchaseReturnPageFromEdit = null
        refreshPurchaseRowsFromBase(resetPage = false)
    } else {
        refreshPurchaseRowsFromBase(resetPage = true)
    }
}

private data class PurchaseListViewSlice(
    val totalPages: Int,
    val displayFrom: Int,
    val displayTo: Int,
    val totalItems: Int,
    val rows: Array<dynamic>,
)

/** One page of rows for the table/cards: either a client slice or a full server page. */
private fun computePurchaseListViewSlice(): PurchaseListViewSlice {
    if (purchaseSearchServerMode) {
        val total = purchaseSearchServerTotal.toInt().coerceAtLeast(0)
        val tp = kotlin.math.max(1, purchaseSearchServerTotalPages)
        val from = if (total == 0) 0 else purchaseSearchServerPageZeroBased * itemsPerPage + 1
        val to = if (total == 0) 0 else kotlin.math.min((purchaseSearchServerPageZeroBased + 1) * itemsPerPage, total)
        return PurchaseListViewSlice(tp, from, to, total, allPurchases)
    }
    val tp = kotlin.math.max(1, kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt())
    val s = (currentPage - 1) * itemsPerPage
    val e = kotlin.math.min(s + itemsPerPage, allPurchases.size)
    val rows = if (s < e) allPurchases.sliceArray(s until e) else emptyArray()
    return PurchaseListViewSlice(tp, s + 1, e, allPurchases.size, rows)
}

fun displayPurchasesWithPagination() {
    val table = document.getElementById("purchaseTable")!!
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        displayPurchasesAsCards()
        return
    }
    
    if (allPurchases.isEmpty()) {
        // Show table headers even when no data, so users can access filters
        val selectedColumns = getSelectedColumns()
        val sortableFields = purchaseAllSortableFields()
        val columnLabels = purchaseListColumnLabels()
    
    val tableHTML = StringBuilder()
        tableHTML.append("""<div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);"><table class="purchase-list-table" style="width: 100%; border-collapse: collapse;">""")
        tableHTML.append("<thead><tr>")
        
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            if (columnKey == "date") {
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative; overflow: visible;">
                        <div style="display:flex; align-items:center; gap:8px;">
                            <span>$label</span>
                            <button id="purchaseDateQuickFilterBtn" title="Filter by purchase date" style="background:none; border:none; cursor:pointer; font-weight:600; color:#111827; padding:0; display:inline-flex; align-items:center; justify-content:center;">
                                📅
                            </button>
                        </div>
                        <div id="purchaseDateQuickFilterMenu" style="display:none; position:absolute; top:100%; left:0; background:white; border:1px solid #ddd; border-radius:4px; padding:10px; box-sizing:border-box; box-shadow:0 2px 8px rgba(0,0,0,0.1); z-index:20000; width:260px; min-width:260px; flex-direction:column; gap:8px;">
                            <div style="display:flex; gap:8px; flex-wrap:wrap;">
                                <button id="purchaseDateQuickTodayBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Today</button>
                                <button id="purchaseDateQuickLast7Btn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Last 7 days</button>
                                <button id="purchaseDateQuickThisMonthBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">This month</button>
                                <button id="purchaseDateQuickClearBtn" style="padding:6px 10px; background:#6c757d; color:white; border:1px solid #6c757d; border-radius:4px; cursor:pointer; font-size:12px;">Clear</button>
                                <button id="purchaseDateQuickFilterApplyBtn" style="padding:6px 10px; background:#007bff; color:white; border:none; border-radius:4px; cursor:pointer; font-size:12px;">Apply</button>
                            </div>
                            <div style="border-top:1px solid #eee; padding-top:8px; margin:0;">
                                <label style="display:block; margin-bottom:6px; font-size:12px; color:#666;">Choose date</label>
                                <input type="date" id="purchaseDateQuickFilterInput" onkeydown="return false;" onpaste="return false;" ondrop="return false;" style="width:100%; padding:6px; border:1px solid #ddd; border-radius:3px; font-size:12px; margin-bottom:8px;">
                            </div>
                        </div>
                    </th>
                """)
            } else if (sortableFields.contains(columnKey)) {
                val sortOrder = purchaseTableSortOrderByField[columnKey] ?: "desc"
                val tooltip = if (sortOrder == "asc") {
                    "Sorted A-Z (click to sort Z-A)"
                } else {
                    "Sorted Z-A (click to sort A-Z)"
                }
                val sortBtnId = "purchaseSortBtn_$columnKey"
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                        <button id="$sortBtnId" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                            <span>$label</span><span style="font-size: 14px;">↕</span>
                        </button>
                    </th>
                """)
            } else {
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">$label</th>
                """)
            }
        }
        
        tableHTML.append("</tr></thead>")
        val emptyRowMsg = if (purchaseSearchServerMode) {
            "No matching purchases for this search. Try another term or change the field filter (sliders button)."
        } else {
            "No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data."
        }
        tableHTML.append("<tbody><tr><td colspan=\"${selectedColumns.size + 2}\" style=\"text-align: center; padding: 40px; color: #666;\">$emptyRowMsg</td></tr></tbody>")
        tableHTML.append("</table></div>")
        
        table.innerHTML = tableHTML.toString()
        
        // Purchase Date quick filters + calendar wiring (table header)
        for (columnKey in selectedColumns) {
            if (columnKey != "date" && sortableFields.contains(columnKey)) {
                val sortBtnId = "purchaseSortBtn_$columnKey"
                document.getElementById(sortBtnId)?.addEventListener("click", { _: Event ->
                    togglePurchaseTableSort(columnKey)
                })
            }
        }

        val btn = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
        val menu = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
        val input = document.getElementById("purchaseDateQuickFilterInput") as? HTMLInputElement
        val applyBtn = document.getElementById("purchaseDateQuickFilterApplyBtn") as? HTMLElement
        val todayBtn = document.getElementById("purchaseDateQuickTodayBtn") as? HTMLElement
        val last7Btn = document.getElementById("purchaseDateQuickLast7Btn") as? HTMLElement
        val thisMonthBtn = document.getElementById("purchaseDateQuickThisMonthBtn") as? HTMLElement
        val clearBtn = document.getElementById("purchaseDateQuickClearBtn") as? HTMLElement

        // Keep an explicit open/close flag so we never depend on style.display strings.
        // (Some browsers + !important assignments can make style.display unreliable.)
        if (window.asDynamic().__purchaseDateQuickFilterMenuOpen == null) {
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        }

        btn?.addEventListener("click", { e: Event ->
            e.stopPropagation()
            if (menu == null) return@addEventListener
            val isOpen = window.asDynamic().__purchaseDateQuickFilterMenuOpen == true
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = !isOpen
            val next = if (isOpen) "none" else "flex"
            menu.style.setProperty("display", next, "important")
        })

        applyBtn?.addEventListener("click", { _: Event ->
            val selected = input?.value ?: ""
            if (selected.trim().length > 0) {
                applyPurchaseDateFilterRange(selected, selected)
            }
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })

        todayBtn?.addEventListener("click", { _: Event ->
            val today = isoLocalToday()
            input?.value = today
            applyPurchaseDateFilterRange(today, today)
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })
        last7Btn?.addEventListener("click", { _: Event ->
            val end = isoLocalToday()
            val start = isoLocalOffsetDays(-6)
            applyPurchaseDateFilterRange(start, end)
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })
        thisMonthBtn?.addEventListener("click", { _: Event ->
            val start = isoLocalThisMonthStart()
            val end = isoLocalThisMonthEnd()
            applyPurchaseDateFilterRange(start, end)
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })

        clearBtn?.addEventListener("click", { _: Event ->
            clearPurchaseDateFilter()
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })

        if (window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached != true) {
            window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached = true
            document.addEventListener("click", { event ->
                val target = event.target as? Node ?: return@addEventListener
                val m = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
                val b = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
                if (m == null) return@addEventListener

                val clickInsideMenu = m.contains(target)
                val clickInsideBtn = b != null && b.contains(target)

                if (!clickInsideMenu && !clickInsideBtn) {
                    window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
                    m.style.setProperty("display", "none", "important")
                }
            })
        }
        return
    }
    
    val sl = computePurchaseListViewSlice()
    val totalPages = sl.totalPages
    val paginatedPurchases = sl.rows
    
    // Build table HTML
    val selectedColumns = getSelectedColumns()
    val sortableFields = purchaseAllSortableFields()
    val columnLabels = purchaseListColumnLabels()
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);">
        <table class="purchase-list-table" style="width: 100%; border-collapse: collapse;">
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
    """)
    
    for (columnKey in selectedColumns) {
        val label = columnLabels[columnKey] ?: columnKey
        if (columnKey == "date") {
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative; overflow: visible;">
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span>$label</span>
                        <button id="purchaseDateQuickFilterBtn" title="Filter by purchase date" style="background:none; border:none; cursor:pointer; font-weight:600; color:#111827; padding:0; display:inline-flex; align-items:center; justify-content:center;">
                            📅
                        </button>
                    </div>
                    <div id="purchaseDateQuickFilterMenu" style="display:none; position:absolute; top:100%; left:0; background:white; border:1px solid #ddd; border-radius:4px; padding:10px; box-sizing:border-box; box-shadow:0 2px 8px rgba(0,0,0,0.1); z-index:20000; width:260px; min-width:260px; flex-direction:column; gap:8px;">
                        <div style="display:flex; gap:8px; flex-wrap:wrap;">
                            <button id="purchaseDateQuickTodayBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Today</button>
                            <button id="purchaseDateQuickLast7Btn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Last 7 days</button>
                            <button id="purchaseDateQuickThisMonthBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">This month</button>
                            <button id="purchaseDateQuickClearBtn" style="padding:6px 10px; background:#6c757d; color:white; border:1px solid #6c757d; border-radius:4px; cursor:pointer; font-size:12px;">Clear</button>
                            <button id="purchaseDateQuickFilterApplyBtn" style="padding:6px 10px; background:#007bff; color:white; border:none; border-radius:4px; cursor:pointer; font-size:12px;">Apply</button>
                        </div>
                        <div style="border-top:1px solid #eee; padding-top:8px; margin:0;">
                            <label style="display:block; margin-bottom:6px; font-size:12px; color:#666;">Choose date</label>
                            <input type="date" id="purchaseDateQuickFilterInput" onkeydown="return false;" onpaste="return false;" ondrop="return false;" style="width:100%; padding:6px; border:1px solid #ddd; border-radius:3px; font-size:12px; margin-bottom:8px;">
                        </div>
                    </div>
                </th>
            """)
        } else if (sortableFields.contains(columnKey)) {
            val sortOrder = purchaseTableSortOrderByField[columnKey] ?: "desc"
            val tooltip = if (sortOrder == "asc") {
                "Sorted A-Z (click to sort Z-A)"
            } else {
                "Sorted Z-A (click to sort A-Z)"
            }
            val sortBtnId = "purchaseSortBtn_$columnKey"
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                    <button id="$sortBtnId" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                        <span>$label</span><span style="font-size: 14px;">↕</span>
                    </button>
                </th>
            """)
        } else {
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">$label</th>
            """)
        }
    }
    
        tableHTML.append("""
                </tr>
            </thead>
            <tbody>
    """)
    
    for (purchase in paginatedPurchases) {
        val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
        
        tableHTML.append("""
            <tr>
                <td style="padding: 8px 12px;">
                    ${if (isEditor() && purchaseId > 0L) """
                    <button class="edit-btn" data-id="${purchaseId}" aria-label="Edit" title="Edit"
                            style="display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    """ else if (isEditor()) """
                    <span style="color: #ccc; font-size: 12px;">Invalid ID</span>
                    """ else """
                    """}
                </td>
        """)
        
        for (columnKey in selectedColumns) {
            val cellValue = purchaseTableCellValue(purchase, columnKey)
            val raw = cellValue.toString().trim()
            val cellHtml = if (raw.length == 0) "" else formatPurchaseListNeutralChipHtml(raw)
            tableHTML.append("""<td style="padding: 12px; vertical-align: top;">$cellHtml</td>""")
        }
        
        tableHTML.append("""</tr>""")
    }
    
    tableHTML.append("""
            </tbody>
        </table>
        </div>
    """)
    
    // Add pagination controls
    val suffix = if (purchaseSearchServerMode) " (search results)" else ""
    if (purchaseSearchServerMode || totalPages > 1) {
        tableHTML.append("""
            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 20px; padding: 10px;">
                <div style="color: #666;">
                    Showing ${sl.displayFrom} to ${sl.displayTo} of ${sl.totalItems} purchases$suffix
                </div>
                <div style="display: flex; gap: 10px;">
                    <button id="prevPageBtn" ${if (currentPage == 1) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (currentPage == 1) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (currentPage == 1) "not-allowed" else "pointer"};">
                        Previous
                    </button>
                    <span style="padding: 8px 16px; color: #666;">
                        Page $currentPage of $totalPages
                    </span>
                    <button id="nextPageBtn" ${if (currentPage == totalPages) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (currentPage == totalPages) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (currentPage == totalPages) "not-allowed" else "pointer"};">
                        Next
                    </button>
                </div>
            </div>
        """)
    }
    
    table.innerHTML = tableHTML.toString()
    
    // Add event listeners for edit buttons
    val editButtons = document.querySelectorAll(".edit-btn")
    for (i in 0 until editButtons.length) {
        val button = editButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val btn = event.currentTarget as HTMLElement
            val id = btn.getAttribute("data-id")
            if (id != null && id.length > 0 && id != "0") {
                purchaseReturnPageFromEdit = currentPage
                window.location.hash = "#/edit/$id"
            } else {
                showMessage("Invalid purchase ID. Cannot edit this purchase.", "error")
            }
        })
    }
    
    // Purchase Date quick filters + calendar wiring (table header)
    val btn = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
    val menu = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
    val input = document.getElementById("purchaseDateQuickFilterInput") as? HTMLInputElement
    val applyBtn = document.getElementById("purchaseDateQuickFilterApplyBtn") as? HTMLElement
    val todayBtn = document.getElementById("purchaseDateQuickTodayBtn") as? HTMLElement
    val last7Btn = document.getElementById("purchaseDateQuickLast7Btn") as? HTMLElement
    val thisMonthBtn = document.getElementById("purchaseDateQuickThisMonthBtn") as? HTMLElement
    val clearBtn = document.getElementById("purchaseDateQuickClearBtn") as? HTMLElement

    // Keep an explicit open/close flag so we never depend on style.display strings.
    if (window.asDynamic().__purchaseDateQuickFilterMenuOpen == null) {
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
    }

    btn?.addEventListener("click", { e: Event ->
        e.stopPropagation()
        if (menu == null) return@addEventListener
        val isOpen = window.asDynamic().__purchaseDateQuickFilterMenuOpen == true
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = !isOpen
        val next = if (isOpen) "none" else "flex"
        menu.style.setProperty("display", next, "important")
    })

    applyBtn?.addEventListener("click", { _: Event ->
        val selected = input?.value ?: ""
        if (selected.trim().length > 0) {
            applyPurchaseDateFilterRange(selected, selected)
        }
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })

    todayBtn?.addEventListener("click", { _: Event ->
        val today = isoLocalToday()
        input?.value = today
        applyPurchaseDateFilterRange(today, today)
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })
    last7Btn?.addEventListener("click", { _: Event ->
        val end = isoLocalToday()
        val start = isoLocalOffsetDays(-6)
        applyPurchaseDateFilterRange(start, end)
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })
    thisMonthBtn?.addEventListener("click", { _: Event ->
        val start = isoLocalThisMonthStart()
        val end = isoLocalThisMonthEnd()
        applyPurchaseDateFilterRange(start, end)
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })
    clearBtn?.addEventListener("click", { _: Event ->
        clearPurchaseDateFilter()
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })

    if (window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached != true) {
        window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val m = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
            val b = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
            if (m == null) return@addEventListener

            val clickInsideMenu = m.contains(target)
            val clickInsideBtn = b != null && b.contains(target)

            if (!clickInsideMenu && !clickInsideBtn) {
                window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
                m.style.setProperty("display", "none", "important")
            }
        })
    }
    for (columnKey in selectedColumns) {
        if (columnKey != "date" && sortableFields.contains(columnKey)) {
            val sortBtnId = "purchaseSortBtn_$columnKey"
            document.getElementById(sortBtnId)?.addEventListener("click", { _: Event ->
                togglePurchaseTableSort(columnKey)
            })
        }
    }
    
    // Setup pagination event listeners
    document.getElementById("prevPageBtn")?.addEventListener("click", { _: Event ->
        if (purchaseSearchServerMode) {
            if (purchaseSearchServerPageZeroBased > 0) {
                loadPurchasesSearchPage(purchaseSearchServerPageZeroBased - 1)
            }
        } else if (currentPage > 1) {
            currentPage--
            displayPurchasesWithPagination()
        }
    })
    
    document.getElementById("nextPageBtn")?.addEventListener("click", { _: Event ->
        if (purchaseSearchServerMode) {
            val tp = kotlin.math.max(1, purchaseSearchServerTotalPages)
            if (purchaseSearchServerPageZeroBased < tp - 1) {
                loadPurchasesSearchPage(purchaseSearchServerPageZeroBased + 1)
            }
        } else {
        val totalPages = kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt()
        if (currentPage < totalPages) {
            currentPage++
            displayPurchasesWithPagination()
            }
        }
    })
}

/**
 * Display purchases as cards for mobile view
 */
fun displayPurchasesAsCards() {
    val table = document.getElementById("purchaseTable")!!
    
    if (allPurchases.isEmpty()) {
        val emptyMsg = if (purchaseSearchServerMode) {
            "No matching purchases for this search. Try another term or change the field filter."
        } else {
            "No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data."
        }
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                $emptyMsg
            </div>
        """
        return
    }
    
    val sl = computePurchaseListViewSlice()
    val totalPages = sl.totalPages
    val paginatedPurchases = sl.rows
    
    val selectedColumns = getSelectedColumns()
    val columnLabels = purchaseListColumnLabels()
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="purchase-cards-container">""")
    
    for (purchase in paginatedPurchases) {
        val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
        val chassis = purchase.chassis ?: ""
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = purchaseTableCellValue(purchase, columnKey)
            
            // Convert to string and check if not blank (safe for JS strings)
            val valueStr = value.toString()
            val isValueNotEmpty = valueStr.length > 0 && valueStr.trim().length > 0
            
            if (isValueNotEmpty) {
                val cellDisplay = formatPurchaseListNeutralChipHtml(valueStr.trim())
                cardFields.append("""
                    <div class="card-field">
                        <span class="card-label">$label:</span>
                        <div class="card-value">$cellDisplay</div>
                    </div>
                """)
            }
        }
        
        // Safe check for chassis (convert to string first)
        val chassisStr = chassis.toString()
        val hasChassis = chassisStr.length > 0 && chassisStr.trim().length > 0
        
        cardsHTML.append("""
            <div class="purchase-card">
                <div class="card-header">
                    ${if (isEditor() && purchaseId > 0L) """
                    <button class="card-edit-btn" data-id="${purchaseId}" aria-label="Edit" title="Edit">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    """ else ""}
                    <div class="card-title">${if (hasChassis) chassisStr else "Purchase #$purchaseId"}</div>
                </div>
                <div class="card-body">
                    $cardFields
                </div>
            </div>
        """)
    }
    
    cardsHTML.append("</div>")
    
    val suffixM = if (purchaseSearchServerMode) " (search)" else ""
    if (purchaseSearchServerMode || totalPages > 1) {
        cardsHTML.append("""
            <div class="pagination-controls">
                <div class="pagination-info">
                    Showing ${sl.displayFrom} to ${sl.displayTo} of ${sl.totalItems} purchases$suffixM
                </div>
                <div class="pagination-buttons">
                    <button id="prevPageBtn" ${if (currentPage == 1) "disabled" else ""} class="pagination-btn ${if (currentPage == 1) "disabled" else ""}">
                        Previous
                    </button>
                    <span class="pagination-page">
                        Page $currentPage of $totalPages
                    </span>
                    <button id="nextPageBtn" ${if (currentPage == totalPages) "disabled" else ""} class="pagination-btn ${if (currentPage == totalPages) "disabled" else ""}">
                        Next
                    </button>
                </div>
            </div>
        """)
    }
    
    table.innerHTML = cardsHTML.toString()
    
    // Add event listeners for edit buttons
    val editButtons = document.querySelectorAll(".card-edit-btn")
    for (i in 0 until editButtons.length) {
        val button = editButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val btn = event.currentTarget as HTMLElement
            val id = btn.getAttribute("data-id")
            if (id != null && id.length > 0 && id != "0") {
                purchaseReturnPageFromEdit = currentPage
                window.location.hash = "#/edit/$id"
            } else {
                showMessage("Invalid purchase ID. Cannot edit this purchase.", "error")
            }
        })
    }
    
    // Setup pagination event listeners
    document.getElementById("prevPageBtn")?.addEventListener("click", { _: Event ->
        if (purchaseSearchServerMode) {
            if (purchaseSearchServerPageZeroBased > 0) {
                loadPurchasesSearchPage(purchaseSearchServerPageZeroBased - 1)
            }
        } else if (currentPage > 1) {
            currentPage--
            displayPurchasesWithPagination()
        }
    })
    
    document.getElementById("nextPageBtn")?.addEventListener("click", { _: Event ->
        if (purchaseSearchServerMode) {
            val tp = kotlin.math.max(1, purchaseSearchServerTotalPages)
            if (purchaseSearchServerPageZeroBased < tp - 1) {
                loadPurchasesSearchPage(purchaseSearchServerPageZeroBased + 1)
            }
        } else {
        val totalPages = kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt()
        if (currentPage < totalPages) {
            currentPage++
            displayPurchasesWithPagination()
            }
        }
    })
}

fun deletePurchase(id: Long) {
    if (window.confirm("Are you sure you want to delete this purchase?")) {
        val scope = MainScope()
        scope.launch {
            val result = ApiClient.delete<dynamic>("purchases/$id")
            result.fold(
                onSuccess = {
                    ErrorHandler.showSuccess("Purchase deleted successfully!")
                    // Always return to the main list after deletion
                    showPurchaseList()
                },
                onError = { message, _ ->
                    Logger.error("Failed to delete purchase: $message")
                    ErrorHandler.showError("Failed to delete purchase: $message")
                }
            )
        }
    }
}

// Helper functions (these may be defined elsewhere or need to be implemented)
fun getSelectedColumns(): List<String> {
    // Get current device type
    val deviceType = getDeviceType()
    val defaultColumns = getDefaultColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return prioritizePurchaseListDateAndChassis(defaultColumns)
    }
    
    // Filter out removed columns (displacement, packagePrice, id/audit fields)
    val validColumns = savedColumns.filter {
        it != "displacement" && it != "packagePrice" &&
            it != "id" && it != "createdAt" && it != "updatedAt"
    }
    
    // Auto-adjust if saved columns exceed device limit, then pin date/chassis to the left
    return prioritizePurchaseListDateAndChassis(autoAdjustColumnsForDevice(validColumns, deviceType))
}

fun setupColumnSorting() {
    // Setup column sorting functionality
    val sortableColumns = document.querySelectorAll(".sortable-column")
    for (i in 0 until sortableColumns.length) {
        val column = sortableColumns.item(i) as HTMLElement
        column.addEventListener("click", { _: Event ->
            val field = column.getAttribute("data-field")
            if (field != null) {
                // Toggle sort order
                currentSortOrder = if (currentSortField == field && currentSortOrder == "asc") "desc" else "asc"
                currentSortField = field
                loadPurchases()
            }
        })
    }
}

fun showColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("columnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "columnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; align-items: center; justify-content: center;
    """
    
    // Get current device type and limits
    val deviceType = getDeviceType()
    val maxColumns = getMaxPurchaseListColumnsForDevice(deviceType)
    val deviceDisplayName = when (deviceType) {
        "mobile" -> "Mobile View"
        "tablet" -> "Tablet View"
        else -> "Desktop View"
    }
    
    val selectedColumnsList = getSelectedColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="selectedCount">0</span></span>
            </div>
            <div id="columnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes (using function from MinimalPurchaseApp.kt)
    populateColumnCheckboxes(selectedColumns)
    
    // Update selection count initially (this will set the correct format)
    updateColumnSelection()
    
    // Add event listeners
    document.getElementById("closeColumnFilter")?.addEventListener("click", { _: Event ->
        closeColumnFilterModal()
    })
    document.getElementById("resetColumns")?.addEventListener("click", { _: Event ->
        resetToDefaultColumns()
    })
    document.getElementById("applyColumns")?.addEventListener("click", { _: Event ->
        applyColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "columnFilterModal") {
            closeColumnFilterModal()
        }
    })
}

// External variables (defined in MinimalPurchaseApp.kt or shared state)
// selectedPurchases, currentSortField, and currentSortOrder are declared as private vars in MinimalPurchaseApp.kt

fun updateRixoButtonVisibility() {
    // Implementation for updating Rixo button visibility
}

// applyRoleBasedRestrictions moved to AuthSetup.kt

fun ensureSidebarPresent() {
    // Ensure sidebar exists (created in createApp)
    val sidebar = document.getElementById("sidebar")
    if (sidebar == null) {
        console.error("Sidebar not found! Make sure createApp() was called.")
        return
    }
    
    // Show hamburger button
    val hamburgerContainer = document.getElementById("hamburgerBtnContainer") as? HTMLElement
    hamburgerContainer?.style?.setProperty("display", "block")
    
    // Setup hamburger button listener
    setupHamburgerListener()
    
    // Setup sidebar listeners
    setupSidebarListeners()
    
    // Setup sidebar close button listener
    val closeSidebarBtn = document.getElementById("closeSidebar")
    if (closeSidebarBtn != null && !closeSidebarBtn.hasAttribute("data-listener-attached")) {
        closeSidebarBtn.setAttribute("data-listener-attached", "true")
        closeSidebarBtn.addEventListener("click", { _: Event ->
            closeSidebar()
        })
    }
    
    // Setup overlay click listener to close sidebar
    val overlay = document.getElementById("sidebarOverlay")
    if (overlay != null && !overlay.hasAttribute("data-listener-attached")) {
        overlay.setAttribute("data-listener-attached", "true")
        overlay.addEventListener("click", { _: Event ->
            closeSidebar()
        })
    }
}

