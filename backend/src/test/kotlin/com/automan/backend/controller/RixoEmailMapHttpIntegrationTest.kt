package com.automan.backend.controller

import com.automan.backend.repository.RixoEmailMapRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RixoEmailMapHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var rixoEmailMapRepository: RixoEmailMapRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val companies = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            companies.toList().forEach { company ->
                rixoEmailMapRepository.findAll()
                    .filter { it.rixoCompany.equals(company, ignoreCase = true) }
                    .forEach { rixoEmailMapRepository.delete(it) }
            }
        }
        companies.clear()
    }

    @Test
    fun oneCompanyCanHaveSeveralEmailsAndLookupIgnoresCase() {
        val company = track("KLC${token()}")
        add(company, "first@klc.example")
        add(company, "Second@KLC.example")

        mockMvc.perform(
            post("/rixo-email-map/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rixoCompany":"$company","email":"first@klc.example"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("This email is already saved for this Rixo company."))

        mockMvc.perform(get("/rixo-email-map/by-company").param("company", company.lowercase()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emails.length()").value(2))
            .andExpect(jsonPath("$.emails[0]").value("first@klc.example"))
            .andExpect(jsonPath("$.emails[1]").value("second@klc.example"))

        mockMvc.perform(get("/rixo-email-map/by-company").param("company", "nobody-${token()}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emails.length()").value(0))
    }

    @Test
    fun deleteRemovesOnlyThatEmail() {
        val company = track("RIX${token()}")
        val keep = add(company, "keep@rix.example")
        val drop = add(company, "drop@rix.example")

        mockMvc.perform(delete("/rixo-email-map/mappings/$drop"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        mockMvc.perform(get("/rixo-email-map/by-company").param("company", company))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emails.length()").value(1))
            .andExpect(jsonPath("$.emails[0]").value("keep@rix.example"))

        org.junit.jupiter.api.Assertions.assertTrue(rixoEmailMapRepository.existsById(keep))
    }

    private fun add(company: String, email: String): Long {
        val json = mockMvc.perform(
            post("/rixo-email-map/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rixoCompany":"$company","email":"$email"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong()
        return requireNotNull(id)
    }

    private fun track(company: String): String {
        companies.add(company)
        return company
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
