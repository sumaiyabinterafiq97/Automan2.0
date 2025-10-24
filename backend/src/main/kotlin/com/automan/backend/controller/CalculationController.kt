package com.automan.backend.controller

import com.automan.backend.model.dto.CalculationRequest
import com.automan.backend.model.dto.CalculationResponse
import com.automan.backend.service.CalculationService
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
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/caf")
    fun calculateCAF(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculateCAF(request)
            ResponseEntity.ok(calculation)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/fob")
    fun calculateFOB(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculateFOB(request)
            ResponseEntity.ok(calculation)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/pakistan")
    fun calculatePakistan(@RequestBody request: CalculationRequest): ResponseEntity<CalculationResponse> {
        return try {
            val calculation = calculationService.calculatePakistan(request)
            ResponseEntity.ok(calculation)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}
