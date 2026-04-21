package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private var rixoHistoryCachedRows: Array<dynamic> = emptyArray()
private var rixoHistorySortField: String = "buyingDate"
private var rixoHistorySortOrder: String = "desc"

fun showRixoHistoryPage() {
    window.location.hash = "#/rixo-history"
    val content = document.getElementById("content") ?: return
    rixoHistoryCachedRows = emptyArray()
    rixoHistorySortField = "buyingDate"
    rixoHistorySortOrder = "desc"

    content.innerHTML = """
        <div id="rixoHistoryPage" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; background: #fafbfc;">
            <div style="display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; margin-bottom: 20px;">
                <h2 style="margin: 0;">Rixo History</h2>
                <div style="display: flex; flex-direction: column; align-items: stretch; gap: 10px; flex: 1; min-width: 0; max-width: 640px;">
                    <div style="display: flex; align-items: center; gap: 10px; width: 100%;">
                        <div style="position: relative; flex: 1; display: flex; align-items: center; min-width: 0; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.06); transition: all .3s ease;">
                            <span style="position: absolute; left: 16px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #9ca3af; display: flex;" aria-hidden="true">
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                            </span>
                            <input type="text" id="rixoHistorySearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search buying date, Rixo company, message, chassis…" aria-label="Search Rixo history" style="width: 100%; box-sizing: border-box; padding: 12px 40px 12px 44px; border: none; font-size: 14px; background: transparent; border-radius: 999px; outline: none; transition: all .2s ease;" />
                            <button type="button" id="rixoHistorySearchClearBtn" title="Clear search" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: transparent; color: #9ca3af; cursor: pointer; font-size: 20px; line-height: 1; padding: 4px 8px; border-radius: 8px;">×</button>
                        </div>
                    </div>
                </div>
            </div>
            <div id="rixoHistoryTableWrap">
                <div id="rixoHistoryTable" style="margin-top: 8px;">
                    <div style="text-align: center; color: #666; padding: 40px;">Loading Rixo history…</div>
                </div>
            </div>
        </div>
    """

    applyRoleBasedRestrictions()
    ensureSidebarPresent()

    val searchInput = document.getElementById("rixoHistorySearchInput") as? HTMLInputElement
    searchInput?.addEventListener("input", { _: Event ->
        renderRixoHistoryTableFromCache()
    })
    document.getElementById("rixoHistorySearchClearBtn")?.addEventListener("click", { _: Event ->
        searchInput?.value = ""
        renderRixoHistoryTableFromCache()
    })

    val wrap = document.getElementById("rixoHistoryTableWrap")
    if (wrap != null && !wrap.hasAttribute("data-rixo-history-sort-delegation")) {
        wrap.setAttribute("data-rixo-history-sort-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? HTMLElement ?: return@addEventListener
            val btn = target.closest("button[data-rixo-history-sort]") as? HTMLElement ?: return@addEventListener
            e.preventDefault()
            val field = btn.getAttribute("data-rixo-history-sort") ?: return@addEventListener
            toggleRixoHistorySort(field)
        })
    }

    loadRixoHistory()
}

private fun loadRixoHistory() {
    val tableHost = document.getElementById("rixoHistoryTable") ?: return
    tableHost.innerHTML = """<div style="text-align: center; color: #666; padding: 40px;">Loading Rixo history…</div>"""

    MainScope().launch {
        ApiClient.get<Array<dynamic>>("rixo-history").fold(
            onSuccess = { rows ->
                rixoHistoryCachedRows = rows
                renderRixoHistoryTableFromCache()
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load Rixo history: $message")
                tableHost.innerHTML = """<div style="text-align: center; color: #c00; padding: 40px;">Could not load Rixo history.</div>"""
            },
        )
    }
}

private fun rixoHistoryDisplayColumnKeys(): List<String> = listOf(
    "buyingDate", "rixoCompany", "message", "chassis",
)

private fun rixoHistorySearchColumnKeys(): List<String> =
    listOf("id") + rixoHistoryDisplayColumnKeys()

private fun rixoHistoryColumnLabel(key: String): String = when (key) {
    "buyingDate" -> "Buying date"
    "rixoCompany" -> "Rixo company"
    "message" -> "Message"
    "chassis" -> "Chassis"
    else -> key
}

private fun rixoHistoryCell(row: dynamic, key: String): String {
    val d = row
    val v: dynamic = when (key) {
        "id" -> d.id
        "buyingDate" -> d.buyingDate
        "rixoCompany" -> d.rixoCompany
        "message" -> d.message
        "chassis" -> d.chassis
        else -> null
    }
    if (v == null) return ""
    val undef = js("void 0")
    if (v === undef) return ""
    return v.toString().trim()
}

private fun rixoHistoryRowMatchesQuery(row: dynamic, q: String): Boolean {
    val t = q.trim().lowercase()
    if (t.isEmpty()) return true
    for (key in rixoHistorySearchColumnKeys()) {
        if (rixoHistoryCell(row, key).lowercase().contains(t)) return true
    }
    return false
}

private fun compareRixoHistoryRows(a: dynamic, b: dynamic, field: String, asc: Boolean): Int {
    fun orient(c: Int): Int = if (asc) c else -c
    return when (field) {
        "buyingDate" -> {
            val sa = rixoHistoryCell(a, "buyingDate")
            val sb = rixoHistoryCell(b, "buyingDate")
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
            val sa = rixoHistoryCell(a, field).lowercase()
            val sb = rixoHistoryCell(b, field).lowercase()
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

private fun toggleRixoHistorySort(field: String) {
    if (rixoHistorySortField == field) {
        rixoHistorySortOrder = if (rixoHistorySortOrder == "asc") "desc" else "asc"
    } else {
        rixoHistorySortField = field
        rixoHistorySortOrder = "desc"
    }
    renderRixoHistoryTableFromCache()
}

private fun renderRixoHistoryTableFromCache() {
    val tableHost = document.getElementById("rixoHistoryTable") ?: return
    val q = (document.getElementById("rixoHistorySearchInput") as? HTMLInputElement)?.value?.trim() ?: ""

    if (rixoHistoryCachedRows.isEmpty()) {
        tableHost.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No Rixo history records yet.
            </div>
        """
        return
    }

    var rows = rixoHistoryCachedRows.filter { rixoHistoryRowMatchesQuery(it, q) }.toTypedArray()

    if (rows.isEmpty()) {
        tableHost.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No rows match your search.
            </div>
        """
        return
    }

    val comparator = Comparator<dynamic> { a, b ->
        compareRixoHistoryRows(a, b, rixoHistorySortField, rixoHistorySortOrder == "asc")
    }
    rows = rows.sortedWith(comparator).toTypedArray()

    val html = StringBuilder()
    html.append("""<div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);"><table class="purchase-list-table" style="width: 100%; border-collapse: collapse;"><thead><tr style="background-color: #f8f9fa;">""")
    for (key in rixoHistoryDisplayColumnKeys()) {
        val label = escapeHtml(rixoHistoryColumnLabel(key))
        val isActive = rixoHistorySortField == key
        val sortOrder = if (isActive) rixoHistorySortOrder else "desc"
        val tooltipRaw = when {
            !isActive -> "Sort by ${rixoHistoryColumnLabel(key)}"
            sortOrder == "asc" -> "Sorted ascending (click for descending)"
            else -> "Sorted descending (click for ascending)"
        }
        val tooltip = escapeHtml(tooltipRaw)
        html.append("""
            <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                <button type="button" data-rixo-history-sort="$key" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                    <span>$label</span><span style="font-size: 14px;">↕</span>
                </button>
            </th>
        """)
    }
    html.append("</tr></thead><tbody>")

    for (row in rows) {
        html.append("<tr>")
        for (key in rixoHistoryDisplayColumnKeys()) {
            val raw = rixoHistoryCell(row, key)
            val cellHtml = when {
                raw.isEmpty() -> ""
                key == "chassis" -> formatInvoiceHistoryChassisChipsHtml(raw)
                else -> formatPurchaseListNeutralChipHtml(raw)
            }
            html.append("""<td style="padding: 12px; vertical-align: top;">$cellHtml</td>""")
        }
        html.append("</tr>")
    }
    html.append("</tbody></table></div>")

    tableHost.innerHTML = html.toString()
}
