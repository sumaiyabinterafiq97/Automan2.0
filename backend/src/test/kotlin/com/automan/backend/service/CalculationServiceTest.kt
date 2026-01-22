package com.automan.backend.service

import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.model.dto.CalculationResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalculationServiceTest {

    @Mock
    private lateinit var countryRulesService: CountryRulesService

    private lateinit var calculationService: CalculationService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        calculationService = CalculationService(countryRulesService)
    }

    @Test
    fun `calculateFreight should calculate total price correctly`() {
        // Given
        val request = CalculationRequest(
            country = null,
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
    }

    @Test
    fun `calculateCAF should apply country multiplier correctly`() {
        // Given
        val request = CalculationRequest(
            country = "Pakistan",
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
        whenever(countryRulesService.getCountryRules("Pakistan")).thenReturn(countryRules)

        // When
        val result = calculationService.calculateCAF(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("C&F calculation completed", result.message)
        assertEquals(3180.0, result.totalPrice) // 2650 * 1.2
        assertEquals(8, result.breakdown.size)
        assertEquals(1.2, result.breakdown["countryMultiplier"])
        
        verify(countryRulesService).getCountryRules("Pakistan")
    }

    @Test
    fun `calculateCAF should use default country when country is null`() {
        // Given
        val request = CalculationRequest(
            country = null,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        val defaultRules = mapOf("multiplier" to 1.1)
        whenever(countryRulesService.getCountryRules("DEFAULT")).thenReturn(defaultRules)

        // When
        val result = calculationService.calculateCAF(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals(2915.0, result.totalPrice) // 2650 * 1.1
        verify(countryRulesService).getCountryRules("DEFAULT")
    }

    @Test
    fun `calculateFOB should calculate total price correctly`() {
        // Given
        val request = CalculationRequest(
            country = null,
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
        val result = calculationService.calculateFOB(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("FOB calculation completed", result.message)
        assertEquals(2650.0, result.totalPrice) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
        assertEquals(7, result.breakdown.size)
    }

    @Test
    fun `calculatePakistan should calculate Pakistan charges successfully`() {
        // Given
        val request = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = true // Required for Pakistan calculation
        )

        val pakistanRules = mapOf(
            "customDutyRate" to 0.25,
            "otherChargesRate" to 0.08
        )
        whenever(countryRulesService.getPakistanRules()).thenReturn(pakistanRules)

        // When
        val result = calculationService.calculatePakistan(request)

        // Then
        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("Pakistan calculation completed", result.message)
        assertEquals(9, result.breakdown.size)
        assertEquals(2650.0, result.breakdown["baseTotal"])
        assertTrue(result.breakdown.containsKey("customDuty"))
        assertTrue(result.breakdown.containsKey("otherCharges"))
        
        verify(countryRulesService).getPakistanRules()
    }

    @Test
    fun `calculatePakistan should throw exception when package option is false`() {
        // Given
        val request = CalculationRequest(
            country = "Pakistan",
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
        val exception = assertThrows<IllegalArgumentException> {
            calculationService.calculatePakistan(request)
        }
        
        assertEquals("Pakistan calculation requires package option to be enabled", exception.message)
    }
}
