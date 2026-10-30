package com.automan.backend.db

import com.automan.backend.util.AuctionHouseNormalizer
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/** V93 seed → V94 backfill and safety checks on disposable MySQL 8.0 (requires Docker). */
class PurchaseAuctionHouseNormV94MigrationMysqlTest {

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
    fun v93SeedThenV94BackfillMatchesSqlExpression() {
        val url = startMysql()
        flyway(url, target = "93").migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO purchases (chassis, chassis_key, auction_house, created_at, updated_at) VALUES
                ('AH-N-1', 'ah-n-1', NULL, NOW(), NOW()),
                ('AH-N-2', 'ah-n-2', '   ', NOW(), NOW()),
                ('AH-N-3', 'ah-n-3', '  ju aichi  ', NOW(), NOW()),
                ('AH-N-4', 'ah-n-4', 'JU AICHI; USS', NOW(), NOW())
                """.trimIndent(),
            )
        }
        flyway(url).migrate()
        assertZeroMismatches(url)
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().executeQuery(
                """
                SELECT auction_house, auction_house_norm
                FROM purchases WHERE chassis = 'AH-N-3'
                """.trimIndent(),
            ).use { rs ->
                rs.next()
                assertEquals("  ju aichi  ", rs.getString(1))
                assertEquals("JU AICHI", rs.getString(2))
            }
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun v94MigrationFailsWhenNormalizedLengthExceeds100() {
        val url = startMysql()
        flyway(url, target = "93").migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            val expands = connection.createStatement().executeQuery(
                "SELECT CHAR_LENGTH(UPPER(REPEAT('ß', 51))) > 100",
            ).use { rs ->
                rs.next()
                rs.getInt(1) > 0
            }
            assumeTrue(
                expands,
                "MySQL 8.0 UPPER does not expand ß in this image; overflow pre-check is defensive for ASCII suppliers",
            )
            connection.createStatement().execute(
                """
                INSERT INTO purchases (chassis, chassis_key, auction_house, created_at, updated_at)
                VALUES ('AH-OVER', 'ah-over', REPEAT('ß', 51), NOW(), NOW())
                """.trimIndent(),
            )
        }
        assertThrows(FlywayException::class.java) {
            flyway(url, target = "94").migrate()
        }
    }

    @Test
    @Timeout(value = 4, unit = TimeUnit.MINUTES)
    fun staleNormDetectedAndRepairSqlRestoresConsistency() {
        val url = startMysql()
        flyway(url).migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO purchases (chassis, chassis_key, auction_house, auction_house_norm, created_at, updated_at)
                VALUES ('AH-STALE', 'ah-stale', '  ju aichi  ', 'WRONG', NOW(), NOW())
                """.trimIndent(),
            )
            assertTrue(countMismatches(connection) >= 1L)
            connection.createStatement().execute(REPAIR_SQL)
            assertEquals(0L, countMismatches(connection))
        }
    }

    private fun assertZeroMismatches(url: String) {
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            assertEquals(0L, countMismatches(connection))
        }
    }

    private fun countMismatches(connection: java.sql.Connection): Long {
        connection.createStatement().executeQuery(MISMATCH_COUNT_SQL).use { rs ->
            rs.next()
            return rs.getLong(1)
        }
    }

    private fun flyway(url: String, target: String? = null): Flyway {
        val configured = Flyway.configure()
            .dataSource(url, "root", PASSWORD)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
        if (target != null) configured.target(target)
        return configured.load()
    }

    private fun startMysql(): String {
        val name = "automan-v94-mig-${UUID.randomUUID().toString().take(8)}"
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
        error("MySQL migration test container did not become ready")
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
        private const val PASSWORD = "mysql_v94_mig_root"
        private const val DATABASE = "automan_v94_mig_test"

        private const val MISMATCH_COUNT_SQL = """
            SELECT COUNT(*) FROM purchases
            WHERE auction_house_norm IS NULL
               OR auction_house_norm <> UPPER(TRIM(COALESCE(auction_house, '')))
        """

        private const val REPAIR_SQL = """
            UPDATE purchases
            SET auction_house_norm = UPPER(TRIM(COALESCE(auction_house, '')))
            WHERE auction_house_norm IS NULL
               OR auction_house_norm <> UPPER(TRIM(COALESCE(auction_house, '')))
        """
    }
}
