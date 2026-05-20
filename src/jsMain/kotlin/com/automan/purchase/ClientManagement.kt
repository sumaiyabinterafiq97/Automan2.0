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
    "SHIPMENT" -> "Shipment"
    else -> "Other (legacy)"
}

private fun eventTypeBadgeClass(eventType: String?): String = when (eventType?.uppercase()) {
    "INVOICE_ISSUED" -> "ledger-type-badge ledger-type-invoice"
    "INVOICE_REVERSAL" -> "ledger-type-badge ledger-type-reversal"
    "PAYMENT_RECEIVED" -> "ledger-type-badge ledger-type-payment"
    "ADJUSTMENT" -> "ledger-type-badge ledger-type-adjustment"
    "SHIPMENT" -> "ledger-type-badge ledger-type-shipment"
    else -> "ledger-type-badge ledger-type-legacy"
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
        <div class="client-details-container">
            <div class="client-details-card">
                <div class="client-details-header">
                    <h2>Client Details</h2>
                    <div class="client-details-actions">
                        <button id="exportClientTxBtn" class="client-btn client-btn-info">Export Data</button>
                        <button id="backToClientsBtn" class="client-btn client-btn-secondary">Back to Client Transactions</button>
                    </div>
                </div>
                <div id="clientDetailsContent"></div>
                <div id="clientEventsTable" class="client-transactions-section">
                    <div style="text-align: center; color: #666; padding: 20px;">
                        Loading transactions...
                    </div>
                </div>
            </div>
        </div>
    """
    document.getElementById("backToClientsBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/client-transactions"
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
    val isMasterList = window.location.hash.startsWith("#/master/client-transactions")
    val pageTitle = if (isMasterList) "Client Transactions" else "Client Accounts Management"
    content.innerHTML = """
        <div class="client-page-container">
            <div class="client-page-card">
                <div class="client-page-header">
                    <h2>$pageTitle</h2>
                    <div class="client-action-buttons">
                        <button id="addClientBtn" class="client-btn client-btn-primary">Add New Client</button>
                        <button id="clientAlertsBtn" class="client-btn client-btn-warning">View Alerts</button>
                        <button id="exportClientsBtn" class="client-btn client-btn-info">Export Data</button>
                    </div>
                </div>
                
                <!-- Client Alerts Section -->
                <div id="clientAlertsSection" class="client-alerts-section">
                    <h3>Client Alerts</h3>
                    <div id="clientAlertsTable">
                        <div style="text-align: center; color: #666; padding: 20px;">
                            Loading client alerts...
                        </div>
                    </div>
                </div>
                
                <!-- Client List Section -->
                <div class="client-list-section">
                    <h3>Client List</h3>
                    <div class="client-search-container">
                        <input id="clientSearchInput" type="text" placeholder="Search clients..." class="client-search-input">
                    </div>
                    <div id="clientListTable" class="client-list-container">
                        <div style="text-align: center; color: #666; padding: 20px;">
                            Loading clients...
                        </div>
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
        filterClients()
    })
    
    // Load clients
    loadClients()

    if (!isEditor()) {
        document.getElementById("addClientBtn")?.asDynamic()?.style?.display = "none"
    }
}

fun loadClients() {
    window.fetch(apiUrl("clients"))
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    displayClients(clients)
                }
            } else {
                document.getElementById("clientListTable")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load clients
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientListTable")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading clients: $error
                </div>
            """
        }
}

fun displayClients(clients: dynamic) {
    val clientListTable = document.getElementById("clientListTable")
    if (clientListTable == null) return
    
    if (js("Array.isArray(clients)") as Boolean && (clients as Array<dynamic>).isEmpty()) {
        clientListTable.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No clients found
            </div>
        """
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
            usedPct >= 100.0 -> Triple("⚠️", "border-left: 4px solid #e74c3c;", """<div class="client-limit-badge client-limit-over">OVER LIMIT (${usedPct.toInt()}%)</div>""")
            usedPct >= 90.0 -> Triple("⚠️", "border-left: 4px solid #ffc107;", "")
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
                <div class="client-info client-info-flex" onclick="window.location.hash='#/client/${client.id}'">
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
    
    clientListTable.innerHTML = clientsHtml
    wireClientListCreditLimitButtons()
}

fun selectClient(clientId: Long) {
    window.location.hash = "#/client/$clientId"
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
                document.getElementById("clientDetailsContent")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load client details
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientDetailsContent")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading client details: $error
                </div>
            """
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
                    displayClientEvents(events)
                }
            } else {
                document.getElementById("clientEventsTable")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load transactions
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientEventsTable")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading transactions: $error
                </div>
            """
        }
}

fun displayClientEvents(events: dynamic) {
    val clientEventsTable = document.getElementById("clientEventsTable")
    if (clientEventsTable == null) return
    
    if (js("Array.isArray(events)") as Boolean && (events as Array<dynamic>).isEmpty()) {
        clientEventsTable.innerHTML = """
            <h3>Ledger</h3>
            <div style="text-align: center; color: #666; padding: 20px;">
                No ledger entries yet. Invoices post automatically when confirmed in Invoice.
            </div>
        """
        return
    }
    
    val eventsArray = events as Array<dynamic>

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
        
        """
        <tr>
            <td>${event.eventDate}</td>
            <td><span class="$typeClass">$typeLabel</span></td>
            <td>${if (desc.isEmpty()) "—" else desc}</td>
            <td>${if (ref.isEmpty()) "—" else ref}</td>
            <td style="text-align: right;">${fmtLedgerAmount(tPrice)}</td>
            <td style="text-align: right;">${fmtLedgerAmount(payment)}</td>
            <td style="text-align: right; color: $balanceColor; font-weight: bold;">$balanceText</td>
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
                        <th>DATE</th>
                        <th>TYPE</th>
                        <th>DESCRIPTION</th>
                        <th>REFERENCE</th>
                        <th style="text-align: right;">DEBIT</th>
                        <th style="text-align: right;">CREDIT</th>
                        <th style="text-align: right;">BALANCE</th>
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
}

// Note: Additional client management functions (showAddClientForm, handleAddClientSubmit, etc.) 
// will be added in subsequent updates to keep this file manageable.
// Add New Client Modal Implementation

fun showAddClientForm() {
    val modalHTML = """
        <div id="addClientModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2>Add New Client</h2>
                    <button id="closeAddClientModalBtn" class="client-modal-close">&times;</button>
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
                            ${createEditableCombobox("clientName", "Select Client Name", required = true)}
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
                            <label for="alertThreshold" class="client-form-label">Alert Threshold</label>
                            <input type="number" id="alertThreshold" name="alertThreshold" step="0.01"
                                   class="client-form-input" placeholder="e.g., 10000000">
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

    // Load combobox options from master_menu
    populateClientNameOptions("clientName", null)
}

fun handleAddClientSubmit() {
    val form = document.getElementById("addClientForm") as? HTMLFormElement
    if (form == null) return
    
    val clientData = js("{}")
    clientData["clientNumber"] = (document.getElementById("clientNumber") as? HTMLInputElement)?.value ?: ""
    clientData["clientName"] = getClientModalComboboxValue("clientName")
    clientData["currentBalance"] = (document.getElementById("currentBalance") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: 0.0
    clientData["creditLimit"] = (document.getElementById("creditLimit") as? HTMLInputElement)?.value?.toDoubleOrNull()
    clientData["alertThreshold"] = (document.getElementById("alertThreshold") as? HTMLInputElement)?.value?.toDoubleOrNull()
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
    val limitValue = creditLimit?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: ""
    val thresholdValue = alertThreshold?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: ""
    val balanceText = formatClientBalanceAmount(currentBalance)
    val availableText = formatAvailableCredit(currentBalance, creditLimit) ?: "— (set a limit)"

    val modalHTML = """
        <div id="creditLimitModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2>$title</h2>
                    <button id="closeCreditLimitModalBtn" class="client-modal-close">&times;</button>
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
                            <label for="clAlertThreshold" class="client-form-label">Alert threshold (¥)</label>
                            <input type="number" id="clAlertThreshold" name="clAlertThreshold" step="1" min="0"
                                   class="client-form-input" placeholder="Auto: 90% of limit if blank" value="$thresholdValue">
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

    document.getElementById("clCreditLimit")?.addEventListener("input", { _: Event -> refreshAvailablePreview() })

    document.getElementById("cancelCreditLimitBtn")?.addEventListener("click", { _: Event -> closeCreditLimitModal() })
    document.getElementById("closeCreditLimitModalBtn")?.addEventListener("click", { _: Event -> closeCreditLimitModal() })
    document.getElementById("creditLimitModal")?.addEventListener("click", { event: Event ->
        if ((event.target as? HTMLElement)?.id == "creditLimitModal") closeCreditLimitModal()
    })

    document.getElementById("creditLimitForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleCreditLimitSubmit(clientName)
    })
}

private fun handleCreditLimitSubmit(clientName: String) {
    val clientId = (document.getElementById("clClientId") as? HTMLInputElement)?.value?.trim()?.toLongOrNull()
    if (clientId == null) {
        showMessage("Could not determine client.", "error")
        return
    }

    val limitRaw = (document.getElementById("clCreditLimit") as? HTMLInputElement)?.value?.trim().orEmpty()
    val thresholdRaw = (document.getElementById("clAlertThreshold") as? HTMLInputElement)?.value?.trim().orEmpty()

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
        alertThreshold = if (thresholdRaw.isEmpty()) {
            creditLimit * 0.9
        } else {
            val t = thresholdRaw.toDoubleOrNull()
            if (t == null || t < 0.0) {
                showMessage("Alert threshold must be a number ≥ 0.", "error")
                return
            }
            t
        }
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
    val select = document.getElementById(fieldId) as? HTMLSelectElement
    val input = document.getElementById("${fieldId}Input") as? HTMLInputElement
    if (v.isNotEmpty()) {
        val exists = (0 until (select?.options?.length ?: 0)).any { idx ->
            (select?.options?.item(idx) as? HTMLOptionElement)?.value?.equals(v, ignoreCase = true) == true
        }
        if (!exists && select != null) {
            val fallback = document.createElement("option") as HTMLOptionElement
            fallback.value = v
            fallback.text = v
            select.add(fallback)
        }
    }
    if (select != null) select.value = v
    if (input != null) input.value = v
    js("window.__tmpClientComboboxId = fieldId")
    js("if (typeof window.syncComboboxInput === 'function') { window.syncComboboxInput(window.__tmpClientComboboxId); }")
}

private fun populateClientNameOptions(selectId: String, selectedValue: String?) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    select.innerHTML = """<option value="">▼</option>"""
    window.fetch(apiUrl("master-menu/clients"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load client list')")
        }
        .then { raw: dynamic ->
            val clients = parseMasterClientNameList(raw)
            clients.forEach { name ->
                val option = document.createElement("option") as HTMLOptionElement
                option.value = name
                option.text = name
                select.add(option)
            }
            setClientModalComboboxValue(selectId, selectedValue)
        }
        .catch { error: dynamic ->
            Logger.error("Failed to populate client-name options: ${error.toString()}")
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
                    <h2>Add Ledger Entry</h2>
                    <button id="closeTxModalBtn" class="client-modal-close">&times;</button>
                </div>
                <p class="client-ledger-hint" style="margin: 0 0 16px;">Invoices post automatically. Enter bank payments or adjustments here.</p>
                <form id="addTransactionForm" class="client-form">
                    <input type="hidden" id="txClientId" value="$clientId">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="txDate" class="client-form-label">DATE *</label>
                            <div style="position:relative; width:100%;">
                                <div style="display:flex; gap:8px; align-items:center; width:100%;">
                                    <input type="text" id="txDateText" maxlength="10" inputmode="numeric" autocomplete="off" required class="client-form-input" placeholder="MM/DD/YYYY">
                                    <button type="button" id="txDateCalendarBtn" title="Open calendar"
                                            style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                                </div>
                                <input type="date" id="txDate" name="txDate" required class="client-form-input" tabindex="-1" aria-hidden="true"
                                       style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;">
                            </div>
                        </div>
                        <div class="client-form-field">
                            <label for="txEventType" class="client-form-label">TYPE *</label>
                            <select id="txEventType" name="txEventType" required class="client-form-select">
                                <option value="">Select type</option>
                                <option value="PAYMENT_RECEIVED">Payment received</option>
                                <option value="ADJUSTMENT">Adjustment</option>
                            </select>
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="txBillNo" class="client-form-label">REFERENCE (TT slip, bank ref)</label>
                            <input type="text" id="txBillNo" name="txBillNo" class="client-form-input" placeholder="Optional">
                        </div>
                        <div class="client-form-field">
                            <label for="txDescription" class="client-form-label">DESCRIPTION / NOTE</label>
                            <input type="text" id="txDescription" name="txDescription" class="client-form-input" placeholder="Required for adjustments">
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field full-width">
                            <label for="txAmount" class="client-form-label">AMOUNT (¥) *</label>
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
            "ADJUSTMENT" -> if (amount > 0) {
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
    val searchInput = document.getElementById("clientSearchInput") as HTMLInputElement?
    val searchTerm = searchInput?.value?.uppercase() ?: ""
    
    val clientItems = document.querySelectorAll(".client-item")
    for (i in 0 until clientItems.length) {
        val item = clientItems.item(i) as HTMLElement
        val text = item.textContent ?: ""
        item.style.display = if (text.uppercase().contains(searchTerm)) "block" else "none"
    }
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
            balance < 0 -> Triple("Debt Alert", "#e74c3c", "#e74c3c")
            creditLimit != null && creditLimit > 0.0 -> {
                val usedPct = (kotlin.math.abs(balance) / creditLimit) * 100.0
                when {
                    usedPct >= 100.0 -> Triple("Credit Limit Alert", "#e74c3c", "#e74c3c")
                    usedPct >= 90.0 -> Triple("Credit Limit Alert", "#ffc107", "#ffc107")
                    else -> Triple("", "", "") // No alert if < 90%
                }
            }
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

