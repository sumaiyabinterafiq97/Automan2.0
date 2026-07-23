package com.automan.backend.dto

import com.automan.backend.model.ClientMap

/** Paginated Client Map browse / search (client_map). */
data class ClientMapPageResponse(
    val content: List<ClientMap>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
)
