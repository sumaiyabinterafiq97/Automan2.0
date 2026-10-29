package com.automan.backend.testsupport

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.service.PurchaseChassisLookupService
import com.automan.backend.service.PurchaseVehicleOverrideService
import com.automan.backend.service.RixoHistoryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID

/** Shared service-level scenarios for H2 and MySQL SQL-count integration tests. */
object ChassisQueryCountScenarioSupport {

    data class SqlCountSnapshot(
        val mappingQueries: Int,
        val purchaseChassisLookups: Int,
        val chassisKeyInQueries: Int,
        val chassisKeyLikeQueries: Int,
        val totalJdbcStatements: Int,
    ) {
        fun assertExactBrandMapBounds() {
            assertTrue(mappingQueries <= 2, "expected batched exact mapping reads, got $mappingQueries")
            assertEquals(0, purchaseChassisLookups)
        }

        fun assertPrefixFallbackBounds() {
            assertTrue(mappingQueries <= 2, "expected one IN + one prefix pool query, got $mappingQueries")
        }

        fun assertRixoPageBounds() {
            assertTrue(chassisKeyInQueries <= 2, "expected one batched chassis_key IN, got $chassisKeyInQueries")
        }

        fun assertRixoDeleteBounds() {
            assertTrue(chassisKeyInQueries <= 2, "expected bounded chassis_key IN, got $chassisKeyInQueries")
        }

        fun assertShippingBounds() {
            assertTrue(chassisKeyInQueries <= 1, "expected one exact IN batch, got $chassisKeyInQueries")
            assertTrue(chassisKeyLikeQueries <= 1, "expected one prefix OR-batch, got $chassisKeyLikeQueries")
        }
    }

    fun snapshot(): SqlCountSnapshot {
        val stmts = ChassisSqlStatementInspector.statements()
        return SqlCountSnapshot(
            mappingQueries = ChassisSqlStatementInspector.countCarBrandMappingQueries(),
            purchaseChassisLookups = ChassisSqlStatementInspector.countPurchaseChassisLookups(),
            chassisKeyInQueries = ChassisSqlStatementInspector.countPurchaseChassisKeyInQueries(),
            chassisKeyLikeQueries = ChassisSqlStatementInspector.countStatements { sql ->
                val n = sql.lowercase()
                n.contains("chassis_key") && n.contains(" like ")
            },
            totalJdbcStatements = stmts.size,
        )
    }

    fun runExactBrandMap20(
        purchaseRepository: PurchaseRepository,
        carBrandMappingRepository: CarBrandMappingRepository,
        purchaseVehicleOverrideService: PurchaseVehicleOverrideService,
        tag: String = "",
    ): SqlCountSnapshot {
        val prefix = if (tag.isEmpty()) "EXACT" else "EXACT-$tag"
        val purchases = (1..20).map { i ->
            purchaseRepository.save(Purchase(chassis = "$prefix-$i", brand = "TOYOTA"))
        }
        purchases.forEach { p ->
            carBrandMappingRepository.save(
                CarBrandMapping(carBrand = "TOYOTA", chassis = p.chassis, fuel = "GASOLINE"),
            )
        }
        ChassisSqlStatementInspector.reset()
        purchaseVehicleOverrideService.applyForReadBatch(purchases)
        return snapshot()
    }

    fun runPrefixFallback20(
        purchaseRepository: PurchaseRepository,
        carBrandMappingRepository: CarBrandMappingRepository,
        purchaseVehicleOverrideService: PurchaseVehicleOverrideService,
        tag: String = "",
    ): Pair<SqlCountSnapshot, String> {
        val prefix = if (tag.isEmpty()) "PFX" else "PFX-$tag"
        carBrandMappingRepository.save(
            CarBrandMapping(carBrand = "TOYOTA", chassis = "PFX", fuel = "DIESEL", grade = "S"),
        )
        val purchases = (1..20).map { i ->
            purchaseRepository.save(Purchase(chassis = "$prefix-$i", brand = "TOYOTA"))
        }
        ChassisSqlStatementInspector.reset()
        val merged = purchaseVehicleOverrideService.applyForReadBatch(purchases)
        assertEquals("DIESEL", merged.first().fuel)
        return snapshot() to prefix
    }

    fun runRixoHistoryPage(
        purchaseRepository: PurchaseRepository,
        rixoHistoryRepository: RixoHistoryRepository,
        rixoHistoryService: RixoHistoryService,
        tag: String = "",
    ): SqlCountSnapshot {
        val t = tag.ifEmpty { "RCH" }
        (1..5).forEach { i ->
            purchaseRepository.save(Purchase(chassis = "$t-$i"))
        }
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "CO", chassis = "$t-1;$t-2;$t-3"))
        rixoHistoryRepository.save(RixoHistory(rixoCompany = "CO", chassis = "$t-4;$t-5"))
        ChassisSqlStatementInspector.reset()
        rixoHistoryService.listRowsPage(page = 0, rawSize = 20)
        return snapshot()
    }

    fun runRixoDeleteThreeRows(
        purchaseRepository: PurchaseRepository,
        rixoHistoryRepository: RixoHistoryRepository,
        rixoHistoryService: RixoHistoryService,
        tag: String = UUID.randomUUID().toString().take(8),
    ): Pair<SqlCountSnapshot, String> {
        val historyIds = (1..3).map { i ->
            val chassis = "DEL-$tag-$i"
            purchaseRepository.save(Purchase(chassis = chassis))
            rixoHistoryRepository.save(RixoHistory(rixoCompany = "CO", chassis = chassis)).id!!
        }
        ChassisSqlStatementInspector.reset()
        rixoHistoryService.deleteHistoryRows(historyIds)
        assertEquals(
            3,
            purchaseRepository.findAll().count { it.chassis.startsWith("DEL-$tag-") },
        )
        val remainingTagged = rixoHistoryRepository.findAll().count { row ->
            row.chassis?.contains("DEL-$tag-") == true
        }
        assertEquals(0, remainingTagged)
        return snapshot() to tag
    }

    fun runShippingCodeOnly(
        purchaseRepository: PurchaseRepository,
        purchaseChassisLookupService: PurchaseChassisLookupService,
        tag: String = "",
    ): SqlCountSnapshot {
        val code = if (tag.isEmpty()) "SHIP" else "SHIP-$tag"
        purchaseRepository.save(Purchase(chassis = "$code-1"))
        purchaseRepository.save(Purchase(chassis = "$code-2"))
        ChassisSqlStatementInspector.reset()
        purchaseChassisLookupService.resolveShippingChassisTokens(listOf(code, code))
        return snapshot()
    }
}
