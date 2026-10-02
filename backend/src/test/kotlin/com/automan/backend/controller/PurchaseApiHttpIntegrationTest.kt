package com.automan.backend.controller

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
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
 * Purchase HTTP boundaries that are not already covered by service or workflow tests:
 * update/delete isolation, duplicate-chassis conflict, list search/filter, and booking stock filter.
 * Not @Transactional: a 409 rolls back only the service transaction, and the fixtures must stay readable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseApiHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purchaseRepository: PurchaseRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val chassisToDelete = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            chassisToDelete.toList().forEach { chassis ->
                purchaseRepository.findByChassisIgnoreCaseTrim(chassis).forEach { purchaseRepository.delete(it) }
            }
        }
        chassisToDelete.clear()
    }

    @Test
    fun updatePersistsTheRequestedPurchaseAndLeavesAnotherUntouched() {
        val target = save("P0UPD", carName = "Before Name", clientName = "Client A")
        val other = save("P0UPD", carName = "Other Name", clientName = "Client B")

        mockMvc.perform(
            put("/purchases/${target.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"carName":"After Name"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(target.id))
            .andExpect(jsonPath("$.carName").value("After Name"))
            .andExpect(jsonPath("$.chassis").value(target.chassis))

        assertEquals("After Name", purchaseRepository.findById(target.id!!).orElseThrow().carName)
        assertEquals("Other Name", purchaseRepository.findById(other.id!!).orElseThrow().carName)
    }

    @Test
    fun updateToAnExistingFullChassisConflictsAndChangesNeitherRow() {
        val kept = save("P0DUP", chassisSuffix = "1001")
        val edited = save("P0DUP", chassisSuffix = "2002", carName = "Stay Put")

        mockMvc.perform(
            put("/purchases/${edited.id}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"chassis":"${kept.chassis}"}"""),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("the chassis number already exist"))

        assertEquals(kept.chassis, purchaseRepository.findById(kept.id!!).orElseThrow().chassis)
        assertEquals(edited.chassis, purchaseRepository.findById(edited.id!!).orElseThrow().chassis)
        assertEquals("Stay Put", purchaseRepository.findById(edited.id!!).orElseThrow().carName)
        assertEquals(1, purchaseRepository.findByChassisIgnoreCaseTrim(kept.chassis).size)
    }

    @Test
    fun deleteRemovesOnlyTheRequestedPurchase() {
        val target = save("P0DEL")
        val other = save("P0DEL")

        mockMvc.perform(delete("/purchases/${target.id}"))
            .andExpect(status().isOk)

        assertFalse(purchaseRepository.existsById(target.id!!))
        assertTrue(purchaseRepository.existsById(other.id!!))

        mockMvc.perform(delete("/purchases/${target.id}"))
            .andExpect(status().isNotFound)
        assertTrue(purchaseRepository.existsById(other.id!!))
    }

    @Test
    fun checkDuplicateReportsAnExistingFullChassisAndExcludesThatRow() {
        val existing = save("P0CHK", chassisSuffix = "4455")

        mockMvc.perform(get("/purchases/check-duplicate").param("chassis", existing.chassis))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicate").value(true))
            .andExpect(jsonPath("$.message").value("the chassis number already exist"))
            .andExpect(jsonPath("$.existingPurchaseId").value(existing.id))

        mockMvc.perform(
            get("/purchases/check-duplicate")
                .param("chassis", existing.chassis)
                .param("excludeId", existing.id.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicate").value(false))

        mockMvc.perform(get("/purchases/check-duplicate").param("chassis", "P0CHK-NO-SUCH-${token()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.duplicate").value(false))
    }

    @Test
    fun pageSearchByChassisPrefixReturnsTheMatchAndNotAnotherPurchase() {
        val match = save("P0SRCH", carName = "Search Match")
        val other = save("P0SRCH", carName = "Search Other")

        val body = mockMvc.perform(
            get("/purchases/page-search")
                .param("q", match.chassis)
                .param("field", "chassis")
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.content[0].chassis").value(match.chassis))
            .andReturn().response.contentAsString

        assertFalse(body.contains(other.chassis))
    }

    @Test
    fun pageFilterByClientNameReturnsThatClientAndNotAnother() {
        val client = "P0Client${token()}"
        val match = save("P0FLT", clientName = client, carName = "Filtered")
        val other = save("P0FLT", clientName = "P0Other${token()}", carName = "Hidden")

        val body = mockMvc.perform(
            post("/purchases/page-filter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"page":0,"size":20,"filters":[{"field":"clientName","operator":"contains","value":"$client"}]}""",
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(match.id))
            .andExpect(jsonPath("$.content[0].clientName").value(client))
            .andReturn().response.contentAsString

        assertFalse(body.contains(other.chassis))
    }

    @Test
    fun pageSearchAndPageRejectInvalidQuery() {
        mockMvc.perform(get("/purchases/page-search").param("q", "   "))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Search text is required"))

        mockMvc.perform(
            get("/purchases/page-search")
                .param("q", "toyota")
                .param("field", "not-a-field"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Invalid search field")))

        mockMvc.perform(get("/purchases/page").param("dateFrom", "13-13-13"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Invalid dateFrom")))
    }

    @Test
    fun filteredPurchasesByStocksReturnOnlyTheEligibleStock() {
        val country = "P0CTY${token()}"
        val stock = "P0STK${token()}"
        val eligible = save(
            "P0STK",
            country = country,
            stockLocation = stock,
            workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
        )
        val otherStock = save(
            "P0STK",
            country = country,
            stockLocation = "P0OTHER${token()}",
            workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
        )
        val otherCountry = save(
            "P0STK",
            country = "P0ELSE${token()}",
            stockLocation = stock,
            workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
        )
        val alreadyBooked = save(
            "P0STK",
            country = country,
            stockLocation = stock,
            workflowStatus = WorkflowStatus.BOOKING_REQUESTED,
        )
        val notConfirmed = save(
            "P0STK",
            country = country,
            stockLocation = stock,
            workflowStatus = WorkflowStatus.PURCHASED,
        )
        val localSale = save(
            "P0STK",
            country = country,
            stockLocation = stock,
            workflowStatus = WorkflowStatus.RIXO_CONFIRMED,
            local = true,
        )

        val body = mockMvc.perform(
            get("/purchases/filtered-purchases-by-stocks")
                .param("country", country)
                .param("stockLocations", stock),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].chassis").value(eligible.chassis))
            .andReturn().response.contentAsString

        assertFalse(body.contains(otherStock.chassis))
        assertFalse(body.contains(otherCountry.chassis))
        assertFalse(body.contains(alreadyBooked.chassis))
        assertFalse(body.contains(notConfirmed.chassis))
        assertFalse(body.contains(localSale.chassis))
    }

    private fun save(
        prefix: String,
        chassisSuffix: String = token(),
        carName: String = "P0 Car",
        clientName: String = "P0 Client",
        country: String = "Japan",
        stockLocation: String? = null,
        workflowStatus: WorkflowStatus? = WorkflowStatus.PURCHASED,
        local: Boolean = false,
    ): Purchase {
        val chassis = "$prefix-$chassisSuffix"
        chassisToDelete.add(chassis)
        return purchaseRepository.save(
            Purchase(
                chassis = chassis,
                carName = carName,
                clientName = clientName,
                country = country,
                stockLocation = stockLocation,
                workflowStatus = workflowStatus,
                local = local,
            ),
        )
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
