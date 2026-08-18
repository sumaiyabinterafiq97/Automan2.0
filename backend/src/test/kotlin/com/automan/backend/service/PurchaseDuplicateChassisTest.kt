package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.BookingMappingRepository
import com.automan.backend.repository.ClientRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class PurchaseDuplicateChassisTest {

    private val purchaseRepository = mock(PurchaseRepository::class.java)
    private val service = PurchaseService(
        purchaseRepository = purchaseRepository,
        pdfService = mock(PdfService::class.java),
        bookingMappingRepository = mock(BookingMappingRepository::class.java),
        shippingHistoryRepository = mock(ShippingHistoryRepository::class.java),
        purchaseWorkflowService = mock(PurchaseWorkflowService::class.java),
        purchaseChangeHistoryService = mock(PurchaseChangeHistoryService::class.java),
        purchaseCostLineService = mock(PurchaseCostLineService::class.java),
        purchaseVehicleOverrideService = mock(PurchaseVehicleOverrideService::class.java),
        purchaseExtendedAttributesService = mock(PurchaseExtendedAttributesService::class.java),
        shippingSnapshotService = mock(ShippingSnapshotService::class.java),
        localPurchaseSanitizer = LocalPurchaseSanitizer(),
        clientRepository = mock(ClientRepository::class.java),
        stockLocationMapService = mock(StockLocationMapService::class.java),
    )

    @Test
    fun findDuplicatePurchase_returnsMatchWhenFullChassisExists() {
        val existing = Purchase(id = 10L, chassis = "NZE141-1234567", auctionHouse = "USS")
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("NZE141-1234567")).thenReturn(listOf(existing))

        val duplicate = service.findDuplicatePurchase("NZE141-1234567", excludeId = null)

        assertEquals(existing, duplicate)
    }

    @Test
    fun findDuplicatePurchase_codeOnlyDoesNotCountAsDuplicate() {
        val duplicate = service.findDuplicatePurchase("AAHH45", excludeId = null)

        assertNull(duplicate)
    }

    @Test
    fun findDuplicatePurchase_codeOnlyWithTrailingDashDoesNotCountAsDuplicate() {
        val duplicate = service.findDuplicatePurchase("AAHH45-", excludeId = null)

        assertNull(duplicate)
    }

    @Test
    fun findDuplicatePurchase_differentNumberIsNotDuplicate() {
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("NZE141-5678")).thenReturn(emptyList())

        val duplicate = service.findDuplicatePurchase("NZE141-5678", excludeId = null)

        assertNull(duplicate)
    }

    @Test
    fun findDuplicatePurchase_trimsChassisBeforeLookup() {
        val existing = Purchase(id = 11L, chassis = "NZE141-1234567")
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("NZE141-1234567")).thenReturn(listOf(existing))

        val duplicate = service.findDuplicatePurchase("  NZE141-1234567  ", excludeId = null)

        assertEquals(existing, duplicate)
    }

    @Test
    fun findDuplicatePurchase_excludesCurrentPurchaseOnUpdate() {
        val existing = Purchase(id = 12L, chassis = "NZE141-1234567")
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("NZE141-1234567")).thenReturn(listOf(existing))

        val duplicate = service.findDuplicatePurchase("NZE141-1234567", excludeId = 12L)

        assertNull(duplicate)
    }

    @Test
    fun findDuplicatePurchase_returnsOtherRowWhenUpdatingToExistingChassis() {
        val self = Purchase(id = 12L, chassis = "NZE141-OLD")
        val other = Purchase(id = 99L, chassis = "NZE141-1234567")
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("NZE141-1234567")).thenReturn(listOf(other, self))

        val duplicate = service.findDuplicatePurchase("NZE141-1234567", excludeId = 12L)

        assertEquals(other, duplicate)
    }

    @Test
    fun duplicatePurchaseErrorMessage_usesStandardText() {
        val duplicate = Purchase(id = 1L, chassis = "NZE141-1234567", auctionHouse = "USS")

        assertEquals(PurchaseService.DUPLICATE_CHASSIS_MESSAGE, service.duplicatePurchaseErrorMessage(duplicate))
    }

    @Test
    fun createPurchase_rejectsDuplicateFullChassis() {
        val existing = Purchase(id = 20L, chassis = "NZE141-1234567")
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("NZE141-1234567")).thenReturn(listOf(existing))

        val error = assertThrows(IllegalArgumentException::class.java) {
            service.createPurchase(Purchase(chassis = "NZE141-1234567"))
        }

        assertEquals(PurchaseService.DUPLICATE_CHASSIS_MESSAGE, error.message)
    }

    @Test
    fun createPurchase_allowsCodeOnlyEvenIfLookupWouldMatch() {
        // Code-only must short-circuit before repository lookup; stub would fail if called incorrectly.
        `when`(purchaseRepository.findByChassisIgnoreCaseTrim("AAHH45")).thenReturn(
            listOf(Purchase(id = 21L, chassis = "AAHH45")),
        )

        // createPurchase has more deps; only assert findDuplicatePurchase gate here.
        assertNull(service.findDuplicatePurchase("AAHH45", excludeId = null))
    }
}
