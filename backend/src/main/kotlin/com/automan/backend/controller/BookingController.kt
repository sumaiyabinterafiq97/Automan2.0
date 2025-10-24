package com.automan.backend.controller

import com.automan.backend.model.dto.BookingRequest
import com.automan.backend.model.dto.BookingResponse
import com.automan.backend.service.BookingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/bookings")
class BookingController(
    private val bookingService: BookingService
) {

    @GetMapping
    fun getAllBookings(): ResponseEntity<List<BookingResponse>> {
        return try {
            val bookings = bookingService.getAllBookings()
            ResponseEntity.ok(bookings)
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @GetMapping("/{id}")
    fun getBooking(@PathVariable id: Long): ResponseEntity<BookingResponse> {
        return try {
            val booking = bookingService.getBooking(id)
            ResponseEntity.ok(booking)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping
    fun createBooking(@RequestBody request: BookingRequest): ResponseEntity<BookingResponse> {
        return try {
            val booking = bookingService.createBooking(request)
            ResponseEntity.status(HttpStatus.CREATED).body(booking)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PutMapping("/{id}")
    fun updateBooking(
        @PathVariable id: Long,
        @RequestBody request: BookingRequest
    ): ResponseEntity<BookingResponse> {
        return try {
            val booking = bookingService.updateBooking(id, request)
            ResponseEntity.ok(booking)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @DeleteMapping("/{id}")
    fun deleteBooking(@PathVariable id: Long): ResponseEntity<Void> {
        return try {
            bookingService.deleteBooking(id)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }
}
