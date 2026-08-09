package com.automan.backend.config

import com.automan.backend.model.UserRole
import com.automan.backend.service.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthFilter(
    private val authService: AuthService,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = normalizedPath(request)

        if (request.method.equals("OPTIONS", ignoreCase = true) || isPublicPath(path)) {
            filterChain.doFilter(request, response)
            return
        }

        val authenticatedUser = authService.authenticate(request.getHeader("Authorization"))
        if (authenticatedUser == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
            return
        }

        if (isAdminPath(path) && authenticatedUser.role != UserRole.ADMIN) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Admin role required")
            return
        }

        request.setAttribute("auth.userId", authenticatedUser.id)
        request.setAttribute("auth.userRole", authenticatedUser.role.name)
        filterChain.doFilter(request, response)
    }

    private fun normalizedPath(request: HttpServletRequest): String {
        val contextPath = request.contextPath ?: ""
        val rawPath = (request.requestURI ?: "/").removePrefix(contextPath).ifBlank { "/" }
        return if (rawPath.startsWith("/api/")) rawPath.removePrefix("/api") else rawPath
    }

    private fun isPublicPath(path: String): Boolean {
        return path == "/auth/login" ||
            path == "/auth/signup" ||
            path == "/auth/check-email" ||
            path == "/auth/users/count" ||
            path == "/auth/setup" ||
            path == "/auth/verify-signup" ||
            path == "/actuator/health" ||
            path == "/actuator/info"
    }

    private fun isAdminPath(path: String): Boolean {
        return path == "/users" ||
            path.startsWith("/users/") ||
            path == "/auth/pending-signups" ||
            path == "/role-requests/pending" ||
            Regex("^/role-requests/\\d+/review/\\d+$").matches(path)
    }
}
