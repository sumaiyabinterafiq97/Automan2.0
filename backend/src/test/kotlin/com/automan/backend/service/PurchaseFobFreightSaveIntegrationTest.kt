package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseCostLineRepository
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * C&F save stores freight. FOB save does not take a freight argument and must leave
 * the freight cost line that was already stored.
 */
@SpringBootTest
@ActiveProfiles("test")
class PurchaseFobFreightSaveIntegrationTest {

    @Autowired private lateinit var purchaseService: PurchaseService
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var purchaseCostLineRepository: PurchaseCostLineRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }

    private val chassis = "NZE141-FREIGHT"

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            val ids = purchaseRepository.findByChassis(chassis).mapNotNull { it.id }
            for (id in ids) {
                purchaseCostLineRepository.deleteAll(
                    purchaseCostLineRepository.findByPurchaseIdOrderBySortOrderAsc(id),
                )
                purchaseRepository.deleteById(id)
            }
        }
    }

    @Test
    fun cnfSaveStoresFreightAndFobSaveLeavesIt() {
        purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = "Freight Car",
                workflowStatus = WorkflowStatus.BOOKING_REQUESTED,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
            ),
        )

        purchaseService.saveCarCostDetails(
            chassis = chassis,
            carPrice = 1_000_000.0,
            auctionFee = 0.0,
            auctionPenaltyFee = 0.0,
            rixoPrice = 0.0,
            shippingCharge = 20_000.0,
            freight = 50_000.0,
            inspectionFee = 0.0,
            repairFee = 0.0,
            mscCharges = 0.0,
            profit = 0.0,
        )
        assertEquals(0, BigDecimal("50000").compareTo(freightAmount()))

        purchaseService.saveFobCarCostDetails(
            chassis = chassis,
            carPrice = 1_000_000.0,
            auctionFee = 0.0,
            auctionPenaltyFee = 0.0,
            rixoPrice = 0.0,
            shippingCharge = 20_000.0,
            inspectionFee = 0.0,
            repairFee = 0.0,
            mscCharges = 0.0,
            profit = 0.0,
        )

        assertEquals(0, BigDecimal("50000").compareTo(freightAmount()))
        assertEquals(0, BigDecimal("1000000").compareTo(priceAmount()))
        assertEquals(0, BigDecimal("20000").compareTo(shippingAmount()))
    }

    private fun freightAmount(): BigDecimal = cost("freight")

    private fun priceAmount(): BigDecimal = cost("carPrice")

    private fun shippingAmount(): BigDecimal = cost("shippingCharge")

    private fun cost(key: String): BigDecimal {
        val map = purchaseService.getCostDetailsByChassis(chassis) ?: error("no costs for $chassis")
        val raw = map[key] ?: error("missing $key")
        return when (raw) {
            is BigDecimal -> raw
            is Number -> BigDecimal(raw.toString())
            else -> BigDecimal(raw.toString())
        }
    }
}
