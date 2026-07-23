package com.automan.backend.dto

import com.automan.backend.model.Client

/** Paginated Client Accounts list browse / search. */
data class ClientPageResponse(
    val content: List<Client>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
