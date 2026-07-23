package com.automan.backend.repository

import com.automan.backend.model.Client
import com.automan.backend.model.ClientStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClientRepository : JpaRepository<Client, Long> {
    
    @Query("SELECT c FROM Client c WHERE " +
           "LOWER(c.clientNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(c.clientName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    fun searchClients(@Param("searchTerm") searchTerm: String): List<Client>

    @Query(
        value = "SELECT c FROM Client c WHERE " +
            "LOWER(COALESCE(c.clientNumber, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(COALESCE(c.clientName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))",
        countQuery = "SELECT count(c) FROM Client c WHERE " +
            "LOWER(COALESCE(c.clientNumber, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(COALESCE(c.clientName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))",
    )
    fun searchClientsPage(@Param("searchTerm") searchTerm: String, pageable: Pageable): Page<Client>
    
    fun findByClientNumberContainingIgnoreCase(clientNumber: String): List<Client>
    fun findByClientNameContainingIgnoreCase(clientName: String): List<Client>
    fun findByStatus(status: ClientStatus): List<Client>
    
    // Method to find clients with negative balance (debt)
    @Query("SELECT c FROM Client c WHERE c.currentBalance < 0")
    fun findClientsWithDebt(): List<Client>
    
    // Method to find clients with positive balance (credit)
    @Query("SELECT c FROM Client c WHERE c.currentBalance > 0")
    fun findClientsWithCredit(): List<Client>
    
    // Method to find clients approaching credit limit (Option A: balance ≤ −90% of limit, not yet over)
    @Query(
        "SELECT c FROM Client c WHERE c.creditLimit IS NOT NULL AND c.creditLimit > 0 " +
            "AND c.currentBalance < 0 AND c.currentBalance > -c.creditLimit " +
            "AND c.currentBalance <= -(0.9 * c.creditLimit)",
    )
    fun findClientsNearCreditLimit(): List<Client>

    // Method to find clients over credit limit (Option A: balance < −limit)
    @Query(
        "SELECT c FROM Client c WHERE c.creditLimit IS NOT NULL AND c.creditLimit > 0 " +
            "AND c.currentBalance < -c.creditLimit",
    )
    fun findClientsOverCreditLimit(): List<Client>
    
    // Method to find clients by balance range
    @Query("SELECT c FROM Client c WHERE c.currentBalance BETWEEN :minBalance AND :maxBalance")
    fun findClientsByBalanceRange(@Param("minBalance") minBalance: Double, @Param("maxBalance") maxBalance: Double): List<Client>
    
    // Method to check if client number exists
    fun existsByClientNumber(clientNumber: String): Boolean
    
    // Method to find client by client number
    fun findByClientNumber(clientNumber: String): Client?

    /** Exact match, case-insensitive (for resolving invoice PDF client name to ledger client). */
    fun findByClientNameIgnoreCase(clientName: String): List<Client>
    
    // Method to get total outstanding balance
    @Query("SELECT SUM(c.currentBalance) FROM Client c")
    fun getTotalOutstandingBalance(): java.math.BigDecimal?
}
