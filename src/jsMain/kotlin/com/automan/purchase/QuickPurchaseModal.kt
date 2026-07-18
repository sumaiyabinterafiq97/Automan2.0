package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import kotlin.js.JSON

private const val QP_MODAL_ID = "quickPurchaseModal"

fun openQuickPurchaseModal() {
    closeQuickPurchaseModal()
    closeSidebar()

    val modal = document.createElement("div")
    modal.id = QP_MODAL_ID
    modal.setAttribute(
        "style",
        "position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.5);display:flex;align-items:center;justify-content:center;z-index:10000;"
    )

    modal.innerHTML = """
        <div id="quickPurchaseModalContent" style="background:#fff;padding:24px 28px;border-radius:10px;width:min(720px,94vw);max-height:90vh;overflow-y:auto;box-shadow:0 8px 32px rgba(0,0,0,0.18);">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:20px;">
                <h2 style="margin:0;font-size:20px;color:#1f2937;">Add Quick Purchase</h2>
                <button type="button" id="qpCloseBtn" style="background:none;border:none;font-size:26px;cursor:pointer;color:#6b7280;line-height:1;">&times;</button>
            </div>
            <div class="form-grid-2col" style="display:grid;grid-template-columns:1fr 1fr;gap:16px 20px;">
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Purchase Date</label>
                    <div style="position:relative;width:100%;">
                        <div style="display:flex;gap:8px;align-items:center;">
                            <input type="text" id="qpDateText" placeholder="DD/MM/YYYY" autocomplete="off"
                                   style="flex:1;padding:8px;border:1px solid #ddd;border-radius:4px;box-sizing:border-box;">
                            <button type="button" id="qpDateCalendarBtn" title="Open calendar"
                                    style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                        </div>
                        <input type="date" id="qpDate" value="${todayIsoLocalDate()}" tabindex="-1" aria-hidden="true"
                               style="position:absolute;left:0;top:0;width:0;height:0;opacity:0;border:none;padding:0;margin:0;overflow:hidden;">
                    </div>
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Auction No</label>
                    <input type="text" id="qpAuctionNo" placeholder="Auction No"
                           style="width:100%;padding:8px;border:1px solid #ddd;border-radius:4px;box-sizing:border-box;">
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Chassis *</label>
                    ${createEditableCombobox("qpChassis", "Select Chassis", required = true)}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Car Name</label>
                    ${createEditableCombobox("qpCarName", "Select Car Name")}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Registration Date</label>
                    <div style="position:relative;width:100%;">
                        <div style="display:flex;gap:8px;align-items:center;">
                            <input type="text" id="qpCarModelYearText" autocomplete="off"
                                   style="flex:1;padding:8px;border:1px solid #ddd;border-radius:4px;box-sizing:border-box;">
                            <button type="button" id="qpCarModelYearCalendarBtn" title="Open month picker"
                                    style="flex-shrink:0;padding:8px 10px;border:1px solid #ddd;background:#f9fafb;border-radius:4px;cursor:pointer;">📅</button>
                        </div>
                        <input type="hidden" id="qpCarModelYear" value="" tabindex="-1" aria-hidden="true">
                        <span id="qpCarModelYearHint" style="position:absolute;left:10px;top:50%;transform:translateY(-50%);color:#9ca3af;pointer-events:none;">MM/YYYY</span>
                    </div>
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Supplier Name</label>
                    ${createEditableCombobox("qpAuctionName", "Add Supplier Name")}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Stock Location</label>
                    ${createEditableCombobox("qpStockLocation", "Select Stock Location")}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Rixo Company</label>
                    ${createEditableCombobox("qpRixoCompany", "Select Rixo Company")}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Client Name</label>
                    ${createEditableCombobox("qpClientName", "Select Client Name")}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Country</label>
                    ${createEditableCombobox("qpCountry", "Select Country")}
                </div>
                <div>
                    <label style="display:block;margin-bottom:6px;font-weight:600;color:#374151;">Car Price</label>
                    <div style="display:flex;align-items:center;gap:12px;">
                        <div class="currency-input" style="display:flex;gap:8px;align-items:center;flex:1;min-width:0;">
                            <span style="font-weight:600;color:#374151;">¥</span>
                            <input type="text" inputmode="decimal" id="qpPrice" class="money-input" placeholder="0"
                                   style="flex:1;padding:8px;border:1px solid #ddd;border-radius:4px;box-sizing:border-box;">
                        </div>
                        <label style="display:inline-flex;align-items:center;gap:6px;font-weight:600;color:#374151;cursor:pointer;white-space:nowrap;margin:0;">
                            <input type="checkbox" id="qpNegotiate" style="width:18px;height:18px;accent-color:#007bff;">
                            NEGOTIATE
                        </label>
                    </div>
                </div>
            </div>
            <div style="margin-top:20px;">
                <h3 style="color:#333;margin:0 0 10px 0;border-bottom:1px solid #eee;padding-bottom:5px;font-size:16px;">Car Pictures</h3>
                <div style="padding:20px;border:2px dashed #ddd;border-radius:8px;background-color:#f9f9f9;">
                    <div style="text-align:center;">
                        <label for="carPictures" style="display:inline-block;padding:12px 24px;background-color:#007bff;color:white;border-radius:6px;cursor:pointer;font-weight:600;transition:background-color 0.3s;">
                            📷 Upload Car Pictures
                        </label>
                        <input type="file" id="carPictures" multiple accept="image/*" style="display:none;" onchange="handleCarPictureUpload(this)">
                    </div>
                    <div id="carPicturePreview" style="display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:15px;margin-top:20px;"></div>
                    <div id="uploadProgress" style="display:none;margin-top:15px;">
                        <div style="background-color:#e9ecef;border-radius:4px;height:20px;overflow:hidden;">
                            <div id="progressBar" style="background-color:#007bff;height:100%;width:0%;transition:width 0.3s;"></div>
                        </div>
                        <div id="progressText" style="text-align:center;margin-top:5px;font-size:14px;color:#666;"></div>
                    </div>
                </div>
            </div>
            <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:24px;flex-wrap:wrap;">
                <button type="button" id="qpCancelBtn" style="padding:10px 18px;background:#6c757d;color:#fff;border:none;border-radius:6px;cursor:pointer;">Cancel</button>
                <button type="button" id="qpSaveAndMoreBtn" style="padding:10px 18px;background:#17a2b8;color:#fff;border:none;border-radius:6px;cursor:pointer;">Save and Add More</button>
                <button type="button" id="qpSaveBtn" style="padding:10px 18px;background:#007bff;color:#fff;border:none;border-radius:6px;cursor:pointer;">Save</button>
            </div>
        </div>
    """.trimIndent()

    document.body?.appendChild(modal)

    // Car pictures use the same staging/upload machinery as the Add Purchase form.
    resetPendingCarPictureUploads()
    ensureCarPictureMediaConfig()

    bindStrictDateTextMask("qpDate", null)
    bindStrictMonthYearTextMask("qpCarModelYear", "qpCarModelYearHint")
    setupQuickPurchaseModalListeners()
    preloadQuickPurchaseDropdowns()

    window.setTimeout({
        (document.getElementById("qpChassisInput") as? HTMLInputElement)?.focus()
    }, 50)
}

fun closeQuickPurchaseModal() {
    document.getElementById(QP_MODAL_ID)?.remove()
    // Drop any staged-but-unsaved car pictures so they don't leak into a later Add/Quick Purchase.
    resetPendingCarPictureUploads()
}

private fun resetQuickPurchaseModalForm() {
    (document.getElementById("qpAuctionNo") as? HTMLInputElement)?.value = ""
    listOf("qpChassis", "qpCarName", "qpAuctionName", "qpStockLocation", "qpRixoCompany", "qpClientName", "qpCountry").forEach { id ->
        val sel = document.getElementById(id) as? HTMLSelectElement
        val inp = document.getElementById("${id}Input") as? HTMLInputElement
        sel?.value = ""
        inp?.value = ""
    }
    (document.getElementById("qpPrice") as? HTMLInputElement)?.value = ""
    (document.getElementById("qpNegotiate") as? HTMLInputElement)?.checked = false
    (document.getElementById("carPicturePreview") as? org.w3c.dom.HTMLElement)?.innerHTML = ""
    (document.getElementById("carPictures") as? HTMLInputElement)?.value = ""
    resetPendingCarPictureUploads()
    writeCarModelYearInput("qpCarModelYear", "")
    window.asDynamic().__qpChassisMappingCache = null
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
    refreshPurchaseClientNameToCountryMap()
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

    document.getElementById("qpSaveBtn")?.addEventListener("click", { _: Event ->
        saveQuickPurchase(saveAndMore = false)
    })
    document.getElementById("qpSaveAndMoreBtn")?.addEventListener("click", { _: Event ->
        saveQuickPurchase(saveAndMore = true)
    })

    fun onSupplierChange() {
        val name = getComboboxValueSafe("qpAuctionName").trim()
        if (name.isBlank()) return
        fetchSupplierMapByAuctionName(name, isEditForm = false, purchaseForMerge = null, supplierTarget = "quickPurchase")
    }

    document.getElementById("qpAuctionName")?.addEventListener("change", { _: Event -> onSupplierChange() })
    document.getElementById("qpAuctionNameInput")?.addEventListener("change", { _: Event -> onSupplierChange() })

    fun onClientChange() {
        applyPurchaseCountryFromClientName("qpClientName")
    }
    document.getElementById("qpClientName")?.addEventListener("change", { _: Event -> onClientChange() })
    document.getElementById("qpClientNameInput")?.addEventListener("change", { _: Event -> onClientChange() })

    // Arrow-key nav for Quick Purchase comboboxes (attach once even before first dropdown open)
    js("if (typeof window.wireAddPurchaseComboboxKeyboardNav === 'function') window.wireAddPurchaseComboboxKeyboardNav();")
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

    // Quick Purchase stores the chassis CODE only (drop any typed "-number" suffix). The chassis
    // number belongs to the full Add/Edit form; QP is a minimal quick entry keyed by code.
    val chassisCodeOnly = chassis.substringBefore("-").trim()

    val purchaseData = js("{}")
    val dateIso = (document.getElementById("qpDate") as? HTMLInputElement)?.value ?: ""
    purchaseData.date = if (dateIso.isNotBlank()) formatWithWeekday(dateIso) else ""
    purchaseData.auctionNo = (document.getElementById("qpAuctionNo") as? HTMLInputElement)?.value?.trim() ?: ""
    purchaseData.chassis = chassisCodeOnly
    purchaseData.carName = getComboboxValueSafe("qpCarName")
    purchaseData.auctionHouse = getComboboxValueSafe("qpAuctionName")
    purchaseData.stockLocation = getComboboxValueSafe("qpStockLocation")
    purchaseData.rixoCompany = persistableRixoCompanyFromCombobox(getComboboxValueSafe("qpRixoCompany"))
    purchaseData.clientName = getComboboxValueSafe("qpClientName")
    purchaseData.country = getComboboxValueSafe("qpCountry")
    val priceValue = js("window.getMoneyRawValue ? window.getMoneyRawValue('qpPrice') : ''").unsafeCast<String>().trim()
    purchaseData.price = if (priceValue.isNotBlank()) "¥$priceValue" else ""
    purchaseData.negotiate = (document.getElementById("qpNegotiate") as? HTMLInputElement)?.checked ?: false
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
