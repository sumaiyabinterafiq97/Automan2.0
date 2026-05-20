package com.automan.purchase.models

import kotlinx.serialization.Serializable

/**
 * Data classes for API request/response models
 * Provides type safety instead of using dynamic
 */

@Serializable
data class PurchaseResponse(
    val id: Long? = null,
    val chassis: String,
    val carName: String? = null,
    val date: String? = null,
    val auctionNo: String? = null,
    val grade: String? = null,
    val shaken: Boolean? = null,
    val numberCutPlace: String? = null,
    val numberCutNumber: String? = null,
    val numberCutHiragana: String? = null,
    val rank: String? = null,
    val color: String? = null,
    val mileage: String? = null,
    val transmission: String? = null,
    val driveType: String? = null,
    val seat: Int? = null,
    val door: Int? = null,
    val options: String? = null,
    val auctionName: String? = null,
    val venueId: String? = null,
    val rixoCompany: String? = null,
    val stockLocation: String? = null,
    val rixoPrice: String? = null,
    val rixoRequested: String? = null,
    val rixoConfirmed: String? = null,
    val price: String? = null,
    val totalPrice: String? = null,
    val auctionFees: String? = null,
    val recycleFees: String? = null,
    val roadTax: String? = null,
    val paymentDate: String? = null,
    val clientName: String? = null,
    val targetCountry: String? = null,
    val vessel: String? = null,
    val shipmentDate: String? = null,
    val fromLocation: String? = null,
    val toLocation: String? = null,
    val shipmentCharges: String? = null,
    val miscCharges: String? = null,
    val repairCharges: String? = null,
    val totalCostBeforeTax: String? = null,
    val totalCostAfterTax: String? = null,
    val totalExpense: String? = null,
    val bookingId: Long? = null,
    val bookingRequested: Boolean? = null,
    val sold: Boolean? = null,
    val invoiceConfirmed: Boolean? = null,
    val workflowStatus: String? = null,
    val workflowStatusUpdatedAt: String? = null,
    val carModelYear: String? = null,
    val consignee: String? = null
)

@Serializable
data class ClientResponse(
    val id: Long? = null,
    val clientNumber: String,
    val clientName: String,
    val status: String? = null,
    val currency: String? = null,
    val creditLimit: Double? = null,
    val alertThreshold: Double? = null,
    val currentBalance: Double = 0.0
)

@Serializable
data class TransactionResponse(
    val success: Boolean,
    val transactionId: Long? = null,
    val message: String,
    val runningBalance: Double? = null
)

@Serializable
data class BookingMappingResponse(
    val id: Long? = null,
    val country: String,
    val pol: String? = null,
    val pod: String? = null,
    val consignee: String? = null
)

@Serializable
data class ApiErrorResponse(
    val message: String? = null,
    val error: String? = null,
    val status: Int? = null
)

@Serializable
data class ImportResponse(
    val success: Boolean,
    val message: String,
    val importedCount: Int = 0,
    val duplicateCount: Int = 0,
    val errorCount: Int = 0,
    val totalProcessed: Int = 0,
    val importedPurchases: List<PurchaseResponse> = emptyList(),
    val duplicateDetails: List<String> = emptyList(),
    val errorDetails: List<String> = emptyList()
)
