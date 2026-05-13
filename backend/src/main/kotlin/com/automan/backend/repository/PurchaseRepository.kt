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
    
    @Query("SELECT p FROM Purchase p WHERE p.bookingId IS NULL AND LOWER(p.chassis) LIKE LOWER(CONCAT('%', :chassis, '%'))")
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
    
    // POL by country (from purchases only) - used for booking page POL dropdown.
    // Existing RDS rows may predate purchases.pol, so fall back to stock_location only when pol is blank.
    @Query(
        value = """
            SELECT DISTINCT COALESCE(NULLIF(p.pol, ''), p.stock_location) AS effective_pol
            FROM purchases p
            WHERE p.country = :country
              AND COALESCE(NULLIF(p.pol, ''), p.stock_location) IS NOT NULL
              AND COALESCE(NULLIF(p.pol, ''), p.stock_location) <> ''
            ORDER BY effective_pol
        """,
        nativeQuery = true
    )
    fun findDistinctPolByCountry(@Param("country") country: String): List<String>
    
    // Rixo company (from purchases only)
    @Query("SELECT DISTINCT p.rixoCompany FROM Purchase p WHERE p.rixoCompany IS NOT NULL AND p.rixoCompany != '' ORDER BY p.rixoCompany")
    fun findDistinctRixoCompanies(): List<String>
    
    // Repair company (from purchases only)
    @Query("SELECT DISTINCT p.repairCompany FROM Purchase p WHERE p.repairCompany IS NOT NULL AND p.repairCompany != '' ORDER BY p.repairCompany")
    fun findDistinctRepairCompanies(): List<String>
    
    // Venue ID (from purchases only)
    @Query("SELECT DISTINCT p.venueId FROM Purchase p WHERE p.venueId IS NOT NULL AND p.venueId != '' ORDER BY p.venueId")
    fun findDistinctVenueIds(): List<String>
    
    // Filtered chassis methods - filter by shipped=false (unshipped cars only)
    // Filter by POL field, falling back to stock_location for legacy rows where POL is missing.
    @Query(
        value = """
            SELECT DISTINCT p.chassis
            FROM purchases p
            WHERE p.country = :country
              AND COALESCE(NULLIF(p.pol, ''), p.stock_location) = :polPort
              AND p.chassis IS NOT NULL
              AND p.chassis <> ''
              AND (p.shipped IS NULL OR p.shipped = FALSE)
            ORDER BY p.chassis
        """,
        nativeQuery = true
    )
    fun findFilteredChassis(@Param("country") country: String, @Param("polPort") polPort: String): List<String>

    @Query(
        value = """
            SELECT p.*
            FROM purchases p
            WHERE p.country = :country
              AND COALESCE(NULLIF(p.pol, ''), p.stock_location) = :polPort
              AND (p.shipped IS NULL OR p.shipped = FALSE)
            ORDER BY p.chassis
        """,
        nativeQuery = true
    )
    fun findFilteredPurchasesByCountryAndPol(
        @Param("country") country: String,
        @Param("polPort") polPort: String
    ): List<Purchase>
    
    // Unshipped chassis by POL (using shipped field: null or false = unshipped)
    // MySQL accepts FALSE for TINYINT(1), and this keeps the native query portable in tests.
    @Query(
        value = """
            SELECT DISTINCT p.chassis
            FROM purchases p
            WHERE COALESCE(NULLIF(p.pol, ''), p.stock_location) = :polPort
              AND (p.shipped IS NULL OR p.shipped = FALSE)
              AND p.chassis IS NOT NULL
              AND p.chassis <> ''
            ORDER BY p.chassis
        """,
        nativeQuery = true
    )
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
