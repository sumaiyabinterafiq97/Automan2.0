package com.automan.backend.db

import com.automan.backend.util.AuctionHouseNormalizer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Legacy JPQL predicate vs indexed [auction_house_norm] must return the same candidate ids
 * (whole-string equality, including semicolon exclusion).
 */
class PurchaseAuctionHouseNormCandidateEquivalenceMysqlTest {

    private var containerName: String? = null

    @AfterEach
    fun tearDown() {
        val name = containerName ?: return
        ProcessBuilder("docker", "rm", "-f", name)
            .redirectErrorStream(true)
            .start()
            .waitFor(30, TimeUnit.SECONDS)
        containerName = null
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun legacyPredicateAndNormColumnReturnSameIds() {
        val url = startMysql()
        flyway(url).migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO purchases (chassis, chassis_key, auction_house, created_at, updated_at) VALUES
                ('EQ-1', 'eq-1', '  ju aichi  ', NOW(), NOW()),
                ('EQ-2', 'eq-2', 'JU AICHI', NOW(), NOW()),
                ('EQ-3', 'eq-3', 'JU AICHI; USS NAGOYA', NOW(), NOW()),
                ('EQ-4', 'eq-4', 'USS TOKYO', NOW(), NOW()),
                ('EQ-5', 'eq-5', NULL, NOW(), NOW())
                """.trimIndent(),
            )
            connection.createStatement().execute(
                """
                UPDATE purchases
                SET auction_house_norm = UPPER(TRIM(COALESCE(auction_house, '')))
                WHERE auction_house_norm IS NULL
                """.trimIndent(),
            )
        }
        val mappingParam = "JU AICHI"
        val legacyIds = queryIds(
            url,
            """
            SELECT id FROM purchases
            WHERE UPPER(TRIM(COALESCE(auction_house, ''))) = UPPER(TRIM(?))
            """.trimIndent(),
            mappingParam,
        )
        val normIds = queryIds(
            url,
            "SELECT id FROM purchases WHERE auction_house_norm = ?",
            AuctionHouseNormalizer.normalize(mappingParam),
        )
        assertEquals(legacyIds, normIds)
        assertEquals(2, legacyIds.size)
        assertTrue(legacyIds.isNotEmpty())
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun explainUsesAuctionHouseNormIndexOnEqualityLookup() {
        val url = startMysql()
        flyway(url).migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO purchases (chassis, chassis_key, auction_house, auction_house_norm, created_at, updated_at)
                SELECT CONCAT('EX-', n), CONCAT('ex-', n), 'JU AICHI', 'JU AICHI', NOW(), NOW()
                FROM (
                  SELECT a.N + b.N * 10 + c.N * 100 + 1 AS n
                  FROM (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) a
                  CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) b
                  CROSS JOIN (SELECT 0 N UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4
                        UNION SELECT 5 UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9) c
                ) seq
                WHERE n <= 200
                """.trimIndent(),
            )
            connection.createStatement().execute("ANALYZE TABLE purchases")
            connection.createStatement().executeQuery(
                """
                EXPLAIN FORMAT=TRADITIONAL
                SELECT id FROM purchases WHERE auction_house_norm = 'JU AICHI'
                """.trimIndent(),
            ).use { rs ->
                rs.next()
                val key = rs.getString("key")
                val type = rs.getString("type")
                assertEquals("idx_purchases_auction_house_norm", key)
                assertTrue(type == "ref" || type == "range") { "unexpected type: $type" }
            }
        }
    }

    private fun queryIds(url: String, sql: String, param: String): Set<Long> {
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, param)
                ps.executeQuery().use { rs ->
                    val ids = mutableSetOf<Long>()
                    while (rs.next()) {
                        ids.add(rs.getLong(1))
                    }
                    return ids
                }
            }
        }
    }

    private fun flyway(url: String): Flyway =
        Flyway.configure()
            .dataSource(url, "root", PASSWORD)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .load()
            .also { it.migrate() }

    private fun startMysql(): String {
        val name = "automan-ah-eq-${UUID.randomUUID().toString().take(8)}"
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
        waitUntilReady(name)
        return "jdbc:mysql://127.0.0.1:$port/$DATABASE" +
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
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
        error("MySQL container did not become ready")
    }

    private fun exec(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) {
            "Command failed: ${command.joinToString(" ")}\n$output"
        }
        return output
    }

    companion object {
        private const val PASSWORD = "mysql_ah_eq_root"
        private const val DATABASE = "automan_ah_eq_test"
    }
}
