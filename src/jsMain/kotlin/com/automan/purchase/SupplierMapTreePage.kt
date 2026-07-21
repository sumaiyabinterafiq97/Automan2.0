package com.automan.purchase

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event
import kotlin.js.unsafeCast

private data class SupplierMapTreeRowLite(
    val id: String,
    val supplier: String,
    val venueId: String?,
    val stock: String,
    val pol: String?,
    val company: String,
    val vType: String?,
    val price: String?,
)

private data class SmLeafRow(val id: Long, val type: String, val priceDisplay: String)

private const val SM_PLACEHOLDER_VENUE = "(no venue id)"
private const val SM_PLACEHOLDER_POL = "(no pol)"
private const val SM_SEMICOLON_REJECT_MSG =
    "Use a single value only. Do not use \";\" to join multiple values. Add separate mappings instead."

private fun smNormalizeSemicolons(raw: String): String =
    raw.replace("\uFF1B", ";").replace("\uFE55", ";")

private fun smContainsSemicolon(raw: String?): Boolean {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return false
    return smNormalizeSemicolons(t).contains(';')
}

/** Returns false and shows an error if any value contains `;`. */
private fun smRejectIfSemicolon(vararg values: String?): Boolean {
    if (values.any { smContainsSemicolon(it) }) {
        showMessage(SM_SEMICOLON_REJECT_MSG, "error")
        return true
    }
    return false
}

private var smTreeRowsCache: List<SupplierMapTreeRowLite> = emptyList()
private var smSelectedSupplier: String? = null
private var smSelectedVenue: String? = null
private var smSelectedStock: String? = null
private var smSelectedPol: String? = null
private var smSelectedCompany: String? = null
private var smSelectedMappingId: Long? = null
private var smLeafInlineEditMappingId: Long? = null
private var smLeafInlineEditLineType: String = ""
private var smMasterVehicleTypes: List<String> = emptyList()
private var smMasterSuppliers: List<String> = emptyList()
private var smMasterStocks: List<String> = emptyList()
private var smMasterPols: List<String> = emptyList()
private var smMasterCompanies: List<String> = emptyList()
private var smMasterOptionsReady: Boolean = false

private var smCardInlineAddLevel: String? = null
private var smCardInlineAddSupplier: String = ""
private var smCardInlineAddVenue: String = ""
private var smCardInlineAddStock: String = ""
private var smCardInlineAddPol: String = ""
private var smCardInlineAddCompany: String = ""

private var smCardInlineEditLevel: String? = null
private var smCardInlineEditSupplier: String = ""
private var smCardInlineEditVenue: String = ""
private var smCardInlineEditStock: String = ""
private var smCardInlineEditPol: String = ""
private var smCardInlineEditCurrentLabel: String = ""

private var smFullRowAddOpen: Boolean = false
private var smSearchQuery: String = ""
private var smSupplierSortOrder: String? = null // null = newest-first; "asc" | "desc"
private var smSearchDebounceTimer: dynamic = null

private fun smSortedSuppliers(list: List<SupplierMapTreeRowLite>): List<String> {
    val grouped = list.groupBy { smNormSupplier(it.supplier) }
        .map { (name, rows) -> name to rows.maxOf { it.id.toLongOrNull() ?: 0L } }
    val q = smSearchQuery.trim().lowercase()
    val filtered = if (q.isEmpty()) grouped else grouped.filter { (name, _) -> name.lowercase().contains(q) }
    return when (smSupplierSortOrder) {
        "asc" -> filtered.sortedBy { it.first.lowercase() }.map { it.first }
        "desc" -> filtered.sortedByDescending { it.first.lowercase() }.map { it.first }
        else -> filtered.sortedWith(
            compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first.lowercase() },
        ).map { it.first }
    }
}

private fun smSearchToolbarHtml(): String = """
    <div class="tree-map-search-toolbar" data-sm-search-toolbar="1">
        <div class="tree-map-search-pill">
            <span class="tree-map-search-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            </span>
            <input type="text" id="smSupplierSearchInput" role="searchbox" autocomplete="off" inputmode="search"
                placeholder="Type to search…" aria-label="Search supplier name"
                value="${escapeHtml(smSearchQuery)}" />
            <button type="button" id="smSupplierSearchClearBtn" class="tree-map-search-clear" title="Clear search"
                style="${if (smSearchQuery.isBlank()) "visibility:hidden;" else ""}">×</button>
        </div>
    </div>
""".trimIndent()

private fun smSupplierSortTooltip(): String = when (smSupplierSortOrder) {
    "asc" -> "Sorted A-Z (click to sort Z-A)"
    "desc" -> "Sorted Z-A (click to sort A-Z)"
    else -> "Sort supplier names A-Z"
}

private fun clearSmFullRowAdd() {
    smFullRowAddOpen = false
}

private fun smDynStr(v: dynamic?): String {
    if (v == null || v == js("undefined")) return ""
    return v.toString().trim()
}

private fun smNormSupplier(s: String) = s.trim().ifEmpty { "(no supplier)" }

private fun smNormVenue(s: String?) =
    s?.trim()?.takeIf { it.isNotEmpty() } ?: SM_PLACEHOLDER_VENUE

private fun smNormPol(s: String?) =
    s?.trim()?.takeIf { it.isNotEmpty() } ?: SM_PLACEHOLDER_POL

private fun smNormStock(s: String) = s.trim().ifEmpty { "(no stock location)" }

private fun smNormCompany(s: String) = s.trim().ifEmpty { "(no company)" }

private fun smIsBlankSupplier(s: String): Boolean {
    val t = s.trim()
    return t.isEmpty() || t == "-" || t.equals("(no supplier)", ignoreCase = true)
}

private fun smSplitVehicleTypes(raw: String?): List<String> {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return emptyList()
    return t.split(";").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun smFormatPriceDisplay(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return "—"
    val digits = s.replace(Regex("[^0-9]"), "")
    if (digits.isEmpty()) return escapeHtml(s)
    val formattedInt = digits.replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
    return "¥$formattedInt"
}

private fun smParseMoney(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return t.replace(",", "").toDoubleOrNull()
}

private fun smNormalizePriceForDb(raw: String): String {
    val n = smParseMoney(raw) ?: return raw.trim()
    return if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
}

private fun parseSupplierMapTreeRows(rows: List<dynamic>): List<SupplierMapTreeRowLite> =
    rows.map { r ->
        SupplierMapTreeRowLite(
            id = smDynStr(r.id),
            supplier = smDynStr(r.auctionName),
            venueId = smDynStr(r.venueId).takeIf { it.isNotEmpty() },
            stock = smDynStr(r.stockLocation),
            pol = smDynStr(r.pol).takeIf { it.isNotEmpty() },
            company = smDynStr(r.rixoCompany),
            vType = smDynStr(r.supportedVehicleType).takeIf { it.isNotEmpty() },
            price = smDynStr(r.rixoPrice).takeIf { it.isNotEmpty() },
        )
    }.filter { !smIsBlankSupplier(it.supplier) }

private fun smVisibleVenues(rows: List<SupplierMapTreeRowLite>): List<String> {
    val hasNull = rows.any { it.venueId.isNullOrBlank() }
    val venues = rows.map { smNormVenue(it.venueId) }
        .filter { it != SM_PLACEHOLDER_VENUE }
        .distinct()
        .sortedBy { it.lowercase() }
    return if (hasNull) listOf(SM_PLACEHOLDER_VENUE) + venues else venues
}

private fun smVisiblePols(rows: List<SupplierMapTreeRowLite>): List<String> {
    val hasNull = rows.any { it.pol.isNullOrBlank() }
    val pols = rows.map { smNormPol(it.pol) }
        .filter { it != SM_PLACEHOLDER_POL }
        .distinct()
        .sortedBy { it.lowercase() }
    return if (hasNull) listOf(SM_PLACEHOLDER_POL) + pols else pols
}

private fun smVisibleStocks(rows: List<SupplierMapTreeRowLite>): List<String> =
    rows.map { smNormStock(it.stock) }.filter { it != "(no stock location)" && it != "-" }.distinct()
        .sortedBy { it.lowercase() }

private fun smVisibleCompanies(rows: List<SupplierMapTreeRowLite>): List<String> =
    rows.map { smNormCompany(it.company) }.filter { it != "(no company)" && it != "-" }.distinct()
        .sortedBy { it.lowercase() }

private fun smRowVenueKey(row: SupplierMapTreeRowLite) = smNormVenue(row.venueId)
private fun smRowPolKey(row: SupplierMapTreeRowLite) = smNormPol(row.pol)

private fun smBuildLeafRows(companyRows: List<SupplierMapTreeRowLite>): List<SmLeafRow> {
    val out = mutableListOf<SmLeafRow>()
    for (r in companyRows) {
        val rowId = r.id.toLongOrNull() ?: continue
        val types = smSplitVehicleTypes(r.vType)
        val priceDisplay = smFormatPriceDisplay(r.price)
        if (types.isEmpty()) {
            if (r.price.isNullOrBlank()) continue
            out.add(SmLeafRow(id = rowId, type = "", priceDisplay = priceDisplay))
        } else {
            for (type in types) {
                out.add(SmLeafRow(id = rowId, type = type, priceDisplay = priceDisplay))
            }
        }
    }
    return out
}

private fun clearSmCardInlineAdd() {
    smCardInlineAddLevel = null
    smCardInlineAddSupplier = ""
    smCardInlineAddVenue = ""
    smCardInlineAddStock = ""
    smCardInlineAddPol = ""
    smCardInlineAddCompany = ""
}

private fun clearSmCardInlineEdit() {
    smCardInlineEditLevel = null
    smCardInlineEditSupplier = ""
    smCardInlineEditVenue = ""
    smCardInlineEditStock = ""
    smCardInlineEditPol = ""
    smCardInlineEditCurrentLabel = ""
}

private fun smListContains(list: List<String>, value: String): Boolean =
    list.any { it.equals(value.trim(), ignoreCase = true) }

private fun smFirstNonBlank(vararg candidates: String?): String {
    for (c in candidates) {
        val t = c?.trim().orEmpty()
        if (t.isNotEmpty()) return t
    }
    return ""
}

private fun smCardInlineAddId(level: String): String = when (level) {
    "supplier" -> "smCardInlineAddSupplier"
    "venue" -> "smCardInlineAddVenue"
    "stock" -> "smCardInlineAddStock"
    "pol" -> "smCardInlineAddPol"
    "rixo_company" -> "smCardInlineAddCompany"
    else -> "smCardInlineAddSupplier"
}

private fun smCardInlineEditId(level: String): String = when (level) {
    "supplier" -> "smCardInlineEditSupplier"
    "venue" -> "smCardInlineEditVenue"
    "stock" -> "smCardInlineEditStock"
    "pol" -> "smCardInlineEditPol"
    "rixo_company" -> "smCardInlineEditCompany"
    else -> "smCardInlineEditSupplier"
}

private fun smCardInlineAddMatchesVenueBranch(supplier: String): Boolean =
    smCardInlineAddLevel == "venue" && smNormSupplier(smCardInlineAddSupplier) == smNormSupplier(supplier)

private fun smCardInlineAddMatchesStockBranch(supplier: String, venue: String): Boolean =
    smCardInlineAddLevel == "stock" &&
        smNormSupplier(smCardInlineAddSupplier) == smNormSupplier(supplier) &&
        smNormVenue(smCardInlineAddVenue) == smNormVenue(venue)

private fun smCardInlineAddMatchesPolBranch(supplier: String, venue: String, stock: String): Boolean =
    smCardInlineAddLevel == "pol" &&
        smNormSupplier(smCardInlineAddSupplier) == smNormSupplier(supplier) &&
        smNormVenue(smCardInlineAddVenue) == smNormVenue(venue) &&
        smNormStock(smCardInlineAddStock) == smNormStock(stock)

private fun smCardInlineAddMatchesCompanyBranch(supplier: String, venue: String, stock: String, pol: String): Boolean =
    smCardInlineAddLevel == "rixo_company" &&
        smNormSupplier(smCardInlineAddSupplier) == smNormSupplier(supplier) &&
        smNormVenue(smCardInlineAddVenue) == smNormVenue(venue) &&
        smNormStock(smCardInlineAddStock) == smNormStock(stock) &&
        smNormPol(smCardInlineAddPol) == smNormPol(pol)

private fun smCardInlineAddMatchesLeafBranch(supplier: String, venue: String, stock: String, pol: String, company: String): Boolean =
    smCardInlineAddLevel == "leaf" &&
        smNormSupplier(smCardInlineAddSupplier) == smNormSupplier(supplier) &&
        smNormVenue(smCardInlineAddVenue) == smNormVenue(venue) &&
        smNormStock(smCardInlineAddStock) == smNormStock(stock) &&
        smNormPol(smCardInlineAddPol) == smNormPol(pol) &&
        smNormCompany(smCardInlineAddCompany) == smNormCompany(company)

private fun smCardInlineEditTargetMatches(level: String, label: String, supplier: String, venue: String, stock: String, pol: String): Boolean {
    if (smCardInlineEditLevel != level) return false
    if (!label.equals(smCardInlineEditCurrentLabel, ignoreCase = true)) return false
    return when (level) {
        "supplier" -> smNormSupplier(smCardInlineEditSupplier) == smNormSupplier(supplier)
        "venue" -> smNormSupplier(smCardInlineEditSupplier) == smNormSupplier(supplier) &&
            smNormVenue(smCardInlineEditVenue) == smNormVenue(venue)
        "stock" -> smNormSupplier(smCardInlineEditSupplier) == smNormSupplier(supplier) &&
            smNormVenue(smCardInlineEditVenue) == smNormVenue(venue) &&
            smNormStock(smCardInlineEditStock) == smNormStock(stock)
        "pol" -> smNormSupplier(smCardInlineEditSupplier) == smNormSupplier(supplier) &&
            smNormVenue(smCardInlineEditVenue) == smNormVenue(venue) &&
            smNormStock(smCardInlineEditStock) == smNormStock(stock) &&
            smNormPol(smCardInlineEditPol) == smNormPol(pol)
        "rixo_company" -> smNormSupplier(smCardInlineEditSupplier) == smNormSupplier(supplier) &&
            smNormVenue(smCardInlineEditVenue) == smNormVenue(venue) &&
            smNormStock(smCardInlineEditStock) == smNormStock(stock) &&
            smNormPol(smCardInlineEditPol) == smNormPol(pol)
        else -> false
    }
}

private fun SupplierMapTreeRowLite.isSmVenueSkeleton(): Boolean =
    smRowVenueKey(this) == SM_PLACEHOLDER_VENUE &&
        (smNormStock(stock) == "(no stock location)" || stock == "-") &&
        vType.isNullOrBlank() && price.isNullOrBlank()

private fun SupplierMapTreeRowLite.isSmStockSkeleton(): Boolean =
    smRowVenueKey(this) != SM_PLACEHOLDER_VENUE &&
        (smNormStock(stock) == "(no stock location)" || stock == "-") &&
        vType.isNullOrBlank() && price.isNullOrBlank()

private fun SupplierMapTreeRowLite.isSmPolSkeleton(): Boolean =
    smNormStock(stock) != "(no stock location)" && stock != "-" &&
        smRowPolKey(this) == SM_PLACEHOLDER_POL &&
        vType.isNullOrBlank() && price.isNullOrBlank()

private fun SupplierMapTreeRowLite.isSmCompanySkeleton(): Boolean =
    smRowPolKey(this) != SM_PLACEHOLDER_POL &&
        (smNormCompany(company) == "(no company)" || company == "-") &&
        vType.isNullOrBlank() && price.isNullOrBlank()

private fun SupplierMapTreeRowLite.isSmLeafSkeleton(): Boolean =
    smNormCompany(company) != "(no company)" && company != "-" &&
        vType.isNullOrBlank() && price.isNullOrBlank()

private fun smMergeRowIdForVenue(supplier: String): Long? {
    val s = smNormSupplier(supplier)
    val rows = smTreeRowsCache.filter { smNormSupplier(it.supplier) == s }
    if (smVisibleVenues(rows).isNotEmpty()) return null
    return rows.filter { it.isSmVenueSkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun smMergeRowIdForStock(supplier: String, venue: String): Long? {
    val rows = smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) && smRowVenueKey(it) == smNormVenue(venue)
    }
    if (smVisibleStocks(rows).isNotEmpty()) return null
    return rows.filter { it.isSmStockSkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun smMergeRowIdForPol(supplier: String, venue: String, stock: String): Long? {
    val rows = smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) &&
            smRowVenueKey(it) == smNormVenue(venue) &&
            smNormStock(it.stock) == smNormStock(stock)
    }
    if (smVisiblePols(rows).isNotEmpty()) return null
    return rows.filter { it.isSmPolSkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun smMergeRowIdForCompany(supplier: String, venue: String, stock: String, pol: String): Long? {
    val rows = smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) &&
            smRowVenueKey(it) == smNormVenue(venue) &&
            smNormStock(it.stock) == smNormStock(stock) &&
            smRowPolKey(it) == smNormPol(pol)
    }
    if (smVisibleCompanies(rows).isNotEmpty()) return null
    return rows.filter { it.isSmCompanySkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun smMergeRowIdForLeaf(supplier: String, venue: String, stock: String, pol: String, company: String): Long? {
    val rows = smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) &&
            smRowVenueKey(it) == smNormVenue(venue) &&
            smNormStock(it.stock) == smNormStock(stock) &&
            smRowPolKey(it) == smNormPol(pol) &&
            smNormCompany(it.company) == smNormCompany(company)
    }
    return rows.filter { it.isSmLeafSkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun smTreeAddButtonHtml(
    level: String,
    supplier: String?,
    venue: String?,
    stock: String?,
    pol: String?,
    company: String?,
): String {
    val wrapClass = if (level == "leaf") "rixo-tree-add-wrap rixo-tree-add-wrap--leaf" else "rixo-tree-add-wrap"
    return """<div class="$wrapClass"><button type="button" class="rixo-tree-add-btn" data-add-level="$level"
        data-supplier="${escapeHtml(supplier ?: "")}" data-venue="${escapeHtml(venue ?: "")}"
        data-stock="${escapeHtml(stock ?: "")}" data-pol="${escapeHtml(pol ?: "")}"
        data-company="${escapeHtml(company ?: "")}">+ Add</button></div>"""
}

private fun buildSmCardInlineAddHtml(level: String): String {
    val comboboxId = smCardInlineAddId(level)
    val placeholder = when (level) {
        "supplier" -> "Select Supplier Name"
        "venue" -> "Enter Venue ID"
        "stock" -> "Select Stock Location"
        "pol" -> "Select POL"
        "rixo_company" -> "Select Rixo Company"
        else -> ""
    }
    val fieldPrimary = if (level == "venue") {
        """<div class="rixo-tree-inline-add-field-primary rixo-tree-inline-add-plain">${createPlainTextInput(comboboxId, placeholder, required = true)}</div>"""
    } else {
        """<div class="rixo-tree-inline-add-field-primary rixo-tree-card-combobox-wrap">${createEditableCombobox(comboboxId, placeholder, required = true)}</div>"""
    }
    return """
        <div class="rixo-tree-inline-add-outer rixo-tree-card--inline-editing" data-level="$level" data-sm-inline-add="1">
            <div class="rixo-tree-inline-add-box">
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--inputs">$fieldPrimary</div>
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--actions">
                    <button type="button" class="rixo-tree-card-inline-add-cancel">Cancel</button>
                    <button type="button" class="rixo-tree-card-inline-add-save">Add</button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildSmLeafInlineAddHtml(): String = """
    <div class="rixo-tree-leaf-row rixo-tree-leaf-row--inline-editing" data-sm-inline-add="leaf">
        <div class="rixo-tree-leaf-cells">
            <div class="rixo-tree-leaf-edit rixo-tree-leaf-edit--inline-add-stack">
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--inputs">
                    <div class="rixo-tree-leaf-vtype-wrap">${createEditableCombobox("smCardInlineAddLeafType", "Select Vehicle Type", required = false)}</div>
                    <input id="smCardInlineAddLeafPrice" class="rixo-tree-leaf-inline-price money-input" type="text" value="" placeholder="0" inputmode="decimal">
                </div>
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--actions">
                    <button type="button" class="rixo-tree-leaf-inline-add-cancel">Cancel</button>
                    <button type="button" class="rixo-tree-leaf-inline-add-save">Add</button>
                </div>
            </div>
        </div>
    </div>
""".trimIndent()

private fun smTreeHeadersHtml(): String = """
    <div class="rixo-tree-headers supplier-map-tree-headers">
        <div class="rixo-tree-header rixo-tree-header--with-sort">
            <button type="button" id="smSupplierSortBtn" class="tree-map-col-sort-btn" data-sm-sort="supplier" title="${escapeHtml(smSupplierSortTooltip())}">
                <span>Supplier Name</span>
                <span class="tree-map-col-sort-icon" aria-hidden="true">↕</span>
            </button>
        </div>
        <div class="rixo-tree-header">Venue ID</div>
        <div class="rixo-tree-header">Stock Location</div>
        <div class="rixo-tree-header">POL</div>
        <div class="rixo-tree-header">Rixo Company</div>
        <div class="rixo-tree-header">Supported Vehicle Type</div>
        <div class="rixo-tree-header rixo-tree-header--price">Rixo Price</div>
    </div>
""".trimIndent()

private fun buildSmFullRowAddHtml(): String = """
    <div class="sm-tree-full-row-add" data-sm-full-row-add="1">
        <div class="sm-tree-full-row-add-grid">
            <div class="sm-tree-full-row-col sm-tree-full-row-col--supplier">
                ${createPlainTextInput("smFullRowSupplier", "Enter Supplier Name", required = true)}
            </div>
            <div class="sm-tree-full-row-col sm-tree-full-row-col--venue">
                ${createPlainTextInput("smFullRowVenue", "Enter Venue ID", required = false)}
            </div>
            <div class="sm-tree-full-row-col sm-tree-full-row-col--stock">
                ${createEditableCombobox("smFullRowStock", "Select Stock Location", required = true)}
            </div>
            <div class="sm-tree-full-row-col sm-tree-full-row-col--pol">
                ${createEditableCombobox("smFullRowPol", "Select POL", required = false)}
            </div>
            <div class="sm-tree-full-row-col sm-tree-full-row-col--company">
                ${createEditableCombobox("smFullRowCompany", "Select Rixo Company", required = true)}
            </div>
            <div class="sm-tree-full-row-col sm-tree-full-row-col--vtype">
                ${createEditableCombobox("smFullRowVehicleType", "Select Vehicle Type", required = false)}
            </div>
            <div class="sm-tree-full-row-col sm-tree-full-row-col--price">
                <input id="smFullRowPrice" class="rixo-tree-leaf-inline-price money-input sm-full-row-price-input" type="text" value="" placeholder="0" inputmode="decimal">
            </div>
        </div>
        <div class="sm-tree-full-row-actions">
            <button type="button" class="sm-full-row-add-cancel">Cancel</button>
            <button type="button" class="sm-full-row-add-save rixo-tree-btn rixo-tree-btn--add">Save</button>
        </div>
    </div>
""".trimIndent()

private fun smCardWrapperClass(level: String): String = when (level) {
    "supplier" -> "rixo-tree-card-wrapper--company"
    "venue" -> "rixo-tree-card-wrapper--venue"
    "stock" -> "rixo-tree-card-wrapper--stock"
    "pol" -> "rixo-tree-card-wrapper--pol"
    else -> "rixo-tree-card-wrapper--field"
}

private fun smCardLevelClass(level: String): String = when (level) {
    "supplier" -> " rixo-tree-card--company"
    "venue" -> " rixo-tree-card--auction"
    else -> " rixo-tree-card--stock"
}

private fun smBuildCardHtml(
    level: String,
    label: String,
    open: Boolean,
    pathSupplier: String,
    pathVenue: String = "",
    pathStock: String = "",
    pathPol: String = "",
): String {
    val selected = when (level) {
        "supplier" -> smSelectedSupplier == label
        "venue" -> smSelectedVenue == label
        "stock" -> smSelectedStock == label
        "pol" -> smSelectedPol == label
        "rixo_company" -> smSelectedCompany == label
        else -> false
    }
    val selectedClass = if (selected) " rixo-tree-card--selected" else ""
    val levelClass = smCardLevelClass(level)
    val wrapperClass = smCardWrapperClass(level)
    val ariaExpanded = if (open) "true" else "false"
    val useCardInline = smCardInlineEditLevel == level &&
        smCardInlineEditTargetMatches(level, label, pathSupplier, pathVenue, pathStock, pathPol)
    if (useCardInline) {
        val comboboxId = smCardInlineEditId(level)
        val placeholder = when (level) {
            "supplier" -> "Enter Supplier Name"
            "venue" -> "Enter Venue ID"
            "stock" -> "Select Stock Location"
            "pol" -> "Select POL"
            else -> "Select Rixo Company"
        }
        val comboboxHtml = if (level == "supplier" || level == "venue") {
            createPlainTextInput(comboboxId, placeholder, required = true, initialValue = smCardInlineEditCurrentLabel)
        } else {
            createEditableCombobox(comboboxId, placeholder, required = true)
        }
        return """
            <div class="rixo-tree-card-wrapper $wrapperClass" data-card-level="$level"
                 data-path-supplier="${escapeHtml(pathSupplier)}"
                 data-path-venue="${escapeHtml(pathVenue)}"
                 data-path-stock="${escapeHtml(pathStock)}"
                 data-path-pol="${escapeHtml(pathPol)}">
                <div class="rixo-tree-card$levelClass$selectedClass rixo-tree-card--inline-editing rixo-tree-card--inline-editing-with-actions" data-level="$level" data-value="${escapeHtml(label)}" aria-expanded="$ariaExpanded">
                    <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                    <div class="rixo-tree-card-combobox-wrap">$comboboxHtml</div>
                    <div class="rixo-tree-card-inline-actions">
                        <button type="button" class="rixo-tree-card-inline-cancel">Cancel</button>
                        <button type="button" class="rixo-tree-card-inline-save">Save</button>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }
    return """
        <div class="rixo-tree-card-wrapper $wrapperClass" data-card-level="$level"
             data-path-supplier="${escapeHtml(pathSupplier)}"
             data-path-venue="${escapeHtml(pathVenue)}"
             data-path-stock="${escapeHtml(pathStock)}"
             data-path-pol="${escapeHtml(pathPol)}">
            <button type="button" class="rixo-tree-card$levelClass$selectedClass" data-level="$level" data-value="${escapeHtml(label)}" aria-expanded="$ariaExpanded" title="${escapeHtml(label)}">
                <span class="rixo-tree-exp-indicator" aria-hidden="true"></span>
                <span class="rixo-tree-label">${escapeHtml(label)}</span>
            </button>
            <div class="rixo-tree-card-menu-wrap">
                <button type="button" class="rixo-tree-card-menu-btn" aria-label="More actions" aria-haspopup="true">&#8942;</button>
                <div class="rixo-tree-card-menu-panel" role="menu">
                    <button type="button" class="rixo-tree-card-menu-item" data-menu-action="edit" role="menuitem">Edit</button>
                    <button type="button" class="rixo-tree-card-menu-item rixo-tree-card-menu-item--danger" data-menu-action="delete" role="menuitem">Delete branch</button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun smBuildLeafRowHtml(leaf: SmLeafRow, baseRow: SupplierMapTreeRowLite, fieldSeq: Int): String {
    val isEditing = smLeafInlineEditMappingId == leaf.id &&
        smLeafInlineEditLineType.equals(leaf.type, ignoreCase = true)
    val inlineClass = if (isEditing) " rixo-tree-leaf-row--inline-editing" else ""
    val selectedClass = if (smSelectedMappingId == leaf.id) " rixo-tree-leaf-row--selected" else ""
    val typeDisplay = leaf.type.ifBlank { "—" }
    val normalizedPrice = Regex("""-?\d[\d,]*(?:\.\d+)?""")
        .find(baseRow.price.orEmpty())?.value?.replace(",", "") ?: ""
    val prefix = "smLeafInline_${leaf.id}_$fieldSeq"
    val pencilSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a.996.996 0 0 0 0-1.41l-2.34-2.34a.996.996 0 0 0-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>"""
    val trashSvg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path fill="currentColor" d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>"""
    return """
        <div class="rixo-tree-leaf-row rixo-tree-leaf-row--selectable$selectedClass$inlineClass" data-mapping-id="${leaf.id}" data-mapping-type="${escapeHtml(leaf.type)}" data-inline-field-id="$prefix">
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
                    <div class="rixo-tree-leaf-vtype-wrap">${createEditableCombobox("${prefix}_type", "Select Vehicle Type", required = false)}</div>
                    <input id="${prefix}_price" class="rixo-tree-leaf-inline-price money-input" type="text" value="${escapeHtml(normalizedPrice)}" placeholder="0" inputmode="decimal">
                    <button type="button" class="rixo-tree-leaf-update-btn" aria-label="Update mapping"><span class="rixo-tree-leaf-update-icon">&gt;&gt;</span></button>
                </div>
            </div>
        </div>
    """.trimIndent()
}

private fun buildSupplierMapTreeHtmlFromCache(): String {
    val list = smTreeRowsCache
    val fullRowHtml = if (smFullRowAddOpen) buildSmFullRowAddHtml() else ""
    if (list.isEmpty()) {
        return """
            <div class="rixo-tree supplier-map-tree">
                ${smTreeHeadersHtml()}
                $fullRowHtml
                <div class="rixo-tree-note" style="max-width:560px;">No supplier mappings yet. Use Add above to create your first mapping.</div>
            </div>
        """.trimIndent()
    }

    val suppliers = smSortedSuppliers(list)
    if (smSelectedSupplier !in suppliers) {
        smSelectedSupplier = null
        smSelectedVenue = null
        smSelectedStock = null
        smSelectedPol = null
        smSelectedCompany = null
    }

    val sb = StringBuilder()
    sb.append("""<div class="rixo-tree supplier-map-tree">""")
    sb.append(smTreeHeadersHtml())
    if (smFullRowAddOpen) {
        sb.append(buildSmFullRowAddHtml())
    }

    if (suppliers.isEmpty() && smSearchQuery.isNotBlank()) {
        sb.append("""<div class="rixo-tree-note" style="max-width:560px;">No suppliers match “${escapeHtml(smSearchQuery.trim())}”.</div>""")
        sb.append("</div>")
        return sb.toString()
    }

    for (supplier in suppliers) {
        val supplierRows = list.filter { smNormSupplier(it.supplier) == supplier }
        val supplierOpen = supplier == smSelectedSupplier
        sb.append("""<div class="rixo-tree-node">""")
        sb.append(smBuildCardHtml("supplier", supplier, supplierOpen, supplier))

        if (supplierOpen) {
            val venues = smVisibleVenues(supplierRows)
            sb.append("""<div class="rixo-tree-children">""")
            if (venues.isEmpty()) {
                if (smCardInlineAddMatchesVenueBranch(supplier)) {
                    sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("venue")}</div>""")
                }
                sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("venue", supplier, null, null, null, null)}</div>""")
            } else {
                for (venue in venues) {
                    val venueRows = supplierRows.filter { smRowVenueKey(it) == venue }
                    val venueOpen = venue == smSelectedVenue
                    sb.append("""<div class="rixo-tree-node">""")
                    sb.append(smBuildCardHtml("venue", venue, venueOpen, supplier, venue))

                    if (venueOpen) {
                        val stocks = smVisibleStocks(venueRows)
                        sb.append("""<div class="rixo-tree-children">""")
                        if (stocks.isEmpty()) {
                            if (smCardInlineAddMatchesStockBranch(supplier, venue)) {
                                sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("stock")}</div>""")
                            }
                            sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("stock", supplier, venue, null, null, null)}</div>""")
                        } else {
                            for (stock in stocks) {
                                val stockRows = venueRows.filter { smNormStock(it.stock) == stock }
                                val stockOpen = stock == smSelectedStock
                                sb.append("""<div class="rixo-tree-node">""")
                                sb.append(smBuildCardHtml("stock", stock, stockOpen, supplier, venue, stock))

                                if (stockOpen) {
                                    val pols = smVisiblePols(stockRows)
                                    sb.append("""<div class="rixo-tree-children">""")
                                    if (pols.isEmpty()) {
                                        if (smCardInlineAddMatchesPolBranch(supplier, venue, stock)) {
                                            sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("pol")}</div>""")
                                        }
                                        sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("pol", supplier, venue, stock, null, null)}</div>""")
                                    } else {
                                        for (pol in pols) {
                                            val polRows = stockRows.filter { smRowPolKey(it) == pol }
                                            val polOpen = pol == smSelectedPol
                                            sb.append("""<div class="rixo-tree-node">""")
                                            sb.append(smBuildCardHtml("pol", pol, polOpen, supplier, venue, stock, pol))

                                            if (polOpen) {
                                                val companies = smVisibleCompanies(polRows)
                                                sb.append("""<div class="rixo-tree-children">""")
                                                if (companies.isEmpty()) {
                                                    if (smCardInlineAddMatchesCompanyBranch(supplier, venue, stock, pol)) {
                                                        sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("rixo_company")}</div>""")
                                                    }
                                                    sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("rixo_company", supplier, venue, stock, pol, null)}</div>""")
                                                } else {
                                                    for (company in companies) {
                                                        val companyRows = polRows.filter { smNormCompany(it.company) == company }
                                                        val companyOpen = company == smSelectedCompany
                                                        sb.append("""<div class="rixo-tree-node">""")
                                                        sb.append(smBuildCardHtml("rixo_company", company, companyOpen, supplier, venue, stock, pol))

                                                        if (companyOpen) {
                                                            val leaves = smBuildLeafRows(companyRows)
                                                            sb.append("""<div class="rixo-tree-children"><div class="rixo-tree-leaf-wrap"><div class="rixo-tree-leaf-grid">""")
                                                            var seq = 0
                                                            for (leaf in leaves) {
                                                                val base = companyRows.firstOrNull { it.id.toLongOrNull() == leaf.id }
                                                                if (base != null) sb.append(smBuildLeafRowHtml(leaf, base, seq++))
                                                            }
                                                            if (smCardInlineAddMatchesLeafBranch(supplier, venue, stock, pol, company)) {
                                                                sb.append(buildSmLeafInlineAddHtml())
                                                            }
                                                            sb.append(smTreeAddButtonHtml("leaf", supplier, venue, stock, pol, company))
                                                            sb.append("""</div></div></div>""")
                                                        }
                                                        sb.append("""</div>""")
                                                    }
                                                    if (smCardInlineAddMatchesCompanyBranch(supplier, venue, stock, pol)) {
                                                        sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("rixo_company")}</div>""")
                                                    }
                                                    sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("rixo_company", supplier, venue, stock, pol, null)}</div>""")
                                                }
                                                sb.append("""</div>""")
                                            }
                                            sb.append("""</div>""")
                                        }
                                        if (smCardInlineAddMatchesPolBranch(supplier, venue, stock)) {
                                            sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("pol")}</div>""")
                                        }
                                        sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("pol", supplier, venue, stock, null, null)}</div>""")
                                    }
                                    sb.append("""</div>""")
                                }
                                sb.append("""</div>""")
                            }
                            if (smCardInlineAddMatchesStockBranch(supplier, venue)) {
                                sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("stock")}</div>""")
                            }
                            sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("stock", supplier, venue, null, null, null)}</div>""")
                        }
                        sb.append("""</div>""")
                    }
                    sb.append("""</div>""")
                }
                if (smCardInlineAddMatchesVenueBranch(supplier)) {
                    sb.append("""<div class="rixo-tree-node">${buildSmCardInlineAddHtml("venue")}</div>""")
                }
                sb.append("""<div class="rixo-tree-col-footer">${smTreeAddButtonHtml("venue", supplier, null, null, null, null)}</div>""")
            }
            sb.append("""</div>""")
        }
        sb.append("""</div>""")
    }
    sb.append("""</div>""")
    return sb.toString()
}

private fun smCloseAllMenus(root: HTMLElement) {
    val list = root.querySelectorAll(".rixo-tree-card-menu-wrap.is-open")
    val len = list.asDynamic().length as Int
    var i = 0
    while (i < len) {
        (list.asDynamic()[i] as? HTMLElement)?.classList?.remove("is-open")
        i++
    }
}

private fun smDispatchUpdated() {
    js("""
        window.dispatchEvent(new CustomEvent('supplierMapUpdated'));
        window.dispatchEvent(new CustomEvent('rixoMappingUpdated'));
        try { localStorage.setItem('supplierMapUpdatedAt', String(Date.now())); } catch (e) {}
    """)
}

private fun smDeleteMappingRow(id: Long, onDone: () -> Unit) {
    window.fetch(apiUrl("rixo-mapping/$id"), js("""{ method:'DELETE', headers:{'Content-Type':'application/json'} }"""))
        .then { resp: dynamic ->
            resp.json().then { result: dynamic ->
                val p = js("{}")
                p.resp = resp
                p.result = result
                p
            }
        }
        .then { pair: dynamic ->
            if (pair.resp.ok && (pair.result.success as? Boolean == true)) {
                showMessage("Mapping deleted", "success")
                smDispatchUpdated()
                onDone()
            } else {
                showMessage(pair.result.message?.toString() ?: "Failed to delete", "error")
            }
        }
        .catch { err: dynamic ->
            Logger.error("Supplier map delete failed: ${err.toString()}")
            showMessage("Failed to delete mapping", "error")
        }
}

private fun smEnsureMasterOptions(callback: (Boolean) -> Unit) {
    if (smMasterOptionsReady) {
        callback(true)
        return
    }
    val requests = js("[]")
    requests.push(window.fetch(apiUrl("rixo-mapping/distinct-auction-names")))
    requests.push(window.fetch(apiUrl("master-menu/stock_location")))
    requests.push(window.fetch(apiUrl("master-menu/pol")))
    requests.push(window.fetch(apiUrl("rixo-mapping/distinct-rixo-companies")))
    requests.push(window.fetch(apiUrl("master-menu/type_of_vehicle")))
    js("Promise.all")(requests)
        .then { responses: dynamic ->
            val parsePromises = js("[]")
            for (i in 0 until 5) {
                val resp = responses[i]
                parsePromises.push(if (resp.ok as Boolean) resp.json() else js("Promise.resolve([])"))
            }
            js("Promise.all")(parsePromises)
        }
        .then { results: dynamic ->
            smMasterSuppliers = parseMasterListArray(results[0]).distinct().sortedBy { it.lowercase() }
            smMasterStocks = parseMasterListArray(results[1]).distinct().sortedBy { it.lowercase() }
            smMasterPols = parseMasterListArray(results[2]).distinct().sortedBy { it.lowercase() }
            smMasterCompanies = parseMasterListArray(results[3]).distinct().sortedBy { it.lowercase() }
            smMasterVehicleTypes = parseMasterListArray(results[4]).distinct().sortedBy { it.lowercase() }
            smMasterOptionsReady = true
            callback(true)
        }
        .catch { err: dynamic ->
            Logger.error("Supplier map master options: ${err.toString()}")
            showMessage("Failed to load master options", "error")
            callback(false)
        }
}

private fun smPopulateCombobox(selectId: String, values: List<String>, selected: String) {
    val select = document.getElementById(selectId) as? HTMLSelectElement ?: return
    while (select.options.length > 1) select.remove(1)
    for (v in values) {
        val opt = document.createElement("option") as HTMLOptionElement
        opt.value = v
        opt.text = v
        select.add(opt)
    }
    setEditableComboboxValue(selectId, selected)
    val sync = window.asDynamic().syncComboboxInput
    if (sync != null) sync(selectId)
    // Arrow-key nav for eligible tree comboboxes (no-op for non-registered ids).
    val wireKeyNav = window.asDynamic().wireComboboxKeyboardNav
    if (wireKeyNav != null) wireKeyNav(selectId)
}

private fun smRowsForBranch(
    level: String,
    supplier: String,
    venue: String,
    stock: String,
    pol: String,
    company: String = "",
): List<SupplierMapTreeRowLite> = when (level) {
    "supplier" -> smTreeRowsCache.filter { smNormSupplier(it.supplier) == smNormSupplier(supplier) }
    "venue" -> smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) && smRowVenueKey(it) == smNormVenue(venue)
    }
    "stock" -> smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) &&
            smRowVenueKey(it) == smNormVenue(venue) &&
            smNormStock(it.stock) == smNormStock(stock)
    }
    "pol" -> smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) &&
            smRowVenueKey(it) == smNormVenue(venue) &&
            smNormStock(it.stock) == smNormStock(stock) &&
            smRowPolKey(it) == smNormPol(pol)
    }
    "rixo_company" -> smTreeRowsCache.filter {
        smNormSupplier(it.supplier) == smNormSupplier(supplier) &&
            smRowVenueKey(it) == smNormVenue(venue) &&
            smNormStock(it.stock) == smNormStock(stock) &&
            smRowPolKey(it) == smNormPol(pol) &&
            smNormCompany(it.company) == smNormCompany(company)
    }
    else -> emptyList()
}

private fun smPutPayloadFromRow(
    row: SupplierMapTreeRowLite,
    newSupplier: String? = null,
    newVenue: String? = null,
    newStock: String? = null,
    newPol: String? = null,
    newCompany: String? = null,
    pathEditOnly: Boolean = false,
): dynamic {
    val p = js("{}")
    p.auctionName = (newSupplier ?: row.supplier).trim()
    p.venueId = (newVenue ?: row.venueId)?.trim()?.takeIf { it.isNotEmpty() }
    p.stockLocation = (newStock ?: row.stock).trim()
    p.pol = (newPol ?: row.pol)?.trim()?.takeIf { it.isNotEmpty() }
    p.rixoCompany = (newCompany ?: row.company).trim()
    if (!pathEditOnly) {
        p.supportedVehicleType = row.vType?.trim()?.takeIf { it.isNotEmpty() }
        p.rixoPrice = row.price?.trim()?.takeIf { it.isNotEmpty() }
    }
    return p
}

private fun runSmPutBatchSequential(
    rows: List<SupplierMapTreeRowLite>,
    payloadForRow: (SupplierMapTreeRowLite) -> dynamic,
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
        runSmPutBatchSequential(rest, payloadForRow, onDone, onFail)
        return
    }
    val payload = payloadForRow(row)
    val fetchOpts = js("{}")
    fetchOpts.method = "PUT"
    fetchOpts.headers = js("({ 'Content-Type': 'application/json' })")
    fetchOpts.body = JSON.stringify(payload)
    window.fetch(apiUrl("rixo-mapping/$id"), fetchOpts)
        .then { resp: dynamic ->
            resp.json().then { result: dynamic ->
                val pair = js("{}")
                pair.resp = resp
                pair.result = result
                pair
            }
        }
        .then { pair: dynamic ->
            if (pair.resp.ok && (pair.result.success as? Boolean == true)) {
                runSmPutBatchSequential(rest, payloadForRow, onDone, onFail)
            } else {
                onFail(pair.result.message?.toString() ?: "Failed to update")
            }
        }
        .catch { err: dynamic -> onFail(err.toString()) }
}

private fun postSmMappingBulkOneRow(
    insertMode: String?,
    supplier: String,
    venue: String,
    stock: String,
    pol: String,
    company: String,
    vtype: String,
    price: String,
    onSuccess: () -> Unit,
) {
    if (smRejectIfSemicolon(supplier, venue, stock, pol, company, vtype, price)) return
    val mode = insertMode ?: "FULL"
    val obj: dynamic = js("{}")
    when (mode) {
        "SUPPLIER" -> {
            if (supplier.isBlank()) { showMessage("Supplier name is required", "error"); return }
            obj.insertMode = "SUPPLIER"
            obj.auctionName = supplier.trim()
            obj.rixoCompany = "-"
            obj.stockLocation = "-"
        }
        "VENUE" -> {
            if (supplier.isBlank() || venue.isBlank()) { showMessage("Supplier and venue are required", "error"); return }
            obj.insertMode = "VENUE"
            obj.auctionName = supplier.trim()
            obj.venueId = venue.trim()
            obj.rixoCompany = "-"
            obj.stockLocation = "-"
        }
        "STOCK" -> {
            if (supplier.isBlank() || stock.isBlank()) { showMessage("Supplier and stock location are required", "error"); return }
            obj.insertMode = "STOCK"
            obj.auctionName = supplier.trim()
            obj.venueId = venue.trim().takeIf { it.isNotEmpty() && it != SM_PLACEHOLDER_VENUE }
            obj.stockLocation = stock.trim()
            obj.rixoCompany = "-"
        }
        "POL" -> {
            if (supplier.isBlank() || stock.isBlank() || pol.isBlank() || pol == SM_PLACEHOLDER_POL) {
                showMessage("POL is required", "error"); return
            }
            obj.insertMode = "POL"
            obj.auctionName = supplier.trim()
            obj.venueId = venue.trim().takeIf { it.isNotEmpty() && it != SM_PLACEHOLDER_VENUE }
            obj.stockLocation = stock.trim()
            obj.pol = pol.trim()
            obj.rixoCompany = "-"
        }
        "RIXO_COMPANY" -> {
            if (supplier.isBlank() || stock.isBlank() || company.isBlank()) {
                showMessage("Rixo company is required", "error"); return
            }
            obj.insertMode = "RIXO_COMPANY"
            obj.auctionName = supplier.trim()
            obj.venueId = venue.trim().takeIf { it.isNotEmpty() && it != SM_PLACEHOLDER_VENUE }
            obj.stockLocation = stock.trim()
            obj.pol = pol.trim().takeIf { it.isNotEmpty() && it != SM_PLACEHOLDER_POL }
            obj.rixoCompany = company.trim()
        }
        else -> {
            if (supplier.isBlank() || stock.isBlank() || company.isBlank()) {
                showMessage("Complete the path (supplier, stock location, and Rixo company)", "error"); return
            }
            if (vtype.isNotBlank() && !smListContains(smMasterVehicleTypes, vtype)) {
                showMessage("Please select a vehicle type from the list", "error"); return
            }
            if (price.isNotBlank() && smParseMoney(price) == null) {
                showMessage("Rixo price must be numeric", "error"); return
            }
            obj.insertMode = "FULL"
            obj.auctionName = supplier.trim()
            obj.venueId = venue.trim().takeIf { it.isNotEmpty() && it != SM_PLACEHOLDER_VENUE }
            obj.stockLocation = stock.trim()
            obj.pol = pol.trim().takeIf { it.isNotEmpty() && it != SM_PLACEHOLDER_POL }
            obj.rixoCompany = company.trim()
            obj.supportedVehicleType = vtype.trim().takeIf { it.isNotEmpty() }
            obj.rixoPrice = if (price.isBlank()) price else smNormalizePriceForDb(price)
        }
    }
    val mergeId = when (mode) {
        "VENUE" -> smMergeRowIdForVenue(supplier)
        "STOCK" -> smMergeRowIdForStock(supplier, venue)
        "POL" -> smMergeRowIdForPol(supplier, venue, stock)
        "RIXO_COMPANY" -> smMergeRowIdForCompany(supplier, venue, stock, pol)
        "FULL" -> smMergeRowIdForLeaf(supplier, venue, stock, pol, company)
        else -> null
    }
    if (mergeId != null) obj.id = mergeId.toDouble()
    val payload = js("{}")
    payload.rows = arrayOf(obj)
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
            if (pair.resp.ok && (pair.result.success as? Boolean == true)) {
                smDispatchUpdated()
                onSuccess()
                showMessage(pair.result.message?.toString() ?: "Mapping added", "success")
            } else {
                showMessage(pair.result.message?.toString() ?: "Failed to add mapping", "error")
            }
        }
        .catch { err: dynamic ->
            Logger.error("Supplier map add failed: ${err.toString()}")
            showMessage("Failed to add mapping", "error")
        }
}

private fun wireSmInlineAddComboboxes() {
    when (smCardInlineAddLevel) {
        "supplier" -> {
            smPopulateCombobox("smCardInlineAddSupplier", smMasterSuppliers, "")
        }
        "venue" -> {
            setEditableComboboxValue("smCardInlineAddVenue", "")
        }
        "stock" -> smPopulateCombobox("smCardInlineAddStock", smMasterStocks, "")
        "pol" -> smPopulateCombobox("smCardInlineAddPol", smMasterPols, "")
        "rixo_company" -> smPopulateCombobox("smCardInlineAddCompany", smMasterCompanies, "")
        "leaf" -> {
            smPopulateCombobox("smCardInlineAddLeafType", smMasterVehicleTypes, "")
            (document.getElementById("smCardInlineAddLeafPrice") as? HTMLInputElement)?.value = ""
        }
        else -> Unit
    }
}

private fun wireSmCardInlineCombobox() {
    val level = smCardInlineEditLevel ?: return
    val id = smCardInlineEditId(level)
    when (level) {
        "supplier" -> setEditableComboboxValue(id, smCardInlineEditCurrentLabel)
        "venue" -> setEditableComboboxValue(id, smCardInlineEditCurrentLabel)
        "stock" -> smPopulateCombobox(id, smMasterStocks, smCardInlineEditCurrentLabel)
        "pol" -> smPopulateCombobox(id, smMasterPols, smCardInlineEditCurrentLabel)
        "rixo_company" -> smPopulateCombobox(id, smMasterCompanies, smCardInlineEditCurrentLabel)
        else -> Unit
    }
}

private fun wireSmFullRowAddComboboxes() {
    setEditableComboboxValue("smFullRowSupplier", "")
    setEditableComboboxValue("smFullRowVenue", "")
    smPopulateCombobox("smFullRowStock", smMasterStocks, "")
    smPopulateCombobox("smFullRowPol", smMasterPols, "")
    smPopulateCombobox("smFullRowCompany", smMasterCompanies, "")
    smPopulateCombobox("smFullRowVehicleType", smMasterVehicleTypes, "")
    (document.getElementById("smFullRowPrice") as? HTMLInputElement)?.value = ""
}

private fun startSmFullRowAdd(root: HTMLElement) {
    smLeafInlineEditMappingId = null
    smLeafInlineEditLineType = ""
    clearSmCardInlineEdit()
    clearSmCardInlineAdd()
    smFullRowAddOpen = true
    smEnsureMasterOptions { ok ->
        if (!ok) {
            clearSmFullRowAdd()
            return@smEnsureMasterOptions
        }
        root.innerHTML = buildSupplierMapTreeHtmlFromCache()
        bindSupplierMapTreeClicks(root)
        window.setTimeout({ wireSmFullRowAddComboboxes() }, 0)
    }
}

private fun executeSmFullRowAddSave() {
    val supplier = getEditableComboboxValue("smFullRowSupplier").trim()
    val venue = getEditableComboboxValue("smFullRowVenue").trim()
    val stock = getEditableComboboxValue("smFullRowStock").trim()
    val pol = getEditableComboboxValue("smFullRowPol").trim()
    val company = getEditableComboboxValue("smFullRowCompany").trim()
    val vtype = getEditableComboboxValue("smFullRowVehicleType").trim()
    val price = (document.getElementById("smFullRowPrice") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (supplier.isEmpty()) {
        showMessage("Supplier name is required", "error"); return
    }
    if (stock.isEmpty() || !smListContains(smMasterStocks, stock)) {
        showMessage("Please select a stock location from the list", "error"); return
    }
    if (company.isEmpty() || !smListContains(smMasterCompanies, company)) {
        showMessage("Please select a Rixo company from the list", "error"); return
    }
    if (vtype.isNotBlank() && !smListContains(smMasterVehicleTypes, vtype)) {
        showMessage("Please select a vehicle type from the list", "error"); return
    }
    if (price.isNotBlank() && smParseMoney(price) == null) {
        showMessage("Rixo price must be numeric", "error"); return
    }
    postSmMappingBulkOneRow(null, supplier, venue, stock, pol, company, vtype, price) {
        clearSmFullRowAdd()
        smSelectedSupplier = smNormSupplier(supplier)
        smSelectedVenue = venue.takeIf { it.isNotEmpty() }?.let { smNormVenue(it) }
        smSelectedStock = smNormStock(stock)
        smSelectedPol = pol.takeIf { it.isNotEmpty() }?.let { smNormPol(it) }
        smSelectedCompany = smNormCompany(company)
        refreshSupplierMapTreeData()
    }
}

private fun bindSmTopAddButton() {
    val btn = document.getElementById("smTopAddBtn") as? HTMLElement ?: return
    val prev = btn.asDynamic().__smTopAddHandler.unsafeCast<((Event) -> Unit)?>()
    if (prev != null) btn.removeEventListener("click", prev)
    val handler: (Event) -> Unit = handler@{ ev ->
        ev.preventDefault()
        val root = document.getElementById("supplierMapTreeRoot") as? HTMLElement ?: return@handler
        startSmFullRowAdd(root)
    }
    btn.asDynamic().__smTopAddHandler = handler
    btn.addEventListener("click", handler)
}

private fun toggleSmSupplierSort() {
    smSupplierSortOrder = when (smSupplierSortOrder) {
        "asc" -> "desc"
        "desc" -> "asc"
        else -> "asc"
    }
}

private fun smApplySearchAndRerender() {
    val root = document.getElementById("supplierMapTreeRoot") as? HTMLElement ?: return
    val clearBtn = document.getElementById("smSupplierSearchClearBtn") as? HTMLElement
    if (clearBtn != null) {
        clearBtn.style.visibility = if (smSearchQuery.isBlank()) "hidden" else "visible"
    }
    smRerenderTree(root)
}

private fun bindSmSearchToolbar() {
    val input = document.getElementById("smSupplierSearchInput") as? HTMLInputElement ?: return
    val clearBtn = document.getElementById("smSupplierSearchClearBtn") as? HTMLElement

    val prevInput = input.asDynamic().__smSearchInputHandler.unsafeCast<((Event) -> Unit)?>()
    if (prevInput != null) input.removeEventListener("input", prevInput)
    val inputHandler: (Event) -> Unit = {
        val q = input.value
        if (smSearchDebounceTimer != null) window.clearTimeout(smSearchDebounceTimer)
        smSearchDebounceTimer = window.setTimeout({
            smSearchQuery = q
            smApplySearchAndRerender()
        }, 180)
    }
    input.asDynamic().__smSearchInputHandler = inputHandler
    input.addEventListener("input", inputHandler)

    if (clearBtn != null) {
        val prevClear = clearBtn.asDynamic().__smSearchClearHandler.unsafeCast<((Event) -> Unit)?>()
        if (prevClear != null) clearBtn.removeEventListener("click", prevClear)
        val clearHandler: (Event) -> Unit = { ev ->
            ev.preventDefault()
            if (smSearchDebounceTimer != null) window.clearTimeout(smSearchDebounceTimer)
            input.value = ""
            smSearchQuery = ""
            smApplySearchAndRerender()
            input.focus()
        }
        clearBtn.asDynamic().__smSearchClearHandler = clearHandler
        clearBtn.addEventListener("click", clearHandler)
    }
}

private fun startSmInlineAdd(
    level: String,
    pathSupplier: String,
    pathVenue: String,
    pathStock: String,
    pathPol: String,
    pathCompany: String,
    root: HTMLElement,
) {
    smLeafInlineEditMappingId = null
    smLeafInlineEditLineType = ""
    clearSmCardInlineEdit()
    clearSmFullRowAdd()
    when (level) {
        "venue" -> {
            if (pathSupplier.isEmpty()) { showMessage("Select a supplier first", "error"); return }
            smSelectedSupplier = smNormSupplier(pathSupplier)
        }
        "stock" -> {
            if (pathSupplier.isEmpty() || pathVenue.isEmpty()) { showMessage("Select supplier and venue first", "error"); return }
            smSelectedSupplier = smNormSupplier(pathSupplier)
            smSelectedVenue = smNormVenue(pathVenue)
        }
        "pol" -> {
            if (pathSupplier.isEmpty() || pathStock.isEmpty()) { showMessage("Select supplier and stock first", "error"); return }
            smSelectedSupplier = smNormSupplier(pathSupplier)
            smSelectedVenue = smNormVenue(pathVenue)
            smSelectedStock = smNormStock(pathStock)
        }
        "rixo_company" -> {
            if (pathSupplier.isEmpty() || pathStock.isEmpty() || pathPol.isEmpty()) {
                showMessage("Select supplier, stock, and POL first", "error"); return
            }
            smSelectedSupplier = smNormSupplier(pathSupplier)
            smSelectedVenue = smNormVenue(pathVenue)
            smSelectedStock = smNormStock(pathStock)
            smSelectedPol = smNormPol(pathPol)
        }
        "leaf" -> {
            if (pathCompany.isEmpty()) { showMessage("Select a Rixo company branch first", "error"); return }
            smSelectedSupplier = smNormSupplier(pathSupplier)
            smSelectedVenue = smNormVenue(pathVenue)
            smSelectedStock = smNormStock(pathStock)
            smSelectedPol = smNormPol(pathPol)
            smSelectedCompany = smNormCompany(pathCompany)
        }
        else -> return
    }
    smCardInlineAddLevel = level
    smCardInlineAddSupplier = pathSupplier.trim()
    smCardInlineAddVenue = pathVenue.trim()
    smCardInlineAddStock = pathStock.trim()
    smCardInlineAddPol = pathPol.trim()
    smCardInlineAddCompany = pathCompany.trim()
    smEnsureMasterOptions { ok ->
        if (!ok) {
            clearSmCardInlineAdd()
            return@smEnsureMasterOptions
        }
        root.innerHTML = buildSupplierMapTreeHtmlFromCache()
        bindSupplierMapTreeClicks(root)
        window.setTimeout({ wireSmInlineAddComboboxes() }, 0)
    }
}

private fun executeSmCardInlineAddSave() {
    when (smCardInlineAddLevel) {
        "venue" -> {
            val supplier = smFirstNonBlank(smCardInlineAddSupplier, smSelectedSupplier)
            val venue = getEditableComboboxValue("smCardInlineAddVenue").trim()
            if (supplier.isEmpty() || venue.isEmpty()) { showMessage("Supplier and venue are required", "error"); return }
            postSmMappingBulkOneRow("VENUE", supplier, venue, "", "", "", "", "") {
                clearSmCardInlineAdd()
                refreshSupplierMapTreeData()
            }
        }
        "stock" -> {
            val supplier = smFirstNonBlank(smCardInlineAddSupplier, smSelectedSupplier)
            val venue = smFirstNonBlank(smCardInlineAddVenue, smSelectedVenue)
            val stock = getEditableComboboxValue("smCardInlineAddStock").trim()
            if (stock.isEmpty() || !smListContains(smMasterStocks, stock)) {
                showMessage("Please select a stock location from the list", "error"); return
            }
            postSmMappingBulkOneRow("STOCK", supplier, venue, stock, "", "", "", "") {
                clearSmCardInlineAdd()
                refreshSupplierMapTreeData()
            }
        }
        "pol" -> {
            val supplier = smFirstNonBlank(smCardInlineAddSupplier, smSelectedSupplier)
            val venue = smFirstNonBlank(smCardInlineAddVenue, smSelectedVenue)
            val stock = smFirstNonBlank(smCardInlineAddStock, smSelectedStock)
            val pol = getEditableComboboxValue("smCardInlineAddPol").trim()
            if (pol.isEmpty()) { showMessage("POL is required", "error"); return }
            postSmMappingBulkOneRow("POL", supplier, venue, stock, pol, "", "", "") {
                clearSmCardInlineAdd()
                refreshSupplierMapTreeData()
            }
        }
        "rixo_company" -> {
            val supplier = smFirstNonBlank(smCardInlineAddSupplier, smSelectedSupplier)
            val venue = smFirstNonBlank(smCardInlineAddVenue, smSelectedVenue)
            val stock = smFirstNonBlank(smCardInlineAddStock, smSelectedStock)
            val pol = smFirstNonBlank(smCardInlineAddPol, smSelectedPol)
            val company = getEditableComboboxValue("smCardInlineAddCompany").trim()
            if (company.isEmpty() || !smListContains(smMasterCompanies, company)) {
                showMessage("Please select a Rixo company from the list", "error"); return
            }
            postSmMappingBulkOneRow("RIXO_COMPANY", supplier, venue, stock, pol, company, "", "") {
                clearSmCardInlineAdd()
                refreshSupplierMapTreeData()
            }
        }
        else -> Unit
    }
}

private fun executeSmLeafInlineAddSave() {
    if (smCardInlineAddLevel != "leaf") return
    val supplier = smFirstNonBlank(smCardInlineAddSupplier, smSelectedSupplier)
    val venue = smFirstNonBlank(smCardInlineAddVenue, smSelectedVenue)
    val stock = smFirstNonBlank(smCardInlineAddStock, smSelectedStock)
    val pol = smFirstNonBlank(smCardInlineAddPol, smSelectedPol)
    val company = smFirstNonBlank(smCardInlineAddCompany, smSelectedCompany)
    val vtype = getEditableComboboxValue("smCardInlineAddLeafType").trim()
    val price = (document.getElementById("smCardInlineAddLeafPrice") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (vtype.isNotBlank() && !smListContains(smMasterVehicleTypes, vtype)) {
        showMessage("Please select a vehicle type from the list", "error"); return
    }
    if (price.isNotBlank() && smParseMoney(price) == null) {
        showMessage("Rixo price must be numeric", "error"); return
    }
    postSmMappingBulkOneRow(null, supplier, venue, stock, pol, company, vtype, price) {
        clearSmCardInlineAdd()
        refreshSupplierMapTreeData()
    }
}

private fun cancelSmCardInlineEdit(root: HTMLElement) {
    clearSmCardInlineEdit()
    root.innerHTML = buildSupplierMapTreeHtmlFromCache()
    bindSupplierMapTreeClicks(root)
}

private fun executeSmCardInlineSave(root: HTMLElement) {
    val level = smCardInlineEditLevel ?: return
    val pathSupplier = smCardInlineEditSupplier
    val pathVenue = smCardInlineEditVenue
    val pathStock = smCardInlineEditStock
    val pathPol = smCardInlineEditPol
    val currentLabel = smCardInlineEditCurrentLabel
    val comboboxId = smCardInlineEditId(level)
    val newVal = getEditableComboboxValue(comboboxId).trim()
    if (newVal.isEmpty()) { showMessage("Value is required", "error"); return }
    if (smRejectIfSemicolon(newVal)) return
    if (newVal.equals(currentLabel, ignoreCase = true)) {
        cancelSmCardInlineEdit(root)
        return
    }
    val listOk = when (level) {
        "supplier", "venue", "pol" -> true
        "stock" -> smListContains(smMasterStocks, newVal)
        "rixo_company" -> smListContains(smMasterCompanies, newVal)
        else -> false
    }
    if (!listOk) { showMessage("Please select a value from the list", "error"); return }
    val rows = smRowsForBranch(level, pathSupplier, pathVenue, pathStock, pathPol, currentLabel).distinctBy { it.id }
    if (rows.isEmpty()) { showMessage("No rows to update", "error"); return }
    val payloadBuilder: (SupplierMapTreeRowLite) -> dynamic = { row ->
        when (level) {
            "supplier" -> smPutPayloadFromRow(row, newSupplier = newVal, pathEditOnly = true)
            "venue" -> smPutPayloadFromRow(row, newVenue = newVal, pathEditOnly = true)
            "stock" -> smPutPayloadFromRow(row, newStock = newVal, pathEditOnly = true)
            "pol" -> smPutPayloadFromRow(row, newPol = newVal, pathEditOnly = true)
            "rixo_company" -> smPutPayloadFromRow(row, newCompany = newVal, pathEditOnly = true)
            else -> smPutPayloadFromRow(row)
        }
    }
    runSmPutBatchSequential(rows, payloadBuilder,
        onDone = {
            clearSmCardInlineEdit()
            when (level) {
                "supplier" -> if (smSelectedSupplier == smNormSupplier(pathSupplier)) smSelectedSupplier = smNormSupplier(newVal)
                "venue" -> if (smSelectedVenue == smNormVenue(pathVenue)) smSelectedVenue = smNormVenue(newVal)
                "stock" -> if (smSelectedStock == smNormStock(pathStock)) smSelectedStock = smNormStock(newVal)
                "pol" -> if (smSelectedPol == smNormPol(pathPol)) smSelectedPol = smNormPol(newVal)
                "rixo_company" -> if (smSelectedCompany == smNormCompany(currentLabel)) smSelectedCompany = smNormCompany(newVal)
            }
            smDispatchUpdated()
            refreshSupplierMapTreeData()
            showMessage("Updated", "success")
        },
        onFail = { msg -> showMessage(msg, "error") },
    )
}

private fun smRerenderTree(root: HTMLElement) {
    root.innerHTML = buildSupplierMapTreeHtmlFromCache()
    bindSupplierMapTreeClicks(root)
    if (smFullRowAddOpen) {
        window.setTimeout({ wireSmFullRowAddComboboxes() }, 0)
    }
}

private fun bindSupplierMapTreeClicks(root: HTMLElement) {
    val prev = root.asDynamic().__smTreeClickHandler.unsafeCast<((Event) -> Unit)?>()
    if (prev != null) root.removeEventListener("click", prev)

    val handler: (Event) -> Unit = click@{ ev ->
        val target = ev.target.asDynamic() as? Element ?: return@click

        val sortBtn = target.closest("[data-sm-sort='supplier']") as? HTMLElement
        if (sortBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            toggleSmSupplierSort()
            smRerenderTree(root)
            return@click
        }

        if (target.closest(".rixo-tree-card-menu-wrap") == null) smCloseAllMenus(root)

        val addBtn = target.closest(".rixo-tree-add-btn") as? HTMLElement
        if (addBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val level = addBtn.getAttribute("data-add-level").orEmpty()
            if (level !in setOf("venue", "stock", "pol", "rixo_company", "leaf")) return@click
            clearSmFullRowAdd()
            startSmInlineAdd(
                level,
                addBtn.getAttribute("data-supplier")?.trim().orEmpty(),
                addBtn.getAttribute("data-venue")?.trim().orEmpty(),
                addBtn.getAttribute("data-stock")?.trim().orEmpty(),
                addBtn.getAttribute("data-pol")?.trim().orEmpty(),
                addBtn.getAttribute("data-company")?.trim().orEmpty(),
                root,
            )
            return@click
        }

        val menuItem = target.closest(".rixo-tree-card-menu-item") as? HTMLElement
        if (menuItem != null) {
            ev.preventDefault()
            ev.stopPropagation()
            smCloseAllMenus(root)
            val action = menuItem.getAttribute("data-menu-action").orEmpty()
            val wrap = menuItem.closest(".rixo-tree-card-wrapper") as? HTMLElement ?: return@click
            val level = wrap.getAttribute("data-card-level").orEmpty()
            if (level !in setOf("supplier", "venue", "stock", "pol", "rixo_company")) return@click
            val supplier = wrap.getAttribute("data-path-supplier").orEmpty()
            val venue = wrap.getAttribute("data-path-venue").orEmpty()
            val stock = wrap.getAttribute("data-path-stock").orEmpty()
            val pol = wrap.getAttribute("data-path-pol").orEmpty()
            val label = wrap.querySelector(".rixo-tree-card")?.getAttribute("data-value").orEmpty()
            when (action) {
                "edit" -> {
                    smLeafInlineEditMappingId = null
                    smLeafInlineEditLineType = ""
                    clearSmCardInlineAdd()
                    clearSmFullRowAdd()
                    smCardInlineEditLevel = level
                    smCardInlineEditSupplier = supplier
                    smCardInlineEditVenue = venue
                    smCardInlineEditStock = stock
                    smCardInlineEditPol = pol
                    smCardInlineEditCurrentLabel = label
                    smEnsureMasterOptions { ok ->
                        if (!ok) {
                            clearSmCardInlineEdit()
                            return@smEnsureMasterOptions
                        }
                        root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                        bindSupplierMapTreeClicks(root)
                        window.setTimeout({ wireSmCardInlineCombobox() }, 0)
                    }
                }
                "delete" -> {
                    val rowsToDelete = smTreeRowsCache.filter { row ->
                        smNormSupplier(row.supplier) == smNormSupplier(supplier) &&
                            when (level) {
                                "supplier" -> true
                                "venue" -> smRowVenueKey(row) == label
                                "stock" -> smRowVenueKey(row) == smNormVenue(venue) && smNormStock(row.stock) == label
                                "pol" -> smRowVenueKey(row) == smNormVenue(venue) &&
                                    smNormStock(row.stock) == smNormStock(stock) && smRowPolKey(row) == label
                                "rixo_company" -> smRowVenueKey(row) == smNormVenue(venue) &&
                                    smNormStock(row.stock) == smNormStock(stock) &&
                                    smRowPolKey(row) == smNormPol(pol) && smNormCompany(row.company) == label
                                else -> false
                            }
                    }.mapNotNull { it.id.toLongOrNull() }.distinct()
                    if (rowsToDelete.isEmpty()) return@click
                    val pathBits = listOfNotNull(
                        supplier.takeIf { it.isNotBlank() }?.let { "Supplier: ${escapeHtml(it)}" },
                        when (level) {
                            "venue" -> "Venue: ${escapeHtml(label)}"
                            "stock" -> "Stock: ${escapeHtml(label)}"
                            "pol" -> "POL: ${escapeHtml(label)}"
                            "rixo_company" -> "Rixo company: ${escapeHtml(label)}"
                            else -> null
                        },
                    ).joinToString(" · ")
                    val levelNote = when (level) {
                        "supplier" ->
                            "<br><br><b>High impact:</b> Deleting this supplier branch removes all nested venues, stocks, POLs, and prices for this supplier."
                        "rixo_company" ->
                            "<br><br>This removes the Rixo company path under this supplier."
                        else -> ""
                    }
                    showRixoMappingDeleteConfirm(
                        title = if (level == "supplier") "Delete entire supplier branch?" else "Delete branch?",
                        messageHtml =
                            (if (pathBits.isNotEmpty()) "$pathBits<br><br>" else "") +
                                "<b>${rowsToDelete.size}</b> mapping row(s) will be permanently deleted.<br><br>" +
                                "These rows are shared with <b>Rixo Price Map</b>. Deleting here also removes them there." +
                                levelNote +
                                "<br><br>This cannot be undone. Are you sure?",
                        onConfirm = {
                            fun deleteNext(idx: Int) {
                                if (idx >= rowsToDelete.size) {
                                    refreshSupplierMapTreeData()
                                    return
                                }
                                smDeleteMappingRow(rowsToDelete[idx]) { deleteNext(idx + 1) }
                            }
                            deleteNext(0)
                        },
                    )
                }
            }
            return@click
        }

        val cardInlineAddSave = target.closest(".rixo-tree-card-inline-add-save") as? HTMLElement
        if (cardInlineAddSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeSmCardInlineAddSave()
            return@click
        }

        val cardInlineAddCancel = target.closest(".rixo-tree-card-inline-add-cancel") as? HTMLElement
        if (cardInlineAddCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearSmCardInlineAdd()
            smRerenderTree(root)
            return@click
        }

        val fullRowSave = target.closest(".sm-full-row-add-save") as? HTMLElement
        if (fullRowSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeSmFullRowAddSave()
            return@click
        }

        val fullRowCancel = target.closest(".sm-full-row-add-cancel") as? HTMLElement
        if (fullRowCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearSmFullRowAdd()
            smRerenderTree(root)
            return@click
        }

        if (target.closest(".sm-tree-full-row-add") != null) return@click

        val cardInlineSave = target.closest(".rixo-tree-card-inline-save") as? HTMLElement
        if (cardInlineSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeSmCardInlineSave(root)
            return@click
        }

        val cardInlineCancel = target.closest(".rixo-tree-card-inline-cancel") as? HTMLElement
        if (cardInlineCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            cancelSmCardInlineEdit(root)
            return@click
        }

        if (target.closest(".rixo-tree-card--inline-editing") != null) return@click

        val leafInlineAddSave = target.closest(".rixo-tree-leaf-inline-add-save") as? HTMLElement
        if (leafInlineAddSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeSmLeafInlineAddSave()
            return@click
        }

        val leafInlineAddCancel = target.closest(".rixo-tree-leaf-inline-add-cancel") as? HTMLElement
        if (leafInlineAddCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearSmCardInlineAdd()
            smRerenderTree(root)
            return@click
        }

        val menuBtn = target.closest(".rixo-tree-card-menu-btn") as? HTMLElement
        if (menuBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val wrap = menuBtn.closest(".rixo-tree-card-menu-wrap") as? HTMLElement ?: return@click
            val wasOpen = wrap.classList.contains("is-open")
            smCloseAllMenus(root)
            if (!wasOpen) wrap.classList.add("is-open")
            return@click
        }

        val leafDelete = target.closest(".rixo-tree-leaf-delete-btn") as? HTMLElement
        if (leafDelete != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val row = leafDelete.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@click
            val id = row.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@click
            val typeLabel = row.getAttribute("data-mapping-type")?.trim().orEmpty()
            val baseRow = smTreeRowsCache.firstOrNull { it.id.toLongOrNull() == id }
            val path = if (baseRow != null) {
                listOf(
                    baseRow.supplier,
                    baseRow.venueId ?: "",
                    baseRow.stock,
                    baseRow.pol ?: "",
                    baseRow.company,
                ).filter { it.isNotBlank() }.joinToString(" → ") { escapeHtml(it) }
            } else ""
            val typeDisp = typeLabel.ifBlank { "—" }
            showRixoMappingDeleteConfirm(
                title = "Delete mapping?",
                messageHtml =
                    "Vehicle type: <b>${escapeHtml(typeDisp)}</b>" +
                        (if (baseRow != null) " · Price: <b>${escapeHtml(smFormatPriceDisplay(baseRow.price))}</b>" else "") +
                        (if (path.isNotEmpty()) "<br>Path: $path" else "") +
                        "<br><br>This row is shared with <b>Rixo Price Map</b>. It will disappear there too." +
                        "<br><br>This cannot be undone. Are you sure?",
                onConfirm = { smDeleteMappingRow(id) { refreshSupplierMapTreeData() } },
            )
            return@click
        }

        val leafEdit = target.closest(".rixo-tree-leaf-edit-btn") as? HTMLElement
        if (leafEdit != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val row = leafEdit.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@click
            val id = row.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@click
            smLeafInlineEditMappingId = id
            smLeafInlineEditLineType = row.getAttribute("data-mapping-type")?.trim().orEmpty()
            smEnsureMasterOptions { ok ->
                if (!ok) {
                    smLeafInlineEditMappingId = null
                    smLeafInlineEditLineType = ""
                    return@smEnsureMasterOptions
                }
                root.innerHTML = buildSupplierMapTreeHtmlFromCache()
                bindSupplierMapTreeClicks(root)
                window.setTimeout({
                    val prefix = row.getAttribute("data-inline-field-id") ?: return@setTimeout
                    smPopulateCombobox("${prefix}_type", smMasterVehicleTypes, smLeafInlineEditLineType)
                }, 0)
            }
            return@click
        }

        val leafUpdate = target.closest(".rixo-tree-leaf-update-btn") as? HTMLElement
        if (leafUpdate != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val rowEl = leafUpdate.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@click
            val id = rowEl.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@click
            val baseRow = smTreeRowsCache.firstOrNull { it.id.toLongOrNull() == id } ?: return@click
            val prefix = rowEl.getAttribute("data-inline-field-id") ?: return@click
            val vtype = getEditableComboboxValue("${prefix}_type").trim()
            val price = (document.getElementById("${prefix}_price") as? HTMLInputElement)?.value?.trim().orEmpty()
            if (smRejectIfSemicolon(vtype, price)) return@click
            if (vtype.isNotBlank() && !smListContains(smMasterVehicleTypes, vtype)) {
                showMessage("Please select a vehicle type from the list", "error")
                return@click
            }
            if (price.isEmpty() || smParseMoney(price) == null) {
                showMessage("Rixo price must be numeric", "error")
                return@click
            }
            val payload = js("{}")
            payload.rixoCompany = baseRow.company
            payload.auctionName = baseRow.supplier
            payload.stockLocation = baseRow.stock
            payload.venueId = baseRow.venueId
            payload.pol = baseRow.pol
            // Always send string (incl. "") so PUT can clear vehicle type; omit/null would coalesce to old value.
            payload.supportedVehicleType = vtype
            payload.rixoPrice = smNormalizePriceForDb(price)
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
                    if (pair.resp.ok && (pair.result.success as? Boolean == true)) {
                        smLeafInlineEditMappingId = null
                        smLeafInlineEditLineType = ""
                        showMessage("Mapping updated", "success")
                        smDispatchUpdated()
                        refreshSupplierMapTreeData()
                    } else {
                        showMessage(pair.result.message?.toString() ?: "Failed to update", "error")
                    }
                }
                .catch { err: dynamic ->
                    Logger.error("Supplier map update failed: ${err.toString()}")
                    showMessage("Failed to update mapping", "error")
                }
            return@click
        }

        if (target.closest(".rixo-tree-leaf-edit") != null) return@click

        val leaf = target.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement
        if (leaf != null) {
            val id = leaf.getAttribute("data-mapping-id")?.toLongOrNull()
            smSelectedMappingId = if (smSelectedMappingId == id) null else id
            smRerenderTree(root)
            return@click
        }

        val card = target.closest(".rixo-tree-card") as? HTMLElement ?: return@click
        if (card.classList.contains("rixo-tree-card--inline-editing")) return@click
        val level = card.getAttribute("data-level") ?: return@click
        val value = card.getAttribute("data-value") ?: return@click
        smLeafInlineEditMappingId = null
        smLeafInlineEditLineType = ""
        clearSmCardInlineEdit()
        clearSmCardInlineAdd()
        when (level) {
            "supplier" -> {
                if (smSelectedSupplier == value) {
                    smSelectedSupplier = null
                    smSelectedVenue = null
                    smSelectedStock = null
                    smSelectedPol = null
                    smSelectedCompany = null
                } else {
                    smSelectedSupplier = value
                    smSelectedVenue = null
                    smSelectedStock = null
                    smSelectedPol = null
                    smSelectedCompany = null
                }
            }
            "venue" -> {
                if (smSelectedVenue == value) {
                    smSelectedVenue = null
                    smSelectedStock = null
                    smSelectedPol = null
                    smSelectedCompany = null
                } else {
                    smSelectedVenue = value
                    smSelectedStock = null
                    smSelectedPol = null
                    smSelectedCompany = null
                }
            }
            "stock" -> {
                if (smSelectedStock == value) {
                    smSelectedStock = null
                    smSelectedPol = null
                    smSelectedCompany = null
                } else {
                    smSelectedStock = value
                    smSelectedPol = null
                    smSelectedCompany = null
                }
            }
            "pol" -> {
                if (smSelectedPol == value) {
                    smSelectedPol = null
                    smSelectedCompany = null
                } else {
                    smSelectedPol = value
                    smSelectedCompany = null
                }
            }
            "rixo_company" -> {
                smSelectedCompany = if (smSelectedCompany == value) null else value
            }
            else -> return@click
        }
        smSelectedMappingId = null
        smRerenderTree(root)
    }

    root.asDynamic().__smTreeClickHandler = handler
    root.addEventListener("click", handler)
}

fun showSupplierMapTreePage() {
    val content = document.getElementById("content") ?: return
    content.innerHTML = """
        <div id="supplierMapTreePage" class="rixo-tree-page rixo-tree-page--supplier-map">
            <div class="rixo-tree-topbar">
                <h2 class="rixo-tree-title">Supplier Map</h2>
                <button type="button" id="smTopAddBtn" class="rixo-tree-btn rixo-tree-btn--add">+ Add</button>
            </div>
            ${smSearchToolbarHtml()}
            <div id="supplierMapTreeRoot" class="rixo-tree-root rixo-tree-root--supplier-map">
                <div class="rixo-tree-loading">Loading…</div>
            </div>
        </div>
    """.trimIndent()
    bindSmTopAddButton()
    bindSmSearchToolbar()
    loadSupplierMapTree()
}

fun loadSupplierMapTree() {
    val root = document.getElementById("supplierMapTreeRoot") as? HTMLElement ?: return
    window.fetch(apiUrl("rixo-mapping/all"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load supplier map')")
        }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message || 'Failed to load supplier map')")
            val data = result.data
            val arr = if (js("Array.isArray(data)") as Boolean) (data as Array<dynamic>).toList() else emptyList()
            smTreeRowsCache = parseSupplierMapTreeRows(arr)
            smSelectedSupplier = null
            smSelectedVenue = null
            smSelectedStock = null
            smSelectedPol = null
            smSelectedCompany = null
            smSelectedMappingId = null
            smLeafInlineEditMappingId = null
            smLeafInlineEditLineType = ""
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
        }
        .catch { err: dynamic ->
            Logger.error("Supplier map tree load: ${err.toString()}")
            root.innerHTML = """<div style="text-align:center;color:#b91c1c;padding:32px;">Failed to load supplier map. ${escapeHtml(err.message?.toString() ?: "")}</div>"""
        }
}

fun refreshSupplierMapTreeData() {
    val root = document.getElementById("supplierMapTreeRoot") as? HTMLElement ?: return
    window.fetch(apiUrl("rixo-mapping/all"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load supplier map')")
        }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message || 'Failed to load supplier map')")
            val data = result.data
            val arr = if (js("Array.isArray(data)") as Boolean) (data as Array<dynamic>).toList() else emptyList()
            smTreeRowsCache = parseSupplierMapTreeRows(arr)
            smLeafInlineEditMappingId = null
            smLeafInlineEditLineType = ""
            root.innerHTML = buildSupplierMapTreeHtmlFromCache()
            bindSupplierMapTreeClicks(root)
            if (smFullRowAddOpen) {
                window.setTimeout({ wireSmFullRowAddComboboxes() }, 0)
            }
        }
        .catch { err: dynamic ->
            Logger.error("Supplier map refresh: ${err.toString()}")
            showMessage("Failed to refresh supplier map", "error")
        }
}
