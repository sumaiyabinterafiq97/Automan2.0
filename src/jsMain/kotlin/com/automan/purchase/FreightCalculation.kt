package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import kotlin.js.unsafeCast

// Freight Calculation Functions
// Note: Global variables (cnfPageSelectedCars, cnfPageSelectedCountry, globalFreightValues, etc.) are defined in MinimalPurchaseApp.kt

/**
 * Tiers for the current stock location: sorted by [Pair.first] = cars per container, [Pair.second] = ¥ per car.
 * Filled when opening Calculate Freight from [fetchFreightScmTiersForStockLocation].
 */
private var freightScmTiersSorted: List<Pair<Int, Double>> = emptyList()

private fun freightScmJsonStr(obj: Any?, key: String): String {
    val v = js("(function(o,k){ if(o==null||o[k]==null||o[k]===undefined) return ''; return String(o[k]).trim(); })")(obj, key)
    return v.unsafeCast<String>()
}

private fun freightScmJsonInt(obj: Any?, key: String): Int? {
    val v = js("(function(o,k){ if(o==null||o[k]==null||o[k]===undefined) return null; var n=Number(o[k]); return isFinite(n)?Math.floor(n):null; })")(obj, key)
    return when (v) {
        null, js("undefined") -> null
        is Number -> v.toInt()
        else -> v.toString().toDoubleOrNull()?.toInt()
    }
}

private fun freightScmJsonDouble(obj: Any?, key: String): Double? {
    val raw = js("(function(o,k){ if(o==null||o[k]==null||o[k]===undefined) return null; return o[k]; })")(obj, key)
    if (raw == null || raw == js("undefined")) return null
    val n = js("Number(raw)") as? Number
    return if (n != null && js("isFinite(n)") as Boolean) n.toDouble() else raw.toString().replace(",", "").toDoubleOrNull()
}

/**
 * Reads stock location from selected car objects, then [carBookingDisplayedCars] by chassis.
 */
fun resolveStockLocationForFreight(cars: List<dynamic>): String {
    for (car in cars) {
        val a = js("(function(c){ return c && c.stockLocation != null ? String(c.stockLocation).trim() : ''; })(car)").unsafeCast<String>()
        if (a.isNotEmpty()) return a
        val b = js("(function(c){ return c && c.stock_location != null ? String(c.stock_location).trim() : ''; })(car)").unsafeCast<String>()
        if (b.isNotEmpty()) return b
    }
    for (car in cars) {
        val ch = car.chassis?.toString()?.trim() ?: continue
        for (c in carBookingDisplayedCars) {
            if (c.chassis?.toString()?.trim() == ch) {
                val sl = c.stockLocation ?: c.stock_location
                val s = sl?.toString()?.trim().orEmpty()
                if (s.isNotEmpty()) return s
            }
        }
    }
    return ""
}

/**
 * Loads tiers from GET /shipping-charge-map/mappings; calls [onDone] with true if at least one tier exists for [stockLocation].
 */
fun fetchFreightScmTiersForStockLocation(stockLocation: String, onDone: (Boolean) -> Unit) {
    val stock = stockLocation.trim()
    if (stock.isEmpty()) {
        freightScmTiersSorted = emptyList()
        onDone(false)
        return
    }
    window.fetch(apiUrl("shipping-charge-map/mappings"))
        .then { r: dynamic ->
            if (js("r.ok") as Boolean) r.json() else throw js("Error('load')")
        }
        .then { result: dynamic ->
            val data = js("result.data") ?: js("[]")
            val arr = js("Array.isArray(data) ? data : []").unsafeCast<Array<dynamic>>()
            val pairs = mutableListOf<Pair<Int, Double>>()
            for (i in 0 until arr.size) {
                val row = arr[i] ?: continue
                if (!freightScmJsonStr(row, "stockLocation").equals(stock, ignoreCase = true)) continue
                val carsN = freightScmJsonInt(row, "carsPerContainer") ?: continue
                if (carsN <= 0) continue
                val price = freightScmJsonDouble(row, "shippingPricePerCar") ?: continue
                pairs.add(carsN to price)
            }
            pairs.sortBy { it.first }
            freightScmTiersSorted = pairs.distinctBy { it.first }
            onDone(freightScmTiersSorted.isNotEmpty())
        }
        .catch { _: dynamic ->
            freightScmTiersSorted = emptyList()
            onDone(false)
        }
}

private fun getFreightNumberOfContainersInput(): Int =
    (document.getElementById("numberOfContainers") as? HTMLInputElement)?.value?.toIntOrNull()?.coerceIn(1, 10) ?: 1

/**
 * Resolves the tier (cars_per_container, shipping_price_per_car) exactly matching [carCount].
 * Returns null if no exact match is found.
 */
private fun tierForCarCount(carCount: Int): Pair<Int, Double>? {
    if (freightScmTiersSorted.isEmpty()) return null
    return freightScmTiersSorted.find { it.first == carCount }
}

fun createFreightCalculationHTML(selectedCars: List<dynamic>): String {
    val formState = window.asDynamic().freightFormState
    val cpStr = if (formState != null && formState.containerPrice != null) formState.containerPrice as String else "3000"
    val yrStr = if (formState != null && formState.yenRate != null) formState.yenRate as String else "150"
    val ncStr = if (formState != null && formState.numberOfContainers != null) formState.numberOfContainers as String else "2"
    
    val cp = parseCurrency(cpStr)
    val yr = yrStr.toDoubleOrNull() ?: 0.0
    val total = (cp * yr).toInt()
    val totalStr = "¥${total.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}"

    return """
        <div class="freight-container">
            <!-- Back Button -->
            <div style="margin-bottom: 20px;">
                <button id="backToCnfBtn" class="cnf-back-btn">← Back to C&F</button>
            </div>
            
            <!-- Freight Calculation Container -->
            <div class="freight-card">
                
                <!-- Header -->
                <div class="freight-header">
                    <h1>CALCULATE FREIGHT & SHIPPING CHARGE:</h1>
                </div>
                
                <!-- Overall Container Price Calculation Section -->
                <div class="freight-price-section">
                    <div class="freight-price-row">
                        <label>CONTAINER PRICE :</label>
                        <input type="text" id="containerPrice" value="$cpStr" placeholder="0" inputmode="decimal" class="money-input">
                        <span class="operator">×</span>
                        <label>YEN RATE :</label>
                        <input type="number" id="yenRate" value="$yrStr">
                        <span class="operator">=</span>
                        <label>TOTAL PER CONTAINER PRICE :</label>
                        <input type="text" id="totalPerContainerPrice" value="$totalStr" readonly style="background-color: #f3f4f6; font-weight: bold; width: 150px;">
                    </div>
                    
                    <div class="freight-price-row">
                        <label>NO. OF CONTAINERS:</label>
                        <input type="number" id="numberOfContainers" value="$ncStr" min="1" max="10" style="width: 100px;">
                    </div>
                    
                </div>
                
                <!-- Container Sections -->
                <div id="containerSections">
                    <!-- Container sections will be generated dynamically -->
                </div>
                
                <!-- Confirm Button -->
                <div style="text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #e5e7eb;">
                    <button id="confirmFreightBtn" class="freight-confirm-btn">CONFIRM</button>
                </div>
            </div>
        </div>
    """
}

fun setupFreightCalculationListeners(currentSelectedCars: List<dynamic>) {
    Logger.debug("Setting up freight calculation listeners...")
    
    // Restrict money inputs to prevent corruption
    window.setTimeout({
        val containerInput = document.getElementById("containerPrice") as? HTMLInputElement ?: return@setTimeout
        containerInput.addEventListener("paste", { ev -> ev.preventDefault() })
        containerInput.addEventListener("drop", { ev -> ev.preventDefault() })
    }, 100)
    
    // Back to C&F button
    document.getElementById("backToCnfBtn")?.addEventListener("click", { _: Event ->
        // Pass the selected cars back to C&F page
        val selectedCars = cnfPageSelectedCars
        val selectedChassis = if (selectedCars.isNotEmpty()) selectedCars[0].chassis else null
        Logger.debug("Returning to C&F page with ${selectedCars.size} selected cars")
        showCnfCalculationPage(selectedChassis, selectedCars, cnfPageSelectedCountry, cnfPageIsFobMode)
    })
    
    // Container price and yen rate calculation
    val containerPriceInput = document.getElementById("containerPrice") as HTMLInputElement?
    val yenRateInput = document.getElementById("yenRate") as HTMLInputElement?
    val totalPerContainerPriceInput = document.getElementById("totalPerContainerPrice") as HTMLInputElement?
    
    fun calculateTotalPerContainer() {
        // containerPrice uses money-input — value may contain commas; toDoubleOrNull() returns null → ¥0 bug
        val containerPrice = parseCurrency(containerPriceInput?.value ?: "")
        val yenRate = yenRateInput?.value?.toDoubleOrNull() ?: 0.0
        val total = containerPrice * yenRate
        val totalStr = "¥${total.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}"
        totalPerContainerPriceInput?.value = totalStr
        
        val numberOfContainers = getFreightNumberOfContainersInput()
        for (i in 1..numberOfContainers) {
            val containerId = "container$i"
            val priceInput = document.getElementById("${containerId}Price") as HTMLInputElement?
            priceInput?.value = totalStr
            calculateContainerFreightAllocation(containerId, currentSelectedCars)
        }
    }
    
    containerPriceInput?.addEventListener("input", { _: Event -> calculateTotalPerContainer() })
    yenRateInput?.addEventListener("input", { _: Event -> calculateTotalPerContainer() })
    
    // Number of containers change - regenerate sections with current selected cars
    val numberOfContainersInput = document.getElementById("numberOfContainers") as? HTMLInputElement
    numberOfContainersInput?.addEventListener("change", { _: Event ->
        generateContainerSections(currentSelectedCars)
    })
    numberOfContainersInput?.addEventListener("input", { _: Event ->
        generateContainerSections(currentSelectedCars)
    })
    
    // Confirm freight button
    document.getElementById("confirmFreightBtn")?.addEventListener("click", { _: Event ->
        confirmFreightCalculation()
    })
    
    // Container sections are generated from showCalculateFreightPage with selected cars
}

fun generateContainerSections(allSelectedCars: List<dynamic>) {
    val numberOfContainers = getFreightNumberOfContainersInput()
    val totalPerContainerPrice = (document.getElementById("totalPerContainerPrice") as HTMLInputElement?)?.value ?: "¥450,000"
    val containerSections = document.getElementById("containerSections")
    
    if (containerSections == null) return
    
    // Use the provided allSelectedCars
    val availableCars = allSelectedCars.toMutableList()
    
    Logger.debug("generateContainerSections called with ${allSelectedCars.size} selected cars")
    Logger.debug("Selected cars: ${allSelectedCars.size} items")
    
    var containerHTML = ""
    
    for (i in 1..numberOfContainers) {
        val containerId = "container$i"
        val tierInfo = """<div id="${containerId}TierInfo" class="freight-container-row" style="font-size:13px;color:#374151;"><span>Current cars: <strong>0</strong> · Applied shipping charge: <strong>¥0</strong></span></div>"""
        val removeBtnHtml =
            if (numberOfContainers > 1) {
                """<button type="button" onclick="window.removeFreightContainer($i)" style="margin-left:12px; padding:4px 10px; background-color:#dc2626; color:white; border:none; border-radius:4px; cursor:pointer; font-size:12px;">Remove</button>"""
            } else {
                ""
            }

        containerHTML += """
            <div class="freight-container-section" data-container-index="$i">
                <div class="freight-container-row">
                    <label>CONTAINER NO.$i:</label>
                    <input type="text" id="${containerId}Price" value="$totalPerContainerPrice" readonly style="background-color: #f3f4f6; font-weight: bold; width: 150px;">
                    $removeBtnHtml
                </div>
                $tierInfo
                <div class="freight-container-row">
                    <label>SELECT CARS IN CONTAINER .$i :</label>
                    <select id="${containerId}CarSelect" style="width: 200px;">
                        <option value="">SELECT</option>
                    </select>
                </div>
                
                <div id="${containerId}CarList" style="margin-left: 20px;">
                    <!-- Selected cars for this container will be displayed here -->
                </div>
                
            </div>
        """
    }
    
    containerSections.innerHTML = containerHTML
    
    rebuildFreightContainerDropdowns(allSelectedCars)
    
    // Setup car selection listeners for each container
    for (i in 1..numberOfContainers) {
        val containerId = "container$i"
        val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
        carSelect?.addEventListener("change", { _: Event ->
            handleContainerCarSelection(containerId, i, availableCars, allSelectedCars)
        })
    }
}

fun updateContainerTierInfo(containerId: String) {
    val carList = document.getElementById("${containerId}CarList") ?: return
    val carItems = carList.querySelectorAll("[data-chassis]")
    val count = carItems.length
    
    val tierInfoDiv = document.getElementById("${containerId}TierInfo")
    val tier = tierForCarCount(count)
    val shippingYen = tier?.second ?: 0.0
    
    if (tierInfoDiv != null) {
        val tierYenStr = shippingYen.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
        tierInfoDiv.innerHTML = """<span>Current cars: <strong>$count</strong> · Applied shipping charge: <strong>¥$tierYenStr</strong></span>"""
    }
    
    for (i in 0 until carItems.length) {
        val el = carItems.item(i) as HTMLElement
        el.setAttribute("data-shipping-yen", shippingYen.toString())
        val shipLabelSpan = el.querySelector(".ship-car-label")
        if (shipLabelSpan != null) {
            val shipFmt = shippingYen.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
            shipLabelSpan.textContent = "Ship/car: ¥$shipFmt"
        }
    }
}

fun handleContainerCarSelection(containerId: String, containerNumber: Int, availableCars: List<dynamic>, allSelectedCars: List<dynamic>) {
    val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
    val selectedChassis = carSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) return
    
    Logger.debug("Car selected in container $containerNumber: $selectedChassis")
    
    carSelect?.selectedIndex = 0
    
    // Add the car to the container first
    addCarToContainer(containerId, selectedChassis, allSelectedCars, containerNumber, availableCars)
}

/** Each container dropdown shows only chassis not yet allocated to any container (unique globally). */
fun rebuildFreightContainerDropdowns(allSelectedCars: List<dynamic>) {
    val numberOfContainers = getFreightNumberOfContainersInput()
    val allocated = getAllSelectedCarsFromContainers().toSet()
    for (i in 1..numberOfContainers) {
        val containerId = "container$i"
        val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement? ?: continue
        carSelect.innerHTML = "<option value=\"\">SELECT</option>"
        for (car in allSelectedCars) {
            val carChassis = car.chassis?.toString()?.trim() ?: ""
            if (carChassis.isEmpty()) continue
            if (carChassis in allocated) continue
            val option = document.createElement("option") as HTMLOptionElement
            option.value = carChassis
            val nm = car.name?.toString() ?: ""
            val yr = car.year?.toString() ?: car.carModelYear?.toString() ?: ""
            option.textContent = "$carChassis - $nm ($yr)"
            carSelect.appendChild(option)
        }
        carSelect.selectedIndex = 0
    }
    Logger.debug("Rebuilt freight dropdowns for $numberOfContainers container(s); allocated count=${allocated.size}")
}

fun getAllSelectedCarsFromContainers(): List<String> {
    val selectedCars = mutableListOf<String>()
    
    for (i in 1..10) { // Assuming max 10 containers
        val containerId = "container$i"
        val carList = document.getElementById("${containerId}CarList")
        
        if (carList != null) {
            val carItems = carList.querySelectorAll("[data-chassis]")
            for (j in 0 until carItems.length) {
                val carItem = carItems.item(j) as HTMLElement
                val chassis = carItem.getAttribute("data-chassis")
                if (chassis != null && chassis.isNotEmpty()) {
                    selectedCars.add(chassis)
                }
            }
        }
    }
    
    Logger.debug("All selected cars in containers: ${selectedCars.size} items")
    return selectedCars
}

fun addCarToContainer(containerId: String, chassis: String, allSelectedCars: List<dynamic>, containerNumber: Int? = null, availableCars: List<dynamic>? = null) {
    if (chassis.isEmpty()) return
    
    val car = allSelectedCars.find { it.chassis == chassis }
    if (car == null) {
        Logger.error("Car with chassis $chassis not found in selected cars list.")
        return
    }

    if (getAllSelectedCarsFromContainers().contains(chassis)) {
        Logger.warn("Blocked duplicate allocation for chassis $chassis")
        showMessage("This chassis is already assigned to a container.", "error")
        rebuildFreightContainerDropdowns(allSelectedCars)
        return
    }
    
    val carList = document.getElementById("${containerId}CarList")
    if (carList == null) return

    val cIdx = containerNumber ?: containerId.removePrefix("container").takeWhile { it.isDigit() }.toIntOrNull() ?: 1
    
    // Check if car is already in this container
    val existingCar = carList.querySelector("[data-chassis='$chassis']")
    if (existingCar != null) return
    
    Logger.debug("Adding car $chassis to container $containerId")
    
    // Fetch C&F price for this chassis
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/costs-by-chassis/$encodedChassis")
    window.fetch(url)
        .then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                Logger.error("Failed to fetch C&F price for chassis: $chassis")
                js("Promise.resolve({})")
            }
        }
        .then { costData: dynamic ->
            Logger.debug("C&F data for $chassis received")
            
            // Calculate total C&F price
            val carPrice = (costData.carPrice as? Number)?.toDouble() ?: 0.0
            val auctionFee = (costData.auctionFee as? Number)?.toDouble() ?: 0.0
            val rixoPrice = (costData.rixoPrice as? Number)?.toDouble() ?: 0.0
            val shippingCharge = (costData.shippingCharge as? Number)?.toDouble() ?: 0.0
            val freight = (costData.freight as? Number)?.toDouble() ?: 0.0
            val inspectionFee = (costData.inspectionFee as? Number)?.toDouble() ?: 0.0
            val repairFee = (costData.repairFee as? Number)?.toDouble() ?: 0.0
            val auctionPenaltyFee = (costData.auctionPenaltyFee as? Number)?.toDouble() ?: 0.0
            val mscCharges = (costData.mscCharges as? Number)?.toDouble() ?: 0.0
            val profit = (costData.profit as? Number)?.toDouble() ?: 0.0
            
            val totalCnfPrice = carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + auctionPenaltyFee + mscCharges + profit
            
            // Add car to container list with C&F price
            val carItem = document.createElement("div")
            carItem.setAttribute("data-chassis", chassis)
            carItem.setAttribute("data-cnf-price", totalCnfPrice.toString())
            (carItem as HTMLElement).style.cssText = "display: flex; align-items: center; gap: 10px; margin-bottom: 8px; padding: 8px; background-color: #f8fafc; border-radius: 4px; flex-wrap: wrap;"
            
            val shipLabel = """<span class="ship-car-label" style="font-size:12px;color:#4b5563;white-space:nowrap;"></span>"""
            val carNumber = carList.children.length + 1
            carItem.innerHTML = """
                <span style="font-weight: 600;">$carNumber.</span>
                <span style="flex: 1;">${car.chassis} - ${car.name} (${car.year}) :</span>
                $shipLabel
                <input type="text" value="" placeholder="Freight" style="width: 100px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; text-align: right;">
                <button type="button" onclick="window.removeCarFromContainer('$containerId', '$chassis')" style="padding: 4px 8px; background-color: #dc2626; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Remove</button>
            """
            
            carList.appendChild(carItem)
            
            // Recalculate freight allocation and update dynamic tier info
            calculateContainerFreightAllocation(containerId, allSelectedCars)
            updateContainerTierInfo(containerId)
            
            Logger.debug("Car $chassis added to container $containerId with C&F price ¥${totalCnfPrice.toInt()}")
            
            rebuildFreightContainerDropdowns(allSelectedCars)
        }
        .catch { error: dynamic ->
            Logger.error("Error fetching C&F price for $chassis: ${error.toString()}")
            
            // Fallback: add car without C&F price
            val carItem = document.createElement("div")
            carItem.setAttribute("data-chassis", chassis)
            (carItem as HTMLElement).style.cssText = "display: flex; align-items: center; gap: 10px; margin-bottom: 8px; padding: 8px; background-color: #f8fafc; border-radius: 4px; flex-wrap: wrap;"
            
            val shipLabelCatch = """<span class="ship-car-label" style="font-size:12px;color:#4b5563;white-space:nowrap;"></span>"""
            val carNumber = carList.children.length + 1
            carItem.innerHTML = """
                <span style="font-weight: 600;">$carNumber.</span>
                <span style="flex: 1;">${car.chassis} - ${car.name} (${car.year}) :</span>
                $shipLabelCatch
                <span style="font-weight: 600; color: #dc2626;">C&F: N/A</span>
                <input type="text" value="" placeholder="Freight" style="width: 100px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; text-align: right;">
                <button type="button" onclick="window.removeCarFromContainer('$containerId', '$chassis')" style="padding: 4px 8px; background-color: #dc2626; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Remove</button>
            """
            
            carList.appendChild(carItem)
            
            // Recalculate freight allocation and update dynamic tier info
            calculateContainerFreightAllocation(containerId, allSelectedCars)
            updateContainerTierInfo(containerId)
            
            rebuildFreightContainerDropdowns(allSelectedCars)
        }
}

fun removeCarFromContainer(containerId: String, chassis: String, allSelectedCars: List<dynamic>) {
    Logger.debug("Removing car $chassis from container $containerId")
    
    val carList = document.getElementById("${containerId}CarList")
    val carItem = carList?.querySelector("[data-chassis='$chassis']")
    carItem?.remove()
    
    // Update all container dropdowns so removed chassis becomes available again everywhere
    rebuildFreightContainerDropdowns(allSelectedCars)
    
    // Recalculate freight allocation and tier info
    calculateContainerFreightAllocation(containerId, allSelectedCars)
    updateContainerTierInfo(containerId)
    
    Logger.debug("Car $chassis removed from container $containerId")
}

/**
 * Removes a single container by 1-based [removeIdx] and renumbers the rest.
 * - Cars in the removed container return to the unallocated pool (dropdowns).
 * - Cars in containers k > removeIdx slide left to (k-1); their tier is recomputed via cycling.
 * - If a slid container's car count exceeds the new tier capacity, surplus cars are pushed back to the pool.
 */
fun removeFreightContainer(removeIdx: Int) {
    val current = getFreightNumberOfContainersInput()
    if (removeIdx < 1 || removeIdx > current) return
    if (current <= 1) {
        showMessage("Cannot remove the only container.", "warning")
        return
    }

    val allSelectedCars = when {
        cnfPageSelectedCars.isNotEmpty() -> cnfPageSelectedCars
        cnfConfirmedCars.isNotEmpty() -> cnfConfirmedCars
        fobConfirmedCars.isNotEmpty() -> fobConfirmedCars
        else -> emptyList()
    }

    // Snapshot chassis allocations per old container index before re-rendering.
    val perContainer = mutableMapOf<Int, List<String>>()
    for (i in 1..current) {
        val carList = document.getElementById("container${i}CarList") ?: continue
        val items = carList.querySelectorAll("[data-chassis]")
        val list = mutableListOf<String>()
        for (j in 0 until items.length) {
            val el = items.item(j) as HTMLElement
            val ch = el.getAttribute("data-chassis")?.trim().orEmpty()
            if (ch.isNotEmpty()) list.add(ch)
        }
        perContainer[i] = list
    }

    // Decrement count and re-render blank containers.
    val newCount = current - 1
    val numInput = document.getElementById("numberOfContainers") as? HTMLInputElement
    numInput?.value = newCount.toString()
    generateContainerSections(allSelectedCars)

    // Re-allocate cars: container k < removeIdx keeps index; k > removeIdx slides to k - 1.
    // Track placement count in-memory because addCarToContainer is async (fetches costs).
    for (oldIdx in 1..current) {
        if (oldIdx == removeIdx) continue
        val newIdx = if (oldIdx < removeIdx) oldIdx else oldIdx - 1
        val newContainerId = "container$newIdx"
        val cars = perContainer[oldIdx] ?: continue
        for (chassis in cars) {
            addCarToContainer(newContainerId, chassis, allSelectedCars, newIdx)
        }
    }

    rebuildFreightContainerDropdowns(allSelectedCars)
    Logger.debug("Removed freight container $removeIdx; new count = $newCount")
}

/**
 * Weight for splitting container freight: prefer numeric [data-cnf-price] from the row (set when costs-by-chassis loads).
 * Fall back to parsing purchase price strings — never cast dynamic [car.price] to Double (it is often a String).
 */
private fun cnfWeightForFreightRow(carItem: HTMLElement, car: dynamic?): Double {
    val attr = carItem.getAttribute("data-cnf-price")?.trim().orEmpty()
    if (attr.isNotEmpty()) {
        val fromAttr = attr.toDoubleOrNull()
        if (fromAttr != null && !fromAttr.isNaN()) return kotlin.math.max(0.0, fromAttr)
    }
    if (car != null) {
        val raw = js("(function(c) { return (c && (c.price != null && c.price !== undefined)) ? c.price : ((c.carPrice != null && c.carPrice !== undefined) ? c.carPrice : null); })")(car)
        if (raw != null && raw != js("undefined")) {
            val parsed = parseCurrency(raw.toString())
            if (parsed > 0.0) return parsed
        }
    }
    return 0.0
}

fun calculateContainerFreightAllocation(containerId: String, allSelectedCars: List<dynamic>) {
    val carList = document.getElementById("${containerId}CarList")
    if (carList == null) return
    
    val totalPrice = (document.getElementById("${containerId}Price") as HTMLInputElement?)?.value ?: "¥450,000"
    val totalAmount = totalPrice.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 450000.0
    
    val carItems = carList.querySelectorAll("[data-chassis]")
    val carCount = carItems.length
    
    if (carCount == 0) return
    
    // Calculate total C&F price for all cars in this container
    var totalCnfPrice = 0.0
    val cnfPrices = mutableListOf<Double>()
    
    for (i in 0 until carItems.length) {
        val carItem = carItems[i] as HTMLElement
        val chassis = carItem.getAttribute("data-chassis")
        val car = allSelectedCars.find { it.chassis == chassis }
        val cnfPrice = cnfWeightForFreightRow(carItem, car)
        cnfPrices.add(cnfPrice)
        totalCnfPrice += cnfPrice
    }
    
    // Distribute freight based on C&F price ratios
    for (i in 0 until carItems.length) {
        val carItem = carItems[i] as HTMLElement
        val cnfPrice = cnfPrices[i]
        
        // Calculate allocation based on C&F price ratio
        val allocationRatio = if (totalCnfPrice > 0) cnfPrice / totalCnfPrice else 1.0 / carCount
        val allocatedAmount = totalAmount * allocationRatio
        
        // Update the freight allocation input
        val freightInput = carItem.querySelector("input[type='text']") as HTMLInputElement?
        freightInput?.value = "¥${allocatedAmount.toInt()}"
        
        Logger.debug("Car ${carItem.getAttribute("data-chassis")}: C&F ¥${cnfPrice.toInt()}, Freight ¥${allocatedAmount.toInt()}")
    }
}

fun confirmFreightCalculation() {
    Logger.debug("Confirming freight calculation and collecting freight values...")

    val expectedChassis = cnfPageSelectedCars.mapNotNull { car ->
        val c = car.chassis
        if (c != null) c.toString().trim() else null
    }.filter { it.isNotEmpty() }.distinct()
    val allocatedChassis = getAllSelectedCarsFromContainers().distinct()
    val allocatedSet = allocatedChassis.toSet()
    val missing = expectedChassis.filter { it !in allocatedSet }
    if (expectedChassis.isNotEmpty() && missing.isNotEmpty()) {
        val preview = missing.take(8).joinToString(", ")
        val suffix = if (missing.size > 8) " (+${missing.size - 8} more)" else ""
        showMessage("Allocate every selected car to a container before confirming. Not allocated: $preview$suffix", "error")
        return
    }

    // Removed hard capacity limits based on Shipping Charge Map tiers

    // Collect freight values from all containers
    val freightValues = mutableMapOf<String, Double>()
    
    val formState = js("{}")
    formState.containerPrice = (document.getElementById("containerPrice") as HTMLInputElement?)?.value ?: "3000"
    formState.yenRate = (document.getElementById("yenRate") as HTMLInputElement?)?.value ?: "150"
    formState.numberOfContainers = (document.getElementById("numberOfContainers") as HTMLInputElement?)?.value ?: "2"
    val containerAllocations = js("{}")
    
    // Get all container sections
    val containerSections = document.getElementById("containerSections")
    Logger.debug("DEBUG: containerSections found")
    if (containerSections != null) {
        val containers = containerSections.querySelectorAll("[id^='container'][id$='CarList']")
        Logger.debug("DEBUG: Found ${containers.length} containers")
        for (i in 0 until containers.length) {
            val container = containers.item(i) as HTMLElement
            val containerId = container.id.replace("CarList", "")
            Logger.debug("DEBUG: Processing container $i, containerId: $containerId")
            val carList = document.getElementById(container.id)
            Logger.debug("DEBUG: carList for ${container.id} found")
            if (carList != null) {
                val carItems = carList.querySelectorAll("[data-chassis]")
                val chassisList = js("[]")
                Logger.debug("DEBUG: Found ${carItems.length} car items in container $containerId")
                for (j in 0 until carItems.length) {
                    val carItem = carItems.item(j) as HTMLElement
                    val chassis = carItem.getAttribute("data-chassis")
                    Logger.debug("DEBUG: Processing car item $j, chassis: $chassis")
                    
                    if (chassis != null) {
                        chassisList.push(chassis)
                    }
                    
                    val freightInput = carItem.querySelector("input[type='text']") as HTMLInputElement?
                    Logger.debug("DEBUG: Freight input found: ${freightInput != null}, value: ${freightInput?.value}")
                    
                    if (chassis != null && freightInput != null) {
                        val freightValue = freightInput.value.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
                        freightValues[chassis] = freightValue
                        Logger.debug("Collected freight for $chassis: ¥${freightValue.toInt()}")
                    } else {
                        Logger.debug("DEBUG: Missing chassis or freight input for car item $j")
                    }
                }
                containerAllocations[containerId] = chassisList
            }
        }
    }
    
    formState.allocations = containerAllocations
    window.asDynamic().freightFormState = formState
    
    // Store freight values globally for C&F page - use Kotlin variable directly
    globalFreightValues.clear()
    for ((chassis, value) in freightValues) {
        globalFreightValues[chassis] = value
    }
    
    // Also expose to JavaScript window object for compatibility
    val windowFreightValues = js("{}")
    for ((chassis, value) in freightValues) {
        windowFreightValues[chassis] = value
    }
    window.asDynamic().globalFreightValues = windowFreightValues
    
    globalShippingChargeValues.clear()
    val nc = getFreightNumberOfContainersInput()
    for (i in 1..nc) {
        val carList = document.getElementById("container${i}CarList") ?: continue
        val carItems = carList.querySelectorAll("[data-chassis]")
        for (j in 0 until carItems.length) {
            val el = carItems.item(j) as HTMLElement
            val ch = el.getAttribute("data-chassis")?.trim().orEmpty()
            val yenStr = el.getAttribute("data-shipping-yen")?.trim().orEmpty()
            if (ch.isNotEmpty() && yenStr.isNotEmpty()) {
                val yen = yenStr.toDoubleOrNull()
                if (yen != null) {
                    globalShippingChargeValues[ch] = yen
                }
            }
        }
    }
    val windowShipValues = js("{}")
    for ((chassis, value) in globalShippingChargeValues) {
        windowShipValues[chassis] = value
    }
    window.asDynamic().globalShippingChargeValues = windowShipValues
    
    Logger.debug("Stored freight values: ${freightValues.size} items")
    Logger.debug("Stored in globalFreightValues (Kotlin): ${globalFreightValues.size} items")
    Logger.debug("Stored in window.globalFreightValues (JS)")
    
    lastCalculationMode = "C&F"
    js("window.lastCalculationMode = 'C&F'")
    Logger.debug("Set lastCalculationMode to C&F (freight calculated)")
    
    // Navigate back to C&F Calculation page with freight values
    Logger.debug("Returning to C&F Calculation page with freight values...")
    val selectedCars = cnfPageSelectedCars
    val selectedChassis = if (selectedCars.isNotEmpty()) selectedCars[0].chassis else null
    showCnfCalculationPage(selectedChassis, selectedCars, cnfPageSelectedCountry, cnfPageIsFobMode)
    
    showMessage("Freight and shipping charge values saved and applied to C&F calculation", "success")
}

fun restoreFreightAllocations(allSelectedCars: List<dynamic>) {
    val formState = window.asDynamic().freightFormState
    if (formState == null || formState.allocations == null) return
    
    val allocations = formState.allocations
    val keys = js("Object.keys(allocations)") as Array<String>
    
    for (containerId in keys) {
        val chassisList = allocations[containerId] as? Array<String> ?: continue
        for (chassis in chassisList) {
            addCarToContainer(containerId, chassis, allSelectedCars)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOB-only: Shipping Charge Calculator (no freight / no container-price fields)
// ─────────────────────────────────────────────────────────────────────────────

/** Entry point called when "CALCULATE SHIPPING CHARGE" is clicked on the FOB page. */
fun showCalculateShippingChargePage() {
    val cars = when {
        cnfPageSelectedCars.isNotEmpty() -> cnfPageSelectedCars
        fobConfirmedCars.isNotEmpty()    -> fobConfirmedCars
        else                             -> emptyList()
    }
    if (cars.isEmpty()) {
        showMessage("No cars selected for shipping charge calculation.", "error")
        return
    }

    val stockLoc = resolveStockLocationForFreight(cars)
    if (stockLoc.isEmpty()) {
        showMessage(
            "Could not determine stock location. Load cars from search so stock location is known.",
            "error",
        )
        return
    }

    fetchFreightScmTiersForStockLocation(stockLoc) { _ ->
        val html = createShippingChargeCalculationHTML()
        val mainContent = document.getElementById("content")
        if (mainContent != null) {
            mainContent.innerHTML = html
            setupShippingChargeCalculationListeners(cars)
            generateShippingChargeContainerSections(cars)
            restoreShippingChargeAllocations(cars)
        }
    }
}

private fun createShippingChargeCalculationHTML(): String {
    val formState = window.asDynamic().shippingChargeFormState
    val ncStr = if (formState != null && formState.numberOfContainers != null) formState.numberOfContainers as String else "1"
    return """
        <div class="freight-container">
            <!-- Back Button -->
            <div style="margin-bottom: 20px;">
                <button id="backToFobBtn" class="cnf-back-btn">← Back to FOB</button>
            </div>

            <!-- Shipping Charge Calculation Card -->
            <div class="freight-card">
                <div class="freight-header">
                    <h1>CALCULATE FREIGHT & SHIPPING CHARGE:</h1>
                </div>

                <div class="freight-price-section">
                    <div class="freight-price-row">
                        <label>NO. OF CONTAINERS:</label>
                        <input type="number" id="scNumberOfContainers" value="$ncStr" min="1" max="10" style="width: 100px;">
                    </div>
                </div>

                <div id="scContainerSections">
                    <!-- Generated dynamically -->
                </div>

                <div style="text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #e5e7eb;">
                    <button id="confirmShippingChargeBtn" class="freight-confirm-btn">CONFIRM</button>
                </div>
            </div>
        </div>
    """
}

private fun setupShippingChargeCalculationListeners(cars: List<dynamic>) {
    // Back to FOB
    document.getElementById("backToFobBtn")?.addEventListener("click", { _: Event ->
        val chassis = if (cars.isNotEmpty()) cars[0].chassis else null
        showCnfCalculationPage(chassis?.toString(), cars, cnfPageSelectedCountry, isFobMode = true)
    })

    // Number of containers change → regenerate sections
    val numInput = document.getElementById("scNumberOfContainers") as? HTMLInputElement
    numInput?.addEventListener("change", { _: Event -> generateShippingChargeContainerSections(cars) })
    numInput?.addEventListener("input",  { _: Event -> generateShippingChargeContainerSections(cars) })

    // Confirm
    document.getElementById("confirmShippingChargeBtn")?.addEventListener("click", { _: Event ->
        confirmShippingChargeCalculation(cars)
    })
}

private fun getScNumberOfContainersInput(): Int =
    (document.getElementById("scNumberOfContainers") as? HTMLInputElement)?.value?.toIntOrNull()?.coerceIn(1, 10) ?: 1

fun generateShippingChargeContainerSections(allCars: List<dynamic>) {
    val n = getScNumberOfContainersInput()
    val containerSections = document.getElementById("scContainerSections") ?: return

    var html = ""
    for (i in 1..n) {
        val tierInfo = """<div id="scContainer${i}TierInfo" class="freight-container-row" style="font-size:13px;color:#374151;">
                   <span>Current cars: <strong>0</strong> · Applied shipping charge: <strong>¥0</strong></span>
               </div>"""

        val removeBtnHtml = if (n > 1) {
            """<button type="button" onclick="window.removeShippingChargeContainer($i)" style="margin-left:12px;padding:4px 10px;background-color:#dc2626;color:white;border:none;border-radius:4px;cursor:pointer;font-size:12px;">Remove</button>"""
        } else ""

        html += """
            <div class="freight-container-section" data-sc-container-index="$i">
                <div class="freight-container-row">
                    <label>CONTAINER NO.$i:</label>
                    $removeBtnHtml
                </div>
                $tierInfo
                <div class="freight-container-row">
                    <label>SELECT CARS IN CONTAINER $i :</label>
                    <select id="scContainer${i}CarSelect" style="width:200px;">
                        <option value="">SELECT</option>
                    </select>
                </div>
                <div id="scContainer${i}CarList" style="margin-left:20px;"></div>
            </div>
        """
    }

    containerSections.innerHTML = html
    rebuildShippingChargeContainerDropdowns(allCars)

    for (i in 1..n) {
        val sel = document.getElementById("scContainer${i}CarSelect") as? HTMLSelectElement
        sel?.addEventListener("change", { _: Event ->
            val ch = sel.value
            if (ch.isNotEmpty()) {
                sel.selectedIndex = 0
                addCarToShippingChargeContainer("scContainer$i", ch, allCars, i)
            }
        })
    }
}

fun updateShippingChargeContainerTierInfo(containerId: String) {
    val carList = document.getElementById("${containerId}CarList") ?: return
    val carItems = carList.querySelectorAll("[data-chassis]")
    val count = carItems.length
    
    val tierInfoDiv = document.getElementById("${containerId}TierInfo")
    val tier = tierForCarCount(count)
    val shippingYen = tier?.second ?: 0.0
    
    if (tierInfoDiv != null) {
        val tierYenStr = shippingYen.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
        tierInfoDiv.innerHTML = """<span>Current cars: <strong>$count</strong> · Applied shipping charge: <strong>¥$tierYenStr</strong></span>"""
    }
    
    for (i in 0 until carItems.length) {
        val el = carItems.item(i) as HTMLElement
        el.setAttribute("data-shipping-yen", shippingYen.toString())
        val shipLabelSpan = el.querySelector(".ship-car-label")
        if (shipLabelSpan != null) {
            val shipFmt = shippingYen.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
            shipLabelSpan.textContent = "Ship/car: ¥$shipFmt"
        }
    }
}

private fun getAllAllocatedShippingChargeChassis(): Set<String> {
    val result = mutableSetOf<String>()
    for (i in 1..10) {
        val carList = document.getElementById("scContainer${i}CarList") ?: continue
        val items = carList.querySelectorAll("[data-chassis]")
        for (j in 0 until items.length) {
            val ch = (items.item(j) as HTMLElement).getAttribute("data-chassis")?.trim().orEmpty()
            if (ch.isNotEmpty()) result.add(ch)
        }
    }
    return result
}

internal fun rebuildShippingChargeContainerDropdowns(allCars: List<dynamic>) {
    val n = getScNumberOfContainersInput()
    val allocated = getAllAllocatedShippingChargeChassis()
    for (i in 1..n) {
        val sel = document.getElementById("scContainer${i}CarSelect") as? HTMLSelectElement ?: continue
        sel.innerHTML = "<option value=\"\">SELECT</option>"
        for (car in allCars) {
            val ch = car.chassis?.toString()?.trim() ?: continue
            if (ch.isEmpty() || ch in allocated) continue
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = ch
            val nm = car.name?.toString() ?: car.carName?.toString() ?: ""
            val yr = car.year?.toString() ?: car.carModelYear?.toString() ?: ""
            opt.textContent = "$ch - $nm ($yr)"
            sel.appendChild(opt)
        }
        sel.selectedIndex = 0
    }
}

private fun addCarToShippingChargeContainer(containerId: String, chassis: String, allCars: List<dynamic>, containerIndex: Int) {
    if (chassis.isEmpty()) return
    val car = allCars.find { it.chassis?.toString()?.trim() == chassis }
    if (car == null) {
        showMessage("Car $chassis not found.", "error")
        return
    }
    if (chassis in getAllAllocatedShippingChargeChassis()) {
        showMessage("Chassis $chassis is already assigned to a container.", "error")
        rebuildShippingChargeContainerDropdowns(allCars)
        return
    }

    val carList = document.getElementById("${containerId}CarList") ?: return
    
    val shipLabel = """<span class="ship-car-label" style="font-size:12px;color:#4b5563;white-space:nowrap;"></span>"""

    val nm = car.name?.toString() ?: car.carName?.toString() ?: ""
    val yr = car.year?.toString() ?: car.carModelYear?.toString() ?: ""
    val carNum = carList.children.length + 1

    val item = document.createElement("div")
    item.setAttribute("data-chassis", chassis)
    (item as HTMLElement).style.cssText = "display:flex;align-items:center;gap:10px;margin-bottom:8px;padding:8px;background-color:#f8fafc;border-radius:4px;flex-wrap:wrap;"
    item.innerHTML = """
        <span style="font-weight:600;">$carNum.</span>
        <span style="flex:1;">$chassis - $nm ($yr)</span>
        $shipLabel
        <button type="button" onclick="window.removeCarFromShippingChargeContainer('$containerId','$chassis')"
            style="padding:4px 8px;background-color:#dc2626;color:white;border:none;border-radius:4px;cursor:pointer;font-size:12px;">Remove</button>
    """
    carList.appendChild(item)
    updateShippingChargeContainerTierInfo(containerId)
    rebuildShippingChargeContainerDropdowns(allCars)
}

fun removeCarFromShippingChargeContainer(containerId: String, chassis: String) {
    val carList = document.getElementById("${containerId}CarList")
    carList?.querySelector("[data-chassis='$chassis']")?.remove()
    updateShippingChargeContainerTierInfo(containerId)
    // The rebuild requires `allCars`. We can grab it from global context or pass it in.
    // For now, let's just trigger a re-render from the UI when a car is removed.
    // In FOB mode, the list of all cars is derived in showCalculateShippingChargePage.
    // The previous implementation didn't even have this function defined here, it was inline in HTML.
    val cars = when {
        cnfPageSelectedCars.isNotEmpty() -> cnfPageSelectedCars
        fobConfirmedCars.isNotEmpty()    -> fobConfirmedCars
        else                             -> emptyList<dynamic>()
    }
    rebuildShippingChargeContainerDropdowns(cars)
}

private fun confirmShippingChargeCalculation(allCars: List<dynamic>) {
    // Validate all selected cars are allocated
    val expectedChassis = allCars.mapNotNull { it.chassis?.toString()?.trim() }
        .filter { it.isNotEmpty() }.distinct()
    val allocated = getAllAllocatedShippingChargeChassis()
    val missing = expectedChassis.filter { it !in allocated }
    if (expectedChassis.isNotEmpty() && missing.isNotEmpty()) {
        val preview = missing.take(8).joinToString(", ")
        val suffix = if (missing.size > 8) " (+${missing.size - 8} more)" else ""
        showMessage("Allocate every selected car to a container before confirming. Not allocated: $preview$suffix", "error")
        return
    }

    // Save form state
    val formState = js("{}")
    formState.numberOfContainers = (document.getElementById("scNumberOfContainers") as? HTMLInputElement)?.value ?: "1"
    val containerAllocations = js("{}")

    // Collect per-chassis shipping charge from dynamically updated data attributes
    globalShippingChargeValues.clear()
    val n = getScNumberOfContainersInput()
    for (i in 1..n) {
        val carList = document.getElementById("scContainer${i}CarList") ?: continue
        val items = carList.querySelectorAll("[data-chassis]")
        val chassisList = js("[]")
        for (j in 0 until items.length) {
            val el = items.item(j) as HTMLElement
            val ch = el.getAttribute("data-chassis")?.trim().orEmpty()
            val yenStr = el.getAttribute("data-shipping-yen")?.trim().orEmpty()
            if (ch.isNotEmpty()) {
                val yen = yenStr.toDoubleOrNull()
                if (yen != null) {
                    globalShippingChargeValues[ch] = yen
                }
                chassisList.push(ch)
            }
        }
        containerAllocations["scContainer$i"] = chassisList
    }
    formState.allocations = containerAllocations
    window.asDynamic().shippingChargeFormState = formState

    // Expose to window for compatibility
    val windowShipValues = js("{}")
    for ((ch, v) in globalShippingChargeValues) windowShipValues[ch] = v
    window.asDynamic().globalShippingChargeValues = windowShipValues

    // FOB has no freight
    globalFreightValues.clear()
    window.asDynamic().globalFreightValues = js("{}")

    lastCalculationMode = "FOB"
    js("window.lastCalculationMode = 'FOB'")

    showMessage("Shipping charges applied to FOB calculation.", "success")

    val chassis0 = if (allCars.isNotEmpty()) allCars[0].chassis?.toString() else null
    showCnfCalculationPage(chassis0, allCars, cnfPageSelectedCountry, isFobMode = true)
}

private fun restoreShippingChargeAllocations(allCars: List<dynamic>) {
    val formState = window.asDynamic().shippingChargeFormState ?: return
    val allocations = formState.allocations ?: return
    val keys = js("Object.keys(allocations)") as Array<String>
    for (containerId in keys) {
        val chassisList = allocations[containerId] as? Array<String> ?: continue
        val idx = containerId.removePrefix("scContainer").takeWhile { it.isDigit() }.toIntOrNull() ?: continue
        for (chassis in chassisList) {
            addCarToShippingChargeContainer(containerId, chassis, allCars, idx)
        }
    }
}
