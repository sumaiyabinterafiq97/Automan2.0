package com.automan.backend.service

import com.automan.backend.model.RoleRequest
import com.automan.backend.model.RequestStatus
import com.automan.backend.model.User
import com.automan.backend.model.UserRole
import com.automan.backend.repository.RoleRequestRepository
import com.automan.backend.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

data class RoleRequestDto(
    val id: Long? = null,
    val userId: Long,
    val userName: String? = null,
    val userEmail: String? = null,
    val currentRole: String? = null,
    val requestedRole: String,
    val reason: String? = null,
    val status: String = "PENDING",
    val reviewedBy: Long? = null,
    val reviewComment: String? = null,
    val createdAt: String? = null,
    val reviewedAt: String? = null
)

data class CreateRoleRequestDto(
    val requestedRole: String,
    val reason: String? = null,
    val adminId: Long? = null
)

data class ReviewRoleRequestDto(
    val status: String,
    val reviewComment: String? = null
)

@Service
class RoleRequestService(
    private val roleRequestRepository: RoleRequestRepository,
    private val userRepository: UserRepository
) {

    @Transactional
    fun createRoleRequest(userId: Long, request: CreateRoleRequestDto): RoleRequestDto {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        val requestedRole = try {
            UserRole.valueOf(request.requestedRole.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid role: ${request.requestedRole}")
        }
        
        // Check if user already has this role
        if (user.role == requestedRole) {
            throw IllegalArgumentException("User already has the requested role")
        }
        
        // Check if there's already a pending request for this role
        val existingRequest = roleRequestRepository.findByUserAndStatus(user, RequestStatus.PENDING)
            .find { it.requestedRole == requestedRole }
        
        if (existingRequest != null) {
            throw IllegalArgumentException("You already have a pending request for this role")
        }
        
        // Validate role escalation (VIEWER can request EDITOR/ADMIN, EDITOR can request ADMIN)
        val currentRole = user.role
        val canRequest = when (currentRole) {
            UserRole.VIEWER -> requestedRole in listOf(UserRole.EDITOR, UserRole.ADMIN)
            UserRole.EDITOR -> requestedRole == UserRole.ADMIN
            UserRole.ADMIN -> false // Admin can't request higher role
        }
        
        if (!canRequest) {
            throw IllegalArgumentException("Invalid role escalation from $currentRole to $requestedRole")
        }
        
        val roleRequest = RoleRequest(
            user = user,
            requestedRole = requestedRole,
            reason = request.reason
        )
        
        val saved = roleRequestRepository.save(roleRequest)
        return mapToDto(saved)
    }

    @Transactional(readOnly = true)
    fun getUserRoleRequests(userId: Long): List<RoleRequestDto> {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("User not found") }
        
        return roleRequestRepository.findByUserOrderByCreatedAtDesc(user)
            .map { mapToDto(it) }
    }

    @Transactional(readOnly = true)
    fun getPendingRequests(): List<RoleRequestDto> {
        return roleRequestRepository.findPendingRequestsOrderByCreatedAt()
            .map { mapToDto(it) }
    }

    @Transactional
    fun reviewRoleRequest(requestId: Long, reviewerId: Long, review: ReviewRoleRequestDto): RoleRequestDto {
        val roleRequest = roleRequestRepository.findById(requestId)
            .orElseThrow { IllegalArgumentException("Role request not found") }
        
        val reviewer = userRepository.findById(reviewerId)
            .orElseThrow { IllegalArgumentException("Reviewer not found") }
        
        // Only ADMINs can review role requests
        if (reviewer.role != UserRole.ADMIN) {
            throw IllegalArgumentException("Only ADMINs can review role requests")
        }
        
        // Can't review already processed requests
        if (roleRequest.status != RequestStatus.PENDING) {
            throw IllegalArgumentException("Request has already been processed")
        }
        
        val newStatus = try {
            RequestStatus.valueOf(review.status.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid status: ${review.status}")
        }
        
        val updatedRequest = roleRequest.copy(
            status = newStatus,
            reviewedBy = reviewer,
            reviewComment = review.reviewComment,
            reviewedAt = LocalDateTime.now()
        )
        
        val saved = roleRequestRepository.save(updatedRequest)
        
        // If approved, update user's role
        if (newStatus == RequestStatus.APPROVED) {
            val user = roleRequest.user.copy(role = roleRequest.requestedRole)
            userRepository.save(user)
        }
        
        return mapToDto(saved)
    }

    private fun mapToDto(roleRequest: RoleRequest): RoleRequestDto {
        return RoleRequestDto(
            id = roleRequest.id,
            userId = roleRequest.user.id!!,
            userName = roleRequest.user.name,
            userEmail = roleRequest.user.email,
            currentRole = roleRequest.user.role.name,
            requestedRole = roleRequest.requestedRole.name,
            reason = roleRequest.reason,
            status = roleRequest.status.name,
            reviewedBy = roleRequest.reviewedBy?.id,
            reviewComment = roleRequest.reviewComment,
            createdAt = roleRequest.createdAt.toString(),
            reviewedAt = roleRequest.reviewedAt?.toString()
        )
    }
}
