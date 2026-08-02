package com.automan.backend.dto

data class ShippingHistoryInvoiceHeaderDto(
    val shipmentDate: String? = null,
    val cyCutDate: String? = null,
    val eta: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val finalDestination: String? = null,
    val priceType: String? = null,
    val consignee: String? = null,
    val notifyParty: String? = null,
    val bookingId: String? = null,
    val vessel: String? = null,
    val carrier: String? = null,
)

data class ShippingHistoryInvoiceLineDto(
    val shippingHistoryId: Long,
    val chassis: String,
    val amount: String,
    val carName: String? = null,
    val carModelYear: String? = null,
    val purchaseId: Long? = null,
    /** Purchase brand / maker (optional; used by Client-Based Shipment Details UI). */
    val brand: String? = null,
)

data class ShippingHistoryInvoiceSliceDto(
    val header: ShippingHistoryInvoiceHeaderDto,
    val lines: List<ShippingHistoryInvoiceLineDto>,
)
