package com.automan.backend.controller

import com.automan.backend.repository.CarBrandMappingRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Chassis map HTTP. Recycle fee and manufacture year use the chassis code before the hyphen,
 * and only the exact chassis row — a shorter code does not inherit a longer code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CarBrandMappingHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var carBrandMappingRepository: CarBrandMappingRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisCodes = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            chassisCodes.toList().forEach { code ->
                carBrandMappingRepository.findByChassis(code).forEach { carBrandMappingRepository.delete(it) }
            }
        }
        chassisCodes.clear()
    }

    @Test
    fun createLookupUpdateAndDeleteStayOnTheRequestedChassis() {
        val chassis = track("P0CH${token()}")
        val other = track("P0OT${token()}")
        val id = create(chassis, "HONDA", "Fit", "Petrol")
        val otherId = create(other, "TOYOTA", "Vitz", "Hybrid")

        val lookup = mockMvc.perform(get("/car-brand-mapping/chassis/$chassis"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.brand").value("HONDA"))
            .andExpect(jsonPath("$.carName").value("Fit"))
            .andReturn().response.contentAsString
        assertFalse(lookup.contains(other))

        mockMvc.perform(get("/car-brand-mapping/brand/HONDA/match").param("chassis", chassis))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.data.fuel").value("Petrol"))
            .andExpect(jsonPath("$.data.carName").value("Fit"))

        mockMvc.perform(
            put("/car-brand-mapping/mappings/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"$chassis","carBrand":"HONDA","fuel":"Diesel"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.fuel").value("Diesel"))

        assertEquals("Hybrid", carBrandMappingRepository.findById(otherId).orElseThrow().fuel)

        create(chassis, "HONDA", "Jazz", "Diesel")
        assertEquals(1, carBrandMappingRepository.findByChassis(chassis).size)
        assertEquals(1, carBrandMappingRepository.findByChassis(other).size)

        mockMvc.perform(delete("/car-brand-mapping/mappings/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertFalse(carBrandMappingRepository.existsById(id))
        assertTrue(carBrandMappingRepository.existsById(otherId))
    }

    @Test
    fun recycleFeeAndManufactureYearUseTheExactChassisCode() {
        val short = track("P0A${token()}")
        val longer = track("${short}X")
        create(short, "HONDA", "Fit", "Petrol", recycleFee = null, chassisNumber = null)
        create(longer, "HONDA", "Fit", "Petrol", recycleFee = "2020-01:8160", chassisNumber = "67H:2019")

        mockMvc.perform(
            get("/car-brand-mapping/chassis/$short-99/recycle-fee").param("productionDate", "01/2020"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.found").value(false))
            .andExpect(jsonPath("$.fee").value(""))

        mockMvc.perform(
            get("/car-brand-mapping/chassis/$longer-1/recycle-fee").param("productionDate", "01/2020"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.fee").value("8160"))

        mockMvc.perform(
            get("/car-brand-mapping/chassis/$short-67H/manufacture-year").param("chassisNumber", "67H"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.found").value(false))

        mockMvc.perform(
            get("/car-brand-mapping/chassis/$longer-67H/manufacture-year").param("chassisNumber", "67H"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.found").value(true))
            .andExpect(jsonPath("$.manufactureYear").value("2019"))
    }

    private fun create(
        chassis: String,
        brand: String,
        carName: String,
        fuel: String,
        recycleFee: String? = null,
        chassisNumber: String? = null,
    ): Long {
        val extra = buildString {
            if (recycleFee != null) append(""","recycleFee":"$recycleFee"""")
            if (chassisNumber != null) append(""","chassisNumber":"$chassisNumber"""")
        }
        val json = mockMvc.perform(
            post("/car-brand-mapping/mappings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"carBrand":"$brand","chassis":"$chassis","carName":"$carName","fuel":"$fuel"$extra}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong()
        return requireNotNull(id)
    }

    private fun track(chassis: String): String {
        chassisCodes.add(chassis)
        return chassis
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(6).uppercase()
}
