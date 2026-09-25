package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.domain.Sort
import java.time.LocalDateTime
import java.util.Optional

class RixoHistoryChassisConfirmTest {

    private fun service(
        rixoRepo: RixoHistoryRepository,
        purchaseRepo: PurchaseRepository,
    ): RixoHistoryService {
        return RixoHistoryService(
            rixoRepo,
            purchaseRepo,
            PurchaseWorkflowService(purchaseRepo),
            mock(ShippingHistoryService::class.java),
            mock(InvoiceHistoryService::class.java),
        )
    }

    @Test
    fun `listAllRows returns each chassis confirm date while the row stays unconfirmed`() {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val purchaseRepo = mock(PurchaseRepository::class.java)
        val sort = Sort.by(Sort.Direction.DESC, "id")
        val confirmedAt = LocalDateTime.of(2026, 9, 22, 8, 0)
        val later = confirmedAt.plusDays(1)
        val confirmedCar = Purchase(id = 1L, chassis = "AAA", rixoConfirmed = "TRUE", updatedAt = confirmedAt)
        val openCar = Purchase(id = 2L, chassis = "BBB", rixoConfirmed = "FALSE", updatedAt = later)
        val row = RixoHistory(id = 10L, chassis = "AAA;BBB", rixoCompany = "LOGICO")

        `when`(rixoRepo.findAll(sort)).thenReturn(listOf(row))
        `when`(purchaseRepo.findByChassisToken("AAA")).thenReturn(listOf(confirmedCar))
        `when`(purchaseRepo.findByChassisToken("BBB")).thenReturn(listOf(openCar))

        val dto = service(rixoRepo, purchaseRepo).listAllRows().single()
        assertFalse(dto.rixoConfirmed)
        assertEquals(confirmedAt.toString(), dto.rixoConfirmedDate)
        assertEquals(listOf("AAA", "BBB"), dto.chassisConfirms.map { it.chassis })
        assertTrue(dto.chassisConfirms[0].confirmed)
        assertEquals(confirmedAt.toString(), dto.chassisConfirms[0].confirmedDate)
        assertFalse(dto.chassisConfirms[1].confirmed)
        assertNull(dto.chassisConfirms[1].confirmedDate)
    }

    @Test
    fun `confirmChassisOnHistoryRow updates only the named chassis`() {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val purchaseRepo = mock(PurchaseRepository::class.java)
        val row = RixoHistory(id = 10L, chassis = "AAA;BBB")
        val openCar = Purchase(id = 1L, chassis = "AAA", workflowStatus = WorkflowStatus.RIXO_REQUESTED)
        val alreadyConfirmed = Purchase(
            id = 3L,
            chassis = "CCC",
            workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
        )

        `when`(rixoRepo.findById(10L)).thenReturn(Optional.of(row))
        `when`(purchaseRepo.findByChassisToken("AAA")).thenReturn(listOf(openCar))
        `when`(purchaseRepo.save(org.mockito.ArgumentMatchers.any(Purchase::class.java)))
            .thenAnswer { it.arguments[0] }

        val svc = service(rixoRepo, purchaseRepo)
        assertEquals(1, svc.confirmChassisOnHistoryRow(10L, "aaa"))

        val saved = ArgumentCaptor.forClass(Purchase::class.java)
        verify(purchaseRepo).save(saved.capture())
        assertEquals(1L, saved.value.id)
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, saved.value.workflowStatus)
        verify(purchaseRepo, never()).findByChassisToken("BBB")

        val confirmedRow = RixoHistory(id = 11L, chassis = "CCC")
        `when`(rixoRepo.findById(11L)).thenReturn(Optional.of(confirmedRow))
        `when`(purchaseRepo.findByChassisToken("CCC")).thenReturn(listOf(alreadyConfirmed))
        assertEquals(0, svc.confirmChassisOnHistoryRow(11L, "CCC"))
    }

    @Test
    fun `confirmSelectedChassis updates only the listed cars`() {
        val rixoRepo = mock(RixoHistoryRepository::class.java)
        val purchaseRepo = mock(PurchaseRepository::class.java)
        val row = RixoHistory(id = 10L, chassis = "AAA;BBB")
        val other = RixoHistory(id = 12L, chassis = "DDD")
        val aaa = Purchase(id = 1L, chassis = "AAA", workflowStatus = WorkflowStatus.RIXO_REQUESTED)
        val ddd = Purchase(id = 4L, chassis = "DDD", workflowStatus = WorkflowStatus.RIXO_REQUESTED)

        `when`(rixoRepo.findById(10L)).thenReturn(Optional.of(row))
        `when`(rixoRepo.findById(12L)).thenReturn(Optional.of(other))
        `when`(purchaseRepo.findByChassisToken("AAA")).thenReturn(listOf(aaa))
        `when`(purchaseRepo.findByChassisToken("DDD")).thenReturn(listOf(ddd))
        `when`(purchaseRepo.save(org.mockito.ArgumentMatchers.any(Purchase::class.java)))
            .thenAnswer { it.arguments[0] }

        val updated = service(rixoRepo, purchaseRepo).confirmSelectedChassis(
            listOf(10L to "AAA", 12L to "DDD"),
        )
        assertEquals(2, updated)
        verify(purchaseRepo, never()).findByChassisToken("BBB")
    }
}
