package com.automan.backend.controller

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.model.Purchase
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseVehicleOverrideRepository
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
class PurchaseVehicleOverrideIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var carBrandMappingRepository: CarBrandMappingRepository

    @Autowired
    private lateinit var purchaseVehicleOverrideRepository: PurchaseVehicleOverrideRepository

    @BeforeEach
    fun setUp() {
        purchaseVehicleOverrideRepository.deleteAll()
        purchaseRepository.deleteAll()
        carBrandMappingRepository.deleteAll()
    }

    @Test
    fun `POST purchase dual-writes vehicle override when fuel differs from map`() {
        carBrandMappingRepository.save(
            CarBrandMapping(
                carBrand = "TOYOTA",
                chassis = "P3MAP",
                fuel = "GASOLINE",
                grade = "G",
            ),
        )

        mockMvc.perform(
            post("/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "chassis":"P3MAP-001",
                      "brand":"TOYOTA",
                      "carName":"Test",
                      "fuel":"HYBRID",
                      "grade":"G",
                      "country":"Japan",
                      "price":"1000",
                      "totalPrice":"1000"
                    }""".trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fuel").value("HYBRID"))
            .andExpect(jsonPath("$.grade").value("G"))
            .andExpect(jsonPath("$.fuelExplicit").value("HYBRID"))
            .andExpect(jsonPath("$.gradeExplicit").value("G"))

        val saved = purchaseRepository.findByChassis("P3MAP-001").first()
        val override = purchaseVehicleOverrideRepository.findByPurchaseId(saved.id!!)
        assert(override != null)
        assert(override!!.overridesJson.contains("HYBRID"))
        // Create snapshots map-matching specs too (Quick Purchase / Add ownership).
        assert(override.overridesJson.contains("\"grade\""))
    }

    @Test
    fun `POST purchase snapshots map-matching specs onto overrides`() {
        carBrandMappingRepository.save(
            CarBrandMapping(
                carBrand = "TOYOTA",
                chassis = "P3SNAP",
                fuel = "GASOLINE",
                grade = "G",
                seat = "5",
                door = "4",
                cc = "2000",
            ),
        )

        mockMvc.perform(
            post("/purchases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{
                      "chassis":"P3SNAP-1",
                      "brand":"TOYOTA",
                      "carName":"SnapTest",
                      "fuel":"GASOLINE",
                      "grade":"G",
                      "rank":"R",
                      "color":"WHITE",
                      "seat":"5",
                      "door":"4",
                      "cc":2000,
                      "distance":"50000",
                      "country":"Japan",
                      "price":"1000",
                      "totalPrice":"1000"
                    }""".trimIndent(),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fuelExplicit").value("GASOLINE"))
            .andExpect(jsonPath("$.gradeExplicit").value("G"))
            .andExpect(jsonPath("$.colorExplicit").value("WHITE"))
            .andExpect(jsonPath("$.distance").value("50000"))

        val saved = purchaseRepository.findByChassis("P3SNAP-1").first()
        val override = purchaseVehicleOverrideRepository.findByPurchaseId(saved.id!!)
        assert(override != null)
        val json = override!!.overridesJson
        assert(json.contains("GASOLINE"))
        assert(json.contains("\"grade\""))
        assert(json.contains("WHITE"))
        assert(json.contains("50000"))
        assert(json.contains("\"rank\""))
    }

    @Test
    fun `PUT purchase updates override row when spec changes`() {
        carBrandMappingRepository.save(
            CarBrandMapping(carBrand = "HONDA", chassis = "P3PUT", fuel = "GASOLINE"),
        )
        val saved = purchaseRepository.save(
            basePurchase("P3PUT-99").copy(brand = "HONDA", fuel = "GASOLINE"),
        )

        mockMvc.perform(
            put("/purchases/${saved.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"P3PUT-99","fuel":"HYBRID","brand":"HONDA"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fuel").value("HYBRID"))

        val override = purchaseVehicleOverrideRepository.findByPurchaseId(saved.id!!)
        assert(override != null)
        assert(override!!.overridesJson.contains("HYBRID"))
    }

    @Test
    fun `GET purchase by id returns overridden fuel`() {
        val saved = purchaseRepository.save(
            basePurchase("P3READ-1").copy(fuel = "DIESEL"),
        )
        purchaseVehicleOverrideRepository.save(
            com.automan.backend.model.PurchaseVehicleOverride(
                purchaseId = saved.id!!,
                overridesJson = """{"fuel":"DIESEL"}""",
            ),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fuel").value("DIESEL"))
    }

    @Test
    fun `GET purchase returns map baseline specs when no override row`() {
        carBrandMappingRepository.save(
            CarBrandMapping(
                carBrand = "TOYOTA",
                chassis = "P3BASE-001",
                fuel = "HYBRID",
                grade = "S",
                wd = "2WD",
            ),
        )
        val saved = purchaseRepository.save(
            basePurchase("P3BASE-001").copy(
                brand = "TOYOTA",
                carName = "Test",
                fuel = null,
                grade = null,
                wd = null,
            ),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fuel").value("HYBRID"))
            .andExpect(jsonPath("$.grade").value("S"))
            .andExpect(jsonPath("$.wd").value("2WD"))
    }

    @Test
    fun `GET purchase list returns vehicle specs via read adapters`() {
        carBrandMappingRepository.save(
            CarBrandMapping(carBrand = "HONDA", chassis = "P3LIST-1", fuel = "DIESEL"),
        )
        val saved = purchaseRepository.save(
            basePurchase("P3LIST-1").copy(brand = "HONDA", fuel = null),
        )

        mockMvc.perform(get("/purchases/purchase/${saved.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.fuel").value("DIESEL"))
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
