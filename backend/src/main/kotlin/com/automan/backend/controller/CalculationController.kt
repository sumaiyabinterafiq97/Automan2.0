package com.automan.backend.controller

import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.model.dto.CalculationResponse
import com.automan.backend.service.CalculationService
import com.automan.backend.util.Logger
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/calculations")
class CalculationController(
    private val calculationService: CalculationService
) {

    @PostMapping("/freight")
    fun calculateFreight(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculateFreight(request)
            ResponseEntity.ok(calculation)
        } catch (e: IllegalArgumentException) {
            Logger.warn("Invalid request for freight calculation: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                CalculationResponse(
                    success = false,
                    message = "Invalid request: ${e.message}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        } catch (e: Exception) {
            Logger.error("Error calculating freight: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CalculationResponse(
                    success = false,
                    message = "Calculation failed: ${e.message ?: "Unknown error"}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        }
    }

    @PostMapping("/caf")
    fun calculateCAF(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculateCAF(request)
            ResponseEntity.ok(calculation)
        } catch (e: IllegalArgumentException) {
            Logger.warn("Invalid request for CAF calculation: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                CalculationResponse(
                    success = false,
                    message = "Invalid request: ${e.message}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        } catch (e: Exception) {
            Logger.error("Error calculating CAF: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CalculationResponse(
                    success = false,
                    message = "Calculation failed: ${e.message ?: "Unknown error"}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        }
    }

    @PostMapping("/fob")
    fun calculateFOB(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculateFOB(request)
            ResponseEntity.ok(calculation)
        } catch (e: IllegalArgumentException) {
            Logger.warn("Invalid request for FOB calculation: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                CalculationResponse(
                    success = false,
                    message = "Invalid request: ${e.message}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        } catch (e: Exception) {
            Logger.error("Error calculating FOB: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CalculationResponse(
                    success = false,
                    message = "Calculation failed: ${e.message ?: "Unknown error"}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        }
    }

    @PostMapping("/pakistan")
    fun calculatePakistan(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculatePakistan(request)
            ResponseEntity.ok(calculation)
        } catch (e: IllegalArgumentException) {
            Logger.warn("Invalid request for Pakistan calculation: ${e.message}")
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                CalculationResponse(
                    success = false,
                    message = e.message ?: "Invalid request",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        } catch (e: Exception) {
            Logger.error("Error calculating Pakistan charges: ${e.message}", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                CalculationResponse(
                    success = false,
                    message = "Calculation failed: ${e.message ?: "Unknown error"}",
                    totalPrice = 0.0,
                    breakdown = emptyMap()
                )
            )
        }
    }
}
