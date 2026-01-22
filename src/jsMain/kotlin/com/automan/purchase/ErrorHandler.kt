package com.automan.purchase

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.Response

/**
 * Centralized error handling utilities
 */
object ErrorHandler {
    
    /**
     * Extracts error message from API response
     */
    fun extractErrorMessage(errorText: String): String {
        return try {
            val errorJson = JSON.parse<dynamic>(errorText)
            errorJson.message?.toString() 
                ?: errorJson.error?.toString()
                ?: errorText
        } catch (e: dynamic) {
            errorText.ifBlank { "Unknown error occurred" }
        }
    }
    
    /**
     * Handles network errors
     */
    fun handleNetworkError(error: dynamic, endpoint: String): String {
        val errorMessage = when {
            error.message != null -> error.message.toString()
            error.toString != null -> error.toString()
            else -> "Network connection failed"
        }
        Logger.error("Network error for endpoint '$endpoint': $errorMessage")
        return errorMessage
    }
    
    /**
     * Shows error message to user
     */
    fun showError(message: String, title: String = "Error") {
        showMessage(message, "error")
    }
    
    /**
     * Shows success message to user
     */
    fun showSuccess(message: String) {
        showMessage(message, "success")
    }
    
    /**
     * Shows warning message to user
     */
    fun showWarning(message: String) {
        showMessage(message, "warning")
    }
    
    /**
     * Handles API error response
     */
    suspend fun handleApiError(response: Response, endpoint: String): String {
        return try {
            val errorText = response.text().await()
            extractErrorMessage(errorText)
        } catch (e: dynamic) {
            "HTTP ${response.status}: ${response.statusText}"
        }
    }
}
