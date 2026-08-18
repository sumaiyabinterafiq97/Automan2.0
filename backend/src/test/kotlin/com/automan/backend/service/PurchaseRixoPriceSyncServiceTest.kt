package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoMapping
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseRixoPriceSyncServiceTest {

    private val purchaseRepository = mock(PurchaseRepository::class.java)
    private val costLineService = mock(PurchaseCostLineService::class.java)
    private val extendedService = mock(PurchaseExtendedAttributesService::class.java)
    private val vehicleService = mock(PurchaseVehicleOverrideService::class.java)
    private val service = PurchaseRixoPriceSyncService(
        purchaseRepository,
        costLineService,
        extendedService,
        vehicleService,
    )

    private fun stubHydration(purchase: Purchase) {
        `when`(extendedService.applyForRead(purchase)).thenReturn(purchase)
        `when`(vehicleService.applyForRead(purchase)).thenReturn(purchase)
        `when`(costLineService.applyForRead(purchase)).thenReturn(purchase)
    }

    @Test
    fun matchesMapping_requiresSupplierPath() {
        val mapping = RixoMapping(
            rixoCompany = "STYLISH AUTO",
            auctionName = "JU AICHI",
            stockLocation = "GLOBAL NAGOYA",
            venueId = "95518",
            pol = "NAGOYA",
            rixoPrice = "¥5,000",
        )
        val hit = Purchase(
            id = 1L,
            chassis = "KDH200-0028634",
            auctionHouse = "JU AICHI",
            stockLocation = "GLOBAL NAGOYA",
            rixoCompany = "STYLISH AUTO",
            venueId = "95518",
            pol = "NAGOYA",
            rixoPrice = "4000",
        )
        val missStock = hit.copy(stockLocation = "GLOBAL KAWASAKI")
        assertTrue(service.matchesMapping(hit, mapping))
        assertFalse(service.matchesMapping(missStock, mapping))
    }

    @Test
    fun syncIfPriceChanged_updatesMatchingPurchaseAndTotalDelta() {
        val purchase = Purchase(
            id = 7L,
            chassis = "KDH200-0028634",
            auctionHouse = "JU AICHI",
            stockLocation = "GLOBAL NAGOYA",
            rixoCompany = "STYLISH AUTO",
            venueId = "95518",
            pol = "NAGOYA",
            rixoPrice = "4000",
            totalPrice = "104000",
        )
        stubHydration(purchase)
        `when`(purchaseRepository.findByAuctionHouseIgnoreCaseTrim("JU AICHI")).thenReturn(listOf(purchase))
        val before = RixoMapping(
            id = 800L,
            rixoCompany = "STYLISH AUTO",
            auctionName = "JU AICHI",
            stockLocation = "GLOBAL NAGOYA",
            venueId = "95518",
            pol = "NAGOYA",
            rixoPrice = "¥4,000",
        )

        val result = service.syncIfPriceChanged(before, before.copy(rixoPrice = "¥5,000"))

        assertEquals(1, result.updatedCount)
        val captor = ArgumentCaptor.forClass(Purchase::class.java)
        verify(purchaseRepository, times(1)).save(captor.capture())
        assertEquals("5000", captor.value.rixoPrice)
        assertEquals("105000", captor.value.totalPrice)
        verify(costLineService, times(1)).syncFromPurchase(captor.value)
    }

    @Test
    fun syncIfPriceChanged_skipsWhenPriceUnchanged() {
        val before = RixoMapping(
            rixoCompany = "STYLISH AUTO",
            auctionName = "JU AICHI",
            stockLocation = "GLOBAL NAGOYA",
            rixoPrice = "5000",
        )
        val result = service.syncIfPriceChanged(before, before.copy(rixoPrice = "¥5,000"))
        assertEquals(0, result.updatedCount)
        verify(purchaseRepository, never()).findByAuctionHouseIgnoreCaseTrim("JU AICHI")
    }

    @Test
    fun syncIfPriceChanged_leavesNonMatchingPurchaseUntouched() {
        val other = Purchase(
            id = 8L,
            chassis = "OTHER-1",
            auctionHouse = "JU AICHI",
            stockLocation = "GLOBAL KAWASAKI",
            rixoCompany = "STYLISH AUTO",
            venueId = "95518",
            rixoPrice = "24000",
            totalPrice = "200000",
        )
        `when`(extendedService.applyForRead(other)).thenReturn(other)
        `when`(vehicleService.applyForRead(other)).thenReturn(other)
        `when`(costLineService.applyForRead(other)).thenReturn(other)
        `when`(purchaseRepository.findByAuctionHouseIgnoreCaseTrim("JU AICHI")).thenReturn(listOf(other))
        val before = RixoMapping(
            rixoCompany = "STYLISH AUTO",
            auctionName = "JU AICHI",
            stockLocation = "GLOBAL NAGOYA",
            venueId = "95518",
            rixoPrice = "4000",
        )
        val result = service.syncIfPriceChanged(before, before.copy(rixoPrice = "5000"))
        assertEquals(0, result.updatedCount)
        verify(purchaseRepository, never()).save(other)
        verify(costLineService, never()).syncFromPurchase(other)
    }
}
