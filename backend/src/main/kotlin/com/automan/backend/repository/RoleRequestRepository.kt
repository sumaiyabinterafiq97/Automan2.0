package com.automan.backend.repository

import com.automan.backend.model.RoleRequest
import com.automan.backend.model.RequestStatus
import com.automan.backend.model.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RoleRequestRepository : JpaRepository<RoleRequest, Long> {
    
    fun findByUserAndStatus(user: User, status: RequestStatus): List<RoleRequest>
    
    fun findByStatus(status: RequestStatus): List<RoleRequest>
    
    @Query("SELECT r FROM RoleRequest r WHERE r.user = :user ORDER BY r.createdAt DESC")
    fun findByUserOrderByCreatedAtDesc(user: User): List<RoleRequest>
    
    @Query("SELECT r FROM RoleRequest r WHERE r.status = 'PENDING' ORDER BY r.createdAt ASC")
    fun findPendingRequestsOrderByCreatedAt(): List<RoleRequest>
}
