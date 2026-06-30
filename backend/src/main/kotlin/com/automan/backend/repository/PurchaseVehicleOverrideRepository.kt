package com.automan.backend.repository

import com.automan.backend.model.PurchaseVehicleOverride
import org.springframework.data.jpa.repository.JpaRepository

interface PurchaseVehicleOverrideRepository : JpaRepository<PurchaseVehicleOverride, Long> {
    fun findByPurchaseId(purchaseId: Long): PurchaseVehicleOverride?
}
