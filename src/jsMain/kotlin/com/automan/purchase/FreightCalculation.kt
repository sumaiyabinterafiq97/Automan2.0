package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger

// Freight Calculation Functions
// Note: Global variables (cnfPageSelectedCars, cnfPageSelectedCountry, globalFreightValues, etc.) are defined in MinimalPurchaseApp.kt

fun createFreightCalculationHTML(selectedCars: List<dynamic>): String {
    return """
        <div style="padding: 20px; background-color: #f9fafb; min-height: 100vh;">
            <!-- Back Button -->
            <div style="margin-bottom: 20px;">
                <button id="backToCnfBtn" style="padding: 8px 16px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px;">← Back to C&F</button>
            </div>
            
            <!-- Freight Calculation Container -->
            <div style="background-color: white; border: 2px solid #8b5cf6; border-radius: 12px; padding: 30px; max-width: 1000px; margin: 0 auto; box-shadow: 0 4px 20px rgba(0,0,0,0.1);">
                
                <!-- Header -->
                <div style="text-align: center; margin-bottom: 30px;">
                    <h1 style="color: #1f2937; font-size: 24px; font-weight: bold; margin: 0;">CALCULATE FREIGHT:</h1>
                </div>
                
                <!-- Overall Container Price Calculation Section -->
                <div style="background-color: #f8fafc; padding: 20px; border-radius: 8px; margin-bottom: 30px;">
                    <div style="display: flex; align-items: center; gap: 15px; flex-wrap: wrap; margin-bottom: 15px;">
                        <label style="font-weight: 600; color: #374151;">CONTAINER PRICE :</label>
                        <input type="number" id="containerPrice" value="3000" style="padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 120px;">
                        <span style="font-size: 18px; font-weight: bold;">×</span>
                        <label style="font-weight: 600; color: #374151;">YEN RATE :</label>
                        <input type="number" id="yenRate" value="150" style="padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 120px;">
                        <span style="font-size: 18px; font-weight: bold;">=</span>
                        <label style="font-weight: 600; color: #374151;">TOTAL PER CONTAINER PRICE :</label>
                        <input type="text" id="totalPerContainerPrice" value="¥450,000" readonly style="padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 150px; background-color: #f3f4f6; font-weight: bold;">
                    </div>
                    
                    <div style="display: flex; align-items: center; gap: 15px; margin-bottom: 15px;">
                        <label style="font-weight: 600; color: #374151;">NO. OF CONTAINERS:</label>
                        <input type="number" id="numberOfContainers" value="2" min="1" max="10" style="padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 100px;">
                    </div>
                    
                </div>
                
                <!-- Container Sections -->
                <div id="containerSections">
                    <!-- Container sections will be generated dynamically -->
                </div>
                
                <!-- Confirm Button -->
                <div style="text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #e5e7eb;">
                    <button id="confirmFreightBtn" style="padding: 16px 32px; background-color: #374151; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px; font-weight: 600;">CONFIRM</button>
                </div>
            </div>
        </div>
    """
}

fun setupFreightCalculationListeners(currentSelectedCars: List<dynamic>) {
    Logger.debug("Setting up freight calculation listeners...")
    
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
        val containerPrice = containerPriceInput?.value?.toDoubleOrNull() ?: 0.0
        val yenRate = yenRateInput?.value?.toDoubleOrNull() ?: 0.0
        val total = containerPrice * yenRate
        totalPerContainerPriceInput?.value = "¥${total.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}"
        // Regenerate sections with current selected cars
        generateContainerSections(currentSelectedCars)
    }
    
    containerPriceInput?.addEventListener("input", { _: Event -> calculateTotalPerContainer() })
    yenRateInput?.addEventListener("input", { _: Event -> calculateTotalPerContainer() })
    
    // Number of containers change - regenerate sections with current selected cars
    document.getElementById("numberOfContainers")?.addEventListener("change", { _: Event ->
        // Use the provided currentSelectedCars
        generateContainerSections(currentSelectedCars)
    })
    
    // Confirm freight button
    document.getElementById("confirmFreightBtn")?.addEventListener("click", { _: Event ->
        confirmFreightCalculation()
    })
    
    // Container sections are generated from showCalculateFreightPage with selected cars
}

fun generateContainerSections(allSelectedCars: List<dynamic>) {
    val numberOfContainers = (document.getElementById("numberOfContainers") as HTMLInputElement?)?.value?.toIntOrNull() ?: 2
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
        val isFirstContainer = i == 1
        
        containerHTML += """
            <div style="border-top: 1px solid #e5e7eb; padding-top: 20px; margin-top: 20px;">
                <div style="display: flex; align-items: center; gap: 15px; margin-bottom: 15px;">
                    <label style="font-weight: 600; color: #374151;">CONTAINER NO.$i:</label>
                    <input type="text" id="${containerId}Price" value="$totalPerContainerPrice" readonly style="padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 150px; background-color: #f3f4f6; font-weight: bold;">
                </div>
                
                <div style="display: flex; align-items: center; gap: 15px; margin-bottom: 15px;">
                    <label style="font-weight: 600; color: #374151;">SELECT CARS IN CONTAINER .$i :</label>
                    <select id="${containerId}CarSelect" style="padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; width: 200px;">
                        <option value="">SELECT</option>
        """
        
        // Add available cars to dropdown (for first container, show all cars)
        if (isFirstContainer) {
            for (car in availableCars) {
                val carInfo = "${car.chassis} - ${car.name} (${car.year})"
                containerHTML += """<option value="${car.chassis}">$carInfo</option>"""
            }
        } else {
            // For subsequent containers, they will be populated dynamically by cascading logic
            // Initially empty except for SELECT option
        }
        
        containerHTML += """
                    </select>
                </div>
                
                <div id="${containerId}CarList" style="margin-left: 20px;">
                    <!-- Selected cars for this container will be displayed here -->
                </div>
                
            </div>
        """
    }
    
    containerSections.innerHTML = containerHTML
    
    // Setup car selection listeners for each container with cascading logic
    for (i in 1..numberOfContainers) {
        val containerId = "container$i"
        val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
        carSelect?.addEventListener("change", { _: Event ->
            handleContainerCarSelection(containerId, i, availableCars, allSelectedCars)
        })
    }
}

fun handleContainerCarSelection(containerId: String, containerNumber: Int, availableCars: List<dynamic>, allSelectedCars: List<dynamic>) {
    val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
    val selectedChassis = carSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) return
    
    Logger.debug("Car selected in container $containerNumber: $selectedChassis")
    
    // Add the car to the container first
    addCarToContainer(containerId, selectedChassis, allSelectedCars, containerNumber, availableCars)
}

fun updateSubsequentContainerDropdowns(currentContainerNumber: Int, selectedChassis: String, availableCars: List<dynamic>) {
    Logger.debug("Updating dropdowns for containers after $currentContainerNumber")
    
    // Get all currently selected cars from all containers
    val allSelectedCarsInContainers = getAllSelectedCarsFromContainers()
    
    // Update each subsequent container dropdown
    for (i in (currentContainerNumber + 1)..10) { // Assuming max 10 containers
        val containerId = "container$i"
        val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
        
        if (carSelect != null) {
            // Clear current options except the first "SELECT" option
            carSelect.innerHTML = "<option value=\"\">SELECT</option>"
            
            // Add only cars that haven't been selected in any container
            for (car in availableCars) {
                val carChassis = car.chassis as? String ?: ""
                if (carChassis.isNotEmpty() && !allSelectedCarsInContainers.contains(carChassis)) {
                    val option = document.createElement("option") as HTMLOptionElement
                    option.value = carChassis
                    option.textContent = "${carChassis} - ${car.name} (${car.year})"
                    carSelect.appendChild(option)
                }
            }
            
            Logger.debug("Updated container $i dropdown with ${carSelect.options.length - 1} available cars")
        }
    }
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
    
    val carList = document.getElementById("${containerId}CarList")
    if (carList == null) return
    
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
            val mscCharges = (costData.mscCharges as? Number)?.toDouble() ?: 0.0
            val profit = (costData.profit as? Number)?.toDouble() ?: 0.0
            
            val totalCnfPrice = carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + mscCharges + profit
            
            // Add car to container list with C&F price
            val carItem = document.createElement("div")
            carItem.setAttribute("data-chassis", chassis)
            carItem.setAttribute("data-cnf-price", totalCnfPrice.toString())
            (carItem as HTMLElement).style.cssText = "display: flex; align-items: center; gap: 10px; margin-bottom: 8px; padding: 8px; background-color: #f8fafc; border-radius: 4px;"
            
            val carNumber = carList.children.length + 1
            carItem.innerHTML = """
                <span style="font-weight: 600;">$carNumber.</span>
                <span style="flex: 1;">${car.chassis} - ${car.name} (${car.year}) :</span>
                <input type="text" value="" placeholder="Enter Freight" style="width: 100px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; text-align: right;">
                <button type="button" onclick="window.removeCarFromContainer('$containerId', '$chassis')" style="padding: 4px 8px; background-color: #dc2626; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Remove</button>
            """
            
            carList.appendChild(carItem)
            
            // Remove car from dropdown
            val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
            val optionToRemove = carSelect?.querySelector("option[value='$chassis']")
            optionToRemove?.remove()
            
            // Recalculate freight allocation
            calculateContainerFreightAllocation(containerId, allSelectedCars)
            
            Logger.debug("Car $chassis added to container $containerId with C&F price ¥${totalCnfPrice.toInt()}")
            
            // Update subsequent container dropdowns to exclude this car (cascading logic)
            if (containerNumber != null && availableCars != null) {
                updateSubsequentContainerDropdowns(containerNumber, chassis, availableCars)
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error fetching C&F price for $chassis: ${error.toString()}")
            
            // Fallback: add car without C&F price
            val carItem = document.createElement("div")
            carItem.setAttribute("data-chassis", chassis)
            (carItem as HTMLElement).style.cssText = "display: flex; align-items: center; gap: 10px; margin-bottom: 8px; padding: 8px; background-color: #f8fafc; border-radius: 4px;"
            
            val carNumber = carList.children.length + 1
            carItem.innerHTML = """
                <span style="font-weight: 600;">$carNumber.</span>
                <span style="flex: 1;">${car.chassis} - ${car.name} (${car.year}) :</span>
                <span style="font-weight: 600; color: #dc2626;">C&F: N/A</span>
                <input type="text" value="¥" style="width: 100px; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; text-align: right;" readonly>
                <button type="button" onclick="window.removeCarFromContainer('$containerId', '$chassis')" style="padding: 4px 8px; background-color: #dc2626; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Remove</button>
            """
            
            carList.appendChild(carItem)
            
            // Remove car from dropdown
            val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
            val optionToRemove = carSelect?.querySelector("option[value='$chassis']")
            optionToRemove?.remove()
            
            // Recalculate freight allocation
            calculateContainerFreightAllocation(containerId, allSelectedCars)
            
            // Update subsequent container dropdowns to exclude this car (cascading logic)
            if (containerNumber != null && availableCars != null) {
                updateSubsequentContainerDropdowns(containerNumber, chassis, availableCars)
            }
        }
    
    // Calculate freight allocation for this container
    calculateContainerFreightAllocation(containerId, allSelectedCars)
}

fun removeCarFromContainer(containerId: String, chassis: String, allSelectedCars: List<dynamic>) {
    Logger.debug("Removing car $chassis from container $containerId")
    
    val carList = document.getElementById("${containerId}CarList")
    val carItem = carList?.querySelector("[data-chassis='$chassis']")
    carItem?.remove()
    
    // Get the container number from containerId (e.g., "container1" -> 1)
    val containerNumber = containerId.replace("container", "").toIntOrNull() ?: 1
    
    // Update all subsequent container dropdowns to include this car back
    updateSubsequentContainerDropdownsAfterRemoval(containerNumber, chassis, allSelectedCars)
    
    // Recalculate freight allocation
    calculateContainerFreightAllocation(containerId, allSelectedCars)
    
    Logger.debug("Car $chassis removed from container $containerId")
}

fun updateSubsequentContainerDropdownsAfterRemoval(removedFromContainerNumber: Int, removedChassis: String, allSelectedCars: List<dynamic>) {
    Logger.debug("Updating dropdowns after removing $removedChassis from container $removedFromContainerNumber")
    
    // Get all currently selected cars from all containers
    val allSelectedCarsInContainers = getAllSelectedCarsFromContainers()
    
    // Update each subsequent container dropdown (including the one the car was removed from)
    for (i in removedFromContainerNumber..10) { // Assuming max 10 containers
        val containerId = "container$i"
        val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
        
        if (carSelect != null) {
            // Clear current options except the first "SELECT" option
            carSelect.innerHTML = "<option value=\"\">SELECT</option>"
            
            // Add only cars that haven't been selected in any container
            for (car in allSelectedCars) {
                val carChassis = car.chassis as? String ?: ""
                if (carChassis.isNotEmpty() && !allSelectedCarsInContainers.contains(carChassis)) {
                    val option = document.createElement("option") as HTMLOptionElement
                    option.value = carChassis
                    option.textContent = "${carChassis} - ${car.name} (${car.year})"
                    carSelect.appendChild(option)
                }
            }
            
            Logger.debug("Updated container $i dropdown with ${carSelect.options.length - 1} available cars")
        }
    }
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
        val cnfPrice = if (car != null && car.price != null) {
            car.price as Double
        } else {
            carItem.getAttribute("data-cnf-price")?.toDoubleOrNull() ?: 0.0
        }
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
    
    // Collect freight values from all containers
    val freightValues = mutableMapOf<String, Double>()
    
    // Get all container sections
    val containerSections = document.getElementById("containerSections")
    Logger.debug("DEBUG: containerSections found")
    if (containerSections != null) {
        val containers = containerSections.querySelectorAll("[id^='container'][id$='CarList']")
        Logger.debug("DEBUG: Found ${containers.length} containers")
        for (i in 0 until containers.length) {
            val container = containers.item(i) as HTMLElement
            val containerId = container.id
            Logger.debug("DEBUG: Processing container $i, containerId: $containerId")
            val carList = document.getElementById(containerId)
            Logger.debug("DEBUG: carList for $containerId found")
            if (carList != null) {
                val carItems = carList.querySelectorAll("[data-chassis]")
                Logger.debug("DEBUG: Found ${carItems.length} car items in container $containerId")
                for (j in 0 until carItems.length) {
                    val carItem = carItems.item(j) as HTMLElement
                    val chassis = carItem.getAttribute("data-chassis")
                    Logger.debug("DEBUG: Processing car item $j, chassis: $chassis")
                    
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
            }
        }
    }
    
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
    
    Logger.debug("Stored freight values: ${freightValues.size} items")
    Logger.debug("Stored in globalFreightValues (Kotlin): ${globalFreightValues.size} items")
    Logger.debug("Stored in window.globalFreightValues (JS)")
    
    // Navigate back to C&F Calculation page with freight values
    Logger.debug("Returning to C&F Calculation page with freight values...")
    val selectedCars = cnfPageSelectedCars
    val selectedChassis = if (selectedCars.isNotEmpty()) selectedCars[0].chassis else null
    showCnfCalculationPage(selectedChassis, selectedCars, cnfPageSelectedCountry, cnfPageIsFobMode)
    
    showMessage("Freight values saved and applied to C&F calculation", "success")
}

