package com.automan.backend.repository

import com.automan.backend.model.BookingMapping
import org.springframework.data.jpa.repository.JpaRepository

interface BookingMappingRepository : JpaRepository<BookingMapping, Long> {
    fun findByCountryIgnoreCase(country: String): List<BookingMapping>

    /** First row for case-insensitive consignee name (for merge-on-duplicate). */
    fun findFirstByConsigneeNameIgnoreCaseOrderByIdAsc(consigneeName: String): BookingMapping?
}
