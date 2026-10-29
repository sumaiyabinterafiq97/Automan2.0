package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseChassisLookupServiceTest {

    private val repo = mock(PurchaseRepository::class.java)
    private val batch = mock(ChassisBatchQuerySupport::class.java)
    private val lookup = PurchaseChassisLookupService(repo, batch)

    @Test
    fun findByExactTokens_returnsAllMatchesForDuplicateKeys() {
        val p1 = Purchase(id = 1L, chassis = "DUP-1", chassisKey = "dup-1")
        val p2 = Purchase(id = 2L, chassis = "dup-1", chassisKey = "dup-1")
        `when`(repo.findByChassisKeyIn(listOf("dup-1"))).thenReturn(listOf(p1, p2))

        val hits = lookup.findByExactTokens(listOf("DUP-1"))
        assertEquals(2, hits.size)
    }

    @Test
    fun resolveShippingChassisTokens_exactBeforePrefix() {
        val exact = Purchase(id = 1L, chassis = "TOK-99", chassisKey = "tok-99")
        `when`(repo.findByChassisKeyIn(listOf("tok"))).thenReturn(emptyList())
        `when`(batch.findPurchasesByCodePrefixes(listOf("tok"))).thenReturn(listOf(exact))

        val hits = lookup.resolveShippingChassisTokens(listOf("TOK"))
        assertEquals(1, hits.size)
        assertEquals(1L, hits.first().id)
    }

    @Test
    fun resolveShippingChassisTokens_skipsEmptyTokens() {
        assertTrue(lookup.resolveShippingChassisTokens(listOf(" ", "")).isEmpty())
    }

    @Test
    fun resolveShippingChassisTokens_batchesCodeOnlyPrefixLookups() {
        val p1 = Purchase(id = 1L, chassis = "AAA-1", chassisKey = "aaa-1")
        val p2 = Purchase(id = 2L, chassis = "BBB-2", chassisKey = "bbb-2")
        `when`(repo.findByChassisKeyIn(anyCollection())).thenReturn(emptyList())
        `when`(batch.findPurchasesByCodePrefixes(anyCollection())).thenReturn(listOf(p1, p2))

        val hits = lookup.resolveShippingChassisTokens(listOf("AAA", "BBB"))
        assertEquals(2, hits.size)
        verify(batch, times(1)).findPurchasesByCodePrefixes(anyCollection())
    }
}
