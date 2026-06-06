package com.automan.backend.dto

enum class CreditLimitStatus {
    OK,
    NO_LIMIT,
    NEAR_LIMIT,
    OVER_LIMIT,
}

/**
 * Option A: positive balance = prepaid; negative = owed.
 * [projectedBalance] after posting [invoiceCharge] (replacing any open charge on [invoiceNumber]).
 */
data class ClientCreditLimitAssessment(
    val status: CreditLimitStatus,
    val clientId: Long,
    val clientName: String,
    val currentBalance: Double,
    val projectedBalance: Double,
    val creditLimit: Double?,
    val invoiceCharge: Double,
    val availableCreditAfter: Double?,
    val message: String? = null,
    val blocked: Boolean = false,
) {
    fun toResponseMap(): Map<String, Any?> = mapOf(
        "creditLimitStatus" to status.name,
        "creditLimitClientId" to clientId,
        "creditLimitClientName" to clientName,
        "currentBalance" to currentBalance,
        "projectedBalance" to projectedBalance,
        "creditLimit" to creditLimit,
        "invoiceCharge" to invoiceCharge,
        "availableCreditAfter" to availableCreditAfter,
        "creditLimitMessage" to message,
        "creditLimitBlocked" to blocked,
    )
}
