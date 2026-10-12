package com.automan.backend.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

@Configuration
class AuthFilterConfig(
    private val authSessionService: AuthSessionService,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun authFilterRegistration(): FilterRegistrationBean<OncePerRequestFilter> {
        val registration = FilterRegistrationBean<OncePerRequestFilter>()
        registration.filter = SessionAuthFilter(authSessionService, objectMapper)
        registration.order = 1
        registration.addUrlPatterns("/*")
        return registration
    }
}

class SessionAuthFilter(
    private val authSessionService: AuthSessionService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.method.equals("OPTIONS", ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }
        val path = requestPath(request)
        if (AuthPolicy.isPublic(request.method, path)) {
            filterChain.doFilter(request, response)
            return
        }
        // X-Automan-Test-Anonymous is not read here. Only the test MockMvc customizer understands it.
        val principal = authSessionService.authenticate(bearerToken(request))
        if (principal == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, AuthPolicy.ANONYMOUS_MESSAGE)
            return
        }
        if (AuthPolicy.isDebugEndpoint(path)) {
            writeError(response, HttpServletResponse.SC_NOT_FOUND, "Not found")
            return
        }
        if (!AuthPolicy.isAllowed(request.method, path, principal.userId, principal.role)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN, AuthPolicy.FORBIDDEN_MESSAGE)
            return
        }
        request.setAttribute(AuthContext.REQUEST_ATTR, principal)
        filterChain.doFilter(request, response)
    }

    private fun bearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization")?.trim().orEmpty()
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        return header.substring(7).trim().ifEmpty { null }
    }

    private fun requestPath(request: HttpServletRequest): String {
        val uri = request.requestURI ?: "/"
        val context = request.contextPath ?: ""
        return if (context.isNotEmpty() && uri.startsWith(context)) uri.removePrefix(context) else uri
    }

    private fun writeError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        objectMapper.writeValue(
            response.writer,
            mapOf("success" to false, "message" to message),
        )
    }
}
