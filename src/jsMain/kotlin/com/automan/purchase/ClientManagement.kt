package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.models.ClientResponse
import com.automan.purchase.models.TransactionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Client Management Functions

fun showClientDetailsPage(clientId: Long) {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #e9ecef; border-radius: 4px; padding: 20px;">
            <div style="display:flex; justify-content: space-between; align-items:center; margin-bottom: 16px;">
                <h2 style="margin:0;">Client Details</h2>
                <div>
                    <button id="exportClientTxBtn" style="padding: 8px 14px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 8px;">Export Data</button>
                    <button id="backToClientsBtn" style="padding: 8px 14px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Back to Clients</button>
                </div>
            </div>
            <div id="clientDetailsContent"></div>
            <div id="clientEventsTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #666; padding: 20px;">
                    Loading transactions...
                </div>
            </div>
        </div>
    """
    document.getElementById("backToClientsBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/clients"
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
    val isMasterList = window.location.hash.startsWith("#/master/clients")
    val pageTitle = if (isMasterList) "Clients" else "Client Accounts Management"
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>$pageTitle</h2>
            <div style="margin-bottom: 20px;">
                <button id="addClientBtn" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Client</button>
                <button id="clientAlertsBtn" style="padding: 10px 20px; background-color: #ffc107; color: black; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">View Alerts</button>
                <button id="exportClientsBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Export Data</button>
            </div>
            
            
            <!-- Client Alerts Section -->
            <div id="clientAlertsSection" style="display: none; margin-bottom: 30px; border: 1px solid #ffc107; border-radius: 4px; padding: 20px; background-color: #fffbf0;">
                <h3 style="color: #856404; margin-top: 0;">Client Alerts</h3>
                <div id="clientAlertsTable">
                    <div style="text-align: center; color: #666; padding: 20px;">
                        Loading client alerts...
                    </div>
                </div>
            </div>
            
            <!-- Client Accounts Section -->
            <div id="clientAccountsSection">
                <div style="display: grid; grid-template-columns: 1fr; gap: 20px;">
                    <!-- Client List (Left Side) -->
                    <div style="border: 1px solid #e9ecef; border-radius: 4px; padding: 20px;">
                        <h3>Client List</h3>
                        <div style="margin-bottom: 15px;">
                            <input id="clientSearchInput" type="text" placeholder="Search clients..." 
                                   style="width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div id="clientListTable" style="max-height: 400px; overflow-y: auto;">
                            <div style="text-align: center; color: #666; padding: 20px;">
                                Loading clients...
                            </div>
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
        <div class="client-item" style="border: 1px solid #e9ecef; border-radius: 4px; padding: 15px; margin-bottom: 10px; transition: background-color 0.2s; $alertStyle" 
             onmouseover="this.style.backgroundColor='#f8f9fa'" onmouseout="this.style.backgroundColor='white'">
            <div style="display: flex; align-items: center; gap: 15px;">
                <!-- Edit Button -->
                <button onclick="event.stopPropagation(); window.editClientFromList(${client.id})" 
                        style="background: #007bff; border: none; border-radius: 50%; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s; box-shadow: 0 2px 4px rgba(0,0,0,0.1);"
                        onmouseover="this.style.background='#0056b3'; this.style.transform='scale(1.05)'" 
                        onmouseout="this.style.background='#007bff'; this.style.transform='scale(1)'"
                        title="Edit Client">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="white">
                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                    </svg>
                </button>
                
                <!-- Client Info (clickable for details) -->
                <div style="flex: 1; cursor: pointer;" onclick="window.location.hash='#/client/${client.id}'">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <div>
                            <div style="font-weight: bold; color: #333; margin-bottom: 5px;">$alertIcon ${client.clientName}</div>
                            <div style="font-size: 12px; color: #666;">#${client.clientNumber}</div>
                        </div>
                        <div style="text-align: right;">
                            <div style="font-weight: bold; color: $balanceColor;">$balanceText</div>
                            <div style="font-size: 12px; color: #666;">${client.status}</div>
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

// Function to edit client from the client list
fun editClientFromList(clientId: Long) {
    // Load client details and open edit modal
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<dynamic>("clients/$clientId")
        result.fold(
            onSuccess = { client ->
                showEditClientForm(client)
            },
            onError = { message, _ ->
                ErrorHandler.showError("Failed to load client details: $message")
            }
        )
    }
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
        <div style="margin-bottom: 20px;">
            <h4 style="margin: 0 0 10px 0; color: #333;">${client.clientName}</h4>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 15px;">
                <div><strong>Client #:</strong> ${client.clientNumber}</div>
                <div><strong>Status:</strong> ${client.status ?: "N/A"}</div>
                <div><strong>Phone:</strong> ${client.phone ?: "N/A"}</div>
                <div><strong>Currency:</strong> ${client.currency ?: "JPY"}</div>
            </div>
            <div style="background-color: #f8f9fa; padding: 15px; border-radius: 4px; margin-bottom: 15px;">
                <div style="text-align: center;">
                    <div id="currentBalanceValue" style="font-size: 24px; font-weight: bold; color: $balanceColor; margin-bottom: 5px;">$balanceText</div>
                    <div style="color: #666;">Current Balance</div>
                </div>
            </div>
            <div style="display: flex; gap: 10px; margin-bottom: 20px;">
                <button onclick="addClientTransaction(${client.id})" style="padding: 8px 16px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Add Transaction</button>
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
        <tr style="border-bottom: 1px solid #f1f3f4; transition: background-color 0.2s;" 
            onmouseover="this.style.backgroundColor='#f8f9fa'" 
            onmouseout="this.style.backgroundColor='white'">
            <td style="padding: 12px;">${event.eventDate}</td>
            <td style="padding: 12px;">${event.eventDescription}</td>
            <td style="padding: 12px; text-align: right;">$qtyText</td>
            <td style="padding: 12px;">${event.billNumber ?: ""}</td>
            <td style="padding: 12px; text-align: right; color: $tPriceColor;">${fmtAmount(tPrice)}</td>
            <td style="padding: 12px; text-align: right; color: $paymentColor;">${fmtAmount(payment)}</td>
            <td style="padding: 12px; text-align: right; color: $balanceColor; font-weight: bold;">$balanceText</td>
        </tr>
        """
    }.joinToString("")
    
    clientEventsTable.innerHTML = """
        <div style="overflow-x: auto;">
            <table style="width: 100%; border-collapse: collapse;">
                <thead>
                    <tr style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white;">
                        <th style="padding: 12px; text-align: left; border-bottom: 2px solid #764ba2; font-weight: 600;">DATE</th>
                        <th style="padding: 12px; text-align: left; border-bottom: 2px solid #764ba2; font-weight: 600;">Event</th>
                        <th style="padding: 12px; text-align: right; border-bottom: 2px solid #764ba2; font-weight: 600;">QUANTITY</th>
                        <th style="padding: 12px; text-align: left; border-bottom: 2px solid #764ba2; font-weight: 600;">BILL. NO</th>
                        <th style="padding: 12px; text-align: right; border-bottom: 2px solid #764ba2; font-weight: 600;">TOTAL SHIPMENT PRICE</th>
                        <th style="padding: 12px; text-align: right; border-bottom: 2px solid #764ba2; font-weight: 600;">PAYMENT RECEIVED</th>
                        <th style="padding: 12px; text-align: right; border-bottom: 2px solid #764ba2; font-weight: 600;">TOTAL BALANCE</th>
                    </tr>
                </thead>
                <tbody>
                    $rowsHtml
                </tbody>
            </table>
        </div>
    """
}

// Note: Additional client management functions (showAddClientForm, handleAddClientSubmit, etc.) 
// will be added in subsequent updates to keep this file manageable.
// Add New Client Modal Implementation

fun showAddClientForm() {
    val modalHTML = """
        <div id="addClientModal" class="modal" style="display: block; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">Add New Client</h2>
                    <span class="close" onclick="closeAddClientModal()" style="color: #aaa; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="addClientForm" style="display: flex; flex-direction: column; gap: 15px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="clientNumber" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Number *</label>
                            <input type="text" id="clientNumber" name="clientNumber" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., 128">
                        </div>
                        <div>
                            <label for="clientName" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Name *</label>
                            <input type="text" id="clientName" name="clientName" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., ABC COMPANY">
                        </div>
                    </div>
                    
                    <div>
                        <label for="address" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Address *</label>
                        <input type="text" id="address" name="address" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               placeholder="e.g., Tokyo, Japan">
                    </div>
                    
                    <div>
                        <label for="phone" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Phone *</label>
                        <input type="text" id="phone" name="phone" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               placeholder="e.g., +81-3-1234-5678">
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="currentBalance" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Initial Balance</label>
                            <input type="number" id="currentBalance" name="currentBalance" step="0.01" value="0.00"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="0.00">
                        </div>
                        <div>
                            <label for="creditLimit" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Credit Limit</label>
                            <input type="number" id="creditLimit" name="creditLimit" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., 50000000">
                        </div>
                        <div>
                            <label for="alertThreshold" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Alert Threshold</label>
                            <input type="number" id="alertThreshold" name="alertThreshold" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., 10000000">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="currency" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Currency</label>
                            <select id="currency" name="currency" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="JPY" selected>JPY (Japanese Yen)</option>
                                <option value="USD">USD (US Dollar)</option>
                                <option value="EUR">EUR (Euro)</option>
                            </select>
                        </div>
                        <div>
                            <label for="status" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Status</label>
                            <select id="status" name="status" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="ACTIVE" selected>Active</option>
                                <option value="SUSPENDED">Suspended</option>
                                <option value="CLOSED">Closed</option>
                            </select>
                        </div>
                    </div>
                    
                    <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;">
                        <button type="button" onclick="closeAddClientModal()" 
                                style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Cancel
                        </button>
                        <button type="submit" 
                                style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
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
}

fun handleAddClientSubmit() {
    val form = document.getElementById("addClientForm") as? HTMLFormElement
    if (form == null) return
    
    val clientData = js("{}")
    clientData["clientNumber"] = (document.getElementById("clientNumber") as? HTMLInputElement)?.value ?: ""
    clientData["clientName"] = (document.getElementById("clientName") as? HTMLInputElement)?.value ?: ""
    clientData["address"] = (document.getElementById("address") as? HTMLInputElement)?.value ?: ""
    clientData["phone"] = (document.getElementById("phone") as? HTMLInputElement)?.value ?: ""
    clientData["currentBalance"] = (document.getElementById("currentBalance") as? HTMLInputElement)?.value?.toDoubleOrNull() ?: 0.0
    clientData["creditLimit"] = (document.getElementById("creditLimit") as? HTMLInputElement)?.value?.toDoubleOrNull()
    clientData["alertThreshold"] = (document.getElementById("alertThreshold") as? HTMLInputElement)?.value?.toDoubleOrNull()
    clientData["currency"] = (document.getElementById("currency") as? HTMLSelectElement)?.value ?: "JPY"
    clientData["status"] = (document.getElementById("status") as? HTMLSelectElement)?.value ?: "ACTIVE"
    
    // Validate required fields
    if (clientData["clientNumber"] == "" || clientData["clientName"] == "" || clientData["address"] == "" || clientData["phone"] == "") {
        showMessage("Please fill in all required fields", "error")
        return
    }
    
    // Show loading state
    val submitBtn = document.querySelector("#addClientForm button[type='submit']") as? HTMLButtonElement
    submitBtn?.let {
        it.disabled = true
        it.textContent = "Adding..."
    }
    
    // Submit to backend
    val requestOptions = js("{}")
    requestOptions["method"] = "POST"
    requestOptions["headers"] = js("{\"Content-Type\": \"application/json\"}")
    requestOptions["body"] = JSON.stringify(clientData)
    
    window.fetch(apiUrl("clients"), requestOptions)
    .then { response ->
        if (response.ok) {
            showMessage("Client added successfully!", "success")
            closeAddClientModal()
            showClientAccountsPage() // Refresh the client list
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

fun editClient(clientId: Long) {
    window.fetch(apiUrl("clients/$clientId"))
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    showEditClientForm(client)
                }
            } else {
                showMessage("Failed to load client data", "error")
            }
        }
        .catch { error ->
            showMessage("Error loading client: $error", "error")
        }
}

fun showEditClientForm(client: dynamic) {
    val clientId = (client.id as Number).toLong()
    val originalBalance = (client.currentBalance as Number).toDouble()
    val creditLimit = (client.creditLimit as Number?)?.toDouble()
    val alertThreshold = (client.alertThreshold as Number?)?.toDouble()
    
    val modalHTML = """
        <div id="editClientModal" class="modal" style="display: block; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">Edit Client</h2>
                    <span class="close" onclick="closeEditClientModal()" style="color: #aaa; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="editClientForm" style="display: flex; flex-direction: column; gap: 15px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="editClientNumber" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Number *</label>
                            <input type="text" id="editClientNumber" name="editClientNumber" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${client.clientNumber}">
                        </div>
                        <div>
                            <label for="editClientName" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Name *</label>
                            <input type="text" id="editClientName" name="editClientName" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${client.clientName}">
                        </div>
                    </div>
                    
                    <div>
                        <label for="editAddress" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Address *</label>
                        <input type="text" id="editAddress" name="editAddress" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               value="${client.address ?: ""}">
                    </div>
                    
                    <div>
                        <label for="editPhone" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Phone *</label>
                        <input type="text" id="editPhone" name="editPhone" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               value="${client.phone ?: ""}">
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="editCurrentBalance" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Current Balance</label>
                            <input type="number" id="editCurrentBalance" name="editCurrentBalance" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="$originalBalance"
                                   data-original-balance="$originalBalance">
                        </div>
                        <div>
                            <label for="editCreditLimit" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Credit Limit</label>
                            <input type="number" id="editCreditLimit" name="editCreditLimit" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${creditLimit ?: ""}">
                        </div>
                        <div>
                            <label for="editAlertThreshold" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Alert Threshold</label>
                            <input type="number" id="editAlertThreshold" name="editAlertThreshold" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${alertThreshold ?: ""}">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="editCurrency" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Currency</label>
                            <select id="editCurrency" name="editCurrency" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="JPY" ${if (client.currency == "JPY") "selected" else ""}>JPY (Japanese Yen)</option>
                                <option value="USD" ${if (client.currency == "USD") "selected" else ""}>USD (US Dollar)</option>
                                <option value="EUR" ${if (client.currency == "EUR") "selected" else ""}>EUR (Euro)</option>
                            </select>
                        </div>
                        <div>
                            <label for="editStatus" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Status</label>
                            <select id="editStatus" name="editStatus" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="ACTIVE" ${if (client.status == "ACTIVE") "selected" else ""}>Active</option>
                                <option value="SUSPENDED" ${if (client.status == "SUSPENDED") "selected" else ""}>Suspended</option>
                                <option value="CLOSED" ${if (client.status == "CLOSED") "selected" else ""}>Closed</option>
                            </select>
                        </div>
                    </div>
                    
                    <div style="display: flex; justify-content: space-between; gap: 10px; margin-top: 20px;">
                        <button type="button" onclick="deleteClientFromModal($clientId)" 
                                style="padding: 10px 20px; background-color: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Delete
                        </button>
                        <div style="display: flex; gap: 10px;">
                            <button type="button" onclick="closeEditClientModal()" 
                                    style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">
                                Cancel
                            </button>
                            <button type="submit" 
                                    style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                                Update Client
                            </button>
                        </div>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Remove existing modal if any
    document.getElementById("editClientModal")?.remove()
    
    // Add modal to body
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Add form submission handler
    document.getElementById("editClientForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        handleEditClientSubmit(clientId)
    })
}

fun handleEditClientSubmit(clientId: Long) {
    val form = document.getElementById("editClientForm") as? HTMLFormElement
    if (form == null) return
    
    val balanceInput = document.getElementById("editCurrentBalance") as? HTMLInputElement
    val originalBalance = balanceInput?.getAttribute("data-original-balance")?.toDoubleOrNull() ?: 0.0
    val newBalance = balanceInput?.value?.toDoubleOrNull() ?: 0.0
    val balanceChanged = kotlin.math.abs(originalBalance - newBalance) > 0.01
    
    val clientData = js("{}")
    clientData["clientNumber"] = (document.getElementById("editClientNumber") as? HTMLInputElement)?.value ?: ""
    clientData["clientName"] = (document.getElementById("editClientName") as? HTMLInputElement)?.value ?: ""
    clientData["address"] = (document.getElementById("editAddress") as? HTMLInputElement)?.value ?: ""
    clientData["phone"] = (document.getElementById("editPhone") as? HTMLInputElement)?.value ?: ""
    clientData["creditLimit"] = (document.getElementById("editCreditLimit") as? HTMLInputElement)?.value?.toDoubleOrNull()
    clientData["alertThreshold"] = (document.getElementById("editAlertThreshold") as? HTMLInputElement)?.value?.toDoubleOrNull()
    clientData["currency"] = (document.getElementById("editCurrency") as? HTMLSelectElement)?.value ?: "JPY"
    clientData["status"] = (document.getElementById("editStatus") as? HTMLSelectElement)?.value ?: "ACTIVE"
    
    // Validate required fields
    if (clientData["clientNumber"] == "" || clientData["clientName"] == "" || clientData["address"] == "" || clientData["phone"] == "") {
        showMessage("Please fill in all required fields", "error")
        return
    }
    
    // Show loading state
    val submitBtn = document.querySelector("#editClientForm button[type='submit']") as? HTMLButtonElement
    submitBtn?.let {
        it.disabled = true
        it.textContent = "Updating..."
    }
    
    // Submit to backend - first update client
    val requestOptions = js("({})")
    requestOptions["method"] = "PUT"
    val headers = js("({})")
    headers["Content-Type"] = "application/json"
    requestOptions["headers"] = headers
    requestOptions["body"] = JSON.stringify(clientData)
    
    window.fetch(apiUrl("clients/$clientId"), requestOptions)
    .then { response ->
        if (response.ok) {
            // If balance changed, update balance separately
            if (balanceChanged) {
                val balanceRequest = js("({})")
                balanceRequest["method"] = "PUT"
                val balanceHeaders = js("({})")
                balanceHeaders["Content-Type"] = "application/json"
                balanceRequest["headers"] = balanceHeaders
                val balanceBody = js("({})")
                balanceBody["balance"] = newBalance
                balanceRequest["body"] = JSON.stringify(balanceBody)
                
                window.fetch(apiUrl("clients/$clientId/balance"), balanceRequest)
                .then { balanceResponse ->
                    if (balanceResponse.ok) {
                        showMessage("Client updated successfully!", "success")
                        closeEditClientModal()
                        showClientAccountsPage() // Refresh the client list
                    } else {
                        balanceResponse.text().then { errorText ->
                            showMessage("Failed to update balance: $errorText", "error")
                        }
                    }
                }
                .catch { error ->
                    showMessage("Error updating balance: $error", "error")
                }
                .finally {
                    submitBtn?.let {
                        it.disabled = false
                        it.textContent = "Update Client"
                    }
                }
            } else {
                showMessage("Client updated successfully!", "success")
                closeEditClientModal()
                showClientAccountsPage() // Refresh the client list
                submitBtn?.let {
                    it.disabled = false
                    it.textContent = "Update Client"
                }
            }
        } else {
            response.text().then { errorText ->
                showMessage("Failed to update client: $errorText", "error")
            }
            submitBtn?.let {
                it.disabled = false
                it.textContent = "Update Client"
            }
        }
    }
    .catch { error ->
        showMessage("Error updating client: $error", "error")
        submitBtn?.let {
            it.disabled = false
            it.textContent = "Update Client"
        }
    }
}

@JsName("closeEditClientModalFromClientManagement")
fun closeEditClientModal() {
    document.getElementById("editClientModal")?.remove()
}

@JsName("deleteClientFromModalFromClientManagement")
fun deleteClientFromModal(clientId: Long) {
    if (!window.confirm("Are you sure you want to delete this client? This action cannot be undone.")) {
        return
    }
    
    val requestOptions = js("({})")
    requestOptions["method"] = "DELETE"
    
    window.fetch(apiUrl("clients/$clientId"), requestOptions)
    .then { response ->
        if (response.ok) {
            showMessage("Client deleted successfully!", "success")
            closeEditClientModal()
            showClientAccountsPage() // Refresh the client list
        } else {
            response.text().then { errorText ->
                showMessage("Failed to delete client: $errorText", "error")
            }
        }
    }
    .catch { error ->
        showMessage("Error deleting client: $error", "error")
    }
}

fun addClientTransaction(clientId: Long) {
    openAddTransactionModal(clientId)
}

fun openAddTransactionModal(clientId: Long) {
    val modalHTML = """
        <div id="addTransactionModal" class="modal" style="display: block; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">Add Transaction</h2>
                    <span id="closeTxModalBtn" class="close" style="color: #aaa; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="addTransactionForm" style="display: flex; flex-direction: column; gap: 15px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txDate" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">DATE *</label>
                            <input type="date" id="txDate" name="txDate" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div>
                            <label for="txEvent" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Event *</label>
                            <select id="txEvent" name="txEvent" required 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="">Select Event</option>
                            </select>
                        </div>
                    </div>
                    <div id="txQtyWrap" style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txQuantity" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">QUANTITY</label>
                            <input type="number" id="txQuantity" name="txQuantity" step="1" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div>
                            <label for="txBillNo" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">BILL. NO</label>
                            <input type="text" id="txBillNo" name="txBillNo" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                    </div>
                    <div id="txPriceWrap" style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txTransactionPrice" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Total Shipment PRICE</label>
                            <input type="text" id="txTransactionPrice" name="txTransactionPrice" 
                                   placeholder="e.g. ¥5,000 or -¥5,000"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div>
                            <label for="txPaymentReceived" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">PAYMENT RECEIVED</label>
                            <input type="text" id="txPaymentReceived" name="txPaymentReceived" 
                                   placeholder="e.g. ¥10,000 or -¥10,000"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txRunningBalance" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Total BALANCE</label>
                            <input type="text" id="txRunningBalance" name="txRunningBalance" 
                                   placeholder="Auto-calculated by system"
                                   readonly
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; background-color: #f5f5f5;">
                        </div>
                        <div>
                            <!-- Empty div for spacing -->
                        </div>
                    </div>
                    <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;">
                        <button type="button" id="cancelTxBtn" 
                                style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Cancel</button>
                        <button type="submit" 
                                style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Save Transaction</button>
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
            it.style.display = if (hide) "none" else "grid"
        }
        // Only hide the first part of txPriceWrap (Total Shipment PRICE), keep PAYMENT RECEIVED visible
        val txPriceWrap = document.getElementById("txPriceWrap") as? HTMLElement
        txPriceWrap?.let {
            val firstChild = it.firstElementChild as? HTMLElement
            firstChild?.style?.display = if (hide) "none" else "block"
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
    val headers = listOf("clientNumber", "clientName", "address", "phone", "currentBalance", "creditLimit", "alertThreshold", "currency", "status")
    val csvRows = mutableListOf<String>()
    
    // Add header row
    csvRows.add(headers.joinToString(","))
    
    // Add data rows
    clientsArray.forEach { client ->
        val row = headers.map { header ->
            val value = when (header) {
                "clientNumber" -> client.clientNumber?.toString() ?: ""
                "clientName" -> client.clientName?.toString() ?: ""
                "address" -> client.address?.toString() ?: ""
                "phone" -> client.phone?.toString() ?: ""
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

