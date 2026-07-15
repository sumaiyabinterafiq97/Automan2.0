package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.ApiClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Invoice Functions

/** Plain-text detail for modals; never returns raw JSON blobs. */
private fun creditLimitDetailText(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("{")) {
        return trimmed.takeIf { !it.startsWith("{") }
    }
    val fromJson = try {
        val o = JSON.parse<dynamic>(trimmed)
        val msg = o.message?.toString()?.trim().orEmpty()
        if (msg.isNotEmpty()) {
            if (msg.contains("400 BAD_REQUEST", ignoreCase = true)) {
                msg.substringAfter("\"").substringBeforeLast("\"").ifEmpty { msg }
            } else {
                msg
            }
        } else {
            ErrorHandler.extractErrorMessage(trimmed).trim()
        }
    } catch (_: dynamic) {
        ErrorHandler.extractErrorMessage(trimmed).trim()
    }
    return fromJson.takeIf { it.isNotEmpty() && !it.startsWith("{") }
}

private fun parseInvoiceApiError(errorText: String): Pair<String, Boolean> {
    val trimmed = errorText.trim()
    val detail = creditLimitDetailText(trimmed)
    val message = detail ?: trimmed
    var creditBlocked = message.contains("credit limit", ignoreCase = true)
    if (trimmed.startsWith("{")) {
        try {
            val o = JSON.parse<dynamic>(trimmed)
            if (o.creditLimitBlocked == true) creditBlocked = true
            val err = o.error?.toString().orEmpty()
            if (err.contains("credit limit", ignoreCase = true)) creditBlocked = true
        } catch (_: dynamic) {
        }
    }
    return Pair(message, creditBlocked)
}

fun showCreditLimitExceededModal(detailMessage: String) {
    document.getElementById("creditLimitInvoiceModal")?.remove()
    val detail = creditLimitDetailText(detailMessage)
    val detailBlock = if (detail != null) {
        val safeMsg = detail
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        """<p style="margin: 0 0 16px; padding: 12px; background: #fef2f2; border-left: 4px solid #e74c3c; color: #7f1d1d; font-size: 14px; line-height: 1.5;">$safeMsg</p>"""
    } else {
        ""
    }
    val modalHTML = """
        <div id="creditLimitInvoiceModal" class="client-modal">
            <div class="client-modal-content" style="max-width: 520px;">
                <div class="client-modal-header">
                    <h2>Credit limit exceeded</h2>
                    <button type="button" id="closeCreditLimitInvoiceModalBtn" class="client-modal-close">&times;</button>
                </div>
                <p style="margin: 0 0 12px; color: #333; line-height: 1.5;">
                    This invoice cannot be saved because the client would exceed their credit limit.
                </p>
                $detailBlock
                <p class="client-ledger-hint" style="margin: 0 0 16px;">
                    Update the credit limit under <strong>Master → Client Transactions</strong>, or reduce the invoice amount.
                </p>
                <div class="client-modal-actions">
                    <button type="button" id="okCreditLimitInvoiceModalBtn" class="client-btn client-btn-primary">OK</button>
                </div>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    fun close() {
        document.getElementById("creditLimitInvoiceModal")?.remove()
    }
    document.getElementById("closeCreditLimitInvoiceModalBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("okCreditLimitInvoiceModalBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("creditLimitInvoiceModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "creditLimitInvoiceModal") close()
    })
}

private fun handleInvoiceSaveFailure(httpStatus: Int, errorText: String) {
    val (userMsg, creditBlocked) = parseInvoiceApiError(errorText)
    if (creditBlocked) {
        Logger.warn("Invoice save blocked (credit limit): $userMsg")
        showCreditLimitExceededModal(userMsg)
        return
    }
    Logger.error("Invoice save failed ($httpStatus): $userMsg")
    if (httpStatus == 409) {
        showMessage(userMsg, "error")
        return
    }
    showMessage("Failed to save invoice: $userMsg", "error")
}

/** Invoice number for the current #/recreate-invoice session (delete / upsert). */
private var invoiceRecreateInvoiceNumber: String? = null

fun isInvoiceRecreateSession(): Boolean =
    routeStartsWith("/recreate-invoice")

private fun clearInvoiceRecreateSessionData() {
    invoiceRecreateInvoiceNumber = null
    window.sessionStorage.removeItem(INVOICE_RECREATE_META_SESSION_KEY)
}

private fun persistInvoiceRecreateMeta() {
    if (!isInvoiceRecreateSession()) return
    val inv = invoiceRecreateInvoiceNumber?.trim().orEmpty()
    if (inv.isEmpty()) return
    val o = js("{}")
    o.invoiceNumber = inv
    window.sessionStorage.setItem(INVOICE_RECREATE_META_SESSION_KEY, JSON.stringify(o))
}

private fun restoreInvoiceRecreateMetaFromSession() {
    if (!isInvoiceRecreateSession()) return
    val raw = window.sessionStorage.getItem(INVOICE_RECREATE_META_SESSION_KEY)?.takeIf { it.isNotEmpty() }
        ?: return
    val o: dynamic = try {
        JSON.parse(raw)
    } catch (_: Throwable) {
        return
    }
    val inv = o.invoiceNumber?.toString()?.trim().orEmpty()
    if (inv.isNotEmpty()) invoiceRecreateInvoiceNumber = inv
}

private fun lockInvoiceRecreateClientAndVessel() {
    (document.getElementById("invoiceClient") as? HTMLSelectElement)?.disabled = true
    (document.getElementById("invoiceVessel") as? HTMLSelectElement)?.disabled = true
}

fun showInvoicePage() {
    val content = document.getElementById("content") ?: return
    
    // Store current invoice purchases (will be populated when CLIENT + VESSEL are selected)
    js("window.currentInvoicePurchaseIds = []")
    js("window.currentInvoicePdfLines = []")
    js("window.currentInvoiceChassisList = []")
    
    // Check for URL parameters (ids) for pre-selected purchases
    val isRecreateInvoice = routeStartsWith("/recreate-invoice")
    val invoicePageMainTitle =
        if (isRecreateInvoice) {
            "AUTOMAN | RECREATE LOCAL CUSTOMER INVOICE"
        } else {
            "AUTOMAN | CREATE LOCAL CUSTOMER INVOICE"
        }
    val invoiceSaveBtnLabel = if (isRecreateInvoice) "Update" else "Save"
    val invoiceListFooterHtml = if (isRecreateInvoice) {
        """
                        <div class="invoice-list-footer" style="display:flex;justify-content:flex-end;margin-top:14px;padding-top:12px;">
                            <button type="button" id="deleteInvoiceFromRecreate" class="invoice-btn" style="padding:10px 20px;border:1px solid #b91c1c;border-radius:8px;background:#fff;color:#b91c1c;font-weight:600;font-size:14px;cursor:pointer;">Delete</button>
                        </div>
        """.trimIndent()
    } else {
        ""
    }

    if (!isRecreateInvoice) {
        clearInvoiceRecreateSessionData()
    }

    val idsParam = window.location.search.removePrefix("?").split("&")
        .find { it.startsWith("ids=") }
        ?.substringAfter("=")
    
    // Also check localStorage for invoiceSelectedIds
    val localStorageIds = try {
        val stored = safeLocalStorageGet("invoiceSelectedIds")
        if (stored != null && stored.isNotEmpty()) {
            JSON.parse<Array<dynamic>>(stored)
        } else {
            null
        }
    } catch (e: dynamic) {
        null
    }
    
    content.innerHTML = """
        <div class="invoice-page-container">
            <div class="invoice-card">
                <div class="invoice-page-header">
                    <h1>$invoicePageMainTitle</h1>
                </div>
                
                <div class="invoice-layout">
                    <!-- Left Column: Form Fields -->
                    <div class="invoice-form-section">
                        <div class="invoice-field">
                            <label for="invoiceClient">CLIENT:</label>
                            <select id="invoiceClient" class="invoice-select">
                                <option value="">Select client</option>
                            </select>
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceVessel">VESSEL:</label>
                            <select id="invoiceVessel" class="invoice-select">
                                <option value="">Select vessel</option>
                            </select>
                            <p id="invoiceVesselEmptyHint" class="invoice-vessel-empty-hint" style="display: none; margin: 6px 0 0; font-size: 13px; color: #6c757d;">No open vessels for this client</p>
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceShippingDate">SHIPPING DATE:</label>
                            <div style="position:relative; width:100%;">
                                <div style="display:flex; gap:8px; align-items:center; width:100%;">
                                    <input type="text" id="invoiceShippingDateText" maxlength="10" inputmode="numeric" autocomplete="off"
                                           class="invoice-input" placeholder="MM/DD/YYYY"
                                           style="flex:1; min-width:0;" />
                                    <button type="button" id="invoiceShippingDateCalendarBtn" title="Open calendar"
                                            style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                                </div>
                                <input type="date" id="invoiceShippingDate" class="invoice-input" tabindex="-1" aria-hidden="true"
                                       style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;" />
                            </div>
                        </div>
                        
                        <div class="invoice-field">
                            <label>SHIPPING LOCATION:</label>
                            <div class="invoice-shipping-grid">
                                <div>
                                    <label>FROM:</label>
                                    <input type="text" id="invoiceFrom" class="invoice-input" placeholder="Origin port" />
                                </div>
                                <div>
                                    <label>TO:</label>
                                    <input type="text" id="invoiceTo" class="invoice-input" placeholder="Destination port" />
                                </div>
                            </div>
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceNumber">INVOICE NUMBER:</label>
                            <div class="invoice-number-row">
                                <input type="text" id="invoiceNumber" class="invoice-input" placeholder="Enter invoice number" />
                                <button type="button" id="generateInvoiceNumberBtn" class="invoice-generate-btn">GENERATE INVOICE NUMBER</button>
                            </div>
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceLcNo">LC NO.:</label>
                            <input type="text" id="invoiceLcNo" class="invoice-input" placeholder="Enter LC number" />
                        </div>
                        
                        <div class="invoice-field">
                            <label>PRICE TYPE:</label>
                            <div class="invoice-radio-group">
                                <div class="invoice-radio">
                                    <input type="radio" id="invoiceCnf" name="invoicePriceType" value="CNF" checked />
                                    <label for="invoiceCnf">C&F</label>
                                </div>
                                <div class="invoice-radio">
                                    <input type="radio" id="invoiceFob" name="invoicePriceType" value="FOB" />
                                    <label for="invoiceFob">FOB</label>
                                </div>
                            </div>
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceBankAccount">SELECT BANK ACCOUNT:</label>
                            <select id="invoiceBankAccount" class="invoice-select">
                                <option value="">Select bank account</option>
                                <option value="BANK OF SMBC MITSUI SUMITOMO (Gyotoku) BRANCH
A/C NO: 0398932
A/C NAME: MEMON Co. Ltd.
SWIFT CODE: SMBCJPJT">BANK OF SMBC MITSUI SUMITOMO (Gyotoku) BRANCH
A/C NO: 0398932
A/C NAME: MEMON Co. Ltd.
SWIFT CODE: SMBCJPJT</option>
                            </select>
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceMessage">MESSAGE:</label>
                            <textarea id="invoiceMessage" class="invoice-textarea" rows="4" placeholder="Enter message"></textarea>
                        </div>
                    </div>
                    
                    <!-- Right Column: LIST Table -->
                    <div class="invoice-list-section">
                        <h2>LIST</h2>
                        
                        <!-- Table/Cards Container (grows to fill space) -->
                        <div class="invoice-table-container">
                            <!-- Table for Tablet/Desktop -->
                            <table class="invoice-list-table" id="invoiceListTable">
                                <thead>
                                    <tr>
                                        <th>NO.</th>
                                        <th>CHASSIS</th>
                                        <th>NAME</th>
                                        <th>YEAR</th>
                                        <th>AMOUNT</th>
                                    </tr>
                                </thead>
                                <tbody id="invoiceListTableBody">
                                    <!-- Will be populated by JavaScript -->
                                </tbody>
                            </table>
                            
                            <!-- Cards for Mobile -->
                            <div class="invoice-cards-container" id="invoiceCardsContainer">
                                <!-- Will be populated by JavaScript -->
                            </div>
                        </div>
                        
                        <div class="invoice-total" id="invoiceTotalAmount">
                            TOTAL AMOUNT: ¥000,000
                        </div>
                        $invoiceListFooterHtml
                    </div>
                </div>
                
                <!-- Action Buttons -->
                <div class="invoice-actions">
                    <button type="button" id="invoiceSaveBtn" class="invoice-btn invoice-btn-primary">$invoiceSaveBtnLabel</button>
                    <button type="button" id="showFullPreviewBtn" class="invoice-btn invoice-btn-secondary">Preview</button>
                    <button type="button" id="invoicePdfBtn" class="invoice-btn invoice-btn-secondary">PDF</button>
                </div>
            </div>
        </div>
    """

    bindStrictDateTextMask("invoiceShippingDate")

    // Setup event listeners
    setupInvoicePageListeners()
    
    // Load unique client_name values from shipping_history for CLIENT dropdown
    loadInvoiceClientOptions()
    
    // Initialize empty LIST table
    val tableBody = document.getElementById("invoiceListTableBody")
    if (tableBody != null) {
        tableBody.innerHTML = ""
    }
    val totalElement = document.getElementById("invoiceTotalAmount")
    if (totalElement != null) {
        totalElement.textContent = "TOTAL AMOUNT: ¥000,000"
    }
    
    // Handle pre-selected purchases from URL or localStorage
    val selectedIds = if (idsParam != null && idsParam.isNotEmpty()) {
        idsParam.split(",").mapNotNull { it.toLongOrNull() }
    } else if (localStorageIds != null) {
        localStorageIds.mapNotNull { id ->
            when (id) {
                is Number -> id.toLong()
                is String -> id.toLongOrNull()
                else -> null
            }
        }
    } else {
        emptyList<Long>()
    }
    
    val prefillRaw = window.sessionStorage.getItem(INVOICE_HISTORY_EDIT_SESSION_KEY)
    val shippingInvoicePrefillRaw = window.sessionStorage.getItem(SHIPPING_HISTORY_INVOICE_PREFILL_SESSION_KEY)
    if (prefillRaw != null && prefillRaw.isNotEmpty()) {
        window.sessionStorage.removeItem(INVOICE_HISTORY_EDIT_SESSION_KEY)
        MainScope().launch {
            applyInvoiceHistoryEditPrefillFromJson(prefillRaw)
            if (isInvoiceRecreateSession()) {
                lockInvoiceRecreateClientAndVessel()
            }
        }
    } else if (shippingInvoicePrefillRaw != null && shippingInvoicePrefillRaw.isNotEmpty()) {
        window.sessionStorage.removeItem(SHIPPING_HISTORY_INVOICE_PREFILL_SESSION_KEY)
        MainScope().launch {
            applyShippingHistoryInvoicePrefillFromJson(shippingInvoicePrefillRaw)
        }
    } else if (selectedIds.isNotEmpty()) {
        window.setTimeout({
            loadPurchasesByIds(selectedIds)
        }, 500)
    } else if (isRecreateInvoice) {
        restoreInvoiceRecreateMetaFromSession()
        window.setTimeout({
            lockInvoiceRecreateClientAndVessel()
        }, 300)
    }
}

fun loadPurchasesByIds(ids: List<Long>) {
    window.fetch(apiUrl("purchases"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load purchases')")
        }
        .then { purchases: dynamic ->
            val purchasesArray = js("Array.isArray(purchases) ? purchases : []") as Array<dynamic>
            
            // Filter to selected purchases
            val selectedPurchases = purchasesArray.filter { purchase ->
                val id = js("purchase.id")?.toString()?.toLongOrNull()
                id != null && ids.contains(id)
            }.toTypedArray()
            
            if (selectedPurchases.isNotEmpty()) {
                // Get unique client name and vessel from selected purchases
                val clientNames = selectedPurchases.mapNotNull {
                    js("it.clientName")?.toString()?.trim()
                }.distinct()
                val vessels = selectedPurchases.mapNotNull { 
                    js("it.vessel")?.toString()?.trim() 
                }.distinct()
                
                // Auto-fill CLIENT and VESSEL if we have unique values
                if (clientNames.size == 1) {
                    val clientSelect = document.getElementById("invoiceClient") as? HTMLSelectElement
                    if (clientSelect != null) {
                        clientSelect.value = clientNames[0]
                    }
                    val preferredVessel = if (vessels.size == 1) vessels[0] else null
                    loadInvoiceVesselOptionsForClient(clientNames[0], preferredVessel)
                }
                
                if (clientNames.size == 1 && vessels.size == 1) {
                    loadInvoiceShippingLines(clientNames[0], vessels[0])
                } else {
                    js("window.currentInvoicePurchaseIds = selectedPurchases.map(function(p) { return p.id; })")
                    populateInvoiceListTable(selectedPurchases)
                }
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading purchases by IDs: ${error.toString()}")
        }
}

/** Fetches invoice slice; applies POL/POD/date/price header only (not LIST lines). */
private fun loadInvoiceShippingHeaderForPrefill(client: String, vessel: String, onDone: () -> Unit) {
    val encodedClient = js("encodeURIComponent")(client.trim()) as String
    val encodedVessel = js("encodeURIComponent")(vessel.trim()) as String
    window.fetch(apiUrl("shipping-history/for-invoice/lines?clientName=$encodedClient&vessel=$encodedVessel"))
        .then { response: dynamic ->
            if (!response.ok) {
                onDone()
                return@then
            }
            response.json().then { payload: dynamic ->
                if (js("payload && payload.success === true") as Boolean) {
                    applyInvoiceHeaderFromShipping(payload)
                }
                onDone()
            }
        }
        .catch { _: dynamic -> onDone() }
}

private suspend fun invoicePrefillEnsureClientDropdownAndSelect(clientName: String) {
    val response = window.fetch(apiUrl("shipping-history/for-invoice/client-names")).await()
    if (!response.ok) return
    val text = response.text().await()
    val payload = JSON.parse<dynamic>(text)
    val namesRaw = if (js("payload && payload.success") as Boolean) payload.data else js("[]")
    val arr = namesRaw.unsafeCast<Array<dynamic>>()
    val clientSelect = document.getElementById("invoiceClient") as? HTMLSelectElement ?: return
    while (clientSelect.options.length > 1) {
        clientSelect.remove(1)
    }
    for (name in arr) {
        val option = document.createElement("option") as HTMLOptionElement
        val n = name.toString()
        option.value = n
        option.textContent = n
        clientSelect.appendChild(option)
    }
    if (clientName.isBlank()) return
    var found = false
    for (i in 0 until clientSelect.options.length) {
        val opt = clientSelect.options.item(i) as? HTMLOptionElement ?: continue
        if (opt.value == clientName) {
            found = true
            break
        }
    }
    if (!found) {
        val opt = document.createElement("option") as HTMLOptionElement
        opt.value = clientName
        opt.textContent = clientName
        clientSelect.appendChild(opt)
    }
    clientSelect.value = clientName
}

private suspend fun invoicePrefillLoadVesselsAndSelect(clientName: String, vessel: String) {
    suspendCoroutine<Unit> { cont ->
        loadInvoiceVesselOptionsForClient(clientName, vessel) { cont.resume(Unit) }
    }
}

private suspend fun invoicePrefillLoadShippingHeader(clientName: String, vessel: String) {
    suspendCoroutine<Unit> { cont ->
        loadInvoiceShippingHeaderForPrefill(clientName, vessel) { cont.resume(Unit) }
    }
}

private fun parseInvoiceHistoryChassisTokens(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split(';', ',', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
}

private fun invoicePrefillChassisTokenMatchesPurchase(token: String, purchaseChassis: String): Boolean {
    val t = token.trim()
    val ch = purchaseChassis.trim()
    if (t.isEmpty() || ch.isEmpty()) return false
    if (ch.equals(t, ignoreCase = true)) return true
    val head = ch.substringBefore('-').trim()
    if (head.equals(t, ignoreCase = true)) return true
    if (ch.startsWith(t, ignoreCase = true) &&
        (ch.length == t.length || ch.getOrNull(t.length) == '-')
    ) {
        return true
    }
    return false
}

private fun invoicePrefillDateKey(raw: String): String =
    toIsoFromLabel(raw.trim()).trim()

/** Invoice history edit: match each chassis token to purchases with same client + vessel (no purchase date filter). */
private fun invoicePrefillPurchaseMatchesClientAndVessel(
    p: dynamic,
    historyClient: String,
    historyVessel: String,
): Boolean {
    if (historyClient.isNotBlank()) {
        val pc = js("p.clientName")?.toString()?.trim() ?: ""
        if (!historyClient.equals(pc, ignoreCase = true)) return false
    }
    if (historyVessel.isNotBlank()) {
        val pv = js("p.vessel")?.toString()?.trim() ?: ""
        if (!historyVessel.equals(pv, ignoreCase = true)) return false
    }
    return true
}

/**
 * Applies invoice form header + LIST from resolved shipping/invoice-history fields (shared by Invoice History edit and Shipping History → Create Invoice).
 */
private suspend fun invoicePrefillApplyHeaderRadiosPurchasesMatch(
    clientName: String,
    vessel: String,
    shippingDateRaw: String,
    invoiceNumber: String,
    lcNo: String,
    bank: String,
    messages: String,
    polRaw: String,
    podRaw: String,
    chassisRaw: String,
    priceTypeFromHistory: String,
    historyLineAmountsRaw: String = "",
) {
    invoicePrefillEnsureClientDropdownAndSelect(clientName)
    if (clientName.isNotBlank() && vessel.isNotBlank()) {
        invoicePrefillLoadVesselsAndSelect(clientName, vessel)
        invoicePrefillLoadShippingHeader(clientName, vessel)
    }

    val vesselSelect = document.getElementById("invoiceVessel") as? HTMLSelectElement
    if (vessel.isNotBlank() && vesselSelect != null) {
        var hasV = false
        for (i in 0 until vesselSelect.options.length) {
            val opt = vesselSelect.options.item(i) as? HTMLOptionElement ?: continue
            if (opt.value == vessel) {
                hasV = true
                break
            }
        }
        if (!hasV) {
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = vessel
            opt.textContent = vessel
            vesselSelect.appendChild(opt)
        }
        vesselSelect.value = vessel
    }

    val shippingIso = invoicePrefillDateKey(shippingDateRaw).ifBlank { shippingDateRaw }
    (document.getElementById("invoiceNumber") as? HTMLInputElement)?.value = invoiceNumber
    (document.getElementById("invoiceLcNo") as? HTMLInputElement)?.value = lcNo
    (document.getElementById("invoiceMessage") as? HTMLTextAreaElement)?.value = messages
    if (shippingIso.isNotBlank()) {
        val hid = document.getElementById("invoiceShippingDate") as? HTMLInputElement
        if (hid != null) {
            hid.value = shippingIso
            val txt = document.getElementById("invoiceShippingDateText") as? HTMLInputElement
            if (txt != null) txt.value = isoToMmDdYyyy(shippingIso)
        }
    }
    if (bank.isNotBlank()) {
        val sel = document.getElementById("invoiceBankAccount") as? HTMLSelectElement
        if (sel != null) {
            var has = false
            for (i in 0 until sel.options.length) {
                val opt = sel.options.item(i) as? HTMLOptionElement ?: continue
                if (opt.value == bank) {
                    has = true
                    break
                }
            }
            if (!has) {
                val opt = document.createElement("option") as HTMLOptionElement
                opt.value = bank
                opt.textContent = bank
                sel.appendChild(opt)
            }
            sel.value = bank
        }
    }
    if (polRaw.isNotBlank()) {
        (document.getElementById("invoiceFrom") as? HTMLInputElement)?.value = polRaw
    }
    if (podRaw.isNotBlank()) {
        (document.getElementById("invoiceTo") as? HTMLInputElement)?.value = podRaw
    }

    val invoiceCnf = document.getElementById("invoiceCnf") as? HTMLInputElement
    val invoiceFob = document.getElementById("invoiceFob") as? HTMLInputElement
    if (priceTypeFromHistory.isNotEmpty()) {
        val upper = priceTypeFromHistory.uppercase()
        when {
            upper.contains("FOB") -> {
                invoiceFob?.checked = true
                invoiceCnf?.checked = false
            }
            upper.contains("CNF") || upper.contains("C&F") || priceTypeFromHistory.contains("C&F") -> {
                invoiceCnf?.checked = true
                invoiceFob?.checked = false
            }
            else -> { }
        }
    }

    when (val result = ApiClient.get<Array<dynamic>>("purchases")) {
        is ApiResult.Success -> {
            val arr = result.data
            val tokens = parseInvoiceHistoryChassisTokens(chassisRaw)
            val matched = mutableListOf<dynamic>()
            val seenIds = mutableSetOf<Long>()
            for (tok in tokens) {
                for (i in 0 until arr.size) {
                    val p = arr[i]
                    val id = js("p.id")?.toString()?.toLongOrNull() ?: continue
                    if (id in seenIds) continue
                    val ch = js("p.chassis")?.toString()?.trim() ?: ""
                    // Match by chassis only — client/vessel already known from history, no need to re-filter
                    if (!invoicePrefillChassisTokenMatchesPurchase(tok, ch)) continue
                    matched.add(p)
                    seenIds.add(id)
                }
            }
            populateInvoiceListTable(
                matched.toTypedArray(),
                parseInvoiceHistoryAmountTokens(historyLineAmountsRaw),
            )
            if (matched.isEmpty() && tokens.isNotEmpty()) {
                showMessage(
                    "No purchases matched (chassis). Check Purchase List.",
                    "warning",
                )
            }
        }
        is ApiResult.Error -> showMessage("Failed to load purchases: ${result.message}", "error")
    }
}

private suspend fun applyInvoiceHistoryEditPrefillFromJson(raw: String) {
    val o: dynamic = try {
        JSON.parse(raw)
    } catch (_: Throwable) {
        return
    }
    val invoiceNumber = (o.invoiceNumber?.toString() ?: "").trim()
    invoiceRecreateInvoiceNumber = invoiceNumber.takeIf { it.isNotEmpty() }
    persistInvoiceRecreateMeta()
    val vessel = (o.vessel?.toString() ?: "").trim()
    val clientName = (o.clientName?.toString() ?: "").trim()
    val shippingDateRaw = (o.shippingDate?.toString() ?: "").trim()
    val lcNo = (o.lcNo?.toString() ?: "").trim()
    val bank = (o.bank?.toString() ?: "").trim()
    val messages = (o.messages?.toString() ?: "").trim()
    val polRaw = (o.pol?.toString() ?: "").trim()
    val podRaw = (o.pod?.toString() ?: "").trim()
    val chassisRaw = (o.chassis?.toString() ?: "").trim()
    val priceTypeFromHistory = (o.priceType?.toString() ?: "").trim()
    val historyLineAmountsRaw = (o.totalAmount?.toString() ?: "").trim()
    invoicePrefillApplyHeaderRadiosPurchasesMatch(
        clientName = clientName,
        vessel = vessel,
        shippingDateRaw = shippingDateRaw,
        invoiceNumber = invoiceNumber,
        lcNo = lcNo,
        bank = bank,
        messages = messages,
        polRaw = polRaw,
        podRaw = podRaw,
        chassisRaw = chassisRaw,
        priceTypeFromHistory = priceTypeFromHistory,
        historyLineAmountsRaw = historyLineAmountsRaw,
    )
}

private fun shippingHistoryInvoiceRowDynStr(row: dynamic, key: String): String {
    // Do not call row.asDynamic() — Kotlin/JS sort/comparator passes plain JS objects; .asDynamic is not a function on them.
    val d = row
    val v: dynamic = when (key) {
        "id" -> d.id
        "chassis" -> d.chassis
        "clientName" -> d.clientName
        "vessel" -> d.vessel
        "pol" -> d.pol
        "pod" -> d.pod
        "shipmentDate" -> d.shipmentDate
        "priceType" -> d.priceType
        else -> null
    }
    if (v == null) return ""
    val undef = js("void 0")
    if (v === undef) return ""
    return v.toString().trim()
}

private suspend fun applyShippingHistoryInvoicePrefillFromJson(raw: String) {
    val payload: dynamic = try {
        JSON.parse(raw)
    } catch (_: Throwable) {
        return
    }
    val rowsRaw = payload.rows
    if (js("!Array.isArray(rowsRaw)") as Boolean) return
    val rows = rowsRaw.unsafeCast<Array<dynamic>>()
    if (rows.size == 0) {
        showMessage("No shipping history rows in prefilled payload.", "warning")
        return
    }

    /** Copy to Kotlin list without sortBy on mixed JS proxies (stable + avoids comparator edge cases). */
    val mutableRows = mutableListOf<dynamic>()
    for (i in 0 until rows.size) {
        mutableRows.add(rows[i])
    }
    mutableRows.sortWith { a, b ->
        val ia = shippingHistoryInvoiceRowDynStr(a, "id").toLongOrNull() ?: 0L
        val ib = shippingHistoryInvoiceRowDynStr(b, "id").toLongOrNull() ?: 0L
        ia.compareTo(ib)
    }
    val sortedList: List<dynamic> = mutableRows

    fun distinctNonempty(selector: (dynamic) -> String): List<String> =
        sortedList.map { selector(it) }.filter { it.isNotBlank() }.distinct()

    val clientOpts = distinctNonempty { shippingHistoryInvoiceRowDynStr(it, "clientName") }
    val vesselOpts = distinctNonempty { shippingHistoryInvoiceRowDynStr(it, "vessel") }

    val clientName = clientOpts.singleOrNull() ?: clientOpts.firstOrNull().orEmpty()
    if (clientOpts.size > 1) {
        showMessage("Multiple clients in this booking; using «$clientName». Verify before saving.", "warning")
    }

    val vessel = vesselOpts.singleOrNull() ?: vesselOpts.firstOrNull().orEmpty()
    if (vesselOpts.size > 1) {
        showMessage("Multiple vessels in this booking; using «$vessel». Verify before saving.", "warning")
    }

    val chassisToks = mutableListOf<String>()
    val seenTok = mutableSetOf<String>()
    for (r in sortedList) {
        val cs = shippingHistoryInvoiceRowDynStr(r, "chassis")
        for (tok in parseInvoiceHistoryChassisTokens(cs)) {
            val k = tok.lowercase()
            if (seenTok.add(k)) chassisToks.add(tok)
        }
    }
    val chassisRaw = chassisToks.joinToString(",")

    val shipmentDateRaw = sortedList.firstNotNullOfOrNull { r ->
        shippingHistoryInvoiceRowDynStr(r, "shipmentDate").takeIf { it.isNotBlank() }
    }.orEmpty()
    val polRaw = sortedList.firstNotNullOfOrNull { r ->
        shippingHistoryInvoiceRowDynStr(r, "pol").takeIf { it.isNotBlank() }
    }.orEmpty()
    val podRaw = sortedList.firstNotNullOfOrNull { r ->
        shippingHistoryInvoiceRowDynStr(r, "pod").takeIf { it.isNotBlank() }
    }.orEmpty()
    val priceTypeFromHistory = sortedList.firstNotNullOfOrNull { r ->
        shippingHistoryInvoiceRowDynStr(r, "priceType").takeIf { it.isNotBlank() }
    }.orEmpty()

    invoicePrefillApplyHeaderRadiosPurchasesMatch(
        clientName = clientName,
        vessel = vessel,
        shippingDateRaw = shipmentDateRaw,
        invoiceNumber = "",
        lcNo = "",
        bank = "",
        messages = "",
        polRaw = polRaw,
        podRaw = podRaw,
        chassisRaw = chassisRaw,
        priceTypeFromHistory = priceTypeFromHistory,
    )
}

fun loadInvoiceClientOptions() {
    window.fetch(apiUrl("shipping-history/for-invoice/client-names"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load client names')")
        }
        .then { payload: dynamic ->
            val namesRaw = js("payload && payload.success ? payload.data : []")
            val clientNames = js("Array.isArray(namesRaw) ? namesRaw : []") as Array<dynamic>
            
            val clientSelect = document.getElementById("invoiceClient") as? HTMLSelectElement
            if (clientSelect != null) {
                while (clientSelect.options.length > 1) {
                    clientSelect.remove(1)
                }
                
                for (name in clientNames) {
                    val option = document.createElement("option") as HTMLOptionElement
                    option.value = name.toString()
                    option.textContent = name.toString()
                    clientSelect.appendChild(option)
                }
            }
        }
        .catch { error: dynamic ->
            val errorMsg = ErrorHandler.handleNetworkError(error, "shipping-history")
            Logger.error("Error loading shipping history client names: $errorMsg")
            ErrorHandler.showError("Failed to load client options: $errorMsg")
        }
}

fun loadInvoiceVesselOptionsForClient(client: String, preferredVessel: String? = null, onDone: (() -> Unit)? = null) {
    val vesselSelect = document.getElementById("invoiceVessel") as? HTMLSelectElement
    if (vesselSelect == null) {
        onDone?.invoke()
        return
    }

    vesselSelect.innerHTML = "<option value=\"\">Select vessel</option>"
    (document.getElementById("invoiceVesselEmptyHint") as? HTMLElement)?.style?.display = "none"
    if (client.isBlank()) {
        onDone?.invoke()
        return
    }

    clearInvoiceShippingHeaderFields()

    val encodedClient = js("encodeURIComponent")(client.trim()) as String
    window.fetch(apiUrl("shipping-history/for-invoice/vessels?clientName=$encodedClient"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load vessels')")
        }
        .then { payload: dynamic ->
            val namesRaw = js("payload && payload.success ? payload.data : []")
            val vessels = js("Array.isArray(namesRaw) ? namesRaw : []") as Array<dynamic>
            val vesselStrings = mutableListOf<String>()
            for (v in vessels) {
                val vesselName = v.toString().trim()
                if (vesselName.isBlank()) continue
                vesselStrings.add(vesselName)
                val option = document.createElement("option") as HTMLOptionElement
                option.value = vesselName
                option.textContent = vesselName
                vesselSelect.appendChild(option)
            }

            val emptyHint = document.getElementById("invoiceVesselEmptyHint") as? HTMLElement
            val hasAnyVessel = vesselSelect.options.length > 1
            if (emptyHint != null) {
                emptyHint.style.display = if (client.isNotBlank() && !hasAnyVessel) "block" else "none"
            }

            val preferred = preferredVessel?.trim().orEmpty()
            var selectedVessel = ""
            if (preferred.isNotEmpty()) {
                val hasPreferred = vesselStrings.any { it == preferred }
                if (hasPreferred) {
                    vesselSelect.value = preferred
                    selectedVessel = preferred
                } else {
                    showInvoiceListPlaceholder(
                        "That vessel has no open invoice lines for this client (they may already be invoice-confirmed). Pick another vessel from the list if available.",
                    )
                }
            } else {
                when {
                    vesselStrings.size == 1 -> {
                        val only = vesselStrings[0]
                        vesselSelect.value = only
                        selectedVessel = only
                    }
                    vesselStrings.isEmpty() -> {
                        showInvoiceListPlaceholder(
                            "No chassis to invoice for this client yet. There must be shipping history rows with chassis not already invoice-confirmed. If everything is already confirmed, nothing appears here.",
                        )
                    }
                    else -> {
                        showInvoiceListPlaceholder(
                            "Multiple vessels found for this client. Choose VESSEL above to load shipping date, From/To ports, and chassis rows.",
                        )
                    }
                }
            }

            if (selectedVessel.isNotEmpty()) {
                loadInvoiceShippingLines(client, selectedVessel)
            }

            onDone?.invoke()
        }
        .catch { error: dynamic ->
            val errorMsg = ErrorHandler.handleNetworkError(error, "shipping-history")
            Logger.error("Error loading vessel options: $errorMsg")
            ErrorHandler.showError("Failed to load vessels: $errorMsg")
            showInvoiceListPlaceholder("Could not load vessels for this client. Try again or check the server.")
            onDone?.invoke()
        }
}

fun generateInvoiceNumber(): String {
    // Generate invoice number based on timestamp (last 8 digits)
    // Format: INV-{8-digit-timestamp}
    val timestamp = js("Date.now()") as Double
    val timestampStr = timestamp.toLong().toString()
    val last8Digits = if (timestampStr.length >= 8) {
        timestampStr.substring(timestampStr.length - 8)
    } else {
        timestampStr.padStart(8, '0')
    }
    return "INV-$last8Digits"
}

fun setupInvoicePageListeners() {
    // Generate invoice number button
    document.getElementById("generateInvoiceNumberBtn")?.addEventListener("click", { _: Event ->
        val invoiceNumberInput = document.getElementById("invoiceNumber") as? HTMLInputElement
        if (invoiceNumberInput != null) {
            invoiceNumberInput.value = generateInvoiceNumber()
        }
    })
    
    // Client and Vessel selection listeners
    document.getElementById("invoiceClient")?.addEventListener("change", { _: Event ->
        val client = (document.getElementById("invoiceClient") as? HTMLSelectElement)?.value ?: ""
        if (client.isBlank()) {
            clearInvoiceShippingHeaderFields()
        }
        loadInvoiceVesselOptionsForClient(client)
        handleInvoiceClientVesselChange()
        checkInvoiceLedgerClient(client)
    })
    
    document.getElementById("invoiceVessel")?.addEventListener("change", { _: Event ->
        handleInvoiceClientVesselChange()
    })
    
    document.getElementById("invoiceSaveBtn")?.addEventListener("click", { _: Event ->
        handleInvoiceSave()
    })

    document.getElementById("deleteInvoiceFromRecreate")?.addEventListener("click", { _: Event ->
        handleDeleteInvoiceFromRecreate()
    })

    document.getElementById("invoicePdfBtn")?.addEventListener("click", { _: Event ->
        handleInvoicePdfDownload()
    })

    // Show full preview button
    document.getElementById("showFullPreviewBtn")?.addEventListener("click", { _: Event ->
        Logger.debug("[PREVIEW] Button clicked!")
        handleShowFullPreview()
    })
    
    // Price type radio buttons - update table when changed
    // Line amounts come from shipping_history; price type affects PDF header only.
    document.getElementById("invoiceCnf")?.addEventListener("change", { _: Event -> })
    document.getElementById("invoiceFob")?.addEventListener("change", { _: Event -> })
}

fun handleInvoiceClientVesselChange() {
    val client = (document.getElementById("invoiceClient") as? HTMLSelectElement)?.value ?: ""
    val vessel = (document.getElementById("invoiceVessel") as? HTMLSelectElement)?.value ?: ""
    
    if (client.isNotEmpty() && vessel.isNotEmpty()) {
        loadInvoiceShippingLines(client, vessel)
    } else {
        clearInvoiceListAndTotals()
        // Placeholder until vessels finish loading (single vessel auto-loads in callback).
        if (client.isNotBlank()) {
            showInvoiceListPlaceholder("Loading vessels…")
        }
    }
}

private fun clearInvoiceShippingHeaderFields() {
    (document.getElementById("invoiceFrom") as? HTMLInputElement)?.value = ""
    (document.getElementById("invoiceTo") as? HTMLInputElement)?.value = ""
    (document.getElementById("invoiceShippingDate") as? HTMLInputElement)?.value = ""
    (document.getElementById("invoiceShippingDateText") as? HTMLInputElement)?.value = ""
}

private fun clearInvoiceListAndTotals() {
    val tableBody = document.getElementById("invoiceListTableBody")
    if (tableBody != null) {
        tableBody.innerHTML = ""
    }
    val cardsContainer = document.getElementById("invoiceCardsContainer")
    cardsContainer?.innerHTML = ""
    val totalElement = document.getElementById("invoiceTotalAmount")
    if (totalElement != null) {
        totalElement.textContent = "TOTAL AMOUNT: ¥000,000"
    }
    js("window.currentInvoicePurchaseIds = []")
    js("window.currentInvoicePdfLines = []")
    js("window.currentInvoiceChassisList = []")
}

private fun showInvoiceListPlaceholder(message: String) {
    val tableBody = document.getElementById("invoiceListTableBody") ?: return
    val cardsContainer = document.getElementById("invoiceCardsContainer")
    tableBody.innerHTML = ""
    cardsContainer?.innerHTML = ""
    val esc = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val emptyRow = document.createElement("tr")
    emptyRow.innerHTML =
        """<td colspan="5" style="text-align:center; padding: 16px; color:#495057; font-size: 14px; line-height: 1.4;">$esc</td>"""
    tableBody.appendChild(emptyRow)
    val totalElement = document.getElementById("invoiceTotalAmount")
    totalElement?.textContent = "TOTAL AMOUNT: ¥000,000"
    js("window.currentInvoicePurchaseIds = []")
    js("window.currentInvoicePdfLines = []")
    js("window.currentInvoiceChassisList = []")
}

fun loadInvoiceShippingLines(client: String, vessel: String) {
    val encodedClient = js("encodeURIComponent")(client.trim()) as String
    val encodedVessel = js("encodeURIComponent")(vessel.trim()) as String

    window.fetch(apiUrl("shipping-history/for-invoice/lines?clientName=$encodedClient&vessel=$encodedVessel"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load shipping history')")
        }
        .then { payload: dynamic ->
            if (js("payload && payload.success === true") as Boolean != true) {
                throw js("Error('Invalid shipping history response')")
            }
            applyInvoiceHeaderFromShipping(payload)
            val linesRaw = payload.lines
            val lines = js("Array.isArray(linesRaw) ? linesRaw : []") as Array<dynamic>
            populateInvoiceListFromShippingLines(lines)
        }
        .catch { error: dynamic ->
            val errorMsg = ErrorHandler.handleNetworkError(error, "shipping-history/for-invoice/lines")
            Logger.error("Error loading invoice shipping lines: $errorMsg")
            ErrorHandler.showError("Failed to load invoice lines: $errorMsg")
        }
}

fun populateInvoiceListTable(purchases: Array<dynamic>, historyLineAmounts: List<Double> = emptyList()) {
    val tableBody = document.getElementById("invoiceListTableBody")
    val cardsContainer = document.getElementById("invoiceCardsContainer")
    if (tableBody == null) return
    
    // Store purchase IDs for PDF and Ship Cars functions
    val purchaseIds = mutableListOf<Long>()
    
    // Clear table body and cards container
    tableBody.innerHTML = ""
    cardsContainer?.innerHTML = ""
    
    var totalAmount = 0.0
    var rowNumber = 1
    
    // Populate table rows and mobile cards
    if (purchases.isEmpty()) {
        val emptyRow = document.createElement("tr")
        emptyRow.innerHTML = """<td colspan="5" style="text-align:center; padding: 12px; color:#6c757d;">No car available (booking not requested or filters exclude all rows)</td>"""
        tableBody.appendChild(emptyRow)
    }

    val pdfLinesArr = js("[]")
    var purchaseIndex = 0
    for (purchase in purchases) {
        val id = js("purchase.id")?.toString()?.toLongOrNull()
        if (id != null) {
            purchaseIds.add(id)
        }
        
        val chassis = js("purchase.chassis")?.toString() ?: "N/A"
        val carName = js("purchase.carName")?.toString() ?: "N/A"
        val year = js("purchase.carModelYear")?.toString() ?: "N/A"
        
        val amount = historyLineAmounts.getOrNull(purchaseIndex)
            ?: purchaseInvoiceLineAmountYenFromDynamic(purchase)
        purchaseIndex++
        totalAmount += amount
        if (id != null) {
            js("(function(a, p, m) { a.push({ purchaseId: p, amount: m }); })")(pdfLinesArr, id, amount)
        }
        
        // Format amount with ¥ symbol and commas
        val amountInt = amount.toInt()
        val amountStr = amountInt.toString()
        val reversed = amountStr.reversed()
        val chunked = reversed.chunked(3)
        val joined = chunked.joinToString(",")
        val finalReversed = joined.reversed()
        val formattedAmount = "¥$finalReversed"
        
        // Add table row (for tablet/desktop)
        val row = document.createElement("tr")
        row.innerHTML = """
            <td>$rowNumber</td>
            <td>$chassis</td>
            <td>$carName</td>
            <td>$year</td>
            <td>$formattedAmount</td>
        """
        tableBody.appendChild(row)
        
        // Add mobile card
        if (cardsContainer != null) {
            val card = document.createElement("div")
            card.className = "invoice-card-item"
            card.innerHTML = """
                <div class="card-row">
                    <span class="card-label">NO.</span>
                    <span class="card-value">$rowNumber</span>
                </div>
                <div class="card-row">
                    <span class="card-label">CHASSIS</span>
                    <span class="card-value">$chassis</span>
                </div>
                <div class="card-row">
                    <span class="card-label">NAME</span>
                    <span class="card-value">$carName</span>
                </div>
                <div class="card-row">
                    <span class="card-label">AMOUNT</span>
                    <span class="card-amount">$formattedAmount</span>
                </div>
            """
            cardsContainer.appendChild(card)
        }
        
        rowNumber++
    }
    
    // Update total amount
    val totalElement = document.getElementById("invoiceTotalAmount")
    val totalInt = totalAmount.toInt()
    val totalStr = totalInt.toString()
    val totalReversed = totalStr.reversed()
    val totalChunked = totalReversed.chunked(3)
    val totalJoined = totalChunked.joinToString(",")
    val totalFinalReversed = totalJoined.reversed()
    val formattedTotal = "¥$totalFinalReversed"
    totalElement?.textContent = "TOTAL AMOUNT: $formattedTotal"
    
    // Store purchase IDs in JavaScript variable
    val idsArray = js("[]")
    purchaseIds.forEach { id ->
        js("idsArray.push(id)")
    }
    js("window.currentInvoicePurchaseIds = idsArray")
    if (historyLineAmounts.isNotEmpty()) {
        js("window.currentInvoicePdfLines = pdfLinesArr")
    } else {
        syncWindowInvoicePdfLinesFromPurchases(purchases)
    }
    syncWindowInvoiceChassisFromPurchases(purchases)
}

/** Semicolon-separated yen amounts from invoice_history (same order as chassis tokens). */
private fun parseInvoiceHistoryAmountTokens(raw: String): List<Double> {
    if (raw.isBlank()) return emptyList()
    return raw.split(';')
        .mapNotNull { token ->
            val cleaned = token.trim().replace(Regex("[^0-9.-]"), "")
            cleaned.toDoubleOrNull()?.takeIf { it > 0.0 }
        }
}

private fun formatInvoiceYenInt(amount: Double): String {
    val amountInt = amount.toInt()
    val amountStr = amountInt.toString()
    val reversed = amountStr.reversed()
    val chunked = reversed.chunked(3)
    val joined = chunked.joinToString(",")
    val finalReversed = joined.reversed()
    return "¥$finalReversed"
}

/** PDF line amounts: shipping_history amounts when loaded from invoice slice; else derived from purchases. */
private fun syncWindowInvoicePdfLinesFromShipping(lines: Array<dynamic>) {
    val arr = js("[]")
    for (line in lines) {
        val pid = line.purchaseId
        val amt = line.amount?.toString()?.toDoubleOrNull() ?: 0.0
        js("(function(a, p, m) { a.push({ purchaseId: p, amount: m }); })")(arr, pid, amt)
    }
    js("window.currentInvoicePdfLines = arr")
}

/** Chassis column values in LIST table order, for invoice_history.chassis (semicolon-separated). */
private fun syncWindowInvoiceChassisFromShippingLines(lines: Array<dynamic>) {
    val arr = js("[]")
    for (line in lines) {
        val raw = js("line.chassis")?.toString()?.trim()
        val c = if (raw.isNullOrEmpty()) "N/A" else raw
        js("(function(a, x) { a.push(x); })")(arr, c)
    }
    js("window.currentInvoiceChassisList = arr")
}

private fun syncWindowInvoiceChassisFromPurchases(purchases: Array<dynamic>) {
    val arr = js("[]")
    for (purchase in purchases) {
        val raw = js("purchase.chassis")?.toString()?.trim()
        val c = if (raw.isNullOrEmpty()) "N/A" else raw
        js("(function(a, x) { a.push(x); })")(arr, c)
    }
    js("window.currentInvoiceChassisList = arr")
}

private fun syncWindowInvoicePdfLinesFromPurchases(purchases: Array<dynamic>) {
    val arr = js("[]")
    for (purchase in purchases) {
        val id = purchase.id?.toString()?.toLongOrNull() ?: continue
        val amount = purchaseInvoiceLineAmountYenFromDynamic(purchase)
        js("(function(a, p, m) { a.push({ purchaseId: p, amount: m }); })")(arr, id, amount)
    }
    js("window.currentInvoicePdfLines = arr")
}

private fun buildInvoicePdfDescription(purchase: dynamic): String {
    val chassis = js("purchase.chassis")?.toString()?.trim() ?: ""
    val carName = js("purchase.carName")?.toString()?.trim() ?: ""
    val grade = js("purchase.grade")?.toString()?.trim() ?: ""
    val carModelYear = js("purchase.carModelYear")?.toString()?.trim() ?: ""
    val shift = js("purchase.shift")?.toString()?.trim() ?: ""
    val door = js("purchase.door")?.toString()?.trim() ?: ""
    val seat = js("purchase.seat")?.toString()?.trim() ?: ""
    val cc = js("purchase.cc")?.toString()?.trim() ?: ""
    val color = js("purchase.color")?.toString()?.trim() ?: ""
    val distance = js("purchase.distance")?.toString()?.trim() ?: ""
    val fuel = js("purchase.fuel")?.toString()?.trim() ?: ""

    val line1 = buildString {
        if (chassis.isNotEmpty()) append(chassis)
        if (carName.isNotEmpty()) {
            if (isNotEmpty()) append("   ")
            append(carName)
        }
        if (grade.isNotEmpty()) {
            if (isNotEmpty()) append("   ")
            append(grade)
        }
        if (carModelYear.isNotEmpty()) {
            if (isNotEmpty()) append("     ")
            append(carModelYear)
        }
        if (shift.isNotEmpty()) {
            if (isNotEmpty()) append("     ")
            append(shift)
        }
        val doorSeatParts = mutableListOf<String>()
        if (door.isNotEmpty()) doorSeatParts.add("$door DOOR")
        if (seat.isNotEmpty()) doorSeatParts.add("$seat SEAT")
        if (doorSeatParts.isNotEmpty()) {
            if (isNotEmpty()) append(", ")
            append(doorSeatParts.joinToString(", "))
        }
    }

    val line2 = buildString {
        if (cc.isNotEmpty()) append("${cc}CC")
        if (color.isNotEmpty()) {
            if (isNotEmpty()) append("       ")
            append(color)
        }
        if (distance.isNotEmpty()) {
            if (isNotEmpty()) append("     ")
            append(distance)
        }
        if (fuel.isNotEmpty()) {
            if (isNotEmpty()) append("     ")
            append(fuel)
        }
    }

    return if (line2.isNotEmpty()) "$line1\n$line2" else line1
}

/**
 * Builds PDF line items using [window.currentInvoicePdfLines] ({ purchaseId, amount }) order when set;
 * otherwise falls back to [selectedIds] order with C&F/FOB amounts from purchases.
 */
private fun buildInvoicePdfItemsAndTotal(
    allPurchases: Array<dynamic>,
    selectedIds: List<Long>,
): Pair<MutableList<dynamic>, Double> {
    val idToPurchase = mutableMapOf<Long, dynamic>()
    for (purchase in allPurchases) {
        val id = js("purchase.id")?.toString()?.toLongOrNull() ?: continue
        idToPurchase[id] = purchase
    }

    val pdfLines = js("window.currentInvoicePdfLines")
    val useSpecs = pdfLines != null && (js("Array.isArray(pdfLines) && pdfLines.length > 0") as Boolean)

    val itemsList = mutableListOf<dynamic>()
    var totalAmount = 0.0
    var unitNumber = 1

    if (useSpecs) {
        val specs = pdfLines.unsafeCast<Array<dynamic>>()
        for (spec in specs) {
            val purchaseId = js("spec.purchaseId")?.toString()?.toLongOrNull() ?: continue
            val purchase = idToPurchase[purchaseId] ?: continue
            val rawAmt = js("spec.amount")
            val lineAmount = when (rawAmt) {
                is Number -> rawAmt.toDouble()
                else -> rawAmt?.toString()?.toDoubleOrNull() ?: 0.0
            }
            totalAmount += lineAmount
            val formattedAmount = formatInvoiceYenInt(lineAmount)
            val item = js("{}").unsafeCast<dynamic>()
            item.unit = unitNumber
            item.description = buildInvoicePdfDescription(purchase)
            item.amount = formattedAmount
            itemsList.add(item)
            unitNumber++
            }
        } else {
        for (purchaseId in selectedIds) {
            val purchase = idToPurchase[purchaseId] ?: continue
            val amount = purchaseInvoiceLineAmountYenFromDynamic(purchase)
            totalAmount += amount
            val item = js("{}").unsafeCast<dynamic>()
            item.unit = unitNumber
            item.description = buildInvoicePdfDescription(purchase)
            item.amount = formatInvoiceYenInt(amount)
            itemsList.add(item)
            unitNumber++
        }
    }

    return Pair(itemsList, totalAmount)
}

fun populateInvoiceListFromShippingLines(lines: Array<dynamic>) {
    val tableBody = document.getElementById("invoiceListTableBody")
    val cardsContainer = document.getElementById("invoiceCardsContainer")
    if (tableBody == null) return

    val purchaseIds = mutableListOf<Long>()
    tableBody.innerHTML = ""
    cardsContainer?.innerHTML = ""

    var totalAmount = 0.0
    var rowNumber = 1

    if (lines.isEmpty()) {
        val emptyRow = document.createElement("tr")
        emptyRow.innerHTML =
            """<td colspan="5" style="text-align:center; padding: 16px; color:#6c757d; font-size: 14px; line-height: 1.4;">No chassis rows for this shipment (all listed cars may already be invoice-confirmed, or purchases are missing for these chassis).</td>"""
        tableBody.appendChild(emptyRow)
        js("window.currentInvoicePurchaseIds = []")
        js("window.currentInvoicePdfLines = []")
        js("window.currentInvoiceChassisList = []")
        val totalElement = document.getElementById("invoiceTotalAmount")
        totalElement?.textContent = "TOTAL AMOUNT: ¥000,000"
        return
    }

    for (line in lines) {
        val pid = js("line.purchaseId")?.toString()?.toLongOrNull()
        if (pid != null) purchaseIds.add(pid)

        val chassis = js("line.chassis")?.toString() ?: "N/A"
        val carName = js("line.carName")?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A"
        val year = js("line.carModelYear")?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "N/A"
        val amount = js("line.amount")?.toString()?.toDoubleOrNull() ?: 0.0
        totalAmount += amount
        val formattedAmount = formatInvoiceYenInt(amount)

        val row = document.createElement("tr")
        row.innerHTML = """
            <td>$rowNumber</td>
            <td>$chassis</td>
            <td>$carName</td>
            <td>$year</td>
            <td>$formattedAmount</td>
        """
        tableBody.appendChild(row)

        if (cardsContainer != null) {
            val card = document.createElement("div")
            card.className = "invoice-card-item"
            card.innerHTML = """
                <div class="card-row">
                    <span class="card-label">NO.</span>
                    <span class="card-value">$rowNumber</span>
                </div>
                <div class="card-row">
                    <span class="card-label">CHASSIS</span>
                    <span class="card-value">$chassis</span>
                </div>
                <div class="card-row">
                    <span class="card-label">NAME</span>
                    <span class="card-value">$carName</span>
                </div>
                <div class="card-row">
                    <span class="card-label">AMOUNT</span>
                    <span class="card-amount">$formattedAmount</span>
                </div>
            """
            cardsContainer.appendChild(card)
        }
        rowNumber++
    }

    val totalElement = document.getElementById("invoiceTotalAmount")
    totalElement?.textContent = "TOTAL AMOUNT: ${formatInvoiceYenInt(totalAmount)}"

    val idsArray = js("[]")
    purchaseIds.forEach { id -> js("idsArray.push(id)") }
    js("window.currentInvoicePurchaseIds = idsArray")
    syncWindowInvoicePdfLinesFromShipping(lines)
    syncWindowInvoiceChassisFromShippingLines(lines)
}

private fun applyInvoiceHeaderFromShipping(payload: dynamic) {
    val header = payload.header
    if (header == null) return

    val shipmentDateRaw = header.shipmentDate?.toString()?.trim() ?: ""
    if (shipmentDateRaw.isNotEmpty()) {
        val iso = shipmentDateRaw.take(10)
        val hid = document.getElementById("invoiceShippingDate") as? HTMLInputElement
        if (hid != null) {
            hid.value = iso
            val txt = document.getElementById("invoiceShippingDateText") as? HTMLInputElement
            if (txt != null) txt.value = isoToMmDdYyyy(iso)
        }
    }

    val polValue = header.pol?.toString()?.trim() ?: ""
    if (polValue.isNotEmpty()) {
        (document.getElementById("invoiceFrom") as? HTMLInputElement)?.value = polValue
    }

    val podValue = header.pod?.toString()?.trim() ?: ""
    if (podValue.isNotEmpty()) {
        (document.getElementById("invoiceTo") as? HTMLInputElement)?.value = podValue
    }

    val rawPrice = header.priceType?.toString()?.trim() ?: ""
    val upper = rawPrice.uppercase()
    val invoiceCnf = document.getElementById("invoiceCnf") as? HTMLInputElement
    val invoiceFob = document.getElementById("invoiceFob") as? HTMLInputElement
    when {
        upper.contains("FOB") -> {
            invoiceFob?.checked = true
            invoiceCnf?.checked = false
        }
        upper.contains("CNF") || upper.contains("C&F") || rawPrice.contains("C&F") -> {
            invoiceCnf?.checked = true
            invoiceFob?.checked = false
        }
        else -> { }
    }
}

private fun escapeJsonStringForInvoice(str: String): String =
    str.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

private fun parseInvoiceTotalFromPage(): Double? {
    val raw = (document.getElementById("invoiceTotalAmount") as? HTMLElement)?.textContent?.trim().orEmpty()
    if (raw.isEmpty()) return null
    val digits = raw.replace(Regex("[^0-9]"), "")
    if (digits.isEmpty()) return null
    return digits.toDoubleOrNull()
}

private fun checkInvoiceLedgerClient(clientName: String) {
    val name = clientName.trim()
    if (name.isEmpty()) return
    val encodedName = js("encodeURIComponent(name)") as String
    val invoiceNumber = (document.getElementById("invoiceNumber") as? HTMLInputElement)?.value?.trim().orEmpty()
    val invoiceAmount = parseInvoiceTotalFromPage()
    val invoiceNumberPart = if (invoiceNumber.isNotEmpty()) {
        "&invoiceNumber=${js("encodeURIComponent(invoiceNumber)")}"
    } else {
        ""
    }
    val invoiceAmountPart = if (invoiceAmount != null && invoiceAmount > 0.0) {
        "&invoiceAmount=$invoiceAmount"
    } else {
        ""
    }
    val currentIds = js("window.currentInvoicePurchaseIds") as? Array<dynamic>
    val idsQuery = if (currentIds != null && currentIds.isNotEmpty()) {
        currentIds.mapNotNull { id ->
            when (id) {
                is Number -> id.toLong()
                is String -> id.toLongOrNull()
                else -> null
            }
        }.joinToString("") { id -> "&purchaseIds=$id" }
    } else {
        ""
    }
    window.fetch(
        apiUrl("clients/resolve-ledger?name=$encodedName$idsQuery$invoiceNumberPart$invoiceAmountPart"),
    ).then { response ->
        if (!response.ok) return@then
        response.json().then { json ->
            val status = js("json.creditLimitStatus")?.toString()?.trim().orEmpty()
            val creditMsg = js("json.creditLimitMessage")?.toString()?.trim().orEmpty()
            if (creditMsg.isNotEmpty()) {
                if (status == "OVER_LIMIT") {
                    showCreditLimitExceededModal(creditMsg)
                    return@then
                }
                showMessage(creditMsg, "warning")
            }
            val warning = js("json.warning")?.toString()?.trim().orEmpty()
            if (warning.isNotEmpty()) {
                showMessage(warning, "warning")
                return@then
            }
            val info = js("json.info")?.toString()?.trim().orEmpty()
            if (info.isNotEmpty()) {
                showMessage(info, "info")
            }
        }
    }
}

/**
 * Loads purchases, builds invoice JSON. [mode]: `"save"` → POST `/invoice/save`;
 * `"preview"` / `"pdf"` → POST `/invoice/generate-pdf` (open vs download).
 */
private fun invoiceBuildPayloadAndRun(mode: String) {
    if (mode == "preview") {
        Logger.debug("[PREVIEW] invoiceBuildPayloadAndRun(preview)")
    } else {
        Logger.debug("invoiceBuildPayloadAndRun mode=$mode")
    }

    // Get purchase IDs from current invoice purchases
    val currentIds = js("window.currentInvoicePurchaseIds") as? Array<dynamic>
    val selectedIds = if (currentIds != null) {
        currentIds.mapNotNull { id ->
            when (id) {
                is Number -> id.toLong()
                is String -> id.toLongOrNull()
                else -> null
            }
        }
    } else {
        emptyList<Long>()
    }
    
    if (selectedIds.isEmpty()) {
        showMessage("No purchases selected. Please select CLIENT and VESSEL first.", "warning")
        return
    }
    
    // Collect form field values
    val invoiceNumber = (document.getElementById("invoiceNumber") as? HTMLInputElement)?.value?.trim() ?: ""
    val invoiceLcNo = (document.getElementById("invoiceLcNo") as? HTMLInputElement)?.value?.trim() ?: ""
    val invoiceClient = (document.getElementById("invoiceClient") as? HTMLSelectElement)?.value?.trim() ?: ""
    val invoiceVessel = (document.getElementById("invoiceVessel") as? HTMLSelectElement)?.value?.trim() ?: ""
    val invoiceShippingDate = (document.getElementById("invoiceShippingDate") as? HTMLInputElement)?.value?.trim() ?: ""
    val invoiceFrom = (document.getElementById("invoiceFrom") as? HTMLInputElement)?.value?.trim() ?: ""
    val invoiceTo = (document.getElementById("invoiceTo") as? HTMLInputElement)?.value?.trim() ?: ""
    val invoiceMessage = (document.getElementById("invoiceMessage") as? HTMLTextAreaElement)?.value?.trim() ?: ""
    
    // Get price type (C&F or FOB)
    val invoiceCnf = document.getElementById("invoiceCnf") as? HTMLInputElement
    val invoiceFob = document.getElementById("invoiceFob") as? HTMLInputElement
    val priceType = when {
        invoiceCnf?.checked == true -> "C&F"
        invoiceFob?.checked == true -> "FOB"
        else -> "C&F"
    }
    
    // Get bank account - use value attribute which preserves newlines
    val invoiceBankAccount = document.getElementById("invoiceBankAccount") as? HTMLSelectElement
    val bankAccountValue = invoiceBankAccount?.value?.trim() ?: ""
    
    // Validate required fields
    if (invoiceNumber.isEmpty()) {
        showMessage("Please enter an invoice number", "warning")
        return
    }
    
    if (invoiceClient.isEmpty()) {
        showMessage("Please enter a client name", "warning")
        return
    }
    
    // Parse client name and address
    val clientParts = invoiceClient.split("\n", limit = 2)
    val clientName = clientParts[0].trim()
    val clientAddress = if (clientParts.size > 1) clientParts[1].trim() else null
    
    // Get current date for invoice date
    val currentDate = js("new Date()").unsafeCast<dynamic>()
    val year = currentDate.getFullYear() as Int
    val month = ((currentDate.getMonth() as Int) + 1).toString().padStart(2, '0')
    val day = (currentDate.getDate() as Int).toString().padStart(2, '0')
    val invoiceDate = year.toString() + "-" + month + "-" + day
    
    // Format shipping date (convert from YYYY-MM-DD to DD.MMM.YYYY format)
    val formattedShippingDate = if (invoiceShippingDate.isNotEmpty()) {
        try {
            val dateParts = invoiceShippingDate.split("-")
            if (dateParts.size == 3) {
                val months = arrayOf("", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
                val monthNum = dateParts[1].toIntOrNull() ?: 0
                val monthName = if (monthNum in 1..12) months[monthNum] else dateParts[1]
                dateParts[2] + "." + monthName + "." + dateParts[0]
            } else {
                invoiceShippingDate
            }
        } catch (e: dynamic) {
            invoiceShippingDate
        }
    } else {
        ""
    }
    
    // Fetch purchase data to get all fields including brand, door, seat, fuel
    window.fetch(apiUrl("purchases")).then { response ->
        if (!response.ok) {
            showMessage("Failed to load purchase details", "error")
            return@then
        }
        response.json().then { allPurchases ->
            val purchasesArray = allPurchases as Array<dynamic>
            val selectedPurchasesList = mutableListOf<dynamic>()
            
            // Filter to only selected purchases
            for (purchase in purchasesArray) {
                val id = js("purchase.id").toString().toLongOrNull()
                if (id != null && selectedIds.contains(id)) {
                    selectedPurchasesList.add(purchase)
                }
            }
            
            if (selectedPurchasesList.isEmpty()) {
                showMessage("No items found for selected purchases", "warning")
                return@then
            }
            
            val built = buildInvoicePdfItemsAndTotal(purchasesArray, selectedIds)
            val itemsList = built.first
            if (itemsList.isEmpty()) {
                showMessage("No items found for selected purchases", "warning")
                return@then
            }
            val totalAmount = built.second
            val formattedTotal = formatInvoiceYenInt(totalAmount)

            // Prepare request body - build JSON string manually to avoid Kotlin/JS interop issues
            val itemsJsonArray = itemsList.joinToString(",") { item ->
                val itemDynamic = item.unsafeCast<dynamic>()
                val unit = itemDynamic.unit?.toString() ?: "0"
                val description = escapeJsonStringForInvoice((itemDynamic.description as? String) ?: "")
                val amount = escapeJsonStringForInvoice((itemDynamic.amount as? String) ?: "")
                "{\"unit\":" + unit + ",\"description\":\"" + description + "\",\"amount\":\"" + amount + "\"}"
            }
            
            val lcNumberPart = if (invoiceLcNo.isNotEmpty()) {
                "\"" + escapeJsonStringForInvoice(invoiceLcNo) + "\""
            } else {
                "null"
            }
            val clientAddressPart = if (clientAddress != null) {
                "\"" + escapeJsonStringForInvoice(clientAddress) + "\""
            } else {
                "null"
            }
            val bankAccountPart = if (bankAccountValue.isNotEmpty()) {
                "\"" + escapeJsonStringForInvoice(bankAccountValue) + "\""
            } else {
                "null"
            }
            val messagePart = if (invoiceMessage.isNotEmpty()) {
                "\"" + escapeJsonStringForInvoice(invoiceMessage) + "\""
            } else {
                "null"
            }
            
            val requestBodyJson = "{\"invoiceNumber\":\"" + escapeJsonStringForInvoice(invoiceNumber) + 
                "\",\"invoiceDate\":\"" + escapeJsonStringForInvoice(invoiceDate) + 
                "\",\"lcNumber\":" + lcNumberPart + 
                ",\"clientName\":\"" + escapeJsonStringForInvoice(clientName) + 
                "\",\"clientAddress\":" + clientAddressPart + 
                ",\"vessel\":\"" + escapeJsonStringForInvoice(invoiceVessel) + 
                "\",\"shippingDate\":\"" + escapeJsonStringForInvoice(formattedShippingDate) + 
                "\",\"from\":\"" + escapeJsonStringForInvoice(invoiceFrom) + 
                "\",\"to\":\"" + escapeJsonStringForInvoice(invoiceTo) + 
                "\",\"priceType\":\"" + escapeJsonStringForInvoice(priceType) + 
                "\",\"items\":[" + itemsJsonArray + 
                "],\"totalAmount\":\"" + escapeJsonStringForInvoice(formattedTotal) + 
                "\",\"bankAccount\":" + bankAccountPart + 
                ",\"message\":" + messagePart + 
                "}"

            val chassisList = js("window.currentInvoiceChassisList || []") as Array<dynamic>
            val chassisJoinedRaw = chassisList.joinToString(";") { it?.toString()?.trim() ?: "" }
            val shippingDateIsoPart = if (invoiceShippingDate.isNotEmpty()) {
                "\"" + escapeJsonStringForInvoice(invoiceShippingDate) + "\""
            } else {
                "null"
            }
            val purchaseIdsCsv = selectedIds.joinToString(",")
            val wrappedBody =
                "{\"purchaseIds\":[" + purchaseIdsCsv + "],\"chassisJoined\":\"" +
                escapeJsonStringForInvoice(chassisJoinedRaw) + "\",\"shippingDateIso\":" + shippingDateIsoPart +
                ",\"pdf\":" + requestBodyJson + "}"

            when (mode) {
                "save" -> {
                    Logger.debug("Sending invoice save request")
                    val headers = Headers()
                    headers.set("Content-Type", "application/json")
                    val requestInit = RequestInit(
                        method = "POST",
                        headers = headers,
                        body = wrappedBody,
                    )
                    window.fetch(apiUrl("purchases/invoice/save"), requestInit).then { response ->
                        val status = response.status.toInt()
                        if (response.ok) {
                            response.json().then { json ->
                                Logger.debug("Invoice saved successfully")
                                val creditStatus = js("json.creditLimitStatus")?.toString()?.trim().orEmpty()
                                val creditMsg = js("json.creditLimitMessage")?.toString()?.trim().orEmpty()
                                if (creditMsg.isNotEmpty() && creditStatus == "NEAR_LIMIT") {
                                    showMessage(creditMsg, "warning")
                                }
                                val warning = js("json.ledgerWarning")?.toString()?.trim().orEmpty()
                                if (warning.isNotEmpty() && warning != creditMsg) {
                                    showMessage(warning, "warning")
                                }
                                val info = js("json.ledgerInfo")?.toString()?.trim().orEmpty()
                                if (info.isNotEmpty()) {
                                    showMessage(info, "info")
                                }
                                showSuccessModal("Saved", "Invoice saved successfully")
                            }
                        } else {
                            response.text().then { errorText ->
                                handleInvoiceSaveFailure(status, errorText)
                            }
                        }
                    }.catch { error ->
                        Logger.error("Error saving invoice: ${error.toString()}")
                        showMessage("Error saving invoice: ${error.toString()}", "error")
                    }
                }
                in setOf("preview", "pdf") -> {
                    Logger.debug("Invoice generate-pdf request mode=$mode")
                    val headers = Headers()
                    headers.set("Content-Type", "application/json")
                    val requestInit = RequestInit(
                        method = "POST",
                        headers = headers,
                        body = requestBodyJson,
                    )
                    window.fetch(apiUrl("purchases/invoice/generate-pdf"), requestInit).then { response ->
                        if (response.ok) {
                            response.blob().then { blob ->
                                val url = js("URL.createObjectURL(blob)") as String
                                var urlRevoked = false
                                fun revokeUrl() {
                                    if (!urlRevoked) {
                                        try {
                                            js("URL.revokeObjectURL(url)")
                                            urlRevoked = true
                                        } catch (e: dynamic) {
                                            Logger.warn("Failed to revoke URL: ${e.toString()}")
                                        }
                                    }
                                }
                                if (mode == "preview") {
                                    try {
                                        val newWindow = js("window.open(url, '_blank')")
                                        if (newWindow == null) {
                                            Logger.warn("Popup blocked, falling back to download")
                                            val a = document.createElement("a") as HTMLAnchorElement
                                            a.href = url
                                            a.download = "invoice_" + invoiceNumber + ".pdf"
                                            document.body?.appendChild(a)
                                            a.click()
                                            document.body?.removeChild(a)
                                            showMessage("Popup blocked. PDF downloaded instead.", "info")
                                            window.setTimeout({ revokeUrl() }, 1000)
                                        } else {
                                            Logger.debug("Invoice PDF preview opened successfully")
                                            showMessage("✅ Invoice PDF preview opened", "success")
                                            js("""
                                                if (newWindow) {
                                                    newWindow.addEventListener('beforeunload', function() {
                                                        URL.revokeObjectURL(url);
                                                    });
                                                }
                                            """)
                                            window.setTimeout({ revokeUrl() }, 300000)
                                        }
                                    } catch (e: dynamic) {
                                        Logger.error("Error opening PDF: ${e.toString()}")
                                        revokeUrl()
                                        showMessage("Error opening PDF preview", "error")
                                    }
                                } else {
                                    try {
                                        val a = document.createElement("a") as HTMLAnchorElement
                                        a.href = url
                                        a.download = "invoice_" + invoiceNumber + ".pdf"
                                        document.body?.appendChild(a)
                                        a.click()
                                        document.body?.removeChild(a)
                                        window.setTimeout({ revokeUrl() }, 1000)
                                        Logger.debug("Invoice PDF downloaded")
                                        showMessage("PDF downloaded successfully", "success")
                                    } catch (e: dynamic) {
                                        Logger.error("Error downloading PDF: ${e.toString()}")
                                        revokeUrl()
                                        showMessage("Error downloading PDF", "error")
                                    }
                                }
                            }.catch { error ->
                                Logger.error("Error processing blob: ${error.toString()}")
                                showMessage("Error processing PDF: ${error.toString()}", "error")
                            }
                        } else {
                            response.text().then { errorText ->
                                Logger.error("Invoice PDF generation failed: $errorText")
                                showMessage("Failed to generate invoice PDF: $errorText", "error")
                            }
                        }
                    }.catch { error ->
                        Logger.error("Error generating invoice PDF: ${error.toString()}")
                        showMessage("Error generating invoice PDF: ${error.toString()}", "error")
                    }
                }
                else -> Logger.warn("Unknown invoice action mode: $mode")
            }
        }.catch { error ->
            Logger.error("Error loading purchases: ${error.toString()}")
            showMessage("Error loading purchase details", "error")
        }
    }
}

fun handleShowFullPreview() {
    invoiceBuildPayloadAndRun("preview")
}

fun handleInvoiceSave() {
    invoiceBuildPayloadAndRun("save")
}

fun handleInvoicePdfDownload() {
    invoiceBuildPayloadAndRun("pdf")
}

private fun resolveInvoiceNumberForRecreateDelete(): String {
    restoreInvoiceRecreateMetaFromSession()
    val fromSession = invoiceRecreateInvoiceNumber?.trim().orEmpty()
    if (fromSession.isNotEmpty()) return fromSession
    return (document.getElementById("invoiceNumber") as? HTMLInputElement)?.value?.trim().orEmpty()
}

private fun handleDeleteInvoiceFromRecreate() {
    val ok = window.confirm("Are you sure you want to Delete the invoice?")
    if (!ok) return
    MainScope().launch {
        val invoiceNumber = resolveInvoiceNumberForRecreateDelete()
        if (invoiceNumber.isEmpty()) {
            showMessage("No invoice is linked to this session.", "error")
            return@launch
        }
        val numbersArr = js("[]")
        numbersArr.push(invoiceNumber)
        val body = js("{}")
        body.invoiceNumbers = numbersArr
        ApiClient.post<dynamic>("invoice-history/batch-delete", body).fold(
            onSuccess = { data ->
                val d = (data as Any).unsafeCast<dynamic>()
                val n = d.deleted
                val deleted = when (n) {
                    is Number -> n.toInt()
                    else -> n?.toString()?.toIntOrNull() ?: 0
                }
                if (deleted <= 0) {
                    showMessage("Invoice could not be deleted (not found).", "warning")
                    return@fold
                }
                val reversedRaw = d.ledgerReversed
                val ledgerReversed = when (reversedRaw) {
                    is Number -> reversedRaw.toInt()
                    else -> reversedRaw?.toString()?.toIntOrNull() ?: 0
                }
                val warningsRaw = d.ledgerWarnings
                val ledgerWarnings: List<String> = if (warningsRaw != null && js("Array.isArray(warningsRaw)") as Boolean) {
                    val arr = warningsRaw.unsafeCast<Array<*>>()
                    (0 until arr.size).mapNotNull { arr[it]?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                } else {
                    emptyList()
                }
                clearInvoiceRecreateSessionData()
                when {
                    ledgerWarnings.isNotEmpty() -> {
                        showMessage(
                            "Invoice deleted. Ledger warning: ${ledgerWarnings.joinToString(" ")}",
                            "warning",
                        )
                    }
                    ledgerReversed > 0 -> {
                        showMessage("Invoice deleted. $ledgerReversed ledger reversal(s) posted.", "success")
                    }
                    else -> showMessage("Invoice deleted.", "success")
                }
                navigateToApp("/invoice-history")
            },
            onError = { message, statusCode ->
                val msg =
                    if (statusCode == 400 && message.isNotBlank()) message
                    else "Failed to delete invoice: $message"
                showMessage(msg, "error")
            },
        )
    }
}

