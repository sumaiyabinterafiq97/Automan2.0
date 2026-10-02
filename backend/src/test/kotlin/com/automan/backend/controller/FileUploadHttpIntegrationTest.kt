package com.automan.backend.controller

import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
 * HTTP boundary for [FileUploadController]. The handler calls [com.automan.backend.service.PurchaseService.importPurchases],
 * which reads the upload as CSV text. Workbook sheets are not parsed here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileUploadHttpIntegrationTest {

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
    fun csvUploadCreatesThePurchaseAndAHeaderlessFileDoesNot() {
        val chassis = "P0UPL-${UUID.randomUUID().toString().take(8)}-1001"
        chassisToDelete.add(chassis)
        val csv = """
            CHASSIS,CAR NAME,SUPPLIER NAME,COUNTRY,DATE
            $chassis,Civic,USS Tokyo,Bangladesh,2026-04-01
        """.trimIndent()

        mockMvc.perform(
            multipart("/upload/excel").file(
                MockMultipartFile("file", "purchases.csv", "text/csv", csv.toByteArray()),
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.importedCount").value(1))

        val saved = purchaseRepository.findByChassisIgnoreCaseTrim(chassis).single()
        assertEquals("Civic", saved.carName)
        assertEquals("2026-04-01", saved.date)

        val before = purchaseRepository.count()
        mockMvc.perform(
            multipart("/upload/excel").file(
                MockMultipartFile("file", "broken.bin", "application/octet-stream", byteArrayOf(0x00, 0x01, 0x02, 0xFF.toByte())),
            ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.importedCount").value(0))
        assertEquals(before, purchaseRepository.count())
        assertTrue(purchaseRepository.findByChassisIgnoreCaseTrim(chassis).size == 1)
    }
}
