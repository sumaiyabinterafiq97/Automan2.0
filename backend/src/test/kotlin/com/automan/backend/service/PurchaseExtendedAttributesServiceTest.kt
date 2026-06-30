package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseExtendedAttributesServiceTest {

    private val repository = mock(PurchaseRepository::class.java)
    private val objectMapper = ObjectMapper()
    private val service = PurchaseExtendedAttributesService(repository, objectMapper)

    @Test
    fun syncFromPurchase_writes_extended_attributes_json() {
        val purchase = Purchase(
            id = 1L,
            chassis = "EXT-1",
            notes = "QA note",
            venueId = "VENUE-A",
            shaken = true,
            negotiate = false,
        )
        `when`(repository.save(org.mockito.ArgumentMatchers.any(Purchase::class.java))).thenAnswer { it.arguments[0] }

        service.syncFromPurchase(purchase)

        val captor = ArgumentCaptor.forClass(Purchase::class.java)
        verify(repository).save(captor.capture())
        val json = captor.value.extendedAttributesJson!!
        assertTrue(json.contains("QA note"))
        assertTrue(json.contains("VENUE-A"))
        assertTrue(json.contains("\"shaken\":true"))
        assertTrue(json.contains("\"negotiate\":false"))
    }

    @Test
    fun applyForRead_prefers_json_over_columns() {
        val purchase = Purchase(
            id = 2L,
            chassis = "EXT-2",
            notes = "column note",
            extendedAttributesJson = """{"notes":"json note","numberCut":"5"}""",
        )

        val merged = service.applyForRead(purchase)

        assertEquals("json note", merged.notes)
        assertEquals("5", merged.numberCut)
    }

    @Test
    fun applyForRead_parses_boolean_flags() {
        val purchase = Purchase(
            id = 3L,
            chassis = "EXT-3",
            shaken = false,
            negotiate = false,
            extendedAttributesJson = """{"shaken":true,"isPackageMode":true}""",
        )

        val merged = service.applyForRead(purchase)

        assertEquals(true, merged.shaken)
        assertEquals(true, merged.isPackageMode)
    }

    @Test
    fun syncFromPurchase_clears_json_when_no_extended_fields() {
        val purchase = Purchase(id = 4L, chassis = "EXT-4", shaken = null, negotiate = null)
        `when`(repository.save(org.mockito.ArgumentMatchers.any(Purchase::class.java))).thenAnswer { it.arguments[0] }

        service.syncFromPurchase(purchase)

        val captor = ArgumentCaptor.forClass(Purchase::class.java)
        verify(repository).save(captor.capture())
        assertEquals(null, captor.value.extendedAttributesJson)
    }
}
