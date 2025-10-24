package com.automan.backend.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

enum class CalculationType { FREIGHT, CAF, FOB, PAKISTAN }

@Entity
@Table(name = "booking_calculations")
data class BookingCalculation(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "booking_id", nullable = false)
    val bookingId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "calculation_type", nullable = false, length = 20)
    val calculationType: CalculationType,

    @Column(name = "container_price", precision = 15, scale = 2)
    val containerPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "shipping_charge", precision = 15, scale = 2)
    val shippingCharge: BigDecimal = BigDecimal.ZERO,

    @Column(name = "wc_charge", precision = 15, scale = 2)
    val wcCharge: BigDecimal = BigDecimal.ZERO,

    @Column(name = "inspection_fee", precision = 15, scale = 2)
    val inspectionFee: BigDecimal = BigDecimal.ZERO,

    @Column(name = "fob_price", precision = 15, scale = 2)
    val fobPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "freight_price", precision = 15, scale = 2)
    val freightPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "insurance", precision = 15, scale = 2)
    val insurance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_price", precision = 15, scale = 2)
    val totalPrice: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
