package com.automan.backend.dto

data class PurchaseChangeHistoryPageRequest(
    val purchaseIds: List<Long>,
    val historyPage: Int = 0,
    val historySize: Int = 20,
)

data class PurchaseChangeHistoryRowDto(
    val id: Long,
    val purchaseId: Long,
    val chassis: String,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val changedBy: String?,
    val changedAt: String,
)

data class PurchaseChangeHistoryPageResponse(
    val content: List<PurchaseChangeHistoryRowDto>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
)

/** Read-model for edit screen: excludes chassis and changedBy. */
data class PurchaseChangeHistorySingleRowDto(
    val changedAt: String,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
)
