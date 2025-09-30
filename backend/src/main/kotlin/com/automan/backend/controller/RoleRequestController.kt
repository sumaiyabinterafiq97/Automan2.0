package com.automan.backend.controller

import com.automan.backend.service.RoleRequestService
import com.automan.backend.service.CreateRoleRequestDto
import com.automan.backend.service.ReviewRoleRequestDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/role-requests")
@CrossOrigin(origins = ["http://localhost:8080", "http://localhost:8081", "http://localhost:9090"], allowCredentials = "true")
class RoleRequestController(
    private val roleRequestService: RoleRequestService
) {

    @PostMapping("/{userId}")
    fun createRoleRequest(
        @PathVariable userId: Long,
        @RequestBody request: CreateRoleRequestDto
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val result = roleRequestService.createRoleRequest(userId, request)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Role request created successfully",
                "data" to result
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserRoleRequests(@PathVariable userId: Long): ResponseEntity<Map<String, Any>> {
        return try {
            val requests = roleRequestService.getUserRoleRequests(userId)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to requests
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    @GetMapping("/pending")
    fun getPendingRequests(): ResponseEntity<Map<String, Any>> {
        return try {
            val requests = roleRequestService.getPendingRequests()
            ResponseEntity.ok(mapOf(
                "success" to true,
                "data" to requests
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }

    @PostMapping("/{requestId}/review/{reviewerId}")
    fun reviewRoleRequest(
        @PathVariable requestId: Long,
        @PathVariable reviewerId: Long,
        @RequestBody review: ReviewRoleRequestDto
    ): ResponseEntity<Map<String, Any>> {
        return try {
            val result = roleRequestService.reviewRoleRequest(requestId, reviewerId, review)
            ResponseEntity.ok(mapOf(
                "success" to true,
                "message" to "Role request reviewed successfully",
                "data" to result
            ))
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "message" to (e.message ?: "Unknown error")
            ))
        }
    }
}
