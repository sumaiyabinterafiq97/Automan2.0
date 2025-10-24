package com.automan.backend.dto

data class ShippingSchedulePdfRequest(
    val bookingNo: String,
    val vesselName: String,
    val pol: String, // Port of Loading
    val pod: String, // Port of Discharge
    val shippingDate: String,
    val consigneeName: String,
    val consigneeAddress: String,
    val chassisNumbers: List<String>
)
