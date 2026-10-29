package com.automan.backend.service

import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.testsupport.ChassisQueryCountScenarioSupport
import com.automan.backend.testsupport.ChassisSqlStatementInspector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Measured JDBC statement counts via Hibernate [ChassisSqlStatementInspector] (H2, real Spring JPA).
 */
@SpringBootTest
@ActiveProfiles("test", "sqlcount")
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class ChassisQueryCountIntegrationTest {

    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var rixoHistoryRepository: RixoHistoryRepository
    @Autowired private lateinit var carBrandMappingRepository: CarBrandMappingRepository
    @Autowired private lateinit var purchaseVehicleOverrideService: PurchaseVehicleOverrideService
    @Autowired private lateinit var purchaseChassisLookupService: PurchaseChassisLookupService
    @Autowired private lateinit var rixoHistoryService: RixoHistoryService

    @BeforeEach
    fun resetSqlCounter() {
        ChassisSqlStatementInspector.reset()
    }

    @AfterEach
    fun clearSqlCounter() {
        ChassisSqlStatementInspector.reset()
    }

    @Test
    fun purchasePage20_exactBrandMapHits_useBoundedMappingQueries() {
        val counts = ChassisQueryCountScenarioSupport.runExactBrandMap20(
            purchaseRepository,
            carBrandMappingRepository,
            purchaseVehicleOverrideService,
        )
        logH2Counts("exact_brand_map_20", counts)
        counts.assertExactBrandMapBounds()
    }

    @Test
    fun purchasePage20_prefixFallback_usesSinglePrefixPoolQuery() {
        val (counts, _) = ChassisQueryCountScenarioSupport.runPrefixFallback20(
            purchaseRepository,
            carBrandMappingRepository,
            purchaseVehicleOverrideService,
        )
        logH2Counts("prefix_fallback_20", counts)
        counts.assertPrefixFallbackBounds()
    }

    @Test
    fun rixoHistoryPage_manyTokens_oneChassisPurchaseBatch() {
        val counts = ChassisQueryCountScenarioSupport.runRixoHistoryPage(
            purchaseRepository,
            rixoHistoryRepository,
            rixoHistoryService,
        )
        logH2Counts("rixo_history_page", counts)
        counts.assertRixoPageBounds()
    }

    @Test
    fun rixoDeleteMultipleRows_oneChassisEnrichmentBatch() {
        val (counts, _) = ChassisQueryCountScenarioSupport.runRixoDeleteThreeRows(
            purchaseRepository,
            rixoHistoryRepository,
            rixoHistoryService,
        )
        logH2Counts("rixo_delete_3_rows", counts)
        counts.assertRixoDeleteBounds()
    }

    @Test
    fun shippingCodeOnlyTokens_onePrefixBatchQuery() {
        val counts = ChassisQueryCountScenarioSupport.runShippingCodeOnly(
            purchaseRepository,
            purchaseChassisLookupService,
        )
        logH2Counts("shipping_code_only", counts)
        counts.assertShippingBounds()
    }

    private fun logH2Counts(scenario: String, counts: ChassisQueryCountScenarioSupport.SqlCountSnapshot) {
        println(
            "H2_CHASSIS_SQL_COUNT scenario=$scenario " +
                "mapping=${counts.mappingQueries} " +
                "purchase_chassis=${counts.purchaseChassisLookups} " +
                "chassis_key_in=${counts.chassisKeyInQueries} " +
                "chassis_key_like=${counts.chassisKeyLikeQueries} " +
                "total_jdbc=${counts.totalJdbcStatements}",
        )
    }
}
