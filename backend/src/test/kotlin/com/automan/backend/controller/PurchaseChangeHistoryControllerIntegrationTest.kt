package com.automan.backend.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseChangeHistoryControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET purchases change-history for unknown id returns empty json array`() {
        mockMvc.perform(get("/purchases/9223372036854775806/change-history"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `GET purchases change-history rejects non-positive id`() {
        mockMvc.perform(get("/purchases/0/change-history"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST purchases change-history page-scope returns empty when no purchase ids`() {
        mockMvc.perform(
            post("/purchases/change-history/page-scope")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"purchaseIds":[],"historyPage":0,"historySize":20}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isArray)
    }
}
