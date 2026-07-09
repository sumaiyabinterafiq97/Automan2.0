package com.automan.backend.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

enum class MediaStorageProvider {
    R2,
}

@Entity
@Table(name = "purchase_media")
data class PurchaseMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "purchase_id", nullable = false)
    val purchaseId: Long,

    @Column(name = "chassis", nullable = false, length = 100)
    val chassis: String,

    @Column(name = "file_key", nullable = false, length = 512)
    val fileKey: String,

    @Column(name = "original_name", length = 255)
    val originalName: String? = null,

    @Column(name = "content_type", nullable = false, length = 64)
    val contentType: String,

    @Column(name = "file_size", nullable = false)
    val fileSize: Int,

    @Column(name = "sort_order", nullable = false)
    val sortOrder: Int = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false)
    val storageProvider: MediaStorageProvider = MediaStorageProvider.R2,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "created_by", length = 120)
    val createdBy: String? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,
)
