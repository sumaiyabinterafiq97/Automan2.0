package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
 * HTTP wiring for confirm-selected and remove-selected.
 * Chassis confirm rules themselves are covered by [com.automan.backend.service.RixoHistoryChassisConfirmTest].
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RixoHistoryHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var rixoHistoryRepository: RixoHistoryRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisNumbers = mutableListOf<String>()
    private val historyIds = mutableListOf<Long>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            historyIds.distinct().forEach { id ->
                if (rixoHistoryRepository.existsById(id)) rixoHistoryRepository.deleteById(id)
            }
            chassisNumbers.distinct().forEach { chassis ->
                purchaseRepository.findByChassis(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        historyIds.clear()
        chassisNumbers.clear()
    }

    @Test
    fun confirmSelectedMarksOnlyChassisOnTheChosenRow() {
        val selected = chassis()
        val other = chassis()
        val selectedPurchase = purchaseRepository.save(
            Purchase(chassis = selected, carName = "Selected", workflowStatus = WorkflowStatus.PURCHASED),
        )
        val otherPurchase = purchaseRepository.save(
            Purchase(chassis = other, carName = "Other", workflowStatus = WorkflowStatus.PURCHASED),
        )
        val row = rixoHistoryRepository.save(RixoHistory(rixoCompany = "LOGICO", chassis = selected))
        historyIds.add(row.id!!)

        mockMvc.perform(
            post("/rixo-history/confirm-selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyIds":[${row.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.selectedRows").value(1))
            .andExpect(jsonPath("$.updatedPurchases").value(1))

        assertEquals(WorkflowStatus.RIXO_CONFIRMED, purchaseRepository.findById(selectedPurchase.id!!).orElseThrow().workflowStatus)
        assertEquals(WorkflowStatus.PURCHASED, purchaseRepository.findById(otherPurchase.id!!).orElseThrow().workflowStatus)
    }

    @Test
    fun confirmChassisRequiresHistoryIdAndToken() {
        mockMvc.perform(
            post("/rixo-history/confirm-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassisToken":"AAA"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("historyId is required"))

        mockMvc.perform(
            post("/rixo-history/confirm-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyId":1}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("chassisToken is required"))
    }

    @Test
    fun removeSelectedDeletesTheHistoryRow() {
        val row = rixoHistoryRepository.save(RixoHistory(rixoCompany = "LOGICO", chassis = chassis()))
        historyIds.add(row.id!!)

        mockMvc.perform(
            post("/rixo-history/remove-selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyIds":[${row.id}]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deletedRows").value(1))

        assertFalse(rixoHistoryRepository.existsById(row.id!!))
    }

    @Test
    fun removeChassisRequiresHistoryId() {
        mockMvc.perform(
            post("/rixo-history/remove-chassis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassisToken":"AAA"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("historyId is required"))
    }

    private fun chassis(): String {
        val value = "P0RH-${UUID.randomUUID().toString().take(8)}"
        chassisNumbers.add(value)
        return value
    }
}
