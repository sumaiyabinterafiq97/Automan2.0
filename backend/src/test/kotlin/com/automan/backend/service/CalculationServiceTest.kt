package com.automan.backend.service

import com.automan.backend.model.dto.CalculationRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CalculationServiceTest {

    private class StubCountryRulesService(
        private val cafByCountry: Map<String, Map<String, Double>> = emptyMap(),
        private val pakistanOverride: Map<String, Double>? = null,
    ) : CountryRulesService() {
        override fun getCountryRules(country: String): Map<String, Double> {
            return cafByCountry[country] ?: super.getCountryRules(country)
        }

        override fun getPakistanRules(): Map<String, Double> {
            return pakistanOverride ?: super.getPakistanRules()
        }
    }

    @Test
    fun `calculateFreight should calculate total price correctly`() {
        val calculationService = CalculationService(CountryRulesService())
        val request = CalculationRequest(
            country = null,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false,
        )

        val result = calculationService.calculateFreight(request)

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("Freight calculation completed", result.message)
        assertEquals(2650.0, result.totalPrice)
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
        val countryRules = mapOf("multiplier" to 1.2)
        val calculationService = CalculationService(
            StubCountryRulesService(cafByCountry = mapOf("Pakistan" to countryRules)),
        )
        val request = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false,
        )

        val result = calculationService.calculateCAF(request)

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("C&F calculation completed", result.message)
        assertEquals(3180.0, result.totalPrice)
        assertEquals(8, result.breakdown.size)
        assertEquals(1.2, result.breakdown["countryMultiplier"])
    }

    @Test
    fun `calculateCAF should use default country when country is null`() {
        val defaultRules = mapOf("multiplier" to 1.1)
        val calculationService = CalculationService(
            StubCountryRulesService(cafByCountry = mapOf("DEFAULT" to defaultRules)),
        )
        val request = CalculationRequest(
            country = null,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false,
        )

        val result = calculationService.calculateCAF(request)

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals(2915.0, result.totalPrice, 0.0001)
    }

    @Test
    fun `calculateFOB should calculate total price correctly`() {
        val calculationService = CalculationService(CountryRulesService())
        val request = CalculationRequest(
            country = null,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false,
        )

        val result = calculationService.calculateFOB(request)

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("FOB calculation completed", result.message)
        assertEquals(2650.0, result.totalPrice)
        assertEquals(7, result.breakdown.size)
    }

    @Test
    fun `calculatePakistan should calculate Pakistan charges successfully`() {
        val pakistanRules = mapOf(
            "customDutyRate" to 0.25,
            "otherChargesRate" to 0.08,
        )
        val calculationService = CalculationService(
            StubCountryRulesService(pakistanOverride = pakistanRules),
        )
        val request = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = true,
        )

        val result = calculationService.calculatePakistan(request)

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("Pakistan calculation completed", result.message)
        assertEquals(10, result.breakdown.size)
        assertEquals(2650.0, result.breakdown["baseTotal"])
        assertTrue(result.breakdown.containsKey("customDuty"))
        assertTrue(result.breakdown.containsKey("otherCharges"))
    }

    @Test
    fun `calculatePakistan should throw exception when package option is false`() {
        val calculationService = CalculationService(CountryRulesService())
        val request = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false,
        )

        val exception = assertThrows<IllegalArgumentException> {
            calculationService.calculatePakistan(request)
        }

        assertEquals("Pakistan calculation requires package option to be enabled", exception.message)
    }
}
