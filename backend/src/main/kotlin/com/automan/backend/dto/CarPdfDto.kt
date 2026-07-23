package com.automan.backend.dto

data class CarPdfDto(
    val no: Int,
    val name: String,
    val chassisNumber: String,
    val year: String,
    val cnfPrice: String, // Formatted as "¥XXX,XXX"
    val maker: String? = null,
    val model: String? = null,
)
