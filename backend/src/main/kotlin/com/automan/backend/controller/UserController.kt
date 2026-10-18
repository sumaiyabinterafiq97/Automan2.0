package com.automan.backend.controller

import com.automan.backend.model.User
import com.automan.backend.model.UserRole
import com.automan.backend.repository.UserRepository
import com.automan.backend.security.AuthSessionService
import com.automan.backend.service.UserViewsDto
import com.automan.backend.service.UserViewsService
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8081", "http://localhost:9090"], allowCredentials = "true")
class UserController(
    private val userRepository: UserRepository,
    private val userViewsService: UserViewsService,
    private val authSessionService: AuthSessionService,
) {
    private val passwordEncoder = BCryptPasswordEncoder()

    data class CreateUserDto(val email: String, val name: String, val password: String, val role: String)
    data class UpdateUserDto(val email: String?, val name: String?, val role: String?)
    data class UserResponseDto(val id: Long, val email: String, val name: String, val role: String, val createdAt: String)

    @GetMapping
    fun getAllUsers(): ResponseEntity<List<UserResponseDto>> {
        val users = userRepository.findAll().map { user ->
            UserResponseDto(
                id = user.id!!,
                email = user.email,
                name = user.name,
                role = user.role.name,
                createdAt = user.createdAt.toString()
            )
        }
        return ResponseEntity.ok(users)
    }

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: Long): ResponseEntity<UserResponseDto> {
        val user = userRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val userDto = UserResponseDto(
            id = user.id!!,
            email = user.email,
            name = user.name,
            role = user.role.name,
            createdAt = user.createdAt.toString()
        )
        return ResponseEntity.ok(userDto)
    }

    /** Current user's UI prefs (Purchase List sort + columns). */
    @GetMapping("/{id}/views")
    fun getUserViews(@PathVariable id: Long): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(userViewsService.getViews(id))
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    /** Merge Purchase List (and future) prefs into users.views JSON. */
    @PutMapping("/{id}/views")
    fun putUserViews(@PathVariable id: Long, @RequestBody body: UserViewsDto): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok(userViewsService.mergeViews(id, body))
        } catch (_: NoSuchElementException) {
            ResponseEntity.notFound().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "Invalid views")))
        }
    }

    @PostMapping
    fun createUser(@RequestBody body: CreateUserDto): ResponseEntity<UserResponseDto> {
        val role = creatableRole(body.role) ?: return ResponseEntity.badRequest().build()
        if (userRepository.existsByEmail(body.email)) {
            return ResponseEntity.badRequest().build()
        }

        val user = User(
            email = body.email.trim().lowercase(),
            name = body.name.trim(),
            passwordHash = passwordEncoder.encode(body.password),
            role = role
        )
        val saved = userRepository.save(user)

        val userDto = UserResponseDto(
            id = saved.id!!,
            email = saved.email,
            name = saved.name,
            role = saved.role.name,
            createdAt = saved.createdAt.toString()
        )
        return ResponseEntity.ok(userDto)
    }

    @PutMapping("/{id}")
    fun updateUser(@PathVariable id: Long, @RequestBody body: UpdateUserDto): ResponseEntity<UserResponseDto> {
        val user = userRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val previousRole = user.role
        val requestedRole = body.role?.let { creatableRole(it) ?: return ResponseEntity.badRequest().build() }
        val nextRole = requestedRole ?: previousRole
        val updatedUser = user.copy(
            email = body.email?.trim()?.lowercase() ?: user.email,
            name = body.name?.trim() ?: user.name,
            role = nextRole
            // views intentionally preserved
        )
        val saved = userRepository.save(updatedUser)
        // Compare the role captured before save. Saving the copy updates the managed user.
        if (nextRole != previousRole) {
            authSessionService.revokeAllForUser(saved.id!!)
        }

        val userDto = UserResponseDto(
            id = saved.id!!,
            email = saved.email,
            name = saved.name,
            role = saved.role.name,
            createdAt = saved.createdAt.toString()
        )
        return ResponseEntity.ok(userDto)
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ResponseEntity<Void> {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        userRepository.deleteById(id)
        return ResponseEntity.ok().build()
    }

    private fun creatableRole(raw: String): UserRole? {
        val role = try {
            UserRole.valueOf(raw.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            return null
        }
        return role.takeIf {
            it == UserRole.ADMIN || it == UserRole.EDITOR || it == UserRole.CLIENT || it == UserRole.CONSIGNEE
        }
    }
}
