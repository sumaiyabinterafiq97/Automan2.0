package com.automan.backend.repository

import com.automan.backend.model.PurchaseFieldRegistry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Read-only access to schema metadata; no write APIs in Phase 1. */
@Repository
interface PurchaseFieldRegistryRepository : JpaRepository<PurchaseFieldRegistry, Long> {
    fun findByColumnName(columnName: String): PurchaseFieldRegistry?
}
