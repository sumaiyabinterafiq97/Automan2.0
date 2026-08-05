package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.asDynamic
import kotlin.js.unsafeCast
import org.w3c.dom.*
import org.w3c.dom.events.Event
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await

/** sessionStorage: JSON payload when opening Create Invoice from history Edit. */
const val INVOICE_HISTORY_EDIT_SESSION_KEY = "invoiceHistoryEditPayload"
/** Persists invoice number for #/recreate-invoice after in-app navigation. */
const val INVOICE_RECREATE_META_SESSION_KEY = "invoiceRecreateMeta"

private var invoiceHistoryCachedRows: Array<dynamic> = emptyArray()
private var invoiceHistorySortField: String = "invoiceNumber"
private var invoiceHistorySortOrder: String = "desc"
private var invoiceHistoryResizeDebounceHandle: Int? = null
private var invoiceHistorySearchDebounceHandle: Int? = null
private var invoiceHistoryLastCompactLayout: Boolean? = null
private var invoiceHistoryServerMode: Boolean = true
private var invoiceHistoryPageZeroBased: Int = 0
private var invoiceHistoryTotalPages: Int = 1
private var invoiceHistoryTotalElements: Long = 0L
private var invoiceHistoryItemsPerPage: Int = AppConstants.DEFAULT_ITEMS_PER_PAGE
private var invoiceHistoryActiveSearchQ: String = ""

private const val INVOICE_HISTORY_COMPACT_MAX_WIDTH_PX = 860

fun showInvoiceHistoryPage() {
    navigateToApp("/invoice-history")
    val content = document.getElementById("content") ?: return
    invoiceHistoryCachedRows = emptyArray()
    invoiceHistorySortField = "invoiceNumber"
    invoiceHistorySortOrder = "desc"
    invoiceHistoryServerMode = true
    invoiceHistoryPageZeroBased = 0
    invoiceHistoryTotalPages = 1
    invoiceHistoryTotalElements = 0L
    invoiceHistoryItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
    invoiceHistoryActiveSearchQ = ""

    content.innerHTML = """
        <style>
            #invoiceHistoryPage{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;}
            .invoice-history-toolbar{
                display:grid;
                grid-template-columns:1fr;
                grid-template-areas:"title" "search";
                gap:12px;
                margin-bottom:16px;
                align-items:center;
            }
            .invoice-history-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;grid-area:title;text-align:center;}
            .invoice-search{grid-area:search;width:100%;position:relative;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
            .invoice-search input{width:100%;box-sizing:border-box;padding:11px 36px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
            .invoice-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:6px 8px;min-height:36px;min-width:36px;}
            .invoice-search-clear:hover{background:#f3f4f6;color:#111827;}
            .invoice-history-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
            table.purchase-list-table thead th{position:sticky;top:0;z-index:10;}
            .invoice-history-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
            .invoice-history-empty strong{color:#0f172a;}
            .invoice-history-pager{display:flex;justify-content:space-between;align-items:center;padding:16px;background-color:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;margin-top:12px;flex-wrap:wrap;gap:12px;}
            .invoice-history-pager-meta{color:#6b7280;font-size:14px;}
            .invoice-history-pager-btns{display:flex;align-items:center;gap:8px;flex-wrap:wrap;}
            .invoice-history-pager-btn{padding:8px 16px;background-color:#007bff;color:#fff;border:none;border-radius:4px;cursor:pointer;min-height:40px;font-size:14px;}
            .invoice-history-pager-btn:disabled,.invoice-history-pager-btn.is-disabled{background-color:#ccc;cursor:not-allowed;}
            .invoice-history-pager-page{color:#374151;font-size:14px;padding:0 8px;}
            .invoice-cards{display:flex;flex-direction:column;gap:10px;}
            .invoice-card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 2px rgba(0,0,0,0.04);padding:12px;}
            .invoice-card-top{display:flex;align-items:center;justify-content:flex-start;gap:10px;margin-bottom:10px;}
            .invoice-card-actions{display:flex;align-items:center;gap:10px;}
            .invoice-card-grid{display:grid;gap:8px;}
            .invoice-kv{display:flex;gap:10px;align-items:flex-start;}
            .invoice-k{min-width:120px;font-size:12px;color:#64748b;line-height:1.4;}
            .invoice-v{flex:1;min-width:0;}
            @media (max-width: 1024px){
                #invoiceHistoryPage{padding:14px;border-radius:14px;}
                .invoice-history-toolbar{gap:14px;margin-bottom:14px;}
                .invoice-history-title{font-size:17px;}
                .invoice-search input{font-size:13px;padding:10px 34px 10px 38px;}
            }
            @media (min-width: 1025px){
                .invoice-history-toolbar{
                    grid-template-columns:auto 1fr minmax(200px,25%);
                    grid-template-areas:"title . search";
                    column-gap:12px;
                    row-gap:0;
                }
                .invoice-history-title{text-align:left;justify-self:start;}
            }
        </style>
        <div id="invoiceHistoryPage">
            <div class="invoice-history-toolbar">
                <h2 class="invoice-history-title">Invoice History</h2>
                <div class="invoice-search">
                    <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);pointer-events:none;color:#9ca3af;display:flex;" aria-hidden="true">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    </span>
                    <input type="text" id="invoiceHistorySearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search invoice number, vessel, client, POL, POD, LC, price type, chassis, amount…" aria-label="Search invoice history" />
                    <button type="button" id="invoiceHistorySearchClearBtn" class="invoice-search-clear" title="Clear search" aria-label="Clear search">×</button>
                </div>
            </div>
            <div id="invoiceHistoryTableWrap">
                <div id="invoiceHistoryTable" style="margin-top: 8px;">
                    <div class="invoice-history-empty"><strong>Loading</strong><div>Loading invoice history…</div></div>
                </div>
            </div>
        </div>
    """

    applyRoleBasedRestrictions()
    ensureSidebarPresent()

    val searchInput = document.getElementById("invoiceHistorySearchInput") as? HTMLInputElement
    searchInput?.addEventListener("input", { _: Event ->
        scheduleInvoiceHistorySearchDebounced()
    })
    document.getElementById("invoiceHistorySearchClearBtn")?.addEventListener("click", { _: Event ->
        searchInput?.value = ""
        invoiceHistoryPageZeroBased = 0
        loadInvoiceHistory(0)
    })

    setupInvoiceHistoryResizeListener()

    val wrap = document.getElementById("invoiceHistoryTableWrap")
    if (wrap != null && !wrap.hasAttribute("data-invoice-history-sort-delegation")) {
        wrap.setAttribute("data-invoice-history-sort-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-invoice-history-sort]") ?: return@addEventListener
            e.preventDefault()
            val field = btn.getAttribute("data-invoice-history-sort") ?: return@addEventListener
            toggleInvoiceHistorySort(field)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-invoice-history-edit-delegation")) {
        wrap.setAttribute("data-invoice-history-edit-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-invoice-history-edit]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val invKey = btn.getAttribute("data-invoice-number")?.trim() ?: return@addEventListener
            val row = invoiceHistoryCachedRows.firstOrNull { r ->
                invoiceHistoryCell(r, "invoiceNumber") == invKey
            } ?: return@addEventListener
            storeAndNavigateInvoiceHistoryEdit(row)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-invoice-history-pdf-delegation")) {
        wrap.setAttribute("data-invoice-history-pdf-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-invoice-history-pdf]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val invKey = btn.getAttribute("data-invoice-number")?.trim() ?: return@addEventListener
            if (invKey.isEmpty()) return@addEventListener
            downloadInvoiceHistoryPdf(invKey, btn as? HTMLButtonElement)
        })
    }

    loadInvoiceHistory()
}

private fun storeAndNavigateInvoiceHistoryEdit(row: dynamic) {
    val payload = js("{}")
    payload.invoiceNumber = invoiceHistoryCell(row, "invoiceNumber")
    payload.vessel = invoiceHistoryCell(row, "vessel")
    payload.clientName = invoiceHistoryCell(row, "clientName")
    payload.shippingDate = invoiceHistoryCell(row, "shippingDate")
    payload.pol = invoiceHistoryCell(row, "pol")
    payload.pod = invoiceHistoryCell(row, "pod")
    payload.lcNo = invoiceHistoryCell(row, "lcNo")
    payload.priceType = invoiceHistoryCell(row, "priceType")
    payload.bank = invoiceHistoryCell(row, "bank")
    payload.messages = invoiceHistoryCell(row, "messages")
    payload.chassis = invoiceHistoryCell(row, "chassis")
    payload.totalAmount = invoiceHistoryCell(row, "totalAmount")
    window.sessionStorage.setItem(INVOICE_HISTORY_EDIT_SESSION_KEY, JSON.stringify(payload))
    navigateToApp("/recreate-invoice")
}

private fun invoiceHistoryEditButtonHtml(invoiceNumber: String): String {
    if (invoiceNumber.isEmpty()) return ""
    val safeInv = escapeHtml(invoiceNumber)
    return """<button type="button" data-invoice-history-edit data-invoice-number="$safeInv" aria-label="Edit" title="Edit to Create Invoice"
        style="display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;min-width:36px;min-height:36px;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 4px rgba(76,201,255,0.30);padding:0;">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
        </svg>
    </button>"""
}

private fun invoiceHistoryPdfButtonHtml(invoiceNumber: String): String {
    if (invoiceNumber.isEmpty()) return ""
    val safeInv = escapeHtml(invoiceNumber)
    return """<button type="button" class="invoice-history-pdf-btn" data-invoice-history-pdf data-invoice-number="$safeInv"
        aria-label="Download PDF" title="Download PDF">
        <img src="invoice-history-pdf-btn.jpeg" alt="" width="36" height="36" draggable="false" />
    </button>"""
}


private fun downloadInvoiceHistoryPdf(invoiceNumber: String, btn: HTMLButtonElement?) {
    if (btn != null) {
        btn.disabled = true
        btn.style.opacity = "0.6"
    }
    MainScope().launch {
        try {
            val encoded = (js("encodeURIComponent") as (String) -> String)(invoiceNumber)
            val response = window.fetch(apiUrl("invoice-history/$encoded/pdf")).await()
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
                a.download = "invoice_${invoiceNumber.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.pdf"
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

private fun invoiceHistoryIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= INVOICE_HISTORY_COMPACT_MAX_WIDTH_PX
}

private fun setupInvoiceHistoryResizeListener() {
    if (document.getElementById("invoiceHistoryPage") == null) return
    invoiceHistoryLastCompactLayout = invoiceHistoryIsCompactLayout()

    val existing = window.asDynamic().__invoiceHistoryCompactResizeListener
    if (existing != null) {
        window.removeEventListener("resize", existing.unsafeCast<(Event) -> Unit>())
    }

    val resizeListener: (Event) -> Unit = { _: Event ->
        val prev = invoiceHistoryResizeDebounceHandle
        if (prev != null) window.clearTimeout(prev)
        invoiceHistoryResizeDebounceHandle = window.setTimeout({
            if (document.getElementById("invoiceHistoryPage") == null) return@setTimeout
            val compact = invoiceHistoryIsCompactLayout()
            if (invoiceHistoryLastCompactLayout != compact) {
                invoiceHistoryLastCompactLayout = compact
                if (invoiceHistoryCachedRows.isNotEmpty()) renderInvoiceHistoryTableFromCache()
            }
        }, 120)
    }
    window.asDynamic().__invoiceHistoryCompactResizeListener = resizeListener
    window.addEventListener("resize", resizeListener)
}

private fun invoiceHistoryDisplayCellHtml(row: dynamic, key: String): String {
    val raw = invoiceHistoryCell(row, key)
    if (raw.isEmpty()) return ""
    return when (key) {
        "chassis", "totalAmount" -> {
            val tokens = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
            formatHistoryListCollapsibleChipsHtml(tokens)
        }
        else -> formatHistoryListRectChipHtml(raw)
    }
}

private fun appendInvoiceHistoryTableRow(html: StringBuilder, row: dynamic) {
    html.append("<tr>")
    val invNum = invoiceHistoryCell(row, "invoiceNumber")
    html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${invoiceHistoryEditButtonHtml(invNum)}</td>""")
    html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${invoiceHistoryPdfButtonHtml(invNum)}</td>""")
    for (key in invoiceHistoryDisplayColumnKeys()) {
        html.append("""<td style="padding: 12px; vertical-align: top;">${invoiceHistoryDisplayCellHtml(row, key)}</td>""")
    }
    html.append("</tr>")
}

private fun appendInvoiceHistoryCard(html: StringBuilder, row: dynamic) {
    val invNum = invoiceHistoryCell(row, "invoiceNumber")
    html.append("""<div class="invoice-card">""")
    html.append(
        """
        <div class="invoice-card-top">
            <div class="invoice-card-actions">
                ${invoiceHistoryEditButtonHtml(invNum)}
                ${invoiceHistoryPdfButtonHtml(invNum)}
            </div>
        </div>
        """
    )
    html.append("""<div class="invoice-card-grid">""")
    for (key in invoiceHistoryDisplayColumnKeys()) {
        val cellHtml = invoiceHistoryDisplayCellHtml(row, key)
        if (cellHtml.isEmpty()) continue
        val label = escapeHtml(invoiceHistoryColumnLabel(key))
        html.append("""<div class="invoice-kv"><div class="invoice-k">$label</div><div class="invoice-v">$cellHtml</div></div>""")
    }
    html.append("""</div></div>""")
}

private fun scheduleInvoiceHistorySearchDebounced() {
    val prev = invoiceHistorySearchDebounceHandle
    if (prev != null) window.clearTimeout(prev)
    invoiceHistorySearchDebounceHandle = window.setTimeout({
        invoiceHistorySearchDebounceHandle = null
        invoiceHistoryPageZeroBased = 0
        loadInvoiceHistory(0)
    }, 420)
}

private fun applyInvoiceHistoryPageBody(body: dynamic) {
    invoiceHistoryServerMode = true
    val totalEl = js("body.totalElements")
    invoiceHistoryTotalElements = when (totalEl) {
        is Number -> totalEl.toLong()
        else -> totalEl?.toString()?.toLongOrNull() ?: 0L
    }
    val tp = js("body.totalPages")
    invoiceHistoryTotalPages = kotlin.math.max(
        1,
        when (tp) {
            is Number -> tp.toInt()
            else -> tp?.toString()?.toIntOrNull() ?: 1
        },
    )
    val num = js("body.page")
    invoiceHistoryPageZeroBased = when (num) {
        is Number -> num.toInt()
        else -> num?.toString()?.toIntOrNull() ?: 0
    }
    val sz = js("body.size")
    invoiceHistoryItemsPerPage = when (sz) {
        is Number -> sz.toInt()
        else -> sz?.toString()?.toIntOrNull() ?: AppConstants.DEFAULT_ITEMS_PER_PAGE
    }
    invoiceHistoryCachedRows = try {
        js("Array.from((body && body.content) ? body.content : [])").unsafeCast<Array<dynamic>>()
    } catch (_: dynamic) {
        emptyArray()
    }
}

private fun loadInvoiceHistory(page0: Int = invoiceHistoryPageZeroBased) {
    val tableHost = document.getElementById("invoiceHistoryTable") ?: return
    tableHost.innerHTML = """<div class="invoice-history-empty"><strong>Loading</strong><div>Loading invoice history…</div></div>"""

    val q = (document.getElementById("invoiceHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""
    invoiceHistoryActiveSearchQ = q
    invoiceHistoryPageZeroBased = page0.coerceAtLeast(0)
    val size = invoiceHistoryItemsPerPage.coerceAtLeast(1)
    val endpoint = if (q.isNotEmpty()) {
        val encQ = js("encodeURIComponent")(q).unsafeCast<String>()
        "invoice-history/page-search?q=$encQ&page=$invoiceHistoryPageZeroBased&size=$size"
    } else {
        "invoice-history/page?page=$invoiceHistoryPageZeroBased&size=$size"
    }

    MainScope().launch {
        ApiClient.get<dynamic>(endpoint).fold(
            onSuccess = { body ->
                if (body == null) {
                    ErrorHandler.showError("Empty invoice history response")
                    return@fold
                }
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    ErrorHandler.showError(err)
                    return@fold
                }
                applyInvoiceHistoryPageBody(body)
                renderInvoiceHistoryTableFromCache()
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load invoice history: $message")
                tableHost.innerHTML = """
                    <div class="invoice-history-empty" style="color:#b91c1c;">
                        <strong>Could not load</strong>
                        <div>Unable to load invoice history. Please reload and try again.</div>
                    </div>
                """
            },
        )
    }
}

/** Columns shown in the history table/cards. Bank/messages stay in API + edit payload, not displayed. */
private fun invoiceHistoryDisplayColumnKeys(): List<String> = listOf(
    "invoiceNumber", "vessel", "clientName", "shippingDate", "pol", "pod", "lcNo", "priceType", "chassis", "totalAmount",
)

private fun invoiceHistorySearchColumnKeys(): List<String> = invoiceHistoryDisplayColumnKeys()

private fun invoiceHistoryColumnLabel(key: String): String = when (key) {
    "invoiceNumber" -> "Invoice number"
    "vessel" -> "Vessel"
    "clientName" -> "Client name"
    "shippingDate" -> "Date"
    "pol" -> "POL"
    "pod" -> "POD"
    "lcNo" -> "LC"
    "priceType" -> "Price type"
    "bank" -> "Bank"
    "messages" -> "Messages"
    "chassis" -> "Chassis"
    "totalAmount" -> "Amount"
    else -> key
}

private fun invoiceHistoryCell(row: dynamic, key: String): String {
    val d = row
    val v: dynamic = when (key) {
        "invoiceNumber" -> d.invoiceNumber
        "vessel" -> d.vessel
        "clientName" -> d.clientName
        "shippingDate" -> d.shippingDate
        "pol" -> d.pol
        "pod" -> d.pod
        "lcNo" -> d.lcNo
        "priceType" -> d.priceType
        "bank" -> d.bank
        "messages" -> d.messages
        "chassis" -> d.chassis
        "totalAmount" -> d.totalAmount
        else -> null
    }
    if (v == null) return ""
    val undef = js("void 0")
    if (v === undef) return ""
    return v.toString().trim()
}

private fun invoiceHistoryRowMatchesQuery(row: dynamic, q: String): Boolean {
    val t = q.trim().lowercase()
    if (t.isEmpty()) return true
    for (key in invoiceHistorySearchColumnKeys()) {
        if (invoiceHistoryCell(row, key).lowercase().contains(t)) return true
    }
    return false
}

private fun compareInvoiceHistoryRows(a: dynamic, b: dynamic, field: String, asc: Boolean): Int {
    fun orient(c: Int): Int = if (asc) c else -c
    return when (field) {
        "shippingDate" -> {
            val sa = invoiceHistoryCell(a, field)
            val sb = invoiceHistoryCell(b, field)
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
        else -> {
            val sa = invoiceHistoryCell(a, field).lowercase()
            val sb = invoiceHistoryCell(b, field).lowercase()
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

private fun toggleInvoiceHistorySort(field: String) {
    if (invoiceHistorySortField == field) {
        invoiceHistorySortOrder = if (invoiceHistorySortOrder == "asc") "desc" else "asc"
    } else {
        invoiceHistorySortField = field
        invoiceHistorySortOrder = "desc"
    }
    renderInvoiceHistoryTableFromCache()
}

private fun appendInvoiceHistoryPager(html: StringBuilder) {
    if (!invoiceHistoryServerMode) return
    val totalPages = kotlin.math.max(1, invoiceHistoryTotalPages)
    val currentPage = invoiceHistoryPageZeroBased + 1
    if (totalPages <= 1 && invoiceHistoryTotalElements <= invoiceHistoryItemsPerPage) return
    val prevDisabled = currentPage <= 1
    val nextDisabled = currentPage >= totalPages
    html.append(
        """
        <div id="invoiceHistoryPager" class="invoice-history-pager">
            <div class="invoice-history-pager-meta">Page $currentPage of $totalPages · $invoiceHistoryTotalElements row(s)</div>
            <div class="invoice-history-pager-btns">
                <button type="button" id="invoiceHistoryPrevPage" class="invoice-history-pager-btn${if (prevDisabled) " is-disabled" else ""}" ${if (prevDisabled) "disabled" else ""}>Previous</button>
                <span class="invoice-history-pager-page">Page $currentPage of $totalPages</span>
                <button type="button" id="invoiceHistoryNextPage" class="invoice-history-pager-btn${if (nextDisabled) " is-disabled" else ""}" ${if (nextDisabled) "disabled" else ""}>Next</button>
            </div>
        </div>
        """
    )
}

private fun wireInvoiceHistoryPager() {
    document.getElementById("invoiceHistoryPrevPage")?.addEventListener("click", { _: Event ->
        if (invoiceHistoryPageZeroBased > 0) loadInvoiceHistory(invoiceHistoryPageZeroBased - 1)
    })
    document.getElementById("invoiceHistoryNextPage")?.addEventListener("click", { _: Event ->
        if (invoiceHistoryPageZeroBased + 1 < invoiceHistoryTotalPages) {
            loadInvoiceHistory(invoiceHistoryPageZeroBased + 1)
        }
    })
}

private fun renderInvoiceHistoryTableFromCache() {
    val tableHost = document.getElementById("invoiceHistoryTable") ?: return
    val q = if (invoiceHistoryServerMode) {
        invoiceHistoryActiveSearchQ
    } else {
        (document.getElementById("invoiceHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""
    }

    if (invoiceHistoryCachedRows.isEmpty()) {
        val emptyHtml = if (q.isNotEmpty()) {
            """<div class="invoice-history-empty"><strong>No matches</strong><div>No rows match your search.</div></div>"""
        } else {
            """<div class="invoice-history-empty"><strong>No history yet</strong><div>No invoice history records yet.</div></div>"""
        }
        val html = StringBuilder(emptyHtml)
        appendInvoiceHistoryPager(html)
        tableHost.innerHTML = html.toString()
        wireInvoiceHistoryPager()
        return
    }

    var rows = if (invoiceHistoryServerMode) {
        invoiceHistoryCachedRows
    } else {
        invoiceHistoryCachedRows.filter { invoiceHistoryRowMatchesQuery(it, q) }.toTypedArray()
    }

    if (rows.isEmpty()) {
        tableHost.innerHTML = """<div class="invoice-history-empty"><strong>No matches</strong><div>No rows match your search.</div></div>"""
        return
    }

    val comparator = Comparator<dynamic> { a, b ->
        compareInvoiceHistoryRows(a, b, invoiceHistorySortField, invoiceHistorySortOrder == "asc")
    }
    rows = rows.sortedWith(comparator).toTypedArray()

    val compact = invoiceHistoryIsCompactLayout()
    invoiceHistoryLastCompactLayout = compact
    val html = StringBuilder()

    if (!compact) {
        val colCountInv = 2 + invoiceHistoryDisplayColumnKeys().size
        val invoiceHistoryColWidthsPx = listOf(
            56, // Edit
            56, // PDF
            132, // Invoice number
            96, // Vessel
            132, // Client name
            104, // Date
            96, // POL
            104, // POD
            72, // LC
            96, // Price type
            152, // Chassis
            116, // Amount
        )
        html.append(
            """<div class="invoice-history-table-shell"><table class="purchase-list-table" style="width:100%;border-collapse:collapse;table-layout:fixed;">""" +
                htmlTableColgroupFixedWidthsPx(colCountInv, invoiceHistoryColWidthsPx) +
                """<thead><tr style="background-color:#f8f9fa;">"""
        )
        html.append("""<th style="padding:12px;text-align:center;border-bottom:1px solid #dee2e6;width:56px;">Edit</th>""")
        html.append("""<th style="padding:12px;text-align:center;border-bottom:1px solid #dee2e6;width:56px;">PDF</th>""")
        for (key in invoiceHistoryDisplayColumnKeys()) {
            val label = escapeHtml(invoiceHistoryColumnLabel(key))
            val isActive = invoiceHistorySortField == key
            val sortOrder = if (isActive) invoiceHistorySortOrder else "desc"
            val tooltipRaw = when {
                !isActive -> "Sort by ${invoiceHistoryColumnLabel(key)}"
                sortOrder == "asc" -> "Sorted ascending (click for descending)"
                else -> "Sorted descending (click for ascending)"
            }
            val tooltip = escapeHtml(tooltipRaw)
            html.append(
                """
                <th style="padding:12px;text-align:left;border-bottom:1px solid #dee2e6;">
                    <button type="button" data-invoice-history-sort="$key" title="$tooltip" style="background:none;border:none;cursor:pointer;font-weight:700;color:#0f172a;padding:0;display:inline-flex;align-items:center;gap:6px;">
                        <span>$label</span><span style="font-size:14px;color:#64748b;">↕</span>
                    </button>
                </th>
                """
            )
        }
        html.append("</tr></thead><tbody id='invoiceHistoryTableBody'>")
        for (row in rows) {
            appendInvoiceHistoryTableRow(html, row)
        }
        html.append("</tbody></table></div>")
    } else {
        html.append("""<div id="invoiceHistoryTableBody" class="invoice-cards">""")
        for (row in rows) {
            appendInvoiceHistoryCard(html, row)
        }
        html.append("</div>")
    }

    appendInvoiceHistoryPager(html)
    tableHost.innerHTML = html.toString()
    wireInvoiceHistoryPager()
}

