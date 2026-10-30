package com.automan.backend.db

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.AuctionHouseNormalizer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.util.concurrent.TimeUnit

/** JPA maintenance of auction_house_norm on disposable MySQL 8.0 (requires Docker). */
@SpringBootTest
@ActiveProfiles("mysql-jpa-test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PurchaseAuctionHouseNormMysqlJpaTest {

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Test
    @Transactional
    fun jpaInsertAndUpdateMaintainAuctionHouseNormWithoutChangingDisplayValue() {
        val saved = purchaseRepository.saveAndFlush(
            Purchase(
                chassis = "AH-JPA-1",
                auctionHouse = "  ju aichi  ",
            ),
        )
        assertEquals("  ju aichi  ", saved.auctionHouse)
        assertEquals("JU AICHI", saved.auctionHouseNorm)

        val updated = purchaseRepository.saveAndFlush(
            saved.copy(auctionHouse = "  USS TOKYO  "),
        )
        assertEquals("  USS TOKYO  ", updated.auctionHouse)
        assertEquals("USS TOKYO", updated.auctionHouseNorm)

        val loaded = purchaseRepository.findByAuctionHouseNorm("USS TOKYO")
        assertEquals(1, loaded.size)
    }

    companion object {
        private const val PASSWORD = "mysql_ah_jpa_root"
        private const val DATABASE = "automan_ah_jpa_test"
        private var containerName: String? = null
        private val jdbcUrl: String

        init {
            jdbcUrl = startMysql()
            flyway(jdbcUrl).migrate()
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
            val name = "automan-ah-jpa-${UUID.randomUUID().toString().take(8)}"
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
            error("MySQL jpa test container did not become ready")
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
