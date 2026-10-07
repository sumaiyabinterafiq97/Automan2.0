package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.js.JSON

private var remBaseRows: List<dynamic> = emptyList()
private var remSearchQuery: String = ""
private var remEditOriginalCompany: String? = null

private fun remParseId(raw: dynamic): Long? =
    (raw as? Number)?.toLong() ?: raw?.toString()?.toLongOrNull()

private fun remCell(row: dynamic, key: String): String =
    when (key) {
        "rixoCompany" -> (row.rixoCompany ?: "").toString()
        "email" -> (row.email ?: "").toString()
        else -> ""
    }

fun showRixoEmailMapPage() {
    val content = document.getElementById("content") ?: return
    content.innerHTML = """
        <div id="remRoot" style="border:1px solid #ddd;border-radius:4px;padding:20px;max-width:1400px;margin:0 auto;width:100%;box-sizing:border-box;">
            <style>
                #remRoot .rem-header-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;gap:12px;}
                #remRoot .rem-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
                #remRoot table.purchase-list-table thead th{position:sticky;top:0;z-index:1;background:#f9fafb;}
                #remRoot .rem-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
                #remRoot .rem-empty strong{color:#0f172a;}
                @media (max-width:767px){
                    #remRoot{padding:14px;}
                    #remRoot .rem-header-row{flex-direction:column;align-items:stretch;}
                    #remRoot #remAddBtn{width:100%;justify-content:center;}
                }
            </style>
            <div class="rem-header-row">
                <h2 style="margin:0;color:#111827;font-size:28px;font-weight:700;">Rixo Email Map</h2>
            </div>
            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:20px;margin-bottom:20px;">
                <div style="position:relative;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);">
                    <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);color:#9ca3af;display:flex;" aria-hidden="true">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    </span>
                    <input type="text" id="remSearchInput" role="searchbox" autocomplete="off" placeholder="Search company or email…" aria-label="Search Rixo email map" style="width:100%;box-sizing:border-box;padding:11px 38px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;" />
                    <button type="button" id="remSearchClearBtn" title="Clear search" aria-label="Clear search" style="position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:4px 8px;min-height:36px;min-width:36px;">×</button>
                </div>
            </div>
            <div style="margin-bottom:20px;">
                <button type="button" id="remAddBtn" style="padding:12px 24px;background-color:#059669;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px;font-weight:600;box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                    + Add Email
                </button>
            </div>
            <div id="remTable">
                <div class="rem-empty"><strong>Loading</strong><div>Loading Rixo email map…</div></div>
            </div>
        </div>
    """.trimIndent()

    (document.getElementById("remSearchInput") as? HTMLInputElement)?.value = remSearchQuery
    loadRixoEmailMapTable()
    document.getElementById("remAddBtn")?.addEventListener("click", { _: Event -> showRixoEmailMapModal(null) })
    document.getElementById("remSearchInput")?.addEventListener("input", { _: Event ->
        remSearchQuery = (document.getElementById("remSearchInput") as? HTMLInputElement)?.value ?: ""
        renderRixoEmailMapTable()
    })
    document.getElementById("remSearchClearBtn")?.addEventListener("click", { _: Event ->
        remSearchQuery = ""
        (document.getElementById("remSearchInput") as? HTMLInputElement)?.value = ""
        renderRixoEmailMapTable()
    })
}

private fun loadRixoEmailMapTable() {
    val tableDiv = document.getElementById("remTable") ?: return
    tableDiv.innerHTML = """<div class="rem-empty"><strong>Loading</strong><div>Loading Rixo email map…</div></div>"""
    window.fetch(apiUrl("rixo-email-map/mappings"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to load Rixo email map')") }
        .then { body: dynamic ->
            val raw = js("(function(b){ var d = b && b.data; return Array.isArray(d) ? d : []; })")(body)
            remBaseRows = (raw as Array<dynamic>).toList()
            renderRixoEmailMapTable()
        }
        .catch { e: dynamic ->
            tableDiv.innerHTML = """<div class="rem-empty"><strong>Error</strong><div>${escapeHtml(e.message?.toString() ?: "Failed to load")}</div></div>"""
        }
}

private data class RemCompanyGroup(val company: String, val rows: List<dynamic>)

/** One group per company. A search hit keeps every address for that company. */
private fun filteredRemCompanyGroups(): List<RemCompanyGroup> {
    val order = mutableListOf<String>()
    val byKey = linkedMapOf<String, MutableList<dynamic>>()
    val label = linkedMapOf<String, String>()
    remBaseRows.forEach { row ->
        val company = remCell(row, "rixoCompany")
        val key = company.lowercase()
        if (!byKey.containsKey(key)) {
            order.add(key)
            label[key] = company
            byKey[key] = mutableListOf()
        }
        byKey[key]?.add(row)
    }
    val q = remSearchQuery.trim().lowercase()
    return order.mapNotNull { key ->
        val rows = byKey[key].orEmpty()
        val company = label[key].orEmpty()
        val matches = q.isEmpty() ||
            company.lowercase().contains(q) ||
            rows.any { remCell(it, "email").lowercase().contains(q) }
        if (!matches) null else RemCompanyGroup(company, rows)
    }
}

private fun renderRixoEmailMapTable() {
    val tableDiv = document.getElementById("remTable") ?: return
    val groups = filteredRemCompanyGroups()
    if (groups.isEmpty()) {
        val msg = if (remSearchQuery.trim().isNotEmpty()) "No matches for your search." else "No Rixo emails yet. Add one for a company such as KLC."
        tableDiv.innerHTML = """<div class="rem-empty"><strong>No results</strong><div>$msg</div></div>"""
        return
    }
    val body = groups.joinToString("") { group ->
        val id = group.rows.firstNotNullOfOrNull { remParseId(it.id) } ?: 0L
        val emails = group.rows.map { remCell(it, "email") }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        """
            <tr>
                <td style="padding:12px 14px;width:88px;">
                    <div style="display:inline-flex;align-items:center;gap:8px;">
                        <button type="button" onclick="window.editMasterRixoEmailMap($id)" aria-label="Edit" title="Edit" style="width:32px;height:32px;display:inline-flex;align-items:center;justify-content:center;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
                        </button>
                        <button type="button" onclick="window.deleteMasterRixoEmailMap($id)" aria-label="Delete" title="Delete" style="width:32px;height:32px;display:inline-flex;align-items:center;justify-content:center;background:#fff;border:1px solid #fecaca;border-radius:50%;cursor:pointer;color:#b91c1c;font-weight:700;">×</button>
                    </div>
                </td>
                <td style="padding:12px 14px;">${formatConsigneeMapValueChipHtml(group.company)}</td>
                <td style="padding:12px 14px;">${formatConsigneeMapValueChipHtml(emails.joinToString(";"))}</td>
            </tr>
        """.trimIndent()
    }
    tableDiv.innerHTML = """
        <div class="rem-table-shell">
            <table class="purchase-list-table" style="width:100%;border-collapse:collapse;">
                <thead><tr>
                    <th style="padding:12px 14px;"></th>
                    <th style="padding:12px 14px;text-align:left;">Rixo Company</th>
                    <th style="padding:12px 14px;text-align:left;">Email</th>
                </tr></thead>
                <tbody>$body</tbody>
            </table>
        </div>
    """.trimIndent()
}

private fun showRixoEmailMapModal(mappingId: Long?) {
    val isEdit = mappingId != null
    val title = if (isEdit) "Edit Rixo Email" else "Add Rixo Email"
    val modalHtml = """
        <div id="remModal" style="position:fixed;top:0;left:0;width:100%;height:100%;background-color:rgba(0,0,0,0.5);z-index:10000;display:flex;align-items:center;justify-content:center;">
            <div style="background:white;border-radius:12px;width:90%;max-width:560px;max-height:90vh;overflow-y:auto;box-shadow:0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding:24px;border-bottom:1px solid #e5e7eb;">
                    <div style="display:flex;justify-content:space-between;align-items:center;">
                        <h2 style="margin:0;font-size:24px;font-weight:700;color:#111827;">$title</h2>
                        <button type="button" id="closeRemModal" style="background:none;border:none;font-size:24px;color:#6b7280;cursor:pointer;padding:0;width:32px;height:32px;">×</button>
                    </div>
                </div>
                <div style="padding:24px;">
                    <form id="remForm">
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:8px;font-weight:600;color:#374151;font-size:14px;">Rixo Company <span style="color:#ef4444;">*</span></label>
                            ${createEditableCombobox("remCompany", "Select or type a Rixo company", required = true)}
                        </div>
                        <div style="margin-bottom:20px;">
                            <label style="display:block;margin-bottom:8px;font-weight:600;color:#374151;font-size:14px;">Email <span style="color:#ef4444;">*</span></label>
                            ${createChipInput("remEmails", "Type an email and press Enter")}
                        </div>
                        <div style="display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap;">
                            <button type="button" id="cancelRemBtn" style="padding:10px 16px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;cursor:pointer;font-size:14px;color:#374151;">Cancel</button>
                            <button type="submit" id="saveRemBtn" style="padding:10px 16px;border:none;border-radius:8px;background:#059669;color:#fff;cursor:pointer;font-weight:700;font-size:14px;">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """.trimIndent()
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    ensureSupplierChipJs()
    populateEditableComboboxFromRixoMappingDistinctCompanies("remCompany")
    remEditOriginalCompany = null
    if (isEdit && mappingId != null) {
        val row = remBaseRows.find { remParseId(it.id) == mappingId }
        if (row != null) {
            val company = remCell(row, "rixoCompany")
            remEditOriginalCompany = company
            val emails = remBaseRows
                .filter { remCell(it, "rixoCompany").equals(company, ignoreCase = true) }
                .map { remCell(it, "email") }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
            window.setTimeout({
                setEditableComboboxValue("remCompany", company)
                setChipFieldValue("remEmails", emails.joinToString(";"))
            }, 450)
        }
    }
    document.getElementById("closeRemModal")?.addEventListener("click", { _: Event -> closeRixoEmailMapModal() })
    document.getElementById("cancelRemBtn")?.addEventListener("click", { _: Event -> closeRixoEmailMapModal() })
    document.getElementById("remForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveRixoEmailMap(mappingId)
    })
    document.getElementById("remModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "remModal") closeRixoEmailMapModal()
    })
}

private fun closeRixoEmailMapModal() {
    document.getElementById("remModal")?.remove()
}

private fun remCommitEmailChips(): List<String> {
    js("if (window.supplierChipAddFromInput) window.supplierChipAddFromInput('remEmails');")
    return getChipFieldValue("remEmails")
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
}

private fun saveRixoEmailMap(mappingId: Long?) {
    val company = getEditableComboboxValue("remCompany").trim()
    val emails = remCommitEmailChips()
    val leftover = (document.getElementById("remEmailsInput") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (company.isEmpty()) {
        showMessage("Rixo company is required", "error")
        return
    }
    if (leftover.isNotEmpty()) {
        showMessage("Enter a valid email address.", "error")
        return
    }
    if (emails.isEmpty()) {
        showMessage("Email is required", "error")
        return
    }
    val saveButton = document.getElementById("saveRemBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    val original = if (mappingId != null) remEditOriginalCompany else null
    val existing = if (original.isNullOrBlank()) {
        emptyList()
    } else {
        remBaseRows.filter { remCell(it, "rixoCompany").equals(original, ignoreCase = true) }
    }
    val desiredKeys = emails.map { it.lowercase() }.toSet()
    val existingKeys = existing.map { remCell(it, "email").lowercase() }.toSet()
    val companyChanged = original != null && !original.equals(company, ignoreCase = true)
    val ops = mutableListOf<dynamic>()
    existing.filter { remCell(it, "email").lowercase() !in desiredKeys }.forEach { row ->
        val id = remParseId(row.id) ?: return@forEach
        val op = js("{}")
        op.method = "DELETE"
        op.url = apiUrl("rixo-email-map/mappings/$id")
        ops.add(op)
    }
    if (companyChanged) {
        existing.filter { remCell(it, "email").lowercase() in desiredKeys }.forEach { row ->
            val id = remParseId(row.id) ?: return@forEach
            val op = js("{}")
            op.method = "PUT"
            op.url = apiUrl("rixo-email-map/mappings/$id")
            op.rixoCompany = company
            op.email = remCell(row, "email")
            ops.add(op)
        }
    }
    emails.filter { it.lowercase() !in existingKeys }.forEach { email ->
        val op = js("{}")
        op.method = "POST"
        op.url = apiUrl("rixo-email-map/mappings/add")
        op.rixoCompany = company
        op.email = email
        ops.add(op)
    }
    if (ops.isEmpty()) {
        showMessage(if (mappingId != null) "Rixo email updated" else "Rixo email added", "success")
        closeRixoEmailMapModal()
        saveButton?.disabled = false
        saveButton?.textContent = if (mappingId != null) "Update" else "Save"
        return
    }
    runRemOps(ops, 0, mappingId != null) { ok, msg ->
        if (ok) {
            showMessage(msg, "success")
            closeRixoEmailMapModal()
            loadRixoEmailMapTable()
        } else {
            showMessage(msg, "error")
            saveButton?.disabled = false
            saveButton?.textContent = if (mappingId != null) "Update" else "Save"
        }
    }
}

private fun runRemOps(ops: List<dynamic>, index: Int, isEdit: Boolean, done: (Boolean, String) -> Unit) {
    if (index >= ops.size) {
        done(true, if (isEdit) "Rixo email updated" else "Rixo email added")
        return
    }
    val op = ops[index]
    val requestInit = js("{}")
    requestInit.method = op.method
    val method = op.method?.toString() ?: ""
    if (method == "POST" || method == "PUT") {
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        val payload = js("{}")
        payload.rixoCompany = op.rixoCompany
        payload.email = op.email
        requestInit.body = JSON.stringify(payload)
    }
    window.fetch(op.url as String, requestInit)
        .then { response: dynamic -> response.json() }
        .then { result: dynamic ->
            val ok = js("(function(j){ return !!(j && j.success); })")(result).unsafeCast<Boolean>()
            if (!ok) {
                val msg = js("(function(j){ if(!j) return 'Failed to save'; var m=j.message||j.error; return (m==null||String(m).trim()==='')?'Failed to save':String(m); })")(result).unsafeCast<String>()
                done(false, msg)
            } else {
                runRemOps(ops, index + 1, isEdit, done)
            }
        }
        .catch { error: dynamic ->
            done(false, error.message?.toString() ?: "Failed to save")
        }
}

fun editMasterRixoEmailMap(id: dynamic) {
    val mappingId = remParseId(id) ?: return
    showRixoEmailMapModal(mappingId)
}

fun deleteMasterRixoEmailMap(id: dynamic) {
    val mappingId = remParseId(id) ?: run {
        showMessage("Invalid Rixo email map ID", "error")
        return
    }
    val row = remBaseRows.find { remParseId(it.id) == mappingId } ?: run {
        showMessage("Invalid Rixo email map ID", "error")
        return
    }
    val company = remCell(row, "rixoCompany")
    val ids = remBaseRows
        .filter { remCell(it, "rixoCompany").equals(company, ignoreCase = true) }
        .mapNotNull { remParseId(it.id) }
    if (ids.isEmpty()) {
        showMessage("Invalid Rixo email map ID", "error")
        return
    }
    val safeCompany = escapeHtml(company.ifEmpty { "this company" })
    showRixoMappingDeleteConfirm(
        "Delete Rixo email",
        "Delete every email saved for $safeCompany? Email PDF will no longer offer them for this company.",
    ) {
        deleteRemMappings(ids, 0)
    }
}

private fun deleteRemMappings(ids: List<Long>, index: Int) {
    if (index >= ids.size) {
        showMessage("Deleted", "success")
        loadRixoEmailMapTable()
        return
    }
    val requestInit = js("{}")
    requestInit.method = "DELETE"
    window.fetch(apiUrl("rixo-email-map/mappings/${ids[index]}"), requestInit)
        .then { response: dynamic -> response.json() }
        .then { result: dynamic ->
            val ok = js("(function(j){ return !!(j && j.success); })")(result).unsafeCast<Boolean>()
            if (ok) {
                deleteRemMappings(ids, index + 1)
            } else {
                showMessage("Failed to delete", "error")
                loadRixoEmailMapTable()
            }
        }
        .catch { error: dynamic ->
            showMessage(error.message?.toString() ?: "Failed to delete", "error")
            loadRixoEmailMapTable()
        }
}

/** To field plus a ▼ button. The address list stays closed until that button is clicked. */
fun rixoEmailToFieldHtml(inputId: String, choicesWrapId: String): String = """
    <label for="$inputId" style="display:block;font-size:13px;font-weight:600;color:#0f172a;margin-bottom:6px;">To</label>
    <div style="position:relative;">
        <input id="$inputId" type="text" name="rixoPdfTo" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" placeholder="name@company.com"
            style="width:100%;box-sizing:border-box;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;min-height:40px;" />
        <button type="button" id="${choicesWrapId}Toggle" aria-label="Show saved emails" title="Show saved emails"
            style="display:none;position:absolute;top:0;right:0;width:40px;height:40px;border:none;border-left:1px solid #d1d5db;background:#f5f5f5;cursor:pointer;border-radius:0 8px 8px 0;font-size:12px;font-weight:700;color:#666;align-items:center;justify-content:center;">▼</button>
        <div id="$choicesWrapId" style="display:none;position:absolute;left:0;right:0;top:44px;z-index:30;border:1px solid #cbd5e1;border-radius:8px;background:#fff;box-shadow:0 8px 24px rgba(15,23,42,0.16);max-height:220px;overflow:auto;"></div>
    </div>
""".trimIndent()

/** Fill an Email PDF To field from Rixo Email Map. The first saved address is filled in. Several stay behind the ▼ button. */
fun bindRixoCompanyEmailChoices(company: String, inputId: String, choicesWrapId: String) {
    val name = company.trim()
    val toggle = document.getElementById("${choicesWrapId}Toggle") as? HTMLButtonElement
    val wrap = document.getElementById(choicesWrapId) as? HTMLElement
    if (name.isEmpty() || name.equals("Undefined", ignoreCase = true)) {
        toggle?.style?.display = "none"
        return
    }
    val encoded = js("encodeURIComponent")(name).unsafeCast<String>()
    window.fetch(apiUrl("rixo-email-map/by-company?company=$encoded"))
        .then { r: dynamic -> if (r.ok) r.json() else js("Promise.resolve(null)") }
        .then { body: dynamic ->
            val raw = js("(function(b){ var d = b && b.emails; return Array.isArray(d) ? d : []; })")(body)
            val emails = (raw as Array<dynamic>).map { it?.toString()?.trim().orEmpty() }.filter { it.isNotEmpty() }
            val input = document.getElementById(inputId) as? HTMLInputElement ?: return@then
            if (emails.size == 1) {
                input.value = emails[0]
                input.style.paddingRight = "12px"
                toggle?.style?.display = "none"
                if (wrap != null) wrap.style.display = "none"
                return@then
            }
            if (emails.size < 2 || wrap == null || toggle == null) {
                toggle?.style?.display = "none"
                return@then
            }
            input.value = emails[0]
            input.style.paddingRight = "44px"
            wrap.innerHTML = emails.joinToString("") { addr ->
                val safe = escapeHtml(addr)
                """<button type="button" data-rixo-email-choice="$safe" style="display:block;width:100%;text-align:left;padding:10px 12px;border:none;border-bottom:1px solid #e5e7eb;background:#fff;cursor:pointer;font-size:14px;color:#0f172a;">$safe</button>"""
            }
            wrap.style.display = "none"
            toggle.style.display = "flex"
            toggle.addEventListener("click", { ev: Event ->
                ev.preventDefault()
                ev.stopPropagation()
                wrap.style.display = if (wrap.style.display == "none") "block" else "none"
            })
            wrap.addEventListener("click", { ev: Event ->
                ev.stopPropagation()
                val target = ev.target as? HTMLElement ?: return@addEventListener
                val btn = target.closest("button[data-rixo-email-choice]") as? HTMLElement ?: return@addEventListener
                val chosen = btn.getAttribute("data-rixo-email-choice")?.trim().orEmpty()
                if (chosen.isNotEmpty()) input.value = chosen
                wrap.style.display = "none"
            })
            lateinit var closer: (Event) -> Unit
            closer = { ev: Event ->
                if (wrap.parentNode == null) {
                    document.removeEventListener("click", closer)
                } else {
                    val target = ev.target as? HTMLElement
                    val inside = target != null && (wrap.contains(target) || toggle.contains(target))
                    if (!inside) wrap.style.display = "none"
                }
            }
            document.addEventListener("click", closer)
        }
        .catch { _: dynamic -> }
}
