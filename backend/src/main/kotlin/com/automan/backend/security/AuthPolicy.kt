package com.automan.backend.security

import com.automan.backend.model.UserRole

/**
 * Staff authorization for the existing API.
 *
 * ADMIN, EDITOR, and VIEWER keep their current permissions.
 * CLIENT and CONSIGNEE are external roles. They are denied on every staff route.
 * CLIENT may GET /portal/client and its child paths only. Credit-limit changes stay on the staff client screen.
 * CONSIGNEE may GET /portal/consignee and its child paths only.
 * Staff roles are denied on /portal. A path such as /portal/clientele does not match the client portal.
 * Any role outside the staff set is denied as well, so a new role cannot inherit Editor access.
 * External roles may also POST /auth/logout, which revokes the presented session.
 *
 * Viewer may read and may save their own Purchase List preferences.
 * Viewer may POST a small set of non-persisting search and calculation calls the current UI already uses.
 * Editor may change operational data. Admin is required for user management and signup review.
 */
object AuthPolicy {
    const val ANONYMOUS_MESSAGE = "Authentication required"
    const val FORBIDDEN_MESSAGE = "You do not have permission to perform this action."

    private val viewerPostReads = setOf(
        "/purchases/page-filter",
        "/purchases/change-history/page-scope",
        "/purchases/by-chassis-tokens",
        "/purchases/export/xlsx",
        "/calculations/freight",
        "/calculations/caf",
        "/calculations/fob",
        "/calculations/pakistan",
    )

    private val ownViews = Regex("^/users/(\\d+)/views$")

    private val debugEndpoints = setOf(
        "/events/test",
        "/events/test-create",
        "/events/simple",
        "/events/ping",
        "/events/health",
        "/purchases/test",
        "/purchases/test-costs",
        "/purchases/simple-test",
        "/purchases/costs-test",
        "/purchases/test-import-response",
        "/clients/debug/test",
        "/upload/test",
        "/upload/simple",
        "/rixo/test",
    )

    fun normalize(rawPath: String): String {
        var path = rawPath.substringBefore('?').trim()
        if (path.isEmpty()) return "/"
        if (!path.startsWith("/")) path = "/$path"
        if (path.length > 1 && path.endsWith("/")) path = path.trimEnd('/')
        if (path.startsWith("/api/")) path = path.removePrefix("/api")
        return path.ifEmpty { "/" }
    }

    fun isPublic(method: String, path: String): Boolean {
        val m = method.uppercase()
        val p = normalize(path)
        if (m == "GET" && (p == "/config/media" || p == "/actuator/health")) return true
        if (!p.startsWith("/auth")) return false
        return when (m) {
            "POST" -> p == "/auth/login" || p == "/auth/signup" || p == "/auth/setup"
            "GET" -> false
            else -> false
        }
    }

    /**
     * Diagnostic routes that echo input or report "working" and are not part of the staff product.
     * They stay behind authentication, then the filter returns 404 so they cannot run.
     */
    fun isDebugEndpoint(path: String): Boolean {
        val p = normalize(path)
        if (p == "/test" || p.startsWith("/test/")) return true
        return p in debugEndpoints
    }

    private val staffRoles = setOf(UserRole.ADMIN, UserRole.EDITOR, UserRole.VIEWER)

    fun isAllowed(method: String, path: String, userId: Long, role: UserRole): Boolean {
        val m = method.uppercase()
        val p = normalize(path)
        if (role !in staffRoles) {
            if (m == "POST" && p == "/auth/logout") return true
            if (m == "GET" && role == UserRole.CLIENT && isClientPortal(p)) return true
            if (m == "GET" && role == UserRole.CONSIGNEE && isConsigneePortal(p)) return true
            return false
        }
        if (isPortalPath(p)) return false
        val views = ownViews.matchEntire(p)
        if (views != null && (m == "GET" || m == "PUT" || m == "HEAD")) {
            val ownerId = views.groupValues[1].toLongOrNull()
            return ownerId != null && (ownerId == userId || role == UserRole.ADMIN)
        }
        if (isAdminOnly(p)) return role == UserRole.ADMIN
        if (role == UserRole.VIEWER && m != "GET" && m != "HEAD") {
            if (m == "POST" && p in viewerPostReads) return true
            return false
        }
        return true
    }

    private fun isPortalPath(path: String): Boolean =
        path == "/portal" || path.startsWith("/portal/")

    /** Exact portal root or a slash-separated child. /portal/clientele does not match. */
    private fun isClientPortal(path: String): Boolean =
        path == "/portal/client" || path.startsWith("/portal/client/")

    private fun isConsigneePortal(path: String): Boolean =
        path == "/portal/consignee" || path.startsWith("/portal/consignee/")

    private fun isAdminOnly(path: String): Boolean {
        if (path == "/auth/pending-signups" || path == "/auth/signup-review") return true
        if (path == "/users" || (path.startsWith("/users/") && ownViews.matchEntire(path) == null)) return true
        if (path.startsWith("/admin/")) return true
        if (path.startsWith("/events/system")) return true
        if (path.startsWith("/actuator/") && path != "/actuator/health") return true
        return false
    }
}
