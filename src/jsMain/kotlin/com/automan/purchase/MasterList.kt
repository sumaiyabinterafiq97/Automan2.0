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


/** Active column sort for Car Brands Map / Consignee Map tables (same UX as purchase list / simple master). */
private var carBrandMapSortField: String? = null
private val carBrandMapSortOrderByField: MutableMap<String, String> = mutableMapOf()

private var consigneeMapSortField: String? = null
private val consigneeMapSortOrderByField: MutableMap<String, String> = mutableMapOf()

/** Notes field removed from Consignee Map modal; snapshot keeps DB value on edit/duplicate saves. */
private var consigneeModalNotesSnapshot: String? = null


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
 * View-only grouping for Car Brands Map:
 * - Group rows by Chassis (normalized uppercase)
 * - Join other column values with ';' (deduped)
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
        groupedObj.rank = joinDistinctNonBlank(list.map { (it.rank ?: "").toString() })
        groupedObj.color = joinDistinctNonBlank(list.map { (it.color ?: "").toString() })
        groupedObj.driveType = joinDistinctNonBlank(list.map { (it.driveType ?: "").toString() })
        groupedObj.recycleFee = joinDistinctNonBlank(list.map { (it.recycleFee ?: "").toString() })
        groupedObj.carModelYear = joinDistinctNonBlank(list.map { (it.carModelYear ?: "").toString() })
        groupedObj.chassisNumber = joinDistinctNonBlank(list.map { (it.chassisNumber ?: "").toString() })
        groupedObj.manufactureYear = joinDistinctNonBlank(list.map { (it.manufactureYear ?: "").toString() })

        grouped.add(groupedObj)
    }

    return grouped
}

private fun populateCarBrandModalComboboxes() {
    populateEditableComboboxFromMasterMenu("carBrandBrand", "car_brands")
    populateEditableComboboxFromMasterMenu("carBrandFuel", "fuel")
    populateEditableComboboxFromMasterMenu("carBrandShift", "shift")
    populateEditableComboboxFromMasterMenu("carBrandColor", "color")
    populateEditableComboboxFromMasterMenu("carBrandGrade", "car_grade")

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
    val rankVal = (row.rank ?: "").toString()
    val colorVal = (row.color ?: "").toString()
    val driveType = (row.driveType ?: "").toString()
    val recycleFeeVal = (row.recycleFee ?: "").toString()
    val chassisNumberVal = (row.chassisNumber ?: "").toString()

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
    setChipFieldValue("carBrandRank", rankVal)
    setChipFieldValue("carBrandColor", colorVal)
    setChipFieldValue("carBrandDriveType", driveType)
    // Populate dynamic recycle fee rows from stored delimited string
    window.asDynamic().__prefillRecycleFee = recycleFeeVal
    window.asDynamic().__prefillChassisNumber = chassisNumberVal
    js("""
        if (typeof window.setRecycleFeeRowsValue === 'function') {
            window.setRecycleFeeRowsValue(window.__prefillRecycleFee || '');
        }
        if (typeof window.setChassisManufactureYearRowsValue === 'function') {
            window.setChassisManufactureYearRowsValue(window.__prefillChassisNumber || '');
        }
    """)

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

/** Distinct rixo companies from supplier map (`GET rixo-mapping/distinct-rixo-companies`). */
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
    "number_place" to "Number Place",
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
    val six = listOf("chassis", "chassisNumber", "carBrand", "carName", "fuel", "wd")
    return when (device) {
        "mobile" -> listOf("chassis", "chassisNumber", "carBrand")
        "tablet" -> six
        "desktop" -> six
        else -> six
    }
}

/** Keep Chassis Numbers immediately after Chassis when both are selected. */
fun normalizeCarBrandColumnOrder(columns: List<String>): List<String> {
    if (!columns.contains("chassisNumber")) return columns
    val without = columns.filter { it != "chassisNumber" }
    if (!without.contains("chassis")) return columns
    val mutable = without.toMutableList()
    val chassisIdx = mutable.indexOf("chassis")
    mutable.add(chassisIdx + 1, "chassisNumber")
    return mutable
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
    val filteredColumns = savedColumns.filter { it.isNotBlank() && it != "id" && it != "vehicleType" }
    val ordered = normalizeCarBrandColumnOrder(filteredColumns)
    return if (ordered.size > maxColumns) {
        defaultColumns
    } else {
        ordered.take(maxColumns)
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
        <div id="consigneeMapRoot" style="border: 1px solid #ddd; border-radius: 4px; padding: 20px; max-width: 1400px; margin: 0 auto; width: 100%; box-sizing: border-box;">
            <style>
                #consigneeMapRoot .consignee-map-search-filter-opt:hover{background:#f3f4f6!important;}
                #consigneeMapRoot .consignee-map-search-filter-opt--active{background:#eef2ff!important;font-weight:600;}
                #consigneeMapRoot .consignee-map-sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0;}
                #consigneeMapRoot #consigneeMapSearchFilterBtn:hover{background:#e8eaed!important;box-shadow:0 2px 8px rgba(0,0,0,0.08)!important;}
                #consigneeMapRoot #consigneeMapSearchFilterBtn:focus-visible{outline:2px solid #3b82f6;outline-offset:2px;}
                .consignee-map-table-shell{overflow-x:auto;border-radius:12px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,0.04);border:1px solid #eef2f7;}
                #consigneeMapRoot table.purchase-list-table thead th{position:sticky;top:0;z-index:1;background:#f9fafb;}
                .consignee-map-empty{display:flex;flex-direction:column;align-items:center;text-align:center;color:#475569;padding:44px 16px;gap:8px;}
                .consignee-map-empty strong{color:#0f172a;}
                .consignee-map-pager{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:10px;padding:14px 4px 4px;color:#475569;font-size:14px;}
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
                @media (max-width:767px){
                    #consigneeMapRoot{padding:14px;}
                    #consigneeMapRoot .consignee-map-header-row{flex-direction:column;align-items:stretch;gap:12px;}
                    #consigneeMapRoot .consignee-map-header-row h2{font-size:22px;}
                    #consigneeMapRoot #consigneeColumnFilterBtn,
                    #consigneeMapRoot #addConsigneeBtn{width:100%;justify-content:center;}
                }
            </style>
            <div class="consignee-map-header-row" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;gap:12px;">
                <h2 style="margin:0;color:#111827;font-size:28px;font-weight:700;">Consignee Map</h2>
                <button type="button" id="consigneeColumnFilterBtn" title="Column filter" style="padding:8px 16px;background-color:#6c757d;color:#fff;border:none;border-radius:4px;cursor:pointer;font-size:14px;display:inline-flex;align-items:center;gap:6px;flex-shrink:0;">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 17h6v-2H3v2zm0-5h6v-2H3v2zm0-5h6V5H3v2zm10 10h8v-2h-8v2zm0-5h8V7h-8v2zm0-5h8V2h-8v2z" fill="currentColor"/></svg>
                    Column Filter
                </button>
            </div>
            <div style="background:#fff;border:1px solid #e5e7eb;border-radius:8px;padding:20px;margin-bottom:20px;">
                <div style="display:flex;align-items:center;gap:10px;width:100%;min-width:0;">
                    <div style="position:relative;flex:1;display:flex;align-items:center;min-width:0;border:1px solid #e5e7eb;border-radius:999px;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,0.06);">
                        <span style="position:absolute;left:14px;top:50%;transform:translateY(-50%);color:#9ca3af;display:flex;" aria-hidden="true">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                        </span>
                        <input type="text" id="consigneeMapSearchInput" role="searchbox" autocomplete="off" inputmode="search" placeholder="Type to search…" aria-label="Search consignee map" style="width:100%;box-sizing:border-box;padding:11px 38px 11px 40px;border:none;font-size:14px;background:transparent;border-radius:999px;outline:none;" />
                        <button type="button" id="consigneeMapSearchClearBtn" title="Clear search" aria-label="Clear search" style="position:absolute;right:8px;top:50%;transform:translateY(-50%);border:none;background:transparent;color:#9ca3af;cursor:pointer;font-size:20px;padding:4px 8px;min-height:36px;min-width:36px;">×</button>
                    </div>
                    <div style="position:relative;flex-shrink:0;">
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
            </div>
            <div style="margin-bottom:20px;">
                <button type="button" id="addConsigneeBtn" style="padding:12px 24px;background-color:#059669;color:#fff;border:none;border-radius:6px;cursor:pointer;font-size:14px;font-weight:600;box-shadow:0 2px 4px rgba(0,0,0,0.1);">
                    + Add New Consignee
                </button>
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
                if (routeStartsWith("/master/consignee")) {
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
                if (routeStartsWith("/master/car-brands")) {
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
            val carBrandSortable = setOf("chassis", "carBrand", "carName", "fuel")
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
                "rank" to "Rank",
                "color" to "Color",
                "driveType" to "Drive Type",
                "recycleFee" to "Recycle Fees",
                "carModelYear" to "Registration Date",
                "chassisNumber" to "Chassis Numbers",
                "manufactureYear" to "Manufacture Years"
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
            val carBrandSortableCols = setOf("chassis", "carBrand", "carName", "fuel")
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
                val rankVal = (mapping.rank ?: "").toString()
                val colorVal = (mapping.color ?: "").toString()
                val driveTypeVal = (mapping.driveType ?: "").toString()
                val recycleFeeVal = (mapping.recycleFee ?: "").toString()
                val carModelYearVal = (mapping.carModelYear ?: "").toString()
                val chassisNumberVal = (mapping.chassisNumber ?: "").toString()
                val manufactureYearVal = (mapping.manufactureYear ?: "").toString()
                val carModelYearDisplay = carModelYearVal.split(";").map { token ->
                    val m = Regex("""^(\d{4})-(\d{2})$""").find(token.trim())
                    if (m != null) "${m.groupValues[2]}/${m.groupValues[1]}" else token.trim()
                }.filter { it.isNotEmpty() }.joinToString(";")

                val rowDataJs =
                    "window.__carBrandRowData={chassis:'${escapeJsString(chassis)}',carBrand:'${escapeJsString(carBrand)}',carName:'${escapeJsString(carName)}',fuel:'${escapeJsString(fuel)}',wd:'${escapeJsString(wd)}',shift:'${escapeJsString(shift)}',grade:'${escapeJsString(grade)}',cc:'${escapeJsString(cc)}',seat:'${escapeJsString(seat)}',door:'${escapeJsString(door)}',rank:'${escapeJsString(rankVal)}',color:'${escapeJsString(colorVal)}',driveType:'${escapeJsString(driveTypeVal)}',recycleFee:'${escapeJsString(recycleFeeVal)}',carModelYear:'${escapeJsString(carModelYearVal)}',chassisNumber:'${escapeJsString(chassisNumberVal)}',manufactureYear:'${escapeJsString(manufactureYearVal)}'};"
                
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
                        "rank" -> rankVal
                        "color" -> colorVal
                        "driveType" -> driveTypeVal
                        "recycleFee" -> recycleFeeVal
                        "carModelYear" -> carModelYearDisplay
                        "chassisNumber" -> chassisNumberVal
                        "manufactureYear" -> manufactureYearVal
                        else -> ""
                    }
                    val cellStyle = when (columnKey) {
                        "carBrand" -> "padding: 12px 16px; color: #111827; font-size: 14px; font-weight: 500; vertical-align: top;"
                        "chassis", "carName" -> "padding: 12px 16px; color: #111827; font-size: 14px; vertical-align: top;"
                        else -> "padding: 12px 16px; color: #374151; font-size: 14px; vertical-align: top;"
                    }
                    val cellInner = when (columnKey) {
                        "recycleFee" -> formatRecycleFeeChipHtml(value)
                        "chassisNumber" -> formatChassisNumberOnlyChipHtml(value)
                        else -> formatCarBrandMapValueChipHtml(value)
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
            val sortKeys = listOf("chassis", "carBrand", "carName", "fuel")
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
            val carBrandSortable = setOf("chassis", "carBrand", "carName", "fuel")
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
        "rank" to "Rank",
        "color" to "Color",
        "driveType" to "Drive Type",
        "recycleFee" to "Recycle Fees",
        "carModelYear" to "Registration Date",
        "chassisNumber" to "Chassis Numbers",
        "manufactureYear" to "Manufacture Years"
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
        val rankVal = (mapping.rank ?: "").toString()
        val colorVal = (mapping.color ?: "").toString()
        val driveTypeVal = (mapping.driveType ?: "").toString()
        val recycleFeeVal = (mapping.recycleFee ?: "").toString()
        val carModelYearVal = (mapping.carModelYear ?: "").toString()
        val chassisNumberVal = (mapping.chassisNumber ?: "").toString()
        val manufactureYearVal = (mapping.manufactureYear ?: "").toString()
        val carModelYearDisplay = carModelYearVal.split(";").map { token ->
            val m = Regex("""^(\d{4})-(\d{2})$""").find(token.trim())
            if (m != null) "${m.groupValues[2]}/${m.groupValues[1]}" else token.trim()
        }.filter { it.isNotEmpty() }.joinToString(";")

        val rowDataJs =
            "window.__carBrandRowData={chassis:'${escapeJsString(chassis)}',carBrand:'${escapeJsString(carBrand)}',carName:'${escapeJsString(carName)}',fuel:'${escapeJsString(fuel)}',wd:'${escapeJsString(wd)}',shift:'${escapeJsString(shift)}',grade:'${escapeJsString(grade)}',cc:'${escapeJsString(cc)}',seat:'${escapeJsString(seat)}',door:'${escapeJsString(door)}',rank:'${escapeJsString(rankVal)}',color:'${escapeJsString(colorVal)}',driveType:'${escapeJsString(driveTypeVal)}',recycleFee:'${escapeJsString(recycleFeeVal)}',carModelYear:'${escapeJsString(carModelYearVal)}',chassisNumber:'${escapeJsString(chassisNumberVal)}',manufactureYear:'${escapeJsString(manufactureYearVal)}'};"
        
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
                "rank" -> rankVal
                "color" -> colorVal
                "driveType" -> driveTypeVal
                "recycleFee" -> recycleFeeVal
                "carModelYear" -> carModelYearDisplay
                "chassisNumber" -> chassisNumberVal
                "manufactureYear" -> manufactureYearVal
                else -> ""
            }
            
            if (value.isNotEmpty()) {
                val displayValue = when (columnKey) {
                    "recycleFee" -> formatRecycleFeeChipHtml(value)
                    "chassisNumber" -> formatChassisNumberOnlyChipHtml(value)
                    else -> formatCarBrandMapValueChipHtml(value)
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
        "rank" to "Rank",
        "color" to "Color",
        "driveType" to "Drive Type",
        "recycleFee" to "Recycle Fees",
        "carModelYear" to "Registration Date",
        "chassisNumber" to "Chassis Numbers",
        "manufactureYear" to "Manufacture Years"
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
    
    // Save to localStorage (Chassis Numbers stays beside Chassis)
    val orderedColumns = normalizeCarBrandColumnOrder(selectedColumns)
    safeLocalStorageSet("selectedCarBrandColumns", JSON.stringify(orderedColumns.toTypedArray()))
    
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
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Car Name</label>
                            ${createChipInput("carBrandCarName", "Type Car Name", CAR_BRAND_CAR_NAME_MAX_LEN)}
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
                                ${createChipMultiSelectCombobox("carBrandColor", "Select Color")}
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
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Recycle Fees <span style="color: #6b7280; font-weight: 400; font-size: 12px;">(by Registration Date)</span></label>
                            <div id="recycleFeeRows" style="display: flex; flex-direction: column; gap: 8px; margin-bottom: 8px;"></div>
                            <button type="button" id="addRecycleFeeRow" style="display: inline-flex; align-items: center; gap: 6px; padding: 7px 14px; background: #f3f4f6; border: 1px dashed #d1d5db; border-radius: 6px; color: #374151; font-size: 13px; cursor: pointer; transition: background 0.15s;">
                                <span style="font-size: 16px; line-height: 1;">+</span> Add Registration Date
                            </button>
                        </div>
                        <div style="margin-bottom: 20px;">
                            <label style="display: block; margin-bottom: 8px; font-weight: 600; color: #374151; font-size: 14px;">Manufacture Year <span style="color: #6b7280; font-weight: 400; font-size: 12px;">(by Chassis Number)</span></label>
                            <div id="chassisManufactureYearRows" style="display: flex; flex-direction: column; gap: 8px; margin-bottom: 8px;"></div>
                            <button type="button" id="addChassisManufactureYearRow" style="display: inline-flex; align-items: center; gap: 6px; padding: 7px 14px; background: #f3f4f6; border: 1px dashed #d1d5db; border-radius: 6px; color: #374151; font-size: 13px; cursor: pointer; transition: background 0.15s;">
                                <span style="font-size: 16px; line-height: 1;">+</span> Add Chassis Number
                            </button>
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

    // Inject JS helpers for dynamic recycle fee rows
    js("""
        window.addRecycleFeeRow = function(productionDate, fee) {
            var container = document.getElementById('recycleFeeRows');
            if (!container) return;
            var rowIdx = container.children.length;
            var row = document.createElement('div');
            row.style.cssText = 'display:flex; align-items:center; gap:8px;';
            row.innerHTML =
                '<div style="position:relative; flex:1; display:flex; gap:6px; align-items:center;">' +
                    '<div style="position:relative; flex:1;">' +
                        '<input type="text" class="recycle-fee-date-input" maxlength="7" inputmode="numeric" autocomplete="off" ' +
                            'placeholder="MM/YYYY" value="' + (productionDate || '') + '" ' +
                            'style="width:100%; padding:9px 10px; border:1px solid #d1d5db; border-radius:6px; font-size:13px; box-sizing:border-box; font-family:inherit;">' +
                        '<span class="recycle-fee-date-hint" style="position:absolute; left:10px; top:50%; transform:translateY(-50%); color:#9ca3af; pointer-events:none; font-size:13px; display:' + (productionDate ? 'none' : 'block') + ';">MM/YYYY</span>' +
                    '</div>' +
                    '<button type="button" class="recycle-fee-date-btn" title="Open month picker" ' +
                        'style="flex-shrink:0; padding:9px 10px; border:1px solid #d1d5db; background:#f9fafb; border-radius:6px; cursor:pointer; font-size:14px; display:flex; align-items:center; justify-content:center; box-sizing:border-box;">📅</button>' +
                    '<input type="hidden" class="recycle-fee-date-canonical" value="">' +
                '</div>' +
                '<input type="text" class="recycle-fee-amount-input money-input" inputmode="decimal" autocomplete="off" ' +
                    'placeholder="Fee (¥)" value="' + (fee || '') + '" ' +
                    'style="flex:1; padding:9px 10px; border:1px solid #d1d5db; border-radius:6px; font-size:13px; box-sizing:border-box; font-family:inherit;">' +
                '<button type="button" class="recycle-fee-delete-btn" style="flex-shrink:0; padding:6px 10px; background:#fee2e2; border:1px solid #fca5a5; border-radius:6px; color:#dc2626; font-size:14px; cursor:pointer; transition:background 0.15s;" title="Remove row">✕</button>';
            // Delete row
            row.querySelector('.recycle-fee-delete-btn').addEventListener('click', function() { row.remove(); });
            // MM/YYYY mask on date input
            var dateInput = row.querySelector('.recycle-fee-date-input');
            var hint = row.querySelector('.recycle-fee-date-hint');
            var dateBtn = row.querySelector('.recycle-fee-date-btn');
            var canonicalInput = row.querySelector('.recycle-fee-date-canonical');
            var monthPicker = null;

            function syncRecycleFeeCanonical() {
                var current = (dateInput.value || '').trim();
                var match = current.match(/^(\d{2})\/(\d{4})$/);
                if (match) {
                    var mm = parseInt(match[1], 10);
                    var yyyy = parseInt(match[2], 10);
                    if (mm >= 0 && mm <= 12) {
                        var paddedMm = mm < 10 ? '0' + mm : '' + mm;
                        canonicalInput.value = yyyy + '-' + paddedMm;
                        return;
                    }
                }
                if (!current) canonicalInput.value = '';
            }

            function ensureMonthPicker() {
                if (monthPicker) {
                    if (typeof window.positionHiddenPickerOverButton === 'function') {
                        window.positionHiddenPickerOverButton(monthPicker, dateBtn);
                    }
                    return monthPicker;
                }
                monthPicker = document.createElement('input');
                monthPicker.type = 'month';
                monthPicker.tabIndex = -1;
                monthPicker.setAttribute('aria-hidden', 'true');
                monthPicker.style.opacity = '0';
                monthPicker.style.border = 'none';
                monthPicker.style.padding = '0';
                monthPicker.style.margin = '0';
                monthPicker.style.setProperty('overflow', 'hidden');
                monthPicker.style.zIndex = '5';
                monthPicker.style.cursor = 'pointer';
                dateBtn.parentElement.appendChild(monthPicker);
                if (typeof window.positionHiddenPickerOverButton === 'function') {
                    window.positionHiddenPickerOverButton(monthPicker, dateBtn);
                }
                monthPicker.addEventListener('change', function() {
                    var iso = monthPicker.value.trim();
                    if (iso.match(/^\d{4}-\d{2}$/)) {
                        var parts = iso.split('-');
                        var formatted = parts[1] + '/' + parts[0];
                        dateInput.value = formatted;
                        lastAcceptedText = formatted;
                        canonicalInput.value = iso;
                        hint.style.display = 'none';
                    }
                });
                return monthPicker;
            }

            var lastAcceptedText = dateInput.value || '';
            syncRecycleFeeCanonical();
            dateInput.addEventListener('input', function(e) {
                var raw = dateInput.value;
                var digits = raw.replace(/\D/g, '').substring(0, 6);
                var next = '';
                if (digits.length > 0) {
                    var mm = digits.substring(0, 2);
                    if (digits.length <= 2) {
                        next = mm;
                    } else {
                        var yyyy = digits.substring(2);
                        next = mm + '/' + yyyy;
                    }
                }
                
                var isValid = true;
                if (next !== '') {
                    if (!/^\d{0,2}(\/\d{0,4})?$/.test(next)) {
                        isValid = false;
                    } else {
                        var parts = next.split('/');
                        var mmVal = parts[0] || '';
                        var yyyyVal = parts[1] || '';
                        if (mmVal.length === 1) {
                            if (mmVal[0] !== '0' && mmVal[0] !== '1') isValid = false;
                        } else if (mmVal.length === 2) {
                            var mNum = parseInt(mmVal, 10);
                            if (isNaN(mNum) || mNum < 0 || mNum > 12) isValid = false;
                        }
                        if (yyyyVal.length > 4) isValid = false;
                    }
                }
                
                if (!isValid) {
                    dateInput.value = lastAcceptedText;
                    return;
                }
                
                dateInput.value = next;
                lastAcceptedText = next;
                hint.style.display = next.length > 0 ? 'none' : 'block';
                syncRecycleFeeCanonical();
            });

            dateInput.addEventListener('keydown', function(ev) {
                var key = ev.key;
                var isDigit = key.length === 1 && key >= '0' && key <= '9';
                var isSep = key === '/' || key === '-';
                var navKeys = ['Backspace', 'Delete', 'ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Tab', 'Enter', 'Home', 'End'];
                if (ev.ctrlKey || ev.metaKey || ev.altKey) return;
                if (isDigit || isSep || navKeys.indexOf(key) !== -1) return;
                ev.preventDefault();
            });

            dateInput.addEventListener('focus', function() { hint.style.display = 'none'; });

            dateInput.addEventListener('blur', function() {
                var current = dateInput.value.trim();
                if (current === '') {
                    canonicalInput.value = '';
                    hint.style.display = 'block';
                    lastAcceptedText = '';
                    return;
                }
                var match = current.match(/^(\d{1,2})\/(\d{4})$/);
                if (match) {
                    var mm = parseInt(match[1], 10);
                    var yyyy = parseInt(match[2], 10);
                    if (mm >= 0 && mm <= 12) {
                        var paddedMm = mm < 10 ? '0' + mm : '' + mm;
                        var formatted = paddedMm + '/' + yyyy;
                        dateInput.value = formatted;
                        lastAcceptedText = formatted;
                        canonicalInput.value = yyyy + '-' + paddedMm;
                        hint.style.display = 'none';
                        return;
                    }
                }
            });

            dateBtn.addEventListener('click', function(e) {
                e.preventDefault();
                syncRecycleFeeCanonical();
                var canonical = (canonicalInput.value || '').trim();
                var picker = ensureMonthPicker();
                if (typeof window.positionHiddenPickerOverButton === 'function') {
                    window.positionHiddenPickerOverButton(picker, dateBtn);
                }
                // Month input cannot represent 00 — seed January so 📅 still opens (typing 00 remains allowed).
                if (/^\d{4}-00$/.test(canonical)) {
                    picker.value = canonical.substring(0, 4) + '-01';
                } else if (/^\d{4}-\d{2}$/.test(canonical)) {
                    picker.value = canonical;
                } else {
                    picker.value = new Date().getFullYear() + '-' + String(new Date().getMonth() + 1).padStart(2, '0');
                }
                try {
                    picker.showPicker();
                } catch (err) {
                    picker.click();
                }
            });

            container.appendChild(row);
        };

        window.getRecycleFeeRowsValue = function() {
            var rows = document.querySelectorAll('#recycleFeeRows > div');
            var pairs = [];
            rows.forEach(function(row) {
                var dateEl = row.querySelector('.recycle-fee-date-input');
                var feeEl = row.querySelector('.recycle-fee-amount-input');
                if (!dateEl || !feeEl) return;
                var dateVal = (dateEl.value || '').trim();
                var feeVal = (feeEl.value || '').replace(/[^0-9.]/g, '').trim();
                if (dateVal && feeVal) {
                    // Normalize MM/YYYY to YYYY-MM for storage
                    var normalized = dateVal;
                    var mmYYYY = dateVal.match(/^(\d{2})\/(\d{4})$/);
                    if (mmYYYY) normalized = mmYYYY[2] + '-' + mmYYYY[1];
                    pairs.push(normalized + ':' + feeVal);
                }
            });
            return pairs.join(';');
        };

        window.setRecycleFeeRowsValue = function(delimitedStr) {
            var container = document.getElementById('recycleFeeRows');
            if (!container) return;
            container.innerHTML = '';
            if (!delimitedStr || !delimitedStr.trim()) return;
            var pairs = delimitedStr.split(';');
            pairs.forEach(function(pair) {
                var colonIdx = pair.lastIndexOf(':');
                if (colonIdx <= 0) return;
                var dateToken = pair.substring(0, colonIdx).trim();
                var feeToken = pair.substring(colonIdx + 1).trim();
                // Convert YYYY-MM to MM/YYYY for display
                var displayDate = dateToken;
                var yyyyMM = dateToken.match(/^(\d{4})-(\d{2})$/);
                if (yyyyMM) displayDate = yyyyMM[2] + '/' + yyyyMM[1];
                if (typeof window.addRecycleFeeRow === 'function') window.addRecycleFeeRow(displayDate, feeToken);
            });
        };

        window.addChassisManufactureYearRow = function(chassisNumber, manufactureYear) {
            var container = document.getElementById('chassisManufactureYearRows');
            if (!container) return;
            var row = document.createElement('div');
            row.style.cssText = 'display:flex; align-items:center; gap:8px;';
            row.innerHTML =
                '<input type="text" class="chassis-number-input" autocomplete="off" placeholder="Chassis Number" value="' + (chassisNumber || '') + '" ' +
                    'style="flex:1; padding:9px 10px; border:1px solid #d1d5db; border-radius:6px; font-size:13px; box-sizing:border-box; font-family:inherit;">' +
                '<div style="position:relative; flex:1; display:flex; gap:6px; align-items:center;">' +
                    '<div style="position:relative; flex:1;">' +
                        '<input type="text" class="chassis-manufacture-year-input" maxlength="4" inputmode="numeric" autocomplete="off" value="' + (manufactureYear || '') + '" ' +
                            'style="width:100%; padding:9px 10px; border:1px solid #d1d5db; border-radius:6px; font-size:13px; box-sizing:border-box; font-family:inherit;">' +
                        '<span class="chassis-manufacture-year-hint" style="position:absolute; left:10px; top:50%; transform:translateY(-50%); color:#9ca3af; pointer-events:none; font-size:13px; display:' + (manufactureYear ? 'none' : 'block') + ';">YYYY</span>' +
                    '</div>' +
                    '<button type="button" class="chassis-manufacture-year-btn" title="Open year picker" ' +
                        'style="flex-shrink:0; padding:9px 10px; border:1px solid #d1d5db; background:#f9fafb; border-radius:6px; cursor:pointer; font-size:14px;">📅</button>' +
                '</div>' +
                '<button type="button" class="chassis-manufacture-year-delete-btn" style="flex-shrink:0; padding:6px 10px; background:#fee2e2; border:1px solid #fca5a5; border-radius:6px; color:#dc2626; font-size:14px; cursor:pointer;" title="Remove row">✕</button>';
            row.querySelector('.chassis-manufacture-year-delete-btn').addEventListener('click', function() { row.remove(); });
            var numberInput = row.querySelector('.chassis-number-input');
            var yearInput = row.querySelector('.chassis-manufacture-year-input');
            var yearHint = row.querySelector('.chassis-manufacture-year-hint');
            var yearBtn = row.querySelector('.chassis-manufacture-year-btn');
            numberInput.addEventListener('input', function() {
                var raw = numberInput.value || '';
                var cleaned = raw.replace(/[\s-]/g, '').replace(/[^A-Za-z0-9]/g, '');
                if (cleaned !== raw) numberInput.value = cleaned;
            });
            yearInput.addEventListener('input', function() {
                var digits = (yearInput.value || '').replace(/\D/g, '').substring(0, 4);
                yearInput.value = digits;
                if (yearHint) yearHint.style.display = digits.length > 0 ? 'none' : 'block';
            });
            yearInput.addEventListener('focus', function() { if (yearHint) yearHint.style.display = 'none'; });
            yearInput.addEventListener('blur', function() {
                if (yearHint) yearHint.style.display = (yearInput.value || '').trim().length > 0 ? 'none' : 'block';
            });
            yearBtn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                if (typeof window.openStrictYearPickerForButton === 'function') {
                    window.openStrictYearPickerForButton(yearBtn, (yearInput.value || '').trim(), function(year) {
                        yearInput.value = year;
                        if (yearHint) yearHint.style.display = 'none';
                    });
                }
            });
            container.appendChild(row);
        };

        window.getChassisManufactureYearRowsValue = function() {
            var rows = document.querySelectorAll('#chassisManufactureYearRows > div');
            var pairs = [];
            rows.forEach(function(row) {
                var numberEl = row.querySelector('.chassis-number-input');
                var yearEl = row.querySelector('.chassis-manufacture-year-input');
                if (!numberEl || !yearEl) return;
                var numberVal = (numberEl.value || '').trim();
                var yearVal = (yearEl.value || '').trim();
                if (numberVal && /^\d{4}$/.test(yearVal)) {
                    pairs.push(numberVal + ':' + yearVal);
                }
            });
            return pairs.join(';');
        };

        window.setChassisManufactureYearRowsValue = function(delimitedStr) {
            var container = document.getElementById('chassisManufactureYearRows');
            if (!container) return;
            container.innerHTML = '';
            if (!delimitedStr || !delimitedStr.trim()) return;
            var pairs = delimitedStr.split(';');
            pairs.forEach(function(pair) {
                var colonIdx = pair.lastIndexOf(':');
                if (colonIdx <= 0) return;
                var numberToken = pair.substring(0, colonIdx).trim();
                var yearToken = pair.substring(colonIdx + 1).trim();
                if (typeof window.addChassisManufactureYearRow === 'function') {
                    window.addChassisManufactureYearRow(numberToken, yearToken);
                }
            });
        };
    """)

    // Wire up "Add Production Date" button
    document.getElementById("addRecycleFeeRow")?.addEventListener("click", { _: Event ->
        js("window.addRecycleFeeRow('', '')")
    })
    document.getElementById("addChassisManufactureYearRow")?.addEventListener("click", { _: Event ->
        js("window.addChassisManufactureYearRow('', '')")
    })

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
                setChipFieldValue("carBrandRank", (data.rank ?: "").toString())
                setChipFieldValue("carBrandColor", (data.color ?: "").toString())
                setChipFieldValue("carBrandDriveType", (data.driveType ?: "").toString())
                // Populate dynamic recycle fee rows from stored delimited string
                val recycleStr = (data.recycleFee ?: "").toString()
                window.asDynamic().__prefillRecycleFee = recycleStr
                js("if (typeof window.setRecycleFeeRowsValue === 'function') window.setRecycleFeeRowsValue(window.__prefillRecycleFee || '')")
                val chassisNumberStr = (data.chassisNumber ?: "").toString()
                window.asDynamic().__prefillChassisNumber = chassisNumberStr
                js("if (typeof window.setChassisManufactureYearRowsValue === 'function') window.setChassisManufactureYearRowsValue(window.__prefillChassisNumber || '')")
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
    carBrandData.rank = getChipFieldValue("carBrandRank").takeIf { it.isNotEmpty() } ?: null
    carBrandData.color = getChipFieldValue("carBrandColor").takeIf { it.isNotEmpty() } ?: null
    carBrandData.driveType = getChipFieldValue("carBrandDriveType").takeIf { it.isNotEmpty() } ?: null
    val recFeeStr = js("window.getRecycleFeeRowsValue ? window.getRecycleFeeRowsValue() : ''").unsafeCast<String>().trim()
    carBrandData.recycleFee = recFeeStr.takeIf { it.isNotEmpty() } ?: null
    val derivedModelYears = if (recFeeStr.isNotEmpty()) {
        recFeeStr.split(";").map { it.substringBefore(":") }.filter { it.isNotEmpty() }.joinToString(";")
    } else ""
    carBrandData.carModelYear = derivedModelYears.takeIf { it.isNotEmpty() } ?: null
    val chassisNumberStr = js("window.getChassisManufactureYearRowsValue ? window.getChassisManufactureYearRowsValue() : ''").unsafeCast<String>().trim()
    carBrandData.chassisNumber = chassisNumberStr.takeIf { it.isNotEmpty() } ?: null
    val derivedManufactureYears = if (chassisNumberStr.isNotEmpty()) {
        chassisNumberStr.split(";").map { it.substringAfter(":") }.filter { it.isNotEmpty() }.joinToString(";")
    } else ""
    carBrandData.manufactureYear = derivedManufactureYears.takeIf { it.isNotEmpty() } ?: null
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
    navigateToApp("/master/set/country")
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

fun showMasterRixoCompanyPage() {
    navigateToApp("/master/rixo-company")
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
    navigateToApp("/master/stock-location")
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
    navigateToApp("/master/set/repair_company")
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
    navigateToApp("/master/set/bank_accounts")
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
    navigateToApp("/master/set/venue_id")
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
    navigateToApp("/master/set/pol")
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
    navigateToApp("/master/set/pod")
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
    navigateToApp("/master/set/fuel")
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
    navigateToApp("/master/car-grade")
}
fun loadMasterCarGrade() { loadSimpleMaster("master-menu/car_grade", "carGradeFilter", "carGradeTable", "Car Grade", carGradeCurrentPage, carGradeItemsPerPage, allCarGrades, { carGradeCurrentPage = it }, { allCarGrades = it }, ::showEditCarGradeModal, "car-grade-edit-btn", "data-car-grade", "carGradePrevPage", "carGradeNextPage", ::loadMasterCarGrade) }
fun showAddCarGradeModal() { addSimpleMasterModal("master-menu/car_grade", "Car Grade", "carGradeEditModal", "carGradeModalInput", "carGradeModalCancelBtn", "carGradeModalAddBtn", { carGradeCurrentPage = 1; loadMasterCarGrade() }) }
fun showEditCarGradeModal(originalName: String) { editSimpleMasterModal("master-menu/car_grade", "Car Grade", originalName, "carGradeEditModal", "carGradeModalInput", "carGradeModalCancelBtn", "carGradeModalUpdateBtn", "carGradeModalDeleteBtn", { loadMasterCarGrade() }, { carGradeCurrentPage = 1; loadMasterCarGrade() }) }

fun showMasterCarShiftPage() {
    navigateToApp("/master/set/shift")
    showDynamicMasterSetPage("shift")
}
fun loadMasterCarShift() { loadSimpleMaster("master-menu/shift", "carShiftFilter", "carShiftTable", "Car Shift", carShiftCurrentPage, carShiftItemsPerPage, allCarShifts, { carShiftCurrentPage = it }, { allCarShifts = it }, ::showEditCarShiftModal, "car-shift-edit-btn", "data-car-shift", "carShiftPrevPage", "carShiftNextPage", ::loadMasterCarShift) }
fun showAddCarShiftModal() { addSimpleMasterModal("master-menu/shift", "Car Shift", "carShiftEditModal", "carShiftModalInput", "carShiftModalCancelBtn", "carShiftModalAddBtn", { carShiftCurrentPage = 1; loadMasterCarShift() }) }
fun showEditCarShiftModal(originalName: String) { editSimpleMasterModal("master-menu/shift", "Car Shift", originalName, "carShiftEditModal", "carShiftModalInput", "carShiftModalCancelBtn", "carShiftModalUpdateBtn", "carShiftModalDeleteBtn", { loadMasterCarShift() }, { carShiftCurrentPage = 1; loadMasterCarShift() }) }

fun showMasterTypeOfVehiclesPage() {
    navigateToApp("/master/set/type_of_vehicle")
    showDynamicMasterSetPage("type_of_vehicle")
}
fun loadMasterTypeOfVehicles() { loadSimpleMaster("master-menu/type_of_vehicle", "typeOfVehiclesFilter", "typeOfVehiclesTable", "Type of Vehicles", typeOfVehiclesCurrentPage, typeOfVehiclesItemsPerPage, allTypeOfVehicles, { typeOfVehiclesCurrentPage = it }, { allTypeOfVehicles = it }, ::showEditTypeOfVehiclesModal, "type-of-vehicles-edit-btn", "data-type-of-vehicles", "typeOfVehiclesPrevPage", "typeOfVehiclesNextPage", ::loadMasterTypeOfVehicles) }
fun showAddTypeOfVehiclesModal() { addSimpleMasterModal("master-menu/type_of_vehicle", "Type of Vehicles", "typeOfVehiclesEditModal", "typeOfVehiclesModalInput", "typeOfVehiclesModalCancelBtn", "typeOfVehiclesModalAddBtn", { typeOfVehiclesCurrentPage = 1; loadMasterTypeOfVehicles() }) }
fun showEditTypeOfVehiclesModal(originalName: String) { editSimpleMasterModal("master-menu/type_of_vehicle", "Type of Vehicles", originalName, "typeOfVehiclesEditModal", "typeOfVehiclesModalInput", "typeOfVehiclesModalCancelBtn", "typeOfVehiclesModalUpdateBtn", "typeOfVehiclesModalDeleteBtn", { loadMasterTypeOfVehicles() }, { typeOfVehiclesCurrentPage = 1; loadMasterTypeOfVehicles() }) }

fun showMasterNumberPlacePage() {
    navigateToApp("/master/number-place")
    showDynamicMasterSetPage("number_place")
}

fun showDynamicMasterSetPage(fieldName: String) {
    val normalizedField = fieldName.trim().lowercase()
    if (normalizedField.isEmpty()) {
        showMessage("Invalid master set", "error")
        return
    }
    if (normalizedField == "car_grade") {
        navigateToApp("/master/car-grade")
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

private fun invalidateNumberCutPlaceCacheIfNeeded(apiPath: String) {
    if (apiPath.endsWith("number_place")) {
        invalidateNumberCutPlaceOptionsCache()
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
            .then { _: dynamic -> showMessage("$title added successfully", "success"); modal.remove(); invalidateNumberCutPlaceCacheIfNeeded(apiPath); onSuccess() }
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
            .then { _: dynamic -> showMessage("$title updated successfully", "success"); modal.remove(); invalidateNumberCutPlaceCacheIfNeeded(apiPath); onUpdateSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
    document.getElementById(deleteBtnId)?.addEventListener("click", { _: Event ->
        if (!window.confirm("Delete this $title?")) return@addEventListener
        val encoded = js("encodeURIComponent")(originalName) as String
        val req = js("{}"); req.method = "DELETE"
        window.fetch(apiUrl(apiPath + "?value=" + encoded), req)
            .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('Failed to delete')") }
            .then { _: dynamic -> showMessage("$title deleted successfully", "success"); modal.remove(); invalidateNumberCutPlaceCacheIfNeeded(apiPath); onDeleteSuccess() }
            .catch { e: dynamic -> showMessage("Error: ${e.message}", "error") }
    })
}

