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

// Global pagination variables for POL, POD, Fuel, Car Grade, Car Shift, Type of Vehicles
var polCurrentPage = 1
var polItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allPol: List<String> = emptyList()
var podCurrentPage = 1
var podItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allPod: List<String> = emptyList()
var fuelCurrentPage = 1
var fuelItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allFuel: List<String> = emptyList()
var carGradeCurrentPage = 1
var carGradeItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarGrades: List<String> = emptyList()
var carShiftCurrentPage = 1
var carShiftItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarShifts: List<String> = emptyList()
var typeOfVehiclesCurrentPage = 1
var typeOfVehiclesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allTypeOfVehicles: List<String> = emptyList()

// Global pagination variables for Client master list (from master_menu)
var clientMasterCurrentPage = 1
var clientMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allClientMaster: List<String> = emptyList()

// Global pagination variables for Consignee master list (from master_menu)
var consigneeMasterCurrentPage = 1
var consigneeMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allConsigneeMaster: List<String> = emptyList()

// Global pagination variables for Supplier master list (from master_menu)
var supplierMasterCurrentPage = 1
var supplierMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allSupplierMaster: List<String> = emptyList()

// Global pagination variables for Car Brands master list (from master_menu)
var carBrandsMasterCurrentPage = 1
var carBrandsMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarBrandsMaster: List<String> = emptyList()

// Global variable to track last device type for Car Brands page
var lastCarBrandDeviceType: String? = getDeviceType()

/**
 * Get default columns for Supplier page based on device type
 */
fun getDefaultSupplierColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("supplierName", "stockLocation", "rixoCompany")
        "tablet" -> listOf("supplierName", "stockLocation", "rixoCompany", "venueId", "rixoPrice")
        "desktop" -> listOf("supplierName", "stockLocation", "rixoCompany", "venueId", "rixoPrice", "typeOfVehicle")
        else -> listOf("supplierName", "stockLocation", "rixoCompany", "venueId", "rixoPrice", "typeOfVehicle")
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
    
    // Filter out "id" column (removed from UI) and auto-adjust if saved columns exceed device limit
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it != "id" }
    return if (filteredColumns.size > maxColumns) {
        defaultColumns
    } else {
        filteredColumns.take(maxColumns)
    }
}

/**
 * Get default columns for Consignee page based on device type
 */
fun getDefaultConsigneeColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("country", "consigneeName", "pod")
        "tablet" -> listOf("country", "consigneeName", "pod", "pols", "clientName", "stockLocation")
        "desktop" -> listOf("country", "clientName", "consigneeName", "consigneeAddress", "pod", "pols", "stockLocation")
        else -> listOf("country", "clientName", "consigneeName", "consigneeAddress", "pod", "pols", "stockLocation")
    }
}

/**
 * Get default columns for Car Brands page based on device type
 */
fun getDefaultCarBrandColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("carBrand", "chassis", "carName")
        "tablet" -> listOf("carBrand", "chassis", "carName", "fuel", "wd")
        "desktop" -> listOf("carBrand", "chassis", "carName", "fuel", "wd", "shift", "grade", "cc", "door")
        else -> listOf("carBrand", "chassis", "carName", "fuel", "wd", "shift", "grade", "cc", "door")
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
    
    // Filter out "id" column (removed from UI) and auto-adjust if saved columns exceed device limit
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it != "id" }
    return if (filteredColumns.size > maxColumns) {
        defaultColumns
    } else {
        filteredColumns.take(maxColumns)
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
    
    // Filter out "id" column (removed from UI) and auto-adjust if saved columns exceed device limit
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it != "id" }
    return if (filteredColumns.size > maxColumns) {
        defaultColumns
    } else {
        filteredColumns.take(maxColumns)
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

/** Client page: shows Client Transactions button and a client master list (from master_menu). */
fun showClientPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="clientMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Client</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addClientMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Client</span>
                    </button>
                </div>
            </div>

            <div style="margin-bottom: 16px;">
                <button id="clientTransactionsBtn" class="client-btn client-btn-primary" style="padding: 10px 20px; font-size: 14px;">Client Transactions</button>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Client:</label>
                    <input type="text" id="clientMasterFilter" placeholder="Type client name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="clientMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading clients...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadClientMasterList()

    document.getElementById("clientTransactionsBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/client-transactions"
    })

    document.getElementById("clientMasterFilter")?.addEventListener("input", { _: Event ->
        loadClientMasterList()
    })

    document.getElementById("addClientMasterBtn")?.addEventListener("click", { _: Event ->
        showAddClientMasterModal()
    })
}

fun loadClientMasterList() {
    val tableDiv = document.getElementById("clientMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("clientMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading clients...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/clients"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load clients')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allClientMaster = filtered
            if (searchFilter.isNotEmpty()) clientMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No clients found for: $searchFilter" else "No clients found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "client")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / clientMasterItemsPerPage).toInt()
            val startIndex = (clientMasterCurrentPage - 1) * clientMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + clientMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "client" to "Client")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="client-master-table">
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
            for ((idx, clientName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="client-master-edit-btn"
                                        data-client="${clientName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "client" -> clientName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "client" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
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
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} client${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="clientMasterPrevPage" class="consignee-pagination-btn" ${if (clientMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $clientMasterCurrentPage of $totalPages</span>
                            <button id="clientMasterNextPage" class="consignee-pagination-btn" ${if (clientMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} client${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".client-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-client") ?: return@addEventListener
                    showEditClientMasterModal(name)
                })
            }
            
            document.getElementById("clientMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (clientMasterCurrentPage > 1) {
                    clientMasterCurrentPage--
                    loadClientMasterList()
                }
            })
            document.getElementById("clientMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allClientMaster.size.toDouble() / clientMasterItemsPerPage).toInt()
                if (clientMasterCurrentPage < totalP) {
                    clientMasterCurrentPage++
                    loadClientMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading clients: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading clients</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddClientMasterModal() {
    document.getElementById("clientMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "clientMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Client</h3>
            <div style="margin-bottom: 16px;">
                <label for="clientMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Client</label>
                <input type="text" id="clientMasterModalInput" placeholder="Enter client name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="clientMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="clientMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("clientMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("clientMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("clientMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Client name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/clients"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add client')")
            }
            .then { _: dynamic ->
                showMessage("Client added successfully", "success")
                modal.remove()
                clientMasterCurrentPage = 1
                loadClientMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding client: ${error.toString()}")
                showMessage("Error adding client: ${error.message}", "error")
            }
    })
}

fun showEditClientMasterModal(originalName: String) {
    document.getElementById("clientMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "clientMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Client</h3>
            <div style="margin-bottom: 16px;">
                <label for="clientMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Client</label>
                <input type="text" id="clientMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="clientMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="clientMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="clientMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("clientMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("clientMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("clientMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Client name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/clients"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update client')")
            }
            .then { _: dynamic ->
                showMessage("Client updated successfully", "success")
                modal.remove()
                loadClientMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating client: ${error.toString()}")
                showMessage("Error updating client: ${error.message}", "error")
            }
    })

    document.getElementById("clientMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete client '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/clients?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete client')")
            }
            .then { _: dynamic ->
                showMessage("Client deleted successfully", "success")
                modal.remove()
                clientMasterCurrentPage = 1
                loadClientMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting client: ${error.toString()}")
                showMessage("Error deleting client: ${error.message}", "error")
            }
    })
}

fun showMasterClientsPage() {
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

/** Consignee page: shows Consignee Map button and a consignee master list (from master_menu). */
fun showConsigneePage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="consigneeMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Consignee</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addConsigneeMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Consignee</span>
                    </button>
                </div>
            </div>

            <div style="margin-bottom: 16px;">
                <button id="consigneeMapBtn" class="client-btn client-btn-primary" style="padding: 10px 20px; font-size: 14px;">Consignee Map</button>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Consignee:</label>
                    <input type="text" id="consigneeMasterFilter" placeholder="Type consignee name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="consigneeMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading consignees...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadConsigneeMasterList()

    document.getElementById("consigneeMapBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/consignee-map"
    })

    document.getElementById("consigneeMasterFilter")?.addEventListener("input", { _: Event ->
        loadConsigneeMasterList()
    })

    document.getElementById("addConsigneeMasterBtn")?.addEventListener("click", { _: Event ->
        showAddConsigneeMasterModal()
    })
}

fun loadConsigneeMasterList() {
    val tableDiv = document.getElementById("consigneeMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("consigneeMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading consignees...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/consignee"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignees')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allConsigneeMaster = filtered
            if (searchFilter.isNotEmpty()) consigneeMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No consignees found for: $searchFilter" else "No consignees found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "consignee")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / consigneeMasterItemsPerPage).toInt()
            val startIndex = (consigneeMasterCurrentPage - 1) * consigneeMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + consigneeMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "consignee" to "Consignee")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="consignee-master-table">
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
            for ((idx, consigneeName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="consignee-master-edit-btn"
                                        data-consignee="${consigneeName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "consignee" -> consigneeName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "consignee" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
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
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} consignee${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="consigneeMasterPrevPage" class="consignee-pagination-btn" ${if (consigneeMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $consigneeMasterCurrentPage of $totalPages</span>
                            <button id="consigneeMasterNextPage" class="consignee-pagination-btn" ${if (consigneeMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} consignee${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".consignee-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-consignee") ?: return@addEventListener
                    showEditConsigneeMasterModal(name)
                })
            }
            
            document.getElementById("consigneeMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (consigneeMasterCurrentPage > 1) {
                    consigneeMasterCurrentPage--
                    loadConsigneeMasterList()
                }
            })
            document.getElementById("consigneeMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allConsigneeMaster.size.toDouble() / consigneeMasterItemsPerPage).toInt()
                if (consigneeMasterCurrentPage < totalP) {
                    consigneeMasterCurrentPage++
                    loadConsigneeMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignees: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading consignees</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddConsigneeMasterModal() {
    document.getElementById("consigneeMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "consigneeMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Consignee</h3>
            <div style="margin-bottom: 16px;">
                <label for="consigneeMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Consignee</label>
                <input type="text" id="consigneeMasterModalInput" placeholder="Enter consignee name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="consigneeMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="consigneeMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("consigneeMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("consigneeMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("consigneeMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Consignee name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/consignee"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add consignee')")
            }
            .then { _: dynamic ->
                showMessage("Consignee added successfully", "success")
                modal.remove()
                consigneeMasterCurrentPage = 1
                loadConsigneeMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding consignee: ${error.toString()}")
                showMessage("Error adding consignee: ${error.message}", "error")
            }
    })
}

fun showEditConsigneeMasterModal(originalName: String) {
    document.getElementById("consigneeMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "consigneeMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Consignee</h3>
            <div style="margin-bottom: 16px;">
                <label for="consigneeMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Consignee</label>
                <input type="text" id="consigneeMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="consigneeMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="consigneeMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="consigneeMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("consigneeMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("consigneeMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("consigneeMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Consignee name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/consignee"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update consignee')")
            }
            .then { _: dynamic ->
                showMessage("Consignee updated successfully", "success")
                modal.remove()
                loadConsigneeMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating consignee: ${error.toString()}")
                showMessage("Error updating consignee: ${error.message}", "error")
            }
    })

    document.getElementById("consigneeMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete consignee '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/consignee?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete consignee')")
            }
            .then { _: dynamic ->
                showMessage("Consignee deleted successfully", "success")
                modal.remove()
                consigneeMasterCurrentPage = 1
                loadConsigneeMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting consignee: ${error.toString()}")
                showMessage("Error deleting consignee: ${error.message}", "error")
            }
    })
}

fun showMasterConsigneePage() {
    showConsigneeMapPage()
}

fun showConsigneeMapPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="consigneeList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Consignee Map</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="backToConsigneePageBtn" style="padding: 8px 16px; background-color: #6b7280; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Back to Consignee Page</button>
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
    document.getElementById("backToConsigneePageBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/consignee"
    })
    
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
            val filteredMappingsUnsorted = if (countryFilter.isNotEmpty()) {
                mappingsArray.filter { mapping ->
                    val country = (mapping.country ?: "").toString().uppercase()
                    country.contains(countryFilter)
                }
            } else {
                mappingsArray.toList()
            }
            
            // Sort by ID descending (newest first)
            val filteredMappings = filteredMappingsUnsorted.sortedByDescending { 
                (it.id as? Number)?.toLong() ?: 0L 
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
            val filteredMappingsUnsorted = if (countryFilter.isNotEmpty()) {
                mappingsArray.filter { mapping ->
                    val country = (mapping.country ?: "").toString().uppercase()
                    country.contains(countryFilter)
                }
            } else {
                mappingsArray.toList()
            }
            
            // Sort by ID descending (newest first)
            val filteredMappings = filteredMappingsUnsorted.sortedByDescending { 
                (it.id as? Number)?.toLong() ?: 0L 
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
                "country" to "Country",
                "clientName" to "Client Name",
                "consigneeName" to "Consignee Name",
                "consigneeAddress" to "Consignee Address",
                "pod" to "POD",
                "pols" to "POL",
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
                val pols = (mapping.pols ?: "").toString()
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
                        "country" -> country
                        "clientName" -> clientName
                        "consigneeName" -> consigneeName
                        "consigneeAddress" -> consigneeAddressShort
                        "pod" -> pod
                        "pols" -> pols
                        "stockLocation" -> stockLocation
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
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
        "country" to "Country",
        "clientName" to "Client Name",
        "consigneeName" to "Consignee Name",
        "consigneeAddress" to "Consignee Address",
        "pod" to "POD",
        "pols" to "POL",
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
        val pols = (mapping.pols ?: "").toString()
        val stockLocation = (mapping.stockLocation ?: "").toString()
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "country" -> country
                "clientName" -> clientName
                "consigneeName" -> consigneeName
                "consigneeAddress" -> consigneeAddress
                "pod" -> pod
                "pols" -> pols
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
        "country" to "Country",
        "clientName" to "Client Name",
        "consigneeName" to "Consignee Name",
        "consigneeAddress" to "Consignee Address",
        "pod" to "POD",
        "pols" to "POL",
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
    
    val clientName = (document.getElementById("consigneeClientName") as? HTMLInputElement)?.value?.trim() ?: ""
    val consigneeName = (document.getElementById("consigneeName") as? HTMLInputElement)?.value?.trim() ?: ""
    val pod = (document.getElementById("consigneePOD") as? HTMLInputElement)?.value?.trim() ?: ""
    val stockLocation = (document.getElementById("consigneeStockLocation") as? HTMLInputElement)?.value?.trim() ?: ""
    val pols = (document.getElementById("consigneePOLs") as? HTMLInputElement)?.value?.trim() ?: ""
    
    val saveButton = document.getElementById("saveConsigneeBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = "Validating..."
    
    // Validate all fields against master lists before saving
    validateConsigneeMasterFields(country, clientName, consigneeName, pod, pols, stockLocation) { missingFields ->
        if (missingFields.isNotEmpty()) {
            // Close consignee modal and show error modal
            closeConsigneeModal()
            showConsigneeMasterFieldsErrorModal(missingFields)
        } else {
            // All fields are valid, proceed with save
            performConsigneeSave(mappingId)
        }
    }
}

fun validateConsigneeMasterFields(
    country: String,
    clientName: String,
    consigneeName: String,
    pod: String,
    pols: String,
    stockLocation: String,
    callback: (List<Pair<String, String>>) -> Unit
) {
    // Fetch all master lists in parallel
    val masterListPromises = js("[]")
    masterListPromises.push(window.fetch(apiUrl("master-menu/country")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/clients")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/consignee")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/pod")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/pol")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/stock_location")))
    
    js("Promise.all")(masterListPromises)
        .then { responses: dynamic ->
            val jsonPromises = js("[]")
            for (i in 0 until 6) {
                val resp = responses[i]
                if (resp.ok) {
                    jsonPromises.push(resp.json())
                } else {
                    jsonPromises.push(js("Promise.resolve([])"))
                }
            }
            js("Promise.all")(jsonPromises)
        }
        .then { results: dynamic ->
            val countryList = parseMasterListArray(results[0])
            val clientsList = parseMasterListArray(results[1])
            val consigneeList = parseMasterListArray(results[2])
            val podList = parseMasterListArray(results[3])
            val polList = parseMasterListArray(results[4])
            val stockLocationList = parseMasterListArray(results[5])
            
            Logger.debug("Validation - Country list: $countryList")
            Logger.debug("Validation - Checking country: '$country'")
            
            val missingFields = mutableListOf<Pair<String, String>>()
            
            // Check country (required)
            if (country.isNotEmpty() && !countryList.any { it.equals(country, ignoreCase = true) }) {
                missingFields.add(Pair("Country", "Country"))
            }
            
            // Check client name (optional, only validate if provided)
            if (clientName.isNotEmpty() && !clientsList.any { it.equals(clientName, ignoreCase = true) }) {
                missingFields.add(Pair("Client Name", "Client"))
            }
            
            // Check consignee name (optional, only validate if provided)
            if (consigneeName.isNotEmpty() && !consigneeList.any { it.equals(consigneeName, ignoreCase = true) }) {
                missingFields.add(Pair("Consignee Name", "Consignee"))
            }
            
            // Check POD (optional, only validate if provided)
            if (pod.isNotEmpty() && !podList.any { it.equals(pod, ignoreCase = true) }) {
                missingFields.add(Pair("POD", "POD"))
            }
            
            // Check POLs (comma-separated, check each)
            if (pols.isNotEmpty()) {
                val polValues = pols.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                for (polValue in polValues) {
                    if (!polList.any { it.equals(polValue, ignoreCase = true) }) {
                        missingFields.add(Pair("POL ($polValue)", "POL"))
                        break // Only report once for POL
                    }
                }
            }
            
            // Check stock location (optional, only validate if provided)
            if (stockLocation.isNotEmpty() && !stockLocationList.any { it.equals(stockLocation, ignoreCase = true) }) {
                missingFields.add(Pair("Stock Location", "Stock Location"))
            }
            
            callback(missingFields)
        }
        .catch { error: dynamic ->
            Logger.error("Error validating consignee fields: ${error.toString()}")
            // On error, proceed with save (don't block the user)
            callback(emptyList())
        }
}

fun parseMasterListArray(raw: dynamic): List<String> {
    return try {
        if (raw != null && js("Array.isArray(raw)").unsafeCast<Boolean>()) {
            val arr = raw.unsafeCast<Array<String>>()
            arr.map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    } catch (e: dynamic) {
        Logger.error("Error parsing master list: ${e.toString()}")
        emptyList()
    }
}

fun showConsigneeMasterFieldsErrorModal(missingFields: List<Pair<String, String>>) {
    document.getElementById("consigneeMasterFieldsErrorModal")?.remove()
    
    // Build the error message
    val fieldNames = missingFields.map { it.first }.joinToString(", ")
    val pageNames = missingFields.map { it.second }.distinct().joinToString(", ")
    
    val modal = document.createElement("div")
    modal.id = "consigneeMasterFieldsErrorModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10001;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #ef4444;">Field(s) Not Found in Master List</h3>
            <p style="margin-bottom: 20px; color: #374151; font-size: 14px; line-height: 1.6;">
                <strong>$fieldNames</strong> does not exist in Master List. Go to the <strong>$pageNames</strong> page and add the missing value(s).
            </p>
            <div style="display: flex; justify-content: flex-end;">
                <button id="closeConsigneeMasterFieldsErrorModalBtn" style="padding: 10px 24px; border-radius: 6px; border: none; background: #3b82f6; color: white; cursor: pointer; font-size: 14px; font-weight: 500;">
                    Close
                </button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    
    document.getElementById("closeConsigneeMasterFieldsErrorModalBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })
}

fun performConsigneeSave(mappingId: Long?) {
    val country = (document.getElementById("consigneeCountry") as? HTMLInputElement)?.value?.trim() ?: ""
    
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

/** Car Brands page: shows Car Brands Map button and a car brands master list (from master_menu). */
fun showCarBrandsPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="carBrandsMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Car Brands</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addCarBrandsMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Car Brands</span>
                    </button>
                </div>
            </div>

            <div style="margin-bottom: 16px;">
                <button id="carBrandsMapBtn" class="client-btn client-btn-primary" style="padding: 10px 20px; font-size: 14px;">Car Brands Map</button>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Car Brands:</label>
                    <input type="text" id="carBrandsMasterFilter" placeholder="Type car brand name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="carBrandsMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading car brands...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadCarBrandsMasterList()

    document.getElementById("carBrandsMapBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/car-brands-map"
    })

    document.getElementById("carBrandsMasterFilter")?.addEventListener("input", { _: Event ->
        loadCarBrandsMasterList()
    })

    document.getElementById("addCarBrandsMasterBtn")?.addEventListener("click", { _: Event ->
        showAddCarBrandsMasterModal()
    })
}

fun loadCarBrandsMasterList() {
    val tableDiv = document.getElementById("carBrandsMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("carBrandsMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading car brands...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/car_brands"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brands')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allCarBrandsMaster = filtered
            if (searchFilter.isNotEmpty()) carBrandsMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No car brands found for: $searchFilter" else "No car brands found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "carBrands")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / carBrandsMasterItemsPerPage).toInt()
            val startIndex = (carBrandsMasterCurrentPage - 1) * carBrandsMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + carBrandsMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "carBrands" to "Car Brands")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="car-brands-master-table">
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
            for ((idx, carBrandName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="car-brands-master-edit-btn"
                                        data-car-brands="${carBrandName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "carBrands" -> carBrandName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "carBrands" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
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
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} car brand${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="carBrandsMasterPrevPage" class="consignee-pagination-btn" ${if (carBrandsMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $carBrandsMasterCurrentPage of $totalPages</span>
                            <button id="carBrandsMasterNextPage" class="consignee-pagination-btn" ${if (carBrandsMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} car brand${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".car-brands-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-car-brands") ?: return@addEventListener
                    showEditCarBrandsMasterModal(name)
                })
            }
            
            document.getElementById("carBrandsMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (carBrandsMasterCurrentPage > 1) {
                    carBrandsMasterCurrentPage--
                    loadCarBrandsMasterList()
                }
            })
            document.getElementById("carBrandsMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allCarBrandsMaster.size.toDouble() / carBrandsMasterItemsPerPage).toInt()
                if (carBrandsMasterCurrentPage < totalP) {
                    carBrandsMasterCurrentPage++
                    loadCarBrandsMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brands</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddCarBrandsMasterModal() {
    document.getElementById("carBrandsMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "carBrandsMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Car Brands</h3>
            <div style="margin-bottom: 16px;">
                <label for="carBrandsMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Car Brands</label>
                <input type="text" id="carBrandsMasterModalInput" placeholder="Enter car brand name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="carBrandsMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="carBrandsMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("carBrandsMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("carBrandsMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("carBrandsMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Car brand name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/car_brands"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add car brand')")
            }
            .then { _: dynamic ->
                showMessage("Car brand added successfully", "success")
                modal.remove()
                carBrandsMasterCurrentPage = 1
                loadCarBrandsMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding car brand: ${error.toString()}")
                showMessage("Error adding car brand: ${error.message}", "error")
            }
    })
}

fun showEditCarBrandsMasterModal(originalName: String) {
    document.getElementById("carBrandsMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "carBrandsMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Car Brands</h3>
            <div style="margin-bottom: 16px;">
                <label for="carBrandsMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Car Brands</label>
                <input type="text" id="carBrandsMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="carBrandsMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="carBrandsMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="carBrandsMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("carBrandsMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("carBrandsMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("carBrandsMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Car brand name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/car_brands"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update car brand')")
            }
            .then { _: dynamic ->
                showMessage("Car brand updated successfully", "success")
                modal.remove()
                loadCarBrandsMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating car brand: ${error.toString()}")
                showMessage("Error updating car brand: ${error.message}", "error")
            }
    })

    document.getElementById("carBrandsMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete car brand '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/car_brands?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete car brand')")
            }
            .then { _: dynamic ->
                showMessage("Car brand deleted successfully", "success")
                modal.remove()
                carBrandsMasterCurrentPage = 1
                loadCarBrandsMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting car brand: ${error.toString()}")
                showMessage("Error deleting car brand: ${error.message}", "error")
            }
    })
}

fun showMasterCarBrandsPage() {
    showCarBrandsMapPage()
}

fun showCarBrandsMapPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="carBrandList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Car Brands Map</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="backToCarBrandsPageBtn" style="padding: 8px 16px; background-color: #6b7280; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Back to Car Brands Page</button>
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
    document.getElementById("backToCarBrandsPageBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/car-brands"
    })
    
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
                "carBrand" to "Car Brand",
                "chassis" to "Chassis",
                "carName" to "Car Name",
                "fuel" to "Fuel",
                "wd" to "WD",
                "shift" to "Shift",
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
        "carBrand" to "Car Brand",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "fuel" to "Fuel",
        "wd" to "WD",
        "shift" to "Shift",
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
        "carBrand" to "Car Brand",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "fuel" to "Fuel",
        "wd" to "WD",
        "shift" to "Shift",
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
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Shift</label>
                                <div style="position: relative; width: 100%;">
                                    <input type="text" id="carBrandShiftInput" placeholder="Select or type Shift" style="width: 100%; padding: 10px 40px 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;" autocomplete="off" onfocus="this.select();">
                                    <select id="carBrandShift" style="position: absolute; top: 0; right: 0; width: 40px; height: 100%; border: none; border-left: 1px solid #d1d5db; background: #f5f5f5; border-radius: 0 6px 6px 0; appearance: none; -webkit-appearance: none; -moz-appearance: none; padding: 0; text-align: center; font-size: 14px; z-index: 1; font-weight: bold; color: #666; opacity: 0; pointer-events: none;" aria-hidden="true" onchange="if (typeof syncComboboxInput === 'function') syncComboboxInput('carBrandShift');">
                                        <option value="">▼</option>
                                        <option value="AT">AT</option>
                                        <option value="MT">MT</option>
                                        <option value="6F">6F</option>
                                        <option value="5F">5F</option>
                                    </select>
                                    <div id="carBrandShiftDropdownBtn" role="button" tabindex="0" aria-label="Open shift list" style="position: absolute; top: 0; right: 0; width: 40px; height: 100%; border: none; border-left: 1px solid #d1d5db; background: #f5f5f5; cursor: pointer; border-radius: 0 6px 6px 0; z-index: 3; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: bold; color: #666; user-select: none;" onmousedown="event.preventDefault(); event.stopPropagation(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandShift');" onclick="event.preventDefault(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandShift');" onkeydown="if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); if (typeof openComboboxDropdown === 'function') openComboboxDropdown('carBrandShift'); }">▼</div>
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
    
    val fuel = (document.getElementById("carBrandFuelInput") as? HTMLInputElement)?.value?.trim() ?: ""
    val shift = (document.getElementById("carBrandShiftInput") as? HTMLInputElement)?.value?.trim() ?: ""
    val grade = (document.getElementById("carBrandGrade") as? HTMLInputElement)?.value?.trim() ?: ""
    
    val saveButton = document.getElementById("saveCarBrandBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = "Validating..."
    
    // Validate all fields against master lists before saving
    validateCarBrandMasterFields(carBrand, fuel, grade, shift) { missingFields ->
        if (missingFields.isNotEmpty()) {
            // Close car brand modal and show error modal
            closeCarBrandModal()
            showCarBrandMasterFieldsErrorModal(missingFields)
        } else {
            // All fields are valid, proceed with save
            performCarBrandSave(mappingId)
        }
    }
}

fun validateCarBrandMasterFields(
    carBrand: String,
    fuel: String,
    grade: String,
    shift: String,
    callback: (List<Pair<String, String>>) -> Unit
) {
    // Fetch all master lists in parallel
    val masterListPromises = js("[]")
    masterListPromises.push(window.fetch(apiUrl("master-menu/car_brands")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/fuel")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/car_grade")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/shift")))
    
    js("Promise.all")(masterListPromises)
        .then { responses: dynamic ->
            val jsonPromises = js("[]")
            for (i in 0 until 4) {
                val resp = responses[i]
                if (resp.ok) {
                    jsonPromises.push(resp.json())
                } else {
                    jsonPromises.push(js("Promise.resolve([])"))
                }
            }
            js("Promise.all")(jsonPromises)
        }
        .then { results: dynamic ->
            val carBrandList = parseMasterListArray(results[0])
            val fuelList = parseMasterListArray(results[1])
            val gradeList = parseMasterListArray(results[2])
            val shiftList = parseMasterListArray(results[3])
            
            val missingFields = mutableListOf<Pair<String, String>>()
            
            // Check car brand (required)
            if (carBrand.isNotEmpty() && !carBrandList.any { it.equals(carBrand, ignoreCase = true) }) {
                missingFields.add(Pair("Car Brand", "Car Brands"))
            }
            
            // Check fuel (optional, only validate if provided)
            if (fuel.isNotEmpty() && !fuelList.any { it.equals(fuel, ignoreCase = true) }) {
                missingFields.add(Pair("Fuel", "Fuel"))
            }
            
            // Check grade (optional, only validate if provided)
            if (grade.isNotEmpty() && !gradeList.any { it.equals(grade, ignoreCase = true) }) {
                missingFields.add(Pair("Grade", "Car Grade"))
            }
            
            // Check shift (optional, only validate if provided)
            if (shift.isNotEmpty() && !shiftList.any { it.equals(shift, ignoreCase = true) }) {
                missingFields.add(Pair("Shift", "Car Shift"))
            }
            
            callback(missingFields)
        }
        .catch { error: dynamic ->
            Logger.error("Error validating car brand fields: ${error.toString()}")
            // On error, proceed with save (don't block the user)
            callback(emptyList())
        }
}

fun showCarBrandMasterFieldsErrorModal(missingFields: List<Pair<String, String>>) {
    document.getElementById("carBrandMasterFieldsErrorModal")?.remove()
    
    // Build the error message
    val fieldNames = missingFields.map { it.first }.joinToString(", ")
    val pageNames = missingFields.map { it.second }.distinct().joinToString(", ")
    
    val modal = document.createElement("div")
    modal.id = "carBrandMasterFieldsErrorModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10001;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #ef4444;">Field(s) Not Found in Master List</h3>
            <p style="margin-bottom: 20px; color: #374151; font-size: 14px; line-height: 1.6;">
                <strong>$fieldNames</strong> does not exist in Master List. Go to the <strong>$pageNames</strong> page(s) and add the missing value(s).
            </p>
            <div style="display: flex; justify-content: flex-end;">
                <button id="closeCarBrandMasterFieldsErrorModalBtn" style="padding: 10px 24px; border-radius: 6px; border: none; background: #3b82f6; color: white; cursor: pointer; font-size: 14px; font-weight: 500;">
                    Close
                </button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    
    document.getElementById("closeCarBrandMasterFieldsErrorModalBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })
}

fun performCarBrandSave(mappingId: Long?) {
    val carBrand = (document.getElementById("carBrandBrand") as? HTMLInputElement)?.value?.trim() ?: ""
    
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
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addCountryBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Country</span>
                    </button>
                </div>
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

    document.getElementById("addCountryBtn")?.addEventListener("click", { _: Event ->
        showAddCountryModal()
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
    
    window.fetch(apiUrl("master-menu/country"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load countries')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
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
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="country-edit-btn"
                                        data-country="${countryName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
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

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".country-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-country") ?: return@addEventListener
                    showEditCountryModal(name)
                })
            }
            
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
    val name = countryName?.toString() ?: return
    showEditCountryModal(name)
}

fun showAddCountryModal() {
    document.getElementById("countryEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "countryEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Country</h3>
            <div style="margin-bottom: 16px;">
                <label for="countryModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Country</label>
                <input type="text" id="countryModalInput" placeholder="Enter country name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="countryModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="countryModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("countryModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("countryModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("countryModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Country name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/country"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add country')")
            }
            .then { _: dynamic ->
                showMessage("Country added successfully", "success")
                modal.remove()
                countriesCurrentPage = 1
                loadMasterCountries()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding country: ${error.toString()}")
                showMessage("Error adding country: ${error.message}", "error")
            }
    })
}

fun showEditCountryModal(originalName: String) {
    document.getElementById("countryEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "countryEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Country</h3>
            <div style="margin-bottom: 16px;">
                <label for="countryModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Country</label>
                <input type="text" id="countryModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="countryModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="countryModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="countryModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("countryModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("countryModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("countryModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Country name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/country"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update country')")
            }
            .then { _: dynamic ->
                showMessage("Country updated successfully", "success")
                modal.remove()
                loadMasterCountries()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating country: ${error.toString()}")
                showMessage("Error updating country: ${error.message}", "error")
            }
    })

    document.getElementById("countryModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete country '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/country?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete country')")
            }
            .then { _: dynamic ->
                showMessage("Country deleted successfully", "success")
                modal.remove()
                countriesCurrentPage = 1
                loadMasterCountries()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting country: ${error.toString()}")
                showMessage("Error deleting country: ${error.message}", "error")
            }
    })
}

/** Supplier page: shows Supplier Map button and a supplier master list (from master_menu). */
fun showSupplierPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="supplierMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Supplier</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addSupplierMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Supplier</span>
                    </button>
                </div>
            </div>

            <div style="margin-bottom: 16px;">
                <button id="supplierMapBtn" class="client-btn client-btn-primary" style="padding: 10px 20px; font-size: 14px;">Supplier Map</button>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Supplier:</label>
                    <input type="text" id="supplierMasterFilter" placeholder="Type supplier name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="supplierMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading suppliers...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadSupplierMasterList()

    document.getElementById("supplierMapBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/supplier-map"
    })

    document.getElementById("supplierMasterFilter")?.addEventListener("input", { _: Event ->
        loadSupplierMasterList()
    })

    document.getElementById("addSupplierMasterBtn")?.addEventListener("click", { _: Event ->
        showAddSupplierMasterModal()
    })
}

fun loadSupplierMasterList() {
    val tableDiv = document.getElementById("supplierMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("supplierMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading suppliers...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/supplier"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load suppliers')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allSupplierMaster = filtered
            if (searchFilter.isNotEmpty()) supplierMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No suppliers found for: $searchFilter" else "No suppliers found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "supplier")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / supplierMasterItemsPerPage).toInt()
            val startIndex = (supplierMasterCurrentPage - 1) * supplierMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + supplierMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "supplier" to "Supplier")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="supplier-master-table">
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
            for ((idx, supplierName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="supplier-master-edit-btn"
                                        data-supplier="${supplierName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "supplier" -> supplierName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "supplier" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
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
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} supplier${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="supplierMasterPrevPage" class="consignee-pagination-btn" ${if (supplierMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $supplierMasterCurrentPage of $totalPages</span>
                            <button id="supplierMasterNextPage" class="consignee-pagination-btn" ${if (supplierMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} supplier${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".supplier-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-supplier") ?: return@addEventListener
                    showEditSupplierMasterModal(name)
                })
            }
            
            document.getElementById("supplierMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (supplierMasterCurrentPage > 1) {
                    supplierMasterCurrentPage--
                    loadSupplierMasterList()
                }
            })
            document.getElementById("supplierMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allSupplierMaster.size.toDouble() / supplierMasterItemsPerPage).toInt()
                if (supplierMasterCurrentPage < totalP) {
                    supplierMasterCurrentPage++
                    loadSupplierMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading suppliers: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading suppliers</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddSupplierMasterModal() {
    document.getElementById("supplierMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "supplierMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Supplier</h3>
            <div style="margin-bottom: 16px;">
                <label for="supplierMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Supplier</label>
                <input type="text" id="supplierMasterModalInput" placeholder="Enter supplier name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="supplierMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="supplierMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("supplierMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("supplierMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("supplierMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Supplier name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/supplier"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add supplier')")
            }
            .then { _: dynamic ->
                showMessage("Supplier added successfully", "success")
                modal.remove()
                supplierMasterCurrentPage = 1
                loadSupplierMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding supplier: ${error.toString()}")
                showMessage("Error adding supplier: ${error.message}", "error")
            }
    })
}

fun showEditSupplierMasterModal(originalName: String) {
    document.getElementById("supplierMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "supplierMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Supplier</h3>
            <div style="margin-bottom: 16px;">
                <label for="supplierMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Supplier</label>
                <input type="text" id="supplierMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="supplierMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="supplierMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="supplierMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("supplierMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("supplierMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("supplierMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Supplier name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/supplier"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update supplier')")
            }
            .then { _: dynamic ->
                showMessage("Supplier updated successfully", "success")
                modal.remove()
                loadSupplierMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating supplier: ${error.toString()}")
                showMessage("Error updating supplier: ${error.message}", "error")
            }
    })

    document.getElementById("supplierMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete supplier '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/supplier?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete supplier')")
            }
            .then { _: dynamic ->
                showMessage("Supplier deleted successfully", "success")
                modal.remove()
                supplierMasterCurrentPage = 1
                loadSupplierMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting supplier: ${error.toString()}")
                showMessage("Error deleting supplier: ${error.message}", "error")
            }
    })
}

fun showMasterSuppliersPage() {
    showSupplierMapPage()
}

fun showSupplierMapPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="supplierList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Supplier Map</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="backToSupplierPageBtn" style="padding: 8px 16px; background-color: #6b7280; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Back to Supplier Page</button>
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
    document.getElementById("backToSupplierPageBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/master/supplier"
    })
    
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
                        "supplierName" -> supplierName.toString()
                        "stockLocation" -> stockLocation.toString()
                        "rixoCompany" -> rixoCompany.toString()
                        "venueId" -> venueId.toString()
                        "rixoPrice" -> rixoPrice.toString()
                        "typeOfVehicle" -> typeOfVehicle.toString()
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                        "venueId", "rixoPrice", "typeOfVehicle" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
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
    
    // Validate all fields against master lists before saving
    validateSupplierMasterFields(auctionHouse, stockLocation, rixoCompany, venueId) { missingFields ->
        if (missingFields.isNotEmpty()) {
            // Close supplier modal and show error modal
            closeSupplierModal()
            showSupplierMasterFieldsErrorModal(missingFields)
        } else {
            // All fields are valid, proceed with save
            performSupplierSave(priceId, isDuplicate)
        }
    }
}

fun validateSupplierMasterFields(
    supplierName: String,
    stockLocation: String,
    rixoCompany: String,
    venueId: String,
    callback: (List<Pair<String, String>>) -> Unit
) {
    // Fetch all master lists in parallel
    val masterListPromises = js("[]")
    masterListPromises.push(window.fetch(apiUrl("master-menu/supplier")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/stock_location")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/rixo_company")))
    masterListPromises.push(window.fetch(apiUrl("master-menu/venue_id")))
    
    js("Promise.all")(masterListPromises)
        .then { responses: dynamic ->
            val jsonPromises = js("[]")
            for (i in 0 until 4) {
                val resp = responses[i]
                if (resp.ok) {
                    jsonPromises.push(resp.json())
                } else {
                    jsonPromises.push(js("Promise.resolve([])"))
                }
            }
            js("Promise.all")(jsonPromises)
        }
        .then { results: dynamic ->
            val supplierList = parseMasterListArray(results[0])
            val stockLocationList = parseMasterListArray(results[1])
            val rixoCompanyList = parseMasterListArray(results[2])
            val venueIdList = parseMasterListArray(results[3])
            
            val missingFields = mutableListOf<Pair<String, String>>()
            
            // Check supplier name (required)
            if (supplierName.isNotEmpty() && !supplierList.any { it.equals(supplierName, ignoreCase = true) }) {
                missingFields.add(Pair("Supplier Name", "Supplier"))
            }
            
            // Check stock location (optional, only validate if provided)
            if (stockLocation.isNotEmpty() && !stockLocationList.any { it.equals(stockLocation, ignoreCase = true) }) {
                missingFields.add(Pair("Stock Location", "Stock Location"))
            }
            
            // Check rixo company (optional, only validate if provided)
            if (rixoCompany.isNotEmpty() && !rixoCompanyList.any { it.equals(rixoCompany, ignoreCase = true) }) {
                missingFields.add(Pair("Rixo Company", "Rixo Company"))
            }
            
            // Check venue ID (optional, only validate if provided)
            if (venueId.isNotEmpty() && !venueIdList.any { it.equals(venueId, ignoreCase = true) }) {
                missingFields.add(Pair("Venue ID", "Venue ID"))
            }
            
            callback(missingFields)
        }
        .catch { error: dynamic ->
            Logger.error("Error validating supplier fields: ${error.toString()}")
            // On error, proceed with save (don't block the user)
            callback(emptyList())
        }
}

fun showSupplierMasterFieldsErrorModal(missingFields: List<Pair<String, String>>) {
    document.getElementById("supplierMasterFieldsErrorModal")?.remove()
    
    // Build the error message
    val fieldNames = missingFields.map { it.first }.joinToString(", ")
    val pageNames = missingFields.map { it.second }.distinct().joinToString(", ")
    
    val modal = document.createElement("div")
    modal.id = "supplierMasterFieldsErrorModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10001;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #ef4444;">Field(s) Not Found in Master List</h3>
            <p style="margin-bottom: 20px; color: #374151; font-size: 14px; line-height: 1.6;">
                <strong>$fieldNames</strong> does not exist in Master List. Go to the <strong>$pageNames</strong> page(s) and add the missing value(s).
            </p>
            <div style="display: flex; justify-content: flex-end;">
                <button id="closeSupplierMasterFieldsErrorModalBtn" style="padding: 10px 24px; border-radius: 6px; border: none; background: #3b82f6; color: white; cursor: pointer; font-size: 14px; font-weight: 500;">
                    Close
                </button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    
    document.getElementById("closeSupplierMasterFieldsErrorModalBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })
}

fun performSupplierSave(priceId: Long?, isDuplicate: Boolean = false) {
    val auctionHouse = (document.getElementById("supplierAuctionHouse") as? HTMLInputElement)?.value?.trim() ?: ""
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
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addRixoCompanyBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Rixo Company</span>
                    </button>
                </div>
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

    document.getElementById("addRixoCompanyBtn")?.addEventListener("click", { _: Event ->
        showAddRixoCompanyModal()
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
    
    // Use Rixo companies from master_menu (rixo_company field)
    window.fetch(apiUrl("master-menu/rixo_company"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load Rixo companies')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
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
                val safeName = companyName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="rixo-company-edit-btn"
                                        data-rixo-company="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
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

            val editButtons = document.querySelectorAll(".rixo-company-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-rixo-company") ?: return@addEventListener
                    showEditRixoCompanyModal(name)
                })
            }
            
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

fun showAddRixoCompanyModal() {
    document.getElementById("rixoCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "rixoCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Rixo Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="rixoCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Rixo Company</label>
                <input type="text" id="rixoCompanyModalInput" placeholder="Enter Rixo company name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="rixoCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="rixoCompanyModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("rixoCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("rixoCompanyModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("rixoCompanyModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Rixo company name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/rixo_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add Rixo company')")
            }
            .then { _: dynamic ->
                showMessage("Rixo company added successfully", "success")
                modal.remove()
                rixoCompaniesCurrentPage = 1
                loadMasterRixoCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding Rixo company: ${error.toString()}")
                showMessage("Error adding Rixo company: ${error.message}", "error")
            }
    })
}

fun showEditRixoCompanyModal(originalName: String) {
    document.getElementById("rixoCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "rixoCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Rixo Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="rixoCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Rixo Company</label>
                <input type="text" id="rixoCompanyModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="rixoCompanyModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="rixoCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="rixoCompanyModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("rixoCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("rixoCompanyModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("rixoCompanyModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Rixo company name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/rixo_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update Rixo company')")
            }
            .then { _: dynamic ->
                showMessage("Rixo company updated successfully", "success")
                modal.remove()
                loadMasterRixoCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating Rixo company: ${error.toString()}")
                showMessage("Error updating Rixo company: ${error.message}", "error")
            }
    })

    document.getElementById("rixoCompanyModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this Rixo company?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/rixo_company?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete Rixo company')")
            }
            .then { _: dynamic ->
                showMessage("Rixo company deleted successfully", "success")
                modal.remove()
                rixoCompaniesCurrentPage = 1
                loadMasterRixoCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting Rixo company: ${error.toString()}")
                showMessage("Error deleting Rixo company: ${error.message}", "error")
            }
    })
}

fun showMasterStockLocationsPage() {
    window.location.hash = "#/master/stock-location"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="stockLocationList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Stock Location</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addStockLocationBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Stock Location</span>
                    </button>
                </div>
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

    document.getElementById("addStockLocationBtn")?.addEventListener("click", { _: Event ->
        showAddStockLocationModal()
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
    
    window.fetch(apiUrl("master-menu/stock_location"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load stock locations')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
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
                val safeName = locationName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="stock-location-edit-btn"
                                        data-stock-location="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
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

            val editButtons = document.querySelectorAll(".stock-location-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-stock-location") ?: return@addEventListener
                    showEditStockLocationModal(name)
                })
            }
            
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

fun showAddStockLocationModal() {
    document.getElementById("stockLocationEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "stockLocationEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Stock Location</h3>
            <div style="margin-bottom: 16px;">
                <label for="stockLocationModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Stock Location</label>
                <input type="text" id="stockLocationModalInput" placeholder="Enter stock location"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="stockLocationModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="stockLocationModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("stockLocationModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("stockLocationModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("stockLocationModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Stock location is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/stock_location"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add stock location')")
            }
            .then { _: dynamic ->
                showMessage("Stock location added successfully", "success")
                modal.remove()
                stockLocationsCurrentPage = 1
                loadMasterStockLocations()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding stock location: ${error.toString()}")
                showMessage("Error adding stock location: ${error.message}", "error")
            }
    })
}

fun showEditStockLocationModal(originalName: String) {
    document.getElementById("stockLocationEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "stockLocationEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Stock Location</h3>
            <div style="margin-bottom: 16px;">
                <label for="stockLocationModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Stock Location</label>
                <input type="text" id="stockLocationModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="stockLocationModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="stockLocationModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="stockLocationModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("stockLocationModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("stockLocationModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("stockLocationModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Stock location is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/stock_location"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update stock location')")
            }
            .then { _: dynamic ->
                showMessage("Stock location updated successfully", "success")
                modal.remove()
                loadMasterStockLocations()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating stock location: ${error.toString()}")
                showMessage("Error updating stock location: ${error.message}", "error")
            }
    })

    document.getElementById("stockLocationModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete stock location '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/stock_location?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete stock location')")
            }
            .then { _: dynamic ->
                showMessage("Stock location deleted successfully", "success")
                modal.remove()
                stockLocationsCurrentPage = 1
                loadMasterStockLocations()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting stock location: ${error.toString()}")
                showMessage("Error deleting stock location: ${error.message}", "error")
            }
    })
}

fun showMasterRepairCompaniesPage() {
    window.location.hash = "#/master/repair-company"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="repairCompanyList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Repair Company</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addRepairCompanyBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Repair Company</span>
                    </button>
                </div>
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

    document.getElementById("addRepairCompanyBtn")?.addEventListener("click", { _: Event ->
        showAddRepairCompanyModal()
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
    
    // Master-menu based repair companies
    window.fetch(apiUrl("master-menu/repair_company"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load repair companies')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
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
                val safeName = companyName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="repair-company-edit-btn"
                                        data-repair-company="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
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

            val editButtons = document.querySelectorAll(".repair-company-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-repair-company") ?: return@addEventListener
                    showEditRepairCompanyModal(name)
                })
            }
            
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

fun showAddRepairCompanyModal() {
    document.getElementById("repairCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "repairCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Repair Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="repairCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Repair Company</label>
                <input type="text" id="repairCompanyModalInput" placeholder="Enter repair company"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="repairCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="repairCompanyModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("repairCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("repairCompanyModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("repairCompanyModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Repair company is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/repair_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add repair company')")
            }
            .then { _: dynamic ->
                showMessage("Repair company added successfully", "success")
                modal.remove()
                repairCompaniesCurrentPage = 1
                loadMasterRepairCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding repair company: ${error.toString()}")
                showMessage("Error adding repair company: ${error.message}", "error")
            }
    })
}

fun showEditRepairCompanyModal(originalName: String) {
    document.getElementById("repairCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "repairCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Repair Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="repairCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Repair Company</label>
                <input type="text" id="repairCompanyModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="repairCompanyModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="repairCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="repairCompanyModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("repairCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("repairCompanyModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("repairCompanyModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Repair company is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/repair_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update repair company')")
            }
            .then { _: dynamic ->
                showMessage("Repair company updated successfully", "success")
                modal.remove()
                loadMasterRepairCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating repair company: ${error.toString()}")
                showMessage("Error updating repair company: ${error.message}", "error")
            }
    })

    document.getElementById("repairCompanyModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this repair company?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/repair_company?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete repair company')")
            }
            .then { _: dynamic ->
                showMessage("Repair company deleted successfully", "success")
                modal.remove()
                repairCompaniesCurrentPage = 1
                loadMasterRepairCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting repair company: ${error.toString()}")
                showMessage("Error deleting repair company: ${error.message}", "error")
            }
    })
}

fun showMasterBankAccountsPage() {
    window.location.hash = "#/master/bank-accounts"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="bankAccountList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Bank Accounts</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addBankAccountBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Bank Account</span>
                    </button>
            </div>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Bank Account:</label>
                    <input type="text" id="bankAccountFilter" placeholder="Type bank account to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="bankAccountTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading bank accounts...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterBankAccounts()

    document.getElementById("bankAccountFilter")?.addEventListener("input", { _: Event ->
        loadMasterBankAccounts()
    })
    
    document.getElementById("addBankAccountBtn")?.addEventListener("click", { _: Event ->
        showAddBankAccountModal()
    })
}

fun loadMasterBankAccounts() {
    val tableDiv = document.getElementById("bankAccountTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("bankAccountFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading bank accounts...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/bank_accounts"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load bank accounts')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No bank accounts found for: $searchFilter" else "No bank accounts found."
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
                    <table style="width: 100%; border-collapse: collapse;" class="bank-account-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Bank Account</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, accountName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = accountName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="bank-account-edit-btn"
                                        data-bank-account="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$accountName</td>
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
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} bank accounts${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="bankAccountsPrevPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $venueIdsCurrentPage of $totalPages</span>
                            <button id="bankAccountsNextPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} bank accounts${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            val editButtons = document.querySelectorAll(".bank-account-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-bank-account") ?: return@addEventListener
                    showEditBankAccountModal(name)
                })
            }

            document.getElementById("bankAccountsPrevPage")?.addEventListener("click", { _: Event ->
                if (venueIdsCurrentPage > 1) {
                    venueIdsCurrentPage--
                    loadMasterBankAccounts()
                }
            })
            document.getElementById("bankAccountsNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(list.size.toDouble() / venueIdsItemsPerPage).toInt()
                if (venueIdsCurrentPage < totalP) {
                    venueIdsCurrentPage++
                    loadMasterBankAccounts()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading bank accounts: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading bank accounts</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddBankAccountModal() {
    document.getElementById("bankAccountEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "bankAccountEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Bank Account</h3>
            <div style="margin-bottom: 16px;">
                <label for="bankAccountModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Bank Account</label>
                <input type="text" id="bankAccountModalInput" placeholder="Enter bank account"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="bankAccountModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="bankAccountModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("bankAccountModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("bankAccountModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("bankAccountModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Bank account is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/bank_accounts"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add bank account')")
            }
            .then { _: dynamic ->
                showMessage("Bank account added successfully", "success")
                modal.remove()
                loadMasterBankAccounts()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding bank account: ${error.toString()}")
                showMessage("Error adding bank account: ${error.message}", "error")
            }
    })
}

fun showEditBankAccountModal(originalName: String) {
    document.getElementById("bankAccountEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "bankAccountEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Bank Account</h3>
            <div style="margin-bottom: 16px;">
                <label for="bankAccountModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Bank Account</label>
                <input type="text" id="bankAccountModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="bankAccountModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="bankAccountModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="bankAccountModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("bankAccountModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("bankAccountModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("bankAccountModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Bank account is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/bank_accounts"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update bank account')")
            }
            .then { _: dynamic ->
                showMessage("Bank account updated successfully", "success")
                modal.remove()
                loadMasterBankAccounts()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating bank account: ${error.toString()}")
                showMessage("Error updating bank account: ${error.message}", "error")
            }
    })

    document.getElementById("bankAccountModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this bank account?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/bank_accounts?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete bank account')")
            }
            .then { _: dynamic ->
                showMessage("Bank account deleted successfully", "success")
                modal.remove()
                loadMasterBankAccounts()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting bank account: ${error.toString()}")
                showMessage("Error deleting bank account: ${error.message}", "error")
            }
    })
}

fun showMasterVenueIdsPage() {
    window.location.hash = "#/master/venue-ids"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="venueIdList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Venue ID</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addVenueIdBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Venue ID</span>
                    </button>
                </div>
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

    document.getElementById("addVenueIdBtn")?.addEventListener("click", { _: Event ->
        showAddVenueIdModal()
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
    
    // Master-menu based venue IDs
    window.fetch(apiUrl("master-menu/venue_id"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load venue IDs')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
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
                val safeName = venueIdName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="venue-id-edit-btn"
                                        data-venue-id="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
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

            val editButtons = document.querySelectorAll(".venue-id-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-venue-id") ?: return@addEventListener
                    showEditVenueIdModal(name)
                })
            }
            
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

fun showAddVenueIdModal() {
    document.getElementById("venueIdEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "venueIdEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Venue ID</h3>
            <div style="margin-bottom: 16px;">
                <label for="venueIdModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Venue ID</label>
                <input type="text" id="venueIdModalInput" placeholder="Enter venue ID"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="venueIdModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="venueIdModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("venueIdModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("venueIdModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("venueIdModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Venue ID is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/venue_id"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add venue ID')")
            }
            .then { _: dynamic ->
                showMessage("Venue ID added successfully", "success")
                modal.remove()
                venueIdsCurrentPage = 1
                loadMasterVenueIds()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding venue ID: ${error.toString()}")
                showMessage("Error adding venue ID: ${error.message}", "error")
            }
    })
}

fun showEditVenueIdModal(originalValue: String) {
    document.getElementById("venueIdEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "venueIdEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalValue.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Venue ID</h3>
            <div style="margin-bottom: 16px;">
                <label for="venueIdModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Venue ID</label>
                <input type="text" id="venueIdModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="venueIdModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="venueIdModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="venueIdModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("venueIdModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("venueIdModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("venueIdModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Venue ID is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalValue

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/venue_id"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update venue ID')")
            }
            .then { _: dynamic ->
                showMessage("Venue ID updated successfully", "success")
                modal.remove()
                loadMasterVenueIds()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating venue ID: ${error.toString()}")
                showMessage("Error updating venue ID: ${error.message}", "error")
            }
    })

    document.getElementById("venueIdModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this venue ID?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalValue) as String
        val url = apiUrl("master-menu/venue_id?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete venue ID')")
            }
            .then { _: dynamic ->
                showMessage("Venue ID deleted successfully", "success")
                modal.remove()
                venueIdsCurrentPage = 1
                loadMasterVenueIds()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting venue ID: ${error.toString()}")
                showMessage("Error deleting venue ID: ${error.message}", "error")
            }
    })
}

// --- POL master page (same UI/UX as Country, data from master_menu.pol) ---
fun showMasterPolPage() {
    window.location.hash = "#/master/pol"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="polList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">POL</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addPolBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add POL</span>
                    </button>
                </div>
            </div>
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by POL:</label>
                    <input type="text" id="polFilter" placeholder="Type POL to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            <div id="polTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading POL...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterPol()
    document.getElementById("polFilter")?.addEventListener("input", { _: Event -> loadMasterPol() })
    document.getElementById("addPolBtn")?.addEventListener("click", { _: Event -> showAddPolModal() })
}

fun loadMasterPol() {
    val tableDiv = document.getElementById("polTable") ?: return
    val searchFilter = (document.getElementById("polFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading POL...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    window.fetch(apiUrl("master-menu/pol"))
        .then { response: dynamic -> if (response.ok) response.json() else throw js("Error('Failed to load POL')") }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allPol = filtered
            if (searchFilter.isNotEmpty()) polCurrentPage = 1
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No POL found for: $searchFilter" else "No POL found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / polItemsPerPage).toInt()
            val startIndex = (polCurrentPage - 1) * polItemsPerPage
            val endIndex = kotlin.math.min(startIndex + polItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="pol-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">POL</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, name) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = name.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="pol-edit-btn" data-pol="$safeName" aria-label="Edit" title="Edit" style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer;">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$name</td>
                    </tr>
                """
            }
            html += "</tbody></table></div>"
            if (totalPages > 1) html += """
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb;">
                    <div style="color: #6b7280; font-size: 14px;">Showing ${startIndex + 1} to $endIndex of ${filtered.size}</div>
                    <div><button id="polPrevPage" class="consignee-pagination-btn" ${if (polCurrentPage == 1) "disabled" else ""}>Previous</button>
                    <span class="consignee-pagination-page">Page $polCurrentPage of $totalPages</span>
                    <button id="polNextPage" class="consignee-pagination-btn" ${if (polCurrentPage >= totalPages) "disabled" else ""}>Next</button></div>
                </div>
            """ else html += """
                <div style="padding: 16px; background-color: #f9fafb; color: #6b7280; font-size: 14px;">Total: ${filtered.size} POL</div>
            """
            tableDiv.innerHTML = html
            val polEditButtons = document.querySelectorAll(".pol-edit-btn")
            for (i in 0 until polEditButtons.length) {
                val btn = polEditButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    showEditPolModal(btn.getAttribute("data-pol") ?: return@addEventListener)
                })
            }
            document.getElementById("polPrevPage")?.addEventListener("click", { _: Event ->
                if (polCurrentPage > 1) { polCurrentPage--; loadMasterPol() }
            })
            document.getElementById("polNextPage")?.addEventListener("click", { _: Event ->
                if (polCurrentPage < kotlin.math.ceil(allPol.size.toDouble() / polItemsPerPage).toInt()) { polCurrentPage++; loadMasterPol() }
            })
        }
        .catch { error: dynamic ->
            tableDiv.innerHTML = "<div style=\"text-align: center; color: #ef4444; padding: 60px 20px;\">Error loading POL: ${error?.message ?: error.toString()}</div>"
        }
}

fun showAddPolModal() {
    document.getElementById("polEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "polEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700;">Add POL</h3>
            <div style="margin-bottom: 16px;">
                <label for="polModalInput" style="display:block; margin-bottom: 6px; font-weight: 600;">POL</label>
                <input type="text" id="polModalInput" placeholder="Enter POL" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px;">
                <button id="polModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                <button id="polModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("polModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("polModalAddBtn")?.addEventListener("click", { _: Event ->
        val value = (document.getElementById("polModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (value.isEmpty()) { showMessage("POL is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = value
        val req = js("{}"); req.method = "POST"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pol"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to add')") }
            .then { _: dynamic -> showMessage("POL added successfully", "success"); modal.remove(); polCurrentPage = 1; loadMasterPol() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showEditPolModal(originalName: String) {
    document.getElementById("polEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "polEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700;">Edit POL</h3>
            <div style="margin-bottom: 16px;">
                <label for="polModalInput" style="display:block; margin-bottom: 6px; font-weight: 600;">POL</label>
                <input type="text" id="polModalInput" value="$safeOriginal" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px;">
                <button id="polModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="polModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                    <button id="polModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("polModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("polModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val newValue = (document.getElementById("polModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (newValue.isEmpty()) { showMessage("POL is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = newValue; body.originalValue = originalName
        val req = js("{}"); req.method = "PUT"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pol"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to update')") }
            .then { _: dynamic -> showMessage("POL updated successfully", "success"); modal.remove(); loadMasterPol() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById("polModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this POL?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl("master-menu/pol?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("POL deleted successfully", "success"); modal.remove(); polCurrentPage = 1; loadMasterPol() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showMasterPodPage() {
    window.location.hash = "#/master/pod"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="podList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">POD</h2>
                <div><button id="addPodBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px;">+ Add POD</button></div>
            </div>
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Search by POD:</label>
                <input type="text" id="podFilter" placeholder="Type POD to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px;">
            </div>
            <div id="podTable" style="margin-top: 20px;"><div style="text-align: center; color: #6b7280; padding: 60px 20px;">Loading POD...</div></div>
        </div>
    """
    loadMasterPod()
    document.getElementById("podFilter")?.addEventListener("input", { _: Event -> loadMasterPod() })
    document.getElementById("addPodBtn")?.addEventListener("click", { _: Event -> showAddPodModal() })
}

fun loadMasterPod() {
    val tableDiv = document.getElementById("podTable") ?: return
    val searchFilter = (document.getElementById("podFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = "<div style=\"text-align: center; color: #6b7280; padding: 60px 20px;\">Loading POD...</div>"
    window.fetch(apiUrl("master-menu/pod"))
        .then { response: dynamic -> if (response.ok) response.json() else throw js("Error('Failed to load POD')") }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allPod = filtered
            if (searchFilter.isNotEmpty()) podCurrentPage = 1
            if (filtered.isEmpty()) {
                tableDiv.innerHTML = "<div style=\"text-align: center; color: #6b7280; padding: 60px 20px;\">${if (searchFilter.isNotEmpty()) "No POD found for: $searchFilter" else "No POD found."}</div>"
                return@then
            }
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / podItemsPerPage).toInt()
            val startIndex = (podCurrentPage - 1) * podItemsPerPage
            val endIndex = kotlin.math.min(startIndex + podItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead><tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                            <th style="padding: 14px 16px; text-align: left; font-weight: 600;">ID</th>
                            <th style="padding: 14px 16px; text-align: left; font-weight: 600;">POD</th>
                        </tr></thead><tbody>
            """
            for ((idx, name) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = name.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb;">
                        <td style="padding: 14px 16px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="pod-edit-btn" data-pod="$safeName" style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer;">
                                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; font-weight: 500;">$name</td>
                    </tr>
                """
            }
            html += "</tbody></table></div>"
            if (totalPages > 1) html += "<div style=\"padding: 16px; background-color: #f9fafb;\"><button id=\"podPrevPage\" ${if (podCurrentPage == 1) "disabled" else ""}>Previous</button> Page $podCurrentPage of $totalPages <button id=\"podNextPage\" ${if (podCurrentPage >= totalPages) "disabled" else ""}>Next</button></div>"
            else html += "<div style=\"padding: 16px; background-color: #f9fafb;\">Total: ${filtered.size} POD</div>"
            tableDiv.innerHTML = html
            val podEditButtons = document.querySelectorAll(".pod-edit-btn")
            for (i in 0 until podEditButtons.length) {
                val btn = podEditButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event -> showEditPodModal(btn.getAttribute("data-pod") ?: return@addEventListener) })
            }
            document.getElementById("podPrevPage")?.addEventListener("click", { _: Event -> if (podCurrentPage > 1) { podCurrentPage--; loadMasterPod() } })
            document.getElementById("podNextPage")?.addEventListener("click", { _: Event -> if (podCurrentPage < kotlin.math.ceil(allPod.size.toDouble() / podItemsPerPage).toInt()) { podCurrentPage++; loadMasterPod() } })
        }
        .catch { error: dynamic -> tableDiv.innerHTML = "<div style=\"text-align: center; color: #ef4444; padding: 60px 20px;\">Error loading POD</div>" }
}

fun showAddPodModal() {
    document.getElementById("podEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "podEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Add POD</h3>
            <label>POD</label>
            <input type="text" id="podModalInput" placeholder="Enter POD" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 16px;">
                <button id="podModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                <button id="podModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("podModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("podModalAddBtn")?.addEventListener("click", { _: Event ->
        val value = (document.getElementById("podModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (value.isEmpty()) { showMessage("POD is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = value
        val req = js("{}"); req.method = "POST"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pod"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to add')") }
            .then { _: dynamic -> showMessage("POD added successfully", "success"); modal.remove(); podCurrentPage = 1; loadMasterPod() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showEditPodModal(originalName: String) {
    document.getElementById("podEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "podEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Edit POD</h3>
            <label>POD</label>
            <input type="text" id="podModalInput" value="$safeOriginal" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 16px;">
                <button id="podModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="podModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                    <button id="podModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("podModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("podModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val newValue = (document.getElementById("podModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (newValue.isEmpty()) { showMessage("POD is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = newValue; body.originalValue = originalName
        val req = js("{}"); req.method = "PUT"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pod"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to update')") }
            .then { _: dynamic -> showMessage("POD updated successfully", "success"); modal.remove(); loadMasterPod() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById("podModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this POD?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl("master-menu/pod?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("POD deleted successfully", "success"); modal.remove(); podCurrentPage = 1; loadMasterPod() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showMasterFuelPage() { window.location.hash = "#/master/fuel"; renderSimpleMasterPage("fuel", "Fuel", "fuel", "fuelFilter", "fuelTable", "addFuelBtn", ::loadMasterFuel, ::showAddFuelModal) }
fun loadMasterFuel() { loadSimpleMaster("master-menu/fuel", "fuelFilter", "fuelTable", "Fuel", fuelCurrentPage, fuelItemsPerPage, allFuel, { fuelCurrentPage = it }, { allFuel = it }, ::showEditFuelModal, "fuel-edit-btn", "data-fuel", "fuelPrevPage", "fuelNextPage", ::loadMasterFuel) }
fun showAddFuelModal() { addSimpleMasterModal("master-menu/fuel", "Fuel", "fuelEditModal", "fuelModalInput", "fuelModalCancelBtn", "fuelModalAddBtn", { fuelCurrentPage = 1; loadMasterFuel() }) }
fun showEditFuelModal(originalName: String) { editSimpleMasterModal("master-menu/fuel", "Fuel", originalName, "fuelEditModal", "fuelModalInput", "fuelModalCancelBtn", "fuelModalUpdateBtn", "fuelModalDeleteBtn", { loadMasterFuel() }, { fuelCurrentPage = 1; loadMasterFuel() }) }

fun showMasterCarGradePage() { window.location.hash = "#/master/car-grade"; renderSimpleMasterPage("car_grade", "Car Grade", "carGrade", "carGradeFilter", "carGradeTable", "addCarGradeBtn", ::loadMasterCarGrade, ::showAddCarGradeModal) }
fun loadMasterCarGrade() { loadSimpleMaster("master-menu/car_grade", "carGradeFilter", "carGradeTable", "Car Grade", carGradeCurrentPage, carGradeItemsPerPage, allCarGrades, { carGradeCurrentPage = it }, { allCarGrades = it }, ::showEditCarGradeModal, "car-grade-edit-btn", "data-car-grade", "carGradePrevPage", "carGradeNextPage", ::loadMasterCarGrade) }
fun showAddCarGradeModal() { addSimpleMasterModal("master-menu/car_grade", "Car Grade", "carGradeEditModal", "carGradeModalInput", "carGradeModalCancelBtn", "carGradeModalAddBtn", { carGradeCurrentPage = 1; loadMasterCarGrade() }) }
fun showEditCarGradeModal(originalName: String) { editSimpleMasterModal("master-menu/car_grade", "Car Grade", originalName, "carGradeEditModal", "carGradeModalInput", "carGradeModalCancelBtn", "carGradeModalUpdateBtn", "carGradeModalDeleteBtn", { loadMasterCarGrade() }, { carGradeCurrentPage = 1; loadMasterCarGrade() }) }

fun showMasterCarShiftPage() { window.location.hash = "#/master/car-shift"; renderSimpleMasterPage("car_shift", "Car Shift", "carShift", "carShiftFilter", "carShiftTable", "addCarShiftBtn", ::loadMasterCarShift, ::showAddCarShiftModal) }
fun loadMasterCarShift() { loadSimpleMaster("master-menu/shift", "carShiftFilter", "carShiftTable", "Car Shift", carShiftCurrentPage, carShiftItemsPerPage, allCarShifts, { carShiftCurrentPage = it }, { allCarShifts = it }, ::showEditCarShiftModal, "car-shift-edit-btn", "data-car-shift", "carShiftPrevPage", "carShiftNextPage", ::loadMasterCarShift) }
fun showAddCarShiftModal() { addSimpleMasterModal("master-menu/shift", "Car Shift", "carShiftEditModal", "carShiftModalInput", "carShiftModalCancelBtn", "carShiftModalAddBtn", { carShiftCurrentPage = 1; loadMasterCarShift() }) }
fun showEditCarShiftModal(originalName: String) { editSimpleMasterModal("master-menu/shift", "Car Shift", originalName, "carShiftEditModal", "carShiftModalInput", "carShiftModalCancelBtn", "carShiftModalUpdateBtn", "carShiftModalDeleteBtn", { loadMasterCarShift() }, { carShiftCurrentPage = 1; loadMasterCarShift() }) }

fun showMasterTypeOfVehiclesPage() { window.location.hash = "#/master/type-of-vehicles"; renderSimpleMasterPage("type_of_vehicles", "Type of Vehicles", "typeOfVehicles", "typeOfVehiclesFilter", "typeOfVehiclesTable", "addTypeOfVehiclesBtn", ::loadMasterTypeOfVehicles, ::showAddTypeOfVehiclesModal) }
fun loadMasterTypeOfVehicles() { loadSimpleMaster("master-menu/type_of_vehicle", "typeOfVehiclesFilter", "typeOfVehiclesTable", "Type of Vehicles", typeOfVehiclesCurrentPage, typeOfVehiclesItemsPerPage, allTypeOfVehicles, { typeOfVehiclesCurrentPage = it }, { allTypeOfVehicles = it }, ::showEditTypeOfVehiclesModal, "type-of-vehicles-edit-btn", "data-type-of-vehicles", "typeOfVehiclesPrevPage", "typeOfVehiclesNextPage", ::loadMasterTypeOfVehicles) }
fun showAddTypeOfVehiclesModal() { addSimpleMasterModal("master-menu/type_of_vehicle", "Type of Vehicles", "typeOfVehiclesEditModal", "typeOfVehiclesModalInput", "typeOfVehiclesModalCancelBtn", "typeOfVehiclesModalAddBtn", { typeOfVehiclesCurrentPage = 1; loadMasterTypeOfVehicles() }) }
fun showEditTypeOfVehiclesModal(originalName: String) { editSimpleMasterModal("master-menu/type_of_vehicle", "Type of Vehicles", originalName, "typeOfVehiclesEditModal", "typeOfVehiclesModalInput", "typeOfVehiclesModalCancelBtn", "typeOfVehiclesModalUpdateBtn", "typeOfVehiclesModalDeleteBtn", { loadMasterTypeOfVehicles() }, { typeOfVehiclesCurrentPage = 1; loadMasterTypeOfVehicles() }) }

private fun renderSimpleMasterPage(apiPath: String, title: String, listId: String, filterId: String, tableId: String, addBtnId: String, loadFn: () -> Unit, addModalFn: () -> Unit) {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="${listId}List" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">$title</h2>
                <div><button id="$addBtnId" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px;">+ Add $title</button></div>
            </div>
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Search by $title:</label>
                <input type="text" id="$filterId" placeholder="Type $title to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px;">
            </div>
            <div id="$tableId" style="margin-top: 20px;"><div style="text-align: center; color: #6b7280; padding: 60px 20px;">Loading $title...</div></div>
        </div>
    """
    loadFn()
    document.getElementById(filterId)?.addEventListener("input", { _: Event -> loadFn() })
    document.getElementById(addBtnId)?.addEventListener("click", { _: Event -> addModalFn() })
}

private fun loadSimpleMaster(apiPath: String, filterId: String, tableId: String, title: String, currentPage: Int, itemsPerPage: Int, allList: List<String>, setPage: (Int) -> Unit, setList: (List<String>) -> Unit, editModalFn: (String) -> Unit, editBtnClass: String, dataAttr: String, prevBtnId: String, nextBtnId: String, loadFn: () -> Unit) {
    val tableDiv = document.getElementById(tableId) ?: return
    val searchFilter = (document.getElementById(filterId) as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = "<div style=\"text-align: center; color: #6b7280; padding: 60px 20px;\">Loading $title...</div>"
    window.fetch(apiUrl(apiPath))
        .then { response: dynamic -> if (response.ok) response.json() else throw js("Error('Failed to load')") }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            setList(filtered)
            if (searchFilter.isNotEmpty()) setPage(1)
            val page = if (searchFilter.isNotEmpty()) 1 else currentPage
            if (filtered.isEmpty()) {
                tableDiv.innerHTML = "<div style=\"text-align: center; color: #6b7280; padding: 60px 20px;\">No $title found.</div>"
                return@then
            }
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / itemsPerPage).toInt()
            val startIndex = ((page - 1) * itemsPerPage).coerceIn(0, filtered.size)
            val endIndex = kotlin.math.min(startIndex + itemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead><tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                            <th style="padding: 14px 16px; text-align: left; font-weight: 600;">ID</th>
                            <th style="padding: 14px 16px; text-align: left; font-weight: 600;">$title</th>
                        </tr></thead><tbody>
            """
            for ((idx, name) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = name.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb;">
                        <td style="padding: 14px 16px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="$editBtnClass" $dataAttr="$safeName" style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer;">
                                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; font-weight: 500;">$name</td>
                    </tr>
                """
            }
            html += "</tbody></table></div>"
            if (totalPages > 1) html += "<div style=\"padding: 16px; background-color: #f9fafb;\"><button id=\"$prevBtnId\" ${if (page == 1) "disabled" else ""}>Previous</button> Page $page of $totalPages <button id=\"$nextBtnId\" ${if (page >= totalPages) "disabled" else ""}>Next</button></div>"
            else html += "<div style=\"padding: 16px; background-color: #f9fafb;\">Total: ${filtered.size} $title</div>"
            tableDiv.innerHTML = html
            val editButtons = document.querySelectorAll(".$editBtnClass")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event -> editModalFn(btn.getAttribute(dataAttr) ?: return@addEventListener) })
            }
            document.getElementById(prevBtnId)?.addEventListener("click", { _: Event -> if (page > 1) { setPage(page - 1); loadFn() } })
            document.getElementById(nextBtnId)?.addEventListener("click", { _: Event -> if (page < totalPages) { setPage(page + 1); loadFn() } })
        }
        .catch { error: dynamic -> tableDiv.innerHTML = "<div style=\"text-align: center; color: #ef4444; padding: 60px 20px;\">Error loading $title</div>" }
}

private fun addSimpleMasterModal(apiPath: String, title: String, modalId: String, inputId: String, cancelBtnId: String, addBtnId: String, onSuccess: () -> Unit) {
    document.getElementById(modalId)?.remove()
    val modal = document.createElement("div")
    modal.id = modalId
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Add $title</h3>
            <label>$title</label>
            <input type="text" id="$inputId" placeholder="Enter $title" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 16px;">
                <button id="$cancelBtnId" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                <button id="$addBtnId" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById(cancelBtnId)?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById(addBtnId)?.addEventListener("click", { _: Event ->
        val value = (document.getElementById(inputId) as? HTMLInputElement)?.value?.trim() ?: ""
        if (value.isEmpty()) { showMessage("$title is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = value
        val req = js("{}"); req.method = "POST"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl(apiPath), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to add')") }
            .then { _: dynamic -> showMessage("$title added successfully", "success"); modal.remove(); onSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

private fun editSimpleMasterModal(apiPath: String, title: String, originalName: String, modalId: String, inputId: String, cancelBtnId: String, updateBtnId: String, deleteBtnId: String, onUpdateSuccess: () -> Unit, onDeleteSuccess: () -> Unit) {
    document.getElementById(modalId)?.remove()
    val modal = document.createElement("div")
    modal.id = modalId
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Edit $title</h3>
            <label>$title</label>
            <input type="text" id="$inputId" value="$safeOriginal" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 16px;">
                <button id="$deleteBtnId" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="$cancelBtnId" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                    <button id="$updateBtnId" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById(cancelBtnId)?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById(updateBtnId)?.addEventListener("click", { _: Event ->
        val newValue = (document.getElementById(inputId) as? HTMLInputElement)?.value?.trim() ?: ""
        if (newValue.isEmpty()) { showMessage("$title is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = newValue; body.originalValue = originalName
        val req = js("{}"); req.method = "PUT"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl(apiPath), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to update')") }
            .then { _: dynamic -> showMessage("$title updated successfully", "success"); modal.remove(); onUpdateSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById(deleteBtnId)?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this $title?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl(apiPath + "?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("$title deleted successfully", "success"); modal.remove(); onDeleteSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

