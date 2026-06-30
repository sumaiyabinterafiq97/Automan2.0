package com.automan.backend.repository

import com.automan.backend.model.PurchaseCostLine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PurchaseCostLineRepository : JpaRepository<PurchaseCostLine, Long> {
    fun findByPurchaseIdOrderBySortOrderAsc(purchaseId: Long): List<PurchaseCostLine>
    fun findByPurchaseIdIn(purchaseIds: Collection<Long>): List<PurchaseCostLine>
}
