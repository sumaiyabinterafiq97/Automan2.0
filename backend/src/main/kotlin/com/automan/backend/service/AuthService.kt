package com.automan.backend.service

import com.automan.backend.model.User
import com.automan.backend.model.UserRole
import com.automan.backend.repository.UserRepository
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class SignupRequest(val email: String, val name: String, val password: String, val role: UserRole, val createdByRole: UserRole? = null)
data class LoginRequest(val email: String, val password: String)
data class AuthResponse(val id: Long, val email: String, val name: String, val role: UserRole, val token: String)

@Service
class AuthService(
    private val userRepository: UserRepository
) {
    private val encoder = BCryptPasswordEncoder()

    @Transactional
    fun signup(req: SignupRequest): AuthResponse {
        if (userRepository.existsByEmail(req.email)) {
            throw IllegalArgumentException("Email already registered")
        }
        
        // Validate role permissions
        validateRoleCreation(req.createdByRole, req.role)
        
        val user = User(
            email = req.email.trim().lowercase(),
            name = req.name.trim(),
            passwordHash = encoder.encode(req.password),
            role = req.role
        )
        val saved = userRepository.save(user)
        val token = generateMockToken(saved)
        return AuthResponse(saved.id!!, saved.email, saved.name, saved.role, token)
    }
    
    private fun validateRoleCreation(creatorRole: UserRole?, requestedRole: UserRole) {
        // Allow role selection during signup (when creatorRole is null)
        if (creatorRole == null) {
            return // Allow any role during signup
        }
        
        val allowedRoles = when (creatorRole) {
            UserRole.ADMIN -> listOf(UserRole.VIEWER, UserRole.EDITOR, UserRole.ADMIN)
            UserRole.EDITOR -> listOf(UserRole.VIEWER, UserRole.EDITOR)
            else -> listOf(UserRole.VIEWER) // Default/VIEWER can only create VIEWER
        }
        
        if (requestedRole !in allowedRoles) {
            throw IllegalArgumentException("Insufficient permissions to create user with role: $requestedRole")
        }
    }

    @Transactional(readOnly = true)
    fun login(req: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(req.email.trim().lowercase())
            ?: throw IllegalArgumentException("Invalid credentials")
        if (!encoder.matches(req.password, user.passwordHash)) {
            throw IllegalArgumentException("Invalid credentials")
        }
        val token = generateMockToken(user)
        return AuthResponse(user.id!!, user.email, user.name, user.role, token)
    }

    fun getUserCount(): Long {
        return userRepository.count()
    }

    private fun generateMockToken(user: User): String {
        // TODO: Replace with proper JWT token generation for production
        // For now, using a more secure approach with random component
        val randomComponent = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val timestamp = System.currentTimeMillis()
        val userId = user.id ?: 0L
        // Combine with hash-like structure (not cryptographically secure, but better than predictable)
        return "tok_${userId}_${timestamp}_$randomComponent"
    }
}


