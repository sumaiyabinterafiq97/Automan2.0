package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

private var portalLedger: dynamic = null
private var portalLedgerSortField: String = "eventDate"
private var portalLedgerSortOrder: String = "desc"

internal suspend fun renderClientLedger(generation: Int) {
    when (val result = ApiClient.request<dynamic>("portal/client/ledger")) {
        is ApiResult.Error -> renderPortalFailure(generation, result.message, result.statusCode)
        is ApiResult.Success -> {
            portalLedger = result.data
            drawPortalLedger(generation)
        }
    }
}

private fun drawPortalLedger(generation: Int) {
    val account = portalLedger ?: return
    setPortalBody(generation, portalLedgerHtml(account))
    document.getElementById("portalStatementPdfBtn")?.addEventListener("click", { _: Event ->
        openPortalStatementPdf()
    })
    document.getElementById("portalLedgerExportBtn")?.addEventListener("click", { _: Event ->
        exportPortalLedger()
    })
    document.getElementById("portalCreditLimitBtn")?.addEventListener("click", { _: Event ->
        openPortalCreditLimit(generation)
    })
    eachPortalLedgerElement("#portalClientLedger button[data-ledger-sort]") { button ->
        button.addEventListener("click", { _: Event ->
            val field = button.getAttribute("data-ledger-sort") ?: return@addEventListener
            portalLedgerSortOrder = if (portalLedgerSortField == field && portalLedgerSortOrder == "asc") "desc" else "asc"
            portalLedgerSortField = field
            drawPortalLedger(generation)
        })
    }
}

private fun portalLedgerHtml(account: dynamic): String {
    val name = jsText(account.clientName).ifBlank { "—" }
    val status = jsText(account.status).ifBlank { "N/A" }
    val currency = jsText(account.currency).ifBlank { "JPY" }
    val balance = portalLedgerNumber(account.currentBalance) ?: 0.0
    val limit = portalLedgerNumber(account.creditLimit)
    val limitText = limit?.let { "¥${it.toLong()}" } ?: "—"
    val availableText = if (limit == null) "—" else portalLedgerBalanceText(limit + balance)
    val limitLabel = if (limit != null) "Edit credit limit" else "Set credit limit"
    val events = portalLedgerSorted(jsArray(account.events))
    val ledger = if (events.isEmpty()) {
        """<div class="portal-state" role="status">No ledger entries yet. Invoices post automatically when confirmed in Invoice.</div>"""
    } else {
        portalLedgerTable(events)
    }
    val balanceClass = if (balance < 0) "portal-money portal-money--negative" else if (balance > 0) "portal-money portal-money--positive" else "portal-money"
    return """
        <section id="portalClientLedger">
            <style>
                #portalClientLedger .portal-ledger-card{background:#fff;border:1px solid #e2e8f0;border-radius:12px;padding:16px 18px 18px;box-shadow:0 1px 2px rgba(15,23,42,.04);}
                #portalClientLedger .portal-ledger-head{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;flex-wrap:wrap;margin-bottom:14px;}
                #portalClientLedger h2{margin:0;font-size:18px;color:#2c3e50;}
                #portalClientLedger .portal-ledger-actions{display:flex;flex-wrap:wrap;gap:8px;}
                #portalClientLedger .portal-ledger-btn{height:36px;padding:0 14px;border-radius:8px;border:1px solid #2c3e50;background:#2c3e50;color:#fff;font-size:14px;font-weight:600;cursor:pointer;}
                #portalClientLedger .portal-ledger-btn:hover{background:#1f2d3a;}
                #portalClientLedger .portal-ledger-btn:focus-visible,#portalClientLedger .portal-ledger-btn--quiet:focus-visible{outline:2px solid #2c3e50;outline-offset:2px;}
                #portalClientLedger .portal-ledger-btn--quiet{background:#fff;color:#2c3e50;}
                #portalClientLedger .portal-ledger-btn--quiet:hover{background:#f8fafc;}
                #portalClientLedger .portal-ledger-layout{display:grid;grid-template-columns:minmax(0,1fr) minmax(180px,240px);gap:16px;align-items:start;}
                #portalClientLedger .portal-ledger-name{margin:0 0 10px;font-size:18px;color:#1f2937;}
                #portalClientLedger .portal-ledger-facts{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:8px 16px;margin:0;}
                #portalClientLedger .portal-ledger-facts div{font-size:14px;color:#334155;}
                #portalClientLedger .portal-ledger-facts dt{color:#64748b;font-size:12px;font-weight:600;}
                #portalClientLedger .portal-ledger-facts dd{margin:2px 0 0;}
                #portalClientLedger .portal-ledger-balance{border:1px solid #e2e8f0;border-radius:10px;background:#f8fafc;padding:14px 16px;text-align:right;}
                #portalClientLedger .portal-ledger-balance strong{display:block;font-size:26px;line-height:1.1;font-variant-numeric:tabular-nums;}
                #portalClientLedger .portal-ledger-balance span{display:block;margin-top:4px;color:#64748b;font-size:13px;}
                #portalClientLedger .portal-ledger-section{margin-top:16px;}
                #portalClientLedger .portal-ledger-section h3{margin:0 0 4px;font-size:16px;color:#2c3e50;}
                @media (max-width: 800px) {
                    #portalClientLedger .portal-ledger-layout{grid-template-columns:1fr;}
                    #portalClientLedger .portal-ledger-balance{text-align:left;}
                }
            </style>
            <div class="portal-ledger-card">
                <div class="portal-ledger-head">
                    <h2>Client Details</h2>
                    <div class="portal-ledger-actions">
                        <button type="button" id="portalStatementPdfBtn" class="portal-ledger-btn">Statement PDF</button>
                        <button type="button" id="portalLedgerExportBtn" class="portal-ledger-btn">Export CSV</button>
                    </div>
                </div>
                <div class="portal-ledger-layout">
                    <div>
                        <h3 class="portal-ledger-name">${escapeHtml(name)}</h3>
                        <dl class="portal-ledger-facts">
                            <div><dt>Status</dt><dd>${escapeHtml(status)}</dd></div>
                            <div><dt>Currency</dt><dd>${escapeHtml(currency)}</dd></div>
                            <div><dt>Credit limit</dt><dd>${escapeHtml(limitText)}</dd></div>
                            <div><dt>Available credit</dt><dd>${escapeHtml(availableText)}</dd></div>
                        </dl>
                        <div style="margin-top:14px;">
                            <button id="portalCreditLimitBtn" type="button" class="portal-ledger-btn portal-ledger-btn--quiet">$limitLabel</button>
                        </div>
                    </div>
                    <div class="portal-ledger-balance">
                        <strong class="$balanceClass">${escapeHtml(portalLedgerBalanceText(balance))}</strong>
                        <span>${escapeHtml(portalLedgerBalanceLabel(balance))}</span>
                    </div>
                </div>
                <div class="portal-ledger-section">
                    <h3>Ledger</h3>
                    $ledger
                </div>
            </div>
        </section>
    """.trimIndent()
}

private fun portalLedgerTable(events: List<dynamic>): String {
    val rows = events.joinToString("") { event ->
        val type = jsText(event.eventType)
        val desc = jsText(event.eventDescription).trim()
        val ref = jsText(event.billNumber).trim().ifEmpty { jsText(event.invoiceNumber).trim() }
        val balance = portalLedgerNumber(event.runningBalance) ?: 0.0
        val balanceClass = if (balance < 0) "portal-money portal-money--negative" else if (balance > 0) "portal-money portal-money--positive" else "portal-money"
        """
            <tr>
                <td><span class="portal-nowrap">${escapeHtml(jsText(event.eventDate))}</span></td>
                <td><span class="${portalLedgerTypeClass(type)}">${escapeHtml(portalLedgerTypeLabel(type))}</span></td>
                <td class="portal-ledger-text">${if (desc.isEmpty()) "—" else escapeHtml(desc)}</td>
                <td class="portal-ledger-text">${if (ref.isEmpty()) "—" else escapeHtml(ref)}</td>
                <td class="portal-money">${escapeHtml(portalLedgerAmount(event.transactionPrice))}</td>
                <td class="portal-money">${escapeHtml(portalLedgerAmount(event.paymentReceived))}</td>
                <td class="$balanceClass">${escapeHtml(portalLedgerBalanceText(balance))}</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table portal-table--ledger">
                <thead>
                    <tr>
                        ${portalLedgerSortHeader("eventDate", "Date", false)}
                        ${portalLedgerSortHeader("eventType", "Type", false)}
                        <th scope="col">Description</th>
                        <th scope="col">Reference</th>
                        ${portalLedgerSortHeader("transactionPrice", "Debit", true)}
                        ${portalLedgerSortHeader("paymentReceived", "Credit", true)}
                        <th scope="col" class="portal-money">Balance</th>
                    </tr>
                </thead>
                <tbody>$rows</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun portalLedgerSortHeader(field: String, label: String, alignRight: Boolean): String {
    val mark = if (portalLedgerSortField == field) if (portalLedgerSortOrder == "asc") "↑" else "↓" else "↕"
    val align = if (alignRight) "text-align:right;" else ""
    return """<th scope="col" style="$align"><button type="button" data-ledger-sort="$field" style="background:none;border:none;cursor:pointer;font:inherit;font-weight:700;padding:0;$align">${escapeHtml(label)} $mark</button></th>"""
}

private fun portalLedgerSorted(events: List<dynamic>): List<dynamic> {
    val asc = portalLedgerSortOrder == "asc"
    return events.sortedWith { left, right ->
        val compared = when (portalLedgerSortField) {
            "eventType" -> jsText(left.eventType).lowercase().compareTo(jsText(right.eventType).lowercase())
            "transactionPrice" -> (portalLedgerNumber(left.transactionPrice) ?: Double.NEGATIVE_INFINITY)
                .compareTo(portalLedgerNumber(right.transactionPrice) ?: Double.NEGATIVE_INFINITY)
            "paymentReceived" -> (portalLedgerNumber(left.paymentReceived) ?: Double.NEGATIVE_INFINITY)
                .compareTo(portalLedgerNumber(right.paymentReceived) ?: Double.NEGATIVE_INFINITY)
            else -> jsText(left.eventDate).compareTo(jsText(right.eventDate))
        }
        if (asc) compared else -compared
    }
}

private fun openPortalCreditLimit(generation: Int) {
    val account = portalLedger ?: return
    val name = escapeHtml(jsText(account.clientName).ifBlank { "—" })
    val balance = portalLedgerNumber(account.currentBalance) ?: 0.0
    val limit = portalLedgerNumber(account.creditLimit)
    val threshold = when {
        limit != null && portalLedgerNumber(account.alertThreshold) != null -> portalLedgerPlain(portalLedgerNumber(account.alertThreshold)!!)
        limit != null -> portalLedgerPlain(limit * 0.9)
        else -> ""
    }
    val title = if (limit != null) "Edit credit limit" else "Set credit limit"
    document.getElementById("portalCreditLimitModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "portalCreditLimitModal"
    modal.className = "client-modal"
    modal.innerHTML = """
        <div class="client-modal-content">
            <div class="client-modal-header">
                <h2>$title</h2>
                <button type="button" id="portalCreditLimitClose" class="client-modal-close">&times;</button>
            </div>
            <p class="client-ledger-hint" style="margin:0 0 12px;">Client: <strong>$name</strong></p>
            <form id="portalCreditLimitForm" class="client-form">
                <div class="client-form-row">
                    <div class="client-form-field">
                        <label class="client-form-label">Current balance</label>
                        <div class="client-form-readonly">${escapeHtml(portalLedgerBalanceText(balance))}</div>
                    </div>
                    <div class="client-form-field">
                        <label class="client-form-label">Available credit (now)</label>
                        <div id="portalCreditAvailable" class="client-form-readonly">${escapeHtml(if (limit == null) "— (set a limit)" else portalLedgerBalanceText(limit + balance))}</div>
                    </div>
                </div>
                <div class="client-form-row">
                    <div class="client-form-field">
                        <label for="portalCreditLimitInput" class="client-form-label">Credit limit (¥)</label>
                        <input type="number" id="portalCreditLimitInput" step="1" min="0" class="client-form-input" placeholder="e.g. 50000000" value="${escapeHtml(limit?.let { portalLedgerPlain(it) } ?: "")}">
                        <div class="client-form-hint">Leave blank to remove credit limit.</div>
                    </div>
                    <div class="client-form-field">
                        <label for="portalAlertThreshold" class="client-form-label">Alert threshold (90% of limit)</label>
                        <input type="text" id="portalAlertThreshold" readonly class="client-form-input client-form-readonly" tabindex="-1" value="${escapeHtml(threshold)}">
                        <div class="client-form-hint">Calculated automatically from credit limit.</div>
                    </div>
                </div>
                <div class="client-modal-actions">
                    <button type="button" id="portalCreditLimitCancel" class="client-btn client-btn-secondary">Cancel</button>
                    <button type="submit" class="client-btn client-btn-primary">Save credit limit</button>
                </div>
            </form>
        </div>
    """.trimIndent()
    document.body?.appendChild(modal)
    val close = { _: Event -> modal.remove() }
    document.getElementById("portalCreditLimitClose")?.addEventListener("click", close)
    document.getElementById("portalCreditLimitCancel")?.addEventListener("click", close)
    modal.addEventListener("click", { event: Event ->
        if (event.target == modal) modal.remove()
    })
    document.getElementById("portalCreditLimitInput")?.addEventListener("input", { _: Event ->
        val raw = (document.getElementById("portalCreditLimitInput") as? HTMLInputElement)?.value?.trim().orEmpty()
        val next = raw.toDoubleOrNull()
        document.getElementById("portalAlertThreshold")?.let { field ->
            (field as? HTMLInputElement)?.value = if (next != null && next >= 0.0) portalLedgerPlain(next * 0.9) else ""
        }
        document.getElementById("portalCreditAvailable")?.textContent = if (next != null && next >= 0.0) {
            portalLedgerBalanceText(next + balance)
        } else {
            "— (no limit set)"
        }
    })
    document.getElementById("portalCreditLimitForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        val raw = (document.getElementById("portalCreditLimitInput") as? HTMLInputElement)?.value?.trim().orEmpty()
        val payload = js("({})")
        if (raw.isEmpty()) {
            payload["creditLimit"] = null
        } else {
            val value = raw.toDoubleOrNull()
            if (value == null || value < 0.0) {
                showMessage("Credit limit must be a number ≥ 0.", "error")
                return@addEventListener
            }
            payload["creditLimit"] = value
        }
        val submit = document.querySelector("#portalCreditLimitForm button[type='submit']") as? org.w3c.dom.HTMLButtonElement
        submit?.disabled = true
        launchPortal {
            when (val result = ApiClient.request<dynamic>("portal/client/credit-limit", "PUT", payload)) {
                is ApiResult.Error -> showMessage(result.message, "error")
                is ApiResult.Success -> {
                    showMessage("Credit limit updated for ${jsText(account.clientName)}.", "success")
                    modal.remove()
                    if (generation == currentPortalGeneration()) {
                        portalLedger = result.data
                        drawPortalLedger(generation)
                    }
                }
            }
            submit?.disabled = false
        }
    })
}

private fun openPortalStatementPdf() {
    val account = portalLedger ?: return
    document.getElementById("portalStatementPdfModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "portalStatementPdfModal"
    modal.className = "client-modal"
    modal.innerHTML = """
        <div class="client-modal-content">
            <div class="client-modal-header">
                <h2>Export statement PDF</h2>
                <button type="button" id="portalStatementClose" class="client-modal-close">&times;</button>
            </div>
            <p class="client-ledger-hint">Leave dates empty to include all ledger entries.</p>
            <div class="client-form-row">
                <div class="client-form-field">
                    <label for="portalStmtStart" class="client-form-label">From (optional)</label>
                    <input type="date" id="portalStmtStart" class="client-form-input">
                </div>
                <div class="client-form-field">
                    <label for="portalStmtEnd" class="client-form-label">To (optional)</label>
                    <input type="date" id="portalStmtEnd" class="client-form-input">
                </div>
            </div>
            <div class="client-modal-actions">
                <button type="button" id="portalStatementCancel" class="client-btn client-btn-secondary">Cancel</button>
                <button type="button" id="portalStatementDownload" class="client-btn client-btn-primary">Download PDF</button>
            </div>
        </div>
    """.trimIndent()
    document.body?.appendChild(modal)
    val close = { _: Event -> modal.remove() }
    document.getElementById("portalStatementClose")?.addEventListener("click", close)
    document.getElementById("portalStatementCancel")?.addEventListener("click", close)
    modal.addEventListener("click", { event: Event ->
        if (event.target == modal) modal.remove()
    })
    document.getElementById("portalStatementDownload")?.addEventListener("click", { _: Event ->
        val start = (document.getElementById("portalStmtStart") as? HTMLInputElement)?.value?.trim().orEmpty()
        val end = (document.getElementById("portalStmtEnd") as? HTMLInputElement)?.value?.trim().orEmpty()
        val params = mutableListOf("ts=${js("Date.now()")}")
        if (start.isNotEmpty()) params.add("startDate=$start")
        if (end.isNotEmpty()) params.add("endDate=$end")
        downloadPortalFile(
            apiUrl("portal/client/ledger/statement-pdf?${params.joinToString("&")}"),
            buildPdfFilename("ClientStatement", jsText(account.clientName).ifBlank { "unknown" }),
            "Statement PDF downloaded.",
            pdf = true,
        )
        modal.remove()
    })
}

private fun exportPortalLedger() {
    downloadPortalFile(
        apiUrl("portal/client/ledger/export?ts=${js("Date.now()")}"),
        "client_transactions.csv",
        "Transactions exported successfully!",
        pdf = false,
    )
}

private fun downloadPortalFile(url: String, filename: String, success: String, pdf: Boolean) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    window.fetch(url, opts).then { response ->
        if (!response.ok) {
            response.text().then { errorText -> showMessage("Download failed: $errorText", "error") }
            return@then
        }
        val deliver: (dynamic) -> Unit = { blob ->
            val blobUrl = js("URL.createObjectURL(blob)") as String
            val link = document.createElement("a") as HTMLAnchorElement
            link.href = blobUrl
            link.download = filename
            link.style.display = "none"
            document.body?.appendChild(link)
            link.click()
            document.body?.removeChild(link)
            window.setTimeout({ js("URL.revokeObjectURL(blobUrl)") }, 1000)
            showMessage(success, "success")
        }
        if (pdf) {
            response.blob().then { blob -> deliver(blob) }
        } else {
            response.text().then { text ->
                deliver(js("new Blob([text], { type: 'text/csv;charset=utf-8;' })"))
            }
        }
    }.catch { error ->
        showMessage("Download failed: $error", "error")
    }
}

private fun eachPortalLedgerElement(selector: String, block: (HTMLElement) -> Unit) {
    val nodes = document.querySelectorAll(selector)
    val count = (nodes.length as Number).toInt()
    for (index in 0 until count) {
        val element = nodes.item(index) as? HTMLElement ?: continue
        block(element)
    }
}

private fun portalLedgerNumber(value: dynamic): Double? {
    if (value == null || value == js("undefined")) return null
    return when (value) {
        is Number -> value.toDouble()
        else -> value.toString().trim().toDoubleOrNull()
    }
}

private fun portalLedgerPlain(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun portalLedgerBalanceText(balance: Double): String = when {
    balance < 0 -> "−¥${kotlin.math.abs(balance).toLong()}"
    balance > 0 -> "+¥${balance.toLong()}"
    else -> "¥0"
}

private fun portalLedgerBalanceLabel(balance: Double): String = when {
    balance > 0 -> "Prepaid credit"
    balance < 0 -> "Amount owed"
    else -> "Settled"
}

private fun portalLedgerAmount(value: dynamic): String {
    val amount = portalLedgerNumber(value) ?: return ""
    if (amount == 0.0) return ""
    val sign = if (amount < 0) "−" else ""
    return "$sign¥${kotlin.math.abs(amount).toLong()}"
}

private fun portalLedgerTypeLabel(eventType: String): String = when (eventType.uppercase()) {
    "INVOICE_ISSUED" -> "Invoice"
    "INVOICE_REVERSAL" -> "Reversal"
    "PAYMENT_RECEIVED" -> "Payment"
    "ADJUSTMENT" -> "Adjustment"
    "OPENING_BALANCE" -> "Opening balance"
    "SHIPMENT" -> "Shipment"
    else -> "Other (legacy)"
}

private fun portalLedgerTypeClass(eventType: String): String = when (eventType.uppercase()) {
    "INVOICE_ISSUED" -> "ledger-type-badge ledger-type-invoice"
    "INVOICE_REVERSAL" -> "ledger-type-badge ledger-type-reversal"
    "PAYMENT_RECEIVED" -> "ledger-type-badge ledger-type-payment"
    "ADJUSTMENT" -> "ledger-type-badge ledger-type-adjustment"
    "OPENING_BALANCE" -> "ledger-type-badge ledger-type-opening"
    "SHIPMENT" -> "ledger-type-badge ledger-type-shipment"
    else -> "ledger-type-badge ledger-type-legacy"
}
