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

    @Column(name = "cc")
    val cc: Int? = null,

    @Column(name = "door")
    val door: Int? = null,

    @Column(name = "grade", length = 50)
    val grade: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

