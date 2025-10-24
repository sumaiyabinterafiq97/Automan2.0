package com.automan.backend.repository

import com.automan.backend.model.BookingCalculation
import com.automan.backend.model.CalculationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface BookingCalculationRepository : JpaRepository<BookingCalculation, Long> {
    
    fun findByBookingId(bookingId: Long): List<BookingCalculation>
    
    fun findByBookingIdAndCalculationType(bookingId: Long, calculationType: CalculationType): BookingCalculation?
    
    @Query("SELECT bc FROM BookingCalculation bc WHERE bc.bookingId = :bookingId ORDER BY bc.createdAt DESC")
    fun findByBookingIdOrderByCreatedAtDesc(@Param("bookingId") bookingId: Long): List<BookingCalculation>
    
    @Query("SELECT bc FROM BookingCalculation bc WHERE bc.calculationType = :type ORDER BY bc.createdAt DESC")
    fun findByCalculationTypeOrderByCreatedAtDesc(@Param("type") type: CalculationType): List<BookingCalculation>
    
    @Query("SELECT SUM(bc.totalPrice) FROM BookingCalculation bc WHERE bc.bookingId = :bookingId")
    fun sumTotalPriceByBookingId(@Param("bookingId") bookingId: Long): Double?
    
    @Query("SELECT bc FROM BookingCalculation bc WHERE bc.bookingId = :bookingId AND bc.calculationType = :type ORDER BY bc.createdAt DESC LIMIT 1")
    fun findLatestByBookingIdAndCalculationType(
        @Param("bookingId") bookingId: Long, 
        @Param("type") type: CalculationType
    ): BookingCalculation?
    
    fun deleteByBookingId(bookingId: Long)
    
    fun deleteByBookingIdAndCalculationType(bookingId: Long, calculationType: CalculationType)
}
