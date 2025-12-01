package com.automan.backend.repository

import com.automan.backend.model.BookingMapping
import org.springframework.data.jpa.repository.JpaRepository

interface BookingMappingRepository : JpaRepository<BookingMapping, Long> {
    fun findByCountryIgnoreCase(country: String): List<BookingMapping>
    fun findByCountryIgnoreCaseAndClientNameIgnoreCase(country: String, clientName: String): List<BookingMapping>
}


