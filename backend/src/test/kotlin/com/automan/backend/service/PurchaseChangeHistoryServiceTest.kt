package com.automan.backend.service

import com.automan.backend.dto.PurchaseChangeHistoryPageRequest
import com.automan.backend.repository.PurchaseChangeHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class PurchaseChangeHistoryServiceTest {

    @Test
    fun `pageScoped with empty purchase ids returns empty response`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val r = svc.pageScoped(PurchaseChangeHistoryPageRequest(purchaseIds = emptyList(), historyPage = 0, historySize = 20))
        assertEquals(0L, r.totalElements)
        assertEquals(0, r.content.size)
    }
}
