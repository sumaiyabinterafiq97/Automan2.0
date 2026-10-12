package com.automan.backend.exception

import com.automan.backend.util.Logger
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.LocalDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<Map<String, Any>> {
        val msg = ClientErrorMessages.safe(ex.message, "Invalid request")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(msg, HttpStatus.BAD_REQUEST.value()))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalStateException(ex: IllegalStateException): ResponseEntity<Map<String, Any>> {
        val msg = ClientErrorMessages.safe(ex.message, "The request could not be completed.")
        val status = if (msg.contains("already completed", ignoreCase = true)) HttpStatus.CONFLICT else HttpStatus.BAD_REQUEST
        return ResponseEntity.status(status).body(errorBody(msg, status.value()))
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<Map<String, Any>> {
        val status = ex.statusCode
        val msg = ClientErrorMessages.safe(ex.reason, "Request failed")
        return ResponseEntity.status(status).body(errorBody(msg, status.value()))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentNotValidException::class)
    fun handleUnreadable(ex: Exception): ResponseEntity<Map<String, Any>> {
        Logger.warn("Rejected an unreadable request: ${ex.javaClass.simpleName}")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(errorBody("The request body is invalid.", HttpStatus.BAD_REQUEST.value()))
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleMissingResource(ex: NoResourceFoundException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(errorBody("Not found", HttpStatus.NOT_FOUND.value()))
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccess(ex: DataAccessException): ResponseEntity<Map<String, Any>> {
        Logger.error("Database error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("The request could not be completed.", HttpStatus.INTERNAL_SERVER_ERROR.value()))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<Map<String, Any>> {
        Logger.error("Unhandled request error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorBody("An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR.value()))
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

object ClientErrorMessages {
    private val leaked = listOf(
        "sql",
        "jdbc",
        "hibernate",
        "stacktrace",
        "exception",
        "c:\\",
        "/users/",
        "select ",
        "insert ",
        "update ",
        "delete from",
        "could not execute",
    )

    fun safe(raw: String?, fallback: String): String {
        val msg = raw?.trim().orEmpty()
        if (msg.isEmpty() || msg.length > 300) return fallback
        val lower = msg.lowercase()
        if (leaked.any { lower.contains(it) }) return fallback
        return msg
    }
}
