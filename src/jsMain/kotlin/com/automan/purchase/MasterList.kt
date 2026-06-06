package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler

// Global pagination variables for Car Brands
var carBrandsCurrentPage = 1
var carBrandsItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarBrands: List<dynamic> = emptyList()

/** When true, list data comes from [car-brand-mapping/mappings/page-search]. */
var carBrandMapSearchServerMode: Boolean = false
var carBrandMapSearchTotal: Long = 0
var carBrandMapSearchTotalPages: Int = 0
var carBrandMapSearchPageZeroBased: Int = 0
/** API field: all | chassis | brand | carName */
var carBrandMapSearchFieldChoice: String = "all"
private var carBrandMapSearchDebounceTimer: dynamic = null

var clientMapCurrentPage = 1
var clientMapItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allClientMaps: List<dynamic> = emptyList()
var lastClientMapDeviceType: String? = getDeviceType()

// Global pagination variables for Suppliers
var suppliersCurrentPage = 1
var suppliersItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allSuppliers: List<dynamic> = emptyList()

/** When true, list data comes from [rixo/prices/page-search]. */
var supplierMapSearchServerMode: Boolean = false
var supplierMapSearchTotal: Long = 0
var supplierMapSearchTotalPages: Int = 0
var supplierMapSearchPageZeroBased: Int = 0
/** API field: all | supplierName | stockLocation | rixoCompany */
var supplierMapSearchFieldChoice: String = "all"
private var supplierMapSearchDebounceTimer: dynamic = null

// Global variable to track last device type for Supplier page
var lastSupplierDeviceType: String? = getDeviceType()

/** Active column sort for Car Brands Map / Supplier Map / Consignee Map tables (same UX as purchase list / simple master). */
private var carBrandMapSortField: String? = null
private val carBrandMapSortOrderByField: MutableMap<String, String> = mutableMapOf()

private var supplierMapSortField: String? = null
private val supplierMapSortOrderByField: MutableMap<String, String> = mutableMapOf()

private var consigneeMapSortField: String? = null
private val consigneeMapSortOrderByField: MutableMap<String, String> = mutableMapOf()

/** Notes field removed from Consignee Map modal; snapshot keeps DB value on edit/duplicate saves. */
private var consigneeModalNotesSnapshot: String? = null

/** Supplier Map tree (`rixo_prices`): one UI row per DB row; grouped visually by supplier → stock. */
private data class SupplierPriceRowLite(
    val id: Long,
    val supplier: String,
    val stock: String,
    val rixoCompany: String,
    val venueId: String,
    val pol: String,
)

private var supplierMapTreeRowsCache: List<SupplierPriceRowLite> = emptyList()
private var supplierTreeSelectedSupplier: String? = null
private var supplierTreeSelectedStock: String? = null
private var supplierTreePolEditRowId: Long? = null
private var supplierTreePolEditBranchIdx: Int? = null
private var supplierTreeVenueEditRowId: Long? = null
private var supplierTreeVenueEditBranchIdx: Int? = null
private var supplierTreeSelectedStockRowId: Long? = null
private var supplierTreeSelectedStockBranchIdx: Int? = null
private var supplierTreeLeafAddRowId: Long? = null
private var supplierTreeLeafAddBranchIdx: Int? = null
private var supplierTreeInlineAddLevel: String? = null
private var supplierTreeInlineSupplier: String = ""
private var supplierTreeInlineStock: String = ""
/** Full parallel-branch inline edit (stock + venue + POL + Rixo); one >> commits all. */
private var supplierTreeBranchEditRowId: Long? = null
private var supplierTreeBranchEditBranchIdx: Int? = null
private var supplierTreeSupplierEditName: String? = null

/** Matches `CarBrandMapping.carName` / DB column `car_name` length. */
private const val CAR_BRAND_CAR_NAME_MAX_LEN = 100

private fun joinDistinctNonBlank(values: List<String>): String {
    val seen = HashSet<String>()
    val out = mutableListOf<String>()

    for (raw in values) {
        // Each "raw" might already contain semicolon-separated values.
        val tokens = raw.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        for (t in tokens) {
            val key = t.uppercase()
            if (seen.add(key)) out.add(t)
        }
    }
    return out.joinToString(";")
}

private fun escapeJsString(s: String): String {
    return s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
}

private fun masterMapColumnSortTooltip(orderByField: Map<String, String>, field: String): String {
    val ord = orderByField[field] ?: "desc"
    return if (ord == "asc") "Sorted A-Z (click to sort Z-A)" else "Sorted Z-A (click to sort A-Z)"
}

private fun extractCarBrandSortKey(m: dynamic, field: String): String {
    val raw = when (field) {
        "chassis" -> (m.chassis ?: "").toString()
        "carBrand" -> (m.carBrand ?: "").toString()
        "carName" -> (m.carName ?: "").toString()
        "fuel" -> (m.fuel ?: "").toString()
        "vehicleType" -> (m.vehicleType ?: "").toString()
        else -> ""
    }
    return raw.trim().lowercase()
}

private fun extractSupplierSortKey(p: dynamic, field: String): String {
    val raw = when (field) {
        "supplierName" -> (p.auctionHouse ?: "").toString()
        "stockLocation" -> (p.stockLocation ?: "").toString()
        "rixoCompany" -> (p.rixoCompany ?: "").toString()
        "venueId" -> (p.venueId ?: "").toString()
        "pol" -> (p.pol ?: "").toString()
        else -> ""
    }
    return raw.trim().lowercase()
}

private fun extractConsigneeMapSortKey(m: dynamic, field: String): String {
    val raw = when (field) {
        "consigneeName" -> (m.consigneeName ?: "").toString()
        "country" -> (m.country ?: "").toString()
        "pod" -> (m.pod ?: "").toString()
        else -> ""
    }
    return raw.trim().lowercase()
}

private fun toggleCarBrandMapSort(field: String) {
    if (carBrandMapSearchServerMode) {
        showMessage("Clear the search box to sort the full list.", "info")
        return
    }
    val cur = carBrandMapSortOrderByField[field] ?: "desc"
    carBrandMapSortOrderByField[field] = if (cur == "asc") "desc" else "asc"
    carBrandMapSortField = field
    carBrandsCurrentPage = 1
    loadMasterCarBrands()
}

private fun toggleSupplierMapSort(field: String) {
    if (supplierMapSearchServerMode) {
        showMessage("Clear the search box to sort the full list.", "info")
        return
    }
    val cur = supplierMapSortOrderByField[field] ?: "desc"
    supplierMapSortOrderByField[field] = if (cur == "asc") "desc" else "asc"
    supplierMapSortField = field
    suppliersCurrentPage = 1
    loadMasterSuppliers()
}

private fun toggleConsigneeMapSort(field: String) {
    if (consigneeMapSearchServerMode) {
        showMessage("Clear the search box to sort the full list.", "info")
        return
    }
    val cur = consigneeMapSortOrderByField[field] ?: "desc"
    consigneeMapSortOrderByField[field] = if (cur == "asc") "desc" else "asc"
    consigneeMapSortField = field
    consigneesCurrentPage = 1
    loadMasterConsignee()
}

/**
 * View-only grouping for Supplier Map:
 * - Group rows by Supplier Name (auctionHouse)
 * - Join other column values with ';'
 * - Keep the first (newest) row's id for Edit/Duplicate actions so modals stay unchanged
 */
private fun groupSupplierPricesForView(prices: List<dynamic>): List<dynamic> {
    if (prices.isEmpty()) return prices

    val buckets = LinkedHashMap<String, MutableList<dynamic>>()
    for (p in prices) {
        val idStr = (p.id ?: "").toString()
        val supplierName = (p.auctionHouse ?: "").toString().trim()
        val key = if (supplierName.isNotEmpty()) supplierName.uppercase() else "__EMPTY__:$idStr"
        val list = buckets.getOrPut(key) { mutableListOf() }
        list.add(p)
    }

    val grouped = mutableListOf<dynamic>()
    for ((_, list) in buckets) {
        val first = list.first()
        val groupedObj: dynamic = js("({})")

        // Use first row's id so existing edit/duplicate modals still work unchanged
        groupedObj.id = first.id
        groupedObj.auctionHouse = (first.auctionHouse ?: "").toString()

        groupedObj.stockLocation = joinDistinctNonBlank(list.map { (it.stockLocation ?: "").toString() })
        groupedObj.rixoCompany = joinDistinctNonBlank(list.map { (it.rixoCompany ?: "").toString() })
        groupedObj.venueId = joinDistinctNonBlank(list.map { (it.venueId ?: "").toString() })
        groupedObj.rixoPrice = joinDistinctNonBlank(list.map { (it.rixoPrice ?: "").toString() })
        groupedObj.pol = joinDistinctNonBlank(list.map { (it.pol ?: "").toString() })
        groupedObj.shipmentSize = joinDistinctNonBlank(list.map { (it.shipmentSize ?: "").toString() })

        grouped.add(groupedObj)
    }

    return grouped
}

/**
 * View-only grouping for Car Brands Map:
 * - Group rows by Chassis (normalized uppercase)
 * - Join other column values with ';' (deduped like Supplier Map)
 * - Keep the first (newest) row's id for Edit/Duplicate actions
 */
private fun groupCarBrandMappingsForView(mappings: List<dynamic>): List<dynamic> {
    if (mappings.isEmpty()) return mappings

    val buckets = LinkedHashMap<String, MutableList<dynamic>>()
    for (m in mappings) {
        val idStr = (m.id ?: "").toString()
        val chassis = (m.chassis ?: "").toString().trim()
        val key = if (chassis.isNotEmpty()) chassis.uppercase() else "__EMPTY__:$idStr"
        val list = buckets.getOrPut(key) { mutableListOf() }
        list.add(m)
    }

    val grouped = mutableListOf<dynamic>()
    for ((_, list) in buckets) {
        val first = list.first()
        val groupedObj: dynamic = js("({})")

        groupedObj.id = first.id
        groupedObj.chassis = (first.chassis ?: "").toString().trim()
        groupedObj.carBrand = joinDistinctNonBlank(list.map { (it.carBrand ?: "").toString() })
        groupedObj.carName = joinDistinctNonBlank(list.map { (it.carName ?: "").toString() })
        groupedObj.fuel = joinDistinctNonBlank(list.map { (it.fuel ?: "").toString() })
        groupedObj.wd = joinDistinctNonBlank(list.map { (it.wd ?: "").toString() })
        groupedObj.shift = joinDistinctNonBlank(list.map { (it.shift ?: "").toString() })
        groupedObj.grade = joinDistinctNonBlank(list.map { (it.grade ?: "").toString() })
        groupedObj.cc = joinDistinctNonBlank(list.map { (it.cc ?: "").toString() })
        groupedObj.seat = joinDistinctNonBlank(list.map { (it.seat ?: "").toString() })
        groupedObj.door = joinDistinctNonBlank(list.map { (it.door ?: "").toString() })
        groupedObj.vehicleType = joinDistinctNonBlank(list.map { (it.vehicleType ?: "").toString() })
        groupedObj.rank = joinDistinctNonBlank(list.map { (it.rank ?: "").toString() })
        groupedObj.color = joinDistinctNonBlank(list.map { (it.color ?: "").toString() })
        groupedObj.driveType = joinDistinctNonBlank(list.map { (it.driveType ?: "").toString() })
        groupedObj.recycleFee = joinDistinctNonBlank(list.map { (it.recycleFee ?: "").toString() })

        grouped.add(groupedObj)
    }

    return grouped
}

private fun populateCarBrandModalComboboxes() {
    populateEditableComboboxFromMasterMenu("carBrandBrand", "car_brands")
    populateEditableComboboxFromMasterMenu("carBrandFuel", "fuel")
    populateEditableComboboxFromMasterMenu("carBrandShift", "shift")
    populateEditableComboboxFromMasterMenu("carBrandGrade", "car_grade")
    populateEditableComboboxFromMasterMenu("carBrandVehicleType", "type_of_vehicle")

    val wdSelect = document.getElementById("carBrandWd") as? HTMLSelectElement
    if (wdSelect != null) {
        while (wdSelect.options.length > 1) {
            wdSelect.remove(1)
        }
        val wdOptions = listOf("2WD", "4WD")
        for (v in wdOptions) {
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = v
            opt.text = v
            wdSelect.add(opt)
        }
    }

    val driveTypeSelect = document.getElementById("carBrandDriveType") as? HTMLSelectElement
    if (driveTypeSelect != null) {
        while (driveTypeSelect.options.length > 1) {
            driveTypeSelect.remove(1)
        }
        val driveTypes = listOf("LHD", "RHD")
        for (v in driveTypes) {
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = v
            opt.text = v
            driveTypeSelect.add(opt)
        }
    }
}

private fun tryPrefillCarBrandModalFromGroupedRow() {
    val rowAny = js("window.__carBrandRowData") as Any?
    if (rowAny == null) return
    val row = rowAny.asDynamic()

    val chassis = (row.chassis ?: "").toString()
    val carBrand = (row.carBrand ?: "").toString()
    val carName = (row.carName ?: "").toString()
    val fuelVal = (row.fuel ?: "").toString()
    val wd = (row.wd ?: "").toString()
    val shiftVal = (row.shift ?: "").toString()
    val grade = (row.grade ?: "").toString()
    val cc = (row.cc ?: "").toString()
    val seat = (row.seat ?: "").toString()
    val door = (row.door ?: "").toString()
    val vehicleType = (row.vehicleType ?: "").toString()
    val rankVal = (row.rank ?: "").toString()
    val colorVal = (row.color ?: "").toString()
    val driveType = (row.driveType ?: "").toString()
    val recycleFeeVal = (row.recycleFee ?: "").toString()

    (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value = chassis
    setChipFieldValue("carBrandBrand", carBrand)
    setChipFieldValue("carBrandCarName", carName)
    setChipFieldValue("carBrandFuel", fuelVal)
    setChipFieldValue("carBrandWd", wd)
    setChipFieldValue("carBrandShift", shiftVal)
    setChipFieldValue("carBrandGrade", grade)
    setChipFieldValue("carBrandCc", cc)
    setChipFieldValue("carBrandSeat", seat)
    setChipFieldValue("carBrandDoor", door)
    setChipFieldValue("carBrandVehicleType", vehicleType)
    setChipFieldValue("carBrandRank", rankVal)
    setChipFieldValue("carBrandColor", colorVal)
    setChipFieldValue("carBrandDriveType", driveType)
    val recycleFeeInput = document.getElementById("carBrandRecycleFee") as? HTMLInputElement
    if (recycleFeeInput != null) {
        recycleFeeInput.value = recycleFeeVal
        // Format it using the global money formatter if available
        window.setTimeout({
            if (js("typeof window._moneyFormat === 'function'").unsafeCast<Boolean>()) {
                recycleFeeInput.value = js("window._moneyFormat(recycleFeeInput.value)").unsafeCast<String>()
            }
        }, 0)
    }

    js("window.__carBrandRowData = null")
}

fun getEditableComboboxValue(fieldId: String): String {
    val input = document.getElementById("${fieldId}Input") as? HTMLInputElement
    if (input != null) return input.value.trim()
    return (document.getElementById(fieldId) as? HTMLInputElement)?.value?.trim() ?: ""
}

fun setEditableComboboxValue(fieldId: String, value: String) {
    val v = value.trim()
    (document.getElementById("${fieldId}Input") as? HTMLInputElement)?.value = v
    val select = document.getElementById(fieldId) as? HTMLSelectElement
    if (select != null) {
        // If the value exists in options, align select.value for nicer UX (optional)
        try {
            val opts = select.options
            var found = false
            for (i in 0 until opts.length) {
                val opt = opts.item(i) as? HTMLOptionElement ?: continue
                if (opt.value.equals(v, ignoreCase = true)) {
                    select.value = opt.value
                    found = true
                    break
                }
            }
            if (!found && v.isNotEmpty()) {
                val opt = document.createElement("option") as HTMLOptionElement
                opt.value = v
                opt.text = v
                select.add(opt)
                select.value = v
            }
        } catch (_: dynamic) {
            // ignore
        }
    }
}

fun getChipFieldValue(fieldId: String): String {
    val el = document.getElementById("${fieldId}Hidden") as? HTMLInputElement
    return el?.value?.trim() ?: ""
}

fun setChipFieldValue(fieldId: String, value: String) {
    js("window.__tmpChipFieldId = fieldId")
    js("window.__tmpChipFieldValue = value")
    js("if (window.supplierChipSetValue) { window.supplierChipSetValue(window.__tmpChipFieldId, window.__tmpChipFieldValue); }")
}

fun populateEditableComboboxFromMasterMenu(selectId: String, fieldName: String) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    window.fetch(apiUrl("master-menu/$fieldName"))
        .then { resp: dynamic ->
            if (resp.ok) resp.json() else js("Promise.resolve([])")
        }
        .then { raw: dynamic ->
            val list = parseMasterListArray(raw).distinct().filter { it.isNotBlank() }

            // Preserve first option (▼)
            while (select.options.length > 1) {
                select.remove(1)
            }
            for (v in list) {
                val opt = document.createElement("option") as HTMLOptionElement
                opt.value = v
                opt.text = v
                select.add(opt)
            }

            // If user already typed a value, ensure it's selectable
            val current = (document.getElementById("${selectId}Input") as? HTMLInputElement)?.value?.trim() ?: ""
            if (current.isNotEmpty()) {
                setEditableComboboxValue(selectId, current)
            }
        }
        .catch { _: dynamic ->
            // ignore
        }
}

/** Consignee Map distinct names — same source as Consignee Map list (`GET booking/mappings/consignee-names`). */
fun populateEditableComboboxFromBookingConsigneeNames(selectId: String) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    window.fetch(apiUrl("booking/mappings/consignee-names"))
        .then { resp: dynamic ->
            if (resp.ok) resp.json() else js("Promise.resolve({})")
        }
        .then { raw: dynamic ->
            val list = parseConsigneeNamesApiResponse(raw).distinct().sorted()
            while (select.options.length > 1) {
                select.remove(1)
            }
            for (v in list) {
                val opt = document.createElement("option") as HTMLOptionElement
                opt.value = v
                opt.text = v
                select.add(opt)
            }
            val current = (document.getElementById("${selectId}Input") as? HTMLInputElement)?.value?.trim() ?: ""
            if (current.isNotEmpty()) {
                setEditableComboboxValue(selectId, current)
            }
        }
        .catch { _: dynamic -> }
}

private fun parseConsigneeNamesApiResponse(raw: dynamic): List<String> {
    return try {
        val data = js("raw && raw.data")
        if (js("Array.isArray(data)") as Boolean) {
            data.unsafeCast<Array<dynamic>>().mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        } else if (js("Array.isArray(raw)") as Boolean) {
            raw.unsafeCast<Array<dynamic>>().mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    } catch (_: dynamic) {
        emptyList()
    }
}

/** Rixo Price Mapping distinct rixo companies (`GET rixo-mapping/distinct-rixo-companies` — raw JSON array). */
fun populateEditableComboboxFromRixoMappingDistinctCompanies(selectId: String) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    window.fetch(apiUrl("rixo-mapping/distinct-rixo-companies"))
        .then { resp: dynamic ->
            if (resp.ok) resp.json() else js("Promise.resolve([])")
        }
        .then { raw: dynamic ->
            val list = when {
                js("Array.isArray(raw)") as Boolean ->
                    raw.unsafeCast<Array<dynamic>>().mapNotNull { it?.toString()?.trim() }.filter { it.isNotEmpty() }
                else ->
                    parseConsigneeNamesApiResponse(raw)
            }.distinct().sorted()
            while (select.options.length > 1) {
                select.remove(1)
            }
            for (v in list) {
                val opt = document.createElement("option") as HTMLOptionElement
                opt.value = v
                opt.text = v
                select.add(opt)
            }
            val current = (document.getElementById("${selectId}Input") as? HTMLInputElement)?.value?.trim() ?: ""
            if (current.isNotEmpty()) {
                setEditableComboboxValue(selectId, current)
            }
        }
        .catch { _: dynamic -> }
}

private fun populateSupplierMapModalComboboxes(isDuplicate: Boolean) {
    val prefix = if (isDuplicate) "dupSupplier" else "supplier"
    populateEditableComboboxFromMasterMenu("${prefix}StockLocation", "stock_location")
    populateEditableComboboxFromRixoMappingDistinctCompanies("${prefix}RixoCompany")
    populateEditableComboboxFromMasterMenu("${prefix}VenueId", "venue_id")
    populateEditableComboboxFromMasterMenu("${prefix}Pol", "pol")
    // DB field is 'type_of_vehicle' (label: Vehicle type)
    populateEditableComboboxFromMasterMenu("${prefix}TypeOfVehicle", "type_of_vehicle")
}

private fun populateConsigneeMapModalComboboxes() {
    populateEditableComboboxFromMasterMenu("consigneeMapCountry", "country")
    populateEditableComboboxFromMasterMenu("consigneeMapPod", "pod")
}

/** Client Map modal: Bank Info from master bank_accounts; Consignee from Consignee Map (booking consignee-names). Bank/Consignee allow typed values not only in the list. */
fun wireClientMapModalComboboxes() {
    ensureSupplierChipJs()
    populateEditableComboboxFromMasterMenu("clientMapMmCountry", "country")
    populateEditableComboboxFromMasterMenu("clientMapMmPod", "pod")
    populateEditableComboboxFromMasterMenu("clientMapMmBankInfo", "bank_accounts")
    populateEditableComboboxFromBookingConsigneeNames("clientMapMmConsignee")
}

/** DB / legacy rows may use commas or semicolons; chip UI expects ';'-joined tokens. */
fun normalizeStoredListForChips(raw: String): String {
    if (raw.isBlank()) return ""
    return raw.split(Regex("[;,\\n]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(";")
}

private fun splitConsigneeChipTokens(hiddenJoined: String): List<String> =
    hiddenJoined.split(';').map { it.trim() }.filter { it.isNotEmpty() }

fun createChipMultiSelectCombobox(id: String, placeholder: String, allowAnyTypedToken: Boolean = false): String {
    val allowAttr =
        if (allowAnyTypedToken) """ data-supplier-chip-allow-any="true"""" else ""
    // Structure intentionally mirrors editable combobox IDs:
    // - input:  ${id}Input
    // - select: ${id}
    // plus:
    // - chips:  ${id}Chips
    // - hidden: ${id}Hidden  (stores ';'-joined values for saving)
    return """
        <div style="position: relative; width: 100%;">
            <div id="${id}Wrap" style="display:flex; flex-wrap:wrap; align-items:center; gap:6px; width:100%; min-height:42px; padding:6px 44px 6px 8px; border:1px solid #d1d5db; border-radius:6px; box-sizing:border-box; background:white;"$allowAttr>
                <div id="${id}Chips" style="display:flex; flex-wrap:wrap; gap:6px;"></div>
                <input type="text" id="${id}Input" placeholder="$placeholder"
                       style="flex:1; min-width:120px; border:none; outline:none; font-size:14px; padding:6px 0; background:transparent;"
                       autocomplete="off"
                       onkeydown="return window.supplierChipHandleKey(event, '$id');"
                       onblur="window.supplierChipAddFromInput('$id');">
                <input type="hidden" id="${id}Hidden" value="">
            </div>
            <select id="$id"
                    style="position:absolute; top:0; right:0; width:40px; height:100%; border:none; border-left:1px solid #d1d5db; background:#f5f5f5; cursor:pointer; border-radius:0 6px 6px 0; appearance:none; -webkit-appearance:none; -moz-appearance:none; padding:0; text-align:center; font-size:14px; z-index:2; font-weight:bold; color:#666; opacity:0;"
                    onmousedown="event.preventDefault(); event.stopPropagation(); openComboboxDropdown('$id');"
                    onchange="window.supplierChipAddFromSelect('$id');">
                <option value="">▼</option>
            </select>
            <div id="${id}Button" onclick="openComboboxDropdown('$id')"
                 style="position:absolute; top:0; right:0; width:40px; height:100%; border:none; border-left:1px solid #d1d5db; background:#f5f5f5; cursor:pointer; border-radius:0 6px 6px 0; z-index:3; pointer-events:auto; display:flex; align-items:center; justify-content:center; font-size:14px; font-weight:bold; color:#666; user-select:none;">
                ▼
            </div>
        </div>
    """.trimIndent()
}

fun createChipInput(id: String, placeholder: String, maxLength: Int? = null): String {
    // For fields with no dropdown (e.g. Rixo Price)
    val maxAttr = maxLength?.let { """ maxlength="$it"""" } ?: ""
    return """
        <div style="position: relative; width: 100%;">
            <div id="${id}Wrap" class="chip-input-inner-wrap" style="display:flex; flex-wrap:wrap; align-items:center; gap:6px; width:100%; min-height:42px; padding:6px 8px; border:1px solid #d1d5db; border-radius:6px; box-sizing:border-box; background:white;">
                <div id="${id}Chips" style="display:flex; flex-wrap:wrap; gap:6px;"></div>
                <input type="text" id="${id}Input" placeholder="$placeholder"$maxAttr
                       style="flex:1; min-width:0; border:none; outline:none; font-size:14px; padding:6px 0; background:transparent;"
                       autocomplete="off"
                       onkeydown="return window.supplierChipHandleKey(event, '$id');"
                       onblur="window.supplierChipAddFromInput('$id');">
                <input type="hidden" id="${id}Hidden" value="">
            </div>
        </div>
    """.trimIndent()
}

fun ensureSupplierChipJs() {
    js("""
        if (!window.__supplierChipJsReady) {
          window.__supplierChipJsReady = true;

          function _splitTokens(v) {
            if (!v) return [];
            var parts = v.split(';');
            var out = [];
            for (var i = 0; i < parts.length; i++) {
              var s = (parts[i] || '').trim();
              if (s) out.push(s);
            }
            return out;
          }
          function _joinTokens(tokens) {
            // distinct, keep order
            var seen = {};
            var out = [];
            for (var i = 0; i < tokens.length; i++) {
              var t = tokens[i];
              var key = (t || '').toUpperCase();
              if (!key) continue;
              if (!seen[key]) { seen[key] = true; out.push(t); }
            }
            return out.join(';');
          }
          function _getHidden(id) { return document.getElementById(id + 'Hidden'); }
          function _getChips(id) { return document.getElementById(id + 'Chips'); }
          function _getInput(id) { return document.getElementById(id + 'Input'); }
          function _normalize(v) { return (v || '').toString().trim().toUpperCase(); }
          function _isNumericChipField(id) {
            return id === 'carBrandCc' || id === 'carBrandSeat' || id === 'carBrandDoor'
              || id === 'scmCarsPerContainer' || id === 'dupScmCarsPerContainer';
          }
          function _isValidNumericToken(v) {
            return /^[0-9]+$/.test((v || '').toString().trim());
          }
          function _isAllowedToken(id, value) {
            var wrap = document.getElementById(id + 'Wrap');
            if (wrap && wrap.getAttribute && wrap.getAttribute('data-supplier-chip-allow-any') === 'true') {
              var fs = (value || '').toString().trim();
              return fs.length > 0;
            }
            var select = document.getElementById(id);
            // Free-text chip inputs (no select element) should accept typed values.
            if (!select) return true;
            var needle = _normalize(value);
            if (!needle) return false;
            for (var i = 0; i < select.options.length; i++) {
              var opt = select.options[i];
              var ov = _normalize(opt.value);
              var ot = _normalize(opt.text);
              if (!ov) continue;
              if (ov === '__SEE_MORE__' || ov === '__SEE_LESS__') continue;
              if (needle === ov || needle === ot) return true;
            }
            return false;
          }
          function _render(id) {
            var hidden = _getHidden(id);
            var chips = _getChips(id);
            if (!hidden || !chips) return;
            var tokens = _splitTokens(hidden.value);
            chips.innerHTML = '';
            for (var i = 0; i < tokens.length; i++) {
              (function(t) {
              var chip = document.createElement('span');
              chip.style.cssText = 'display:inline-flex; align-items:center; gap:6px; background:#2563eb; color:white; border-radius:9999px; padding:6px 10px; font-size:12px; font-weight:600; line-height:1;';
              var x = document.createElement('button');
              x.type = 'button';
              x.textContent = '×';
              x.setAttribute('aria-label', 'Remove');
              x.style.cssText = 'border:none; background:rgba(255,255,255,0.20); color:white; width:18px; height:18px; border-radius:9999px; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; padding:0; line-height:1;';
              x.addEventListener('click', function(ev) {
                ev.preventDefault();
                ev.stopPropagation();
                window.supplierChipRemove(id, t);
              });
              var label = document.createElement('span');
              var labelText = t;
              if ((id === 'scmShippingPricePerCar' || id === 'dupScmShippingPricePerCar')
                  && typeof window._moneySanitize === 'function' && typeof window._moneyFormat === 'function') {
                labelText = window._moneyFormat(window._moneySanitize(t));
              }
              label.textContent = labelText;
              chip.appendChild(x);
              chip.appendChild(label);
              chips.appendChild(chip);
              })(tokens[i]);
            }
            if (id.startsWith('scm') || id.startsWith('dupScm')) {
              var prefix = id.startsWith('dupScm') ? 'dupScm' : 'scm';
              _updateScmCombinedPreview(prefix);
            }
          }

          function _updateScmCombinedPreview(prefix) {
             var carsHidden = _getHidden(prefix + 'CarsPerContainer');
             var priceHidden = _getHidden(prefix + 'ShippingPricePerCar');
             var previewChips = document.getElementById(prefix + 'CombinedPreviewChips');
             if (!carsHidden || !priceHidden || !previewChips) return;
             
             var cars = _splitTokens(carsHidden.value);
             var prices = _splitTokens(priceHidden.value);
             
             previewChips.innerHTML = '';
             var maxLen = Math.max(cars.length, prices.length);
             if (maxLen === 0) {
                 previewChips.innerHTML = '<span style="color:#9ca3af;font-size:13px;font-style:italic;">No tiers added yet</span>';
                 return;
             }
             for (var i = 0; i < maxLen; i++) {
                var c = i < cars.length ? cars[i] : '?';
                var rawP = i < prices.length ? prices[i] : '?';
                var p = rawP;
                if (typeof window._moneySanitize === 'function' && typeof window._moneyFormat === 'function' && p !== '?') {
                   p = window._moneyFormat(window._moneySanitize(p));
                } else if (p !== '?') {
                   p = '$' + p;
                }
                
                var chip = document.createElement('span');
                chip.style.cssText = 'display:inline-flex; align-items:center; gap:6px; background:#4f46e5; color:white; border-radius:9999px; padding:6px 10px; font-size:12px; font-weight:600; line-height:1;';
                
                var x = document.createElement('button');
                x.type = 'button';
                x.textContent = '×';
                x.setAttribute('aria-label', 'Remove');
                x.style.cssText = 'border:none; background:rgba(255,255,255,0.20); color:white; width:18px; height:18px; border-radius:9999px; cursor:pointer; display:inline-flex; align-items:center; justify-content:center; padding:0; line-height:1;';
                
                (function(rawCar, rawPrice) {
                    x.addEventListener('click', function(ev) {
                        ev.preventDefault();
                        ev.stopPropagation();
                        if (rawCar !== '?') {
                            window.supplierChipRemove(prefix + 'CarsPerContainer', rawCar);
                        }
                        if (rawPrice !== '?') {
                            window.supplierChipRemove(prefix + 'ShippingPricePerCar', rawPrice);
                        }
                    });
                })(c, rawP);

                var label = document.createElement('span');
                label.textContent = c + ' / ' + p;

                chip.appendChild(x);
                chip.appendChild(label);
                previewChips.appendChild(chip);
             }
          }

          // Prefill from server / grouped row: show all tokens as chips even before async
          // master_menu fetch fills the select. Missing options are added so chips validate on blur.
          window.supplierChipSetValue = function(id, value) {
            var hidden = _getHidden(id);
            if (!hidden) return;
            var tokens = _splitTokens(value || '');
            var select = document.getElementById(id);
            if (select) {
              for (var i = 0; i < tokens.length; i++) {
                var t = (tokens[i] || '').toString().trim();
                if (!t) continue;
                if (!_isAllowedToken(id, t)) {
                  var opt = document.createElement('option');
                  opt.value = t;
                  opt.textContent = t;
                  select.appendChild(opt);
                }
              }
            }
            hidden.value = _joinTokens(tokens);
            _render(id);
          };
          window.supplierChipGetValue = function(id) {
            var hidden = _getHidden(id);
            return hidden ? (hidden.value || '').trim() : '';
          };
          window.supplierChipAdd = function(id, value) {
            var v = (value || '').toString().trim();
            if (!v) return;
            if ((id === 'scmShippingPricePerCar' || id === 'dupScmShippingPricePerCar')
                && typeof window._moneySanitize === 'function') {
              v = window._moneySanitize(v);
            }
            if (_isNumericChipField(id) && !_isValidNumericToken(v)) {
              var invalidNumberInput = _getInput(id);
              if (invalidNumberInput) invalidNumberInput.value = '';
              return;
            }
            if (!_isAllowedToken(id, v)) {
              var invalidInput = _getInput(id);
              if (invalidInput) invalidInput.value = '';
              return;
            }
            var hidden = _getHidden(id);
            if (!hidden) return;
            var tokens = _splitTokens(hidden.value);
            tokens.push(v);
            var joined = _joinTokens(tokens);
            if (id === 'carBrandCarName' && joined.length > ${CAR_BRAND_CAR_NAME_MAX_LEN}) {
              var invalidInput2 = _getInput(id);
              if (invalidInput2) invalidInput2.value = '';
              return;
            }
            hidden.value = joined;
            var input = _getInput(id);
            if (input) input.value = '';
            var select = document.getElementById(id);
            if (select) select.value = '';
            _render(id);
          };
          window.supplierChipRemove = function(id, value) {
            var v = (value || '').toString().trim();
            var hidden = _getHidden(id);
            if (!hidden) return;
            var existing = _splitTokens(hidden.value);
            var tokens = [];
            for (var i = 0; i < existing.length; i++) {
              if (existing[i].toUpperCase() !== v.toUpperCase()) tokens.push(existing[i]);
            }
            hidden.value = _joinTokens(tokens);
            _render(id);
          };
          window.supplierChipAddFromSelect = function(id) {
            var select = document.getElementById(id);
            if (!select) return;
            var v = (select.value || '').toString().trim();
            if (!v) return;
            window.supplierChipAdd(id, v);
          };
          window.supplierChipAddFromInput = function(id) {
            var input = _getInput(id);
            if (!input) return;
            var v = (input.value || '').toString().trim();
            if (!v) return;
            // Support typing multiple at once separated by ';'
            var tokens = _splitTokens(v);
            if (tokens.length > 1) {
              for (var i = 0; i < tokens.length; i++) window.supplierChipAdd(id, tokens[i]);
            } else {
              window.supplierChipAdd(id, v);
            }
            // If anything typed was invalid, keep the field clean.
            if (input) input.value = '';
          };
          window.supplierChipHandleKey = function(e, id) {
            if (!e) return true;
            var key = e.key;
            if (key === 'Enter') {
              e.preventDefault();
              window.supplierChipAddFromInput(id);
              return false;
            }
            return true;
          };

          // Hook into the existing combobox behavior: when a dropdown selection uses syncComboboxInput(selectId),
          // automatically convert selected value into a chip for chip-enabled fields.
          if (!window.__supplierChipWrappedSync && typeof window.syncComboboxInput === 'function') {
            window.__supplierChipWrappedSync = true;
            var _origSync = window.syncComboboxInput;
            window.syncComboboxInput = function(selectId) {
              _origSync(selectId);
              try {
                var hidden = document.getElementById(selectId + 'Hidden');
                var select = document.getElementById(selectId);
                if (hidden && select && select.value) {
                  window.supplierChipAdd(selectId, select.value);
                }
              } catch (_) {}
            };
          }
        }
    """)
}

@Suppress("UNUSED_PARAMETER")
private fun enforceSupplierModalDropdownOnly(prefix: String) {
    // Supplier name is free text; chip fields use master-menu dropdowns.
}

// Global pagination variables for Consignees
var consigneesCurrentPage = 1
var consigneesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allConsignees: List<dynamic> = emptyList()

/** When true, list data comes from [booking/mappings/page-search]. */
var consigneeMapSearchServerMode: Boolean = false
var consigneeMapSearchTotal: Long = 0
var consigneeMapSearchTotalPages: Int = 0
var consigneeMapSearchPageZeroBased: Int = 0
/** API field: all | consigneeName | country */
var consigneeMapSearchFieldChoice: String = "all"
private var consigneeMapSearchDebounceTimer: dynamic = null

private const val CONSIGNEE_MAP_COMPACT_MAX_WIDTH_PX = 860
private var consigneeMapResizeDebounceHandle: Int? = null

private data class ConsigneeMapRenderSlice(
    val paginatedMappings: List<dynamic>,
    val orderedForDisplay: List<dynamic>,
    val filterLabel: String,
    val totalPages: Int,
    val isServerSearch: Boolean,
    val footerStart: Int,
    val footerEnd: Int,
)

private var consigneeMapLastRenderSlice: ConsigneeMapRenderSlice? = null

private fun consigneeMapIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= CONSIGNEE_MAP_COMPACT_MAX_WIDTH_PX
}

private fun renderConsigneeMapFromCache() {
    val slice = consigneeMapLastRenderSlice ?: return
    val tableDiv = document.getElementById("consigneeTable") as? HTMLElement ?: return
    if (consigneeMapIsCompactLayout()) {
        displayConsigneesAsCards(
            if (slice.isServerSearch) slice.paginatedMappings else slice.orderedForDisplay,
            slice.filterLabel,
            slice.isServerSearch,
        )
    } else {
        buildConsigneeTableUi(
            tableDiv,
            slice.paginatedMappings,
            slice.orderedForDisplay,
            slice.filterLabel,
            slice.totalPages,
            slice.isServerSearch,
            slice.footerStart,
            slice.footerEnd,
        )
    }
}

private fun renderConsigneeMapList(
    paginatedMappings: List<dynamic>,
    orderedForDisplay: List<dynamic>,
    filterLabel: String,
    totalPages: Int,
    isServerSearch: Boolean,
    footerStart: Int,
    footerEnd: Int,
) {
    consigneeMapLastRenderSlice = ConsigneeMapRenderSlice(
        paginatedMappings = paginatedMappings,
        orderedForDisplay = orderedForDisplay,
        filterLabel = filterLabel,
        totalPages = totalPages,
        isServerSearch = isServerSearch,
        footerStart = footerStart,
        footerEnd = footerEnd,
    )
    renderConsigneeMapFromCache()
}

private fun setupConsigneeMapResizeListener() {
    val root = document.getElementById("consigneeMapRoot") ?: return
    if (root.hasAttribute("data-consignee-map-resize")) return
    root.setAttribute("data-consignee-map-resize", "true")
    window.addEventListener("resize", { _: Event ->
        consigneeMapResizeDebounceHandle?.let { window.clearTimeout(it) }
        consigneeMapResizeDebounceHandle = window.setTimeout({
            if (document.getElementById("consigneeMapRoot") == null) return@setTimeout
            if (consigneeMapLastRenderSlice != null) renderConsigneeMapFromCache()
        }, 120)
    })
}

// Global variable to track last device type for Consignee page
var lastConsigneeDeviceType: String? = getDeviceType()

// Global pagination variables for Country page
var countriesCurrentPage = 1
var countriesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCountries: List<String> = emptyList()

// Global pagination variables for Rixo Company page
var rixoCompaniesCurrentPage = 1
var rixoCompaniesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allRixoCompanies: List<String> = emptyList()

// Global pagination variables for Stock Location page
var stockLocationsCurrentPage = 1
var stockLocationsItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allStockLocations: List<String> = emptyList()

// Global pagination variables for Repair Company page
var repairCompaniesCurrentPage = 1
var repairCompaniesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allRepairCompanies: List<String> = emptyList()

// Global pagination variables for Venue ID page
var venueIdsCurrentPage = 1
var venueIdsItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allVenueIds: List<String> = emptyList()

// Global pagination variables for POL, POD, Fuel, Car Grade, Car Shift, Type of Vehicles
var polCurrentPage = 1
var polItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allPol: List<String> = emptyList()
var podCurrentPage = 1
var podItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allPod: List<String> = emptyList()
var fuelCurrentPage = 1
var fuelItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allFuel: List<String> = emptyList()
var carGradeCurrentPage = 1
var carGradeItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarGrades: List<String> = emptyList()
var carShiftCurrentPage = 1
var carShiftItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarShifts: List<String> = emptyList()
var typeOfVehiclesCurrentPage = 1
var typeOfVehiclesItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allTypeOfVehicles: List<String> = emptyList()
var dynamicMasterSetCurrentPage: MutableMap<String, Int> = mutableMapOf()
var dynamicMasterSetAllValues: MutableMap<String, List<String>> = mutableMapOf()
var dynamicMasterSetSortOrder: MutableMap<String, String> = mutableMapOf()

private const val SIMPLE_MASTER_COMPACT_MAX_WIDTH_PX = 860
private var simpleMasterResizeDebounceHandle: Int? = null
private val simpleMasterLastRenderMeta: MutableMap<String, SimpleMasterRenderMeta> = mutableMapOf()

private val simpleMasterTitleOverrides = mapOf(
    "bank_accounts" to "Bank Accounts",
    "car_brands" to "Car Brands",
    "shift" to "Car Shift",
    "country" to "Country",
    "fuel" to "Fuel",
    "pod" to "POD",
    "pol" to "POL",
    "repair_company" to "Repair Company",
    "stock_location" to "Stock Location",
    "type_of_vehicle" to "Type of Vehicles",
    "venue_id" to "Venue ID List",
)

private data class SimpleMasterRenderMeta(
    val tableId: String,
    val title: String,
    val apiPath: String,
    val editBtnClass: String,
    val dataAttr: String,
    val prevBtnId: String,
    val nextBtnId: String,
    val setPage: (Int) -> Unit,
    val loadFn: () -> Unit,
    val editModalFn: (String) -> Unit,
    val sorted: List<String>,
    val page: Int,
    val itemsPerPage: Int,
    val searchFilter: String,
)

// Global pagination variables for Client master list (from master_menu)
var clientMasterCurrentPage = 1
var clientMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allClientMaster: List<String> = emptyList()

// Global pagination variables for Consignee master list (from master_menu)
var consigneeMasterCurrentPage = 1
var consigneeMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allConsigneeMaster: List<String> = emptyList()

// Global pagination variables for Supplier master list (from master_menu)
var supplierMasterCurrentPage = 1
var supplierMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allSupplierMaster: List<String> = emptyList()

// Global pagination variables for Car Brands master list (from master_menu)
var carBrandsMasterCurrentPage = 1
var carBrandsMasterItemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE
var allCarBrandsMaster: List<String> = emptyList()

// Global variable to track last device type for Car Brands page
var lastCarBrandDeviceType: String? = getDeviceType()

/**
 * Get default columns for Supplier page based on device type
 */
fun getDefaultSupplierColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    return when (device) {
        "mobile" -> listOf("supplierName", "stockLocation", "rixoCompany")
        "tablet" -> listOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
        "desktop" -> listOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
        else -> listOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
    }
}

/**
 * Get selected columns for Supplier page
 */
fun getSelectedSupplierColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxConsigneeSupplierMapColumnsForDevice(deviceType)
    val defaultColumns = getDefaultSupplierColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedSupplierColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved supplier columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Filter out "id" column (removed from UI) and columns we explicitly no longer display
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it != "id" && it != "rixoPrice" && it != "typeOfVehicle" }
    return if (filteredColumns.size > maxColumns) {
        defaultColumns
    } else {
        filteredColumns.take(maxColumns)
    }
}

/**
 * Get default columns for Consignee page based on device type
 */
fun getDefaultConsigneeColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    // Display order: Consignee Name → Consignee Address → Country → POD
    return when (device) {
        "mobile" -> listOf("consigneeName", "consigneeAddress", "country", "pod")
        "tablet" -> listOf("consigneeName", "consigneeAddress", "country", "pod")
        "desktop" -> listOf("consigneeName", "consigneeAddress", "country", "pod")
        else -> listOf("consigneeName", "consigneeAddress", "country", "pod")
    }
}

/**
 * Get default columns for Car Brands page based on device type
 */
fun getDefaultCarBrandColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    val six = listOf("chassis", "carBrand", "carName", "fuel", "wd", "shift")
    return when (device) {
        "mobile" -> listOf("chassis", "carBrand", "carName")
        "tablet" -> six
        "desktop" -> six
        else -> six
    }
}

/**
 * Get selected columns for Car Brands page
 */
fun getSelectedCarBrandColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxCarBrandMapColumnsForDevice(deviceType)
    val defaultColumns = getDefaultCarBrandColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedCarBrandColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved car brand columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Filter out "id" column (removed from UI) and auto-adjust if saved columns exceed device limit
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it != "id" }
    return if (filteredColumns.size > maxColumns) {
        defaultColumns
    } else {
        filteredColumns.take(maxColumns)
    }
}

fun getMaxClientMapColumnsForDevice(deviceType: String? = null): Int = 7

fun getDefaultClientMapColumnsForDevice(deviceType: String? = null): List<String> {
    val device = deviceType ?: getDeviceType()
    val all = listOf("clientName", "country", "pod", "address", "bankInfo", "consignee", "debitLimit")
    return when (device) {
        "mobile" -> listOf("clientName", "country", "pod")
        "tablet" -> all.take(6)
        else -> all
    }
}

fun getSelectedClientMapColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxClientMapColumnsForDevice(deviceType)
    val defaultColumns = getDefaultClientMapColumnsForDevice(deviceType)
    val saved = safeLocalStorageGet("selectedClientMapColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved client map columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    val filtered = savedColumns.filter { it.isNotBlank() && it != "id" }
    return if (filtered.size > maxColumns) {
        defaultColumns
    } else {
        filtered.take(maxColumns)
    }
}

/**
 * Get selected columns for Consignee page
 */
fun getSelectedConsigneeColumns(): List<String> {
    val deviceType = getDeviceType()
    val maxColumns = getMaxConsigneeSupplierMapColumnsForDevice(deviceType)
    val defaultColumns = getDefaultConsigneeColumnsForDevice(deviceType)
    
    // Try to get saved columns from localStorage
    val saved = safeLocalStorageGet("selectedConsigneeColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            Logger.warn("Failed to parse saved consignee columns: ${e.toString()}")
            null
        }
    } else {
        null
    }
    
    // If no saved columns, return device defaults
    if (savedColumns == null || savedColumns.isEmpty()) {
        return defaultColumns
    }
    
    // Filter out removed columns (Client Name, POL, Stock Location) and "id"
    val removedConsigneeCols = setOf("id", "clientName", "pols", "stockLocation")
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it !in removedConsigneeCols }
    if (filteredColumns.isEmpty()) {
        return defaultColumns
    }
    val preferredConsigneeColumnOrder = listOf("consigneeName", "consigneeAddress", "country", "pod")
    val sortedColumns = filteredColumns.sortedBy { col ->
        val idx = preferredConsigneeColumnOrder.indexOf(col)
        if (idx >= 0) idx else 999
    }
    return if (sortedColumns.size > maxColumns) {
        defaultColumns
    } else {
        sortedColumns.take(maxColumns)
    }
}

/**
 * Get default columns for Country page (same for all devices)
 */
fun getDefaultCountryColumnsForDevice(deviceType: String? = null): List<String> {
    return listOf("id", "country")
}

/**
 * Get selected columns for Country page
 */
fun getSelectedCountryColumns(): List<String> {
    val defaultColumns = getDefaultCountryColumnsForDevice(getDeviceType())
    val saved = safeLocalStorageGet("selectedCountryColumns")
    val savedColumns = if (saved != null) {
        try {
            JSON.parse<Array<String>>(saved).toList()
        } catch (e: dynamic) {
            null
        }
    } else null
    if (savedColumns == null || savedColumns.isEmpty()) return defaultColumns
    return savedColumns.filter { it.isNotBlank() }.ifEmpty { defaultColumns }
}

// Master List Functions

/** Client page: shows a client master list (from master_menu). */
fun showClientPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="clientMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Client</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addClientMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Client</span>
                    </button>
                </div>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Client:</label>
                    <input type="text" id="clientMasterFilter" placeholder="Type client name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="clientMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading clients...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadClientMasterList()

    document.getElementById("clientMasterFilter")?.addEventListener("input", { _: Event ->
        loadClientMasterList()
    })

    document.getElementById("addClientMasterBtn")?.addEventListener("click", { _: Event ->
        showAddClientMasterModal()
    })
}

fun loadClientMasterList() {
    val tableDiv = document.getElementById("clientMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("clientMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading clients...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/clients"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load clients')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allClientMaster = filtered
            if (searchFilter.isNotEmpty()) clientMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No clients found for: $searchFilter" else "No clients found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "client")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / clientMasterItemsPerPage).toInt()
            val startIndex = (clientMasterCurrentPage - 1) * clientMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + clientMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "client" to "Client")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="client-master-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
            """
            for (col in selectedColumns) {
                val label = columnLabels[col] ?: col
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, clientName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="client-master-edit-btn"
                                        data-client="${clientName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "client" -> clientName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "client" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                html += """</tr>"""
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} client${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="clientMasterPrevPage" class="consignee-pagination-btn" ${if (clientMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $clientMasterCurrentPage of $totalPages</span>
                            <button id="clientMasterNextPage" class="consignee-pagination-btn" ${if (clientMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} client${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".client-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-client") ?: return@addEventListener
                    showEditClientMasterModal(name)
                })
            }
            
            document.getElementById("clientMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (clientMasterCurrentPage > 1) {
                    clientMasterCurrentPage--
                    loadClientMasterList()
                }
            })
            document.getElementById("clientMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allClientMaster.size.toDouble() / clientMasterItemsPerPage).toInt()
                if (clientMasterCurrentPage < totalP) {
                    clientMasterCurrentPage++
                    loadClientMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading clients: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading clients</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddClientMasterModal() {
    document.getElementById("clientMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "clientMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Client</h3>
            <div style="margin-bottom: 16px;">
                <label for="clientMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Client</label>
                <input type="text" id="clientMasterModalInput" placeholder="Enter client name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="clientMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="clientMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("clientMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("clientMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("clientMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Client name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/clients"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add client')")
            }
            .then { _: dynamic ->
                showMessage("Client added successfully", "success")
                modal.remove()
                clientMasterCurrentPage = 1
                loadClientMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding client: ${error.toString()}")
                showMessage("Error adding client: ${error.message}", "error")
            }
    })
}

fun showEditClientMasterModal(originalName: String) {
    document.getElementById("clientMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "clientMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Client</h3>
            <div style="margin-bottom: 16px;">
                <label for="clientMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Client</label>
                <input type="text" id="clientMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="clientMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="clientMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="clientMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("clientMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("clientMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("clientMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Client name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/clients"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update client')")
            }
            .then { _: dynamic ->
                showMessage("Client updated successfully", "success")
                modal.remove()
                loadClientMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating client: ${error.toString()}")
                showMessage("Error updating client: ${error.message}", "error")
            }
    })

    document.getElementById("clientMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete client '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/clients?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete client')")
            }
            .then { _: dynamic ->
                showMessage("Client deleted successfully", "success")
                modal.remove()
                clientMasterCurrentPage = 1
                loadClientMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting client: ${error.toString()}")
                showMessage("Error deleting client: ${error.message}", "error")
            }
    })
}

fun showMasterClientsPage() {
    showClientAccountsPage()
}

fun loadMasterClients() {
    // Use the loadClients function from ClientManagement.kt
    loadClients()
}

fun showAddClientModal() {
    // Use the showAddClientForm function from ClientManagement.kt
    showAddClientForm()
}

/** Consignee page: shows a consignee master list (from master_menu). */
fun showConsigneePage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="consigneeMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Consignee</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addConsigneeMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Consignee</span>
                    </button>
                </div>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Consignee:</label>
                    <input type="text" id="consigneeMasterFilter" placeholder="Type consignee name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="consigneeMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading consignees...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadConsigneeMasterList()

    document.getElementById("consigneeMasterFilter")?.addEventListener("input", { _: Event ->
        loadConsigneeMasterList()
    })

    document.getElementById("addConsigneeMasterBtn")?.addEventListener("click", { _: Event ->
        showAddConsigneeMasterModal()
    })
}

fun loadConsigneeMasterList() {
    val tableDiv = document.getElementById("consigneeMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("consigneeMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading consignees...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/consignee"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignees')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allConsigneeMaster = filtered
            if (searchFilter.isNotEmpty()) consigneeMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No consignees found for: $searchFilter" else "No consignees found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "consignee")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / consigneeMasterItemsPerPage).toInt()
            val startIndex = (consigneeMasterCurrentPage - 1) * consigneeMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + consigneeMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "consignee" to "Consignee")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="consignee-master-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
            """
            for (col in selectedColumns) {
                val label = columnLabels[col] ?: col
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, consigneeName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="consignee-master-edit-btn"
                                        data-consignee="${consigneeName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "consignee" -> consigneeName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "consignee" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                html += """</tr>"""
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} consignee${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="consigneeMasterPrevPage" class="consignee-pagination-btn" ${if (consigneeMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $consigneeMasterCurrentPage of $totalPages</span>
                            <button id="consigneeMasterNextPage" class="consignee-pagination-btn" ${if (consigneeMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} consignee${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".consignee-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-consignee") ?: return@addEventListener
                    showEditConsigneeMasterModal(name)
                })
            }
            
            document.getElementById("consigneeMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (consigneeMasterCurrentPage > 1) {
                    consigneeMasterCurrentPage--
                    loadConsigneeMasterList()
                }
            })
            document.getElementById("consigneeMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allConsigneeMaster.size.toDouble() / consigneeMasterItemsPerPage).toInt()
                if (consigneeMasterCurrentPage < totalP) {
                    consigneeMasterCurrentPage++
                    loadConsigneeMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignees: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading consignees</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddConsigneeMasterModal() {
    document.getElementById("consigneeMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "consigneeMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Consignee</h3>
            <div style="margin-bottom: 16px;">
                <label for="consigneeMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Consignee</label>
                <input type="text" id="consigneeMasterModalInput" placeholder="Enter consignee name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="consigneeMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="consigneeMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("consigneeMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("consigneeMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("consigneeMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Consignee name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/consignee"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add consignee')")
            }
            .then { _: dynamic ->
                showMessage("Consignee added successfully", "success")
                modal.remove()
                consigneeMasterCurrentPage = 1
                loadConsigneeMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding consignee: ${error.toString()}")
                showMessage("Error adding consignee: ${error.message}", "error")
            }
    })
}

fun showEditConsigneeMasterModal(originalName: String) {
    document.getElementById("consigneeMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "consigneeMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Consignee</h3>
            <div style="margin-bottom: 16px;">
                <label for="consigneeMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Consignee</label>
                <input type="text" id="consigneeMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="consigneeMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="consigneeMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="consigneeMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("consigneeMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("consigneeMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("consigneeMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Consignee name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/consignee"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update consignee')")
            }
            .then { _: dynamic ->
                showMessage("Consignee updated successfully", "success")
                modal.remove()
                loadConsigneeMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating consignee: ${error.toString()}")
                showMessage("Error updating consignee: ${error.message}", "error")
            }
    })

    document.getElementById("consigneeMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete consignee '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/consignee?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete consignee')")
            }
            .then { _: dynamic ->
                showMessage("Consignee deleted successfully", "success")
                modal.remove()
                consigneeMasterCurrentPage = 1
                loadConsigneeMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting consignee: ${error.toString()}")
                showMessage("Error deleting consignee: ${error.message}", "error")
            }
    })
}

fun showMasterConsigneePage() {
    showConsigneeMapPage()
}

private fun getConsigneeMapSearchQuery(): String =
    (document.getElementById("consigneeMapSearchInput") as? HTMLInputElement)?.value?.trim() ?: ""

private fun consigneeMapSearchFieldDisplayLabel(): String = when (consigneeMapSearchFieldChoice) {
    "consigneeName" -> "Consignee name"
    "country" -> "Country"
    else -> "All fields"
}

private fun refreshConsigneeMapSearchScopeUi() {
    val scopeLabel = consigneeMapSearchFieldDisplayLabel()
    val btn = document.getElementById("consigneeMapSearchFilterBtn") as? HTMLElement
    if (btn != null) {
        btn.setAttribute("title", "Filter — search in: $scopeLabel")
        btn.setAttribute("aria-label", "Open filter for which field to search. Current: $scopeLabel.")
    }
    val sr = document.getElementById("consigneeMapSearchFieldLabel") as? HTMLElement
    if (sr != null) sr.textContent = scopeLabel
}

private fun closeConsigneeMapSearchFilterMenu() {
    val menu = document.getElementById("consigneeMapSearchFilterMenu") as? HTMLElement
    menu?.style?.setProperty("display", "none", "important")
    window.asDynamic().__consigneeMapSearchFilterMenuOpen = false
    document.getElementById("consigneeMapSearchFilterBtn")?.setAttribute("aria-expanded", "false")
}

private fun updateConsigneeMapSearchFilterMenuActive(selected: String) {
    val pairs = listOf(
        "all" to "consigneeMapSearchOptAll",
        "consigneeName" to "consigneeMapSearchOptConsigneeName",
        "country" to "consigneeMapSearchOptCountry"
    )
    for ((value, id) in pairs) {
        val el = document.getElementById(id) as? HTMLElement ?: continue
        if (value == selected) el.classList.add("consignee-map-search-filter-opt--active")
        else el.classList.remove("consignee-map-search-filter-opt--active")
    }
}

private fun scheduleConsigneeMapSearchDebounced() {
    if (consigneeMapSearchDebounceTimer != null) {
        window.clearTimeout(consigneeMapSearchDebounceTimer.unsafeCast<Int>())
        consigneeMapSearchDebounceTimer = null
    }
    consigneeMapSearchDebounceTimer = window.setTimeout({
        consigneeMapSearchDebounceTimer = null
        runConsigneeMapSearchFromInput()
    }, 420)
}

private fun runConsigneeMapSearchFromInput() {
    val raw = getConsigneeMapSearchQuery()
    if (raw.isEmpty()) {
        consigneeMapSearchServerMode = false
        consigneeMapSearchTotal = 0
        consigneeMapSearchTotalPages = 0
        consigneeMapSearchPageZeroBased = 0
        consigneesCurrentPage = 1
        loadMasterConsignee()
        return
    }
    consigneeMapSearchPageZeroBased = 0
    consigneesCurrentPage = 1
    loadMasterConsignee()
}

fun setupConsigneeMapSearchBarListeners() {
    val input = document.getElementById("consigneeMapSearchInput") as? HTMLInputElement ?: return
    val filterBtn = document.getElementById("consigneeMapSearchFilterBtn") as? HTMLElement
    val menu = document.getElementById("consigneeMapSearchFilterMenu") as? HTMLElement
    val clearBtn = document.getElementById("consigneeMapSearchClearBtn") as? HTMLElement

    if (!input.hasAttribute("data-consignee-map-search-bound")) {
        input.setAttribute("data-consignee-map-search-bound", "true")
        input.addEventListener("input", { _: Event -> scheduleConsigneeMapSearchDebounced() })
        input.addEventListener("keydown", { ev: Event ->
            val kev = ev.asDynamic()
            if (kev.key == "Enter") {
                ev.preventDefault()
                if (consigneeMapSearchDebounceTimer != null) {
                    window.clearTimeout(consigneeMapSearchDebounceTimer.unsafeCast<Int>())
                    consigneeMapSearchDebounceTimer = null
                }
                runConsigneeMapSearchFromInput()
            }
        })
    }

    if (filterBtn != null && !filterBtn.hasAttribute("data-consignee-map-search-bound")) {
        filterBtn.setAttribute("data-consignee-map-search-bound", "true")
        filterBtn.addEventListener("click", { e: Event ->
            e.stopPropagation()
            if (menu == null) return@addEventListener
            val open = window.asDynamic().__consigneeMapSearchFilterMenuOpen == true
            window.asDynamic().__consigneeMapSearchFilterMenuOpen = !open
            val nowOpen = !open
            menu.style.setProperty("display", if (open) "none" else "block", "important")
            filterBtn.setAttribute("aria-expanded", if (nowOpen) "true" else "false")
        })
    }

    if (clearBtn != null && !clearBtn.hasAttribute("data-consignee-map-search-bound")) {
        clearBtn.setAttribute("data-consignee-map-search-bound", "true")
        clearBtn.addEventListener("click", { _: Event ->
            input.value = ""
            closeConsigneeMapSearchFilterMenu()
            runConsigneeMapSearchFromInput()
        })
    }

    val optIds = listOf(
        "consigneeMapSearchOptAll" to "all",
        "consigneeMapSearchOptConsigneeName" to "consigneeName",
        "consigneeMapSearchOptCountry" to "country"
    )
    for ((id, value) in optIds) {
        val el = document.getElementById(id) as? HTMLElement
        if (el != null && !el.hasAttribute("data-consignee-map-search-bound")) {
            el.setAttribute("data-consignee-map-search-bound", "true")
            el.addEventListener("click", { _: Event ->
                consigneeMapSearchFieldChoice = value
                refreshConsigneeMapSearchScopeUi()
                updateConsigneeMapSearchFilterMenuActive(value)
                closeConsigneeMapSearchFilterMenu()
                val q = input.value.trim()
                if (q.isNotEmpty()) {
                    consigneeMapSearchPageZeroBased = 0
                    consigneesCurrentPage = 1
                    loadMasterConsignee()
                }
            })
        }
    }

    if (window.asDynamic().__consigneeMapSearchFilterOutsideAttached != true) {
        window.asDynamic().__consigneeMapSearchFilterOutsideAttached = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val m = document.getElementById("consigneeMapSearchFilterMenu") as? HTMLElement
            val b = document.getElementById("consigneeMapSearchFilterBtn") as? HTMLElement
            if (m == null) return@addEventListener
            val insideMenu = m.contains(target)
            val insideBtn = b != null && b.contains(target)
            if (!insideMenu && !insideBtn) {
                closeConsigneeMapSearchFilterMenu()
            }
        })
    }
}

fun showConsigneeMapPage() {
    val content = document.getElementById("content")!!
    consigneeMapLastRenderSlice = null
    content.innerHTML = """
        <div id="consigneeMapRoot">
            <style>
                #consigneeMapRoot{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;width:100%;max-width:100%;box-sizing:border-box;}
                .consignee-map-toolbar{display:grid;grid-template-columns:1fr 1fr;grid-template-areas:"title title" "search search" "colfilter add";gap:12px;margin-bottom:16px;align-items:center;}
                .consignee-map-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;grid-area:title;text-align:center;letter-spacing:-0.01em;}
                .consignee-map-col-btn{grid-area:colfilter;justify-self:start;}
                .consignee-map-add-btn{grid-area:add;justify-self:end;}
                .consignee-map-search-row{grid-area:search;grid-column:1/-1;display:flex;align-items:center;gap:10px;width:100%;min-width:0;}
                .consignee-map-search{position:relative;flex:1;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
                .consignee-map-search input{width:100%;box-sizing:border-box;padding:11px 38px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
                .consignee-map-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:4px 8px;min-height:36px;min-width:36px;}
                .consignee-map-search-clear:hover{background:#f3f4f6;color:#111827;}
                .consignee-map-filter-wrap{position:relative;flex-shrink:0;}
                .consignee-map-col-btn{padding:10px 14px;background:#6b7280;color:#fff;border:none;border-radius:10px;cursor:pointer;font-size:14px;font-weight:600;min-height:40px;display:inline-flex;align-items:center;justify-content:center;gap:6px;}
                .consignee-map-add-btn{padding:10px 16px;background:#059669;color:#fff;border:none;border-radius:10px;cursor:pointer;font-size:14px;font-weight:600;min-height:40px;white-space:nowrap;display:inline-flex;align-items:center;justify-content:center;}
                .consignee-map-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
                #consigneeMapRoot table.purchase-list-table thead th{position:sticky;top:0;z-index:1;background:#f9fafb;}
                .consignee-map-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
                .consignee-map-empty strong{color:#0f172a;}
                .consignee-map-pager{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:10px;padding:14px 4px 4px;color:#475569;font-size:14px;}
                #consigneeMapRoot .consignee-map-search-filter-opt:hover{background:#f3f4f6!important;}
                #consigneeMapRoot .consignee-map-search-filter-opt--active{background:#eef2ff!important;font-weight:600;}
                #consigneeMapRoot .consignee-map-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;}
                #consigneeMapRoot #consigneeMapSearchFilterBtn:hover{background:#e8eaed!important;box-shadow:0 2px 8px rgba(0,0,0,0.08)!important;}
                #consigneeMapRoot #consigneeMapSearchFilterBtn:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;}
                #consigneeMapRoot .consignee-cards-container{display:flex;flex-direction:column;gap:12px;width:100%;max-width:100%;min-width:0;}
                #consigneeMapRoot .consignee-card{background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:16px;box-shadow:0 2px 4px rgba(0,0,0,0.08);max-width:100%;box-sizing:border-box;overflow:hidden;}
                #consigneeMapRoot .consignee-card .card-header{display:flex;justify-content:flex-start;align-items:center;margin-bottom:12px;padding-bottom:12px;border-bottom:1px solid #f0f0f0;gap:8px;max-width:100%;}
                #consigneeMapRoot .consignee-card .card-title{font-size:16px;font-weight:600;color:#111827;flex:1;min-width:0;line-height:1.35;word-break:break-word;}
                #consigneeMapRoot .consignee-card .card-body{max-width:100%;min-width:0;overflow:hidden;}
                #consigneeMapRoot .consignee-map-field{margin-bottom:10px;max-width:100%;min-width:0;}
                #consigneeMapRoot .consignee-map-field-label{display:block;font-weight:600;color:#64748b;font-size:12px;text-transform:uppercase;letter-spacing:.04em;margin-bottom:6px;}
                #consigneeMapRoot .consignee-map-field-value{display:block;width:100%;max-width:100%;min-width:0;overflow:hidden;}
                #consigneeMapRoot .consignee-cards-container .card-edit-btn,
                #consigneeMapRoot .consignee-card .card-edit-btn{width:24px!important;height:24px!important;min-width:24px!important;min-height:24px!important;padding:4px!important;display:inline-flex;align-items:center;justify-content:center;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 1px 3px rgba(76,201,255,0.25);flex-shrink:0;}
                #consigneeMapRoot .consignee-cards-container .card-edit-btn svg,
                #consigneeMapRoot .consignee-card .card-edit-btn svg{width:10px!important;height:10px!important;}
                @media (max-width:1024px){
                    #consigneeMapRoot{padding:14px;border-radius:14px;}
                    .consignee-map-toolbar{gap:14px;margin-bottom:14px;}
                    .consignee-map-title{font-size:17px;}
                    .consignee-map-search input{font-size:13px;padding:10px 34px 10px 38px;}
                }
                @media (max-width:767px){
                    .consignee-map-toolbar{grid-template-columns:1fr;grid-template-areas:"title" "colfilter" "search" "add";gap:12px;}
                    .consignee-map-col-btn{justify-self:stretch;width:100%;max-width:100%;min-height:44px;}
                    .consignee-map-add-btn{justify-self:stretch;width:100%;max-width:100%;min-height:44px;white-space:normal;text-align:center;}
                }
                @media (min-width:1025px){
                    #consigneeMapRoot{max-width:1200px;margin:0 auto;}
                    .consignee-map-toolbar{
                        grid-template-columns:auto 1fr minmax(260px,32%);
                        grid-template-areas:"title . search" "colfilter add .";
                        column-gap:12px;
                        row-gap:10px;
                    }
                    .consignee-map-title{text-align:left;justify-self:start;font-size:22px;}
                    .consignee-map-col-btn,.consignee-map-add-btn{width:auto;white-space:nowrap;}
                }
            </style>
            <div class="consignee-map-toolbar">
                <h2 class="consignee-map-title">Consignee Map</h2>
                <button type="button" id="consigneeColumnFilterBtn" class="consignee-map-col-btn" title="Column filter">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/></svg>
                    Column Filter
                </button>
                <div class="consignee-map-search-row">
                    <div class="consignee-map-search">
                        <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);color:#9ca3af;display:flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="text" id="consigneeMapSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Type to search…" aria-label="Search consignee map" />
                        <button type="button" id="consigneeMapSearchClearBtn" class="consignee-map-search-clear" title="Clear search" aria-label="Clear search">×</button>
                    </div>
                    <div class="consignee-map-filter-wrap">
                        <span id="consigneeMapSearchFieldLabel" class="consignee-map-sr-only" aria-live="polite">All fields</span>
                        <button type="button" id="consigneeMapSearchFilterBtn" title="Filter — search in: All fields" aria-haspopup="true" aria-expanded="false" aria-label="Open filter for which field to search. Current: All fields." style="width:46px;height:46px;border-radius:50%;border:1px solid #e5e7eb;background:#f3f4f6;cursor:pointer;display:flex;align-items:center;justify-content:center;color:#4b5563;padding:0;">
                            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                                <line x1="3" y1="7" x2="21" y2="7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="8" cy="7" r="2.25" fill="currentColor"/>
                                <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="16" cy="12" r="2.25" fill="currentColor"/>
                                <line x1="3" y1="17" x2="21" y2="17" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="7" cy="17" r="2.25" fill="currentColor"/>
                            </svg>
                        </button>
                        <div id="consigneeMapSearchFilterMenu" style="display:none;position:absolute;right:0;top:calc(100% + 8px);z-index:20001;min-width:220px;background:#fff;border:1px solid #e5e7eb;border-radius:12px;box-shadow:0 10px 40px rgba(0,0,0,0.12);padding:8px 0;">
                            <div style="padding:8px 14px 4px;font-size:11px;font-weight:600;color:#6b7280;text-transform:uppercase;letter-spacing:.04em;">Search in</div>
                            <button type="button" class="consignee-map-search-filter-opt consignee-map-search-filter-opt--active" id="consigneeMapSearchOptAll" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">All fields</button>
                            <button type="button" class="consignee-map-search-filter-opt" id="consigneeMapSearchOptConsigneeName" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Consignee name</button>
                            <button type="button" class="consignee-map-search-filter-opt" id="consigneeMapSearchOptCountry" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Country</button>
                        </div>
                    </div>
                </div>
                <button type="button" id="addConsigneeBtn" class="consignee-map-add-btn">+ Add New Consignee</button>
            </div>
            <div id="consigneeMapTableWrap">
                <div id="consigneeTable">
                    <div class="consignee-map-empty"><strong>Loading</strong><div>Loading consignee data…</div></div>
                </div>
            </div>
        </div>
    """
    
    consigneeMapSearchFieldChoice = "all"
    consigneeMapSearchServerMode = false
    consigneeMapSearchTotal = 0
    consigneeMapSearchTotalPages = 0
    consigneeMapSearchPageZeroBased = 0
    consigneesCurrentPage = 1
    updateConsigneeMapSearchFilterMenuActive("all")
    refreshConsigneeMapSearchScopeUi()
    setupConsigneeMapSearchBarListeners()
    setupConsigneeMapResizeListener()
    
    loadMasterConsignee()
    
    document.getElementById("addConsigneeBtn")?.addEventListener("click", { _: Event ->
        showAddConsigneeModal()
    })
    
    document.getElementById("consigneeColumnFilterBtn")?.addEventListener("click", { _: Event ->
        showConsigneeColumnFilterModal()
    })
}

/**
 * Check if device type changed for Consignee page and reload if needed
 */
fun checkConsigneeDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, reload consignees to switch between card/table views
    if (lastConsigneeDeviceType != null && lastConsigneeDeviceType != currentDeviceType) {
        Logger.debug("Consignee page: Device type changed from $lastConsigneeDeviceType to $currentDeviceType, reloading consignees")
        loadMasterConsignee()
    }
    
    // Update last device type
    lastConsigneeDeviceType = currentDeviceType
}

/**
 * Setup window resize listener for Consignee page to detect device changes
 */
fun setupConsigneeDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__consigneeDeviceChangeListener
    if (existingListener != null) {
        val listenerFunc = existingListener.unsafeCast<((Event) -> Unit)?>()
        window.removeEventListener("resize", listenerFunc)
    }
    
    // Debounce resize events
    var resizeTimeout: dynamic = null
    val resizeListener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) {
            window.clearTimeout(resizeTimeout)
        }
        resizeTimeout = window.setTimeout({
            // Check if device type actually changed
            val newDeviceType = getDeviceType()
            if (lastConsigneeDeviceType != null && lastConsigneeDeviceType != newDeviceType) {
                // Device changed - reload consignees to switch between card/table views
                Logger.debug("Consignee page: Device type changed from $lastConsigneeDeviceType to $newDeviceType, reloading")
                
                // If we're on the consignee page, reload to show correct view (cards or table)
                if (window.location.hash.contains("#/master/consignee")) {
                    loadMasterConsignee()
                }
            }
            lastConsigneeDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__consigneeDeviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadMasterConsignee() {
    if (document.getElementById("consigneeTable") == null) return
    loadMasterConsigneesWithTable()
}

/**
 * Renders Consignee Map table + pagination. [isServerSearch] uses [consigneeMapSearchPageZeroBased] / totals from API.
 */
private fun buildConsigneeTableUi(
    tableDiv: HTMLElement,
    paginatedMappings: List<dynamic>,
    orderedForDisplay: List<dynamic>,
    filterLabel: String,
    totalPages: Int,
    isServerSearch: Boolean,
    footerStart: Int,
    footerEnd: Int,
) {
    val consigneeSortable = setOf("consigneeName", "country", "pod")
    if (orderedForDisplay.isEmpty()) {
        val message = if (filterLabel.isNotEmpty()) {
            "No matches for your search."
        } else {
            "No consignee data found yet."
        }
        tableDiv.innerHTML = """<div class="consignee-map-empty"><strong>No results</strong><div>$message</div></div>"""
        return
    }

    if (paginatedMappings.isEmpty()) {
        tableDiv.innerHTML = """<div class="consignee-map-empty"><strong>No results</strong><div>No rows on this page.</div></div>"""
        return
    }

            val selectedColumns = getSelectedConsigneeColumns()
            val columnLabels = mapOf(
                "country" to "Country",
                "consigneeName" to "Consignee Name",
                "consigneeAddress" to "Consignee Address",
        "pod" to "POD"
            )
            
            val consigneeColCount = 1 + selectedColumns.size
            var html = """
                <div class="consignee-map-table-shell">
                    <table class="purchase-list-table consignee-table" style="width:100%;border-collapse:collapse;table-layout:fixed;">${htmlTableColgroupNarrowActionEqualRest(consigneeColCount)}
                        <thead>
                            <tr>
                                <th style="padding:12px 14px;text-align:left;min-width:88px;"></th>
            """
            
            for (columnKey in selectedColumns) {
                val label = columnLabels[columnKey] ?: columnKey
        html += if (columnKey in consigneeSortable) {
            val tip = masterMapColumnSortTooltip(consigneeMapSortOrderByField, columnKey)
            val bid = "consigneeMapSort_$columnKey"
            """<th><button type="button" id="$bid" title="$tip" style="background:none;border:none;cursor:pointer;font-weight:700;color:#111827;padding:0;display:inline-flex;align-items:center;gap:6px;"><span>${escapeHtml(label)}</span><span style="font-size:14px;">&#x2195;</span></button></th>"""
        } else {
            """<th>${escapeHtml(label)}</th>"""
        }
            }
            
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (mapping in paginatedMappings) {
                val id = (mapping.id ?: "").toString()
                val country = (mapping.country ?: "").toString()
                val consigneeName = (mapping.consigneeName ?: "").toString()
                val consigneeAddress = (mapping.consigneeAddress ?: "").toString()
                val consigneeAddressShort = if (consigneeAddress.length > 60) consigneeAddress.take(60) + "..." else consigneeAddress
                val pod = (mapping.pod ?: "").toString()
                
                html += """
                    <tr>
                        <td style="padding:10px 12px;vertical-align:middle;">
                            <div style="display:flex;gap:6px;align-items:center;">
                            <button onclick="window.editMasterConsignee($id)" aria-label="Edit" title="Edit"
                                    style="width:36px;height:36px;min-width:36px;min-height:36px;display:inline-flex;align-items:center;justify-content:center;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 6px rgba(76,201,255,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                </svg>
                            </button>
                            <button onclick="window.duplicateMasterConsignee($id)" aria-label="Duplicate" title="Duplicate"
                                    style="width:36px;height:36px;min-width:36px;min-height:36px;display:inline-flex;align-items:center;justify-content:center;background-color:#3b82f6;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 6px rgba(59,130,246,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
                                </svg>
                            </button>
                            </div>
                        </td>
                """
                
                for (columnKey in selectedColumns) {
                    val value = when (columnKey) {
                        "country" -> country
                        "consigneeName" -> consigneeName
                        "consigneeAddress" -> consigneeAddressShort
                        "pod" -> pod
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                "country", "consigneeName" -> "padding: 12px 16px; color: #111827; font-size: 14px; font-weight: 500; vertical-align: top; border: 1px solid #e5e7eb; background-color: #ffffff;"
                "consigneeAddress" -> "padding: 12px 16px; color: #374151; font-size: 14px; vertical-align: top; border: 1px solid #e5e7eb; background-color: #ffffff;"
                else -> "padding: 12px 16px; color: #111827; font-size: 14px; vertical-align: top; border: 1px solid #e5e7eb; background-color: #ffffff;"
            }
            val titleAttr = if (columnKey == "consigneeAddress" && consigneeAddress.length > 60) " title=\"${escapeHtml(consigneeAddress)}\"" else ""
            val cellInner =
                if (columnKey == "consigneeAddress") formatConsigneeMapAddressChipHtml(value)
                else formatConsigneeMapValueChipHtml(value)
            html += """<td style="$cellStyle"$titleAttr>$cellInner</td>"""
                }
                
                html += """</tr>"""
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
    val footerSummary = if (isServerSearch) {
        "Page $consigneesCurrentPage of $totalPages (search) · ${consigneeMapSearchTotal} matching row(s) · ${paginatedMappings.size} on this page"
    } else {
        "Showing $footerStart to $footerEnd of ${orderedForDisplay.size} consignee${if (orderedForDisplay.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"
    }

            if (totalPages > 1) {
                val prevDisabled = if (consigneesCurrentPage <= 1) " disabled" else ""
                val nextDisabled = if (consigneesCurrentPage >= totalPages) " disabled" else ""
                html += """
                    <div class="consignee-map-pager">
                        <span style="flex:1;min-width:200px;">$footerSummary</span>
                        <div class="consignee-pagination-controls" style="display:flex;align-items:center;gap:10px;">
                            <button type="button" id="consigneesPrevPage" class="consignee-pagination-btn"$prevDisabled>Prev</button>
                            <span class="consignee-pagination-page">Page $consigneesCurrentPage of $totalPages</span>
                            <button type="button" id="consigneesNextPage" class="consignee-pagination-btn"$nextDisabled>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """<div class="consignee-map-pager"><span>${if (isServerSearch) footerSummary else "Total: ${orderedForDisplay.size} consignee${if (orderedForDisplay.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"}</span></div>"""
            }
            
            tableDiv.innerHTML = html
            
            document.getElementById("consigneesPrevPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (consigneeMapSearchPageZeroBased > 0) {
                consigneeMapSearchPageZeroBased--
                consigneesCurrentPage = consigneeMapSearchPageZeroBased + 1
                loadMasterConsignee()
            }
        } else if (consigneesCurrentPage > 1) {
                    consigneesCurrentPage--
                    loadMasterConsignee()
                }
            })
            
            document.getElementById("consigneesNextPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (consigneeMapSearchPageZeroBased < consigneeMapSearchTotalPages - 1) {
                consigneeMapSearchPageZeroBased++
                consigneesCurrentPage = consigneeMapSearchPageZeroBased + 1
                loadMasterConsignee()
            }
        } else {
            val tp = kotlin.math.ceil(allConsignees.size.toDouble() / consigneesItemsPerPage).toInt()
            if (consigneesCurrentPage < tp) {
                    consigneesCurrentPage++
                    loadMasterConsignee()
            }
        }
    })
    val consigneeSortKeys = listOf("consigneeName", "country", "pod")
    for (key in consigneeSortKeys) {
        if (key in selectedColumns) {
            document.getElementById("consigneeMapSort_$key")?.addEventListener("click", { _: Event ->
                toggleConsigneeMapSort(key)
            })
        }
    }
}

fun loadMasterConsigneesWithTable() {
    val tableDiv = document.getElementById("consigneeTable") as? HTMLElement ?: return

    val searchQ = getConsigneeMapSearchQuery()

    tableDiv.innerHTML = """<div class="consignee-map-empty"><strong>Loading</strong><div>Loading consignee data…</div></div>"""

    if (searchQ.isNotEmpty()) {
        consigneeMapSearchServerMode = true
        val encQ = js("encodeURIComponent")(searchQ).unsafeCast<String>()
        val encF = js("encodeURIComponent")(consigneeMapSearchFieldChoice).unsafeCast<String>()
        val p = consigneeMapSearchPageZeroBased
        val url = apiUrl("booking/mappings/page-search?q=$encQ&field=$encF&page=$p&size=$consigneesItemsPerPage")
        window.fetch(url)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Search failed')")
            }
            .then { body: dynamic ->
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) throw js("Error(err)")
                val totalEl = js("body.totalElements")
                consigneeMapSearchTotal = when (totalEl) {
                    is Number -> totalEl.toLong()
                    else -> totalEl?.toString()?.toLongOrNull() ?: 0L
                }
                val tp = js("body.totalPages")
                consigneeMapSearchTotalPages = kotlin.math.max(1, when (tp) {
                    is Number -> tp.toInt()
                    else -> tp?.toString()?.toIntOrNull() ?: 1
                })
                val num = js("body.page")
                consigneeMapSearchPageZeroBased = when (num) {
                    is Number -> num.toInt()
                    else -> num?.toString()?.toIntOrNull() ?: 0
                }
                consigneesCurrentPage = consigneeMapSearchPageZeroBased + 1

                val content = js("body.content") ?: js("[]")
                val arr = js("Array.isArray(content) ? content : []") as Array<dynamic>
                val list = arr.toList()
                allConsignees = list

                if (list.isEmpty()) {
                    consigneeMapLastRenderSlice = null
                    tableDiv.innerHTML = """<div class="consignee-map-empty"><strong>No results</strong><div>No matches for your search.</div></div>"""
                    return@then
                }

                val totalPages = consigneeMapSearchTotalPages
                renderConsigneeMapList(
                    paginatedMappings = list,
                    orderedForDisplay = list,
                    filterLabel = searchQ,
                    totalPages = totalPages,
                    isServerSearch = true,
                    footerStart = 1,
                    footerEnd = list.size,
                )
            }
            .catch { error: dynamic ->
                Logger.error("Error searching consignees: ${error.toString()}")
                consigneeMapLastRenderSlice = null
                tableDiv.innerHTML = """<div class="consignee-map-empty" style="color:#dc2626;"><strong>Could not load</strong><div>${escapeHtml(error.asDynamic().message?.toString() ?: "Search failed")}</div></div>"""
            }
        return
    }

    consigneeMapSearchServerMode = false
    consigneeMapSearchTotal = 0
    consigneeMapSearchTotalPages = 0
    consigneeMapSearchPageZeroBased = 0

    window.fetch(apiUrl("booking/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignee')")
        }
        .then { result: dynamic ->
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            val filteredMappings = mappingsArray.toList().sortedByDescending {
                (it.id as? Number)?.toLong() ?: 0L
            }
            val consigneeSortable = setOf("consigneeName", "country", "pod")
            var orderedForDisplay = filteredMappings
            val cmsf = consigneeMapSortField
            if (cmsf != null && cmsf in consigneeSortable) {
                val ord = consigneeMapSortOrderByField[cmsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    filteredMappings.sortedBy { extractConsigneeMapSortKey(it, cmsf) }
                } else {
                    filteredMappings.sortedByDescending { extractConsigneeMapSortKey(it, cmsf) }
                }
            }
            allConsignees = orderedForDisplay

            if (orderedForDisplay.isEmpty()) {
                consigneeMapLastRenderSlice = null
                tableDiv.innerHTML = """<div class="consignee-map-empty"><strong>No results</strong><div>No consignee data found yet.</div></div>"""
                return@then
            }

            val totalPages = kotlin.math.max(1, kotlin.math.ceil(orderedForDisplay.size.toDouble() / consigneesItemsPerPage).toInt())
            val startIndex = (consigneesCurrentPage - 1) * consigneesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + consigneesItemsPerPage, orderedForDisplay.size)
            val paginatedMappings = orderedForDisplay.subList(startIndex, endIndex)
            val footerStart = startIndex + 1
            val footerEnd = endIndex

            renderConsigneeMapList(
                paginatedMappings = paginatedMappings,
                orderedForDisplay = orderedForDisplay,
                filterLabel = "",
                totalPages = totalPages,
                isServerSearch = false,
                footerStart = footerStart,
                footerEnd = footerEnd,
            )
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignee: ${error.toString()}")
            consigneeMapLastRenderSlice = null
            tableDiv.innerHTML = """<div class="consignee-map-empty" style="color:#dc2626;"><strong>Could not load</strong><div>${escapeHtml(error.message?.toString() ?: "Failed to load consignee data")}</div></div>"""
        }
}

fun displayConsigneesAsCards(filteredMappings: List<dynamic>, filterLabel: String, isServerSearch: Boolean = false) {
    val tableDiv = document.getElementById("consigneeTable")
    if (tableDiv == null) return
    
    if (filteredMappings.isEmpty()) {
        val message = if (filterLabel.isNotEmpty()) "No matches for your search." else "No consignee data found yet."
        tableDiv.innerHTML = """<div class="consignee-map-empty"><strong>No results</strong><div>$message</div></div>"""
        return
    }
    
    val totalPages = if (isServerSearch) {
        kotlin.math.max(1, consigneeMapSearchTotalPages)
    } else {
        kotlin.math.max(1, kotlin.math.ceil(filteredMappings.size.toDouble() / consigneesItemsPerPage).toInt())
    }
    val paginatedMappings = if (isServerSearch) {
        filteredMappings
    } else {
    val startIndex = (consigneesCurrentPage - 1) * consigneesItemsPerPage
    val endIndex = kotlin.math.min(startIndex + consigneesItemsPerPage, filteredMappings.size)
        filteredMappings.subList(startIndex, endIndex)
    }
    
    val selectedColumns = getSelectedConsigneeColumns()
    val columnLabels = mapOf(
        "country" to "Country",
        "consigneeName" to "Consignee Name",
        "consigneeAddress" to "Consignee Address",
        "pod" to "POD"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="consignee-cards-container">""")
    
    for (mapping in paginatedMappings) {
        val id = (mapping.id ?: "").toString()
        val country = (mapping.country ?: "").toString()
        val consigneeName = (mapping.consigneeName ?: "").toString()
        val consigneeAddress = (mapping.consigneeAddress ?: "").toString()
        val pod = (mapping.pod ?: "").toString()
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "country" -> country
                "consigneeName" -> consigneeName
                "consigneeAddress" -> consigneeAddress
                "pod" -> pod
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                val displayValue =
                    if (columnKey == "consigneeAddress") formatConsigneeMapAddressChipHtml(value)
                    else formatConsigneeMapValueChipHtml(value)
                cardFields.append("""
                    <div class="consignee-map-field">
                        <span class="consignee-map-field-label">${escapeHtml(label)}</span>
                        <div class="consignee-map-field-value">$displayValue</div>
                    </div>
                """)
            }
        }
        
        val cardTitle = escapeHtml(
            when {
                consigneeName.isNotEmpty() -> consigneeName
                country.isNotEmpty() -> country
                else -> "Consignee #$id"
            },
        )
        cardsHTML.append("""
            <div class="consignee-card">
                <div class="card-header">
                    <button type="button" class="card-edit-btn" onclick="window.editMasterConsignee($id)" aria-label="Edit" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                    <button type="button" class="card-edit-btn" onclick="window.duplicateMasterConsignee($id)" aria-label="Duplicate" title="Duplicate" style="background-color:#3b82f6;">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
                        </svg>
                    </button>
                    <div class="card-title">$cardTitle</div>
                </div>
                <div class="card-body">
                    $cardFields
                </div>
            </div>
        """)
    }
    
    cardsHTML.append("</div>")
    
    // Add pagination controls
    if (totalPages > 1) {
        val prevDisabled = if (consigneesCurrentPage <= 1) " disabled" else ""
        val nextDisabled = if (consigneesCurrentPage >= totalPages) " disabled" else ""
        cardsHTML.append("""
            <div class="consignee-map-pager">
                <div class="pagination-controls" style="display:flex;align-items:center;justify-content:center;gap:10px;width:100%;">
                    <button type="button" id="consigneesPrevPage" class="pagination-btn"$prevDisabled>Prev</button>
                    <span class="pagination-page">Page $consigneesCurrentPage of $totalPages</span>
                    <button type="button" id="consigneesNextPage" class="pagination-btn"$nextDisabled>Next</button>
                </div>
            </div>
        """)
    } else {
        val summary = if (isServerSearch) {
            "Page $consigneesCurrentPage of $totalPages (search) · ${consigneeMapSearchTotal} matching row(s) · ${paginatedMappings.size} on this page"
        } else {
            "Total: ${filteredMappings.size} consignee${if (filteredMappings.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"
        }
        cardsHTML.append("""<div class="consignee-map-pager"><span>$summary</span></div>""")
    }
    
    tableDiv.innerHTML = cardsHTML.toString()
    
    // Add pagination event listeners
    document.getElementById("consigneesPrevPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (consigneeMapSearchPageZeroBased > 0) {
                consigneeMapSearchPageZeroBased--
                consigneesCurrentPage = consigneeMapSearchPageZeroBased + 1
                loadMasterConsignee()
            }
        } else if (consigneesCurrentPage > 1) {
            consigneesCurrentPage--
            loadMasterConsignee()
        }
    })
    
    document.getElementById("consigneesNextPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (consigneeMapSearchPageZeroBased < consigneeMapSearchTotalPages - 1) {
                consigneeMapSearchPageZeroBased++
                consigneesCurrentPage = consigneeMapSearchPageZeroBased + 1
                loadMasterConsignee()
            }
        } else {
            val tp = kotlin.math.ceil(allConsignees.size.toDouble() / consigneesItemsPerPage).toInt()
            if (consigneesCurrentPage < tp) {
            consigneesCurrentPage++
            loadMasterConsignee()
            }
        }
    })
}

fun showConsigneeColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("consigneeColumnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "consigneeColumnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; align-items: center; justify-content: center;
    """
    
    // Get current device type and limits
    val deviceType = getDeviceType()
    val maxColumns = getMaxConsigneeSupplierMapColumnsForDevice(deviceType)
    val deviceDisplayName = when (deviceType) {
        "mobile" -> "Mobile View"
        "tablet" -> "Tablet View"
        else -> "Desktop View"
    }
    
    val selectedColumnsList = getSelectedConsigneeColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeConsigneeColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="consigneeSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="consigneeColumnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetConsigneeColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyConsigneeColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes (order matches table default: Name → Address → Country → POD)
    val columnLabels = listOf(
        "consigneeName" to "Consignee Name",
        "consigneeAddress" to "Consignee Address",
        "country" to "Country",
        "pod" to "POD"
    )
    
    val checkboxesDiv = document.getElementById("consigneeColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val checkbox = document.createElement("div")
        val checkboxStyle = checkbox.asDynamic().style
        checkboxStyle.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "consigneeCol_$key"
        input.setAttribute("data-column", key)
        input.checked = selectedColumns.contains(key)
        input.addEventListener("change", { _: Event ->
            updateConsigneeColumnSelection()
        })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "consigneeCol_$key"
        labelEl.textContent = label
        val labelStyle = labelEl.asDynamic().style
        labelStyle.cssText = "cursor: pointer; margin: 0;"
        checkbox.appendChild(input)
        checkbox.appendChild(labelEl)
        checkboxesDiv?.appendChild(checkbox)
    }
    
    // Update selection count initially
    updateConsigneeColumnSelection()
    
    // Add event listeners
    document.getElementById("closeConsigneeColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("consigneeColumnFilterModal")?.remove()
    })
    document.getElementById("resetConsigneeColumns")?.addEventListener("click", { _: Event ->
        val deviceType = getDeviceType()
        val defaultColumns = getDefaultConsigneeColumnsForDevice(deviceType)
        columnLabels.map { it.first }.forEach { col ->
            val checkbox = document.getElementById("consigneeCol_$col") as? HTMLInputElement
            checkbox?.checked = defaultColumns.contains(col)
        }
        updateConsigneeColumnSelection()
    })
    document.getElementById("applyConsigneeColumns")?.addEventListener("click", { _: Event ->
        applyConsigneeColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "consigneeColumnFilterModal") {
            document.getElementById("consigneeColumnFilterModal")?.remove()
        }
    })
}

fun updateConsigneeColumnSelection() {
    val deviceType = getDeviceType()
    val maxColumns = getMaxConsigneeSupplierMapColumnsForDevice(deviceType)
    val checkboxes = document.querySelectorAll("#consigneeColumnCheckboxes input[type='checkbox']")
    var selectedCount = 0
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedCount++
        }
    }
    
    val countSpan = document.getElementById("consigneeSelectedCount")
    countSpan?.textContent = "$selectedCount"
    
    // Disable/enable checkboxes based on max limit
    if (selectedCount >= maxColumns) {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            if (!checkbox.checked) {
                checkbox.disabled = true
            }
        }
    } else {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.disabled = false
        }
    }
}

fun applyConsigneeColumnChanges() {
    val checkboxes = document.querySelectorAll("#consigneeColumnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableListOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val columnKey = checkbox.getAttribute("data-column") ?: ""
            if (columnKey.isNotEmpty()) {
                selectedColumns.add(columnKey)
            }
        }
    }
    
    // Save to localStorage
    safeLocalStorageSet("selectedConsigneeColumns", JSON.stringify(selectedColumns.toTypedArray()))
    
    // Close modal
    document.getElementById("consigneeColumnFilterModal")?.remove()
    
    // Reload consignees to apply changes
    loadMasterConsignee()
}

fun showAddConsigneeModal() {
    showConsigneeModal(null)
}

fun showConsigneeModal(mappingId: Long?, duplicateFromId: Long? = null) {
    consigneeModalNotesSnapshot = null
    val isDuplicate = duplicateFromId != null
    val isEdit = mappingId != null && !isDuplicate
    val title = when {
        isDuplicate -> "Duplicate Consignee"
        isEdit -> "Edit Consignee"
        else -> "Add New Consignee"
    }
    
    val modalHtml = """
        <div id="consigneeModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeConsigneeModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="consigneeForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Consignee Name <span style="color: #ef4444;">*</span></label>
                            ${createPlainTextInput("consigneeMapConsigneeName", "Enter Consignee Name", required = true)}
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Consignee Address</label>
                            <textarea id="consigneeAddress" rows="4" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box; resize: vertical; font-family: inherit;"></textarea>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Country</label>
                            ${createChipMultiSelectCombobox("consigneeMapCountry", "Select Country")}
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POD (Port of Discharge)</label>
                            ${createChipMultiSelectCombobox("consigneeMapPod", "Select POD")}
                        </div>
                        <div class="consignee-modal-actions">
                            <button type="button" id="cancelConsigneeBtn" class="consignee-modal-btn consignee-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteConsigneeBtn" class="consignee-modal-btn consignee-modal-btn-delete">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveConsigneeBtn" class="consignee-modal-btn consignee-modal-btn-save">${if (isEdit) "Update" else "Save"} Consignee</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    
    ensureSupplierChipJs()
    populateConsigneeMapModalComboboxes()

    when {
        isDuplicate && duplicateFromId != null -> loadConsigneeDataForEdit(duplicateFromId, clearConsigneeNameAndAddressForDuplicate = true)
        isEdit && mappingId != null -> loadConsigneeDataForEdit(mappingId)
    }
    
    // Event listeners
    document.getElementById("closeConsigneeModal")?.addEventListener("click", { _: Event ->
        closeConsigneeModal()
    })
    
    document.getElementById("cancelConsigneeBtn")?.addEventListener("click", { _: Event ->
        closeConsigneeModal()
    })
    
    // Delete button (only shown in edit mode); confirmation is inside deleteMasterConsignee only
    if (isEdit && mappingId != null) {
        document.getElementById("deleteConsigneeBtn")?.addEventListener("click", { _: Event ->
                deleteMasterConsignee(mappingId)
        })
    }
    
    document.getElementById("consigneeForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        val saveId = if (isDuplicate) null else mappingId
        saveConsignee(saveId)
    })
    
    // Close on background click
    document.getElementById("consigneeModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "consigneeModal") {
            closeConsigneeModal()
        }
    })
}

fun loadConsigneeDataForEdit(mappingId: Long, clearConsigneeNameAndAddressForDuplicate: Boolean = false) {
    window.fetch(apiUrl("booking/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load consignee data')")
        }
        .then { result: dynamic ->
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            val mapping = mappingsArray.find { (it.id ?: 0).toString() == mappingId.toString() }
            
            if (mapping != null) {
                val notesVal = (mapping.notes ?: "").toString()
                consigneeModalNotesSnapshot =
                    if (clearConsigneeNameAndAddressForDuplicate) null
                    else notesVal.takeUnless { it.isBlank() }
                val countryRaw = (mapping.country ?: "").toString()
                val nameRaw = (mapping.consigneeName ?: "").toString()
                val podRaw = (mapping.pod ?: "").toString()
                val addrRaw = (mapping.consigneeAddress ?: "").toString()
                window.setTimeout({
                    setChipFieldValue("consigneeMapCountry", normalizeStoredListForChips(countryRaw))
                    setEditableComboboxValue("consigneeMapConsigneeName", if (clearConsigneeNameAndAddressForDuplicate) "" else nameRaw.trim())
                    setChipFieldValue("consigneeMapPod", normalizeStoredListForChips(podRaw))
                    (document.getElementById("consigneeAddress") as? HTMLTextAreaElement)?.value =
                        if (clearConsigneeNameAndAddressForDuplicate) "" else addrRaw
                }, 450)
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading consignee data: ${error.toString()}")
            showMessage("Error loading consignee data: ${error.message}", "error")
        }
}

fun closeConsigneeModal() {
    consigneeModalNotesSnapshot = null
    document.getElementById("consigneeModal")?.remove()
}

fun saveConsignee(mappingId: Long?) {
    val country = getChipFieldValue("consigneeMapCountry")
    val consigneeName = getEditableComboboxValue("consigneeMapConsigneeName")
    
    if (consigneeName.isEmpty()) {
        showMessage("Consignee Name is required", "error")
        return
    }
    
    val pod = getChipFieldValue("consigneeMapPod")
    
    val saveButton = document.getElementById("saveConsigneeBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = "Validating..."
    
    validateConsigneeMasterFields(country, consigneeName, pod) { missingFields ->
        if (missingFields.isNotEmpty()) {
            // Close consignee modal and show error modal
            closeConsigneeModal()
            showConsigneeMasterFieldsErrorModal(missingFields)
        } else {
            // All fields are valid, proceed with save
            performConsigneeSave(mappingId)
        }
    }
}

fun validateConsigneeMasterFields(
    country: String,
    consigneeName: String,
    pod: String,
    callback: (List<Pair<String, String>>) -> Unit
) {
    // Master-menu membership checks removed — always allow save; backend may still validate.
            callback(emptyList())
}

fun parseMasterListArray(raw: dynamic): List<String> {
    return try {
        if (raw != null && js("Array.isArray(raw)").unsafeCast<Boolean>()) {
            val arr = raw.unsafeCast<Array<String>>()
            arr.map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    } catch (e: dynamic) {
        Logger.error("Error parsing master list: ${e.toString()}")
        emptyList()
    }
}

fun showConsigneeMasterFieldsErrorModal(missingFields: List<Pair<String, String>>) {
    document.getElementById("consigneeMasterFieldsErrorModal")?.remove()
    
    // Build the error message
    val fieldNames = missingFields.map { it.first }.joinToString(", ")
    val pageNames = missingFields.map { it.second }.distinct().joinToString(", ")
    
    val modal = document.createElement("div")
    modal.id = "consigneeMasterFieldsErrorModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10001;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #ef4444;">Field(s) Not Found in Master List</h3>
            <p style="margin-bottom: 20px; color: #374151; font-size: 14px; line-height: 1.6;">
                <strong>$fieldNames</strong> does not exist in Master List. Go to the <strong>$pageNames</strong> page and add the missing value(s).
            </p>
            <div style="display: flex; justify-content: flex-end;">
                <button id="closeConsigneeMasterFieldsErrorModalBtn" style="padding: 10px 24px; border-radius: 6px; border: none; background: #3b82f6; color: white; cursor: pointer; font-size: 14px; font-weight: 500;">
                    Close
                </button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    
    document.getElementById("closeConsigneeMasterFieldsErrorModalBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })
}

fun performConsigneeSave(mappingId: Long?) {
    val country = getChipFieldValue("consigneeMapCountry")
    
    val consigneeData = js("{}")
    consigneeData.country = country
    consigneeData.clientName = null
    consigneeData.consigneeName = getEditableComboboxValue("consigneeMapConsigneeName").takeUnless { it.isBlank() }
    consigneeData.consigneeAddress = (document.getElementById("consigneeAddress") as? HTMLTextAreaElement)?.value?.trim() ?: null
    consigneeData.pod = getChipFieldValue("consigneeMapPod").takeUnless { it.isBlank() }
    consigneeData.stockLocation = null
    consigneeData.pols = null
    consigneeData.notes = consigneeModalNotesSnapshot?.takeUnless { it.isBlank() }
    
    val saveButton = document.getElementById("saveConsigneeBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = if (mappingId != null) "Updating..." else "Saving..."
    
    val url = if (mappingId != null) {
        apiUrl("booking/mappings/$mappingId")
    } else {
        apiUrl("booking/mappings/add")
    }
    
    val method = if (mappingId != null) "PUT" else "POST"
    
    val requestInit = js("{}")
    requestInit.method = method
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(consigneeData)
    
    window.fetch(url, requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to save consignee')")
        }
        .then { result: dynamic ->
            if (result.success) {
                val serverMsg = (result.message as? String)?.trim().orEmpty()
                val fallback =
                    if (mappingId != null) "Consignee updated successfully" else "Consignee added successfully"
                showMessage(if (serverMsg.isNotEmpty()) serverMsg else fallback, "success")
                closeConsigneeModal()
                loadMasterConsignee()
            } else {
                throw js("Error(result.message || 'Failed to save consignee')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error saving consignee: ${error.toString()}")
            showMessage("Error saving consignee: ${error.message}", "error")
        }
        .finally {
            saveButton?.disabled = false
            saveButton?.textContent = if (mappingId != null) "Update Consignee" else "Save Consignee"
        }
}

fun deleteMasterConsignee(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId == null) {
        showMessage("Invalid consignee ID", "error")
        return
    }
    
    if (!js("confirm('Are you sure you want to delete this consignee? This action cannot be undone.')").unsafeCast<Boolean>()) {
        return
    }
    
    val requestInit = js("{}")
    requestInit.method = "DELETE"
    
    window.fetch(apiUrl("booking/mappings/$mappingId"), requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to delete consignee')")
        }
        .then { result: dynamic ->
            if (result.success) {
                showMessage("Consignee deleted successfully", "success")
                closeConsigneeModal()
                loadMasterConsignee()
            } else {
                throw js("Error(result.message || 'Failed to delete consignee')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error deleting consignee: ${error.toString()}")
            showMessage("Error deleting consignee: ${error.message}", "error")
        }
}

fun editMasterConsignee(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId != null) {
        showConsigneeModal(mappingId)
    }
}

fun duplicateMasterConsignee(id: dynamic) {
    val sourceId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (sourceId != null) {
        showConsigneeModal(mappingId = null, duplicateFromId = sourceId)
    }
}

/** Car Brands page: shows a car brands master list (from master_menu). */
fun showCarBrandsPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="carBrandsMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Car Brands</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addCarBrandsMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Car Brands</span>
                    </button>
                </div>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Car Brands:</label>
                    <input type="text" id="carBrandsMasterFilter" placeholder="Type car brand name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="carBrandsMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading car brands...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadCarBrandsMasterList()

    document.getElementById("carBrandsMasterFilter")?.addEventListener("input", { _: Event ->
        loadCarBrandsMasterList()
    })

    document.getElementById("addCarBrandsMasterBtn")?.addEventListener("click", { _: Event ->
        showAddCarBrandsMasterModal()
    })
}

fun loadCarBrandsMasterList() {
    val tableDiv = document.getElementById("carBrandsMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("carBrandsMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading car brands...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/car_brands"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brands')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allCarBrandsMaster = filtered
            if (searchFilter.isNotEmpty()) carBrandsMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No car brands found for: $searchFilter" else "No car brands found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "carBrands")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / carBrandsMasterItemsPerPage).toInt()
            val startIndex = (carBrandsMasterCurrentPage - 1) * carBrandsMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + carBrandsMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "carBrands" to "Car Brands")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="car-brands-master-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
            """
            for (col in selectedColumns) {
                val label = columnLabels[col] ?: col
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, carBrandName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="car-brands-master-edit-btn"
                                        data-car-brands="${carBrandName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "carBrands" -> carBrandName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "carBrands" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                html += """</tr>"""
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} car brand${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="carBrandsMasterPrevPage" class="consignee-pagination-btn" ${if (carBrandsMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $carBrandsMasterCurrentPage of $totalPages</span>
                            <button id="carBrandsMasterNextPage" class="consignee-pagination-btn" ${if (carBrandsMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} car brand${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".car-brands-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-car-brands") ?: return@addEventListener
                    showEditCarBrandsMasterModal(name)
                })
            }
            
            document.getElementById("carBrandsMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (carBrandsMasterCurrentPage > 1) {
                    carBrandsMasterCurrentPage--
                    loadCarBrandsMasterList()
                }
            })
            document.getElementById("carBrandsMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allCarBrandsMaster.size.toDouble() / carBrandsMasterItemsPerPage).toInt()
                if (carBrandsMasterCurrentPage < totalP) {
                    carBrandsMasterCurrentPage++
                    loadCarBrandsMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brands</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddCarBrandsMasterModal() {
    document.getElementById("carBrandsMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "carBrandsMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Car Brands</h3>
            <div style="margin-bottom: 16px;">
                <label for="carBrandsMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Car Brands</label>
                <input type="text" id="carBrandsMasterModalInput" placeholder="Enter car brand name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="carBrandsMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="carBrandsMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("carBrandsMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("carBrandsMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("carBrandsMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Car brand name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/car_brands"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add car brand')")
            }
            .then { _: dynamic ->
                showMessage("Car brand added successfully", "success")
                modal.remove()
                carBrandsMasterCurrentPage = 1
                loadCarBrandsMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding car brand: ${error.toString()}")
                showMessage("Error adding car brand: ${error.message}", "error")
            }
    })
}

fun showEditCarBrandsMasterModal(originalName: String) {
    document.getElementById("carBrandsMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "carBrandsMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Car Brands</h3>
            <div style="margin-bottom: 16px;">
                <label for="carBrandsMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Car Brands</label>
                <input type="text" id="carBrandsMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="carBrandsMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="carBrandsMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="carBrandsMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("carBrandsMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("carBrandsMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("carBrandsMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Car brand name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/car_brands"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update car brand')")
            }
            .then { _: dynamic ->
                showMessage("Car brand updated successfully", "success")
                modal.remove()
                loadCarBrandsMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating car brand: ${error.toString()}")
                showMessage("Error updating car brand: ${error.message}", "error")
            }
    })

    document.getElementById("carBrandsMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete car brand '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/car_brands?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete car brand')")
            }
            .then { _: dynamic ->
                showMessage("Car brand deleted successfully", "success")
                modal.remove()
                carBrandsMasterCurrentPage = 1
                loadCarBrandsMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting car brand: ${error.toString()}")
                showMessage("Error deleting car brand: ${error.message}", "error")
            }
    })
}

private fun getCarBrandSearchQuery(): String =
    (document.getElementById("carBrandSearchInput") as? HTMLInputElement)?.value?.trim() ?: ""

private fun carBrandMapSearchFieldDisplayLabel(): String = when (carBrandMapSearchFieldChoice) {
    "chassis" -> "Chassis"
    "brand" -> "Car brand"
    "carName" -> "Car name"
    else -> "All fields"
}

private fun refreshCarBrandSearchScopeUi() {
    val scopeLabel = carBrandMapSearchFieldDisplayLabel()
    val btn = document.getElementById("carBrandSearchFilterBtn") as? HTMLElement
    if (btn != null) {
        btn.setAttribute("title", "Filter — search in: $scopeLabel")
        btn.setAttribute("aria-label", "Open filter for which field to search. Current: $scopeLabel.")
    }
    val sr = document.getElementById("carBrandSearchFieldLabel") as? HTMLElement
    if (sr != null) sr.textContent = scopeLabel
}

private fun closeCarBrandSearchFilterMenu() {
    val menu = document.getElementById("carBrandSearchFilterMenu") as? HTMLElement
    menu?.style?.setProperty("display", "none", "important")
    window.asDynamic().__carBrandSearchFilterMenuOpen = false
    document.getElementById("carBrandSearchFilterBtn")?.setAttribute("aria-expanded", "false")
}

private fun updateCarBrandSearchFilterMenuActive(selected: String) {
    val pairs = listOf(
        "all" to "carBrandSearchOptAll",
        "chassis" to "carBrandSearchOptChassis",
        "brand" to "carBrandSearchOptBrand",
        "carName" to "carBrandSearchOptCarName"
    )
    for ((value, id) in pairs) {
        val el = document.getElementById(id) as? HTMLElement ?: continue
        if (value == selected) el.classList.add("car-brand-search-filter-opt--active")
        else el.classList.remove("car-brand-search-filter-opt--active")
    }
}

private fun scheduleCarBrandSearchDebounced() {
    if (carBrandMapSearchDebounceTimer != null) {
        window.clearTimeout(carBrandMapSearchDebounceTimer.unsafeCast<Int>())
        carBrandMapSearchDebounceTimer = null
    }
    carBrandMapSearchDebounceTimer = window.setTimeout({
        carBrandMapSearchDebounceTimer = null
        runCarBrandSearchFromInput()
    }, 420)
}

private fun runCarBrandSearchFromInput() {
    val raw = getCarBrandSearchQuery()
    if (raw.isEmpty()) {
        carBrandMapSearchServerMode = false
        carBrandMapSearchTotal = 0
        carBrandMapSearchTotalPages = 0
        carBrandMapSearchPageZeroBased = 0
        carBrandsCurrentPage = 1
        loadMasterCarBrands()
        return
    }
    carBrandMapSearchPageZeroBased = 0
    carBrandsCurrentPage = 1
    loadMasterCarBrands()
}

fun setupCarBrandSearchBarListeners() {
    val input = document.getElementById("carBrandSearchInput") as? HTMLInputElement ?: return
    val filterBtn = document.getElementById("carBrandSearchFilterBtn") as? HTMLElement
    val menu = document.getElementById("carBrandSearchFilterMenu") as? HTMLElement
    val clearBtn = document.getElementById("carBrandSearchClearBtn") as? HTMLElement

    if (!input.hasAttribute("data-car-brand-search-bound")) {
        input.setAttribute("data-car-brand-search-bound", "true")
        input.addEventListener("input", { _: Event -> scheduleCarBrandSearchDebounced() })
        input.addEventListener("keydown", { ev: Event ->
            val kev = ev.asDynamic()
            if (kev.key == "Enter") {
                ev.preventDefault()
                if (carBrandMapSearchDebounceTimer != null) {
                    window.clearTimeout(carBrandMapSearchDebounceTimer.unsafeCast<Int>())
                    carBrandMapSearchDebounceTimer = null
                }
                runCarBrandSearchFromInput()
            }
        })
    }

    if (filterBtn != null && !filterBtn.hasAttribute("data-car-brand-search-bound")) {
        filterBtn.setAttribute("data-car-brand-search-bound", "true")
        filterBtn.addEventListener("click", { e: Event ->
            e.stopPropagation()
            if (menu == null) return@addEventListener
            val open = window.asDynamic().__carBrandSearchFilterMenuOpen == true
            window.asDynamic().__carBrandSearchFilterMenuOpen = !open
            val nowOpen = !open
            menu.style.setProperty("display", if (open) "none" else "block", "important")
            filterBtn.setAttribute("aria-expanded", if (nowOpen) "true" else "false")
        })
    }

    if (clearBtn != null && !clearBtn.hasAttribute("data-car-brand-search-bound")) {
        clearBtn.setAttribute("data-car-brand-search-bound", "true")
        clearBtn.addEventListener("click", { _: Event ->
            input.value = ""
            closeCarBrandSearchFilterMenu()
            runCarBrandSearchFromInput()
        })
    }

    val optIds = listOf(
        "carBrandSearchOptAll" to "all",
        "carBrandSearchOptChassis" to "chassis",
        "carBrandSearchOptBrand" to "brand",
        "carBrandSearchOptCarName" to "carName"
    )
    for ((id, value) in optIds) {
        val el = document.getElementById(id) as? HTMLElement
        if (el != null && !el.hasAttribute("data-car-brand-search-bound")) {
            el.setAttribute("data-car-brand-search-bound", "true")
            el.addEventListener("click", { _: Event ->
                carBrandMapSearchFieldChoice = value
                refreshCarBrandSearchScopeUi()
                updateCarBrandSearchFilterMenuActive(value)
                closeCarBrandSearchFilterMenu()
                val q = input.value.trim()
                if (q.isNotEmpty()) {
                    carBrandMapSearchPageZeroBased = 0
                    carBrandsCurrentPage = 1
                    loadMasterCarBrands()
                }
            })
        }
    }

    if (window.asDynamic().__carBrandSearchFilterOutsideAttached != true) {
        window.asDynamic().__carBrandSearchFilterOutsideAttached = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val m = document.getElementById("carBrandSearchFilterMenu") as? HTMLElement
            val b = document.getElementById("carBrandSearchFilterBtn") as? HTMLElement
            if (m == null) return@addEventListener
            val insideMenu = m.contains(target)
            val insideBtn = b != null && b.contains(target)
            if (!insideMenu && !insideBtn) {
                closeCarBrandSearchFilterMenu()
            }
        })
    }
}

fun showMasterCarBrandsPage() {
    showCarBrandsMapPage()
}

fun showCarBrandsMapPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="carBrandList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Chassis Map</h2>
                <div style="display: flex; align-items: center; gap: 10px;">
                    <button id="carBrandColumnFilterBtn" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/>
                        </svg>
                        Column Filter
                    </button>
                </div>
            </div>
            
            <!-- Search (same pattern as Purchase List) -->
            <style>
                #carBrandList .car-brand-search-filter-opt:hover { background: #f3f4f6 !important; }
                #carBrandList .car-brand-search-filter-opt--active { background: #eef2ff !important; font-weight: 600; }
                #carBrandList .car-brand-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
                #carBrandList #carBrandSearchFilterBtn:hover { background: #e8eaed !important; box-shadow: 0 2px 8px rgba(0,0,0,0.08) !important; }
                #carBrandList #carBrandSearchFilterBtn:focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
            </style>
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 16px; margin-bottom: 20px;">
                <div style="display: flex; align-items: center; gap: 10px; width: 100%; max-width: 720px;">
                    <div style="position: relative; flex: 1; display: flex; align-items: center; min-width: 0; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.06);">
                        <span style="position: absolute; left: 16px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #9ca3af; display: flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="text" id="carBrandSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Type to search…" aria-label="Search car brand map" style="width: 100%; box-sizing: border-box; padding: 12px 40px 12px 44px; border: none; font-size: 14px; background: transparent; border-radius: 999px; outline: none;" />
                        <button type="button" id="carBrandSearchClearBtn" title="Clear search" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: transparent; color: #9ca3af; cursor: pointer; font-size: 20px; line-height: 1; padding: 4px 8px; border-radius: 8px;">×</button>
                    </div>
                    <div style="position: relative; flex-shrink: 0;">
                        <span id="carBrandSearchFieldLabel" class="car-brand-sr-only" aria-live="polite">All fields</span>
                        <button type="button" id="carBrandSearchFilterBtn" title="Filter — search in: All fields" aria-haspopup="true" aria-expanded="false" aria-label="Open filter for which field to search. Current: All fields." style="width: 48px; height: 48px; border-radius: 50%; border: 1px solid #e5e7eb; background: #f3f4f6; box-shadow: 0 1px 3px rgba(0,0,0,0.06); cursor: pointer; display: flex; align-items: center; justify-content: center; color: #4b5563; padding: 0;">
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                <line x1="3" y1="7" x2="21" y2="7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="8" cy="7" r="2.25" fill="currentColor"/>
                                <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="16" cy="12" r="2.25" fill="currentColor"/>
                                <line x1="3" y1="17" x2="21" y2="17" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="7" cy="17" r="2.25" fill="currentColor"/>
                            </svg>
                        </button>
                        <div id="carBrandSearchFilterMenu" style="display: none; position: absolute; right: 0; top: calc(100% + 8px); z-index: 20001; min-width: 220px; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 10px 40px rgba(0,0,0,0.12); padding: 8px 0;">
                            <div style="padding: 8px 14px 4px; font-size: 11px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .04em;">Search in</div>
                            <button type="button" class="car-brand-search-filter-opt car-brand-search-filter-opt--active" id="carBrandSearchOptAll" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">All fields</button>
                            <button type="button" class="car-brand-search-filter-opt" id="carBrandSearchOptChassis" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Chassis</button>
                            <button type="button" class="car-brand-search-filter-opt" id="carBrandSearchOptBrand" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Car brand</button>
                            <button type="button" class="car-brand-search-filter-opt" id="carBrandSearchOptCarName" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Car name</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <!-- Action Buttons -->
            <div style="margin-bottom: 20px;">
                <button id="addCarBrandBtn" style="padding: 12px 24px; background-color: #059669; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">
                    ➕ Add New Car Brand
                </button>
            </div>
            
            <!-- Car Brand Table/Cards Container -->
            <div id="carBrandTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading car brand data...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    
    carBrandMapSearchFieldChoice = "all"
    carBrandMapSearchServerMode = false
    carBrandMapSearchTotal = 0
    carBrandMapSearchTotalPages = 0
    carBrandMapSearchPageZeroBased = 0
    carBrandsCurrentPage = 1
    updateCarBrandSearchFilterMenuActive("all")
    refreshCarBrandSearchScopeUi()
    setupCarBrandSearchBarListeners()
    
    // Load initial data
    loadMasterCarBrands()
    
    // Event listeners
    document.getElementById("addCarBrandBtn")?.addEventListener("click", { _: Event ->
        showAddCarBrandModal()
    })
    
    // Column filter button
    document.getElementById("carBrandColumnFilterBtn")?.addEventListener("click", { _: Event ->
        showCarBrandColumnFilterModal()
    })
    
    // Setup device change listener for Car Brands page
    setupCarBrandDeviceChangeListener()
    
    // Check for device change and reload if needed
    checkCarBrandDeviceChange()
}

/**
 * Check if device type changed for Car Brands page and reload if needed
 */
fun checkCarBrandDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, reload car brands to switch between card/table views
    if (lastCarBrandDeviceType != null && lastCarBrandDeviceType != currentDeviceType) {
        Logger.debug("Car Brands page: Device type changed from $lastCarBrandDeviceType to $currentDeviceType, reloading car brands")
        loadMasterCarBrands()
    }
    
    // Update last device type
    lastCarBrandDeviceType = currentDeviceType
}

/**
 * Setup window resize listener for Car Brands page to detect device changes
 */
fun setupCarBrandDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__carBrandDeviceChangeListener
    if (existingListener != null) {
        val listenerFunc = existingListener.unsafeCast<((Event) -> Unit)?>()
        window.removeEventListener("resize", listenerFunc)
    }
    
    // Debounce resize events
    var resizeTimeout: dynamic = null
    val resizeListener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) {
            window.clearTimeout(resizeTimeout)
        }
        resizeTimeout = window.setTimeout({
            // Check if device type actually changed
            val newDeviceType = getDeviceType()
            if (lastCarBrandDeviceType != null && lastCarBrandDeviceType != newDeviceType) {
                // Device changed - reload car brands to switch between card/table views
                Logger.debug("Car Brands page: Device type changed from $lastCarBrandDeviceType to $newDeviceType, reloading")
                
                // If we're on the car brands page, reload to show correct view (cards or table)
                if (window.location.hash.contains("#/master/car-brands")) {
                    loadMasterCarBrands()
                }
            }
            lastCarBrandDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__carBrandDeviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadMasterCarBrands() {
    val tableDiv = document.getElementById("carBrandTable")
    if (tableDiv == null) return
    
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        loadMasterCarBrandsWithCards()
        return
    }
    
    loadMasterCarBrandsWithTable()
}

fun loadMasterCarBrandsWithCards() {
    val tableDiv = document.getElementById("carBrandTable") as? HTMLElement ?: return
    
    val searchQ = getCarBrandSearchQuery()
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading car brand data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    if (searchQ.isNotEmpty()) {
        carBrandMapSearchServerMode = true
        val encQ = js("encodeURIComponent")(searchQ).unsafeCast<String>()
        val encF = js("encodeURIComponent")(carBrandMapSearchFieldChoice).unsafeCast<String>()
        val p = carBrandMapSearchPageZeroBased
        val url = apiUrl("car-brand-mapping/mappings/page-search?q=$encQ&field=$encF&page=$p&size=$carBrandsItemsPerPage")
        window.fetch(url)
        .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Search failed')")
            }
            .then { body: dynamic ->
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) throw js("Error(err)")
                val totalEl = js("body.totalElements")
                carBrandMapSearchTotal = when (totalEl) {
                    is Number -> totalEl.toLong()
                    else -> totalEl?.toString()?.toLongOrNull() ?: 0L
                }
                val tp = js("body.totalPages")
                carBrandMapSearchTotalPages = kotlin.math.max(1, when (tp) {
                    is Number -> tp.toInt()
                    else -> tp?.toString()?.toIntOrNull() ?: 1
                })
                val num = js("body.page")
                carBrandMapSearchPageZeroBased = when (num) {
                    is Number -> num.toInt()
                    else -> num?.toString()?.toIntOrNull() ?: 0
                }
                carBrandsCurrentPage = carBrandMapSearchPageZeroBased + 1

                val content = js("body.content") ?: js("[]")
                val mappingsArray = js("Array.isArray(content) ? content : []") as Array<dynamic>
                val groupedMappings = groupCarBrandMappingsForView(mappingsArray.toList())
                allCarBrands = groupedMappings
                displayCarBrandsAsCards(groupedMappings, searchQ, true)
        }
        .catch { error: dynamic ->
                Logger.error("Error searching car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brand data</div>
                        <div style="font-size: 14px; color: #9ca3af;">${error.asDynamic().message}</div>
                </div>
            """
        }
        return
    }

    carBrandMapSearchServerMode = false
    carBrandMapSearchTotal = 0
    carBrandMapSearchTotalPages = 0
    carBrandMapSearchPageZeroBased = 0

    window.fetch(apiUrl("car-brand-mapping/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brands')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load car brands')")
            }
            
            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>
            val mappingsList = mappingsArray.toList()
            val sortedMappings = mappingsList.sortedByDescending { mapping ->
                val id = mapping.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            val groupedMappings = groupCarBrandMappingsForView(sortedMappings)
            val carBrandSortable = setOf("chassis", "carBrand", "carName", "fuel", "vehicleType")
            var orderedForDisplay = groupedMappings
            val cbsf = carBrandMapSortField
            if (cbsf != null && cbsf in carBrandSortable) {
                val ord = carBrandMapSortOrderByField[cbsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    groupedMappings.sortedBy { extractCarBrandSortKey(it, cbsf) }
            } else {
                    groupedMappings.sortedByDescending { extractCarBrandSortKey(it, cbsf) }
                }
            }

            allCarBrands = orderedForDisplay
            displayCarBrandsAsCards(orderedForDisplay, "", false)
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brand data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

/**
 * Renders Car Brands Map table + pagination. [isServerSearch] uses [carBrandMapSearchPageZeroBased] / totals from API.
 */
private fun buildCarBrandTableUi(
    tableDiv: HTMLElement,
    paginatedMappings: List<dynamic>,
    orderedForDisplay: List<dynamic>,
    filterLabel: String,
    totalPages: Int,
    isServerSearch: Boolean,
    footerStart: Int,
    footerEnd: Int,
) {
            if (orderedForDisplay.isEmpty()) {
                val message = if (filterLabel.isNotEmpty()) {
                    "No car brand data found for: $filterLabel"
                } else {
                    "No car brand data found."
                }
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search or filter</div>
                    </div>
                """
                return
            }
            
            if (paginatedMappings.isEmpty()) {
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">No rows on this page.</div>
                    </div>
                """
                return
            }
            
            // Get selected columns
            val selectedColumns = getSelectedCarBrandColumns()
            val columnLabels = mapOf(
                "chassis" to "Chassis",
                "carBrand" to "Car Brand",
                "carName" to "Car Name",
                "fuel" to "Fuel",
                "wd" to "WD",
                "shift" to "Shift",
                "grade" to "Grade",
                "cc" to "CC",
                "seat" to "Seat",
                "door" to "Door",
                "vehicleType" to "Vehicle type",
                "rank" to "Rank",
                "color" to "Color",
                "driveType" to "Drive Type",
                "recycleFee" to "Recycle Fees"
            )
            
            val carBrandColCount = 1 + selectedColumns.size
            var html = """
                <div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);">
                    <table style="width: 100%; border-collapse: collapse; table-layout: fixed;" class="car-brand-table">${htmlTableColgroupNarrowActionEqualRest(carBrandColCount)}
                        <thead>
                            <tr>
                                <th style="padding: 12px 14px; text-align: left; min-width: 72px;"></th>
            """
            
            // Add headers for selected columns only (sortable columns match Car Brands Map spec)
            val carBrandSortableCols = setOf("chassis", "carBrand", "carName", "fuel", "vehicleType")
            for (columnKey in selectedColumns) {
                val label = columnLabels[columnKey] ?: columnKey
                val thBase = """padding: 12px 16px; text-align: left; font-weight: 700; color: #111827; font-size: 13px; letter-spacing: 0.02em"""
                html += if (columnKey in carBrandSortableCols) {
                    val tip = masterMapColumnSortTooltip(carBrandMapSortOrderByField, columnKey)
                    val bid = "carBrandMapSort_$columnKey"
                    """<th style="$thBase"><button type="button" id="$bid" title="$tip" style="background: none; border: none; cursor: pointer; font-weight: 700; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;"><span>$label</span><span style="font-size: 14px;">↕</span></button></th>"""
                } else {
                    """<th style="$thBase">$label</th>"""
                }
            }
            
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            
            for (mapping in paginatedMappings) {
                val id = (mapping.id ?: "").toString()
                val carBrand = (mapping.carBrand ?: "").toString()
                val chassis = (mapping.chassis ?: "").toString()
                val carName = (mapping.carName ?: "").toString()
                val fuel = (mapping.fuel ?: "").toString()
                val wd = (mapping.wd ?: "").toString()
                val shift = (mapping.shift ?: "").toString()
                val grade = (mapping.grade ?: "").toString()
                val cc = (mapping.cc ?: "").toString()
                val seat = (mapping.seat ?: "").toString()
                val door = (mapping.door ?: "").toString()
                val vehicleType = (mapping.vehicleType ?: "").toString()
                val rankVal = (mapping.rank ?: "").toString()
                val colorVal = (mapping.color ?: "").toString()
                val driveTypeVal = (mapping.driveType ?: "").toString()
                val recycleFeeVal = (mapping.recycleFee ?: "").toString()

                val rowDataJs =
                    "window.__carBrandRowData={chassis:'${escapeJsString(chassis)}',carBrand:'${escapeJsString(carBrand)}',carName:'${escapeJsString(carName)}',fuel:'${escapeJsString(fuel)}',wd:'${escapeJsString(wd)}',shift:'${escapeJsString(shift)}',grade:'${escapeJsString(grade)}',cc:'${escapeJsString(cc)}',seat:'${escapeJsString(seat)}',door:'${escapeJsString(door)}',vehicleType:'${escapeJsString(vehicleType)}',rank:'${escapeJsString(rankVal)}',color:'${escapeJsString(colorVal)}',driveType:'${escapeJsString(driveTypeVal)}',recycleFee:'${escapeJsString(recycleFeeVal)}'};"
                
                html += """
                    <tr>
                        <td style="padding: 10px 12px;">
                            <div style="display:flex; gap:6px; align-items:center;">
                            <button onclick="$rowDataJs window.editMasterCarBrand($id)" aria-label="Edit" title="Edit"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                </svg>
                            </button>
                                <button onclick="$rowDataJs window.duplicateMasterCarBrand($id)" aria-label="Duplicate" title="Duplicate"
                                        style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#3b82f6; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(59,130,246,0.30);">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
                                    </svg>
                                </button>
                            </div>
                        </td>
                """
                
                // Add cells for selected columns only
                for (columnKey in selectedColumns) {
                    val value = when (columnKey) {
                        "carBrand" -> carBrand
                        "chassis" -> chassis
                        "carName" -> carName
                        "fuel" -> fuel
                        "wd" -> wd
                        "shift" -> shift
                        "grade" -> grade
                        "cc" -> cc
                        "seat" -> seat
                        "door" -> door
                        "vehicleType" -> vehicleType
                        "rank" -> rankVal
                        "color" -> colorVal
                        "driveType" -> driveTypeVal
                        "recycleFee" -> recycleFeeVal
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                        "carBrand" -> "padding: 12px 16px; color: #111827; font-size: 14px; font-weight: 500; vertical-align: top;"
                        "chassis", "carName" -> "padding: 12px 16px; color: #111827; font-size: 14px; vertical-align: top;"
                        else -> "padding: 12px 16px; color: #374151; font-size: 14px; vertical-align: top;"
                    }
                    val cellInner = if (columnKey == "recycleFee") {
                        formatNumericValueChipHtml(value)
                    } else {
                        formatCarBrandMapValueChipHtml(value)
                    }
                    html += """<td style="$cellStyle">$cellInner</td>"""
                }
                
                html += """</tr>"""
            }
            
            html += """
                        </tbody>
                    </table>
                </div>
            """
            
            val footerSummary = if (isServerSearch) {
                "Page $carBrandsCurrentPage of $totalPages (search) · ${carBrandMapSearchTotal} matching row(s) · ${paginatedMappings.size} chassis group(s) on this page"
            } else {
                "Showing $footerStart to $footerEnd of ${orderedForDisplay.size} chassis group${if (orderedForDisplay.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"
            }

            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            $footerSummary
                        </div>
                        <div class="car-brand-pagination-controls">
                            <button id="carBrandsPrevPage" class="car-brand-pagination-btn" ${if (carBrandsCurrentPage == 1) "disabled" else ""}>
                                Previous
                            </button>
                            <span class="car-brand-pagination-page">Page $carBrandsCurrentPage of $totalPages</span>
                            <button id="carBrandsNextPage" class="car-brand-pagination-btn" ${if (carBrandsCurrentPage >= totalPages) "disabled" else ""}>
                                Next
                            </button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        ${if (isServerSearch) footerSummary else "Total: ${orderedForDisplay.size} chassis group${if (orderedForDisplay.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"}
                    </div>
                """
            }
            
            tableDiv.innerHTML = html
            
            document.getElementById("carBrandsPrevPage")?.addEventListener("click", { _: Event ->
                if (carBrandMapSearchServerMode) {
                    if (carBrandMapSearchPageZeroBased > 0) {
                        carBrandMapSearchPageZeroBased--
                        carBrandsCurrentPage = carBrandMapSearchPageZeroBased + 1
                        loadMasterCarBrands()
                    }
                } else if (carBrandsCurrentPage > 1) {
                    carBrandsCurrentPage--
                    loadMasterCarBrands()
                }
            })
            
            document.getElementById("carBrandsNextPage")?.addEventListener("click", { _: Event ->
                if (carBrandMapSearchServerMode) {
                    if (carBrandMapSearchPageZeroBased < carBrandMapSearchTotalPages - 1) {
                        carBrandMapSearchPageZeroBased++
                        carBrandsCurrentPage = carBrandMapSearchPageZeroBased + 1
                        loadMasterCarBrands()
                    }
                } else {
                    val tp = kotlin.math.ceil(allCarBrands.size.toDouble() / carBrandsItemsPerPage).toInt()
                    if (carBrandsCurrentPage < tp) {
                    carBrandsCurrentPage++
                    loadMasterCarBrands()
                    }
                }
            })
            val sortKeys = listOf("chassis", "carBrand", "carName", "fuel", "vehicleType")
            for (key in sortKeys) {
                if (key in selectedColumns) {
                    document.getElementById("carBrandMapSort_$key")?.addEventListener("click", { _: Event ->
                        toggleCarBrandMapSort(key)
                    })
                }
            }
}

fun loadMasterCarBrandsWithTable() {
    val tableDiv = document.getElementById("carBrandTable") as? HTMLElement ?: return

    val searchQ = getCarBrandSearchQuery()

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading car brand data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    if (searchQ.isNotEmpty()) {
        carBrandMapSearchServerMode = true
        val encQ = js("encodeURIComponent")(searchQ).unsafeCast<String>()
        val encF = js("encodeURIComponent")(carBrandMapSearchFieldChoice).unsafeCast<String>()
        val p = carBrandMapSearchPageZeroBased
        val url = apiUrl("car-brand-mapping/mappings/page-search?q=$encQ&field=$encF&page=$p&size=$carBrandsItemsPerPage")
        window.fetch(url)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Search failed')")
            }
            .then { body: dynamic ->
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) {
                    throw js("Error(err)")
                }
                val totalEl = js("body.totalElements")
                carBrandMapSearchTotal = when (totalEl) {
                    is Number -> totalEl.toLong()
                    else -> totalEl?.toString()?.toLongOrNull() ?: 0L
                }
                val tp = js("body.totalPages")
                carBrandMapSearchTotalPages = kotlin.math.max(1, when (tp) {
                    is Number -> tp.toInt()
                    else -> tp?.toString()?.toIntOrNull() ?: 1
                })
                val num = js("body.page")
                carBrandMapSearchPageZeroBased = when (num) {
                    is Number -> num.toInt()
                    else -> num?.toString()?.toIntOrNull() ?: 0
                }
                carBrandsCurrentPage = carBrandMapSearchPageZeroBased + 1

                val content = js("body.content") ?: js("[]")
                val mappingsArray = js("Array.isArray(content) ? content : []") as Array<dynamic>
                val mappingsList = mappingsArray.toList()
                val groupedMappings = groupCarBrandMappingsForView(mappingsList)
                val orderedForDisplay = groupedMappings
                allCarBrands = orderedForDisplay

                if (orderedForDisplay.isEmpty()) {
                    tableDiv.innerHTML = """
                        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                            <div style="font-size: 16px; margin-bottom: 8px;">No car brand data found for: $searchQ</div>
                            <div style="font-size: 14px; color: #9ca3af;">Try a different search</div>
                        </div>
                    """
                    return@then
                }

                val paginatedMappings = orderedForDisplay
                val totalPages = carBrandMapSearchTotalPages
                buildCarBrandTableUi(
                    tableDiv,
                    paginatedMappings,
                    orderedForDisplay,
                    searchQ,
                    totalPages,
                    true,
                    1,
                    paginatedMappings.size
                )
            }
            .catch { error: dynamic ->
                Logger.error("Error searching car brands: ${error.toString()}")
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brand data</div>
                        <div style="font-size: 14px; color: #9ca3af;">${error.asDynamic().message}</div>
                    </div>
                """
            }
        return
    }

    carBrandMapSearchServerMode = false
    carBrandMapSearchTotal = 0
    carBrandMapSearchTotalPages = 0
    carBrandMapSearchPageZeroBased = 0

    window.fetch(apiUrl("car-brand-mapping/mappings"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brands')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load car brands')")
            }

            val mappings = result.data ?: js("[]")
            val mappingsArray = js("Array.isArray(mappings) ? mappings : []") as Array<dynamic>

            val mappingsList = mappingsArray.toList()
            val sortedMappings = mappingsList.sortedByDescending { mapping ->
                val id = mapping.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }

            val groupedMappings = groupCarBrandMappingsForView(sortedMappings)
            val carBrandSortable = setOf("chassis", "carBrand", "carName", "fuel", "vehicleType")
            var orderedForDisplay = groupedMappings
            val cbsf = carBrandMapSortField
            if (cbsf != null && cbsf in carBrandSortable) {
                val ord = carBrandMapSortOrderByField[cbsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    groupedMappings.sortedBy { extractCarBrandSortKey(it, cbsf) }
                } else {
                    groupedMappings.sortedByDescending { extractCarBrandSortKey(it, cbsf) }
                }
            }

            allCarBrands = orderedForDisplay

            if (orderedForDisplay.isEmpty()) {
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">No car brand data found.</div>
                    </div>
                """
                return@then
            }

            val totalPages = kotlin.math.max(1, kotlin.math.ceil(orderedForDisplay.size.toDouble() / carBrandsItemsPerPage).toInt())
            val startIndex = (carBrandsCurrentPage - 1) * carBrandsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + carBrandsItemsPerPage, orderedForDisplay.size)
            val paginatedMappings = orderedForDisplay.subList(startIndex, endIndex)
            val footerStart = startIndex + 1
            val footerEnd = endIndex

            buildCarBrandTableUi(
                tableDiv,
                paginatedMappings,
                orderedForDisplay,
                "",
                totalPages,
                false,
                footerStart,
                footerEnd
            )
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brands: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading car brand data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun displayCarBrandsAsCards(filteredMappings: List<dynamic>, brandFilter: String, isServerSearch: Boolean = false) {
    val tableDiv = document.getElementById("carBrandTable")
    if (tableDiv == null) return
    
    if (filteredMappings.isEmpty()) {
        val message = if (brandFilter.isNotEmpty()) {
            "No car brand data found for: $brandFilter"
        } else {
            "No car brand data found."
        }
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                $message
            </div>
        """
        return
    }
    
    val totalPages = if (isServerSearch) {
        kotlin.math.max(1, carBrandMapSearchTotalPages)
    } else {
        kotlin.math.max(1, kotlin.math.ceil(filteredMappings.size.toDouble() / carBrandsItemsPerPage).toInt())
    }
    val paginatedMappings = if (isServerSearch) {
        filteredMappings
    } else {
    val startIndex = (carBrandsCurrentPage - 1) * carBrandsItemsPerPage
    val endIndex = kotlin.math.min(startIndex + carBrandsItemsPerPage, filteredMappings.size)
        filteredMappings.subList(startIndex, endIndex)
    }
    
    val selectedColumns = getSelectedCarBrandColumns()
    val columnLabels = mapOf(
        "chassis" to "Chassis",
        "carBrand" to "Car Brand",
        "carName" to "Car Name",
        "fuel" to "Fuel",
        "wd" to "WD",
        "shift" to "Shift",
        "grade" to "Grade",
        "cc" to "CC",
        "seat" to "Seat",
        "door" to "Door",
        "vehicleType" to "Vehicle type",
        "rank" to "Rank",
        "color" to "Color",
        "driveType" to "Drive Type",
        "recycleFee" to "Recycle Fees"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="car-brand-cards-container">""")
    
    for (mapping in paginatedMappings) {
        val id = (mapping.id ?: "").toString()
        val carBrand = (mapping.carBrand ?: "").toString()
        val chassis = (mapping.chassis ?: "").toString()
        val carName = (mapping.carName ?: "").toString()
        val fuel = (mapping.fuel ?: "").toString()
        val wd = (mapping.wd ?: "").toString()
        val shift = (mapping.shift ?: "").toString()
        val grade = (mapping.grade ?: "").toString()
        val cc = (mapping.cc ?: "").toString()
        val seat = (mapping.seat ?: "").toString()
        val door = (mapping.door ?: "").toString()
        val vehicleType = (mapping.vehicleType ?: "").toString()
        val rankVal = (mapping.rank ?: "").toString()
        val colorVal = (mapping.color ?: "").toString()
        val driveTypeVal = (mapping.driveType ?: "").toString()
        val recycleFeeVal = (mapping.recycleFee ?: "").toString()

        val rowDataJs =
            "window.__carBrandRowData={chassis:'${escapeJsString(chassis)}',carBrand:'${escapeJsString(carBrand)}',carName:'${escapeJsString(carName)}',fuel:'${escapeJsString(fuel)}',wd:'${escapeJsString(wd)}',shift:'${escapeJsString(shift)}',grade:'${escapeJsString(grade)}',cc:'${escapeJsString(cc)}',seat:'${escapeJsString(seat)}',door:'${escapeJsString(door)}',vehicleType:'${escapeJsString(vehicleType)}',rank:'${escapeJsString(rankVal)}',color:'${escapeJsString(colorVal)}',driveType:'${escapeJsString(driveTypeVal)}',recycleFee:'${escapeJsString(recycleFeeVal)}'};"
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "carBrand" -> carBrand
                "chassis" -> chassis
                "carName" -> carName
                "fuel" -> fuel
                "wd" -> wd
                "shift" -> shift
                "grade" -> grade
                "cc" -> cc
                "seat" -> seat
                "door" -> door
                "vehicleType" -> vehicleType
                "rank" -> rankVal
                "color" -> colorVal
                "driveType" -> driveTypeVal
                "recycleFee" -> recycleFeeVal
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                val displayValue = if (columnKey == "recycleFee") {
                    formatNumericValueChipHtml(value)
                } else {
                    formatCarBrandMapValueChipHtml(value)
                }
                cardFields.append("""
                    <div style="margin-bottom: 8px;">
                        <span style="font-weight: 600; color: #666; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">$label:</span>
                        <div style="color: #333; font-size: 14px; margin-top: 2px;">$displayValue</div>
                    </div>
                """)
            }
        }
        
        cardsHTML.append("""
            <div class="car-brand-card">
                <div class="card-header">
                    <div style="display:flex; gap:6px; align-items:center;">
                    <button class="card-edit-btn" onclick="$rowDataJs window.editMasterCarBrand($id)" aria-label="Edit" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                        <button class="card-edit-btn" onclick="$rowDataJs window.duplicateMasterCarBrand($id)" aria-label="Duplicate" title="Duplicate" style="background-color:#3b82f6;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
                            </svg>
                        </button>
                    </div>
                    <div class="card-title">${if (carBrand.isNotEmpty()) carBrand else "Car Brand #$id"}</div>
                </div>
                <div class="card-body">
                    $cardFields
                </div>
            </div>
        """)
    }
    
    cardsHTML.append("</div>")
    
    // Add pagination controls
    if (totalPages > 1) {
        cardsHTML.append("""
            <div class="pagination-controls">
                <button id="carBrandsPrevPage" class="pagination-btn" ${if (carBrandsCurrentPage == 1) "disabled" else ""}>
                    Previous
                </button>
                <span class="pagination-page">Page $carBrandsCurrentPage of $totalPages</span>
                <button id="carBrandsNextPage" class="pagination-btn" ${if (carBrandsCurrentPage >= totalPages) "disabled" else ""}>
                    Next
                </button>
            </div>
        """)
    } else {
        val summary = if (isServerSearch) {
            "Page $carBrandsCurrentPage of $totalPages (search) · ${carBrandMapSearchTotal} matching row(s) · ${paginatedMappings.size} group(s) on this page"
        } else {
            "Total: ${filteredMappings.size} chassis group${if (filteredMappings.size != 1) "s" else ""}${if (brandFilter.isNotEmpty()) " (filtered)" else ""}"
        }
        cardsHTML.append("""
            <div style="padding: 16px; text-align: center; color: #6b7280; font-size: 14px;">
                $summary
            </div>
        """)
    }
    
    tableDiv.innerHTML = cardsHTML.toString()
    
    document.getElementById("carBrandsPrevPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (carBrandMapSearchPageZeroBased > 0) {
                carBrandMapSearchPageZeroBased--
                carBrandsCurrentPage = carBrandMapSearchPageZeroBased + 1
                loadMasterCarBrands()
            }
        } else if (carBrandsCurrentPage > 1) {
            carBrandsCurrentPage--
            loadMasterCarBrands()
        }
    })
    
    document.getElementById("carBrandsNextPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (carBrandMapSearchPageZeroBased < carBrandMapSearchTotalPages - 1) {
                carBrandMapSearchPageZeroBased++
                carBrandsCurrentPage = carBrandMapSearchPageZeroBased + 1
                loadMasterCarBrands()
            }
        } else {
            val tp = kotlin.math.ceil(allCarBrands.size.toDouble() / carBrandsItemsPerPage).toInt()
            if (carBrandsCurrentPage < tp) {
            carBrandsCurrentPage++
            loadMasterCarBrands()
            }
        }
    })
}

fun showCarBrandColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("carBrandColumnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "carBrandColumnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; align-items: center; justify-content: center;
    """
    
    // Get current device type and limits
    val deviceType = getDeviceType()
    val maxColumns = getMaxCarBrandMapColumnsForDevice(deviceType)
    val deviceDisplayName = when (deviceType) {
        "mobile" -> "Mobile View"
        "tablet" -> "Tablet View"
        else -> "Desktop View"
    }
    
    val selectedColumnsList = getSelectedCarBrandColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 520px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeCarBrandColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="carBrandSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="carBrandColumnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetCarBrandColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyCarBrandColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes
    val columnLabels = mapOf(
        "chassis" to "Chassis",
        "carBrand" to "Car Brand",
        "carName" to "Car Name",
        "fuel" to "Fuel",
        "wd" to "WD",
        "shift" to "Shift",
        "grade" to "Grade",
        "cc" to "CC",
        "seat" to "Seat",
        "door" to "Door",
        "vehicleType" to "Vehicle type",
        "rank" to "Rank",
        "color" to "Color",
        "driveType" to "Drive Type",
        "recycleFee" to "Recycle Fees"
    )
    
    val checkboxesDiv = document.getElementById("carBrandColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val checkbox = document.createElement("div")
        val checkboxStyle = checkbox.asDynamic().style
        checkboxStyle.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "carBrandCol_$key"
        input.setAttribute("data-column", key)
        input.checked = selectedColumns.contains(key)
        input.addEventListener("change", { _: Event ->
            updateCarBrandColumnSelection()
        })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "carBrandCol_$key"
        labelEl.textContent = label
        val labelStyle = labelEl.asDynamic().style
        labelStyle.cssText = "cursor: pointer; margin: 0;"
        checkbox.appendChild(input)
        checkbox.appendChild(labelEl)
        checkboxesDiv?.appendChild(checkbox)
    }
    
    // Update selection count initially
    updateCarBrandColumnSelection()
    
    // Add event listeners
    document.getElementById("closeCarBrandColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("carBrandColumnFilterModal")?.remove()
    })
    document.getElementById("resetCarBrandColumns")?.addEventListener("click", { _: Event ->
        val deviceType = getDeviceType()
        val defaultColumns = getDefaultCarBrandColumnsForDevice(deviceType)
        columnLabels.keys.forEach { col ->
            val checkbox = document.getElementById("carBrandCol_$col") as? HTMLInputElement
            checkbox?.checked = defaultColumns.contains(col)
        }
        updateCarBrandColumnSelection()
    })
    document.getElementById("applyCarBrandColumns")?.addEventListener("click", { _: Event ->
        applyCarBrandColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "carBrandColumnFilterModal") {
            document.getElementById("carBrandColumnFilterModal")?.remove()
        }
    })
}

fun updateCarBrandColumnSelection() {
    val deviceType = getDeviceType()
    val maxColumns = getMaxCarBrandMapColumnsForDevice(deviceType)
    val checkboxes = document.querySelectorAll("#carBrandColumnCheckboxes input[type='checkbox']")
    var selectedCount = 0
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedCount++
        }
    }
    
    val countSpan = document.getElementById("carBrandSelectedCount")
    countSpan?.textContent = "$selectedCount"
    
    // Disable/enable checkboxes based on max limit
    if (selectedCount >= maxColumns) {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            if (!checkbox.checked) {
                checkbox.disabled = true
            }
        }
    } else {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.disabled = false
        }
    }
}

fun applyCarBrandColumnChanges() {
    val checkboxes = document.querySelectorAll("#carBrandColumnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableListOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val columnKey = checkbox.getAttribute("data-column") ?: ""
            if (columnKey.isNotEmpty()) {
                selectedColumns.add(columnKey)
            }
        }
    }
    
    // Save to localStorage
    safeLocalStorageSet("selectedCarBrandColumns", JSON.stringify(selectedColumns.toTypedArray()))
    
    // Close modal
    document.getElementById("carBrandColumnFilterModal")?.remove()
    
    // Reload car brands to apply changes
    loadMasterCarBrands()
}

fun showAddCarBrandModal() {
    showCarBrandModal(null)
}

fun showCarBrandModal(mappingId: Long?, duplicateFromId: Long? = null) {
    val isDuplicate = duplicateFromId != null
    val isEdit = mappingId != null && !isDuplicate
    val title = when {
        isDuplicate -> "Duplicate Car Brand"
        isEdit -> "Edit Car Brand"
        else -> "Add New Car Brand"
    }
    
    val modalHtml = """
        <div id="carBrandModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div id="carBrandModalContent" style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeCarBrandModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="carBrandForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Chassis <span style="color: #ef4444;">*</span></label>
                            <input type="text" id="carBrandChassis" required style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Car Brand <span style="color: #ef4444;">*</span></label>
                            ${createChipMultiSelectCombobox("carBrandBrand", "Select Car Brand")}
                        </div>
                        <div class="car-brand-modal-grid car-brand-modal-grid--car-name-row">
                            <div class="car-brand-modal-grid__car-name">
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Car Name</label>
                                ${createChipInput("carBrandCarName", "Type Car Name", CAR_BRAND_CAR_NAME_MAX_LEN)}
                            </div>
                            <div class="car-brand-modal-grid__vehicle-type">
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Vehicle type</label>
                                ${createChipMultiSelectCombobox("carBrandVehicleType", "Select Vehicle type")}
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Fuel</label>
                                ${createChipMultiSelectCombobox("carBrandFuel", "Select Fuel")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">WD</label>
                                ${createChipMultiSelectCombobox("carBrandWd", "Select WD")}
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Shift</label>
                                ${createChipMultiSelectCombobox("carBrandShift", "Select Shift")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Grade</label>
                                ${createChipInput("carBrandGrade", "Type Grade")}
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">CC</label>
                                ${createChipInput("carBrandCc", "Type CC")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Seat</label>
                                ${createChipInput("carBrandSeat", "Type Seat")}
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Door</label>
                                ${createChipInput("carBrandDoor", "Type Door")}
                            </div>
                            <div style="visibility:hidden;"></div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Rank</label>
                                ${createChipInput("carBrandRank", "Type Rank")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Color</label>
                                ${createChipInput("carBrandColor", "Type Color")}
                            </div>
                        </div>
                        <div class="car-brand-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Drive Type (LHD/RHD)</label>
                                ${createChipMultiSelectCombobox("carBrandDriveType", "Select Drive Type")}
                            </div>
                            <div style="visibility:hidden;"></div>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Recycle Fees</label>
                            <input type="text" id="carBrandRecycleFee" class="money-input" inputmode="decimal" autocomplete="off" placeholder="e.g. 6550 (yen amount)"
                                   style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
                        </div>
                        <div class="car-brand-modal-actions">
                            <button type="button" id="cancelCarBrandBtn" class="car-brand-modal-btn car-brand-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteCarBrandBtn" class="car-brand-modal-btn car-brand-modal-btn-delete">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveCarBrandBtn" class="car-brand-modal-btn car-brand-modal-btn-save">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)
    ensureSupplierChipJs()
    populateCarBrandModalComboboxes()

    if (!isEdit && !isDuplicate) {
        js("window.__carBrandRowData = null")
    }
    
    // Load data if editing or duplicating (grouped row prefill matches table; else fetch single mapping)
    if (isDuplicate && duplicateFromId != null) {
        val hasGrouped = js("window.__carBrandRowData != null") as Boolean
        if (hasGrouped) {
            tryPrefillCarBrandModalFromGroupedRow()
            (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value = ""
        } else {
            loadCarBrandDataForEdit(duplicateFromId, clearChassisForDuplicate = true)
        }
    } else if (isEdit && mappingId != null) {
        val hasGrouped = js("window.__carBrandRowData != null") as Boolean
        if (hasGrouped) {
            tryPrefillCarBrandModalFromGroupedRow()
        } else {
        loadCarBrandDataForEdit(mappingId)
        }
    }
    
    // Event listeners
    document.getElementById("closeCarBrandModal")?.addEventListener("click", { _: Event ->
        closeCarBrandModal()
    })
    
    document.getElementById("cancelCarBrandBtn")?.addEventListener("click", { _: Event ->
        closeCarBrandModal()
    })
    
    // Delete button (only shown in edit mode)
    if (isEdit && mappingId != null) {
        document.getElementById("deleteCarBrandBtn")?.addEventListener("click", { _: Event ->
            if (js("confirm('Are you sure you want to delete this car brand mapping? This action cannot be undone.')").unsafeCast<Boolean>()) {
                deleteMasterCarBrand(mappingId)
            }
        })
    }
    
    document.getElementById("carBrandForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        // Duplicate always creates a new row (POST); edit updates existing (PUT)
        val saveId = if (isDuplicate) null else mappingId
        val replaceExistingValues = isEdit || isDuplicate
        saveCarBrand(saveId, replaceExistingValues)
    })
    
    // Close on background click
    document.getElementById("carBrandModal")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "carBrandModal") {
            closeCarBrandModal()
        }
    })
}

fun closeCarBrandModal() {
    document.getElementById("carBrandModal")?.remove()
}

fun loadCarBrandDataForEdit(mappingId: Long, clearChassisForDuplicate: Boolean = false) {
    window.fetch(apiUrl("car-brand-mapping/mappings/$mappingId"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load car brand data')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                val data = result.data ?: js("{}")
                (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value =
                    if (clearChassisForDuplicate) "" else (data.chassis ?: "").toString()
                setChipFieldValue("carBrandBrand", (data.carBrand ?: "").toString())
                setChipFieldValue("carBrandCarName", (data.carName ?: "").toString())
                setChipFieldValue("carBrandFuel", (data.fuel ?: "").toString())
                setChipFieldValue("carBrandWd", (data.wd ?: "").toString())
                setChipFieldValue("carBrandShift", (data.shift ?: "").toString())
                setChipFieldValue("carBrandCc", (data.cc ?: "").toString())
                setChipFieldValue("carBrandSeat", (data.seat ?: "").toString())
                setChipFieldValue("carBrandDoor", (data.door ?: "").toString())
                setChipFieldValue("carBrandGrade", (data.grade ?: "").toString())
                setChipFieldValue("carBrandVehicleType", (data.vehicleType ?: "").toString())
                setChipFieldValue("carBrandRank", (data.rank ?: "").toString())
                setChipFieldValue("carBrandColor", (data.color ?: "").toString())
                setChipFieldValue("carBrandDriveType", (data.driveType ?: "").toString())
                val recycleFeeInput = document.getElementById("carBrandRecycleFee") as? HTMLInputElement
                if (recycleFeeInput != null) {
                    val rawValue = (data.recycleFee ?: "").toString()
                    recycleFeeInput.value = rawValue
                    // Format it using the global money formatter if available
                    window.setTimeout({
                        if (js("typeof window._moneyFormat === 'function'").unsafeCast<Boolean>()) {
                            recycleFeeInput.value = js("window._moneyFormat(recycleFeeInput.value)").unsafeCast<String>()
                        }
                    }, 0)
                }
            } else {
                throw js("Error(result.message || 'Failed to load car brand data')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading car brand data: ${error.toString()}")
            showMessage("Error loading car brand data: ${error.message}", "error")
        }
}

fun saveCarBrand(mappingId: Long?, replaceExistingValues: Boolean = false) {
    val chassis = (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value?.trim() ?: ""
    val carBrand = getChipFieldValue("carBrandBrand")

    if (chassis.isEmpty()) {
        showMessage("Chassis is required", "error")
        return
    }
    
    if (carBrand.isEmpty()) {
        showMessage("Car Brand is required", "error")
        return
    }
    
    val fuel = getChipFieldValue("carBrandFuel")
    val shift = getChipFieldValue("carBrandShift")
    val grade = getChipFieldValue("carBrandGrade")
    
    val saveButton = document.getElementById("saveCarBrandBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = "Validating..."
    
    // Validate supported fields against master lists before saving (Grade is intentionally excluded)
    validateCarBrandMasterFields(carBrand, fuel, grade, shift) { missingFields ->
        if (missingFields.isNotEmpty()) {
            // Close car brand modal and show error modal
            closeCarBrandModal()
            showCarBrandMasterFieldsErrorModal(missingFields)
        } else {
            // All fields are valid, proceed with save
            performCarBrandSave(mappingId, replaceExistingValues)
        }
    }
}

@Suppress("UNUSED_PARAMETER")
fun validateCarBrandMasterFields(
    carBrand: String,
    fuel: String,
    grade: String,
    shift: String,
    callback: (List<Pair<String, String>>) -> Unit
) {
    // Master-menu membership checks removed — always allow save.
            callback(emptyList())
}

fun showCarBrandMasterFieldsErrorModal(missingFields: List<Pair<String, String>>) {
    document.getElementById("carBrandMasterFieldsErrorModal")?.remove()
    
    // Build the error message
    val fieldNames = missingFields.map { it.first }.joinToString(", ")
    val pageNames = missingFields.map { it.second }.distinct().joinToString(", ")
    
    val modal = document.createElement("div")
    modal.id = "carBrandMasterFieldsErrorModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10001;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #ef4444;">Field(s) Not Found in Master List</h3>
            <p style="margin-bottom: 20px; color: #374151; font-size: 14px; line-height: 1.6;">
                <strong>$fieldNames</strong> does not exist in Master List. Go to the <strong>$pageNames</strong> page(s) and add the missing value(s).
            </p>
            <div style="display: flex; justify-content: flex-end;">
                <button id="closeCarBrandMasterFieldsErrorModalBtn" style="padding: 10px 24px; border-radius: 6px; border: none; background: #3b82f6; color: white; cursor: pointer; font-size: 14px; font-weight: 500;">
                    Close
                </button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    
    document.getElementById("closeCarBrandMasterFieldsErrorModalBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })
}

fun performCarBrandSave(mappingId: Long?, replaceExistingValues: Boolean = false) {
    // Full multi-brand support: chips are deduped and joined with ";" (do not use parseFirstToken — it dropped extra brands)
    val carBrand = splitSemicolonDistinctTokens(getChipFieldValue("carBrandBrand")).joinToString(";")
    
    val carBrandData = js("{}")
    carBrandData.carBrand = carBrand
    carBrandData.chassis = (document.getElementById("carBrandChassis") as? HTMLInputElement)?.value?.trim() ?: null
    carBrandData.carName = getChipFieldValue("carBrandCarName").takeIf { it.isNotEmpty() }?.take(CAR_BRAND_CAR_NAME_MAX_LEN) ?: null
    carBrandData.fuel = getChipFieldValue("carBrandFuel").takeIf { it.isNotEmpty() } ?: null
    carBrandData.wd = getChipFieldValue("carBrandWd").takeIf { it.isNotEmpty() } ?: null
    carBrandData.shift = getChipFieldValue("carBrandShift").takeIf { it.isNotEmpty() } ?: null
    carBrandData.grade = getChipFieldValue("carBrandGrade").takeIf { it.isNotEmpty() } ?: null
    carBrandData.cc = getChipFieldValue("carBrandCc").takeIf { it.isNotEmpty() } ?: null
    carBrandData.seat = getChipFieldValue("carBrandSeat").takeIf { it.isNotEmpty() } ?: null
    carBrandData.door = getChipFieldValue("carBrandDoor").takeIf { it.isNotEmpty() } ?: null
    carBrandData.vehicleType = getChipFieldValue("carBrandVehicleType").takeIf { it.isNotEmpty() } ?: null
    carBrandData.rank = getChipFieldValue("carBrandRank").takeIf { it.isNotEmpty() } ?: null
    carBrandData.color = getChipFieldValue("carBrandColor").takeIf { it.isNotEmpty() } ?: null
    carBrandData.driveType = getChipFieldValue("carBrandDriveType").takeIf { it.isNotEmpty() } ?: null
    carBrandData.recycleFee = js("window.getMoneyRawValue ? window.getMoneyRawValue('carBrandRecycleFee') : ''").unsafeCast<String>()
        .trim().takeIf { it.isNotEmpty() } ?: null
    carBrandData.replaceExistingValues = replaceExistingValues
    
    val saveButton = document.getElementById("saveCarBrandBtn") as? HTMLButtonElement
    saveButton?.disabled = true
    saveButton?.textContent = if (mappingId != null) "Updating..." else "Saving..."
    
    val url = if (mappingId != null) {
        apiUrl("car-brand-mapping/mappings/$mappingId")
    } else {
        apiUrl("car-brand-mapping/mappings")
    }
    
    val method = if (mappingId != null) "PUT" else "POST"
    
    val requestInit = js("{}")
    requestInit.method = method
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers
    requestInit.body = JSON.stringify(carBrandData)
    
    Logger.debug("[CAR BRAND] Sending $method request to: $url")
    Logger.debug("[CAR BRAND] Request body: ${requestInit.body}")
    
    window.fetch(url, requestInit)
        .then { response: dynamic ->
            Logger.debug("[CAR BRAND] Response status: ${response.status}")
            if (response.ok) {
                response.json().then { result: dynamic ->
                        Logger.debug("[CAR BRAND] Processing result")
                    val success = result.success as? Boolean ?: false
                    if (success) {
                        showMessage(if (mappingId != null) "Car brand updated successfully" else "Car brand added successfully", "success")
                        closeCarBrandModal()
                        loadMasterCarBrands()
                        
                        // Trigger localStorage event to notify other tabs to refresh chassis dropdown
                        // This allows the chassis dropdown on Add/Edit Purchase pages to auto-update
                        val chassisValue = carBrandData.chassis as? String
                        if (chassisValue != null && chassisValue.isNotBlank()) {
                            try {
                                val timestamp = js("Date.now()").toString()
                                safeLocalStorageSet("chassisUpdated", timestamp)
                                // Also trigger a custom event for same-tab communication
                                val event = js("new CustomEvent('chassisUpdated', { detail: { chassis: chassisValue, timestamp: timestamp } })")
                                window.dispatchEvent(event)
                                Logger.debug("✅ Triggered chassis update event for chassis: $chassisValue")
                            } catch (e: dynamic) {
                                Logger.warn("⚠️ Failed to trigger chassis update event: ${e.toString()}")
                            }
                        }
                        
                        // Trigger localStorage event to notify other tabs to refresh brand dropdown
                        // This allows the brand dropdown on Add/Edit Purchase pages to auto-update
                        try {
                            val timestamp = js("Date.now()").toString()
                            safeLocalStorageSet("brandUpdated", timestamp)
                            // Also trigger a custom event for same-tab communication
                            val brandEvent = js("new CustomEvent('brandUpdated', { detail: { brand: carBrand, timestamp: timestamp } })")
                            window.dispatchEvent(brandEvent)
                            Logger.debug("✅ Triggered brand update event for brand: $carBrand")
                        } catch (e: dynamic) {
                            Logger.warn("⚠️ Failed to trigger brand update event: ${e.toString()}")
                        }
                    } else {
                        val errorMsg = (result.message as? String) ?: "Failed to save car brand"
                        Logger.error("[CAR BRAND] Save failed: $errorMsg")
                        showMessage("Error saving car brand: $errorMsg", "error")
                    }
                }
            } else {
                response.text().then { errorText: String ->
                    Logger.error("[CAR BRAND] Error response: $errorText")
                    showMessage("Error saving car brand: $errorText", "error")
                }
            }
        }
        .catch { error: dynamic ->
            Logger.error("[CAR BRAND] Error saving car brand: ${error.toString()}")
            showMessage("Error saving car brand: ${error.message}", "error")
        }
        .finally {
            saveButton?.disabled = false
            saveButton?.textContent = if (mappingId != null) "Update Car Brand" else "Save Car Brand"
        }
}

fun deleteMasterCarBrand(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId == null) {
        showMessage("Invalid car brand ID", "error")
        return
    }
    
    // Confirmation is already checked in the event listener, so no need to check again here
    
    val requestInit = js("{}")
    requestInit.method = "DELETE"
    
    window.fetch(apiUrl("car-brand-mapping/mappings/$mappingId"), requestInit)
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to delete car brand')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                showMessage("Car brand deleted successfully", "success")
                closeCarBrandModal()
                loadMasterCarBrands()
                
                // Trigger localStorage event to notify other tabs to refresh chassis dropdown
                try {
                    val timestamp = js("Date.now()").toString()
                    safeLocalStorageSet("chassisUpdated", timestamp)
                    // Also trigger a custom event for same-tab communication
                    val event = js("new CustomEvent('chassisUpdated', { detail: { timestamp: timestamp } })")
                    window.dispatchEvent(event)
                    Logger.debug("✅ Triggered chassis update event after deletion")
                } catch (e: dynamic) {
                    Logger.warn("⚠️ Failed to trigger chassis update event: ${e.toString()}")
                }
                
                // Trigger localStorage event to notify other tabs to refresh brand dropdown
                try {
                    val timestamp = js("Date.now()").toString()
                    safeLocalStorageSet("brandUpdated", timestamp)
                    // Also trigger a custom event for same-tab communication
                    val brandEvent = js("new CustomEvent('brandUpdated', { detail: { timestamp: timestamp } })")
                    window.dispatchEvent(brandEvent)
                    Logger.debug("✅ Triggered brand update event after deletion")
                } catch (e: dynamic) {
                    Logger.warn("⚠️ Failed to trigger brand update event: ${e.toString()}")
                }
            } else {
                throw js("Error(result.message || 'Failed to delete car brand')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error deleting car brand: ${error.toString()}")
            showMessage("Error deleting car brand: ${error.message}", "error")
        }
}

fun editMasterCarBrand(id: dynamic) {
    val mappingId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (mappingId != null) {
        showCarBrandModal(mappingId)
    }
}

fun duplicateMasterCarBrand(id: dynamic) {
    val sourceId = (id as? Number)?.toLong() ?: id.toString().toLongOrNull()
    if (sourceId != null) {
        showCarBrandModal(mappingId = null, duplicateFromId = sourceId)
    }
}

// Placeholder functions for other master list pages
fun showMasterCountriesPage() {
    window.location.hash = "#/master/set/country"
    showDynamicMasterSetPage("country")
}

fun loadMasterCountries() {
    val tableDiv = document.getElementById("countryTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("countryFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading countries...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    window.fetch(apiUrl("master-menu/country"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load countries')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allCountries = filtered
            if (searchFilter.isNotEmpty()) countriesCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No countries found for: $searchFilter" else "No countries found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val selectedColumns = getSelectedCountryColumns()
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / countriesItemsPerPage).toInt()
            val startIndex = (countriesCurrentPage - 1) * countriesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + countriesItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            val columnLabels = mapOf("id" to "ID", "country" to "Country")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="country-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
            """
            for (col in selectedColumns) {
                val label = columnLabels[col] ?: col
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, countryName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="country-edit-btn"
                                        data-country="${countryName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "country" -> countryName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "country" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                html += """</tr>"""
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} countr${if (filtered.size != 1) "ies" else "y"}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="countriesPrevPage" class="consignee-pagination-btn" ${if (countriesCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $countriesCurrentPage of $totalPages</span>
                            <button id="countriesNextPage" class="consignee-pagination-btn" ${if (countriesCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} countr${if (filtered.size != 1) "ies" else "y"}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".country-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-country") ?: return@addEventListener
                    showEditCountryModal(name)
                })
            }
            
            document.getElementById("countriesPrevPage")?.addEventListener("click", { _: Event ->
                if (countriesCurrentPage > 1) {
                    countriesCurrentPage--
                    loadMasterCountries()
                }
            })
            document.getElementById("countriesNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allCountries.size.toDouble() / countriesItemsPerPage).toInt()
                if (countriesCurrentPage < totalP) {
                    countriesCurrentPage++
                    loadMasterCountries()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading countries: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading countries</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showCountryColumnFilterModal() {
    document.getElementById("countryColumnFilterModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "countryColumnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val selectedColumnsList = getSelectedCountryColumns()
    val selectedSet = selectedColumnsList.toSet()
    val columnLabels = mapOf("id" to "ID", "country" to "Country")
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h3 style="margin: 0; color: #333;">Select Columns to Display</h3>
                <button id="closeCountryColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666;">&times;</button>
            </div>
            <div id="countryColumnCheckboxes" style="display: grid; gap: 12px; margin-bottom: 20px;"></div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetCountryColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applyCountryColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    val checkboxesDiv = document.getElementById("countryColumnCheckboxes")!!
    columnLabels.forEach { (key, label) ->
        val div = document.createElement("div")
        div.asDynamic().style.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "countryCol_$key"
        input.setAttribute("data-column", key)
        input.checked = key in selectedSet
        div.appendChild(input)
        val lbl = document.createElement("label")
        lbl.setAttribute("for", "countryCol_$key")
        lbl.textContent = label
        div.appendChild(lbl)
        checkboxesDiv.appendChild(div)
    }
    document.getElementById("closeCountryColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("countryColumnFilterModal")?.remove()
    })
    document.getElementById("resetCountryColumns")?.addEventListener("click", { _: Event ->
        safeLocalStorageSet("selectedCountryColumns", JSON.stringify(getDefaultCountryColumnsForDevice(getDeviceType()).toTypedArray()))
        document.getElementById("countryColumnFilterModal")?.remove()
        loadMasterCountries()
    })
    document.getElementById("applyCountryColumns")?.addEventListener("click", { _: Event ->
        val inputs = document.querySelectorAll("#countryColumnCheckboxes input[type=checkbox]:checked")
        val selected = (0 until inputs.length).map { (inputs.item(it) as HTMLInputElement).getAttribute("data-column") ?: "" }.filter { it.isNotEmpty() }
        if (selected.isNotEmpty()) {
            safeLocalStorageSet("selectedCountryColumns", JSON.stringify(selected.toTypedArray()))
        }
        document.getElementById("countryColumnFilterModal")?.remove()
        loadMasterCountries()
    })
}

fun editMasterCountry(countryName: dynamic) {
    val name = countryName?.toString() ?: return
    showEditCountryModal(name)
}

fun showAddCountryModal() {
    document.getElementById("countryEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "countryEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Country</h3>
            <div style="margin-bottom: 16px;">
                <label for="countryModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Country</label>
                <input type="text" id="countryModalInput" placeholder="Enter country name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="countryModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="countryModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("countryModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("countryModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("countryModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Country name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/country"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add country')")
            }
            .then { _: dynamic ->
                showMessage("Country added successfully", "success")
                modal.remove()
                countriesCurrentPage = 1
                loadMasterCountries()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding country: ${error.toString()}")
                showMessage("Error adding country: ${error.message}", "error")
            }
    })
}

fun showEditCountryModal(originalName: String) {
    document.getElementById("countryEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "countryEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Country</h3>
            <div style="margin-bottom: 16px;">
                <label for="countryModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Country</label>
                <input type="text" id="countryModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="countryModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="countryModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="countryModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("countryModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("countryModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("countryModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Country name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/country"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update country')")
            }
            .then { _: dynamic ->
                showMessage("Country updated successfully", "success")
                modal.remove()
                loadMasterCountries()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating country: ${error.toString()}")
                showMessage("Error updating country: ${error.message}", "error")
            }
    })

    document.getElementById("countryModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete country '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/country?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete country')")
            }
            .then { _: dynamic ->
                showMessage("Country deleted successfully", "success")
                modal.remove()
                countriesCurrentPage = 1
                loadMasterCountries()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting country: ${error.toString()}")
                showMessage("Error deleting country: ${error.message}", "error")
            }
    })
}

/** Supplier page: shows a supplier master list (from master_menu). */
fun showSupplierPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="supplierMasterList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Supplier</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addSupplierMasterBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Supplier</span>
                    </button>
                </div>
            </div>

            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Supplier:</label>
                    <input type="text" id="supplierMasterFilter" placeholder="Type supplier name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>

            <div id="supplierMasterTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading suppliers...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadSupplierMasterList()

    document.getElementById("supplierMasterFilter")?.addEventListener("input", { _: Event ->
        loadSupplierMasterList()
    })

    document.getElementById("addSupplierMasterBtn")?.addEventListener("click", { _: Event ->
        showAddSupplierMasterModal()
    })
}

fun loadSupplierMasterList() {
    val tableDiv = document.getElementById("supplierMasterTable")
    if (tableDiv == null) return

    val searchFilter = (document.getElementById("supplierMasterFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading suppliers...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/supplier"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load suppliers')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allSupplierMaster = filtered
            if (searchFilter.isNotEmpty()) supplierMasterCurrentPage = 1

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No suppliers found for: $searchFilter" else "No suppliers found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val selectedColumns = listOf("id", "supplier")
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / supplierMasterItemsPerPage).toInt()
            val startIndex = (supplierMasterCurrentPage - 1) * supplierMasterItemsPerPage
            val endIndex = kotlin.math.min(startIndex + supplierMasterItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            val columnLabels = mapOf("id" to "ID", "supplier" to "Supplier")
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="supplier-master-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
            """
            for (col in selectedColumns) {
                val label = columnLabels[col] ?: col
                html += """<th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">$label</th>"""
            }
            html += """
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, supplierName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                """
                for (col in selectedColumns) {
                    val value = when (col) {
                        "id" -> """
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="supplier-master-edit-btn"
                                        data-supplier="${supplierName.replace("\"", "&quot;")}"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        """.trimIndent()
                        "supplier" -> supplierName
                        else -> ""
                    }
                    val cellStyle = when (col) {
                        "id" -> "padding: 14px 16px; color: #6b7280; font-size: 14px;"
                        "supplier" -> "padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;"
                        else -> "padding: 14px 16px; color: #111827; font-size: 14px;"
                    }
                    html += """<td style="$cellStyle">$value</td>"""
                }
                html += """</tr>"""
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} supplier${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="supplierMasterPrevPage" class="consignee-pagination-btn" ${if (supplierMasterCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $supplierMasterCurrentPage of $totalPages</span>
                            <button id="supplierMasterNextPage" class="consignee-pagination-btn" ${if (supplierMasterCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} supplier${if (filtered.size != 1) "s" else ""}${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            // Attach edit handlers
            val editButtons = document.querySelectorAll(".supplier-master-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-supplier") ?: return@addEventListener
                    showEditSupplierMasterModal(name)
                })
            }
            
            document.getElementById("supplierMasterPrevPage")?.addEventListener("click", { _: Event ->
                if (supplierMasterCurrentPage > 1) {
                    supplierMasterCurrentPage--
                    loadSupplierMasterList()
                }
            })
            document.getElementById("supplierMasterNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allSupplierMaster.size.toDouble() / supplierMasterItemsPerPage).toInt()
                if (supplierMasterCurrentPage < totalP) {
                    supplierMasterCurrentPage++
                    loadSupplierMasterList()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading suppliers: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading suppliers</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddSupplierMasterModal() {
    document.getElementById("supplierMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "supplierMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Supplier</h3>
            <div style="margin-bottom: 16px;">
                <label for="supplierMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Supplier</label>
                <input type="text" id="supplierMasterModalInput" placeholder="Enter supplier name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="supplierMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="supplierMasterModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("supplierMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("supplierMasterModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("supplierMasterModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Supplier name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/supplier"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add supplier')")
            }
            .then { _: dynamic ->
                showMessage("Supplier added successfully", "success")
                modal.remove()
                supplierMasterCurrentPage = 1
                loadSupplierMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding supplier: ${error.toString()}")
                showMessage("Error adding supplier: ${error.message}", "error")
            }
    })
}

fun showEditSupplierMasterModal(originalName: String) {
    document.getElementById("supplierMasterEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "supplierMasterEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Supplier</h3>
            <div style="margin-bottom: 16px;">
                <label for="supplierMasterModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Supplier</label>
                <input type="text" id="supplierMasterModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="supplierMasterModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="supplierMasterModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="supplierMasterModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("supplierMasterModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("supplierMasterModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("supplierMasterModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Supplier name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/supplier"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update supplier')")
            }
            .then { _: dynamic ->
                showMessage("Supplier updated successfully", "success")
                modal.remove()
                loadSupplierMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating supplier: ${error.toString()}")
                showMessage("Error updating supplier: ${error.message}", "error")
            }
    })

    document.getElementById("supplierMasterModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete supplier '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/supplier?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete supplier')")
            }
            .then { _: dynamic ->
                showMessage("Supplier deleted successfully", "success")
                modal.remove()
                supplierMasterCurrentPage = 1
                loadSupplierMasterList()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting supplier: ${error.toString()}")
                showMessage("Error deleting supplier: ${error.message}", "error")
            }
    })
}

fun showMasterSuppliersPage() {
    showSupplierMapPage()
}

private fun getSupplierMapSearchQuery(): String =
    (document.getElementById("supplierMapSearchInput") as? HTMLInputElement)?.value?.trim() ?: ""

private fun supplierMapSearchFieldDisplayLabel(): String = when (supplierMapSearchFieldChoice) {
    "supplierName" -> "Supplier name"
    "stockLocation" -> "Stock location"
    "rixoCompany" -> "Rixo company"
    else -> "All fields"
}

private fun refreshSupplierMapSearchScopeUi() {
    val scopeLabel = supplierMapSearchFieldDisplayLabel()
    val btn = document.getElementById("supplierMapSearchFilterBtn") as? HTMLElement
    if (btn != null) {
        btn.setAttribute("title", "Filter — search in: $scopeLabel")
        btn.setAttribute("aria-label", "Open filter for which field to search. Current: $scopeLabel.")
    }
    val sr = document.getElementById("supplierMapSearchFieldLabel") as? HTMLElement
    if (sr != null) sr.textContent = scopeLabel
}

private fun closeSupplierMapSearchFilterMenu() {
    val menu = document.getElementById("supplierMapSearchFilterMenu") as? HTMLElement
    menu?.style?.setProperty("display", "none", "important")
    window.asDynamic().__supplierMapSearchFilterMenuOpen = false
    document.getElementById("supplierMapSearchFilterBtn")?.setAttribute("aria-expanded", "false")
}

private fun updateSupplierMapSearchFilterMenuActive(selected: String) {
    val pairs = listOf(
        "all" to "supplierMapSearchOptAll",
        "supplierName" to "supplierMapSearchOptSupplierName",
        "stockLocation" to "supplierMapSearchOptStockLocation",
        "rixoCompany" to "supplierMapSearchOptRixoCompany"
    )
    for ((value, id) in pairs) {
        val el = document.getElementById(id) as? HTMLElement ?: continue
        if (value == selected) el.classList.add("supplier-map-search-filter-opt--active")
        else el.classList.remove("supplier-map-search-filter-opt--active")
    }
}

private fun scheduleSupplierMapSearchDebounced() {
    if (supplierMapSearchDebounceTimer != null) {
        window.clearTimeout(supplierMapSearchDebounceTimer.unsafeCast<Int>())
        supplierMapSearchDebounceTimer = null
    }
    supplierMapSearchDebounceTimer = window.setTimeout({
        supplierMapSearchDebounceTimer = null
        runSupplierMapSearchFromInput()
    }, 420)
}

private fun runSupplierMapSearchFromInput() {
    val raw = getSupplierMapSearchQuery()
    if (raw.isEmpty()) {
        supplierMapSearchServerMode = false
        supplierMapSearchTotal = 0
        supplierMapSearchTotalPages = 0
        supplierMapSearchPageZeroBased = 0
        suppliersCurrentPage = 1
        loadMasterSuppliers()
        return
    }
    supplierMapSearchPageZeroBased = 0
    suppliersCurrentPage = 1
    loadMasterSuppliers()
}

fun setupSupplierMapSearchBarListeners() {
    val input = document.getElementById("supplierMapSearchInput") as? HTMLInputElement ?: return
    val filterBtn = document.getElementById("supplierMapSearchFilterBtn") as? HTMLElement
    val menu = document.getElementById("supplierMapSearchFilterMenu") as? HTMLElement
    val clearBtn = document.getElementById("supplierMapSearchClearBtn") as? HTMLElement

    if (!input.hasAttribute("data-supplier-map-search-bound")) {
        input.setAttribute("data-supplier-map-search-bound", "true")
        input.addEventListener("input", { _: Event -> scheduleSupplierMapSearchDebounced() })
        input.addEventListener("keydown", { ev: Event ->
            val kev = ev.asDynamic()
            if (kev.key == "Enter") {
                ev.preventDefault()
                if (supplierMapSearchDebounceTimer != null) {
                    window.clearTimeout(supplierMapSearchDebounceTimer.unsafeCast<Int>())
                    supplierMapSearchDebounceTimer = null
                }
                runSupplierMapSearchFromInput()
            }
        })
    }

    if (filterBtn != null && !filterBtn.hasAttribute("data-supplier-map-search-bound")) {
        filterBtn.setAttribute("data-supplier-map-search-bound", "true")
        filterBtn.addEventListener("click", { e: Event ->
            e.stopPropagation()
            if (menu == null) return@addEventListener
            val open = window.asDynamic().__supplierMapSearchFilterMenuOpen == true
            window.asDynamic().__supplierMapSearchFilterMenuOpen = !open
            val nowOpen = !open
            menu.style.setProperty("display", if (open) "none" else "block", "important")
            filterBtn.setAttribute("aria-expanded", if (nowOpen) "true" else "false")
        })
    }

    if (clearBtn != null && !clearBtn.hasAttribute("data-supplier-map-search-bound")) {
        clearBtn.setAttribute("data-supplier-map-search-bound", "true")
        clearBtn.addEventListener("click", { _: Event ->
            input.value = ""
            closeSupplierMapSearchFilterMenu()
            runSupplierMapSearchFromInput()
        })
    }

    val optIds = listOf(
        "supplierMapSearchOptAll" to "all",
        "supplierMapSearchOptSupplierName" to "supplierName",
        "supplierMapSearchOptStockLocation" to "stockLocation",
        "supplierMapSearchOptRixoCompany" to "rixoCompany"
    )
    for ((id, value) in optIds) {
        val el = document.getElementById(id) as? HTMLElement
        if (el != null && !el.hasAttribute("data-supplier-map-search-bound")) {
            el.setAttribute("data-supplier-map-search-bound", "true")
            el.addEventListener("click", { _: Event ->
                supplierMapSearchFieldChoice = value
                refreshSupplierMapSearchScopeUi()
                updateSupplierMapSearchFilterMenuActive(value)
                closeSupplierMapSearchFilterMenu()
                val q = input.value.trim()
                if (q.isNotEmpty()) {
                    supplierMapSearchPageZeroBased = 0
                    suppliersCurrentPage = 1
                    loadMasterSuppliers()
                }
            })
        }
    }

    if (window.asDynamic().__supplierMapSearchFilterOutsideAttached != true) {
        window.asDynamic().__supplierMapSearchFilterOutsideAttached = true
        document.addEventListener("click", { event ->
            val target = event.target as? Node ?: return@addEventListener
            val m = document.getElementById("supplierMapSearchFilterMenu") as? HTMLElement
            val b = document.getElementById("supplierMapSearchFilterBtn") as? HTMLElement
            if (m == null) return@addEventListener
            val insideMenu = m.contains(target)
            val insideBtn = b != null && b.contains(target)
            if (!insideMenu && !insideBtn) {
                closeSupplierMapSearchFilterMenu()
            }
        })
    }
}

fun showSupplierMapPage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="supplierList" class="rixo-tree-page supplier-map-page">
            <div class="supplier-map-topbar">
                <h2 class="supplier-map-title">Supplier Map</h2>
            </div>
            
            <style>
                #supplierList .supplier-map-search-filter-opt:hover { background: #f3f4f6 !important; }
                #supplierList .supplier-map-search-filter-opt--active { background: #eef2ff !important; font-weight: 600; }
                #supplierList .supplier-map-sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0; }
                #supplierList #supplierMapSearchFilterBtn:hover { background: #e8eaed !important; box-shadow: 0 2px 8px rgba(0,0,0,0.08) !important; }
                #supplierList #supplierMapSearchFilterBtn:focus-visible { outline: 2px solid #3b82f6; outline-offset: 2px; }
            </style>
            <div class="supplier-map-search-panel">
                <div class="supplier-map-search-toolbar">
                    <div class="supplier-map-search-input-wrap" style="position: relative; flex: 1; display: flex; align-items: center; min-width: 0; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; box-shadow: 0 1px 3px rgba(0,0,0,0.06);">
                        <span style="position: absolute; left: 16px; top: 50%; transform: translateY(-50%); pointer-events: none; color: #9ca3af; display: flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="text" id="supplierMapSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Type to search…" aria-label="Search supplier map" style="width: 100%; box-sizing: border-box; padding: 12px 40px 12px 44px; border: none; font-size: 14px; background: transparent; border-radius: 999px; outline: none;" />
                        <button type="button" id="supplierMapSearchClearBtn" title="Clear search" style="position: absolute; right: 10px; top: 50%; transform: translateY(-50%); border: none; background: transparent; color: #9ca3af; cursor: pointer; font-size: 20px; line-height: 1; padding: 4px 8px; border-radius: 8px;">×</button>
                    </div>
                    <div class="supplier-map-search-filter-wrap" style="position: relative; flex-shrink: 0;">
                        <span id="supplierMapSearchFieldLabel" class="supplier-map-sr-only" aria-live="polite">All fields</span>
                        <button type="button" id="supplierMapSearchFilterBtn" title="Filter — search in: All fields" aria-haspopup="true" aria-expanded="false" aria-label="Open filter for which field to search. Current: All fields." style="width: 48px; height: 48px; border-radius: 50%; border: 1px solid #e5e7eb; background: #f3f4f6; box-shadow: 0 1px 3px rgba(0,0,0,0.06); cursor: pointer; display: flex; align-items: center; justify-content: center; color: #4b5563; padding: 0;">
                            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                                <line x1="3" y1="7" x2="21" y2="7" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="8" cy="7" r="2.25" fill="currentColor"/>
                                <line x1="3" y1="12" x2="21" y2="12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="16" cy="12" r="2.25" fill="currentColor"/>
                                <line x1="3" y1="17" x2="21" y2="17" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/>
                                <circle cx="7" cy="17" r="2.25" fill="currentColor"/>
                            </svg>
                        </button>
                        <div id="supplierMapSearchFilterMenu" style="display: none; position: absolute; right: 0; top: calc(100% + 8px); z-index: 20001; min-width: 220px; background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 10px 40px rgba(0,0,0,0.12); padding: 8px 0;">
                            <div style="padding: 8px 14px 4px; font-size: 11px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .04em;">Search in</div>
                            <button type="button" class="supplier-map-search-filter-opt supplier-map-search-filter-opt--active" id="supplierMapSearchOptAll" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">All fields</button>
                            <button type="button" class="supplier-map-search-filter-opt" id="supplierMapSearchOptSupplierName" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Supplier name</button>
                            <button type="button" class="supplier-map-search-filter-opt" id="supplierMapSearchOptStockLocation" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Stock location</button>
                            <button type="button" class="supplier-map-search-filter-opt" id="supplierMapSearchOptRixoCompany" style="display:block;width:100%;text-align:left;padding:10px 16px;border:none;background:#fff;cursor:pointer;font-size:14px;color:#111827;">Rixo company</button>
                        </div>
                    </div>
                </div>
            </div>
            
            <div id="supplierMapTreeRoot" class="rixo-tree-root supplier-map-tree supplier-map-tree-root">
                <div class="rixo-tree-loading">Loading supplier map…</div>
            </div>
        </div>
    """
    
    supplierMapSearchFieldChoice = "all"
    supplierMapSearchServerMode = false
    supplierMapSearchTotal = 0
    supplierMapSearchTotalPages = 0
    supplierMapSearchPageZeroBased = 0
    suppliersCurrentPage = 1
    updateSupplierMapSearchFilterMenuActive("all")
    refreshSupplierMapSearchScopeUi()
    setupSupplierMapSearchBarListeners()
    
    // Load initial data
    loadMasterSuppliers()
    
}

private fun supplierStockIsReal(st: String): Boolean {
    val t = st.trim()
    return t.isNotEmpty() && t != "-"
}

/** One visual branch under a supplier: token-split stock + aligned venue/POL + one or more Rixo leaves. */
private data class SupplierStockBranchView(
    val supplier: String,
    val stockToken: String,
    val venueToken: String,
    val polToken: String,
    val row: SupplierPriceRowLite,
    val stockBranchIndex: Int,
    /** Pairs of (index in `rixo_company` semicolon list, label). */
    val rixoLeaves: List<Pair<Int, String>>,
)

private fun splitSupplierSemicolonTokens(raw: String): List<String> =
    raw.split(';').map { it.trim() }

private fun joinSupplierSemicolonParts(parts: List<String>): String =
    parts.joinToString(";")

/** Strip grouping/currency so inputs like `15,000` or `¥60,000` parse as numbers. */
private fun parseMoneyNumericInput(raw: String): Double? {
    val t = raw.trim().replace(",", "").replace(Regex("[¥₩£€\\s]"), "").trim()
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()
}

private fun replaceSupplierSemicolonSegment(raw: String, branchIdx: Int, newSegment: String): String {
    val p = splitSupplierSemicolonTokens(raw).toMutableList()
    val nv = newSegment.trim()
    while (p.size <= branchIdx) p.add("")
    if (branchIdx < p.size) p[branchIdx] = nv else p.add(nv)
    return joinSupplierSemicolonParts(p)
}

private fun computeRixoLeavesForBranch(
    numStockBranches: Int,
    rixosAll: List<String>,
    stockBranchIdx: Int,
): List<Pair<Int, String>> {
    if (numStockBranches <= 1) {
        val leaves = mutableListOf<Pair<Int, String>>()
        for ((j, t) in rixosAll.withIndex()) {
            val tt = t.trim()
            if (tt.isEmpty() || tt == "-") continue
            leaves.add(j to tt)
        }
        return leaves.ifEmpty { listOf(0 to "") }
    }
    val tt = rixosAll.getOrNull(stockBranchIdx)?.trim().orEmpty()
    return listOf(stockBranchIdx to tt)
}

private fun expandStockBranchesForSupplier(rows: List<SupplierPriceRowLite>, supplier: String): List<SupplierStockBranchView> {
    val out = mutableListOf<SupplierStockBranchView>()
    for (row in rows.filter { it.supplier.equals(supplier, ignoreCase = true) }) {
        val stocksRaw = splitSupplierSemicolonTokens(row.stock)
        val stocks = stocksRaw.filter { it.isNotBlank() && it != "-" }
        val stockBranches = if (stocks.isEmpty()) listOf("-") else stocks
        val venues = splitSupplierSemicolonTokens(row.venueId)
        val pols = splitSupplierSemicolonTokens(row.pol)
        val rixosAll = splitSupplierSemicolonTokens(row.rixoCompany)
        val n = stockBranches.size
        for ((i, stTok) in stockBranches.withIndex()) {
            val venueTok = venues.getOrNull(i)?.trim().orEmpty()
            val polTok = pols.getOrNull(i)?.trim().orEmpty()
            val rixoLeaves = computeRixoLeavesForBranch(n, rixosAll, i)
            out.add(
                SupplierStockBranchView(
                    supplier = row.supplier,
                    stockToken = stTok,
                    venueToken = venueTok,
                    polToken = polTok,
                    row = row,
                    stockBranchIndex = i,
                    rixoLeaves = rixoLeaves,
                )
            )
        }
    }
    return out.sortedWith(
        compareBy(
            { it.stockToken.lowercase() },
            { it.row.id },
            { it.stockBranchIndex },
        ),
    )
}

private fun parseSupplierPriceRows(rows: List<dynamic>): List<SupplierPriceRowLite> {
    val out = mutableListOf<SupplierPriceRowLite>()
    for (r in rows) {
        val idAny = r.id ?: continue
        val id = when (idAny) {
            is Number -> idAny.toLong()
            else -> idAny.toString().toLongOrNull() ?: continue
        }
        val supplier = (r.auctionHouse ?: "").toString().trim()
        if (supplier.isEmpty()) continue
        out.add(
            SupplierPriceRowLite(
                id = id,
                supplier = supplier,
                stock = (r.stockLocation ?: "").toString().trim(),
                rixoCompany = (r.rixoCompany ?: "").toString().trim(),
                venueId = (r.venueId ?: "").toString().trim(),
                pol = (r.pol ?: "").toString().trim(),
            )
        )
    }
    return out
}

private fun supplierTreeRowsForSearch(): List<SupplierPriceRowLite> {
    val q = getSupplierMapSearchQuery().trim().lowercase()
    val all = supplierMapTreeRowsCache
    if (q.isEmpty()) return all
    val field = supplierMapSearchFieldChoice.lowercase().replace("_", "")
    return all.filter { row ->
        when (field) {
            "suppliername" -> row.supplier.lowercase().contains(q)
            "stocklocation" -> row.stock.lowercase().contains(q)
            "rixocompany" -> row.rixoCompany.lowercase().contains(q)
            else ->
                listOf(row.supplier, row.stock, row.rixoCompany, row.venueId, row.pol)
                    .any { it.lowercase().contains(q) }
        }
    }
}

private fun distinctSuppliersSorted(rows: List<SupplierPriceRowLite>): List<String> =
    rows.map { it.supplier }.distinct().sortedBy { it.lowercase() }

private fun supplierTreeAddButtonHtml(level: String, supplier: String?, stock: String?, rowId: Long?, branchIdx: Int?): String {
    val c = escapeHtml(supplier ?: "")
    val s = escapeHtml(stock ?: "")
    val rid = rowId?.toString() ?: ""
    val bidx = branchIdx?.toString() ?: ""
    return """<div class="rixo-tree-add-wrap"><button type="button" class="rixo-tree-add-btn" data-smap-add="$level" data-smap-supplier="$c" data-smap-stock="$s" data-smap-row-id="$rid" data-smap-branch-idx="$bidx">+ Add</button></div>"""
}

private fun buildSupplierTreeCardHtml(
    level: String,
    label: String,
    selected: Boolean,
    pathSupplier: String,
    pathStock: String,
    rowId: Long?,
    branchIdx: Int?,
    branchEditActive: Boolean = false,
): String {
    val selectedClass = if (selected) " rixo-tree-card--selected" else ""
    val levelClass = if (level == "supplier") " rixo-tree-card--company" else " rixo-tree-card--auction"
    val wrapperClass = if (level == "supplier") "rixo-tree-card-wrapper--company" else "rixo-tree-card-wrapper--auction"
    val ariaExpanded = if (selected) "true" else "false"
    val ps = escapeHtml(pathSupplier)
    val pst = escapeHtml(pathStock)
    val rid = rowId?.toString() ?: ""
    val bidx = branchIdx?.toString() ?: ""

    if (level == "supplier" && supplierTreeSupplierEditName == pathSupplier) {
        return """
            <div class="rixo-tree-card-wrapper $wrapperClass" data-smap-path-supplier="$ps" data-smap-path-stock="$pst" data-smap-card-level="$level" data-smap-row-id="$rid" data-smap-branch-idx="$bidx">
                <div class="rixo-tree-card$levelClass$selectedClass rixo-tree-card--inline-editing" data-smap-card-level="$level" aria-expanded="$ariaExpanded" style="cursor: default;">
                    <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                    <span class="rixo-tree-label-wrap" style="display:flex;flex-direction:row;align-items:center;min-width:0;text-align:left;width:100%;gap:10px;">
                        <input type="text" id="supTreeSupplierInlineEditInput" value="$ps" style="flex:1; padding: 4px 8px; border: 1px solid #d1d5db; border-radius: 4px; font-size: 14px; box-sizing: border-box;" />
                        <button type="button" class="rixo-tree-card-inline-cancel" data-smap-supplier-cancel="1">Cancel</button>
                        <button type="button" class="rixo-tree-card-inline-save" data-smap-supplier-save="1">Save</button>
                    </span>
                </div>
            </div>
        """.trimIndent()
    }

    if (level == "stock" && branchEditActive) {
        return """
            <div class="rixo-tree-card-wrapper $wrapperClass" data-smap-path-supplier="$ps" data-smap-path-stock="$pst" data-smap-card-level="$level" data-smap-row-id="$rid" data-smap-branch-idx="$bidx">
                <div class="rixo-tree-card$levelClass$selectedClass rixo-tree-card--inline-editing" data-smap-card-level="$level" aria-expanded="$ariaExpanded" style="cursor: default;">
                    <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                    <span class="rixo-tree-label-wrap" style="display:flex;flex-direction:column;align-items:flex-start;min-width:0;text-align:left;width:100%;">
                        ${createEditableCombobox("supTreeBranchEditStock", "Select Stock Location", required = true)}
                    </span>
                </div>
                <div class="rixo-tree-card-menu-wrap">
                    <button type="button" class="rixo-tree-card-menu-btn" aria-label="More actions" aria-haspopup="true">&#8942;</button>
                    <div class="rixo-tree-card-menu-panel" role="menu">
                        <button type="button" class="rixo-tree-card-menu-item" data-smap-menu="edit" role="menuitem">Edit…</button>
                        <button type="button" class="rixo-tree-card-menu-item rixo-tree-card-menu-item--danger" data-smap-menu="delete" role="menuitem">Delete</button>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    return """
        <div class="rixo-tree-card-wrapper $wrapperClass" data-smap-path-supplier="$ps" data-smap-path-stock="$pst" data-smap-card-level="$level" data-smap-row-id="$rid" data-smap-branch-idx="$bidx">
            <button type="button" class="rixo-tree-card$levelClass$selectedClass" data-smap-card-level="$level" data-smap-card-value="${escapeHtml(label)}" aria-expanded="$ariaExpanded">
                <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                <span class="rixo-tree-label-wrap" style="display:flex;flex-direction:column;align-items:flex-start;min-width:0;text-align:left;">
                    <span class="rixo-tree-label">${escapeHtml(label)}</span>
                </span>
            </button>
            <div class="rixo-tree-card-menu-wrap">
                <button type="button" class="rixo-tree-card-menu-btn" aria-label="More actions" aria-haspopup="true">&#8942;</button>
                <div class="rixo-tree-card-menu-panel" role="menu">
                    <button type="button" class="rixo-tree-card-menu-item" data-smap-menu="edit" role="menuitem">Edit…</button>
                    <button type="button" class="rixo-tree-card-menu-item rixo-tree-card-menu-item--danger" data-smap-menu="delete" role="menuitem">Delete</button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildSupplierVenueStripHtml(venue: String, rowId: Long, branchIdx: Int, branchEditing: Boolean = false): String {
    val editing = supplierTreeVenueEditRowId == rowId && supplierTreeVenueEditBranchIdx == branchIdx
    val rid = rowId.toString()
    val bi = branchIdx.toString()
    if (branchEditing) {
        return """
            <div class="supplier-tree-venue-strip supplier-tree-pol-strip--editing" data-smap-venue-row="$rid" data-smap-venue-idx="$bi">
                <span class="supplier-tree-pol-label">Venue ID</span>
                <div class="supplier-tree-pol-combo">${createEditableCombobox("supTreeBranchEditVenue", "Select Venue ID")}</div>
            </div>
        """.trimIndent()
    }
    if (editing) {
        return """
            <div class="supplier-tree-venue-strip supplier-tree-pol-strip--editing" data-smap-venue-row="$rid" data-smap-venue-idx="$bi">
                <span class="supplier-tree-pol-label">Venue ID</span>
                <div class="supplier-tree-pol-combo">${createEditableCombobox("supTreeVenueEditCombo", "Select Venue ID")}</div>
                <button type="button" class="rixo-tree-card-inline-cancel" data-smap-venue-cancel="1">Cancel</button>
                <button type="button" class="rixo-tree-card-inline-save" data-smap-venue-save="1">Save</button>
            </div>
        """.trimIndent()
    }
    return """
        <div class="supplier-tree-venue-strip supplier-tree-pol-strip" data-smap-venue-row="$rid" data-smap-venue-idx="$bi">
            <span class="supplier-tree-pol-label">Venue ID</span>
            <span class="supplier-tree-pol-value">${escapeHtml(venue.ifBlank { "—" })}</span>
            <button type="button" class="supplier-tree-pol-edit-btn" data-smap-venue-edit="1" title="Edit venue">✎</button>
        </div>
    """.trimIndent()
}

private fun buildSupplierPolStripHtml(rowId: Long, branchIdx: Int, pol: String, branchEditing: Boolean = false): String {
    val editing = supplierTreePolEditRowId == rowId && supplierTreePolEditBranchIdx == branchIdx
    val rid = rowId.toString()
    val bi = branchIdx.toString()
    if (branchEditing) {
        return """
            <div class="supplier-tree-pol-strip supplier-tree-pol-strip--editing" data-smap-pol-row="$rid" data-smap-pol-idx="$bi">
                <span class="supplier-tree-pol-label">POL</span>
                <div class="supplier-tree-pol-combo">${createEditableCombobox("supTreeBranchEditPol", "Select POL")}</div>
            </div>
        """.trimIndent()
    }
    if (editing) {
        return """
            <div class="supplier-tree-pol-strip supplier-tree-pol-strip--editing" data-smap-pol-row="$rid" data-smap-pol-idx="$bi">
                <span class="supplier-tree-pol-label">POL</span>
                <div class="supplier-tree-pol-combo">${createEditableCombobox("supTreePolEditCombo", "Select POL")}</div>
                <button type="button" class="rixo-tree-card-inline-cancel" data-smap-pol-cancel="1">Cancel</button>
                <button type="button" class="rixo-tree-card-inline-save" data-smap-pol-save="1">Save</button>
            </div>
        """.trimIndent()
    }
    return """
        <div class="supplier-tree-pol-strip" data-smap-pol-row="$rid" data-smap-pol-idx="$bi">
            <span class="supplier-tree-pol-label">POL</span>
            <span class="supplier-tree-pol-value">${escapeHtml(pol.ifBlank { "—" })}</span>
            <button type="button" class="supplier-tree-pol-edit-btn" data-smap-pol-edit="1" title="Edit POL">✎</button>
        </div>
    """.trimIndent()
}

private fun buildSupplierRixoLeafRowHtml(
    rowId: Long,
    stockBranchIdx: Int,
    rixoSegIdx: Int,
    rixoToken: String,
    branchEditing: Boolean,
): String {
    val chip = formatSupplierMapValueChipHtml(rixoToken.ifBlank { "—" })
    val pencilSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>"""
    val trashSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>"""
    val sbi = stockBranchIdx.toString()
    val comboId = "supTreeBranchEditRixo_${rowId}_${rixoSegIdx}"
    if (branchEditing) {
        return """
            <div class="rixo-tree-leaf-row rixo-tree-leaf-row--selectable rixo-tree-leaf-row--inline-editing" data-smap-leaf-id="$rowId" data-smap-stock-branch-idx="$sbi" data-smap-rixo-idx="$rixoSegIdx">
                <div class="rixo-tree-leaf-cells">
                    <div class="rixo-tree-leaf-edit" style="display:flex !important;align-items:center;gap:8px;width:100%;">
                        <div class="rixo-tree-leaf-vtype-wrap" style="flex:1;min-width:120px;">${createEditableCombobox(comboId, "Select Rixo Company", required = true)}</div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    return """
        <div class="rixo-tree-leaf-row rixo-tree-leaf-row--selectable" data-smap-leaf-id="$rowId" data-smap-stock-branch-idx="$sbi" data-smap-rixo-idx="$rixoSegIdx">
            <div class="rixo-tree-leaf-cells">
                <div class="rixo-tree-leaf-view">
                    <div style="flex:1;min-width:0;">$chip</div>
                    <div class="rixo-tree-leaf-price-cell" style="max-width:none;">
                        <button type="button" class="rixo-tree-leaf-edit-btn" data-smap-rixo-edit="1" aria-label="Edit">$pencilSvg</button>
                        <button type="button" class="rixo-tree-leaf-delete-btn" data-smap-rixo-branch-delete="$rowId" data-smap-branch-idx="$sbi" aria-label="Delete">$trashSvg</button>
                    </div>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildSupplierSupplierInlineAddHtml(): String = """
    <div class="rixo-tree-card-wrapper rixo-tree-card-wrapper--company">
        <div class="rixo-tree-inline-add-outer rixo-tree-inline-add-outer--company rixo-tree-card--inline-editing" data-smap-supplier-inline-add="1">
            <div class="rixo-tree-inline-add-box">
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--inputs">
                    <div class="rixo-tree-inline-add-field-primary rixo-tree-inline-add-plain">${createPlainTextInput("supTreeSupplierInlineAddName", "Enter Supplier Name", required = true)}</div>
                </div>
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--actions">
                    <button type="button" class="rixo-tree-card-inline-add-cancel" data-smap-supplier-add-cancel="1">Cancel</button>
                    <button type="button" class="rixo-tree-card-inline-add-save" data-smap-supplier-add-save="1">Add</button>
                </div>
            </div>
        </div>
    </div>
""".trimIndent()

private fun buildSupplierLeafInlineAddHtml(): String = """
    <div class="rixo-tree-inline-add-outer" style="width:100%;max-width:520px;">
        <div class="rixo-tree-inline-add-box">
            <div class="rixo-tree-inline-add-row--inputs">
                <div class="rixo-tree-inline-add-field-primary rixo-tree-card-combobox-wrap">${createEditableCombobox("supTreeLeafAddRixo", "Select Rixo Company", required = true)}</div>
            </div>
            <div class="rixo-tree-inline-add-row--actions">
                <button type="button" class="rixo-tree-card-inline-cancel" data-smap-leaf-add-cancel="1">Cancel</button>
                <button type="button" class="rixo-tree-card-inline-save" data-smap-leaf-add-save="1">Add</button>
            </div>
        </div>
    </div>
""".trimIndent()

private fun buildSupplierStockInlineAddHtml(): String = """
    <div class="rixo-tree-node">
        <div class="rixo-tree-inline-add-outer rixo-tree-inline-add-outer--auction">
            <div class="rixo-tree-inline-add-box">
                <div class="rixo-tree-inline-add-row--inputs">
                    <div class="rixo-tree-inline-add-field-primary rixo-tree-card-combobox-wrap">${createEditableCombobox("supTreeStockAddCombo", "Select Stock Location", required = true)}</div>
                </div>
                <div class="rixo-tree-inline-add-row--actions">
                    <button type="button" class="rixo-tree-card-inline-cancel" data-smap-stock-add-cancel="1">Cancel</button>
                    <button type="button" class="rixo-tree-card-inline-save" data-smap-stock-add-save="1">Add</button>
                </div>
            </div>
        </div>
    </div>
""".trimIndent()

private fun supplierBranchIsSelected(br: SupplierStockBranchView): Boolean =
    supplierTreeSelectedStockRowId == br.row.id &&
        supplierTreeSelectedStockBranchIdx == br.stockBranchIndex

private fun buildSupplierMapTreeHtmlFromCache(): String {
    val rows = supplierTreeRowsForSearch()
    if (rows.isEmpty()) {
        return """
            <div class="rixo-tree supplier-map-tree">
                <div class="rixo-tree-headers">
                    <div class="rixo-tree-header">Supplier</div>
                    <div class="rixo-tree-header">Stock location</div>
                    <div class="rixo-tree-header">POL</div>
                    <div class="rixo-tree-header">Venue ID</div>
                    <div class="rixo-tree-header rixo-tree-header--price">Rixo company</div>
                </div>
                <div class="rixo-tree-note">No matching rows. Add a supplier or adjust search.</div>
                ${if (supplierTreeInlineAddLevel == "supplier") """<div class="rixo-tree-node">${buildSupplierSupplierInlineAddHtml()}</div>""" else ""}
                ${supplierTreeAddButtonHtml("supplier", null, null, null, null)}
            </div>
        """.trimIndent()
    }

    val suppliers = distinctSuppliersSorted(rows)
    if (supplierTreeSelectedSupplier != null && suppliers.none { it.equals(supplierTreeSelectedSupplier, ignoreCase = true) }) {
        supplierTreeSelectedSupplier = null
        supplierTreeSelectedStock = null
        supplierTreeSelectedStockRowId = null
        supplierTreeSelectedStockBranchIdx = null
    }
    val selSup = supplierTreeSelectedSupplier

    val sb = StringBuilder()
    sb.append("""<div class="rixo-tree supplier-map-tree">""")
    sb.append(
        """<div class="rixo-tree-headers"><div class="rixo-tree-header">Supplier</div><div class="rixo-tree-header">Stock location</div><div class="rixo-tree-header">POL</div><div class="rixo-tree-header">Venue ID</div><div class="rixo-tree-header rixo-tree-header--price">Rixo company</div></div>""",
    )

    for (supplier in suppliers) {
        val isSupOpen = supplier.equals(selSup, ignoreCase = true)
        sb.append("""<div class="rixo-tree-node">""")
        sb.append(buildSupplierTreeCardHtml("supplier", supplier, isSupOpen, supplier, "", null, null))
        if (isSupOpen) {
            val branches = expandStockBranchesForSupplier(rows, supplier)
            sb.append("""<div class="rixo-tree-children">""")
            if (branches.isEmpty()) {
                sb.append(supplierTreeAddButtonHtml("stock", supplier, null, null, null))
            } else {
                for (br in branches) {
                    val isStockOpen = supplierBranchIsSelected(br)
                    val branchEditing =
                        supplierTreeBranchEditRowId == br.row.id &&
                            supplierTreeBranchEditBranchIdx == br.stockBranchIndex
                    sb.append("""<div class="rixo-tree-node">""")
                    sb.append(
                        buildSupplierTreeCardHtml(
                            "stock",
                            br.stockToken,
                            isStockOpen,
                            supplier,
                            br.stockToken,
                            br.row.id,
                            br.stockBranchIndex,
                            branchEditActive = branchEditing,
                        ),
                    )
                    if (isStockOpen) {
                        sb.append("""<div class="rixo-tree-children supplier-tree-branch-columns">""")
                        sb.append("""<div class="supplier-tree-branch-grid">""")
                        sb.append("""<div class="supplier-tree-branch-col supplier-tree-branch-col--pol">""")
                        sb.append(buildSupplierPolStripHtml(br.row.id, br.stockBranchIndex, br.polToken, branchEditing = branchEditing))
                        sb.append("""</div>""")
                        sb.append("""<div class="supplier-tree-branch-col supplier-tree-branch-col--venue">""")
                        sb.append(buildSupplierVenueStripHtml(br.venueToken, br.row.id, br.stockBranchIndex, branchEditing = branchEditing))
                        sb.append("""</div>""")
                        sb.append("""<div class="supplier-tree-branch-col supplier-tree-branch-col--rixo">""")
                        for ((rixIdx, tok) in br.rixoLeaves) {
                            sb.append(
                                buildSupplierRixoLeafRowHtml(
                                    br.row.id,
                                    br.stockBranchIndex,
                                    rixIdx,
                                    tok,
                                    branchEditing = branchEditing,
                                ),
                            )
                        }
                        if (branchEditing) {
                            sb.append(
                                """<div class="supplier-tree-branch-update-wrap" style="margin-top:8px;"><button type="button" class="rixo-tree-leaf-update-btn" data-smap-supplier-branch-save="${br.row.id}" data-smap-branch-idx="${br.stockBranchIndex}" aria-label="Update branch"><span class="rixo-tree-leaf-update-icon">&gt;&gt;</span></button></div>""",
                            )
                        }
                        if (supplierTreeInlineAddLevel == "leaf" &&
                            supplierTreeInlineSupplier.equals(supplier, ignoreCase = true) &&
                            supplierTreeInlineStock.equals(br.stockToken, ignoreCase = true) &&
                            supplierTreeLeafAddRowId == br.row.id &&
                            supplierTreeLeafAddBranchIdx == br.stockBranchIndex
                        ) {
                            sb.append(buildSupplierLeafInlineAddHtml())
                        }
                        sb.append(
                            supplierTreeAddButtonHtml(
                                "leaf",
                                supplier,
                                br.stockToken,
                                br.row.id,
                                br.stockBranchIndex,
                            ),
                        )
                        sb.append("""</div></div></div>""")
                    }
                    sb.append("""</div>""")
                }
                if (supplierTreeInlineAddLevel == "stock" &&
                    supplierTreeInlineSupplier.equals(supplier, ignoreCase = true)
                ) {
                    sb.append(buildSupplierStockInlineAddHtml())
                }
                sb.append(supplierTreeAddButtonHtml("stock", supplier, null, null, null))
            }
            sb.append("""</div>""")
        }
        sb.append("""</div>""")
    }
    if (supplierTreeInlineAddLevel == "supplier") {
        sb.append("""<div class="rixo-tree-node">""")
        sb.append(buildSupplierSupplierInlineAddHtml())
        sb.append("""</div>""")
    }
    sb.append(supplierTreeAddButtonHtml("supplier", null, null, null, null))
    sb.append("""</div>""")
    return sb.toString()
}

private fun wireSupplierMapTreeComboboxes() {
    if (supplierTreePolEditRowId != null && supplierTreePolEditBranchIdx != null) {
        populateEditableComboboxFromMasterMenu("supTreePolEditCombo", "pol")
        val row = supplierRowById(supplierTreePolEditRowId!!)
        val parts = splitSupplierSemicolonTokens(row?.pol ?: "")
        val pv = parts.getOrNull(supplierTreePolEditBranchIdx!!).orEmpty()
        setEditableComboboxValue("supTreePolEditCombo", pv)
    }
    if (supplierTreeVenueEditRowId != null && supplierTreeVenueEditBranchIdx != null) {
        populateEditableComboboxFromMasterMenu("supTreeVenueEditCombo", "venue_id")
        val row = supplierRowById(supplierTreeVenueEditRowId!!)
        val parts = splitSupplierSemicolonTokens(row?.venueId ?: "")
        val vv = parts.getOrNull(supplierTreeVenueEditBranchIdx!!).orEmpty()
        setEditableComboboxValue("supTreeVenueEditCombo", vv)
    }
    if (supplierTreeInlineAddLevel == "stock" && supplierTreeInlineSupplier.isNotBlank()) {
        populateEditableComboboxFromMasterMenu("supTreeStockAddCombo", "stock_location")
    }
    if (supplierTreeInlineAddLevel == "leaf") {
        populateEditableComboboxFromRixoMappingDistinctCompanies("supTreeLeafAddRixo")
    }
    if (supplierTreeBranchEditRowId != null && supplierTreeBranchEditBranchIdx != null) {
        val rid = supplierTreeBranchEditRowId!!
        val bidx = supplierTreeBranchEditBranchIdx!!
        populateEditableComboboxFromMasterMenu("supTreeBranchEditStock", "stock_location")
        populateEditableComboboxFromMasterMenu("supTreeBranchEditVenue", "venue_id")
        populateEditableComboboxFromMasterMenu("supTreeBranchEditPol", "pol")
        val row = supplierRowById(rid)
        if (row != null) {
            val stocks = splitSupplierSemicolonTokens(row.stock)
            setEditableComboboxValue("supTreeBranchEditStock", stocks.getOrNull(bidx).orEmpty())
            val venues = splitSupplierSemicolonTokens(row.venueId)
            setEditableComboboxValue("supTreeBranchEditVenue", venues.getOrNull(bidx).orEmpty())
            val pols = splitSupplierSemicolonTokens(row.pol)
            setEditableComboboxValue("supTreeBranchEditPol", pols.getOrNull(bidx).orEmpty())
            val branches = expandStockBranchesForSupplier(supplierMapTreeRowsCache, row.supplier)
            val br = branches.firstOrNull { it.row.id == rid && it.stockBranchIndex == bidx }
            if (br != null) {
                for ((rixIdx, _) in br.rixoLeaves) {
                    val cid = "supTreeBranchEditRixo_${rid}_${rixIdx}"
                    populateEditableComboboxFromRixoMappingDistinctCompanies(cid)
                    val rp = splitSupplierSemicolonTokens(row.rixoCompany)
                    setEditableComboboxValue(cid, rp.getOrNull(rixIdx).orEmpty())
                }
            }
        }
    }
}

private fun closeAllSupplierCardMenus(root: HTMLElement) {
    val wraps = root.querySelectorAll(".rixo-tree-card-menu-wrap")
    for (i in 0 until wraps.length) {
        (wraps.item(i) as? HTMLElement)?.classList?.remove("is-open")
    }
}

private fun refreshSupplierMapTreeData() {
    val root = document.getElementById("supplierMapTreeRoot") as? HTMLElement ?: return
    val searchQ = getSupplierMapSearchQuery().trim()
    if (searchQ.isNotEmpty()) {
        fetchSupplierMapTreeRowsSearch { ok ->
            if (ok) {
                root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                bindSupplierMapTreeClicks(root)
                window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
            }
        }
    } else {
        window.fetch(apiUrl("rixo/prices"))
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('load')") }
            .then { result: dynamic ->
                val ok = result.success as? Boolean ?: false
                if (!ok) throw js("Error('bad')")
                val prices = result.data ?: js("[]")
                val arr = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
                supplierMapTreeRowsCache = parseSupplierPriceRows(arr.toList())
                root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                bindSupplierMapTreeClicks(root)
                window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
            }
            .catch { _: dynamic ->
                showMessage("Failed to refresh supplier map", "error")
            }
    }
}

private fun fetchSupplierMapTreeRowsSearch(done: (Boolean) -> Unit) {
    val encQ = js("encodeURIComponent")(getSupplierMapSearchQuery().trim()).unsafeCast<String>()
    val encF = js("encodeURIComponent")(supplierMapSearchFieldChoice).unsafeCast<String>()
    val url = apiUrl("rixo/prices/page-search?q=$encQ&field=$encF&page=0&size=500")
    window.fetch(url)
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('search')") }
        .then { body: dynamic ->
            val err = js("body.error")?.toString()?.trim()
            if (!err.isNullOrEmpty()) throw js("Error(err)")
            val content = js("body.content") ?: js("[]")
            val arr = js("Array.isArray(content) ? content : []") as Array<dynamic>
            supplierMapTreeRowsCache = parseSupplierPriceRows(arr.toList())
            supplierMapSearchTotal = js("body.totalElements")?.toString()?.toLongOrNull() ?: 0L
            supplierMapSearchTotalPages = js("body.totalPages")?.toString()?.toIntOrNull() ?: 1
            done(true)
        }
        .catch { _: dynamic ->
            showMessage("Supplier map search failed", "error")
            done(false)
        }
}

private fun loadSupplierMapTree() {
    val root = document.getElementById("supplierMapTreeRoot") as? HTMLElement ?: return
    root.innerHTML = """<div class="rixo-tree-loading">Loading…</div>"""
    val searchQ = getSupplierMapSearchQuery().trim()
    if (searchQ.isNotEmpty()) {
        supplierMapSearchServerMode = true
        fetchSupplierMapTreeRowsSearch { ok ->
            if (!ok) {
                root.innerHTML = """<div class="rixo-tree-note">Search failed.</div>"""
                return@fetchSupplierMapTreeRowsSearch
            }
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
        }
        return
    }
    supplierMapSearchServerMode = false
    supplierMapSearchTotal = 0
    supplierMapSearchTotalPages = 0
    supplierMapSearchPageZeroBased = 0
    window.fetch(apiUrl("rixo/prices"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('load')") }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message||'')")
            val prices = result.data ?: js("[]")
            val arr = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            supplierMapTreeRowsCache = parseSupplierPriceRows(arr.toList())
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
        }
        .catch { _: dynamic ->
            root.innerHTML = """<div class="rixo-tree-note">Failed to load supplier map.</div>"""
        }
}

private fun supplierRowsBySupplierKey(supplier: String): List<SupplierPriceRowLite> =
    supplierMapTreeRowsCache.filter { it.supplier.equals(supplier, ignoreCase = true) }

private fun supplierRowById(id: Long): SupplierPriceRowLite? =
    supplierMapTreeRowsCache.firstOrNull { it.id == id }

private fun notifySupplierRixoPricesChanged() {
    try {
        val timestamp = js("Date.now()").toString()
        safeLocalStorageSet("supplierUpdated", timestamp)
        val supplierEvent = js("new CustomEvent('supplierUpdated', { detail: { timestamp: timestamp } })")
        window.dispatchEvent(supplierEvent)
    } catch (_: dynamic) { }
}

private fun deleteSupplierPriceIds(ids: List<Long>, _root: HTMLElement, onDone: () -> Unit) {
    if (ids.isEmpty()) {
        onDone()
        return
    }
    val id = ids.first()
    val rest = ids.drop(1)
    window.fetch(apiUrl("rixo/mappings/$id"), js("""{ method:'DELETE', headers:{'Content-Type':'application/json'} }"""))
        .then { r: dynamic -> r.json().then { _: dynamic -> r } }
        .then { _: dynamic ->
            deleteSupplierPriceIds(rest, _root, onDone)
        }
        .catch { _: dynamic ->
            showMessage("Delete failed partway", "error")
            refreshSupplierMapTreeData()
        }
}

private fun putSupplierMappingRow(row: SupplierPriceRowLite, successMessage: String = "Updated") {
    val payload = js("{}")
    payload.auctionHouse = row.supplier
    payload.stockLocation = row.stock
    payload.rixoCompany = row.rixoCompany
    payload.venueId = row.venueId
    payload.pol = row.pol
    window.fetch(apiUrl("rixo/mappings/${row.id}"), js("""{ method:'PUT', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('put')") }
        .then { _: dynamic ->
            supplierTreePolEditRowId = null
            supplierTreePolEditBranchIdx = null
            supplierTreeVenueEditRowId = null
            supplierTreeVenueEditBranchIdx = null
            supplierTreeBranchEditRowId = null
            supplierTreeBranchEditBranchIdx = null
            supplierTreeSupplierEditName = null
            showMessage(successMessage, "success")
            notifySupplierRixoPricesChanged()
            refreshSupplierMapTreeData()
        }
        .catch { _: dynamic ->
            showMessage("Update failed", "error")
            refreshSupplierMapTreeData()
        }
}

private fun updateSupplierRowsSequentially(rows: List<SupplierPriceRowLite>, newSupplierName: String, root: HTMLElement) {
    if (rows.isEmpty()) {
        supplierTreeSupplierEditName = null
        supplierTreeSelectedSupplier = newSupplierName
        showMessage("Supplier name updated", "success")
        notifySupplierRixoPricesChanged()
        refreshSupplierMapTreeData()
        return
    }
    
    val row = rows.first()
    val rest = rows.drop(1)
    
    val payload = js("{}")
    payload.auctionHouse = newSupplierName
    payload.stockLocation = row.stock
    payload.rixoCompany = row.rixoCompany
    payload.venueId = row.venueId
    payload.pol = row.pol
    window.fetch(apiUrl("rixo/mappings/${row.id}"), js("""{ method:'PUT', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('put')") }
        .then { _: dynamic ->
            updateSupplierRowsSequentially(rest, newSupplierName, root)
        }
        .catch { _: dynamic ->
            showMessage("Update failed partway", "error")
            supplierTreeSupplierEditName = null
            refreshSupplierMapTreeData()
        }
}

/** Replace one semicolon segment for POL or venue_id on a single mapping row. */
private fun putSupplierFieldSegment(
    rowId: Long,
    branchIdx: Int,
    field: String,
    newValue: String,
    successMessage: String,
) {
    val row = supplierRowById(rowId) ?: return
    val nv = newValue.trim()
    val parts = when (field) {
        "pol" -> splitSupplierSemicolonTokens(row.pol).toMutableList()
        "venueId" -> splitSupplierSemicolonTokens(row.venueId).toMutableList()
        else -> return
    }
    while (parts.size <= branchIdx) parts.add("")
    if (branchIdx < parts.size) parts[branchIdx] = nv
    else parts.add(nv)
    val joined = joinSupplierSemicolonParts(parts)
    val updated = when (field) {
        "pol" -> row.copy(pol = joined)
        "venueId" -> row.copy(venueId = joined)
        else -> row
    }
    putSupplierMappingRow(updated, successMessage)
}

private fun applySupplierBranchInlineEdit(rowId: Long, branchIdx: Int) {
    val row = supplierRowById(rowId) ?: return
    val newStock = getEditableComboboxValue("supTreeBranchEditStock").trim()
    val newVenue = getEditableComboboxValue("supTreeBranchEditVenue").trim()
    val newPol = getEditableComboboxValue("supTreeBranchEditPol").trim()
    if (newStock.isEmpty() || newStock == "-") {
        showMessage("Stock location is required", "error")
        return
    }
    val branches = expandStockBranchesForSupplier(supplierMapTreeRowsCache, row.supplier)
    val br = branches.firstOrNull { it.row.id == rowId && it.stockBranchIndex == branchIdx } ?: run {
        showMessage("Branch not found", "error")
        return
    }
    val rixSegs = splitSupplierSemicolonTokens(row.rixoCompany).toMutableList()
    for ((rixIdx, _) in br.rixoLeaves) {
        val v = getEditableComboboxValue("supTreeBranchEditRixo_${rowId}_${rixIdx}").trim()
        if (v.isEmpty()) {
            showMessage("Rixo company is required", "error")
            return
        }
        while (rixSegs.size <= rixIdx) rixSegs.add("")
        if (rixIdx < rixSegs.size) rixSegs[rixIdx] = v else rixSegs.add(v)
    }
    val newRixoJoined = joinSupplierSemicolonParts(rixSegs)
    val updated = row.copy(
        stock = replaceSupplierSemicolonSegment(row.stock, branchIdx, newStock),
        venueId = replaceSupplierSemicolonSegment(row.venueId, branchIdx, newVenue),
        pol = replaceSupplierSemicolonSegment(row.pol, branchIdx, newPol),
        rixoCompany = newRixoJoined.ifBlank { "-" },
    )
    putSupplierMappingRow(updated, "Mapping updated")
}

private fun supplierStockBranchCount(row: SupplierPriceRowLite): Int {
    val stocks = splitSupplierSemicolonTokens(row.stock).filter { it.isNotBlank() && it != "-" }
    return if (stocks.isEmpty()) 1 else stocks.size
}

/** Add a Rixo token for this branch: multiple leaves when one stock branch; one segment per branch when stock is split. */
private fun mergeNewRixoIntoRow(row: SupplierPriceRowLite, branchIdx: Int, newTok: String): SupplierPriceRowLite {
    val nt = newTok.trim()
    val nBranches = supplierStockBranchCount(row)
    val rixosAll = splitSupplierSemicolonTokens(row.rixoCompany).toMutableList()
    if (nBranches <= 1) {
        val nonBlank = rixosAll.map { it.trim() }.filter { it.isNotEmpty() && it != "-" }.toMutableList()
        nonBlank.add(nt)
        return row.copy(rixoCompany = joinSupplierSemicolonParts(nonBlank))
    }
    while (rixosAll.size <= branchIdx) rixosAll.add("")
    val cur = rixosAll.getOrNull(branchIdx)?.trim().orEmpty()
    rixosAll[branchIdx] = if (cur.isEmpty() || cur == "-") nt else "$cur;$nt"
    return row.copy(rixoCompany = joinSupplierSemicolonParts(rixosAll))
}

private fun removeParallelBranchFromRow(rowId: Long, branchIdx: Int, _root: HTMLElement) {
    val row = supplierRowById(rowId) ?: return
    fun rm(raw: String): String {
        val p = splitSupplierSemicolonTokens(raw).toMutableList()
        if (branchIdx in p.indices) p.removeAt(branchIdx)
        return joinSupplierSemicolonParts(p)
    }
    val ns = rm(row.stock)
    val nv = rm(row.venueId)
    val np = rm(row.pol)
    val nr = rm(row.rixoCompany)
    val stocksAfter = splitSupplierSemicolonTokens(ns).filter { it.isNotBlank() && it != "-" }
    if (stocksAfter.isEmpty()) {
        deleteSupplierPriceIds(listOf(rowId), _root) {
            supplierTreeSelectedStockRowId = null
            supplierTreeSelectedStockBranchIdx = null
            supplierTreeSelectedStock = null
            showMessage("Mapping deleted", "success")
            notifySupplierRixoPricesChanged()
            refreshSupplierMapTreeData()
        }
        return
    }
    putSupplierMappingRow(row.copy(stock = ns, venueId = nv, pol = np, rixoCompany = nr), "Branch removed")
}

private fun bindSupplierMapTreeClicks(root: HTMLElement) {
    val prev = root.asDynamic().__supplierTreeClickHandler.unsafeCast<((Event) -> Unit)?>()
    if (prev != null) root.removeEventListener("click", prev)

    val handler: (Event) -> Unit = click@{ ev ->
        val target = ev.target.asDynamic() as? Element ?: return@click
        if (target.closest(".rixo-tree-card-menu-wrap") == null) closeAllSupplierCardMenus(root)

        val addBtn = target.closest(".rixo-tree-add-btn") as? HTMLElement
        if (addBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val lvl = addBtn.getAttribute("data-smap-add").orEmpty()
            val sup = addBtn.getAttribute("data-smap-supplier")?.trim().orEmpty()
            val stk = addBtn.getAttribute("data-smap-stock")?.trim().orEmpty()
            when (lvl) {
                "supplier" -> {
                    supplierTreeInlineAddLevel = "supplier"
                    supplierTreeBranchEditRowId = null
                    supplierTreeBranchEditBranchIdx = null
                    supplierTreePolEditRowId = null
                    supplierTreePolEditBranchIdx = null
                    supplierTreeVenueEditRowId = null
                    supplierTreeVenueEditBranchIdx = null
                    root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                    bindSupplierMapTreeClicks(root)
                    window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
                }
                "stock" -> {
                    supplierTreeInlineAddLevel = "stock"
                    supplierTreeInlineSupplier = sup
                    supplierTreeInlineStock = ""
                    supplierTreeBranchEditRowId = null
                    supplierTreeBranchEditBranchIdx = null
                    supplierTreeSelectedSupplier = sup
                    supplierTreeSelectedStock = null
                    root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                    bindSupplierMapTreeClicks(root)
                    window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
                }
                "leaf" -> {
                    supplierTreeInlineAddLevel = "leaf"
                    supplierTreeInlineSupplier = sup
                    supplierTreeInlineStock = stk
                    supplierTreeLeafAddRowId = addBtn.getAttribute("data-smap-row-id")?.toLongOrNull()
                    supplierTreeLeafAddBranchIdx = addBtn.getAttribute("data-smap-branch-idx")?.toIntOrNull()
                    supplierTreeBranchEditRowId = null
                    supplierTreeBranchEditBranchIdx = null
                    supplierTreeSelectedSupplier = sup
                    supplierTreeSelectedStock = stk
                    supplierTreeSelectedStockRowId = supplierTreeLeafAddRowId
                    supplierTreeSelectedStockBranchIdx = supplierTreeLeafAddBranchIdx
                    root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                    bindSupplierMapTreeClicks(root)
                    window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
                }
            }
            return@click
        }

        val menuBtn = target.closest(".rixo-tree-card-menu-btn") as? HTMLElement
        if (menuBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val wrap = menuBtn.closest(".rixo-tree-card-menu-wrap") as? HTMLElement ?: return@click
            val wasOpen = wrap.classList.contains("is-open")
            closeAllSupplierCardMenus(root)
            if (!wasOpen) wrap.classList.add("is-open")
            return@click
        }

        val menuItem = target.closest("[data-smap-menu]") as? HTMLElement
        if (menuItem != null) {
            ev.preventDefault()
            ev.stopPropagation()
            closeAllSupplierCardMenus(root)
            val action = menuItem.getAttribute("data-smap-menu").orEmpty()
            val wrap = menuItem.closest(".rixo-tree-card-wrapper") as? HTMLElement ?: return@click
            val level = wrap.getAttribute("data-smap-card-level").orEmpty()
            val ps = wrap.getAttribute("data-smap-path-supplier")?.trim().orEmpty()
            when (action) {
                "edit" -> {
                    when (level) {
                        "supplier" -> {
                            supplierTreeSupplierEditName = ps
                            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                            bindSupplierMapTreeClicks(root)
                        }
                        "stock" -> {
                            val id = wrap.getAttribute("data-smap-row-id")?.toLongOrNull()
                            if (id != null) editMasterSupplier(id)
                            else showMessage("No row for stock branch", "error")
                        }
                    }
                }
                "delete" -> {
                    when (level) {
                        "supplier" -> {
                            val ids = supplierRowsBySupplierKey(ps).map { it.id }
                            if (ids.isEmpty()) return@click
                            if (js("window.confirm")("Delete all mappings for this supplier?") as Boolean) {
                                deleteSupplierPriceIds(ids, root) {
                                    showMessage("Deleted", "success")
                                    notifySupplierRixoPricesChanged()
                                    refreshSupplierMapTreeData()
                                }
                            }
                        }
                        "stock" -> {
                            val rid = wrap.getAttribute("data-smap-row-id")?.toLongOrNull()
                            val bidx = wrap.getAttribute("data-smap-branch-idx")?.toIntOrNull()
                            if (rid == null || bidx == null) return@click
                            if (js("window.confirm")("Remove this stock branch and aligned venue / POL / Rixo slots?") as Boolean) {
                                removeParallelBranchFromRow(rid, bidx, root)
                            }
                        }
                    }
                }
            }
            return@click
        }

        val cardBtn = target.closest(".rixo-tree-card[data-smap-card-level]") as? HTMLElement
        if (cardBtn != null && target.closest(".rixo-tree-card-menu-wrap") == null) {
            if (cardBtn.classList.contains("rixo-tree-card--inline-editing")) return@click
            ev.preventDefault()
            val level = cardBtn.getAttribute("data-smap-card-level").orEmpty()
            val wrap = cardBtn.closest(".rixo-tree-card-wrapper") as? HTMLElement ?: return@click
            val ps = wrap.getAttribute("data-smap-path-supplier")?.trim().orEmpty()
            val pst = wrap.getAttribute("data-smap-path-stock")?.trim().orEmpty()
            when (level) {
                "supplier" -> {
                    supplierTreeBranchEditRowId = null
                    supplierTreeBranchEditBranchIdx = null
                    supplierTreeSelectedSupplier = ps
                    supplierTreeSelectedStock = null
                    supplierTreeSelectedStockRowId = null
                    supplierTreeSelectedStockBranchIdx = null
                }
                "stock" -> {
                    supplierTreeBranchEditRowId = null
                    supplierTreeBranchEditBranchIdx = null
                    supplierTreeSelectedSupplier = ps
                    supplierTreeSelectedStock = pst
                    supplierTreeSelectedStockRowId = wrap.getAttribute("data-smap-row-id")?.toLongOrNull()
                    supplierTreeSelectedStockBranchIdx = wrap.getAttribute("data-smap-branch-idx")?.toIntOrNull()
                }
            }
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
            return@click
        }

        val polEdit = target.closest("[data-smap-pol-edit]") as? HTMLElement
        if (polEdit != null) {
            ev.preventDefault()
            val strip = polEdit.closest(".supplier-tree-pol-strip") as? HTMLElement ?: return@click
            supplierTreeBranchEditRowId = null
            supplierTreeBranchEditBranchIdx = null
            supplierTreePolEditRowId = strip.getAttribute("data-smap-pol-row")?.toLongOrNull()
            supplierTreePolEditBranchIdx = strip.getAttribute("data-smap-pol-idx")?.toIntOrNull()
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
            return@click
        }

        val polCancel = target.closest("[data-smap-pol-cancel]") as? HTMLElement
        if (polCancel != null) {
            ev.preventDefault()
            supplierTreePolEditRowId = null
            supplierTreePolEditBranchIdx = null
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            return@click
        }

        val polSave = target.closest("[data-smap-pol-save]") as? HTMLElement
        if (polSave != null) {
            ev.preventDefault()
            val rid = supplierTreePolEditRowId ?: return@click
            val bidx = supplierTreePolEditBranchIdx ?: return@click
            val np = getEditableComboboxValue("supTreePolEditCombo").trim()
            putSupplierFieldSegment(rid, bidx, "pol", np, "POL updated")
            return@click
        }

        val venueEdit = target.closest("[data-smap-venue-edit]") as? HTMLElement
        if (venueEdit != null) {
            ev.preventDefault()
            val strip = venueEdit.closest(".supplier-tree-venue-strip") as? HTMLElement ?: return@click
            supplierTreeBranchEditRowId = null
            supplierTreeBranchEditBranchIdx = null
            supplierTreeVenueEditRowId = strip.getAttribute("data-smap-venue-row")?.toLongOrNull()
            supplierTreeVenueEditBranchIdx = strip.getAttribute("data-smap-venue-idx")?.toIntOrNull()
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
            return@click
        }

        val venueCancel = target.closest("[data-smap-venue-cancel]") as? HTMLElement
        if (venueCancel != null) {
            ev.preventDefault()
            supplierTreeVenueEditRowId = null
            supplierTreeVenueEditBranchIdx = null
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            return@click
        }

        val venueSave = target.closest("[data-smap-venue-save]") as? HTMLElement
        if (venueSave != null) {
            ev.preventDefault()
            val rid = supplierTreeVenueEditRowId ?: return@click
            val bidx = supplierTreeVenueEditBranchIdx ?: return@click
            val nv = getEditableComboboxValue("supTreeVenueEditCombo").trim()
            putSupplierFieldSegment(rid, bidx, "venueId", nv, "Venue ID updated")
            return@click
        }

        val supplierCancel = target.closest("[data-smap-supplier-cancel]") as? HTMLElement
        if (supplierCancel != null) {
            ev.preventDefault()
            supplierTreeSupplierEditName = null
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            return@click
        }

        val supplierSave = target.closest("[data-smap-supplier-save]") as? HTMLElement
        if (supplierSave != null) {
            ev.preventDefault()
            val oldName = supplierTreeSupplierEditName ?: return@click
            val newName = (document.getElementById("supTreeSupplierInlineEditInput") as? HTMLInputElement)?.value?.trim().orEmpty()
            if (newName.isEmpty()) {
                showMessage("Supplier name is required", "error")
                return@click
            }
            if (newName == oldName) {
                supplierTreeSupplierEditName = null
                root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                bindSupplierMapTreeClicks(root)
                return@click
            }
            val rows = supplierRowsBySupplierKey(oldName)
            if (rows.isEmpty()) {
                supplierTreeSupplierEditName = null
                root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                bindSupplierMapTreeClicks(root)
                return@click
            }
            updateSupplierRowsSequentially(rows, newName, root)
            return@click
        }

        val supplierBranchSaveBtn = target.closest("[data-smap-supplier-branch-save]") as? HTMLElement
        if (supplierBranchSaveBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val rid = supplierBranchSaveBtn.getAttribute("data-smap-supplier-branch-save")?.toLongOrNull() ?: return@click
            val bidx = supplierBranchSaveBtn.getAttribute("data-smap-branch-idx")?.toIntOrNull() ?: return@click
            applySupplierBranchInlineEdit(rid, bidx)
            return@click
        }

        val leafEdit = target.closest("[data-smap-rixo-edit]") as? HTMLElement
        if (leafEdit != null) {
            ev.preventDefault()
            val row = leafEdit.closest(".rixo-tree-leaf-row") as? HTMLElement ?: return@click
            val id = row.getAttribute("data-smap-leaf-id")?.toLongOrNull() ?: return@click
            val stockBranchIdx = row.getAttribute("data-smap-stock-branch-idx")?.toIntOrNull() ?: return@click
            if (supplierTreeBranchEditRowId == id && supplierTreeBranchEditBranchIdx == stockBranchIdx) {
                supplierTreeBranchEditRowId = null
                supplierTreeBranchEditBranchIdx = null
            } else {
                supplierTreeBranchEditRowId = id
                supplierTreeBranchEditBranchIdx = stockBranchIdx
                supplierTreePolEditRowId = null
                supplierTreePolEditBranchIdx = null
                supplierTreeVenueEditRowId = null
                supplierTreeVenueEditBranchIdx = null
            }
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            window.setTimeout({ wireSupplierMapTreeComboboxes() }, 0)
            return@click
        }

        val rixoBranchDel = target.closest("[data-smap-rixo-branch-delete]") as? HTMLElement
        if (rixoBranchDel != null) {
            ev.preventDefault()
            val id = rixoBranchDel.getAttribute("data-smap-rixo-branch-delete")?.toLongOrNull() ?: return@click
            val bidx = rixoBranchDel.getAttribute("data-smap-branch-idx")?.toIntOrNull() ?: return@click
            if (!window.confirm("Remove this branch (stock, venue, POL, and Rixo slots)?") as Boolean) return@click
            removeParallelBranchFromRow(id, bidx, root)
            return@click
        }

        val supplierAddCancel = target.closest("[data-smap-supplier-add-cancel]") as? HTMLElement
        if (supplierAddCancel != null) {
            ev.preventDefault()
            supplierTreeInlineAddLevel = null
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            return@click
        }

        val supplierAddSave = target.closest("[data-smap-supplier-add-save]") as? HTMLElement
        if (supplierAddSave != null) {
            ev.preventDefault()
            val name = (document.getElementById("supTreeSupplierInlineAddNameInput") as? HTMLInputElement)?.value?.trim().orEmpty()
            if (name.isEmpty()) {
                showMessage("Supplier name is required", "error")
                return@click
            }
            val payload = js("{}")
            payload.auctionHouse = name
            payload.stockLocation = "-"
            payload.rixoCompany = "-"
            payload.venueId = ""
            payload.pol = ""
            window.fetch(apiUrl("rixo/mappings/add"), js("""{ method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
                .then { r: dynamic ->
                    r.json().then { res: dynamic ->
                        if (r.ok && (res.success as? Boolean == true)) {
                            supplierTreeInlineAddLevel = null
                            supplierTreeSelectedSupplier = name
                            supplierTreeSelectedStock = null
                            supplierTreeSelectedStockRowId = null
                            supplierTreeSelectedStockBranchIdx = null
                            showMessage((res.message as? String) ?: "Supplier added", "success")
                            notifySupplierRixoPricesChanged()
                            refreshSupplierMapTreeData()
                        } else {
                            showMessage((res.message as? String) ?: "Add failed", "error")
                        }
                        Unit
                    }
                }
                .catch { _: dynamic -> showMessage("Add failed", "error") }
            return@click
        }

        val stockAddCancel = target.closest("[data-smap-stock-add-cancel]") as? HTMLElement
        if (stockAddCancel != null) {
            ev.preventDefault()
            supplierTreeInlineAddLevel = null
            supplierTreeInlineSupplier = ""
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            return@click
        }

        val stockAddSave = target.closest("[data-smap-stock-add-save]") as? HTMLElement
        if (stockAddSave != null) {
            ev.preventDefault()
            val sup = supplierTreeInlineSupplier.trim()
            val st = getEditableComboboxValue("supTreeStockAddCombo").trim()
            if (sup.isEmpty() || st.isEmpty()) {
                showMessage("Stock location is required", "error")
                return@click
            }
            val venue = supplierRowsBySupplierKey(sup).firstOrNull()?.venueId ?: ""
            val payload = js("{}")
            payload.auctionHouse = sup
            payload.stockLocation = st
            payload.rixoCompany = "-"
            payload.venueId = venue
            payload.pol = ""
            window.fetch(apiUrl("rixo/mappings/add"), js("""{ method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
                .then { r: dynamic ->
                    r.json().then { res: dynamic ->
                        if (r.ok && (res.success as? Boolean == true)) {
                            supplierTreeInlineAddLevel = null
                            supplierTreeInlineSupplier = ""
                            supplierTreeSelectedSupplier = sup
                            supplierTreeSelectedStock = st
                            showMessage((res.message as? String) ?: "Stock added", "success")
                            notifySupplierRixoPricesChanged()
                            refreshSupplierMapTreeData()
                        } else {
                            showMessage((res.message as? String) ?: "Add failed", "error")
                        }
                        Unit
                    }
                }
                .catch { _: dynamic -> showMessage("Add failed", "error") }
            return@click
        }

        val leafAddCancel = target.closest("[data-smap-leaf-add-cancel]") as? HTMLElement
        if (leafAddCancel != null) {
            ev.preventDefault()
            supplierTreeInlineAddLevel = null
            supplierTreeLeafAddRowId = null
            supplierTreeLeafAddBranchIdx = null
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            return@click
        }

        val leafAddSave = target.closest("[data-smap-leaf-add-save]") as? HTMLElement
        if (leafAddSave != null) {
            ev.preventDefault()
            val sup = supplierTreeInlineSupplier.trim()
            val stk = supplierTreeInlineStock.trim()
            val rixo = getEditableComboboxValue("supTreeLeafAddRixo").trim()
            if (sup.isEmpty() || stk.isEmpty() || rixo.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return@click
            }
            val rid = supplierTreeLeafAddRowId
            val bidx = supplierTreeLeafAddBranchIdx ?: 0
            if (rid != null) {
                val row = supplierRowById(rid) ?: return@click
                supplierTreeInlineAddLevel = null
                supplierTreeLeafAddRowId = null
                supplierTreeLeafAddBranchIdx = null
                val updated = mergeNewRixoIntoRow(row, bidx, rixo)
                putSupplierMappingRow(updated, "Rixo company added")
                return@click
            }
            val venue = supplierRowsBySupplierKey(sup).firstOrNull()?.venueId ?: ""
            val payload = js("{}")
            payload.auctionHouse = sup
            payload.stockLocation = stk
            payload.rixoCompany = rixo
            payload.venueId = venue
            payload.pol = ""
            window.fetch(apiUrl("rixo/mappings/add"), js("""{ method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
                .then { r: dynamic ->
                    r.json().then { res: dynamic ->
                        if (r.ok && (res.success as? Boolean == true)) {
                            supplierTreeInlineAddLevel = null
                            supplierTreeLeafAddRowId = null
                            supplierTreeLeafAddBranchIdx = null
                            showMessage((res.message as? String) ?: "Mapping added", "success")
                            notifySupplierRixoPricesChanged()
                            refreshSupplierMapTreeData()
                        } else {
                            showMessage((res.message as? String) ?: "Add failed", "error")
                        }
                        Unit
                    }
                }
                .catch { _: dynamic -> showMessage("Add failed", "error") }
            return@click
        }
    }

    root.asDynamic().__supplierTreeClickHandler = handler
    root.addEventListener("click", handler)
}

private val rixoCanonicalVehicleTypes = listOf(
    "TRUCK",
    "PASSENGER CAR",
    "MACHINERY",
    "CAR",
    "BUS"
)

private data class RixoMappingRowLite(
    val id: String,
    val company: String,
    val auction: String,
    val stock: String,
    val vType: String?,
    val price: String?,
)

/** Tree UI labels for empty DB columns (not shown as cards — only + Add). */
private const val RIXO_PLACEHOLDER_AUCTION = "(no auction house)"
private const val RIXO_PLACEHOLDER_STOCK = "(no stock location)"

private fun rixoNormCompany(s: String) = s.ifEmpty { "(no company)" }
private fun rixoNormAuction(s: String) = s.ifEmpty { RIXO_PLACEHOLDER_AUCTION }
private fun rixoNormStock(s: String) = s.ifEmpty { RIXO_PLACEHOLDER_STOCK }

/** Tree lists only real keys; skeleton rows (empty auction/stock in DB) do not create nodes — user uses + Add instead. */
private fun rixoVisibleAuctionsForTree(companyRows: List<RixoMappingRowLite>): List<String> =
    companyRows.map { rixoNormAuction(it.auction) }.filter { it != RIXO_PLACEHOLDER_AUCTION }.distinct()
        .sortedBy { it.lowercase() }

private fun rixoVisibleStocksForTree(auctionRows: List<RixoMappingRowLite>): List<String> =
    auctionRows.map { rixoNormStock(it.stock) }.filter { it != RIXO_PLACEHOLDER_STOCK }.distinct()
        .sortedBy { it.lowercase() }

private fun RixoMappingRowLite.isRixoCompanyOnlySkeleton(): Boolean {
    val a = rixoNormAuction(auction)
    val st = rixoNormStock(stock)
    return a == RIXO_PLACEHOLDER_AUCTION && st == RIXO_PLACEHOLDER_STOCK &&
        vType.isNullOrBlank() && (price.isNullOrBlank())
}

private fun RixoMappingRowLite.isRixoStockPlaceholderPending(): Boolean {
    return rixoNormStock(stock) == RIXO_PLACEHOLDER_STOCK && vType.isNullOrBlank() && (price.isNullOrBlank())
}

private fun RixoMappingRowLite.isRixoLeafFieldsPending(): Boolean {
    return rixoNormStock(stock) != RIXO_PLACEHOLDER_STOCK &&
        vType.isNullOrBlank() && (price.isNullOrBlank())
}

/** One partial row to merge when adding the first auction under a company (no real auctions in tree yet). */
private fun rixoMergeRowIdForAuction(company: String): Long? {
    val c = rixoNormCompany(company)
    val rows = rixoTreeRowsCache.filter { rixoNormCompany(it.company) == c }
    if (rixoVisibleAuctionsForTree(rows).isNotEmpty()) return null
    return rows.filter { it.isRixoCompanyOnlySkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

/** Merge when this auction branch has no real stock yet but one row already has company+auction. */
private fun rixoMergeRowIdForStock(company: String, auction: String): Long? {
    val c = rixoNormCompany(company)
    val a = rixoNormAuction(auction)
    val rows = rixoTreeRowsCache.filter { rixoNormCompany(it.company) == c && rixoNormAuction(it.auction) == a }
    if (rixoVisibleStocksForTree(rows).isNotEmpty()) return null
    return rows.filter { it.isRixoStockPlaceholderPending() }.singleOrNull()?.id?.toLongOrNull()
}

/** Merge first vehicle/price onto the row that already has company + auction + stock. */
private fun rixoMergeRowIdForLeaf(company: String, auction: String, stock: String): Long? {
    val c = rixoNormCompany(company)
    val a = rixoNormAuction(auction)
    val s = rixoNormStock(stock)
    val rows = rixoTreeRowsCache.filter {
        rixoNormCompany(it.company) == c && rixoNormAuction(it.auction) == a && rixoNormStock(it.stock) == s
    }
    return rows.filter { it.isRixoLeafFieldsPending() }.singleOrNull()?.id?.toLongOrNull()
}

private fun rixoDynStr(v: dynamic?): String {
    if (v == null || v == js("undefined")) return ""
    return v.toString().trim()
}

/** True when a rixo_company / stock_location value is empty or a placeholder (not a real mapping key). */
private fun rixoIsBlankMappingKey(s: String): Boolean {
    val t = s.trim()
    if (t.isEmpty()) return true
    if (t == "-" || t == "—" || t == "–") return true
    if (t.equals("n/a", ignoreCase = true) || t.equals("na", ignoreCase = true)) return true
    if (t.equals("null", ignoreCase = true) || t.equals("none", ignoreCase = true)) return true
    if (t.equals("(no company)", ignoreCase = true)) return true
    if (t.equals("(no stock location)", ignoreCase = true)) return true
    return false
}

private fun rixoMatchCanonicalVehicleType(raw: String?): String? {
    val t = raw?.trim() ?: return null
    if (t.isEmpty()) return null
    return rixoCanonicalVehicleTypes.firstOrNull { it.equals(t, ignoreCase = true) }
}

private fun rixoSplitVehicleTypes(raw: String?): List<String> {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return emptyList()
    // DB values may store multiple types in one row: "CAR ; BIG CAR" / "BIG CAR; TRUCK".
    return t.split(";")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

private fun rixoFormatPriceDisplay(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return "—"

    // Normalize to digits only; DB or UI might store "15000", "¥15,000", etc.
    val digits = s.replace(Regex("[^0-9]"), "")
    if (digits.isEmpty()) {
        // Non-numeric content; just escape for safety.
        return escapeHtml(s)
    }

    // Format integer with thousands separators and prepend Yen sign.
    val formattedInt = digits.replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
    return "¥$formattedInt"
}

/**
 * Hub-and-spoke SVG stretched to match the adjacent column height (preserveAspectRatio none).
 * Connects one source (left) to N targets stacked vertically (right).
 */
private fun rixoOkrBranchHubSvg(nTargets: Int, variant: String): String {
    if (nTargets <= 0) return ""
    val stroke = """stroke="#d1d5db" stroke-width="2" fill="none" stroke-linecap="round" vector-effect="non-scaling-stroke" """
    val sb = StringBuilder()
    val w = if (variant == "leaves") 44 else 52
    sb.append("""<svg class="rixo-okr-hub-svg rixo-okr-hub-svg--$variant" viewBox="0 0 100 100" preserveAspectRatio="none" width="$w" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">""")
    if (nTargets == 1) {
        sb.append("""<path d="M 0 50 L 100 50" $stroke/>""")
    } else {
        val ys = (0 until nTargets).map { (it + 0.5) / nTargets * 100.0 }
        val yMin = ys.first()
        val yMax = ys.last()
        sb.append("""<path d="M 0 50 L 32 50" $stroke/>""")
        sb.append("""<path d="M 32 $yMin L 32 $yMax" $stroke/>""")
        for (y in ys) {
            sb.append("""<path d="M 32 $y L 100 $y" $stroke/>""")
        }
    }
    sb.append("</svg>")
    return sb.toString()
}

private var rixoTreeRowsCache: List<RixoMappingRowLite> = emptyList()
private var rixoSelectedCompany: String? = null
private var rixoSelectedAuction: String? = null
private var rixoSelectedStock: String? = null
private var rixoSelectedMappingId: Long? = null
private var rixoSelectedLeafType: String? = null
/** Inline edit for a specific leaf line (same mapping id can have multiple type segments). */
private var rixoLeafInlineEditMappingId: Long? = null
private var rixoLeafInlineEditLineType: String = ""
/** Overflow-menu inline rename for company / auction / stock cards (no modal). */
private var rixoCardInlineEditLevel: String? = null
private var rixoCardInlineEditCompany: String = ""
private var rixoCardInlineEditAuction: String = ""
private var rixoCardInlineEditStock: String = ""
private var rixoCardInlineEditCurrentLabel: String = ""
/** Distinct non-blank `rixo_mapping.auction_name` values (ordered) for auction add/edit comboboxes on this page. */
private var rixoMasterSuppliers: List<String> = emptyList()
private var rixoMasterCompanies: List<String> = emptyList()
private var rixoMasterStocks: List<String> = emptyList()
private var rixoMasterVehicleTypes: List<String> = emptyList()
private var rixoMasterOptionsReady: Boolean = false

private var rixoCardInlineAddLevel: String? = null
private var rixoCardInlineAddCompany: String = ""
private var rixoCardInlineAddAuction: String = ""
private var rixoCardInlineAddStock: String = ""

/**
 * Tree flow: rixo_company -> auction_name -> stock_location -> vehicle/price rows.
 */
fun showRixoMappingTreePage() {
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="rixoMappingPage" class="rixo-tree-page rixo-tree-page--mapping">
            <div class="rixo-tree-topbar">
                <h2 class="rixo-tree-title">Rixo Price Mapping</h2>
            </div>
            <div id="rixoMappingTreeRoot" class="rixo-tree-root rixo-tree-root--mapping">
                <div class="rixo-tree-loading">Loading…</div>
            </div>
        </div>
    """.trimIndent()
    loadRixoMappingTree()
}

private fun parseRixoMappingRows(rows: List<dynamic>): List<RixoMappingRowLite> {
    return rows.map { r ->
        RixoMappingRowLite(
            id = rixoDynStr(r.id),
            company = rixoDynStr(r.rixoCompany),
            auction = rixoDynStr(r.auctionName),
            stock = rixoDynStr(r.stockLocation),
            vType = rixoDynStr(r.supportedVehicleType).takeIf { it.isNotEmpty() },
            price = rixoDynStr(r.rixoPrice).takeIf { it.isNotEmpty() }
        )
    }.filter { row ->
        !(rixoIsBlankMappingKey(row.company) && rixoIsBlankMappingKey(row.stock))
    }
}

/** Footer "+ Add" (values escaped for HTML attributes). Opens inline add row, not a modal. */
private fun rixoTreeAddButtonHtml(level: String, company: String?, auction: String?, stock: String?): String {
    val label = "+ Add"
    val c = escapeHtml(company ?: "")
    val a = escapeHtml(auction ?: "")
    val s = escapeHtml(stock ?: "")
    val wrapClass = if (level == "leaf") "rixo-tree-add-wrap rixo-tree-add-wrap--leaf" else "rixo-tree-add-wrap"
    return """<div class="$wrapClass"><button type="button" class="rixo-tree-add-btn" data-add-level="$level" data-company="$c" data-auction="$a" data-stock="$s">$label</button></div>"""
}

private fun rixoInlinePathSelectId(mappingId: Long, level: String): String =
    "rixo-inline-path-${mappingId}-$level"

private fun rixoCardInlineComboboxId(level: String): String = when (level) {
    "company" -> "rixoCardInlineCompany"
    "auction" -> "rixoCardInlineAuction"
    "stock" -> "rixoCardInlineStock"
    else -> "rixoCardInlineCompany"
}

private fun clearRixoCardInlineEdit() {
    rixoCardInlineEditLevel = null
    rixoCardInlineEditCompany = ""
    rixoCardInlineEditAuction = ""
    rixoCardInlineEditStock = ""
    rixoCardInlineEditCurrentLabel = ""
}

private fun clearRixoCardInlineAdd() {
    rixoCardInlineAddLevel = null
    rixoCardInlineAddCompany = ""
    rixoCardInlineAddAuction = ""
    rixoCardInlineAddStock = ""
}

private fun rixoCardInlineAddMatchesAuctionBranch(company: String): Boolean =
    rixoCardInlineAddLevel == "auction" &&
        rixoNormCompany(rixoCardInlineAddCompany) == rixoNormCompany(company)

private fun rixoCardInlineAddMatchesStockBranch(company: String, auction: String): Boolean =
    rixoCardInlineAddLevel == "stock" &&
        rixoNormCompany(rixoCardInlineAddCompany) == rixoNormCompany(company) &&
        rixoNormAuction(rixoCardInlineAddAuction) == rixoNormAuction(auction)

private fun rixoCardInlineAddMatchesLeafBranch(company: String, auction: String, stock: String): Boolean =
    rixoCardInlineAddLevel == "leaf" &&
        rixoNormCompany(rixoCardInlineAddCompany) == rixoNormCompany(company) &&
        rixoNormAuction(rixoCardInlineAddAuction) == rixoNormAuction(auction) &&
        rixoNormStock(rixoCardInlineAddStock) == rixoNormStock(stock)

/** Boxed two-row inline add (inputs row + Cancel/Add), aligned with leaf add UX. */
private fun buildRixoCardInlineAddHtml(level: String): String {
    val outerClass = when (level) {
        "company" -> "rixo-tree-inline-add-outer rixo-tree-inline-add-outer--company"
        "auction" -> "rixo-tree-inline-add-outer rixo-tree-inline-add-outer--auction"
        else -> "rixo-tree-inline-add-outer rixo-tree-inline-add-outer--stock"
    }
    val comboboxId = when (level) {
        "company" -> "rixoCardInlineAddCompany"
        "auction" -> "rixoCardInlineAddAuction"
        else -> "rixoCardInlineAddStock"
    }
    val placeholder = when (level) {
        "company" -> "Enter Rixo Company"
        "auction" -> "Select Auction House"
        else -> "Select Stock Location"
    }
    val fieldPrimary = if (level == "company") {
        """<div class="rixo-tree-inline-add-field-primary rixo-tree-inline-add-plain">${createPlainTextInput(comboboxId, placeholder, required = true)}</div>"""
    } else {
        """<div class="rixo-tree-inline-add-field-primary rixo-tree-card-combobox-wrap">${createEditableCombobox(comboboxId, placeholder, required = true)}</div>"""
    }
    return """
        <div class="$outerClass rixo-tree-card--inline-editing" data-level="$level" data-value="" aria-expanded="false" data-rixo-inline-add="1">
            <div class="rixo-tree-inline-add-box">
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--inputs">
                    $fieldPrimary
                </div>
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--actions">
                    <button type="button" class="rixo-tree-card-inline-add-cancel">Cancel</button>
                    <button type="button" class="rixo-tree-card-inline-add-save">Add</button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildRixoLeafInlineAddHtml(): String {
    return """
        <div class="rixo-tree-leaf-row rixo-tree-leaf-row--inline-editing" data-rixo-inline-add="leaf">
            <div class="rixo-tree-leaf-cells">
                <div class="rixo-tree-leaf-edit rixo-tree-leaf-edit--inline-add-stack">
                    <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--inputs">
                        <div class="rixo-tree-leaf-vtype-wrap">${createEditableCombobox("rixoCardInlineAddLeafType", "Select Vehicle Type", required = true)}</div>
                        <input id="rixoCardInlineAddLeafPrice" class="rixo-tree-leaf-inline-price money-input" type="text" step="any" min="0" value="" placeholder="0" inputmode="decimal">
                    </div>
                    <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--actions">
                        <button type="button" class="rixo-tree-leaf-inline-add-cancel">Cancel</button>
                        <button type="button" class="rixo-tree-leaf-inline-add-save">Add</button>
                    </div>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun rixoCardInlineTargetMatches(level: String, pathCompany: String, pathAuction: String, pathStock: String): Boolean {
    if (rixoCardInlineEditLevel != level) return false
    return when (level) {
        "company" -> rixoNormCompany(rixoCardInlineEditCompany) == rixoNormCompany(pathCompany)
        "auction" -> rixoNormCompany(rixoCardInlineEditCompany) == rixoNormCompany(pathCompany) &&
            rixoNormAuction(rixoCardInlineEditAuction) == rixoNormAuction(pathAuction)
        "stock" -> rixoNormCompany(rixoCardInlineEditCompany) == rixoNormCompany(pathCompany) &&
            rixoNormAuction(rixoCardInlineEditAuction) == rixoNormAuction(pathAuction) &&
            rixoNormStock(rixoCardInlineEditStock) == rixoNormStock(pathStock)
        else -> false
    }
}

/** When inline-editing a leaf row, company/auction/stock cards on that path render as master dropdowns. */
private fun buildRixoCardHtml(
    level: String,
    label: String,
    selected: Boolean,
    inlineRow: RixoMappingRowLite?,
    showInlinePathSelect: Boolean,
    pathCompany: String = "",
    pathAuction: String = "",
    pathStock: String = "",
): String {
    val selectedClass = if (selected) " rixo-tree-card--selected" else ""
    val levelClass = when (level) {
        "company" -> " rixo-tree-card--company"
        "auction" -> " rixo-tree-card--auction"
        else -> " rixo-tree-card--stock"
    }
    val ariaExpanded = if (selected) "true" else "false"
    val mid = inlineRow?.id?.toLongOrNull()
    val useLeafInline = showInlinePathSelect && inlineRow != null && mid != null
    val useCardInline =
        rixoCardInlineEditLevel == level &&
            rixoCardInlineTargetMatches(level, pathCompany, pathAuction, pathStock) &&
            !useLeafInline
    if (useLeafInline || useCardInline) {
        val comboboxId = if (useLeafInline) rixoInlinePathSelectId(mid!!, level) else rixoCardInlineComboboxId(level)
        val placeholder = when (level) {
            "company" -> "Select Rixo Company"
            "auction" -> "Select Auction House"
            else -> "Select Stock Location"
        }
        val comboboxHtml = if (level == "company") {
            createPlainTextInput(comboboxId, "Enter Rixo Company", required = true)
        } else {
            createEditableCombobox(comboboxId, placeholder, required = true)
        }
        val extraInlineClass = if (useCardInline) " rixo-tree-card--inline-editing-with-actions" else ""
        val actionsHtml = if (useCardInline) {
            """
                <div class="rixo-tree-card-inline-actions">
                    <button type="button" class="rixo-tree-card-inline-cancel">Cancel</button>
                    <button type="button" class="rixo-tree-card-inline-save">Save</button>
                </div>
            """.trimIndent()
        } else {
            ""
        }
        return """
                <div class="rixo-tree-card$levelClass$selectedClass rixo-tree-card--inline-editing$extraInlineClass" data-level="$level" data-value="${escapeHtml(label)}" aria-expanded="$ariaExpanded">
                    <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                    <div class="rixo-tree-card-combobox-wrap">$comboboxHtml</div>
                    $actionsHtml
                </div>
            """.trimIndent()
    }
    val wrapperLevelClass = when (level) {
        "company" -> "rixo-tree-card-wrapper--company"
        "auction" -> "rixo-tree-card-wrapper--auction"
        else -> "rixo-tree-card-wrapper--stock"
    }
    val pathC = escapeHtml(pathCompany)
    val pathA = escapeHtml(pathAuction)
    val pathS = escapeHtml(pathStock)
    return """
        <div class="rixo-tree-card-wrapper $wrapperLevelClass" data-path-company="$pathC" data-path-auction="$pathA" data-path-stock="$pathS" data-card-level="$level">
            <button type="button" class="rixo-tree-card$levelClass$selectedClass" data-level="$level" data-value="${escapeHtml(label)}" aria-expanded="$ariaExpanded">
                <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                <span class="rixo-tree-label">${escapeHtml(label)}</span>
            </button>
            <div class="rixo-tree-card-menu-wrap">
                <button type="button" class="rixo-tree-card-menu-btn" aria-label="More actions" aria-haspopup="true">&#8942;</button>
                <div class="rixo-tree-card-menu-panel" role="menu">
                    <button type="button" class="rixo-tree-card-menu-item" data-menu-action="edit" role="menuitem">Edit</button>
                    <button type="button" class="rixo-tree-card-menu-item rixo-tree-card-menu-item--danger" data-menu-action="delete" role="menuitem">Delete</button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildRixoMappingTreeHtml(rows: List<dynamic>): String {
    val list = parseRixoMappingRows(rows)
    rixoTreeRowsCache = list
    return buildRixoMappingTreeHtmlFromCache()
}

private data class RixoLeafRow(val id: Long, val type: String, val priceDisplay: String)

private fun buildRixoLeafRowsFromStockRows(stockRows: List<RixoMappingRowLite>): List<RixoLeafRow> {
    val leafRows = mutableListOf<RixoLeafRow>()
    for (r in stockRows) {
        val rowId = r.id.toLongOrNull() ?: continue
        val types = rixoSplitVehicleTypes(r.vType)
        val rawPrice = r.price?.toString()
        val priceDisplay = rixoFormatPriceDisplay(rawPrice)
        if (types.isEmpty()) {
            val noPrice = rawPrice.isNullOrBlank() || priceDisplay == "—"
            if (noPrice) continue
            leafRows.add(RixoLeafRow(id = rowId, type = "", priceDisplay = priceDisplay))
        } else {
            for (type in types) {
                leafRows.add(RixoLeafRow(id = rowId, type = type, priceDisplay = priceDisplay))
            }
        }
    }
    return leafRows
}

private fun rixoLeafLineTypesMatch(a: String, b: String): Boolean {
    val ta = a.trim()
    val tb = b.trim()
    if (ta.isEmpty() && tb.isEmpty()) return true
    return ta.equals(tb, ignoreCase = true)
}

/** When a DB row lists multiple vehicle types (semicolon-separated), replace only the segment for this leaf line. */
private fun mergeRixoVehicleTypeForLineEdit(
    row: RixoMappingRowLite,
    lineType: String,
    newType: String,
): String {
    val nt = newType.trim()
    val oldTypes = rixoSplitVehicleTypes(row.vType)
    if (oldTypes.isEmpty()) return nt
    if (oldTypes.size == 1) return nt
    val lineNorm = lineType.trim()
    var replacedOne = false
    val replaced = oldTypes.map { t ->
        if (!replacedOne && rixoLeafLineTypesMatch(t, lineNorm)) {
            replacedOne = true
            nt
        } else {
            t
        }
    }
    return replaced.joinToString("; ")
}

/** Delete the entire `rixo_mapping` row (whole branch / DB row). */
private fun executeRixoLeafLineDelete(mappingId: Long, @Suppress("UNUSED_PARAMETER") lineTypeRaw: String) {
    if (rixoTreeRowsCache.none { it.id.toLongOrNull() == mappingId }) {
        showMessage("Mapping row not found", "error")
        return
    }

    fun clearSelectionIfRow() {
        if (rixoSelectedMappingId == mappingId) {
            rixoSelectedMappingId = null
            rixoSelectedLeafType = null
        }
        if (rixoLeafInlineEditMappingId == mappingId) {
            rixoLeafInlineEditMappingId = null
            rixoLeafInlineEditLineType = ""
        }
    }

    window.fetch(apiUrl("rixo-mapping/$mappingId"), js("""{ method:'DELETE', headers:{'Content-Type':'application/json'} }"""))
        .then { resp: dynamic ->
            resp.json().then { result: dynamic ->
                val p = js("{}")
                p.resp = resp
                p.result = result
                p
            }
        }
        .then { pair: dynamic ->
            val resp = pair.resp
            val result = pair.result
            if (resp.ok && (result.success as? Boolean == true)) {
                showMessage("Mapping deleted", "success")
                clearSelectionIfRow()
                refreshRixoMappingTreeData()
            } else {
                showMessage(result.message?.toString() ?: "Failed to delete", "error")
            }
        }
        .catch { err: dynamic ->
            Logger.error("Delete rixo mapping failed: ${err.toString()}")
            showMessage("Failed to delete mapping", "error")
        }
}

private fun buildRixoLeafRowHtml(
    leaf: RixoLeafRow,
    baseRow: RixoMappingRowLite,
    fieldSeq: Int,
    selectedClass: String,
): String {
    val isEditing = rixoLeafInlineEditMappingId != null &&
        rixoLeafInlineEditMappingId == leaf.id &&
        rixoLeafLineTypesMatch(rixoLeafInlineEditLineType, leaf.type)
    val inlineClass = if (isEditing) " rixo-tree-leaf-row--inline-editing" else ""
    val typeDisplay = leaf.type.ifBlank { "—" }
    val rawPrice = baseRow.price ?: ""
    val normalizedPrice = Regex("""-?\d[\d,]*(?:\.\d+)?""")
        .find(rawPrice)
        ?.value
        ?.replace(",", "")
        ?: ""
    val prefix = "rixoLeafInline_${leaf.id}_$fieldSeq"
    val lineAttr = escapeHtml(leaf.type)
    val pencilSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>"""
    val trashSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>"""
    return """
        <div class="rixo-tree-leaf-row rixo-tree-leaf-row--selectable$selectedClass$inlineClass" data-mapping-id="${leaf.id}" data-mapping-type="${escapeHtml(leaf.type)}" data-line-type="$lineAttr" data-inline-field-id="$prefix">
            <div class="rixo-tree-leaf-cells">
                <div class="rixo-tree-leaf-view">
                    <span class="rixo-tree-leaf-type">${escapeHtml(typeDisplay)}</span>
                    <div class="rixo-tree-leaf-price-cell">
                        <span class="rixo-tree-leaf-price">${escapeHtml(leaf.priceDisplay)}</span>
                        <button type="button" class="rixo-tree-leaf-edit-btn" aria-label="Edit">$pencilSvg</button>
                        <button type="button" class="rixo-tree-leaf-delete-btn" aria-label="Delete">$trashSvg</button>
                    </div>
                </div>
                <div class="rixo-tree-leaf-edit">
                    <div class="rixo-tree-leaf-vtype-wrap">${createEditableCombobox("${prefix}_type", "Select Vehicle Type", required = true)}</div>
                    <input id="${prefix}_price" class="rixo-tree-leaf-inline-price money-input" type="text" step="any" min="0" value="${escapeHtml(normalizedPrice)}" placeholder="0" inputmode="decimal">
                    <button type="button" class="rixo-tree-leaf-update-btn" aria-label="Update mapping"><span class="rixo-tree-leaf-update-icon">&gt;&gt;</span></button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildRixoMappingTreeHtmlFromCache(): String {
    val list = rixoTreeRowsCache
    if (list.isEmpty()) {
        val inlineCompany = if (rixoCardInlineAddLevel == "company") {
            """<div class="rixo-tree-node">${buildRixoCardInlineAddHtml("company")}</div>"""
        } else ""
        return """
            <div class="rixo-tree">
                <div class="rixo-tree-headers"><div class="rixo-tree-header">Rixo Company</div><div class="rixo-tree-header">Auction House</div><div class="rixo-tree-header">Stock Location</div><div class="rixo-tree-header">Supported Vehicle Type</div><div class="rixo-tree-header rixo-tree-header--price">Rixo Price</div></div>
                <div class="rixo-tree-note" style="max-width:560px;">No rixo_mapping rows yet. Add your first mapping below (values must exist in master menus for each dropdown).</div>
                $inlineCompany
                <div class="rixo-tree-col-footer">${rixoTreeAddButtonHtml("company", null, null, null)}</div>
            </div>
        """.trimIndent()
    }

    val companies = list.map { rixoNormCompany(it.company) }.distinct().sortedBy { it.lowercase() }
    if (rixoSelectedCompany !in companies) {
        rixoSelectedCompany = null
        rixoSelectedAuction = null
        rixoSelectedStock = null
    }

    val rowsByCompany = if (rixoSelectedCompany == null) emptyList() else list.filter {
        rixoNormCompany(it.company) == rixoSelectedCompany
    }
    val auctions = rixoVisibleAuctionsForTree(rowsByCompany)
    if (rixoSelectedAuction !in auctions) {
        rixoSelectedAuction = null
        rixoSelectedStock = null
    }

    val rowsByAuction = if (rixoSelectedAuction == null) emptyList() else rowsByCompany.filter {
        rixoNormAuction(it.auction) == rixoSelectedAuction
    }
    val stocks = rixoVisibleStocksForTree(rowsByAuction)
    if (rixoSelectedStock !in stocks) {
        rixoSelectedStock = null
    }

    var leafFieldSeq = 0
    val inlineRow = rixoTreeRowsCache.firstOrNull { it.id.toLongOrNull() == rixoLeafInlineEditMappingId }
    val sb = StringBuilder()
    sb.append("""<div class="rixo-tree">""")
    sb.append("""<div class="rixo-tree-headers"><div class="rixo-tree-header">Rixo Company</div><div class="rixo-tree-header">Auction House</div><div class="rixo-tree-header">Stock Location</div><div class="rixo-tree-header">Supported Vehicle Type</div><div class="rixo-tree-header rixo-tree-header--price">Rixo Price</div></div>""")
    for (company in companies) {
        val companyRows = list.filter { rixoNormCompany(it.company) == company }
        val isCompanyOpen = (company == rixoSelectedCompany)
        val showInlineCompany = (inlineRow != null && rixoNormCompany(inlineRow.company) == company) ||
            (rixoCardInlineEditLevel == "company" && rixoCardInlineTargetMatches("company", company, "", ""))
        sb.append("""<div class="rixo-tree-node">""")
        sb.append(buildRixoCardHtml("company", company, isCompanyOpen, inlineRow, showInlineCompany, pathCompany = company))
        if (isCompanyOpen) {
            val companyAuctions = rixoVisibleAuctionsForTree(companyRows)
            sb.append("""<div class="rixo-tree-children">""")
            if (companyAuctions.isEmpty()) {
                if (rixoCardInlineAddMatchesAuctionBranch(company)) {
                    sb.append("""<div class="rixo-tree-node">""")
                    sb.append(buildRixoCardInlineAddHtml("auction"))
                    sb.append("""</div>""")
                }
                sb.append("""<div class="rixo-tree-col-footer">${rixoTreeAddButtonHtml("auction", company, null, null)}</div>""")
            } else {
                for (auction in companyAuctions) {
                    val auctionRows = companyRows.filter { rixoNormAuction(it.auction) == auction }
                    val isAuctionOpen = (auction == rixoSelectedAuction)
                    val showInlineAuction = (inlineRow != null &&
                        rixoNormCompany(inlineRow.company) == company &&
                        rixoNormAuction(inlineRow.auction) == auction) ||
                        (rixoCardInlineEditLevel == "auction" &&
                            rixoCardInlineTargetMatches("auction", company, auction, ""))
                    sb.append("""<div class="rixo-tree-node">""")
                    sb.append(buildRixoCardHtml("auction", auction, isAuctionOpen, inlineRow, showInlineAuction, pathCompany = company, pathAuction = auction))
                    if (isAuctionOpen) {
                        val auctionStocks = rixoVisibleStocksForTree(auctionRows)
                        sb.append("""<div class="rixo-tree-children">""")
                        if (auctionStocks.isEmpty()) {
                            if (rixoCardInlineAddMatchesStockBranch(company, auction)) {
                                sb.append("""<div class="rixo-tree-node">""")
                                sb.append(buildRixoCardInlineAddHtml("stock"))
                                sb.append("""</div>""")
                            }
                            sb.append("""<div class="rixo-tree-col-footer">${rixoTreeAddButtonHtml("stock", company, auction, null)}</div>""")
                        } else {
                            for (stock in auctionStocks) {
                                val stockRows = auctionRows.filter { rixoNormStock(it.stock) == stock }
                                val isStockOpen = (stock == rixoSelectedStock)
                                val showInlineStock = (inlineRow != null &&
                                    rixoNormCompany(inlineRow.company) == company &&
                                    rixoNormAuction(inlineRow.auction) == auction &&
                                    rixoNormStock(inlineRow.stock) == stock) ||
                                    (rixoCardInlineEditLevel == "stock" &&
                                        rixoCardInlineTargetMatches("stock", company, auction, stock))
                                sb.append("""<div class="rixo-tree-node">""")
                                sb.append(buildRixoCardHtml("stock", stock, isStockOpen, inlineRow, showInlineStock, pathCompany = company, pathAuction = auction, pathStock = stock))
                                if (isStockOpen) {
                                    val leafRows = buildRixoLeafRowsFromStockRows(stockRows)
                                    sb.append("""<div class="rixo-tree-children"><div class="rixo-tree-leaf-wrap"><div class="rixo-tree-leaf-grid">""")
                                    if (leafRows.isNotEmpty()) {
                                        for (leaf in leafRows) {
                                            val selectedClass = if (rixoSelectedMappingId == leaf.id) " rixo-tree-leaf-row--selected" else ""
                                            val baseRow = stockRows.firstOrNull { it.id.toLongOrNull() == leaf.id }
                                            if (baseRow != null) {
                                                sb.append(buildRixoLeafRowHtml(leaf, baseRow, leafFieldSeq, selectedClass))
                                                leafFieldSeq++
                                            }
                                        }
                                    }
                                    if (rixoCardInlineAddMatchesLeafBranch(company, auction, stock)) {
                                        sb.append(buildRixoLeafInlineAddHtml())
                                    }
                                    sb.append(rixoTreeAddButtonHtml("leaf", company, auction, stock))
                                    sb.append("""</div></div></div>""")
                                }
                                sb.append("""</div>""")
                            }
                            if (rixoCardInlineAddMatchesStockBranch(company, auction)) {
                                sb.append("""<div class="rixo-tree-node">""")
                                sb.append(buildRixoCardInlineAddHtml("stock"))
                                sb.append("""</div>""")
                            }
                            sb.append("""<div class="rixo-tree-col-footer">${rixoTreeAddButtonHtml("stock", company, auction, null)}</div>""")
                        }
                        sb.append("""</div>""")
                    }
                    sb.append("""</div>""")
                }
            }
            // One auction + Add: empty branch already added it above; when there are auctions, footer adds another auction under this company.
            if (companyAuctions.isNotEmpty()) {
                if (rixoCardInlineAddMatchesAuctionBranch(company)) {
                    sb.append("""<div class="rixo-tree-node">""")
                    sb.append(buildRixoCardInlineAddHtml("auction"))
                    sb.append("""</div>""")
                }
                sb.append("""<div class="rixo-tree-col-footer">${rixoTreeAddButtonHtml("auction", company, null, null)}</div>""")
            }
            sb.append("""</div>""")
        }
        sb.append("""</div>""")
    }
    if (rixoCardInlineAddLevel == "company") {
        sb.append("""<div class="rixo-tree-node">""")
        sb.append(buildRixoCardInlineAddHtml("company"))
        sb.append("""</div>""")
    }
    sb.append("""<div class="rixo-tree-col-footer">${rixoTreeAddButtonHtml("company", null, null, null)}</div>""")
    sb.append("""</div>""")
    return sb.toString()
}

private fun bindRixoMappingTreeClicks(root: HTMLElement) {
    val prev = root.asDynamic().__rixoTreeClickHandler.unsafeCast<((Event) -> Unit)?>()
    if (prev != null) {
        root.removeEventListener("click", prev)
    }
    val clickHandler: (Event) -> Unit = treeClick@{ ev ->
        val target = ev.target.asDynamic() as? Element ?: return@treeClick
        if (target.closest(".rixo-tree-card-menu-wrap") == null) {
            closeAllRixoCardMenus(root)
        }
        val addBtn = target.closest(".rixo-tree-add-btn") as? HTMLElement
        if (addBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val level = addBtn.getAttribute("data-add-level").orEmpty()
            if (level !in setOf("company", "auction", "stock", "leaf")) return@treeClick
            val companyAttr = addBtn.getAttribute("data-company")?.trim().orEmpty()
            val auctionAttr = addBtn.getAttribute("data-auction")?.trim().orEmpty()
            val stockAttr = addBtn.getAttribute("data-stock")?.trim().orEmpty()
            startRixoInlineAdd(level, companyAttr, auctionAttr, stockAttr, root)
            return@treeClick
        }
        val menuItemEl = target.closest(".rixo-tree-card-menu-item") as? HTMLElement
        if (menuItemEl != null) {
            ev.preventDefault()
            ev.stopPropagation()
            closeAllRixoCardMenus(root)
            val action = menuItemEl.getAttribute("data-menu-action").orEmpty()
            val cardWrap = menuItemEl.closest(".rixo-tree-card-wrapper") as? HTMLElement ?: return@treeClick
            val cardLevel = cardWrap.getAttribute("data-card-level").orEmpty()
            if (cardLevel !in setOf("company", "auction", "stock")) return@treeClick
            val pc = cardWrap.getAttribute("data-path-company")?.trim().orEmpty()
            val pa = cardWrap.getAttribute("data-path-auction")?.trim().orEmpty()
            val ps = cardWrap.getAttribute("data-path-stock")?.trim().orEmpty()
            val cardBtn = cardWrap.querySelector(".rixo-tree-card[data-level]") as? HTMLElement
            val currentLabel = cardBtn?.getAttribute("data-value")?.trim().orEmpty()
            when (action) {
                "edit" -> {
                    rixoLeafInlineEditMappingId = null
                    rixoLeafInlineEditLineType = ""
                    clearRixoCardInlineAdd()
                    rixoCardInlineEditLevel = cardLevel
                    rixoCardInlineEditCompany = pc
                    rixoCardInlineEditAuction = pa
                    rixoCardInlineEditStock = ps
                    rixoCardInlineEditCurrentLabel = currentLabel
                    rixoEnsureMasterOptions { ok ->
                        if (!ok) {
                            clearRixoCardInlineEdit()
                            return@rixoEnsureMasterOptions
                        }
                        root.innerHTML = buildRixoMappingTreeHtmlFromCache()
                        bindRixoMappingTreeClicks(root)
                        window.setTimeout({
                            wireRixoInlineTreeComboboxes()
                        }, 0)
                    }
                }
                "delete" -> executeRixoBranchDelete(cardLevel, pc, pa, ps, currentLabel)
                else -> {}
            }
            return@treeClick
        }
        val cardInlineAddSave = target.closest(".rixo-tree-card-inline-add-save") as? HTMLElement
        if (cardInlineAddSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRixoCardInlineAddSave()
            return@treeClick
        }
        val cardInlineAddCancel = target.closest(".rixo-tree-card-inline-add-cancel") as? HTMLElement
        if (cardInlineAddCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearRixoCardInlineAdd()
            root.innerHTML = buildRixoMappingTreeHtmlFromCache()
            bindRixoMappingTreeClicks(root)
            return@treeClick
        }
        val menuToggleBtn = target.closest(".rixo-tree-card-menu-btn") as? HTMLElement
        if (menuToggleBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val wrap = menuToggleBtn.closest(".rixo-tree-card-menu-wrap") as? HTMLElement ?: return@treeClick
            val wasOpen = wrap.classList.contains("is-open")
            closeAllRixoCardMenus(root)
            if (!wasOpen) wrap.classList.add("is-open")
            return@treeClick
        }
        val cardInlineSave = target.closest(".rixo-tree-card-inline-save") as? HTMLElement
        if (cardInlineSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRixoCardInlineSave(root)
            return@treeClick
        }
        val cardInlineCancel = target.closest(".rixo-tree-card-inline-cancel") as? HTMLElement
        if (cardInlineCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            cancelRixoCardInlineEdit(root)
            return@treeClick
        }
        if (target.closest(".rixo-tree-card--inline-editing") != null) {
            return@treeClick
        }
        val leafInlineAddSave = target.closest(".rixo-tree-leaf-inline-add-save") as? HTMLElement
        if (leafInlineAddSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRixoLeafInlineAddSave()
            return@treeClick
        }
        val leafInlineAddCancel = target.closest(".rixo-tree-leaf-inline-add-cancel") as? HTMLElement
        if (leafInlineAddCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearRixoCardInlineAdd()
            root.innerHTML = buildRixoMappingTreeHtmlFromCache()
            bindRixoMappingTreeClicks(root)
            return@treeClick
        }
        val leafEditBtn = target.closest(".rixo-tree-leaf-edit-btn") as? HTMLElement
        if (leafEditBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val row = leafEditBtn.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@treeClick
            val id = row.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@treeClick
            val lineType = row.getAttribute("data-mapping-type")?.trim().orEmpty()
            clearRixoCardInlineEdit()
            clearRixoCardInlineAdd()
            rixoLeafInlineEditMappingId = id
            rixoLeafInlineEditLineType = lineType
            rixoEnsureMasterOptions { ok ->
                if (!ok) {
                    rixoLeafInlineEditMappingId = null
                    rixoLeafInlineEditLineType = ""
                    return@rixoEnsureMasterOptions
                }
                root.innerHTML = buildRixoMappingTreeHtmlFromCache()
                bindRixoMappingTreeClicks(root)
                window.setTimeout({
                    wireRixoInlineTreeComboboxes()
                }, 0)
            }
            return@treeClick
        }
        val leafDeleteBtn = target.closest(".rixo-tree-leaf-delete-btn") as? HTMLElement
        if (leafDeleteBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val row = leafDeleteBtn.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@treeClick
            val id = row.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@treeClick
            val lineType = row.getAttribute("data-mapping-type")?.trim().orEmpty()
            if (!window.confirm("Delete this mapping row?")) return@treeClick
            executeRixoLeafLineDelete(id, lineType)
            return@treeClick
        }
        val leafUpdateBtn = target.closest(".rixo-tree-leaf-update-btn") as? HTMLElement
        if (leafUpdateBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val row = leafUpdateBtn.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@treeClick
            val id = row.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@treeClick
            val lineType = row.getAttribute("data-mapping-type")?.trim().orEmpty()
            val prefix = row.getAttribute("data-inline-field-id") ?: return@treeClick
            val baseRow = rixoTreeRowsCache.firstOrNull { it.id.toLongOrNull() == id }
            if (baseRow == null) {
                showMessage("Mapping row not found", "error")
                return@treeClick
            }
            val inp = document.getElementById("${prefix}_price") as? HTMLInputElement
            val price = inp?.value?.trim().orEmpty()
            val coId = rixoInlinePathSelectId(id, "company")
            val auId = rixoInlinePathSelectId(id, "auction")
            val stId = rixoInlinePathSelectId(id, "stock")
            val typeId = "${prefix}_type"
            val vtype = getEditableComboboxValue(typeId)
            val company = getEditableComboboxValue(coId).takeIf { it.isNotEmpty() } ?: baseRow.company.trim()
            val auction = getEditableComboboxValue(auId).takeIf { it.isNotEmpty() } ?: baseRow.auction.trim()
            val stock = getEditableComboboxValue(stId).takeIf { it.isNotEmpty() } ?: baseRow.stock.trim()
            if (vtype.isEmpty()) {
                showMessage("Vehicle type is required", "error")
                return@treeClick
            }
            if (!rixoListContains(rixoMasterVehicleTypes, vtype)) {
                showMessage("Please select a vehicle type from the list", "error")
                return@treeClick
            }
            if (price.isEmpty()) {
                showMessage("Rixo price must be numeric", "error")
                return@treeClick
            }
            val priceNum = parseMoneyNumericInput(price)
            if (priceNum == null) {
                showMessage("Rixo price must be numeric", "error")
                return@treeClick
            }
            if (company.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return@treeClick
            }
            val auctionOk = auction.isEmpty() || rixoListContains(rixoMasterSuppliers, auction)
            val stockOk = stock.isEmpty() || rixoListContains(rixoMasterStocks, stock)
            if (!auctionOk || !stockOk) {
                showMessage("Auction house must be a known Rixo mapping name; stock location must match the master list when set", "error")
                return@treeClick
            }
            val merged = mergeRixoVehicleTypeForLineEdit(baseRow, lineType, vtype)
            val payload = js("{}")
            payload.rixoCompany = company
            payload.auctionName = auction
            payload.stockLocation = stock
            payload.supportedVehicleType = merged
            payload.rixoPrice = price.replace(",", "").trim()
            window.fetch(apiUrl("rixo-mapping/$id"), js("""{ method:'PUT', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
                .then { resp: dynamic ->
                    resp.json().then { result: dynamic ->
                        val p = js("{}")
                        p.resp = resp
                        p.result = result
                        p
                    }
                }
                .then { pair: dynamic ->
                    val resp = pair.resp
                    val result = pair.result
                    if (resp.ok && (result.success as? Boolean == true)) {
                        rixoLeafInlineEditMappingId = null
                        rixoLeafInlineEditLineType = ""
                        rixoSelectedCompany = rixoNormCompany(company)
                        rixoSelectedAuction = rixoNormAuction(auction)
                        rixoSelectedStock = rixoNormStock(stock)
                        showMessage("Mapping row updated", "success")
                        refreshRixoMappingTreeData()
                    } else {
                        showMessage(result.message?.toString() ?: "Failed to update", "error")
                    }
                }
                .catch { err: dynamic ->
                    Logger.error("Update rixo mapping failed: ${err.toString()}")
                    showMessage("Failed to update mapping", "error")
                }
            return@treeClick
        }
        if (target.closest(".rixo-tree-leaf-edit") != null) {
            return@treeClick
        }
        val leaf = target.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement
        if (leaf != null) {
            rixoLeafInlineEditMappingId = null
            rixoLeafInlineEditLineType = ""
            clearRixoCardInlineEdit()
            clearRixoCardInlineAdd()
            val id = leaf.getAttribute("data-mapping-id")?.toLongOrNull()
            val leafType = leaf.getAttribute("data-mapping-type")?.trim()
            rixoSelectedMappingId = if (rixoSelectedMappingId == id) null else id
            rixoSelectedLeafType = if (rixoSelectedMappingId == null) null else leafType
            root.innerHTML = buildRixoMappingTreeHtmlFromCache()
            return@treeClick
        }
        val card = target.closest(".rixo-tree-card") as? HTMLElement ?: return@treeClick
        val level = card.getAttribute("data-level") ?: return@treeClick
        val value = card.getAttribute("data-value") ?: return@treeClick
        when (level) {
            "company" -> {
                rixoLeafInlineEditMappingId = null
                rixoLeafInlineEditLineType = ""
                clearRixoCardInlineEdit()
                clearRixoCardInlineAdd()
                if (rixoSelectedCompany == value) {
                    rixoSelectedCompany = null
                    rixoSelectedAuction = null
                    rixoSelectedStock = null
                    rixoSelectedMappingId = null
                    rixoSelectedLeafType = null
                } else {
                    rixoSelectedCompany = value
                    rixoSelectedAuction = null
                    rixoSelectedStock = null
                    rixoSelectedMappingId = null
                    rixoSelectedLeafType = null
                }
            }
            "auction" -> {
                rixoLeafInlineEditMappingId = null
                rixoLeafInlineEditLineType = ""
                clearRixoCardInlineEdit()
                clearRixoCardInlineAdd()
                if (rixoSelectedAuction == value) {
                    rixoSelectedAuction = null
                    rixoSelectedStock = null
                    rixoSelectedMappingId = null
                    rixoSelectedLeafType = null
                } else {
                    rixoSelectedAuction = value
                    rixoSelectedStock = null
                    rixoSelectedMappingId = null
                    rixoSelectedLeafType = null
                }
            }
            "stock" -> {
                rixoLeafInlineEditMappingId = null
                rixoLeafInlineEditLineType = ""
                clearRixoCardInlineEdit()
                clearRixoCardInlineAdd()
                rixoSelectedStock = if (rixoSelectedStock == value) null else value
                rixoSelectedMappingId = null
                rixoSelectedLeafType = null
            }
            else -> return@treeClick
        }
        root.innerHTML = buildRixoMappingTreeHtmlFromCache()
    }
    root.asDynamic().__rixoTreeClickHandler = clickHandler
    root.addEventListener("click", clickHandler)
    window.setTimeout({
        val addPriceInput = document.getElementById("rixoCardInlineAddLeafPrice") as? HTMLInputElement
        addPriceInput?.addEventListener("paste", { ev -> ev.preventDefault() })
        addPriceInput?.addEventListener("drop", { ev -> ev.preventDefault() })
        val priceInputs = document.querySelectorAll("input.rixo-tree-leaf-inline-price.money-input")
        for (i in 0 until priceInputs.length) {
            val input = priceInputs.item(i) as? HTMLInputElement ?: continue
            input.addEventListener("paste", { ev -> ev.preventDefault() })
            input.addEventListener("drop", { ev -> ev.preventDefault() })
        }
    }, 100)
}

fun loadRixoMappingTree() {
    val root = document.getElementById("rixoMappingTreeRoot") as? HTMLElement ?: return
    window.fetch(apiUrl("rixo-mapping/all"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load rixo mapping')")
        }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message || 'Failed to load rixo mapping')")
            val data = result.data
            val arr = if (js("Array.isArray(data)") as Boolean) {
                (data as Array<dynamic>).toList()
            } else {
                emptyList()
            }
            rixoSelectedCompany = null
            rixoSelectedAuction = null
            rixoSelectedStock = null
            rixoSelectedMappingId = null
            rixoSelectedLeafType = null
            rixoLeafInlineEditMappingId = null
            rixoLeafInlineEditLineType = ""
            clearRixoCardInlineEdit()
            clearRixoCardInlineAdd()
            root.innerHTML = buildRixoMappingTreeHtml(arr)
            bindRixoMappingTreeClicks(root)
            fetchRixoDistinctAuctionNames()
        }
        .catch { err: dynamic ->
            Logger.error("Rixo mapping tree: ${err.toString()}")
            root.innerHTML = """<div style="text-align:center;color:#b91c1c;padding:32px;">Failed to load rixo mapping. ${escapeHtml(err.message?.toString() ?: "")}</div>"""
        }
}

/** Reloads tree data from the server without resetting expanded company/auction/stock selection. */
private fun refreshRixoMappingTreeData() {
    val root = document.getElementById("rixoMappingTreeRoot") as? HTMLElement ?: return
    window.fetch(apiUrl("rixo-mapping/all"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load rixo mapping')")
        }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message || 'Failed to load rixo mapping')")
            val data = result.data
            val arr = if (js("Array.isArray(data)") as Boolean) {
                (data as Array<dynamic>).toList()
            } else {
                emptyList()
            }
            rixoTreeRowsCache = parseRixoMappingRows(arr)
            rixoLeafInlineEditMappingId = null
            rixoLeafInlineEditLineType = ""
            clearRixoCardInlineEdit()
            clearRixoCardInlineAdd()
            root.innerHTML = buildRixoMappingTreeHtmlFromCache()
            bindRixoMappingTreeClicks(root)
            fetchRixoDistinctAuctionNames()
        }
        .catch { err: dynamic ->
            Logger.error("Rixo mapping refresh: ${err.toString()}")
            showMessage("Failed to refresh mapping", "error")
        }
}

private fun rixoToOptions(list: List<String>): String =
    list.joinToString("") { """<option value="${escapeHtml(it)}">${escapeHtml(it)}</option>""" }

private fun populateRixoEditableComboboxOptions(selectId: String, values: List<String>) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    while (select.options.length > 1) {
        select.remove(1)
    }
    for (v in values) {
        val opt = document.createElement("option") as HTMLOptionElement
        opt.value = v
        opt.text = v
        select.add(opt)
    }
}

private fun syncRixoInlineComboboxInputs(ids: List<String>) {
    val sync = window.asDynamic().syncComboboxInput ?: return
    for (id in ids) {
        sync(id)
    }
}

/** After injecting inline-edit HTML, populate editable comboboxes (same pattern as Rixo add/edit modals). */
private fun wireRixoInlineTreeComboboxes() {
    val mid = rixoLeafInlineEditMappingId
    if (mid != null) {
        val row = rixoTreeRowsCache.firstOrNull { it.id.toLongOrNull() == mid }
        if (row != null) {
            val coId = rixoInlinePathSelectId(mid, "company")
            val auId = rixoInlinePathSelectId(mid, "auction")
            val stId = rixoInlinePathSelectId(mid, "stock")
            setEditableComboboxValue(coId, row.company.trim())
            populateRixoEditableComboboxOptions(auId, rixoMasterSuppliers)
            setEditableComboboxValue(auId, row.auction.trim())
            populateRixoEditableComboboxOptions(stId, rixoMasterStocks)
            setEditableComboboxValue(stId, row.stock.trim())
            val treeRoot = document.getElementById("rixoMappingTreeRoot") as? HTMLElement
            val rowEl = treeRoot?.querySelector(".rixo-tree-leaf-row--inline-editing") as? HTMLElement
            val strictIds = mutableListOf(auId, stId)
            if (rowEl != null) {
                val prefix = rowEl.getAttribute("data-inline-field-id")
                if (prefix != null) {
                    val typeId = "${prefix}_type"
                    populateRixoEditableComboboxOptions(typeId, rixoMasterVehicleTypes)
                    val leafType = rowEl.getAttribute("data-mapping-type")?.trim() ?: ""
                    setEditableComboboxValue(typeId, leafType)
                    strictIds.add(typeId)
                }
            }
            enforceRixoComboboxStrict(strictIds)
            syncRixoInlineComboboxInputs(strictIds)
        }
    }
    if (rixoCardInlineEditLevel != null) {
        wireRixoCardInlineCombobox()
    }
}

private fun wireRixoCardInlineCombobox() {
    val level = rixoCardInlineEditLevel ?: return
    val id = rixoCardInlineComboboxId(level)
    when (level) {
        "company" -> {
            setEditableComboboxValue(id, rixoCardInlineEditCurrentLabel)
        }
        "auction" -> {
            populateRixoEditableComboboxOptions(id, rixoMasterSuppliers)
            setEditableComboboxValue(id, rixoCardInlineEditCurrentLabel)
            enforceRixoComboboxStrict(listOf(id))
            syncRixoInlineComboboxInputs(listOf(id))
        }
        "stock" -> {
            populateRixoEditableComboboxOptions(id, rixoMasterStocks)
            setEditableComboboxValue(id, rixoCardInlineEditCurrentLabel)
            enforceRixoComboboxStrict(listOf(id))
            syncRixoInlineComboboxInputs(listOf(id))
        }
        else -> {}
    }
}

private fun cancelRixoCardInlineEdit(root: HTMLElement) {
    clearRixoCardInlineEdit()
    root.innerHTML = buildRixoMappingTreeHtmlFromCache()
    bindRixoMappingTreeClicks(root)
}

private fun executeRixoCardInlineSave(root: HTMLElement) {
    val level = rixoCardInlineEditLevel ?: return
    val pathCompany = rixoCardInlineEditCompany
    val pathAuction = rixoCardInlineEditAuction
    val pathStock = rixoCardInlineEditStock
    val currentLabel = rixoCardInlineEditCurrentLabel
    val comboboxId = rixoCardInlineComboboxId(level)
    val newVal = getEditableComboboxValue(comboboxId).trim()
    if (newVal.isEmpty()) {
        showMessage("Value is required", "error")
        return
    }
    val listOk = when (level) {
        "company" -> true
        "auction" -> rixoListContains(rixoMasterSuppliers, newVal)
        "stock" -> rixoListContains(rixoMasterStocks, newVal)
        else -> false
    }
    if (!listOk) {
        showMessage("Please select a value from the list", "error")
        return
    }
    if (newVal.equals(currentLabel, ignoreCase = true)) {
        cancelRixoCardInlineEdit(root)
        return
    }
    val rows = uniqueRowsById(rixoRowsForBranch(level, pathCompany, pathAuction, pathStock))
    if (rows.isEmpty()) {
        showMessage("No rows to update", "error")
        return
    }
    val payloadBuilder: (RixoMappingRowLite) -> dynamic = { row ->
        when (level) {
            "company" -> rixoPutPayloadFromRow(row, newCompany = newVal, pathEditOnly = true)
            "auction" -> rixoPutPayloadFromRow(row, newAuction = newVal, pathEditOnly = true)
            "stock" -> rixoPutPayloadFromRow(row, newStock = newVal, pathEditOnly = true)
            else -> rixoPutPayloadFromRow(row)
        }
    }
    runRixoPutBatchSequential(
        rows,
        payloadBuilder,
        onDone = {
            clearRixoCardInlineEdit()
            when (level) {
                "company" -> {
                    if (rixoSelectedCompany == rixoNormCompany(pathCompany)) {
                        rixoSelectedCompany = rixoNormCompany(newVal)
                    }
                }
                "auction" -> {
                    if (rixoSelectedCompany == rixoNormCompany(pathCompany) &&
                        rixoSelectedAuction == rixoNormAuction(pathAuction)
                    ) {
                        rixoSelectedAuction = rixoNormAuction(newVal)
                    }
                }
                "stock" -> {
                    if (rixoSelectedCompany == rixoNormCompany(pathCompany) &&
                        rixoSelectedAuction == rixoNormAuction(pathAuction) &&
                        rixoSelectedStock == rixoNormStock(pathStock)
                    ) {
                        rixoSelectedStock = rixoNormStock(newVal)
                    }
                }
            }
            refreshRixoMappingTreeData()
            showMessage("Updated", "success")
        },
        onFail = { msg -> showMessage(msg, "error") },
    )
}

private fun rixoListContains(list: List<String>, value: String): Boolean =
    list.any { it.equals(value.trim(), ignoreCase = true) }

private fun enforceRixoComboboxStrict(selectIds: List<String>) {
    val arr = js("[]")
    for (id in selectIds) {
        arr.push(id)
    }
    window.asDynamic().__rixoStrictIds = arr
    js("""
        (function(ids){
          function norm(v){ return (v || '').toString().trim().toUpperCase(); }
          function exists(selectId, typed){
            var sel = document.getElementById(selectId);
            if (!sel) return false;
            var needle = norm(typed);
            if (!needle) return false;
            for (var i=0;i<sel.options.length;i++){
              var opt = sel.options[i];
              var ov = norm(opt.value);
              var ot = norm(opt.text);
              if (!ov) continue;
              if (needle === ov || needle === ot) return true;
            }
            return false;
          }
          function clearIfInvalid(selectId){
            var input = document.getElementById(selectId + 'Input');
            var sel = document.getElementById(selectId);
            if (!input || !sel) return;
            var typed = (input.value || '').toString().trim();
            if (!typed) return;
            if (!exists(selectId, typed)) {
              input.value = '';
              sel.value = '';
            }
          }
          ids.forEach(function(selectId){
            var input = document.getElementById(selectId + 'Input');
            if (!input || input.__rixoStrictBound) return;
            input.__rixoStrictBound = true;
            input.addEventListener('blur', function(){ clearIfInvalid(selectId); });
            input.addEventListener('keydown', function(e){
              if (e && e.key === 'Enter') {
                e.preventDefault();
                clearIfInvalid(selectId);
              }
            });
          });
        })(window.__rixoStrictIds || []);
    """)
}

private fun fetchRixoDistinctAuctionNames() {
    window.fetch(apiUrl("rixo-mapping/distinct-auction-names"))
        .then { r: dynamic -> if (r.ok as Boolean) r.json() else js("[]") }
        .then { raw: dynamic ->
            rixoMasterSuppliers = parseMasterListArray(raw).distinct().sortedBy { it.lowercase() }
        }
        .catch { err: dynamic ->
            Logger.error("Rixo distinct auction names: ${err.toString()}")
        }
}

private fun rixoEnsureMasterOptions(callback: (Boolean) -> Unit) {
    if (rixoMasterOptionsReady) {
        callback(true)
        return
    }
    val requests = js("[]")
    requests.push(window.fetch(apiUrl("master-menu/rixo_company")))
    requests.push(window.fetch(apiUrl("rixo-mapping/distinct-auction-names")))
    requests.push(window.fetch(apiUrl("master-menu/stock_location")))
    requests.push(window.fetch(apiUrl("master-menu/type_of_vehicle")))
    js("Promise.all")(requests)
        .then { responses: dynamic ->
            val parsePromises = js("[]")
            for (i in 0 until 4) {
                val resp = responses[i]
                parsePromises.push(if (resp.ok as Boolean) resp.json() else js("Promise.resolve([])"))
            }
            js("Promise.all")(parsePromises)
        }
        .then { results: dynamic ->
            rixoMasterCompanies = parseMasterListArray(results[0]).distinct().sortedBy { it.lowercase() }
            rixoMasterSuppliers = parseMasterListArray(results[1]).distinct().sortedBy { it.lowercase() }
            rixoMasterStocks = parseMasterListArray(results[2]).distinct().sortedBy { it.lowercase() }
            rixoMasterVehicleTypes = parseMasterListArray(results[3]).distinct().sortedBy { it.lowercase() }
            rixoMasterOptionsReady = true
            callback(true)
        }
        .catch { err: dynamic ->
            Logger.error("Failed loading Rixo master options: ${err.toString()}")
            showMessage("Failed to load master options", "error")
            callback(false)
        }
}

private fun rixoFirstNonBlank(vararg candidates: String?): String {
    for (c in candidates) {
        val t = c?.trim().orEmpty()
        if (t.isNotEmpty()) return t
    }
    return ""
}

/**
 * @param insertMode null or "FULL" = complete row; "COMPANY" / "AUCTION" / "STOCK" = skeleton rows (backend clears unset columns).
 */
private fun postRixoMappingBulkOneRow(
    insertMode: String?,
    company: String,
    auction: String,
    stock: String,
    vtype: String,
    price: String,
    onSuccess: () -> Unit,
) {
    val mode = insertMode ?: "FULL"
    val c = company.trim()
    val a = auction.trim()
    val s = stock.trim()
    val t = vtype.trim()
    val p = price.trim()
    val obj: dynamic = js("{}")
    when (mode) {
        "COMPANY" -> {
            if (c.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return
            }
            obj.rixoCompany = c
            obj.insertMode = "COMPANY"
            obj.auctionName = null
            obj.stockLocation = ""
            obj.supportedVehicleType = null
            obj.rixoPrice = null
        }
        "AUCTION" -> {
            // Company comes from the tree (existingrixo_mapping rows); it may not be in master-menu/rixo_company.
            if (c.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return
            }
            if (a.isEmpty() || !rixoListContains(rixoMasterSuppliers, a)) {
                showMessage("Please select an auction house from the list", "error")
                return
            }
            obj.rixoCompany = c
            obj.insertMode = "AUCTION"
            obj.auctionName = a
            obj.stockLocation = ""
            obj.supportedVehicleType = null
            obj.rixoPrice = null
        }
        "STOCK" -> {
            if (c.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return
            }
            if (a.isEmpty() || !rixoListContains(rixoMasterSuppliers, a)) {
                showMessage("Please select an auction house from the list", "error")
                return
            }
            if (s.isEmpty() || !rixoListContains(rixoMasterStocks, s)) {
                showMessage("Please select a stock location from the list", "error")
                return
            }
            obj.rixoCompany = c
            obj.insertMode = "STOCK"
            obj.auctionName = a
            obj.stockLocation = s
            obj.supportedVehicleType = null
            obj.rixoPrice = null
        }
        else -> {
            if (c.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return
            }
            if (!rixoListContains(rixoMasterSuppliers, a)) {
                showMessage("Please select an auction house from the list", "error")
                return
            }
            if (!rixoListContains(rixoMasterStocks, s)) {
                showMessage("Please select a stock location from the list", "error")
                return
            }
            if (!rixoListContains(rixoMasterVehicleTypes, t)) {
                showMessage("Please select a supported vehicle type from the list", "error")
                return
            }
            if (p.isNotEmpty() && parseMoneyNumericInput(p) == null) {
                showMessage("Rixo price must be numeric", "error")
                return
            }
            obj.rixoCompany = c
            obj.auctionName = a
            obj.stockLocation = s
            obj.supportedVehicleType = t
            obj.rixoPrice = p
        }
    }
    val mergeId = when (mode) {
        "AUCTION" -> rixoMergeRowIdForAuction(c)
        "STOCK" -> rixoMergeRowIdForStock(c, a)
        "FULL" -> rixoMergeRowIdForLeaf(c, a, s)
        else -> null
    }
    // Kotlin/JS Long serializes as a non-JSON-number object; Jackson expects a numeric id.
    if (mergeId != null) {
        obj.id = mergeId.toDouble()
    }
    val payload = js("{}")
    val rowList = mutableListOf<dynamic>()
    rowList.add(obj)
    payload.rows = rowList.toTypedArray()
    window.fetch(apiUrl("rixo-mapping/bulk"), js("""{ method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
        .then { resp: dynamic ->
            resp.json().then { result: dynamic ->
                val pair = js("{}")
                pair.resp = resp
                pair.result = result
                pair
            }
        }
        .then { pair: dynamic ->
            val resp = pair.resp
            val result = pair.result
            if (resp.ok && (result.success as? Boolean == true)) {
                onSuccess()
                showMessage(result.message?.toString() ?: "Mapping added", "success")
            } else {
                showMessage(result.message?.toString() ?: "Failed to add mapping", "error")
            }
        }
        .catch { err: dynamic ->
            Logger.error("Rixo level add failed: ${err.toString()}")
            showMessage("Failed to add mapping", "error")
        }
}

private fun startRixoInlineAdd(level: String, pathCompany: String, pathAuction: String, pathStock: String, root: HTMLElement) {
    rixoLeafInlineEditMappingId = null
    rixoLeafInlineEditLineType = ""
    clearRixoCardInlineEdit()
    when (level) {
        "company" -> Unit
        "auction" -> {
            if (pathCompany.isEmpty()) {
                showMessage("Company context is required. Expand a company in the tree first.", "error")
                return
            }
            rixoSelectedCompany = rixoNormCompany(pathCompany)
        }
        "stock" -> {
            if (pathCompany.isEmpty() || pathAuction.isEmpty()) {
                showMessage("Company and auction house context are required.", "error")
                return
            }
            rixoSelectedCompany = rixoNormCompany(pathCompany)
            rixoSelectedAuction = rixoNormAuction(pathAuction)
        }
        "leaf" -> {
            if (pathCompany.isEmpty() || pathAuction.isEmpty() || pathStock.isEmpty()) {
                showMessage("Open company, auction, and stock to add a vehicle type and price.", "error")
                return
            }
            rixoSelectedCompany = rixoNormCompany(pathCompany)
            rixoSelectedAuction = rixoNormAuction(pathAuction)
            rixoSelectedStock = rixoNormStock(pathStock)
        }
        else -> return
    }
    rixoCardInlineAddLevel = level
    rixoCardInlineAddCompany = pathCompany.trim()
    rixoCardInlineAddAuction = pathAuction.trim()
    rixoCardInlineAddStock = pathStock.trim()
    rixoEnsureMasterOptions { ok ->
        if (!ok) {
            clearRixoCardInlineAdd()
            return@rixoEnsureMasterOptions
        }
        root.innerHTML = buildRixoMappingTreeHtmlFromCache()
        bindRixoMappingTreeClicks(root)
        window.setTimeout({
            wireRixoInlineAddComboboxes()
        }, 0)
    }
}

private fun wireRixoInlineAddComboboxes() {
    when (rixoCardInlineAddLevel) {
        "company" -> setEditableComboboxValue("rixoCardInlineAddCompany", "")
        "auction" -> {
            populateRixoEditableComboboxOptions("rixoCardInlineAddAuction", rixoMasterSuppliers)
            setEditableComboboxValue("rixoCardInlineAddAuction", "")
            enforceRixoComboboxStrict(listOf("rixoCardInlineAddAuction"))
            syncRixoInlineComboboxInputs(listOf("rixoCardInlineAddAuction"))
        }
        "stock" -> {
            populateRixoEditableComboboxOptions("rixoCardInlineAddStock", rixoMasterStocks)
            setEditableComboboxValue("rixoCardInlineAddStock", "")
            enforceRixoComboboxStrict(listOf("rixoCardInlineAddStock"))
            syncRixoInlineComboboxInputs(listOf("rixoCardInlineAddStock"))
        }
        "leaf" -> {
            populateRixoEditableComboboxOptions("rixoCardInlineAddLeafType", rixoMasterVehicleTypes)
            setEditableComboboxValue("rixoCardInlineAddLeafType", "")
            enforceRixoComboboxStrict(listOf("rixoCardInlineAddLeafType"))
            syncRixoInlineComboboxInputs(listOf("rixoCardInlineAddLeafType"))
            (document.getElementById("rixoCardInlineAddLeafPrice") as? HTMLInputElement)?.value = ""
        }
        else -> Unit
    }
}

private fun executeRixoCardInlineAddSave() {
    when (rixoCardInlineAddLevel) {
        "company" -> {
            val company = getEditableComboboxValue("rixoCardInlineAddCompany")
            if (company.isEmpty()) {
                showMessage("Rixo company is required", "error")
                return
            }
            postRixoMappingBulkOneRow("COMPANY", company, "", "", "", "") {
                clearRixoCardInlineAdd()
                refreshRixoMappingTreeData()
            }
        }
        "auction" -> {
            val company = rixoFirstNonBlank(rixoCardInlineAddCompany, rixoSelectedCompany)
            val auction = getEditableComboboxValue("rixoCardInlineAddAuction")
            if (company.isEmpty()) {
                showMessage("Company context is required.", "error")
                return
            }
            if (auction.isEmpty()) {
                showMessage("Auction house is required", "error")
                return
            }
            postRixoMappingBulkOneRow("AUCTION", company, auction, "", "", "") {
                clearRixoCardInlineAdd()
                refreshRixoMappingTreeData()
            }
        }
        "stock" -> {
            val company = rixoFirstNonBlank(rixoCardInlineAddCompany, rixoSelectedCompany)
            val auction = rixoFirstNonBlank(rixoCardInlineAddAuction, rixoSelectedAuction)
            val stock = getEditableComboboxValue("rixoCardInlineAddStock")
            if (company.isEmpty() || auction.isEmpty()) {
                showMessage("Company and auction house context are required.", "error")
                return
            }
            if (stock.isEmpty()) {
                showMessage("Stock location is required", "error")
                return
            }
            postRixoMappingBulkOneRow("STOCK", company, auction, stock, "", "") {
                clearRixoCardInlineAdd()
                refreshRixoMappingTreeData()
            }
        }
        else -> Unit
    }
}

private fun executeRixoLeafInlineAddSave() {
    if (rixoCardInlineAddLevel != "leaf") return
    val company = rixoFirstNonBlank(rixoCardInlineAddCompany, rixoSelectedCompany)
    val auction = rixoFirstNonBlank(rixoCardInlineAddAuction, rixoSelectedAuction)
    val stock = rixoFirstNonBlank(rixoCardInlineAddStock, rixoSelectedStock)
    val vtype = getEditableComboboxValue("rixoCardInlineAddLeafType")
    val priceRaw = (document.getElementById("rixoCardInlineAddLeafPrice") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (company.isEmpty() || auction.isEmpty() || stock.isEmpty()) {
        showMessage("Missing company, auction, or stock context for this row.", "error")
        return
    }
    if (priceRaw.isEmpty() || priceRaw.toDoubleOrNull() == null) {
        showMessage("Enter a numeric Rixo price", "error")
        return
    }
    postRixoMappingBulkOneRow(null, company, auction, stock, vtype, priceRaw) {
        clearRixoCardInlineAdd()
        refreshRixoMappingTreeData()
    }
}

private fun closeAllRixoCardMenus(root: HTMLElement) {
    val list = root.querySelectorAll(".rixo-tree-card-menu-wrap.is-open")
    val len = list.asDynamic().length as Int
    var i = 0
    while (i < len) {
        val el = list.asDynamic()[i] as? HTMLElement
        el?.classList?.remove("is-open")
        i++
    }
}

private fun rixoRowsForBranch(level: String, pathCompany: String, pathAuction: String, pathStock: String): List<RixoMappingRowLite> {
    return when (level) {
        "company" -> rixoTreeRowsCache.filter { rixoNormCompany(it.company) == rixoNormCompany(pathCompany) }
        "auction" -> rixoTreeRowsCache.filter {
            rixoNormCompany(it.company) == rixoNormCompany(pathCompany) &&
                rixoNormAuction(it.auction) == rixoNormAuction(pathAuction)
        }
        "stock" -> rixoTreeRowsCache.filter {
            rixoNormCompany(it.company) == rixoNormCompany(pathCompany) &&
                rixoNormAuction(it.auction) == rixoNormAuction(pathAuction) &&
                rixoNormStock(it.stock) == rixoNormStock(pathStock)
        }
        else -> emptyList()
    }
}

private fun uniqueRowsById(rows: List<RixoMappingRowLite>): List<RixoMappingRowLite> =
    rows.distinctBy { it.id }

/**
 * @param pathEditOnly When true (inline edit of company / auction / stock card only), omit vehicle
 * type and price from the JSON body so the server merges from DB and does not re-validate unrelated
 * columns (e.g. non-numeric legacy price text).
 */
private fun rixoPutPayloadFromRow(
    row: RixoMappingRowLite,
    newCompany: String? = null,
    newAuction: String? = null,
    newStock: String? = null,
    pathEditOnly: Boolean = false,
): dynamic {
    val p = js("{}")
    p.rixoCompany = (newCompany ?: row.company).trim()
    val a = (newAuction ?: row.auction).trim()
    // Plain js("{}") objects are dynamic; do not use .asDynamic() here (runtime: asDynamic is not a function).
    p.auctionName = if (a.isEmpty()) null else a
    p.stockLocation = (newStock ?: row.stock).trim()
    if (!pathEditOnly) {
        p.supportedVehicleType = row.vType?.trim().orEmpty()
        p.rixoPrice = row.price?.trim().orEmpty()
    }
    return p
}

private fun runRixoPutBatchSequential(
    rows: List<RixoMappingRowLite>,
    payloadForRow: (RixoMappingRowLite) -> dynamic,
    onDone: () -> Unit,
    onFail: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        onDone()
        return
    }
    val row = rows.first()
    val rest = rows.drop(1)
    val id = row.id.toLongOrNull()
    if (id == null) {
        runRixoPutBatchSequential(rest, payloadForRow, onDone, onFail)
        return
    }
    val payload = payloadForRow(row)
    window.fetch(apiUrl("rixo-mapping/$id"), js("""{ method:'PUT', headers:{'Content-Type':'application/json'}, body: JSON.stringify(payload) }"""))
        .then { resp: dynamic ->
            resp.json().then { result: dynamic ->
                val pair = js("{}")
                pair.resp = resp
                pair.result = result
                pair
            }
        }
        .then { pair: dynamic ->
            val resp = pair.resp
            val result = pair.result
            if (resp.ok && (result.success as? Boolean == true)) {
                runRixoPutBatchSequential(rest, payloadForRow, onDone, onFail)
            } else {
                onFail(result.message?.toString() ?: "Failed to update")
            }
        }
        .catch { err: dynamic ->
            onFail(err.toString())
        }
}

private fun runRixoDeleteBatchSequential(
    ids: List<Long>,
    onDone: () -> Unit,
    onFail: (String) -> Unit,
) {
    if (ids.isEmpty()) {
        onDone()
        return
    }
    val id = ids.first()
    val rest = ids.drop(1)
    window.fetch(apiUrl("rixo-mapping/$id"), js("""{ method:'DELETE', headers:{'Content-Type':'application/json'} }"""))
        .then { resp: dynamic ->
            resp.json().then { result: dynamic ->
                val pair = js("{}")
                pair.resp = resp
                pair.result = result
                pair
            }
        }
        .then { pair: dynamic ->
            val resp = pair.resp
            val result = pair.result
            if (resp.ok && (result.success as? Boolean == true)) {
                runRixoDeleteBatchSequential(rest, onDone, onFail)
            } else {
                onFail(result.message?.toString() ?: "Failed to delete")
            }
        }
        .catch { err: dynamic ->
            onFail(err.toString())
        }
}

private fun branchDeleteConfirmMessage(level: String, label: String): String {
    val safe = label.ifBlank { "this item" }
    return when (level) {
        "company" -> "Delete all mappings under company \"$safe\"? This cannot be undone."
        "auction" -> "Delete all mappings under auction \"$safe\" and everything below it? This cannot be undone."
        "stock" -> "Delete all mappings for stock \"$safe\" (including vehicle types and prices)? This cannot be undone."
        else -> "Delete this branch?"
    }
}

private fun executeRixoBranchDelete(level: String, pathCompany: String, pathAuction: String, pathStock: String, currentLabel: String) {
    val rows = uniqueRowsById(rixoRowsForBranch(level, pathCompany, pathAuction, pathStock))
    if (rows.isEmpty()) {
        showMessage("Nothing to delete", "error")
        return
    }
    if (!window.confirm(branchDeleteConfirmMessage(level, currentLabel))) return
    val ids = rows.mapNotNull { it.id.toLongOrNull() }.distinct()
    runRixoDeleteBatchSequential(ids, {
        if (level == "company" && rixoSelectedCompany == rixoNormCompany(pathCompany)) {
            rixoSelectedCompany = null
            rixoSelectedAuction = null
            rixoSelectedStock = null
            rixoSelectedMappingId = null
            rixoSelectedLeafType = null
        }
        if (level == "auction" &&
            rixoSelectedCompany == rixoNormCompany(pathCompany) &&
            rixoSelectedAuction == rixoNormAuction(pathAuction)
        ) {
            rixoSelectedAuction = null
            rixoSelectedStock = null
            rixoSelectedMappingId = null
            rixoSelectedLeafType = null
        }
        if (level == "stock" &&
            rixoSelectedCompany == rixoNormCompany(pathCompany) &&
            rixoSelectedAuction == rixoNormAuction(pathAuction) &&
            rixoSelectedStock == rixoNormStock(pathStock)
        ) {
            rixoSelectedStock = null
            rixoSelectedMappingId = null
            rixoSelectedLeafType = null
        }
        rixoLeafInlineEditMappingId = null
        rixoLeafInlineEditLineType = ""
        clearRixoCardInlineEdit()
        refreshRixoMappingTreeData()
        showMessage("Branch deleted", "success")
    }, { msg -> showMessage(msg, "error") })
}

/**
 * Check if device type changed for Supplier page and reload if needed
 */
fun checkSupplierDeviceChange() {
    val currentDeviceType = getDeviceType()
    
    // If device changed, reload suppliers to switch between card/table views
    if (lastSupplierDeviceType != null && lastSupplierDeviceType != currentDeviceType) {
        Logger.debug("Supplier page: Device type changed from $lastSupplierDeviceType to $currentDeviceType, reloading suppliers")
        loadMasterSuppliers()
    }
    
    // Update last device type
    lastSupplierDeviceType = currentDeviceType
}

/**
 * Setup window resize listener for Supplier page to detect device changes
 */
fun setupSupplierDeviceChangeListener() {
    // Remove existing listener if any (to avoid duplicates)
    val existingListener = window.asDynamic().__supplierDeviceChangeListener
    if (existingListener != null) {
        val listenerFunc = existingListener.unsafeCast<((Event) -> Unit)?>()
        window.removeEventListener("resize", listenerFunc)
    }
    
    // Debounce resize events
    var resizeTimeout: dynamic = null
    val resizeListener: (Event) -> Unit = { _: Event ->
        if (resizeTimeout != null) {
            window.clearTimeout(resizeTimeout)
        }
        resizeTimeout = window.setTimeout({
            // Check if device type actually changed
            val newDeviceType = getDeviceType()
            if (lastSupplierDeviceType != null && lastSupplierDeviceType != newDeviceType) {
                // Device changed - reload suppliers to switch between card/table views
                Logger.debug("Supplier page: Device type changed from $lastSupplierDeviceType to $newDeviceType, reloading")
                
                // If we're on the supplier page, reload to show correct view (cards or table)
                if (window.location.hash.contains("#/master/supplier")) {
                    loadMasterSuppliers()
                }
            }
            lastSupplierDeviceType = newDeviceType
        }, 300) // 300ms debounce
    }
    
    // Store listener reference
    window.asDynamic().__supplierDeviceChangeListener = resizeListener
    
    // Add event listener
    window.addEventListener("resize", resizeListener)
}

fun loadMasterSuppliers() {
    val treeRoot = document.getElementById("supplierMapTreeRoot") as? HTMLElement
    if (treeRoot != null) {
        loadSupplierMapTree()
        return
    }

    val tableDiv = document.getElementById("supplierTable") as? HTMLElement ?: return
    
    val deviceType = getDeviceType()
    
    // Use card layout for mobile, table for tablet/desktop
    if (deviceType == "mobile") {
        loadMasterSuppliersWithCards()
        return
    }
    
    loadMasterSuppliersWithTable()
}

/**
 * Renders Supplier Map table + pagination. [isServerSearch] uses [supplierMapSearchPageZeroBased] / totals from API.
 */
private fun buildSupplierTableUi(
    tableDiv: HTMLElement,
    paginatedPrices: List<dynamic>,
    orderedForDisplay: List<dynamic>,
    filterLabel: String,
    totalPages: Int,
    isServerSearch: Boolean,
    footerStart: Int,
    footerEnd: Int,
) {
    val supplierSortable = setOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
    if (orderedForDisplay.isEmpty()) {
        val message = if (filterLabel.isNotEmpty()) {
            "No supplier data found for: $filterLabel"
        } else {
            "No supplier data found."
        }
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search or filter</div>
            </div>
        """
        return
    }

    if (paginatedPrices.isEmpty()) {
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                <div style="font-size: 16px; margin-bottom: 8px;">No rows on this page.</div>
            </div>
        """
        return
    }

    val selectedColumns = getSelectedSupplierColumns()
    val columnLabels = mapOf(
        "supplierName" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "rixoCompany" to "Rixo Company",
        "venueId" to "Venue ID",
        "pol" to "POL"
    )

    val supplierColCount = 1 + selectedColumns.size
    var html = """
                <div style="overflow-x: auto; border-radius: 10px; background: #fff; box-shadow: 0 1px 2px rgba(0,0,0,0.04);">
                    <table style="width: 100%; border-collapse: collapse; table-layout: fixed;" class="supplier-table">${htmlTableColgroupNarrowActionEqualRest(supplierColCount)}
                        <thead>
                            <tr>
                                <th style="padding: 12px 14px; text-align: left; min-width: 72px;"></th>
            """

    for (columnKey in selectedColumns) {
        val label = columnLabels[columnKey] ?: columnKey
        val thBase = """padding: 12px 16px; text-align: left; font-weight: 700; color: #111827; font-size: 13px; letter-spacing: 0.02em"""
        html += if (columnKey in supplierSortable) {
            val tip = masterMapColumnSortTooltip(supplierMapSortOrderByField, columnKey)
            val bid = "supplierMapSort_$columnKey"
            """<th style="$thBase"><button type="button" id="$bid" title="$tip" style="background: none; border: none; cursor: pointer; font-weight: 700; color: #111827; padding: 0; display: inline-flex; align-items: center; gap: 6px;"><span>$label</span><span style="font-size: 14px;">↕</span></button></th>"""
        } else {
            """<th style="$thBase">$label</th>"""
        }
    }

    html += """
                            </tr>
                        </thead>
                        <tbody>
            """

    for (price in paginatedPrices) {
        val id = (price.id ?: "").toString()
        val supplierName = (price.auctionHouse ?: "").toString()
        val stockLocation = (price.stockLocation ?: "").toString()
        val rixoCompany = (price.rixoCompany ?: "").toString()
        val venueId = (price.venueId ?: "").toString()
        val pol = (price.pol ?: "").toString()

        val rowDataJs = "window.__supplierRowData={supplierName:'${escapeJsString(supplierName)}',stockLocation:'${escapeJsString(stockLocation)}',rixoCompany:'${escapeJsString(rixoCompany)}',venueId:'${escapeJsString(venueId)}',pol:'${escapeJsString(pol)}'};"

        html += """
                    <tr>
                        <td style="padding: 10px 12px;">
                            <div style="display:flex; gap:6px; align-items:center;">
                            <button onclick="$rowDataJs window.editMasterSupplier($id)" aria-label="Edit" title="Edit"
                                    style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(76,201,255,0.30);">
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                    <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                </svg>
                            </button>
                                <button onclick="$rowDataJs window.duplicateMasterSupplier($id)" aria-label="Duplicate" title="Duplicate"
                                        style="width: 28px; height: 28px; display:inline-flex; align-items:center; justify-content:center; background-color:#3b82f6; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 6px rgba(59,130,246,0.30);">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
                                    </svg>
                                </button>
                            </div>
                        </td>
                """

        for (columnKey in selectedColumns) {
            val value = when (columnKey) {
                "supplierName" -> supplierName
                "stockLocation" -> stockLocation
                "rixoCompany" -> rixoCompany
                "venueId" -> venueId
                "pol" -> pol
                else -> ""
            }
            val cellStyle = when (columnKey) {
                "venueId", "pol" -> "padding: 12px 16px; color: #374151; font-size: 14px; vertical-align: top;"
                "supplierName" -> "padding: 12px 16px; color: #111827; font-size: 14px; font-weight: 500; vertical-align: top;"
                else -> "padding: 12px 16px; color: #111827; font-size: 14px; vertical-align: top;"
            }
            val cellInner = formatSupplierMapValueChipHtml(value)
            html += """<td style="$cellStyle">$cellInner</td>"""
        }

        html += """</tr>"""
    }

    html += """
                        </tbody>
                    </table>
                </div>
            """

    val footerSummary = if (isServerSearch) {
        "Page $suppliersCurrentPage of $totalPages (search) · ${supplierMapSearchTotal} matching row(s) · ${paginatedPrices.size} supplier group(s) on this page"
    } else {
        "Showing $footerStart to $footerEnd of ${orderedForDisplay.size} supplier${if (orderedForDisplay.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"
    }

    if (totalPages > 1) {
        html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            $footerSummary
                        </div>
                        <div class="supplier-pagination-controls">
                            <button id="suppliersPrevPage" class="supplier-pagination-btn" ${if (suppliersCurrentPage == 1) "disabled" else ""}>
                                Previous
                            </button>
                            <span class="supplier-pagination-page">Page $suppliersCurrentPage of $totalPages</span>
                            <button id="suppliersNextPage" class="supplier-pagination-btn" ${if (suppliersCurrentPage >= totalPages) "disabled" else ""}>
                                Next
                            </button>
                        </div>
                    </div>
                """
    } else {
        html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        ${if (isServerSearch) footerSummary else "Total: ${orderedForDisplay.size} supplier${if (orderedForDisplay.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"}
                    </div>
                """
    }

    tableDiv.innerHTML = html

    document.getElementById("suppliersPrevPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (supplierMapSearchPageZeroBased > 0) {
                supplierMapSearchPageZeroBased--
                suppliersCurrentPage = supplierMapSearchPageZeroBased + 1
                loadMasterSuppliers()
            }
        } else if (suppliersCurrentPage > 1) {
            suppliersCurrentPage--
            loadMasterSuppliers()
        }
    })

    document.getElementById("suppliersNextPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (supplierMapSearchPageZeroBased < supplierMapSearchTotalPages - 1) {
                supplierMapSearchPageZeroBased++
                suppliersCurrentPage = supplierMapSearchPageZeroBased + 1
                loadMasterSuppliers()
            }
        } else {
            val tp = kotlin.math.ceil(allSuppliers.size.toDouble() / suppliersItemsPerPage).toInt()
            if (suppliersCurrentPage < tp) {
                suppliersCurrentPage++
                loadMasterSuppliers()
            }
        }
    })
    val supplierSortKeys = listOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
    for (key in supplierSortKeys) {
        if (key in selectedColumns) {
            document.getElementById("supplierMapSort_$key")?.addEventListener("click", { _: Event ->
                toggleSupplierMapSort(key)
            })
        }
    }
}

fun loadMasterSuppliersWithCards() {
    val tableDiv = document.getElementById("supplierTable") as? HTMLElement ?: return
    
    val searchQ = getSupplierMapSearchQuery()
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading supplier data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    if (searchQ.isNotEmpty()) {
        supplierMapSearchServerMode = true
        val encQ = js("encodeURIComponent")(searchQ).unsafeCast<String>()
        val encF = js("encodeURIComponent")(supplierMapSearchFieldChoice).unsafeCast<String>()
        val p = supplierMapSearchPageZeroBased
        val url = apiUrl("rixo/prices/page-search?q=$encQ&field=$encF&page=$p&size=$suppliersItemsPerPage")
        window.fetch(url)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Search failed')")
            }
            .then { body: dynamic ->
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) throw js("Error(err)")
                val totalEl = js("body.totalElements")
                supplierMapSearchTotal = when (totalEl) {
                    is Number -> totalEl.toLong()
                    else -> totalEl?.toString()?.toLongOrNull() ?: 0L
                }
                val tp = js("body.totalPages")
                supplierMapSearchTotalPages = kotlin.math.max(1, when (tp) {
                    is Number -> tp.toInt()
                    else -> tp?.toString()?.toIntOrNull() ?: 1
                })
                val num = js("body.page")
                supplierMapSearchPageZeroBased = when (num) {
                    is Number -> num.toInt()
                    else -> num?.toString()?.toIntOrNull() ?: 0
                }
                suppliersCurrentPage = supplierMapSearchPageZeroBased + 1

                val content = js("body.content") ?: js("[]")
                val pricesArray = js("Array.isArray(content) ? content : []") as Array<dynamic>
                val grouped = groupSupplierPricesForView(pricesArray.toList())
                allSuppliers = grouped
                displaySuppliersAsCards(grouped, searchQ, true)
            }
            .catch { error: dynamic ->
                Logger.error("Error searching suppliers: ${error.toString()}")
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading supplier data</div>
                        <div style="font-size: 14px; color: #9ca3af;">${error.asDynamic().message}</div>
                    </div>
                """
            }
        return
    }

    supplierMapSearchServerMode = false
    supplierMapSearchTotal = 0
    supplierMapSearchTotalPages = 0
    supplierMapSearchPageZeroBased = 0

    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load suppliers')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load suppliers')")
            }
            
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            
            val pricesList = pricesArray.toList()
            val sortedPrices = pricesList.sortedByDescending { price ->
                val id = price.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            val groupedPrices = groupSupplierPricesForView(sortedPrices)
            val supplierSortable = setOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
            var orderedForDisplay = groupedPrices
            val smsf = supplierMapSortField
            if (smsf != null && smsf in supplierSortable) {
                val ord = supplierMapSortOrderByField[smsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    groupedPrices.sortedBy { extractSupplierSortKey(it, smsf) }
            } else {
                    groupedPrices.sortedByDescending { extractSupplierSortKey(it, smsf) }
            }
            }
            
            allSuppliers = orderedForDisplay
            displaySuppliersAsCards(orderedForDisplay, "", false)
        }
        .catch { error: dynamic ->
            Logger.error("Error loading suppliers: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading supplier data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun displaySuppliersAsCards(filteredPrices: List<dynamic>, filterLabel: String, isServerSearch: Boolean = false) {
    val tableDiv = document.getElementById("supplierTable")
    if (tableDiv == null) return
    
    if (filteredPrices.isEmpty()) {
        val message = if (filterLabel.isNotEmpty()) {
            "No supplier data found for: $filterLabel"
        } else {
            "No supplier data found."
        }
        tableDiv.innerHTML = """
            <div style="text-align: center; color: #666; padding: 40px;">
                $message
            </div>
        """
        return
    }
    
    val totalPages = if (isServerSearch) {
        kotlin.math.max(1, supplierMapSearchTotalPages)
    } else {
        kotlin.math.max(1, kotlin.math.ceil(filteredPrices.size.toDouble() / suppliersItemsPerPage).toInt())
    }
    val paginatedPrices = if (isServerSearch) {
        filteredPrices
    } else {
    val startIndex = (suppliersCurrentPage - 1) * suppliersItemsPerPage
    val endIndex = kotlin.math.min(startIndex + suppliersItemsPerPage, filteredPrices.size)
        filteredPrices.subList(startIndex, endIndex)
    }
    
    val selectedColumns = getSelectedSupplierColumns()
    val columnLabels = mapOf(
        "supplierName" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "rixoCompany" to "Rixo Company",
        "venueId" to "Venue ID",
        "pol" to "POL"
    )
    
    val cardsHTML = StringBuilder()
    cardsHTML.append("""<div class="supplier-cards-container">""")
    
    for (price in paginatedPrices) {
        val id = (price.id ?: "").toString()
        val supplierName = (price.auctionHouse ?: "").toString()
        val stockLocation = (price.stockLocation ?: "").toString()
        val rixoCompany = (price.rixoCompany ?: "").toString()
        val venueId = (price.venueId ?: "").toString()
        val pol = (price.pol ?: "").toString()

        val rowDataJs = "window.__supplierRowData={supplierName:'${escapeJsString(supplierName)}',stockLocation:'${escapeJsString(stockLocation)}',rixoCompany:'${escapeJsString(rixoCompany)}',venueId:'${escapeJsString(venueId)}',pol:'${escapeJsString(pol)}'};"
        
        // Build card content based on selected columns
        val cardFields = StringBuilder()
        for (columnKey in selectedColumns) {
            val label = columnLabels[columnKey] ?: columnKey
            val value = when (columnKey) {
                "supplierName" -> supplierName
                "stockLocation" -> stockLocation
                "rixoCompany" -> rixoCompany
                "venueId" -> venueId
                "pol" -> pol
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                val displayValue = formatSupplierMapValueChipHtml(value)
                cardFields.append("""
                    <div style="margin-bottom: 8px;">
                        <span style="font-weight: 600; color: #666; font-size: 12px; text-transform: uppercase; letter-spacing: 0.5px;">$label:</span>
                        <div style="color: #333; font-size: 14px; margin-top: 2px;">$displayValue</div>
                    </div>
                """)
            }
        }
        
        cardsHTML.append("""
            <div class="supplier-card">
                <div class="card-header">
                    <div style="display:flex; gap:6px; align-items:center;">
                    <button class="card-edit-btn" onclick="$rowDataJs window.editMasterSupplier($id)" aria-label="Edit" title="Edit">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                            <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                        </svg>
                    </button>
                        <button class="card-edit-btn" onclick="$rowDataJs window.duplicateMasterSupplier($id)" aria-label="Duplicate" title="Duplicate" style="background-color:#3b82f6;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z" fill="white"/>
                            </svg>
                        </button>
                    </div>
                    <div class="card-title">${if (supplierName.isNotEmpty()) supplierName else "Supplier #$id"}</div>
                </div>
                <div class="card-body">
                    $cardFields
                </div>
            </div>
        """)
    }
    
    cardsHTML.append("</div>")
    
    // Add pagination controls
    if (totalPages > 1) {
        cardsHTML.append("""
            <div class="pagination-controls">
                <button id="suppliersPrevPage" class="pagination-btn" ${if (suppliersCurrentPage == 1) "disabled" else ""}>
                    Previous
                </button>
                <span class="pagination-page">Page $suppliersCurrentPage of $totalPages</span>
                <button id="suppliersNextPage" class="pagination-btn" ${if (suppliersCurrentPage >= totalPages) "disabled" else ""}>
                    Next
                </button>
            </div>
        """)
    } else {
        val summary = if (isServerSearch) {
            "Page $suppliersCurrentPage of $totalPages (search) · ${supplierMapSearchTotal} matching row(s) · ${paginatedPrices.size} group(s) on this page"
        } else {
            "Total: ${filteredPrices.size} supplier${if (filteredPrices.size != 1) "s" else ""}${if (filterLabel.isNotEmpty()) " (filtered)" else ""}"
        }
        cardsHTML.append("""
            <div style="padding: 16px; text-align: center; color: #6b7280; font-size: 14px;">
                $summary
            </div>
        """)
    }
    
    tableDiv.innerHTML = cardsHTML.toString()
    
    // Add pagination event listeners
    document.getElementById("suppliersPrevPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (supplierMapSearchPageZeroBased > 0) {
                supplierMapSearchPageZeroBased--
                suppliersCurrentPage = supplierMapSearchPageZeroBased + 1
                loadMasterSuppliers()
            }
        } else if (suppliersCurrentPage > 1) {
            suppliersCurrentPage--
            loadMasterSuppliers()
        }
    })
    
    document.getElementById("suppliersNextPage")?.addEventListener("click", { _: Event ->
        if (isServerSearch) {
            if (supplierMapSearchPageZeroBased < supplierMapSearchTotalPages - 1) {
                supplierMapSearchPageZeroBased++
                suppliersCurrentPage = supplierMapSearchPageZeroBased + 1
                loadMasterSuppliers()
            }
        } else {
            val tp = kotlin.math.ceil(allSuppliers.size.toDouble() / suppliersItemsPerPage).toInt()
            if (suppliersCurrentPage < tp) {
            suppliersCurrentPage++
            loadMasterSuppliers()
            }
        }
    })
}

fun loadMasterSuppliersWithTable() {
    val tableDiv = document.getElementById("supplierTable") as? HTMLElement ?: return
    
    val searchQ = getSupplierMapSearchQuery()
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading supplier data...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    if (searchQ.isNotEmpty()) {
        supplierMapSearchServerMode = true
        val encQ = js("encodeURIComponent")(searchQ).unsafeCast<String>()
        val encF = js("encodeURIComponent")(supplierMapSearchFieldChoice).unsafeCast<String>()
        val p = supplierMapSearchPageZeroBased
        val url = apiUrl("rixo/prices/page-search?q=$encQ&field=$encF&page=$p&size=$suppliersItemsPerPage")
        window.fetch(url)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Search failed')")
            }
            .then { body: dynamic ->
                val err = js("body.error")?.toString()?.trim()
                if (!err.isNullOrEmpty()) throw js("Error(err)")
                val totalEl = js("body.totalElements")
                supplierMapSearchTotal = when (totalEl) {
                    is Number -> totalEl.toLong()
                    else -> totalEl?.toString()?.toLongOrNull() ?: 0L
                }
                val tp = js("body.totalPages")
                supplierMapSearchTotalPages = kotlin.math.max(1, when (tp) {
                    is Number -> tp.toInt()
                    else -> tp?.toString()?.toIntOrNull() ?: 1
                })
                val num = js("body.page")
                supplierMapSearchPageZeroBased = when (num) {
                    is Number -> num.toInt()
                    else -> num?.toString()?.toIntOrNull() ?: 0
                }
                suppliersCurrentPage = supplierMapSearchPageZeroBased + 1

                val content = js("body.content") ?: js("[]")
                val pricesArray = js("Array.isArray(content) ? content : []") as Array<dynamic>
                val grouped = groupSupplierPricesForView(pricesArray.toList())
                allSuppliers = grouped

                if (grouped.isEmpty()) {
                    tableDiv.innerHTML = """
                        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                            <div style="font-size: 16px; margin-bottom: 8px;">No supplier data found for: $searchQ</div>
                            <div style="font-size: 14px; color: #9ca3af;">Try a different search</div>
                        </div>
                    """
                    return@then
                }

                val paginatedPrices = grouped
                val totalPages = supplierMapSearchTotalPages
                buildSupplierTableUi(
                    tableDiv,
                    paginatedPrices,
                    grouped,
                    searchQ,
                    totalPages,
                    true,
                    1,
                    paginatedPrices.size
                )
            }
            .catch { error: dynamic ->
                Logger.error("Error searching suppliers: ${error.toString()}")
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading supplier data</div>
                        <div style="font-size: 14px; color: #9ca3af;">${error.asDynamic().message}</div>
                    </div>
                """
            }
        return
    }

    supplierMapSearchServerMode = false
    supplierMapSearchTotal = 0
    supplierMapSearchTotalPages = 0
    supplierMapSearchPageZeroBased = 0

    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load suppliers')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load suppliers')")
            }
            
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            
            val pricesList = pricesArray.toList()
            val sortedPrices = pricesList.sortedByDescending { price ->
                val id = price.id
                try {
                    when (id) {
                        is Number -> id.toDouble()
                        is String -> id.toDoubleOrNull() ?: 0.0
                        else -> {
                            val idStr = id?.toString() ?: "0"
                            idStr.toDoubleOrNull() ?: 0.0
                        }
                    }
                } catch (e: dynamic) {
                    0.0
                }
            }
            
            val groupedPrices = groupSupplierPricesForView(sortedPrices)
            val supplierSortable = setOf("supplierName", "stockLocation", "rixoCompany", "pol", "venueId")
            var orderedForDisplay = groupedPrices
            val smsf = supplierMapSortField
            if (smsf != null && smsf in supplierSortable) {
                val ord = supplierMapSortOrderByField[smsf] ?: "desc"
                orderedForDisplay = if (ord == "asc") {
                    groupedPrices.sortedBy { extractSupplierSortKey(it, smsf) }
            } else {
                    groupedPrices.sortedByDescending { extractSupplierSortKey(it, smsf) }
                }
            }

            allSuppliers = orderedForDisplay

            if (orderedForDisplay.isEmpty()) {
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">No supplier data found.</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.max(1, kotlin.math.ceil(orderedForDisplay.size.toDouble() / suppliersItemsPerPage).toInt())
            val startIndex = (suppliersCurrentPage - 1) * suppliersItemsPerPage
            val endIndex = kotlin.math.min(startIndex + suppliersItemsPerPage, orderedForDisplay.size)
            val paginatedPrices = orderedForDisplay.subList(startIndex, endIndex)
            val footerStart = startIndex + 1
            val footerEnd = endIndex

            buildSupplierTableUi(
                tableDiv,
                paginatedPrices,
                orderedForDisplay,
                "",
                totalPages,
                false,
                footerStart,
                footerEnd
            )
        }
        .catch { error: dynamic ->
            Logger.error("Error loading suppliers: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading supplier data</div>
                    <div style="font-size: 14px; color: #9ca3af;">${error.message}</div>
                </div>
            """
        }
}

fun showSupplierColumnFilterModal() {
    // Remove existing modal if any
    document.getElementById("supplierColumnFilterModal")?.remove()
    
    val modal = document.createElement("div")
    modal.id = "supplierColumnFilterModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
        background-color: rgba(0,0,0,0.5); z-index: 10000; 
        display: flex; align-items: center; justify-content: center;
    """
    
    // Get current device type and limits
    val deviceType = getDeviceType()
    val maxColumns = getMaxConsigneeSupplierMapColumnsForDevice(deviceType)
    val deviceDisplayName = when (deviceType) {
        "mobile" -> "Mobile View"
        "tablet" -> "Tablet View"
        else -> "Desktop View"
    }
    
    val selectedColumnsList = getSelectedSupplierColumns()
    val selectedColumns = selectedColumnsList.toSet()
    
    modal.innerHTML = """
        <div style="background: white; border-radius: 8px; padding: 24px; max-width: 500px; width: 90%; max-height: 80vh; overflow-y: auto; box-shadow: 0 10px 30px rgba(0,0,0,0.3);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; position: relative;">
                <h3 style="margin: 0; color: #333; flex: 1;">Select Columns to Display</h3>
                <button id="closeSupplierColumnFilter" style="background: none; border: none; font-size: 28px; cursor: pointer; color: #666; padding: 4px 8px; line-height: 1; min-width: 44px; min-height: 44px; display: flex; align-items: center; justify-content: center; flex-shrink: 0;">&times;</button>
            </div>
            <div style="margin-bottom: 16px; padding: 12px; background-color: #f8f9fa; border-radius: 4px; border-left: 4px solid #007bff;">
                <strong>$deviceDisplayName - Maximum $maxColumns columns allowed</strong><br>
                <span style="color: #666; font-size: 14px;">Currently selected: <span id="supplierSelectedCount">0</span>/$maxColumns</span>
            </div>
            <div id="supplierColumnCheckboxes" style="display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px;">
                <!-- Column checkboxes will be populated here -->
            </div>
            <div style="display: flex; gap: 10px; justify-content: flex-end;">
                <button id="resetSupplierColumns" style="padding: 8px 16px; background-color: #6c757d; color: white; border: none; border-radius: 4px; cursor: pointer;">Reset to Default</button>
                <button id="applySupplierColumns" style="padding: 8px 16px; background-color: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer;">Apply Changes</button>
            </div>
        </div>
    """
    
    document.body?.appendChild(modal)
    
    // Populate column checkboxes
    val columnLabels = mapOf(
        "supplierName" to "Supplier Name",
        "stockLocation" to "Stock Location",
        "rixoCompany" to "Rixo Company",
        "venueId" to "Venue ID",
        "pol" to "POL"
    )
    
    val checkboxesDiv = document.getElementById("supplierColumnCheckboxes")
    columnLabels.forEach { (key, label) ->
        val checkbox = document.createElement("div")
        val checkboxStyle = checkbox.asDynamic().style
        checkboxStyle.cssText = "display: flex; align-items: center; gap: 8px;"
        val input = document.createElement("input") as HTMLInputElement
        input.type = "checkbox"
        input.id = "supplierCol_$key"
        input.setAttribute("data-column", key)
        input.checked = selectedColumns.contains(key)
        input.addEventListener("change", { _: Event ->
            updateSupplierColumnSelection()
        })
        val labelEl = document.createElement("label") as HTMLLabelElement
        labelEl.htmlFor = "supplierCol_$key"
        labelEl.textContent = label
        val labelStyle = labelEl.asDynamic().style
        labelStyle.cssText = "cursor: pointer; margin: 0;"
        checkbox.appendChild(input)
        checkbox.appendChild(labelEl)
        checkboxesDiv?.appendChild(checkbox)
    }
    
    // Update selection count initially
    updateSupplierColumnSelection()
    
    // Add event listeners
    document.getElementById("closeSupplierColumnFilter")?.addEventListener("click", { _: Event ->
        document.getElementById("supplierColumnFilterModal")?.remove()
    })
    document.getElementById("resetSupplierColumns")?.addEventListener("click", { _: Event ->
        val deviceType = getDeviceType()
        val defaultColumns = getDefaultSupplierColumnsForDevice(deviceType)
        defaultColumns.forEach { col ->
            val checkbox = document.getElementById("supplierCol_$col") as? HTMLInputElement
            checkbox?.checked = true
        }
        defaultColumns.forEach { col ->
            if (!defaultColumns.contains(col)) {
                val checkbox = document.getElementById("supplierCol_$col") as? HTMLInputElement
                checkbox?.checked = false
            }
        }
        updateSupplierColumnSelection()
    })
    document.getElementById("applySupplierColumns")?.addEventListener("click", { _: Event ->
        applySupplierColumnChanges()
    })
    
    // Close modal when clicking outside
    modal.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement
        if (target?.id == "supplierColumnFilterModal") {
            document.getElementById("supplierColumnFilterModal")?.remove()
        }
    })
}

fun updateSupplierColumnSelection() {
    val deviceType = getDeviceType()
    val maxColumns = getMaxConsigneeSupplierMapColumnsForDevice(deviceType)
    val checkboxes = document.querySelectorAll("#supplierColumnCheckboxes input[type='checkbox']")
    var selectedCount = 0
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            selectedCount++
        }
    }
    
    val countSpan = document.getElementById("supplierSelectedCount")
    countSpan?.textContent = "$selectedCount"
    
    // Disable/enable checkboxes based on max limit
    if (selectedCount >= maxColumns) {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            if (!checkbox.checked) {
                checkbox.disabled = true
            }
        }
    } else {
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            checkbox.disabled = false
        }
    }
}

fun applySupplierColumnChanges() {
    val checkboxes = document.querySelectorAll("#supplierColumnCheckboxes input[type='checkbox']")
    val selectedColumns = mutableListOf<String>()
    
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as HTMLInputElement
        if (checkbox.checked) {
            val columnKey = checkbox.getAttribute("data-column") ?: ""
            if (columnKey.isNotEmpty()) {
                selectedColumns.add(columnKey)
            }
        }
    }
    
    // Save to localStorage
    safeLocalStorageSet("selectedSupplierColumns", JSON.stringify(selectedColumns.toTypedArray()))
    
    // Close modal
    document.getElementById("supplierColumnFilterModal")?.remove()
    
    // Reload suppliers to apply changes
    loadMasterSuppliers()
}

fun showAddSupplierModal() {
    showSupplierModal(null)
}

fun showSupplierModal(priceId: Long?) {
    val isEdit = priceId != null
    val title = if (isEdit) "Edit Supplier" else "Add New Supplier"
    
    val modalHtml = """
        <div id="supplierModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">$title</h2>
                        <button id="closeSupplierModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f3f4f6'" onmouseout="this.style.backgroundColor='transparent'">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="supplierForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Supplier Name <span style="color: #ef4444;">*</span></label>
                            ${createPlainTextInput("supplierAuctionHouse", "Enter Supplier Name", required = true)}
                        </div>
                        <div class="supplier-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</label>
                                ${createChipMultiSelectCombobox("supplierStockLocation", "Select Stock Location")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</label>
                                ${createChipMultiSelectCombobox("supplierRixoCompany", "Select Rixo Company")}
                            </div>
                        </div>
                        <div class="supplier-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Venue ID</label>
                                ${createChipMultiSelectCombobox("supplierVenueId", "Select Venue ID")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POL</label>
                                ${createChipMultiSelectCombobox("supplierPol", "Select POL")}
                            </div>
                        </div>
                        <div class="supplier-modal-actions">
                            <button type="button" id="cancelSupplierBtn" class="supplier-modal-btn supplier-modal-btn-cancel">Cancel</button>
                            ${if (isEdit) """
                            <button type="button" id="deleteSupplierBtn" class="supplier-modal-btn supplier-modal-btn-delete">Delete</button>
                            """ else ""}
                            <button type="submit" id="saveSupplierBtn" class="supplier-modal-btn supplier-modal-btn-save">${if (isEdit) "Update" else "Save"}</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    
    document.body?.insertAdjacentHTML("beforeend", modalHtml)

    ensureSupplierChipJs()
    enforceSupplierModalDropdownOnly(prefix = "supplier")

    // Populate modal dropdowns from master_menu (view-only; allows typing too)
    populateSupplierMapModalComboboxes(isDuplicate = false)
    
    // Load data if editing
    if (isEdit && priceId != null) {
        // If opened from grouped table row, prefill from grouped values (includes ';' lists)
        val hasGrouped = js("window.__supplierRowData != null") as Boolean
        if (hasGrouped) {
            tryPrefillSupplierModalFromGroupedRow(prefix = "supplier")
        } else {
        loadSupplierDataForEdit(priceId)
        }
    }
    
    // Event listeners
    document.getElementById("closeSupplierModal")?.addEventListener("click", { _: Event ->
        closeSupplierModal()
    })
    
    document.getElementById("cancelSupplierBtn")?.addEventListener("click", { _: Event ->
        closeSupplierModal()
    })
    
    // Delete button (only shown in edit mode)
    if (isEdit && priceId != null) {
        document.getElementById("deleteSupplierBtn")?.addEventListener("click", { _: Event ->
            if (js("confirm('Are you sure you want to delete this supplier mapping? This action cannot be undone.')").unsafeCast<Boolean>()) {
                deleteMasterSupplier(priceId)
            }
        })
    }
    
    document.getElementById("supplierForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveSupplier(priceId)
    })
}

fun closeSupplierModal() {
    document.getElementById("supplierModal")?.remove()
}

fun closeDuplicateSupplierModal() {
    document.getElementById("duplicateSupplierModal")?.remove()
}

fun showDuplicateSupplierModal(priceId: Long) {
    val modalHtml = """
        <div id="duplicateSupplierModal" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;">
            <div style="background: white; border-radius: 12px; width: 90%; max-width: 700px; max-height: 90vh; overflow-y: auto; box-shadow: 0 20px 25px -5px rgba(0,0,0,0.1);">
                <div style="padding: 24px; border-bottom: 1px solid #e5e7eb;">
                    <div style="display: flex; justify-content: space-between; align-items: center;">
                        <h2 style="margin: 0; font-size: 24px; font-weight: 700; color: #111827;">Duplicate Supplier</h2>
                        <button id="closeDuplicateSupplierModal" style="background: none; border: none; font-size: 24px; color: #6b7280; cursor: pointer; padding: 0; width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; border-radius: 6px;">×</button>
                    </div>
                </div>
                <div style="padding: 24px;">
                    <form id="duplicateSupplierForm">
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Supplier Name <span style="color: #ef4444;">*</span></label>
                            ${createPlainTextInput("dupSupplierAuctionHouse", "Enter Supplier Name", required = true)}
                        </div>
                        <div class="supplier-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</label>
                                ${createChipMultiSelectCombobox("dupSupplierStockLocation", "Select Stock Location")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</label>
                                ${createChipMultiSelectCombobox("dupSupplierRixoCompany", "Select Rixo Company")}
                            </div>
                        </div>
                        <div class="supplier-modal-grid">
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Venue ID</label>
                                ${createChipMultiSelectCombobox("dupSupplierVenueId", "Select Venue ID")}
                            </div>
                            <div>
                                <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">POL</label>
                                ${createChipMultiSelectCombobox("dupSupplierPol", "Select POL")}
                            </div>
                        </div>
                        <div class="supplier-modal-actions">
                            <button type="button" id="cancelDuplicateSupplierBtn" class="supplier-modal-btn supplier-modal-btn-cancel">Cancel</button>
                            <button type="submit" id="saveDuplicateSupplierBtn" class="supplier-modal-btn supplier-modal-btn-save">Save</button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    """
    document.body?.insertAdjacentHTML("beforeend", modalHtml)

    ensureSupplierChipJs()
    enforceSupplierModalDropdownOnly(prefix = "dupSupplier")

    // Populate modal dropdowns from master_menu
    populateSupplierMapModalComboboxes(isDuplicate = true)

    // Prefill from grouped row data if available; otherwise load by id
    val hasGrouped = js("window.__supplierRowData != null") as Boolean
    if (hasGrouped) {
        tryPrefillSupplierModalFromGroupedRow(prefix = "dupSupplier")
        setEditableComboboxValue("dupSupplierAuctionHouse", "")
    } else {
    // Load row data and fill form
    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load supplier data')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                val msg = (result.message as? String) ?: "Failed to load supplier data"
                throw Exception(msg)
            }
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            val price = pricesArray.find { it.id == priceId }
            if (price != null) {
                    setChipFieldValue("dupSupplierStockLocation", (price.stockLocation ?: "").toString())
                    setChipFieldValue("dupSupplierRixoCompany", (price.rixoCompany ?: "").toString())
                    setChipFieldValue("dupSupplierVenueId", (price.venueId ?: "").toString())
                    setChipFieldValue("dupSupplierPol", (price.pol ?: "").toString())
                    setEditableComboboxValue("dupSupplierAuctionHouse", "")
            } else {
                closeDuplicateSupplierModal()
                showMessage("Supplier not found", "error")
            }
        }
        .catch { error: dynamic ->
            closeDuplicateSupplierModal()
            Logger.error("Error loading supplier for duplicate: ${error.toString()}")
            showMessage("Failed to load supplier data", "error")
            }
        }

    document.getElementById("closeDuplicateSupplierModal")?.addEventListener("click", { _: Event ->
        closeDuplicateSupplierModal()
    })
    document.getElementById("cancelDuplicateSupplierBtn")?.addEventListener("click", { _: Event ->
        closeDuplicateSupplierModal()
    })
    document.getElementById("duplicateSupplierForm")?.addEventListener("submit", { event: Event ->
        event.preventDefault()
        saveDuplicateSupplier()
    })
}

fun saveDuplicateSupplier() {
    val auctionHouse = getEditableComboboxValue("dupSupplierAuctionHouse")
    if (auctionHouse.isEmpty()) {
        showMessage("Supplier Name is required", "error")
        return
    }
    val stockLocation = getChipFieldValue("dupSupplierStockLocation")
    val rixoCompany = getChipFieldValue("dupSupplierRixoCompany")
    val venueId = getChipFieldValue("dupSupplierVenueId")
    val pol = getChipFieldValue("dupSupplierPol")
    validateSupplierMasterFields(auctionHouse, stockLocation, rixoCompany, venueId, pol) { missingFields ->
        if (missingFields.isNotEmpty()) {
            closeDuplicateSupplierModal()
            showSupplierMasterFieldsErrorModal(missingFields)
        } else {
            performDuplicateSupplierSave()
        }
    }
}

fun performDuplicateSupplierSave() {
    val auctionHouse = getEditableComboboxValue("dupSupplierAuctionHouse")
    val stockLocation = getChipFieldValue("dupSupplierStockLocation")
    val rixoCompany = getChipFieldValue("dupSupplierRixoCompany")
    val venueId = getChipFieldValue("dupSupplierVenueId")
    val pol = getChipFieldValue("dupSupplierPol")
    val requestData = js("{}")
    requestData.auctionHouse = auctionHouse
    requestData.stockLocation = stockLocation
    requestData.rixoCompany = rixoCompany
    requestData.venueId = venueId
    requestData.pol = pol
    window.fetch(apiUrl("rixo/mappings/add"), js("""
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestData)
        }
    """))
        .then { response: dynamic ->
            response.json().then { result: dynamic ->
                if (response.ok && (result.success as? Boolean == true)) {
                    closeDuplicateSupplierModal()
                    loadMasterSuppliers()
                    try {
                        val timestamp = js("Date.now()").toString()
                        safeLocalStorageSet("supplierUpdated", timestamp)
                        window.dispatchEvent(js("new CustomEvent('supplierUpdated', { detail: { timestamp: timestamp } })"))
                    } catch (e: dynamic) {}
                    val msg = (result.message as? String)?.takeIf { it.isNotBlank() } ?: "Supplier saved successfully"
                    showMessage(msg, "success")
                } else {
                    val msg = result.message as? String ?: "Failed to save"
                    showMessage("Error: $msg", "error")
                }
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error saving duplicate supplier: ${error.toString()}")
            showMessage("Failed to save duplicate supplier", "error")
        }
}

fun loadSupplierDataForEdit(priceId: Long) {
    // Get the price from the prices list (since there's no single GET endpoint)
    window.fetch(apiUrl("rixo/prices"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load supplier data')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (!success) {
                throw js("Error(result.message || 'Failed to load supplier data')")
            }
            
            val prices = result.data ?: js("[]")
            val pricesArray = js("Array.isArray(prices) ? prices : []") as Array<dynamic>
            val price = pricesArray.find { it.id == priceId }
            
            if (price != null) {
                setEditableComboboxValue("supplierAuctionHouse", (price.auctionHouse ?: "").toString())
                setChipFieldValue("supplierStockLocation", (price.stockLocation ?: "").toString())
                setChipFieldValue("supplierRixoCompany", (price.rixoCompany ?: "").toString())
                setChipFieldValue("supplierVenueId", (price.venueId ?: "").toString())
                setChipFieldValue("supplierPol", (price.pol ?: "").toString())
            } else {
                throw js("Error('Supplier not found')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading supplier data: ${error.toString()}")
            showMessage("Failed to load supplier data: ${error.message}", "error")
        }
}

fun saveSupplier(priceId: Long?, isDuplicate: Boolean = false) {
    val auctionHouse = getEditableComboboxValue("supplierAuctionHouse")
    
    if (auctionHouse.isEmpty()) {
        showMessage("Supplier Name is required", "error")
        return
    }
    
    val stockLocation = getChipFieldValue("supplierStockLocation")
    val rixoCompany = getChipFieldValue("supplierRixoCompany")
    val venueId = getChipFieldValue("supplierVenueId")
    val pol = getChipFieldValue("supplierPol")
    
    // Validate all fields against master lists before saving
    validateSupplierMasterFields(auctionHouse, stockLocation, rixoCompany, venueId, pol) { missingFields ->
        if (missingFields.isNotEmpty()) {
            // Close supplier modal and show error modal
            closeSupplierModal()
            showSupplierMasterFieldsErrorModal(missingFields)
        } else {
            // All fields are valid, proceed with save
            performSupplierSave(priceId, isDuplicate)
        }
    }
}

fun validateSupplierMasterFields(
    supplierName: String,
    stockLocation: String,
    rixoCompany: String,
    venueId: String,
    pol: String,
    callback: (List<Pair<String, String>>) -> Unit
) {
    // Master-menu membership checks removed — always allow save; backend may still validate.
            callback(emptyList())
}

fun showSupplierMasterFieldsErrorModal(missingFields: List<Pair<String, String>>) {
    document.getElementById("supplierMasterFieldsErrorModal")?.remove()
    
    // Build the error message
    val fieldNames = missingFields.map { it.first }.joinToString(", ")
    val pageNames = missingFields.map { it.second }.distinct().joinToString(", ")
    
    val modal = document.createElement("div")
    modal.id = "supplierMasterFieldsErrorModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10001;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 480px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #ef4444;">Field(s) Not Found in Master List</h3>
            <p style="margin-bottom: 20px; color: #374151; font-size: 14px; line-height: 1.6;">
                <strong>$fieldNames</strong> does not exist in Master List. Go to the <strong>$pageNames</strong> page(s) and add the missing value(s).
            </p>
            <div style="display: flex; justify-content: flex-end;">
                <button id="closeSupplierMasterFieldsErrorModalBtn" style="padding: 10px 24px; border-radius: 6px; border: none; background: #3b82f6; color: white; cursor: pointer; font-size: 14px; font-weight: 500;">
                    Close
                </button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    
    document.getElementById("closeSupplierMasterFieldsErrorModalBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })
}

fun performSupplierSave(priceId: Long?, isDuplicate: Boolean = false) {
    val auctionHouse = getEditableComboboxValue("supplierAuctionHouse")
    val stockLocation = getChipFieldValue("supplierStockLocation")
    val rixoCompany = getChipFieldValue("supplierRixoCompany")
    val venueId = getChipFieldValue("supplierVenueId")
    val pol = getChipFieldValue("supplierPol")
    
    val requestData = js("{}")
    requestData.auctionHouse = auctionHouse
    requestData.stockLocation = stockLocation
    requestData.rixoCompany = rixoCompany
    requestData.venueId = venueId
    requestData.pol = pol
    
    val url = if (priceId != null) {
        apiUrl("rixo/mappings/$priceId")
    } else {
        apiUrl("rixo/mappings/add")
    }
    
    val method = if (priceId != null) "PUT" else "POST"
    
    window.fetch(url, js("""
        {
            method: method,
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        }
    """))
        .then { response: dynamic ->
            // Always parse JSON response, even for error status codes
            response.json().then { result: dynamic ->
                if (response.ok) {
                    val success = result.success as? Boolean ?: false
                    if (success) {
                        closeSupplierModal()
                        loadMasterSuppliers()
                        // Notify Add/Edit Purchase tab to refresh supplier dropdown
                        try {
                            val timestamp = js("Date.now()").toString()
                            safeLocalStorageSet("supplierUpdated", timestamp)
                            val supplierEvent = js("new CustomEvent('supplierUpdated', { detail: { timestamp: timestamp } })")
                            window.dispatchEvent(supplierEvent)
                            Logger.debug("✅ Triggered supplier update event")
                        } catch (e: dynamic) {
                            Logger.warn("⚠️ Failed to trigger supplier update event: ${e.toString()}")
                        }
                        val backendMsg = (result.message as? String)?.takeIf { it.isNotBlank() }
                        val fallback = when {
                            priceId != null -> "Supplier updated successfully"
                            isDuplicate -> "Supplier duplicated successfully"
                            else -> "Supplier added successfully"
                        }
                        showMessage(backendMsg ?: fallback, "success")
                    } else {
                        val errorMsg = result.message as? String ?: "Failed to save supplier"
                        Logger.error("Error saving supplier: $errorMsg")
                        showMessage("Error saving supplier: $errorMsg", "error")
                    }
                } else {
                    // Handle error response (400, 500, etc.)
                    val errorMsg = result.message as? String ?: "Failed to save supplier"
                    Logger.error("Error saving supplier (${response.status}): $errorMsg")
                    showMessage("Error saving supplier: $errorMsg", "error")
                }
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error saving supplier: ${error.toString()}")
            val errorMessage = if (error.message != null) error.message.toString() else "Failed to save supplier. Please check your connection and try again."
            showMessage("Error saving supplier: $errorMessage", "error")
        }
}

fun editMasterSupplier(priceId: Long) {
    showSupplierModal(priceId)
}

fun duplicateMasterSupplier(priceId: Long) {
    showDuplicateSupplierModal(priceId)
}

private fun tryPrefillSupplierModalFromGroupedRow(prefix: String) {
    // Reads grouped row values that were attached just before opening the modal.
    // This ensures Edit/Duplicate show ALL semicolon-joined values (same as table view).
    val rowAny = js("window.__supplierRowData") as Any?
    if (rowAny == null) return
    val row = rowAny.asDynamic()

    val supplierName = (row.supplierName ?: "").toString()
    val stockLocation = (row.stockLocation ?: "").toString()
    val rixoCompany = (row.rixoCompany ?: "").toString()
    val venueId = (row.venueId ?: "").toString()
    val pol = (row.pol ?: "").toString()

    if (supplierName.isNotBlank()) setEditableComboboxValue("${prefix}AuctionHouse", supplierName)
    setChipFieldValue("${prefix}StockLocation", stockLocation)
    setChipFieldValue("${prefix}RixoCompany", rixoCompany)
    setChipFieldValue("${prefix}VenueId", venueId)
    setChipFieldValue("${prefix}Pol", pol)

    js("window.__supplierRowData = null")
}

fun deleteMasterSupplier(priceId: Long) {
    window.fetch(apiUrl("rixo/mappings/$priceId"), js("""
        {
            method: 'DELETE',
            headers: {
                'Content-Type': 'application/json'
            }
        }
    """))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to delete supplier')")
        }
        .then { result: dynamic ->
            val success = result.success as? Boolean ?: false
            if (success) {
                closeSupplierModal()
                loadMasterSuppliers()
                // Notify Add/Edit Purchase tab to refresh supplier dropdown
                try {
                    val timestamp = js("Date.now()").toString()
                    safeLocalStorageSet("supplierUpdated", timestamp)
                    val supplierEvent = js("new CustomEvent('supplierUpdated', { detail: { timestamp: timestamp } })")
                    window.dispatchEvent(supplierEvent)
                    Logger.debug("✅ Triggered supplier update event after deletion")
                } catch (e: dynamic) {
                    Logger.warn("⚠️ Failed to trigger supplier update event: ${e.toString()}")
                }
                showMessage("Supplier deleted successfully", "success")
            } else {
                throw js("Error(result.message || 'Failed to delete supplier')")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error deleting supplier: ${error.toString()}")
            showMessage("Error deleting supplier: ${error.message}", "error")
        }
}

fun showMasterRixoCompanyPage() {
    window.location.hash = "#/master/rixo-company"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="rixoCompanyList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Rixo Company</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addRixoCompanyBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Rixo Company</span>
                    </button>
                </div>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Rixo Company:</label>
                    <input type="text" id="rixoCompanyFilter" placeholder="Type Rixo company name to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="rixoCompanyTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading Rixo companies...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterRixoCompanies()
    
    document.getElementById("rixoCompanyFilter")?.addEventListener("input", { _: Event ->
        loadMasterRixoCompanies()
    })

    document.getElementById("addRixoCompanyBtn")?.addEventListener("click", { _: Event ->
        showAddRixoCompanyModal()
    })
}

fun loadMasterRixoCompanies() {
    val tableDiv = document.getElementById("rixoCompanyTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("rixoCompanyFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading Rixo companies...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Use Rixo companies from master_menu (rixo_company field)
    window.fetch(apiUrl("master-menu/rixo_company"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load Rixo companies')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allRixoCompanies = filtered
            if (searchFilter.isNotEmpty()) rixoCompaniesCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No Rixo companies found for: $searchFilter" else "No Rixo companies found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / rixoCompaniesItemsPerPage).toInt()
            val startIndex = (rixoCompaniesCurrentPage - 1) * rixoCompaniesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + rixoCompaniesItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="rixo-company-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Rixo Company</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, companyName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = companyName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="rixo-company-edit-btn"
                                        data-rixo-company="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$companyName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} Rixo companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="rixoCompaniesPrevPage" class="consignee-pagination-btn" ${if (rixoCompaniesCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $rixoCompaniesCurrentPage of $totalPages</span>
                            <button id="rixoCompaniesNextPage" class="consignee-pagination-btn" ${if (rixoCompaniesCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} Rixo companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            val editButtons = document.querySelectorAll(".rixo-company-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-rixo-company") ?: return@addEventListener
                    showEditRixoCompanyModal(name)
                })
            }
            
            document.getElementById("rixoCompaniesPrevPage")?.addEventListener("click", { _: Event ->
                if (rixoCompaniesCurrentPage > 1) {
                    rixoCompaniesCurrentPage--
                    loadMasterRixoCompanies()
                }
            })
            document.getElementById("rixoCompaniesNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allRixoCompanies.size.toDouble() / rixoCompaniesItemsPerPage).toInt()
                if (rixoCompaniesCurrentPage < totalP) {
                    rixoCompaniesCurrentPage++
                    loadMasterRixoCompanies()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading Rixo companies: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading Rixo companies</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddRixoCompanyModal() {
    document.getElementById("rixoCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "rixoCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Rixo Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="rixoCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Rixo Company</label>
                <input type="text" id="rixoCompanyModalInput" placeholder="Enter Rixo company name"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="rixoCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="rixoCompanyModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("rixoCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("rixoCompanyModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("rixoCompanyModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Rixo company name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/rixo_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add Rixo company')")
            }
            .then { _: dynamic ->
                showMessage("Rixo company added successfully", "success")
                modal.remove()
                rixoCompaniesCurrentPage = 1
                loadMasterRixoCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding Rixo company: ${error.toString()}")
                showMessage("Error adding Rixo company: ${error.message}", "error")
            }
    })
}

fun showEditRixoCompanyModal(originalName: String) {
    document.getElementById("rixoCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "rixoCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Rixo Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="rixoCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Rixo Company</label>
                <input type="text" id="rixoCompanyModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="rixoCompanyModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="rixoCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="rixoCompanyModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("rixoCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("rixoCompanyModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("rixoCompanyModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Rixo company name is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/rixo_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update Rixo company')")
            }
            .then { _: dynamic ->
                showMessage("Rixo company updated successfully", "success")
                modal.remove()
                loadMasterRixoCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating Rixo company: ${error.toString()}")
                showMessage("Error updating Rixo company: ${error.message}", "error")
            }
    })

    document.getElementById("rixoCompanyModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this Rixo company?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/rixo_company?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete Rixo company')")
            }
            .then { _: dynamic ->
                showMessage("Rixo company deleted successfully", "success")
                modal.remove()
                rixoCompaniesCurrentPage = 1
                loadMasterRixoCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting Rixo company: ${error.toString()}")
                showMessage("Error deleting Rixo company: ${error.message}", "error")
            }
    })
}

fun showMasterStockLocationsPage() {
    window.location.hash = "#/master/stock-location"
    val content = document.getElementById("content")!!
    content.innerHTML = """
        <div id="stockLocationList" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                <h2 style="margin: 0; color: #111827; font-size: 28px; font-weight: 700;">Stock Location</h2>
                <div style="display: flex; gap: 10px; align-items: center;">
                    <button id="addStockLocationBtn" style="padding: 8px 16px; background-color: #10b981; color: white; border: none; border-radius: 9999px; cursor: pointer; font-size: 14px; display: flex; align-items: center; gap: 6px;">
                        <span style="font-size: 18px; line-height: 1;">+</span>
                        <span>Add Stock Location</span>
                    </button>
                </div>
            </div>
            
            <div style="background: white; border: 1px solid #e5e7eb; border-radius: 8px; padding: 20px; margin-bottom: 20px;">
                <div style="flex: 1; min-width: 250px;">
                    <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Search by Stock Location:</label>
                    <input type="text" id="stockLocationFilter" placeholder="Type stock location to filter..." style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px;">
                </div>
            </div>
            
            <div id="stockLocationTable" style="margin-top: 20px;">
                <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px;">Loading stock locations...</div>
                    <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
                </div>
            </div>
        </div>
    """
    loadMasterStockLocations()
    
    document.getElementById("stockLocationFilter")?.addEventListener("input", { _: Event ->
        loadMasterStockLocations()
    })

    document.getElementById("addStockLocationBtn")?.addEventListener("click", { _: Event ->
        showAddStockLocationModal()
    })
}

fun loadMasterStockLocations() {
    val tableDiv = document.getElementById("stockLocationTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("stockLocationFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading stock locations...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    window.fetch(apiUrl("master-menu/stock_location"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load stock locations')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allStockLocations = filtered
            if (searchFilter.isNotEmpty()) stockLocationsCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No stock locations found for: $searchFilter" else "No stock locations found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / stockLocationsItemsPerPage).toInt()
            val startIndex = (stockLocationsCurrentPage - 1) * stockLocationsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + stockLocationsItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="stock-location-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Stock Location</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, locationName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = locationName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="stock-location-edit-btn"
                                        data-stock-location="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$locationName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} stock locations${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="stockLocationsPrevPage" class="consignee-pagination-btn" ${if (stockLocationsCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $stockLocationsCurrentPage of $totalPages</span>
                            <button id="stockLocationsNextPage" class="consignee-pagination-btn" ${if (stockLocationsCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} stock locations${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            val editButtons = document.querySelectorAll(".stock-location-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-stock-location") ?: return@addEventListener
                    showEditStockLocationModal(name)
                })
            }
            
            document.getElementById("stockLocationsPrevPage")?.addEventListener("click", { _: Event ->
                if (stockLocationsCurrentPage > 1) {
                    stockLocationsCurrentPage--
                    loadMasterStockLocations()
                }
            })
            document.getElementById("stockLocationsNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allStockLocations.size.toDouble() / stockLocationsItemsPerPage).toInt()
                if (stockLocationsCurrentPage < totalP) {
                    stockLocationsCurrentPage++
                    loadMasterStockLocations()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading stock locations: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading stock locations</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddStockLocationModal() {
    document.getElementById("stockLocationEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "stockLocationEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Stock Location</h3>
            <div style="margin-bottom: 16px;">
                <label for="stockLocationModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Stock Location</label>
                <input type="text" id="stockLocationModalInput" placeholder="Enter stock location"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="stockLocationModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="stockLocationModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("stockLocationModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("stockLocationModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("stockLocationModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Stock location is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/stock_location"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add stock location')")
            }
            .then { _: dynamic ->
                showMessage("Stock location added successfully", "success")
                modal.remove()
                stockLocationsCurrentPage = 1
                loadMasterStockLocations()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding stock location: ${error.toString()}")
                showMessage("Error adding stock location: ${error.message}", "error")
            }
    })
}

fun showEditStockLocationModal(originalName: String) {
    document.getElementById("stockLocationEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "stockLocationEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Stock Location</h3>
            <div style="margin-bottom: 16px;">
                <label for="stockLocationModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Stock Location</label>
                <input type="text" id="stockLocationModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="stockLocationModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="stockLocationModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="stockLocationModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("stockLocationModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("stockLocationModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("stockLocationModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Stock location is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/stock_location"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update stock location')")
            }
            .then { _: dynamic ->
                showMessage("Stock location updated successfully", "success")
                modal.remove()
                loadMasterStockLocations()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating stock location: ${error.toString()}")
                showMessage("Error updating stock location: ${error.message}", "error")
            }
    })

    document.getElementById("stockLocationModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Are you sure you want to delete stock location '$originalName'?")) {
            return@addEventListener
        }

        val requestInit = js("{}")
        requestInit.method = "DELETE"

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/stock_location?value=$encoded")

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete stock location')")
            }
            .then { _: dynamic ->
                showMessage("Stock location deleted successfully", "success")
                modal.remove()
                stockLocationsCurrentPage = 1
                loadMasterStockLocations()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting stock location: ${error.toString()}")
                showMessage("Error deleting stock location: ${error.message}", "error")
            }
    })
}

fun showMasterRepairCompaniesPage() {
    window.location.hash = "#/master/set/repair_company"
    showDynamicMasterSetPage("repair_company")
}

fun loadMasterRepairCompanies() {
    val tableDiv = document.getElementById("repairCompanyTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("repairCompanyFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading repair companies...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Master-menu based repair companies
    window.fetch(apiUrl("master-menu/repair_company"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load repair companies')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allRepairCompanies = filtered
            if (searchFilter.isNotEmpty()) repairCompaniesCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No repair companies found for: $searchFilter" else "No repair companies found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / repairCompaniesItemsPerPage).toInt()
            val startIndex = (repairCompaniesCurrentPage - 1) * repairCompaniesItemsPerPage
            val endIndex = kotlin.math.min(startIndex + repairCompaniesItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="repair-company-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Repair Company</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, companyName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = companyName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="repair-company-edit-btn"
                                        data-repair-company="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$companyName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} repair companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="repairCompaniesPrevPage" class="consignee-pagination-btn" ${if (repairCompaniesCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $repairCompaniesCurrentPage of $totalPages</span>
                            <button id="repairCompaniesNextPage" class="consignee-pagination-btn" ${if (repairCompaniesCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} repair companies${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            val editButtons = document.querySelectorAll(".repair-company-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-repair-company") ?: return@addEventListener
                    showEditRepairCompanyModal(name)
                })
            }
            
            document.getElementById("repairCompaniesPrevPage")?.addEventListener("click", { _: Event ->
                if (repairCompaniesCurrentPage > 1) {
                    repairCompaniesCurrentPage--
                    loadMasterRepairCompanies()
                }
            })
            document.getElementById("repairCompaniesNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allRepairCompanies.size.toDouble() / repairCompaniesItemsPerPage).toInt()
                if (repairCompaniesCurrentPage < totalP) {
                    repairCompaniesCurrentPage++
                    loadMasterRepairCompanies()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading repair companies: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading repair companies</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddRepairCompanyModal() {
    document.getElementById("repairCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "repairCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Repair Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="repairCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Repair Company</label>
                <input type="text" id="repairCompanyModalInput" placeholder="Enter repair company"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="repairCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="repairCompanyModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("repairCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("repairCompanyModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("repairCompanyModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Repair company is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/repair_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add repair company')")
            }
            .then { _: dynamic ->
                showMessage("Repair company added successfully", "success")
                modal.remove()
                repairCompaniesCurrentPage = 1
                loadMasterRepairCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding repair company: ${error.toString()}")
                showMessage("Error adding repair company: ${error.message}", "error")
            }
    })
}

fun showEditRepairCompanyModal(originalName: String) {
    document.getElementById("repairCompanyEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "repairCompanyEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Repair Company</h3>
            <div style="margin-bottom: 16px;">
                <label for="repairCompanyModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Repair Company</label>
                <input type="text" id="repairCompanyModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="repairCompanyModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="repairCompanyModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="repairCompanyModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("repairCompanyModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("repairCompanyModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("repairCompanyModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Repair company is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/repair_company"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update repair company')")
            }
            .then { _: dynamic ->
                showMessage("Repair company updated successfully", "success")
                modal.remove()
                loadMasterRepairCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating repair company: ${error.toString()}")
                showMessage("Error updating repair company: ${error.message}", "error")
            }
    })

    document.getElementById("repairCompanyModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this repair company?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/repair_company?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete repair company')")
            }
            .then { _: dynamic ->
                showMessage("Repair company deleted successfully", "success")
                modal.remove()
                repairCompaniesCurrentPage = 1
                loadMasterRepairCompanies()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting repair company: ${error.toString()}")
                showMessage("Error deleting repair company: ${error.message}", "error")
            }
    })
}

fun showMasterBankAccountsPage() {
    window.location.hash = "#/master/set/bank_accounts"
    showDynamicMasterSetPage("bank_accounts")
}

fun loadMasterBankAccounts() {
    val tableDiv = document.getElementById("bankAccountTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("bankAccountFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""

    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading bank accounts...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """

    window.fetch(apiUrl("master-menu/bank_accounts"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load bank accounts')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list

            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No bank accounts found for: $searchFilter" else "No bank accounts found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }

            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / venueIdsItemsPerPage).toInt()
            val startIndex = (venueIdsCurrentPage - 1) * venueIdsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + venueIdsItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)

            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="bank-account-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Bank Account</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, accountName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = accountName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="bank-account-edit-btn"
                                        data-bank-account="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$accountName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} bank accounts${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="bankAccountsPrevPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $venueIdsCurrentPage of $totalPages</span>
                            <button id="bankAccountsNextPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} bank accounts${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            val editButtons = document.querySelectorAll(".bank-account-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-bank-account") ?: return@addEventListener
                    showEditBankAccountModal(name)
                })
            }

            document.getElementById("bankAccountsPrevPage")?.addEventListener("click", { _: Event ->
                if (venueIdsCurrentPage > 1) {
                    venueIdsCurrentPage--
                    loadMasterBankAccounts()
                }
            })
            document.getElementById("bankAccountsNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(list.size.toDouble() / venueIdsItemsPerPage).toInt()
                if (venueIdsCurrentPage < totalP) {
                    venueIdsCurrentPage++
                    loadMasterBankAccounts()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading bank accounts: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading bank accounts</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddBankAccountModal() {
    document.getElementById("bankAccountEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "bankAccountEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Bank Account</h3>
            <div style="margin-bottom: 16px;">
                <label for="bankAccountModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Bank Account</label>
                <input type="text" id="bankAccountModalInput" placeholder="Enter bank account"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="bankAccountModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="bankAccountModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("bankAccountModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("bankAccountModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("bankAccountModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Bank account is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/bank_accounts"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add bank account')")
            }
            .then { _: dynamic ->
                showMessage("Bank account added successfully", "success")
                modal.remove()
                loadMasterBankAccounts()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding bank account: ${error.toString()}")
                showMessage("Error adding bank account: ${error.message}", "error")
            }
    })
}

fun showEditBankAccountModal(originalName: String) {
    document.getElementById("bankAccountEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "bankAccountEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Bank Account</h3>
            <div style="margin-bottom: 16px;">
                <label for="bankAccountModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Bank Account</label>
                <input type="text" id="bankAccountModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="bankAccountModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="bankAccountModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="bankAccountModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("bankAccountModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("bankAccountModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("bankAccountModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Bank account is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalName

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/bank_accounts"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update bank account')")
            }
            .then { _: dynamic ->
                showMessage("Bank account updated successfully", "success")
                modal.remove()
                loadMasterBankAccounts()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating bank account: ${error.toString()}")
                showMessage("Error updating bank account: ${error.message}", "error")
            }
    })

    document.getElementById("bankAccountModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this bank account?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalName) as String
        val url = apiUrl("master-menu/bank_accounts?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete bank account')")
            }
            .then { _: dynamic ->
                showMessage("Bank account deleted successfully", "success")
                modal.remove()
                loadMasterBankAccounts()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting bank account: ${error.toString()}")
                showMessage("Error deleting bank account: ${error.message}", "error")
            }
    })
}

fun showMasterVenueIdsPage() {
    window.location.hash = "#/master/set/venue_id"
    showDynamicMasterSetPage("venue_id")
}

fun loadMasterVenueIds() {
    val tableDiv = document.getElementById("venueIdTable")
    if (tableDiv == null) return
    
    val searchFilter = (document.getElementById("venueIdFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading venue IDs...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    
    // Master-menu based venue IDs
    window.fetch(apiUrl("master-menu/venue_id"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load venue IDs')")
        }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allVenueIds = filtered
            if (searchFilter.isNotEmpty()) venueIdsCurrentPage = 1
            
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No venue IDs found for: $searchFilter" else "No venue IDs found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / venueIdsItemsPerPage).toInt()
            val startIndex = (venueIdsCurrentPage - 1) * venueIdsItemsPerPage
            val endIndex = kotlin.math.min(startIndex + venueIdsItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="venue-id-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">Venue ID</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, venueIdName) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = venueIdName.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb; transition: background-color 0.2s;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="venue-id-edit-btn"
                                        data-venue-id="$safeName"
                                        aria-label="Edit"
                                        title="Edit"
                                        style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer; box-shadow: 0 2px 4px rgba(76,201,255,0.30);">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$venueIdName</td>
                    </tr>
                """
            }
            html += """
                        </tbody>
                    </table>
                </div>
            """
            if (totalPages > 1) {
                html += """
                    <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; flex-wrap: wrap; gap: 12px;">
                        <div style="color: #6b7280; font-size: 14px; flex: 1; min-width: 200px;">
                            Showing ${startIndex + 1} to $endIndex of ${filtered.size} venue IDs${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                        </div>
                        <div class="consignee-pagination-controls">
                            <button id="venueIdsPrevPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage == 1) "disabled" else ""}>Previous</button>
                            <span class="consignee-pagination-page">Page $venueIdsCurrentPage of $totalPages</span>
                            <button id="venueIdsNextPage" class="consignee-pagination-btn" ${if (venueIdsCurrentPage >= totalPages) "disabled" else ""}>Next</button>
                        </div>
                    </div>
                """
            } else {
                html += """
                    <div style="padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb; color: #6b7280; font-size: 14px;">
                        Total: ${filtered.size} venue IDs${if (searchFilter.isNotEmpty()) " (filtered)" else ""}
                    </div>
                """
            }
            tableDiv.innerHTML = html

            val editButtons = document.querySelectorAll(".venue-id-edit-btn")
            for (i in 0 until editButtons.length) {
                val btn = editButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    val name = btn.getAttribute("data-venue-id") ?: return@addEventListener
                    showEditVenueIdModal(name)
                })
            }
            
            document.getElementById("venueIdsPrevPage")?.addEventListener("click", { _: Event ->
                if (venueIdsCurrentPage > 1) {
                    venueIdsCurrentPage--
                    loadMasterVenueIds()
                }
            })
            document.getElementById("venueIdsNextPage")?.addEventListener("click", { _: Event ->
                val totalP = kotlin.math.ceil(allVenueIds.size.toDouble() / venueIdsItemsPerPage).toInt()
                if (venueIdsCurrentPage < totalP) {
                    venueIdsCurrentPage++
                    loadMasterVenueIds()
                }
            })
        }
        .catch { error: dynamic ->
            Logger.error("Error loading venue IDs: ${error.toString()}")
            tableDiv.innerHTML = """
                <div style="text-align: center; color: #ef4444; padding: 60px 20px;">
                    <div style="font-size: 16px; margin-bottom: 8px; font-weight: 600;">Error loading venue IDs</div>
                    <div style="font-size: 14px; color: #9ca3af;">${(error?.message ?: error.toString())}</div>
                </div>
            """
        }
}

fun showAddVenueIdModal() {
    document.getElementById("venueIdEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "venueIdEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Add Venue ID</h3>
            <div style="margin-bottom: 16px;">
                <label for="venueIdModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Venue ID</label>
                <input type="text" id="venueIdModalInput" placeholder="Enter venue ID"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 20px;">
                <button id="venueIdModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                <button id="venueIdModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer; font-size: 14px;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("venueIdModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("venueIdModalAddBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("venueIdModalInput") as? HTMLInputElement
        val value = input?.value?.trim() ?: ""
        if (value.isEmpty()) {
            showMessage("Venue ID is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = value

        val requestInit = js("{}")
        requestInit.method = "POST"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/venue_id"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to add venue ID')")
            }
            .then { _: dynamic ->
                showMessage("Venue ID added successfully", "success")
                modal.remove()
                venueIdsCurrentPage = 1
                loadMasterVenueIds()
            }
            .catch { error: dynamic ->
                Logger.error("Error adding venue ID: ${error.toString()}")
                showMessage("Error adding venue ID: ${error.message}", "error")
            }
    })
}

fun showEditVenueIdModal(originalValue: String) {
    document.getElementById("venueIdEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "venueIdEditModal"
    modal.asDynamic().style.cssText = """
        position: fixed; top: 0; left: 0; width: 100%; height: 100%;
        background-color: rgba(0,0,0,0.5); z-index: 10000;
        display: flex; align-items: center; justify-content: center;
    """
    val safeOriginal = originalValue.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700; color: #111827;">Edit Venue ID</h3>
            <div style="margin-bottom: 16px;">
                <label for="venueIdModalInput" style="display:block; margin-bottom: 6px; font-weight: 600; font-size: 14px; color:#374151;">Venue ID</label>
                <input type="text" id="venueIdModalInput" value="$safeOriginal"
                       style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; font-size: 14px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 20px; flex-wrap: wrap;">
                <button id="venueIdModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer; font-size: 14px;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="venueIdModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer; font-size: 14px;">Cancel</button>
                    <button id="venueIdModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer; font-size: 14px;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)

    document.getElementById("venueIdModalCancelBtn")?.addEventListener("click", { _: Event ->
        modal.remove()
    })

    document.getElementById("venueIdModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val input = document.getElementById("venueIdModalInput") as? HTMLInputElement
        val newValue = input?.value?.trim() ?: ""
        if (newValue.isEmpty()) {
            showMessage("Venue ID is required", "error")
            return@addEventListener
        }

        val body = js("{}")
        body.value = newValue
        body.originalValue = originalValue

        val requestInit = js("{}")
        requestInit.method = "PUT"
        val headers = js("{}")
        headers["Content-Type"] = "application/json"
        requestInit.headers = headers
        requestInit.body = JSON.stringify(body)

        window.fetch(apiUrl("master-menu/venue_id"), requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to update venue ID')")
            }
            .then { _: dynamic ->
                showMessage("Venue ID updated successfully", "success")
                modal.remove()
                loadMasterVenueIds()
            }
            .catch { error: dynamic ->
                Logger.error("Error updating venue ID: ${error.toString()}")
                showMessage("Error updating venue ID: ${error.message}", "error")
            }
    })

    document.getElementById("venueIdModalDeleteBtn")?.addEventListener("click", { _: Event ->
        val confirmDelete = window.confirm("Are you sure you want to delete this venue ID?")
        if (!confirmDelete) {
            return@addEventListener
        }

        val encoded = js("encodeURIComponent")(originalValue) as String
        val url = apiUrl("master-menu/venue_id?value=" + encoded)
        val requestInit = js("{}")
        requestInit.method = "DELETE"

        window.fetch(url, requestInit)
            .then { response: dynamic ->
                if (response.ok) response.json() else throw js("Error('Failed to delete venue ID')")
            }
            .then { _: dynamic ->
                showMessage("Venue ID deleted successfully", "success")
                modal.remove()
                venueIdsCurrentPage = 1
                loadMasterVenueIds()
            }
            .catch { error: dynamic ->
                Logger.error("Error deleting venue ID: ${error.toString()}")
                showMessage("Error deleting venue ID: ${error.message}", "error")
            }
    })
}

// --- POL master page (responsive simple-master UI via dynamic master set) ---
fun showMasterPolPage() {
    window.location.hash = "#/master/set/pol"
    showDynamicMasterSetPage("pol")
}

fun loadMasterPol() {
    val tableDiv = document.getElementById("polTable") ?: return
    val searchFilter = (document.getElementById("polFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = """
        <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
            <div style="font-size: 16px; margin-bottom: 8px;">Loading POL...</div>
            <div style="font-size: 14px; color: #9ca3af;">Please wait</div>
        </div>
    """
    window.fetch(apiUrl("master-menu/pol"))
        .then { response: dynamic -> if (response.ok) response.json() else throw js("Error('Failed to load POL')") }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allPol = filtered
            if (searchFilter.isNotEmpty()) polCurrentPage = 1
            if (filtered.isEmpty()) {
                val message = if (searchFilter.isNotEmpty()) "No POL found for: $searchFilter" else "No POL found."
                tableDiv.innerHTML = """
                    <div style="text-align: center; color: #6b7280; padding: 60px 20px;">
                        <div style="font-size: 16px; margin-bottom: 8px;">$message</div>
                        <div style="font-size: 14px; color: #9ca3af;">Try adjusting your search filter</div>
                    </div>
                """
                return@then
            }
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / polItemsPerPage).toInt()
            val startIndex = (polCurrentPage - 1) * polItemsPerPage
            val endIndex = kotlin.math.min(startIndex + polItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;" class="pol-table">
                        <thead>
                            <tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">ID</th>
                                <th style="padding: 14px 16px; text-align: left; font-weight: 600; color: #374151; font-size: 14px;">POL</th>
                            </tr>
                        </thead>
                        <tbody>
            """
            for ((idx, name) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = name.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb;" onmouseover="this.style.backgroundColor='#f9fafb'" onmouseout="this.style.backgroundColor='white'">
                        <td style="padding: 14px 16px; color: #6b7280; font-size: 14px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="pol-edit-btn" data-pol="$safeName" aria-label="Edit" title="Edit" style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer;">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                                        <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/>
                                        <path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/>
                                    </svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; color: #111827; font-size: 14px; font-weight: 500;">$name</td>
                    </tr>
                """
            }
            html += "</tbody></table></div>"
            if (totalPages > 1) html += """
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 16px; background-color: #f9fafb; border-top: 1px solid #e5e7eb;">
                    <div style="color: #6b7280; font-size: 14px;">Showing ${startIndex + 1} to $endIndex of ${filtered.size}</div>
                    <div><button id="polPrevPage" class="consignee-pagination-btn" ${if (polCurrentPage == 1) "disabled" else ""}>Previous</button>
                    <span class="consignee-pagination-page">Page $polCurrentPage of $totalPages</span>
                    <button id="polNextPage" class="consignee-pagination-btn" ${if (polCurrentPage >= totalPages) "disabled" else ""}>Next</button></div>
                </div>
            """ else html += """
                <div style="padding: 16px; background-color: #f9fafb; color: #6b7280; font-size: 14px;">Total: ${filtered.size} POL</div>
            """
            tableDiv.innerHTML = html
            val polEditButtons = document.querySelectorAll(".pol-edit-btn")
            for (i in 0 until polEditButtons.length) {
                val btn = polEditButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event ->
                    showEditPolModal(btn.getAttribute("data-pol") ?: return@addEventListener)
                })
            }
            document.getElementById("polPrevPage")?.addEventListener("click", { _: Event ->
                if (polCurrentPage > 1) { polCurrentPage--; loadMasterPol() }
            })
            document.getElementById("polNextPage")?.addEventListener("click", { _: Event ->
                if (polCurrentPage < kotlin.math.ceil(allPol.size.toDouble() / polItemsPerPage).toInt()) { polCurrentPage++; loadMasterPol() }
            })
        }
        .catch { error: dynamic ->
            tableDiv.innerHTML = "<div style=\"text-align: center; color: #ef4444; padding: 60px 20px;\">Error loading POL: ${error?.message ?: error.toString()}</div>"
        }
}

fun showAddPolModal() {
    document.getElementById("polEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "polEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700;">Add POL</h3>
            <div style="margin-bottom: 16px;">
                <label for="polModalInput" style="display:block; margin-bottom: 6px; font-weight: 600;">POL</label>
                <input type="text" id="polModalInput" placeholder="Enter POL" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:flex-end; gap:10px;">
                <button id="polModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                <button id="polModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("polModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("polModalAddBtn")?.addEventListener("click", { _: Event ->
        val value = (document.getElementById("polModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (value.isEmpty()) { showMessage("POL is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = value
        val req = js("{}"); req.method = "POST"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pol"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to add')") }
            .then { _: dynamic -> showMessage("POL added successfully", "success"); modal.remove(); polCurrentPage = 1; loadMasterPol() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showEditPolModal(originalName: String) {
    document.getElementById("polEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "polEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%; box-shadow: 0 10px 30px rgba(0,0,0,0.25);">
            <h3 style="margin-top: 0; margin-bottom: 16px; font-size: 20px; font-weight: 700;">Edit POL</h3>
            <div style="margin-bottom: 16px;">
                <label for="polModalInput" style="display:block; margin-bottom: 6px; font-weight: 600;">POL</label>
                <input type="text" id="polModalInput" value="$safeOriginal" style="width: 100%; padding: 10px 12px; border: 1px solid #d1d5db; border-radius: 6px; box-sizing: border-box;">
            </div>
            <div style="display:flex; justify-content:space-between; gap:10px;">
                <button id="polModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="polModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                    <button id="polModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("polModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("polModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val newValue = (document.getElementById("polModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (newValue.isEmpty()) { showMessage("POL is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = newValue; body.originalValue = originalName
        val req = js("{}"); req.method = "PUT"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pol"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to update')") }
            .then { _: dynamic -> showMessage("POL updated successfully", "success"); modal.remove(); loadMasterPol() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById("polModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this POL?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl("master-menu/pol?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("POL deleted successfully", "success"); modal.remove(); polCurrentPage = 1; loadMasterPol() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showMasterPodPage() {
    window.location.hash = "#/master/set/pod"
    showDynamicMasterSetPage("pod")
}

fun loadMasterPod() {
    val tableDiv = document.getElementById("podTable") ?: return
    val searchFilter = (document.getElementById("podFilter") as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = "<div style=\"text-align: center; color: #6b7280; padding: 60px 20px;\">Loading POD...</div>"
    window.fetch(apiUrl("master-menu/pod"))
        .then { response: dynamic -> if (response.ok) response.json() else throw js("Error('Failed to load POD')") }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            allPod = filtered
            if (searchFilter.isNotEmpty()) podCurrentPage = 1
            if (filtered.isEmpty()) {
                tableDiv.innerHTML = "<div style=\"text-align: center; color: #6b7280; padding: 60px 20px;\">${if (searchFilter.isNotEmpty()) "No POD found for: $searchFilter" else "No POD found."}</div>"
                return@then
            }
            val totalPages = kotlin.math.ceil(filtered.size.toDouble() / podItemsPerPage).toInt()
            val startIndex = (podCurrentPage - 1) * podItemsPerPage
            val endIndex = kotlin.math.min(startIndex + podItemsPerPage, filtered.size)
            val pageItems = filtered.subList(startIndex, endIndex)
            var html = """
                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead><tr style="background-color: #f9fafb; border-bottom: 2px solid #e5e7eb;">
                            <th style="padding: 14px 16px; text-align: left; font-weight: 600;">ID</th>
                            <th style="padding: 14px 16px; text-align: left; font-weight: 600;">POD</th>
                        </tr></thead><tbody>
            """
            for ((idx, name) in pageItems.withIndex()) {
                val rowNum = startIndex + idx + 1
                val safeName = name.replace("\"", "&quot;")
                html += """
                    <tr style="border-bottom: 1px solid #e5e7eb;">
                        <td style="padding: 14px 16px;">
                            <div style="display: flex; align-items: center; gap: 10px;">
                                <button class="pod-edit-btn" data-pod="$safeName" style="display:inline-flex; align-items:center; justify-content:center; width:32px; height:32px; background-color:#4CC9FF; border:none; border-radius:50%; cursor:pointer;">
                                    <svg viewBox="0 0 24 24" fill="none" width="16" height="16"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
                                </button>
                                <span>$rowNum</span>
                            </div>
                        </td>
                        <td style="padding: 14px 16px; font-weight: 500;">$name</td>
                    </tr>
                """
            }
            html += "</tbody></table></div>"
            if (totalPages > 1) html += "<div style=\"padding: 16px; background-color: #f9fafb;\"><button id=\"podPrevPage\" ${if (podCurrentPage == 1) "disabled" else ""}>Previous</button> Page $podCurrentPage of $totalPages <button id=\"podNextPage\" ${if (podCurrentPage >= totalPages) "disabled" else ""}>Next</button></div>"
            else html += "<div style=\"padding: 16px; background-color: #f9fafb;\">Total: ${filtered.size} POD</div>"
            tableDiv.innerHTML = html
            val podEditButtons = document.querySelectorAll(".pod-edit-btn")
            for (i in 0 until podEditButtons.length) {
                val btn = podEditButtons.item(i) as? HTMLElement ?: continue
                btn.addEventListener("click", { _: Event -> showEditPodModal(btn.getAttribute("data-pod") ?: return@addEventListener) })
            }
            document.getElementById("podPrevPage")?.addEventListener("click", { _: Event -> if (podCurrentPage > 1) { podCurrentPage--; loadMasterPod() } })
            document.getElementById("podNextPage")?.addEventListener("click", { _: Event -> if (podCurrentPage < kotlin.math.ceil(allPod.size.toDouble() / podItemsPerPage).toInt()) { podCurrentPage++; loadMasterPod() } })
        }
        .catch { error: dynamic -> tableDiv.innerHTML = "<div style=\"text-align: center; color: #ef4444; padding: 60px 20px;\">Error loading POD</div>" }
}

fun showAddPodModal() {
    document.getElementById("podEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "podEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Add POD</h3>
            <label>POD</label>
            <input type="text" id="podModalInput" placeholder="Enter POD" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 16px;">
                <button id="podModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                <button id="podModalAddBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("podModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("podModalAddBtn")?.addEventListener("click", { _: Event ->
        val value = (document.getElementById("podModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (value.isEmpty()) { showMessage("POD is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = value
        val req = js("{}"); req.method = "POST"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pod"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to add')") }
            .then { _: dynamic -> showMessage("POD added successfully", "success"); modal.remove(); podCurrentPage = 1; loadMasterPod() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showEditPodModal(originalName: String) {
    document.getElementById("podEditModal")?.remove()
    val modal = document.createElement("div")
    modal.id = "podEditModal"
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Edit POD</h3>
            <label>POD</label>
            <input type="text" id="podModalInput" value="$safeOriginal" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 16px;">
                <button id="podModalDeleteBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="podModalCancelBtn" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                    <button id="podModalUpdateBtn" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById("podModalCancelBtn")?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById("podModalUpdateBtn")?.addEventListener("click", { _: Event ->
        val newValue = (document.getElementById("podModalInput") as? HTMLInputElement)?.value?.trim() ?: ""
        if (newValue.isEmpty()) { showMessage("POD is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = newValue; body.originalValue = originalName
        val req = js("{}"); req.method = "PUT"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl("master-menu/pod"), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to update')") }
            .then { _: dynamic -> showMessage("POD updated successfully", "success"); modal.remove(); loadMasterPod() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById("podModalDeleteBtn")?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this POD?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl("master-menu/pod?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("POD deleted successfully", "success"); modal.remove(); podCurrentPage = 1; loadMasterPod() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

fun showMasterFuelPage() {
    window.location.hash = "#/master/set/fuel"
    showDynamicMasterSetPage("fuel")
}
fun loadMasterFuel() { loadSimpleMaster("master-menu/fuel", "fuelFilter", "fuelTable", "Fuel", fuelCurrentPage, fuelItemsPerPage, allFuel, { fuelCurrentPage = it }, { allFuel = it }, ::showEditFuelModal, "fuel-edit-btn", "data-fuel", "fuelPrevPage", "fuelNextPage", ::loadMasterFuel) }
fun showAddFuelModal() { addSimpleMasterModal("master-menu/fuel", "Fuel", "fuelEditModal", "fuelModalInput", "fuelModalCancelBtn", "fuelModalAddBtn", { fuelCurrentPage = 1; loadMasterFuel() }) }
fun showEditFuelModal(originalName: String) { editSimpleMasterModal("master-menu/fuel", "Fuel", originalName, "fuelEditModal", "fuelModalInput", "fuelModalCancelBtn", "fuelModalUpdateBtn", "fuelModalDeleteBtn", { loadMasterFuel() }, { fuelCurrentPage = 1; loadMasterFuel() }) }

/** Opens Car Grade master UI (hash must already be #/master/car-grade, or call [showMasterCarGradePage] to set it). */
fun openMasterCarGradeRoute() {
    renderSimpleMasterPage("car_grade", "Car Grade", "carGrade", "carGradeFilter", "carGradeTable", "addCarGradeBtn", ::loadMasterCarGrade, ::showAddCarGradeModal)
}

fun showMasterCarGradePage() {
    window.location.hash = "#/master/car-grade"
}
fun loadMasterCarGrade() { loadSimpleMaster("master-menu/car_grade", "carGradeFilter", "carGradeTable", "Car Grade", carGradeCurrentPage, carGradeItemsPerPage, allCarGrades, { carGradeCurrentPage = it }, { allCarGrades = it }, ::showEditCarGradeModal, "car-grade-edit-btn", "data-car-grade", "carGradePrevPage", "carGradeNextPage", ::loadMasterCarGrade) }
fun showAddCarGradeModal() { addSimpleMasterModal("master-menu/car_grade", "Car Grade", "carGradeEditModal", "carGradeModalInput", "carGradeModalCancelBtn", "carGradeModalAddBtn", { carGradeCurrentPage = 1; loadMasterCarGrade() }) }
fun showEditCarGradeModal(originalName: String) { editSimpleMasterModal("master-menu/car_grade", "Car Grade", originalName, "carGradeEditModal", "carGradeModalInput", "carGradeModalCancelBtn", "carGradeModalUpdateBtn", "carGradeModalDeleteBtn", { loadMasterCarGrade() }, { carGradeCurrentPage = 1; loadMasterCarGrade() }) }

fun showMasterCarShiftPage() {
    window.location.hash = "#/master/set/shift"
    showDynamicMasterSetPage("shift")
}
fun loadMasterCarShift() { loadSimpleMaster("master-menu/shift", "carShiftFilter", "carShiftTable", "Car Shift", carShiftCurrentPage, carShiftItemsPerPage, allCarShifts, { carShiftCurrentPage = it }, { allCarShifts = it }, ::showEditCarShiftModal, "car-shift-edit-btn", "data-car-shift", "carShiftPrevPage", "carShiftNextPage", ::loadMasterCarShift) }
fun showAddCarShiftModal() { addSimpleMasterModal("master-menu/shift", "Car Shift", "carShiftEditModal", "carShiftModalInput", "carShiftModalCancelBtn", "carShiftModalAddBtn", { carShiftCurrentPage = 1; loadMasterCarShift() }) }
fun showEditCarShiftModal(originalName: String) { editSimpleMasterModal("master-menu/shift", "Car Shift", originalName, "carShiftEditModal", "carShiftModalInput", "carShiftModalCancelBtn", "carShiftModalUpdateBtn", "carShiftModalDeleteBtn", { loadMasterCarShift() }, { carShiftCurrentPage = 1; loadMasterCarShift() }) }

fun showMasterTypeOfVehiclesPage() {
    window.location.hash = "#/master/set/type_of_vehicle"
    showDynamicMasterSetPage("type_of_vehicle")
}
fun loadMasterTypeOfVehicles() { loadSimpleMaster("master-menu/type_of_vehicle", "typeOfVehiclesFilter", "typeOfVehiclesTable", "Type of Vehicles", typeOfVehiclesCurrentPage, typeOfVehiclesItemsPerPage, allTypeOfVehicles, { typeOfVehiclesCurrentPage = it }, { allTypeOfVehicles = it }, ::showEditTypeOfVehiclesModal, "type-of-vehicles-edit-btn", "data-type-of-vehicles", "typeOfVehiclesPrevPage", "typeOfVehiclesNextPage", ::loadMasterTypeOfVehicles) }
fun showAddTypeOfVehiclesModal() { addSimpleMasterModal("master-menu/type_of_vehicle", "Type of Vehicles", "typeOfVehiclesEditModal", "typeOfVehiclesModalInput", "typeOfVehiclesModalCancelBtn", "typeOfVehiclesModalAddBtn", { typeOfVehiclesCurrentPage = 1; loadMasterTypeOfVehicles() }) }
fun showEditTypeOfVehiclesModal(originalName: String) { editSimpleMasterModal("master-menu/type_of_vehicle", "Type of Vehicles", originalName, "typeOfVehiclesEditModal", "typeOfVehiclesModalInput", "typeOfVehiclesModalCancelBtn", "typeOfVehiclesModalUpdateBtn", "typeOfVehiclesModalDeleteBtn", { loadMasterTypeOfVehicles() }, { typeOfVehiclesCurrentPage = 1; loadMasterTypeOfVehicles() }) }

fun showDynamicMasterSetPage(fieldName: String) {
    val normalizedField = fieldName.trim().lowercase()
    if (normalizedField.isEmpty()) {
        showMessage("Invalid master set", "error")
        return
    }
    if (normalizedField == "car_grade") {
        window.location.hash = "#/master/car-grade"
        return
    }
    window.fetch(apiUrl("master-menu/fields"))
        .then { r: dynamic -> if (r.ok) r.json() else js("[]") }
        .then { raw: dynamic ->
            val fields = if (raw != null && js("Array.isArray(raw)").unsafeCast<Boolean>()) {
                val arr = raw.unsafeCast<Array<*>>()
                (0 until arr.size).mapNotNull { idx ->
                    val value = arr[idx]?.toString()?.trim() ?: ""
                    if (value.isEmpty()) null else value.lowercase()
                }
            } else {
                emptyList()
            }
            val exists = fields.any { it.equals(normalizedField, ignoreCase = true) }
            if (!exists) {
                showMessage("This master set is not available. It may have been removed.", "warning")
                navigateToAppHome()
                return@then
            }
            showDynamicMasterSetPageInner(normalizedField)
        }
        .catch { _: dynamic ->
            showDynamicMasterSetPageInner(normalizedField)
        }
}

private fun showDynamicMasterSetPageInner(normalizedField: String) {
    val title = simpleMasterTitleOverrides[normalizedField]
        ?: normalizedField
            .split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercaseChar() } }
    val key = normalizedField.replace(Regex("[^a-z0-9]"), "")
    val listId = "dynamicMasterList$key"
    val filterId = "dynamicMasterFilter$key"
    val tableId = "dynamicMasterTable$key"
    val addBtnId = "dynamicMasterAddBtn$key"
    val modalId = "dynamicMasterModal$key"
    val inputId = "dynamicMasterInput$key"
    val cancelBtnId = "dynamicMasterCancel$key"
    val addModalBtnId = "dynamicMasterAddConfirm$key"
    val updateBtnId = "dynamicMasterUpdate$key"
    val deleteBtnId = "dynamicMasterDelete$key"
    val prevBtnId = "dynamicMasterPrev$key"
    val nextBtnId = "dynamicMasterNext$key"
    val editBtnClass = "dynamic-master-edit-btn-$key"
    val dataAttr = "data-dynamic-master-$key"
    val apiPath = "master-menu/$normalizedField"

    fun loadDynamic() {
        loadSimpleMaster(
            apiPath = apiPath,
            filterId = filterId,
            tableId = tableId,
            title = title,
            currentPage = dynamicMasterSetCurrentPage[normalizedField] ?: 1,
            itemsPerPage = AppConstants.DEFAULT_ITEMS_PER_PAGE,
            allList = dynamicMasterSetAllValues[normalizedField] ?: emptyList(),
            setPage = { dynamicMasterSetCurrentPage[normalizedField] = it },
            setList = { dynamicMasterSetAllValues[normalizedField] = it },
            editModalFn = { originalName ->
                editSimpleMasterModal(
                    apiPath = apiPath,
                    title = title,
                    originalName = originalName,
                    modalId = modalId,
                    inputId = inputId,
                    cancelBtnId = cancelBtnId,
                    updateBtnId = updateBtnId,
                    deleteBtnId = deleteBtnId,
                    onUpdateSuccess = { loadDynamic() },
                    onDeleteSuccess = {
                        dynamicMasterSetCurrentPage[normalizedField] = 1
                        loadDynamic()
                    },
                )
            },
            editBtnClass = editBtnClass,
            dataAttr = dataAttr,
            prevBtnId = prevBtnId,
            nextBtnId = nextBtnId,
            loadFn = { loadDynamic() },
        )
    }

    renderSimpleMasterPage(
        apiPath = normalizedField,
        title = title,
        listId = listId,
        filterId = filterId,
        tableId = tableId,
        addBtnId = addBtnId,
        loadFn = { loadDynamic() },
        addModalFn = {
            addSimpleMasterModal(
                apiPath = apiPath,
                title = title,
                modalId = modalId,
                inputId = inputId,
                cancelBtnId = cancelBtnId,
                addBtnId = addModalBtnId,
                onSuccess = {
                    dynamicMasterSetCurrentPage[normalizedField] = 1
                    loadDynamic()
                },
            )
        },
    )
}

private fun simpleMasterIsCompactLayout(): Boolean {
    val w = window.innerWidth
    return w > 0 && w <= SIMPLE_MASTER_COMPACT_MAX_WIDTH_PX
}

private fun simpleMasterEditButtonHtml(editBtnClass: String, dataAttr: String, name: String): String {
    val safeName = escapeHtml(name)
    return """<button type="button" class="$editBtnClass" $dataAttr="$safeName" aria-label="Edit" title="Edit"
        style="display:inline-flex;align-items:center;justify-content:center;width:36px;height:36px;min-width:36px;min-height:36px;background-color:#4CC9FF;border:none;border-radius:50%;cursor:pointer;box-shadow:0 2px 4px rgba(76,201,255,0.30);padding:0;">
        <svg viewBox="0 0 24 24" fill="none" width="18" height="18"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25z" fill="white"/><path d="M20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z" fill="white"/></svg>
    </button>"""
}

private fun simpleMasterPagerHtml(meta: SimpleMasterRenderMeta): String {
    val sorted = meta.sorted
    val page = meta.page
    val itemsPerPage = meta.itemsPerPage
    val totalPages = kotlin.math.ceil(sorted.size.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
    val safeTitle = escapeHtml(meta.title)
    if (totalPages <= 1) {
        return """<div class="simple-master-pager"><span>Total: ${sorted.size} $safeTitle</span></div>"""
    }
    val prevDisabled = if (page <= 1) " disabled" else ""
    val nextDisabled = if (page >= totalPages) " disabled" else ""
    return """
        <div class="simple-master-pager">
            <button type="button" id="${meta.prevBtnId}" class="simple-master-pager-btn"$prevDisabled>Prev</button>
            <span class="simple-master-pager-meta">Page $page of $totalPages</span>
            <button type="button" id="${meta.nextBtnId}" class="simple-master-pager-btn"$nextDisabled>Next</button>
        </div>
    """.trimIndent()
}

private fun renderSimpleMasterListUi(meta: SimpleMasterRenderMeta) {
    val tableDiv = document.getElementById(meta.tableId) ?: return
    simpleMasterLastRenderMeta[meta.tableId] = meta
    val sorted = meta.sorted
    val page = meta.page
    val itemsPerPage = meta.itemsPerPage
    val title = meta.title
    val safeTitle = escapeHtml(title)
    if (sorted.isEmpty()) {
        val emptyMsg = if (meta.searchFilter.isNotEmpty()) "No matches for your search." else "No $safeTitle found yet."
        tableDiv.innerHTML = """<div class="simple-master-empty"><strong>No results</strong><div>$emptyMsg</div></div>"""
        return
    }
    val totalPages = kotlin.math.ceil(sorted.size.toDouble() / itemsPerPage).toInt().coerceAtLeast(1)
    val startIndex = ((page - 1) * itemsPerPage).coerceIn(0, sorted.size)
    val endIndex = kotlin.math.min(startIndex + itemsPerPage, sorted.size)
    val pageItems = sorted.subList(startIndex, endIndex)
    val sortOrder = dynamicMasterSetSortOrder[meta.apiPath] ?: "desc"
    val sortArrow = if (sortOrder == "asc") "↑" else "↓"
    val sortTooltip = if (sortOrder == "asc") "Sorted A-Z (click for Z-A)" else "Sorted Z-A (click for A-Z)"
    val pagerHtml = simpleMasterPagerHtml(meta)
    val compact = simpleMasterIsCompactLayout()
    if (!compact) {
        var rowsHtml = ""
        for ((idx, name) in pageItems.withIndex()) {
            val rowNum = startIndex + idx + 1
            rowsHtml += """
                <tr>
                    <td><div class="simple-master-id-cell">${simpleMasterEditButtonHtml(meta.editBtnClass, meta.dataAttr, name)}<span>$rowNum</span></div></td>
                    <td>${escapeHtml(name)}</td>
                </tr>
            """.trimIndent()
        }
        tableDiv.innerHTML = """
            <div class="simple-master-table-shell">
                <table class="purchase-list-table simple-master-table">
                    <colgroup><col style="width:120px"><col></colgroup>
                    <thead><tr>
                        <th>ID</th>
                        <th><button type="button" class="simple-master-sort-btn" data-sm-sort title="$sortTooltip"><span>$safeTitle</span><span aria-hidden="true">$sortArrow</span></button></th>
                    </tr></thead>
                    <tbody>$rowsHtml</tbody>
                </table>
            </div>
            $pagerHtml
        """.trimIndent()
    } else {
        var cardsHtml = """<div class="simple-master-cards">"""
        for ((idx, name) in pageItems.withIndex()) {
            val rowNum = startIndex + idx + 1
            cardsHtml += """
                <div class="simple-master-card">
                    <div class="simple-master-card-top">
                        <div class="simple-master-card-actions">${simpleMasterEditButtonHtml(meta.editBtnClass, meta.dataAttr, name)}</div>
                        <span class="simple-master-card-id">#$rowNum</span>
                    </div>
                    <div class="simple-master-card-grid">
                        <div class="simple-master-kv"><span class="simple-master-k">$safeTitle</span><span class="simple-master-v">${escapeHtml(name)}</span></div>
                    </div>
                </div>
            """.trimIndent()
        }
        cardsHtml += "</div>$pagerHtml"
        tableDiv.innerHTML = cardsHtml
    }
}

private fun setupSimpleMasterResizeListener(pageId: String, tableId: String) {
    val page = document.getElementById(pageId) ?: return
    if (page.hasAttribute("data-simple-master-resize")) return
    page.setAttribute("data-simple-master-resize", "true")
    window.addEventListener("resize", { _: Event ->
        simpleMasterResizeDebounceHandle?.let { window.clearTimeout(it) }
        simpleMasterResizeDebounceHandle = window.setTimeout({
            if (document.getElementById(pageId) == null) return@setTimeout
            simpleMasterLastRenderMeta[tableId]?.let { renderSimpleMasterListUi(it) }
        }, 120)
    })
}

private fun setupSimpleMasterTableDelegation(tableWrapId: String, tableId: String) {
    val wrap = document.getElementById(tableWrapId) ?: return
    if (wrap.hasAttribute("data-sm-delegation")) return
    wrap.setAttribute("data-sm-delegation", "true")
    wrap.addEventListener("click", { e: Event ->
        val target = e.target as? Element ?: return@addEventListener
        val meta = simpleMasterLastRenderMeta[tableId] ?: return@addEventListener
        val sortBtn = target.closest("button[data-sm-sort]")
        if (sortBtn != null) {
            e.preventDefault()
            val current = dynamicMasterSetSortOrder[meta.apiPath] ?: "desc"
            dynamicMasterSetSortOrder[meta.apiPath] = if (current == "asc") "desc" else "asc"
            meta.setPage(1)
            meta.loadFn()
            return@addEventListener
        }
        val editBtn = target.closest("button.${meta.editBtnClass}")
        if (editBtn != null) {
            e.preventDefault()
            val value = editBtn.getAttribute(meta.dataAttr)?.trim() ?: return@addEventListener
            meta.editModalFn(value)
            return@addEventListener
        }
        val pagerBtn = target.closest("button") as? HTMLElement ?: return@addEventListener
        val totalPages = kotlin.math.ceil(meta.sorted.size.toDouble() / meta.itemsPerPage).toInt().coerceAtLeast(1)
        when (pagerBtn.id) {
            meta.prevBtnId -> if (meta.page > 1) {
                meta.setPage(meta.page - 1)
                meta.loadFn()
            }
            meta.nextBtnId -> if (meta.page < totalPages) {
                meta.setPage(meta.page + 1)
                meta.loadFn()
            }
        }
    })
}

private fun renderSimpleMasterPage(apiPath: String, title: String, listId: String, filterId: String, tableId: String, addBtnId: String, loadFn: () -> Unit, addModalFn: () -> Unit) {
    val content = document.getElementById("content") ?: return
    val pageId = "${listId}Page"
    val tableWrapId = "${tableId}Wrap"
    val safeTitle = escapeHtml(title)
    val searchPlaceholder = "Search $title…"
    content.innerHTML = """
        <style>
            #$pageId{background:#f8fafc;border:1px solid #e5e7eb;border-radius:12px;padding:20px;max-width:1200px;margin:0 auto;width:100%;box-sizing:border-box;}
            .simple-master-toolbar{display:grid;grid-template-columns:1fr;grid-template-areas:"title" "search" "add";gap:12px;margin-bottom:16px;align-items:center;}
            .simple-master-title{margin:0;font-size:18px;font-weight:700;color:#0f172a;letter-spacing:-0.01em;grid-area:title;text-align:center;}
            .simple-master-search{grid-area:search;width:100%;position:relative;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);}
            .simple-master-search input{width:100%;box-sizing:border-box;padding:11px 36px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;}
            .simple-master-search-clear{position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:6px 8px;min-height:36px;min-width:36px;}
            .simple-master-search-clear:hover{background:#f3f4f6;color:#111827;}
            .simple-master-add-btn{grid-area:add;justify-self:end;padding:10px 16px;background:#10b981;color:#fff;border:none;border-radius:9999px;cursor:pointer;font-size:14px;font-weight:600;min-height:40px;white-space:nowrap;}
            .simple-master-table-shell{overflow-x:auto;border-radius:12px;background:#fff;border:1px solid #eef2f7;}
            table.simple-master-table thead th{position:sticky;top:0;z-index:1;background:#f9fafb;}
            .simple-master-id-cell{display:flex;align-items:center;gap:10px;}
            .simple-master-sort-btn{background:none;border:none;cursor:pointer;font-weight:600;color:#111827;padding:0;display:inline-flex;align-items:center;gap:6px;font-size:inherit;}
            .simple-master-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
            .simple-master-empty strong{color:#0f172a;}
            .simple-master-cards{display:flex;flex-direction:column;gap:10px;}
            .simple-master-card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;box-shadow:0 1px 2px rgba(0,0,0,0.04);padding:12px;}
            .simple-master-card-top{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px;}
            .simple-master-card-id{font-size:12px;color:#64748b;font-weight:600;}
            .simple-master-card-grid{display:grid;gap:8px;}
            .simple-master-kv{display:flex;gap:10px;align-items:flex-start;}
            .simple-master-k{min-width:120px;font-size:12px;color:#64748b;line-height:1.4;}
            .simple-master-v{flex:1;min-width:0;font-weight:500;color:#0f172a;}
            .simple-master-pager{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:10px;padding:14px 4px 4px;color:#475569;font-size:14px;}
            .simple-master-pager-btn{padding:8px 14px;border:1px solid #e5e7eb;border-radius:8px;background:#fff;cursor:pointer;min-height:36px;}
            .simple-master-pager-btn:disabled{opacity:0.5;cursor:not-allowed;}
            .simple-master-pager-meta{font-weight:500;color:#334155;}
            @media (max-width:1024px){#$pageId{padding:14px;border-radius:14px;max-width:100%;}.simple-master-toolbar{gap:14px;}.simple-master-title{font-size:17px;}.simple-master-search input{font-size:13px;padding:10px 34px 10px 38px;}}
            @media (min-width:1025px){.simple-master-toolbar{grid-template-columns:auto 1fr minmax(200px,25%) auto;grid-template-areas:"title . search add";column-gap:12px;row-gap:0;}.simple-master-title{text-align:left;justify-self:start;}}
        </style>
        <div id="$pageId">
            <div class="simple-master-toolbar">
                <h2 class="simple-master-title">$safeTitle</h2>
                <div class="simple-master-search">
                    <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);pointer-events:none;color:#9ca3af;display:flex;" aria-hidden="true">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    </span>
                    <input type="text" id="$filterId" role="searchbox" autocomplete="off" inputmode="search" placeholder="$searchPlaceholder" aria-label="Search $safeTitle" />
                    <button type="button" id="${filterId}Clear" class="simple-master-search-clear" title="Clear search" aria-label="Clear search">×</button>
                </div>
                <button type="button" id="$addBtnId" class="simple-master-add-btn">+ Add $safeTitle</button>
            </div>
            <div id="$tableWrapId">
                <div id="$tableId">
                    <div class="simple-master-empty"><strong>Loading</strong><div>Loading $safeTitle…</div></div>
                </div>
            </div>
        </div>
    """
    applyRoleBasedRestrictions()
    ensureSidebarPresent()
    setupSimpleMasterTableDelegation(tableWrapId, tableId)
    setupSimpleMasterResizeListener(pageId, tableId)
    val searchInput = document.getElementById(filterId) as? HTMLInputElement
    searchInput?.addEventListener("input", { _: Event -> loadFn() })
    document.getElementById("${filterId}Clear")?.addEventListener("click", { _: Event ->
        searchInput?.value = ""
        loadFn()
    })
    document.getElementById(addBtnId)?.addEventListener("click", { _: Event -> addModalFn() })
    loadFn()
}

private fun loadSimpleMaster(apiPath: String, filterId: String, tableId: String, title: String, currentPage: Int, itemsPerPage: Int, allList: List<String>, setPage: (Int) -> Unit, setList: (List<String>) -> Unit, editModalFn: (String) -> Unit, editBtnClass: String, dataAttr: String, prevBtnId: String, nextBtnId: String, loadFn: () -> Unit) {
    val tableDiv = document.getElementById(tableId) ?: return
    val searchFilter = (document.getElementById(filterId) as? HTMLInputElement)?.value?.trim()?.uppercase() ?: ""
    tableDiv.innerHTML = """<div class="simple-master-empty"><strong>Loading</strong><div>Loading ${escapeHtml(title)}…</div></div>"""
    window.fetch(apiUrl(apiPath))
        .then { response: dynamic -> if (response.ok) response.json() else throw js("Error('Failed to load')") }
        .then { raw: dynamic ->
            val list: List<String> = if (raw != null && js("Array.isArray(raw)")) {
                val a = raw.unsafeCast<Array<*>>()
                (0 until a.size).map { (a[it]?.toString() ?: "").trim() }.filter { it.isNotEmpty() }.distinct().reversed()
            } else emptyList()
            val filtered = if (searchFilter.isNotEmpty()) list.filter { it.uppercase().contains(searchFilter) } else list
            val sortOrder = dynamicMasterSetSortOrder[apiPath] ?: "desc"
            val sorted = if (sortOrder == "asc") filtered.sortedBy { it.lowercase() } else filtered.sortedByDescending { it.lowercase() }
            setList(sorted)
            if (searchFilter.isNotEmpty()) setPage(1)
            val page = if (searchFilter.isNotEmpty()) 1 else currentPage
            val meta = SimpleMasterRenderMeta(
                tableId = tableId,
                title = title,
                apiPath = apiPath,
                editBtnClass = editBtnClass,
                dataAttr = dataAttr,
                prevBtnId = prevBtnId,
                nextBtnId = nextBtnId,
                setPage = setPage,
                loadFn = loadFn,
                editModalFn = editModalFn,
                sorted = sorted,
                page = page,
                itemsPerPage = itemsPerPage,
                searchFilter = searchFilter,
            )
            renderSimpleMasterListUi(meta)
        }
        .catch { _: dynamic ->
            tableDiv.innerHTML = """<div class="simple-master-empty" style="color:#dc2626;"><strong>Could not load</strong><div>Error loading ${escapeHtml(title)}. Try again.</div></div>"""
        }
}

private fun addSimpleMasterModal(apiPath: String, title: String, modalId: String, inputId: String, cancelBtnId: String, addBtnId: String, onSuccess: () -> Unit) {
    document.getElementById(modalId)?.remove()
    val modal = document.createElement("div")
    modal.id = modalId
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Add $title</h3>
            <label>$title</label>
            <input type="text" id="$inputId" placeholder="Enter $title" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:flex-end; gap:10px; margin-top: 16px;">
                <button id="$cancelBtnId" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                <button id="$addBtnId" style="padding: 8px 16px; border-radius: 6px; border: none; background: #10b981; color:white; cursor: pointer;">Add</button>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById(cancelBtnId)?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById(addBtnId)?.addEventListener("click", { _: Event ->
        val value = (document.getElementById(inputId) as? HTMLInputElement)?.value?.trim() ?: ""
        if (value.isEmpty()) { showMessage("$title is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = value
        val req = js("{}"); req.method = "POST"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl(apiPath), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to add')") }
            .then { _: dynamic -> showMessage("$title added successfully", "success"); modal.remove(); onSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

private fun editSimpleMasterModal(apiPath: String, title: String, originalName: String, modalId: String, inputId: String, cancelBtnId: String, updateBtnId: String, deleteBtnId: String, onUpdateSuccess: () -> Unit, onDeleteSuccess: () -> Unit) {
    document.getElementById(modalId)?.remove()
    val modal = document.createElement("div")
    modal.id = modalId
    modal.asDynamic().style.cssText = "position: fixed; top: 0; left: 0; width: 100%; height: 100%; background-color: rgba(0,0,0,0.5); z-index: 10000; display: flex; align-items: center; justify-content: center;"
    val safeOriginal = originalName.replace("\"", "&quot;")
    modal.innerHTML = """
        <div style="background: white; border-radius: 10px; padding: 24px; max-width: 420px; width: 90%;">
            <h3 style="margin-top: 0;">Edit $title</h3>
            <label>$title</label>
            <input type="text" id="$inputId" value="$safeOriginal" style="width: 100%; padding: 10px 12px; margin: 8px 0; box-sizing: border-box;">
            <div style="display:flex; justify-content:space-between; gap:10px; margin-top: 16px;">
                <button id="$deleteBtnId" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #ef4444; background: white; color:#ef4444; cursor: pointer;">Delete</button>
                <div style="display:flex; gap:10px;">
                    <button id="$cancelBtnId" style="padding: 8px 16px; border-radius: 6px; border: 1px solid #d1d5db; background: white; cursor: pointer;">Cancel</button>
                    <button id="$updateBtnId" style="padding: 8px 16px; border-radius: 6px; border: none; background: #3b82f6; color:white; cursor: pointer;">Update</button>
                </div>
            </div>
        </div>
    """
    document.body?.appendChild(modal)
    document.getElementById(cancelBtnId)?.addEventListener("click", { _: Event -> modal.remove() })
    document.getElementById(updateBtnId)?.addEventListener("click", { _: Event ->
        val newValue = (document.getElementById(inputId) as? HTMLInputElement)?.value?.trim() ?: ""
        if (newValue.isEmpty()) { showMessage("$title is required", "error"); return@addEventListener }
        val body = js("{}"); body.value = newValue; body.originalValue = originalName
        val req = js("{}"); req.method = "PUT"; req.headers = js("{\"Content-Type\": \"application/json\"}"); req.body = JSON.stringify(body)
        window.fetch(apiUrl(apiPath), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to update')") }
            .then { _: dynamic -> showMessage("$title updated successfully", "success"); modal.remove(); onUpdateSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById(deleteBtnId)?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this $title?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl(apiPath + "?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("$title deleted successfully", "success"); modal.remove(); onDeleteSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

