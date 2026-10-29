package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.model.Purchase
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseVehicleOverrideRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseVehicleOverrideBatchPrefixTest {

    private val overrideRepository = mock(PurchaseVehicleOverrideRepository::class.java)
    private val mappingRepository = mock(CarBrandMappingRepository::class.java)
    private val batchQueries = mock(ChassisBatchQuerySupport::class.java)
    private val service = PurchaseVehicleOverrideService(
        overrideRepository,
        mappingRepository,
        batchQueries,
        ObjectMapper(),
    )

    @Test
    fun applyForReadBatch_usesOnePrefixPoolQueryForManyMisses() {
        val short = CarBrandMapping(id = 1L, carBrand = "TOYOTA", chassis = "MAP", fuel = "GAS", grade = "G")
        val long = CarBrandMapping(id = 2L, carBrand = "TOYOTA", chassis = "MAP-ONLY", fuel = "DIESEL", grade = "S")
        `when`(mappingRepository.findByChassisIn(anyCollection())).thenReturn(emptyList())
        `when`(batchQueries.findMappingPrefixCandidatesForFullChassis(anyCollection()))
            .thenReturn(listOf(short, long))

        val purchases = listOf(
            Purchase(id = 1L, chassis = "MAP-ONLY-1", brand = "TOYOTA"),
            Purchase(id = 2L, chassis = "MAP-ONLY-2", brand = "TOYOTA"),
        )
        `when`(overrideRepository.findByPurchaseIdIn(listOf(1L, 2L))).thenReturn(emptyList())

        val merged = service.applyForReadBatch(purchases)
        assertEquals("DIESEL", merged[0].fuel)
        assertEquals("S", merged[0].grade)
        assertEquals("DIESEL", merged[1].fuel)
        verify(batchQueries, times(1)).findMappingPrefixCandidatesForFullChassis(anyCollection())
        verify(mappingRepository, never()).findBestPrefixMatchForChassis(org.mockito.ArgumentMatchers.anyString())
    }

    @Test
    fun prefixCandidates_prefersLongestPrefixThenHighestId() {
        val short = CarBrandMapping(id = 10L, carBrand = "X", chassis = "AB", fuel = "A")
        val long = CarBrandMapping(id = 11L, carBrand = "X", chassis = "ABC", fuel = "B")
        val pool = listOf(short, long)
        val ordered = service.prefixCandidatesByFullChassis(listOf("ABC-9"), pool)["ABC-9"]!!
        assertEquals("B", ordered.first().fuel)
    }

    @Test
    fun pickBaselineCandidate_prefersBrandMatchAmongPrefixCandidates() {
        val otherBrand = CarBrandMapping(id = 1L, carBrand = "HONDA", chassis = "ZZ", fuel = "X")
        val toyota = CarBrandMapping(id = 2L, carBrand = "TOYOTA", chassis = "ZZ", fuel = "Y")
        `when`(mappingRepository.findByChassisIn(anyCollection())).thenReturn(emptyList())
        `when`(batchQueries.findMappingPrefixCandidatesForFullChassis(anyCollection()))
            .thenReturn(listOf(otherBrand, toyota))
        `when`(overrideRepository.findByPurchaseIdIn(listOf(3L))).thenReturn(emptyList())

        val merged = service.applyForReadBatch(
            listOf(Purchase(id = 3L, chassis = "ZZ-1", brand = "TOYOTA")),
        )
        assertEquals("Y", merged.single().fuel)
    }
}
