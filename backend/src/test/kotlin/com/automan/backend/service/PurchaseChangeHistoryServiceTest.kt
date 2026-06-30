package com.automan.backend.service

import com.automan.backend.dto.PurchaseChangeHistoryPageRequest
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseChangeHistory
import com.automan.backend.repository.PurchaseChangeHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class PurchaseChangeHistoryServiceTest {

    @Test
    fun `pageScoped with empty purchase ids returns empty response`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val r = svc.pageScoped(PurchaseChangeHistoryPageRequest(purchaseIds = emptyList(), historyPage = 0, historySize = 20))
        assertEquals(0L, r.totalElements)
        assertEquals(0, r.content.size)
    }

    @Test
    fun `recordPurchasePartialEdit writes one row per changed field`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val before = Purchase(id = 6L, chassis = "ACV30", price = "0", auctionFee = "0")
        val after = before.copy(price = "100", auctionFee = "200", recycleFee = "300")
        svc.recordPurchasePartialEdit(before, after, "admin@test.com")
        verify(repo, times(3)).save(any(PurchaseChangeHistory::class.java))
    }
}
