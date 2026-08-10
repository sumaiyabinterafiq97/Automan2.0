package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import kotlin.math.abs

// C&F/FOB Calculation Functions
// Note: Global variables (cnfPageIsFobMode, cnfPageSelectedCars, etc.) are defined in MinimalPurchaseApp.kt

/** Blank when zero so money-empty-as-zero inputs stay empty; totals still treat blank as 0 via [parseCurrency]. */
fun cnfMoneyFieldDisplay(amount: Double): String {
    if (amount == 0.0) return ""
    val whole = amount.toInt().toDouble()
    return if (abs(amount - whole) < 1e-9) amount.toInt().toString() else amount.toString()
}

/** Saved session strings: show blank when numeric value is zero. */
fun cnfRestoreMoneyDisplay(saved: String?): String {
    val s = saved?.trim().orEmpty()
    if (s.isEmpty()) return ""
    return if (parseCurrency(s) == 0.0) "" else s
}

/** Per-chassis Total Expense lock (default locked). In-memory only — not persisted. */
private data class CnfExpenseLockState(
    var locked: Boolean = true,
    var target: Double? = null,
)

private val cnfExpenseLockByChassis = mutableMapOf<String, CnfExpenseLockState>()

private fun cnfExpenseLockState(chassis: String): CnfExpenseLockState =
    cnfExpenseLockByChassis.getOrPut(chassis) { CnfExpenseLockState() }

fun showCnfCalculationPage(
    selectedChassis: String? = null,
    selectedCars: List<dynamic>? = null,
    selectedCountry: String = "PAKISTAN",
    isFobMode: Boolean = false,
    isRecreateCalculation: Boolean = isCarBookingRecreateCalculationSession(),
) {
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
    
    val cnfPageHTML = createCnfCalculationHTML(isFobMode, isRecreateCalculation)
    
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

private fun cnfQuickSaveIconSvg(): String =
    """<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline></svg>"""

/** Stable DOM id for expanded money fields (chassis may contain non-id-safe chars). */
private fun cnfFieldDomId(chassis: String, field: String): String {
    val safe = chassis.replace(Regex("[^A-Za-z0-9_-]"), "_")
    return "cnf_${safe}_$field"
}

fun createCnfCalculationHTML(isFobMode: Boolean = false, isRecreateCalculation: Boolean = false): String {
    val pageTitle = if (isFobMode) "FOB Calculation" else "C&F Calculation"
    val saveButtonLabel = if (isRecreateCalculation) "Update" else "Save"
    val calculateFreightButton = if (isFobMode) """
        <div class="cnf-freight-action-wrap">
            <button id="calculateShippingChargeBtn" type="button" class="cnf-btn cnf-btn-freight">Calculate Shipping Charge</button>
        </div>
    """ else """
        <div class="cnf-freight-action-wrap">
            <button id="calculateFreightBtn" type="button" class="cnf-btn cnf-btn-freight">Calculate Freight &amp; Shipping Charge</button>
        </div>
    """
    
    return """
        <div class="cnf-calculation-container" id="cnfCalculationRoot">
            <div class="cnf-back-row">
                <button id="backToBookingBtn" class="cnf-back-btn" type="button">← Back to Car Booking</button>
            </div>
            
            <!-- C&F Calculation Container -->
            <div class="cnf-card">
                
                <!-- Header -->
                <div class="cnf-header">
                    <h1>$pageTitle</h1>
                </div>
                
                $calculateFreightButton
                
                <!-- Cars Table Section -->
                <div class="cnf-cars-table-wrapper">
                    <table id="cnfCarsTable" class="cnf-cars-table">
                        <thead>
                            <tr>
                                <th class="cnf-th-icon" scope="col" style="width: 30px; text-align: center;"><span class="visually-hidden">Expand</span><span aria-hidden="true">▼</span></th>
                                <th class="cnf-th-icon" scope="col" style="width: 36px; text-align: center;" title="Saved"><span class="visually-hidden">Saved</span><span aria-hidden="true">●</span></th>
                                <th scope="col" style="width: 160px;">Chassis</th>
                                <th scope="col" style="width: 200px;">Car Name</th>
                                <th scope="col" style="width: 140px; text-align: right;">Car Price (¥)</th>
                                <th id="totalPriceHeader" scope="col" style="width: 160px; text-align: right;">Total C&F Price (¥)</th>
                                <th scope="col" style="width: 80px; text-align: center;">Save</th>
                            </tr>
                        </thead>
                        <tbody id="cnfCarsTableBody">
                            <!-- Rows will be populated by JavaScript -->
                        </tbody>
                    </table>
                </div>

                <!-- Batch PDF actions (unchanged backend payload: full selected batch) -->
                <div class="cnf-action-buttons">
                    <button id="previewPdfBtn" type="button" class="cnf-btn cnf-btn-preview">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                            <circle cx="12" cy="12" r="3"></circle>
                        </svg>
                        Preview
                    </button>
                    <button id="saveCnfBtn" type="button" class="cnf-btn cnf-btn-save">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path>
                            <polyline points="17 21 17 13 7 13 7 21"></polyline>
                            <polyline points="7 3 7 8 15 8"></polyline>
                        </svg>
                        $saveButtonLabel
                    </button>
                    <button id="downloadPdfBtn" type="button" class="cnf-btn cnf-btn-download">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                            <polyline points="14,2 14,8 20,8"></polyline>
                            <line x1="16" y1="13" x2="8" y2="13"></line>
                            <line x1="16" y1="17" x2="8" y2="17"></line>
                        </svg>
                        PDF
                    </button>
                </div>
                
            </div>
        </div>
    """
}

/** Inner HTML for expanded detail row: single &lt;td colspan="7"&gt; only (parent &lt;tr&gt; is created in JS). */
fun createCnfCarExpandedRowInnerHTML(chassis: String, isFobMode: Boolean = false): String {
    fun field(field: String, label: String, extraClass: String = ""): String {
        val id = cnfFieldDomId(chassis, field)
        val cls = "cnf-expanded-input money-input money-empty-as-zero$extraClass".trim()
        return """
                            <div class="cnf-cost-field">
                                <label for="$id">$label</label>
                                <div class="input-with-prefix">
                                    <span aria-hidden="true">¥</span>
                                    <input type="text" id="$id" data-field="$field" data-chassis="$chassis" value="" placeholder="" inputmode="decimal" class="$cls" aria-label="$label">
                                </div>
                            </div>
        """.trimIndent()
    }

    val freightFieldHTML = if (isFobMode) "" else field("freight", "Freight (¥)")
    val totalLabel = if (isFobMode) "Total FOB Price (¥)" else "Total C&F Price (¥)"
    
    return """
                <td colspan="7">
                <div class="cnf-expanded-content">
                    <!-- Cost Fields Grid -->
                    <div class="cnf-expanded-grid">
                        <!-- Left Column -->
                        <div class="cnf-expanded-column">
                            ${field("carPrice", "Car Price (¥)")}
                            ${field("rixoPrice", "Rixo Price (¥)")}
                            $freightFieldHTML
                            ${field("repairFee", "Repair Fee (¥)")}
                            ${field("auctionPenaltyFee", "Auction Penalty Fee (¥)")}
                        </div>
                        
                        <!-- Right Column -->
                        <div class="cnf-expanded-column">
                            ${field("auctionFee", "Auction Fee (¥)")}
                            ${field("shippingCharge", "Shipping Charge (¥)")}
                            ${field("inspectionFee", "Inspection Fee (¥)")}
                            ${field("mscCharges", "Misc. Charges (¥)")}
                            ${field("profit", "Profit (¥)", " allow-negative")}
                        </div>
                    </div>
                    
                    <!-- Totals Summary -->
                    <div class="cnf-expanded-totals">
                        <div class="cnf-totals-grid">
                            <div class="cnf-total-box">
                                <div class="cnf-total-box-label">$totalLabel</div>
                                <div class="cnf-expanded-total-value green" data-chassis="$chassis" data-type="total" aria-live="polite">¥0</div>
                            </div>
                            <div class="cnf-total-box">
                                <div class="cnf-total-box-label-row">
                                    <div class="cnf-total-box-label">Total Expense (¥)</div>
                                    <button type="button"
                                        class="cnf-expense-lock-btn cnf-expense-lock-btn--locked"
                                        data-chassis="$chassis"
                                        data-locked="true"
                                        aria-pressed="true"
                                        title="Locked: cost changes adjust profit to keep total expense fixed"
                                        aria-label="Unlock total expense">
                                        <span class="cnf-expense-lock-icon" aria-hidden="true">🔒</span>
                                    </button>
                                </div>
                                <div class="cnf-expanded-total-value red" data-chassis="$chassis" data-type="expense" aria-live="polite">¥0</div>
                            </div>
                        </div>
                    </div>
                </div>
            </td>
    """
}

/** Keep FREIGHT (¥) visible in C&F mode; leave blank when unset (numeric zero for totals). */
fun updateCnfFreightFieldVisibility() {
    if (cnfPageIsFobMode) return
    val wrap = document.getElementById("cnfFreightFieldWrap") as? HTMLElement
    if (wrap == null) return
    wrap.style.display = ""
}

fun setupCnfCalculationListeners(selectedChassis: String? = null, selectedCars: List<dynamic>? = null, isFobMode: Boolean = false) {
    console.log("🔧 Setting up C&F table-based UI...")
    
    val carsToUse = selectedCars ?: cnfPageSelectedCars
    if (carsToUse.isEmpty()) {
        showMessage("No cars selected for calculation", "warning")
        return
    }
    
    // Update the table header based on FOB mode
    val headerEl = document.getElementById("totalPriceHeader")
    if (headerEl != null) {
        headerEl.textContent = if (isFobMode) "Total FOB Price (¥)" else "Total C&F Price (¥)"
    }
    
    // Populate table with cars
    renderCnfCarsTable(carsToUse, isFobMode)
    
    // Back to booking button
    document.getElementById("backToBookingBtn")?.addEventListener("click", { _: Event ->
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        js("if (window.saveCarBookingState) window.saveCarBookingState()")
        val mode = if (globalFreightValues.isNotEmpty()) "C&F" else "FOB"
        lastCalculationMode = mode
        js("window.lastCalculationMode = mode")
        console.log("💾 Back to booking: lastCalculationMode = $mode")
        val backRoute = if (isCarBookingRecreateCalculationSession() || isCarBookingRecreateSession()) {
            "/recalculate-booking"
        } else {
            "/booking"
        }
        val purchaseIdsToRefresh = if (cnfPageSelectedPurchaseIds.isNotEmpty()) {
            cnfPageSelectedPurchaseIds
        } else {
            carsToUse.mapNotNull { car ->
                val purchaseId = js("(car.purchaseId != null && car.purchaseId !== undefined) ? car.purchaseId : car.id")
                if (purchaseId != null && purchaseId != js("undefined")) {
                    (purchaseId as? Number)?.toLong()
                } else null
            }
        }
        navigateToApp(backRoute)
        if (purchaseIdsToRefresh.isNotEmpty()) {
            refreshPurchasesByIds(purchaseIdsToRefresh)
        } else {
            showCarBookingPage()
        }
    })
    
    // Calculate Freight / Calculate Shipping Charge button
    if (!isFobMode) {
        document.getElementById("calculateFreightBtn")?.addEventListener("click", { _: Event ->
            js("if (window.saveCnfFormState) window.saveCnfFormState()")
            showCalculateFreightPage()
        })
    } else {
        document.getElementById("calculateShippingChargeBtn")?.addEventListener("click", { _: Event ->
            js("if (window.saveCnfFormState) window.saveCnfFormState()")
            showCalculateShippingChargePage()
        })
    }

    document.getElementById("previewPdfBtn")?.addEventListener("click", { _: Event ->
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        console.log("📄 Preview PDF button clicked")
        generateShippingSchedulePdfPreview()
    })
    document.getElementById("saveCnfBtn")?.addEventListener("click", { _: Event ->
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        console.log("💾 Save C&F button clicked")
        saveShippingHistoryAndBooking()
    })
    document.getElementById("downloadPdfBtn")?.addEventListener("click", { _: Event ->
        js("if (window.saveCnfFormState) window.saveCnfFormState()")
        console.log("📥 Download PDF clicked")
        generateShippingSchedulePdf()
    })
    
    // Restrict money inputs
    window.setTimeout({
        val moneyInputs = document.querySelectorAll(".cnf-expanded-input.money-input")
        for (i in 0 until moneyInputs.length) {
            val input = moneyInputs.item(i) as? HTMLInputElement ?: continue
            input.addEventListener("paste", { ev -> ev.preventDefault() })
            input.addEventListener("drop", { ev -> ev.preventDefault() })
        }
        setupMoneyInputFormattingOnce()
    }, 150)
    
    console.log("✅ C&F table UI setup complete")
}

private fun mergeSavedCostsIntoCnfPageCar(
    chassis: String,
    carPrice: Double,
    auctionFee: Double,
    auctionPenaltyFee: Double,
    rixoPrice: Double,
    shippingCharge: Double,
    freight: Double,
    inspectionFee: Double,
    repairFee: Double,
    mscCharges: Double,
    profit: Double,
    isFobMode: Boolean,
) {
    val idx = cnfPageSelectedCars.indexOfFirst { it.chassis?.toString() == chassis }
    if (idx < 0) return
    val p = cnfPageSelectedCars[idx]
    p.price = carPrice
    p.carPrice = carPrice
    p.auctionFee = auctionFee
    p.auction_fee = auctionFee
    p.auctionPenaltyFee = auctionPenaltyFee
    p.auction_penalty_fee = auctionPenaltyFee
    p.rixoPrice = rixoPrice
    p.rixo_price = rixoPrice
    p.shipmentCharges = shippingCharge
    p.shipment_charges = shippingCharge
    p.shippingCharge = shippingCharge
    if (!isFobMode) {
        p.freight = freight
    }
    p.inspectionFee = inspectionFee
    p.inspection_fee = inspectionFee
    p.repairCharges = repairFee
    p.repair_charges = repairFee
    p.miscCharges = mscCharges
    p.misc_charges = mscCharges
    p.profit = profit
}

fun setCnfRowSavedIndicator(chassis: String, saved: Boolean) {
    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement ?: return
    val indicator = tbody.querySelector("tr.cnf-table-row[data-chassis=\"$chassis\"] .cnf-row-saved-indicator") as? HTMLElement
        ?: return
    if (saved) {
        indicator.classList.add("cnf-row-saved-indicator--on")
        indicator.setAttribute("aria-label", "Saved")
    } else {
        indicator.classList.remove("cnf-row-saved-indicator--on")
        indicator.setAttribute("aria-label", "Not saved")
    }
}

fun markAllCnfRowSavedIndicators() {
    val nodes = document.querySelectorAll(".cnf-row-saved-indicator")
    for (i in 0 until nodes.length) {
        val indicator = nodes.item(i) as? HTMLElement ?: continue
        indicator.classList.add("cnf-row-saved-indicator--on")
        indicator.setAttribute("aria-label", "Saved")
    }
}

private fun updateCnfMainRowCarPriceCell(chassis: String, carPrice: Double) {
    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement ?: return
    val row = tbody.querySelector("tr.cnf-table-row[data-chassis=\"$chassis\"]") ?: return
    val cell = row.querySelector(".cnf-car-price-cell") as? HTMLElement ?: return
    cell.textContent = "¥${formatYenDisplay(carPrice)}"
}

/** Parse numeric fields from costs-by-chassis JSON (Number or numeric string). */
private fun cnfNumericFromPayload(raw: dynamic): Double {
    if (raw == null || raw == js("undefined")) return 0.0
    val asNum = raw as? Number
    if (asNum != null) {
        val d = asNum.toDouble()
        if (!d.isNaN()) return d
    }
    return parseCurrency(raw.toString())
}

fun renderCnfCarsTable(cars: List<dynamic>, isFobMode: Boolean = false) {
    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement ?: return
    tbody.innerHTML = ""
    
    for (idx in cars.indices) {
        val car = cars[idx]
        val chassis = car.chassis?.toString() ?: "N/A"
        val carName = car.name?.toString() ?: car.carName?.toString() ?: "N/A"
        // Booking list rows often omit price; costs-by-chassis later fills cnfPageSelectedCars + main-row cell.
        val carPrice = cnfNumericFromPayload(car.price)
            .takeIf { it > 0.0 }
            ?: cnfNumericFromPayload(car.carPrice)
            .takeIf { it > 0.0 }
            ?: parseCurrency((car.price ?: car.carPrice)?.toString() ?: "0")
        
        // Main row
        val mainRow = document.createElement("tr") as HTMLTableRowElement
        mainRow.setAttribute("data-chassis", chassis)
        mainRow.className = "cnf-table-row"
        
        mainRow.innerHTML = """
            <td class="cnf-td-expand"><button type="button" class="cnf-expand-btn" data-chassis="$chassis" aria-expanded="false" aria-label="Expand cost details for $chassis">▼</button></td>
            <td class="cnf-td-saved"><span class="cnf-row-saved-indicator" data-chassis="$chassis" role="img" aria-label="Not saved" title="Saved to purchases"></span></td>
            <td class="cnf-td-chassis">$chassis</td>
            <td class="cnf-td-name">$carName</td>
            <td class="cnf-car-price-cell cnf-td-price">¥${formatYenDisplay(carPrice)}</td>
            <td class="cnf-td-total"><strong data-chassis="$chassis" class="cnf-total-display">¥0</strong></td>
            <td class="cnf-td-save"><button type="button" class="cnf-quick-save-btn" data-chassis="$chassis" aria-label="Save costs for $chassis" title="Save costs">${cnfQuickSaveIconSvg()}</button></td>
        """
        
        tbody.appendChild(mainRow)
        
        val expandedRow = document.createElement("tr") as HTMLTableRowElement
        expandedRow.setAttribute("data-chassis", chassis)
        expandedRow.className = "cnf-detail-row"
        expandedRow.innerHTML = createCnfCarExpandedRowInnerHTML(chassis, isFobMode)
        tbody.appendChild(expandedRow)
        
        // Setup expand button
        val expandBtnEl = mainRow.querySelector(".cnf-expand-btn") as? HTMLButtonElement
        expandBtnEl?.addEventListener("click", { _: Event ->
            toggleCnfRowExpanded(chassis, isFobMode)
        })
        
        // Setup quick save — do NOT call populateCnfExpandedFields here; it overwrites in-memory edits with stale purchase data.
        val quickSaveBtn = mainRow.querySelector(".cnf-quick-save-btn") as? HTMLButtonElement
        quickSaveBtn?.addEventListener("click", { _: Event ->
            saveCnfCarCosts(chassis, isFobMode)
        })
        
        // Load cost data for this car
        loadCnfCarCostData(car, chassis, isFobMode)
    }
    
    console.log("✅ Rendered ${cars.size} cars in table")
}

private fun setCnfExpandBtnState(btn: HTMLButtonElement?, expanded: Boolean, chassis: String? = null) {
    if (btn == null) return
    btn.textContent = if (expanded) "▲" else "▼"
    btn.setAttribute("aria-expanded", if (expanded) "true" else "false")
    val ch = chassis ?: btn.getAttribute("data-chassis").orEmpty()
    if (ch.isNotEmpty()) {
        btn.setAttribute(
            "aria-label",
            if (expanded) "Collapse cost details for $ch" else "Expand cost details for $ch",
        )
    }
}

fun toggleCnfRowExpanded(chassis: String, isFobMode: Boolean) {
    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement ?: return
    val expandedRow = tbody.querySelector("tr.cnf-detail-row[data-chassis=\"$chassis\"]") as? HTMLTableRowElement
        ?: return
    
    val rowEl = expandedRow as HTMLElement
    val isOpen = rowEl.classList.contains("cnf-detail-row--open")
    
    if (isOpen) {
        rowEl.classList.remove("cnf-detail-row--open")
        val btn = tbody.querySelector(".cnf-expand-btn[data-chassis=\"$chassis\"]") as? HTMLButtonElement
        setCnfExpandBtnState(btn, expanded = false, chassis = chassis)
    } else {
        val allDetailRows = tbody.querySelectorAll("tr.cnf-detail-row")
        for (i in 0 until allDetailRows.length) {
            val row = allDetailRows.item(i) as HTMLElement
            if (row.classList.contains("cnf-detail-row--open")) {
                row.classList.remove("cnf-detail-row--open")
                val otherChassis = row.getAttribute("data-chassis")
                val otherBtn = tbody.querySelector(".cnf-expand-btn[data-chassis=\"$otherChassis\"]") as? HTMLButtonElement
                setCnfExpandBtnState(otherBtn, expanded = false, chassis = otherChassis)
            }
        }
        
        rowEl.classList.add("cnf-detail-row--open")
        val btn = tbody.querySelector(".cnf-expand-btn[data-chassis=\"$chassis\"]") as? HTMLButtonElement
        setCnfExpandBtnState(btn, expanded = true, chassis = chassis)
        populateCnfExpandedFields(chassis, isFobMode)
        setupCnfExpandedFieldListeners(chassis, isFobMode)
    }
}

/** Hide the detail row and reset the expand arrow (no-op if already collapsed). */
fun collapseCnfExpandedRow(chassis: String) {
    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement ?: return
    val expandedRow = tbody.querySelector("tr.cnf-detail-row[data-chassis=\"$chassis\"]") as? HTMLElement ?: return
    expandedRow.classList.remove("cnf-detail-row--open")
    val btn = tbody.querySelector(".cnf-expand-btn[data-chassis=\"$chassis\"]") as? HTMLButtonElement
    setCnfExpandBtnState(btn, expanded = false, chassis = chassis)
}

fun populateCnfExpandedFields(chassis: String, isFobMode: Boolean) {
    val expandedRow = cnfDetailRowForChassis(chassis) ?: return
    
    // Find the purchase data in cnfPageSelectedCars
    val purchase = cnfPageSelectedCars.find { it.chassis == chassis } ?: return
    
    // Get cost data
    val carPrice = parseCurrency((purchase.price ?: purchase.carPrice)?.toString() ?: "0")
    val auctionFee = parseCurrency((purchase.auctionFee ?: purchase.auction_fee)?.toString() ?: "0")
    val auctionPenaltyFee = parseCurrency((purchase.auctionPenaltyFee ?: purchase.auction_penalty_fee)?.toString() ?: "0")
    val rixoPrice = parseCurrency((purchase.rixoPrice ?: purchase.rixo_price)?.toString() ?: "0")
    val shippingCharge =
        if (globalShippingChargeValues.containsKey(chassis)) {
            globalShippingChargeValues[chassis] ?: 0.0
        } else {
            parseCurrency((purchase.shipmentCharges ?: purchase.shipment_charges)?.toString() ?: "0")
        }
    val freight = if (isFobMode) 0.0 else (globalFreightValues[chassis] ?: parseCurrency((purchase.freight)?.toString() ?: "0"))
    val inspectionFee = parseCurrency((purchase.inspectionFee ?: purchase.inspection_fee)?.toString() ?: "0")
    val repairFee = parseCurrency((purchase.repairCharges ?: purchase.repair_charges)?.toString() ?: "0")
    val mscCharges = parseCurrency((purchase.miscCharges ?: purchase.misc_charges)?.toString() ?: "0")
    val profit = parseCurrency((purchase.profit)?.toString() ?: "0")
    
    // Populate fields
    val fieldMap = mapOf(
        "carPrice" to carPrice,
        "auctionFee" to auctionFee,
        "auctionPenaltyFee" to auctionPenaltyFee,
        "rixoPrice" to rixoPrice,
        "shippingCharge" to shippingCharge,
        "freight" to freight,
        "inspectionFee" to inspectionFee,
        "repairFee" to repairFee,
        "mscCharges" to mscCharges,
        "profit" to profit,
    )
    
    for ((fieldName, value) in fieldMap) {
        val input = expandedRow.querySelector("input[data-field=\"$fieldName\"]") as? HTMLInputElement
        input?.value = cnfMoneyFieldDisplay(value)
    }
    
    // Update totals; recapture lock target from freshly populated expense (default locked).
    updateCnfExpandedTotals(chassis, isFobMode, changedField = null, recaptureLockTarget = true)
    syncCnfExpenseLockButton(chassis)
}

fun setupCnfExpandedFieldListeners(chassis: String, isFobMode: Boolean) {
    val expandedRow = cnfDetailRowForChassis(chassis) ?: return
    if (expandedRow.getAttribute("data-cnf-listeners") == "1") return
    expandedRow.setAttribute("data-cnf-listeners", "1")
    val inputs = expandedRow.querySelectorAll(".cnf-expanded-input")

    for (i in 0 until inputs.length) {
        val input = inputs.item(i) as? HTMLInputElement ?: continue
        input.addEventListener("input", { _: Event ->
            val field = input.getAttribute("data-field")
            updateCnfExpandedTotals(chassis, isFobMode, changedField = field)
        })
    }

    val lockBtn = expandedRow.querySelector(".cnf-expense-lock-btn") as? HTMLButtonElement
    lockBtn?.addEventListener("click", { ev: Event ->
        ev.preventDefault()
        ev.stopPropagation()
        toggleCnfExpenseLock(chassis, isFobMode)
    })
    syncCnfExpenseLockButton(chassis)
}

private fun syncCnfExpenseLockButton(chassis: String) {
    val expandedRow = cnfDetailRowForChassis(chassis) ?: return
    val btn = expandedRow.querySelector(".cnf-expense-lock-btn") as? HTMLButtonElement ?: return
    val state = cnfExpenseLockState(chassis)
    val locked = state.locked
    btn.setAttribute("data-locked", if (locked) "true" else "false")
    btn.setAttribute("aria-pressed", if (locked) "true" else "false")
    btn.classList.toggle("cnf-expense-lock-btn--locked", locked)
    btn.classList.toggle("cnf-expense-lock-btn--unlocked", !locked)
    btn.title = if (locked) {
        "Locked: cost changes adjust profit to keep total expense fixed"
    } else {
        "Unlocked: total expense follows the sum of cost fields"
    }
    btn.setAttribute("aria-label", if (locked) "Unlock total expense" else "Lock total expense")
    val icon = btn.querySelector(".cnf-expense-lock-icon")
    if (icon != null) {
        icon.textContent = if (locked) "🔒" else "🔓"
    }
}

private fun toggleCnfExpenseLock(chassis: String, isFobMode: Boolean) {
    val state = cnfExpenseLockState(chassis)
    if (state.locked) {
        state.locked = false
        // Keep target for optional re-lock; will recapture on lock.
    } else {
        state.locked = true
        updateCnfExpandedTotals(chassis, isFobMode, changedField = null, recaptureLockTarget = true)
    }
    syncCnfExpenseLockButton(chassis)
}

/**
 * Refresh Total C&F/FOB + Total Expense for an expanded row.
 * When expense is locked, non-car-price / non-profit field edits adjust [profit] only.
 */
fun updateCnfExpandedTotals(
    chassis: String,
    isFobMode: Boolean,
    changedField: String? = null,
    recaptureLockTarget: Boolean = false,
) {
    val expandedRow = cnfDetailRowForChassis(chassis) ?: return

    val fieldIds = listOf(
        "carPrice", "auctionFee", "auctionPenaltyFee", "rixoPrice", "shippingCharge",
        "freight", "inspectionFee", "repairFee", "mscCharges", "profit",
    )
    val values = mutableMapOf<String, Double>()

    for (fieldId in fieldIds) {
        val input = expandedRow.querySelector("input[data-field=\"$fieldId\"]") as? HTMLInputElement
        values[fieldId] = input?.value?.let { parseCurrency(it) } ?: 0.0
    }

    val carPrice = values["carPrice"] ?: 0.0
    val auctionFee = values["auctionFee"] ?: 0.0
    val auctionPenaltyFee = values["auctionPenaltyFee"] ?: 0.0
    val rixoPrice = values["rixoPrice"] ?: 0.0
    val shippingCharge = values["shippingCharge"] ?: 0.0
    val freight = if (isFobMode) 0.0 else (values["freight"] ?: 0.0)
    val inspectionFee = values["inspectionFee"] ?: 0.0
    val repairFee = values["repairFee"] ?: 0.0
    val mscCharges = values["mscCharges"] ?: 0.0
    var profit = values["profit"] ?: 0.0

    fun nonProfitExpense(): Double =
        auctionFee + auctionPenaltyFee + rixoPrice + shippingCharge + freight +
            inspectionFee + repairFee + mscCharges

    fun expenseWith(p: Double): Double = nonProfitExpense() + p

    val lock = cnfExpenseLockState(chassis)

    if (lock.locked) {
        when {
            recaptureLockTarget || lock.target == null -> {
                lock.target = expenseWith(profit)
            }
            changedField == "profit" -> {
                lock.target = expenseWith(profit)
            }
            changedField == "carPrice" -> {
                // Car price does not affect total expense; keep profit + target.
            }
            else -> {
                // Cost field edit, or programmatic refresh (changedField == null): hold target via profit.
                val target = lock.target ?: expenseWith(profit)
                lock.target = target
                val adjusted = target - nonProfitExpense()
                if (abs(adjusted - profit) > 1e-9) {
                    profit = adjusted
                    val profitInput = expandedRow.querySelector("input[data-field=\"profit\"]") as? HTMLInputElement
                    profitInput?.value = cnfMoneyFieldDisplay(profit)
                }
            }
        }
    }

    val displayExpense = if (lock.locked) {
        lock.target ?: expenseWith(profit)
    } else {
        expenseWith(profit)
    }
    val totalPrice = carPrice + displayExpense

    expandedRow.querySelector(".cnf-expanded-total-value[data-type=\"total\"]")?.textContent =
        formatYenTotal(totalPrice)
    expandedRow.querySelector(".cnf-expanded-total-value[data-type=\"expense\"]")?.textContent =
        formatYenTotal(displayExpense)

    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement
    val mainRow = tbody?.querySelector("tr.cnf-table-row[data-chassis=\"$chassis\"]")
    mainRow?.querySelector(".cnf-total-display")?.textContent = formatYenTotal(totalPrice)
}

fun saveCnfCarCosts(chassis: String, isFobMode: Boolean) {
    val expandedRow = cnfDetailRowForChassis(chassis)
    
    if (expandedRow == null) {
        showMessage("Cannot find car details to save", "error")
        return
    }
    
    fun fieldDouble(name: String): Double {
        val input = expandedRow.querySelector("input[data-field=\"$name\"]") as? HTMLInputElement
        return input?.value?.let { parseCurrency(it) } ?: 0.0
    }
    
    val carPrice = fieldDouble("carPrice")
    val auctionFee = fieldDouble("auctionFee")
    val auctionPenaltyFee = fieldDouble("auctionPenaltyFee")
    val rixoPrice = fieldDouble("rixoPrice")
    val shippingCharge = fieldDouble("shippingCharge")
    val freight = if (isFobMode) 0.0 else fieldDouble("freight")
    val inspectionFee = fieldDouble("inspectionFee")
    val repairFee = fieldDouble("repairFee")
    val mscCharges = fieldDouble("mscCharges")
    val profit = fieldDouble("profit")
    
    val url = if (isFobMode) apiUrl("purchases/save-fob-costs") else apiUrl("purchases/save-costs")
    
    val bodyObj = js("{}")
    bodyObj.chassis = chassis
    bodyObj.carPrice = carPrice
    bodyObj.auctionFee = auctionFee
    bodyObj.auctionPenaltyFee = auctionPenaltyFee
    bodyObj.rixoPrice = rixoPrice
    bodyObj.shippingCharge = shippingCharge
    bodyObj.inspectionFee = inspectionFee
    bodyObj.repairFee = repairFee
    bodyObj.mscCharges = mscCharges
    bodyObj.profit = profit
    if (!isFobMode) {
        bodyObj.freight = freight
    }
    
    val requestInit = js("{}")
    requestInit.method = "PUT"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(bodyObj)
    
    console.log("💾 Saving costs for chassis $chassis (FOB=$isFobMode):", bodyObj)
    
    window.fetch(url, requestInit)
        .then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                throw js("Error('Failed to save')")
            }
        }
        .then { _: dynamic ->
            mergeSavedCostsIntoCnfPageCar(
                chassis,
                carPrice,
                auctionFee,
                auctionPenaltyFee,
                rixoPrice,
                shippingCharge,
                freight,
                inspectionFee,
                repairFee,
                mscCharges,
                profit,
                isFobMode,
            )
            saveCnfFormStateForChassis(chassis)
            updateCnfMainRowCarPriceCell(chassis, carPrice)
            updateCnfExpandedTotals(chassis, isFobMode)
            setCnfRowSavedIndicator(chassis, true)
            collapseCnfExpandedRow(chassis)
            showMessage("✅ Costs saved for chassis $chassis", "success")
            console.log("✅ Saved successfully")
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving:", error)
            showMessage("❌ Failed to save costs", "error")
        }
}

fun saveCnfCarCostsThenPreview(chassis: String) {
    saveCnfFormStateForChassis(chassis)
    console.log("💾 Preparing to preview PDF for chassis $chassis")
    // TODO: Implement PDF preview logic
    showMessage("PDF preview coming soon...", "info")
}

fun saveCnfCarCostsThenDownload(chassis: String) {
    saveCnfFormStateForChassis(chassis)
    console.log("💾 Preparing to download PDF for chassis $chassis")
    // TODO: Implement PDF download logic
    showMessage("PDF download coming soon...", "info")
}

fun saveCnfFormStateForChassis(chassis: String) {
    val expandedRow = cnfDetailRowForChassis(chassis) ?: return
    val windowCnfFormState = js("window.cnfFormState")
    if (windowCnfFormState == null || js("typeof window.cnfFormState") == "undefined") {
        js("window.cnfFormState = {}")
    }
    
    val chassisState = js("{}")
    val fieldIds = listOf("carPrice", "auctionFee", "auctionPenaltyFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    
    for (fieldId in fieldIds) {
        val input = expandedRow.querySelector("input[data-field=\"$fieldId\"]") as? HTMLInputElement
        chassisState[fieldId] = input?.value ?: ""
    }
    
    js("window.cnfFormState[chassis] = chassisState")
    console.log("✅ Saved form state for $chassis")
}

fun loadCnfCarCostData(car: dynamic, chassis: String, isFobMode: Boolean) {
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/costs-by-chassis/$encodedChassis")
    
    window.fetch(url)
        .then { response: dynamic ->
            if (response.ok) response.json() else null
        }
        .then { costData: dynamic ->
            if (costData != null) {
                // Update cnfPageSelectedCars with cost data
                val idx = cnfPageSelectedCars.indexOfFirst { it.chassis == chassis }
                if (idx >= 0) {
                    val updated = cnfPageSelectedCars[idx]
                    updated.price = costData.carPrice
                    updated.carPrice = costData.carPrice
                    updated.auctionFee = costData.auctionFee
                    updated.auctionPenaltyFee = costData.auctionPenaltyFee
                    updated.rixoPrice = costData.rixoPrice
                    updated.shipmentCharges =
                        if (globalShippingChargeValues.containsKey(chassis)) {
                            globalShippingChargeValues[chassis]
                        } else {
                            costData.shippingCharge
                        }
                    updated.inspectionFee = costData.inspectionFee
                    updated.repairCharges = costData.repairFee
                    updated.miscCharges = costData.mscCharges
                    updated.profit = costData.profit
                    val loadedCarPrice = cnfNumericFromPayload(costData.carPrice)
                    updateCnfMainRowCarPriceCell(chassis, loadedCarPrice)
                    val detailRow = cnfDetailRowForChassis(chassis) as? HTMLElement
                    val collapsed = detailRow == null || !detailRow.classList.contains("cnf-detail-row--open")
                    if (collapsed) {
                        populateCnfExpandedFields(chassis, isFobMode)
                    } else {
                        updateCnfExpandedTotals(chassis, isFobMode)
                    }
                }
            }
        }
        .catch { _: dynamic ->
            console.warn("⚠️ Could not load cost data for $chassis")
        }
}

fun formatYenDisplay(amount: Double): String {
    return amount.toLong().toString().reversed().chunked(3).joinToString(",").reversed()
}

fun formatYenTotal(amount: Double): String {
    val v = amount.toLong()
    val neg = v < 0L
    val abs = kotlin.math.abs(v)
    val grouped = abs.toString().reversed().chunked(3).joinToString(",").reversed()
    return "¥${if (neg) "-" else ""}$grouped"
}

fun cnfDetailRowForChassis(chassis: String): HTMLElement? {
    val tbody = document.getElementById("cnfCarsTableBody") as? HTMLTableSectionElement ?: return null
    return tbody.querySelector("tr.cnf-detail-row[data-chassis=\"$chassis\"]") as? HTMLElement
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
    if (document.getElementById("totalCnfPrice") == null) {
        return
    }
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
    val auctionPenaltyFee = parseCurrency((document.getElementById("auctionPenaltyFee") as HTMLInputElement?)?.value ?: "0")
    val mscCharges = parseCurrency((document.getElementById("mscCharges") as HTMLInputElement?)?.value ?: "0")
    val profit = parseCurrency((document.getElementById("profit") as HTMLInputElement?)?.value ?: "0")
    
    // TOTAL C&F/FOB PRICE = sum of all fields (excluding freight in FOB mode)
    val totalCnfPrice = if (isFobMode) {
        // FOB: sum of all fields EXCEPT freight
        carPrice + auctionFee + rixoPrice + shippingCharge + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
    } else {
        // C&F: sum of all fields INCLUDING freight
        carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
    }
    
    // TOTAL EXPENSE = sum of all fields EXCEPT Car Price
    val totalExpense = if (isFobMode) {
        // FOB: sum of all fields EXCEPT Car Price and Freight
        auctionFee + rixoPrice + shippingCharge + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
    } else {
        // C&F: sum of all fields EXCEPT Car Price (includes Freight)
        auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
    }
        
    // Update display (support negative totals with grouped digits)
    fun formatYenTotal(amount: Double): String {
        val v = amount.toLong()
        val neg = v < 0L
        val abs = kotlin.math.abs(v)
        val grouped = abs.toString().reversed().chunked(3).joinToString(",").reversed()
        return "¥${if (neg) "-" else ""}$grouped"
    }

    document.getElementById("totalCnfPrice")?.textContent = formatYenTotal(totalCnfPrice)
    document.getElementById("totalExpense")?.textContent = formatYenTotal(totalExpense)
        
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
                costData.auctionPenaltyFee = toDoubleValue(costDetailsData.auctionPenaltyFee)
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
                    freightField.value = cnfMoneyFieldDisplay(freightFromGlobal)
                    if (freightFromGlobal > 0.0) {
                        console.log("🚢 Populated freight from globalFreightValues: ¥${freightFromGlobal.toInt()}")
                    } else {
                        console.log("🚢 No freight value in globalFreightValues for chassis $selectedChassis, leaving freight blank")
                    }
                }
                applyGlobalShippingChargeToExpandedForm(selectedChassis)
                
                // Recalculate total after loading
                calculateCnfTotal()
                updateCnfFreightFieldVisibility()
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
    costData.auctionPenaltyFee = toDoubleValue(purchase.auctionPenaltyFee ?: purchase.auction_penalty_fee)
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
        freightField.value = cnfMoneyFieldDisplay(freightFromGlobal)
        if (freightFromGlobal > 0.0) {
            console.log("🚢 Populated freight from globalFreightValues: ¥${freightFromGlobal.toInt()}")
        } else {
            console.log("🚢 No freight value in globalFreightValues for chassis $chassis, leaving freight blank")
        }
    }
    applyGlobalShippingChargeToExpandedForm(chassis)
    
    // Recalculate total after loading
    calculateCnfTotal()
    updateCnfFreightFieldVisibility()
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
        costData.auctionPenaltyFee = toDoubleValue(purchase.auctionPenaltyFee ?: purchase.auction_penalty_fee)
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
            freightField.value = cnfMoneyFieldDisplay(freightFromGlobal)
            if (freightFromGlobal > 0.0) {
                console.log("🚢 Populated freight from globalFreightValues: ¥${freightFromGlobal.toInt()}")
            } else {
                console.log("🚢 No freight value in globalFreightValues for chassis $chassis, leaving freight blank")
            }
        }
        applyGlobalShippingChargeToExpandedForm(chassis)
        
        calculateCnfTotal()
        updateCnfFreightFieldVisibility()
        console.log("✅ Populated cost fields from selected cars")
    } else {
        console.log("⚠️ Purchase not found in selected cars either, clearing fields")
        clearCostFields()
    }
}

fun populateCostFields(costData: dynamic) {
    val carPrice = (costData.carPrice as? Number)?.toDouble() ?: 0.0
    val auctionFee = (costData.auctionFee as? Number)?.toDouble() ?: 0.0
    val auctionPenaltyFee = (costData.auctionPenaltyFee as? Number)?.toDouble() ?: 0.0
    val rixoPrice = (costData.rixoPrice as? Number)?.toDouble() ?: 0.0
    val shippingCharge = (costData.shippingCharge as? Number)?.toDouble() ?: 0.0
    // Note: Freight is handled separately - NOT populated from database
    // Freight comes from globalFreightValues (set by Calculate Freight page)
    val inspectionFee = (costData.inspectionFee as? Number)?.toDouble() ?: 0.0
    val repairFee = (costData.repairFee as? Number)?.toDouble() ?: 0.0
    val mscCharges = (costData.mscCharges as? Number)?.toDouble() ?: 0.0
    val profit = (costData.profit as? Number)?.toDouble() ?: 0.0
    
    console.log("📊 Populating cost fields:", "carPrice=$carPrice", "auctionFee=$auctionFee", "etc.")
    
    (document.getElementById("carPrice") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(carPrice)
    (document.getElementById("auctionFee") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(auctionFee)
    (document.getElementById("auctionPenaltyFee") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(auctionPenaltyFee)
    (document.getElementById("rixoPrice") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(rixoPrice)
    (document.getElementById("shippingCharge") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(shippingCharge)
    // Freight field is NOT populated here - handled separately after this function
    (document.getElementById("inspectionFee") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(inspectionFee)
    (document.getElementById("repairFee") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(repairFee)
    (document.getElementById("mscCharges") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(mscCharges)
    (document.getElementById("profit") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(profit)

    console.log("✅ Cost fields populated (excluding freight), values:", 
        "carPrice=${(document.getElementById("carPrice") as? HTMLInputElement)?.value}",
        "auctionFee=${(document.getElementById("auctionFee") as? HTMLInputElement)?.value}")
}

fun clearCostFields() {
    (document.getElementById("carPrice") as? HTMLInputElement)?.value = ""
    (document.getElementById("auctionFee") as? HTMLInputElement)?.value = ""
    (document.getElementById("rixoPrice") as? HTMLInputElement)?.value = ""
    (document.getElementById("shippingCharge") as? HTMLInputElement)?.value = ""
    (document.getElementById("freight") as? HTMLInputElement)?.value = ""
    (document.getElementById("inspectionFee") as? HTMLInputElement)?.value = ""
    (document.getElementById("repairFee") as? HTMLInputElement)?.value = ""
    (document.getElementById("auctionPenaltyFee") as? HTMLInputElement)?.value = ""
    (document.getElementById("mscCharges") as? HTMLInputElement)?.value = ""
    (document.getElementById("profit") as? HTMLInputElement)?.value = ""
    calculateCnfTotal()
}

fun saveCarCostDetails() {
    if (document.getElementById("cnfCarsTableBody") != null) {
        showMessage("Use the ✓ or Save button on each row to save costs.", "info")
        return
    }
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    val selectedChassis = chassisSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) {
        showMessage("Please select a car chassis first", "warning")
        return
    }
    
    console.log("💾 Saving cost details for chassis:", selectedChassis)
    
    // Get all cost values from the form (comma-formatted money inputs need parseCurrency)
    val carPrice = parseCurrency((document.getElementById("carPrice") as HTMLInputElement).value)
    val auctionFee = parseCurrency((document.getElementById("auctionFee") as HTMLInputElement).value)
    val rixoPrice = parseCurrency((document.getElementById("rixoPrice") as HTMLInputElement).value)
    val shippingCharge = parseCurrency((document.getElementById("shippingCharge") as HTMLInputElement).value)
    val freight = (document.getElementById("freight") as HTMLInputElement?)?.value?.let { parseCurrency(it) } ?: 0.0
    val inspectionFee = parseCurrency((document.getElementById("inspectionFee") as HTMLInputElement).value)
    val repairFee = parseCurrency((document.getElementById("repairFee") as HTMLInputElement).value)
    val auctionPenaltyFee = parseCurrency((document.getElementById("auctionPenaltyFee") as HTMLInputElement).value)
    val mscCharges = parseCurrency((document.getElementById("mscCharges") as HTMLInputElement).value)
    val profit = parseCurrency((document.getElementById("profit") as HTMLInputElement).value)
    
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
    costData.auctionPenaltyFee = auctionPenaltyFee
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
    console.log("💾 Refreshing purchases after C&F screen (totals are not persisted on purchase rows)...")
    console.log("🔍 cnfPageSelectedPurchaseIds:", cnfPageSelectedPurchaseIds)
    
    val finalPurchaseIds = if (cnfPageSelectedPurchaseIds.isNotEmpty()) {
        cnfPageSelectedPurchaseIds
    } else {
        console.log("⚠️ No purchase IDs stored, trying to get from table...")
        getSelectedPurchaseIds()
    }
    
    if (finalPurchaseIds.isEmpty()) {
        console.error("❌ No purchase IDs found")
        showMessage("No selected purchases found. Please select cars from Car Booking page.", "warning")
        return
    }
    
    showMessage("Refreshing purchase data (C&F/FOB totals are shown on screen only).", "info")
    refreshPurchasesByIds(finalPurchaseIds)
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
                    console.log("✅ Updated purchase ${purchaseId} from API refresh")
                    
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
        showMessage("Please select a car chassis first", "warning")
        return
    }
    
    val purchaseId = cnfPageCurrentPurchaseId
    if (purchaseId == null) {
        console.error("❌ No purchase ID found for chassis: $selectedChassis")
        showMessage("Could not find purchase ID. Please select a chassis again.", "error")
        return
    }
    
    val saveAsCnf = lastCalculationMode == "C&F"
    console.log("ℹ️ C&F/FOB total for chassis $selectedChassis is display-only (not stored on purchase); mode=${if (saveAsCnf) "C&F" else "FOB"}")
    showMessage("C&F/FOB totals are shown on screen only; they are not saved to the database.", "info")
}

fun calculateTotalCnfPrice() {
    // Alias for calculateCnfTotal() for backward compatibility
    calculateCnfTotal()
}

/** When set by Calculate Freight + shipping charge map, overrides DB for the expanded form. */
private fun applyGlobalShippingChargeToExpandedForm(chassis: String) {
    if (chassis.isBlank()) return
    if (!globalShippingChargeValues.containsKey(chassis)) return
    val v = globalShippingChargeValues[chassis] ?: 0.0
    (document.getElementById("shippingCharge") as? HTMLInputElement)?.value = cnfMoneyFieldDisplay(v)
}

private fun getCnfFormStateForChassis(chassis: String): dynamic {
    val fs = window.asDynamic().cnfFormState ?: return null
    return js("(function(o, k) { return (o && o[k] != null && typeof o[k] !== 'undefined') ? o[k] : null; })")(fs, chassis)
}

private fun mergedCostField(st: dynamic?, baseline: Double, key: String): Double {
    if (st == null) return baseline
    val raw = js("(function(s, k) { return (s && s[k] != null && s[k] !== undefined) ? s[k] : null; })")(st, key)
    if (raw == null || raw == js("undefined")) return baseline
    val s = raw.toString().trim()
    if (s.length == 0) return baseline
    return parseCurrency(s)
}

private fun purchaseFieldDouble(car: dynamic, keys: Array<out String>): Double {
    for (k in keys) {
        val raw = js("(function(o, key) { try { return (o != null && Object.prototype.hasOwnProperty.call(o, key)) ? o[key] : null; } catch (e) { return null; } })")(car, k)
        if (raw != null && raw != js("undefined")) {
            val t = raw.toString().trim()
            if (t.length > 0) return parseCurrency(t)
        }
    }
    return 0.0
}

/** Per-chassis total matching [calculateCnfTotal] (uses form state + purchase fallback + global freight for C&F). */
fun computeTotalCnfOrFobForChassis(car: dynamic, chassis: String, isFobMode: Boolean): Double {
    val st = getCnfFormStateForChassis(chassis)
    val carPrice = mergedCostField(st, purchaseFieldDouble(car, arrayOf("price", "carPrice")), "carPrice")
    val auctionFee = mergedCostField(st, purchaseFieldDouble(car, arrayOf("auctionFee", "auction_fee")), "auctionFee")
    val auctionPenaltyFee = mergedCostField(
        st,
        purchaseFieldDouble(car, arrayOf("auctionPenaltyFee", "auction_penalty_fee")),
        "auctionPenaltyFee",
    )
    val rixoPrice = mergedCostField(st, purchaseFieldDouble(car, arrayOf("rixoPrice", "rixo_price")), "rixoPrice")
    val shippingBaseline =
        if (globalShippingChargeValues.containsKey(chassis)) {
            globalShippingChargeValues[chassis] ?: 0.0
        } else {
            purchaseFieldDouble(car, arrayOf("shipmentCharges", "shipment_charges", "shippingCharge"))
        }
    val shippingCharge = mergedCostField(st, shippingBaseline, "shippingCharge")
    val freightFromGlobal = if (isFobMode) 0.0 else (globalFreightValues[chassis] ?: 0.0)
    val freight = if (isFobMode) 0.0 else mergedCostField(st, freightFromGlobal, "freight")
    val inspectionFee = mergedCostField(st, purchaseFieldDouble(car, arrayOf("inspectionFee", "inspection_fee")), "inspectionFee")
    val repairFee = mergedCostField(st, purchaseFieldDouble(car, arrayOf("repairCharges", "repair_charges", "repairFee")), "repairFee")
    val mscCharges = mergedCostField(st, purchaseFieldDouble(car, arrayOf("miscCharges", "misc_charges")), "mscCharges")
    val profit = mergedCostField(st, purchaseFieldDouble(car, arrayOf("profit")), "profit")

    return if (isFobMode) {
        carPrice + auctionFee + rixoPrice + shippingCharge + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
    } else {
        carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
    }
}

fun extractClientNameFromCar(car: dynamic): String {
    val a = car.clientName
    val b = car.client_name
    val fromA = a?.toString()?.trim().orEmpty()
    if (fromA.length > 0) return fromA
    val fromB = b?.toString()?.trim().orEmpty()
    return fromB
}

/**
 * Loads numeric cost fields from DB so totals are correct even when the user never opened this chassis in the dropdown.
 */
fun enrichCarWithCostsFromApi(car: dynamic, chassis: String): dynamic {
    val enc = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/costs-by-chassis/$enc")
    return window.fetch(url)
        .then { r: dynamic ->
            if (js("r.ok") as Boolean) {
                r.json()
            } else {
                js("Promise.resolve(null)")
            }
        }
        .then { cost: dynamic ->
            val out: dynamic = js("{}")
            js("Object.assign")(out, car)
            out.chassis = chassis
            if (cost != null && js("typeof cost !== 'undefined'") as Boolean) {
                val pid = cost.id
                if (pid != null && pid != js("undefined")) {
                    out.id = pid
                }
                out.price = cost.carPrice
                out.carPrice = cost.carPrice
                out.auctionFee = cost.auctionFee
                out.auctionPenaltyFee = cost.auctionPenaltyFee
                out.rixoPrice = cost.rixoPrice
                out.shipmentCharges = cost.shippingCharge
                out.shippingCharge = cost.shippingCharge
                out.inspectionFee = cost.inspectionFee
                out.repairCharges = cost.repairFee
                out.repairFee = cost.repairFee
                out.miscCharges = cost.mscCharges
                out.profit = cost.profit
            }
            out
        }
}

/**
 * Purchase rows to flag as booking-requested after a successful shipping-history save from C&F/FOB.
 * Prefer IDs from Car Booking "Calculate", then enriched API rows, then selected car objects.
 */
private fun resolvePurchaseIdsForBookingBatch(enrichedArr: dynamic, enrichedCount: Int): List<Long> {
    val out = linkedSetOf<Long>()
    for (pid in cnfPageSelectedPurchaseIds) {
        if (pid > 0L) out.add(pid)
    }
    if (out.isNotEmpty()) return out.toList()
    for (i in 0 until enrichedCount) {
        val enriched = js("(function(a, idx) { return a[idx]; })")(enrichedArr, i)
        val id = (enriched.id as? Number)?.toLong() ?: continue
        if (id > 0L) out.add(id)
    }
    if (out.isNotEmpty()) return out.toList()
    for (car in cnfPageSelectedCars) {
        val id = (car.id as? Number)?.toLong() ?: continue
        if (id > 0L) out.add(id)
    }
    return out.toList()
}

private fun postBookingRequestedThen(
    purchaseIds: List<Long>,
    onDone: () -> Unit,
    onFailed: (String) -> Unit,
) {
    if (purchaseIds.isEmpty()) {
        onDone()
        return
    }
    val bodyJson = "{\"purchaseIds\":[" + purchaseIds.joinToString(",") + "]}"
    val req = js("{}")
    req.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    req.headers = headers
    req.body = bodyJson
    window.fetch(apiUrl("purchases/booking-requested"), req)
        .then { response: dynamic ->
            if (js("response.ok") as Boolean) {
                onDone()
            } else {
                response.text().then { err: dynamic ->
                    onFailed(err?.toString() ?: "HTTP ${js("response.status")}")
                }
            }
            Unit
        }
        .catch { e: dynamic ->
            onFailed(e?.toString() ?: "Network error")
        }
}

/**
 * Persists [shipping_history] (server fills [client_name] from [purchases]), and makes Booking-Requested.
 */
fun saveShippingHistoryAndBooking() {
    console.log("💾 Saving shipping history and booking requested...")
    saveCnfFormState()

    if (cnfPageSelectedCars.isEmpty()) {
        showMessage("No cars in this calculation batch.", "error")
        return
    }

    val bd: dynamic = globalBookingDetails
    val bookingNo = bd.bookingNo?.toString()?.trim().orEmpty()
    if (bookingNo.length == 0 || bookingNo == "EBKG14265885") {
        showMessage("Booking details missing or invalid. Go back to Car Booking and click Calculate again.", "error")
        return
    }

    val isFob = cnfPageIsFobMode
    val priceType = if (isFob) "FOB" else "C&F"

    val promiseArr = js("[]")
    for (idx in cnfPageSelectedCars.indices) {
        val car: dynamic = cnfPageSelectedCars[idx]
        val chassis = car.chassis?.toString()?.trim().orEmpty()
        if (chassis.length == 0 || chassis == "N/A") continue
        promiseArr.push(enrichCarWithCostsFromApi(car, chassis))
    }
    val pc = (promiseArr.length as Number).toInt()
    if (pc == 0) {
        showMessage("No valid chassis to save.", "error")
        return
    }

    js("Promise.all")(promiseArr)
        .then { enrichedArr: dynamic ->
            val items: dynamic = js("[]")
            for (i in 0 until pc) {
                val enriched = js("(function(a, i) { return a[i]; })")(enrichedArr, i)
                val chassis = enriched.chassis?.toString()?.trim().orEmpty()
                if (chassis.length == 0) continue
                val row: dynamic = js("{}")
                row.chassis = chassis
                row.clientName = extractClientNameFromCar(enriched)
                val stock = (enriched.stockLocation ?: enriched.stock_location)?.toString()?.trim().orEmpty()
                if (stock.isNotEmpty() && stock != "-") row.stockLocation = stock
                row.amount = computeTotalCnfOrFobForChassis(enriched, chassis, isFob)
                items.push(row)
            }
            val n = (items.length as Number).toInt()
            if (n == 0) {
                showMessage("No valid chassis to save.", "error")
            } else {
                val req: dynamic = js("{}")
                req.country = bd.consigneeCountry
                req.consignee = bd.consigneeName
                req.notifyParty = bd.notifyParty
                req.inTransitClause = bd.inTransitClause
                req.shipmentDate = bd.shippingDate
                req.cyCutDate = bd.cyCutDate
                req.eta = bd.eta
                req.pol = bd.pol
                req.pod = bd.pod
                req.finalDestination = bd.finalDestination
                req.bookingId = bd.bookingNo
                req.vessel = bd.vesselName
                req.carrier = bd.carrier
                req.priceType = priceType
                req.items = items

                val requestInit = js("{}")
                requestInit.method = "POST"
                val headers = js("{}")
                headers["Content-Type"] = "application/json"
                requestInit.headers = headers
                requestInit.body = JSON.stringify(req)

                window.fetch(apiUrl("shipping-history/batch"), requestInit)
                    .then { response: dynamic ->
                        if (!(js("response.ok") as Boolean)) {
                            showMessage("Failed to save shipping history (HTTP ${js("response.status")}).", "error")
                            throw js("Error('shipping-history save failed')")
                        }
                        response.json()
                    }
                    .then { _: dynamic ->
                        val idsToMark = resolvePurchaseIdsForBookingBatch(enrichedArr, pc)
                        fun finalizeSave() {
                            markAllCnfRowSavedIndicators()
                        }
                        if (idsToMark.isEmpty()) {
                            console.warn("⚠️ Shipping history saved but no purchase IDs found to set booking_requested")
                            showMessage(
                                "Shipping history saved. Could not determine purchase IDs for booking requested.",
                                "warning",
                            )
                            finalizeSave()
                        } else {
                            postBookingRequestedThen(
                                purchaseIds = idsToMark,
                                onDone = {
                                    showSuccessModal(
                                        "Saved",
                                        "Shipping history saved; booking requested updated for selected cars.",
                                    )
                                    finalizeSave()
                                },
                                onFailed = { msg ->
                                    console.error("❌ booking-requested after shipping save:", msg)
                                    showMessage(
                                        "Shipping history saved, but booking requested could not be updated: $msg",
                                        "warning",
                                    )
                                    finalizeSave()
                                },
                            )
                        }
                    }
                    .catch { err: dynamic ->
                        console.error("❌ shipping-history batch:", err)
                        showMessage("Failed to save shipping history.", "error")
                    }
                Unit
            }
        }
        .catch { err: dynamic ->
            console.error("❌ saveShippingHistoryThenPdf (cost fetch):", err)
            showMessage("Failed to load cost data for all chassis. Try again.", "error")
        }
}

