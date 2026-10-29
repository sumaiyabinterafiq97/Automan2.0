package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RixoHistorySaveFromTransportIntegrationTest {

    @Autowired
    private lateinit var rixoHistoryService: RixoHistoryService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var rixoHistoryRepository: RixoHistoryRepository

    @BeforeEach
    fun clearTables() {
        rixoHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun saveFromTransport_preservesSelectedIdOrderAndSkipsMissing() {
        val p1 = savePurchase("ORDER-1")
        val p2 = savePurchase("ORDER-2")
        val p3 = savePurchase("ORDER-3")

        rixoHistoryService.saveFromTransport(
            listOf(p3.id!!, p1.id!!, 9_999_999L, p2.id!!),
            mapOf("rixoCompany" to "CO", "buyingDate" to "2026-01-15"),
        )

        val saved = rixoHistoryRepository.findAll().single()
        assertEquals("ORDER-3;ORDER-1;ORDER-2", saved.chassis)
        assertEquals("CO", saved.rixoCompany)
        assertEquals(LocalDate.parse("2026-01-15"), saved.buyingDate)
    }

    @Test
    fun saveFromTransport_duplicateSelectedIds_repeatChassisInJoin() {
        val p1 = savePurchase("DUP-CH")

        rixoHistoryService.saveFromTransport(listOf(p1.id!!, p1.id!!), mapOf("rixoCompany" to "CO"))

        assertEquals("DUP-CH;DUP-CH", rixoHistoryRepository.findAll().single().chassis)
    }

    @Test
    fun saveFromTransport_emptySelectedIds_persistsRowWithoutChassis() {
        rixoHistoryService.saveFromTransport(emptyList(), mapOf("rixoCompany" to "CO"))

        val saved = rixoHistoryRepository.findAll().single()
        assertNull(saved.chassis)
        assertEquals("CO", saved.rixoCompany)
    }

    @Test
    fun saveFromTransport_skipsBlankChassisSegments() {
        val withChassis = savePurchase("HAS-CH")
        val blankChassis = purchaseRepository.save(savePurchase("PLACEHOLDER").copy(chassis = "   "))

        rixoHistoryService.saveFromTransport(
            listOf(withChassis.id!!, blankChassis.id!!),
            mapOf("rixoCompany" to "CO"),
        )

        assertEquals("HAS-CH", rixoHistoryRepository.findAll().single().chassis)
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
                paymentDate = LocalDate.now().toString(),
                rixoRequested = "0",
                rixoConfirmed = "0",
                rixoPrice = "0",
                shipmentDate = LocalDate.now().toString(),
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

class RixoHistorySaveFromTransportLookupTest {

    @Test
    fun saveFromTransport_usesSingleFindAllById_notPerIdFindById() {
        val purchaseRepository = mock(PurchaseRepository::class.java)
        val rixoHistoryRepository = mock(RixoHistoryRepository::class.java)
        val workflowService = mock(PurchaseWorkflowService::class.java)
        val service = rixoHistoryService(rixoHistoryRepository, purchaseRepository, workflowService)

        val p1 = Purchase(id = 1L, chassis = "A-1")
        val p2 = Purchase(id = 2L, chassis = "B-2")
        `when`(
            purchaseRepository.findAllById(
                argThat { ids: Iterable<Long> -> ids.toSet() == setOf(2L, 1L) },
            ),
        ).thenReturn(listOf(p1, p2))
        `when`(rixoHistoryRepository.findAll()).thenReturn(emptyList())
        `when`(rixoHistoryRepository.save(any(RixoHistory::class.java))).thenAnswer { it.arguments[0] }
        stubChassisPurchases(purchaseRepository, listOf(p1, p2))
        `when`(purchaseRepository.save(any(Purchase::class.java))).thenAnswer { it.arguments[0] as Purchase }

        service.saveFromTransport(listOf(2L, 1L, 2L), mapOf("rixoCompany" to "CO"))

        verify(purchaseRepository, times(1)).findAllById(
            argThat { ids: Iterable<Long> -> ids.toSet() == setOf(1L, 2L) },
        )
        verify(purchaseRepository, never()).findById(anyLong())
    }
}
