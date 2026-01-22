package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.ApiClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Purchase Management Functions

// Global pagination variables
var currentPage = 1
var itemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allPurchases: Array<dynamic> = emptyArray()

fun showPurchaseList() {
    window.location.hash = "#/purchase"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        
        <div id="purchaseList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0;">Purchase List</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="columnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            <div id="purchaseTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">
                    Loading purchases...
                </div>
            </div>
        </div>
    """
    // Ensure no stale selections carry over between navigations
    try {
        selectedPurchases.clear()
        updateRixoButtonVisibility()
    } catch (e: dynamic) {
        Logger.error("Error clearing selected purchases: ${e.toString()}")
    }
    
    // Hamburger button listener is set up by ensureSidebarPresent()
    
    // Add column filter button event listener
    document.getElementById("columnFilterBtn")?.addEventListener("click", { _: Event ->
        showColumnFilterModal()
    })
    
    // Add quick access to client accounts
    document.getElementById("clientAccountsQuickBtn")?.addEventListener("click", { _: Event ->
        showClientAccountsPage()
    })
    
    // Apply role-based restrictions
    applyRoleBasedRestrictions()
    
    // Ensure sidebar and hamburger button are present
    ensureSidebarPresent()
    
    loadPurchases()
}

fun loadPurchases() {
    Logger.debug("loadPurchases function called")
    val endpoint = if (currentSortField != null) {
        "purchases/sort?field=$currentSortField&order=$currentSortOrder"
    } else {
        "purchases"
    }
    
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<Array<dynamic>>(endpoint)
        result.fold(
            onSuccess = { purchases ->
                Logger.debug("Purchases data received: ${purchases.size} items")
                displayPurchases(purchases)
            },
            onError = { message, status ->
                Logger.error("API call failed: $message (status: $status)")
                ErrorHandler.showError("Failed to load purchases: $message")
            }
        )
    }
}

fun displayPurchases(purchases: dynamic) {
    val table = document.getElementById("purchaseTable")!!
    
    if (js("purchases.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data.
            </div>
        """
        return
    }
    
    // Convert to array and sort by ID descending (newest first) if no custom sort is applied
    val purchasesArray = purchases as Array<dynamic>
    val sortedPurchases = if (currentSortField == null) {
        // Sort by ID descending (newest first) when no custom sort is active
        purchasesArray.sortedByDescending { purchase ->
            val id = purchase.id
            when {
                id is Number -> id.toDouble()
                id is String -> id.toDoubleOrNull() ?: 0.0
                else -> {
                    val idStr = id?.toString() ?: "0"
                    idStr.toDoubleOrNull() ?: 0.0
                }
            }
        }
    } else {
        // Use purchases as-is when custom sort is active
        purchasesArray.toList()
    }
    
    // Store all purchases globally for pagination
    allPurchases = sortedPurchases.toTypedArray()
    currentPage = 1 // Reset to first page when new data is loaded
    
    displayPurchasesWithPagination()
}

fun displayPurchasesWithPagination() {
    val table = document.getElementById("purchaseTable")!!
    
    if (allPurchases.isEmpty()) {
        // Show table headers even when no data, so users can access filters
        val selectedColumns = getSelectedColumns()
        val sortableFields = setOf("chassis", "carName", "auctionHouse", "stockLocation", "rixoCompany", "country", "clientName", "brand", "repairCompany")
        val columnLabels = mapOf(
            "date" to "Purchase Date",
            "chassis" to "Chassis",
            "carName" to "Car Name",
            "auctionHouse" to "Supplier Name",
            "stockLocation" to "Stock Location",
            "clientName" to "Client Name",
            "rixoCompany" to "Rixo Company",
            "price" to "Car Price",
            "carModelYear" to "Production Date",
            "brand" to "Brand",
            "grade" to "Grade",
            "rank" to "Rank",
            "color" to "Color",
            "displacement" to "Displacement",
            "fuel" to "Fuel",
            "seat" to "Seat",
            "door" to "Door",
            "distance" to "Distance",
            "options" to "Options",
            "auctionNo" to "Auction No",
            "rixoPrice" to "Rixo Price",
            "venueId" to "Venue ID",
            "shipmentSize" to "Shipment Size"
        )
        
        val tableHTML = StringBuilder()
        tableHTML.append("<table class='table table-striped'>")
        tableHTML.append("<thead><tr>")
        
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            if (columnKey == "date") {
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative;">
                        <div style="display: flex; align-items: center; justify-content: space-between; cursor: pointer;" class="date-filter-header" data-field="$columnKey">
                            <span>$label</span>
                            <span style="margin-left: 8px;">📅</span>
                        </div>
                        <div class="date-filter-menu" data-field="$columnKey" style="display: none; position: absolute; top: 100%; left: 0; background: white; border: 1px solid #ddd; border-radius: 4px; padding: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); z-index: 1000; min-width: 200px;">
                            <label style="display: block; margin-bottom: 5px; font-size: 12px; color: #666;">Filter by Date</label>
                            <input type="date" id="dateFilterInput" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 3px; font-size: 12px; margin-bottom: 8px;">
                            <div style="display: flex; gap: 5px; margin-bottom: 8px;">
                                <button id="applyDateFilter" style="padding: 6px 12px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Apply</button>
                                <button id="clearDateFilter" style="padding: 6px 12px; background: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Clear</button>
                            </div>
                            <div style="border-top: 1px solid #ddd; padding-top: 8px; margin-top: 8px;">
                                <div style="font-size: 12px; color: #666; margin-bottom: 5px;">Sort by Date:</div>
                                <div class="date-sort-option" data-sort="asc" style="padding: 6px 8px; cursor: pointer; font-size: 12px; border-radius: 3px; background-color: #f8f9fa;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='#f8f9fa'">Ascending</div>
                                <div class="date-sort-option" data-sort="desc" style="padding: 6px 8px; cursor: pointer; font-size: 12px; border-radius: 3px; background-color: #f8f9fa; margin-top: 4px;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='#f8f9fa'">Descending</div>
                            </div>
                        </div>
                    </th>
                """)
            } else if (sortableFields.contains(columnKey)) {
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative;">
                        <div style="display: flex; align-items: center; justify-content: space-between; cursor: pointer;" class="sortable-header" data-field="$columnKey">
                            <span>$label</span>
                            <span style="margin-left: 8px;">↕</span>
                        </div>
                        <div class="sort-menu" data-field="$columnKey" style="display: none; position: absolute; top: 100%; left: 0; background: white; border: 1px solid #ddd; border-radius: 4px; padding: 5px 0; box-shadow: 0 2px 8px rgba(0,0,0,0.1); z-index: 1000; min-width: 120px;">
                            <div class="sort-option" data-field="$columnKey" data-order="asc" style="padding: 8px 12px; cursor: pointer; font-size: 12px;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='transparent'">Ascending</div>
                            <div class="sort-option" data-field="$columnKey" data-order="desc" style="padding: 8px 12px; cursor: pointer; font-size: 12px;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='transparent'">Descending</div>
                        </div>
                    </th>
                """)
            } else {
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">$label</th>
                """)
            }
        }
        
        tableHTML.append("</tr></thead>")
        tableHTML.append("<tbody><tr><td colspan=\"${selectedColumns.size + 2}\" style=\"text-align: center; padding: 40px; color: #666;\">No purchases found.</td></tr></tbody>")
        tableHTML.append("</table>")
        
        table.innerHTML = tableHTML.toString()
        setupColumnSorting()
        setupSortableHeaders()
        setupDateFilter()
        return
    }
    
    // Calculate pagination
    val totalPages = kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt()
    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = kotlin.math.min(startIndex + itemsPerPage, allPurchases.size)
    val paginatedPurchases = allPurchases.sliceArray(startIndex until endIndex)
    
    // Build table HTML
    val selectedColumns = getSelectedColumns()
    val sortableFields = setOf("chassis", "carName", "auctionHouse", "stockLocation", "rixoCompany", "country", "clientName", "brand", "repairCompany")
    val columnLabels = mapOf(
        "date" to "Purchase Date",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "auctionHouse" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "clientName" to "Client Name",
        "rixoCompany" to "Rixo Company",
        "price" to "Car Price",
        "carModelYear" to "Production Date",
        "brand" to "Brand",
        "grade" to "Grade",
        "rank" to "Rank",
        "color" to "Color",
        "displacement" to "Displacement",
        "fuel" to "Fuel",
        "seat" to "Seat",
        "door" to "Door",
        "distance" to "Distance",
        "options" to "Options",
        "auctionNo" to "Auction No",
        "country" to "Target Country",
        "auctionFee" to "Auction Fee",
        "recycleFee" to "Recycle Fee",
        "roadTax" to "Road Tax",
        "totalPrice" to "Total Price",
        "paymentDate" to "Payment Date",
        "rixoRequested" to "Rixo Requested",
        "rixoConfirmed" to "Rixo Confirmed",
        "rixoPrice" to "Rixo Price",
        "shipmentDate" to "Shipment Date",
        "blNo" to "BL No",
        "vesselNo" to "Vessel No",
        "destination" to "Destination",
        "shipmentCharges" to "Shipment Charges",
        "freight" to "Freight",
        "storageCharges" to "Storage Charges",
        "miscCharges" to "Misc Charges",
        "inspectionFee" to "Inspection Fee",
        "commission" to "Commission",
        "repairCompany" to "Repair Company",
        "repairCharges" to "Repair Charges",
        "venueId" to "Venue ID",
        "shipmentSize" to "Shipment Size",
        "numberCut" to "Number Cut",
        "taxTotal" to "Tax Total",
        "profit" to "Profit",
        "packagePrice" to "Package Price",
        "bookingId" to "Booking ID",
        "notes" to "Notes"
    )
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <table style="width: 100%; border-collapse: collapse;">
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
    """)
    
    for (columnKey in selectedColumns) {
        val label = columnLabels[columnKey] ?: columnKey
        if (columnKey == "date") {
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative;">
                    <div style="display: flex; align-items: center; justify-content: space-between; cursor: pointer;" class="date-filter-header" data-field="$columnKey">
                        <span>$label</span>
                        <span style="margin-left: 8px;">📅</span>
                    </div>
                    <div class="date-filter-menu" data-field="$columnKey" style="display: none; position: absolute; top: 100%; left: 0; background: white; border: 1px solid #ddd; border-radius: 4px; padding: 10px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); z-index: 1000; min-width: 200px;">
                        <label style="display: block; margin-bottom: 5px; font-size: 12px; color: #666;">Filter by Date</label>
                        <input type="date" id="dateFilterInput" style="width: 100%; padding: 6px; border: 1px solid #ddd; border-radius: 3px; font-size: 12px; margin-bottom: 8px;">
                        <div style="display: flex; gap: 5px; margin-bottom: 8px;">
                            <button id="applyDateFilter" style="padding: 6px 12px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Apply</button>
                            <button id="clearDateFilter" style="padding: 6px 12px; background: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Clear</button>
                        </div>
                        <div style="border-top: 1px solid #ddd; padding-top: 8px; margin-top: 8px;">
                            <div style="font-size: 12px; color: #666; margin-bottom: 5px;">Sort by Date:</div>
                            <div class="date-sort-option" data-sort="asc" style="padding: 6px 8px; cursor: pointer; font-size: 12px; border-radius: 3px; background-color: #f8f9fa;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='#f8f9fa'">Ascending</div>
                            <div class="date-sort-option" data-sort="desc" style="padding: 6px 8px; cursor: pointer; font-size: 12px; border-radius: 3px; background-color: #f8f9fa; margin-top: 4px;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='#f8f9fa'">Descending</div>
                        </div>
                    </div>
                </th>
            """)
        } else if (sortableFields.contains(columnKey)) {
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative;">
                    <div style="display: flex; align-items: center; justify-content: space-between; cursor: pointer;" class="sortable-header" data-field="$columnKey">
                        <span>$label</span>
                        <span style="margin-left: 8px;">↕</span>
                    </div>
                    <div class="sort-menu" data-field="$columnKey" style="display: none; position: absolute; top: 100%; left: 0; background: white; border: 1px solid #ddd; border-radius: 4px; padding: 5px 0; box-shadow: 0 2px 8px rgba(0,0,0,0.1); z-index: 1000; min-width: 120px;">
                        <div class="sort-option" data-field="$columnKey" data-order="asc" style="padding: 8px 12px; cursor: pointer; font-size: 12px;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='transparent'">Ascending</div>
                        <div class="sort-option" data-field="$columnKey" data-order="desc" style="padding: 8px 12px; cursor: pointer; font-size: 12px;" onmouseover="this.style.backgroundColor='#e9ecef'" onmouseout="this.style.backgroundColor='transparent'">Descending</div>
                    </div>
                </th>
            """)
        } else {
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">$label</th>
            """)
        }
    }
    
        tableHTML.append("""
                </tr>
            </thead>
            <tbody>
    """)
    
    for (purchase in paginatedPurchases) {
        val id = purchase.id ?: ""
        val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
        // Safely handle date - could be null, empty string, or other types
        val dateValue = try {
            val dateStr = purchase.date?.toString() ?: ""
            if (dateStr.isBlank()) "" else formatWithWeekday(dateStr)
        } catch (e: dynamic) {
            ""
        }
        val date = dateValue
        val chassis = purchase.chassis ?: ""
        val carName = purchase.carName ?: ""
        val auctionHouse = purchase.auctionHouse ?: ""
        val stockLocation = purchase.stockLocation ?: ""
        val clientName = purchase.clientName ?: ""
        val rixoCompany = purchase.rixoCompany ?: ""
        val priceStr = (purchase.price as? String) ?: ""
        val carModelYear = purchase.carModelYear ?: ""
        val brand = purchase.brand ?: ""
        val grade = purchase.grade ?: ""
        val rank = purchase.rank ?: ""
        val color = purchase.color ?: ""
        val displacement = purchase.displacement ?: ""
        val fuel = purchase.fuel ?: ""
        val seat = purchase.seat ?: ""
        val door = purchase.door ?: ""
        val distance = purchase.distance ?: ""
        val options = purchase.options ?: ""
        val auctionNo = purchase.auctionNo ?: ""
        val rixoPrice = purchase.rixoPrice ?: 0
        val venueId = purchase.venueId ?: ""
        val shipmentSize = purchase.shipmentSize ?: ""
        
        tableHTML.append("""
            <tr style="border-bottom: 1px solid #f0f0f0;">
                <td style="padding: 8px 12px;">
                    ${if (isEditor() && purchaseId > 0L) """
                    <button class="edit-btn" data-id="${purchaseId}" aria-label="Edit" title="Edit"
                            style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    """ else if (isEditor()) """
                    <span style="color: #ccc; font-size: 12px;">Invalid ID</span>
                    """ else """
                    """}
                </td>
        """)
        
        for (columnKey in selectedColumns) {
            val cellValue = when (columnKey) {
                "date" -> date
                "chassis" -> chassis
                "carName" -> carName
                "auctionHouse" -> auctionHouse
                "stockLocation" -> stockLocation
                "clientName" -> clientName
                "rixoCompany" -> rixoCompany
                "price" -> {
                    val priceValue = parseCurrency(priceStr)
                    if (priceValue > 0.0) formatCurrency(priceValue) else ""
                }
                "carModelYear" -> carModelYear.toString()
                "brand" -> brand
                "grade" -> grade
                "rank" -> rank
                "color" -> color
                "displacement" -> displacement
                "fuel" -> fuel
                "seat" -> seat
                "door" -> door
                "distance" -> distance
                "options" -> options
                "auctionNo" -> auctionNo
                "country" -> (purchase.country as? String) ?: ""
                "auctionFee" -> (purchase.auctionFee as? String) ?: ""
                "recycleFee" -> (purchase.recycleFee as? String) ?: ""
                "roadTax" -> (purchase.roadTax as? String) ?: ""
                "totalPrice" -> (purchase.totalPrice as? String) ?: ""
                "paymentDate" -> (purchase.paymentDate as? String) ?: ""
                "rixoRequested" -> (purchase.rixoRequested as? String) ?: ""
                "rixoConfirmed" -> (purchase.rixoConfirmed as? String) ?: ""
                "rixoPrice" -> formatCurrency((rixoPrice as? Number)?.toDouble() ?: 0.0)
                "shipmentDate" -> (purchase.shipmentDate as? String) ?: ""
                "blNo" -> (purchase.blNo as? String) ?: ""
                "vesselNo" -> (purchase.vesselNo as? String) ?: ""
                "destination" -> (purchase.destination as? String) ?: ""
                "shipmentCharges" -> (purchase.shipmentCharges as? String) ?: ""
                "freight" -> (purchase.freight as? String) ?: ""
                "storageCharges" -> (purchase.storageCharges as? String) ?: ""
                "miscCharges" -> (purchase.miscCharges as? String) ?: ""
                "inspectionFee" -> (purchase.inspectionFee as? String) ?: ""
                "commission" -> (purchase.commission as? String) ?: ""
                "repairCompany" -> (purchase.repairCompany as? String) ?: ""
                "repairCharges" -> (purchase.repairCharges as? String) ?: ""
                "venueId" -> venueId
                "shipmentSize" -> shipmentSize
                "numberCut" -> (purchase.numberCut as? String) ?: ""
                "taxTotal" -> (purchase.taxTotal as? String) ?: ""
                "profit" -> (purchase.profit as? String) ?: ""
                "packagePrice" -> (purchase.packagePrice as? String) ?: ""
                "bookingId" -> (purchase.bookingId as? String) ?: ""
                "notes" -> (purchase.notes as? String) ?: ""
                else -> ""
            }
            
            tableHTML.append("""<td style="padding: 12px;">$cellValue</td>""")
        }
        
        tableHTML.append("""</tr>""")
    }
    
    tableHTML.append("""
            </tbody>
        </table>
    """)
    
    // Add pagination controls
    if (totalPages > 1) {
        tableHTML.append("""
            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 20px; padding: 10px;">
                <div style="color: #666;">
                    Showing ${startIndex + 1} to $endIndex of ${allPurchases.size} purchases
                </div>
                <div style="display: flex; gap: 10px;">
                    <button id="prevPageBtn" ${if (currentPage == 1) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (currentPage == 1) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (currentPage == 1) "not-allowed" else "pointer"};">
                        Previous
                    </button>
                    <span style="padding: 8px 16px; color: #666;">
                        Page $currentPage of $totalPages
                    </span>
                    <button id="nextPageBtn" ${if (currentPage == totalPages) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (currentPage == totalPages) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (currentPage == totalPages) "not-allowed" else "pointer"};">
                        Next
                    </button>
                </div>
            </div>
        """)
    }
    
    table.innerHTML = tableHTML.toString()
    
    // Add event listeners for edit buttons
    val editButtons = document.querySelectorAll(".edit-btn")
    for (i in 0 until editButtons.length) {
        val button = editButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val btn = event.currentTarget as HTMLElement
            val id = btn.getAttribute("data-id")
            if (id != null && id.isNotEmpty() && id != "0") {
                window.location.hash = "#/edit/$id"
            } else {
                showMessage("Invalid purchase ID. Cannot edit this purchase.", "error")
            }
        })
    }
    
    // Add event listeners for sortable headers
    setupSortableHeaders()
    
    // Setup date filter
    setupDateFilter()
    
    // Setup pagination event listeners
    document.getElementById("prevPageBtn")?.addEventListener("click", { _: Event ->
        if (currentPage > 1) {
            currentPage--
            displayPurchasesWithPagination()
        }
    })
    
    document.getElementById("nextPageBtn")?.addEventListener("click", { _: Event ->
        val totalPages = kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt()
        if (currentPage < totalPages) {
            currentPage++
            displayPurchasesWithPagination()
        }
    })
    
    setupColumnSorting()
}

fun deletePurchase(id: Long) {
    if (window.confirm("Are you sure you want to delete this purchase?")) {
        val scope = MainScope()
        scope.launch {
            val result = ApiClient.delete<dynamic>("purchases/$id")
            result.fold(
                onSuccess = {
                    ErrorHandler.showSuccess("Purchase deleted successfully!")
                    // Always return to the main list after deletion
                    showPurchaseList()
                },
                onError = { message, _ ->
                    Logger.error("Failed to delete purchase: $message")
                    ErrorHandler.showError("Failed to delete purchase: $message")
                }
            )
        }
    }
}

// Helper functions (these may be defined elsewhere or need to be implemented)
fun getSelectedColumns(): List<String> {
    // This should read from localStorage or return default columns
    val saved = safeLocalStorageGet("selectedColumns")
    return if (saved != null) {
        JSON.parse<Array<String>>(saved).toList()
    } else {
        listOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "clientName", "rixoCompany", "price")
    }
}

fun setupColumnSorting() {
    // Setup column sorting functionality
    val sortableColumns = document.querySelectorAll(".sortable-column")
    for (i in 0 until sortableColumns.length) {
        val column = sortableColumns.item(i) as HTMLElement
        column.addEventListener("click", { _: Event ->
            val field = column.getAttribute("data-field")
            if (field != null) {
                // Toggle sort order
                currentSortOrder = if (currentSortField == field && currentSortOrder == "asc") "desc" else "asc"
                currentSortField = field
                loadPurchases()
            }
        })
    }
}

fun showColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("columnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "columnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; align-items: center; justify-content: center;
    """
    
    val selectedColumnsList = getSelectedColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h3 style="margin: 0; color: #333;">Select Columns to Display</h3>
                <button id="closeColumnFilter" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>Maximum 9 columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="selectedCount">${selectedColumns.size}</span>/9</span>
            </div>
            <div id="columnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes (using function from MinimalPurchaseApp.kt)
    populateColumnCheckboxes(selectedColumns)
    
    // Update selection count initially
    updateColumnSelection()
    
    // Add event listeners
    document.getElementById("closeColumnFilter")?.addEventListener("click", { _: Event ->
        closeColumnFilterModal()
    })
    document.getElementById("resetColumns")?.addEventListener("click", { _: Event ->
        resetToDefaultColumns()
    })
    document.getElementById("applyColumns")?.addEventListener("click", { _: Event ->
        applyColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "columnFilterModal") {
            closeColumnFilterModal()
        }
    })
}

// External variables (defined in MinimalPurchaseApp.kt or shared state)
// selectedPurchases, currentSortField, and currentSortOrder are declared as private vars in MinimalPurchaseApp.kt

fun updateRixoButtonVisibility() {
    // Implementation for updating Rixo button visibility
}

// applyRoleBasedRestrictions moved to AuthSetup.kt

fun ensureSidebarPresent() {
    // Ensure sidebar exists (created in createApp)
    val sidebar = document.getElementById("sidebar")
    if (sidebar == null) {
        console.error("Sidebar not found! Make sure createApp() was called.")
        return
    }
    
    // Show hamburger button
    val hamburgerContainer = document.getElementById("hamburgerBtnContainer") as? HTMLElement
    hamburgerContainer?.style?.setProperty("display", "block")
    
    // Setup hamburger button listener
    setupHamburgerListener()
    
    // Setup sidebar listeners
    setupSidebarListeners()
    
    // Setup sidebar close button listener
    val closeSidebarBtn = document.getElementById("closeSidebar")
    if (closeSidebarBtn != null && !closeSidebarBtn.hasAttribute("data-listener-attached")) {
        closeSidebarBtn.setAttribute("data-listener-attached", "true")
        closeSidebarBtn.addEventListener("click", { _: Event ->
            closeSidebar()
        })
    }
    
    // Setup overlay click listener to close sidebar
    val overlay = document.getElementById("sidebarOverlay")
    if (overlay != null && !overlay.hasAttribute("data-listener-attached")) {
        overlay.setAttribute("data-listener-attached", "true")
        overlay.addEventListener("click", { _: Event ->
            closeSidebar()
        })
    }
}

