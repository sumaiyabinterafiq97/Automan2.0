package com.automan.backend.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountryRulesServiceTest {

    private lateinit var countryRulesService: CountryRulesService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        countryRulesService = CountryRulesService()
    }

    @Test
    fun `getCountryRules should return Pakistan rules for Pakistan`() {
        // When
        val result = countryRulesService.getCountryRules("Pakistan")

        // Then
        assertEquals(1.15, result["multiplier"])
        assertEquals(0.25, result["customDutyRate"])
        assertEquals(0.08, result["otherChargesRate"])
        assertEquals(1.1, result["shippingMultiplier"])
        assertEquals(1.05, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return South Africa rules for South Africa`() {
        // When
        val result = countryRulesService.getCountryRules("South Africa")

        // Then
        assertEquals(1.12, result["multiplier"])
        assertEquals(0.20, result["customDutyRate"])
        assertEquals(0.06, result["otherChargesRate"])
        assertEquals(1.08, result["shippingMultiplier"])
        assertEquals(1.03, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return Kenya rules for Kenya`() {
        // When
        val result = countryRulesService.getCountryRules("Kenya")

        // Then
        assertEquals(1.18, result["multiplier"])
        assertEquals(0.30, result["customDutyRate"])
        assertEquals(0.10, result["otherChargesRate"])
        assertEquals(1.12, result["shippingMultiplier"])
        assertEquals(1.06, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return Tanzania rules for Tanzania`() {
        // When
        val result = countryRulesService.getCountryRules("Tanzania")

        // Then
        assertEquals(1.20, result["multiplier"])
        assertEquals(0.35, result["customDutyRate"])
        assertEquals(0.12, result["otherChargesRate"])
        assertEquals(1.15, result["shippingMultiplier"])
        assertEquals(1.08, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return Uganda rules for Uganda`() {
        // When
        val result = countryRulesService.getCountryRules("Uganda")

        // Then
        assertEquals(1.22, result["multiplier"])
        assertEquals(0.40, result["customDutyRate"])
        assertEquals(0.15, result["otherChargesRate"])
        assertEquals(1.18, result["shippingMultiplier"])
        assertEquals(1.10, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return Ghana rules for Ghana`() {
        // When
        val result = countryRulesService.getCountryRules("Ghana")

        // Then
        assertEquals(1.16, result["multiplier"])
        assertEquals(0.25, result["customDutyRate"])
        assertEquals(0.08, result["otherChargesRate"])
        assertEquals(1.10, result["shippingMultiplier"])
        assertEquals(1.05, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return Nigeria rules for Nigeria`() {
        // When
        val result = countryRulesService.getCountryRules("Nigeria")

        // Then
        assertEquals(1.25, result["multiplier"])
        assertEquals(0.45, result["customDutyRate"])
        assertEquals(0.20, result["otherChargesRate"])
        assertEquals(1.20, result["shippingMultiplier"])
        assertEquals(1.12, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return default rules for unsupported country`() {
        // When
        val result = countryRulesService.getCountryRules("Unknown Country")

        // Then
        assertEquals(1.10, result["multiplier"])
        assertEquals(0.15, result["customDutyRate"])
        assertEquals(0.05, result["otherChargesRate"])
        assertEquals(1.05, result["shippingMultiplier"])
        assertEquals(1.02, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should return default rules for DEFAULT`() {
        // When
        val result = countryRulesService.getCountryRules("DEFAULT")

        // Then
        assertEquals(1.10, result["multiplier"])
        assertEquals(0.15, result["customDutyRate"])
        assertEquals(0.05, result["otherChargesRate"])
        assertEquals(1.05, result["shippingMultiplier"])
        assertEquals(1.02, result["insuranceMultiplier"])
    }

    @Test
    fun `getCountryRules should handle case insensitive country names`() {
        // When
        val result = countryRulesService.getCountryRules("pakistan") // lowercase

        // Then
        assertEquals(1.15, result["multiplier"]) // Should return Pakistan rules
        assertEquals(0.25, result["customDutyRate"])
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
