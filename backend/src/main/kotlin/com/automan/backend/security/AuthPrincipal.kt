package com.automan.backend.security

import com.automan.backend.model.UserRole
import jakarta.servlet.http.HttpServletRequest

data class AuthPrincipal(
    val userId: Long,
    val role: UserRole,
    val sessionId: Long,
)

object AuthContext {
    const val REQUEST_ATTR = "com.automan.auth.principal"

    fun current(request: HttpServletRequest): AuthPrincipal? =
        request.getAttribute(REQUEST_ATTR) as? AuthPrincipal
}
