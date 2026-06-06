package com.automan.backend.dto

data class OpeningBalanceImportRowDto(
    val clientNumber: String,
    val eventDate: String,
    val amount: Double,
    val note: String? = null,
)

data class OpeningBalanceImportRequest(
    val rows: List<OpeningBalanceImportRowDto>,
)

data class OpeningBalanceImportResult(
    val imported: Int,
    val skipped: Int,
    val errors: List<String>,
)
