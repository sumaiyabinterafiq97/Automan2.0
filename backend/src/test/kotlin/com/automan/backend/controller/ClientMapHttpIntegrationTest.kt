package com.automan.backend.controller

import com.automan.backend.repository.ClientMapRepository
import org.hamcrest.Matchers.hasItem
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
 * Client map names are unique. A second create for the same name merges into the existing row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientMapHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var clientMapRepository: ClientMapRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val names = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            names.toList().forEach { name ->
                clientMapRepository.findByClientNameIgnoreCase(name)?.let { clientMapRepository.delete(it) }
            }
        }
        names.clear()
    }

    @Test
    fun createUpdateAndDeleteAffectOnlyTheRequestedClient() {
        val name = track("P0Client${token()}")
        val otherName = track("P0Other${token()}")
        val id = create(name, "Bangladesh", "Consignee A")
        val otherId = create(otherName, "Japan", "Consignee B")

        mockMvc.perform(get("/client-map/mappings/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.clientName").value(name))
            .andExpect(jsonPath("$.data.country").value("Bangladesh"))

        mockMvc.perform(get("/client-map/dropdowns/client-names"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", hasItem(name)))

        val search = mockMvc.perform(
            get("/client-map/mappings/page-search")
                .param("q", name)
                .param("field", "clientName")
                .param("page", "0")
                .param("size", "20"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].clientName").value(name))
            .andReturn().response.contentAsString
        assertFalse(search.contains(otherName))

        mockMvc.perform(
            put("/client-map/mappings/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientName":"$name","country":"Kenya","consignee":"Consignee A"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.merged").value(false))
            .andExpect(jsonPath("$.data.id").value(id))
            .andExpect(jsonPath("$.data.country").value("Kenya"))

        assertEquals("Japan", clientMapRepository.findById(otherId).orElseThrow().country)

        mockMvc.perform(delete("/client-map/mappings/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertFalse(clientMapRepository.existsById(id))
        assertTrue(clientMapRepository.existsById(otherId))
    }

    @Test
    fun creatingTheSameClientNameMergesIntoTheExistingRow() {
        val name = track("P0Merge${token()}")
        create(name, "Bangladesh", "First")

        mockMvc.perform(
            post("/client-map/mappings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientName":"$name","country":"Kenya","consignee":"Second"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.merged").value(true))
            .andExpect(jsonPath("$.message").value("Values merged into existing client map"))

        val row = clientMapRepository.findByClientNameIgnoreCase(name)
        assertEquals(name, row?.clientName)
        assertTrue(row!!.country!!.contains("Bangladesh"))
        assertTrue(row.country!!.contains("Kenya"))
    }

    private fun create(name: String, country: String, consignee: String): Long {
        val json = mockMvc.perform(
            post("/client-map/mappings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientName":"$name","country":"$country","consignee":"$consignee"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.merged").value(false))
            .andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong()
        return requireNotNull(id)
    }

    private fun track(name: String): String {
        names.add(name)
        return name
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
