package com.automan.backend.repository

import com.automan.backend.model.PurchaseChangeHistory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface PurchaseChangeHistoryRepository : JpaRepository<PurchaseChangeHistory, Long> {
    fun findByPurchaseIdIn(purchaseIds: Collection<Long>, pageable: Pageable): Page<PurchaseChangeHistory>

    fun findByPurchaseId(purchaseId: Long, pageable: Pageable): Page<PurchaseChangeHistory>
}
