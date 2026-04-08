package com.automan.backend.controller

import com.automan.backend.model.User
import com.automan.backend.model.UserRole
import com.automan.backend.repository.UserRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8081", "http://localhost:9090"], allowCredentials = "true")
class UserController(
    private val userRepository: UserRepository
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

    @PostMapping
    fun createUser(@RequestBody body: CreateUserDto): ResponseEntity<UserResponseDto> {
        if (userRepository.existsByEmail(body.email)) {
            return ResponseEntity.badRequest().build()
        }
        
        val user = User(
            email = body.email.trim().lowercase(),
            name = body.name.trim(),
            passwordHash = passwordEncoder.encode(body.password),
            role = UserRole.valueOf(body.role.uppercase())
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
        
        val updatedUser = user.copy(
            email = body.email?.trim()?.lowercase() ?: user.email,
            name = body.name?.trim() ?: user.name,
            role = body.role?.let { UserRole.valueOf(it.uppercase()) } ?: user.role
        )
        val saved = userRepository.save(updatedUser)
        
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
}
