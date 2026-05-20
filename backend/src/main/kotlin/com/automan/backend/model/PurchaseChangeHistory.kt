package com.automan.backend.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "purchase_change_history")
data class PurchaseChangeHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "purchase_id", nullable = false)
    val purchaseId: Long,

    @Column(name = "chassis", nullable = false, length = 100)
    val chassis: String,

    @Column(name = "field_name", nullable = false, columnDefinition = "TEXT")
    val fieldName: String,

    @Column(name = "old_value", columnDefinition = "TEXT")
    val oldValue: String? = null,

    @Column(name = "new_value", columnDefinition = "TEXT")
    val newValue: String? = null,

    @Column(name = "changed_by", length = 256)
    val changedBy: String? = null,

    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime = LocalDateTime.now(),
)
