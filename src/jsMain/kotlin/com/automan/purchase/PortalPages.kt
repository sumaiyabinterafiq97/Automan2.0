package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event

private val portalScope = MainScope()
private var portalGeneration = 0

data class PortalCarRow(
    val chassis: String,
    val carName: String,
    val year: String,
    val stockLocation: String,
    val country: String,
    val shipped: Boolean,
)

data class PortalShipmentRow(
    val bookingId: String,
    val vessel: String,
    val pol: String,
    val pod: String,
    val shipmentDate: String,
    val cyCutDate: String,
    val eta: String,
    val chassis: List<String>,
    val clientNames: List<String> = emptyList(),
)

data class PortalInvoiceRow(
    val invoiceNumber: String,
    val date: String,
    val vessel: String,
    val chassis: List<String>,
    val priceType: String,
)

data class PortalTransactionRow(
    val date: String,
    val type: String,
)

fun usePortalChrome() {
    document.body?.classList?.add("portal-session")
    document.getElementById("sidebar")?.setAttribute("hidden", "")
    document.getElementById("hamburgerBtnContainer")?.setAttribute("hidden", "")
    document.getElementById("sidebarOverlay")?.setAttribute("hidden", "")
}

fun restoreStaffChrome() {
    document.body?.classList?.remove("portal-session")
    document.body?.classList?.remove("portal-client-view")
    document.body?.classList?.remove("portal-consignee-view")
    document.getElementById("sidebar")?.removeAttribute("hidden")
    document.getElementById("hamburgerBtnContainer")?.removeAttribute("hidden")
    document.getElementById("sidebarOverlay")?.removeAttribute("hidden")
}

fun showClientPortal(route: String) {
    document.body?.classList?.add("portal-client-view")
    document.body?.classList?.remove("portal-consignee-view")
    val page = clientPortalPage(route)
    val generation = mountPortalShell("client", "Client portal", clientNav(page))
    portalScope.launch {
        when (val me = ApiClient.request<dynamic>("portal/client/me")) {
            is ApiResult.Error -> renderPortalFailure(generation, me.message, me.statusCode)
            is ApiResult.Success -> {
                if (generation != portalGeneration) return@launch
                val account = me.data
                if (account.linked != true) {
                    renderPortalWaiting(generation, "Portal access has not been assigned yet. An administrator needs to link this account before any records can be shown.")
                    return@launch
                }
                if (account.active != true) {
                    renderPortalInactive(generation, "This client account is not active. Purchases, shipments, invoices, and transactions are not available.")
                    return@launch
                }
                renderClientIdentity(generation, jsText(account.clientName))
                loadClientPage(generation, page, route)
            }
        }
    }
}

fun showConsigneePortal(route: String) {
    document.body?.classList?.remove("portal-client-view")
    document.body?.classList?.add("portal-consignee-view")
    val page = consigneePortalPage(route)
    val generation = mountPortalShell("consignee", "Consignee portal", consigneeNav(page))
    portalScope.launch {
        when (val me = ApiClient.request<dynamic>("portal/consignee/me")) {
            is ApiResult.Error -> renderPortalFailure(generation, me.message, me.statusCode)
            is ApiResult.Success -> {
                if (generation != portalGeneration) return@launch
                val account = me.data
                if (account.linked != true) {
                    renderPortalWaiting(generation, "Portal access has not been assigned yet. An administrator needs to link this account before any records can be shown.")
                    return@launch
                }
                if (account.active != true) {
                    renderPortalInactive(generation, "This consignee account is inactive. Shipment and car records are not available.")
                    return@launch
                }
                renderPortalAccount(generation, jsText(account.legalName))
                loadConsigneePage(generation, page)
            }
        }
    }
}

private suspend fun loadClientPage(generation: Int, page: String, route: String) {
    try {
        when (page) {
            "car" -> renderClientPurchaseDetail(generation, clientPurchaseDetailId(route) ?: return)
            "cars" -> renderClientPurchaseList(generation)
            "shipments" -> renderClientShipments(generation, fetchPortal("portal/client/shipments"))
            "invoices" -> renderClientInvoices(generation, fetchPortal("portal/client/invoices"))
            "transactions" -> renderClientLedger(generation)
            else -> renderClientDashboard(generation, fetchPortal("portal/client/dashboard"))
        }
    } catch (e: dynamic) {
        renderPortalFailure(generation, "The request failed.", 0)
    }
}

private suspend fun loadConsigneePage(generation: Int, page: String) {
    try {
        when (page) {
            "ongoing" -> renderConsigneeShipments(
                generation,
                fetchPortal("portal/consignee/shipments/ongoing"),
                "No ongoing shipments.",
                "",
            )
            "not-shipped" -> renderPortalCars(generation, fetchPortal("portal/consignee/cars/not-shipped${portalQuerySuffix(portalQuery())}"), portalQuery(), "No cars are waiting to ship.", "not-shipped")
            "shipped" -> renderPortalCars(generation, fetchPortal("portal/consignee/cars/shipped${portalQuerySuffix(portalQuery())}"), portalQuery(), "No shipped cars.", "shipped")
            "history" -> renderConsigneeShipments(
                generation,
                fetchPortal("portal/consignee/shipments/history"),
                "No shipment history.",
                "",
            )
            else -> renderConsigneeDashboard(generation, fetchPortal("portal/consignee/dashboard"))
        }
    } catch (e: dynamic) {
        renderPortalFailure(generation, "The request failed.", 0)
    }
}

private fun clientPurchaseDetailId(route: String): Long? {
    val path = route.substringBefore('?')
    val marker = "/portal/client/cars/"
    val at = path.indexOf(marker)
    if (at < 0) return null
    val rest = path.substring(at + marker.length).trim('/').substringBefore('/')
    return rest.toLongOrNull()
}

private fun clientPortalPage(route: String): String = when {
    clientPurchaseDetailId(route) != null -> "car"
    routeAtStartsWith(route, "/portal/client/cars") -> "cars"
    routeAtStartsWith(route, "/portal/client/shipments") -> "shipments"
    routeAtStartsWith(route, "/portal/client/invoices") -> "invoices"
    routeAtStartsWith(route, "/portal/client/transactions") -> "transactions"
    else -> "dashboard"
}

private fun consigneePortalPage(route: String): String = when {
    routeAtStartsWith(route, "/portal/consignee/ongoing") -> "ongoing"
    routeAtStartsWith(route, "/portal/consignee/not-shipped") -> "not-shipped"
    routeAtStartsWith(route, "/portal/consignee/shipped") -> "shipped"
    routeAtStartsWith(route, "/portal/consignee/history") -> "history"
    else -> "dashboard"
}

private fun clientNav(page: String): String = portalNav(
    listOf(
        PortalNavItem("dashboard", "Dashboard", "/portal/client"),
        PortalNavItem("cars", "Purchased cars", "/portal/client/cars"),
        PortalNavItem("shipments", "Shipments", "/portal/client/shipments"),
        PortalNavItem("invoices", "Invoices", "/portal/client/invoices"),
        PortalNavItem("transactions", "Transactions", "/portal/client/transactions"),
    ),
    if (page == "car") "cars" else page,
)

private fun consigneeNav(page: String): String = portalNav(
    listOf(
        PortalNavItem("dashboard", "Dashboard", "/portal/consignee"),
        PortalNavItem("ongoing", "Ongoing shipments", "/portal/consignee/ongoing"),
        PortalNavItem("not-shipped", "Not yet shipped", "/portal/consignee/not-shipped"),
        PortalNavItem("shipped", "Shipped cars", "/portal/consignee/shipped"),
        PortalNavItem("history", "Shipment history", "/portal/consignee/history"),
    ),
    page,
)

private data class PortalNavItem(val id: String, val label: String, val route: String)

private fun portalNav(items: List<PortalNavItem>, current: String): String =
    items.joinToString("") { item ->
        val currentAttr = if (item.id == current) " aria-current=\"page\"" else ""
        """<button type="button" class="portal-nav-btn" data-portal-route="${item.route}"$currentAttr>${escapeHtml(item.label)}</button>"""
    }

private fun mountPortalShell(role: String, title: String, navHtml: String): Int {
    usePortalChrome()
    val generation = ++portalGeneration
    val content = document.getElementById("content") ?: return generation
    content.innerHTML = """
        <div id="portalPage" class="portal-page fade-in" data-portal-role="$role">
            <style>
                .portal-page { max-width: 1100px; margin: 0 auto; color: #1f2937; }
                .portal-top { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
                .portal-brand { font-size: 13px; letter-spacing: .04em; text-transform: uppercase; color: #64748b; }
                .portal-top h1 { margin: 2px 0 4px; font-size: 28px; color: #2c3e50; }
                #portalAccount { color: #475569; min-height: 1.2em; }
                .portal-logout { background: #e74c3c; color: #fff; border: 0; border-radius: 6px; padding: 10px 14px; cursor: pointer; }
                .portal-nav { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 18px; }
                .portal-nav-btn { background: #fff; color: #2c3e50; border: 1px solid #cbd5e1; border-radius: 999px; padding: 8px 12px; cursor: pointer; }
                .portal-nav-btn[aria-current="page"] { background: #2c3e50; color: #fff; border-color: #2c3e50; }
                .portal-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 12px; }
                .portal-card { background: #fff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; box-shadow: 0 1px 2px rgba(15, 23, 42, .04); }
                .portal-card span { display: block; color: #64748b; font-size: 13px; }
                .portal-card strong { display: block; margin-top: 6px; font-size: 28px; color: #2c3e50; }
                .portal-note, .portal-state { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 10px; padding: 14px 16px; }
                .portal-state h2 { margin: 0 0 6px; font-size: 18px; color: #2c3e50; }
                .portal-search { display: flex; gap: 8px; margin-bottom: 12px; }
                .portal-search input { flex: 1; min-width: 0; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 10px; }
                .portal-table-wrap { overflow-x: auto; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
                .portal-table { width: 100%; border-collapse: collapse; min-width: 720px; }
                .portal-table th, .portal-table td { text-align: left; padding: 10px 12px; border-bottom: 1px solid #e2e8f0; vertical-align: top; }
                .portal-table th { background: #f8fafc; color: #475569; font-size: 12px; letter-spacing: .03em; text-transform: uppercase; }
                .portal-missing { color: #64748b; }
                .portal-badge { display: inline-block; border-radius: 999px; padding: 2px 8px; font-size: 12px; background: #e2e8f0; }
                .portal-badge.shipped { background: #dcfce7; color: #166534; }
                @media (max-width: 720px) {
                    .portal-top { flex-direction: column; }
                    .portal-top h1 { font-size: 24px; }
                }

                /* Client portal presentation only. Every rule below is scoped to the client role. */
                .portal-page[data-portal-role='client'] { --pc-navy: #2c3e50; --pc-ink: #1f2937; --pc-muted: #64748b; --pc-line: #e2e8f0; --pc-line-strong: #cbd5e1; --pc-soft: #f8fafc; max-width: 1240px; }
                body.portal-client-view .page-title-bar { margin: 0 0 4px; padding-top: 2px; padding-bottom: 0; }
                /* Staff toolbar is already empty for portal users; its margins otherwise leave a large gap under the title. */
                body.portal-client-view div:has(> #rixoTransportBtn) { display: none !important; margin: 0 !important; }
                body.portal-client-view .page-title-bar h1 { margin: 0; font-size: 22px !important; line-height: 1.2 !important; font-weight: 700; letter-spacing: -0.01em; }
                #content:has(> .portal-page[data-portal-role='client']) { padding-top: 16px !important; }
                #content > .portal-page[data-portal-role='client'] { padding-top: 0; }
                @media (max-width: 767px) {
                    /* Global mobile rules force larger buttons and an 18px h1 inside #content. Client portal keeps its own sizes. */
                    #content .portal-page[data-portal-role='client'] .portal-top h1 { font-size: 22px !important; line-height: 1.2 !important; }
                    #content .portal-page[data-portal-role='client'] .portal-nav-btn,
                    #content .portal-page[data-portal-role='client'] .portal-logout,
                    #content .portal-page[data-portal-role='client'] .portal-search button { min-height: 40px; min-width: 0; height: 40px; padding: 0 14px; font-size: 14px; }
                }
                .portal-page[data-portal-role='client'] .portal-top { align-items: center; margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--pc-line); }
                .portal-page[data-portal-role='client'] .portal-brand { font-size: 11px; font-weight: 600; letter-spacing: .08em; line-height: 1.2; }
                .portal-page[data-portal-role='client'] .portal-top h1 { margin: 2px 0 0; font-size: 22px; line-height: 1.2; }
                .portal-page[data-portal-role='client'] #portalAccount { display: flex; flex-wrap: wrap; align-items: baseline; gap: 8px; margin-top: 4px; font-size: 14px; line-height: 1.3; color: var(--pc-muted); }
                .portal-page[data-portal-role='client'] .portal-account-name { color: var(--pc-ink); font-weight: 600; font-size: 15px; }
                .portal-page[data-portal-role='client'] .portal-account-code { font-variant-numeric: tabular-nums; letter-spacing: .02em; }
                .portal-page[data-portal-role='client'] .portal-logout { flex: 0 0 auto; height: 36px; padding: 0 16px; font-size: 14px; font-weight: 600; transition: background-color .15s ease; }
                .portal-page[data-portal-role='client'] .portal-logout:hover { background: #d63e2f; }
                .portal-page[data-portal-role='client'] .portal-logout:focus-visible,
                .portal-page[data-portal-role='client'] .portal-nav-btn:focus-visible,
                .portal-page[data-portal-role='client'] .portal-search button:focus-visible { outline: 2px solid var(--pc-navy); outline-offset: 2px; }
                .portal-page[data-portal-role='client'] .portal-nav { gap: 6px; margin-bottom: 14px; }
                .portal-page[data-portal-role='client'] .portal-nav-btn { display: inline-flex; align-items: center; height: 36px; padding: 0 14px; font-size: 14px; font-weight: 500; line-height: 1; white-space: nowrap; transition: background-color .15s ease, border-color .15s ease; }
                .portal-page[data-portal-role='client'] .portal-nav-btn:hover { background: #f1f5f9; border-color: #94a3b8; }
                .portal-page[data-portal-role='client'] .portal-nav-btn[aria-current='page'] { background: var(--pc-navy); border-color: var(--pc-navy); color: #fff; font-weight: 600; }

                .portal-page[data-portal-role='client'] .portal-cards { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
                .portal-page[data-portal-role='client'] .portal-card { width: 100%; padding: 18px 18px 20px; border-top: 3px solid var(--pc-navy); text-align: left; box-shadow: 0 1px 2px rgba(15, 23, 42, .05); }
                .portal-page[data-portal-role='client'] button.portal-card { font: inherit; color: inherit; cursor: pointer; }
                .portal-page[data-portal-role='client'] button.portal-card:hover { border-color: #cbd5e1; box-shadow: 0 4px 12px rgba(15, 23, 42, .08); }
                .portal-page[data-portal-role='client'] button.portal-card:focus-visible { outline: 2px solid var(--pc-navy); outline-offset: 2px; }
                .portal-page[data-portal-role='client'] .portal-card:nth-child(2) { border-top-color: #22c55e; }
                .portal-page[data-portal-role='client'] .portal-card:nth-child(3) { border-top-color: #64748b; }
                .portal-page[data-portal-role='client'] .portal-card span { font-size: 12px; font-weight: 600; letter-spacing: .05em; text-transform: uppercase; }
                .portal-page[data-portal-role='client'] .portal-card strong { margin-top: 8px; font-size: 32px; line-height: 1.1; font-variant-numeric: tabular-nums; }

                .portal-page[data-portal-role='client'] .portal-note { margin: 0 0 12px; padding: 10px 12px; border-radius: 8px; border-left: 3px solid #94a3b8; background: #f8fafc; font-size: 13px; line-height: 1.45; color: #475569; }
                .portal-page[data-portal-role='client'] .portal-state { border-radius: 8px; }

                .portal-page[data-portal-role='client'] .portal-search { align-items: stretch; margin-bottom: 12px; }
                .portal-page[data-portal-role='client'] .portal-search input { box-sizing: border-box; height: 40px; padding: 0 12px; border-radius: 8px; font-size: 14px; background: #fff; }
                .portal-page[data-portal-role='client'] .portal-search input:focus-visible { outline: none; border-color: var(--pc-navy); box-shadow: 0 0 0 3px rgba(44, 62, 80, .15); }
                .portal-page[data-portal-role='client'] .portal-search button { box-sizing: border-box; height: 40px; padding: 0 18px; border: 1px solid var(--pc-navy); border-radius: 8px; background: var(--pc-navy); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; transition: background-color .15s ease; }
                .portal-page[data-portal-role='client'] .portal-search button:hover { background: #1f2d3a; }

                .portal-page[data-portal-role='client'] .portal-table-wrap { border-radius: 10px; }
                .portal-page[data-portal-role='client'] .portal-table { min-width: 0; font-size: 14px; }
                .portal-page[data-portal-role='client'] .portal-table th,
                .portal-page[data-portal-role='client'] .portal-table td { padding: 9px 14px; vertical-align: middle; }
                .portal-page[data-portal-role='client'] .portal-table th { background: #f1f5f9; color: #334155; font-size: 11.5px; font-weight: 700; letter-spacing: .05em; white-space: nowrap; }
                .portal-page[data-portal-role='client'] .portal-table tbody tr:last-child td { border-bottom: 0; }
                .portal-page[data-portal-role='client'] .portal-table tbody tr:hover { background: var(--pc-soft); }
                .portal-page[data-portal-role='client'] .portal-missing { white-space: nowrap; }
                .portal-page[data-portal-role='client'] .portal-id { white-space: nowrap; font-weight: 600; font-variant-numeric: tabular-nums; }
                .portal-page[data-portal-role='client'] .portal-nowrap { white-space: nowrap; font-variant-numeric: tabular-nums; }
                .portal-page[data-portal-role='client'] .portal-badge { padding: 3px 10px; font-size: 12px; font-weight: 600; line-height: 1.3; white-space: nowrap; color: #475569; }
                .portal-page[data-portal-role='client'] .portal-badge.shipped { color: #166534; }
                .portal-page[data-portal-role='client'] .portal-table--shipments { min-width: 980px; }
                .portal-page[data-portal-role='client'] .portal-table--invoices .portal-id { font-size: 13.5px; }
                .portal-page[data-portal-role='client'] .portal-table--ledger { min-width: 920px; }
                .portal-page[data-portal-role='client'] .portal-table--ledger td { white-space: nowrap; }
                .portal-page[data-portal-role='client'] .portal-table--ledger .portal-ledger-text { white-space: normal; min-width: 180px; max-width: 320px; line-height: 1.35; }
                .portal-page[data-portal-role='client'] .portal-money { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
                .portal-page[data-portal-role='client'] .portal-money--negative { color: #b91c1c; }
                .portal-page[data-portal-role='client'] .portal-money--positive { color: #166534; }
                .portal-page[data-portal-role='client'] .portal-table--shipments th,
                .portal-page[data-portal-role='client'] .portal-table--shipments td { padding-left: 10px; padding-right: 10px; }
                .portal-page[data-portal-role='client'] .portal-table--shipments .portal-col-vessel { min-width: 90px; }
                .portal-page[data-portal-role='client'] .portal-table--invoices { min-width: 560px; }
                .portal-page[data-portal-role='client'] .portal-table--transactions .portal-col-date { width: 160px; }
                .portal-page[data-portal-role='client'] .portal-pill { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; line-height: 1.3; white-space: nowrap; background: #e2e8f0; color: #475569; }
                .portal-page[data-portal-role='client'] .portal-pill--booked { background: #fef3c7; color: #92400e; }
                .portal-page[data-portal-role='client'] .portal-pill--shipment { background: #dbeafe; color: #1e40af; }
                .portal-page[data-portal-role='client'] .portal-pill--cf { background: #dbeafe; color: #1e40af; }
                .portal-page[data-portal-role='client'] .portal-pill--fob { background: #ede9fe; color: #5b21b6; }
                .portal-page[data-portal-role='client'] .portal-pill--issued { background: #dcfce7; color: #166534; }
                .portal-page[data-portal-role='client'] .portal-pill--reversal { background: #fee2e2; color: #991b1b; }
                @media (max-width: 720px) {
                    .portal-page[data-portal-role='client'] .portal-top { flex-direction: row; flex-wrap: wrap; align-items: center; }
                    .portal-page[data-portal-role='client'] .portal-top h1 { font-size: 22px; }
                    .portal-page[data-portal-role='client'] .portal-table--transactions { min-width: 0; }
                }
                @media (max-width: 900px) {
                    .portal-page[data-portal-role='client'] .portal-cards { grid-template-columns: 1fr; }
                }
                @media (max-width: 480px) {
                    .portal-page[data-portal-role='client'] .portal-nav { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 4px; }
                    .portal-page[data-portal-role='client'] .portal-nav-btn { padding: 0 12px; }
                    .portal-page[data-portal-role='client'] .portal-card strong { font-size: 28px; }
                    body.portal-client-view .page-title-bar h1 { font-size: 18px !important; }
                }
                @media (max-width: 720px) {
                    .portal-page[data-portal-role='client'] .portal-table--cars thead { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
                    .portal-page[data-portal-role='client'] .portal-table--cars,
                    .portal-page[data-portal-role='client'] .portal-table--cars tbody,
                    .portal-page[data-portal-role='client'] .portal-table--cars tr { display: block; }
                    .portal-page[data-portal-role='client'] .portal-table--cars tr { padding: 8px 14px; border-bottom: 1px solid var(--pc-line); }
                    .portal-page[data-portal-role='client'] .portal-table--cars tbody tr:last-child { border-bottom: 0; }
                    .portal-page[data-portal-role='client'] .portal-table--cars td { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 4px 0; border: 0; text-align: right; }
                    .portal-page[data-portal-role='client'] .portal-table--cars td::before { content: attr(data-label); flex: 0 0 auto; color: var(--pc-muted); font-size: 12px; font-weight: 600; text-align: left; }
                }

                /* Consignee portal presentation only. */
                .portal-page[data-portal-role='consignee'] { --pg-navy: #2c3e50; --pg-ink: #1f2937; --pg-muted: #64748b; --pg-line: #e2e8f0; --pg-soft: #f8fafc; max-width: 1240px; }
                body.portal-consignee-view .page-title-bar { margin: 0 0 4px; padding-top: 2px; padding-bottom: 0; }
                body.portal-consignee-view .page-title-bar h1 { margin: 0; font-size: 22px !important; line-height: 1.2 !important; font-weight: 700; letter-spacing: -0.01em; }
                body.portal-consignee-view div:has(> #rixoTransportBtn) { display: none !important; margin: 0 !important; }
                #content:has(> .portal-page[data-portal-role='consignee']) { padding-top: 16px !important; }
                .portal-page[data-portal-role='consignee'] .portal-top { align-items: center; margin-bottom: 12px; padding-bottom: 12px; border-bottom: 1px solid var(--pg-line); }
                .portal-page[data-portal-role='consignee'] .portal-top > div:first-child { min-width: 0; flex: 1; }
                .portal-page[data-portal-role='consignee'] .portal-brand { font-size: 11px; font-weight: 600; letter-spacing: .08em; line-height: 1.2; }
                .portal-page[data-portal-role='consignee'] .portal-top h1 { margin: 2px 0 0; font-size: 22px; line-height: 1.2; }
                .portal-page[data-portal-role='consignee'] #portalAccount { margin-top: 4px; color: var(--pg-ink); font-size: 15px; font-weight: 600; line-height: 1.35; overflow-wrap: anywhere; }
                .portal-page[data-portal-role='consignee'] .portal-logout { flex: 0 0 auto; height: 36px; padding: 0 16px; font-size: 14px; font-weight: 600; }
                .portal-page[data-portal-role='consignee'] .portal-logout:hover { background: #d63e2f; }
                .portal-page[data-portal-role='consignee'] .portal-logout:focus-visible,
                .portal-page[data-portal-role='consignee'] .portal-nav-btn:focus-visible,
                .portal-page[data-portal-role='consignee'] .portal-search button:focus-visible,
                .portal-page[data-portal-role='consignee'] button.portal-card:focus-visible { outline: 2px solid var(--pg-navy); outline-offset: 2px; }
                .portal-page[data-portal-role='consignee'] .portal-nav { gap: 6px; margin-bottom: 14px; }
                .portal-page[data-portal-role='consignee'] .portal-nav-btn { display: inline-flex; align-items: center; height: 36px; padding: 0 14px; font-size: 14px; font-weight: 500; line-height: 1; white-space: nowrap; }
                .portal-page[data-portal-role='consignee'] .portal-nav-btn:hover { background: #f1f5f9; border-color: #94a3b8; }
                .portal-page[data-portal-role='consignee'] .portal-nav-btn[aria-current='page'] { background: var(--pg-navy); border-color: var(--pg-navy); color: #fff; font-weight: 600; }
                .portal-page[data-portal-role='consignee'] .portal-nav-btn[aria-current='page']:hover { background: var(--pg-navy); color: #fff; }

                .portal-page[data-portal-role='consignee'] .portal-cards { grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
                .portal-page[data-portal-role='consignee'] .portal-card { box-sizing: border-box; width: 100%; height: 100%; margin: 0; padding: 18px 18px 20px; border-top: 3px solid var(--pg-navy); border-radius: 12px; text-align: left; box-shadow: 0 1px 2px rgba(15, 23, 42, .05); }
                .portal-page[data-portal-role='consignee'] button.portal-card { font: inherit; color: inherit; cursor: pointer; }
                .portal-page[data-portal-role='consignee'] button.portal-card:hover { border-color: #cbd5e1; box-shadow: 0 4px 12px rgba(15, 23, 42, .08); }
                .portal-page[data-portal-role='consignee'] .portal-card:nth-child(2) { border-top-color: #22c55e; }
                .portal-page[data-portal-role='consignee'] .portal-card:nth-child(3) { border-top-color: #64748b; }
                .portal-page[data-portal-role='consignee'] .portal-card:nth-child(4) { border-top-color: #d97706; }
                .portal-page[data-portal-role='consignee'] .portal-card span { font-size: 12px; font-weight: 600; letter-spacing: .05em; text-transform: uppercase; }
                .portal-page[data-portal-role='consignee'] .portal-card strong { margin-top: 8px; font-size: 32px; line-height: 1.1; font-variant-numeric: tabular-nums; }

                .portal-page[data-portal-role='consignee'] .portal-note { display: flex; align-items: flex-start; gap: 10px; margin: 0 0 12px; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--pg-line); border-left: 3px solid #94a3b8; background: var(--pg-soft); font-size: 13px; line-height: 1.45; color: #475569; }
                .portal-page[data-portal-role='consignee'] .portal-note::before { content: ""; flex: 0 0 16px; width: 16px; height: 16px; margin-top: 1px; background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16' fill='none'%3E%3Ccircle cx='8' cy='8' r='6.25' stroke='%2364748b' stroke-width='1.4'/%3E%3Cpath d='M8 7.2V11' stroke='%2364748b' stroke-width='1.4' stroke-linecap='round'/%3E%3Ccircle cx='8' cy='5.1' r='.7' fill='%2364748b'/%3E%3C/svg%3E") center / 16px no-repeat; }
                .portal-page[data-portal-role='consignee'] .portal-state { border-radius: 8px; }

                .portal-page[data-portal-role='consignee'] .portal-search { align-items: stretch; margin-bottom: 12px; }
                .portal-page[data-portal-role='consignee'] .portal-search-field { position: relative; flex: 1; min-width: 0; }
                .portal-page[data-portal-role='consignee'] .portal-search-icon { position: absolute; left: 12px; top: 50%; width: 16px; height: 16px; margin-top: -8px; color: #94a3b8; pointer-events: none; }
                .portal-page[data-portal-role='consignee'] .portal-search input { box-sizing: border-box; width: 100%; height: 40px; padding: 0 36px 0 36px; border: 1px solid #cbd5e1; border-radius: 8px; font-size: 14px; background: #fff; }
                .portal-page[data-portal-role='consignee'] .portal-search input:focus-visible { outline: none; border-color: var(--pg-navy); box-shadow: 0 0 0 3px rgba(44, 62, 80, .15); }
                .portal-page[data-portal-role='consignee'] .portal-search-clear { position: absolute; right: 4px; top: 4px; width: 32px; height: 32px; border: 0; border-radius: 6px; background: transparent; color: #64748b; font-size: 18px; line-height: 1; cursor: pointer; }
                .portal-page[data-portal-role='consignee'] .portal-search-field:has(input:placeholder-shown) .portal-search-clear { visibility: hidden; }
                .portal-page[data-portal-role='consignee'] .portal-search button[type='submit'] { box-sizing: border-box; height: 40px; padding: 0 18px; border: 1px solid var(--pg-navy); border-radius: 8px; background: var(--pg-navy); color: #fff; font-size: 14px; font-weight: 600; cursor: pointer; }
                .portal-page[data-portal-role='consignee'] .portal-search button[type='submit']:hover { background: #1f2d3a; }

                .portal-page[data-portal-role='consignee'] .portal-table-wrap { border-radius: 10px; }
                .portal-page[data-portal-role='consignee'] .portal-table { min-width: 760px; font-size: 14px; }
                .portal-page[data-portal-role='consignee'] .portal-table th,
                .portal-page[data-portal-role='consignee'] .portal-table td { padding: 10px 14px; vertical-align: middle; }
                .portal-page[data-portal-role='consignee'] .portal-table th { background: #f1f5f9; color: #334155; font-size: 11.5px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; white-space: nowrap; }
                .portal-page[data-portal-role='consignee'] .portal-table tbody tr:last-child td { border-bottom: 0; }
                .portal-page[data-portal-role='consignee'] .portal-table tbody tr:hover { background: var(--pg-soft); }
                .portal-page[data-portal-role='consignee'] .portal-missing { color: var(--pg-muted); font-weight: 400; white-space: nowrap; }
                .portal-page[data-portal-role='consignee'] .portal-id { font-weight: 600; white-space: nowrap; font-variant-numeric: tabular-nums; }
                .portal-page[data-portal-role='consignee'] .portal-nowrap { white-space: nowrap; font-variant-numeric: tabular-nums; }
                .portal-page[data-portal-role='consignee'] .portal-table--cars td:nth-child(2) { font-weight: 600; color: var(--pg-ink); }
                .portal-page[data-portal-role='consignee'] .portal-table--cars { min-width: 820px; }
                .portal-page[data-portal-role='consignee'] .portal-table--shipments { min-width: 1120px; }
                .portal-page[data-portal-role='consignee'] .portal-col-vessel { min-width: 88px; white-space: normal; }
                .portal-page[data-portal-role='consignee'] .portal-badge { padding: 3px 10px; font-size: 12px; font-weight: 600; line-height: 1.3; white-space: nowrap; color: #475569; background: #e2e8f0; }
                .portal-page[data-portal-role='consignee'] .portal-badge.shipped { background: #dcfce7; color: #166534; }
                .portal-page[data-portal-role='consignee'] .portal-pill { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; line-height: 1.3; white-space: nowrap; background: #e2e8f0; color: #475569; }
                .portal-page[data-portal-role='consignee'] .portal-pill--booked { background: #fef3c7; color: #92400e; }
                .portal-page[data-portal-role='consignee'] .portal-pill--shipment { background: #dbeafe; color: #1e40af; }
                @media (max-width: 1100px) {
                    .portal-page[data-portal-role='consignee'] .portal-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); }
                }
                @media (max-width: 720px) {
                    .portal-page[data-portal-role='consignee'] .portal-top { flex-direction: row; flex-wrap: wrap; align-items: center; }
                    .portal-page[data-portal-role='consignee'] .portal-cards { grid-template-columns: 1fr; }
                    #content .portal-page[data-portal-role='consignee'] .portal-top h1 { font-size: 22px !important; line-height: 1.2 !important; }
                    #content .portal-page[data-portal-role='consignee'] .portal-nav-btn,
                    #content .portal-page[data-portal-role='consignee'] .portal-logout,
                    #content .portal-page[data-portal-role='consignee'] .portal-search button[type='submit'] { min-height: 40px; min-width: 0; height: 40px; padding: 0 14px; font-size: 14px; }
                }
                @media (max-width: 640px) {
                    .portal-page[data-portal-role='consignee'] .portal-nav { flex-wrap: nowrap; overflow-x: auto; padding-bottom: 4px; }
                    .portal-page[data-portal-role='consignee'] .portal-card strong { font-size: 28px; }
                    body.portal-consignee-view .page-title-bar h1 { font-size: 18px !important; }
                }
            </style>
            <header class="portal-top">
                <div>
                    <div class="portal-brand">Automan</div>
                    <h1>$title</h1>
                    <div id="portalAccount"></div>
                </div>
                <button id="portalLogoutBtn" class="portal-logout" type="button">Log out</button>
            </header>
            <nav class="portal-nav" aria-label="$title">$navHtml</nav>
            <main id="portalBody"><p class="portal-state" data-portal-state="loading">Loading…</p></main>
        </div>
    """.trimIndent()
    document.getElementById("portalLogoutBtn")?.addEventListener("click", { _: Event -> logout() })
    val buttons = document.querySelectorAll(".portal-nav-btn")
    val count = (buttons.length as Number).toInt()
    for (index in 0 until count) {
        val button = buttons.item(index) as? HTMLElement ?: continue
        button.addEventListener("click", { _: Event ->
            val target = button.getAttribute("data-portal-route") ?: return@addEventListener
            navigateToApp(target)
        })
    }
    if (role == "client" || role == "consignee") {
        val current = document.querySelector(".portal-nav-btn[aria-current='page']") as? HTMLElement
        val nav = current?.parentElement as? HTMLElement
        if (current != null && nav != null) {
            val left = current.offsetLeft - 8
            nav.scrollLeft = if (left > 0) left.toDouble() else 0.0
        }
    }
    return generation
}

private fun renderPortalAccount(generation: Int, label: String) {
    if (generation != portalGeneration) return
    val account = document.getElementById("portalAccount")
    account?.textContent = label
}

private fun renderClientIdentity(generation: Int, name: String) {
    if (generation != portalGeneration) return
    val account = document.getElementById("portalAccount") ?: return
    account.innerHTML = if (name.isBlank()) "" else """<span class="portal-account-name">${escapeHtml(name)}</span>"""
}

private fun renderPortalWaiting(generation: Int, message: String) {
    renderState(generation, "waiting", "Access not assigned", message)
}

private fun renderPortalInactive(generation: Int, message: String) {
    renderState(generation, "inactive", "Account inactive", message)
}

internal fun renderPortalFailure(generation: Int, message: String, statusCode: Int) {
    if (statusCode == 401) return
    renderState(generation, "error", "Could not load this page", message.ifBlank { "The request failed." })
}

private fun renderState(generation: Int, state: String, title: String, message: String) {
    if (generation != portalGeneration) return
    val body = document.getElementById("portalBody") ?: return
    body.innerHTML = """
        <section class="portal-state" data-portal-state="$state">
            <h2>${escapeHtml(title)}</h2>
            <p>${escapeHtml(message)}</p>
        </section>
    """.trimIndent()
}

private suspend fun fetchPortal(endpoint: String): ApiResult<dynamic> = ApiClient.request(endpoint)

private fun renderClientDashboard(generation: Int, result: ApiResult<dynamic>) {
    if (generation != portalGeneration) return
    val data = result.asDynamicOrNull() ?: return renderPortalFailure(generation, result.errorMessage(), result.errorStatus())
    val purchased = jsCount(data.purchasedCount)
    val shipped = jsCount(data.shippedCount)
    val invoiced = jsCount(data.invoicedCount)
    setPortalBody(generation, portalDashboardHtml(purchased, shipped, invoiced, null, linkClientPages = true))
    val cards = document.querySelectorAll("#portalBody .portal-card-link")
    val count = (cards.length as Number).toInt()
    for (index in 0 until count) {
        val card = cards.item(index) as? HTMLElement ?: continue
        card.addEventListener("click", { _: Event ->
            val target = card.getAttribute("data-portal-route") ?: return@addEventListener
            navigateToApp(target)
        })
    }
}

private fun renderConsigneeDashboard(generation: Int, result: ApiResult<dynamic>) {
    if (generation != portalGeneration) return
    val data = result.asDynamicOrNull() ?: return renderPortalFailure(generation, result.errorMessage(), result.errorStatus())
    setPortalBody(
        generation,
        portalDashboardHtml(
            jsCount(data.purchasedCount),
            jsCount(data.shippedCount),
            jsCount(data.invoicedCount),
            jsCount(data.clientCount),
        ),
    )
    val shipped = document.querySelectorAll("#portalBody .portal-card").item(1) as? HTMLElement
    if (shipped != null) {
        val button = document.createElement("button")
        button.className = "portal-card portal-card-link"
        button.setAttribute("type", "button")
        button.setAttribute("data-portal-route", "/portal/consignee/shipped")
        button.innerHTML = shipped.innerHTML
        shipped.replaceWith(button)
        button.addEventListener("click", { _: Event -> navigateToApp("/portal/consignee/shipped") })
    }
}

fun portalDashboardHtml(
    purchased: Int,
    shipped: Int,
    invoiced: Int,
    clientCount: Int?,
    linkClientPages: Boolean = false,
): String {
    val clients = if (clientCount == null) "" else portalCard("Assigned clients", clientCount.toString())
    val purchasedCard = if (linkClientPages) {
        portalLinkCard("Purchased cars", purchased.toString(), "/portal/client/cars")
    } else {
        portalCard("Purchased cars", purchased.toString())
    }
    val invoicedCard = if (linkClientPages) {
        portalLinkCard("Invoiced cars", invoiced.toString(), "/portal/client/invoices")
    } else {
        portalCard("Invoiced cars", invoiced.toString())
    }
    return """
        <section class="portal-cards" data-portal-state="ready">
            $purchasedCard
            ${portalCard("Shipped cars", shipped.toString())}
            $invoicedCard
            $clients
        </section>
    """.trimIndent()
}

private fun portalCard(label: String, value: String) =
    """<article class="portal-card"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></article>"""

private fun portalLinkCard(label: String, value: String, route: String) =
    """<button type="button" class="portal-card portal-card-link" data-portal-route="$route"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></button>"""

private fun renderClientCars(generation: Int, result: ApiResult<dynamic>, query: String) {
    renderPortalCars(generation, result, query, "No purchased cars yet.", "cars")
}

private fun renderPortalCars(generation: Int, result: ApiResult<dynamic>, query: String, emptyMessage: String, pageKey: String) {
    if (generation != portalGeneration) return
    val data = result.asDynamicOrNull() ?: return renderPortalFailure(generation, result.errorMessage(), result.errorStatus())
    val rows = jsArray(data).map { item ->
        PortalCarRow(
            chassis = jsText(item.chassis),
            carName = jsText(item.carName),
            year = jsText(item.year),
            stockLocation = jsText(item.stockLocation),
            country = jsText(item.country),
            shipped = item.shipped == true,
        )
    }
    setPortalBody(generation, portalCarSearchHtml(query) + portalCarsTableHtml(rows, emptyMessage))
    val form = document.getElementById("portalCarSearch")
    form?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        val value = (document.getElementById("portalCarQuery") as? HTMLInputElement)?.value?.trim().orEmpty()
        val route = when (pageKey) {
            "not-shipped" -> "/portal/consignee/not-shipped"
            "shipped" -> "/portal/consignee/shipped"
            else -> "/portal/client/cars"
        }
        val encoded = js("encodeURIComponent")(value).unsafeCast<String>()
        navigateToApp(if (value.isEmpty()) route else "$route?q=$encoded")
    })
    document.getElementById("portalCarSearchClear")?.addEventListener("click", { _: Event ->
        val route = when (pageKey) {
            "not-shipped" -> "/portal/consignee/not-shipped"
            "shipped" -> "/portal/consignee/shipped"
            else -> "/portal/client/cars"
        }
        navigateToApp("$route?q=")
    })
}

fun portalCarsTableHtml(rows: List<PortalCarRow>, emptyMessage: String): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>${escapeHtml(emptyMessage)}</p></section>"""
    val body = rows.joinToString("") { row ->
        val status = if (row.shipped) """<span class="portal-badge shipped">Shipped</span>""" else """<span class="portal-badge">Not shipped</span>"""
        """
            <tr>
                <td data-label="Chassis"><span class="portal-id">${portalText(row.chassis)}</span></td>
                <td data-label="Car">${portalText(row.carName)}</td>
                <td data-label="Year"><span class="portal-nowrap">${portalText(row.year)}</span></td>
                <td data-label="Stock location">${portalText(row.stockLocation)}</td>
                <td data-label="Country">${portalText(row.country)}</td>
                <td data-label="Status">$status</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table portal-table--cars">
                <thead><tr><th>Chassis</th><th>Car</th><th>Year</th><th>Stock location</th><th>Country</th><th>Status</th></tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun portalCarSearchHtml(query: String) = """
    <form id="portalCarSearch" class="portal-search">
        <div class="portal-search-field">
            <svg class="portal-search-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                <circle cx="7" cy="7" r="4.25" fill="none" stroke="currentColor" stroke-width="1.4"></circle>
                <path d="M10.2 10.2L13 13" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"></path>
            </svg>
            <input id="portalCarQuery" type="search" name="q" value="${escapeHtml(query)}" placeholder="Search chassis" aria-label="Search chassis">
            <button id="portalCarSearchClear" class="portal-search-clear" type="button" aria-label="Clear search">×</button>
        </div>
        <button type="submit">Search</button>
    </form>
""".trimIndent()

private fun renderClientShipments(generation: Int, result: ApiResult<dynamic>) {
    renderShipmentTable(
        generation,
        result,
        includeClients = false,
        emptyMessage = "No shipments yet.",
        note = "",
    )
}

private fun renderConsigneeShipments(generation: Int, result: ApiResult<dynamic>, emptyMessage: String, note: String) {
    renderShipmentTable(generation, result, includeClients = true, emptyMessage = emptyMessage, note = note)
}

private fun renderShipmentTable(
    generation: Int,
    result: ApiResult<dynamic>,
    includeClients: Boolean,
    emptyMessage: String,
    note: String,
) {
    if (generation != portalGeneration) return
    val data = result.asDynamicOrNull() ?: return renderPortalFailure(generation, result.errorMessage(), result.errorStatus())
    val rows = jsArray(data).map { item ->
        PortalShipmentRow(
            bookingId = jsText(item.bookingId),
            vessel = jsText(item.vessel),
            pol = jsText(item.pol),
            pod = jsText(item.pod),
            shipmentDate = jsText(item.shipmentDate),
            cyCutDate = jsText(item.cyCutDate),
            eta = jsText(item.eta),
            chassis = jsStringList(item.chassis),
            clientNames = if (includeClients) jsStringList(item.clientNames) else emptyList(),
        )
    }
    val noteHtml = if (note.isBlank()) "" else """<p class="portal-note">${escapeHtml(note)}</p>"""
    setPortalBody(generation, noteHtml + portalShipmentsTableHtml(rows, emptyMessage, includeClients))
}

fun portalShipmentsTableHtml(rows: List<PortalShipmentRow>, emptyMessage: String, includeClients: Boolean): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>${escapeHtml(emptyMessage)}</p></section>"""
    val clientHeader = if (includeClients) "<th>Client</th>" else ""
    val body = rows.joinToString("") { row ->
        val clients = if (!includeClients) "" else "<td>${portalText(row.clientNames.joinToString(", "))}</td>"
        val booked = row.vessel.isBlank() && row.shipmentDate.isBlank() && row.eta.isBlank() && row.cyCutDate.isBlank()
        val kind = if (booked) "Booked, not yet shipped" else "Shipment row"
        val kindClass = if (booked) "portal-pill--booked" else "portal-pill--shipment"
        """
            <tr>
                <td><span class="portal-nowrap">${portalText(row.bookingId)}</span></td>
                <td><span class="portal-pill $kindClass">${escapeHtml(kind)}</span></td>
                <td class="portal-col-vessel">${portalText(row.vessel)}</td>
                <td><span class="portal-nowrap">${portalText(row.pol)}</span></td>
                <td><span class="portal-nowrap">${portalText(row.pod)}</span></td>
                <td><span class="portal-nowrap">${portalText(row.shipmentDate)}</span></td>
                <td><span class="portal-nowrap">${portalText(row.cyCutDate)}</span></td>
                <td><span class="portal-nowrap">${portalText(row.eta)}</span></td>
                <td>${portalChassisList(row.chassis)}</td>
                $clients
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table portal-table--shipments">
                <thead><tr><th>Booking ID</th><th>Record</th><th>Vessel</th><th>POL</th><th>POD</th><th>Shipment date</th><th>CY cut</th><th>ETA</th><th>Chassis</th>$clientHeader</tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun renderClientInvoices(generation: Int, result: ApiResult<dynamic>) {
    if (generation != portalGeneration) return
    val data = result.asDynamicOrNull() ?: return renderPortalFailure(generation, result.errorMessage(), result.errorStatus())
    val rows = jsArray(data).map { item ->
        PortalInvoiceRow(
            invoiceNumber = jsText(item.invoiceNumber),
            date = jsText(item.date),
            vessel = jsText(item.vessel),
            chassis = jsStringList(item.chassis),
            priceType = jsText(item.priceType),
        )
    }
    setPortalBody(generation, portalInvoicesTableHtml(rows))
}

fun portalInvoicesTableHtml(rows: List<PortalInvoiceRow>): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>No invoices yet.</p></section>"""
    val body = rows.joinToString("") { row ->
        """
            <tr>
                <td><span class="portal-id">${portalText(row.invoiceNumber)}</span></td>
                <td><span class="portal-nowrap">${portalText(row.date)}</span></td>
                <td>${portalText(row.vessel)}</td>
                <td>${portalChassisList(row.chassis)}</td>
                <td>${portalPriceType(row.priceType)}</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table portal-table--invoices">
                <thead><tr><th>Invoice</th><th>Date</th><th>Vessel</th><th>Chassis</th><th>Price type</th></tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

fun portalTransactionsTableHtml(rows: List<PortalTransactionRow>): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>No transactions yet.</p></section>"""
    val body = rows.joinToString("") { row ->
        """
            <tr>
                <td><span class="portal-nowrap">${portalText(row.date)}</span></td>
                <td>${portalTransactionType(row.type)}</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table portal-table--transactions">
                <thead><tr><th class="portal-col-date">Date</th><th>Type</th></tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

internal fun setPortalBody(generation: Int, html: String) {
    if (generation != portalGeneration) return
    val body = document.getElementById("portalBody") ?: return
    body.innerHTML = html
}

private fun portalText(value: String): String =
    if (value.isBlank()) """<span class="portal-missing">Not recorded</span>""" else escapeHtml(value)

/** Keeps each identifier on one line and wraps only between identifiers. */
private fun portalChassisList(values: List<String>): String =
    if (values.isEmpty()) portalText("") else values.joinToString(", ") { """<span class="portal-id">${escapeHtml(it)}</span>""" }

/** Same text as before, only wrapped in a pill. The class is chosen from the text and never adds data. */
private fun portalPriceType(value: String): String {
    if (value.isBlank()) return portalText("")
    val key = value.filter { it.isLetterOrDigit() }.lowercase()
    val extra = if (key == "cf" || key == "fob") " portal-pill--$key" else ""
    return """<span class="portal-pill$extra">${escapeHtml(value)}</span>"""
}

private fun portalTransactionType(value: String): String {
    if (value.isBlank()) return portalText("")
    val upper = value.uppercase()
    val extra = when {
        upper.contains("REVERSAL") -> " portal-pill--reversal"
        upper.contains("ISSUED") -> " portal-pill--issued"
        else -> ""
    }
    return """<span class="portal-pill$extra">${escapeHtml(value)}</span>"""
}

private fun portalQuery(): String {
    val params = js("new URLSearchParams(window.location.search)").unsafeCast<dynamic>()
    val value = params.get("q")
    if (value == null || value == js("undefined")) return ""
    return value.toString().trim()
}

private fun portalQuerySuffix(query: String): String {
    if (query.isEmpty()) return ""
    val encoded = js("encodeURIComponent")(query).unsafeCast<String>()
    return "?q=$encoded"
}

internal fun currentPortalGeneration(): Int = portalGeneration

internal fun launchPortal(block: suspend () -> Unit) {
    portalScope.launch { block() }
}

internal fun jsText(value: dynamic): String {
    if (value == null || value == js("undefined")) return ""
    return value.toString().trim()
}

private fun jsCount(value: dynamic): Int = (value as? Number)?.toInt() ?: 0

internal fun jsArray(value: dynamic): List<dynamic> {
    if (value == null || value == js("undefined")) return emptyList()
    val length = (value.length as? Number)?.toInt() ?: return emptyList()
    return (0 until length).map { value[it] }
}

private fun jsStringList(value: dynamic): List<String> = jsArray(value).map { jsText(it) }.filter { it.isNotEmpty() }

private fun ApiResult<dynamic>.asDynamicOrNull(): dynamic? = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Error -> null
}

private fun ApiResult<dynamic>.errorMessage(): String = (this as? ApiResult.Error)?.message.orEmpty()

private fun ApiResult<dynamic>.errorStatus(): Int = (this as? ApiResult.Error)?.statusCode ?: 0
