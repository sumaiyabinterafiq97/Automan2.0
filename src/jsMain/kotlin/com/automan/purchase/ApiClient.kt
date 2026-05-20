package com.automan.purchase

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestInit
import com.automan.purchase.Logger
import com.automan.purchase.ErrorHandler
import com.automan.purchase.apiUrl

/**
 * Centralized API client for making HTTP requests
 * Provides consistent error handling and request/response processing
 */
object ApiClient {
    
    /**
     * Makes an API request with proper error handling
     * @param endpoint API endpoint (without /api prefix)
     * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
     * @param body Request body (will be JSON stringified)
     * @return Response data or null if error occurred
     */
    suspend fun <T> request(
        endpoint: String,
        method: String = "GET",
        body: dynamic = null
    ): ApiResult<T> {
        return try {
            val requestInit = js("{}").unsafeCast<RequestInit>()
            requestInit.method = method
            
            val headers = js("{}")
            headers["Content-Type"] = "application/json"
            requestInit.headers = headers
            
            if (body != null) {
                requestInit.body = JSON.stringify(body)
            }
            
            Logger.debug("API request: $method ${apiUrl(endpoint)}")
            
            val response = window.fetch(apiUrl(endpoint), requestInit).await()

            if (response.ok) {
                val text = response.text().await()
                val trimmed = text.trim()
                val data: T = if (trimmed.isEmpty()) {
                    js("null").unsafeCast<T>()
                } else {
                    val parsed: dynamic = JSON.parse(trimmed)
                    parsed.unsafeCast<T>()
                }
                Logger.debug("API success: $endpoint")
                ApiResult.Success(data)
            } else {
                val errorText = response.text().await()
                val status = response.status.toInt()
                val errorMessage = when {
                    status == 502 || status == 503 || status == 504 -> {
                        val isGateway =
                            errorText.contains("Bad Gateway", ignoreCase = true) ||
                            errorText.contains("Gateway", ignoreCase = true) ||
                            errorText.contains("Service Unavailable", ignoreCase = true)
                        if (isGateway) {
                            "The API server is not reachable (HTTP $status). " +
                            "If you use Docker, ensure the stack is up: e.g. docker compose -f docker/docker-compose.multiplatform.yml up -d, " +
                            "and check the backend: docker logs automan_backend_multiplatform"
                        } else {
                            ErrorHandler.extractErrorMessage(errorText)
                        }
                    }
                    else -> ErrorHandler.extractErrorMessage(errorText)
                }
                Logger.error("API error: $endpoint - $errorMessage")
                ApiResult.Error(errorMessage, status)
            }
        } catch (e: dynamic) {
            val errorMessage = ErrorHandler.handleNetworkError(e, endpoint)
            Logger.error("Network error: $endpoint - $errorMessage")
            ApiResult.Error(errorMessage, 0)
        }
    }
    
    /**
     * GET request
     */
    suspend fun <T> get(endpoint: String): ApiResult<T> {
        return request<T>(endpoint, "GET")
    }
    
    /**
     * POST request
     */
    suspend fun <T> post(endpoint: String, body: dynamic): ApiResult<T> {
        return request<T>(endpoint, "POST", body)
    }
    
    /**
     * PUT request
     */
    suspend fun <T> put(endpoint: String, body: dynamic): ApiResult<T> {
        return request<T>(endpoint, "PUT", body)
    }
    
    /**
     * PATCH request
     */
    suspend fun <T> patch(endpoint: String, body: dynamic): ApiResult<T> {
        return request<T>(endpoint, "PATCH", body)
    }
    
    /**
     * DELETE request
     */
    suspend fun <T> delete(endpoint: String): ApiResult<T> {
        return request<T>(endpoint, "DELETE")
    }
}

/**
 * Result wrapper for API calls
 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val statusCode: Int = 0) : ApiResult<Nothing>()
    
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (String, Int) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Error -> onError(message, statusCode)
    }
}
