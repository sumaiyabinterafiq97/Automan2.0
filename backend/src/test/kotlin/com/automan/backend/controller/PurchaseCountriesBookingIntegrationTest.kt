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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PurchaseCountriesBookingIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun `GET purchases countries omits countries where every purchase is booking requested`() {
        purchase("CH-JP", "Japan", bookingRequested = false, rixoConfirmed = "1")
        purchase("CH-KR", "Korea", bookingRequested = true, rixoConfirmed = "1")

        mockMvc.perform(get("/purchases/countries"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value("Japan"))
    }

    @Test
    fun `GET purchases countries includes country if any row has booking not requested`() {
        purchase("CH-US-A", "USA", bookingRequested = true, rixoConfirmed = "1")
        purchase("CH-US-B", "USA", bookingRequested = false, rixoConfirmed = "TRUE")

        mockMvc.perform(get("/purchases/countries"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value("USA"))
    }

    @Test
    fun `GET purchases countries omits country when no Rixo-confirmed booking-eligible chassis`() {
        purchase("CH-JP", "Japan", bookingRequested = false, rixoConfirmed = "0")

        mockMvc.perform(get("/purchases/countries"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    private fun purchase(
        chassis: String,
        country: String,
        bookingRequested: Boolean,
        rixoConfirmed: String = "1",
    ): Purchase {
        val car = Purchase(
            chassis = chassis,
            carName = "Test Car",
            carModelYear = "2020",
            brand = "Toyota",
            country = country,
            color = "White",
            fuel = "Gasoline",
            price = "10000.00",
            auctionFee = "500.00",
            recycleFee = "200.00",
            roadTax = "300.00",
            totalPrice = "11000.00",
            paymentDate = LocalDate.now().toString(),
            rixoRequested = "0",
            rixoConfirmed = rixoConfirmed,
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
            bookingRequested = bookingRequested,
        )
        return purchaseRepository.save(car)
    }
}
