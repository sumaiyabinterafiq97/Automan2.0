package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import kotlin.js.JSON
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private data class SlmAdvancedFilter(
    val field: String,
    val value: String = "",
)

private var slmBaseRows: List<dynamic> = emptyList()
private var slmSearchQuery: String = ""
private val slmFilterSelectedColumns: MutableSet<String> = mutableSetOf()
private var slmAdvancedFilters: List<SlmAdvancedFilter> = emptyList()
private var slmAdvancedPanelOpen: Boolean = false
private var slmSortField: String? = null
private val slmSortOrderByField: MutableMap<String, String> = mutableMapOf()
private var slmPage: Int = 1
private val slmPageSize: Int = AppConstants.DEFAULT_ITEMS_PER_PAGE
private var slmSearchDebounceTimer: dynamic = null
private var slmLastDeviceType: String? = getDeviceType()
private var slmDocumentClickBound: Boolean = false

private val slmColumns = listOf("stockLocation", "pol", "address")

private val slmColumnLabels = mapOf(
    "stockLocation" to "Stock location",
    "pol" to "POL",
    "address" to "Address",
)

private fun slmParseId(raw: dynamic): Long? =
    (raw as? Number)?.toLong() ?: raw?.toString()?.toLongOrNull()

private fun slmCellText(row: dynamic, key: String): String =
    when (key) {
        "stockLocation" -> (row.stockLocation ?: "").toString()
        "pol" -> (row.pol ?: "").toString()
        "address" -> (row.address ?: "").toString()
        else -> ""
    }

private fun slmMatchesFilter(row: dynamic, f: SlmAdvancedFilter): Boolean {
    val q = f.value.trim().lowercase()
    if (q.isEmpty()) return true
    return slmCellText(row, f.field).lowercase().contains(q)
}

private fun applySlmAdvancedFilters(rows: List<dynamic>): List<dynamic> {
    if (slmAdvancedFilters.isEmpty()) return rows
    return rows.filter { r -> slmAdvancedFilters.all { f -> slmMatchesFilter(r, f) } }
}

private fun applySlmTextSearch(rows: List<dynamic>): List<dynamic> {
    val q = slmSearchQuery.trim().lowercase()
    if (q.isEmpty()) return rows
    val keys = if (slmFilterSelectedColumns.isNotEmpty()) slmFilterSelectedColumns.toList() else slmColumns
    return rows.filter { r -> keys.any { k -> slmCellText(r, k).lowercase().contains(q) } }
}

private fun applySlmSorting(rows: List<dynamic>): List<dynamic> {
    val sf = slmSortField ?: return rows
    if (sf !in slmColumns) return rows
    val ord = slmSortOrderByField[sf] ?: "desc"
    return if (ord == "asc") {
        rows.sortedBy { slmCellText(it, sf).trim().lowercase() }
    } else {
        rows.sortedByDescending { slmCellText(it, sf).trim().lowercase() }
    }
}

private fun getProcessedSlmRows(): List<dynamic> {
    val filtered = applySlmAdvancedFilters(slmBaseRows)
    val searched = applySlmTextSearch(filtered)
    return applySlmSorting(searched)
}

private fun toggleSlmSort(field: String) {
    val cur = slmSortOrderByField[field] ?: "desc"
    slmSortOrderByField[field] = if (cur == "asc") "desc" else "asc"
    slmSortField = field
    slmPage = 1
    renderSlmFromState()
}

private fun slmSortTooltip(field: String): String {
    val ord = slmSortOrderByField[field] ?: "desc"
    return if (ord == "asc") "Sorted A-Z (click to sort Z-A)" else "Sorted Z-A (click to sort A-Z)"
}

private fun slmDefaultColumns(): List<String> = slmColumns.toList()

private fun getSelectedSlmColumns(): List<String> {
    val saved = safeLocalStorageGet("selectedSlmColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (_: dynamic) {
            null
        }
    } else {
        null
    }
    if (savedColumns == null || savedColumns.isEmpty()) return slmDefaultColumns()
    val filtered = savedColumns.filter { it in slmColumns }
    return if (filtered.isEmpty()) slmDefaultColumns() else filtered
}

fun showStockLocationMapPage() {
    val content = document.getElementById("content") ?: return
    content.innerHTML = """
        <div id="slmRoot" style="border:1px solid #ddd;border-radius:4px;padding:20px;max-width:1400px;margin:0 auto;width:100%;box-sizing:border-box;">
            <style>
                #slmRoot .slm-header-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;gap:12px;}
                #slmRoot .slm-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
                #slmRoot table.purchase-list-table thead th{position:sticky;top:0;z-index:1;background:#f9fafb;}
                #slmRoot .slm-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
                #slmRoot .slm-empty strong{color:#0f172a;}
                #slmRoot .slm-address{white-space:normal;word-break:break-word;overflow-wrap:anywhere;}
                #slmRoot .slm-cards{display:flex;flex-direction:column;gap:12px;}
                #slmRoot .slm-card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:16px;box-shadow:0 2px 4px rgba(0,0,0,0.08);}
                @media (max-width:767px){
                    #slmRoot{padding:14px;}
                    #slmRoot .slm-header-row{flex-direction:column;align-items:stretch;}
                    #slmRoot #slmColumnFilterBtn,#slmRoot #slmAddBtn{width:100%;justify-content:center;}
                }
            </style>
            <div class="slm-header-row">
                <h2 style="margin:0;color:#111827;font-size:28px;font-weight:700;">Stock Location Map</h2>
                <button type="button" id="slmColumnFilterBtn" title="Column filter" style="padding:8px 16px;background-color:#6c757d;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:14px;display:inline-flex;align-items:center;gap:6px;flex-shrink:0;">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/></svg>
                    Column Filter
                </button>
            </div>
            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:20px;margin-bottom:20px;">
                <div style="display:flex;align-items:center;gap:10px;width:100%;min-width:0;">
                    <div style="position:relative;flex:1;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);">
                        <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);color:#9ca3af;display:flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="text" id="slmSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Type to search…" aria-label="Search stock location map" style="width:100%;box-sizing:border-box;padding:11px 38px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;" />
                        <button type="button" id="slmSearchClearBtn" title="Clear search" aria-label="Clear search" style="position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:4px 8px;min-height:36px;min-width:36px;">×</button>
                    </div>
                    <button type="button" id="slmSearchFilterBtn" title="Advanced Filters" aria-label="Open filter columns panel." style="width:48px;height:48px;border-radius:50%;border:1px solid #e5e7eb;background:#f3f4f6;box-shadow:0 1px 3px rgba(0,0,0,0.06);cursor:pointer;display:flex;align-items:center;justify-content:center;color:#4b5563;padding:0;flex-shrink:0;">
                        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                            <line x1="3" y1="7" x2="21" y2="7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                            <circle cx="8" cy="7" r="2.25" fill="currentColor"/>
                            <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                            <circle cx="16" cy="12" r="2.25" fill="currentColor"/>
                            <line x1="3" y1="17" x2="21" y2="17" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                            <circle cx="7" cy="17" r="2.25" fill="currentColor"/>
                        </svg>
                    </button>
                </div>
                <div id="slmAdvancedFilterDropdown" style="display:none;border:1px solid #e5e7eb;border-radius:12px;background:#fff;padding:14px;margin-top:12px;"></div>
            </div>
            <div style="margin-bottom:20px;">
                <button type="button" id="slmAddBtn" style="padding:12px 24px;background-color:#059669;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px;font-weight:600;box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                    + Add Stock Location
                </button>
            </div>
            <div id="slmTable">
                <div class="slm-empty"><strong>Loading</strong><div>Loading stock location map…</div></div>
            </div>
        </div>
    """.trimIndent()

    (document.getElementById("slmSearchInput") as? HTMLInputElement)?.value = slmSearchQuery
    renderSlmAdvancedFilterDropdown()
    loadStockLocationMapTable()

    document.getElementById("slmAddBtn")?.addEventListener("click", { _: Event -> showStockLocationMapModal(null) })
    document.getElementById("slmSearchInput")?.addEventListener("input", { _: Event ->
        slmSearchQuery = (document.getElementById("slmSearchInput") as? HTMLInputElement)?.value ?: ""
        scheduleSlmSearchDebounced()
    })
    document.getElementById("slmSearchClearBtn")?.addEventListener("click", { _: Event ->
        slmSearchQuery = ""
        (document.getElementById("slmSearchInput") as? HTMLInputElement)?.value = ""
        slmPage = 1
        renderSlmFromState()
    })
    document.getElementById("slmSearchFilterBtn")?.addEventListener("click", { ev: Event ->
        ev.stopPropagation()
        slmAdvancedPanelOpen = !slmAdvancedPanelOpen
        renderSlmAdvancedFilterDropdown()
    })
    document.getElementById("slmColumnFilterBtn")?.addEventListener("click", { _: Event -> showSlmColumnFilterModal() })
    if (!slmDocumentClickBound) {
        slmDocumentClickBound = true
        document.addEventListener("click", { ev: Event ->
            val t = ev.target as? Node ?: return@addEventListener
            val panel = document.getElementById("slmAdvancedFilterDropdown")
            val btn = document.getElementById("slmSearchFilterBtn")
            if (panel != null && btn != null && !panel.contains(t) && !btn.contains(t)) {
                if (slmAdvancedPanelOpen) {
                    slmAdvancedPanelOpen = false
                    renderSlmAdvancedFilterDropdown()
                }
            }
        })
    }
    setupSlmResizeListener()
}

private fun scheduleSlmSearchDebounced() {
    if (slmSearchDebounceTimer != null) {
        window.clearTimeout(slmSearchDebounceTimer.unsafeCast<Int>())
        slmSearchDebounceTimer = null
    }
    slmSearchDebounceTimer = window.setTimeout({
        slmSearchDebounceTimer = null
        slmPage = 1
        renderSlmFromState()
    }, 420)
}

private fun setupSlmResizeListener() {
    val existing = window.asDynamic().__slmDeviceChangeListener
    if (existing != null) {
        window.removeEventListener("resize", existing.unsafeCast<((Event) -> Unit)?>())
    }
    var resizeTimeout: dynamic = null
    val listener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) window.clearTimeout(resizeTimeout)
        resizeTimeout = window.setTimeout({
            val next = getDeviceType()
            if (slmLastDeviceType != null && slmLastDeviceType != next && routeStartsWith("/master/stock-location-map")) {
                renderSlmFromState()
            }
            slmLastDeviceType = next
        }, 300)
    }
    window.asDynamic().__slmDeviceChangeListener = listener
    window.addEventListener("resize", listener)
}

private fun loadStockLocationMapTable() {
    val tableDiv = document.getElementById("slmTable") ?: return
    tableDiv.innerHTML = """<div class="slm-empty"><strong>Loading</strong><div>Loading stock location map…</div></div>"""
    window.fetch(apiUrl("stock-location-map/mappings"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to load stock location map')") }
        .then { body: dynamic ->
            val raw = js("(function(b){ var d = b && b.data; return Array.isArray(d) ? d : []; })")(body)
            slmBaseRows = (raw as Array<dynamic>).toList()
            renderSlmFromState()
        }
        .catch { e: dynamic ->
            tableDiv.innerHTML = """<div class="slm-empty"><strong>Error</strong><div>${escapeHtml(e.message?.toString() ?: "Failed to load")}</div></div>"""
        }
}

private fun renderSlmFromState() {
    if (getDeviceType() == "mobile") {
        renderSlmCards()
    } else {
        renderSlmTable()
    }
}

private fun slmPageSlice(rows: List<dynamic>): Pair<List<dynamic>, Int> {
    val totalPages = max(1, ceil(rows.size.toDouble() / slmPageSize).toInt())
    if (slmPage > totalPages) slmPage = totalPages
    if (slmPage < 1) slmPage = 1
    val start = (slmPage - 1) * slmPageSize
    val end = min(start + slmPageSize, rows.size)
    val pageRows = if (start >= rows.size) emptyList() else rows.subList(start, end)
    return pageRows to totalPages
}

private fun slmPagerHtml(totalPages: Int, totalRows: Int, pageCount: Int, filterLabel: Boolean): String {
    val start = if (totalRows == 0) 0 else (slmPage - 1) * slmPageSize + 1
    val end = (slmPage - 1) * slmPageSize + pageCount
    val filteredNote = if (filterLabel) " (filtered)" else ""
    val summary = if (totalRows == 0) {
        "No rows$filteredNote"
    } else {
        "Showing $start to $end of $totalRows$filteredNote"
    }
    if (totalPages <= 1) {
        return """<div class="consignee-map-pager" style="display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:10px;padding:14px 4px 4px;color:#475569;font-size:14px;"><span>$summary</span></div>"""
    }
    val prevDisabled = if (slmPage <= 1) " disabled" else ""
    val nextDisabled = if (slmPage >= totalPages) " disabled" else ""
    return """
        <div class="consignee-map-pager" style="display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:10px;padding:14px 4px 4px;color:#475569;font-size:14px;">
            <span style="flex:1;min-width:200px;">$summary</span>
            <div style="display:flex;align-items:center;gap:10px;">
                <button type="button" id="slmPrevPage" class="consignee-pagination-btn"$prevDisabled>Prev</button>
                <span>Page $slmPage of $totalPages</span>
                <button type="button" id="slmNextPage" class="consignee-pagination-btn"$nextDisabled>Next</button>
            </div>
        </div>
    """.trimIndent()
}

private fun bindSlmPager(totalPages: Int) {
    document.getElementById("slmPrevPage")?.addEventListener("click", { _: Event ->
        if (slmPage > 1) {
            slmPage--
            renderSlmFromState()
        }
    })
    document.getElementById("slmNextPage")?.addEventListener("click", { _: Event ->
        if (slmPage < totalPages) {
            slmPage++
            renderSlmFromState()
        }
    })
}

private fun slmHasActiveFilter(): Boolean =
    slmSearchQuery.trim().isNotEmpty() || slmAdvancedFilters.isNotEmpty()

private fun slmCellInner(key: String, value: String): String =
    when (key) {
        "pol" -> formatConsigneeMapValueChipHtml(value)
        "address" -> formatConsigneeMapAddressChipHtml(value)
        else -> formatConsigneeMapValueChipHtml(value)
    }

private fun renderSlmTable() {
    val tableDiv = document.getElementById("slmTable") ?: return
    val rows = getProcessedSlmRows()
    if (rows.isEmpty()) {
        val msg = if (slmHasActiveFilter()) "No matches for your search." else "No stock location map data found yet."
        tableDiv.innerHTML = """<div class="slm-empty"><strong>No results</strong><div>$msg</div></div>"""
        return
    }
    val (pageRows, totalPages) = slmPageSlice(rows)
    val selectedColumns = getSelectedSlmColumns()
    val colCount = 1 + selectedColumns.size
    var html = """
        <div class="slm-table-shell">
            <table class="purchase-list-table" style="width:100%;border-collapse:collapse;table-layout:fixed;">${htmlTableColgroupNarrowActionEqualRest(colCount)}
                <thead><tr>
                    <th style="padding:12px 14px;text-align:left;min-width:88px;"></th>
    """.trimIndent()
    for (ck in selectedColumns) {
        val label = slmColumnLabels[ck] ?: ck
        val tip = slmSortTooltip(ck)
        html += """<th><button type="button" id="slmSort_$ck" title="${escapeHtml(tip)}" style="background:none;border:none;cursor:pointer;font-weight:700;color:#111827;padding:0;display:inline-flex;align-items:center;gap:6px;"><span>${escapeHtml(label)}</span><span style="font-size:14px;">&#x2195;</span></button></th>"""
    }
    html += "</tr></thead><tbody>"
    for (row in pageRows) {
        val id = (row.id ?: "").toString()
        html += """
            <tr>
                <td style="padding:10px 12px;vertical-align:middle;">
                    <div style="display:flex;gap:6px;align-items:center;">
                        <button onclick="window.editMasterStockLocationMap($id)" aria-label="Edit" title="Edit"
                                style="width:36px;height:36px;min-width:36px;min-height:36px;display:inline-flex;align-items:center;justify-content:center;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 6px rgba(76,201,255,0.30);">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
                        </button>
                    </div>
                </td>
        """.trimIndent()
        for (ck in selectedColumns) {
            val value = slmCellText(row, ck)
            val extra = if (ck == "address") " class=\"slm-address\"" else ""
            html += """<td$extra style="padding:12px 16px;color:#111827;font-size:14px;vertical-align:top;border:1px solid #e5e7eb;background:#fff;">${slmCellInner(ck, value)}</td>"""
        }
        html += "</tr>"
    }
    html += "</tbody></table></div>"
    html += slmPagerHtml(totalPages, rows.size, pageRows.size, slmHasActiveFilter())
    tableDiv.innerHTML = html
    bindSlmPager(totalPages)
    for (ck in selectedColumns) {
        document.getElementById("slmSort_$ck")?.addEventListener("click", { _: Event -> toggleSlmSort(ck) })
    }
}

private fun renderSlmCards() {
    val tableDiv = document.getElementById("slmTable") ?: return
    val rows = getProcessedSlmRows()
    if (rows.isEmpty()) {
        val msg = if (slmHasActiveFilter()) "No matches for your search." else "No stock location map data found yet."
        tableDiv.innerHTML = """<div class="slm-empty"><strong>No results</strong><div>$msg</div></div>"""
        return
    }
    val (pageRows, totalPages) = slmPageSlice(rows)
    val selectedColumns = getSelectedSlmColumns()
    val sb = StringBuilder()
    sb.append("""<div class="slm-cards">""")
    for (row in pageRows) {
        val id = (row.id ?: "").toString()
        val title = slmCellText(row, "stockLocation").ifBlank { "Stock #$id" }
        sb.append("""<div class="slm-card"><div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:12px;padding-bottom:12px;border-bottom:1px solid #f0f0f0;">""")
        sb.append("""<div style="font-size:16px;font-weight:600;color:#111827;min-width:0;word-break:break-word;">${escapeHtml(title)}</div>""")
        sb.append("""<button type="button" onclick="window.editMasterStockLocationMap($id)" aria-label="Edit" title="Edit" style="width:28px;height:28px;display:inline-flex;align-items:center;justify-content:center;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;flex-shrink:0;"><svg width="12" height="12" viewBox="0 0 24 24" fill="none"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg></button></div>""")
        for (ck in selectedColumns) {
            if (ck == "stockLocation") continue
            val label = slmColumnLabels[ck] ?: ck
            val value = slmCellText(row, ck)
            sb.append("""<div style="margin-bottom:10px;"><div style="font-weight:600;color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.04em;margin-bottom:6px;">${escapeHtml(label)}</div><div>${slmCellInner(ck, value)}</div></div>""")
        }
        sb.append("</div>")
    }
    sb.append("</div>")
    sb.append(slmPagerHtml(totalPages, rows.size, pageRows.size, slmHasActiveFilter()))
    tableDiv.innerHTML = sb.toString()
    bindSlmPager(totalPages)
}

private fun renderSlmAdvancedFilterDropdown() {
    val panel = document.getElementById("slmAdvancedFilterDropdown") ?: return
    panel.asDynamic().style.display = if (slmAdvancedPanelOpen) "block" else "none"
    if (!slmAdvancedPanelOpen) return
    panel.addEventListener("click", { ev: Event -> ev.stopPropagation() })

    val chips = slmColumns.joinToString("") { key ->
        val active = slmFilterSelectedColumns.contains(key)
        val bg = if (active) "#e0f2fe" else "#f3f4f6"
        val color = if (active) "#0369a1" else "#374151"
        val cross = if (active) {
            """<span id="slmFilterChipRemove_$key" data-chip-remove="$key" style="display:inline-flex;align-items:center;justify-content:center;width:14px;height:14px;border-radius:50%;background:#bae6fd;color:#075985;font-size:11px;font-weight:700;margin-right:6px;line-height:1;">×</span>"""
        } else {
            ""
        }
        """<button type="button" id="slmFilterChip_$key" style="border:none;border-radius:9999px;padding:6px 10px;margin:0 6px 8px 0;cursor:pointer;background:$bg;color:$color;font-size:12px;display:inline-flex;align-items:center;">$cross<span>${slmColumnLabels[key] ?: key}</span></button>"""
    }
    val rowsHtml = slmFilterSelectedColumns.joinToString("") { key ->
        val current = slmAdvancedFilters.find { it.field == key }
        val v1 = escapeHtml(current?.value ?: "")
        """
        <div id="slmFilterRow_$key" style="display:grid;grid-template-columns:180px 1fr;gap:8px;margin-bottom:8px;">
            <div style="font-size:13px;color:#111827;padding-top:8px;">${slmColumnLabels[key] ?: key}</div>
            <input id="slmFilterValue_$key" type="text" value="$v1" placeholder="Contains value..." style="padding:8px;border:1px solid #d1d5db;border-radius:6px;">
        </div>
        """
    }
    panel.innerHTML = """
        <div style="font-weight:700;color:#111827;margin-bottom:6px;">Advanced Filters</div>
        <div style="font-size:11px;color:#6b7280;margin-bottom:8px;">CHOOSE FILTER COLUMNS</div>
        <div style="margin-bottom:8px;">$chips</div>
        <div>$rowsHtml</div>
        <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:10px;">
            <button type="button" id="slmFilterClearAll" style="padding:8px 12px;border:none;border-radius:6px;background:#f3f4f6;color:#374151;cursor:pointer;">Clear all</button>
            <button type="button" id="slmFilterApplyBtn" style="padding:8px 12px;border:none;border-radius:6px;background:#0ea5e9;color:white;cursor:pointer;">Apply filter</button>
        </div>
    """.trimIndent()

    slmColumns.forEach { key ->
        document.getElementById("slmFilterChip_$key")?.addEventListener("click", { ev: Event ->
            val target = ev.target as? HTMLElement
            val removeKey = target?.getAttribute("data-chip-remove")
            if (removeKey != null && removeKey.isNotEmpty()) {
                slmFilterSelectedColumns.remove(removeKey)
                slmAdvancedFilters = slmAdvancedFilters.filterNot { it.field == removeKey }
                slmPage = 1
                renderSlmAdvancedFilterDropdown()
                renderSlmFromState()
                return@addEventListener
            }
            if (slmFilterSelectedColumns.contains(key)) slmFilterSelectedColumns.remove(key) else slmFilterSelectedColumns.add(key)
            renderSlmAdvancedFilterDropdown()
        })
    }
    document.getElementById("slmFilterClearAll")?.addEventListener("click", { _: Event ->
        slmFilterSelectedColumns.clear()
        slmAdvancedFilters = emptyList()
        slmPage = 1
        renderSlmAdvancedFilterDropdown()
        renderSlmFromState()
    })
    document.getElementById("slmFilterApplyBtn")?.addEventListener("click", { _: Event ->
        val out = mutableListOf<SlmAdvancedFilter>()
        slmFilterSelectedColumns.forEach { key ->
            val v1 = (document.getElementById("slmFilterValue_$key") as? HTMLInputElement)?.value ?: ""
            if (v1.trim().isNotEmpty()) out.add(SlmAdvancedFilter(field = key, value = v1))
        }
        slmAdvancedFilters = out
        slmPage = 1
        renderSlmFromState()
    })
}

private fun showSlmColumnFilterModal() {
    document.getElementById("slmColumnFilterModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "slmColumnFilterModal"
    modal.asDynamic().style.cssText =
        "position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:10000;display:flex;align-items:center;justify-content:center;"
    val selected = getSelectedSlmColumns().toSet()
    modal.innerHTML = """
        <div style="background:white;border-radius:8px;padding:24px;max-width:520px;width:90%;max-height:80vh;overflow-y:auto;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
                <h3 style="margin:0;">Select Columns to Display</h3>
                <button type="button" id="closeSlmColumnFilter" style="background:none;border:none;font-size:28px;cursor:pointer;">&times;</button>
            </div>
            <div id="slmColumnCheckboxes" style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:16px;"></div>
            <div style="display:flex;gap:10px;justify-content:flex-end;">
                <button type="button" id="resetSlmColumns" style="padding:8px 16px;background:#6c757d;color:white;border:none;border-radius:4px;cursor:pointer;">Reset to Default</button>
                <button type="button" id="applySlmColumns" style="padding:8px 16px;background:#007bff;color:white;border:none;border-radius:4px;cursor:pointer;">Apply Changes</button>
            </div>
        </div>
    """.trimIndent()
    document.body?.appendChild(modal)
    val box = document.getElementById("slmColumnCheckboxes")
    slmColumnLabels.forEach { (key, label) ->
        val wrap = document.createElement("div")
        wrap.asDynamic().style.display = "flex"
        wrap.asDynamic().style.alignItems = "center"
        wrap.asDynamic().style.gap = "8px"
        val inp = document.createElement("input") as HTMLInputElement
        inp.type = "checkbox"
        inp.id = "slmCol_$key"
        inp.setAttribute("data-column", key)
        inp.checked = selected.contains(key)
        val lab = document.createElement("label") as HTMLLabelElement
        lab.htmlFor = "slmCol_$key"
        lab.textContent = label
        wrap.appendChild(inp)
        wrap.appendChild(lab)
        box?.appendChild(wrap)
    }
    document.getElementById("closeSlmColumnFilter")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("resetSlmColumns")?.addEventListener("click", { _: Event ->
        slmColumns.forEach { k ->
            (document.getElementById("slmCol_$k") as? HTMLInputElement)?.checked = true
        }
    })
    document.getElementById("applySlmColumns")?.addEventListener("click", { _: Event ->
        val checks = document.querySelectorAll("#slmColumnCheckboxes input[type='checkbox']")
        val out = mutableListOf<String>()
        for (i in 0 until checks.length) {
            val c = checks.item(i) as HTMLInputElement
            if (c.checked) {
                val k = c.getAttribute("data-column") ?: ""
                if (k.isNotEmpty()) out.add(k)
            }
        }
        safeLocalStorageSet("selectedSlmColumns", JSON.stringify(out.toTypedArray()))
        modal.remove()
        renderSlmFromState()
    })
    modal.addEventListener("click", { ev: Event ->
        if ((ev.target as? HTMLElement)?.id == "slmColumnFilterModal") modal.remove()
    })
}

fun showStockLocationMapModal(mappingId: Long?) {
    val isEdit = mappingId != null
    val title = if (isEdit) "Edit Stock Location" else "Add Stock Location"
    val modalHtml = """
        <div id="slmModal" style="position:fixed;top:0;left:0;width:100%;height:100%;background-color:rgba(0,0,0,0.5);z-index:10000;display:flex;align-items:center;justify-content:center;">
            <div style="background:white;border-radius:12px;width:90%;max-width:700px;max-height:90vh;overflow-y:auto;box-shadow:0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding:24px;border-bottom:1px solid #e5e7eb;">
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <h2 style="margin:0;font-size:24px;font-weight:700;color:#111827;">$title</h2>
                        <button type="button" id="closeSlmModal" style="background:none;border:none;font-size:24px;color:#6b7280;cursor:pointer;padding:0;width:32px;height:32px;display:flex;align-items:center;justify-content:center;border-radius:6px;">×</button>
                    </div>
                </div>
                <div style="padding:24px;">
                    <form id="slmForm">
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:8px;font-weight:600;color:#374151;font-size:14px;">Stock location <span style="color:#ef4444;">*</span></label>
                            ${createEditableCombobox("slmStockLocation", "Select stock location", required = true)}
                        </div>
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:8px;font-weight:600;color:#374151;font-size:14px;">POL</label>
                            ${createChipMultiSelectCombobox("slmPol", "Select or type POL", allowAnyTypedToken = true)}
                        </div>
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:8px;font-weight:600;color:#374151;font-size:14px;">Address</label>
                            <textarea id="slmAddress" rows="4" style="width:100%;padding:10px 12px;border:1px solid #d1d5db;border-radius:6px;font-size:14px;box-sizing:border-box;resize:vertical;font-family:inherit;white-space:pre-wrap;word-break:break-word;"></textarea>
                        </div>
                        <div style="display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;">
                            <button type="button" id="cancelSlmBtn" style="padding:10px 16px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;cursor:pointer;font-size:14px;color:#374151;">Cancel</button>
                            ${if (isEdit) """<button type="button" id="deleteSlmBtn" style="padding:10px 16px;border:none;border-radius:8px;background:#b91c1c;color:#fff;cursor:pointer;font-weight:700;font-size:14px;">Delete</button>""" else ""}
                            <button type="submit" id="saveSlmBtn" style="padding:10px 16px;border:none;border-radius:8px;background:#059669;color:#fff;cursor:pointer;font-weight:700;font-size:14px;">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """.trimIndent()
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    ensureSupplierChipJs()
    populateEditableComboboxFromMasterMenu("slmStockLocation", "stock_location")
    populateEditableComboboxFromMasterMenu("slmPol", "pol")
    js("if (typeof window.wireAddPurchaseComboboxKeyboardNav === 'function') window.wireAddPurchaseComboboxKeyboardNav();")
    if (isEdit && mappingId != null) loadStockLocationMapForEdit(mappingId)

    document.getElementById("closeSlmModal")?.addEventListener("click", { _: Event -> closeStockLocationMapModal() })
    document.getElementById("cancelSlmBtn")?.addEventListener("click", { _: Event -> closeStockLocationMapModal() })
    if (isEdit && mappingId != null) {
        document.getElementById("deleteSlmBtn")?.addEventListener("click", { _: Event -> deleteMasterStockLocationMap(mappingId) })
    }
    document.getElementById("slmForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveStockLocationMap(mappingId)
    })
    document.getElementById("slmModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "slmModal") closeStockLocationMapModal()
    })
}

private fun loadStockLocationMapForEdit(mappingId: Long) {
    val row = slmBaseRows.find { slmParseId(it.id) == mappingId }
    if (row == null) {
        showMessage("Stock location map row not found", "error")
        return
    }
    val stock = slmCellText(row, "stockLocation")
    val pol = slmCellText(row, "pol")
    val address = slmCellText(row, "address")
    window.setTimeout({
        setEditableComboboxValue("slmStockLocation", stock)
        setChipFieldValue("slmPol", normalizeStoredListForChips(pol))
        (document.getElementById("slmAddress") as? HTMLTextAreaElement)?.value = address
    }, 450)
}

fun closeStockLocationMapModal() {
    document.getElementById("slmModal")?.remove()
}

private fun saveStockLocationMap(mappingId: Long?) {
    val stock = getEditableComboboxValue("slmStockLocation").trim()
    if (stock.isEmpty()) {
        showMessage("Stock location is required", "error")
        return
    }
    val pol = getChipFieldValue("slmPol")
    val address = (document.getElementById("slmAddress") as? HTMLTextAreaElement)?.value ?: ""
    val saveButton = document.getElementById("saveSlmBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = if (mappingId != null) "Updating..." else "Saving..."

    val payload = js("{}")
    payload.stockLocation = stock
    payload.pol = pol
    payload.address = address

    val url = if (mappingId != null) {
        apiUrl("stock-location-map/mappings/$mappingId")
    } else {
        apiUrl("stock-location-map/mappings/add")
    }
    val requestInit = js("{}")
    requestInit.method = if (mappingId != null) "PUT" else "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(payload)

    window.fetch(url, requestInit)
        .then { response: dynamic -> response.json() }
        .then { result: dynamic ->
            val ok = js("(function(j){ return !!(j && j.success); })")(result).unsafeCast<Boolean>()
            val fallback = if (mappingId != null) "Stock location map updated" else "Stock location map added"
            val msg = js("(function(j,d){ if(!j) return d; var m=j.message||j.error; return (m==null||m===undefined||String(m).trim()==='')?d:String(m); })")(
                result,
                if (ok) fallback else "Failed to save",
            ).unsafeCast<String>()
            if (ok) {
                showMessage(msg, "success")
                closeStockLocationMapModal()
                loadStockLocationMapTable()
            } else {
                showMessage(msg, "error")
            }
        }
        .catch { error: dynamic ->
            showMessage(error.message?.toString() ?: "Failed to save", "error")
        }
        .finally {
            saveButton?.disabled = false
            saveButton?.textContent = if (mappingId != null) "Update" else "Save"
        }
}

fun editMasterStockLocationMap(id: dynamic) {
    val mappingId = slmParseId(id)
    if (mappingId != null) showStockLocationMapModal(mappingId)
}

fun deleteMasterStockLocationMap(id: dynamic) {
    val mappingId = slmParseId(id) ?: run {
        showMessage("Invalid stock location map ID", "error")
        return
    }
    showRixoMappingDeleteConfirm(
        "Delete stock location",
        "Delete this stock location map row? This does not change Booking, Supplier Map, or Master Set lists.",
    ) {
        val requestInit = js("{}")
        requestInit.method = "DELETE"
        window.fetch(apiUrl("stock-location-map/mappings/$mappingId"), requestInit)
            .then { response: dynamic -> response.json() }
            .then { result: dynamic ->
                val ok = js("(function(j){ return !!(j && j.success); })")(result).unsafeCast<Boolean>()
                val msg = js("(function(j,d){ if(!j) return d; var m=j.message||j.error; return (m==null||m===undefined||String(m).trim()==='')?d:String(m); })")(
                    result,
                    if (ok) "Deleted" else "Failed to delete",
                ).unsafeCast<String>()
                if (ok) {
                    showMessage(msg, "success")
                    closeStockLocationMapModal()
                    loadStockLocationMapTable()
                } else {
                    showMessage(msg, "error")
                }
            }
            .catch { error: dynamic ->
                showMessage(error.message?.toString() ?: "Failed to delete", "error")
            }
    }
}
