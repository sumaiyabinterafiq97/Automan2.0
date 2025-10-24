package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.files.File
import org.w3c.dom.events.Event

// Global mutable list to store cars that have been confirmed (saved) on the C&F page
val cnfConfirmedCars: MutableList<dynamic> = mutableListOf()

// Global variable to store cars passed to C&F page from Car Booking
var cnfPageSelectedCars: List<dynamic> = emptyList()

// Global mutable list to store cars that have been confirmed (saved) on the FOB page
val fobConfirmedCars: MutableList<dynamic> = mutableListOf()

// Global variable to store cars passed to FOB page from Car Booking
var fobPageSelectedCars: List<dynamic> = emptyList()

// Global variable to store the currently selected country from the Car Booking page
var currentSelectedCountry: String = "PAKISTAN" // Default to Pakistan

// Global variable to store the country selected on the Car Booking page for C&F/FOB/Package context
var cnfPageSelectedCountry: String = "PAKISTAN" // Default value

// Global variables to store booking details for PDF generation
var globalBookingDetails: dynamic = js("{}")
var globalSelectedCarsForPdf: List<dynamic> = emptyList()
var globalFreightValues: MutableMap<String, Double> = mutableMapOf()

// Store booking details for PDF generation
fun storeBookingDetailsForPdf() {
    console.log("📋 Storing booking details for PDF generation...")
    
    val bookingDetails = js("{}")
    
    // Get form values and use fallbacks for empty strings
    val bookingNoValue = (document.getElementById("bookingNo") as? HTMLInputElement)?.value ?: ""
    val vesselNameValue = (document.getElementById("vesselSelect") as? HTMLSelectElement)?.selectedOptions?.item(0)?.textContent ?: ""
    val polValue = (document.getElementById("polPort") as? HTMLSelectElement)?.selectedOptions?.item(0)?.textContent ?: ""
    val podValue = (document.getElementById("podPort") as? HTMLInputElement)?.value ?: ""
    val shippingDateValue = (document.getElementById("etdDate") as? HTMLInputElement)?.value ?: ""
    val consigneeNameValue = (document.getElementById("consigneeName") as? HTMLInputElement)?.value ?: ""
    
    // Use fallback values for empty strings
    bookingDetails.bookingNo = if (bookingNoValue.isNotEmpty()) bookingNoValue else "EBKG14265885"
    bookingDetails.vesselName = if (vesselNameValue.isNotEmpty() && vesselNameValue != "Select Vessel") vesselNameValue else "MSC RICCARDA II"
    bookingDetails.pol = if (polValue.isNotEmpty()) polValue else "HAKATA"
    bookingDetails.pod = if (podValue.isNotEmpty()) podValue else "KARACHI"
    bookingDetails.shippingDate = if (shippingDateValue.isNotEmpty()) shippingDateValue else "2025-09-27"
    bookingDetails.consigneeName = if (consigneeNameValue.isNotEmpty()) consigneeNameValue else "OVERSEAS TRANSIT AGENCY (PVT) LTD."
    bookingDetails.consigneeAddress = "1201-1203, 12TH FLOOR, Q.M.HOUSE, PLOT NO. 11/2RY9, ELLANDER ROAD, OFF.I.I CHUNDRIGAR ROAD (OPP. SHAHEEN COMPLEX), KARACHI"
    
    globalBookingDetails = bookingDetails
    globalSelectedCarsForPdf = getSelectedCarsFromTable()
    
    console.log("✅ Booking details stored:", bookingDetails)
    console.log("✅ Selected cars stored:", globalSelectedCarsForPdf.size)
    console.log("🔍 DEBUG: globalBookingDetails after assignment:", globalBookingDetails)
}

// Global variable to store PDF blob
var globalPdfBlob: dynamic = null

// Show PDF download modal
fun showPdfDownloadModal(pdfBlob: dynamic) {
    console.log("📄 Showing PDF download modal...")
    console.log("🔍 DEBUG: pdfBlob received:", pdfBlob)
    
    // Store the blob globally so it can be accessed by the event listener
    globalPdfBlob = pdfBlob
    console.log("🔍 DEBUG: globalPdfBlob set to:", globalPdfBlob)
    console.log("🔍 DEBUG: Using direct parameter passing approach")
    console.log("🚀🚀🚀 PDF MODAL FIX - CACHE BUST - 1736383000 🚀🚀🚀")
    console.log("🔍 DEBUG: globalBookingDetails value before modal JS setup:", globalBookingDetails)
    console.log("🔍 DEBUG: globalBookingDetails.bookingNo:", globalBookingDetails.bookingNo)
    
    // Extract booking number to avoid scope issues
    val bookingNumber = globalBookingDetails.bookingNo ?: "unknown"
    console.log("🔍 DEBUG: Extracted booking number:", bookingNumber)
    
    val modalHTML = """
        <div id="pdfDownloadModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 1000; display: flex; justify-content: center; align-items: center;">
            <div style="background: white; padding: 40px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); max-width: 500px; text-align: center;">
                <div style="margin-bottom: 30px;">
                    <div style="width: 80px; height: 80px; background-color: #dcfce7; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 20px;">
                        <span style="font-size: 40px;">📄</span>
                    </div>
                    <h3 style="margin: 0 0 15px 0; color: #374151; font-size: 24px; font-weight: 600;">PDF Generated Successfully!</h3>
                    <p style="margin: 0; color: #6b7280; font-size: 16px; line-height: 1.5;">Your Shipping Schedule PDF is ready for download.</p>
                </div>
                <div style="display: flex; gap: 15px; justify-content: center;">
                    <button id="downloadPdfBtn" style="padding: 12px 24px; background-color: #059669; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px; font-weight: 500; display: flex; align-items: center; gap: 8px;">
                        <span>📥</span>
                        <span>Download PDF</span>
                    </button>
                    <button id="closePdfModal" style="padding: 12px 24px; background-color: #6b7280; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px; font-weight: 500;">Close</button>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Attach event listeners with a small delay to ensure DOM is ready
    js("""
        setTimeout(function() {
            console.log('🔍 DEBUG: Setting up modal event listeners...');
            
            var downloadBtn = document.getElementById('downloadPdfBtn');
            console.log('🔍 DEBUG: downloadPdfBtn element found:', downloadBtn);
            if (downloadBtn) {
                downloadBtn.addEventListener('click', function() {
                    console.log('📥 Downloading PDF...');
                    try {
                        // Create download link directly with the blob
                        var url = URL.createObjectURL(arguments[0]);
                        var link = document.createElement('a');
                        link.setAttribute('href', url);
                        link.setAttribute('download', 'shipping_schedule_' + (arguments[1] || 'unknown') + '.pdf');
                        document.body.appendChild(link);
                        link.click();
                        document.body.removeChild(link);
                        setTimeout(function() { URL.revokeObjectURL(url); }, 1000);
                        console.log('✅ PDF download initiated');
                    } catch (e) {
                        console.error('❌ Error downloading PDF:', e);
                        alert('Error downloading PDF: ' + e.message);
                    }
                }.bind(null, pdfBlob, bookingNumber));
            }
            var closeBtn = document.getElementById('closePdfModal');
            if (closeBtn) {
                closeBtn.addEventListener('click', function() {
                    var modal = document.getElementById('pdfDownloadModal');
                    if (modal) modal.remove();
                });
            }
            var modal = document.getElementById('pdfDownloadModal');
            if (modal) {
                modal.addEventListener('click', function(event) {
                    if (event.target === modal) {
                        modal.remove();
                    }
                });
            }
        }, 100);
    """)
    
    console.log("✅ PDF download modal displayed")
}

fun showClientDetailsPage(clientId: Long) {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #e9ecef; border-radius: 4px; padding: 20px;">
            <div style="display:flex; justify-content: space-between; align-items:center; margin-bottom: 16px;">
                <h2 style="margin:0;">Client Details</h2>
                <div>
                    <button id="exportClientTxBtn" style="padding: 8px 14px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 8px;">Export Data</button>
                    <button id="backToClientsBtn" style="padding: 8px 14px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Back to Clients</button>
                </div>
            </div>
            <div id="clientDetailsContent"></div>
            <div id="clientEventsTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #666; padding: 20px;">
                    Loading transactions...
                </div>
            </div>
        </div>
    """
    document.getElementById("backToClientsBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/clients"
    })
    document.getElementById("exportClientTxBtn")?.addEventListener("click", { _: Event ->
        exportClientTransactions(clientId)
    })
    // Load data
    loadClientDetails(clientId)
    loadClientEvents(clientId)
}

fun main() {
    console.log("🚀🚀🚀 FOB PDF FUNCTIONALITY IMPLEMENTED - CACHE BUST - 1736382000 🚀🚀🚀")
    console.log("🔥🔥🔥 FOB PDF - CONFIRM BUTTON GENERATES PDF - CACHE BUSTED! 🔥🔥🔥")
    console.log("💥💥💥 FOB PDF - 1736382000 - NEW CODE LOADED! 💥💥💥")
    console.log("🎯🎯🎯 FOB PDF CACHE BUST - 1736382000 - NEW FILENAME LOADED! 🎯🎯🎯")
    val root = document.getElementById("root")!!
    console.log("Root element found: $root")
    
    // Expose functions to global scope for HTML onclick attributes
    window.asDynamic().closeAddClientModal = ::closeAddClientModal
    window.asDynamic().selectClient = ::selectClient
    window.asDynamic().editClient = ::editClient
    window.asDynamic().editClientFromList = ::editClientFromList
    window.asDynamic().addClientTransaction = ::addClientTransaction
    window.asDynamic().closeEditClientModal = ::closeEditClientModal
    
    // Expose state persistence functions to global scope
    window.asDynamic().saveCarBookingState = ::saveCarBookingState
    window.asDynamic().restoreCarBookingState = ::restoreCarBookingState
    window.asDynamic().restoreSelectedRows = ::restoreSelectedRows
    window.asDynamic().displayPurchasesAsCars = ::displayPurchasesAsCars
    
    // Expose freight calculation functions to global scope
    window.asDynamic().removeCarFromContainer = { containerId: String, chassis: String ->
        // Get the current selected cars from the global variable
        val selectedCars = if (cnfConfirmedCars.isNotEmpty()) cnfConfirmedCars else fobConfirmedCars
        removeCarFromContainer(containerId, chassis, selectedCars)
    }
    
    
    // Expose client selection handlers used by Add/Edit forms
    window.asDynamic().handleClientSelection = { value: Any? ->
        val v = value?.toString() ?: ""
        handleClientSelection(v)
    }
    window.asDynamic().handleEditClientSelection = { value: Any? ->
        val v = value?.toString() ?: ""
        handleEditClientSelection(v)
    }
    
    console.log("Functions exposed to global scope successfully")
    console.log("closeAddClientModal available:", window.asDynamic().closeAddClientModal != null)
    console.log("selectClient available:", window.asDynamic().selectClient != null)
    console.log("editClient available:", window.asDynamic().editClient != null)
    
    // Check if system is initialized first, then create app if needed
    checkSystemInitialization(root)
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

// Converts a stored date label like "June3, 2025(Tuesday)" to ISO yyyy-MM-dd for <input type="date">
private fun toIsoFromLabel(dateStr: String?): String {
    if (dateStr == null || dateStr.isBlank()) return ""
    // If already ISO-like, return as-is
    if (dateStr.contains("-")) return dateStr
    // Strip weekday part in parentheses
    val base = dateStr.replace(Regex("\\(.*?\\)"), "").trim()
    // Ensure a space between month and day (e.g., "June3, 2025" -> "June 3, 2025")
    val normalized = base.replace(Regex("^([A-Za-z]+)(\\d+),\\s*(\\d{4})$"), "$1 $2, $3")
    return try {
        val d = js("new Date(normalized)")
        if (js("isNaN(d)") as Boolean) "" else buildString {
            val y = js("d.getFullYear()") as Int
            val m = (js("d.getMonth()") as Int) + 1
            val day = js("d.getDate()") as Int
            append(y.toString())
            append("-")
            append(if (m < 10) "0$m" else m.toString())
            append("-")
            append(if (day < 10) "0$day" else day.toString())
        }
    } catch (e: dynamic) { "" }
}

// Current sorting state
private var currentSortField: String? = null
private var currentSortOrder: String = "desc" // desc = newest first for date, Z-A for text

// Multi-select state
private var selectedPurchases = mutableSetOf<Long>()

// System initialization check
fun checkSystemInitialization(root: Element) {
    window.fetch("/api/auth/users/count")
        .then { it.json() }
        .then { response ->
            val count = response.asDynamic().count as Number
            val isInitialized = response.asDynamic().isInitialized as Boolean
            
            if (isInitialized) {
                // System is initialized, create app and proceed with normal flow
                createApp(root)
                window.addEventListener("hashchange", { _: Event -> updateContent(root) })
                updateContent(root)
            } else {
                // System not initialized, show setup page
                showSetupPage(root)
            }
        }
        .catch { error ->
            console.error("Failed to check system initialization:", error)
            // On error, assume system is initialized and proceed normally
            createApp(root)
            window.addEventListener("hashchange", { _: Event -> updateContent(root) })
            updateContent(root)
        }
}

// Initial Setup Page
fun showSetupPage(root: Element) {
    root.innerHTML = """
        <div style="max-width: 600px; margin: 0 auto; font-family: Inter, system-ui, -apple-system, Segoe UI, Roboto, Arial; padding: 20px;">
            <div style="text-align: center; margin-bottom: 40px;">
                <h1 style="color: #1f2937; font-size: 32px; font-weight: 800; margin-bottom: 8px;">Welcome to Automan</h1>
                <p style="color: #6b7280; font-size: 18px;">Let's set up your system with the first administrator account</p>
            </div>
            
            <div style="background: #fff; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.1); padding: 32px; border: 1px solid #e5e7eb;">
                <div style="margin-bottom: 24px;">
                    <h2 style="color: #1f2937; font-size: 24px; font-weight: 700; margin-bottom: 8px;">Create Admin Account</h2>
                    <p style="color: #6b7280; font-size: 14px;">This will be your system administrator account with full access to all features.</p>
                </div>
                
                <form id="setupForm" style="display: flex; flex-direction: column; gap: 20px;">
                    <div>
                        <label style="display: block; color: #374151; font-weight: 600; margin-bottom: 8px; font-size: 14px;">Email Address</label>
                        <input id="setupEmail" type="email" placeholder="admin@example.com" required
                               style="width: 100%; padding: 12px 16px; border: 2px solid #e5e7eb; border-radius: 8px; font-size: 16px; outline: none; transition: border-color 0.2s;"
                               onfocus="this.style.borderColor='#3b82f6'" onblur="this.style.borderColor='#e5e7eb'">
                    </div>
                    
                    <div>
                        <label style="display: block; color: #374151; font-weight: 600; margin-bottom: 8px; font-size: 14px;">Full Name</label>
                        <input id="setupName" type="text" placeholder="John Doe" required
                               style="width: 100%; padding: 12px 16px; border: 2px solid #e5e7eb; border-radius: 8px; font-size: 16px; outline: none; transition: border-color 0.2s;"
                               onfocus="this.style.borderColor='#3b82f6'" onblur="this.style.borderColor='#e5e7eb'">
                    </div>
                    
                    <div>
                        <label style="display: block; color: #374151; font-weight: 600; margin-bottom: 8px; font-size: 14px;">Password</label>
                        <input id="setupPassword" type="password" placeholder="Enter a strong password" required
                               style="width: 100%; padding: 12px 16px; border: 2px solid #e5e7eb; border-radius: 8px; font-size: 16px; outline: none; transition: border-color 0.2s;"
                               onfocus="this.style.borderColor='#3b82f6'" onblur="this.style.borderColor='#e5e7eb'">
                        <div style="margin-top: 8px; font-size: 12px; color: #6b7280;">
                            Password must be at least 8 characters with uppercase, lowercase, and numbers
                        </div>
                    </div>
                    
                    <button id="setupSubmit" type="submit" 
                            style="background: linear-gradient(135deg, #3b82f6, #1d4ed8); color: white; padding: 14px 24px; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; transition: transform 0.2s;"
                            onmouseover="this.style.transform='translateY(-1px)'" onmouseout="this.style.transform='translateY(0)'">
                        Create Admin Account
                    </button>
                </form>
                
                <div id="setupMessage" style="margin-top: 20px; padding: 12px; border-radius: 8px; display: none;"></div>
            </div>
            
            <div style="text-align: center; margin-top: 32px; color: #6b7280; font-size: 14px;">
                <p>🔒 This setup is only available for the first 5 minutes after system startup</p>
                <p>🌐 Only accessible from localhost for security</p>
            </div>
        </div>
    """
    
    setupSetupHandlers()
}

fun setupSetupHandlers() {
    val form = document.getElementById("setupForm") as HTMLFormElement
    val messageDiv = document.getElementById("setupMessage") as HTMLElement
    
    form.addEventListener("submit", { event ->
        event.preventDefault()
        
        val email = (document.getElementById("setupEmail") as HTMLInputElement).value.trim()
        val name = (document.getElementById("setupName") as HTMLInputElement).value.trim()
        val password = (document.getElementById("setupPassword") as HTMLInputElement).value
        
        if (email.isBlank() || name.isBlank() || password.isBlank()) {
            showSetupMessage("Please fill in all fields", "error")
            return@addEventListener
        }
        
        // Show loading state
        val submitBtn = document.getElementById("setupSubmit") as HTMLButtonElement
        submitBtn.disabled = true
        submitBtn.textContent = "Creating Account..."
        
        val body = js("({method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({email:email,name:name,password:password})})")
        
        window.fetch("/api/auth/setup", body)
            .then { response ->
                if (response.ok) {
                    response.json()
                } else {
                    response.json().then { errorData ->
                        throw Error(errorData.asDynamic().message?.toString() ?: "Setup failed")
                    }
                }
            }
            .then { response ->
                showSetupMessage("Admin account created successfully! Redirecting to login...", "success")
                
                // Redirect to login page after 2 seconds
                window.setTimeout({
                    window.location.hash = "#/"
                    window.location.reload()
                }, 2000)
            }
            .catch { error ->
                showSetupMessage("Setup failed: ${error.message}", "error")
                submitBtn.disabled = false
                submitBtn.textContent = "Create Admin Account"
            }
    })
}

fun showSetupMessage(message: String, type: String) {
    val messageDiv = document.getElementById("setupMessage") as HTMLElement
    messageDiv.style.display = "block"
    messageDiv.textContent = message
    
    when (type) {
        "success" -> {
            messageDiv.style.backgroundColor = "#d1fae5"
            messageDiv.style.color = "#065f46"
            messageDiv.style.border = "1px solid #a7f3d0"
        }
        "error" -> {
            messageDiv.style.backgroundColor = "#fee2e2"
            messageDiv.style.color = "#991b1b"
            messageDiv.style.border = "1px solid #fca5a5"
        }
    }
}

fun createApp(root: Element) {
    root.innerHTML = """
        <div style="padding: 20px; font-family: Arial, sans-serif;">
            <style>
                .checkwrap { display: inline-flex; align-items: center; cursor: pointer; user-select: none; }
                .checkwrap input { position: absolute; opacity: 0; width: 0; height: 0; }
                .checkmark { width: 22px; height: 22px; flex: 0 0 22px; margin-right: 8px; border-radius: 50%; border: 2px solid #cbd5e1; display: inline-block; position: relative; transition: all .2s; background: #fff; line-height: 22px; text-align: center; }
                .checkwrap input:checked + .checkmark { background: #1e90ff; border-color: #1e90ff; }
                .checkwrap input:checked + .checkmark::after { content: "✓"; position: absolute; left: 4px; top: -1px; color: #fff; font-size: 16px; }
            </style>
            <div style="text-align: center; width: 100%;">
                <h1 style="margin: 0; display: inline-block;">Automan Car Purchase Management</h1>
            </div>
            
            <!-- Sidebar -->
            <div id="sidebar" style="position: fixed; top: 0; left: -250px; width: 250px; height: 100vh; background-color: #2c3e50; transition: left 0.3s ease; z-index: 1000; box-shadow: 2px 0 5px rgba(0,0,0,0.1);">
                <div style="padding: 20px;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px;">
                        <h3 style="color: white; margin: 0;">Menu</h3>
                        <button id="closeSidebar" style="background: none; border: none; color: white; font-size: 20px; cursor: pointer;">×</button>
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 15px;">
                        <button id="newBtn" style="padding: 12px 20px; background-color: #007bff; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left;">New+</button>
                        <button id="importBtn" style="padding: 12px 20px; background-color: #28a745; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left;">Import CSV</button>
                        <button id="rixoRequestBtn" style="padding: 12px 20px; background-color: #8e44ad; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left;">Rixo Request</button>
                        <button id="carBookingBtn" style="padding: 12px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left;">Car Booking</button>
                        <button id="userManagementBtn" style="padding: 12px 20px; background-color: #9b59b6; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left; display: none;">Manage Users</button>
                        <button id="clientAccountsBtn" style="padding: 12px 20px; background-color: #e74c3c; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left; display: none;">Client Accounts</button>
                        <button id="roleRequestBtn" style="padding: 12px 20px; background-color: #ffc107; color: black; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left; display: none;">Request Role</button>
                        <div style="border-top: 1px solid #34495e; margin: 10px 0; padding-top: 15px;">
                            <div id="userInfo" style="color: #bdc3c7; font-size: 12px; margin-bottom: 10px; text-align: center;">
                                <!-- User info will be populated here -->
                            </div>
                            <button id="logoutBtn" style="padding: 12px 20px; background-color: #e74c3c; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-align: left; width: 100%;">Logout</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Overlay -->
            <div id="sidebarOverlay" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 999; display: none;"></div>
            
            
            <div style="margin-bottom: 20px; margin-top: 60px; display: flex; gap: 10px;">
                <!-- Rixo buttons temporarily hidden per request -->
                <button id="rixoTransportBtn" style="display: none;">Generate Rixo PDF</button>
                <button id="rixoBtn" style="display: none;">Generating Invoice PDF</button>
            </div>
            <style>
                /* Force-hide only the Rixo Transport button */
                #rixoTransportBtn { display: none !important; }
                /* Hide invoice button by default, JS shows it when rows selected */
                #rixoBtn { display: none; }
                .sort-menu { display: none; }
                /* Ensure hamburger button stays fixed during scroll */
                #hamburgerBtn { 
                    position: fixed !important; 
                    top: 20px !important; 
                    left: 20px !important; 
                    z-index: 10000 !important; 
                    pointer-events: auto !important;
                }
            </style>
            
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
    
    document.getElementById("rixoRequestBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/rixo-transport"
    })
    
    document.getElementById("carBookingBtn")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/booking"
    })
    
    document.getElementById("rixoTransportBtn")?.addEventListener("click", { _: Event ->
        handleRixoTransportPdfGeneration()
    })
    
    // Sidebar event listeners (hamburger button will be added only on purchase list page)
    
    document.getElementById("closeSidebar")?.addEventListener("click", { _: Event ->
        closeSidebar()
    })
    
    document.getElementById("sidebarOverlay")?.addEventListener("click", { _: Event ->
        closeSidebar()
    })
    
    // Logout functionality
    document.getElementById("logoutBtn")?.addEventListener("click", { _: Event ->
        logout()
    })
    
    // User management functionality (for ADMIN only)
    document.getElementById("userManagementBtn")?.addEventListener("click", { _: Event ->
        showUserManagementPage()
    })
    
    // Client accounts functionality (for ADMIN only)
    document.getElementById("clientAccountsBtn")?.addEventListener("click", { _: Event ->
        showClientAccountsPage()
    })
    
    // Role request functionality (for non-ADMIN users)
    document.getElementById("roleRequestBtn")?.addEventListener("click", { _: Event ->
        showRoleRequestForm()
    })
    
    // Update user info in sidebar
    updateUserInfoInSidebar()
    
    // Load initial data only if we're on the purchase list page
    val hash = window.location.hash
    if (hash == "#/purchase") {
        loadPurchases()
    }
}

fun updateContent(root: Element) {
    val hash = window.location.hash
    val content = document.getElementById("content")!!
    
    // Redirect unauthenticated users to login page at #/
    val token = window.localStorage.getItem("authToken")
    if (token == null || token.isBlank()) {
        if (hash != "#/" && hash != "" && hash != "#") {
            window.location.hash = "#/"
        return
        }
    }

    when {
        hash == "#/" -> {
            removeHamburgerMenu()
            showAuthPage()
            // Hide PDF buttons on auth page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/add") -> {
            removeHamburgerMenu() // Remove hamburger menu from add page
            content.innerHTML = createAddFormHTML()
            setupAddFormListeners()
            // Hide sorting bar on add page
            // Hide PDF buttons on add page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/edit/") -> {
            removeHamburgerMenu() // Remove hamburger menu from edit page
            val id = hash.substring(7).toLongOrNull()
            if (id != null) {
                showEditForm(id)
            } else {
                showPurchaseList()
            }
            // Hide sorting bar on edit page
            // Hide PDF buttons on edit page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/invoice") -> {
            removeHamburgerMenu() // Remove hamburger menu from invoice page
            showInvoicePage()
            // Hide sorting bar on invoice page
            // Hide PDF buttons on invoice page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/rixo-transport") -> {
            showRixoRequestGeneratorPage()
        }
        hash.startsWith("#/booking") -> {
            showCarBookingPage()
        }
        hash.startsWith("#/users/edit/") -> {
            removeHamburgerMenu() // Remove hamburger menu from edit user page
            val id = hash.substring(13).toLongOrNull()
            if (id != null) {
                showEditUserPage(id)
            } else {
                showUserManagementPage()
            }
            // Hide sorting bar on edit user page
            // Hide PDF buttons on edit user page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/users/add") -> {
            removeHamburgerMenu() // Remove hamburger menu from add user page
            showAddUserForm()
            // Hide sorting bar on add user page
            // Hide PDF buttons on add user page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/users") -> {
            removeHamburgerMenu() // Remove hamburger menu from user management page
            showUserManagementPage()
            // Hide sorting bar on user management page
            // Hide PDF buttons on user management page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/client/") -> {
            removeHamburgerMenu()
            val id = hash.substring(9).toLongOrNull()
            if (id != null) {
                showClientDetailsPage(id)
            } else {
                showClientAccountsPage()
            }
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        hash.startsWith("#/clients") -> {
            removeHamburgerMenu() // Remove hamburger menu from client accounts page
            showClientAccountsPage()
            // Hide sorting bar on client accounts page
            // Hide PDF buttons on client accounts page
            (document.getElementById("rixoBtn") as HTMLElement?)?.style?.display = "none"
            (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
        }
        else -> {
            if (token == null || token.isBlank()) {
                window.location.hash = "#/"
            } else {
                showPurchaseList()
                // Show sorting bar on purchase list page
                // Hide only Rixo Transport by default; invoice button is controlled by selection
                (document.getElementById("rixoTransportBtn") as HTMLElement?)?.style?.display = "none"
            }
        }
    }
}
// Combined Auth Page (Signup / Signin with slide toggle) and role cards
fun showAuthPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div class="auth-container">
            <!-- Background with automotive theme -->
            <div class="auth-background">
                <div class="background-overlay"></div>
                </div> 
            
            <!-- Auth Modal -->
            <div class="auth-modal">
                <div class="modal-header">
                    <h1 class="modal-title" id="authModalTitle">Sign Up</h1>
                    <button class="close-btn" id="closeAuth">×</button>
                </div> 
                
                <!-- Signup Panel -->
                <div id="signupPanel" class="auth-panel" style="display:block;">
                    <div class="panel-content">
                        <div class="form-group">
                            <label class="form-label">Email</label>
                            <div class="input-container">
                                <input id="su_email" type="email" placeholder="Enter your email" class="form-input"/>
                                <span class="input-icon">✉️</span>
                    </div> 
                    </div> 
                        
                        <div class="form-group">
                            <label class="form-label">Your Name</label>
                            <div class="input-container">
                                <input id="su_name" type="text" placeholder="Enter your name" class="form-input"/>
                                <span class="input-icon">👤</span>
                    </div> 
                    </div> 
                        
                        <div class="form-group">
                            <label class="form-label">Password</label>
                            <div class="input-container">
                                <input id="su_pass" type="password" placeholder="Enter your password" class="form-input"/>
                                <span class="input-icon">🔒</span>
                            </div> 
                        </div>
                        
                        <div class="form-group">
                            <label class="form-label">Role</label>
                            <div class="input-container">
                                <select id="su_role" class="form-input">
                                    <option value="VIEWER">Viewer</option>
                                    <option value="EDITOR">Editor</option>
                                    <option value="ADMIN">Admin</option>
                                </select>
                                <span class="input-icon">👤</span>
                            </div> 
                        </div>
                        
                        <div class="form-options">
                            <label class="checkbox-container">
                                <input type="checkbox" id="rememberMe">
                                <span class="checkmark"></span>
                                Remember me
                            </label>
                            <a href="#" class="forgot-link">Forget Password?</a>
                        </div>
                        
                        <button id="btn_signup" class="auth-button">Sign Up</button>
                        
                        <div class="auth-switch">
                            <span>Already have an account? </span>
                            <button id="toggleToSignin" class="switch-link">Sign In</button>
                        </div>
                    </div>
                </div>

                <!-- Signin Panel -->
                <div id="signinPanel" class="auth-panel" style="display:none;">
                    <div class="panel-content">
                        <div class="form-group">
                            <label class="form-label">Email</label>
                            <div class="input-container">
                                <input id="si_email" type="email" placeholder="Enter your email" class="form-input"/>
                                <span class="input-icon">✉️</span>
                    </div> 
                    </div> 
                        
                        <div class="form-group">
                            <label class="form-label">Password</label>
                            <div class="input-container">
                                <input id="si_pass" type="password" placeholder="Enter your password" class="form-input"/>
                                <span class="input-icon">🔒</span>
                    </div> 
                    </div> 
                        
                        
                        <div class="form-options">
                            <label class="checkbox-container">
                                <input type="checkbox" id="rememberMeSignin">
                                <span class="checkmark"></span>
                                Remember me
                            </label>
                            <a href="#" class="forgot-link">Forget Password?</a>
                </div> 
                        
                        <button id="btn_signin" class="auth-button">Sign In</button>
                        
                        <div class="auth-switch">
                            <span>Don't have an account? </span>
                            <button id="toggleToSignup" class="switch-link">Sign Up</button>
            </div> 
                    </div>
                </div>
            </div>
        </div>
        
            <style>
            .auth-container {
                position: fixed;
                top: 0;
                left: 0;
                width: 100vw;
                height: 100vh;
                display: flex;
                align-items: center;
                justify-content: center;
                font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                overflow: hidden;
            }
            
            .auth-background {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: url('images/login-bg.png') center/cover no-repeat;
                z-index: 1;
            }
            
            .background-overlay {
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.4);
                z-index: 2;
            }
            
            
            .auth-modal {
                position: relative;
                background: rgba(0, 0, 0, 0.3);
                backdrop-filter: blur(15px);
                border-radius: 20px;
                box-shadow: 0 25px 50px rgba(0, 0, 0, 0.4);
                width: 90%;
                max-width: 400px;
                z-index: 10;
                border: 1px solid rgba(255, 255, 255, 0.1);
            }
            
            .modal-header {
                display: flex;
                justify-content: space-between;
                align-items: center;
                padding: 24px 24px 0 24px;
                border-bottom: 1px solid rgba(0, 0, 0, 0.1);
                margin-bottom: 24px;
            }
            
            .modal-title {
                font-size: 24px;
                font-weight: 700;
                color: white;
                margin: 0;
            }
            
            .close-btn {
                background: none;
                border: none;
                font-size: 24px;
                color: white;
                cursor: pointer;
                padding: 4px;
                border-radius: 50%;
                width: 32px;
                height: 32px;
                display: flex;
                align-items: center;
                justify-content: center;
                transition: all 0.2s;
            }
            
            .close-btn:hover {
                background: rgba(255, 255, 255, 0.2);
                color: white;
            }
            
            .auth-panel {
                padding: 0 24px 24px 24px;
            }
            
            .form-group {
                margin-bottom: 20px;
            }
            
            .form-label {
                display: block;
                font-size: 14px;
                font-weight: 600;
                color: white;
                margin-bottom: 8px;
            }
            
            .input-container {
                position: relative;
            }
            
            .form-input {
                width: 100%;
                padding: 12px 16px 12px 40px;
                border: 2px solid rgba(255, 255, 255, 0.3);
                border-radius: 12px;
                font-size: 16px;
                transition: all 0.3s ease;
                background: rgba(255, 255, 255, 0.1);
                outline: none;
                color: white;
            }
            
            .form-input::placeholder {
                color: rgba(255, 255, 255, 0.7);
            }
            
            .form-input:focus {
                border-color: rgba(255, 255, 255, 0.6);
                box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.1);
                background: rgba(255, 255, 255, 0.2);
            }
            
            .input-icon {
                position: absolute;
                left: 12px;
                top: 50%;
                transform: translateY(-50%);
                font-size: 16px;
                color: rgba(255, 255, 255, 0.7);
            }
            
            /* Select dropdown styling */
            select.form-input {
                appearance: none;
                -webkit-appearance: none;
                -moz-appearance: none;
                background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='white' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6,9 12,15 18,9'%3e%3c/polyline%3e%3c/svg%3e");
                background-repeat: no-repeat;
                background-position: right 12px center;
                background-size: 16px;
                padding-right: 40px;
                cursor: pointer;
            }
            
            select.form-input option {
                background: #1a1a1a;
                color: white;
                padding: 8px;
            }
            
            
            .form-options {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 24px;
            }
            
            .checkbox-container {
                display: flex;
                align-items: center;
                cursor: pointer;
                font-size: 14px;
                color: white;
            }
            
            .checkbox-container input {
                margin-right: 8px;
            }
            
            .forgot-link {
                color: rgba(255, 255, 255, 0.8);
                text-decoration: none;
                font-size: 14px;
                font-weight: 500;
            }
            
            .forgot-link:hover {
                text-decoration: underline;
            }
            
            .auth-button {
                width: 100%;
                padding: 14px;
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                border: none;
                border-radius: 12px;
                font-size: 16px;
                font-weight: 600;
                cursor: pointer;
                transition: all 0.3s ease;
                margin-bottom: 20px;
            }
            
            .auth-button:hover {
                transform: translateY(-2px);
                box-shadow: 0 8px 25px rgba(102, 126, 234, 0.4);
            }
            
            .auth-button:active {
                transform: translateY(0);
            }
            
            .auth-switch {
                text-align: center;
                font-size: 14px;
                color: white;
            }
            
            .switch-link {
                background: none;
                border: none;
                color: rgba(255, 255, 255, 0.8);
                font-weight: 600;
                cursor: pointer;
                text-decoration: underline;
            }
            
            .switch-link:hover {
                color: white;
            }
            
            @media (max-width: 480px) {
                .auth-modal {
                    width: 95%;
                    margin: 20px;
                }
                
                .role-selection {
                    flex-direction: column;
                }
                
                .role-card {
                    min-width: auto;
                }
            }
            </style>
    """
    setupAuthHandlers()
}
fun applyRoleBasedSignupRestrictions(currentUserRole: String) {
    val sgRoles = document.getElementsByClassName("sg-role")
    val siRoles = document.getElementsByClassName("si-role")
    
    // After system initialization, only allow VIEWER signup for regular users
    // Only ADMINs can create other roles through user management
    val allowedSignupRoles = when (currentUserRole) {
        "ADMIN" -> listOf("VIEWER", "EDITOR", "ADMIN") // Admin can create any role (through user management)
        else -> listOf("VIEWER") // All other users can only signup as VIEWER
    }
    
    // For SIGNIN, always show all roles so users can sign in with their actual role
    val allowedSigninRoles = listOf("VIEWER", "EDITOR", "ADMIN")
    
    // Apply restrictions to SIGNUP role cards only
    for (i in 0 until sgRoles.length) {
        val el = sgRoles.item(i) as HTMLElement
        val role = el.getAttribute("data-role") ?: ""
        if (role !in allowedSignupRoles) {
            el.style.display = "none"
        } else {
            el.style.display = "block"
        }
    }
    
    // Show ALL roles for SIGNIN (no restrictions)
    for (i in 0 until siRoles.length) {
        val el = siRoles.item(i) as HTMLElement
        val role = el.getAttribute("data-role") ?: ""
        if (role !in allowedSigninRoles) {
            el.style.display = "none"
        } else {
            el.style.display = "block"
        }
    }
    
    // Set default role to VIEWER for signup (only available role for non-admins)
    document.querySelector(".sg-role[data-role='VIEWER']")?.classList?.add("active")
    
    // Set default role to VIEWER for signin (but all roles are visible)
    document.querySelector(".si-role[data-role='VIEWER']")?.classList?.add("active")
}
private fun setupAuthHandlers() {
    var signupRole = "VIEWER"
    var signinRole = "VIEWER"
    val signupPanel = document.getElementById("signupPanel") as HTMLElement
    val signinPanel = document.getElementById("signinPanel") as HTMLElement
    val titleEl = document.getElementById("authModalTitle") as HTMLElement
    val toSignin = document.getElementById("toggleToSignin") as HTMLButtonElement
    val toSignup = document.getElementById("toggleToSignup") as HTMLButtonElement

    var isSigninMode = false

    // Check if user is already logged in and get their role
    val currentUserRole = window.localStorage.getItem("authUserRole")
    
    fun setModeSignin(signin: Boolean) {
        isSigninMode = signin
        if (signin) {
            signupPanel.style.display = "none"
            signinPanel.style.display = "block"
            titleEl.textContent = "Login"
        } else {
            signupPanel.style.display = "block"
            signinPanel.style.display = "none"
            titleEl.textContent = "Sign Up"
        }
    }
    toSignin.addEventListener("click", { _: Event -> setModeSignin(true) })
    toSignup.addEventListener("click", { _: Event -> setModeSignin(false) })
    
    // Apply role-based restrictions to signup roles
    // If no user is logged in (null), show all roles for initial signup
    applyRoleBasedSignupRestrictions(currentUserRole ?: "GUEST")

    // Role pickers
    val sgRoles = document.getElementsByClassName("sg-role")
    for (i in 0 until sgRoles.length) {
        val el = sgRoles.item(i) as HTMLElement
        el.addEventListener("click", { _: Event ->
            signupRole = el.getAttribute("data-role") ?: "VIEWER"
            for (j in 0 until sgRoles.length) (sgRoles.item(j) as HTMLElement).classList.remove("active")
            el.classList.add("active")
        })
    }
    val siRoles = document.getElementsByClassName("si-role")
    for (i in 0 until siRoles.length) {
        val el = siRoles.item(i) as HTMLElement
        el.addEventListener("click", { _: Event ->
            signinRole = el.getAttribute("data-role") ?: "VIEWER"
            for (j in 0 until siRoles.length) (siRoles.item(j) as HTMLElement).classList.remove("active")
            el.classList.add("active")
        })
    }

    fun doSignup() {
        val email = (document.getElementById("su_email") as HTMLInputElement).value
        val name = (document.getElementById("su_name") as HTMLInputElement).value
        val pass = (document.getElementById("su_pass") as HTMLInputElement).value
        val role = (document.getElementById("su_role") as HTMLSelectElement).value
        if (email.isBlank() || name.isBlank() || pass.isBlank() || role.isBlank()) {
            js("alert('Please fill in all fields')")
            return
        }
        val body = js("({method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({email:email,name:name,password:pass,role:role})})")
        window.fetch("/api/auth/signup", body)
            .then { it.json() }
            .then { resp ->
                val success = resp.asDynamic().success as Boolean?
                if (success == true) {
                    val data = resp.asDynamic().data
                    val token = data?.token as String?
                    val role = data?.role as String?
                    val nameResp = data?.name as String?
                    val userId = data?.id as Number?
                    if (token != null) {
                        window.localStorage.setItem("authToken", token)
                        if (role != null) window.localStorage.setItem("authUserRole", role)
                        if (nameResp != null) window.localStorage.setItem("authUserName", nameResp)
                        if (userId != null) window.localStorage.setItem("authUserId", userId.toString())
                        window.location.hash = ""
                        showPurchaseList()
                        updateUserInfoInSidebar()
                        applyRoleBasedRestrictions()
                    } else js("alert('Signup failed: Invalid response')")
                } else {
                    val message = resp.asDynamic().message as String? ?: "Signup failed"
                    js("alert(message)")
                }
            }
            .catch { _ -> js("alert('Network error during signup')") }
    }

    fun doSignin() {
        val email = (document.getElementById("si_email") as HTMLInputElement).value
        val pass = (document.getElementById("si_pass") as HTMLInputElement).value
        if (email.isBlank() || pass.isBlank()) {
            js("alert('Please fill in all fields')")
            return
        }
        val body = js("({method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({email:email,password:pass})})")
        window.fetch("/api/auth/login", body)
            .then { it.json() }
            .then { resp ->
                val token = resp.asDynamic().token as String?
                val role = resp.asDynamic().role as String?
                val nameResp = resp.asDynamic().name as String?
                val userId = resp.asDynamic().id as Number?
                if (token != null) {
                    window.localStorage.setItem("authToken", token)
                    if (role != null) window.localStorage.setItem("authUserRole", role)
                    if (nameResp != null) window.localStorage.setItem("authUserName", nameResp)
                    if (userId != null) window.localStorage.setItem("authUserId", userId.toString())
                    window.location.hash = ""
                    showPurchaseList()
                    updateUserInfoInSidebar()
                    applyRoleBasedRestrictions()
                } else js("alert('Login failed')")
            }
            .catch { _ -> js("alert('Network error during login')") }
    }
    document.getElementById("btn_signup")?.addEventListener("click", { _: Event -> doSignup() })
    document.getElementById("btn_signin")?.addEventListener("click", { _: Event -> doSignin() })
    
    // Close button handler
    document.getElementById("closeAuth")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/"
    })
}

fun removeHamburgerMenu() {
    document.getElementById("hamburgerBtn")?.let { button ->
        button.parentElement?.remove()
    }
}

fun logout() {
    // Clear authentication data
    window.localStorage.removeItem("authToken")
    window.localStorage.removeItem("authUserRole")
    window.localStorage.removeItem("authUserName")
    window.localStorage.removeItem("authUserId")
    
    // Close sidebar
    closeSidebar()
    
    // Redirect to login page
    window.location.hash = "#/"
}

fun updateUserInfoInSidebar() {
    val userInfoElement = document.getElementById("userInfo")
    val userName = window.localStorage.getItem("authUserName") ?: "User"
    val userRole = window.localStorage.getItem("authUserRole") ?: "VIEWER"
    
    userInfoElement?.innerHTML = """
        <div style="margin-bottom: 5px;">👤 $userName</div>
        <div style="font-size: 10px; color: #95a5a6;">Role: $userRole</div>
    """
}

// Role-based access control functions
fun getUserRole(): String {
    return window.localStorage.getItem("authUserRole") ?: "VIEWER"
}

fun isViewer(): Boolean = getUserRole() == "VIEWER"
fun isEditor(): Boolean = getUserRole() == "EDITOR" || getUserRole() == "ADMIN"
fun isAdmin(): Boolean = getUserRole() == "ADMIN"

fun applyRoleBasedRestrictions() {
    val role = getUserRole()
    
    // Hide/show elements based on role
    val newBtn = document.getElementById("newBtn") as HTMLElement?
    val importBtn = document.getElementById("importBtn") as HTMLElement?
    val userManagementBtn = document.getElementById("userManagementBtn") as HTMLElement?
    val clientAccountsBtn = document.getElementById("clientAccountsBtn") as HTMLElement?
    val roleRequestBtn = document.getElementById("roleRequestBtn") as HTMLElement?
    val rixoBtn = document.getElementById("rixoBtn") as HTMLElement?
    val rixoTransportBtn = document.getElementById("rixoTransportBtn") as HTMLElement?
    val clientAccountsQuickBtn = document.getElementById("clientAccountsQuickBtn") as HTMLElement?
    
    newBtn?.style?.display = if (isEditor()) "block" else "none"
    importBtn?.style?.display = if (isEditor()) "block" else "none"
    userManagementBtn?.style?.display = if (isAdmin()) "block" else "none"
    clientAccountsBtn?.style?.display = if (isAdmin()) "block" else "none"
    roleRequestBtn?.style?.display = if (!isAdmin()) "block" else "none" // Show for non-admin users
    // Keep invoice button hidden by default; it appears when rows are selected
    rixoBtn?.style?.display = "none"
    rixoTransportBtn?.style?.display = "none"
    clientAccountsQuickBtn?.style?.display = if (isAdmin()) "block" else "none"
    
    // Update sidebar button visibility
    updateSidebarForRole()
    
    console.log("Applied restrictions for role: $role")
}

fun updateSidebarForRole() {
    val newBtn = document.getElementById("newBtn") as HTMLElement?
    val importBtn = document.getElementById("importBtn") as HTMLElement?
    val rixoRequestBtn = document.getElementById("rixoRequestBtn") as HTMLElement?
    
    if (isViewer()) {
        newBtn?.style?.display = "none"
        importBtn?.style?.display = "none"
        rixoRequestBtn?.style?.display = "none"
    } else if (isEditor()) {
        newBtn?.style?.display = "block"
        importBtn?.style?.display = "block"
        rixoRequestBtn?.style?.display = "block"
    } else if (isAdmin()) {
        newBtn?.style?.display = "block"
        importBtn?.style?.display = "block"
        rixoRequestBtn?.style?.display = "block"
    }
}

fun showPurchaseList() {
    window.location.hash = "#/purchase"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <!-- Hamburger Menu Button (only on purchase list page) -->
        <div style="position: fixed; top: 20px; left: 20px; z-index: 10000; pointer-events: auto;">
            <button id="hamburgerBtn" style="background: none; border: none; cursor: pointer; padding: 10px; border-radius: 4px; background-color: #2c3e50; box-shadow: 0 2px 8px rgba(0,0,0,0.3);">
                <div style="width: 25px; height: 3px; background-color: white; margin: 3px 0; border-radius: 2px;"></div>
                <div style="width: 25px; height: 3px; background-color: white; margin: 3px 0; border-radius: 2px;"></div>
                <div style="width: 25px; height: 3px; background-color: white; margin: 3px 0; border-radius: 2px;"></div>
            </button>
        </div>
        
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
    } catch (e: dynamic) {}
    
    // Add hamburger button event listener only on purchase list page
    document.getElementById("hamburgerBtn")?.addEventListener("click", { _: Event ->
        openSidebar()
    })
    
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
    
    loadPurchases()
}
fun showUserManagementPage() {
    window.location.hash = "#/users"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>User Management</h2>
            <div style="margin-bottom: 20px;">
                <button id="addUserBtn" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New User</button>
                <button id="roleRequestsBtn" style="padding: 10px 20px; background-color: #ffc107; color: black; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Role Requests</button>
                <button id="purchaseListBtn" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Purchase List</button>
            </div>
            
            <!-- Role Requests Section -->
            <div id="roleRequestsSection" style="display: none; margin-bottom: 30px; border: 1px solid #ffc107; border-radius: 4px; padding: 20px; background-color: #fffbf0;">
                <h3 style="color: #856404; margin-top: 0;">Pending Role Requests</h3>
                <div id="roleRequestsTable">
                    <div style="text-align: center; color: #666; padding: 20px;">
                        Loading role requests...
                    </div>
                </div>
            </div>
            
            <!-- Users Section -->
            <div id="usersSection">
                <h3>All Users</h3>
                <div id="usersTable" style="margin-top: 20px;">
                    <div style="text-align: center; color: #666; padding: 40px;">
                        Loading users...
                    </div>
                </div>
            </div>
        </div>
    """
    
    // Add event listeners
    document.getElementById("addUserBtn")?.addEventListener("click", { _: Event ->
        showAddUserForm()
    })
    document.getElementById("roleRequestsBtn")?.addEventListener("click", { _: Event ->
        toggleRoleRequestsSection()
    })
    document.getElementById("purchaseListBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    loadUsers()
    loadRoleRequests()
}

fun showClientAccountsPage() {
    // Stay on current hash; this function renders list view
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>Client Accounts Management</h2>
            <div style="margin-bottom: 20px;">
                <button id="addClientBtn" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New Client</button>
                <button id="clientAlertsBtn" style="padding: 10px 20px; background-color: #ffc107; color: black; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">View Alerts</button>
                <button id="importClientsBtn" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Import CSV</button>
                <button id="exportClientsBtn" style="padding: 10px 20px; background-color: #17a2b8; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Export Data</button>
                
                <button id="purchaseListBtn" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Purchase List</button>
            </div>
            
            
            <!-- Client Alerts Section -->
            <div id="clientAlertsSection" style="display: none; margin-bottom: 30px; border: 1px solid #ffc107; border-radius: 4px; padding: 20px; background-color: #fffbf0;">
                <h3 style="color: #856404; margin-top: 0;">Client Alerts</h3>
                <div id="clientAlertsTable">
                    <div style="text-align: center; color: #666; padding: 20px;">
                        Loading client alerts...
                    </div>
                </div>
            </div>
            
            <!-- Client Accounts Section -->
            <div id="clientAccountsSection">
                <div style="display: grid; grid-template-columns: 1fr; gap: 20px;">
                    <!-- Client List (Left Side) -->
                    <div style="border: 1px solid #e9ecef; border-radius: 4px; padding: 20px;">
                        <h3>Client List</h3>
                        <div style="margin-bottom: 15px;">
                            <input id="clientSearchInput" type="text" placeholder="Search clients..." 
                                   style="width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div id="clientListTable" style="max-height: 400px; overflow-y: auto;">
                            <div style="text-align: center; color: #666; padding: 20px;">
                                Loading clients...
                            </div>
                        </div>
                    </div>
                    
                </div>
            </div>
        </div>
    """
    
    // Add event listeners
    document.getElementById("addClientBtn")?.addEventListener("click", { _: Event ->
        showAddClientForm()
    })
    
    document.getElementById("clientAlertsBtn")?.addEventListener("click", { _: Event ->
        toggleClientAlerts()
    })
    
    document.getElementById("importClientsBtn")?.addEventListener("click", { _: Event ->
        showImportClientsModal()
    })
    
    document.getElementById("exportClientsBtn")?.addEventListener("click", { _: Event ->
        exportClientsData()
    })
    
    
    
    document.getElementById("purchaseListBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    document.getElementById("clientSearchInput")?.addEventListener("input", { _: Event ->
        filterClients()
    })
    
    // Load clients
    loadClients()
}

fun toggleRoleRequestsSection() {
    val section = document.getElementById("roleRequestsSection") as HTMLElement
    val isVisible = section.style.display != "none"
    section.style.display = if (isVisible) "none" else "block"
    
    if (!isVisible) {
        loadRoleRequests()
    }
}

private fun loadUsers() {
    window.fetch("/api/users")
        .then { response ->
            if (response.ok) {
                response.json().then { users ->
                    displayUsers(users)
                }
            } else {
                showMessage("Failed to load users", "error")
            }
        }
        .catch { error ->
            showMessage("Failed to load users: $error", "error")
        }
}
private fun displayUsers(users: dynamic) {
    val table = document.getElementById("usersTable")!!
    
    if (js("users.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No users found.
            </div>
        """
        return
    }
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <table style="width: 100%; border-collapse: collapse;">
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 50px;"></th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Email</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Name</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Role</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6;">Created</th>
                </tr>
            </thead>
            <tbody>
    """)
    
    val usersArray = users as Array<dynamic>
    for (i in 0 until usersArray.size) {
        val user = usersArray[i]
        tableHTML.append("""
            <tr style="border-bottom: 1px solid #f0f0f0;" data-user-id="${user.id}">
                <td style="padding: 12px; text-align: center;">
                    <button class="edit-user-btn" data-id="${user.id}" style="width: 32px; height: 32px; background-color: #007bff; color: white; border: none; border-radius: 50%; cursor: pointer; display: flex; align-items: center; justify-content: center; font-size: 14px;" title="Edit User">
                        ✏️
                    </button>
                </td>
                <td style="padding: 12px;">${user.email}</td>
                <td style="padding: 12px;">${user.name}</td>
                <td style="padding: 12px;">
                    <span style="padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; 
                        background-color: ${getRoleColor(user.role)}; color: white;">
                        ${user.role}
                    </span>
                </td>
                <td style="padding: 12px;">${user.createdAt}</td>
            </tr>
        """)
    }
    
    tableHTML.append("""
            </tbody>
        </table>
    """)
    
    table.innerHTML = tableHTML.toString()
    
    // Add event listeners for edit buttons
    val editButtons = document.querySelectorAll(".edit-user-btn")
    for (i in 0 until editButtons.length) {
        val button = editButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val btn = event.currentTarget as HTMLElement
            val id = btn.getAttribute("data-id")
            editUser(id?.toLongOrNull())
        })
    }
}

private fun getRoleColor(role: String): String {
    return when (role) {
        "ADMIN" -> "#dc3545"
        "EDITOR" -> "#28a745"
        "VIEWER" -> "#6c757d"
        else -> "#6c757d"
    }
}
private fun showAddUserForm() {
    window.location.hash = "#/users/add"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 600px; margin: 0 auto;">
            <div style="display: flex; align-items: center; margin-bottom: 30px;">
                <button id="backToUsersBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 15px;">← Back</button>
                <h2 style="margin: 0; color: #333;">Add New User</h2>
            </div>
            
            <form id="addUserForm" style="background-color: #f8f9fa; padding: 25px; border-radius: 8px; border: 1px solid #e9ecef;">
                <div style="margin-bottom: 20px;">
                    <label for="userEmail" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Email Address *</label>
                    <input type="email" id="userEmail" required style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box;" placeholder="Enter email address">
                    <div id="emailError" style="color: #dc3545; font-size: 12px; margin-top: 4px; display: none;"></div>
                </div>
                
                <div style="margin-bottom: 20px;">
                    <label for="userName" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Full Name *</label>
                    <input type="text" id="userName" required style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box;" placeholder="Enter full name">
                    <div id="nameError" style="color: #dc3545; font-size: 12px; margin-top: 4px; display: none;"></div>
                </div>
                
                <div style="margin-bottom: 20px;">
                    <label for="userPassword" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Password *</label>
                    <input type="password" id="userPassword" required style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box;" placeholder="Enter password (min 6 characters)">
                    <div id="passwordError" style="color: #dc3545; font-size: 12px; margin-top: 4px; display: none;"></div>
                </div>
                
                <div style="margin-bottom: 25px;">
                    <label for="userRole" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Role *</label>
                    <select id="userRole" required style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box; background-color: white;">
                        <option value="">Select a role</option>
                        <option value="VIEWER">Viewer - Can view data only</option>
                        <option value="EDITOR">Editor - Can view and edit data</option>
                        <option value="ADMIN">Admin - Full access including user management</option>
                    </select>
                    <div id="roleError" style="color: #dc3545; font-size: 12px; margin-top: 4px; display: none;"></div>
                </div>
                
                <div style="display: flex; gap: 15px; justify-content: flex-end;">
                    <button type="button" id="cancelAddUserBtn" style="padding: 12px 24px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button type="submit" id="submitAddUserBtn" style="padding: 12px 24px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Create User</button>
                </div>
            </form>
        </div>
    """
    
    // Add event listeners
    document.getElementById("backToUsersBtn")?.addEventListener("click", { _: Event ->
        showUserManagementPage()
    })
    
    document.getElementById("cancelAddUserBtn")?.addEventListener("click", { _: Event ->
        showUserManagementPage()
    })
    
    document.getElementById("addUserForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        createNewUser()
    })
    
    // Add real-time validation
    document.getElementById("userEmail")?.addEventListener("blur", { _: Event ->
        validateEmail()
    })
    
    document.getElementById("userName")?.addEventListener("blur", { _: Event ->
        validateName()
    })
    
    document.getElementById("userPassword")?.addEventListener("blur", { _: Event ->
        validatePassword()
    })
    
    document.getElementById("userRole")?.addEventListener("change", { _: Event ->
        validateRole()
    })
}

private fun showEditUserPage(userId: Long) {
    window.location.hash = "#/users/edit/$userId"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 600px; margin: 0 auto;">
            <div style="display: flex; align-items: center; margin-bottom: 30px;">
                <button id="backToUsersBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 15px;">← Back</button>
                <h2 style="margin: 0; color: #333;">Edit User</h2>
            </div>
            
            <div id="editUserContent" style="background-color: #f8f9fa; padding: 25px; border-radius: 8px; border: 1px solid #e9ecef;">
                <div style="text-align: center; color: #666; padding: 40px;">
                    Loading user information...
                </div>
            </div>
        </div>
    """
    
    // Add event listener for back button
    document.getElementById("backToUsersBtn")?.addEventListener("click", { _: Event ->
        showUserManagementPage()
    })
    
    // Load user data
    loadUserForEdit(userId)
}

private fun loadUserForEdit(userId: Long) {
    window.fetch("/api/users/$userId")
        .then { response ->
            if (response.ok) {
                response.json().then { user ->
                    displayEditUserForm(user)
                }
            } else {
                showMessage("Failed to load user information", "error")
                showUserManagementPage()
            }
        }
        .catch { error ->
            showMessage("Failed to load user information: $error", "error")
            showUserManagementPage()
        }
}

private fun displayEditUserForm(user: dynamic) {
    val editUserContent = document.getElementById("editUserContent")!!
    editUserContent.innerHTML = """
        <form id="editUserForm">
            <div style="margin-bottom: 20px;">
                <label for="editUserEmail" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Email Address</label>
                <input type="email" id="editUserEmail" value="${user.email}" readonly style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box; background-color: #e9ecef; color: #6c757d;">
                <div style="color: #6c757d; font-size: 12px; margin-top: 4px;">Email cannot be changed</div>
            </div>
            
            <div style="margin-bottom: 20px;">
                <label for="editUserName" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Full Name</label>
                <input type="text" id="editUserName" value="${user.name}" readonly style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box; background-color: #e9ecef; color: #6c757d;">
                <div style="color: #6c757d; font-size: 12px; margin-top: 4px;">Name cannot be changed</div>
            </div>
            
            <div style="margin-bottom: 25px;">
                <label for="editUserRole" style="display: block; margin-bottom: 8px; font-weight: 600; color: #495057;">Role *</label>
                <select id="editUserRole" required style="width: 100%; padding: 12px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px; box-sizing: border-box; background-color: white;">
                    <option value="VIEWER" ${if (user.role == "VIEWER") "selected" else ""}>Viewer - Can view data only</option>
                    <option value="EDITOR" ${if (user.role == "EDITOR") "selected" else ""}>Editor - Can view and edit data</option>
                    <option value="ADMIN" ${if (user.role == "ADMIN") "selected" else ""}>Admin - Full access including user management</option>
                </select>
                <div id="editRoleError" style="color: #dc3545; font-size: 12px; margin-top: 4px; display: none;"></div>
            </div>
            
            <div style="display: flex; gap: 15px; justify-content: space-between;">
                <button type="button" id="deleteUserBtn" style="padding: 12px 24px; background-color: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Delete User</button>
                <div style="display: flex; gap: 15px;">
                    <button type="button" id="cancelEditUserBtn" style="padding: 12px 24px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button type="submit" id="submitEditUserBtn" style="padding: 12px 24px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Update Role</button>
                </div>
            </div>
        </form>
    """
    
    // Add event listeners
    document.getElementById("cancelEditUserBtn")?.addEventListener("click", { _: Event ->
        showUserManagementPage()
    })
    
    document.getElementById("deleteUserBtn")?.addEventListener("click", { _: Event ->
        deleteUser(user.id)
    })
    
    document.getElementById("editUserForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        updateUserRole(user.id)
    })
    
    // Store user ID for later use
    js("window._editingUserId = user.id")
}

private fun updateUserRole(userId: Long) {
    val roleSelect = document.getElementById("editUserRole") as HTMLSelectElement
    val submitBtn = document.getElementById("submitEditUserBtn") as HTMLButtonElement
    val roleError = document.getElementById("editRoleError") as HTMLElement
    
    val newRole = roleSelect.value
    
    if (newRole.isEmpty()) {
        roleError.textContent = "Please select a role"
        roleError.style.display = "block"
        roleSelect.style.borderColor = "#dc3545"
        return
    } else {
        roleError.style.display = "none"
        roleSelect.style.borderColor = "#ced4da"
    }
    
    // Disable submit button to prevent double submission
    submitBtn.disabled = true
    submitBtn.textContent = "Updating..."
    
    val updateData = js("""({
        role: newRole
    })""")
    
    val requestOptions = js("""({
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(updateData)
    })""")
    
    window.fetch("/api/users/$userId", requestOptions)
        .then { response ->
            if (response.ok) {
                showMessage("User role updated successfully!", "success")
                // Navigate back to user management
                window.setTimeout({ showUserManagementPage() }, 1500)
            } else {
                response.text().then { errorText ->
                    showMessage("Failed to update user role: $errorText", "error")
                }
            }
        }
        .catch { error ->
            showMessage("Failed to update user role: $error", "error")
        }
        .finally {
            // Re-enable submit button
            submitBtn.disabled = false
            submitBtn.textContent = "Update Role"
        }
}

private fun validateEmail(): Boolean {
    val emailInput = document.getElementById("userEmail") as HTMLInputElement
    val emailError = document.getElementById("emailError") as HTMLElement
    val email = emailInput.value.trim()
    
    val emailRegex = js("""/^[^\s@]+@[^\s@]+\.[^\s@]+$/""")
    
    if (email.isEmpty()) {
        emailError.textContent = "Email is required"
        emailError.style.display = "block"
        emailInput.style.borderColor = "#dc3545"
        return false
    } else if (!emailRegex.test(email)) {
        emailError.textContent = "Please enter a valid email address"
        emailError.style.display = "block"
        emailInput.style.borderColor = "#dc3545"
        return false
    } else {
        emailError.style.display = "none"
        emailInput.style.borderColor = "#ced4da"
        return true
    }
}
private fun validateName(): Boolean {
    val nameInput = document.getElementById("userName") as HTMLInputElement
    val nameError = document.getElementById("nameError") as HTMLElement
    val name = nameInput.value.trim()
    
    if (name.isEmpty()) {
        nameError.textContent = "Name is required"
        nameError.style.display = "block"
        nameInput.style.borderColor = "#dc3545"
        return false
    } else if (name.length < 2) {
        nameError.textContent = "Name must be at least 2 characters long"
        nameError.style.display = "block"
        nameInput.style.borderColor = "#dc3545"
        return false
    } else {
        nameError.style.display = "none"
        nameInput.style.borderColor = "#ced4da"
        return true
    }
}

private fun validatePassword(): Boolean {
    val passwordInput = document.getElementById("userPassword") as HTMLInputElement
    val passwordError = document.getElementById("passwordError") as HTMLElement
    val password = passwordInput.value
    
    if (password.isEmpty()) {
        passwordError.textContent = "Password is required"
        passwordError.style.display = "block"
        passwordInput.style.borderColor = "#dc3545"
        return false
    } else if (password.length < 6) {
        passwordError.textContent = "Password must be at least 6 characters long"
        passwordError.style.display = "block"
        passwordInput.style.borderColor = "#dc3545"
        return false
    } else {
        passwordError.style.display = "none"
        passwordInput.style.borderColor = "#ced4da"
        return true
    }
}

private fun validateRole(): Boolean {
    val roleSelect = document.getElementById("userRole") as HTMLSelectElement
    val roleError = document.getElementById("roleError") as HTMLElement
    val role = roleSelect.value
    
    if (role.isEmpty()) {
        roleError.textContent = "Please select a role"
        roleError.style.display = "block"
        roleSelect.style.borderColor = "#dc3545"
        return false
    } else {
        roleError.style.display = "none"
        roleSelect.style.borderColor = "#ced4da"
        return true
    }
}

private fun createNewUser() {
    // Validate all fields
    val isEmailValid = validateEmail()
    val isNameValid = validateName()
    val isPasswordValid = validatePassword()
    val isRoleValid = validateRole()
    
    if (!isEmailValid || !isNameValid || !isPasswordValid || !isRoleValid) {
        showMessage("Please fix the validation errors before submitting", "error")
        return
    }
    
    val emailInput = document.getElementById("userEmail") as HTMLInputElement
    val nameInput = document.getElementById("userName") as HTMLInputElement
    val passwordInput = document.getElementById("userPassword") as HTMLInputElement
    val roleSelect = document.getElementById("userRole") as HTMLSelectElement
    val submitBtn = document.getElementById("submitAddUserBtn") as HTMLButtonElement
    
    // Disable submit button to prevent double submission
    submitBtn.disabled = true
    submitBtn.textContent = "Creating User..."
    
    val userData = js("""({
        email: emailInput.value.trim(),
        name: nameInput.value.trim(),
        password: passwordInput.value,
        role: roleSelect.value
    })""")
    
    val requestOptions = js("""({
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(userData)
    })""")
    
    window.fetch("/api/users", requestOptions)
        .then { response ->
            if (response.ok) {
                showMessage("User created successfully!", "success")
                // Clear form
                emailInput.value = ""
                nameInput.value = ""
                passwordInput.value = ""
                roleSelect.value = ""
                // Navigate back to user management
                window.setTimeout({ showUserManagementPage() }, 1500)
            } else {
                response.text().then { errorText ->
                    showMessage("Failed to create user: $errorText", "error")
                }
            }
        }
        .catch { error ->
            showMessage("Failed to create user: $error", "error")
        }
        .finally {
            // Re-enable submit button
            submitBtn.disabled = false
            submitBtn.textContent = "Create User"
        }
}

private fun editUser(id: Long?) {
    if (id == null) return
    window.location.hash = "#/users/edit/$id"
}

private fun deleteUser(id: Long?) {
    if (id == null) return
    if (js("confirm('Are you sure you want to delete this user? This action cannot be undone.')")) {
        val deleteOptions = js("""({method: 'DELETE'})""")
        window.fetch("/api/users/$id", deleteOptions)
            .then { response ->
                if (response.ok) {
                    showMessage("User deleted successfully", "success")
                    // Navigate back to user management page
                    window.setTimeout({ showUserManagementPage() }, 1500)
                } else {
                    showMessage("Failed to delete user", "error")
                }
            }
            .catch { error ->
                showMessage("Failed to delete user: $error", "error")
            }
    }
}
fun createAddFormHTML(): String {
    return """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>Add New Purchase</h2>
            <form id="addForm">
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Basic Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Purchase Date</label>
                        <div style="position:relative;">
                            <input type="date" id="date" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <span id="dateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                    <div>
                    </div>
                    <div>
                        <label>Chassis *</label>
                        <input type="text" id="chassis" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Production Date</label>
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
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Supplier Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Auction No</label>
                        <input type="text" id="auctionNo" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Supplier Name *</label>
                        <select id="auctionName" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" onchange="handleAuctionNameChange(this.value)">
                            <option value="">Select Supplier Name</option>
                        </select>
                    </div>
                    <div>
                        <label>Venue ID</label>
                        <select id="venueId" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Venue ID</option>
                        </select>
                    </div>
                    <div>
                        <label>Stock Location</label>
                        <select id="stockLocation" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Stock Location</option>
                        </select>
                    </div>
                    <div>
                        <label>Rixo Company</label>
                        <select id="rixoCompany" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" onchange="handleRixoCompanyChange(this.value)">
                            <option value="">Select Rixo Company</option>
                        </select>
                    </div>
                    <div>
                        <label>Shipment size</label>
                        <select id="typeOfVehicle" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Shipment size</option>
                            <option value="Car">Car</option>
                            <option value="Truck">Truck</option>
                        </select>
                    </div>
                    <div>
                        <label>Rixo Price</label>
                        <div style="position: relative;">
                            <input type="text" id="rixoPrice" placeholder="Enter or select Rixo Price" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <select id="rixoPriceDropdown" style="position: absolute; top: 0; right: 0; width: 30px; height: 100%; border: none; background: #f5f5f5; cursor: pointer;" onchange="selectRixoPrice(this.value)">
                                <option value="">▼</option>
                            </select>
                        </div>
                    </div>
                    <div>
                        <label>Client Name</label>
                        <input type="text" id="clientName" placeholder="Enter client name" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Target Country</label>
                        <input type="text" id="country" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Rixo Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Rixo Requested</label>
                        <div style="display: flex; gap: 16px; align-items: center; margin-top: 8px;">
                            <label class="checkwrap">
                                <input type="radio" name="rixoRequested" value="TRUE">
                                <span class="checkmark"></span>
                                TRUE
                            </label>
                            <label class="checkwrap">
                                <input type="radio" name="rixoRequested" value="FALSE">
                                <span class="checkmark"></span>
                                FALSE
                            </label>
                        </div>
                    </div>
                    <div>
                        <label>Rixo Confirmed</label>
                        <div style="display: flex; gap: 16px; align-items: center; margin-top: 8px;">
                            <label class="checkwrap">
                                <input type="radio" name="rixoConfirmed" value="TRUE">
                                <span class="checkmark"></span>
                                TRUE
                            </label>
                            <label class="checkwrap">
                                <input type="radio" name="rixoConfirmed" value="FALSE">
                                <span class="checkmark"></span>
                                FALSE
                            </label>
                        </div>
                    </div>
                        </div>

                <!-- SHAKEN Checkbox -->
                <div style="margin: 20px 0 10px 0;">
                    <label style="display: flex; align-items: center; gap: 8px; font-weight: 600; color: #374151; cursor: pointer;">
                        <input type="checkbox" id="shakenCheckbox" style="width: 18px; height: 18px; accent-color: #007bff;">
                        SHAKEN
                    </label>
                </div>

                <!-- Number Cut Information Section (initially hidden) -->
                <div id="numberCutSection" style="display: none;">
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Number Cut Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12px; margin-bottom: 20px; align-items: end;">
                    <div>
                        <label>Place Name (Japanese)</label>
                        <select id="numberCutPlace" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Place</option>
                            <option value="札幌">札幌 (Sapporo)</option>
                            <option value="函館">函館 (Hakodate)</option>
                            <option value="旭川">旭川 (Asahikawa)</option>
                            <option value="室蘭">室蘭 (Muroran)</option>
                            <option value="釧路">釧路 (Kushiro)</option>
                            <option value="帯広">帯広 (Obihiro)</option>
                            <option value="十勝">十勝 (Tokachi)</option>
                            <option value="北見">北見 (Kitami)</option>
                            <option value="知床">知床 (Shiretoko)</option>
                            <option value="苫小牧">苫小牧 (Tomakomai)</option>
                            <option value="青森">青森 (Aomori)</option>
                            <option value="弘前">弘前 (Hirosaki)</option>
                            <option value="岩手">岩手 (Iwate)</option>
                            <option value="盛岡">盛岡 (Morioka)</option>
                            <option value="平泉">平泉 (Hiraizumi)</option>
                            <option value="宮城">宮城 (Miyagi)</option>
                            <option value="仙台">仙台 (Sendai)</option>
                            <option value="八戸">八戸 (Hachinohe)</option>
                            <option value="秋田">秋田 (Akita)</option>
                            <option value="山形">山形 (Yamagata)</option>
                            <option value="福島">福島 (Fukushima)</option>
                            <option value="茨城">茨城 (Ibaraki)</option>
                            <option value="栃木">栃木 (Tochigi)</option>
                            <option value="群馬">群馬 (Gunma)</option>
                            <option value="埼玉">埼玉 (Saitama)</option>
                            <option value="千葉">千葉 (Chiba)</option>
                            <option value="東京">東京 (Tokyo)</option>
                            <option value="神奈川">神奈川 (Kanagawa)</option>
                            <option value="新潟">新潟 (Niigata)</option>
                            <option value="富山">富山 (Toyama)</option>
                            <option value="石川">石川 (Ishikawa)</option>
                            <option value="福井">福井 (Fukui)</option>
                            <option value="山梨">山梨 (Yamanashi)</option>
                            <option value="長野">長野 (Nagano)</option>
                            <option value="岐阜">岐阜 (Gifu)</option>
                            <option value="静岡">静岡 (Shizuoka)</option>
                            <option value="愛知">愛知 (Aichi)</option>
                            <option value="三重">三重 (Mie)</option>
                            <option value="滋賀">滋賀 (Shiga)</option>
                            <option value="京都">京都 (Kyoto)</option>
                            <option value="大阪">大阪 (Osaka)</option>
                            <option value="兵庫">兵庫 (Hyogo)</option>
                            <option value="奈良">奈良 (Nara)</option>
                            <option value="和歌山">和歌山 (Wakayama)</option>
                            <option value="鳥取">鳥取 (Tottori)</option>
                            <option value="島根">島根 (Shimane)</option>
                            <option value="岡山">岡山 (Okayama)</option>
                            <option value="広島">広島 (Hiroshima)</option>
                            <option value="山口">山口 (Yamaguchi)</option>
                            <option value="徳島">徳島 (Tokushima)</option>
                            <option value="香川">香川 (Kagawa)</option>
                            <option value="愛媛">愛媛 (Ehime)</option>
                            <option value="高知">高知 (Kochi)</option>
                            <option value="福岡">福岡 (Fukuoka)</option>
                            <option value="佐賀">佐賀 (Saga)</option>
                            <option value="長崎">長崎 (Nagasaki)</option>
                            <option value="熊本">熊本 (Kumamoto)</option>
                            <option value="大分">大分 (Oita)</option>
                            <option value="宮崎">宮崎 (Miyazaki)</option>
                            <option value="鹿児島">鹿児島 (Kagoshima)</option>
                            <option value="沖縄">沖縄 (Okinawa)</option>
                        </select>
                    </div>
                    <div>
                        <label>Number (English)</label>
                        <input type="number" id="numberCutNumber1" placeholder="Enter number" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Hiragana Character</label>
                        <select id="numberCutHiragana" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Character</option>
                            <option value="あ">あ (a)</option>
                            <option value="い">い (i)</option>
                            <option value="う">う (u)</option>
                            <option value="え">え (e)</option>
                            <option value="お">お (o)</option>
                            <option value="か">か (ka)</option>
                            <option value="き">き (ki)</option>
                            <option value="く">く (ku)</option>
                            <option value="け">け (ke)</option>
                            <option value="こ">こ (ko)</option>
                            <option value="さ">さ (sa)</option>
                            <option value="し">し (shi)</option>
                            <option value="す">す (su)</option>
                            <option value="せ">せ (se)</option>
                            <option value="そ">そ (so)</option>
                            <option value="た">た (ta)</option>
                            <option value="ち">ち (chi)</option>
                            <option value="つ">つ (tsu)</option>
                            <option value="て">て (te)</option>
                            <option value="と">と (to)</option>
                            <option value="な">な (na)</option>
                            <option value="に">に (ni)</option>
                            <option value="ぬ">ぬ (nu)</option>
                            <option value="ね">ね (ne)</option>
                            <option value="の">の (no)</option>
                            <option value="は">は (ha)</option>
                            <option value="ひ">ひ (hi)</option>
                            <option value="ふ">ふ (fu)</option>
                            <option value="へ">へ (he)</option>
                            <option value="ほ">ほ (ho)</option>
                            <option value="ま">ま (ma)</option>
                            <option value="み">み (mi)</option>
                            <option value="む">む (mu)</option>
                            <option value="め">め (me)</option>
                            <option value="も">も (mo)</option>
                            <option value="や">や (ya)</option>
                            <option value="ゆ">ゆ (yu)</option>
                            <option value="よ">よ (yo)</option>
                            <option value="ら">ら (ra)</option>
                            <option value="り">り (ri)</option>
                            <option value="る">る (ru)</option>
                            <option value="れ">れ (re)</option>
                            <option value="ろ">ろ (ro)</option>
                            <option value="わ">わ (wa)</option>
                            <option value="ゐ">ゐ (wi)</option>
                            <option value="ゑ">ゑ (we)</option>
                            <option value="を">を (wo)</option>
                            <option value="ん">ん (n)</option>
                        </select>
                    </div>
                    <div>
                        <label>Number (English)</label>
                        <input type="number" id="numberCutNumber2" placeholder="Enter number" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                <div style="margin-bottom: 20px;">
                    <label>Generated Number Cut String:</label>
                    <input type="text" id="numberCutString" readonly style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; background-color: #f9f9f9;" placeholder="Will be generated automatically">
                </div>
                </div> <!-- End of numberCutSection -->

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
                        <label>Tax Total</label>
                        <div style="display: flex; gap: 8px;">
                            <input type="text" id="taxTotal" style="flex: 1; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="Calculated tax amount">
                            <button type="button" id="calculateTaxBtn" style="padding: 8px 12px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; white-space: nowrap;">10% Tax</button>
                        </div>
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

// JavaScript functions for Rixo dropdown functionality
fun setEditFormValuesFromKotlin(purchaseData: dynamic) {
    val dataToPass = purchaseData
    js("""
        try {
            if (window.setEditFormValues && typeof window.setEditFormValues === 'function') {
                window.setEditFormValues(dataToPass);
            } else {
                console.log('setEditFormValues not available yet, skipping...');
            }
        } catch (e) {
            console.log('Error calling setEditFormValues:', e);
        }
    """)
}
fun setupRixoDropdowns() {
    js("""
        // Dynamic dropdown system with hierarchical filtering
        function populateDropdownOptions() {
            if (typeof window.rixoPriceMapping === 'undefined') {
                console.log('Rixo price mapping not loaded yet, retrying in 500ms...');
                setTimeout(populateDropdownOptions, 500);
                return;
            }
            
            console.log('Rixo price mapping loaded, found', Object.keys(window.rixoPriceMapping).length, 'auctions');
            
            // Get all unique auction names
            var auctionNames = Object.keys(window.rixoPriceMapping);
            
            // Populate Auction house dropdown
            var auctionSelect = document.getElementById('auctionName');
            var editAuctionSelect = document.getElementById('editAuctionName');
            if (auctionSelect) {
                auctionSelect.innerHTML = '<option value="">Select Supplier Name</option>';
                auctionNames.forEach(function(auction) {
                    auctionSelect.innerHTML += '<option value="' + auction + '">' + auction + '</option>';
                });
            }
            if (editAuctionSelect) {
                editAuctionSelect.innerHTML = '<option value="">Select Supplier Name</option>';
                auctionNames.forEach(function(auction) {
                    editAuctionSelect.innerHTML += '<option value="' + auction + '">' + auction + '</option>';
                });
            }
            
            // Clear other dropdowns initially
            clearDropdown('typeOfVehicle');
            clearDropdown('editTypeOfVehicle');
            clearDropdown('stockLocation');
            clearDropdown('editStockLocation');
            clearDropdown('rixoCompany');
            clearDropdown('editRixoCompany');
            clearDropdown('rixoPrice');
            clearDropdown('editRixoPrice');
        }
        
        function clearDropdown(elementId) {
            var select = document.getElementById(elementId);
            if (select) {
                select.innerHTML = '<option value="">Select ' + getFieldLabel(elementId) + '</option>';
            }
        }
        
        function getFieldLabel(elementId) {
            switch(elementId) {
                case 'typeOfVehicle':
                case 'editTypeOfVehicle':
                    return 'Shipment size';
                case 'stockLocation':
                case 'editStockLocation':
                    return 'Stock Location';
                case 'rixoCompany':
                case 'editRixoCompany':
                    return 'Rixo Company';
                case 'rixoPrice':
                case 'editRixoPrice':
                    return 'Rixo Price';
                default:
                    return 'Option';
            }
        }
        
        function updateDropdownOptions(auctionName, typeOfVehicle, stockLocation, rixoCompany) {
            console.log('🔄 NEW updateDropdownOptions called - CACHE BUST 1736384000:', auctionName, typeOfVehicle, stockLocation, rixoCompany);
            
            // Call the mapping file's autoSelectRelatedFields function
            if (window.autoSelectRelatedFields) {
                console.log('🔄 Calling autoSelectRelatedFields - CACHE BUST 1736384000');
                window.autoSelectRelatedFields(auctionName, 'auctionHouse', auctionName);
            } else {
                console.log('❌ autoSelectRelatedFields not available - CACHE BUST 1736384000');
            }
        }
        
        function updateDropdown(elementId, editElementId, options) {
            // Handle rixo price fields specially (they are input + dropdown)
            if (elementId === 'rixoPrice' || elementId === 'editRixoPrice') {
                var dropdown = document.getElementById(elementId + 'Dropdown');
                var editDropdown = document.getElementById(editElementId + 'Dropdown');
                
                if (dropdown) {
                    dropdown.innerHTML = '<option value="">▼</option>';
                    var uniqueOptions = window.getUniqueValues(options);
                    uniqueOptions.forEach(function(option) {
                        dropdown.innerHTML += '<option value="' + option + '">' + option + '</option>';
                    });
                }
                
                if (editDropdown) {
                    editDropdown.innerHTML = '<option value="">▼</option>';
                    var uniqueOptions = window.getUniqueValues(options);
                    uniqueOptions.forEach(function(option) {
                        editDropdown.innerHTML += '<option value="' + option + '">' + option + '</option>';
                    });
                }
            } else {
                // Handle regular dropdowns
                var select = document.getElementById(elementId);
                var editSelect = document.getElementById(editElementId);
                
                if (select) {
                    select.innerHTML = '<option value="">Select ' + getFieldLabel(elementId) + '</option>';
                    var uniqueOptions = window.getUniqueValues(options);
                    uniqueOptions.forEach(function(option) {
                        select.innerHTML += '<option value="' + option + '">' + option + '</option>';
                    });
                }
                
                if (editSelect) {
                    editSelect.innerHTML = '<option value="">Select ' + getFieldLabel(editElementId) + '</option>';
                    var uniqueOptions = window.getUniqueValues(options);
                    uniqueOptions.forEach(function(option) {
                        editSelect.innerHTML += '<option value="' + option + '">' + option + '</option>';
                    });
                }
            }
        }
        
        function autoSelectSingleOption(elementId, editElementId, options) {
            var uniqueOptions = window.getUniqueValues(options);
            console.log('Auto-selecting for', elementId, ':', uniqueOptions);
            
            if (uniqueOptions.length === 1) {
                console.log('Auto-selecting single option:', uniqueOptions[0], 'for', elementId);
                
                // Handle rixo price fields specially (they are input + dropdown)
                if (elementId === 'rixoPrice' || elementId === 'editRixoPrice') {
                    var input = document.getElementById(elementId);
                    var editInput = document.getElementById(editElementId);
                    
                    if (input) {
                        input.value = uniqueOptions[0];
                        console.log('Set rixo price input value:', uniqueOptions[0]);
                    }
                    if (editInput) {
                        editInput.value = uniqueOptions[0];
                        console.log('Set edit rixo price input value:', uniqueOptions[0]);
                    }
                } else {
                    // Handle regular dropdowns
                    var select = document.getElementById(elementId);
                    var editSelect = document.getElementById(editElementId);
                    
                    if (select) {
                        select.value = uniqueOptions[0];
                        console.log('Set dropdown value for', elementId, ':', uniqueOptions[0]);
                        
                        // Trigger change event to ensure other listeners are notified
                        var changeEvent = new Event('change', { bubbles: true });
                        select.dispatchEvent(changeEvent);
                    }
                    if (editSelect) {
                        editSelect.value = uniqueOptions[0];
                        console.log('Set edit dropdown value for', editElementId, ':', uniqueOptions[0]);
                        
                        // Trigger change event to ensure other listeners are notified
                        var changeEvent = new Event('change', { bubbles: true });
                        editSelect.dispatchEvent(changeEvent);
                    }
                }
            } else {
                console.log('Multiple options available for', elementId, ':', uniqueOptions);
            }
        }
        
        // Handle auction name change
        function handleAuctionNameChange(auctionName) {
            console.log('Auction name changed to:', auctionName);
            
            if (!auctionName || typeof window.rixoPriceMapping === 'undefined') {
                return;
            }
            
                    var auctionData = window.rixoPriceMapping[auctionName];
            if (!auctionData) {
                console.log('No data found for auction:', auctionName);
                return;
            }
            
            // Call the new hierarchical filtering logic
            window.autoSelectRelatedFields(auctionName, 'auctionHouse', auctionName);
        }
        
        // Handle type of vehicle change
        function handleTypeOfVehicleChange(auctionName, typeOfVehicle) {
            console.log('Shipment size changed to:', typeOfVehicle);
            // Auto-select related fields based on type of vehicle
            if (window.autoSelectRelatedFields) {
                window.autoSelectRelatedFields(auctionName, 'typeOfVehicle', typeOfVehicle);
            }
        }
        
        // Handle rixo company change
        function handleRixoCompanyChange(rixoCompany) {
            console.log('Rixo company changed to:', rixoCompany);
            
            var auctionName = document.getElementById('auctionName') ? document.getElementById('auctionName').value : null;
            if (auctionName) {
                window.autoSelectRelatedFields(auctionName, 'rixoCompany', rixoCompany);
            }
        }
        
        // Handle edit form rixo company change
        function handleEditRixoCompanyChange(rixoCompany) {
            console.log('Edit Rixo company changed to:', rixoCompany);
            
            var auctionName = document.getElementById('editAuctionName') ? document.getElementById('editAuctionName').value : null;
            if (auctionName) {
                window.autoSelectRelatedFields(auctionName, 'rixoCompany', rixoCompany);
            }
        }
        
        // Helper functions for rixo price selection
        function selectRixoPrice(value) {
            if (value) {
                document.getElementById('rixoPrice').value = value;
            }
        }
        
        function selectEditRixoPrice(value) {
            if (value) {
                document.getElementById('editRixoPrice').value = value;
            }
        }
        
        // Make functions globally available
        window.handleAuctionNameChange = handleAuctionNameChange;
        window.handleTypeOfVehicleChange = handleTypeOfVehicleChange;
        window.handleRixoCompanyChange = handleRixoCompanyChange;
        window.handleEditRixoCompanyChange = handleEditRixoCompanyChange;
        window.populateDropdownOptions = populateDropdownOptions;
        window.updateDropdownOptions = updateDropdownOptions;
        window.selectRixoPrice = selectRixoPrice;
        window.selectEditRixoPrice = selectEditRixoPrice;
        
        // Handle car picture upload
        function handleCarPictureUpload(input) {
            var files = input.files;
            if (files.length === 0) return;
            
            console.log('📷 Uploading', files.length, 'car pictures');
            
            // Show progress bar
            var progressDiv = document.getElementById('uploadProgress');
            var progressBar = document.getElementById('progressBar');
            var progressText = document.getElementById('progressText');
            var previewDiv = document.getElementById('carPicturePreview');
            
            progressDiv.style.display = 'block';
            progressBar.style.width = '0%';
            progressText.textContent = 'Preparing upload...';
            
            var uploadedCount = 0;
            var totalFiles = files.length;
            
            // Process each file
            for (var i = 0; i < files.length; i++) {
                var file = files[i];
                var index = i;
                
                // Validate file type
                if (!file.type.startsWith('image/')) {
                    console.warn('Skipping non-image file:', file.name);
                    continue;
                }
                
                // Validate file size (max 5MB)
                if (file.size > 5 * 1024 * 1024) {
                    alert('File ' + file.name + ' is too large. Maximum size is 5MB.');
                    continue;
                }
                
                // Create preview
                var reader = new FileReader();
                reader.onload = function(e) {
                    var previewItem = document.createElement('div');
                    previewItem.style.cssText = 'position: relative; border: 1px solid #ddd; border-radius: 8px; overflow: hidden; background: white;';
                    
                    // Generate unique ID for this picture
                    var pictureId = 'pic_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
                    previewItem.setAttribute('data-picture-id', pictureId);
                    previewItem.setAttribute('data-picture-data', e.target.result);
                    
                    var img = document.createElement('img');
                    img.src = e.target.result;
                    img.style.cssText = 'width: 100%; height: 150px; object-fit: cover;';
                    
                    var overlay = document.createElement('div');
                    overlay.style.cssText = 'position: absolute; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); color: white; display: flex; align-items: center; justify-content: center; font-size: 12px; opacity: 0; transition: opacity 0.3s;';
                    overlay.textContent = 'Uploading...';
                    
                    var deleteBtn = document.createElement('button');
                    deleteBtn.innerHTML = '❌';
                    deleteBtn.style.cssText = 'position: absolute; top: 5px; right: 5px; background: rgba(255,0,0,0.8); color: white; border: none; border-radius: 50%; width: 25px; height: 25px; cursor: pointer; font-size: 12px;';
                    deleteBtn.onclick = function() {
                        previewItem.remove();
                    };
                    
                    previewItem.appendChild(img);
                    previewItem.appendChild(overlay);
                    previewItem.appendChild(deleteBtn);
                    
                    // Show overlay on hover
                    previewItem.onmouseenter = function() {
                        overlay.style.opacity = '1';
                    };
                    previewItem.onmouseleave = function() {
                        overlay.style.opacity = '0';
                    };
                    
                    previewDiv.appendChild(previewItem);
                    
                    // Simulate upload progress
                    setTimeout(function() {
                        overlay.textContent = 'Uploaded ✓';
                        overlay.style.background = 'rgba(0,128,0,0.7)';
                        uploadedCount++;
                        
                        var progress = (uploadedCount / totalFiles) * 100;
                        progressBar.style.width = progress + '%';
                        progressText.textContent = 'Uploaded ' + uploadedCount + '/' + totalFiles + ' pictures';
                        
                        if (uploadedCount === totalFiles) {
                            setTimeout(function() {
                                progressDiv.style.display = 'none';
                                progressText.textContent = 'All pictures uploaded successfully!';
                            }, 1000);
                        }
                    }, 1000 + (index * 500)); // Stagger uploads
                };
                
                reader.readAsDataURL(file);
            }
        }
        
        window.handleCarPictureUpload = handleCarPictureUpload;
        
        // Set values for Edit form dropdowns
        function setEditFormValues(purchaseData) {
            console.log('🔄 NEW setEditFormValues called - CACHE BUST 1736384000');
            if (!purchaseData) return;
            
            // Set Auction house first
            var editAuctionSelect = document.getElementById('editAuctionName');
             if (editAuctionSelect && purchaseData.auctionHouse) {
                editAuctionSelect.value = purchaseData.auctionHouse;
                // Update other dropdowns based on auction selection
                updateDropdownOptions(purchaseData.auctionHouse);
            }
            
            // Set other values after a short delay to ensure dropdowns are populated
            setTimeout(function() {
                // Set Stock Location
                var editStockSelect = document.getElementById('editStockLocation');
                if (editStockSelect && purchaseData.stockLocation) {
                    editStockSelect.value = purchaseData.stockLocation;
                    console.log('Set edit stock location:', purchaseData.stockLocation);
                }
                
                // Set Rixo Company
                var editRixoSelect = document.getElementById('editRixoCompany');
                if (editRixoSelect && purchaseData.rixoCompany) {
                    editRixoSelect.value = purchaseData.rixoCompany;
                    console.log('Set edit rixo company:', purchaseData.rixoCompany);
                }
                
                // Set Rixo Price
                var editPriceInput = document.getElementById('editRixoPrice');
                if (editPriceInput && purchaseData.rixoPrice) {
                    editPriceInput.value = purchaseData.rixoPrice;
                    console.log('Set edit rixo price:', purchaseData.rixoPrice);
                }
                
                // Set Shipment Size (Type of Vehicle)
                var editShipmentSelect = document.getElementById('editTypeOfVehicle');
                if (editShipmentSelect && (purchaseData.shipmentSize || purchaseData.vehicleType)) {
                    var shipmentValue = purchaseData.shipmentSize || purchaseData.vehicleType;
                    editShipmentSelect.value = shipmentValue;
                    console.log('Set edit shipment size:', shipmentValue);
                }
                
                // Set Rixo Requested radio button
                if (purchaseData.rixoRequested) {
                    var rixoRequestedRadio = document.querySelector('input[name="editRixoRequested"][value="' + purchaseData.rixoRequested + '"]');
                    if (rixoRequestedRadio) {
                        rixoRequestedRadio.checked = true;
                    }
                }
                
                // Set Rixo Confirmed radio button
                if (purchaseData.rixoConfirmed) {
                    var rixoConfirmedRadio = document.querySelector('input[name="editRixoConfirmed"][value="' + purchaseData.rixoConfirmed + '"]');
                    if (rixoConfirmedRadio) {
                        rixoConfirmedRadio.checked = true;
                    }
                }
                
                // Set SHAKEN checkbox
                var editShakenCheckbox = document.getElementById('editShakenCheckbox');
                var editNumberCutSection = document.getElementById('editNumberCutSection');
                if (editShakenCheckbox) {
                    editShakenCheckbox.checked = purchaseData.shaken || false;
                    if (editShakenCheckbox.checked) {
                        editNumberCutSection.style.display = 'block';
                    } else {
                        editNumberCutSection.style.display = 'none';
                    }
                }
            }, 100);
        }
        
        // Make function globally available
        window.setEditFormValues = setEditFormValues;
        window.handleClientSelection = handleClientSelection;
        window.handleEditClientSelection = handleEditClientSelection;
        
        // Initialize dropdowns when DOM is ready
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', populateDropdownOptions);
        } else {
            populateDropdownOptions();
        }
    """)
}
fun setupAddFormListeners() {
    // Client dropdown removed; using plain Client Name field
    
    // Setup Rixo dropdowns
    setupRixoDropdowns()
    
    // Add event listener for auction name change
    document.getElementById("auctionName")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLSelectElement
        js("window.handleAuctionNameChange(target.value)")
    })
    
    // Add event listener for type of vehicle change
    document.getElementById("typeOfVehicle")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLSelectElement
        val auctionName = (document.getElementById("auctionName") as HTMLSelectElement?)?.value ?: ""
        js("window.handleTypeOfVehicleChange(auctionName, target.value)")
    })
    
    // Add event listener for rixo company change
    document.getElementById("rixoCompany")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLSelectElement
        js("window.handleRixoCompanyChange(target.value)")
    })
    
    document.getElementById("cancelBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    document.getElementById("addForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleAddPurchase()
    })
    
    // Tax calculation button
    document.getElementById("calculateTaxBtn")?.addEventListener("click", { _: Event ->
        calculateTax("price", "auctionFee", "roadTax", "taxTotal")
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

    // Add SHAKEN checkbox listener
    document.getElementById("shakenCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        val numberCutSection = document.getElementById("numberCutSection") as HTMLElement?
        if (target.checked) {
            numberCutSection?.style?.setProperty("display", "block")
        } else {
            numberCutSection?.style?.setProperty("display", "none")
        }
    })

    // Add number cut listeners
    setupNumberCutListeners()
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
fun setupNumberCutListeners() {
    // Function to generate number cut string
    fun generateNumberCutString() {
        val place = (document.getElementById("numberCutPlace") as HTMLSelectElement?)?.value ?: ""
        val number1 = (document.getElementById("numberCutNumber1") as HTMLInputElement?)?.value ?: ""
        val hiragana = (document.getElementById("numberCutHiragana") as HTMLSelectElement?)?.value ?: ""
        val number2 = (document.getElementById("numberCutNumber2") as HTMLInputElement?)?.value ?: ""
        
        val numberCutString = if (place.isNotEmpty() && number1.isNotEmpty() && hiragana.isNotEmpty() && number2.isNotEmpty()) {
            "$place$number1$hiragana$number2"
        } else {
            ""
        }
        
        (document.getElementById("numberCutString") as HTMLInputElement?)?.value = numberCutString
    }
    
    // Add event listeners for all number cut fields
    document.getElementById("numberCutPlace")?.addEventListener("change", { _: Event -> generateNumberCutString() })
    document.getElementById("numberCutNumber1")?.addEventListener("input", { _: Event -> generateNumberCutString() })
    document.getElementById("numberCutHiragana")?.addEventListener("change", { _: Event -> generateNumberCutString() })
    document.getElementById("numberCutNumber2")?.addEventListener("input", { _: Event -> generateNumberCutString() })
}

fun setupEditNumberCutListeners() {
    // Function to generate number cut string for edit form
    fun generateEditNumberCutString() {
        val place = (document.getElementById("editNumberCutPlace") as HTMLSelectElement?)?.value ?: ""
        val number1 = (document.getElementById("editNumberCutNumber1") as HTMLInputElement?)?.value ?: ""
        val hiragana = (document.getElementById("editNumberCutHiragana") as HTMLSelectElement?)?.value ?: ""
        val number2 = (document.getElementById("editNumberCutNumber2") as HTMLInputElement?)?.value ?: ""
        
        val numberCutString = if (place.isNotEmpty() && number1.isNotEmpty() && hiragana.isNotEmpty() && number2.isNotEmpty()) {
            "$place$number1$hiragana$number2"
        } else {
            ""
        }
        
        (document.getElementById("editNumberCutString") as HTMLInputElement?)?.value = numberCutString
    }
    
    // Add event listeners for all edit number cut fields
    document.getElementById("editNumberCutPlace")?.addEventListener("change", { _: Event -> generateEditNumberCutString() })
    document.getElementById("editNumberCutNumber1")?.addEventListener("input", { _: Event -> generateEditNumberCutString() })
    document.getElementById("editNumberCutHiragana")?.addEventListener("change", { _: Event -> generateEditNumberCutString() })
    document.getElementById("editNumberCutNumber2")?.addEventListener("input", { _: Event -> generateEditNumberCutString() })
}

fun calculateTax(priceFieldId: String, auctionFeeFieldId: String, roadTaxFieldId: String, taxTotalFieldId: String) {
    try {
        // Get values from form fields
        val priceValue = (document.getElementById(priceFieldId) as HTMLInputElement).value.trim()
        val auctionFeeValue = (document.getElementById(auctionFeeFieldId) as HTMLInputElement).value.trim()
        val roadTaxValue = (document.getElementById(roadTaxFieldId) as HTMLInputElement).value.trim()
        
        // Helper function to extract numeric value from string (remove currency symbols, commas, etc.)
        fun extractNumericValue(value: String): Double {
            if (value.isBlank()) return 0.0
            // Remove currency symbols, commas, spaces and extract only numbers and decimal point
            val cleaned = value.replace(Regex("[^0-9.-]"), "")
            return cleaned.toDoubleOrNull() ?: 0.0
        }
        
        // Extract numeric values
        val price = extractNumericValue(priceValue)
        val auctionFee = extractNumericValue(auctionFeeValue)
        val roadTax = extractNumericValue(roadTaxValue)
        
        // Calculate 10% tax on the sum of Price + Auction Fee + Road Tax
        val totalBase = price + auctionFee + roadTax
        val taxAmount = totalBase * 0.10
        
        // Format the result with currency symbol
        val formattedTax = "¥${taxAmount.toInt()}"
        
        // Set the calculated value in the tax total field
        (document.getElementById(taxTotalFieldId) as HTMLInputElement).value = formattedTax
        
        console.log("Tax calculation: Price=$price, Auction Fee=$auctionFee, Road Tax=$roadTax, Total Base=$totalBase, 10% Tax=$formattedTax")
        
    } catch (e: Exception) {
        console.error("Error calculating tax:", e)
        showMessage("Error calculating tax. Please check your input values.", "error")
    }
}

fun handleAddPurchase() {
    val dateIso = (document.getElementById("date") as HTMLInputElement).value
    val date = formatWithWeekday(dateIso)
    val chassis = (document.getElementById("chassis") as HTMLInputElement).value
    val carModelYear = (document.getElementById("carModelYear") as HTMLInputElement).value
    val brand = (document.getElementById("brand") as HTMLInputElement).value
    val carName = (document.getElementById("carName") as HTMLInputElement).value
    val vehicleType = (document.getElementById("typeOfVehicle") as HTMLSelectElement).value
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
    val auctionName = (document.getElementById("auctionName") as HTMLSelectElement).value
    val stockLocation = (document.getElementById("stockLocation") as HTMLSelectElement).value
    val rixoCompany = (document.getElementById("rixoCompany") as HTMLSelectElement).value
    val clientName = (document.getElementById("clientName") as HTMLInputElement).value
    val country = (document.getElementById("country") as HTMLInputElement).value
    val venueId = (document.getElementById("venueId") as HTMLSelectElement).value
    val price = (document.getElementById("price") as HTMLInputElement).value
    val auctionFee = (document.getElementById("auctionFee") as HTMLInputElement).value
    val recycleFee = (document.getElementById("recycleFee") as HTMLInputElement).value
    val roadTax = (document.getElementById("roadTax") as HTMLInputElement).value
    val taxTotal = (document.getElementById("taxTotal") as HTMLInputElement).value
    val totalPrice = (document.getElementById("totalPrice") as HTMLInputElement).value
    val paymentDate = formatWithWeekday((document.getElementById("paymentDate") as HTMLInputElement).value)
    val rixoRequested = (document.querySelector("input[name=\"rixoRequested\"]:checked") as HTMLInputElement?)?.value ?: ""
    val rixoConfirmed = (document.querySelector("input[name=\"rixoConfirmed\"]:checked") as HTMLInputElement?)?.value ?: ""
    val rixoPrice = (document.getElementById("rixoPrice") as HTMLInputElement).value
    val numberCutString = (document.getElementById("numberCutString") as HTMLInputElement).value
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
    val shaken = (document.getElementById("shakenCheckbox") as HTMLInputElement).checked
    
    val purchaseData = js("{}")
    purchaseData.date = date
    purchaseData.chassis = chassis
    purchaseData.carModelYear = carModelYear
    purchaseData.brand = brand
    purchaseData.carName = carName
    // Send shipment size using the canonical field name; keep legacy vehicleType for compatibility
    purchaseData.shipmentSize = vehicleType
    purchaseData.vehicleType = vehicleType
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
    purchaseData.auctionHouse = auctionName
    purchaseData.stockLocation = stockLocation
    purchaseData.rixoCompany = rixoCompany
    purchaseData.clientName = clientName
    purchaseData.country = country
    purchaseData.venueId = venueId
    purchaseData.price = price
    purchaseData.auctionFee = auctionFee
    purchaseData.recycleFee = recycleFee
    purchaseData.roadTax = roadTax
    purchaseData.taxTotal = taxTotal
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
    purchaseData.shaken = shaken
    purchaseData.numberCut = numberCutString
    
    // Call API to create purchase
    val requestInit = js("{}")
    requestInit.method = "POST"
      val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(purchaseData)
    
    window.fetch("/api/purchases", requestInit).then { response ->
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
    window.fetch("/api/purchases/purchase/$id").then { response ->
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
                        <label>Purchase Date</label>
                        <div style="position:relative;">
                            <input type="date" id="editDate" value="${toIsoFromLabel(purchaseData.date)}" style="width:100%; padding: 8px 110px 8px 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="${purchaseData.date ?: ""}">
                            <span id="editDateDayHint" style="position:absolute; right:12px; top:50%; transform: translateY(-50%); color:#6b7280; pointer-events:none;"></span>
                        </div>
                    </div>
                    <div>
                    </div>
                    <div>
                        <label>Chassis *</label>
                        <input type="text" id="editChassis" value="${purchaseData.chassis}" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Production Date</label>
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
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Supplier Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Auction No</label>
                        <input type="text" id="editAuctionNo" value="${purchaseData.auctionNo ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Supplier Name *</label>
                        <select id="editAuctionName" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" onchange="handleAuctionNameChange(this.value)">
                            <option value="">Select Supplier Name</option>
                        </select>
                    </div>
                    <div>
                        <label>Venue ID</label>
                        <select id="editVenueId" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Venue ID</option>
                            <option value="${purchaseData.venueId ?: ""}" ${if (purchaseData.venueId != null) "selected" else ""}>${purchaseData.venueId ?: ""}</option>
                        </select>
                    </div>
                    <div>
                        <label>Stock Location</label>
                        <select id="editStockLocation" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Stock Location</option>
                        </select>
                    </div>
                    <div>
                        <label>Rixo Company</label>
                        <select id="editRixoCompany" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" onchange="handleEditRixoCompanyChange(this.value)">
                            <option value="">Select Rixo Company</option>
                        </select>
                    </div>
                    <div>
                        <label>Shipment size</label>
                        <select id="editTypeOfVehicle" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Shipment size</option>
                            <option value="Car" ${if ((purchaseData.shipmentSize ?: purchaseData.vehicleType) == "Car") "selected" else ""}>Car</option>
                            <option value="Truck" ${if ((purchaseData.shipmentSize ?: purchaseData.vehicleType) == "Truck") "selected" else ""}>Truck</option>
                        </select>
                    </div>
                    <div>
                        <label>Rixo Price</label>
                        <div style="position: relative;">
                            <input type="text" id="editRixoPrice" placeholder="Enter or select Rixo Price" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <select id="editRixoPriceDropdown" style="position: absolute; top: 0; right: 0; width: 30px; height: 100%; border: none; background: #f5f5f5; cursor: pointer;" onchange="selectEditRixoPrice(this.value)">
                                <option value="">▼</option>
                            </select>
                        </div>
                    </div>
                    <div>
                        <label>Client Name</label>
                        <input type="text" id="editClientName" value="${purchaseData.clientName ?: ""}" placeholder="Enter client name" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Target Country</label>
                        <input type="text" id="editCountry" value="${purchaseData.country ?: ""}" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Rixo Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 20px;">
                    <div>
                        <label>Rixo Requested</label>
                        <div style="display: flex; gap: 16px; align-items: center; margin-top: 8px;">
                            <label class="checkwrap">
                                <input type="radio" name="editRixoRequested" value="TRUE">
                                <span class="checkmark"></span>
                                TRUE
                            </label>
                            <label class="checkwrap">
                                <input type="radio" name="editRixoRequested" value="FALSE">
                                <span class="checkmark"></span>
                                FALSE
                            </label>
                        </div>
                    </div>
                    <div>
                        <label>Rixo Confirmed</label>
                        <div style="display: flex; gap: 16px; align-items: center; margin-top: 8px;">
                            <label class="checkwrap">
                                <input type="radio" name="editRixoConfirmed" value="TRUE">
                                <span class="checkmark"></span>
                                TRUE
                            </label>
                            <label class="checkwrap">
                                <input type="radio" name="editRixoConfirmed" value="FALSE">
                                <span class="checkmark"></span>
                                FALSE
                            </label>
                        </div>
                    </div>
                        </div>

                <!-- SHAKEN Checkbox -->
                <div style="margin: 20px 0 10px 0;">
                    <label style="display: flex; align-items: center; gap: 8px; font-weight: 600; color: #374151; cursor: pointer;">
                        <input type="checkbox" id="editShakenCheckbox" style="width: 18px; height: 18px; accent-color: #007bff;">
                        SHAKEN
                    </label>
                </div>

                <!-- Number Cut Information Section (initially hidden) -->
                <div id="editNumberCutSection" style="display: none;">
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Number Cut Information</h3>
                <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12px; margin-bottom: 20px; align-items: end;">
                    <div>
                        <label>Place Name (Japanese)</label>
                        <select id="editNumberCutPlace" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Place</option>
                            <option value="札幌">札幌 (Sapporo)</option>
                            <option value="函館">函館 (Hakodate)</option>
                            <option value="旭川">旭川 (Asahikawa)</option>
                            <option value="室蘭">室蘭 (Muroran)</option>
                            <option value="釧路">釧路 (Kushiro)</option>
                            <option value="帯広">帯広 (Obihiro)</option>
                            <option value="十勝">十勝 (Tokachi)</option>
                            <option value="北見">北見 (Kitami)</option>
                            <option value="知床">知床 (Shiretoko)</option>
                            <option value="苫小牧">苫小牧 (Tomakomai)</option>
                            <option value="青森">青森 (Aomori)</option>
                            <option value="弘前">弘前 (Hirosaki)</option>
                            <option value="岩手">岩手 (Iwate)</option>
                            <option value="盛岡">盛岡 (Morioka)</option>
                            <option value="平泉">平泉 (Hiraizumi)</option>
                            <option value="宮城">宮城 (Miyagi)</option>
                            <option value="仙台">仙台 (Sendai)</option>
                            <option value="八戸">八戸 (Hachinohe)</option>
                            <option value="秋田">秋田 (Akita)</option>
                            <option value="山形">山形 (Yamagata)</option>
                            <option value="福島">福島 (Fukushima)</option>
                            <option value="茨城">茨城 (Ibaraki)</option>
                            <option value="栃木">栃木 (Tochigi)</option>
                            <option value="群馬">群馬 (Gunma)</option>
                            <option value="埼玉">埼玉 (Saitama)</option>
                            <option value="千葉">千葉 (Chiba)</option>
                            <option value="東京">東京 (Tokyo)</option>
                            <option value="神奈川">神奈川 (Kanagawa)</option>
                            <option value="新潟">新潟 (Niigata)</option>
                            <option value="富山">富山 (Toyama)</option>
                            <option value="石川">石川 (Ishikawa)</option>
                            <option value="福井">福井 (Fukui)</option>
                            <option value="山梨">山梨 (Yamanashi)</option>
                            <option value="長野">長野 (Nagano)</option>
                            <option value="岐阜">岐阜 (Gifu)</option>
                            <option value="静岡">静岡 (Shizuoka)</option>
                            <option value="愛知">愛知 (Aichi)</option>
                            <option value="三重">三重 (Mie)</option>
                            <option value="滋賀">滋賀 (Shiga)</option>
                            <option value="京都">京都 (Kyoto)</option>
                            <option value="大阪">大阪 (Osaka)</option>
                            <option value="兵庫">兵庫 (Hyogo)</option>
                            <option value="奈良">奈良 (Nara)</option>
                            <option value="和歌山">和歌山 (Wakayama)</option>
                            <option value="鳥取">鳥取 (Tottori)</option>
                            <option value="島根">島根 (Shimane)</option>
                            <option value="岡山">岡山 (Okayama)</option>
                            <option value="広島">広島 (Hiroshima)</option>
                            <option value="山口">山口 (Yamaguchi)</option>
                            <option value="徳島">徳島 (Tokushima)</option>
                            <option value="香川">香川 (Kagawa)</option>
                            <option value="愛媛">愛媛 (Ehime)</option>
                            <option value="高知">高知 (Kochi)</option>
                            <option value="福岡">福岡 (Fukuoka)</option>
                            <option value="佐賀">佐賀 (Saga)</option>
                            <option value="長崎">長崎 (Nagasaki)</option>
                            <option value="熊本">熊本 (Kumamoto)</option>
                            <option value="大分">大分 (Oita)</option>
                            <option value="宮崎">宮崎 (Miyazaki)</option>
                            <option value="鹿児島">鹿児島 (Kagoshima)</option>
                            <option value="沖縄">沖縄 (Okinawa)</option>
                        </select>
                    </div>
                    <div>
                        <label>Number (English)</label>
                        <input type="number" id="editNumberCutNumber1" placeholder="Enter number" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    <div>
                        <label>Hiragana Character</label>
                        <select id="editNumberCutHiragana" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            <option value="">Select Character</option>
                            <option value="あ">あ (a)</option>
                            <option value="い">い (i)</option>
                            <option value="う">う (u)</option>
                            <option value="え">え (e)</option>
                            <option value="お">お (o)</option>
                            <option value="か">か (ka)</option>
                            <option value="き">き (ki)</option>
                            <option value="く">く (ku)</option>
                            <option value="け">け (ke)</option>
                            <option value="こ">こ (ko)</option>
                            <option value="さ">さ (sa)</option>
                            <option value="し">し (shi)</option>
                            <option value="す">す (su)</option>
                            <option value="せ">せ (se)</option>
                            <option value="そ">そ (so)</option>
                            <option value="た">た (ta)</option>
                            <option value="ち">ち (chi)</option>
                            <option value="つ">つ (tsu)</option>
                            <option value="て">て (te)</option>
                            <option value="と">と (to)</option>
                            <option value="な">な (na)</option>
                            <option value="に">に (ni)</option>
                            <option value="ぬ">ぬ (nu)</option>
                            <option value="ね">ね (ne)</option>
                            <option value="の">の (no)</option>
                            <option value="は">は (ha)</option>
                            <option value="ひ">ひ (hi)</option>
                            <option value="ふ">ふ (fu)</option>
                            <option value="へ">へ (he)</option>
                            <option value="ほ">ほ (ho)</option>
                            <option value="ま">ま (ma)</option>
                            <option value="み">み (mi)</option>
                            <option value="む">む (mu)</option>
                            <option value="め">め (me)</option>
                            <option value="も">も (mo)</option>
                            <option value="や">や (ya)</option>
                            <option value="ゆ">ゆ (yu)</option>
                            <option value="よ">よ (yo)</option>
                            <option value="ら">ら (ra)</option>
                            <option value="り">り (ri)</option>
                            <option value="る">る (ru)</option>
                            <option value="れ">れ (re)</option>
                            <option value="ろ">ろ (ro)</option>
                            <option value="わ">わ (wa)</option>
                            <option value="ゐ">ゐ (wi)</option>
                            <option value="ゑ">ゑ (we)</option>
                            <option value="を">を (wo)</option>
                            <option value="ん">ん (n)</option>
                        </select>
                    </div>
                    <div>
                        <label>Number (English)</label>
                        <input type="number" id="editNumberCutNumber2" placeholder="Enter number" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                </div>
                <div style="margin-bottom: 20px;">
                    <label>Generated Number Cut String:</label>
                    <input type="text" id="editNumberCutString" readonly style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; background-color: #f9f9f9;" placeholder="Will be generated automatically">
                </div>
                </div> <!-- End of editNumberCutSection -->

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
                        <label>Tax Total</label>
                        <div style="display: flex; gap: 8px;">
                            <input type="text" id="editTaxTotal" value="${purchaseData.taxTotal ?: ""}" style="flex: 1; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" placeholder="Calculated tax amount">
                            <button type="button" id="editCalculateTaxBtn" style="padding: 8px 12px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; white-space: nowrap;">10% Tax</button>
                        </div>
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
                
                <!-- Car Pictures Section -->
                <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Car Pictures</h3>
                <div style="margin-bottom: 20px; padding: 20px; border: 2px dashed #ddd; border-radius: 8px; background-color: #f9f9f9;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <label for="carPictures" style="display: inline-block; padding: 12px 24px; background-color: #007bff; color: white; border-radius: 6px; cursor: pointer; font-weight: 600; transition: background-color 0.3s;">
                            📷 Upload Car Pictures
                        </label>
                        <input type="file" id="carPictures" multiple accept="image/*" style="display: none;" onchange="handleCarPictureUpload(this)">
                    </div>
                    
                    <!-- Picture Preview Area -->
                    <div id="carPicturePreview" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 15px; margin-top: 20px;">
                        <!-- Pictures will be displayed here -->
                    </div>
                    
                    <!-- Upload Progress -->
                    <div id="uploadProgress" style="display: none; margin-top: 15px;">
                        <div style="background-color: #e9ecef; border-radius: 4px; height: 20px; overflow: hidden;">
                            <div id="progressBar" style="background-color: #007bff; height: 100%; width: 0%; transition: width 0.3s;"></div>
                        </div>
                        <div id="progressText" style="text-align: center; margin-top: 5px; font-size: 14px; color: #666;"></div>
                    </div>
                    
                    <!-- Existing Pictures (if any) -->
                    <div id="existingPictures" style="margin-top: 20px;">
                        <h4 style="color: #555; margin-bottom: 10px;">Existing Pictures:</h4>
                        <div id="existingPicturesList" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px;">
                            <!-- Existing pictures will be displayed here -->
                        </div>
                    </div>
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
    
    // Setup Rixo dropdowns and set values
    setupRixoDropdowns()
    // Set edit form values after a longer delay to ensure dropdowns are fully populated
    window.setTimeout({
        setEditFormValuesFromKotlin(purchaseData)
        // Load existing car pictures
        loadExistingCarPictures(purchaseData)
    }, 500)
}

fun setupEditFormListeners() {
    // Client dropdown removed; using plain Client Name field
    
    // Add event listener for auction name change
    document.getElementById("editAuctionName")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLSelectElement
        js("window.handleAuctionNameChange(target.value)")
    })
    
    // Add event listener for type of vehicle change
    document.getElementById("editTypeOfVehicle")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLSelectElement
        val auctionName = (document.getElementById("editAuctionName") as HTMLSelectElement?)?.value ?: ""
        js("window.handleTypeOfVehicleChange(auctionName, target.value)")
    })
    
    // Add event listener for rixo company change
    document.getElementById("editRixoCompany")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLSelectElement
        js("window.handleEditRixoCompanyChange(target.value)")
    })
    
    document.getElementById("editCancelBtn")?.addEventListener("click", { _: Event ->
        showPurchaseList()
    })
    
    document.getElementById("editForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleEditPurchase()
    })
    
    // Tax calculation button for edit form
    document.getElementById("editCalculateTaxBtn")?.addEventListener("click", { _: Event ->
        calculateTax("editPrice", "editAuctionFee", "editRoadTax", "editTaxTotal")
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

    // Add SHAKEN checkbox listener for edit form
    document.getElementById("editShakenCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        val numberCutSection = document.getElementById("editNumberCutSection") as HTMLElement?
        if (target.checked) {
            numberCutSection?.style?.setProperty("display", "block")
        } else {
            numberCutSection?.style?.setProperty("display", "none")
        }
    })

    // Add number cut listeners for edit form
    setupEditNumberCutListeners()
}
fun handleEditPurchase() {
    val id = (document.getElementById("editId") as HTMLInputElement).value.toLong()
    val date = formatWithWeekday((document.getElementById("editDate") as HTMLInputElement).value)
    val chassis = (document.getElementById("editChassis") as HTMLInputElement).value
    val carModelYear = (document.getElementById("editCarModelYear") as HTMLInputElement).value
    val brand = (document.getElementById("editBrand") as HTMLInputElement).value
    val carName = (document.getElementById("editCarName") as HTMLInputElement).value
    val vehicleType = (document.getElementById("editTypeOfVehicle") as HTMLSelectElement).value
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
    val auctionName = (document.getElementById("editAuctionName") as HTMLSelectElement).value
    val stockLocation = (document.getElementById("editStockLocation") as HTMLSelectElement).value
    val rixoCompany = (document.getElementById("editRixoCompany") as HTMLSelectElement).value
    val clientName = (document.getElementById("editClientName") as HTMLInputElement).value
    val country = (document.getElementById("editCountry") as HTMLInputElement).value
    val venueId = (document.getElementById("editVenueId") as HTMLSelectElement).value
    val price = (document.getElementById("editPrice") as HTMLInputElement).value
    val auctionFee = (document.getElementById("editAuctionFee") as HTMLInputElement).value
    val recycleFee = (document.getElementById("editRecycleFee") as HTMLInputElement).value
    val roadTax = (document.getElementById("editRoadTax") as HTMLInputElement).value
    val taxTotal = (document.getElementById("editTaxTotal") as HTMLInputElement).value
    val totalPrice = (document.getElementById("editTotalPrice") as HTMLInputElement).value
    val paymentDate = formatWithWeekday((document.getElementById("editPaymentDate") as HTMLInputElement).value)
    val rixoRequested = (document.querySelector("input[name=\"editRixoRequested\"]:checked") as HTMLInputElement?)?.value ?: ""
    val rixoConfirmed = (document.querySelector("input[name=\"editRixoConfirmed\"]:checked") as HTMLInputElement?)?.value ?: ""
    val rixoPrice = (document.getElementById("editRixoPrice") as HTMLInputElement).value
    val numberCutString = (document.getElementById("editNumberCutString") as HTMLInputElement).value
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
    val shaken = (document.getElementById("editShakenCheckbox") as HTMLInputElement).checked
    
    val purchaseData = js("{}")
    purchaseData.date = date
    purchaseData.chassis = chassis
    purchaseData.carModelYear = carModelYear
    purchaseData.brand = brand
    purchaseData.carName = carName
    // Send shipment size using the canonical field name; keep legacy vehicleType for compatibility
    purchaseData.shipmentSize = vehicleType
    purchaseData.vehicleType = vehicleType
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
    purchaseData.auctionHouse = auctionName
    purchaseData.stockLocation = stockLocation
    purchaseData.rixoCompany = rixoCompany
    purchaseData.clientName = clientName
    purchaseData.country = country
    purchaseData.venueId = venueId
    purchaseData.price = price
    purchaseData.auctionFee = auctionFee
    purchaseData.recycleFee = recycleFee
    purchaseData.roadTax = roadTax
    purchaseData.taxTotal = taxTotal
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
    purchaseData.shaken = shaken
    purchaseData.numberCut = numberCutString
    
    // Collect car pictures data
    val carPictures = collectCarPictures()
    purchaseData.carPictures = carPictures
    
    console.log("Sending update data: ${JSON.stringify(purchaseData)}")
    console.log("📷 Car pictures data:", carPictures)
    console.log("Request URL: /api/purchases/$id")
    console.log("🔍 DEBUG: venueId = $venueId")
    console.log("🔍 DEBUG: vehicleType = $vehicleType")
    console.log("🔍 DEBUG: shipmentSize = ${purchaseData.shipmentSize}")
    
    // Call API to update purchase
    val requestInit = js("{}")
    requestInit.method = "PUT"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(purchaseData)
    
    console.log("Request headers:", headers)
    console.log("Request body:", requestInit.body)
    
    window.fetch("/api/purchases/$id", requestInit).then { response ->
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

// Collect car pictures data for saving
fun collectCarPictures(): dynamic {
    val pictures = js("[]")
    
    // Get all uploaded pictures from the preview area
    val previewDiv = document.getElementById("carPicturePreview")
    if (previewDiv != null) {
        val pictureElements = previewDiv.querySelectorAll("div[data-picture-id]")
        for (i in 0 until pictureElements.length) {
            val element = pictureElements.item(i) as HTMLElement
            val pictureId = element.getAttribute("data-picture-id")
            val pictureData = element.getAttribute("data-picture-data")
            
            if (pictureId != null && pictureData != null) {
                val pictureObj = js("{}")
                pictureObj.id = pictureId
                pictureObj.data = pictureData
                pictures.push(pictureObj)
            }
        }
    }
    
    console.log("📷 Collected ${pictures.length} car pictures")
    return pictures
}

// Load existing car pictures when editing
fun loadExistingCarPictures(purchaseData: dynamic) {
    console.log("📷 Loading existing car pictures for purchase:", purchaseData.id)
    
    // Check if purchase has car pictures data
    val carPicturesJson = purchaseData.carPictures
    if (carPicturesJson != null && carPicturesJson.toString().isNotEmpty()) {
        try {
            // Parse JSON string to array
            val carPictures = js("JSON.parse(carPicturesJson)")
            console.log("📷 Parsed car pictures:", carPictures)
            
            if (js("Array.isArray(carPictures)") && js("carPictures.length > 0")) {
                console.log("📷 Found ${js("carPictures.length")} existing pictures")
                
                val existingPicturesList = document.getElementById("existingPicturesList")
                if (existingPicturesList != null) {
                    // Clear any existing content
                    existingPicturesList.innerHTML = ""
                    
                    // Display each existing picture
                    for (i in 0 until js("carPictures.length").unsafeCast<Int>()) {
                        val picture = js("carPictures[i]")
                        val pictureId = js("picture.id").toString()
                        val pictureData = js("picture.data").toString()
                        
                        // Create picture element
                        val pictureElement = document.createElement("div")
                        pictureElement.setAttribute("style", "position: relative; border: 1px solid #ddd; border-radius: 8px; overflow: hidden; background: white;")
                        pictureElement.setAttribute("data-picture-id", pictureId)
                        pictureElement.setAttribute("data-picture-data", pictureData)
                        
                        // Create image
                        val img = document.createElement("img")
                        img.setAttribute("src", pictureData)
                        img.setAttribute("style", "width: 100%; height: 150px; object-fit: cover;")
                        
                        // Create delete button
                        val deleteBtn = document.createElement("button")
                        deleteBtn.innerHTML = "❌"
                        deleteBtn.setAttribute("style", "position: absolute; top: 5px; right: 5px; background: rgba(255,0,0,0.8); color: white; border: none; border-radius: 50%; width: 25px; height: 25px; cursor: pointer; font-size: 12px;")
                        deleteBtn.setAttribute("onclick", "this.parentElement.remove();")
                        
                        pictureElement.appendChild(img)
                        pictureElement.appendChild(deleteBtn)
                        existingPicturesList.appendChild(pictureElement)
                    }
                }
            } else {
                console.log("📷 No pictures in parsed array")
            }
        } catch (e: Exception) {
            console.log("📷 Error parsing car pictures JSON:", e.message)
        }
    } else {
        console.log("📷 No existing pictures found")
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
    
    console.log("Sending request to: /api/purchases/import")
    
    window.fetch("/api/purchases/import", requestInit).then { response ->
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
        "/api/purchases/sort?field=" + currentSortField + "&order=" + currentSortOrder
    } else {
        "/api/purchases"
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
// Global pagination variables
var currentPage = 1
var itemsPerPage = 20
var allPurchases: Array<dynamic> = emptyArray()

// Global Car Booking state persistence variables
var carBookingFormState: dynamic = js("{}")
var carBookingSelectedRows: Array<String> = emptyArray()
var carBookingTableData: Array<dynamic> = emptyArray()
var carBookingDisplayedCars: Array<dynamic> = emptyArray()

fun saveCarBookingState() {
    console.log("💾 Saving Car Booking state...")
    
    // Check if we're on Car Booking page (has carSelectionTableBody)
    val carSelectionTableBodyEl = document.getElementById("carSelectionTableBody")
    val isOnCarBookingPage = carSelectionTableBodyEl != null
    
    console.log("🔍 Current page context - isOnCarBookingPage: $isOnCarBookingPage")
    
    // Only save form state if we're on the Car Booking page
    if (isOnCarBookingPage) {
        console.log("🔍 On Car Booking page - saving form state...")
        
        // Debug: Check if form elements exist and their current values
        val consigneeCountryEl = document.getElementById("consigneeCountry") as? HTMLSelectElement
        val consigneeNameEl = document.getElementById("consigneeName") as? HTMLInputElement
        val etdDateEl = document.getElementById("etdDate") as? HTMLInputElement
        val polPortEl = document.getElementById("polPort") as? HTMLSelectElement
        val podPortEl = document.getElementById("podPort") as? HTMLInputElement
        val bookingNoEl = document.getElementById("bookingNo") as? HTMLInputElement
        val vesselSelectEl = document.getElementById("vesselSelect") as? HTMLSelectElement
        val chassisSelectEl = document.getElementById("chassisSelect") as? HTMLSelectElement
        
        console.log("🔍 Form elements found:")
        console.log("  - consigneeCountry: ${consigneeCountryEl?.value ?: "null"}")
        console.log("  - consigneeName: ${consigneeNameEl?.value ?: "null"}")
        console.log("  - etdDate: ${etdDateEl?.value ?: "null"}")
        console.log("  - polPort: ${polPortEl?.value ?: "null"}")
        console.log("  - podPort: ${podPortEl?.value ?: "null"}")
        console.log("  - bookingNo: ${bookingNoEl?.value ?: "null"}")
        console.log("  - vesselSelect: ${vesselSelectEl?.value ?: "null"}")
        console.log("  - chassisSelect: ${chassisSelectEl?.value ?: "null"}")
        
        // Save form field values
        carBookingFormState = js("{}")
        carBookingFormState.consigneeCountry = consigneeCountryEl?.value ?: ""
        carBookingFormState.consigneeName = consigneeNameEl?.value ?: ""
        carBookingFormState.etdDate = etdDateEl?.value ?: ""
        carBookingFormState.polPort = polPortEl?.value ?: ""
        carBookingFormState.podPort = podPortEl?.value ?: ""
        carBookingFormState.bookingNo = bookingNoEl?.value ?: ""
        carBookingFormState.vesselSelect = vesselSelectEl?.value ?: ""
        carBookingFormState.chassisSelect = chassisSelectEl?.value ?: ""
        
        console.log("🔍 Form state being saved:", carBookingFormState)
    } else {
        console.log("🔍 Not on Car Booking page - preserving existing form state")
        console.log("🔍 Existing form state:", carBookingFormState)
    }
    
    // Save selected rows - handle different page contexts
    carBookingSelectedRows = emptyArray()
    
    // Check if we're on Car Booking page (has carSelectionTableBody)
    val carSelectionTableBodyEl2 = document.getElementById("carSelectionTableBody")
    if (carSelectionTableBodyEl2 != null) {
        // We're on Car Booking page - get selected rows from checkboxes
        val checkboxes = document.querySelectorAll("#carSelectionTableBody input[type='checkbox']:checked")
        console.log("🔍 Found ${checkboxes.length} checked checkboxes on Car Booking page")
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            val chassis = checkbox.getAttribute("data-chassis")
            console.log("🔍 Checkbox $i: chassis = $chassis, checked = ${checkbox.checked}")
            if (chassis != null) {
                carBookingSelectedRows += chassis
            }
        }
    } else {
        // We're on C&F or Package page - use stored selected cars
        console.log("🔍 Not on Car Booking page - using stored selected cars")
        carBookingSelectedRows = cnfPageSelectedCars.map { it.chassis }.toTypedArray()
        console.log("🔍 Using ${carBookingSelectedRows.size} stored selected cars:", carBookingSelectedRows.contentToString())
    }
    
    // Save table data - both displayed cars and selected cars
    carBookingTableData = getSelectedCarsFromTable().toTypedArray()
    
    console.log("✅ Car Booking state saved:", carBookingFormState)
    console.log("✅ Selected rows saved:", carBookingSelectedRows.size)
    console.log("✅ Table data saved:", carBookingTableData.size)
    console.log("✅ Displayed cars saved:", carBookingDisplayedCars.size)
}

// Save booking selection state (C&F or FOB)
fun saveBookingSelectionState(selection: String) {
    console.log("💾 Saving booking selection state: $selection")
    window.localStorage.setItem("bookingSelection", selection)
}

// Restore booking selection state
fun restoreBookingSelectionState() {
    console.log("🔄 Restoring booking selection state...")
    val savedSelection = window.localStorage.getItem("bookingSelection")
    console.log("🔍 Saved selection:", savedSelection)
    
    if (savedSelection != null) {
        val cnfCheckbox = document.getElementById("cnfCheckbox") as HTMLInputElement?
        val fobCheckbox = document.getElementById("fobCheckbox") as HTMLInputElement?
        
        when (savedSelection) {
            "cnf" -> {
                cnfCheckbox?.checked = true
                fobCheckbox?.checked = false
                console.log("✅ C&F checkbox restored as checked")
            }
            "fob" -> {
                fobCheckbox?.checked = true
                cnfCheckbox?.checked = false
                console.log("✅ FOB checkbox restored as checked")
            }
        }
    }
}

fun restoreCarBookingState() {
    console.log("🔄 Restoring Car Booking state...")
    console.log("🔍 Form state to restore:", carBookingFormState)
    
    // Debug: Check if form elements exist before restoring
    val consigneeCountryEl = document.getElementById("consigneeCountry") as? HTMLSelectElement
    val consigneeNameEl = document.getElementById("consigneeName") as? HTMLInputElement
    val etdDateEl = document.getElementById("etdDate") as? HTMLInputElement
    val polPortEl = document.getElementById("polPort") as? HTMLSelectElement
    val podPortEl = document.getElementById("podPort") as? HTMLInputElement
    val bookingNoEl = document.getElementById("bookingNo") as? HTMLInputElement
    val vesselSelectEl = document.getElementById("vesselSelect") as? HTMLSelectElement
    val chassisSelectEl = document.getElementById("chassisSelect") as? HTMLSelectElement
    
    console.log("🔍 Form elements found for restoration:")
    console.log("  - consigneeCountry: ${consigneeCountryEl != null}")
    console.log("  - consigneeName: ${consigneeNameEl != null}")
    console.log("  - etdDate: ${etdDateEl != null}")
    console.log("  - polPort: ${polPortEl != null}")
    console.log("  - podPort: ${podPortEl != null}")
    console.log("  - bookingNo: ${bookingNoEl != null}")
    console.log("  - vesselSelect: ${vesselSelectEl != null}")
    console.log("  - chassisSelect: ${chassisSelectEl != null}")
    
    // Restore form field values
    consigneeCountryEl?.value = carBookingFormState.consigneeCountry ?: ""
    consigneeNameEl?.value = carBookingFormState.consigneeName ?: ""
    etdDateEl?.value = carBookingFormState.etdDate ?: ""
    polPortEl?.value = carBookingFormState.polPort ?: ""
    podPortEl?.value = carBookingFormState.podPort ?: ""
    bookingNoEl?.value = carBookingFormState.bookingNo ?: ""
    vesselSelectEl?.value = carBookingFormState.vesselSelect ?: ""
    chassisSelectEl?.value = carBookingFormState.chassisSelect ?: ""
    
    console.log("🔍 Form fields restored - Country: ${carBookingFormState.consigneeCountry}, POL: ${carBookingFormState.polPort}")
    console.log("🔍 After restoration - Country: ${consigneeCountryEl?.value}, POL: ${polPortEl?.value}")
    
    // Restore chassis dropdown first
    console.log("🔄 Restoring chassis dropdown...")
    loadFilteredChassis()
    
    // Restore displayed cars first, then selected rows
    if (carBookingDisplayedCars.isNotEmpty()) {
        console.log("🔄 Restoring displayed cars:", carBookingDisplayedCars.size)
        displayPurchasesAsCars(carBookingDisplayedCars)
        
        // Restore selected rows after a longer delay to ensure table is fully rendered
        js("setTimeout(function() { window.restoreSelectedRows(); }, 500)")
            } else {
        console.log("⚠️ No displayed cars to restore")
    }
    
    console.log("✅ Car Booking state restored")
}

fun restoreSelectedRows() {
    console.log("🔄 Restoring selected rows...")
    console.log("🔍 carBookingSelectedRows contains:", carBookingSelectedRows.contentToString())
    
    // Check if table body exists and has rows
    val tableBody = document.getElementById("carSelectionTableBody")
    if (tableBody == null) {
        console.log("❌ Table body not found, retrying in 200ms...")
        js("setTimeout(function() { window.restoreSelectedRows(); }, 200)")
        return
    }
    
    // Clear all checkboxes first
    val allCheckboxes = document.querySelectorAll("#carSelectionTableBody input[type='checkbox']")
    console.log("🔍 Found ${allCheckboxes.length} total checkboxes to clear")
    
    if (allCheckboxes.length == 0) {
        console.log("❌ No checkboxes found in table, retrying in 200ms...")
        js("setTimeout(function() { window.restoreSelectedRows(); }, 200)")
        return
    }
    
    for (i in 0 until allCheckboxes.length) {
        val checkbox = allCheckboxes.item(i) as HTMLInputElement
        checkbox.checked = false
    }
    
    // Check the previously selected rows
    var restoredCount = 0
    for (chassis in carBookingSelectedRows) {
        console.log("🔍 Looking for checkbox with chassis: $chassis")
        val checkbox = document.querySelector("#carSelectionTableBody input[data-chassis='$chassis']") as? HTMLInputElement
        if (checkbox != null) {
            checkbox.checked = true
            restoredCount++
            console.log("✅ Restored checkbox for chassis: $chassis")
        } else {
            console.log("❌ Checkbox not found for chassis: $chassis")
            // Debug: List all available checkboxes
            val allChassisCheckboxes = document.querySelectorAll("#carSelectionTableBody input[data-chassis]")
            console.log("🔍 Available checkboxes in table:")
            for (j in 0 until allChassisCheckboxes.length) {
                val cb = allChassisCheckboxes.item(j) as HTMLInputElement
                val cbChassis = cb.getAttribute("data-chassis")
                console.log("  - Checkbox $j: chassis = $cbChassis")
            }
        }
    }
    
    console.log("✅ Selected rows restored: $restoredCount out of ${carBookingSelectedRows.size}")
}

fun displayPurchases(purchases: dynamic) {
    val table = document.getElementById("purchaseTable")!!
    
    if (js("purchases.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data.
            </div>
        """
        return
    }
    
    // Store all purchases globally for pagination
    allPurchases = purchases as Array<dynamic>
    currentPage = 1 // Reset to first page when new data is loaded
    
    displayPurchasesWithPagination()
}

fun displayPurchasesWithPagination() {
    val table = document.getElementById("purchaseTable")!!
    
    if (allPurchases.isEmpty()) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                No purchases found. Click the menu button (☰) in the top-left corner to add a purchase or import data.
            </div>
        """
        return
    }
    
    val selectedColumns = getSelectedColumns()
    val columnLabels = mapOf(
        "date" to "Purchase Date",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "auctionHouse" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "clientName" to "Client Name",
        "rixoCompany" to "Rixo Company",
        "price" to "Price",
        "carModelYear" to "Production Date",
        "brand" to "Brand",
        "grade" to "Grade",
        "rank" to "Rank",
        "color" to "Color",
        "displacement" to "Displacement",
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
        "shipmentSize" to "Shipment Size",
        "numberCut" to "Number Cut",
        "taxTotal" to "Tax Total",
        "profit" to "Profit",
        "packagePrice" to "Package Price",
        "bookingId" to "Booking ID",
        "notes" to "Notes"
    )
    
    val sortableFields = setOf("carName", "auctionHouse", "stockLocation", "clientName", "rixoCompany")
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <table style="width: 100%; border-collapse: collapse;">
            <thead>
                <tr style="background-color: #f8f9fa;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 40px;">
                        ${if (isEditor()) """
                        <input type="checkbox" id="selectAll" style="transform: scale(1.2);">
                        """ else """
                        <span style="color: #ccc;">-</span>
                        """}
                    </th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; width: 44px;"></th>
    """)
    
    // Generate header columns based on selected columns
    selectedColumns.forEach { columnKey ->
        val label = columnLabels[columnKey] ?: columnKey
        if (columnKey == "date") {
            // Special handling for Date column - show date picker filter
            tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative;">
                        <div style="display: flex; align-items: center; justify-content: space-between; cursor: pointer;" class="date-filter-header" data-field="$columnKey">
                            <span>$label</span>
                            <span style="font-size: 12px; color: #666;">📅</span>
                        </div>
                        <div class="date-filter-menu" data-field="$columnKey" style="position: absolute; top: 42px; left: 0; background: #fff; border: 1px solid #ccc; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); padding: 12px; display: none; z-index: 10; min-width: 280px;">
                            <div style="margin-bottom: 8px; font-weight: 600; color: #333;">Filter by Date</div>
                            <input type="date" id="dateFilterInput" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; margin-bottom: 8px;">
                            <div style="display: flex; gap: 8px;">
                                <button id="applyDateFilter" style="padding: 6px 12px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Apply</button>
                                <button id="clearDateFilter" style="padding: 6px 12px; background: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Clear</button>
                        </div>
                        </div>
                    </th>
            """)
        } else if (sortableFields.contains(columnKey)) {
            tableHTML.append("""
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #dee2e6; position: relative;">
                        <div style="display: flex; align-items: center; justify-content: space-between; cursor: pointer;" class="sortable-header" data-field="$columnKey">
                            <span>$label</span>
                            <span style="font-size: 12px; color: #666;">▼</span>
                        </div>
                        <div class="sort-menu" data-field="$columnKey" style="position: absolute; top: 42px; left: 0; background: #fff; border: 1px solid #ccc; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); padding: 6px 0; display: none; z-index: 10; min-width: 220px;">
                            <div class="sort-option" data-field="$columnKey" data-order="asc" style="padding: 8px 16px; cursor: pointer; color: #333;">Ascending</div>
                            <div class="sort-option" data-field="$columnKey" data-order="desc" style="padding: 8px 16px; cursor: pointer; color: #333;">Descending</div>
                        </div>
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
    
    // Calculate pagination
    val totalItems = allPurchases.size
    val totalPages = kotlin.math.ceil(totalItems.toDouble() / itemsPerPage).toInt()
    val startIndex = (currentPage - 1) * itemsPerPage
    val endIndex = kotlin.math.min(startIndex + itemsPerPage, totalItems)
    
    // Get current page data
    val currentPageData = allPurchases.sliceArray(startIndex until endIndex)
    
    for (i in 0 until currentPageData.size) {
        val purchase = currentPageData[i]
        tableHTML.append("""
            <tr style="border-bottom: 1px solid #f0f0f0;">
                <td style="padding: 12px; text-align: center;">
                    ${if (isEditor()) """
                    <input type="checkbox" class="purchase-checkbox" data-id="${purchase.id}" style="transform: scale(1.2);">
                    """ else """
                    <span style="color: #ccc;">-</span>
                    """}
                </td>
                <td style="padding: 8px 12px;">
                    ${if (isEditor()) """
                    <button class="edit-btn" data-id="${purchase.id}" aria-label="Edit" title="Edit"
                            style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    """ else """
                    """}
                </td>
        """)
        
        // Generate data columns based on selected columns
        selectedColumns.forEach { columnKey ->
            val value = when (columnKey) {
                "date" -> formatWithWeekday(purchase.date ?: "")
                "chassis" -> purchase.chassis ?: ""
                "carName" -> purchase.carName ?: ""
                "auctionHouse" -> purchase.auctionHouse ?: ""
                "stockLocation" -> purchase.stockLocation ?: ""
                "clientName" -> purchase.clientName ?: ""
                "rixoCompany" -> purchase.rixoCompany ?: ""
                "price" -> purchase.price ?: ""
                "carModelYear" -> purchase.carModelYear ?: ""
                "brand" -> purchase.brand ?: ""
                "grade" -> purchase.grade ?: ""
                "rank" -> purchase.rank ?: ""
                "color" -> purchase.color ?: ""
                "displacement" -> purchase.displacement ?: ""
                "fuel" -> purchase.fuel ?: ""
                "seat" -> purchase.seat ?: ""
                "door" -> purchase.door ?: ""
                "distance" -> purchase.distance ?: ""
                "options" -> purchase.options ?: ""
                "auctionNo" -> purchase.auctionNo ?: ""
                "country" -> purchase.country ?: ""
                "auctionFee" -> purchase.auctionFee ?: ""
                "recycleFee" -> purchase.recycleFee ?: ""
                "roadTax" -> purchase.roadTax ?: ""
                "totalPrice" -> purchase.totalPrice ?: ""
                "paymentDate" -> purchase.paymentDate ?: ""
                "rixoRequested" -> purchase.rixoRequested ?: ""
                "rixoConfirmed" -> purchase.rixoConfirmed ?: ""
                "rixoPrice" -> purchase.rixoPrice ?: ""
                "shipmentDate" -> purchase.shipmentDate ?: ""
                "blNo" -> purchase.blNo ?: ""
                "vesselNo" -> purchase.vesselNo ?: ""
                "destination" -> purchase.destination ?: ""
                "shipmentCharges" -> purchase.shipmentCharges ?: ""
                "freight" -> purchase.freight ?: ""
                "storageCharges" -> purchase.storageCharges ?: ""
                "miscCharges" -> purchase.miscCharges ?: ""
                "inspectionFee" -> purchase.inspectionFee ?: ""
                "commission" -> purchase.commission ?: ""
                "repairCompany" -> purchase.repairCompany ?: ""
                "repairCharges" -> purchase.repairCharges ?: ""
                "venueId" -> {
                    val venueIdValue = purchase.venueId ?: ""
                    console.log("🔍 DEBUG: venueId for ${purchase.chassis} = '$venueIdValue'")
                    venueIdValue
                }
                "shipmentSize" -> {
                    val shipmentSizeValue = purchase.shipmentSize ?: ""
                    console.log("🔍 DEBUG: shipmentSize for ${purchase.chassis} = '$shipmentSizeValue'")
                    shipmentSizeValue
                }
                "numberCut" -> purchase.numberCut ?: ""
                "taxTotal" -> purchase.taxTotal ?: ""
                "profit" -> purchase.profit ?: ""
                "packagePrice" -> purchase.packagePrice ?: ""
                "bookingId" -> purchase.bookingId ?: ""
                "notes" -> purchase.notes ?: ""
                else -> ""
            }
            tableHTML.append("""<td style="padding: 12px;">$value</td>""")
        }
        
        tableHTML.append("""</tr>""")
    }
    
    tableHTML.append("""
            </tbody>
        </table>
    """)
    
    // Add pagination controls
    tableHTML.append(generatePaginationHTML(totalPages, totalItems))
    
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
    
    // Add pagination event listeners
    addPaginationEventListeners(totalPages)
    
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
    
    // Add event listeners for sortable headers
    setupSortableHeaders()
    
    // Add event listeners for date filter
    setupDateFilter()
}

fun generatePaginationHTML(totalPages: Int, totalItems: Int): String {
    if (totalPages <= 1) return ""
    
    val startItem = (currentPage - 1) * itemsPerPage + 1
    val endItem = kotlin.math.min(currentPage * itemsPerPage, totalItems)
    
    return """
        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 20px; padding: 15px; background-color: #f8f9fa; border-radius: 8px; border: 1px solid #e9ecef;">
            <div style="color: #6c757d; font-size: 14px;">
                Showing $startItem to $endItem of $totalItems entries
            </div>
            <div style="display: flex; align-items: center; gap: 10px;">
                <div style="display: flex; align-items: center; gap: 5px;">
                    <label style="font-size: 14px; color: #495057;">Rows per page:</label>
                    <select id="itemsPerPageSelect" style="padding: 4px 8px; border: 1px solid #ced4da; border-radius: 4px; font-size: 14px;">
                        <option value="10" ${if (itemsPerPage == 10) "selected" else ""}>10</option>
                        <option value="20" ${if (itemsPerPage == 20) "selected" else ""}>20</option>
                        <option value="50" ${if (itemsPerPage == 50) "selected" else ""}>50</option>
                        <option value="100" ${if (itemsPerPage == 100) "selected" else ""}>100</option>
                    </select>
                </div>
                <div style="display: flex; align-items: center; gap: 5px;">
                    <button id="prevPage" ${if (currentPage <= 1) "disabled" else ""} 
                            style="padding: 6px 12px; border: 1px solid #ced4da; background: ${if (currentPage <= 1) "#e9ecef" else "white"}; 
                                   color: ${if (currentPage <= 1) "#6c757d" else "#495057"}; border-radius: 4px; cursor: ${if (currentPage <= 1) "not-allowed" else "pointer"}; font-size: 14px;">
                        Previous
                    </button>
                    <span style="padding: 6px 12px; font-size: 14px; color: #495057;">
                        Page $currentPage of $totalPages
                    </span>
                    <button id="nextPage" ${if (currentPage >= totalPages) "disabled" else ""} 
                            style="padding: 6px 12px; border: 1px solid #ced4da; background: ${if (currentPage >= totalPages) "#e9ecef" else "white"}; 
                                   color: ${if (currentPage >= totalPages) "#6c757d" else "#495057"}; border-radius: 4px; cursor: ${if (currentPage >= totalPages) "not-allowed" else "pointer"}; font-size: 14px;">
                        Next
                    </button>
                </div>
            </div>
        </div>
    """
}

fun addPaginationEventListeners(totalPages: Int) {
    // Items per page change
    document.getElementById("itemsPerPageSelect")?.addEventListener("change", { event ->
        val select = event.target as HTMLSelectElement
        itemsPerPage = select.value.toInt()
        currentPage = 1 // Reset to first page
        displayPurchasesWithPagination()
    })
    
    // Previous page button
    document.getElementById("prevPage")?.addEventListener("click", { _ ->
        if (currentPage > 1) {
            currentPage--
            displayPurchasesWithPagination()
        }
    })
    
    // Next page button
    document.getElementById("nextPage")?.addEventListener("click", { _ ->
        if (currentPage < totalPages) {
            currentPage++
            displayPurchasesWithPagination()
        }
    })
}

fun setupSortableHeaders() {
    // Add event listeners for sortable headers
    val sortableHeaders = document.querySelectorAll(".sortable-header")
    for (i in 0 until sortableHeaders.length) {
        val header = sortableHeaders.item(i) as HTMLElement
        header.addEventListener("click", { event ->
            val target = event.currentTarget as? HTMLElement ?: return@addEventListener
            val field = target.getAttribute("data-field") ?: return@addEventListener
            
            // Toggle the sort menu
            val menu = document.querySelector(".sort-menu[data-field='$field']") as HTMLElement?
            if (menu != null) {
                // Hide all other menus
                val allMenus = document.querySelectorAll(".sort-menu")
                for (j in 0 until allMenus.length) {
                    val menuItem = allMenus.item(j) as? HTMLElement
                    if (menuItem != null && menuItem != menu) {
                        menuItem.style.display = "none"
                    }
                }
                
                // Toggle current menu
                if (menu.style.display == "none" || menu.style.display.isEmpty()) {
                    menu.style.display = "block"
                } else {
                    menu.style.display = "none"
                }
            }
        })
    }
    
    // Add event listeners for sort options
    val sortOptions = document.querySelectorAll(".sort-option")
    for (i in 0 until sortOptions.length) {
        val option = sortOptions.item(i) as HTMLElement
        option.addEventListener("click", { event ->
            val target = event.currentTarget as? HTMLElement ?: return@addEventListener
            val field = target.getAttribute("data-field") ?: return@addEventListener
            val order = target.getAttribute("data-order") ?: return@addEventListener
            
            // Update current sort
            currentSortField = field
            currentSortOrder = order
            
            // Hide all menus
            val allMenus = document.querySelectorAll(".sort-menu")
            for (j in 0 until allMenus.length) {
                val menuItem = allMenus.item(j) as? HTMLElement
                if (menuItem != null) {
                    menuItem.style.display = "none"
                }
            }
            
            // Reload purchases with new sort
            loadPurchases()
        })
    }
    
    // Hide menus when clicking outside
    document.addEventListener("click", { event ->
        val target = event.target as? HTMLElement ?: return@addEventListener
        if (target.closest(".sortable-header") == null && target.closest(".sort-menu") == null && 
            target.closest(".date-filter-header") == null && target.closest(".date-filter-menu") == null) {
            val allMenus = document.querySelectorAll(".sort-menu, .date-filter-menu")
            for (i in 0 until allMenus.length) {
                val menu = allMenus.item(i) as? HTMLElement
                if (menu != null) {
                    menu.style.display = "none"
                }
            }
        }
    })
}

fun setupDateFilter() {
    // Add event listeners for date filter headers
    val dateFilterHeaders = document.querySelectorAll(".date-filter-header")
    for (i in 0 until dateFilterHeaders.length) {
        val header = dateFilterHeaders.item(i) as HTMLElement
        header.addEventListener("click", { event ->
            val target = event.currentTarget as? HTMLElement ?: return@addEventListener
            val field = target.getAttribute("data-field") ?: return@addEventListener
            
            // Toggle the date filter menu
            val menu = document.querySelector(".date-filter-menu[data-field='$field']") as HTMLElement?
            if (menu != null) {
                // Hide all other menus
                val allMenus = document.querySelectorAll(".sort-menu, .date-filter-menu")
                for (j in 0 until allMenus.length) {
                    val menuItem = allMenus.item(j) as? HTMLElement
                    if (menuItem != null && menuItem != menu) {
                        menuItem.style.display = "none"
                    }
                }
                
                // Toggle current menu
                if (menu.style.display == "none" || menu.style.display.isEmpty()) {
                    menu.style.display = "block"
                } else {
                    menu.style.display = "none"
                }
            }
        })
    }
    
    // Add event listeners for date filter buttons
    document.getElementById("applyDateFilter")?.addEventListener("click", { _: Event ->
        val dateInput = document.getElementById("dateFilterInput") as HTMLInputElement?
        val selectedDate = dateInput?.value
        if (selectedDate != null && selectedDate.isNotEmpty()) {
            applyDateFilter(selectedDate)
        }
    })
    
    document.getElementById("clearDateFilter")?.addEventListener("click", { _: Event ->
        clearDateFilter()
    })
}

fun applyDateFilter(selectedDate: String) {
    // Convert the selected date to the format used in the database
    val formattedDate = formatWithWeekday(selectedDate)
    console.log("Filtering by date:", formattedDate)
    
    // Fetch all purchases and filter by date
    window.fetch("/api/purchases").then { response ->
        if (response.ok) {
            response.json().then { purchases ->
                val filteredPurchases = js("[]")
                val purchasesArray = purchases as Array<dynamic>
                
                for (i in 0 until purchasesArray.size) {
                    val purchase = purchasesArray[i]
                    if (purchase.date == formattedDate) {
                        js("filteredPurchases.push(purchase)")
                    }
                }
                
                // Display filtered purchases
                displayPurchases(filteredPurchases)
                
                // Hide the date filter menu
                val allMenus = document.querySelectorAll(".date-filter-menu")
                for (j in 0 until allMenus.length) {
                    val menuItem = allMenus.item(j) as? HTMLElement
                    menuItem?.style?.setProperty("display", "none")
                }
                
                showMessage("Showing purchases for $formattedDate", "success")
            }
        } else {
            showMessage("Failed to load purchases for filtering", "error")
        }
    }.catch { error ->
        console.error("Error filtering purchases by date:", error)
        showMessage("Error filtering purchases by date", "error")
    }
}

fun clearDateFilter() {
    // Clear the date input
    val dateInput = document.getElementById("dateFilterInput") as HTMLInputElement?
    dateInput?.value = ""
    
    // Hide the date filter menu
    val allMenus = document.querySelectorAll(".date-filter-menu")
    for (j in 0 until allMenus.length) {
        val menuItem = allMenus.item(j) as? HTMLElement
        menuItem?.style?.setProperty("display", "none")
    }
    
    // Reload all purchases
    loadPurchases()
    showMessage("Date filter cleared", "success")
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
    val rixoTransportBtn = document.getElementById("rixoTransportBtn")
    // Show invoice button when there is a selection; hide only Rixo Transport
    if (selectedPurchases.isNotEmpty()) {
        rixoBtn?.setAttribute("style", "display: block; padding: 10px 20px; background-color: #6f42c1; color: white; border: none; border-radius: 4px; cursor: pointer;")
        rixoBtn?.textContent = "Generating Invoice PDF (${selectedPurchases.size} selected)"
    } else {
        rixoBtn?.setAttribute("style", "display: none;")
    }
        rixoTransportBtn?.setAttribute("style", "display: none;")
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
    
    // Validate that all selected purchases have the same client name
    if (!validateSameClientName(onValid = { checkMissingRixoData() })) {
        // If validation is pending (fallback fetch), we return here; the onValid callback will continue automatically
        return
    }
}

fun validateSameClientName(onValid: (() -> Unit)? = null): Boolean {
    if (selectedPurchases.isEmpty()) {
        return true
    }
    val selectedIds = selectedPurchases.toList()
    val clientNames = mutableSetOf<String>()
    val emptyClientNames = mutableListOf<Long>()

    // First pass: try to read from DOM rows
    for (id in selectedIds) {
        val purchaseRow = document.querySelector("tr[data-purchase-id='${id}']")
        if (purchaseRow != null) {
            val clientNameCell = purchaseRow.querySelector("td:nth-child(7)")
            val clientName = clientNameCell?.textContent?.trim() ?: ""
            if (clientName.isEmpty()) {
                emptyClientNames.add(id)
            } else {
                clientNames.add(clientName)
            }
        }
    }

    // If DOM lookup failed to find any names, fall back to API data for robustness
    if (clientNames.isEmpty() && emptyClientNames.isEmpty()) {
        // Best-effort synchronous-like fetch using then; we will block navigation by returning false and letting caller re-trigger after cache loads
        window.fetch("/api/purchases").then { response ->
            if (response.ok) {
                response.json().then { allPurchases ->
                    val purchasesArray = allPurchases as Array<dynamic>
                    for (purchase in purchasesArray) {
                        val pid = js("purchase.id").toString().toLongOrNull()
                        if (pid != null && selectedIds.contains(pid)) {
                            val name = js("purchase.clientName").toString().trim()
                            if (name.isEmpty()) emptyClientNames.add(pid) else clientNames.add(name)
                        }
                    }
                    // After populating, show messages if needed or continue
                    if (emptyClientNames.isNotEmpty()) {
                        showMessage("Some selected rows have empty client names.", "error")
                    } else if (clientNames.size > 1) {
                        showMessage("Selected multiple clients. Select again.", "error")
                    } else {
                        // Validation passed after fallback; proceed automatically
                        onValid?.invoke()
                    }
                }
            }
        }
        // Prevent navigation on this attempt
        showMessage("Validating selected client names...", "warning")
        return false
    }

    if (emptyClientNames.isNotEmpty()) {
        showMessage("Some selected rows have empty client names.", "error")
        return false
    }
    if (clientNames.size > 1) {
        showMessage("Selected multiple clients. Select again.", "error")
        return false
    }
    // If we reached here via DOM path and it's valid, allow caller to continue (and also invoke callback if provided)
    onValid?.invoke()
    return true
}

fun checkMissingRixoData() {
    // Get selected purchases data
    val selectedIds = selectedPurchases.toList()
    val missingDataPurchases = mutableListOf<dynamic>()
    
    // Check each selected purchase for missing Rixo data
    window.fetch("/api/purchases").then { response ->
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
                    // Persist missing purchases to be filled on the invoice page
                    try {
                        val json = JSON.stringify(missingDataPurchases)
                        window.localStorage.setItem("invoiceMissingPurchases", json)
                    } catch (e: dynamic) {
                        console.error("Failed to store missing purchases for invoice page", e)
                    }
                }
                // Always navigate to the invoice page; it will render the missing-data section if present
                navigateToInvoicePage(selectedIds)
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
        val chasis = js("purchase.chassis")
        val missingFields = js("purchase.missingFields") as List<String>
        
        modalContent.append("""
            <div style="border: 1px solid #ddd; padding: 15px; margin: 10px 0; border-radius: 4px;">
                <h4>Purchase: ${chasis}</h4>
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
        
        window.fetch("/api/purchases/${id}", requestInit)
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
fun handleRixoTransportPdfGeneration() {
    if (selectedPurchases.isEmpty()) {
        showMessage("Please select at least one purchase", "error")
        return
    }
    
    // Store selected purchases for the rixo transport page
    val selectedIds = selectedPurchases.toList()
    js("window.selectedPurchasesForRixoTransport = selectedIds")
    
    // Also store in localStorage for cross-tab access
    val idsJson = JSON.stringify(selectedIds.toTypedArray())
    window.localStorage.setItem("rixoTransportSelectedIds", idsJson)
    console.log("Storing selected IDs for Rixo Transport:", selectedIds)
    
    // Navigate to rixo transport page in new tab
    val url = "${window.location.origin}${window.location.pathname}#/rixo-transport?ids=${selectedIds.joinToString(",")}"
    window.open(url, "_blank")
}

// Navigate to invoice page with selected IDs stored globally
fun navigateToInvoicePage(selectedIds: List<Long>) {
    // Store selected IDs in localStorage for cross-tab access
    val idsJson = JSON.stringify(selectedIds.toTypedArray())
    window.localStorage.setItem("invoiceSelectedIds", idsJson)
    console.log("Storing selected IDs in localStorage:", selectedIds)
    
    // Open invoice page in a new tab with selected IDs as URL parameter
    val newTab = window.open("", "_blank")
    if (newTab != null) {
        // Set the URL to the invoice page with selected IDs as parameter
        val idsParam = selectedIds.joinToString(",")
        newTab.location.href = window.location.origin + window.location.pathname + "#/invoice?ids=" + idsParam
    } else {
        // Fallback to same tab if popup is blocked
        window.location.hash = "#/invoice"
    }
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
                    <div id="missingRixoSection"></div>
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
                                    <input class="input" type="date" id="sailDate" aria-required="true" data-required="true" />
                                    <div id="sailDateFormatted" style="margin-top: 4px; font-size: 12px; color: #6b7280; min-height: 16px;"></div>
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

    // Load selected IDs from localStorage or URL parameters for new tab functionality
    var loadedIds = false
    
    // First try to get IDs from URL parameters
    val urlParams = window.location.hash.split("?")
    if (urlParams.size > 1) {
        val params = urlParams[1]
        val idsMatch = Regex("ids=([^&]*)").find(params)
        if (idsMatch != null) {
            val idsString = idsMatch.groupValues[1]
            if (idsString.isNotEmpty()) {
                try {
                    val idsList = idsString.split(",").map { it.trim().toLong() }
                    selectedPurchases.clear()
                    idsList.forEach { id -> selectedPurchases.add(id) }
                    console.log("Loaded selected purchases from URL parameters:", selectedPurchases.toList())
                    loadedIds = true
                } catch (e: Exception) {
                    console.error("Failed to parse URL parameter IDs:", e)
                }
            }
        }
    }
    
    // If not loaded from URL, try localStorage
    if (!loadedIds) {
        val storedIds = window.localStorage.getItem("invoiceSelectedIds")
        if (storedIds != null) {
            try {
                val idsArray = JSON.parse(storedIds) as Array<Double>
                selectedPurchases.clear()
                idsArray.forEach { id -> selectedPurchases.add(id.toLong()) }
                console.log("Loaded selected purchases from localStorage:", selectedPurchases.toList())
                // Clear the stored IDs after loading
                window.localStorage.removeItem("invoiceSelectedIds")
                loadedIds = true
            } catch (e: Exception) {
                console.error("Failed to parse stored invoice IDs:", e)
            }
        }
    }
    
    if (!loadedIds) {
        console.log("No selected purchases found")
        showMessage("No purchases selected for invoice", "warning")
    }

    // Render missing rixo data section if any stored
    run {
        val section = document.getElementById("missingRixoSection") as HTMLElement?
        val storedMissing = window.localStorage.getItem("invoiceMissingPurchases")
        if (section != null && storedMissing != null) {
            try {
                val purchases = JSON.parse(storedMissing) as Array<dynamic>
                // expose to window for later collection on submit
                js("window._missingPurchasesForInvoice = purchases")
                if (purchases.isNotEmpty()) {
                    val sb = StringBuilder()
                    sb.append("<div class=\"section\" style=\"margin-bottom:22px;\">")
                    sb.append("<h3>Fill Missing Rixo Data</h3>")
                    for (p in purchases) {
                        val id = js("p.id")
                        val chassis = js("p.chassis")
                        val missingFields = js("p.missingFields") as Array<dynamic>
                        sb.append("<div style=\"border:1px solid #e5e7eb; padding:12px; border-radius:8px; margin:10px 0; background:#fff;\">")
                        sb.append("<h4 style=\"margin:0 0 10px 0;\">Purchase: "+chassis+"</h4>")
                        for (f in missingFields) {
                            val field = f as String
                            val label = when(field) {
                                "rixoCompany" -> "Rixo Company"
                                "rixoRequested" -> "Rixo Requested"
                                "rixoConfirmed" -> "Rixo Confirmed"
                                "rixoPrice" -> "Rixo Price"
                                "clientName" -> "Client Name"
                                "carName" -> "Car Name"
                                "carModelYear" -> "Car Model Year"
                                else -> field
                            }
                            sb.append("<div class=\"field\"><label>"+label+"</label>")
                            sb.append("<input class=\"input\" type=\"text\" id=\"missing_"+id+"_"+field+"\" placeholder=\"Enter "+label+"\" /></div>")
                        }
                        sb.append("</div>")
                    }
                    sb.append("</div>")
                    section.innerHTML = sb.toString()
                }
                window.localStorage.removeItem("invoiceMissingPurchases")
            } catch (e: dynamic) {
                console.error("Failed to render missing rixo section", e)
            }
        }
    }

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
        collectMissingFromInvoiceAndGenerate(idsList)
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
        window.fetch("/api/purchases").then { response ->
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

    // Add date formatting for sailDate field
    document.getElementById("sailDate")?.addEventListener("change", { ev: Event ->
        val input = ev.target as HTMLInputElement
        val dateValue = input.value
        val formattedDiv = document.getElementById("sailDateFormatted") as HTMLElement?
        if (dateValue.isNotEmpty()) {
            val formattedDate = formatWithWeekday(dateValue)
            formattedDiv?.textContent = formattedDate
        } else {
            formattedDiv?.textContent = ""
        }
    })

    // Initial preview render
    renderSelectedCarsPreview()
}
// Render Rixo Transport page content
fun showRixoTransportPage() {
    val content = document.getElementById("content") ?: return
    
    // Load selected IDs from localStorage or URL parameters for new tab functionality (same as Invoice page)
    var loadedIds = false
    var loadedSelectedIds = mutableListOf<Long>()
    
    // First try to get IDs from URL parameters
    val urlParams = window.location.hash.split("?")
    if (urlParams.size > 1) {
        val params = urlParams[1]
        val idsMatch = Regex("ids=([^&]*)").find(params)
        if (idsMatch != null) {
            val idsString = idsMatch.groupValues[1]
            if (idsString.isNotEmpty()) {
                try {
                    val idsList = idsString.split(",").map { it.trim().toLong() }
                    loadedSelectedIds.addAll(idsList)
                    console.log("Loaded selected purchases from URL parameters for Rixo Transport:", loadedSelectedIds)
                    loadedIds = true
                } catch (e: Exception) {
                    console.error("Failed to parse URL parameter IDs for Rixo Transport:", e)
                }
            }
        }
    }
    
    // If not loaded from URL, try localStorage
    if (!loadedIds) {
        val storedIds = window.localStorage.getItem("rixoTransportSelectedIds")
        if (storedIds != null) {
            try {
                val idsArray = JSON.parse(storedIds) as Array<Double>
                loadedSelectedIds.addAll(idsArray.map { it.toLong() })
                console.log("Loaded selected purchases from localStorage for Rixo Transport:", loadedSelectedIds)
                // Clear the stored IDs after loading
                window.localStorage.removeItem("rixoTransportSelectedIds")
                loadedIds = true
            } catch (e: Exception) {
                console.error("Failed to parse stored Rixo Transport IDs:", e)
            }
        }
    }
    
    if (!loadedIds) {
        console.log("No selected purchases found for Rixo Transport")
        showMessage("No purchases selected for Rixo Transport", "warning")
    }
    
    content.innerHTML = """
        <div class="rixo-transport-shell" style="width:100%; min-height: calc(100vh - 140px); display:flex; align-items:flex-start; justify-content:center; padding: 32px 16px; box-sizing:border-box;">
            <style>
                .rixo-transport-card { 
                    width: 100%; max-width: 1000px; 
                    border-radius: 16px; 
                    padding: 28px; 
                    background: rgba(255,255,255,0.75);
                    box-shadow: 0 10px 30px rgba(0,0,0,0.10);
                    border: 1px solid rgba(229,231,235,0.6);
                    backdrop-filter: blur(8px);
                }
                .rixo-transport-title { margin: 0; color: #111827; font-size: 28px; text-align: center; letter-spacing: .2px; }
                .rixo-transport-sub { color:#6b7280; margin: 10px 0 26px 0; text-align:center; }
                .section { border: 1px solid rgba(229,231,235,0.9); border-radius: 12px; padding: 18px; background: rgba(249,250,251,0.8); margin-bottom: 20px; }
                .section h3 { margin:0 0 14px 0; color:#111827; font-size:16px; text-align:left; }
                .grid-2 { display:grid; grid-template-columns: 1fr 1fr; gap: 24px; }
                .grid-1 { display:grid; grid-template-columns: 1fr; gap: 16px; }
                .field label { display:block; margin-bottom: 6px; font-weight: 600; color:#374151; }
                .input, .textarea { width:100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 10px; background: rgba(255,255,255,0.9); transition: box-shadow .2s, border-color .2s, background .2s; color:#111827; }
                .input::placeholder, .textarea::placeholder { color:#9ca3af; }
                .input:hover, .textarea:hover { border-color:#a5b4fc; }
                .input:focus, .textarea:focus { outline:none; border-color:#6d28d9; box-shadow: 0 0 0 4px rgba(109,40,217,0.15); background:#fff; }
                .error-text { margin-top:6px; font-size: 12px; color:#b91c1c; min-height: 16px; }
                .actions { display:flex; gap: 12px; justify-content:center; margin-top:22px; }
                .btn { padding: 10px 18px; border: none; border-radius: 10px; cursor: pointer; transition: transform .05s ease, box-shadow .2s ease; }
                .btn:active { transform: translateY(1px); }
                .btn-primary { background: linear-gradient(135deg, #28a745, #20c997); color:white; box-shadow: 0 6px 16px rgba(40,167,69,0.35); }
                .btn-primary:hover { box-shadow: 0 8px 20px rgba(40,167,69,0.45); }
                .btn-secondary { background:#6b7280; color:white; }
                @media (max-width: 720px) { .grid-2 { grid-template-columns: 1fr; } }
            </style>
            <div class="rixo-transport-card">
                <h2 class="rixo-transport-title">Rixo Information</h2>
                <p class="rixo-transport-sub">Please fill in the details for land transportation PDF generation:</p>
                <form id="rixoTransportForm" novalidate>
                                    <div class="section">
                                        <h3>Selected Cars (${loadedSelectedIds.size})</h3>
                                        <div id="selectedCarsPreview" style="margin: 8px 0 6px 0; font-weight:600;">Loading selected cars...</div>
                                    </div>
                    
                    <div id="fillMissingDataSection" class="section" style="display: none;">
                        <h3>Fill Missing Data</h3>
                        <div id="missingDataContent"></div>
                    </div>
                    
                    <div class="section">
                        <h3>Details</h3>
                        <div class="field">
                            <label for="transportDate">Date</label>
                            <input class="input" type="date" id="transportDate" aria-required="true" data-required="true" />
                            <div id="transportDateFormatted" style="margin-top: 4px; font-size: 12px; color: #6b7280; min-height: 16px;"></div>
                            <div id="transportDateError" class="error-text"></div>
                        </div>
                    </div>
                    
                    <div class="actions">
                        <button type="button" id="cancelRixoTransportBtn" class="btn btn-secondary">Cancel</button>
                        <button type="submit" id="generateRixoTransportPdfBtn" class="btn btn-primary">Generate PDF</button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Store selected purchases globally for this page
    val jsArray = loadedSelectedIds.toTypedArray()
    js("window.selectedPurchasesForRixoTransport = jsArray")
    
    // Load and display selected cars (same as Invoice page)
    loadSelectedCarsForRixoTransport(loadedSelectedIds)
    
    // Set up event listeners
    setupRixoTransportPageListeners()
}

// New Rixo Request Generator Page (matches the image layout)
fun showRixoRequestGeneratorPage() {
    val content = document.getElementById("content") ?: return
    
    content.innerHTML = """
        <div style="width: 100%; min-height: calc(100vh - 140px); padding: 20px; box-sizing: border-box;">
            <div style="display: flex; gap: 20px; height: 100%;">
                <!-- Left Panel: Rixo Request Generator Form -->
                <div style="flex: 1; background: white; border-radius: 12px; padding: 24px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 1px solid #e5e7eb;">
                    <h2 style="margin: 0 0 24px 0; color: #111827; font-size: 24px; font-weight: 600;">Rixo Request Generator</h2>
                    
                    <form id="rixoRequestForm">
                        <!-- Buying Date -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Buying Date</label>
                            <input type="date" id="buyingDate" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                        
                        <!-- Rixo Company -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Rixo Company</label>
                            <select id="rixoCompany" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; background: white;">
                                <option value="">Select Rixo Company</option>
                            </select>
                        </div>
                        
                        <!-- Head Message -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Head Message</label>
                            <textarea id="headMessage" rows="3" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; resize: vertical;">いつもお世話になっております。
下記の車両の陸送手配をお願いいたします。</textarea>
                        </div>
                        
                        <!-- Footer Message -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Footer Message</label>
                            <textarea id="footerMessage" rows="3" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; resize: vertical;">※港や船での盗難が多発の為、スペアキーやリモコンキーが車内に
ありましたら弊社まで郵送していただけると助かります。</textarea>
                        </div>
                        
                        <!-- Extra Message -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Extra Message</label>
                            <textarea id="extraMessage" rows="2" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; resize: vertical;"></textarea>
                        </div>
                        
                        <!-- Contact Details -->
                        <div style="margin-bottom: 24px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Contact Details</label>
                            <textarea id="contactDetails" rows="3" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; resize: vertical;">担当：芽紋 080-3918-1478
FAX: 047-711-0409
有限会社メモン</textarea>
                        </div>
                        
                        <!-- Print Button -->
                        <button type="button" id="printRixoRequest" style="width: 100%; padding: 14px; background: linear-gradient(135deg, #8e44ad, #9b59b6); color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: 600; cursor: pointer; transition: all 0.2s;">
                            Print
                        </button>
                    </form>
                </div>
                
                <!-- Right Panel: Rows Preview -->
                <div style="flex: 1; background: white; border-radius: 12px; padding: 24px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                        <h2 style="margin: 0; color: #111827; font-size: 24px; font-weight: 600;">Rows Preview</h2>
                        <button id="backToPurchaseList" style="padding: 8px 16px; background: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px;">Back to Purchase List</button>
                    </div>
                    
                    <div id="selectedCount" style="margin-bottom: 16px; color: #6b7280; font-size: 14px;">Selected: 0 of 0</div>
                    
                    <div id="rixoRowsPreview" style="border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden;">
                        <div style="text-align: center; padding: 40px; color: #9ca3af;">
                            Please select a buying date and Rixo company to view available rows.
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <style>
            /* Custom checkbox styling for Rixo rows */
            .rixo-checkwrap {
                display: inline-flex;
                align-items: center;
                cursor: pointer;
                position: relative;
            }
            
            .rixo-check {
                appearance: none !important;
                -webkit-appearance: none !important;
                -moz-appearance: none !important;
                width: 20px;
                height: 20px;
                border-radius: 50%;
                border: 2px solid #007bff;
                background: #fff;
                cursor: pointer;
                position: relative;
                flex-shrink: 0;
                margin: 0;
            }
            
            .rixo-check:checked {
                background: #007bff;
                border-color: #007bff;
            }
            
            .rixo-check:checked::after {
                content: '✓';
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                color: white;
                font-size: 12px;
                font-weight: bold;
            }
            
            .rixo-table {
                width: 100%;
                border-collapse: collapse;
            }
            
            .rixo-table th,
            .rixo-table td {
                padding: 12px;
                text-align: left;
                border-bottom: 1px solid #e5e7eb;
            }
            
            .rixo-table th {
                background: #f9fafb;
                font-weight: 600;
                color: #374151;
            }
            
            .rixo-table tr:hover {
                background: #f9fafb;
            }
            
            .rixo-edit-btn {
                width: 28px;
                height: 28px;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                background-color: #4CC9FF;
                border: none;
                border-radius: 50%;
                cursor: pointer;
                box-shadow: 0 2px 6px rgba(76,201,255,0.30);
            }
            
            .rixo-edit-btn:hover {
                background-color: #3bb5e6;
            }
        </style>
    """
    
    // Set up event listeners
    setupRixoRequestGeneratorListeners()
    
    // Set default date to today
    val today = js("new Date().toISOString().split('T')[0]") as String
    document.getElementById("buyingDate")?.setAttribute("value", today)
}

// Set up event listeners for the Rixo Request Generator page
fun setupRixoRequestGeneratorListeners() {
    // Back to Purchase List button
    document.getElementById("backToPurchaseList")?.addEventListener("click", { _: Event ->
        window.location.hash = "#/purchase"
    })
    
    // Buying Date change - load Rixo companies and rows
    document.getElementById("buyingDate")?.addEventListener("change", { _: Event ->
        loadRixoCompaniesForDate()
        loadRowsForDateAndCompany()
    })
    
    // Rixo Company change - load rows
    document.getElementById("rixoCompany")?.addEventListener("change", { _: Event ->
        loadRowsForDateAndCompany()
    })
    
    // Print button
    document.getElementById("printRixoRequest")?.addEventListener("click", { _: Event ->
        generateRixoRequestPdf()
    })
}

// Load Rixo companies for the selected date
fun loadRixoCompaniesForDate() {
    val buyingDate = (document.getElementById("buyingDate") as HTMLInputElement).value
    if (buyingDate.isEmpty()) return
    
    // Convert ISO date to database format (e.g., "2025-04-24" -> "April24, 2025(Thursday)")
    val formattedDate = formatWithWeekday(buyingDate)
    console.log("Looking for purchases with date:", formattedDate)
    
    window.fetch("/api/purchases").then { response ->
        if (response.ok) {
            response.json().then { allPurchases ->
                val purchasesArray = allPurchases as Array<dynamic>
                val rixoCompanies = mutableSetOf<String>()
                
                for (purchase in purchasesArray) {
                    val date = js("purchase.date")?.toString() ?: ""
                    val rixoRequested = js("purchase.rixoRequested")?.toString() ?: "FALSE"
                    val rixoCompany = js("purchase.rixoCompany")?.toString() ?: ""
                    
                    // Only include purchases from the selected date where rixoRequested is FALSE
                    if (date == formattedDate && rixoRequested == "FALSE" && rixoCompany.isNotEmpty()) {
                        rixoCompanies.add(rixoCompany)
                        console.log("Found matching purchase with rixoCompany:", rixoCompany)
                    }
                }
                
                console.log("Found rixoCompanies:", rixoCompanies)
                
                // Update dropdown
                val select = document.getElementById("rixoCompany") as HTMLSelectElement
                select.innerHTML = "<option value=\"\">Select Rixo Company</option>"
                
                rixoCompanies.sorted().forEach { company ->
                    val option = document.createElement("option")
                    option.setAttribute("value", company)
                    option.textContent = company
                    select.appendChild(option)
                }
            }
        }
    }
}

// Load rows for the selected date and company
fun loadRowsForDateAndCompany() {
    val buyingDate = (document.getElementById("buyingDate") as HTMLInputElement).value
    val rixoCompany = (document.getElementById("rixoCompany") as HTMLSelectElement).value
    
    if (buyingDate.isEmpty() || rixoCompany.isEmpty()) {
        document.getElementById("rixoRowsPreview")?.innerHTML = """
            <div style="text-align: center; padding: 40px; color: #9ca3af;">
                Please select a buying date and Rixo company to view available rows.
            </div>
        """
        document.getElementById("selectedCount")?.textContent = "Selected: 0 of 0"
        return
    }
    
    // Convert ISO date to database format
    val formattedDate = formatWithWeekday(buyingDate)
    
    window.fetch("/api/purchases").then { response ->
        if (response.ok) {
            response.json().then { allPurchases ->
                val purchasesArray = allPurchases as Array<dynamic>
                val matchingPurchases = mutableListOf<dynamic>()
                
                for (purchase in purchasesArray) {
                    val date = js("purchase.date")?.toString() ?: ""
                    val rixoRequested = js("purchase.rixoRequested")?.toString() ?: "FALSE"
                    val company = js("purchase.rixoCompany")?.toString() ?: ""
                    
                    // Only include purchases from the selected date and company where rixoRequested is FALSE
                    if (date == formattedDate && company == rixoCompany && rixoRequested == "FALSE") {
                        matchingPurchases.add(purchase)
                    }
                }
                
                renderRixoRowsPreview(matchingPurchases)
            }
        }
    }
}

// Render the rows preview table
fun renderRixoRowsPreview(purchases: List<dynamic>) {
    val preview = document.getElementById("rixoRowsPreview") ?: return
    val selectedCount = document.getElementById("selectedCount") ?: return
    
    if (purchases.isEmpty()) {
        preview.innerHTML = """
            <div style="text-align: center; padding: 40px; color: #9ca3af;">
                No rows found for the selected date and Rixo company.
            </div>
        """
        selectedCount.textContent = "Selected: 0 of 0"
        return
    }
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <table class="rixo-table">
            <thead>
                <tr>
                    <th style="width: 60px;">
                        <label class="rixo-checkwrap">
                            <input type="checkbox" id="selectAllRixo" class="rixo-check">
                            <span style="margin-left: 8px; font-weight: 600;">Select All</span>
                        </label>
                    </th>
                    <th style="width: 50px;"></th>
                    <th>Chassis</th>
                    <th>Year</th>
                    <th>Car</th>
                    <th>Supplier Name</th>
                    <th>Stock</th>
                    <th>Venue ID</th>
                    <th>Number Cut</th>
                </tr>
            </thead>
            <tbody>
    """)
    
    purchases.forEach { purchase ->
        val id = js("purchase.id").toString()
        val chassis = js("purchase.chassis")?.toString() ?: ""
        val year = js("purchase.carModelYear")?.toString() ?: ""
        val carName = js("purchase.carName")?.toString() ?: ""
        val auctionHouse = js("purchase.auctionHouse")?.toString() ?: ""
        val stockLocation = js("purchase.stockLocation")?.toString() ?: ""
        val venueId = js("purchase.venueId")?.toString() ?: ""
        val numberCut = js("purchase.numberCut")?.toString() ?: ""
        
        tableHTML.append("""
            <tr>
                <td>
                    <label class="rixo-checkwrap">
                        <input type="checkbox" class="rixo-check rixo-row-check" data-id="$id" checked>
                    </label>
                </td>
                <td>
                    <button class="rixo-edit-btn" data-id="$id" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                </td>
                <td>$chassis</td>
                <td>$year</td>
                <td>$carName</td>
                <td>$auctionHouse</td>
                <td>$stockLocation</td>
                <td>$venueId</td>
                <td>$numberCut</td>
            </tr>
        """)
    }
    
    tableHTML.append("""
            </tbody>
        </table>
    """)
    
    preview.innerHTML = tableHTML.toString()
    selectedCount.textContent = "Selected: ${purchases.size} of ${purchases.size}"
    
    // Set up checkbox listeners
    setupRixoCheckboxListeners()
}

// Set up checkbox event listeners for the Rixo rows
fun setupRixoCheckboxListeners() {
    // Select All checkbox
    val selectAllCheckbox = document.getElementById("selectAllRixo") as HTMLInputElement?
    val rowCheckboxes = document.querySelectorAll(".rixo-row-check")
    
    selectAllCheckbox?.addEventListener("change", { _: Event ->
        val isChecked = selectAllCheckbox.checked
        for (i in 0 until rowCheckboxes.length) {
            val checkbox = rowCheckboxes.item(i) as HTMLInputElement
            checkbox.checked = isChecked
        }
        updateRixoSelectedCount()
    })
    
    // Individual row checkboxes
    for (i in 0 until rowCheckboxes.length) {
        val checkbox = rowCheckboxes.item(i) as HTMLInputElement
        checkbox.addEventListener("change", { _: Event ->
            updateRixoSelectedCount()
            updateSelectAllRixoCheckbox()
        })
    }
    
    // Edit button listeners
    val editButtons = document.querySelectorAll(".rixo-edit-btn")
    for (i in 0 until editButtons.length) {
        val button = editButtons.item(i) as HTMLElement
        button.addEventListener("click", { event ->
            val btn = event.currentTarget as HTMLElement
            val id = btn.getAttribute("data-id")?.toLongOrNull()
            if (id != null) {
                showRixoEditModal(id)
            }
        })
    }
}

// Update the selected count display
fun updateRixoSelectedCount() {
    val rowCheckboxes = document.querySelectorAll(".rixo-row-check")
    var checkedCount = 0
    for (i in 0 until rowCheckboxes.length) {
        val checkbox = rowCheckboxes.item(i) as HTMLInputElement
        if (checkbox.checked) checkedCount++
    }
    val totalCount = rowCheckboxes.length
    
    document.getElementById("selectedCount")?.textContent = "Selected: $checkedCount of $totalCount"
}

// Update the Select All checkbox state
fun updateSelectAllRixoCheckbox() {
    val selectAllCheckbox = document.getElementById("selectAllRixo") as HTMLInputElement?
    val rowCheckboxes = document.querySelectorAll(".rixo-row-check")
    
    if (selectAllCheckbox != null && rowCheckboxes.length > 0) {
        var checkedCount = 0
        for (i in 0 until rowCheckboxes.length) {
            val checkbox = rowCheckboxes.item(i) as HTMLInputElement
            if (checkbox.checked) checkedCount++
        }
        selectAllCheckbox.checked = checkedCount == rowCheckboxes.length
        selectAllCheckbox.indeterminate = checkedCount > 0 && checkedCount < rowCheckboxes.length
    }
}

// Show edit modal for a Rixo row
fun showRixoEditModal(purchaseId: Long) {
    // Fetch the purchase data first
    window.fetch("/api/purchases/purchase/$purchaseId")
        .then { response -> response.json() }
        .then { purchaseData ->
            createRixoEditModal(purchaseData)
        }
        .catch { error ->
            console.error("Error fetching purchase data:", error)
            showMessage("Error loading purchase data", "error")
        }
}

fun createRixoEditModal(purchaseData: dynamic) {
    val modalHTML = """
        <div id="rixoEditModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.5); z-index: 1000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; padding: 24px; max-width: 800px; width: 90%; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px;">
                    <h2 style="margin: 0; color: #111827; font-size: 24px; font-weight: 600;">Edit Purchase</h2>
                    <button id="closeRixoModal" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #6b7280;">&times;</button>
                </div>
                
                <form id="rixoEditForm">
                    <input type="hidden" id="rixoEditId" value="${purchaseData.id}">
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                        <div>
                        </div>
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Chassis</label>
                            <input type="text" id="rixoEditChassis" value="${purchaseData.chassis ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Car Model Year</label>
                            <input type="text" id="rixoEditCarModelYear" value="${purchaseData.carModelYear ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Car Name</label>
                            <input type="text" id="rixoEditCarName" value="${purchaseData.carName ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Supplier Name</label>
                            <input type="text" id="rixoEditAuctionHouse" value="${purchaseData.auctionHouse ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Stock Location</label>
                            <input type="text" id="rixoEditStockLocation" value="${purchaseData.stockLocation ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px;">
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Venue ID</label>
                            <input type="text" id="rixoEditVenueId" value="${purchaseData.venueId ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151;">Number Cut</label>
                            <input type="text" id="rixoEditNumberCut" value="${purchaseData.numberCut ?: ""}" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px;">
                        </div>
                    </div>
                    
                    <!-- SHAKEN Checkbox -->
                    <div style="margin: 20px 0 10px 0;">
                        <label style="display: flex; align-items: center; gap: 8px; font-weight: 600; color: #374151; cursor: pointer;">
                            <input type="checkbox" id="rixoEditShakenCheckbox" style="width: 18px; height: 18px; accent-color: #007bff;">
                            SHAKEN
                        </label>
                    </div>

                    <!-- Number Cut Information Section (initially hidden) -->
                    <div id="rixoEditNumberCutSection" style="display: none;">
                        <h3 style="color: #333; margin: 20px 0 10px 0; border-bottom: 1px solid #eee; padding-bottom: 5px;">Number Cut Information</h3>
                        <div style="display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 12px; margin-bottom: 20px; align-items: end;">
                            <div>
                                <label>Place Name (Japanese)</label>
                                <select id="rixoEditNumberCutPlace" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                                    <option value="">Select Place</option>
                                    <option value="札幌">札幌 (Sapporo)</option>
                                    <option value="函館">函館 (Hakodate)</option>
                                    <option value="旭川">旭川 (Asahikawa)</option>
                                    <option value="室蘭">室蘭 (Muroran)</option>
                                    <option value="釧路">釧路 (Kushiro)</option>
                                    <option value="帯広">帯広 (Obihiro)</option>
                                    <option value="十勝">十勝 (Tokachi)</option>
                                    <option value="北見">北見 (Kitami)</option>
                                    <option value="知床">知床 (Shiretoko)</option>
                                    <option value="苫小牧">苫小牧 (Tomakomai)</option>
                                    <option value="青森">青森 (Aomori)</option>
                                    <option value="弘前">弘前 (Hirosaki)</option>
                                    <option value="岩手">岩手 (Iwate)</option>
                                    <option value="盛岡">盛岡 (Morioka)</option>
                                    <option value="平泉">平泉 (Hiraizumi)</option>
                                    <option value="宮城">宮城 (Miyagi)</option>
                                    <option value="仙台">仙台 (Sendai)</option>
                                    <option value="八戸">八戸 (Hachinohe)</option>
                                    <option value="秋田">秋田 (Akita)</option>
                                    <option value="山形">山形 (Yamagata)</option>
                                    <option value="福島">福島 (Fukushima)</option>
                                    <option value="茨城">茨城 (Ibaraki)</option>
                                    <option value="栃木">栃木 (Tochigi)</option>
                                    <option value="群馬">群馬 (Gunma)</option>
                                    <option value="埼玉">埼玉 (Saitama)</option>
                                    <option value="千葉">千葉 (Chiba)</option>
                                    <option value="東京">東京 (Tokyo)</option>
                                    <option value="神奈川">神奈川 (Kanagawa)</option>
                                    <option value="新潟">新潟 (Niigata)</option>
                                    <option value="富山">富山 (Toyama)</option>
                                    <option value="石川">石川 (Ishikawa)</option>
                                    <option value="福井">福井 (Fukui)</option>
                                    <option value="山梨">山梨 (Yamanashi)</option>
                                    <option value="長野">長野 (Nagano)</option>
                                    <option value="岐阜">岐阜 (Gifu)</option>
                                    <option value="静岡">静岡 (Shizuoka)</option>
                                    <option value="愛知">愛知 (Aichi)</option>
                                    <option value="三重">三重 (Mie)</option>
                                    <option value="滋賀">滋賀 (Shiga)</option>
                                    <option value="京都">京都 (Kyoto)</option>
                                    <option value="大阪">大阪 (Osaka)</option>
                                    <option value="兵庫">兵庫 (Hyogo)</option>
                                    <option value="奈良">奈良 (Nara)</option>
                                    <option value="和歌山">和歌山 (Wakayama)</option>
                                    <option value="鳥取">鳥取 (Tottori)</option>
                                    <option value="島根">島根 (Shimane)</option>
                                    <option value="岡山">岡山 (Okayama)</option>
                                    <option value="広島">広島 (Hiroshima)</option>
                                    <option value="山口">山口 (Yamaguchi)</option>
                                    <option value="徳島">徳島 (Tokushima)</option>
                                    <option value="香川">香川 (Kagawa)</option>
                                    <option value="愛媛">愛媛 (Ehime)</option>
                                    <option value="高知">高知 (Kochi)</option>
                                    <option value="福岡">福岡 (Fukuoka)</option>
                                    <option value="佐賀">佐賀 (Saga)</option>
                                    <option value="長崎">長崎 (Nagasaki)</option>
                                    <option value="熊本">熊本 (Kumamoto)</option>
                                    <option value="大分">大分 (Oita)</option>
                                    <option value="宮崎">宮崎 (Miyazaki)</option>
                                    <option value="鹿児島">鹿児島 (Kagoshima)</option>
                                    <option value="沖縄">沖縄 (Okinawa)</option>
                                </select>
                            </div>
                            <div>
                                <label>Number (English)</label>
                                <input type="number" id="rixoEditNumberCutNumber1" placeholder="Enter number" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            </div>
                            <div>
                                <label>Hiragana Character</label>
                                <select id="rixoEditNumberCutHiragana" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                                    <option value="">Select Character</option>
                                    <option value="あ">あ (a)</option>
                                    <option value="い">い (i)</option>
                                    <option value="う">う (u)</option>
                                    <option value="え">え (e)</option>
                                    <option value="お">お (o)</option>
                                    <option value="か">か (ka)</option>
                                    <option value="き">き (ki)</option>
                                    <option value="く">く (ku)</option>
                                    <option value="け">け (ke)</option>
                                    <option value="こ">こ (ko)</option>
                                    <option value="さ">さ (sa)</option>
                                    <option value="し">し (shi)</option>
                                    <option value="す">す (su)</option>
                                    <option value="せ">せ (se)</option>
                                    <option value="そ">そ (so)</option>
                                    <option value="た">た (ta)</option>
                                    <option value="ち">ち (chi)</option>
                                    <option value="つ">つ (tsu)</option>
                                    <option value="て">て (te)</option>
                                    <option value="と">と (to)</option>
                                    <option value="な">な (na)</option>
                                    <option value="に">に (ni)</option>
                                    <option value="ぬ">ぬ (nu)</option>
                                    <option value="ね">ね (ne)</option>
                                    <option value="の">の (no)</option>
                                    <option value="は">は (ha)</option>
                                    <option value="ひ">ひ (hi)</option>
                                    <option value="ふ">ふ (fu)</option>
                                    <option value="へ">へ (he)</option>
                                    <option value="ほ">ほ (ho)</option>
                                    <option value="ま">ま (ma)</option>
                                    <option value="み">み (mi)</option>
                                    <option value="む">む (mu)</option>
                                    <option value="め">め (me)</option>
                                    <option value="も">も (mo)</option>
                                    <option value="や">や (ya)</option>
                                    <option value="ゆ">ゆ (yu)</option>
                                    <option value="よ">よ (yo)</option>
                                    <option value="ら">ら (ra)</option>
                                    <option value="り">り (ri)</option>
                                    <option value="る">る (ru)</option>
                                    <option value="れ">れ (re)</option>
                                    <option value="ろ">ろ (ro)</option>
                                    <option value="わ">わ (wa)</option>
                                    <option value="ゐ">ゐ (wi)</option>
                                    <option value="ゑ">ゑ (we)</option>
                                    <option value="を">を (wo)</option>
                                    <option value="ん">ん (n)</option>
                                </select>
                            </div>
                            <div>
                                <label>Number (English)</label>
                                <input type="number" id="rixoEditNumberCutNumber2" placeholder="Enter number" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                            </div>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label>Generated Number Cut String:</label>
                            <input type="text" id="rixoEditNumberCutString" readonly style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; background-color: #f9f9f9;" placeholder="Will be generated automatically">
                        </div>
                    </div>
                    
                    <div style="display: flex; gap: 12px; justify-content: flex-end; margin-top: 24px;">
                        <button type="button" id="rixoEditCancel" style="padding: 12px 24px; background: #6b7280; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 14px;">Cancel</button>
                        <button type="submit" id="rixoEditSave" style="padding: 12px 24px; background: #007bff; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 14px;">Save Changes</button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Remove any existing modal
    document.getElementById("rixoEditModal")?.remove()
    
    // Add the modal to the page
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Set up the modal functionality
    setupRixoEditModalListeners(purchaseData)
}

fun setupRixoEditModalListeners(purchaseData: dynamic) {
    // Close modal functionality
    document.getElementById("closeRixoModal")?.addEventListener("click", { _: Event ->
        document.getElementById("rixoEditModal")?.remove()
    })
    
    document.getElementById("rixoEditCancel")?.addEventListener("click", { _: Event ->
        document.getElementById("rixoEditModal")?.remove()
    })
    
    // Close modal when clicking outside
    document.getElementById("rixoEditModal")?.addEventListener("click", { event: Event ->
        val target = event.target as HTMLElement?
        if (target?.id == "rixoEditModal") {
            document.getElementById("rixoEditModal")?.remove()
        }
    })
    
    // SHAKEN checkbox functionality
    document.getElementById("rixoEditShakenCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        val numberCutSection = document.getElementById("rixoEditNumberCutSection") as HTMLElement?
        if (target.checked) {
            numberCutSection?.style?.setProperty("display", "block")
        } else {
            numberCutSection?.style?.setProperty("display", "none")
        }
    })
    
    // Set initial SHAKEN checkbox state
    val shakenCheckbox = document.getElementById("rixoEditShakenCheckbox") as HTMLInputElement?
    val numberCutSection = document.getElementById("rixoEditNumberCutSection") as HTMLElement?
    if (shakenCheckbox != null) {
        shakenCheckbox.checked = purchaseData.shaken || false
        if (shakenCheckbox.checked) {
            numberCutSection?.style?.setProperty("display", "block")
        } else {
            numberCutSection?.style?.setProperty("display", "none")
        }
    }
    
    // Number cut generation listeners
    setupRixoModalNumberCutListeners()
    
    // Form submission
    document.getElementById("rixoEditForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleRixoEditSubmit()
    })
}

fun setupRixoModalNumberCutListeners() {
    // Add listeners for number cut generation (similar to the main form)
    val placeSelect = document.getElementById("rixoEditNumberCutPlace") as HTMLSelectElement?
    val number1Input = document.getElementById("rixoEditNumberCutNumber1") as HTMLInputElement?
    val hiraganaSelect = document.getElementById("rixoEditNumberCutHiragana") as HTMLSelectElement?
    val number2Input = document.getElementById("rixoEditNumberCutNumber2") as HTMLInputElement?
    val resultInput = document.getElementById("rixoEditNumberCutString") as HTMLInputElement?
    
    fun updateNumberCutString() {
        val place = placeSelect?.value ?: ""
        val number1 = number1Input?.value ?: ""
        val hiragana = hiraganaSelect?.value ?: ""
        val number2 = number2Input?.value ?: ""
        
        if (place.isNotEmpty() && number1.isNotEmpty() && hiragana.isNotEmpty() && number2.isNotEmpty()) {
            val result = "$place$number1$hiragana$number2"
            resultInput?.value = result
        } else {
            resultInput?.value = ""
        }
    }
    
    placeSelect?.addEventListener("change", { _: Event -> updateNumberCutString() })
    number1Input?.addEventListener("input", { _: Event -> updateNumberCutString() })
    hiraganaSelect?.addEventListener("change", { _: Event -> updateNumberCutString() })
    number2Input?.addEventListener("input", { _: Event -> updateNumberCutString() })
}

fun handleRixoEditSubmit() {
    val id = (document.getElementById("rixoEditId") as HTMLInputElement).value.toLong()
    val chassis = (document.getElementById("rixoEditChassis") as HTMLInputElement).value
    val carModelYear = (document.getElementById("rixoEditCarModelYear") as HTMLInputElement).value
    val carName = (document.getElementById("rixoEditCarName") as HTMLInputElement).value
    val auctionHouse = (document.getElementById("rixoEditAuctionHouse") as HTMLInputElement).value
    val stockLocation = (document.getElementById("rixoEditStockLocation") as HTMLInputElement).value
    val venueId = (document.getElementById("rixoEditVenueId") as HTMLInputElement).value
    val numberCut = (document.getElementById("rixoEditNumberCut") as HTMLInputElement).value
    val shaken = (document.getElementById("rixoEditShakenCheckbox") as HTMLInputElement).checked
    val numberCutString = (document.getElementById("rixoEditNumberCutString") as HTMLInputElement).value
    
    val updateData = js("{}")
    updateData.chassis = chassis
    updateData.carModelYear = carModelYear
    updateData.carName = carName
    updateData.auctionHouse = auctionHouse
    updateData.stockLocation = stockLocation
    updateData.venueId = venueId
    updateData.numberCut = if (numberCutString.isNotEmpty()) numberCutString else numberCut
    updateData.shaken = shaken
    
    // Update the purchase
    val requestInit = js("{}")
    requestInit.method = "PUT"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(updateData)
    
    window.fetch("/api/purchases/$id", requestInit)
        .then { response ->
            if (response.ok) {
                showMessage("Purchase updated successfully", "success")
                document.getElementById("rixoEditModal")?.remove()
                // Refresh the rows preview
                val buyingDate = (document.getElementById("buyingDate") as HTMLInputElement).value
                val rixoCompany = (document.getElementById("rixoCompany") as HTMLSelectElement).value
                if (buyingDate.isNotEmpty() && rixoCompany.isNotEmpty()) {
                    loadRowsForDateAndCompany()
                }
            } else {
                showMessage("Error updating purchase", "error")
            }
        }
        .catch { error ->
            console.error("Error updating purchase:", error)
            showMessage("Error updating purchase", "error")
        }
}

// Generate Rixo Request PDF
fun generateRixoRequestPdf() {
    val buyingDate = (document.getElementById("buyingDate") as HTMLInputElement).value
    val rixoCompany = (document.getElementById("rixoCompany") as HTMLSelectElement).value
    val headMessage = (document.getElementById("headMessage") as HTMLTextAreaElement).value
    val footerMessage = (document.getElementById("footerMessage") as HTMLTextAreaElement).value
    val extraMessage = (document.getElementById("extraMessage") as HTMLTextAreaElement).value
    val contactDetails = (document.getElementById("contactDetails") as HTMLTextAreaElement).value
    
    if (buyingDate.isEmpty() || rixoCompany.isEmpty()) {
        showMessage("Please select a buying date and Rixo company", "error")
        return
    }
    
    // Get selected purchase IDs
    val selectedIds = mutableListOf<Long>()
    val rowCheckboxes = document.querySelectorAll(".rixo-row-check")
    for (i in 0 until rowCheckboxes.length) {
        val checkbox = rowCheckboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val id = checkbox.getAttribute("data-id")?.toLongOrNull()
            if (id != null) {
                selectedIds.add(id)
            }
        }
    }
    
    if (selectedIds.isEmpty()) {
        showMessage("Please select at least one row to generate the PDF", "error")
        return
    }
    
    // Fetch selected purchase data
    window.fetch("/api/purchases").then { response ->
        if (response.ok) {
            response.json().then { allPurchases ->
                val purchasesArray = allPurchases as Array<dynamic>
                val selectedPurchases = mutableListOf<dynamic>()
                
                for (purchase in purchasesArray) {
                    val id = js("purchase.id").toString().toLongOrNull() ?: continue
                    if (selectedIds.contains(id)) {
                        selectedPurchases.add(purchase)
                    }
                }
                
                // Generate PDF using backend API
                generateRixoRequestPdfViaBackend(selectedPurchases, buyingDate, rixoCompany, headMessage, footerMessage, extraMessage, contactDetails, selectedIds)
            }
        }
    }
}

// Generate PDF using backend API
fun generateRixoRequestPdfViaBackend(purchases: List<dynamic>, buyingDate: String, rixoCompany: String, headMessage: String, footerMessage: String, extraMessage: String, contactDetails: String, selectedIds: List<Long>) {
    console.log("🔧 [DEBUG] generateRixoRequestPdfViaBackend called with selectedIds:", selectedIds)
    
    // Prepare transport data
    val transportData = js("{}")
    transportData.rixoCompany = rixoCompany
    transportData.buyingDate = buyingDate
    transportData.headMessage = headMessage
    transportData.footerMessage = footerMessage
    transportData.extraMessage = extraMessage
    transportData.contactDetails = contactDetails
    
    // Prepare request body
    val requestBody = js("{}")
    val jsArray = js("[]")
    selectedIds.forEach { id ->
        jsArray.push(id.toInt())
    }
    requestBody.ids = jsArray
    requestBody.transportData = transportData
    
    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(requestBody)
    
    console.log("🔧 [DEBUG] Calling backend API with requestBody:", requestBody)
    
    // Call backend API
    window.fetch("/api/purchases/rixo-transport-pdf", requestInit).then { response ->
        if (response.ok) {
            response.blob().then { blob ->
                // Create download link
                val url = js("URL.createObjectURL(blob)")
                val a = document.createElement("a") as HTMLAnchorElement
                a.href = url
                a.setAttribute("download", "rixo-transport-${js("Date.now()")}.pdf")
                document.body?.appendChild(a)
                a.click()
                document.body?.removeChild(a)
                js("URL.revokeObjectURL(url)")
                
                // Update rixoRequested status
                updateRixoRequestedStatus(selectedIds)
            }
        } else {
            console.error("❌ PDF generation failed:", response.status, response.statusText)
            showMessage("PDF generation failed: ${response.status} ${response.statusText}", "error")
        }
    }.catch { error ->
        console.error("❌ PDF generation error:", error)
        showMessage("PDF generation error: $error", "error")
    }
}

// Generate the actual PDF document
fun generateRixoRequestPdfDocument(purchases: List<dynamic>, buyingDate: String, rixoCompany: String, headMessage: String, footerMessage: String, extraMessage: String, contactDetails: String, selectedIds: List<Long>) {
    console.log("🔧 [DEBUG] generateRixoRequestPdfDocument called with selectedIds:", selectedIds)
    // Convert buying date to Japanese format
    val formattedDate = formatWithWeekday(buyingDate)
    val currentDate = js("new Date().toLocaleDateString('ja-JP', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })") as String
    
    // Split messages into lines for proper formatting
    val headMessageLines = headMessage.split("\n")
    val footerMessageLines = footerMessage.split("\n")
    
    // Create HTML content for PDF
    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { 
                    font-family: 'Hiragino Sans', 'Yu Gothic', sans-serif; 
                    margin: 20px; 
                    font-size: 12px;
                    line-height: 1.4;
                }
                .header { 
                    display: flex; 
                    justify-content: space-between; 
                    align-items: flex-start;
                    margin-bottom: 20px;
                }
                .title-section {
                    display: flex;
                    align-items: center;
                    gap: 10px;
                }
                .main-title {
                    font-size: 24px;
                    font-weight: bold;
                    margin: 0;
                }
                .company-name {
                    font-size: 18px;
                    margin: 0;
                }
                .date-info {
                    text-align: right;
                    font-size: 12px;
                }
                .head-message {
                    margin: 20px 0;
                    font-size: 14px;
                    line-height: 1.6;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 20px 0;
                    font-size: 11px;
                }
                th, td {
                    border: 1px solid #000;
                    padding: 8px 4px;
                    text-align: left;
                    vertical-align: top;
                }
                th {
                    background-color: #f0f0f0;
                    font-weight: bold;
                    text-align: center;
                }
                .footer-message {
                    margin: 20px 0;
                    font-size: 12px;
                    line-height: 1.5;
                }
                .extra-message {
                    margin: 10px 0;
                    font-size: 12px;
                    line-height: 1.5;
                }
                .contact-details {
                    text-align: right;
                    margin-top: 30px;
                    font-size: 12px;
                    line-height: 1.4;
                }
                .total-count {
                    text-align: right;
                    margin: 10px 0;
                    font-weight: bold;
                }
            </style>
        </head>
        <body>
            <div class="header">
                <div class="title-section">
                    <h1 class="main-title">陸送</h1>
                    <h2 class="company-name">${rixoCompany} 様</h2>
                </div>
                <div class="date-info">
                    日付 ${currentDate}
                </div>
            </div>
            
            <div class="head-message">
                ${headMessageLines.joinToString("<br>")}
            </div>
            
            <table>
                <thead>
                    <tr>
                        <th>日付</th>
                        <th>出品番号</th>
                        <th>型式・車体番号</th>
                        <th>年式</th>
                        <th>車名</th>
                        <th>取引先名</th>
                        <th>搬入先名</th>
                        <th>会場ID</th>
                        <th>ナンバーカット</th>
                    </tr>
                </thead>
                <tbody>
                    ${generateTableRows(purchases, formattedDate)}
                </tbody>
            </table>
            
            <div class="total-count">
                合計${purchases.size}台
            </div>
            
            <div class="footer-message">
                ${footerMessageLines.joinToString("<br>")}
            </div>
            
            ${if (extraMessage.isNotEmpty()) "<div class=\"extra-message\">${extraMessage}</div>" else ""}
            
            <div class="contact-details">
                ${contactDetails.replace("\n", "<br>")}
            </div>
        </body>
        </html>
    """
    
    // Create a new window for printing
    val printWindow = window.open("", "_blank")
    printWindow?.document?.write(htmlContent)
    printWindow?.document?.close()
    
    // Flag to ensure update only happens once
    var updateCalled = false
    
    fun updateStatusOnce() {
        if (!updateCalled) {
            updateCalled = true
            console.log("🔧 [DEBUG] updateStatusOnce called with IDs:", selectedIds)
            console.log("🔧 [DEBUG] selectedIds size:", selectedIds.size)
            updateRixoRequestedStatus(selectedIds)
        } else {
            console.log("🔧 [DEBUG] updateStatusOnce already called, skipping")
        }
    }
    
    // Wait for content to load, then print
    printWindow?.onload = {
        printWindow?.print()
        
        // Use setTimeout to ensure the print dialog has time to appear
        window.setTimeout({
            printWindow?.close()
            updateStatusOnce()
        }, 1000) // 1 second delay
    }
    
    // Fallback: if onload doesn't fire, update after a delay anyway
    window.setTimeout({
        updateStatusOnce()
    }, 3000) // 3 second fallback
    
    showMessage("PDF generated successfully for ${purchases.size} vehicles", "success")
}

// Generate table rows for the PDF
fun generateTableRows(purchases: List<dynamic>, formattedDate: String): String {
    val rows = StringBuilder()
    
    for (purchase in purchases) {
        val chassis = js("purchase.chassis")?.toString() ?: ""
        val carModelYear = js("purchase.carModelYear")?.toString() ?: ""
        val carName = js("purchase.carName")?.toString() ?: ""
        val clientName = js("purchase.clientName")?.toString() ?: ""
        val stockLocation = js("purchase.stockLocation")?.toString() ?: ""
        val venueId = js("purchase.venueId")?.toString() ?: ""
        val numberCut = js("purchase.numberCut")?.toString() ?: ""
        
        rows.append("""
            <tr>
                <td>${formattedDate}</td>
                <td>${chassis}</td>
                <td>${carModelYear}</td>
                <td>${carName}</td>
                <td>${clientName}</td>
                <td>${stockLocation}</td>
                <td>${venueId}</td>
                <td>${numberCut}</td>
            </tr>
        """)
    }
    
    return rows.toString()
}

// Update rixoRequested status to TRUE for selected purchases
fun updateRixoRequestedStatus(selectedIds: List<Long>) {
    console.log("🔧 [DEBUG] updateRixoRequestedStatus called with IDs:", selectedIds)
    
    if (selectedIds.isEmpty()) {
        console.log("❌ [DEBUG] No IDs to update")
        return
    }
    
    // Update each purchase individually
    var completedUpdates = 0
    var failedUpdates = 0
    
    selectedIds.forEach { id ->
        val updateData = js("""
            {
                "rixoRequested": "TRUE"
            }
        """)
        
        console.log("🔧 [DEBUG] Making PUT request to /api/purchases/$id with data:", updateData)
        
        window.fetch("/api/purchases/$id", js("""
            {
                method: "PUT",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(updateData)
            }
        """)).then { response ->
            console.log("🔧 [DEBUG] Response for ID $id: status=${response.status}, ok=${response.ok}")
            if (response.ok) {
                completedUpdates++
                console.log("Successfully updated rixoRequested for purchase ID: $id")
            } else {
                failedUpdates++
                console.error("Failed to update rixoRequested for purchase ID: $id, status: ${response.status}")
            }
            
            // Check if all updates are complete
            if (completedUpdates + failedUpdates == selectedIds.size) {
                if (failedUpdates == 0) {
                    showMessage("Successfully updated ${completedUpdates} purchases to rixoRequested = TRUE", "success")
                } else {
                    showMessage("Updated ${completedUpdates} purchases successfully, ${failedUpdates} failed", "warning")
                }
            }
        }.catch { error ->
            failedUpdates++
            console.error("Error updating rixoRequested for purchase ID: $id:", error)
            
            // Check if all updates are complete
            if (completedUpdates + failedUpdates == selectedIds.size) {
                if (failedUpdates == 0) {
                    showMessage("Successfully updated ${completedUpdates} purchases to rixoRequested = TRUE", "success")
                } else {
                    showMessage("Updated ${completedUpdates} purchases successfully, ${failedUpdates} failed", "warning")
                }
            }
        }
    }
}

fun loadSelectedCarsForRixoTransport(selectedIds: List<Long>) {
    console.log("Loading selected cars for Rixo Transport. Selected IDs:", selectedIds)
    if (selectedIds.isEmpty()) {
        document.getElementById("selectedCarsPreview")?.innerHTML = "<div style=\"color:#6b7280; font-size: 13px;\">No cars selected.</div>"
        return
    }
    
    window.fetch("/api/purchases").then { response ->
        console.log("API response status:", response.status)
        if (response.ok) {
            response.json().then { allPurchases ->
                console.log("Received purchases from API:", allPurchases)
                val purchasesArray = allPurchases as Array<dynamic>
                val selectedCars = mutableListOf<String>()
                val missingDataPurchases = mutableListOf<dynamic>()
                
                for (purchase in purchasesArray) {
                    val id = js("purchase.id").toString().toLongOrNull() ?: continue
                    if (selectedIds.contains(id)) {
                        val chassis = js("purchase.chassis")?.toString() ?: ""
                        val carName = js("purchase.carName")?.toString() ?: ""
                        selectedCars.add("$chassis - $carName")
                        
                        // Determine which fields are actually missing
                        val missingFields = mutableListOf<String>()
                        
                        // Check for missing core data fields
                        if ((js("purchase.carModelYear")?.toString() ?: "").isEmpty()) missingFields.add("carModelYear")
                        if ((js("purchase.carName")?.toString() ?: "").isEmpty()) missingFields.add("carName")
                        if ((js("purchase.clientName")?.toString() ?: "").isEmpty()) missingFields.add("clientName")
                        if ((js("purchase.stockLocation")?.toString() ?: "").isEmpty()) missingFields.add("stockLocation")
                        
                        val venueIdVal = js("purchase.venueId")?.toString() ?: ""
                        val numberCutVal = js("purchase.numberCut")?.toString() ?: ""
                        if (venueIdVal.isEmpty()) missingFields.add("venueId")
                        if (numberCutVal.isEmpty()) missingFields.add("numberCut")
                        
                        // Only add to the form when at least one field is missing
                        if (missingFields.isNotEmpty()) {
                            val missingPurchase = js("Object.assign({}, purchase)")
                            val missingFieldsArray = missingFields.toTypedArray()
                            js("missingPurchase.missingFields = missingFieldsArray")
                            missingDataPurchases.add(missingPurchase)
                        }
                    }
                }
                
                // Display selected cars
                val previewHtml = if (selectedCars.isNotEmpty()) {
                    "<div style=\"margin:8px 0 6px 0; font-weight:600;\">Selected Cars (${selectedCars.size})</div>" +
                    selectedCars.joinToString("<br>") { "<div style=\"color:#6b7280; font-size: 13px; margin: 2px 0;\">$it</div>" }
                } else {
                    "<div style=\"color:#6b7280; font-size: 13px;\">No cars found.</div>"
                }
                document.getElementById("selectedCarsPreview")?.innerHTML = previewHtml
                
                // Show section only if something is missing for at least one selected car
                if (missingDataPurchases.isNotEmpty()) {
                    renderMissingDataForRixoTransport(missingDataPurchases)
                    document.getElementById("fillMissingDataSection")?.setAttribute("style", "display: block;")
                } else {
                    document.getElementById("fillMissingDataSection")?.setAttribute("style", "display: none;")
                }
            }
        } else {
            document.getElementById("selectedCarsPreview")?.innerHTML = "<div style=\"color:#ef4444; font-size: 13px;\">Failed to load cars.</div>"
        }
    }.catch { error ->
        document.getElementById("selectedCarsPreview")?.innerHTML = "<div style=\"color:#ef4444; font-size: 13px;\">Error loading cars: ${error.message}</div>"
    }
}
fun renderMissingDataForRixoTransport(missingPurchases: List<dynamic>) {
    val content = document.getElementById("missingDataContent") ?: return
    val sb = StringBuilder()
    
    for (purchase in missingPurchases) {
        val id = js("purchase.id").toString()
        val chassis = js("purchase.chassis")?.toString() ?: ""
        val missingFields = js("purchase.missingFields") as Array<dynamic>
        
        sb.append("<div style=\"margin-bottom: 20px; padding: 15px; border: 1px solid #e5e7eb; border-radius: 8px; background: #f9fafb;\">")
        sb.append("<h4 style=\"margin: 0 0 10px 0; color: #111827;\">Purchase: $chassis</h4>")
        
        for (field in missingFields) {
            val fieldName = field as String
            val label = when (fieldName) {
                "carModelYear" -> "Car Model Year"
                "carName" -> "Car Name"
                "clientName" -> "Client Name"
                "stockLocation" -> "Stock Location"
                "venueId" -> "Venue ID"
                "numberCut" -> "Number Cut"
                else -> fieldName
            }
            
            // Only show fields that are actually missing (not Venue ID and Number Cut which are always shown)
            val shouldShow = when (fieldName) {
                "venueId", "numberCut" -> true // Always show these
                else -> {
                    // Check if the field is actually missing
                    val currentValue = when (fieldName) {
                        "carModelYear" -> js("purchase.carModelYear")?.toString() ?: ""
                        "carName" -> js("purchase.carName")?.toString() ?: ""
                        "clientName" -> js("purchase.clientName")?.toString() ?: ""
                        "stockLocation" -> js("purchase.stockLocation")?.toString() ?: ""
                        else -> ""
                    }
                    currentValue.isEmpty()
                }
            }
            
            if (shouldShow) {
                if (fieldName == "numberCut") {
                    // Special handling for number cut - show 4-box structure
                    sb.append("<div class=\"field\" style=\"margin-bottom: 10px;\">")
                    sb.append("<label style=\"display: block; margin-bottom: 8px; font-weight: 500; color: #374151;\">Number Cut Information</label>")
                    sb.append("<div style=\"display: grid; grid-template-columns: 1fr 1fr 1fr 1fr; gap: 8px; margin-bottom: 8px;\">")
                    
                    // Box 1: Place Name (Japanese)
                    sb.append("<div>")
                    sb.append("<label style=\"display: block; margin-bottom: 4px; font-size: 12px; font-weight: 500; color: #6b7280;\">Place Name</label>")
                    sb.append("<select id=\"missing_${id}_numberCutPlace\" style=\"width: 100%; padding: 6px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 12px;\">")
                    sb.append("<option value=\"\">Select Place</option>")
                    sb.append("<option value=\"札幌\">札幌 (Sapporo)</option>")
                    sb.append("<option value=\"函館\">函館 (Hakodate)</option>")
                    sb.append("<option value=\"旭川\">旭川 (Asahikawa)</option>")
                    sb.append("<option value=\"室蘭\">室蘭 (Muroran)</option>")
                    sb.append("<option value=\"釧路\">釧路 (Kushiro)</option>")
                    sb.append("<option value=\"帯広\">帯広 (Obihiro)</option>")
                    sb.append("<option value=\"十勝\">十勝 (Tokachi)</option>")
                    sb.append("<option value=\"北見\">北見 (Kitami)</option>")
                    sb.append("<option value=\"知床\">知床 (Shiretoko)</option>")
                    sb.append("<option value=\"苫小牧\">苫小牧 (Tomakomai)</option>")
                    sb.append("<option value=\"青森\">青森 (Aomori)</option>")
                    sb.append("<option value=\"弘前\">弘前 (Hirosaki)</option>")
                    sb.append("<option value=\"岩手\">岩手 (Iwate)</option>")
                    sb.append("<option value=\"盛岡\">盛岡 (Morioka)</option>")
                    sb.append("<option value=\"平泉\">平泉 (Hiraizumi)</option>")
                    sb.append("<option value=\"宮城\">宮城 (Miyagi)</option>")
                    sb.append("<option value=\"仙台\">仙台 (Sendai)</option>")
                    sb.append("<option value=\"八戸\">八戸 (Hachinohe)</option>")
                    sb.append("<option value=\"秋田\">秋田 (Akita)</option>")
                    sb.append("<option value=\"山形\">山形 (Yamagata)</option>")
                    sb.append("<option value=\"福島\">福島 (Fukushima)</option>")
                    sb.append("<option value=\"茨城\">茨城 (Ibaraki)</option>")
                    sb.append("<option value=\"栃木\">栃木 (Tochigi)</option>")
                    sb.append("<option value=\"群馬\">群馬 (Gunma)</option>")
                    sb.append("<option value=\"埼玉\">埼玉 (Saitama)</option>")
                    sb.append("<option value=\"千葉\">千葉 (Chiba)</option>")
                    sb.append("<option value=\"東京\">東京 (Tokyo)</option>")
                    sb.append("<option value=\"神奈川\">神奈川 (Kanagawa)</option>")
                    sb.append("<option value=\"新潟\">新潟 (Niigata)</option>")
                    sb.append("<option value=\"富山\">富山 (Toyama)</option>")
                    sb.append("<option value=\"石川\">石川 (Ishikawa)</option>")
                    sb.append("<option value=\"福井\">福井 (Fukui)</option>")
                    sb.append("<option value=\"山梨\">山梨 (Yamanashi)</option>")
                    sb.append("<option value=\"長野\">長野 (Nagano)</option>")
                    sb.append("<option value=\"岐阜\">岐阜 (Gifu)</option>")
                    sb.append("<option value=\"静岡\">静岡 (Shizuoka)</option>")
                    sb.append("<option value=\"愛知\">愛知 (Aichi)</option>")
                    sb.append("<option value=\"三重\">三重 (Mie)</option>")
                    sb.append("<option value=\"滋賀\">滋賀 (Shiga)</option>")
                    sb.append("<option value=\"京都\">京都 (Kyoto)</option>")
                    sb.append("<option value=\"大阪\">大阪 (Osaka)</option>")
                    sb.append("<option value=\"兵庫\">兵庫 (Hyogo)</option>")
                    sb.append("<option value=\"奈良\">奈良 (Nara)</option>")
                    sb.append("<option value=\"和歌山\">和歌山 (Wakayama)</option>")
                    sb.append("<option value=\"鳥取\">鳥取 (Tottori)</option>")
                    sb.append("<option value=\"島根\">島根 (Shimane)</option>")
                    sb.append("<option value=\"岡山\">岡山 (Okayama)</option>")
                    sb.append("<option value=\"広島\">広島 (Hiroshima)</option>")
                    sb.append("<option value=\"山口\">山口 (Yamaguchi)</option>")
                    sb.append("<option value=\"徳島\">徳島 (Tokushima)</option>")
                    sb.append("<option value=\"香川\">香川 (Kagawa)</option>")
                    sb.append("<option value=\"愛媛\">愛媛 (Ehime)</option>")
                    sb.append("<option value=\"高知\">高知 (Kochi)</option>")
                    sb.append("<option value=\"福岡\">福岡 (Fukuoka)</option>")
                    sb.append("<option value=\"佐賀\">佐賀 (Saga)</option>")
                    sb.append("<option value=\"長崎\">長崎 (Nagasaki)</option>")
                    sb.append("<option value=\"熊本\">熊本 (Kumamoto)</option>")
                    sb.append("<option value=\"大分\">大分 (Oita)</option>")
                    sb.append("<option value=\"宮崎\">宮崎 (Miyazaki)</option>")
                    sb.append("<option value=\"鹿児島\">鹿児島 (Kagoshima)</option>")
                    sb.append("<option value=\"沖縄\">沖縄 (Okinawa)</option>")
                    sb.append("</select>")
                    sb.append("</div>")
                    
                    // Box 2: Number (English)
                    sb.append("<div>")
                    sb.append("<label style=\"display: block; margin-bottom: 4px; font-size: 12px; font-weight: 500; color: #6b7280;\">Number</label>")
                    sb.append("<input type=\"number\" id=\"missing_${id}_numberCutNumber1\" placeholder=\"Number\" style=\"width: 100%; padding: 6px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 12px;\" />")
                    sb.append("</div>")
                    
                    // Box 3: Hiragana Character
                    sb.append("<div>")
                    sb.append("<label style=\"display: block; margin-bottom: 4px; font-size: 12px; font-weight: 500; color: #6b7280;\">Hiragana</label>")
                    sb.append("<select id=\"missing_${id}_numberCutHiragana\" style=\"width: 100%; padding: 6px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 12px;\">")
                    sb.append("<option value=\"\">Select</option>")
                    sb.append("<option value=\"あ\">あ (a)</option>")
                    sb.append("<option value=\"い\">い (i)</option>")
                    sb.append("<option value=\"う\">う (u)</option>")
                    sb.append("<option value=\"え\">え (e)</option>")
                    sb.append("<option value=\"お\">お (o)</option>")
                    sb.append("<option value=\"か\">か (ka)</option>")
                    sb.append("<option value=\"き\">き (ki)</option>")
                    sb.append("<option value=\"く\">く (ku)</option>")
                    sb.append("<option value=\"け\">け (ke)</option>")
                    sb.append("<option value=\"こ\">こ (ko)</option>")
                    sb.append("<option value=\"さ\">さ (sa)</option>")
                    sb.append("<option value=\"し\">し (shi)</option>")
                    sb.append("<option value=\"す\">す (su)</option>")
                    sb.append("<option value=\"せ\">せ (se)</option>")
                    sb.append("<option value=\"そ\">そ (so)</option>")
                    sb.append("<option value=\"た\">た (ta)</option>")
                    sb.append("<option value=\"ち\">ち (chi)</option>")
                    sb.append("<option value=\"つ\">つ (tsu)</option>")
                    sb.append("<option value=\"て\">て (te)</option>")
                    sb.append("<option value=\"と\">と (to)</option>")
                    sb.append("<option value=\"な\">な (na)</option>")
                    sb.append("<option value=\"に\">に (ni)</option>")
                    sb.append("<option value=\"ぬ\">ぬ (nu)</option>")
                    sb.append("<option value=\"ね\">ね (ne)</option>")
                    sb.append("<option value=\"の\">の (no)</option>")
                    sb.append("<option value=\"は\">は (ha)</option>")
                    sb.append("<option value=\"ひ\">ひ (hi)</option>")
                    sb.append("<option value=\"ふ\">ふ (fu)</option>")
                    sb.append("<option value=\"へ\">へ (he)</option>")
                    sb.append("<option value=\"ほ\">ほ (ho)</option>")
                    sb.append("<option value=\"ま\">ま (ma)</option>")
                    sb.append("<option value=\"み\">み (mi)</option>")
                    sb.append("<option value=\"む\">む (mu)</option>")
                    sb.append("<option value=\"め\">め (me)</option>")
                    sb.append("<option value=\"も\">も (mo)</option>")
                    sb.append("<option value=\"や\">や (ya)</option>")
                    sb.append("<option value=\"ゆ\">ゆ (yu)</option>")
                    sb.append("<option value=\"よ\">よ (yo)</option>")
                    sb.append("<option value=\"ら\">ら (ra)</option>")
                    sb.append("<option value=\"り\">り (ri)</option>")
                    sb.append("<option value=\"る\">る (ru)</option>")
                    sb.append("<option value=\"れ\">れ (re)</option>")
                    sb.append("<option value=\"ろ\">ろ (ro)</option>")
                    sb.append("<option value=\"わ\">わ (wa)</option>")
                    sb.append("<option value=\"ゐ\">ゐ (wi)</option>")
                    sb.append("<option value=\"ゑ\">ゑ (we)</option>")
                    sb.append("<option value=\"を\">を (wo)</option>")
                    sb.append("<option value=\"ん\">ん (n)</option>")
                    sb.append("</select>")
                    sb.append("</div>")
                    
                    // Box 4: Number (English)
                    sb.append("<div>")
                    sb.append("<label style=\"display: block; margin-bottom: 4px; font-size: 12px; font-weight: 500; color: #6b7280;\">Number</label>")
                    sb.append("<input type=\"number\" id=\"missing_${id}_numberCutNumber2\" placeholder=\"Number\" style=\"width: 100%; padding: 6px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 12px;\" />")
                    sb.append("</div>")
                    
                    sb.append("</div>")
                    
                    // Generated string display
                    sb.append("<div style=\"margin-top: 8px;\">")
                    sb.append("<label style=\"display: block; margin-bottom: 4px; font-size: 12px; font-weight: 500; color: #6b7280;\">Generated Number Cut String:</label>")
                    sb.append("<input type=\"text\" id=\"missing_${id}_numberCutString\" readonly style=\"width: 100%; padding: 6px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 12px; background-color: #f9f9f9;\" placeholder=\"Will be generated automatically\" />")
                    sb.append("</div>")
                    
                    sb.append("</div>")
                } else {
                    // Regular field handling
                    sb.append("<div class=\"field\" style=\"margin-bottom: 10px;\">")
                    sb.append("<label for=\"missing_${id}_${fieldName}\" style=\"display: block; margin-bottom: 4px; font-weight: 500; color: #374151;\">$label</label>")
                    sb.append("<input class=\"input\" type=\"text\" id=\"missing_${id}_${fieldName}\" placeholder=\"Enter $label\" style=\"width: 100%; padding: 8px; border: 1px solid #d1d5db; border-radius: 6px;\" />")
                    sb.append("</div>")
                }
            }
        }
        
        sb.append("</div>")
    }
    
    content.innerHTML = sb.toString()
    
    // Set up number cut listeners for all purchases
    setupRixoTransportNumberCutListeners()
}

fun setupRixoTransportNumberCutListeners() {
    // Find all number cut fields and set up listeners
    val allInputs = document.querySelectorAll("input, select")
    for (i in 0 until allInputs.length) {
        val element = allInputs[i] as HTMLElement
        val id = element.id
        
        if (id.contains("numberCutPlace") || id.contains("numberCutNumber1") || 
            id.contains("numberCutHiragana") || id.contains("numberCutNumber2")) {
            
            // Extract the purchase ID from the element ID
            val purchaseId = id.substringAfter("missing_").substringBefore("_")
            
            // Set up the listener
            element.addEventListener("change", { _: Event ->
                generateRixoTransportNumberCutString(purchaseId)
            })
            element.addEventListener("input", { _: Event ->
                generateRixoTransportNumberCutString(purchaseId)
            })
        }
    }
}
fun generateRixoTransportNumberCutString(purchaseId: String) {
    val place = (document.getElementById("missing_${purchaseId}_numberCutPlace") as HTMLSelectElement?)?.value ?: ""
    val number1 = (document.getElementById("missing_${purchaseId}_numberCutNumber1") as HTMLInputElement?)?.value ?: ""
    val hiragana = (document.getElementById("missing_${purchaseId}_numberCutHiragana") as HTMLSelectElement?)?.value ?: ""
    val number2 = (document.getElementById("missing_${purchaseId}_numberCutNumber2") as HTMLInputElement?)?.value ?: ""
    
    val numberCutString = if (place.isNotEmpty() && number1.isNotEmpty() && hiragana.isNotEmpty() && number2.isNotEmpty()) {
        "$place$number1$hiragana$number2"
    } else {
        ""
    }
    
    (document.getElementById("missing_${purchaseId}_numberCutString") as HTMLInputElement?)?.value = numberCutString
}

fun setupRixoTransportPageListeners() {
    // Cancel button
    document.getElementById("cancelRixoTransportBtn")?.addEventListener("click", { _: Event ->
        window.close()
    })
    
    // Date formatting for transportDate field
    document.getElementById("transportDate")?.addEventListener("change", { ev: Event ->
        val input = ev.target as HTMLInputElement
        val dateValue = input.value
        val formattedDiv = document.getElementById("transportDateFormatted") as HTMLElement?
        if (dateValue.isNotEmpty()) {
            val formattedDate = formatWithWeekday(dateValue)
            formattedDiv?.textContent = formattedDate
        } else {
            formattedDiv?.textContent = ""
        }
    })
    
    // Form submission
    document.getElementById("rixoTransportForm")?.addEventListener("submit", { ev: Event ->
        ev.preventDefault()
        val selectedIds = js("window.selectedPurchasesForRixoTransport") as Array<dynamic>?
        if (selectedIds != null && selectedIds.isNotEmpty()) {
            val idsList = selectedIds.mapNotNull { js("it").toString().toLongOrNull() }
            collectRixoTransportDataAndGenerate(idsList)
        } else {
            showMessage("No cars selected", "error")
        }
    })
}
fun collectRixoTransportDataAndGenerate(selectedIds: List<Long>) {
    console.log("🔍 collectRixoTransportDataAndGenerate called with IDs:", selectedIds)
    
    // SIMPLIFIED APPROACH: Directly update each selected purchase with missing data
    val updates = mutableListOf<dynamic>()
    
    for (purchaseId in selectedIds) {
        val updateData = js("{}")
        var hasUpdates = false
        
        // Check for missing data inputs for this specific purchase
        val nodeList = document.querySelectorAll("input[id^='missing_${purchaseId}_']")
        console.log("🔍 Found ${nodeList.length} inputs for purchase $purchaseId")
        
        for (i in 0 until nodeList.length) {
            val el = nodeList.item(i) as? HTMLInputElement ?: continue
            val parts = el.id.split("_")
            if (parts.size >= 3) {
                val field = parts[2]
                val value = el.value.trim()
                
                if (value.isNotEmpty()) {
                    console.log("🔍 Found value for $field: $value")
                    js("updateData[field] = value")
                    hasUpdates = true
                }
            }
        }
        
        if (hasUpdates) {
            js("updateData.id = purchaseId")
            updates.add(updateData)
            console.log("🔍 Added update for purchase $purchaseId:", updateData)
        }
    }
    
    if (updates.isEmpty()) {
        console.log("🔍 No updates to make, proceeding to PDF generation")
        generateRixoTransportPdf(selectedIds)
        return
    }
    
    // Update purchases one by one
    updatePurchasesForRixoTransport(updates, selectedIds)
}

fun updatePurchasesForRixoTransport(updates: List<dynamic>, selectedIds: List<Long>) {
    console.log("🔍 updatePurchasesForRixoTransport called with ${updates.size} updates")
    
    var completedUpdates = 0
    val totalUpdates = updates.size
    
    for (update in updates) {
        val purchaseId = js("update.id").toString().toLongOrNull() ?: continue
        console.log("🔍 Updating purchase $purchaseId with data:", update)
        
        window.fetch("/api/purchases/$purchaseId", js("""
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(update)
            }
        """)).then { response ->
            completedUpdates++
            console.log("🔍 Update $completedUpdates/$totalUpdates completed for purchase $purchaseId, status: ${response.status}")
            
            if (completedUpdates == totalUpdates) {
                console.log("✅ All updates completed, proceeding to PDF generation")
                showMessage("Missing data saved successfully!", "success")
                
                // Wait a moment for database to commit, then generate PDF
                window.setTimeout({
                    generateRixoTransportPdf(selectedIds)
                }, 1000)
            }
        }.catch { error ->
            console.error("❌ Error updating purchase $purchaseId:", error)
            completedUpdates++
            
            if (completedUpdates == totalUpdates) {
                showMessage("Some updates failed, but proceeding to PDF generation", "warning")
                window.setTimeout({
                    generateRixoTransportPdf(selectedIds)
                }, 1000)
            }
        }
    }
}

fun generateRixoTransportPdf(selectedIds: List<Long>) {
    showMessage("Generating Rixo Transport PDF...", "info")
    
    // Collect form data
    val transportData = js("{}")
    transportData.transportDate = (document.getElementById("transportDate") as HTMLInputElement).value
    
    // Collect ALL data from missing data form for each purchase
    val purchaseData = js("[]")
    for (id in selectedIds) {
        val purchaseInfo = js("{}")
        js("purchaseInfo.id = id")
        
        // Collect all possible fields from the form
        val fields = arrayOf("carModelYear", "carName", "clientName", "stockLocation", "venueId")
        for (field in fields) {
            val input = document.getElementById("missing_${id}_${field}") as HTMLInputElement?
            val value = input?.value?.trim() ?: ""
            if (value.isNotEmpty()) {
                when (field) {
                    "carModelYear" -> js("purchaseInfo.carModelYear = value")
                    "carName" -> js("purchaseInfo.carName = value")
                    "clientName" -> js("purchaseInfo.clientName = value")
                    "stockLocation" -> js("purchaseInfo.stockLocation = value")
                    "venueId" -> js("purchaseInfo.venueId = value")
                }
            }
        }
        
        // Special handling for number cut - get from the generated string field
        val numberCutInput = document.getElementById("missing_${id}_numberCutString") as HTMLInputElement?
        val numberCutValue = numberCutInput?.value?.trim() ?: ""
        if (numberCutValue.isNotEmpty()) {
            js("purchaseInfo.numberCut = numberCutValue")
        }
        js("purchaseData.push(purchaseInfo)")
    }
    transportData.purchaseData = purchaseData
    
    // Create request body
    val requestBody = js("{}")
    val jsArray = js("[]")
    selectedIds.forEach { id ->
        jsArray.push(id.toInt())
    }
    requestBody.ids = jsArray
    requestBody.transportData = transportData
    
    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(requestBody)
    
    window.fetch("/api/purchases/rixo-transport-pdf", requestInit).then { response ->
        if (response.ok) {
            response.blob().then { blob ->
                // Store the blob for later use
                js("window.generatedRixoTransportPdfBlob = blob")
                
                // Show PDF generation success modal
                showRixoTransportPdfGenerationSuccessModal(blob)
                
                showMessage("Rixo Transport PDF generated successfully!", "success")
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

fun showRixoTransportPdfGenerationSuccessModal(blob: dynamic) {
    val modal = document.createElement("div")
    modal.id = "rixoTransportPdfSuccessModal"
    modal.setAttribute("style", "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0, 0, 0, 0.5); display: flex; align-items: center; justify-content: center; z-index: 1000;")
    
    modal.innerHTML = """
        <div style="background-color: white; padding: 30px; border-radius: 8px; min-width: 400px; max-width: 500px; text-align: center;">
            <div style="margin-bottom: 20px;">
                <div style="width: 60px; height: 60px; background-color: #28a745; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 15px;">
                    <span style="color: white; font-size: 24px; font-weight: bold;">✓</span>
                </div>
                <h2 style="margin: 0; color: #333;">Rixo Transport PDF Generated!</h2>
                <p style="margin: 10px 0 0; color: #666;">Your land transportation report has been generated. What would you like to do next?</p>
            </div>
            
            <div style="display: flex; gap: 15px; justify-content: center; margin-top: 25px;">
                <button id="downloadRixoTransportPdfBtn" style="padding: 12px 24px; background-color: #28a745; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 8px;">
                    <span>📥</span>
                    Download PDF
                </button>
                <button id="sendRixoTransportEmailBtn" style="padding: 12px 24px; background-color: #dc3545; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 8px;">
                    <span>📧</span>
                    Send in Gmail
                </button>
            </div>
            
            <div style="margin-top: 20px;">
                <button id="closeRixoTransportPdfModalBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">
                    Close
                </button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Event listeners
    document.getElementById("downloadRixoTransportPdfBtn")?.addEventListener("click", { _: Event ->
        downloadRixoTransportPdf(blob)
        document.body?.removeChild(modal)
    })
    
    document.getElementById("sendRixoTransportEmailBtn")?.addEventListener("click", { _: Event ->
        sendRixoTransportPdfViaGmail(blob)
        document.body?.removeChild(modal)
    })
    
    document.getElementById("closeRixoTransportPdfModalBtn")?.addEventListener("click", { _: Event ->
        document.body?.removeChild(modal)
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event ->
        if (event.target == modal) {
            document.body?.removeChild(modal)
        }
    })
}

fun downloadRixoTransportPdf(blob: dynamic) {
    val url = js("window.URL.createObjectURL(blob)")
    val a = document.createElement("a") as HTMLAnchorElement
    a.setAttribute("href", url)
    a.setAttribute("download", "rixo-transport-${js("Date.now()")}.pdf")
    document.body?.appendChild(a)
    a.click()
    document.body?.removeChild(a)
    js("window.URL.revokeObjectURL(url)")
    
    showMessage("Rixo Transport PDF downloaded successfully!", "success")
}

fun sendRixoTransportPdfViaGmail(blob: dynamic) {
    // Placeholder for Gmail functionality
    showMessage("Gmail functionality will be implemented later", "info")
    console.log("Gmail send functionality - blob size:", js("blob.size"))
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

// Collect missing Rixo data inputs rendered on the invoice page, update backend, then generate PDF
fun collectMissingFromInvoiceAndGenerate(selectedIds: List<Long>) {
    console.log("🔍 collectMissingFromInvoiceAndGenerate called with IDs:", selectedIds)
    
    // DEBUG: Check all missing data inputs on the page
    val allMissingInputs = document.querySelectorAll("input[id^='missing_']")
    console.log("🔍 DEBUG: Found ${allMissingInputs.length} total missing data inputs on page")
    
    for (i in 0 until allMissingInputs.length) {
        val el = allMissingInputs.item(i) as? HTMLInputElement ?: continue
        console.log("🔍 DEBUG: Input ID: ${el.id}, Value: '${el.value}'")
    }
    
    // SIMPLIFIED APPROACH: Directly update each selected purchase with missing data
    val updates = mutableListOf<dynamic>()
    
    for (purchaseId in selectedIds) {
        val updateData = js("{}")
        var hasUpdates = false
        
        // Check for missing data inputs for this specific purchase
        val nodeList = document.querySelectorAll("input[id^='missing_${purchaseId}_']")
        console.log("🔍 Found ${nodeList.length} inputs for purchase $purchaseId")
        
        for (i in 0 until nodeList.length) {
            val el = nodeList.item(i) as? HTMLInputElement ?: continue
            val parts = el.id.split("_")
            if (parts.size >= 3) {
                val field = parts[2]
                val value = el.value.trim()
                
                console.log("🔍 DEBUG: Processing field $field with value '$value'")
                
                if (value.isNotEmpty()) {
                    console.log("🔍 Found value for $field: $value")
                    js("updateData[field] = value")
                    hasUpdates = true
                }
            }
        }
        
        if (hasUpdates) {
            js("updateData.id = purchaseId")
            updates.add(updateData)
            console.log("🔍 Added update for purchase $purchaseId:", updateData)
        } else {
            console.log("🔍 No updates found for purchase $purchaseId")
        }
    }
    
    if (updates.isEmpty()) {
        console.log("🔍 No updates to make, proceeding to PDF generation")
        collectInvoiceDataAndGeneratePdf(selectedIds)
        return
    }
    
    // Update purchases one by one
    updatePurchasesAndThenGenerate(updates, selectedIds)
}

fun updatePurchasesAndThenGenerate(updates: List<dynamic>, selectedIds: List<Long>) {
    console.log("🔍 updatePurchasesAndThenGenerate called with ${updates.size} updates")
    
    var completedUpdates = 0
    val totalUpdates = updates.size
    
    for (update in updates) {
        val purchaseId = js("update.id").toString().toLongOrNull() ?: continue
        console.log("🔍 Updating purchase $purchaseId with data:", update)
        
        window.fetch("/api/purchases/$purchaseId", js("""
            {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(update)
            }
        """)).then { response ->
            completedUpdates++
            console.log("🔍 Update $completedUpdates/$totalUpdates completed for purchase $purchaseId, status: ${response.status}")
            
            if (completedUpdates == totalUpdates) {
                console.log("✅ All updates completed, proceeding to PDF generation")
                showMessage("Missing data saved successfully!", "success")
                
                // Wait a moment for database to commit, then generate PDF
                window.setTimeout({
                    collectInvoiceDataAndGeneratePdf(selectedIds)
                }, 1000)
            }
        }.catch { error ->
            console.error("❌ Error updating purchase $purchaseId:", error)
            completedUpdates++
            
            if (completedUpdates == totalUpdates) {
                showMessage("Some updates failed, but proceeding to PDF generation", "warning")
                window.setTimeout({
                    collectInvoiceDataAndGeneratePdf(selectedIds)
                }, 1000)
            }
        }
    }
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
    
    // Just show the count without making an API call to avoid errors
    val count = ids.size
    container.innerHTML = "<div style=\"margin:8px 0 6px 0; font-weight:600;\">Selected Cars (${count})</div>" +
        "<div style=\"color:#6b7280; font-size: 13px;\">Ready for PDF generation</div>"
}

fun collectMissingRixoDataFromForm(): dynamic {
    val missingData = js("[]")
    
    // Find all missing data inputs
    val nodeList = document.querySelectorAll("input[id^='missing_']")
    for (i in 0 until nodeList.length) {
        val el = nodeList.item(i) as? HTMLInputElement ?: continue
        val parts = el.id.split("_")
        if (parts.size >= 3) {
            val purchaseId = parts[1]
            val field = parts[2]
            val value = el.value.trim()
            
            if (value.isNotEmpty()) {
                val dataItem = js("{}")
                js("dataItem.purchaseId = purchaseId")
                js("dataItem.field = field")
                js("dataItem.value = value")
                missingData.push(dataItem)
            }
        }
    }
    
    return missingData
}

fun generateRixoPdfWithInvoiceData(selectedIds: List<Long>, invoiceData: dynamic) {
    showMessage("Generating Rixo PDF...", "info")
    console.log("Selected IDs for PDF generation:", selectedIds)
    console.log("Invoice data:", invoiceData)
    
    // Collect missing Rixo data from the form
    val missingRixoData = collectMissingRixoDataFromForm()
    console.log("Missing Rixo data:", missingRixoData)
    
    // Create request body with selected IDs, invoice data, and missing Rixo data
    val requestBody = js("{}")
    val jsArray = js("[]")
    selectedIds.forEach { id ->
        // Convert Kotlin Long to JavaScript primitive number
        jsArray.push(id.toInt())
    }
    requestBody.ids = jsArray
    requestBody.invoiceData = invoiceData
    requestBody.missingRixoData = missingRixoData
    
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
    
    window.fetch("/api/purchases/rixo-pdf", requestInit).then { response ->
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
    a.setAttribute("download", "invoice.pdf")
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
        
        window.fetch("/api/purchases/$id", requestInit).then { response ->
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

fun openSidebar() {
    val sidebar = document.getElementById("sidebar") as HTMLElement?
    val overlay = document.getElementById("sidebarOverlay") as HTMLElement?
    
    sidebar?.style?.left = "0px"
    overlay?.style?.display = "block"
}

fun closeSidebar() {
    val sidebar = document.getElementById("sidebar") as HTMLElement?
    val overlay = document.getElementById("sidebarOverlay") as HTMLElement?
    
    sidebar?.style?.left = "-250px"
    overlay?.style?.display = "none"
}

// Role Request Functions
fun loadRoleRequests() {
    window.fetch("/api/role-requests/pending")
        .then { response ->
            if (response.ok) {
                response.json().then { data ->
                    val requests = data.asDynamic().data
                    displayRoleRequests(requests)
                }
            } else {
                showMessage("Failed to load role requests", "error")
            }
        }
        .catch { error ->
            showMessage("Failed to load role requests: $error", "error")
        }
}

fun displayRoleRequests(requests: dynamic) {
    val table = document.getElementById("roleRequestsTable")!!
    
    if (js("requests.length") == 0) {
        table.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No pending role requests.
            </div>
        """
        return
    }
    
    val tableHTML = StringBuilder()
    tableHTML.append("""
        <table style="width: 100%; border-collapse: collapse;">
            <thead>
                <tr style="background-color: #fff3cd;">
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ffeaa7;">User</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ffeaa7;">Current Role</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ffeaa7;">Requested Role</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ffeaa7;">Reason</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ffeaa7;">Date</th>
                    <th style="padding: 12px; text-align: left; border-bottom: 1px solid #ffeaa7;">Actions</th>
                </tr>
            </thead>
            <tbody>
    """)
    
    val requestsArray = requests as Array<dynamic>
    for (i in 0 until requestsArray.size) {
        val request = requestsArray[i]
        tableHTML.append("""
            <tr style="border-bottom: 1px solid #ffeaa7;">
                <td style="padding: 12px;">
                    <div style="font-weight: bold;">${request.userName ?: "Unknown User"}</div>
                    <div style="font-size: 12px; color: #666;">${request.userEmail ?: "No email"}</div>
                </td>
                <td style="padding: 12px;">
                    <span style="padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; 
                        background-color: ${getRoleColor(request.currentRole ?: "VIEWER")}; color: white;">
                        ${request.currentRole ?: "VIEWER"}
                    </span>
                </td>
                <td style="padding: 12px;">
                    <span style="padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; 
                        background-color: ${getRoleColor(request.requestedRole)}; color: white;">
                        ${request.requestedRole}
                    </span>
                </td>
                <td style="padding: 12px; max-width: 200px; word-wrap: break-word;">${request.reason ?: "No reason provided"}</td>
                <td style="padding: 12px; font-size: 12px;">${request.createdAt}</td>
                <td style="padding: 12px;">
                    <button class="approve-request-btn" data-id="${request.id}" style="padding: 6px 12px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 5px;">Approve</button>
                    <button class="reject-request-btn" data-id="${request.id}" style="padding: 6px 12px; background-color: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">Reject</button>
                </td>
            </tr>
        """)
    }
    
    tableHTML.append("""
            </tbody>
        </table>
    """)
    
    table.innerHTML = tableHTML.toString()
    
    // Add event listeners for approve/reject buttons
    val approveButtons = document.querySelectorAll(".approve-request-btn")
    for (i in 0 until approveButtons.length) {
        val btn = approveButtons.item(i) as HTMLElement
        btn.addEventListener("click", { event ->
            val requestId = (event.target as HTMLElement).getAttribute("data-id")?.toLongOrNull()
            if (requestId != null) {
                reviewRoleRequest(requestId, "APPROVED")
            }
        })
    }
    
    val rejectButtons = document.querySelectorAll(".reject-request-btn")
    for (i in 0 until rejectButtons.length) {
        val btn = rejectButtons.item(i) as HTMLElement
        btn.addEventListener("click", { event ->
            val requestId = (event.target as HTMLElement).getAttribute("data-id")?.toLongOrNull()
            if (requestId != null) {
                reviewRoleRequest(requestId, "REJECTED")
            }
        })
    }
}
fun reviewRoleRequest(requestId: Long, status: String) {
    val currentUserId = window.localStorage.getItem("authUserId")?.toLongOrNull()
    if (currentUserId == null) {
        showMessage("User not authenticated", "error")
        return
    }
    
    val body = js("({method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({status:status, reviewComment:'Reviewed by admin'})})")
    
    window.fetch("/api/role-requests/$requestId/review/$currentUserId", body)
        .then { response ->
            if (response.ok) {
                response.json().then { data ->
                    showMessage("Role request $status successfully", "success")
                    loadRoleRequests()
                    loadUsers() // Refresh users to show updated roles
                }
            } else {
                response.json().then { errorData ->
                    showMessage("Failed to review request: ${errorData.asDynamic().message}", "error")
                }
            }
        }
        .catch { error ->
            showMessage("Failed to review request: $error", "error")
        }
}

// Add role request functionality for regular users
fun showRoleRequestForm() {
    val currentUserRole = window.localStorage.getItem("authUserRole") ?: "VIEWER"
    val currentUserId = window.localStorage.getItem("authUserId")?.toLongOrNull()
    
    if (currentUserId == null) {
        showMessage("User not authenticated", "error")
        return
    }
    
    // Determine what roles the user can request
    val availableRoles = when (currentUserRole) {
        "VIEWER" -> listOf("EDITOR", "ADMIN")
        "EDITOR" -> listOf("ADMIN")
        else -> emptyList()
    }
    
    if (availableRoles.isEmpty()) {
        showMessage("You cannot request any higher roles", "error")
        return
    }
    
    val roleOptions = availableRoles.joinToString("") { role ->
        "<option value='$role'>$role</option>"
    }
    
    val modal = document.createElement("div") as HTMLElement
    modal.style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; justify-content: center; align-items: center;
    """
    
    modal.innerHTML = """
        <div style="background: white; padding: 30px; border-radius: 8px; width: 500px; max-width: 90%;">
            <h3 style="margin-top: 0; margin-bottom: 20px; color: #333;">Request Role Upgrade</h3>
            <form id="roleRequestForm">
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Requested Role:</label>
                    <select id="requestedRole" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" required>
                        <option value="">Select a role</option>
                        $roleOptions
                    </select>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Send Request To Admin:</label>
                    <select id="adminSelect" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;" required>
                        <option value="">Loading admins...</option>
                    </select>
                </div>
                <div style="margin-bottom: 15px;">
                    <label style="display: block; margin-bottom: 5px; font-weight: bold;">Reason:</label>
                    <textarea id="requestReason" style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; height: 80px;" placeholder="Please explain why you need this role upgrade..." required></textarea>
                </div>
                <div style="display: flex; gap: 10px; justify-content: flex-end;">
                    <button type="button" id="cancelRoleRequest" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                    <button type="submit" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Submit Request</button>
                </div>
            </form>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Load admins for dropdown
    loadAdminsForRoleRequest()
    
    // Event listeners
    document.getElementById("cancelRoleRequest")?.addEventListener("click", { _: Event ->
        document.body?.removeChild(modal)
    })
    
    document.getElementById("roleRequestForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        
        val requestedRole = (document.getElementById("requestedRole") as HTMLSelectElement?)?.value ?: ""
        val selectedAdminId = (document.getElementById("adminSelect") as HTMLSelectElement?)?.value ?: ""
        val reason = (document.getElementById("requestReason") as HTMLTextAreaElement?)?.value ?: ""
        
        if (requestedRole.isBlank()) {
            showMessage("Please select a role", "error")
            return@addEventListener
        }
        
        if (selectedAdminId.isBlank()) {
            showMessage("Please select an admin", "error")
            return@addEventListener
        }
        
        if (reason.isBlank()) {
            showMessage("Please provide a reason", "error")
            return@addEventListener
        }
        
        val body = js("({method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({requestedRole:requestedRole, reason:reason, adminId:selectedAdminId})})")
        
        window.fetch("/api/role-requests/$currentUserId", body)
            .then { response ->
                if (response.ok) {
                    response.json().then { data ->
                        showMessage("Role request submitted successfully", "success")
                        document.body?.removeChild(modal)
                    }
                } else {
                    response.json().then { errorData ->
                        showMessage("Failed to submit request: ${errorData.asDynamic().message}", "error")
                    }
                }
            }
            .catch { error ->
                showMessage("Failed to submit request: $error", "error")
            }
    })
}

private fun loadAdminsForRoleRequest() {
    window.fetch("/api/users")
        .then { response ->
            if (response.ok) {
                response.json().then { users ->
                    val adminSelect = document.getElementById("adminSelect") as HTMLSelectElement
                    val usersArray = users as Array<dynamic>
                    val admins = usersArray.filter { user -> user.role == "ADMIN" }
                    
                    adminSelect.innerHTML = "<option value=''>Select an admin</option>"
                    
                    admins.forEach { admin ->
                        val option = document.createElement("option")
                        option.setAttribute("value", admin.id.toString())
                        option.textContent = "${admin.name} (${admin.email})"
                        adminSelect.appendChild(option)
                    }
                }
            } else {
                showMessage("Failed to load admins", "error")
            }
        }
        .catch { error ->
            showMessage("Failed to load admins: $error", "error")
        }
}

// Client Management Functions
private fun loadClients() {
    window.fetch("/api/api/clients")
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    displayClients(clients)
                }
            } else {
                document.getElementById("clientListTable")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load clients
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientListTable")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading clients: $error
                </div>
            """
        }
}

private fun displayClients(clients: dynamic) {
    val clientListTable = document.getElementById("clientListTable")
    if (clientListTable == null) return
    
    if (js("Array.isArray(clients)") as Boolean && (clients as Array<dynamic>).isEmpty()) {
        clientListTable.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No clients found
            </div>
        """
        return
    }
    
    val clientsArray = clients as Array<dynamic>
    val clientsHtml = clientsArray.map { client ->
        val balance = (client.currentBalance as Number).toDouble()
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        
        // Credit limit alert color logic (Green / Orange / Red)
        val creditLimit = (client.creditLimit as Number?)?.toDouble()
        val usedPct: Double? = if (creditLimit != null && creditLimit > 0.0) {
            (kotlin.math.abs(balance) / creditLimit) * 100.0
        } else null
        val (alertIcon, alertStyle) = when {
            usedPct == null -> Pair("", "")
            usedPct >= 100.0 -> Pair("⚠️", "border-left: 4px solid #e74c3c;") // Red - exceeded
            usedPct >= 80.0 -> Pair("⚠️", "border-left: 4px solid #ffc107;") // Orange - approaching
            else -> Pair("", "") // Green/normal - no alert border
        }
        
        """
        <div class="client-item" style="border: 1px solid #e9ecef; border-radius: 4px; padding: 15px; margin-bottom: 10px; transition: background-color 0.2s; $alertStyle" 
             onmouseover="this.style.backgroundColor='#f8f9fa'" onmouseout="this.style.backgroundColor='white'">
            <div style="display: flex; align-items: center; gap: 15px;">
                <!-- Edit Button -->
                <button onclick="event.stopPropagation(); window.editClientFromList(${client.id})" 
                        style="background: #007bff; border: none; border-radius: 50%; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; cursor: pointer; transition: all 0.2s; box-shadow: 0 2px 4px rgba(0,0,0,0.1);"
                        onmouseover="this.style.background='#0056b3'; this.style.transform='scale(1.05)'" 
                        onmouseout="this.style.background='#007bff'; this.style.transform='scale(1)'"
                        title="Edit Client">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="white">
                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                    </svg>
                </button>
                
                <!-- Client Info (clickable for details) -->
                <div style="flex: 1; cursor: pointer;" onclick="window.location.hash='#/client/${client.id}'">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <div>
                            <div style="font-weight: bold; color: #333; margin-bottom: 5px;">$alertIcon ${client.clientName}</div>
                            <div style="font-size: 12px; color: #666;">#${client.clientNumber}</div>
                        </div>
                        <div style="text-align: right;">
                            <div style="font-weight: bold; color: $balanceColor;">$balanceText</div>
                            <div style="font-size: 12px; color: #666;">${client.status}</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        """
    }.joinToString("")
    
    clientListTable.innerHTML = clientsHtml
}

private fun selectClient(clientId: Long) {
    window.location.hash = "#/client/$clientId"
}

// Function to edit client from the client list
fun editClientFromList(clientId: Long) {
    // Load client details and open edit modal
    window.fetch("/api/api/clients/$clientId")
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    showEditClientForm(client)
                }
            } else {
                showMessage("Failed to load client details", "error")
            }
        }
        .catch { error ->
            showMessage("Error loading client: $error", "error")
        }
}
private fun loadClientDetails(clientId: Long) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    val ts = js("Date.now()")
    window.fetch("/api/clients/$clientId?ts=$ts", opts)
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    displayClientDetails(client)
                    loadClientEvents(clientId)
                }
            } else {
                document.getElementById("clientDetailsContent")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load client details
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientDetailsContent")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading client details: $error
                </div>
            """
        }
}

private fun displayClientDetails(client: dynamic) {
    val clientDetailsContent = document.getElementById("clientDetailsContent")
    if (clientDetailsContent == null) return
    
    val balance = (client.currentBalance as Number).toDouble()
    val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
    val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
    
    clientDetailsContent.innerHTML = """
        <div style="margin-bottom: 20px;">
            <h4 style="margin: 0 0 10px 0; color: #333;">${client.clientName}</h4>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 15px;">
                <div><strong>Client #:</strong> ${client.clientNumber}</div>
                <div><strong>Status:</strong> ${client.status}</div>
                <div><strong>Phone:</strong> ${client.phone ?: "N/A"}</div>
                <div><strong>Currency:</strong> ${client.currency}</div>
            </div>
            <div style="background-color: #f8f9fa; padding: 15px; border-radius: 4px; margin-bottom: 15px;">
                <div style="text-align: center;">
                    <div id="currentBalanceValue" style="font-size: 24px; font-weight: bold; color: $balanceColor; margin-bottom: 5px;">$balanceText</div>
                    <div style="color: #666;">Current Balance</div>
                </div>
            </div>
            <div style="display: flex; gap: 10px; margin-bottom: 20px;">
                <button onclick="addClientTransaction(${client.id})" style="padding: 8px 16px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Add Transaction</button>
            </div>
        </div>
    """
}

private fun loadClientEvents(clientId: Long) {
    val opts = js("({})")
    opts.method = "GET"
    opts.cache = "no-store"
    val ts = js("Date.now()")
    window.fetch("/api/events/client/$clientId?ts=$ts", opts)
        .then { response ->
            if (response.ok) {
                response.json().then { events ->
                    displayClientEvents(events)
                }
            } else {
                document.getElementById("clientEventsTable")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load transactions
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientEventsTable")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading transactions: $error
                </div>
            """
        }
}

private fun displayClientEvents(events: dynamic) {
    val clientEventsTable = document.getElementById("clientEventsTable")
    if (clientEventsTable == null) return
    
    if (js("Array.isArray(events)") as Boolean && (events as Array<dynamic>).isEmpty()) {
        clientEventsTable.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No transactions found
            </div>
        """
        return
    }
    
    val eventsArray = events as Array<dynamic>

    fun fmtAmount(value: dynamic?): String {
        if (value == null) return ""
        val n = (value as Number).toInt()
        return "¥${n}"
    }

    val rowsHtml = eventsArray.map { event ->
        val qtyRaw = event.quantity
        val qty = if (qtyRaw != null) (qtyRaw as Number).toInt() else null
        val qtyText = if (qty != null) "${qty} UNITS" else ""
        val tPrice = event.transactionPrice
        val payment = event.paymentReceived
        val balance = (event.runningBalance as Number).toDouble()
        val tPriceColor = if (tPrice != null) {
            val v = (tPrice as Number).toDouble()
            if (v < 0) "#e74c3c" else if (v > 0) "#27ae60" else "#666"
        } else "#666"
        val paymentColor = if (payment != null) {
            val v = (payment as Number).toDouble()
            if (v < 0) "#e74c3c" else if (v > 0) "#27ae60" else "#666"
        } else "#666"
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        """
        <tr style="border-bottom: 1px solid #f1f3f4; transition: background-color 0.2s ease;" onmouseover="this.style.backgroundColor='#f8f9fa'" onmouseout="this.style.backgroundColor='transparent'">
            <td style="padding: 16px; color: #5f6368; font-size: 13px; font-weight: 500; border-right: 1px solid #f1f3f4;">${event.eventDate}</td>
            <td style="padding: 16px; font-weight: 600; color: #2c3e50; font-size: 14px; border-right: 1px solid #f1f3f4;">${event.eventDescription ?: ""}</td>
            <td style="padding: 16px; color: #5f6368; font-size: 13px; font-weight: 500; border-right: 1px solid #f1f3f4;">$qtyText</td>
            <td style="padding: 16px; color: #5f6368; font-size: 13px; font-weight: 500; border-right: 1px solid #f1f3f4;">${event.billNumber ?: ""}</td>
            <td style="padding: 16px; color: $tPriceColor; font-weight: 600; font-size: 14px; text-align: right; border-right: 1px solid #f1f3f4;">${if (tPrice != null) fmtAmount(tPrice) else ""}</td>
            <td style="padding: 16px; color: $paymentColor; font-weight: 600; font-size: 14px; text-align: right; border-right: 1px solid #f1f3f4;">${if (payment != null) fmtAmount(payment) else ""}</td>
            <td style="padding: 16px; font-weight: 700; color: $balanceColor; font-size: 15px; text-align: right;">${fmtAmount(event.runningBalance)}</td>
        </tr>
        """
    }.joinToString("")

    clientEventsTable.innerHTML = """
        <div style="background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.08); margin-top: 24px; border: 1px solid #e8eaed;">
            <div style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); padding: 24px; border-bottom: 1px solid #e8eaed;">
                <h2 style="margin: 0; color: white; font-size: 20px; font-weight: 600; letter-spacing: 0.5px;">Transaction History</h2>
                <p style="margin: 8px 0 0 0; color: rgba(255,255,255,0.9); font-size: 14px; font-weight: 400;">Complete financial transaction records</p>
            </div>
            <div style="overflow-x: auto; background: #fafbfc;">
                <table style="width: 100%; border-collapse: collapse; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
                    <thead>
                        <tr style="background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%); border-bottom: 2px solid #dee2e6;">
                            <th style="padding: 18px 16px; text-align: left; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px; border-right: 1px solid #e8eaed;">DATE</th>
                            <th style="padding: 18px 16px; text-align: left; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px; border-right: 1px solid #e8eaed;">Event</th>
                            <th style="padding: 18px 16px; text-align: left; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px; border-right: 1px solid #e8eaed;">QUANTITY</th>
                            <th style="padding: 18px 16px; text-align: left; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px; border-right: 1px solid #e8eaed;">BILL. NO</th>
                            <th style="padding: 18px 16px; text-align: right; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px; border-right: 1px solid #e8eaed;">TOTAL SHIPMENT PRICE</th>
                            <th style="padding: 18px 16px; text-align: right; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px; border-right: 1px solid #e8eaed;">PAYMENT RECEIVED</th>
                            <th style="padding: 18px 16px; text-align: right; font-weight: 700; color: #2c3e50; font-size: 13px; text-transform: uppercase; letter-spacing: 0.8px;">TOTAL BALANCE</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rowsHtml
                    </tbody>
                </table>
            </div>
        </div>
    """
}

private fun filterClients() {
    val searchInput = document.getElementById("clientSearchInput") as HTMLInputElement
    val searchTerm = searchInput.value.toLowerCase()
    
    val clientItems = document.querySelectorAll(".client-item")
    for (i in 0 until clientItems.length) {
        val item = clientItems[i] as HTMLElement
        val clientName = (item.querySelector("div[style*='font-weight: bold']")?.textContent ?: "").toLowerCase()
        val clientNumber = (item.querySelector("div[style*='font-size: 12px']")?.textContent ?: "").toLowerCase()
        
        val isVisible = clientName.contains(searchTerm) || clientNumber.contains(searchTerm)
        item.style.display = if (isVisible) "block" else "none"
    }
}

private fun toggleClientAlerts() {
    val section = document.getElementById("clientAlertsSection") as HTMLElement
    val isVisible = section.style.display != "none"
    section.style.display = if (isVisible) "none" else "block"
    
    if (!isVisible) {
        loadClientAlerts()
    }
}

private fun loadClientAlerts() {
    window.fetch("/api/api/clients/alerts")
        .then { response ->
            if (response.ok) {
                response.json().then { alerts ->
                    displayClientAlerts(alerts)
                }
            } else {
                document.getElementById("clientAlertsTable")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load alerts
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("clientAlertsTable")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading alerts: $error
                </div>
            """
        }
}

private fun displayClientAlerts(alerts: dynamic) {
    val clientAlertsTable = document.getElementById("clientAlertsTable")
    if (clientAlertsTable == null) return
    
    if (js("Array.isArray(alerts)") as Boolean && (alerts as Array<dynamic>).isEmpty()) {
        clientAlertsTable.innerHTML = """
            <div style="text-align: center; color: #666; padding: 20px;">
                No alerts found
            </div>
        """
        return
    }
    
    val alertsArray = alerts as Array<dynamic>
    val alertsHtml = alertsArray.map { alert ->
        val balance = (alert.currentBalance as Number).toDouble()
        val creditLimit = (alert.creditLimit as Number?)?.toDouble()
        val usedPct: Double? = if (creditLimit != null && creditLimit > 0.0) {
            (kotlin.math.abs(balance) / creditLimit) * 100.0
        } else null

        val (alertType, alertColor) = when {
            balance < 0 -> Pair("Debt Alert", "#e74c3c")
            usedPct != null && usedPct >= 100.0 -> Pair("Credit Limit Alert", "#e74c3c") // exceeded
            usedPct != null && usedPct >= 80.0 -> Pair("Credit Limit Alert", "#ffc107") // approaching
            else -> Pair("Credit Limit Alert", "#28a745") // healthy
        }
        val pctText = if (usedPct != null) "${usedPct.toInt()}% used" else ""
        
        """
        <div style="border: 1px solid $alertColor; border-radius: 4px; padding: 12px; margin-bottom: 8px; background-color: #fff5f5;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
                <div>
                    <div style="font-weight: bold; color: $alertColor;">$alertType</div>
                    <div style="font-size: 12px; color: #666;">${alert.clientName} (#${alert.clientNumber})</div>
                </div>
                <div style="text-align: right;">
                    <div style="font-weight: bold; color: $alertColor;">¥${balance.toInt()}</div>
                    <div style="font-size: 12px; color: #666;">$pctText</div>
                </div>
            </div>
        </div>
        """
    }.joinToString("")
    
    clientAlertsTable.innerHTML = alertsHtml
}

private fun showAddClientForm() {
    val modalHTML = """
        <div id="addClientModal" class="modal" style="display: block; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">Add New Client</h2>
                    <span class="close" onclick="closeAddClientModal()" style="color: #aaa; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="addClientForm" style="display: flex; flex-direction: column; gap: 15px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="clientNumber" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Number *</label>
                            <input type="text" id="clientNumber" name="clientNumber" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., 128">
                        </div>
                        <div>
                            <label for="clientName" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Name *</label>
                            <input type="text" id="clientName" name="clientName" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., ABC COMPANY">
                        </div>
                    </div>
                    
                    <div>
                        <label for="address" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Address *</label>
                        <input type="text" id="address" name="address" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               placeholder="e.g., Tokyo, Japan">
                    </div>
                    
                    <div>
                        <label for="phone" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Phone *</label>
                        <input type="text" id="phone" name="phone" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               placeholder="e.g., +81-3-1234-5678">
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="currentBalance" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Initial Balance</label>
                            <input type="number" id="currentBalance" name="currentBalance" step="0.01" value="0.00"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="0.00">
                        </div>
                        <div>
                            <label for="creditLimit" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Credit Limit</label>
                            <input type="number" id="creditLimit" name="creditLimit" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., 50000000">
                        </div>
                        <div>
                            <label for="alertThreshold" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Alert Threshold</label>
                            <input type="number" id="alertThreshold" name="alertThreshold" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   placeholder="e.g., 10000000">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="currency" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Currency</label>
                            <select id="currency" name="currency" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="JPY" selected>JPY (Japanese Yen)</option>
                                <option value="USD">USD (US Dollar)</option>
                                <option value="EUR">EUR (Euro)</option>
                            </select>
                        </div>
                        <div>
                            <label for="status" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Status</label>
                            <select id="status" name="status" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="ACTIVE" selected>Active</option>
                                <option value="SUSPENDED">Suspended</option>
                                <option value="CLOSED">Closed</option>
                            </select>
                        </div>
                    </div>
                    
                    <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;">
                        <button type="button" onclick="closeAddClientModal()" 
                                style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Cancel
                        </button>
                        <button type="submit" 
                                style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Add Client
                        </button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Remove existing modal if any
    document.getElementById("addClientModal")?.remove()
    
    // Add modal to body
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Add form submission handler
    document.getElementById("addClientForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        handleAddClientSubmit()
    })
}

private fun handleAddClientSubmit() {
    val form = document.getElementById("addClientForm") as HTMLFormElement
    
    val clientData = js("{}")
    clientData["clientNumber"] = (document.getElementById("clientNumber") as HTMLInputElement).value
    clientData["clientName"] = (document.getElementById("clientName") as HTMLInputElement).value
    clientData["address"] = (document.getElementById("address") as HTMLInputElement).value
    clientData["phone"] = (document.getElementById("phone") as HTMLInputElement).value
    clientData["currentBalance"] = (document.getElementById("currentBalance") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    clientData["creditLimit"] = (document.getElementById("creditLimit") as HTMLInputElement).value.toDoubleOrNull()
    clientData["alertThreshold"] = (document.getElementById("alertThreshold") as HTMLInputElement).value.toDoubleOrNull()
    clientData["currency"] = (document.getElementById("currency") as HTMLSelectElement).value
    clientData["status"] = (document.getElementById("status") as HTMLSelectElement).value
    
    // Validate required fields
    if (clientData["clientNumber"] == "" || clientData["clientName"] == "" || clientData["address"] == "" || clientData["phone"] == "") {
        showMessage("Please fill in all required fields", "error")
        return
    }
    
    // Show loading state
    val submitBtn = document.querySelector("#addClientForm button[type='submit']") as HTMLButtonElement
    submitBtn.disabled = true
    submitBtn.textContent = "Adding..."
    
    // Submit to backend
    val requestOptions = js("{}")
    requestOptions["method"] = "POST"
    requestOptions["headers"] = js("{\"Content-Type\": \"application/json\"}")
    requestOptions["body"] = JSON.stringify(clientData)
    
    window.fetch("/api/api/clients", requestOptions)
    .then { response ->
        if (response.ok) {
            showMessage("Client added successfully!", "success")
            closeAddClientModal()
            showClientAccountsPage() // Refresh the client list
        } else {
            response.text().then { errorText ->
                showMessage("Failed to add client: $errorText", "error")
            }
        }
    }
    .catch { error ->
        showMessage("Error adding client: ${error}", "error")
    }
    .finally {
        submitBtn.disabled = false
        submitBtn.textContent = "Add Client"
    }
}

private fun closeAddClientModal() {
    document.getElementById("addClientModal")?.remove()
}

// Expose functions to global scope for HTML onclick attributes
@JsName("closeAddClientModal")
fun closeAddClientModalGlobal() {
    closeAddClientModal()
}

@JsName("selectClient")
fun selectClientGlobal(clientId: Long) {
    selectClient(clientId)
}

@JsName("editClient")
fun editClientGlobal(clientId: Long) {
    editClient(clientId)
}

@JsName("addClientTransaction")
fun addClientTransactionGlobal(clientId: Long) {
    addClientTransaction(clientId)
}

@JsName("closeEditClientModal")
fun closeEditClientModalGlobal() {
    closeEditClientModal()
}




// Performance Dashboard
/* Performance feature removed
private fun showPerformanceDashboard() {
    val dashboardHtml = """
        <div id="performanceDashboardModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; justify-content: center; align-items: center;">
            <div style="background: white; border-radius: 8px; padding: 30px; max-width: 900px; width: 95%; max-height: 90%; overflow-y: auto;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">🚀 Performance Dashboard</h2>
                    <button onclick="closePerformanceDashboard()" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #aaa;">&times;</button>
                </div>
                
                <div id="performanceContent" style="text-align: center; padding: 40px;">
                    <div style="width: 50px; height: 50px; border: 4px solid #f3f3f3; border-top: 4px solid #fd7e14; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto;"></div>
                    <p style="margin-top: 20px; color: #666;">Loading performance metrics...</p>
                </div>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", dashboardHtml)
    
    // Load performance data
    loadPerformanceData()
}

private fun closePerformanceDashboard() {}
private fun loadPerformanceData() {
    window.fetch("/api/events/performance/summary")
        .then { response ->
            if (response.ok) {
                response.json().then { data ->
                    displayPerformanceData(data)
                }
            } else {
                document.getElementById("performanceContent")?.innerHTML = """
                    <div style="text-align: center; color: #e74c3c; padding: 20px;">
                        Failed to load performance data
                    </div>
                """
            }
        }
        .catch { error ->
            document.getElementById("performanceContent")?.innerHTML = """
                <div style="text-align: center; color: #e74c3c; padding: 20px;">
                    Error loading performance data: $error
                </div>
            """
        }
}

private fun displayPerformanceData(data: dynamic) {
    val performanceScore = js("data.performanceScore") as String
    val averageSuccessRate = js("data.averageSuccessRate") as Int
    val systemMetrics = js("data.systemMetrics")
    val recommendations = js("data.recommendations") as Array<String>
    
    val scoreColor = when (performanceScore) {
        "EXCELLENT" -> "#28a745"
        "GOOD" -> "#17a2b8"
        "FAIR" -> "#ffc107"
        else -> "#dc3545"
    }
    
    val content = """
        <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 30px;">
            <!-- Performance Score -->
            <div style="background: linear-gradient(135deg, $scoreColor, ${scoreColor}88); color: white; padding: 20px; border-radius: 8px; text-align: center;">
                <h3 style="margin: 0 0 10px 0;">Performance Score</h3>
                <div style="font-size: 36px; font-weight: bold; margin: 10px 0;">$performanceScore</div>
                <div style="font-size: 14px; opacity: 0.9;">Success Rate: $averageSuccessRate%</div>
            </div>
            
            <!-- System Metrics -->
            <div style="background: #f8f9fa; padding: 20px; border-radius: 8px;">
                <h4 style="margin: 0 0 15px 0; color: #333;">System Metrics</h4>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; font-size: 14px;">
                    <div><strong>Total Imports:</strong> ${js("systemMetrics.totalImports")}</div>
                    <div><strong>Total Transactions:</strong> ${js("systemMetrics.totalTransactions")}</div>
                    <div><strong>Total Clients:</strong> ${js("systemMetrics.totalClients")}</div>
                    <div><strong>Avg Import Time:</strong> ${js("systemMetrics.averageImportTime")}ms</div>
                    <div><strong>System Uptime:</strong> ${js("systemMetrics.systemUptimeFormatted")}</div>
                </div>
            </div>
        </div>
        
        <!-- Recommendations -->
        ${if (recommendations.isNotEmpty()) """
        <div style="background: #fff3cd; border: 1px solid #ffeaa7; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
            <h4 style="margin: 0 0 15px 0; color: #856404;">💡 Recommendations</h4>
            <ul style="margin: 0; padding-left: 20px; color: #856404;">
                ${recommendations.map { "<li>$it</li>" }.joinToString("")}
            </ul>
        </div>
        """ else ""}
        
        <!-- Action Buttons -->
        <div style="display: flex; gap: 10px; justify-content: center;">
            <button onclick="refreshPerformanceData()" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Refresh</button>
            <button onclick="resetPerformanceMetrics()" style="padding: 10px 20px; background-color: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset Metrics</button>
            <button onclick="closePerformanceDashboard()" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Close</button>
        </div>
    """
    
    document.getElementById("performanceContent")?.innerHTML = content
}
*/

// Performance helpers removed

private fun editClient(clientId: Long) {
    // Load client data and show edit form
    window.fetch("/api/api/clients/$clientId")
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    showEditClientForm(client)
                }
            } else {
                showMessage("Failed to load client data", "error")
            }
        }
        .catch { error ->
            showMessage("Error loading client: $error", "error")
        }
}

private fun showEditClientForm(client: dynamic) {
    val modalHTML = """
        <div id="editClientModal" class="modal" style="display: block; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">Edit Client</h2>
                    <span class="close" onclick="closeEditClientModal()" style="color: #aaa; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="editClientForm" style="display: flex; flex-direction: column; gap: 15px;">
                    <div>
                        <label for="editClientNumber" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Number *</label>
                        <input type="text" id="editClientNumber" name="clientNumber" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               value="${client.clientNumber}" placeholder="e.g., 128">
                    </div>
                    
                    <div>
                        <label for="editClientName" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Client Name *</label>
                        <input type="text" id="editClientName" name="clientName" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               value="${client.clientName}" placeholder="e.g., ABC COMPANY">
                    </div>
                    
                    <div>
                        <label for="editAddress" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Address *</label>
                        <input type="text" id="editAddress" name="address" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               value="${client.address}" placeholder="e.g., Tokyo, Japan">
                    </div>
                    
                    <div>
                        <label for="editPhone" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Phone *</label>
                        <input type="text" id="editPhone" name="phone" required 
                               style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                               value="${client.phone}" placeholder="e.g., +81-3-1234-5678">
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="editCurrentBalance" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Current Balance</label>
                            <input type="number" id="editCurrentBalance" name="currentBalance" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${client.currentBalance}" placeholder="0.00">
                        </div>
                        <div>
                            <label for="editCreditLimit" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Credit Limit</label>
                            <input type="number" id="editCreditLimit" name="creditLimit" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${client.creditLimit}" placeholder="e.g., 50000000">
                        </div>
                        <div>
                            <label for="editAlertThreshold" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Alert Threshold</label>
                            <input type="number" id="editAlertThreshold" name="alertThreshold" step="0.01"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;"
                                   value="${client.alertThreshold}" placeholder="e.g., 10000000">
                        </div>
                    </div>
                    
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="editCurrency" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Currency</label>
                            <select id="editCurrency" name="currency" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="JPY" ${if (client.currency == "JPY") "selected" else ""}>JPY (Japanese Yen)</option>
                                <option value="USD" ${if (client.currency == "USD") "selected" else ""}>USD (US Dollar)</option>
                                <option value="EUR" ${if (client.currency == "EUR") "selected" else ""}>EUR (Euro)</option>
                            </select>
                        </div>
                        <div>
                            <label for="editStatus" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Status</label>
                            <select id="editStatus" name="status" 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="ACTIVE" ${if (client.status == "ACTIVE") "selected" else ""}>Active</option>
                                <option value="SUSPENDED" ${if (client.status == "SUSPENDED") "selected" else ""}>Suspended</option>
                                <option value="CLOSED" ${if (client.status == "CLOSED") "selected" else ""}>Closed</option>
                            </select>
                        </div>
                    </div>
                    
                    <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;">
                        <button type="button" onclick="closeEditClientModal()" 
                                style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Cancel
                        </button>
                        <button type="submit" 
                                style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">
                            Update Client
                        </button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    // Remove existing modal if any
    document.getElementById("editClientModal")?.remove()
    
    // Add modal to body
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Add form submission handler
    document.getElementById("editClientForm")?.addEventListener("submit", { event ->
        event.preventDefault()
        handleEditClientSubmit(client.id)
    })
}

private fun handleEditClientSubmit(clientId: Long) {
    val form = document.getElementById("editClientForm") as HTMLFormElement
    
    val clientData = js("{}")
    clientData["clientNumber"] = (document.getElementById("editClientNumber") as HTMLInputElement).value
    clientData["clientName"] = (document.getElementById("editClientName") as HTMLInputElement).value
    clientData["address"] = (document.getElementById("editAddress") as HTMLInputElement).value
    clientData["phone"] = (document.getElementById("editPhone") as HTMLInputElement).value
    clientData["currentBalance"] = (document.getElementById("editCurrentBalance") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    clientData["creditLimit"] = (document.getElementById("editCreditLimit") as HTMLInputElement).value.toDoubleOrNull()
    clientData["alertThreshold"] = (document.getElementById("editAlertThreshold") as HTMLInputElement).value.toDoubleOrNull()
    clientData["currency"] = (document.getElementById("editCurrency") as HTMLSelectElement).value
    clientData["status"] = (document.getElementById("editStatus") as HTMLSelectElement).value
    
    // Validate required fields
    if (clientData["clientNumber"] == "" || clientData["clientName"] == "" || clientData["address"] == "" || clientData["phone"] == "") {
        showMessage("Please fill in all required fields", "error")
        return
    }
    
    // Show loading state
    val submitBtn = document.querySelector("#editClientForm button[type='submit']") as HTMLButtonElement
    submitBtn.disabled = true
    submitBtn.textContent = "Updating..."
    
    // Submit to backend
    val requestOptions = js("{}")
    requestOptions["method"] = "PUT"
    requestOptions["headers"] = js("{\"Content-Type\": \"application/json\"}")
    requestOptions["body"] = JSON.stringify(clientData)
    
    console.log("Submitting client update", js("JSON.stringify(clientData)") )
    window.fetch("/api/api/clients/$clientId", requestOptions)
    .then { response ->
        if (response.ok) {
            response.clone().json().then { updated ->
                console.log("Update response", updated)
            }
            // Enforce balance persistence via dedicated endpoint
            val balance = (document.getElementById("editCurrentBalance") as HTMLInputElement).value.toDoubleOrNull()
            if (balance != null) {
                val balanceReq = js("{}")
                val balanceBody = js("{}")
                balanceBody["balance"] = balance
                balanceReq["method"] = "PUT"
                balanceReq["headers"] = js("{\"Content-Type\": \"application/json\"}")
                balanceReq["body"] = JSON.stringify(balanceBody)
                window.fetch("/api/api/clients/$clientId/balance", balanceReq)
                .then { balResp ->
                    if (!balResp.ok) {
                        balResp.text().then { err -> console.error("Balance update failed:", err) }
                    }
                    showMessage("Client updated successfully!", "success")
                    closeEditClientModal()
                    showClientAccountsPage()
                }
                .catch { err ->
                    console.error("Balance update error", err)
                    showMessage("Client updated, but balance sync failed.", "warning")
                    closeEditClientModal()
                    showClientAccountsPage()
                }
            } else {
                showMessage("Client updated successfully!", "success")
                closeEditClientModal()
                showClientAccountsPage()
            }
        } else {
            response.text().then { errorText ->
                showMessage("Failed to update client: $errorText", "error")
            }
        }
    }
    .catch { error ->
        showMessage("Error updating client: ${error}", "error")
    }
    .finally {
        submitBtn.disabled = false
        submitBtn.textContent = "Update Client"
    }
}

private fun closeEditClientModal() {
    document.getElementById("editClientModal")?.remove()
}

private fun addClientTransaction(clientId: Long) {
    openAddTransactionModal(clientId)
}

// Client Selection Functions for Purchase Forms
private fun loadClientsForPurchase() {
    window.fetch("/api/api/clients")
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    populateClientDropdown(clients)
                }
            } else {
                console.error("Failed to load clients for purchase form")
            }
        }
        .catch { error ->
            console.error("Error loading clients for purchase form:", error)
        }
}

private fun populateClientDropdown(clients: dynamic) {
    val clientSelect = document.getElementById("clientId") as HTMLSelectElement
    val editClientSelect = document.getElementById("editClientId") as HTMLSelectElement
    
    if (clientSelect != null) {
        clientSelect.innerHTML = "<option value=\"\">Select Client</option>"
        if (js("Array.isArray(clients)") as Boolean) {
            val clientsArray = clients as Array<dynamic>
            clientsArray.forEach { client ->
                val option = document.createElement("option")
                (option as HTMLOptionElement).value = client.id.toString()
                option.textContent = "${client.clientName} (#${client.clientNumber})"
                clientSelect.appendChild(option)
            }
        }
    }
    
    if (editClientSelect != null) {
        editClientSelect.innerHTML = "<option value=\"\">Select Client</option>"
        if (js("Array.isArray(clients)") as Boolean) {
            val clientsArray = clients as Array<dynamic>
            clientsArray.forEach { client ->
                val option = document.createElement("option")
                (option as HTMLOptionElement).value = client.id.toString()
                option.textContent = "${client.clientName} (#${client.clientNumber})"
                editClientSelect.appendChild(option)
            }
        }
    }
}

private fun handleClientSelection(clientId: String) {
    if (clientId.isBlank()) {
        (document.getElementById("clientBalanceInfo") as HTMLElement?)?.style?.display = "none"
        return
    }
    
    window.fetch("/api/api/clients/$clientId")
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    displayClientBalance(client, "clientBalance", "creditLimitWarning", "clientBalanceInfo")
                }
            }
        }
        .catch { error ->
            console.error("Error loading client details:", error)
        }
}

private fun handleEditClientSelection(clientId: String) {
    if (clientId.isBlank()) {
        (document.getElementById("editClientBalanceInfo") as HTMLElement?)?.style?.display = "none"
        return
    }
    
    window.fetch("/api/api/clients/$clientId")
        .then { response ->
            if (response.ok) {
                response.json().then { client ->
                    displayClientBalance(client, "editClientBalance", "editCreditLimitWarning", "editClientBalanceInfo")
                }
            }
        }
        .catch { error ->
            console.error("Error loading client details:", error)
        }
}

private fun displayClientBalance(client: dynamic, balanceElementId: String, warningElementId: String, infoElementId: String) {
    val balance = (client.currentBalance as Number).toDouble()
    val creditLimit = (client.creditLimit as Number?)?.toDouble()
    val alertThreshold = (client.alertThreshold as Number?)?.toDouble()
    
    val balanceElement = document.getElementById(balanceElementId)
    val warningElement = document.getElementById(warningElementId)
    val infoElement = document.getElementById(infoElementId)
    
    if (balanceElement != null) {
        val balanceColor = if (balance < 0) "#e74c3c" else if (balance > 0) "#27ae60" else "#666"
        val balanceText = if (balance < 0) "¥${kotlin.math.abs(balance).toInt()}" else if (balance > 0) "+¥${balance.toInt()}" else "¥0"
        balanceElement.innerHTML = "<span style='color: $balanceColor;'>$balanceText</span>"
    }
    
    if (warningElement != null && infoElement != null) {
        // Compute usage percent to drive color/state
        val usedPct: Double? = if (creditLimit != null && creditLimit > 0.0) {
            (kotlin.math.abs(balance) / creditLimit) * 100.0
        } else null
        val (warningDisplay, borderColor) = when {
            usedPct == null -> Pair("none", "")
            usedPct >= 100.0 -> Pair("block", "#e74c3c") // Red
            usedPct >= 80.0 -> Pair("block", "#ffc107") // Orange
            else -> Pair("none", "") // Green/OK
        }
        (warningElement as HTMLElement?)?.style?.display = warningDisplay
        (infoElement as HTMLElement?)?.style?.display = "block"
        // Optional: color hint on the warning element
        (warningElement as HTMLElement?)?.style?.color = if (borderColor.isNotEmpty()) borderColor else "#e74c3c"
    }
}

// Advanced Features Functions

private fun showImportClientsModal() {
    val modalHtml = """
        <div id="importClientsModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; justify-content: center; align-items: center;">
            <div style="background: white; border-radius: 8px; padding: 30px; max-width: 500px; width: 90%; max-height: 80%; overflow-y: auto;">
                <h3 style="margin: 0 0 20px 0; color: #333;">Import Client Data</h3>
                <p style="color: #666; margin-bottom: 20px;">Upload a CSV file with client data. Expected columns: clientNumber, clientName, address, phone, currentBalance, creditLimit, alertThreshold, currency, status</p>
                
                <form id="importClientsForm">
                    <div style="margin-bottom: 20px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: bold;">Select CSV File</label>
                        <input type="file" id="clientsFileInput" accept=".csv" required style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px;">
                    </div>
                    
                    <div style="margin-bottom: 20px;">
                        <label style="display: flex; align-items: center; margin-bottom: 8px;">
                            <input type="checkbox" id="updateExistingClients" style="margin-right: 8px;">
                            Update existing clients if client number matches
                        </label>
                    </div>
                    
                    <div style="display: flex; gap: 10px; justify-content: flex-end;">
                        <button type="button" id="cancelImportClients" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Cancel</button>
                        <button type="submit" id="importClientsSubmit" style="padding: 10px 20px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer;">Import</button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    
    // Add event listeners
    document.getElementById("cancelImportClients")?.addEventListener("click", { _: Event ->
        document.getElementById("importClientsModal")?.remove()
    })
    
    document.getElementById("importClientsForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleImportClients()
    })
}

private fun handleImportClients() {
    val fileInput = document.getElementById("clientsFileInput") as HTMLInputElement
    val updateExisting = (document.getElementById("updateExistingClients") as HTMLInputElement).checked
    val submitBtn = document.getElementById("importClientsSubmit") as HTMLButtonElement
    
    if (fileInput.files?.length == 0) {
        showMessage("Please select a CSV file", "error")
        return
    }
    
    submitBtn.disabled = true
    submitBtn.textContent = "Importing..."
    
    val file = js("fileInput.files[0]")
    val reader = js("new FileReader()")
    
    reader.onload = { event: dynamic ->
        val csvContent = event.target.result as String
        processClientsCSV(csvContent, updateExisting)
    }
    
    reader.readAsText(file)
}

private fun processClientsCSV(csvContent: String, updateExisting: Boolean) {
    val lines = csvContent.split("\n").filter { it.isNotBlank() }
    if (lines.isEmpty()) {
        showMessage("CSV file is empty", "error")
        return
    }
    
    val headers = lines[0].split(",").map { it.trim() }
    val clients = mutableListOf<dynamic>()
    
    for (i in 1 until lines.size) {
        val values = lines[i].split(",").map { it.trim() }
        if (values.size != headers.size) continue
        
        val client = js("{}")
        for (j in headers.indices) {
            val header = headers[j].lowercase().replace(" ", "")
            val value = values[j]
            
            when (header) {
                "clientnumber" -> client.clientNumber = value
                "clientname" -> client.clientName = value
                "address" -> client.address = value
                "phone" -> client.phone = value
                "currentbalance" -> client.currentBalance = value.toDoubleOrNull() ?: 0.0
                "creditlimit" -> client.creditLimit = value.toDoubleOrNull()
                "alertthreshold" -> client.alertThreshold = value.toDoubleOrNull()
                "currency" -> client.currency = value.ifBlank { "JPY" }
                "status" -> client.status = value.ifBlank { "ACTIVE" }
            }
        }
        clients.add(client)
    }
    
    if (clients.isEmpty()) {
        showMessage("No valid client data found in CSV", "error")
        return
    }
    
    // Import clients
    importClientsData(clients, updateExisting)
}

private fun importClientsData(clients: List<dynamic>, updateExisting: Boolean) {
    val importData = js("{}")
    importData.clients = clients
    importData.updateExisting = updateExisting
    
    val requestOptions = js("""({
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(importData)
    })""")
    
    window.fetch("/api/api/clients/import", requestOptions)
        .then { response ->
            if (response.ok) {
                response.json().then { result ->
                    showMessage("Successfully imported ${js("result.imported")} clients", "success")
                    document.getElementById("importClientsModal")?.remove()
                    loadClients()
                }
            } else {
                response.text().then { errorText ->
                    showMessage("Failed to import clients: $errorText", "error")
                }
            }
        }
        .catch { error ->
            showMessage("Failed to import clients: $error", "error")
        }
        .finally {
            val submitBtn = document.getElementById("importClientsSubmit") as HTMLButtonElement?
            submitBtn?.disabled = false
            submitBtn?.textContent = "Import"
        }
}

private fun exportClientsData() {
    window.fetch("/api/api/clients")
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    exportClientsToCSV(clients)
                }
            } else {
                showMessage("Failed to load clients for export", "error")
            }
        }
        .catch { error ->
            showMessage("Failed to export clients: $error", "error")
        }
}

private fun exportClientsToCSV(clients: dynamic) {
    if (!js("Array.isArray(clients)") as Boolean || (clients as Array<dynamic>).isEmpty()) {
        showMessage("No clients to export", "error")
        return
    }
    
    val clientsArray = clients as Array<dynamic>
    val csvHeaders = "Client Number,Client Name,Address,Phone,Current Balance,Credit Limit,Alert Threshold,Currency,Status,Created At"
    val csvRows = clientsArray.map { client ->
        "${client.clientNumber},${client.clientName},${client.address ?: ""},${client.phone ?: ""},${client.currentBalance},${client.creditLimit ?: ""},${client.alertThreshold ?: ""},${client.currency},${client.status},${client.createdAt}"
    }
    
    val csvContent = csvHeaders + "\n" + csvRows.joinToString("\n")
    
    // Create and download file
    val blob = js("new Blob([csvContent], { type: 'text/csv' })")
    val url = js("window.URL.createObjectURL(blob)")
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = "clients_export_${js("new Date().toISOString().split('T')[0]")}.csv"
    link.click()
    js("window.URL.revokeObjectURL(url)")
    
    showMessage("Client data exported successfully", "success")
}

// Export a single client's Transaction History as CSV using backend endpoint
private fun exportClientTransactions(clientId: Long) {
    val url = "/api/events/export/$clientId"
    window.fetch(url)
        .then { response ->
            if (response.ok) {
                response.text().then { csv ->
                    val blob = js("new Blob([csv], { type: 'text/csv;charset=utf-8;' })")
                    val urlObj = js("window.URL.createObjectURL(blob)")
                    val link = document.createElement("a") as HTMLAnchorElement
                    link.href = urlObj
                    link.download = "client_${'$'}clientId_transactions.csv"
                    link.click()
                    js("window.URL.revokeObjectURL(urlObj)")
                    showMessage("Transactions exported successfully", "success")
                }
            } else {
                response.text().then { showMessage("Failed to export transactions: ${'$'}it", "error") }
            }
        }
        .catch { error ->
            showMessage("Failed to export transactions: ${'$'}error", "error")
        }
}
// Reporting feature removed
/* private fun showReportsModal() {
    val modalHtml = """
        <div id="reportsModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; justify-content: center; align-items: center;">
            <div style="background: white; border-radius: 8px; padding: 30px; max-width: 600px; width: 90%; max-height: 80%; overflow-y: auto;">
                <h3 style="margin: 0 0 20px 0; color: #333;">Client Reports</h3>
                
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px;">
                    <button id="balanceSummaryBtn" style="padding: 15px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; text-align: left;">
                        <div style="font-weight: bold; margin-bottom: 5px;">📊 Balance Summary</div>
                        <div style="font-size: 12px; opacity: 0.8;">Total outstanding balances and credit limits</div>
                    </button>
                    
                    <button id="transactionReportBtn" style="padding: 15px; background-color: #28a745; color: white; border: none; border-radius: 4px; cursor: pointer; text-align: left;">
                        <div style="font-weight: bold; margin-bottom: 5px;">📈 Transaction Report</div>
                        <div style="font-size: 12px; opacity: 0.8;">Client transaction history and trends</div>
                    </button>
                    
                    <button id="creditLimitReportBtn" style="padding: 15px; background-color: #ffc107; color: black; border: none; border-radius: 4px; cursor: pointer; text-align: left;">
                        <div style="font-weight: bold; margin-bottom: 5px;">⚠️ Credit Limit Report</div>
                        <div style="font-size: 12px; opacity: 0.8;">Clients approaching credit limits</div>
                    </button>
                    
                    <button id="auditTrailBtn" style="padding: 15px; background-color: #6f42c1; color: white; border: none; border-radius: 4px; cursor: pointer; text-align: left;">
                        <div style="font-weight: bold; margin-bottom: 5px;">🔍 Audit Trail</div>
                        <div style="font-size: 12px; opacity: 0.8;">Client account changes and history</div>
                    </button>
                </div>
                
                <div style="display: flex; gap: 10px; justify-content: flex-end;">
                    <button type="button" id="cancelReports" style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Close</button>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    
    // Add event listeners
    document.getElementById("cancelReports")?.addEventListener("click", { _: Event ->
        document.getElementById("reportsModal")?.remove()
    })
    
    document.getElementById("balanceSummaryBtn")?.addEventListener("click", { _: Event ->
        generateBalanceSummary()
    })
    
    document.getElementById("transactionReportBtn")?.addEventListener("click", { _: Event ->
        generateTransactionReport()
    })
    
    document.getElementById("creditLimitReportBtn")?.addEventListener("click", { _: Event ->
        generateCreditLimitReport()
    })
    
    document.getElementById("auditTrailBtn")?.addEventListener("click", { _: Event ->
        generateAuditTrail()
    })
}

private fun generateBalanceSummary() {
    window.fetch("/api/clients")
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    displayBalanceSummary(clients)
                }
            } else {
                showMessage("Failed to load clients for balance summary", "error")
            }
        }
        .catch { error ->
            showMessage("Failed to generate balance summary: $error", "error")
        }
}

private fun displayBalanceSummary(clients: dynamic) {
    if (!js("Array.isArray(clients)") as Boolean) {
        showMessage("No client data available", "error")
        return
    }
    
    val clientsArray = clients as Array<dynamic>
    val totalOutstanding = clientsArray.sumOf { (it.currentBalance as Number).toDouble() }
    val totalCreditLimit = clientsArray.sumOf { (it.creditLimit as Number?)?.toDouble() ?: 0.0 }
    val clientsWithDebt = clientsArray.count { (it.currentBalance as Number).toDouble() < 0 }
    val clientsWithCredit = clientsArray.count { (it.currentBalance as Number).toDouble() > 0 }
    
    val summaryHtml = """
        <div style="background: white; border-radius: 8px; padding: 20px; margin: 20px 0;">
            <h4 style="margin: 0 0 15px 0; color: #333;">📊 Client Balance Summary</h4>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 20px;">
                <div style="padding: 15px; background-color: #f8f9fa; border-radius: 4px;">
                    <div style="font-size: 24px; font-weight: bold; color: #e74c3c;">¥${totalOutstanding.toInt()}</div>
                    <div style="font-size: 12px; color: #666;">Total Outstanding Balance</div>
                </div>
                <div style="padding: 15px; background-color: #f8f9fa; border-radius: 4px;">
                    <div style="font-size: 24px; font-weight: bold; color: #007bff;">¥${totalCreditLimit.toInt()}</div>
                    <div style="font-size: 12px; color: #666;">Total Credit Limit</div>
                </div>
                <div style="padding: 15px; background-color: #f8f9fa; border-radius: 4px;">
                    <div style="font-size: 24px; font-weight: bold; color: #e74c3c;">$clientsWithDebt</div>
                    <div style="font-size: 12px; color: #666;">Clients with Debt</div>
                </div>
                <div style="padding: 15px; background-color: #f8f9fa; border-radius: 4px;">
                    <div style="font-size: 24px; font-weight: bold; color: #27ae60;">$clientsWithCredit</div>
                    <div style="font-size: 12px; color: #666;">Clients with Credit</div>
                </div>
            </div>
            <div style="text-align: center;">
                <button onclick="exportBalanceSummary()" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Export Summary</button>
            </div>
        </div>
    """
    
    showReportResult(summaryHtml)
}

private fun generateTransactionReport() {
    showMessage("Transaction report generation will be implemented in the next iteration", "info")
}

private fun generateCreditLimitReport() {
    window.fetch("/api/clients")
        .then { response ->
            if (response.ok) {
                response.json().then { clients ->
                    displayCreditLimitReport(clients)
                }
            } else {
                showMessage("Failed to load clients for credit limit report", "error")
            }
        }
        .catch { error ->
            showMessage("Failed to generate credit limit report: $error", "error")
        }
}

private fun displayCreditLimitReport(clients: dynamic) {
    if (!js("Array.isArray(clients)") as Boolean) {
        showMessage("No client data available", "error")
        return
    }
    
    val clientsArray = clients as Array<dynamic>
    val alertClients = clientsArray.filter { client ->
        val balance = (client.currentBalance as Number).toDouble()
        val creditLimit = (client.creditLimit as Number?)?.toDouble()
        val alertThreshold = (client.alertThreshold as Number?)?.toDouble()
        creditLimit != null && alertThreshold != null && balance <= alertThreshold
    }
    
    val reportHtml = """
        <div style="background: white; border-radius: 8px; padding: 20px; margin: 20px 0;">
            <h4 style="margin: 0 0 15px 0; color: #333;">⚠️ Credit Limit Report</h4>
            <div style="margin-bottom: 15px;">
                <div style="font-size: 18px; font-weight: bold; color: #e74c3c;">${alertClients.size} clients approaching credit limits</div>
            </div>
            <div style="max-height: 300px; overflow-y: auto;">
                ${alertClients.map { client ->
                    val balance = (client.currentBalance as Number).toDouble()
                    val creditLimit = (client.creditLimit as Number).toDouble()
                    val remaining = creditLimit + balance
                    val percentage = ((balance / creditLimit) * 100).toInt()
                    
                    """
                    <div style="padding: 10px; border: 1px solid #e9ecef; border-radius: 4px; margin-bottom: 8px;">
                        <div style="display: flex; justify-content: space-between; align-items: center;">
                            <div>
                                <div style="font-weight: bold;">${client.clientName} (#${client.clientNumber})</div>
                                <div style="font-size: 12px; color: #666;">Balance: ¥${balance.toInt()} | Credit Limit: ¥${creditLimit.toInt()}</div>
                            </div>
                            <div style="text-align: right;">
                                <div style="font-size: 14px; font-weight: bold; color: #e74c3c;">${percentage}% used</div>
                                <div style="font-size: 12px; color: #666;">Remaining: ¥${remaining.toInt()}</div>
                            </div>
                        </div>
                    </div>
                    """
                }.joinToString("")}
            </div>
        </div>
    """
    
    showReportResult(reportHtml)
}
*/

private fun generateAuditTrail() {
    showMessage("Audit trail functionality will be implemented in the next iteration", "info")
}

private fun showReportResult(html: String) {
    val resultModal = """
        <div id="reportResultModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10001; display: flex; justify-content: center; align-items: center;">
            <div style="background: white; border-radius: 8px; padding: 20px; max-width: 800px; width: 90%; max-height: 80%; overflow-y: auto;">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h3 style="margin: 0; color: #333;">Report Results</h3>
                    <button id="closeReportResult" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">&times;</button>
                </div>
                $html
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", resultModal)
    
    document.getElementById("closeReportResult")?.addEventListener("click", { _: Event ->
        document.getElementById("reportResultModal")?.remove()
    })
}

// ==============================
// Add Transaction (Event) Modal
// ==============================

private fun openAddTransactionModal(clientId: Long) {
    val modalHTML = """
        <div id="addTransactionModal" class="modal" style="display: block; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5);">
            <div class="modal-content" style="background-color: #fefefe; margin: 5% auto; padding: 20px; border: 1px solid #888; width: 80%; max-width: 600px; border-radius: 8px;">
                <div class="modal-header" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0; color: #333;">Add Transaction</h2>
                    <span class="close" onclick="closeAddTransactionModal()" style="color: #aaa; font-size: 28px; font-weight: bold; cursor: pointer;">&times;</span>
                </div>
                <form id="addTransactionForm" style="display: flex; flex-direction: column; gap: 15px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txDate" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">DATE *</label>
                            <input type="date" id="txDate" name="txDate" required 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div>
                            <label for="txEvent" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Event *</label>
                            <select id="txEvent" name="txEvent" required 
                                    style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                                <option value="">Select Event</option>
                            </select>
                        </div>
                    </div>
                    <div id="txQtyWrap" style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txQuantity" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">QUANTITY</label>
                            <input type="number" id="txQuantity" name="txQuantity" step="1" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div>
                            <label for="txBillNo" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">BILL. NO</label>
                            <input type="text" id="txBillNo" name="txBillNo" 
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                    </div>
                    <div id="txPriceWrap" style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txTransactionPrice" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Total Shipment PRICE</label>
                            <input type="text" id="txTransactionPrice" name="txTransactionPrice" 
                                   placeholder="e.g. ¥5,000 or -¥5,000"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                        <div>
                            <label for="txPaymentReceived" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">PAYMENT RECEIVED</label>
                            <input type="text" id="txPaymentReceived" name="txPaymentReceived" 
                                   placeholder="e.g. ¥10,000 or -¥10,000"
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px;">
                        </div>
                    </div>
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <div>
                            <label for="txRunningBalance" style="display: block; margin-bottom: 5px; font-weight: bold; color: #333;">Total BALANCE</label>
                            <input type="text" id="txRunningBalance" name="txRunningBalance" 
                                   placeholder="Auto-calculated by system"
                                   readonly
                                   style="width: 100%; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; background-color: #f5f5f5;">
                        </div>
                        <div>
                            <!-- Empty div for spacing -->
                        </div>
                    </div>
                    <div style="display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px;">
                        <button type="button" onclick="closeAddTransactionModal()" 
                                style="padding: 10px 20px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Cancel</button>
                        <button type="submit" 
                                style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px;">Save Transaction</button>
                    </div>
                </form>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHTML)

    // Populate event dropdown
    val events = arrayOf(
        "TT RECIEVED",
        "MSC BASIL-HI513A",
        "CAPTAIN THANASIS I-HG514A",
        "MSC MANHATTAN V-HI515A",
        "MSC GENERAL IV-HI516A",
        "MSC AUDREY-GS512S",
        "MSC PRECISION V HI517A",
        "TT RECIEVED(CASH 5-7)",
        "MSC FORTUNE F-XA518A",
        "CAPTHAIN THANASIS I-HG518A",
        "MAERSK VIRGINIA 520S",
        "VIRGO V.520W",
        "NAVIOS TEMPO V.521S"
    )
    val eventSelect = document.getElementById("txEvent") as HTMLSelectElement
    for (ev in events) {
        val opt = document.createElement("option") as HTMLOptionElement
        opt.value = ev
        opt.text = ev
        eventSelect.appendChild(opt)
    }

    fun toggleByEvent(value: String) {
        val hide = value == "TT RECIEVED" || value == "TT RECIEVED(CASH 5-7)"
        (document.getElementById("txQtyWrap") as HTMLElement).style.display = if (hide) "none" else "grid"
        // Only hide the first part of txPriceWrap (Total Shipment PRICE), keep PAYMENT RECEIVED visible
        val txPriceWrap = document.getElementById("txPriceWrap") as HTMLElement
        val firstChild = txPriceWrap.firstElementChild as HTMLElement
        firstChild.style.display = if (hide) "none" else "block"
    }
    eventSelect.addEventListener("change", { _: Event ->
        toggleByEvent(eventSelect.value)
    })

    fun parseCurrency(input: String?): Double? {
        if (input == null) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed.replace(Regex("[^0-9.-]"), "")
        return cleaned.toDoubleOrNull()
    }

    document.getElementById("addTransactionForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        
        val dateIso = (document.getElementById("txDate") as HTMLInputElement).value
        val eventDesc = (document.getElementById("txEvent") as HTMLSelectElement).value
        val qtyStr = (document.getElementById("txQuantity") as HTMLInputElement).value
        val billNo = (document.getElementById("txBillNo") as HTMLInputElement).value
        val tPriceStr = (document.getElementById("txTransactionPrice") as HTMLInputElement).value
        val payStr = (document.getElementById("txPaymentReceived") as HTMLInputElement).value

        if (dateIso.isBlank() || eventDesc.isBlank()) {
            showMessage("Please fill DATE and Event.", "error")
            return@addEventListener
        }

        val quantity = qtyStr.trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
        val transactionPrice = parseCurrency(tPriceStr) ?: 0.0
        val paymentReceived = parseCurrency(payStr) ?: 0.0
        
        // Calculate total balance (used server-side too)
        val totalBalance = paymentReceived - transactionPrice

        // Send payload to backend transaction endpoint
        run {
            val payload = js("({})")
            payload["clientId"] = clientId
            payload["eventDate"] = dateIso
            payload["eventDescription"] = eventDesc
            payload["quantity"] = quantity
            payload["billNumber"] = if (billNo.trim().isEmpty()) null else billNo.trim()
            payload["transactionPrice"] = transactionPrice
            payload["paymentReceived"] = paymentReceived

            val req = js("({})")
            req.method = "POST"
            val headers = js("({})")
            headers["Content-Type"] = "application/json"
            req.headers = headers
            req.body = JSON.stringify(payload)

            console.log("DEBUG: Sending transaction payload:", payload)

            window.fetch("/api/clients/add-transaction", req).then { response ->
                if (response.ok) {
                    response.json().then { result ->
                        console.log("DEBUG: Transaction response:", result)
                        showMessage("Transaction added successfully!", "success")
                        document.getElementById("addTransactionModal")?.remove()
                        // Optimistic UI update: insert row and update balance immediately
                        try {
                            val tx = result.asDynamic()
                            val running = (tx.runningBalance as Number?)?.toDouble()
                            if (running != null) {
                                val balanceColor = if (running < 0) "#e74c3c" else if (running > 0) "#27ae60" else "#666"
                                val balanceText = if (running < 0) "¥${'$'}{kotlin.math.abs(running).toInt()}" else if (running > 0) "+¥${'$'}{running.toInt()}" else "¥0"
                                val balEl = document.getElementById("currentBalanceValue")
                                if (balEl != null) {
                                    balEl.asDynamic().style.color = balanceColor
                                    balEl.textContent = balanceText
                                }
                            }
                            val table = document.querySelector("#clientEventsTable tbody") as HTMLElement?
                            if (table != null) {
                                val row = document.createElement("tr")
                                row.setAttribute("style", "border-bottom: 1px solid #f1f3f4;")
                                row.innerHTML = """
                                    <td style=\"padding: 12px;\">${'$'}dateIso</td>
                                    <td style=\"padding: 12px;\">${'$'}eventDesc</td>
                                    <td style=\"padding: 12px;\">${'$'}{quantity ?: ""} ${'$'}{if (quantity != null) "UNITS" else ""}</td>
                                    <td style=\"padding: 12px;\">${'$'}{if (billNo.trim().isEmpty()) "" else billNo.trim()}</td>
                                    <td style=\"padding: 12px; color: ${'$'}{if (transactionPrice < 0) "#e74c3c" else if (transactionPrice > 0) "#27ae60" else "#666"};\">¥${'$'}{transactionPrice.toInt()}</td>
                                    <td style=\"padding: 12px; color: ${'$'}{if (paymentReceived < 0) "#e74c3c" else if (paymentReceived > 0) "#27ae60" else "#666"};\">¥${'$'}{paymentReceived.toInt()}</td>
                                    <td style=\"padding: 12px; color: ${'$'}{if ((running ?: 0.0) < 0) "#e74c3c" else if ((running ?: 0.0) > 0) "#27ae60" else "#666"};\">${'$'}{if (running != null) (if (running < 0) "¥${'$'}{kotlin.math.abs(running).toInt()}" else if (running > 0) "+¥${'$'}{running.toInt()}" else "¥0") else ""}</td>
                                """
                                // Prepend new row to top of tbody
                                val first = table.firstChild
                                if (first != null) table.insertBefore(row, first) else table.appendChild(row)
                            }
                        } catch (e: dynamic) { }
                        // Also trigger a fresh reload shortly after to confirm state
                        window.setTimeout({
                            loadClientDetails(clientId)
                            loadClientEvents(clientId)
                        }, 300)
                    }
                } else {
                    response.text().then { errorText ->
                        console.error("Backend error:", errorText)
                        showMessage("Failed to add transaction: ${errorText}", "error")
                    }
                }
            }.catch { error ->
                console.error("Network error:", error)
                showMessage("Failed to add transaction: ${'$'}error", "error")
            }
        }
    })

    // Initialize visibility
    toggleByEvent("")
    
    // Add real-time calculation for Total BALANCE
    fun updateTotalBalance() {
        val transactionPrice = parseCurrency((document.getElementById("txTransactionPrice") as HTMLInputElement).value) ?: 0.0
        val paymentReceived = parseCurrency((document.getElementById("txPaymentReceived") as HTMLInputElement).value) ?: 0.0
        
        // For new transactions, start from 0 and calculate: payment - transaction
        val newBalance = paymentReceived - transactionPrice
        
        // Update the Total BALANCE field
        val balanceField = document.getElementById("txRunningBalance") as HTMLInputElement
        balanceField.value = "¥${newBalance.toInt()}"
    }
    
    // Add event listeners for real-time calculation
    document.getElementById("txTransactionPrice")?.addEventListener("input", { updateTotalBalance() })
    document.getElementById("txPaymentReceived")?.addEventListener("input", { updateTotalBalance() })
    
    // Initial calculation
    updateTotalBalance()
}

private fun closeAddTransactionModal() {
    document.getElementById("addTransactionModal")?.remove()
}

@Suppress("UnsafeCastFromDynamic")
private fun exposeAddTransactionModal() {
    try {
        window.asDynamic().openAddTransactionModal = { id: dynamic ->
            val cid = try { (id as Number).toLong() } catch (e: dynamic) { id.toString().toLong() }
            openAddTransactionModal(cid)
        }
        window.asDynamic().closeAddTransactionModal = ::closeAddTransactionModal
    } catch (e: dynamic) { }
}

// Ensure exposure on module load
@Suppress("unused")
private val __exposeAddTxOnce = run {
    exposeAddTransactionModal()
    exposeColumnFilterFunctions()
}

// Column Filter Functions
private fun showColumnFilterModal() {
    val modal = document.createElement("div")
    modal.id = "columnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; align-items: center; justify-content: center;
    """
    
    val selectedColumns = getSelectedColumns()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h3 style="margin: 0; color: #333;">Select Columns to Display</h3>
                <button id="closeColumnFilter" style="background: none; border: none; font-size: 24px; cursor: pointer; color: #666;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>Maximum 9 columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="selectedCount">${selectedColumns.size}</span>/9</span>
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
    
    // Populate column checkboxes
    populateColumnCheckboxes(selectedColumns)
    
    // Add event listeners
    document.getElementById("closeColumnFilter")?.addEventListener("click", { closeColumnFilterModal() })
    document.getElementById("resetColumns")?.addEventListener("click", { resetToDefaultColumns() })
    document.getElementById("applyColumns")?.addEventListener("click", { applyColumnChanges() })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event ->
        if (event.target == modal) {
            closeColumnFilterModal()
        }
    })
}

private fun populateColumnCheckboxes(selectedColumns: Set<String>) {
    val container = document.getElementById("columnCheckboxes")
    if (container == null) return
    
    val allColumns = mapOf(
        "date" to "Purchase Date",
        "chassis" to "Chassis",
        "carName" to "Car Name",
        "auctionHouse" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "clientName" to "Client Name",
        "rixoCompany" to "Rixo Company",
        "price" to "Price",
        "carModelYear" to "Production Date",
        "brand" to "Brand",
        "grade" to "Grade",
        "rank" to "Rank",
        "color" to "Color",
        "displacement" to "Displacement",
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
        "shipmentSize" to "Shipment Size",
        "numberCut" to "Number Cut",
        "taxTotal" to "Tax Total",
        "profit" to "Profit",
        "packagePrice" to "Package Price",
        "bookingId" to "Booking ID",
        "notes" to "Notes"
    )
    
    container.innerHTML = allColumns.map { (key, label) ->
        val isChecked = selectedColumns.contains(key)
        """
        <label style="display: flex; align-items: center; gap: 8px; padding: 8px; border-radius: 4px; cursor: pointer; transition: background-color 0.2s;" 
               onmouseover="this.style.backgroundColor='#f8f9fa'" onmouseout="this.style.backgroundColor='transparent'">
            <input type="checkbox" value="$key" ${if (isChecked) "checked" else ""} 
                   style="transform: scale(1.1);" onchange="updateColumnSelection()">
            <span style="font-size: 14px;">$label</span>
        </label>
        """
    }.joinToString("")
}

private fun updateColumnSelection() {
    val checkboxes = document.querySelectorAll("#columnCheckboxes input[type='checkbox']")
    val selectedCount = checkboxes.asDynamic().length
    var count = 0
    for (i in 0 until selectedCount) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) count++
    }
    
    document.getElementById("selectedCount")?.textContent = count.toString()
    
    // Disable checkboxes if 9 are selected
    for (i in 0 until selectedCount) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (!checkbox.checked && count >= 9) {
            checkbox.disabled = true
            checkbox.parentElement?.asDynamic()?.style?.setProperty("opacity", "0.5")
        } else {
            checkbox.disabled = false
            checkbox.parentElement?.asDynamic()?.style?.setProperty("opacity", "1")
        }
    }
}

private fun getSelectedColumns(): Set<String> {
    val saved = window.localStorage.getItem("selectedColumns")
    return if (saved != null) {
        try {
            val columns = JSON.parse(saved) as Array<String>
            columns.toSet()
        } catch (e: dynamic) {
            getDefaultColumns()
        }
    } else {
        getDefaultColumns()
    }
}

private fun getDefaultColumns(): Set<String> {
    return setOf("date", "chassis", "carName", "auctionHouse", "stockLocation", "clientName", "rixoCompany", "price")
}

private fun saveSelectedColumns(columns: Set<String>) {
    window.localStorage.setItem("selectedColumns", JSON.stringify(columns.toTypedArray()))
}

private fun resetToDefaultColumns() {
    val defaultColumns = getDefaultColumns()
    saveSelectedColumns(defaultColumns)
    populateColumnCheckboxes(defaultColumns)
    updateColumnSelection()
}

private fun applyColumnChanges() {
    val checkboxes = document.querySelectorAll("#columnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableSetOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedColumns.add(checkbox.value)
        }
    }
    
    if (selectedColumns.size > 9) {
        showMessage("Please select maximum 9 columns", "error")
        return
    }
    
    saveSelectedColumns(selectedColumns)
    closeColumnFilterModal()
    loadPurchases() // Reload to apply column changes
}

private fun closeColumnFilterModal() {
    document.getElementById("columnFilterModal")?.remove()
}

// Expose column filter functions globally
@Suppress("UnsafeCastFromDynamic")
private fun exposeColumnFilterFunctions() {
    try {
        window.asDynamic().updateColumnSelection = { updateColumnSelection() }
    } catch (e: dynamic) {
        console.log("Error exposing column filter functions:", e)
        }
}

// Car Booking Page - Main booking interface
fun showCarBookingPage() {
    try {
        console.log("=== showCarBookingPage() function called ===")
        val content = document.getElementById("content") ?: return
    
    content.innerHTML = """
        <div style="width: 100%; min-height: calc(100vh - 140px); padding: 20px; box-sizing: border-box;">
            <!-- Header -->
            <div style="background: white; border-radius: 12px; padding: 24px; margin-bottom: 20px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 1px solid #e5e7eb;">
                <div style="display: flex; justify-content: space-between; align-items: center;">
                      <h1 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">AUTOMAN | CREATE SHIPPING SCHEDULE</h1>
                      <div style="display: flex; align-items: center; gap: 16px;">
                          <button id="purchaseListBtn" style="padding: 8px 16px; background-color: #3b82f6; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">📋 Purchase List</button>
                      </div>
                </div>
            </div>
            
            <!-- Main Content Container -->
            <div style="background: white; border-radius: 12px; padding: 30px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); border: 1px solid #e5e7eb;">
                <div style="display: flex; gap: 30px; min-height: 600px;">
                    
                    <!-- Left Section: BOOKING DETAILS -->
                    <div style="flex: 1; padding-right: 20px; border-right: 2px solid #e5e7eb;">
                        <h2 style="margin: 0 0 24px 0; color: #111827; font-size: 20px; font-weight: 700; text-transform: uppercase;">BOOKING DETAILS</h2>
                        
                        <!-- CONSIGNEE -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">CONSIGNEE:</label>
                            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                                <span style="color: #6b7280; font-size: 16px;">👤</span>
                                <select id="consigneeCountry" style="flex: 1; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                    <option value="">Select Country</option>
                                </select>
                                <span style="color: #6b7280; font-size: 16px; cursor: pointer;">✏️</span>
                            </div>
                            <input type="text" id="consigneeName" placeholder="(CONSIGNEE NAME)" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                        </div>
                        
                        <!-- ETD -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">ETD:</label>
                            <input type="date" id="etdDate" placeholder="ESTIMATED SHIPPING DATE" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; color: #000000;">
                        </div>
                        
                        <!-- POL -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POL:</label>
                            <select id="polPort" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; color: #000000;">
                                <option value="">Select Port of Loading</option>
                            </select>
                        </div>
                        
                        <!-- POD -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POD:</label>
                            <input type="text" id="podPort" placeholder="PORT OF DISCHARGE" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; color: #000000;">
                        </div>
                        
                        <!-- BOOKING NO -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">BOOKING NO:</label>
                            <input type="text" id="bookingNo" placeholder="" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                        </div>
                        
                        <!-- VESSEL -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">VESSEL:</label>
                            <select id="vesselSelect" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                <option value="">Select Vessel</option>
                            </select>
                        </div>
                        
                        <!-- SEARCH CHASSIS -->
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">SEARCH CHASSIS:</label>
                            <select id="chassisSearch" style="width: 100%; padding: 10px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                <option value="">Select Chassis (Filtered by Country & POL)</option>
                            </select>
                        </div>
                        
                        <!-- Selection Options -->
                        <div style="margin-top: 30px;">
                            <div style="display: flex; gap: 20px; margin-bottom: 15px;">
                                <label style="display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 14px; font-weight: 600; color: #111827;">
                                    <input type="checkbox" id="cnfCheckbox" style="width: 18px; height: 18px; accent-color: #3b82f6;">
                                    C&F
                                </label>
                                <label style="display: flex; align-items: center; gap: 8px; cursor: pointer; font-size: 14px; font-weight: 600; color: #111827;">
                                    <input type="checkbox" id="fobCheckbox" style="width: 18px; height: 18px; accent-color: #3b82f6;">
                                    FOB
                                </label>
                            </div>
                            <button id="calculateBtn" style="width: 100%; padding: 12px 20px; background: #3b82f6; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600;">Calculate</button>
                        </div>
                        
                        <!-- Additional Action Buttons -->
                        <div style="display: flex; gap: 12px; margin-top: 15px; justify-content: space-between;">
                            <a href="#" id="cancelBtn" style="text-decoration: underline; color: #111827; font-size: 14px; font-weight: 600; padding: 8px 0;">CANCEL</a>
                            <button id="emailBtn" style="padding: 8px 16px; background: white; color: #111827; border: 1px solid #d1d5db; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 6px;">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path>
                                    <polyline points="22,6 12,13 2,6"></polyline>
                                </svg>
                                EMAIL
                            </button>
                            <button id="exportExcelBtn" style="padding: 8px 16px; background: white; color: #111827; border: 1px solid #d1d5db; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 6px;">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                                    <polyline points="14,2 14,8 20,8"></polyline>
                                    <line x1="16" y1="13" x2="8" y2="13"></line>
                                    <line x1="16" y1="17" x2="8" y2="17"></line>
                                    <polyline points="10,9 9,9 8,9"></polyline>
                                </svg>
                                EXPORT EXCEL
                            </button>
                            <button id="downloadPdfBtn" style="padding: 8px 16px; background: white; color: #111827; border: 1px solid #d1d5db; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 6px;">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                                    <polyline points="14,2 14,8 20,8"></polyline>
                                    <line x1="16" y1="13" x2="8" y2="13"></line>
                                    <line x1="16" y1="17" x2="8" y2="17"></line>
                                    <text x="12" y="15" text-anchor="middle" font-size="6" fill="currentColor">PDF</text>
                                </svg>
                                DOWNLOAD PDF
                            </button>
                        </div>
                    </div>
                    
                    <!-- Right Section: LIST -->
                    <div style="flex: 1; padding-left: 20px;">
                        <h2 style="margin: 0 0 24px 0; color: #111827; font-size: 20px; font-weight: 700; text-transform: uppercase;">LIST</h2>
                        
                        <!-- Car Selection Table -->
                        <div style="border: 1px solid #e5e7eb; border-radius: 8px; overflow: hidden; margin-bottom: 20px;">
                            <table style="width: 100%; border-collapse: collapse;">
                                <thead style="background-color: #f9fafb;">
                                    <tr>
                                        <th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">
                                            <input type="checkbox" id="selectAllCars" style="margin-right: 8px;">SELECT
                                        </th>
                                        <th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">NO.</th>
                                        <th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">CHASSIS</th>
                                        <th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">NAME</th>
                                        <th style="padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 1px solid #e5e7eb;">YEAR</th>
                                    </tr>
                                </thead>
                                <tbody id="carSelectionTableBody">
                                    <!-- Cars will be loaded here -->
                                </tbody>
                            </table>
                        </div>
                        
                        <!-- SHOW FULL PREVIEW Button -->
                        <div style="text-align: center;">
                            <button id="showFullPreviewBtn" style="padding: 16px 32px; background-color: #111827; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px; font-weight: 600;">SHOW FULL PREVIEW</button>
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
    
    console.log("=== About to load vessels ===")
    // Load vessels
    loadVessels()
    
    // Load countries from database
    loadCountries()
    
    // Load stock locations (POL) from database
    loadStockLocations()
    
    // Restore Car Booking state if it exists
    js("setTimeout(function() { window.restoreCarBookingState(); }, 500)")
    
    // Don't load cars automatically - wait for user search
    console.log("Car Booking page loaded - waiting for user to search by chassis number")
    
    // Immediate fallback - load vessels directly since API is not working
    console.log("Loading vessels with immediate fallback...")
    console.log("About to call loadVesselsFallback()")
    
    // Force load vessels immediately - bypass API entirely
    // Removed problematic setTimeout call
    
    // Also try calling directly without timeout
    console.log("Calling fallback functions directly...")
    try {
        loadVesselsFallback()
        console.log("loadVesselsFallback() called directly - SUCCESS")
    } catch (e: dynamic) {
        console.error("Error calling loadVesselsFallback() directly:", e)
    }
    
    // Cars will be loaded when user searches by chassis number
    
    console.log("loadVesselsFallback() scheduled")
    
    // Safety fallback - ensure vessels are loaded even if API fails
    // Removed problematic setTimeout call
    } catch (e: dynamic) {
        console.error("Error in showCarBookingPage():", e)
    }
}

// Setup event listeners for Car Booking page
fun setupCarBookingPageListeners() {
    // Restore booking selection state (C&F or FOB)
    restoreBookingSelectionState()
    
    // Purchase List button
    document.getElementById("purchaseListBtn")?.addEventListener("click", { _: Event ->
        console.log("📋 Purchase List button clicked - navigating to existing purchase list")
        showPurchaseList()
    })
    
    // Country dropdown change - trigger filtered chassis loading
    document.getElementById("consigneeCountry")?.addEventListener("change", { event: Event ->
        val selectedCountry = (event.target as HTMLSelectElement).value
        console.log("🌍 Country selected:", selectedCountry)
        currentSelectedCountry = selectedCountry // Update the global variable
        console.log("Country changed, loading filtered chassis...")
        loadFilteredChassis()
    })
    
    // POL dropdown change - trigger filtered chassis loading
    document.getElementById("polPort")?.addEventListener("change", { _: Event ->
        console.log("POL changed, loading filtered chassis...")
        loadFilteredChassis()
    })
    
    // Chassis dropdown change - search for cars with selected chassis
    document.getElementById("chassisSearch")?.addEventListener("change", { _: Event ->
        val chassisSelect = document.getElementById("chassisSearch") as HTMLSelectElement?
        val selectedChassis = chassisSelect?.value ?: ""
        if (selectedChassis.isNotEmpty()) {
            console.log("Chassis selected:", selectedChassis)
            searchCarsByChassis(selectedChassis)
        } else {
            clearCarTable()
        }
    })
    
    // Select all cars
    document.getElementById("selectAllCars")?.addEventListener("change", { _: Event ->
        val isChecked = (document.getElementById("selectAllCars") as HTMLInputElement).checked
        val checkboxes = document.querySelectorAll("#carSelectionTableBody input[type='checkbox']")
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.checked = isChecked
        }
        // Update chassis dropdown when select all changes
        updateChassisDropdown()
    })
    
    // Individual car selection checkboxes - update chassis dropdown
    val carTableBody = document.getElementById("carSelectionTableBody")
    carTableBody?.addEventListener("change", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.getAttribute("type") == "checkbox" && target.classList.contains("car-checkbox")) {
            updateChassisDropdown()
        }
    })
    
    // Calculate freight button (placeholder for future freight calculation page)
    document.getElementById("calculateFreightBtn")?.addEventListener("click", { _: Event ->
        showCalculateFreightPage()
    })
    
    // Show full preview
    document.getElementById("showFullPreviewBtn")?.addEventListener("click", { _: Event ->
        showFullPreview()
    })
    
    // C&F and FOB checkboxes - mutual exclusivity
    document.getElementById("cnfCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        if (target.checked) {
            // Uncheck FOB if C&F is checked
            val fobCheckbox = document.getElementById("fobCheckbox") as HTMLInputElement?
            fobCheckbox?.checked = false
            // Save state
            saveBookingSelectionState("cnf")
            console.log("✅ C&F selected, FOB unchecked")
        }
    })
    
    document.getElementById("fobCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        if (target.checked) {
            // Uncheck C&F if FOB is checked
            val cnfCheckbox = document.getElementById("cnfCheckbox") as HTMLInputElement?
            cnfCheckbox?.checked = false
            // Save state
            saveBookingSelectionState("fob")
            console.log("✅ FOB selected, C&F unchecked")
        }
    })
    
    // Calculate button - navigate based on selection
    document.getElementById("calculateBtn")?.addEventListener("click", { _: Event ->
        val cnfChecked = (document.getElementById("cnfCheckbox") as HTMLInputElement?)?.checked ?: false
        val fobChecked = (document.getElementById("fobCheckbox") as HTMLInputElement?)?.checked ?: false
        
        if (!cnfChecked && !fobChecked) {
            showMessage("Please select either C&F or FOB before calculating", "error")
            return@addEventListener
        }
        
        if (cnfChecked && fobChecked) {
            showMessage("Please select only one option (C&F or FOB)", "error")
            return@addEventListener
        }
        
        // Save Car Booking state before navigating
        saveCarBookingState()
        
        // Store booking details for PDF generation
        storeBookingDetailsForPdf()
        
        // Get the selected cars and first selected car's chassis
        val selectedCars = getSelectedCarsFromTable()
        val selectedChassis = if (selectedCars.isNotEmpty()) selectedCars[0].chassis else null
        
        if (cnfChecked) {
            console.log("🚗 Navigating to C&F page with ${selectedCars.size} selected cars")
            showCnfCalculationPage(selectedChassis, selectedCars, currentSelectedCountry)
        } else if (fobChecked) {
            console.log("🚗 Navigating to FOB page with ${selectedCars.size} selected cars")
            showFobCalculationPage(selectedChassis, selectedCars)
        }
    })
    
    // CANCEL button - navigate back to purchase list
    document.getElementById("cancelBtn")?.addEventListener("click", { event: Event ->
        event.preventDefault()
        console.log("❌ CANCEL button clicked - navigating back to purchase list")
        showPurchaseList()
    })
    
    // EMAIL button - placeholder for email functionality
    document.getElementById("emailBtn")?.addEventListener("click", { _: Event ->
        console.log("📧 EMAIL button clicked - functionality to be implemented")
        showMessage("Email functionality will be implemented later", "info")
    })
    
    // EXPORT EXCEL button - placeholder for Excel export functionality
    document.getElementById("exportExcelBtn")?.addEventListener("click", { _: Event ->
        console.log("📊 EXPORT EXCEL button clicked - functionality to be implemented")
        showMessage("Excel export functionality will be implemented later", "info")
    })
    
    // DOWNLOAD PDF button - generate shipping schedule PDF
    document.getElementById("downloadPdfBtn")?.addEventListener("click", { _: Event ->
        console.log("📄 DOWNLOAD PDF button clicked - generating shipping schedule PDF")
        generateShippingSchedulePdf()
    })
}

// Generate shipping schedule PDF from BOOKING DETAILS page
fun generateShippingSchedulePdf() {
    console.log("✅ Generating shipping schedule PDF from BOOKING DETAILS page...")
    
    // Store booking details for PDF generation
    storeBookingDetailsForPdf()
    
    // Prepare PDF request data
    val pdfRequest = js("{}")
    pdfRequest.bookingNo = globalBookingDetails.bookingNo
    pdfRequest.vesselName = globalBookingDetails.vesselName
    pdfRequest.pol = globalBookingDetails.pol
    pdfRequest.pod = globalBookingDetails.pod
    pdfRequest.shippingDate = globalBookingDetails.shippingDate
    pdfRequest.consigneeName = globalBookingDetails.consigneeName
    pdfRequest.consigneeAddress = globalBookingDetails.consigneeAddress
    pdfRequest.chassisNumbers = globalSelectedCarsForPdf.map { it.chassis }
    
    console.log("📋 PDF Request data:", pdfRequest)
    console.log("🚀🚀🚀 USING CORRECT ENDPOINT: /api/purchases/shipping-schedule/generate-pdf 🚀🚀🚀")
    
    // Call PDF generation API
    js("fetch('/api/purchases/shipping-schedule/generate-pdf', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(pdfRequest) })")
        .then { response: dynamic ->
            if (!response.ok) {
                throw Exception("HTTP error! status: ${response.status}")
            }
            response.blob()
        }
        .then { pdfBlob: dynamic ->
            console.log("✅ PDF generated successfully")
            console.log("📥 Downloading PDF directly...")
            
            // Create download link directly
            val url = js("URL.createObjectURL(pdfBlob)")
            val link = js("document.createElement('a')")
            link.setAttribute("href", url)
            link.setAttribute("download", "shipping_schedule_${globalBookingDetails.bookingNo ?: "unknown"}.pdf")
            js("document.body.appendChild(link)")
            js("link.click()")
            js("document.body.removeChild(link)")
            js("setTimeout(function() { URL.revokeObjectURL(url); }, 1000)")
            console.log("✅ PDF download initiated")
        }
        .catch { error: dynamic ->
            console.error("❌ Error generating PDF:", error)
            js("alert('Error generating PDF: ' + error.message)")
        }
}

// Load vessels from API (with fallback to hardcoded data)
fun loadVessels() {
    console.log("Loading vessels...")
    
    js("fetch('/api/api/vessels')")
        .then { response: dynamic ->
            console.log("Vessels API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                throw js("new Error('Failed to load vessels - Status: ' + response.status)")
            }
        }
        .then { vessels: dynamic ->
            console.log("Vessels loaded from API:", vessels)
            val vesselSelect = document.getElementById("vesselSelect") as HTMLSelectElement?
            if (vesselSelect != null) {
                vesselSelect.innerHTML = "<option value=\"\">Select Vessel</option>"
                
                vessels.forEach { vessel: dynamic ->
                    val option = document.createElement("option") as HTMLOptionElement
                    option.value = vessel.vesselNo
                    option.textContent = "${vessel.vesselName} (${vessel.vesselNo})"
                    vesselSelect.appendChild(option)
                }
                console.log("Vessels populated from API successfully")
            } else {
                console.error("Vessel select element not found!")
            }
        }
        .catch { error: dynamic ->
            console.error("Error loading vessels from API, using fallback data:", error)
            // Fallback to hardcoded vessel data
            loadVesselsFallback()
        }
}

// Fallback vessel data when API is not available
fun loadVesselsFallback() {
    console.log("Loading fallback vessel data...")
    console.log("Searching for vesselSelect element...")
    
    val vesselSelect = document.getElementById("vesselSelect") as HTMLSelectElement?
    console.log("Vessel select element found:", vesselSelect)
    
    if (vesselSelect == null) {
        console.error("Vessel select element not found in fallback!")
        return
    }
    
    console.log("Clearing vessel select and adding default option...")
    vesselSelect.innerHTML = "<option value=\"\">Select Vessel</option>"
    
    // Vessel data from your database - exact match with vessels table
    val fallbackVessels = listOf(
        mapOf("vesselNo" to "CAP789", "vesselName" to "CAPTAIN THANASIS I", "company" to "CAPTAIN"),
        mapOf("vesselNo" to "MAE012", "vesselName" to "MAERSK VIRGINIA", "company" to "MAERSK"),
        mapOf("vesselNo" to "MSC123", "vesselName" to "MSC BASIL", "company" to "MSC"),
        mapOf("vesselNo" to "MSC456", "vesselName" to "MSC MANHATTAN V", "company" to "MSC"),
        mapOf("vesselNo" to "NAV678", "vesselName" to "NAVIOS TEMPO V", "company" to "NAVIOS"),
        mapOf("vesselNo" to "VIR345", "vesselName" to "VIRGO V", "company" to "VIRGO"),
        mapOf("vesselNo" to "VSL001", "vesselName" to "Ever Given", "company" to "Evergreen Marine"),
        mapOf("vesselNo" to "VSL002", "vesselName" to "MSC Oscar", "company" to "MSC"),
        mapOf("vesselNo" to "VSL003", "vesselName" to "CMA CGM Marco Polo", "company" to "CMA CGM")
    )
    
    console.log("Adding", fallbackVessels.size, "vessels to dropdown...")
    
    fallbackVessels.forEach { vessel ->
        val option = document.createElement("option") as HTMLOptionElement
        option.value = vessel["vesselNo"] as String
        option.textContent = "${vessel["vesselName"]} (${vessel["vesselNo"]})"
        vesselSelect.appendChild(option)
        console.log("Added vessel:", option.textContent)
    }
    
    console.log("Fallback vessels loaded successfully:", fallbackVessels.size, "vessels")
    console.log("Final vessel select options count:", vesselSelect.options.length)
}

// Load countries from purchases table
fun loadCountries() {
    console.log("Loading countries from purchases table...")
    
    js("fetch('/api/purchases/countries')")
        .then { response: dynamic ->
            console.log("Countries API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                console.log("Countries API failed, using fallback")
                js("Promise.resolve([])")
            }
        }
        .then { countries: dynamic ->
            console.log("Countries data received:", countries)
            val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
            if (countrySelect != null) {
                // Clear existing options except the first one
                countrySelect.innerHTML = "<option value=\"\">Select Country</option>"
                
                // Add countries from API
                val countriesArray = countries as Array<dynamic>
                countriesArray.forEach { country ->
                    val option = document.createElement("option")
                    option.setAttribute("value", country as String)
                    option.textContent = country as String
                    countrySelect.appendChild(option)
                }
                console.log("Countries loaded from API:", countriesArray.size)
            }
        }
        .catch { error: dynamic ->
            console.error("Error loading countries:", error)
            loadCountriesFallback()
        }
}

// Load countries fallback data
fun loadCountriesFallback() {
    console.log("Loading fallback country data...")
    
    val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
    if (countrySelect == null) {
        console.error("Country select element not found in fallback!")
        return
    }
    
    console.log("Clearing country select and adding default option...")
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
    
    console.log("Adding", fallbackCountries.size, "countries to dropdown...")
    
    fallbackCountries.forEach { country ->
        val option = document.createElement("option")
        option.setAttribute("value", country)
        option.textContent = country
        countrySelect.appendChild(option)
        console.log("Added country:", country)
    }
    
    console.log("Fallback countries loaded successfully:", fallbackCountries.size, "countries")
    console.log("Final country select options count:", countrySelect.options.length)
}

// Load stock locations (POL) from purchases table
fun loadStockLocations() {
    console.log("Loading stock locations from purchases table...")
    
    js("fetch('/api/purchases/stock-locations')")
        .then { response: dynamic ->
            console.log("Stock locations API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                console.log("Stock locations API failed, using fallback")
                js("Promise.resolve([])")
            }
        }
        .then { stockLocations: dynamic ->
            console.log("Stock locations data received:", stockLocations)
            val polSelect = document.getElementById("polPort") as HTMLSelectElement?
            if (polSelect != null) {
                // Clear existing options except the first one
                polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"
                
                // Add stock locations from API
                val stockLocationsArray = stockLocations as Array<dynamic>
                stockLocationsArray.forEach { location ->
                    val option = document.createElement("option")
                    option.setAttribute("value", location as String)
                    option.textContent = location as String
                    polSelect.appendChild(option)
                }
                console.log("Stock locations loaded from API:", stockLocationsArray.size)
            }
        }
        .catch { error: dynamic ->
            console.error("Error loading stock locations:", error)
            loadStockLocationsFallback()
        }
}

// Load stock locations fallback data
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

// Load filtered chassis based on selected country and POL
fun loadFilteredChassis() {
    val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
    val polSelect = document.getElementById("polPort") as HTMLSelectElement?
    val chassisSelect = document.getElementById("chassisSearch") as HTMLSelectElement?
    
    if (countrySelect == null || polSelect == null || chassisSelect == null) {
        console.error("Required select elements not found!")
        return
    }
    
    val selectedCountry = countrySelect.value
    val selectedPol = polSelect.value
    
    if (selectedCountry.isEmpty() || selectedPol.isEmpty()) {
        console.log("Country or POL not selected, clearing chassis dropdown")
        chassisSelect.innerHTML = "<option value=\"\">Select Chassis (Filtered by Country & POL)</option>"
        return
    }
    
    console.log("Loading filtered chassis for country:", selectedCountry, "and POL:", selectedPol)
    
    js("fetch('/api/purchases/filtered-chassis?country=' + encodeURIComponent(selectedCountry) + '&polPort=' + encodeURIComponent(selectedPol))")
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
            
            // Clear existing options except the first one
            chassisSelect.innerHTML = "<option value=\"\">Select Chassis (Filtered by Country & POL)</option>"
            
            // Add chassis from API
            val chassisArray = chassis as Array<dynamic>
            chassisArray.forEach { chassisNumber ->
                val option = document.createElement("option")
                option.setAttribute("value", chassisNumber as String)
                option.textContent = chassisNumber as String
                chassisSelect.appendChild(option)
            }
            console.log("Filtered chassis loaded from API:", chassisArray.size)
        }
        .catch { error: dynamic ->
            console.error("Error loading filtered chassis:", error)
            loadFilteredChassisFallback()
        }
}

// Load filtered chassis fallback data
fun loadFilteredChassisFallback() {
    console.log("Loading fallback filtered chassis data...")
    
    val chassisSelect = document.getElementById("chassisSearch") as HTMLSelectElement?
    if (chassisSelect == null) {
        console.error("Chassis select element not found in fallback!")
        return
    }
    
    console.log("Clearing chassis select and adding default option...")
    chassisSelect.innerHTML = "<option value=\"\">Select Chassis (Filtered by Country & POL)</option>"
    
    // Sample chassis data - in real implementation, this would be filtered by country and POL
    val fallbackChassis = listOf(
        "VY12-265058",
        "ANH20-8170371",
        "AVU65-0007399",
        "LA350S-0306292",
        "LA350S-0305865",
        "SLP2T-105089"
    )
    
    console.log("Adding", fallbackChassis.size, "chassis to dropdown...")
    
    fallbackChassis.forEach { chassis ->
        val option = document.createElement("option")
        option.setAttribute("value", chassis)
        option.textContent = chassis
        chassisSelect.appendChild(option)
        console.log("Added chassis:", chassis)
    }
    
    console.log("Fallback filtered chassis loaded successfully:", fallbackChassis.size, "chassis")
    console.log("Final chassis select options count:", chassisSelect.options.length)
}

// Search cars by specific chassis number
fun searchCarsByChassis(chassis: String) {
    console.log("Searching cars by chassis:", chassis)
    
    js("fetch('/api/purchases/search?query=' + encodeURIComponent(chassis))")
        .then { response: dynamic ->
            console.log("Search API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                console.log("Search API failed, using fallback")
                js("Promise.resolve([])")
            }
        }
        .then { purchases: dynamic ->
            console.log("Search results received:", purchases)
            displayPurchasesAsCarsAPPEND(purchases)
        }
        .catch { error: dynamic ->
            console.error("Error searching cars:", error)
            searchCarsFallback(chassis)
        }
}

// Load unshipped cars from API (with fallback to hardcoded data)
// Old functions removed - now using search-based approach

// Display cars in the table
fun displayCars(cars: dynamic) {
    val tbody = document.getElementById("carSelectionTableBody")
    tbody?.innerHTML = ""
    
    cars.forEachIndexed { index: Int, car: dynamic ->
        val row = document.createElement("tr")
        row.innerHTML = """
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">
                <input type="checkbox" value="${car.id}" style="margin-right: 8px;">
            </td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${index + 1}</td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${car.chassis}</td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${car.carName ?: "N/A"}</td>
            <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${car.carModelYear ?: "N/A"}</td>
        """
        tbody?.appendChild(row)
    }
}

// Search cars by chassis from purchases table
fun searchCars() {
    console.log("=== searchCars() function called ===")
    val chassisSearch = (document.getElementById("chassisSearch") as HTMLInputElement).value.trim()
    console.log("Search term:", chassisSearch)
    
    if (chassisSearch.isBlank()) {
        clearCarTable()
        clearAccumulatedCars() // Clear accumulated cars when search is cleared
        console.log("Search cleared - table emptied")
        return
    }
    
    console.log("Searching purchases table for chassis:", chassisSearch)
    
    // Search the purchases table by chassis number
    js("fetch('/api/purchases/search?query=' + encodeURIComponent(chassisSearch))")
        .then { response: dynamic ->
            console.log("Purchases search API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                throw js("new Error('Failed to search purchases - Status: ' + response.status)")
            }
        }
        .then { purchases: dynamic ->
            console.log("Found purchases from API:", purchases)
                displayPurchasesAsCarsAPPEND(purchases)
        }
        .catch { error: dynamic ->
            console.error("Error searching purchases from API, using fallback search:", error)
            // Fallback: search in sample data
            searchCarsFallback(chassisSearch)
        }
}

// Clear the car table
fun clearCarTable() {
    val tbody = document.getElementById("carSelectionTableBody")
    tbody?.innerHTML = ""
    console.log("Car table cleared")
}

// Clear accumulated displayed cars (call when starting new search)
fun clearAccumulatedCars() {
    carBookingDisplayedCars = emptyArray()
    console.log("🧹 Cleared accumulated displayed cars")
}

// Wrapper function for state restoration
fun displayPurchasesAsCars(purchases: dynamic) {
    displayPurchasesAsCarsAPPEND(purchases)
}

// Display purchases as cars in the table - APPEND VERSION
fun displayPurchasesAsCarsAPPEND(purchases: dynamic) {
    console.log("🔥🔥🔥 NEW APPEND FUNCTION CALLED - CACHE BUSTED! 🔥🔥🔥")
    console.log("💰💰💰 CURRENCY FORMATTING ACTIVE - CACHE BUSTED! 💰💰💰")
    console.log("Displaying purchases as cars:", purchases)
    
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
        console.log("Available elements with 'car' in ID:")
        val allElements = document.querySelectorAll("[id*='car']")
        for (i in 0 until allElements.length) {
            val element = allElements.item(i) as HTMLElement
            console.log("Found element:", element.id, element.tagName)
        }
        return
    }
    
    // Don't clear the table - append new cars instead
    // tbody.innerHTML = ""  // REMOVED: This was clearing the table
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
        
        if (!carAlreadyExists) {
            val row = document.createElement("tr")
            val rowNumber = currentRowCount + index + 1
            row.innerHTML = """
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">
                    <input type="checkbox" class="car-checkbox" data-purchase-id="${purchase.id}" data-chassis="${chassisNumber}">
                </td>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${rowNumber}</td>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${chassisNumber}</td>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${purchase.carName ?: "N/A"}</td>
                <td style="padding: 12px; border-bottom: 1px solid #e5e7eb;">${purchase.carModelYear ?: "N/A"}</td>
            """
            tbody.appendChild(row)
            console.log("Added car", chassisNumber, "as row", rowNumber)
        }
    }
    
    console.log("Displayed", purchasesArray.size, "purchases in car table")
}

// Fallback search function for when API fails
fun searchCarsFallback(searchTerm: String) {
    console.log("Using fallback search for term:", searchTerm)
    
    // Sample purchase data for testing
    val samplePurchases = listOf(
        mapOf("id" to 1, "chassis" to "KDH201-5012551", "carName" to "Toyota Camry", "carModelYear" to "2020"),
        mapOf("id" to 2, "chassis" to "KDH201-5012552", "carName" to "Honda Accord", "carModelYear" to "2021"),
        mapOf("id" to 3, "chassis" to "KDH201-5012553", "carName" to "Nissan Altima", "carModelYear" to "2019"),
        mapOf("id" to 4, "chassis" to "KDH201-5012554", "carName" to "BMW 3 Series", "carModelYear" to "2022"),
        mapOf("id" to 5, "chassis" to "KDH201-5012555", "carName" to "Mercedes C-Class", "carModelYear" to "2021"),
        mapOf("id" to 6, "chassis" to "KDH201-5012556", "carName" to "Audi A4", "carModelYear" to "2020"),
        mapOf("id" to 7, "chassis" to "KDH201-5012557", "carName" to "Lexus ES", "carModelYear" to "2022"),
        mapOf("id" to 8, "chassis" to "KDH201-5012558", "carName" to "Hyundai Sonata", "carModelYear" to "2021"),
        mapOf("id" to 9, "chassis" to "KDH201-5012559", "carName" to "Kia Optima", "carModelYear" to "2020"),
        mapOf("id" to 10, "chassis" to "KDH201-5012560", "carName" to "Mazda 6", "carModelYear" to "2022")
    )
    
    // Filter by search term
    val filteredPurchases = samplePurchases.filter { purchase ->
        val chassis = purchase["chassis"] as? String ?: ""
        chassis.contains(searchTerm, ignoreCase = true)
    }
    
    console.log("Fallback search found", filteredPurchases.size, "matching purchases")
    displayPurchasesAsCarsAPPEND(filteredPurchases.toTypedArray())
}

// Calculate Freight
fun calculateFreight() {
    val containerPrice = (document.getElementById("containerPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val wcCharge = (document.getElementById("wcCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val fobPrice = (document.getElementById("fobPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val freightPrice = (document.getElementById("freightPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val insurance = (document.getElementById("insurance") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    val total = containerPrice + shippingCharge + wcCharge + inspectionFee + fobPrice + freightPrice + insurance
    
    document.getElementById("freightTotal")?.textContent = "Total: $${total.toString().let { it.substringBefore('.') + '.' + it.substringAfter('.').padEnd(2, '0').take(2) }}"
    (document.getElementById("freightResult") as HTMLElement?)?.style?.display = "block"
}

// Calculate CAF
fun calculateCAF() {
    val containerPrice = (document.getElementById("containerPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val wcCharge = (document.getElementById("wcCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val fobPrice = (document.getElementById("fobPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val freightPrice = (document.getElementById("freightPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val insurance = (document.getElementById("insurance") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    val baseTotal = containerPrice + shippingCharge + wcCharge + inspectionFee + fobPrice + freightPrice + insurance
    val total = baseTotal * 1.15 // 15% markup for CAF
    
    document.getElementById("cafTotal")?.textContent = "Total: $${total.toString().let { it.substringBefore('.') + '.' + it.substringAfter('.').padEnd(2, '0').take(2) }}"
    (document.getElementById("cafResult") as HTMLElement?)?.style?.display = "block"
}

// Calculate FOB
fun calculateFOB() {
    val containerPrice = (document.getElementById("containerPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val wcCharge = (document.getElementById("wcCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val fobPrice = (document.getElementById("fobPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val freightPrice = (document.getElementById("freightPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val insurance = (document.getElementById("insurance") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    val total = containerPrice + shippingCharge + wcCharge + inspectionFee + fobPrice + freightPrice + insurance
    
    document.getElementById("fobTotal")?.textContent = "Total: $${total.toString().let { it.substringBefore('.') + '.' + it.substringAfter('.').padEnd(2, '0').take(2) }}"
    (document.getElementById("fobResult") as HTMLElement?)?.style?.display = "block"
}

// Calculate Pakistan
fun calculatePakistan() {
    val containerPrice = (document.getElementById("containerPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val wcCharge = (document.getElementById("wcCharge") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val fobPrice = (document.getElementById("fobPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val freightPrice = (document.getElementById("freightPrice") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val insurance = (document.getElementById("insurance") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    val baseTotal = containerPrice + shippingCharge + wcCharge + inspectionFee + fobPrice + freightPrice + insurance
    val total = baseTotal * 1.25 // 25% markup for Pakistan
    
    document.getElementById("pakistanTotal")?.textContent = "Total: $${total.toString().let { it.substringBefore('.') + '.' + it.substringAfter('.').padEnd(2, '0').take(2) }}"
    (document.getElementById("pakistanResult") as HTMLElement?)?.style?.display = "block"
}

// Show full preview
fun showFullPreview() {
    js("alert('Full preview functionality will be implemented in the next phase!')")
}

// Show Calculate Freight page
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
        } else if (fobPageSelectedCars.isNotEmpty()) {
            // Fourth: use cars that were passed to FOB page from Car Booking
            console.log("📋 Using cars from FOB page selection:", fobPageSelectedCars.size)
            fobPageSelectedCars
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

// Get selected cars from the car selection table
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
                
                console.log("🔍 Selected car: chassis=${chassisCell?.textContent}, name=${nameCell?.textContent}, year=${yearCell?.textContent}")
                
                if (chassisCell != null && nameCell != null && yearCell != null) {
                    val carObject = js("{}")
                    carObject.chassis = chassisCell.textContent
                    carObject.name = nameCell.textContent
                    carObject.year = yearCell.textContent
                    carObject.price = 0 // Will be populated from API
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

// Create freight calculation HTML page
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

// Setup event listeners for freight calculation page
fun setupFreightCalculationListeners(currentSelectedCars: List<dynamic>) {
    console.log("🔧 Setting up freight calculation listeners...")
    
    // Back to C&F button
    document.getElementById("backToCnfBtn")?.addEventListener("click", { _: Event ->
        // Pass the selected cars back to C&F page
        val selectedCars = cnfPageSelectedCars
        val selectedChassis = if (selectedCars.isNotEmpty()) selectedCars[0].chassis else null
        console.log("🔄 Returning to C&F page with ${selectedCars.size} selected cars")
        showCnfCalculationPage(selectedChassis, selectedCars, cnfPageSelectedCountry)
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

// Generate container sections dynamically
fun generateContainerSections(allSelectedCars: List<dynamic>) {
    val numberOfContainers = (document.getElementById("numberOfContainers") as HTMLInputElement?)?.value?.toIntOrNull() ?: 2
    val totalPerContainerPrice = (document.getElementById("totalPerContainerPrice") as HTMLInputElement?)?.value ?: "¥450,000"
    val containerSections = document.getElementById("containerSections")
    
    if (containerSections == null) return
    
    // Use the provided allSelectedCars
    val availableCars = allSelectedCars.toMutableList()
    
    console.log("🚢 generateContainerSections called with ${allSelectedCars.size} selected cars")
    console.log("🚢 Selected cars:", allSelectedCars)
    
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

// Handle container car selection with cascading dropdown logic
fun handleContainerCarSelection(containerId: String, containerNumber: Int, availableCars: List<dynamic>, allSelectedCars: List<dynamic>) {
    val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
    val selectedChassis = carSelect?.value ?: ""
    
    if (selectedChassis.isEmpty()) return
    
    console.log("🚗 Car selected in container $containerNumber: $selectedChassis")
    
    // Add the car to the container first
    addCarToContainer(containerId, selectedChassis, allSelectedCars, containerNumber, availableCars)
}

// Update subsequent container dropdowns to exclude selected cars
fun updateSubsequentContainerDropdowns(currentContainerNumber: Int, selectedChassis: String, availableCars: List<dynamic>) {
    console.log("🔄 Updating dropdowns for containers after $currentContainerNumber")
    
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
            
            console.log("✅ Updated container $i dropdown with ${carSelect.options.length - 1} available cars")
        }
    }
}

// Get all currently selected cars from all containers
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
    
    console.log("📋 All selected cars in containers: $selectedCars")
    return selectedCars
}

// Add car to container
fun addCarToContainer(containerId: String, chassis: String, allSelectedCars: List<dynamic>, containerNumber: Int? = null, availableCars: List<dynamic>? = null) {
    if (chassis.isEmpty()) return
    
    val car = allSelectedCars.find { it.chassis == chassis }
    if (car == null) {
        console.error("❌ Car with chassis $chassis not found in selected cars list.")
        return
    }
    
    val carList = document.getElementById("${containerId}CarList")
    if (carList == null) return
    
    // Check if car is already in this container
    val existingCar = carList.querySelector("[data-chassis='$chassis']")
    if (existingCar != null) return
    
    console.log("🚢 Adding car $chassis to container $containerId")
    
    // Fetch C&F price for this chassis
    js("fetch('/api/purchases/costs-by-chassis/' + encodeURIComponent(chassis))")
        .then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                console.error("Failed to fetch C&F price for chassis:", chassis)
                js("Promise.resolve({})")
            }
        }
        .then { costData: dynamic ->
            console.log("🚢 C&F data for $chassis:", costData)
            
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
                <button type="button" onclick="removeCarFromContainer('$containerId', '$chassis')" style="padding: 4px 8px; background-color: #dc2626; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Remove</button>
            """
            
            carList.appendChild(carItem)
            
            // Remove car from dropdown
            val carSelect = document.getElementById("${containerId}CarSelect") as HTMLSelectElement?
            val optionToRemove = carSelect?.querySelector("option[value='$chassis']")
            optionToRemove?.remove()
            
            // Recalculate freight allocation
            calculateContainerFreightAllocation(containerId, allSelectedCars)
            
            console.log("✅ Car $chassis added to container $containerId with C&F price ¥${totalCnfPrice.toInt()}")
            
            // Update subsequent container dropdowns to exclude this car (cascading logic)
            if (containerNumber != null && availableCars != null) {
                updateSubsequentContainerDropdowns(containerNumber, chassis, availableCars)
            }
        }
        .catch { error: dynamic ->
            console.error("❌ Error fetching C&F price for $chassis:", error)
            
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
                <button type="button" onclick="removeCarFromContainer('$containerId', '$chassis')" style="padding: 4px 8px; background-color: #dc2626; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">Remove</button>
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

// Remove car from container
fun removeCarFromContainer(containerId: String, chassis: String, allSelectedCars: List<dynamic>) {
    console.log("🗑️ Removing car $chassis from container $containerId")
    
    val carList = document.getElementById("${containerId}CarList")
    val carItem = carList?.querySelector("[data-chassis='$chassis']")
    carItem?.remove()
    
    // Get the container number from containerId (e.g., "container1" -> 1)
    val containerNumber = containerId.replace("container", "").toIntOrNull() ?: 1
    
    // Update all subsequent container dropdowns to include this car back
    updateSubsequentContainerDropdownsAfterRemoval(containerNumber, chassis, allSelectedCars)
    
    // Recalculate freight allocation
    calculateContainerFreightAllocation(containerId, allSelectedCars)
    
    console.log("✅ Car $chassis removed from container $containerId")
}

// Update subsequent container dropdowns after a car is removed
fun updateSubsequentContainerDropdownsAfterRemoval(removedFromContainerNumber: Int, removedChassis: String, allSelectedCars: List<dynamic>) {
    console.log("🔄 Updating dropdowns after removing $removedChassis from container $removedFromContainerNumber")
    
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
            
            console.log("✅ Updated container $i dropdown with ${carSelect.options.length - 1} available cars")
        }
    }
}

// Calculate freight allocation for a container
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
        
        console.log("🚢 Car ${carItem.getAttribute("data-chassis")}: C&F ¥${cnfPrice.toInt()}, Freight ¥${allocatedAmount.toInt()}")
    }
}

// Confirm freight calculation
fun confirmFreightCalculation() {
    console.log("✅ Confirming freight calculation and collecting freight values...")
    
    // Collect freight values from all containers
    val freightValues = mutableMapOf<String, Double>()
    
    // Get all container sections
    val containerSections = document.getElementById("containerSections")
    console.log("🔍 DEBUG: containerSections found:", containerSections)
    if (containerSections != null) {
        val containers = containerSections.querySelectorAll("[id^='container'][id$='CarList']")
        console.log("🔍 DEBUG: Found ${containers.length} containers")
        for (i in 0 until containers.length) {
            val container = containers.item(i) as HTMLElement
            val containerId = container.id
            console.log("🔍 DEBUG: Processing container $i, containerId: $containerId")
            val carList = document.getElementById(containerId)
            console.log("🔍 DEBUG: carList for $containerId:", carList)
            if (carList != null) {
                val carItems = carList.querySelectorAll("[data-chassis]")
                console.log("🔍 DEBUG: Found ${carItems.length} car items in container $containerId")
                for (j in 0 until carItems.length) {
                    val carItem = carItems.item(j) as HTMLElement
                    val chassis = carItem.getAttribute("data-chassis")
                    console.log("🔍 DEBUG: Processing car item $j, chassis: $chassis")
                    console.log("🔍 DEBUG: Car item HTML:", carItem.innerHTML)
                    
                    val freightInput = carItem.querySelector("input[type='text']") as HTMLInputElement?
                    console.log("🔍 DEBUG: Freight input found:", freightInput)
                    console.log("🔍 DEBUG: Freight input value:", freightInput?.value)
                    
                    if (chassis != null && freightInput != null) {
                        val freightValue = freightInput.value.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
                        freightValues[chassis] = freightValue
                        console.log("🚢 Collected freight for $chassis: ¥${freightValue.toInt()}")
                    } else {
                        console.log("❌ DEBUG: Missing chassis or freight input for car item $j")
                    }
                }
            }
        }
    }
    
    // Store freight values globally for C&F page
    globalFreightValues.clear()
    globalFreightValues.putAll(freightValues)
    console.log("📋 Stored freight values:", freightValues)
    console.log("🔍 DEBUG: globalFreightValues after assignment:", globalFreightValues)
    console.log("🔍 DEBUG: globalFreightValues size:", globalFreightValues.size)
    console.log("🔍 DEBUG: globalFreightValues keys:", globalFreightValues.keys)
    
    // Navigate back to C&F Calculation page with freight values
    console.log("🔄 Returning to C&F Calculation page with freight values...")
    showCnfCalculationPage(null, cnfPageSelectedCars, currentSelectedCountry)
}

// Show C&F Calculation Page
fun showCnfCalculationPage(selectedChassis: String? = null, selectedCars: List<dynamic>? = null, selectedCountry: String = "PAKISTAN") {
    console.log("💰 Opening C&F Calculation page...")
    
    // Store the selected cars globally so Calculate Freight can access them
    cnfPageSelectedCars = selectedCars ?: emptyList()
    cnfPageSelectedCountry = selectedCountry // Store the selected country
    console.log("📋 Stored ${cnfPageSelectedCars.size} selected cars for C&F page")
    console.log("🌍 Stored selected country:", selectedCountry)
    
    val cnfPageHTML = createCnfCalculationHTML()
    
    // Replace the main content with C&F calculation page
    val mainContent = document.getElementById("content")
    if (mainContent != null) {
        mainContent.innerHTML = cnfPageHTML
        setupCnfCalculationListeners(selectedChassis, selectedCars)
        
        console.log("✅ C&F Calculation page loaded successfully")
    } else {
        console.error("❌ Main content element not found")
    }
}

// Show modal for "all cars details checked"
fun showAllCarsDetailsCheckedModal() {
    val modalHTML = """
        <div id="allCarsDetailsModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 1000; display: flex; justify-content: center; align-items: center;">
            <div style="background: white; padding: 30px; border-radius: 12px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); max-width: 400px; text-align: center;">
                <div style="margin-bottom: 20px;">
                    <div style="width: 60px; height: 60px; background-color: #fef3c7; border-radius: 50%; display: flex; align-items: center; justify-content: center; margin: 0 auto 15px;">
                        <span style="font-size: 30px;">⚠️</span>
                    </div>
                    <h3 style="margin: 0 0 10px 0; color: #374151; font-size: 20px; font-weight: 600;">All Cars Details Checked</h3>
                    <p style="margin: 0; color: #6b7280; font-size: 14px; line-height: 1.5;">Please check the details of all selected cars before proceeding.</p>
                </div>
                <button id="closeAllCarsDetailsModal" style="padding: 10px 24px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">OK</button>
            </div>
        </div>
    """
    
    // Add modal to body
    document.body?.insertAdjacentHTML("beforeend", modalHTML)
    
    // Add close event listener
    document.getElementById("closeAllCarsDetailsModal")?.addEventListener("click", { _: Event ->
        document.getElementById("allCarsDetailsModal")?.remove()
    })
    
    // Close modal when clicking outside
    document.getElementById("allCarsDetailsModal")?.addEventListener("click", { event: Event ->
        if (event.target == document.getElementById("allCarsDetailsModal")) {
            document.getElementById("allCarsDetailsModal")?.remove()
        }
    })
}





// Show FOB Calculation Page
fun showFobCalculationPage(selectedChassis: String? = null, selectedCars: List<dynamic>? = null) {
    console.log("🚢 Opening FOB Calculation page...")
    
    // Store the selected cars globally so Calculate Freight can access them
    fobPageSelectedCars = selectedCars ?: emptyList()
    console.log("📋 Stored ${fobPageSelectedCars.size} selected cars for FOB page")
    
    val fobPageHTML = createFobCalculationHTML()
    
    // Replace the main content with FOB calculation page
    val mainContent = document.getElementById("content")
    if (mainContent != null) {
        mainContent.innerHTML = fobPageHTML
        setupFobCalculationListeners(selectedChassis, selectedCars)
        
        console.log("✅ FOB Calculation page loaded successfully - FREIGHT FIELD REMOVED - CACHE BUST 1736383002")
    } else {
        console.error("❌ Main content element not found")
    }
}

// Create C&F Calculation HTML page
fun createCnfCalculationHTML(): String {
    return """
        <div style="padding: 20px; background-color: #f9fafb; min-height: 100vh;">
            <!-- Back Button -->
            <div style="margin-bottom: 20px;">
                <button id="backToBookingBtn" style="padding: 8px 16px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px;">← Back to Car Booking</button>
            </div>
            
            <!-- C&F Calculation Container -->
            <div style="background-color: white; border: 2px solid #8b5cf6; border-radius: 12px; padding: 30px; max-width: 800px; margin: 0 auto; box-shadow: 0 4px 20px rgba(0,0,0,0.1);">
                
                <!-- Header -->
                <div style="text-align: center; margin-bottom: 30px;">
                    <h1 style="color: #1f2937; font-size: 24px; font-weight: bold; margin: 0;">C&F CALCULATION</h1>
                </div>
                
                <!-- Car Selection -->
                <div style="margin-bottom: 20px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 16px;">SELECT CAR CHASSIS :</label>
                    <select id="chassisSelect" style="width: 100%; padding: 12px; border: 2px solid #d1d5db; border-radius: 6px; font-size: 14px; background-color: white;">
                        <option value="">Select a car chassis...</option>
                    </select>
                </div>
                
                <!-- Cost Fields -->
                <div style="background-color: #f8fafc; padding: 20px; border-radius: 8px; margin-bottom: 20px;">
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                        <!-- Left Column -->
                        <div>
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">CAR PRICE (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="carPrice" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">RIXO PRICE (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="rixoPrice" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">FREIGHT (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="freight" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">REPAIR FEE (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="repairFee" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">PROFIT (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="profit" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                        </div>
                        
                        <!-- Right Column -->
                        <div>
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">AUCTION FEE (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="auctionFee" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">SHIPPING CHARGE (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="shippingCharge" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">INSPECTION FEE (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="inspectionFee" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                            
                            <div style="margin-bottom: 15px;">
                                <label style="display: block; margin-bottom: 5px; font-weight: 500; color: #374151;">MSC. CHARGES (¥):</label>
                                <div style="display: flex; align-items: center;">
                                    <span style="margin-right: 5px; font-weight: bold;">¥</span>
                                    <input type="text" id="mscCharges" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                                </div>
                            </div>
                        </div>
                    </div>
                    
                    <!-- Package Option -->
                    <div style="margin-top: 20px; padding: 15px; background-color: #f3f4f6; border-radius: 8px; border: 1px solid #d1d5db;">
                        <label style="display: flex; align-items: center; cursor: pointer; font-weight: 500; color: #374151;">
                            <input type="checkbox" id="packageCheckbox" style="margin-right: 10px; transform: scale(1.2);">
                            <span style="font-size: 16px;">PACKAGE</span>
                        </label>
                    </div>
                    
                    <!-- Package Price Field (Hidden by default) -->
                    <div id="packagePriceSection" style="display: none; margin-top: 20px; padding: 15px; background-color: #fef3c7; border-radius: 8px; border: 1px solid #f59e0b;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 16px;">PACKAGE PRICE (¥):</label>
                        <div style="display: flex; align-items: center;">
                            <span style="margin-right: 5px; font-weight: bold;">¥</span>
                            <input type="text" id="packagePrice" value="0" style="flex: 1; padding: 8px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                        </div>
                    </div>
                    
                    <!-- Total Calculations -->
                    <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 20px; padding-top: 20px; border-top: 2px solid #e5e7eb;">
                        <!-- Total C&F Price -->
                        <div style="text-align: center;">
                            <label style="display: block; margin-bottom: 10px; font-weight: 600; color: #374151; font-size: 16px;">TOTAL C&F PRICE (¥):</label>
                            <div id="totalCnfPrice" style="font-size: 20px; font-weight: bold; color: #059669; background-color: #f0fdf4; padding: 15px; border-radius: 8px; border: 2px solid #22c55e;">0</div>
                        </div>
                        
                        <!-- Total Cost (Hidden by default) -->
                        <div id="totalCostSection" style="text-align: center; display: none;">
                            <label style="display: block; margin-bottom: 10px; font-weight: 600; color: #374151; font-size: 16px;">TOTAL COST (¥):</label>
                            <div id="totalCost" style="font-size: 20px; font-weight: bold; color: #dc2626; background-color: #fef2f2; padding: 15px; border-radius: 8px; border: 2px solid #fca5a5;">0</div>
                        </div>
                    </div>
                    
                    <!-- Action Buttons -->
                    <div style="text-align: center; margin-top: 20px;">
                        <button id="saveCarCostsBtn" style="padding: 12px 32px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; margin-right: 15px;">SAVE</button>
                        <button id="confirmCarCostsBtn" style="padding: 12px 32px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; margin-right: 15px;">CONFIRM</button>
                        <button id="calculateFreightBtn" style="padding: 12px 32px; background-color: #8b5cf6; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">CALCULATE FREIGHT</button>
                    </div>
                </div>
            </div>
        </div>
    """
}

// Create FOB Calculation HTML page
fun createFobCalculationHTML(): String {
    return """
        <div style="padding: 20px; background-color: #f9fafb; min-height: 100vh;">
            <!-- Back Button -->
            <div style="margin-bottom: 20px;">
                <button id="backToBookingBtnFob" style="padding: 8px 16px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px;">← Back to Car Booking</button>
            </div>
            
            <!-- Main Container -->
            <div style="background: linear-gradient(135deg, #ffffff 0%, #f8fafc 100%); border: 2px solid #3b82f6; border-radius: 12px; padding: 30px; box-shadow: 0 10px 25px rgba(0,0,0,0.1);">
                <h3 style="margin: 0 0 25px 0; color: #374151; font-size: 20px; font-weight: 600; text-align: center;">FOB CALCULATION</h3>
                
                <!-- Chassis Selection -->
                <div style="margin-bottom: 25px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">SELECT CAR CHASSIS :</label>
                    <select id="chassisSelectFob" style="width: 100%; padding: 12px; border: 1px solid #d1d5db; border-radius: 8px; font-size: 14px; background-color: white;">
                        <option value="">Select a car chassis...</option>
                    </select>
                </div>
                
                <!-- Cost Fields Grid -->
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 25px;">
                    <!-- Left Column -->
                    <div style="display: flex; flex-direction: column; gap: 15px;">
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">CAR PRICE (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="carPriceFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                        
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">RIXO PRICE (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="rixoPriceFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                        
                        
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">REPAIR FEE (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="repairFeeFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                        
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">PROFIT (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="profitFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                    </div>
                    
                    <!-- Right Column -->
                    <div style="display: flex; flex-direction: column; gap: 15px;">
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">AUCTION FEE (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="auctionFeeFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                        
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">SHIPPING CHARGE (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="shippingChargeFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                        
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">INSPECTION FEE (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="inspectionFeeFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                        
                        <div>
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">MSC. CHARGES (¥):</label>
                            <div style="display: flex; align-items: center; border: 1px solid #d1d5db; border-radius: 6px; overflow: hidden;">
                                <span style="padding: 10px; background-color: #f3f4f6; color: #6b7280; font-weight: 600;">¥</span>
                                <input type="text" id="mscChargesFob" placeholder="0" style="flex: 1; padding: 10px; border: none; font-size: 14px; outline: none;">
                            </div>
                        </div>
                    </div>
                </div>
                
                <!-- Total FOB Price -->
                <div style="text-align: center; margin-top: 20px; padding-top: 20px; border-top: 2px solid #e5e7eb;">
                    <label style="display: block; margin-bottom: 10px; font-weight: 600; color: #374151; font-size: 18px;">TOTAL FOB PRICE (¥):</label>
                    <div id="totalFobPrice" style="font-size: 24px; font-weight: bold; color: #3b82f6; background-color: #eff6ff; padding: 15px; border-radius: 8px; border: 2px solid #3b82f6;">0</div>
                </div>
                
                <!-- Action Buttons -->
                <div style="text-align: center; margin-top: 20px;">
                    <button id="saveCarCostsBtnFob" style="padding: 12px 32px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500; margin-right: 15px;">SAVE</button>
                    <button id="confirmCarCostsBtnFob" style="padding: 12px 32px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">CONFIRM</button>
                </div>
            </div>
        </div>
    """
}

// Setup event listeners for C&F calculation page
fun setupCnfCalculationListeners(selectedChassis: String? = null, selectedCars: List<dynamic>? = null) {
    console.log("🔧 Setting up C&F calculation listeners...")
    
    // Back to booking button
    document.getElementById("backToBookingBtn")?.addEventListener("click", { _: Event ->
        // Save state before navigating back to Car Booking page
        saveCarBookingState()
        showCarBookingPage()
    })
    
    // Load chassis dropdown with available cars
    loadChassisDropdownForCnf(selectedCars)
    
    // If a specific chassis is selected, set it in the dropdown
    if (!selectedChassis.isNullOrEmpty()) {
        val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
        chassisSelect?.value = selectedChassis
        loadCarCostDetails()
    }
    
    // Chassis selection dropdown - load car cost details
    document.getElementById("chassisSelect")?.addEventListener("change", { _: Event ->
        loadCarCostDetails()
    })
    
    // Save button - save calculated Total C&F Price
    document.getElementById("saveCarCostsBtn")?.addEventListener("click", { _: Event ->
        saveTotalCnfPrice()
    })
    
    // Confirm button - save data and then navigate
    document.getElementById("confirmCarCostsBtn")?.addEventListener("click", { _: Event ->
        console.log("🔘 CONFIRM button clicked!")
        // First, save the calculated C&F cost details to database, THEN navigate
        console.log("💾 Saving C&F cost details before navigation...")
        saveCarCostDetailsAndNavigate()
    })
    
    // Calculate freight button - navigate to freight calculation page
    document.getElementById("calculateFreightBtn")?.addEventListener("click", { _: Event ->
        console.log("🚢 Navigating from C&F page to Calculate Freight page...")
        showCalculateFreightPage()
    })
    
    // Add input validation to all cost fields
    addCostFieldValidation()
    
    // Add event listeners for all cost input fields to calculate total in real-time
    val costFieldIds = listOf("carPrice", "auctionFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    costFieldIds.forEach { fieldId ->
        document.getElementById(fieldId)?.addEventListener("input", { _: Event ->
            calculateCnfTotal()
        })
        document.getElementById(fieldId)?.addEventListener("change", { _: Event ->
            calculateCnfTotal()
        })
    }
    
    // Package checkbox event listener
    document.getElementById("packageCheckbox")?.addEventListener("change", { _: Event ->
        togglePackageFields()
    })
    
    // Package price input event listener
    document.getElementById("packagePrice")?.addEventListener("input", { _: Event ->
        calculateCnfTotal()
    })
    
    console.log("✅ C&F calculation listeners setup complete")
}

// Setup event listeners for FOB calculation page
fun setupFobCalculationListeners(selectedChassis: String? = null, selectedCars: List<dynamic>? = null) {
    console.log("🔧 Setting up FOB calculation listeners...")
    
    // Back to booking button
    document.getElementById("backToBookingBtnFob")?.addEventListener("click", { _: Event ->
        // Save state before navigating back to Car Booking page
        saveCarBookingState()
        showCarBookingPage()
    })
    
    // Load chassis dropdown with available cars
    loadChassisDropdownForFob(selectedCars)
    
    // If a specific chassis is selected, set it in the dropdown
    if (!selectedChassis.isNullOrEmpty()) {
        val chassisSelect = document.getElementById("chassisSelectFob") as HTMLSelectElement?
        chassisSelect?.value = selectedChassis
        loadCarCostDetailsFob()
    }
    
    // Chassis selection dropdown - load car cost details
    document.getElementById("chassisSelectFob")?.addEventListener("change", { _: Event ->
        loadCarCostDetailsFob()
    })
    
    // Save button - save calculated Total FOB Price
    document.getElementById("saveCarCostsBtnFob")?.addEventListener("click", { _: Event ->
        console.log("🔘 SAVE button clicked - NEW FUNCTIONALITY - CACHE BUST 1736383003")
        saveTotalFobPrice()
    })
    
    // Confirm button - navigate to C&F page
    document.getElementById("confirmCarCostsBtnFob")?.addEventListener("click", { _: Event ->
        console.log("🔘 CONFIRM button clicked - NEW FUNCTIONALITY - CACHE BUST 1736383003")
        navigateToCnfPage()
    })
    
    // Add input validation to all cost fields
    addCostFieldValidationFob()
    
    console.log("✅ FOB calculation listeners setup complete - FREIGHT FIELD REMOVED - CACHE BUST 1736383002")
}

// Load chassis dropdown for C&F page
fun loadChassisDropdownForCnf(selectedCars: List<dynamic>? = null) {
    console.log("🔄 Loading chassis dropdown for C&F page...")
    
    // Use provided selectedCars or stored cnfPageSelectedCars (fallback)
    val cars = selectedCars ?: cnfPageSelectedCars
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    
    if (chassisSelect != null) {
        // Clear existing options except the first one
        chassisSelect.innerHTML = "<option value=\"\">Select a car chassis...</option>"
        
        // Add selected cars to dropdown
        cars.forEach { car ->
            val option = document.createElement("option") as HTMLOptionElement
            option.value = car.chassis
            option.textContent = "${car.chassis} - ${car.name} (${car.year})"
            chassisSelect.appendChild(option)
        }
        
        console.log("✅ Added ${cars.size} cars to C&F chassis dropdown")
    } else {
        console.error("❌ Chassis select element not found!")
    }
}

// Calculate and update C&F total display
// Toggle package fields visibility
fun togglePackageFields() {
    val packageCheckbox = document.getElementById("packageCheckbox") as HTMLInputElement?
    val packagePriceSection = document.getElementById("packagePriceSection")
    val totalCostSection = document.getElementById("totalCostSection")
    
    if (packageCheckbox?.checked == true) {
        // Show package fields
        packagePriceSection?.setAttribute("style", "display: block; margin-top: 20px; padding: 15px; background-color: #fef3c7; border-radius: 8px; border: 1px solid #f59e0b;")
        totalCostSection?.setAttribute("style", "text-align: center; display: block;")
        console.log("📦 Package fields shown")
    } else {
        // Hide package fields
        packagePriceSection?.setAttribute("style", "display: none;")
        totalCostSection?.setAttribute("style", "text-align: center; display: none;")
        console.log("📦 Package fields hidden")
    }
    
    // Recalculate totals
    calculateCnfTotal()
}

fun calculateCnfTotal() {
    // Get current cost values from the form
    val carPrice = (document.getElementById("carPrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val auctionFee = (document.getElementById("auctionFee") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val rixoPrice = (document.getElementById("rixoPrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val freight = (document.getElementById("freight") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val repairFee = (document.getElementById("repairFee") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val mscCharges = (document.getElementById("mscCharges") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val profit = (document.getElementById("profit") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    
    // Check if package checkbox is checked
    val packageCheckbox = document.getElementById("packageCheckbox") as HTMLInputElement?
    val isPackageChecked = packageCheckbox?.checked == true
    
    if (isPackageChecked) {
        // Package mode: Total C&F = Car Price + Package Price
        val packagePrice = (document.getElementById("packagePrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
        val totalCnfPrice = carPrice + packagePrice
        
        // Total Cost = sum of all fees
        val totalCost = auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + mscCharges + profit
        
        // Update displays
        document.getElementById("totalCnfPrice")?.textContent = "¥${totalCnfPrice.toInt()}"
        document.getElementById("totalCost")?.textContent = "¥${totalCost.toInt()}"
        
        console.log("📦 Package mode - Total C&F: $totalCnfPrice, Total Cost: $totalCost")
    } else {
        // Normal mode: Total C&F = Car Price + All Fees (current behavior)
        val totalCnfPrice = carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + mscCharges + profit
        
        // Update display
        document.getElementById("totalCnfPrice")?.textContent = "¥${totalCnfPrice.toInt()}"
        
        console.log("💰 Normal mode - Total C&F: $totalCnfPrice")
    }
}

// Confirm car cost details - save to database and add to confirmed cars list
fun confirmCarCostDetails() {
    console.log("✅ Confirming car cost details...")
    
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement?
    if (chassisSelect == null || chassisSelect.value.isEmpty()) {
        window.alert("Please select a car chassis first!")
        return
    }
    
    val selectedChassis = chassisSelect.value
    console.log("🔍 Confirming costs for chassis: $selectedChassis")
    
    // Get current cost values from the form
    val carPrice = (document.getElementById("carPrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val auctionFee = (document.getElementById("auctionFee") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val rixoPrice = (document.getElementById("rixoPrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val shippingCharge = (document.getElementById("shippingCharge") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val freight = (document.getElementById("freight") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val repairFee = (document.getElementById("repairFee") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val mscCharges = (document.getElementById("mscCharges") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    val profit = (document.getElementById("profit") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    
    // Calculate total C&F price
    val totalCnfPrice = carPrice + auctionFee + rixoPrice + shippingCharge + freight + inspectionFee + repairFee + mscCharges + profit
    
    // Update the display
    document.getElementById("totalCnfPrice")?.textContent = "¥${totalCnfPrice.toInt()}"
    
    console.log("💰 Total C&F Price calculated: $totalCnfPrice")
    
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
    costData.totalCnfPrice = totalCnfPrice
    
    // Save to database using existing saveCarCostDetails function
    saveCarCostDetails()
    
    // Add to confirmed cars list
    val confirmedCar = js("{}")
    confirmedCar.chassis = selectedChassis
    
    // Parse car name and year from dropdown text
    val optionText = chassisSelect.options[chassisSelect.selectedIndex]?.textContent ?: ""
    val nameAndYear = optionText.split(" - ")
    if (nameAndYear.size >= 2) {
        val nameAndYearPart = nameAndYear[1].split(" (")
        confirmedCar.name = nameAndYearPart[0]
        if (nameAndYearPart.size >= 2) {
            confirmedCar.year = nameAndYearPart[1].replace(")", "")
        } else {
            confirmedCar.year = "Unknown"
        }
    } else {
        confirmedCar.name = "Unknown"
        confirmedCar.year = "Unknown"
    }
    
    confirmedCar.totalCnfPrice = totalCnfPrice
    
    // Check if car already exists in confirmed list and update it
    val existingIndex = cnfConfirmedCars.indexOfFirst { it.chassis == selectedChassis }
    if (existingIndex >= 0) {
        cnfConfirmedCars[existingIndex] = confirmedCar
        console.log("🔄 Updated existing confirmed car: $selectedChassis")
    } else {
        cnfConfirmedCars.add(confirmedCar)
        console.log("➕ Added new confirmed car: $selectedChassis")
    }
    
    window.alert("✅ Car costs confirmed and saved!\nChassis: $selectedChassis\nTotal C&F Price: ¥${totalCnfPrice.toInt()}")
}

// ==================== FOB HELPER FUNCTIONS ====================

// Load chassis dropdown for FOB page
fun loadChassisDropdownForFob(selectedCars: List<dynamic>? = null) {
    console.log("🔄 Loading chassis dropdown for FOB page...")
    
    // Use provided selectedCars or stored fobPageSelectedCars (fallback)
    val cars = selectedCars ?: fobPageSelectedCars
    
    val chassisSelect = document.getElementById("chassisSelectFob") as HTMLSelectElement?
    if (chassisSelect == null) {
        console.error("❌ Chassis select element for FOB not found!")
        return
    }
    
    chassisSelect.innerHTML = "<option value=\"\">Select a car chassis...</option>" // Clear existing options
    
    if (cars.isNotEmpty()) {
        cars.forEach { car ->
            val chassis = car.chassis as? String
            val name = car.name as? String
            val year = car.year as? String
            if (chassis != null) {
                val option = document.createElement("option") as HTMLOptionElement
                option.setAttribute("value", chassis)
                option.textContent = "$chassis - $name ($year)"
                chassisSelect.appendChild(option)
            }
        }
        console.log("✅ FOB chassis dropdown populated with ${cars.size} selected cars.")
    } else {
        console.warn("⚠️ No cars passed to FOB page to populate dropdown.")
    }
}

// Load car cost details for FOB page
fun loadCarCostDetailsFob() {
    val chassisSelect = document.getElementById("chassisSelectFob") as HTMLSelectElement?
    if (chassisSelect == null || chassisSelect.value.isEmpty()) {
        clearCostFieldsFob()
        return
    }
    
    val selectedChassis = chassisSelect.value
    console.log("🔍 Loading FOB cost details for chassis:", selectedChassis)
    
    js("fetch('/api/purchases/costs-by-chassis/' + encodeURIComponent(selectedChassis))")
        .then { response: dynamic ->
            if (!response.ok) {
                throw Exception("HTTP error! status: ${response.status}")
            }
            response.json()
        }
        .then { costData: dynamic ->
            console.log("✅ FOB Cost details fetched:", costData)
            populateCostFieldsFob(costData)
            calculateTotalFobPrice()
        }
        .catch { error: dynamic ->
            console.error("❌ Error fetching FOB cost details:", error)
            js("alert('Error fetching FOB cost details: ' + error.message)")
            clearCostFieldsFob()
        }
}

// Populate cost fields for FOB page
fun populateCostFieldsFob(purchase: dynamic) {
    console.log("Filling FOB cost fields with data:", purchase)
    
    // Helper function to safely convert value to string
    fun getValueAsString(value: dynamic): String {
        return when (value) {
            is String -> value.replace(",", "")
            is Number -> value.toString()
            else -> "0"
        }
    }
    
    (document.getElementById("carPriceFob") as? HTMLInputElement)?.value = getValueAsString(purchase.carPrice)
    (document.getElementById("auctionFeeFob") as? HTMLInputElement)?.value = getValueAsString(purchase.auctionFee)
    (document.getElementById("rixoPriceFob") as? HTMLInputElement)?.value = getValueAsString(purchase.rixoPrice)
    (document.getElementById("shippingChargeFob") as? HTMLInputElement)?.value = getValueAsString(purchase.shippingCharge)
    (document.getElementById("inspectionFeeFob") as? HTMLInputElement)?.value = getValueAsString(purchase.inspectionFee)
    (document.getElementById("repairFeeFob") as? HTMLInputElement)?.value = getValueAsString(purchase.repairFee)
    (document.getElementById("mscChargesFob") as? HTMLInputElement)?.value = getValueAsString(purchase.mscCharges)
    (document.getElementById("profitFob") as? HTMLInputElement)?.value = getValueAsString(purchase.profit)
    
    console.log("✅ FOB fields populated successfully")
    calculateTotalFobPrice()
}

// Clear cost fields for FOB page
fun clearCostFieldsFob() {
    console.log("🧹 Clearing FOB cost fields...")
    (document.getElementById("carPriceFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("auctionFeeFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("rixoPriceFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("shippingChargeFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("inspectionFeeFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("repairFeeFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("mscChargesFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("profitFob") as? HTMLInputElement)?.value = "0"
    (document.getElementById("totalFobPrice") as? HTMLElement)?.textContent = "¥0"
}

// Calculate total FOB price
fun calculateTotalFobPrice() {
    val carPrice = parseCurrency((document.getElementById("carPriceFob") as? HTMLInputElement)?.value ?: "0")
    val auctionFee = parseCurrency((document.getElementById("auctionFeeFob") as? HTMLInputElement)?.value ?: "0")
    val rixoPrice = parseCurrency((document.getElementById("rixoPriceFob") as? HTMLInputElement)?.value ?: "0")
    val shippingCharge = parseCurrency((document.getElementById("shippingChargeFob") as? HTMLInputElement)?.value ?: "0")
    val inspectionFee = parseCurrency((document.getElementById("inspectionFeeFob") as? HTMLInputElement)?.value ?: "0")
    val repairFee = parseCurrency((document.getElementById("repairFeeFob") as? HTMLInputElement)?.value ?: "0")
    val mscCharges = parseCurrency((document.getElementById("mscChargesFob") as? HTMLInputElement)?.value ?: "0")
    val profit = parseCurrency((document.getElementById("profitFob") as? HTMLInputElement)?.value ?: "0")
    
    val total = carPrice + auctionFee + rixoPrice + shippingCharge + inspectionFee + repairFee + mscCharges + profit
    (document.getElementById("totalFobPrice") as? HTMLElement)?.textContent = "¥${total.toInt()}"
}

// Save FOB cost details - FUNCTIONALITY REMOVED
fun saveFobCostDetails() {
    console.log("🔘 saveFobCostDetails called - functionality removed - CACHE BUST 1736383001")
    // Functionality removed - no longer saves to database
}

// Save calculated Total FOB Price for individual car
fun saveTotalFobPrice() {
    console.log("🔘 saveTotalFobPrice called - NEW FUNCTIONALITY - CACHE BUST 1736383003")
    
    val chassisSelect = document.getElementById("chassisSelectFob") as HTMLSelectElement
    val selectedChassis = chassisSelect.value

    if (selectedChassis.isEmpty()) {
        js("alert('Please select a car chassis first')")
        return
    }

    console.log("💾 Saving Total FOB Price for chassis:", selectedChassis)

    // Get current Total FOB Price from the display
    val totalFobPriceElement = document.getElementById("totalFobPrice")
    val totalFobPriceText = totalFobPriceElement?.textContent ?: "¥0"

    // Extract numeric value from "¥26900" format
    val totalFobPrice = totalFobPriceText.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0

    console.log("📊 Total FOB Price to save:", totalFobPrice)

    // Create cost data object
    val costData = js("{}")
    costData.chassis = selectedChassis
    costData.totalCnfPrice = totalFobPrice  // Save FOB price to total_cnf_price column

    console.log("📊 Cost data to save:", costData)

    // Call backend API to save FOB price to total_cnf_price column
    js("fetch('/api/purchases/save-total-cnf', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(costData) })")
        .then { response: dynamic ->
            console.log("Save Total FOB API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                throw js("Error('Failed to save total FOB price')")
            }
        }
        .then { result: dynamic ->
            console.log("✅ Total FOB price saved successfully:", result)
            js("alert('Total FOB Price saved successfully for chassis: ' + selectedChassis)")
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving total FOB price:", error)
            js("alert('Error saving total FOB price: ' + error.message)")
        }
}

// Navigate to C&F page from FOB page
fun navigateToCnfPage() {
    console.log("🔘 navigateToCnfPage called - Navigating to BOOKING DETAILS page")
    
    // Navigate back to BOOKING DETAILS page
    showCarBookingPage()
    console.log("✅ Navigated to BOOKING DETAILS page")
}

// Confirm FOB cost details - FUNCTIONALITY REMOVED
fun confirmFobCostDetails() {
    console.log("🔘 confirmFobCostDetails called - functionality removed - CACHE BUST 1736383001")
    // Functionality removed - no longer saves to database or navigates
}

// Confirm FOB PDF generation - FUNCTIONALITY REMOVED
fun confirmFobPdfGeneration() {
    console.log("🔘 confirmFobPdfGeneration called - functionality removed - CACHE BUST 1736383001")
    // Functionality removed - no longer generates PDF
}

// Add cost field validation for FOB page
fun addCostFieldValidationFob() {
    val costFields = listOf(
        "carPriceFob", "auctionFeeFob", "rixoPriceFob", "shippingChargeFob",
        "inspectionFeeFob", "repairFeeFob", "mscChargesFob", "profitFob"
    )
    
    costFields.forEach { fieldId ->
        val field = document.getElementById(fieldId) as? HTMLInputElement
        if (field != null) {
            field.addEventListener("input", { _: Event ->
                calculateTotalFobPrice()
            })
        }
    }
}

// Update chassis dropdown with selected cars
fun updateChassisDropdown() {
    console.log("🔄 Updating chassis dropdown...")
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement
    val selectedChassis = mutableListOf<String>()
    
    // Get all checked cars from the table
    val checkboxes = document.querySelectorAll("#carSelectionTableBody input[type='checkbox']:checked")
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        val row = checkbox.closest("tr") as HTMLElement
        val chassisCell = row.querySelector("td:nth-child(3)") // 3rd column is chassis
        val chassisNumber = chassisCell?.textContent?.trim()
        if (!chassisNumber.isNullOrEmpty()) {
            selectedChassis.add(chassisNumber)
        }
    }
    
    // Clear and repopulate dropdown
    chassisSelect.innerHTML = ""
    chassisSelect.add(js("new Option('Select a car chassis...', '')"))
    
    for (chassis in selectedChassis) {
        val option = js("new Option(chassis, chassis)")
        chassisSelect.add(option)
    }
    
    console.log("✅ Chassis dropdown updated with", selectedChassis.size, "selected cars")
}

// Currency formatting functions
fun formatCurrency(amount: Double): String {
    return amount.toInt().toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
}

fun parseCurrency(currencyString: String): Double {
    // Remove commas and any currency symbols, then parse as double
    val cleanString = currencyString.replace(",", "").replace("¥", "").replace("Â¥", "").trim()
    return cleanString.toDoubleOrNull() ?: 0.0
}

// Input validation and formatting
fun validateAndFormatCurrencyInput(field: HTMLInputElement) {
    val currentValue = field.value
    val numericValue = currentValue.toDoubleOrNull() ?: 0.0
    
    // Validate: only allow positive numbers
    if (numericValue < 0) {
        field.value = "0"
    } else {
        field.value = numericValue.toInt().toString()
    }
    
    // Recalculate total after any change
    calculateTotalCnfPrice()
}

// Add input validation to all cost fields
fun addCostFieldValidation() {
    val costFields = listOf("carPrice", "auctionFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    
    for (fieldId in costFields) {
        val field = document.getElementById(fieldId) as HTMLInputElement
        if (field != null) {
            // Add event listeners for real-time validation
            field.addEventListener("blur", { _: Event ->
                validateAndFormatCurrencyInput(field)
            })
            
            field.addEventListener("input", { _: Event ->
                // Allow typing but validate on blur
                val currentValue = field.value
                // Only allow numbers (no currency symbols or commas)
                if (!currentValue.matches(Regex("^[\\d]*$"))) {
                    field.value = currentValue.replace(Regex("[^\\d]"), "")
                }
            })
        }
    }
}

// Load car cost details when chassis is selected
fun loadCarCostDetails() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement
    val selectedChassis = chassisSelect.value
    
    if (selectedChassis.isEmpty()) {
        clearCostFields()
        return
    }
    
    console.log("📋 Loading cost details for chassis:", selectedChassis)
    
    // Try API call first, fallback to sample data if it fails
    js("fetch('/api/purchases/costs-by-chassis/' + encodeURIComponent(selectedChassis))")
        .then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                throw js("new Error('Failed to load cost details - Status: ' + response.status)")
            }
        }
        .then { costData: dynamic ->
            console.log("✅ Cost data loaded from API:", costData)
            if (costData && costData.chassis) {
                console.log("✅ Cost details extracted:", costData)
                populateCostFields(costData)
            } else {
                throw js("new Error('No cost data found for chassis: ' + selectedChassis)")
            }
        }
        .catch { error: dynamic ->
            console.error("❌ Error loading cost details from API, using fallback:", error)
            loadCarCostDetailsFallback(selectedChassis)
        }
}

// Populate cost fields from API data
fun populateCostFields(costData: dynamic) {
    console.log("🔄 Populating cost fields with API data")
    
    // Get current selected chassis to check for freight value
    val selectedChassis = (document.getElementById("chassisSelect") as HTMLSelectElement?)?.value ?: ""
    console.log("🔍 DEBUG: selectedChassis:", selectedChassis)
    console.log("🔍 DEBUG: globalFreightValues:", globalFreightValues)
    console.log("🔍 DEBUG: globalFreightValues size:", globalFreightValues.size)
    console.log("🔍 DEBUG: globalFreightValues contains key $selectedChassis:", globalFreightValues.containsKey(selectedChassis))
    
    console.log("🔍 DEBUG: Value from globalFreightValues[selectedChassis]:", globalFreightValues[selectedChassis])
    val freightValue = if (selectedChassis.isNotEmpty() && globalFreightValues.containsKey(selectedChassis)) {
        globalFreightValues[selectedChassis] ?: 0.0
    } else {
        0.0 // Freight always starts at 0, not from database
    }
    
    console.log("🚢 Using freight value for $selectedChassis: ¥${freightValue.toInt()}")
    
    val fieldMappings = mapOf(
        "carPrice" to costData.carPrice,
        "auctionFee" to costData.auctionFee,
        "rixoPrice" to costData.rixoPrice,
        "shippingCharge" to costData.shippingCharge,
        "freight" to freightValue, // Use freight value from Calculate Freight page
        "inspectionFee" to costData.inspectionFee,
        "repairFee" to costData.repairFee,
        "mscCharges" to costData.mscCharges,
        "profit" to costData.profit
    )
    
    // Populate cost fields with plain numbers (¥ symbol is displayed in HTML)
    for ((fieldId, value) in fieldMappings) {
        val field = document.getElementById(fieldId) as HTMLInputElement
        val numericValue = value?.toString()?.toDoubleOrNull() ?: 0.0
        val plainValue = numericValue.toInt().toString()
        console.log("🔍 Field:", fieldId, "Raw value:", value, "Numeric:", numericValue, "Plain value:", plainValue)
        field.value = plainValue
    }
    
    // Calculate total using the new function that handles package checkbox
    calculateCnfTotal()
    
    console.log("✅ Cost fields populated from API data with currency formatting")
}

// Fallback function to load car cost details (will be replaced with API call)
fun loadCarCostDetailsFallback(chassis: String) {
    console.log("🔄 Loading fallback cost details for:", chassis)
    
    // Sample cost data - will be replaced with API call
    val costData = mapOf(
        "carPrice" to 300000.0,
        "auctionFee" to 14500.0, 
        "rixoPrice" to 12000.0,
        "shippingCharge" to 20000.0,
        "freight" to 0.0, // Freight always starts at 0, not from database
        "inspectionFee" to 0.0,
        "repairFee" to 0.0,
        "mscCharges" to 0.0,
        "profit" to 20000.0
    )
    
    // Populate cost fields with currency formatting
    for ((fieldId, value) in costData) {
        val field = document.getElementById(fieldId) as HTMLInputElement
        field.value = formatCurrency(value)
    }
    
    // Calculate total using the new function that handles package checkbox
    calculateCnfTotal()
    
    console.log("✅ Cost details loaded for chassis:", chassis)
}

// Clear all cost fields
fun clearCostFields() {
    val costFields = listOf("carPrice", "auctionFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    for (fieldId in costFields) {
        val field = document.getElementById(fieldId) as HTMLInputElement
        field.value = "0"
    }
    calculateCnfTotal()
}

// Calculate total C&F price
fun calculateTotalCnfPrice() {
    val costFields = listOf("carPrice", "auctionFee", "rixoPrice", "shippingCharge", "freight", "inspectionFee", "repairFee", "mscCharges", "profit")
    var total = 0.0
    
    for (fieldId in costFields) {
        val field = document.getElementById(fieldId) as HTMLInputElement
        val value = field.value.toDoubleOrNull() ?: 0.0
        total += value
    }
    
    val totalElement = document.getElementById("totalCnfPrice")
    totalElement?.textContent = "¥${total.toInt()}"
    
    console.log("💰 Total C&F Price calculated:", total)
}

// Save car cost details
fun saveCarCostDetails() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement
    val selectedChassis = chassisSelect.value
    
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
    val freight = (document.getElementById("freight") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val repairFee = (document.getElementById("repairFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val mscCharges = (document.getElementById("mscCharges") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val profit = (document.getElementById("profit") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    // Check if package checkbox is checked and get package price
    val packageCheckbox = document.getElementById("packageCheckbox") as HTMLInputElement?
    val isPackageChecked = packageCheckbox?.checked == true
    val packagePrice = if (isPackageChecked) {
        (document.getElementById("packagePrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    } else {
        0.0
    }
    
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
    costData.packagePrice = packagePrice
    costData.isPackageMode = isPackageChecked
    
    console.log("📊 Cost data to save:", costData)
    console.log("🔍 DEBUG: Package price:", packagePrice)
    console.log("🔍 DEBUG: Is package mode:", isPackageChecked)
    
    // Call backend API to save cost details
    js("fetch('/api/purchases/save-costs', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(costData) })")
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
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving cost details:", error)
            js("alert('Error saving cost details: ' + error.message)")
        }
}

// Save car cost details and then navigate to booking details page
fun saveCarCostDetailsAndNavigate() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement
    val selectedChassis = chassisSelect.value
    
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
    val freight = (document.getElementById("freight") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val inspectionFee = (document.getElementById("inspectionFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val repairFee = (document.getElementById("repairFee") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val mscCharges = (document.getElementById("mscCharges") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    val profit = (document.getElementById("profit") as HTMLInputElement).value.toDoubleOrNull() ?: 0.0
    
    // Check if package checkbox is checked and get package price
    val packageCheckbox = document.getElementById("packageCheckbox") as HTMLInputElement?
    val isPackageChecked = packageCheckbox?.checked == true
    val packagePrice = if (isPackageChecked) {
        (document.getElementById("packagePrice") as HTMLInputElement?)?.value?.toDoubleOrNull() ?: 0.0
    } else {
        0.0
    }
    
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
    costData.packagePrice = packagePrice
    costData.isPackageMode = isPackageChecked
    
    console.log("📊 Cost data to save:", costData)
    console.log("🔍 DEBUG: Package price:", packagePrice)
    console.log("🔍 DEBUG: Is package mode:", isPackageChecked)
    
    // Call backend API to save cost details, THEN navigate
    js("fetch('/api/purchases/save-costs', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(costData) })")
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
            // NOW navigate to booking details page after save completes
            console.log("📋 Returning to populated BOOKING DETAILS page")
            showCarBookingPage()
            console.log("✅ Navigation completed")
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving cost details:", error)
            js("alert('Error saving cost details: ' + error.message)")
        }
}

// Save calculated Total C&F Price for individual car
fun saveTotalCnfPrice() {
    val chassisSelect = document.getElementById("chassisSelect") as HTMLSelectElement
    val selectedChassis = chassisSelect.value
    
    if (selectedChassis.isEmpty()) {
        js("alert('Please select a car chassis first')")
        return
    }
    
    console.log("💾 Saving Total C&F Price for chassis:", selectedChassis)
    
    // Get current Total C&F Price from the display
    val totalCnfPriceElement = document.getElementById("totalCnfPrice")
    val totalCnfPriceText = totalCnfPriceElement?.textContent ?: "¥0"
    
    // Extract numeric value from "¥476900" format
    val totalCnfPrice = totalCnfPriceText.replace("¥", "").replace(",", "").toDoubleOrNull() ?: 0.0
    
    console.log("📊 Total C&F Price to save:", totalCnfPrice)
    
    // Create cost data object
    val costData = js("{}")
    costData.chassis = selectedChassis
    costData.totalCnfPrice = totalCnfPrice
    
    console.log("📊 Cost data to save:", costData)
    
    // Call backend API to save total C&F price
    js("fetch('/api/purchases/save-total-cnf', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(costData) })")
        .then { response: dynamic ->
            console.log("Save Total C&F API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                throw js("Error('Failed to save total C&F price')")
            }
        }
        .then { result: dynamic ->
            console.log("✅ Total C&F price saved successfully:", result)
            js("alert('Total C&F Price saved successfully for chassis: ' + selectedChassis)")
        }
        .catch { error: dynamic ->
            console.error("❌ Error saving total C&F price:", error)
            js("alert('Error saving total C&F price: ' + error.message)")
        }
}