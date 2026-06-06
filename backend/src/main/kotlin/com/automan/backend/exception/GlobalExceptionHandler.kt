package com.automan.backend.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<Map<String, Any>> {
        val msg = ex.message ?: "Invalid request"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(msg, HttpStatus.BAD_REQUEST.value()))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<Map<String, Any>> {
        val status = ex.statusCode
        val msg = ex.reason?.takeIf { it.isNotBlank() } ?: "Request failed"
        return ResponseEntity.status(status).body(errorBody(msg, status.value()))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, Any>> {
        val errorResponse = mapOf<String, Any>(
            "timestamp" to LocalDateTime.now().toString(),
            "status" to HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "error" to "Internal Server Error",
            "message" to (ex.message ?: "An unexpected error occurred"),
            "success" to false,
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }

    private fun errorBody(message: String, statusCode: Int): Map<String, Any> {
        val creditBlocked = message.contains("credit limit", ignoreCase = true)
        val reason = HttpStatus.resolve(statusCode)?.reasonPhrase ?: "Error"
        val body = linkedMapOf<String, Any>(
            "timestamp" to LocalDateTime.now().toString(),
            "status" to statusCode,
            "error" to if (creditBlocked) "Credit limit exceeded" else reason,
            "message" to message,
            "success" to false,
        )
        if (creditBlocked) {
            body["creditLimitBlocked"] = true
        }
        return body
    }
}
