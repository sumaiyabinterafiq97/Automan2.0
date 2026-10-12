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
    document.getElementById("sidebar")?.removeAttribute("hidden")
    document.getElementById("hamburgerBtnContainer")?.removeAttribute("hidden")
    document.getElementById("sidebarOverlay")?.removeAttribute("hidden")
}

fun showClientPortal(route: String) {
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
                renderPortalAccount(generation, listOf(jsText(account.clientName), jsText(account.clientNumber)).filter { it.isNotEmpty() }.joinToString(" · "))
                loadClientPage(generation, page)
            }
        }
    }
}

fun showConsigneePortal(route: String) {
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

private suspend fun loadClientPage(generation: Int, page: String) {
    try {
        when (page) {
            "cars" -> {
                val query = portalQuery()
                renderClientCars(generation, fetchPortal("portal/client/cars${portalQuerySuffix(query)}"), query)
            }
            "shipments" -> renderClientShipments(generation, fetchPortal("portal/client/shipments"))
            "invoices" -> renderClientInvoices(generation, fetchPortal("portal/client/invoices"))
            "transactions" -> renderClientTransactions(generation, fetchPortal("portal/client/transactions"))
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
                "Booked cars that are not yet on a shipment are listed separately from rows that are still inside the ETA window. A future or missing ETA does not confirm the vessel has sailed.",
            )
            "not-shipped" -> renderPortalCars(generation, fetchPortal("portal/consignee/cars/not-shipped${portalQuerySuffix(portalQuery())}"), portalQuery(), "No cars are waiting to ship.", "not-shipped")
            "shipped" -> renderPortalCars(generation, fetchPortal("portal/consignee/cars/shipped${portalQuerySuffix(portalQuery())}"), portalQuery(), "No shipped cars.", "shipped")
            "history" -> renderConsigneeShipments(
                generation,
                fetchPortal("portal/consignee/shipments/history"),
                "No shipment history.",
                "These rows are classified as history because their ETA is before today. An earlier ETA does not confirm that the vehicle was delivered.",
            )
            else -> renderConsigneeDashboard(generation, fetchPortal("portal/consignee/dashboard"))
        }
    } catch (e: dynamic) {
        renderPortalFailure(generation, "The request failed.", 0)
    }
}

private fun clientPortalPage(route: String): String = when {
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
    page,
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
    return generation
}

private fun renderPortalAccount(generation: Int, label: String) {
    if (generation != portalGeneration) return
    val account = document.getElementById("portalAccount")
    account?.textContent = label
}

private fun renderPortalWaiting(generation: Int, message: String) {
    renderState(generation, "waiting", "Access not assigned", message)
}

private fun renderPortalInactive(generation: Int, message: String) {
    renderState(generation, "inactive", "Account inactive", message)
}

private fun renderPortalFailure(generation: Int, message: String, statusCode: Int) {
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
    setPortalBody(generation, portalDashboardHtml(purchased, shipped, invoiced, null))
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
}

fun portalDashboardHtml(purchased: Int, shipped: Int, invoiced: Int, clientCount: Int?): String {
    val clients = if (clientCount == null) "" else portalCard("Assigned clients", clientCount.toString())
    return """
        <section class="portal-cards" data-portal-state="ready">
            ${portalCard("Purchased cars", purchased.toString())}
            ${portalCard("Shipped cars", shipped.toString())}
            ${portalCard("Invoiced cars", invoiced.toString())}
            $clients
        </section>
    """.trimIndent()
}

private fun portalCard(label: String, value: String) =
    """<article class="portal-card"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></article>"""

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
}

fun portalCarsTableHtml(rows: List<PortalCarRow>, emptyMessage: String): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>${escapeHtml(emptyMessage)}</p></section>"""
    val body = rows.joinToString("") { row ->
        val status = if (row.shipped) """<span class="portal-badge shipped">Shipped</span>""" else """<span class="portal-badge">Not shipped</span>"""
        """
            <tr>
                <td>${portalText(row.chassis)}</td>
                <td>${portalText(row.carName)}</td>
                <td>${portalText(row.year)}</td>
                <td>${portalText(row.stockLocation)}</td>
                <td>${portalText(row.country)}</td>
                <td>$status</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table">
                <thead><tr><th>Chassis</th><th>Car</th><th>Year</th><th>Stock location</th><th>Country</th><th>Status</th></tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun portalCarSearchHtml(query: String) = """
    <form id="portalCarSearch" class="portal-search">
        <input id="portalCarQuery" type="search" name="q" value="${escapeHtml(query)}" placeholder="Search chassis" aria-label="Search chassis">
        <button type="submit">Search</button>
    </form>
""".trimIndent()

private fun renderClientShipments(generation: Int, result: ApiResult<dynamic>) {
    renderShipmentTable(
        generation,
        result,
        includeClients = false,
        emptyMessage = "No shipments yet.",
        note = "Missing vessel names and dates are left blank. They are not filled in from another record.",
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
    setPortalBody(generation, """<p class="portal-note">${escapeHtml(note)}</p>""" + portalShipmentsTableHtml(rows, emptyMessage, includeClients))
}

fun portalShipmentsTableHtml(rows: List<PortalShipmentRow>, emptyMessage: String, includeClients: Boolean): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>${escapeHtml(emptyMessage)}</p></section>"""
    val clientHeader = if (includeClients) "<th>Client</th>" else ""
    val body = rows.joinToString("") { row ->
        val clients = if (!includeClients) "" else "<td>${portalText(row.clientNames.joinToString(", "))}</td>"
        val kind = if (row.vessel.isBlank() && row.shipmentDate.isBlank() && row.eta.isBlank() && row.cyCutDate.isBlank()) {
            "Booked, not yet shipped"
        } else {
            "Shipment row"
        }
        """
            <tr>
                <td>${portalText(row.bookingId)}</td>
                <td>${escapeHtml(kind)}</td>
                <td>${portalText(row.vessel)}</td>
                <td>${portalText(row.pol)}</td>
                <td>${portalText(row.pod)}</td>
                <td>${portalText(row.shipmentDate)}</td>
                <td>${portalText(row.cyCutDate)}</td>
                <td>${portalText(row.eta)}</td>
                <td>${portalText(row.chassis.joinToString(", "))}</td>
                $clients
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table">
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
                <td>${portalText(row.invoiceNumber)}</td>
                <td>${portalText(row.date)}</td>
                <td>${portalText(row.vessel)}</td>
                <td>${portalText(row.chassis.joinToString(", "))}</td>
                <td>${portalText(row.priceType)}</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table">
                <thead><tr><th>Invoice</th><th>Date</th><th>Vessel</th><th>Chassis</th><th>Price type</th></tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun renderClientTransactions(generation: Int, result: ApiResult<dynamic>) {
    if (generation != portalGeneration) return
    val data = result.asDynamicOrNull() ?: return renderPortalFailure(generation, result.errorMessage(), result.errorStatus())
    val rows = jsArray(data).map { item ->
        PortalTransactionRow(
            date = jsText(item.date),
            type = jsText(item.type).replace('_', ' '),
        )
    }
    setPortalBody(generation, portalTransactionsTableHtml(rows))
}

fun portalTransactionsTableHtml(rows: List<PortalTransactionRow>): String {
    if (rows.isEmpty()) return """<section class="portal-state" data-portal-state="empty"><p>No transactions yet.</p></section>"""
    val body = rows.joinToString("") { row ->
        """
            <tr>
                <td>${portalText(row.date)}</td>
                <td>${portalText(row.type)}</td>
            </tr>
        """.trimIndent()
    }
    return """
        <div class="portal-table-wrap">
            <table class="portal-table">
                <thead><tr><th>Date</th><th>Type</th></tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun setPortalBody(generation: Int, html: String) {
    if (generation != portalGeneration) return
    val body = document.getElementById("portalBody") ?: return
    body.innerHTML = html
}

private fun portalText(value: String): String =
    if (value.isBlank()) """<span class="portal-missing">Not recorded</span>""" else escapeHtml(value)

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

private fun jsText(value: dynamic): String {
    if (value == null || value == js("undefined")) return ""
    return value.toString().trim()
}

private fun jsCount(value: dynamic): Int = (value as? Number)?.toInt() ?: 0

private fun jsArray(value: dynamic): List<dynamic> {
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
