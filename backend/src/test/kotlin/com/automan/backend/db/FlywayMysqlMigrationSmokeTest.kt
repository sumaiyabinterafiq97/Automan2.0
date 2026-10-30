package com.automan.backend.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationState
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
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
    fun cleanMysqlMigratesToV94AndExposesCoreTables() {
        val url = startMysql()
        val flyway = flyway(url)
        val result = flyway.migrate()
        assertMigratedTo(flyway, result.migrationsExecuted, "94")

        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            val tables = tableNames(connection)
            val missing = CORE_TABLES.filter { it !in tables }
            assertTrue(missing.isEmpty()) { "Missing tables: $missing" }
            assertEquals("varchar(16)", columnType(connection, "users", "role"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM consignees"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM user_party_links"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM clients WHERE consignee_id IS NOT NULL"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM client_consignee_assignments"))
            assertPartySchema(connection)
            assertChassisAssociationIndexes(connection)
            assertConcurrentActiveLinkRejected(url)
        }
    }

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    fun v86DatabaseWithLegacyRoleEnumMigratesAndKeepsUsers() {
        val url = startMysql()
        val stopped = flyway(url, target = "86")
        val stoppedResult = stopped.migrate()
        assertMigratedTo(stopped, stoppedResult.migrationsExecuted, "86")

        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "ALTER TABLE users MODIFY COLUMN role ENUM('VIEWER','EDITOR','ADMIN') NOT NULL",
                )
                statement.execute(
                    """
                    INSERT INTO users (email, name, password_hash, role)
                    VALUES ('legacy-admin@automan.test', 'Legacy Admin', 'not-a-real-hash', 'ADMIN')
                    """.trimIndent(),
                )
            }
            assertEquals("enum('viewer','editor','admin')", columnType(connection, "users", "role"))
        }

        val migrated = flyway(url)
        val migratedResult = migrated.migrate()
        assertTrue(migratedResult.migrationsExecuted >= 5)
        assertMigratedTo(migrated, migratedResult.migrationsExecuted, "94")

        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            assertEquals("varchar(16)", columnType(connection, "users", "role"))
            assertEquals(
                "ADMIN",
                queryString(connection, "SELECT role FROM users WHERE email = 'legacy-admin@automan.test'"),
            )
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO users (email, name, password_hash, role) VALUES
                    ('legacy-client@automan.test', 'Legacy Client', 'not-a-real-hash', 'CLIENT'),
                    ('legacy-consignee@automan.test', 'Legacy Consignee', 'not-a-real-hash', 'CONSIGNEE')
                    """.trimIndent(),
                )
            }
            assertEquals("CLIENT", queryString(connection, "SELECT role FROM users WHERE email = 'legacy-client@automan.test'"))
            assertEquals(
                "CONSIGNEE",
                queryString(connection, "SELECT role FROM users WHERE email = 'legacy-consignee@automan.test'"),
            )
            val adminId = count(connection, "SELECT id FROM users WHERE email = 'legacy-admin@automan.test'")
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO auth_sessions (token_hash, user_id, created_at, expires_at)
                    VALUES ('${"a".repeat(64)}', $adminId, UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 DAY))
                    """.trimIndent(),
                )
            }
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM auth_sessions WHERE user_id = $adminId"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM clients WHERE consignee_id IS NOT NULL"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM consignees"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM user_party_links"))
            assertEquals(0L, count(connection, "SELECT COUNT(*) FROM client_consignee_assignments"))
        }
    }

    private fun assertPartySchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("INSERT INTO consignees (legal_name, status) VALUES ('Overseas Transit', 'ACTIVE')")
            statement.execute("INSERT INTO consignees (legal_name, status) VALUES ('Overseas Transit', 'ACTIVE')")
            statement.execute("INSERT INTO clients (client_number, client_name) VALUES ('P2B-1', 'Unassigned Client')")
            statement.execute("INSERT INTO clients (client_number, client_name) VALUES ('P2B-2', 'Second Client')")
        }
        val consigneeId = count(connection, "SELECT MIN(id) FROM consignees")
        val clientId = count(connection, "SELECT id FROM clients WHERE client_number = 'P2B-1'")
        val secondClientId = count(connection, "SELECT id FROM clients WHERE client_number = 'P2B-2'")
        connection.prepareStatement("UPDATE clients SET consignee_id = ? WHERE id = ?").use { update ->
            update.setLong(1, consigneeId)
            update.setLong(2, clientId)
            assertEquals(1, update.executeUpdate())
        }
        assertNull(queryNullable(connection, "SELECT consignee_id FROM clients WHERE client_number = 'P2B-2'"))
        assertThrows(SQLException::class.java) {
            connection.createStatement().execute(
                "INSERT INTO clients (client_number, client_name, consignee_id) VALUES ('P2B-BAD', 'Bad', 999999)",
            )
        }

        connection.createStatement().use { statement ->
            statement.execute(
                """
                INSERT INTO users (email, name, password_hash, role) VALUES
                ('party-a@automan.test', 'Party A', 'hash', 'CLIENT'),
                ('party-b@automan.test', 'Party B', 'hash', 'CLIENT')
                """.trimIndent(),
            )
        }
        val userA = count(connection, "SELECT id FROM users WHERE email = 'party-a@automan.test'")
        val userB = count(connection, "SELECT id FROM users WHERE email = 'party-b@automan.test'")
        insertLink(connection, userA, "CLIENT", clientId, "ACTIVE", null)
        insertLink(connection, userB, "CLIENT", clientId, "ACTIVE", null)
        assertEquals(2L, count(connection, "SELECT COUNT(*) FROM user_party_links WHERE party_id = $clientId AND status = 'ACTIVE'"))
        assertThrows(SQLException::class.java) {
            insertLink(connection, userA, "CLIENT", secondClientId, "ACTIVE", null)
        }
        insertLink(connection, userA, "CLIENT", secondClientId, "REVOKED", "UTC_TIMESTAMP(6)")
        insertLink(connection, userA, "CONSIGNEE", consigneeId, "ACTIVE", null)
        assertThrows(SQLException::class.java) {
            connection.createStatement().execute(
                """
                INSERT INTO user_party_links (user_id, party_type, party_id, client_id, status)
                VALUES ($userA, 'CLIENT', $clientId, $secondClientId, 'PENDING')
                """.trimIndent(),
            )
        }
    }

    private fun assertChassisAssociationIndexes(connection: Connection) {
        assertEquals(
            0L,
            count(
                connection,
                """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'purchases'
                  AND column_name = 'chassis' AND non_unique = 0
                """.trimIndent(),
            ),
        )
        assertEquals(
            1L,
            count(
                connection,
                """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'shipping_history'
                  AND index_name = 'ux_shipping_history_chassis' AND non_unique = 0
                """.trimIndent(),
            ),
        )
        assertEquals(
            0L,
            count(
                connection,
                """
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = 'invoice_history_line'
                  AND column_name = 'chassis' AND non_unique = 0
                """.trimIndent(),
            ),
        )
    }

    private fun assertConcurrentActiveLinkRejected(url: String) {
        val userId = DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO users (email, name, password_hash, role)
                VALUES ('party-race@automan.test', 'Party Race', 'hash', 'CLIENT')
                """.trimIndent(),
            )
            count(connection, "SELECT id FROM users WHERE email = 'party-race@automan.test'")
        }
        val clientId = DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            count(connection, "SELECT id FROM clients WHERE client_number = 'P2B-1'")
        }
        val sql = """
            INSERT INTO user_party_links (user_id, party_type, party_id, client_id, status)
            VALUES ($userId, 'CLIENT', $clientId, $clientId, 'ACTIVE')
        """.trimIndent()
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val results = Collections.synchronizedList(mutableListOf<String>())
        repeat(2) {
            Thread {
                try {
                    start.await(20, TimeUnit.SECONDS)
                    DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
                        connection.createStatement().execute(sql)
                    }
                    results.add("ok")
                } catch (_: SQLException) {
                    results.add("rejected")
                } finally {
                    done.countDown()
                }
            }.start()
        }
        start.countDown()
        assertTrue(done.await(20, TimeUnit.SECONDS))
        assertEquals(listOf("ok", "rejected"), results.sorted())
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            assertEquals(1L, count(connection, "SELECT COUNT(*) FROM user_party_links WHERE user_id = $userId AND status = 'ACTIVE'"))
        }
    }

    private fun insertLink(
        connection: Connection,
        userId: Long,
        partyType: String,
        partyId: Long,
        status: String,
        revokedAtSql: String?,
    ) {
        val targetColumn = if (partyType == "CLIENT") "client_id" else "consignee_id"
        val revokedAt = revokedAtSql ?: "NULL"
        connection.createStatement().execute(
            """
            INSERT INTO user_party_links
                (user_id, party_type, party_id, $targetColumn, status, revoked_at)
            VALUES ($userId, '$partyType', $partyId, $partyId, '$status', $revokedAt)
            """.trimIndent(),
        )
    }

    private fun startMysql(): String {
        val name = "automan-flyway-smoke-${UUID.randomUUID().toString().take(8)}"
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
        assertDisposableDatabase(url, DATABASE)
        waitUntilReady(name, PASSWORD)
        return url
    }

    private fun flyway(url: String, target: String? = null): Flyway {
        val configured = Flyway.configure()
            .dataSource(url, "root", PASSWORD)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
        if (target != null) configured.target(target)
        return configured.load()
    }

    private fun assertMigratedTo(flyway: Flyway, executed: Int, version: String) {
        val info = flyway.info()
        assertTrue(executed > 0)
        val failed = info.all().filter { it.state == MigrationState.FAILED }
        assertTrue(failed.isEmpty()) { "Failed migrations: ${failed.map { it.version }}" }
        if (version != "86") {
            assertTrue(info.pending().isEmpty()) { "Pending migrations: ${info.pending().map { it.version }}" }
        }
        assertEquals(version, info.current().version.version)
    }

    private fun tableNames(connection: Connection): Set<String> {
        val tables = mutableSetOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = '$DATABASE'
                """.trimIndent(),
            ).use { rows ->
                while (rows.next()) tables.add(rows.getString(1).lowercase())
            }
        }
        return tables
    }

    private fun columnType(connection: Connection, table: String, column: String): String {
        return queryString(
            connection,
            """
            SELECT COLUMN_TYPE
            FROM information_schema.columns
            WHERE table_schema = '$DATABASE' AND table_name = '$table' AND column_name = '$column'
            """.trimIndent(),
        ).lowercase()
    }

    private fun count(connection: Connection, sql: String): Long {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                check(rows.next())
                return rows.getLong(1)
            }
        }
    }

    private fun queryString(connection: Connection, sql: String): String =
        queryNullable(connection, sql) ?: ""

    private fun queryNullable(connection: Connection, sql: String): String? {
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                check(rows.next()) { "No row for $sql" }
                return rows.getString(1)
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
            "auth_sessions",
            "auth_bootstrap",
            "consignees",
            "user_party_links",
            "client_consignee_assignments",
        )

        private const val PASSWORD = "flyway_smoke_only"
        private const val DATABASE = "automan_flyway_smoke"
    }
}
