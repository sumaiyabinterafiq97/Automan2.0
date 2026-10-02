package com.automan.backend.controller

import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Full chassis identity is rejected on the create API. Code-only chassis values may repeat.
 * Not @Transactional: the duplicate call throws inside the service transaction, and the
 * first insert must stay committed so the row count can be read afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseDuplicateChassisIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }

    private val fullChassis = "NZE141-1234567"
    private val codeOnly = "AAHH45"

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            purchaseRepository.findByChassisIgnoreCaseTrim(fullChassis).forEach { purchaseRepository.delete(it) }
            purchaseRepository.findByChassisIgnoreCaseTrim(codeOnly).forEach { purchaseRepository.delete(it) }
        }
    }

    @Test
    fun secondFullChassisCreateConflictsAndCodeOnlyChassisMayRepeat() {
        create(fullChassis).andExpect(status().isOk)
        create(fullChassis).andExpect(status().isConflict)
        assertEquals(1, purchaseRepository.findByChassisIgnoreCaseTrim(fullChassis).size)

        create(codeOnly).andExpect(status().isOk)
        create(codeOnly).andExpect(status().isOk)
        assertEquals(2, purchaseRepository.findByChassisIgnoreCaseTrim(codeOnly).size)
    }

    private fun create(chassis: String) = mockMvc.perform(
        post("/purchases")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"chassis":"$chassis","carName":"Dup Car","carModelYear":"2020-01"}"""),
    )
}
