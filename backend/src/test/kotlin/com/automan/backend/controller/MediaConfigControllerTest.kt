package com.automan.backend.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MediaConfigControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `media config returns disabled by default in test profile`() {
        mockMvc.get("/config/media")
            .andExpect {
                status { isOk() }
                jsonPath("$.r2Enabled") { value(false) }
                jsonPath("$.maxFileSizeBytes") { value(5_242_880) }
            }
    }
}
