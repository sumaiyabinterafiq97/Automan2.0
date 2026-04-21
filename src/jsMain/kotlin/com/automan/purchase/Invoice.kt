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

// Invoice Functions

fun showInvoicePage() {
    val content = document.getElementById("content") ?: return
    
    // Store current invoice purchases (will be populated when CLIENT + VESSEL are selected)
    js("window.currentInvoicePurchaseIds = []")
    js("window.currentInvoicePdfLines = []")
    js("window.currentInvoiceChassisList = []")
    
    // Check for URL parameters (ids) for pre-selected purchases
    val urlHash = window.location.hash
    val idsParam = if (urlHash.contains("?")) {
        val queryString = urlHash.substringAfter("?")
        val params = queryString.split("&")
        params.find { it.startsWith("ids=") }?.substringAfter("=")
    } else {
        null
    }
    
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
                    <h1>AUTOMAN | CREATE CUSTOMER INVOICE</h1>
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
                        </div>
                        
                        <div class="invoice-field">
                            <label for="invoiceShippingDate">SHIPPING DATE:</label>
                            <input type="date" id="invoiceShippingDate" onkeydown="return false;" onpaste="return false;" ondrop="return false;" class="invoice-input" placeholder="Select shipping date" />
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
                        
                        <div class="invoice-total" id="invoiceTotalAmount">
                            TOTAL AMOUNT: ¥000,000
                        </div>
                    </div>
                </div>
                
                <!-- Action Buttons -->
                <div class="invoice-actions">
                    <button type="button" id="cancelInvoiceBtn" class="invoice-btn invoice-btn-secondary">CANCEL</button>
                    <button type="button" id="downloadPdfBtn" class="invoice-btn invoice-btn-primary">CONFIRM AND DOWNLOAD PDF</button>
                    <button type="button" id="showFullPreviewBtn" class="invoice-btn invoice-btn-secondary">PREVIEW</button>
                </div>
            </div>
        </div>
    """

    // Setup event listeners
    setupInvoicePageListeners()
    
    // Load unique client_name values from client_map for CLIENT dropdown
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
    
    if (selectedIds.isNotEmpty()) {
        // Load purchases by IDs and auto-fill form
        window.setTimeout({
            loadPurchasesByIds(selectedIds)
        }, 500) // Delay to ensure DOM is ready
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

fun loadInvoiceClientOptions() {
    window.fetch(apiUrl("client-map/dropdowns/client-names"))
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
            val errorMsg = ErrorHandler.handleNetworkError(error, "client-map")
            Logger.error("Error loading client map names: $errorMsg")
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
    if (client.isBlank()) {
        onDone?.invoke()
        return
    }

    val encodedClient = js("encodeURIComponent")(client.trim()) as String
    window.fetch(apiUrl("shipping-history/for-invoice/vessels?clientName=$encodedClient"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load vessels')")
        }
        .then { payload: dynamic ->
            val namesRaw = js("payload && payload.success ? payload.data : []")
            val vessels = js("Array.isArray(namesRaw) ? namesRaw : []") as Array<dynamic>
            for (v in vessels) {
                val vessel = v.toString()
                if (vessel.isBlank()) continue
                val option = document.createElement("option") as HTMLOptionElement
                option.value = vessel
                option.textContent = vessel
                vesselSelect.appendChild(option)
            }

            val preferred = preferredVessel?.trim().orEmpty()
            if (preferred.isNotEmpty()) {
                val hasPreferred = (0 until vesselSelect.options.length).any { i ->
                    (vesselSelect.options.item(i) as? HTMLOptionElement)?.value == preferred
                }
                if (hasPreferred) vesselSelect.value = preferred
            }
            onDone?.invoke()
        }
        .catch { error: dynamic ->
            val errorMsg = ErrorHandler.handleNetworkError(error, "shipping-history")
            Logger.error("Error loading vessel options: $errorMsg")
            ErrorHandler.showError("Failed to load vessels: $errorMsg")
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
        loadInvoiceVesselOptionsForClient(client)
        handleInvoiceClientVesselChange()
    })
    
    document.getElementById("invoiceVessel")?.addEventListener("change", { _: Event ->
        handleInvoiceClientVesselChange()
    })
    
    // Cancel button
    document.getElementById("cancelInvoiceBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    // Confirm and download PDF (saves invoice_history, marks purchases confirmed, downloads PDF)
    document.getElementById("downloadPdfBtn")?.addEventListener("click", { _: Event ->
        handleConfirmAndDownloadPdf()
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

fun populateInvoiceListTable(purchases: Array<dynamic>) {
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
        emptyRow.innerHTML = """<td colspan="5" style="text-align:center; padding: 12px; color:#6c757d;">No Unshipped car available</td>"""
        tableBody.appendChild(emptyRow)
    }

    for (purchase in purchases) {
        val id = js("purchase.id")?.toString()?.toLongOrNull()
        if (id != null) {
            purchaseIds.add(id)
        }
        
        val chassis = js("purchase.chassis")?.toString() ?: "N/A"
        val carName = js("purchase.carName")?.toString() ?: "N/A"
        val year = js("purchase.carModelYear")?.toString() ?: "N/A"
        
        val amount = purchaseInvoiceLineAmountYenFromDynamic(purchase)
        totalAmount += amount
        
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
    syncWindowInvoicePdfLinesFromPurchases(purchases)
    syncWindowInvoiceChassisFromPurchases(purchases)
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
            """<td colspan="5" style="text-align:center; padding: 12px; color:#6c757d;">No cars selected</td>"""
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

    val shipmentDate = header.shipmentDate?.toString()?.trim() ?: ""
    if (shipmentDate.isNotEmpty()) {
        (document.getElementById("invoiceShippingDate") as? HTMLInputElement)?.value = shipmentDate
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

/**
 * Saves invoice_history, sets invoice_confirmed on listed purchases, downloads PDF.
 * Duplicate invoice number returns 409 from the server.
 */
fun handleConfirmAndDownloadPdf() {
    Logger.debug("Confirm and download PDF clicked for invoice")
    
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
            
            // Helper function to escape JSON strings
            fun escapeJsonString(str: String): String {
                return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
            }
            
            // Prepare request body - build JSON string manually to avoid Kotlin/JS interop issues
            val itemsJsonArray = itemsList.joinToString(",") { item ->
                val itemDynamic = item.unsafeCast<dynamic>()
                val unit = itemDynamic.unit?.toString() ?: "0"
                val description = escapeJsonString((itemDynamic.description as? String) ?: "")
                val amount = escapeJsonString((itemDynamic.amount as? String) ?: "")
                "{\"unit\":" + unit + ",\"description\":\"" + description + "\",\"amount\":\"" + amount + "\"}"
            }
            
            val lcNumberPart = if (invoiceLcNo.isNotEmpty()) {
                "\"" + escapeJsonString(invoiceLcNo) + "\""
            } else {
                "null"
            }
            val clientAddressPart = if (clientAddress != null) {
                "\"" + escapeJsonString(clientAddress) + "\""
            } else {
                "null"
            }
            val bankAccountPart = if (bankAccountValue.isNotEmpty()) {
                "\"" + escapeJsonString(bankAccountValue) + "\""
            } else {
                "null"
            }
            val messagePart = if (invoiceMessage.isNotEmpty()) {
                "\"" + escapeJsonString(invoiceMessage) + "\""
            } else {
                "null"
            }
            
            val requestBodyJson = "{\"invoiceNumber\":\"" + escapeJsonString(invoiceNumber) + 
                "\",\"invoiceDate\":\"" + escapeJsonString(invoiceDate) + 
                "\",\"lcNumber\":" + lcNumberPart + 
                ",\"clientName\":\"" + escapeJsonString(clientName) + 
                "\",\"clientAddress\":" + clientAddressPart + 
                ",\"vessel\":\"" + escapeJsonString(invoiceVessel) + 
                "\",\"shippingDate\":\"" + escapeJsonString(formattedShippingDate) + 
                "\",\"from\":\"" + escapeJsonString(invoiceFrom) + 
                "\",\"to\":\"" + escapeJsonString(invoiceTo) + 
                "\",\"priceType\":\"" + escapeJsonString(priceType) + 
                "\",\"items\":[" + itemsJsonArray + 
                "],\"totalAmount\":\"" + escapeJsonString(formattedTotal) + 
                "\",\"bankAccount\":" + bankAccountPart + 
                ",\"message\":" + messagePart + 
                "}"

            val chassisList = js("window.currentInvoiceChassisList || []") as Array<dynamic>
            val chassisJoinedRaw = chassisList.joinToString(";") { it?.toString()?.trim() ?: "" }
            val shippingDateIsoPart = if (invoiceShippingDate.isNotEmpty()) {
                "\"" + escapeJsonString(invoiceShippingDate) + "\""
            } else {
                "null"
            }
            val purchaseIdsCsv = selectedIds.joinToString(",")
            val wrappedBody =
                "{\"purchaseIds\":[" + purchaseIdsCsv + "],\"chassisJoined\":\"" +
                escapeJsonString(chassisJoinedRaw) + "\",\"shippingDateIso\":" + shippingDateIsoPart +
                ",\"pdf\":" + requestBodyJson + "}"

            Logger.debug("Sending invoice confirm-and-download request")
            
            val headers = Headers()
            headers.set("Content-Type", "application/json")
            
            val requestInit = RequestInit(
                method = "POST",
                headers = headers,
                body = wrappedBody
            )
            
            window.fetch(apiUrl("purchases/invoice/confirm-and-download"), requestInit).then { response ->
                val status = response.status.toInt()
                if (response.ok) {
                    response.blob().then { blob ->
                        val url = js("window.URL.createObjectURL(blob)") as String
                        val a = document.createElement("a") as HTMLAnchorElement
                        a.href = url
                        a.download = "invoice_" + invoiceNumber + ".pdf"
                        document.body?.appendChild(a)
                        a.click()
                        document.body?.removeChild(a)
                        window.setTimeout({
                            try {
                                js("URL.revokeObjectURL(url)")
                            } catch (e: dynamic) {
                                Logger.warn("Failed to revoke URL: ${e.toString()}")
                            }
                        }, 1000)
                        
                        Logger.debug("Invoice PDF downloaded successfully")
                        showMessage("Invoice saved and PDF downloaded successfully", "success")
                    }.catch { error ->
                        Logger.error("Error processing blob: ${error.toString()}")
                        showMessage("Error processing PDF: ${error.toString()}", "error")
                    }
                } else {
                    response.text().then { errorText ->
                        Logger.error("Invoice confirm-and-download failed: $errorText")
                        var userMsg = errorText
                        if (status == 409) {
                            try {
                                val parsed = JSON.parse<dynamic>(errorText)
                                val m = parsed.message?.toString()
                                if (m != null && m.isNotEmpty()) userMsg = m
                            } catch (_: dynamic) { }
                            showMessage(userMsg, "error")
                        } else {
                            showMessage("Failed to confirm invoice and download PDF: $errorText", "error")
                        }
                    }
                }
            }.catch { error ->
                Logger.error("Error confirming invoice / generating PDF: ${error.toString()}")
                showMessage("Error confirming invoice / generating PDF: ${error.toString()}", "error")
            }
        }.catch { error ->
            Logger.error("Error loading purchases: ${error.toString()}")
            showMessage("Error loading purchase details", "error")
        }
    }
}

fun handleShowFullPreview() {
    Logger.debug("[PREVIEW] handleShowFullPreview() called")
    
    // Get purchase IDs from global variable
    val selectedIds = (js("window.currentInvoicePurchaseIds") as? Array<dynamic>)?.mapNotNull { 
        js("it").toString().toLongOrNull() 
    } ?: emptyList()
    
    Logger.debug("[PREVIEW] Selected IDs: $selectedIds")
    
    if (selectedIds.isEmpty()) {
        Logger.warn("[PREVIEW] No purchases selected")
        showMessage("Please select purchases first", "warning")
        return
    }
    
    // Get form values
    val invoiceNumber = (document.getElementById("invoiceNumber") as? HTMLInputElement)?.value ?: ""
    val invoiceClient = (document.getElementById("invoiceClient") as? HTMLSelectElement)?.value ?: ""
    val invoiceVessel = (document.getElementById("invoiceVessel") as? HTMLSelectElement)?.value ?: ""
    val invoiceShippingDate = (document.getElementById("invoiceShippingDate") as? HTMLInputElement)?.value ?: ""
    val invoiceFrom = (document.getElementById("invoiceFrom") as? HTMLInputElement)?.value ?: ""
    val invoiceTo = (document.getElementById("invoiceTo") as? HTMLInputElement)?.value ?: ""
    val invoiceLcNo = (document.getElementById("invoiceLcNo") as? HTMLInputElement)?.value ?: ""
    val invoiceMessage = (document.getElementById("invoiceMessage") as? HTMLTextAreaElement)?.value ?: ""
    
    // Get price type (C&F or FOB)
    val invoiceCnf = document.getElementById("invoiceCnf") as? HTMLInputElement
    val invoiceFob = document.getElementById("invoiceFob") as? HTMLInputElement
    val priceType = when {
        invoiceCnf?.checked == true -> "C&F"
        invoiceFob?.checked == true -> "FOB"
        else -> "C&F"
    }
    
    // Get bank account
    val invoiceBankAccount = document.getElementById("invoiceBankAccount") as? HTMLSelectElement
    val bankAccountValue = invoiceBankAccount?.value ?: ""
    
    // Validation
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
            
            // Helper function to escape JSON strings
            fun escapeJsonString(str: String): String {
                return str.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
            }
            
            // Prepare request body - build JSON string manually to avoid Kotlin/JS interop issues
            val itemsJsonArray = itemsList.joinToString(",") { item ->
                val itemDynamic = item.unsafeCast<dynamic>()
                val unit = itemDynamic.unit?.toString() ?: "0"
                val description = escapeJsonString((itemDynamic.description as? String) ?: "")
                val amount = escapeJsonString((itemDynamic.amount as? String) ?: "")
                "{\"unit\":" + unit + ",\"description\":\"" + description + "\",\"amount\":\"" + amount + "\"}"
            }
            
            val lcNumberPart = if (invoiceLcNo.isNotEmpty()) {
                "\"" + escapeJsonString(invoiceLcNo) + "\""
            } else {
                "null"
            }
            val clientAddressPart = if (clientAddress != null) {
                "\"" + escapeJsonString(clientAddress) + "\""
            } else {
                "null"
            }
            val bankAccountPart = if (bankAccountValue.isNotEmpty()) {
                "\"" + escapeJsonString(bankAccountValue) + "\""
            } else {
                "null"
            }
            val messagePart = if (invoiceMessage.isNotEmpty()) {
                "\"" + escapeJsonString(invoiceMessage) + "\""
            } else {
                "null"
            }
            
            val requestBodyJson = "{\"invoiceNumber\":\"" + escapeJsonString(invoiceNumber) + 
                "\",\"invoiceDate\":\"" + escapeJsonString(invoiceDate) + 
                "\",\"lcNumber\":" + lcNumberPart + 
                ",\"clientName\":\"" + escapeJsonString(clientName) + 
                "\",\"clientAddress\":" + clientAddressPart + 
                ",\"vessel\":\"" + escapeJsonString(invoiceVessel) + 
                "\",\"shippingDate\":\"" + escapeJsonString(formattedShippingDate) + 
                "\",\"from\":\"" + escapeJsonString(invoiceFrom) + 
                "\",\"to\":\"" + escapeJsonString(invoiceTo) + 
                "\",\"priceType\":\"" + escapeJsonString(priceType) + 
                "\",\"items\":[" + itemsJsonArray + 
                "],\"totalAmount\":\"" + escapeJsonString(formattedTotal) + 
                "\",\"bankAccount\":" + bankAccountPart + 
                ",\"message\":" + messagePart + 
                "}"
            Logger.debug("Generating invoice PDF for preview")
            
            // Make API call to generate PDF
            val headers = Headers()
            headers.set("Content-Type", "application/json")
            
            val requestInit = RequestInit(
                method = "POST",
                headers = headers,
                body = requestBodyJson
            )
            
            window.fetch(apiUrl("purchases/invoice/generate-pdf"), requestInit).then { response ->
                if (response.ok) {
                    response.blob().then { blob ->
                        // Create object URL and open in new window for preview
                        val url = js("URL.createObjectURL(blob)") as String
                        var urlRevoked = false
                        
                        // Function to safely revoke URL
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
                        
                        try {
                            val newWindow = js("window.open(url, '_blank')")
                            if (newWindow == null) {
                                Logger.warn("Popup blocked, falling back to download")
                                // Fallback to download if popup is blocked
                                val a = document.createElement("a") as HTMLAnchorElement
                                a.href = url
                                a.download = "invoice_" + invoiceNumber + ".pdf"
                                document.body?.appendChild(a)
                                a.click()
                                document.body?.removeChild(a)
                                showMessage("Popup blocked. PDF downloaded instead.", "info")
                                // Revoke URL after download (short delay to ensure download starts)
                                window.setTimeout({ revokeUrl() }, 1000)
                            } else {
                                Logger.debug("Invoice PDF preview opened successfully")
                                showMessage("✅ Invoice PDF preview opened", "success")
                                // Revoke URL when window is closed or after delay
                                js("""
                                    if (newWindow) {
                                        newWindow.addEventListener('beforeunload', function() {
                                            URL.revokeObjectURL(url);
                                        });
                                    }
                                """)
                                // Fallback: revoke after 5 minutes if window still open
                                window.setTimeout({ revokeUrl() }, 300000)
                            }
                        } catch (e: dynamic) {
                            Logger.error("Error opening PDF: ${e.toString()}")
                            revokeUrl()
                            showMessage("Error opening PDF preview", "error")
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
        }.catch { error ->
            Logger.error("Error loading purchases: ${error.toString()}")
            showMessage("Error loading purchase details", "error")
        }
    }
}

