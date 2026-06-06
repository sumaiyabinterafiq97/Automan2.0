package com.automan.backend.dto

import java.time.LocalDate

data class ClientStatementLineDto(
    val date: LocalDate,
    val typeLabel: String,
    val reference: String?,
    val description: String?,
    val credit: Double?,
    val debit: Double?,
    val balance: Double,
)

data class ClientStatementDto(
    val clientNumber: String,
    val clientName: String,
    val currency: String,
    val creditLimit: Double?,
    val currentBalance: Double,
    val availableCredit: Double?,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val generatedAt: LocalDate,
    val balanceLabel: String,
    val lines: List<ClientStatementLineDto>,
)
