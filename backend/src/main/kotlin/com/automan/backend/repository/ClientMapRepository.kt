package com.automan.backend.repository

import com.automan.backend.model.ClientMap
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ClientMapRepository : JpaRepository<ClientMap, Long> {
    fun findByClientNameIgnoreCase(clientName: String): ClientMap?
}
