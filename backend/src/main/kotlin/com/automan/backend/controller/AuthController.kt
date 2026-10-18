package com.automan.backend.controller

import com.automan.backend.service.AuthService
import com.automan.backend.service.LoginRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import com.automan.backend.util.Logger
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import jakarta.servlet.http.HttpServletRequest

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService
) {

    data class SignupDto(val email: String, val name: String, val password: String, val role: String)
    data class LoginDto(val email: String, val password: String)
    data class SetupDto(val email: String, val name: String, val password: String)
    data class UserCountResponse(val count: Long, val isInitialized: Boolean)

    // Security tracking for setup endpoint
    private val startupTime = System.currentTimeMillis()
    @org.springframework.beans.factory.annotation.Value("\${app.auth.setup-max-attempts:5}")
    private var maxSetupAttempts: Long = 5
    private val setupTimeWindowMs = 300_000L
    private val setupAttemptTimes = ConcurrentHashMap<String, java.util.concurrent.ConcurrentLinkedQueue<Long>>()

    @PostMapping("/signup")
    @Suppress("UNUSED_PARAMETER")
    fun signup(@RequestBody body: SignupDto): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.badRequest().body(mapOf(
            "success" to false,
            "message" to "Accounts are created by an administrator."
        ))
    }

    @GetMapping("/pending-signups")
    fun listPendingSignups(): ResponseEntity<Map<String, Any>> {
        val list = authService.listPendingSignups()
        return ResponseEntity.ok(mapOf("data" to list))
    }

    /** Does not approve or reject. Review is POST /auth/signup-review with an admin session. */
    @GetMapping("/verify-signup")
    fun verifySignup(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(mapOf(
            "success" to false,
            "message" to "Signup review must be submitted from an admin session."
        ))
    }

    data class SignupReviewDto(val id: Long, val action: String)

    @PostMapping("/signup-review")
    fun reviewSignup(@RequestBody body: SignupReviewDto): ResponseEntity<Map<String, Any>> {
        return try {
            authService.reviewPending(body.id, body.action)
            val approved = body.action.trim().equals("approve", ignoreCase = true)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to if (approved) {
                    "User approved. They have been notified by email."
                } else {
                    "Signup request rejected. The user has been notified."
                }
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    @PostMapping("/logout")
    fun logout(request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        val header = request.getHeader("Authorization")?.trim().orEmpty()
        val token = if (header.startsWith("Bearer ", ignoreCase = true)) header.substring(7).trim() else ""
        authService.logout(token)
        return ResponseEntity.ok(mapOf("success" to true, "message" to "Signed out"))
    }

    @PostMapping("/login")
    fun login(@RequestBody body: LoginDto) = ResponseEntity.ok(
        authService.login(
            LoginRequest(
                email = body.email,
                password = body.password
            )
        )
    )

    @GetMapping("/users/count")
    fun getUserCount(): ResponseEntity<UserCountResponse> {
        val count = authService.getUserCount()
        return ResponseEntity.ok(UserCountResponse(count, count > 0))
    }

    @PostMapping("/setup")
    fun initialSetup(
        @RequestBody body: SetupDto,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, String>> {
        val clientIp = getClientIpAddress(request)
        return try {
            validateSetupSecurity(clientIp)
            validatePasswordStrength(body.password)
            authService.setupInitialAdmin(body.email, body.name, body.password)
            logSecurityEvent("SETUP_COMPLETED", clientIp, "First admin account created")
            ResponseEntity.ok(mapOf(
                "message" to "System setup completed successfully",
                "adminEmail" to body.email
            ))
        } catch (e: IllegalStateException) {
            val tooMany = e.message?.contains("Too many", ignoreCase = true) == true
            ResponseEntity.status(if (tooMany) HttpStatus.TOO_MANY_REQUESTS else HttpStatus.CONFLICT).body(mapOf(
                "message" to (e.message ?: "System setup already completed")
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf(
                "message" to (e.message ?: "Invalid setup request")
            ))
        }
    }

    private fun validateSetupSecurity(clientIp: String) {
        // The bootstrap row, not this count, decides whether a second Admin can be created.
        val now = System.currentTimeMillis()
        val recent = setupAttemptTimes.computeIfAbsent(clientIp) { java.util.concurrent.ConcurrentLinkedQueue() }
        recent.removeIf { now - it > setupTimeWindowMs }
        if (recent.size >= maxSetupAttempts) {
            logSecurityEvent("SETUP_ATTEMPT_RATE_LIMITED", clientIp, "Too many attempts")
            throw IllegalStateException("Too many setup attempts. Please try again later.")
        }
        recent.add(now)
        
        // Log the setup attempt for security monitoring
        logSecurityEvent("SETUP_ATTEMPT", clientIp, "Setup attempt from IP: $clientIp")
    }

    private fun validatePasswordStrength(password: String) {
        if (password.length < 8) {
            throw IllegalArgumentException("Password must be at least 8 characters long")
        }
        if (!password.any { it.isDigit() }) {
            throw IllegalArgumentException("Password must contain at least one digit")
        }
        if (!password.any { it.isUpperCase() }) {
            throw IllegalArgumentException("Password must contain at least one uppercase letter")
        }
        if (!password.any { it.isLowerCase() }) {
            throw IllegalArgumentException("Password must contain at least one lowercase letter")
        }
    }

    private fun getClientIpAddress(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return when {
            !xForwardedFor.isNullOrBlank() -> {
                val parts = xForwardedFor.split(",")
                if (parts.isNotEmpty()) parts[0].trim() else "unknown"
            }
            else -> request.remoteAddr ?: "unknown"
        }
    }

    private fun isLocalhost(ip: String): Boolean {
        return ip == "127.0.0.1" || 
               ip == "::1" || 
               ip == "0:0:0:0:0:0:0:1" || 
               ip == "localhost" || 
               ip.startsWith("192.168.") || 
               ip.startsWith("10.") ||
               ip.startsWith("172.16.") ||
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") ||
               ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") ||
               ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") ||
               ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") ||
               ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") ||
               ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") ||
               ip.startsWith("172.31.")
    }

    private fun logSecurityEvent(event: String, ip: String, details: String) {
        Logger.debug("[SECURITY] $event - IP: $ip - Details: $details - Time: ${LocalDateTime.now()}")
    }
}


