package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.util.ChassisKeyNormalizer
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`

internal fun purchaseChassisLookup(repo: PurchaseRepository): PurchaseChassisLookupService =
    PurchaseChassisLookupService(repo, ChassisBatchQuerySupport(org.mockito.Mockito.mock(jakarta.persistence.EntityManager::class.java)))

/** Stubs batched and single-key chassis lookups for unit tests. */
internal fun stubChassisPurchases(repo: PurchaseRepository, purchases: List<Purchase>) {
    val byKey = purchases.groupBy { ChassisKeyNormalizer.normalize(it.chassis) }
    `when`(repo.findByChassisKeyIn(anyCollection())).thenAnswer { inv ->
        @Suppress("UNCHECKED_CAST")
        val keys = inv.arguments[0] as Collection<String>
        keys.flatMap { byKey[it].orEmpty() }.distinctBy { it.id }
    }
    for ((key, list) in byKey) {
        if (key.isNotEmpty()) {
            `when`(repo.findByChassisKey(key)).thenReturn(list)
        }
    }
    `when`(repo.findByChassisKeyPrefix(anyString())).thenReturn(emptyList())
}

internal fun rixoHistoryService(
    rixoRepo: RixoHistoryRepository,
    purchaseRepo: PurchaseRepository,
    workflow: PurchaseWorkflowService = PurchaseWorkflowService(purchaseRepo),
    overlapCandidateQuery: RixoHistoryOverlapCandidateQuery? = null,
): RixoHistoryService {
    val overlap = overlapCandidateQuery
        ?: org.mockito.Mockito.mock(RixoHistoryOverlapCandidateQuery::class.java).also { mock ->
            org.mockito.Mockito.`when`(mock.findCandidates(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(emptyList())
        }
    return RixoHistoryService(
        rixoRepo,
        purchaseRepo,
        purchaseChassisLookup(purchaseRepo),
        workflow,
        org.mockito.Mockito.mock(ShippingHistoryService::class.java),
        org.mockito.Mockito.mock(InvoiceHistoryService::class.java),
        overlap,
    )
}
