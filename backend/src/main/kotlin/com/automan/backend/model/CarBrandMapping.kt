package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "car_brand_mapping")
data class CarBrandMapping(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "car_brand", nullable = false, length = 100)
    val carBrand: String,

    @Column(name = "chassis", length = 50)
    val chassis: String? = null,

    @Column(name = "car_name", length = 100)
    val carName: String? = null,

    @Column(name = "fuel", length = 50)
    val fuel: String? = null,

    @Column(name = "wd", length = 50)
    val wd: String? = null,

    @Column(name = "shift", length = 50)
    val shift: String? = null,

    @Column(name = "cc", length = 100)
    val cc: String? = null,

    @Column(name = "door", length = 100)
    val door: String? = null,

    @Column(name = "seat", length = 100)
    val seat: String? = null,

    @Column(name = "grade", length = 50)
    val grade: String? = null,

    @Column(name = "vehicle_type", length = 100)
    val vehicleType: String? = null,

    @Column(name = "`rank`", length = 50)
    val rank: String? = null,

    @Column(name = "color", length = 100)
    val color: String? = null,

    @Column(name = "drive_type", length = 20)
    val driveType: String? = null,

    @Column(name = "recycle_fee", columnDefinition = "TEXT")
    val recycleFee: String? = null,

    @Column(name = "car_model_year", columnDefinition = "TEXT")
    val carModelYear: String? = null,

    @Column(name = "chassis_number", columnDefinition = "TEXT")
    val chassisNumber: String? = null,

    @Column(name = "manufacture_year", columnDefinition = "TEXT")
    val manufactureYear: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

