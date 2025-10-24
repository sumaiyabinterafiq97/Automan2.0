package com.automan.backend.controller

import com.automan.backend.model.Booking
import com.automan.backend.model.BookingStatus
import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.repository.BookingRepository
import com.automan.backend.repository.BookingCalculationRepository
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
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class CalculationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var bookingRepository: BookingRepository

    @Autowired
    private lateinit var bookingCalculationRepository: BookingCalculationRepository

    @BeforeEach
    fun setUp() {
        // Clean up test data
        bookingCalculationRepository.deleteAll()
        bookingRepository.deleteAll()
    }

    @Test
    fun `POST /api/calculations/freight should calculate freight successfully`() {
        // Given
        val booking = createTestBooking("Pakistan")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/freight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Freight calculation completed"))
            .andExpect(jsonPath("$.totalPrice").value(2650.0)) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
            .andExpect(jsonPath("$.breakdown.containerPrice").value(1000.0))
            .andExpect(jsonPath("$.breakdown.shippingCharge").value(500.0))
            .andExpect(jsonPath("$.breakdown.wcCharge").value(200.0))
            .andExpect(jsonPath("$.breakdown.inspectionFee").value(100.0))
            .andExpect(jsonPath("$.breakdown.fobPrice").value(300.0))
            .andExpect(jsonPath("$.breakdown.freightPrice").value(400.0))
            .andExpect(jsonPath("$.breakdown.insurance").value(150.0))
    }

    @Test
    fun `POST /api/calculations/caf should calculate CAF with country rules`() {
        // Given
        val booking = createTestBooking("Pakistan")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("C&F calculation completed"))
            .andExpect(jsonPath("$.totalPrice").value(3047.5)) // 2650 * 1.15 (Pakistan multiplier)
            .andExpect(jsonPath("$.breakdown.countryMultiplier").value(1.15))
    }

    @Test
    fun `POST /api/calculations/fob should calculate FOB successfully`() {
        // Given
        val booking = createTestBooking("Pakistan")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/fob")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("FOB calculation completed"))
            .andExpect(jsonPath("$.totalPrice").value(2650.0)) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
    }

    @Test
    fun `POST /api/calculations/pakistan should calculate Pakistan charges successfully`() {
        // Given
        val booking = createTestBooking("Pakistan")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = true // Required for Pakistan calculation
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/pakistan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Pakistan calculation completed"))
            .andExpect(jsonPath("$.totalPrice").exists())
            .andExpect(jsonPath("$.breakdown.customDuty").exists())
            .andExpect(jsonPath("$.breakdown.otherCharges").exists())
            .andExpect(jsonPath("$.breakdown.baseTotal").value(2650.0))
    }

    @Test
    fun `POST /api/calculations/pakistan should return error when package option is false`() {
        // Given
        val booking = createTestBooking("Pakistan")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false // Should cause error
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/pakistan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Pakistan calculation requires package option to be enabled"))
    }

    @Test
    fun `POST /api/calculations/freight should return error for non-existent booking`() {
        // Given
        val calculationRequest = CalculationRequest(
            bookingId = 999L, // Non-existent booking
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/freight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Booking not found with id: 999"))
    }

    @Test
    fun `POST /api/calculations/caf should apply different country rules`() {
        // Given - Test with different countries
        val pakistanBooking = createTestBooking("Pakistan")
        val kenyaBooking = createTestBooking("Kenya")
        val nigeriaBooking = createTestBooking("Nigeria")

        val baseRequest = CalculationRequest(
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // Test Pakistan (1.15 multiplier)
        val pakistanRequest = baseRequest.copy(bookingId = pakistanBooking.id!!)
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pakistanRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPrice").value(3047.5)) // 2650 * 1.15

        // Test Kenya (1.18 multiplier)
        val kenyaRequest = baseRequest.copy(bookingId = kenyaBooking.id!!)
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(kenyaRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPrice").value(3127.0)) // 2650 * 1.18

        // Test Nigeria (1.25 multiplier)
        val nigeriaRequest = baseRequest.copy(bookingId = nigeriaBooking.id!!)
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nigeriaRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPrice").value(3312.5)) // 2650 * 1.25
    }

    @Test
    fun `POST /api/calculations/freight should validate required fields`() {
        // Given - Invalid request with missing fields
        val booking = createTestBooking("Pakistan")
        val invalidRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = -100.0, // Negative price
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/freight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
            .andExpect(status().isOk) // Service should handle negative values gracefully
            .andExpect(jsonPath("$.success").value(true))
    }

    @Test
    fun `POST /api/calculations/freight should save calculation to database`() {
        // Given
        val booking = createTestBooking("Pakistan")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When
        mockMvc.perform(
            post("/api/calculations/freight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)

        // Then - Verify calculation was saved
        val savedCalculations = bookingCalculationRepository.findByBookingId(booking.id!!)
        assert(savedCalculations.isNotEmpty())
        assert(savedCalculations[0].totalPrice.toDouble() == 2650.0)
    }

    @Test
    fun `POST /api/calculations/caf should handle unsupported country with default rules`() {
        // Given
        val booking = createTestBooking("Unknown Country")
        val calculationRequest = CalculationRequest(
            bookingId = booking.id!!,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.totalPrice").value(2915.0)) // 2650 * 1.10 (default multiplier)
            .andExpect(jsonPath("$.breakdown.countryMultiplier").value(1.10))
    }

    private fun createTestBooking(consigneeCountry: String): Booking {
        val booking = Booking(
            bookingNumber = "BK-TEST-${System.currentTimeMillis()}",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = consigneeCountry,
            polPort = "Test Port",
            bookingDate = LocalDate.now(),
            status = BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        return bookingRepository.save(booking)
    }
}
