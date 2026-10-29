package com.automan.backend.db

import com.automan.backend.util.ChassisKeyNormalizer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/** V92 schema + seed data → V93 backfill on disposable MySQL 8.0 (requires Docker). */
class PurchaseChassisKeyV93MigrationMysqlTest {

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
    fun v92SeedThenV93BackfillPreservesKeys() {
        val url = startMysql()
        flyway(url, target = "92").migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().execute(
                """
                INSERT INTO purchases (chassis, created_at, updated_at) VALUES
                ('  RIXO-001  ', NOW(), NOW()),
                ('rixo-001', NOW(), NOW())
                """.trimIndent(),
            )
        }
        flyway(url).migrate()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.createStatement().executeQuery(
                "SELECT chassis, chassis_key FROM purchases ORDER BY id",
            ).use { rows ->
                val keys = mutableListOf<String>()
                while (rows.next()) {
                    val chassis = rows.getString(1)
                    val key = rows.getString(2)
                    assertEquals(ChassisKeyNormalizer.normalize(chassis), key)
                    keys.add(key)
                }
                assertEquals(2, keys.size)
                assertEquals(listOf("rixo-001", "rixo-001"), keys)
            }
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
        val name = "automan-v93-mig-${UUID.randomUUID().toString().take(8)}"
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
        private const val PASSWORD = "mysql_v93_mig_root"
        private const val DATABASE = "automan_v93_mig_test"
    }
}
