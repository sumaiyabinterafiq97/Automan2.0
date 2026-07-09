package com.automan.backend.dto

data class PurchaseMediaDto(
    val id: Long,
    val purchaseId: Long,
    val chassis: String,
    val originalName: String?,
    val contentType: String,
    val fileSize: Int,
    val sortOrder: Int,
    val url: String?,
    val createdAt: String,
)

data class PurchaseMediaOrderRequest(
    val mediaIds: List<Long>,
)

data class MediaConfigDto(
    val r2Enabled: Boolean,
    val maxFileSizeBytes: Long,
    val maxFilesPerPurchase: Int,
)

data class CarPictureMigrationResultDto(
    val dryRun: Boolean,
    val processedPurchases: Int,
    val uploadedFiles: Int,
    val skippedPurchases: Int,
    val failedPurchases: Int,
    val errors: List<String>,
)
