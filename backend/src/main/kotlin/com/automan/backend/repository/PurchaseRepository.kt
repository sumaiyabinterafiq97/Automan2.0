package com.automan.backend.repository

import com.automan.backend.model.Purchase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PurchaseRepository : JpaRepository<Purchase, Long> {
    
    @Query("SELECT p FROM Purchase p WHERE " +
           "LOWER(p.date) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.lotNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.chassis) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.carName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
        "LOWER(p.auctionHouse) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.stockLocation) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.rixoCompany) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.clientName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.country) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.price) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.rixoRequested) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.rixoConfirmed) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.notes) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    fun searchPurchases(@Param("searchTerm") searchTerm: String): List<Purchase>
    
    fun findByCarNameContainingIgnoreCase(carName: String): List<Purchase>
    fun findByAuctionHouseContainingIgnoreCase(auctionHouse: String): List<Purchase>
    fun findByClientNameContainingIgnoreCase(clientName: String): List<Purchase>
    fun findByDateContainingIgnoreCase(date: String): List<Purchase>
    
    // Method to check for duplicates based on lot number and chassis
    fun findByLotNumberAndChassis(lotNumber: String, chassis: String): Purchase?
    
    // Method to find all purchases with the same lot number and chassis (for duplicate checking)
    fun findAllByLotNumberAndChassis(lotNumber: String, chassis: String): List<Purchase>
    
    // Method to check for duplicates based on lot number OR chassis
    fun findAllByLotNumberOrChassis(lotNumber: String, chassis: String): List<Purchase>
}
