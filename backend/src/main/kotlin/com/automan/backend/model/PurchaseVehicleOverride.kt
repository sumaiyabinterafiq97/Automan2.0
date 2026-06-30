package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "purchase_vehicle_overrides")
data class PurchaseVehicleOverride(
    @Id
    @Column(name = "purchase_id", nullable = false)
    val purchaseId: Long,

    @Column(name = "overrides", columnDefinition = "JSON", nullable = false)
    val overridesJson: String = "{}",

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime? = null,
)
