package com.automan.backend.service

import com.automan.backend.model.CarBrandMapping
import com.automan.backend.repository.CarBrandMappingRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Recycle fee / manufacture year lookups must use exact chassis codes only.
 * Prefix LIKE (GK3 → GK30) previously leaked fees across Chassis Map rows.
 */
class CarBrandMappingRecycleFeeExactChassisTest {

    private val repository = mock(CarBrandMappingRepository::class.java)
    private val service = CarBrandMappingService(repository)

    @Test
    fun getRecycleFeeForProductionDate_emptyChassisDoesNotInheritLongerPrefixFee() {
        val gk3 = CarBrandMapping(
            id = 1L,
            carBrand = "HONDA",
            chassis = "GK3",
            recycleFee = null,
        )
        val gk30 = CarBrandMapping(
            id = 2L,
            carBrand = "HONDA",
            chassis = "GK30",
            recycleFee = "2020-01:8160",
        )
        `when`(repository.findByChassis("GK3")).thenReturn(listOf(gk3))
        `when`(repository.findByChassis("GK30")).thenReturn(listOf(gk30))

        assertNull(service.getRecycleFeeForProductionDate("GK3", "01/2020"))
        assertEquals("8160", service.getRecycleFeeForProductionDate("GK30", "01/2020"))
    }

    @Test
    fun getManufactureYearForChassisNumber_exactChassisOnly() {
        val short = CarBrandMapping(
            id = 1L,
            carBrand = "TOYOTA",
            chassis = "ACR50",
            chassisNumber = null,
        )
        val longer = CarBrandMapping(
            id = 2L,
            carBrand = "TOYOTA",
            chassis = "ACR500",
            chassisNumber = "67H:2019",
        )
        `when`(repository.findByChassis("ACR50")).thenReturn(listOf(short))
        `when`(repository.findByChassis("ACR500")).thenReturn(listOf(longer))

        assertNull(service.getManufactureYearForChassisNumber("ACR50", "67H"))
        assertEquals("2019", service.getManufactureYearForChassisNumber("ACR500", "67H"))
    }
}
