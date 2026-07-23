package com.automan.backend.repository

import com.automan.backend.model.ClientMap
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClientMapRepository : JpaRepository<ClientMap, Long> {
    fun findByClientNameIgnoreCase(clientName: String): ClientMap?

    @Query("SELECT DISTINCT c.clientName FROM ClientMap c ORDER BY c.clientName ASC")
    fun findDistinctClientNamesOrdered(): List<String>

    @Query(
        value = """
            SELECT c FROM ClientMap c WHERE
            LOWER(COALESCE(c.clientName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(c.country, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(c) FROM ClientMap c WHERE
            LOWER(COALESCE(c.clientName, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(c.country, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
    )
    fun searchClientMapAllFields(@Param("q") q: String, pageable: Pageable): Page<ClientMap>

    @Query(
        value = """SELECT c FROM ClientMap c WHERE LOWER(COALESCE(c.clientName, '')) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(c) FROM ClientMap c WHERE LOWER(COALESCE(c.clientName, '')) LIKE LOWER(CONCAT('%', :q, '%'))""",
    )
    fun searchClientMapClientNameContains(@Param("q") q: String, pageable: Pageable): Page<ClientMap>

    @Query(
        value = """SELECT c FROM ClientMap c WHERE LOWER(COALESCE(c.country, '')) LIKE LOWER(CONCAT('%', :q, '%'))""",
        countQuery = """SELECT count(c) FROM ClientMap c WHERE LOWER(COALESCE(c.country, '')) LIKE LOWER(CONCAT('%', :q, '%'))""",
    )
    fun searchClientMapCountryContains(@Param("q") q: String, pageable: Pageable): Page<ClientMap>
}
