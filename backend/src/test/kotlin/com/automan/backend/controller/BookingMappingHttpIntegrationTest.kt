package com.automan.backend.controller

import com.automan.backend.repository.BookingMappingRepository
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
 * Consignee Map HTTP: create, update, delete, and country-token lookup.
 * A second add for the same consignee name merges into the existing row.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookingMappingHttpIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var bookingMappingRepository: BookingMappingRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }
    private val names = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        transactionTemplate.executeWithoutResult {
            names.toList().forEach { name ->
                bookingMappingRepository.findAllByConsigneeNameIgnoreCaseOrderByIdAsc(name)
                    .forEach { bookingMappingRepository.delete(it) }
            }
        }
        names.clear()
    }

    @Test
    fun addPersistsTheMappingAndCountryLookupReturnsThatTokenOnly() {
        val name = track("P0Consignee${token()}")
        val otherName = track("P0Other${token()}")
        val country = "P0BD${token()}"
        val otherCountry = "P0JP${token()}"
        val notify = "P0Notify${token()}"
        val destination = "P0Dest${token()}"

        mockMvc.perform(
            post("/booking/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, "$country;P0KE${token()}", notify, destination)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.merged").value(false))
            .andExpect(jsonPath("$.data.consigneeName").value(name))
            .andExpect(jsonPath("$.data.notifyParty").value(notify))
            .andExpect(jsonPath("$.data.finalDestination").value(destination))

        mockMvc.perform(
            post("/booking/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(otherName, otherCountry, "Other Notify", "Other Dest")),
        ).andExpect(status().isOk)

        val lookup = mockMvc.perform(get("/booking/mappings/by-country/$country"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].consigneeName").value(name))
            .andReturn().response.contentAsString
        assertFalse(lookup.contains(otherName))

        mockMvc.perform(get("/booking/mappings/notify-parties"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data", hasItem(notify)))
    }

    @Test
    fun updateChangesOnlyTheRequestedMapping() {
        val targetName = track("P0Upd${token()}")
        val otherName = track("P0Keep${token()}")
        val targetId = create(targetName, "P0C${token()}", "Before Notify")
        val otherId = create(otherName, "P0C${token()}", "Stay Notify")

        mockMvc.perform(
            put("/booking/mappings/$targetId")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(targetName, "P0C${token()}", "After Notify", "After Dest")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.merged").value(false))
            .andExpect(jsonPath("$.data.id").value(targetId))
            .andExpect(jsonPath("$.data.notifyParty").value("After Notify"))

        assertEquals("After Notify", bookingMappingRepository.findById(targetId).orElseThrow().notifyParty)
        assertEquals("Stay Notify", bookingMappingRepository.findById(otherId).orElseThrow().notifyParty)
        assertEquals(otherName, bookingMappingRepository.findById(otherId).orElseThrow().consigneeName)
    }

    @Test
    fun deleteRemovesOnlyTheRequestedMapping() {
        val targetName = track("P0Del${token()}")
        val otherName = track("P0DelKeep${token()}")
        val targetId = create(targetName, "P0C${token()}", "Gone")
        val otherId = create(otherName, "P0C${token()}", "Kept")

        mockMvc.perform(delete("/booking/mappings/$targetId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))

        assertFalse(bookingMappingRepository.existsById(targetId))
        assertTrue(bookingMappingRepository.existsById(otherId))
    }

    @Test
    fun addingTheSameConsigneeNameMergesIntoTheExistingRow() {
        val name = track("P0Merge${token()}")
        val firstCountry = "P0A${token()}"
        val secondCountry = "P0B${token()}"
        create(name, firstCountry, "Notify A")

        mockMvc.perform(
            post("/booking/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, secondCountry, "Notify B", "Dest B")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.merged").value(true))
            .andExpect(jsonPath("$.message").value("Values merged into existing consignee"))

        val rows = bookingMappingRepository.findAllByConsigneeNameIgnoreCaseOrderByIdAsc(name)
        assertEquals(1, rows.size)
        assertTrue(rows.single().country.contains(firstCountry))
        assertTrue(rows.single().country.contains(secondCountry))
    }

    private fun create(name: String, country: String, notify: String): Long {
        val json = mockMvc.perform(
            post("/booking/mappings/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(name, country, notify, "Dest")),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andReturn().response.contentAsString
        val id = Regex(""""id"\s*:\s*(\d+)""").find(json)?.groupValues?.get(1)?.toLong()
        return requireNotNull(id)
    }

    private fun body(name: String, country: String, notify: String, destination: String) =
        """{"consigneeName":"$name","country":"$country","notifyParty":"$notify","finalDestination":"$destination","consigneeAddress":"Address"}"""

    private fun track(name: String): String {
        names.add(name)
        return name
    }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
}
