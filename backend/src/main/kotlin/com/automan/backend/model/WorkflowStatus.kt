package com.automan.backend.model

/**
 * Canonical purchase workflow stage, derived from flags with fixed precedence:
 * INVOICE_CONFIRMED → BOOKING_REQUESTED → RIXO_CONFIRMED → RIXO_REQUESTED → PURCHASED.
 */
enum class WorkflowStatus {
    PURCHASED,
    RIXO_REQUESTED,
    RIXO_CONFIRMED,
    BOOKING_REQUESTED,
    INVOICE_CONFIRMED,
}
