package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.w3c.dom.events.Event
import kotlin.js.JSON
import kotlin.js.unsafeCast
import kotlin.math.max

private data class ScmGroupRow(
    val stockLocation: String,
    val carsJoined: String,
    val pricesJoined: String,
    val minCars: Int,
)

private var scmAllFlatRows: MutableList<dynamic> = mutableListOf()
private var scmSearchServerMode: Boolean = false
private var scmSearchTotalFlat: Long = 0
private var scmSearchTotalPages: Int = 1
private var scmSearchPageZeroBased: Int = 0
private var scmSearchFieldChoice: String = "all"
private var scmCurrentPage: Int = 1
private val scmItemsPerPage: Int = 25
private var scmSearchDebounceTimer: dynamic = null
private var scmMapResizeDebounceHandle: Int? = null
private var scmLastRenderSlice: List<ScmGroupRow>? = null
private var scmLastRenderPage: Int = 1
private var scmLastRenderTotalPages: Int = 1
private var scmLastRenderFooter: String = ""

private const val SCM_MAP_COMPACT_MAX_WIDTH_PX = 860

private fun scmPriceTokenForJoin(v: Any?): String {
    if (v == null || v == js("undefined")) return ""
    return extractNumericFromDbValue(v.unsafeCast<dynamic>()).trim()
}

/** Plain objects from fetch().json() — read props without Kotlin `asDynamic()` (runtime TypeError on JS objects). */
private fun scmJsonObjStrProp(obj: Any?, key: String): String {
    val v = js("(function(o,k){ if(o==null||o[k]==null||o[k]===undefined) return ''; return String(o[k]).trim(); })")(obj, key)
    return v.unsafeCast<String>()
}

private fun scmJsonObjIntProp(obj: Any?, key: String): Int? {
    val v =
        js("(function(o,k){ if(o==null||o[k]==null||o[k]===undefined) return null; var n=Number(o[k]); return isFinite(n)?Math.floor(n):null; })")(obj, key)
    return when (v) {
        null, js("undefined") -> null
        is Number -> v.toInt()
        else -> v.toString().toDoubleOrNull()?.toInt()
    }
}

private fun groupShippingChargesForView(rows: List<dynamic>): List<ScmGroupRow> {
    data class Acc(val canonicalStock: String, val pairs: MutableList<Pair<Int, String>>)

    val map = linkedMapOf<String, Acc>()
    for (r in rows) {
        val stock = scmJsonObjStrProp(r, "stockLocation")
        if (stock.isEmpty()) continue
        val cars = scmJsonObjIntProp(r, "carsPerContainer") ?: continue
        if (cars <= 0) continue
        val priceStr = scmPriceTokenForJoin(js("(function(o,k){ return o==null?null:o[k]; })")(r, "shippingPricePerCar"))
        val key = stock.lowercase()
        val acc = map.getOrPut(key) { Acc(stock, mutableListOf()) }
        acc.pairs.add(cars to priceStr)
    }

    val out = mutableListOf<ScmGroupRow>()
    for ((_, acc) in map) {
        val sorted = acc.pairs.sortedBy { it.first }.distinctBy { it.first }
        val carsJoined = sorted.joinToString(";") { it.first.toString() }
        val pricesJoined = sorted.joinToString(";") { it.second }
        val minCars = sorted.firstOrNull()?.first ?: 0
        out.add(
            ScmGroupRow(
                stockLocation = acc.canonicalStock,
                carsJoined = carsJoined,
                pricesJoined = pricesJoined,
                minCars = minCars,
            ),
        )
    }
    return out.sortedWith(compareBy({ it.stockLocation.lowercase() }, { it.minCars }))
}

private fun formatScmPriceChipsCell(rawJoined: String): String {
    val tokens = splitMultiValueDisplayTokens(rawJoined)
    if (tokens.isEmpty()) return ""
    fun chipFor(tok: String): String {
        val num = extractNumericFromDbValue(tok)
        if (num.isEmpty()) return ""
        val disp = "¥${formatNumericWithCommas(num)}"
        return formatConsigneeMapAddressChipHtml(disp)
    }
    if (tokens.size == 1) return chipFor(tokens[0])
    val inner = tokens.joinToString("") { chipFor(it) }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

private fun formatScmMergedTierChipsCell(carsJoined: String, pricesJoined: String): String {
    val carTokens = splitMultiValueDisplayTokens(carsJoined)
    val priceTokens = splitMultiValueDisplayTokens(pricesJoined)
    if (carTokens.isEmpty() || priceTokens.isEmpty() || carTokens.size != priceTokens.size) return ""
    
    fun chipFor(carsStr: String, priceStr: String): String {
        val priceNum = extractNumericFromDbValue(priceStr)
        if (carsStr.isEmpty() || priceNum.isEmpty()) return ""
        val disp = "${carsStr}/¥${formatNumericWithCommas(priceNum)}"
        return formatConsigneeMapAddressChipHtml(disp)
    }
    
    val pairs = carTokens.zip(priceTokens)
    if (pairs.size == 1) return chipFor(pairs[0].first, pairs[0].second)
    val inner = pairs.joinToString("") { (cars, price) -> chipFor(cars, price) }
    return """<span style="display:inline-flex;flex-wrap:wrap;gap:6px;align-items:center;">$inner</span>"""
}

private fun getScmSearchQuery(): String =
    (document.getElementById("scmMapSearchInput") as? HTMLInputElement)?.value?.trim().orEmpty()

private fun scmApiFieldParam(): String =
    when (scmSearchFieldChoice.lowercase()) {
        "stocklocation" -> "stockLocation"
        "carspercontainer" -> "carsPerContainer"
        "shippingpricepercar" -> "shippingPricePerCar"
        else -> "all"
    }

private fun scheduleScmSearchDebounced() {
    if (scmSearchDebounceTimer != null) {
        window.clearTimeout(scmSearchDebounceTimer.unsafeCast<Int>())
        scmSearchDebounceTimer = null
    }
    scmSearchDebounceTimer = window.setTimeout({
        scmSearchDebounceTimer = null
        runScmSearchFromInput()
    }, 420)
}

private fun runScmSearchFromInput() {
    val raw = getScmSearchQuery()
    if (raw.isEmpty()) {
        scmSearchServerMode = false
        scmSearchTotalFlat = 0
        scmSearchTotalPages = 1
        scmSearchPageZeroBased = 0
        scmCurrentPage = 1
        loadShippingChargeMapTable()
        return
    }
    scmSearchPageZeroBased = 0
    scmCurrentPage = 1
    loadShippingChargeMapTable()
}

private fun closeScmSearchFilterMenu() {
    val menu = document.getElementById("scmMapSearchFilterMenu") as? HTMLElement
    menu?.style?.setProperty("display", "none", "important")
    window.asDynamic().__scmMapSearchFilterMenuOpen = false
    (document.getElementById("scmMapSearchFilterBtn") as? HTMLElement)?.setAttribute("aria-expanded", "false")
}

private fun updateScmSearchFilterMenuActive(choice: String) {
    scmSearchFieldChoice = choice
    val map =
        listOf(
            "all" to "scmMapSearchOptAll",
            "stockLocation" to "scmMapSearchOptStock",
            "carsPerContainer" to "scmMapSearchOptCars",
            "shippingPricePerCar" to "scmMapSearchOptPrice",
        )
    for ((v, id) in map) {
        val el = document.getElementById(id) as? HTMLElement ?: continue
        if (v == choice) {
            el.classList.add("scm-map-search-filter-opt--active")
        } else {
            el.classList.remove("scm-map-search-filter-opt--active")
        }
    }
    val label =
        when (choice) {
            "stockLocation" -> "Stock location"
            "carsPerContainer" -> "Cars per container"
            "shippingPricePerCar" -> "Shipping price / car"
            else -> "All fields"
        }
    document.getElementById("scmMapSearchFieldLabel")?.textContent = label
    (document.getElementById("scmMapSearchFilterBtn") as? HTMLElement)?.setAttribute(
        "title",
        "Filter — search in: $label",
    )
}

private fun setupScmSearchBarListeners() {
    val input = document.getElementById("scmMapSearchInput") as? HTMLInputElement ?: return
    val filterBtn = document.getElementById("scmMapSearchFilterBtn") as? HTMLElement
    val menu = document.getElementById("scmMapSearchFilterMenu") as? HTMLElement
    val clearBtn = document.getElementById("scmMapSearchClearBtn") as? HTMLElement

    if (!input.hasAttribute("data-scm-map-search-bound")) {
        input.setAttribute("data-scm-map-search-bound", "true")
        input.addEventListener("input", { _: Event -> scheduleScmSearchDebounced() })
        input.addEventListener(
            "keydown",
            { ev: Event ->
                val kev = ev.asDynamic()
                if (kev.key == "Enter") {
                    ev.preventDefault()
                    if (scmSearchDebounceTimer != null) {
                        window.clearTimeout(scmSearchDebounceTimer.unsafeCast<Int>())
                        scmSearchDebounceTimer = null
                    }
                    runScmSearchFromInput()
                }
            },
        )
    }

    if (filterBtn != null && !filterBtn.hasAttribute("data-scm-map-search-bound")) {
        filterBtn.setAttribute("data-scm-map-search-bound", "true")
        filterBtn.addEventListener(
            "click",
            { e: Event ->
                e.stopPropagation()
                if (menu == null) return@addEventListener
                val open = window.asDynamic().__scmMapSearchFilterMenuOpen == true
                window.asDynamic().__scmMapSearchFilterMenuOpen = !open
                val nowOpen = !open
                menu.style.setProperty("display", if (open) "none" else "block", "important")
                filterBtn.setAttribute("aria-expanded", if (nowOpen) "true" else "false")
            },
        )
    }

    if (clearBtn != null && !clearBtn.hasAttribute("data-scm-map-search-bound")) {
        clearBtn.setAttribute("data-scm-map-search-bound", "true")
        clearBtn.addEventListener(
            "click",
            { _: Event ->
                input.value = ""
                closeScmSearchFilterMenu()
                runScmSearchFromInput()
            },
        )
    }

    val opts =
        listOf(
            "scmMapSearchOptAll" to "all",
            "scmMapSearchOptStock" to "stockLocation",
            "scmMapSearchOptCars" to "carsPerContainer",
            "scmMapSearchOptPrice" to "shippingPricePerCar",
        )
    for ((id, value) in opts) {
        val el = document.getElementById(id) as? HTMLElement
        if (el != null && !el.hasAttribute("data-scm-map-search-bound")) {
            el.setAttribute("data-scm-map-search-bound", "true")
            el.addEventListener(
                "click",
                { e: Event ->
                    e.stopPropagation()
                    updateScmSearchFilterMenuActive(value)
                    closeScmSearchFilterMenu()
                    val q = getScmSearchQuery()
                    if (q.isNotEmpty()) {
                        scmSearchPageZeroBased = 0
                        scmCurrentPage = 1
                        loadShippingChargeMapTable()
                    }
                },
            )
        }
    }

    if (window.asDynamic().__scmMapSearchFilterOutsideAttached != true) {
        window.asDynamic().__scmMapSearchFilterOutsideAttached = true
        document.addEventListener(
            "click",
            { event ->
                val target = event.target.unsafeCast<Node>() ?: return@addEventListener
                val m = document.getElementById("scmMapSearchFilterMenu") as? HTMLElement
                val b = document.getElementById("scmMapSearchFilterBtn") as? HTMLElement
                if (m == null) return@addEventListener
                val insideMenu = m.contains(target)
                val insideBtn = b != null && b.contains(target)
                if (!insideMenu && !insideBtn) closeScmSearchFilterMenu()
            },
        )
    }
}

private fun bindScmRowActionButtons(scope: HTMLElement) {
    val editBtns = scope.querySelectorAll(".scm-edit-btn")
    bindScmButtons(editBtns) { stock ->
        window.asDynamic().__scmModalStock = stock
        window.asDynamic().__scmModalMode = "edit"
        openScmModal(isDuplicate = false)
    }
    val dupBtns = scope.querySelectorAll(".scm-dup-btn")
    bindScmButtons(dupBtns) { stock ->
        window.asDynamic().__scmModalStock = stock
        window.asDynamic().__scmModalMode = "duplicate"
        openScmModal(isDuplicate = true)
    }
}

private fun bindScmButtons(nodes: NodeList, handler: (String) -> Unit) {
    val len = nodes.length
    for (i in 0 until len) {
        val el = nodes.item(i) as? HTMLElement ?: continue
        if (el.hasAttribute("data-scm-bound")) continue
        el.setAttribute("data-scm-bound", "true")
        val stock = el.getAttribute("data-scm-stock") ?: ""
        el.addEventListener("click", { handler(stock) })
    }
}

private fun renderScmPagerAndBind(tableDiv: HTMLElement, page: Int, totalPages: Int, footerNote: String) {
    val prevDisabled = page <= 1
    val nextDisabled = page >= totalPages
    val pagerHtml =
        """
        <div style="display:flex;justify-content:space-between;align-items:center;margin-top:16px;flex-wrap:wrap;gap:10px;">
            <div style="font-size:13px;color:#6b7280;">$footerNote</div>
            <div style="display:flex;align-items:center;gap:10px;">
                <button type="button" id="scmPrevPageBtn" style="padding:8px 14px;border-radius:6px;border:1px solid #d1d5db;background:${if (prevDisabled) "#f3f4f6" else "#fff"};cursor:${if (prevDisabled) "default" else "pointer"};" ${if (prevDisabled) "disabled" else ""}>Prev</button>
                <span style="font-size:14px;color:#374151;">Page <strong>$page</strong> of <strong>$totalPages</strong></span>
                <button type="button" id="scmNextPageBtn" style="padding:8px 14px;border-radius:6px;border:1px solid #d1d5db;background:${if (nextDisabled) "#f3f4f6" else "#fff"};cursor:${if (nextDisabled) "default" else "pointer"};" ${if (nextDisabled) "disabled" else ""}>Next</button>
            </div>
        </div>
        """.trimIndent()

    tableDiv.insertAdjacentHTML("beforeend", pagerHtml)

    document.getElementById("scmPrevPageBtn")?.addEventListener(
        "click",
        {
            if (page <= 1) return@addEventListener
            if (scmSearchServerMode) {
                scmSearchPageZeroBased = max(0, scmSearchPageZeroBased - 1)
                scmCurrentPage = scmSearchPageZeroBased + 1
            } else {
                scmCurrentPage = max(1, scmCurrentPage - 1)
            }
            loadShippingChargeMapTable()
        },
    )
    document.getElementById("scmNextPageBtn")?.addEventListener(
        "click",
        {
            if (page >= totalPages) return@addEventListener
            if (scmSearchServerMode) {
                scmSearchPageZeroBased = scmSearchPageZeroBased + 1
                scmCurrentPage = scmSearchPageZeroBased + 1
            } else {
                scmCurrentPage = scmCurrentPage + 1
            }
            loadShippingChargeMapTable()
        },
    )
}

private fun scmMapIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= SCM_MAP_COMPACT_MAX_WIDTH_PX
}

private fun setupScmMapResizeListener() {
    val root = document.getElementById("scmMapRoot") ?: return
    if (root.hasAttribute("data-scm-map-resize")) return
    root.setAttribute("data-scm-map-resize", "true")
    window.addEventListener("resize", { _: Event ->
        val prev = scmMapResizeDebounceHandle
        if (prev != null) window.clearTimeout(prev)
        scmMapResizeDebounceHandle = window.setTimeout({
            if (document.getElementById("scmMapRoot") == null) return@setTimeout
            val slice = scmLastRenderSlice ?: return@setTimeout
            val host = document.getElementById("scmMapTable") as? HTMLElement ?: return@setTimeout
            renderGroupedTableUi(host, slice, scmLastRenderPage, scmLastRenderTotalPages, scmLastRenderFooter)
        }, 120)
    })
}

private fun scmMapActionButtonsHtml(stockEscaped: String): String =
    """
    <div style="display:flex;gap:8px;align-items:center;">
        <button type="button" class="scm-edit-btn" data-scm-stock="$stockEscaped" aria-label="Edit" title="Edit" style="width:36px;height:36px;display:inline-flex;align-items:center;justify-content:center;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 6px rgba(76,201,255,0.30);">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
            </svg>
        </button>
        <button type="button" class="scm-dup-btn" data-scm-stock="$stockEscaped" aria-label="Duplicate" title="Duplicate" style="width:36px;height:36px;display:inline-flex;align-items:center;justify-content:center;background-color:#3b82f6;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 6px rgba(59,130,246,0.30);">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
            </svg>
        </button>
    </div>
    """.trimIndent()

private fun appendScmMapTableRow(html: StringBuilder, g: ScmGroupRow) {
    val st = escapeHtml(g.stockLocation)
    val mergedCell = formatScmMergedTierChipsCell(g.carsJoined, g.pricesJoined)
    html.append(
        """
        <tr style="border-bottom:1px solid #f3f4f6;">
            <td style="padding:10px 12px;vertical-align:middle;">${scmMapActionButtonsHtml(st)}</td>
            <td style="padding:12px 14px;font-size:14px;color:#111827;font-weight:600;vertical-align:top;">$st</td>
            <td style="padding:12px 14px;font-size:14px;color:#374151;vertical-align:top;">$mergedCell</td>
        </tr>
        """.trimIndent()
    )
}

private fun appendScmMapCard(html: StringBuilder, g: ScmGroupRow) {
    val st = escapeHtml(g.stockLocation)
    val mergedCell = formatScmMergedTierChipsCell(g.carsJoined, g.pricesJoined)
    html.append("""<div class="scm-map-card">""")
    html.append("""<div class="scm-map-card-top">${scmMapActionButtonsHtml(st)}</div>""")
    html.append("""<div class="scm-map-card-grid">""")
    html.append(
        """<div class="scm-map-kv"><div class="scm-map-k">Stock location</div><div class="scm-map-v">${formatPurchaseListNeutralChipHtml(g.stockLocation)}</div></div>"""
    )
    if (mergedCell.isNotEmpty()) {
        html.append(
            """<div class="scm-map-kv"><div class="scm-map-k">Cars / price per car</div><div class="scm-map-v">$mergedCell</div></div>"""
        )
    }
    html.append("""</div></div>""")
}

private fun renderGroupedTableUi(
    host: HTMLElement,
    pageSlice: List<ScmGroupRow>,
    page: Int,
    totalPages: Int,
    footerNote: String,
) {
    scmLastRenderSlice = pageSlice
    scmLastRenderPage = page
    scmLastRenderTotalPages = totalPages
    scmLastRenderFooter = footerNote

    val compact = scmMapIsCompactLayout()
    val html = StringBuilder()

    if (!compact) {
        html.append(
            """
            <div class="scm-map-table-shell">
            <table class="purchase-list-table" style="width:100%;border-collapse:collapse;table-layout:fixed;">
            <colgroup><col style="width:88px;"><col/><col/></colgroup>
            <thead>
            <tr style="background:#f9fafb;text-align:left;">
            <th style="padding:12px 14px;font-size:12px;font-weight:700;color:#6b7280;text-transform:uppercase;">Actions</th>
            <th style="padding:12px 14px;font-size:12px;font-weight:700;color:#6b7280;text-transform:uppercase;">Stock location</th>
            <th style="padding:12px 14px;font-size:12px;font-weight:700;color:#6b7280;text-transform:uppercase;">Cars per container / Shipping price per car</th>
            </tr>
            </thead>
            <tbody id="scmMapTableBody">
            """.trimIndent()
        )
        for (g in pageSlice) {
            appendScmMapTableRow(html, g)
        }
        html.append("</tbody></table></div>")
    } else {
        html.append("""<div id="scmMapTableBody" class="scm-map-cards">""")
        for (g in pageSlice) {
            appendScmMapCard(html, g)
        }
        html.append("</div>")
    }

    host.innerHTML = html.toString()
    bindScmRowActionButtons(host)
    renderScmPagerAndBind(host, page, totalPages, footerNote)
}

fun loadShippingChargeMapTable() {
    val tableDiv = document.getElementById("scmMapTable") as? HTMLElement ?: return
    scmLastRenderSlice = null
    tableDiv.innerHTML =
        """<div class="scm-map-empty"><strong>Loading</strong><div>Loading shipping charge map…</div></div>"""

    val q = getScmSearchQuery()
    if (q.isNotEmpty()) {
        scmSearchServerMode = true
        val encQ = js("encodeURIComponent")(q).unsafeCast<String>()
        val encF = js("encodeURIComponent")(scmApiFieldParam()).unsafeCast<String>()
        val p = scmSearchPageZeroBased
        val url = apiUrl("shipping-charge-map/mappings/page-search?q=$encQ&field=$encF&page=$p&size=$scmItemsPerPage")
        window.fetch(url)
            .then { resp: dynamic ->
                if (resp.ok) resp.json() else throw js("Error('Search failed')")
            }
            .then { body: dynamic ->
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) throw js("Error(err)")
                scmSearchTotalFlat =
                    when (val te = js("body.totalElements")) {
                        is Number -> te.toLong()
                        else -> te?.toString()?.toLongOrNull() ?: 0L
                    }
                scmSearchTotalPages =
                    max(
                        1,
                        when (val tp = js("body.totalPages")) {
                            is Number -> tp.toInt()
                            else -> tp?.toString()?.toIntOrNull() ?: 1
                        },
                    )
                scmSearchPageZeroBased =
                    when (val num = js("body.page")) {
                        is Number -> num.toInt()
                        else -> num?.toString()?.toIntOrNull() ?: 0
                    }
                scmCurrentPage = scmSearchPageZeroBased + 1

                val content = js("body.content") ?: js("[]")
                val arr = js("Array.isArray(content) ? content : []").unsafeCast<Array<dynamic>>()
                val grouped = groupShippingChargesForView(arr.toList())
                if (grouped.isEmpty()) {
                    scmLastRenderSlice = null
                    tableDiv.innerHTML =
                        """
                        <div class="scm-map-empty">
                            <strong>No matches</strong>
                            <div>No rows match your search.</div>
                            <div style="font-size:13px;color:#9ca3af;">Edit still loads full tiers for a stock location.</div>
                        </div>
                        """.trimIndent()
                    return@then
                }

                val footer =
                    "Matched DB rows: ${scmSearchTotalFlat}. Showing ${grouped.size} stock location group(s) on this page (tiers may be partial until search is cleared)."
                renderGroupedTableUi(tableDiv, grouped, scmCurrentPage, scmSearchTotalPages, footer)
            }
            .catch { e: dynamic ->
                Logger.error("Shipping charge map search failed: ${e.toString()}")
                scmLastRenderSlice = null
                tableDiv.innerHTML =
                    """<div class="scm-map-empty" style="color:#b91c1c;"><strong>Could not load</strong><div>Could not load results.</div></div>"""
            }
        return
    }

    scmSearchServerMode = true
    val pBrowse = scmSearchPageZeroBased
    val browseUrl = apiUrl("shipping-charge-map/mappings/page?page=$pBrowse&size=$scmItemsPerPage")
    window.fetch(browseUrl)
        .then { resp: dynamic ->
            if (resp.ok) resp.json() else throw js("Error('Failed to load')")
        }
        .then { body: dynamic ->
            val err = js("body.error")?.toString()?.trim()
            if (!err.isNullOrEmpty()) throw js("Error(err)")
            scmSearchTotalFlat =
                when (val te = js("body.totalElements")) {
                    is Number -> te.toLong()
                    else -> te?.toString()?.toLongOrNull() ?: 0L
                }
            scmSearchTotalPages =
                max(
                    1,
                    when (val tp = js("body.totalPages")) {
                        is Number -> tp.toInt()
                        else -> tp?.toString()?.toIntOrNull() ?: 1
                    },
                )
            scmSearchPageZeroBased =
                when (val num = js("body.page")) {
                    is Number -> num.toInt()
                    else -> num?.toString()?.toIntOrNull() ?: 0
                }
            scmCurrentPage = scmSearchPageZeroBased + 1

            val content = js("body.content") ?: js("[]")
            val arr = js("Array.isArray(content) ? content : []").unsafeCast<Array<dynamic>>()
            scmAllFlatRows = arr.toList().toMutableList()
            val grouped = groupShippingChargesForView(scmAllFlatRows)
            if (grouped.isEmpty()) {
                scmLastRenderSlice = null
                tableDiv.innerHTML =
                    """
                    <div class="scm-map-empty">
                        <strong>No tiers yet</strong>
                        <div>No shipping charge rows yet.</div>
                        <div style="font-size:13px;color:#9ca3af;">Use Add tiers to create tiers for a stock location.</div>
                    </div>
                    """.trimIndent()
                return@then
            }
            val footer =
                "DB rows: ${scmSearchTotalFlat}. Showing ${grouped.size} stock location group(s) on this page."
            renderGroupedTableUi(tableDiv, grouped, scmCurrentPage, scmSearchTotalPages, footer)
        }
        .catch { e: dynamic ->
            Logger.error("Shipping charge map load failed: ${e.toString()}")
            scmLastRenderSlice = null
            tableDiv.innerHTML =
                """<div class="scm-map-empty" style="color:#b91c1c;"><strong>Could not load</strong><div>Unable to load shipping charge map.</div></div>"""
        }
}

private fun populateNativeStockSelect(selectId: String, selectedValue: String) {
    val sel = document.getElementById(selectId) as? HTMLSelectElement ?: return
    window.fetch(apiUrl("master-menu/stock_location"))
        .then { r: dynamic -> if (r.ok) r.json() else js("Promise.resolve([])") }
        .then { raw: dynamic ->
            val list = parseMasterListArray(raw).distinct().filter { it.isNotBlank() }.sorted()
            while (sel.options.length > 1) {
                sel.remove(1)
            }
            for (v in list) {
                val opt = document.createElement("option") as HTMLOptionElement
                opt.value = v
                opt.text = v
                sel.add(opt)
            }
            if (selectedValue.isNotBlank()) {
                var found = false
                for (i in 0 until sel.options.length) {
                    val o = sel.options.item(i) as? HTMLOptionElement ?: continue
                    if (o.value.equals(selectedValue, ignoreCase = true)) {
                        sel.selectedIndex = i
                        found = true
                        break
                    }
                }
                if (!found) {
                    val opt = document.createElement("option") as HTMLOptionElement
                    opt.value = selectedValue
                    opt.text = selectedValue
                    sel.add(opt)
                    sel.value = selectedValue
                }
            }
        }
        .catch { _: dynamic -> }
}

private fun fetchTiersForStock(stock: String, onDone: (carsJoined: String, pricesJoined: String) -> Unit) {
    val stockTrim = stock.trim()
    val enc = js("encodeURIComponent")(stockTrim).unsafeCast<String>()
    window.fetch(apiUrl("shipping-charge-map/mappings/by-stock-location?stockLocation=$enc"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('load')") }
        .then { result: dynamic ->
            val data = js("result.data") ?: js("[]")
            val arr = js("Array.isArray(data) ? data : []").unsafeCast<Array<dynamic>>()
            // Server already filters by stock; equals check kept as safety.
            val filtered =
                arr.toList().filter { row ->
                    scmJsonObjStrProp(row, "stockLocation").equals(stockTrim, ignoreCase = true)
                }
            val grouped = groupShippingChargesForView(filtered)
            val g = grouped.firstOrNull()
            if (g == null) {
                onDone("", "")
            } else {
                onDone(g.carsJoined, g.pricesJoined)
            }
        }
        .catch { _: dynamic -> onDone("", "") }
}

private fun collectScmTierPairs(carsFieldId: String, priceFieldId: String): List<Pair<Int, Double>>? {
    val carsTok = splitMultiValueDisplayTokens(getChipFieldValue(carsFieldId))
    val priceTok = splitMultiValueDisplayTokens(getChipFieldValue(priceFieldId))
    if (carsTok.isEmpty()) {
        showMessage("Add at least one cars-per-container value.", "error")
        return null
    }
    if (carsTok.size != priceTok.size) {
        showMessage("Cars per container and shipping price chips must match one-for-one.", "error")
        return null
    }
    val carsInts = mutableListOf<Int>()
    for (t in carsTok) {
        val n = t.trim().toIntOrNull()
        if (n == null || n <= 0) {
            showMessage("Each cars-per-container chip must be a positive whole number.", "error")
            return null
        }
        carsInts.add(n)
    }
    if (carsInts.size != carsInts.toSet().size) {
        showMessage("Duplicate cars-per-container values are not allowed.", "error")
        return null
    }
    val prices = mutableListOf<Double>()
    for (t in priceTok) {
        val n = extractNumericFromDbValue(t).toDoubleOrNull()
        if (n == null || n < 0) {
            showMessage("Each shipping price must be a valid non-negative number.", "error")
            return null
        }
        prices.add(n)
    }
    return carsInts.zip(prices)
}

private fun putScmReplaceTiers(stock: String, previousStock: String?, tiers: List<Pair<Int, Double>>, onComplete: (Boolean) -> Unit) {
    val tiersArr = js("[]")
    for ((c, p) in tiers.sortedBy { it.first }) {
        val row = js("{}")
        js("(function(r,a,b){ r.carsPerContainer=a; r.shippingPricePerCar=b; })")(row, c, p)
        js("(function(arr,x){ arr.push(x); })")(tiersArr, row)
    }
    val bodyObj = js("{}")
    val prevJs = previousStock?.takeIf { it.isNotBlank() } ?: js("undefined")
    js("(function(b,s,p,t){ b.stockLocation=s; if(p!==undefined&&p!==null&&p!=='') b.previousStockLocation=p; b.tiers=t; })")(bodyObj, stock.trim(), prevJs, tiersArr)

    val req = js("{}")
    js("(function(r,m,h,b){ r.method=m; r.headers=h; r.body=b; })")(req, "PUT", js("{\"Content-Type\":\"application/json\"}"), JSON.stringify(bodyObj))

    window.fetch(apiUrl("shipping-charge-map/mappings/replace-tiers"), req.unsafeCast<org.w3c.fetch.RequestInit>())
        .then { r: dynamic -> r.json() }
        .then { jsonRaw: dynamic ->
            val ok = js("(function(j){ return !!(j && j.success); })")(jsonRaw).unsafeCast<Boolean>()
            val msg =
                js("(function(j,d){ if(!j) return d; var m=j.message; return (m==null||m===undefined)?d:String(m); })")(
                    jsonRaw,
                    if (ok) "Saved" else "Save failed",
                ).unsafeCast<String>()
            if (ok) {
                showMessage(msg, "success")
                onComplete(true)
            } else {
                showMessage(msg, "error")
                onComplete(false)
            }
        }
        .catch { e: dynamic ->
            Logger.error("replace-tiers failed: ${e.toString()}")
            showMessage("Save failed", "error")
            onComplete(false)
        }
}

private fun deleteScmStock(stock: String, onComplete: (Boolean) -> Unit) {
    val enc = js("encodeURIComponent")(stock.trim()).unsafeCast<String>()
    window.fetch(apiUrl("shipping-charge-map/mappings/by-stock-location?stockLocation=$enc"), js("{ method: 'DELETE' }").unsafeCast<org.w3c.fetch.RequestInit>())
        .then { r: dynamic -> r.json() }
        .then { jsonRaw: dynamic ->
            val ok = js("(function(j){ return !!(j && j.success); })")(jsonRaw).unsafeCast<Boolean>()
            val msg =
                js("(function(j,d){ if(!j) return d; var m=j.message; return (m==null||m===undefined)?d:String(m); })")(
                    jsonRaw,
                    if (ok) "Deleted" else "Delete failed",
                ).unsafeCast<String>()
            if (ok) {
                showMessage(msg, "success")
                onComplete(true)
            } else {
                showMessage(msg, "error")
                onComplete(false)
            }
        }
        .catch { e: dynamic ->
            Logger.error("delete stock failed: ${e.toString()}")
            showMessage("Delete failed", "error")
            onComplete(false)
        }
}

private fun closeScmModal() {
    document.getElementById("scmModalOverlay")?.remove()
}

private fun closeDupScmModal() {
    document.getElementById("scmDupModalOverlay")?.remove()
}

private fun openScmModal(isDuplicate: Boolean) {
    ensureSupplierChipJs()

    val mode = window.asDynamic().__scmModalMode as? String ?: ""
    val stockArg = window.asDynamic().__scmModalStock as? String ?: ""
    window.asDynamic().__scmModalStock = null
    window.asDynamic().__scmModalMode = null

    val dupMode = isDuplicate || mode == "duplicate"

    if (!dupMode) {
        val overlayId = "scmModalOverlay"
        document.getElementById(overlayId)?.remove()
        val html =
            """
            <div id="$overlayId" style="position:fixed;inset:0;background:rgba(0,0,0,0.45);z-index:10050;display:flex;align-items:center;justify-content:center;">
                <div style="background:#fff;border-radius:12px;width:92%;max-width:560px;max-height:90vh;overflow-y:auto;box-shadow:0 20px 40px rgba(0,0,0,0.18);">
                    <div style="padding:20px 22px;border-bottom:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:center;">
                        <h2 style="margin:0;font-size:20px;color:#111827;">${if (stockArg.isBlank()) "Add shipping tiers" else "Edit shipping tiers"}</h2>
                        <button type="button" id="scmModalClose" style="border:none;background:transparent;font-size:22px;color:#6b7280;cursor:pointer;">×</button>
                    </div>
                    <style>
                        #scmCarsPerContainerChips, #scmShippingPricePerCarChips { display: none !important; }
                    </style>
                    <div style="padding:22px;">
                        <input type="hidden" id="scmOriginalStockHidden" value="${escapeHtml(stockArg)}">
                        <div style="margin-bottom:16px;">
                            <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Stock location</label>
                            <select id="scmStockSelect" style="width:100%;padding:10px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:14px;">
                                <option value="">Select stock location</option>
                            </select>
                        </div>
                        <div style="margin-bottom:16px;">
                            <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Cars per container</label>
                            ${createChipInput("scmCarsPerContainer", "Add count, Enter")}
                        </div>
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Shipping price / car</label>
                            ${createChipInput("scmShippingPricePerCar", "Amount, Enter")}
                        </div>
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Combined Tiers (Preview)</label>
                            <div id="scmCombinedPreviewChips" style="display:flex; flex-wrap:wrap; gap:6px; min-height:42px; padding:6px 8px; border:1px dashed #d1d5db; border-radius:6px; background:#f9fafb;">
                                <span style="color:#9ca3af;font-size:13px;font-style:italic;">No tiers added yet</span>
                            </div>
                        </div>
                        <div style="display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;">
                            ${if (stockArg.isNotBlank()) """<button type="button" id="scmModalDelete" style="padding:10px 16px;border-radius:8px;border:1px solid #ef4444;background:#fff;color:#ef4444;cursor:pointer;font-size:14px;">Delete all tiers</button>""" else ""}
                            <button type="button" id="scmModalCancel" style="padding:10px 16px;border-radius:8px;border:1px solid #d1d5db;background:#fff;cursor:pointer;font-size:14px;">Cancel</button>
                            <button type="button" id="scmModalSave" style="padding:10px 16px;border-radius:8px;border:none;background:#059669;color:#fff;cursor:pointer;font-size:14px;font-weight:600;">Save</button>
                        </div>
                    </div>
                </div>
            </div>
            """.trimIndent()
        document.body?.insertAdjacentHTML("beforeend", html)

        populateNativeStockSelect("scmStockSelect", stockArg)
        setChipFieldValue("scmCarsPerContainer", "")
        setChipFieldValue("scmShippingPricePerCar", "")
        if (stockArg.isNotBlank()) {
            fetchTiersForStock(stockArg) { cars, prices ->
                setChipFieldValue("scmCarsPerContainer", cars)
                setChipFieldValue("scmShippingPricePerCar", prices)
            }
        }

        document.getElementById("scmModalClose")?.addEventListener("click", { closeScmModal() })
        document.getElementById("scmModalCancel")?.addEventListener("click", { closeScmModal() })
        document.getElementById("scmModalSave")?.addEventListener(
            "click",
            {
                val sel = (document.getElementById("scmStockSelect") as? HTMLSelectElement)?.value?.trim().orEmpty()
                if (sel.isEmpty()) {
                    showMessage("Choose a stock location.", "error")
                    return@addEventListener
                }
                val tiers = collectScmTierPairs("scmCarsPerContainer", "scmShippingPricePerCar") ?: return@addEventListener
                val original = (document.getElementById("scmOriginalStockHidden") as? HTMLInputElement)?.value?.trim().orEmpty()
                val prevForApi =
                    if (original.isNotBlank() && !original.equals(sel, ignoreCase = true)) original else null
                putScmReplaceTiers(sel, prevForApi, tiers) { ok ->
                    if (ok) {
                        closeScmModal()
                        scmCurrentPage = 1
                        loadShippingChargeMapTable()
                    }
                }
            },
        )
        document.getElementById("scmModalDelete")?.addEventListener(
            "click",
            {
                val original = (document.getElementById("scmOriginalStockHidden") as? HTMLInputElement)?.value?.trim().orEmpty()
                if (original.isBlank()) return@addEventListener
                if (!js("confirm('Delete all tiers for this stock location?')").unsafeCast<Boolean>()) return@addEventListener
                deleteScmStock(original) { ok ->
                    if (ok) {
                        closeScmModal()
                        loadShippingChargeMapTable()
                    }
                }
            },
        )
        return
    }

    // Duplicate modal
    val dupOverlayId = "scmDupModalOverlay"
    document.getElementById(dupOverlayId)?.remove()
    val dupHtml =
        """
        <div id="$dupOverlayId" style="position:fixed;inset:0;background:rgba(0,0,0,0.45);z-index:10050;display:flex;align-items:center;justify-content:center;">
            <div style="background:#fff;border-radius:12px;width:92%;max-width:560px;max-height:90vh;overflow-y:auto;box-shadow:0 20px 40px rgba(0,0,0,0.18);">
                <div style="padding:20px 22px;border-bottom:1px solid #e5e7eb;display:flex;justify-content:space-between;align-items:center;">
                    <h2 style="margin:0;font-size:20px;color:#111827;">Duplicate shipping tiers</h2>
                    <button type="button" id="scmDupModalClose" style="border:none;background:transparent;font-size:22px;color:#6b7280;cursor:pointer;">×</button>
                </div>
                <style>
                    #dupScmCarsPerContainerChips, #dupScmShippingPricePerCarChips { display: none !important; }
                </style>
                <div style="padding:22px;">
                    <div style="margin-bottom:16px;">
                        <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Stock location</label>
                        <select id="dupScmStockSelect" style="width:100%;padding:10px 12px;border:1px solid #d1d5db;border-radius:8px;font-size:14px;">
                            <option value="">Select stock location</option>
                        </select>
                    </div>
                    <div style="margin-bottom:16px;">
                        <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Cars per container</label>
                        ${createChipInput("dupScmCarsPerContainer", "Add count, Enter")}
                    </div>
                    <div style="margin-bottom:20px;">
                        <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Shipping price / car</label>
                        ${createChipInput("dupScmShippingPricePerCar", "Amount, Enter")}
                    </div>
                    <div style="margin-bottom:20px;">
                        <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;font-size:13px;">Combined Tiers (Preview)</label>
                        <div id="dupScmCombinedPreviewChips" style="display:flex; flex-wrap:wrap; gap:6px; min-height:42px; padding:6px 8px; border:1px dashed #d1d5db; border-radius:6px; background:#f9fafb;">
                            <span style="color:#9ca3af;font-size:13px;font-style:italic;">No tiers added yet</span>
                        </div>
                    </div>
                    <div style="display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;">
                        <button type="button" id="scmDupModalCancel" style="padding:10px 16px;border-radius:8px;border:1px solid #d1d5db;background:#fff;cursor:pointer;font-size:14px;">Cancel</button>
                        <button type="button" id="scmDupModalSave" style="padding:10px 16px;border-radius:8px;border:none;background:#2563eb;color:#fff;cursor:pointer;font-size:14px;font-weight:600;">Save</button>
                    </div>
                </div>
            </div>
        </div>
        """.trimIndent()
    document.body?.insertAdjacentHTML("beforeend", dupHtml)

    populateNativeStockSelect("dupScmStockSelect", "")
    setChipFieldValue("dupScmCarsPerContainer", "")
    setChipFieldValue("dupScmShippingPricePerCar", "")
    if (stockArg.isNotBlank()) {
        fetchTiersForStock(stockArg) { cars, prices ->
            setChipFieldValue("dupScmCarsPerContainer", cars)
            setChipFieldValue("dupScmShippingPricePerCar", prices)
        }
    }

    document.getElementById("scmDupModalClose")?.addEventListener("click", { closeDupScmModal() })
    document.getElementById("scmDupModalCancel")?.addEventListener("click", { closeDupScmModal() })
    document.getElementById("scmDupModalSave")?.addEventListener(
        "click",
        {
            val sel = (document.getElementById("dupScmStockSelect") as? HTMLSelectElement)?.value?.trim().orEmpty()
            if (sel.isEmpty()) {
                showMessage("Choose a stock location.", "error")
                return@addEventListener
            }
            val tiers = collectScmTierPairs("dupScmCarsPerContainer", "dupScmShippingPricePerCar") ?: return@addEventListener
            putScmReplaceTiers(sel, null, tiers) { ok ->
                if (ok) {
                    closeDupScmModal()
                    scmCurrentPage = 1
                    loadShippingChargeMapTable()
                }
            }
        },
    )
}

fun showShippingChargeMapPage() {
    val content = document.getElementById("content") ?: return
    scmLastRenderSlice = null
    content.innerHTML =
        """
        <div id="scmMapRoot">
            <style>
                #scmMapRoot{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;width:100%;max-width:100%;box-sizing:border-box;}
                .scm-map-toolbar{display:grid;grid-template-columns:1fr;grid-template-areas:"title" "search" "actions";gap:12px;margin-bottom:16px;align-items:center;}
                .scm-map-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;grid-area:title;text-align:center;letter-spacing:-0.01em;}
                .scm-map-search-row{grid-area:search;display:flex;align-items:center;gap:10px;width:100%;min-width:0;}
                .scm-map-search{position:relative;flex:1;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
                .scm-map-search input{width:100%;box-sizing:border-box;padding:11px 38px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
                .scm-map-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:4px 8px;min-height:36px;min-width:36px;}
                .scm-map-search-clear:hover{background:#f3f4f6;color:#111827;}
                .scm-map-filter-wrap{position:relative;flex-shrink:0;}
                .scm-map-actions{grid-area:actions;}
                .scm-map-add-btn{padding:10px 16px;background:#059669;color:#fff;border:none;border-radius:10px;font-size:14px;font-weight:600;cursor:pointer;min-height:40px;}
                .scm-map-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
                #scmMapRoot table.purchase-list-table thead th{position:sticky;top:0;z-index:1;background:#f9fafb;}
                .scm-map-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
                .scm-map-empty strong{color:#0f172a;}
                .scm-map-cards{display:flex;flex-direction:column;gap:10px;}
                .scm-map-card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 2px rgba(0,0,0,0.04);padding:12px;}
                .scm-map-card-top{margin-bottom:10px;}
                .scm-map-card-grid{display:grid;gap:8px;}
                .scm-map-kv{display:flex;gap:10px;align-items:flex-start;}
                .scm-map-k{min-width:120px;font-size:12px;color:#64748b;line-height:1.4;}
                .scm-map-v{flex:1;min-width:0;}
                #scmMapRoot .scm-map-search-filter-opt:hover{background:#f3f4f6!important;}
                #scmMapRoot .scm-map-search-filter-opt--active{background:#eef2ff!important;font-weight:600;}
                #scmMapRoot #scmMapSearchFilterBtn:hover{background:#e8eaed!important;box-shadow:0 2px 8px rgba(0,0,0,0.08)!important;}
                @media (max-width:1024px){
                    #scmMapRoot{padding:14px;border-radius:14px;}
                    .scm-map-toolbar{gap:14px;margin-bottom:14px;}
                    .scm-map-title{font-size:17px;}
                    .scm-map-search input{font-size:13px;padding:10px 34px 10px 38px;}
                }
                @media (min-width:1025px){
                    #scmMapRoot{max-width:1200px;margin:0 auto;}
                    .scm-map-toolbar{grid-template-columns:auto 1fr minmax(260px,32%);grid-template-areas:"title . search" "actions actions actions";column-gap:12px;row-gap:10px;}
                    .scm-map-title{text-align:left;justify-self:start;font-size:22px;}
                    .scm-map-actions{justify-self:start;}
                }
            </style>
            <div class="scm-map-toolbar">
                <h2 class="scm-map-title">Shipping Charge Map</h2>
                <div class="scm-map-search-row">
                    <div class="scm-map-search">
                        <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);color:#9ca3af;display:flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="search" id="scmMapSearchInput" autocomplete="off" placeholder="Search…" aria-label="Search shipping charge map" />
                        <button type="button" id="scmMapSearchClearBtn" class="scm-map-search-clear" title="Clear search" aria-label="Clear search">×</button>
                    </div>
                    <div class="scm-map-filter-wrap">
                        <span id="scmMapSearchFieldLabel" style="position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;">All fields</span>
                        <button type="button" id="scmMapSearchFilterBtn" aria-haspopup="true" aria-expanded="false" title="Search field filter" style="width:46px;height:46px;border-radius:50%;border:1px solid #e5e7eb;background:#f3f4f6;cursor:pointer;display:flex;align-items:center;justify-content:center;color:#4b5563;">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </button>
                        <div id="scmMapSearchFilterMenu" style="display:none;position:absolute;right:0;top:calc(100% + 8px);z-index:20001;min-width:220px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;box-shadow:0 10px 40px rgba(0,0,0,0.12);padding:8px 0;">
                            <div style="padding:8px 14px 4px;font-size:11px;font-weight:600;color:#6b7280;text-transform:uppercase;">Search in</div>
                            <button type="button" class="scm-map-search-filter-opt scm-map-search-filter-opt--active" id="scmMapSearchOptAll" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;">All fields</button>
                            <button type="button" class="scm-map-search-filter-opt" id="scmMapSearchOptStock" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;">Stock location</button>
                            <button type="button" class="scm-map-search-filter-opt" id="scmMapSearchOptCars" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;">Cars per container</button>
                            <button type="button" class="scm-map-search-filter-opt" id="scmMapSearchOptPrice" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;">Shipping price / car</button>
                        </div>
                    </div>
                </div>
                <div class="scm-map-actions">
                    <button type="button" id="scmMapAddBtn" class="scm-map-add-btn">＋ Add tiers</button>
                </div>
            </div>
            <div id="scmMapTable"></div>
        </div>
        """.trimIndent()

    scmSearchFieldChoice = "all"
    scmSearchServerMode = false
    scmSearchTotalFlat = 0
    scmSearchTotalPages = 1
    scmSearchPageZeroBased = 0
    scmCurrentPage = 1
    updateScmSearchFilterMenuActive("all")
    setupScmSearchBarListeners()
    setupScmMapResizeListener()

    document.getElementById("scmMapAddBtn")?.addEventListener(
        "click",
        {
            window.asDynamic().__scmModalStock = ""
            window.asDynamic().__scmModalMode = "add"
            openScmModal(isDuplicate = false)
        },
    )

    loadShippingChargeMapTable()
}

/** Legacy entry points from grouped table rows (`window` globals). */
@Suppress("UNUSED_PARAMETER")
fun editMasterShippingCharge(rowIndex: Int) {
    val stock = window.asDynamic().__scmEditStock as? String
    window.asDynamic().__scmEditStock = null
    if (stock.isNullOrBlank()) return
    window.asDynamic().__scmModalStock = stock
    window.asDynamic().__scmModalMode = "edit"
    openScmModal(isDuplicate = false)
}

@Suppress("UNUSED_PARAMETER")
fun duplicateMasterShippingCharge(rowIndex: Int) {
    val stock = window.asDynamic().__scmDupStock as? String
    window.asDynamic().__scmDupStock = null
    if (stock.isNullOrBlank()) return
    window.asDynamic().__scmModalStock = stock
    window.asDynamic().__scmModalMode = "duplicate"
    openScmModal(isDuplicate = true)
}
