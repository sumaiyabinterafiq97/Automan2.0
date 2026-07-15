package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.JSON
import org.w3c.dom.*
import org.w3c.dom.events.Event
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit

/** sessionStorage key: JSON payload when opening Rixo Request Generator from history Edit. */
const val RIXO_HISTORY_EDIT_SESSION_KEY = "rixoHistoryEditPayload"

private var rixoHistoryCachedRows: Array<dynamic> = emptyArray()
private var rixoHistorySortField: String = "buyingDate"
private var rixoHistorySortOrder: String = "desc"
private val rixoHistorySelectedIds: MutableSet<String> = mutableSetOf()
private var rixoHistoryResizeDebounceHandle: Int? = null

private const val RIXO_HISTORY_COMPACT_MAX_WIDTH_PX = 860

fun showRixoHistoryPage() {
    navigateToApp("/rixo-history")
    val content = document.getElementById("content") ?: return
    rixoHistoryCachedRows = emptyArray()
    rixoHistorySortField = "buyingDate"
    rixoHistorySortOrder = "desc"
    rixoHistorySelectedIds.clear()

    content.innerHTML = """
        <style>
            #rixoHistoryPage{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;}
            .rixo-history-toolbar{
                display:grid;
                grid-template-columns:1fr;
                grid-template-areas:
                    "title"
                    "search"
                    "confirm";
                gap:12px;
                margin-bottom:16px;
                align-items:center;
            }
            .rixo-history-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;grid-area:title;text-align:center;}
            .rixo-search{grid-area:search;width:100%;}
            #rixoConfirmSelectedBtn{grid-area:confirm;justify-self:end;}
            .rixo-primary-btn{padding:10px 14px;border:1px solid #0f766e;border-radius:10px;background:linear-gradient(135deg,#14b8a6,#0f766e);color:#fff;font-weight:700;cursor:pointer;box-shadow:0 2px 10px rgba(15,118,110,0.22);min-height:40px;white-space:nowrap;flex-shrink:0;}
            .rixo-primary-btn:disabled{opacity:.6;cursor:not-allowed;box-shadow:none;}
            .rixo-search{position:relative;flex:1;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
            .rixo-search input{width:100%;box-sizing:border-box;padding:11px 36px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
            .rixo-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;line-height:1;padding:6px 8px;border-radius:10px;min-height:36px;min-width:36px;}
            .rixo-search-clear:hover{background:#f3f4f6;color:#111827;}
            .rixo-history-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
            table.purchase-list-table thead th{position:sticky;top:0;z-index:1;}
            .rixo-history-empty{display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
            .rixo-history-empty strong{color:#0f172a;}

            /* Card list (compact) */
            .rixo-cards{display:flex;flex-direction:column;gap:10px;}
            .rixo-card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 2px rgba(0,0,0,0.04);padding:12px;}
            .rixo-card-top{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;}
            .rixo-card-actions{display:flex;align-items:center;gap:10px;}
            .rixo-card-select{display:flex;align-items:center;gap:10px;}
            .rixo-card-grid{display:grid;grid-template-columns:1fr;gap:8px;}
            .rixo-kv{display:flex;gap:10px;align-items:flex-start;}
            .rixo-k{min-width:132px;font-size:12px;color:#64748b;line-height:1.4;}
            .rixo-v{flex:1;min-width:0;}
            .rixo-divider{height:1px;background:#eef2f7;margin:10px 0;}
            .rixo-checkbox{width:18px;height:18px;cursor:pointer;}

            @media (max-width: 1024px){
                #rixoHistoryPage{padding:14px;border-radius:14px;}
                .rixo-history-toolbar{gap:14px;margin-bottom:14px;}
                .rixo-history-title{font-size:17px;}
                .rixo-search input{font-size:13px;padding:10px 34px 10px 38px;}
            }
            @media (min-width: 1025px){
                .rixo-history-toolbar{
                    grid-template-columns:auto 1fr minmax(200px,25%) auto;
                    grid-template-areas:"title . search confirm";
                    column-gap:12px;
                    row-gap:0;
                    align-items:center;
                }
                .rixo-history-title{text-align:left;justify-self:start;}
                .rixo-search{width:100%;max-width:100%;justify-self:stretch;}
                #rixoConfirmSelectedBtn{justify-self:end;}
            }
        </style>

        <div id="rixoHistoryPage">
            <div class="rixo-history-toolbar">
                <h2 class="rixo-history-title">Rixo History</h2>
                <div class="rixo-search">
                    <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);pointer-events:none;color:#9ca3af;display:flex;" aria-hidden="true">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    </span>
                    <input type="text" id="rixoHistorySearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Search buying date, Rixo company, message, chassis…" aria-label="Search Rixo history" />
                    <button type="button" id="rixoHistorySearchClearBtn" class="rixo-search-clear" title="Clear search" aria-label="Clear search">×</button>
                </div>
                <button id="rixoConfirmSelectedBtn" type="button" class="rixo-primary-btn" disabled>
                    Rixo Confirmed
                </button>
            </div>
            <div id="rixoHistoryTableWrap">
                <div id="rixoHistoryTable" style="margin-top: 8px;">
                    <div class="rixo-history-empty"><strong>Loading</strong><div>Loading Rixo history…</div></div>
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

    setupRixoHistoryResizeListener()

    val wrap = document.getElementById("rixoHistoryTableWrap")
    if (wrap != null && !wrap.hasAttribute("data-rixo-history-sort-delegation")) {
        wrap.setAttribute("data-rixo-history-sort-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            // Use Element (not HTMLElement): clicks on SVG/path inside buttons are not HTMLElements.
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-rixo-history-sort]") ?: return@addEventListener
            e.preventDefault()
            val field = btn.getAttribute("data-rixo-history-sort") ?: return@addEventListener
            toggleRixoHistorySort(field)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-rixo-history-edit-delegation")) {
        wrap.setAttribute("data-rixo-history-edit-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-rixo-history-edit]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val hid = btn.getAttribute("data-history-id")?.trim() ?: return@addEventListener
            val row = rixoHistoryCachedRows.firstOrNull { r -> rixoHistoryRowIdString(r) == hid }
                ?: return@addEventListener
            storeAndNavigateRixoHistoryEdit(row)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-rixo-history-pdf-delegation")) {
        wrap.setAttribute("data-rixo-history-pdf-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val btn = target.closest("button[data-rixo-history-pdf]") ?: return@addEventListener
            e.preventDefault()
            e.stopPropagation()
            val hid = btn.getAttribute("data-history-id")?.trim() ?: return@addEventListener
            val row = rixoHistoryCachedRows.firstOrNull { r -> rixoHistoryRowIdString(r) == hid }
                ?: return@addEventListener
            downloadRixoHistoryPdf(row, btn as? HTMLButtonElement)
        })
    }
    if (wrap != null && !wrap.hasAttribute("data-rixo-history-selection-delegation")) {
        wrap.setAttribute("data-rixo-history-selection-delegation", "true")
        wrap.addEventListener("click", { e: Event ->
            val target = e.target as? Element ?: return@addEventListener
            val rowCb = target.closest("input[data-rixo-history-select]") as? HTMLInputElement
            if (rowCb != null) {
                val hid = rowCb.getAttribute("data-history-id")?.trim().orEmpty()
                if (hid.isNotEmpty()) {
                    if (rowCb.checked) rixoHistorySelectedIds.add(hid) else rixoHistorySelectedIds.remove(hid)
                    updateRixoHistorySelectionUi()
                }
                return@addEventListener
            }
        })
    }

    document.getElementById("rixoConfirmSelectedBtn")?.addEventListener("click", { _: Event ->
        confirmSelectedRixoHistoryRows()
    })

    loadRixoHistory()
}

private fun rixoHistoryIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= RIXO_HISTORY_COMPACT_MAX_WIDTH_PX
}

private fun setupRixoHistoryResizeListener() {
    val page = document.getElementById("rixoHistoryPage") ?: return
    if (page.hasAttribute("data-rixo-history-resize")) return
    page.setAttribute("data-rixo-history-resize", "true")
    window.addEventListener("resize", { _: Event ->
        val prev = rixoHistoryResizeDebounceHandle
        if (prev != null) window.clearTimeout(prev)
        rixoHistoryResizeDebounceHandle = window.setTimeout({
            if (document.getElementById("rixoHistoryPage") == null) return@setTimeout
            if (rixoHistoryCachedRows.isNotEmpty()) renderRixoHistoryTableFromCache()
        }, 120)
    })
}

private fun updateRixoHistorySelectionUi() {
    val confirmBtn = document.getElementById("rixoConfirmSelectedBtn") as? HTMLButtonElement
    if (confirmBtn != null) {
        val hasAny = rixoHistorySelectedIds.isNotEmpty()
        confirmBtn.disabled = !hasAny
        confirmBtn.style.opacity = if (hasAny) "1" else "0.6"
        confirmBtn.style.cursor = if (hasAny) "pointer" else "not-allowed"
        confirmBtn.textContent = if (hasAny) "Rixo Confirmed (${rixoHistorySelectedIds.size})" else "Rixo Confirmed"
    }
}

private fun confirmSelectedRixoHistoryRows() {
    if (rixoHistorySelectedIds.isEmpty()) {
        showMessage("Select at least one history row.", "warning")
        return
    }
    val ok = window.confirm("Mark all cars under ${rixoHistorySelectedIds.size} selected Rixo history row(s) as Rixo Confirmed?")
    if (!ok) return

    val btn = document.getElementById("rixoConfirmSelectedBtn") as? HTMLButtonElement
    btn?.disabled = true
    val prevLabel = btn?.textContent ?: "Rixo Confirmed"
    btn?.textContent = "Sending..."

    MainScope().launch {
        val idLongs = rixoHistorySelectedIds.mapNotNull { it.toLongOrNull() }.distinct()
        if (idLongs.isEmpty()) {
            showMessage("Could not read selected row ids. Reload the page and try again.", "error")
            btn?.textContent = prevLabel
            btn?.disabled = false
            updateRixoHistorySelectionUi()
            return@launch
        }
        // Plain JSON array of numbers — avoids Kotlin Long/JS interop breaking JSON.stringify(historyIds).
        val bodyJson = "{\"historyIds\":[" + idLongs.joinToString(",") + "]}"
        val body = JSON.parse<dynamic>(bodyJson)
        ApiClient.post<dynamic>("rixo-history/confirm-selected", body).fold(
            onSuccess = { data ->
                // JSON.parse yields a plain object; avoid .asDynamic() (not a function on that prototype in JS IR).
                val d: dynamic = (data as Any).unsafeCast<dynamic>()
                val updated = d.updatedPurchases?.toString() ?: "0"
                val rows = d.selectedRows?.toString() ?: "0"
                when {
                    rows == "0" || rows.isEmpty() ->
                        showMessage("No history rows were marked as Rixo Confirmed (server got no ids). Try again.", "warning")
                    updated == "0" ->
                        showMessage(
                            "Selected $rows Rixo history row(s), but no purchase rows were updated. " +
                                "Chassis in history must match purchase chassis (exact or prefix before \"-\"), " +
                                "or purchases may already be Rixo Confirmed.",
                            "warning",
                        )
                    else ->
                        showMessage(
                            "Rixo Confirmed for $updated purchase row(s) from $rows selected history row(s).",
                            "success",
                        )
                }
                rixoHistorySelectedIds.clear()
                loadRixoHistory()
            },
            onError = { message, _ ->
                showMessage("Failed to mark Rixo Confirmed: $message", "error")
            },
        )
        btn?.textContent = prevLabel
        btn?.disabled = false
        updateRixoHistorySelectionUi()
    }
}



private fun storeAndNavigateRixoHistoryEdit(row: dynamic) {
    val payload = js("{}")
    payload.buyingDate = rixoHistoryCell(row, "buyingDate")
    payload.rixoCompany = rixoHistoryCell(row, "rixoCompany")
    payload.message = rixoHistoryCell(row, "message")
    payload.chassis = rixoHistoryCell(row, "chassis")
    payload.historyId = rixoHistoryCell(row, "id")
    payload.hasBookingRequested = rixoHistoryHasBookingRequestedFromRow(row)
    window.sessionStorage.setItem(RIXO_HISTORY_EDIT_SESSION_KEY, JSON.stringify(payload))
    navigateToApp("/rixo-updater")
}

private fun rixoHistoryEditButtonHtml(historyId: String): String {
    if (historyId.isEmpty()) return ""
    val safeId = escapeHtml(historyId)
    return """<button type="button" data-rixo-history-edit data-history-id="$safeId" aria-label="Edit" title="Edit to Rixo Request Generator"
        style="display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;min-width:36px;min-height:36px;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 4px rgba(76,201,255,0.30);padding:0;">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
        </svg>
    </button>"""
}

private fun rixoHistoryPdfButtonHtml(historyId: String): String {
    if (historyId.isEmpty()) return ""
    val safeId = escapeHtml(historyId)
    return """<button type="button" class="invoice-history-pdf-btn" data-rixo-history-pdf data-history-id="$safeId"
        aria-label="Download Rixo transport PDF" title="Download PDF">
        <img src="invoice-history-pdf-btn.jpeg" alt="" width="36" height="36" draggable="false" />
    </button>"""
}

private const val RIXO_HISTORY_UNDEFINED_COMPANY_VALUE = "__RIXO_COMPANY_UNDEFINED__"
private const val RIXO_HISTORY_UNDEFINED_COMPANY_LABEL = "Undefined"

private const val RIXO_HISTORY_DEFAULT_HEAD_MESSAGE = """いつもお世話になっております。
下記の車両の陸送手配をお願いいたします。"""

private const val RIXO_HISTORY_DEFAULT_FOOTER_MESSAGE = """※港や船での盗難が多発の為、スペアキーやリモコンキーが車内に
ありましたら弊社まで郵送していただけると助かります。"""

private const val RIXO_HISTORY_DEFAULT_CONTACT_DETAILS = """担当：芽紋 080-3918-1478
FAX: 047-711-0409
有限会社メモン"""

private fun parseRixoHistoryChassisTokens(chassisRaw: String): List<String> {
    if (chassisRaw.isBlank()) return emptyList()
    return chassisRaw.split(';', ',', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
}

private fun rixoHistoryChassisHead(purchaseChassis: String): String =
    purchaseChassis.trim().substringBefore('-').trim()

private fun rixoHistoryChassisTokenMatchesPurchase(token: String, purchaseChassis: String): Boolean {
    val t = token.trim()
    val ch = purchaseChassis.trim()
    if (t.isEmpty() || ch.isEmpty()) return false
    if (ch.equals(t, ignoreCase = true)) return true
    if (rixoHistoryChassisHead(ch).equals(t, ignoreCase = true)) return true
    if (ch.startsWith(t, ignoreCase = true) &&
        (ch.length == t.length || ch.getOrNull(t.length) == '-')
    ) {
        return true
    }
    return false
}

private fun rixoHistoryPurchaseRixoCompanyRaw(@Suppress("UNUSED_PARAMETER") purchase: dynamic): String =
    js("purchase.rixoCompany")?.toString()?.trim() ?: ""

private fun rixoHistoryPurchaseMatchesRowFilters(
    purchase: dynamic,
    historyBuyingDate: String,
    historyRixoCompany: String,
): Boolean {
    val wantDate = historyBuyingDate.isNotBlank()
    val pDateRaw = js("purchase.date")?.toString()?.trim() ?: ""
    val pRixoRaw = rixoHistoryPurchaseRixoCompanyRaw(purchase)
    if (wantDate) {
        val a = toIsoFromLabel(historyBuyingDate).trim()
        val b = toIsoFromLabel(pDateRaw).trim()
        if (a.isEmpty() || b.isEmpty() || !a.equals(b, ignoreCase = true)) return false
    }
    val hist = historyRixoCompany.trim()
    val matchUndefinedBucket =
        hist.isEmpty() ||
            hist == RIXO_HISTORY_UNDEFINED_COMPANY_VALUE ||
            hist.equals(RIXO_HISTORY_UNDEFINED_COMPANY_LABEL, ignoreCase = true)
    return if (matchUndefinedBucket) {
        pRixoRaw.isEmpty()
    } else {
        hist.equals(pRixoRaw, ignoreCase = true)
    }
}

private fun persistableRixoCompanyFromHistory(raw: String): String {
    val t = raw.trim()
    return when {
        t.isEmpty() -> ""
        t == RIXO_HISTORY_UNDEFINED_COMPANY_VALUE -> ""
        t.equals(RIXO_HISTORY_UNDEFINED_COMPANY_LABEL, ignoreCase = true) -> ""
        else -> t
    }
}

private fun resolveRixoHistoryPurchaseIds(row: dynamic, allPurchases: Array<dynamic>): List<Long> {
    val chassisRaw = rixoHistoryCell(row, "chassis")
    val buyingDate = rixoHistoryCell(row, "buyingDate")
    val rixoCompany = rixoHistoryCell(row, "rixoCompany")
    val tokens = parseRixoHistoryChassisTokens(chassisRaw)
    if (tokens.isEmpty()) return emptyList()
    val ids = linkedSetOf<Long>()
    for (tok in tokens) {
        for (p in allPurchases) {
            val id = js("p.id")?.toString()?.toLongOrNull() ?: continue
            if (id in ids) continue
            val ch = js("p.chassis")?.toString()?.trim() ?: ""
            if (!rixoHistoryChassisTokenMatchesPurchase(tok, ch)) continue
            if (!rixoHistoryPurchaseMatchesRowFilters(p, buyingDate, rixoCompany)) continue
            ids.add(id)
        }
    }
    return ids.toList()
}

private fun downloadRixoHistoryPdf(row: dynamic, btn: HTMLButtonElement?) {
    val buyingDate = rixoHistoryCell(row, "buyingDate")
    val rixoCompany = persistableRixoCompanyFromHistory(rixoHistoryCell(row, "rixoCompany"))
    if (buyingDate.isEmpty()) {
        showMessage("Buying date is required to generate PDF.", "warning")
        return
    }
    val chassisRaw = rixoHistoryCell(row, "chassis")
    if (parseRixoHistoryChassisTokens(chassisRaw).isEmpty()) {
        showMessage("No chassis on this history row to generate PDF.", "warning")
        return
    }
    if (btn != null) {
        btn.disabled = true
        btn.style.opacity = "0.6"
    }
    MainScope().launch {
        try {
            val purchasesResult = ApiClient.get<Array<dynamic>>("purchases")
            val allPurchases = when (purchasesResult) {
                is ApiResult.Success -> purchasesResult.data
                is ApiResult.Error -> {
                    showMessage("Failed to load purchases: ${purchasesResult.message}", "error")
                    return@launch
                }
            }
            val selectedIds = resolveRixoHistoryPurchaseIds(row, allPurchases)
            if (selectedIds.isEmpty()) {
                showMessage(
                    "No purchases matched this history row (chassis + buying date + Rixo company).",
                    "warning",
                )
                return@launch
            }
            val transportData = js("{}")
            transportData.rixoCompany = rixoCompany
            transportData.buyingDate = buyingDate
            transportData.headMessage = RIXO_HISTORY_DEFAULT_HEAD_MESSAGE
            transportData.footerMessage = RIXO_HISTORY_DEFAULT_FOOTER_MESSAGE
            transportData.extraMessage = rixoHistoryCell(row, "message")
            transportData.contactDetails = RIXO_HISTORY_DEFAULT_CONTACT_DETAILS
            val requestBody = js("{}")
            val jsArray = js("[]")
            selectedIds.forEach { id -> jsArray.push(id.toInt()) }
            requestBody.ids = jsArray
            requestBody.transportData = transportData
            requestBody.persistHistory = false
            requestBody.generatePdf = true
            val headers = Headers()
            headers.set("Content-Type", "application/json")
            val requestInit = RequestInit(
                method = "POST",
                headers = headers,
                body = JSON.stringify(requestBody),
            )
            val response = window.fetch(apiUrl("purchases/rixo-transport-pdf"), requestInit).await()
            if (!response.ok) {
                val errorText = response.text().await()
                ErrorHandler.showError("Failed to download PDF: ${ErrorHandler.extractErrorMessage(errorText)}")
                return@launch
            }
            @Suppress("UNUSED_VARIABLE")
            val blob = response.blob().await()
            val url = js("URL.createObjectURL(blob)") as String
            try {
                val companySlug = rixoCompany.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifEmpty { "rixo" }
                val hid = rixoHistoryRowIdString(row).ifEmpty { "row" }
                val a = document.createElement("a") as HTMLAnchorElement
                a.href = url
                a.download = "rixo-transport-${companySlug}-${hid}.pdf"
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

private fun rixoHistoryRixoConfirmedFromRow(row: dynamic): Boolean {
    val d = row
    val v: dynamic = d.rixoConfirmed
    if (v == null || v == js("undefined")) return false
    if (v is Boolean) return v
    val s = v.toString().trim().lowercase()
    return s == "true" || s == "1"
}

private fun rixoHistoryHasBookingRequestedFromRow(row: dynamic): Boolean {
    val v: dynamic = row.hasBookingRequested
    if (v == null || v == js("undefined")) return false
    if (v is Boolean) return v
    val s = v.toString().trim().lowercase()
    return s == "true" || s == "1"
}

private fun rixoHistoryConfirmedIndicatorHtml(confirmed: Boolean): String {
    val label = if (confirmed) "Rixo Confirmed" else "Not Rixo Confirmed"
    val safeLabel = escapeHtml(label)
    return if (confirmed) {
        """<span role="img" aria-label="$safeLabel" title="$safeLabel" style="display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;border-radius:50%;background:#d1fae5;border:2px solid #6ee7b7;color:#047857;cursor:default;font-size:13px;line-height:1;font-weight:700;">✓</span>"""
    } else {
        """<span role="img" aria-label="$safeLabel" title="$safeLabel" style="display:inline-flex;align-items:center;justify-content:center;width:24px;height:24px;border-radius:50%;background:#fff;border:2px solid #d1d5db;cursor:default;">&nbsp;</span>"""
    }
}

private fun loadRixoHistory() {
    val tableHost = document.getElementById("rixoHistoryTable") ?: return
    tableHost.innerHTML = """<div class="rixo-history-empty"><strong>Loading</strong><div>Loading Rixo history…</div></div>"""

    MainScope().launch {
        ApiClient.get<Array<dynamic>>("rixo-history").fold(
            onSuccess = { rows ->
                rixoHistoryCachedRows = rows
                renderRixoHistoryTableFromCache()
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load Rixo history: $message")
                tableHost.innerHTML = """
                    <div class="rixo-history-empty" style="color:#b91c1c;">
                        <strong>Could not load</strong>
                        <div>Unable to load Rixo history. Please reload and try again.</div>
                    </div>
                """
            },
        )
    }
}

private fun rixoHistoryDisplayColumnKeys(): List<String> = listOf(
    "rixoConfirmed", "rixoConfirmedDate", "buyingDate", "rixoCompany", "message", "chassis",
)

private fun rixoHistorySearchColumnKeys(): List<String> =
    listOf("id") + rixoHistoryDisplayColumnKeys()

private fun rixoHistoryColumnLabel(key: String): String = when (key) {
    "rixoConfirmed" -> "Rixo Confirmed"
    "rixoConfirmedDate" -> "Rixo Confirmed Date"
    "buyingDate" -> "Buying date"
    "rixoCompany" -> "Rixo company"
    "message" -> "Message"
    "chassis" -> "Chassis"
    else -> key
}

/** Stable string id for matching DOM data-history-id to cached API rows (id may be number in JSON). */
private fun rixoHistoryRowIdString(row: dynamic): String {
    val d = row
    val v: dynamic = d.id
    if (v == null) return ""
    val undef = js("void 0")
    if (v === undef) return ""
    return v.toString().trim()
}

private fun rixoHistoryFormatConfirmedDateDisplay(iso: String): String {
    val t = iso.trim()
    if (t.isEmpty()) return ""
    val idx = t.indexOf('T')
    return if (idx in 1 until t.length) t.substring(0, idx) else t.take(10)
}

private fun rixoHistoryCell(row: dynamic, key: String): String {
    val d = row
    val v: dynamic = when (key) {
        "id" -> d.id
        "rixoConfirmed" -> null
        "rixoConfirmedDate" -> d.rixoConfirmedDate
        "buyingDate" -> d.buyingDate
        "rixoCompany" -> d.rixoCompany
        "message" -> d.message
        "chassis" -> d.chassis
        else -> null
    }
    if (key == "rixoConfirmed") {
        return if (rixoHistoryRixoConfirmedFromRow(d)) "yes" else "no"
    }
    if (key == "rixoConfirmedDate") {
        if (v == null) return ""
        val undef = js("void 0")
        if (v === undef) return ""
        return rixoHistoryFormatConfirmedDateDisplay(v.toString().trim())
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
        "rixoConfirmed" -> {
            val ca = if (rixoHistoryRixoConfirmedFromRow(a)) 1 else 0
            val cb = if (rixoHistoryRixoConfirmedFromRow(b)) 1 else 0
            orient(ca.compareTo(cb))
        }
        "rixoConfirmedDate" -> {
            fun rawIso(@Suppress("UNUSED_PARAMETER") r: dynamic): String {
                val v = js("r.rixoConfirmedDate")
                if (v == null || v === js("void 0")) return ""
                return v.toString().trim()
            }
            val rawA = rawIso(a)
            val rawB = rawIso(b)
            val aBlank = rawA.isEmpty()
            val bBlank = rawB.isEmpty()
            val c = when {
                aBlank && bBlank -> 0
                aBlank -> 1
                bBlank -> -1
                else -> rawA.compareTo(rawB)
            }
            orient(c)
        }
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
        tableHost.innerHTML = """<div class="rixo-history-empty"><strong>No history yet</strong><div>No Rixo history records yet.</div></div>"""
        return
    }

    var rows = rixoHistoryCachedRows.filter { rixoHistoryRowMatchesQuery(it, q) }.toTypedArray()

    if (rows.isEmpty()) {
        tableHost.innerHTML = """<div class="rixo-history-empty"><strong>No matches</strong><div>No rows match your search.</div></div>"""
        return
    }

    val comparator = Comparator<dynamic> { a, b ->
        compareRixoHistoryRows(a, b, rixoHistorySortField, rixoHistorySortOrder == "asc")
    }
    rows = rows.sortedWith(comparator).toTypedArray()
    // Keep only selections that still exist in loaded rows.
    val knownIds = rows.map { rixoHistoryRowIdString(it) }.filter { it.isNotEmpty() }.toSet()
    rixoHistorySelectedIds.retainAll(knownIds)

    val compact = rixoHistoryIsCompactLayout()
    val colCountRixo = 3 + rixoHistoryDisplayColumnKeys().size
    val rixoHistoryColWidthsPx = listOf(
        56, // Edit
        56, // PDF
        72, // Select checkbox
        96, // Rixo Confirmed
        120, // Rixo Confirmed Date
        112, // Buying date
        128, // Rixo company
        160, // Message
        200, // Chassis
    )
    val html = StringBuilder()
    if (!compact) {
        html.append(
            """<div class="rixo-history-table-shell"><table class="purchase-list-table" style="width:100%;border-collapse:collapse;table-layout:fixed;">""" +
                htmlTableColgroupFixedWidthsPx(colCountRixo, rixoHistoryColWidthsPx) +
                """<thead><tr style="background-color:#f8f9fa;">"""
        )
        html.append("""<th style="padding: 12px; text-align: center; border-bottom: 1px solid #dee2e6; width: 56px;">Edit</th>""")
        html.append("""<th style="padding: 12px; text-align: center; border-bottom: 1px solid #dee2e6; width: 56px;">PDF</th>""")
        html.append("""<th style="padding: 12px; text-align: center; border-bottom: 1px solid #dee2e6; width: 72px;">Select</th>""")
        for (key in rixoHistoryDisplayColumnKeys()) {
            val label = escapeHtml(rixoHistoryColumnLabel(key))
            val thAlign = if (key == "rixoConfirmed") "center" else "left"
            val isActive = rixoHistorySortField == key
            val sortOrder = if (isActive) rixoHistorySortOrder else "desc"
            val tooltipRaw = when {
                !isActive -> "Sort by ${rixoHistoryColumnLabel(key)}"
                sortOrder == "asc" -> "Sorted ascending (click for descending)"
                else -> "Sorted descending (click for ascending)"
            }
            val tooltip = escapeHtml(tooltipRaw)
            html.append(
                """
                <th style="padding: 12px; text-align: $thAlign; border-bottom: 1px solid #dee2e6;">
                    <button type="button" data-rixo-history-sort="$key" title="$tooltip" style="background:none;border:none;cursor:pointer;font-weight:700;color:#0f172a;padding:0;display:inline-flex;align-items:center;gap:6px;">
                        <span>$label</span><span style="font-size: 14px; color:#64748b;">↕</span>
                    </button>
                </th>
                """
            )
        }
        html.append("</tr></thead><tbody id='rixoHistoryTableBody'>")

        for (row in rows) {
            html.append("<tr>")
            val hid = rixoHistoryRowIdString(row)
            val checked = if (hid in rixoHistorySelectedIds) "checked" else ""
            html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${rixoHistoryEditButtonHtml(hid)}</td>""")
            html.append("""<td style="padding: 12px; vertical-align: middle; text-align: center;">${rixoHistoryPdfButtonHtml(hid)}</td>""")
            html.append(
                """<td style="padding: 12px; text-align: center; vertical-align: middle;">""" +
                    """<input type="checkbox" data-rixo-history-select data-history-id="${escapeHtml(hid)}" $checked title="Select row" aria-label="Select row ${escapeHtml(hid)}" class="rixo-checkbox" />""" +
                    "</td>"
            )
            html.append(
                """<td style="padding: 12px; text-align: center; vertical-align: middle;">${rixoHistoryConfirmedIndicatorHtml(rixoHistoryRixoConfirmedFromRow(row))}</td>"""
            )
            for (key in rixoHistoryDisplayColumnKeys()) {
                if (key == "rixoConfirmed") continue
                val raw = rixoHistoryCell(row, key)
                val cellHtml = when {
                    raw.isEmpty() -> ""
                    key == "chassis" -> formatRixoHistoryChassisChipsHtml(raw, hid)
                    else -> formatPurchaseListNeutralChipHtml(raw)
                }
                html.append("""<td style="padding: 12px; vertical-align: top;">$cellHtml</td>""")
            }
            html.append("</tr>")
        }
        html.append("</tbody></table></div>")
    } else {
        html.append("""<div id="rixoHistoryTableBody" class="rixo-cards">""")
        for (row in rows) {
            val hid = rixoHistoryRowIdString(row)
            val checked = if (hid in rixoHistorySelectedIds) "checked" else ""
            val confirmed = rixoHistoryRixoConfirmedFromRow(row)
            val confirmedDate = rixoHistoryCell(row, "rixoConfirmedDate")
            val buyingDate = rixoHistoryCell(row, "buyingDate")
            val rixoCompany = rixoHistoryCell(row, "rixoCompany")
            val msg = rixoHistoryCell(row, "message")
            val chassisRaw = rixoHistoryCell(row, "chassis")
            html.append("""<div class="rixo-card">""")
            html.append(
                """
                <div class="rixo-card-top">
                    <div class="rixo-card-actions">
                        ${rixoHistoryEditButtonHtml(hid)}
                        ${rixoHistoryPdfButtonHtml(hid)}
                    </div>
                    <div class="rixo-card-select">
                        <input type="checkbox" data-rixo-history-select data-history-id="${escapeHtml(hid)}" $checked
                            aria-label="Select row ${escapeHtml(hid)}" title="Select row" class="rixo-checkbox" />
                    </div>
                </div>
                """
            )
            html.append("""<div class="rixo-card-grid">""")
            html.append("""<div class="rixo-kv"><div class="rixo-k">Rixo status</div><div class="rixo-v">${rixoHistoryConfirmedIndicatorHtml(confirmed)}</div></div>""")
            if (confirmedDate.isNotEmpty()) {
                html.append("""<div class="rixo-kv"><div class="rixo-k">Rixo Confirmed Date</div><div class="rixo-v">${formatPurchaseListNeutralChipHtml(confirmedDate)}</div></div>""")
            }
            if (buyingDate.isNotEmpty()) {
                html.append("""<div class="rixo-kv"><div class="rixo-k">Buying date</div><div class="rixo-v">${formatPurchaseListNeutralChipHtml(buyingDate)}</div></div>""")
            }
            if (rixoCompany.isNotEmpty()) {
                html.append("""<div class="rixo-kv"><div class="rixo-k">Rixo company</div><div class="rixo-v">${formatPurchaseListNeutralChipHtml(rixoCompany)}</div></div>""")
            }
            if (msg.isNotEmpty()) {
                html.append("""<div class="rixo-kv"><div class="rixo-k">Message</div><div class="rixo-v">${formatPurchaseListNeutralChipHtml(msg)}</div></div>""")
            }
            if (chassisRaw.isNotEmpty()) {
                html.append("""<div class="rixo-kv"><div class="rixo-k">Chassis</div><div class="rixo-v">${formatRixoHistoryChassisChipsHtml(chassisRaw, hid)}</div></div>""")
            }
            html.append("""</div></div>""")
        }
        html.append("</div>")
    }

    tableHost.innerHTML = html.toString()
    updateRixoHistorySelectionUi()
}
