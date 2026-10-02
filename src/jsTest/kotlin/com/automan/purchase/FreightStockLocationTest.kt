package com.automan.purchase

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Freight stock lookup reads the selected cars, then the booking list by chassis.
 * Mixed stock locations are not given a separate error by this function.
 */
class FreightStockLocationTest {

    @Test
    fun sharedStockLocationIsReturnedFromTheSelectedCars() {
        val first = js("({})")
        first.stockLocation = "KLC"
        val second = js("({})")
        second.stockLocation = "KLC"
        assertEquals("KLC", resolveStockLocationForFreight(listOf(first, second)))
    }

    @Test
    fun snakeCaseStockAndBookingListAreFallbacks() {
        val snake = js("({})")
        snake.stock_location = "AQUA"
        assertEquals("AQUA", resolveStockLocationForFreight(listOf(snake)))

        val previous = carBookingDisplayedCars
        try {
            val displayed = js("({})")
            displayed.chassis = "P0-FR-1"
            displayed.stockLocation = "NAGOYA"
            carBookingDisplayedCars = arrayOf(displayed)

            val selected = js("({})")
            selected.chassis = "P0-FR-1"
            assertEquals("NAGOYA", resolveStockLocationForFreight(listOf(selected)))
            assertEquals("", resolveStockLocationForFreight(emptyList()))
        } finally {
            carBookingDisplayedCars = previous
        }
    }
}
