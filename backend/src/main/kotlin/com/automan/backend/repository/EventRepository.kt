package com.automan.backend.repository

import com.automan.backend.model.Event
import com.automan.backend.model.EventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.Optional

@Repository
interface EventRepository : JpaRepository<Event, Long> {

    fun existsByClientIdAndInvoiceNumberAndEventType(
        clientId: Long,
        invoiceNumber: String,
        eventType: EventType,
    ): Boolean

    fun findFirstByClientIdAndInvoiceNumberAndEventTypeOrderByIdDesc(
        clientId: Long,
        invoiceNumber: String,
        eventType: EventType,
    ): Optional<Event>

    fun findByClientIdAndInvoiceNumberOrderByIdDesc(
        clientId: Long,
        invoiceNumber: String,
    ): List<Event>
    
    // Find all events for a specific client
    fun findByClientIdOrderByEventDateDesc(clientId: Long): List<Event>
    
    // Find events by client and date range
    fun findByClientIdAndEventDateBetweenOrderByEventDateDesc(
        clientId: Long, 
        startDate: LocalDate, 
        endDate: LocalDate
    ): List<Event>
    
    // Find events by type
    // Removed eventType-based queries
    
    
    
    // Calculate total payments received for a client
    @Query("SELECT COALESCE(SUM(e.paymentReceived), 0) FROM Event e WHERE e.clientId = :clientId AND e.paymentReceived IS NOT NULL")
    fun calculateTotalPaymentsByClientId(@Param("clientId") clientId: Long): Double
    
    // Calculate total transaction prices for a client
    @Query("SELECT COALESCE(SUM(e.transactionPrice), 0) FROM Event e WHERE e.clientId = :clientId AND e.transactionPrice IS NOT NULL")
    fun calculateTotalTransactionPricesByClientId(@Param("clientId") clientId: Long): Double
    
    // Find events by date range
    fun findByEventDateBetweenOrderByEventDateDesc(startDate: LocalDate, endDate: LocalDate): List<Event>
    
    // Find events by description containing text
    fun findByEventDescriptionContainingIgnoreCase(description: String): List<Event>
    
    // Find events by bill number
    fun findByBillNumber(billNumber: String): List<Event>
    
    // Count events by client
    fun countByClientId(clientId: Long): Long

    fun countByClientIdAndEventType(clientId: Long, eventType: EventType): Long
    
    // Find events with specific balance range
    @Query("SELECT e FROM Event e WHERE e.runningBalance BETWEEN :minBalance AND :maxBalance ORDER BY e.eventDate DESC")
    fun findEventsByBalanceRange(@Param("minBalance") minBalance: Double, @Param("maxBalance") maxBalance: Double): List<Event>
    
    // Find events by client ID ordered by event date and creation time
    fun findByClientIdOrderByEventDateAscCreatedAtAsc(clientId: Long): List<Event>

    fun findByEventTypeInOrderByClientIdAscEventDateAsc(
        eventTypes: Collection<EventType>,
    ): List<Event>
    
    // Find events by client ID and date range
    fun findByClientIdAndEventDateBetween(clientId: Long, startDate: LocalDate, endDate: LocalDate): List<Event>
    
    // Search client events
    @Query("SELECT e FROM Event e WHERE e.clientId = :clientId AND " +
           "(LOWER(e.eventDescription) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(e.billNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    fun searchClientEvents(@Param("clientId") clientId: Long, @Param("searchTerm") searchTerm: String): List<Event>
    
    
}
