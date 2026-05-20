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
class CarSearchControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun setUp() {
        purchaseRepository.deleteAll()
    }

    /** API returns a JSON array of [com.automan.backend.model.dto.CarInfo] at the root (no success/data wrapper). */
    @Test
    fun `GET api cars search should return unshipped cars with no filters`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)
        createTestCar("CHASSIS003", "Nissan Altima", "2021", "Japan", 1L)

        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.chassis == 'CHASSIS001')]").exists())
            .andExpect(jsonPath("$[?(@.chassis == 'CHASSIS002')]").exists())
    }

    @Test
    fun `GET api cars search should filter cars by consignee country`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        createTestCar("CHASSIS002", "Hyundai Sonata", "2019", "Korea", null)
        createTestCar("CHASSIS003", "BYD Qin", "2021", "China", null)

        mockMvc.perform(get("/api/cars/search?consignee=Japan&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].chassis").value("CHASSIS001"))
    }

    @Test
    fun `GET api cars search should filter cars by port of loading`() {
        createTestCar(
            "CHASSIS001",
            "Toyota Camry",
            "2020",
            "Japan",
            null,
            polPortStockLocation = "Tokyo",
        )
        createTestCar(
            "CHASSIS002",
            "Honda Civic",
            "2019",
            "Japan",
            null,
            polPortStockLocation = "Osaka",
        )

        mockMvc.perform(get("/api/cars/search?pol=Tokyo&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].chassis").value("CHASSIS001"))
    }

    @Test
    fun `GET api cars search should filter cars by chassis number`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)
        createTestCar("CHASSIS003", "Nissan Altima", "2021", "Japan", null)

        mockMvc.perform(get("/api/cars/search?chassis=CHASSIS001&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].chassis").value("CHASSIS001"))
    }

    @Test
    fun `GET api cars search should return empty list when no cars match criteria`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)

        mockMvc.perform(get("/api/cars/search?consignee=Korea&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET api cars search should combine multiple filters`() {
        createTestCar(
            "CHASSIS001",
            "Toyota Camry",
            "2020",
            "Japan",
            null,
            polPortStockLocation = "Tokyo",
        )
        createTestCar(
            "CHASSIS002",
            "Honda Civic",
            "2019",
            "Japan",
            null,
            polPortStockLocation = "Osaka",
        )
        createTestCar(
            "CHASSIS003",
            "Hyundai Sonata",
            "2021",
            "Korea",
            null,
            polPortStockLocation = "Seoul",
        )

        mockMvc.perform(get("/api/cars/search?consignee=Japan&pol=Tokyo&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].chassis").value("CHASSIS001"))
    }

    @Test
    fun `GET api cars search with unshipped false returns empty list`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", 1L)

        mockMvc.perform(get("/api/cars/search?unshipped=false"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET api cars search should match country when param matches stored country`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)
        createTestCar("CHASSIS002", "Honda Civic", "2019", "Japan", null)

        mockMvc.perform(get("/api/cars/search?consignee=Japan&unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET api cars search should return cars with CarInfo structure`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)

        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].chassis").value("CHASSIS001"))
            .andExpect(jsonPath("$[0].carName").value("Toyota Camry"))
            .andExpect(jsonPath("$[0].carModelYear").value("2020"))
            .andExpect(jsonPath("$[0].brand").value("Toyota"))
    }

    @Test
    fun `GET api cars search should handle empty database`() {
        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `GET api cars search with only unshipped true lists all unshipped`() {
        createTestCar("CHASSIS001", "Toyota Camry", "2020", "Japan", null)

        mockMvc.perform(get("/api/cars/search?unshipped=true"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
    }

    private fun createTestCar(
        chassis: String,
        carName: String,
        carModelYear: String,
        country: String,
        bookingId: Long?,
        /** [CarSearchService] POL filter maps to [Purchase.stockLocation] in queries. */
        polPortStockLocation: String? = null,
    ): Purchase {
        val brandFromName = carName.split(" ").firstOrNull()?.trim().orEmpty().ifEmpty { "Toyota" }
        val car = Purchase(
            chassis = chassis,
            carName = carName,
            carModelYear = carModelYear,
            brand = brandFromName,
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
            pol = polPortStockLocation,
            stockLocation = polPortStockLocation,
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
            bookingId = bookingId,
        )
        return purchaseRepository.save(car)
    }
}
