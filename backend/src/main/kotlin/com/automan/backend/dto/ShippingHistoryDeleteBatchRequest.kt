package com.automan.backend.dto

data class ShippingHistoryDeleteBatchRequest(
    val ids: List<Long> = emptyList(),
)
