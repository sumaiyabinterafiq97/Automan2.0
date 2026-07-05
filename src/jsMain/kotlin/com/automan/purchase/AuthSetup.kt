package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event

/**
 * Production-only auto-login driven by `<meta name="automan-prod-auto-login" content="true">` in index.html.
 * Local/dev builds ship `false` so users still use the login screen.
 * Optional build-arg ENABLE_PROD_AUTO_LOGIN=true on Dockerfile.frontend.prod for AWS images.
 *
 * Credentials default to seeded admin (`admin@automan.com`); password via optional meta override.
 */
fun isProdAutoLoginEnabled(): Boolean {
    val meta = document.querySelector("meta[name=\"automan-prod-auto-login\"]") as? HTMLMetaElement
    return meta?.getAttribute("content")?.trim()?.equals("true", ignoreCase = true) == true
}

fun prodAutoLoginEmail(): String {
    val meta = document.querySelector("meta[name=\"automan-prod-auto-login-email\"]") as? HTMLMetaElement
    return meta?.getAttribute("content")?.trim()?.takeIf { it.isNotEmpty() } ?: "admin@automan.com"
}

fun prodAutoLoginPassword(): String {
    val meta = document.querySelector("meta[name=\"automan-prod-auto-login-password\"]") as? HTMLMetaElement
    val raw = meta?.getAttribute("content")?.trim()
    return raw?.takeIf { it.isNotEmpty() } ?: "Automan!Ship26Tokyo"
}

/**
 * Calls POST /api/auth/login when no token exists and prod auto-login meta is enabled.
 */
suspend fun prodAutoLoginIfNeeded() {
    val existing = safeLocalStorageGet("authToken")
    if (!existing.isNullOrBlank()) return
    if (!isProdAutoLoginEnabled()) return
    val email = prodAutoLoginEmail()
    val password = prodAutoLoginPassword()
    val body = js("{}")
    body.email = email
    body.password = password
    when (val r = ApiClient.post<dynamic>("auth/login", body)) {
        is ApiResult.Success -> {
            val d = (r.data as Any).unsafeCast<dynamic>()
            val token = d.token as? String
            val role = d.role as? String
            val nameResp = d.name as? String
            val userId = d.id as? Number
            if (token.isNullOrBlank()) {
                Logger.warn("Prod auto-login: login response had no token")
                return
            }
            safeLocalStorageSet("authToken", token)
            if (!role.isNullOrBlank()) safeLocalStorageSet("authUserRole", role)
            if (!nameResp.isNullOrBlank()) safeLocalStorageSet("authUserName", nameResp)
            if (userId != null) safeLocalStorageSet("authUserId", userId.toString())
            Logger.debug("Prod auto-login succeeded for ${email}")
            applyRoleBasedRestrictions()
            updateUserInfoInSidebar()
            if (currentRoute().isEmpty() || isAuthRoute()) {
                navigateToApp("/purchase")
            }
        }
        is ApiResult.Error -> {
            Logger.warn("Prod auto-login failed (${r.statusCode}): ${r.message}")
        }
    }
}

// Authentication Functions (sign in / sign up only; no setup page)

fun logout() {
    // Clear authentication data
    safeLocalStorageRemove("authToken")
    safeLocalStorageRemove("authUserRole")
    safeLocalStorageRemove("authUserName")
    safeLocalStorageRemove("authUserId")
    
    // Close sidebar
    closeSidebar()
    
    // Redirect to login page
    navigateToApp("/login")
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
    navigateToApp("/users")
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div class="user-page-container">
            <div class="user-page-card">
                <div class="user-page-header">
                    <h2>User Management</h2>
                    <div class="user-action-buttons">
                        <button id="addUserBtn" class="user-btn user-btn-primary">Add New User</button>
                        <button id="pendingRequestBtn" class="user-btn user-btn-warning">Pending Request</button>
                    </div>
                </div>
                
                <!-- Users Section -->
                <div class="users-section">
                    <h3>All Users</h3>
                    <div id="usersTable">
                        <div style="text-align: center; color: #666; padding: 40px;">
                            Loading users...
                        </div>
                    </div>
                </div>
            </div>
        </div>
    """
    
    // Add event listeners
    document.getElementById("addUserBtn")?.addEventListener("click", { _: Event ->
        showAddUserForm()
    })
    document.getElementById("pendingRequestBtn")?.addEventListener("click", { _: Event ->
        navigateToApp("/pending-signups")
    })
    loadUsers()
}

fun showPendingSignupsPage() {
    console.log("🔵 [PENDING PAGE] showPendingSignupsPage() called")
    navigateToApp("/pending-signups")
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div class="user-page-container">
            <div class="user-page-card">
                <div class="user-page-header">
                    <h2>Pending Signups</h2>
                    <div class="user-action-buttons">
                        <button id="backToUsersBtn" class="user-btn user-btn-secondary">Back to Users</button>
                    </div>
                </div>
                
                <div id="pendingSignupsTable" style="margin-top: 20px; padding: 16px;">
                    <div style="text-align: center; color: #666; padding: 20px;">
                        Loading pending signups...
                    </div>
                </div>
            </div>
        </div>
    """
    console.log("🔵 [PENDING PAGE] Content set, pendingSignupsTable element: ", document.getElementById("pendingSignupsTable"))
    
    // Add event listener for back button
    document.getElementById("backToUsersBtn")?.addEventListener("click", { _: Event ->
        navigateToApp("/users")
    })
    
    // Load pending signups
    console.log("🔵 [PENDING PAGE] Calling loadPendingSignups()...")
    loadPendingSignups()
}

// User management functions (showAddUserForm, loadUsers, etc.) are implemented in MinimalPurchaseApp.kt
// These are kept private because they're only used within the user management page context

// External function (defined in MinimalPurchaseApp.kt)
fun closeSidebar() {
    val sidebar = document.getElementById("sidebar") as HTMLElement?
    val overlay = document.getElementById("sidebarOverlay") as HTMLElement?
    
    val isMobile = window.innerWidth <= 767
    if (isMobile) {
        sidebar?.style?.setProperty("transform", "translateX(-100%)")
        sidebar?.style?.removeProperty("z-index")
    } else {
        sidebar?.style?.setProperty("left", "-250px")
        sidebar?.style?.removeProperty("transform")
        sidebar?.style?.removeProperty("z-index")
    }
    sidebar?.classList?.remove("sidebar-open")
    overlay?.style?.setProperty("display", "none")
}

