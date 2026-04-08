package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event

private var clientMapSortField: String? = null
private val clientMapSortOrderByField: MutableMap<String, String> = mutableMapOf()

private fun clientMapSortTooltip(field: String): String {
    val ord = clientMapSortOrderByField[field] ?: "desc"
    return if (ord == "asc") "Sorted A-Z (click to sort Z-A)" else "Sorted Z-A (click to sort A-Z)"
}

private fun extractClientMapSortKey(m: dynamic, field: String): String =
    clientMapCellText(m, field).trim().lowercase()

private fun toggleClientMapSort(field: String) {
    val cur = clientMapSortOrderByField[field] ?: "desc"
    clientMapSortOrderByField[field] = if (cur == "asc") "desc" else "asc"
    clientMapSortField = field
    clientMapCurrentPage = 1
    loadClientMaps()
}

private fun clientMapCellText(mapping: dynamic, key: String): String {
    return when (key) {
        "clientName" -> (mapping.clientName ?: "").toString()
        "country" -> (mapping.country ?: "").toString()
        "pod" -> (mapping.pod ?: "").toString()
        "address" -> (mapping.address ?: "").toString()
        "bankInfo" -> (mapping.bankInfo ?: "").toString()
        "consignee" -> (mapping.consignee ?: "").toString()
        "debitLimit" -> {
            val d = mapping.debitLimit
            when {
                d == null || d == js("undefined") -> ""
                else -> d.toString()
            }
        }
        else -> ""
    }
}

private fun clientMapRowMatches(mapping: dynamic, q: String): Boolean {
    if (q.isEmpty()) return true
    val keys = listOf("clientName", "country", "pod", "address", "bankInfo", "consignee", "debitLimit")
    for (k in keys) {
        if (clientMapCellText(mapping, k).uppercase().contains(q)) return true
    }
    return false
}

fun showClientMapPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="clientMapList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Client Map</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="clientMapColumnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="display: flex; gap: 15px; align-items: center; flex-wrap: wrap;">
                    <div style="flex: 1; min-width: 250px;">
                        <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search:</label>
                        <input type="text" id="clientMapFilter" placeholder="Search by client name, country, POD, address..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                    </div>
                    <div style="display: flex; gap: 10px; align-items: flex-end;">
                        <button id="clearClientMapFilterBtn" style="padding: 10px 20px; background-color: #6b7280; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 500;">Clear Filter</button>
                    </div>
                </div>
            </div>
            <div style="margin-bottom: 20px;">
                <button id="addClientMapBtn" style="padding: 12px 24px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    ➕ Add New Client Map
                </button>
            </div>
            <div id="clientMapTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading client map data...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadClientMaps()
    document.getElementById("addClientMapBtn")?.addEventListener("click", { _: Event -> showClientMapModal(null) })
    document.getElementById("clearClientMapFilterBtn")?.addEventListener("click", { _: Event ->
        (document.getElementById("clientMapFilter") as? HTMLInputElement)?.value = ""
        loadClientMaps()
    })
    document.getElementById("clientMapFilter")?.addEventListener("input", { _: Event -> loadClientMaps() })
    document.getElementById("clientMapColumnFilterBtn")?.addEventListener("click", { _: Event -> showClientMapColumnFilterModal() })
    setupClientMapDeviceChangeListener()
    checkClientMapDeviceChange()
}

fun checkClientMapDeviceChange() {
    val current = getDeviceType()
    if (lastClientMapDeviceType != null && lastClientMapDeviceType != current) {
        loadClientMaps()
    }
    lastClientMapDeviceType = current
}

fun setupClientMapDeviceChangeListener() {
    val existingListener = window.asDynamic().__clientMapDeviceChangeListener
    if (existingListener != null) {
        window.removeEventListener("resize", existingListener.unsafeCast<((Event) -> Unit)?>())
    }
    var resizeTimeout: dynamic = null
    val resizeListener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) window.clearTimeout(resizeTimeout)
        resizeTimeout = window.setTimeout({
            val newDevice = getDeviceType()
            if (lastClientMapDeviceType != null && lastClientMapDeviceType != newDevice) {
                if (window.location.hash.contains("#/master/client-map")) {
                    loadClientMaps()
                }
            }
            lastClientMapDeviceType = newDevice
        }, 300)
    }
    window.asDynamic().__clientMapDeviceChangeListener = resizeListener
    window.addEventListener("resize", resizeListener)
}

fun loadClientMaps() {
    val tableDiv = document.getElementById("clientMapTable") ?: return
    if (getDeviceType() == "mobile") {
        loadClientMapsWithCards()
        return
    }
    loadClientMapsWithTable()
}

fun loadClientMapsWithTable() {
    val tableDiv = document.getElementById("clientMapTable") ?: return
    val filterQ = (document.getElementById("clientMapFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading client map data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    window.fetch(apiUrl("client-map/mappings"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to load')") }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message || 'Failed')")
            val arr = result.data ?: js("[]")
            val list = (js("Array.isArray(arr) ? arr : []") as Array<dynamic>).toList()
            val sorted = list.sortedByDescending { m ->
                val id = m.id
                when (id) {
                    is Number -> id.toDouble()
                    is String -> id.toDoubleOrNull() ?: 0.0
                    else -> id?.toString()?.toDoubleOrNull() ?: 0.0
                }
            }
            val filtered = if (filterQ.isNotEmpty()) {
                sorted.filter { clientMapRowMatches(it, filterQ) }
            } else sorted
            val clientMapSortableCols = setOf("clientName", "country", "pod", "consignee")
            var orderedForDisplay = filtered
            val cmsf = clientMapSortField
            if (cmsf != null && cmsf in clientMapSortableCols) {
                val ord = clientMapSortOrderByField[cmsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    filtered.sortedBy { extractClientMapSortKey(it, cmsf) }
                } else {
                    filtered.sortedByDescending { extractClientMapSortKey(it, cmsf) }
                }
            }
            allClientMaps = orderedForDisplay
            if (filterQ.isNotEmpty()) clientMapCurrentPage = 1
            if (orderedForDisplay.isEmpty()) {
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">${if (filterQ.isNotEmpty()) "No rows match your search" else "No client map rows yet"}</div>
                    </div>
                """
                return@then
            }
            val totalPages = kotlin.math.ceil(orderedForDisplay.size.toDouble() / clientMapItemsPerPage).toInt()
            val start = (clientMapCurrentPage - 1) * clientMapItemsPerPage
            val end = kotlin.math.min(start + clientMapItemsPerPage, orderedForDisplay.size)
            val pageRows = orderedForDisplay.subList(start, end)
            val selectedColumns = getSelectedClientMapColumns()
            val columnLabels = mapOf(
                "clientName" to "Client Name",
                "country" to "Country",
                "pod" to "POD",
                "address" to "Address",
                "bankInfo" to "Bank Info",
                "consignee" to "Consignee",
                "debitLimit" to "Debit Limit",
            )
            var html = """<div class="client-map-table-wrap"><table class="client-map-table"><colgroup><col class="client-map-col-actions">"""
            for (ck in selectedColumns) {
                html += when (ck) {
                    "bankInfo" -> """<col class="client-map-col-bank">"""
                    else -> """<col class="client-map-col-default">"""
                }
            }
            html += """</colgroup><thead><tr><th class="client-map-th-actions"></th>"""
            for (ck in selectedColumns) {
                val label = columnLabels[ck] ?: ck
                html += if (ck in clientMapSortableCols) {
                    val tip = clientMapSortTooltip(ck)
                    val bid = "clientMapSort_$ck"
                    """<th class="client-map-th" data-col="$ck"><button type="button" id="$bid" title="${escapeHtml(tip)}" style="background: none; border: none; cursor: pointer; font-weight: 700; color: #111827; padding: 0; font-size: inherit; display: inline-flex; align-items: center; gap: 6px;"><span>$label</span><span style="font-size: 14px;">↕</span></button></th>"""
                } else {
                    """<th class="client-map-th" data-col="$ck">$label</th>"""
                }
            }
            html += """</tr></thead><tbody>"""
            for (mapping in pageRows) {
                val id = (mapping.id ?: "").toString()
                html += """
                    <tr>
                        <td class="client-map-td-actions">
                            <div style="display:flex; gap:6px; align-items:center;">
                                <button onclick="window.editMasterClientMap($id)" aria-label="Edit" title="Edit"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer;">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
                                </button>
                                <button onclick="window.duplicateMasterClientMap($id)" aria-label="Duplicate" title="Duplicate"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#3b82f6; border:none; border-radius:50%; cursor:pointer;">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="white"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
                                </button>
                            </div>
                        </td>
                """
                for (ck in selectedColumns) {
                    val value = clientMapCellText(mapping, ck)
                    val inner = when (ck) {
                        "address" -> formatMultiValueChipCellHtmlSemicolonOnly(value)
                        "bankInfo" -> formatClientMapBankInfoCellHtml(value)
                        "consignee" -> formatClientMapConsigneeChipCellHtml(value)
                        "debitLimit" -> formatClientMapDebitLimitCellHtml(value)
                        else -> formatMultiValueChipCellHtml(value)
                    }
                    html += """<td class="client-map-td" data-col="$ck">$inner</td>"""
                }
                html += "</tr>"
            }
            html += """</tbody></table></div>"""
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px;">Showing ${start + 1} to $end of ${orderedForDisplay.size}</div>
                        <div class="car-brand-pagination-controls">
                            <button id="clientMapPrevPage" class="car-brand-pagination-btn" ${if (clientMapCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="car-brand-pagination-page">Page $clientMapCurrentPage of $totalPages</span>
                            <button id="clientMapNextPage" class="car-brand-pagination-btn" ${if (clientMapCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            }
            tableDiv.innerHTML = html
            document.getElementById("clientMapPrevPage")?.addEventListener("click", { _: Event ->
                if (clientMapCurrentPage > 1) {
                    clientMapCurrentPage--
                    loadClientMaps()
                }
            })
            document.getElementById("clientMapNextPage")?.addEventListener("click", { _: Event ->
                val tp = kotlin.math.ceil(allClientMaps.size.toDouble() / clientMapItemsPerPage).toInt()
                if (clientMapCurrentPage < tp) {
                    clientMapCurrentPage++
                    loadClientMaps()
                }
            })
            val clientMapSortKeys = listOf("clientName", "country", "pod", "consignee")
            for (key in clientMapSortKeys) {
                if (key in selectedColumns) {
                    document.getElementById("clientMapSort_$key")?.addEventListener("click", { _: Event ->
                        toggleClientMapSort(key)
                    })
                }
            }
        }
        .catch { e: dynamic ->
            tableDiv.innerHTML = """<div style="text-align:center;color:#b91c1c;padding:40px;">${escapeHtml(e.message?.toString() ?: "Error")}</div>"""
        }
}

fun loadClientMapsWithCards() {
    val tableDiv = document.getElementById("clientMapTable") ?: return
    val filterQ = (document.getElementById("clientMapFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = """<div style="text-align:center;color:#6b7280;padding:40px;">Loading…</div>"""
    window.fetch(apiUrl("client-map/mappings"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed')") }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message)")
            val arr = result.data ?: js("[]")
            val list = (js("Array.isArray(arr) ? arr : []") as Array<dynamic>).toList()
            val sorted = list.sortedByDescending { m ->
                val id = m.id
                when (id) {
                    is Number -> id.toDouble()
                    is String -> id.toDoubleOrNull() ?: 0.0
                    else -> id?.toString()?.toDoubleOrNull() ?: 0.0
                }
            }
            val filtered = if (filterQ.isNotEmpty()) sorted.filter { clientMapRowMatches(it, filterQ) } else sorted
            val clientMapSortableCols = setOf("clientName", "country", "pod", "consignee")
            var orderedForDisplay = filtered
            val cmsf = clientMapSortField
            if (cmsf != null && cmsf in clientMapSortableCols) {
                val ord = clientMapSortOrderByField[cmsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    filtered.sortedBy { extractClientMapSortKey(it, cmsf) }
                } else {
                    filtered.sortedByDescending { extractClientMapSortKey(it, cmsf) }
                }
            }
            allClientMaps = orderedForDisplay
            if (filterQ.isNotEmpty()) clientMapCurrentPage = 1
            if (orderedForDisplay.isEmpty()) {
                tableDiv.innerHTML = """<div style="text-align:center;padding:40px;color:#6b7280;">No rows</div>"""
                return@then
            }
            val totalPages = kotlin.math.ceil(orderedForDisplay.size.toDouble() / clientMapItemsPerPage).toInt()
            val start = (clientMapCurrentPage - 1) * clientMapItemsPerPage
            val end = kotlin.math.min(start + clientMapItemsPerPage, orderedForDisplay.size)
            val pageRows = orderedForDisplay.subList(start, end)
            val selectedColumns = getSelectedClientMapColumns()
            val columnLabels = mapOf(
                "clientName" to "Client Name",
                "country" to "Country",
                "pod" to "POD",
                "address" to "Address",
                "bankInfo" to "Bank Info",
                "consignee" to "Consignee",
                "debitLimit" to "Debit Limit",
            )
            val sb = StringBuilder()
            sb.append("""<div class="car-brand-cards-container">""")
            for (mapping in pageRows) {
                val id = (mapping.id ?: "").toString()
                val title = clientMapCellText(mapping, "clientName").ifEmpty { "Client #$id" }
                val fields = StringBuilder()
                for (ck in selectedColumns) {
                    val label = columnLabels[ck] ?: ck
                    val value = clientMapCellText(mapping, ck)
                    if (value.isNotEmpty()) {
                        val cellHtml = when (ck) {
                            "address" -> formatMultiValueChipCellHtmlSemicolonOnly(value)
                            "bankInfo" -> formatClientMapBankInfoCellHtml(value)
                            "consignee" -> formatClientMapConsigneeChipCellHtml(value)
                            "debitLimit" -> formatClientMapDebitLimitCellHtml(value)
                            else -> formatMultiValueChipCellHtml(value)
                        }
                        fields.append("""<div style="margin-bottom:8px;"><span style="font-weight:600;color:#666;font-size:12px;">$label:</span><div style="margin-top:2px;">$cellHtml</div></div>""")
                    }
                }
                sb.append("""
                    <div class="car-brand-card">
                        <div class="card-header">
                            <div style="display:flex;gap:6px;">
                                <button class="card-edit-btn" onclick="window.editMasterClientMap($id)"><svg width="14" height="14" viewBox="0 0 24 24"><path fill="white" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z"/></svg></button>
                                <button class="card-edit-btn" onclick="window.duplicateMasterClientMap($id)" style="background:#3b82f6;"><svg width="14" height="14" viewBox="0 0 24 24"><path fill="white" d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1z"/></svg></button>
                            </div>
                            <div class="card-title">${escapeHtml(title)}</div>
                        </div>
                        <div class="card-body">$fields</div>
                    </div>
                """)
            }
            sb.append("</div>")
            if (totalPages > 1) {
                sb.append("""<div class="pagination-controls">
                    <button id="clientMapPrevPage" class="pagination-btn" ${if (clientMapCurrentPage == 1) "disabled" else ""}>Previous</button>
                    <span class="pagination-page">Page $clientMapCurrentPage of $totalPages</span>
                    <button id="clientMapNextPage" class="pagination-btn" ${if (clientMapCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                </div>""")
            }
            tableDiv.innerHTML = sb.toString()
            document.getElementById("clientMapPrevPage")?.addEventListener("click", { _: Event ->
                if (clientMapCurrentPage > 1) {
                    clientMapCurrentPage--
                    loadClientMaps()
                }
            })
            document.getElementById("clientMapNextPage")?.addEventListener("click", { _: Event ->
                val tp = kotlin.math.ceil(allClientMaps.size.toDouble() / clientMapItemsPerPage).toInt()
                if (clientMapCurrentPage < tp) {
                    clientMapCurrentPage++
                    loadClientMaps()
                }
            })
        }
        .catch { e: dynamic ->
            tableDiv.innerHTML = """<div style="color:#b91c1c;text-align:center;">${escapeHtml(e.message?.toString() ?: "")}</div>"""
        }
}

fun showClientMapColumnFilterModal() {
    document.getElementById("clientMapColumnFilterModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "clientMapColumnFilterModal"
    modal.asDynamic().style.cssText =
        "position:fixed;inset:0;background:rgba(0,0,0,.5);z-index:10000;display:flex;align-items:center;justify-content:center;"
    val deviceType = getDeviceType()
    val maxColumns = getMaxClientMapColumnsForDevice(deviceType)
    val deviceName = when (deviceType) {
        "mobile" -> "Mobile View"
        "tablet" -> "Tablet View"
        else -> "Desktop View"
    }
    val selected = getSelectedClientMapColumns().toSet()
    val columnLabels = mapOf(
        "clientName" to "Client Name",
        "country" to "Country",
        "pod" to "POD",
        "address" to "Address",
        "bankInfo" to "Bank Info",
        "consignee" to "Consignee",
        "debitLimit" to "Debit Limit",
    )
    modal.innerHTML = """
        <div style="background:white;border-radius:8px;padding:24px;max-width:520px;width:90%;max-height:80vh;overflow-y:auto;">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
                <h3 style="margin:0;">Select Columns to Display</h3>
                <button id="closeClientMapColumnFilter" style="background:none;border:none;font-size:28px;cursor:pointer;">&times;</button>
            </div>
            <div style="margin-bottom:12px;padding:12px;background:#f8f9fa;border-radius:4px;border-left:4px solid #007bff;">
                <strong>$deviceName — max $maxColumns columns</strong><br>
                <span style="color:#666;font-size:14px;">Selected: <span id="clientMapSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="clientMapColumnCheckboxes" style="display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:16px;"></div>
            <div style="display:flex;gap:10px;justify-content:flex-end;">
                <button id="resetClientMapColumns" style="padding:8px 16px;background:#6c757d;color:white;border:none;border-radius:4px;cursor:pointer;">Reset to Default</button>
                <button id="applyClientMapColumns" style="padding:8px 16px;background:#007bff;color:white;border:none;border-radius:4px;cursor:pointer;">Apply Changes</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    val box = document.getElementById("clientMapColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val wrap = document.createElement("div")
        wrap.asDynamic().style.display = "flex"
        wrap.asDynamic().style.alignItems = "center"
        wrap.asDynamic().style.gap = "8px"
        val inp = document.createElement("input") as HTMLInputElement
        inp.type = "checkbox"
        inp.id = "clientMapCol_$key"
        inp.setAttribute("data-column", key)
        inp.checked = selected.contains(key)
        inp.addEventListener("change", { _: Event -> updateClientMapColumnSelectionCount() })
        val lab = document.createElement("label") as HTMLLabelElement
        lab.htmlFor = "clientMapCol_$key"
        lab.textContent = label
        wrap.appendChild(inp)
        wrap.appendChild(lab)
        box?.appendChild(wrap)
    }
    updateClientMapColumnSelectionCount()
    document.getElementById("closeClientMapColumnFilter")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("resetClientMapColumns")?.addEventListener("click", { _: Event ->
        val def = getDefaultClientMapColumnsForDevice(getDeviceType()).toSet()
        columnLabels.keys.forEach { k ->
            (document.getElementById("clientMapCol_$k") as? HTMLInputElement)?.checked = def.contains(k)
        }
        updateClientMapColumnSelectionCount()
    })
    document.getElementById("applyClientMapColumns")?.addEventListener("click", { _: Event ->
        val checks = document.querySelectorAll("#clientMapColumnCheckboxes input[type='checkbox']")
        val out = mutableListOf<String>()
        for (i in 0 until checks.length) {
            val c = checks.item(i) as HTMLInputElement
            if (c.checked) {
                val k = c.getAttribute("data-column") ?: ""
                if (k.isNotEmpty()) out.add(k)
            }
        }
        safeLocalStorageSet("selectedClientMapColumns", JSON.stringify(out.toTypedArray()))
        modal.remove()
        loadClientMaps()
    })
    modal.addEventListener("click", { ev: Event ->
        if ((ev.target as? HTMLElement)?.id == "clientMapColumnFilterModal") modal.remove()
    })
}

fun updateClientMapColumnSelectionCount() {
    val maxColumns = getMaxClientMapColumnsForDevice(getDeviceType())
    val checks = document.querySelectorAll("#clientMapColumnCheckboxes input[type='checkbox']")
    var n = 0
    for (i in 0 until checks.length) {
        if ((checks.item(i) as HTMLInputElement).checked) n++
    }
    (document.getElementById("clientMapSelectedCount") as? HTMLElement)?.textContent = "$n"
    for (i in 0 until checks.length) {
        val c = checks.item(i) as HTMLInputElement
        c.disabled = n >= maxColumns && !c.checked
    }
}

fun showClientMapModal(mappingId: Long?, duplicateFromId: Long? = null) {
    val isDup = duplicateFromId != null
    val isEdit = mappingId != null && !isDup
    val title = when {
        isDup -> "Duplicate Client Map"
        isEdit -> "Edit Client Map"
        else -> "Add New Client Map"
    }
    val html = """
        <div id="clientMapModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div id="clientMapModalContent" style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeClientMapModalX" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="clientMapForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Client Name <span style="color: #ef4444;">*</span></label>
                            ${createEditableCombobox("clientMapMmClientName", "Select Client Name", required = true)}
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Country</label>
                                ${createChipMultiSelectCombobox("clientMapMmCountry", "Select Country")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POD</label>
                                ${createChipMultiSelectCombobox("clientMapMmPod", "Select POD")}
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Bank Info</label>
                                ${createChipMultiSelectCombobox("clientMapMmBankInfo", "Select Bank Info")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Consignee</label>
                                ${createChipMultiSelectCombobox("clientMapMmConsignee", "Select Consignee")}
                            </div>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Address</label>
                            ${createChipInput("clientMapMmAddress", "Type address")}
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Debit Limit</label>
                            <input type="number" step="any" id="clientMapDebitLimit" placeholder="e.g. 10000.00" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div class="car-brand-modal-actions">
                            <button type="button" id="cancelClientMapBtn" class="car-brand-modal-btn car-brand-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """<button type="button" id="deleteClientMapBtn" class="car-brand-modal-btn car-brand-modal-btn-delete">Delete</button>""" else ""}
                            <button type="submit" id="saveClientMapBtn" class="car-brand-modal-btn car-brand-modal-btn-save">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", html)
    wireClientMapModalComboboxes()
    if (isEdit && mappingId != null) {
        loadClientMapDataForEdit(mappingId, clearNameForDup = false)
    } else if (isDup && duplicateFromId != null) {
        loadClientMapDataForEdit(duplicateFromId, clearNameForDup = true)
    }
    document.getElementById("closeClientMapModalX")?.addEventListener("click", { _: Event -> closeClientMapModal() })
    document.getElementById("cancelClientMapBtn")?.addEventListener("click", { _: Event -> closeClientMapModal() })
    if (isEdit && mappingId != null) {
        document.getElementById("deleteClientMapBtn")?.addEventListener("click", { _: Event ->
            if (js("confirm('Delete this client map row?')").unsafeCast<Boolean>()) {
                deleteMasterClientMap(mappingId)
            }
        })
    }
    document.getElementById("clientMapForm")?.addEventListener("submit", { ev: Event ->
        ev.preventDefault()
        val saveId = if (isDup) null else mappingId
        saveClientMapRow(saveId)
    })
    document.getElementById("clientMapModal")?.addEventListener("click", { ev: Event ->
        if ((ev.target as? HTMLElement)?.id == "clientMapModal") closeClientMapModal()
    })
}

fun closeClientMapModal() {
    document.getElementById("clientMapModal")?.remove()
}

fun loadClientMapDataForEdit(id: Long, clearNameForDup: Boolean) {
    window.fetch(apiUrl("client-map/mappings/$id"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error()") }
        .then { res: dynamic ->
            if (res.success as? Boolean != true) throw js("Error()")
            val d = res.data ?: js("{}")
            setEditableComboboxValue(
                "clientMapMmClientName",
                if (clearNameForDup) "" else (d.clientName ?: "").toString()
            )
            setChipFieldValue("clientMapMmCountry", normalizeStoredListForChips((d.country ?: "").toString()))
            setChipFieldValue("clientMapMmPod", normalizeStoredListForChips((d.pod ?: "").toString()))
            setChipFieldValue("clientMapMmBankInfo", normalizeBankInfoForChips((d.bankInfo ?: "").toString()))
            setChipFieldValue("clientMapMmConsignee", normalizeStoredListForChips((d.consignee ?: "").toString()))
            setChipFieldValue("clientMapMmAddress", normalizeMultilineSemicolonListForChips((d.address ?: "").toString()))
            (document.getElementById("clientMapDebitLimit") as? HTMLInputElement)?.value =
                if (d.debitLimit == null || d.debitLimit == js("undefined")) "" else d.debitLimit.toString()
        }
        .catch { e: dynamic -> showMessage("Failed to load: ${e.message}", "error") }
}

fun saveClientMapRow(mappingId: Long?) {
    val name = getEditableComboboxValue("clientMapMmClientName")
    if (name.isEmpty()) {
        showMessage("Client name is required", "error")
        return
    }
    val body = js("{}")
    body.clientName = name
    body.country = getChipFieldValue("clientMapMmCountry").takeIf { it.isNotEmpty() }
    body.pod = getChipFieldValue("clientMapMmPod").takeIf { it.isNotEmpty() }
    body.address = getChipFieldValue("clientMapMmAddress").takeIf { it.isNotEmpty() }
    body.bankInfo = getChipFieldValue("clientMapMmBankInfo").takeIf { it.isNotEmpty() }
    body.consignee = getChipFieldValue("clientMapMmConsignee").takeIf { it.isNotEmpty() }
    val debitStr = (document.getElementById("clientMapDebitLimit") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (debitStr.isNotEmpty()) {
        body.debitLimit = debitStr
    }
    val url = if (mappingId != null) apiUrl("client-map/mappings/$mappingId") else apiUrl("client-map/mappings")
    val method = if (mappingId != null) "PUT" else "POST"
    val init = js("{}")
    init.method = method
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    init.headers = headers
    init.body = JSON.stringify(body)
    window.fetch(url, init)
        .then { response: dynamic ->
            val httpOk = response.ok as Boolean
            response.json().then { jsonBody: dynamic ->
                if (httpOk && jsonBody.success as? Boolean == true) {
                    val serverMsg = (jsonBody.message as? String)?.trim().orEmpty()
                    val fallback = if (mappingId != null) "Updated" else "Saved"
                    showMessage(if (serverMsg.isNotEmpty()) serverMsg else fallback, "success")
                    closeClientMapModal()
                    loadClientMaps()
                } else {
                    val msg = (jsonBody.message as? String) ?: if (!httpOk) "Request failed" else "Save failed"
                    showMessage(msg, "error")
                }
            }
        }
        .catch { e: dynamic -> showMessage("Error: ${e.message ?: e}", "error") }
}

fun deleteMasterClientMap(id: Long) {
    window.fetch(apiUrl("client-map/mappings/$id"), js("""{ method: 'DELETE' }"""))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error()") }
        .then { result: dynamic ->
            if (result.success as? Boolean == true) {
                showMessage("Deleted", "success")
                closeClientMapModal()
                loadClientMaps()
            } else {
                showMessage("Delete failed", "error")
            }
        }
        .catch { e: dynamic -> showMessage("${e.message}", "error") }
}

fun editMasterClientMap(id: dynamic) {
    val mid = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mid != null) showClientMapModal(mid)
}

fun duplicateMasterClientMap(id: dynamic) {
    val sid = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (sid != null) showClientMapModal(mappingId = null, duplicateFromId = sid)
}
