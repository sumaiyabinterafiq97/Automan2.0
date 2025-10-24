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
    
    // Method to check for duplicates based on chassis only
    fun findByChassis(chassis: String): Purchase?
    
    // Booking-related methods
    fun findByBookingId(bookingId: Long): List<Purchase>
    
    @Query("SELECT p FROM Purchase p WHERE p.bookingId IS NULL")
    fun findUnshippedCars(): List<Purchase>
    
    @Query("SELECT p FROM Purchase p WHERE p.bookingId IS NULL AND p.country = :country")
    fun findUnshippedCarsByCountry(@Param("country") country: String): List<Purchase>
    
    @Query("SELECT p FROM Purchase p WHERE p.bookingId IS NULL AND p.stockLocation = :polPort")
    fun findUnshippedCarsByPolPort(@Param("polPort") polPort: String): List<Purchase>
    
    @Query("SELECT p FROM Purchase p WHERE p.bookingId IS NULL AND p.country = :country AND p.stockLocation = :polPort")
    fun findUnshippedCarsByCountryAndPolPort(
        @Param("country") country: String, 
        @Param("polPort") polPort: String
    ): List<Purchase>
    
    @Query("SELECT p FROM Purchase p WHERE p.bookingId IS NULL AND p.chassis LIKE %:chassis%")
    fun findUnshippedCarsByChassisContaining(@Param("chassis") chassis: String): List<Purchase>
    
    @Query("UPDATE Purchase p SET p.bookingId = :bookingId WHERE p.id IN :carIds")
    fun updateBookingIdForCars(@Param("bookingId") bookingId: Long, @Param("carIds") carIds: List<Long>): Int
    
    @Query("UPDATE Purchase p SET p.bookingId = NULL WHERE p.bookingId = :bookingId")
    fun removeCarsFromBooking(@Param("bookingId") bookingId: Long): Int
    
    // Cost-related methods
    
    @Query("SELECT p FROM Purchase p WHERE p.chassis = :chassis")
    fun findCostDetailsByChassis(@Param("chassis") chassis: String): Purchase?
    
    // Country-related methods
    @Query("SELECT DISTINCT p.country FROM Purchase p WHERE p.country IS NOT NULL AND p.country != '' ORDER BY p.country")
    fun findDistinctCountries(): List<String>
    
    // Stock location-related methods
    @Query("SELECT DISTINCT p.stockLocation FROM Purchase p WHERE p.stockLocation IS NOT NULL AND p.stockLocation != '' ORDER BY p.stockLocation")
    fun findDistinctStockLocations(): List<String>
    
    // Filtered chassis methods
    @Query("SELECT DISTINCT p.chassis FROM Purchase p WHERE p.country = :country AND p.stockLocation = :polPort AND p.bookingId IS NULL ORDER BY p.chassis")
    fun findFilteredChassis(@Param("country") country: String, @Param("polPort") polPort: String): List<String>
}
