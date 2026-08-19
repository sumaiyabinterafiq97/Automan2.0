package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.js.JSON

private const val QP_MODAL_ID = "quickPurchaseModal"
private var qpEscapeHandler: ((Event) -> Unit)? = null
private var qpSupplierChangeDebounceHandle: Int? = null
private var qpLastSupplierFetchName: String = ""
private var qpLastSupplierFetchAtMs: Double = 0.0

fun openQuickPurchaseModal() {
    closeQuickPurchaseModal()
    closeSidebar()

    val modal = document.createElement("div") as HTMLElement
    modal.id = QP_MODAL_ID
    modal.className = "qp-modal-overlay"
    modal.setAttribute("role", "presentation")

    modal.innerHTML = """
        <div id="quickPurchaseModalContent" class="qp-modal" role="dialog" aria-modal="true" aria-labelledby="qpModalTitle">
            <div class="qp-modal-header">
                <h2 id="qpModalTitle" class="qp-modal-title">Add Quick Purchase</h2>
                <button type="button" id="qpCloseBtn" class="qp-modal-close" aria-label="Close quick purchase">&times;</button>
            </div>
            <div class="qp-modal-body">
                <div class="form-grid-2col qp-form-grid">
                    <div class="qp-field">
                        <label class="qp-label" for="qpDateText">Purchase Date</label>
                        <div class="qp-date-wrap">
                            <div class="qp-date-row">
                                <input type="text" id="qpDateText" placeholder="DD/MM/YYYY" autocomplete="off" class="qp-input">
                                <button type="button" id="qpDateCalendarBtn" class="qp-calendar-btn" title="Open calendar" aria-label="Open purchase date calendar">📅</button>
                            </div>
                            <input type="date" id="qpDate" value="${todayIsoLocalDate()}" tabindex="-1" aria-hidden="true" class="qp-date-hidden">
                        </div>
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpAuctionNo">Auction No</label>
                        <input type="text" id="qpAuctionNo" placeholder="Auction No" class="qp-input" autocomplete="off">
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpChassisInput">Chassis *</label>
                        ${createEditableCombobox("qpChassis", "Select Chassis", required = true)}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpChassisNumberInput">Chassis Number *</label>
                        ${createPlainTextInput("qpChassisNumber", "Suffix", required = true)}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpCarNameInput">Car Name</label>
                        ${createEditableCombobox("qpCarName", "Select Car Name")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpCarModelYearText">Registration Date</label>
                        <div class="qp-date-wrap">
                            <div class="qp-date-row">
                                <input type="text" id="qpCarModelYearText" autocomplete="off" class="qp-input">
                                <button type="button" id="qpCarModelYearCalendarBtn" class="qp-calendar-btn" title="Open month picker" aria-label="Open registration month picker">📅</button>
                            </div>
                            <input type="hidden" id="qpCarModelYear" value="" tabindex="-1" aria-hidden="true">
                            <span id="qpCarModelYearHint" class="qp-month-hint">MM/YYYY</span>
                        </div>
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpGradeInput">Grade</label>
                        ${createEditableCombobox("qpGrade", "Select Grade", showDropdownButton = false)}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpRankInput">Rank</label>
                        ${createEditableCombobox("qpRank", "Select Rank")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpSeatInput">Seat</label>
                        ${createEditableCombobox("qpSeat", "Select Seat", showDropdownButton = false, additionalAttrs = """inputmode="numeric" class="plain-int-input"""")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpDoorInput">Door</label>
                        ${createEditableCombobox("qpDoor", "Select Door", showDropdownButton = false, additionalAttrs = """inputmode="numeric" class="plain-int-input"""")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpColorInput">Color</label>
                        ${createEditableCombobox("qpColor", "Select Color")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpDistance">Mileage</label>
                        <input type="text" id="qpDistance" class="comma-int-input km-suffix-input qp-input" placeholder="e.g., 50,000 km" autocomplete="off">
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpFuelInput">Fuel</label>
                        ${createEditableCombobox("qpFuel", "Select Fuel Type")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpCcInput">CC</label>
                        ${createEditableCombobox("qpCc", "Select CC", showDropdownButton = false, additionalAttrs = """inputmode="numeric" class="plain-int-input"""")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpAuctionNameInput">Supplier Name</label>
                        ${createEditableCombobox("qpAuctionName", "Add Supplier Name")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpStockLocationInput">Stock Location</label>
                        ${createEditableCombobox("qpStockLocation", "Select Stock Location")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpRixoCompanyInput">Rixo Company</label>
                        ${createEditableCombobox("qpRixoCompany", "Select Rixo Company")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpRixoPrice">Rixo Price</label>
                        <div class="currency-input qp-currency">
                            <span class="qp-currency-symbol" aria-hidden="true">¥</span>
                            <input type="text" inputmode="decimal" id="qpRixoPrice" class="money-input qp-input" placeholder="0"
                                   autocomplete="off" onfocus="this.select();">
                        </div>
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpClientNameInput">Client Name</label>
                        ${createEditableCombobox("qpClientName", "Select Client Name")}
                    </div>
                    <div class="qp-field">
                        <label class="qp-label" for="qpCountryInput">Country</label>
                        ${createEditableCombobox("qpCountry", "Select Country")}
                    </div>
                    <div class="qp-field qp-field-span">
                        <label class="qp-label" for="qpPrice">Car Price</label>
                        <div class="qp-price-row">
                            <div class="currency-input qp-currency">
                                <span class="qp-currency-symbol" aria-hidden="true">¥</span>
                                <input type="text" inputmode="decimal" id="qpPrice" class="money-input qp-input" placeholder="0" autocomplete="off">
                            </div>
                            <label class="qp-negotiate" for="qpNegotiate">
                                <input type="checkbox" id="qpNegotiate">
                                NEGOTIATE
                            </label>
                        </div>
                    </div>
                </div>
                <div id="qpNumberCutSection" class="qp-number-cut">
                    <h3 class="qp-section-title">Number Cut Information</h3>
                    <div class="qp-shaken-row">
                        <label class="qp-shaken-check" for="qpShakenCheckbox">
                            <input type="checkbox" id="qpShakenCheckbox">
                            SHAKEN
                        </label>
                        <label class="qp-shaken-check" for="qpShakenWithoutNumberCheckbox">
                            <input type="checkbox" id="qpShakenWithoutNumberCheckbox">
                            Shaken without Number
                        </label>
                    </div>
                    <div id="qpNumberCutFieldsWrap" class="qp-number-cut-fields" style="display: none;">
                        <div class="form-grid-2col qp-form-grid">
                            <div class="qp-field">
                                <label class="qp-label" for="qpNumberCutPlaceInput">Place Name (Japanese)</label>
                                ${createEditableComboboxWithOptions("qpNumberCutPlace", "Select Place", emptyList(), "")}
                            </div>
                            <div class="qp-field">
                                <label class="qp-label" for="qpNumberCutNumber1">Number (English)</label>
                                <input type="number" id="qpNumberCutNumber1" class="qp-input" placeholder="Enter number" autocomplete="off">
                            </div>
                            <div class="qp-field">
                                <label class="qp-label" for="qpNumberCutHiraganaInput">Hiragana Character</label>
                                ${createEditableComboboxWithOptions("qpNumberCutHiragana", "Select Character", getNumberCutHiraganaOptions(), "")}
                            </div>
                            <div class="qp-field">
                                <label class="qp-label" for="qpNumberCutNumber2">Number (English)</label>
                                <input type="number" id="qpNumberCutNumber2" class="qp-input" placeholder="Enter number" autocomplete="off">
                            </div>
                        </div>
                        <div class="qp-field">
                            <label class="qp-label" for="qpNumberCutString">Number Cut</label>
                            <input type="text" id="qpNumberCutString" class="qp-input qp-number-cut-readonly" readonly placeholder="Will be generated automatically">
                        </div>
                    </div>
                </div>
                <div class="qp-options">
                    <label class="qp-label" for="qpOptions">Options</label>
                    <div class="options-buttons-grid" id="qpOptionsButtonsGrid"></div>
                    <input type="hidden" id="qpOptionsPredefined" value="">
                    <div class="qp-options-row">
                        <button type="button" class="option-btn option-btn-basic" data-option="Basic" title="Select ABS, Air Bag, Power Window, Power Steering, AC">Basic</button>
                        <input type="text" id="qpOptions" placeholder="Type custom option and press Enter..." class="qp-input qp-options-custom">
                    </div>
                </div>
                <div class="qp-field">
                    <label class="qp-label" for="qpNotes">Note</label>
                    <textarea id="qpNotes" placeholder="Optional" class="qp-textarea" rows="3"></textarea>
                </div>
                <div class="qp-pictures">
                    <h3 class="qp-section-title">Car Pictures</h3>
                    <div class="qp-pictures-box">
                        <div class="qp-pictures-actions">
                            <label for="carPictures" class="qp-upload-btn">Upload Car Pictures</label>
                            <input type="file" id="carPictures" multiple accept="image/*" class="qp-file-input" onchange="handleCarPictureUpload(this)">
                        </div>
                        <div id="carPicturePreview" class="qp-picture-preview"></div>
                        <div id="uploadProgress" class="qp-upload-progress" style="display:none;">
                            <div class="qp-progress-track">
                                <div id="progressBar" class="qp-progress-bar"></div>
                            </div>
                            <div id="progressText" class="qp-progress-text"></div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="qp-modal-actions">
                <button type="button" id="qpCancelBtn" class="qp-btn qp-btn-secondary">Cancel</button>
                <button type="button" id="qpSaveAndMoreBtn" class="qp-btn qp-btn-teal">Save and Add More</button>
                <button type="button" id="qpSaveBtn" class="qp-btn qp-btn-primary">Save</button>
            </div>
        </div>
    """.trimIndent()

    document.body?.appendChild(modal)

    // Car pictures use the same staging/upload machinery as the Add Purchase form.
    resetPendingCarPictureUploads()
    ensureCarPictureMediaConfig()

    bindStrictDateTextMask("qpDate", null)
    bindStrictMonthYearTextMask("qpCarModelYear", "qpCarModelYearHint")
    bindPurchaseChassisNumberInput("qpChassisNumber")
    setupQuickPurchaseModalListeners()
    setupQuickPurchaseNumberCutListeners()
    preloadQuickPurchaseDropdowns()
    fetchAndRenderPurchaseOptionButtons("qpOptionsButtonsGrid") {
        js("if (typeof window.setupOptionButtons === 'function') window.setupOptionButtons();")
    }

    window.setTimeout({
        (document.getElementById("qpChassisInput") as? HTMLInputElement)?.focus()
    }, 50)
}

fun closeQuickPurchaseModal() {
    qpSupplierChangeDebounceHandle?.let { window.clearTimeout(it) }
    qpSupplierChangeDebounceHandle = null
    qpLastSupplierFetchName = ""
    qpLastSupplierFetchAtMs = 0.0
    qpEscapeHandler?.let { document.removeEventListener("keydown", it) }
    qpEscapeHandler = null
    document.getElementById(QP_MODAL_ID)?.remove()
    // Drop any staged-but-unsaved car pictures so they don't leak into a later Add/Quick Purchase.
    resetPendingCarPictureUploads()
}

private fun resetQuickPurchaseModalForm() {
    (document.getElementById("qpAuctionNo") as? HTMLInputElement)?.value = ""
    listOf(
        "qpChassis", "qpChassisNumber", "qpCarName", "qpAuctionName", "qpStockLocation", "qpRixoCompany",
        "qpClientName", "qpCountry", "qpGrade", "qpRank", "qpSeat", "qpDoor", "qpColor", "qpFuel", "qpCc",
        "qpNumberCutPlace", "qpNumberCutHiragana",
    ).forEach { id ->
        val sel = document.getElementById(id) as? HTMLSelectElement
        val inp = document.getElementById("${id}Input") as? HTMLInputElement
        sel?.value = ""
        inp?.value = ""
    }
    (document.getElementById("qpPrice") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpRixoPrice") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpDistance") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpNotes") as? HTMLTextAreaElement)?.value = ""
    (document.getElementById("qpNegotiate") as? HTMLInputElement)?.checked = false
    (document.getElementById("qpShakenCheckbox") as? HTMLInputElement)?.checked = false
    (document.getElementById("qpShakenWithoutNumberCheckbox") as? HTMLInputElement)?.checked = false
    (document.getElementById("qpNumberCutNumber1") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpNumberCutNumber2") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpNumberCutString") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpNumberCutFieldsWrap") as? HTMLElement)?.style?.display = "none"
    (document.getElementById("qpOptionsPredefined") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpOptions") as? HTMLInputElement)?.value = ""
    document.getElementById("quickPurchaseModalContent")?.querySelectorAll(".option-btn")?.asDynamic()?.forEach { btn: dynamic ->
        (btn as? HTMLElement)?.classList?.remove("selected")
    }
    (document.getElementById("carPicturePreview") as? HTMLElement)?.innerHTML = ""
    (document.getElementById("carPictures") as? HTMLInputElement)?.value = ""
    resetPendingCarPictureUploads()
    writeCarModelYearInput("qpCarModelYear", "")
    window.asDynamic().__qpChassisMappingCache = null
    window.asDynamic().__qpChassisVehicleType = null
    window.asDynamic().__qpResolvedSupplier = null
    val today = todayIsoLocalDate()
    (document.getElementById("qpDate") as? HTMLInputElement)?.value = today
    val dateText = document.getElementById("qpDateText") as? HTMLInputElement
    if (dateText != null) {
        dateText.value = formatWithWeekday(today)
    }
    preloadQuickPurchaseDropdowns()
}

private fun preloadQuickPurchaseDropdowns() {
    js("""
        if ((typeof window.rixoPriceMapping === 'undefined' || !window.rixoPriceMapping) && typeof window.refreshRixoDropdowns === 'function') {
            window.refreshRixoDropdowns();
        }
    """)
    loadAllChassisDropdown(isEditForm = false, preserveForm = false, fieldIdOverride = "qpChassis")
    populateComboboxFromApiForField("qpAuctionName", "rixo/dropdowns/auction-names", "▼")
    populateComboboxFromApiForField("qpClientName", "client-map/dropdowns/client-names", "")
    populateComboboxFromApiForField("qpColor", "master-menu/color", "Select Color")
    populateComboboxFromApiForField("qpFuel", "master-menu/fuel", "Select Fuel")
    populateChassisMappingWithMasterListAsync(
        "qpRank", "Select Rank", emptyList(), getComboboxValueSafe("qpRank"), "master-menu/rank",
    )
    refreshPurchaseClientNameToCountryMap()
    ensureNumberCutPlaceOptionsLoaded {
        repopulateNumberCutPlaceCombobox("qpNumberCutPlace")
    }
    window.fetch(apiUrl("master-menu/country"))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error('country')") }
        .then { raw: dynamic ->
            purchaseMasterCountryList = parsePurchaseMasterListArray(raw)
            rebuildPurchaseTargetCountryDropdown("qpCountry", "")
        }
        .catch { _: dynamic -> console.warn("Quick purchase: failed to load country master list") }
}

private fun populateComboboxFromApiForField(selectId: String, apiPath: String, emptyLabel: String) {
    window.fetch(apiUrl(apiPath))
        .then { r: dynamic -> if (r.ok) r.json() else throw js("Error()") }
        .then { raw: dynamic ->
            val values = parseApiDataStringArray(raw)
            val select = document.getElementById(selectId) as? HTMLSelectElement ?: return@then
            val current = (document.getElementById("${selectId}Input") as? HTMLInputElement)?.value?.trim()
                ?: select.value.trim()
            select.innerHTML = ""
            val def = document.createElement("option") as org.w3c.dom.HTMLOptionElement
            def.value = ""
            def.textContent = emptyLabel
            select.appendChild(def)
            val seen = mutableSetOf<String>()
            values.forEach { v ->
                val t = v.trim()
                if (t.isNotEmpty() && seen.add(t.lowercase())) {
                    val opt = document.createElement("option") as org.w3c.dom.HTMLOptionElement
                    opt.value = t
                    opt.textContent = t
                    select.appendChild(opt)
                }
            }
            if (current.isNotEmpty()) {
                ensureComboboxOptionExistsForPurchase(selectId, current)
                select.value = current
                (document.getElementById("${selectId}Input") as? HTMLInputElement)?.value = current
            }
        }
        .catch { _: dynamic -> console.warn("Quick purchase: failed to load $apiPath") }
}

private fun setupQuickPurchaseModalListeners() {
    document.getElementById("qpCloseBtn")?.addEventListener("click", { _: Event -> closeQuickPurchaseModal() })
    document.getElementById("qpCancelBtn")?.addEventListener("click", { _: Event -> closeQuickPurchaseModal() })

    document.getElementById(QP_MODAL_ID)?.addEventListener("click", { e: Event ->
        if ((e.target as? org.w3c.dom.Element)?.id == QP_MODAL_ID) closeQuickPurchaseModal()
    })

    qpEscapeHandler?.let { document.removeEventListener("keydown", it) }
    val escapeHandler: (Event) -> Unit = { event: Event ->
        val ke = event as? KeyboardEvent
        if (ke?.key == "Escape") {
            // Field-selection overlays (Stock Location / chassis specs) own Escape first.
            if (document.querySelector(".chassis-field-selection-backdrop") == null) {
                event.preventDefault()
                closeQuickPurchaseModal()
            }
        }
    }
    qpEscapeHandler = escapeHandler
    document.addEventListener("keydown", escapeHandler)

    document.getElementById("qpSaveBtn")?.addEventListener("click", { _: Event ->
        saveQuickPurchase(saveAndMore = false)
    })
    document.getElementById("qpSaveAndMoreBtn")?.addEventListener("click", { _: Event ->
        saveQuickPurchase(saveAndMore = true)
    })

    fun onSupplierChange() {
        val name = getComboboxValueSafe("qpAuctionName").trim()
        if (name.isBlank()) return
        val now = js("Date.now()").unsafeCast<Double>()
        // Collapse select+input double-change and ignore identical re-fires within a short window
        if (name.equals(qpLastSupplierFetchName, ignoreCase = true) && (now - qpLastSupplierFetchAtMs) < 400.0) {
            return
        }
        qpSupplierChangeDebounceHandle?.let { window.clearTimeout(it) }
        qpSupplierChangeDebounceHandle = window.setTimeout({
            qpSupplierChangeDebounceHandle = null
            val latest = getComboboxValueSafe("qpAuctionName").trim()
            if (latest.isBlank()) return@setTimeout
            val t = js("Date.now()").unsafeCast<Double>()
            if (latest.equals(qpLastSupplierFetchName, ignoreCase = true) && (t - qpLastSupplierFetchAtMs) < 400.0) {
                return@setTimeout
            }
            qpLastSupplierFetchName = latest
            qpLastSupplierFetchAtMs = t
            fetchSupplierMapByAuctionName(
                latest,
                isEditForm = false,
                purchaseForMerge = null,
                supplierTarget = "quickPurchase",
            )
        }, 50)
    }

    // Listen on the text input only: select onchange → syncComboboxInput already dispatches input change.
    // Listening on both caused duplicate supplier fetches and Stock Location modal reopen/flash.
    document.getElementById("qpAuctionNameInput")?.addEventListener("change", { _: Event -> onSupplierChange() })

    fun onClientChange() {
        applyPurchaseCountryFromClientName("qpClientName")
    }
    document.getElementById("qpClientName")?.addEventListener("change", { _: Event -> onClientChange() })
    document.getElementById("qpClientNameInput")?.addEventListener("change", { _: Event -> onClientChange() })

    // Arrow-key nav for Quick Purchase comboboxes (attach once even before first dropdown open)
    js("if (typeof window.wireAddPurchaseComboboxKeyboardNav === 'function') window.wireAddPurchaseComboboxKeyboardNav();")
}

/** Number Cut + SHAKEN wiring for Quick Purchase (qp* IDs only — does not touch Add/Edit). */
private fun setupQuickPurchaseNumberCutListeners() {
    fun generateQpNumberCutString() {
        val place = getComboboxValueSafe("qpNumberCutPlace").trim()
        val number1 = (document.getElementById("qpNumberCutNumber1") as? HTMLInputElement)?.value?.trim().orEmpty()
        val hiragana = getComboboxValueSafe("qpNumberCutHiragana").trim()
        val number2 = (document.getElementById("qpNumberCutNumber2") as? HTMLInputElement)?.value?.trim().orEmpty()
        val numberCutString = if (place.isNotEmpty() && number1.isNotEmpty() && hiragana.isNotEmpty() && number2.isNotEmpty()) {
            "$place$number1$hiragana$number2"
        } else {
            ""
        }
        (document.getElementById("qpNumberCutString") as? HTMLInputElement)?.value = numberCutString
    }

    fun clearQpNumberCutFields() {
        listOf("qpNumberCutPlace", "qpNumberCutHiragana").forEach { id ->
            (document.getElementById(id) as? HTMLSelectElement)?.value = ""
            (document.getElementById("${id}Input") as? HTMLInputElement)?.value = ""
        }
        (document.getElementById("qpNumberCutNumber1") as? HTMLInputElement)?.value = ""
        (document.getElementById("qpNumberCutNumber2") as? HTMLInputElement)?.value = ""
        (document.getElementById("qpNumberCutString") as? HTMLInputElement)?.value = ""
    }

    fun setQpNumberCutFieldsDisabled(disabled: Boolean) {
        val fieldIds = listOf(
            "qpNumberCutPlaceInput", "qpNumberCutPlace",
            "qpNumberCutHiraganaInput", "qpNumberCutHiragana",
            "qpNumberCutNumber1", "qpNumberCutNumber2", "qpNumberCutString",
        )
        for (fieldId in fieldIds) {
            val el = document.getElementById(fieldId) as? HTMLElement ?: continue
            when (el) {
                is HTMLInputElement -> el.disabled = disabled
                is HTMLSelectElement -> el.disabled = disabled
            }
            if (disabled) {
                el.style.setProperty("pointer-events", "none")
                el.style.setProperty("opacity", "0.6")
                el.style.setProperty("background-color", "#f3f4f6")
            } else {
                el.style.removeProperty("pointer-events")
                el.style.removeProperty("opacity")
                if (fieldId == "qpNumberCutString") {
                    el.style.setProperty("background-color", "#f9f9f9")
                } else {
                    el.style.removeProperty("background-color")
                }
            }
        }
        for (buttonId in listOf("qpNumberCutPlaceButton", "qpNumberCutHiraganaButton")) {
            val btn = document.getElementById(buttonId) as? HTMLElement ?: continue
            if (disabled) {
                btn.style.setProperty("pointer-events", "none")
                btn.style.setProperty("opacity", "0.5")
                btn.style.setProperty("cursor", "not-allowed")
            } else {
                btn.style.removeProperty("pointer-events")
                btn.style.removeProperty("opacity")
                btn.style.setProperty("cursor", "pointer")
            }
        }
    }

    fun syncQpShakenNumberCutUi() {
        val wrap = document.getElementById("qpNumberCutFieldsWrap") as? HTMLElement
        val withNumber = (document.getElementById("qpShakenCheckbox") as? HTMLInputElement)?.checked == true
        val withoutNumber = (document.getElementById("qpShakenWithoutNumberCheckbox") as? HTMLInputElement)?.checked == true
        val anyShaken = withNumber || withoutNumber
        wrap?.style?.setProperty("display", if (anyShaken) "block" else "none")
        setQpNumberCutFieldsDisabled(withoutNumber)
        if (withoutNumber) clearQpNumberCutFields()
    }

    document.getElementById("qpShakenCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        if (target.checked) {
            (document.getElementById("qpShakenWithoutNumberCheckbox") as? HTMLInputElement)?.checked = false
        }
        syncQpShakenNumberCutUi()
    })
    document.getElementById("qpShakenWithoutNumberCheckbox")?.addEventListener("change", { event: Event ->
        val target = event.target as HTMLInputElement
        if (target.checked) {
            (document.getElementById("qpShakenCheckbox") as? HTMLInputElement)?.checked = false
            clearQpNumberCutFields()
        }
        syncQpShakenNumberCutUi()
    })

    document.getElementById("qpNumberCutPlace")?.addEventListener("change", { _: Event -> generateQpNumberCutString() })
    document.getElementById("qpNumberCutPlaceInput")?.addEventListener("change", { _: Event -> generateQpNumberCutString() })
    document.getElementById("qpNumberCutNumber1")?.addEventListener("input", { _: Event -> generateQpNumberCutString() })
    document.getElementById("qpNumberCutHiragana")?.addEventListener("change", { _: Event -> generateQpNumberCutString() })
    document.getElementById("qpNumberCutHiraganaInput")?.addEventListener("change", { _: Event -> generateQpNumberCutString() })
    document.getElementById("qpNumberCutNumber2")?.addEventListener("input", { _: Event -> generateQpNumberCutString() })

    syncQpShakenNumberCutUi()
}

private fun readQuickPurchaseShakenAndNumberCut(): Pair<Boolean, String> {
    val shakenWithNumber = (document.getElementById("qpShakenCheckbox") as? HTMLInputElement)?.checked == true
    val shakenWithoutNumber = (document.getElementById("qpShakenWithoutNumberCheckbox") as? HTMLInputElement)?.checked == true
    val shaken = shakenWithNumber || shakenWithoutNumber
    val numberCut = when {
        shakenWithoutNumber -> ""
        shakenWithNumber -> (document.getElementById("qpNumberCutString") as? HTMLInputElement)?.value?.trim().orEmpty()
        else -> ""
    }
    return shaken to numberCut
}

private fun validateQuickPurchaseNumberCutWhenShaken(): Boolean {
    val shakenWithNumber = (document.getElementById("qpShakenCheckbox") as? HTMLInputElement)?.checked == true
    val shakenWithoutNumber = (document.getElementById("qpShakenWithoutNumberCheckbox") as? HTMLInputElement)?.checked == true
    if (shakenWithoutNumber || !shakenWithNumber) return true
    val missing = mutableListOf<String>()
    if (getComboboxValueSafe("qpNumberCutPlace").trim().isEmpty()) missing.add("Place Name (Japanese)")
    if ((document.getElementById("qpNumberCutNumber1") as? HTMLInputElement)?.value?.trim().isNullOrEmpty()) {
        missing.add("Number (English) — first field")
    }
    if (getComboboxValueSafe("qpNumberCutHiragana").trim().isEmpty()) missing.add("Hiragana Character")
    if ((document.getElementById("qpNumberCutNumber2") as? HTMLInputElement)?.value?.trim().isNullOrEmpty()) {
        missing.add("Number (English) — second field")
    }
    if (missing.isEmpty()) return true
    (document.getElementById("qpNumberCutFieldsWrap") as? HTMLElement)?.style?.display = "block"
    showErrorModal(
        "Number Cut Required",
        "SHAKEN is selected. Please complete all Number Cut detail fields.",
    )
    return false
}

fun populateQuickSupplierDropdownsFromAuction(auctionName: String) {
    window.asDynamic().__supplierAuctionForDropdowns = auctionName
    js("""
        (function() {
            var auc = window.__supplierAuctionForDropdowns;
            if (!auc || typeof normalizeAuctionNameForMapping !== 'function') return;
            var normalized = normalizeAuctionNameForMapping(auc);
            if (!normalized || !window.rixoPriceMapping || !window.rixoPriceMapping[normalized] || !window.rixoPriceMapping[normalized].mappings) return;
            var mappings = window.rixoPriceMapping[normalized].mappings;
            var stocks = window.getUniqueValuesCaseInsensitive(mappings.map(function(m) { return m.stockLocation; }).filter(function(s) { return s && String(s).trim() !== ''; }));
            var rixos = window.getUniqueValuesCaseInsensitive(mappings.map(function(m) { return m.rixoCompany; }).filter(function(c) { return c && String(c).trim() !== ''; }));
            if (typeof window.updateDropdown === 'function') {
                // mergeSupplierThenMaster=true → mapping values + Other Options + master list
                window.updateDropdown('qpStockLocation', 'qpStockLocation', stocks, true);
                window.updateDropdown('qpRixoCompany', 'qpRixoCompany', rixos, true);
            }
        })();
    """)
}

fun applyQuickPurchaseSupplierSelection(selection: dynamic) {
    window.asDynamic().__supplierSel = selection
    window.asDynamic().__qpResolvedSupplier = selection
    js("""
        (function() {
            var sel = window.__supplierSel;
            if (!sel) return;
            var prevSuppress = window.__suppressRixoAutoSelect === true;
            window.__suppressRixoAutoSelect = true;
            try {
                function setOne(id, val) {
                    if (!val || !id) return;
                    if (typeof ensureComboboxOptionExists === 'function') ensureComboboxOptionExists(id, val);
                    if (typeof window.setFieldValue === 'function') window.setFieldValue(id, id, val);
                }
                setOne('qpStockLocation', sel.stockLocation);
                setOne('qpRixoCompany', sel.rixoCompany);
            } finally {
                window.__suppressRixoAutoSelect = prevSuppress;
            }
            window.__qpResolvedSupplier = sel;
            if (typeof window.scheduleAutofillRixoPriceFromMapping === 'function') {
                window.__rixoPriceUserOverride = false;
                var auc = '';
                if (typeof window.getComboboxValue === 'function') {
                    auc = (window.getComboboxValue('qpAuctionName') || '').toString().trim();
                }
                window.scheduleAutofillRixoPriceFromMapping(false, {
                    force: true,
                    delay: 0,
                    inputId: 'qpRixoPrice',
                    auctionName: auc,
                    stockLocation: sel.stockLocation || '',
                    rixoCompany: sel.rixoCompany || '',
                    venueId: sel.venueId || '',
                    pol: sel.pol || '',
                    supportedVehicleType: (window.__qpChassisVehicleType || sel.supportedVehicleType || ''),
                    selection: sel
                });
            }
        })();
    """)
}

fun validateQuickPurchaseChassisPart1(chassis: String): Boolean =
    validateChassisPart1(chassis, selectFieldId = "qpChassis")

fun saveQuickPurchase(saveAndMore: Boolean) {
    val saveBtn = document.getElementById("qpSaveBtn") as? HTMLButtonElement
    val saveMoreBtn = document.getElementById("qpSaveAndMoreBtn") as? HTMLButtonElement
    val activeBtn = if (saveAndMore) saveMoreBtn else saveBtn
    if (activeBtn?.disabled == true) return

    saveBtn?.disabled = true
    saveMoreBtn?.disabled = true
    val originalSave = saveBtn?.textContent ?: "Save"
    val originalMore = saveMoreBtn?.textContent ?: "Save and Add More"
    activeBtn?.textContent = "Saving..."

    val chassis = (document.getElementById("qpChassisInput") as? HTMLInputElement)?.value?.trim() ?: ""
    if (chassis.isBlank()) {
        saveBtn?.disabled = false
        saveMoreBtn?.disabled = false
        saveBtn?.textContent = originalSave
        saveMoreBtn?.textContent = originalMore
        showErrorModal("Validation Error", "Chassis is required. Please select or enter a chassis number.")
        return
    }
    if (!validateQuickPurchaseChassisPart1(chassis)) {
        saveBtn?.disabled = false
        saveMoreBtn?.disabled = false
        saveBtn?.textContent = originalSave
        saveMoreBtn?.textContent = originalMore
        showErrorModal("Invalid Chassis", "Please select chassis from dropdown. The first part cannot be edited manually.")
        return
    }
    if (!validateQuickPurchaseNumberCutWhenShaken()) {
        saveBtn?.disabled = false
        saveMoreBtn?.disabled = false
        saveBtn?.textContent = originalSave
        saveMoreBtn?.textContent = originalMore
        return
    }

    // Quick Purchase: save full chassis CODE-NUMBER (same as Add Purchase).
    val chassisCodeOnly = chassis.substringBefore("-").trim()
    val chassisNumber = (
        (document.getElementById("qpChassisNumberInput") as? HTMLInputElement)?.value
            ?: (document.getElementById("qpChassisNumber") as? HTMLInputElement)?.value
            ?: ""
        ).trim().ifBlank {
            // If user typed CODE-NUMBER into Chassis, keep the suffix
            if (chassis.contains("-")) chassis.substringAfter("-").trim() else ""
        }
    val (validNumber, numberError) = validateRequiredChassisNumber(chassisNumber)
    if (!validNumber) {
        saveBtn?.disabled = false
        saveMoreBtn?.disabled = false
        saveBtn?.textContent = originalSave
        saveMoreBtn?.textContent = originalMore
        showErrorModal("Validation Error", numberError)
        return
    }
    val chassisForSave = composePurchaseChassisForSave(chassisCodeOnly, chassisNumber)

    val purchaseData = js("{}")
    val dateIso = (document.getElementById("qpDate") as? HTMLInputElement)?.value ?: ""
    purchaseData.date = if (dateIso.isNotBlank()) formatWithWeekday(dateIso) else ""
    purchaseData.auctionNo = (document.getElementById("qpAuctionNo") as? HTMLInputElement)?.value?.trim() ?: ""
    purchaseData.chassis = chassisForSave
    purchaseData.carName = getComboboxValueSafe("qpCarName")
    purchaseData.auctionHouse = getComboboxValueSafe("qpAuctionName")
    purchaseData.stockLocation = getComboboxValueSafe("qpStockLocation")
    purchaseData.rixoCompany = persistableRixoCompanyFromCombobox(getComboboxValueSafe("qpRixoCompany"))
    purchaseData.clientName = getComboboxValueSafe("qpClientName")
    purchaseData.country = getComboboxValueSafe("qpCountry")
    purchaseData.grade = getComboboxValueSafe("qpGrade")
    purchaseData.rank = getComboboxValueSafe("qpRank")
    purchaseData.seat = getComboboxValueSafe("qpSeat")
    purchaseData.door = getComboboxValueSafe("qpDoor")
    purchaseData.color = getComboboxValueSafe("qpColor")
    purchaseData.fuel = getComboboxValueSafe("qpFuel")
    val qpCcValue = getComboboxValueSafe("qpCc")
    // Backend expects Int? for cc (same as Add/Edit Purchase).
    purchaseData.cc = if (qpCcValue.isNotEmpty()) {
        val numValue = qpCcValue.replace(Regex("[^0-9]"), "")
        numValue.toIntOrNull()
    } else null
    purchaseData.distance = sanitizeDistanceUiToDb((document.getElementById("qpDistance") as? HTMLInputElement)?.value ?: "")
    val (shaken, numberCut) = readQuickPurchaseShakenAndNumberCut()
    purchaseData.shaken = shaken
    purchaseData.numberCut = numberCut
    val predefinedOpts = (document.getElementById("qpOptionsPredefined") as? HTMLInputElement)?.value?.trim() ?: ""
    val customOpts = (document.getElementById("qpOptions") as? HTMLInputElement)?.value?.trim() ?: ""
    val optionsJoined = listOf(predefinedOpts, customOpts).filter { it.isNotEmpty() }.joinToString(", ")
    if (optionsJoined.isNotBlank()) purchaseData.options = optionsJoined
    val priceValue = js("window.getMoneyRawValue ? window.getMoneyRawValue('qpPrice') : ''").unsafeCast<String>().trim()
    purchaseData.price = if (priceValue.isNotBlank()) "¥$priceValue" else ""
    val rixoPriceValue = js("window.getMoneyRawValue ? window.getMoneyRawValue('qpRixoPrice') : ''").unsafeCast<String>().trim()
    purchaseData.rixoPrice = if (rixoPriceValue.isNotBlank()) "¥$rixoPriceValue" else ""
    purchaseData.negotiate = (document.getElementById("qpNegotiate") as? HTMLInputElement)?.checked ?: false
    val notes = (document.getElementById("qpNotes") as? HTMLTextAreaElement)?.value?.trim().orEmpty()
    if (notes.isNotBlank()) purchaseData.notes = notes
    val regDate = readCarModelYearInput("qpCarModelYear")
    if (regDate.isNotBlank()) purchaseData.carModelYear = regDate

    // Car pictures: same mechanism as Add Purchase. With R2 storage the files are staged and
    // uploaded after create; without R2 they are embedded as base64 on the payload.
    if (!isR2CarPictureStorageEnabled()) {
        val pics = collectCarPictures()
        val picsCount = js("pics.length").unsafeCast<Int>()
        purchaseData.carPictures = if (picsCount > 0) JSON.stringify(pics) else null
    }

    enrichQuickPurchasePayload(purchaseData)

    val requestInit = js("{}")
    requestInit.method = "POST"
    val headers = js("{}")
    headers["Content-Type"] = "application/json"
    requestInit.headers = headers

    finalizeQuickPurchasePayload(purchaseData).then { enriched: dynamic ->
        requestInit.body = JSON.stringify(enriched)
        window.fetch(apiUrl("purchases"), requestInit).then { response ->
            if (response.ok) {
                response.json().then { created: dynamic ->
                    val createdId = (created.id as? Number)?.toLong()
                    // Upload any staged car pictures (R2) before the modal/form is reset/closed.
                    val uploadChain: dynamic = if (createdId != null && isR2CarPictureStorageEnabled()) {
                        uploadPendingCarPicturesAfterCreate(createdId)
                    } else {
                        js("Promise.resolve(null)")
                    }
                    uploadChain.then { _: dynamic ->
                        saveBtn?.disabled = false
                        saveMoreBtn?.disabled = false
                        saveBtn?.textContent = originalSave
                        saveMoreBtn?.textContent = originalMore
                        if (saveAndMore) {
                            resetQuickPurchaseModalForm()
                            showSuccessModal("Saved", "Purchase saved. You can add another.")
                        } else {
                            closeQuickPurchaseModal()
                            showSuccessModal("Saved", "Purchase created successfully!")
                        }
                        if (document.getElementById("purchaseTable") != null) {
                            loadPurchases()
                        }
                    }.catch { uploadErr: dynamic ->
                        saveBtn?.disabled = false
                        saveMoreBtn?.disabled = false
                        saveBtn?.textContent = originalSave
                        saveMoreBtn?.textContent = originalMore
                        console.error("Quick purchase: car picture upload after create failed:", uploadErr)
                        showMessage("Purchase saved, but some pictures failed to upload.", "warning")
                        if (saveAndMore) {
                            resetQuickPurchaseModalForm()
                        } else {
                            closeQuickPurchaseModal()
                        }
                        if (document.getElementById("purchaseTable") != null) {
                            loadPurchases()
                        }
                    }
                }
            } else {
                response.text().then { errorText ->
                    saveBtn?.disabled = false
                    saveMoreBtn?.disabled = false
                    saveBtn?.textContent = originalSave
                    saveMoreBtn?.textContent = originalMore
                    try {
                        val errorJson = JSON.parse<dynamic>(errorText)
                        val errorMessage = errorJson.message as? String ?: errorText
                        if (isPurchaseDuplicateError(response.status, errorMessage)) {
                            showErrorModal("Duplicate Purchase", errorMessage)
                        } else {
                            showMessage("Failed to create purchase: $errorMessage", "error")
                        }
                    } catch (_: dynamic) {
                        if (isPurchaseDuplicateError(response.status, errorText)) {
                            showErrorModal("Duplicate Purchase", errorText)
                        } else {
                            showMessage("Failed to create purchase: $errorText", "error")
                        }
                    }
                }
            }
        }.catch { error: dynamic ->
            saveBtn?.disabled = false
            saveMoreBtn?.disabled = false
            saveBtn?.textContent = originalSave
            saveMoreBtn?.textContent = originalMore
            showMessage("Failed to create purchase: ${error.message}", "error")
        }
    }.catch { error: dynamic ->
        saveBtn?.disabled = false
        saveMoreBtn?.disabled = false
        saveBtn?.textContent = originalSave
        saveMoreBtn?.textContent = originalMore
        showMessage("Failed to prepare purchase: ${error.message}", "error")
    }
}
