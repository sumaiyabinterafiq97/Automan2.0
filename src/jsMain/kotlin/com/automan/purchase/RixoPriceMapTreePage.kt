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

private data class RixoPriceMapTreeRowLite(
    val id: String,
    val company: String,
    val stock: String,
    val pol: String?,
    val vType: String?,
    val price: String?,
    val auctionName: String? = null,
    val venueId: String? = null,
)

private data class RpmLeafRow(val id: Long, val type: String, val priceDisplay: String)

private const val RPM_PLACEHOLDER_SUPPLIER = "(no supplier)"
private const val RPM_SEMICOLON_REJECT_MSG =
    "Use a single value only. Do not use \";\" to join multiple values. Add separate mappings instead."

private fun rpmNormalizeSemicolons(raw: String): String =
    raw.replace("\uFF1B", ";").replace("\uFE55", ";")

private fun rpmContainsSemicolon(raw: String?): Boolean {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return false
    return rpmNormalizeSemicolons(t).contains(';')
}

private fun rpmRejectIfSemicolon(vararg values: String?): Boolean {
    if (values.any { rpmContainsSemicolon(it) }) {
        showMessage(RPM_SEMICOLON_REJECT_MSG, "error")
        return true
    }
    return false
}

private var rpmTreeRowsCache: List<RixoPriceMapTreeRowLite> = emptyList()
private var rpmRootCompanies: List<String> = emptyList()
private var rpmLoadedCompanyKeys: MutableSet<String> = mutableSetOf()
private var rpmSelectedCompany: String? = null
private var rpmSelectedSupplier: String? = null
private var rpmSelectedStock: String? = null
private var rpmSelectedMappingId: Long? = null
private var rpmLeafInlineEditMappingId: Long? = null
private var rpmLeafInlineEditLineType: String = ""
private var rpmMasterVehicleTypes: List<String> = emptyList()
private var rpmMasterCompanies: List<String> = emptyList()
private var rpmMasterSuppliers: List<String> = emptyList()
private var rpmMasterStocks: List<String> = emptyList()
private var rpmMasterOptionsReady: Boolean = false

private var rpmCardInlineAddLevel: String? = null
private var rpmCardInlineAddCompany: String = ""
private var rpmCardInlineAddSupplier: String = ""
private var rpmCardInlineAddStock: String = ""

private var rpmCardInlineEditLevel: String? = null
private var rpmCardInlineEditCompany: String = ""
private var rpmCardInlineEditSupplier: String = ""
private var rpmCardInlineEditStock: String = ""
private var rpmCardInlineEditCurrentLabel: String = ""

private var rpmFullRowAddOpen: Boolean = false
private var rpmSearchQuery: String = ""
private var rpmCompanySortOrder: String? = null // null = newest-first; "asc" | "desc"
private var rpmSearchDebounceTimer: dynamic = null

private fun rpmSortedCompanies(list: List<RixoPriceMapTreeRowLite>): List<String> {
    if (rpmRootCompanies.isNotEmpty()) {
        val names = rpmFilterRealCompanies(rpmRootCompanies.map { rpmNormCompany(it) })
        val q = rpmSearchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) names else names.filter { it.lowercase().contains(q) }
        return when (rpmCompanySortOrder) {
            "asc" -> filtered.sortedBy { it.lowercase() }
            "desc" -> filtered.sortedByDescending { it.lowercase() }
            // Stable A–Z: do not re-rank by partially loaded branch row ids (keeps expand in place).
            else -> filtered.sortedBy { it.lowercase() }
        }
    }
    val grouped = list.groupBy { rpmNormCompany(it.company) }
        .filterKeys { !rpmIsBlankCompany(it) }
        .map { (name, rows) -> name to rows.maxOf { it.id.toLongOrNull() ?: 0L } }
    val q = rpmSearchQuery.trim().lowercase()
    val filtered = if (q.isEmpty()) grouped else grouped.filter { (name, _) -> name.lowercase().contains(q) }
    return when (rpmCompanySortOrder) {
        "asc" -> filtered.sortedBy { it.first.lowercase() }.map { it.first }
        "desc" -> filtered.sortedByDescending { it.first.lowercase() }.map { it.first }
        else -> filtered.sortedWith(
            compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first.lowercase() },
        ).map { it.first }
    }
}

private fun rpmSearchToolbarHtml(): String = """
    <div class="tree-map-search-toolbar" data-rpm-search-toolbar="1">
        <div class="tree-map-search-pill">
            <span class="tree-map-search-icon" aria-hidden="true">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M10.5 18a7.5 7.5 0 1 1 0-15 7.5 7.5 0 0 1 0 15Z" stroke="currentColor" stroke-width="2"/><path d="M16.5 16.5 21 21" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
            </span>
            <input type="text" id="rpmCompanySearchInput" role="searchbox" autocomplete="off" inputmode="search"
                placeholder="Type to search…" aria-label="Search Rixo company"
                value="${escapeHtml(rpmSearchQuery)}" />
            <button type="button" id="rpmCompanySearchClearBtn" class="tree-map-search-clear" title="Clear search"
                style="${if (rpmSearchQuery.isBlank()) "visibility:hidden;" else ""}">×</button>
        </div>
    </div>
""".trimIndent()

private fun rpmCompanySortTooltip(): String = when (rpmCompanySortOrder) {
    "asc" -> "Sorted A-Z (click to sort Z-A)"
    "desc" -> "Sorted Z-A (click to sort A-Z)"
    else -> "Sort Rixo companies A-Z"
}

private fun clearRpmFullRowAdd() {
    rpmFullRowAddOpen = false
}

private fun rpmDynStr(v: dynamic?): String {
    if (v == null || v == js("undefined")) return ""
    return v.toString().trim()
}

private fun rpmNormCompany(s: String) = s.trim().ifEmpty { "(no company)" }

private fun rpmNormSupplier(s: String?) =
    s?.trim()?.takeIf { it.isNotEmpty() } ?: RPM_PLACEHOLDER_SUPPLIER

private fun rpmNormStock(s: String) = s.trim().ifEmpty { "(no stock location)" }

private fun rpmIsBlankCompany(s: String): Boolean {
    val t = s.trim()
    return t.isEmpty() || t == "-" || t.equals("(no company)", ignoreCase = true)
}

/** Distinct company labels for roots/combobox — hide Supplier Map placeholders. */
private fun rpmFilterRealCompanies(names: List<String>): List<String> =
    names.map { it.trim() }.filter { !rpmIsBlankCompany(it) }.distinctBy { it.lowercase() }

private fun rpmIsBlankSupplier(s: String?): Boolean {
    val t = s?.trim().orEmpty()
    return t.isEmpty() || t == "-" || t.equals(RPM_PLACEHOLDER_SUPPLIER, ignoreCase = true)
}

private fun rpmSplitVehicleTypes(raw: String?): List<String> {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return emptyList()
    return t.split(";").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun rpmFormatPriceDisplay(raw: String?): String {
    val s = raw?.trim().orEmpty()
    if (s.isEmpty()) return "—"
    val digits = s.replace(Regex("[^0-9]"), "")
    if (digits.isEmpty()) return escapeHtml(s)
    val formattedInt = digits.replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")
    return "¥$formattedInt"
}

private fun rpmParseMoney(raw: String): Double? {
    val t = raw.trim()
    if (t.isEmpty()) return null
    return t.replace(",", "").toDoubleOrNull()
}

private fun rpmNormalizePriceForDb(raw: String): String {
    val n = rpmParseMoney(raw) ?: return raw.trim()
    return if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
}

private fun parseRixoPriceMapTreeRows(rows: List<dynamic>): List<RixoPriceMapTreeRowLite> =
    rows.map { r ->
        RixoPriceMapTreeRowLite(
            id = rpmDynStr(r.id),
            company = rpmDynStr(r.rixoCompany),
            stock = rpmDynStr(r.stockLocation),
            pol = rpmDynStr(r.pol).takeIf { it.isNotEmpty() },
            vType = rpmDynStr(r.supportedVehicleType).takeIf { it.isNotEmpty() },
            price = rpmDynStr(r.rixoPrice).takeIf { it.isNotEmpty() },
            auctionName = rpmDynStr(r.auctionName).takeIf { it.isNotEmpty() },
            venueId = rpmDynStr(r.venueId).takeIf { it.isNotEmpty() },
        )
    }.filter { !rpmIsBlankCompany(it.company) }

private fun rpmVisibleSuppliers(rows: List<RixoPriceMapTreeRowLite>): List<String> =
    rows.map { rpmRowSupplierKey(it) }
        .filter { it != RPM_PLACEHOLDER_SUPPLIER && it != "-" }
        .distinct()
        .sortedBy { it.lowercase() }

private fun rpmVisibleStocks(rows: List<RixoPriceMapTreeRowLite>): List<String> =
    rows.map { rpmNormStock(it.stock) }.filter { it != "(no stock location)" && it != "-" }.distinct()
        .sortedBy { it.lowercase() }

private fun rpmRowSupplierKey(row: RixoPriceMapTreeRowLite) = rpmNormSupplier(row.auctionName)

/**
 * Resolve venue for a supplier from loaded RPM cache.
 * Exactly one distinct non-blank → that venue; zero → null; two+ → null + warn.
 */
private fun rpmResolveUniqueVenueForSupplier(supplier: String, warnOnConflict: Boolean = true): String? {
    val key = supplier.trim()
    if (key.isEmpty() || key == RPM_PLACEHOLDER_SUPPLIER || key == "-") return null
    val venues = rpmTreeRowsCache
        .filter { rpmNormSupplier(it.auctionName).equals(rpmNormSupplier(key), ignoreCase = true) }
        .mapNotNull { it.venueId?.trim()?.takeIf { v -> v.isNotEmpty() } }
        .distinctBy { it.lowercase() }
    if (venues.size == 1) return venues[0]
    if (venues.size > 1 && warnOnConflict) {
        showMessage(
            "Supplier “${rpmNormSupplier(key)}” has multiple venue IDs (${venues.joinToString(", ")}). " +
                "Venue was left blank — fix conflicts in Supplier Map.",
            "warning",
        )
    }
    return null
}

private fun rpmApplyVenueToBulkPayload(obj: dynamic, supplier: String) {
    val venue = rpmResolveUniqueVenueForSupplier(supplier)
    if (venue != null) obj.venueId = venue
}

/**
 * Resolve POL for a stock from loaded RPM cache.
 * Exactly one distinct non-blank → that POL; zero → null; two+ → null + warn.
 */
private fun rpmResolveUniquePolForStock(stock: String, warnOnConflict: Boolean = true): String? {
    val key = stock.trim()
    if (key.isEmpty() || key == "(no stock location)" || key == "-") return null
    val pols = rpmTreeRowsCache
        .filter { rpmNormStock(it.stock).equals(rpmNormStock(key), ignoreCase = true) }
        .mapNotNull { it.pol?.trim()?.takeIf { p -> p.isNotEmpty() } }
        .distinctBy { it.lowercase() }
    if (pols.size == 1) return pols[0]
    if (pols.size > 1 && warnOnConflict) {
        showMessage(
            "Stock “${rpmNormStock(key)}” has multiple POLs (${pols.joinToString(", ")}). " +
                "POL was left blank — fix conflicts in Supplier Map.",
            "warning",
        )
    }
    return null
}

private fun rpmCachePolConflictCount(stock: String): Int {
    val key = stock.trim()
    if (key.isEmpty() || key == "(no stock location)" || key == "-") return 0
    return rpmTreeRowsCache
        .filter { rpmNormStock(it.stock).equals(rpmNormStock(key), ignoreCase = true) }
        .mapNotNull { it.pol?.trim()?.takeIf { p -> p.isNotEmpty() } }
        .distinctBy { it.lowercase() }
        .size
}

/**
 * Cache first; on miss call GET pol-by-stock (optional auction/supplier scope).
 * When [auctionName] is known, still call API even if company cache shows multi-POL globally.
 */
private fun rpmResolvePolForStockAsync(stock: String, auctionName: String? = null, onDone: (String?) -> Unit) {
    val key = stock.trim()
    if (key.isEmpty() || key == "(no stock location)" || key == "-") {
        onDone(null)
        return
    }
    val auction = auctionName?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
    // Company-scoped unique only when no supplier — auction scope needs the API.
    if (auction == null) {
        val fromCache = rpmResolveUniquePolForStock(key, warnOnConflict = true)
        if (fromCache != null) {
            onDone(fromCache)
            return
        }
        // Company cache already showed a multi-POL conflict — do not invent via stock-only API.
        if (rpmCachePolConflictCount(key) > 1) {
            onDone(null)
            return
        }
    }
    val encStock = js("encodeURIComponent")(key).unsafeCast<String>()
    val encAuction = if (auction != null) {
        "&auctionName=" + js("encodeURIComponent")(auction).unsafeCast<String>()
    } else {
        ""
    }
    window.fetch(apiUrl("rixo-mapping/pol-by-stock?stockLocation=$encStock$encAuction"))
        .then { resp: dynamic ->
            if (resp.ok as Boolean) resp.json() else js("Promise.resolve({ success:false, data:null })")
        }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            val raw = if (ok) result.data else null
            val pol = when {
                raw == null || raw == js("undefined") -> null
                else -> raw.toString().trim().takeIf { it.isNotEmpty() && it != "null" && it != "undefined" }
            }
            onDone(pol)
        }
        .catch { _: dynamic ->
            onDone(null)
        }
}

private fun rpmApplyPolToBulkPayload(obj: dynamic, stock: String) {
    // Prefer an existing payload pol; otherwise unique-from-cache; never invent on conflict.
    val existing = (obj.pol as? String)?.trim()?.takeIf { it.isNotEmpty() }
    if (existing != null) return
    val pol = rpmResolveUniquePolForStock(stock, warnOnConflict = false)
    if (pol != null) obj.pol = pol
}

private fun rpmPayloadNeedsStockPol(obj: dynamic): Boolean {
    val stock = (obj.stockLocation as? String)?.trim().orEmpty()
    if (stock.isEmpty() || stock == "-") return false
    val pol = (obj.pol as? String)?.trim()?.takeIf { it.isNotEmpty() }
    return pol == null
}

private fun rpmBuildLeafRows(polRows: List<RixoPriceMapTreeRowLite>): List<RpmLeafRow> {
    val out = mutableListOf<RpmLeafRow>()
    for (r in polRows) {
        val rowId = r.id.toLongOrNull() ?: continue
        val types = rpmSplitVehicleTypes(r.vType)
        val priceDisplay = rpmFormatPriceDisplay(r.price)
        if (types.isEmpty()) {
            if (r.price.isNullOrBlank()) continue
            out.add(RpmLeafRow(id = rowId, type = "", priceDisplay = priceDisplay))
        } else {
            for (type in types) {
                out.add(RpmLeafRow(id = rowId, type = type, priceDisplay = priceDisplay))
            }
        }
    }
    return out
}

private fun clearRpmCardInlineAdd() {
    rpmCardInlineAddLevel = null
    rpmCardInlineAddCompany = ""
    rpmCardInlineAddSupplier = ""
    rpmCardInlineAddStock = ""
}

private fun clearRpmCardInlineEdit() {
    rpmCardInlineEditLevel = null
    rpmCardInlineEditCompany = ""
    rpmCardInlineEditSupplier = ""
    rpmCardInlineEditStock = ""
    rpmCardInlineEditCurrentLabel = ""
}

private fun rpmListContains(list: List<String>, value: String): Boolean =
    list.any { it.equals(value.trim(), ignoreCase = true) }

private fun rpmFirstNonBlank(vararg candidates: String?): String {
    for (c in candidates) {
        val t = c?.trim().orEmpty()
        if (t.isNotEmpty()) return t
    }
    return ""
}

private fun rpmCardInlineAddId(level: String): String = when (level) {
    "rixo_company" -> "rpmCardInlineAddCompany"
    "supplier" -> "rpmCardInlineAddSupplier"
    "stock" -> "rpmCardInlineAddStock"
    else -> "rpmCardInlineAddCompany"
}

private fun rpmCardInlineEditId(level: String): String = when (level) {
    "rixo_company" -> "rpmCardInlineEditCompany"
    "supplier" -> "rpmCardInlineEditSupplier"
    "stock" -> "rpmCardInlineEditStock"
    else -> "rpmCardInlineEditCompany"
}

private fun rpmCardInlineAddMatchesCompanyBranch(): Boolean =
    rpmCardInlineAddLevel == "rixo_company"

private fun rpmCardInlineAddMatchesStockBranch(company: String): Boolean =
    rpmCardInlineAddLevel == "stock" &&
        rpmNormCompany(rpmCardInlineAddCompany) == rpmNormCompany(company)

private fun rpmCardInlineAddMatchesSupplierBranch(company: String, stock: String): Boolean =
    rpmCardInlineAddLevel == "supplier" &&
        rpmNormCompany(rpmCardInlineAddCompany) == rpmNormCompany(company) &&
        rpmNormStock(rpmCardInlineAddStock) == rpmNormStock(stock)

private fun rpmCardInlineAddMatchesLeafBranch(company: String, stock: String, supplier: String): Boolean =
    rpmCardInlineAddLevel == "leaf" &&
        rpmNormCompany(rpmCardInlineAddCompany) == rpmNormCompany(company) &&
        rpmNormStock(rpmCardInlineAddStock) == rpmNormStock(stock) &&
        rpmNormSupplier(rpmCardInlineAddSupplier) == rpmNormSupplier(supplier)

private fun rpmCardInlineEditTargetMatches(
    level: String,
    label: String,
    company: String,
    stock: String,
    supplier: String,
): Boolean {
    if (rpmCardInlineEditLevel != level) return false
    if (!label.equals(rpmCardInlineEditCurrentLabel, ignoreCase = true)) return false
    return when (level) {
        "rixo_company" -> rpmNormCompany(rpmCardInlineEditCompany) == rpmNormCompany(company)
        "stock" -> rpmNormCompany(rpmCardInlineEditCompany) == rpmNormCompany(company) &&
            rpmNormStock(rpmCardInlineEditStock) == rpmNormStock(stock)
        "supplier" -> rpmNormCompany(rpmCardInlineEditCompany) == rpmNormCompany(company) &&
            rpmNormStock(rpmCardInlineEditStock) == rpmNormStock(stock) &&
            rpmNormSupplier(rpmCardInlineEditSupplier) == rpmNormSupplier(supplier)
        else -> false
    }
}

/** Company skeleton: stock blank/`-`, no supplier — merge target for RPM_STOCK. */
private fun RixoPriceMapTreeRowLite.isRpmCompanySkeleton(): Boolean =
    rpmIsBlankSupplier(auctionName) &&
        (rpmNormStock(stock) == "(no stock location)" || stock == "-") &&
        vType.isNullOrBlank() && price.isNullOrBlank()

/** Stock skeleton: stock set, supplier blank — merge target for RPM_SUPPLIER. */
private fun RixoPriceMapTreeRowLite.isRpmStockSkeleton(): Boolean =
    rpmNormStock(stock) != "(no stock location)" && stock != "-" &&
        rpmIsBlankSupplier(auctionName) &&
        vType.isNullOrBlank() && price.isNullOrBlank()

/** Leaf skeleton: supplier+stock set, type/price blank — merge target for RPM_FULL. */
private fun RixoPriceMapTreeRowLite.isRpmLeafSkeleton(): Boolean =
    rpmNormStock(stock) != "(no stock location)" && stock != "-" &&
        !rpmIsBlankSupplier(auctionName) &&
        vType.isNullOrBlank() && price.isNullOrBlank()

private fun rpmMergeRowIdForStock(company: String): Long? {
    val rows = rpmTreeRowsCache.filter { rpmNormCompany(it.company) == rpmNormCompany(company) }
    if (rpmVisibleStocks(rows).isNotEmpty()) return null
    return rows.filter { it.isRpmCompanySkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun rpmMergeRowIdForSupplier(company: String, stock: String): Long? {
    val rows = rpmTreeRowsCache.filter {
        rpmNormCompany(it.company) == rpmNormCompany(company) &&
            rpmNormStock(it.stock) == rpmNormStock(stock)
    }
    if (rpmVisibleSuppliers(rows).isNotEmpty()) return null
    return rows.filter { it.isRpmStockSkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun rpmMergeRowIdForLeaf(company: String, stock: String, supplier: String): Long? {
    val rows = rpmTreeRowsCache.filter {
        rpmNormCompany(it.company) == rpmNormCompany(company) &&
            rpmNormStock(it.stock) == rpmNormStock(stock) &&
            rpmRowSupplierKey(it) == rpmNormSupplier(supplier)
    }
    return rows.filter { it.isRpmLeafSkeleton() }.singleOrNull()?.id?.toLongOrNull()
}

private fun rpmTreeAddButtonHtml(
    level: String,
    company: String?,
    stock: String?,
    supplier: String?,
): String {
    val wrapClass = if (level == "leaf") "rixo-tree-add-wrap rixo-tree-add-wrap--leaf" else "rixo-tree-add-wrap"
    return """<div class="$wrapClass"><button type="button" class="rixo-tree-add-btn" data-add-level="$level"
        data-company="${escapeHtml(company ?: "")}" data-stock="${escapeHtml(stock ?: "")}"
        data-supplier="${escapeHtml(supplier ?: "")}">+ Add</button></div>"""
}

private fun buildRpmCardInlineAddHtml(level: String): String {
    val comboboxId = rpmCardInlineAddId(level)
    val placeholder = when (level) {
        "rixo_company" -> "Enter Rixo Company"
        "supplier" -> "Select Supplier Name"
        "stock" -> "Select Stock Location"
        else -> ""
    }
    val fieldPrimary = if (level == "rixo_company") {
        """<div class="rixo-tree-inline-add-field-primary rixo-tree-inline-add-plain">${createPlainTextInput(comboboxId, placeholder, required = true)}</div>"""
    } else {
        """<div class="rixo-tree-inline-add-field-primary rixo-tree-card-combobox-wrap">${createEditableCombobox(comboboxId, placeholder, required = true)}</div>"""
    }
    return """
        <div class="rixo-tree-inline-add-outer rixo-tree-card--inline-editing" data-level="$level" data-rpm-inline-add="1">
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

private fun buildRpmLeafInlineAddHtml(): String = """
    <div class="rixo-tree-leaf-row rixo-tree-leaf-row--inline-editing" data-rpm-inline-add="leaf">
        <div class="rixo-tree-leaf-cells">
            <div class="rixo-tree-leaf-edit rixo-tree-leaf-edit--inline-add-stack">
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--inputs">
                    <div class="rixo-tree-leaf-vtype-wrap">${createEditableCombobox("rpmCardInlineAddLeafType", "Select Vehicle Type", required = false)}</div>
                    <input id="rpmCardInlineAddLeafPrice" class="rixo-tree-leaf-inline-price money-input" type="text" value="" placeholder="0" inputmode="decimal">
                </div>
                <div class="rixo-tree-inline-add-row rixo-tree-inline-add-row--actions">
                    <button type="button" class="rixo-tree-leaf-inline-add-cancel">Cancel</button>
                    <button type="button" class="rixo-tree-leaf-inline-add-save">Add</button>
                </div>
            </div>
        </div>
    </div>
""".trimIndent()

private fun rpmTreeHeadersHtml(): String = """
    <div class="rixo-tree-headers rixo-price-map-tree-headers">
        <div class="rixo-tree-header rixo-tree-header--with-sort">
            <button type="button" id="rpmCompanySortBtn" class="tree-map-col-sort-btn" data-rpm-sort="company" title="${escapeHtml(rpmCompanySortTooltip())}">
                <span>Rixo Company</span>
                <span class="tree-map-col-sort-icon" aria-hidden="true">↕</span>
            </button>
        </div>
        <div class="rixo-tree-header">Stock Location</div>
        <div class="rixo-tree-header">Supplier Name</div>
        <div class="rixo-tree-header">Supported Vehicle Type</div>
        <div class="rixo-tree-header rixo-tree-header--price">Rixo Price</div>
    </div>
""".trimIndent()

private fun buildRpmFullRowAddHtml(): String = """
    <div class="rpm-tree-full-row-add" data-rpm-full-row-add="1">
        <div class="rpm-tree-full-row-add-grid">
            <div class="rpm-tree-full-row-col rpm-tree-full-row-col--company">
                ${createPlainTextInput("rpmFullRowCompany", "Enter Rixo Company", required = true)}
            </div>
            <div class="rpm-tree-full-row-col rpm-tree-full-row-col--stock">
                ${createEditableCombobox("rpmFullRowStock", "Select Stock Location", required = true)}
            </div>
            <div class="rpm-tree-full-row-col rpm-tree-full-row-col--supplier">
                ${createEditableCombobox("rpmFullRowSupplier", "Select Supplier Name", required = true)}
            </div>
            <div class="rpm-tree-full-row-col rpm-tree-full-row-col--vtype">
                ${createEditableCombobox("rpmFullRowVehicleType", "Select Vehicle Type", required = false)}
            </div>
            <div class="rpm-tree-full-row-col rpm-tree-full-row-col--price">
                <input id="rpmFullRowPrice" class="rixo-tree-leaf-inline-price money-input rpm-full-row-price-input" type="text" value="" placeholder="0" inputmode="decimal">
            </div>
        </div>
        <div class="rpm-tree-full-row-actions">
            <button type="button" class="rpm-full-row-add-cancel">Cancel</button>
            <button type="button" class="rpm-full-row-add-save rixo-tree-btn rixo-tree-btn--add">Save</button>
        </div>
    </div>
""".trimIndent()

private fun rpmCardWrapperClass(level: String): String = when (level) {
    "rixo_company" -> "rixo-tree-card-wrapper--company"
    "stock" -> "rixo-tree-card-wrapper--stock"
    "supplier" -> "rixo-tree-card-wrapper--field"
    else -> "rixo-tree-card-wrapper--field"
}

private fun rpmCardLevelClass(level: String): String = when (level) {
    "rixo_company" -> " rixo-tree-card--company"
    else -> " rixo-tree-card--stock"
}

private fun rpmBuildCardHtml(
    level: String,
    label: String,
    open: Boolean,
    pathCompany: String,
    pathStock: String = "",
    pathSupplier: String = "",
): String {
    val selected = when (level) {
        "rixo_company" -> rpmSelectedCompany == label
        "stock" -> rpmSelectedStock == label
        "supplier" -> rpmSelectedSupplier == label
        else -> false
    }
    val selectedClass = if (selected) " rixo-tree-card--selected" else ""
    val levelClass = rpmCardLevelClass(level)
    val wrapperClass = rpmCardWrapperClass(level)
    val ariaExpanded = if (open) "true" else "false"
    val useCardInline = rpmCardInlineEditLevel == level &&
        rpmCardInlineEditTargetMatches(level, label, pathCompany, pathStock, pathSupplier)
    if (useCardInline) {
        val comboboxId = rpmCardInlineEditId(level)
        val placeholder = when (level) {
            "rixo_company" -> "Enter Rixo Company"
            "supplier" -> "Select Supplier Name"
            "stock" -> "Select Stock Location"
            else -> "Select value"
        }
        val comboboxHtml = if (level == "rixo_company") {
            createPlainTextInput(comboboxId, placeholder, required = true, initialValue = rpmCardInlineEditCurrentLabel)
        } else {
            createEditableCombobox(comboboxId, placeholder, required = true)
        }
        return """
            <div class="rixo-tree-card-wrapper $wrapperClass" data-card-level="$level"
                 data-path-company="${escapeHtml(pathCompany)}"
                 data-path-stock="${escapeHtml(pathStock)}"
                 data-path-supplier="${escapeHtml(pathSupplier)}">
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
             data-path-company="${escapeHtml(pathCompany)}"
             data-path-stock="${escapeHtml(pathStock)}"
             data-path-supplier="${escapeHtml(pathSupplier)}">
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

private fun rpmBuildLeafRowHtml(leaf: RpmLeafRow, baseRow: RixoPriceMapTreeRowLite, fieldSeq: Int): String {
    val isEditing = rpmLeafInlineEditMappingId == leaf.id &&
        rpmLeafInlineEditLineType.equals(leaf.type, ignoreCase = true)
    val inlineClass = if (isEditing) " rixo-tree-leaf-row--inline-editing" else ""
    val selectedClass = if (rpmSelectedMappingId == leaf.id) " rixo-tree-leaf-row--selected" else ""
    val typeDisplay = leaf.type.ifBlank { "—" }
    val normalizedPrice = Regex("""-?\d[\d,]*(?:\.\d+)?""")
        .find(baseRow.price.orEmpty())?.value?.replace(",", "") ?: ""
    val prefix = "rpmLeafInline_${leaf.id}_$fieldSeq"
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

private fun buildRixoPriceMapTreeHtmlFromCache(): String {
    val list = rpmTreeRowsCache
    val fullRowHtml = if (rpmFullRowAddOpen) buildRpmFullRowAddHtml() else ""
    // Roots may be present with an empty cache until a company branch is expanded.
    if (rpmRootCompanies.isEmpty() && list.isEmpty()) {
        val emptyCompanyAdd = if (rpmCardInlineAddMatchesCompanyBranch()) {
            """<div class="rixo-tree-node">${buildRpmCardInlineAddHtml("rixo_company")}</div>"""
        } else ""
        return """
            <div class="rixo-tree rixo-price-map-tree">
                ${rpmTreeHeadersHtml()}
                $fullRowHtml
                <div class="rixo-tree-note" style="max-width:560px;">No Rixo price mappings yet. Use Add above to create your first mapping.</div>
                $emptyCompanyAdd
                <div class="rixo-tree-col-footer">${rpmTreeAddButtonHtml("rixo_company", null, null, null)}</div>
            </div>
        """.trimIndent()
    }

    val companies = rpmSortedCompanies(list)
    if (rpmSelectedCompany !in companies) {
        rpmSelectedCompany = null
        rpmSelectedStock = null
        rpmSelectedSupplier = null
    }

    val sb = StringBuilder()
    sb.append("""<div class="rixo-tree rixo-price-map-tree">""")
    sb.append(rpmTreeHeadersHtml())
    if (rpmFullRowAddOpen) {
        sb.append(buildRpmFullRowAddHtml())
    }

    if (companies.isEmpty() && rpmSearchQuery.isNotBlank()) {
        sb.append("""<div class="rixo-tree-note" style="max-width:560px;">No companies match “${escapeHtml(rpmSearchQuery.trim())}”.</div>""")
        sb.append("</div>")
        return sb.toString()
    }

    for (company in companies) {
        val companyRows = list.filter { rpmNormCompany(it.company) == company }
        val companyOpen = company == rpmSelectedCompany
        sb.append("""<div class="rixo-tree-node">""")
        sb.append(rpmBuildCardHtml("rixo_company", company, companyOpen, company))

        if (companyOpen) {
            val stocks = rpmVisibleStocks(companyRows)
            if (rpmSelectedStock != null && rpmSelectedStock !in stocks) {
                rpmSelectedStock = null
                rpmSelectedSupplier = null
            }
            sb.append("""<div class="rixo-tree-children">""")
            if (stocks.isEmpty()) {
                if (rpmCardInlineAddMatchesStockBranch(company)) {
                    sb.append("""<div class="rixo-tree-node">${buildRpmCardInlineAddHtml("stock")}</div>""")
                }
                sb.append("""<div class="rixo-tree-col-footer">${rpmTreeAddButtonHtml("stock", company, null, null)}</div>""")
            } else {
                for (stock in stocks) {
                    val stockRows = companyRows.filter { rpmNormStock(it.stock) == stock }
                    val stockOpen = stock == rpmSelectedStock
                    sb.append("""<div class="rixo-tree-node">""")
                    sb.append(rpmBuildCardHtml("stock", stock, stockOpen, company, stock))

                    if (stockOpen) {
                        val suppliers = rpmVisibleSuppliers(stockRows)
                        if (rpmSelectedSupplier != null && rpmSelectedSupplier !in suppliers) {
                            rpmSelectedSupplier = null
                        }
                        sb.append("""<div class="rixo-tree-children">""")
                        if (suppliers.isEmpty()) {
                            if (rpmCardInlineAddMatchesSupplierBranch(company, stock)) {
                                sb.append("""<div class="rixo-tree-node">${buildRpmCardInlineAddHtml("supplier")}</div>""")
                            }
                            sb.append("""<div class="rixo-tree-col-footer">${rpmTreeAddButtonHtml("supplier", company, stock, null)}</div>""")
                        } else {
                            for (supplier in suppliers) {
                                val supplierRows = stockRows.filter { rpmRowSupplierKey(it) == supplier }
                                val supplierOpen = supplier == rpmSelectedSupplier
                                sb.append("""<div class="rixo-tree-node">""")
                                sb.append(rpmBuildCardHtml("supplier", supplier, supplierOpen, company, stock, supplier))

                                if (supplierOpen) {
                                    val leaves = rpmBuildLeafRows(supplierRows)
                                    sb.append("""<div class="rixo-tree-children"><div class="rixo-tree-leaf-wrap"><div class="rixo-tree-leaf-grid">""")
                                    var seq = 0
                                    for (leaf in leaves) {
                                        val base = supplierRows.firstOrNull { it.id.toLongOrNull() == leaf.id }
                                        if (base != null) sb.append(rpmBuildLeafRowHtml(leaf, base, seq++))
                                    }
                                    if (rpmCardInlineAddMatchesLeafBranch(company, stock, supplier)) {
                                        sb.append(buildRpmLeafInlineAddHtml())
                                    }
                                    sb.append(rpmTreeAddButtonHtml("leaf", company, stock, supplier))
                                    sb.append("""</div></div></div>""")
                                }
                                sb.append("""</div>""")
                            }
                            if (rpmCardInlineAddMatchesSupplierBranch(company, stock)) {
                                sb.append("""<div class="rixo-tree-node">${buildRpmCardInlineAddHtml("supplier")}</div>""")
                            }
                            sb.append("""<div class="rixo-tree-col-footer">${rpmTreeAddButtonHtml("supplier", company, stock, null)}</div>""")
                        }
                        sb.append("""</div>""")
                    }
                    sb.append("""</div>""")
                }
                if (rpmCardInlineAddMatchesStockBranch(company)) {
                    sb.append("""<div class="rixo-tree-node">${buildRpmCardInlineAddHtml("stock")}</div>""")
                }
                sb.append("""<div class="rixo-tree-col-footer">${rpmTreeAddButtonHtml("stock", company, null, null)}</div>""")
            }
            sb.append("""</div>""")
        }
        sb.append("""</div>""")
    }
    sb.append("""</div>""")
    return sb.toString()
}

private fun rpmCloseAllMenus(root: HTMLElement) {
    val list = root.querySelectorAll(".rixo-tree-card-menu-wrap.is-open")
    val len = list.asDynamic().length as Int
    var i = 0
    while (i < len) {
        (list.asDynamic()[i] as? HTMLElement)?.classList?.remove("is-open")
        i++
    }
}

private fun rpmDispatchUpdated() {
    js("""
        window.dispatchEvent(new CustomEvent('rixoMappingUpdated'));
        window.dispatchEvent(new CustomEvent('supplierMapUpdated'));
        try { localStorage.setItem('rixoPriceMapUpdatedAt', String(Date.now())); } catch (e) {}
    """)
}

private fun rpmDeleteMappingRow(id: Long, onDone: () -> Unit) {
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
                rpmDispatchUpdated()
                onDone()
            } else {
                showMessage(pair.result.message?.toString() ?: "Failed to delete", "error")
            }
        }
        .catch { err: dynamic ->
            Logger.error("Rixo price map delete failed: ${err.toString()}")
            showMessage("Failed to delete mapping", "error")
        }
}

private fun rpmEnsureMasterOptions(callback: (Boolean) -> Unit) {
    if (rpmMasterOptionsReady) {
        callback(true)
        return
    }
    val requests = js("[]")
    requests.push(window.fetch(apiUrl("rixo-mapping/distinct-rixo-companies")))
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
            rpmMasterCompanies = rpmFilterRealCompanies(parseMasterListArray(results[0])).sortedBy { it.lowercase() }
            rpmMasterSuppliers = parseMasterListArray(results[1]).distinct().sortedBy { it.lowercase() }
            rpmMasterStocks = parseMasterListArray(results[2]).distinct().sortedBy { it.lowercase() }
            rpmMasterVehicleTypes = parseMasterListArray(results[3]).distinct().sortedBy { it.lowercase() }
            rpmMasterOptionsReady = true
            callback(true)
        }
        .catch { err: dynamic ->
            Logger.error("Rixo price map master options: ${err.toString()}")
            showMessage("Failed to load master options", "error")
            callback(false)
        }
}

private fun rpmPopulateCombobox(selectId: String, values: List<String>, selected: String) {
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

private fun rpmRowsForBranch(
    level: String,
    company: String,
    stock: String,
    supplier: String,
): List<RixoPriceMapTreeRowLite> = when (level) {
    "rixo_company" -> rpmTreeRowsCache.filter { rpmNormCompany(it.company) == rpmNormCompany(company) }
    "stock" -> rpmTreeRowsCache.filter {
        rpmNormCompany(it.company) == rpmNormCompany(company) &&
            rpmNormStock(it.stock) == rpmNormStock(stock)
    }
    "supplier" -> rpmTreeRowsCache.filter {
        rpmNormCompany(it.company) == rpmNormCompany(company) &&
            rpmNormStock(it.stock) == rpmNormStock(stock) &&
            rpmRowSupplierKey(it) == rpmNormSupplier(supplier)
    }
    else -> emptyList()
}

private fun rpmPutPayloadFromRow(
    row: RixoPriceMapTreeRowLite,
    newCompany: String? = null,
    newSupplier: String? = null,
    newStock: String? = null,
    pathEditOnly: Boolean = false,
): dynamic {
    val p = js("{}")
    p.rixoCompany = (newCompany ?: row.company).trim()
    p.stockLocation = (newStock ?: row.stock).trim()
    // Never send null/blank pol — omit so backend keeps existing value (healPol).
    val existingPol = row.pol?.trim()?.takeIf { it.isNotEmpty() }
    if (existingPol != null) p.pol = existingPol
    val auction = newSupplier ?: row.auctionName
    p.auctionName = auction?.trim()?.takeIf { it.isNotEmpty() }
    p.venueId = row.venueId?.trim()?.takeIf { it.isNotEmpty() }
        ?: row.auctionName?.let { rpmResolveUniqueVenueForSupplier(it, warnOnConflict = false) }
    if (!pathEditOnly) {
        p.supportedVehicleType = row.vType?.trim()?.takeIf { it.isNotEmpty() }
        p.rixoPrice = row.price?.trim()?.takeIf { it.isNotEmpty() }
    }
    return p
}

private fun runRpmPutBatchSequential(
    rows: List<RixoPriceMapTreeRowLite>,
    payloadForRow: (RixoPriceMapTreeRowLite) -> dynamic,
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
        runRpmPutBatchSequential(rest, payloadForRow, onDone, onFail)
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
                runRpmPutBatchSequential(rest, payloadForRow, onDone, onFail)
            } else {
                onFail(pair.result.message?.toString() ?: "Failed to update")
            }
        }
        .catch { err: dynamic -> onFail(err.toString()) }
}

private fun postRpmMappingBulkOneRow(
    insertMode: String?,
    company: String,
    stock: String,
    supplier: String,
    vtype: String,
    price: String,
    onSuccess: () -> Unit,
) {
    if (rpmRejectIfSemicolon(company, stock, supplier, vtype, price)) return
    val mode = insertMode ?: "RPM_FULL"
    val obj: dynamic = js("{}")
    when (mode) {
        "RPM_COMPANY" -> {
            if (company.isBlank()) { showMessage("Rixo company is required", "error"); return }
            obj.insertMode = "RPM_COMPANY"
            obj.rixoCompany = company.trim()
            obj.stockLocation = "-"
        }
        "RPM_STOCK" -> {
            if (company.isBlank() || stock.isBlank()) {
                showMessage("Company and stock location are required", "error"); return
            }
            obj.insertMode = "RPM_STOCK"
            obj.rixoCompany = company.trim()
            obj.stockLocation = stock.trim()
            // Auction left unset so stock-before-supplier skeletons stay blank/`-`.
            rpmApplyPolToBulkPayload(obj, stock)
        }
        "RPM_SUPPLIER" -> {
            if (company.isBlank() || stock.isBlank() || supplier.isBlank()) {
                showMessage("Company, stock location, and supplier name are required", "error"); return
            }
            obj.insertMode = "RPM_SUPPLIER"
            obj.rixoCompany = company.trim()
            obj.stockLocation = stock.trim()
            obj.auctionName = supplier.trim()
            rpmApplyVenueToBulkPayload(obj, supplier)
            rpmApplyPolToBulkPayload(obj, stock)
        }
        else -> {
            if (company.isBlank() || stock.isBlank() || supplier.isBlank()) {
                showMessage("Complete the path (Rixo company, stock location, and supplier)", "error"); return
            }
            if (vtype.isNotBlank() && !rpmListContains(rpmMasterVehicleTypes, vtype)) {
                showMessage("Please select a vehicle type from the list", "error"); return
            }
            if (price.isNotBlank() && rpmParseMoney(price) == null) {
                showMessage("Rixo price must be numeric", "error"); return
            }
            obj.insertMode = if (mode == "FULL") "FULL" else "RPM_FULL"
            obj.rixoCompany = company.trim()
            obj.stockLocation = stock.trim()
            obj.auctionName = supplier.trim()
            rpmApplyVenueToBulkPayload(obj, supplier)
            rpmApplyPolToBulkPayload(obj, stock)
            obj.supportedVehicleType = vtype.trim().takeIf { it.isNotEmpty() }
            obj.rixoPrice = if (price.isBlank()) price else rpmNormalizePriceForDb(price)
        }
    }
    val mergeId = when (mode) {
        "RPM_COMPANY" -> null
        "RPM_STOCK" -> rpmMergeRowIdForStock(company)
        "RPM_SUPPLIER" -> rpmMergeRowIdForSupplier(company, stock)
        "RPM_FULL", "FULL" -> rpmMergeRowIdForLeaf(company, stock, supplier)
        else -> rpmMergeRowIdForLeaf(company, stock, supplier)
    }
    if (mergeId != null) obj.id = mergeId.toDouble()
    fun sendBulk() {
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
                    rpmDispatchUpdated()
                    onSuccess()
                    showMessage(pair.result.message?.toString() ?: "Mapping added", "success")
                } else {
                    showMessage(pair.result.message?.toString() ?: "Failed to add mapping", "error")
                }
            }
            .catch { err: dynamic ->
                Logger.error("Rixo price map add failed: ${err.toString()}")
                showMessage("Failed to add mapping", "error")
            }
    }
    if (rpmPayloadNeedsStockPol(obj)) {
        val stockKey = (obj.stockLocation as? String)?.trim().orEmpty()
        val auctionKey = (obj.auctionName as? String)?.trim()?.takeIf { it.isNotEmpty() && it != "-" }
        rpmResolvePolForStockAsync(stockKey, auctionKey) { apiPol ->
            if (apiPol != null) obj.pol = apiPol
            sendBulk()
        }
    } else {
        sendBulk()
    }
}

private fun wireRpmInlineAddComboboxes() {
    when (rpmCardInlineAddLevel) {
        "rixo_company" -> Unit // plain text — no dropdown options
        "supplier" -> rpmPopulateCombobox("rpmCardInlineAddSupplier", rpmMasterSuppliers, "")
        "stock" -> rpmPopulateCombobox("rpmCardInlineAddStock", rpmMasterStocks, "")
        "leaf" -> {
            rpmPopulateCombobox("rpmCardInlineAddLeafType", rpmMasterVehicleTypes, "")
            (document.getElementById("rpmCardInlineAddLeafPrice") as? HTMLInputElement)?.value = ""
        }
        else -> Unit
    }
}

private fun wireRpmCardInlineCombobox() {
    val level = rpmCardInlineEditLevel ?: return
    val id = rpmCardInlineEditId(level)
    when (level) {
        "rixo_company" -> Unit // plain text — value set via createPlainTextInput initialValue
        "supplier" -> rpmPopulateCombobox(id, rpmMasterSuppliers, rpmCardInlineEditCurrentLabel)
        "stock" -> rpmPopulateCombobox(id, rpmMasterStocks, rpmCardInlineEditCurrentLabel)
        else -> Unit
    }
}

private fun wireRpmFullRowAddComboboxes() {
    // Rixo Company is plain text (no dropdown).
    rpmPopulateCombobox("rpmFullRowStock", rpmMasterStocks, "")
    rpmPopulateCombobox("rpmFullRowSupplier", rpmMasterSuppliers, "")
    rpmPopulateCombobox("rpmFullRowVehicleType", rpmMasterVehicleTypes, "")
    (document.getElementById("rpmFullRowPrice") as? HTMLInputElement)?.value = ""
}

private fun startRpmFullRowAdd(root: HTMLElement) {
    rpmLeafInlineEditMappingId = null
    rpmLeafInlineEditLineType = ""
    clearRpmCardInlineEdit()
    clearRpmCardInlineAdd()
    rpmFullRowAddOpen = true
    rpmEnsureMasterOptions { ok ->
        if (!ok) {
            clearRpmFullRowAdd()
            return@rpmEnsureMasterOptions
        }
        root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
        bindRixoPriceMapTreeClicks(root)
        window.setTimeout({ wireRpmFullRowAddComboboxes() }, 0)
    }
}

private fun executeRpmFullRowAddSave() {
    val company = getEditableComboboxValue("rpmFullRowCompany").trim()
    val stock = getEditableComboboxValue("rpmFullRowStock").trim()
    val supplier = getEditableComboboxValue("rpmFullRowSupplier").trim()
    val vtype = getEditableComboboxValue("rpmFullRowVehicleType").trim()
    val price = (document.getElementById("rpmFullRowPrice") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (company.isEmpty()) {
        showMessage("Rixo company is required", "error"); return
    }
    if (rpmIsBlankCompany(company)) {
        showMessage("Rixo company cannot be blank or '-'", "error"); return
    }
    if (stock.isEmpty() || !rpmListContains(rpmMasterStocks, stock)) {
        showMessage("Please select a stock location from the list", "error"); return
    }
    if (supplier.isEmpty()) {
        showMessage("Supplier name is required", "error"); return
    }
    if (vtype.isNotBlank() && !rpmListContains(rpmMasterVehicleTypes, vtype)) {
        showMessage("Please select a vehicle type from the list", "error"); return
    }
    if (price.isNotBlank() && rpmParseMoney(price) == null) {
        showMessage("Rixo price must be numeric", "error"); return
    }
    postRpmMappingBulkOneRow(null, company, stock, supplier, vtype, price) {
        clearRpmFullRowAdd()
        rpmSelectedCompany = rpmNormCompany(company)
        rpmSelectedStock = rpmNormStock(stock)
        rpmSelectedSupplier = rpmNormSupplier(supplier)
        rpmMasterOptionsReady = false
        refreshRixoPriceMapTreeData()
    }
}

private fun bindRpmTopAddButton() {
    val btn = document.getElementById("rpmTopAddBtn") as? HTMLElement ?: return
    val prev = btn.asDynamic().__rpmTopAddHandler.unsafeCast<((Event) -> Unit)?>()
    if (prev != null) btn.removeEventListener("click", prev)
    val handler: (Event) -> Unit = handler@{ ev ->
        ev.preventDefault()
        val root = document.getElementById("rixoPriceMapTreeRoot") as? HTMLElement ?: return@handler
        startRpmFullRowAdd(root)
    }
    btn.asDynamic().__rpmTopAddHandler = handler
    btn.addEventListener("click", handler)
}

private fun toggleRpmCompanySort() {
    rpmCompanySortOrder = when (rpmCompanySortOrder) {
        "asc" -> "desc"
        "desc" -> "asc"
        else -> "asc"
    }
}

private fun rpmApplySearchAndRerender() {
    val root = document.getElementById("rixoPriceMapTreeRoot") as? HTMLElement ?: return
    val clearBtn = document.getElementById("rpmCompanySearchClearBtn") as? HTMLElement
    if (clearBtn != null) {
        clearBtn.style.visibility = if (rpmSearchQuery.isBlank()) "hidden" else "visible"
    }
    rpmRerenderTree(root)
}

private fun bindRpmSearchToolbar() {
    val input = document.getElementById("rpmCompanySearchInput") as? HTMLInputElement ?: return
    val clearBtn = document.getElementById("rpmCompanySearchClearBtn") as? HTMLElement

    val prevInput = input.asDynamic().__rpmSearchInputHandler.unsafeCast<((Event) -> Unit)?>()
    if (prevInput != null) input.removeEventListener("input", prevInput)
    val inputHandler: (Event) -> Unit = {
        val q = input.value
        if (rpmSearchDebounceTimer != null) window.clearTimeout(rpmSearchDebounceTimer)
        rpmSearchDebounceTimer = window.setTimeout({
            rpmSearchQuery = q
            rpmApplySearchAndRerender()
        }, 180)
    }
    input.asDynamic().__rpmSearchInputHandler = inputHandler
    input.addEventListener("input", inputHandler)

    if (clearBtn != null) {
        val prevClear = clearBtn.asDynamic().__rpmSearchClearHandler.unsafeCast<((Event) -> Unit)?>()
        if (prevClear != null) clearBtn.removeEventListener("click", prevClear)
        val clearHandler: (Event) -> Unit = { ev ->
            ev.preventDefault()
            if (rpmSearchDebounceTimer != null) window.clearTimeout(rpmSearchDebounceTimer)
            input.value = ""
            rpmSearchQuery = ""
            rpmApplySearchAndRerender()
            input.focus()
        }
        clearBtn.asDynamic().__rpmSearchClearHandler = clearHandler
        clearBtn.addEventListener("click", clearHandler)
    }
}

private fun startRpmInlineAdd(
    level: String,
    pathCompany: String,
    pathStock: String,
    pathSupplier: String,
    root: HTMLElement,
) {
    rpmLeafInlineEditMappingId = null
    rpmLeafInlineEditLineType = ""
    clearRpmCardInlineEdit()
    clearRpmFullRowAdd()
    when (level) {
        "rixo_company" -> {
            // Root-level company add when list empty
        }
        "stock" -> {
            if (pathCompany.isEmpty()) { showMessage("Select a Rixo company first", "error"); return }
            rpmSelectedCompany = rpmNormCompany(pathCompany)
            rpmSelectedStock = null
            rpmSelectedSupplier = null
        }
        "supplier" -> {
            if (pathCompany.isEmpty() || pathStock.isEmpty()) {
                showMessage("Select company and stock location first", "error"); return
            }
            rpmSelectedCompany = rpmNormCompany(pathCompany)
            rpmSelectedStock = rpmNormStock(pathStock)
            rpmSelectedSupplier = null
        }
        "leaf" -> {
            if (pathCompany.isEmpty() || pathStock.isEmpty() || pathSupplier.isEmpty()) {
                showMessage("Select company, stock location, and supplier first", "error"); return
            }
            rpmSelectedCompany = rpmNormCompany(pathCompany)
            rpmSelectedStock = rpmNormStock(pathStock)
            rpmSelectedSupplier = rpmNormSupplier(pathSupplier)
        }
        else -> return
    }
    rpmCardInlineAddLevel = level
    rpmCardInlineAddCompany = pathCompany.trim()
    rpmCardInlineAddStock = pathStock.trim()
    rpmCardInlineAddSupplier = pathSupplier.trim()
    rpmEnsureMasterOptions { ok ->
        if (!ok) {
            clearRpmCardInlineAdd()
            return@rpmEnsureMasterOptions
        }
        root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
        bindRixoPriceMapTreeClicks(root)
        window.setTimeout({ wireRpmInlineAddComboboxes() }, 0)
    }
}

private fun executeRpmCardInlineAddSave() {
    when (rpmCardInlineAddLevel) {
        "rixo_company" -> {
            val company = getEditableComboboxValue("rpmCardInlineAddCompany").trim()
            if (company.isEmpty()) {
                showMessage("Rixo company is required", "error"); return
            }
            if (rpmIsBlankCompany(company)) {
                showMessage("Rixo company cannot be blank or '-'", "error"); return
            }
            if (rpmRejectIfSemicolon(company)) return
            postRpmMappingBulkOneRow("RPM_COMPANY", company, "", "", "", "") {
                clearRpmCardInlineAdd()
                rpmSelectedCompany = rpmNormCompany(company)
                rpmMasterOptionsReady = false
                refreshRixoPriceMapTreeData()
            }
        }
        "stock" -> {
            val company = rpmFirstNonBlank(rpmCardInlineAddCompany, rpmSelectedCompany)
            val stock = getEditableComboboxValue("rpmCardInlineAddStock").trim()
            if (stock.isEmpty() || !rpmListContains(rpmMasterStocks, stock)) {
                showMessage("Please select a stock location from the list", "error"); return
            }
            postRpmMappingBulkOneRow("RPM_STOCK", company, stock, "", "", "") {
                clearRpmCardInlineAdd()
                rpmSelectedStock = rpmNormStock(stock)
                refreshRixoPriceMapTreeData()
            }
        }
        "supplier" -> {
            val company = rpmFirstNonBlank(rpmCardInlineAddCompany, rpmSelectedCompany)
            val stock = rpmFirstNonBlank(rpmCardInlineAddStock, rpmSelectedStock)
            val supplier = getEditableComboboxValue("rpmCardInlineAddSupplier").trim()
            if (supplier.isEmpty()) { showMessage("Supplier name is required", "error"); return }
            postRpmMappingBulkOneRow("RPM_SUPPLIER", company, stock, supplier, "", "") {
                clearRpmCardInlineAdd()
                rpmSelectedSupplier = rpmNormSupplier(supplier)
                refreshRixoPriceMapTreeData()
            }
        }
        else -> Unit
    }
}

private fun executeRpmLeafInlineAddSave() {
    if (rpmCardInlineAddLevel != "leaf") return
    val company = rpmFirstNonBlank(rpmCardInlineAddCompany, rpmSelectedCompany)
    val stock = rpmFirstNonBlank(rpmCardInlineAddStock, rpmSelectedStock)
    val supplier = rpmFirstNonBlank(rpmCardInlineAddSupplier, rpmSelectedSupplier)
    val vtype = getEditableComboboxValue("rpmCardInlineAddLeafType").trim()
    val price = (document.getElementById("rpmCardInlineAddLeafPrice") as? HTMLInputElement)?.value?.trim().orEmpty()
    if (vtype.isNotBlank() && !rpmListContains(rpmMasterVehicleTypes, vtype)) {
        showMessage("Please select a vehicle type from the list", "error"); return
    }
    if (price.isNotBlank() && rpmParseMoney(price) == null) {
        showMessage("Rixo price must be numeric", "error"); return
    }
    postRpmMappingBulkOneRow(null, company, stock, supplier, vtype, price) {
        clearRpmCardInlineAdd()
        refreshRixoPriceMapTreeData()
    }
}

private fun cancelRpmCardInlineEdit(root: HTMLElement) {
    clearRpmCardInlineEdit()
    root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
    bindRixoPriceMapTreeClicks(root)
}

private fun executeRpmCardInlineSave(root: HTMLElement) {
    val level = rpmCardInlineEditLevel ?: return
    val pathCompany = rpmCardInlineEditCompany
    val pathStock = rpmCardInlineEditStock
    val pathSupplier = rpmCardInlineEditSupplier
    val currentLabel = rpmCardInlineEditCurrentLabel
    val comboboxId = rpmCardInlineEditId(level)
    val newVal = getEditableComboboxValue(comboboxId).trim()
    if (newVal.isEmpty()) { showMessage("Value is required", "error"); return }
    if (rpmRejectIfSemicolon(newVal)) return
    if (newVal.equals(currentLabel, ignoreCase = true)) {
        cancelRpmCardInlineEdit(root)
        return
    }
    val listOk = when (level) {
        "supplier", "rixo_company" -> true
        "stock" -> rpmListContains(rpmMasterStocks, newVal)
        else -> false
    }
    if (!listOk) { showMessage("Please select a value from the list", "error"); return }
    if (level == "rixo_company" && rpmIsBlankCompany(newVal)) {
        showMessage("Rixo company cannot be blank or '-'", "error"); return
    }
    val branchKey = when (level) {
        "rixo_company" -> currentLabel
        "stock" -> pathStock
        "supplier" -> pathSupplier
        else -> currentLabel
    }
    val rows = rpmRowsForBranch(level, pathCompany, pathStock, branchKey).distinctBy { it.id }
    if (rows.isEmpty()) { showMessage("No rows to update", "error"); return }
    val payloadBuilder: (RixoPriceMapTreeRowLite) -> dynamic = { row ->
        when (level) {
            "rixo_company" -> rpmPutPayloadFromRow(row, newCompany = newVal, pathEditOnly = true)
            "stock" -> rpmPutPayloadFromRow(row, newStock = newVal, pathEditOnly = true)
            "supplier" -> rpmPutPayloadFromRow(row, newSupplier = newVal, pathEditOnly = true)
            else -> rpmPutPayloadFromRow(row)
        }
    }
    runRpmPutBatchSequential(rows, payloadBuilder,
        onDone = {
            clearRpmCardInlineEdit()
            when (level) {
                "rixo_company" -> if (rpmSelectedCompany == rpmNormCompany(pathCompany)) rpmSelectedCompany = rpmNormCompany(newVal)
                "stock" -> if (rpmSelectedStock == rpmNormStock(pathStock)) rpmSelectedStock = rpmNormStock(newVal)
                "supplier" -> if (rpmSelectedSupplier == rpmNormSupplier(pathSupplier)) rpmSelectedSupplier = rpmNormSupplier(newVal)
            }
            rpmDispatchUpdated()
            refreshRixoPriceMapTreeData()
            showMessage("Updated", "success")
        },
        onFail = { msg -> showMessage(msg, "error") },
    )
}

private fun rpmRerenderTree(root: HTMLElement) {
    root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
    bindRixoPriceMapTreeClicks(root)
    if (rpmFullRowAddOpen) {
        window.setTimeout({ wireRpmFullRowAddComboboxes() }, 0)
    }
}

private fun rpmBranchDeleteTitle(level: String): String = when (level) {
    "rixo_company" -> "Delete company branch?"
    "stock" -> "Delete stock branch?"
    "supplier" -> "Delete supplier branch?"
    else -> "Delete branch?"
}

private fun rpmBranchDeleteMessageHtml(
    level: String,
    company: String,
    stock: String,
    label: String,
    rowCount: Int,
): String {
    val pathParts = when (level) {
        "rixo_company" -> listOf(label)
        "stock" -> listOf(company, label)
        "supplier" -> listOf(company, stock, label)
        else -> listOf(label)
    }.filter { it.isNotBlank() }
    val pathLine = pathParts.joinToString(" → ") { escapeHtml(it) }
    val shared = "These rows are shared with Supplier Map. Deleting here also removes them there."
    val companyRisk = if (level == "rixo_company") {
        "<br><br>This deletes <strong>all</strong> mappings for this company across every supplier."
    } else ""
    return "Path: <strong>$pathLine</strong><br>" +
        "This will delete <strong>$rowCount</strong> mapping row(s).<br><br>" +
        "$shared$companyRisk<br><br>This cannot be undone. Are you sure?"
}

private fun rpmLeafDeleteMessageHtml(baseRow: RixoPriceMapTreeRowLite, typeLabel: String): String {
    val type = typeLabel.ifBlank { "—" }
    val price = rpmFormatPriceDisplay(baseRow.price)
    val path = listOfNotNull(
        baseRow.company.takeIf { it.isNotBlank() },
        baseRow.stock.takeIf { it.isNotBlank() && it != "-" },
        baseRow.auctionName?.takeIf { it.isNotBlank() },
    ).joinToString(" → ") { escapeHtml(it) }
    return "Delete mapping?<br><br>" +
        "Type: <strong>${escapeHtml(type)}</strong><br>" +
        "Price: <strong>$price</strong><br>" +
        "Path: <strong>$path</strong><br><br>" +
        "These rows are shared with Supplier Map. Deleting here also removes them there.<br><br>" +
        "This cannot be undone. Are you sure?"
}

private fun bindRixoPriceMapTreeClicks(root: HTMLElement) {
    val prev = root.asDynamic().__rpmTreeClickHandler.unsafeCast<((Event) -> Unit)?>()
    if (prev != null) root.removeEventListener("click", prev)

    val handler: (Event) -> Unit = click@{ ev ->
        val target = ev.target.asDynamic() as? Element ?: return@click

        val sortBtn = target.closest("[data-rpm-sort='company']") as? HTMLElement
        if (sortBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            toggleRpmCompanySort()
            rpmRerenderTree(root)
            return@click
        }

        if (target.closest(".rixo-tree-card-menu-wrap") == null) rpmCloseAllMenus(root)

        val addBtn = target.closest(".rixo-tree-add-btn") as? HTMLElement
        if (addBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val level = addBtn.getAttribute("data-add-level").orEmpty()
            if (level !in setOf("rixo_company", "stock", "supplier", "leaf")) return@click
            clearRpmFullRowAdd()
            startRpmInlineAdd(
                level,
                addBtn.getAttribute("data-company")?.trim().orEmpty(),
                addBtn.getAttribute("data-stock")?.trim().orEmpty(),
                addBtn.getAttribute("data-supplier")?.trim().orEmpty(),
                root,
            )
            return@click
        }

        val menuItem = target.closest(".rixo-tree-card-menu-item") as? HTMLElement
        if (menuItem != null) {
            ev.preventDefault()
            ev.stopPropagation()
            rpmCloseAllMenus(root)
            val action = menuItem.getAttribute("data-menu-action").orEmpty()
            val wrap = menuItem.closest(".rixo-tree-card-wrapper") as? HTMLElement ?: return@click
            val level = wrap.getAttribute("data-card-level").orEmpty()
            if (level !in setOf("rixo_company", "stock", "supplier")) return@click
            val company = wrap.getAttribute("data-path-company").orEmpty()
            val stock = wrap.getAttribute("data-path-stock").orEmpty()
            val supplier = wrap.getAttribute("data-path-supplier").orEmpty()
            val label = wrap.querySelector(".rixo-tree-card")?.getAttribute("data-value").orEmpty()
            when (action) {
                "edit" -> {
                    rpmLeafInlineEditMappingId = null
                    rpmLeafInlineEditLineType = ""
                    clearRpmCardInlineAdd()
                    clearRpmFullRowAdd()
                    rpmCardInlineEditLevel = level
                    rpmCardInlineEditCompany = company
                    rpmCardInlineEditStock = stock
                    rpmCardInlineEditSupplier = supplier
                    rpmCardInlineEditCurrentLabel = label
                    rpmEnsureMasterOptions { ok ->
                        if (!ok) {
                            clearRpmCardInlineEdit()
                            return@rpmEnsureMasterOptions
                        }
                        root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
                        bindRixoPriceMapTreeClicks(root)
                        window.setTimeout({ wireRpmCardInlineCombobox() }, 0)
                    }
                }
                "delete" -> {
                    // Ensure branch rows (ids) are loaded before cascade delete.
                    ensureRpmCompanyBranchLoaded(company) {
                        val branchRows = rpmTreeRowsCache.filter { row ->
                            rpmNormCompany(row.company) == rpmNormCompany(company) &&
                                when (level) {
                                    "rixo_company" -> true
                                    "stock" -> rpmNormStock(row.stock) == label
                                    "supplier" -> rpmNormStock(row.stock) == rpmNormStock(stock) &&
                                        rpmRowSupplierKey(row) == rpmNormSupplier(label)
                                    else -> false
                                }
                        }
                        val rowsToDelete = branchRows.mapNotNull { it.id.toLongOrNull() }.distinct()
                        if (rowsToDelete.isEmpty()) return@ensureRpmCompanyBranchLoaded
                        showRixoMappingDeleteConfirm(
                            title = rpmBranchDeleteTitle(level),
                            messageHtml = rpmBranchDeleteMessageHtml(
                                level, company, stock, label, rowsToDelete.size,
                            ),
                            onConfirm = {
                                fun deleteNext(idx: Int) {
                                    if (idx >= rowsToDelete.size) {
                                        refreshRixoPriceMapTreeData()
                                        return
                                    }
                                    rpmDeleteMappingRow(rowsToDelete[idx]) { deleteNext(idx + 1) }
                                }
                                deleteNext(0)
                            },
                        )
                    }
                }
            }
            return@click
        }

        val cardInlineAddSave = target.closest(".rixo-tree-card-inline-add-save") as? HTMLElement
        if (cardInlineAddSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRpmCardInlineAddSave()
            return@click
        }

        val cardInlineAddCancel = target.closest(".rixo-tree-card-inline-add-cancel") as? HTMLElement
        if (cardInlineAddCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearRpmCardInlineAdd()
            rpmRerenderTree(root)
            return@click
        }

        val fullRowSave = target.closest(".rpm-full-row-add-save") as? HTMLElement
        if (fullRowSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRpmFullRowAddSave()
            return@click
        }

        val fullRowCancel = target.closest(".rpm-full-row-add-cancel") as? HTMLElement
        if (fullRowCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearRpmFullRowAdd()
            rpmRerenderTree(root)
            return@click
        }

        if (target.closest(".rpm-tree-full-row-add") != null) return@click

        val cardInlineSave = target.closest(".rixo-tree-card-inline-save") as? HTMLElement
        if (cardInlineSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRpmCardInlineSave(root)
            return@click
        }

        val cardInlineCancel = target.closest(".rixo-tree-card-inline-cancel") as? HTMLElement
        if (cardInlineCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            cancelRpmCardInlineEdit(root)
            return@click
        }

        if (target.closest(".rixo-tree-card--inline-editing") != null) return@click

        val leafInlineAddSave = target.closest(".rixo-tree-leaf-inline-add-save") as? HTMLElement
        if (leafInlineAddSave != null) {
            ev.preventDefault()
            ev.stopPropagation()
            executeRpmLeafInlineAddSave()
            return@click
        }

        val leafInlineAddCancel = target.closest(".rixo-tree-leaf-inline-add-cancel") as? HTMLElement
        if (leafInlineAddCancel != null) {
            ev.preventDefault()
            ev.stopPropagation()
            clearRpmCardInlineAdd()
            rpmRerenderTree(root)
            return@click
        }

        val menuBtn = target.closest(".rixo-tree-card-menu-btn") as? HTMLElement
        if (menuBtn != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val wrap = menuBtn.closest(".rixo-tree-card-menu-wrap") as? HTMLElement ?: return@click
            val wasOpen = wrap.classList.contains("is-open")
            rpmCloseAllMenus(root)
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
            val baseRow = rpmTreeRowsCache.firstOrNull { it.id.toLongOrNull() == id } ?: return@click
            showRixoMappingDeleteConfirm(
                title = "Delete mapping?",
                messageHtml = rpmLeafDeleteMessageHtml(baseRow, typeLabel),
                onConfirm = { rpmDeleteMappingRow(id) { refreshRixoPriceMapTreeData() } },
            )
            return@click
        }

        val leafEdit = target.closest(".rixo-tree-leaf-edit-btn") as? HTMLElement
        if (leafEdit != null) {
            ev.preventDefault()
            ev.stopPropagation()
            val row = leafEdit.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement ?: return@click
            val id = row.getAttribute("data-mapping-id")?.toLongOrNull() ?: return@click
            rpmLeafInlineEditMappingId = id
            rpmLeafInlineEditLineType = row.getAttribute("data-mapping-type")?.trim().orEmpty()
            rpmEnsureMasterOptions { ok ->
                if (!ok) {
                    rpmLeafInlineEditMappingId = null
                    rpmLeafInlineEditLineType = ""
                    return@rpmEnsureMasterOptions
                }
                root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
                bindRixoPriceMapTreeClicks(root)
                window.setTimeout({
                    val prefix = row.getAttribute("data-inline-field-id") ?: return@setTimeout
                    rpmPopulateCombobox("${prefix}_type", rpmMasterVehicleTypes, rpmLeafInlineEditLineType)
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
            val baseRow = rpmTreeRowsCache.firstOrNull { it.id.toLongOrNull() == id } ?: return@click
            val prefix = rowEl.getAttribute("data-inline-field-id") ?: return@click
            val vtype = getEditableComboboxValue("${prefix}_type").trim()
            val price = (document.getElementById("${prefix}_price") as? HTMLInputElement)?.value?.trim().orEmpty()
            if (rpmRejectIfSemicolon(vtype, price)) return@click
            if (vtype.isNotBlank() && !rpmListContains(rpmMasterVehicleTypes, vtype)) {
                showMessage("Please select a vehicle type from the list", "error")
                return@click
            }
            if (price.isEmpty() || rpmParseMoney(price) == null) {
                showMessage("Rixo price must be numeric", "error")
                return@click
            }
            val payload = js("{}")
            payload.rixoCompany = baseRow.company
            payload.auctionName = baseRow.auctionName
            payload.stockLocation = baseRow.stock
            payload.venueId = baseRow.venueId?.trim()?.takeIf { it.isNotEmpty() }
                ?: baseRow.auctionName?.let { rpmResolveUniqueVenueForSupplier(it, warnOnConflict = false) }
            val leafPol = baseRow.pol?.trim()?.takeIf { it.isNotEmpty() }
                ?: rpmResolveUniquePolForStock(baseRow.stock, warnOnConflict = false)
            if (leafPol != null) payload.pol = leafPol
            // Always send string (incl. "") so PUT can clear vehicle type; omit/null would coalesce to old value.
            payload.supportedVehicleType = vtype
            payload.rixoPrice = rpmNormalizePriceForDb(price)
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
                        rpmLeafInlineEditMappingId = null
                        rpmLeafInlineEditLineType = ""
                        showMessage("Mapping updated", "success")
                        rpmDispatchUpdated()
                        refreshRixoPriceMapTreeData()
                    } else {
                        showMessage(pair.result.message?.toString() ?: "Failed to update", "error")
                    }
                }
                .catch { err: dynamic ->
                    Logger.error("Rixo price map update failed: ${err.toString()}")
                    showMessage("Failed to update mapping", "error")
                }
            return@click
        }

        if (target.closest(".rixo-tree-leaf-edit") != null) return@click

        val leaf = target.closest(".rixo-tree-leaf-row--selectable") as? HTMLElement
        if (leaf != null) {
            val id = leaf.getAttribute("data-mapping-id")?.toLongOrNull()
            rpmSelectedMappingId = if (rpmSelectedMappingId == id) null else id
            rpmRerenderTree(root)
            return@click
        }

        val card = target.closest(".rixo-tree-card") as? HTMLElement ?: return@click
        if (card.classList.contains("rixo-tree-card--inline-editing")) return@click
        val level = card.getAttribute("data-level") ?: return@click
        val value = card.getAttribute("data-value") ?: return@click
        rpmLeafInlineEditMappingId = null
        rpmLeafInlineEditLineType = ""
        clearRpmCardInlineEdit()
        clearRpmCardInlineAdd()
        when (level) {
            "rixo_company" -> {
                if (rpmSelectedCompany == value) {
                    rpmSelectedCompany = null
                    rpmSelectedStock = null
                    rpmSelectedSupplier = null
                    rpmSelectedMappingId = null
                    rpmRerenderTree(root)
                } else {
                    rpmSelectedCompany = value
                    rpmSelectedStock = null
                    rpmSelectedSupplier = null
                    rpmSelectedMappingId = null
                    ensureRpmCompanyBranchLoaded(value) { rpmRerenderTree(root) }
                }
                return@click
            }
            "stock" -> {
                if (rpmSelectedStock == value) {
                    rpmSelectedStock = null
                    rpmSelectedSupplier = null
                } else {
                    rpmSelectedStock = value
                    rpmSelectedSupplier = null
                }
            }
            "supplier" -> {
                rpmSelectedSupplier = if (rpmSelectedSupplier == value) null else value
            }
            else -> return@click
        }
        rpmSelectedMappingId = null
        rpmRerenderTree(root)
    }

    root.asDynamic().__rpmTreeClickHandler = handler
    root.addEventListener("click", handler)
}

fun showRixoPriceMapTreePage() {
    val content = document.getElementById("content") ?: return
    content.innerHTML = """
        <div id="rixoPriceMapTreePage" class="rixo-tree-page rixo-tree-page--rixo-price-map">
            <div class="rixo-tree-topbar">
                <h2 class="rixo-tree-title">Rixo Price Map</h2>
                <button type="button" id="rpmTopAddBtn" class="rixo-tree-btn rixo-tree-btn--add">+ Add</button>
            </div>
            ${rpmSearchToolbarHtml()}
            <div id="rixoPriceMapTreeRoot" class="rixo-tree-root rixo-tree-root--rixo-price-map">
                <div class="rixo-tree-loading">Loading…</div>
            </div>
        </div>
    """.trimIndent()
    bindRpmTopAddButton()
    bindRpmSearchToolbar()
    loadRixoPriceMapTree()
}

private fun ensureRpmCompanyBranchLoaded(company: String, onDone: () -> Unit) {
    val key = rpmNormCompany(company)
    if (key in rpmLoadedCompanyKeys) {
        onDone()
        return
    }
    val enc = js("encodeURIComponent")(company.trim()).unsafeCast<String>()
    window.fetch(apiUrl("rixo-mapping/by-company?rixoCompany=$enc"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load company branch')")
        }
        .then { result: dynamic ->
            val ok = result.success as? Boolean ?: false
            if (!ok) throw js("Error(result.message || 'Failed to load company branch')")
            val data = result.data
            val arr = if (js("Array.isArray(data)") as Boolean) (data as Array<dynamic>).toList() else emptyList()
            val parsed = parseRixoPriceMapTreeRows(arr)
            rpmTreeRowsCache = rpmTreeRowsCache.filter { rpmNormCompany(it.company) != key } + parsed
            rpmLoadedCompanyKeys.add(key)
            onDone()
        }
        .catch { err: dynamic ->
            Logger.error("Rixo price map company branch: ${err.toString()}")
            showMessage("Failed to load company branch", "error")
            onDone()
        }
}

fun loadRixoPriceMapTree() {
    val root = document.getElementById("rixoPriceMapTreeRoot") as? HTMLElement ?: return
    window.fetch(apiUrl("rixo-mapping/distinct-rixo-companies"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load Rixo companies')")
        }
        .then { raw: dynamic ->
            rpmRootCompanies = rpmFilterRealCompanies(parseMasterListArray(raw))
            rpmTreeRowsCache = emptyList()
            rpmLoadedCompanyKeys.clear()
            rpmSelectedCompany = null
            rpmSelectedSupplier = null
            rpmSelectedStock = null
            rpmSelectedMappingId = null
            rpmLeafInlineEditMappingId = null
            rpmLeafInlineEditLineType = ""
            root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
            bindRixoPriceMapTreeClicks(root)
        }
        .catch { err: dynamic ->
            Logger.error("Rixo price map tree load: ${err.toString()}")
            root.innerHTML = """<div style="text-align:center;color:#b91c1c;padding:32px;">Failed to load Rixo price map. ${escapeHtml(err.message?.toString() ?: "")}</div>"""
        }
}

fun refreshRixoPriceMapTreeData() {
    val root = document.getElementById("rixoPriceMapTreeRoot") as? HTMLElement ?: return
    window.fetch(apiUrl("rixo-mapping/distinct-rixo-companies"))
        .then { response: dynamic ->
            if (response.ok) response.json() else throw js("Error('Failed to load Rixo companies')")
        }
        .then { raw: dynamic ->
            rpmRootCompanies = rpmFilterRealCompanies(parseMasterListArray(raw))
            rpmMasterCompanies = rpmFilterRealCompanies(rpmRootCompanies).sortedBy { it.lowercase() }
            rpmLeafInlineEditMappingId = null
            rpmLeafInlineEditLineType = ""
            fun finishRender() {
                root.innerHTML = buildRixoPriceMapTreeHtmlFromCache()
                bindRixoPriceMapTreeClicks(root)
                if (rpmFullRowAddOpen) {
                    window.setTimeout({ wireRpmFullRowAddComboboxes() }, 0)
                }
            }
            val selected = rpmSelectedCompany
            if (selected != null) {
                // Force reload open branch so children stay visible after save.
                rpmLoadedCompanyKeys.remove(rpmNormCompany(selected))
                ensureRpmCompanyBranchLoaded(selected) { finishRender() }
            } else {
                rpmTreeRowsCache = emptyList()
                rpmLoadedCompanyKeys.clear()
                finishRender()
            }
        }
        .catch { err: dynamic ->
            Logger.error("Rixo price map refresh: ${err.toString()}")
            showMessage("Failed to refresh Rixo price map", "error")
        }
}
