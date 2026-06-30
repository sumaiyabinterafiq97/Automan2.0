package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.never
import java.util.Optional

class PurchaseWorkflowServiceTest {

    private val purchaseRepository = mock(PurchaseRepository::class.java)
    private val service = PurchaseWorkflowService(purchaseRepository)

    @Test
    fun computeStatus_invoiceConfirmed_wins_over_booking_and_rixo() {
        val p = Purchase(
            chassis = "X",
            bookingRequested = true,
            invoiceConfirmed = true,
            rixoConfirmed = "FALSE",
            rixoRequested = "TRUE",
        )
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, service.computeStatus(p))
    }

    @Test
    fun computeStatus_bookingRequested_wins_over_rixo_flags() {
        val p = Purchase(
            chassis = "X",
            bookingRequested = true,
            invoiceConfirmed = false,
            rixoConfirmed = "TRUE",
            rixoRequested = "TRUE",
        )
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, service.computeStatus(p))
    }

    @Test
    fun computeStatus_rixoConfirmed_wins_over_rixoRequested() {
        val p = Purchase(
            chassis = "X",
            bookingRequested = false,
            invoiceConfirmed = false,
            rixoConfirmed = "TRUE",
            rixoRequested = "TRUE",
        )
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, service.computeStatus(p))
    }

    @Test
    fun computeStatus_rixoRequested_string_one() {
        val p = Purchase(
            chassis = "X",
            rixoRequested = "1",
        )
        assertEquals(WorkflowStatus.RIXO_REQUESTED, service.computeStatus(p))
    }

    @Test
    fun computeStatus_defaults_to_purchased() {
        val p = Purchase(chassis = "X")
        assertEquals(WorkflowStatus.PURCHASED, service.computeStatus(p))
    }

    @Test
    fun isRixoConfirmedForBooking_legacy_flag_true() {
        val p = Purchase(chassis = "X", rixoConfirmed = "1", workflowStatus = WorkflowStatus.PURCHASED)
        assertEquals(true, PurchaseWorkflowService.isRixoConfirmedForBooking(p))
    }

    @Test
    fun isRixoConfirmedForBooking_workflow_at_rixo_confirmed() {
        val p = Purchase(chassis = "X", rixoConfirmed = "0", workflowStatus = WorkflowStatus.RIXO_CONFIRMED)
        assertEquals(true, PurchaseWorkflowService.isRixoConfirmedForBooking(p))
    }

    @Test
    fun isRixoConfirmedForBooking_neither_legacy_nor_workflow() {
        val p = Purchase(chassis = "X", rixoConfirmed = "0", workflowStatus = WorkflowStatus.RIXO_REQUESTED)
        assertEquals(false, PurchaseWorkflowService.isRixoConfirmedForBooking(p))
    }

    @Test
    fun recomputeByPurchaseId_persists_when_status_changes() {
        val p = Purchase(
            id = 1L,
            chassis = "A",
            invoiceConfirmed = true,
            workflowStatus = WorkflowStatus.PURCHASED,
        )
        `when`(purchaseRepository.findById(1L)).thenReturn(Optional.of(p))
        service.recomputeByPurchaseId(1L)
        val captor = ArgumentCaptor.forClass(Purchase::class.java)
        verify(purchaseRepository).save(captor.capture())
        assertEquals(WorkflowStatus.INVOICE_CONFIRMED, captor.value.workflowStatus)
    }

    @Test
    fun recomputeByPurchaseId_skips_save_when_already_matches() {
        val p = Purchase(
            id = 1L,
            chassis = "A",
            invoiceConfirmed = true,
            workflowStatus = WorkflowStatus.INVOICE_CONFIRMED,
        )
        `when`(purchaseRepository.findById(1L)).thenReturn(Optional.of(p))
        service.recomputeByPurchaseId(1L)
        verify(purchaseRepository, never()).save(any(Purchase::class.java))
    }

    @Test
    fun applyForRead_derives_legacy_flags_from_workflow_status() {
        val merged = service.applyForRead(
            Purchase(id = 2L, chassis = "B", workflowStatus = WorkflowStatus.RIXO_CONFIRMED),
        )
        assertEquals("TRUE", merged.rixoConfirmed)
        assertEquals("TRUE", merged.rixoRequested)
        assertEquals(false, merged.bookingRequested)
        assertEquals(false, merged.invoiceConfirmed)
    }

    @Test
    fun applyForRead_invoice_confirmed_sets_all_downstream_flags() {
        val merged = service.applyForRead(
            Purchase(id = 3L, chassis = "C", workflowStatus = WorkflowStatus.INVOICE_CONFIRMED),
        )
        assertEquals(true, merged.invoiceConfirmed)
        assertEquals(true, merged.bookingRequested)
        assertEquals("TRUE", merged.rixoConfirmed)
    }
}
