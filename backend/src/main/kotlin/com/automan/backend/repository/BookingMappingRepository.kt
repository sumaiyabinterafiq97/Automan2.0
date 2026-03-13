package com.automan.backend.repository

import com.automan.backend.model.BookingMapping
import org.springframework.data.jpa.repository.JpaRepository

interface BookingMappingRepository : JpaRepository<BookingMapping, Long> {
    fun findByCountryIgnoreCase(country: String): List<BookingMapping>
    fun findByCountryIgnoreCaseAndClientNameIgnoreCase(country: String, clientName: String): List<BookingMapping>
    fun findByCountryIgnoreCaseAndStockLocationIgnoreCase(country: String, stockLocation: String): List<BookingMapping>
    /** Find all rows with this stock_location (any country). Used to resolve POLs from any booking_mappings row. */
    fun findByStockLocationIgnoreCase(stockLocation: String): List<BookingMapping>
}


