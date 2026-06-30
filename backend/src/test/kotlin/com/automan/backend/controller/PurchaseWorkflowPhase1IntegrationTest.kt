package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseWorkflowPhase1IntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var rixoHistoryRepository: RixoHistoryRepository

    @BeforeEach
    fun setUp() {
        rixoHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun `Rixo confirm-selected sets legacy flag and advances workflow_status`() {
        val saved = purchaseRepository.save(
            basePurchase("RIXO-001").copy(rixoConfirmed = "FALSE", workflowStatus = WorkflowStatus.PURCHASED),
        )
        val history = rixoHistoryRepository.save(
            RixoHistory(
                buyingDate = LocalDate.now(),
                rixoCompany = "Test Co",
                chassis = "RIXO-001",
            ),
        )
        val historyId = history.id ?: error("history id")

        mockMvc.perform(
            post("/rixo-history/confirm-selected")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"historyIds":[$historyId]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.updatedPurchases").value(1))

        val updated = purchaseRepository.findById(saved.id!!).orElseThrow()
        assertEquals(WorkflowStatus.RIXO_CONFIRMED, updated.workflowStatus)
        assertNotNull(updated.workflowStatusUpdatedAt)

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rixoConfirmed").value("TRUE"))
            .andExpect(jsonPath("$.workflowStatus").value("RIXO_CONFIRMED"))
    }

    @Test
    fun `chassis prefix search includes workflow-only Rixo confirmed cars in booking pool`() {
        purchaseRepository.save(
            basePurchase("WF-PREFIX-1").copy(
                workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
            ),
        )
        purchaseRepository.save(
            basePurchase("WF-PREFIX-2").copy(
                workflowStatus = WorkflowStatus.PURCHASED,
            ),
        )

        mockMvc.perform(get("/purchases/search-chassis?query=WF-PREFIX"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].chassis").value("WF-PREFIX-1"))
            .andExpect(jsonPath("$[0].rixoConfirmed").value("TRUE"))
    }

    @Test
    fun `GET purchase derives workflow flags from workflow_status after column drop`() {
        val saved = purchaseRepository.save(
            basePurchase("WF-GET-1").copy(workflowStatus = WorkflowStatus.BOOKING_REQUESTED),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.bookingRequested").value(true))
            .andExpect(jsonPath("$.rixoConfirmed").value("TRUE"))
            .andExpect(jsonPath("$.workflowStatus").value("BOOKING_REQUESTED"))
    }

    private fun basePurchase(chassis: String): Purchase = Purchase(
        chassis = chassis,
        carName = "Test Car",
        carModelYear = "2020",
        brand = "Toyota",
        country = "Japan",
        color = "White",
        fuel = "Gasoline",
        price = "10000.00",
        auctionFee = "500.00",
        recycleFee = "200.00",
        roadTax = "300.00",
        totalPrice = "11000.00",
        paymentDate = LocalDate.now().toString(),
        rixoRequested = "0",
        rixoConfirmed = "0",
        rixoPrice = "0.00",
        shipmentDate = LocalDate.now().toString(),
        vessel = "V1",
        shipmentCharges = "0",
        freight = "0",
        storageCharges = "0",
        miscCharges = "0",
        inspectionFee = "0",
        commission = "0",
        repairCompany = "",
        repairCharges = "0",
        notes = "",
        numberCut = "0",
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now(),
        bookingRequested = false,
    )
}
