package com.automan.backend.service

import com.automan.backend.model.BookingCalculation
import com.automan.backend.model.CalculationType
import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.model.dto.CalculationResponse
import com.automan.backend.repository.BookingCalculationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalculationServiceTest {

    @Mock
    private lateinit var bookingCalculationRepository: BookingCalculationRepository

    @Mock
    private lateinit var countryRulesService: CountryRulesService

    private lateinit var calculationService: CalculationService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        calculationService = CalculationService(bookingCalculationRepository, countryRulesService)
    }

    @Test
    fun `calculateFreight should calculate total price correctly`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        val result = calculationService.calculateFreight(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("Freight calculation completed", result.message)
        assertEquals(2650.0, result.totalPrice) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
        assertEquals(7, result.breakdown.size)
        assertEquals(1000.0, result.breakdown["containerPrice"])
        assertEquals(500.0, result.breakdown["shippingCharge"])
        assertEquals(200.0, result.breakdown["wcCharge"])
        assertEquals(100.0, result.breakdown["inspectionFee"])
        assertEquals(300.0, result.breakdown["fobPrice"])
        assertEquals(400.0, result.breakdown["freightPrice"])
        assertEquals(150.0, result.breakdown["insurance"])
        
        verify(bookingCalculationRepository).save(any())
    }

    @Test
    fun `calculateCAF should apply country multiplier correctly`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        val countryRules = mapOf("multiplier" to 1.2)
        whenever(countryRulesService.getCountryRules(1L)).thenReturn(countryRules)
        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        val result = calculationService.calculateCAF(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("C&F calculation completed", result.message)
        assertEquals(3180.0, result.totalPrice) // 2650 * 1.2
        assertEquals(8, result.breakdown.size)
        assertEquals(1.2, result.breakdown["countryMultiplier"])
        
        verify(countryRulesService).getCountryRules(1L)
        verify(bookingCalculationRepository).save(any())
    }

    @Test
    fun `calculateCAF should use default multiplier when country rules not found`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        val countryRules = emptyMap<String, Double>()
        whenever(countryRulesService.getCountryRules(1L)).thenReturn(countryRules)
        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        val result = calculationService.calculateCAF(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals(2650.0, result.totalPrice) // 2650 * 1.0 (default multiplier)
        assertEquals(1.0, result.breakdown["countryMultiplier"])
        
        verify(countryRulesService).getCountryRules(1L)
        verify(bookingCalculationRepository).save(any())
    }

    @Test
    fun `calculateFOB should calculate total price correctly`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        val result = calculationService.calculateFOB(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("FOB calculation completed", result.message)
        assertEquals(2650.0, result.totalPrice) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
        assertEquals(7, result.breakdown.size)
        
        verify(bookingCalculationRepository).save(any())
    }

    @Test
    fun `calculatePakistan should calculate with custom duty and other charges`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = true
        )

        val pakistanRules = mapOf(
            "customDutyRate" to 0.15,
            "otherChargesRate" to 0.08
        )
        whenever(countryRulesService.getPakistanRules()).thenReturn(pakistanRules)
        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        val result = calculationService.calculatePakistan(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("Pakistan calculation completed", result.message)
        
        val baseTotal = 2650.0
        val customDuty = baseTotal * 0.15 // 397.5
        val otherCharges = baseTotal * 0.08 // 212.0
        val expectedTotal = baseTotal + customDuty + otherCharges // 3259.5
        
        assertEquals(expectedTotal, result.totalPrice)
        assertEquals(10, result.breakdown.size)
        assertEquals(baseTotal, result.breakdown["baseTotal"])
        assertEquals(customDuty, result.breakdown["customDuty"])
        assertEquals(otherCharges, result.breakdown["otherCharges"])
        
        verify(countryRulesService).getPakistanRules()
        verify(bookingCalculationRepository).save(any())
    }

    @Test
    fun `calculatePakistan should throw exception when package option is false`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
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
        val exception = assertThrows<IllegalArgumentException> {
            calculationService.calculatePakistan(request)
        }
        
        assertEquals("Pakistan calculation requires package option to be enabled", exception.message)
        verify(countryRulesService, never()).getPakistanRules()
        verify(bookingCalculationRepository, never()).save(any())
    }

    @Test
    fun `calculatePakistan should use default rates when rules not found`() {
        // Given
        val request = CalculationRequest(
            bookingId = 1L,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = true
        )

        val pakistanRules = emptyMap<String, Double>()
        whenever(countryRulesService.getPakistanRules()).thenReturn(pakistanRules)
        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        val result = calculationService.calculatePakistan(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        
        val baseTotal = 2650.0
        val customDuty = baseTotal * 0.1 // 265.0 (default rate)
        val otherCharges = baseTotal * 0.05 // 132.5 (default rate)
        val expectedTotal = baseTotal + customDuty + otherCharges // 3047.5
        
        assertEquals(expectedTotal, result.totalPrice)
        assertEquals(customDuty, result.breakdown["customDuty"])
        assertEquals(otherCharges, result.breakdown["otherCharges"])
        
        verify(countryRulesService).getPakistanRules()
        verify(bookingCalculationRepository).save(any())
    }

    @Test
    fun `getCalculationsByBooking should return all calculations for booking`() {
        // Given
        val bookingId = 1L
        val calculations = listOf(
            BookingCalculation(
                id = 1L,
                bookingId = bookingId,
                calculationType = CalculationType.FREIGHT,
                totalPrice = BigDecimal.valueOf(2650.0)
            ),
            BookingCalculation(
                id = 2L,
                bookingId = bookingId,
                calculationType = CalculationType.CAF,
                totalPrice = BigDecimal.valueOf(3180.0)
            )
        )

        whenever(bookingCalculationRepository.findByBookingId(bookingId)).thenReturn(calculations)

        // When
        val result = calculationService.getCalculationsByBooking(bookingId)

        // Then
        assertEquals(2, result.size)
        assertEquals(CalculationType.FREIGHT, result[0].calculationType)
        assertEquals(CalculationType.CAF, result[1].calculationType)
        
        verify(bookingCalculationRepository).findByBookingId(bookingId)
    }

    @Test
    fun `getCalculationByType should return specific calculation type`() {
        // Given
        val bookingId = 1L
        val calculationType = CalculationType.FREIGHT
        val calculation = BookingCalculation(
            id = 1L,
            bookingId = bookingId,
            calculationType = calculationType,
            totalPrice = BigDecimal.valueOf(2650.0)
        )

        whenever(bookingCalculationRepository.findByBookingIdAndCalculationType(bookingId, calculationType))
            .thenReturn(calculation)

        // When
        val result = calculationService.getCalculationByType(bookingId, calculationType)

        // Then
        assertNotNull(result)
        assertEquals(calculationType, result.calculationType)
        assertEquals(BigDecimal.valueOf(2650.0), result.totalPrice)
        
        verify(bookingCalculationRepository).findByBookingIdAndCalculationType(bookingId, calculationType)
    }

    @Test
    fun `getTotalPriceByBooking should return sum of all calculations`() {
        // Given
        val bookingId = 1L
        val totalPrice = 5830.0

        whenever(bookingCalculationRepository.sumTotalPriceByBookingId(bookingId)).thenReturn(totalPrice)

        // When
        val result = calculationService.getTotalPriceByBooking(bookingId)

        // Then
        assertEquals(totalPrice, result)
        
        verify(bookingCalculationRepository).sumTotalPriceByBookingId(bookingId)
    }

    @Test
    fun `saveCalculation should create BookingCalculation with correct values`() {
        // Given
        val bookingId = 1L
        val type = CalculationType.FREIGHT
        val request = CalculationRequest(
            bookingId = bookingId,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )
        val totalPrice = 2650.0

        whenever(bookingCalculationRepository.save(any())).thenReturn(BookingCalculation())

        // When
        calculationService.calculateFreight(request)

        // Then
        verify(bookingCalculationRepository).save(argThat { calculation ->
            calculation.bookingId == bookingId &&
            calculation.calculationType == type &&
            calculation.containerPrice == BigDecimal.valueOf(1000.0) &&
            calculation.shippingCharge == BigDecimal.valueOf(500.0) &&
            calculation.wcCharge == BigDecimal.valueOf(200.0) &&
            calculation.inspectionFee == BigDecimal.valueOf(100.0) &&
            calculation.fobPrice == BigDecimal.valueOf(300.0) &&
            calculation.freightPrice == BigDecimal.valueOf(400.0) &&
            calculation.insurance == BigDecimal.valueOf(150.0) &&
            calculation.totalPrice == BigDecimal.valueOf(totalPrice)
        })
    }
}
