package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

private const val PORTAL_PURCHASE_PAGE_SIZE = 20
private const val PORTAL_DATA_COLUMN_MAX = 7

private class PortalPurchaseRoutes(
    val columnsKey: String,
    val listPath: String,
    val detailApi: (Long) -> String,
    val detailRoute: (Long) -> String,
    val backRoute: String,
    val mediaPath: (Long, String) -> String,
)

private val clientPurchaseRoutes = PortalPurchaseRoutes(
    columnsKey = "portalClientPurchaseColumns",
    listPath = "portal/client/cars/list",
    detailApi = { id -> "portal/client/cars/$id" },
    detailRoute = { id -> "/portal/client/cars/$id" },
    backRoute = "/portal/client/cars",
    mediaPath = { purchaseId, mediaId -> "portal/client/cars/$purchaseId/media/$mediaId/url" },
)

private val consigneePurchaseRoutes = PortalPurchaseRoutes(
    columnsKey = "portalConsigneePurchaseColumns",
    listPath = "portal/consignee/purchases",
    detailApi = { id -> "portal/consignee/purchases/$id" },
    detailRoute = { id -> "/portal/consignee/purchased/$id" },
    backRoute = "/portal/consignee/purchased",
    mediaPath = { purchaseId, mediaId -> "portal/consignee/cars/$purchaseId/media/$mediaId/url" },
)

private var portalPurchaseRoutes = clientPurchaseRoutes
private var portalPurchaseItems: List<dynamic> = emptyList()
private var portalPurchaseQuery: String = ""
private var portalPurchaseSortField: String = "date"
private var portalPurchaseSortOrder: String = "desc"
private var portalPurchasePage: Int = 1

private fun usePortalPurchaseRoutes(routes: PortalPurchaseRoutes) {
    if (portalPurchaseRoutes.columnsKey != routes.columnsKey) {
        portalPurchaseQuery = ""
        portalPurchasePage = 1
        portalPurchaseSortField = "date"
        portalPurchaseSortOrder = "desc"
    }
    portalPurchaseRoutes = routes
}

internal suspend fun renderClientPurchaseList(generation: Int) {
    renderPortalPurchaseList(generation, clientPurchaseRoutes)
}

internal suspend fun renderConsigneePurchaseList(generation: Int) {
    renderPortalPurchaseList(generation, consigneePurchaseRoutes)
}

private suspend fun renderPortalPurchaseList(generation: Int, routes: PortalPurchaseRoutes) {
    usePortalPurchaseRoutes(routes)
    when (val result = ApiClient.request<dynamic>(routes.listPath)) {
        is ApiResult.Error -> renderPortalFailure(generation, result.message, result.statusCode)
        is ApiResult.Success -> {
            portalPurchaseItems = jsArray(result.data)
            drawClientPurchaseList(generation)
        }
    }
}

internal suspend fun renderClientPurchaseDetail(generation: Int, purchaseId: Long) {
    renderPortalPurchaseDetail(generation, purchaseId, clientPurchaseRoutes)
}

internal suspend fun renderConsigneePurchaseDetail(generation: Int, purchaseId: Long) {
    renderPortalPurchaseDetail(generation, purchaseId, consigneePurchaseRoutes)
}

private suspend fun renderPortalPurchaseDetail(generation: Int, purchaseId: Long, routes: PortalPurchaseRoutes) {
    usePortalPurchaseRoutes(routes)
    val detail = ApiClient.request<dynamic>(routes.detailApi(purchaseId))
    if (detail is ApiResult.Error) {
        renderPortalFailure(generation, detail.message, detail.statusCode)
        return
    }
    val purchase = (detail as ApiResult.Success).data.purchase
    val mediaIds = jsArray((detail as ApiResult.Success).data.mediaIds).mapNotNull { (it as? Number)?.toLong() }
    setPortalBody(generation, clientPurchaseDetailHtml(purchase, mediaIds))
    document.getElementById("portalPurchaseBack")?.addEventListener("click", { _: Event ->
        navigateToApp(routes.backRoute)
    })
    loadPortalThumbs(generation)
}

private fun drawClientPurchaseList(generation: Int) {
    val columns = portalClientSelectedColumns()
    val filtered = portalPurchaseItems.filter { portalPurchaseMatches(it, portalPurchaseQuery, columns) }
    val purchases = filtered.map { it.purchase }.toTypedArray()
    val sorted = sortPurchasesInMemory(purchases, portalPurchaseSortField, portalPurchaseSortOrder)
    val pages = kotlin.math.max(1, kotlin.math.ceil(sorted.size.toDouble() / PORTAL_PURCHASE_PAGE_SIZE).toInt())
    if (portalPurchasePage > pages) portalPurchasePage = pages
    if (portalPurchasePage < 1) portalPurchasePage = 1
    val start = (portalPurchasePage - 1) * PORTAL_PURCHASE_PAGE_SIZE
    val pageRows = sorted.drop(start).take(PORTAL_PURCHASE_PAGE_SIZE)
    val mediaById = portalPurchaseItems.associate { item ->
        val id = (item.purchase.id as? Number)?.toLong() ?: 0L
        id to ((item.mediaId as? Number)?.toLong() ?: 0L)
    }
    setPortalBody(generation, clientPurchaseListHtml(columns, pageRows, mediaById, pages))
    bindClientPurchaseList(generation, columns)
    loadPortalThumbs(generation)
}

private fun bindClientPurchaseList(generation: Int, columns: List<String>) {
    document.getElementById("portalPurchaseSearch")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        portalPurchaseQuery = (document.getElementById("portalPurchaseQuery") as? HTMLInputElement)?.value?.trim().orEmpty()
        portalPurchasePage = 1
        drawClientPurchaseList(generation)
    })
    document.getElementById("portalPurchaseSearchClear")?.addEventListener("click", { _: Event ->
        portalPurchaseQuery = ""
        portalPurchasePage = 1
        drawClientPurchaseList(generation)
    })
    document.getElementById("portalPurchaseColumnsBtn")?.addEventListener("click", { _: Event ->
        openPortalColumnModal(generation, columns)
    })
    eachElement(".portal-purchase-sort") { button ->
        button.addEventListener("click", { _: Event ->
            val field = button.getAttribute("data-sort") ?: return@addEventListener
            portalPurchaseSortOrder = if (portalPurchaseSortField == field && portalPurchaseSortOrder == "asc") "desc" else "asc"
            portalPurchaseSortField = field
            portalPurchasePage = 1
            drawClientPurchaseList(generation)
        })
    }
    eachElement(".portal-purchase-view-btn") { button ->
        button.addEventListener("click", { event: Event ->
            event.preventDefault()
            val id = button.getAttribute("data-id")?.toLongOrNull() ?: return@addEventListener
            navigateToApp(portalPurchaseRoutes.detailRoute(id))
        })
    }
    document.getElementById("portalPurchasePrev")?.addEventListener("click", { _: Event ->
        if (portalPurchasePage > 1) {
            portalPurchasePage -= 1
            drawClientPurchaseList(generation)
        }
    })
    document.getElementById("portalPurchaseNext")?.addEventListener("click", { _: Event ->
        portalPurchasePage += 1
        drawClientPurchaseList(generation)
    })
}

private fun clientPurchaseListHtml(
    columns: List<String>,
    rows: List<dynamic>,
    mediaById: Map<Long, Long>,
    pages: Int,
): String {
    val labels = purchaseListColumnLabels()
    val minWidth = purchaseListTableMinWidthPx(columns, labels, includeAction = true) + 118
    val colgroup = htmlTableColgroupPurchaseList(columns, labels, includeAction = true).replaceFirst(
        """<col style="width:${PURCHASE_LIST_ACTION_COL_PX}px">""",
        """<col style="width:${PURCHASE_LIST_ACTION_COL_PX}px"><col style="width:118px">""",
    )
    val heads = columns.joinToString("") { key ->
        val label = escapeHtml(labels[key] ?: key)
        val mark = if (portalPurchaseSortField == key) {
            val arrow = if (portalPurchaseSortOrder == "asc") "↑" else "↓"
            """<span class="portal-sort-mark"> $arrow</span>"""
        } else {
            """<span class="portal-sort-mark portal-sort-idle"> ↕</span>"""
        }
        """<th><button type="button" class="portal-purchase-sort" data-sort="$key">$label$mark</button></th>"""
    }
    val body = if (rows.isEmpty()) {
        """<tr><td colspan="${columns.size + 2}" class="portal-purchase-empty">No purchased cars match this search.</td></tr>"""
    } else {
        rows.joinToString("") { purchase ->
            val id = (purchase.id as? Number)?.toLong() ?: 0L
            val photo = portalCarThumbHtml(id, mediaById[id] ?: 0L)
            val cells = columns.joinToString("") { key ->
                val raw = purchaseTableCellValue(purchase, key).trim()
                val html = if (raw.isEmpty()) """<span class="portal-missing">Not recorded</span>""" else escapeHtml(raw)
                val emphasis = when (key) {
                    "chassis" -> " portal-cell-chassis"
                    "carName" -> " portal-cell-name"
                    else -> ""
                }
                """<td class="$emphasis" title="${escapeHtml(raw)}">$html</td>"""
            }
            """
                <tr>
                    <td><button type="button" class="portal-purchase-view-btn" data-id="$id" aria-label="View purchase" title="View purchase">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="2"/></svg>
                    </button></td>
                    <td>$photo</td>
                    $cells
                </tr>
            """.trimIndent()
        }
    }
    val pager = if (pages <= 1) {
        ""
    } else {
        """
            <div class="portal-purchase-pager">
                <button id="portalPurchasePrev" type="button" ${if (portalPurchasePage <= 1) "disabled" else ""}>Previous</button>
                <button id="portalPurchaseNext" type="button" ${if (portalPurchasePage >= pages) "disabled" else ""}>Next</button>
            </div>
        """.trimIndent()
    }
    return """
        <section id="portalClientPurchases">
            <style>
                #portalClientPurchases .portal-purchase-toolbar{display:flex;gap:10px;align-items:center;margin-bottom:12px;}
                #portalClientPurchases .portal-purchase-search{position:relative;flex:1;min-width:0;display:flex;align-items:center;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.06);}
                #portalClientPurchases .portal-purchase-search input{width:100%;box-sizing:border-box;height:44px;border:0;background:transparent;border-radius:999px;padding:0 40px 0 16px;font-size:14px;outline:none;}
                #portalClientPurchases .portal-purchase-search button{position:absolute;right:6px;border:0;background:transparent;color:#9ca3af;font-size:20px;cursor:pointer;width:32px;height:32px;}
                #portalClientPurchases .portal-column-btn{width:44px;height:44px;border-radius:50%;border:1px solid #e5e7eb;background:#f3f4f6;cursor:pointer;color:#4b5563;flex:0 0 auto;}
                #portalClientPurchases .purchase-list-table-shell{overflow-x:auto;border-radius:12px;background:#fff;border:1px solid #eef2f7;}
                #portalClientPurchases table.purchase-list-table{width:max-content;min-width:100%;table-layout:auto;border-collapse:collapse;background:#fff;font-size:14px;}
                #portalClientPurchases table.purchase-list-table th,#portalClientPurchases table.purchase-list-table td{padding:11px 14px;border-bottom:1px solid #e5e7eb;text-align:left;white-space:nowrap;vertical-align:middle;}
                #portalClientPurchases table.purchase-list-table tbody tr:hover{background:#f8fafc;}
                #portalClientPurchases table.purchase-list-table thead th{position:sticky;top:0;background:#f1f5f9;color:#334155;font-size:11.5px;letter-spacing:.04em;text-transform:uppercase;}
                #portalClientPurchases .portal-purchase-sort{background:none;border:0;padding:0;font:inherit;font-weight:700;color:#0f172a;cursor:pointer;text-align:left;}
                #portalClientPurchases .portal-sort-idle{color:#94a3b8;font-weight:500;}
                #portalClientPurchases .portal-purchase-view-btn{display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;padding:0;background:#fff;border:1px solid #d1d5db;border-radius:6px;cursor:pointer;color:#374151;}
                #portalClientPurchases .portal-purchase-view-btn:hover{background:#f8fafc;border-color:#94a3b8;color:#1f2937;}
                #portalClientPurchases .portal-purchase-view-btn:focus-visible{outline:2px solid #2c3e50;outline-offset:2px;}
                #portalClientPurchases .portal-car-thumb-btn{display:inline-flex;padding:0;border:0;background:transparent;cursor:pointer;border-radius:6px;}
                #portalClientPurchases .portal-car-thumb-btn:focus-visible{outline:2px solid #2c3e50;outline-offset:2px;}
                #portalClientPurchases .portal-car-thumb{display:flex;align-items:center;justify-content:center;width:56px;height:40px;object-fit:cover;border-radius:6px;background:#f8fafc;border:1px solid #e2e8f0;}
                #portalClientPurchases .portal-cell-chassis{font-weight:600;font-variant-numeric:tabular-nums;}
                #portalClientPurchases .portal-cell-name{font-weight:600;color:#1f2937;}
                #portalClientPurchases .portal-purchase-empty{padding:28px 12px;text-align:center;color:#64748b;}
                #portalClientPurchases .portal-purchase-pager{display:flex;justify-content:flex-end;align-items:center;gap:8px;margin-top:12px;}
                #portalClientPurchases .portal-purchase-pager button{min-height:36px;padding:0 14px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;cursor:pointer;}
                #portalClientPurchases .portal-purchase-pager button:disabled{opacity:.45;cursor:default;}
            </style>
            <form id="portalPurchaseSearch" class="portal-purchase-toolbar">
                <div class="portal-purchase-search">
                    <input id="portalPurchaseQuery" type="search" value="${escapeHtml(portalPurchaseQuery)}" placeholder="Search by chassis, brand, supplier…" aria-label="Search purchases">
                    <button id="portalPurchaseSearchClear" type="button" aria-label="Clear search">×</button>
                </div>
                <button id="portalPurchaseColumnsBtn" class="portal-column-btn" type="button" aria-label="Choose columns" title="Columns">☰</button>
            </form>
            <div id="portalPurchaseTable">
                <div class="purchase-list-table-shell">
                    <table class="purchase-list-table" style="width:100%;min-width:${minWidth}px;border-collapse:collapse;">
                        $colgroup
                        <thead><tr><th></th><th>Car Picture</th>$heads</tr></thead>
                        <tbody>$body</tbody>
                    </table>
                </div>
            </div>
            $pager
        </section>
    """.trimIndent()
}

private fun portalPurchaseMatches(item: dynamic, query: String, columns: List<String>): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    val purchase = item.purchase
    val keys = (columns + listOf("chassis", "brand", "carName", "auctionHouse")).distinct()
    return keys.any { purchaseTableCellValue(purchase, it).lowercase().contains(q) }
}

private fun portalClientDefaultColumns(): List<String> =
    getDefaultColumnsForDevice().take(PORTAL_DATA_COLUMN_MAX)

private fun portalClientSelectedColumns(): List<String> {
    val saved = window.localStorage.getItem(portalPurchaseRoutes.columnsKey)
    val parsed = if (saved.isNullOrBlank()) {
        emptyList()
    } else {
        try {
            sanitizePurchaseListSelectedColumns(JSON.parse<Array<String>>(saved).toList())
        } catch (_: dynamic) {
            emptyList()
        }
    }
    val base = if (parsed.isEmpty()) portalClientDefaultColumns() else parsed
    return base.distinct().take(PORTAL_DATA_COLUMN_MAX)
}

private fun openPortalColumnModal(generation: Int, selected: List<String>) {
    document.getElementById("portalColumnModal")?.remove()
    val labels = purchaseListSelectableColumnLabels()
    val boxes = labels.entries.joinToString("") { (key, label) ->
        val checked = if (key in selected) " checked" else ""
        """<label style="display:flex;gap:8px;align-items:center;padding:4px 0;"><input type="checkbox" value="$key"$checked> ${escapeHtml(label)}</label>"""
    }
    val modal = document.createElement("div")
    modal.id = "portalColumnModal"
    modal.innerHTML = """
        <div style="position:fixed;inset:0;background:rgba(15,23,42,.45);z-index:10000;display:flex;align-items:center;justify-content:center;padding:16px;">
            <div style="background:#fff;border-radius:12px;max-width:520px;width:100%;max-height:80vh;overflow:auto;padding:16px;">
                <h2 style="margin:0 0 8px;font-size:18px;">Columns</h2>
                <p style="margin:0 0 12px;color:#64748b;font-size:13px;">Car Picture is always shown. Choose up to $PORTAL_DATA_COLUMN_MAX more columns.</p>
                <div id="portalColumnChoices">$boxes</div>
                <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:12px;">
                    <button type="button" id="portalColumnCancel">Cancel</button>
                    <button type="button" id="portalColumnApply">Apply</button>
                </div>
            </div>
        </div>
    """.trimIndent()
    document.body?.appendChild(modal)
    syncPortalColumnLimit()
    eachElement("#portalColumnChoices input") { input ->
        input.addEventListener("change", { _: Event -> syncPortalColumnLimit() })
    }
    document.getElementById("portalColumnCancel")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("portalColumnApply")?.addEventListener("click", { _: Event ->
        val chosen = mutableListOf<String>()
        val inputs = document.querySelectorAll("#portalColumnChoices input")
        val count = (inputs.length as Number).toInt()
        for (index in 0 until count) {
            val input = inputs.item(index) as? HTMLInputElement ?: continue
            if (input.checked) chosen.add(input.value)
        }
        val next = if (chosen.isEmpty()) portalClientDefaultColumns() else chosen.take(PORTAL_DATA_COLUMN_MAX)
        window.localStorage.setItem(portalPurchaseRoutes.columnsKey, JSON.stringify(next.toTypedArray()))
        modal.remove()
        portalPurchasePage = 1
        drawClientPurchaseList(generation)
    })
}

private fun syncPortalColumnLimit() {
    val inputs = document.querySelectorAll("#portalColumnChoices input")
    val count = (inputs.length as Number).toInt()
    var checked = 0
    for (index in 0 until count) {
        if ((inputs.item(index) as? HTMLInputElement)?.checked == true) checked += 1
    }
    val full = checked >= PORTAL_DATA_COLUMN_MAX
    for (index in 0 until count) {
        val input = inputs.item(index) as? HTMLInputElement ?: continue
        input.disabled = full && !input.checked
    }
}

private fun portalCarThumbHtml(purchaseId: Long, mediaId: Long, wide: Boolean = false): String {
    if (mediaId <= 0L || purchaseId <= 0L) {
        return """<span class="portal-car-thumb portal-car-thumb-empty" aria-hidden="true"><svg width="22" height="16" viewBox="0 0 22 16" fill="none"><path d="M3 11.5h16M4.2 11.5 6 7.2h10l1.8 4.3M7 11.5v1.2M15 11.5v1.2" stroke="#94a3b8" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg></span>"""
    }
    val size = if (wide) """ style="width:120px;height:80px;"""" else ""
    return """<button type="button" class="portal-car-thumb-btn" data-portal-purchase="$purchaseId" data-portal-media="$mediaId" aria-label="Show car picture"><img class="portal-car-thumb" alt="Car picture"$size></button>"""
}

private fun loadPortalThumbs(generation: Int) {
    eachElement(".portal-car-thumb-btn[data-portal-media]") { button ->
        val purchaseId = button.getAttribute("data-portal-purchase") ?: return@eachElement
        val mediaId = button.getAttribute("data-portal-media") ?: return@eachElement
        val image = button.querySelector("img") ?: return@eachElement
        button.addEventListener("click", { event: Event ->
            event.preventDefault()
            val src = image.getAttribute("src").orEmpty()
            if (button.getAttribute("data-ready") == "1" && src.isNotBlank()) {
                openPortalCarPicture(src)
            }
        })
        launchPortal {
            when (val result = ApiClient.request<dynamic>(portalPurchaseRoutes.mediaPath(purchaseId.toLong(), mediaId))) {
                is ApiResult.Success -> {
                    if (generation != currentPortalGeneration()) return@launchPortal
                    val url = jsText(result.data.url)
                    if (url.isBlank()) return@launchPortal
                    image.setAttribute("src", url)
                    button.setAttribute("data-ready", "1")
                }
                is ApiResult.Error -> Unit
            }
        }
    }
}

private fun openPortalCarPicture(src: String) {
    document.getElementById("portalCarPictureViewer")?.remove()
    val viewer = document.createElement("div")
    viewer.id = "portalCarPictureViewer"
    viewer.innerHTML = """
        <div id="portalCarPictureBackdrop" style="position:fixed;inset:0;z-index:10001;display:flex;align-items:center;justify-content:center;padding:24px;background:rgba(15,23,42,.72);">
            <button id="portalCarPictureClose" type="button" aria-label="Close picture" style="position:absolute;top:16px;right:16px;width:40px;height:40px;border:0;border-radius:999px;background:#fff;font-size:24px;cursor:pointer;">×</button>
            <img alt="Car picture" src="${escapeHtml(src)}" style="max-width:min(960px,100%);max-height:calc(100vh - 48px);object-fit:contain;border-radius:8px;background:#fff;">
        </div>
    """.trimIndent()
    document.body?.appendChild(viewer)
    val close = { _: Event -> viewer.remove() }
    document.getElementById("portalCarPictureClose")?.addEventListener("click", close)
    document.getElementById("portalCarPictureBackdrop")?.addEventListener("click", { event: Event ->
        if (event.target == event.currentTarget) viewer.remove()
    })
}

private fun eachElement(selector: String, block: (HTMLElement) -> Unit) {
    val nodes = document.querySelectorAll(selector)
    val count = (nodes.length as Number).toInt()
    for (index in 0 until count) {
        val element = nodes.item(index) as? HTMLElement ?: continue
        block(element)
    }
}

private fun clientPurchaseDetailHtml(purchase: dynamic, mediaIds: List<Long>): String {
    val id = (purchase.id as? Number)?.toLong() ?: 0L
    val photos = if (mediaIds.isEmpty()) {
        """<span class="portal-car-thumb portal-car-thumb-empty" aria-hidden="true"></span>"""
    } else {
        mediaIds.joinToString("") { mediaId -> portalCarThumbHtml(id, mediaId, wide = true) }
    }
    val sections = portalReadonlySections().joinToString("") { (title, fields) ->
        val grid = fields.joinToString("") { (key, label) -> portalReadonlyField(purchase, key, label) }
        """<section class="portal-readonly-section"><h3>$title</h3><div class="portal-readonly-grid">$grid</div></section>"""
    }
    return """
        <section id="portalClientPurchaseDetail">
            <style>
                #portalClientPurchaseDetail .portal-readonly-bar{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px;}
                #portalClientPurchaseDetail .portal-readonly-bar button{height:36px;padding:0 14px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;cursor:pointer;}
                #portalClientPurchaseDetail .portal-readonly-section{background:#fff;border:1px solid #e5e7eb;border-radius:12px;padding:14px 16px;margin-bottom:12px;}
                #portalClientPurchaseDetail h3{margin:0 0 12px;font-size:16px;color:#2c3e50;}
                #portalClientPurchaseDetail .portal-readonly-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px;}
                #portalClientPurchaseDetail label{display:block;font-size:12px;font-weight:600;color:#64748b;margin-bottom:4px;}
                #portalClientPurchaseDetail input,#portalClientPurchaseDetail textarea{width:100%;box-sizing:border-box;border:1px solid #e5e7eb;border-radius:6px;background:#f8fafc;color:#111827;padding:8px 10px;}
                #portalClientPurchaseDetail .portal-readonly-photos{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:12px;}
                #portalClientPurchaseDetail .portal-car-thumb-btn{display:inline-flex;padding:0;border:0;background:transparent;cursor:pointer;}
                #portalClientPurchaseDetail .portal-car-thumb{width:120px;height:80px;object-fit:cover;border-radius:6px;background:#f1f5f9;border:1px solid #e2e8f0;}
            </style>
            <div class="portal-readonly-bar">
                <button id="portalPurchaseBack" type="button">Back to purchased cars</button>
                <span class="portal-missing">View only</span>
            </div>
            <div class="portal-readonly-photos">$photos</div>
            $sections
        </section>
    """.trimIndent()
}

private fun portalReadonlyField(purchase: dynamic, key: String, label: String): String {
    val raw = jsText(purchase[key])
    val checkKeys = setOf("shaken", "negotiate", "local", "bookingRequested", "invoiceConfirmed")
    if (key in checkKeys) {
        val checked = if (raw == "true" || raw == "1") " checked" else ""
        return """<div><label><input type="checkbox" disabled$checked> ${escapeHtml(label)}</label></div>"""
    }
    if (key == "wd" || key == "driveType") {
        return """<div><label>${escapeHtml(label)}</label><input type="text" value="${escapeHtml(raw)}" disabled readonly></div>"""
    }
    if (key == "notes") {
        return """<div style="grid-column:1/-1;"><label>${escapeHtml(label)}</label><textarea rows="3" disabled readonly>${escapeHtml(raw)}</textarea></div>"""
    }
    val shown = if (key == "beforeTax" || key == "afterTax") portalCostText(purchase, key) else raw
    return """<div><label>${escapeHtml(label)}</label><input type="text" value="${escapeHtml(shown)}" disabled readonly></div>"""
}

private fun portalCostText(purchase: dynamic, key: String): String {
    val parts = listOf(
        "price", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "freight", "shipmentCharges",
        "storageCharges", "repairCharges", "miscCharges", "inspectionFee", "commission", "rixoPrice",
    )
    val before = parts.sumOf { portalMoney(jsText(purchase[it])) }
    val tax = portalMoney(jsText(purchase.taxTotal))
    val amount = if (key == "afterTax") before + tax else before
    return "¥" + amount.toLong().toString()
}

private fun portalMoney(raw: String): Double {
    val cleaned = raw.replace(Regex("[^0-9.]"), "")
    return cleaned.toDoubleOrNull() ?: 0.0
}

private fun portalReadonlySections(): List<Pair<String, List<Pair<String, String>>>> = listOf(
    "Basic Information" to listOf(
        "date" to "Purchase Date",
        "chassis" to "Chassis",
        "brand" to "Brand",
        "carName" to "Car Name",
        "grade" to "Grade",
        "carModelYear" to "Registration Date",
        "manufactureYear" to "Manufacture Year",
        "auctionNo" to "Auction No",
    ),
    "Number Cut Information" to listOf(
        "shaken" to "SHAKEN",
        "numberCut" to "Number Cut",
    ),
    "Car Specifications" to listOf(
        "rank" to "Rank",
        "color" to "Color",
        "fuel" to "Fuel",
        "seat" to "Seat",
        "door" to "Door",
        "distance" to "Mileage",
        "cc" to "CC",
        "shift" to "Shift",
        "wd" to "WD",
        "driveType" to "Drive Type",
        "shipmentSize" to "Vehicle type",
        "options" to "Options",
    ),
    "Supplier Information" to listOf(
        "auctionHouse" to "Supplier Name",
        "venueId" to "Venue ID",
    ),
    "Rixo Information" to listOf(
        "rixoCompany" to "Rixo Company",
        "stockLocation" to "Stock Location",
        "rixoPrice" to "Rixo Price",
        "rixoRequested" to "Rixo Requested",
        "rixoConfirmed" to "Rixo Confirmed",
    ),
    "Status" to listOf(
        "bookingRequested" to "Booking Requested",
        "invoiceConfirmed" to "Sold",
        "local" to "LOCAL",
        "negotiate" to "NEGOTIATE",
    ),
    "Client Information" to listOf(
        "clientName" to "Client Name",
        "country" to "Country",
    ),
    "Shipment Information" to listOf(
        "pod" to "POD",
        "shipmentDate" to "Shipment Date",
        "blNo" to "B/L No.",
        "bookingId" to "Booking No",
        "vessel" to "Vessel",
        "shipmentCharges" to "Shipment Charges",
        "consignee" to "Consignee",
    ),
    "Pricing Information" to listOf(
        "price" to "Car Price",
        "auctionFee" to "Auction Fees",
        "recycleFee" to "Recycle Fees",
        "roadTax" to "Road Tax",
        "auctionPenaltyFee" to "Auction Penalty Fee",
        "paymentDate" to "Payment Date",
        "freight" to "Freight",
        "storageCharges" to "Storage Fees",
        "repairCompany" to "Repair Company",
        "repairCharges" to "Repair Charges",
        "miscCharges" to "Misc. Charges",
        "inspectionFee" to "Inspection Fees",
        "commission" to "Commission",
        "beforeTax" to "Total Cost (Before Tax)",
        "taxTotal" to "Tax Total",
        "afterTax" to "Total Cost (After Tax 10%)",
        "profit" to "Profit",
        "notes" to "Notes",
    ),
)
