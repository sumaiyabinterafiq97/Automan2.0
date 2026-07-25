package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseDistinctDatesForRixoIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `GET distinct-purchase-dates only returns dates with pending rixo requested`() {
        // Pending = workflow_status null or PURCHASED (rixoRequested is @Transient).
        purchaseRepository.save(
            Purchase(
                chassis = "PEND-001",
                date = "July 10, 2026 (Friday)",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "PEND-002",
                date = "July 10, 2026 (Friday)",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "DONE-001",
                date = "July 4, 2026 (Saturday)",
                workflowStatus = WorkflowStatus.RIXO_REQUESTED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "DONE-002",
                date = "July 4, 2026 (Saturday)",
                workflowStatus = WorkflowStatus.RIXO_REQUESTED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "PEND-003",
                date = "June 27, 2026 (Saturday)",
                workflowStatus = null,
            ),
        )

        val result = mockMvc.perform(get("/purchases/distinct-purchase-dates"))
            .andExpect(status().isOk)
            .andReturn()

        val dates = objectMapper.readValue(
            result.response.contentAsByteArray,
            object : TypeReference<List<String>>() {},
        )

        assertTrue(dates.contains("2026-07-10"), "pending date should be included: $dates")
        assertTrue(dates.contains("2026-06-27"), "null workflow_status date should be included: $dates")
        assertFalse(dates.contains("2026-07-04"), "fully requested date must be excluded: $dates")
        assertEquals(listOf("2026-07-10", "2026-06-27"), dates)
    }
}
