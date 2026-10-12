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

/**
 * Resolved on every portal request from the session user, role, and current database rows.
 * Party ids are never taken from the bearer token or from request parameters.
 */
data class PortalScope(
    val active: Boolean,
    val clientIds: List<Long>,
    val me: PortalMeDto,
)
