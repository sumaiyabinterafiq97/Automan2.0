package com.automan.purchase.validation

import com.automan.purchase.AppConstants
import com.automan.purchase.extractNumericFromDbValue

/**
 * Input validation utilities
 */
object Validators {
    
    /**
     * Validation result
     */
    data class ValidationResult(
        val isValid: Boolean,
        val message: String = ""
    )
    
    /**
     * Validates chassis number
     */
    fun validateChassis(chassis: String?): ValidationResult {
        if (chassis.isNullOrBlank()) {
            return ValidationResult(false, "Chassis number is required")
        }
        val trimmed = chassis.trim()
        if (trimmed.length < 5) {
            return ValidationResult(false, "Chassis number must be at least 5 characters")
        }
        if (trimmed.length > 50) {
            return ValidationResult(false, "Chassis number must be less than 50 characters")
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates date string
     */
    fun validateDate(date: String?): ValidationResult {
        if (date.isNullOrBlank()) {
            return ValidationResult(false, "Date is required")
        }
        // Basic ISO date format validation (YYYY-MM-DD)
        val isoPattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (!isoPattern.matches(date.trim())) {
            // Try parsing as Date object
            try {
                val dateObj = js("new Date(date)")
                if (js("isNaN(dateObj.getTime())") as Boolean) {
                    return ValidationResult(false, "Invalid date format")
                }
            } catch (e: dynamic) {
                return ValidationResult(false, "Invalid date format")
            }
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates currency amount
     */
    fun validateCurrency(amount: String?): ValidationResult {
        if (amount.isNullOrBlank()) {
            return ValidationResult(true) // Allow empty amounts
        }
        val numeric = extractNumericFromDbValue(amount)
        if (numeric.isEmpty()) {
            return ValidationResult(false, "Invalid amount format")
        }
        val value = numeric.toDoubleOrNull()
        if (value == null) {
            return ValidationResult(false, "Invalid amount")
        }
        if (value < 0) {
            return ValidationResult(false, "Amount cannot be negative")
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates year
     */
    fun validateYear(year: Int?): ValidationResult {
        if (year == null) {
            return ValidationResult(true) // Allow null years
        }
        if (year < AppConstants.MIN_YEAR || year > AppConstants.MAX_YEAR) {
            return ValidationResult(
                false,
                "Year must be between ${AppConstants.MIN_YEAR} and ${AppConstants.MAX_YEAR}"
            )
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates day of month
     */
    fun validateDay(day: Int?): ValidationResult {
        if (day == null) {
            return ValidationResult(true) // Allow null days
        }
        if (day < AppConstants.MIN_DAY || day > AppConstants.MAX_DAY) {
            return ValidationResult(
                false,
                "Day must be between ${AppConstants.MIN_DAY} and ${AppConstants.MAX_DAY}"
            )
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates required field
     */
    fun validateRequired(value: String?, fieldName: String): ValidationResult {
        if (value.isNullOrBlank()) {
            return ValidationResult(false, "$fieldName is required")
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates email format
     */
    fun validateEmail(email: String?): ValidationResult {
        if (email.isNullOrBlank()) {
            return ValidationResult(true) // Allow empty emails
        }
        val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        if (!emailPattern.matches(email.trim())) {
            return ValidationResult(false, "Invalid email format")
        }
        return ValidationResult(true)
    }
    
    /**
     * Validates string length
     */
    fun validateLength(value: String?, minLength: Int, maxLength: Int, fieldName: String): ValidationResult {
        if (value == null) {
            return ValidationResult(true) // Allow null
        }
        val length = value.trim().length
        if (length < minLength) {
            return ValidationResult(false, "$fieldName must be at least $minLength characters")
        }
        if (length > maxLength) {
            return ValidationResult(false, "$fieldName must be less than $maxLength characters")
        }
        return ValidationResult(true)
    }
}
