package com.automan.backend.repository

import com.automan.backend.model.Booking
import com.automan.backend.model.BookingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BookingRepository : JpaRepository<Booking, Long> {
    
    fun findByBookingNumber(bookingNumber: String): Booking?
    
    fun findByStatus(status: BookingStatus): List<Booking>
    
    @Query("SELECT b FROM Booking b WHERE b.status = :status ORDER BY b.createdAt DESC")
    fun findByStatusOrderByCreatedAtDesc(@Param("status") status: BookingStatus): List<Booking>
    
    @Query("SELECT b FROM Booking b WHERE b.consigneeCountry = :country AND b.status = :status")
    fun findByConsigneeCountryAndStatus(
        @Param("country") country: String, 
        @Param("status") status: BookingStatus
    ): List<Booking>
    
    @Query("SELECT b FROM Booking b WHERE b.vesselNo = :vesselNo AND b.status = :status")
    fun findByVesselNoAndStatus(
        @Param("vesselNo") vesselNo: String, 
        @Param("status") status: BookingStatus
    ): List<Booking>
    
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.status = :status")
    fun countByStatus(@Param("status") status: BookingStatus): Long
    
    @Query("SELECT b FROM Booking b WHERE b.bookingDate BETWEEN :startDate AND :endDate ORDER BY b.bookingDate DESC")
    fun findByBookingDateBetween(
        @Param("startDate") startDate: java.time.LocalDate,
        @Param("endDate") endDate: java.time.LocalDate
    ): List<Booking>
    
    fun existsByBookingNumber(bookingNumber: String): Boolean
}
