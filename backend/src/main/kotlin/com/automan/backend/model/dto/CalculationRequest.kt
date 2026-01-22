package com.automan.backend.model.dto

data class CalculationRequest(
    val country: String? = null, // Country name instead of bookingId
    val containerPrice: Double,
    val shippingCharge: Double,
    val wcCharge: Double,
    val inspectionFee: Double,
    val fobPrice: Double,
    val freightPrice: Double,
    val insurance: Double,
    val packageOption: Boolean = false
)

data class CalculationResponse(
    val success: Boolean,
    val message: String,
    val totalPrice: Double,
    val breakdown: Map<String, Double>
)
