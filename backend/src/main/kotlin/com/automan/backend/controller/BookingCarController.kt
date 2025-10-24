package com.automan.backend.controller

import com.automan.backend.model.dto.CarSelectionRequest
import com.automan.backend.service.CarSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/booking-cars")
class BookingCarController(
    private val carSearchService: CarSearchService
) {

    @PostMapping
    fun addCarsToBooking(@RequestBody request: CarSelectionRequest): ResponseEntity<Map<String, Any>> {
        return try {
            val updatedCount = carSearchService.addCarsToBooking(request.bookingId, request.carIds)
            val response = mapOf(
                "message" to "Cars assigned to booking successfully",
                "updatedCount" to updatedCount
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @DeleteMapping("/{bookingId}")
    fun removeCarsFromBooking(@PathVariable bookingId: Long): ResponseEntity<Map<String, Any>> {
        return try {
            val removedCount = carSearchService.removeCarsFromBooking(bookingId)
            val response = mapOf(
                "message" to "Cars removed from booking successfully",
                "removedCount" to removedCount
            )
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}
