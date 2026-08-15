package com.automan.purchase

import kotlinx.browser.document

const val APP_DOCUMENT_TITLE_SUFFIX = "Automan"

private val masterSetTitleOverrides = mapOf(
    "clients" to "Client",
    "consignee" to "Consignee",
    "country" to "Country",
    "supplier" to "Supplier",
    "rixo_company" to "Rixo Company",
    "stock_location" to "Stock Location",
    "repair_company" to "Repair Company",
    "car_brands" to "Car Brands",
    "bank_accounts" to "Bank Accounts",
    "venue_id" to "Venue ID List",
    "pol" to "POL",
    "pod" to "POD",
    "fuel" to "Fuel",
    "car_grade" to "Car Grade",
    "shift" to "Car Shift",
    "type_of_vehicle" to "Type of Vehicles",
    "number_place" to "Number Place",
)

private fun formatPageTitle(pageName: String): String =
    if (pageName.isBlank()) APP_DOCUMENT_TITLE_SUFFIX else "$pageName | $APP_DOCUMENT_TITLE_SUFFIX"

private fun masterSetFieldTitle(fieldName: String): String {
    val key = fieldName.trim().lowercase()
    if (key.isEmpty()) return "Master Set"
    return masterSetTitleOverrides[key]
        ?: key.split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { token -> token.replaceFirstChar { c -> c.uppercaseChar() } }
}

/**
 * Browser tab title for an app route. Prefix matching order mirrors [updateContent].
 */
fun documentTitleForRoute(route: String): String {
    val r = route.trim().trimEnd('/')
    val page = when {
        r.isEmpty() || routeAtEquals(r, "/purchase") -> "Purchase List"
        routeAtEquals(r, "/login") -> "Sign In"
        routeAtEquals(r, "/signup") -> "Sign Up"
        routeAtEquals(r, "/home") -> "Home"
        routeAtStartsWith(r, "/add") -> "Add Purchase"
        routeAtStartsWith(r, "/edit/") -> {
            val chassis = chassisFromEditRoute(r)?.trim().orEmpty()
            if (chassis.isNotEmpty() && !isLegacyNumericEditRoute(r)) "Edit $chassis"
            else "Edit Purchase"
        }
        routeAtStartsWith(r, "/invoice-history") -> "Invoice History"
        routeAtStartsWith(r, "/client-shipment-details") -> "Client Shipment Details"
        routeAtStartsWith(r, "/recreate-invoice") || routeAtStartsWith(r, "/invoice") -> "Invoice"
        routeAtStartsWith(r, "/rixo-history") -> "Rixo History"
        routeAtStartsWith(r, "/rixo-generator") ||
            routeAtStartsWith(r, "/rixo-updater") ||
            routeAtStartsWith(r, "/rixo-transport") -> "Rixo Request"
        routeAtStartsWith(r, "/recalculate-booking/recalculation") -> "Recalculate C&F / FOB"
        routeAtStartsWith(r, "/recalculate-booking") -> "Recalculate Booking"
        routeAtStartsWith(r, "/booking/calculation") -> "C&F / FOB Calculation"
        routeAtStartsWith(r, "/booking") -> "Booking"
        routeAtStartsWith(r, "/shipping-history") -> "Shipping History"
        routeAtStartsWith(r, "/users/edit/") -> "Edit User"
        routeAtStartsWith(r, "/pending-signups") -> "Pending Signups"
        routeAtStartsWith(r, "/role-request") -> "Role Request"
        routeAtStartsWith(r, "/users/add") -> "Add User"
        routeAtStartsWith(r, "/users") -> "Users"
        routeAtStartsWith(r, "/master/client-transactions") -> "Client Transactions"
        routeAtStartsWith(r, "/master/client-map") -> "Client Map"
        routeAtStartsWith(r, "/master/client") -> "Client"
        routeAtStartsWith(r, "/master/consignee-map") -> "Consignee Map"
        routeAtStartsWith(r, "/master/consignee") -> "Consignee"
        routeAtStartsWith(r, "/master/country") -> "Country"
        routeAtStartsWith(r, "/master/shipping-charge-map") -> "Shipping Charge Map"
        routeAtStartsWith(r, "/master/stock-location-map") -> "Stock Location Map"
        routeAtStartsWith(r, "/master/supplier-map") -> "Supplier Map"
        routeAtStartsWith(r, "/master/rixo-mapping") -> "Rixo Price Map"
        routeAtStartsWith(r, "/master/supplier") -> "Supplier"
        routeAtStartsWith(r, "/master/rixo-company") -> "Rixo Company"
        routeAtStartsWith(r, "/master/stock-location") -> "Stock Location"
        routeAtStartsWith(r, "/master/repair-company") -> "Repair Company"
        routeAtStartsWith(r, "/master/car-brands-map") -> "Chassis Map"
        routeAtStartsWith(r, "/master/car-brands") -> "Car Brands"
        routeAtStartsWith(r, "/master/bank-accounts") -> "Bank Accounts"
        routeAtStartsWith(r, "/master/venue-id") -> "Venue ID List"
        routeAtStartsWith(r, "/master/pol") -> "POL"
        routeAtStartsWith(r, "/master/pod") -> "POD"
        routeAtStartsWith(r, "/master/fuel") -> "Fuel"
        routeAtStartsWith(r, "/master/car-grade") -> "Car Grade"
        routeAtStartsWith(r, "/master/car-shift") -> "Car Shift"
        routeAtStartsWith(r, "/master/type-of-vehicles") -> "Type of Vehicles"
        routeAtStartsWith(r, "/master/number-place") -> "Number Place"
        routeAtStartsWith(r, "/master/set/") -> {
            val encoded = r.removePrefix("/master/set/")
            val fieldName = try {
                js("decodeURIComponent")(encoded).unsafeCast<String>()
            } catch (_: dynamic) {
                encoded
            }
            masterSetFieldTitle(fieldName)
        }
        routeAtStartsWith(r, "/client/") -> "Client Details"
        routeAtStartsWith(r, "/clients") -> "Client Transactions"
        else -> ""
    }
    return formatPageTitle(page)
}

fun applyDocumentTitleForRoute(route: String = currentRoute()) {
    document.title = documentTitleForRoute(route)
}
