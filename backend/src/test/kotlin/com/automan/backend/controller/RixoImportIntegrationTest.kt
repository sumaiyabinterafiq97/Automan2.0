package com.automan.backend.controller

import com.automan.backend.repository.RixoMappingRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Text import uses the same parser as CSV file import.
 * Columns are supplier, stock, company, venue. A later short row does not roll back earlier rows.
 * The same supplier + stock + company updates the existing row instead of inserting another.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RixoImportIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var rixoMappingRepository: RixoMappingRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val auctions = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            val ids = rixoMappingRepository.findAll()
                .filter { row -> auctions.any { it.equals(row.auctionName, ignoreCase = true) } }
                .mapNotNull { it.id }
            if (ids.isNotEmpty()) rixoMappingRepository.deleteAllById(ids)
        }
        auctions.clear()
    }

    @Test
    fun validRowIsStoredAndAShortRowDoesNotRemoveIt() {
        val auction = auction()
        val csv = """
            supplier,stock,company,venue
            $auction,GLOBAL NAGOYA,LOGICO,95518
            too,short
        """.trimIndent()

        mockMvc.perform(
            post("/rixo/import/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"csvContent":${jsonString(csv)}}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.successCount").value(1))
            .andExpect(jsonPath("$.errorCount").value(1))

        val rows = rixoMappingRepository.findByAuctionNameIgnoreCase(auction)
        assertEquals(1, rows.size)
        assertEquals("GLOBAL NAGOYA", rows[0].stockLocation)
        assertEquals("LOGICO", rows[0].rixoCompany)
        assertEquals("95518", rows[0].venueId)
        assertNull(rows[0].rixoPrice)
    }

    @Test
    fun importingTheSameSupplierStockAndCompanyMergesVenue() {
        val auction = auction()
        val header = "supplier,stock,company,venue"
        postCsv("$header\n$auction,GLOBAL NAGOYA,LOGICO,111")
            .andExpect(jsonPath("$.successCount").value(1))
        postCsv("$header\n$auction,GLOBAL NAGOYA,LOGICO,222")
            .andExpect(jsonPath("$.successCount").value(1))

        val rows = rixoMappingRepository.findByAuctionNameIgnoreCase(auction)
        assertEquals(1, rows.size)
        assertEquals("222", rows[0].venueId)
    }

    @Test
    fun textImportRequiresCsvContent() {
        mockMvc.perform(
            post("/rixo/import/text")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("Import failed: csvContent is required"))
    }

    private fun auction(): String {
        val value = "P0IMP-${UUID.randomUUID().toString().take(8)}"
        auctions.add(value)
        return value
    }

    private fun postCsv(csv: String) = mockMvc.perform(
        post("/rixo/import/text")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"csvContent":${jsonString(csv)}}"""),
    ).andExpect(status().isOk)

    private fun jsonString(raw: String): String =
        "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}
