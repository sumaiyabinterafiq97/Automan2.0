package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Read-only schema metadata for purchases column consolidation (Phase 1).
 * Seeded by Flyway V41; not used on critical runtime paths until later phases.
 */
@Entity
@Table(name = "purchase_field_registry")
data class PurchaseFieldRegistry(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "column_name", nullable = false, unique = true, length = 128)
    val columnName: String,

    @Column(name = "kotlin_property", length = 128)
    val kotlinProperty: String? = null,

    @Column(name = "json_api_key", length = 255)
    val jsonApiKey: String? = null,

    @Column(name = "mysql_type", length = 64)
    val mysqlType: String? = null,

    @Column(name = "classification", nullable = false, length = 64)
    val classification: String,

    @Column(name = "target_phase", length = 16)
    val targetPhase: String? = null,

    @Column(name = "drop_candidate", length = 16)
    val dropCandidate: String? = null,

    @Column(name = "is_indexed", length = 64)
    val isIndexed: String? = null,

    @Column(name = "query_critical", length = 16)
    val queryCritical: String? = null,

    @Column(name = "car_brand_mapping_column", length = 128)
    val carBrandMappingColumn: String? = null,

    @Column(name = "cost_code_or_json_key", length = 128)
    val costCodeOrJsonKey: String? = null,

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    @Column(name = "review_decision", length = 32)
    val reviewDecision: String? = null,

    @Column(name = "created_at")
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at")
    val updatedAt: LocalDateTime? = null,
)
