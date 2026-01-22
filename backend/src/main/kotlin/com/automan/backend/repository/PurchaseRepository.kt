package com.automan.backend.repository

import com.automan.backend.model.Purchase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

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
    
    // Method to find purchases by chassis (can return multiple since chassis is no longer unique)
    fun findByChassis(chassis: String): List<Purchase>
    
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
    
    @Modifying
    @Transactional
    @Query("UPDATE Purchase p SET p.bookingId = :bookingId WHERE p.id IN :carIds")
    fun updateBookingIdForCars(@Param("bookingId") bookingId: Long, @Param("carIds") carIds: List<Long>): Int
    
    @Modifying
    @Transactional
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
    
    @Query("SELECT DISTINCT p.stockLocation FROM Purchase p WHERE p.country = :country AND p.stockLocation IS NOT NULL AND p.stockLocation != '' ORDER BY p.stockLocation")
    fun findDistinctStockLocationsByCountry(@Param("country") country: String): List<String>
    
    // Filtered chassis methods - filter by shipped=0 (unshipped cars only)
    // Simplified: removed redundant condition (shipped <> 1 is redundant when we already check for NULL or 0)
    @Query(value = "SELECT DISTINCT p.chassis FROM purchases p WHERE p.country = :country AND p.stock_location = :polPort AND p.chassis IS NOT NULL AND p.chassis != '' AND (p.shipped IS NULL OR p.shipped = 0) ORDER BY p.chassis", nativeQuery = true)
    fun findFilteredChassis(@Param("country") country: String, @Param("polPort") polPort: String): List<String>
    
    // Unshipped chassis by POL (using shipped field: null or 0 = unshipped)
    // Note: shipped is stored as TINYINT(1) in MySQL, so we check for 0 or NULL
    // Using native query to avoid Boolean/Integer comparison issues
    @Query(value = "SELECT DISTINCT p.chassis FROM purchases p WHERE p.stock_location = :polPort AND (p.shipped IS NULL OR p.shipped = 0) AND p.chassis IS NOT NULL AND p.chassis != '' ORDER BY p.chassis", nativeQuery = true)
    fun findUnshippedChassisByPolPort(@Param("polPort") polPort: String): List<String>
    
    // Invoice filtering: Find purchases by consignee, vessel, and shipment_date
    @Query("SELECT p FROM Purchase p WHERE " +
           "(:consignee IS NULL OR :consignee = '' OR p.consignee = :consignee) AND " +
           "(:vessel IS NULL OR :vessel = '' OR p.vessel = :vessel) AND " +
           "(:shipmentDate IS NULL OR :shipmentDate = '' OR p.shipmentDate = :shipmentDate)")
    fun findByConsigneeAndVesselAndShipmentDate(
        @Param("consignee") consignee: String?,
        @Param("vessel") vessel: String?,
        @Param("shipmentDate") shipmentDate: String?
    ): List<Purchase>
}
