package com.automan.backend.controller

import com.automan.backend.model.BookingStatus
import com.automan.backend.model.dto.BookingRequest
import com.automan.backend.repository.BookingRepository
import com.automan.backend.repository.PurchaseRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class BookingControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var bookingRepository: BookingRepository

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        // Clean up test data
        bookingRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun `POST /api/bookings should create a new booking`() {
        // Given
        val bookingRequest = BookingRequest(
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Pakistan",
            polPort = "Karachi",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT
        )

        // When & Then
        mockMvc.perform(
            post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(bookingRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Booking created successfully"))
            .andExpect(jsonPath("$.data.bookingNumber").exists())
            .andExpect(jsonPath("$.data.vesselNo").value("VESSEL001"))
            .andExpect(jsonPath("$.data.vesselName").value("Test Vessel"))
            .andExpect(jsonPath("$.data.consigneeCountry").value("Pakistan"))
            .andExpect(jsonPath("$.data.polPort").value("Karachi"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
    }

    @Test
    fun `GET /api/bookings should return all bookings`() {
        // Given - Create test bookings
        val booking1 = createTestBooking("VESSEL001", "Test Vessel 1", "Pakistan", "Karachi")
        val booking2 = createTestBooking("VESSEL002", "Test Vessel 2", "Kenya", "Mombasa")

        // When & Then
        mockMvc.perform(get("/api/bookings"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].vesselNo").value("VESSEL001"))
            .andExpect(jsonPath("$.data[1].vesselNo").value("VESSEL002"))
    }

    @Test
    fun `GET /api/bookings/{id} should return specific booking`() {
        // Given
        val booking = createTestBooking("VESSEL001", "Test Vessel", "Pakistan", "Karachi")

        // When & Then
        mockMvc.perform(get("/api/bookings/${booking.id}"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(booking.id))
            .andExpect(jsonPath("$.data.vesselNo").value("VESSEL001"))
            .andExpect(jsonPath("$.data.vesselName").value("Test Vessel"))
            .andExpect(jsonPath("$.data.consigneeCountry").value("Pakistan"))
            .andExpect(jsonPath("$.data.polPort").value("Karachi"))
    }

    @Test
    fun `GET /api/bookings/{id} should return 404 for non-existent booking`() {
        // When & Then
        mockMvc.perform(get("/api/bookings/999"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Booking not found with id: 999"))
    }

    @Test
    fun `PUT /api/bookings/{id} should update existing booking`() {
        // Given
        val booking = createTestBooking("VESSEL001", "Test Vessel", "Pakistan", "Karachi")
        val updateRequest = BookingRequest(
            vesselNo = "VESSEL002",
            vesselName = "Updated Vessel",
            consigneeCountry = "Kenya",
            polPort = "Mombasa",
            bookingDate = LocalDate.now(),
            status = BookingStatus.CONFIRMED
        )

        // When & Then
        mockMvc.perform(
            put("/api/bookings/${booking.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Booking updated successfully"))
            .andExpect(jsonPath("$.data.vesselNo").value("VESSEL002"))
            .andExpect(jsonPath("$.data.vesselName").value("Updated Vessel"))
            .andExpect(jsonPath("$.data.consigneeCountry").value("Kenya"))
            .andExpect(jsonPath("$.data.polPort").value("Mombasa"))
            .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
    }

    @Test
    fun `PUT /api/bookings/{id} should return 404 for non-existent booking`() {
        // Given
        val updateRequest = BookingRequest(
            vesselNo = "VESSEL002",
            vesselName = "Updated Vessel",
            consigneeCountry = "Kenya",
            polPort = "Mombasa",
            bookingDate = LocalDate.now(),
            status = BookingStatus.CONFIRMED
        )

        // When & Then
        mockMvc.perform(
            put("/api/bookings/999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Booking not found with id: 999"))
    }

    @Test
    fun `DELETE /api/bookings/{id} should delete existing booking`() {
        // Given
        val booking = createTestBooking("VESSEL001", "Test Vessel", "Pakistan", "Karachi")

        // When & Then
        mockMvc.perform(delete("/api/bookings/${booking.id}"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Booking deleted successfully"))
    }

    @Test
    fun `DELETE /api/bookings/{id} should return 404 for non-existent booking`() {
        // When & Then
        mockMvc.perform(delete("/api/bookings/999"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Booking not found with id: 999"))
    }

    @Test
    fun `GET /api/bookings/status/{status} should return bookings by status`() {
        // Given
        val draftBooking = createTestBooking("VESSEL001", "Draft Vessel", "Pakistan", "Karachi")
        val confirmedBooking = createTestBooking("VESSEL002", "Confirmed Vessel", "Kenya", "Mombasa")
        
        // Update one booking to CONFIRMED status
        confirmedBooking.status = BookingStatus.CONFIRMED
        bookingRepository.save(confirmedBooking)

        // When & Then
        mockMvc.perform(get("/api/bookings/status/DRAFT"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
    }

    @Test
    fun `GET /api/bookings/statistics should return booking statistics`() {
        // Given - Create bookings with different statuses
        val draftBooking = createTestBooking("VESSEL001", "Draft Vessel", "Pakistan", "Karachi")
        val confirmedBooking = createTestBooking("VESSEL002", "Confirmed Vessel", "Kenya", "Mombasa")
        confirmedBooking.status = BookingStatus.CONFIRMED
        bookingRepository.save(confirmedBooking)

        // When & Then
        mockMvc.perform(get("/api/bookings/statistics"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.totalBookings").value(2))
            .andExpect(jsonPath("$.data.draftBookings").value(1))
            .andExpect(jsonPath("$.data.confirmedBookings").value(1))
    }

    @Test
    fun `POST /api/bookings should validate required fields`() {
        // Given - Invalid booking request with missing required fields
        val invalidRequest = BookingRequest(
            vesselNo = "", // Empty vessel number
            vesselName = "", // Empty vessel name
            consigneeCountry = "", // Empty country
            polPort = "", // Empty port
            bookingDate = null,
            status = null
        )

        // When & Then
        mockMvc.perform(
            post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
    }

    private fun createTestBooking(
        vesselNo: String,
        vesselName: String,
        consigneeCountry: String,
        polPort: String
    ): com.automan.backend.model.Booking {
        val booking = com.automan.backend.model.Booking(
            bookingNumber = "BK-TEST-${System.currentTimeMillis()}",
            vesselNo = vesselNo,
            vesselName = vesselName,
            consigneeCountry = consigneeCountry,
            polPort = polPort,
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT
        )
        return bookingRepository.save(booking)
    }
}
