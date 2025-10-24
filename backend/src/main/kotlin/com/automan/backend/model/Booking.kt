package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

enum class BookingStatus { DRAFT, CONFIRMED, SHIPPED }

@Entity
@Table(name = "bookings")
data class Booking(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "booking_number", nullable = false, unique = true, length = 50)
    val bookingNumber: String,

    @Column(name = "vessel_no", length = 100)
    val vesselNo: String?,

    @Column(name = "vessel_name", length = 200)
    val vesselName: String?,

    @Column(name = "consignee_country", length = 100)
    val consigneeCountry: String?,

    @Column(name = "pol_port", length = 100)
    val polPort: String?,

    @Column(name = "booking_date")
    val bookingDate: LocalDate?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: BookingStatus = BookingStatus.DRAFT,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @PreUpdate
    fun preUpdate() {
        // This will be called before updating to set the update time
    }
}
