package com.automan.backend.repository

import com.automan.backend.model.PurchaseMedia
import org.springframework.data.jpa.repository.JpaRepository

interface PurchaseMediaRepository : JpaRepository<PurchaseMedia, Long> {
    fun findByPurchaseIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(purchaseId: Long): List<PurchaseMedia>

    fun countByPurchaseIdAndDeletedAtIsNull(purchaseId: Long): Long

    fun findByIdAndPurchaseIdAndDeletedAtIsNull(id: Long, purchaseId: Long): PurchaseMedia?
}
