package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Headers

// Authentication and Setup Functions

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
    val form = document.getElementById("setupForm") as? HTMLFormElement
    
    if (form == null) {
        Logger.error("Setup form not found!")
        return
    }
    
    // Use a separate function to avoid closure issues
    form.addEventListener("submit", { event: Event ->
        event.preventDefault()
        handleSetupSubmit()
    })
}

fun handleSetupSubmit() {
    val emailInput = document.getElementById("setupEmail") as? HTMLInputElement
    val nameInput = document.getElementById("setupName") as? HTMLInputElement
    val passwordInput = document.getElementById("setupPassword") as? HTMLInputElement
    val submitBtn = document.getElementById("setupSubmit") as? HTMLButtonElement
    
    if (emailInput == null || nameInput == null || passwordInput == null || submitBtn == null) {
        Logger.error("Setup form elements not found!")
        showSetupMessage("Form elements not found. Please refresh the page.", "error")
        return
    }
    
    val email = emailInput.value.trim()
    val name = nameInput.value.trim()
    val password = passwordInput.value
        
        if (email.isBlank() || name.isBlank() || password.isBlank()) {
            showSetupMessage("Please fill in all fields", "error")
        return
        }
        
        // Show loading state
        submitBtn.disabled = true
        submitBtn.textContent = "Creating Account..."
        
    // Construct fetch request using proper Kotlin/JS types (same pattern as ApiService.kt)
    val requestBodyJson = """{"email":"${email.replace("\"", "\\\"").replace("\\", "\\\\")}","name":"${name.replace("\"", "\\\"").replace("\\", "\\\\")}","password":"${password.replace("\"", "\\\"").replace("\\", "\\\\")}"}"""
    
    val headers = Headers().apply {
        append("Content-Type", "application/json")
    }
    val init = RequestInit(
        method = "POST",
        headers = headers,
        body = requestBodyJson
    )
        
    window.fetch(apiUrl("auth/setup"), init)
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
            val errorMessage = try {
                val errorDynamic = error.asDynamic()
                (errorDynamic.message?.toString() ?: errorDynamic.toString() ?: "Unknown error")
            } catch (e: dynamic) {
                "Setup failed. Please try again."
            }
            showSetupMessage("Setup failed: $errorMessage", "error")
                submitBtn.disabled = false
                submitBtn.textContent = "Create Admin Account"
            }
}

fun showSetupMessage(message: String, type: String) {
    val messageDiv = document.getElementById("setupMessage") as? HTMLElement
    if (messageDiv == null) {
        Logger.error("Setup message div not found!")
        // Fallback to alert if message div not found
        window.alert(message)
        return
    }
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

fun logout() {
    // Clear authentication data
    safeLocalStorageRemove("authToken")
    safeLocalStorageRemove("authUserRole")
    safeLocalStorageRemove("authUserName")
    safeLocalStorageRemove("authUserId")
    
    // Close sidebar
    closeSidebar()
    
    // Redirect to login page
    window.location.hash = "#/"
}

fun updateUserInfoInSidebar() {
    val userInfoElement = document.getElementById("userInfo")
    val userName = safeLocalStorageGet("authUserName") ?: "User"
    val userRole = safeLocalStorageGet("authUserRole") ?: "VIEWER"
    
    userInfoElement?.innerHTML = """
        <div style="margin-bottom: 5px;">👤 $userName</div>
        <div style="font-size: 10px; color: #95a5a6;">Role: $userRole</div>
    """
}

// Role-based access control functions
fun getUserRole(): String {
    return safeLocalStorageGet("authUserRole") ?: "VIEWER"
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
    val roleRequestBtn = document.getElementById("roleRequestBtn") as HTMLElement?
    val rixoTransportBtn = document.getElementById("rixoTransportBtn") as HTMLElement?
    val clientAccountsQuickBtn = document.getElementById("clientAccountsQuickBtn") as HTMLElement?
    
    newBtn?.style?.display = if (isEditor()) "block" else "none"
    importBtn?.style?.display = if (isEditor()) "block" else "none"
    userManagementBtn?.style?.display = if (isAdmin()) "block" else "none"
    roleRequestBtn?.style?.display = if (!isAdmin()) "block" else "none" // Show for non-admin users
    rixoTransportBtn?.style?.display = "none"
    clientAccountsQuickBtn?.style?.display = if (isAdmin()) "block" else "none"
    
    // Update sidebar button visibility
    updateSidebarForRole()
    
    Logger.debug("Applied restrictions for role: $role")
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

fun showUserManagementPage() {
    window.location.hash = "#/users"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div style="border: 1px solid #ddd; border-radius: 4px; padding: 20px;">
            <h2>User Management</h2>
            <div style="margin-bottom: 20px;">
                <button id="addUserBtn" style="padding: 10px 20px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; margin-right: 10px;">Add New User</button>
                <button id="roleRequestsBtn" style="padding: 10px 20px; background-color: #ffc107; color: black; border: none; border-radius: 4px; cursor: pointer;">Role Requests</button>
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
    loadUsers()
    loadRoleRequests()
}

fun toggleRoleRequestsSection() {
    val section = document.getElementById("roleRequestsSection")
    if (section != null) {
        val currentDisplay = (section as HTMLElement).style.display
        (section as HTMLElement).style.display = if (currentDisplay == "none" || currentDisplay.isEmpty()) "block" else "none"
    }
}

// User management functions (showAddUserForm, loadUsers, etc.) are implemented in MinimalPurchaseApp.kt
// These are kept private because they're only used within the user management page context

// External function (defined in MinimalPurchaseApp.kt)
fun closeSidebar() {
    val sidebar = document.getElementById("sidebar") as HTMLElement?
    val overlay = document.getElementById("sidebarOverlay") as HTMLElement?
    
    sidebar?.style?.left = "-250px"
    overlay?.style?.display = "none"
}

