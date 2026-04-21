package com.automan.backend.dto

data class ShippingSchedulePdfRequest(
    val bookingNo: String,
    val vesselName: String,
    val pol: String, // Port of Loading
    val pod: String, // Port of Discharge
    val shippingDate: String,
    val consigneeName: String,
    val consigneeAddress: String,
    /** Consignee country selected on booking form — used with POD to pick the right `booking_mappings` row when names repeat. */
    val consigneeCountry: String? = null,
    val chassisNumbers: List<String>,
    val calculationMode: String? = null // "C&F" or "FOB" - determines PDF column header
)
