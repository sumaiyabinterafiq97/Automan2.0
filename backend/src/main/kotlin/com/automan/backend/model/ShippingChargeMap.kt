package com.automan.backend.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "shipping_charge_map",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_shipping_charge_stock_cars", columnNames = ["stock_location", "cars_per_container"]),
    ],
)
@JsonIgnoreProperties(ignoreUnknown = true)
data class ShippingChargeMap(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "stock_location", nullable = false, length = 100)
    val stockLocation: String,

    @Column(name = "cars_per_container", nullable = false)
    val carsPerContainer: Int,

    @Column(name = "shipping_price_per_car", nullable = false, precision = 18, scale = 2)
    val shippingPricePerCar: BigDecimal,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)
