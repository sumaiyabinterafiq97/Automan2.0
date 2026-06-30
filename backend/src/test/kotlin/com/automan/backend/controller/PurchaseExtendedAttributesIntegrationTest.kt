package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseExtendedAttributesIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `POST purchase dual-writes extended_attributes for cold fields`() {
        mockMvc.perform(
            post("/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "chassis":"EXT-QA-001",
                      "carName":"Test",
                      "country":"Japan",
                      "price":"1000",
                      "totalPrice":"1000",
                      "notes":"Phase4 QA note",
                      "venueId":"RIXO-V1",
                      "numberCut":"3",
                      "shaken":true,
                      "negotiate":false,
                      "isPackageMode":true
                    }""".trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notes").value("Phase4 QA note"))
            .andExpect(jsonPath("$.venueId").value("RIXO-V1"))
            .andExpect(jsonPath("$.shaken").value(true))
            .andExpect(jsonPath("$.isPackageMode").value(true))

        val saved = purchaseRepository.findByChassis("EXT-QA-001").first()
        assert(saved.extendedAttributesJson?.contains("Phase4 QA note") == true)
        assert(saved.extendedAttributesJson?.contains("RIXO-V1") == true)
    }

    @Test
    fun `PUT purchase updates extended_attributes json`() {
        val saved = purchaseRepository.save(
            basePurchase("EXT-PUT-1").copy(
                extendedAttributesJson = """{"notes":"before","venueId":"OLD"}""",
            ),
        )

        mockMvc.perform(
            put("/purchases/${saved.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"EXT-PUT-1","notes":"after update","venueId":"NEW"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notes").value("after update"))
            .andExpect(jsonPath("$.venueId").value("NEW"))

        val refreshed = purchaseRepository.findById(saved.id!!).orElseThrow()
        assert(refreshed.extendedAttributesJson?.contains("after update") == true)
    }

    @Test
    fun `GET purchase applies extended_attributes on read`() {
        val saved = purchaseRepository.save(
            basePurchase("EXT-READ-1").copy(
                extendedAttributesJson = """{"notes":"from json","paymentDate":"2026-06-28"}""",
            ),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.notes").value("from json"))
            .andExpect(jsonPath("$.paymentDate").value("2026-06-28"))
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
        rixoConfirmed = "1",
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
        shaken = false,
        negotiate = false,
    )
}
