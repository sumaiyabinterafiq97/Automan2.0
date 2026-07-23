package com.automan.backend.dto

data class ShippingSchedulePdfData(
    val companyName: String,
    val bookingNo: String,
    val vesselName: String,
    val pol: String,
    val pod: String,
    val shippingDate: String, // Formatted as "DD.MON.YYYY"
    val consigneeDetails: ConsigneeDetailsDto,
    val carList: List<CarPdfDto>,
    val calculationMode: String? = null, // "C&F" or "FOB"
    val carrier: String? = null,
    val cyCutDate: String? = null,
    val eta: String? = null,
    val finalDestination: String? = null,
    val notifyParty: String? = null,
    val inTransitClause: String? = null,
)
