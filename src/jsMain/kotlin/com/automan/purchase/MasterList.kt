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
        <div style="padding: 24px; max-width: 1400px; margin: 0 auto;">
            <h1 style="margin: 0 0 24px 0; color: #111827; font-size: 28px; font-weight: 700;">Consignee</h1>
            
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
            
            <!-- Consignee Table -->
            <div id="consigneeTable" style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden;">
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
}

fun loadMasterConsignee() {
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
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse; min-width: 800px;">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Country</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Client Name</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Consignee Name</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Consignee Address</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">POD</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (mapping in filteredMappings) {
                val id = mapping.id ?: ""
                val country = mapping.country ?: ""
                val clientName = mapping.clientName ?: ""
                val consigneeName = mapping.consigneeName ?: ""
                val consigneeAddress = (mapping.consigneeAddress ?: "").toString()
                val consigneeAddressShort = if (consigneeAddress.length > 60) consigneeAddress.take(60) + "..." else consigneeAddress
                val pod = mapping.pod ?: ""
                val stockLocation = mapping.stockLocation ?: ""
                
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
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$id</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$country</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$clientName</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$consigneeName</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 13px;" title="$consigneeAddress">$consigneeAddressShort</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$pod</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$stockLocation</td>
                    </tr>
                """
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
                <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                    Total: ${filteredMappings.size} consignee${if (filteredMappings.size != 1) "s" else ""}${if (countryFilter.isNotEmpty()) " (filtered)" else ""}
                </div>
            """
            
            tableDiv.innerHTML = html
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
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
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
                        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px; padding-top: 24px; border-top: 1px solid #e5e7eb;">
                            <button type="button" id="cancelConsigneeBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteConsigneeBtn" style="padding: 10px 20px; background-color: #ef4444; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600;">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveConsigneeBtn" style="padding: 10px 20px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600;">${if (isEdit) "Update" else "Save"} Consignee</button>
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
        <div style="padding: 24px; max-width: 1400px; margin: 0 auto;">
            <h1 style="margin: 0 0 24px 0; color: #111827; font-size: 28px; font-weight: 700;">Car Brands</h1>
            
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
            
            <!-- Car Brand Table -->
            <div id="carBrandTable" style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden;">
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
}

fun loadMasterCarBrands() {
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
            // Convert to list, sort, then convert back
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
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse; min-width: 1000px;">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Car Brand</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Chassis</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Car Name</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Fuel</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">WD</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Shift</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Grade</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">CC</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Door</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (mapping in paginatedMappings) {
                val id = mapping.id ?: ""
                val carBrand = mapping.carBrand ?: ""
                val chassis = mapping.chassis ?: ""
                val carName = mapping.carName ?: ""
                val fuel = mapping.fuel ?: ""
                val wd = mapping.wd ?: ""
                val shift = mapping.shift ?: ""
                val grade = mapping.grade ?: ""
                val cc = mapping.cc ?: ""
                val door = mapping.door ?: ""
                
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
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$id</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$carBrand</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$chassis</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$carName</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$fuel</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$wd</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$shift</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$grade</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$cc</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$door</td>
                    </tr>
                """
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
            // Add pagination controls if there are multiple pages
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb;">
                        <div style="color: #6b7280; font-size: 14px;">
                            Showing ${startIndex + 1} to $endIndex of ${filteredMappings.size} car brand${if (filteredMappings.size != 1) "s" else ""}${if (brandFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div style="display: flex; gap: 10px; align-items: center;">
                            <button id="carBrandsPrevPage" ${if (carBrandsCurrentPage == 1) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (carBrandsCurrentPage == 1) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (carBrandsCurrentPage == 1) "not-allowed" else "pointer"};">
                                Previous
                            </button>
                            <span style="padding: 8px 16px; color: #666;">
                                Page $carBrandsCurrentPage of $totalPages
                            </span>
                            <button id="carBrandsNextPage" ${if (carBrandsCurrentPage >= totalPages) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (carBrandsCurrentPage >= totalPages) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (carBrandsCurrentPage >= totalPages) "not-allowed" else "pointer"};">
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

fun showAddCarBrandModal() {
    showCarBrandModal(null)
}

fun showCarBrandModal(mappingId: Long?) {
    val isEdit = mappingId != null
    val title = if (isEdit) "Edit Car Brand" else "Add New Car Brand"
    
    val modalHtml = """
        <div id="carBrandModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
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
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Chassis</label>
                                <input type="text" id="carBrandChassis" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Car Name</label>
                                <input type="text" id="carBrandCarName" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Fuel</label>
                                <input type="text" id="carBrandFuel" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">WD</label>
                                <input type="text" id="carBrandWd" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Shift</label>
                                <input type="text" id="carBrandShift" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Grade</label>
                                <input type="text" id="carBrandGrade" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">CC</label>
                                <input type="number" id="carBrandCc" min="0" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Door</label>
                                <input type="number" id="carBrandDoor" min="0" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px; padding-top: 24px; border-top: 1px solid #e5e7eb;">
                            <button type="button" id="cancelCarBrandBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteCarBrandBtn" style="padding: 10px 20px; background-color: #ef4444; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600;">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveCarBrandBtn" style="padding: 10px 20px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600;">${if (isEdit) "Update" else "Save"}</button>
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
                (document.getElementById("carBrandFuel") as? HTMLInputElement)?.value = (data.fuel ?: "").toString()
                (document.getElementById("carBrandWd") as? HTMLInputElement)?.value = (data.wd ?: "").toString()
                (document.getElementById("carBrandShift") as? HTMLInputElement)?.value = (data.shift ?: "").toString()
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
    carBrandData.fuel = (document.getElementById("carBrandFuel") as? HTMLInputElement)?.value?.trim() ?: null
    carBrandData.wd = (document.getElementById("carBrandWd") as? HTMLInputElement)?.value?.trim() ?: null
    carBrandData.shift = (document.getElementById("carBrandShift") as? HTMLInputElement)?.value?.trim() ?: null
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
        <div style="padding: 20px;">
            <h2 style="margin-bottom: 20px;">Master List - Country</h2>
            <div style="margin-bottom: 20px;">
                <button id="addCountryBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Country</button>
                <button id="refreshCountryBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            </div>
            <div id="countryTable" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">Loading countries...</div>
            </div>
        </div>
    """
    loadMasterCountries()
    
    document.getElementById("addCountryBtn")?.addEventListener("click", { _: Event ->
        showAddCountryModal()
    })
    document.getElementById("refreshCountryBtn")?.addEventListener("click", { _: Event ->
        loadMasterCountries()
    })
}

fun loadMasterCountries() {
    // Countries are typically loaded from booking mappings or purchases
    val tableDiv = document.getElementById("countryTable")
    if (tableDiv == null) return
    
    window.fetch(apiUrl("booking/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load countries')")
        }
        .then { result: dynamic ->
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            
            // Extract unique countries
            val countriesSet = js("new Set()")
            for (mapping in mappingsArray) {
                val country = mapping.country ?: ""
                if (country.isNotEmpty()) {
                    js("countriesSet.add(country)")
                }
            }
            
            val countries = js("Array.from(countriesSet)") as Array<dynamic>
            
            if (countries.isEmpty()) {
                tableDiv.innerHTML = "<div style='text-align: center; color: #666; padding: 40px;'>No countries found.</div>"
                return@then
            }
            
            var html = """
                <table style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr style="background-color: #f8f9fa; border-bottom: 2px solid #dee2e6;">
                            <th style="padding: 12px; text-align: left;">Country</th>
                            <th style="padding: 12px; text-align: left;">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
            """
            
            for (country in countries) {
                val countryStr = country.toString()
                html += """
                    <tr style="border-bottom: 1px solid #dee2e6;">
                        <td style="padding: 12px;">$countryStr</td>
                        <td style="padding: 12px;">
                            <button onclick="editMasterCountry('$countryStr')" style="padding: 6px 12px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 5px;">Edit</button>
                        </td>
                    </tr>
                """
            }
            
            html += """
                    </tbody>
                </table>
            """
            
            tableDiv.innerHTML = html
        }
        .catch { error: dynamic ->
            Logger.error("Error loading countries: ${error.toString()}")
            tableDiv.innerHTML = "<div style='text-align: center; color: #dc3545; padding: 40px;'>Error loading countries: ${error.message}</div>"
        }
}

fun showAddCountryModal() {
    js("alert('Add Country modal - Coming soon')")
}

fun showMasterSuppliersPage() {
    window.location.hash = "#/master/supplier"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="padding: 24px; max-width: 1400px; margin: 0 auto;">
            <h1 style="margin: 0 0 24px 0; color: #111827; font-size: 28px; font-weight: 700;">Supplier</h1>
            
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
            
            <!-- Supplier Table -->
            <div id="supplierTable" style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden;">
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
    
    document.getElementById("clearSupplierFilterBtn")?.addEventListener("click", { _: Event ->
        val filterInput = document.getElementById("supplierFilter") as HTMLInputElement?
        filterInput?.value = ""
        loadMasterSuppliers()
    })
    
    // Real-time search filter
    document.getElementById("supplierFilter")?.addEventListener("input", { _: Event ->
        loadMasterSuppliers()
    })
}

fun loadMasterSuppliers() {
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
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse; min-width: 1200px;">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Supplier Name</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Venue ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Rixo Price</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Type of Vehicle</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (price in paginatedPrices) {
                val id = price.id ?: ""
                val supplierName = price.auctionHouse ?: ""
                val stockLocation = price.stockLocation ?: ""
                val rixoCompany = price.rixoCompany ?: ""
                val venueId = price.venueId ?: ""
                val rixoPrice = price.rixoPrice ?: ""
                val typeOfVehicle = price.shipmentSize ?: ""
                
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
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$id</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$supplierName</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$stockLocation</td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px;">$rixoCompany</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$venueId</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$rixoPrice</td>
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">$typeOfVehicle</td>
                    </tr>
                """
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
            // Add pagination controls if there are multiple pages
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb;">
                        <div style="color: #6b7280; font-size: 14px;">
                            Showing ${startIndex + 1} to $endIndex of ${filteredPrices.size} supplier${if (filteredPrices.size != 1) "s" else ""}${if (supplierFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div style="display: flex; gap: 10px; align-items: center;">
                            <button id="suppliersPrevPage" ${if (suppliersCurrentPage == 1) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (suppliersCurrentPage == 1) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (suppliersCurrentPage == 1) "not-allowed" else "pointer"};">
                                Previous
                            </button>
                            <span style="padding: 8px 16px; color: #666;">
                                Page $suppliersCurrentPage of $totalPages
                            </span>
                            <button id="suppliersNextPage" ${if (suppliersCurrentPage >= totalPages) "disabled" else ""} style="padding: 8px 16px; background-color: ${if (suppliersCurrentPage >= totalPages) "#ccc" else "#007bff"}; color: white; border: none; border-radius: 4px; cursor: ${if (suppliersCurrentPage >= totalPages) "not-allowed" else "pointer"};">
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
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</label>
                                <input type="text" id="supplierStockLocation" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</label>
                                <input type="text" id="supplierRixoCompany" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                            </div>
                        </div>
                        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
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
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Type of Vehicle</label>
                            <input type="text" id="supplierTypeOfVehicle" placeholder="e.g., CAR, TRUCK" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px; padding-top: 24px; border-top: 1px solid #e5e7eb;">
                            <button type="button" id="cancelSupplierBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteSupplierBtn" style="padding: 10px 20px; background-color: #ef4444; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600;">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveSupplierBtn" style="padding: 10px 20px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600;">${if (isEdit) "Update" else "Save"}</button>
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

fun saveSupplier(priceId: Long?) {
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
            if (response.ok) response.json() else throw js("Error('Failed to save supplier')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                closeSupplierModal()
                loadMasterSuppliers()
                showMessage(if (priceId != null) "Supplier updated successfully" else "Supplier added successfully", "success")
            } else {
                throw js("Error(result.message || 'Failed to save supplier')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error saving supplier: ${error.toString()}")
            showMessage("Error saving supplier: ${error.message}", "error")
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
        <div style="padding: 20px;">
            <h2 style="margin-bottom: 20px;">Master List - Rixo Company</h2>
            <div style="margin-bottom: 20px;">
                <button id="addRixoCompanyBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Rixo Company</button>
                <button id="refreshRixoCompanyBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            </div>
            <div id="rixoCompanyTable" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">Loading Rixo companies...</div>
            </div>
        </div>
    """
    loadMasterRixoCompanies()
    
    document.getElementById("addRixoCompanyBtn")?.addEventListener("click", { _: Event ->
        showAddRixoCompanyModal()
    })
    document.getElementById("refreshRixoCompanyBtn")?.addEventListener("click", { _: Event ->
        loadMasterRixoCompanies()
    })
}

fun loadMasterRixoCompanies() {
    val tableDiv = document.getElementById("rixoCompanyTable")
    if (tableDiv == null) return
    
    // Rixo companies are typically from rixo_prices (rixo_company)
    window.fetch(apiUrl("rixo-prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load Rixo companies')")
        }
        .then { result: dynamic ->
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            
            // Extract unique Rixo companies
            val companiesSet = js("new Set()")
            for (price in pricesArray) {
                val company = price.rixoCompany ?: ""
                if (company.isNotEmpty()) {
                    js("companiesSet.add(company)")
                }
            }
            
            val companies = js("Array.from(companiesSet)") as Array<dynamic>
            
            if (companies.isEmpty()) {
                tableDiv.innerHTML = "<div style='text-align: center; color: #666; padding: 40px;'>No Rixo companies found.</div>"
                return@then
            }
            
            var html = """
                <table style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr style="background-color: #f8f9fa; border-bottom: 2px solid #dee2e6;">
                            <th style="padding: 12px; text-align: left;">Rixo Company</th>
                            <th style="padding: 12px; text-align: left;">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
            """
            
            for (company in companies) {
                val companyStr = company.toString()
                html += """
                    <tr style="border-bottom: 1px solid #dee2e6;">
                        <td style="padding: 12px;">$companyStr</td>
                        <td style="padding: 12px;">
                            <button onclick="editMasterRixoCompany('$companyStr')" style="padding: 6px 12px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 5px;">Edit</button>
                        </td>
                    </tr>
                """
            }
            
            html += """
                    </tbody>
                </table>
            """
            
            tableDiv.innerHTML = html
        }
        .catch { error: dynamic ->
            Logger.error("Error loading Rixo companies: ${error.toString()}")
            tableDiv.innerHTML = "<div style='text-align: center; color: #dc3545; padding: 40px;'>Error loading Rixo companies: ${error.message}</div>"
        }
}

fun showAddRixoCompanyModal() {
    js("alert('Add Rixo Company modal - Coming soon')")
}

fun showMasterStockLocationsPage() {
    window.location.hash = "#/master/stock-location"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="padding: 20px;">
            <h2 style="margin-bottom: 20px;">Master List - Stock Location</h2>
            <div style="margin-bottom: 20px;">
                <button id="addStockLocationBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Stock Location</button>
                <button id="refreshStockLocationBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            </div>
            <div id="stockLocationTable" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">Loading stock locations...</div>
            </div>
        </div>
    """
    loadMasterStockLocations()
    
    document.getElementById("addStockLocationBtn")?.addEventListener("click", { _: Event ->
        showAddStockLocationModal()
    })
    document.getElementById("refreshStockLocationBtn")?.addEventListener("click", { _: Event ->
        loadMasterStockLocations()
    })
}

fun loadMasterStockLocations() {
    val tableDiv = document.getElementById("stockLocationTable")
    if (tableDiv == null) return
    
    // Stock locations are typically from purchases (stock_location)
    window.fetch(apiUrl("purchases/stock-locations"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load stock locations')")
        }
        .then { locations: dynamic ->
            val locationsArray = js("Array.isArray(locations) ? locations : []") as Array<dynamic>
            
            if (locationsArray.isEmpty()) {
                tableDiv.innerHTML = "<div style='text-align: center; color: #666; padding: 40px;'>No stock locations found.</div>"
                return@then
            }
            
            var html = """
                <table style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr style="background-color: #f8f9fa; border-bottom: 2px solid #dee2e6;">
                            <th style="padding: 12px; text-align: left;">Stock Location</th>
                            <th style="padding: 12px; text-align: left;">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
            """
            
            for (location in locationsArray) {
                val locationStr = location.toString()
                html += """
                    <tr style="border-bottom: 1px solid #dee2e6;">
                        <td style="padding: 12px;">$locationStr</td>
                        <td style="padding: 12px;">
                            <button onclick="editMasterStockLocation('$locationStr')" style="padding: 6px 12px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 5px;">Edit</button>
                        </td>
                    </tr>
                """
            }
            
            html += """
                    </tbody>
                </table>
            """
            
            tableDiv.innerHTML = html
        }
        .catch { error: dynamic ->
            Logger.error("Error loading stock locations: ${error.toString()}")
            tableDiv.innerHTML = "<div style='text-align: center; color: #dc3545; padding: 40px;'>Error loading stock locations: ${error.message}</div>"
        }
}

fun showAddStockLocationModal() {
    js("alert('Add Stock Location modal - Coming soon')")
}

fun showMasterRepairCompaniesPage() {
    window.location.hash = "#/master/repair-company"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="padding: 20px;">
            <h2 style="margin-bottom: 20px;">Master List - Repair Company</h2>
            <div style="margin-bottom: 20px;">
                <button id="addRepairCompanyBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Repair Company</button>
                <button id="refreshRepairCompanyBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            </div>
            <div id="repairCompanyTable" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">Loading repair companies...</div>
            </div>
        </div>
    """
    loadMasterRepairCompanies()
    
    document.getElementById("addRepairCompanyBtn")?.addEventListener("click", { _: Event ->
        showAddRepairCompanyModal()
    })
    document.getElementById("refreshRepairCompanyBtn")?.addEventListener("click", { _: Event ->
        loadMasterRepairCompanies()
    })
}

fun loadMasterRepairCompanies() {
    val tableDiv = document.getElementById("repairCompanyTable")
    if (tableDiv == null) return
    
    // Repair companies are typically from purchases (repair_company)
    window.fetch(apiUrl("purchases"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load repair companies')")
        }
        .then { purchases: dynamic ->
            val purchasesArray = js("Array.isArray(purchases) ? purchases : []") as Array<dynamic>
            
            // Extract unique repair companies
            val companiesSet = js("new Set()")
            for (purchase in purchasesArray) {
                val company = purchase.repairCompany ?: ""
                if (company.isNotEmpty()) {
                    js("companiesSet.add(company)")
                }
            }
            
            val companies = js("Array.from(companiesSet)") as Array<dynamic>
            
            if (companies.isEmpty()) {
                tableDiv.innerHTML = "<div style='text-align: center; color: #666; padding: 40px;'>No repair companies found.</div>"
                return@then
            }
            
            var html = """
                <table style="width: 100%; border-collapse: collapse;">
                    <thead>
                        <tr style="background-color: #f8f9fa; border-bottom: 2px solid #dee2e6;">
                            <th style="padding: 12px; text-align: left;">Repair Company</th>
                            <th style="padding: 12px; text-align: left;">Actions</th>
                        </tr>
                    </thead>
                    <tbody>
            """
            
            for (company in companies) {
                val companyStr = company.toString()
                html += """
                    <tr style="border-bottom: 1px solid #dee2e6;">
                        <td style="padding: 12px;">$companyStr</td>
                        <td style="padding: 12px;">
                            <button onclick="editMasterRepairCompany('$companyStr')" style="padding: 6px 12px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 5px;">Edit</button>
                        </td>
                    </tr>
                """
            }
            
            html += """
                    </tbody>
                </table>
            """
            
            tableDiv.innerHTML = html
        }
        .catch { error: dynamic ->
            Logger.error("Error loading repair companies: ${error.toString()}")
            tableDiv.innerHTML = "<div style='text-align: center; color: #dc3545; padding: 40px;'>Error loading repair companies: ${error.message}</div>"
        }
}

fun showAddRepairCompanyModal() {
    js("alert('Add Repair Company modal - Coming soon')")
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
        <div style="padding: 20px;">
            <h2 style="margin-bottom: 20px;">Master List - Venue IDs</h2>
            <div style="margin-bottom: 20px;">
                <button id="addVenueIdBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Venue ID</button>
                <button id="refreshVenueIdBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            </div>
            <div id="venueIdTable" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">Loading venue IDs...</div>
            </div>
        </div>
    """
    loadMasterVenueIds()
    
    document.getElementById("addVenueIdBtn")?.addEventListener("click", { _: Event ->
        showAddVenueIdModal()
    })
    document.getElementById("refreshVenueIdBtn")?.addEventListener("click", { _: Event ->
        loadMasterVenueIds()
    })
}

fun loadMasterVenueIds() {
    val tableDiv = document.getElementById("venueIdTable")
    if (tableDiv == null) return
    
    // Venue IDs - implementation needed
    tableDiv.innerHTML = "<div style='text-align: center; color: #666; padding: 40px;'>Venue IDs loading - implementation in progress</div>"
}

fun showAddVenueIdModal() {
    js("alert('Add Venue ID modal - Coming soon')")
}

