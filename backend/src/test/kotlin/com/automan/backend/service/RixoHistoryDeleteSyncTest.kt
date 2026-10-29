package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atMost
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RixoHistoryDeleteSyncIntegrationTest {

    @Autowired
    private lateinit var rixoHistoryService: RixoHistoryService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @SpyBean
    private lateinit var rixoHistoryRepository: RixoHistoryRepository

    @BeforeEach
    fun clearTables() {
        rixoHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun deleteHistoryRow_whenAnotherRowStillCoversChassis_keepsPurchaseRixoConfirmed() {
        val purchase = savePurchase("SHARED-CH", WorkflowStatus.RIXO_CONFIRMED)
        val keep = rixoHistoryRepository.save(RixoHistory(chassis = "SHARED-CH", rixoCompany = "A"))
        val remove = rixoHistoryRepository.save(RixoHistory(chassis = "SHARED-CH", rixoCompany = "B"))

        assertTrue(rixoHistoryService.deleteHistoryRow(remove.id!!))

        val reloaded = purchaseRepository.findById(purchase.id!!).orElseThrow()
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, reloaded.workflowStatus)
        assertEquals(1, rixoHistoryRepository.count())
        assertEquals(keep.id, rixoHistoryRepository.findAll().single().id)
    }

    @Test
    fun deleteHistoryRow_whenLastCoveringRowRemoved_resetsPurchaseToPurchased() {
        val purchase = savePurchase("ONLY-CH", WorkflowStatus.RIXO_CONFIRMED)
        val row = rixoHistoryRepository.save(RixoHistory(chassis = "ONLY-CH"))

        assertTrue(rixoHistoryService.deleteHistoryRow(row.id!!))

        val reloaded = purchaseRepository.findById(purchase.id!!).orElseThrow()
        assertEquals(WorkflowStatus.PURCHASED, reloaded.workflowStatus)
    }

    @Test
    fun deleteHistoryRows_batchDelete_usesRemainingHistoryForCover() {
        val purchase = savePurchase("BATCH-CH", WorkflowStatus.RIXO_CONFIRMED)
        val survivor = rixoHistoryRepository.save(RixoHistory(chassis = "BATCH-CH"))
        val doomed = rixoHistoryRepository.save(RixoHistory(chassis = "OTHER-CH"))

        rixoHistoryService.deleteHistoryRows(listOf(doomed.id!!))

        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)

        rixoHistoryService.deleteHistoryRows(listOf(survivor.id!!))
        assertEquals(WorkflowStatus.PURCHASED, purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun removeChassisToken_whenOtherRowStillHasToken_keepsRixoConfirmed() {
        val purchase = savePurchase("TOK-A", WorkflowStatus.RIXO_CONFIRMED)
        rixoHistoryRepository.save(RixoHistory(chassis = "TOK-A"))
        val rowB = rixoHistoryRepository.save(RixoHistory(chassis = "TOK-A;TOK-B"))

        rixoHistoryService.removeChassisTokenFromHistoryRow(rowB.id!!, "TOK-B")

        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
        assertEquals(2, rixoHistoryRepository.count())
    }

    @Test
    fun removeChassisToken_whenLastTokenRemoved_resetsPurchase() {
        val purchase = savePurchase("RM-ME", WorkflowStatus.RIXO_CONFIRMED)
        val row = rixoHistoryRepository.save(RixoHistory(chassis = "RM-ME,RM-OTHER"))

        rixoHistoryService.removeChassisTokenFromHistoryRow(row.id!!, "rm-me")

        assertEquals(WorkflowStatus.PURCHASED, purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
        assertEquals("RM-OTHER", rixoHistoryRepository.findAll().single().chassis)
    }

    @Test
    fun deleteHistoryRow_syncUsesChassisProjection_notFullEntityFindAll() {
        val purchase = savePurchase("PROJ-CH", WorkflowStatus.RIXO_CONFIRMED)
        val row = rixoHistoryRepository.save(RixoHistory(chassis = "PROJ-CH"))

        rixoHistoryService.deleteHistoryRow(row.id!!)

        verify(rixoHistoryRepository, atMost(1)).findAllChassisValues()
        verify(rixoHistoryRepository, never()).findAll()
        assertEquals(WorkflowStatus.PURCHASED, purchaseRepository.findById(purchase.id!!).orElseThrow().workflowStatus)
    }

    private fun savePurchase(chassis: String, workflowStatus: WorkflowStatus): Purchase =
        purchaseRepository.saveAndFlush(
            Purchase(
                chassis = chassis,
                carName = "Test",
                brand = "Toyota",
                country = "Japan",
                workflowStatus = workflowStatus,
                price = "1",
                auctionFee = "0",
                recycleFee = "0",
                roadTax = "0",
                totalPrice = "1",
                paymentDate = java.time.LocalDate.now().toString(),
                rixoRequested = "0",
                rixoConfirmed = "0",
                rixoPrice = "0",
                shipmentDate = java.time.LocalDate.now().toString(),
                vessel = "V",
                shipmentCharges = "0",
                freight = "0",
                storageCharges = "0",
                miscCharges = "0",
                inspectionFee = "0",
                commission = "0",
                repairCompany = "R",
                repairCharges = "0",
                notes = "",
                numberCut = "0",
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )
}
