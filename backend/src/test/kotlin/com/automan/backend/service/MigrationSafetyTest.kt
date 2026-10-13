package com.automan.backend.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText

class MigrationSafetyTest {

    @Test
    fun v25InvoiceHistoryBackfillUsesDataSizedSequence() {
        val sql = Path.of("src/main/resources/db/migration/V25__invoice_history_lines.sql").readText()

        assertTrue(sql.contains("WITH RECURSIVE nums"), "V25 must generate rows beyond a fixed inline list")
        assertTrue(
            sql.contains("MAX(CHAR_LENGTH(chassis) - CHAR_LENGTH(REPLACE(chassis, ';', '')) + 1)"),
            "V25 must size the generated sequence from the legacy chassis data",
        )
        assertFalse(
            Regex("""UNION\s+SELECT\s+30""", RegexOption.IGNORE_CASE).containsMatchIn(sql),
            "V25 must not cap invoice line backfill at 30 chassis",
        )
    }

    @Test
    fun v17InvoiceConfirmedMigrationIsIdempotent() {
        val sql = Path.of("src/main/resources/db/migration/V17__purchases_add_invoice_confirmed.sql").readText()

        assertTrue(sql.contains("INFORMATION_SCHEMA.COLUMNS"))
        assertTrue(sql.contains("COLUMN_NAME = 'invoice_confirmed'"))
        assertTrue(sql.contains("PREPARE stmt_purchase_invoice_confirmed"))
    }

    @Test
    fun backendDockerHealthchecksUseRuntimePort() {
        listOf("Dockerfile", "Dockerfile.prebuilt").forEach { dockerfile ->
            val contents = Path.of(dockerfile).readText()

            assertTrue(
                contents.contains("\${PORT:-8083}/api/actuator/health"),
                "$dockerfile healthcheck must honor hosted runtime PORT",
            )
            assertFalse(
                contents.contains("localhost:8083/api/actuator/health"),
                "$dockerfile healthcheck must not probe fixed 8083 when PORT can vary",
            )
        }
    }
}
