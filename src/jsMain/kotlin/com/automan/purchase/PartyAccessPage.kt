package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.fetch.RequestInit

fun showPartyAccessPage() {
    val content = document.getElementById("content")!!
    if (!isAdmin()) {
        content.innerHTML = """
            <div class="user-page-container">
                <div class="user-page-card" role="alert">You do not have permission to manage party access.</div>
            </div>
        """.trimIndent()
        return
    }
    content.innerHTML = """
        <div class="user-page-container">
            <div class="user-page-card">
                <div class="user-page-header">
                    <h2>Party access</h2>
                </div>
                <p id="partyAccessStatus" class="user-status-block" role="status">Loading party access…</p>
                <section>
                    <h3>Consignee records</h3>
                    <p>Create a consignee by its legal name. Similar names stay separate records.</p>
                    <div style="display:flex; gap:8px; flex-wrap:wrap; margin-bottom:12px;">
                        <input id="partyConsigneeName" type="text" maxlength="255" placeholder="Legal name" style="flex:1; min-width:220px; padding:8px;">
                        <button id="partyCreateConsigneeBtn" class="user-btn user-btn-primary" type="button">Add consignee</button>
                    </div>
                    <div id="partyConsigneeList"></div>
                </section>
                <section style="margin-top:24px;">
                    <h3>Client consignee assignment</h3>
                    <p>Choose the client and consignee by id. This does not read names from purchases, invoices, or the client map.</p>
                    <input id="partyClientSearch" type="search" placeholder="Search client number, name, or id" style="width:100%; padding:8px; margin-bottom:8px;">
                    <div id="partyClientList"></div>
                </section>
                <section style="margin-top:24px;">
                    <h3>Portal links</h3>
                    <p>A client account links to one client. A consignee account links to one consignee. Revoke a link before creating a different one.</p>
                    <input id="partyUserSearch" type="search" placeholder="Search external user name, email, or id" style="width:100%; padding:8px; margin-bottom:8px;">
                    <div id="partyUserList"></div>
                    <h4>Link history</h4>
                    <div id="partyLinkHistory"><div class="user-status-block" role="status">Select an account to see its link history.</div></div>
                </section>
            </div>
        </div>
    """.trimIndent()

    document.getElementById("partyCreateConsigneeBtn")?.addEventListener("click", { _: dynamic ->
        val name = (document.getElementById("partyConsigneeName") as HTMLInputElement?)?.value.orEmpty()
        if (!window.confirm("Create a new consignee record for this legal name? It will not be assigned to any client.")) return@addEventListener
        val payload: dynamic = js("({})")
        payload.legalName = name
        partyRequest("POST", "admin/party-access/consignees", payload) { ok, body ->
            partyStatus(if (ok) "Consignee created." else partyMessage(body), ok)
            if (ok) loadPartyAccess()
        }
    })
    document.getElementById("partyClientSearch")?.addEventListener("input", { _: dynamic -> loadPartyClients() })
    document.getElementById("partyUserSearch")?.addEventListener("input", { _: dynamic -> loadPartyUsers() })
    loadPartyAccess()
}

private fun loadPartyAccess() {
    loadPartyConsignees()
    loadPartyClients()
    loadPartyUsers()
}

private var partyClientLoad = 0
private var partyUserLoad = 0
private var partyConsigneeLoad = 0

private fun loadPartyConsignees() {
    val generation = ++partyConsigneeLoad
    partyRequest("GET", "admin/party-access/consignees") { ok, body ->
        if (generation != partyConsigneeLoad) return@partyRequest
        val host = document.getElementById("partyConsigneeList") ?: return@partyRequest
        if (!ok) {
            host.innerHTML = """<div class="user-status-block" role="alert">${esc(partyMessage(body))}</div>"""
            return@partyRequest
        }
        val rows = body.unsafeCast<Array<dynamic>>()
        if (rows.isEmpty()) {
            host.innerHTML = """<div class="user-status-block" role="status">No consignee records yet.</div>"""
            return@partyRequest
        }
        host.innerHTML = rows.joinToString("") { row ->
            val id = row.id
            val status = row.status as String
            val next = if (status == "ACTIVE") "INACTIVE" else "ACTIVE"
            """
            <div style="display:flex; justify-content:space-between; gap:8px; padding:8px 0; border-bottom:1px solid #e5e7eb;">
                <span><strong>${esc(row.legalName as String)}</strong> · id ${esc("$id")} · ${esc(status)}</span>
                <button type="button" class="user-btn" data-consignee-id="$id" data-next-status="$next">Mark $next</button>
            </div>
            """.trimIndent()
        }
        host.querySelectorAll("[data-consignee-id]").toElements().forEach { button ->
            button.addEventListener("click", { _: dynamic ->
                val id = button.getAttribute("data-consignee-id")
                val next = button.getAttribute("data-next-status")
                if (!window.confirm("Change consignee $id to $next? Active portal links must be revoked before it can become inactive.")) return@addEventListener
                val payload: dynamic = js("({})")
                payload.status = next
                partyRequest("PUT", "admin/party-access/consignees/$id/status", payload) { ok, response ->
                    partyStatus(if (ok) "Consignee status updated." else partyMessage(response), ok)
                    if (ok) loadPartyAccess()
                }
            })
        }
    }
}

private fun loadPartyClients() {
    val generation = ++partyClientLoad
    val q = (document.getElementById("partyClientSearch") as HTMLInputElement?)?.value.orEmpty().trim()
    partyRequest("GET", "admin/party-access/clients?q=${encodeQuery(q)}") { ok, body ->
        if (generation != partyClientLoad) return@partyRequest
        val host = document.getElementById("partyClientList") ?: return@partyRequest
        if (!ok) {
            host.innerHTML = """<div class="user-status-block" role="alert">${esc(partyMessage(body))}</div>"""
            return@partyRequest
        }
        val rows = body.unsafeCast<Array<dynamic>>()
        if (rows.isEmpty()) {
            host.innerHTML = """<div class="user-status-block" role="status">No clients match that search.</div>"""
            return@partyRequest
        }
        host.innerHTML = rows.joinToString("") { row ->
            val assigned = row.consigneeId
            val label = if (assigned == null) {
                "Unassigned"
            } else {
                "${row.consigneeLegalName} · id $assigned · ${row.consigneeStatus}"
            }
            """
            <div style="padding:10px 0; border-bottom:1px solid #e5e7eb;">
                <div><strong>${esc(row.clientNumber as String)}</strong> ${esc(row.clientName as String)} · client id ${esc("${row.clientId}")}</div>
                <div>Consignee: ${esc(label)}</div>
                <div style="display:flex; gap:8px; flex-wrap:wrap; margin-top:6px;">
                    <input data-assign-input="${row.clientId}" type="text" inputmode="numeric" placeholder="Consignee id" style="padding:6px; width:140px;">
                    <button type="button" class="user-btn user-btn-primary" data-assign-client="${row.clientId}">Assign</button>
                    <button type="button" class="user-btn" data-clear-client="${row.clientId}">Clear</button>
                </div>
            </div>
            """.trimIndent()
        }
        host.querySelectorAll("[data-assign-client]").toElements().forEach { button ->
            button.addEventListener("click", { _: dynamic ->
                val clientId = button.getAttribute("data-assign-client")
                val input = button.parentElement?.querySelector("input") as HTMLInputElement?
                val consigneeId = input?.value?.trim().orEmpty()
                if (consigneeId.isEmpty()) {
                    partyStatus("Enter the consignee id to assign.", false)
                    return@addEventListener
                }
                if (!window.confirm("Assign client $clientId to consignee $consigneeId? Consignee users on the old and new records will be signed out. Links are not moved.")) return@addEventListener
                partyRequest("PUT", "admin/party-access/clients/$clientId/consignee", assignPayload(consigneeId)) { ok, response ->
                    partyStatus(if (ok) "Consignee assignment saved." else partyMessage(response), ok)
                    if (ok) loadPartyClients()
                }
            })
        }
        host.querySelectorAll("[data-clear-client]").toElements().forEach { button ->
            button.addEventListener("click", { _: dynamic ->
                val clientId = button.getAttribute("data-clear-client")
                if (!window.confirm("Clear the consignee for client $clientId? This is refused while a client portal link is active. Affected consignee users will be signed out.")) return@addEventListener
                val payload: dynamic = js("({})")
                payload.consigneeId = null
                partyRequest("PUT", "admin/party-access/clients/$clientId/consignee", payload) { ok, response ->
                    partyStatus(if (ok) "Consignee assignment cleared." else partyMessage(response), ok)
                    if (ok) loadPartyClients()
                }
            })
        }
    }
}

private fun loadPartyUsers() {
    val generation = ++partyUserLoad
    val q = (document.getElementById("partyUserSearch") as HTMLInputElement?)?.value.orEmpty().trim()
    partyRequest("GET", "admin/party-access/users?q=${encodeQuery(q)}") { ok, body ->
        if (generation != partyUserLoad) return@partyRequest
        val host = document.getElementById("partyUserList") ?: return@partyRequest
        if (!ok) {
            host.innerHTML = """<div class="user-status-block" role="alert">${esc(partyMessage(body))}</div>"""
            partyStatus(partyMessage(body), false)
            return@partyRequest
        }
        val rows = body.unsafeCast<Array<dynamic>>()
        if (rows.isEmpty()) {
            host.innerHTML = """<div class="user-status-block" role="status">No client or consignee accounts match that search.</div>"""
            return@partyRequest
        }
        host.innerHTML = rows.joinToString("") { row ->
            val active = row.activeLink
            val summary = if (active == null) {
                "No active link"
            } else {
                "Active ${active.partyType} ${active.partyName ?: ""} · party id ${active.partyId}"
            }
            val revoke = if (active == null) "" else """<button type="button" class="user-btn" data-revoke-link="${active.id}">Revoke</button>"""
            """
            <div style="padding:10px 0; border-bottom:1px solid #e5e7eb;">
                <div><strong>${esc(row.name as String)}</strong> · ${esc(row.email as String)} · ${esc(row.role as String)} · user id ${esc("${row.userId}")}</div>
                <div>${esc(summary)}</div>
                <div style="display:flex; gap:8px; flex-wrap:wrap; margin-top:6px;">
                    <input data-link-input="${row.userId}" type="text" inputmode="numeric" placeholder="Party id" style="padding:6px; width:120px;">
                    <button type="button" class="user-btn user-btn-primary" data-link-user="${row.userId}" data-link-role="${row.role}">Create link</button>
                    $revoke
                    <button type="button" class="user-btn" data-history-user="${row.userId}">History</button>
                </div>
            </div>
            """.trimIndent()
        }
        host.querySelectorAll("[data-link-user]").toElements().forEach { button ->
            button.addEventListener("click", { _: dynamic ->
                val userId = button.getAttribute("data-link-user")
                val role = button.getAttribute("data-link-role")
                val partyId = (button.parentElement?.querySelector("input") as HTMLInputElement?)?.value?.trim().orEmpty()
                if (partyId.isEmpty()) {
                    partyStatus("Enter the client or consignee id.", false)
                    return@addEventListener
                }
                if (!window.confirm("Create an active $role link from user $userId to party $partyId? The user will be signed out. This does not replace an existing active link.")) return@addEventListener
                val payload: dynamic = js("({})")
                payload.userId = userId?.toDouble()
                payload.partyType = role
                payload.partyId = partyId.toDouble()
                partyRequest("POST", "admin/party-access/links", payload) { ok, response ->
                    partyStatus(if (ok) "Link created." else partyMessage(response), ok)
                    if (ok) {
                        loadPartyUsers()
                        loadPartyHistory(userId ?: "")
                    }
                }
            })
        }
        host.querySelectorAll("[data-revoke-link]").toElements().forEach { button ->
            button.addEventListener("click", { _: dynamic ->
                val linkId = button.getAttribute("data-revoke-link")
                if (!window.confirm("Revoke link $linkId? The account will be signed out. The revoked row stays in the history.")) return@addEventListener
                partyRequest("POST", "admin/party-access/links/$linkId/revoke", null) { ok, response ->
                    partyStatus(if (ok) "Link revoked." else partyMessage(response), ok)
                    if (ok) loadPartyUsers()
                }
            })
        }
        host.querySelectorAll("[data-history-user]").toElements().forEach { button ->
            button.addEventListener("click", { _: dynamic ->
                loadPartyHistory(button.getAttribute("data-history-user") ?: "")
            })
        }
    }
}

private fun loadPartyHistory(userId: String) {
    val host = document.getElementById("partyLinkHistory") ?: return
    host.innerHTML = """<div class="user-status-block" role="status">Loading link history…</div>"""
    partyRequest("GET", "admin/party-access/users/$userId/links") { ok, body ->
        if (!ok) {
            host.innerHTML = """<div class="user-status-block" role="alert">${esc(partyMessage(body))}</div>"""
            return@partyRequest
        }
        val rows = body.unsafeCast<Array<dynamic>>()
        if (rows.isEmpty()) {
            host.innerHTML = """<div class="user-status-block" role="status">No links have been created for this account.</div>"""
            return@partyRequest
        }
        host.innerHTML = rows.joinToString("") { row ->
            val revoked = row.revokedAt ?: "—"
            """
            <div style="padding:8px 0; border-bottom:1px solid #e5e7eb;">
                ${esc(row.status as String)} ${esc(row.partyType as String)} ${esc((row.partyName ?: "") as String)} · party id ${esc("${row.partyId}")}
                · activated by user ${esc("${row.linkedByUserId}")} at ${esc(row.createdAt as String)} · revoked at ${esc("$revoked")}
            </div>
            """.trimIndent()
        }
    }
}

private fun partyRequest(method: String, path: String, body: dynamic = null, done: (Boolean, dynamic) -> Unit) {
    val init = js("{}").unsafeCast<RequestInit>()
    init.method = method
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    val token = safeLocalStorageGet("authToken")
    if (!token.isNullOrBlank()) headers["Authorization"] = "Bearer $token"
    init.headers = headers
    if (body != null) init.body = JSON.stringify(body)
    window.fetch(apiUrl(path), init).then { response ->
        response.text().then { text ->
            val parsed = if (text.isNullOrBlank()) js("null") else JSON.parse(text)
            done(response.ok, parsed)
        }
    }.catch { error ->
        done(false, js("{message: 'The request could not be completed.'}"))
        console.log(error)
    }
}

private fun partyStatus(message: String, ok: Boolean) {
    val node = document.getElementById("partyAccessStatus") ?: return
    if (message.isBlank()) {
        node.textContent = ""
        return
    }
    node.textContent = message
    node.setAttribute("role", if (ok) "status" else "alert")
}

private fun partyMessage(body: dynamic): String {
    val message = body?.message as? String
    return if (message.isNullOrBlank()) "The request could not be completed." else message
}

private fun esc(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun assignPayload(consigneeId: String): dynamic {
    val payload: dynamic = js("({})")
    payload.consigneeId = consigneeId.toDouble()
    return payload
}

private fun encodeQuery(value: String): String = window.asDynamic().encodeURIComponent(value) as String

private fun org.w3c.dom.NodeList.toElements(): List<HTMLElement> {
    val items = mutableListOf<HTMLElement>()
    for (index in 0 until length) {
        items.add(item(index) as HTMLElement)
    }
    return items
}
