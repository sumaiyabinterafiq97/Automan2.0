package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

/**
 * Ensures Rixo history enrichment issues one batched chassis lookup per enrich call,
 * not one query per token.
 */
class PurchaseChassisLookupBatchQueryTest {

    @Test
    fun enrichRowsPage_callsFindByChassisKeyInOnceForManyTokens() {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val purchaseRepo = mock(PurchaseRepository::class.java)
        val row1 = RixoHistory(id = 1L, chassis = "A-1;B-2;C-3")
        val row2 = RixoHistory(id = 2L, chassis = "D-4;E-5")
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"))
        `when`(rixoRepo.findAll(pageable)).thenReturn(PageImpl(listOf(row1, row2), pageable, 2))

        val purchases = listOf(
            Purchase(id = 10L, chassis = "A-1", chassisKey = "a-1"),
            Purchase(id = 11L, chassis = "B-2", chassisKey = "b-2"),
            Purchase(id = 12L, chassis = "C-3", chassisKey = "c-3"),
            Purchase(id = 13L, chassis = "D-4", chassisKey = "d-4"),
            Purchase(id = 14L, chassis = "E-5", chassisKey = "e-5"),
        )
        `when`(purchaseRepo.findByChassisKeyIn(org.mockito.ArgumentMatchers.anyCollection()))
            .thenReturn(purchases)

        val svc = rixoHistoryService(rixoRepo, purchaseRepo)
        val page = svc.listRowsPage(page = 0, rawSize = 20, sortField = "id", sortOrder = "desc")

        assertEquals(2, page.content.size)
        verify(purchaseRepo, times(1)).findByChassisKeyIn(org.mockito.ArgumentMatchers.anyCollection())
    }
}
