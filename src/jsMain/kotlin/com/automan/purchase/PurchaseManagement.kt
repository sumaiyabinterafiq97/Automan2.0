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

// Purchase table sort state (mirrors Car Brands master-list behavior: toggle asc/desc on each click)
// Key: purchase field (e.g. "chassis", "auctionHouse", "date")
var purchaseTableSortOrderByField: MutableMap<String, String> = mutableMapOf()

// Purchase date filter state (used only for Purchase List table view)
private var purchaseDateFilterActive: Boolean = false
private var purchasesBeforePurchaseDateFilter: Array<dynamic> = emptyArray()

private fun isoLocalToday(): String {
    // Local (not UTC) yyyy-MM-dd
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    val day = d.getDate() as Int
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

private fun isoLocalOffsetDays(daysOffset: Int): String {
    // Local (not UTC) yyyy-MM-dd
    val d = js("new Date()").unsafeCast<dynamic>()
    d.setDate(d.getDate() + daysOffset)
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    val day = d.getDate() as Int
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + day.toString().padStart(2, '0')
}

private fun isoLocalThisMonthStart(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val m = (d.getMonth() as Int) + 1
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-01"
}

private fun isoLocalThisMonthEnd(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val y = d.getFullYear() as Int
    val mIndex = d.getMonth() as Int // 0..11
    val tmp = js("new Date()").unsafeCast<dynamic>()
    // day=0 => last day of previous month
    tmp.setFullYear(y, mIndex + 1, 0)
    val lastDay = tmp.getDate() as Int
    val m = mIndex + 1
    return y.toString() + "-" + m.toString().padStart(2, '0') + "-" + lastDay.toString().padStart(2, '0')
}

private fun isoToLocalDayRangeTimestamps(isoDate: String): Pair<Long, Long>? {
    val trimmed = isoDate.trim()
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null

    // start at local 00:00:00.000
    val start = js("new Date()").unsafeCast<dynamic>()
    start.setFullYear(year, month - 1, day)
    start.setHours(0, 0, 0, 0)
    val startTs = (start.getTime() as Double).toLong()

    // end at local 23:59:59.999
    val end = js("new Date()").unsafeCast<dynamic>()
    end.setFullYear(year, month - 1, day)
    end.setHours(23, 59, 59, 999)
    val endTs = (end.getTime() as Double).toLong()
    return startTs to endTs
}

private fun applyPurchaseDateFilterRange(startIso: String, endIso: String) {
    val range = isoToLocalDayRangeTimestamps(startIso)?.let { startPair ->
        // Recompute end timestamps from the end iso (in case they're different)
        val endRange = isoToLocalDayRangeTimestamps(endIso) ?: return
        startPair.first to endRange.second
    } ?: return

    if (!purchaseDateFilterActive) {
        purchasesBeforePurchaseDateFilter = allPurchases
        purchaseDateFilterActive = true
    }

    val (startTs, endTs) = range
    val base = purchasesBeforePurchaseDateFilter.toList()

    val filtered = base.filter { purchase ->
        val raw = purchase.date?.toString() ?: ""
        val ts = parseDateForSorting(raw)
        ts != null && ts >= startTs && ts <= endTs
    }.toTypedArray()

    allPurchases = filtered
    currentPage = 1
    displayPurchasesWithPagination()
}

private fun clearPurchaseDateFilter() {
    if (!purchaseDateFilterActive) return
    allPurchases = purchasesBeforePurchaseDateFilter
    purchaseDateFilterActive = false
    purchasesBeforePurchaseDateFilter = emptyArray()
    currentPage = 1
    displayPurchasesWithPagination()
}

// Global variable to track last device type for auto-adjustment
var lastDeviceType: String? = getDeviceType()

private fun extractPurchaseSortStringValue(purchase: dynamic, field: String): String {
    return when (field) {
        "date" -> (purchase.date as? String) ?: purchase.date?.toString() ?: ""
        "chassis" -> (purchase.chassis as? String) ?: purchase.chassis?.toString() ?: ""
        "carName" -> (purchase.carName as? String) ?: purchase.carName?.toString() ?: ""
        "auctionHouse" -> (purchase.auctionHouse as? String) ?: purchase.auctionHouse?.toString() ?: ""
        "stockLocation" -> (purchase.stockLocation as? String) ?: purchase.stockLocation?.toString() ?: ""
        "clientName" -> (purchase.clientName as? String) ?: purchase.clientName?.toString() ?: ""
        "rixoCompany" -> (purchase.rixoCompany as? String) ?: purchase.rixoCompany?.toString() ?: ""
        "brand" -> (purchase.brand as? String) ?: purchase.brand?.toString() ?: ""
        "country" -> (purchase.country as? String) ?: purchase.country?.toString() ?: ""
        "repairCompany" -> (purchase.repairCompany as? String) ?: purchase.repairCompany?.toString() ?: ""
        else -> {
            // Fallback: try dynamic property
            val v = purchase.asDynamic()[field]
            if (v == null || v == js("undefined")) "" else v.toString()
        }
    }
}

private fun sortPurchasesInMemory(field: String, order: String): List<dynamic> {
    val purchasesList = allPurchases.toList()

    return purchasesList.sortedWith { a, b ->
        when (field) {
            "date" -> {
                val dateA = parseDateForSorting(extractPurchaseSortStringValue(a, "date"))
                val dateB = parseDateForSorting(extractPurchaseSortStringValue(b, "date"))

                when {
                    dateA == null && dateB == null -> 0
                    dateA == null -> 1 // nulls last
                    dateB == null -> -1 // nulls last
                    order == "asc" -> dateA.compareTo(dateB)
                    else -> dateB.compareTo(dateA)
                }
            }
            else -> {
                val aStr = extractPurchaseSortStringValue(a, field).trim().lowercase()
                val bStr = extractPurchaseSortStringValue(b, field).trim().lowercase()

                val aBlank = aStr.isBlank()
                val bBlank = bStr.isBlank()
                when {
                    aBlank && bBlank -> 0
                    aBlank -> 1 // blanks last
                    bBlank -> -1 // blanks last
                    order == "asc" -> aStr.compareTo(bStr)
                    else -> bStr.compareTo(aStr)
                }
            }
        }
    }
}

private fun togglePurchaseTableSort(field: String) {
    val current = purchaseTableSortOrderByField[field] ?: "desc"
    val next = if (current == "asc") "desc" else "asc"
    purchaseTableSortOrderByField[field] = next

    // Sort the currently displayed rows (filtered or unfiltered)
    val sortedCurrent = sortPurchasesInMemory(field, next).toTypedArray()
    allPurchases = sortedCurrent

    // If a date filter is active, keep the "base list" sorted too so clearing the date filter
    // restores the same order the user selected.
    if (purchaseDateFilterActive) {
        val base = purchasesBeforePurchaseDateFilter
        val prev = allPurchases
        allPurchases = base
        purchasesBeforePurchaseDateFilter = sortPurchasesInMemory(field, next).toTypedArray()
        allPurchases = prev
    }

    currentPage = 1
    displayPurchasesWithPagination()
}

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
    
    // Check for device change and auto-adjust columns
    checkAndAdjustColumnsForDeviceChange()
    
    // Setup window resize listener for device change detection
    setupDeviceChangeListener()
    
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

/**
 * Check if device type changed and auto-adjust columns if needed
 */
fun checkAndAdjustColumnsForDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, auto-adjust columns
    if (lastDeviceType != null && lastDeviceType != currentDeviceType) {
        Logger.debug("Device type changed from $lastDeviceType to $currentDeviceType, auto-adjusting columns")
        
        // Get saved columns
        val saved = safeLocalStorageGet("selectedColumns")
        val savedColumns = if (saved != null) {
            try {
                JSON.parse<Array<String>>(saved).toList()
            } catch (e: dynamic) {
                null
            }
        } else {
            null
        }
        
        // Auto-adjust if needed
        if (savedColumns != null) {
            val adjustedColumns = autoAdjustColumnsForDevice(savedColumns, currentDeviceType)
            
            // Save adjusted columns if they changed
            if (adjustedColumns != savedColumns) {
                safeLocalStorageSet("selectedColumns", JSON.stringify(adjustedColumns.toTypedArray()))
                Logger.debug("Auto-adjusted columns from ${savedColumns.size} to ${adjustedColumns.size} for $currentDeviceType")
            }
        }
    }
    
    // Update last device type
    lastDeviceType = currentDeviceType
}

/**
 * Setup window resize listener to detect device changes
 */
fun setupDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__deviceChangeListener
    if (existingListener != null) {
        val listenerFunc = existingListener.unsafeCast<((Event) -> Unit)?>()
        window.removeEventListener("resize", listenerFunc)
    }
    
    // Debounce resize events
    var resizeTimeout: dynamic = null
    val resizeListener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) {
            window.clearTimeout(resizeTimeout)
        }
        resizeTimeout = window.setTimeout({
            // Check if device type actually changed
            val newDeviceType = getDeviceType()
            if (lastDeviceType != null && lastDeviceType != newDeviceType) {
                // Device changed - auto-adjust columns and reload if on purchase list page
                checkAndAdjustColumnsForDeviceChange()
                
                // If we're on the purchase list page, reload to show adjusted columns
                if (window.location.hash.contains("#/purchase")) {
                    loadPurchases()
                }
            }
            lastDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__deviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadPurchases() {
    Logger.debug("loadPurchases function called")
    // Sorting dropdowns removed; always load base purchases.
    val endpoint = "purchases"
    
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

    // New data load => reset any active date filter to avoid stale base arrays.
    purchaseDateFilterActive = false
    purchasesBeforePurchaseDateFilter = emptyArray()
    
    if (js("purchases.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data.
            </div>
        """
        return
    }
    
    // Always sort by ID descending (newest first).
    val purchasesArray = purchases as Array<dynamic>
    val sortedPurchases = purchasesArray.sortedByDescending { purchase ->
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
    
    // Store all purchases globally for pagination
    allPurchases = sortedPurchases.toTypedArray()
    currentPage = 1 // Reset to first page when new data is loaded
    
    displayPurchasesWithPagination()
}

fun displayPurchasesWithPagination() {
    val table = document.getElementById("purchaseTable")!!
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        displayPurchasesAsCards()
        return
    }
    
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
            "fuel" to "Fuel",
            "seat" to "Seat",
            "door" to "Door",
            "distance" to "Distance",
            "options" to "Options",
            "auctionNo" to "Auction No",
            "rixoPrice" to "Rixo Price",
            "venueId" to "Venue ID",
            "shipmentSize" to "Vehicle type",
            "totalPrice" to "Total Price"
    )
    
    val tableHTML = StringBuilder()
        tableHTML.append("<table class='table table-striped'>")
        tableHTML.append("<thead><tr>")
        
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            if (columnKey == "date") {
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative; overflow: visible;">
                        <div style="display:flex; align-items:center; gap:8px;">
                            <span>$label</span>
                            <button id="purchaseDateQuickFilterBtn" title="Filter by purchase date" style="background:none; border:none; cursor:pointer; font-weight:600; color:#111827; padding:0; display:inline-flex; align-items:center; justify-content:center;">
                                📅
                            </button>
                        </div>
                        <div id="purchaseDateQuickFilterMenu" style="display:none; position:absolute; top:100%; left:0; background:white; border:1px solid #ddd; border-radius:4px; padding:10px; box-sizing:border-box; box-shadow:0 2px 8px rgba(0,0,0,0.1); z-index:20000; width:260px; min-width:260px; flex-direction:column; gap:8px;">
                            <div style="display:flex; gap:8px; flex-wrap:wrap;">
                                <button id="purchaseDateQuickTodayBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Today</button>
                                <button id="purchaseDateQuickLast7Btn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Last 7 days</button>
                                <button id="purchaseDateQuickThisMonthBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">This month</button>
                                <button id="purchaseDateQuickClearBtn" style="padding:6px 10px; background:#6c757d; color:white; border:1px solid #6c757d; border-radius:4px; cursor:pointer; font-size:12px;">Clear</button>
                                <button id="purchaseDateQuickFilterApplyBtn" style="padding:6px 10px; background:#007bff; color:white; border:none; border-radius:4px; cursor:pointer; font-size:12px;">Apply</button>
                            </div>
                            <div style="border-top:1px solid #eee; padding-top:8px; margin:0;">
                                <label style="display:block; margin-bottom:6px; font-size:12px; color:#666;">Choose date</label>
                                <input type="date" id="purchaseDateQuickFilterInput" style="width:100%; padding:6px; border:1px solid #ddd; border-radius:3px; font-size:12px; margin-bottom:8px;">
                            </div>
                        </div>
                    </th>
                """)
            } else if (sortableFields.contains(columnKey)) {
                val sortOrder = purchaseTableSortOrderByField[columnKey] ?: "desc"
                val tooltip = if (sortOrder == "asc") {
                    "Sorted A-Z (click to sort Z-A)"
                } else {
                    "Sorted Z-A (click to sort A-Z)"
                }
                val sortBtnId = "purchaseSortBtn_$columnKey"
                tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                        <button id="$sortBtnId" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                            <span>$label</span><span style="font-size: 14px;">↕</span>
                        </button>
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
        
        // Purchase Date quick filters + calendar wiring (table header)
        for (columnKey in selectedColumns) {
            if (columnKey != "date" && sortableFields.contains(columnKey)) {
                val sortBtnId = "purchaseSortBtn_$columnKey"
                document.getElementById(sortBtnId)?.addEventListener("click", { _: Event ->
                    togglePurchaseTableSort(columnKey)
                })
            }
        }

        val btn = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
        val menu = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
        val input = document.getElementById("purchaseDateQuickFilterInput") as? HTMLInputElement
        val applyBtn = document.getElementById("purchaseDateQuickFilterApplyBtn") as? HTMLElement
        val todayBtn = document.getElementById("purchaseDateQuickTodayBtn") as? HTMLElement
        val last7Btn = document.getElementById("purchaseDateQuickLast7Btn") as? HTMLElement
        val thisMonthBtn = document.getElementById("purchaseDateQuickThisMonthBtn") as? HTMLElement
        val clearBtn = document.getElementById("purchaseDateQuickClearBtn") as? HTMLElement

        // Keep an explicit open/close flag so we never depend on style.display strings.
        // (Some browsers + !important assignments can make style.display unreliable.)
        if (window.asDynamic().__purchaseDateQuickFilterMenuOpen == null) {
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        }

        btn?.addEventListener("click", { e: Event ->
            e.stopPropagation()
            if (menu == null) return@addEventListener
            val isOpen = window.asDynamic().__purchaseDateQuickFilterMenuOpen == true
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = !isOpen
            val next = if (isOpen) "none" else "flex"
            menu.style.setProperty("display", next, "important")
        })

        applyBtn?.addEventListener("click", { _: Event ->
            val selected = input?.value ?: ""
            if (selected.isNotBlank()) {
                applyPurchaseDateFilterRange(selected, selected)
            }
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })

        todayBtn?.addEventListener("click", { _: Event ->
            val today = isoLocalToday()
            input?.value = today
            applyPurchaseDateFilterRange(today, today)
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })
        last7Btn?.addEventListener("click", { _: Event ->
            val end = isoLocalToday()
            val start = isoLocalOffsetDays(-6)
            applyPurchaseDateFilterRange(start, end)
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })
        thisMonthBtn?.addEventListener("click", { _: Event ->
            val start = isoLocalThisMonthStart()
            val end = isoLocalThisMonthEnd()
            applyPurchaseDateFilterRange(start, end)
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })

        clearBtn?.addEventListener("click", { _: Event ->
            clearPurchaseDateFilter()
            window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
            menu?.style?.setProperty("display", "none", "important")
        })

        if (window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached != true) {
            window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached = true
            document.addEventListener("click", { event ->
                val target = event.target as? Node ?: return@addEventListener
                val m = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
                val b = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
                if (m == null) return@addEventListener

                val clickInsideMenu = m.contains(target)
                val clickInsideBtn = b != null && b.contains(target)

                if (!clickInsideMenu && !clickInsideBtn) {
                    window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
                    m.style.setProperty("display", "none", "important")
                }
            })
        }
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
        "shipmentSize" to "Vehicle type",
        "numberCut" to "Number Cut",
        "taxTotal" to "Tax Total",
        "profit" to "Profit",
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
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative; overflow: visible;">
                    <div style="display:flex; align-items:center; gap:8px;">
                        <span>$label</span>
                        <button id="purchaseDateQuickFilterBtn" title="Filter by purchase date" style="background:none; border:none; cursor:pointer; font-weight:600; color:#111827; padding:0; display:inline-flex; align-items:center; justify-content:center;">
                            📅
                        </button>
                    </div>
                    <div id="purchaseDateQuickFilterMenu" style="display:none; position:absolute; top:100%; left:0; background:white; border:1px solid #ddd; border-radius:4px; padding:10px; box-sizing:border-box; box-shadow:0 2px 8px rgba(0,0,0,0.1); z-index:20000; width:260px; min-width:260px; flex-direction:column; gap:8px;">
                        <div style="display:flex; gap:8px; flex-wrap:wrap;">
                            <button id="purchaseDateQuickTodayBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Today</button>
                            <button id="purchaseDateQuickLast7Btn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">Last 7 days</button>
                            <button id="purchaseDateQuickThisMonthBtn" style="padding:6px 10px; background:#f8f9fa; border:1px solid #e5e7eb; border-radius:4px; cursor:pointer; font-size:12px;">This month</button>
                            <button id="purchaseDateQuickClearBtn" style="padding:6px 10px; background:#6c757d; color:white; border:1px solid #6c757d; border-radius:4px; cursor:pointer; font-size:12px;">Clear</button>
                            <button id="purchaseDateQuickFilterApplyBtn" style="padding:6px 10px; background:#007bff; color:white; border:none; border-radius:4px; cursor:pointer; font-size:12px;">Apply</button>
                        </div>
                        <div style="border-top:1px solid #eee; padding-top:8px; margin:0;">
                            <label style="display:block; margin-bottom:6px; font-size:12px; color:#666;">Choose date</label>
                            <input type="date" id="purchaseDateQuickFilterInput" style="width:100%; padding:6px; border:1px solid #ddd; border-radius:3px; font-size:12px; margin-bottom:8px;">
                        </div>
                    </div>
                </th>
            """)
        } else if (sortableFields.contains(columnKey)) {
            val sortOrder = purchaseTableSortOrderByField[columnKey] ?: "desc"
            val tooltip = if (sortOrder == "asc") {
                "Sorted A-Z (click to sort Z-A)"
            } else {
                "Sorted Z-A (click to sort A-Z)"
            }
            val sortBtnId = "purchaseSortBtn_$columnKey"
            tableHTML.append("""
                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">
                    <button id="$sortBtnId" title="$tooltip" style="background: none; border: none; cursor: pointer; font-weight: 600; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;">
                        <span>$label</span><span style="font-size: 14px;">↕</span>
                    </button>
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
            val dateStr = (purchase.date?.toString() ?: "").toString()
            if (dateStr.isEmpty() || dateStr.trim().isEmpty()) "" else formatWithWeekday(dateStr)
        } catch (e: dynamic) {
            ""
        }
        val date = dateValue
        val chassis = (purchase.chassis ?: "").toString()
        val carName = (purchase.carName ?: "").toString()
        val auctionHouse = (purchase.auctionHouse ?: "").toString()
        val stockLocation = (purchase.stockLocation ?: "").toString()
        val clientName = (purchase.clientName ?: "").toString()
        val rixoCompany = (purchase.rixoCompany ?: "").toString()
        val priceStr = (purchase.price as? String) ?: ""
        val carModelYear = purchase.carModelYear ?: ""
        val brand = purchase.brand ?: ""
        val grade = purchase.grade ?: ""
        val rank = purchase.rank ?: ""
        val color = purchase.color ?: ""
        val fuel = purchase.fuel ?: ""
        val seat = purchase.seat ?: ""
        val door = purchase.door ?: ""
        val distance = purchase.distance ?: ""
        val options = purchase.options ?: ""
        val auctionNo = purchase.auctionNo ?: ""
        val rixoPriceRaw = purchase.rixoPrice
        val venueId = purchase.venueId ?: ""
        val shipmentSize = purchase.shipmentSize ?: ""
        
        tableHTML.append("""
            <tr style="border-bottom: 1px solid #f0f0f0;">
                <td style="padding: 8px 12px;">
                    ${if (isEditor() && purchaseId > 0L) """
                    <button class="edit-btn" data-id="${purchaseId}" aria-label="Edit" title="Edit"
                            style="display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
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
                "fuel" -> fuel
                "seat" -> seat
                "door" -> door
                "distance" -> distance
                "options" -> options
                "auctionNo" -> auctionNo
                "country" -> (purchase.country as? String) ?: ""
                "totalPrice" -> {
                    val raw = purchase.totalPrice as? String ?: ""
                    if (raw.isEmpty()) "" else (if (raw.startsWith("¥")) raw else "¥$raw")
                }
                "auctionFee" -> (purchase.auctionFee as? String) ?: ""
                "recycleFee" -> (purchase.recycleFee as? String) ?: ""
                "roadTax" -> (purchase.roadTax as? String) ?: ""
                "paymentDate" -> (purchase.paymentDate as? String) ?: ""
                "rixoRequested" -> (purchase.rixoRequested as? String) ?: ""
                "rixoConfirmed" -> (purchase.rixoConfirmed as? String) ?: ""
                "rixoPrice" -> {
                    val num = when (rixoPriceRaw) {
                        null -> 0.0
                        is Number -> (rixoPriceRaw as Number).toDouble()
                        else -> parseCurrency(rixoPriceRaw.toString())
                    }
                    if (num > 0.0) "¥" + formatCurrency(num) else "¥0"
                }
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

    // Purchase Date quick filters + calendar wiring (table header)
    val btn = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
    val menu = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
    val input = document.getElementById("purchaseDateQuickFilterInput") as? HTMLInputElement
    val applyBtn = document.getElementById("purchaseDateQuickFilterApplyBtn") as? HTMLElement
    val todayBtn = document.getElementById("purchaseDateQuickTodayBtn") as? HTMLElement
    val last7Btn = document.getElementById("purchaseDateQuickLast7Btn") as? HTMLElement
    val thisMonthBtn = document.getElementById("purchaseDateQuickThisMonthBtn") as? HTMLElement
    val clearBtn = document.getElementById("purchaseDateQuickClearBtn") as? HTMLElement

    // Keep an explicit open/close flag so we never depend on style.display strings.
    if (window.asDynamic().__purchaseDateQuickFilterMenuOpen == null) {
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
    }

    btn?.addEventListener("click", { e: Event ->
        e.stopPropagation()
        if (menu == null) return@addEventListener
        val isOpen = window.asDynamic().__purchaseDateQuickFilterMenuOpen == true
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = !isOpen
        val next = if (isOpen) "none" else "flex"
        menu.style.setProperty("display", next, "important")
    })

    applyBtn?.addEventListener("click", { _: Event ->
        val selected = input?.value ?: ""
        if (selected.isNotBlank()) {
            applyPurchaseDateFilterRange(selected, selected)
        }
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })

    todayBtn?.addEventListener("click", { _: Event ->
        val today = isoLocalToday()
        input?.value = today
        applyPurchaseDateFilterRange(today, today)
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })
    last7Btn?.addEventListener("click", { _: Event ->
        val end = isoLocalToday()
        val start = isoLocalOffsetDays(-6)
        applyPurchaseDateFilterRange(start, end)
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })
    thisMonthBtn?.addEventListener("click", { _: Event ->
        val start = isoLocalThisMonthStart()
        val end = isoLocalThisMonthEnd()
        applyPurchaseDateFilterRange(start, end)
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })
    clearBtn?.addEventListener("click", { _: Event ->
        clearPurchaseDateFilter()
        window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
        menu?.style?.setProperty("display", "none", "important")
    })

    if (window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached != true) {
        window.asDynamic().__purchaseDateQuickFilterOutsideListenerAttached = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val m = document.getElementById("purchaseDateQuickFilterMenu") as? HTMLElement
            val b = document.getElementById("purchaseDateQuickFilterBtn") as? HTMLElement
            if (m == null) return@addEventListener

            val clickInsideMenu = m.contains(target)
            val clickInsideBtn = b != null && b.contains(target)

            if (!clickInsideMenu && !clickInsideBtn) {
                window.asDynamic().__purchaseDateQuickFilterMenuOpen = false
                m.style.setProperty("display", "none", "important")
            }
        })
    }
    for (columnKey in selectedColumns) {
        if (columnKey != "date" && sortableFields.contains(columnKey)) {
            val sortBtnId = "purchaseSortBtn_$columnKey"
            document.getElementById(sortBtnId)?.addEventListener("click", { _: Event ->
                togglePurchaseTableSort(columnKey)
            })
        }
    }
    
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
}

/**
 * Display purchases as cards for mobile view
 */
fun displayPurchasesAsCards() {
    val table = document.getElementById("purchaseTable")!!
    
    if (allPurchases.isEmpty()) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data.
            </div>
        """
        return
    }
    
    // Calculate pagination
    val totalPages = kotlin.math.ceil(allPurchases.size.toDouble() / itemsPerPage).toInt()
    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = kotlin.math.min(startIndex + itemsPerPage, allPurchases.size)
    val paginatedPurchases = allPurchases.sliceArray(startIndex until endIndex)
    
    val selectedColumns = getSelectedColumns()
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
        "shipmentSize" to "Vehicle type",
        "numberCut" to "Number Cut",
        "taxTotal" to "Tax Total",
        "profit" to "Profit",
        "bookingId" to "Booking ID",
        "notes" to "Notes"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="purchase-cards-container">""")
    
    for (purchase in paginatedPurchases) {
        val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
        
        // Safely handle date
        val dateValue = try {
            val dateStr = (purchase.date?.toString() ?: "").toString()
            if (dateStr.isEmpty() || dateStr.trim().isEmpty()) "" else formatWithWeekday(dateStr)
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
        val brand = purchase.brand ?: ""
        val country = (purchase.country as? String) ?: ""
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
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
                "brand" -> brand
                "country" -> country
                else -> {
                    val purchaseValue = purchase.asDynamic()[columnKey]
                    when {
                        purchaseValue != null && purchaseValue != js("undefined") -> purchaseValue.toString()
                        else -> ""
                    }
                }
            }
            
            // Convert to string and check if not blank (safe for JS strings)
            val valueStr = value.toString()
            val isValueNotEmpty = valueStr.isNotEmpty() && valueStr.trim().isNotEmpty()
            
            if (isValueNotEmpty) {
                cardFields.append("""
                    <div class="card-field">
                        <span class="card-label">$label:</span>
                        <span class="card-value">$valueStr</span>
                    </div>
                """)
            }
        }
        
        // Safe check for chassis (convert to string first)
        val chassisStr = chassis.toString()
        val hasChassis = chassisStr.isNotEmpty() && chassisStr.trim().isNotEmpty()
        
        cardsHTML.append("""
            <div class="purchase-card">
                <div class="card-header">
                    ${if (isEditor() && purchaseId > 0L) """
                    <button class="card-edit-btn" data-id="${purchaseId}" aria-label="Edit" title="Edit">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    """ else ""}
                    <div class="card-title">${if (hasChassis) chassisStr else "Purchase #$purchaseId"}</div>
                </div>
                <div class="card-body">
                    $cardFields
                </div>
            </div>
        """)
    }
    
    cardsHTML.append("</div>")
    
    // Add pagination controls
    if (totalPages > 1) {
        cardsHTML.append("""
            <div class="pagination-controls">
                <div class="pagination-info">
                    Showing ${startIndex + 1} to $endIndex of ${allPurchases.size} purchases
                </div>
                <div class="pagination-buttons">
                    <button id="prevPageBtn" ${if (currentPage == 1) "disabled" else ""} class="pagination-btn ${if (currentPage == 1) "disabled" else ""}">
                        Previous
                    </button>
                    <span class="pagination-page">
                        Page $currentPage of $totalPages
                    </span>
                    <button id="nextPageBtn" ${if (currentPage == totalPages) "disabled" else ""} class="pagination-btn ${if (currentPage == totalPages) "disabled" else ""}">
                        Next
                    </button>
                </div>
            </div>
        """)
    }
    
    table.innerHTML = cardsHTML.toString()
    
    // Add event listeners for edit buttons
    val editButtons = document.querySelectorAll(".card-edit-btn")
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
    // Get current device type
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val defaultColumns = getDefaultColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Filter out removed columns (displacement, packagePrice)
    val validColumns = savedColumns.filter { it != "displacement" && it != "packagePrice" }
    
    // Auto-adjust if saved columns exceed device limit
    return autoAdjustColumnsForDevice(validColumns, deviceType)
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
    
    // Get current device type and limits
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val deviceDisplayName = when (deviceType) {
        "mobile" -> "Mobile View"
        "tablet" -> "Tablet View"
        else -> "Desktop View"
    }
    
    val selectedColumnsList = getSelectedColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="selectedCount">0</span></span>
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
    
    // Update selection count initially (this will set the correct format)
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

