package com.automan.purchase.validation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Validators
 */
class ValidatorsTest {
    
    @Test
    fun testValidateChassis_Valid() {
        val result = Validators.validateChassis("ABC123456789")
        assertTrue(result.isValid, "Valid chassis should pass validation")
    }
    
    @Test
    fun testValidateChassis_Empty() {
        val result = Validators.validateChassis("")
        assertFalse(result.isValid, "Empty chassis should fail validation")
    }
    
    @Test
    fun testValidateChassis_TooShort() {
        val result = Validators.validateChassis("ABC")
        assertFalse(result.isValid, "Chassis shorter than 5 characters should fail")
    }
    
    @Test
    fun testValidateChassis_Null() {
        val result = Validators.validateChassis(null)
        assertFalse(result.isValid, "Null chassis should fail validation")
    }
    
    @Test
    fun testValidateDate_ValidISO() {
        val result = Validators.validateDate("2025-01-15")
        assertTrue(result.isValid, "Valid ISO date should pass validation")
    }
    
    @Test
    fun testValidateDate_Empty() {
        val result = Validators.validateDate("")
        assertFalse(result.isValid, "Empty date should fail validation")
    }
    
    @Test
    fun testValidateDate_InvalidFormat() {
        val result = Validators.validateDate("invalid-date")
        assertFalse(result.isValid, "Invalid date format should fail validation")
    }
    
    @Test
    fun testValidateCurrency_Valid() {
        val result = Validators.validateCurrency("1000")
        assertTrue(result.isValid, "Valid currency should pass validation")
    }
    
    @Test
    fun testValidateCurrency_Empty() {
        val result = Validators.validateCurrency("")
        assertTrue(result.isValid, "Empty currency should be allowed")
    }
    
    @Test
    fun testValidateCurrency_Negative() {
        val result = Validators.validateCurrency("-100")
        assertFalse(result.isValid, "Negative currency should fail validation")
    }
    
    @Test
    fun testValidateYear_Valid() {
        val result = Validators.validateYear(2025)
        assertTrue(result.isValid, "Valid year should pass validation")
    }
    
    @Test
    fun testValidateYear_TooLow() {
        val result = Validators.validateYear(1800)
        assertFalse(result.isValid, "Year below minimum should fail validation")
    }
    
    @Test
    fun testValidateYear_TooHigh() {
        val result = Validators.validateYear(2200)
        assertFalse(result.isValid, "Year above maximum should fail validation")
    }
    
    @Test
    fun testValidateRequired_Valid() {
        val result = Validators.validateRequired("value", "Field")
        assertTrue(result.isValid, "Non-empty value should pass validation")
    }
    
    @Test
    fun testValidateRequired_Empty() {
        val result = Validators.validateRequired("", "Field")
        assertFalse(result.isValid, "Empty value should fail validation")
    }
    
    @Test
    fun testValidateEmail_Valid() {
        val result = Validators.validateEmail("test@example.com")
        assertTrue(result.isValid, "Valid email should pass validation")
    }
    
    @Test
    fun testValidateEmail_Invalid() {
        val result = Validators.validateEmail("invalid-email")
        assertFalse(result.isValid, "Invalid email should fail validation")
    }
    
    @Test
    fun testValidateLength_Valid() {
        val result = Validators.validateLength("test", 2, 10, "Field")
        assertTrue(result.isValid, "Valid length should pass validation")
    }
    
    @Test
    fun testValidateLength_TooShort() {
        val result = Validators.validateLength("a", 2, 10, "Field")
        assertFalse(result.isValid, "Too short value should fail validation")
    }
    
    @Test
    fun testValidateLength_TooLong() {
        val result = Validators.validateLength("a".repeat(20), 2, 10, "Field")
        assertFalse(result.isValid, "Too long value should fail validation")
    }
}
