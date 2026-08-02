package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseVehicleOverride
import com.automan.backend.repository.CarBrandMappingRepository
import com.automan.backend.repository.PurchaseVehicleOverrideRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseVehicleOverrideServiceTest {

    private val overrideRepository = mock(PurchaseVehicleOverrideRepository::class.java)
    private val mappingRepository = mock(CarBrandMappingRepository::class.java)
    private val objectMapper = ObjectMapper()
    private val service = PurchaseVehicleOverrideService(overrideRepository, mappingRepository, objectMapper)

    @Test
    fun syncFromPurchase_stores_fuel_delta_vs_map_baseline() {
        val mapping = CarBrandMapping(
            id = 1L,
            carBrand = "TOYOTA",
            chassis = "ZZE124",
            fuel = "GASOLINE",
            grade = "G",
        )
        `when`(mappingRepository.findByChassis("ZZE124-ABC")).thenReturn(emptyList())
        `when`(mappingRepository.findBestPrefixMatchForChassis("ZZE124-ABC")).thenReturn(listOf(mapping))
        `when`(overrideRepository.findByPurchaseId(5L)).thenReturn(null)

        val purchase = Purchase(
            id = 5L,
            chassis = "ZZE124-ABC",
            brand = "TOYOTA",
            fuel = "HYBRID",
            grade = "G",
        )
        service.syncFromPurchase(purchase)

        verify(overrideRepository).save(any(PurchaseVehicleOverride::class.java))
    }

    @Test
    fun syncFromPurchase_snapshotSpecs_stores_values_equal_to_map_baseline() {
        val mapping = CarBrandMapping(
            id = 1L,
            carBrand = "TOYOTA",
            chassis = "SNAP01",
            fuel = "GASOLINE",
            grade = "G",
        )
        `when`(mappingRepository.findByChassis("SNAP01-1")).thenReturn(emptyList())
        `when`(mappingRepository.findBestPrefixMatchForChassis("SNAP01-1")).thenReturn(listOf(mapping))
        `when`(overrideRepository.findByPurchaseId(50L)).thenReturn(null)

        val purchase = Purchase(
            id = 50L,
            chassis = "SNAP01-1",
            brand = "TOYOTA",
            fuel = "GASOLINE",
            grade = "G",
            color = "WHITE",
            distance = "12000",
        )
        service.syncFromPurchase(purchase, snapshotSpecs = true)

        verify(overrideRepository).save(any(PurchaseVehicleOverride::class.java))
    }

    @Test
    fun syncFromPurchase_deletes_row_when_all_specs_match_map() {
        val mapping = CarBrandMapping(
            id = 1L,
            carBrand = "TOYOTA",
            chassis = "ACR50",
            fuel = "GASOLINE",
        )
        `when`(mappingRepository.findByChassis("ACR50")).thenReturn(listOf(mapping))
        `when`(overrideRepository.findByPurchaseId(6L)).thenReturn(
            PurchaseVehicleOverride(purchaseId = 6L, overridesJson = """{"fuel":"GASOLINE"}"""),
        )

        val purchase = Purchase(id = 6L, chassis = "ACR50", fuel = "GASOLINE")
        service.syncFromPurchase(purchase)

        verify(overrideRepository).delete(any(PurchaseVehicleOverride::class.java))
    }

    @Test
    fun syncFromPurchase_stores_distance_without_map_baseline() {
        `when`(mappingRepository.findByChassis("NOMAP-1")).thenReturn(emptyList())
        `when`(mappingRepository.findBestPrefixMatchForChassis("NOMAP-1")).thenReturn(emptyList())
        `when`(overrideRepository.findByPurchaseId(7L)).thenReturn(null)

        service.syncFromPurchase(Purchase(id = 7L, chassis = "NOMAP-1", distance = "120000"))

        verify(overrideRepository).save(any(PurchaseVehicleOverride::class.java))
    }

    @Test
    fun applyForRead_applies_stored_override_values() {
        `when`(overrideRepository.findByPurchaseId(8L)).thenReturn(
            PurchaseVehicleOverride(
                purchaseId = 8L,
                overridesJson = """{"fuel":"DIESEL","grade":"S"}""",
            ),
        )

        val merged = service.applyForRead(
            Purchase(id = 8L, chassis = "X-1", fuel = "GASOLINE", grade = "G"),
        )

        assertEquals("DIESEL", merged.fuel)
        assertEquals("S", merged.grade)
    }

    @Test
    fun applyForRead_fills_map_baseline_when_no_override() {
        val mapping = CarBrandMapping(
            id = 1L,
            carBrand = "TOYOTA",
            chassis = "MAP-ONLY",
            fuel = "GASOLINE",
            grade = "G",
            wd = "4WD",
        )
        `when`(mappingRepository.findByChassis("MAP-ONLY-1")).thenReturn(emptyList())
        `when`(mappingRepository.findBestPrefixMatchForChassis("MAP-ONLY-1")).thenReturn(listOf(mapping))
        `when`(overrideRepository.findByPurchaseId(9L)).thenReturn(null)

        val merged = service.applyForRead(
            Purchase(id = 9L, chassis = "MAP-ONLY-1", brand = "TOYOTA"),
        )

        assertEquals("GASOLINE", merged.fuel)
        assertEquals("G", merged.grade)
        assertEquals("4WD", merged.wd)
    }

    @Test
    fun syncFromPurchase_preserves_carModelYear_when_field_null_on_write() {
        `when`(mappingRepository.findByChassis("AAHP45W")).thenReturn(emptyList())
        `when`(mappingRepository.findBestPrefixMatchForChassis("AAHP45W")).thenReturn(emptyList())
        `when`(overrideRepository.findByPurchaseId(10L)).thenReturn(
            PurchaseVehicleOverride(
                purchaseId = 10L,
                overridesJson = """{"carModelYear":"2026-05"}""",
            ),
        )

        // Cost-only style write: Transient carModelYear is null on raw entity
        service.syncFromPurchase(Purchase(id = 10L, chassis = "AAHP45W", price = "100000"))

        val captor = org.mockito.ArgumentCaptor.forClass(PurchaseVehicleOverride::class.java)
        verify(overrideRepository).save(captor.capture())
        val savedJson = captor.value.overridesJson
        assertTrue(savedJson.contains("2026-05"), "expected carModelYear preserved, got: $savedJson")
    }

    @Test
    fun syncFromPurchase_clears_carModelYear_when_explicitly_blank() {
        `when`(mappingRepository.findByChassis("AAHP45W")).thenReturn(emptyList())
        `when`(mappingRepository.findBestPrefixMatchForChassis("AAHP45W")).thenReturn(emptyList())
        `when`(overrideRepository.findByPurchaseId(11L)).thenReturn(
            PurchaseVehicleOverride(
                purchaseId = 11L,
                overridesJson = """{"carModelYear":"2026-05"}""",
            ),
        )

        service.syncFromPurchase(Purchase(id = 11L, chassis = "AAHP45W", carModelYear = ""))

        verify(overrideRepository).delete(any(PurchaseVehicleOverride::class.java))
        verify(overrideRepository, never()).save(any(PurchaseVehicleOverride::class.java))
    }

    @Test
    fun normalize_ignores_case_and_whitespace() {
        assertTrue(PurchaseVehicleOverrideService.normalize(" Gasoline ") == "gasoline")
        assertFalse(PurchaseVehicleOverrideService.normalize("GASOLINE") == "gasoline ")
    }
}
