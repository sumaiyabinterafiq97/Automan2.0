package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
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
private val invoiceHistorySelectedIds: MutableSet<String> = mutableSetOf()

fun showInvoiceHistoryPage() {
    window.location.hash = "#/invoice-history"
    val content = document.getElementById("content") ?: return
    invoiceHistoryCachedRows = emptyArray()
    invoiceHistorySortField = "invoiceNumber"
    invoiceHistorySortOrder = "desc"
    invoiceHistorySelectedIds.clear()

    content.innerHTML = """
        <div id="invoiceHistoryPage" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; background: #fafbfc;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; margin-bottom: 20px;">
                <h2 style="margin: 0;">Invoice History</h2>
                <div style="display: flex; flex-direction: column; align-items: stretch; gap: 10px; flex: 1; min-width: 0; max-width: 640px;">
                    <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                        <div style="position: relative; flex: 1; display: flex; align-items: center; min-width: 0; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.06); transition: all .3s ease;">
                            <span style="position: absolute; left: 16px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #9ca3af; display: flex;" aria-hidden="true">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                            </span>
                            <input type="text" id="invoiceHistorySearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search invoice number, vessel, client, POL, POD, LC, price type, chassis, amount, bank…" aria-label="Search invoice history" style="width: 100%; box-sizing: border-box; padding: 12px 40px 12px 44px; border: none; font-size: 14px; background: transparent; border-radius: 999px; outline: none; transition: all .2s ease;" />
                            <button type="button" id="invoiceHistorySearchClearBtn" title="Clear search" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: transparent; color: #9ca3af; cursor: pointer; font-size: 20px; line-height: 1; padding: 4px 8px; border-radius: 8px;">×</button>
                        </div>
                    </div>
                </div>
            </div>
            <div id="invoiceHistoryTableWrap">
                <div id="invoiceHistoryTable" style="margin-top: 8px;">
                    <div style="text-align: center; color: #666; padding: 40px;">Loading invoice history…</div>
                </div>
            </div>
        </div>
    """

    applyRoleBasedRestrictions()
    ensureSidebarPresent()

    val searchInput = document.getElementById("invoiceHistorySearchInput") as? HTMLInputElement
    searchInput?.addEventListener("input", { _: Event ->
        renderInvoiceHistoryTableFromCache()
    })
    document.getElementById("invoiceHistorySearchClearBtn")?.addEventListener("click", { _: Event ->
        searchInput?.value = ""
        renderInvoiceHistoryTableFromCache()
    })

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
    window.location.hash = "#/recreate-invoice"
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

private fun loadInvoiceHistory() {
    val tableHost = document.getElementById("invoiceHistoryTable") ?: return
    tableHost.innerHTML = """<div style="text-align: center; color: #666; padding: 40px;">Loading invoice history…</div>"""

    MainScope().launch {
        ApiClient.get<Array<dynamic>>("invoice-history").fold(
            onSuccess = { rows ->
                invoiceHistoryCachedRows = rows
                renderInvoiceHistoryTableFromCache()
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load invoice history: $message")
                tableHost.innerHTML = """<div style="text-align: center; color: #c00; padding: 40px;">Could not load invoice history.</div>"""
            },
        )
    }
}

private fun invoiceHistoryDisplayColumnKeys(): List<String> = listOf(
    "invoiceNumber", "vessel", "clientName", "shippingDate", "pol", "pod", "lcNo", "priceType", "bank", "messages", "chassis", "totalAmount",
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

private fun renderInvoiceHistoryTableFromCache() {
    val tableHost = document.getElementById("invoiceHistoryTable") ?: return
    val q = (document.getElementById("invoiceHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""

    if (invoiceHistoryCachedRows.isEmpty()) {
        tableHost.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No invoice history records yet.
            </div>
        """
        return
    }

    var rows = invoiceHistoryCachedRows.filter { invoiceHistoryRowMatchesQuery(it, q) }.toTypedArray()

    if (rows.isEmpty()) {
        tableHost.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No rows match your search.
            </div>
        """
        return
    }

    val comparator = Comparator<dynamic> { a, b ->
        compareInvoiceHistoryRows(a, b, invoiceHistorySortField, invoiceHistorySortOrder == "asc")
    }
    rows = rows.sortedWith(comparator).toTypedArray()

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
        220, // Bank
        128, // Messages
        152, // Chassis
        116, // Amount
    )
    val html = StringBuilder()
    html.append("""<div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);"><table class="purchase-list-table" style="width: 100%; border-collapse: collapse; table-layout: fixed;">${htmlTableColgroupFixedWidthsPx(colCountInv, invoiceHistoryColWidthsPx)}<thead><tr style="background-color: #f8f9fa;">""")
    html.append("""<th style="padding: 12px; text-align: center; border-bottom: 1px solid #dee2e6; width: 56px;">Edit</th>""")
    html.append("""<th style="padding: 12px; text-align: center; border-bottom: 1px solid #dee2e6; width: 56px;">PDF</th>""")
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
        html.append("""
            <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                <button type="button" data-invoice-history-sort="$key" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                    <span>$label</span><span style="font-size: 14px;">↕</span>
                </button>
            </th>
        """)
    }
    html.append("</tr></thead><tbody>")

    for (row in rows) {
        html.append("<tr>")
        val invNum = invoiceHistoryCell(row, "invoiceNumber")
        html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${invoiceHistoryEditButtonHtml(invNum)}</td>""")
        html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${invoiceHistoryPdfButtonHtml(invNum)}</td>""")
        for (key in invoiceHistoryDisplayColumnKeys()) {
            val raw = invoiceHistoryCell(row, key)
            val cellHtml = when {
                raw.isEmpty() -> ""
                key == "chassis" || key == "totalAmount" -> {
                    val tokens = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
                    formatCollapsibleChipsHtml(tokens)
                }
                key == "bank" -> formatClientMapBankInfoCellHtml(raw)
                else -> formatPurchaseListNeutralChipHtml(raw)
            }
            html.append("""<td style="padding: 12px; vertical-align: top;">$cellHtml</td>""")
        }
        html.append("</tr>")
    }
    html.append("</tbody></table></div>")

    tableHost.innerHTML = html.toString()
}

