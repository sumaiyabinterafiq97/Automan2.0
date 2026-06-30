package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class RixoHistoryServiceSyncTest {

    @Test
    fun syncRixoConfirmedWithAllHistory_downgrades_workflow_when_no_history_matches() {
        val purchaseRepository = mock(PurchaseRepository::class.java)
        val rixoHistoryRepository = mock(RixoHistoryRepository::class.java)
        val workflowService = PurchaseWorkflowService(purchaseRepository)
        val shippingHistoryService = mock(ShippingHistoryService::class.java)
        val invoiceHistoryService = mock(InvoiceHistoryService::class.java)
        val service = RixoHistoryService(
            rixoHistoryRepository,
            purchaseRepository,
            workflowService,
            shippingHistoryService,
            invoiceHistoryService,
        )

        val purchase = Purchase(id = 10L, chassis = "AA-111", workflowStatus = WorkflowStatus.RIXO_CONFIRMED)
        `when`(rixoHistoryRepository.findAll()).thenReturn(emptyList())
        `when`(purchaseRepository.findPurchasesWhereRixoConfirmedPositive()).thenReturn(listOf(purchase))
        `when`(purchaseRepository.save(org.mockito.ArgumentMatchers.any(Purchase::class.java)))
            .thenAnswer { it.arguments[0] as Purchase }

        val changedIds = service.syncRixoConfirmedWithAllHistory()

        assertEquals(setOf(10L), changedIds)
        val captor = ArgumentCaptor.forClass(Purchase::class.java)
        verify(purchaseRepository).save(captor.capture())
        assertEquals(WorkflowStatus.RIXO_REQUESTED, captor.value.workflowStatus)
    }

    @Test
    fun deleteHistoryRow_resetsPurchaseWhenChassisNoLongerInAnyHistory() {
        val purchaseRepository = mock(PurchaseRepository::class.java)
        val rixoHistoryRepository = mock(RixoHistoryRepository::class.java)
        val workflowService = PurchaseWorkflowService(purchaseRepository)
        val shippingHistoryService = mock(ShippingHistoryService::class.java)
        val invoiceHistoryService = mock(InvoiceHistoryService::class.java)
        val service = RixoHistoryService(
            rixoHistoryRepository,
            purchaseRepository,
            workflowService,
            shippingHistoryService,
            invoiceHistoryService,
        )

        val row = RixoHistory(id = 1L, chassis = "ABC-999")
        `when`(rixoHistoryRepository.findById(1L)).thenReturn(Optional.of(row))
        `when`(rixoHistoryRepository.findAll()).thenReturn(emptyList())

        val p = Purchase(
            id = 10L,
            chassis = "ABC-999",
            workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
        )
        `when`(purchaseRepository.findByChassisToken(anyString())).thenAnswer { inv ->
            val token = inv.getArgument<String>(0)
            if (token == "ABC-999" || token == "ABC") listOf(p) else emptyList()
        }
        `when`(purchaseRepository.findById(10L)).thenReturn(Optional.of(p))
        `when`(purchaseRepository.findPurchasesWhereRixoConfirmedPositive()).thenReturn(listOf(p))
        `when`(purchaseRepository.save(org.mockito.ArgumentMatchers.any(Purchase::class.java)))
            .thenAnswer { it.arguments[0] as Purchase }

        val ok = service.deleteHistoryRow(1L)
        assertTrue(ok)
        verify(rixoHistoryRepository).deleteById(1L)
        verify(purchaseRepository, atLeastOnce()).save(
            argThat { saved: Purchase ->
                saved.id == 10L && saved.workflowStatus == WorkflowStatus.PURCHASED
            },
        )
    }

    @Test
    fun deleteHistoryRow_throwsWhenAnyMatchedPurchaseIsBookingRequested() {
        val purchaseRepository = mock(PurchaseRepository::class.java)
        val rixoHistoryRepository = mock(RixoHistoryRepository::class.java)
        val workflowService = PurchaseWorkflowService(purchaseRepository)
        val shippingHistoryService = mock(ShippingHistoryService::class.java)
        val invoiceHistoryService = mock(InvoiceHistoryService::class.java)
        val service = RixoHistoryService(
            rixoHistoryRepository,
            purchaseRepository,
            workflowService,
            shippingHistoryService,
            invoiceHistoryService,
        )

        val row = RixoHistory(id = 1L, chassis = "ABC-999")
        `when`(rixoHistoryRepository.findById(1L)).thenReturn(Optional.of(row))

        val p = Purchase(id = 10L, chassis = "ABC-999", workflowStatus = WorkflowStatus.BOOKING_REQUESTED)
        `when`(purchaseRepository.findByChassisToken(anyString())).thenAnswer { inv ->
            val token = inv.getArgument<String>(0)
            if (token == "ABC-999" || token == "ABC") listOf(p) else emptyList()
        }
        `when`(purchaseRepository.findById(10L)).thenReturn(Optional.of(p))

        assertThrows(IllegalArgumentException::class.java) {
            service.deleteHistoryRow(1L)
        }
    }

    @Test
    fun removeChassisTokenFromHistoryRow_throwsWhenPurchaseIsBookingRequested() {
        val purchaseRepository = mock(PurchaseRepository::class.java)
        val rixoHistoryRepository = mock(RixoHistoryRepository::class.java)
        val workflowService = PurchaseWorkflowService(purchaseRepository)
        val shippingHistoryService = mock(ShippingHistoryService::class.java)
        val invoiceHistoryService = mock(InvoiceHistoryService::class.java)
        val service = RixoHistoryService(
            rixoHistoryRepository,
            purchaseRepository,
            workflowService,
            shippingHistoryService,
            invoiceHistoryService,
        )

        val row = RixoHistory(id = 1L, chassis = "ABC-999;OTHER")
        `when`(rixoHistoryRepository.findById(1L)).thenReturn(Optional.of(row))

        val p = Purchase(id = 10L, chassis = "ABC-999", workflowStatus = WorkflowStatus.BOOKING_REQUESTED)
        `when`(purchaseRepository.findByChassisToken(anyString())).thenAnswer { inv ->
            val token = inv.getArgument<String>(0)
            if (token == "ABC-999" || token == "ABC") listOf(p) else emptyList()
        }

        assertThrows(IllegalArgumentException::class.java) {
            service.removeChassisTokenFromHistoryRow(1L, "ABC-999")
        }
    }
}
