package com.automan.backend.repository

import com.automan.backend.model.UserPartyLink
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserPartyLinkRepository : JpaRepository<UserPartyLink, Long> {
    fun findByUserIdOrderByIdDesc(userId: Long): List<UserPartyLink>

    fun findByUserIdAndPartyTypeAndStatus(userId: Long, partyType: String, status: String): List<UserPartyLink>

    fun findByPartyTypeAndPartyIdAndStatus(partyType: String, partyId: Long, status: String): List<UserPartyLink>
}
