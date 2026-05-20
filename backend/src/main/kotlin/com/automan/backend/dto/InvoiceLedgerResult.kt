package com.automan.backend.dto

data class InvoiceLedgerResult(
    val posted: Boolean = false,
    val reversed: Boolean = false,
    val clientId: Long? = null,
    val clientCreated: Boolean = false,
    val warning: String? = null,
    val info: String? = null,
) {
    fun toResponseMap(): Map<String, Any?> = mapOf(
        "ledgerPosted" to posted,
        "ledgerReversed" to reversed,
        "ledgerClientId" to clientId,
        "ledgerClientCreated" to clientCreated,
        "ledgerWarning" to warning,
        "ledgerInfo" to info,
    )
}
