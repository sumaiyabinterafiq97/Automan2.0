package com.automan.backend.dto

/** Paginated Car Brands Map search (chassis / brand / car name / all). */
data class CarBrandMappingPageResponse(
    val content: List<Map<String, Any>>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
