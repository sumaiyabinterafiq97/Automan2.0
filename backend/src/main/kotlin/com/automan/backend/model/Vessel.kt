package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "vessels")
data class Vessel(
    @Id
    @Column(name = "vessel_no", length = 100)
    val vesselNo: String,

    @Column(name = "vessel_name", nullable = false, length = 200)
    val vesselName: String,

    @Column(name = "company", length = 100)
    val company: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
