package com.automan.backend.service

import com.automan.backend.model.Booking
import com.automan.backend.model.BookingStatus
import com.automan.backend.model.dto.BookingRequest
import com.automan.backend.model.dto.BookingResponse
import com.automan.backend.repository.BookingRepository
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BookingServiceTest {

    @Mock
    private lateinit var bookingRepository: BookingRepository

    @Mock
    private lateinit var purchaseRepository: PurchaseRepository

    private lateinit var bookingService: BookingService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        bookingService = BookingService(bookingRepository, purchaseRepository)
    }

    @Test
    fun `createBooking should create a new booking successfully`() {
        // Given
        val request = BookingRequest(
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Japan",
            polPort = "Tokyo",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT
        )

        val savedBooking = Booking(
            id = 1L,
            bookingNumber = "BK-20240101120000",
            vesselNo = request.vesselNo,
            vesselName = request.vesselName,
            consigneeCountry = request.consigneeCountry,
            polPort = request.polPort,
            bookingDate = request.bookingDate!!,
            status = request.status,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.save(any())).thenReturn(savedBooking)

        // When
        val result = bookingService.createBooking(request)

        // Then
        assertNotNull(result)
        assertEquals("BK-20240101120000", result.bookingNumber)
        assertEquals("VESSEL001", result.vesselNo)
        assertEquals("Test Vessel", result.vesselName)
        assertEquals("Japan", result.consigneeCountry)
        assertEquals("Tokyo", result.polPort)
        assertEquals(BookingStatus.DRAFT, result.status)
        
        verify(bookingRepository).save(any())
    }

    @Test
    fun `updateBooking should update existing booking successfully`() {
        // Given
        val bookingId = 1L
        val existingBooking = Booking(
            id = bookingId,
            bookingNumber = "BK-20240101120000",
            vesselNo = "VESSEL001",
            vesselName = "Old Vessel",
            consigneeCountry = "Japan",
            polPort = "Tokyo",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val updateRequest = BookingRequest(
            vesselNo = "VESSEL002",
            vesselName = "Updated Vessel",
            consigneeCountry = "Korea",
            polPort = "Seoul",
            bookingDate = LocalDate.now(),
            status = BookingStatus.CONFIRMED
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(existingBooking))
        whenever(bookingRepository.save(any())).thenReturn(existingBooking)

        // When
        val result = bookingService.updateBooking(bookingId, updateRequest)

        // Then
        assertNotNull(result)
        assertEquals("VESSEL002", result.vesselNo)
        assertEquals("Updated Vessel", result.vesselName)
        assertEquals("Korea", result.consigneeCountry)
        assertEquals("Seoul", result.polPort)
        assertEquals(BookingStatus.CONFIRMED, result.status)
        
        verify(bookingRepository).findById(bookingId)
        verify(bookingRepository).save(any())
    }

    @Test
    fun `updateBooking should throw exception when booking not found`() {
        // Given
        val bookingId = 999L
        val updateRequest = BookingRequest(
            vesselNo = "VESSEL002",
            vesselName = "Updated Vessel",
            consigneeCountry = "Korea",
            polPort = "Seoul",
            bookingDate = LocalDate.now(),
            status = BookingStatus.CONFIRMED
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.empty())

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            bookingService.updateBooking(bookingId, updateRequest)
        }
        
        assertEquals("Booking not found with id: $bookingId", exception.message)
        verify(bookingRepository).findById(bookingId)
        verify(bookingRepository, never()).save(any())
    }

    @Test
    fun `getBooking should return booking when found`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-20240101120000",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Japan",
            polPort = "Tokyo",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = bookingService.getBooking(bookingId)

        // Then
        assertNotNull(result)
        assertEquals(bookingId, result.id)
        assertEquals("BK-20240101120000", result.bookingNumber)
        assertEquals("VESSEL001", result.vesselNo)
        assertEquals("Test Vessel", result.vesselName)
        assertEquals("Japan", result.consigneeCountry)
        assertEquals("Tokyo", result.polPort)
        assertEquals(BookingStatus.DRAFT, result.status)
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getBooking should throw exception when booking not found`() {
        // Given
        val bookingId = 999L
        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.empty())

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            bookingService.getBooking(bookingId)
        }
        
        assertEquals("Booking not found with id: $bookingId", exception.message)
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getBookingByNumber should return booking when found`() {
        // Given
        val bookingNumber = "BK-20240101120000"
        val booking = Booking(
            id = 1L,
            bookingNumber = bookingNumber,
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Japan",
            polPort = "Tokyo",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findByBookingNumber(bookingNumber)).thenReturn(booking)

        // When
        val result = bookingService.getBookingByNumber(bookingNumber)

        // Then
        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals(bookingNumber, result.bookingNumber)
        assertEquals("VESSEL001", result.vesselNo)
        assertEquals("Test Vessel", result.vesselName)
        
        verify(bookingRepository).findByBookingNumber(bookingNumber)
    }

    @Test
    fun `getBookingByNumber should throw exception when booking not found`() {
        // Given
        val bookingNumber = "BK-NOTFOUND"
        whenever(bookingRepository.findByBookingNumber(bookingNumber)).thenReturn(null)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            bookingService.getBookingByNumber(bookingNumber)
        }
        
        assertEquals("Booking not found with number: $bookingNumber", exception.message)
        verify(bookingRepository).findByBookingNumber(bookingNumber)
    }

    @Test
    fun `getAllBookings should return all bookings`() {
        // Given
        val bookings = listOf(
            Booking(
                id = 1L,
                bookingNumber = "BK-001",
                vesselNo = "VESSEL001",
                vesselName = "Vessel 1",
                consigneeCountry = "Japan",
                polPort = "Tokyo",
                bookingDate = LocalDate.now(),
                status = BookingStatus.DRAFT,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            ),
            Booking(
                id = 2L,
                bookingNumber = "BK-002",
                vesselNo = "VESSEL002",
                vesselName = "Vessel 2",
                consigneeCountry = "Korea",
                polPort = "Seoul",
                bookingDate = LocalDate.now(),
                status = BookingStatus.CONFIRMED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        whenever(bookingRepository.findAll()).thenReturn(bookings)

        // When
        val result = bookingService.getAllBookings()

        // Then
        assertEquals(2, result.size)
        assertEquals("BK-001", result[0].bookingNumber)
        assertEquals("BK-002", result[1].bookingNumber)
        
        verify(bookingRepository).findAll()
    }

    @Test
    fun `getBookingsByStatus should return bookings with specific status`() {
        // Given
        val status = BookingStatus.CONFIRMED
        val bookings = listOf(
            Booking(
                id = 1L,
                bookingNumber = "BK-001",
                vesselNo = "VESSEL001",
                vesselName = "Vessel 1",
                consigneeCountry = "Japan",
                polPort = "Tokyo",
                bookingDate = LocalDate.now(),
                status = status,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        )

        whenever(bookingRepository.findByStatus(status)).thenReturn(bookings)

        // When
        val result = bookingService.getBookingsByStatus(status)

        // Then
        assertEquals(1, result.size)
        assertEquals(status, result[0].status)
        
        verify(bookingRepository).findByStatus(status)
    }

    @Test
    fun `deleteBooking should delete booking successfully`() {
        // Given
        val bookingId = 1L
        whenever(bookingRepository.existsById(bookingId)).thenReturn(true)
        whenever(purchaseRepository.removeCarsFromBooking(bookingId)).thenReturn(1)

        // When
        val result = bookingService.deleteBooking(bookingId)

        // Then
        assertTrue(result)
        verify(bookingRepository).existsById(bookingId)
        verify(purchaseRepository).removeCarsFromBooking(bookingId)
        verify(bookingRepository).deleteById(bookingId)
    }

    @Test
    fun `deleteBooking should throw exception when booking not found`() {
        // Given
        val bookingId = 999L
        whenever(bookingRepository.existsById(bookingId)).thenReturn(false)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            bookingService.deleteBooking(bookingId)
        }
        
        assertEquals("Booking not found with id: $bookingId", exception.message)
        verify(bookingRepository).existsById(bookingId)
        verify(purchaseRepository, never()).removeCarsFromBooking(any())
        verify(bookingRepository, never()).deleteById(any())
    }

    @Test
    fun `updateBookingStatus should update status successfully`() {
        // Given
        val bookingId = 1L
        val newStatus = BookingStatus.CONFIRMED
        val existingBooking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Japan",
            polPort = "Tokyo",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(existingBooking))
        whenever(bookingRepository.save(any())).thenReturn(existingBooking)

        // When
        val result = bookingService.updateBookingStatus(bookingId, newStatus)

        // Then
        assertNotNull(result)
        assertEquals(newStatus, result.status)
        
        verify(bookingRepository).findById(bookingId)
        verify(bookingRepository).save(any())
    }

    @Test
    fun `getBookingStatistics should return correct statistics`() {
        // Given
        whenever(bookingRepository.count()).thenReturn(10L)
        whenever(bookingRepository.countByStatus(BookingStatus.DRAFT)).thenReturn(3L)
        whenever(bookingRepository.countByStatus(BookingStatus.CONFIRMED)).thenReturn(5L)
        whenever(bookingRepository.countByStatus(BookingStatus.SHIPPED)).thenReturn(2L)

        // When
        val result = bookingService.getBookingStatistics()

        // Then
        assertEquals(10L, result["totalBookings"])
        assertEquals(3L, result["draftBookings"])
        assertEquals(5L, result["confirmedBookings"])
        assertEquals(2L, result["shippedBookings"])
        
        verify(bookingRepository).count()
        verify(bookingRepository).countByStatus(BookingStatus.DRAFT)
        verify(bookingRepository).countByStatus(BookingStatus.CONFIRMED)
        verify(bookingRepository).countByStatus(BookingStatus.SHIPPED)
    }
}
