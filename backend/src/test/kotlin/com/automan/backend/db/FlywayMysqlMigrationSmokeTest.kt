package com.automan.backend.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Migrates an empty MySQL 8 database with the real Flyway chain (SQL and Kotlin).
 * The container is created for this test and removed afterwards.
 * It does not use application-test.yml, H2, the Compose database, Railway, or RDS.
 */
class FlywayMysqlMigrationSmokeTest {

    private var containerName: String? = null

    @AfterEach
    fun removeContainer() {
        val name = containerName ?: return
        ProcessBuilder("docker", "rm", "-f", name)
            .redirectErrorStream(true)
            .start()
            .waitFor(30, TimeUnit.SECONDS)
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    fun cleanMysqlMigratesToV83AndExposesCoreTables() {
        val name = "automan-flyway-smoke-${UUID.randomUUID().toString().take(8)}"
        containerName = name
        val password = "flyway_smoke_only"
        val database = "automan_flyway_smoke"

        exec(
            "docker", "run", "-d",
            "--name", name,
            "-p", "127.0.0.1::3306",
            "-e", "MYSQL_ROOT_PASSWORD=$password",
            "-e", "MYSQL_DATABASE=$database",
            "mysql:8.0",
            "--default-authentication-plugin=mysql_native_password",
        )
        val port = publishedPort(name)
        val url = "jdbc:mysql://127.0.0.1:$port/$database" +
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        assertDisposableDatabase(url, database)
        waitUntilReady(name, password)

        val flyway = Flyway.configure()
            .dataSource(url, "root", password)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .load()
        val result = flyway.migrate()
        val info = flyway.info()

        assertTrue(result.success)
        assertTrue(result.migrationsExecuted > 0)
        val failed = info.all().filter { it.state == MigrationState.FAILED }
        assertTrue(failed.isEmpty()) { "Failed migrations: ${failed.map { it.version }}" }
        assertTrue(info.pending().isEmpty()) { "Pending migrations: ${info.pending().map { it.version }}" }
        assertEquals("83", info.current().version.version)

        DriverManager.getConnection(url, "root", password).use { connection ->
            val tables = mutableSetOf<String>()
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = '$database'
                    """.trimIndent(),
                ).use { rows ->
                    while (rows.next()) {
                        tables.add(rows.getString(1).lowercase())
                    }
                }
            }
            val missing = CORE_TABLES.filter { it !in tables }
            assertTrue(missing.isEmpty()) { "Missing tables: $missing" }

            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM purchases").use { rows ->
                    assertTrue(rows.next())
                    assertTrue(rows.getLong(1) >= 0L)
                }
            }
        }
    }

    private fun assertDisposableDatabase(url: String, database: String) {
        val lower = url.lowercase()
        check(lower.startsWith("jdbc:mysql://127.0.0.1:")) { "Refusing non-local database: $url" }
        check(lower.contains("/$database")) { "Refusing unexpected database: $url" }
        val blocked = listOf("railway", "amazonaws", "rds.", "automan_car_purchase")
        val hit = blocked.firstOrNull { lower.contains(it) }
        check(hit == null) { "Refusing non-test database marker '$hit' in $url" }
    }

    private fun publishedPort(name: String): Int {
        val output = exec("docker", "port", name, "3306/tcp").trim()
        val port = output.substringAfterLast(":").trim().toIntOrNull()
        check(port != null && port > 0) { "Could not read published port from '$output'" }
        return port
    }

    private fun waitUntilReady(name: String, password: String) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(90)
        var last = ""
        while (System.currentTimeMillis() < deadline) {
            val process = ProcessBuilder(
                "docker", "exec", name,
                "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "-p$password", "--silent",
            ).redirectErrorStream(true).start()
            last = process.inputStream.bufferedReader().readText()
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) return
            Thread.sleep(2000)
        }
        error("MySQL smoke container did not become ready. Last output: $last")
    }

    private fun exec(vararg command: String): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        check(finished && process.exitValue() == 0) {
            "Command failed: ${command.joinToString(" ")}\n$output"
        }
        return output
    }

    companion object {
        private val CORE_TABLES = listOf(
            "purchases",
            "clients",
            "events",
            "invoice_history",
            "shipping_history",
            "rixo_history",
            "rixo_mapping",
            "booking_mappings",
            "car_brand_mapping",
            "purchase_media",
            "stock_location_map",
            "shipping_charge_map",
        )
    }
}
