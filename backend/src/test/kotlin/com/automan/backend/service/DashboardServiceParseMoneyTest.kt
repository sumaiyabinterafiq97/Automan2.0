package com.automan.backend.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DashboardServiceParseMoneyTest {
    @Test
    fun parseMoney_stripsCurrencyAndCommas() {
        assertEquals(1234567.0, DashboardService.parseMoney("¥1,234,567"), 0.001)
        assertEquals(99.5, DashboardService.parseMoney("99.5"), 0.001)
        assertEquals(0.0, DashboardService.parseMoney(null), 0.001)
        assertEquals(0.0, DashboardService.parseMoney(""), 0.001)
        assertEquals(0.0, DashboardService.parseMoney("abc"), 0.001)
    }

    @Test
    fun parseMoney_handlesSpaces() {
        assertTrue(DashboardService.parseMoney(" 12,000 ") == 12000.0)
    }
}
