package com.automan.backend.service

import com.automan.backend.model.Booking
import com.automan.backend.model.BookingStatus
import com.automan.backend.model.dto.BookingRequest
import com.automan.backend.model.dto.BookingResponse
import com.automan.backend.repository.BookingRepository
import com.automan.backend.repository.PurchaseRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
@Transactional
class BookingService(
    private val bookingRepository: BookingRepository,
    private val purchaseRepository: PurchaseRepository
) {
    
    fun createBooking(request: BookingRequest): BookingResponse {
        // Generate unique booking number
        val bookingNumber = generateBookingNumber()
        
        // Create booking entity
        val booking = Booking(
            bookingNumber = bookingNumber,
            vesselNo = request.vesselNo,
            vesselName = request.vesselName,
            consigneeCountry = request.consigneeCountry,
            polPort = request.polPort,
            bookingDate = request.bookingDate ?: LocalDate.now(),
            status = request.status
        )
        
        val savedBooking = bookingRepository.save(booking)
        
        return mapToResponse(savedBooking)
    }
    
    fun updateBooking(id: Long, request: BookingRequest): BookingResponse {
        val existingBooking = bookingRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Booking not found with id: $id") }
        
        val updatedBooking = existingBooking.copy(
            vesselNo = request.vesselNo,
            vesselName = request.vesselName,
            consigneeCountry = request.consigneeCountry,
            polPort = request.polPort,
            bookingDate = request.bookingDate,
            status = request.status,
            updatedAt = LocalDateTime.now()
        )
        
        val savedBooking = bookingRepository.save(updatedBooking)
        
        return mapToResponse(savedBooking)
    }
    
    @Transactional(readOnly = true)
    fun getBooking(id: Long): BookingResponse {
        val booking = bookingRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Booking not found with id: $id") }
        
        return mapToResponse(booking)
    }
    
    @Transactional(readOnly = true)
    fun getBookingByNumber(bookingNumber: String): BookingResponse {
        val booking = bookingRepository.findByBookingNumber(bookingNumber)
            ?: throw IllegalArgumentException("Booking not found with number: $bookingNumber")
        
        return mapToResponse(booking)
    }
    
    @Transactional(readOnly = true)
    fun getAllBookings(): List<BookingResponse> {
        return bookingRepository.findAll().map { mapToResponse(it) }
    }
    
    @Transactional(readOnly = true)
    fun getBookingsByStatus(status: BookingStatus): List<BookingResponse> {
        return bookingRepository.findByStatus(status).map { mapToResponse(it) }
    }
    
    fun deleteBooking(id: Long): Boolean {
        if (!bookingRepository.existsById(id)) {
            throw IllegalArgumentException("Booking not found with id: $id")
        }
        
        // Remove cars from booking first
        purchaseRepository.removeCarsFromBooking(id)
        
        // Delete booking
        bookingRepository.deleteById(id)
        
        return true
    }
    
    fun updateBookingStatus(id: Long, status: BookingStatus): BookingResponse {
        val booking = bookingRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Booking not found with id: $id") }
        
        val updatedBooking = booking.copy(
            status = status,
            updatedAt = LocalDateTime.now()
        )
        
        val savedBooking = bookingRepository.save(updatedBooking)
        
        return mapToResponse(savedBooking)
    }
    
    @Transactional(readOnly = true)
    fun getBookingStatistics(): Map<String, Any> {
        val totalBookings = bookingRepository.count()
        val draftBookings = bookingRepository.countByStatus(BookingStatus.DRAFT)
        val confirmedBookings = bookingRepository.countByStatus(BookingStatus.CONFIRMED)
        val shippedBookings = bookingRepository.countByStatus(BookingStatus.SHIPPED)
        
        return mapOf(
            "totalBookings" to totalBookings,
            "draftBookings" to draftBookings,
            "confirmedBookings" to confirmedBookings,
            "shippedBookings" to shippedBookings
        )
    }
    
    private fun generateBookingNumber(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        return "BK-$timestamp"
    }
    
    private fun mapToResponse(booking: Booking): BookingResponse {
        return BookingResponse(
            id = booking.id!!,
            bookingNumber = booking.bookingNumber,
            vesselNo = booking.vesselNo,
            vesselName = booking.vesselName,
            consigneeCountry = booking.consigneeCountry,
            polPort = booking.polPort,
            bookingDate = booking.bookingDate,
            status = booking.status,
            createdAt = booking.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            updatedAt = booking.updatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
}
