package com.automan.purchase

import kotlin.js.JSON
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

private var purchaseBulkEditMode: Boolean = false
private val purchaseBulkDrafts: MutableMap<String, String> = mutableMapOf()
private val purchaseBulkListCache: MutableMap<String, List<String>> = mutableMapOf()
private val purchaseBulkListLoading: MutableSet<String> = mutableSetOf()

/** Column key to an existing list API. Fetched once, then reused for every row. */
private val purchaseBulkComboApis = mapOf(
    "chassis" to "car-brand-mapping/chassis/all",
    "brand" to "master-menu/car_brands",
    "carName" to "car-brand-mapping/car-names/distinct",
    "color" to "master-menu/color",
    "fuel" to "master-menu/fuel",
    "shift" to "master-menu/shift",
    "grade" to "master-menu/car_grade",
    "rank" to "master-menu/rank",
    "shipmentSize" to "master-menu/type_of_vehicle",
    "auctionHouse" to "rixo/dropdowns/auction-names",
    "venueId" to "master-menu/venue_id",
    "stockLocation" to "master-menu/stock_location",
    "rixoCompany" to "rixo-mapping/distinct-rixo-companies",
    "clientName" to "client-map/dropdowns/client-names",
    "country" to "master-menu/country",
    "consignee" to "booking/mappings/consignee-names",
    "repairCompany" to "master-menu/repair_company",
)

private val purchaseBulkComboFields: Set<String> =
    purchaseBulkComboApis.keys + setOf("wd", "driveType")

private val purchaseBulkMoneyFields = setOf(
    "price", "auctionFee", "auctionPenaltyFee", "recycleFee", "roadTax", "taxTotal", "totalPrice",
    "rixoPrice", "shipmentCharges", "freight", "storageCharges", "miscCharges", "inspectionFee",
    "commission", "repairCharges", "profit",
)

private val purchaseBulkDateFields = setOf("date", "paymentDate", "shipmentDate")

private val purchaseBulkBoolFields = setOf(
    "shaken", "negotiate", "local", "bookingRequested", "invoiceConfirmed", "isPackageMode",
)

private val purchaseBulkTrueStringFields = setOf("rixoRequested", "rixoConfirmed")

private val purchaseBulkSpecFields = setOf(
    "grade", "rank", "color", "fuel", "seat", "door", "cc", "shift", "wd", "driveType", "shipmentSize",
)

/** Shipping fields [LocalPurchaseSanitizer] clears. Already-LOCAL rows keep these read-only. */
private val purchaseBulkLocalLockedFields = setOf(
    "shipmentDate", "blNo", "vessel", "bookingId", "bookingRequested",
    "shipmentCharges", "freight", "inspectionFee",
)

/** Only these purchase-list columns get inline editors in bulk mode. */
private val purchaseBulkEditableFields = setOf(
    "auctionFee",
    "roadTax",
    "auctionPenaltyFee",
    "inspectionFee",
    "shipmentCharges",
    "freight",
    "rixoPrice",
    "stockLocation",
    "rixoCompany",
)

private class PurchaseBulkChange(
    val purchaseId: Long,
    val chassis: String,
    val field: String,
    val label: String,
    val oldDisplay: String,
    val newDisplay: String,
    val jsonValue: dynamic,
    val explicitNull: Boolean,
)

fun purchaseBulkEditIsActive(): Boolean = purchaseBulkEditMode

/**
 * Returns false when the user wants to keep unsaved bulk edits.
 * A clean edit session ends so the existing navigation can run.
 */
fun purchaseBulkEditAllowLeave(): Boolean {
    if (!purchaseBulkEditMode) return true
    if (document.getElementById("purchaseTable") == null) {
        purchaseBulkEditExit()
        return true
    }
    capturePurchaseBulkDraftsFromDom()
    if (collectPurchaseBulkChanges().isEmpty()) {
        purchaseBulkEditExit()
        return true
    }
    val discard = window.confirm("Discard bulk edits on this page?")
    if (!discard) return false
    purchaseBulkEditExit()
    return true
}

fun purchaseBulkEditExit() {
    purchaseBulkEditMode = false
    purchaseBulkDrafts.clear()
    syncPurchaseBulkEditButton()
}

fun onPurchaseBulkEditButtonClick() {
    if (!isEditor()) return
    if (!purchaseBulkEditMode) {
        purchaseBulkDrafts.clear()
        purchaseBulkEditMode = true
        syncPurchaseBulkEditButton()
        ensurePurchaseBulkLists()
        displayPurchasesWithPagination()
        return
    }
    capturePurchaseBulkDraftsFromDom()
    showPurchaseBulkSummary(collectPurchaseBulkChanges())
}

fun syncPurchaseBulkEditButton() {
    val btn = document.getElementById("purchaseBulkEditBtn") as? HTMLElement ?: return
    if (purchaseBulkEditMode) {
        btn.textContent = "Bulk Save"
        btn.classList.add("is-save")
        btn.title = "Review and save fee, Rixo Price, stock location, and Rixo Company edits on this page"
        btn.setAttribute("aria-label", "Bulk Save")
    } else {
        btn.textContent = "Bulk Edit"
        btn.classList.remove("is-save")
        btn.title = "Edit fees, Rixo Price, stock location, and Rixo Company on this page"
        btn.setAttribute("aria-label", "Bulk Edit")
    }
}

fun capturePurchaseBulkDraftsFromDom() {
    if (!purchaseBulkEditMode) return
    val nodes = document.querySelectorAll("#purchaseTable [data-bulk-field]")
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? Element ?: continue
        val id = el.getAttribute("data-bulk-id")?.trim().orEmpty()
        val field = el.getAttribute("data-bulk-field")?.trim().orEmpty()
        if (id.isEmpty() || field.isEmpty()) continue
        purchaseBulkDrafts["$id|$field"] = readPurchaseBulkControl(el)
    }
}

fun purchaseBulkEditCellHtml(purchase: dynamic, columnKey: String, displayHtml: String): String {
    if (!purchaseBulkEditMode) return displayHtml
    val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
    if (purchaseId <= 0L) return displayHtml
    if (columnKey !in purchaseBulkEditableFields) return displayHtml
    if (purchaseBulkRowIsLocal(purchase) && columnKey in purchaseBulkLocalLockedFields) return displayHtml
    val seed = purchaseBulkDrafts["$purchaseId|$columnKey"] ?: purchaseBulkEditorSeed(purchase, columnKey)
    val idAttr = purchaseId.toString()
    val fieldAttr = escapeAttr(columnKey)
    val common = """data-bulk-id="$idAttr" data-bulk-field="$fieldAttr" class="purchase-bulk-input""""
    return when {
        columnKey in purchaseBulkBoolFields || columnKey in purchaseBulkTrueStringFields -> {
            val checked = if (seed == "1") " checked" else ""
            """<input type="checkbox" $common$checked aria-label="${escapeAttr(purchaseBulkFieldLabel(columnKey))}">"""
        }
        columnKey in purchaseBulkDateFields ->
            purchaseBulkPickerHtml(purchaseId, columnKey, seed, "date")
        columnKey == "carModelYear" ->
            purchaseBulkPickerHtml(purchaseId, columnKey, seed, "month")
        columnKey == "manufactureYear" ->
            purchaseBulkPickerHtml(purchaseId, columnKey, seed, "year")
        columnKey == "notes" ->
            """<textarea $common rows="2" aria-label="Notes">${escapeHtml(seed)}</textarea>"""
        columnKey in purchaseBulkMoneyFields || columnKey == "cc" || columnKey == "bookingId" ->
            """<input type="text" inputmode="decimal" $common value="${escapeAttr(seed)}" aria-label="${escapeAttr(purchaseBulkFieldLabel(columnKey))}">"""
        columnKey in purchaseBulkComboFields ->
            purchaseBulkComboHtml(purchaseId, columnKey, seed)
        else ->
            """<input type="text" $common value="${escapeAttr(seed)}" aria-label="${escapeAttr(purchaseBulkFieldLabel(columnKey))}">"""
    }
}

/** Wires calendar, month, and year buttons, and fills combobox lists, after the table is drawn. */
fun bindPurchaseBulkEditors() {
    if (!purchaseBulkEditMode) return
    ensurePurchaseBulkLists()
    fillPurchaseBulkCombos()
    val pickers = document.querySelectorAll("#purchaseTable [data-bulk-picker]")
    for (i in 0 until pickers.length) {
        val el = pickers.item(i) as? HTMLElement ?: continue
        val base = el.getAttribute("data-bulk-base")?.trim().orEmpty()
        if (base.isEmpty()) continue
        when (el.getAttribute("data-bulk-picker")) {
            "date" -> bindStrictDateTextMask(base)
            "month" -> bindStrictMonthYearTextMask(base)
            "year" -> bindStrictYearTextMask(base)
        }
    }
}

private fun purchaseBulkComboHtml(purchaseId: Long, columnKey: String, seed: String): String {
    val comboId = "bulkc${purchaseId}_$columnKey"
    val safeSeed = seed.replace("\"", "")
    val attrs = """data-bulk-id="$purchaseId" data-bulk-field="${escapeAttr(columnKey)}" class="purchase-bulk-input" aria-label="${escapeAttr(purchaseBulkFieldLabel(columnKey))}""""
    return """<div class="purchase-bulk-combo">${createEditableCombobox(comboId, "", initialValue = safeSeed, additionalAttrs = attrs)}</div>"""
}

private fun purchaseBulkPickerHtml(purchaseId: Long, columnKey: String, seed: String, kind: String): String {
    val base = "bulk$kind${purchaseId}_$columnKey"
    val label = escapeAttr(purchaseBulkFieldLabel(columnKey))
    val pickerLabel = when (kind) {
        "month" -> "Open month picker"
        "year" -> "Open year picker"
        else -> "Open calendar"
    }
    val maxLength = when (kind) {
        "date" -> "10"
        "month" -> "7"
        else -> "4"
    }
    val hiddenType = if (kind == "date") "date" else "hidden"
    val hiddenExtra = if (kind == "date") """ tabindex="-1" aria-hidden="true"""" else ""
    return """
        <div class="purchase-bulk-date" data-bulk-picker="$kind" data-bulk-base="$base">
            <input type="text" id="${base}Text" maxlength="$maxLength" inputmode="numeric" autocomplete="off" class="purchase-bulk-input" aria-label="$label">
            <button type="button" id="${base}CalendarBtn" class="purchase-form-calendar-btn" title="$pickerLabel" aria-label="$pickerLabel">${purchaseFormCalendarIconSvg()}</button>
            <input type="$hiddenType" id="$base" data-bulk-id="$purchaseId" data-bulk-field="${escapeAttr(columnKey)}" value="${escapeAttr(seed)}"$hiddenExtra>
        </div>
    """.trimIndent()
}

private fun ensurePurchaseBulkLists() {
    if (!purchaseBulkListCache.containsKey("wd")) purchaseBulkListCache["wd"] = listOf("2WD", "4WD")
    if (!purchaseBulkListCache.containsKey("driveType")) purchaseBulkListCache["driveType"] = listOf("LHD", "RHD")
    for ((field, path) in purchaseBulkComboApis) {
        if (purchaseBulkListCache.containsKey(field) || field in purchaseBulkListLoading) continue
        purchaseBulkListLoading.add(field)
        val fieldKey = field
        window.fetch(apiUrl(path))
            .then { response -> if (response.ok) response.json() else js("[]") }
            .then { raw: dynamic ->
                purchaseBulkListCache[fieldKey] = parsePurchaseBulkOptionList(raw)
                purchaseBulkListLoading.remove(fieldKey)
                if (purchaseBulkEditMode) fillPurchaseBulkCombos()
            }
            .catch { _: dynamic ->
                purchaseBulkListCache[fieldKey] = emptyList()
                purchaseBulkListLoading.remove(fieldKey)
            }
    }
}

private fun parsePurchaseBulkOptionList(raw: dynamic): List<String> {
    return try {
        val chassis = raw?.chassisList
        if (chassis != null && js("Array.isArray(chassis)").unsafeCast<Boolean>()) {
            chassis.unsafeCast<Array<dynamic>>().map { it.toString().trim() }.filter { it.isNotEmpty() }
        } else {
            val parsed = parseApiDataStringArray(raw)
            if (parsed.isNotEmpty()) parsed else parseMasterListArray(raw)
        }
    } catch (_: dynamic) {
        emptyList()
    }
}

private fun fillPurchaseBulkCombos() {
    val selects = document.querySelectorAll("#purchaseTable select[id^='bulkc']")
    for (i in 0 until selects.length) {
        val select = selects.item(i) as? HTMLSelectElement ?: continue
        val field = select.id.substringAfter("_", "")
        if (field.isEmpty()) continue
        val options = purchaseBulkListCache[field] ?: continue
        val input = document.getElementById("${select.id}Input") as? HTMLInputElement
        val current = input?.value?.trim().orEmpty()
        while (select.options.length > 1) select.remove(1)
        val seen = mutableSetOf<String>()
        fun addOption(value: String) {
            val trimmed = value.trim()
            if (trimmed.isEmpty() || !seen.add(trimmed.lowercase())) return
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = trimmed
            opt.text = trimmed
            select.add(opt)
        }
        options.forEach { addOption(it) }
        if (current.isNotEmpty()) addOption(current)
        if (current.isNotEmpty()) select.value = current
    }
}

private fun purchaseBulkFieldLabel(columnKey: String): String =
    purchaseListColumnLabels()[columnKey] ?: columnKey

private fun purchaseBulkRowIsLocal(purchase: dynamic): Boolean = purchaseBulkFlag(purchase, "local")

private fun purchaseBulkFlag(purchase: dynamic, key: String): Boolean {
    if (key == "invoiceConfirmed") {
        val cell = purchaseSoldOrInvoiceConfirmedCell(purchase)
        return cell.equals("TRUE", ignoreCase = true) || cell.equals("Yes", ignoreCase = true)
    }
    val v = purchase[key]
    if (v == true || v == 1) return true
    val s = v?.toString()?.trim()?.lowercase() ?: return false
    return s == "true" || s == "1" || s == "yes"
}

private fun purchaseBulkRaw(purchase: dynamic, columnKey: String): String {
    if (columnKey in purchaseBulkSpecFields) {
        val explicit = purchase.vehicleSpecExplicit
        if (explicit != null && explicit != js("undefined")) {
            val v = js("explicit[columnKey]")
            if (v != null && v != js("undefined")) return v.toString().trim()
        }
        return ""
    }
    val key = if (columnKey == "invoiceConfirmed") "invoiceConfirmed" else columnKey
    val v = purchase[key]
    if (v == null || v == js("undefined")) {
        if (columnKey == "bookingId") {
            val alt = purchase["booking_id"]
            if (alt != null && alt != js("undefined")) return alt.toString().trim()
        }
        return ""
    }
    return when (v) {
        is Boolean -> if (v) "true" else "false"
        is Number -> {
            val d = v.toDouble()
            if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
        }
        else -> v.toString().trim()
    }
}

private fun purchaseBulkEditorSeed(purchase: dynamic, columnKey: String): String {
    return when {
        columnKey in purchaseBulkDateFields -> toIsoFromLabel(purchaseBulkRaw(purchase, columnKey))
        columnKey in purchaseBulkMoneyFields -> extractNumericFromDbValue(purchaseBulkRaw(purchase, columnKey))
        columnKey in purchaseBulkBoolFields || columnKey in purchaseBulkTrueStringFields ->
            if (purchaseBulkFlag(purchase, columnKey)) "1" else "0"
        columnKey == "carModelYear" -> {
            val raw = purchaseBulkRaw(purchase, columnKey)
            carModelYearToMonthInputValue(raw).ifEmpty { raw }
        }
        columnKey == "cc" || columnKey == "bookingId" -> extractNumericFromDbValue(purchaseBulkRaw(purchase, columnKey)).substringBefore(".")
        else -> purchaseBulkRaw(purchase, columnKey)
    }
}

private fun readPurchaseBulkControl(el: Element): String {
    val input = el as? HTMLInputElement
    if (input != null) {
        if (input.type == "checkbox") return if (input.checked) "1" else "0"
        return input.value
    }
    val area = el as? HTMLTextAreaElement
    if (area != null) return area.value
    return ""
}

private fun purchaseBulkDigits(raw: String): String =
    raw.replace(Regex("[^0-9.]"), "")

private fun collectPurchaseBulkChanges(): List<PurchaseBulkChange> {
    val changes = mutableListOf<PurchaseBulkChange>()
    val columns = getSelectedColumns()
    val rows = computePurchaseListViewSlice().rows
    for (purchase in rows) {
        val purchaseId = (purchase.id as? Number)?.toLong() ?: continue
        if (purchaseId <= 0L) continue
        val chassis = purchaseBulkRaw(purchase, "chassis")
        val localLocked = purchaseBulkRowIsLocal(purchase)
        var turningLocalOn = false
        for (columnKey in columns) {
            if (columnKey !in purchaseBulkEditableFields) continue
            if (localLocked && columnKey in purchaseBulkLocalLockedFields) continue
            val editor = purchaseBulkDrafts["$purchaseId|$columnKey"] ?: continue
            val change = purchaseBulkChangeOrNull(purchase, purchaseId, chassis, columnKey, editor) ?: continue
            if (columnKey == "local" && editor == "1") turningLocalOn = true
            changes.add(change)
        }
        if (turningLocalOn && !localLocked) {
            changes.add(
                PurchaseBulkChange(
                    purchaseId = purchaseId,
                    chassis = chassis,
                    field = "localNote",
                    label = "LOCAL",
                    oldDisplay = "",
                    newDisplay = "Shipment date, BL, vessel, booking, freight, and inspection fee are cleared",
                    jsonValue = null,
                    explicitNull = false,
                ),
            )
        }
    }
    return changes
}

private fun purchaseBulkChangeOrNull(
    purchase: dynamic,
    purchaseId: Long,
    chassis: String,
    columnKey: String,
    editor: String,
): PurchaseBulkChange? {
    val label = purchaseBulkFieldLabel(columnKey)
    val trimmed = editor.trim()
    return when {
        columnKey in purchaseBulkDateFields -> {
            val raw = purchaseBulkRaw(purchase, columnKey)
            val originalIso = toIsoFromLabel(raw)
            if (trimmed == originalIso) return null
            val saved = if (trimmed.isEmpty()) "" else formatWithWeekday(trimmed)
            PurchaseBulkChange(purchaseId, chassis, columnKey, label, formatPurchaseListYmdWeekday(raw), formatPurchaseListYmdWeekday(saved), saved, false)
        }
        columnKey in purchaseBulkMoneyFields -> {
            val original = purchaseBulkDigits(extractNumericFromDbValue(purchaseBulkRaw(purchase, columnKey)))
            val next = purchaseBulkDigits(trimmed)
            if (next == original) return null
            val saved = if (next.isEmpty()) "" else "¥$next"
            val oldDisplay = purchaseTableCellValue(purchase, columnKey)
            PurchaseBulkChange(purchaseId, chassis, columnKey, label, oldDisplay, if (saved.isEmpty()) "" else saved, saved, false)
        }
        columnKey in purchaseBulkBoolFields -> {
            val next = trimmed == "1"
            if (next == purchaseBulkFlag(purchase, columnKey)) return null
            PurchaseBulkChange(
                purchaseId, chassis, columnKey, label,
                if (purchaseBulkFlag(purchase, columnKey)) "Yes" else "No",
                if (next) "Yes" else "No",
                next,
                false,
            )
        }
        columnKey in purchaseBulkTrueStringFields -> {
            val next = trimmed == "1"
            if (next == purchaseBulkFlag(purchase, columnKey)) return null
            val saved = if (next) "TRUE" else "FALSE"
            PurchaseBulkChange(
                purchaseId, chassis, columnKey, label,
                if (purchaseBulkFlag(purchase, columnKey)) "Yes" else "No",
                if (next) "Yes" else "No",
                saved,
                false,
            )
        }
        columnKey == "carModelYear" -> {
            val raw = purchaseBulkRaw(purchase, columnKey)
            val original = carModelYearToMonthInputValue(raw).ifEmpty { raw }
            if (trimmed == original.trim()) return null
            PurchaseBulkChange(purchaseId, chassis, columnKey, label, raw, trimmed, trimmed, false)
        }
        columnKey == "cc" -> {
            val original = purchaseBulkDigits(purchaseBulkRaw(purchase, columnKey)).substringBefore(".")
            val next = purchaseBulkDigits(trimmed).substringBefore(".")
            if (next == original) return null
            PurchaseBulkChange(purchaseId, chassis, columnKey, label, original, next, next, false)
        }
        columnKey == "bookingId" -> {
            val original = purchaseBulkDigits(purchaseBulkRaw(purchase, columnKey)).substringBefore(".")
            val next = purchaseBulkDigits(trimmed).substringBefore(".")
            if (next == original) return null
            val number = next.toLongOrNull()
            PurchaseBulkChange(purchaseId, chassis, columnKey, label, original, next, number, number == null)
        }
        else -> {
            val original = purchaseBulkRaw(purchase, columnKey).trim()
            if (trimmed == original) return null
            PurchaseBulkChange(purchaseId, chassis, columnKey, label, original, trimmed, trimmed, false)
        }
    }
}

private fun showPurchaseBulkSummary(changes: List<PurchaseBulkChange>) {
    document.getElementById("purchaseBulkSummary")?.remove()
    val rows = StringBuilder()
    for (change in changes) {
        if (change.field == "localNote") continue
        rows.append(
            """<tr>
                <td>${escapeHtml(change.chassis)}</td>
                <td>${escapeHtml(change.label)}</td>
                <td>${escapeHtml(change.oldDisplay)}</td>
                <td>${escapeHtml(change.newDisplay)}</td>
            </tr>""",
        )
    }
    val localNotes = changes.filter { it.field == "localNote" }
        .joinToString("") { """<p class="purchase-bulk-note">${escapeHtml(it.chassis)}: ${escapeHtml(it.newDisplay)}</p>""" }
    val reviewBody = if (rows.isEmpty()) {
        """<p class="purchase-bulk-note">No changes</p>"""
    } else {
        """<div class="purchase-bulk-summary-table-wrap">
                <table>
                    <thead><tr><th>Chassis</th><th>Column</th><th>From</th><th>To</th></tr></thead>
                    <tbody>$rows</tbody>
                </table>
            </div>"""
    }
    val overlay = document.createElement("div") as HTMLElement
    overlay.id = "purchaseBulkSummary"
    overlay.className = "purchase-bulk-summary-overlay"
    overlay.innerHTML = """
        <div class="purchase-bulk-summary" role="dialog" aria-modal="true" aria-labelledby="purchaseBulkSummaryTitle">
            <h2 id="purchaseBulkSummaryTitle">Review bulk edits</h2>
            <p id="purchaseBulkSummaryError" class="purchase-bulk-summary-error" style="display:none;"></p>
            $localNotes
            $reviewBody
            <div class="purchase-bulk-summary-actions">
                <button type="button" id="purchaseBulkSummaryCancel">Cancel</button>
                <button type="button" id="purchaseBulkSummaryConfirm">Save</button>
            </div>
        </div>
    """
    document.body?.appendChild(overlay)
    document.getElementById("purchaseBulkSummaryCancel")?.addEventListener("click", { _: Event ->
        overlay.remove()
    })
    document.getElementById("purchaseBulkSummaryConfirm")?.addEventListener("click", { _: Event ->
        if (changes.isEmpty()) {
            overlay.remove()
            purchaseBulkEditExit()
            displayPurchasesWithPagination()
        } else {
            savePurchaseBulkChanges(changes)
        }
    })
}

private fun savePurchaseBulkChanges(changes: List<PurchaseBulkChange>) {
    val confirmBtn = document.getElementById("purchaseBulkSummaryConfirm") as? HTMLElement
    confirmBtn?.setAttribute("disabled", "true")
    val errorEl = document.getElementById("purchaseBulkSummaryError") as? HTMLElement
    errorEl?.style?.display = "none"
    val byId = linkedMapOf<Long, MutableList<PurchaseBulkChange>>()
    for (change in changes) {
        if (change.field == "localNote") continue
        byId.getOrPut(change.purchaseId) { mutableListOf() }.add(change)
    }
    val queue = byId.entries.toList()
    fun finishSuccess() {
        document.getElementById("purchaseBulkSummary")?.remove()
        purchaseBulkEditExit()
        showMessage("Bulk edits saved", "success")
        val page0 = if (purchaseSearchServerMode) purchaseSearchServerPageZeroBased.coerceAtLeast(0) else (currentPage - 1).coerceAtLeast(0)
        loadPurchasesListPage(page0)
    }
    fun saveAt(index: Int) {
        if (index >= queue.size) {
            finishSuccess()
            return
        }
        val (id, rowChanges) = queue[index]
        val payload = js("{}")
        val fields = js("[]")
        for (change in rowChanges) {
            if (change.explicitNull) {
                payload[change.field] = null
            } else {
                payload[change.field] = change.jsonValue
            }
            fields.push(change.field)
        }
        payload.auditChangedFields = fields
        val init = js("{}")
        init.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        init.headers = headers
        init.body = JSON.stringify(payload)
        window.fetch(apiUrl("purchases/$id"), init)
            .then { response ->
                if (response.ok) {
                    applyPurchaseBulkChangesToMemory(id, rowChanges)
                    saveAt(index + 1)
                } else {
                    response.text().then { text ->
                        val chassis = rowChanges.firstOrNull()?.chassis ?: id.toString()
                        val message = ErrorHandler.extractErrorMessage(text?.toString() ?: "")
                        confirmBtn?.removeAttribute("disabled")
                        if (errorEl != null) {
                            errorEl.style.display = "block"
                            errorEl.textContent = "Could not save $chassis. $message"
                        }
                    }
                }
            }
            .catch { err: dynamic ->
                confirmBtn?.removeAttribute("disabled")
                if (errorEl != null) {
                    errorEl.style.display = "block"
                    errorEl.textContent = err?.toString() ?: "Save failed"
                }
            }
    }
    saveAt(0)
}

private fun applyPurchaseBulkChangesToMemory(purchaseId: Long, changes: List<PurchaseBulkChange>) {
    val purchase = findPurchaseInListById(purchaseId) ?: return
    for (change in changes) {
        if (change.explicitNull) {
            purchase[change.field] = null
        } else {
            purchase[change.field] = change.jsonValue
        }
        if (change.field in purchaseBulkSpecFields) {
            var explicit = purchase.vehicleSpecExplicit
            if (explicit == null || explicit == js("undefined")) {
                explicit = js("{}")
                purchase.vehicleSpecExplicit = explicit
            }
            explicit[change.field] = if (change.explicitNull) null else change.jsonValue
        }
        purchaseBulkDrafts.remove("$purchaseId|${change.field}")
    }
}
