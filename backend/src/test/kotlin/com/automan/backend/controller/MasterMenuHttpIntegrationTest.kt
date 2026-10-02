package com.automan.backend.controller

import com.automan.backend.repository.MasterMenuRepository
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
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
 * Master menu values are a comma-separated set per field.
 * A duplicate value, ignoring case, is not stored twice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MasterMenuHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var masterMenuRepository: MasterMenuRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val fields = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            fields.toList().forEach { masterMenuRepository.deleteByFieldNameIgnoreCase(it) }
        }
        fields.clear()
    }

    @Test
    fun addUpdateAndDeleteChangeOnlyTheRequestedValue() {
        val field = track("p0menu${token()}")
        val other = track("p0other${token()}")

        mockMvc.perform(
            post("/master-menu/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fieldName":"$field"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasItem(field)))

        mockMvc.perform(
            post("/master-menu/fields")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fieldName":"$other"}"""),
        ).andExpect(status().isOk)

        addValue(field, "Alpha")
        addValue(field, "Beta")
        addValue(other, "Kept")

        mockMvc.perform(
            post("/master-menu/$field")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"alpha"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))

        mockMvc.perform(
            put("/master-menu/$field")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"Gamma","originalValue":"Alpha"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$", hasItem("Gamma")))
            .andExpect(jsonPath("$", hasItem("Beta")))
            .andExpect(jsonPath("$", not(hasItem("Alpha"))))

        mockMvc.perform(get("/master-menu/$other"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value("Kept"))

        mockMvc.perform(delete("/master-menu/$field").param("value", "Beta"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0]").value("Gamma"))

        mockMvc.perform(delete("/master-menu/fields/$field"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        mockMvc.perform(get("/master-menu/$field"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        mockMvc.perform(get("/master-menu/$other"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value("Kept"))
    }

    private fun addValue(field: String, value: String) {
        mockMvc.perform(
            post("/master-menu/$field")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"value":"$value"}"""),
        ).andExpect(status().isOk)
    }

    private fun track(field: String): String {
        fields.add(field)
        return field
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).lowercase()
}
