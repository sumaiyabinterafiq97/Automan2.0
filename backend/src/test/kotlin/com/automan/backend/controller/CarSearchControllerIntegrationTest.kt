package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@Transactional
class CarSearchControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        // Clean up test data
        purchaseRepository.deleteAll()
    }

    @Test
    fun `GET /api/cars/search should return unshipped cars with no filters`() {
        // Given - Create test cars
        val unshippedCar1 = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        val unshippedCar2 = createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)
        val shippedCar = createTestCar("CHASSIS003", "Nissan Altima", "2021", "Japan", 1L)

        // When & Then
        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].chassis").value("CHASSIS001"))
            .andExpect(jsonPath("$.data[1].chassis").value("CHASSIS002"))
    }

    @Test
    fun `GET /api/cars/search should filter cars by consignee country`() {
        // Given - Create test cars with different countries
        val japanCar = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        val koreaCar = createTestCar("CHASSIS002", "Hyundai Sonata", "2019", "Korea", null)
        val chinaCar = createTestCar("CHASSIS003", "BYD Qin", "2021", "China", null)

        // When & Then
        mockMvc.perform(get("/api/cars/search?consignee=Japan&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].chassis").value("CHASSIS001"))
            .andExpect(jsonPath("$.data[0].country").value("Japan"))
    }

    @Test
    fun `GET /api/cars/search should filter cars by port of loading`() {
        // Given - Create test cars with different ports
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null, pol = "Tokyo")
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null, pol = "Osaka")

        // When & Then
        mockMvc.perform(get("/api/cars/search?pol=Tokyo&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].chassis").value("CHASSIS001"))
    }

    @Test
    fun `GET /api/cars/search should filter cars by chassis number`() {
        // Given - Create test cars with different chassis numbers
        val car1 = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        val car2 = createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)
        val car3 = createTestCar("CHASSIS003", "Nissan Altima", "2021", "Japan", null)

        // When & Then
        mockMvc.perform(get("/api/cars/search?chassis=CHASSIS001&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].chassis").value("CHASSIS001"))
    }

    @Test
    fun `GET /api/cars/search should return empty list when no cars match criteria`() {
        // Given - Create test cars
        val car1 = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        val car2 = createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)

        // When & Then
        mockMvc.perform(get("/api/cars/search?consignee=Korea&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    fun `GET /api/cars/search should combine multiple filters`() {
        // Given - Create test cars with different attributes
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null, pol = "Tokyo")
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null, pol = "Osaka")
        createTestCar("CHASSIS003", "Hyundai Sonata", "2021", "Korea", null, pol = "Seoul")

        // When & Then
        mockMvc.perform(get("/api/cars/search?consignee=Japan&pol=Tokyo&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].chassis").value("CHASSIS001"))
            .andExpect(jsonPath("$.data[0].country").value("Japan"))
    }

    @Test
    fun `GET /api/cars/search should return all cars when unshipped=false`() {
        // Given - Create test cars (both shipped and unshipped)
        val unshippedCar = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        val shippedCar = createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", 1L)

        // When & Then
        mockMvc.perform(get("/api/cars/search?unshipped=false"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `GET /api/cars/search should handle case insensitive search`() {
        // Given - Create test cars
        val car1 = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        val car2 = createTestCar("CHASSIS002", "Honda Civic", "2019", "japan", null) // lowercase

        // When & Then
        mockMvc.perform(get("/api/cars/search?consignee=japan&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    fun `GET /api/cars/search should return cars with correct structure`() {
        // Given - Create test car
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)

        // When & Then
        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").exists())
            .andExpect(jsonPath("$.data[0].chassis").value("CHASSIS001"))
            .andExpect(jsonPath("$.data[0].carName").value("Toyota Camry"))
            .andExpect(jsonPath("$.data[0].carModelYear").value("2020"))
            .andExpect(jsonPath("$.data[0].brand").value("Toyota"))
            .andExpect(jsonPath("$.data[0].country").value("Japan"))
            .andExpect(jsonPath("$.data[0].color").value("White"))
            .andExpect(jsonPath("$.data[0].fuel").value("Gasoline"))
    }

    @Test
    fun `GET /api/cars/search should handle empty database`() {
        // When & Then
        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    @Test
    fun `GET /api/cars/search should handle invalid parameters gracefully`() {
        // Given - Create test car
        val car = createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)

        // When & Then - Test with invalid parameters
        mockMvc.perform(get("/api/cars/search?consignee=&pol=&chassis=&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(1)) // Should return all cars when filters are empty
    }

    private fun createTestCar(
        chassis: String,
        carName: String,
        carModelYear: String,
        country: String,
        bookingId: Long?,
        pol: String? = null
    ): Purchase {
        val car = Purchase(
            chassis = chassis,
            carName = carName,
            carModelYear = carModelYear,
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
            rixoConfirmed = "0",
            rixoPrice = "0.00",
            shipmentDate = LocalDate.now().toString(),
            vessel = "VESSEL001",
            pol = pol,
            shipmentCharges = "1000.00",
            freight = "800.00",
            storageCharges = "200.00",
            miscCharges = "100.00",
            inspectionFee = "150.00",
            commission = "200.00",
            repairCompany = "Test Repair",
            repairCharges = "300.00",
            notes = "Test notes",
            numberCut = "0",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            bookingId = bookingId
        )
        return purchaseRepository.save(car)
    }
}
