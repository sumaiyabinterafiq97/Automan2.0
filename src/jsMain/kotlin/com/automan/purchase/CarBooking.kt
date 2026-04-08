package com.automan.purchase

import kotlin.js.asDynamic
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

// Car Booking Functions
// Note: Global variables (currentSelectedCountry, carBookingDisplayedCars, etc.) are defined in MinimalPurchaseApp.kt

fun showCarBookingPage() {
    try {
        Logger.debug("showCarBookingPage() function called")

        // Keep Kotlin var in sync with window (SPA: C&F/FOB flow sets both; avoid empty LIST price column)
        val wm = window.asDynamic().lastCalculationMode
        if (wm != null && js("typeof wm === 'string'") as Boolean) {
            val s = (wm as String).trim()
            if (s.isNotEmpty()) lastCalculationMode = s
        }
        
        // Only clear displayed cars if we don't have saved state to restore
        // Check if we have saved state before clearing
        val hasSavedState = carBookingFormState.consigneeCountry != null || 
                           carBookingFormState.polPort != null ||
                           carBookingDisplayedCars.isNotEmpty()
        
        if (!hasSavedState) {
            // Clear displayed cars when starting NEW booking session (no saved state)
            carBookingDisplayedCars = emptyArray()
            Logger.debug("Cleared carBookingDisplayedCars for new booking session")
        } else {
            Logger.debug("Preserving carBookingDisplayedCars for state restoration (${carBookingDisplayedCars.size} cars)")
        }
        
        val content = document.getElementById("content") ?: return
    
        // Decide LIST table price header based on last calculation mode
        val listPriceHeader = when (lastCalculationMode) {
            "C&F" -> """<th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">C&F</th>"""
            "FOB" -> """<th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">FOB</th>"""
            else -> ""
        }

    content.innerHTML = """
        <div class="booking-page-container">
            <!-- Header -->
            <div class="booking-header">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                      <h1>AUTOMAN | CREATE SHIPPING SCHEDULE</h1>
                </div>
            </div>
            
            <!-- Main Content Container -->
            <div class="booking-main-content">
                <div class="booking-columns">
                    
                    <!-- Left Section: BOOKING DETAILS -->
                    <div class="booking-details-section">
                        <h2 class="booking-section-header">BOOKING DETAILS</h2>
                        
                        <!-- CONSIGNEE -->
                        <div class="booking-form-group">
                            <label>CONSIGNEE:</label>
                            <div class="booking-consignee-row">
                                <span style="color: #6b7280; font-size: 16px;">👤</span>
                                <select id="consigneeCountry">
                                    <option value="">Select Country</option>
                                </select>
                                <button id="manageBookingMappingsBtn" type="button" style="display: none; background: none; border: none; cursor: pointer; padding: 4px; font-size: 18px; color: #6b7280;" title="Manage Mappings">
                                    ⚙️
                                </button>
                            </div>
                            <input type="text" id="consigneeName" placeholder="(CONSIGNEE NAME)">
                        </div>
                        
                        <!-- POL -->
                        <div class="booking-form-group">
                            <label>POL:</label>
                            <select id="polPort" style="color: #000000;">
                                <option value="">Select Port of Loading</option>
                            </select>
                        </div>
                        
                        <!-- ETD -->
                        <div class="booking-form-group">
                            <label>ETD:</label>
                            <input type="date" id="etdDate" onkeydown="return false;" onpaste="return false;" ondrop="return false;" placeholder="ESTIMATED SHIPPING DATE" style="color: #000000;">
                        </div>
                        
                        <!-- POD -->
                        <div class="booking-form-group">
                            <label>POD:</label>
                            <input type="text" id="podPort" placeholder="PORT OF DISCHARGE" style="color: #000000;">
                        </div>
                        
                        <!-- BOOKING NO -->
                        <div class="booking-form-group">
                            <label>BOOKING NO:</label>
                            <input type="text" id="bookingNo" placeholder="">
                        </div>
                        
                        <!-- VESSEL -->
                        <div class="booking-form-group">
                            <label>VESSEL:</label>
                            <input type="text" id="vesselSelect" placeholder="Enter Vessel">
                        </div>
                        
                        <!-- Selection Options: C&F vs FOB is automatic — C&F after Calculate Freight on the cost page; otherwise FOB -->
                        <div class="booking-selection-options">
                            <button id="calculateBtn" class="booking-calculate-btn">Calculate</button>
                        </div>
                        
                        <!-- Additional Action Buttons -->
                        <div class="booking-action-buttons">
                            <a href="#" id="cancelBtn" class="booking-cancel-link">CANCEL</a>
                            <button id="shippedBtn" class="booking-action-btn">SHIPPED</button>
                            <button id="emailBtn" class="booking-action-btn">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                                    <polyline points="22,6 12,13 2,6"></polyline>
                                </svg>
                                EMAIL
                            </button>
                            <button id="exportExcelBtn" class="booking-action-btn">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                                    <polyline points="14,2 14,8 20,8"></polyline>
                                    <line x1="16" y1="13" x2="8" y2="13"></line>
                                    <line x1="16" y1="17" x2="8" y2="17"></line>
                                    <polyline points="10,9 9,9 8,9"></polyline>
                                </svg>
                                EXPORT EXCEL
                            </button>
                        </div>
                    </div>
                    
                    <!-- Right Section: LIST -->
                    <div class="booking-list-section">
                        <h2 class="booking-section-header">LIST</h2>
                        
                        <!-- SEARCH CHASSIS: plain input, suggestions from purchase table search (no dropdown button) -->
                        <div class="booking-form-group" style="position: relative;">
                            <label>SEARCH CHASSIS:</label>
                            <input type="text" id="chassisSearchInput" placeholder="Type to search Chassis"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box;"
                                   autocomplete="off">
                            <div id="chassisSuggestions" style="display: none; position: absolute; top: 100%; left: 0; right: 0; background: white; border: 1px solid #ddd; border-top: none; border-radius: 0 0 4px 4px; max-height: 200px; overflow-y: auto; z-index: 100; box-shadow: 0 4px 6px rgba(0,0,0,0.1);"></div>
                        </div>
                        
                        <!-- Car Selection Table -->
                        <div style="border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; margin-bottom: 20px; overflow-x: auto;">
                            <table class="booking-chassis-table">
                                <thead>
                                    <tr>
                                        <th>
                                            <input type="checkbox" id="selectAllCars" style="margin-right: 8px;">SELECT
                                        </th>
                                        <th>NO.</th>
                                        <th>CHASSIS</th>
                                        <th>NAME</th>
                                        <th>YEAR</th>
                                        $listPriceHeader
                                    </tr>
                                </thead>
                                <tbody id="carSelectionTableBody">
                                    <!-- Cars will be loaded here -->
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
                
            </div>
        </div>
    """
    
    // Set current date as default for ETD
    val today = js("new Date().toISOString().split('T')[0]") as String
    document.getElementById("etdDate")?.setAttribute("value", today)
    
    // Setup event listeners
    setupCarBookingPageListeners()
    
    // Load countries from database
    loadCountries()
    
    // Load stock locations (POL) from database
    loadStockLocations()
    
    // Restore Car Booking state if it exists
    // Delay restoration to ensure booking mapping initialization completes first
    js("setTimeout(function() { window.restoreCarBookingState(); }, 1000)")
    
    // Auto-load purchases into LIST table if both country and POL are selected (after state restoration)
    // Chassis search is a plain input with API suggestions - no dropdown. Skip loadFilteredPurchasesIntoTable when returning from C&F/FOB.
    window.setTimeout({
        val polSelect = document.getElementById("polPort") as? HTMLSelectElement
        val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
        val selectedPol = polSelect?.value ?: ""
        val selectedCountry = countrySelect?.value ?: ""
        if (selectedPol.isNotEmpty() && selectedCountry.isNotEmpty()) {
            if (carBookingDisplayedCars.isEmpty()) {
                Logger.debug("Loading purchases into table after state restoration (Country: $selectedCountry, POL: $selectedPol)")
                loadFilteredPurchasesIntoTable()
            } else {
                Logger.debug("Skipping loadFilteredPurchasesIntoTable - restoring ${carBookingDisplayedCars.size} cars from state (backtrack/FINISH)")
            }
        } else {
            Logger.warn("Cannot load - Country or POL is empty (Country: '$selectedCountry', POL: '$selectedPol')")
        }
    }, 1000)
    
    // Don't load cars automatically - wait for user search
    Logger.debug("Car Booking page loaded - waiting for user to search by chassis number")
    
    } catch (e: dynamic) {
        Logger.error("Error in showCarBookingPage(): ${e.toString()}")
    }
}

fun setupCarBookingPageListeners() {
    // Restore booking selection state (C&F or FOB) - with delay to ensure DOM is ready
    window.setTimeout({
        // restoreBookingSelectionState() - This function should be in MinimalPurchaseApp.kt
        js("if (window.restoreBookingSelectionState) window.restoreBookingSelectionState()")
    }, 200)
    
    // Select All checkbox for car selection table
    document.getElementById("selectAllCars")?.addEventListener("change", { event: Event ->
        val target = event.target as? HTMLInputElement
        val isChecked = target?.checked ?: false
        
        val tableBody = document.getElementById("carSelectionTableBody")
        if (tableBody != null) {
            val checkboxes = tableBody.querySelectorAll("input[type='checkbox']")
            for (i in 0 until checkboxes.length) {
                val checkbox = checkboxes.item(i) as? HTMLInputElement
                checkbox?.checked = isChecked
            }
            Logger.debug("${if (isChecked) "Selected" else "Deselected"} all ${checkboxes.length} cars")
        }
    })
    
    // Purchase List button
    document.getElementById("purchaseListBtn")?.addEventListener("click", { _: Event ->
        Logger.debug("Purchase List button clicked - navigating to existing purchase list")
        showPurchaseList()
    })
    
    // Cancel button - navigate back to purchase list
    document.getElementById("cancelBtn")?.addEventListener("click", { event: Event ->
        event.preventDefault()
        showPurchaseList()
    })
    
    // Shipped button - mark selected rows as shipped=1 in database
    document.getElementById("shippedBtn")?.addEventListener("click", { _: Event ->
        val selectedIds = getSelectedPurchaseIds()
        if (selectedIds.isEmpty()) {
            showMessage("Please select at least one row to mark as shipped", "error")
            return@addEventListener
        }
        val count = selectedIds.size
        val confirmed = window.confirm("Mark $count selected purchase(s) as shipped?")
        if (!confirmed) return@addEventListener
        val purchaseIdsJson = selectedIds.joinToString(",", "[", "]")
        val requestBodyJson = "{\"purchaseIds\":$purchaseIdsJson}"
        Logger.debug("Shipped request body: $requestBodyJson")
        val headers = Headers()
        headers.set("Content-Type", "application/json")
        val requestInit = RequestInit(
            method = "POST",
            headers = headers,
            body = requestBodyJson
        )
        window.fetch(apiUrl("purchases/ship"), requestInit).then { response: dynamic ->
            if (response.ok) {
                response.json().then { data: dynamic ->
                    val updatedCount = (js("data.updatedCount") as? Number)?.toInt() ?: count
                    showMessage("Successfully marked $updatedCount purchase(s) as shipped", "success")
                    loadFilteredPurchasesIntoTable()
                }
            } else {
                response.text().then { errorText: dynamic ->
                    showMessage("Failed to mark as shipped: $errorText", "error")
                }
            }
        }.catch { error: dynamic ->
            showMessage("Error marking as shipped: ${error.toString()}", "error")
        }
    })
    
    // Country dropdown change - trigger filtered chassis loading and booking mappings
    document.getElementById("consigneeCountry")?.addEventListener("change", { event: Event ->
        val selectedCountry = (event.target as HTMLSelectElement).value
        Logger.debug("Country selected: $selectedCountry")
        currentSelectedCountry = selectedCountry // Update the global variable
        
        // Ensure POD is auto-filled for the newly selected country:
        // booking-mapping.js preserves current POD value if present, so clear it first.
        val podPortEl = document.getElementById("podPort")
        if (podPortEl != null) {
            if (podPortEl.tagName == "SELECT") {
                (podPortEl as HTMLSelectElement).value = ""
            } else {
                (podPortEl as HTMLInputElement).value = ""
            }
        }
        carBookingFormState.podPort = ""
        
        // Show/hide Manage Mapping button based on country selection
        val manageBtn = document.getElementById("manageBookingMappingsBtn") as? HTMLElement
        if (manageBtn != null) {
            if (selectedCountry.isNotEmpty()) {
                manageBtn.style.display = "block"
                Logger.debug("Showing Manage Mapping button for country: $selectedCountry")
            } else {
                manageBtn.style.display = "none"
                Logger.debug("Hiding Manage Mapping button - no country selected")
            }
        } else {
            Logger.error("Manage Mapping button not found in DOM!")
        }
        
        // Apply booking mappings (POD and CONSIGNEE auto-fill) - Direct JS call
        Logger.debug("Attempting to call booking mappings for country: $selectedCountry")
        
        // Direct call using window.asDynamic() - same pattern as other window functions
        try {
            val applyBookingMappings = window.asDynamic().applyBookingMappingsByCountry
            if (applyBookingMappings != null && jsTypeOf(applyBookingMappings) == "function") {
                Logger.debug("Calling applyBookingMappingsByCountry with: $selectedCountry")
                applyBookingMappings.unsafeCast<(String) -> Unit>().invoke(selectedCountry)
            } else {
                Logger.warn("window.applyBookingMappingsByCountry is not available")
            }
        } catch (e: dynamic) {
            Logger.error("Error calling booking mappings: ${e.toString()}")
        }

        // booking-mapping.js may replace the POD input with a <select> element.
        // Re-attach the POD change listener after that happens so the state is updated correctly.
        window.setTimeout({
            val podPortElAfter = document.getElementById("podPort") as? HTMLElement
            if (podPortElAfter != null) {
                attachPodChangeListener(podPortElAfter)
                val podValue = if (podPortElAfter.tagName == "SELECT") {
                    (podPortElAfter as HTMLSelectElement).value ?: ""
                } else {
                    (podPortElAfter as HTMLInputElement).value ?: ""
                }
                if (podValue.isNotEmpty()) carBookingFormState.podPort = podValue
            }
        }, 250)
        
        Logger.debug("Country changed")
        
        // Reload POL dropdown based on selected country
        if (selectedCountry.isNotEmpty()) {
            Logger.debug("Reloading POL dropdown for country: $selectedCountry")
            loadStockLocations(selectedCountry)
        } else {
            Logger.debug("Country is empty, loading all stock locations")
            loadStockLocations()
            clearBookingListTable()
        }
    })
    
    // Manage Booking Mappings button click handler
    document.getElementById("manageBookingMappingsBtn")?.addEventListener("click", { _: Event ->
        val selectedCountry = (document.getElementById("consigneeCountry") as? HTMLSelectElement)?.value ?: ""
        if (selectedCountry.isNotEmpty()) {
            Logger.debug("Opening booking mappings modal for country: $selectedCountry")
            try {
                val showBookingMappingsModal = window.asDynamic().showBookingMappingsModal
                if (showBookingMappingsModal != null && jsTypeOf(showBookingMappingsModal) == "function") {
                    showBookingMappingsModal.unsafeCast<(String) -> Unit>().invoke(selectedCountry)
                } else {
                    Logger.error("window.showBookingMappingsModal is not available")
                }
            } catch (e: dynamic) {
                Logger.error("Error opening booking mappings modal: ${e.toString()}")
            }
        } else {
            Logger.warn("Please select a country first")
        }
    })
    
    // POL dropdown change - auto-load purchases into LIST table
    document.getElementById("polPort")?.addEventListener("change", { event: Event ->
        val selectedPol = (event.target as HTMLSelectElement).value
        Logger.debug("POL selected: $selectedPol")
        val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
        val selectedCountry = countrySelect?.value ?: ""
        if (selectedCountry.isNotEmpty() && selectedPol.isNotEmpty()) {
            Logger.debug("Loading purchases into table after POL change")
            loadFilteredPurchasesIntoTable()
        } else {
            Logger.warn("Cannot reload - Country or POL is empty")
            if (selectedPol.isEmpty()) clearBookingListTable()
        }
    })
    
    // POD field change - save to state immediately to preserve it
    val podPortElForListener = document.getElementById("podPort") as? HTMLElement
    if (podPortElForListener != null) {
        attachPodChangeListener(podPortElForListener)
    }
}

// Helper function to attach POD change listener (can be called multiple times if element is replaced)
fun attachPodChangeListener(podPortEl: HTMLElement) {
    // Remove existing listeners by cloning the element (if it's an input)
    // For select, we can just add new listeners
    val savePodToState = {
        val podEl = document.getElementById("podPort")
        if (podEl != null) {
            val podValue = if (podEl.tagName == "SELECT") {
                (podEl as HTMLSelectElement).value ?: ""
            } else {
                (podEl as HTMLInputElement).value ?: ""
            }
            if (podValue.isNotEmpty()) {
                carBookingFormState.podPort = podValue
                Logger.debug("Saved POD to state on change: $podValue (element type: ${podEl.tagName})")
            } else {
                Logger.debug("POD value is empty, not saving to state")
            }
        } else {
            Logger.warn("POD element not found when trying to save to state")
        }
    }
    // Listen to both input and change events
    podPortEl.addEventListener("input", { event: Event -> 
        Logger.debug("POD input event triggered")
        savePodToState() 
    })
    podPortEl.addEventListener("change", { event: Event -> 
        Logger.debug("POD change event triggered")
        savePodToState() 
    })
    // Also listen to blur event to catch when user leaves the field
    podPortEl.addEventListener("blur", { event: Event -> 
        Logger.debug("POD blur event triggered")
        savePodToState() 
    })
    Logger.debug("Attached POD change listener to element type: ${podPortEl.tagName}")
    
    // Also save current value immediately if it's not empty
    val currentPodValue = if (podPortEl.tagName == "SELECT") {
        (podPortEl as HTMLSelectElement).value ?: ""
    } else {
        (podPortEl as HTMLInputElement).value ?: ""
    }
    if (currentPodValue.isNotEmpty()) {
        carBookingFormState.podPort = currentPodValue
        Logger.debug("Saved existing POD value to state: $currentPodValue")
    }
    
    // Chassis search: plain input - show suggestions from purchase table search, add car on Enter or suggestion click
    window.asDynamic().addCarToBookingTable = ::addCarToBookingTable
    var chassisSearchDebounceTimer: dynamic = 0
    document.getElementById("chassisSearchInput")?.addEventListener("input", { event: Event ->
        val input = event.target as? HTMLInputElement ?: return@addEventListener
        val q = input.value.trim()
        window.clearTimeout(chassisSearchDebounceTimer)
        chassisSearchDebounceTimer = window.setTimeout({
            fetchChassisSuggestionsForBooking(q)
        }, 250)
    })
    document.getElementById("chassisSearchInput")?.addEventListener("keydown", { event: Event ->
        val keyEvent = event.asDynamic()
        if (keyEvent.key == "Enter") {
            val input = document.getElementById("chassisSearchInput") as? HTMLInputElement
            val chassis = input?.value?.trim() ?: ""
            if (chassis.isNotEmpty()) {
                keyEvent.preventDefault()
                hideChassisSuggestions()
                addCarToBookingTable(chassis)
            }
        }
    })
    document.getElementById("chassisSearchInput")?.addEventListener("blur", { _: Event ->
        window.setTimeout({ hideChassisSuggestions() }, 200)
    })
    window.asDynamic().handleChassisSearchChange = { }
    
    // Calculate button → C&F cost page; list/PDF use FOB until freight is calculated (see FreightCalculation.confirmFreightCalculation)
    document.getElementById("calculateBtn")?.addEventListener("click", { _: Event ->
        lastCalculationMode = "FOB"
        js("window.lastCalculationMode = 'FOB'")
        
        val selectedIds = getSelectedPurchaseIds()
        if (selectedIds.isEmpty()) {
            showMessage("Please select at least one car", "error")
            return@addEventListener
        }
        
        // Get all booking form values for validation
        val etd = (document.getElementById("etdDate") as? HTMLInputElement)?.value ?: ""
        val bookingNo = (document.getElementById("bookingNo") as? HTMLInputElement)?.value ?: ""
        val vessel = (document.getElementById("vesselSelect") as? HTMLInputElement)?.value ?: ""
        
        // Validate required fields (as per documentation)
        if (etd.isEmpty()) {
            showMessage("Please fill in ETD (Estimated Time of Departure) before calculating", "error")
            return@addEventListener
        }
        if (bookingNo.isEmpty()) {
            showMessage("Please fill in BOOKING NO before calculating", "error")
            return@addEventListener
        }
        if (vessel.isEmpty()) {
            showMessage("Please enter VESSEL before calculating", "error")
            return@addEventListener
        }
        
        Logger.debug("Booking Calculate: lastCalculationMode starts as FOB until Calculate Freight is confirmed")
        
        // Store purchase IDs globally for FINISH button (as per documentation)
        cnfPageSelectedPurchaseIds = selectedIds
        Logger.debug("Stored purchase IDs in cnfPageSelectedPurchaseIds: $selectedIds")
        
        // Get selected cars from table
        val selectedCars = getSelectedCarsFromTable()
        Logger.debug("Selected cars for calculation: ${selectedCars.size}")
        
        // Save selected car IDs for restoration (using already declared selectedIds)
        carBookingFormState.selectedPurchaseIds = selectedIds.toTypedArray()
        Logger.debug("Saved selected purchase IDs in form state: $selectedIds")
        
        // Get selected country
        val selectedCountry = (document.getElementById("consigneeCountry") as? HTMLSelectElement)?.value ?: "PAKISTAN"
        Logger.debug("Selected country: $selectedCountry")
        
        // Get POD value - check both input and select elements, and fallback to saved state or database
        val podPortEl = document.getElementById("podPort")
        var podPort = ""
        if (podPortEl != null) {
            if (podPortEl.tagName == "SELECT") {
                podPort = (podPortEl as HTMLSelectElement).value ?: ""
            } else {
                podPort = (podPortEl as HTMLInputElement).value ?: ""
            }
        }
        // If POD is empty, try to get from saved state
        if (podPort.isEmpty()) {
            val savedPod = carBookingFormState.podPort as? String ?: ""
            if (savedPod.isNotEmpty()) {
                podPort = savedPod
                console.log("⚠️ POD was empty in form, using saved POD from state: $podPort")
            }
        }
        // If POD is still empty, try to get from database (destination field of selected purchases)
        if (podPort.isEmpty() && selectedCars.isNotEmpty()) {
            // Check if any selected car has a destination (POD) value
            for (car in selectedCars) {
                // Try multiple ways to access destination field
                val carDestination = when {
                    js("typeof car.destination !== 'undefined' && car.destination !== null") as Boolean -> {
                        js("car.destination") as? String ?: ""
                    }
                    js("typeof car['destination'] !== 'undefined' && car['destination'] !== null") as Boolean -> {
                        js("car['destination']") as? String ?: ""
                    }
                    else -> {
                        car.destination as? String ?: ""
                    }
                }
                if (carDestination.isNotEmpty() && carDestination.trim().isNotEmpty()) {
                    podPort = carDestination.trim()
                    Logger.debug("POD was empty, using POD from database (destination field): $podPort")
                    break
                }
            }
        }
        
        // Also check displayed cars if still empty
        if (podPort.isEmpty() && carBookingDisplayedCars.isNotEmpty()) {
            for (car in carBookingDisplayedCars) {
                val carDestination = when {
                    js("typeof car.destination !== 'undefined' && car.destination !== null") as Boolean -> {
                        js("car.destination") as? String ?: ""
                    }
                    js("typeof car['destination'] !== 'undefined' && car['destination'] !== null") as Boolean -> {
                        js("car['destination']") as? String ?: ""
                    }
                    else -> {
                        car.destination as? String ?: ""
                    }
                }
                if (carDestination.isNotEmpty() && carDestination.trim().isNotEmpty()) {
                    podPort = carDestination.trim()
                    Logger.debug("POD was empty, using POD from displayed cars (destination field): $podPort")
                    break
                }
            }
        }
        
        // Save POD to state immediately to preserve it (in case form gets cleared)
        if (podPort.isNotEmpty()) {
            carBookingFormState.podPort = podPort
            Logger.debug("Saved POD to state for preservation: $podPort")
        }
        
        val consigneeName = (document.getElementById("consigneeName") as? HTMLInputElement)?.value?.trim() ?: ""
        val consignee = consigneeName
        
        console.log("📋 Booking form values:")
        console.log("   ETD: $etd")
        console.log("   Booking No: $bookingNo")
        console.log("   Vessel: $vessel")
        console.log("   POD: $podPort (from form: ${podPortEl?.let { if (it.tagName == "SELECT") (it as HTMLSelectElement).value else (it as HTMLInputElement).value } ?: ""}, from state: ${carBookingFormState.podPort})")
        console.log("   Consignee: $consignee")
        
        // Update purchases table with booking data and set booking_id (shipped is set via Shipped button)
        updateSelectedPurchasesWithBookingData(
            purchaseIds = selectedIds,
            etd = etd,
            bookingNo = bookingNo,
            vessel = vessel,
            destination = podPort,
            consignee = consignee,
            onComplete = {
                Logger.debug("All purchases updated with booking data and booking_id")
                
                // Store booking details for PDF generation
                storeBookingDetailsForPdf()
                
                // Save booking state before navigation
                saveCarBookingState()
                
                // Always open C&F-style page (freight field + Calculate Freight). Mode FOB/C&F follows freight calculation.
                showCnfCalculationPage(selectedChassis = null, selectedCars = selectedCars, selectedCountry = selectedCountry, isFobMode = false)
            }
        )
    })
}

fun loadCountries() {
    Logger.debug("Loading countries from purchases table...")
    
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<Array<String>>("purchases/countries")
        result.fold(
            onSuccess = { countries ->
                Logger.debug("Countries data received: ${countries.size} countries")
                val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
                if (countrySelect != null) {
                    // Clear existing options except the first one
                    countrySelect.innerHTML = "<option value=\"\">Select Country</option>"
                    
                    // Add countries from API
                    countries.forEach { country ->
                        val option = document.createElement("option")
                        option.setAttribute("value", country)
                        option.textContent = country
                        countrySelect.appendChild(option)
                    }
                    Logger.debug("Countries loaded from API: ${countries.size}")
                }
            },
            onError = { message, _ ->
                Logger.error("Error loading countries: $message")
                loadCountriesFallback()
            }
        )
    }
}

fun loadCountriesFallback() {
    Logger.debug("Loading fallback country data...")
    
    val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
    if (countrySelect == null) {
        Logger.error("Country select element not found in fallback!")
        return
    }
    
    Logger.debug("Clearing country select and adding default option...")
    countrySelect.innerHTML = "<option value=\"\">Select Country</option>"
    
    // Country data from your CSV file - unique countries
    val fallbackCountries = listOf(
        "PAKISTAN",
        "SOUTH AFRICA", 
        "KENYA",
        "TANZANIA",
        "UGANDA",
        "GHANA",
        "NIGERIA",
        "JAPAN",
        "DURBAN",
        "MAPUTO",
        "DUBAI"
    )
    
    Logger.debug("Adding ${fallbackCountries.size} countries to dropdown...")
    
    fallbackCountries.forEach { country ->
        val option = document.createElement("option")
        option.setAttribute("value", country)
        option.textContent = country
        countrySelect.appendChild(option)
    }
    
    Logger.debug("Fallback countries loaded successfully: ${fallbackCountries.size} countries, final options count: ${countrySelect.options.length}")
}

fun loadStockLocations(country: String? = null) {
    val countryParam = country ?: ""
    console.log("Loading POL values from purchases table${if (countryParam.isNotEmpty()) " for country: $countryParam" else " (all POLs)"}...")
    
    // If country is specified, fetch POL values filtered by country
    if (countryParam.isNotEmpty()) {
        val encodedCountry = js("encodeURIComponent")(countryParam).unsafeCast<String>()
        val polApiUrl = apiUrl("purchases/pols-by-country?country=$encodedCountry")
        
        window.fetch(polApiUrl)
            .then { response: dynamic ->
                console.log("POLs by country API response status:", response.status)
                if (response.ok) {
                    response.json()
                } else {
                    console.log("POLs by country API failed, fetching all purchases to filter client-side")
                    // Fallback: fetch all purchases and filter by country
                    window.fetch(apiUrl("purchases"))
                        .then { purchasesResponse: dynamic ->
                            if (purchasesResponse.ok) {
                                purchasesResponse.json()
                            } else {
                                js("Promise.resolve([])")
                            }
                        }
                        .then { purchases: dynamic ->
                            val purchasesArray = purchases as Array<dynamic>
                            val polsSet = mutableSetOf<String>()
                            purchasesArray.forEach { purchase ->
                                val purchaseCountry = js("purchase.country")?.toString() ?: ""
                                val pol = js("purchase.pol")?.toString() ?: ""
                                if (purchaseCountry.equals(countryParam, ignoreCase = true) && pol.isNotEmpty()) {
                                    polsSet.add(pol)
                                }
                            }
                            polsSet.toTypedArray()
                        }
                }
            }
            .then { pols: dynamic ->
                console.log("POL data received:", pols)
                val polSelect = document.getElementById("polPort") as HTMLSelectElement?
                if (polSelect != null) {
                    val currentPolValue = (polSelect.value ?: "").toString().trim()
                    val statePolValue = (carBookingFormState.polPort as? String ?: "").trim()
                    val preservedPolValue = if (currentPolValue.isNotEmpty()) currentPolValue else statePolValue

                    // Clear existing options except the first one
                    polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"
                    
                    // Add POL values from API
                    val polsArray = pols as Array<dynamic>
                    val normalizedPols = polsArray.map { (it?.toString() ?: "").trim() }.filter { it.isNotEmpty() }
                    normalizedPols.forEach { pol ->
                        val option = document.createElement("option") as HTMLOptionElement
                        option.setAttribute("value", pol)
                        option.textContent = pol
                        polSelect.appendChild(option)
                    }

                    // Auto-fill POL based on selected country:
                    // - keep preservedPolValue if it still exists in this country's options
                    // - otherwise select the first available POL
                    val preservedExists = preservedPolValue.isNotEmpty() && normalizedPols.any { it == preservedPolValue }
                    val desiredPol = if (preservedExists) preservedPolValue else normalizedPols.firstOrNull() ?: ""

                    if (desiredPol.isNotEmpty()) {
                        polSelect.value = desiredPol
                        carBookingFormState.polPort = desiredPol
                        val changeEvent = js("new Event('change', { bubbles: true })")
                        polSelect.dispatchEvent(changeEvent.unsafeCast<Event>())
                    } else {
                        carBookingFormState.polPort = ""
                    }

                    console.log("✅ POL values loaded: ${normalizedPols.size} POLs for country: $countryParam (selected: '${polSelect.value}')")
                }
            }
            .catch { error: dynamic ->
                console.error("Error loading POL values:", error)
                loadStockLocationsFallback()
            }
    } else {
        // No country specified, load all POL values from all purchases
        window.fetch(apiUrl("purchases"))
            .then { response: dynamic ->
                console.log("All purchases API response status:", response.status)
                if (response.ok) {
                    response.json()
                } else {
                    console.log("Purchases API failed, using fallback")
                    js("Promise.resolve([])")
                }
            }
            .then { purchases: dynamic ->
                val purchasesArray = purchases as Array<dynamic>
                val polsSet = mutableSetOf<String>()
                purchasesArray.forEach { purchase ->
                    val pol = js("purchase.pol")?.toString() ?: ""
                    if (pol.isNotEmpty()) {
                        polsSet.add(pol)
                    }
                }
                console.log("All POL data extracted:", polsSet)
                val polSelect = document.getElementById("polPort") as HTMLSelectElement?
                if (polSelect != null) {
                    val currentPolValue = (polSelect.value ?: "").toString().trim()
                    val statePolValue = (carBookingFormState.polPort as? String ?: "").trim()
                    val preservedPolValue = if (currentPolValue.isNotEmpty()) currentPolValue else statePolValue

                    // Clear existing options except the first one
                    polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"
                    
                    // Add POL values
                    val normalizedPols = polsSet.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
                    normalizedPols.forEach { pol ->
                        val option = document.createElement("option") as HTMLOptionElement
                        option.setAttribute("value", pol)
                        option.textContent = pol
                        polSelect.appendChild(option)
                    }

                    // Auto-fill POL (preserve if possible, else first option)
                    val preservedExists = preservedPolValue.isNotEmpty() && normalizedPols.any { it == preservedPolValue }
                    val desiredPol = if (preservedExists) preservedPolValue else normalizedPols.firstOrNull() ?: ""
                    if (desiredPol.isNotEmpty()) {
                        polSelect.value = desiredPol
                        carBookingFormState.polPort = desiredPol
                        val changeEvent = js("new Event('change', { bubbles: true })")
                        polSelect.dispatchEvent(changeEvent.unsafeCast<Event>())
                    } else {
                        carBookingFormState.polPort = ""
                    }

                    console.log("POL values loaded from purchases: ${normalizedPols.size} POLs (selected: '${polSelect.value}')")
                }
            }
            .catch { error: dynamic ->
                console.error("Error loading POL values:", error)
                loadStockLocationsFallback()
            }
    }
}

fun loadStockLocationsFallback() {
    console.log("Loading fallback stock location data...")
    
    val polSelect = document.getElementById("polPort") as HTMLSelectElement?
    if (polSelect == null) {
        console.error("POL select element not found in fallback!")
        return
    }
    
    console.log("Clearing POL select and adding default option...")
    polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"
    
    // Stock location data from your CSV file - unique locations
    val fallbackStockLocations = listOf(
        "GLOBAL KAWASAKI",
        "GLOBAL HAKATA", 
        "GLOBAL NAGOYA",
        "KLC",
        "BARAKI",
        "-"
    )
    
    console.log("Adding", fallbackStockLocations.size, "stock locations to dropdown...")
    
    fallbackStockLocations.forEach { location ->
        val option = document.createElement("option")
        option.setAttribute("value", location)
        option.textContent = location
        polSelect.appendChild(option)
        console.log("Added stock location:", location)
    }
    
    console.log("Fallback stock locations loaded successfully:", fallbackStockLocations.size, "locations")
    console.log("Final POL select options count:", polSelect.options.length)
}

fun loadFilteredChassis() {
    val polSelect = document.getElementById("polPort") as HTMLSelectElement?
    val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
    val chassisSelect = document.getElementById("chassisSearch") as HTMLSelectElement?
    val chassisInput = document.getElementById("chassisSearchInput") as HTMLInputElement?
    
    if (chassisSelect == null) {
        Logger.error("Chassis select element not found!")
        return
    }
    
    val selectedPol = polSelect?.value ?: ""
    val selectedCountry = countrySelect?.value ?: ""
    
    // Both country and POL are required for filtering
    if (selectedPol.isEmpty() || selectedCountry.isEmpty()) {
        Logger.debug("Country or POL not selected, clearing chassis data (Country: '$selectedCountry', POL: '$selectedPol')")
        // Clear combobox dropdown
        chassisSelect.innerHTML = "<option value=\"\">▼</option>"
        if (chassisInput != null) {
            chassisInput.value = ""
        }
        return
    }
    
    Logger.debug("Loading unshipped chassis for Country: $selectedCountry, POL: $selectedPol")
    
    val encodedCountry = js("encodeURIComponent")(selectedCountry) as String
    val encodedPol = js("encodeURIComponent")(selectedPol) as String
    val url = apiUrl("purchases/filtered-chassis?country=$encodedCountry&polPort=$encodedPol")
    console.log("🔍 Fetching filtered chassis from:", url)
    window.fetch(url)
        .then { response: dynamic ->
            console.log("Filtered chassis API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                console.log("Filtered chassis API failed, using fallback")
                js("Promise.resolve([])")
            }
        }
        .then { chassis: dynamic ->
            console.log("Filtered chassis data received:", chassis)
            
            // Populate combobox dropdown with chassis options
            val chassisArray = chassis as Array<dynamic>
            chassisSelect.innerHTML = "<option value=\"\">▼</option>"
            chassisArray.forEach { chassisValue ->
                val chassisStr = chassisValue.toString()
                val option = js("new Option(chassisStr, chassisStr)")
                chassisSelect.appendChild(option.unsafeCast<HTMLOptionElement>())
            }
            console.log("✅ Chassis dropdown populated with ${chassisArray.size} options (filtered by Country: $selectedCountry, POL: $selectedPol)")
        }
        .catch { error: dynamic ->
            console.error("Error loading filtered chassis:", error)
            loadFilteredChassisFallback()
        }
}

fun loadFilteredChassisFallback() {
    console.log("Using fallback chassis data")
    // Fallback implementation can be added if needed
}

fun loadFilteredPurchasesIntoTable() {
    val polSelect = document.getElementById("polPort") as? HTMLSelectElement
    val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
    val selectedPol = polSelect?.value ?: ""
    val selectedCountry = countrySelect?.value ?: ""
    if (selectedPol.isEmpty() || selectedCountry.isEmpty()) {
        Logger.debug("Country or POL empty, clearing list table")
        clearBookingListTable()
        return
    }
    val encodedCountry = js("encodeURIComponent")(selectedCountry) as String
    val encodedPol = js("encodeURIComponent")(selectedPol) as String
    val url = apiUrl("purchases/filtered-purchases?country=$encodedCountry&polPort=$encodedPol")
    Logger.debug("Fetching filtered purchases from: $url")
    window.fetch(url)
        .then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                console.error("Filtered purchases API failed:", response.status)
                js("Promise.resolve([])")
            }
        }
        .then { purchases: dynamic ->
            val purchasesArray = js("Array.isArray(purchases) ? purchases : (purchases ? [purchases] : [])") as Array<dynamic>
            Logger.debug("Loaded ${purchasesArray.size} purchases for Country: $selectedCountry, POL: $selectedPol")
            clearBookingListTable()
            if (purchasesArray.isNotEmpty()) {
                displayPurchasesAsCarsAPPEND(purchasesArray)
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading filtered purchases: $error")
        }
}

fun clearBookingListTable() {
    carBookingDisplayedCars = emptyArray()
    val tbody = document.getElementById("carSelectionTableBody")
    tbody?.innerHTML = ""
    Logger.debug("Cleared booking list table")
}

fun hideChassisSuggestions() {
    val suggestionsDiv = document.getElementById("chassisSuggestions")
    if (suggestionsDiv != null) {
        (suggestionsDiv as HTMLElement).style.display = "none"
    }
}

/** Fetch chassis suggestions from entire purchase table by search query (not filtered by Country & POL). */
fun fetchChassisSuggestionsForBooking(query: String) {
    if (query.trim().isEmpty()) {
        hideChassisSuggestions()
        return
    }
    val encoded = js("encodeURIComponent")(query.trim()) as String
    val url = apiUrl("purchases/search?query=$encoded")
    window.fetch(url)
        .then { response: dynamic ->
            if (response.ok) response.json() else js("Promise.resolve([])")
        }
        .then { data: dynamic ->
            val arr = js("Array.isArray(data) ? data : (data ? [data] : [])") as Array<dynamic>
            val chassisSet = mutableSetOf<String>()
            for (i in arr.indices) {
                val ch = (js("arr[i].chassis") as? String) ?: ""
                if (ch.isNotBlank()) chassisSet.add(ch)
            }
            showChassisSuggestions(chassisSet.toTypedArray())
        }
        .catch { _: dynamic ->
            hideChassisSuggestions()
        }
}

fun searchCarsByChassis(chassis: String) {
    Logger.debug("Searching cars by chassis: $chassis")
    
    if (chassis == null || chassis == "" || chassis.trim() == "") {
        Logger.debug("Empty chassis, skipping search (table remains unchanged)")
        return
    }
    
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/search?query=$encodedChassis")
    Logger.debug("Fetching from URL: $url")
    
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<Array<dynamic>>("purchases/search?query=$encodedChassis")
        result.fold(
            onSuccess = { purchases ->
                Logger.debug("Search results received: ${purchases.size} items")
                
                // Check if purchases is an array
                val isArray = js("Array.isArray")(purchases).unsafeCast<Boolean>()
                if (isArray) {
                    val purchasesArray = purchases as Array<dynamic>
                    Logger.debug("Purchases array size: ${purchasesArray.size}")
                    if (purchasesArray.isNotEmpty()) {
                        Logger.debug("Displaying ${purchasesArray.size} purchase(s) in table")
                        try {
                            displayPurchasesAsCarsAPPEND(purchases)
                            Logger.debug("Successfully called displayPurchasesAsCarsAPPEND")
                        } catch (e: dynamic) {
                            Logger.error("Error displaying purchases: $e")
                        }
                    } else {
                        Logger.debug("No purchases found for chassis: $chassis")
                        // Don't clear table - keep existing entries
                    }
                } else {
                    Logger.warn("Invalid response format (not an array)")
                    // Don't clear table - keep existing entries
                }
            },
            onError = { message, _ ->
                Logger.error("Error searching cars: $message")
                searchCarsFallback(chassis)
            }
        )
    }
}

/**
 * Read total C&F or FOB from API/JS purchase (Jackson: camelCase; snake_case fallback).
 * Returns null when both fields are absent/null (do not treat as 0).
 */
private fun readPurchaseTotalNullable(purchase: dynamic, mode: String): Double? {
    if (purchase == null) return null
    // Do NOT call .asDynamic() on a `dynamic` receiver — JS looks up .asDynamic on the object (plain JSON has no such method).
    val p = purchase
    val a: dynamic
    val b: dynamic
    when (mode) {
        "C&F" -> {
            a = p.totalCnfPrice
            b = p.total_cnf_price
        }
        "FOB" -> {
            a = p.totalFobPrice
            b = p.total_fob_price
        }
        else -> return null
    }
    val candidate = firstDefinedNumeric(a, b) ?: return null
    return dynamicToDouble(candidate)
}

/** Convert JSON number/string to Double; null if not parseable. */
private fun dynamicToDouble(v: dynamic): Double? {
    if (v == null) return null
    if (js("v === undefined") as Boolean) return null
    if (js("typeof v === 'number'") as Boolean) {
        return if (js("isNaN(v)") as Boolean) null else (v as Number).toDouble()
    }
    val n = js("parseFloat(String(v))")
    return if (n != null && !(js("isNaN(n)") as Boolean)) (n as Number).toDouble() else null
}

private fun firstDefinedNumeric(a: dynamic, b: dynamic): dynamic? {
    if (isDefinedNumeric(a)) return a
    if (isDefinedNumeric(b)) return b
    return null
}

private fun isDefinedNumeric(v: dynamic): Boolean {
    if (v == null) return false
    if (js("v === undefined") as Boolean) return false
    if (js("typeof v === 'number'") as Boolean) return !(js("isNaN(v)") as Boolean)
    if (js("typeof v === 'string'") as Boolean) {
        val t = js("String(v).trim()") as String
        if (t.isEmpty()) return false
        val n = js("parseFloat(t)")
        return !(js("isNaN(n)") as Boolean)
    }
    return false
}

private fun readPurchaseTotalForList(purchase: dynamic, mode: String): Double {
    return readPurchaseTotalNullable(purchase, mode) ?: 0.0
}

/** Replace or append one purchase in [carBookingDisplayedCars] (used when refresh returns updated totals). */
private fun mergePurchaseIntoDisplayedCars(purchase: dynamic) {
    val chassis = (purchase.chassis as? String)?.trim() ?: return
    if (chassis.isEmpty()) return
    val out = mutableListOf<dynamic>()
    var replaced = false
    for (i in 0 until carBookingDisplayedCars.size) {
        val c = carBookingDisplayedCars[i]
        val ch = (c.chassis as? String)?.trim() ?: ""
        if (ch == chassis) {
            out.add(purchase)
            replaced = true
        } else {
            out.add(c)
        }
    }
    if (!replaced) out.add(purchase)
    carBookingDisplayedCars = out.toTypedArray()
}

/** Update the FOB/C&F column (6th td when price mode is active) for an existing chassis row. */
private fun updateBookingTablePriceCellForChassis(chassisNumber: String, purchase: dynamic) {
    if (lastCalculationMode.isEmpty()) return
    val tbody = document.getElementById("carSelectionTableBody") ?: return
    val rows = tbody.querySelectorAll("tr")
    for (i in 0 until rows.length) {
        val row = rows.item(i) as HTMLElement
        val chassisCell = row.querySelector("td:nth-child(3)") ?: continue
        if (chassisCell.textContent?.trim() != chassisNumber) continue
        val total = readPurchaseTotalNullable(purchase, lastCalculationMode)
        val priceText = if (total != null) formatCurrency(total) else ""
        var priceCell = row.querySelector("td:nth-child(6)") as? HTMLElement
        if (priceCell == null) {
            val td = document.createElement("td") as HTMLElement
            td.setAttribute("style", "padding: 12px; border-bottom: 1px solid #e5e7eb;")
            row.appendChild(td)
            priceCell = td
        }
        priceCell.textContent = priceText
        break
    }
}

fun displayPurchasesAsCarsAPPEND(purchases: dynamic) {
    Logger.debug("Displaying purchases as cars, calculation mode: $lastCalculationMode")
    
    // Save displayed cars data for state persistence - ACCUMULATE instead of overwrite
    val displayedCarsArray = js("Array.isArray(purchases) ? purchases : [purchases]") as Array<dynamic>
    
    // Add new cars to existing displayed cars (avoid duplicates)
    val newCars = mutableListOf<dynamic>()
    for (newCar in displayedCarsArray) {
        val chassis = newCar.chassis
        val alreadyExists = carBookingDisplayedCars.any { it.chassis == chassis }
        if (!alreadyExists) {
            newCars.add(newCar)
        }
    }
    carBookingDisplayedCars = carBookingDisplayedCars + newCars.toTypedArray()
    
    console.log("💾 Accumulated displayed cars for state persistence:", carBookingDisplayedCars.size)
    
    // Debug: Check if table body exists
    val tbody = document.getElementById("carSelectionTableBody")
    console.log("Table body element found:", tbody)
    console.log("Table body element type:", tbody?.tagName)
    
    if (tbody == null) {
        console.error("Car table body not found!")
        return
    }
    
    // Don't clear the table - append new cars instead
    console.log("✅ TABLE NOT CLEARED - APPENDING MODE ACTIVE")
    
    val purchasesArray = js("Array.isArray(purchases) ? purchases : [purchases]") as Array<dynamic>
    console.log("🚀 NEW APPEND LOGIC: Processing", purchasesArray.size, "purchases to ADD to existing table")
    
    // Get current row count to continue numbering
    val currentRowCount = tbody.children.length
    console.log("Current table has", currentRowCount, "rows, adding", purchasesArray.size, "more")
    
    for (index in purchasesArray.indices) {
        val purchase = purchasesArray[index]
        
        // Check if this car is already in the table (avoid duplicates)
        val chassisNumber = purchase.chassis ?: "N/A"
        val existingRows = tbody.querySelectorAll("tr")
        var carAlreadyExists = false
        
        for (i in 0 until existingRows.length) {
            val row = existingRows.item(i) as HTMLElement
            val chassisCell = row.querySelector("td:nth-child(3)") // 3rd column is chassis (after checkbox column)
            if (chassisCell?.textContent?.trim() == chassisNumber) {
                carAlreadyExists = true
                console.log("Car with chassis", chassisNumber, "already exists in table, skipping")
                break
            }
        }
        
        if (carAlreadyExists) {
            mergePurchaseIntoDisplayedCars(purchase)
            updateBookingTablePriceCellForChassis(chassisNumber, purchase)
            continue
        }
        
        val total = readPurchaseTotalNullable(purchase, lastCalculationMode)
        val priceText = if (total != null) formatCurrency(total) else ""
        
        val rowNumber = currentRowCount + index + 1
        val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
        // chassisNumber already declared above at line 740
        
        val row = document.createElement("tr")
        row.setAttribute("data-purchase-id", purchaseId.toString())
        row.innerHTML = """
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">
                <input type="checkbox" class="car-checkbox" data-purchase-id="$purchaseId" data-chassis="$chassisNumber" style="margin-right: 8px;">
            </td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">$rowNumber</td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">$chassisNumber</td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${purchase.carName ?: "N/A"}</td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${formatCarModelYear(purchase.carModelYear?.toString())}</td>
            ${if (lastCalculationMode.isNotEmpty()) "<td style=\"padding: 12px; border-bottom: 1px solid #e5e7eb;\">$priceText</td>" else ""}
        """
        tbody.appendChild(row)
    }
    
    console.log("✅ Added", purchasesArray.size, "new cars to table")
}

fun searchCarsFallback(searchTerm: String) {
    console.log("Using fallback search for term:", searchTerm)
    
    // Sample purchase data for testing
    val samplePurchases = listOf(
        mapOf("id" to 1, "chassis" to "KDH201-5012551", "carName" to "Toyota Camry", "carModelYear" to "2020"),
        mapOf("id" to 2, "chassis" to "KDH201-5012552", "carName" to "Honda Accord", "carModelYear" to "2021"),
        mapOf("id" to 3, "chassis" to "KDH201-5012553", "carName" to "Nissan Altima", "carModelYear" to "2019"),
        mapOf("id" to 4, "chassis" to "KDH201-5012554", "carName" to "BMW 3 Series", "carModelYear" to "2022"),
        mapOf("id" to 5, "chassis" to "KDH201-5012555", "carName" to "Mercedes C-Class", "carModelYear" to "2021")
    )
    
    // Filter by search term
    val filteredPurchases = samplePurchases.filter { purchase ->
        val chassis = purchase["chassis"] as? String ?: ""
        chassis.contains(searchTerm, ignoreCase = true)
    }
    
    console.log("Fallback search found", filteredPurchases.size, "matching purchases")
    displayPurchasesAsCarsAPPEND(filteredPurchases.toTypedArray())
}

fun showCalculateFreightPage() {
    try {
        console.log("🚢 Opening Calculate Freight page...")
        
        // First try to get cars from C&F confirmed cars list
        val selectedCars = if (cnfConfirmedCars.isNotEmpty()) {
            console.log("📋 Using confirmed cars from C&F page:", cnfConfirmedCars.size)
            cnfConfirmedCars
        } else if (fobConfirmedCars.isNotEmpty()) {
            // Second: use cars from FOB confirmed cars list
            console.log("📋 Using confirmed cars from FOB page:", fobConfirmedCars.size)
            fobConfirmedCars
        } else if (cnfPageSelectedCars.isNotEmpty()) {
            // Third: use cars that were passed to C&F page from Car Booking
            console.log("📋 Using cars from C&F page selection:", cnfPageSelectedCars.size)
            cnfPageSelectedCars
        } else {
            // Fallback: try to get selected cars from the car selection table
            console.log("📋 No confirmed cars found, trying to get from car selection table...")
            getSelectedCarsFromTable()
        }
        
        console.log("📋 Selected cars found:", selectedCars.size)
        console.log("📋 Selected cars details:", selectedCars)
        
        if (selectedCars.isEmpty()) {
            js("alert('Please select cars first before calculating freight!')")
            return
        }
        
        // Create the freight calculation page HTML
        val freightPageHTML = createFreightCalculationHTML(selectedCars)
        
        // Replace the main content with freight calculation page
        val mainContent = document.getElementById("content")
        if (mainContent != null) {
            mainContent.innerHTML = freightPageHTML
            setupFreightCalculationListeners(selectedCars)
            
            // Generate container sections with the selected cars
            generateContainerSections(selectedCars)
            
            console.log("✅ Freight calculation page loaded successfully")
        } else {
            console.error("❌ Main content element not found")
        }
        
    } catch (e: dynamic) {
        console.error("❌ Error opening freight calculation page:", e)
        js("alert('Error opening freight calculation page: ' + e.message)")
    }
}

fun getSelectedCarsFromTable(): List<dynamic> {
    val selectedCars = mutableListOf<dynamic>()
    val tableBody = document.getElementById("carSelectionTableBody")
    
    console.log("🔍 Looking for car selection table...")
    console.log("🔍 Table body element:", tableBody)
    
    if (tableBody != null) {
        val rows = tableBody.querySelectorAll("tr")
        console.log("🔍 Found ${rows.length} rows in table")
        
        for (i in 0 until rows.length) {
            val row = rows[i] as HTMLTableRowElement
            val checkbox = row.querySelector("input[type='checkbox']") as HTMLInputElement?
            
            console.log("🔍 Row $i: checkbox found = ${checkbox != null}, checked = ${checkbox?.checked}")
            
            if (checkbox != null && checkbox.checked) {
                val chassisCell = row.cells[2] // Chassis is in the third column (index 2)
                val nameCell = row.cells[3]    // Name is in the fourth column (index 3)
                val yearCell = row.cells[4]    // Year is in the fifth column (index 4)
                
                // Get purchase ID from checkbox data attribute
                val purchaseId = checkbox.getAttribute("data-purchase-id")?.toLongOrNull()
                
                console.log("🔍 Selected car: chassis=${chassisCell?.textContent}, name=${nameCell?.textContent}, year=${yearCell?.textContent}, purchaseId=$purchaseId")
                
                if (chassisCell != null && nameCell != null && yearCell != null) {
                    val carObject = js("{}")
                    carObject.chassis = chassisCell.textContent
                    carObject.name = nameCell.textContent
                    carObject.year = yearCell.textContent
                    carObject.price = 0 // Will be populated from API
                    if (purchaseId != null) {
                        carObject.id = purchaseId
                        carObject.purchaseId = purchaseId
                    }
                    selectedCars.add(carObject)
                }
            }
        }
    } else {
        console.error("❌ Car selection table body not found!")
    }
    
    console.log("🔍 Total selected cars: ${selectedCars.size}")
    return selectedCars
}

fun getSelectedPurchaseIds(): List<Long> {
    val selectedIds = mutableListOf<Long>()
    val tableBody = document.getElementById("carSelectionTableBody")
    
    console.log("🔍 getSelectedPurchaseIds() called")
    console.log("🔍 Table body element:", tableBody)
    
    if (tableBody != null) {
        // Try to find checkboxes with class 'car-checkbox' first
        var checkboxes = tableBody.querySelectorAll("input[type='checkbox'].car-checkbox")
        console.log("🔍 Found ${checkboxes.length} checkboxes with class 'car-checkbox'")
        
        // If none found, try finding all checkboxes in the table
        if (checkboxes.length == 0) {
            checkboxes = tableBody.querySelectorAll("input[type='checkbox']")
            console.log("🔍 No 'car-checkbox' class found, trying all checkboxes: ${checkboxes.length} found")
        }
        
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            console.log("🔍 Checkbox $i: checked=${checkbox.checked}, data-purchase-id=${checkbox.getAttribute("data-purchase-id")}")
            
            if (checkbox.checked) {
                // Try to get purchase ID from data attribute
                var purchaseId = checkbox.getAttribute("data-purchase-id")?.toLongOrNull()
                
                // If not found, try to get it from the row's data attribute or from the purchase object
                if (purchaseId == null) {
                    val row = checkbox.closest("tr") as? HTMLTableRowElement
                    if (row != null) {
                        purchaseId = row.getAttribute("data-purchase-id")?.toLongOrNull()
                        console.log("🔍 Trying row data-purchase-id: $purchaseId")
                    }
                }
                
                // If still not found, try to find the purchase ID from the displayed cars data
                if (purchaseId == null) {
                    val chassisCell = (checkbox.closest("tr") as? HTMLTableRowElement)?.cells?.get(2)
                    val chassis = chassisCell?.textContent?.trim()
                    if (chassis != null) {
                        // Find the purchase in carBookingDisplayedCars
                        for (car in carBookingDisplayedCars) {
                            if (car.chassis == chassis) {
                                purchaseId = (car.id as? Number)?.toLong()
                                console.log("🔍 Found purchase ID from displayed cars: $purchaseId for chassis $chassis")
                                break
                            }
                        }
                    }
                }
                
                if (purchaseId != null) {
                    selectedIds.add(purchaseId)
                    console.log("✅ Added purchase ID: $purchaseId")
                } else {
                    console.warn("⚠️ Could not find purchase ID for checked checkbox")
                }
            }
        }
    } else {
        console.error("❌ Car selection table body not found!")
    }
    
    console.log("🔍 Selected purchase IDs: $selectedIds")

    if (selectedIds.isEmpty()) {
        console.warn("⚠️ No checkboxes selected. Falling back to all displayed cars.")
        val fallbackIds = mutableListOf<Long>()
        for (car in carBookingDisplayedCars) {
            val id = (car.id as? Number)?.toLong()
            if (id != null) {
                fallbackIds.add(id)
            }
        }
        if (fallbackIds.isNotEmpty()) {
            selectedIds.addAll(fallbackIds)
            console.log("✅ Defaulted to all displayed cars: $selectedIds")
        } else {
            console.warn("⚠️ No displayed cars available for fallback.")
        }
    }

    return selectedIds
}

fun updateSelectedPurchasesWithBookingData(
    purchaseIds: List<Long>,
    etd: String,
    bookingNo: String,
    vessel: String,
    destination: String,
    consignee: String,
    onComplete: () -> Unit
) {
    if (purchaseIds.isEmpty()) {
        console.log("⚠️ No purchase IDs to update")
        onComplete()
        return
    }
    
    console.log("🔄 Updating ${purchaseIds.size} purchases with booking data...")
    console.log("   ETD → shipmentDate: $etd")
    console.log("   BOOKING NO → bookingId (raw): '$bookingNo'")
    console.log("   BOOKING NO → bookingId (trimmed): '${bookingNo.trim()}'")
    console.log("   VESSEL → vessel: $vessel")
    console.log("   POD → destination: $destination")
    console.log("   CONSIGNEE → consignee: $consignee")
    
    var completedUpdates = 0
    val totalUpdates = purchaseIds.size
    
    // Convert bookingNo to Long if it's numeric, otherwise null
    val bookingNoTrimmed = bookingNo.trim()
    val bookingIdLong = if (bookingNoTrimmed.isNotEmpty()) {
        bookingNoTrimmed.toLongOrNull()
    } else {
        null
    }
    console.log("   BOOKING ID (converted): $bookingIdLong")
    console.log("   📝 Will update: booking_id=$bookingIdLong for ${purchaseIds.size} purchase(s)")
    
    // Update each purchase sequentially (one after another)
    fun updateNext(index: Int) {
        if (index >= purchaseIds.size) {
            console.log("✅ All purchases updated successfully")
            onComplete()
            return
        }
        
        val purchaseId = purchaseIds[index]
        val payload = js("{}")
        // Map form fields to database columns:
        // ETD → shipmentDate
        payload["shipmentDate"] = etd
        // BOOKING NO → bookingId (update booking_id column)
        payload["bookingId"] = bookingIdLong
        // VESSEL → vessel
        payload["vessel"] = vessel
        // POD → destination
        payload["destination"] = destination
        // CONSIGNEE → consignee
        payload["consignee"] = formatConsigneeForUpdate(consignee, currentSelectedCountry)
        // Note: shipped is set via the Shipped button, not Calculate
        // POL is not sent to database (not needed)
        
        Logger.debug("Sending update payload for purchase $purchaseId")
        Logger.debug("Payload contents: bookingId=$bookingIdLong, shipmentDate=$etd, vessel=$vessel, destination=$destination")
        console.log("📦 Update payload for purchase $purchaseId:", JSON.stringify(payload))
        
        val req = js("({})")
        req.method = "PUT"
        val headers = js("({})")
        headers["Content-Type"] = "application/json"
        req.headers = headers
        req.body = JSON.stringify(payload)
        
        window.fetch(apiUrl("purchases/$purchaseId"), req)
            .then { response: dynamic ->
                if (response.ok) {
                    completedUpdates++
                    Logger.debug("✅ Successfully updated purchase $purchaseId ($completedUpdates/$totalUpdates) - booking data updated")
                    console.log("✅ Purchase $purchaseId updated: booking_id=$bookingIdLong")
                    updateNext(index + 1)
                } else {
                    response.text().then { errorText: dynamic ->
                        Logger.error("❌ Failed to update purchase $purchaseId: $errorText")
                        console.error("❌ Failed to update purchase $purchaseId:", errorText)
                        updateNext(index + 1) // Continue with next purchase even if this one failed
                    }
                }
            }
            .catch { error: dynamic ->
                Logger.error("Error updating purchase $purchaseId: $error")
                updateNext(index + 1) // Continue with next purchase even if this one failed
            }
    }
    
    updateNext(0)
}

fun updateChassisDropdown() {
    Logger.debug("Updating chassis dropdown...")
    // Note: This function is for a different purpose (updating selected cars)
    // The chassis search combobox uses "chassisSearch" ID, not "chassisSelect"
    val chassisSelect = document.getElementById("chassisSelect") as? HTMLSelectElement
    if (chassisSelect == null) {
        // This is expected - chassisSelect might not exist on booking page
        // Only log if we're actually trying to update it
        return
    }
    
    val selectedChassis = mutableListOf<String>()
    
    // Get all checked cars from the table
    val checkboxes = document.querySelectorAll("#carSelectionTableBody input[type='checkbox']:checked")
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as? HTMLInputElement
        if (checkbox != null) {
            try {
                // closest() returns Element?, need to check if it's a table row
                val rowElement = checkbox.closest("tr")
                if (rowElement != null) {
                    // Use querySelector to get the chassis cell (3rd column, index 2)
                    val chassisCell = rowElement.querySelector("td:nth-child(3)")
                    if (chassisCell != null) {
                        val chassisText = chassisCell.textContent
                        val chassisNumber = chassisText?.trim() ?: ""
                        if (chassisNumber.isNotEmpty()) {
                            selectedChassis.add(chassisNumber)
                        }
                    }
                }
            } catch (e: dynamic) {
                Logger.error("Error extracting chassis from row: $e")
            }
        }
    }
    
    // Clear and repopulate dropdown using innerHTML for safety
    chassisSelect.innerHTML = "<option value=\"\">Select a car chassis...</option>"
    
    // Add selected chassis options using innerHTML (more reliable than appendChild)
    for (chassis in selectedChassis) {
        val option = document.createElement("option")
        option.setAttribute("value", chassis)
        option.textContent = chassis
        chassisSelect.appendChild(option)
    }
    
    Logger.debug("Chassis dropdown updated with ${selectedChassis.size} selected cars")
}

fun addCarToBookingTable(chassis: String) {
    Logger.debug("Adding car to booking table for chassis: $chassis")
    
    // Check if car already exists in table
    val tbody = document.getElementById("carSelectionTableBody")
    if (tbody != null) {
        val existingRows = tbody.querySelectorAll("tr")
        for (i in 0 until existingRows.length) {
            val row = existingRows.item(i) as HTMLElement
            val chassisCell = row.querySelector("td:nth-child(3)") // 3rd column is chassis
            if (chassisCell?.textContent?.trim() == chassis.trim()) {
                Logger.debug("Car with chassis $chassis already exists in table")
                showMessage("Car with chassis $chassis is already in the list", "info")
                return
            }
        }
    }
    
    // Fetch purchase data by chassis
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/chassis/$encodedChassis")
    Logger.debug("Fetching purchase data from: $url")
    
    window.fetch(url)
        .then { response: dynamic ->
            Logger.debug("Fetch response status: ${response.status}")
            if (response.ok) {
                response.json()
            } else {
                Logger.debug("Purchase not found by chassis, trying search API")
                // Fallback to search API
                val searchUrl = apiUrl("purchases/search?query=$encodedChassis")
                window.fetch(searchUrl)
                    .then { searchResponse: dynamic ->
                        if (searchResponse.ok) {
                            searchResponse.json()
                        } else {
                            js("Promise.resolve([])")
                        }
                    }
            }
        }
        .then { purchaseData: dynamic ->
            Logger.debug("Purchase data received")
            
            // Handle both single purchase and array of purchases
            val purchasesArray = if (js("Array.isArray")(purchaseData).unsafeCast<Boolean>()) {
                purchaseData as Array<dynamic>
            } else if (purchaseData != null) {
                arrayOf(purchaseData)
            } else {
                emptyArray()
            }
            
            if (purchasesArray.isNotEmpty()) {
                Logger.debug("Adding ${purchasesArray.size} car(s) to table")
                displayPurchasesAsCarsAPPEND(purchasesArray)
                val chassisInput = document.getElementById("chassisSearchInput") as? HTMLInputElement
                chassisInput?.value = ""
            } else {
                Logger.debug("No purchase found for chassis: $chassis")
                showMessage("No purchase found for chassis: $chassis", "warning")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error fetching purchase by chassis: $error")
            showMessage("Error fetching car data: ${error.message}", "error")
        }
}

