package com.automan.backend.db

import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import com.automan.backend.model.Purchase
import com.automan.backend.service.PurchaseChassisLookupService
import com.automan.backend.service.PurchaseDateRangeCandidateQuery
import com.automan.backend.service.PurchaseService
import com.automan.backend.service.PurchaseVehicleOverrideService
import com.automan.backend.service.RixoHistoryService
import com.automan.backend.util.PurchaseDateParseUtils
import com.automan.backend.testsupport.ChassisQueryCountScenarioSupport
import com.automan.backend.testsupport.ChassisSqlStatementInspector
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Same statement-count scenarios as [com.automan.backend.service.ChassisQueryCountIntegrationTest]
 * on disposable MySQL 8.0 (requires Docker). Does not use the H2 `test` profile.
 */
@SpringBootTest
@ActiveProfiles("chassis-sqlcount-mysql")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Transactional
class ChassisQueryCountMysqlIntegrationTest {

    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var rixoHistoryRepository: RixoHistoryRepository
    @Autowired private lateinit var carBrandMappingRepository: CarBrandMappingRepository
    @Autowired private lateinit var purchaseVehicleOverrideService: PurchaseVehicleOverrideService
    @Autowired private lateinit var purchaseChassisLookupService: PurchaseChassisLookupService
    @Autowired private lateinit var rixoHistoryService: RixoHistoryService
    @Autowired private lateinit var purchaseService: PurchaseService
    @Autowired private lateinit var purchaseDateRangeCandidateQuery: PurchaseDateRangeCandidateQuery

    private val runTag = UUID.randomUUID().toString().take(8)

    @BeforeEach
    fun resetSqlCounter() {
        ChassisSqlStatementInspector.reset()
    }

    private fun logCounts(scenario: String, counts: ChassisQueryCountScenarioSupport.SqlCountSnapshot) {
        println(
            "MYSQL_CHASSIS_SQL_COUNT scenario=$scenario " +
                "mapping=${counts.mappingQueries} " +
                "purchase_chassis=${counts.purchaseChassisLookups} " +
                "chassis_key_in=${counts.chassisKeyInQueries} " +
                "chassis_key_like=${counts.chassisKeyLikeQueries} " +
                "total_jdbc=${counts.totalJdbcStatements}",
        )
    }

    @Test
    fun purchasePage20_exactBrandMapHits_useBoundedMappingQueries_mysql() {
        val counts = ChassisQueryCountScenarioSupport.runExactBrandMap20(
            purchaseRepository,
            carBrandMappingRepository,
            purchaseVehicleOverrideService,
            runTag,
        )
        logCounts("exact_brand_map_20", counts)
        counts.assertExactBrandMapBounds()
    }

    @Test
    fun purchasePage20_prefixFallback_usesSinglePrefixPoolQuery_mysql() {
        val (counts, _) = ChassisQueryCountScenarioSupport.runPrefixFallback20(
            purchaseRepository,
            carBrandMappingRepository,
            purchaseVehicleOverrideService,
            runTag,
        )
        logCounts("prefix_fallback_20", counts)
        counts.assertPrefixFallbackBounds()
    }

    @Test
    fun rixoHistoryPage_manyTokens_oneChassisPurchaseBatch_mysql() {
        val counts = ChassisQueryCountScenarioSupport.runRixoHistoryPage(
            purchaseRepository,
            rixoHistoryRepository,
            rixoHistoryService,
            "$runTag-RCH",
        )
        logCounts("rixo_history_page", counts)
        counts.assertRixoPageBounds()
    }

    @Test
    fun rixoDeleteMultipleRows_oneChassisEnrichmentBatch_mysql() {
        val (counts, _) = ChassisQueryCountScenarioSupport.runRixoDeleteThreeRows(
            purchaseRepository,
            rixoHistoryRepository,
            rixoHistoryService,
            runTag,
        )
        logCounts("rixo_delete_3_rows", counts)
        counts.assertRixoDeleteBounds()
    }

    @Test
    fun shippingCodeOnlyTokens_onePrefixBatchQuery_mysql() {
        val counts = ChassisQueryCountScenarioSupport.runShippingCodeOnly(
            purchaseRepository,
            purchaseChassisLookupService,
            runTag,
        )
        logCounts("shipping_code_only", counts)
        counts.assertShippingBounds()
    }

    @Test
    fun dateRangeCandidate_finalIdsMatchFullProjectionBaseline_mysql() {
        purchaseRepository.save(Purchase(chassis = "$runTag-ISO", date = "2026-06-20"))
        purchaseRepository.save(Purchase(chassis = "$runTag-LBL", date = "June 29, 2026 (Monday)"))
        purchaseRepository.save(Purchase(chassis = "$runTag-OUT", date = "2020-01-01"))

        val from = LocalDate.of(2026, 6, 1)
        val to = LocalDate.of(2026, 6, 30)
        val optimized = purchaseService.idDatePairsMatchingInclusiveDateRange(from, to)
            .mapNotNull { it.getId() }
            .toSet()
        val baseline = purchaseRepository.findIdAndDateAll()
            .filter { pair ->
                val parsed = PurchaseDateParseUtils.parseToLocalDate(pair.getDate()?.trim().orEmpty())
                parsed != null && !parsed.isBefore(from) && !parsed.isAfter(to)
            }
            .mapNotNull { it.getId() }
            .toSet()
        assertEquals(baseline, optimized)

        ChassisSqlStatementInspector.reset()
        purchaseDateRangeCandidateQuery.findIdDateCandidates(from, to)
        val candidateSelects = ChassisSqlStatementInspector.statements().count { sql ->
            val n = sql.lowercase()
            n.contains(" from purchases") && n.contains("date")
        }
        assertEquals(1, candidateSelects)
        println(
            "MYSQL_DATE_RANGE candidate_rows=${purchaseDateRangeCandidateQuery.findIdDateCandidates(from, to).size} " +
                "mysql_version=$mysqlVersion",
        )
    }

    companion object {
        private const val PASSWORD = "chassis_sqlcount_mysql_root"
        private const val DATABASE = "automan_chassis_sqlcount_mysql"
        private var containerName: String? = null
        private lateinit var jdbcUrl: String
        lateinit var mysqlVersion: String
        var chassisKeyIndexPresent: Boolean = false

        @JvmStatic
        @BeforeAll
        fun verifyMysqlReady() {
            assertTrue(chassisKeyIndexPresent, "idx_purchases_chassis_key must exist after V93")
        }

        init {
            jdbcUrl = startMysql()
            flyway(jdbcUrl).migrate()
            DriverManager.getConnection(jdbcUrl, "root", PASSWORD).use { connection ->
                connection.createStatement().executeQuery("SELECT VERSION()").use { rs ->
                    rs.next()
                    mysqlVersion = rs.getString(1)
                }
                connection.createStatement().executeQuery(
                    """
                    SELECT COUNT(*) FROM information_schema.statistics
                    WHERE table_schema = '$DATABASE' AND table_name = 'purchases'
                      AND index_name = 'idx_purchases_chassis_key'
                    """.trimIndent(),
                ).use { rs ->
                    rs.next()
                    chassisKeyIndexPresent = rs.getInt(1) > 0
                }
                connection.createStatement().executeQuery(
                    "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                ).use { rs ->
                    rs.next()
                    val v = rs.getString(1)
                    assertTrue(v == "94", "expected Flyway V94, got $v")
                }
            }
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            dockerRm()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerDataSource(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { jdbcUrl }
            registry.add("spring.datasource.username") { "root" }
            registry.add("spring.datasource.password") { PASSWORD }
        }

        private fun flyway(url: String): Flyway =
            Flyway.configure()
                .dataSource(url, "root", PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()

        private fun startMysql(): String {
            dockerRm()
            val name = "automan-chassis-sqlcount-${UUID.randomUUID().toString().take(8)}"
            containerName = name
            exec(
                "docker", "run", "-d",
                "--name", name,
                "-p", "127.0.0.1::3306",
                "-e", "MYSQL_ROOT_PASSWORD=$PASSWORD",
                "-e", "MYSQL_DATABASE=$DATABASE",
                "mysql:8.0",
                "--default-authentication-plugin=mysql_native_password",
            )
            val port = publishedPort(name)
            val url = "jdbc:mysql://127.0.0.1:$port/$DATABASE" +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            waitUntilReady(name)
            return url
        }

        private fun dockerRm() {
            val name = containerName ?: return
            ProcessBuilder("docker", "rm", "-f", name)
                .redirectErrorStream(true)
                .start()
                .waitFor(30, TimeUnit.SECONDS)
            containerName = null
        }

        private fun publishedPort(name: String): Int {
            val output = exec("docker", "port", name, "3306/tcp").trim()
            return output.substringAfterLast(":").trim().toInt()
        }

        private fun waitUntilReady(name: String) {
            val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90)
            while (System.currentTimeMillis() < deadline) {
                val process = ProcessBuilder(
                    "docker", "exec", name,
                    "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-p$PASSWORD", "--silent",
                ).redirectErrorStream(true).start()
                if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) return
                Thread.sleep(2000)
            }
            error("MySQL chassis sqlcount container did not become ready")
        }

        private fun exec(vararg command: String): String {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) {
                "Command failed: ${command.joinToString(" ")}\n$output"
            }
            return output
        }
    }
}
