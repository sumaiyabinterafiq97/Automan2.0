package com.automan.backend.service

import com.automan.backend.dto.PurchaseChangeHistoryPageRequest
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseChangeHistory
import com.automan.backend.repository.PurchaseChangeHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
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

    @Test
    fun `recordPurchasePartialEdit ignores yen formatting-only money differences`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val before = Purchase(
            id = 7L,
            chassis = "AAHH45",
            price = "6767",
            freight = "10432",
            shipmentCharges = "15000",
            commission = null,
            totalPrice = "32199",
        )
        val after = before.copy(
            price = "¥6767",
            freight = "¥10432",
            shipmentCharges = "¥15000",
            commission = "¥600",
            totalPrice = "¥32799",
        )
        svc.recordPurchasePartialEdit(before, after)
        val captor = ArgumentCaptor.forClass(PurchaseChangeHistory::class.java)
        verify(repo, times(2)).save(captor.capture())
        val fields = captor.allValues.map { it.fieldName }.toSet()
        assertEquals(setOf("commission", "totalPrice"), fields)
    }

    @Test
    fun `recordPurchasePartialEdit ignores null vs false boolean differences`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val before = Purchase(id = 8L, chassis = "X", shaken = null, negotiate = null, invoiceConfirmed = null)
        val after = before.copy(shaken = false, negotiate = false, invoiceConfirmed = false)
        svc.recordPurchasePartialEdit(before, after)
        verify(repo, times(0)).save(any(PurchaseChangeHistory::class.java))
    }

    @Test
    fun `recordPurchasePartialEdit with onlyFields logs modal-confirmed fields only`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val before = Purchase(
            id = 9L,
            chassis = "AAHH45",
            price = "6767",
            freight = "10432",
            commission = null,
            totalPrice = "32199",
            wd = "",
        )
        val after = before.copy(
            price = "¥6767",
            freight = "¥10432",
            commission = "¥600",
            totalPrice = "¥32799",
            wd = "2WD",
        )
        svc.recordPurchasePartialEdit(
            before,
            after,
            onlyFields = setOf("totalPrice", "commission"),
        )
        val captor = ArgumentCaptor.forClass(PurchaseChangeHistory::class.java)
        verify(repo, times(2)).save(captor.capture())
        val fields = captor.allValues.map { it.fieldName }.toSet()
        assertEquals(setOf("totalPrice", "commission"), fields)
    }

    @Test
    fun `mapAuditFieldKeys maps sold and vehicleType aliases`() {
        assertEquals(
            setOf("invoiceConfirmed", "shipmentSize", "commission"),
            PurchaseChangeHistoryService.mapAuditFieldKeys(listOf("sold", "vehicleType", "commission")),
        )
    }

    @Test
    fun `extractAuditChangedFields reads auditChangedFields from update payload`() {
        val fields = PurchaseChangeHistoryService.extractAuditChangedFields(
            mapOf(
                "auditChangedFields" to listOf("totalPrice", "sold"),
                "commission" to "¥600",
            ),
        )
        assertEquals(setOf("totalPrice", "invoiceConfirmed"), fields)
    }

    @Test
    fun `extractAuditChangedFields returns null when absent`() {
        assertEquals(null, PurchaseChangeHistoryService.extractAuditChangedFields(emptyMap()))
    }

    @Test
    fun `recordPurchasePartialEdit stores changedAt in Japan zone`() {
        val repo = mock(PurchaseChangeHistoryRepository::class.java)
        val svc = PurchaseChangeHistoryService(repo)
        val before = Purchase(id = 10L, chassis = "Z", notes = "a")
        val after = before.copy(notes = "b")
        svc.recordPurchasePartialEdit(before, after)
        val captor = ArgumentCaptor.forClass(PurchaseChangeHistory::class.java)
        verify(repo).save(captor.capture())
        val savedAt = captor.value.changedAt
        val nowJapan = java.time.LocalDateTime.now(PurchaseChangeHistoryService.JAPAN_ZONE)
        assertTrue(savedAt.year == nowJapan.year)
        assertTrue(savedAt.month == nowJapan.month)
        assertTrue(savedAt.dayOfMonth == nowJapan.dayOfMonth)
        assertTrue(kotlin.math.abs(savedAt.hour - nowJapan.hour) <= 1)
    }
}
