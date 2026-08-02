package com.automan.backend.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DashboardFiscalYearTest {
    @Test
    fun fiscalYear_august2026_isMay2026ToApr2027() {
        val (from, to) = DashboardService.currentFiscalYearBounds(LocalDate.of(2026, 8, 2))
        assertEquals(LocalDate.of(2026, 5, 1), from)
        assertEquals(LocalDate.of(2027, 4, 30), to)
        assertEquals(33, DashboardService.fiscalYearTermNumber(2026))
        assertEquals(
            "FY2026 (33rd · May 2026 - Apr 2027)",
            DashboardService.currentFiscalYearLabel(LocalDate.of(2026, 8, 2)),
        )
    }

    @Test
    fun fiscalYear_april2026_isStillPriorFy() {
        val (from, to) = DashboardService.currentFiscalYearBounds(LocalDate.of(2026, 4, 15))
        assertEquals(LocalDate.of(2025, 5, 1), from)
        assertEquals(LocalDate.of(2026, 4, 30), to)
        assertEquals(32, DashboardService.fiscalYearTermNumber(2025))
    }

    @Test
    fun fiscalYear_may1_startsNewFy() {
        val (from, to) = DashboardService.currentFiscalYearBounds(LocalDate.of(2026, 5, 1))
        assertEquals(LocalDate.of(2026, 5, 1), from)
        assertEquals(LocalDate.of(2027, 4, 30), to)
    }
}
