package com.automan.backend.controller

import com.automan.backend.model.UserRole
import com.automan.backend.service.AuthService
import com.automan.backend.service.LoginRequest
import com.automan.backend.service.SignupRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import com.automan.backend.util.Logger
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
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
    private val setupAttempts = ConcurrentHashMap<String, AtomicLong>()
    private val maxSetupAttempts = 5L
    private val setupTimeWindow = 300000L // 5 minutes

    @PostMapping("/signup")
    fun signup(@RequestBody body: SignupDto): ResponseEntity<Map<String, Any>> {
        return try {
            val userRole = try {
                UserRole.valueOf(body.role.uppercase())
            } catch (e: IllegalArgumentException) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Invalid role. Please select a valid role."
                ))
            }
            authService.signupPending(body.email, body.name, body.password, userRole)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Registration submitted. You will receive an email once an admin approves your account."
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    @GetMapping("/check-email")
    fun checkEmail(@RequestParam email: String): ResponseEntity<Map<String, Any>> {
        val exists = authService.emailExists(email)
        return ResponseEntity.ok(mapOf("exists" to exists))
    }

    @GetMapping("/pending-signups")
    fun listPendingSignups(): ResponseEntity<Map<String, Any>> {
        val list = authService.listPendingSignups()
        return ResponseEntity.ok(mapOf("data" to list))
    }

    @GetMapping("/verify-signup")
    fun verifySignup(
        @RequestParam token: String,
        @RequestParam action: String
    ): ResponseEntity<Map<String, Any>> {
        return try {
            when (action.lowercase()) {
                "approve" -> authService.approvePending(token)
                "reject" -> authService.rejectPending(token)
                else -> return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "message" to "Invalid action. Use approve or reject."
                ))
            }
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to if (action.lowercase() == "approve") "User approved. They have been notified by email." else "Signup request rejected. The user has been notified."
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
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
        
        // Security checks
        validateSetupSecurity(clientIp)
        
        // Validate password strength
        validatePasswordStrength(body.password)
        
        
        // Create first ADMIN user
        authService.signup(
            SignupRequest(
                email = body.email,
                name = body.name,
                password = body.password,
                role = UserRole.ADMIN,
                createdByRole = null // First admin created by system
            )
        )
        
        logSecurityEvent("SETUP_COMPLETED", clientIp, "Admin created: ${body.email}")
        
        return ResponseEntity.ok(mapOf(
            "message" to "System setup completed successfully",
            "adminEmail" to body.email
        ))
    }

    private fun validateSetupSecurity(clientIp: String) {
        // Check if system is already initialized (most important check)
        if (authService.getUserCount() > 0) {
            logSecurityEvent("SETUP_ATTEMPT_AFTER_INIT", clientIp, "System already initialized")
            throw IllegalStateException("System setup already completed")
        }
        
        // Check rate limiting (prevent brute force)
        val attempts = setupAttempts.computeIfAbsent(clientIp) { AtomicLong(0) }
        if (attempts.incrementAndGet() > maxSetupAttempts) {
            logSecurityEvent("SETUP_ATTEMPT_RATE_LIMITED", clientIp, "Too many attempts")
            throw IllegalStateException("Too many setup attempts. Please try again later.")
        }
        
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


