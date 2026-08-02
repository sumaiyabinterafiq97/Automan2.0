package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.models.ClientResponse
import com.automan.purchase.models.TransactionResponse
// Client Management Functions

/** Matches backend [AppConstants.CREDIT_LIMIT_NEAR_FRACTION] — warn at 90% of limit used. */
private const val CLIENT_CREDIT_ALERT_FRACTION = 0.9

private var clientListServerMode: Boolean = true
private var clientListPageZeroBased: Int = 0
private var clientListTotalPages: Int = 1
private var clientListTotalElements: Long = 0L
private var clientListItemsPerPage: Int = AppConstants.DEFAULT_ITEMS_PER_PAGE
private var clientListActiveSearchQ: String = ""
private var clientListSearchDebounceHandle: Int? = null
private var clientListCachedRows: Array<dynamic> = emptyArray()
private var clientModalEscapeHandler: ((Event) -> Unit)? = null
private var clientConfirmModalKeyHandler: ((Event) -> Unit)? = null

private fun clearClientModalEscape() {
    clientModalEscapeHandler?.let { document.removeEventListener("keydown", it) }
    clientModalEscapeHandler = null
}

/** Dialog a11y + Escape for `.client-modal` overlays. Call from open; clear on close. */
private fun wireClientModalA11y(modalId: String, titleId: String, close: () -> Unit, focusId: String? = null) {
    clearClientModalEscape()
    val modal = document.getElementById(modalId) as? HTMLElement ?: return
    val content = modal.querySelector(".client-modal-content") as? HTMLElement
    content?.setAttribute("role", "dialog")
    content?.setAttribute("aria-modal", "true")
    content?.setAttribute("aria-labelledby", titleId)
    (modal.querySelector(".client-modal-close") as? HTMLElement)?.setAttribute("aria-label", "Close")
    val escapeHandler: (Event) -> Unit = { event: Event ->
        if (event.asDynamic().key == "Escape") {
            event.preventDefault()
            close()
        }
    }
    clientModalEscapeHandler = escapeHandler
    document.addEventListener("keydown", escapeHandler)
    val focusEl = (
        focusId?.let { document.getElementById(it) }
            ?: content?.querySelector("button:not(.client-modal-close), input, select, textarea")
        ) as? HTMLElement
    window.setTimeout({ focusEl?.focus() }, 0)
}

/** In-app confirm for destructive ledger actions (replaces window.confirm). */
private fun showClientConfirmModal(
    title: String,
    message: String,
    confirmLabel: String = "Delete",
    onConfirm: () -> Unit,
) {
    document.getElementById("clientConfirmModal")?.remove()
    clientConfirmModalKeyHandler?.let { document.removeEventListener("keydown", it) }
    clientConfirmModalKeyHandler = null
    val returnFocus = document.activeElement as? HTMLElement
    val safeTitle = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val safeMessage = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val safeConfirm = confirmLabel.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    val overlay = document.createElement("div") as HTMLElement
    overlay.id = "clientConfirmModal"
    overlay.style.cssText =
        "position:fixed;inset:0;z-index:10020;display:flex;align-items:center;justify-content:center;" +
            "background:rgba(15,23,42,0.45);padding:16px;box-sizing:border-box;"
    overlay.innerHTML = """
        <div role="dialog" aria-modal="true" aria-labelledby="clientConfirmTitle"
             style="background:#fff;border-radius:12px;box-shadow:0 20px 50px rgba(15,23,42,0.28);
             max-width:440px;width:100%;padding:22px 24px;box-sizing:border-box;">
            <h3 id="clientConfirmTitle" style="margin:0 0 12px;font-size:18px;font-weight:700;color:#0f172a;">$safeTitle</h3>
            <div style="font-size:14px;line-height:1.55;color:#334155;margin-bottom:20px;">$safeMessage</div>
            <div style="display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;">
                <button type="button" id="clientConfirmCancel"
                    style="padding:9px 16px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;cursor:pointer;min-height:40px;font-size:14px;color:#374151;">Cancel</button>
                <button type="button" id="clientConfirmOk"
                    style="padding:9px 16px;border:none;border-radius:8px;background:#b91c1c;color:#fff;cursor:pointer;font-weight:700;min-height:40px;font-size:14px;">$safeConfirm</button>
            </div>
        </div>
    """.trimIndent()

    fun closeModal() {
        clientConfirmModalKeyHandler?.let { document.removeEventListener("keydown", it) }
        clientConfirmModalKeyHandler = null
        overlay.remove()
        returnFocus?.focus()
    }

    document.body?.appendChild(overlay)
    document.getElementById("clientConfirmCancel")?.addEventListener("click", { _: Event -> closeModal() })
    document.getElementById("clientConfirmOk")?.addEventListener("click", { _: Event ->
        closeModal()
        onConfirm()
    })
    overlay.addEventListener("click", { ev: Event ->
        if (ev.target === overlay) closeModal()
    })
    val escapeHandler: (Event) -> Unit = { event: Event ->
        if (event.asDynamic().key == "Escape") {
            event.preventDefault()
            closeModal()
        }
    }
    clientConfirmModalKeyHandler = escapeHandler
    document.addEventListener("keydown", escapeHandler)
    (document.getElementById("clientConfirmCancel") as? HTMLElement)?.focus()
}

private fun clientStatusBlock(message: String, kind: String = "muted"): String {
    val cls = when (kind) {
        "error" -> "client-status-block client-status-block--error"
        else -> "client-status-block"
    }
    return """<div class="$cls" role="status">${escapeHtml(message)}</div>"""
}

private fun alertThresholdFromCreditLimit(creditLimit: Double): Double =
    creditLimit * CLIENT_CREDIT_ALERT_FRACTION

private fun formatCreditLimitNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun syncAlertThresholdFromCreditLimit(creditLimitInputId: String, alertThresholdInputId: String) {
    val limitInput = document.getElementById(creditLimitInputId) as? HTMLInputElement ?: return
    val thresholdInput = document.getElementById(alertThresholdInputId) as? HTMLInputElement ?: return
    val limitRaw = limitInput.value.trim()
    if (limitRaw.isEmpty()) {
        thresholdInput.value = ""
        return
    }
    val limit = limitRaw.toDoubleOrNull() ?: return
    if (limit < 0.0) return
    thresholdInput.value = formatCreditLimitNumber(alertThresholdFromCreditLimit(limit))
}

/** Option A: positive balance = prepaid credit; negative = amount owed. */
private fun formatClientBalanceAmount(balance: Double): String = when {
    balance < 0 -> "−¥${kotlin.math.abs(balance).toLong()}"
    balance > 0 -> "+¥${balance.toLong()}"
    else -> "¥0"
}

private fun clientBalanceColor(balance: Double): String =
    if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"

private fun clientBalanceStatusLabel(balance: Double): String = when {
    balance > 0 -> "Prepaid credit"
    balance < 0 -> "Amount owed"
    else -> "Settled"
}

private fun formatAvailableCredit(balance: Double, creditLimit: Double?): String? {
    if (creditLimit == null) return null
    val available = creditLimit + balance
    return formatClientBalanceAmount(available)
}

private fun eventTypeBadgeLabel(eventType: String?): String = when (eventType?.uppercase()) {
    "INVOICE_ISSUED" -> "Invoice"
    "INVOICE_REVERSAL" -> "Reversal"
    "PAYMENT_RECEIVED" -> "Payment"
    "ADJUSTMENT" -> "Adjustment"
    "OPENING_BALANCE" -> "Opening balance"
    "SHIPMENT" -> "Shipment"
    else -> "Other (legacy)"
}

private fun eventTypeBadgeClass(eventType: String?): String = when (eventType?.uppercase()) {
    "INVOICE_ISSUED" -> "ledger-type-badge ledger-type-invoice"
    "INVOICE_REVERSAL" -> "ledger-type-badge ledger-type-reversal"
    "PAYMENT_RECEIVED" -> "ledger-type-badge ledger-type-payment"
    "ADJUSTMENT" -> "ledger-type-badge ledger-type-adjustment"
    "OPENING_BALANCE" -> "ledger-type-badge ledger-type-opening"
    "SHIPMENT" -> "ledger-type-badge ledger-type-shipment"
    else -> "ledger-type-badge ledger-type-legacy"
}

private fun isManualEditableLedgerType(eventType: String?): Boolean = when (eventType?.uppercase()) {
    "PAYMENT_RECEIVED", "ADJUSTMENT", "OPENING_BALANCE" -> true
    else -> false
}

private fun ledgerSignedAmount(event: dynamic): Double? {
    val payment = (event.paymentReceived as? Number)?.toDouble()
    val debit = (event.transactionPrice as? Number)?.toDouble()
    return when {
        payment != null && payment > 0.0 -> payment
        debit != null && debit > 0.0 -> -debit
        else -> null
    }
}

private fun fmtLedgerAmount(value: dynamic?): String {
    if (value == null) return ""
    val v = (value as Number).toDouble()
    if (v == 0.0) return ""
    val sign = if (v < 0) "−" else ""
    return "$sign¥${kotlin.math.abs(v).toLong()}"
}

fun showClientDetailsPage(clientId: Long) {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="clientDetailsPage" class="client-details-container">
            <div class="client-details-card">
                <div class="client-details-header client-page-toolbar">
                    <h2 class="client-page-title">Client Details</h2>
                    <div class="client-details-actions client-toolbar-actions">
                        <button type="button" id="clientStatementPdfBtn" class="client-btn client-btn-info">Statement PDF</button>
                        <button type="button" id="exportClientTxBtn" class="client-btn client-btn-info">Export CSV</button>
                        <button type="button" id="backToClientsBtn" class="client-btn client-btn-secondary">Back to Client Transactions</button>
                    </div>
                </div>
                <div id="clientDetailsContent"></div>
                <div id="clientEventsTable" class="client-transactions-section">
                    ${clientStatusBlock("Loading transactions…")}
                </div>
            </div>
        </div>
    """
    document.getElementById("backToClientsBtn")?.addEventListener("click", { _: Event ->
        navigateToApp("/master/client-transactions")
    })
    document.getElementById("clientStatementPdfBtn")?.addEventListener("click", { _: Event ->
        openStatementPdfModal(clientId)
    })
    document.getElementById("exportClientTxBtn")?.addEventListener("click", { _: Event ->
        exportClientTransactions(clientId)
    })
    // Load data
    loadClientDetails(clientId)
    loadClientEvents(clientId)
}

fun showClientAccountsPage() {
    // Stay on current hash; this function renders list view
    val content = document.getElementById("content")!!
    // Check if we're on master/clients route to use appropriate title
    val isMasterList = routeStartsWith("/master/client-transactions")
    val pageTitle = if (isMasterList) "Client Transactions" else "Client Accounts Management"
    content.innerHTML = """
        <div id="clientTransactionsPage" class="client-page-container">
            <div class="client-page-card">
                <div class="client-page-header client-page-toolbar">
                    <h2 class="client-page-title">$pageTitle</h2>
                    <div class="client-action-buttons client-toolbar-actions">
                        <button type="button" id="addClientBtn" class="client-btn client-btn-primary">Add New Client</button>
                        <button type="button" id="clientAlertsBtn" class="client-btn client-btn-warning">View Alerts</button>
                        <button type="button" id="exportClientsBtn" class="client-btn client-btn-info">Export clients CSV</button>
                    </div>
                </div>
                
                <!-- Client Alerts Section -->
                <div id="clientAlertsSection" class="client-alerts-section">
                    <h3>Client Alerts</h3>
                    <div id="clientAlertsTable">
                        ${clientStatusBlock("Loading client alerts…")}
                    </div>
                </div>
                
                <!-- Client List Section -->
                <div class="client-list-section">
                    <h3>Client List</h3>
                    <div class="client-search-container">
                        <label for="clientSearchInput" class="visually-hidden">Search clients</label>
                        <input id="clientSearchInput" type="text" role="searchbox" autocomplete="off" inputmode="search"
                               placeholder="Search clients…" class="client-search-input" aria-label="Search clients">
                    </div>
                    <div id="clientListTable" class="client-list-container">
                        ${clientStatusBlock("Loading clients…")}
                    </div>
                </div>
            </div>
        </div>
    """
    
    // Add event listeners
    document.getElementById("addClientBtn")?.addEventListener("click", { _: Event ->
        showAddClientForm()
    })
    
    document.getElementById("clientAlertsBtn")?.addEventListener("click", { _: Event ->
        toggleClientAlerts()
    })
    
    document.getElementById("exportClientsBtn")?.addEventListener("click", { _: Event ->
        exportClientsData()
    })
    
    
    
    document.getElementById("clientSearchInput")?.addEventListener("input", { _: Event ->
        scheduleClientListSearchDebounced()
    })
    
    // Load clients
    clientListPageZeroBased = 0
    clientListActiveSearchQ = ""
    loadClients()

    if (!isEditor()) {
        document.getElementById("addClientBtn")?.asDynamic()?.style?.display = "none"
    }
}

private fun scheduleClientListSearchDebounced() {
    val prev = clientListSearchDebounceHandle
    if (prev != null) window.clearTimeout(prev)
    clientListSearchDebounceHandle = window.setTimeout({
        clientListSearchDebounceHandle = null
        clientListPageZeroBased = 0
        loadClients(0)
    }, 420)
}

fun loadClients(page0: Int = clientListPageZeroBased) {
    val host = document.getElementById("clientListTable")
    host?.innerHTML = clientStatusBlock("Loading clients…")
    val q = (document.getElementById("clientSearchInput") as? HTMLInputElement)?.value?.trim() ?: ""
    clientListActiveSearchQ = q
    clientListPageZeroBased = page0.coerceAtLeast(0)
    val size = clientListItemsPerPage.coerceAtLeast(1)
    val url = if (q.isNotEmpty()) {
        val encQ = js("encodeURIComponent")(q).unsafeCast<String>()
        apiUrl("clients/page-search?q=$encQ&page=$clientListPageZeroBased&size=$size")
    } else {
        apiUrl("clients/page?page=$clientListPageZeroBased&size=$size")
    }
    window.fetch(url)
        .then { response ->
            if (response.ok) {
                response.json().then { body ->
                    val err = js("body.error")?.toString()?.trim()
                    if (!err.isNullOrEmpty()) {
                        host?.innerHTML = clientStatusBlock(err, "error")
                        return@then
                    }
                    clientListServerMode = true
                    val totalEl = js("body.totalElements")
                    clientListTotalElements = when (totalEl) {
                        is Number -> totalEl.toLong()
                        else -> totalEl?.toString()?.toLongOrNull() ?: 0L
                    }
                    val tp = js("body.totalPages")
                    clientListTotalPages = kotlin.math.max(
                        1,
                        when (tp) {
                            is Number -> tp.toInt()
                            else -> tp?.toString()?.toIntOrNull() ?: 1
                        },
                    )
                    val num = js("body.page")
                    clientListPageZeroBased = when (num) {
                        is Number -> num.toInt()
                        else -> num?.toString()?.toIntOrNull() ?: 0
                    }
                    val sz = js("body.size")
                    clientListItemsPerPage = when (sz) {
                        is Number -> sz.toInt()
                        else -> sz?.toString()?.toIntOrNull() ?: AppConstants.DEFAULT_ITEMS_PER_PAGE
                    }
                    clientListCachedRows = try {
                        js("Array.from((body && body.content) ? body.content : [])").unsafeCast<Array<dynamic>>()
                    } catch (_: dynamic) {
                        emptyArray()
                    }
                    displayClients(clientListCachedRows)
                }
            } else {
                host?.innerHTML = clientStatusBlock("Failed to load clients", "error")
            }
        }
        .catch { error ->
            host?.innerHTML = clientStatusBlock("Error loading clients: $error", "error")
        }
}

fun displayClients(clients: dynamic) {
    val clientListTable = document.getElementById("clientListTable")
    if (clientListTable == null) return
    
    if (js("Array.isArray(clients)") as Boolean && (clients as Array<dynamic>).isEmpty()) {
        clientListTable.innerHTML = clientStatusBlock("No clients found")
        return
    }
    
    val clientsArray = clients as Array<dynamic>
    val showCreditLimitActions = isEditor()
    val clientsHtml = clientsArray.map { client ->
        val clientId = js("client.id")?.toString()?.toLongOrNull()
        val balance = (client.currentBalance as Number).toDouble()
        val balanceColor = clientBalanceColor(balance)
        val balanceText = formatClientBalanceAmount(balance)
        val statusLabel = clientBalanceStatusLabel(balance)
        
        // Credit limit alert color logic (Green / Orange / Red)
        val creditLimit = (client.creditLimit as Number?)?.toDouble()
        val alertThreshold = (client.alertThreshold as Number?)?.toDouble()
        val usedPct: Double? = if (creditLimit != null && creditLimit > 0.0) {
            (kotlin.math.abs(balance) / creditLimit) * 100.0
        } else null
        val (alertIcon, alertStyle, overLimitBadge) = when {
            usedPct == null -> Triple("", "", "")
            usedPct >= 100.0 -> Triple(
                """<span class="client-alert-icon" role="img" aria-label="Over credit limit">!</span>""",
                "border-left: 4px solid #e74c3c;",
                """<div class="client-limit-badge client-limit-over">Over limit (${usedPct.toInt()}%)</div>""",
            )
            usedPct >= 90.0 -> Triple(
                """<span class="client-alert-icon client-alert-icon--warn" role="img" aria-label="Near credit limit">!</span>""",
                "border-left: 4px solid #ffc107;",
                "",
            )
            else -> Triple("", "", "")
        }
        val limitSummary = when {
            creditLimit != null && creditLimit > 0.0 -> {
                val avail = formatAvailableCredit(balance, creditLimit) ?: "—"
                """<div class="client-limit-summary">Limit ¥${creditLimit.toLong()} · Available $avail</div>"""
            }
            else -> ""
        }
        val creditLimitBtn = if (showCreditLimitActions && clientId != null) {
            val btnLabel = if (creditLimit != null) "Edit limit" else "Set limit"
            """<button type="button" class="client-btn client-btn-secondary client-credit-limit-btn"
                data-client-id="$clientId"
                data-client-name="${(client.clientName as? String)?.replace("\"", "&quot;") ?: ""}"
                data-balance="$balance"
                data-credit-limit="${creditLimit ?: ""}"
                data-alert-threshold="${alertThreshold ?: ""}"
                title="Set or change credit limit">$btnLabel</button>"""
        } else {
            ""
        }
        
        """
        <div class="client-item" style="$alertStyle">
            <div class="client-item-content">
                <div class="client-info client-info-flex" onclick="window.navigateToApp('/client/${client.id}')">
                    <div class="client-info-row">
                        <div>
                            <div class="client-name">$alertIcon ${client.clientName}</div>
                            <div class="client-number">#${client.clientNumber}</div>
                            $overLimitBadge
                            $limitSummary
                        </div>
                        <div style="text-align: right;">
                            <div class="client-balance" style="color: $balanceColor;">$balanceText</div>
                            <div class="client-balance-sub">${statusLabel}</div>
                            <div class="client-status">${client.status}</div>
                        </div>
                    </div>
                </div>
                $creditLimitBtn
            </div>
        </div>
        """
    }.joinToString("")

    val pagerHtml = if (clientListServerMode) {
        val totalPages = kotlin.math.max(1, clientListTotalPages)
        val currentPage = clientListPageZeroBased + 1
        if (totalPages > 1 || clientListTotalElements > clientListItemsPerPage) {
            val prevDisabled = currentPage <= 1
            val nextDisabled = currentPage >= totalPages
            """
            <div id="clientListPager" class="client-list-pager">
                <div class="client-list-pager-meta">Page $currentPage of $totalPages · $clientListTotalElements client(s)</div>
                <div class="client-list-pager-btns">
                    <button type="button" id="clientListPrevPage" class="client-list-pager-btn${if (prevDisabled) " is-disabled" else ""}" ${if (prevDisabled) "disabled" else ""}>Previous</button>
                    <span class="client-list-pager-page">Page $currentPage of $totalPages</span>
                    <button type="button" id="clientListNextPage" class="client-list-pager-btn${if (nextDisabled) " is-disabled" else ""}" ${if (nextDisabled) "disabled" else ""}>Next</button>
                </div>
            </div>
            """
        } else {
            ""
        }
    } else {
        ""
    }
    
    clientListTable.innerHTML = clientsHtml + pagerHtml
    wireClientListCreditLimitButtons()
    document.getElementById("clientListPrevPage")?.addEventListener("click", { _: Event ->
        if (clientListPageZeroBased > 0) loadClients(clientListPageZeroBased - 1)
    })
    document.getElementById("clientListNextPage")?.addEventListener("click", { _: Event ->
        if (clientListPageZeroBased + 1 < clientListTotalPages) {
            loadClients(clientListPageZeroBased + 1)
        }
    })
}

fun selectClient(clientId: Long) {
    navigateToApp("/client/$clientId")
}

fun loadClientDetails(clientId: Long) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    val ts = js("Date.now()")
    window.fetch(apiUrl("clients/$clientId?ts=$ts"), opts)
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    displayClientDetails(clientId, client)
                }
            } else {
                document.getElementById("clientDetailsContent")?.innerHTML =
                    clientStatusBlock("Failed to load client details", "error")
            }
        }
        .catch { error ->
            document.getElementById("clientDetailsContent")?.innerHTML =
                clientStatusBlock("Error loading client details: $error", "error")
        }
}

fun displayClientDetails(clientId: Long, raw: dynamic) {
    val clientDetailsContent = document.getElementById("clientDetailsContent")
    if (clientDetailsContent == null) return

    val clientName = raw.clientName as? String ?: "—"
    val clientNumber = raw.clientNumber as? String ?: "—"
    val status = raw.status as? String ?: "N/A"
    val currency = raw.currency as? String ?: "JPY"
    val creditLimit = (raw.creditLimit as? Number)?.toDouble()
    val alertThreshold = (raw.alertThreshold as? Number)?.toDouble()
    val balance = (raw.currentBalance as? Number)?.toDouble() ?: 0.0
    val balanceColor = clientBalanceColor(balance)
    val balanceText = formatClientBalanceAmount(balance)
    val statusLabel = clientBalanceStatusLabel(balance)
    val creditLimitText = creditLimit?.let { "¥${it.toLong()}" } ?: "—"
    val availableCreditText = formatAvailableCredit(balance, creditLimit) ?: "—"
    val creditLimitBtnLabel = if (creditLimit != null) "Edit credit limit" else "Set credit limit"
    val addTxBtn = if (isEditor()) {
        """<button id="addClientTransactionBtn" type="button" class="client-btn client-btn-success">Add Transaction</button>"""
    } else {
        ""
    }
    val creditLimitBtn = if (isEditor()) {
        """<button id="editCreditLimitBtn" type="button" class="client-btn client-btn-secondary">$creditLimitBtnLabel</button>"""
    } else {
        ""
    }
    val editClientBtn = if (isEditor()) {
        """<button id="editClientProfileBtn" type="button" class="client-btn client-btn-secondary">Edit client</button>"""
    } else {
        ""
    }
    
    clientDetailsContent.innerHTML = """
        <div class="client-info-card">
            <h4 class="client-info-name">${clientName}</h4>
            <div class="client-info-grid">
                <div class="client-info-item"><strong>Client #:</strong> ${clientNumber}</div>
                <div class="client-info-item"><strong>Status:</strong> ${status}</div>
                <div class="client-info-item"><strong>Currency:</strong> ${currency}</div>
                <div class="client-info-item"><strong>Credit limit:</strong> $creditLimitText</div>
                <div class="client-info-item"><strong>Available credit:</strong> $availableCreditText</div>
            </div>
            <div class="client-balance-card">
                <div id="currentBalanceValue" class="client-balance-amount" style="color: $balanceColor;">$balanceText</div>
                <div class="client-balance-label">$statusLabel</div>
            </div>
            <div class="client-action-buttons">
                $addTxBtn
                $editClientBtn
                $creditLimitBtn
            </div>
        </div>
    """

    document.getElementById("addClientTransactionBtn")?.addEventListener("click", { _: Event ->
        addClientTransaction(clientId)
    })
    document.getElementById("editCreditLimitBtn")?.addEventListener("click", { _: Event ->
        openCreditLimitModal(
            clientId = clientId,
            clientName = clientName,
            currentBalance = balance,
            creditLimit = creditLimit,
            alertThreshold = alertThreshold,
        )
    })
    document.getElementById("editClientProfileBtn")?.addEventListener("click", { _: Event ->
        openEditClientModal(
            clientId = clientId,
            clientNumber = clientNumber,
            clientName = clientName,
            currency = currency,
            status = status,
        )
    })
}

fun loadClientEvents(clientId: Long) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    val ts = js("Date.now()")
    window.fetch(apiUrl("events/client/$clientId?ts=$ts"), opts)
        .then { response ->
            if (response.ok) {
                response.json().then { events ->
                    displayClientEvents(clientId, events)
                }
            } else {
                document.getElementById("clientEventsTable")?.innerHTML =
                    clientStatusBlock("Failed to load transactions", "error")
            }
        }
        .catch { error ->
            document.getElementById("clientEventsTable")?.innerHTML =
                clientStatusBlock("Error loading transactions: $error", "error")
        }
}

fun displayClientEvents(clientId: Long, events: dynamic) {
    val clientEventsTable = document.getElementById("clientEventsTable")
    if (clientEventsTable == null) return
    
    if (js("Array.isArray(events)") as Boolean && (events as Array<dynamic>).isEmpty()) {
        clientEventsTable.innerHTML = """
            <h3>Ledger</h3>
            ${clientStatusBlock("No ledger entries yet. Invoices post automatically when confirmed in Invoice.")}
        """
        return
    }
    
    val eventsArray = events as Array<dynamic>
    val showActions = isEditor()
    val actionsHeader = if (showActions) """<th scope="col">Actions</th>""" else ""

    val rowsHtml = eventsArray.map { event ->
        val eventType = event.eventType?.toString()
        val typeLabel = eventTypeBadgeLabel(eventType)
        val typeClass = eventTypeBadgeClass(eventType)
        val desc = event.eventDescription?.toString()?.trim().orEmpty()
        val tPrice = event.transactionPrice
        val payment = event.paymentReceived
        val balance = (event.runningBalance as Number).toDouble()
        val balanceColor = clientBalanceColor(balance)
        val balanceText = formatClientBalanceAmount(balance)
        val ref = event.billNumber?.toString()?.trim().orEmpty()
            .ifEmpty { event.invoiceNumber?.toString()?.trim().orEmpty() }
        val eventId = event.id?.toString()?.trim().orEmpty()
        val actionsCell = if (!showActions) {
            ""
        } else if (isManualEditableLedgerType(eventType) && eventId.isNotEmpty()) {
            """<td class="ledger-actions-cell">
                <button type="button" class="client-btn client-btn-secondary ledger-edit-btn"
                    data-event-id="$eventId" data-client-id="$clientId">Edit</button>
                <button type="button" class="client-btn client-btn-secondary ledger-delete-btn"
                    data-event-id="$eventId" data-client-id="$clientId">Delete</button>
            </td>"""
        } else {
            """<td class="ledger-actions-cell">—</td>"""
        }
        
        """
        <tr>
            <td>${event.eventDate}</td>
            <td><span class="$typeClass">$typeLabel</span></td>
            <td>${if (desc.isEmpty()) "—" else desc}</td>
            <td>${if (ref.isEmpty()) "—" else ref}</td>
            <td style="text-align: right;">${fmtLedgerAmount(tPrice)}</td>
            <td style="text-align: right;">${fmtLedgerAmount(payment)}</td>
            <td style="text-align: right; color: $balanceColor; font-weight: bold;">$balanceText</td>
            $actionsCell
        </tr>
        """
    }.joinToString("")
    
    val cardsHtml = eventsArray.map { event ->
        val eventType = event.eventType?.toString()
        val typeLabel = eventTypeBadgeLabel(eventType)
        val typeClass = eventTypeBadgeClass(eventType)
        val desc = event.eventDescription?.toString()?.trim().orEmpty()
        val tPrice = event.transactionPrice
        val payment = event.paymentReceived
        val balance = (event.runningBalance as Number).toDouble()
        val balanceColor = clientBalanceColor(balance)
        val balanceText = formatClientBalanceAmount(balance)
        val ref = event.billNumber?.toString()?.trim().orEmpty()
            .ifEmpty { event.invoiceNumber?.toString()?.trim().orEmpty() }
        
        """
        <div class="transaction-card">
            <div class="transaction-card-header">
                <span class="transaction-date">${event.eventDate}</span>
                <span class="$typeClass">$typeLabel</span>
            </div>
            <div class="transaction-card-body">
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Description</span>
                    <span class="transaction-card-value">${if (desc.isEmpty()) "—" else desc}</span>
                </div>
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Reference</span>
                    <span class="transaction-card-value">${if (ref.isEmpty()) "—" else ref}</span>
                </div>
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Debit</span>
                    <span class="transaction-card-value">${fmtLedgerAmount(tPrice)}</span>
                </div>
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Credit</span>
                    <span class="transaction-card-value">${fmtLedgerAmount(payment)}</span>
                </div>
                <div class="transaction-balance" style="color: $balanceColor;">Balance: $balanceText</div>
            </div>
        </div>
        """
    }.joinToString("")
    
    clientEventsTable.innerHTML = """
        <h3>Ledger</h3>
        <p class="client-ledger-hint">Invoices post here automatically. Add payments and adjustments manually.</p>
        
        <!-- Table for Tablet/Desktop -->
        <div class="transactions-table-container">
            <table class="transactions-table">
                <thead>
                    <tr>
                        <th scope="col">Date</th>
                        <th scope="col">Type</th>
                        <th scope="col">Description</th>
                        <th scope="col">Reference</th>
                        <th scope="col" style="text-align: right;">Debit</th>
                        <th scope="col" style="text-align: right;">Credit</th>
                        <th scope="col" style="text-align: right;">Balance</th>
                        $actionsHeader
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
        </div>
        
        <!-- Cards for Mobile -->
        <div class="transactions-cards-container">
            $cardsHtml
        </div>
    """
    wireLedgerActionButtons(clientId, eventsArray)
}

private fun wireLedgerActionButtons(clientId: Long, events: Array<dynamic>) {
    if (!isEditor()) return
    val eventsById = events.associateBy { it.id?.toString()?.trim().orEmpty() }

    val editButtons = document.querySelectorAll(".ledger-edit-btn")
    for (i in 0 until editButtons.length) {
        val btn = editButtons.item(i) as? HTMLElement ?: continue
        btn.addEventListener("click", { _: Event ->
            val eventId = btn.getAttribute("data-event-id")?.trim().orEmpty()
            val event = eventsById[eventId] ?: return@addEventListener
            openEditLedgerModal(clientId, event)
        })
    }

    val deleteButtons = document.querySelectorAll(".ledger-delete-btn")
    for (i in 0 until deleteButtons.length) {
        val btn = deleteButtons.item(i) as? HTMLElement ?: continue
        btn.addEventListener("click", { _: Event ->
            val eventId = btn.getAttribute("data-event-id")?.toLongOrNull() ?: return@addEventListener
            deleteLedgerEntry(clientId, eventId)
        })
    }
}

// Note: Additional client management functions (showAddClientForm, handleAddClientSubmit, etc.) 
// will be added in subsequent updates to keep this file manageable.
// Add New Client Modal Implementation

fun showAddClientForm() {
    val modalHTML = """
        <div id="addClientModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2 id="addClientModalTitle">Add New Client</h2>
                    <button type="button" id="closeAddClientModalBtn" class="client-modal-close">&times;</button>
                </div>
                <form id="addClientForm" class="client-form">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="clientNumber" class="client-form-label">Client Number *</label>
                            <input type="text" id="clientNumber" name="clientNumber" required 
                                   class="client-form-input" placeholder="e.g., 128">
                        </div>
                        <div class="client-form-field">
                            <label for="clientName" class="client-form-label">Client Name *</label>
                            <select id="clientName" name="clientName" class="client-form-select" required>
                                <option value="">Select Client Name</option>
                            </select>
                        </div>
                    </div>
                    
                    <div class="client-form-row-3">
                        <div class="client-form-field">
                            <label for="currentBalance" class="client-form-label">Initial Balance</label>
                            <input type="number" id="currentBalance" name="currentBalance" step="0.01" value="0.00"
                                   class="client-form-input" placeholder="0.00">
                        </div>
                        <div class="client-form-field">
                            <label for="creditLimit" class="client-form-label">Credit Limit</label>
                            <input type="number" id="creditLimit" name="creditLimit" step="0.01"
                                   class="client-form-input" placeholder="e.g., 50000000">
                        </div>
                        <div class="client-form-field">
                            <label for="alertThreshold" class="client-form-label">Alert threshold (90% of limit)</label>
                            <input type="text" id="alertThreshold" name="alertThreshold" readonly
                                   class="client-form-input client-form-readonly" tabindex="-1"
                                   placeholder="Filled when credit limit is entered">
                        </div>
                    </div>
                    
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="currency" class="client-form-label">Currency</label>
                            <select id="currency" name="currency" class="client-form-select">
                                <option value="JPY" selected>JPY (Japanese Yen)</option>
                                <option value="USD">USD (US Dollar)</option>
                                <option value="EUR">EUR (Euro)</option>
                            </select>
                        </div>
                        <div class="client-form-field">
                            <label for="status" class="client-form-label">Status</label>
                            <select id="status" name="status" class="client-form-select">
                                <option value="ACTIVE" selected>Active</option>
                                <option value="SUSPENDED">Suspended</option>
                                <option value="CLOSED">Closed</option>
                            </select>
                        </div>
                    </div>
                    
                    <div class="client-modal-actions">
                        <button type="button" id="cancelAddClientBtn" class="client-btn client-btn-secondary">
                            Cancel
                        </button>
                        <button type="submit" class="client-btn client-btn-primary">
                            Add Client
                        </button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Remove existing modal if any
    document.getElementById("addClientModal")?.remove()
    
    // Add modal to body
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Add form submission handler
    document.getElementById("addClientForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        handleAddClientSubmit()
    })
    
    // Add close button (X) handler
    document.getElementById("closeAddClientModalBtn")?.addEventListener("click", { _: Event ->
        closeAddClientModal()
    })
    
    // Add cancel button handler
    document.getElementById("cancelAddClientBtn")?.addEventListener("click", { _: Event ->
        closeAddClientModal()
    })

    populateClientNameOptions("clientName", null)
    document.getElementById("creditLimit")?.addEventListener("input", { _: Event ->
        syncAlertThresholdFromCreditLimit("creditLimit", "alertThreshold")
    })
    document.getElementById("addClientModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "addClientModal") closeAddClientModal()
    })
    wireClientModalA11y("addClientModal", "addClientModalTitle", ::closeAddClientModal, "clientNumber")
}

fun handleAddClientSubmit() {
    val form = document.getElementById("addClientForm") as? HTMLFormElement
    if (form == null) return
    
    val clientData = js("{}")
    clientData["clientNumber"] = (document.getElementById("clientNumber") as? HTMLInputElement)?.value ?: ""
    clientData["clientName"] = (document.getElementById("clientName") as? HTMLSelectElement)?.value?.trim().orEmpty()
    clientData["currentBalance"] = (document.getElementById("currentBalance") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: 0.0
    val creditLimit = (document.getElementById("creditLimit") as? HTMLInputElement)?.value?.toDoubleOrNull()
    clientData["creditLimit"] = creditLimit
    clientData["alertThreshold"] = creditLimit?.let { alertThresholdFromCreditLimit(it) }
    clientData["currency"] = (document.getElementById("currency") as? HTMLSelectElement)?.value ?: "JPY"
    clientData["status"] = (document.getElementById("status") as? HTMLSelectElement)?.value ?: "ACTIVE"
    
    // Validate required fields
    if (clientData["clientNumber"] == "" || clientData["clientName"] == "") {
        showMessage("Please fill in all required fields", "error")
        return
    }
    
    // Show loading state
    val submitBtn = document.querySelector("#addClientForm button[type='submit']") as? HTMLButtonElement
    submitBtn?.let {
        it.disabled = true
        it.textContent = "Adding..."
    }
            
            val requestOptions = js("{}")
            requestOptions["method"] = "POST"
            requestOptions["headers"] = js("{\"Content-Type\": \"application/json\"}")
            requestOptions["body"] = JSON.stringify(clientData)
            
            window.fetch(apiUrl("clients"), requestOptions)
                .then { response ->
                    if (response.ok) {
                        showMessage("Client added successfully!", "success")
                        closeAddClientModal()
                        showClientAccountsPage()
                    } else {
                        response.text().then { errorText ->
                            showMessage("Failed to add client: $errorText", "error")
                        }
                    }
                }
                .catch { error ->
                    showMessage("Error adding client: $error", "error")
                }
                .finally {
            submitBtn?.let {
                it.disabled = false
                it.textContent = "Add Client"
            }
        }
}

@JsName("closeAddClientModalFromClientManagement")
fun closeAddClientModal() {
    clearClientModalEscape()
    document.getElementById("addClientModal")?.remove()
}

private fun wireClientListCreditLimitButtons() {
    if (!isEditor()) return
    val buttons = document.querySelectorAll(".client-credit-limit-btn")
    for (i in 0 until buttons.length) {
        val btn = buttons.item(i) as? HTMLElement ?: continue
        btn.addEventListener("click", { event: Event ->
            event.stopPropagation()
            event.preventDefault()
            val id = btn.getAttribute("data-client-id")?.toLongOrNull() ?: return@addEventListener
            val name = btn.getAttribute("data-client-name") ?: "—"
            val balance = btn.getAttribute("data-balance")?.toDoubleOrNull() ?: 0.0
            val limitRaw = btn.getAttribute("data-credit-limit")?.trim().orEmpty()
            val thresholdRaw = btn.getAttribute("data-alert-threshold")?.trim().orEmpty()
            openCreditLimitModal(
                clientId = id,
                clientName = name,
                currentBalance = balance,
                creditLimit = limitRaw.toDoubleOrNull(),
                alertThreshold = thresholdRaw.toDoubleOrNull(),
            )
        })
    }
}

fun openCreditLimitModal(
    clientId: Long,
    clientName: String,
    currentBalance: Double,
    creditLimit: Double?,
    alertThreshold: Double?,
) {
    if (!isEditor()) {
        showMessage("You do not have permission to change credit limits.", "error")
        return
    }
    val title = if (creditLimit != null) "Edit credit limit" else "Set credit limit"
    val limitValue = creditLimit?.let { formatCreditLimitNumber(it) } ?: ""
    val thresholdValue = when {
        creditLimit != null && alertThreshold != null -> formatCreditLimitNumber(alertThreshold)
        creditLimit != null -> formatCreditLimitNumber(alertThresholdFromCreditLimit(creditLimit))
        else -> ""
    }
    val balanceText = formatClientBalanceAmount(currentBalance)
    val availableText = formatAvailableCredit(currentBalance, creditLimit) ?: "— (set a limit)"

    val modalHTML = """
        <div id="creditLimitModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2 id="creditLimitModalTitle">$title</h2>
                    <button type="button" id="closeCreditLimitModalBtn" class="client-modal-close">&times;</button>
                </div>
                <p class="client-ledger-hint" style="margin: 0 0 12px;">Client: <strong>${clientName}</strong></p>
                <form id="creditLimitForm" class="client-form">
                    <input type="hidden" id="clClientId" value="$clientId">
                    <input type="hidden" id="clCurrentBalance" value="$currentBalance">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label class="client-form-label">Current balance</label>
                            <div class="client-form-readonly">$balanceText</div>
                        </div>
                        <div class="client-form-field">
                            <label class="client-form-label">Available credit (now)</label>
                            <div id="clAvailablePreview" class="client-form-readonly">$availableText</div>
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="clCreditLimit" class="client-form-label">Credit limit (¥)</label>
                            <input type="number" id="clCreditLimit" name="clCreditLimit" step="1" min="0"
                                   class="client-form-input" placeholder="e.g. 50000000" value="$limitValue">
                            <div class="client-form-hint">Leave blank to remove credit limit.</div>
                        </div>
                        <div class="client-form-field">
                            <label for="clAlertThreshold" class="client-form-label">Alert threshold (90% of limit)</label>
                            <input type="text" id="clAlertThreshold" name="clAlertThreshold" readonly
                                   class="client-form-input client-form-readonly" tabindex="-1"
                                   value="$thresholdValue">
                            <div class="client-form-hint">Calculated automatically from credit limit.</div>
                        </div>
                    </div>
                    <div class="client-modal-actions">
                        <button type="button" id="cancelCreditLimitBtn" class="client-btn client-btn-secondary">Cancel</button>
                        <button type="submit" class="client-btn client-btn-primary">Save credit limit</button>
                    </div>
                </form>
            </div>
        </div>
    """

    document.getElementById("creditLimitModal")?.remove()
    document.body?.insertAdjacentHTML("beforeend", modalHTML)

    fun refreshAvailablePreview() {
        val balance = (document.getElementById("clCurrentBalance") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: currentBalance
        val limit = (document.getElementById("clCreditLimit") as? HTMLInputElement)?.value?.trim()?.toDoubleOrNull()
        val preview = document.getElementById("clAvailablePreview")
        preview?.textContent = if (limit != null) {
            formatAvailableCredit(balance, limit) ?: "—"
        } else {
            "— (no limit set)"
        }
    }

    document.getElementById("clCreditLimit")?.addEventListener("input", { _: Event ->
        refreshAvailablePreview()
        syncAlertThresholdFromCreditLimit("clCreditLimit", "clAlertThreshold")
    })

    document.getElementById("cancelCreditLimitBtn")?.addEventListener("click", { _: Event -> closeCreditLimitModal() })
    document.getElementById("closeCreditLimitModalBtn")?.addEventListener("click", { _: Event -> closeCreditLimitModal() })
    document.getElementById("creditLimitModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "creditLimitModal") closeCreditLimitModal()
    })

    document.getElementById("creditLimitForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleCreditLimitSubmit(clientName)
    })
    wireClientModalA11y("creditLimitModal", "creditLimitModalTitle", ::closeCreditLimitModal, "clCreditLimit")
}

private fun handleCreditLimitSubmit(clientName: String) {
    val clientId = (document.getElementById("clClientId") as? HTMLInputElement)?.value?.trim()?.toLongOrNull()
    if (clientId == null) {
        showMessage("Could not determine client.", "error")
        return
    }

    val limitRaw = (document.getElementById("clCreditLimit") as? HTMLInputElement)?.value?.trim().orEmpty()

    val creditLimit: Double?
    val alertThreshold: Double?

    if (limitRaw.isEmpty()) {
        creditLimit = null
        alertThreshold = null
    } else {
        creditLimit = limitRaw.toDoubleOrNull()
        if (creditLimit == null || creditLimit < 0.0) {
            showMessage("Credit limit must be a number ≥ 0.", "error")
            return
        }
        alertThreshold = alertThresholdFromCreditLimit(creditLimit)
    }

    val payload = js("({})")
    payload["creditLimit"] = creditLimit
    payload["alertThreshold"] = alertThreshold

    val submitBtn = document.querySelector("#creditLimitForm button[type='submit']") as? HTMLButtonElement
    submitBtn?.disabled = true
    submitBtn?.textContent = "Saving..."

    val req = js("({})")
    req.method = "PUT"
    req.headers = js("{\"Content-Type\": \"application/json\"}")
    req.body = JSON.stringify(payload)

    window.fetch(apiUrl("clients/$clientId"), req).then { response ->
        if (response.ok) {
            showMessage("Credit limit updated for $clientName.", "success")
            closeCreditLimitModal()
            val onListPage = document.getElementById("clientListTable") != null
            val onDetailPage = document.getElementById("clientDetailsContent") != null
            if (onListPage) loadClients()
            if (onDetailPage) {
                loadClientDetails(clientId)
                loadClientEvents(clientId)
            }
            Unit
        } else {
            response.text().then { errorText ->
                showMessage("Failed to update credit limit: $errorText", "error")
            }
        }
    }.catch { error ->
        showMessage("Error updating credit limit: $error", "error")
    }.finally {
        submitBtn?.disabled = false
        submitBtn?.textContent = "Save credit limit"
    }
}

@JsName("closeCreditLimitModal")
fun closeCreditLimitModal() {
    clearClientModalEscape()
    document.getElementById("creditLimitModal")?.remove()
}

private fun parseMasterClientNameList(raw: dynamic): List<String> {
    val arr: dynamic = when {
        raw != null && js("Array.isArray(raw)") as Boolean -> raw
        raw != null && raw.data != null && (js("Array.isArray(raw.data)") as Boolean) -> raw.data
        else -> null
    }
    if (arr == null) return emptyList()
    val a = arr.unsafeCast<Array<*>>()
    return (0 until a.size)
        .map { (a[it]?.toString() ?: "").trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.uppercase() }
        .sortedBy { it.uppercase() }
}

private fun getClientModalComboboxValue(fieldId: String): String {
    val inputValue = (document.getElementById("${fieldId}Input") as? HTMLInputElement)?.value?.trim()
    if (!inputValue.isNullOrBlank()) return inputValue
    return (document.getElementById(fieldId) as? HTMLSelectElement)?.value?.trim().orEmpty()
}

private fun setClientModalComboboxValue(fieldId: String, value: String?) {
    val v = value?.trim().orEmpty()
    val select = document.getElementById(fieldId) as? HTMLSelectElement ?: return
    if (v.isNotEmpty()) {
        val exists = (0 until select.options.length).any { idx ->
            (select.options.item(idx) as? HTMLOptionElement)?.value?.equals(v, ignoreCase = true) == true
        }
        if (!exists) {
            val fallback = document.createElement("option") as HTMLOptionElement
            fallback.value = v
            fallback.text = v
            select.add(fallback)
        }
    }
    select.value = v
    val input = document.getElementById("${fieldId}Input") as? HTMLInputElement
    if (input != null) {
        input.value = v
        js("window.__tmpClientComboboxId = fieldId")
        js("if (typeof window.syncComboboxInput === 'function') { window.syncComboboxInput(window.__tmpClientComboboxId); }")
    }
}

/** Distinct `client_map.client_name` from GET client-map/dropdowns/client-names (`{ success, data: string[] }`). */
private fun parseClientMapNameList(raw: dynamic): List<String> = parseMasterClientNameList(raw)

private fun populateClientNameOptions(selectId: String, selectedValue: String?) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    val placeholder = if (selectId == "clientName") "Select Client Name" else "▼"
    select.innerHTML = """<option value="">$placeholder</option>"""
    window.fetch(apiUrl("client-map/dropdowns/client-names"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load client map names')")
        }
        .then { raw: dynamic ->
            val clients = parseClientMapNameList(raw)
            clients.forEach { name ->
                val option = document.createElement("option") as HTMLOptionElement
                option.value = name
                option.text = name
                select.add(option)
            }
            setClientModalComboboxValue(selectId, selectedValue)
        }
        .catch { error: dynamic ->
            Logger.warn("Client map names unavailable, falling back to master menu: ${error.toString()}")
            window.fetch(apiUrl("master-menu/clients"))
                .then { response: dynamic ->
                    if (response.ok) response.json() else throw js("Error('Failed to load client list')")
                }
                .then { raw: dynamic ->
                    parseMasterClientNameList(raw).forEach { name ->
                        val option = document.createElement("option") as HTMLOptionElement
                        option.value = name
                        option.text = name
                        select.add(option)
                    }
                    setClientModalComboboxValue(selectId, selectedValue)
                }
                .catch { fallbackErr: dynamic ->
                    Logger.error("Failed to populate client-name options: ${fallbackErr.toString()}")
                }
        }
}

fun addClientTransaction(clientId: dynamic) {
    if (!isEditor()) {
        showMessage("You do not have permission to add transactions.", "error")
        return
    }
    val id = when (clientId) {
        is Number -> clientId.toLong()
        else -> clientId?.toString()?.toLongOrNull()
    }
    if (id == null) {
        showMessage("Could not determine client. Open the client from Client Transactions again.", "error")
        return
    }
    openAddTransactionModal(id)
}

fun openAddTransactionModal(clientId: Long) {
    val modalHTML = """
        <div id="addTransactionModal" class="client-modal" data-client-id="$clientId">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2 id="addTransactionModalTitle">Add Ledger Entry</h2>
                    <button type="button" id="closeTxModalBtn" class="client-modal-close">&times;</button>
                </div>
                <p class="client-ledger-hint" style="margin: 0 0 16px;">Invoices post automatically. Enter bank payments or adjustments here.</p>
                <form id="addTransactionForm" class="client-form">
                    <input type="hidden" id="txClientId" value="$clientId">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="txDateText" class="client-form-label">Date *</label>
                            <div class="client-date-wrap">
                                <div class="client-date-row">
                                    <input type="text" id="txDateText" maxlength="10" inputmode="numeric" autocomplete="off" required class="client-form-input" placeholder="MM/DD/YYYY">
                                    <button type="button" id="txDateCalendarBtn" class="client-calendar-btn"
                                            title="Open calendar" aria-label="Open transaction date calendar">${purchaseFormCalendarIconSvg()}</button>
                                </div>
                                <input type="date" id="txDate" name="txDate" required class="client-form-input" tabindex="-1" aria-hidden="true"
                                       style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;">
                            </div>
                        </div>
                        <div class="client-form-field">
                            <label for="txEventType" class="client-form-label">Type *</label>
                            <select id="txEventType" name="txEventType" required class="client-form-select">
                                <option value="">Select type</option>
                                <option value="PAYMENT_RECEIVED">Payment received</option>
                                <option value="ADJUSTMENT">Adjustment</option>
                                <option value="OPENING_BALANCE">Opening balance</option>
                            </select>
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="txBillNo" class="client-form-label">Reference (TT slip, bank ref)</label>
                            <input type="text" id="txBillNo" name="txBillNo" class="client-form-input" placeholder="Optional">
                        </div>
                        <div class="client-form-field">
                            <label for="txDescription" class="client-form-label">Description / note</label>
                            <input type="text" id="txDescription" name="txDescription" class="client-form-input" placeholder="Required for adjustments">
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field full-width">
                            <label for="txAmount" class="client-form-label">Amount (¥) *</label>
                            <input type="text" id="txAmount" name="txAmount" required
                                   placeholder="Payment: positive. Adjustment: use − for debit"
                                   class="client-form-input" inputmode="decimal">
                        </div>
                    </div>
                    <div class="client-modal-actions">
                        <button type="button" id="cancelTxBtn" class="client-btn client-btn-secondary">Cancel</button>
                        <button type="submit" class="client-btn client-btn-primary">Save Entry</button>
                    </div>
                </form>
            </div>
        </div>
    """

    fun parseCurrency(input: String?): Double? {
        if (input == null) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed.replace(Regex("[^0-9.-]"), "")
        return cleaned.toDoubleOrNull()
    }

    fun isValidMoneyInput(raw: String): Boolean {
        if (raw.isBlank()) return true
        val v = raw.trim()
        if (!v.matches(Regex("^-?\\d{1,15}(\\.\\d{0,2})?$"))) return false
        if (v.startsWith("0") && v.length > 1 && v[1] != '.') return false
        return true
    }

    fun restrictMoneyInput(fieldId: String) {
        val input = document.getElementById(fieldId) as? HTMLInputElement ?: return
        input.addEventListener("keydown", { ev ->
            val event = ev as? org.w3c.dom.events.KeyboardEvent ?: return@addEventListener
            val key = event.asDynamic().key as? String ?: return@addEventListener
            val isDigit = key.length == 1 && key[0] in '0'..'9'
            val isDecimal = key == "."
            val isMinus = key == "-"
            val isNav = setOf("Backspace", "Delete", "ArrowLeft", "ArrowRight", "Tab", "Enter", "Home", "End").contains(key)
            if (!(isDigit || isDecimal || isMinus || isNav || event.ctrlKey || event.metaKey)) {
                event.preventDefault()
            }
        })
        input.addEventListener("input", { ev ->
            val el = ev.target as? HTMLInputElement ?: return@addEventListener
            if (!isValidMoneyInput(el.value)) {
                el.value = el.value.replace(Regex("[^0-9.-]"), "")
            }
        })
    }

    document.getElementById("addTransactionModal")?.remove()
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    bindStrictDateTextMask("txDate")
    window.setTimeout({ restrictMoneyInput("txAmount") }, 100)

    document.getElementById("cancelTxBtn")?.addEventListener("click", { _: Event -> closeAddTransactionModal() })
    document.getElementById("closeTxModalBtn")?.addEventListener("click", { _: Event -> closeAddTransactionModal() })
    document.getElementById("addTransactionModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "addTransactionModal") closeAddTransactionModal()
    })
    wireClientModalA11y("addTransactionModal", "addTransactionModalTitle", ::closeAddTransactionModal, "txDateText")

    val typeSelect = document.getElementById("txEventType") as? HTMLSelectElement
    typeSelect?.addEventListener("change", { _: Event ->
        val isAdj = typeSelect.value == "ADJUSTMENT"
        val desc = document.getElementById("txDescription") as? HTMLInputElement
        desc?.placeholder = if (isAdj) "Required — reason for adjustment" else "Optional note"
    })

    document.getElementById("addTransactionForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()

        val dateIso = (document.getElementById("txDate") as? HTMLInputElement)?.value?.trim().orEmpty()
        val eventType = (document.getElementById("txEventType") as? HTMLSelectElement)?.value?.trim().orEmpty()
        val billNo = (document.getElementById("txBillNo") as? HTMLInputElement)?.value?.trim().orEmpty()
        val description = (document.getElementById("txDescription") as? HTMLInputElement)?.value?.trim().orEmpty()
        val amount = parseCurrency((document.getElementById("txAmount") as? HTMLInputElement)?.value)

        if (dateIso.isBlank() || eventType.isBlank()) {
            showMessage("Please select date and type.", "error")
            return@addEventListener
        }
        if (amount == null || amount == 0.0) {
            showMessage("Please enter a non-zero amount.", "error")
            return@addEventListener
        }
        if (eventType == "PAYMENT_RECEIVED" && amount <= 0.0) {
            showMessage("Payment amount must be positive.", "error")
            return@addEventListener
        }
        if (eventType == "ADJUSTMENT" && description.isEmpty()) {
            showMessage("Adjustment requires a description/reason.", "error")
            return@addEventListener
        }

        val paymentReceived: Double?
        val transactionPrice: Double?
        when (eventType) {
            "PAYMENT_RECEIVED" -> {
                paymentReceived = amount
                transactionPrice = null
            }
            "ADJUSTMENT", "OPENING_BALANCE" -> if (amount > 0) {
                paymentReceived = amount
                transactionPrice = null
            } else {
                paymentReceived = null
                transactionPrice = kotlin.math.abs(amount)
            }
            else -> {
                showMessage("Invalid transaction type.", "error")
                return@addEventListener
            }
        }

        val eventDesc = when {
            description.isNotEmpty() -> description
            eventType == "PAYMENT_RECEIVED" -> "Payment received"
            eventType == "OPENING_BALANCE" -> "Opening balance as of $dateIso"
            else -> "Adjustment"
        }

        val txClientIdRaw = (document.getElementById("txClientId") as? HTMLInputElement)?.value?.trim().orEmpty()
        val resolvedClientId = txClientIdRaw.toLongOrNull() ?: clientId

        val payload = js("({})")
        payload["clientId"] = resolvedClientId.toDouble()
        payload["eventDate"] = dateIso
        payload["eventType"] = eventType
        payload["eventDescription"] = eventDesc
        payload["billNumber"] = if (billNo.isEmpty()) null else billNo
        payload["paymentReceived"] = paymentReceived
        payload["transactionPrice"] = transactionPrice

        val req = js("({})")
        req.method = "POST"
        req.headers = js("{\"Content-Type\": \"application/json\"}")
        req.body = JSON.stringify(payload)

        window.fetch(apiUrl("clients/add-transaction"), req).then { response ->
            if (response.ok) {
                response.json().then { _ ->
                    showMessage("Ledger entry saved.", "success")
                    closeAddTransactionModal()
                    loadClientDetails(resolvedClientId)
                    loadClientEvents(resolvedClientId)
                }
            } else {
                response.text().then { errorText ->
                    ErrorHandler.showError("Failed to save entry: $errorText")
                }
            }
        }.catch { error ->
            ErrorHandler.showError(ErrorHandler.handleNetworkError(error, "clients/add-transaction"))
        }
    })
}

@JsName("closeAddTransactionModal")
fun closeAddTransactionModal() {
    clearClientModalEscape()
    document.getElementById("addTransactionModal")?.remove()
}

@JsName("openAddTransactionModalFromClientManagement")
fun openAddTransactionModalGlobal(clientId: Long) {
    openAddTransactionModal(clientId)
}

fun loadClientsForPurchase() {
    window.fetch(apiUrl("clients"))
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    populateClientDropdown(clients)
                }
            } else {
                Logger.error("Failed to load clients for purchase form")
            }
        }
        .catch { error ->
            Logger.error("Error loading clients for purchase form: ${error.toString()}")
        }
}

fun populateClientDropdown(clients: dynamic) {
    val clientSelect = document.getElementById("clientId") as HTMLSelectElement?
    val editClientSelect = document.getElementById("editClientId") as HTMLSelectElement?
    
    if (clientSelect != null) {
        clientSelect.innerHTML = "<option value=\"\">Select Client</option>"
        if (js("Array.isArray(clients)") as Boolean) {
            val clientsArray = clients as Array<dynamic>
            clientsArray.forEach { client ->
                val option = document.createElement("option")
                (option as HTMLOptionElement).value = client.id.toString()
                option.textContent = "${client.clientName} (#${client.clientNumber})"
                clientSelect.appendChild(option)
            }
        }
    }
    
    if (editClientSelect != null) {
        editClientSelect.innerHTML = "<option value=\"\">Select Client</option>"
        if (js("Array.isArray(clients)") as Boolean) {
            val clientsArray = clients as Array<dynamic>
            clientsArray.forEach { client ->
                val option = document.createElement("option")
                (option as HTMLOptionElement).value = client.id.toString()
                option.textContent = "${client.clientName} (#${client.clientNumber})"
                editClientSelect.appendChild(option)
            }
        }
    }
}

fun handleClientSelection(clientId: String) {
    if (clientId.isBlank()) {
        (document.getElementById("clientBalanceInfo") as HTMLElement?)?.style?.display = "none"
        return
    }
    
    window.fetch(apiUrl("clients/$clientId"))
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    displayClientBalance(client, "clientBalance", "creditLimitWarning", "clientBalanceInfo")
                }
            }
        }
        .catch { error ->
            Logger.error("Error loading client details: ${error.toString()}")
        }
}

fun handleEditClientSelection(clientId: String) {
    if (clientId.isBlank()) {
        (document.getElementById("editClientBalanceInfo") as HTMLElement?)?.style?.display = "none"
        return
    }
    
    window.fetch(apiUrl("clients/$clientId"))
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    displayClientBalance(client, "editClientBalance", "editCreditLimitWarning", "editClientBalanceInfo")
                }
            }
        }
        .catch { error ->
            Logger.error("Error loading client details: ${error.toString()}")
        }
}

fun displayClientBalance(client: dynamic, balanceElementId: String, warningElementId: String, infoElementId: String) {
    val balance = (client.currentBalance as Number).toDouble()
    val creditLimit = (client.creditLimit as Number?)?.toDouble()
    val alertThreshold = (client.alertThreshold as Number?)?.toDouble()
    
    val balanceElement = document.getElementById(balanceElementId)
    val warningElement = document.getElementById(warningElementId)
    val infoElement = document.getElementById(infoElementId)
    
    if (balanceElement != null) {
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        balanceElement.innerHTML = "<span style='color: $balanceColor;'>$balanceText</span>"
    }
    
    if (infoElement != null) {
        (infoElement as HTMLElement).style.display = "block"
    }
    
    if (warningElement != null && creditLimit != null && creditLimit > 0.0) {
        val usedPct = (kotlin.math.abs(balance) / creditLimit) * 100.0
        val warningHTMLElement = warningElement as HTMLElement
        if (usedPct >= 100.0) {
            warningHTMLElement.style.display = "block"
            warningHTMLElement.innerHTML = """
                <div style="color: #e74c3c; font-size: 12px; margin-top: 5px;">
                    ⚠️ Credit limit exceeded (${usedPct.toInt()}% used)
                </div>
            """
        } else if (usedPct >= 80.0) {
            warningHTMLElement.style.display = "block"
            warningHTMLElement.innerHTML = """
                <div style="color: #ffc107; font-size: 12px; margin-top: 5px;">
                    ⚠️ Approaching credit limit (${usedPct.toInt()}% used)
                </div>
            """
        } else {
            warningHTMLElement.style.display = "none"
        }
    }
}

fun filterClients() {
    // Server-side search via [loadClients]; kept for any legacy callers.
    clientListPageZeroBased = 0
    loadClients(0)
}

fun toggleClientAlerts() {
    val section = document.getElementById("clientAlertsSection") as HTMLElement?
    if (section != null) {
        val isVisible = section.style.display != "none"
        section.style.display = if (isVisible) "none" else "block"
        if (!isVisible) {
            loadClientAlerts()
        }
    }
}

fun loadClientAlerts() {
    window.fetch(apiUrl("clients/alerts"))
        .then { response ->
            if (response.ok) {
                response.json().then { alerts ->
                    displayClientAlerts(alerts)
                }
            } else {
                document.getElementById("clientAlertsTable")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load client alerts
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientAlertsTable")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading client alerts: $error
                </div>
            """
        }
}

fun displayClientAlerts(alerts: dynamic) {
    val alertsTable = document.getElementById("clientAlertsTable")
    if (alertsTable == null) return
    
    if (js("Array.isArray(alerts)") as Boolean && (alerts as Array<dynamic>).isEmpty()) {
        alertsTable.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No alerts found
            </div>
        """
        return
    }
    
    val alertsArray = alerts as Array<dynamic>
    val alertsHtml = alertsArray.mapNotNull { client ->
        val balance = (client.currentBalance as Number).toDouble()
        val creditLimit = (client.creditLimit as Number?)?.toDouble()
        
        // Determine alert type and color
        // Credit Limit Alerts only show when >= 90% usage
        val (alertType, borderColor, alertColor) = when {
            creditLimit != null && creditLimit > 0.0 -> {
                val usedPct = (kotlin.math.abs(balance) / creditLimit) * 100.0
                when {
                    balance < -creditLimit -> Triple("Over credit limit", "#e74c3c", "#e74c3c")
                    usedPct >= 100.0 -> Triple("Over credit limit", "#e74c3c", "#e74c3c")
                    usedPct >= 90.0 -> Triple("Near credit limit", "#ffc107", "#ffc107")
                    else -> Triple("", "", "")
                }
            }
            balance < 0 -> Triple("Amount owed", "#e74c3c", "#e74c3c")
            else -> Triple("", "", "")
        }
        
        // Skip clients that don't have alerts (empty alertType means no alert)
        if (alertType.isEmpty()) {
            return@mapNotNull null
        }
        
        val usedPct = if (creditLimit != null && creditLimit > 0.0) {
            (kotlin.math.abs(balance) / creditLimit) * 100.0
        } else null
        
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        
        """
        <div style="border: 2px solid $borderColor; border-radius: 4px; padding: 15px; margin-bottom: 10px; background-color: white;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                    <div style="font-weight: bold; color: $alertColor; margin-bottom: 5px;">$alertType</div>
                    <div style="font-weight: bold; color: #333; margin-bottom: 5px;">${client.clientName}</div>
                    <div style="font-size: 12px; color: #666;">#${client.clientNumber}</div>
                </div>
                <div style="text-align: right;">
                    <div style="font-weight: bold; color: $balanceColor; margin-bottom: 5px;">$balanceText</div>
                    ${if (usedPct != null && usedPct >= 90.0) {
                        val pct = usedPct.toInt()
                        "<div style=\"font-size: 12px; color: #666;\">${pct}% of credit limit used</div>"
                    } else {
                        ""
                    }}
                </div>
            </div>
        </div>
        """
    }.joinToString("")
    
    // If no alerts after filtering, show "No alerts found"
    if (alertsHtml.isEmpty()) {
        alertsTable.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No alerts found
            </div>
        """
        return
    }
    
    alertsTable.innerHTML = alertsHtml
}


fun exportClientsData() {
    window.fetch(apiUrl("clients"))
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    exportClientsToCSV(clients)
                }
            } else {
                showMessage("Failed to load clients for export", "error")
            }
        }
        .catch { error ->
            showMessage("Error loading clients: $error", "error")
        }
}

fun exportClientsToCSV(clients: dynamic) {
    if (!js("Array.isArray(clients)") as Boolean || (clients as Array<dynamic>).isEmpty()) {
        showMessage("No clients to export", "info")
        return
    }
    
    val clientsArray = clients as Array<dynamic>
    
    // CSV headers
    val headers = listOf("clientNumber", "clientName", "currentBalance", "creditLimit", "alertThreshold", "currency", "status")
    val csvRows = mutableListOf<String>()
    
    // Add header row
    csvRows.add(headers.joinToString(","))
    
    // Add data rows
    clientsArray.forEach { client ->
        val row = headers.map { header ->
            val value = when (header) {
                "clientNumber" -> client.clientNumber?.toString() ?: ""
                "clientName" -> client.clientName?.toString() ?: ""
                "currentBalance" -> ((client.currentBalance as? Number)?.toDouble() ?: 0.0).toString()
                "creditLimit" -> ((client.creditLimit as? Number?)?.toDouble() ?: null)?.toString() ?: ""
                "alertThreshold" -> ((client.alertThreshold as? Number?)?.toDouble() ?: null)?.toString() ?: ""
                "currency" -> client.currency?.toString() ?: "JPY"
                "status" -> client.status?.toString() ?: "ACTIVE"
                else -> ""
            }
            // Escape commas and quotes in values
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                "\"${value.replace("\"", "\"\"")}\""
            } else {
                value
            }
        }
        csvRows.add(row.joinToString(","))
    }
    
    val csvContent = csvRows.joinToString("\n")
    
    // Create Blob and download
    val blob = js("new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })")
    val link = document.createElement("a") as HTMLAnchorElement
    val url = js("URL.createObjectURL(blob)") as String
    link.href = url
    val timestamp = js("Date.now()")
    link.download = "clients_export_$timestamp.csv"
    link.style.display = "none"
    document.body?.appendChild(link)
    link.click()
    document.body?.removeChild(link)
    // Revoke URL after download completes
    window.setTimeout({
        try {
            js("URL.revokeObjectURL(url)")
        } catch (e: dynamic) {
            console.warn("Failed to revoke URL:", e)
        }
    }, 1000)
    
    showMessage("Clients exported successfully!", "success")
}

fun generateBalanceSummary() {
    // Implementation will be added
}

fun displayBalanceSummary(clients: dynamic) {
    // Implementation will be added
}

fun generateTransactionReport() {
    // Implementation will be added
}

fun generateCreditLimitReport() {
    // Implementation will be added
}

fun displayCreditLimitReport(clients: dynamic) {
    // Implementation will be added
}

fun generateAuditTrail() {
    // Implementation will be added
}

fun showReportResult(html: String) {
    // Implementation will be added
}

fun showPerformanceDashboard() {
    // Implementation will be added
}

fun closePerformanceDashboard() {
    // Implementation will be added
}

fun loadPerformanceData() {
    // Implementation will be added
}

fun displayPerformanceData(data: dynamic) {
    // Implementation will be added
}

private fun downloadPdfFromUrl(url: String, filename: String, successMessage: String) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    window.fetch(url, opts)
        .then { response ->
            if (response.ok) {
                response.blob().then { blob ->
                    val blobUrl = js("URL.createObjectURL(blob)") as String
                    try {
                        val link = document.createElement("a") as HTMLAnchorElement
                        link.href = blobUrl
                        link.download = filename
                        link.style.display = "none"
                        document.body?.appendChild(link)
                        link.click()
                        document.body?.removeChild(link)
                        showMessage(successMessage, "success")
                    } finally {
                        js("URL.revokeObjectURL(blobUrl)")
                    }
                }
            } else {
                response.text().then { errorText ->
                    showMessage("Download failed: $errorText", "error")
                }
            }
        }
        .catch { error ->
            showMessage("Download failed: $error", "error")
        }
}

fun openStatementPdfModal(clientId: Long) {
    document.getElementById("statementPdfModal")?.remove()
    val modalHTML = """
        <div id="statementPdfModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2 id="statementPdfModalTitle">Export statement PDF</h2>
                    <button type="button" id="closeStatementPdfModalBtn" class="client-modal-close">&times;</button>
                </div>
                <p class="client-ledger-hint">Leave dates empty to include all ledger entries.</p>
                <div class="client-form-row">
                    <div class="client-form-field">
                        <label for="stmtStartDate" class="client-form-label">From (optional)</label>
                        <input type="date" id="stmtStartDate" class="client-form-input"
                               min="${AppConstants.MIN_YEAR}-01-01" max="${AppConstants.MAX_YEAR}-12-31">
                    </div>
                    <div class="client-form-field">
                        <label for="stmtEndDate" class="client-form-label">To (optional)</label>
                        <input type="date" id="stmtEndDate" class="client-form-input"
                               min="${AppConstants.MIN_YEAR}-01-01" max="${AppConstants.MAX_YEAR}-12-31">
                    </div>
                </div>
                <div class="client-modal-actions">
                    <button type="button" id="cancelStatementPdfBtn" class="client-btn client-btn-secondary">Cancel</button>
                    <button type="button" id="downloadStatementPdfBtn" class="client-btn client-btn-primary">Download PDF</button>
                </div>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    fun close() {
        clearClientModalEscape()
        document.getElementById("statementPdfModal")?.remove()
    }
    document.getElementById("closeStatementPdfModalBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("cancelStatementPdfBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("statementPdfModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "statementPdfModal") close()
    })
    document.getElementById("downloadStatementPdfBtn")?.addEventListener("click", { _: Event ->
        val start = (document.getElementById("stmtStartDate") as? HTMLInputElement)?.value?.trim().orEmpty()
        val end = (document.getElementById("stmtEndDate") as? HTMLInputElement)?.value?.trim().orEmpty()
        val ts = js("Date.now()")
        val params = mutableListOf("ts=$ts")
        if (start.isNotEmpty()) params.add("startDate=$start")
        if (end.isNotEmpty()) params.add("endDate=$end")
        downloadPdfFromUrl(
            apiUrl("clients/$clientId/statement-pdf?${params.joinToString("&")}"),
            "client-statement-$clientId.pdf",
            "Statement PDF downloaded.",
        )
        close()
    })
    wireClientModalA11y("statementPdfModal", "statementPdfModalTitle", { close() }, "stmtStartDate")
}

fun openEditClientModal(
    clientId: Long,
    clientNumber: String,
    clientName: String,
    currency: String,
    status: String,
) {
    if (!isEditor()) {
        showMessage("You do not have permission to edit clients.", "error")
        return
    }
    document.getElementById("editClientProfileModal")?.remove()
    val modalHTML = """
        <div id="editClientProfileModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2 id="editClientProfileModalTitle">Edit client</h2>
                    <button type="button" id="closeEditClientProfileBtn" class="client-modal-close">&times;</button>
                </div>
                <form id="editClientProfileForm" class="client-form">
                    <input type="hidden" id="editProfileClientId" value="$clientId">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label class="client-form-label">Client #</label>
                            <input type="text" class="client-form-input" value="$clientNumber" disabled>
                        </div>
                        <div class="client-form-field">
                            <label class="client-form-label">Client name</label>
                            <div class="client-form-readonly">${clientName.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</div>
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="editProfileCurrency" class="client-form-label">Currency</label>
                            <select id="editProfileCurrency" class="client-form-select">
                                <option value="JPY"${if (currency == "JPY") " selected" else ""}>JPY</option>
                                <option value="USD"${if (currency == "USD") " selected" else ""}>USD</option>
                                <option value="EUR"${if (currency == "EUR") " selected" else ""}>EUR</option>
                            </select>
                        </div>
                        <div class="client-form-field">
                            <label for="editProfileStatus" class="client-form-label">Status</label>
                            <select id="editProfileStatus" class="client-form-select">
                                <option value="ACTIVE"${if (status.equals("ACTIVE", true)) " selected" else ""}>Active</option>
                                <option value="SUSPENDED"${if (status.equals("SUSPENDED", true)) " selected" else ""}>Suspended</option>
                                <option value="CLOSED"${if (status.equals("CLOSED", true)) " selected" else ""}>Closed</option>
                            </select>
                        </div>
                    </div>
                    <p class="client-ledger-hint">Balance changes via ledger entries only (payments, invoices, opening balance).</p>
                    <div class="client-modal-actions">
                        <button type="button" id="cancelEditClientProfileBtn" class="client-btn client-btn-secondary">Cancel</button>
                        <button type="submit" class="client-btn client-btn-primary">Save</button>
                    </div>
                </form>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    fun close() {
        clearClientModalEscape()
        document.getElementById("editClientProfileModal")?.remove()
    }
    document.getElementById("closeEditClientProfileBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("cancelEditClientProfileBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("editClientProfileModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "editClientProfileModal") close()
    })
    document.getElementById("editClientProfileForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        val payload = js("({})")
        payload["currency"] = (document.getElementById("editProfileCurrency") as? HTMLSelectElement)?.value ?: "JPY"
        payload["status"] = (document.getElementById("editProfileStatus") as? HTMLSelectElement)?.value ?: "ACTIVE"
        val req = js("({})")
        req.method = "PUT"
        req.headers = js("{\"Content-Type\": \"application/json\"}")
        req.body = JSON.stringify(payload)
        window.fetch(apiUrl("clients/$clientId"), req).then { response ->
            if (response.ok) {
                showMessage("Client updated.", "success")
                close()
                loadClientDetails(clientId)
                loadClients()
            } else {
                response.text().then { err -> showMessage("Update failed: $err", "error") }
            }
        }.catch { err -> showMessage("Update failed: $err", "error") }
    })
    wireClientModalA11y("editClientProfileModal", "editClientProfileModalTitle", { close() }, "editProfileCurrency")
}

fun openEditLedgerModal(clientId: Long, event: dynamic) {
    if (!isEditor()) return
    val eventId = event.id?.toString()?.toLongOrNull() ?: return
    val eventType = event.eventType?.toString()?.uppercase().orEmpty()
    val signedAmount = ledgerSignedAmount(event) ?: 0.0
    val dateStr = event.eventDate?.toString().orEmpty()
    val billNo = event.billNumber?.toString().orEmpty()
    val desc = event.eventDescription?.toString().orEmpty()

    document.getElementById("editLedgerModal")?.remove()
    val modalHTML = """
        <div id="editLedgerModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2>Edit ledger entry</h2>
                    <button type="button" id="closeEditLedgerBtn" class="client-modal-close">&times;</button>
                </div>
                <form id="editLedgerForm" class="client-form">
                    <input type="hidden" id="editLedgerEventId" value="$eventId">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label class="client-form-label">Type</label>
                            <input type="text" class="client-form-input" value="${eventTypeBadgeLabel(eventType)}" disabled>
                        </div>
                        <div class="client-form-field">
                            <label for="editLedgerDate" class="client-form-label">Date *</label>
                            <input type="date" id="editLedgerDate" class="client-form-input" required value="$dateStr"
                                   min="${AppConstants.MIN_YEAR}-01-01" max="${AppConstants.MAX_YEAR}-12-31">
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="editLedgerRef" class="client-form-label">Reference</label>
                            <input type="text" id="editLedgerRef" class="client-form-input" value="${billNo.replace("\"", "&quot;")}">
                        </div>
                        <div class="client-form-field">
                            <label for="editLedgerDesc" class="client-form-label">Description</label>
                            <input type="text" id="editLedgerDesc" class="client-form-input" value="${desc.replace("\"", "&quot;")}">
                        </div>
                    </div>
                    <div class="client-form-field full-width">
                        <label for="editLedgerAmount" class="client-form-label">Amount (¥) *</label>
                        <input type="text" id="editLedgerAmount" class="client-form-input" required value="$signedAmount">
                    </div>
                    <div class="client-modal-actions">
                        <button type="button" id="cancelEditLedgerBtn" class="client-btn client-btn-secondary">Cancel</button>
                        <button type="submit" class="client-btn client-btn-primary">Save changes</button>
                    </div>
                </form>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    fun close() {
        document.getElementById("editLedgerModal")?.remove()
    }
    document.getElementById("closeEditLedgerBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("cancelEditLedgerBtn")?.addEventListener("click", { _: Event -> close() })
    document.getElementById("editLedgerForm")?.addEventListener("submit", { ev: Event ->
        ev.preventDefault()
        val dateIso = (document.getElementById("editLedgerDate") as? HTMLInputElement)?.value?.trim().orEmpty()
        val amountStr = (document.getElementById("editLedgerAmount") as? HTMLInputElement)?.value?.trim().orEmpty()
        val amount = amountStr.replace(Regex("[^0-9.-]"), "").toDoubleOrNull()
        if (dateIso.isEmpty() || amount == null || amount == 0.0) {
            showMessage("Date and non-zero amount are required.", "error")
            return@addEventListener
        }
        val (payment, charge) = if (amount > 0) Pair(amount, null) else Pair(null, kotlin.math.abs(amount))
        val payload = js("({})")
        payload["eventDate"] = dateIso
        payload["eventDescription"] = (document.getElementById("editLedgerDesc") as? HTMLInputElement)?.value?.trim()
        payload["billNumber"] = (document.getElementById("editLedgerRef") as? HTMLInputElement)?.value?.trim()
        payload["paymentReceived"] = payment
        payload["transactionPrice"] = charge
        val req = js("({})")
        req.method = "PUT"
        req.headers = js("{\"Content-Type\": \"application/json\"}")
        req.body = JSON.stringify(payload)
        window.fetch(apiUrl("events/$eventId"), req).then { response ->
            if (response.ok) {
                showMessage("Ledger entry updated.", "success")
                close()
                loadClientDetails(clientId)
                loadClientEvents(clientId)
            } else {
                response.text().then { err -> showMessage("Update failed: $err", "error") }
            }
        }.catch { err -> showMessage("Update failed: $err", "error") }
    })
}

fun deleteLedgerEntry(clientId: Long, eventId: Long) {
    if (!isEditor()) return
    showClientConfirmModal(
        title = "Delete ledger entry?",
        message = "Delete this ledger entry? Balances will be recalculated.",
        confirmLabel = "Delete",
    ) {
        deleteLedgerEntryConfirmed(clientId, eventId)
    }
}

private fun deleteLedgerEntryConfirmed(clientId: Long, eventId: Long) {
    val req = js("({})")
    req.method = "DELETE"
    window.fetch(apiUrl("events/$eventId"), req).then { response ->
        if (response.ok) {
            showMessage("Ledger entry deleted.", "success")
            loadClientDetails(clientId)
            loadClientEvents(clientId)
        } else {
            response.text().then { err -> showMessage("Delete failed: $err", "error") }
        }
    }.catch { err -> showMessage("Delete failed: $err", "error") }
}

fun exportClientTransactions(clientId: Long) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    val ts = js("Date.now()")
    
    window.fetch(apiUrl("events/export/$clientId?ts=$ts"), opts)
        .then { response ->
            if (response.ok) {
                response.text().then { csvText ->
                    // Create Blob and download
                    val blob = js("new Blob([csvText], { type: 'text/csv;charset=utf-8;' })")
                    val link = document.createElement("a") as HTMLAnchorElement
                    val url = js("URL.createObjectURL(blob)") as String
                    link.href = url
                    link.download = "client_${clientId}_transactions.csv"
                    link.style.display = "none"
                    document.body?.appendChild(link)
                    link.click()
                    document.body?.removeChild(link)
                    // Revoke URL after download completes
                    window.setTimeout({
                        try {
                            js("URL.revokeObjectURL(url)")
                        } catch (e: dynamic) {
                            Logger.warn("Failed to revoke URL: ${e.toString()}")
                        }
                    }, 1000)
                    
                    showMessage("Transactions exported successfully!", "success")
                }
            } else {
                response.text().then { errorText ->
                    showMessage("Failed to export transactions: $errorText", "error")
                }
            }
        }
        .catch { error ->
            showMessage("Error exporting transactions: $error", "error")
        }
}

