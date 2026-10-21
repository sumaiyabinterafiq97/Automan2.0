package com.automan.backend.dto

data class PurchaseChassisHistorySyncPreviewDto(
    val rixo: List<RixoChassisSyncItemDto> = emptyList(),
    val shipping: List<ShippingChassisSyncItemDto> = emptyList(),
    val invoice: List<InvoiceChassisSyncItemDto> = emptyList(),
    val canApply: Boolean = true,
    val blockers: List<String> = emptyList(),
    val skippedReason: String? = null,
)

data class RixoChassisSyncItemDto(
    val historyId: Long,
    val buyingDate: String?,
    val rixoCompany: String?,
    val oldChassis: String,
    val newChassis: String,
    val chassisAfter: String?,
)

data class ShippingChassisSyncItemDto(
    val historyId: Long,
    val clientName: String?,
    val vessel: String?,
    val bookingId: String?,
    val oldChassis: String,
    val newChassis: String,
    val chassisAfter: String?,
)

data class InvoiceChassisSyncItemDto(
    val lineId: Long,
    val invoiceNumber: String,
    val sortOrder: Int,
    val oldChassis: String,
    val newChassis: String,
)

data class PurchaseChassisHistorySyncPreviewRequest(
    val newChassis: String? = null,
)
