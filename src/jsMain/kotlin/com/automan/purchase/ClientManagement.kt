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
    val clientsHtml = clientsArray.map { client ->
        val balance = (client.currentBalance as Number).toDouble()
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        
        // Credit limit alert color logic (Green / Orange / Red)
        val creditLimit = (client.creditLimit as Number?)?.toDouble()
        val usedPct: Double? = if (creditLimit != null && creditLimit > 0.0) {
            (kotlin.math.abs(balance) / creditLimit) * 100.0
        } else null
        val (alertIcon, alertStyle) = when {
            usedPct == null -> Pair("", "")
            usedPct >= 100.0 -> Pair("⚠️", "border-left: 4px solid #e74c3c;") // Red - exceeded
            usedPct >= 90.0 -> Pair("⚠️", "border-left: 4px solid #ffc107;") // Orange - approaching (changed from 80% to 90%)
            else -> Pair("", "") // Green/normal - no alert border
        }
        
        """
        <div class="client-item" style="$alertStyle">
            <div class="client-item-content">
                <!-- Client Info (clickable for details) -->
                <div class="client-info" onclick="window.location.hash='#/client/${client.id}'">
                    <div class="client-info-row">
                        <div>
                            <div class="client-name">$alertIcon ${client.clientName}</div>
                            <div class="client-number">#${client.clientNumber}</div>
                        </div>
                        <div>
                            <div class="client-balance" style="color: $balanceColor;">$balanceText</div>
                            <div class="client-status">${client.status}</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        """
    }.joinToString("")
    
    clientListTable.innerHTML = clientsHtml
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
                    displayClientDetails(client.unsafeCast<ClientResponse>())
                    loadClientEvents(clientId)
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

fun displayClientDetails(client: ClientResponse) {
    val clientDetailsContent = document.getElementById("clientDetailsContent")
    if (clientDetailsContent == null) return
    
    val balance = client.currentBalance
    val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
    val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
    
    clientDetailsContent.innerHTML = """
        <div class="client-info-card">
            <h4 class="client-info-name">${client.clientName}</h4>
            <div class="client-info-grid">
                <div class="client-info-item"><strong>Client #:</strong> ${client.clientNumber}</div>
                <div class="client-info-item"><strong>Status:</strong> ${client.status ?: "N/A"}</div>
                <div class="client-info-item"><strong>Currency:</strong> ${client.currency ?: "JPY"}</div>
            </div>
            <div class="client-balance-card">
                <div id="currentBalanceValue" class="client-balance-amount" style="color: $balanceColor;">$balanceText</div>
                <div class="client-balance-label">Current Balance</div>
            </div>
            <div class="client-action-buttons">
                <button onclick="addClientTransaction(${client.id})" class="client-btn client-btn-success">Add Transaction</button>
            </div>
        </div>
    """
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
            <h3>Transactions</h3>
            <div style="text-align: center; color: #666; padding: 20px;">
                No transactions found
            </div>
        """
        return
    }
    
    val eventsArray = events as Array<dynamic>

    fun fmtAmount(value: dynamic?): String {
        if (value == null) return ""
        val n = (value as Number).toInt()
        return "¥${n}"
    }

    val rowsHtml = eventsArray.map { event ->
        val qtyRaw = event.quantity
        val qty = if (qtyRaw != null) (qtyRaw as Number).toInt() else null
        val qtyText = if (qty != null) "${qty} UNITS" else ""
        val tPrice = event.transactionPrice
        val payment = event.paymentReceived
        val balance = (event.runningBalance as Number).toDouble()
        val tPriceColor = if (tPrice != null) {
            val v = (tPrice as Number).toDouble()
            if (v < 0) "#e74c3c" else if (v > 0) "#27ae60" else "#666"
        } else "#666"
        val paymentColor = if (payment != null) {
            val v = (payment as Number).toDouble()
            if (v < 0) "#e74c3c" else if (v > 0) "#27ae60" else "#666"
        } else "#666"
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        
        """
        <tr>
            <td>${event.eventDate}</td>
            <td>${event.eventDescription}</td>
            <td style="text-align: right;">$qtyText</td>
            <td>${event.billNumber ?: ""}</td>
            <td style="text-align: right; color: $tPriceColor;">${fmtAmount(tPrice)}</td>
            <td style="text-align: right; color: $paymentColor;">${fmtAmount(payment)}</td>
            <td style="text-align: right; color: $balanceColor; font-weight: bold;">$balanceText</td>
        </tr>
        """
    }.joinToString("")
    
    // Mobile cards HTML
    val cardsHtml = eventsArray.map { event ->
        val qtyRaw = event.quantity
        val qty = if (qtyRaw != null) (qtyRaw as Number).toInt() else null
        val qtyText = if (qty != null) "${qty} UNITS" else "-"
        val tPrice = event.transactionPrice
        val payment = event.paymentReceived
        val balance = (event.runningBalance as Number).toDouble()
        val tPriceColor = if (tPrice != null) {
            val v = (tPrice as Number).toDouble()
            if (v < 0) "#e74c3c" else if (v > 0) "#27ae60" else "#666"
        } else "#666"
        val paymentColor = if (payment != null) {
            val v = (payment as Number).toDouble()
            if (v < 0) "#e74c3c" else if (v > 0) "#27ae60" else "#666"
        } else "#666"
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        
        """
        <div class="transaction-card">
            <div class="transaction-card-header">
                <span class="transaction-date">${event.eventDate}</span>
                <span class="transaction-event">${event.eventDescription}</span>
            </div>
            <div class="transaction-card-body">
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Quantity</span>
                    <span class="transaction-card-value">$qtyText</span>
                </div>
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Shipment Price</span>
                    <span class="transaction-card-value" style="color: $tPriceColor;">${fmtAmount(tPrice)}</span>
                </div>
                <div class="transaction-card-row">
                    <span class="transaction-card-label">Payment</span>
                    <span class="transaction-card-value" style="color: $paymentColor;">${fmtAmount(payment)}</span>
                </div>
                <div class="transaction-balance" style="color: $balanceColor;">Balance: $balanceText</div>
            </div>
        </div>
        """
    }.joinToString("")
    
    clientEventsTable.innerHTML = """
        <h3>Transactions</h3>
        
        <!-- Table for Tablet/Desktop -->
        <div class="transactions-table-container">
            <table class="transactions-table">
                <thead>
                    <tr>
                        <th>DATE</th>
                        <th>Event</th>
                        <th style="text-align: right;">QUANTITY</th>
                        <th>BILL. NO</th>
                        <th style="text-align: right;">SHIPMENT PRICE</th>
                        <th style="text-align: right;">PAYMENT</th>
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

fun addClientTransaction(clientId: Long) {
    openAddTransactionModal(clientId)
}

fun openAddTransactionModal(clientId: Long) {
    val modalHTML = """
        <div id="addTransactionModal" class="client-modal">
            <div class="client-modal-content">
                <div class="client-modal-header">
                    <h2>Add Transaction</h2>
                    <button id="closeTxModalBtn" class="client-modal-close">&times;</button>
                </div>
                <form id="addTransactionForm" class="client-form">
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="txDate" class="client-form-label">DATE *</label>
                            <input type="date" id="txDate" name="txDate" onkeydown="return false;" onpaste="return false;" ondrop="return false;" required class="client-form-input">
                        </div>
                        <div class="client-form-field">
                            <label for="txEvent" class="client-form-label">Event *</label>
                            <select id="txEvent" name="txEvent" required class="client-form-select">
                                <option value="">Select Event</option>
                            </select>
                        </div>
                    </div>
                    <div id="txQtyWrap" class="client-form-row">
                        <div class="client-form-field">
                            <label for="txQuantity" class="client-form-label">QUANTITY</label>
                            <input type="number" id="txQuantity" name="txQuantity" step="1" class="client-form-input">
                        </div>
                        <div class="client-form-field">
                            <label for="txBillNo" class="client-form-label">BILL. NO</label>
                            <input type="text" id="txBillNo" name="txBillNo" class="client-form-input">
                        </div>
                    </div>
                    <div id="txPriceWrap" class="client-form-row">
                        <div class="client-form-field">
                            <label for="txTransactionPrice" class="client-form-label">Total Shipment PRICE</label>
                            <input type="text" id="txTransactionPrice" name="txTransactionPrice" 
                                   placeholder="e.g. ¥5,000 or -¥5,000" class="client-form-input">
                        </div>
                        <div class="client-form-field">
                            <label for="txPaymentReceived" class="client-form-label">PAYMENT RECEIVED</label>
                            <input type="text" id="txPaymentReceived" name="txPaymentReceived" 
                                   placeholder="e.g. ¥10,000 or -¥10,000" class="client-form-input">
                        </div>
                    </div>
                    <div class="client-form-row">
                        <div class="client-form-field">
                            <label for="txRunningBalance" class="client-form-label">Total BALANCE</label>
                            <input type="text" id="txRunningBalance" name="txRunningBalance" 
                                   placeholder="Auto-calculated" readonly
                                   class="client-form-input" style="background-color: #f5f5f5;">
                        </div>
                        <div class="client-form-field">
                            <!-- Empty div for spacing -->
                        </div>
                    </div>
                    <div class="client-modal-actions">
                        <button type="button" id="cancelTxBtn" class="client-btn client-btn-secondary">Cancel</button>
                        <button type="submit" class="client-btn client-btn-primary">Save Transaction</button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Remove existing modal if any
    document.getElementById("addTransactionModal")?.remove()
    
    // Add modal to body
    document.body?.insertAdjacentHTML("beforeend", modalHTML)

    // Add event listeners for cancel and close buttons
    document.getElementById("cancelTxBtn")?.addEventListener("click", { _: Event ->
        closeAddTransactionModal()
    })
    
    document.getElementById("closeTxModalBtn")?.addEventListener("click", { _: Event ->
        closeAddTransactionModal()
    })
    
    // Close modal when clicking outside
    document.getElementById("addTransactionModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "addTransactionModal") {
            closeAddTransactionModal()
        }
    })

    // Populate event dropdown
    val events = arrayOf(
        "TT RECIEVED",
        "MSC BASIL-HI513A",
        "CAPTAIN THANASIS I-HG514A",
        "MSC MANHATTAN V-HI515A",
        "MSC GENERAL IV-HI516A",
        "MSC AUDREY-GS512S",
        "MSC PRECISION V HI517A",
        "TT RECIEVED(CASH 5-7)",
        "MSC FORTUNE F-XA518A",
        "CAPTHAIN THANASIS I-HG518A",
        "MAERSK VIRGINIA 520S",
        "VIRGO V.520W",
        "NAVIOS TEMPO V.521S"
    )
    val eventSelect = document.getElementById("txEvent") as? HTMLSelectElement
    eventSelect?.let {
        for (ev in events) {
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = ev
            opt.text = ev
            it.appendChild(opt)
        }
    }

    fun toggleByEvent(value: String) {
        val hide = value == "TT RECIEVED" || value == "TT RECIEVED(CASH 5-7)"
        (document.getElementById("txQtyWrap") as? HTMLElement)?.let {
            it.style.display = if (hide) "none" else ""
        }
        // Only hide the first part of txPriceWrap (Total Shipment PRICE), keep PAYMENT RECEIVED visible
        val txPriceWrap = document.getElementById("txPriceWrap") as? HTMLElement
        txPriceWrap?.let {
            val firstChild = it.firstElementChild as? HTMLElement
            firstChild?.style?.display = if (hide) "none" else ""
        }
    }
    eventSelect?.addEventListener("change", { _: Event ->
        toggleByEvent(eventSelect.value)
    })

    fun parseCurrency(input: String?): Double? {
        if (input == null) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed.replace(Regex("[^0-9.-]"), "")
        return cleaned.toDoubleOrNull()
    }

    document.getElementById("addTransactionForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        
        val dateIso = (document.getElementById("txDate") as? HTMLInputElement)?.value ?: ""
        val eventDesc = (document.getElementById("txEvent") as? HTMLSelectElement)?.value ?: ""
        val qtyStr = (document.getElementById("txQuantity") as? HTMLInputElement)?.value ?: ""
        val billNo = (document.getElementById("txBillNo") as? HTMLInputElement)?.value ?: ""
        val tPriceStr = (document.getElementById("txTransactionPrice") as? HTMLInputElement)?.value ?: ""
        val payStr = (document.getElementById("txPaymentReceived") as? HTMLInputElement)?.value ?: ""

        if (dateIso.isBlank() || eventDesc.isBlank()) {
            showMessage("Please fill DATE and Event.", "error")
            return@addEventListener
        }

        val quantity = qtyStr.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
        val transactionPrice = parseCurrency(tPriceStr) ?: 0.0
        val paymentReceived = parseCurrency(payStr) ?: 0.0

        // Send payload to backend transaction endpoint
        val payload = js("({})")
        payload["clientId"] = clientId
        payload["eventDate"] = dateIso
        payload["eventDescription"] = eventDesc
        payload["quantity"] = quantity
        payload["billNumber"] = if (billNo.trim().isEmpty()) null else billNo.trim()
        payload["transactionPrice"] = transactionPrice
        payload["paymentReceived"] = paymentReceived

        val req = js("({})")
        req.method = "POST"
        val headers = js("({})")
        headers["Content-Type"] = "application/json"
        req.headers = headers
        req.body = JSON.stringify(payload)

        Logger.debug("Sending transaction payload")

        window.fetch(apiUrl("clients/add-transaction"), req).then { response ->
            if (response.ok) {
                response.json().then { result ->
                    Logger.debug("Transaction response received")
                    showMessage("Transaction added successfully!", "success")
                    document.getElementById("addTransactionModal")?.remove()
                    // Optimistic UI update: insert row and update balance immediately
                    try {
                        val tx = result.asDynamic()
                        val running = (tx.runningBalance as Number?)?.toDouble()
                        if (running != null) {
                            val balanceColor = if (running < 0) "#e74c3c" else if (running > 0) "#27ae60" else "#666"
                            val balanceText = if (running < 0) "¥${kotlin.math.abs(running).toInt()}" else if (running > 0) "+¥${running.toInt()}" else "¥0"
                            val balEl = document.getElementById("currentBalanceValue")
                            balEl?.let {
                                it.asDynamic().style.color = balanceColor
                                it.textContent = balanceText
                            }
                        }
                        // Find the table tbody - try multiple selectors
                        var table = document.querySelector("#clientEventsTable tbody") as? HTMLElement
                        if (table == null) {
                            // Try finding table first, then tbody
                            val tableElement = document.querySelector("#clientEventsTable table") as? HTMLElement
                            table = tableElement?.querySelector("tbody") as? HTMLElement
                        }
                        if (table == null) {
                            // If still not found, reload the events to rebuild the table
                            loadClientEvents(clientId)
                        } else {
                            val row = document.createElement("tr")
                            row.setAttribute("style", "border-bottom: 1px solid #f1f3f4; transition: background-color 0.2s;")
                            row.setAttribute("onmouseover", "this.style.backgroundColor='#f8f9fa'")
                            row.setAttribute("onmouseout", "this.style.backgroundColor='white'")
                            val runningBalance = (result.asDynamic().runningBalance as? Number)?.toDouble() ?: 0.0
                            val qtyText = if (quantity != null) "${quantity} UNITS" else ""
                            val billNoText = if (billNo.trim().isEmpty()) "" else billNo.trim()
                            val tPriceColor = if (transactionPrice < 0) "#e74c3c" else if (transactionPrice > 0) "#27ae60" else "#666"
                            val paymentColor = if (paymentReceived < 0) "#e74c3c" else if (paymentReceived > 0) "#27ae60" else "#666"
                            val balanceColor = if (runningBalance < 0) "#e74c3c" else if (runningBalance > 0) "#27ae60" else "#666"
                            val balanceText = if (runningBalance < 0) "¥${kotlin.math.abs(runningBalance).toInt()}" else if (runningBalance > 0) "+¥${runningBalance.toInt()}" else "¥0"
                            
                            row.innerHTML = """
                                <td style="padding: 12px;">$dateIso</td>
                                <td style="padding: 12px;">$eventDesc</td>
                                <td style="padding: 12px; text-align: right;">$qtyText</td>
                                <td style="padding: 12px;">$billNoText</td>
                                <td style="padding: 12px; text-align: right; color: $tPriceColor;">¥${transactionPrice.toInt()}</td>
                                <td style="padding: 12px; text-align: right; color: $paymentColor;">¥${paymentReceived.toInt()}</td>
                                <td style="padding: 12px; text-align: right; color: $balanceColor; font-weight: bold;">$balanceText</td>
                            """
                            // Prepend new row to top of tbody (first row)
                            val first = table.firstChild
                            if (first != null) {
                                table.insertBefore(row, first)
                            } else {
                                table.appendChild(row)
                            }
                        }
                    } catch (e: dynamic) {
                        Logger.error("Error updating UI: ${e.toString()}")
                    }
                    // Also trigger a fresh reload shortly after to confirm state
                    window.setTimeout({
                        loadClientDetails(clientId)
                        loadClientEvents(clientId)
                    }, 300)
                }
            } else {
                response.text().then { errorText ->
                    Logger.error("Backend error: $errorText")
                    ErrorHandler.showError("Failed to add transaction: $errorText")
                }
            }
        }.catch { error ->
            val errorMsg = ErrorHandler.handleNetworkError(error, "clients/add-transaction")
            Logger.error("Network error: $errorMsg")
            ErrorHandler.showError("Failed to add transaction: $errorMsg")
        }
    })

    // Initialize visibility
    toggleByEvent("")
    
    // Add real-time calculation for Total BALANCE
    fun updateTotalBalance() {
        val transactionPrice = parseCurrency((document.getElementById("txTransactionPrice") as? HTMLInputElement)?.value) ?: 0.0
        val paymentReceived = parseCurrency((document.getElementById("txPaymentReceived") as? HTMLInputElement)?.value) ?: 0.0
        
        // For new transactions, start from 0 and calculate: payment - transaction
        val newBalance = paymentReceived - transactionPrice
        
        // Update the Total BALANCE field
        val balanceField = document.getElementById("txRunningBalance") as? HTMLInputElement
        balanceField?.value = "¥${newBalance.toInt()}"
    }
    
    // Add event listeners for real-time calculation
    document.getElementById("txTransactionPrice")?.addEventListener("input", { updateTotalBalance() })
    document.getElementById("txPaymentReceived")?.addEventListener("input", { updateTotalBalance() })
    
    // Initial calculation
    updateTotalBalance()
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

