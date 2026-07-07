package com.automan.purchase

import kotlinx.browser.window
import org.w3c.dom.events.Event

/** Production mount path for the Automan SPA on memon.co.jp. */
const val APP_BASE_PATH = "/automan"

private var routeUpdateListener: (() -> Unit)? = null

fun registerRouteUpdateListener(listener: () -> Unit) {
    routeUpdateListener = listener
}

fun installPopstateListener(onPopState: () -> Unit) {
    window.addEventListener("popstate", { _: Event -> onPopState() })
}

private fun notifyRouteUpdate() {
    routeUpdateListener?.invoke()
}

/** `/automan` when deployed under subpath; empty when served at site root (local dev). */
fun detectedAppBasePath(): String {
    val pathname = window.location.pathname
    return if (pathname == APP_BASE_PATH || pathname.startsWith("$APP_BASE_PATH/")) {
        APP_BASE_PATH
    } else {
        ""
    }
}

/** App route with leading slash, e.g. `/purchase`. Empty at bare app base. */
fun currentRoute(): String {
    val base = detectedAppBasePath()
    val pathname = window.location.pathname
    val rest = if (base.isNotEmpty()) pathname.removePrefix(base) else pathname
    val normalized = rest.trimEnd('/')
    return if (normalized.isEmpty()) "" else normalized
}

fun routeStartsWith(prefix: String): Boolean {
    val route = currentRoute()
    return routeAtStartsWith(route, prefix)
}

fun routeEquals(path: String): Boolean {
    return routeAtEquals(currentRoute(), path)
}

/** Route match against a captured route string (avoids re-reading URL mid-router pass). */
fun routeAtStartsWith(route: String, prefix: String): Boolean {
    val p = if (prefix.startsWith("/")) prefix else "/$prefix"
    val base = p.trimEnd('/')
    if (base.isEmpty()) return route.isNotEmpty()
    if (route == base) return true
    return route.startsWith("$base/")
}

fun routeAtEquals(route: String, path: String): Boolean {
    val p = if (path.startsWith("/")) path else "/$path"
    return route == p
}

fun isAuthRoute(): Boolean = routeEquals("/login") || routeEquals("/signup")

fun hasAuthToken(): Boolean = !safeLocalStorageGet("authToken").isNullOrBlank()

private fun buildAppFullPath(path: String): String {
    val trimmed = path.trim()
    val pathPart = trimmed.substringBefore('?')
    val queryPart = trimmed.substringAfter('?', "")
    val normalized = pathPart.let {
        when {
            it.isEmpty() -> "/"
            it.startsWith("/") -> it
            else -> "/$it"
        }
    }
    val base = detectedAppBasePath().ifEmpty {
        if (window.location.pathname.startsWith(APP_BASE_PATH)) APP_BASE_PATH else ""
    }
    val fullPath = if (base.isNotEmpty()) "$base$normalized" else normalized
    val routeQuery = if (queryPart.isNotEmpty()) "?$queryPart" else ""
    val currentSearch = window.location.search
    return if (routeQuery.isNotEmpty()) {
        fullPath + routeQuery
    } else {
        fullPath + currentSearch
    }
}

/** Full browser URL for opening an app route in a new tab (pathname routing, no hash). */
fun buildAppAbsoluteUrl(path: String): String {
    val trimmed = path.trim()
    val pathPart = trimmed.substringBefore('?')
    val queryPart = trimmed.substringAfter('?', "")
    val normalized = pathPart.let {
        when {
            it.isEmpty() -> "/"
            it.startsWith("/") -> it
            else -> "/$it"
        }
    }
    val base = detectedAppBasePath().ifEmpty {
        if (window.location.pathname.startsWith(APP_BASE_PATH)) APP_BASE_PATH else ""
    }
    val fullPath = if (base.isNotEmpty()) "$base$normalized" else normalized
    val query = if (queryPart.isNotEmpty()) "?$queryPart" else ""
    return window.location.origin + fullPath + query
}

/** Updates the browser URL without re-running the router (avoids re-fetch loops on edit). */
fun replaceAppRouteSilently(path: String) {
    val target = buildAppFullPath(path)
    val current = window.location.pathname + window.location.search
    if (current == target) return
    window.history.replaceState(null, "", target)
}

fun navigateToApp(path: String, replace: Boolean = false, forceRefresh: Boolean = false) {
    val target = buildAppFullPath(path)
    val current = window.location.pathname + window.location.search
    if (current == target) {
        if (forceRefresh) notifyRouteUpdate()
        return
    }
    if (replace) {
        window.history.replaceState(null, "", target)
    } else {
        window.history.pushState(null, "", target)
    }
    // Legacy hash routes (#/edit/ggg) must not survive pushState or migrateLegacyHash loops with pathname routing.
    if (window.location.hash.isNotEmpty()) {
        window.location.hash = ""
    }
    notifyRouteUpdate()
}

fun navigateToAppHome() {
    navigateToApp("/purchase")
}

fun editPurchaseRouteFromChassis(chassis: String): String {
    val trimmed = chassis.trim()
    if (trimmed.isEmpty()) return "/purchase"
    val encoded = js("encodeURIComponent")(trimmed).unsafeCast<String>()
    return "/edit/$encoded"
}

fun navigateToEditPurchase(chassis: String) {
    val trimmed = chassis.trim()
    if (trimmed.isEmpty()) {
        showMessage("Invalid purchase. Missing chassis.", "error")
        return
    }
    navigateToApp(editPurchaseRouteFromChassis(trimmed))
}

fun chassisFromEditRoute(route: String = currentRoute()): String? {
    if (!route.startsWith("/edit/")) return null
    val segment = route.removePrefix("/edit/").substringBefore("?").trim()
    if (segment.isEmpty()) return null
    val decoded = try {
        js("decodeURIComponent")(segment).unsafeCast<String>().trim()
    } catch (_: dynamic) {
        segment.trim()
    }
    return decoded.takeIf { it.isNotEmpty() }
}

fun isLegacyNumericEditRoute(route: String = currentRoute()): Boolean {
    if (!route.startsWith("/edit/")) return false
    val segment = route.removePrefix("/edit/")
    return segment.isNotEmpty() && segment.all { it.isDigit() }
}

fun migrateLegacyHashIfPresent(): Boolean {
    val hash = window.location.hash
    if (!hash.startsWith("#/")) return false
    val path = hash.removePrefix("#")
    val normalizedPath = path.trimEnd('/').ifEmpty { "/" }
    val current = currentRoute().trimEnd('/').ifEmpty { "/" }
    window.location.hash = ""
    if (current == normalizedPath || currentRoute().startsWith("$normalizedPath/")) {
        return false
    }
    navigateToApp(path, replace = true)
    return true
}

/**
 * Normalizes URL and enforces auth redirects.
 * @return true when navigation was adjusted and [updateContent] should return early.
 */
fun ensureAppPathOrRedirect(): Boolean {
    if (migrateLegacyHashIfPresent()) return true

    val route = currentRoute()
    val base = detectedAppBasePath()

    if (base == APP_BASE_PATH && route.isEmpty()) {
        navigateToApp("/login", replace = true)
        return true
    }

    val token = hasAuthToken()
    if (token && isAuthRoute()) {
        navigateToApp("/purchase", replace = true)
        return true
    }
    if (!token && !isAuthRoute()) {
        navigateToApp("/login", replace = true)
        return true
    }
    return false
}

fun exposeNavigateToAppOnWindow() {
    window.asDynamic().navigateToApp = { path: String ->
        navigateToApp(path)
    }
    window.asDynamic().buildAppAbsoluteUrl = { path: String ->
        buildAppAbsoluteUrl(path)
    }
}
