package com.automan.backend.db

import com.automan.backend.util.AuctionHouseNormalizer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Compares [AuctionHouseNormalizer] to MySQL `UPPER(TRIM(COALESCE(?, '')))` on MySQL 8.0 (Docker).
 */
class AuctionHouseNormalizerMysqlEquivalenceTest {

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
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun kotlinNormMatchesMysqlReferenceForFixtures() {
        val url = startMysql()
        val fixtures = listOf(
            null,
            "",
            "   ",
            "  ju aichi  ",
            "USS NAGOYA",
            "\tJU AICHI\t",
            "\u00A0X\u00A0",
            "JU AICHI; USS NAGOYA",
        )
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.prepareStatement(
                "SELECT UPPER(TRIM(COALESCE(?, '')))",
            ).use { ps ->
                for (raw in fixtures) {
                    ps.setString(1, raw)
                    ps.executeQuery().use { rs ->
                        rs.next()
                        val sqlRef = rs.getString(1).orEmpty()
                        assertEquals(
                            sqlRef,
                            AuctionHouseNormalizer.normalize(raw),
                            "Mismatch for input: ${raw?.let { it.codePoints().toArray().contentToString() } ?: "null"}",
                        )
                    }
                }
            }
        }
    }

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    fun eszettNormalizationMatchesMysqlWhenComparedPerConnection() {
        val url = startMysql()
        DriverManager.getConnection(url, "root", PASSWORD).use { connection ->
            connection.prepareStatement("SELECT UPPER(TRIM(COALESCE(?, '')))").use { ps ->
                ps.setString(1, "ß")
                ps.executeQuery().use { rs ->
                    rs.next()
                    assertEquals(rs.getString(1).orEmpty(), AuctionHouseNormalizer.normalize("ß"))
                }
            }
        }
    }

    private fun startMysql(): String {
        val name = "automan-ah-norm-eq-${UUID.randomUUID().toString().take(8)}"
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
        private const val PASSWORD = "mysql_ah_norm_eq_root"
        private const val DATABASE = "automan_ah_norm_eq_test"
    }
}
