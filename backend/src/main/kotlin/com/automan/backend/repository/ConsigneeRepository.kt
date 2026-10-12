package com.automan.backend.repository

import com.automan.backend.model.Consignee
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConsigneeRepository : JpaRepository<Consignee, Long> {
    fun findByLegalNameContainingIgnoreCase(legalName: String): List<Consignee>
}
