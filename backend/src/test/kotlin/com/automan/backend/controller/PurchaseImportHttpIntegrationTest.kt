package com.automan.backend.controller

import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * CSV import contract: a headed row is inserted, a blank chassis is skipped, a missing header or
 * empty file imports nothing, and a second file for the same chassis keeps one row while updating
 * date, supplier, and country.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseImportHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisToDelete = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            chassisToDelete.toList().forEach { chassis ->
                purchaseRepository.findByChassisIgnoreCaseTrim(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        chassisToDelete.clear()
    }

    @Test
    fun validImportPersistsTheRowAndSkipsAChassislessRow() {
        val chassis = track("P0IMP-${token()}-1001")
        val csv = """
            CHASSIS,CAR NAME,SUPPLIER NAME,COUNTRY,DATE
            $chassis,Civic,USS Tokyo,Bangladesh,2026-04-01
            ,No Chassis Car,USS Tokyo,Bangladesh,2026-04-01
            -,Dash Chassis,USS Tokyo,Bangladesh,2026-04-01
        """.trimIndent()

        mockMvc.perform(multipart("/purchases/import").file(csvFile(csv)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.importedCount").value(1))
            .andExpect(jsonPath("$.errorCount").value(0))

        val saved = purchaseRepository.findByChassisIgnoreCaseTrim(chassis).single()
        assertEquals("Civic", saved.carName)
        assertEquals("USS Tokyo", saved.auctionHouse)
        assertEquals("Bangladesh", saved.country)
        assertEquals("2026-04-01", saved.date)
    }

    @Test
    fun emptyAndHeaderlessFilesImportNothing() {
        mockMvc.perform(multipart("/purchases/import").file(csvFile("   \n")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.importedCount").value(0))
            .andExpect(jsonPath("$.message").value("No data found in CSV file"))

        val before = purchaseRepository.count()
        mockMvc.perform(multipart("/purchases/import").file(csvFile("foo,bar\n1,2\n")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.importedCount").value(0))
            .andExpect(jsonPath("$.message").value("No valid header row found in CSV file"))
        assertEquals(before, purchaseRepository.count())
    }

    @Test
    fun reimportUpdatesKeyFieldsAndDoesNotCreateAnotherRow() {
        val chassis = track("P0RE-${token()}-1001")
        import(
            """
            CHASSIS,CAR NAME,SUPPLIER NAME,COUNTRY,DATE
            $chassis,Original Name,Old Supplier,Bangladesh,2026-04-01
            """.trimIndent(),
        )

        mockMvc.perform(
            multipart("/purchases/import").file(
                csvFile(
                    """
                    CHASSIS,CAR NAME,SUPPLIER NAME,COUNTRY,DATE
                    $chassis,Replacement Name,New Supplier,Kenya,2026-05-02
                    """.trimIndent(),
                ),
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.importedCount").value(1))

        val rows = purchaseRepository.findByChassisIgnoreCaseTrim(chassis)
        assertEquals(1, rows.size)
        assertEquals("New Supplier", rows.single().auctionHouse)
        assertEquals("Kenya", rows.single().country)
        assertEquals("2026-05-02", rows.single().date)
    }

    private fun import(csv: String) {
        mockMvc.perform(multipart("/purchases/import").file(csvFile(csv)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
    }

    private fun csvFile(csv: String) = MockMultipartFile(
        "file",
        "purchases.csv",
        "text/csv",
        csv.toByteArray(Charsets.UTF_8),
    )

    private fun track(chassis: String): String {
        chassisToDelete.add(chassis)
        return chassis
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
