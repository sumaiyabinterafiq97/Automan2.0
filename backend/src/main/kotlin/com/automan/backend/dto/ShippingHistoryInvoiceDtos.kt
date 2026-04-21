package com.automan.backend.dto

data class ShippingHistoryInvoiceHeaderDto(
    val shipmentDate: String? = null,
    val pol: String? = null,
    val pod: String? = null,
    val priceType: String? = null,
)

data class ShippingHistoryInvoiceLineDto(
    val shippingHistoryId: Long,
    val chassis: String,
    val amount: String,
    val carName: String? = null,
    val carModelYear: String? = null,
    val purchaseId: Long? = null,
)

data class ShippingHistoryInvoiceSliceDto(
    val header: ShippingHistoryInvoiceHeaderDto,
    val lines: List<ShippingHistoryInvoiceLineDto>,
)
