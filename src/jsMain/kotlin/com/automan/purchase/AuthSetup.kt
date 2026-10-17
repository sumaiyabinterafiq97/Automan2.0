package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.fetch.RequestInit

/**
 * Adds the staff session bearer token to same-app `/api/` fetches, including calls that do not use [ApiClient].
 * Presigned storage URLs and other third-party hosts are left unchanged.
 * A 401 is returned to the caller and does not clear the saved login. A 403 is left for the page to show.
 */
fun installAuthenticatedFetch() {
    if (window.asDynamic().__automanFetchInstalled == true) return
    window.asDynamic().__automanFetchInstalled = true
    js(
        """
        (function () {
          var original = window.fetch.bind(window);
          window.fetch = function (input, init) {
            var url = '';
            if (typeof input === 'string') url = input;
            else if (input && typeof input.url === 'string') url = input.url;
            function automanApi(raw) {
              try {
                var parsed = new URL(raw, window.location.href);
                var path = parsed.pathname || '';
                if (path !== '/api' && path.indexOf('/api/') !== 0) return false;
                if (parsed.origin === window.location.origin) return true;
                var host = parsed.hostname;
                return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === window.location.hostname;
              } catch (e) { return false; }
            }
            if (!automanApi(url)) return original(input, init);
            var next = init ? Object.assign({}, init) : {};
            var headers = new Headers(next.headers || {});
            var token = '';
            try { token = window.localStorage.getItem('authToken') || ''; } catch (e) { token = ''; }
            if (token && !headers.has('Authorization')) headers.set('Authorization', 'Bearer ' + token);
            next.headers = headers;
            return original(input, next).then(function (response) {
              if (response.status === 403) {
                var now = Date.now();
                if (!window.__automan403At || now - window.__automan403At > 2000) {
                  window.__automan403At = now;
                  var note = document.getElementById('automan-permission-note');
                  if (!note) {
                    note = document.createElement('div');
                    note.id = 'automan-permission-note';
                    note.setAttribute('role', 'alert');
                    note.style.cssText = 'position:fixed;top:16px;left:50%;transform:translateX(-50%);z-index:20000;background:#7f1d1d;color:#fff;padding:12px 16px;border-radius:8px;font:14px sans-serif;max-width:90%;box-shadow:0 8px 24px rgba(0,0,0,.2);';
                    if (document.body) document.body.appendChild(note);
                  }
                  if (note) note.textContent = 'You do not have permission to perform this action.';
                  clearTimeout(window.__automan403Timer);
                  window.__automan403Timer = setTimeout(function () {
                    var current = document.getElementById('automan-permission-note');
                    if (current && current.parentNode) current.parentNode.removeChild(current);
                  }, 4000);
                }
              }
              return response;
            });
          };
        })();
        """
    )
}

// Authentication Functions (sign in / sign up only; no setup page)

fun logout() {
    val hadToken = !safeLocalStorageGet("authToken").isNullOrBlank()
    var finished = false
    fun finishLogout() {
        if (finished) return
        finished = true
        safeLocalStorageRemove("authToken")
        safeLocalStorageRemove("authUserRole")
        safeLocalStorageRemove("authUserName")
        safeLocalStorageRemove("authUserId")
        clearPurchaseListViewsSessionCache()
        closeSidebar()
        navigateToApp("/login")
    }
    if (!hadToken) {
        finishLogout()
        return
    }
    val init = js("{}").unsafeCast<RequestInit>()
    init.method = "POST"
    val promise = window.fetch(apiUrl("auth/logout"), init).asDynamic()
    promise.then({ _: dynamic -> finishLogout() })
    promise.catch({ _: dynamic -> finishLogout() })
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

/** UI-only. Booking, invoice creation, and other staff writes stay blocked on the server. */
fun isViewerBlockedRoute(route: String): Boolean {
    if (!isViewer()) return false
    return routeAtStartsWith(route, "/add")
        || routeAtStartsWith(route, "/edit")
        || routeAtStartsWith(route, "/booking")
        || routeAtStartsWith(route, "/recalculate-booking")
        || routeAtStartsWith(route, "/invoice")
        || routeAtStartsWith(route, "/recreate-invoice")
        || routeAtStartsWith(route, "/rixo-generator")
        || routeAtStartsWith(route, "/rixo-updater")
        || routeAtStartsWith(route, "/rixo-transport")
        || routeAtStartsWith(route, "/users")
        || routeAtStartsWith(route, "/admin/party-access")
        || routeAtStartsWith(route, "/pending-signups")
        || routeAtStartsWith(route, "/role-request")
}
fun isEditor(): Boolean = getUserRole() == "EDITOR" || getUserRole() == "ADMIN"
fun isAdmin(): Boolean = getUserRole() == "ADMIN"

fun applyRoleBasedRestrictions() {
    val role = getUserRole()
    
    // Hide/show elements based on role
    val newBtn = document.getElementById("newBtn") as HTMLElement?
    val importBtn = document.getElementById("importBtn") as HTMLElement?
    val userManagementBtn = document.getElementById("userManagementBtn") as HTMLElement?
    val partyAccessBtn = document.getElementById("partyAccessBtn") as HTMLElement?
    val roleRequestBtn = document.getElementById("roleRequestBtn") as HTMLElement?
    val rixoTransportBtn = document.getElementById("rixoTransportBtn") as HTMLElement?
    val clientAccountsQuickBtn = document.getElementById("clientAccountsQuickBtn") as HTMLElement?
    val purchaseListQuickAddBtn = document.getElementById("purchaseListQuickAddBtn") as HTMLElement?
    val purchaseBulkEditBtn = document.getElementById("purchaseBulkEditBtn") as HTMLElement?
    
    newBtn?.style?.display = if (isEditor()) "block" else "none"
    importBtn?.style?.display = if (isEditor()) "block" else "none"
    userManagementBtn?.style?.display = if (isAdmin()) "block" else "none"
    // Hidden for now. The page remains at /admin/party-access.
    partyAccessBtn?.style?.display = "none"
    roleRequestBtn?.style?.display = if (isEditor() && !isAdmin()) "block" else "none"
    rixoTransportBtn?.style?.display = "none"
    clientAccountsQuickBtn?.style?.display = if (isAdmin()) "block" else "none"
    // inline-flex matches .purchase-quick-add-btn; hide for VIEWER (same create gate as New Purchase)
    purchaseListQuickAddBtn?.style?.display = if (isEditor()) "inline-flex" else "none"
    purchaseBulkEditBtn?.style?.display = if (isEditor()) "inline-flex" else "none"
    document.body?.classList?.toggle("automan-viewer", isViewer())
    if (document.getElementById("automan-viewer-style") == null && isViewer()) {
        val style = document.createElement("style") as HTMLStyleElement
        style.id = "automan-viewer-style"
        style.textContent = """
            body.automan-viewer #newBtn,
            body.automan-viewer #importBtn,
            body.automan-viewer #quickPurchaseBtn,
            body.automan-viewer #purchaseBulkEditBtn,
            body.automan-viewer #rixoRequestBtn,
            body.automan-viewer #carBookingBtn,
            body.automan-viewer #anInvoiceBtn,
            body.automan-viewer #addMasterSetBtn,
            body.automan-viewer #roleRequestBtn,
            body.automan-viewer #userManagementBtn,
            body.automan-viewer #partyAccessBtn,
            body.automan-viewer .edit-btn,
            body.automan-viewer .card-edit-btn,
            body.automan-viewer .scm-edit-btn,
            body.automan-viewer .pol-edit-btn,
            body.automan-viewer .rixo-tree-leaf-edit-btn,
            body.automan-viewer .rixo-tree-leaf-delete-btn,
            body.automan-viewer .rixo-tree-inline-add-outer,
            body.automan-viewer [data-sm-inline-add],
            body.automan-viewer [data-rpm-inline-add],
            body.automan-viewer button[aria-label="Edit"],
            body.automan-viewer button[aria-label="Delete"],
            body.automan-viewer button[id^="add"][id$="Btn"],
            body.automan-viewer button[id^="save"][id$="Btn"],
            body.automan-viewer button[id^="delete"][id$="Btn"] {
                display: none !important;
            }
        """.trimIndent()
        document.head?.appendChild(style)
    }
    
    // Update sidebar button visibility
    updateSidebarForRole()
    
    Logger.debug("Applied restrictions for role: $role")
}

fun updateSidebarForRole() {
    val newBtn = document.getElementById("newBtn") as HTMLElement?
    val importBtn = document.getElementById("importBtn") as HTMLElement?
    val rixoRequestBtn = document.getElementById("rixoRequestBtn") as HTMLElement?
    
    val quickPurchaseBtn = document.getElementById("quickPurchaseBtn") as HTMLElement?
    val carBookingBtn = document.getElementById("carBookingBtn") as HTMLElement?
    val anInvoiceBtn = document.getElementById("anInvoiceBtn") as HTMLElement?
    val addMasterSetBtn = document.getElementById("addMasterSetBtn") as HTMLElement?
    if (isViewer()) {
        newBtn?.style?.display = "none"
        importBtn?.style?.display = "none"
        rixoRequestBtn?.style?.display = "none"
        quickPurchaseBtn?.style?.display = "none"
        carBookingBtn?.style?.display = "none"
        anInvoiceBtn?.style?.display = "none"
        addMasterSetBtn?.style?.display = "none"
    } else if (isEditor()) {
        newBtn?.style?.display = "block"
        importBtn?.style?.display = "block"
        rixoRequestBtn?.style?.display = "block"
        quickPurchaseBtn?.style?.display = "block"
        carBookingBtn?.style?.display = "block"
        anInvoiceBtn?.style?.display = "block"
        addMasterSetBtn?.style?.display = "block"
    } else if (isAdmin()) {
        newBtn?.style?.display = "block"
        importBtn?.style?.display = "block"
        rixoRequestBtn?.style?.display = "block"
        quickPurchaseBtn?.style?.display = "block"
        carBookingBtn?.style?.display = "block"
        anInvoiceBtn?.style?.display = "block"
        addMasterSetBtn?.style?.display = "block"
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
                        <button type="button" id="addUserBtn" class="user-btn user-btn-primary">Add New User</button>
                        <button type="button" id="pendingRequestBtn" class="user-btn user-btn-warning">Pending Request</button>
                    </div>
                </div>
                
                <!-- Users Section -->
                <div class="users-section">
                    <h3>All Users</h3>
                    <div id="usersTable">
                        <div class="user-status-block" role="status">Loading users…</div>
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
                        <button type="button" id="backToUsersBtn" class="user-btn user-btn-secondary">Back to Users</button>
                    </div>
                </div>
                
                <div id="pendingSignupsTable" class="pending-signups-table-wrap">
                    <div class="user-status-block" role="status">Loading pending signups…</div>
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
    val hamburger = document.getElementById("hamburgerBtn")
    
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
    overlay?.setAttribute("aria-hidden", "true")
    hamburger?.setAttribute("aria-expanded", "false")
    hamburger?.setAttribute("aria-label", "Open menu")
}

