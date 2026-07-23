package com.automan.backend.dto

/** Paginated Rixo history list. */
data class RixoHistoryPageResponse(
    val content: List<RixoHistoryRowDto>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
