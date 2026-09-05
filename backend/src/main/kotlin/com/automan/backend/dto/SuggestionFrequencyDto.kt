package com.automan.backend.dto

/** Purchase-history counts for supplier disambiguation modal order (keys are lowercased). */
data class SuggestionFrequencyDto(
    val stockLocation: Map<String, Long> = emptyMap(),
    val rixoCompany: Map<String, Long> = emptyMap(),
    val rixoCompanyByStock: Map<String, Map<String, Long>> = emptyMap(),
)
