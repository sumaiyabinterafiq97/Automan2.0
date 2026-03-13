package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event

// C&F/FOB Calculation Functions
// Note: Global variables (cnfPageIsFobMode, cnfPageSelectedCars, etc.) are defined in MinimalPurchaseApp.kt

fun showCnfCalculationPage(selectedChassis: String? = null, selectedCars: List<dynamic>? = null, selectedCountry: String = "PAKISTAN", isFobMode: Boolean = false) {
    // Store FOB mode flag globally
    cnfPageIsFobMode = isFobMode
    
    if (isFobMode) {
        console.log("💰 Opening C&F Calculation page (FOB MODE)...")
    } else {
        console.log("💰 Opening C&F Calculation page...")
    }
    
    // Store the selected cars globally so Calculate Freight can access them
    cnfPageSelectedCars = selectedCars ?: emptyList()
    cnfPageSelectedCountry = selectedCountry // Store the selected country
    console.log("📋 Stored ${cnfPageSelectedCars.size} selected cars for C&F page")
    console.log("🌍 Stored selected country:", selectedCountry)
    console.log("🔧 FOB Mode:", isFobMode)
    
    val cnfPageHTML = createCnfCalculationHTML(isFobMode)
    
    // Replace the main content with C&F calculation page
    val mainContent = document.getElementById("content")
    if (mainContent != null) {
        mainContent.innerHTML = cnfPageHTML
        setupCnfCalculationListeners(selectedChassis, selectedCars, isFobMode)
        
        if (isFobMode) {
            console.log("✅ C&F Calculation page loaded successfully (FOB MODE)")
        } else {
            console.log("✅ C&F Calculation page loaded successfully")
        }
    } else {
        console.error("❌ Main content element not found")
    }
}

fun createCnfCalculationHTML(isFobMode: Boolean = false): String {
    val pageTitle = if (isFobMode) "FOB CALCULATION" else "C&F CALCULATION"
    val totalPriceLabel = if (isFobMode) "TOTAL FOB PRICE (¥):" else "TOTAL C&F PRICE (¥):"
    val calculateFreightButton = if (isFobMode) "" else """
                    <!-- Calculate Freight Button -->
                    <div style="margin-top: 15px;">
                        <button id="calculateFreightBtn" class="cnf-btn cnf-btn-freight">CALCULATE FREIGHT</button>
                    </div>
"""
    val freightField = if (isFobMode) "" else """
                            <div class="cnf-cost-field">
                                <label>FREIGHT (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="freight" value="0" min="0" step="1">
                                </div>
                            </div>
"""
    return """
        <div class="cnf-calculation-container">
            <!-- Back Button -->
            <div style="margin-bottom: 20px;">
                <button id="backToBookingBtn" class="cnf-back-btn">← Back to Car Booking</button>
            </div>
            
            <!-- C&F Calculation Container -->
            <div class="cnf-card">
                
                <!-- Header -->
                <div class="cnf-header">
                    <h1>$pageTitle</h1>
                </div>
                
                <!-- Car Selection -->
                <div class="cnf-chassis-selector">
                    <label>SELECT CAR CHASSIS :</label>
                    <select id="chassisSelect">
                        <option value="">Select a car chassis...</option>
                    </select>
                    $calculateFreightButton
                </div>
                
                <!-- Cost Fields -->
                <div class="cnf-cost-fields">
                    <div class="cnf-cost-grid">
                        <!-- Left Column -->
                        <div>
                            <div class="cnf-cost-field">
                                <label>CAR PRICE (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="carPrice" value="0" min="0" step="1">
                                </div>
                            </div>
                            
                            <div class="cnf-cost-field">
                                <label>RIXO PRICE (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="rixoPrice" value="0" min="0" step="1">
                                </div>
                            </div>
                            $freightField
                            <div class="cnf-cost-field">
                                <label>REPAIR FEE (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="repairFee" value="0" min="0" step="1">
                                </div>
                            </div>
                            
                            <div class="cnf-cost-field">
                                <label>PROFIT (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="profit" value="0" min="0" step="1">
                                </div>
                            </div>
                        </div>
                        
                        <!-- Right Column -->
                        <div>
                            <div class="cnf-cost-field">
                                <label>AUCTION FEE (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="auctionFee" value="0" min="0" step="1">
                                </div>
                            </div>
                            
                            <div class="cnf-cost-field">
                                <label>SHIPPING CHARGE (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="shippingCharge" value="0" min="0" step="1">
                                </div>
                            </div>
                            
                            <div class="cnf-cost-field">
                                <label>INSPECTION FEE (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="inspectionFee" value="0" min="0" step="1">
                                </div>
                            </div>
                            
                            <div class="cnf-cost-field">
                                <label>MSC. CHARGES (¥):</label>
                                <div class="input-with-prefix">
                                    <span>¥</span>
                                    <input type="number" id="mscCharges" value="0" min="0" step="1">
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Total Calculations -->
                    <div class="cnf-totals">
                        <div class="cnf-totals-grid">
                            <!-- Total C&F/FOB Price -->
                            <div class="cnf-total-box">
                                <label id="totalPriceLabel">$totalPriceLabel</label>
                                <div id="totalCnfPrice" class="cnf-total-value green">0</div>
                            </div>
                            
                            <!-- Total Expense -->
                            <div class="cnf-total-box">
                                <label>TOTAL EXPENSE (¥):</label>
                                <div id="totalExpense" class="cnf-total-value red">0</div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Action Buttons -->
                    <div class="cnf-action-buttons">
                        <button id="saveCarCostsBtn" class="cnf-btn cnf-btn-save">SAVE</button>
                        <button id="previewPdfBtn" class="cnf-btn cnf-btn-preview">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                                <circle cx="12" cy="12" r="3"></circle>
                            </svg>
                            PREVIEW
                        </button>
                        <button id="downloadPdfBtn" class="cnf-btn cnf-btn-download">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                                <polyline points="14,2 14,8 20,8"></polyline>
                                <line x1="16" y1="13" x2="8" y2="13"></line>
                                <line x1="16" y1="17" x2="8" y2="17"></line>
                            </svg>
                            DOWNLOAD PDF
                        </button>
                        <button id="confirmCarCostsBtn" class="cnf-btn cnf-btn-finish">FINISH</button>
                    </div>
                </div>
            </div>
        </div>
    """
}

fun setupCnfCalculationListeners(selectedChassis: String? = null, selectedCars: List<dynamic>? = null, isFobMode: Boolean = false) {
    console.log("🔧 Setting up C&F calculation listeners...")
    
    // Back to booking button
    document.getElementById("backToBookingBtn")?.addEventListener("click", { _: Event ->
        // Save C&F form state before navigating back
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        // Save state before navigating back to Car Booking page
        js("if (window.saveCarBookingState) window.saveCarBookingState()")
        showCarBookingPage()
    })
    
    // Load chassis dropdown with available cars
    loadChassisDropdownForCnf(selectedCars)
    
    // Track previous chassis to save state before switching (declared before use)
    var previousChassis: String = ""
    
    // Auto-select first chassis if available and no specific chassis provided
    window.setTimeout({
        val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
        if (chassisSelect != null && chassisSelect.options.length > 1) {
            if (!selectedChassis.isNullOrEmpty()) {
                // Use provided chassis
                chassisSelect.value = selectedChassis
                previousChassis = selectedChassis // Initialize previous chassis tracker
                console.log("📋 Setting chassis to:", selectedChassis)
            } else {
                // Auto-select first available car
                chassisSelect.selectedIndex = 1 // Select first actual option (skip "Select Chassis")
                previousChassis = chassisSelect.value // Initialize previous chassis tracker
                console.log("📋 Auto-selecting first chassis:", chassisSelect.value)
            }
            // Load car cost details for the selected chassis
            loadCarCostDetails()
        } else {
            console.log("⚠️ No chassis options available in dropdown")
        }
    }, 300) // Delay to ensure dropdown is populated
    
    // Chassis selection dropdown - load car cost details
    document.getElementById("chassisSelect")?.addEventListener("change", { event: Event ->
        val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
        val selectedChassis = chassisSelect?.value ?: ""
        
        // Save state for PREVIOUS chassis before switching (if it exists and is different)
        if (previousChassis.isNotEmpty() && previousChassis != selectedChassis) {
            console.log("💾 Saving state for previous chassis before switching: $previousChassis")
            // Get current form values before they're cleared
            val currentCarPrice = (document.getElementById("carPrice") as? HTMLInputElement)?.value ?: "0"
            val currentAuctionFee = (document.getElementById("auctionFee") as? HTMLInputElement)?.value ?: "0"
            val currentRixoPrice = (document.getElementById("rixoPrice") as? HTMLInputElement)?.value ?: "0"
            val currentShippingCharge = (document.getElementById("shippingCharge") as? HTMLInputElement)?.value ?: "0"
            val currentFreight = (document.getElementById("freight") as? HTMLInputElement)?.value ?: "0"
            val currentInspectionFee = (document.getElementById("inspectionFee") as? HTMLInputElement)?.value ?: "0"
            val currentRepairFee = (document.getElementById("repairFee") as? HTMLInputElement)?.value ?: "0"
            val currentMscCharges = (document.getElementById("mscCharges") as? HTMLInputElement)?.value ?: "0"
            val currentProfit = (document.getElementById("profit") as? HTMLInputElement)?.value ?: "0"
            
            // Save state for previous chassis directly
            val windowCnfFormState = js("window.cnfFormState")
            if (windowCnfFormState == null || js("typeof window.cnfFormState") == "undefined") {
                js("window.cnfFormState = {}")
            }
            val previousChassisState = js("{}")
            previousChassisState.carPrice = currentCarPrice
            previousChassisState.auctionFee = currentAuctionFee
            previousChassisState.rixoPrice = currentRixoPrice
            previousChassisState.shippingCharge = currentShippingCharge
            previousChassisState.freight = currentFreight
            previousChassisState.inspectionFee = currentInspectionFee
            previousChassisState.repairFee = currentRepairFee
            previousChassisState.mscCharges = currentMscCharges
            previousChassisState.profit = currentProfit
            js("window.cnfFormState[previousChassis] = previousChassisState")
            console.log("✅ Saved state for previous chassis $previousChassis:", previousChassisState)
        }
        
        // Update previous chassis tracker
        previousChassis = selectedChassis
        
        if (selectedChassis.isNotEmpty()) {
            loadCarCostDetails()
            // Populate freight from globalFreightValues after loading other fields
            window.setTimeout({
                val freightField = document.getElementById("freight") as? HTMLInputElement
                if (freightField != null && !isFobMode) {
                    val freightFromGlobal = globalFreightValues[selectedChassis] ?: 0.0
                    freightField.value = freightFromGlobal.toInt().toString()
                    if (freightFromGlobal > 0.0) {
                        console.log("🚢 Populated freight from globalFreightValues for chassis $selectedChassis: ¥${freightFromGlobal.toInt()}")
                    } else {
                        console.log("🚢 No freight value in globalFreightValues for chassis $selectedChassis, leaving as 0")
                    }
                    calculateCnfTotal()
                }
                // Restore saved state for THIS SPECIFIC chassis after loading database values
                window.setTimeout({
                    console.log("🔄 Restoring state for chassis: $selectedChassis")
                    js("if (window.restoreCnfFormState) window.restoreCnfFormState()")
                }, 500)
            }, 100)
        } else {
            clearCostFields()
        }
    })
    
    // Add event listeners to save state on field changes
    val fieldIds = listOf("carPrice", "auctionFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    fieldIds.forEach { fieldId ->
        document.getElementById(fieldId)?.addEventListener("input", { _: Event ->
            // Debounce: save state after user stops typing (500ms delay)
            window.clearTimeout(js("window.cnfFormStateSaveTimeout"))
            js("window.cnfFormStateSaveTimeout = setTimeout(function() { if (window.saveCnfFormState) window.saveCnfFormState(); }, 500)")
        })
    }
    
    // After page loads, check if there are freight values and populate them (only in C&F mode)
    if (!isFobMode) {
        window.setTimeout({
            val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
            val currentChassis = chassisSelect?.value ?: ""
            // Access globalFreightValues from MinimalPurchaseApp.kt
            if (currentChassis.isNotEmpty() && globalFreightValues.containsKey(currentChassis)) {
                val freightValue = globalFreightValues[currentChassis] ?: 0.0
                val freightField = document.getElementById("freight") as HTMLInputElement?
                if (freightField != null) {
                    freightField.value = freightValue.toDouble().toInt().toString()
                    console.log("🚢 Auto-populated freight field from globalFreightValues: ¥${freightValue.toDouble().toInt()}")
                    // Recalculate total with freight
                    calculateCnfTotal()
                }
            } else if (currentChassis.isNotEmpty()) {
                console.log("🚢 No freight value found in globalFreightValues for chassis $currentChassis")
            }
        }, 800) // Delay to ensure fields are loaded
    }
    
    // Restore C&F form state after page loads - with longer delay to ensure database values are loaded first
    // Then restore edited values on top of database values
    window.setTimeout({
        js("if (window.restoreCnfFormState) window.restoreCnfFormState()")
    }, 1500) // Increased delay to ensure loadCarCostDetails completes first
    
    // Save button - save calculated Total C&F Price (as per documentation: only saves total price, not cost details)
    document.getElementById("saveCarCostsBtn")?.addEventListener("click", { _: Event ->
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        // Per documentation: SAVE button should only save total C&F/FOB price, not cost details
        saveTotalCnfPrice()
    })
    
    // Calculate Freight button (only in C&F mode) - save state before navigating
    document.getElementById("calculateFreightBtn")?.addEventListener("click", { _: Event ->
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
    })
    
    // Calculate Freight button (only in C&F mode)
    if (!isFobMode) {
        document.getElementById("calculateFreightBtn")?.addEventListener("click", { _: Event ->
            showCalculateFreightPage()
        })
    }
    
    // Add input listeners for real-time calculation
    val costFields = listOf("carPrice", "auctionFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    for (fieldId in costFields) {
        val field = document.getElementById(fieldId) as? HTMLInputElement
        if (field != null) {
            field.addEventListener("input", { _: Event ->
                calculateCnfTotal()
            })
        }
    }
    
    // Preview PDF button
    document.getElementById("previewPdfBtn")?.addEventListener("click", { _: Event ->
        console.log("📄 Preview PDF button clicked")
        generateShippingSchedulePdfPreview()
    })
    
    // Download PDF button
    document.getElementById("downloadPdfBtn")?.addEventListener("click", { _: Event ->
        console.log("📥 Download PDF button clicked")
        generateShippingSchedulePdf()
    })
    
    // Confirm/FINISH button (as per documentation: should NOT save total price, should refresh data and return)
    document.getElementById("confirmCarCostsBtn")?.addEventListener("click", { _: Event ->
        // Per documentation (Line 995-1016):
        // 1. Set lastCalculationMode
        // 2. Save C&F form state
        // 3. Call refreshPurchasesByIds() to get updated prices from API
        // 4. Update carBookingDisplayedCars with fresh data
        // 5. Return to Car Booking page
        // NOTE: Does NOT save total price (individual SAVE buttons handle that)
        
        // Set calculation mode for LIST table column header
        // Note: lastCalculationMode is a global variable in MinimalPurchaseApp.kt (same package)
        val mode = if (isFobMode) "FOB" else "C&F"
        // Access global variable - since it's in same package, we can reference it directly
        // But we need to use js() to set it on window for compatibility
        js("window.lastCalculationMode = mode")
        console.log("💾 Set lastCalculationMode: $mode")
        
        // Save C&F form state
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        
        // Save Car Booking state (selected rows from cnfPageSelectedCars) before navigating
        js("if (window.saveCarBookingState) window.saveCarBookingState()")
        
        // Get purchase IDs to refresh (use stored IDs from navigation)
        val purchaseIdsToRefresh = if (cnfPageSelectedPurchaseIds.isNotEmpty()) {
            cnfPageSelectedPurchaseIds
        } else {
            console.warn("⚠️ No purchase IDs stored in cnfPageSelectedPurchaseIds, trying fallback...")
            // Fallback: try to get from selected cars (use js() - car may be plain JS object)
            cnfPageSelectedCars.mapNotNull { car ->
                val purchaseId = js("(car.purchaseId != null && car.purchaseId !== undefined) ? car.purchaseId : car.id")
                if (purchaseId != null && purchaseId != js("undefined")) {
                    (purchaseId as? Number)?.toLong()
                } else {
                    null
                }
            }
        }
        
        if (purchaseIdsToRefresh.isEmpty()) {
            console.warn("⚠️ No purchase IDs found for refresh, navigating directly to Car Booking page")
            showCarBookingPage()
            return@addEventListener
        }
        
        console.log("🔄 FINISH button: Refreshing ${purchaseIdsToRefresh.size} purchases before navigation...")
        
        // Refresh purchases from API and then navigate (refreshPurchasesByIds handles navigation)
        refreshPurchasesByIds(purchaseIdsToRefresh)
    })
}

fun loadChassisDropdownForCnf(selectedCars: List<dynamic>? = null) {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    if (chassisSelect == null) return
    
    chassisSelect.innerHTML = "<option value=\"\">Select a car chassis...</option>"
    
    val carsToUse = selectedCars ?: cnfPageSelectedCars
    
    carsToUse.forEach { car ->
        val option = document.createElement("option") as HTMLOptionElement
        val chassis = car.chassis ?: "N/A"
        val name = car.name ?: car.carName ?: "N/A"
        val year = car.year ?: car.carModelYear ?: "N/A"
        option.value = chassis
        option.textContent = "$chassis - $name ($year)"
        chassisSelect.appendChild(option)
    }
    
    console.log("✅ Loaded ${carsToUse.size} cars into chassis dropdown")
}

fun calculateCnfTotal() {
    console.log("🔢 calculateCnfTotal() called")
    // Get current cost values from the form - use parseCurrency to handle formatted values (with commas)
    // Check if we're in FOB mode - if so, exclude freight from calculations
    val isFobMode = cnfPageIsFobMode
    console.log("🔍 FOB Mode: $isFobMode")
    val carPrice = parseCurrency((document.getElementById("carPrice") as HTMLInputElement?)?.value ?: "0")
    val auctionFee = parseCurrency((document.getElementById("auctionFee") as HTMLInputElement?)?.value ?: "0")
    val rixoPrice = parseCurrency((document.getElementById("rixoPrice") as HTMLInputElement?)?.value ?: "0")
    val shippingCharge = parseCurrency((document.getElementById("shippingCharge") as HTMLInputElement?)?.value ?: "0")
    // In FOB mode, freight is excluded (field doesn't exist or should be 0)
    val freight = if (isFobMode) 0.0 else parseCurrency((document.getElementById("freight") as HTMLInputElement?)?.value ?: "0")
    val inspectionFee = parseCurrency((document.getElementById("inspectionFee") as HTMLInputElement?)?.value ?: "0")
    val repairFee = parseCurrency((document.getElementById("repairFee") as HTMLInputElement?)?.value ?: "0")
    val mscCharges = parseCurrency((document.getElementById("mscCharges") as HTMLInputElement?)?.value ?: "0")
    val profit = parseCurrency((document.getElementById("profit") as HTMLInputElement?)?.value ?: "0")
    
    // TOTAL C&F/FOB PRICE = sum of all fields (excluding freight in FOB mode)
    val totalCnfPrice = if (isFobMode) {
        // FOB: sum of all fields EXCEPT freight
        carPrice + auctionFee + rixoPrice + shippingCharge + inspectionFee + repairFee + mscCharges + profit
    } else {
        // C&F: sum of all fields INCLUDING freight
        carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + mscCharges + profit
    }
    
    // TOTAL EXPENSE = sum of all fields EXCEPT Car Price
    val totalExpense = if (isFobMode) {
        // FOB: sum of all fields EXCEPT Car Price and Freight
        auctionFee + rixoPrice + shippingCharge + inspectionFee + repairFee + mscCharges + profit
    } else {
        // C&F: sum of all fields EXCEPT Car Price (includes Freight)
        auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + mscCharges + profit
    }
        
    // Update display
    document.getElementById("totalCnfPrice")?.textContent = "¥${totalCnfPrice.toInt()}"
    document.getElementById("totalExpense")?.textContent = "¥${totalExpense.toInt()}"
        
    // Update label based on mode
    val totalPriceLabelElement = document.getElementById("totalPriceLabel")
    if (totalPriceLabelElement != null) {
        totalPriceLabelElement.textContent = if (isFobMode) "TOTAL FOB PRICE (¥):" else "TOTAL C&F PRICE (¥):"
    }
    
    console.log("💰 Total ${if (isFobMode) "FOB" else "C&F"} Price: $totalCnfPrice")
    console.log("💰 Total Expense: $totalExpense")
}

fun loadCarCostDetails() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    val selectedChassis = chassisSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) {
        clearCostFields()
        return
    }
    
    console.log("📋 Loading cost details for chassis: $selectedChassis")
    
    // Find the purchase ID for this chassis
    val purchase = cnfPageSelectedCars.find { it.chassis == selectedChassis }
    if (purchase != null) {
        cnfPageCurrentPurchaseId = (purchase.id as? Number)?.toLong()
    }
    
    // Per documentation: Use GET /api/purchases/costs-by-chassis/{chassis}
    val encodedChassis = js("encodeURIComponent")(selectedChassis) as String
    val costDetailsUrl = apiUrl("purchases/costs-by-chassis/$encodedChassis")
    console.log("🔍 Fetching from costs-by-chassis endpoint:", costDetailsUrl)
    
    window.fetch(costDetailsUrl)
        .then { response: dynamic ->
            console.log("📥 Cost details API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                response.text().then { errorText: dynamic ->
                    console.log("⚠️ Cost details API error:", errorText)
                    console.log("⚠️ Falling back to search endpoint")
                }
                // Fallback to search endpoint if costs-by-chassis fails
                js("Promise.resolve(null)")
            }
        }
        .then { costDetailsData: dynamic ->
            if (costDetailsData != null && costDetailsData != js("undefined")) {
                console.log("✅ Found cost details via costs-by-chassis endpoint:", costDetailsData)
                // Backend returns cost details in format: { id, chassis, carPrice, auctionFee, shippingCharge, repairFee, mscCharges, ... }
                // Convert to costData format for populateCostFields
                val costData = js("{}")
                // Helper function to convert value to Double (handles both String and Number)
                fun toDoubleValue(value: dynamic): Double {
                    if (value == null || value == js("undefined")) return 0.0
                    val numValue = js("parseFloat(value)") as? Number
                    return if (numValue != null && !js("isNaN(numValue)")) {
                        numValue.toDouble()
                    } else {
                        0.0
                    }
                }
                
                // Map backend response to costData format
                costData.carPrice = toDoubleValue(costDetailsData.carPrice)
                costData.auctionFee = toDoubleValue(costDetailsData.auctionFee)
                costData.rixoPrice = toDoubleValue(costDetailsData.rixoPrice)
                costData.shippingCharge = toDoubleValue(costDetailsData.shippingCharge)
                costData.freight = 0.0  // Freight comes from globalFreightValues, not database
                costData.inspectionFee = toDoubleValue(costDetailsData.inspectionFee)
                costData.repairFee = toDoubleValue(costDetailsData.repairFee)
                costData.mscCharges = toDoubleValue(costDetailsData.mscCharges)
                costData.profit = toDoubleValue(costDetailsData.profit)
                
                console.log("📊 Cost data extracted from costs-by-chassis:", costData)
                
                // Update purchase ID
                val purchaseId = (costDetailsData.id as? Number)?.toLong()
                if (purchaseId != null) {
                    cnfPageCurrentPurchaseId = purchaseId
                    console.log("✅ Set purchase ID:", purchaseId)
                }
                
                populateCostFields(costData)
                console.log("✅ Populated cost fields from costs-by-chassis endpoint")
                
                // Populate freight from globalFreightValues (not from database)
                val freightField = document.getElementById("freight") as? HTMLInputElement
                if (freightField != null && !cnfPageIsFobMode) {
                    val freightFromGlobal = globalFreightValues[selectedChassis] ?: 0.0
                    freightField.value = freightFromGlobal.toInt().toString()
                    if (freightFromGlobal > 0.0) {
                        console.log("🚢 Populated freight from globalFreightValues: ¥${freightFromGlobal.toInt()}")
                    } else {
                        console.log("🚢 No freight value in globalFreightValues for chassis $selectedChassis, leaving as 0")
                    }
                }
                
                // Recalculate total after loading
                calculateCnfTotal()
            } else {
                // Fallback to search endpoint
                console.log("⚠️ costs-by-chassis returned null, trying search endpoint fallback")
                loadCarCostDetailsFallback(selectedChassis)
            }
        }
        .catch { error: dynamic ->
            console.error("❌ Error loading cost details from costs-by-chassis endpoint:", error)
            console.log("⚠️ Falling back to search endpoint")
            // Fallback to search endpoint
            loadCarCostDetailsFallback(selectedChassis)
        }
}

fun loadCarCostDetailsFallback(chassis: String) {
    console.log("📋 Loading cost details from database for chassis: $chassis")
    
    // Use search API directly (chassis endpoint doesn't work as GET)
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val searchUrl = apiUrl("purchases/search?query=$encodedChassis")
    Logger.debug("Fetching from search endpoint: $searchUrl")
    
    window.fetch(searchUrl)
        .then { searchResponse: dynamic ->
            Logger.debug("Search API response status: ${searchResponse.status}")
            if (searchResponse.ok) {
                searchResponse.json()
            } else {
                searchResponse.text().then { searchErrorText: dynamic ->
                    Logger.warn("Search API error: $searchErrorText")
                }
                js("Promise.resolve(null)")
            }
        }
        .then { purchaseData: dynamic ->
            Logger.debug("Processing purchase data - type: ${js("typeof purchaseData")}, isArray: ${js("Array.isArray")(purchaseData)}")
            
            // Handle null/undefined
            if (purchaseData == null || js("purchaseData === undefined")) {
                Logger.warn("Purchase data is null/undefined, trying fallback")
                loadCarCostDetailsFromSelectedCars(chassis)
                return@then
            }
            
            // Handle both single purchase and array of purchases
            val purchase = if (js("Array.isArray")(purchaseData).unsafeCast<Boolean>()) {
                val purchasesArray = purchaseData as Array<dynamic>
                Logger.debug("Found array with ${purchasesArray.size} items")
                purchasesArray.firstOrNull()
            } else {
                Logger.debug("Found single purchase object")
                purchaseData
            }
            
            if (purchase != null && purchase != js("undefined")) {
                Logger.debug("Found purchase in database (via search endpoint)")
                processPurchaseCostData(purchase, chassis)
            } else {
                Logger.warn("Purchase is null, trying fallback from selected cars")
                loadCarCostDetailsFromSelectedCars(chassis)
            }
        }
        .catch { error: dynamic ->
            val errorMsg = ErrorHandler.handleNetworkError(error, "purchases/search")
            Logger.error("Error loading purchase from database: $errorMsg")
            // Final fallback
            loadCarCostDetailsFromSelectedCars(chassis)
        }
}

fun processPurchaseCostData(purchase: dynamic, chassis: String) {
    console.log("📊 Processing purchase cost data for chassis: $chassis")
    console.log("📊 Purchase fields:", 
        "carPrice=${purchase.carPrice}", 
        "price=${purchase.price}",
        "auctionFee=${purchase.auctionFee}",
        "auction_fee=${purchase.auction_fee}",
        "freight=${purchase.freight}")
    
    val costData = js("{}")
    // Helper function to convert value to Double (handles both String and Number)
    // Uses JavaScript's parseFloat for compatibility
    fun toDoubleValue(value: dynamic): Double {
        if (value == null || value == js("undefined")) return 0.0
        val numValue = js("parseFloat(value)") as? Number
        return if (numValue != null && !js("isNaN(numValue)")) {
            numValue.toDouble()
        } else {
            0.0
        }
    }
    
    // Directly access properties - handle both String and Number types
    // Use 'price' column from database (not 'carPrice')
    costData.carPrice = toDoubleValue(purchase.price ?: purchase.carPrice)
    costData.auctionFee = toDoubleValue(purchase.auctionFee ?: purchase.auction_fee)
    costData.rixoPrice = toDoubleValue(purchase.rixoPrice ?: purchase.rixo_price)
    costData.shippingCharge = toDoubleValue(purchase.shipmentCharges ?: purchase.shipment_charges ?: purchase.shippingCharge)
    // Freight should NOT come from database - only from globalFreightValues
    costData.freight = 0.0
    costData.inspectionFee = toDoubleValue(purchase.inspectionFee ?: purchase.inspection_fee)
    costData.repairFee = toDoubleValue(purchase.repairCharges ?: purchase.repair_charges)
    costData.mscCharges = toDoubleValue(purchase.miscCharges ?: purchase.misc_charges)
    costData.profit = toDoubleValue(purchase.profit)
    
    console.log("📊 Field extraction - carPrice: ${costData.carPrice}, auctionFee: ${costData.auctionFee}, rixoPrice: ${costData.rixoPrice}")
    // Use direct property access for logging (purchase is already a dynamic type)
    console.log("📊 Raw values - purchase.price: ${purchase.price}, purchase.auctionFee: ${purchase.auctionFee}, purchase.auction_fee: ${purchase.auction_fee}")
    
    console.log("📊 Cost data extracted:", costData)
    
    // Also update purchase ID if available
    val purchaseId = (purchase.id as? Number)?.toLong()
    if (purchaseId != null) {
        cnfPageCurrentPurchaseId = purchaseId
        console.log("✅ Set purchase ID:", purchaseId)
    }
    
    populateCostFields(costData)
    console.log("✅ Populated cost fields from database")
    
    // Populate freight from globalFreightValues (not from database)
    val freightField = document.getElementById("freight") as? HTMLInputElement
    if (freightField != null && !cnfPageIsFobMode) {
        val freightFromGlobal = globalFreightValues[chassis] ?: 0.0
        freightField.value = freightFromGlobal.toInt().toString()
        if (freightFromGlobal > 0.0) {
            console.log("🚢 Populated freight from globalFreightValues: ¥${freightFromGlobal.toInt()}")
        } else {
            console.log("🚢 No freight value in globalFreightValues for chassis $chassis, leaving as 0")
        }
    }
    
    // Recalculate total after loading
    calculateCnfTotal()
}

fun loadCarCostDetailsFromSelectedCars(chassis: String) {
    console.log("📋 Trying to load from cnfPageSelectedCars for chassis: $chassis")
    val purchase = cnfPageSelectedCars.find { it.chassis == chassis }
    if (purchase != null) {
        console.log("✅ Found purchase in selected cars:", purchase)
        val costData = js("{}")
        // Helper function to convert value to Double (handles both String and Number)
        // Uses JavaScript's parseFloat for compatibility
        fun toDoubleValue(value: dynamic): Double {
            if (value == null || value == js("undefined")) return 0.0
            val numValue = js("parseFloat(value)") as? Number
            return if (numValue != null && !js("isNaN(numValue)")) {
                numValue.toDouble()
            } else {
                0.0
            }
        }
        
        // Directly access properties - handle both String and Number types
        // Use 'price' column from database (not 'carPrice')
        costData.carPrice = toDoubleValue(purchase.price ?: purchase.carPrice)
        costData.auctionFee = toDoubleValue(purchase.auctionFee ?: purchase.auction_fee)
        costData.rixoPrice = toDoubleValue(purchase.rixoPrice ?: purchase.rixo_price)
        costData.shippingCharge = toDoubleValue(purchase.shipmentCharges ?: purchase.shipment_charges ?: purchase.shippingCharge)
        // Freight should NOT come from database - only from globalFreightValues
        costData.freight = 0.0
        costData.inspectionFee = toDoubleValue(purchase.inspectionFee ?: purchase.inspection_fee)
        costData.repairFee = toDoubleValue(purchase.repairCharges ?: purchase.repair_charges)
        costData.mscCharges = toDoubleValue(purchase.miscCharges ?: purchase.misc_charges)
        costData.profit = toDoubleValue(purchase.profit)
        
        console.log("📊 Field extraction (fallback) - carPrice: ${costData.carPrice}, auctionFee: ${costData.auctionFee}")
        // Use direct property access for logging (purchase is already a dynamic type)
        console.log("📊 Raw values (fallback) - purchase.price: ${purchase.price}, purchase.auctionFee: ${purchase.auctionFee}, purchase.auction_fee: ${purchase.auction_fee}")
        
        // Update purchase ID
        val purchaseId = (purchase.id as? Number)?.toLong()
        if (purchaseId != null) {
            cnfPageCurrentPurchaseId = purchaseId
        }
        
        populateCostFields(costData)
        
        // Populate freight from globalFreightValues (not from database)
        val freightField = document.getElementById("freight") as? HTMLInputElement
        if (freightField != null && !cnfPageIsFobMode) {
            val freightFromGlobal = globalFreightValues[chassis] ?: 0.0
            freightField.value = freightFromGlobal.toInt().toString()
            if (freightFromGlobal > 0.0) {
                console.log("🚢 Populated freight from globalFreightValues: ¥${freightFromGlobal.toInt()}")
            } else {
                console.log("🚢 No freight value in globalFreightValues for chassis $chassis, leaving as 0")
            }
        }
        
        calculateCnfTotal()
        console.log("✅ Populated cost fields from selected cars")
    } else {
        console.log("⚠️ Purchase not found in selected cars either, clearing fields")
        clearCostFields()
    }
}

fun populateCostFields(costData: dynamic) {
    val carPrice = (costData.carPrice as? Number)?.toDouble() ?: 0.0
    val auctionFee = (costData.auctionFee as? Number)?.toDouble() ?: 0.0
    val rixoPrice = (costData.rixoPrice as? Number)?.toDouble() ?: 0.0
    val shippingCharge = (costData.shippingCharge as? Number)?.toDouble() ?: 0.0
    // Note: Freight is handled separately - NOT populated from database
    // Freight comes from globalFreightValues (set by Calculate Freight page)
    val inspectionFee = (costData.inspectionFee as? Number)?.toDouble() ?: 0.0
    val repairFee = (costData.repairFee as? Number)?.toDouble() ?: 0.0
    val mscCharges = (costData.mscCharges as? Number)?.toDouble() ?: 0.0
    val profit = (costData.profit as? Number)?.toDouble() ?: 0.0
    
    console.log("📊 Populating cost fields:", "carPrice=$carPrice", "auctionFee=$auctionFee", "etc.")
    
    (document.getElementById("carPrice") as? HTMLInputElement)?.value = carPrice.toInt().toString()
    (document.getElementById("auctionFee") as? HTMLInputElement)?.value = auctionFee.toInt().toString()
    (document.getElementById("rixoPrice") as? HTMLInputElement)?.value = rixoPrice.toInt().toString()
    (document.getElementById("shippingCharge") as? HTMLInputElement)?.value = shippingCharge.toInt().toString()
    // Freight field is NOT populated here - handled separately after this function
    (document.getElementById("inspectionFee") as? HTMLInputElement)?.value = inspectionFee.toInt().toString()
    (document.getElementById("repairFee") as? HTMLInputElement)?.value = repairFee.toInt().toString()
    (document.getElementById("mscCharges") as? HTMLInputElement)?.value = mscCharges.toInt().toString()
    (document.getElementById("profit") as? HTMLInputElement)?.value = profit.toInt().toString()
    
    console.log("✅ Cost fields populated (excluding freight), values:", 
        "carPrice=${(document.getElementById("carPrice") as? HTMLInputElement)?.value}",
        "auctionFee=${(document.getElementById("auctionFee") as? HTMLInputElement)?.value}")
}

fun clearCostFields() {
    (document.getElementById("carPrice") as? HTMLInputElement)?.value = "0"
    (document.getElementById("auctionFee") as? HTMLInputElement)?.value = "0"
    (document.getElementById("rixoPrice") as? HTMLInputElement)?.value = "0"
    (document.getElementById("shippingCharge") as? HTMLInputElement)?.value = "0"
    (document.getElementById("freight") as? HTMLInputElement)?.value = "0"
    (document.getElementById("inspectionFee") as? HTMLInputElement)?.value = "0"
    (document.getElementById("repairFee") as? HTMLInputElement)?.value = "0"
    (document.getElementById("mscCharges") as? HTMLInputElement)?.value = "0"
    (document.getElementById("profit") as? HTMLInputElement)?.value = "0"
    calculateCnfTotal()
}

fun saveCarCostDetails() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    val selectedChassis = chassisSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) {
        js("alert('Please select a car chassis first')")
        return
    }
    
    console.log("💾 Saving cost details for chassis:", selectedChassis)
    
    // Get all cost values from the form
    val carPrice = (document.getElementById("carPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val auctionFee = (document.getElementById("auctionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val rixoPrice = (document.getElementById("rixoPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val freight = (document.getElementById("freight") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val repairFee = (document.getElementById("repairFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val mscCharges = (document.getElementById("mscCharges") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val profit = (document.getElementById("profit") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    // Create cost data object
    val costData = js("{}")
    costData.chassis = selectedChassis
    costData.carPrice = carPrice
    costData.auctionFee = auctionFee
    costData.rixoPrice = rixoPrice
    costData.shippingCharge = shippingCharge
    costData.freight = freight
    costData.inspectionFee = inspectionFee
    costData.repairFee = repairFee
    costData.mscCharges = mscCharges
    costData.profit = profit
    
    console.log("📊 Cost data to save:", costData)
    
    // Call backend API to save cost details
    val requestInit = js("{}")
    requestInit.method = "PUT"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(costData)
    window.fetch(apiUrl("purchases/save-costs"), requestInit)
        .then { response: dynamic ->
            console.log("Save API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                throw js("Error('Failed to save cost details')")
            }
        }
        .then { result: dynamic ->
            console.log("✅ Cost details saved successfully:", result)
            // After saving cost details, also save the total C&F/FOB price
            saveTotalCnfPrice()
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving cost details:", error)
            showMessage("Error saving cost details: ${error.message}", "error")
        }
}

fun saveTotalCnfPriceForSelectedPurchases() {
    console.log("💾 Saving TOTAL C&F PRICE for selected purchases...")
    console.log("🔍 cnfPageSelectedPurchaseIds:", cnfPageSelectedPurchaseIds)
    
    // Use stored purchase IDs from when navigating to C&F page
    val finalPurchaseIds = if (cnfPageSelectedPurchaseIds.isNotEmpty()) {
        cnfPageSelectedPurchaseIds
    } else {
        // Fallback: try to get from table
        console.log("⚠️ No purchase IDs stored, trying to get from table...")
        getSelectedPurchaseIds()
    }
    
    if (finalPurchaseIds.isEmpty()) {
        console.error("❌ No purchase IDs found")
        js("alert('No selected purchases found. Please select cars from Car Booking page.')")
        return
    }
    
    console.log("📋 Purchase IDs to update: $finalPurchaseIds")
    
    // Get current TOTAL C&F PRICE from the display
    val totalCnfPriceElement = document.getElementById("totalCnfPrice")
    val totalCnfPriceText = totalCnfPriceElement?.textContent ?: "¥0"
    
    // Extract numeric value from "¥476900" format
    val totalCnfPrice = totalCnfPriceText.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
    
    console.log("📊 TOTAL C&F PRICE to save: $totalCnfPrice")
    
    // Create request data object - convert IDs to JS numbers to avoid Long serialization issues
    val purchaseIdsJs = finalPurchaseIds.map { it.toDouble() }.toTypedArray()
    val requestData = js("{}")
    requestData.purchaseIds = purchaseIdsJs
    requestData.totalCnfPrice = totalCnfPrice
    
    console.log("📊 Request data to save:", requestData)
    
    // Call backend API to save total C&F price for selected purchases
    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(requestData)
    window.fetch(apiUrl("purchases/save-total-cnf-by-ids"), requestInit)
        .then { response: dynamic ->
            console.log("Save Total C&F API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                throw js("Error('Failed to save total C&F price')")
            }
        }
        .then { result: dynamic ->
            console.log("✅ TOTAL C&F PRICE saved successfully for ${purchaseIdsJs.size} purchase(s):", result)
            showMessage("Total C&F price saved successfully", "success")
            // Refresh purchase data from API to get updated totalCnfPrice
            console.log("🔄 Refreshing purchase data from API...")
            refreshPurchasesByIds(finalPurchaseIds)
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving total C&F price:", error)
            showMessage("Error saving total C&F price: ${error.message}", "error")
        }
}

fun refreshPurchasesByIds(purchaseIds: List<Long>) {
    console.log("🔄 Refreshing ${purchaseIds.size} purchases from API...")
    
    // Create a JavaScript array to hold promises
    val promisesArray = js("[]") as Array<dynamic>
    
    // Fetch each purchase by ID and add promise to array
    for (id in purchaseIds) {
        val promise = window.fetch(apiUrl("purchases/purchase/$id"))
            .then { response: dynamic ->
                if (response.ok) {
                    response.json()
                } else {
                    console.warn("⚠️ Failed to fetch purchase $id")
                    null
                }
            }
        promisesArray.asDynamic().push(promise)
    }
    
    // Wait for all fetches to complete using Promise.all with proper JS array
    js("Promise.all")(promisesArray)
        .then { purchases: dynamic ->
            val purchasesArray = js("Array.isArray(purchases) ? purchases : [purchases]") as Array<dynamic>
            val validPurchases = purchasesArray.filterNotNull()
            
            Logger.log("✅ Refreshed ${validPurchases.size} purchases from API")
            
            // Update carBookingDisplayedCars with fresh data
            for (freshPurchase in validPurchases) {
                val purchaseId = freshPurchase.id
                val index = carBookingDisplayedCars.indexOfFirst { it.id == purchaseId }
                if (index >= 0) {
                    // Update existing entry with fresh data
                    carBookingDisplayedCars[index] = freshPurchase
                    console.log("✅ Updated purchase ${purchaseId} with fresh totalCnfPrice: ${freshPurchase.totalCnfPrice}")
                    
                    // Check if this purchase has a destination (POD) value and save it to state
                    // Use js() to avoid asDynamic on API response (plain JS objects may not have Kotlin extensions)
                    val destination = when {
                        js("typeof freshPurchase.destination !== 'undefined' && freshPurchase.destination !== null") as Boolean -> {
                            (js("freshPurchase.destination") as? String)?.trim() ?: ""
                        }
                        else -> ""
                    }
                    if (destination.isNotEmpty()) {
                        carBookingFormState.podPort = destination
                        console.log("💾 Saved POD to state from refreshed purchase ${purchaseId}: $destination")
                    }
                } else {
                    // Add if not found (shouldn't happen, but handle gracefully)
                    console.warn("⚠️ Purchase ${purchaseId} not found in displayed cars, adding it")
                    carBookingDisplayedCars = carBookingDisplayedCars + arrayOf(freshPurchase)
                    
                    // Also check for POD in new purchase
                    val destination = when {
                        js("typeof freshPurchase.destination !== 'undefined' && freshPurchase.destination !== null") as Boolean -> {
                            (js("freshPurchase.destination") as? String)?.trim() ?: ""
                        }
                        else -> ""
                    }
                    if (destination.isNotEmpty()) {
                        carBookingFormState.podPort = destination
                        console.log("💾 Saved POD to state from new purchase ${purchaseId}: $destination")
                    }
                }
            }
            
            // Navigate back to Car Booking page with refreshed data
            console.log("📋 Returning to Car Booking page with refreshed data")
            showCarBookingPage()
            console.log("✅ Navigation completed")
        }
        .catch { error: dynamic ->
            console.error("❌ Error refreshing purchases:", error)
            // Still navigate even if refresh fails
            console.log("📋 Returning to Car Booking page (refresh failed)")
            showCarBookingPage()
        }
}

fun saveTotalCnfPrice() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    val selectedChassis = chassisSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) {
        js("alert('Please select a car chassis first')")
        return
    }
    
    // Get the purchase ID for the currently selected chassis
    val purchaseId = cnfPageCurrentPurchaseId
    if (purchaseId == null) {
        console.error("❌ No purchase ID found for chassis: $selectedChassis")
        js("alert('Error: Could not find purchase ID. Please select a chassis again.')")
        return
    }
    
    console.log("💾 Saving Total C&F Price for chassis: $selectedChassis, purchase ID: $purchaseId")
    
    // Get current Total C&F Price from the display
    val totalCnfPriceElement = document.getElementById("totalCnfPrice")
    val totalCnfPriceText = totalCnfPriceElement?.textContent ?: "¥0"
    
    // Extract numeric value from "¥476900" format
    val totalCnfPrice = totalCnfPriceText.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
    
    console.log("📊 Total C&F Price to save: $totalCnfPrice for purchase ID: $purchaseId")
    
    // Per documentation: Use POST /api/purchases/save-total-cnf-by-ids with purchaseIds array
    val requestData = js("{}")
    // Convert purchase ID to JS number array (backend expects array of numbers)
    requestData.purchaseIds = arrayOf(purchaseId.toDouble())
    requestData.totalCnfPrice = totalCnfPrice
    
    console.log("📤 Sending save request:", "purchaseIds=[$purchaseId]", "totalCnfPrice=$totalCnfPrice")
    
    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(requestData)
    
    console.log("📤 Request body:", JSON.stringify(requestData))
    console.log("📤 Request URL:", apiUrl("purchases/save-total-cnf-by-ids"))
    
    window.fetch(apiUrl("purchases/save-total-cnf-by-ids"), requestInit)
        .then { response: dynamic ->
            console.log("📥 Response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                response.text().then { errorText: dynamic ->
                    console.error("❌ API Error Response (status ${response.status}):", errorText)
                    throw js("Error('Failed to save total C&F price: ' + errorText)")
                }
            }
        }
        .then { result: dynamic ->
            console.log("✅ Total ${if (cnfPageIsFobMode) "FOB" else "C&F"} Price saved successfully:", result)
            val message = if (cnfPageIsFobMode) "Total FOB price saved successfully" else "Total C&F price saved successfully"
            showMessage(message, "success")
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving total C&F price:", error)
            showMessage("Error saving total C&F price: ${error.message}", "error")
        }
}

fun calculateTotalCnfPrice() {
    // Alias for calculateCnfTotal() for backward compatibility
    calculateCnfTotal()
}

