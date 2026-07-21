package com.automan.purchase

import kotlin.js.asDynamic
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.*
import org.w3c.dom.events.Event
import org.w3c.fetch.Headers
import org.w3c.fetch.RequestInit
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.ApiClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Car Booking Functions
// Note: Global variables (currentSelectedCountry, carBookingDisplayedCars, etc.) are defined in MinimalPurchaseApp.kt

/** Set when opening booking from Shipping History edit; allows Calculate for that same booking id. */
private var carBookingShippingHistoryEditBookingIdNormalized: String? = null

/** Shipping-history row ids for the current recreate session (#/recalculate-booking). */
var carBookingShippingRecreateRowIds: MutableList<Long> = mutableListOf()

/** Uppercase chassis token → shipping_history row id (for remove-chassis). */
var carBookingShippingRecreateChassisToHistoryId: MutableMap<String, Long> = mutableMapOf()

/** Uppercase chassis token → amount from history (for list Update without recalculation). */
var carBookingShippingRecreateChassisAmounts: MutableMap<String, String> = mutableMapOf()

fun isCarBookingRecreateSession(): Boolean =
    routeStartsWith("/recalculate-booking")

fun isCarBookingRecreateCalculationSession(): Boolean =
    routeStartsWith("/recalculate-booking/recalculation")

private fun clearCarBookingShippingRecreateSessionData() {
    carBookingShippingRecreateRowIds.clear()
    carBookingShippingRecreateChassisToHistoryId.clear()
    carBookingShippingRecreateChassisAmounts.clear()
    window.sessionStorage.removeItem(SHIPPING_RECREATE_META_SESSION_KEY)
}

private fun persistShippingRecreateMeta() {
    if (!isCarBookingRecreateSession()) return
    val o = js("{}")
    val idsArr = js("[]")
    for (id in carBookingShippingRecreateRowIds.distinct()) {
        idsArr.push(id.toDouble())
    }
    o.rowIds = idsArr
    o.bookingIdNormalized = carBookingShippingHistoryEditBookingIdNormalized ?: ""
    window.sessionStorage.setItem(SHIPPING_RECREATE_META_SESSION_KEY, JSON.stringify(o))
}

private fun restoreShippingRecreateMetaFromSession() {
    if (!isCarBookingRecreateSession()) return
    val raw = window.sessionStorage.getItem(SHIPPING_RECREATE_META_SESSION_KEY)?.takeIf { it.isNotEmpty() }
        ?: return
    val o: dynamic = try {
        JSON.parse(raw)
    } catch (_: Throwable) {
        return
    }
    val idsDyn = o.rowIds
    if (js("Array.isArray(idsDyn)").unsafeCast<Boolean>()) {
        val arr = idsDyn.unsafeCast<Array<dynamic>>()
        carBookingShippingRecreateRowIds.clear()
        for (item in arr) {
            val id = when (item) {
                is Number -> item.toLong()
                else -> item?.toString()?.toLongOrNull()
            }
            if (id != null) carBookingShippingRecreateRowIds.add(id)
        }
    }
    val bid = o.bookingIdNormalized?.toString()?.trim().orEmpty()
    if (bid.isNotEmpty()) {
        carBookingShippingHistoryEditBookingIdNormalized = bid
    }
}

/** JSON array of numeric ids for shipping-history batch delete. */
private fun shippingHistoryDeleteIdsJsonArray(ids: List<Long>): dynamic {
    val arr = js("[]")
    for (id in ids.distinct()) {
        arr.push(id.toDouble())
    }
    return arr
}

private fun shippingHistoryIdFromDynamic(row: dynamic): Long? {
    val raw = row.id
    return when (raw) {
        is Number -> raw.toLong()
        else -> raw?.toString()?.trim()?.toLongOrNull()
    }
}

/** Resolve shipping_history row ids for recreate Delete (session, map, or API lookup). */
private suspend fun resolveShippingHistoryIdsForRecreateDelete(): List<Long> {
    restoreShippingRecreateMetaFromSession()
    val fromSession = carBookingShippingRecreateRowIds.distinct()
    if (fromSession.isNotEmpty()) return fromSession
    val fromMap = carBookingShippingRecreateChassisToHistoryId.values.distinct()
    if (fromMap.isNotEmpty()) return fromMap

    val bookingNo = (document.getElementById("bookingNo") as? HTMLInputElement)?.value?.trim().orEmpty()
    val wantBooking = normalizeBookingIdKey(bookingNo)
    val chassisTokens = carBookingDisplayedCars.mapNotNull { car ->
        car.chassis?.toString()?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
    }.toSet()
    if (chassisTokens.isEmpty()) return emptyList()

    return when (val result = ApiClient.get<Array<dynamic>>("shipping-history")) {
        is ApiResult.Success -> {
            val matched = mutableListOf<Long>()
            for (row in result.data) {
                val rowId = shippingHistoryIdFromDynamic(row) ?: continue
                val rowBooking = normalizeBookingIdKey(row.bookingId?.toString()?.trim().orEmpty())
                if (wantBooking.isNotEmpty() && rowBooking.isNotEmpty() && !wantBooking.equals(rowBooking, ignoreCase = true)) {
                    continue
                }
                val rowChassis = (row.chassis?.toString() ?: "").trim()
                val tokens = parseShippingHistoryChassisTokens(rowChassis).map { it.uppercase() }
                if (tokens.any { it in chassisTokens }) {
                    matched.add(rowId)
                }
            }
            matched.distinct()
        }
        is ApiResult.Error -> emptyList()
    }
}

/** ISO yyyy-MM-dd from hidden `etdDate`, or parsed from visible `etdDateText` if hidden is empty. */
private fun bookingFormEtdIso(): String {
    val hidden = document.getElementById("etdDate") as? HTMLInputElement
    val text = document.getElementById("etdDateText") as? HTMLInputElement
    val fromHidden = hidden?.value?.trim().orEmpty()
    if (fromHidden.isNotEmpty()) return fromHidden
    return strictMmDdYyyySlashToIso(text?.value?.trim().orEmpty()).orEmpty()
}

/** ISO yyyy-MM-dd helper for booking optional date fields (CY CUT / ETA). */
private fun bookingFormOptionalDateIso(hiddenId: String, textId: String): String {
    val hidden = document.getElementById(hiddenId) as? HTMLInputElement
    val text = document.getElementById(textId) as? HTMLInputElement
    val fromHidden = hidden?.value?.trim().orEmpty()
    if (fromHidden.isNotEmpty()) return fromHidden
    return strictMmDdYyyySlashToIso(text?.value?.trim().orEmpty()).orEmpty()
}

private fun bookingFormCyCutIso(): String = bookingFormOptionalDateIso("cyCutDate", "cyCutDateText")

private fun bookingFormEtaIso(): String = bookingFormOptionalDateIso("etaDate", "etaDateText")

private fun setBookingOptionalDateFields(hiddenId: String, textId: String, isoRaw: String) {
    val iso = toIsoFromLabel(isoRaw).ifBlank { isoRaw.trim().take(10) }
    if (iso.isBlank()) return
    (document.getElementById(hiddenId) as? HTMLInputElement)?.value = iso
    (document.getElementById(textId) as? HTMLInputElement)?.value = isoToMmDdYyyy(iso)
}

fun showCarBookingPage() {
    try {
        Logger.debug("showCarBookingPage() function called")

        val shippingHistoryEditPrefillRaw =
            window.sessionStorage.getItem(SHIPPING_HISTORY_EDIT_SESSION_KEY)?.takeIf { it.isNotEmpty() }
        if (shippingHistoryEditPrefillRaw != null) {
            syncLastCalculationModeFromShippingHistoryPayload(shippingHistoryEditPrefillRaw)
            window.sessionStorage.removeItem(SHIPPING_HISTORY_EDIT_SESSION_KEY)
        } else {
            if (!isCarBookingRecreateSession()) {
                carBookingShippingHistoryEditBookingIdNormalized = null
                clearCarBookingShippingRecreateSessionData()
            }
            // Keep Kotlin var in sync with window (SPA: C&F/FOB flow sets both; avoid empty LIST price column)
            val wm = window.asDynamic().lastCalculationMode
            if (wm != null && js("typeof wm === 'string'") as Boolean) {
                val s = (wm as String).trim()
                if (s.isNotEmpty()) lastCalculationMode = s
            }
        }
        
        // Only clear displayed cars if we don't have saved state to restore
        val hasSavedState = if (shippingHistoryEditPrefillRaw != null) {
            false
        } else run {
            val cc = (carBookingFormState.consigneeCountry as? String)?.trim().orEmpty()
            val pol = (carBookingFormState.polPort as? String)?.trim().orEmpty()
            val pod = (carBookingFormState.podPort as? String)?.trim().orEmpty()
            val bn = (carBookingFormState.bookingNo as? String)?.trim().orEmpty()
            val vs = (carBookingFormState.vesselSelect as? String)?.trim().orEmpty()
            val carrier = (carBookingFormState.carrierSelect as? String)?.trim().orEmpty()
            cc.isNotEmpty() || pol.isNotEmpty() || pod.isNotEmpty() || bn.isNotEmpty() ||
                vs.isNotEmpty() || carrier.isNotEmpty() || carBookingDisplayedCars.isNotEmpty()
        }
        
        if (!hasSavedState) {
            // Clear displayed cars when starting NEW booking session (no saved state)
            carBookingDisplayedCars = emptyArray()
            Logger.debug("Cleared carBookingDisplayedCars for new booking session")
        } else {
            Logger.debug("Preserving carBookingDisplayedCars for state restoration (${carBookingDisplayedCars.size} cars)")
        }
        
        val content = document.getElementById("content") ?: return
    
        // C&F/FOB price column removed from list table per user request
        val listPriceHeader = ""

    val savedEtdEarly = (carBookingFormState.etdDate as? String)?.trim().orEmpty()
    val isRecreateMode = shippingHistoryEditPrefillRaw != null || isCarBookingRecreateSession()
    val listSelectHeaderHtml = if (isRecreateMode) {
        "<th class=\"booking-th-select\"></th>"
    } else {
        """<th class="booking-th-select">
            <input type="checkbox" id="selectAllCars" class="booking-select-all-cb" aria-label="Select all"> SELECT
        </th>"""
    }
    val bookingActionButtonsHtml = ""
    val listFooterHtml = if (isRecreateMode) {
        """
                        <div class="booking-list-footer" style="display:flex;justify-content:space-between;align-items:center;margin-top:14px;padding-top:12px;border-top:1px solid #e5e7eb;">
                            <button type="button" id="deleteShippingHistoryFromRecreate" style="padding:10px 20px;border:1px solid #b91c1c;border-radius:8px;background:#fff;color:#b91c1c;font-weight:600;font-size:14px;cursor:pointer;">Delete</button>
                            <button type="button" id="updateShippingHistoryFromRecreate" style="padding:10px 20px;border:none;border-radius:8px;background:#2563eb;color:#fff;font-weight:600;font-size:14px;cursor:pointer;">Update</button>
                        </div>
        """.trimIndent()
    } else {
        ""
    }
    val pageTitle = if (isRecreateMode) "RECREATE SHIPPING SCHEDULE" else "CREATE SHIPPING SCHEDULE"
    val pageSubtitle = if (isRecreateMode)
        "Recreating from shipping history. Review and recalculate."
    else
        "Set consignee, ports, and ETD \u2014 then add chassis to the list."

    content.innerHTML = """
        <div class="booking-page-container">
            <!-- Header -->
            <div class="booking-header">
                <div class="booking-header-inner">
                      <h1 class="booking-title">$pageTitle</h1>
                      <p class="booking-header-sub">$pageSubtitle</p>
                </div>
            </div>
            
            <!-- Main Content Container -->
            <div class="booking-main-content">
                <div class="booking-columns">
                    
                    <!-- Left Section: BOOKING DETAILS -->
                    <div class="booking-details-section booking-panel">
                        <h2 class="booking-section-header">BOOKING DETAILS</h2>
                        
                        <!-- CONSIGNEE (country + name): FAB UI matches Rixo Company picker on Rixo Transport page -->
                        <div class="booking-form-group">
                            <label>CONSIGNEE:</label>
                            <div class="booking-consignee-row">
                                <span style="color: #6b7280; font-size: 16px; flex-shrink: 0;">👤</span>
                                <div class="booking-fab-field rixo-company-fab-wrap" id="bookingCountryFabWrap" style="flex: 1; min-width: 0;">
                                    <select id="consigneeCountry" class="rixo-company-fab-native-select" tabindex="-1" aria-hidden="true">
                                        <option value="">Select Country</option>
                                    </select>
                                    <div class="rixo-company-fab">
                                        <button type="button" id="bookingCountryFabTrigger" class="rixo-fab-trigger" aria-expanded="false" aria-haspopup="listbox" aria-controls="bookingCountryFabActions">
                                            <span class="rixo-fab-trigger-text-wrap">
                                                <span class="rixo-fab-trigger-label" id="bookingCountryFabLabel">Select Country</span>
                                                <span class="rixo-fab-trigger-hint">Tap to choose country</span>
                                            </span>
                                            <span class="rixo-fab-trigger-chevron" aria-hidden="true">▼</span>
                                        </button>
                                        <div id="bookingCountryFabActions" class="rixo-fab-actions" role="listbox" style="display: none;" aria-label="Countries"></div>
                                    </div>
                                </div>
                                <button id="manageBookingMappingsBtn" type="button" style="display: flex; flex-shrink: 0; align-items: center; justify-content: center; background: none; border: none; cursor: pointer; padding: 4px; font-size: 18px; color: #6b7280;" title="Open Consignee Map in new tab">
                                    ⚙️
                                </button>
                            </div>
                            <input type="text" id="consigneeName" placeholder="(CONSIGNEE NAME)">
                        </div>
                        
                        <!-- POL -->
                        <div class="booking-form-group">
                            <label>POL:</label>
                            <div class="booking-fab-field rixo-company-fab-wrap" id="bookingPolFabWrap">
                                <select id="polPort" class="rixo-company-fab-native-select" tabindex="-1" aria-hidden="true">
                                    <option value="">Select Port of Loading</option>
                                </select>
                                <div class="rixo-company-fab">
                                    <button type="button" id="bookingPolFabTrigger" class="rixo-fab-trigger" aria-expanded="false" aria-haspopup="listbox" aria-controls="bookingPolFabActions">
                                        <span class="rixo-fab-trigger-text-wrap">
                                            <span class="rixo-fab-trigger-label" id="bookingPolFabLabel">Select Port of Loading</span>
                                            <span class="rixo-fab-trigger-hint">Tap to choose POL</span>
                                        </span>
                                        <span class="rixo-fab-trigger-chevron" aria-hidden="true">▼</span>
                                    </button>
                                    <div id="bookingPolFabActions" class="rixo-fab-actions" role="listbox" style="display: none;" aria-label="Port of loading"></div>
                                </div>
                            </div>
                        </div>
                        
                        <!-- ETD -->
                        <div class="booking-form-group">
                            <label>ETD:</label>
                            <div style="position:relative; width:100%;">
                                <div style="display:flex; gap:8px; align-items:center; width:100%;">
                                    <input type="text" id="etdDateText" maxlength="10" inputmode="numeric" autocomplete="off"
                                           placeholder="MM/DD/YYYY"
                                           style="flex:1; min-width:0; color:#000000; padding:8px; border:1px solid #ddd; border-radius:4px;">
                                    <button type="button" id="etdDateCalendarBtn" title="Open calendar"
                                            style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                                </div>
                                <input type="date" id="etdDate" placeholder="ESTIMATED SHIPPING DATE" tabindex="-1" aria-hidden="true"
                                       style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;">
                            </div>
                        </div>

                        <!-- CY CUT Date -->
                        <div class="booking-form-group">
                            <label>CY CUT Date:</label>
                            <div style="position:relative; width:100%;">
                                <div style="display:flex; gap:8px; align-items:center; width:100%;">
                                    <input type="text" id="cyCutDateText" maxlength="10" inputmode="numeric" autocomplete="off"
                                           placeholder="MM/DD/YYYY"
                                           style="flex:1; min-width:0; color:#000000; padding:8px; border:1px solid #ddd; border-radius:4px;">
                                    <button type="button" id="cyCutDateCalendarBtn" title="Open calendar"
                                            style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                                </div>
                                <input type="date" id="cyCutDate" tabindex="-1" aria-hidden="true"
                                       style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;">
                            </div>
                        </div>

                        <!-- ETA -->
                        <div class="booking-form-group">
                            <label>ETA:</label>
                            <div style="position:relative; width:100%;">
                                <div style="display:flex; gap:8px; align-items:center; width:100%;">
                                    <input type="text" id="etaDateText" maxlength="10" inputmode="numeric" autocomplete="off"
                                           placeholder="MM/DD/YYYY"
                                           style="flex:1; min-width:0; color:#000000; padding:8px; border:1px solid #ddd; border-radius:4px;">
                                    <button type="button" id="etaDateCalendarBtn" title="Open calendar"
                                            style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                                </div>
                                <input type="date" id="etaDate" tabindex="-1" aria-hidden="true"
                                       style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;">
                            </div>
                        </div>
                        
                        <!-- POD -->
                        <div class="booking-form-group">
                            <label>POD:</label>
                            <input type="text" id="podPort" placeholder="PORT OF DISCHARGE" style="color: #000000;">
                        </div>

                        <!-- Final Destination (optional) -->
                        <div class="booking-form-group">
                            <label>Final Destination:</label>
                            <input type="text" id="finalDestination" placeholder="Optional" style="color: #000000;">
                        </div>

                        <!-- Notify party (optional) — values from Consignee Map -->
                        <div class="booking-form-group">
                            <label>Notify party:</label>
                            ${createEditableCombobox("notifyParty", "Optional", required = false)}
                        </div>
                        
                        <!-- BOOKING NO -->
                        <div class="booking-form-group">
                            <label>BOOKING NO:</label>
                            <input type="text" id="bookingNo" placeholder="">
                        </div>
                        
                        <!-- VESSEL -->
                        <div class="booking-form-group">
                            <label>VESSEL:</label>
                            <input type="text" id="vesselSelect" placeholder="Enter Vessel">
                        </div>

                        <!-- CARRIER (master_menu field: carrier) -->
                        <div class="booking-form-group">
                            <label>CARRIER:</label>
                            <div class="booking-fab-field rixo-company-fab-wrap" id="bookingCarrierFabWrap">
                                <select id="carrierSelect" class="rixo-company-fab-native-select" tabindex="-1" aria-hidden="true">
                                    <option value="">Select Carrier</option>
                                </select>
                                <div class="rixo-company-fab">
                                    <button type="button" id="bookingCarrierFabTrigger" class="rixo-fab-trigger" aria-expanded="false" aria-haspopup="listbox" aria-controls="bookingCarrierFabActions">
                                        <span class="rixo-fab-trigger-text-wrap">
                                            <span class="rixo-fab-trigger-label" id="bookingCarrierFabLabel">Select Carrier</span>
                                            <span class="rixo-fab-trigger-hint">Tap to choose carrier</span>
                                        </span>
                                        <span class="rixo-fab-trigger-chevron" aria-hidden="true">▼</span>
                                    </button>
                                    <div id="bookingCarrierFabActions" class="rixo-fab-actions" role="listbox" style="display: none;" aria-label="Carrier"></div>
                                </div>
                            </div>
                        </div>
                        
                        <!-- Selection Options -->
                        <div class="booking-selection-options">
                            <div class="booking-selection-mode" style="display: flex; align-items: center; gap: 16px; margin-bottom: 10px;">
                                <label style="display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; cursor: pointer;">
                                    <input type="checkbox" id="cnfCheckbox" checked>
                                    C&amp;F
                                </label>
                                <label style="display: inline-flex; align-items: center; gap: 6px; font-size: 13px; color: #374151; cursor: pointer;">
                                    <input type="checkbox" id="fobCheckbox">
                                    FOB
                                </label>
                            </div>
                            <button id="calculateBtn" class="booking-calculate-btn">${if (isRecreateMode) "Recalculate" else "Calculate"}</button>
                        </div>
                        
                        $bookingActionButtonsHtml
                    </div>
                    
                    <!-- Right Section: LIST -->
                    <div class="booking-list-section booking-panel">
                        <div class="booking-list-header-row">
                            <h2 class="booking-section-header">LIST</h2>
                            <span id="bookingCarsSelectedCount" class="booking-cars-selected-count is-empty" aria-live="polite">0 CARS SELECTED</span>
                        </div>
                        
                        <!-- SEARCH CHASSIS: plain input, suggestions from purchase table search (no dropdown button) -->
                        <div class="booking-form-group booking-chassis-search-wrap">
                            <label>SEARCH CHASSIS:</label>
                            <input type="text" id="chassisSearchInput" class="booking-chassis-search-input" placeholder="Type to search chassis from purchases…"
                                   autocomplete="off">
                            <div id="chassisSuggestions" class="booking-chassis-suggestions"></div>
                        </div>
                        
                        <!-- Car Selection Table -->
                        <div class="booking-table-card">
                            <table class="booking-chassis-table">
                                <thead>
                                    <tr>
                                        $listSelectHeaderHtml
                                        <th>NO.</th>
                                        <th>CHASSIS</th>
                                        <th>NAME</th>
                                        <th>YEAR</th>
                                        <th>STOCK</th>
                                        $listPriceHeader
                                    </tr>
                                </thead>
                                <tbody id="carSelectionTableBody">
                                    <!-- Cars will be loaded here -->
                                </tbody>
                            </table>
                        </div>
                        $listFooterHtml
                    </div>
                </div>
                
            </div>
        </div>
    """
    
    // Default ETD to today only when we are not restoring a saved session
    val today = js("new Date().toISOString().split('T')[0]") as String
    val etdToShow = if (savedEtdEarly.isNotEmpty()) savedEtdEarly else today
    (document.getElementById("etdDate") as? HTMLInputElement)?.value = etdToShow
    bindStrictDateTextMask("etdDate")
    bindStrictDateTextMask("cyCutDate")
    bindStrictDateTextMask("etaDate")
    
    // Setup event listeners
    setupCarBookingPageListeners()
    if (isRecreateMode) {
        setupBookingRecreatePageListeners()
        lockBookingRecreateCountryAndPol()
    }

    js(
        """
        setTimeout(function() {
          if (typeof window.registerBookingFabSelect === 'function') {
            window.registerBookingFabSelect({
              selectId: 'consigneeCountry',
              wrapId: 'bookingCountryFabWrap',
              triggerId: 'bookingCountryFabTrigger',
              actionsId: 'bookingCountryFabActions',
              labelId: 'bookingCountryFabLabel',
              defaultLabel: 'Select Country',
              emptyMessage: 'No countries available'
            });
            window.registerBookingFabSelect({
              selectId: 'polPort',
              wrapId: 'bookingPolFabWrap',
              triggerId: 'bookingPolFabTrigger',
              actionsId: 'bookingPolFabActions',
              labelId: 'bookingPolFabLabel',
              defaultLabel: 'Select Port of Loading',
              emptyMessage: 'No POL values available'
            });
            window.registerBookingFabSelect({
              selectId: 'carrierSelect',
              wrapId: 'bookingCarrierFabWrap',
              triggerId: 'bookingCarrierFabTrigger',
              actionsId: 'bookingCarrierFabActions',
              labelId: 'bookingCarrierFabLabel',
              defaultLabel: 'Select Carrier',
              emptyMessage: 'No carriers available'
            });
          }
        }, 0);
        """
    )
    
    // POL: clear only for a fresh session; returning from C&F keeps state and restore will refill POL
    if (!hasSavedState) {
        clearPolDropdownNoCountry()
    }
    
    // Load countries first, then carriers + notify parties, then restore — otherwise a late API response rebuilds the country dropdown and clears the restored value.
    loadCountries {
        loadCarriers {
            populateEditableComboboxFromBookingNotifyParties("notifyParty")
            window.setTimeout({
                if (shippingHistoryEditPrefillRaw != null) {
                    MainScope().launch {
                        applyShippingHistoryEditPrefillFromJson(shippingHistoryEditPrefillRaw)
                    }
                } else {
                    restoreShippingRecreateMetaFromSession()
                    restoreCarBookingState()
                }
            }, 80)
        }
    }
    
    // Auto-load purchases into LIST table if both country and POL are selected (after state restoration)
    // Chassis search is a plain input with API suggestions - no dropdown. Skip loadFilteredPurchasesIntoTable when returning from C&F/FOB.
    val skipAutoFilteredLoadForShippingEdit = shippingHistoryEditPrefillRaw != null
    window.setTimeout({
        if (skipAutoFilteredLoadForShippingEdit) {
            Logger.debug("Skipping loadFilteredPurchasesIntoTable (shipping history edit prefill)")
            return@setTimeout
        }
        val polSelect = document.getElementById("polPort") as? HTMLSelectElement
        val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
        var selectedPol = bookingDynString(nativeSelectValueOrText(polSelect))
        var selectedCountry = bookingDynString(nativeSelectValueOrText(countrySelect))
        if (selectedCountry.isEmpty()) {
            selectedCountry = currentSelectedCountry.trim()
        }
        if (selectedPol.isNotEmpty() && selectedCountry.isNotEmpty()) {
            if (carBookingDisplayedCars.isEmpty()) {
                Logger.debug("Loading purchases into table after state restoration (Country: $selectedCountry, POL: $selectedPol)")
                loadFilteredPurchasesIntoTable()
            } else {
                Logger.debug("Skipping loadFilteredPurchasesIntoTable - restoring ${carBookingDisplayedCars.size} cars from state (backtrack/FINISH)")
            }
        } else {
            Logger.warn("Cannot load - Country or POL is empty (Country: '$selectedCountry', POL: '$selectedPol')")
        }
    }, 2000)
    
    // Don't load cars automatically - wait for user search
    Logger.debug("Car Booking page loaded - waiting for user to search by chassis number")
    
    } catch (e: dynamic) {
        Logger.error("Error in showCarBookingPage(): ${e.toString()}")
    }
}

/** @return null if the API call failed; true if any shipping-history row uses this booking id. */
private suspend fun fetchShippingHistoryBookingIdExists(bookingNo: String): Boolean? {
    val want = normalizeBookingIdKey(bookingNo.trim())
    if (want.isEmpty()) return false
    return when (val result = ApiClient.get<Array<dynamic>>("shipping-history")) {
        is ApiResult.Success -> {
            var found = false
            for (i in 0 until result.data.size) {
                val row = result.data[i]
                val rawBid = row.bookingId
                val cell = when {
                    rawBid == null || (js("rawBid === undefined") as Boolean) -> ""
                    js("typeof rawBid === 'number'") as Boolean && !(js("isNaN(rawBid)") as Boolean) ->
                        (rawBid as Number).toLong().toString()
                    else -> normalizeBookingIdKey(rawBid.toString().trim())
                }
                if (cell.isNotEmpty() && want.equals(cell, ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        }
        is ApiResult.Error -> null
    }
}

private fun proceedCarBookingCalculateToCnf(
    isFobMode: Boolean,
    selectedMode: String,
    selectedIds: List<Long>,
    selectedCars: List<dynamic>,
    etd: String,
    bookingNo: String,
    vessel: String,
) {
    Logger.debug("Booking Calculate: opening calculation page in $selectedMode mode")

    cnfPageSelectedPurchaseIds = selectedIds
    Logger.debug("Stored purchase IDs in cnfPageSelectedPurchaseIds: $selectedIds")

    carBookingFormState.selectedPurchaseIds = selectedIds.toTypedArray()
    Logger.debug("Saved selected purchase IDs in form state: $selectedIds")

    var selectedCountry = (document.getElementById("consigneeCountry") as? HTMLSelectElement)?.value?.trim().orEmpty()
    if (selectedCountry.isEmpty()) {
        selectedCountry = currentSelectedCountry.trim().ifEmpty { "PAKISTAN" }
    }
    Logger.debug("Selected country: $selectedCountry")

    val podPortEl = document.getElementById("podPort")
    var podPort = ""
    if (podPortEl != null) {
        if (podPortEl.tagName == "SELECT") {
            podPort = (podPortEl as HTMLSelectElement).value ?: ""
        } else {
            podPort = (podPortEl as HTMLInputElement).value ?: ""
        }
    }
    if (podPort.isEmpty()) {
        val savedPod = carBookingFormState.podPort as? String ?: ""
        if (savedPod.isNotEmpty()) {
            podPort = savedPod
            console.log("⚠️ POD was empty in form, using saved POD from state: $podPort")
        }
    }
    if (podPort.isEmpty() && selectedCars.isNotEmpty()) {
        for (car in selectedCars) {
            val carDestination = when {
                js("typeof car.destination !== 'undefined' && car.destination !== null") as Boolean -> {
                    js("car.destination") as? String ?: ""
                }
                js("typeof car['destination'] !== 'undefined' && car['destination'] !== null") as Boolean -> {
                    js("car['destination']") as? String ?: ""
                }
                else -> {
                    car.destination as? String ?: ""
                }
            }
            if (carDestination.isNotEmpty() && carDestination.trim().isNotEmpty()) {
                podPort = carDestination.trim()
                Logger.debug("POD was empty, using POD from database (destination field): $podPort")
                break
            }
        }
    }

    if (podPort.isEmpty() && carBookingDisplayedCars.isNotEmpty()) {
        for (car in carBookingDisplayedCars) {
            val carDestination = when {
                js("typeof car.destination !== 'undefined' && car.destination !== null") as Boolean -> {
                    js("car.destination") as? String ?: ""
                }
                js("typeof car['destination'] !== 'undefined' && car['destination'] !== null") as Boolean -> {
                    js("car['destination']") as? String ?: ""
                }
                else -> {
                    car.destination as? String ?: ""
                }
            }
            if (carDestination.isNotEmpty() && carDestination.trim().isNotEmpty()) {
                podPort = carDestination.trim()
                Logger.debug("POD was empty, using POD from displayed cars (destination field): $podPort")
                break
            }
        }
    }

    if (podPort.isNotEmpty()) {
        carBookingFormState.podPort = podPort
        Logger.debug("Saved POD to state for preservation: $podPort")
    }

    val consigneeName = (document.getElementById("consigneeName") as? HTMLInputElement)?.value?.trim() ?: ""
    val consignee = consigneeName

    console.log("📋 Booking form values:")
    console.log("   ETD: $etd")
    console.log("   Booking No: $bookingNo")
    console.log("   Vessel: $vessel")
    console.log("   POD: $podPort (from form: ${podPortEl?.let { if (it.tagName == "SELECT") (it as HTMLSelectElement).value else (it as HTMLInputElement).value } ?: ""}, from state: ${carBookingFormState.podPort})")
    console.log("   Consignee: $consignee")

    saveCarBookingState()

    updateSelectedPurchasesWithBookingData(
        purchaseIds = selectedIds,
        etd = etd,
        bookingNo = bookingNo,
        vessel = vessel,
        destination = podPort,
        consignee = consignee,
        onComplete = {
            Logger.debug("All purchases updated with booking data and booking_id")

            storeBookingDetailsForPdf()

            saveCarBookingState()

            showCnfCalculationPage(
                selectedChassis = null,
                selectedCars = selectedCars,
                selectedCountry = selectedCountry,
                isFobMode = isFobMode,
                isRecreateCalculation = isCarBookingRecreateSession(),
            )
            navigateToApp(
                if (isCarBookingRecreateSession()) {
                    "/recalculate-booking/recalculation"
                } else {
                    "/booking/calculation"
                }
            )
        }
    )
}

fun setupCarBookingPageListeners() {
    // Restore booking selection state (C&F or FOB) - with delay to ensure DOM is ready
    window.setTimeout({
        // restoreBookingSelectionState() - This function should be in MinimalPurchaseApp.kt
        js("if (window.restoreBookingSelectionState) window.restoreBookingSelectionState()")
    }, 200)
    
    // Select All checkbox for car selection table
    document.getElementById("selectAllCars")?.addEventListener("change", { event: Event ->
        val target = event.target as? HTMLInputElement
        val isChecked = target?.checked ?: false
        
        val tableBody = document.getElementById("carSelectionTableBody")
        if (tableBody != null) {
            val checkboxes = tableBody.querySelectorAll("input[type='checkbox']")
            for (i in 0 until checkboxes.length) {
                val checkbox = checkboxes.item(i) as? HTMLInputElement
                checkbox?.checked = isChecked
            }
            Logger.debug("${if (isChecked) "Selected" else "Deselected"} all ${checkboxes.length} cars")
        }
        target?.indeterminate = false
        updateBookingCarsSelectedCount()
    })

    document.getElementById("carSelectionTableBody")?.addEventListener("change", { event: Event ->
        val target = event.target as? HTMLInputElement ?: return@addEventListener
        if (target.type != "checkbox" || !target.classList.contains("car-checkbox")) return@addEventListener
        updateBookingCarsSelectedCount()
        updateBookingSelectAllCheckbox()
    })
    
    // Purchase List button
    document.getElementById("purchaseListBtn")?.addEventListener("click", { _: Event ->
        Logger.debug("Purchase List button clicked - navigating to existing purchase list")
        navigateToPurchaseList(forceClearFilters = true)
    })
    
    // Country dropdown change - trigger filtered chassis loading and booking mappings
    document.getElementById("consigneeCountry")?.addEventListener("change", { event: Event ->
        val selectedCountry = (event.target as HTMLSelectElement).value
        Logger.debug("Country selected: $selectedCountry")
        currentSelectedCountry = selectedCountry // Update the global variable
        
        // Ensure POD is auto-filled for the newly selected country:
        // booking-mapping.js preserves current POD value if present, so clear it first.
        val podPortEl = document.getElementById("podPort")
        if (podPortEl != null) {
            if (podPortEl.tagName == "SELECT") {
                (podPortEl as HTMLSelectElement).value = ""
            } else {
                (podPortEl as HTMLInputElement).value = ""
            }
        }
        carBookingFormState.podPort = ""
        carBookingFormState.consigneeName = ""

        // Apply booking mappings (POD and CONSIGNEE auto-fill) - Direct JS call
        Logger.debug("Attempting to call booking mappings for country: $selectedCountry")
        
        // Direct call using window.asDynamic() - same pattern as other window functions
        try {
            val applyBookingMappings = window.asDynamic().applyBookingMappingsByCountry
            if (applyBookingMappings != null && jsTypeOf(applyBookingMappings) == "function") {
                Logger.debug("Calling applyBookingMappingsByCountry with: $selectedCountry")
                applyBookingMappings.unsafeCast<(String) -> Unit>().invoke(selectedCountry)
            } else {
                Logger.warn("window.applyBookingMappingsByCountry is not available")
            }
        } catch (e: dynamic) {
            Logger.error("Error calling booking mappings: ${e.toString()}")
        }

        // booking-mapping.js may replace the POD input with a <select> element.
        // Re-attach the POD change listener after that happens so the state is updated correctly.
        window.setTimeout({
            val podPortElAfter = document.getElementById("podPort") as? HTMLElement
            if (podPortElAfter != null) {
                attachPodChangeListener(podPortElAfter)
                val podValue = if (podPortElAfter.tagName == "SELECT") {
                    (podPortElAfter as HTMLSelectElement).value ?: ""
                } else {
                    (podPortElAfter as HTMLInputElement).value ?: ""
                }
                if (podValue.isNotEmpty()) carBookingFormState.podPort = podValue
            }
        }, 250)
        
        Logger.debug("Country changed")
        
        // Reload POL dropdown based on selected country
        if (selectedCountry.isNotEmpty()) {
            Logger.debug("Reloading POL dropdown for country: $selectedCountry")
            loadStockLocations(selectedCountry)
        } else {
            Logger.debug("Country is empty, loading all stock locations")
            loadStockLocations()
            clearBookingListTable()
        }
    })
    
    // Gear: open Consignee Map master page in a new tab; remind user to refresh for latest mappings.
    document.getElementById("manageBookingMappingsBtn")?.addEventListener("click", { _: Event ->
        val consigneeMapUrl = buildAppAbsoluteUrl("/master/consignee-map")
        Logger.debug("Opening Consignee Map in new tab: $consigneeMapUrl")
        window.open(consigneeMapUrl, "_blank")
        showConsigneeMapRefreshNoticeModal()
    })
    
    // POL dropdown change - auto-load purchases into LIST table
    document.getElementById("polPort")?.addEventListener("change", { event: Event ->
        val restoring = js("window.__bookingRestoreInProgress === true") as Boolean
        if (restoring) {
            Logger.debug("Skipping loadFilteredPurchasesIntoTable (booking restore in progress)")
            return@addEventListener
        }
        val selectedPol = (event.target as HTMLSelectElement).value
        Logger.debug("POL selected: $selectedPol")
        val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
        val selectedCountry = countrySelect?.value ?: ""
        if (selectedCountry.isNotEmpty() && selectedPol.isNotEmpty()) {
            Logger.debug("Loading purchases into table after POL change")
            loadFilteredPurchasesIntoTable()
        } else {
            Logger.warn("Cannot reload - Country or POL is empty")
            if (selectedPol.isEmpty()) clearBookingListTable()
        }
    })
    
    // POD field change - save to state immediately to preserve it
    val podPortElForListener = document.getElementById("podPort") as? HTMLElement
    if (podPortElForListener != null) {
        attachPodChangeListener(podPortElForListener)
    }

    updateBookingCarsSelectedCount()
}

// Helper function to attach POD change listener (can be called multiple times if element is replaced)
fun attachPodChangeListener(podPortEl: HTMLElement) {
    // Remove existing listeners by cloning the element (if it's an input)
    // For select, we can just add new listeners
    val savePodToState = {
        val podEl = document.getElementById("podPort")
        if (podEl != null) {
            val podValue = if (podEl.tagName == "SELECT") {
                (podEl as HTMLSelectElement).value ?: ""
            } else {
                (podEl as HTMLInputElement).value ?: ""
            }
            if (podValue.isNotEmpty()) {
                carBookingFormState.podPort = podValue
                Logger.debug("Saved POD to state on change: $podValue (element type: ${podEl.tagName})")
            } else {
                Logger.debug("POD value is empty, not saving to state")
            }
        } else {
            Logger.warn("POD element not found when trying to save to state")
        }
    }
    // Listen to both input and change events
    podPortEl.addEventListener("input", { event: Event -> 
        Logger.debug("POD input event triggered")
        savePodToState() 
    })
    podPortEl.addEventListener("change", { event: Event -> 
        Logger.debug("POD change event triggered")
        savePodToState() 
    })
    // Also listen to blur event to catch when user leaves the field
    podPortEl.addEventListener("blur", { event: Event -> 
        Logger.debug("POD blur event triggered")
        savePodToState() 
    })
    Logger.debug("Attached POD change listener to element type: ${podPortEl.tagName}")
    
    // Also save current value immediately if it's not empty
    val currentPodValue = if (podPortEl.tagName == "SELECT") {
        (podPortEl as HTMLSelectElement).value ?: ""
    } else {
        (podPortEl as HTMLInputElement).value ?: ""
    }
    if (currentPodValue.isNotEmpty()) {
        carBookingFormState.podPort = currentPodValue
        Logger.debug("Saved existing POD value to state: $currentPodValue")
    }
    
    // Chassis search: plain input - show suggestions from purchase table search, add car on Enter or suggestion click
    window.asDynamic().addCarToBookingTable = ::addCarToBookingTable
    var chassisSearchDebounceTimer: dynamic = 0
    document.getElementById("chassisSearchInput")?.addEventListener("input", { event: Event ->
        val input = event.target as? HTMLInputElement ?: return@addEventListener
        val q = input.value.trim()
        window.clearTimeout(chassisSearchDebounceTimer)
        chassisSearchDebounceTimer = window.setTimeout({
            fetchChassisSuggestionsForBooking(q)
        }, 250)
    })
    document.getElementById("chassisSearchInput")?.addEventListener("keydown", { event: Event ->
        val keyEvent = event.asDynamic()
        if (keyEvent.key == "Enter") {
            val input = document.getElementById("chassisSearchInput") as? HTMLInputElement
            val chassis = input?.value?.trim() ?: ""
            if (chassis.isNotEmpty()) {
                keyEvent.preventDefault()
                hideChassisSuggestions()
                addCarToBookingTable(chassis)
            }
        }
    })
    document.getElementById("chassisSearchInput")?.addEventListener("blur", { _: Event ->
        window.setTimeout({ hideChassisSuggestions() }, 200)
    })
    window.asDynamic().handleChassisSearchChange = { }
    
    // Calculate button → open C&F/FOB cost page based on selected checkbox mode
    document.getElementById("calculateBtn")?.addEventListener("click", { _: Event ->
        val cnfCheckbox = document.getElementById("cnfCheckbox") as? HTMLInputElement
        val fobCheckbox = document.getElementById("fobCheckbox") as? HTMLInputElement
        val isFobMode = fobCheckbox?.checked == true && cnfCheckbox?.checked != true
        val selectedMode = if (isFobMode) "FOB" else "C&F"

        lastCalculationMode = selectedMode
        window.asDynamic().lastCalculationMode = selectedMode
        carBookingFormState.cnfChecked = !isFobMode
        carBookingFormState.fobChecked = isFobMode
        saveBookingSelectionState(selectedMode)
        
        val selectedIds = getSelectedPurchaseIds()
        if (selectedIds.isEmpty()) {
            showMessage(
                if (isCarBookingRecreateSession()) "Add at least one car to the list" else "Please select at least one car",
                "error",
            )
            return@addEventListener
        }
        
        // Get all booking form values for validation (hidden ISO + fallback to masked text)
        val etd = bookingFormEtdIso()
        val bookingNo = (document.getElementById("bookingNo") as? HTMLInputElement)?.value ?: ""
        val vessel = (document.getElementById("vesselSelect") as? HTMLInputElement)?.value ?: ""
        
        // Validate required fields (as per documentation)
        if (etd.isEmpty()) {
            showMessage("Please fill in ETD (Estimated Time of Departure) before calculating", "error")
            return@addEventListener
        }
        if (bookingNo.isEmpty()) {
            showMessage("Please fill in BOOKING NO before calculating", "error")
            return@addEventListener
        }
        if (vessel.isEmpty()) {
            showMessage("Please enter VESSEL before calculating", "error")
            return@addEventListener
        }

        val selectedCars = getSelectedCarsFromTable()
        Logger.debug("Selected cars for calculation: ${selectedCars.size}")

        MainScope().launch {
            if (!isCarBookingRecreateSession()) {
                val exists = fetchShippingHistoryBookingIdExists(bookingNo)
                if (exists == null) {
                    showMessage("Could not verify booking ID against shipping history. Try again.", "error")
                    return@launch
                }
                if (exists) {
                    val edited = carBookingShippingHistoryEditBookingIdNormalized
                    val sameAsEdit = edited != null &&
                        normalizeBookingIdKey(bookingNo.trim()).equals(edited, ignoreCase = true)
                    if (!sameAsEdit) {
                        showMessage("Booking ID already exists in shipping history.", "error")
                        return@launch
                    }
                }
            }
            proceedCarBookingCalculateToCnf(
                isFobMode = isFobMode,
                selectedMode = selectedMode,
                selectedIds = selectedIds,
                selectedCars = selectedCars,
                etd = etd,
                bookingNo = bookingNo,
                vessel = vessel,
            )
        }
    })

    // C&F/FOB mode checkboxes (single-select checkbox behavior)
    val cnfCheckbox = document.getElementById("cnfCheckbox") as? HTMLInputElement
    val fobCheckbox = document.getElementById("fobCheckbox") as? HTMLInputElement

    cnfCheckbox?.addEventListener("change", { _: Event ->
        if (cnfCheckbox.checked) {
            fobCheckbox?.checked = false
        } else if (fobCheckbox?.checked != true) {
            cnfCheckbox.checked = true
        }
        carBookingFormState.cnfChecked = cnfCheckbox.checked
        carBookingFormState.fobChecked = fobCheckbox?.checked == true
        saveBookingSelectionState(if (fobCheckbox?.checked == true) "FOB" else "C&F")
    })

    fobCheckbox?.addEventListener("change", { _: Event ->
        if (fobCheckbox.checked) {
            cnfCheckbox?.checked = false
        } else if (cnfCheckbox?.checked != true) {
            fobCheckbox.checked = true
        }
        carBookingFormState.cnfChecked = cnfCheckbox?.checked == true
        carBookingFormState.fobChecked = fobCheckbox.checked
        saveBookingSelectionState(if (fobCheckbox.checked) "FOB" else "C&F")
    })
}

fun loadCountries(onCountriesLoaded: (() -> Unit)? = null) {
    Logger.debug("Loading countries from purchases/countries (pending booking only)...")
    
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<Array<String>>("purchases/countries")
        result.fold(
            onSuccess = { countries ->
                Logger.debug("Countries data received: ${countries.size} countries")
                val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
                if (countrySelect != null) {
                    // Clear existing options except the first one
                    countrySelect.innerHTML = "<option value=\"\">Select Country</option>"
                    
                    // Add countries from API
                    countries.forEach { country ->
                        val option = document.createElement("option")
                        option.setAttribute("value", country)
                        option.textContent = country
                        countrySelect.appendChild(option)
                    }
                    Logger.debug("Countries loaded from API: ${countries.size}")
                    refreshBookingFabConsigneeCountryUi()
                } else {
                    Logger.error("consigneeCountry select missing after countries API response")
                }
                onCountriesLoaded?.invoke()
            },
            onError = { message, _ ->
                Logger.error("Error loading countries: $message")
                loadCountriesFallback(onCountriesLoaded)
            }
        )
    }
}

fun loadCountriesFallback(onCountriesLoaded: (() -> Unit)? = null) {
    Logger.debug("Loading fallback country data...")
    
    val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
    if (countrySelect == null) {
        Logger.error("Country select element not found in fallback!")
        onCountriesLoaded?.invoke()
        return
    }
    
    Logger.debug("Clearing country select and adding default option...")
    countrySelect.innerHTML = "<option value=\"\">Select Country</option>"
    
    // Country data from your CSV file - unique countries
    val fallbackCountries = listOf(
        "PAKISTAN",
        "SOUTH AFRICA", 
        "KENYA",
        "TANZANIA",
        "UGANDA",
        "GHANA",
        "NIGERIA",
        "JAPAN",
        "DURBAN",
        "MAPUTO",
        "DUBAI"
    )
    
    Logger.debug("Adding ${fallbackCountries.size} countries to dropdown...")
    
    fallbackCountries.forEach { country ->
        val option = document.createElement("option")
        option.setAttribute("value", country)
        option.textContent = country
        countrySelect.appendChild(option)
    }
    
    Logger.debug("Fallback countries loaded successfully: ${fallbackCountries.size} countries, final options count: ${countrySelect.options.length}")
    refreshBookingFabConsigneeCountryUi()
    onCountriesLoaded?.invoke()
}

/** Carrier dropdown options from master_menu field `carrier`. */
fun loadCarriers(onComplete: (() -> Unit)? = null) {
    val carrierSelect = document.getElementById("carrierSelect") as? HTMLSelectElement
    if (carrierSelect == null) {
        onComplete?.invoke()
        return
    }
    val preferred = bookingDynString(carBookingFormState.carrierSelect)
    window.fetch(apiUrl("master-menu/carrier"))
        .then { response: dynamic ->
            if (response.ok) response.json() else js("[]")
        }
        .then { values: dynamic ->
            carrierSelect.innerHTML = "<option value=\"\">Select Carrier</option>"
            val list = mutableListOf<String>()
            if (js("Array.isArray(values)").unsafeCast<Boolean>()) {
                val n = js("values.length").unsafeCast<Int>()
                for (i in 0 until n) {
                    val v = js("values[i]")?.toString()?.trim().orEmpty()
                    if (v.isNotEmpty()) list.add(v)
                }
            }
            for (v in list.distinct()) {
                val option = document.createElement("option") as HTMLOptionElement
                option.value = v
                option.textContent = v
                carrierSelect.appendChild(option)
            }
            if (preferred.isNotEmpty()) {
                bookingEnsureCarrierOption(preferred)
            }
            refreshBookingFabCarrierUi()
            onComplete?.invoke()
        }
        .catch { _: dynamic ->
            refreshBookingFabCarrierUi()
            onComplete?.invoke()
        }
}

/** POL dropdown: empty until a country is chosen (no global fetch on page load). */
fun clearPolDropdownNoCountry() {
    val polSelect = document.getElementById("polPort") as? HTMLSelectElement ?: return
    polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"
    polSelect.value = ""
    carBookingFormState.polPort = ""
    refreshBookingFabPolUi()
}

/**
 * Loads POL options from purchases for [country] via [purchases/pols-by-country].
 * First value in the list auto-fills POL; rest appear in the dropdown.
 * If [country] is null/blank, clears POL (does not load all POLs).
 */
fun loadStockLocations(country: String? = null, onComplete: (() -> Unit)? = null) {
    val countryParam = (country ?: "").trim()
    console.log("Loading POL from purchases for country='$countryParam'...")

    fun done() {
        onComplete?.invoke()
    }

    if (countryParam.isEmpty()) {
        clearPolDropdownNoCountry()
        done()
        return
    }

    val encodedCountry = js("encodeURIComponent")(countryParam).unsafeCast<String>()
    val polApiUrl = apiUrl("purchases/pols-by-country?country=$encodedCountry")

    window.fetch(polApiUrl)
        .then { response: dynamic ->
            console.log("POLs by country API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                console.log("POLs by country API failed, fetching purchases to filter client-side")
                window.fetch(apiUrl("purchases"))
                    .then { purchasesResponse: dynamic ->
                        if (purchasesResponse.ok) purchasesResponse.json() else js("Promise.resolve([])")
                    }
                    .then { purchases: dynamic ->
                        val purchasesArray = purchases as Array<dynamic>
                        val ordered = mutableListOf<String>()
                        val seen = mutableSetOf<String>()
                        purchasesArray.forEach { purchase ->
                            val purchaseCountry = js("purchase.country")?.toString() ?: ""
                            if (!purchaseCountry.equals(countryParam, ignoreCase = true)) return@forEach
                            val pol = js("purchase.pol")?.toString()?.trim() ?: ""
                            if (pol.isEmpty()) return@forEach
                            val key = pol.lowercase()
                            if (seen.add(key)) ordered.add(pol)
                        }
                        ordered.toTypedArray()
                    }
            }
        }
        .then { pols: dynamic ->
            console.log("POL data received:", pols)
            val polSelect = document.getElementById("polPort") as HTMLSelectElement?
            if (polSelect != null) {
                val currentPolValue = bookingDynString(nativeSelectValueOrText(polSelect))
                val statePolValue = bookingDynString(carBookingFormState.polPort)
                val preservedPolValue = if (currentPolValue.isNotEmpty()) currentPolValue else statePolValue

                polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"

                val polsArray = pols as Array<dynamic>
                val normalizedPols = polsArray.map { (it?.toString() ?: "").trim() }.filter { it.isNotEmpty() }
                normalizedPols.forEach { pol ->
                    val option = document.createElement("option") as HTMLOptionElement
                    option.setAttribute("value", pol)
                    option.textContent = pol
                    polSelect.appendChild(option)
                }

                val preservedExists = preservedPolValue.isNotEmpty() && normalizedPols.any { it == preservedPolValue }
                val desiredPol = if (preservedExists) preservedPolValue else normalizedPols.firstOrNull() ?: ""

                if (desiredPol.isNotEmpty()) {
                    polSelect.value = desiredPol
                    carBookingFormState.polPort = desiredPol
                    val changeEvent = js("new Event('change', { bubbles: true })")
                    polSelect.dispatchEvent(changeEvent.unsafeCast<Event>())
                } else {
                    carBookingFormState.polPort = ""
                }

                console.log("✅ POL values loaded: ${normalizedPols.size} POLs for country: $countryParam (selected: '${polSelect.value}')")
                refreshBookingFabPolUi()
            }
            done()
        }
        .catch { error: dynamic ->
            console.error("Error loading POL values:", error)
            loadStockLocationsFallback()
            done()
        }
}

fun loadStockLocationsFallback() {
    console.log("Loading fallback stock location data...")
    
    val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
    val countryNow = (countrySelect?.value ?: "").trim()
    if (countryNow.isEmpty()) {
        clearPolDropdownNoCountry()
        return
    }

    val polSelect = document.getElementById("polPort") as HTMLSelectElement?
    if (polSelect == null) {
        console.error("POL select element not found in fallback!")
        return
    }
    
    console.log("Clearing POL select and adding default option...")
    polSelect.innerHTML = "<option value=\"\">Select Port of Loading</option>"
    
    // Stock location data from your CSV file - unique locations
    val fallbackStockLocations = listOf(
        "GLOBAL KAWASAKI",
        "GLOBAL HAKATA", 
        "GLOBAL NAGOYA",
        "KLC",
        "BARAKI",
        "-"
    )
    
    console.log("Adding", fallbackStockLocations.size, "stock locations to dropdown...")
    
    fallbackStockLocations.forEach { location ->
        val option = document.createElement("option")
        option.setAttribute("value", location)
        option.textContent = location
        polSelect.appendChild(option)
        console.log("Added stock location:", location)
    }
    
    console.log("Fallback stock locations loaded successfully:", fallbackStockLocations.size, "locations")
    console.log("Final POL select options count:", polSelect.options.length)
    refreshBookingFabPolUi()
}

fun loadFilteredChassis() {
    val polSelect = document.getElementById("polPort") as HTMLSelectElement?
    val countrySelect = document.getElementById("consigneeCountry") as HTMLSelectElement?
    val chassisSelect = document.getElementById("chassisSearch") as HTMLSelectElement?
    val chassisInput = document.getElementById("chassisSearchInput") as HTMLInputElement?
    
    if (chassisSelect == null) {
        Logger.error("Chassis select element not found!")
        return
    }
    
    val selectedPol = polSelect?.value ?: ""
    val selectedCountry = countrySelect?.value ?: ""
    
    // Both country and POL are required for filtering
    if (selectedPol.isEmpty() || selectedCountry.isEmpty()) {
        Logger.debug("Country or POL not selected, clearing chassis data (Country: '$selectedCountry', POL: '$selectedPol')")
        // Clear combobox dropdown
        chassisSelect.innerHTML = "<option value=\"\">▼</option>"
        if (chassisInput != null) {
            chassisInput.value = ""
        }
        return
    }
    
    Logger.debug("Loading unshipped chassis for Country: $selectedCountry, POL: $selectedPol")
    
    val encodedCountry = js("encodeURIComponent")(selectedCountry) as String
    val encodedPol = js("encodeURIComponent")(selectedPol) as String
    val url = apiUrl("purchases/filtered-chassis?country=$encodedCountry&polPort=$encodedPol")
    console.log("🔍 Fetching filtered chassis from:", url)
    window.fetch(url)
        .then { response: dynamic ->
            console.log("Filtered chassis API response status:", response.status)
            if (response.ok) {
                response.json()
            } else {
                console.log("Filtered chassis API failed, using fallback")
                js("Promise.resolve([])")
            }
        }
        .then { chassis: dynamic ->
            console.log("Filtered chassis data received:", chassis)
            
            // Populate combobox dropdown with chassis options
            val chassisArray = chassis as Array<dynamic>
            chassisSelect.innerHTML = "<option value=\"\">▼</option>"
            chassisArray.forEach { chassisValue ->
                val chassisStr = chassisValue.toString()
                val option = js("new Option(chassisStr, chassisStr)")
                chassisSelect.appendChild(option.unsafeCast<HTMLOptionElement>())
            }
            console.log("✅ Chassis dropdown populated with ${chassisArray.size} options (filtered by Country: $selectedCountry, POL: $selectedPol)")
        }
        .catch { error: dynamic ->
            console.error("Error loading filtered chassis:", error)
            loadFilteredChassisFallback()
        }
}

fun loadFilteredChassisFallback() {
    console.log("Using fallback chassis data")
    // Fallback implementation can be added if needed
}

fun loadFilteredPurchasesIntoTable() {
    val polSelect = document.getElementById("polPort") as? HTMLSelectElement
    val countrySelect = document.getElementById("consigneeCountry") as? HTMLSelectElement
    val selectedPol = polSelect?.value ?: ""
    val selectedCountry = countrySelect?.value ?: ""
    if (selectedPol.isEmpty() || selectedCountry.isEmpty()) {
        Logger.debug("Country or POL empty, clearing list table")
        clearBookingListTable()
        return
    }
    val encodedCountry = js("encodeURIComponent")(selectedCountry) as String
    val encodedPol = js("encodeURIComponent")(selectedPol) as String
    val url = apiUrl("purchases/filtered-purchases?country=$encodedCountry&polPort=$encodedPol")
    Logger.debug("Fetching filtered purchases from: $url")
    window.fetch(url)
        .then { response: dynamic ->
            if (response.ok) {
                response.json()
            } else {
                console.error("Filtered purchases API failed:", response.status)
                js("Promise.resolve([])")
            }
        }
        .then { purchases: dynamic ->
            val purchasesArray = js("Array.isArray(purchases) ? purchases : (purchases ? [purchases] : [])") as Array<dynamic>
            Logger.debug("Loaded ${purchasesArray.size} purchases for Country: $selectedCountry, POL: $selectedPol")
            clearBookingListTable()
            if (purchasesArray.isNotEmpty()) {
                displayPurchasesAsCarsAPPEND(purchasesArray)
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error loading filtered purchases: $error")
        }
}

fun clearBookingListTable() {
    carBookingDisplayedCars = emptyArray()
    val tbody = document.getElementById("carSelectionTableBody")
    tbody?.innerHTML = ""
    Logger.debug("Cleared booking list table")
    updateBookingCarsSelectedCount()
    updateBookingSelectAllCheckbox()
}

fun updateBookingCarsSelectedCount() {
    val countEl = document.getElementById("bookingCarsSelectedCount") as? HTMLElement ?: return
    val selectedCount = if (isCarBookingRecreateSession()) {
        document.getElementById("carSelectionTableBody")?.querySelectorAll("tr")?.length ?: 0
    } else {
        val tableBody = document.getElementById("carSelectionTableBody")
        var checkboxes = tableBody?.querySelectorAll("input[type='checkbox'].car-checkbox")
        if (checkboxes == null || checkboxes.length == 0) {
            checkboxes = tableBody?.querySelectorAll("input[type='checkbox']")
        }
        var checked = 0
        if (checkboxes != null) {
            for (i in 0 until checkboxes.length) {
                val checkbox = checkboxes.item(i) as? HTMLInputElement
                if (checkbox?.checked == true) checked++
            }
        }
        checked
    }
    countEl.textContent = if (selectedCount == 1) "1 CAR SELECTED" else "$selectedCount CARS SELECTED"
    if (selectedCount == 0) {
        countEl.classList.add("is-empty")
    } else {
        countEl.classList.remove("is-empty")
    }
}

fun updateBookingSelectAllCheckbox() {
    if (isCarBookingRecreateSession()) return
    val selectAllCheckbox = document.getElementById("selectAllCars") as? HTMLInputElement ?: return
    val tableBody = document.getElementById("carSelectionTableBody") ?: return
    val checkboxes = tableBody.querySelectorAll("input[type='checkbox'].car-checkbox")
    val total = checkboxes.length
    if (total == 0) {
        selectAllCheckbox.checked = false
        selectAllCheckbox.indeterminate = false
        return
    }
    var checkedCount = 0
    for (i in 0 until total) {
        val checkbox = checkboxes.item(i) as? HTMLInputElement
        if (checkbox?.checked == true) checkedCount++
    }
    selectAllCheckbox.checked = checkedCount == total
    selectAllCheckbox.indeterminate = checkedCount > 0 && checkedCount < total
}

fun hideChassisSuggestions() {
    val suggestionsDiv = document.getElementById("chassisSuggestions")
    if (suggestionsDiv != null) {
        (suggestionsDiv as HTMLElement).style.display = "none"
    }
}

/** Fetch chassis suggestions from purchases, filtered by selected Country & POL and matching query. */
fun fetchChassisSuggestionsForBooking(query: String) {
    if (query.trim().isEmpty()) {
        hideChassisSuggestions()
        return
    }
    val selectedCountry = (document.getElementById("consigneeCountry") as? HTMLSelectElement)?.value?.trim() ?: ""
    val selectedPol = (document.getElementById("polPort") as? HTMLSelectElement)?.value?.trim() ?: ""
    val encoded = js("encodeURIComponent")(query.trim()) as String

    // If both country and POL are selected, use filtered-chassis endpoint for accuracy
    if (selectedCountry.isNotEmpty() && selectedPol.isNotEmpty()) {
        val encodedCountry = js("encodeURIComponent")(selectedCountry) as String
        val encodedPol = js("encodeURIComponent")(selectedPol) as String
        val url = apiUrl("purchases/filtered-chassis?country=$encodedCountry&polPort=$encodedPol")
        window.fetch(url)
            .then { response: dynamic ->
                if (response.ok) response.json() else js("Promise.resolve([])")
            }
            .then { data: dynamic ->
                val arr = js("Array.isArray(data) ? data : (data ? [data] : [])") as Array<dynamic>
                val q = query.trim().lowercase()
                val matching = arr
                    .map { it?.toString() ?: "" }
                    .filter { it.isNotBlank() && it.lowercase().contains(q) }
                    .toTypedArray()
                showChassisSuggestions(matching)
            }
            .catch { _: dynamic -> hideChassisSuggestions() }
    } else {
        // Fallback: search all purchases by query
        val url = apiUrl("purchases/search-chassis?query=$encoded")
        window.fetch(url)
            .then { response: dynamic ->
                if (response.ok) response.json() else js("Promise.resolve([])")
            }
            .then { data: dynamic ->
                val arr = js("Array.isArray(data) ? data : (data ? [data] : [])") as Array<dynamic>
                val chassisSet = mutableSetOf<String>()
                for (i in arr.indices) {
                    val ch = (js("arr[i].chassis") as? String) ?: ""
                    if (ch.isNotBlank()) chassisSet.add(ch)
                }
                showChassisSuggestions(chassisSet.toTypedArray())
            }
            .catch { _: dynamic -> hideChassisSuggestions() }
    }
}

fun searchCarsByChassis(chassis: String) {
    Logger.debug("Searching cars by chassis: $chassis")
    
    if (chassis == null || chassis == "" || chassis.trim() == "") {
        Logger.debug("Empty chassis, skipping search (table remains unchanged)")
        return
    }
    
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/search-chassis?query=$encodedChassis")
    Logger.debug("Fetching from URL: $url")
    
    val scope = MainScope()
    scope.launch {
        val result = ApiClient.get<Array<dynamic>>("purchases/search-chassis?query=$encodedChassis")
        result.fold(
            onSuccess = { purchases ->
                Logger.debug("Search results received: ${purchases.size} items")
                
                // Check if purchases is an array
                val isArray = js("Array.isArray")(purchases).unsafeCast<Boolean>()
                if (isArray) {
                    val purchasesArray = purchases as Array<dynamic>
                    Logger.debug("Purchases array size: ${purchasesArray.size}")
                    if (purchasesArray.isNotEmpty()) {
                        Logger.debug("Displaying ${purchasesArray.size} purchase(s) in table")
                        try {
                            displayPurchasesAsCarsAPPEND(purchases)
                            Logger.debug("Successfully called displayPurchasesAsCarsAPPEND")
                        } catch (e: dynamic) {
                            Logger.error("Error displaying purchases: $e")
                        }
                    } else {
                        Logger.debug("No purchases found for chassis: $chassis")
                        // Don't clear table - keep existing entries
                    }
                } else {
                    Logger.warn("Invalid response format (not an array)")
                    // Don't clear table - keep existing entries
                }
            },
            onError = { message, _ ->
                Logger.error("Error searching cars: $message")
                searchCarsFallback(chassis)
            }
        )
    }
}

/**
 * Read total C&F or FOB from API/JS purchase (Jackson: camelCase; snake_case fallback).
 * After removing persisted total_cnf_price / total_fob_price, falls back to totalPrice then price.
 */
private fun readPurchaseTotalNullable(purchase: dynamic, mode: String): Double? {
    if (purchase == null) return null
    // Do NOT call .asDynamic() on a `dynamic` receiver — JS looks up .asDynamic on the object (plain JSON has no such method).
    val p = purchase
    val a: dynamic
    val b: dynamic
    when (mode) {
        "C&F" -> {
            a = p.totalCnfPrice
            b = p.total_cnf_price
        }
        "FOB" -> {
            a = p.totalFobPrice
            b = p.total_fob_price
        }
        else -> return null
    }
    val fromStored = firstDefinedNumeric(a, b)?.let { v -> dynamicToDouble(v) }
    if (fromStored != null) return fromStored
    val totalPrice = js("p.totalPrice") ?: js("p.total_price")
    val price = js("p.price")
    return parsePurchaseMoneyField(totalPrice) ?: parsePurchaseMoneyField(price)
}

private fun parsePurchaseMoneyField(field: dynamic): Double? {
    if (field == null) return null
    if (js("field === undefined") as Boolean) return null
    if (js("typeof field === 'number'") as Boolean) {
        return if (js("isNaN(field)") as Boolean) null else (field as Number).toDouble()
    }
    val s = js("String(field)").toString().replace("¥", "").replace(",", "").trim()
    if (s.isEmpty()) return null
    return s.toDoubleOrNull()
}

/** Convert JSON number/string to Double; null if not parseable. */
private fun dynamicToDouble(v: dynamic): Double? {
    if (v == null) return null
    if (js("v === undefined") as Boolean) return null
    if (js("typeof v === 'number'") as Boolean) {
        return if (js("isNaN(v)") as Boolean) null else (v as Number).toDouble()
    }
    val n = js("parseFloat(String(v))")
    return if (n != null && !(js("isNaN(n)") as Boolean)) (n as Number).toDouble() else null
}

private fun firstDefinedNumeric(a: dynamic, b: dynamic): dynamic? {
    if (isDefinedNumeric(a)) return a
    if (isDefinedNumeric(b)) return b
    return null
}

private fun isDefinedNumeric(v: dynamic): Boolean {
    if (v == null) return false
    if (js("v === undefined") as Boolean) return false
    if (js("typeof v === 'number'") as Boolean) return !(js("isNaN(v)") as Boolean)
    if (js("typeof v === 'string'") as Boolean) {
        val t = js("String(v).trim()") as String
        if (t.isEmpty()) return false
        val n = js("parseFloat(t)")
        return !(js("isNaN(n)") as Boolean)
    }
    return false
}

private fun readPurchaseTotalForList(purchase: dynamic, mode: String): Double {
    return readPurchaseTotalNullable(purchase, mode) ?: 0.0
}

/** Replace or append one purchase in [carBookingDisplayedCars] (used when refresh returns updated totals). */
private fun mergePurchaseIntoDisplayedCars(purchase: dynamic) {
    val chassis = (purchase.chassis as? String)?.trim() ?: return
    if (chassis.isEmpty()) return
    val out = mutableListOf<dynamic>()
    var replaced = false
    for (i in 0 until carBookingDisplayedCars.size) {
        val c = carBookingDisplayedCars[i]
        val ch = (c.chassis as? String)?.trim() ?: ""
        if (ch == chassis) {
            out.add(purchase)
            replaced = true
        } else {
            out.add(c)
        }
    }
    if (!replaced) out.add(purchase)
    carBookingDisplayedCars = out.toTypedArray()
}

/** Price column was removed from the list table; keep as no-op so refresh merges do not overwrite STOCK. */
private fun updateBookingTablePriceCellForChassis(@Suppress("UNUSED_PARAMETER") chassisNumber: String, @Suppress("UNUSED_PARAMETER") purchase: dynamic) {
    if (lastCalculationMode.isEmpty()) return
    // Intentionally no UI update — C&F/FOB price column is not shown in the LIST table.
}

fun displayPurchasesAsCarsAPPEND(purchases: dynamic) {
    Logger.debug("Displaying purchases as cars, calculation mode: $lastCalculationMode")
    
    // Save displayed cars data for state persistence - ACCUMULATE instead of overwrite
    val displayedCarsArray = js("Array.isArray(purchases) ? purchases : [purchases]") as Array<dynamic>
    
    // Add new cars to existing displayed cars (avoid duplicates)
    val newCars = mutableListOf<dynamic>()
    for (newCar in displayedCarsArray) {
        val chassis = newCar.chassis
        val alreadyExists = carBookingDisplayedCars.any { it.chassis == chassis }
        if (!alreadyExists) {
            newCars.add(newCar)
        }
    }
    carBookingDisplayedCars = carBookingDisplayedCars + newCars.toTypedArray()
    
    console.log("💾 Accumulated displayed cars for state persistence:", carBookingDisplayedCars.size)
    
    // Debug: Check if table body exists
    val tbody = document.getElementById("carSelectionTableBody")
    console.log("Table body element found:", tbody)
    console.log("Table body element type:", tbody?.tagName)
    
    if (tbody == null) {
        console.error("Car table body not found!")
        return
    }
    
    // Don't clear the table - append new cars instead
    console.log("✅ TABLE NOT CLEARED - APPENDING MODE ACTIVE")
    
    val purchasesArray = js("Array.isArray(purchases) ? purchases : [purchases]") as Array<dynamic>
    console.log("🚀 NEW APPEND LOGIC: Processing", purchasesArray.size, "purchases to ADD to existing table")
    
    // Get current row count to continue numbering
    val currentRowCount = tbody.children.length
    console.log("Current table has", currentRowCount, "rows, adding", purchasesArray.size, "more")
    
    for (index in purchasesArray.indices) {
        val purchase = purchasesArray[index]
        
        // Check if this car is already in the table (avoid duplicates)
        val chassisNumber = purchase.chassis ?: "N/A"
        val existingRows = tbody.querySelectorAll("tr")
        var carAlreadyExists = false
        
        for (i in 0 until existingRows.length) {
            val row = existingRows.item(i) as HTMLElement
            val chassisCell = row.querySelector("td:nth-child(3)") // 3rd column is chassis (after checkbox column)
            if (chassisCell?.textContent?.trim() == chassisNumber) {
                carAlreadyExists = true
                console.log("Car with chassis", chassisNumber, "already exists in table, skipping")
                break
            }
        }
        
        if (carAlreadyExists) {
            mergePurchaseIntoDisplayedCars(purchase)
            updateBookingTablePriceCellForChassis(chassisNumber, purchase)
            continue
        }
        
        val rowNumber = currentRowCount + index + 1
        val purchaseId = (purchase.id as? Number)?.toLong() ?: 0L
        val chStr = chassisNumber.toString()
        val nameStr = (purchase.carName ?: "N/A").toString()
        val yearStr = formatCarModelYear(purchase.carModelYear?.toString())
        val stockRaw = (purchase.stockLocation ?: purchase.stock_location ?: "").toString()
        val stockStr = firstSemicolonToken(stockRaw)
        val noChip = formatPurchaseListCellChipHtml(rowNumber.toString())
        val chChip = formatPurchaseListCellChipHtml(chStr)
        val nmChip = formatPurchaseListCellChipHtml(nameStr)
        val yrChip = if (yearStr.isNotBlank()) formatPurchaseListCellChipHtml(yearStr) else ""
        val stockChip = if (stockStr.isNotBlank()) formatPurchaseListCellChipHtml(stockStr) else ""
        
        val chAttr = chStr.replace("&", "&amp;").replace("\"", "&quot;")
        val historyId = carBookingShippingRecreateChassisToHistoryId[chStr.uppercase()] ?: 0L
        val isSold = purchaseDynIsSold(purchase)
        val selectCellHtml = if (isCarBookingRecreateSession()) {
            if (isSold) {
                bookingListSoldLockedHtml()
            } else {
                bookingListRemoveButtonHtml(purchaseId, chAttr, historyId)
            }
        } else {
            """<input type="checkbox" class="car-checkbox" data-purchase-id="$purchaseId" data-chassis="$chAttr" aria-label="Select row">"""
        }
        val row = document.createElement("tr")
        row.setAttribute("data-purchase-id", purchaseId.toString())
        row.setAttribute("data-chassis", chStr)
        if (isSold) row.setAttribute("data-sold", "true")
        row.innerHTML = """
            <td class="booking-td booking-td-select">
                $selectCellHtml
            </td>
            <td class="booking-td">$noChip</td>
            <td class="booking-td">$chChip</td>
            <td class="booking-td">$nmChip</td>
            <td class="booking-td">$yrChip</td>
            <td class="booking-td">$stockChip</td>
        """
        tbody.appendChild(row)

    }
    
    console.log("✅ Added", purchasesArray.size, "new cars to table")
    updateBookingCarsSelectedCount()
    updateBookingSelectAllCheckbox()
}

fun searchCarsFallback(searchTerm: String) {
    console.log("Using fallback search for term:", searchTerm)
    
    // Sample purchase data for testing
    val samplePurchases = listOf(
        mapOf("id" to 1, "chassis" to "KDH201-5012551", "carName" to "Toyota Camry", "carModelYear" to "2020"),
        mapOf("id" to 2, "chassis" to "KDH201-5012552", "carName" to "Honda Accord", "carModelYear" to "2021"),
        mapOf("id" to 3, "chassis" to "KDH201-5012553", "carName" to "Nissan Altima", "carModelYear" to "2019"),
        mapOf("id" to 4, "chassis" to "KDH201-5012554", "carName" to "BMW 3 Series", "carModelYear" to "2022"),
        mapOf("id" to 5, "chassis" to "KDH201-5012555", "carName" to "Mercedes C-Class", "carModelYear" to "2021")
    )
    
    // Filter by search term
    val filteredPurchases = samplePurchases.filter { purchase ->
        val chassis = purchase["chassis"] as? String ?: ""
        chassis.contains(searchTerm, ignoreCase = true)
    }
    
    console.log("Fallback search found", filteredPurchases.size, "matching purchases")
    displayPurchasesAsCarsAPPEND(filteredPurchases.toTypedArray())
}

fun showCalculateFreightPage() {
    try {
        console.log("🚢 Opening Calculate Freight page...")
        
        // First try to get cars from C&F confirmed cars list
        val selectedCars = if (cnfConfirmedCars.isNotEmpty()) {
            console.log("📋 Using confirmed cars from C&F page:", cnfConfirmedCars.size)
            cnfConfirmedCars
        } else if (fobConfirmedCars.isNotEmpty()) {
            // Second: use cars from FOB confirmed cars list
            console.log("📋 Using confirmed cars from FOB page:", fobConfirmedCars.size)
            fobConfirmedCars
        } else if (cnfPageSelectedCars.isNotEmpty()) {
            // Third: use cars that were passed to C&F page from Car Booking
            console.log("📋 Using cars from C&F page selection:", cnfPageSelectedCars.size)
            cnfPageSelectedCars
        } else {
            // Fallback: try to get selected cars from the car selection table
            console.log("📋 No confirmed cars found, trying to get from car selection table...")
            getSelectedCarsFromTable()
        }
        
        console.log("📋 Selected cars found:", selectedCars.size)
        console.log("📋 Selected cars details:", selectedCars)
        
        if (selectedCars.isEmpty()) {
            js("alert('Please select cars first before calculating freight!')")
            return
        }

        val stockLoc = resolveStockLocationForFreight(selectedCars)
        if (stockLoc.isEmpty()) {
            showMessage(
                "Could not determine stock location for the selected cars. It must match Shipping Charge Map (e.g. load cars from search so stock location is known).",
                "error",
            )
            return
        }

        fetchFreightScmTiersForStockLocation(stockLoc) { _ ->
            val freightPageHTML = createFreightCalculationHTML(selectedCars)
            val mainContent = document.getElementById("content")
            if (mainContent != null) {
                mainContent.innerHTML = freightPageHTML
                setupFreightCalculationListeners(selectedCars)
                generateContainerSections(selectedCars)
                restoreFreightAllocations(selectedCars)
                console.log("✅ Freight calculation page loaded successfully (stock: $stockLoc)")
            } else {
                console.error("❌ Main content element not found")
            }
        }
        
    } catch (e: dynamic) {
        console.error("❌ Error opening freight calculation page:", e)
        js("alert('Error opening freight calculation page: ' + e.message)")
    }
}

fun getSelectedCarsFromTable(): List<dynamic> {
    val selectedCars = mutableListOf<dynamic>()
    val tableBody = document.getElementById("carSelectionTableBody")
    val recreateAllRows = isCarBookingRecreateSession()
    
    console.log("🔍 Looking for car selection table...")
    console.log("🔍 Table body element:", tableBody)
    
    if (tableBody != null) {
        val rows = tableBody.querySelectorAll("tr")
        console.log("🔍 Found ${rows.length} rows in table")
        
        for (i in 0 until rows.length) {
            val row = rows[i] as HTMLTableRowElement
            val checkbox = row.querySelector("input[type='checkbox']") as HTMLInputElement?
            val includeRow = recreateAllRows || (checkbox != null && checkbox.checked)
            
            console.log("🔍 Row $i: checkbox found = ${checkbox != null}, checked = ${checkbox?.checked}")
            
            if (includeRow) {
                val chassisCell = row.cells[2] // Chassis is in the third column (index 2)
                val nameCell = row.cells[3]    // Name is in the fourth column (index 3)
                val yearCell = row.cells[4]    // Year is in the fifth column (index 4)
                
                val purchaseId = row.getAttribute("data-purchase-id")?.toLongOrNull()
                    ?: checkbox?.getAttribute("data-purchase-id")?.toLongOrNull()
                
                console.log("🔍 Selected car: chassis=${chassisCell?.textContent}, name=${nameCell?.textContent}, year=${yearCell?.textContent}, purchaseId=$purchaseId")
                
                if (chassisCell != null && nameCell != null && yearCell != null) {
                    val carObject = js("{}")
                    val chassisStr = chassisCell.textContent?.trim().orEmpty()
                    carObject.chassis = chassisCell.textContent
                    carObject.name = nameCell.textContent
                    carObject.year = yearCell.textContent
                    carObject.price = 0 // Will be populated from API
                    if (purchaseId != null) {
                        carObject.id = purchaseId
                        carObject.purchaseId = purchaseId
                    }
                    if (chassisStr.isNotEmpty()) {
                        val fromDisplayed = carBookingDisplayedCars.firstOrNull {
                            it.chassis?.toString()?.trim().equals(chassisStr, ignoreCase = true)
                        }
                        val sl = fromDisplayed?.stockLocation ?: fromDisplayed?.stock_location
                        val sls = sl?.toString()?.trim().orEmpty()
                        if (sls.isNotEmpty()) {
                            carObject.stockLocation = sls
                        }
                    }
                    selectedCars.add(carObject)
                }
            }
        }
    } else {
        console.error("❌ Car selection table body not found!")
    }
    
    console.log("🔍 Total selected cars: ${selectedCars.size}")
    return selectedCars
}

fun getSelectedPurchaseIds(): List<Long> {
    val selectedIds = mutableListOf<Long>()
    val tableBody = document.getElementById("carSelectionTableBody")
    
    console.log("🔍 getSelectedPurchaseIds() called")
    console.log("🔍 Table body element:", tableBody)
    
    if (tableBody != null) {
        if (isCarBookingRecreateSession()) {
            val rows = tableBody.querySelectorAll("tr")
            for (i in 0 until rows.length) {
                val row = rows.item(i) as? HTMLElement ?: continue
                row.getAttribute("data-purchase-id")?.toLongOrNull()?.let { selectedIds.add(it) }
            }
            console.log("🔍 Recreate mode purchase IDs from rows: $selectedIds")
            return selectedIds
        }
        // Try to find checkboxes with class 'car-checkbox' first
        var checkboxes = tableBody.querySelectorAll("input[type='checkbox'].car-checkbox")
        console.log("🔍 Found ${checkboxes.length} checkboxes with class 'car-checkbox'")
        
        // If none found, try finding all checkboxes in the table
        if (checkboxes.length == 0) {
            checkboxes = tableBody.querySelectorAll("input[type='checkbox']")
            console.log("🔍 No 'car-checkbox' class found, trying all checkboxes: ${checkboxes.length} found")
        }
        
        for (i in 0 until checkboxes.length) {
            val checkbox = checkboxes.item(i) as HTMLInputElement
            console.log("🔍 Checkbox $i: checked=${checkbox.checked}, data-purchase-id=${checkbox.getAttribute("data-purchase-id")}")
            
            if (checkbox.checked) {
                // Try to get purchase ID from data attribute
                var purchaseId = checkbox.getAttribute("data-purchase-id")?.toLongOrNull()
                
                // If not found, try to get it from the row's data attribute or from the purchase object
                if (purchaseId == null) {
                    val row = checkbox.closest("tr") as? HTMLTableRowElement
                    if (row != null) {
                        purchaseId = row.getAttribute("data-purchase-id")?.toLongOrNull()
                        console.log("🔍 Trying row data-purchase-id: $purchaseId")
                    }
                }
                
                // If still not found, try to find the purchase ID from the displayed cars data
                if (purchaseId == null) {
                    val chassisCell = (checkbox.closest("tr") as? HTMLTableRowElement)?.cells?.get(2)
                    val chassis = chassisCell?.textContent?.trim()
                    if (chassis != null) {
                        // Find the purchase in carBookingDisplayedCars
                        for (car in carBookingDisplayedCars) {
                            if (car.chassis == chassis) {
                                purchaseId = (car.id as? Number)?.toLong()
                                console.log("🔍 Found purchase ID from displayed cars: $purchaseId for chassis $chassis")
                                break
                            }
                        }
                    }
                }
                
                if (purchaseId != null) {
                    selectedIds.add(purchaseId)
                    console.log("✅ Added purchase ID: $purchaseId")
                } else {
                    console.warn("⚠️ Could not find purchase ID for checked checkbox")
                }
            }
        }
    } else {
        console.error("❌ Car selection table body not found!")
    }
    
    console.log("🔍 Selected purchase IDs: $selectedIds")

    if (selectedIds.isEmpty()) {
        console.warn("⚠️ No checkboxes selected. Falling back to all displayed cars.")
        val fallbackIds = mutableListOf<Long>()
        for (car in carBookingDisplayedCars) {
            val id = (car.id as? Number)?.toLong()
            if (id != null) {
                fallbackIds.add(id)
            }
        }
        if (fallbackIds.isNotEmpty()) {
            selectedIds.addAll(fallbackIds)
            console.log("✅ Defaulted to all displayed cars: $selectedIds")
        } else {
            console.warn("⚠️ No displayed cars available for fallback.")
        }
    }

    return selectedIds
}

fun updateSelectedPurchasesWithBookingData(
    purchaseIds: List<Long>,
    etd: String,
    bookingNo: String,
    vessel: String,
    destination: String,
    consignee: String,
    onComplete: () -> Unit
) {
    if (purchaseIds.isEmpty()) {
        console.log("⚠️ No purchase IDs to update")
        onComplete()
        return
    }
    
    console.log("🔄 Updating ${purchaseIds.size} purchases with booking data...")
    console.log("   ETD → shipmentDate: $etd")
    console.log("   BOOKING NO → bookingId (raw): '$bookingNo'")
    console.log("   BOOKING NO → bookingId (trimmed): '${bookingNo.trim()}'")
    console.log("   VESSEL → vessel: $vessel")
    console.log("   POD → pod column: $destination")
    console.log("   CONSIGNEE → consignee: $consignee")
    
    var completedUpdates = 0
    val totalUpdates = purchaseIds.size
    
    // Convert bookingNo to Long if it's numeric, otherwise null
    val bookingNoTrimmed = bookingNo.trim()
    val bookingIdLong = if (bookingNoTrimmed.isNotEmpty()) {
        bookingNoTrimmed.toLongOrNull()
    } else {
        null
    }
    console.log("   BOOKING ID (converted): $bookingIdLong")
    console.log("   📝 Will update: booking_id=$bookingIdLong for ${purchaseIds.size} purchase(s)")
    
    // Update each purchase sequentially (one after another)
    fun updateNext(index: Int) {
        if (index >= purchaseIds.size) {
            console.log("✅ All purchases updated successfully")
            onComplete()
            return
        }
        
        val purchaseId = purchaseIds[index]
        val payload = js("{}")
        // Map form fields to database columns:
        // ETD → shipmentDate
        payload["shipmentDate"] = etd
        // BOOKING NO → booking_id (must be a plain JSON number; Kotlin Long breaks JSON.stringify on JS IR)
        if (bookingIdLong != null) {
            payload["bookingId"] = bookingIdLong.toDouble()
        } else {
            payload["bookingId"] = null
        }
        // VESSEL → vessel
        payload["vessel"] = vessel
        // POD → purchases.pod (backend also accepts legacy key "destination")
        payload["pod"] = destination
        // CONSIGNEE → consignee (name only; no "Country - " prefix — see consigneeNameWithoutCountryPrefix)
        payload["consignee"] = consigneeNameWithoutCountryPrefix(consignee)
        // Note: booking_requested is set via the Booking Requested button, not Calculate
        // POL is not sent to database (not needed)
        
        Logger.debug("Sending update payload for purchase $purchaseId")
        Logger.debug("Payload contents: bookingId=$bookingIdLong, shipmentDate=$etd, vessel=$vessel, pod=$destination")
        console.log("📦 Update payload for purchase $purchaseId:", JSON.stringify(payload))
        
        val req = js("({})")
        req.method = "PUT"
        val headers = js("({})")
        headers["Content-Type"] = "application/json"
        req.headers = headers
        req.body = JSON.stringify(payload)
        
        window.fetch(apiUrl("purchases/$purchaseId"), req)
            .then { response: dynamic ->
                if (response.ok) {
                    completedUpdates++
                    Logger.debug("✅ Successfully updated purchase $purchaseId ($completedUpdates/$totalUpdates) - booking data updated")
                    console.log("✅ Purchase $purchaseId updated: booking_id=$bookingIdLong")
                    updateNext(index + 1)
                } else {
                    response.text().then { errorText: dynamic ->
                        Logger.error("❌ Failed to update purchase $purchaseId: $errorText")
                        console.error("❌ Failed to update purchase $purchaseId:", errorText)
                        updateNext(index + 1) // Continue with next purchase even if this one failed
                    }
                }
            }
            .catch { error: dynamic ->
                Logger.error("Error updating purchase $purchaseId: $error")
                updateNext(index + 1) // Continue with next purchase even if this one failed
            }
    }
    
    updateNext(0)
}

fun updateChassisDropdown() {
    Logger.debug("Updating chassis dropdown...")
    // Note: This function is for a different purpose (updating selected cars)
    // The chassis search combobox uses "chassisSearch" ID, not "chassisSelect"
    val chassisSelect = document.getElementById("chassisSelect") as? HTMLSelectElement
    if (chassisSelect == null) {
        // This is expected - chassisSelect might not exist on booking page
        // Only log if we're actually trying to update it
        return
    }
    
    val selectedChassis = mutableListOf<String>()
    
    // Get all checked cars from the table
    val checkboxes = document.querySelectorAll("#carSelectionTableBody input[type='checkbox']:checked")
    for (i in 0 until checkboxes.length) {
        val checkbox = checkboxes.item(i) as? HTMLInputElement
        if (checkbox != null) {
            try {
                // closest() returns Element?, need to check if it's a table row
                val rowElement = checkbox.closest("tr")
                if (rowElement != null) {
                    // Use querySelector to get the chassis cell (3rd column, index 2)
                    val chassisCell = rowElement.querySelector("td:nth-child(3)")
                    if (chassisCell != null) {
                        val chassisText = chassisCell.textContent
                        val chassisNumber = chassisText?.trim() ?: ""
                        if (chassisNumber.isNotEmpty()) {
                            selectedChassis.add(chassisNumber)
                        }
                    }
                }
            } catch (e: dynamic) {
                Logger.error("Error extracting chassis from row: $e")
            }
        }
    }
    
    // Clear and repopulate dropdown using innerHTML for safety
    chassisSelect.innerHTML = "<option value=\"\">Select a car chassis...</option>"
    
    // Add selected chassis options using innerHTML (more reliable than appendChild)
    for (chassis in selectedChassis) {
        val option = document.createElement("option")
        option.setAttribute("value", chassis)
        option.textContent = chassis
        chassisSelect.appendChild(option)
    }
    
    Logger.debug("Chassis dropdown updated with ${selectedChassis.size} selected cars")
}

fun addCarToBookingTable(chassis: String) {
    Logger.debug("Adding car to booking table for chassis: $chassis")
    
    // Check if car already exists in table
    val tbody = document.getElementById("carSelectionTableBody")
    if (tbody != null) {
        val existingRows = tbody.querySelectorAll("tr")
        for (i in 0 until existingRows.length) {
            val row = existingRows.item(i) as HTMLElement
            val chassisCell = row.querySelector("td:nth-child(3)") // 3rd column is chassis
            if (chassisCell?.textContent?.trim() == chassis.trim()) {
                Logger.debug("Car with chassis $chassis already exists in table")
                showMessage("Car with chassis $chassis is already in the list", "info")
                return
            }
        }
    }
    
    // Fetch purchase data by chassis
    val encodedChassis = js("encodeURIComponent")(chassis) as String
    val url = apiUrl("purchases/chassis/$encodedChassis")
    Logger.debug("Fetching purchase data from: $url")
    
    window.fetch(url)
        .then { response: dynamic ->
            Logger.debug("Fetch response status: ${response.status}")
            if (response.ok) {
                response.json()
            } else {
                Logger.debug("Purchase not found by chassis, trying search API")
                // Fallback to search API
                val searchUrl = apiUrl("purchases/search-chassis?query=$encodedChassis")
                window.fetch(searchUrl)
                    .then { searchResponse: dynamic ->
                        if (searchResponse.ok) {
                            searchResponse.json()
                        } else {
                            js("Promise.resolve([])")
                        }
                    }
            }
        }
        .then { purchaseData: dynamic ->
            Logger.debug("Purchase data received")
            
            // Handle both single purchase and array of purchases
            val purchasesArray = if (js("Array.isArray")(purchaseData).unsafeCast<Boolean>()) {
                purchaseData as Array<dynamic>
            } else if (purchaseData != null) {
                arrayOf(purchaseData)
            } else {
                emptyArray()
            }
            
            if (purchasesArray.isNotEmpty()) {
                Logger.debug("Adding ${purchasesArray.size} car(s) to table")
                displayPurchasesAsCarsAPPEND(purchasesArray)
                val chassisInput = document.getElementById("chassisSearchInput") as? HTMLInputElement
                chassisInput?.value = ""
            } else {
                Logger.debug("No purchase found for chassis: $chassis")
                showMessage("No purchase found for chassis: $chassis", "warning")
            }
        }
        .catch { error: dynamic ->
            Logger.error("Error fetching purchase by chassis: $error")
            showMessage("Error fetching car data: ${error.message}", "error")
        }
}

private fun refreshBookingFabConsigneeCountryUi() {
    js("if (typeof window.refreshBookingFabSelect === 'function') window.refreshBookingFabSelect('consigneeCountry')")
}

private fun refreshBookingFabPolUi() {
    js("if (typeof window.refreshBookingFabSelect === 'function') window.refreshBookingFabSelect('polPort')")
}

private fun refreshBookingFabCarrierUi() {
    js("if (typeof window.refreshBookingFabSelect === 'function') window.refreshBookingFabSelect('carrierSelect')")
}

private fun syncLastCalculationModeFromShippingHistoryPayload(raw: String) {
    try {
        val o = JSON.parse<dynamic>(raw)
        val rows = o.rows
        if (!js("Array.isArray(rows)").unsafeCast<Boolean>()) return
        val arr = rows.unsafeCast<Array<dynamic>>()
        if (arr.isEmpty()) return
        val pt = (arr[0].priceType?.toString() ?: "").trim()
        if (pt.contains("FOB", ignoreCase = true)) {
            lastCalculationMode = "FOB"
            window.asDynamic().lastCalculationMode = "FOB"
        } else {
            lastCalculationMode = "C&F"
            window.asDynamic().lastCalculationMode = "C&F"
        }
    } catch (_: Throwable) {
    }
}

private fun parseShippingHistoryChassisTokens(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    return raw.split(';', ',', '\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
}

private fun shippingPrefillChassisTokenMatchesPurchase(token: String, purchaseChassis: String): Boolean {
    val t = token.trim()
    val ch = purchaseChassis.trim()
    if (t.isEmpty() || ch.isEmpty()) return false
    if (ch.equals(t, ignoreCase = true)) return true
    val head = ch.substringBefore('-').trim()
    if (head.equals(t, ignoreCase = true)) return true
    if (ch.startsWith(t, ignoreCase = true) &&
        (ch.length == t.length || ch.getOrNull(t.length) == '-')
    ) {
        return true
    }
    return false
}

/** Normalized booking id from API purchase (camelCase or snake_case; number or string). */
private fun readPurchaseBookingIdString(purchase: dynamic): String {
    // Use JS-safe bracket access for plain API JSON objects
    val a = js("(function(p){return p['bookingId'];})(purchase)")
    val b = js("(function(p){return p['booking_id'];})(purchase)")
    val raw: dynamic = when {
        a != null && js("a !== undefined && a !== null") as Boolean -> a
        b != null && js("b !== undefined && b !== null") as Boolean -> b
        else -> null
    } ?: return ""
    if (js("typeof raw === 'number'") as Boolean && !(js("isNaN(raw)") as Boolean)) {
        return (raw as Number).toLong().toString()
    }
    return normalizeBookingIdKey(raw.toString().trim())
}

private fun normalizeBookingIdKey(raw: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    val asLong = s.toLongOrNull()
    if (asLong != null) return asLong.toString()
    val asDouble = s.toDoubleOrNull()
    if (asDouble != null && !asDouble.isNaN()) return asDouble.toLong().toString()
    return s
}

private fun shippingPrefillPurchaseMatchesBookingAndVessel(
    purchase: dynamic,
    historyBookingId: String,
    historyVessel: String,
): Boolean {
    if (historyBookingId.isNotBlank()) {
        val pb = readPurchaseBookingIdString(purchase)
        val hb = normalizeBookingIdKey(historyBookingId)
        if (!hb.equals(pb, ignoreCase = true)) return false
    }
    if (historyVessel.isNotBlank()) {
        val pv = purchase.vessel?.toString()?.trim() ?: ""
        // Use contains instead of exact match: "msc" in history matches "MSC RICCARDA II" in purchase
        val pvLc = pv.lowercase()
        val hvLc = historyVessel.lowercase()
        if (!pvLc.contains(hvLc) && !hvLc.contains(pvLc)) return false
    }
    return true
}

private fun bookingEnsureCountryOption(country: String) {
    if (country.isBlank()) return
    val sel = document.getElementById("consigneeCountry") as? HTMLSelectElement ?: return
    for (i in 0 until sel.options.length) {
        val opt = sel.options.item(i) as? HTMLOptionElement ?: continue
        if (opt.value.equals(country, ignoreCase = true)) {
            sel.value = opt.value
            return
        }
    }
    val opt = document.createElement("option") as HTMLOptionElement
    opt.value = country
    opt.textContent = country
    sel.appendChild(opt)
    sel.value = country
}

private fun bookingEnsurePolOption(pol: String) {
    if (pol.isBlank()) return
    val polSelect = document.getElementById("polPort") as? HTMLSelectElement ?: return
    for (i in 0 until polSelect.options.length) {
        val opt = polSelect.options.item(i) as? HTMLOptionElement ?: continue
        if (opt.value.equals(pol, ignoreCase = true)) {
            polSelect.value = opt.value
            carBookingFormState.polPort = polSelect.value
            return
        }
    }
    val opt = document.createElement("option") as HTMLOptionElement
    opt.value = pol
    opt.textContent = pol
    polSelect.appendChild(opt)
    polSelect.value = pol
    carBookingFormState.polPort = pol
}

private fun bookingEnsureCarrierOption(carrier: String) {
    if (carrier.isBlank()) return
    val sel = document.getElementById("carrierSelect") as? HTMLSelectElement ?: return
    for (i in 0 until sel.options.length) {
        val opt = sel.options.item(i) as? HTMLOptionElement ?: continue
        if (opt.value.equals(carrier, ignoreCase = true)) {
            sel.value = opt.value
            carBookingFormState.carrierSelect = sel.value
            return
        }
    }
    val opt = document.createElement("option") as HTMLOptionElement
    opt.value = carrier
    opt.textContent = carrier
    sel.appendChild(opt)
    sel.value = carrier
    carBookingFormState.carrierSelect = carrier
}

private fun setBookingPodDomValue(pod: String) {
    val el = document.getElementById("podPort") as? HTMLElement ?: return
    if (el.tagName == "SELECT") {
        val sel = el as HTMLSelectElement
        var found = false
        for (i in 0 until sel.options.length) {
            val opt = sel.options.item(i) as? HTMLOptionElement ?: continue
            if (opt.value == pod) {
                found = true
                break
            }
        }
        if (!found && pod.isNotEmpty()) {
            val opt = document.createElement("option") as HTMLOptionElement
            opt.value = pod
            opt.textContent = pod
            sel.appendChild(opt)
        }
        if (pod.isNotEmpty()) sel.value = pod
    } else {
        (el as HTMLInputElement).value = pod
    }
    if (pod.isNotEmpty()) {
        carBookingFormState.podPort = pod
    }
}

private suspend fun awaitLoadStockLocations(country: String) {
    suspendCoroutine<Unit> { cont ->
        val c = country.trim()
        if (c.isEmpty()) {
            cont.resume(Unit)
            return@suspendCoroutine
        }
        loadStockLocations(c) { cont.resume(Unit) }
    }
}

private suspend fun applyShippingHistoryEditPrefillFromJson(raw: String) {
    val o: dynamic
    try {
        o = JSON.parse(raw)
    } catch (_: Throwable) {
        return
    }
    val rowsDyn = o.rows
    if (!js("Array.isArray(rowsDyn)").unsafeCast<Boolean>()) return
    val rowsArr = rowsDyn.unsafeCast<Array<dynamic>>()
    if (rowsArr.isEmpty()) return

    clearCarBookingShippingRecreateSessionData()
    val rows = rowsArr.toList().sortedBy { r ->
        r.id?.toString()?.toLongOrNull() ?: 0L
    }
    val first = rows.first()
    val country = (first.country?.toString() ?: "").trim()
    val consignee = (first.consignee?.toString() ?: "").trim()
    val pol = (first.pol?.toString() ?: "").trim()
    val pod = (first.pod?.toString() ?: "").trim()
    val shipmentDateRaw = (first.shipmentDate?.toString() ?: "").trim()
    val bookingId = (first.bookingId?.toString() ?: "").trim()
    val vessel = (first.vessel?.toString() ?: "").trim()
    val carrier = (first.carrier?.toString() ?: "").trim()
    val priceTypeRaw = (first.priceType?.toString() ?: "").trim()

    js("window.__bookingRestoreInProgress = true")
    try {
        clearBookingListTable()

        val isFob = priceTypeRaw.contains("FOB", ignoreCase = true)
        lastCalculationMode = if (isFob) "FOB" else "C&F"
        window.asDynamic().lastCalculationMode = lastCalculationMode

        val cnfCheckbox = document.getElementById("cnfCheckbox") as? HTMLInputElement
        val fobCheckbox = document.getElementById("fobCheckbox") as? HTMLInputElement
        cnfCheckbox?.checked = !isFob
        fobCheckbox?.checked = isFob
        carBookingFormState.cnfChecked = cnfCheckbox?.checked == true
        carBookingFormState.fobChecked = fobCheckbox?.checked == true
        saveBookingSelectionState(if (isFob) "FOB" else "C&F")

        if (country.isNotEmpty()) {
            bookingEnsureCountryOption(country)
            val countrySel = document.getElementById("consigneeCountry") as? HTMLSelectElement
            currentSelectedCountry = countrySel?.value?.trim()?.ifEmpty { country } ?: country
            carBookingFormState.consigneeCountry = currentSelectedCountry
            refreshBookingFabConsigneeCountryUi()
            carBookingFormState.polPort = pol
            awaitLoadStockLocations(country)
            bookingEnsurePolOption(pol)
            refreshBookingFabPolUi()
        } else {
            if (pol.isNotEmpty()) {
                bookingEnsurePolOption(pol)
                refreshBookingFabPolUi()
            }
        }

        (document.getElementById("consigneeName") as? HTMLInputElement)?.value = consignee
        carBookingFormState.consigneeName = consignee

        setBookingPodDomValue(pod)
        delay(300)
        val podElAfter = document.getElementById("podPort") as? HTMLElement
        if (podElAfter != null) {
            attachPodChangeListener(podElAfter)
            setBookingPodDomValue(pod)
        }

        val etdIso = toIsoFromLabel(shipmentDateRaw).ifBlank { shipmentDateRaw }
        if (etdIso.isNotBlank()) {
            val hiddenEtd = document.getElementById("etdDate") as? HTMLInputElement
            hiddenEtd?.value = etdIso
            carBookingFormState.etdDate = etdIso
            // Prefill runs after bindStrictDateTextMask; visible field is not updated when only hidden changes.
            (document.getElementById("etdDateText") as? HTMLInputElement)?.value = isoToMmDdYyyy(etdIso)
        }
        val cyCutRaw = (first.cyCutDate?.toString() ?: "").trim()
        if (cyCutRaw.isNotEmpty()) {
            setBookingOptionalDateFields("cyCutDate", "cyCutDateText", cyCutRaw)
            carBookingFormState.cyCutDate = toIsoFromLabel(cyCutRaw).ifBlank { cyCutRaw.take(10) }
        }
        val etaRaw = (first.eta?.toString() ?: "").trim()
        if (etaRaw.isNotEmpty()) {
            setBookingOptionalDateFields("etaDate", "etaDateText", etaRaw)
            carBookingFormState.etaDate = toIsoFromLabel(etaRaw).ifBlank { etaRaw.take(10) }
        }
        val finalDestination = (first.finalDestination?.toString() ?: "").trim()
        (document.getElementById("finalDestination") as? HTMLInputElement)?.value = finalDestination
        carBookingFormState.finalDestination = finalDestination
        val notifyParty = (first.notifyParty?.toString() ?: "").trim()
        setEditableComboboxValue("notifyParty", notifyParty)
        carBookingFormState.notifyParty = notifyParty
        (document.getElementById("bookingNo") as? HTMLInputElement)?.value = bookingId
        carBookingFormState.bookingNo = bookingId
        (document.getElementById("vesselSelect") as? HTMLInputElement)?.value = vessel
        carBookingFormState.vesselSelect = vessel
        if (carrier.isNotEmpty()) {
            bookingEnsureCarrierOption(carrier)
            refreshBookingFabCarrierUi()
        } else {
            (document.getElementById("carrierSelect") as? HTMLSelectElement)?.value = ""
            carBookingFormState.carrierSelect = ""
            refreshBookingFabCarrierUi()
        }
        carBookingShippingHistoryEditBookingIdNormalized = normalizeBookingIdKey(bookingId).takeIf { it.isNotEmpty() }

        for (hist in rows) {
            val rowId = shippingHistoryIdFromDynamic(hist)
            if (rowId != null) {
                carBookingShippingRecreateRowIds.add(rowId)
            }
            val rowChassisRaw = (hist.chassis?.toString() ?: "").trim()
            val amount = (hist.amount?.toString() ?: "").trim()
            for (tok in parseShippingHistoryChassisTokens(rowChassisRaw)) {
                val key = tok.uppercase()
                if (rowId != null) {
                    carBookingShippingRecreateChassisToHistoryId[key] = rowId
                }
                if (amount.isNotEmpty()) {
                    val parsed = parseCurrency(amount)
                    carBookingShippingRecreateChassisAmounts[key] =
                        if (parsed > 0.0) parsed.toString() else amount
                }
            }
        }

        // Collect all chassis tokens from all shipping history rows in this edit
        val allChassisTokens = mutableListOf<String>()
        for (hist in rows) {
            val rowChassisRaw = (hist.chassis?.toString() ?: "").trim()
            allChassisTokens.addAll(parseShippingHistoryChassisTokens(rowChassisRaw))
        }

        // Prefer fetching by numeric booking ID directly (avoids string vs numeric mismatch).
        // The shipping_history stores booking_id as a human string (e.g. "HKTG00762300"),
        // but purchases.booking_id is a Long. Extract any numeric suffix from the booking string.
        val numericBookingId: Long? = bookingId.trim().toLongOrNull()
            ?: Regex("(\\d{4,})").findAll(bookingId).lastOrNull()?.value?.toLongOrNull()

        val purchasesForBooking: List<dynamic> = if (numericBookingId != null) {
            when (val r = ApiClient.get<Array<dynamic>>("purchases/by-booking/$numericBookingId")) {
                is ApiResult.Success -> r.data.toList()
                is ApiResult.Error -> emptyList()
            }
        } else emptyList()

        val matched = mutableListOf<dynamic>()
        val seenIds = mutableSetOf<Long>()

        if (purchasesForBooking.isNotEmpty()) {
            // Same booking id can include cars not on this shipping_history row — keep only chassis from the payload.
            for (p in purchasesForBooking) {
                val id = js("p.id")?.toString()?.toLongOrNull() ?: continue
                if (id in seenIds) continue
                val ch = js("p.chassis")?.toString()?.trim() ?: ""
                val onThisShipment = allChassisTokens.any { tok ->
                    shippingPrefillChassisTokenMatchesPurchase(tok, ch)
                }
                if (!onThisShipment) continue
                matched.add(p)
                seenIds.add(id)
            }
        }
        if (matched.isEmpty()) {
            // Fallback: scan all purchases and match by chassis tokens only
            when (val result = ApiClient.get<Array<dynamic>>("purchases")) {
                is ApiResult.Success -> {
                    val arr = result.data
                    for (tok in allChassisTokens) {
                        for (i in 0 until arr.size) {
                            val p = arr[i]
                            val id = js("p.id")?.toString()?.toLongOrNull() ?: continue
                            if (id in seenIds) continue
                            val ch = js("p.chassis")?.toString()?.trim() ?: ""
                            if (!shippingPrefillChassisTokenMatchesPurchase(tok, ch)) continue
                            matched.add(p)
                            seenIds.add(id)
                            break
                        }
                    }
                }
                is ApiResult.Error -> showMessage("Failed to load purchases: ${result.message}", "error")
            }
        }

        displayPurchasesAsCarsAPPEND(matched.toTypedArray())
        lockBookingRecreateCountryAndPol()

        if (matched.isEmpty() && allChassisTokens.isNotEmpty()) {
            showMessage(
                "No purchases matched this shipping history. Check Purchase List.",
                "warning",
            )
        } else if (matched.isNotEmpty()) {
            showMessage("Loaded ${matched.size} vehicle(s) from shipping history.", "success")
        }

        saveCarBookingState()
        persistShippingRecreateMeta()
    } finally {
        js("window.__bookingRestoreInProgress = false")
    }
}

private fun purchaseDynIsSold(p: dynamic): Boolean {
    val ic = p.invoiceConfirmed
    if (ic == true || ic == 1 || ic == "1" || ic?.toString()?.equals("true", ignoreCase = true) == true) {
        return true
    }
    val sold = p.sold
    if (sold == true || sold == 1 || sold == "1" || sold?.toString()?.equals("true", ignoreCase = true) == true) {
        return true
    }
    val status = p.workflowStatus?.toString()?.trim()?.uppercase().orEmpty()
    return status == "INVOICE_CONFIRMED"
}

private fun bookingListSoldLockedHtml(): String {
    return """<span class="booking-row-sold-locked" title="Cannot remove: Sold is true" aria-label="Sold — cannot remove" style="display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:6px;background:#f3f4f6;color:#6b7280;font-size:11px;font-weight:700;user-select:none;">Sold</span>"""
}

private fun bookingListRemoveButtonHtml(purchaseId: Long, chassisAttr: String, historyId: Long): String {
    val hid = if (historyId > 0L) historyId.toString() else ""
    return """<button type="button" class="booking-row-remove-btn" data-purchase-id="$purchaseId" data-chassis="$chassisAttr" data-history-id="$hid" title="Remove car" aria-label="Remove car" style="width:28px;height:28px;border:none;border-radius:6px;background:#fee2e2;color:#b91c1c;cursor:pointer;line-height:1;font-size:18px;font-weight:700;padding:0;display:inline-flex;align-items:center;justify-content:center;">×</button>"""
}

private fun lockBookingRecreateCountryAndPol() {
    listOf("bookingCountryFabTrigger", "bookingPolFabTrigger").forEach { id ->
        (document.getElementById(id) as? HTMLButtonElement)?.let { btn ->
            btn.disabled = true
            btn.asDynamic().style.pointerEvents = "none"
            btn.asDynamic().style.opacity = "0.7"
        }
    }
    document.getElementById("manageBookingMappingsBtn")?.asDynamic()?.style?.display = "none"
}

private fun setupBookingRecreatePageListeners() {
    document.getElementById("deleteShippingHistoryFromRecreate")?.addEventListener("click", { _: Event ->
        handleDeleteShippingHistoryFromRecreate()
    })
    document.getElementById("updateShippingHistoryFromRecreate")?.addEventListener("click", { _: Event ->
        MainScope().launch { saveBookingRecreateShippingHistoryFromList() }
    })
    document.getElementById("carSelectionTableBody")?.addEventListener("click", { event: Event ->
        val target = event.target as? HTMLElement ?: return@addEventListener
        val btn = target.closest(".booking-row-remove-btn") as? HTMLButtonElement ?: return@addEventListener
        event.preventDefault()
        event.stopPropagation()
        handleRemoveChassisFromBookingRecreate(btn)
    })
}

private fun handleRemoveChassisFromBookingRecreate(btn: HTMLButtonElement) {
    val chassis = btn.getAttribute("data-chassis")?.trim().orEmpty()
    if (chassis.isEmpty()) {
        showMessage("Chassis is missing for this row.", "error")
        return
    }
    val purchaseId = btn.getAttribute("data-purchase-id")?.toLongOrNull()
    val soldFromList = carBookingDisplayedCars.any { car ->
        val idMatch = purchaseId != null && (car.id as? Number)?.toLong() == purchaseId
        val chMatch = car.chassis?.toString()?.trim()?.equals(chassis, ignoreCase = true) == true
        (idMatch || chMatch) && purchaseDynIsSold(car)
    }
    if (soldFromList) {
        val soldChassis = carBookingDisplayedCars.mapNotNull { car ->
            val idMatch = purchaseId != null && (car.id as? Number)?.toLong() == purchaseId
            val chMatch = car.chassis?.toString()?.trim()?.equals(chassis, ignoreCase = true) == true
            if ((idMatch || chMatch) && purchaseDynIsSold(car)) {
                car.chassis?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            } else null
        }
        showNoticeModal("Notice", alreadySoldOkMessage(soldChassis.ifEmpty { listOf(chassis) }))
        return
    }
    val ok = window.confirm("Are you sure you want to remove the car?")
    if (!ok) return
    val historyId = btn.getAttribute("data-history-id")?.trim()?.toLongOrNull()
    MainScope().launch {
        val body = js("{}")
        body.chassisToken = chassis
        if (historyId != null && historyId > 0L) {
            body.historyId = historyId
        }
        if (purchaseId != null && purchaseId > 0L) {
            body.purchaseId = purchaseId
        }
        ApiClient.post<dynamic>("shipping-history/remove-chassis", body).fold(
            onSuccess = { data ->
                val d = (data as Any).unsafeCast<dynamic>()
                val deletedRow = d.deletedRow == true ||
                    d.deletedRow?.toString()?.lowercase() == "true"
                val key = chassis.uppercase()
                carBookingShippingRecreateChassisToHistoryId.remove(key)
                carBookingShippingRecreateChassisAmounts.remove(key)
                if (deletedRow && historyId != null) {
                    carBookingShippingRecreateRowIds.remove(historyId)
                }
                persistShippingRecreateMeta()
                removeBookingTableRowForChassis(chassis, purchaseId)
                if (purchaseId != null) {
                    carBookingDisplayedCars = carBookingDisplayedCars.filter {
                        (it.id as? Number)?.toLong() != purchaseId
                    }.toTypedArray()
                } else {
                    carBookingDisplayedCars = carBookingDisplayedCars.filter {
                        it.chassis?.toString()?.trim()?.equals(chassis, ignoreCase = true) != true
                    }.toTypedArray()
                }
                renumberBookingListTable()
                showMessage("Car removed from booking.", "success")
                if (carBookingDisplayedCars.isEmpty() && carBookingShippingRecreateRowIds.isEmpty()) {
                    navigateToApp("/shipping-history")
                }
            },
            onError = { message, statusCode ->
                val soldHint = statusCode == 400 && message.contains("Sold", ignoreCase = true)
                if (soldHint) {
                    showNoticeModal("Notice", alreadySoldOkMessage(listOf(chassis)))
                } else {
                    val msg =
                        if (statusCode == 400 && message.isNotBlank()) message
                        else "Failed to remove car: $message"
                    showMessage(msg, "error")
                }
            },
        )
    }
}

private fun removeBookingTableRowForChassis(chassis: String, purchaseId: Long?) {
    val tbody = document.getElementById("carSelectionTableBody") ?: return
    val rows = tbody.querySelectorAll("tr")
    for (i in 0 until rows.length) {
        val row = rows.item(i) as? HTMLElement ?: continue
        val rowChassis = row.getAttribute("data-chassis")?.trim()
            ?: row.querySelector("td:nth-child(3)")?.textContent?.trim()
        val rowPid = row.getAttribute("data-purchase-id")?.toLongOrNull()
        val matchChassis = rowChassis != null && rowChassis.equals(chassis, ignoreCase = true)
        val matchId = purchaseId != null && rowPid == purchaseId
        if (matchChassis || matchId) {
            row.remove()
            break
        }
    }
}

private fun renumberBookingListTable() {
    val tbody = document.getElementById("carSelectionTableBody") ?: return
    val rows = tbody.querySelectorAll("tr")
    for (i in 0 until rows.length) {
        val row = rows.item(i) as? HTMLElement ?: continue
        val noCell = row.querySelector("td:nth-child(2)")
        if (noCell != null) {
            noCell.innerHTML = formatPurchaseListCellChipHtml((i + 1).toString())
        }
    }
    updateBookingCarsSelectedCount()
}

private fun handleDeleteShippingHistoryFromRecreate() {
    val soldCars = carBookingDisplayedCars.filter { purchaseDynIsSold(it) }
    if (soldCars.isNotEmpty()) {
        val chassisList = soldCars.mapNotNull { it.chassis?.toString()?.trim()?.takeIf { c -> c.isNotEmpty() } }
        showNoticeModal("Notice", alreadySoldOkMessage(chassisList))
        return
    }
    val ok = window.confirm("Are you sure you want to remove the history?")
    if (!ok) return
    MainScope().launch {
        val ids = resolveShippingHistoryIdsForRecreateDelete()
        if (ids.isEmpty()) {
            showMessage("No shipping history is linked to this session.", "error")
            return@launch
        }
        val body = js("{}")
        body.ids = shippingHistoryDeleteIdsJsonArray(ids)
        ApiClient.post<dynamic>("shipping-history/delete-batch", body).fold(
            onSuccess = { _ ->
                clearCarBookingShippingRecreateSessionData()
                carBookingDisplayedCars = emptyArray()
                carBookingShippingHistoryEditBookingIdNormalized = null
                showSuccessModal("Deleted", "Shipping history removed.")
                navigateToApp("/shipping-history")
            },
            onError = { message, statusCode ->
                val soldHint = statusCode == 400 && message.contains("Sold", ignoreCase = true)
                if (soldHint) {
                    val chassisList = carBookingDisplayedCars
                        .mapNotNull { it.chassis?.toString()?.trim()?.takeIf { c -> c.isNotEmpty() } }
                    showNoticeModal("Notice", alreadySoldOkMessage(chassisList))
                } else {
                    val msg =
                        if (statusCode == 400 && message.isNotBlank()) message
                        else "Failed to delete shipping history: $message"
                    showMessage(msg, "error")
                }
            },
        )
    }
}

private fun bookingRecreateCarsMergedForSave(): List<dynamic> {
    val byChassis = mutableMapOf<String, dynamic>()
    for (car in cnfPageSelectedCars) {
        val ch = car.chassis?.toString()?.trim()?.uppercase().orEmpty()
        if (ch.isNotEmpty()) byChassis[ch] = car
    }
    for (car in carBookingDisplayedCars) {
        val ch = car.chassis?.toString()?.trim()?.uppercase().orEmpty()
        if (ch.isEmpty()) continue
        val existing = byChassis[ch]
        if (existing == null) {
            byChassis[ch] = car
        } else {
            val pid = car.id ?: car.purchaseId
            if (pid != null && pid != js("undefined")) {
                existing.id = pid
                existing.purchaseId = pid
            }
        }
    }
    return byChassis.values.toList()
}

private suspend fun saveBookingRecreateShippingHistoryFromList() {
    if (carBookingDisplayedCars.isEmpty()) {
        showMessage("No cars in the list.", "error")
        return
    }
    val etd = bookingFormEtdIso()
    val bookingNo = (document.getElementById("bookingNo") as? HTMLInputElement)?.value?.trim().orEmpty()
    val vessel = (document.getElementById("vesselSelect") as? HTMLInputElement)?.value?.trim().orEmpty()
    val carrier = (document.getElementById("carrierSelect") as? HTMLSelectElement)?.value?.trim().orEmpty()
        .ifEmpty { bookingDynString(carBookingFormState.carrierSelect) }
    if (etd.isEmpty() || bookingNo.isEmpty() || vessel.isEmpty()) {
        showMessage("Please fill in ETD, booking no, and vessel before updating.", "error")
        return
    }
    var selectedCountry = (document.getElementById("consigneeCountry") as? HTMLSelectElement)?.value?.trim().orEmpty()
    if (selectedCountry.isEmpty()) selectedCountry = currentSelectedCountry.trim()
    val consigneeName = (document.getElementById("consigneeName") as? HTMLInputElement)?.value?.trim().orEmpty()
    val pol = (document.getElementById("polPort") as? HTMLSelectElement)?.value?.trim().orEmpty()
        .ifEmpty { carBookingFormState.polPort as? String ?: "" }
    val podEl = document.getElementById("podPort")
    val pod = when {
        podEl?.tagName == "SELECT" -> (podEl as HTMLSelectElement).value?.trim().orEmpty()
        else -> (podEl as? HTMLInputElement)?.value?.trim().orEmpty()
    }
    val cnfCheckbox = document.getElementById("cnfCheckbox") as? HTMLInputElement
    val fobCheckbox = document.getElementById("fobCheckbox") as? HTMLInputElement
    val isFob = fobCheckbox?.checked == true && cnfCheckbox?.checked != true
    cnfPageIsFobMode = isFob
    val priceType = if (isFob) "FOB" else "C&F"
    js("if (typeof window.saveCnfFormState === 'function') window.saveCnfFormState()")

    val carsToSave = bookingRecreateCarsMergedForSave()
    if (carsToSave.isEmpty()) {
        showMessage("No valid chassis to update.", "error")
        return
    }

    val promiseArr = js("[]")
    for (car in carsToSave) {
        val chassis = car.chassis?.toString()?.trim().orEmpty()
        if (chassis.isEmpty()) continue
        promiseArr.push(enrichCarWithCostsFromApi(car, chassis))
    }
    val pc = (promiseArr.length as Number).toInt()
    if (pc == 0) {
        showMessage("No valid chassis to update.", "error")
        return
    }

    suspendCoroutine<Unit> { cont ->
        js("Promise.all")(promiseArr)
            .then { enrichedArr: dynamic ->
                val items = js("[]")
                for (i in 0 until pc) {
                    val enriched = js("(function(a, i) { return a[i]; })")(enrichedArr, i)
                    val chassis = enriched.chassis?.toString()?.trim().orEmpty()
                    if (chassis.isEmpty()) continue
                    val total = computeTotalCnfOrFobForChassis(enriched, chassis, isFob)
                    carBookingShippingRecreateChassisAmounts[chassis.uppercase()] = total.toString()
                    val row: dynamic = js("{}")
                    row.chassis = chassis
                    val client = extractClientNameFromCar(enriched)
                    if (client.isNotEmpty()) row.clientName = client
                    row.amount = total
                    items.push(row)
                }
                if ((items.length as Number).toInt() == 0) {
                    showMessage("No valid chassis to update.", "error")
                    cont.resume(Unit)
                    return@then js("Promise.resolve()")
                }
                val req: dynamic = js("{}")
                req.country = selectedCountry
                req.consignee = consigneeName
                req.notifyParty = getEditableComboboxValue("notifyParty")
                req.shipmentDate = etd
                req.cyCutDate = bookingFormCyCutIso()
                req.eta = bookingFormEtaIso()
                req.pol = pol
                req.pod = pod
                req.finalDestination = (document.getElementById("finalDestination") as? HTMLInputElement)?.value?.trim().orEmpty()
                req.bookingId = bookingNo
                req.vessel = vessel
                req.carrier = carrier
                req.priceType = priceType
                req.items = items
                val requestInit = js("{}")
                requestInit.method = "POST"
                val headers = js("{}")
                headers["Content-Type"] = "application/json"
                requestInit.headers = headers
                requestInit.body = JSON.stringify(req)
                window.fetch(apiUrl("shipping-history/batch"), requestInit)
            }
            .then { response: dynamic ->
                if (!(js("response.ok") as Boolean)) {
                    showMessage("Failed to update shipping history (HTTP ${js("response.status")}).", "error")
                    cont.resume(Unit)
                    return@then Unit
                }
                showSuccessModal("Saved", "Shipping history updated.")
                saveCarBookingState()
                cont.resume(Unit)
                Unit
            }
            .catch { err: dynamic ->
                console.error("saveBookingRecreateShippingHistoryFromList:", err)
                showMessage("Failed to update shipping history.", "error")
                cont.resume(Unit)
            }
    }
}

private fun showConsigneeMapRefreshNoticeModal() {
    val modalId = "consigneeMapRefreshNoticeModal"
    document.getElementById(modalId)?.remove()
    val overlay = document.createElement("div") as HTMLDivElement
    overlay.id = modalId
    overlay.style.cssText = "position:fixed;inset:0;background:rgba(0,0,0,0.45);z-index:10050;display:flex;align-items:center;justify-content:center;padding:16px;"
    overlay.innerHTML = """
        <div style="background:#fff;border-radius:12px;max-width:420px;width:100%;padding:28px 24px;box-shadow:0 10px 40px rgba(0,0,0,0.2);text-align:center;">
            <p style="margin:0 0 20px 0;color:#374151;font-size:16px;line-height:1.5;">New data is available. Refresh the page to load the latest content.</p>
            <button type="button" id="consigneeMapRefreshNoticeOk" style="padding:10px 22px;background:#2563eb;color:#fff;border:none;border-radius:8px;font-size:15px;cursor:pointer;">OK</button>
        </div>
    """.trimIndent()
    document.body?.appendChild(overlay)
    fun dismissOnly() {
        overlay.remove()
    }
    document.getElementById("consigneeMapRefreshNoticeOk")?.addEventListener("click", {
        window.location.reload()
    })
    overlay.addEventListener("click", { e: Event ->
        if (js("e.target === overlay") as Boolean) dismissOnly()
    })
}

