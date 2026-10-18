package com.automan.backend.portal

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * Allowlist responses for the client and consignee portals.
 * These types are the only objects portal controllers return.
 * Staff entities are not serialized on these routes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PortalMeDto(
    val linked: Boolean,
    val active: Boolean,
    val clientNumber: String? = null,
    val clientName: String? = null,
    val legalName: String? = null,
)

data class ClientDashboardDto(
    val purchasedCount: Long,
    val shippedCount: Long,
    val invoicedCount: Long,
)

data class ConsigneeDashboardDto(
    val purchasedCount: Long,
    val shippedCount: Long,
    val invoicedCount: Long,
    val clientCount: Long,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PortalCarDto(
    val purchaseId: Long,
    val chassis: String,
    val carName: String? = null,
    val year: String? = null,
    val stockLocation: String? = null,
    val country: String? = null,
    val shipped: Boolean,
    val clientName: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PortalClientMapDto(
    val clientName: String,
    val country: String? = null,
    val pod: String? = null,
    val address: String? = null,
    val bankInfo: String? = null,
    val consignee: String? = null,
    val debitLimit: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ClientShipmentDto(
    val bookingId: String? = null,
    val vessel: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val shipmentDate: String? = null,
    val cyCutDate: String? = null,
    val eta: String? = null,
    val chassis: List<String>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConsigneeShippingHistoryDto(
    val bookingId: String,
    val consignee: String,
    val shipmentDate: String? = null,
    val pol: String? = null,
    val stockLocations: List<String>,
    val chassis: List<String>,
    val clientNames: List<String>,
    val units: Int,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConsigneeShipmentDto(
    val bookingId: String? = null,
    val vessel: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val shipmentDate: String? = null,
    val cyCutDate: String? = null,
    val eta: String? = null,
    val chassis: List<String>,
    val clientNames: List<String>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PortalInvoiceDto(
    val invoiceNumber: String,
    val date: String? = null,
    val vessel: String? = null,
    val chassis: List<String>,
    val priceType: String? = null,
)

/**
 * Date and type only. Staff event descriptions and bill numbers are free text and can contain
 * amounts, bank details, or internal costs, so they stay on the staff ledger and are not copied here.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PortalTransactionDto(
    val date: String,
    val type: String,
)

data class PortalMediaUrlDto(
    val url: String,
)

/** One in-scope purchase for the client purchase list. [purchase] uses the staff purchase field names. */
data class PortalPurchaseListItemDto(
    val mediaId: Long? = null,
    val purchase: Map<String, Any?>,
)

/** Same purchase fields as the edit screen, plus image ids the client may view. */
data class PortalPurchaseDetailDto(
    val purchase: Map<String, Any?>,
    val mediaIds: List<Long>,
)

data class PortalLedgerEventDto(
    val id: Long,
    val eventDate: String,
    val eventType: String,
    val eventDescription: String? = null,
    val billNumber: String? = null,
    val invoiceNumber: String? = null,
    val transactionPrice: Double? = null,
    val paymentReceived: Double? = null,
    val runningBalance: Double,
)

data class PortalLedgerDto(
    val clientName: String,
    val clientNumber: String,
    val status: String,
    val currency: String,
    val creditLimit: Double? = null,
    val alertThreshold: Double? = null,
    val currentBalance: Double,
    val availableCredit: Double? = null,
    val events: List<PortalLedgerEventDto>,
)

data class PortalPurchaseHistoryDto(
    val changedAt: String,
    val fieldName: String,
    val oldValue: String? = null,
    val newValue: String? = null,
)

/**
 * Resolved on every portal request from the session user, role, and current database rows.
 * Party ids are never taken from the bearer token or from request parameters.
 */
data class PortalScope(
    val active: Boolean,
    val clientIds: List<Long>,
    val me: PortalMeDto,
    /** Set for an active consignee. Purchases with this consignee text are included without reassigning clients. */
    val consigneeLegalName: String? = null,
)
