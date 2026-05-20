package com.automan.backend.repository

import com.automan.backend.model.Purchase
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
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

    /**
     * Prefix match on chassis — booking flow only: [bookingRequested] false/null and Rixo confirmed (`1`/`TRUE`).
     */
    @Query(
        "SELECT p FROM Purchase p WHERE p.chassis LIKE CONCAT(:prefix, '%') " +
            "AND (p.bookingRequested IS NULL OR p.bookingRequested = false) " +
            "AND UPPER(TRIM(COALESCE(p.rixoConfirmed, ''))) IN ('TRUE', '1') " +
            "ORDER BY p.chassis ASC",
    )
    fun searchByChassisPrefix(@Param("prefix") prefix: String, pageable: Pageable): List<Purchase>

    /**
     * Substring match on chassis — same eligibility as [searchByChassisPrefix] (Car Booking search).
     */
    @Query(
        "SELECT p FROM Purchase p WHERE p.chassis LIKE CONCAT('%', :q, '%') " +
            "AND (p.bookingRequested IS NULL OR p.bookingRequested = false) " +
            "AND UPPER(TRIM(COALESCE(p.rixoConfirmed, ''))) IN ('TRUE', '1') " +
            "ORDER BY p.chassis ASC",
    )
    fun searchByChassisContains(@Param("q") q: String, pageable: Pageable): List<Purchase>

    /** Paged search: chassis, car name, brand, client, supplier — substring match on each (OR). */
    @Query(
        value = """
            SELECT p FROM Purchase p WHERE
            LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))
            """,
        countQuery = """
            SELECT count(p) FROM Purchase p WHERE
            LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
            LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))
            """
    )
    fun searchPurchasesKeyFieldsContains(@Param("q") q: String, pageable: Pageable): Page<Purchase>

    /** Prefix match on chassis (index-friendly when term has no leading wildcards). */
    @Query(
        value = "SELECT p FROM Purchase p WHERE LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT(:q, '%'))",
        countQuery = "SELECT count(p) FROM Purchase p WHERE LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT(:q, '%'))"
    )
    fun searchPurchasesChassisPrefixPage(@Param("q") q: String, pageable: Pageable): Page<Purchase>

    @Query(
        value = "SELECT p FROM Purchase p WHERE LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
        countQuery = "SELECT count(p) FROM Purchase p WHERE LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%'))"
    )
    fun searchPurchasesCarNameContainsPage(@Param("q") q: String, pageable: Pageable): Page<Purchase>

    @Query(
        value = "SELECT p FROM Purchase p WHERE LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
        countQuery = "SELECT count(p) FROM Purchase p WHERE LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%'))"
    )
    fun searchPurchasesBrandContainsPage(@Param("q") q: String, pageable: Pageable): Page<Purchase>

    @Query(
        value = "SELECT p FROM Purchase p WHERE LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
        countQuery = "SELECT count(p) FROM Purchase p WHERE LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%'))"
    )
    fun searchPurchasesClientNameContainsPage(@Param("q") q: String, pageable: Pageable): Page<Purchase>

    @Query(
        value = "SELECT p FROM Purchase p WHERE LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
        countQuery = "SELECT count(p) FROM Purchase p WHERE LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))"
    )
    fun searchPurchasesSupplierContainsPage(@Param("q") q: String, pageable: Pageable): Page<Purchase>
    
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
    
    // Country-related methods (Car Booking): countries with at least one chassis Rixo-confirmed and booking not requested yet.
    @Query(
        "SELECT DISTINCT p.country FROM Purchase p " +
            "WHERE p.country IS NOT NULL AND p.country != '' " +
            "AND (p.bookingRequested IS NULL OR p.bookingRequested = false) " +
            "AND UPPER(TRIM(COALESCE(p.rixoConfirmed, ''))) IN ('TRUE', '1') " +
            "ORDER BY p.country",
    )
    fun findDistinctCountriesWithPendingBooking(): List<String>
    
    // Stock location-related methods
    @Query("SELECT DISTINCT p.stockLocation FROM Purchase p WHERE p.stockLocation IS NOT NULL AND p.stockLocation != '' ORDER BY p.stockLocation")
    fun findDistinctStockLocations(): List<String>
    
    @Query("SELECT DISTINCT p.stockLocation FROM Purchase p WHERE p.country = :country AND p.stockLocation IS NOT NULL AND p.stockLocation != '' ORDER BY p.stockLocation")
    fun findDistinctStockLocationsByCountry(@Param("country") country: String): List<String>
    
    // For booking-page filtering: booking not requested yet and Rixo confirmed.
    @Query(
        "SELECT p FROM Purchase p " +
            "WHERE (p.bookingRequested IS NULL OR p.bookingRequested = false) " +
            "AND UPPER(TRIM(COALESCE(p.rixoConfirmed, ''))) IN ('TRUE', '1') " +
            "AND LOWER(TRIM(p.country)) = LOWER(TRIM(:country)) " +
            "ORDER BY p.chassis",
    )
    fun findUnshippedPurchasesByCountryForPolFiltering(@Param("country") country: String): List<Purchase>

    // POL by country (from purchases only) - used for booking page POL dropdown
    @Query("SELECT DISTINCT p.pol FROM Purchase p " +
           "WHERE LOWER(TRIM(p.country)) = LOWER(TRIM(:country)) " +
           "AND p.pol IS NOT NULL AND p.pol != '' " +
           "ORDER BY p.pol")
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
    
    // Filtered chassis: booking not requested and Rixo confirmed (native query for raw table).
    @Query(
        value = "SELECT DISTINCT p.chassis FROM purchases p " +
            "WHERE LOWER(TRIM(p.country)) = LOWER(TRIM(:country)) " +
            "AND LOWER(TRIM(p.pol)) = LOWER(TRIM(:polPort)) " +
            "AND p.chassis IS NOT NULL AND p.chassis != '' " +
            "AND (p.booking_requested IS NULL OR p.booking_requested = 0) " +
            "AND UPPER(TRIM(COALESCE(p.rixo_confirmed, ''))) IN ('TRUE', '1') " +
            "ORDER BY p.chassis",
        nativeQuery = true,
    )
    fun findFilteredChassis(@Param("country") country: String, @Param("polPort") polPort: String): List<String>

    @Query(
        "SELECT p FROM Purchase p " +
            "WHERE LOWER(TRIM(p.country)) = LOWER(TRIM(:country)) " +
            "AND LOWER(TRIM(p.pol)) = LOWER(TRIM(:polPort)) " +
            "AND (p.bookingRequested IS NULL OR p.bookingRequested = false) " +
            "AND UPPER(TRIM(COALESCE(p.rixoConfirmed, ''))) IN ('TRUE', '1') " +
            "ORDER BY p.chassis",
    )
    fun findFilteredPurchasesByCountryAndPol(
        @Param("country") country: String,
        @Param("polPort") polPort: String,
    ): List<Purchase>

    // Chassis by POL where booking not requested and Rixo confirmed
    @Query(
        value = "SELECT DISTINCT p.chassis FROM purchases p " +
            "WHERE LOWER(TRIM(p.pol)) = LOWER(TRIM(:polPort)) " +
            "AND (p.booking_requested IS NULL OR p.booking_requested = 0) " +
            "AND UPPER(TRIM(COALESCE(p.rixo_confirmed, ''))) IN ('TRUE', '1') " +
            "AND p.chassis IS NOT NULL AND p.chassis != '' " +
            "ORDER BY p.chassis",
        nativeQuery = true,
    )
    fun findUnshippedChassisByPolPort(@Param("polPort") polPort: String): List<String>
    
    // Invoice filtering: Find purchases by consignee, vessel, and shipment_date
    @Query("SELECT p FROM Purchase p WHERE " +
           "(:consignee IS NULL OR TRIM(:consignee) = '' OR LOWER(TRIM(COALESCE(p.consignee, ''))) = LOWER(TRIM(:consignee))) AND " +
           "(:vessel IS NULL OR TRIM(:vessel) = '' OR LOWER(TRIM(COALESCE(p.vessel, ''))) = LOWER(TRIM(:vessel))) AND " +
           "(:shipmentDate IS NULL OR TRIM(:shipmentDate) = '' OR TRIM(COALESCE(p.shipmentDate, '')) = TRIM(:shipmentDate)) AND " +
           "(p.bookingRequested IS NULL OR p.bookingRequested = false) AND " +
           "(p.invoiceConfirmed IS NULL OR p.invoiceConfirmed = false)")
    fun findByConsigneeAndVesselAndShipmentDate(
        @Param("consignee") consignee: String?,
        @Param("vessel") vessel: String?,
        @Param("shipmentDate") shipmentDate: String?
    ): List<Purchase>

    // Invoice filtering: purchases by client name (client_map / purchases.client_name), vessel, shipment_date
    @Query("SELECT p FROM Purchase p WHERE " +
           "(:clientName IS NULL OR TRIM(:clientName) = '' OR LOWER(TRIM(COALESCE(p.clientName, ''))) = LOWER(TRIM(:clientName))) AND " +
           "(:vessel IS NULL OR TRIM(:vessel) = '' OR LOWER(TRIM(COALESCE(p.vessel, ''))) = LOWER(TRIM(:vessel))) AND " +
           "(:shipmentDate IS NULL OR TRIM(:shipmentDate) = '' OR TRIM(COALESCE(p.shipmentDate, '')) = TRIM(:shipmentDate)) AND " +
           "(p.bookingRequested IS NULL OR p.bookingRequested = false) AND " +
           "(p.invoiceConfirmed IS NULL OR p.invoiceConfirmed = false)")
    fun findByClientNameAndVesselAndShipmentDate(
        @Param("clientName") clientName: String?,
        @Param("vessel") vessel: String?,
        @Param("shipmentDate") shipmentDate: String?
    ): List<Purchase>

    /**
     * Distinct non-blank [Purchase.date] (VARCHAR) for Rixo "Buying date", including only rows
     * where rixo_requested is not TRUE/1 (i.e., pending requests still exist on that date).
     */
    @Query(
        "SELECT DISTINCT p.date FROM Purchase p " +
            "WHERE p.date IS NOT NULL AND p.date <> '' " +
            "AND (p.rixoRequested IS NULL OR TRIM(p.rixoRequested) = '' " +
            "OR LOWER(p.rixoRequested) NOT IN ('1', 'true'))"
    )
    fun findDistinctPurchaseDateStrings(): List<String>

    /** Match a history chassis token to purchases: exact chassis match only. */
    @Query(
        "SELECT p FROM Purchase p WHERE " +
            "LOWER(TRIM(COALESCE(p.chassis, ''))) = LOWER(TRIM(:token))"
    )
    fun findByChassisToken(@Param("token") token: String): List<Purchase>

    @Query(
        "SELECT p FROM Purchase p WHERE UPPER(TRIM(COALESCE(p.rixoConfirmed, ''))) IN ('TRUE', '1')"
    )
    fun findPurchasesWhereRixoConfirmedPositive(): List<Purchase>
}
