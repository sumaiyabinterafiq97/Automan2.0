package com.automan.backend.repository

import com.automan.backend.model.Purchase
import com.automan.backend.service.PurchaseWorkflowService
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
           "LOWER(p.totalPrice) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(COALESCE(CAST(p.workflowStatus AS string), '')) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(COALESCE(p.extendedAttributesJson, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    fun searchPurchases(@Param("searchTerm") searchTerm: String): List<Purchase>

    /**
     * Prefix match on chassis — booking flow only: [bookingRequested] false/null and Rixo confirmed
     * (legacy flag or [Purchase.workflowStatus] at/after RIXO_CONFIRMED).
     */
    @Query(
        "SELECT p FROM Purchase p WHERE p.chassis LIKE CONCAT(:prefix, '%') " +
            "AND ${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.JPQL_RIXO_CONFIRMED_ELIGIBILITY} " +
            "ORDER BY p.chassis ASC",
    )
    fun searchByChassisPrefix(@Param("prefix") prefix: String, pageable: Pageable): List<Purchase>

    /**
     * Substring match on chassis — same eligibility as [searchByChassisPrefix] (Car Booking search).
     */
    @Query(
        "SELECT p FROM Purchase p WHERE p.chassis LIKE CONCAT('%', :q, '%') " +
            "AND ${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.JPQL_RIXO_CONFIRMED_ELIGIBILITY} " +
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

    @Query(
        "SELECT p FROM Purchase p WHERE LOWER(TRIM(p.chassis)) = LOWER(TRIM(:chassis))",
    )
    fun findByChassisIgnoreCaseTrim(@Param("chassis") chassis: String): List<Purchase>
    
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
    
    // Country-related methods (Car Booking): countries with at least one Rixo-confirmed, booking-not-requested chassis.
    @Query(
        "SELECT DISTINCT p.country FROM Purchase p " +
            "WHERE p.country IS NOT NULL AND p.country != '' " +
            "AND ${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.JPQL_RIXO_CONFIRMED_ELIGIBILITY} " +
            "ORDER BY p.country",
    )
    fun findDistinctCountriesWithPendingBooking(): List<String>
    
    // Stock location-related methods
    @Query("SELECT DISTINCT p.stockLocation FROM Purchase p WHERE p.stockLocation IS NOT NULL AND p.stockLocation != '' ORDER BY p.stockLocation")
    fun findDistinctStockLocations(): List<String>
    
    @Query("SELECT DISTINCT p.stockLocation FROM Purchase p WHERE p.country = :country AND p.stockLocation IS NOT NULL AND p.stockLocation != '' ORDER BY p.stockLocation")
    fun findDistinctStockLocationsByCountry(@Param("country") country: String): List<String>
    
    // For booking-page filtering: booking not requested yet and Rixo confirmed (legacy or workflow).
    @Query(
        "SELECT p FROM Purchase p " +
            "WHERE ${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.JPQL_RIXO_CONFIRMED_ELIGIBILITY} " +
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
    @Query(
        value = """
            SELECT DISTINCT JSON_UNQUOTE(JSON_EXTRACT(p.extended_attributes, '$.venueId')) AS venue_id
            FROM purchases p
            WHERE p.extended_attributes IS NOT NULL
              AND JSON_UNQUOTE(JSON_EXTRACT(p.extended_attributes, '$.venueId')) IS NOT NULL
              AND TRIM(JSON_UNQUOTE(JSON_EXTRACT(p.extended_attributes, '$.venueId'))) <> ''
            ORDER BY venue_id
        """,
        nativeQuery = true,
    )
    fun findDistinctVenueIds(): List<String>
    
    // Filtered chassis: booking not requested and Rixo confirmed (native query for raw table).
    @Query(
        value = "SELECT DISTINCT p.chassis FROM purchases p " +
            "WHERE LOWER(TRIM(p.country)) = LOWER(TRIM(:country)) " +
            "AND LOWER(TRIM(p.pol)) = LOWER(TRIM(:polPort)) " +
            "AND p.chassis IS NOT NULL AND p.chassis != '' " +
            "AND ${PurchaseWorkflowService.SQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.SQL_RIXO_CONFIRMED_ELIGIBILITY} " +
            "ORDER BY p.chassis",
        nativeQuery = true,
    )
    fun findFilteredChassis(@Param("country") country: String, @Param("polPort") polPort: String): List<String>

    @Query(
        "SELECT p FROM Purchase p " +
            "WHERE LOWER(TRIM(p.country)) = LOWER(TRIM(:country)) " +
            "AND LOWER(TRIM(p.pol)) = LOWER(TRIM(:polPort)) " +
            "AND ${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.JPQL_RIXO_CONFIRMED_ELIGIBILITY} " +
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
            "AND ${PurchaseWorkflowService.SQL_BOOKING_NOT_REQUESTED} " +
            "AND ${PurchaseWorkflowService.SQL_RIXO_CONFIRMED_ELIGIBILITY} " +
            "AND p.chassis IS NOT NULL AND p.chassis != '' " +
            "ORDER BY p.chassis",
        nativeQuery = true,
    )
    fun findUnshippedChassisByPolPort(@Param("polPort") polPort: String): List<String>
    
    // Invoice filtering: consignee candidates (vessel/date matched post read-adapter in PurchaseService)
    @Query(
        "SELECT p FROM Purchase p WHERE " +
            "(:consignee IS NULL OR TRIM(:consignee) = '' OR LOWER(TRIM(COALESCE(p.consignee, ''))) = LOWER(TRIM(:consignee))) AND " +
            "${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} AND " +
            "${PurchaseWorkflowService.JPQL_INVOICE_NOT_CONFIRMED}",
    )
    fun findInvoiceFilterCandidatesByConsignee(@Param("consignee") consignee: String?): List<Purchase>

    // Invoice filtering: client name candidates (vessel/date matched post read-adapter in PurchaseService)
    @Query(
        "SELECT p FROM Purchase p WHERE " +
            "(:clientName IS NULL OR TRIM(:clientName) = '' OR LOWER(TRIM(COALESCE(p.clientName, ''))) = LOWER(TRIM(:clientName))) AND " +
            "${PurchaseWorkflowService.JPQL_BOOKING_NOT_REQUESTED} AND " +
            "${PurchaseWorkflowService.JPQL_INVOICE_NOT_CONFIRMED}",
    )
    fun findInvoiceFilterCandidatesByClientName(@Param("clientName") clientName: String?): List<Purchase>

    /** Match a history chassis token to purchases: exact chassis match only. */
    @Query(
        "SELECT p FROM Purchase p WHERE " +
            "LOWER(TRIM(COALESCE(p.chassis, ''))) = LOWER(TRIM(:token))"
    )
    fun findByChassisToken(@Param("token") token: String): List<Purchase>

    /**
     * Lightweight projection for Rixo buying-date dropdown (avoids loading full Purchase rows).
     * Pending Rixo = workflow not yet RIXO_REQUESTED or later (see PurchaseWorkflowService.applyForRead).
     */
    @Query(
        "SELECT p.date AS date, p.workflowStatus AS workflowStatus FROM Purchase p " +
            "WHERE p.date IS NOT NULL AND TRIM(p.date) <> ''",
    )
    fun findDateAndWorkflowPairs(): List<PurchaseDateWorkflowProjection>

    /** Lightweight id+date for purchase-list date filtering (parse labels in service). */
    @Query("SELECT p.id AS id, p.date AS date FROM Purchase p")
    fun findIdAndDateAll(): List<PurchaseIdDateProjection>

    @Query(
        """
        SELECT p.id AS id, p.date AS date FROM Purchase p WHERE
        LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%')) OR
        LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))
        """,
    )
    fun searchPurchasesKeyFieldsIdDate(@Param("q") q: String): List<PurchaseIdDateProjection>

    @Query(
        "SELECT p.id AS id, p.date AS date FROM Purchase p WHERE LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT(:q, '%'))",
    )
    fun searchPurchasesChassisPrefixIdDate(@Param("q") q: String): List<PurchaseIdDateProjection>

    @Query(
        "SELECT p.id AS id, p.date AS date FROM Purchase p WHERE LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun searchPurchasesCarNameContainsIdDate(@Param("q") q: String): List<PurchaseIdDateProjection>

    @Query(
        "SELECT p.id AS id, p.date AS date FROM Purchase p WHERE LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun searchPurchasesBrandContainsIdDate(@Param("q") q: String): List<PurchaseIdDateProjection>

    @Query(
        "SELECT p.id AS id, p.date AS date FROM Purchase p WHERE LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun searchPurchasesClientNameContainsIdDate(@Param("q") q: String): List<PurchaseIdDateProjection>

    @Query(
        "SELECT p.id AS id, p.date AS date FROM Purchase p WHERE LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun searchPurchasesSupplierContainsIdDate(@Param("q") q: String): List<PurchaseIdDateProjection>

    /** Paged hydrate after date/advanced filter ID narrowing. */
    fun findByIdIn(ids: Collection<Long>, pageable: Pageable): Page<Purchase>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.chassis,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsChassisContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.brand,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsBrandContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.carName,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsCarNameContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.auctionHouse,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsAuctionHouseContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.stockLocation,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsStockLocationContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.pol,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsPolContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.pod,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsPodContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.rixoCompany,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsRixoCompanyContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.clientName,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsClientNameContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.consignee,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsConsigneeContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.country,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsCountryContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.totalPrice,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsTotalPriceContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.repairCompany,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsRepairCompanyContains(@Param("q") q: String): List<Long>

    @Query("SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(p.manufactureYear,'')) LIKE LOWER(CONCAT('%', :q, '%'))")
    fun findIdsManufactureYearContains(@Param("q") q: String): List<Long>

    @Query(
        "SELECT p.id FROM Purchase p WHERE p.bookingId IS NOT NULL AND " +
            "LOWER(CONCAT('', p.bookingId)) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun findIdsBookingIdContains(@Param("q") q: String): List<Long>

    @Query(
        "SELECT p.id FROM Purchase p WHERE LOWER(COALESCE(CAST(p.workflowStatus AS string), '')) LIKE LOWER(CONCAT('%', :q, '%'))",
    )
    fun findIdsWorkflowStatusContains(@Param("q") q: String): List<Long>

    @Query(
        "SELECT p FROM Purchase p WHERE p.workflowStatus IN (" +
            "com.automan.backend.model.WorkflowStatus.RIXO_CONFIRMED, " +
            "com.automan.backend.model.WorkflowStatus.BOOKING_REQUESTED, " +
            "com.automan.backend.model.WorkflowStatus.INVOICE_CONFIRMED)",
    )
    fun findPurchasesWhereRixoConfirmedPositive(): List<Purchase>

    /**
     * Lightweight rows for Home dashboard aggregations (avoids loading full Purchase entities).
     */
    @Query(
        "SELECT p.id AS id, p.date AS date, p.chassis AS chassis, p.brand AS brand, " +
            "p.carName AS carName, p.auctionHouse AS auctionHouse, p.clientName AS clientName, " +
            "p.country AS country, p.totalPrice AS totalPrice, p.workflowStatus AS workflowStatus " +
            "FROM Purchase p",
    )
    fun findDashboardRows(): List<DashboardPurchaseRowProjection>
}

/** Closed projection: purchase date string + workflow (rixo_requested is @Transient). */
interface PurchaseDateWorkflowProjection {
    fun getDate(): String?
    fun getWorkflowStatus(): com.automan.backend.model.WorkflowStatus?
}

/** Closed projection: purchase id + date label for server-side date range filtering. */
interface PurchaseIdDateProjection {
    fun getId(): Long?
    fun getDate(): String?
}

/** Closed projection: fields needed for Home dashboard KPIs, charts, and tables. */
interface DashboardPurchaseRowProjection {
    fun getId(): Long?
    fun getDate(): String?
    fun getChassis(): String?
    fun getBrand(): String?
    fun getCarName(): String?
    fun getAuctionHouse(): String?
    fun getClientName(): String?
    fun getCountry(): String?
    fun getTotalPrice(): String?
    fun getWorkflowStatus(): com.automan.backend.model.WorkflowStatus?
}
