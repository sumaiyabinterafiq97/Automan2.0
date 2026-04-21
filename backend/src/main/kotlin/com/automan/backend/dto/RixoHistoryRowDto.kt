package com.automan.backend.dto

data class RixoHistoryRowDto(
    val id: Long,
    val buyingDate: String? = null,
    val rixoCompany: String? = null,
    val message: String? = null,
    val chassis: String? = null,
)
