package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTableSectionElement
import org.w3c.dom.events.Event

/** sessionStorage: client+vessel when opening from Shipping History next to INVOICE. */
const val CLIENT_SHIPMENT_DETAILS_PREFILL_SESSION_KEY = "clientShipmentDetailsPrefillPayload"

private var clientShipmentCurrentClient: String = ""
private var clientShipmentCurrentVessel: String = ""
private var clientShipmentLineCount: Int = 0

fun showClientShipmentDetailsPage() {
    val content = document.getElementById("content") ?: return
    clientShipmentCurrentClient = ""
    clientShipmentCurrentVessel = ""
    clientShipmentLineCount = 0

    val prefillRaw = window.sessionStorage.getItem(CLIENT_SHIPMENT_DETAILS_PREFILL_SESSION_KEY)
    if (prefillRaw != null) {
        window.sessionStorage.removeItem(CLIENT_SHIPMENT_DETAILS_PREFILL_SESSION_KEY)
    }
    var prefillClient = ""
    var prefillVessel = ""
    if (!prefillRaw.isNullOrBlank()) {
        try {
            val o = JSON.parse<dynamic>(prefillRaw)
            prefillClient = o.clientName?.toString()?.trim().orEmpty()
            prefillVessel = o.vessel?.toString()?.trim().orEmpty()
        } catch (_: dynamic) {
            prefillClient = ""
            prefillVessel = ""
        }
    }

    content.innerHTML = """
        <div class="invoice-page-container">
            <div class="invoice-card">
                <div class="invoice-page-header">
                    <h1>Client-Based Shipment Details</h1>
                </div>

                <div class="invoice-layout">
                    <div class="invoice-form-section">
                        <div class="invoice-field">
                            <label for="clientShipmentClient">Client</label>
                            <select id="clientShipmentClient" class="invoice-select">
                                <option value="">Select client</option>
                            </select>
                        </div>
                        <div class="invoice-field">
                            <label for="clientShipmentVessel">Vessel</label>
                            <select id="clientShipmentVessel" class="invoice-select">
                                <option value="">Select vessel</option>
                            </select>
                            <p id="clientShipmentVesselEmptyHint" class="invoice-vessel-empty-hint" style="display:none;margin:6px 0 0;font-size:13px;color:#6c757d;">
                                No vessels for this client
                            </p>
                        </div>
                        <div class="invoice-actions" style="display:flex;gap:10px;flex-wrap:wrap;margin-top:16px;">
                            <button type="button" id="clientShipmentPreviewBtn" class="invoice-btn invoice-btn-secondary" disabled>Preview PDF</button>
                            <button type="button" id="clientShipmentDownloadBtn" class="invoice-btn invoice-btn-primary" disabled>Download PDF</button>
                        </div>
                    </div>

                    <div class="invoice-list-section">
                        <div class="invoice-list-header">
                            <h2 style="margin:0;font-size:16px;">Cars</h2>
                            <span id="clientShipmentCount" style="color:#64748b;font-size:13px;">0 units</span>
                        </div>
                        <div id="clientShipmentTableWrap" class="invoice-table-wrap" style="overflow-x:auto;">
                            <table class="purchase-list-table" style="width:100%;border-collapse:collapse;">
                                <thead>
                                    <tr>
                                        <th style="width:48px;">No</th>
                                        <th>Maker</th>
                                        <th>Model</th>
                                        <th>Chassis</th>
                                        <th style="width:72px;">Year</th>
                                    </tr>
                                </thead>
                                <tbody id="clientShipmentTableBody">
                                    <tr><td colspan="5" style="text-align:center;color:#64748b;padding:24px;">Select client and vessel</td></tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    """.trimIndent()

    setupClientShipmentDetailsListeners()
    loadClientShipmentClientOptions(preferredClient = prefillClient, preferredVessel = prefillVessel)
}

private fun setupClientShipmentDetailsListeners() {
    document.getElementById("clientShipmentClient")?.addEventListener("change", { _: Event ->
        val client = (document.getElementById("clientShipmentClient") as? HTMLSelectElement)?.value?.trim().orEmpty()
        clientShipmentCurrentClient = client
        clientShipmentCurrentVessel = ""
        clientShipmentLineCount = 0
        setClientShipmentPdfButtonsEnabled(false)
        clearClientShipmentTable("Select vessel")
        loadClientShipmentVesselOptions(client)
    })

    document.getElementById("clientShipmentVessel")?.addEventListener("change", { _: Event ->
        val client = clientShipmentCurrentClient
        val vessel = (document.getElementById("clientShipmentVessel") as? HTMLSelectElement)?.value?.trim().orEmpty()
        clientShipmentCurrentVessel = vessel
        if (client.isNotEmpty() && vessel.isNotEmpty()) {
            loadClientShipmentLines(client, vessel)
        } else {
            clientShipmentLineCount = 0
            setClientShipmentPdfButtonsEnabled(false)
            clearClientShipmentTable("Select vessel")
        }
    })

    document.getElementById("clientShipmentPreviewBtn")?.addEventListener("click", { _: Event ->
        fetchClientShipmentPdf(preview = true)
    })
    document.getElementById("clientShipmentDownloadBtn")?.addEventListener("click", { _: Event ->
        fetchClientShipmentPdf(preview = false)
    })
}

private fun setClientShipmentPdfButtonsEnabled(enabled: Boolean) {
    val preview = document.getElementById("clientShipmentPreviewBtn") as? org.w3c.dom.HTMLButtonElement
    val download = document.getElementById("clientShipmentDownloadBtn") as? org.w3c.dom.HTMLButtonElement
    preview?.disabled = !enabled
    download?.disabled = !enabled
}

private fun clearClientShipmentTable(message: String) {
    val tbody = document.getElementById("clientShipmentTableBody") as? HTMLTableSectionElement ?: return
    tbody.innerHTML = """<tr><td colspan="5" style="text-align:center;color:#64748b;padding:24px;">$message</td></tr>"""
    document.getElementById("clientShipmentCount")?.textContent = "0 units"
}

private fun loadClientShipmentClientOptions(preferredClient: String = "", preferredVessel: String = "") {
    window.fetch(apiUrl("shipping-history/for-shipment-details/client-names"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load client names')")
        }
        .then { payload: dynamic ->
            val namesRaw = js("payload && payload.success ? payload.data : []")
            val clientNames = js("Array.isArray(namesRaw) ? namesRaw : []") as Array<dynamic>
            val clientSelect = document.getElementById("clientShipmentClient") as? HTMLSelectElement ?: return@then
            while (clientSelect.options.length > 1) {
                clientSelect.remove(1)
            }
            val nameList = mutableListOf<String>()
            for (name in clientNames) {
                val n = name.toString().trim()
                if (n.isEmpty()) continue
                nameList.add(n)
                val option = document.createElement("option") as HTMLOptionElement
                option.value = n
                option.textContent = n
                clientSelect.appendChild(option)
            }

            val preferred = preferredClient.trim()
            if (preferred.isNotEmpty() && nameList.any { it == preferred }) {
                clientSelect.value = preferred
                clientShipmentCurrentClient = preferred
                loadClientShipmentVesselOptions(preferred, preferredVessel = preferredVessel)
            } else if (preferred.isNotEmpty()) {
                // Prefill client not in open-invoice list — still try vessels for that name.
                val option = document.createElement("option") as HTMLOptionElement
                option.value = preferred
                option.textContent = preferred
                clientSelect.appendChild(option)
                clientSelect.value = preferred
                clientShipmentCurrentClient = preferred
                loadClientShipmentVesselOptions(preferred, preferredVessel = preferredVessel)
            }
        }
        .catch { error: dynamic ->
            showMessage("Failed to load clients: $error", "error")
        }
}

private fun loadClientShipmentVesselOptions(client: String, preferredVessel: String = "") {
    val vesselSelect = document.getElementById("clientShipmentVessel") as? HTMLSelectElement ?: return
    vesselSelect.innerHTML = "<option value=\"\">Select vessel</option>"
    (document.getElementById("clientShipmentVesselEmptyHint") as? HTMLElement)?.style?.display = "none"
    if (client.isBlank()) return

    val encodedClient = js("encodeURIComponent")(client) as String
    window.fetch(apiUrl("shipping-history/for-shipment-details/vessels?clientName=$encodedClient"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load vessels')")
        }
        .then { payload: dynamic ->
            val namesRaw = js("payload && payload.success ? payload.data : []")
            val vessels = js("Array.isArray(namesRaw) ? namesRaw : []") as Array<dynamic>
            val vesselStrings = mutableListOf<String>()
            for (v in vessels) {
                val vesselName = v.toString().trim()
                if (vesselName.isEmpty()) continue
                vesselStrings.add(vesselName)
                val option = document.createElement("option") as HTMLOptionElement
                option.value = vesselName
                option.textContent = vesselName
                vesselSelect.appendChild(option)
            }
            val emptyHint = document.getElementById("clientShipmentVesselEmptyHint") as? HTMLElement
            if (emptyHint != null) {
                emptyHint.style.display = if (vesselStrings.isEmpty()) "block" else "none"
            }

            val preferred = preferredVessel.trim()
            var selected = ""
            when {
                preferred.isNotEmpty() && vesselStrings.any { it == preferred } -> {
                    vesselSelect.value = preferred
                    selected = preferred
                }
                preferred.isNotEmpty() -> {
                    val option = document.createElement("option") as HTMLOptionElement
                    option.value = preferred
                    option.textContent = preferred
                    vesselSelect.appendChild(option)
                    vesselSelect.value = preferred
                    selected = preferred
                }
                vesselStrings.size == 1 -> {
                    vesselSelect.selectedIndex = 1
                    selected = vesselSelect.value
                }
            }

            if (selected.isNotEmpty()) {
                clientShipmentCurrentVessel = selected
                loadClientShipmentLines(client, selected)
            } else if (vesselStrings.isEmpty()) {
                clearClientShipmentTable("No vessels for this client")
            } else {
                clearClientShipmentTable("Choose a vessel to load cars")
            }
        }
        .catch { error: dynamic ->
            showMessage("Failed to load vessels: $error", "error")
        }
}

private fun loadClientShipmentLines(client: String, vessel: String) {
    val encodedClient = js("encodeURIComponent")(client) as String
    val encodedVessel = js("encodeURIComponent")(vessel) as String
    window.fetch(apiUrl("shipping-history/for-shipment-details/lines?clientName=$encodedClient&vessel=$encodedVessel"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load lines')")
        }
        .then { payload: dynamic ->
            val linesRaw = js("payload && payload.lines ? payload.lines : []")
            val lines = js("Array.isArray(linesRaw) ? linesRaw : []") as Array<dynamic>
            val tbody = document.getElementById("clientShipmentTableBody") as? HTMLTableSectionElement ?: return@then
            if (lines.isEmpty()) {
                clientShipmentLineCount = 0
                setClientShipmentPdfButtonsEnabled(false)
                clearClientShipmentTable("No chassis for this client and vessel")
                return@then
            }
            val html = StringBuilder()
            lines.forEachIndexed { index, line ->
                val chassis = js("line.chassis")?.toString()?.trim().orEmpty().ifEmpty { "-" }
                val model = js("line.carName")?.toString()?.trim().orEmpty().ifEmpty { "-" }
                val maker = js("line.brand")?.toString()?.trim().orEmpty().ifEmpty { "—" }
                val yearRaw = js("line.carModelYear")?.toString()?.trim().orEmpty()
                val year = extractYearOnlyForDisplay(yearRaw).ifEmpty { "-" }
                html.append(
                    """
                    <tr>
                        <td style="text-align:center;">${index + 1}</td>
                        <td>${escapeHtmlForModal(maker)}</td>
                        <td>${escapeHtmlForModal(model)}</td>
                        <td>${escapeHtmlForModal(chassis)}</td>
                        <td style="text-align:center;">${escapeHtmlForModal(year)}</td>
                    </tr>
                    """.trimIndent(),
                )
            }
            tbody.innerHTML = html.toString()
            clientShipmentLineCount = lines.size
            document.getElementById("clientShipmentCount")?.textContent =
                if (lines.size == 1) "1 unit" else "${lines.size} units"
            setClientShipmentPdfButtonsEnabled(true)
        }
        .catch { error: dynamic ->
            setClientShipmentPdfButtonsEnabled(false)
            clearClientShipmentTable("Failed to load cars")
            showMessage("Failed to load shipment lines: $error", "error")
        }
}

private fun extractYearOnlyForDisplay(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    val m = Regex("""(19|20)\d{2}""").find(s)
    return m?.value ?: s.take(4)
}

private fun fetchClientShipmentPdf(preview: Boolean) {
    val client = clientShipmentCurrentClient.trim()
    val vessel = clientShipmentCurrentVessel.trim()
    if (client.isEmpty() || vessel.isEmpty()) {
        showMessage("Select client and vessel first", "warning")
        return
    }
    if (clientShipmentLineCount <= 0) {
        showMessage("No chassis to include in the PDF", "warning")
        return
    }

    val body = js("{}")
    body.clientName = client
    body.vessel = vessel

    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(body)

    window.fetch(apiUrl("shipping-history/client-shipment-details/pdf"), requestInit)
        .then { response: dynamic ->
            val ok = js("!!response.ok") as Boolean
            if (!ok) {
                return@then response.text().then { text: dynamic ->
                    val msg = text?.toString()?.trim().orEmpty().ifEmpty { "PDF request failed" }
                    showMessage(msg, "error")
                }
            }
            response.blob().then { blob: dynamic ->
                val url = js("URL.createObjectURL(blob)") as String
                if (preview) {
                    val opened = window.open(url, "_blank")
                    val blocked = js("(opened == null || typeof opened === 'undefined')") as Boolean
                    if (blocked) {
                        showMessage("Could not open preview. Allow pop-ups, or use Download PDF.", "error")
                        try {
                            js("URL.revokeObjectURL(url)")
                        } catch (_: dynamic) {
                        }
                    } else {
                        window.setTimeout({
                            try {
                                js("URL.revokeObjectURL(url)")
                            } catch (_: dynamic) {
                            }
                        }, 120000)
                        showMessage("Shipment details preview opened.", "success")
                    }
                } else {
                    val a = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
                    a.href = url
                    a.setAttribute("download", "client_shipment_details_${js("Date.now()")}.pdf")
                    document.body?.appendChild(a)
                    a.click()
                    document.body?.removeChild(a)
                    window.setTimeout({
                        try {
                            js("URL.revokeObjectURL(url)")
                        } catch (_: dynamic) {
                        }
                    }, AppConstants.URL_REVOKE_DELAY_SHORT)
                    showMessage("Shipment details PDF downloaded.", "success")
                }
            }
        }
        .catch { error: dynamic ->
            showMessage("PDF error: $error", "error")
        }
}
