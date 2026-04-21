package com.automan.backend.repository

import com.automan.backend.model.ClientMap
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface ClientMapRepository : JpaRepository<ClientMap, Long> {
    fun findByClientNameIgnoreCase(clientName: String): ClientMap?

    @Query("SELECT DISTINCT c.clientName FROM ClientMap c ORDER BY c.clientName ASC")
    fun findDistinctClientNamesOrdered(): List<String>
}
