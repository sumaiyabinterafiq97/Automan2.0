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

/**
 * Rixo Request Generator: date-scoped for-rixo must find weekday-labeled purchase.date
 * values (same parse equality as distinct-purchase-dates), not only ISO LIKE matches.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseForRixoIntegrationTest {

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
    fun `GET for-rixo returns pending rows for weekday-labeled purchase dates`() {
        purchaseRepository.save(
            Purchase(
                chassis = "LABELED-PEND-001",
                date = "June 29, 2026 (Monday)",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "LABELED-PEND-002",
                date = "June 29, 2026 (Monday)",
                rixoCompany = "ABC",
                workflowStatus = null,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "LABELED-DONE-001",
                date = "June 29, 2026 (Monday)",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.RIXO_REQUESTED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "OTHER-DAY-001",
                date = "July 8, 2026 (Wednesday)",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )

        val result = mockMvc.perform(get("/purchases/for-rixo").param("dateIso", "2026-06-29"))
            .andExpect(status().isOk)
            .andReturn()

        val rows = objectMapper.readTree(result.response.contentAsByteArray)
        assertTrue(rows.isArray)
        val chassis = rows.map { it.path("chassis").asText() }.toSet()
        assertEquals(setOf("LABELED-PEND-001", "LABELED-PEND-002"), chassis)
        assertFalse(chassis.contains("LABELED-DONE-001"))
        assertFalse(chassis.contains("OTHER-DAY-001"))

        val companies = rows.map { it.path("rixoCompany").asText() }.toSet()
        assertEquals(setOf("KLC", "ABC"), companies)
    }

    @Test
    fun `GET for-rixo filters by company for weekday-labeled dates`() {
        purchaseRepository.save(
            Purchase(
                chassis = "CO-KLC",
                date = "July 10, 2026 (Friday)",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "CO-ABC",
                date = "July 10, 2026 (Friday)",
                rixoCompany = "ABC",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )

        val result = mockMvc.perform(
            get("/purchases/for-rixo")
                .param("dateIso", "2026-07-10")
                .param("rixoCompany", "KLC"),
        )
            .andExpect(status().isOk)
            .andReturn()

        val rows = objectMapper.readTree(result.response.contentAsByteArray)
        assertEquals(1, rows.size())
        assertEquals("CO-KLC", rows[0].path("chassis").asText())
    }

    @Test
    fun `GET for-rixo returns empty for date with only requested purchases`() {
        purchaseRepository.save(
            Purchase(
                chassis = "DONE-ONLY-001",
                date = "July 4, 2026 (Saturday)",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.RIXO_REQUESTED,
            ),
        )

        val result = mockMvc.perform(get("/purchases/for-rixo").param("dateIso", "2026-07-04"))
            .andExpect(status().isOk)
            .andReturn()

        val rows = objectMapper.readTree(result.response.contentAsByteArray)
        assertTrue(rows.isArray)
        assertEquals(0, rows.size())
    }

    @Test
    fun `GET distinct-purchase-dates excludes dates with only RIXO_REQUESTED purchases`() {
        purchaseRepository.save(
            Purchase(
                chassis = "PEND-ISO",
                date = "2026-07-10",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )
        purchaseRepository.save(
            Purchase(
                chassis = "DONE-LABELED",
                date = "July 4, 2026 (Saturday)",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.RIXO_REQUESTED,
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
        assertFalse(dates.contains("2026-07-04"), "fully requested date must be excluded: $dates")
    }

    @Test
    fun `GET for-rixo still matches ISO-stored purchase dates`() {
        purchaseRepository.save(
            Purchase(
                chassis = "ISO-PEND",
                date = "2026-06-12",
                rixoCompany = "KLC",
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )

        val result = mockMvc.perform(get("/purchases/for-rixo").param("dateIso", "2026-06-12"))
            .andExpect(status().isOk)
            .andReturn()

        val rows = objectMapper.readTree(result.response.contentAsByteArray)
        assertEquals(1, rows.size())
        assertEquals("ISO-PEND", rows[0].path("chassis").asText())
    }
}
