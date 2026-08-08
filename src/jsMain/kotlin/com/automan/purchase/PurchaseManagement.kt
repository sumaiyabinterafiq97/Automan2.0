package com.automan.purchase

import kotlin.js.Date
import kotlin.js.asDynamic
import kotlin.js.unsafeCast
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
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

/** sessionStorage: JSON payload when opening Create Shipping Schedule from Shipping History Edit. */
const val SHIPPING_HISTORY_EDIT_SESSION_KEY = "shippingHistoryEditPayload"
/** Persists shipping-history row ids for #/recalculate-booking after inner navigation. */
const val SHIPPING_RECREATE_META_SESSION_KEY = "shippingRecreateMeta"
/** One-shot payload when opening Create Invoice from a Shipping History group row. */
const val SHIPPING_HISTORY_INVOICE_PREFILL_SESSION_KEY = "shippingHistoryInvoicePrefillPayload"

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
/** When true, next [showPurchaseList] keeps filters (returning from edit purchase). */
private var preservePurchaseListFiltersOnNextShow: Boolean = false
private var restoringPurchaseListFromEdit: Boolean = false
private var purchaseDateFilterStartIso: String = ""
private var purchaseDateFilterEndIso: String = ""

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

/** Debounce timer for PUT users/{id}/views after sort/column changes. */
private var purchaseListViewsSaveTimer: dynamic = null
private var purchaseListViewsLoadedForUserId: String? = null

fun clearPurchaseListViewsSessionCache() {
    purchaseListViewsLoadedForUserId = null
    if (purchaseListViewsSaveTimer != null) {
        window.clearTimeout(purchaseListViewsSaveTimer)
        purchaseListViewsSaveTimer = null
    }
}

private fun currentAuthUserId(): Long? =
    safeLocalStorageGet("authUserId")?.trim()?.toLongOrNull()

/**
 * Load Purchase List sort + columns from users.views.
 * Server wins when present; if server has no columns but localStorage does, migrate once.
 */
private suspend fun loadPurchaseListViewsFromServer() {
    val userId = currentAuthUserId() ?: return
    val result = ApiClient.get<dynamic>("users/$userId/views")
    when (result) {
        is ApiResult.Error -> {
            Logger.warn("Purchase list views load failed: ${result.message}")
            return
        }
        is ApiResult.Success -> {
            val body = result.data ?: return
            val pl = body.purchaseList
            applyPurchaseListViewsFromServer(pl)
            purchaseListViewsLoadedForUserId = userId.toString()
            // One-time migrate: local columns exist, server has none
            val serverCols = try {
                val arr = pl?.columns
                if (arr == null || arr == js("undefined")) emptyList()
                else {
                    val asArr = js("Array.isArray(arr) ? arr : []") as Array<dynamic>
                    asArr.map { it.toString() }.filter { it.isNotBlank() }
                }
            } catch (_: dynamic) {
                emptyList()
            }
            if (serverCols.isEmpty()) {
                val local = safeLocalStorageGet("selectedColumns")
                if (!local.isNullOrBlank()) {
                    persistPurchaseListViewsToServer(immediate = true)
                }
            }
        }
    }
}

private fun applyPurchaseListViewsFromServer(pl: dynamic?) {
    if (pl == null || pl == js("undefined")) return
    val sortField = (pl.sortField as? String)?.trim().orEmpty()
    if (sortField.isNotEmpty()) {
        purchaseTableSortField = sortField
    }
    val sortOrder = (pl.sortOrder as? String)?.trim()?.lowercase().orEmpty()
    if (sortOrder == "asc" || sortOrder == "desc") {
        purchaseTableSortOrderByField[purchaseTableSortField] = sortOrder
    }
    try {
        val mapDyn = pl.sortOrderByField
        if (mapDyn != null && mapDyn != js("undefined")) {
            val keys = js("Object.keys(mapDyn)") as Array<String>
            for (k in keys) {
                val v = (mapDyn[k] as? String)?.trim()?.lowercase().orEmpty()
                if (v == "asc" || v == "desc") {
                    purchaseTableSortOrderByField[k] = v
                }
            }
        }
    } catch (_: dynamic) {
    }
    try {
        val arr = pl.columns
        if (arr != null && arr != js("undefined") && js("Array.isArray(arr)").unsafeCast<Boolean>()) {
            val list = (arr as Array<dynamic>).map { it.toString().trim() }.filter { it.isNotEmpty() }
            if (list.isNotEmpty()) {
                val deviceType = getDeviceType()
                val max = getMaxPurchaseListColumnsForDevice(deviceType)
                val ordered = ensurePurchaseListPinnedColumns(
                    prioritizePurchaseListDateAndChassis(sanitizePurchaseListSelectedColumns(list)),
                    max,
                )
                saveSelectedColumns(ordered)
            }
        }
    } catch (e: dynamic) {
        Logger.warn("Purchase list views columns apply failed: ${e.toString()}")
    }
}

private fun buildPurchaseListViewsPayload(): dynamic {
    val pl = js("{}")
    pl.sortField = purchaseTableSortField
    pl.sortOrder = purchaseTableSortOrderByField[purchaseTableSortField] ?: "desc"
    val orderMap = js("{}")
    for ((k, v) in purchaseTableSortOrderByField) {
        orderMap[k] = v
    }
    pl.sortOrderByField = orderMap
    pl.columns = getSelectedColumns().toTypedArray()
    val body = js("{}")
    body.purchaseList = pl
    return body
}

fun persistPurchaseListViewsToServer(immediate: Boolean = false) {
    val userId = currentAuthUserId() ?: return
    val runSave = {
        MainScope().launch {
            val body = buildPurchaseListViewsPayload()
            when (val r = ApiClient.put<dynamic>("users/$userId/views", body)) {
                is ApiResult.Success -> Logger.debug("Purchase list views saved")
                is ApiResult.Error -> Logger.warn("Purchase list views save failed: ${r.message}")
            }
        }
    }
    if (immediate) {
        if (purchaseListViewsSaveTimer != null) {
            window.clearTimeout(purchaseListViewsSaveTimer)
            purchaseListViewsSaveTimer = null
        }
        runSave()
        return
    }
    if (purchaseListViewsSaveTimer != null) window.clearTimeout(purchaseListViewsSaveTimer)
    purchaseListViewsSaveTimer = window.setTimeout({ runSave() }, 400)
}

// Purchase date filter state (used only for Purchase List table view)
private var purchaseDateFilterActive: Boolean = false
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
private var shippingHistorySortField: String = "bookingId"
private var shippingHistorySortOrder: String = "desc"
private var shippingHistoryResizeDebounceHandle: Int? = null
private var shippingHistorySearchDebounceHandle: Int? = null
private var shippingHistoryLastCompactLayout: Boolean? = null
private var shippingHistoryColumnFilterKeyHandler: ((Event) -> Unit)? = null
private var shippingHistoryInvoiceAlreadyCreatedKeyHandler: ((Event) -> Unit)? = null
private var shippingHistoryServerMode: Boolean = true
private var shippingHistoryPageZeroBased: Int = 0
private var shippingHistoryTotalPages: Int = 1
private var shippingHistoryTotalElements: Long = 0L
private var shippingHistoryItemsPerPage: Int = AppConstants.DEFAULT_ITEMS_PER_PAGE
private var shippingHistoryActiveSearchQ: String = ""

private const val SHIPPING_HISTORY_COMPACT_MAX_WIDTH_PX = 860
private const val PURCHASE_LIST_COMPACT_MAX_WIDTH_PX = 860
private var purchaseListResizeDebounceHandle: Int? = null
private var purchaseListLastCompactLayout: Boolean? = null
internal var columnFilterModalKeyHandler: ((Event) -> Unit)? = null

private val purchaseColumnCategories: Map<String, List<String>> = linkedMapOf(
    "📅 Date Fields" to listOf("date", "shipmentDate", "paymentDate", "carModelYear"),
    "🏷️ Identification" to listOf("chassis", "blNo", "bookingId", "vessel", "venueId", "clientId", "auctionNo"),
    "🚗 Vehicle Info" to listOf("brand", "carName", "color", "grade", "rank", "cc", "seat", "door", "fuel", "shift"),
    "📦 Vehicle Specs" to listOf("shipmentSize", "driveType", "wd", "options", "distance", "carPictures", "shaken", "numberCut"),
    "💼 Client & Logistics" to listOf("clientName", "consignee", "auctionHouse", "stockLocation", "pol", "country", "destination", "rixoCompany"),
    "💰 Pricing" to listOf("price", "totalPrice", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "taxTotal", "rixoPrice", "profit"),
    "📊 Shipment & Status" to listOf("shipmentCharges", "freight", "storageCharges", "miscCharges", "inspectionFee", "commission", "repairCharges", "bookingRequested", "invoiceConfirmed", "rixoRequested", "rixoConfirmed")
)

/** Purchase / Payment / Shipment — full day, same controls as Edit Purchase. */
private fun purchaseAdvFilterFullDayDateFields(): Set<String> = setOf("date", "paymentDate", "shipmentDate")

/** Hidden from Advanced Filters chip list (still valid table columns). */
private val purchaseAdvFilterExcludedFields: Set<String> = setOf(
    "clientId", "carPictures", "shaken", "taxTotal", "profit",
    "bookingRequested", "invoiceConfirmed", "rixoRequested", "rixoConfirmed",
    "consignee", "pod", "destination", "isPackageMode",
)

private fun purchaseAdvPlainIntFields(): Set<String> = setOf("cc", "seat", "door")

private fun purchaseAdvCommaIntFields(): Set<String> = setOf("distance")

private fun purchaseAdvMoneyFields(): Set<String> = setOf(
    "price", "totalPrice", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "rixoPrice",
    "shipmentCharges", "freight", "storageCharges", "miscCharges", "inspectionFee", "commission", "repairCharges",
)

private fun purchaseAdvPurgeExcludedSelections() {
    purchaseFilterSelectedColumns.removeAll(purchaseAdvFilterExcludedFields)
    purchaseAdvancedFilters.removeAll { it.field in purchaseAdvFilterExcludedFields }
}

private fun purchaseAdvSanitizePlainInt(raw: String): String = raw.replace(Regex("[^0-9]"), "")

private fun purchaseAdvSanitizeMoney(raw: String): String {
    if (raw.isBlank()) return ""
    val fn = js("window._moneySanitize")
    return if (fn != js("undefined")) fn(raw).unsafeCast<String>().trim()
    else raw.replace(Regex("[^0-9.]"), "")
}

private fun purchaseAdvSanitizeCommaInt(raw: String, isKm: Boolean): String {
    if (raw.isBlank()) return ""
    val stripped = if (isKm) raw.replace(Regex("\\s*km\\s*$", RegexOption.IGNORE_CASE), "").trim() else raw
    val fn = js("window._commaIntSanitize")
    return if (fn != js("undefined")) fn(stripped).unsafeCast<String>().trim()
    else stripped.replace(Regex("[^0-9]"), "")
}

private fun purchaseAdvFormatMoneyDisplay(v: String): String {
    if (v.isBlank()) return ""
    val core = purchaseAdvSanitizeMoney(v)
    if (core.isBlank()) return ""
    val fn = js("window._moneyFormat")
    return if (fn != js("undefined")) fn(core).unsafeCast<String>() else core
}

private fun purchaseAdvFormatCommaIntDisplay(v: String, isKm: Boolean): String {
    if (v.isBlank()) return ""
    val core = purchaseAdvSanitizeCommaInt(v, isKm)
    if (core.isBlank()) return ""
    if (isKm) {
        val fn = js("window._commaIntFormat")
        val formatted = if (fn != js("undefined")) fn(core).unsafeCast<String>() else core
        return if (formatted.isNotBlank()) "$formatted km" else ""
    }
    val fn = js("window._commaIntFormat")
    return if (fn != js("undefined")) fn(core).unsafeCast<String>() else core
}

private fun purchaseAdvNumericFilterMatch(p: dynamic, field: String, filterValue: String): Boolean {
    val filterNum = parseCurrency(filterValue)
    if (!filterNum.isNaN()) {
        purchaseComparableNumber(p, field)?.let { rowNum ->
            if (rowNum == filterNum) return true
        }
    }
    val rowDigits = purchaseComparableText(p, field).replace(Regex("[^0-9]"), "")
    val filterDigits = filterValue.replace(Regex("[^0-9]"), "")
    if (filterDigits.isEmpty()) return false
    return rowDigits.contains(filterDigits)
}

private val purchaseAdvFilterLeftColumnCategories: List<String> = listOf(
    "📅 Date Fields",
    "🏷️ Identification",
    "🚗 Vehicle Info",
    "📦 Vehicle Specs",
)

private val purchaseAdvFilterRightColumnCategories: List<String> = listOf(
    "💼 Client & Logistics",
    "💰 Pricing",
    "📊 Shipment & Status",
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
    wirePurchaseSuggestionDropdownDelegation(el)
    document.body?.appendChild(el)
    return el
}

private fun wirePurchaseSuggestionDropdownDelegation(dd: HTMLElement) {
    if (dd.getAttribute("data-purchase-suggest-delegation") == "true") return
    dd.setAttribute("data-purchase-suggest-delegation", "true")
    dd.addEventListener("mousedown", { ev: Event ->
        val target = ev.target
        val el = when (target) {
            is HTMLElement -> target.closest(".purchase-suggestion-item") as? HTMLElement
            else -> null
        } ?: return@addEventListener
        ev.preventDefault()
        ev.stopPropagation()
        val idx = el.getAttribute("data-idx")?.toIntOrNull() ?: return@addEventListener
        val item = purchaseSuggestionItems.getOrNull(idx) ?: return@addEventListener
        purchaseSuggestionApplyCallback?.invoke(item.value)
        hidePurchaseSuggestionDropdown()
    })
    dd.addEventListener("mouseover", { ev: Event ->
        val target = ev.target
        val el = when (target) {
            is HTMLElement -> target.closest(".purchase-suggestion-item") as? HTMLElement
            else -> null
        } ?: return@addEventListener
        val idx = el.getAttribute("data-idx")?.toIntOrNull() ?: return@addEventListener
        if (idx == purchaseSuggestionActiveIndex) return@addEventListener
        purchaseSuggestionActiveIndex = idx
        updatePurchaseSuggestionActiveHighlight()
    })
}

private fun updatePurchaseSuggestionActiveHighlight() {
    val dd = document.getElementById("purchaseSuggestionDropdown") as? HTMLElement ?: return
    val nodes = dd.querySelectorAll(".purchase-suggestion-item")
    for (i in 0 until nodes.length) {
        val n = nodes.item(i) as? HTMLElement ?: continue
        val active = i == purchaseSuggestionActiveIndex
        n.style.background = if (active) "#eff6ff" else "transparent"
        n.style.border = if (active) "1px solid #bfdbfe" else "1px solid transparent"
        if (active) {
            n.style.setProperty("border-left", "3px solid #3b82f6")
            n.style.paddingLeft = "9px"
        } else {
            n.style.removeProperty("border-left")
            n.style.paddingLeft = "10px"
        }
    }
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
    updatePurchaseSuggestionActiveHighlight()
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
                updatePurchaseSuggestionActiveHighlight()
            }
            "ArrowUp" -> {
                ev.preventDefault()
                purchaseSuggestionActiveIndex = if (purchaseSuggestionActiveIndex <= 0) 0 else purchaseSuggestionActiveIndex - 1
                updatePurchaseSuggestionActiveHighlight()
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

/** Normalizes API/display production month to `yyyy-MM` for filter comparison. */
private fun purchaseNormalizeYearMonth(raw: String): String? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    if (t.matches(Regex("^\\d{4}-\\d{2}$"))) return t
    strictMmYyyySlashToIsoMonth(t)?.let { return it }
    return null
}

/** Restores advanced-filter value to ISO `yyyy-MM-dd` for Edit-style date pickers. */
private fun purchaseAdvInitialIsoDay(v: String): String {
    val t = v.trim()
    if (t.isEmpty()) return ""
    if (t.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return t
    strictMmDdYyyySlashToIso(t)?.let { return it }
    val iso = toIsoFromLabel(t).trim()
    return if (iso.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) iso else ""
}

/** Restores advanced-filter production value to ISO month `yyyy-MM`. */
private fun purchaseAdvInitialIsoMonth(v: String): String {
    val t = v.trim()
    if (t.isEmpty()) return ""
    if (t.matches(Regex("^\\d{4}-\\d{2}$"))) return t
    strictMmYyyySlashToIsoMonth(t)?.let { return it }
    return ""
}
private fun purchaseNumericFields(): Set<String> = setOf(
    "price", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "totalPrice",
    "shipmentCharges", "freight", "storageCharges", "miscCharges", "inspectionFee", "commission",
    "repairCharges", "rixoPrice", "bookingId", "cc", "door", "seat", "distance",
)
private fun purchaseBooleanFields(): Set<String> = setOf("bookingRequested", "shaken", "isPackageMode")
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

/**
 * Vehicle specs that the backend fills from the car_brand_mapping baseline on read.
 * In the Purchase List we want to show ONLY what's actually stored on the purchase, so these
 * columns read from `vehicleSpecExplicit` (stored overrides) and stay blank when inherited.
 */
private val purchaseListStoredOnlySpecColumns = setOf(
    "grade", "rank", "color", "fuel", "seat", "door", "cc", "shift", "wd", "driveType", "shipmentSize",
)

/** Explicitly-stored spec value for [key] from `purchase.vehicleSpecExplicit`; blank when inherited from mapping. */
private fun purchaseStoredSpecValue(purchase: dynamic, key: String): String {
    if (purchase == null || purchase == js("undefined")) return ""
    val explicit = purchase.vehicleSpecExplicit
    if (explicit == null || explicit == js("undefined")) return ""
    val v = js("explicit[key]")
    if (v == null || v == js("undefined")) return ""
    return v.toString().trim()
}

/**
 * Purchase List cell value: same as [purchaseTableCellValue] except mapping-derived specs show
 * only the value stored on the purchase (blank when inherited). Scoped to the Purchase List
 * table + card view; sorting, the Rixo table, and other consumers keep using the merged value.
 */
private fun purchaseListCellValue(purchase: dynamic, columnKey: String): String {
    if (columnKey in purchaseListStoredOnlySpecColumns) {
        return purchaseStoredSpecValue(purchase, columnKey)
    }
    return purchaseTableCellValue(purchase, columnKey)
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
        if (f.field == "carModelYear") {
            val rowYm = purchaseNormalizeYearMonth(purchaseRawFieldValue(p, "carModelYear")) ?: return false
            val filterYm = purchaseNormalizeYearMonth(v) ?: return false
            return rowYm == filterYm
        }
        val ts = purchaseComparableDateTs(p, f.field) ?: return false
        val fromTs = parseDateForSorting(v) ?: return false
        val toTs = parseDateForSorting(v) ?: return false
        return ts in fromTs..toTs
    }

    if (purchaseAdvMoneyFields().contains(f.field) ||
        purchaseAdvCommaIntFields().contains(f.field) ||
        purchaseAdvPlainIntFields().contains(f.field)
    ) {
        return purchaseAdvNumericFilterMatch(p, f.field, v)
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

private fun filterPurchasesByPurchaseDateRange(rows: Array<dynamic>, startIso: String, endIso: String): Array<dynamic> {
    val range = isoToLocalDayRangeTimestamps(startIso)?.let { startPair ->
        val endRange = isoToLocalDayRangeTimestamps(endIso) ?: return rows
        startPair.first to endRange.second
    } ?: return rows

    val (startTs, endTs) = range
    return rows.filter { purchase ->
        val raw = purchase.date?.toString() ?: ""
        val ts = parseDateForSorting(raw)
        ts != null && ts >= startTs && ts <= endTs
    }.toTypedArray()
}

private fun applyPurchaseDateFilterRange(startIso: String, endIso: String, resetPage: Boolean = true) {
    if (isoToLocalDayRangeTimestamps(startIso) == null || isoToLocalDayRangeTimestamps(endIso) == null) return

    purchaseDateFilterStartIso = startIso
    purchaseDateFilterEndIso = endIso
    purchaseDateFilterActive = true
    if (purchaseSearchServerMode) {
        loadPurchasesListPage(if (resetPage) 0 else purchaseSearchServerPageZeroBased.coerceAtLeast(0))
    } else {
        refreshPurchaseRowsFromBase(resetPage)
    }
}

private fun clearPurchaseDateFilter() {
    if (!purchaseDateFilterActive && purchaseDateFilterStartIso.isEmpty() && purchaseDateFilterEndIso.isEmpty()) {
        clearPurchaseDateQuickFilterInputs()
        return
    }
    purchaseDateFilterActive = false
    purchaseDateFilterStartIso = ""
    purchaseDateFilterEndIso = ""
    clearPurchaseDateQuickFilterInputs()
    if (purchaseSearchServerMode) {
        loadPurchasesListPage(0)
    } else {
        refreshPurchaseRowsFromBase(resetPage = true)
    }
}

/** Call when opening edit purchase so Cancel/back restores list filters. */
fun markPurchaseListPreserveFiltersForEditReturn() {
    preservePurchaseListFiltersOnNextShow = true
}

/** Call when navigating to any page other than purchase list or edit purchase. */
fun clearPurchaseListPreserveFiltersForEditReturn() {
    preservePurchaseListFiltersOnNextShow = false
    restoringPurchaseListFromEdit = false
}

private fun resetPurchaseListFilters() {
    purchaseSearchQuery = ""
    purchaseAdvancedFilters.clear()
    purchaseFilterSelectedColumns.clear()
    purchaseDateFilterActive = false
    purchaseDateFilterStartIso = ""
    purchaseDateFilterEndIso = ""
    purchaseSearchServerMode = false
    purchaseReturnPageFromEdit = null
    window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
}

private fun restorePurchaseListFilterUi() {
    (document.getElementById("purchaseSearchInput") as? HTMLInputElement)?.let { input ->
        input.value = purchaseSearchQuery
    }
    refreshPurchaseSearchScopeUi()
    updatePurchaseFilterBadge()
    syncPurchaseDateQuickFilterRangeUiFromState()
}

private const val PURCHASE_DATE_QUICK_FILTER_MENU_WIDTH_PX = 280.0

private fun purchaseDateQuickFilterMenuPortalHtml(): String = """
    <div id="purchaseDateQuickFilterMenu" class="purchase-date-quick-filter-menu" style="display: none;" role="dialog" aria-label="Filter by purchase date">
        <div class="purchase-date-quick-filter-menu__quick-row">
            <button type="button" id="purchaseDateQuickTodayBtn">Today</button>
            <button type="button" id="purchaseDateQuickLast7Btn">Last 7 days</button>
            <button type="button" id="purchaseDateQuickThisMonthBtn">This month</button>
            <button type="button" id="purchaseDateQuickClearBtn" class="is-muted">Clear</button>
            <button type="button" id="purchaseDateQuickFilterApplyBtn" class="is-primary">Apply</button>
        </div>
        <div class="purchase-date-quick-filter-menu__date-row">
            <label for="purchaseDateQuickFilterFromText">From</label>
            <div class="purchase-date-quick-filter-menu__date-input-wrap">
                <input type="text" id="purchaseDateQuickFilterFromText" maxlength="10" inputmode="numeric" autocomplete="off"
                       placeholder="MM/DD/YYYY">
                <button type="button" id="purchaseDateQuickFilterFromCalendarBtn" title="Open calendar" aria-label="Open From calendar">📅</button>
                <input type="date" id="purchaseDateQuickFilterFrom" tabindex="-1" aria-hidden="true"
                       class="purchase-date-quick-filter-menu__native-date">
            </div>
        </div>
        <div class="purchase-date-quick-filter-menu__date-row purchase-date-quick-filter-menu__date-row--to">
            <label for="purchaseDateQuickFilterToText">To</label>
            <div class="purchase-date-quick-filter-menu__date-input-wrap">
                <input type="text" id="purchaseDateQuickFilterToText" maxlength="10" inputmode="numeric" autocomplete="off"
                       placeholder="MM/DD/YYYY">
                <button type="button" id="purchaseDateQuickFilterToCalendarBtn" title="Open calendar" aria-label="Open To calendar">📅</button>
                <input type="date" id="purchaseDateQuickFilterTo" tabindex="-1" aria-hidden="true"
                       class="purchase-date-quick-filter-menu__native-date">
            </div>
        </div>
    </div>
""".trimIndent()

private fun purchaseDateQuickFilterHeaderCellHtml(label: String): String {
    val sortOrder = purchaseTableSortOrderByField["date"] ?: "desc"
    val sortTooltip = if (sortOrder == "asc") {
        "Sorted oldest to newest (click for newest first)"
    } else {
        "Sorted newest to oldest (click for oldest first)"
    }
    return """
    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
        <div style="display:flex; align-items:center; gap:8px;">
            <button type="button" id="purchaseSortBtn_date" title="$sortTooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                <span>$label</span><span style="font-size: 14px;">↕</span>
            </button>
            <button type="button" id="purchaseDateQuickFilterBtn" title="Filter by purchase date" aria-haspopup="dialog" aria-expanded="false"
                    style="background:none; border:none; cursor:pointer; font-weight:600; color:#111827; padding:0; display:inline-flex; align-items:center; justify-content:center;">
                📅
            </button>
        </div>
    </th>
""".trimIndent()
}

private fun closePurchaseDateQuickFilterMenu() {
    window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
    val menu = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement ?: return
    menu.style.setProperty("display", "none", "important")
    (document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
}

private fun positionPurchaseDateQuickFilterMenu(anchor: HTMLElement) {
    val menu = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement ?: return
    val rect = anchor.getBoundingClientRect()
    val viewportW = (window.asDynamic().innerWidth as? Number)?.toDouble() ?: 0.0
    val viewportH = (window.asDynamic().innerHeight as? Number)?.toDouble() ?: 0.0
    val margin = 8.0

    menu.style.position = "fixed"
    menu.style.setProperty("display", "flex", "important")
    menu.style.visibility = "hidden"
    val menuHeight = (menu.asDynamic().offsetHeight as? Number)?.toDouble() ?: 0.0
    menu.style.visibility = "visible"

    var top = rect.bottom + 4.0
    var left = rect.left
    if (top + menuHeight + margin > viewportH) {
        top = (rect.top - menuHeight - 4.0).coerceAtLeast(margin)
    }
    if (left + PURCHASE_DATE_QUICK_FILTER_MENU_WIDTH_PX + margin > viewportW) {
        left = (viewportW - PURCHASE_DATE_QUICK_FILTER_MENU_WIDTH_PX - margin).coerceAtLeast(margin)
    }

    menu.style.top = "${top}px"
    menu.style.left = "${left}px"
    menu.style.zIndex = "25000"
}

private fun openPurchaseDateQuickFilterMenu(anchor: HTMLElement) {
    syncPurchaseDateQuickFilterRangeUiFromState()
    positionPurchaseDateQuickFilterMenu(anchor)
    window.asDynamic().__purchaseDateQuickFilterMenuOpen = true
    anchor.setAttribute("aria-expanded", "true")
}

private fun togglePurchaseDateQuickFilterMenu(anchor: HTMLElement) {
    val isOpen = window.asDynamic().__purchaseDateQuickFilterMenuOpen == true
    if (isOpen) {
        closePurchaseDateQuickFilterMenu()
    } else {
        openPurchaseDateQuickFilterMenu(anchor)
    }
}

/** Read one From/To field (text mask preferred, then hidden native date). */
private fun purchaseDateQuickFilterFieldIso(baseId: String): String {
    val text = document.getElementById("${baseId}Text") as? HTMLInputElement
    val hidden = document.getElementById(baseId) as? HTMLInputElement
    strictMmDdYyyySlashToIso(text?.value?.trim() ?: "")?.let { return it }
    val h = hidden?.value?.trim() ?: ""
    return if (h.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) h else ""
}

private fun syncPurchaseDateQuickFilterFieldFromIso(baseId: String, iso: String) {
    val hidden = document.getElementById(baseId) as? HTMLInputElement ?: return
    val text = document.getElementById("${baseId}Text") as? HTMLInputElement
    if (iso.isBlank()) {
        hidden.value = ""
        text?.value = ""
        return
    }
    hidden.value = iso
    text?.value = isoToMmDdYyyy(iso)
}

private fun clearPurchaseDateQuickFilterInputs() {
    syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterFrom", "")
    syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterTo", "")
}

private fun syncPurchaseDateQuickFilterRangeUiFromState() {
    syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterFrom", purchaseDateFilterStartIso)
    syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterTo", purchaseDateFilterEndIso)
}

/**
 * Resolve From/To for Apply: one filled → single day; both filled → range (swap if From > To).
 */
private fun purchaseDateQuickFilterResolvedRange(): Pair<String, String>? {
    val from = purchaseDateQuickFilterFieldIso("purchaseDateQuickFilterFrom")
    val to = purchaseDateQuickFilterFieldIso("purchaseDateQuickFilterTo")
    return when {
        from.isEmpty() && to.isEmpty() -> null
        from.isNotEmpty() && to.isEmpty() -> from to from
        from.isEmpty() && to.isNotEmpty() -> to to to
        from > to -> to to from
        else -> from to to
    }
}

private fun setupPurchaseDateQuickFilterMenuPortal() {
    if (window.asDynamic().__purchaseDateQuickFilterMenuOpen == null) {
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
    }

    // Always rebuild portal so From/To markup stays in sync (old single-date menu may linger on body).
    document.getElementById("purchaseList")?.querySelector("#purchaseDateQuickFilterMenu")?.remove()
    document.getElementById("purchaseDateQuickFilterMenu")?.remove()

    val wrapper = document.createElement("div")
    wrapper.innerHTML = purchaseDateQuickFilterMenuPortalHtml()
    val menu = wrapper.firstElementChild as? HTMLElement ?: return
    document.body?.appendChild(menu)

    listOf("purchaseDateQuickFilterFrom", "purchaseDateQuickFilterTo").forEach { baseId ->
        (document.getElementById(baseId) as? HTMLInputElement)?.asDynamic()?.__strictDateBound = false
        bindStrictDateTextMask(baseId)
    }
    syncPurchaseDateQuickFilterRangeUiFromState()

    val closeAfterAction = { closePurchaseDateQuickFilterMenu() }

    menu.addEventListener("click", { ev: Event ->
        val target = ev.target as? HTMLElement ?: return@addEventListener
        ev.stopPropagation()
        when {
            target.id == "purchaseDateQuickFilterApplyBtn" || target.closest("#purchaseDateQuickFilterApplyBtn") != null -> {
                val range = purchaseDateQuickFilterResolvedRange()
                if (range != null) {
                    syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterFrom", range.first)
                    syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterTo", range.second)
                    applyPurchaseDateFilterRange(range.first, range.second)
                }
                closeAfterAction()
            }
            target.id == "purchaseDateQuickTodayBtn" || target.closest("#purchaseDateQuickTodayBtn") != null -> {
                val today = isoLocalToday()
                syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterFrom", today)
                syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterTo", today)
                applyPurchaseDateFilterRange(today, today)
                closeAfterAction()
            }
            target.id == "purchaseDateQuickLast7Btn" || target.closest("#purchaseDateQuickLast7Btn") != null -> {
                val end = isoLocalToday()
                val start = isoLocalOffsetDays(-6)
                syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterFrom", start)
                syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterTo", end)
                applyPurchaseDateFilterRange(start, end)
                closeAfterAction()
            }
            target.id == "purchaseDateQuickThisMonthBtn" || target.closest("#purchaseDateQuickThisMonthBtn") != null -> {
                val start = isoLocalThisMonthStart()
                val end = isoLocalThisMonthEnd()
                syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterFrom", start)
                syncPurchaseDateQuickFilterFieldFromIso("purchaseDateQuickFilterTo", end)
                applyPurchaseDateFilterRange(start, end)
                closeAfterAction()
            }
            target.id == "purchaseDateQuickClearBtn" || target.closest("#purchaseDateQuickClearBtn") != null -> {
                clearPurchaseDateFilter()
                closeAfterAction()
            }
        }
    })

    if (window.asDynamic().__purchaseDateQuickFilterDocumentWired != true) {
        window.asDynamic().__purchaseDateQuickFilterDocumentWired = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val btnEl = (target as? HTMLElement)?.closest("#purchaseDateQuickFilterBtn") as? HTMLElement
            if (btnEl != null) {
                event.stopPropagation()
                togglePurchaseDateQuickFilterMenu(btnEl)
                return@addEventListener
            }
            val menuEl = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement ?: return@addEventListener
            if (!menuEl.contains(target)) {
                closePurchaseDateQuickFilterMenu()
            }
        })

        val repositionOpenMenu = {
            if (window.asDynamic().__purchaseDateQuickFilterMenuOpen == true) {
                val btn = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
                if (btn != null) positionPurchaseDateQuickFilterMenu(btn)
            }
        }
        window.addEventListener("resize", { _: Event -> repositionOpenMenu() })
        window.addEventListener("scroll", { _: Event -> repositionOpenMenu() }, true)
    }
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
    if (purchaseDateFilterActive && purchaseDateFilterStartIso.isNotEmpty() && purchaseDateFilterEndIso.isNotEmpty()) {
        rows = filterPurchasesByPurchaseDateRange(rows, purchaseDateFilterStartIso, purchaseDateFilterEndIso)
    }
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
    val current = purchaseTableSortOrderByField[field] ?: "desc"
    val next = if (current == "asc") "desc" else "asc"
    purchaseTableSortOrderByField[field] = next
    purchaseTableSortField = field
    persistPurchaseListViewsToServer()
    if (purchaseSearchServerMode) {
        loadPurchasesListPage(purchaseSearchServerPageZeroBased.coerceAtLeast(0))
    } else {
        refreshPurchaseRowsFromBase(resetPage = true)
    }
}

fun navigateToPurchaseList(forceClearFilters: Boolean = false, forceRefresh: Boolean = true) {
    if (forceClearFilters) resetPurchaseListFilters()
    navigateToApp("/purchase", forceRefresh = forceRefresh)
}

/** Bumps generation so in-flight purchase list fetches skip DOM updates after navigation away. */
private var purchaseListLoadGeneration = 0

fun invalidatePurchaseListLoads() {
    purchaseListLoadGeneration++
}

private fun purchaseListViewButtonHtml(purchaseId: Long, chassisAttr: String, extraClass: String = ""): String {
    val cls = listOf("purchase-view-btn", extraClass).filter { it.isNotEmpty() }.joinToString(" ")
    return """
        <button type="button" class="$cls" data-id="$purchaseId" data-chassis="$chassisAttr"
                aria-label="View summary" title="Vehicle Summary"
                style="display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;padding:0;background:#fff;border:1px solid #d1d5db;border-radius:6px;cursor:pointer;color:#374151;box-shadow:0 1px 2px rgba(0,0,0,0.06);">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                <circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/>
            </svg>
        </button>
    """.trimIndent()
}

fun setupPurchaseEditDelegationOnTable() {
    val table = document.getElementById("purchaseTable") ?: return
    if (table.hasAttribute("data-purchase-edit-delegation")) return
    table.setAttribute("data-purchase-edit-delegation", "true")
    table.addEventListener("click", { event ->
        // Use Element (not HTMLElement): clicks on SVG/path inside buttons are not HTMLElements.
        val target = event.target as? Element ?: return@addEventListener
        val viewBtn = target.closest(".purchase-view-btn, .card-view-btn") as? HTMLElement
        if (viewBtn != null) {
            event.preventDefault()
            event.stopPropagation()
            handlePurchaseViewButtonClick(viewBtn)
            return@addEventListener
        }
        val btn = target.closest(".edit-btn, .card-edit-btn") as? HTMLElement ?: return@addEventListener
        event.preventDefault()
        event.stopPropagation()
        handlePurchaseEditButtonClick(btn)
    })
}

private fun findPurchaseInListById(id: Long): dynamic? {
    if (id <= 0L) return null
    for (p in allPurchases) {
        val pid = (p.id as? Number)?.toLong() ?: continue
        if (pid == id) return p
    }
    for (p in purchaseBaseRows) {
        val pid = (p.id as? Number)?.toLong() ?: continue
        if (pid == id) return p
    }
    return null
}

private fun purchaseSummaryField(p: dynamic, key: String): String {
    val raw = when (key) {
        "date" -> {
            val dateStr = p.date?.toString()?.trim().orEmpty()
            if (dateStr.isEmpty()) "" else {
                val iso = toIsoFromLabel(dateStr)
                formatWithWeekday(if (iso.isNotEmpty()) iso else dateStr)
            }
        }
        "price" -> {
            val priceStr = (p.price as? String) ?: p.price?.toString().orEmpty()
            val priceValue = parseCurrency(priceStr)
            if (priceValue > 0.0) formatCurrency(priceValue) else priceStr.trim()
        }
        "distance" -> {
            val d = p.distance?.toString()?.trim().orEmpty()
            if (d.isEmpty()) "" else if (d.contains("km", ignoreCase = true)) d else "$d km"
        }
        "chassis" -> p.chassis?.toString()?.trim().orEmpty()
        "grade" -> p.grade?.toString()?.trim().orEmpty()
        "gradeExplicit" -> p.gradeExplicit?.toString()?.trim().orEmpty()
        "carName" -> p.carName?.toString()?.trim().orEmpty()
        "color" -> p.color?.toString()?.trim().orEmpty()
        "colorExplicit" -> p.colorExplicit?.toString()?.trim().orEmpty()
        "fuel" -> p.fuel?.toString()?.trim().orEmpty()
        "fuelExplicit" -> p.fuelExplicit?.toString()?.trim().orEmpty()
        "auctionHouse" -> p.auctionHouse?.toString()?.trim().orEmpty()
        "stockLocation" -> p.stockLocation?.toString()?.trim().orEmpty()
        "rixoCompany" -> p.rixoCompany?.toString()?.trim().orEmpty()
        "clientName" -> p.clientName?.toString()?.trim().orEmpty()
        "country" -> p.country?.toString()?.trim().orEmpty()
        else -> ""
    }
    return raw
}

/** Calendar days from purchase date to today; blank if unparseable; future dates clamp to 0. */
private fun purchaseSummaryDaysPassed(p: dynamic): String {
    val dateStr = p.date?.toString()?.trim().orEmpty()
    if (dateStr.isEmpty()) return ""
    val iso = toIsoFromLabel(dateStr)
    if (!iso.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return ""
    val parts = iso.split("-")
    val y = parts.getOrNull(0)?.toIntOrNull() ?: return ""
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return ""
    val d = parts.getOrNull(2)?.toIntOrNull() ?: return ""
    val bought = Date(y, m - 1, d)
    val now = Date()
    val today = Date(now.getFullYear(), now.getMonth(), now.getDate())
    val diffMs = today.getTime() - bought.getTime()
    val days = kotlin.math.floor(diffMs / 86_400_000.0).toInt().coerceAtLeast(0)
    return if (days == 1) "1 day" else "$days days"
}

/** Display-only negotiate check for Vehicle Summary (mirrors Add/Edit fallback). */
private fun purchaseSummaryNegotiateChecked(p: dynamic): Boolean {
    val negotiate = p.negotiate
    if (negotiate == true || negotiate == "true" || negotiate == 1) return true
    val notes = p.notes?.toString() ?: ""
    return notes.contains("NEGOTIATE", ignoreCase = true)
}

private fun handlePurchaseViewButtonClick(btn: HTMLElement) {
    val id = btn.getAttribute("data-id")?.trim()?.toLongOrNull() ?: 0L
    if (id <= 0L) {
        showMessage("Cannot open summary: missing purchase id.", "error")
        return
    }
    val cached = findPurchaseInListById(id)
    if (cached != null) {
        showVehicleSummaryModal(cached)
        return
    }
    MainScope().launch {
        ApiClient.get<dynamic>("purchases/purchase/$id").fold(
            onSuccess = { purchase -> showVehicleSummaryModal(purchase) },
            onError = { message, _ ->
                showMessage("Failed to load vehicle summary: $message", "error")
            },
        )
    }
}

/** Read-only Vehicle Summary modal (Purchase List eye icon). */
fun showVehicleSummaryModal(purchase: dynamic) {
    document.getElementById("vehicleSummaryModal")?.remove()
    closeCarPictureLightbox()

    fun cell(label: String, value: String): String {
        val v = if (value.isBlank()) "—" else escapeHtml(value)
        return """
            <div style="display:flex;flex-direction:column;gap:4px;min-width:0;">
                <label style="font-size:11px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:.04em;">${escapeHtml(label)}</label>
                <div style="padding:10px 12px;border:1px solid #e5e7eb;border-radius:6px;background:#f9fafb;color:#111827;font-size:14px;line-height:1.4;word-break:break-word;">$v</div>
            </div>
        """.trimIndent()
    }

    fun carPriceWithNegotiateCell(): String {
        val priceRaw = purchaseSummaryField(purchase, "price")
        val priceHtml = if (priceRaw.isBlank()) "—" else escapeHtml(priceRaw)
        val checkedAttr = if (purchaseSummaryNegotiateChecked(purchase)) " checked" else ""
        return """
            <div style="display:flex;flex-direction:column;gap:4px;min-width:0;">
                <label style="font-size:11px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:.04em;">Car Price</label>
                <div style="padding:10px 12px;border:1px solid #e5e7eb;border-radius:6px;background:#f9fafb;color:#111827;font-size:14px;line-height:1.4;display:flex;align-items:center;gap:12px;min-width:0;">
                    <span style="flex:1;min-width:0;word-break:break-word;">$priceHtml</span>
                    <label style="display:inline-flex;align-items:center;gap:6px;font-weight:600;color:#374151;white-space:nowrap;margin:0;pointer-events:none;user-select:none;cursor:default;">
                        <input type="checkbox" tabindex="-1" aria-disabled="true" aria-readonly="true" onclick="return false;"$checkedAttr
                               style="width:18px;height:18px;accent-color:#60a5fa;pointer-events:none;cursor:default;">
                        NEGOTIATE
                    </label>
                </div>
            </div>
        """.trimIndent()
    }

    val gridHtml = buildString {
        append(cell("Purchase Date", purchaseSummaryField(purchase, "date")))
        append(cell("Days Passed", purchaseSummaryDaysPassed(purchase)))
        append(cell("Chassis", purchaseSummaryField(purchase, "chassis")))
        append(cell("Grade", purchaseSummaryField(purchase, "gradeExplicit")))
        append(cell("Car Name", purchaseSummaryField(purchase, "carName")))
        append(cell("Color", purchaseSummaryField(purchase, "colorExplicit")))
        append(cell("Mileage", purchaseSummaryField(purchase, "distance")))
        append(cell("Fuel", purchaseSummaryField(purchase, "fuel")))
        append(cell("Supplier Name", purchaseSummaryField(purchase, "auctionHouse")))
        append(cell("Stock Location", purchaseSummaryField(purchase, "stockLocation")))
        append(cell("Rixo Company", purchaseSummaryField(purchase, "rixoCompany")))
        append(carPriceWithNegotiateCell())
        append(cell("Client Name", purchaseSummaryField(purchase, "clientName")))
        append(cell("Country", purchaseSummaryField(purchase, "country")))
    }

    val modal = document.createElement("div") as HTMLDivElement
    modal.id = "vehicleSummaryModal"
    modal.setAttribute(
        "style",
        "position:fixed;inset:0;background:rgba(15,23,42,0.45);z-index:10020;display:flex;align-items:center;justify-content:center;padding:16px;",
    )
    modal.innerHTML = """
        <div role="dialog" aria-modal="true" aria-labelledby="vehicleSummaryTitle"
             style="background:#fff;border-radius:10px;max-width:720px;width:100%;max-height:90vh;overflow:auto;box-shadow:0 20px 50px rgba(0,0,0,0.2);">
            <div style="display:flex;align-items:center;justify-content:space-between;padding:18px 22px;border-bottom:1px solid #e5e7eb;">
                <h2 id="vehicleSummaryTitle" style="margin:0;font-size:20px;font-weight:700;color:#111827;">Vehicle Summary</h2>
                <button type="button" id="closeVehicleSummaryModal" aria-label="Close"
                        style="background:none;border:none;font-size:26px;line-height:1;cursor:pointer;color:#6b7280;padding:4px 8px;">&times;</button>
            </div>
            <div style="padding:20px 22px 24px;display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:14px 16px;">
                $gridHtml
            </div>
            <div style="padding:0 22px 24px;">
                <h3 style="margin:0 0 12px 0;font-size:13px;font-weight:700;color:#374151;text-transform:uppercase;letter-spacing:.04em;border-top:1px solid #e5e7eb;padding-top:16px;">Car Pictures</h3>
                <div id="vehicleSummaryPictures"
                     style="display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:12px;"></div>
            </div>
        </div>
    """.trimIndent()

    document.body?.appendChild(modal)

    val closeModal = {
        closeCarPictureLightbox()
        document.removeEventListener("keydown", ::handleVehicleSummaryEscape)
        document.getElementById("vehicleSummaryModal")?.remove()
    }
    document.getElementById("closeVehicleSummaryModal")?.addEventListener("click", { _: Event -> closeModal() })
    modal.addEventListener("click", { event ->
        if (event.target == modal) closeModal()
    })
    document.addEventListener("keydown", ::handleVehicleSummaryEscape)

    loadVehicleSummaryCarPictures(purchase, "vehicleSummaryPictures")
}

private fun handleVehicleSummaryEscape(event: Event) {
    val keyEvent = event.asDynamic()
    if (keyEvent.key != "Escape") return
    // Lightbox sits above the summary; Escape should close it first.
    if (document.getElementById("carPictureLightbox") != null) {
        closeCarPictureLightbox()
        return
    }
    closeCarPictureLightbox()
    document.removeEventListener("keydown", ::handleVehicleSummaryEscape)
    document.getElementById("vehicleSummaryModal")?.remove()
}

/** One-shot purchase id used if chassis lookup fails after navigating to /edit/{chassis}. */
private const val EDIT_PURCHASE_FALLBACK_ID_KEY = "automanEditPurchaseFallbackId"

fun stashEditPurchaseFallbackId(id: Long) {
    try {
        window.sessionStorage.setItem(EDIT_PURCHASE_FALLBACK_ID_KEY, id.toString())
    } catch (_: dynamic) { }
}

fun consumeEditPurchaseFallbackId(): Long? {
    return try {
        val raw = window.sessionStorage.getItem(EDIT_PURCHASE_FALLBACK_ID_KEY)?.trim().orEmpty()
        window.sessionStorage.removeItem(EDIT_PURCHASE_FALLBACK_ID_KEY)
        raw.toLongOrNull()?.takeIf { it > 0L }
    } catch (_: dynamic) {
        null
    }
}

private fun handlePurchaseEditButtonClick(btn: HTMLElement) {
    val chassis = btn.getAttribute("data-chassis")?.trim().orEmpty()
    val idStr = btn.getAttribute("data-id")?.trim().orEmpty()
    val id = idStr.toLongOrNull()
    purchaseReturnPageFromEdit = currentPage
    when {
        chassis.isNotEmpty() -> {
            if (id != null && id > 0L) stashEditPurchaseFallbackId(id)
            invalidatePurchaseListLoads()
            navigateToEditPurchase(chassis)
        }
        id != null && id > 0L -> {
            invalidatePurchaseListLoads()
            navigateToApp("/edit/$id")
        }
        else -> showMessage("Invalid purchase. Cannot edit this purchase.", "error")
    }
}

private fun purchaseListIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= PURCHASE_LIST_COMPACT_MAX_WIDTH_PX
}

private fun purchaseListEmptyHtml(title: String, detail: String): String =
    """<div class="purchase-list-empty"><strong>${escapeHtml(title)}</strong><div>${escapeHtml(detail)}</div></div>"""

private fun setupPurchaseListCompactResizeListener() {
    if (document.getElementById("purchaseList") == null) return
    purchaseListLastCompactLayout = purchaseListIsCompactLayout()

    val existing = window.asDynamic().__purchaseListCompactResizeListener
    if (existing != null) {
        window.removeEventListener("resize", existing.unsafeCast<(Event) -> Unit>())
    }

    val resizeListener: (Event) -> Unit = { _: Event ->
        val prev = purchaseListResizeDebounceHandle
        if (prev != null) window.clearTimeout(prev)
        purchaseListResizeDebounceHandle = window.setTimeout({
            if (document.getElementById("purchaseList") == null) return@setTimeout
            val compact = purchaseListIsCompactLayout()
            if (purchaseListLastCompactLayout != compact) {
                purchaseListLastCompactLayout = compact
                displayPurchasesWithPagination()
            }
        }, 120)
    }
    window.asDynamic().__purchaseListCompactResizeListener = resizeListener
    window.addEventListener("resize", resizeListener)
}

fun showPurchaseList(forceClearFilters: Boolean = false) {
    val preserveFilters = !forceClearFilters && preservePurchaseListFiltersOnNextShow
    preservePurchaseListFiltersOnNextShow = false
    if (preserveFilters) {
        restoringPurchaseListFromEdit = true
    } else {
        resetPurchaseListFilters()
    }
    
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <style>
            #purchaseList{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;}
            .purchase-list-toolbar{
                display:grid;
                grid-template-columns:1fr;
                grid-template-areas:"title" "search" "actions";
                gap:12px;
                margin-bottom:16px;
                align-items:center;
            }
            .purchase-list-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;grid-area:title;text-align:center;}
            .purchase-list-search-row{grid-area:search;display:flex;align-items:center;gap:10px;width:100%;min-width:0;}
            .purchase-search{position:relative;flex:1;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
            .purchase-search input{width:100%;box-sizing:border-box;padding:11px 36px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
            .purchase-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:6px 8px;min-height:36px;min-width:36px;border-radius:8px;}
            .purchase-search-clear:hover{background:#f3f4f6;color:#111827;}
            .purchase-list-actions{grid-area:actions;display:flex;justify-content:flex-end;align-items:center;gap:10px;flex-wrap:wrap;}
            .purchase-quick-add-btn{padding:8px 16px;background-color:#0d9488;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:14px;font-weight:600;display:inline-flex;align-items:center;gap:6px;min-height:40px;white-space:nowrap;}
            .purchase-quick-add-btn:hover{background-color:#0f766e;}
            .purchase-export-btn{padding:8px 16px;background-color:#198754;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:14px;display:inline-flex;align-items:center;gap:6px;min-height:40px;white-space:nowrap;}
            .purchase-export-btn:disabled{opacity:0.7;cursor:not-allowed;}
            .purchase-column-filter-btn,.purchase-adv-filter-btn{
                width:48px;height:48px;min-width:48px;min-height:48px;border-radius:50%;border:1px solid #e5e7eb;background:#f3f4f6;
                cursor:pointer;display:inline-flex;align-items:center;justify-content:center;box-shadow:0 1px 2px rgba(0,0,0,0.06);
                color:#4b5563;padding:0;position:relative;flex-shrink:0;
            }
            .purchase-column-filter-btn:hover,.purchase-adv-filter-btn:hover{background:#e8eaed;box-shadow:0 2px 8px rgba(0,0,0,0.08);}
            .purchase-column-filter-btn:focus-visible,.purchase-adv-filter-btn:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;}
            .purchase-list-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
            .purchase-list-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
            .purchase-list-empty strong{color:#0f172a;}
            .purchase-list-pager{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px;margin-top:20px;padding:10px;}
            .purchase-list-pager-btns{display:flex;gap:10px;align-items:center;flex-wrap:wrap;}
            .purchase-list-pager-btn{padding:8px 16px;background-color:#007bff;color:#fff;border:none;border-radius:4px;cursor:pointer;min-height:40px;}
            .purchase-list-pager-btn:disabled,.purchase-list-pager-btn.is-disabled{background-color:#ccc;cursor:not-allowed;}
            #purchaseList .purchase-search-filter-opt:hover{background:#f3f4f6 !important;}
            #purchaseList .purchase-search-filter-opt--active{background:#eef2ff !important;font-weight:600;}
            #purchaseList .purchase-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;}
            @media (max-width:1024px){
                #purchaseList{padding:14px;border-radius:14px;}
                .purchase-list-toolbar{gap:14px;margin-bottom:14px;}
                .purchase-list-title{font-size:17px;}
                .purchase-search input{font-size:13px;padding:10px 34px 10px 38px;}
                .purchase-list-actions{justify-self:end;}
            }
            @media (min-width:1025px){
                .purchase-list-toolbar{
                    grid-template-columns:auto 1fr minmax(220px,32%) auto;
                    grid-template-areas:"title . search actions";
                    column-gap:12px;
                    row-gap:0;
                }
                .purchase-list-title{text-align:left;justify-self:start;}
            }
        </style>
        <div id="purchaseList">
            <div class="purchase-list-toolbar">
                <h2 class="purchase-list-title">Purchase List</h2>
                <div class="purchase-list-search-row">
                    <div class="purchase-search">
                        <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);pointer-events:none;color:#9ca3af;display:flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="text" id="purchaseSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search by chassis, brand, supplier…" aria-label="Search purchases" />
                        <button type="button" id="purchaseSearchClearBtn" class="purchase-search-clear" title="Clear search" aria-label="Clear search">×</button>
                    </div>
                    <span id="purchaseSearchFieldLabel" class="purchase-sr-only" aria-live="polite">All fields</span>
                    <button type="button" id="purchaseSearchFilterBtn" class="purchase-adv-filter-btn" title="Filter columns" aria-haspopup="true" aria-expanded="false" aria-label="Open filter columns panel.">
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
                <div class="purchase-list-actions">
                    <button type="button" id="columnFilterBtn" class="purchase-column-filter-btn" title="Column filter" aria-label="Column filter">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                            <path d="M4 6h16M7 12h10M10 18h4" stroke="#6b7280" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                    </button>
                    <button type="button" id="purchaseListQuickAddBtn" class="purchase-quick-add-btn" title="Add Quick Purchase" aria-label="Add Quick Purchase">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                            <path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.25" stroke-linecap="round"/>
                        </svg>
                        ADD
                    </button>
                    <button type="button" id="exportPurchasesExcelBtn" class="purchase-export-btn" title="Export purchases to Excel">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z" stroke="currentColor" stroke-width="1.75" stroke-linejoin="round"/>
                            <path d="M14 2v6h6M8 13h8M8 17h8M8 9h2" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                        </svg>
                        Export Excel
                    </button>
                </div>
            </div>
            <div id="purchaseAdvancedFilterDropdown" style="display:none;margin-bottom:14px;border:none;border-radius:12px;background:#fff;box-shadow:0 10px 30px rgba(0,0,0,0.08);"></div>
            <div id="purchaseTable">
                ${purchaseListEmptyHtml("Loading", "Loading purchases…")}
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
    setupPurchaseListCompactResizeListener()
    
    // Hamburger button listener is set up by ensureSidebarPresent()
    
    // Add column filter button event listener
    document.getElementById("columnFilterBtn")?.addEventListener("click", { _: Event ->
        showColumnFilterModal()
    })

    document.getElementById("exportPurchasesExcelBtn")?.addEventListener("click", { _: Event ->
        exportPurchasesToExcel()
    })

    document.getElementById("purchaseListQuickAddBtn")?.addEventListener("click", { _: Event ->
        openQuickPurchaseModal()
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
    setupPurchaseDateQuickFilterMenuPortal()
    setupPurchaseEditDelegationOnTable()
    MainScope().launch {
        loadPurchaseListViewsFromServer()
        if (!routeEquals("/purchase")) return@launch
        loadPurchases()
    }
}

fun showShippingHistoryPage() {
    navigateToApp("/shipping-history")
    val content = document.getElementById("content") ?: return
    shippingHistoryCachedRows = emptyArray()
    shippingHistorySortField = "bookingId"
    shippingHistorySortOrder = "desc"
    shippingHistoryServerMode = true
    shippingHistoryPageZeroBased = 0
    shippingHistoryTotalPages = 1
    shippingHistoryTotalElements = 0L
    shippingHistoryItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
    shippingHistoryActiveSearchQ = ""

    content.innerHTML = """
        <style>
            #shippingHistoryPage{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;}
            .shipping-history-toolbar{
                display:grid;
                grid-template-columns:1fr;
                grid-template-areas:"title" "search" "actions";
                gap:12px;
                margin-bottom:16px;
                align-items:center;
            }
            .shipping-history-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;grid-area:title;text-align:center;}
            .shipping-search{grid-area:search;width:100%;position:relative;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
            .shipping-search input{width:100%;box-sizing:border-box;padding:11px 36px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
            .shipping-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:6px 8px;min-height:36px;min-width:36px;border-radius:8px;}
            .shipping-search-clear:hover{background:#f3f4f6;color:#111827;}
            .shipping-search-clear.is-hidden{display:none;}
            .shipping-history-actions{grid-area:actions;display:flex;justify-content:flex-end;align-items:center;gap:10px;}
            .shipping-column-filter-btn{
                width:48px;height:48px;min-width:48px;min-height:48px;border-radius:50%;border:1px solid #e5e7eb;background:#f3f4f6;
                cursor:pointer;display:inline-flex;align-items:center;justify-content:center;box-shadow:0 1px 2px rgba(0,0,0,0.06);padding:0;color:#4b5563;
            }
            .shipping-column-filter-btn:hover{background:#e8eaed;box-shadow:0 2px 8px rgba(0,0,0,0.08);}
            .shipping-column-filter-btn:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;}
            .shipping-export-btn{padding:8px 16px;background-color:#198754;color:#fff;border:none;border-radius:8px;cursor:pointer;font-size:14px;display:inline-flex;align-items:center;gap:6px;min-height:40px;white-space:nowrap;font-weight:600;}
            .shipping-export-btn:hover{background-color:#157347;}
            .shipping-export-btn:focus-visible{outline:2px solid #157347;outline-offset:2px;}
            .shipping-export-btn:disabled{opacity:0.7;cursor:not-allowed;}
            .shipping-history-edit-btn{
                display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;min-width:36px;min-height:36px;
                background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 4px rgba(76,201,255,0.30);padding:0;
            }
            .shipping-history-edit-btn:hover{filter:brightness(1.05);}
            .shipping-history-edit-btn:focus-visible{outline:2px solid #0284c7;outline-offset:2px;}
            .shipping-history-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
            table.purchase-list-table thead th{position:sticky;top:0;z-index:10;}
            .shipping-history-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
            .shipping-history-empty strong{color:#0f172a;}
            .shipping-history-empty--error{color:#b91c1c;}
            .shipping-history-empty--error strong{color:#991b1b;}
            .shipping-history-empty-cta{margin-top:8px;padding:8px 14px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;color:#0f172a;font-size:13px;font-weight:600;cursor:pointer;min-height:40px;}
            .shipping-history-empty-cta:hover{background:#f8fafc;}
            .shipping-history-empty-cta:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;}
            .shipping-history-pager{display:flex;justify-content:space-between;align-items:center;padding:16px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;margin-top:12px;flex-wrap:wrap;gap:12px;}
            .shipping-history-pager-meta{color:#6b7280;font-size:14px;}
            .shipping-history-pager-btns{display:flex;align-items:center;gap:8px;flex-wrap:wrap;}
            .shipping-history-pager-btn{padding:8px 16px;background-color:#007bff;color:#fff;border:none;border-radius:8px;cursor:pointer;min-height:40px;font-size:14px;font-weight:600;}
            .shipping-history-pager-btn:hover:not(:disabled):not(.is-disabled){background-color:#0069d9;}
            .shipping-history-pager-btn:focus-visible{outline:2px solid #0069d9;outline-offset:2px;}
            .shipping-history-pager-btn:disabled,.shipping-history-pager-btn.is-disabled{background-color:#ccc;cursor:not-allowed;}
            .shipping-history-sort-btn{background:none;border:none;cursor:pointer;font-weight:700;color:#0f172a;padding:0;display:inline-flex;align-items:center;gap:6px;min-height:36px;}
            .shipping-history-sort-btn:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;border-radius:4px;}
            .shipping-history-sort-btn.is-active .shipping-history-sort-icon{color:#0f766e;font-weight:800;}
            .shipping-history-sort-icon{font-size:14px;color:#64748b;}
            .shipping-cards{display:flex;flex-direction:column;gap:10px;}
            .shipping-card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 2px rgba(0,0,0,0.04);padding:12px;}
            .shipping-card-top{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;flex-wrap:wrap;}
            .shipping-card-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;}
            .shipping-card-status{display:inline-flex;align-items:center;gap:8px;margin-left:auto;}
            .shipping-card-grid{display:grid;gap:8px;}
            .shipping-kv{display:flex;gap:10px;align-items:flex-start;}
            .shipping-k{min-width:120px;font-size:12px;color:#64748b;line-height:1.4;}
            .shipping-v{flex:1;min-width:0;}
            button.shipping-history-invoice-btn:focus-visible{outline:2px solid #15803d;outline-offset:2px;}
            button.invoice-history-pdf-btn:focus-visible{outline:2px solid #b91c1c;outline-offset:2px;border-radius:50%;}
            @media (max-width: 1024px){
                #shippingHistoryPage{padding:14px;border-radius:14px;}
                .shipping-history-toolbar{gap:14px;margin-bottom:14px;}
                .shipping-history-title{font-size:17px;}
                .shipping-search input{font-size:13px;padding:10px 34px 10px 38px;}
                .shipping-history-actions{justify-self:end;}
            }
            @media (min-width: 1025px){
                .shipping-history-toolbar{
                    grid-template-columns:auto 1fr minmax(200px,25%) auto;
                    grid-template-areas:"title . search actions";
                    column-gap:12px;
                    row-gap:0;
                }
                .shipping-history-title{text-align:left;justify-self:start;}
            }
        </style>
        <div id="shippingHistoryPage">
            <div class="shipping-history-toolbar">
                <h2 class="shipping-history-title">Shipping History</h2>
                <div class="shipping-search">
                    <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);pointer-events:none;color:#9ca3af;display:flex;" aria-hidden="true">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    </span>
                    <input type="text" id="shippingHistorySearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search country, consignee, chassis, client…" aria-label="Search shipping history" />
                    <button type="button" id="shippingHistorySearchClearBtn" class="shipping-search-clear is-hidden" title="Clear search" aria-label="Clear search">×</button>
                </div>
                <div class="shipping-history-actions">
                    <button type="button" id="shippingHistoryColumnFilterBtn" class="shipping-column-filter-btn" title="Select columns to display" aria-label="Select columns to display">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                            <path d="M4 6h16M7 12h10M10 18h4" stroke="#6b7280" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                    </button>
                    <button type="button" id="exportShippingHistoryExcelBtn" class="shipping-export-btn" title="Export all shipping history to Excel" aria-label="Export all shipping history to Excel">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6z" stroke="currentColor" stroke-width="1.75" stroke-linejoin="round"/>
                            <path d="M14 2v6h6M8 13h8M8 17h8M8 9h2" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                        </svg>
                        Export Excel
                    </button>
                </div>
            </div>
            <div id="shippingHistoryTableWrap">
                <div id="shippingHistoryTable" style="margin-top: 8px;">
                    <div class="shipping-history-empty"><strong>Loading</strong><div>Loading shipping history…</div></div>
                </div>
            </div>
        </div>
    """

    applyRoleBasedRestrictions()
    ensureSidebarPresent()

    val searchInput = document.getElementById("shippingHistorySearchInput") as? HTMLInputElement
    fun syncShippingHistorySearchClearVisibility() {
        val clearBtn = document.getElementById("shippingHistorySearchClearBtn") as? HTMLElement ?: return
        val hasQuery = (searchInput?.value?.trim().orEmpty()).isNotEmpty()
        if (hasQuery) clearBtn.classList.remove("is-hidden") else clearBtn.classList.add("is-hidden")
    }
    searchInput?.addEventListener("input", { _: Event ->
        syncShippingHistorySearchClearVisibility()
        scheduleShippingHistorySearchDebounced()
    })
    document.getElementById("shippingHistorySearchClearBtn")?.addEventListener("click", { _: Event ->
        searchInput?.value = ""
        syncShippingHistorySearchClearVisibility()
        shippingHistoryPageZeroBased = 0
        loadShippingHistory(0)
    })
    syncShippingHistorySearchClearVisibility()
    document.getElementById("exportShippingHistoryExcelBtn")?.addEventListener("click", { _: Event ->
        exportShippingHistoryToExcel()
    })
    document.getElementById("shippingHistoryColumnFilterBtn")?.addEventListener("click", { _: Event ->
        showShippingHistoryColumnFilterModal()
    })

    setupShippingHistoryResizeListener()

    val wrap = document.getElementById("shippingHistoryTableWrap")
    if (wrap != null && !wrap.hasAttribute("data-shipping-empty-cta-delegation")) {
        wrap.setAttribute("data-shipping-empty-cta-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("#shippingHistoryClearSearchCta") ?: return@addEventListener
            e.preventDefault()
            val input = document.getElementById("shippingHistorySearchInput") as? HTMLInputElement
            input?.value = ""
            val clearBtn = document.getElementById("shippingHistorySearchClearBtn") as? HTMLElement
            clearBtn?.classList?.add("is-hidden")
            shippingHistoryPageZeroBased = 0
            loadShippingHistory(0)
            input?.focus()
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-shipping-sort-delegation")) {
        wrap.setAttribute("data-shipping-sort-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-shipping-sort]") ?: return@addEventListener
            e.preventDefault()
            val field = btn.getAttribute("data-shipping-sort") ?: return@addEventListener
            toggleShippingHistorySort(field)
        })
    }

    if (wrap != null && !wrap.hasAttribute("data-shipping-history-edit-delegation")) {
        wrap.setAttribute("data-shipping-history-edit-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-shipping-history-edit]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val idsCsv = btn.getAttribute("data-shipping-group-ids")?.trim() ?: return@addEventListener
            val idSet = idsCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
            if (idSet.isEmpty()) return@addEventListener
            val gRows = shippingHistoryCachedRows.filter { r ->
                val id = shippingHistoryCell(r, "id").trim()
                id.isNotEmpty() && id in idSet
            }.toList()
            if (gRows.isEmpty()) return@addEventListener
            storeAndNavigateShippingHistoryEdit(gRows)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-shipping-history-invoice-delegation")) {
        wrap.setAttribute("data-shipping-history-invoice-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-shipping-history-invoice]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val idsCsv = btn.getAttribute("data-shipping-group-ids")?.trim() ?: return@addEventListener
            val idSet = idsCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
            if (idSet.isEmpty()) return@addEventListener
            val gRows = shippingHistoryCachedRows.filter { r ->
                val id = shippingHistoryCell(r, "id").trim()
                id.isNotEmpty() && id in idSet
            }.toList()
            if (gRows.isEmpty()) return@addEventListener
            if (shippingHistoryGroupIsFullyInvoiced(gRows)) {
                showShippingHistoryInvoiceAlreadyCreatedModal()
                return@addEventListener
            }
            autoCreateInvoicesFromShippingHistory(gRows)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-shipping-history-pdf-delegation")) {
        wrap.setAttribute("data-shipping-history-pdf-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-shipping-history-pdf]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val idsCsv = btn.getAttribute("data-shipping-group-ids")?.trim() ?: return@addEventListener
            val idSet = idsCsv.split(",").mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.toSet()
            if (idSet.isEmpty()) return@addEventListener
            val gRows = shippingHistoryCachedRows.filter { r ->
                val id = shippingHistoryCell(r, "id").trim()
                id.isNotEmpty() && id in idSet
            }.toList()
            if (gRows.isEmpty()) return@addEventListener
            downloadShippingHistoryPdf(gRows, btn as? HTMLButtonElement)
        })
    }

    loadShippingHistory()
}

private fun shippingHistoryEditButtonHtml(sortedIdsCsv: String): String {
    if (sortedIdsCsv.isEmpty()) return ""
    val safe = escapeHtml(sortedIdsCsv)
    return """<button type="button" class="shipping-history-edit-btn" data-shipping-history-edit data-shipping-group-ids="$safe" aria-label="Edit shipping schedule" title="Edit to Create Shipping Schedule">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
        </svg>
    </button>"""
}

private fun shippingHistoryPdfButtonHtml(sortedIdsCsv: String): String {
    if (sortedIdsCsv.isEmpty()) return ""
    val safe = escapeHtml(sortedIdsCsv)
    return """<button type="button" class="invoice-history-pdf-btn" data-shipping-history-pdf data-shipping-group-ids="$safe"
        aria-label="Download shipping schedule PDF" title="Download PDF">
        <img src="invoice-history-pdf-btn.jpeg" alt="" width="36" height="36" draggable="false" />
    </button>"""
}

private fun shippingHistoryRowInvoiceCreated(row: dynamic): Boolean {
    val flag: dynamic = row.invoiceCreated
    if (flag == null || flag == js("undefined")) return false
    if (flag is Boolean) return flag
    val s = flag.toString().trim().lowercase()
    return s == "true" || s == "1"
}

private fun shippingHistoryGroupIsFullyInvoiced(gRows: List<dynamic>): Boolean {
    if (gRows.isEmpty()) return false
    return gRows.all { shippingHistoryRowInvoiceCreated(it) }
}

private fun showShippingHistoryInvoiceAlreadyCreatedModal() {
    val modalId = "shippingInvoiceAlreadyCreatedModal"
    document.getElementById(modalId)?.remove()
    shippingHistoryInvoiceAlreadyCreatedKeyHandler?.let { document.removeEventListener("keydown", it) }
    shippingHistoryInvoiceAlreadyCreatedKeyHandler = null

    val returnFocus = document.activeElement as? HTMLElement
    val overlay = document.createElement("div") as org.w3c.dom.HTMLDivElement
    overlay.id = modalId
    overlay.style.cssText =
        "position:fixed;inset:0;background:rgba(15,23,42,0.45);z-index:10020;display:flex;align-items:center;justify-content:center;padding:16px;"
    overlay.innerHTML = """
        <div role="dialog" aria-labelledby="shippingInvoiceAlreadyCreatedTitle" aria-modal="true"
            style="background:#fff;border-radius:14px;padding:32px 40px;text-align:center;box-shadow:0 20px 50px rgba(15,23,42,0.28);max-width:400px;width:calc(100% - 32px);">
            <div id="shippingInvoiceAlreadyCreatedTitle" style="font-size:22px;font-weight:700;color:#111827;margin-bottom:20px;">Invoice Already Created</div>
            <button type="button" id="shippingInvoiceAlreadyCreatedOkBtn"
                style="padding:10px 28px;border:none;border-radius:8px;background:#0f766e;color:#fff;font-weight:600;font-size:15px;cursor:pointer;min-height:40px;">OK</button>
        </div>
    """.trimIndent()

    fun closeModal() {
        shippingHistoryInvoiceAlreadyCreatedKeyHandler?.let { document.removeEventListener("keydown", it) }
        shippingHistoryInvoiceAlreadyCreatedKeyHandler = null
        overlay.remove()
        returnFocus?.focus()
    }

    overlay.addEventListener("click", { e: Event ->
        if (e.target === overlay) closeModal()
    })
    document.body?.appendChild(overlay)
    val card = overlay.firstElementChild
    card?.addEventListener("click", { e: Event -> e.stopPropagation() })
    document.getElementById("shippingInvoiceAlreadyCreatedOkBtn")?.addEventListener("click", { _: Event ->
        closeModal()
    })

    val escapeHandler: (Event) -> Unit = { event: Event ->
        val keyEvent = event.asDynamic()
        if (keyEvent.key == "Escape") {
            event.preventDefault()
            closeModal()
        }
    }
    shippingHistoryInvoiceAlreadyCreatedKeyHandler = escapeHandler
    document.addEventListener("keydown", escapeHandler)
    (document.getElementById("shippingInvoiceAlreadyCreatedOkBtn") as? HTMLElement)?.focus()
}

private fun shippingHistoryInvoiceButtonHtml(sortedIdsCsv: String): String {
    if (sortedIdsCsv.isEmpty()) return ""
    val safe = escapeHtml(sortedIdsCsv)
    return """<button type="button" class="shipping-history-invoice-btn" data-shipping-history-invoice data-shipping-group-ids="$safe"
        aria-label="Create Invoice" title="Create Invoice"><span class="shipping-history-invoice-btn__text">INVOICE</span></button>"""
}

private fun parseShippingHistoryYenAmount(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return try {
        t.replace("¥", "")
            .replace("$", "")
            .replace(",", "")
            .trim()
            .replace(Regex("[^0-9.-]"), "")
            .toDoubleOrNull()
    } catch (_: Throwable) {
        null
    }
}

private fun shippingHistoryFirstNonEmpty(rows: List<dynamic>, key: String): String {
    val sorted = rows.sortedBy { shippingHistoryCell(it, "id").toLongOrNull() ?: 0L }
    for (r in sorted) {
        val v = shippingHistoryCell(r, key).trim()
        if (v.isNotEmpty() && v != "—") return v
    }
    return ""
}

/** Builds [ShippingSchedulePdfRequest] JSON for POST generate-pdf from a shipping-history booking group. */
private fun buildShippingPdfRequestFromHistoryGroup(gRows: List<dynamic>): dynamic? {
    if (gRows.isEmpty()) return null
    val sorted = gRows.sortedBy { shippingHistoryCell(it, "id").toLongOrNull() ?: 0L }
    val bookingNo = shippingHistoryRepresentativeBookingDisplay(gRows)
    if (bookingNo == "—") {
        showMessage("Booking number is required to generate PDF.", "warning")
        return null
    }
    val chassisList = sorted.mapNotNull { r ->
        shippingHistoryCell(r, "chassis").trim().takeIf { it.isNotEmpty() }
    }
    if (chassisList.isEmpty()) {
        showMessage("No chassis on this booking to generate PDF.", "warning")
        return null
    }
    val priceType = shippingHistoryFirstNonEmpty(gRows, "priceType")
    val isFob = priceType.contains("FOB", ignoreCase = true)
    val pdfRequest = js("{}")
    pdfRequest.bookingNo = bookingNo
    pdfRequest.vesselName = shippingHistoryFirstNonEmpty(gRows, "vessel")
    pdfRequest.pol = shippingHistoryFirstNonEmpty(gRows, "pol")
    pdfRequest.pod = shippingHistoryFirstNonEmpty(gRows, "pod")
    pdfRequest.shippingDate = shippingHistoryFirstNonEmpty(gRows, "shipmentDate")
    pdfRequest.consigneeName = shippingHistoryFirstNonEmpty(gRows, "consignee")
    pdfRequest.consigneeAddress = ""
    pdfRequest.consigneeCountry = shippingHistoryFirstNonEmpty(gRows, "country")
    pdfRequest.carrier = shippingHistoryFirstNonEmpty(gRows, "carrier")
    pdfRequest.cyCutDate = shippingHistoryFirstNonEmpty(gRows, "cyCutDate")
    pdfRequest.eta = shippingHistoryFirstNonEmpty(gRows, "eta")
    pdfRequest.finalDestination = shippingHistoryFirstNonEmpty(gRows, "finalDestination")
    pdfRequest.notifyParty = shippingHistoryFirstNonEmpty(gRows, "notifyParty")
    pdfRequest.inTransitClause = shippingHistoryFirstNonEmpty(gRows, "inTransitClause")
    pdfRequest.calculationMode = if (isFob) "FOB" else "C&F"
    pdfRequest.chassisNumbers = chassisList.toTypedArray()
    val totals = js("{}")
    var totalsCount = 0
    for (r in sorted) {
        val ch = shippingHistoryCell(r, "chassis").trim()
        if (ch.isEmpty()) continue
        val yen = parseShippingHistoryYenAmount(shippingHistoryRawField(r, "amount")) ?: continue
        js("(function(o,k,v){ o[k]=v; })")(totals, ch, yen)
        totalsCount++
    }
    if (totalsCount > 0) {
        pdfRequest.frontendTotalYenByChassis = totals
    }
    return pdfRequest
}

private fun downloadShippingHistoryPdf(gRows: List<dynamic>, btn: HTMLButtonElement?) {
    val pdfRequest = buildShippingPdfRequestFromHistoryGroup(gRows) ?: return
    val mode = pdfRequest.calculationMode?.toString()?.trim().orEmpty()
    val isFob = mode.contains("FOB", ignoreCase = true)
    val bookingNo = pdfRequest.bookingNo?.toString()?.trim().orEmpty().ifEmpty { "unknown" }
    val vessel = pdfRequest.vesselName?.toString()?.trim().orEmpty()
    val docType = if (isFob) "FOB_ShippingSchedule" else "ShippingSchedule"
    val endpoint = if (isFob) {
        "purchases/fob-shipping-schedule/generate-pdf"
    } else {
        "purchases/shipping-schedule/generate-pdf"
    }
    if (btn != null) {
        btn.disabled = true
        btn.style.opacity = "0.6"
    }
    MainScope().launch {
        try {
            val headers = Headers()
            headers.set("Content-Type", "application/json")
            val requestInit = RequestInit(
                method = "POST",
                headers = headers,
                body = JSON.stringify(pdfRequest),
            )
            val response = window.fetch(apiUrl(endpoint), requestInit).await()
            if (!response.ok) {
                val errorText = response.text().await()
                ErrorHandler.showError("Failed to download PDF: ${ErrorHandler.extractErrorMessage(errorText)}")
                return@launch
            }
            val blob = response.blob().await()
            val url = js("URL.createObjectURL(blob)") as String
            try {
                val a = document.createElement("a") as HTMLAnchorElement
                a.href = url
                a.download = buildPdfFilename(docType, bookingNo, vessel)
                document.body?.appendChild(a)
                a.click()
                document.body?.removeChild(a)
                showMessage("PDF downloaded successfully", "success")
            } finally {
                js("URL.revokeObjectURL(url)")
            }
        } catch (e: dynamic) {
            ErrorHandler.showError("Failed to download PDF: ${e.toString()}")
        } finally {
            if (btn != null) {
                btn.disabled = false
                btn.style.opacity = "1"
            }
        }
    }
}

private fun shippingHistoryRowsPayloadArray(gRows: List<dynamic>): dynamic {
    val rowsJson = js("[]")
    for (r in gRows) {
        val o = js("{}")
        o.id = shippingHistoryRawField(r, "id")
        o.country = shippingHistoryCell(r, "country")
        o.consignee = shippingHistoryCell(r, "consignee")
        o.notifyParty = shippingHistoryCell(r, "notifyParty")
        o.inTransitClause = shippingHistoryCell(r, "inTransitClause")
        o.shipmentDate = shippingHistoryCell(r, "shipmentDate")
        o.cyCutDate = shippingHistoryCell(r, "cyCutDate")
        o.eta = shippingHistoryCell(r, "eta")
        o.pol = shippingHistoryCell(r, "pol")
        o.pod = shippingHistoryCell(r, "pod")
        o.finalDestination = shippingHistoryCell(r, "finalDestination")
        o.bookingId = shippingHistoryCell(r, "bookingId")
        o.vessel = shippingHistoryCell(r, "vessel")
        o.carrier = shippingHistoryCell(r, "carrier")
        o.priceType = shippingHistoryCell(r, "priceType")
        o.chassis = shippingHistoryCell(r, "chassis")
        o.clientName = shippingHistoryCell(r, "clientName")
        o.amount = shippingHistoryRawField(r, "amount")
        js("rowsJson.push(o)")
    }
    return rowsJson
}

private fun storeAndNavigateShippingHistoryEdit(gRows: List<dynamic>) {
    val payload = js("{}")
    payload.rows = shippingHistoryRowsPayloadArray(gRows)
    window.sessionStorage.setItem(SHIPPING_HISTORY_EDIT_SESSION_KEY, JSON.stringify(payload))
    navigateToApp("/recalculate-booking")
}

private fun autoCreateInvoicesFromShippingHistory(gRows: List<dynamic>) {
    // --- Loading overlay ---
    val overlayId = "shippingInvoiceBatchOverlay"
    document.getElementById(overlayId)?.remove()
    val overlay = document.createElement("div") as org.w3c.dom.HTMLDivElement
    overlay.id = overlayId
    overlay.style.cssText = "position:fixed;inset:0;background:rgba(0,0,0,0.50);z-index:99999;display:flex;align-items:center;justify-content:center;"
    overlay.innerHTML = """<div style="background:#fff;border-radius:14px;padding:32px 40px;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,0.18);"><div style="font-size:22px;font-weight:700;color:#111827;margin-bottom:10px;">Creating invoices…</div><div style="color:#6b7280;font-size:15px;">Please wait</div></div>"""
    document.body?.appendChild(overlay)

    fun dismiss() { document.getElementById(overlayId)?.remove() }

    MainScope().launch {
        try {
            // --- Group rows by clientName ---
            val byClient = mutableMapOf<String, MutableList<dynamic>>()
            for (r in gRows) {
                val client = (r.clientName?.toString() ?: "").trim().ifEmpty { "Unknown" }
                byClient.getOrPut(client) { mutableListOf() }.add(r)
            }

            if (byClient.isEmpty()) {
                dismiss()
                showMessage("No rows to invoice.", "warning")
                return@launch
            }

            val timestamp = (js("Date.now()") as Double).toLong()
            // Build invoice payloads — one per client
            val invoicesJs = js("[]")
            var idx = 0
            for ((clientName, clientRows) in byClient) {
                idx++
                val sortedRows = clientRows.sortedBy { r -> r.id?.toString()?.toLongOrNull() ?: 0L }
                val first = sortedRows.first()

                val invoiceNumber = "INV-${timestamp}-${idx}"
                val invoiceDateIso = (js("new Date().toISOString().substring(0,10)") as String)
                val vessel = (first.vessel?.toString() ?: "").trim()
                val shipmentDate = (first.shipmentDate?.toString() ?: "").trim()
                val pol = (first.pol?.toString() ?: "").trim()
                val pod = (first.pod?.toString() ?: "").trim()
                val priceType = (first.priceType?.toString() ?: "C&F").trim()

                // Build items list: one per chassis row in this client group
                val itemsJs = js("[]")
                val chassisList = mutableListOf<String>()
                var purchaseIdsList = mutableListOf<Long>()
                var totalYen = 0.0

                for ((rowIdx, r) in sortedRows.withIndex()) {
                    val chassis = (r.chassis?.toString() ?: "").trim()
                    if (chassis.isEmpty()) continue
                    chassisList.add(chassis)

                    // Parse amount from shipping history row (already numeric from backend)
                    val amtRaw = (r.amount?.toString() ?: "0").trim()
                    val amtNum = parseCurrency(amtRaw)
                    totalYen += amtNum
                    val amtFormatted = "¥" + formatCurrency(amtNum)

                    val item = js("{}")
                    item.unit = rowIdx + 1
                    item.description = chassis
                    item.amount = amtFormatted
                    js("itemsJs.push(item)")
                }

                val totalFormatted = "¥" + formatCurrency(totalYen)

                val pdfObj = js("{}")
                pdfObj.invoiceNumber = invoiceNumber
                pdfObj.invoiceDate = invoiceDateIso
                pdfObj.lcNumber = null
                pdfObj.clientName = clientName
                pdfObj.clientAddress = null
                pdfObj.vessel = vessel
                pdfObj.shippingDate = shipmentDate
                pdfObj["from"] = pol
                pdfObj.to = pod
                pdfObj.priceType = priceType
                pdfObj.items = itemsJs
                pdfObj.totalAmount = totalFormatted
                pdfObj.bankAccount = null
                pdfObj.message = null

                val entry = js("{}")
                entry.purchaseIds = js("[]")
                entry.chassisJoined = chassisList.joinToString(";")
                entry.shippingDateIso = shipmentDate.takeIf { it.isNotBlank() && it.length >= 8 }
                entry.pdf = pdfObj
                js("invoicesJs.push(entry)")
            }

            // --- POST to batch-confirm ---
            val bodyObj = js("{}")
            bodyObj.invoices = invoicesJs
            val bodyJson = JSON.stringify(bodyObj)

            val fetchOpts = js("{}")
            fetchOpts.method = "POST"
            val hdr = js("{}")
            hdr["Content-Type"] = "application/json"
            fetchOpts.headers = hdr
            fetchOpts.body = bodyJson

            val response = window.fetch(apiUrl("purchases/invoice/batch-confirm"), fetchOpts).await()
            val resultJson = response.json().await()
            val savedCount = (js("(function(r){return r.saved;})(resultJson)") as? Number)?.toInt() ?: 0
            val errorsArr = js("(function(r){return r.errors;})(resultJson)")
            val hasErrors = (js("Array.isArray(errorsArr) && errorsArr.length > 0") as Boolean)

            dismiss()
            if (savedCount > 0) {
                showSuccessModal(
                    "Saved",
                    if (savedCount == 1) "1 invoice created successfully."
                    else "$savedCount invoices created successfully.",
                )
                navigateToApp("/invoice-history")
            } else if (hasErrors) {
                showMessage("Failed to create invoices. Check console for details.", "error")
                console.error("[batch-confirm] errors:", errorsArr)
            } else {
                showMessage("No invoices were created (possibly already exist).", "warning")
                navigateToApp("/invoice-history")
            }
        } catch (e: Throwable) {
            dismiss()
            showMessage("Error creating invoices: ${e.message ?: e.toString()}", "error")
            console.error("[autoCreateInvoices]", e.toString())
        }
    }
}

private fun shippingHistoryIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= SHIPPING_HISTORY_COMPACT_MAX_WIDTH_PX
}

private fun setupShippingHistoryResizeListener() {
    if (document.getElementById("shippingHistoryPage") == null) return
    shippingHistoryLastCompactLayout = shippingHistoryIsCompactLayout()

    val existing = window.asDynamic().__shippingHistoryCompactResizeListener
    if (existing != null) {
        window.removeEventListener("resize", existing.unsafeCast<(Event) -> Unit>())
    }

    val resizeListener: (Event) -> Unit = { _: Event ->
        val prev = shippingHistoryResizeDebounceHandle
        if (prev != null) window.clearTimeout(prev)
        shippingHistoryResizeDebounceHandle = window.setTimeout({
            if (document.getElementById("shippingHistoryPage") == null) return@setTimeout
            val compact = shippingHistoryIsCompactLayout()
            if (shippingHistoryLastCompactLayout != compact) {
                shippingHistoryLastCompactLayout = compact
                if (shippingHistoryCachedRows.isNotEmpty()) renderShippingHistoryTableFromCache()
            }
        }, 120)
    }
    window.asDynamic().__shippingHistoryCompactResizeListener = resizeListener
    window.addEventListener("resize", resizeListener)
}

private fun shippingHistoryInvoiceStatusCircleHtml(gRows: List<dynamic>): String {
    val invoicedCount = gRows.count { shippingHistoryRowInvoiceCreated(it) }
    val totalRows = gRows.size
    return when {
        totalRows == 0 ->
            """<span role="img" aria-label="No rows" title="No rows" style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;background:#f3f4f6;border:2px solid #d1d5db;"></span>"""
        invoicedCount == totalRows ->
            """<span role="img" aria-label="All chassis invoiced" title="All chassis invoiced" style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;background:#d1fae5;border:2px solid #6ee7b7;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M5 13l4 4L19 7" stroke="#059669" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>"""
        invoicedCount > 0 ->
            """<span role="img" aria-label="Partially invoiced ($invoicedCount/$totalRows)" title="Partially invoiced ($invoicedCount/$totalRows)" style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;background:#fef3c7;border:2px solid #fcd34d;"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M5 13l4 4L19 7" stroke="#d97706" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>"""
        else ->
            """<span role="img" aria-label="Not invoiced" title="Not invoiced" style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:50%;background:#f9fafb;border:2px solid #e5e7eb;"></span>"""
    }
}

private fun shippingHistoryGroupIdsCsv(gRows: List<dynamic>): String =
    gRows.mapNotNull { shippingHistoryCell(it, "id").trim().takeIf { id -> id.isNotEmpty() } }
        .sorted()
        .joinToString(",")

private fun shippingHistoryDisplayCellHtml(gRows: List<dynamic>, key: String): String = when (key) {
    "country" -> formatHistoryListRectChipHtml(shippingHistoryRepresentativeCountry(gRows))
    "chassis", "clientName", "amount" ->
        formatHistoryListCollapsibleChipsHtml(shippingHistoryUniqueSortedValues(gRows, key))
    else -> formatHistoryListDistinctChipsHtml(shippingHistoryUniqueSortedValues(gRows, key))
}

private fun appendShippingHistoryGroupTableRow(html: StringBuilder, gRows: List<dynamic>) {
    html.append("<tr>")
    val groupIds = shippingHistoryGroupIdsCsv(gRows)
    html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${shippingHistoryEditButtonHtml(groupIds)}</td>""")
    html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${shippingHistoryPdfButtonHtml(groupIds)}</td>""")
    html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${shippingHistoryInvoiceButtonHtml(groupIds)}</td>""")
    html.append(
        """<td style="padding: 12px; vertical-align: middle; text-align: center;">${shippingHistoryInvoiceStatusCircleHtml(gRows)}</td>"""
    )
    if (shippingHistoryShowsBookingIdColumn()) {
        val bookingCell = escapeHtml(shippingHistoryRepresentativeBookingDisplay(gRows))
        html.append("""<td style="padding: 12px; vertical-align: top; font-weight: 600;">$bookingCell</td>""")
    }
    for (key in shippingHistoryDisplayColumnKeys()) {
        html.append("""<td style="padding: 12px; vertical-align: top;">${shippingHistoryDisplayCellHtml(gRows, key)}</td>""")
    }
    html.append("</tr>")
}

private fun appendShippingHistoryGroupCard(html: StringBuilder, gRows: List<dynamic>) {
    val groupIds = shippingHistoryGroupIdsCsv(gRows)
    html.append("""<div class="shipping-card">""")
    html.append(
        """
        <div class="shipping-card-top">
            <div class="shipping-card-actions">
                ${shippingHistoryEditButtonHtml(groupIds)}
                ${shippingHistoryPdfButtonHtml(groupIds)}
                ${shippingHistoryInvoiceButtonHtml(groupIds)}
            </div>
            <div class="shipping-card-status" title="Invoice created status">
                ${shippingHistoryInvoiceStatusCircleHtml(gRows)}
            </div>
        </div>
        """
    )
    html.append("""<div class="shipping-card-grid">""")
    if (shippingHistoryShowsBookingIdColumn()) {
        val booking = shippingHistoryRepresentativeBookingDisplay(gRows)
        if (booking.isNotEmpty() && booking != "—") {
            html.append(
                """<div class="shipping-kv"><div class="shipping-k">Booking ID</div><div class="shipping-v">${formatHistoryListRectChipHtml(booking)}</div></div>"""
            )
        }
    }
    for (key in shippingHistoryDisplayColumnKeys()) {
        val cellHtml = shippingHistoryDisplayCellHtml(gRows, key)
        if (cellHtml.isEmpty()) continue
        val label = escapeHtml(shippingHistoryColumnLabel(key))
        html.append("""<div class="shipping-kv"><div class="shipping-k">$label</div><div class="shipping-v">$cellHtml</div></div>""")
    }
    html.append("""</div></div>""")
}

private fun scheduleShippingHistorySearchDebounced() {
    val prev = shippingHistorySearchDebounceHandle
    if (prev != null) window.clearTimeout(prev)
    shippingHistorySearchDebounceHandle = window.setTimeout({
        shippingHistorySearchDebounceHandle = null
        shippingHistoryPageZeroBased = 0
        loadShippingHistory(0)
    }, 420)
}

private fun applyShippingHistoryPageBody(body: dynamic) {
    shippingHistoryServerMode = true
    val totalEl = js("body.totalElements")
    shippingHistoryTotalElements = when (totalEl) {
        is Number -> totalEl.toLong()
        else -> totalEl?.toString()?.toLongOrNull() ?: 0L
    }
    val tp = js("body.totalPages")
    shippingHistoryTotalPages = kotlin.math.max(
        1,
        when (tp) {
            is Number -> tp.toInt()
            else -> tp?.toString()?.toIntOrNull() ?: 1
        },
    )
    val num = js("body.page")
    shippingHistoryPageZeroBased = when (num) {
        is Number -> num.toInt()
        else -> num?.toString()?.toIntOrNull() ?: 0
    }
    val sz = js("body.size")
    shippingHistoryItemsPerPage = when (sz) {
        is Number -> sz.toInt()
        else -> sz?.toString()?.toIntOrNull() ?: AppConstants.DEFAULT_ITEMS_PER_PAGE
    }
    shippingHistoryCachedRows = try {
        js("Array.from((body && body.content) ? body.content : [])").unsafeCast<Array<dynamic>>()
    } catch (_: dynamic) {
        emptyArray()
    }
}

private fun loadShippingHistory(page0: Int = shippingHistoryPageZeroBased) {
    val tableHost = document.getElementById("shippingHistoryTable") ?: return
    tableHost.innerHTML = """<div class="shipping-history-empty"><strong>Loading</strong><div>Loading shipping history…</div></div>"""

    val q = (document.getElementById("shippingHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""
    shippingHistoryActiveSearchQ = q
    shippingHistoryPageZeroBased = page0.coerceAtLeast(0)
    val size = shippingHistoryItemsPerPage.coerceAtLeast(1)
    val endpoint = if (q.isNotEmpty()) {
        val encQ = js("encodeURIComponent")(q).unsafeCast<String>()
        "shipping-history/page-search?q=$encQ&page=$shippingHistoryPageZeroBased&size=$size"
    } else {
        "shipping-history/page?page=$shippingHistoryPageZeroBased&size=$size"
    }

    MainScope().launch {
        ApiClient.get<dynamic>(endpoint).fold(
            onSuccess = { body ->
                if (body == null) {
                    ErrorHandler.showError("Empty shipping history response")
                    return@fold
                }
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    ErrorHandler.showError(err)
                    return@fold
                }
                applyShippingHistoryPageBody(body)
                renderShippingHistoryTableFromCache()
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load shipping history: $message")
                tableHost.innerHTML = """
                    <div class="shipping-history-empty shipping-history-empty--error">
                        <strong>Could not load</strong>
                        <div>Unable to load shipping history. Please reload and try again.</div>
                    </div>
                """
            },
        )
    }
}

/** Data columns only; Booking ID is rendered in its own left column after Actions when selected. */
private fun shippingHistoryAllSelectableColumnKeys(): List<String> = listOf(
    "bookingId",
    "country",
    "consignee",
    "notifyParty",
    "inTransitClause",
    "shipmentDate",
    "cyCutDate",
    "eta",
    "pol",
    "pod",
    "finalDestination",
    "vessel",
    "carrier",
    "priceType",
    "chassis",
    "clientName",
    "amount",
)

private fun shippingHistoryLockedColumnKeys(): Set<String> = setOf("bookingId", "country", "chassis")

private fun shippingHistoryDefaultColumnKeys(): List<String> = listOf(
    "bookingId", "country", "consignee", "shipmentDate", "chassis", "clientName",
)

private const val SHIPPING_HISTORY_MAX_DATA_COLUMNS = 6
private const val SHIPPING_HISTORY_COLUMNS_STORAGE_KEY = "selectedShippingHistoryColumns"

/** Selected data columns (max 6). Booking ID / Country / Chassis are always included. */
private fun getSelectedShippingHistoryColumns(): List<String> {
    val defaults = shippingHistoryDefaultColumnKeys()
    val locked = shippingHistoryLockedColumnKeys()
    val allowed = shippingHistoryAllSelectableColumnKeys().toSet()
    val saved = safeLocalStorageGet(SHIPPING_HISTORY_COLUMNS_STORAGE_KEY)
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList().filter { it in allowed }
        } catch (_: dynamic) {
            null
        }
    } else {
        null
    }
    val base = if (savedColumns.isNullOrEmpty()) defaults else savedColumns
    val withLocked = (locked.toList() + base).distinct()
    val ordered = shippingHistoryAllSelectableColumnKeys().filter { it in withLocked.toSet() }
    return if (ordered.size > SHIPPING_HISTORY_MAX_DATA_COLUMNS) {
        defaults
    } else {
        ordered
    }
}

private fun saveSelectedShippingHistoryColumns(columns: List<String>) {
    val locked = shippingHistoryLockedColumnKeys()
    val allowed = shippingHistoryAllSelectableColumnKeys().toSet()
    val merged = (locked.toList() + columns.filter { it in allowed }).distinct()
    val ordered = shippingHistoryAllSelectableColumnKeys().filter { it in merged.toSet() }
        .take(SHIPPING_HISTORY_MAX_DATA_COLUMNS)
    val finalCols = if (ordered.size < locked.size) shippingHistoryDefaultColumnKeys() else ordered
    safeLocalStorageSet(SHIPPING_HISTORY_COLUMNS_STORAGE_KEY, JSON.stringify(finalCols.toTypedArray()))
}

/** Non-bookingId columns shown in table body / cards. */
private fun shippingHistoryDisplayColumnKeys(): List<String> =
    getSelectedShippingHistoryColumns().filter { it != "bookingId" }

private fun shippingHistoryShowsBookingIdColumn(): Boolean =
    "bookingId" in getSelectedShippingHistoryColumns()

private fun shippingHistorySearchColumnKeys(): List<String> =
    listOf("id", "createdAt") + shippingHistoryAllSelectableColumnKeys()

private fun shippingHistoryColumnLabel(key: String): String = when (key) {
    "country" -> "Country"
    "consignee" -> "Consignee"
    "notifyParty" -> "Notify party"
    "inTransitClause" -> "In-Transit Clause"
    "shipmentDate" -> "Shipment date"
    "cyCutDate" -> "CY CUT"
    "eta" -> "ETA"
    "pol" -> "POL"
    "pod" -> "POD"
    "finalDestination" -> "Final Destination"
    "bookingId" -> "Booking ID"
    "vessel" -> "Vessel"
    "carrier" -> "Carrier"
    "priceType" -> "Price type"
    "chassis" -> "Chassis"
    "clientName" -> "Client name"
    "amount" -> "Amount"
    else -> key
}

/** Raw API value for edit payload (no ¥/comma formatting). */
private fun shippingHistoryRawField(row: dynamic, key: String): String {
    val d = row
    val v: dynamic = when (key) {
        "id" -> d.id
        "country" -> d.country
        "consignee" -> d.consignee
        "notifyParty" -> d.notifyParty
        "inTransitClause" -> d.inTransitClause
        "shipmentDate" -> d.shipmentDate
        "cyCutDate" -> d.cyCutDate
        "eta" -> d.eta
        "pol" -> d.pol
        "pod" -> d.pod
        "finalDestination" -> d.finalDestination
        "bookingId" -> d.bookingId
        "vessel" -> d.vessel
        "carrier" -> d.carrier
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

private fun shippingHistoryCell(row: dynamic, key: String): String {
    val d = row
    val v: dynamic = when (key) {
        "id" -> d.id
        "country" -> d.country
        "consignee" -> d.consignee
        "notifyParty" -> d.notifyParty
        "inTransitClause" -> d.inTransitClause
        "shipmentDate" -> d.shipmentDate
        "cyCutDate" -> d.cyCutDate
        "eta" -> d.eta
        "pol" -> d.pol
        "pod" -> d.pod
        "finalDestination" -> d.finalDestination
        "bookingId" -> d.bookingId
        "vessel" -> d.vessel
        "carrier" -> d.carrier
        "priceType" -> d.priceType
        "chassis" -> d.chassis
        "clientName" -> d.clientName
        "amount" -> {
            val amount = d.amount
            if (amount == null || amount == js("void 0")) {
                return ""
            }
            val amountStr = amount.toString().trim()
            if (amountStr.isEmpty()) {
                return ""
            }
            val numValue = parseCurrency(amountStr)
            if (numValue > 0.0) "¥${formatCurrency(numValue)}" else ""
        }
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

private fun normalizeShippingHistoryBookingKey(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return "—"
    val asLong = s.toLongOrNull()
    if (asLong != null) return asLong.toString()
    val asDouble = s.toDoubleOrNull()
    if (asDouble != null && !asDouble.isNaN()) return asDouble.toLong().toString()
    return s
}

/** One table row per distinct booking id (blank booking id → "—"). */
private fun shippingHistoryBookingGroupKey(row: dynamic): String {
    return normalizeShippingHistoryBookingKey(shippingHistoryCell(row, "bookingId"))
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

private fun shippingHistoryRepresentativeBookingDisplay(rows: List<dynamic>): String {
    val sorted = rows.sortedBy { row -> shippingHistoryCell(row, "id").toLongOrNull() ?: 0L }
    val row = sorted.firstOrNull { r -> shippingHistoryCell(r, "bookingId").isNotEmpty() }
    // Do not use .let on dynamic — Kotlin/JS emits e.let which is not a function on plain objects.
    val raw = if (row == null) null else shippingHistoryCell(row, "bookingId").trim()
    return if (raw.isNullOrEmpty()) "—" else normalizeShippingHistoryBookingKey(raw)
}

private fun compareNormalizedBookingGroupKeys(ka: String, kb: String): Int {
    if (ka == "—" && kb == "—") return 0
    if (ka == "—") return 1
    if (kb == "—") return -1
    val la = ka.toLongOrNull()
    val lb = kb.toLongOrNull()
    if (la != null && lb != null) return la.compareTo(lb)
    return ka.lowercase().compareTo(kb.lowercase())
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
        "bookingId" -> {
            val sa = shippingHistoryBookingGroupKey(rowsA.first())
            val sb = shippingHistoryBookingGroupKey(rowsB.first())
            orient(compareNormalizedBookingGroupKeys(sa, sb))
        }
        "country" -> {
            val sa = shippingHistoryUniqueSortedValues(rowsA, "country").minOrNull()?.lowercase() ?: ""
            val sb = shippingHistoryUniqueSortedValues(rowsB, "country").minOrNull()?.lowercase() ?: ""
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

private fun appendShippingHistoryPager(html: StringBuilder) {
    if (!shippingHistoryServerMode) return
    val totalPages = kotlin.math.max(1, shippingHistoryTotalPages)
    val currentPage = shippingHistoryPageZeroBased + 1
    if (totalPages <= 1 && shippingHistoryTotalElements <= shippingHistoryItemsPerPage) return
    val prevDisabled = currentPage <= 1
    val nextDisabled = currentPage >= totalPages
    html.append(
        """
        <div id="shippingHistoryPager" class="shipping-history-pager">
            <div class="shipping-history-pager-meta">Showing page $currentPage of $totalPages · $shippingHistoryTotalElements group row(s)</div>
            <div class="shipping-history-pager-btns">
                <button type="button" id="shippingHistoryPrevPage" class="shipping-history-pager-btn${if (prevDisabled) " is-disabled" else ""}" ${if (prevDisabled) "disabled" else ""} aria-label="Previous page">Previous</button>
                <button type="button" id="shippingHistoryNextPage" class="shipping-history-pager-btn${if (nextDisabled) " is-disabled" else ""}" ${if (nextDisabled) "disabled" else ""} aria-label="Next page">Next</button>
            </div>
        </div>
        """
    )
}

private fun wireShippingHistoryPager() {
    document.getElementById("shippingHistoryPrevPage")?.addEventListener("click", { _: Event ->
        if (shippingHistoryPageZeroBased > 0) loadShippingHistory(shippingHistoryPageZeroBased - 1)
    })
    document.getElementById("shippingHistoryNextPage")?.addEventListener("click", { _: Event ->
        if (shippingHistoryPageZeroBased + 1 < shippingHistoryTotalPages) {
            loadShippingHistory(shippingHistoryPageZeroBased + 1)
        }
    })
}

private fun renderShippingHistoryTableFromCache() {
    val tableHost = document.getElementById("shippingHistoryTable") ?: return
    val q = if (shippingHistoryServerMode) {
        shippingHistoryActiveSearchQ
    } else {
        (document.getElementById("shippingHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""
    }

    if (shippingHistoryCachedRows.isEmpty()) {
        val emptyHtml = if (q.isNotEmpty()) {
            """
            <div class="shipping-history-empty">
                <strong>No matches</strong>
                <div>No rows match your search.</div>
                <button type="button" id="shippingHistoryClearSearchCta" class="shipping-history-empty-cta">Clear search</button>
            </div>
            """.trimIndent()
        } else {
            """<div class="shipping-history-empty"><strong>No history yet</strong><div>No shipping history records yet.</div></div>"""
        }
        val html = StringBuilder(emptyHtml)
        appendShippingHistoryPager(html)
        tableHost.innerHTML = html.toString()
        wireShippingHistoryPager()
        return
    }

    val rowList = if (shippingHistoryServerMode) {
        shippingHistoryCachedRows.toList()
    } else {
        shippingHistoryCachedRows.filter { shippingHistoryRowMatchesQuery(it, q) }.toList()
    }

    if (rowList.isEmpty()) {
        tableHost.innerHTML = """
            <div class="shipping-history-empty">
                <strong>No matches</strong>
                <div>No rows match your search.</div>
                <button type="button" id="shippingHistoryClearSearchCta" class="shipping-history-empty-cta">Clear search</button>
            </div>
        """.trimIndent()
        return
    }

    val groups = rowList.groupBy { shippingHistoryBookingGroupKey(it) }
        .values
        .map { memberRows ->
            memberRows.sortedBy { shippingHistoryCell(it, "id").toLongOrNull() ?: 0L }
        }
        .sortedWith { a, b ->
            compareShippingHistoryGroupRows(a, b, shippingHistorySortField, shippingHistorySortOrder == "asc")
        }

    val compact = shippingHistoryIsCompactLayout()
    shippingHistoryLastCompactLayout = compact
    val html = StringBuilder()

    if (!compact) {
        val bookingCol = if (shippingHistoryShowsBookingIdColumn()) 1 else 0
        val colCountShip = 4 + bookingCol + shippingHistoryDisplayColumnKeys().size
        // Wider action cols so Invoice / Invoice Created headers do not overlap
        html.append(
            """<div class="shipping-history-table-shell"><table class="purchase-list-table" style="width:100%;border-collapse:collapse;table-layout:fixed;">""" +
                htmlTableColgroupMultipleNarrowActionsEqualRest(colCountShip, 56, 56, 88, 128) +
                """<thead><tr style="background-color:#f8f9fa;">""",
        )
        html.append("""<th style="padding:10px 8px;text-align:center;border-bottom:1px solid #dee2e6;font-weight:600;color:#111827;white-space:nowrap;">Edit</th>""")
        html.append("""<th style="padding:10px 8px;text-align:center;border-bottom:1px solid #dee2e6;font-weight:600;color:#111827;white-space:nowrap;">PDF</th>""")
        html.append("""<th style="padding:10px 8px;text-align:center;border-bottom:1px solid #dee2e6;font-weight:600;color:#111827;white-space:nowrap;">Invoice</th>""")
        html.append(
            """<th style="padding:10px 6px;text-align:center;border-bottom:1px solid #dee2e6;font-weight:600;color:#111827;white-space:nowrap;font-size:13px;" title="Invoice created status">Invoice Created</th>"""
        )
        fun appendSortableHeader(key: String) {
            val label = escapeHtml(shippingHistoryColumnLabel(key))
            val isActive = shippingHistorySortField == key
            val sortOrder = if (isActive) shippingHistorySortOrder else "desc"
            val sortIcon = when {
                !isActive -> "↕"
                sortOrder == "asc" -> "↑"
                else -> "↓"
            }
            val activeClass = if (isActive) " is-active" else ""
            val tooltipRaw = when {
                !isActive -> "Sort by ${shippingHistoryColumnLabel(key)}"
                sortOrder == "asc" -> "Sorted ascending (click for descending)"
                else -> "Sorted descending (click for ascending)"
            }
            val tooltip = escapeHtml(tooltipRaw)
            html.append(
                """
                <th style="padding:12px;text-align:left;border-bottom:1px solid #dee2e6;">
                    <button type="button" class="shipping-history-sort-btn$activeClass" data-shipping-sort="$key" title="$tooltip" aria-label="$tooltip">
                        <span>$label</span><span class="shipping-history-sort-icon" aria-hidden="true">$sortIcon</span>
                    </button>
                </th>
                """
            )
        }
        if (shippingHistoryShowsBookingIdColumn()) {
            appendSortableHeader("bookingId")
        }
        for (key in shippingHistoryDisplayColumnKeys()) {
            appendSortableHeader(key)
        }
        html.append("</tr></thead><tbody id='shippingHistoryTableBody'>")
        for (gRows in groups) {
            appendShippingHistoryGroupTableRow(html, gRows)
        }
        html.append("</tbody></table></div>")
    } else {
        html.append("""<div id="shippingHistoryTableBody" class="shipping-cards">""")
        for (gRows in groups) {
            appendShippingHistoryGroupCard(html, gRows)
        }
        html.append("</div>")
    }

    appendShippingHistoryPager(html)
    tableHost.innerHTML = html.toString()
    wireShippingHistoryPager()
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
            val max = getMaxPurchaseListColumnsForDevice(currentDeviceType)
            val sanitized = sanitizePurchaseListSelectedColumns(savedColumns)
            val adjustedColumns = ensurePurchaseListPinnedColumns(
                prioritizePurchaseListDateAndChassis(
                    autoAdjustColumnsForDevice(sanitized, currentDeviceType),
                ),
                max,
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
                if (routeStartsWith("/purchase")) {
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

fun exportPurchasesToExcel() {
    val btn = document.getElementById("exportPurchasesExcelBtn") as? HTMLButtonElement
    val originalHtml = btn?.innerHTML
    if (btn != null) {
        btn.disabled = true
        btn.style.opacity = "0.7"
        btn.textContent = "Exporting…"
    }
    MainScope().launch {
        try {
            showMessage("Preparing Excel export…", "info")
            val response = window.fetch(apiUrl("purchases/export/xlsx")).await()
            if (!response.ok) {
                val errorText = response.text().await()
                ErrorHandler.showError("Export failed: ${ErrorHandler.extractErrorMessage(errorText)}")
                return@launch
            }
            val blob = response.blob().await()
            val url = js("URL.createObjectURL(blob)") as String
            try {
                val disposition = response.headers.get("Content-Disposition") ?: ""
                val filenameMatch = Regex("filename=\"?([^\";]+)\"?").find(disposition)
                val filename = filenameMatch?.groupValues?.get(1)
                    ?: "purchases_export_${js("Date.now()")}.xlsx"
                val a = document.createElement("a") as HTMLAnchorElement
                a.href = url
                a.download = filename
                document.body?.appendChild(a)
                a.click()
                document.body?.removeChild(a)
                showMessage("Purchase list exported successfully", "success")
            } finally {
                js("URL.revokeObjectURL(url)")
            }
        } catch (e: dynamic) {
            ErrorHandler.showError("Export failed: ${e.toString()}")
        } finally {
            if (btn != null) {
                btn.disabled = false
                btn.style.opacity = "1"
                if (originalHtml != null) btn.innerHTML = originalHtml
                else btn.textContent = "Export Excel"
            }
        }
    }
}

private fun showShippingHistoryColumnFilterModal() {
    document.getElementById("shippingHistoryColumnFilterModal")?.remove()
    shippingHistoryColumnFilterKeyHandler?.let { document.removeEventListener("keydown", it) }
    shippingHistoryColumnFilterKeyHandler = null

    val returnFocus = document.activeElement as? HTMLElement
    val modal = document.createElement("div")
    modal.id = "shippingHistoryColumnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; inset: 0;
        background-color: rgba(15,23,42,0.45); z-index: 10000;
        display: flex; align-items: center; justify-content: center; padding: 16px;
    """

    val selected = getSelectedShippingHistoryColumns().toSet()
    val locked = shippingHistoryLockedColumnKeys()

    modal.innerHTML = """
        <div role="dialog" aria-modal="true" aria-labelledby="shippingHistoryColumnFilterTitle"
             style="background: white; border-radius: 12px; padding: 24px; max-width: 520px; width: 100%; max-height: 80vh; overflow-y: auto; box-shadow: 0 20px 50px rgba(0,0,0,0.2);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
                <h3 id="shippingHistoryColumnFilterTitle" style="margin: 0; color: #0f172a; font-size: 18px; font-weight: 700;">Select Columns to Display</h3>
                <button type="button" id="closeShippingHistoryColumnFilter" aria-label="Close" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center;">&times;</button>
            </div>
            <p style="margin: 0 0 8px 0; color: #64748b; font-size: 13px;">Max $SHIPPING_HISTORY_MAX_DATA_COLUMNS data columns. Booking ID, Country, and Chassis are always on. Action buttons are always visible.</p>
            <p id="shippingHistoryColumnCount" style="margin: 0 0 16px 0; font-size: 13px; font-weight: 600; color: #0f172a;"></p>
            <div id="shippingHistoryColumnCheckboxes" style="display: flex; flex-direction: column; gap: 10px; margin-bottom: 20px;"></div>
            <div style="display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap;">
                <button type="button" id="resetShippingHistoryColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; min-height: 40px;">Reset to Default</button>
                <button type="button" id="applyShippingHistoryColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; min-height: 40px;">Apply Changes</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    fun closeModal() {
        shippingHistoryColumnFilterKeyHandler?.let { document.removeEventListener("keydown", it) }
        shippingHistoryColumnFilterKeyHandler = null
        modal.remove()
        returnFocus?.focus()
    }

    fun updateCountAndLocks() {
        val boxes = document.querySelectorAll("#shippingHistoryColumnCheckboxes input[type=checkbox]")
        var count = 0
        for (i in 0 until boxes.length) {
            val el = boxes.item(i) as? HTMLInputElement ?: continue
            if (el.checked) count++
        }
        document.getElementById("shippingHistoryColumnCount")?.textContent =
            "Selected: $count / $SHIPPING_HISTORY_MAX_DATA_COLUMNS"
        for (i in 0 until boxes.length) {
            val el = boxes.item(i) as? HTMLInputElement ?: continue
            val key = el.getAttribute("data-column") ?: continue
            if (key in locked) {
                el.checked = true
                el.disabled = true
                continue
            }
            el.disabled = !el.checked && count >= SHIPPING_HISTORY_MAX_DATA_COLUMNS
        }
    }

    val checkboxesDiv = document.getElementById("shippingHistoryColumnCheckboxes")
    for (key in shippingHistoryAllSelectableColumnKeys()) {
        val row = document.createElement("div")
        row.asDynamic().style.cssText = "display:flex;align-items:center;gap:8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "shippingHistCol_$key"
        input.setAttribute("data-column", key)
        input.checked = key in selected || key in locked
        if (key in locked) input.disabled = true
        input.addEventListener("change", { _: Event -> updateCountAndLocks() })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "shippingHistCol_$key"
        labelEl.textContent = shippingHistoryColumnLabel(key) + if (key in locked) " (always on)" else ""
        labelEl.asDynamic().style.cssText = "cursor:pointer;margin:0;"
        row.appendChild(input)
        row.appendChild(labelEl)
        checkboxesDiv?.appendChild(row)
    }
    updateCountAndLocks()

    document.getElementById("closeShippingHistoryColumnFilter")?.addEventListener("click", { _: Event ->
        closeModal()
    })
    modal.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "shippingHistoryColumnFilterModal") closeModal()
    })
    document.getElementById("resetShippingHistoryColumns")?.addEventListener("click", { _: Event ->
        saveSelectedShippingHistoryColumns(shippingHistoryDefaultColumnKeys())
        closeModal()
        renderShippingHistoryTableFromCache()
    })
    document.getElementById("applyShippingHistoryColumns")?.addEventListener("click", { _: Event ->
        val boxes = document.querySelectorAll("#shippingHistoryColumnCheckboxes input[type=checkbox]")
        val picked = mutableListOf<String>()
        for (i in 0 until boxes.length) {
            val el = boxes.item(i) as? HTMLInputElement ?: continue
            if (el.checked) {
                val key = el.getAttribute("data-column") ?: continue
                picked.add(key)
            }
        }
        saveSelectedShippingHistoryColumns(picked)
        closeModal()
        renderShippingHistoryTableFromCache()
    })

    val escapeHandler: (Event) -> Unit = { event: Event ->
        val keyEvent = event.asDynamic()
        if (keyEvent.key == "Escape") {
            event.preventDefault()
            closeModal()
        }
    }
    shippingHistoryColumnFilterKeyHandler = escapeHandler
    document.addEventListener("keydown", escapeHandler)
    (document.getElementById("closeShippingHistoryColumnFilter") as? HTMLElement)?.focus()
}

fun exportShippingHistoryToExcel() {
    val btn = document.getElementById("exportShippingHistoryExcelBtn") as? HTMLButtonElement
    val originalHtml = btn?.innerHTML
    if (btn != null) {
        btn.disabled = true
        btn.style.opacity = "0.7"
        btn.textContent = "Exporting…"
    }
    MainScope().launch {
        try {
            showMessage("Preparing Excel export…", "info")
            val response = window.fetch(apiUrl("shipping-history/export/xlsx")).await()
            if (!response.ok) {
                val errorText = response.text().await()
                ErrorHandler.showError("Export failed: ${ErrorHandler.extractErrorMessage(errorText)}")
                return@launch
            }
            val blob = response.blob().await()
            val url = js("URL.createObjectURL(blob)") as String
            try {
                val disposition = response.headers.get("Content-Disposition") ?: ""
                val filenameMatch = Regex("filename=\"?([^\";]+)\"?").find(disposition)
                val filename = filenameMatch?.groupValues?.get(1)
                    ?: "shipping_history_export_${js("Date.now()")}.xlsx"
                val a = document.createElement("a") as HTMLAnchorElement
                a.href = url
                a.download = filename
                document.body?.appendChild(a)
                a.click()
                document.body?.removeChild(a)
                showMessage("Shipping history exported successfully", "success")
            } finally {
                js("URL.revokeObjectURL(url)")
            }
        } catch (e: dynamic) {
            ErrorHandler.showError("Export failed: ${e.toString()}")
        } finally {
            if (btn != null) {
                btn.disabled = false
                btn.style.opacity = "1"
                if (originalHtml != null) btn.innerHTML = originalHtml
                else btn.textContent = "Export Excel"
            }
        }
    }
}

fun loadPurchases() {
    if (!routeEquals("/purchase")) return
    Logger.debug("loadPurchases function called (server page)")
    val returnPage = purchaseReturnPageFromEdit
    val page0 = if (returnPage != null && returnPage > 0) (returnPage - 1).coerceAtLeast(0) else 0
    loadPurchasesListPage(page0)
}

/**
 * Unified loader: advanced filters → [POST /purchases/page-filter];
 * empty search → [GET /purchases/page]; non-empty → [GET /purchases/page-search].
 * Always server-paged (never downloads the full catalog for the list UI).
 */
fun loadPurchasesListPage(page0: Int) {
    if (!routeEquals("/purchase")) return
    val input = document.getElementById("purchaseSearchInput") as? HTMLInputElement
    val qFromInput = input?.value?.trim().orEmpty()
    val q = qFromInput.ifEmpty { purchaseSearchQuery.trim() }
    if (q.isNotEmpty() && input != null && input.value.trim().isEmpty()) {
        input.value = q
    }
    if (purchaseAdvancedFilters.isNotEmpty()) {
        loadPurchasesFilterPage(page0)
        return
    }
    if (q.isNotEmpty()) {
        loadPurchasesSearchPage(page0)
        return
    }
    val generation = purchaseListLoadGeneration
    val sortField = purchaseTableSortField
    val order = purchaseTableSortOrderByField[sortField] ?: "desc"
    val encSort = js("encodeURIComponent")(sortField).unsafeCast<String>()
    val encOrder = js("encodeURIComponent")(order).unsafeCast<String>()
    val dateQs = purchaseDateFilterQuerySuffix()
    val endpoint = "purchases/page?page=$page0&size=$itemsPerPage&sort=$encSort&order=$encOrder$dateQs"
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<dynamic>(endpoint)
        result.fold(
            onSuccess = { body ->
                if (generation != purchaseListLoadGeneration) return@fold
                if (!routeEquals("/purchase")) return@fold
                if (body == null) {
                    ErrorHandler.showError("Empty purchase page response")
                    return@fold
                }
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    ErrorHandler.showError(err)
                    return@fold
                }
                purchaseSearchQuery = ""
                val restoring = restoringPurchaseListFromEdit
                restoringPurchaseListFromEdit = false
                displayPurchasesFromSearchPage(body)
                if (restoring) restorePurchaseListFilterUi()
                purchaseReturnPageFromEdit = null
            },
            onError = { message, status ->
                if (generation != purchaseListLoadGeneration) return@fold
                if (!routeEquals("/purchase")) return@fold
                Logger.error("Purchase page API failed: $message (status: $status)")
                ErrorHandler.showError("Failed to load purchases: $message")
            }
        )
    }
}

/** Appends `&dateFrom=&dateTo=` when the purchase-date quick filter is active. */
private fun purchaseDateFilterQuerySuffix(): String {
    if (!purchaseDateFilterActive) return ""
    val from = purchaseDateFilterStartIso.trim()
    val to = purchaseDateFilterEndIso.trim()
    if (from.isEmpty() || to.isEmpty()) return ""
    val encFrom = js("encodeURIComponent")(from).unsafeCast<String>()
    val encTo = js("encodeURIComponent")(to).unsafeCast<String>()
    return "&dateFrom=$encFrom&dateTo=$encTo"
}

/**
 * POST /purchases/page-filter when advanced chips are active (includes search + date range).
 */
private fun loadPurchasesFilterPage(page0: Int) {
    if (!routeEquals("/purchase")) return
    val input = document.getElementById("purchaseSearchInput") as? HTMLInputElement
    val qFromInput = input?.value?.trim().orEmpty()
    val q = qFromInput.ifEmpty { purchaseSearchQuery.trim() }
    if (q.isNotEmpty()) purchaseSearchQuery = q
    val generation = purchaseListLoadGeneration
    val sortField = purchaseTableSortField
    val order = purchaseTableSortOrderByField[sortField] ?: "desc"
    val filters = purchaseAdvancedFilters.map { f ->
        val obj = js("{}")
        obj.field = f.field
        obj.operator = f.operator
        obj.value = f.value
        obj
    }.toTypedArray()
    val body = js("{}")
    body.page = page0
    body.size = itemsPerPage
    body.sort = sortField
    body.order = order
    if (q.isNotEmpty()) {
        body.q = q
        body.field = purchaseSearchFieldChoice
    }
    if (purchaseDateFilterActive &&
        purchaseDateFilterStartIso.isNotEmpty() &&
        purchaseDateFilterEndIso.isNotEmpty()
    ) {
        body.dateFrom = purchaseDateFilterStartIso
        body.dateTo = purchaseDateFilterEndIso
    }
    body.filters = filters
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.post<dynamic>("purchases/page-filter", body)
        result.fold(
            onSuccess = { resp ->
                if (generation != purchaseListLoadGeneration) return@fold
                if (!routeEquals("/purchase")) return@fold
                if (resp == null) {
                    ErrorHandler.showError("Empty filter page response")
                    return@fold
                }
                val err = js("resp.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    ErrorHandler.showError(err)
                    return@fold
                }
                val restoring = restoringPurchaseListFromEdit
                restoringPurchaseListFromEdit = false
                displayPurchasesFromSearchPage(resp)
                if (restoring) restorePurchaseListFilterUi()
                purchaseReturnPageFromEdit = null
            },
            onError = { message, status ->
                if (generation != purchaseListLoadGeneration) return@fold
                if (!routeEquals("/purchase")) return@fold
                Logger.error("Purchase page-filter API failed: $message (status: $status)")
                ErrorHandler.showError("Failed to filter purchases: $message")
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

private fun buildPurchaseAdvCategoryBlock(category: String, labels: Map<String, String>): String {
    val fields = purchaseColumnCategories[category] ?: return ""
    val categoryFields = fields.filter {
        it in labels && it != "vesselNo" && it !in purchaseAdvFilterExcludedFields
    }
    if (categoryFields.isEmpty()) return ""
    val chipsHtml = categoryFields.joinToString("") { field ->
        val active = purchaseFilterSelectedColumns.contains(field)
        val bg = if (active) "#38bdf8" else "#eef2f6"
        val fg = if (active) "#ffffff" else "#374151"
        val label = labels[field] ?: field
        """<button type="button" class="purchase-adv-chip" data-field="$field" style="padding:8px 12px;border-radius:8px;border:none;background:$bg;color:$fg;font-size:12px;cursor:pointer;display:inline-flex;align-items:center;gap:6px;">☐ ${escapeHtml(label)}</button>"""
    }
    return """
            <div style="margin-bottom:16px;">
              <div class="purchase-category-header" data-category="$category" style="font-size:13px;font-weight:600;color:#374151;margin-bottom:8px;cursor:pointer;display:flex;align-items:center;gap:6px;user-select:none;">
                <span style="font-size:16px;">▼</span> $category
              </div>
              <div class="purchase-category-chips" style="display:flex;flex-wrap:wrap;gap:8px;">
                $chipsHtml
              </div>
            </div>
        """
}

/** Value persisted on Apply: ISO day, ISO month, or plain text for non-dates. */
private fun purchaseAdvCanonicalFilterValue(row: HTMLElement, field: String): String {
    val canon = row.querySelector(".purchase-adv-canon") as? HTMLInputElement
    val cv = canon?.value?.trim() ?: ""
    if (field == "carModelYear") {
        if (cv.isNotEmpty()) return cv
        val t = row.querySelector(".purchase-adv-month-text") as? HTMLInputElement
        return strictMmYyyySlashToIsoMonth(t?.value?.trim() ?: "") ?: ""
    }
    if (purchaseAdvFilterFullDayDateFields().contains(field)) {
        if (cv.isNotEmpty()) return cv
        val t = row.querySelector(".purchase-adv-date-text") as? HTMLInputElement
        return strictMmDdYyyySlashToIso(t?.value?.trim() ?: "") ?: ""
    }
    val money = row.querySelector("input.purchase-adv-v1.money-input") as? HTMLInputElement
    if (money != null) return purchaseAdvSanitizeMoney(money.value)
    val commaInt = row.querySelector("input.purchase-adv-v1.comma-int-input") as? HTMLInputElement
    if (commaInt != null) {
        return purchaseAdvSanitizeCommaInt(commaInt.value, field == "distance")
    }
    val plainInt = row.querySelector("input.purchase-adv-v1.plain-int-input") as? HTMLInputElement
    if (plainInt != null) return purchaseAdvSanitizePlainInt(plainInt.value)
    return (row.querySelector(".purchase-adv-v1") as? HTMLInputElement)?.value?.trim() ?: ""
}

private fun showPurchaseAdvancedFilterModal() {
    val panel = document.getElementById("purchaseAdvancedFilterDropdown") as? HTMLElement ?: return
    val isOpen = panel.style.display == "block"
    if (isOpen) {
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
        return
    }
    purchaseAdvPurgeExcludedSelections()
    val labels = purchaseListColumnLabels()

    val leftCol = StringBuilder()
    for (cat in purchaseAdvFilterLeftColumnCategories) {
        leftCol.append(buildPurchaseAdvCategoryBlock(cat, labels))
    }
    val rightCol = StringBuilder()
    for (cat in purchaseAdvFilterRightColumnCategories) {
        rightCol.append(buildPurchaseAdvCategoryBlock(cat, labels))
    }
    val categorizedHtml = """
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:20px 28px;align-items:start;">
          <div>$leftCol</div>
          <div>$rightCol</div>
        </div>
    """

    panel.innerHTML = """
      <div style="display:flex;flex-direction:column;height:100%;max-height:85vh;">
        <div style="padding:16px 18px;flex-shrink:0;">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;margin-bottom:12px;">
            <h3 style="margin:0;font-size:18px;font-weight:700;color:#111827;">🎚️ Advanced Filters</h3>
            <button id="purchaseAdvFilterClose" type="button" style="border:none;background:transparent;font-size:24px;cursor:pointer;color:#6b7280;line-height:1;padding:0;">&times;</button>
          </div>
          <input type="text" id="purchaseAdvFilterSearch" placeholder="🔍 Search columns..." style="width:100%;padding:8px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;box-sizing:border-box;"/>
        </div>
        
        <div id="purchaseAdvCategoriesWrap" style="flex:1;overflow-y:auto;padding:8px 18px 16px;">
          $categorizedHtml
        </div>

        <div id="purchaseAdvValueFilters" class="purchase-adv-value-filters" style="flex:0 0 auto;max-height:320px;overflow-y:auto;padding:8px 18px 12px;"></div>

        <div style="padding:14px 18px;display:flex;justify-content:space-between;align-items:center;gap:10px;flex-shrink:0;">
          <div style="font-size:12px;color:#6b7280;white-space:nowrap;">
            <strong>${purchaseAdvancedFilters.size}</strong> filter(s) active • Showing <strong id="purchaseAdvResultCount">${if (purchaseSearchServerMode) "—" else "0"}</strong> of <strong>${if (purchaseSearchServerMode) "—" else purchaseBaseRows.size.toString()}</strong>
          </div>
          <div style="display:flex;gap:8px;">
            <button id="purchaseAdvFilterClear" type="button" style="padding:9px 14px;border:1px solid #d1d5db;background:#fff;border-radius:8px;cursor:pointer;color:#374151;font-size:13px;transition:all .2s ease;flex:1;">🗑️ Clear</button>
            <button id="purchaseAdvFilterApply" type="button" style="padding:9px 14px;border:none;background:#0ea5e9;color:#fff;border-radius:8px;cursor:pointer;font-size:13px;font-weight:600;transition:all .2s ease;flex:1;">✓ Apply</button>
          </div>
        </div>
      </div>
    """
    panel.style.display = "block"
    (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "true")

    fun renderFilterRows() {
        val selected = purchaseFilterSelectedColumns
            .filter { it !in purchaseAdvFilterExcludedFields }
            .sorted()
        val container = document.getElementById("purchaseAdvValueFilters") as? HTMLElement ?: return
        if (selected.size == 0) {
            container.innerHTML = ""
            container.style.display = "none"
            return
        }
        val html = StringBuilder()
        selected.forEach { field ->
            val label = labels[field] ?: field
            val existing = purchaseAdvancedFilters.find { it.field == field }
            val v = existing?.value ?: ""
            val icon = when {
                purchaseDateFields().contains(field) -> "📅"
                field in listOf("chassis", "blNo", "bookingId") -> "🏷️"
                purchaseAdvMoneyFields().contains(field) -> "💰"
                purchaseAdvCommaIntFields().contains(field) || purchaseAdvPlainIntFields().contains(field) -> "🔢"
                else -> "📝"
            }
            val rowStack = "display:flex;flex-direction:column;gap:8px;padding:10px;border:1px solid #f3f4f6;border-radius:8px;background:#fafbfc;box-sizing:border-box;"
            val baseDateId = "purchaseAdvDt_$field"
            when {
                field == "carModelYear" -> {
                    val isoMonth = purchaseAdvInitialIsoMonth(v)
                    val monthValAttr = if (isoMonth.isNotEmpty()) " value=\"${escapeHtml(isoMonth)}\"" else ""
                    html.append(
                        """
                        <div class="purchase-adv-filter-row" data-field="$field" style="$rowStack">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;">$icon</span>
                            <div style="font-weight:600;color:#111827;font-size:13px;">${escapeHtml(label)}</div>
                          </div>
                          <div style="position:relative;width:100%;">
                            <div style="display:flex;gap:8px;align-items:center;width:100%;box-sizing:border-box;">
                              <input type="text" id="${baseDateId}Text" class="purchase-adv-month-text" maxlength="7" inputmode="numeric" autocomplete="off" placeholder="MM/YYYY"
                                     style="flex:1;min-width:0;padding:8px;border:1px solid #ddd;border-radius:8px;box-sizing:border-box;font-size:13px;font-family:inherit;" />
                              <button type="button" id="${baseDateId}CalendarBtn" class="purchase-adv-calendar-btn" title="Open month picker"
                                      style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:8px;cursor:pointer;">📅</button>
                            </div>
                            <input type="hidden" id="$baseDateId" class="purchase-adv-canon"$monthValAttr />
                          </div>
                        </div>
                        """.trimIndent()
                    )
                }
                purchaseAdvFilterFullDayDateFields().contains(field) -> {
                    val isoDay = purchaseAdvInitialIsoDay(v)
                    val dateValAttr = if (isoDay.isNotEmpty()) " value=\"${escapeHtml(isoDay)}\"" else ""
                    html.append(
                        """
                        <div class="purchase-adv-filter-row" data-field="$field" style="$rowStack">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;">$icon</span>
                            <div style="font-weight:600;color:#111827;font-size:13px;">${escapeHtml(label)}</div>
                          </div>
                          <div style="position:relative;width:100%;">
                            <div style="display:flex;gap:8px;align-items:center;width:100%;box-sizing:border-box;">
                              <input type="text" id="${baseDateId}Text" class="purchase-adv-date-text" maxlength="10" inputmode="numeric" autocomplete="off" placeholder="MM/DD/YYYY"
                                     style="flex:1;min-width:0;padding:8px;border:1px solid #ddd;border-radius:8px;box-sizing:border-box;font-size:13px;font-family:inherit;" />
                              <button type="button" id="${baseDateId}CalendarBtn" class="purchase-adv-calendar-btn" title="Open calendar"
                                      style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:8px;cursor:pointer;">📅</button>
                              <span id="${baseDateId}Hint" style="color:#6b7280;font-size:12px;white-space:nowrap;"></span>
                            </div>
                            <input type="date" id="$baseDateId" class="purchase-adv-canon"$dateValAttr tabindex="-1" aria-hidden="true"
                                   style="position:absolute;opacity:0;border:none;padding:0;margin:0;overflow:hidden;width:0;height:0;" />
                          </div>
                        </div>
                        """.trimIndent()
                    )
                }
                purchaseAdvCommaIntFields().contains(field) -> {
                    val displayVal = purchaseAdvFormatCommaIntDisplay(v, isKm = true)
                    html.append(
                        """
                        <div class="purchase-adv-filter-row" data-field="$field" style="$rowStack">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;">$icon</span>
                            <div style="font-weight:600;color:#111827;font-size:13px;">${escapeHtml(label)}</div>
                          </div>
                          <input class="purchase-adv-v1 comma-int-input km-suffix-input" type="text" value="${escapeHtml(displayVal)}"
                                 inputmode="numeric" autocomplete="off" placeholder=""
                                 style="display:block;width:100%;padding:8px 10px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;font-family:inherit;box-sizing:border-box;" />
                        </div>
                        """.trimIndent()
                    )
                }
                purchaseAdvPlainIntFields().contains(field) -> {
                    val displayVal = purchaseAdvSanitizePlainInt(v)
                    html.append(
                        """
                        <div class="purchase-adv-filter-row" data-field="$field" style="$rowStack">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;">$icon</span>
                            <div style="font-weight:600;color:#111827;font-size:13px;">${escapeHtml(label)}</div>
                          </div>
                          <input class="purchase-adv-v1 plain-int-input" type="text" value="${escapeHtml(displayVal)}"
                                 inputmode="numeric" autocomplete="off" placeholder=""
                                 style="display:block;width:100%;padding:8px 10px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;font-family:inherit;box-sizing:border-box;" />
                        </div>
                        """.trimIndent()
                    )
                }
                purchaseAdvMoneyFields().contains(field) -> {
                    val displayVal = purchaseAdvFormatMoneyDisplay(v)
                    html.append(
                        """
                        <div class="purchase-adv-filter-row" data-field="$field" style="$rowStack">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;">$icon</span>
                            <div style="font-weight:600;color:#111827;font-size:13px;">${escapeHtml(label)}</div>
                          </div>
                          <input class="purchase-adv-v1 money-input" type="text" value="${escapeHtml(displayVal)}"
                                 inputmode="decimal" autocomplete="off" placeholder="0"
                                 style="display:block;width:100%;padding:8px 10px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;font-family:inherit;box-sizing:border-box;" />
                        </div>
                        """.trimIndent()
                    )
                }
                else -> {
                    html.append(
                        """
                        <div class="purchase-adv-filter-row" data-field="$field" style="$rowStack">
                          <div style="display:flex;align-items:center;gap:8px;">
                            <span style="font-size:14px;">$icon</span>
                            <div style="font-weight:600;color:#111827;font-size:13px;">${escapeHtml(label)}</div>
                          </div>
                          <input class="purchase-adv-v1" type="text" value="${escapeHtml(v)}" style="display:block;width:100%;padding:8px 10px;border:1px solid #d1d5db;border-radius:8px;font-size:13px;font-family:inherit;box-sizing:border-box;" placeholder="" />
                        </div>
                        """.trimIndent()
                    )
                }
            }
        }
        container.innerHTML = html.toString()
        container.style.display = ""
        selected.forEach { f ->
            when {
                f == "carModelYear" -> bindStrictMonthYearTextMask("purchaseAdvDt_carModelYear")
                purchaseAdvFilterFullDayDateFields().contains(f) -> bindStrictDateTextMask("purchaseAdvDt_$f", "purchaseAdvDt_${f}Hint")
            }
        }
        val textOnly = container.querySelectorAll(".purchase-adv-v1:not(.money-input):not(.comma-int-input):not(.plain-int-input)")
        for (i in 0 until textOnly.length) {
            val input = textOnly.item(i) as? HTMLInputElement ?: continue
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
            header.getAttribute("data-category") ?: continue
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
            btn.style.setProperty("background", if (active) "#38bdf8" else "#eef2f6")
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
            val v1 = purchaseAdvCanonicalFilterValue(row, field)
            if (v1.isBlank()) continue
            val op = if (purchaseDateFields().contains(field)) "equals" else "contains"
            next.add(PurchaseAdvancedFilter(field = field, operator = op, value = v1))
        }
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
        purchaseAdvancedFilters = next
        updatePurchaseFilterBadge()
        if (purchaseSearchServerMode) {
            loadPurchasesListPage(0)
            showMessage(
                if (purchaseAdvancedFilters.isEmpty()) "Filters cleared" else "${purchaseAdvancedFilters.size} filter(s) applied",
                "success",
            )
        } else {
            refreshPurchaseRowsFromBase(resetPage = true)
            showMessage("${purchaseAdvancedFilters.size} filter(s) applied", "success")
        }
    })
    document.getElementById("purchaseAdvFilterClear")?.addEventListener("click", { _: Event ->
        purchaseAdvancedFilters.clear()
        purchaseFilterSelectedColumns.clear()
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
        updatePurchaseFilterBadge()
        renderFilterRows()
        updatePurchaseFilterResultCount()
        if (purchaseSearchServerMode) {
            loadPurchasesListPage(0)
        } else {
            refreshPurchaseRowsFromBase(resetPage = true)
        }
        showMessage("All filters cleared", "info")
    })
    document.getElementById("purchaseAdvFilterClose")?.addEventListener("click", { _: Event ->
        panel.style.display = "none"
        (document.getElementById("purchaseSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
    })
}

private fun purchasePriceFields(): Set<String> = purchaseAdvMoneyFields()

private fun updatePurchaseFilterResultCount() {
    val el = document.getElementById("purchaseAdvResultCount") ?: return
    if (purchaseSearchServerMode) {
        el.textContent = "—"
        return
    }
    val count = applyPurchaseAdvancedFilters(purchaseBaseRows).size
    el.textContent = count.toString()
}

fun displayPurchasesFromSearchPage(body: dynamic) {
    purchaseSearchServerMode = true
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
    // Current page only — used for edit lookup / view on this page.
    purchaseBaseRows = allPurchases
    displayPurchasesWithPagination()
}

fun loadPurchasesSearchPage(page0: Int) {
    val input = document.getElementById("purchaseSearchInput") as? HTMLInputElement
    val q = input?.value?.trim() ?: ""
    if (q.length == 0) {
        loadPurchasesListPage(page0)
        return
    }
    if (purchaseAdvancedFilters.isNotEmpty()) {
        purchaseSearchQuery = q
        loadPurchasesFilterPage(page0)
        return
    }
    purchaseSearchQuery = q
    val generation = purchaseListLoadGeneration
    val scope = MainScope()
    scope.launch {
        val field = purchaseSearchFieldChoice
        val encQ = js("encodeURIComponent")(q).unsafeCast<String>()
        val encF = js("encodeURIComponent")(field).unsafeCast<String>()
        val sortField = purchaseTableSortField
        val order = purchaseTableSortOrderByField[sortField] ?: "desc"
        val encSort = js("encodeURIComponent")(sortField).unsafeCast<String>()
        val encOrder = js("encodeURIComponent")(order).unsafeCast<String>()
        val dateQs = purchaseDateFilterQuerySuffix()
        val endpoint =
            "purchases/page-search?q=$encQ&field=$encF&page=$page0&size=$itemsPerPage&sort=$encSort&order=$encOrder$dateQs"
        val result = ApiClient.get<dynamic>(endpoint)
        result.fold(
            onSuccess = { body ->
                if (generation != purchaseListLoadGeneration) return@fold
                if (!routeEquals("/purchase")) return@fold
                if (body == null) {
                    ErrorHandler.showError("Empty search response")
                    return@fold
                }
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    ErrorHandler.showError(err)
                    return@fold
                }
                val restoring = restoringPurchaseListFromEdit
                restoringPurchaseListFromEdit = false
                displayPurchasesFromSearchPage(body)
                if (restoring) restorePurchaseListFilterUi()
                purchaseReturnPageFromEdit = null
            },
            onError = { message, status ->
                if (generation != purchaseListLoadGeneration) return@fold
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
    loadPurchasesListPage(0)
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
            val suggestDd = document.getElementById("purchaseSuggestionDropdown") as? HTMLElement
            val insideSuggest = suggestDd != null && suggestDd.contains(target)
            if (!insidePanel && !insideBtn && !insideSuggest) {
                panel.style.display = "none"
                btn?.setAttribute("aria-expanded", "false")
                hidePurchaseSuggestionDropdown()
            }
        })
    }
}

fun displayPurchases(purchases: dynamic) {
    if (!routeEquals("/purchase")) return
    val table = document.getElementById("purchaseTable") ?: return

    purchaseSearchServerMode = false
    purchaseSearchServerTotal = 0
    purchaseSearchServerTotalPages = 0

    val restoringFromEdit = restoringPurchaseListFromEdit
    restoringPurchaseListFromEdit = false

    if (!restoringFromEdit) {
        // New data load => reset any active date filter to avoid stale base arrays.
        purchaseDateFilterActive = false
        purchaseDateFilterStartIso = ""
        purchaseDateFilterEndIso = ""
    }
    
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
    if (restoringFromEdit) {
        if (purchaseDateFilterStartIso.isNotEmpty()) {
            purchaseDateFilterActive = true
        }
        refreshPurchaseRowsFromBase(resetPage = false)
        val returnPage = purchaseReturnPageFromEdit
        if (returnPage != null && returnPage > 0) {
            currentPage = returnPage
            purchaseReturnPageFromEdit = null
        }
        displayPurchasesWithPagination()
        restorePurchaseListFilterUi()
    } else {
        val returnPage = purchaseReturnPageFromEdit
        if (returnPage != null && returnPage > 0) {
            currentPage = returnPage
            purchaseReturnPageFromEdit = null
            refreshPurchaseRowsFromBase(resetPage = false)
        } else {
            refreshPurchaseRowsFromBase(resetPage = true)
        }
    }
}

private data class PurchaseListViewSlice(
    val totalPages: Int,
    val displayFrom: Int,
    val displayTo: Int,
    val totalItems: Int,
    val rows: Array<dynamic>,
)

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
    if (!routeEquals("/purchase")) return
    val table = document.getElementById("purchaseTable") ?: return
    purchaseListLastCompactLayout = purchaseListIsCompactLayout()

    // Cards on compact viewports (≤860); table on larger screens
    if (purchaseListIsCompactLayout()) {
        displayPurchasesAsCards()
        return
    }
    
    if (allPurchases.isEmpty()) {
        // Show table headers even when no data, so users can access filters
        val selectedColumns = getSelectedColumns()
        val sortableFields = purchaseAllSortableFields()
        val columnLabels = purchaseListColumnLabels()
    
    val tableHTML = StringBuilder()
        val emptyColCount = selectedColumns.size
        tableHTML.append("""<div class="purchase-list-table-shell"><table class="purchase-list-table" style="width: 100%; border-collapse: collapse; table-layout: fixed;">${htmlTableColgroupEqualWidth(emptyColCount)}""")
        tableHTML.append("<thead><tr>")
        
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            if (columnKey == "date") {
                tableHTML.append(purchaseDateQuickFilterHeaderCellHtml(label))
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
        val emptyTitle = if (purchaseSearchServerMode) "No matches" else "No purchases yet"
        val emptyDetail = if (purchaseSearchServerMode) {
            "No matching purchases for this search. Try another term or change the field filter (sliders button)."
        } else {
            "Click the menu button (☰) in the top-left corner to add a purchase or import data."
        }
        tableHTML.append("<tbody><tr><td colspan=\"${selectedColumns.size}\" style=\"padding:0;border:none;\">${purchaseListEmptyHtml(emptyTitle, emptyDetail)}</td></tr></tbody>")
        tableHTML.append("</table></div>")
        
        table.innerHTML = tableHTML.toString()
        
        // Purchase Date quick filter trigger lives in the header; menu is portaled on #purchaseList.
        for (columnKey in selectedColumns) {
            if (sortableFields.contains(columnKey)) {
                val sortBtnId = "purchaseSortBtn_$columnKey"
                document.getElementById(sortBtnId)?.addEventListener("click", { _: Event ->
                    togglePurchaseTableSort(columnKey)
                })
            }
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
    val purchaseColCount = 1 + selectedColumns.size
    tableHTML.append("""
        <div class="purchase-list-table-shell">
        <table class="purchase-list-table" style="width: 100%; border-collapse: collapse; table-layout: fixed;">${htmlTableColgroupNarrowActionEqualRest(purchaseColCount, 88)}
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 88px;"></th>
    """)
    
    for (columnKey in selectedColumns) {
        val label = columnLabels[columnKey] ?: columnKey
        if (columnKey == "date") {
            tableHTML.append(purchaseDateQuickFilterHeaderCellHtml(label))
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
        val chassisStr = (purchase.chassis ?: "").toString().trim()
        val chassisAttr = escapeHtml(chassisStr)
        
        tableHTML.append("""
            <tr>
                <td style="padding: 8px 12px;">
                    ${if (isEditor() && purchaseId > 0L && chassisStr.isNotEmpty()) """
                    <div style="display:inline-flex;align-items:center;gap:8px;">
                        ${purchaseListViewButtonHtml(purchaseId, chassisAttr)}
                        <button type="button" class="edit-btn" data-id="${purchaseId}" data-chassis="$chassisAttr" aria-label="Edit" title="Edit"
                                style="display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                            </svg>
                        </button>
                    </div>
                    """ else if (purchaseId > 0L && chassisStr.isNotEmpty()) """
                    ${purchaseListViewButtonHtml(purchaseId, chassisAttr)}
                    """ else if (isEditor()) """
                    <span style="color: #ccc; font-size: 12px;">Invalid ID</span>
                    """ else """
                    """}
                </td>
        """)
        
        for (columnKey in selectedColumns) {
            val cellValue = purchaseListCellValue(purchase, columnKey)
            val raw = cellValue.toString().trim()
            val cellHtml = when {
                raw.isEmpty() -> ""
                columnKey == "date" -> purchaseListDateCellHtml(raw)
                else -> escapeHtml(raw)
            }
            val tdClass = if (columnKey == "date") """ class="purchase-list-date-td"""" else ""
            tableHTML.append("""<td$tdClass style="padding: 12px; vertical-align: top;">$cellHtml</td>""")
        }
        
        tableHTML.append("""</tr>""")
    }
    
    tableHTML.append("""
            </tbody>
        </table>
        </div>
    """)
    
    // Add pagination controls
    val suffix = if (purchaseSearchServerMode && purchaseSearchQuery.isNotBlank()) " (search results)" else ""
    if (purchaseSearchServerMode || totalPages > 1) {
        val prevDisabled = currentPage == 1
        val nextDisabled = currentPage == totalPages
        tableHTML.append("""
            <div class="purchase-list-pager">
                <div style="color: #666;">
                    Showing ${sl.displayFrom} to ${sl.displayTo} of ${sl.totalItems} purchases$suffix
                </div>
                <div class="purchase-list-pager-btns">
                    <button type="button" id="prevPageBtn" class="purchase-list-pager-btn${if (prevDisabled) " is-disabled" else ""}" ${if (prevDisabled) "disabled" else ""}>
                        Previous
                    </button>
                    <span style="padding: 8px 16px; color: #666;">
                        Page $currentPage of $totalPages
                    </span>
                    <button type="button" id="nextPageBtn" class="purchase-list-pager-btn${if (nextDisabled) " is-disabled" else ""}" ${if (nextDisabled) "disabled" else ""}>
                        Next
                    </button>
                </div>
            </div>
        """)
    }
    
    table.innerHTML = tableHTML.toString()
    setupPurchaseEditDelegationOnTable()
    
    // Purchase Date quick filter trigger lives in the header; menu is portaled on document.body.
    setupPurchaseDateQuickFilterMenuPortal()
    for (columnKey in selectedColumns) {
        if (sortableFields.contains(columnKey)) {
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
                loadPurchasesListPage(purchaseSearchServerPageZeroBased - 1)
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
                loadPurchasesListPage(purchaseSearchServerPageZeroBased + 1)
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
        val emptyTitle = if (purchaseSearchServerMode) "No matches" else "No purchases yet"
        val emptyDetail = if (purchaseSearchServerMode) {
            "No matching purchases for this search. Try another term or change the field filter."
        } else {
            "Click the menu button (☰) in the top-left corner to add a purchase or import data."
        }
        table.innerHTML = purchaseListEmptyHtml(emptyTitle, emptyDetail)
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
            val value = purchaseListCellValue(purchase, columnKey)
            
            // Convert to string and check if not blank (safe for JS strings)
            val valueStr = value.toString()
            val isValueNotEmpty = valueStr.length > 0 && valueStr.trim().length > 0
            
            if (isValueNotEmpty) {
                val cellDisplay = if (columnKey == "date") {
                    purchaseListDateCellHtml(valueStr.trim())
                } else {
                    escapeHtml(valueStr.trim())
                }
                val valueClass = if (columnKey == "date") "card-value purchase-list-date-td" else "card-value"
                cardFields.append("""
                    <div class="card-field">
                        <span class="card-label">$label:</span>
                        <div class="$valueClass">$cellDisplay</div>
                    </div>
                """)
            }
        }
        
        val chassisStr = chassis.toString().trim()
        val hasChassis = chassisStr.isNotEmpty()
        val chassisAttr = escapeHtml(chassisStr)
        
        cardsHTML.append("""
            <div class="purchase-card">
                <div class="card-header">
                    ${if (purchaseId > 0L && hasChassis) """
                    <div style="display:inline-flex;align-items:center;gap:8px;flex-shrink:0;">
                        ${purchaseListViewButtonHtml(purchaseId, chassisAttr, "card-view-btn")}
                        ${if (isEditor()) """
                        <button type="button" class="card-edit-btn" data-id="${purchaseId}" data-chassis="$chassisAttr" aria-label="Edit" title="Edit">
                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                            </svg>
                        </button>
                        """ else ""}
                    </div>
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
    
    val suffixM = if (purchaseSearchServerMode && purchaseSearchQuery.isNotBlank()) " (search)" else ""
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
    setupPurchaseEditDelegationOnTable()
    
    // Setup pagination event listeners
    document.getElementById("prevPageBtn")?.addEventListener("click", { _: Event ->
        if (purchaseSearchServerMode) {
            if (purchaseSearchServerPageZeroBased > 0) {
                loadPurchasesListPage(purchaseSearchServerPageZeroBased - 1)
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
                loadPurchasesListPage(purchaseSearchServerPageZeroBased + 1)
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
                    navigateToPurchaseList(forceClearFilters = true)
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
        val max = getMaxPurchaseListColumnsForDevice(deviceType)
        return ensurePurchaseListPinnedColumns(prioritizePurchaseListDateAndChassis(defaultColumns), max)
    }
    
    // Filter out removed/hidden columns and migrate legacy aliases (sold→invoiceConfirmed, vesselNo→vessel)
    val validColumns = sanitizePurchaseListSelectedColumns(savedColumns)
    if (validColumns != savedColumns) {
        safeLocalStorageSet("selectedColumns", JSON.stringify(validColumns.toTypedArray()))
    }
    
    // Auto-adjust if saved columns exceed device limit, then pin date/chassis to the left
    val max = getMaxPurchaseListColumnsForDevice(deviceType)
    return ensurePurchaseListPinnedColumns(
        prioritizePurchaseListDateAndChassis(autoAdjustColumnsForDevice(validColumns, deviceType)),
        max,
    )
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
    closeColumnFilterModal()

    val returnFocus = document.activeElement as? HTMLElement
    
    val modal = document.createElement("div")
    modal.id = "columnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; inset: 0; 
        background-color: rgba(15,23,42,0.45); z-index: 10000; 
        display: flex; align-items: center; justify-content: center; padding: 16px;
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
        <div role="dialog" aria-modal="true" aria-labelledby="columnFilterTitle"
             style="background: white; border-radius: 12px; padding: 24px; max-width: 500px; width: 100%; max-height: 80vh; overflow-y: auto; box-shadow: 0 20px 50px rgba(0,0,0,0.2);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 id="columnFilterTitle" style="margin: 0; color: #0f172a; flex: 1; font-size: 18px; font-weight: 700;">Select Columns to Display</h3>
                <button type="button" id="closeColumnFilter" aria-label="Close" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8fafc; border-radius: 8px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName — Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Purchase Date and Chassis are always on. Selected total: <span id="selectedCount">0</span></span>
            </div>
            <div id="columnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end; flex-wrap: wrap;">
                <button type="button" id="resetColumns" class="btn-secondary" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; min-height: 40px;">Reset to Default</button>
                <button type="button" id="applyColumns" class="btn-primary" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; min-height: 40px;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)

    fun closeModal() {
        closeColumnFilterModal()
        returnFocus?.focus()
    }
    
    // Populate column checkboxes (using function from MinimalPurchaseApp.kt)
    populateColumnCheckboxes(selectedColumns)
    
    // Update selection count initially (this will set the correct format)
    updateColumnSelection()
    
    // Add event listeners
    document.getElementById("closeColumnFilter")?.addEventListener("click", { _: Event ->
        closeModal()
    })
    document.getElementById("resetColumns")?.addEventListener("click", { _: Event ->
        resetToDefaultColumns()
    })
    document.getElementById("applyColumns")?.addEventListener("click", { _: Event ->
        applyColumnChanges()
        returnFocus?.focus()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "columnFilterModal") {
            closeModal()
        }
    })

    val escapeHandler: (Event) -> Unit = { event: Event ->
        val keyEvent = event.asDynamic()
        if (keyEvent.key == "Escape") {
            event.preventDefault()
            closeModal()
        }
    }
    columnFilterModalKeyHandler = escapeHandler
    document.addEventListener("keydown", escapeHandler)

    (document.getElementById("closeColumnFilter") as? HTMLElement)?.focus()
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

