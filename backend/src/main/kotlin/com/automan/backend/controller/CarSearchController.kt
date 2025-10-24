package com.automan.backend.controller

import com.automan.backend.model.dto.CarInfo
import com.automan.backend.service.CarSearchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/cars")
class CarSearchController(
    private val carSearchService: CarSearchService
) {

    @GetMapping("/search")
    fun searchUnshippedCars(
        @RequestParam(required = false) consignee: String?,
        @RequestParam(required = false) pol: String?,
        @RequestParam(required = false) chassis: String?,
        @RequestParam(defaultValue = "true") unshipped: Boolean
    ): ResponseEntity<List<CarInfo>> {
        return try {
            val cars = if (unshipped) {
                carSearchService.searchUnshippedCars(consignee, pol, chassis)
            } else {
                emptyList()
            }
            ResponseEntity.ok(cars)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    @GetMapping("/booking/{bookingId}")
    fun getCarsByBooking(@PathVariable bookingId: Long): ResponseEntity<List<CarInfo>> {
        return try {
            val cars = carSearchService.getCarsByBooking(bookingId)
            ResponseEntity.ok(cars)
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }
}
