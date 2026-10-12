package com.automan.backend.repository

import com.automan.backend.model.ClientConsigneeAssignment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClientConsigneeAssignmentRepository : JpaRepository<ClientConsigneeAssignment, Long> {
    fun findByClientIdOrderByIdDesc(clientId: Long): List<ClientConsigneeAssignment>
}
