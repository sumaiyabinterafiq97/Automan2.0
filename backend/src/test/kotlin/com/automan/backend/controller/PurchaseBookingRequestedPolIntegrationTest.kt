package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseBookingRequestedPolIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `POST booking-requested with pol updates POL and status`() {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = "BR-POL-1",
                pol = "YOKOHAMA",
                workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
            ),
        )

        mockMvc.perform(
            post("/purchases/booking-requested")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseIds":[${saved.id}],"pol":"Kawasaki"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        val updated = purchaseRepository.findById(saved.id!!).orElseThrow()
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, updated.workflowStatus)
        assertEquals("Kawasaki", updated.pol)
    }

    @Test
    fun `POST booking-requested without pol leaves POL unchanged`() {
        val saved = purchaseRepository.save(
            Purchase(
                chassis = "BR-POL-2",
                pol = "YOKOHAMA",
                workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
            ),
        )

        mockMvc.perform(
            post("/purchases/booking-requested")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseIds":[${saved.id}]}"""),
        )
            .andExpect(status().isOk)

        val updated = purchaseRepository.findById(saved.id!!).orElseThrow()
        assertEquals(WorkflowStatus.BOOKING_REQUESTED, updated.workflowStatus)
        assertEquals("YOKOHAMA", updated.pol)
    }
}
