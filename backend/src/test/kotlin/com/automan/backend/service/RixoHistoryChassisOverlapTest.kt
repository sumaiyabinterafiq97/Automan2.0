package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
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
class RixoHistoryChassisOverlapIntegrationTest {

    @Autowired
    private lateinit var rixoHistoryService: RixoHistoryService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @SpyBean
    private lateinit var rixoHistoryRepository: RixoHistoryRepository

    @SpyBean
    private lateinit var rixoHistoryOverlapCandidateQuery: RixoHistoryOverlapCandidateQuery

    @BeforeEach
    fun clearTables() {
        rixoHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun saveFromTransport_updatesExistingRowOnExactTokenOverlap() {
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "OLD", chassis = "TOK-1"))
        val purchase = savePurchase("TOK-1")

        rixoHistoryService.saveFromTransport(listOf(purchase.id!!), mapOf("rixoCompany" to "NEW"))

        val rows = rixoHistoryRepository.findAll()
        assertEquals(1, rows.size)
        assertEquals("NEW", rows.single().rixoCompany)
        assertEquals("TOK-1", rows.single().chassis)
    }

    @Test
    fun saveFromTransport_overlapIsCaseInsensitiveAndTrimmed() {
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "OLD", chassis = "  tok-2  "))
        val purchase = savePurchase("TOK-2")

        rixoHistoryService.saveFromTransport(listOf(purchase.id!!), mapOf("rixoCompany" to "NEW"))

        assertEquals(1, rixoHistoryRepository.count())
        assertEquals("NEW", rixoHistoryRepository.findAll().single().rixoCompany)
    }

    @Test
    fun saveFromTransport_multiTokenHistoryRow_overlapsSingleIncomingToken() {
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "OLD", chassis = "AAA;BBB"))
        val purchase = savePurchase("BBB")

        rixoHistoryService.saveFromTransport(listOf(purchase.id!!), mapOf("rixoCompany" to "NEW"))

        assertEquals(1, rixoHistoryRepository.count())
        assertEquals("BBB", rixoHistoryRepository.findAll().single().chassis)
    }

    @Test
    fun saveFromTransport_substringInChassisWithoutTokenOverlap_insertsNewRow() {
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "KEEP", chassis = "PREFIX-AA-SUFFIX"))
        val purchase = savePurchase("AA")

        rixoHistoryService.saveFromTransport(listOf(purchase.id!!), mapOf("rixoCompany" to "NEW"))

        val rows = rixoHistoryRepository.findAll().sortedBy { it.id }
        assertEquals(2, rows.size)
        assertEquals("KEEP", rows[0].rixoCompany)
        assertEquals("PREFIX-AA-SUFFIX", rows[0].chassis)
        assertEquals("NEW", rows[1].rixoCompany)
        assertEquals("AA", rows[1].chassis)
    }

    @Test
    fun saveFromTransport_multipleOverlappingRows_selectsHighestId() {
        val older = rixoHistoryRepository.save(RixoHistory(rixoCompany = "OLD-LOW", chassis = "PICK-ME"))
        val newer = rixoHistoryRepository.save(RixoHistory(rixoCompany = "OLD-HIGH", chassis = "PICK-ME"))
        assertNotEquals(older.id, newer.id)
        val purchase = savePurchase("PICK-ME")

        rixoHistoryService.saveFromTransport(listOf(purchase.id!!), mapOf("rixoCompany" to "WINNER"))

        val rows = rixoHistoryRepository.findAll()
        assertEquals(2, rows.size)
        val updated = rows.maxBy { it.id ?: 0L }
        assertEquals(newer.id, updated.id)
        assertEquals("WINNER", updated.rixoCompany)
    }

    @Test
    fun saveFromTransport_overlapCheck_doesNotLoadEntireHistoryTable() {
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "X", chassis = "UNRELATED-1"))
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "Y", chassis = "UNRELATED-2"))
        val purchase = savePurchase("ONLY-NEW")

        rixoHistoryService.saveFromTransport(listOf(purchase.id!!), mapOf("rixoCompany" to "CO"))

        verify(rixoHistoryOverlapCandidateQuery).findCandidates(anyCollection())
        verify(rixoHistoryRepository, never()).findAll()
    }

    private fun savePurchase(chassis: String): Purchase =
        purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Test",
                brand = "Toyota",
                country = "Japan",
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
