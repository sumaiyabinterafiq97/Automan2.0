package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.files.File
import org.w3c.dom.events.Event

fun main() {
    console.log("Main function called")
    val root = document.getElementById("root")!!
    console.log("Root element found: $root")
    
    createApp(root)
    
    // Set up hash change listener
    window.addEventListener("hashchange", { _: Event -> updateContent(root) })
    
    // Initial content
    updateContent(root)
}

// Formats ISO date (yyyy-MM-dd) to "MonthD, yyyy(Day)" e.g., 2025-06-03 -> "June3, 2025(Tuesday)"
private fun formatWithWeekday(isoDate: String?): String {
    if (isoDate == null || isoDate.isBlank()) return ""
    // If already includes weekday, keep as is
    if (isoDate.contains("(") && isoDate.contains(")")) return isoDate
    try {
        val date = js("new Date(isoDate)")
        if (js("isNaN(date)") as Boolean) return isoDate
        val months = arrayOf(
            "January","February","March","April","May","June",
            "July","August","September","October","November","December"
        )
        val days = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        val month = months[js("date.getMonth()") as Int]
        val dayOfMonth = js("date.getDate()") as Int
        val year = js("date.getFullYear()") as Int
        val weekday = days[js("date.getDay()") as Int]
        return month + dayOfMonth.toString() + ", " + year.toString() + "(" + weekday + ")"
    } catch (e: dynamic) {
        return isoDate
    }
}

// Current sorting state
private var currentSortField: String? = null
private var currentSortOrder: String = "desc" // desc = newest first for date, Z-A for text

// Multi-select state
private var selectedPurchases = mutableSetOf<Long>()

fun createApp(root: Element) {
    root.innerHTML = """
        <div style="padding: 20px; font-family: Arial, sans-serif;">
            <h1>Automan Car Purchase Management</h1>
            
            <div style="margin-bottom: 20px;">
                <button id="newBtn" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">New+</button>
                <button id="importBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Import CSV</button>
                <button id="rixoBtn" style="padding: 10px 20px; background-color: #6f42c1; color: white; border: none; border-radius: 4px; cursor: pointer; display: none;">Generate Rixo PDF</button>
            </div>
            <style>
                .sort-menu { display: none; }
            </style>
            <div id="sortingBar" style="display: flex; flex-wrap: wrap; gap: 16px; margin: 0 0 16px 0;"></div>
            
            <div id="content">
                <div id="purchaseList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
                    <h2>Purchase List</h2>
                    <div id="purchaseTable" style="margin-top: 20px;">
                        <div style="text-align: center; color: #666; padding: 40px;">
                            Loading purchases...
                        </div>
                    </div>
                </div>
            </div>
        </div>
    """
    
    // Set up button event listeners
    document.getElementById("newBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/add"
    })
    
    document.getElementById("importBtn")?.addEventListener("click", { _: Event ->
        showImportModal()
    })
    
    document.getElementById("rixoBtn")?.addEventListener("click", { _: Event ->
        handleRixoPdfGeneration()
    })
    
    // Render sorting controls under the buttons
    renderSortingBar()
    
    // Load initial data
    loadPurchases()
}

fun updateContent(root: Element) {
    val hash = window.location.hash
    val content = document.getElementById("content")!!
    
    when {
        hash.startsWith("#/add") -> {
            content.innerHTML = createAddFormHTML()
            setupAddFormListeners()
        }
        hash.startsWith("#/edit/") -> {
            val id = hash.substring(7).toLongOrNull()
            if (id != null) {
                showEditForm(id)
            } else {
                showPurchaseList()
            }
        }
        hash.startsWith("#/invoice") -> {
            showInvoicePage()
        }
        else -> {
            showPurchaseList()
        }
    }
}

fun showPurchaseList() {
    window.location.hash = ""
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="purchaseList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>Purchase List</h2>
            <div id="purchaseTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #666; padding: 40px;">
                    Loading purchases...
                </div>
            </div>
        </div>
    """
    loadPurchases()
}

private fun renderSortingBar() {
    val sortingBar = document.getElementById("sortingBar") ?: return

    fun pillHtml(label: String, field: String, optionsHtml: String): String {
        val activeClass = if (currentSortField == field) "box-shadow: 0 0 0 2px #007bff inset;" else ""
        return """
            <div class="sort-pair" style="position: relative; display:inline-block; margin-right:12px;">
                <button class="sort-pill" data-field="$field" style="padding: 8px 14px; background-color: #fff; color: #333; border: 1px solid #ccc; border-radius: 8px; cursor: pointer; $activeClass">$label ▼</button>
                <div class="sort-menu" data-field="$field" style="position: absolute; top: 42px; left: 0; background: #fff; border: 1px solid #ccc; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); padding: 6px 0; display: none; z-index: 10; min-width: 220px;">$optionsHtml</div>
            </div>
        """
    }

    fun optionItem(field: String, order: String, label: String): String {
        return """
            <div class="sort-option" data-field="$field" data-order="$order" style="padding: 8px 12px; cursor: pointer; white-space: nowrap;">$label</div>
        """
    }

    val dateMenu = optionItem("date", "desc", "Newest to Oldest") + optionItem("date", "asc", "Oldest to Newest")
    val carNameMenu = optionItem("carName", "asc", "Ascending (A→Z)") + optionItem("carName", "desc", "Descending (Z→A)")
    val auctionNameMenu = optionItem("auctionName", "asc", "Ascending (A→Z)") + optionItem("auctionName", "desc", "Descending (Z→A)")
    val stockLocationMenu = optionItem("stockLocation", "asc", "Ascending (A→Z)") + optionItem("stockLocation", "desc", "Descending (Z→A)")
    val rixoCompanyMenu = optionItem("rixoCompany", "asc", "Ascending (A→Z)") + optionItem("rixoCompany", "desc", "Descending (Z→A)")
    val clientNameMenu = optionItem("clientName", "asc", "Ascending (A→Z)") + optionItem("clientName", "desc", "Descending (Z→A)")

    sortingBar.innerHTML = """
        ${pillHtml("Date", "date", dateMenu)}
        ${pillHtml("Car Name", "carName", carNameMenu)}
        ${pillHtml("Auction Name", "auctionName", auctionNameMenu)}
        ${pillHtml("Stock Location", "stockLocation", stockLocationMenu)}
        ${pillHtml("Rixo Company", "rixoCompany", rixoCompanyMenu)}
        ${pillHtml("Client Name", "clientName", clientNameMenu)}
    """

    // Ensure all menus are hidden initially (defensive)
    run {
        val menus = document.querySelectorAll(".sort-menu")
        for (i in 0 until menus.length) {
            (menus.item(i) as HTMLElement).style.display = "none"
        }
    }

    // Toggle menus
    val pills = document.querySelectorAll(".sort-pill")
    for (i in 0 until pills.length) {
        val btn = pills.item(i) as HTMLElement
        btn.addEventListener("click", { ev: Event ->
            val field = (ev.currentTarget as HTMLElement).getAttribute("data-field")
            // Close all
            val menus = document.querySelectorAll(".sort-menu")
            for (j in 0 until menus.length) (menus.item(j) as HTMLElement).style.display = "none"
            // Open this
            val menu = document.querySelector(".sort-menu[data-field='" + field + "']") as HTMLElement?
            menu?.style?.setProperty("display", "block")
        })
    }

    // Handle option click
    val options = document.querySelectorAll(".sort-option")
    for (i in 0 until options.length) {
        val item = options.item(i) as HTMLElement
        item.addEventListener("click", { ev: Event ->
            val node = ev.currentTarget as HTMLElement
            val field = node.getAttribute("data-field")
            val order = node.getAttribute("data-order")
            if (field != null && order != null) {
                currentSortField = field
                currentSortOrder = order
                loadPurchases()
            }
            // Close menus
            val menus = document.querySelectorAll(".sort-menu")
            for (j in 0 until menus.length) (menus.item(j) as HTMLElement).style.display = "none"
        })
    }

    // Click outside to close
    document.addEventListener("click", { ev: Event ->
        val target = ev.target as? HTMLElement ?: return@addEventListener
        if (target.closest(".sort-pair") == null) {
            val menus = document.querySelectorAll(".sort-menu")
            for (j in 0 until menus.length) (menus.item(j) as HTMLElement).style.display = "none"
        }
    })
}

fun createAddFormHTML(): String {
    return """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>Add New Purchase</h2>
            <form id="addForm">
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Basic Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Date</label>
                        <div style="position:relative;">
                            <input type="date" id="date" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <span id="dateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                    <div>
                        <label>Lot Number *</label>
                        <input type="text" id="lotNumber" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Chassis *</label>
                        <input type="text" id="chassis" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Car Model Year</label>
                        <input type="text" id="carModelYear" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Brand</label>
                        <input type="text" id="brand" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Car Name</label>
                        <input type="text" id="carName" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Car Specifications</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Grade</label>
                        <input type="text" id="grade" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rank</label>
                        <input type="text" id="rank" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Color</label>
                        <input type="text" id="color" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Displacement</label>
                        <input type="text" id="displacement" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Fuel</label>
                        <input type="text" id="fuel" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Seat</label>
                        <input type="text" id="seat" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Door</label>
                        <input type="text" id="door" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Distance</label>
                        <input type="text" id="distance" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Options</label>
                        <input type="text" id="options" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Auction Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Auction No</label>
                        <input type="text" id="auctionNo" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Auction Name</label>
                        <input type="text" id="auctionName" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Stock Location</label>
                        <input type="text" id="stockLocation" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rixo Company</label>
                        <input type="text" id="rixoCompany" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Client Name</label>
                        <input type="text" id="clientName" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Country</label>
                        <input type="text" id="country" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Rixo Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Rixo Requested</label>
                        <input type="text" id="rixoRequested" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rixo Confirmed</label>
                        <input type="text" id="rixoConfirmed" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rixo Price</label>
                        <input type="text" id="rixoPrice" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>

                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Pricing Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Price</label>
                        <input type="text" id="price" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="e.g., ¥100,000">
                    </div>
                    <div>
                        <label>Auction Fee</label>
                        <input type="text" id="auctionFee" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Recycle Fee</label>
                        <input type="text" id="recycleFee" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Road Tax</label>
                        <input type="text" id="roadTax" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Total Price</label>
                        <input type="text" id="totalPrice" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Payment Date</label>
                        <div style="position:relative;">
                            <input type="date" id="paymentDate" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <span id="paymentDateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Shipment Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Shipment Date</label>
                        <div style="position:relative;">
                            <input type="date" id="shipmentDate" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <span id="shipmentDateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                    <div>
                        <label>B/L No</label>
                        <input type="text" id="blNo" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Vessel No</label>
                        <input type="text" id="vesselNo" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Destination</label>
                        <input type="text" id="destination" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Charges</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Shipment Charges</label>
                        <input type="text" id="shipmentCharges" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Freight</label>
                        <input type="text" id="freight" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Storage Charges</label>
                        <input type="text" id="storageCharges" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Misc Charges</label>
                        <input type="text" id="miscCharges" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Inspection Fee</label>
                        <input type="text" id="inspectionFee" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Commission</label>
                        <input type="text" id="commission" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Repair Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Repair Company</label>
                        <input type="text" id="repairCompany" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Repair Charges</label>
                        <input type="text" id="repairCharges" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <div style="margin-bottom: 20px;">
                    <label>Notes</label>
                    <textarea id="notes" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; min-height: 80px;"></textarea>
                </div>
                <div style="display: flex; gap: 10px;">
                    <button type="submit" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Save</button>
                    <button type="button" id="cancelBtn" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                </div>
            </form>
        </div>
    """
}

fun setupAddFormListeners() {
    document.getElementById("cancelBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    document.getElementById("addForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleAddPurchase()
    })

    // Hook day hints for date inputs
    fun isoToWeekdayLabel(value: String): String {
        if (value.isBlank()) return ""
        try {
            val date = js("new Date(value)")
            val days = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val idx = js("date.getDay()") as Int
            return "(" + days[idx] + ")"
        } catch (e: dynamic) {
            return ""
        }
    }

    (document.getElementById("date") as HTMLInputElement?)?.addEventListener("change", { ev: Event ->
        val v = (ev.target as HTMLInputElement).value
        val hint = document.getElementById("dateDayHint") as HTMLElement?
        hint?.textContent = isoToWeekdayLabel(v)
    })
    (document.getElementById("shipmentDate") as HTMLInputElement?)?.addEventListener("change", { ev: Event ->
        val v = (ev.target as HTMLInputElement).value
        val hint = document.getElementById("shipmentDateDayHint") as HTMLElement?
        hint?.textContent = isoToWeekdayLabel(v)
    })
    (document.getElementById("paymentDate") as HTMLInputElement?)?.addEventListener("change", { ev: Event ->
        val v = (ev.target as HTMLInputElement).value
        val hint = document.getElementById("paymentDateDayHint") as HTMLElement?
        hint?.textContent = isoToWeekdayLabel(v)
    })
}

fun handleAddPurchase() {
    val dateIso = (document.getElementById("date") as HTMLInputElement).value
    val date = formatWithWeekday(dateIso)
    val lotNumber = (document.getElementById("lotNumber") as HTMLInputElement).value
    val chassis = (document.getElementById("chassis") as HTMLInputElement).value
    val carModelYear = (document.getElementById("carModelYear") as HTMLInputElement).value
    val brand = (document.getElementById("brand") as HTMLInputElement).value
    val carName = (document.getElementById("carName") as HTMLInputElement).value
    val grade = (document.getElementById("grade") as HTMLInputElement).value
    val rank = (document.getElementById("rank") as HTMLInputElement).value
    val color = (document.getElementById("color") as HTMLInputElement).value
    val displacement = (document.getElementById("displacement") as HTMLInputElement).value
    val fuel = (document.getElementById("fuel") as HTMLInputElement).value
    val seat = (document.getElementById("seat") as HTMLInputElement).value
    val door = (document.getElementById("door") as HTMLInputElement).value
    val distance = (document.getElementById("distance") as HTMLInputElement).value
    val options = (document.getElementById("options") as HTMLInputElement).value
    val auctionNo = (document.getElementById("auctionNo") as HTMLInputElement).value
    val auctionName = (document.getElementById("auctionName") as HTMLInputElement).value
    val stockLocation = (document.getElementById("stockLocation") as HTMLInputElement).value
    val rixoCompany = (document.getElementById("rixoCompany") as HTMLInputElement).value
    val clientName = (document.getElementById("clientName") as HTMLInputElement).value
    val country = (document.getElementById("country") as HTMLInputElement).value
    val price = (document.getElementById("price") as HTMLInputElement).value
    val auctionFee = (document.getElementById("auctionFee") as HTMLInputElement).value
    val recycleFee = (document.getElementById("recycleFee") as HTMLInputElement).value
    val roadTax = (document.getElementById("roadTax") as HTMLInputElement).value
    val totalPrice = (document.getElementById("totalPrice") as HTMLInputElement).value
    val paymentDate = formatWithWeekday((document.getElementById("paymentDate") as HTMLInputElement).value)
    val rixoRequested = (document.getElementById("rixoRequested") as HTMLInputElement).value
    val rixoConfirmed = (document.getElementById("rixoConfirmed") as HTMLInputElement).value
    val rixoPrice = (document.getElementById("rixoPrice") as HTMLInputElement).value
    val shipmentDate = formatWithWeekday((document.getElementById("shipmentDate") as HTMLInputElement).value)
    val blNo = (document.getElementById("blNo") as HTMLInputElement).value
    val vesselNo = (document.getElementById("vesselNo") as HTMLInputElement).value
    val destination = (document.getElementById("destination") as HTMLInputElement).value
    val shipmentCharges = (document.getElementById("shipmentCharges") as HTMLInputElement).value
    val freight = (document.getElementById("freight") as HTMLInputElement).value
    val storageCharges = (document.getElementById("storageCharges") as HTMLInputElement).value
    val miscCharges = (document.getElementById("miscCharges") as HTMLInputElement).value
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value
    val commission = (document.getElementById("commission") as HTMLInputElement).value
    val repairCompany = (document.getElementById("repairCompany") as HTMLInputElement).value
    val repairCharges = (document.getElementById("repairCharges") as HTMLInputElement).value
    val notes = (document.getElementById("notes") as HTMLTextAreaElement).value
    
    val purchaseData = js("{}")
    purchaseData.date = date
    purchaseData.lotNumber = lotNumber
    purchaseData.chassis = chassis
    purchaseData.carModelYear = carModelYear
    purchaseData.brand = brand
    purchaseData.carName = carName
    purchaseData.grade = grade
    purchaseData.rank = rank
    purchaseData.color = color
    purchaseData.displacement = displacement
    purchaseData.fuel = fuel
    purchaseData.seat = seat
    purchaseData.door = door
    purchaseData.distance = distance
    purchaseData.options = options
    purchaseData.auctionNo = auctionNo
    purchaseData.auctionName = auctionName
    purchaseData.stockLocation = stockLocation
    purchaseData.rixoCompany = rixoCompany
    purchaseData.clientName = clientName
    purchaseData.country = country
    purchaseData.price = price
    purchaseData.auctionFee = auctionFee
    purchaseData.recycleFee = recycleFee
    purchaseData.roadTax = roadTax
    purchaseData.totalPrice = totalPrice
    purchaseData.paymentDate = paymentDate
    purchaseData.rixoRequested = rixoRequested
    purchaseData.rixoConfirmed = rixoConfirmed
    purchaseData.rixoPrice = rixoPrice
    purchaseData.shipmentDate = shipmentDate
    purchaseData.blNo = blNo
    purchaseData.vesselNo = vesselNo
    purchaseData.destination = destination
    purchaseData.shipmentCharges = shipmentCharges
    purchaseData.freight = freight
    purchaseData.storageCharges = storageCharges
    purchaseData.miscCharges = miscCharges
    purchaseData.inspectionFee = inspectionFee
    purchaseData.commission = commission
    purchaseData.repairCompany = repairCompany
    purchaseData.repairCharges = repairCharges
    purchaseData.notes = notes
    
    // Call API to create purchase
    val requestInit = js("{}")
    requestInit.method = "POST"
      val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(purchaseData)
    
    window.fetch("http://localhost:8083/api/purchases", requestInit).then { response ->
        if (response.ok) {
            showMessage("Purchase created successfully!", "success")
            showPurchaseList()
        } else {
            response.text().then { errorText ->
                showMessage("Failed to create purchase: $errorText", "error")
            }
        }
    }.catch { error ->
        showMessage("Failed to create purchase: ${error.message}", "error")
    }
}

fun showEditForm(id: Long) {
    // First fetch the purchase data
    window.fetch("http://localhost:8083/api/purchases/$id").then { response ->
        if (response.ok) {
            response.json().then { purchaseData ->
                showEditFormWithData(purchaseData)
            }
        } else {
            showMessage("Failed to load purchase data", "error")
            showPurchaseList()
        }
    }.catch { error ->
        showMessage("Failed to load purchase data: ${error.message}", "error")
        showPurchaseList()
    }
}

fun showEditFormWithData(purchaseData: dynamic) {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>Edit Purchase</h2>
            <form id="editForm">
                <input type="hidden" id="editId" value="${purchaseData.id}">
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Basic Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Date</label>
                        <div style="position:relative;">
                            <input type="date" id="editDate" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="${purchaseData.date ?: ""}">
                            <span id="editDateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                    <div>
                        <label>Lot Number *</label>
                        <input type="text" id="editLotNumber" value="${purchaseData.lotNumber}" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Chassis *</label>
                        <input type="text" id="editChassis" value="${purchaseData.chassis}" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Car Model Year</label>
                        <input type="text" id="editCarModelYear" value="${purchaseData.carModelYear ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Brand</label>
                        <input type="text" id="editBrand" value="${purchaseData.brand ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Car Name</label>
                        <input type="text" id="editCarName" value="${purchaseData.carName ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Car Specifications</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Grade</label>
                        <input type="text" id="editGrade" value="${purchaseData.grade ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rank</label>
                        <input type="text" id="editRank" value="${purchaseData.rank ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Color</label>
                        <input type="text" id="editColor" value="${purchaseData.color ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Displacement</label>
                        <input type="text" id="editDisplacement" value="${purchaseData.displacement ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Fuel</label>
                        <input type="text" id="editFuel" value="${purchaseData.fuel ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Seat</label>
                        <input type="text" id="editSeat" value="${purchaseData.seat ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Door</label>
                        <input type="text" id="editDoor" value="${purchaseData.door ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Distance</label>
                        <input type="text" id="editDistance" value="${purchaseData.distance ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Options</label>
                        <input type="text" id="editOptions" value="${purchaseData.options ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Auction Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Auction No</label>
                        <input type="text" id="editAuctionNo" value="${purchaseData.auctionNo ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Auction Name</label>
                        <input type="text" id="editAuctionName" value="${purchaseData.auctionName ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Stock Location</label>
                        <input type="text" id="editStockLocation" value="${purchaseData.stockLocation ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rixo Company</label>
                        <input type="text" id="editRixoCompany" value="${purchaseData.rixoCompany ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Client Name</label>
                        <input type="text" id="editClientName" value="${purchaseData.clientName ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Country</label>
                        <input type="text" id="editCountry" value="${purchaseData.country ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Rixo Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Rixo Requested</label>
                        <input type="text" id="editRixoRequested" value="${purchaseData.rixoRequested ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rixo Confirmed</label>
                        <input type="text" id="editRixoConfirmed" value="${purchaseData.rixoConfirmed ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Rixo Price</label>
                        <input type="text" id="editRixoPrice" value="${purchaseData.rixoPrice ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>

                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Pricing Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Price</label>
                        <input type="text" id="editPrice" value="${purchaseData.price ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Auction Fee</label>
                        <input type="text" id="editAuctionFee" value="${purchaseData.auctionFee ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Recycle Fee</label>
                        <input type="text" id="editRecycleFee" value="${purchaseData.recycleFee ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Road Tax</label>
                        <input type="text" id="editRoadTax" value="${purchaseData.roadTax ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Total Price</label>
                        <input type="text" id="editTotalPrice" value="${purchaseData.totalPrice ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Payment Date</label>
                        <div style="position:relative;">
                            <input type="date" id="editPaymentDate" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="${purchaseData.paymentDate ?: ""}">
                            <span id="editPaymentDateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Shipment Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Shipment Date</label>
                        <div style="position:relative;">
                            <input type="date" id="editShipmentDate" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="${purchaseData.shipmentDate ?: ""}">
                            <span id="editShipmentDateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                    <div>
                        <label>B/L No</label>
                        <input type="text" id="editBlNo" value="${purchaseData.blNo ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Vessel No</label>
                        <input type="text" id="editVesselNo" value="${purchaseData.vesselNo ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Destination</label>
                        <input type="text" id="editDestination" value="${purchaseData.destination ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Charges</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Shipment Charges</label>
                        <input type="text" id="editShipmentCharges" value="${purchaseData.shipmentCharges ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Freight</label>
                        <input type="text" id="editFreight" value="${purchaseData.freight ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Storage Charges</label>
                        <input type="text" id="editStorageCharges" value="${purchaseData.storageCharges ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Misc Charges</label>
                        <input type="text" id="editMiscCharges" value="${purchaseData.miscCharges ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Inspection Fee</label>
                        <input type="text" id="editInspectionFee" value="${purchaseData.inspectionFee ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Commission</label>
                        <input type="text" id="editCommission" value="${purchaseData.commission ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Repair Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Repair Company</label>
                        <input type="text" id="editRepairCompany" value="${purchaseData.repairCompany ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Repair Charges</label>
                        <input type="text" id="editRepairCharges" value="${purchaseData.repairCharges ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <div style="margin-bottom: 20px;">
                    <label>Notes</label>
                    <textarea id="editNotes" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; min-height: 80px;">${purchaseData.notes ?: ""}</textarea>
                </div>
                <div style="display: flex; gap: 10px;">
                    <button type="submit" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Update</button>
                    <button type="button" id="deleteBtn" style="padding: 10px 20px; background-color: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">Delete</button>
                    <button type="button" id="editCancelBtn" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                </div>
            </form>
        </div>
    """
    
    setupEditFormListeners()
}

fun setupEditFormListeners() {
    document.getElementById("editCancelBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    document.getElementById("editForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleEditPurchase()
    })

    document.getElementById("deleteBtn")?.addEventListener("click", { _: Event ->
        val id = (document.getElementById("editId") as HTMLInputElement).value.toLongOrNull()
        if (id != null) {
            deletePurchase(id)
        }
    })

    // Hook day hints for date inputs (edit form)
    fun isoToWeekdayLabel(value: String): String {
        if (value.isBlank()) return ""
        try {
            val date = js("new Date(value)")
            val days = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            val idx = js("date.getDay()") as Int
            return "(" + days[idx] + ")"
        } catch (e: dynamic) {
            return ""
        }
    }

    (document.getElementById("editDate") as HTMLInputElement?)?.addEventListener("change", { ev: Event ->
        val v = (ev.target as HTMLInputElement).value
        val hint = document.getElementById("editDateDayHint") as HTMLElement?
        hint?.textContent = isoToWeekdayLabel(v)
    })
    (document.getElementById("editShipmentDate") as HTMLInputElement?)?.addEventListener("change", { ev: Event ->
        val v = (ev.target as HTMLInputElement).value
        val hint = document.getElementById("editShipmentDateDayHint") as HTMLElement?
        hint?.textContent = isoToWeekdayLabel(v)
    })
    (document.getElementById("editPaymentDate") as HTMLInputElement?)?.addEventListener("change", { ev: Event ->
        val v = (ev.target as HTMLInputElement).value
        val hint = document.getElementById("editPaymentDateDayHint") as HTMLElement?
        hint?.textContent = isoToWeekdayLabel(v)
    })
}

fun handleEditPurchase() {
    val id = (document.getElementById("editId") as HTMLInputElement).value.toLong()
    val date = formatWithWeekday((document.getElementById("editDate") as HTMLInputElement).value)
    val lotNumber = (document.getElementById("editLotNumber") as HTMLInputElement).value
    val chassis = (document.getElementById("editChassis") as HTMLInputElement).value
    val carModelYear = (document.getElementById("editCarModelYear") as HTMLInputElement).value
    val brand = (document.getElementById("editBrand") as HTMLInputElement).value
    val carName = (document.getElementById("editCarName") as HTMLInputElement).value
    val grade = (document.getElementById("editGrade") as HTMLInputElement).value
    val rank = (document.getElementById("editRank") as HTMLInputElement).value
    val color = (document.getElementById("editColor") as HTMLInputElement).value
    val displacement = (document.getElementById("editDisplacement") as HTMLInputElement).value
    val fuel = (document.getElementById("editFuel") as HTMLInputElement).value
    val seat = (document.getElementById("editSeat") as HTMLInputElement).value
    val door = (document.getElementById("editDoor") as HTMLInputElement).value
    val distance = (document.getElementById("editDistance") as HTMLInputElement).value
    val options = (document.getElementById("editOptions") as HTMLInputElement).value
    val auctionNo = (document.getElementById("editAuctionNo") as HTMLInputElement).value
    val auctionName = (document.getElementById("editAuctionName") as HTMLInputElement).value
    val stockLocation = (document.getElementById("editStockLocation") as HTMLInputElement).value
    val rixoCompany = (document.getElementById("editRixoCompany") as HTMLInputElement).value
    val clientName = (document.getElementById("editClientName") as HTMLInputElement).value
    val country = (document.getElementById("editCountry") as HTMLInputElement).value
    val price = (document.getElementById("editPrice") as HTMLInputElement).value
    val auctionFee = (document.getElementById("editAuctionFee") as HTMLInputElement).value
    val recycleFee = (document.getElementById("editRecycleFee") as HTMLInputElement).value
    val roadTax = (document.getElementById("editRoadTax") as HTMLInputElement).value
    val totalPrice = (document.getElementById("editTotalPrice") as HTMLInputElement).value
    val paymentDate = formatWithWeekday((document.getElementById("editPaymentDate") as HTMLInputElement).value)
    val rixoRequested = (document.getElementById("editRixoRequested") as HTMLInputElement).value
    val rixoConfirmed = (document.getElementById("editRixoConfirmed") as HTMLInputElement).value
    val rixoPrice = (document.getElementById("editRixoPrice") as HTMLInputElement).value
    val shipmentDate = formatWithWeekday((document.getElementById("editShipmentDate") as HTMLInputElement).value)
    val blNo = (document.getElementById("editBlNo") as HTMLInputElement).value
    val vesselNo = (document.getElementById("editVesselNo") as HTMLInputElement).value
    val destination = (document.getElementById("editDestination") as HTMLInputElement).value
    val shipmentCharges = (document.getElementById("editShipmentCharges") as HTMLInputElement).value
    val freight = (document.getElementById("editFreight") as HTMLInputElement).value
    val storageCharges = (document.getElementById("editStorageCharges") as HTMLInputElement).value
    val miscCharges = (document.getElementById("editMiscCharges") as HTMLInputElement).value
    val inspectionFee = (document.getElementById("editInspectionFee") as HTMLInputElement).value
    val commission = (document.getElementById("editCommission") as HTMLInputElement).value
    val repairCompany = (document.getElementById("editRepairCompany") as HTMLInputElement).value
    val repairCharges = (document.getElementById("editRepairCharges") as HTMLInputElement).value
    val notes = (document.getElementById("editNotes") as HTMLTextAreaElement).value
    
    val purchaseData = js("{}")
    purchaseData.date = date
    purchaseData.lotNumber = lotNumber
    purchaseData.chassis = chassis
    purchaseData.carModelYear = carModelYear
    purchaseData.brand = brand
    purchaseData.carName = carName
    purchaseData.grade = grade
    purchaseData.rank = rank
    purchaseData.color = color
    purchaseData.displacement = displacement
    purchaseData.fuel = fuel
    purchaseData.seat = seat
    purchaseData.door = door
    purchaseData.distance = distance
    purchaseData.options = options
    purchaseData.auctionNo = auctionNo
    purchaseData.auctionName = auctionName
    purchaseData.stockLocation = stockLocation
    purchaseData.rixoCompany = rixoCompany
    purchaseData.clientName = clientName
    purchaseData.country = country
    purchaseData.price = price
    purchaseData.auctionFee = auctionFee
    purchaseData.recycleFee = recycleFee
    purchaseData.roadTax = roadTax
    purchaseData.totalPrice = totalPrice
    purchaseData.paymentDate = paymentDate
    purchaseData.rixoRequested = rixoRequested
    purchaseData.rixoConfirmed = rixoConfirmed
    purchaseData.rixoPrice = rixoPrice
    purchaseData.shipmentDate = shipmentDate
    purchaseData.blNo = blNo
    purchaseData.vesselNo = vesselNo
    purchaseData.destination = destination
    purchaseData.shipmentCharges = shipmentCharges
    purchaseData.freight = freight
    purchaseData.storageCharges = storageCharges
    purchaseData.miscCharges = miscCharges
    purchaseData.inspectionFee = inspectionFee
    purchaseData.commission = commission
    purchaseData.repairCompany = repairCompany
    purchaseData.repairCharges = repairCharges
    purchaseData.notes = notes
    
    console.log("Sending update data: ${JSON.stringify(purchaseData)}")
    console.log("Request URL: http://localhost:8083/api/purchases/$id")
    
    // Call API to update purchase
    val requestInit = js("{}")
    requestInit.method = "PUT"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(purchaseData)
    
    console.log("Request headers:", headers)
    console.log("Request body:", requestInit.body)
    
    window.fetch("http://localhost:8083/api/purchases/$id", requestInit).then { response ->
        if (response.ok) {
            showMessage("Purchase updated successfully!", "success")
            showPurchaseList()
        } else {
            response.text().then { errorText ->
                console.log("Update error response: $errorText")
                showMessage("Failed to update purchase: $errorText", "error")
            }
        }
    }.catch { error ->
        console.log("Update error: ${error.message}")
        showMessage("Failed to update purchase: ${error.message}", "error")
    }
}

fun showImportModal() {
    // Create modal overlay
    val modal = document.createElement("div")
    modal.id = "importModal"
    modal.setAttribute("style", "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000;")
    
    modal.innerHTML = """
        <div style="background-color: white; padding: 30px; border-radius: 8px; min-width: 400px; max-width: 600px;">
            <h2>Import CSV File</h2>
            <p>Select a CSV file to import purchase data:</p>
            <input type="file" id="csvFile" accept=".csv" style="margin: 20px 0;">
            <div id="fileInfo" style="margin: 20px 0;"></div>
            <div style="display: flex; gap: 10px; margin-top: 20px; justify-content: flex-end;">
                <button id="cancelImportBtn" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                <button id="modalImportBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;" disabled>Import</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Set up event listeners
    document.getElementById("csvFile")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        if (target.files?.length != 0) {
            val file = target.files!!.item(0)
            if (file != null) {
                document.getElementById("fileInfo")!!.innerHTML = "Selected file: ${file.name}"
                document.getElementById("modalImportBtn")?.removeAttribute("disabled")
                
                // Store file for import
                js("window.selectedFile = file")
            }
        }
    })
    
    document.getElementById("cancelImportBtn")?.addEventListener("click", { _: Event ->
        document.body?.removeChild(modal)
    })
    
    document.getElementById("modalImportBtn")?.addEventListener("click", { _: Event ->
        handleImport()
        document.body?.removeChild(modal)
    })
}

fun handleImport() {
    val file = js("window.selectedFile") as File?
    if (file == null) {
        showMessage("Please select a file first", "error")
        return
    }
    
    console.log("Starting import for file: ${file.name}, size: ${file.size}")
    
    val formData = js("new FormData()")
    formData.append("file", file)
    
    val requestInit = js("{}")
    requestInit.method = "POST"
    // Don't set Content-Type for FormData - browser sets it automatically with boundary
    requestInit.body = formData
    
    console.log("Sending request to: http://localhost:8083/api/purchases/import")
    
    window.fetch("http://localhost:8083/api/purchases/import", requestInit).then { response ->
        console.log("Import response status: ${response.status}")
        if (response.ok) {
            response.json().then { result ->
                console.log("Import result:", result)
                val importedCount = js("result.importedCount")
                val duplicateCount = js("result.duplicateCount")
                val errorCount = js("result.errorCount")
                val message = js("result.message")
                
                if (duplicateCount > 0 || errorCount > 0) {
                    showMessage("$message (Imported: $importedCount, Duplicates: $duplicateCount, Errors: $errorCount)", "warning")
                } else {
                    showMessage("Successfully imported $importedCount purchases!", "success")
                }
                
                loadPurchases()
            }
        } else {
            response.text().then { errorText ->
                console.log("Import error response:", errorText)
                showMessage("Import failed: $errorText", "error")
            }
        }
    }.catch { error ->
        console.log("Import fetch error:", error)
        showMessage("Import failed: ${error.message}", "error")
    }
}

fun loadPurchases() {
    console.log("loadPurchases function called")
    val url = if (currentSortField != null) {
        "http://localhost:8083/api/purchases/sort?field=" + currentSortField + "&order=" + currentSortOrder
    } else {
        "http://localhost:8083/api/purchases"
    }
    window.fetch(url).then { response ->
        console.log("API response received: $response")
        if (response.ok) {
            response.json().then { purchases ->
                console.log("Purchases data received: $purchases")
                console.log("First purchase ID:", if (js("purchases.length > 0")) js("purchases[0].id") else "No purchases")
                displayPurchases(purchases)
            }
        } else {
            console.log("API response not ok: ${response.status}")
            showMessage("Failed to load purchases", "error")
        }
    }.catch { error ->
        val errMsg = try {
            val m = js("error && (error.message || (error.toString && error.toString()))")
            (m?.toString() ?: "").ifBlank { "Unexpected error" }
        } catch (e: dynamic) {
            "Unexpected error"
        }
        console.log("API call failed:", error)
        showMessage("Failed to load purchases: ${'$'}errMsg", "error")
    }
}

fun displayPurchases(purchases: dynamic) {
    val table = document.getElementById("purchaseTable")!!
    
    if (js("purchases.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click 'New+' to add a purchase or 'Import CSV' to import data.
            </div>
        """
        return
    }
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <table style="width: 100%; border-collapse: collapse;">
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 40px;">
                        <input type="checkbox" id="selectAll" style="transform: scale(1.2);">
                    </th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Date</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Lot Number</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Chassis</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Car Name</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Auction Name</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Stock Location</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Client Name</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Rixo Company</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Price</th>
                </tr>
            </thead>
            <tbody>
    """)
    
    val purchasesArray = purchases as Array<dynamic>
    for (i in 0 until purchasesArray.size) {
        val purchase = purchasesArray[i]
        tableHTML.append("""
            <tr style="border-bottom: 1px solid #f0f0f0;">
                <td style="padding: 12px; text-align: center;">
                    <input type="checkbox" class="purchase-checkbox" data-id="${purchase.id}" style="transform: scale(1.2);">
                </td>
                <td style="padding: 8px 12px;">
                    <button class="edit-btn" data-id="${purchase.id}" aria-label="Edit" title="Edit"
                            style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                </td>
                <td style="padding: 12px;">${formatWithWeekday(purchase.date ?: "")}</td>
                <td style="padding: 12px;">${purchase.lotNumber ?: ""}</td>
                <td style="padding: 12px;">${purchase.chassis ?: ""}</td>
                <td style="padding: 12px;">${purchase.carName ?: ""}</td>
                <td style="padding: 12px;">${purchase.auctionName ?: ""}</td>
                <td style="padding: 12px;">${purchase.stockLocation ?: ""}</td>
                <td style="padding: 12px;">${purchase.clientName ?: ""}</td>
                <td style="padding: 12px;">${purchase.rixoCompany ?: ""}</td>
                <td style="padding: 12px;">${purchase.price ?: ""}</td>
            </tr>
        """)
    }
    
    tableHTML.append("""
            </tbody>
        </table>
    """)
    
    table.innerHTML = tableHTML.toString()
    
    // Add event listeners for edit and delete buttons
    val editButtons = document.querySelectorAll(".edit-btn")
    for (i in 0 until editButtons.length) {
        val button = editButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val btn = event.currentTarget as HTMLElement
            val id = btn.getAttribute("data-id")
            window.location.hash = "#/edit/$id"
        })
    }
    
    val deleteButtons = document.querySelectorAll(".delete-btn")
    for (i in 0 until deleteButtons.length) {
        val button = deleteButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val target = event.target as HTMLElement
            val id = target.getAttribute("data-id")?.toLongOrNull()
            if (id != null) {
                deletePurchase(id)
            }
        })
    }
    
    // Add event listeners for checkboxes
    setupCheckboxListeners()
}

fun setupCheckboxListeners() {
    // Select All checkbox
    document.getElementById("selectAll")?.addEventListener("change", { event ->
        val target = event.target as HTMLInputElement
        val isChecked = target.checked
        
        val checkboxes = document.querySelectorAll(".purchase-checkbox")
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.checked = isChecked
            
            val id = checkbox.getAttribute("data-id")?.toLongOrNull()
            if (id != null) {
                if (isChecked) {
                    selectedPurchases.add(id)
                    console.log("Added ID to selection:", id)
                } else {
                    selectedPurchases.remove(id)
                    console.log("Removed ID from selection:", id)
                }
            }
        }
        
        updateRixoButtonVisibility()
    })
    
    // Individual checkboxes
    val checkboxes = document.querySelectorAll(".purchase-checkbox")
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        checkbox.addEventListener("change", { event ->
            val target = event.target as HTMLInputElement
            val id = target.getAttribute("data-id")?.toLongOrNull()
            
            if (id != null) {
                if (target.checked) {
                    selectedPurchases.add(id)
                    console.log("Added ID to selection:", id)
                } else {
                    selectedPurchases.remove(id)
                    console.log("Removed ID from selection:", id)
                }
            }
            
            updateRixoButtonVisibility()
            updateSelectAllCheckbox()
        })
    }
}

fun updateRixoButtonVisibility() {
    val rixoBtn = document.getElementById("rixoBtn")
    if (selectedPurchases.isNotEmpty()) {
        rixoBtn?.setAttribute("style", "display: inline-block; padding: 10px 20px; background-color: #6f42c1; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;")
        rixoBtn?.textContent = "Generate Rixo PDF (${selectedPurchases.size} selected)"
    } else {
        rixoBtn?.setAttribute("style", "display: none;")
    }
}

fun updateSelectAllCheckbox() {
    val selectAllCheckbox = document.getElementById("selectAll") as HTMLInputElement?
    val checkboxes = document.querySelectorAll(".purchase-checkbox")
    
    if (checkboxes.length == 0) {
        selectAllCheckbox?.checked = false
        return
    }
    
    val checkedCount = selectedPurchases.size
    selectAllCheckbox?.checked = checkedCount == checkboxes.length
    selectAllCheckbox?.indeterminate = checkedCount > 0 && checkedCount < checkboxes.length
}

fun handleRixoPdfGeneration() {
    if (selectedPurchases.isEmpty()) {
        showMessage("Please select at least one purchase", "error")
        return
    }
    
    // Check for missing Rixo-related data
    checkMissingRixoData()
}

fun checkMissingRixoData() {
    // Get selected purchases data
    val selectedIds = selectedPurchases.toList()
    val missingDataPurchases = mutableListOf<dynamic>()
    
    // Check each selected purchase for missing Rixo data
    window.fetch("http://localhost:8083/api/purchases").then { response ->
        if (response.ok) {
            response.json().then { allPurchases ->
                val purchasesArray = allPurchases as Array<dynamic>
                for (purchase in purchasesArray) {
                    val id = js("purchase.id").toString().toLongOrNull()
                    if (id != null && selectedPurchases.contains(id)) {
                        // Check for missing Rixo-related fields
                        val missingFields = mutableListOf<String>()
                        
                        if (js("purchase.rixoCompany").toString().isNullOrEmpty()) missingFields.add("rixoCompany")
                        if (js("purchase.rixoRequested").toString().isNullOrEmpty()) missingFields.add("rixoRequested")
                        if (js("purchase.rixoConfirmed").toString().isNullOrEmpty()) missingFields.add("rixoConfirmed")
                        if (js("purchase.rixoPrice").toString().isNullOrEmpty()) missingFields.add("rixoPrice")
                        if (js("purchase.clientName").toString().isNullOrEmpty()) missingFields.add("clientName")
                        if (js("purchase.carName").toString().isNullOrEmpty()) missingFields.add("carName")
                        if (js("purchase.carModelYear").toString().isNullOrEmpty()) missingFields.add("carModelYear")
                        
                        if (missingFields.isNotEmpty()) {
                            js("purchase.missingFields = missingFields")
                            missingDataPurchases.add(purchase)
                        }
                    }
                }
                
                if (missingDataPurchases.isNotEmpty()) {
                    showMissingDataModal(missingDataPurchases)
                } else {
                    navigateToInvoicePage(selectedIds)
                }
            }
        }
    }
}

fun showMissingDataModal(purchases: List<dynamic>) {
    val modal = document.createElement("div")
    modal.id = "missingDataModal"
    modal.setAttribute("style", "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000;")
    
    val modalContent = StringBuilder()
    modalContent.append("""
        <div style="background-color: white; padding: 30px; border-radius: 8px; min-width: 600px; max-width: 800px; max-height: 80vh; overflow-y: auto;">
            <h2>Fill Missing Rixo Data</h2>
            <p>Some selected purchases are missing Rixo-related information. Please fill in the missing data:</p>
    """)
    
    for (purchase in purchases) {
        val id = js("purchase.id")
        val lotNumber = js("purchase.lotNumber")
        val chasis = js("purchase.chassis")
        val missingFields = js("purchase.missingFields") as List<String>
        
        modalContent.append("""
            <div style="border: 1px solid #ddd; padding: 15px; margin: 10px 0; border-radius: 4px;">
                <h4>Purchase: Lot ${lotNumber} - ${chasis}</h4>
        """)
        
        for (field in missingFields) {
            val fieldLabel = when (field) {
                
                "carName" -> "Car Name"
                "carModelYear" -> "Car Model Year"
                "clientName" -> "Client Name"
                "rixoCompany" -> "Rixo Company"
                "rixoRequested" -> "Rixo Requested"
                "rixoConfirmed" -> "Rixo Confirmed"
                "rixoPrice" -> "Rixo Price"


                else -> field
            }
            
            modalContent.append("""
                <div style="margin: 10px 0;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">${fieldLabel}:</label>
                    <input type="text" id="missing_${id}_${field}" style="width: 100%; padding: 8px; border: 1px solid #ccc; border-radius: 4px;" placeholder="Enter ${fieldLabel}">
                </div>
            """)
        }
        
        modalContent.append("</div>")
    }
    
    modalContent.append("""
            <div style="display: flex; gap: 10px; margin-top: 20px; justify-content: flex-end;">
                <button id="cancelMissingDataBtn" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                <button id="generatePdfBtn" style="padding: 10px 20px; background-color: #6f42c1; color: white; border: none; border-radius: 4px; cursor: pointer;">Generate PDF</button>
            </div>
        </div>
    """)
    
    modal.innerHTML = modalContent.toString()
    document.body?.appendChild(modal)
    
    // Event listeners
    document.getElementById("cancelMissingDataBtn")?.addEventListener("click", { _: Event ->
        document.body?.removeChild(modal)
    })
    
    document.getElementById("generatePdfBtn")?.addEventListener("click", { _: Event ->
        collectMissingDataAndGeneratePdf(purchases)
        document.body?.removeChild(modal)
    })
}

fun collectMissingDataAndGeneratePdf(purchases: List<dynamic>) {
    val updatedPurchases = mutableListOf<dynamic>()
    
    for (purchase in purchases) {
        val id = js("purchase.id")
        val missingFields = js("purchase.missingFields") as List<String>
        
        // Create a copy of the purchase object
        val updatedPurchase = js("Object.assign({}, purchase)")
        
        for (field in missingFields) {
            val inputId = "missing_${id}_${field}"
            val input = document.getElementById(inputId) as HTMLInputElement?
            if (input != null && input.value.isNotEmpty()) {
                when (field) {
                    "rixoCompany" -> js("updatedPurchase.rixoCompany = input.value")
                    "rixoRequested" -> js("updatedPurchase.rixoRequested = input.value")
                    "rixoConfirmed" -> js("updatedPurchase.rixoConfirmed = input.value")
                    "rixoPrice" -> js("updatedPurchase.rixoPrice = input.value")
                    "clientName" -> js("updatedPurchase.clientName = input.value")
                    "carName" -> js("updatedPurchase.carName = input.value")
                    "carModelYear" -> js("updatedPurchase.carModelYear = input.value")
                }
            }
        }
        
        updatedPurchases.add(updatedPurchase)
    }
    
    // Update purchases in backend and then generate PDF
    updatePurchasesAndGeneratePdf(updatedPurchases)
}

fun updatePurchasesAndGeneratePdf(purchases: List<dynamic>) {
    // Update each purchase in the backend
    val updatePromises = purchases.map { purchase ->
        val id = js("purchase.id")
        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(purchase)
        
        window.fetch("http://localhost:8083/api/purchases/${id}", requestInit)
    }
    
    // Wait for all updates to complete, then generate PDF
    val jsPromises = js("[]")
    updatePromises.forEach { promise ->
        jsPromises.push(promise)
    }
    js("Promise.all(jsPromises)").then { _ ->
        navigateToInvoicePage(selectedPurchases.toList())
    }
}

fun generateRixoPdf(selectedIds: List<Long>) {
    console.log("Selected IDs for PDF generation:", selectedIds)
    navigateToInvoicePage(selectedIds)
}

// Navigate to invoice page with selected IDs stored globally
fun navigateToInvoicePage(selectedIds: List<Long>) {
    val jsArray = js("[]")
    selectedIds.forEach { id -> jsArray.push(id.toInt()) }
    js("window.invoiceSelectedIds = jsArray")
    window.location.hash = "#/invoice"
}

// Render invoice page content
fun showInvoicePage() {
    val content = document.getElementById("content") ?: return
    content.innerHTML = """
        <div class="invoice-shell" style="width:100%; min-height: calc(100vh - 140px); display:flex; align-items:flex-start; justify-content:center; padding: 32px 16px; box-sizing:border-box;">
            <style>
                .invoice-card { 
                    width: 100%; max-width: 900px; 
                    border-radius: 16px; 
                    padding: 28px; 
                    background: rgba(255,255,255,0.75);
                    box-shadow: 0 10px 30px rgba(0,0,0,0.10);
                    border: 1px solid rgba(229,231,235,0.6);
                    backdrop-filter: blur(8px);
                }
                .invoice-title { margin: 0; color: #111827; font-size: 28px; text-align: center; letter-spacing: .2px; }
                .invoice-sub { color:#6b7280; margin: 10px 0 26px 0; text-align:center; }
                .section { border: 1px solid rgba(229,231,235,0.9); border-radius: 12px; padding: 18px; background: rgba(249,250,251,0.8); }
                .section h3 { margin:0 0 14px 0; color:#111827; font-size:16px; text-align:left; }
                .grid-2 { display:grid; grid-template-columns: 1fr 1fr; gap: 24px; }
                .grid-1 { display:grid; grid-template-columns: 1fr; gap: 16px; }
                .grid-2-inner { display:grid; grid-template-columns: 1fr 1fr; gap: 16px; }
                .field label { display:block; margin-bottom: 6px; font-weight: 600; color:#374151; }
                .input, .textarea { width:100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 10px; background: rgba(255,255,255,0.9); transition: box-shadow .2s, border-color .2s, background .2s; color:#111827; }
                .input::placeholder, .textarea::placeholder { color:#9ca3af; }
                .input:hover, .textarea:hover { border-color:#a5b4fc; }
                .input:focus, .textarea:focus { outline:none; border-color:#6d28d9; box-shadow: 0 0 0 4px rgba(109,40,217,0.15); background:#fff; }
                .error-text { margin-top:6px; font-size: 12px; color:#b91c1c; min-height: 16px; }
                .actions { display:flex; gap: 12px; justify-content:center; margin-top:22px; }
                .btn { padding: 10px 18px; border: none; border-radius: 10px; cursor: pointer; transition: transform .05s ease, box-shadow .2s ease; }
                .btn:active { transform: translateY(1px); }
                .btn-primary { background: linear-gradient(135deg, #6d28d9, #7c3aed); color:white; box-shadow: 0 6px 16px rgba(124,58,237,0.35); }
                .btn-primary:hover { box-shadow: 0 8px 20px rgba(124,58,237,0.45); }
                .btn-secondary { background:#6b7280; color:white; }
                @media (max-width: 720px) { .grid-2, .grid-2-inner { grid-template-columns: 1fr; } }
            </style>
            <div class="invoice-card">
                <h2 class="invoice-title">Invoice Information</h2>
                <p class="invoice-sub">Please fill in the invoice details for the PDF generation:</p>
                <form id="invoiceForm" novalidate>
                    <div class="section" style="margin-bottom:22px;">
                        <h3>Add Additional Car</h3>
                        <div class="grid-2-inner">
                            <div class="field">
                                <label for="addChassis">Chassis No</label>
                                <input class="input" type="text" id="addChassis" placeholder="Enter chassis number to add" />
                                <div id="addChassisError" class="error-text"></div>
                            </div>
                            <div class="field" style="align-self:end;">
                                <button type="button" id="addByChassisBtn" class="btn btn-primary" style="width:100%;">Add by Chassis</button>
                            </div>
                        </div>
                        <div id="selectedCarsPreview" style="margin-top:10px;"></div>
                    </div>
                    <div class="grid-2">
                        <div class="section">
                            <h3>Invoice Details</h3>
                            <div class="grid-1">
                                <div class="field">
                                    <label for="invoiceNo">Invoice No</label>
                                    <input class="input" type="text" id="invoiceNo" placeholder="Enter invoice number" aria-required="true" data-required="true" />
                                    <div id="invoiceNoError" class="error-text"></div>
                                </div>
                                <div class="field">
                                    <label for="lcNo">L/C No</label>
                                    <input class="input" type="text" id="lcNo" placeholder="Enter L/C number" />
                                    <div id="lcNoError" class="error-text"></div>
                                </div>
                            </div>
                        </div>
                        <div class="section">
                            <h3>Shipping</h3>
                            <div class="grid-1">
                                <div class="field">
                                    <label for="vessel">Vessel</label>
                                    <input class="input" type="text" id="vessel" placeholder="Enter vessel name" aria-required="true" data-required="true" />
                                    <div id="vesselError" class="error-text"></div>
                                </div>
                                <div class="field">
                                    <label for="sailDate">Sail Date</label>
                                    <input class="input" type="text" id="sailDate" placeholder="Enter sail date" aria-required="true" data-required="true" />
                                    <div id="sailDateError" class="error-text"></div>
                                </div>
                                <div class="grid-2-inner">
                                    <div class="field">
                                        <label for="from">From</label>
                                        <input class="input" type="text" id="from" placeholder="Origin port/location" aria-required="true" data-required="true" />
                                        <div id="fromError" class="error-text"></div>
                                    </div>
                                    <div class="field">
                                        <label for="to">To</label>
                                        <input class="input" type="text" id="to" placeholder="Destination port/location" aria-required="true" data-required="true" />
                                        <div id="toError" class="error-text"></div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="section" style="margin-top:22px;">
                        <h3>Consignee</h3>
                        <textarea class="textarea" id="consignee" placeholder="Enter consignee information" aria-required="true" data-required="true" style="min-height: 120px;"></textarea>
                        <div id="consigneeError" class="error-text"></div>
                    </div>
                    <div class="actions">
                        <button type="button" id="cancelInvoicePageBtn" class="btn btn-secondary">Cancel</button>
                        <button type="submit" id="generateInvoicePdfPageBtn" class="btn btn-primary">Generate PDF</button>
                    </div>
                </form>
            </div>
        </div>
    """

    // Wire up events
    document.getElementById("cancelInvoicePageBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })

    document.getElementById("invoiceForm")?.addEventListener("submit", { ev: Event ->
        ev.preventDefault()
        // Validate before submit
        val isValid = validateInvoiceForm()
        if (!isValid) {
            showMessage("Please fix the highlighted fields", "warning")
            return@addEventListener
        }
        val idsList = selectedPurchases.toList()
        collectInvoiceDataAndGeneratePdf(idsList)
    })

    // Add-by-chasis handler and preview renderer
    document.getElementById("addByChassisBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("addChassis") as HTMLInputElement?
        val value = input?.value?.trim() ?: ""
        val errorEl = document.getElementById("addChassisError") as HTMLElement?
        if (value.isEmpty()) {
            errorEl?.textContent = "Please enter a chassis number"
            return@addEventListener
        } else {
            errorEl?.textContent = ""
        }
        // Fetch purchases and find by chassis (case-insensitive contains or exact match)
        window.fetch("http://localhost:8083/api/purchases").then { response ->
            if (response.ok) {
                response.json().then { allPurchases ->
                    val purchasesArray = allPurchases as Array<dynamic>
                    var foundId: Long? = null
                    var foundText = ""
                    val target = value.lowercase()
                    for (p in purchasesArray) {
                        val ch = js("p.chassis")?.toString() ?: ""
                        if (ch.lowercase() == target || ch.lowercase().contains(target)) {
                            val idDyn = js("p.id")
                            val id = idDyn.toString().toDouble().toLong()
                            foundId = id
                            val carName = js("p.carName")?.toString() ?: ""
                            foundText = "${ch} — ${carName}"
                            break
                        }
                    }
                    if (foundId != null) {
                        if (selectedPurchases.contains(foundId)) {
                            showMessage("Car already selected", "info")
                        } else {
                            selectedPurchases.add(foundId)
                            showMessage("Added car: ${'$'}foundText", "success")
                            renderSelectedCarsPreview()
                        }
                    } else {
                        showMessage("No car found for the given chassis", "error")
                    }
                }
            } else {
                showMessage("Failed to search purchases", "error")
            }
        }.catch { error ->
            showMessage("Search failed: ${'$'}{error.message}", "error")
        }
    })

    // Initial preview render
    renderSelectedCarsPreview()
}

fun collectInvoiceDataAndGeneratePdf(selectedIds: List<Long>) {
    val invoiceData = js("{}")
    invoiceData.invoiceNo = (document.getElementById("invoiceNo") as HTMLInputElement).value
    invoiceData.lcNo = (document.getElementById("lcNo") as HTMLInputElement).value
    invoiceData.vessel = (document.getElementById("vessel") as HTMLInputElement).value
    invoiceData.sailDate = (document.getElementById("sailDate") as HTMLInputElement).value
    invoiceData.from = (document.getElementById("from") as HTMLInputElement).value
    invoiceData.to = (document.getElementById("to") as HTMLInputElement).value
    invoiceData.consignee = (document.getElementById("consignee") as HTMLTextAreaElement).value
    
    // Now generate the PDF with invoice data
    generateRixoPdfWithInvoiceData(selectedIds, invoiceData)
}

// Simple client-side validation with accessible error messages
fun validateInvoiceForm(): Boolean {
    fun setFieldError(fieldId: String, message: String?) {
        val input = document.getElementById(fieldId) as Element?
        val errorEl = document.getElementById(fieldId + "Error") as HTMLElement?
        val inputEl = input as? HTMLElement
        if (errorEl != null) {
            errorEl.textContent = message ?: ""
        }
        inputEl?.setAttribute("aria-invalid", if (message != null) "true" else "false")
        if (inputEl is HTMLInputElement) {
            inputEl.style.borderColor = if (message != null) "#ef4444" else "#d1d5db"
        }
        if (inputEl is HTMLTextAreaElement) {
            inputEl.style.borderColor = if (message != null) "#ef4444" else "#d1d5db"
        }
    }

    var ok = true
    val requiredIds = arrayOf("invoiceNo", "vessel", "sailDate", "from", "to", "consignee")
    requiredIds.forEach { id ->
        val el = document.getElementById(id)
        val value = when (el) {
            is HTMLInputElement -> el.value.trim()
            is HTMLTextAreaElement -> el.value.trim()
            else -> ""
        }
        if (value.isEmpty()) {
            setFieldError(id, "This field is required")
            ok = false
        } else {
            setFieldError(id, null)
        }
    }
    return ok
}

// Renders a small preview list of currently selected cars on the invoice page
fun renderSelectedCarsPreview() {
    val container = document.getElementById("selectedCarsPreview") as HTMLElement?
    if (container == null) return
    val ids = selectedPurchases.toList()
    if (ids.isEmpty()) {
        container.innerHTML = "<div style=\"color:#6b7280; font-size: 13px;\">No cars selected.</div>"
        return
    }
    window.fetch("http://localhost:8083/api/purchases").then { response ->
        if (response.ok) {
            response.json().then { allPurchases ->
                val purchasesArray = allPurchases as Array<dynamic>
                val listItems = StringBuilder()
                var count = 0
                for (p in purchasesArray) {
                    val id = js("p.id").toString().toDouble().toLong()
                    if (selectedPurchases.contains(id)) {
                        count += 1
                        val ch = js("p.chassis")?.toString() ?: ""
                        val car = js("p.carName")?.toString() ?: ""
                        listItems.append("<li style=\"padding:6px 8px;border:1px solid #e5e7eb;border-radius:8px;background:#fff;\">#${id} — ${ch} <span style=\"color:#6b7280;\">${car}</span></li>")
                    }
                }
                container.innerHTML = "<div style=\"margin:8px 0 6px 0; font-weight:600;\">Selected Cars (${count})</div>" +
                    "<ul style=\"display:grid;grid-template-columns:1fr;gap:8px;list-style:none;padding:0;margin:0;\">" +
                    listItems.toString() + "</ul>"
            }
        } else {
            container.innerHTML = "<div style=\"color:#b91c1c; font-size: 13px;\">Failed to load selected cars.</div>"
        }
    }.catch { _ ->
        container.innerHTML = "<div style=\"color:#b91c1c; font-size: 13px;\">Failed to load selected cars.</div>"
    }
}

fun generateRixoPdfWithInvoiceData(selectedIds: List<Long>, invoiceData: dynamic) {
    showMessage("Generating Rixo PDF...", "info")
    console.log("Selected IDs for PDF generation:", selectedIds)
    console.log("Invoice data:", invoiceData)
    
    // Create request body with selected IDs and invoice data
    val requestBody = js("{}")
    val jsArray = js("[]")
    selectedIds.forEach { id ->
        // Convert Kotlin Long to JavaScript primitive number
        jsArray.push(id.toInt())
    }
    requestBody.ids = jsArray
    requestBody.invoiceData = invoiceData
    
    console.log("Request body before stringify:", requestBody)
    console.log("Request body ids:", requestBody.ids)
    console.log("Request body invoice data:", requestBody.invoiceData)
    console.log("JS Array length:", jsArray.length)
    
    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(requestBody)
    
    console.log("Request body after stringify:", requestInit.body)
    
    window.fetch("http://localhost:8083/api/purchases/rixo-pdf", requestInit).then { response ->
        if (response.ok) {
            response.blob().then { blob ->
                // Store the blob for later use
                js("window.generatedPdfBlob = blob")
                
                // Show PDF generation success modal
                showPdfGenerationSuccessModal(blob)
                
                showMessage("Rixo PDF generated successfully!", "success")
                selectedPurchases.clear()
                updateRixoButtonVisibility()
                loadPurchases() // Refresh the list
            }
        } else {
            response.text().then { errorText ->
                showMessage("Failed to generate PDF: $errorText", "error")
            }
        }
    }.catch { error ->
        showMessage("Failed to generate PDF: ${error.message}", "error")
    }
}

fun showPdfGenerationSuccessModal(blob: dynamic) {
    val modal = document.createElement("div")
    modal.id = "pdfSuccessModal"
    modal.setAttribute("style", "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000;")
    
    modal.innerHTML = """
        <div style="background-color: white; padding: 30px; border-radius: 8px; min-width: 400px; max-width: 500px; text-align: center;">
            <div style="margin-bottom: 20px;">
                <div style="width: 60px; height: 60px; background-color: #28a745; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 15px;">
                    <span style="color: white; font-size: 24px; font-weight: bold;">✓</span>
                </div>
                <h2 style="margin: 0; color: #333;">PDF Generated Successfully!</h2>
                <p style="margin: 10px 0 0; color: #666;">Your Rixo purchase report has been generated. What would you like to do next?</p>
            </div>
            
            <div style="display: flex; gap: 15px; justify-content: center; margin-top: 25px;">
                <button id="downloadPdfBtn" style="padding: 12px 24px; background-color: #007bff; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 8px;">
                    <span>📥</span>
                    Download PDF
                </button>
                <button id="sendEmailBtn" style="padding: 12px 24px; background-color: #dc3545; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 8px;">
                    <span>📧</span>
                    Send in Gmail
                </button>
            </div>
            
            <div style="margin-top: 20px;">
                <button id="closePdfModalBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">
                    Close
                </button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Event listeners
    document.getElementById("downloadPdfBtn")?.addEventListener("click", { _: Event ->
        downloadPdf(blob)
        document.body?.removeChild(modal)
    })
    
    document.getElementById("sendEmailBtn")?.addEventListener("click", { _: Event ->
        sendPdfViaGmail(blob)
        document.body?.removeChild(modal)
    })
    
    document.getElementById("closePdfModalBtn")?.addEventListener("click", { _: Event ->
        document.body?.removeChild(modal)
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event ->
        if (event.target == modal) {
            document.body?.removeChild(modal)
        }
    })
}

fun downloadPdf(blob: dynamic) {
    // Create download link
    val url = js("window.URL.createObjectURL(blob)")
    val a = document.createElement("a") as HTMLAnchorElement
    a.setAttribute("href", url)
    a.setAttribute("download", "rixo-purchases-${js("Date.now()")}.pdf")
    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
    js("window.URL.revokeObjectURL(url)")
    
    showMessage("PDF downloaded successfully!", "success")
}

fun sendPdfViaGmail(blob: dynamic) {
    // Placeholder for Gmail functionality
    showMessage("Gmail functionality will be implemented later", "info")
    console.log("Gmail send functionality - blob size:", js("blob.size"))
}

fun deletePurchase(id: Long) {
    if (window.confirm("Are you sure you want to delete this purchase?")) {
        val requestInit = js("{}")
        requestInit.method = "DELETE"
        
        window.fetch("http://localhost:8083/api/purchases/$id", requestInit).then { response ->
            if (response.ok) {
                showMessage("Purchase deleted successfully!", "success")
                // Always return to the main list after deletion
                showPurchaseList()
            } else {
                showMessage("Failed to delete purchase", "error")
            }
        }.catch { error ->
            showMessage("Failed to delete purchase: ${error.message}", "error")
        }
    }
}

fun showMessage(message: String, type: String) {
    // Remove existing message
    document.getElementById("message")?.remove()
    
    val messageDiv = document.createElement("div")
    messageDiv.id = "message"
    
    val backgroundColor = when (type) {
        "success" -> "#d4edda"
        "error" -> "#f8d7da"
        "warning" -> "#fff3cd"
        else -> "#d1ecf1"
    }
    
    val color = when (type) {
        "success" -> "#155724"
        "error" -> "#721c24"
        "warning" -> "#856404"
        else -> "#0c5460"
    }
    
    messageDiv.setAttribute("style", "padding: 10px; margin-bottom: 10px; background-color: $backgroundColor; color: $color; border: 1px solid #c3e6cb; border-radius: 4px; position: fixed; top: 20px; right: 20px; z-index: 1001;")
    messageDiv.textContent = message
    
    document.body?.appendChild(messageDiv)
    
    // Auto-remove after 5 seconds
    window.setTimeout({
        messageDiv.remove()
    }, 5000)
}
