package com.automan.backend.dto

import java.time.LocalDate

data class UnpaidInvoiceAgingRowDto(
    val clientId: Long,
    val clientNumber: String,
    val clientName: String,
    val invoiceNumber: String,
    val invoiceDate: LocalDate,
    val openAmount: Double,
    val daysOutstanding: Long,
    val agingBucket: String,
)

data class ClientAgingSummaryDto(
    val clientId: Long,
    val clientNumber: String,
    val clientName: String,
    val bucket0to30: Double,
    val bucket31to60: Double,
    val bucket61to90: Double,
    val bucket90Plus: Double,
    val totalOpen: Double,
)

data class UnpaidAgingReportDto(
    val asOfDate: LocalDate,
    val rows: List<UnpaidInvoiceAgingRowDto>,
    val summaries: List<ClientAgingSummaryDto>,
    val totalOpen: Double,
)
