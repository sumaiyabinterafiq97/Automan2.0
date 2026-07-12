package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "shipping_history",
    uniqueConstraints = [
        UniqueConstraint(name = "ux_shipping_history_chassis", columnNames = ["chassis"]),
    ],
)
data class ShippingHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "country")
    val country: String? = null,

    @Column(name = "consignee")
    val consignee: String? = null,

    @Column(name = "shipment_date")
    val shipmentDate: LocalDate? = null,

    @Column(name = "pol")
    val pol: String? = null,

    @Column(name = "pod")
    val pod: String? = null,

    @Column(name = "booking_id")
    val bookingId: String? = null,

    @Column(name = "vessel")
    val vessel: String? = null,

    @Column(name = "carrier")
    val carrier: String? = null,

    @Column(name = "bl_no")
    val blNo: String? = null,

    @Column(name = "price_type")
    val priceType: String? = null,

    @Column(name = "chassis", nullable = false)
    val chassis: String,

    @Column(name = "client_name")
    val clientName: String? = null,

    @Column(name = "amount", precision = 19, scale = 2, nullable = false)
    val amount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
)
