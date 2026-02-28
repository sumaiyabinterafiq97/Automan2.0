package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler

// Global pagination variables for Car Brands
var carBrandsCurrentPage = 1
var carBrandsItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarBrands: List<dynamic> = emptyList()

// Global pagination variables for Suppliers
var suppliersCurrentPage = 1
var suppliersItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allSuppliers: List<dynamic> = emptyList()

// Global variable to track last device type for Supplier page
var lastSupplierDeviceType: String? = getDeviceType()

// Global pagination variables for Consignees
var consigneesCurrentPage = 1
var consigneesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allConsignees: List<dynamic> = emptyList()

// Global variable to track last device type for Consignee page
var lastConsigneeDeviceType: String? = getDeviceType()

// Global pagination variables for Country page
var countriesCurrentPage = 1
var countriesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCountries: List<String> = emptyList()

// Global pagination variables for Rixo Company page
var rixoCompaniesCurrentPage = 1
var rixoCompaniesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allRixoCompanies: List<String> = emptyList()

// Global pagination variables for Stock Location page
var stockLocationsCurrentPage = 1
var stockLocationsItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allStockLocations: List<String> = emptyList()

// Global pagination variables for Repair Company page
var repairCompaniesCurrentPage = 1
var repairCompaniesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allRepairCompanies: List<String> = emptyList()

// Global pagination variables for Venue ID page
var venueIdsCurrentPage = 1
var venueIdsItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allVenueIds: List<String> = emptyList()

// Global variable to track last device type for Car Brands page
var lastCarBrandDeviceType: String? = getDeviceType()

/**
 * Get default columns for Supplier page based on device type
 */
fun getDefaultSupplierColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("id", "supplierName", "stockLocation", "rixoCompany")
        "tablet" -> listOf("id", "supplierName", "stockLocation", "rixoCompany", "venueId", "rixoPrice")
        "desktop" -> listOf("id", "supplierName", "stockLocation", "rixoCompany", "venueId", "rixoPrice", "typeOfVehicle")
        else -> listOf("id", "supplierName", "stockLocation", "rixoCompany", "venueId", "rixoPrice", "typeOfVehicle")
    }
}

/**
 * Get selected columns for Supplier page
 */
fun getSelectedSupplierColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val defaultColumns = getDefaultSupplierColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedSupplierColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved supplier columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Auto-adjust if saved columns exceed device limit
    return if (savedColumns.size > maxColumns) {
        defaultColumns
    } else {
        savedColumns.filter { it.isNotBlank() }.take(maxColumns)
    }
}

/**
 * Get default columns for Consignee page based on device type
 */
fun getDefaultConsigneeColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("id", "country", "consigneeName", "pod")
        "tablet" -> listOf("id", "country", "consigneeName", "pod", "clientName", "stockLocation")
        "desktop" -> listOf("id", "country", "clientName", "consigneeName", "consigneeAddress", "pod", "stockLocation")
        else -> listOf("id", "country", "clientName", "consigneeName", "consigneeAddress", "pod", "stockLocation")
    }
}

/**
 * Get default columns for Car Brands page based on device type
 */
fun getDefaultCarBrandColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("id", "carBrand", "chassis", "carName")
        "tablet" -> listOf("id", "carBrand", "chassis", "carName", "fuel", "wd")
        "desktop" -> listOf("id", "carBrand", "chassis", "carName", "fuel", "wd", "shift", "grade", "cc", "door")
        else -> listOf("id", "carBrand", "chassis", "carName", "fuel", "wd", "shift", "grade", "cc", "door")
    }
}

/**
 * Get selected columns for Car Brands page
 */
fun getSelectedCarBrandColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val defaultColumns = getDefaultCarBrandColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedCarBrandColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved car brand columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Auto-adjust if saved columns exceed device limit
    return if (savedColumns.size > maxColumns) {
        defaultColumns
    } else {
        savedColumns.filter { it.isNotBlank() }.take(maxColumns)
    }
}

/**
 * Get selected columns for Consignee page
 */
fun getSelectedConsigneeColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val defaultColumns = getDefaultConsigneeColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedConsigneeColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved consignee columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Auto-adjust if saved columns exceed device limit
    return if (savedColumns.size > maxColumns) {
        defaultColumns
    } else {
        savedColumns.filter { it.isNotBlank() }.take(maxColumns)
    }
}

/**
 * Get default columns for Country page (same for all devices)
 */
fun getDefaultCountryColumnsForDevice(deviceType: String? = null): List<String> {
    return listOf("id", "country")
}

/**
 * Get selected columns for Country page
 */
fun getSelectedCountryColumns(): List<String> {
    val defaultColumns = getDefaultCountryColumnsForDevice(getDeviceType())
    val saved = safeLocalStorageGet("selectedCountryColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            null
        }
    } else null
    if (savedColumns == null || savedColumns.isEmpty()) return defaultColumns
    return savedColumns.filter { it.isNotBlank() }.ifEmpty { defaultColumns }
}

// Master List Functions

fun showMasterClientsPage() {
    window.location.hash = "#/master/clients"
    // Use the Client Accounts Management page functionality
    showClientAccountsPage()
}

fun loadMasterClients() {
    // Use the loadClients function from ClientManagement.kt
    loadClients()
}

fun showAddClientModal() {
    // Use the showAddClientForm function from ClientManagement.kt
    showAddClientForm()
}

fun showMasterConsigneePage() {
    window.location.hash = "#/master/consignee"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="consigneeList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Consignee</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="consigneeColumnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            
            <!-- Search and Filter Section -->
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="display: flex; gap: 15px; align-items: center; flex-wrap: wrap;">
                    <div style="flex: 1; min-width: 250px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Country:</label>
                        <input type="text" id="consigneeCountryFilter" placeholder="Type country name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                    </div>
                    <div style="display: flex; gap: 10px; align-items: flex-end;">
                        <button id="clearConsigneeFilterBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Clear Filter</button>
                    </div>
                </div>
            </div>
            
            <!-- Action Buttons -->
            <div style="margin-bottom: 20px;">
                <button id="addConsigneeBtn" style="padding: 12px 24px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    ➕ Add New Consignee
                </button>
            </div>
            
            <!-- Consignee Table/Cards Container -->
            <div id="consigneeTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading consignee data...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    
    // Load initial data
    loadMasterConsignee()
    
    // Event listeners
    document.getElementById("addConsigneeBtn")?.addEventListener("click", { _: Event ->
        showAddConsigneeModal()
    })
    
    document.getElementById("clearConsigneeFilterBtn")?.addEventListener("click", { _: Event ->
        val filterInput = document.getElementById("consigneeCountryFilter") as HTMLInputElement?
        filterInput?.value = ""
        loadMasterConsignee()
    })
    
    // Real-time search filter
    document.getElementById("consigneeCountryFilter")?.addEventListener("input", { _: Event ->
        loadMasterConsignee()
    })
    
    // Column filter button
    document.getElementById("consigneeColumnFilterBtn")?.addEventListener("click", { _: Event ->
        showConsigneeColumnFilterModal()
    })
    
    // Setup device change listener for Consignee page
    setupConsigneeDeviceChangeListener()
    
    // Check for device change and reload if needed
    checkConsigneeDeviceChange()
}

/**
 * Check if device type changed for Consignee page and reload if needed
 */
fun checkConsigneeDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, reload consignees to switch between card/table views
    if (lastConsigneeDeviceType != null && lastConsigneeDeviceType != currentDeviceType) {
        Logger.debug("Consignee page: Device type changed from $lastConsigneeDeviceType to $currentDeviceType, reloading consignees")
        loadMasterConsignee()
    }
    
    // Update last device type
    lastConsigneeDeviceType = currentDeviceType
}

/**
 * Setup window resize listener for Consignee page to detect device changes
 */
fun setupConsigneeDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__consigneeDeviceChangeListener
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
            if (lastConsigneeDeviceType != null && lastConsigneeDeviceType != newDeviceType) {
                // Device changed - reload consignees to switch between card/table views
                Logger.debug("Consignee page: Device type changed from $lastConsigneeDeviceType to $newDeviceType, reloading")
                
                // If we're on the consignee page, reload to show correct view (cards or table)
                if (window.location.hash.contains("#/master/consignee")) {
                    loadMasterConsignee()
                }
            }
            lastConsigneeDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__consigneeDeviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadMasterConsignee() {
    val tableDiv = document.getElementById("consigneeTable")
    if (tableDiv == null) return
    
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        loadMasterConsigneesWithCards()
        return
    }
    
    loadMasterConsigneesWithTable()
}

fun loadMasterConsigneesWithCards() {
    val tableDiv = document.getElementById("consigneeTable")
    if (tableDiv == null) return
    
    // Get country filter value
    val countryFilter = (document.getElementById("consigneeCountryFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    // Show loading state
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading consignee data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Load from booking mappings
    window.fetch(apiUrl("booking/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignee')")
        }
        .then { result: dynamic ->
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            
            // Filter by country if filter is set
            val filteredMappings = if (countryFilter.isNotEmpty()) {
                mappingsArray.filter { mapping ->
                    val country = (mapping.country ?: "").toString().uppercase()
                    country.contains(countryFilter)
                }
            } else {
                mappingsArray.toList()
            }
            
            // Store all filtered mappings for pagination
            allConsignees = filteredMappings
            if (countryFilter.isNotEmpty()) {
                consigneesCurrentPage = 1
            }
            
            displayConsigneesAsCards(filteredMappings, countryFilter)
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignees: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading consignee data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun loadMasterConsigneesWithTable() {
    val tableDiv = document.getElementById("consigneeTable")
    if (tableDiv == null) return
    
    // Get country filter value
    val countryFilter = (document.getElementById("consigneeCountryFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    // Show loading state
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading consignee data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Load from booking mappings
    window.fetch(apiUrl("booking/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignee')")
        }
        .then { result: dynamic ->
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            
            // Filter by country if filter is set
            val filteredMappings = if (countryFilter.isNotEmpty()) {
                mappingsArray.filter { mapping ->
                    val country = (mapping.country ?: "").toString().uppercase()
                    country.contains(countryFilter)
                }
            } else {
                mappingsArray.toList()
            }
            
            // Store all filtered mappings for pagination
            allConsignees = filteredMappings
            if (countryFilter.isNotEmpty()) {
                consigneesCurrentPage = 1
            }
            
            if (filteredMappings.isEmpty()) {
                val message = if (countryFilter.isNotEmpty()) {
                    "No consignee data found for country: $countryFilter"
                } else {
                    "No consignee data found."
                }
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            // Calculate pagination
            val totalPages = kotlin.math.ceil(filteredMappings.size.toDouble() / consigneesItemsPerPage).toInt()
            val startIndex = (consigneesCurrentPage - 1) * consigneesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + consigneesItemsPerPage, filteredMappings.size)
            val paginatedMappings = filteredMappings.subList(startIndex, endIndex)
            
            // Get selected columns
            val selectedColumns = getSelectedConsigneeColumns()
            val columnLabels = mapOf(
                "id" to "ID",
                "country" to "Country",
                "clientName" to "Client Name",
                "consigneeName" to "Consignee Name",
                "consigneeAddress" to "Consignee Address",
                "pod" to "POD",
                "stockLocation" to "Stock Location"
            )
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="consignee-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
            """
            
            // Add headers for selected columns only
            for (columnKey in selectedColumns) {
                val label = columnLabels[columnKey] ?: columnKey
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (mapping in paginatedMappings) {
                val id = (mapping.id ?: "").toString()
                val country = (mapping.country ?: "").toString()
                val clientName = (mapping.clientName ?: "").toString()
                val consigneeName = (mapping.consigneeName ?: "").toString()
                val consigneeAddress = (mapping.consigneeAddress ?: "").toString()
                val consigneeAddressShort = if (consigneeAddress.length > 60) consigneeAddress.take(60) + "..." else consigneeAddress
                val pod = (mapping.pod ?: "").toString()
                val stockLocation = (mapping.stockLocation ?: "").toString()
                
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 8px 12px;">
                            <button onclick="window.editMasterConsignee($id)" aria-label="Edit" title="Edit"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                </svg>
                            </button>
                        </td>
                """
                
                // Add cells for selected columns only
                for (columnKey in selectedColumns) {
                    val value = when (columnKey) {
                        "id" -> id
                        "country" -> country
                        "clientName" -> clientName
                        "consigneeName" -> consigneeName
                        "consigneeAddress" -> consigneeAddressShort
                        "pod" -> pod
                        "stockLocation" -> stockLocation
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "country", "consigneeName" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        "consigneeAddress" -> "padding: 14px 16px; color: #6b7280; font-size: 13px;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    val titleAttr = if (columnKey == "consigneeAddress" && consigneeAddress.length > 60) " title=\"$consigneeAddress\"" else ""
                    html += """<td style="$cellStyle"$titleAttr>$value</td>"""
                }
                
                html += """</tr>"""
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
            // Add pagination controls if there are multiple pages
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filteredMappings.size} consignee${if (filteredMappings.size != 1) "s" else ""}${if (countryFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="consigneesPrevPage" class="consignee-pagination-btn" ${if (consigneesCurrentPage == 1) "disabled" else ""}>
                                Previous
                            </button>
                            <span class="consignee-pagination-page">Page $consigneesCurrentPage of $totalPages</span>
                            <button id="consigneesNextPage" class="consignee-pagination-btn" ${if (consigneesCurrentPage >= totalPages) "disabled" else ""}>
                                Next
                            </button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filteredMappings.size} consignee${if (filteredMappings.size != 1) "s" else ""}${if (countryFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            
            tableDiv.innerHTML = html
            
            // Add pagination event listeners
            document.getElementById("consigneesPrevPage")?.addEventListener("click", { _: Event ->
                if (consigneesCurrentPage > 1) {
                    consigneesCurrentPage--
                    loadMasterConsignee()
                }
            })
            
            document.getElementById("consigneesNextPage")?.addEventListener("click", { _: Event ->
                val totalPages = kotlin.math.ceil(allConsignees.size.toDouble() / consigneesItemsPerPage).toInt()
                if (consigneesCurrentPage < totalPages) {
                    consigneesCurrentPage++
                    loadMasterConsignee()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignee: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading consignee data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun displayConsigneesAsCards(filteredMappings: List<dynamic>, countryFilter: String) {
    val tableDiv = document.getElementById("consigneeTable")
    if (tableDiv == null) return
    
    if (filteredMappings.isEmpty()) {
        val message = if (countryFilter.isNotEmpty()) {
            "No consignee data found for country: $countryFilter"
        } else {
            "No consignee data found."
        }
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                $message
            </div>
        """
        return
    }
    
    // Calculate pagination
    val totalPages = kotlin.math.ceil(filteredMappings.size.toDouble() / consigneesItemsPerPage).toInt()
    val startIndex = (consigneesCurrentPage - 1) * consigneesItemsPerPage
    val endIndex = kotlin.math.min(startIndex + consigneesItemsPerPage, filteredMappings.size)
    val paginatedMappings = filteredMappings.subList(startIndex, endIndex)
    
    val selectedColumns = getSelectedConsigneeColumns()
    val columnLabels = mapOf(
        "id" to "ID",
        "country" to "Country",
        "clientName" to "Client Name",
        "consigneeName" to "Consignee Name",
        "consigneeAddress" to "Consignee Address",
        "pod" to "POD",
        "stockLocation" to "Stock Location"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="consignee-cards-container">""")
    
    for (mapping in paginatedMappings) {
        val id = (mapping.id ?: "").toString()
        val country = (mapping.country ?: "").toString()
        val clientName = (mapping.clientName ?: "").toString()
        val consigneeName = (mapping.consigneeName ?: "").toString()
        val consigneeAddress = (mapping.consigneeAddress ?: "").toString()
        val pod = (mapping.pod ?: "").toString()
        val stockLocation = (mapping.stockLocation ?: "").toString()
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "id" -> id
                "country" -> country
                "clientName" -> clientName
                "consigneeName" -> consigneeName
                "consigneeAddress" -> consigneeAddress
                "pod" -> pod
                "stockLocation" -> stockLocation
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                cardFields.append("""
                    <div style="margin-bottom: 8px;">
                        <span style="font-weight: 600; color: #666; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">$label:</span>
                        <div style="color: #333; font-size: 14px; margin-top: 2px;">$value</div>
                    </div>
                """)
            }
        }
        
        cardsHTML.append("""
            <div class="consignee-card">
                <div class="card-header">
                    <button class="card-edit-btn" onclick="window.editMasterConsignee($id)" aria-label="Edit" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    <div class="card-title">${if (country.isNotEmpty()) country else "Consignee #$id"}</div>
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
                <button id="consigneesPrevPage" class="pagination-btn" ${if (consigneesCurrentPage == 1) "disabled" else ""}>
                    Previous
                </button>
                <span class="pagination-page">Page $consigneesCurrentPage of $totalPages</span>
                <button id="consigneesNextPage" class="pagination-btn" ${if (consigneesCurrentPage >= totalPages) "disabled" else ""}>
                    Next
                </button>
            </div>
        """)
    } else {
        cardsHTML.append("""
            <div style="padding: 16px; text-align: center; color: #6b7280; font-size: 14px;">
                Total: ${filteredMappings.size} consignee${if (filteredMappings.size != 1) "s" else ""}${if (countryFilter.isNotEmpty()) " (filtered)" else ""}
            </div>
        """)
    }
    
    tableDiv.innerHTML = cardsHTML.toString()
    
    // Add pagination event listeners
    document.getElementById("consigneesPrevPage")?.addEventListener("click", { _: Event ->
        if (consigneesCurrentPage > 1) {
            consigneesCurrentPage--
            loadMasterConsignee()
        }
    })
    
    document.getElementById("consigneesNextPage")?.addEventListener("click", { _: Event ->
        val totalPages = kotlin.math.ceil(allConsignees.size.toDouble() / consigneesItemsPerPage).toInt()
        if (consigneesCurrentPage < totalPages) {
            consigneesCurrentPage++
            loadMasterConsignee()
        }
    })
}

fun showConsigneeColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("consigneeColumnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "consigneeColumnFilterModal"
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
    
    val selectedColumnsList = getSelectedConsigneeColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeConsigneeColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="consigneeSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="consigneeColumnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetConsigneeColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyConsigneeColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes
    val columnLabels = mapOf(
        "id" to "ID",
        "country" to "Country",
        "clientName" to "Client Name",
        "consigneeName" to "Consignee Name",
        "consigneeAddress" to "Consignee Address",
        "pod" to "POD",
        "stockLocation" to "Stock Location"
    )
    
    val checkboxesDiv = document.getElementById("consigneeColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val checkbox = document.createElement("div")
        val checkboxStyle = checkbox.asDynamic().style
        checkboxStyle.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "consigneeCol_$key"
        input.setAttribute("data-column", key)
        input.checked = selectedColumns.contains(key)
        input.addEventListener("change", { _: Event ->
            updateConsigneeColumnSelection()
        })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "consigneeCol_$key"
        labelEl.textContent = label
        val labelStyle = labelEl.asDynamic().style
        labelStyle.cssText = "cursor: pointer; margin: 0;"
        checkbox.appendChild(input)
        checkbox.appendChild(labelEl)
        checkboxesDiv?.appendChild(checkbox)
    }
    
    // Update selection count initially
    updateConsigneeColumnSelection()
    
    // Add event listeners
    document.getElementById("closeConsigneeColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("consigneeColumnFilterModal")?.remove()
    })
    document.getElementById("resetConsigneeColumns")?.addEventListener("click", { _: Event ->
        val deviceType = getDeviceType()
        val defaultColumns = getDefaultConsigneeColumnsForDevice(deviceType)
        columnLabels.keys.forEach { col ->
            val checkbox = document.getElementById("consigneeCol_$col") as? HTMLInputElement
            checkbox?.checked = defaultColumns.contains(col)
        }
        updateConsigneeColumnSelection()
    })
    document.getElementById("applyConsigneeColumns")?.addEventListener("click", { _: Event ->
        applyConsigneeColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "consigneeColumnFilterModal") {
            document.getElementById("consigneeColumnFilterModal")?.remove()
        }
    })
}

fun updateConsigneeColumnSelection() {
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val checkboxes = document.querySelectorAll("#consigneeColumnCheckboxes input[type='checkbox']")
    var selectedCount = 0
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedCount++
        }
    }
    
    val countSpan = document.getElementById("consigneeSelectedCount")
    countSpan?.textContent = "$selectedCount"
    
    // Disable/enable checkboxes based on max limit
    if (selectedCount >= maxColumns) {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            if (!checkbox.checked) {
                checkbox.disabled = true
            }
        }
    } else {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.disabled = false
        }
    }
}

fun applyConsigneeColumnChanges() {
    val checkboxes = document.querySelectorAll("#consigneeColumnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableListOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val columnKey = checkbox.getAttribute("data-column") ?: ""
            if (columnKey.isNotEmpty()) {
                selectedColumns.add(columnKey)
            }
        }
    }
    
    // Save to localStorage
    safeLocalStorageSet("selectedConsigneeColumns", JSON.stringify(selectedColumns.toTypedArray()))
    
    // Close modal
    document.getElementById("consigneeColumnFilterModal")?.remove()
    
    // Reload consignees to apply changes
    loadMasterConsignee()
}

fun showAddConsigneeModal() {
    showConsigneeModal(null)
}

fun showConsigneeModal(mappingId: Long?) {
    val isEdit = mappingId != null
    val title = if (isEdit) "Edit Consignee" else "Add New Consignee"
    
    val modalHtml = """
        <div id="consigneeModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeConsigneeModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="consigneeForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Country <span style="color: #ef4444;">*</span></label>
                            <input type="text" id="consigneeCountry" required style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Client Name</label>
                            <input type="text" id="consigneeClientName" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Consignee Name</label>
                            <input type="text" id="consigneeName" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Consignee Address</label>
                            <textarea id="consigneeAddress" rows="4" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box; resize: vertical; font-family: inherit;"></textarea>
                        </div>
                        <div class="consignee-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POD (Port of Discharge)</label>
                                <input type="text" id="consigneePOD" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</label>
                                <input type="text" id="consigneeStockLocation" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POLs (Ports of Loading) - Comma separated</label>
                            <input type="text" id="consigneePOLs" placeholder="e.g., Global Hakata, Global Kawasaki" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Notes</label>
                            <textarea id="consigneeNotes" rows="3" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box; resize: vertical; font-family: inherit;"></textarea>
                        </div>
                        <div class="consignee-modal-actions">
                            <button type="button" id="cancelConsigneeBtn" class="consignee-modal-btn consignee-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteConsigneeBtn" class="consignee-modal-btn consignee-modal-btn-delete">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveConsigneeBtn" class="consignee-modal-btn consignee-modal-btn-save">${if (isEdit) "Update" else "Save"} Consignee</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    
    // Load data if editing
    if (isEdit && mappingId != null) {
        loadConsigneeDataForEdit(mappingId)
    }
    
    // Event listeners
    document.getElementById("closeConsigneeModal")?.addEventListener("click", { _: Event ->
        closeConsigneeModal()
    })
    
    document.getElementById("cancelConsigneeBtn")?.addEventListener("click", { _: Event ->
        closeConsigneeModal()
    })
    
    // Delete button (only shown in edit mode)
    if (isEdit && mappingId != null) {
        document.getElementById("deleteConsigneeBtn")?.addEventListener("click", { _: Event ->
            if (js("confirm('Are you sure you want to delete this consignee? This action cannot be undone.')").unsafeCast<Boolean>()) {
                deleteMasterConsignee(mappingId)
            }
        })
    }
    
    document.getElementById("consigneeForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveConsignee(mappingId)
    })
    
    // Close on background click
    document.getElementById("consigneeModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "consigneeModal") {
            closeConsigneeModal()
        }
    })
}

fun loadConsigneeDataForEdit(mappingId: Long) {
    window.fetch(apiUrl("booking/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignee data')")
        }
        .then { result: dynamic ->
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            val mapping = mappingsArray.find { (it.id ?: 0).toString() == mappingId.toString() }
            
            if (mapping != null) {
                (document.getElementById("consigneeCountry") as? HTMLInputElement)?.value = mapping.country ?: ""
                (document.getElementById("consigneeClientName") as? HTMLInputElement)?.value = mapping.clientName ?: ""
                (document.getElementById("consigneeName") as? HTMLInputElement)?.value = mapping.consigneeName ?: ""
                (document.getElementById("consigneeAddress") as? HTMLTextAreaElement)?.value = mapping.consigneeAddress ?: ""
                (document.getElementById("consigneePOD") as? HTMLInputElement)?.value = mapping.pod ?: ""
                (document.getElementById("consigneeStockLocation") as? HTMLInputElement)?.value = mapping.stockLocation ?: ""
                (document.getElementById("consigneePOLs") as? HTMLInputElement)?.value = mapping.pols ?: ""
                (document.getElementById("consigneeNotes") as? HTMLTextAreaElement)?.value = mapping.notes ?: ""
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignee data: ${error.toString()}")
            showMessage("Error loading consignee data: ${error.message}", "error")
        }
}

fun closeConsigneeModal() {
    document.getElementById("consigneeModal")?.remove()
}

fun saveConsignee(mappingId: Long?) {
    val country = (document.getElementById("consigneeCountry") as? HTMLInputElement)?.value?.trim() ?: ""
    
    if (country.isEmpty()) {
        showMessage("Country is required", "error")
        return
    }
    
    val consigneeData = js("{}")
    consigneeData.country = country
    consigneeData.clientName = (document.getElementById("consigneeClientName") as? HTMLInputElement)?.value?.trim() ?: null
    consigneeData.consigneeName = (document.getElementById("consigneeName") as? HTMLInputElement)?.value?.trim() ?: null
    consigneeData.consigneeAddress = (document.getElementById("consigneeAddress") as? HTMLTextAreaElement)?.value?.trim() ?: null
    consigneeData.pod = (document.getElementById("consigneePOD") as? HTMLInputElement)?.value?.trim() ?: null
    consigneeData.stockLocation = (document.getElementById("consigneeStockLocation") as? HTMLInputElement)?.value?.trim() ?: null
    consigneeData.pols = (document.getElementById("consigneePOLs") as? HTMLInputElement)?.value?.trim() ?: null
    consigneeData.notes = (document.getElementById("consigneeNotes") as? HTMLTextAreaElement)?.value?.trim() ?: null
    
    val saveButton = document.getElementById("saveConsigneeBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = if (mappingId != null) "Updating..." else "Saving..."
    
    val url = if (mappingId != null) {
        apiUrl("booking/mappings/$mappingId")
    } else {
        apiUrl("booking/mappings/add")
    }
    
    val method = if (mappingId != null) "PUT" else "POST"
    
    val requestInit = js("{}")
    requestInit.method = method
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(consigneeData)
    
    window.fetch(url, requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to save consignee')")
        }
        .then { result: dynamic ->
            if (result.success) {
                showMessage(if (mappingId != null) "Consignee updated successfully" else "Consignee added successfully", "success")
                closeConsigneeModal()
                loadMasterConsignee()
            } else {
                throw js("Error(result.message || 'Failed to save consignee')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error saving consignee: ${error.toString()}")
            showMessage("Error saving consignee: ${error.message}", "error")
        }
        .finally {
            saveButton?.disabled = false
            saveButton?.textContent = if (mappingId != null) "Update Consignee" else "Save Consignee"
        }
}

fun deleteMasterConsignee(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId == null) {
        showMessage("Invalid consignee ID", "error")
        return
    }
    
    if (!js("confirm('Are you sure you want to delete this consignee? This action cannot be undone.')").unsafeCast<Boolean>()) {
        return
    }
    
    val requestInit = js("{}")
    requestInit.method = "DELETE"
    
    window.fetch(apiUrl("booking/mappings/$mappingId"), requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to delete consignee')")
        }
        .then { result: dynamic ->
            if (result.success) {
                showMessage("Consignee deleted successfully", "success")
                closeConsigneeModal()
                loadMasterConsignee()
            } else {
                throw js("Error(result.message || 'Failed to delete consignee')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error deleting consignee: ${error.toString()}")
            showMessage("Error deleting consignee: ${error.message}", "error")
        }
}

fun editMasterConsignee(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId != null) {
        showConsigneeModal(mappingId)
    }
}

fun showMasterCarBrandsPage() {
    window.location.hash = "#/master/car-brands"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="carBrandList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Car Brands</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="carBrandColumnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            
            <!-- Search and Filter Section -->
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="display: flex; gap: 15px; align-items: center; flex-wrap: wrap;">
                    <div style="flex: 1; min-width: 250px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search:</label>
                        <input type="text" id="carBrandFilter" placeholder="Search by brand, car name, or chassis..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                    </div>
                    <div style="display: flex; gap: 10px; align-items: flex-end;">
                        <button id="clearCarBrandFilterBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Clear Filter</button>
                    </div>
                </div>
            </div>
            
            <!-- Action Buttons -->
            <div style="margin-bottom: 20px;">
                <button id="addCarBrandBtn" style="padding: 12px 24px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    ➕ Add New Car Brand
                </button>
            </div>
            
            <!-- Car Brand Table/Cards Container -->
            <div id="carBrandTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading car brand data...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    
    // Load initial data
    loadMasterCarBrands()
    
    // Event listeners
    document.getElementById("addCarBrandBtn")?.addEventListener("click", { _: Event ->
        showAddCarBrandModal()
    })
    
    document.getElementById("clearCarBrandFilterBtn")?.addEventListener("click", { _: Event ->
        val filterInput = document.getElementById("carBrandFilter") as HTMLInputElement?
        filterInput?.value = ""
        loadMasterCarBrands()
    })
    
    // Real-time search filter
    document.getElementById("carBrandFilter")?.addEventListener("input", { _: Event ->
        loadMasterCarBrands()
    })
    
    // Column filter button
    document.getElementById("carBrandColumnFilterBtn")?.addEventListener("click", { _: Event ->
        showCarBrandColumnFilterModal()
    })
    
    // Setup device change listener for Car Brands page
    setupCarBrandDeviceChangeListener()
    
    // Check for device change and reload if needed
    checkCarBrandDeviceChange()
}

/**
 * Check if device type changed for Car Brands page and reload if needed
 */
fun checkCarBrandDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, reload car brands to switch between card/table views
    if (lastCarBrandDeviceType != null && lastCarBrandDeviceType != currentDeviceType) {
        Logger.debug("Car Brands page: Device type changed from $lastCarBrandDeviceType to $currentDeviceType, reloading car brands")
        loadMasterCarBrands()
    }
    
    // Update last device type
    lastCarBrandDeviceType = currentDeviceType
}

/**
 * Setup window resize listener for Car Brands page to detect device changes
 */
fun setupCarBrandDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__carBrandDeviceChangeListener
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
            if (lastCarBrandDeviceType != null && lastCarBrandDeviceType != newDeviceType) {
                // Device changed - reload car brands to switch between card/table views
                Logger.debug("Car Brands page: Device type changed from $lastCarBrandDeviceType to $newDeviceType, reloading")
                
                // If we're on the car brands page, reload to show correct view (cards or table)
                if (window.location.hash.contains("#/master/car-brands")) {
                    loadMasterCarBrands()
                }
            }
            lastCarBrandDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__carBrandDeviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadMasterCarBrands() {
    val tableDiv = document.getElementById("carBrandTable")
    if (tableDiv == null) return
    
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        loadMasterCarBrandsWithCards()
        return
    }
    
    loadMasterCarBrandsWithTable()
}

fun loadMasterCarBrandsWithCards() {
    val tableDiv = document.getElementById("carBrandTable")
    if (tableDiv == null) return
    
    // Get brand filter value
    val brandFilter = (document.getElementById("carBrandFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    // Show loading state
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading car brand data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Load from car brand mappings
    window.fetch(apiUrl("car-brand-mapping/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brands')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load car brands')")
            }
            
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            
            // Sort by ID descending (newest first)
            val mappingsList = mappingsArray.toList()
            val sortedMappings = mappingsList.sortedByDescending { mapping ->
                val id = mapping.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            // Filter by brand, car name, or chassis if filter is set
            val filteredMappings = if (brandFilter.isNotEmpty()) {
                sortedMappings.filter { mapping ->
                    val brand = (mapping.carBrand ?: "").toString().uppercase()
                    val carName = (mapping.carName ?: "").toString().uppercase()
                    val chassis = (mapping.chassis ?: "").toString().uppercase()
                    brand.contains(brandFilter) || carName.contains(brandFilter) || chassis.contains(brandFilter)
                }
            } else {
                sortedMappings
            }
            
            // Store all filtered mappings for pagination
            allCarBrands = filteredMappings
            if (brandFilter.isNotEmpty()) {
                carBrandsCurrentPage = 1
            }
            
            displayCarBrandsAsCards(filteredMappings, brandFilter)
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brand data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun loadMasterCarBrandsWithTable() {
    val tableDiv = document.getElementById("carBrandTable")
    if (tableDiv == null) return
    
    // Get brand filter value
    val brandFilter = (document.getElementById("carBrandFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    // Show loading state
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading car brand data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Load from car brand mappings
    window.fetch(apiUrl("car-brand-mapping/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brands')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load car brands')")
            }
            
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            
            // Sort by ID descending (newest first) so new entries appear at the top
            val mappingsList = mappingsArray.toList()
            val sortedMappings = mappingsList.sortedByDescending { mapping ->
                val id = mapping.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            // Filter by brand, car name, or chassis if filter is set
            val filteredMappings = if (brandFilter.isNotEmpty()) {
                sortedMappings.filter { mapping ->
                    val brand = (mapping.carBrand ?: "").toString().uppercase()
                    val carName = (mapping.carName ?: "").toString().uppercase()
                    val chassis = (mapping.chassis ?: "").toString().uppercase()
                    brand.contains(brandFilter) || carName.contains(brandFilter) || chassis.contains(brandFilter)
                }
            } else {
                sortedMappings
            }
            
            // Store all filtered mappings for pagination
            allCarBrands = filteredMappings
            if (brandFilter.isNotEmpty()) {
                carBrandsCurrentPage = 1 // Reset to first page when filter changes
            }
            
            if (filteredMappings.isEmpty()) {
                val message = if (brandFilter.isNotEmpty()) {
                    "No car brand data found for: $brandFilter"
                } else {
                    "No car brand data found."
                }
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            // Calculate pagination
            val totalPages = kotlin.math.ceil(filteredMappings.size.toDouble() / carBrandsItemsPerPage).toInt()
            val startIndex = (carBrandsCurrentPage - 1) * carBrandsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + carBrandsItemsPerPage, filteredMappings.size)
            val paginatedMappings = filteredMappings.subList(startIndex, endIndex)
            
            // Get selected columns
            val selectedColumns = getSelectedCarBrandColumns()
            val columnLabels = mapOf(
                "id" to "ID",
                "carBrand" to "Car Brand",
                "chassis" to "Chassis",
                "carName" to "Car Name",
                "fuel" to "Fuel",
                "wd" to "WD",
                "shift" to "Transmission",
                "grade" to "Grade",
                "cc" to "CC",
                "door" to "Door"
            )
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="car-brand-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
            """
            
            // Add headers for selected columns only
            for (columnKey in selectedColumns) {
                val label = columnLabels[columnKey] ?: columnKey
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (mapping in paginatedMappings) {
                val id = (mapping.id ?: "").toString()
                val carBrand = (mapping.carBrand ?: "").toString()
                val chassis = (mapping.chassis ?: "").toString()
                val carName = (mapping.carName ?: "").toString()
                val fuel = (mapping.fuel ?: "").toString()
                val wd = (mapping.wd ?: "").toString()
                val shift = (mapping.shift ?: "").toString()
                val grade = (mapping.grade ?: "").toString()
                val cc = (mapping.cc ?: "").toString()
                val door = (mapping.door ?: "").toString()
                
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 8px 12px;">
                            <button onclick="window.editMasterCarBrand($id)" aria-label="Edit" title="Edit"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                </svg>
                            </button>
                        </td>
                """
                
                // Add cells for selected columns only
                for (columnKey in selectedColumns) {
                    val value = when (columnKey) {
                        "id" -> id
                        "carBrand" -> carBrand
                        "chassis" -> chassis
                        "carName" -> carName
                        "fuel" -> fuel
                        "wd" -> wd
                        "shift" -> shift
                        "grade" -> grade
                        "cc" -> cc
                        "door" -> door
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "carBrand" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        "chassis", "carName" -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                        else -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                
                html += """</tr>"""
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
            // Add pagination controls if there are multiple pages
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filteredMappings.size} car brand${if (filteredMappings.size != 1) "s" else ""}${if (brandFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="car-brand-pagination-controls">
                            <button id="carBrandsPrevPage" class="car-brand-pagination-btn" ${if (carBrandsCurrentPage == 1) "disabled" else ""}>
                                Previous
                            </button>
                            <span class="car-brand-pagination-page">Page $carBrandsCurrentPage of $totalPages</span>
                            <button id="carBrandsNextPage" class="car-brand-pagination-btn" ${if (carBrandsCurrentPage >= totalPages) "disabled" else ""}>
                                Next
                            </button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filteredMappings.size} car brand${if (filteredMappings.size != 1) "s" else ""}${if (brandFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            
            tableDiv.innerHTML = html
            
            // Add pagination event listeners
            document.getElementById("carBrandsPrevPage")?.addEventListener("click", { _: Event ->
                if (carBrandsCurrentPage > 1) {
                    carBrandsCurrentPage--
                    loadMasterCarBrands()
                }
            })
            
            document.getElementById("carBrandsNextPage")?.addEventListener("click", { _: Event ->
                val totalPages = kotlin.math.ceil(allCarBrands.size.toDouble() / carBrandsItemsPerPage).toInt()
                if (carBrandsCurrentPage < totalPages) {
                    carBrandsCurrentPage++
                    loadMasterCarBrands()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brand data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun displayCarBrandsAsCards(filteredMappings: List<dynamic>, brandFilter: String) {
    val tableDiv = document.getElementById("carBrandTable")
    if (tableDiv == null) return
    
    if (filteredMappings.isEmpty()) {
        val message = if (brandFilter.isNotEmpty()) {
            "No car brand data found for: $brandFilter"
        } else {
            "No car brand data found."
        }
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                $message
            </div>
        """
        return
    }
    
    // Calculate pagination
    val totalPages = kotlin.math.ceil(filteredMappings.size.toDouble() / carBrandsItemsPerPage).toInt()
    val startIndex = (carBrandsCurrentPage - 1) * carBrandsItemsPerPage
    val endIndex = kotlin.math.min(startIndex + carBrandsItemsPerPage, filteredMappings.size)
    val paginatedMappings = filteredMappings.subList(startIndex, endIndex)
    
    val selectedColumns = getSelectedCarBrandColumns()
    val columnLabels = mapOf(
        "id" to "ID",
        "carBrand" to "Car Brand",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "fuel" to "Fuel",
        "wd" to "WD",
        "shift" to "Transmission",
        "grade" to "Grade",
        "cc" to "CC",
        "door" to "Door"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="car-brand-cards-container">""")
    
    for (mapping in paginatedMappings) {
        val id = (mapping.id ?: "").toString()
        val carBrand = (mapping.carBrand ?: "").toString()
        val chassis = (mapping.chassis ?: "").toString()
        val carName = (mapping.carName ?: "").toString()
        val fuel = (mapping.fuel ?: "").toString()
        val wd = (mapping.wd ?: "").toString()
        val shift = (mapping.shift ?: "").toString()
        val grade = (mapping.grade ?: "").toString()
        val cc = (mapping.cc ?: "").toString()
        val door = (mapping.door ?: "").toString()
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "id" -> id
                "carBrand" -> carBrand
                "chassis" -> chassis
                "carName" -> carName
                "fuel" -> fuel
                "wd" -> wd
                "shift" -> shift
                "grade" -> grade
                "cc" -> cc
                "door" -> door
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                cardFields.append("""
                    <div style="margin-bottom: 8px;">
                        <span style="font-weight: 600; color: #666; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">$label:</span>
                        <div style="color: #333; font-size: 14px; margin-top: 2px;">$value</div>
                    </div>
                """)
            }
        }
        
        cardsHTML.append("""
            <div class="car-brand-card">
                <div class="card-header">
                    <button class="card-edit-btn" onclick="window.editMasterCarBrand($id)" aria-label="Edit" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    <div class="card-title">${if (carBrand.isNotEmpty()) carBrand else "Car Brand #$id"}</div>
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
                <button id="carBrandsPrevPage" class="pagination-btn" ${if (carBrandsCurrentPage == 1) "disabled" else ""}>
                    Previous
                </button>
                <span class="pagination-page">Page $carBrandsCurrentPage of $totalPages</span>
                <button id="carBrandsNextPage" class="pagination-btn" ${if (carBrandsCurrentPage >= totalPages) "disabled" else ""}>
                    Next
                </button>
            </div>
        """)
    } else {
        cardsHTML.append("""
            <div style="padding: 16px; text-align: center; color: #6b7280; font-size: 14px;">
                Total: ${filteredMappings.size} car brand${if (filteredMappings.size != 1) "s" else ""}${if (brandFilter.isNotEmpty()) " (filtered)" else ""}
            </div>
        """)
    }
    
    tableDiv.innerHTML = cardsHTML.toString()
    
    // Add pagination event listeners
    document.getElementById("carBrandsPrevPage")?.addEventListener("click", { _: Event ->
        if (carBrandsCurrentPage > 1) {
            carBrandsCurrentPage--
            loadMasterCarBrands()
        }
    })
    
    document.getElementById("carBrandsNextPage")?.addEventListener("click", { _: Event ->
        val totalPages = kotlin.math.ceil(allCarBrands.size.toDouble() / carBrandsItemsPerPage).toInt()
        if (carBrandsCurrentPage < totalPages) {
            carBrandsCurrentPage++
            loadMasterCarBrands()
        }
    })
}

fun showCarBrandColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("carBrandColumnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "carBrandColumnFilterModal"
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
    
    val selectedColumnsList = getSelectedCarBrandColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeCarBrandColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="carBrandSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="carBrandColumnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetCarBrandColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyCarBrandColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes
    val columnLabels = mapOf(
        "id" to "ID",
        "carBrand" to "Car Brand",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "fuel" to "Fuel",
        "wd" to "WD",
        "shift" to "Transmission",
        "grade" to "Grade",
        "cc" to "CC",
        "door" to "Door"
    )
    
    val checkboxesDiv = document.getElementById("carBrandColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val checkbox = document.createElement("div")
        val checkboxStyle = checkbox.asDynamic().style
        checkboxStyle.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "carBrandCol_$key"
        input.setAttribute("data-column", key)
        input.checked = selectedColumns.contains(key)
        input.addEventListener("change", { _: Event ->
            updateCarBrandColumnSelection()
        })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "carBrandCol_$key"
        labelEl.textContent = label
        val labelStyle = labelEl.asDynamic().style
        labelStyle.cssText = "cursor: pointer; margin: 0;"
        checkbox.appendChild(input)
        checkbox.appendChild(labelEl)
        checkboxesDiv?.appendChild(checkbox)
    }
    
    // Update selection count initially
    updateCarBrandColumnSelection()
    
    // Add event listeners
    document.getElementById("closeCarBrandColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("carBrandColumnFilterModal")?.remove()
    })
    document.getElementById("resetCarBrandColumns")?.addEventListener("click", { _: Event ->
        val deviceType = getDeviceType()
        val defaultColumns = getDefaultCarBrandColumnsForDevice(deviceType)
        columnLabels.keys.forEach { col ->
            val checkbox = document.getElementById("carBrandCol_$col") as? HTMLInputElement
            checkbox?.checked = defaultColumns.contains(col)
        }
        updateCarBrandColumnSelection()
    })
    document.getElementById("applyCarBrandColumns")?.addEventListener("click", { _: Event ->
        applyCarBrandColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "carBrandColumnFilterModal") {
            document.getElementById("carBrandColumnFilterModal")?.remove()
        }
    })
}

fun updateCarBrandColumnSelection() {
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val checkboxes = document.querySelectorAll("#carBrandColumnCheckboxes input[type='checkbox']")
    var selectedCount = 0
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedCount++
        }
    }
    
    val countSpan = document.getElementById("carBrandSelectedCount")
    countSpan?.textContent = "$selectedCount"
    
    // Disable/enable checkboxes based on max limit
    if (selectedCount >= maxColumns) {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            if (!checkbox.checked) {
                checkbox.disabled = true
            }
        }
    } else {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.disabled = false
        }
    }
}

fun applyCarBrandColumnChanges() {
    val checkboxes = document.querySelectorAll("#carBrandColumnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableListOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val columnKey = checkbox.getAttribute("data-column") ?: ""
            if (columnKey.isNotEmpty()) {
                selectedColumns.add(columnKey)
            }
        }
    }
    
    // Save to localStorage
    safeLocalStorageSet("selectedCarBrandColumns", JSON.stringify(selectedColumns.toTypedArray()))
    
    // Close modal
    document.getElementById("carBrandColumnFilterModal")?.remove()
    
    // Reload car brands to apply changes
    loadMasterCarBrands()
}

fun showAddCarBrandModal() {
    showCarBrandModal(null)
}

fun showCarBrandModal(mappingId: Long?) {
    val isEdit = mappingId != null
    val title = if (isEdit) "Edit Car Brand" else "Add New Car Brand"
    
    val modalHtml = """
        <div id="carBrandModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div id="carBrandModalContent" style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeCarBrandModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="carBrandForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Car Brand <span style="color: #ef4444;">*</span></label>
                            <input type="text" id="carBrandBrand" required style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Chassis</label>
                                <input type="text" id="carBrandChassis" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Car Name</label>
                                <input type="text" id="carBrandCarName" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Fuel</label>
                                <div style="position: relative; width: 100%;">
                                    <input type="text" id="carBrandFuelInput" placeholder="Select or type Fuel" style="width: 100%; padding: 10px 40px 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;" autocomplete="off" onfocus="this.select();">
                                    <select id="carBrandFuel" style="position: absolute; top: 0; right: 0; width: 40px; height: 100%; border: none; border-left: 1px solid #d1d5db; background: #f5f5f5; border-radius: 0 6px 6px 0; appearance: none; -webkit-appearance: none; -moz-appearance: none; padding: 0; text-align: center; font-size: 14px; z-index: 1; font-weight: bold; color: #666; opacity: 0; pointer-events: none;" aria-hidden="true" onchange="if (typeof syncComboboxInput === 'function') syncComboboxInput('carBrandFuel');">
                                        <option value="">▼</option>
                                        <option value="GASOLINE">GASOLINE</option>
                                        <option value="DIESEL">DIESEL</option>
                                        <option value="HYBRID">HYBRID</option>
                                        <option value="CNG">CNG</option>
                                        <option value="EV">EV</option>
                                        <option value="HYDROGEN">HYDROGEN</option>
                                        <option value="PHEV">PHEV</option>
                                    </select>
                                    <div id="carBrandFuelDropdownBtn" role="button" tabindex="0" aria-label="Open fuel list" style="position: absolute; top: 0; right: 0; width: 40px; height: 100%; border: none; border-left: 1px solid #d1d5db; background: #f5f5f5; cursor: pointer; border-radius: 0 6px 6px 0; z-index: 3; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: bold; color: #666; user-select: none;" onmousedown="event.preventDefault(); event.stopPropagation(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandFuel');" onclick="event.preventDefault(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandFuel');" onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandFuel'); }">▼</div>
                                </div>
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">WD</label>
                                <input type="number" id="carBrandWd" min="0" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Transmission</label>
                                <div style="position: relative; width: 100%;">
                                    <input type="text" id="carBrandShiftInput" placeholder="Select or type Transmission" style="width: 100%; padding: 10px 40px 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;" autocomplete="off" onfocus="this.select();">
                                    <select id="carBrandShift" style="position: absolute; top: 0; right: 0; width: 40px; height: 100%; border: none; border-left: 1px solid #d1d5db; background: #f5f5f5; border-radius: 0 6px 6px 0; appearance: none; -webkit-appearance: none; -moz-appearance: none; padding: 0; text-align: center; font-size: 14px; z-index: 1; font-weight: bold; color: #666; opacity: 0; pointer-events: none;" aria-hidden="true" onchange="if (typeof syncComboboxInput === 'function') syncComboboxInput('carBrandShift');">
                                        <option value="">▼</option>
                                        <option value="AT">AT</option>
                                        <option value="MT">MT</option>
                                        <option value="6F">6F</option>
                                        <option value="5F">5F</option>
                                    </select>
                                    <div id="carBrandShiftDropdownBtn" role="button" tabindex="0" aria-label="Open transmission list" style="position: absolute; top: 0; right: 0; width: 40px; height: 100%; border: none; border-left: 1px solid #d1d5db; background: #f5f5f5; cursor: pointer; border-radius: 0 6px 6px 0; z-index: 3; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: bold; color: #666; user-select: none;" onmousedown="event.preventDefault(); event.stopPropagation(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandShift');" onclick="event.preventDefault(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandShift');" onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandShift'); }">▼</div>
                                </div>
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Grade</label>
                                <input type="text" id="carBrandGrade" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">CC</label>
                                <input type="number" id="carBrandCc" min="0" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Door</label>
                                <input type="number" id="carBrandDoor" min="0" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div class="car-brand-modal-actions">
                            <button type="button" id="cancelCarBrandBtn" class="car-brand-modal-btn car-brand-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteCarBrandBtn" class="car-brand-modal-btn car-brand-modal-btn-delete">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveCarBrandBtn" class="car-brand-modal-btn car-brand-modal-btn-save">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    
    // Load data if editing
    if (isEdit && mappingId != null) {
        loadCarBrandDataForEdit(mappingId)
    }
    
    // Event listeners
    document.getElementById("closeCarBrandModal")?.addEventListener("click", { _: Event ->
        closeCarBrandModal()
    })
    
    document.getElementById("cancelCarBrandBtn")?.addEventListener("click", { _: Event ->
        closeCarBrandModal()
    })
    
    // Delete button (only shown in edit mode)
    if (isEdit && mappingId != null) {
        document.getElementById("deleteCarBrandBtn")?.addEventListener("click", { _: Event ->
            if (js("confirm('Are you sure you want to delete this car brand mapping? This action cannot be undone.')").unsafeCast<Boolean>()) {
                deleteMasterCarBrand(mappingId)
            }
        })
    }
    
    document.getElementById("carBrandForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveCarBrand(mappingId)
    })
    
    // Close on background click
    document.getElementById("carBrandModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "carBrandModal") {
            closeCarBrandModal()
        }
    })
}

fun closeCarBrandModal() {
    document.getElementById("carBrandModal")?.remove()
}

fun loadCarBrandDataForEdit(mappingId: Long) {
    window.fetch(apiUrl("car-brand-mapping/mappings/$mappingId"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brand data')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                val data = result.data ?: js("{}")
                (document.getElementById("carBrandBrand") as? HTMLInputElement)?.value = (data.carBrand ?: "").toString()
                (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value = (data.chassis ?: "").toString()
                (document.getElementById("carBrandCarName") as? HTMLInputElement)?.value = (data.carName ?: "").toString()
                val fuelVal = (data.fuel ?: "").toString()
                (document.getElementById("carBrandFuelInput") as? HTMLInputElement)?.value = fuelVal
                (document.getElementById("carBrandFuel") as? HTMLSelectElement)?.value = fuelVal
                (document.getElementById("carBrandWd") as? HTMLInputElement)?.value = (data.wd ?: "").toString()
                val shiftVal = (data.shift ?: "").toString()
                (document.getElementById("carBrandShiftInput") as? HTMLInputElement)?.value = shiftVal
                (document.getElementById("carBrandShift") as? HTMLSelectElement)?.value = shiftVal
                (document.getElementById("carBrandCc") as? HTMLInputElement)?.value = (data.cc ?: "").toString()
                (document.getElementById("carBrandDoor") as? HTMLInputElement)?.value = (data.door ?: "").toString()
                (document.getElementById("carBrandGrade") as? HTMLInputElement)?.value = (data.grade ?: "").toString()
            } else {
                throw js("Error(result.message || 'Failed to load car brand data')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brand data: ${error.toString()}")
            showMessage("Error loading car brand data: ${error.message}", "error")
        }
}

fun saveCarBrand(mappingId: Long?) {
    val carBrand = (document.getElementById("carBrandBrand") as? HTMLInputElement)?.value?.trim() ?: ""
    
    if (carBrand.isEmpty()) {
        showMessage("Car Brand is required", "error")
        return
    }
    
    val carBrandData = js("{}")
    carBrandData.carBrand = carBrand
    carBrandData.chassis = (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value?.trim() ?: null
    carBrandData.carName = (document.getElementById("carBrandCarName") as? HTMLInputElement)?.value?.trim() ?: null
    carBrandData.fuel = (document.getElementById("carBrandFuelInput") as? HTMLInputElement)?.value?.trim()?.takeIf { it.isNotEmpty() } ?: null
    carBrandData.wd = (document.getElementById("carBrandWd") as? HTMLInputElement)?.value?.trim()?.takeIf { it.isNotEmpty() } ?: null
    carBrandData.shift = (document.getElementById("carBrandShiftInput") as? HTMLInputElement)?.value?.trim()?.takeIf { it.isNotEmpty() } ?: null
    carBrandData.grade = (document.getElementById("carBrandGrade") as? HTMLInputElement)?.value?.trim() ?: null
    carBrandData.cc = (document.getElementById("carBrandCc") as? HTMLInputElement)?.value?.toIntOrNull()
    carBrandData.door = (document.getElementById("carBrandDoor") as? HTMLInputElement)?.value?.toIntOrNull()
    
    val saveButton = document.getElementById("saveCarBrandBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = if (mappingId != null) "Updating..." else "Saving..."
    
    val url = if (mappingId != null) {
        apiUrl("car-brand-mapping/mappings/$mappingId")
    } else {
        apiUrl("car-brand-mapping/mappings")
    }
    
    val method = if (mappingId != null) "PUT" else "POST"
    
    val requestInit = js("{}")
    requestInit.method = method
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(carBrandData)
    
    Logger.debug("[CAR BRAND] Sending $method request to: $url")
    Logger.debug("[CAR BRAND] Request body: ${requestInit.body}")
    
    window.fetch(url, requestInit)
        .then { response: dynamic ->
            Logger.debug("[CAR BRAND] Response status: ${response.status}")
            if (response.ok) {
                response.json().then { result: dynamic ->
                        Logger.debug("[CAR BRAND] Processing result")
                    val success = result.success as? Boolean ?: false
                    if (success) {
                        showMessage(if (mappingId != null) "Car brand updated successfully" else "Car brand added successfully", "success")
                        closeCarBrandModal()
                        loadMasterCarBrands()
                        
                        // Trigger localStorage event to notify other tabs to refresh chassis dropdown
                        // This allows the chassis dropdown on Add/Edit Purchase pages to auto-update
                        val chassisValue = carBrandData.chassis as? String
                        if (chassisValue != null && chassisValue.isNotBlank()) {
                            try {
                                val timestamp = js("Date.now()").toString()
                                safeLocalStorageSet("chassisUpdated", timestamp)
                                // Also trigger a custom event for same-tab communication
                                val event = js("new CustomEvent('chassisUpdated', { detail: { chassis: chassisValue, timestamp: timestamp } })")
                                window.dispatchEvent(event)
                                Logger.debug("✅ Triggered chassis update event for chassis: $chassisValue")
                            } catch (e: dynamic) {
                                Logger.warn("⚠️ Failed to trigger chassis update event: ${e.toString()}")
                            }
                        }
                        
                        // Trigger localStorage event to notify other tabs to refresh brand dropdown
                        // This allows the brand dropdown on Add/Edit Purchase pages to auto-update
                        try {
                            val timestamp = js("Date.now()").toString()
                            safeLocalStorageSet("brandUpdated", timestamp)
                            // Also trigger a custom event for same-tab communication
                            val brandEvent = js("new CustomEvent('brandUpdated', { detail: { brand: carBrand, timestamp: timestamp } })")
                            window.dispatchEvent(brandEvent)
                            Logger.debug("✅ Triggered brand update event for brand: $carBrand")
                        } catch (e: dynamic) {
                            Logger.warn("⚠️ Failed to trigger brand update event: ${e.toString()}")
                        }
                    } else {
                        val errorMsg = (result.message as? String) ?: "Failed to save car brand"
                        Logger.error("[CAR BRAND] Save failed: $errorMsg")
                        showMessage("Error saving car brand: $errorMsg", "error")
                    }
                }
            } else {
                response.text().then { errorText: String ->
                    Logger.error("[CAR BRAND] Error response: $errorText")
                    showMessage("Error saving car brand: $errorText", "error")
                }
            }
        }
        .catch { error: dynamic ->
            Logger.error("[CAR BRAND] Error saving car brand: ${error.toString()}")
            showMessage("Error saving car brand: ${error.message}", "error")
        }
        .finally {
            saveButton?.disabled = false
            saveButton?.textContent = if (mappingId != null) "Update Car Brand" else "Save Car Brand"
        }
}

fun deleteMasterCarBrand(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId == null) {
        showMessage("Invalid car brand ID", "error")
        return
    }
    
    // Confirmation is already checked in the event listener, so no need to check again here
    
    val requestInit = js("{}")
    requestInit.method = "DELETE"
    
    window.fetch(apiUrl("car-brand-mapping/mappings/$mappingId"), requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to delete car brand')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                showMessage("Car brand deleted successfully", "success")
                closeCarBrandModal()
                loadMasterCarBrands()
                
                // Trigger localStorage event to notify other tabs to refresh chassis dropdown
                try {
                    val timestamp = js("Date.now()").toString()
                    safeLocalStorageSet("chassisUpdated", timestamp)
                    // Also trigger a custom event for same-tab communication
                    val event = js("new CustomEvent('chassisUpdated', { detail: { timestamp: timestamp } })")
                    window.dispatchEvent(event)
                    Logger.debug("✅ Triggered chassis update event after deletion")
                } catch (e: dynamic) {
                    Logger.warn("⚠️ Failed to trigger chassis update event: ${e.toString()}")
                }
                
                // Trigger localStorage event to notify other tabs to refresh brand dropdown
                try {
                    val timestamp = js("Date.now()").toString()
                    safeLocalStorageSet("brandUpdated", timestamp)
                    // Also trigger a custom event for same-tab communication
                    val brandEvent = js("new CustomEvent('brandUpdated', { detail: { timestamp: timestamp } })")
                    window.dispatchEvent(brandEvent)
                    Logger.debug("✅ Triggered brand update event after deletion")
                } catch (e: dynamic) {
                    Logger.warn("⚠️ Failed to trigger brand update event: ${e.toString()}")
                }
            } else {
                throw js("Error(result.message || 'Failed to delete car brand')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error deleting car brand: ${error.toString()}")
            showMessage("Error deleting car brand: ${error.message}", "error")
        }
}

fun editMasterCarBrand(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId != null) {
        showCarBrandModal(mappingId)
    }
}

// Placeholder functions for other master list pages
fun showMasterCountriesPage() {
    window.location.hash = "#/master/country"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="countryList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Country</h2>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Country:</label>
                    <input type="text" id="countryFilter" placeholder="Type country name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="countryTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading countries...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterCountries()
    
    document.getElementById("countryFilter")?.addEventListener("input", { _: Event ->
        loadMasterCountries()
    })
}

fun loadMasterCountries() {
    val tableDiv = document.getElementById("countryTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("countryFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading countries...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    window.fetch(apiUrl("purchases/countries"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load countries')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allCountries = filtered
            if (searchFilter.isNotEmpty()) countriesCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No countries found for: $searchFilter" else "No countries found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val selectedColumns = getSelectedCountryColumns()
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / countriesItemsPerPage).toInt()
            val startIndex = (countriesCurrentPage - 1) * countriesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + countriesItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            val columnLabels = mapOf("id" to "ID", "country" to "Country")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="country-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
            """
            for (col in selectedColumns) {
                val label = columnLabels[col] ?: col
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, countryName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> rowNum.toString()
                        "country" -> countryName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "country" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                html += """</tr>"""
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} countr${if (filtered.size != 1) "ies" else "y"}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="countriesPrevPage" class="consignee-pagination-btn" ${if (countriesCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $countriesCurrentPage of $totalPages</span>
                            <button id="countriesNextPage" class="consignee-pagination-btn" ${if (countriesCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} countr${if (filtered.size != 1) "ies" else "y"}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html
            
            document.getElementById("countriesPrevPage")?.addEventListener("click", { _: Event ->
                if (countriesCurrentPage > 1) {
                    countriesCurrentPage--
                    loadMasterCountries()
                }
            })
            document.getElementById("countriesNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allCountries.size.toDouble() / countriesItemsPerPage).toInt()
                if (countriesCurrentPage < totalP) {
                    countriesCurrentPage++
                    loadMasterCountries()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading countries: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading countries</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showCountryColumnFilterModal() {
    document.getElementById("countryColumnFilterModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "countryColumnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val selectedColumnsList = getSelectedCountryColumns()
    val selectedSet = selectedColumnsList.toSet()
    val columnLabels = mapOf("id" to "ID", "country" to "Country")
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h3 style="margin: 0; color: #333;">Select Columns to Display</h3>
                <button id="closeCountryColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666;">&times;</button>
            </div>
            <div id="countryColumnCheckboxes" style="display: grid; gap: 12px; margin-bottom: 20px;"></div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetCountryColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyCountryColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    val checkboxesDiv = document.getElementById("countryColumnCheckboxes")!!
    columnLabels.forEach { (key, label) ->
        val div = document.createElement("div")
        div.asDynamic().style.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "countryCol_$key"
        input.setAttribute("data-column", key)
        input.checked = key in selectedSet
        div.appendChild(input)
        val lbl = document.createElement("label")
        lbl.setAttribute("for", "countryCol_$key")
        lbl.textContent = label
        div.appendChild(lbl)
        checkboxesDiv.appendChild(div)
    }
    document.getElementById("closeCountryColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("countryColumnFilterModal")?.remove()
    })
    document.getElementById("resetCountryColumns")?.addEventListener("click", { _: Event ->
        safeLocalStorageSet("selectedCountryColumns", JSON.stringify(getDefaultCountryColumnsForDevice(getDeviceType()).toTypedArray()))
        document.getElementById("countryColumnFilterModal")?.remove()
        loadMasterCountries()
    })
    document.getElementById("applyCountryColumns")?.addEventListener("click", { _: Event ->
        val inputs = document.querySelectorAll("#countryColumnCheckboxes input[type=checkbox]:checked")
        val selected = (0 until inputs.length).map { (inputs.item(it) as HTMLInputElement).getAttribute("data-column") ?: "" }.filter { it.isNotEmpty() }
        if (selected.isNotEmpty()) {
            safeLocalStorageSet("selectedCountryColumns", JSON.stringify(selected.toTypedArray()))
        }
        document.getElementById("countryColumnFilterModal")?.remove()
        loadMasterCountries()
    })
}

fun editMasterCountry(countryName: dynamic) {
    js("alert('Edit Country - Coming soon')")
}

fun showAddCountryModal() {
    js("alert('Add Country - Coming soon')")
}

fun showMasterSuppliersPage() {
    window.location.hash = "#/master/supplier"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="supplierList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Supplier</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="supplierColumnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            
            <!-- Search and Filter Section -->
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="display: flex; gap: 15px; align-items: center; flex-wrap: wrap;">
                    <div style="flex: 1; min-width: 250px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Supplier Name:</label>
                        <input type="text" id="supplierFilter" placeholder="Type supplier name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                    </div>
                    <div style="display: flex; gap: 10px; align-items: flex-end;">
                        <button id="clearSupplierFilterBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Clear Filter</button>
                    </div>
                </div>
            </div>
            
            <!-- Action Buttons -->
            <div style="margin-bottom: 20px;">
                <button id="addSupplierBtn" style="padding: 12px 24px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    ➕ Add New Supplier
                </button>
            </div>
            
            <!-- Supplier Table/Cards Container -->
            <div id="supplierTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading supplier data...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    
    // Load initial data
    loadMasterSuppliers()
    
    // Event listeners
    document.getElementById("addSupplierBtn")?.addEventListener("click", { _: Event ->
        showAddSupplierModal()
    })
    
    document.getElementById("supplierColumnFilterBtn")?.addEventListener("click", { _: Event ->
        showSupplierColumnFilterModal()
    })
    
    document.getElementById("clearSupplierFilterBtn")?.addEventListener("click", { _: Event ->
        val filterInput = document.getElementById("supplierFilter") as HTMLInputElement?
        filterInput?.value = ""
        loadMasterSuppliers()
    })
    
    // Real-time search filter
    document.getElementById("supplierFilter")?.addEventListener("input", { _: Event ->
        loadMasterSuppliers()
    })
    
    // Setup device change listener for Supplier page
    setupSupplierDeviceChangeListener()
    
    // Check for device change and reload if needed
    checkSupplierDeviceChange()
}

/**
 * Check if device type changed for Supplier page and reload if needed
 */
fun checkSupplierDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, reload suppliers to switch between card/table views
    if (lastSupplierDeviceType != null && lastSupplierDeviceType != currentDeviceType) {
        Logger.debug("Supplier page: Device type changed from $lastSupplierDeviceType to $currentDeviceType, reloading suppliers")
        loadMasterSuppliers()
    }
    
    // Update last device type
    lastSupplierDeviceType = currentDeviceType
}

/**
 * Setup window resize listener for Supplier page to detect device changes
 */
fun setupSupplierDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__supplierDeviceChangeListener
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
            if (lastSupplierDeviceType != null && lastSupplierDeviceType != newDeviceType) {
                // Device changed - reload suppliers to switch between card/table views
                Logger.debug("Supplier page: Device type changed from $lastSupplierDeviceType to $newDeviceType, reloading")
                
                // If we're on the supplier page, reload to show correct view (cards or table)
                if (window.location.hash.contains("#/master/supplier")) {
                    loadMasterSuppliers()
                }
            }
            lastSupplierDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__supplierDeviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadMasterSuppliers() {
    val tableDiv = document.getElementById("supplierTable")
    if (tableDiv == null) return
    
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        loadMasterSuppliersWithCards()
        return
    }
    
    loadMasterSuppliersWithTable()
}

fun loadMasterSuppliersWithCards() {
    val tableDiv = document.getElementById("supplierTable")
    if (tableDiv == null) return
    
    // Get supplier filter value
    val supplierFilter = (document.getElementById("supplierFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    // Show loading state
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading supplier data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Load from rixo prices
    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load suppliers')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load suppliers')")
            }
            
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            
            // Sort by ID descending (newest first)
            val pricesList = pricesArray.toList()
            val sortedPrices = pricesList.sortedByDescending { price ->
                val id = price.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            // Filter by supplier name if filter is set
            val filteredPrices = if (supplierFilter.isNotEmpty()) {
                sortedPrices.filter { price ->
                    val supplierName = (price.auctionHouse ?: "").toString().uppercase()
                    supplierName.contains(supplierFilter)
                }
            } else {
                sortedPrices
            }
            
            // Store all filtered prices for pagination
            allSuppliers = filteredPrices
            if (supplierFilter.isNotEmpty()) {
                suppliersCurrentPage = 1
            }
            
            displaySuppliersAsCards(filteredPrices, supplierFilter)
        }
        .catch { error: dynamic ->
            Logger.error("Error loading suppliers: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading supplier data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun displaySuppliersAsCards(filteredPrices: List<dynamic>, supplierFilter: String) {
    val tableDiv = document.getElementById("supplierTable")
    if (tableDiv == null) return
    
    if (filteredPrices.isEmpty()) {
        val message = if (supplierFilter.isNotEmpty()) {
            "No supplier data found for: $supplierFilter"
        } else {
            "No supplier data found."
        }
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                $message
            </div>
        """
        return
    }
    
    // Calculate pagination
    val totalPages = kotlin.math.ceil(filteredPrices.size.toDouble() / suppliersItemsPerPage).toInt()
    val startIndex = (suppliersCurrentPage - 1) * suppliersItemsPerPage
    val endIndex = kotlin.math.min(startIndex + suppliersItemsPerPage, filteredPrices.size)
    val paginatedPrices = filteredPrices.subList(startIndex, endIndex)
    
    val selectedColumns = getSelectedSupplierColumns()
    val columnLabels = mapOf(
        "id" to "ID",
        "supplierName" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "rixoCompany" to "Rixo Company",
        "venueId" to "Venue ID",
        "rixoPrice" to "Rixo Price",
        "typeOfVehicle" to "Vehicle type"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="supplier-cards-container">""")
    
    for (price in paginatedPrices) {
        val id = (price.id ?: "").toString()
        val supplierName = (price.auctionHouse ?: "").toString()
        val stockLocation = (price.stockLocation ?: "").toString()
        val rixoCompany = (price.rixoCompany ?: "").toString()
        val venueId = (price.venueId ?: "").toString()
        val rixoPrice = (price.rixoPrice ?: "").toString()
        val typeOfVehicle = (price.shipmentSize ?: "").toString()
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "id" -> id
                "supplierName" -> supplierName
                "stockLocation" -> stockLocation
                "rixoCompany" -> rixoCompany
                "venueId" -> venueId
                "rixoPrice" -> rixoPrice
                "typeOfVehicle" -> typeOfVehicle
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                cardFields.append("""
                    <div style="margin-bottom: 8px;">
                        <span style="font-weight: 600; color: #666; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">$label:</span>
                        <div style="color: #333; font-size: 14px; margin-top: 2px;">$value</div>
                    </div>
                """)
            }
        }
        
        cardsHTML.append("""
            <div class="supplier-card">
                <div class="card-header">
                    <button class="card-edit-btn" onclick="window.editMasterSupplier($id)" aria-label="Edit" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    <div class="card-title">${if (supplierName.isNotEmpty()) supplierName else "Supplier #$id"}</div>
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
                <button id="suppliersPrevPage" class="pagination-btn" ${if (suppliersCurrentPage == 1) "disabled" else ""}>
                    Previous
                </button>
                <span class="pagination-page">Page $suppliersCurrentPage of $totalPages</span>
                <button id="suppliersNextPage" class="pagination-btn" ${if (suppliersCurrentPage >= totalPages) "disabled" else ""}>
                    Next
                </button>
            </div>
        """)
    } else {
        cardsHTML.append("""
            <div style="padding: 16px; text-align: center; color: #6b7280; font-size: 14px;">
                Total: ${filteredPrices.size} supplier${if (filteredPrices.size != 1) "s" else ""}${if (supplierFilter.isNotEmpty()) " (filtered)" else ""}
            </div>
        """)
    }
    
    tableDiv.innerHTML = cardsHTML.toString()
    
    // Add pagination event listeners
    document.getElementById("suppliersPrevPage")?.addEventListener("click", { _: Event ->
        if (suppliersCurrentPage > 1) {
            suppliersCurrentPage--
            loadMasterSuppliers()
        }
    })
    
    document.getElementById("suppliersNextPage")?.addEventListener("click", { _: Event ->
        val totalPages = kotlin.math.ceil(allSuppliers.size.toDouble() / suppliersItemsPerPage).toInt()
        if (suppliersCurrentPage < totalPages) {
            suppliersCurrentPage++
            loadMasterSuppliers()
        }
    })
}

fun loadMasterSuppliersWithTable() {
    val tableDiv = document.getElementById("supplierTable")
    if (tableDiv == null) return
    
    // Get supplier filter value
    val supplierFilter = (document.getElementById("supplierFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    // Show loading state
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading supplier data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Load from rixo prices
    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load suppliers')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load suppliers')")
            }
            
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            
            // Sort by ID descending (newest first) so new entries appear at the top
            val pricesList = pricesArray.toList()
            val sortedPrices = pricesList.sortedByDescending { price ->
                val id = price.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            // Filter by supplier name if filter is set
            val filteredPrices = if (supplierFilter.isNotEmpty()) {
                sortedPrices.filter { price ->
                    val supplierName = (price.auctionHouse ?: "").toString().uppercase()
                    supplierName.contains(supplierFilter)
                }
            } else {
                sortedPrices
            }
            
            // Store all filtered prices for pagination
            allSuppliers = filteredPrices
            if (supplierFilter.isNotEmpty()) {
                suppliersCurrentPage = 1 // Reset to first page when filter changes
            }
            
            if (filteredPrices.isEmpty()) {
                val message = if (supplierFilter.isNotEmpty()) {
                    "No supplier data found for: $supplierFilter"
                } else {
                    "No supplier data found."
                }
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            // Calculate pagination
            val totalPages = kotlin.math.ceil(filteredPrices.size.toDouble() / suppliersItemsPerPage).toInt()
            val startIndex = (suppliersCurrentPage - 1) * suppliersItemsPerPage
            val endIndex = kotlin.math.min(startIndex + suppliersItemsPerPage, filteredPrices.size)
            val paginatedPrices = filteredPrices.subList(startIndex, endIndex)
            
            // Get selected columns
            val selectedColumns = getSelectedSupplierColumns()
            val columnLabels = mapOf(
                "id" to "ID",
                "supplierName" to "Supplier Name",
                "stockLocation" to "Stock Location",
                "rixoCompany" to "Rixo Company",
                "venueId" to "Venue ID",
                "rixoPrice" to "Rixo Price",
                "typeOfVehicle" to "Vehicle type"
            )
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="supplier-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
            """
            
            // Add headers for selected columns only
            for (columnKey in selectedColumns) {
                val label = columnLabels[columnKey] ?: columnKey
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (price in paginatedPrices) {
                val id = (price.id ?: "").toString()
                val supplierName = (price.auctionHouse ?: "").toString()
                val stockLocation = (price.stockLocation ?: "").toString()
                val rixoCompany = (price.rixoCompany ?: "").toString()
                val venueId = (price.venueId ?: "").toString()
                val rixoPrice = (price.rixoPrice ?: "").toString()
                val typeOfVehicle = (price.shipmentSize ?: "").toString()
                
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 8px 12px;">
                            <button onclick="window.editMasterSupplier($id)" aria-label="Edit" title="Edit"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                </svg>
                            </button>
                        </td>
                """
                
                // Add cells for selected columns only
                for (columnKey in selectedColumns) {
                    val value = when (columnKey) {
                        "id" -> id.toString()
                        "supplierName" -> supplierName.toString()
                        "stockLocation" -> stockLocation.toString()
                        "rixoCompany" -> rixoCompany.toString()
                        "venueId" -> venueId.toString()
                        "rixoPrice" -> rixoPrice.toString()
                        "typeOfVehicle" -> typeOfVehicle.toString()
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                        "id", "venueId", "rixoPrice", "typeOfVehicle" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "supplierName" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                
                html += """</tr>"""
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
            // Add pagination controls if there are multiple pages
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filteredPrices.size} supplier${if (filteredPrices.size != 1) "s" else ""}${if (supplierFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="supplier-pagination-controls">
                            <button id="suppliersPrevPage" class="supplier-pagination-btn" ${if (suppliersCurrentPage == 1) "disabled" else ""}>
                                Previous
                            </button>
                            <span class="supplier-pagination-page">Page $suppliersCurrentPage of $totalPages</span>
                            <button id="suppliersNextPage" class="supplier-pagination-btn" ${if (suppliersCurrentPage >= totalPages) "disabled" else ""}>
                                Next
                            </button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filteredPrices.size} supplier${if (filteredPrices.size != 1) "s" else ""}${if (supplierFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            
            tableDiv.innerHTML = html
            
            // Add pagination event listeners
            document.getElementById("suppliersPrevPage")?.addEventListener("click", { _: Event ->
                if (suppliersCurrentPage > 1) {
                    suppliersCurrentPage--
                    loadMasterSuppliers()
                }
            })
            
            document.getElementById("suppliersNextPage")?.addEventListener("click", { _: Event ->
                val totalPages = kotlin.math.ceil(allSuppliers.size.toDouble() / suppliersItemsPerPage).toInt()
                if (suppliersCurrentPage < totalPages) {
                    suppliersCurrentPage++
                    loadMasterSuppliers()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading suppliers: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading supplier data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun showSupplierColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("supplierColumnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "supplierColumnFilterModal"
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
    
    val selectedColumnsList = getSelectedSupplierColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeSupplierColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="supplierSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="supplierColumnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetSupplierColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applySupplierColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes
    val columnLabels = mapOf(
        "id" to "ID",
        "supplierName" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "rixoCompany" to "Rixo Company",
        "venueId" to "Venue ID",
        "rixoPrice" to "Rixo Price",
        "typeOfVehicle" to "Vehicle type"
    )
    
    val checkboxesDiv = document.getElementById("supplierColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val checkbox = document.createElement("div")
        val checkboxStyle = checkbox.asDynamic().style
        checkboxStyle.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "supplierCol_$key"
        input.setAttribute("data-column", key)
        input.checked = selectedColumns.contains(key)
        input.addEventListener("change", { _: Event ->
            updateSupplierColumnSelection()
        })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "supplierCol_$key"
        labelEl.textContent = label
        val labelStyle = labelEl.asDynamic().style
        labelStyle.cssText = "cursor: pointer; margin: 0;"
        checkbox.appendChild(input)
        checkbox.appendChild(labelEl)
        checkboxesDiv?.appendChild(checkbox)
    }
    
    // Update selection count initially
    updateSupplierColumnSelection()
    
    // Add event listeners
    document.getElementById("closeSupplierColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("supplierColumnFilterModal")?.remove()
    })
    document.getElementById("resetSupplierColumns")?.addEventListener("click", { _: Event ->
        val deviceType = getDeviceType()
        val defaultColumns = getDefaultSupplierColumnsForDevice(deviceType)
        defaultColumns.forEach { col ->
            val checkbox = document.getElementById("supplierCol_$col") as? HTMLInputElement
            checkbox?.checked = true
        }
        defaultColumns.forEach { col ->
            if (!defaultColumns.contains(col)) {
                val checkbox = document.getElementById("supplierCol_$col") as? HTMLInputElement
                checkbox?.checked = false
            }
        }
        updateSupplierColumnSelection()
    })
    document.getElementById("applySupplierColumns")?.addEventListener("click", { _: Event ->
        applySupplierColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "supplierColumnFilterModal") {
            document.getElementById("supplierColumnFilterModal")?.remove()
        }
    })
}

fun updateSupplierColumnSelection() {
    val deviceType = getDeviceType()
    val maxColumns = getMaxColumnsForDevice(deviceType)
    val checkboxes = document.querySelectorAll("#supplierColumnCheckboxes input[type='checkbox']")
    var selectedCount = 0
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedCount++
        }
    }
    
    val countSpan = document.getElementById("supplierSelectedCount")
    countSpan?.textContent = "$selectedCount"
    
    // Disable/enable checkboxes based on max limit
    if (selectedCount >= maxColumns) {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            if (!checkbox.checked) {
                checkbox.disabled = true
            }
        }
    } else {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.disabled = false
        }
    }
}

fun applySupplierColumnChanges() {
    val checkboxes = document.querySelectorAll("#supplierColumnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableListOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val columnKey = checkbox.getAttribute("data-column") ?: ""
            if (columnKey.isNotEmpty()) {
                selectedColumns.add(columnKey)
            }
        }
    }
    
    // Save to localStorage
    safeLocalStorageSet("selectedSupplierColumns", JSON.stringify(selectedColumns.toTypedArray()))
    
    // Close modal
    document.getElementById("supplierColumnFilterModal")?.remove()
    
    // Reload suppliers to apply changes
    loadMasterSuppliers()
}

fun showAddSupplierModal() {
    showSupplierModal(null)
}

fun showSupplierModal(priceId: Long?) {
    val isEdit = priceId != null
    val title = if (isEdit) "Edit Supplier" else "Add New Supplier"
    
    val modalHtml = """
        <div id="supplierModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeSupplierModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="supplierForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Supplier Name <span style="color: #ef4444;">*</span></label>
                            <input type="text" id="supplierAuctionHouse" required style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div class="supplier-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</label>
                                <input type="text" id="supplierStockLocation" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</label>
                                <input type="text" id="supplierRixoCompany" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div class="supplier-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Venue ID</label>
                                <input type="text" id="supplierVenueId" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Rixo Price</label>
                                <input type="text" id="supplierRixoPrice" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Vehicle type</label>
                            <input type="text" id="supplierTypeOfVehicle" placeholder="e.g., CAR, TRUCK" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div class="supplier-modal-actions">
                            <button type="button" id="cancelSupplierBtn" class="supplier-modal-btn supplier-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteSupplierBtn" class="supplier-modal-btn supplier-modal-btn-delete">Delete</button>
                            <button type="button" id="duplicateSupplierBtn" class="supplier-modal-btn supplier-modal-btn-duplicate">Duplicate</button>
                            """ else ""}
                            <button type="submit" id="saveSupplierBtn" class="supplier-modal-btn supplier-modal-btn-save">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    
    // Load data if editing
    if (isEdit && priceId != null) {
        loadSupplierDataForEdit(priceId)
    }
    
    // Event listeners
    document.getElementById("closeSupplierModal")?.addEventListener("click", { _: Event ->
        closeSupplierModal()
    })
    
    document.getElementById("cancelSupplierBtn")?.addEventListener("click", { _: Event ->
        closeSupplierModal()
    })
    
    // Delete button (only shown in edit mode)
    if (isEdit && priceId != null) {
        document.getElementById("deleteSupplierBtn")?.addEventListener("click", { _: Event ->
            if (js("confirm('Are you sure you want to delete this supplier mapping? This action cannot be undone.')").unsafeCast<Boolean>()) {
                deleteMasterSupplier(priceId)
            }
        })
        // Duplicate button (only shown in edit mode) - create new supplier with same data
        document.getElementById("duplicateSupplierBtn")?.addEventListener("click", { _: Event ->
            saveSupplier(null, isDuplicate = true)
        })
    }
    
    document.getElementById("supplierForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveSupplier(priceId)
    })
}

fun closeSupplierModal() {
    document.getElementById("supplierModal")?.remove()
}

fun loadSupplierDataForEdit(priceId: Long) {
    // Get the price from the prices list (since there's no single GET endpoint)
    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load supplier data')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load supplier data')")
            }
            
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            val price = pricesArray.find { it.id == priceId }
            
            if (price != null) {
                (document.getElementById("supplierAuctionHouse") as? HTMLInputElement)?.value = (price.auctionHouse ?: "").toString()
                (document.getElementById("supplierStockLocation") as? HTMLInputElement)?.value = (price.stockLocation ?: "").toString()
                (document.getElementById("supplierRixoCompany") as? HTMLInputElement)?.value = (price.rixoCompany ?: "").toString()
                (document.getElementById("supplierVenueId") as? HTMLInputElement)?.value = (price.venueId ?: "").toString()
                (document.getElementById("supplierRixoPrice") as? HTMLInputElement)?.value = (price.rixoPrice ?: "").toString()
                (document.getElementById("supplierTypeOfVehicle") as? HTMLInputElement)?.value = (price.shipmentSize ?: "").toString()
            } else {
                throw js("Error('Supplier not found')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading supplier data: ${error.toString()}")
            showMessage("Failed to load supplier data: ${error.message}", "error")
        }
}

fun saveSupplier(priceId: Long?, isDuplicate: Boolean = false) {
    val auctionHouse = (document.getElementById("supplierAuctionHouse") as? HTMLInputElement)?.value?.trim() ?: ""
    
    if (auctionHouse.isEmpty()) {
        showMessage("Supplier Name is required", "error")
        return
    }
    
    val stockLocation = (document.getElementById("supplierStockLocation") as? HTMLInputElement)?.value?.trim() ?: ""
    val rixoCompany = (document.getElementById("supplierRixoCompany") as? HTMLInputElement)?.value?.trim() ?: ""
    val venueId = (document.getElementById("supplierVenueId") as? HTMLInputElement)?.value?.trim() ?: ""
    val rixoPrice = (document.getElementById("supplierRixoPrice") as? HTMLInputElement)?.value?.trim() ?: ""
    val typeOfVehicle = (document.getElementById("supplierTypeOfVehicle") as? HTMLInputElement)?.value?.trim() ?: ""
    
    val requestData = js("{}")
    requestData.auctionHouse = auctionHouse
    requestData.stockLocation = stockLocation
    requestData.rixoCompany = rixoCompany
    requestData.venueId = venueId
    requestData.rixoPrice = rixoPrice
    requestData.vehicleType = typeOfVehicle
    
    val url = if (priceId != null) {
        apiUrl("rixo/mappings/$priceId")
    } else {
        apiUrl("rixo/mappings/add")
    }
    
    val method = if (priceId != null) "PUT" else "POST"
    
    window.fetch(url, js("""
        {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        }
    """))
        .then { response: dynamic ->
            // Always parse JSON response, even for error status codes
            response.json().then { result: dynamic ->
                if (response.ok) {
                    val success = result.success as? Boolean ?: false
                    if (success) {
                        closeSupplierModal()
                        loadMasterSuppliers()
                        // Notify Add/Edit Purchase tab to refresh supplier dropdown
                        try {
                            val timestamp = js("Date.now()").toString()
                            safeLocalStorageSet("supplierUpdated", timestamp)
                            val supplierEvent = js("new CustomEvent('supplierUpdated', { detail: { timestamp: timestamp } })")
                            window.dispatchEvent(supplierEvent)
                            Logger.debug("✅ Triggered supplier update event")
                        } catch (e: dynamic) {
                            Logger.warn("⚠️ Failed to trigger supplier update event: ${e.toString()}")
                        }
                        showMessage(when { priceId != null -> "Supplier updated successfully"; isDuplicate -> "Supplier duplicated successfully"; else -> "Supplier added successfully" }, "success")
                    } else {
                        val errorMsg = result.message as? String ?: "Failed to save supplier"
                        Logger.error("Error saving supplier: $errorMsg")
                        showMessage("Error saving supplier: $errorMsg", "error")
                    }
                } else {
                    // Handle error response (400, 500, etc.)
                    val errorMsg = result.message as? String ?: "Failed to save supplier"
                    Logger.error("Error saving supplier (${response.status}): $errorMsg")
                    showMessage("Error saving supplier: $errorMsg", "error")
                }
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error saving supplier: ${error.toString()}")
            val errorMessage = if (error.message != null) error.message.toString() else "Failed to save supplier. Please check your connection and try again."
            showMessage("Error saving supplier: $errorMessage", "error")
        }
}

fun editMasterSupplier(priceId: Long) {
    showSupplierModal(priceId)
}

fun deleteMasterSupplier(priceId: Long) {
    window.fetch(apiUrl("rixo/mappings/$priceId"), js("""
        {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        }
    """))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to delete supplier')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                closeSupplierModal()
                loadMasterSuppliers()
                // Notify Add/Edit Purchase tab to refresh supplier dropdown
                try {
                    val timestamp = js("Date.now()").toString()
                    safeLocalStorageSet("supplierUpdated", timestamp)
                    val supplierEvent = js("new CustomEvent('supplierUpdated', { detail: { timestamp: timestamp } })")
                    window.dispatchEvent(supplierEvent)
                    Logger.debug("✅ Triggered supplier update event after deletion")
                } catch (e: dynamic) {
                    Logger.warn("⚠️ Failed to trigger supplier update event: ${e.toString()}")
                }
                showMessage("Supplier deleted successfully", "success")
            } else {
                throw js("Error(result.message || 'Failed to delete supplier')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error deleting supplier: ${error.toString()}")
            showMessage("Error deleting supplier: ${error.message}", "error")
        }
}

fun showMasterRixoCompanyPage() {
    window.location.hash = "#/master/rixo-company"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="rixoCompanyList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Rixo Company</h2>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Rixo Company:</label>
                    <input type="text" id="rixoCompanyFilter" placeholder="Type Rixo company name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="rixoCompanyTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading Rixo companies...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterRixoCompanies()
    
    document.getElementById("rixoCompanyFilter")?.addEventListener("input", { _: Event ->
        loadMasterRixoCompanies()
    })
}

fun loadMasterRixoCompanies() {
    val tableDiv = document.getElementById("rixoCompanyTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("rixoCompanyFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading Rixo companies...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Use unique Rixo companies from purchases (same source as Purchase List)
    window.fetch(apiUrl("purchases/rixo-companies"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load Rixo companies')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allRixoCompanies = filtered
            if (searchFilter.isNotEmpty()) rixoCompaniesCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No Rixo companies found for: $searchFilter" else "No Rixo companies found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / rixoCompaniesItemsPerPage).toInt()
            val startIndex = (rixoCompaniesCurrentPage - 1) * rixoCompaniesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + rixoCompaniesItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="rixo-company-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, companyName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$rowNum</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$companyName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} Rixo companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="rixoCompaniesPrevPage" class="consignee-pagination-btn" ${if (rixoCompaniesCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $rixoCompaniesCurrentPage of $totalPages</span>
                            <button id="rixoCompaniesNextPage" class="consignee-pagination-btn" ${if (rixoCompaniesCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} Rixo companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html
            
            document.getElementById("rixoCompaniesPrevPage")?.addEventListener("click", { _: Event ->
                if (rixoCompaniesCurrentPage > 1) {
                    rixoCompaniesCurrentPage--
                    loadMasterRixoCompanies()
                }
            })
            document.getElementById("rixoCompaniesNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allRixoCompanies.size.toDouble() / rixoCompaniesItemsPerPage).toInt()
                if (rixoCompaniesCurrentPage < totalP) {
                    rixoCompaniesCurrentPage++
                    loadMasterRixoCompanies()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading Rixo companies: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading Rixo companies</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showMasterStockLocationsPage() {
    window.location.hash = "#/master/stock-location"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="stockLocationList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Stock Location</h2>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Stock Location:</label>
                    <input type="text" id="stockLocationFilter" placeholder="Type stock location to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="stockLocationTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading stock locations...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterStockLocations()
    
    document.getElementById("stockLocationFilter")?.addEventListener("input", { _: Event ->
        loadMasterStockLocations()
    })
}

fun loadMasterStockLocations() {
    val tableDiv = document.getElementById("stockLocationTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("stockLocationFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading stock locations...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    window.fetch(apiUrl("purchases/stock-locations"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load stock locations')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allStockLocations = filtered
            if (searchFilter.isNotEmpty()) stockLocationsCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No stock locations found for: $searchFilter" else "No stock locations found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / stockLocationsItemsPerPage).toInt()
            val startIndex = (stockLocationsCurrentPage - 1) * stockLocationsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + stockLocationsItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="stock-location-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, locationName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$rowNum</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$locationName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} stock locations${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="stockLocationsPrevPage" class="consignee-pagination-btn" ${if (stockLocationsCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $stockLocationsCurrentPage of $totalPages</span>
                            <button id="stockLocationsNextPage" class="consignee-pagination-btn" ${if (stockLocationsCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} stock locations${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html
            
            document.getElementById("stockLocationsPrevPage")?.addEventListener("click", { _: Event ->
                if (stockLocationsCurrentPage > 1) {
                    stockLocationsCurrentPage--
                    loadMasterStockLocations()
                }
            })
            document.getElementById("stockLocationsNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allStockLocations.size.toDouble() / stockLocationsItemsPerPage).toInt()
                if (stockLocationsCurrentPage < totalP) {
                    stockLocationsCurrentPage++
                    loadMasterStockLocations()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading stock locations: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading stock locations</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showMasterRepairCompaniesPage() {
    window.location.hash = "#/master/repair-company"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="repairCompanyList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Repair Company</h2>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Repair Company:</label>
                    <input type="text" id="repairCompanyFilter" placeholder="Type repair company to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="repairCompanyTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading repair companies...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterRepairCompanies()
    
    document.getElementById("repairCompanyFilter")?.addEventListener("input", { _: Event ->
        loadMasterRepairCompanies()
    })
}

fun loadMasterRepairCompanies() {
    val tableDiv = document.getElementById("repairCompanyTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("repairCompanyFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading repair companies...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Purchases-based unique repair companies (same source as Purchase List)
    window.fetch(apiUrl("purchases/repair-companies"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load repair companies')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allRepairCompanies = filtered
            if (searchFilter.isNotEmpty()) repairCompaniesCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No repair companies found for: $searchFilter" else "No repair companies found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / repairCompaniesItemsPerPage).toInt()
            val startIndex = (repairCompaniesCurrentPage - 1) * repairCompaniesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + repairCompaniesItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="repair-company-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Repair Company</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, companyName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$rowNum</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$companyName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} repair companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="repairCompaniesPrevPage" class="consignee-pagination-btn" ${if (repairCompaniesCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $repairCompaniesCurrentPage of $totalPages</span>
                            <button id="repairCompaniesNextPage" class="consignee-pagination-btn" ${if (repairCompaniesCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} repair companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html
            
            document.getElementById("repairCompaniesPrevPage")?.addEventListener("click", { _: Event ->
                if (repairCompaniesCurrentPage > 1) {
                    repairCompaniesCurrentPage--
                    loadMasterRepairCompanies()
                }
            })
            document.getElementById("repairCompaniesNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allRepairCompanies.size.toDouble() / repairCompaniesItemsPerPage).toInt()
                if (repairCompaniesCurrentPage < totalP) {
                    repairCompaniesCurrentPage++
                    loadMasterRepairCompanies()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading repair companies: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading repair companies</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showMasterBankAccountsPage() {
    window.location.hash = "#/master/bank-accounts"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="padding: 20px;">
            <h2 style="margin-bottom: 20px;">Master List - Bank Accounts</h2>
            <div style="margin-bottom: 20px;">
                <button id="addBankAccountBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Bank Account</button>
                <button id="refreshBankAccountBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            </div>
            <div id="bankAccountTable" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">Loading bank accounts...</div>
            </div>
        </div>
    """
    loadMasterBankAccounts()
    
    document.getElementById("addBankAccountBtn")?.addEventListener("click", { _: Event ->
        showAddBankAccountModal()
    })
    document.getElementById("refreshBankAccountBtn")?.addEventListener("click", { _: Event ->
        loadMasterBankAccounts()
    })
}

fun loadMasterBankAccounts() {
    val tableDiv = document.getElementById("bankAccountTable")
    if (tableDiv == null) return
    
    // Bank accounts - implementation needed
    tableDiv.innerHTML = "<div style='text-align: center; color: #666; padding: 40px;'>Bank accounts loading - implementation in progress</div>"
}

fun showAddBankAccountModal() {
    js("alert('Add Bank Account modal - Coming soon')")
}

fun showMasterVenueIdsPage() {
    window.location.hash = "#/master/venue-ids"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="venueIdList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Venue ID</h2>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Venue ID:</label>
                    <input type="text" id="venueIdFilter" placeholder="Type venue ID to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="venueIdTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading venue IDs...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterVenueIds()
    
    document.getElementById("venueIdFilter")?.addEventListener("input", { _: Event ->
        loadMasterVenueIds()
    })
}

fun loadMasterVenueIds() {
    val tableDiv = document.getElementById("venueIdTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("venueIdFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading venue IDs...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Purchases-based unique venue IDs (same source as Purchase List)
    window.fetch(apiUrl("purchases/venue-ids"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load venue IDs')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().sorted()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allVenueIds = filtered
            if (searchFilter.isNotEmpty()) venueIdsCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No venue IDs found for: $searchFilter" else "No venue IDs found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / venueIdsItemsPerPage).toInt()
            val startIndex = (venueIdsCurrentPage - 1) * venueIdsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + venueIdsItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="venue-id-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Venue ID</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, venueIdName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$rowNum</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$venueIdName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} venue IDs${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="venueIdsPrevPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $venueIdsCurrentPage of $totalPages</span>
                            <button id="venueIdsNextPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} venue IDs${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html
            
            document.getElementById("venueIdsPrevPage")?.addEventListener("click", { _: Event ->
                if (venueIdsCurrentPage > 1) {
                    venueIdsCurrentPage--
                    loadMasterVenueIds()
                }
            })
            document.getElementById("venueIdsNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allVenueIds.size.toDouble() / venueIdsItemsPerPage).toInt()
                if (venueIdsCurrentPage < totalP) {
                    venueIdsCurrentPage++
                    loadMasterVenueIds()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading venue IDs: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading venue IDs</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

