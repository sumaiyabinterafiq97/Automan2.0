package com.automan.backend.dto

/** Result of resolving an invoice client name to a ledger client (Phase 2b). */
sealed class ClientNameLedgerResolution {
    data class Ok(val clientId: Long, val created: Boolean) : ClientNameLedgerResolution()
    data class Skipped(val warning: String) : ClientNameLedgerResolution()
}

/** Preview only — does not create a client row. */
data class ClientLedgerPreview(
    val clientId: Long? = null,
    val ledgerResolvable: Boolean = false,
    val willCreateClient: Boolean = false,
    val warning: String? = null,
)
