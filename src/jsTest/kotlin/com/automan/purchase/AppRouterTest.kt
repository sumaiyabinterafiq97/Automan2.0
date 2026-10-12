package com.automan.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Route matching against a route string. [routeEquals] and [routeStartsWith]
 * read the live browser location, so these tests use the pure matchers they call.
 */
class AppRouterTest {

    @Test
    fun editRouteRoundTripsChassisAndRejectsOtherRoutes() {
        assertEquals("/purchase", editPurchaseRouteFromChassis("  "))
        assertEquals("/edit/NZE141-1", editPurchaseRouteFromChassis(" NZE141-1 "))
        assertEquals("/edit/A%20B%2FC", editPurchaseRouteFromChassis("A B/C"))

        assertEquals("NZE141-1", chassisFromEditRoute("/edit/NZE141-1"))
        assertEquals("A B/C", chassisFromEditRoute("/edit/A%20B%2FC"))
        assertNull(chassisFromEditRoute("/purchase"))
        assertNull(chassisFromEditRoute("/edit/"))
        assertNull(chassisFromEditRoute("/edit/?x=1"))

        assertTrue(isLegacyNumericEditRoute("/edit/42"))
        assertFalse(isLegacyNumericEditRoute("/edit/NZE141"))
        assertFalse(isLegacyNumericEditRoute("/purchase"))
    }

    @Test
    fun routeMatchersRequireThePathBoundary() {
        assertTrue(routeAtEquals("/purchase", "purchase"))
        assertTrue(routeAtEquals("/purchase", "/purchase"))
        assertFalse(routeAtEquals("/purchase", "/invoice"))

        assertTrue(routeAtStartsWith("/purchase/list", "/purchase"))
        assertTrue(routeAtStartsWith("/purchase", "purchase"))
        assertFalse(routeAtStartsWith("/purchases", "/purchase"))
        assertFalse(routeAtStartsWith("/edit/1", "/purchase"))
    }

    @Test
    fun portalHomesAndGuardsStayInsideEachRole() {
        assertEquals("/portal/client", homeRouteForRole("CLIENT"))
        assertEquals("/portal/consignee", homeRouteForRole("consignee"))
        assertEquals("/purchase", homeRouteForRole("ADMIN"))
        assertEquals("/purchase", homeRouteForRole("EDITOR"))
        assertEquals("/purchase", homeRouteForRole("VIEWER"))
        assertEquals("/purchase", homeRouteForRole(null))

        assertTrue(isOwnPortalRoute("CLIENT", "/portal/client/cars"))
        assertFalse(isOwnPortalRoute("CLIENT", "/portal/consignee"))
        assertFalse(isOwnPortalRoute("CLIENT", "/portal/clientele"))
        assertFalse(isOwnPortalRoute("CONSIGNEE", "/portal/client/me"))
        assertTrue(isOwnPortalRoute("CONSIGNEE", "/portal/consignee/history"))

        assertEquals("/portal/client", portalGuardRedirect("CLIENT", "/purchase"))
        assertEquals("/portal/client", portalGuardRedirect("CLIENT", "/booking"))
        assertEquals("/portal/client", portalGuardRedirect("CLIENT", "/invoice"))
        assertEquals("/portal/client", portalGuardRedirect("CLIENT", "/users"))
        assertEquals("/portal/client", portalGuardRedirect("CLIENT", "/portal/consignee"))
        assertEquals("/portal/consignee", portalGuardRedirect("CONSIGNEE", "/rixo"))
        assertNull(portalGuardRedirect("CLIENT", "/portal/client/invoices"))
        assertNull(portalGuardRedirect("ADMIN", "/purchase"))
        assertNull(portalGuardRedirect("VIEWER", "/master/client"))
        assertEquals("/purchase", portalGuardRedirect("ADMIN", "/portal/client"))
        assertEquals("/purchase", portalGuardRedirect("EDITOR", "/portal/consignee/ongoing"))
        assertEquals("/purchase", portalGuardRedirect("VIEWER", "/portal/client/cars"))
    }
}
