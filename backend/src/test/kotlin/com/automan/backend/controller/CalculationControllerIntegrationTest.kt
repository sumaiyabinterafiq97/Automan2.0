package com.automan.backend.controller

import com.automan.backend.model.dto.CalculationRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CalculationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // No cleanup needed - calculations are no longer persisted
    }

    @Test
    fun `POST api calculations freight should calculate freight successfully`() {
        // Given
        val calculationRequest = CalculationRequest(
            country = null,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/freight")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Freight calculation completed"))
            .andExpect(jsonPath("$.totalPrice").value(2650.0)) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
            .andExpect(jsonPath("$.breakdown.containerPrice").value(1000.0))
            .andExpect(jsonPath("$.breakdown.shippingCharge").value(500.0))
            .andExpect(jsonPath("$.breakdown.wcCharge").value(200.0))
            .andExpect(jsonPath("$.breakdown.inspectionFee").value(100.0))
            .andExpect(jsonPath("$.breakdown.fobPrice").value(300.0))
            .andExpect(jsonPath("$.breakdown.freightPrice").value(400.0))
            .andExpect(jsonPath("$.breakdown.insurance").value(150.0))
    }

    @Test
    fun `POST api calculations caf should calculate CAF with country rules`() {
        // Given
        val calculationRequest = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("C&F calculation completed"))
            .andExpect(jsonPath("$.totalPrice").exists())
            .andExpect(jsonPath("$.breakdown.countryMultiplier").exists())
    }

    @Test
    fun `POST api calculations fob should calculate FOB successfully`() {
        // Given
        val calculationRequest = CalculationRequest(
            country = null,
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/fob")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("FOB calculation completed"))
            .andExpect(jsonPath("$.totalPrice").value(2650.0)) // 1000 + 500 + 200 + 100 + 300 + 400 + 150
    }

    @Test
    fun `POST api calculations pakistan should calculate Pakistan charges successfully`() {
        // Given
        val calculationRequest = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = true // Required for Pakistan calculation
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/pakistan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Pakistan calculation completed"))
            .andExpect(jsonPath("$.totalPrice").exists())
            .andExpect(jsonPath("$.breakdown.customDuty").exists())
            .andExpect(jsonPath("$.breakdown.otherCharges").exists())
            .andExpect(jsonPath("$.breakdown.baseTotal").value(2650.0))
    }

    @Test
    fun `POST api calculations pakistan should return error when package option is false`() {
        // Given
        val calculationRequest = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false // Should cause error
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/pakistan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    fun `POST api calculations caf should apply different country rules`() {
        // Test Pakistan (1.15 multiplier)
        val pakistanRequest = CalculationRequest(
            country = "Pakistan",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pakistanRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPrice").exists())

        // Test Kenya (1.18 multiplier)
        val kenyaRequest = pakistanRequest.copy(country = "Kenya")
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(kenyaRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPrice").exists())

        // Test Nigeria (1.25 multiplier)
        val nigeriaRequest = pakistanRequest.copy(country = "Nigeria")
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nigeriaRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalPrice").exists())
    }

    @Test
    fun `POST api calculations caf should handle unsupported country with default rules`() {
        // Given
        val calculationRequest = CalculationRequest(
            country = "Unknown Country",
            containerPrice = 1000.0,
            shippingCharge = 500.0,
            wcCharge = 200.0,
            inspectionFee = 100.0,
            fobPrice = 300.0,
            freightPrice = 400.0,
            insurance = 150.0,
            packageOption = false
        )

        // When & Then
        mockMvc.perform(
            post("/api/calculations/caf")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(calculationRequest))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.totalPrice").exists())
            .andExpect(jsonPath("$.breakdown.countryMultiplier").exists())
    }
}
