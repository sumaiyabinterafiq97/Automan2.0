package com.automan.backend.dto

data class InvoiceLedgerResult(
    val posted: Boolean = false,
    val reversed: Boolean = false,
    val clientId: Long? = null,
    val clientCreated: Boolean = false,
    val warning: String? = null,
    val info: String? = null,
    val creditLimit: ClientCreditLimitAssessment? = null,
) {
    fun toResponseMap(): Map<String, Any?> = buildMap {
        put("ledgerPosted", posted)
        put("ledgerReversed", reversed)
        put("ledgerClientId", clientId)
        put("ledgerClientCreated", clientCreated)
        put("ledgerWarning", warning)
        put("ledgerInfo", info)
        creditLimit?.toResponseMap()?.forEach { (k, v) -> put(k, v) }
    }
}
