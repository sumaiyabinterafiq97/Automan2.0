package com.automan.backend.service

import com.automan.backend.model.Booking
import com.automan.backend.repository.BookingRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountryRulesServiceTest {

    @Mock
    private lateinit var bookingRepository: BookingRepository

    private lateinit var countryRulesService: CountryRulesService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        countryRulesService = CountryRulesService(bookingRepository)
    }

    @Test
    fun `getCountryRules should return Pakistan rules for Pakistan booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Pakistan",
            polPort = "Karachi",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.15, result["multiplier"])
        assertEquals(0.25, result["customDutyRate"])
        assertEquals(0.08, result["otherChargesRate"])
        assertEquals(1.1, result["shippingMultiplier"])
        assertEquals(1.05, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return South Africa rules for South Africa booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "South Africa",
            polPort = "Cape Town",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.12, result["multiplier"])
        assertEquals(0.20, result["customDutyRate"])
        assertEquals(0.06, result["otherChargesRate"])
        assertEquals(1.08, result["shippingMultiplier"])
        assertEquals(1.03, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return Kenya rules for Kenya booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Kenya",
            polPort = "Mombasa",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.18, result["multiplier"])
        assertEquals(0.30, result["customDutyRate"])
        assertEquals(0.10, result["otherChargesRate"])
        assertEquals(1.12, result["shippingMultiplier"])
        assertEquals(1.06, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return Tanzania rules for Tanzania booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Tanzania",
            polPort = "Dar es Salaam",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.20, result["multiplier"])
        assertEquals(0.35, result["customDutyRate"])
        assertEquals(0.12, result["otherChargesRate"])
        assertEquals(1.15, result["shippingMultiplier"])
        assertEquals(1.08, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return Uganda rules for Uganda booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Uganda",
            polPort = "Kampala",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.22, result["multiplier"])
        assertEquals(0.40, result["customDutyRate"])
        assertEquals(0.15, result["otherChargesRate"])
        assertEquals(1.18, result["shippingMultiplier"])
        assertEquals(1.10, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return Ghana rules for Ghana booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Ghana",
            polPort = "Accra",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.16, result["multiplier"])
        assertEquals(0.25, result["customDutyRate"])
        assertEquals(0.08, result["otherChargesRate"])
        assertEquals(1.10, result["shippingMultiplier"])
        assertEquals(1.05, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return Nigeria rules for Nigeria booking`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Nigeria",
            polPort = "Lagos",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.25, result["multiplier"])
        assertEquals(0.45, result["customDutyRate"])
        assertEquals(0.20, result["otherChargesRate"])
        assertEquals(1.20, result["shippingMultiplier"])
        assertEquals(1.12, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return default rules for unsupported country`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "Unknown Country",
            polPort = "Unknown Port",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.10, result["multiplier"])
        assertEquals(0.15, result["customDutyRate"])
        assertEquals(0.05, result["otherChargesRate"])
        assertEquals(1.05, result["shippingMultiplier"])
        assertEquals(1.02, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should return default rules for null country`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = null,
            polPort = "Unknown Port",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.10, result["multiplier"])
        assertEquals(0.15, result["customDutyRate"])
        assertEquals(0.05, result["otherChargesRate"])
        assertEquals(1.05, result["shippingMultiplier"])
        assertEquals(1.02, result["insuranceMultiplier"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should throw exception when booking not found`() {
        // Given
        val bookingId = 999L
        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.empty())

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            countryRulesService.getCountryRules(bookingId)
        }
        
        assertEquals("Booking not found with id: $bookingId", exception.message)
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getCountryRules should handle case insensitive country names`() {
        // Given
        val bookingId = 1L
        val booking = Booking(
            id = bookingId,
            bookingNumber = "BK-001",
            vesselNo = "VESSEL001",
            vesselName = "Test Vessel",
            consigneeCountry = "pakistan", // lowercase
            polPort = "Karachi",
            bookingDate = LocalDate.now(),
            status = com.automan.backend.model.BookingStatus.DRAFT,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        whenever(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(booking))

        // When
        val result = countryRulesService.getCountryRules(bookingId)

        // Then
        assertEquals(1.15, result["multiplier"]) // Should return Pakistan rules
        assertEquals(0.25, result["customDutyRate"])
        
        verify(bookingRepository).findById(bookingId)
    }

    @Test
    fun `getPakistanRules should return correct Pakistan rules`() {
        // When
        val result = countryRulesService.getPakistanRules()

        // Then
        assertEquals(1.15, result["multiplier"])
        assertEquals(0.25, result["customDutyRate"])
        assertEquals(0.08, result["otherChargesRate"])
        assertEquals(1.1, result["shippingMultiplier"])
        assertEquals(1.05, result["insuranceMultiplier"])
    }

    @Test
    fun `getDefaultRules should return correct default rules`() {
        // When
        val result = countryRulesService.getDefaultRules()

        // Then
        assertEquals(1.10, result["multiplier"])
        assertEquals(0.15, result["customDutyRate"])
        assertEquals(0.05, result["otherChargesRate"])
        assertEquals(1.05, result["shippingMultiplier"])
        assertEquals(1.02, result["insuranceMultiplier"])
    }

    @Test
    fun `getAllSupportedCountries should return all supported countries`() {
        // When
        val result = countryRulesService.getAllSupportedCountries()

        // Then
        assertEquals(7, result.size)
        assertTrue(result.contains("PAKISTAN"))
        assertTrue(result.contains("SOUTH AFRICA"))
        assertTrue(result.contains("KENYA"))
        assertTrue(result.contains("TANZANIA"))
        assertTrue(result.contains("UGANDA"))
        assertTrue(result.contains("GHANA"))
        assertTrue(result.contains("NIGERIA"))
    }

    @Test
    fun `isCountrySupported should return true for supported countries`() {
        // When & Then
        assertTrue(countryRulesService.isCountrySupported("Pakistan"))
        assertTrue(countryRulesService.isCountrySupported("pakistan"))
        assertTrue(countryRulesService.isCountrySupported("PAKISTAN"))
        assertTrue(countryRulesService.isCountrySupported("South Africa"))
        assertTrue(countryRulesService.isCountrySupported("Kenya"))
        assertTrue(countryRulesService.isCountrySupported("Tanzania"))
        assertTrue(countryRulesService.isCountrySupported("Uganda"))
        assertTrue(countryRulesService.isCountrySupported("Ghana"))
        assertTrue(countryRulesService.isCountrySupported("Nigeria"))
    }

    @Test
    fun `isCountrySupported should return false for unsupported countries`() {
        // When & Then
        assertFalse(countryRulesService.isCountrySupported("Unknown Country"))
        assertFalse(countryRulesService.isCountrySupported("Japan"))
        assertFalse(countryRulesService.isCountrySupported("China"))
        assertFalse(countryRulesService.isCountrySupported(""))
        assertFalse(countryRulesService.isCountrySupported(" "))
    }
}
