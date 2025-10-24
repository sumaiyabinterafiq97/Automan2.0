package com.automan.backend.model.dto

import com.automan.backend.model.BookingStatus
import java.time.LocalDate

data class BookingRequest(
    val vesselNo: String?,
    val vesselName: String?,
    val consigneeCountry: String?,
    val polPort: String?,
    val bookingDate: LocalDate?,
    val status: BookingStatus = BookingStatus.DRAFT
)

data class BookingResponse(
    val id: Long,
    val bookingNumber: String,
    val vesselNo: String?,
    val vesselName: String?,
    val consigneeCountry: String?,
    val polPort: String?,
    val bookingDate: LocalDate?,
    val status: BookingStatus,
    val createdAt: String,
    val updatedAt: String
)

data class CarSelectionRequest(
    val bookingId: Long,
    val carIds: List<Long>
)

data class CarSelectionResponse(
    val success: Boolean,
    val message: String,
    val selectedCars: List<CarInfo>
)

data class CarInfo(
    val id: Long,
    val chassis: String,
    val carName: String?,
    val carModelYear: String?,
    val brand: String?
)

data class CalculationRequest(
    val bookingId: Long,
    val containerPrice: Double = 0.0,
    val shippingCharge: Double = 0.0,
    val wcCharge: Double = 0.0,
    val inspectionFee: Double = 0.0,
    val fobPrice: Double = 0.0,
    val freightPrice: Double = 0.0,
    val insurance: Double = 0.0,
    val packageOption: Boolean = false
)

data class CalculationResponse(
    val success: Boolean,
    val message: String,
    val totalPrice: Double,
    val breakdown: Map<String, Double>
)
