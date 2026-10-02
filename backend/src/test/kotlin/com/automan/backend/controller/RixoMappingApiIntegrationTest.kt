package com.automan.backend.controller

import com.automan.backend.repository.RixoMappingRepository
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RixoMappingApiIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var rixoMappingRepository: RixoMappingRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val companies = mutableListOf<String>()
    private val auctions = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            val ids = rixoMappingRepository.findAll()
                .filter { row ->
                    companies.any { it.equals(row.rixoCompany, ignoreCase = true) } ||
                        auctions.any { it.equals(row.auctionName, ignoreCase = true) }
                }
                .mapNotNull { it.id }
            if (ids.isNotEmpty()) rixoMappingRepository.deleteAllById(ids)
        }
        companies.clear()
        auctions.clear()
    }

    @Test
    fun fullInsertThenLookupReturnsThatPrice() {
        val company = company()
        val auction = auction()
        val stock = "P0-STOCK-${UUID.randomUUID().toString().take(8)}"

        val created = mockMvc.perform(
            post("/rixo-mapping/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(company, auction, stock, venue = "111", vehicle = "Sedan", price = "5000")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn()
        assertTrue(created.response.contentAsString.contains("\"rixoPrice\":\"5000\""))

        mockMvc.perform(
            get("/rixo-mapping/lookup")
                .param("auctionName", auction)
                .param("stockLocation", stock)
                .param("rixoCompany", company)
                .param("supportedVehicleType", "Sedan"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.rixoPrice").value("5000"))
    }

    @Test
    fun lookupFallsBackToRowWithoutVehicleType() {
        val company = company()
        val auction = auction()
        val stock = "P0-STOCK-${UUID.randomUUID().toString().take(8)}"
        mockMvc.perform(
            post("/rixo-mapping/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(company, auction, stock, venue = "111", vehicle = null, price = "4200")),
        ).andExpect(status().isOk)

        mockMvc.perform(
            get("/rixo-mapping/lookup")
                .param("auctionName", auction)
                .param("stockLocation", stock)
                .param("rixoCompany", company)
                .param("supportedVehicleType", "Truck"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.rixoPrice").value("4200"))
    }

    @Test
    fun lookupMissReturnsNoMapping() {
        mockMvc.perform(
            get("/rixo-mapping/lookup")
                .param("auctionName", auction())
                .param("stockLocation", "NO-SUCH-STOCK")
                .param("rixoCompany", company()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(containsString("No rixo mapping found")))
    }

    @Test
    fun companyAndAuctionListsStaySeparate() {
        val companyA = company()
        val companyB = company()
        val auctionA = auction()
        val auctionB = auction()
        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(companyA, auctionA, "STOCK-A", "1", "Sedan", "100")),
        ).andExpect(status().isOk)
        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(companyB, auctionB, "STOCK-B", "2", "Sedan", "200")),
        ).andExpect(status().isOk)

        mockMvc.perform(get("/rixo-mapping/by-company").param("rixoCompany", companyA))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].rixoCompany").value(companyA))
            .andExpect(jsonPath("$.data[0].auctionName").value(auctionA))

        mockMvc.perform(get("/rixo-mapping/by-auction").param("auctionName", auctionB))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].rixoCompany").value(companyB))
    }

    @Test
    fun semicolonAndMissingCompanyAndNonNumericPriceAreRejected() {
        val company = company()
        val auction = auction()
        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow("$company;OTHER", auction, "STOCK", "1", "Sedan", "100")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("Do not use")))
        assertEquals(0, rowsFor(company).size)

        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content("""{"rows":[{"auctionName":"$auction","stockLocation":"STOCK","insertMode":"FULL"}]}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("Rixo company is required")))

        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(company, auction, "STOCK", "1", "Sedan", "abc")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("Rixo price must be numeric")))
        assertEquals(0, rowsFor(company).size)
    }

    @Test
    fun secondDistinctVenueForSameSupplierIsRejected() {
        val auction = auction()
        val first = company()
        val second = company()
        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(first, auction, "STOCK-1", "111", "Sedan", "100")),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(second, auction, "STOCK-2", "222", "Sedan", "200")),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(containsString("Only one venue per supplier is allowed")))

        assertEquals(1, rixoMappingRepository.findByAuctionNameIgnoreCase(auction).size)
    }

    @Test
    fun updateKeepsOmittedPriceAndDeleteRemovesTheRow() {
        val company = company()
        val auction = auction()
        val created = mockMvc.perform(
            post("/rixo-mapping/bulk").contentType(MediaType.APPLICATION_JSON)
                .content(fullRow(company, auction, "STOCK", "111", "Sedan", "5000")),
        )
            .andExpect(status().isOk)
            .andReturn()
        val id = Regex("\"id\":(\\d+)").find(created.response.contentAsString)?.groupValues?.get(1)
            ?: error("created id")

        mockMvc.perform(
            put("/rixo-mapping/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"stockLocation":"STOCK-RENAMED"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.stockLocation").value("STOCK-RENAMED"))
            .andExpect(jsonPath("$.data.rixoPrice").value("5000"))
            .andExpect(jsonPath("$.data.rixoCompany").value(company))

        mockMvc.perform(delete("/rixo-mapping/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
        mockMvc.perform(delete("/rixo-mapping/$id"))
            .andExpect(status().isNotFound)
        assertTrue(rixoMappingRepository.findById(id.toLong()).isEmpty)
    }

    private fun company(): String {
        val value = "P0CO-${UUID.randomUUID().toString().take(8)}"
        companies.add(value)
        return value
    }

    private fun auction(): String {
        val value = "P0AUC-${UUID.randomUUID().toString().take(8)}"
        auctions.add(value)
        return value
    }

    private fun rowsFor(company: String) =
        rixoMappingRepository.findAll().filter { it.rixoCompany.equals(company, ignoreCase = true) }

    private fun fullRow(
        company: String,
        auction: String,
        stock: String,
        venue: String?,
        vehicle: String?,
        price: String?,
    ): String {
        val venueJson = if (venue == null) "null" else "\"$venue\""
        val vehicleJson = if (vehicle == null) "null" else "\"$vehicle\""
        val priceJson = if (price == null) "null" else "\"$price\""
        return """
            {"rows":[{
              "rixoCompany":"$company",
              "auctionName":"$auction",
              "stockLocation":"$stock",
              "venueId":$venueJson,
              "supportedVehicleType":$vehicleJson,
              "rixoPrice":$priceJson,
              "insertMode":"FULL"
            }]}
        """.trimIndent()
    }
}
