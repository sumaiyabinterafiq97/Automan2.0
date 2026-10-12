package com.automan.backend.security

import com.automan.backend.model.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthPolicyTest {
    @Test
    fun publicPathsStayOpenAndAliasesNormalize() {
        assertTrue(AuthPolicy.isPublic("POST", "/auth/login"))
        assertTrue(AuthPolicy.isPublic("POST", "/api/auth/signup"))
        assertFalse(AuthPolicy.isPublic("GET", "/auth/verify-signup"))
        assertFalse(AuthPolicy.isPublic("GET", "/auth/users/count"))
        assertFalse(AuthPolicy.isPublic("GET", "/auth/check-email"))
        assertTrue(AuthPolicy.isPublic("GET", "/config/media"))
        assertTrue(AuthPolicy.isDebugEndpoint("/purchases/test"))
        assertTrue(AuthPolicy.isDebugEndpoint("/api/events/test-create"))
        assertFalse(AuthPolicy.isDebugEndpoint("/purchases"))
        assertFalse(AuthPolicy.isPublic("GET", "/purchases"))
        assertFalse(AuthPolicy.isPublic("GET", "/api/clients"))
        assertFalse(AuthPolicy.isPublic("GET", "/auth/pending-signups"))
        assertEquals("/clients", AuthPolicy.normalize("/api/clients"))
        assertEquals("/booking/mappings", AuthPolicy.normalize("/api/booking/mappings"))
    }

    @Test
    fun viewerCanReadAndCannotMutate() {
        assertTrue(AuthPolicy.isAllowed("GET", "/purchases/chassis/ABC", 4, UserRole.VIEWER))
        assertTrue(AuthPolicy.isAllowed("GET", "/invoice-history/INV-1/pdf", 4, UserRole.VIEWER))
        assertTrue(AuthPolicy.isAllowed("GET", "/purchases/9/media/3/url", 4, UserRole.VIEWER))
        assertTrue(AuthPolicy.isAllowed("POST", "/purchases/page-filter", 4, UserRole.VIEWER))
        assertTrue(AuthPolicy.isAllowed("PUT", "/users/4/views", 4, UserRole.VIEWER))
        assertFalse(AuthPolicy.isAllowed("POST", "/purchases", 4, UserRole.VIEWER))
        assertFalse(AuthPolicy.isAllowed("DELETE", "/purchases/9", 4, UserRole.VIEWER))
        assertFalse(AuthPolicy.isAllowed("PUT", "/users/9/views", 4, UserRole.VIEWER))
        assertFalse(AuthPolicy.isAllowed("GET", "/users", 4, UserRole.VIEWER))
        assertFalse(AuthPolicy.isAllowed("GET", "/auth/pending-signups", 4, UserRole.VIEWER))
    }

    @Test
    fun editorCanOperateButCannotAdminister() {
        assertTrue(AuthPolicy.isAllowed("POST", "/purchases", 2, UserRole.EDITOR))
        assertTrue(AuthPolicy.isAllowed("POST", "/shipping-history/batch", 2, UserRole.EDITOR))
        assertTrue(AuthPolicy.isAllowed("PUT", "/users/2/views", 2, UserRole.EDITOR))
        assertFalse(AuthPolicy.isAllowed("POST", "/users", 2, UserRole.EDITOR))
        assertFalse(AuthPolicy.isAllowed("POST", "/auth/signup-review", 2, UserRole.EDITOR))
        assertFalse(AuthPolicy.isAllowed("DELETE", "/users/8", 2, UserRole.EDITOR))
        assertFalse(AuthPolicy.isAllowed("PUT", "/admin/party-access/clients/1/consignee", 2, UserRole.EDITOR))
        assertFalse(AuthPolicy.isAllowed("GET", "/admin/party-access/users", 4, UserRole.VIEWER))
        assertTrue(AuthPolicy.isAllowed("POST", "/users", 1, UserRole.ADMIN))
        assertTrue(AuthPolicy.isAllowed("POST", "/admin/party-access/links", 1, UserRole.ADMIN))
        assertTrue(AuthPolicy.isAllowed("GET", "/api/clients", 1, UserRole.ADMIN))
    }

    @Test
    fun externalRolesAreDeniedOnStaffRoutesIncludingPostReads() {
        val probes = listOf(
            "GET" to "/purchases",
            "GET" to "/api/purchases",
            "GET" to "/purchases/page",
            "GET" to "/purchases/chassis/ABC",
            "POST" to "/purchases/by-chassis-tokens",
            "GET" to "/clients",
            "GET" to "/api/clients/5/balance",
            "GET" to "/events/export/all-clients",
            "GET" to "/invoice-history",
            "GET" to "/invoice-history/INV-1/pdf",
            "POST" to "/purchases/invoice/generate-pdf",
            "GET" to "/shipping-history",
            "GET" to "/shipping-history/export/xlsx",
            "POST" to "/shipping-history/client-shipment-details/pdf",
            "POST" to "/purchases/export/xlsx",
            "POST" to "/calculations/freight",
            "GET" to "/rixo/prices",
            "GET" to "/rixo-history",
            "POST" to "/purchases/rixo-pdf",
            "GET" to "/purchases/1/media",
            "GET" to "/purchases/1/media/3/url",
            "GET" to "/users",
            "PUT" to "/users/9/views",
            "PUT" to "/admin/party-access/clients/1/consignee",
            "POST" to "/admin/party-access/links",
        )
        for (role in listOf(UserRole.CLIENT, UserRole.CONSIGNEE)) {
            for ((method, path) in probes) {
                assertFalse(AuthPolicy.isAllowed(method, path, 9, role), "$role $method $path")
            }
            assertTrue(AuthPolicy.isAllowed("POST", "/auth/logout", 9, role))
            assertTrue(AuthPolicy.isAllowed("POST", "/api/auth/logout", 9, role))
        }
    }

    @Test
    fun portalRoutesMatchOnlyTheOwningExternalRole() {
        assertTrue(AuthPolicy.isAllowed("GET", "/portal/client/me", 9, UserRole.CLIENT))
        assertTrue(AuthPolicy.isAllowed("GET", "/api/portal/client/cars/4/media/8/url", 9, UserRole.CLIENT))
        assertTrue(AuthPolicy.isAllowed("GET", "/portal/consignee/shipments/history", 9, UserRole.CONSIGNEE))
        assertTrue(AuthPolicy.isAllowed("GET", "/api/portal/consignee/me", 9, UserRole.CONSIGNEE))

        assertFalse(AuthPolicy.isAllowed("GET", "/portal/consignee/me", 9, UserRole.CLIENT))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/client/me", 9, UserRole.CONSIGNEE))
        assertFalse(AuthPolicy.isAllowed("POST", "/portal/client/me", 9, UserRole.CLIENT))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/clientele", 9, UserRole.CLIENT))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/clients", 9, UserRole.CLIENT))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/consignees", 9, UserRole.CONSIGNEE))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/consignee-extra", 9, UserRole.CONSIGNEE))
        assertTrue(AuthPolicy.isAllowed("GET", "/portal/client/me/", 9, UserRole.CLIENT))
        assertTrue(AuthPolicy.isAllowed("GET", "/portal/consignee/me/", 9, UserRole.CONSIGNEE))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/client/me/", 9, UserRole.CONSIGNEE))
        assertFalse(AuthPolicy.isAllowed("GET", "/portal/", 9, UserRole.CLIENT))

        for (role in listOf(UserRole.ADMIN, UserRole.EDITOR, UserRole.VIEWER)) {
            assertFalse(AuthPolicy.isAllowed("GET", "/portal/client/me", 1, role), role.name)
            assertFalse(AuthPolicy.isAllowed("GET", "/portal/consignee/dashboard", 1, role), role.name)
            assertFalse(AuthPolicy.isAllowed("GET", "/api/portal/client/cars", 1, role), role.name)
        }
        assertTrue(AuthPolicy.isAllowed("GET", "/purchases", 1, UserRole.VIEWER))
        assertTrue(AuthPolicy.isAllowed("POST", "/admin/party-access/links", 1, UserRole.ADMIN))
    }
}
