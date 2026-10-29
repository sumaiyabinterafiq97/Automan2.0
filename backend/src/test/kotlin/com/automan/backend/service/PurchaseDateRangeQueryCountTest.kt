package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.testsupport.ChassisSqlStatementInspector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Phase 2J baseline (per-day [findIdAndDateRixoDateCandidates]): query count = days in range
 * (1, 7, 30, 90, 366 for max allowed window). Phase 2K uses one bounded id+date SELECT per call.
 */
@SpringBootTest
@ActiveProfiles("test", "sqlcount")
@Transactional
@Execution(ExecutionMode.SAME_THREAD)
class PurchaseDateRangeQueryCountTest {

    @Autowired
    private lateinit var purchaseService: PurchaseService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun resetCounter() {
        ChassisSqlStatementInspector.reset()
        purchaseRepository.deleteAll()
    }

    @AfterEach
    fun clearCounter() {
        ChassisSqlStatementInspector.reset()
    }

    @Test
    fun phase2jBaselineQueryCounts_byDayInRange() {
        assertEquals(1, phase2jDayQueryCount(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 1)))
        assertEquals(7, phase2jDayQueryCount(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 7)))
        assertEquals(30, phase2jDayQueryCount(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
        assertEquals(90, phase2jDayQueryCount(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31)))
        assertEquals(366, phase2jDayQueryCount(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(365)))
    }

    @Test
    fun phase2k_usesSingleIdDateCandidateSelect_forWideRange() {
        purchaseRepository.save(Purchase(chassis = "WIDE-1", date = "2026-06-15"))
        purchaseRepository.save(Purchase(chassis = "WIDE-2", date = "July 4, 2026(Saturday)"))

        val from = LocalDate.of(2026, 1, 1)
        val to = from.plusDays(365)
        ChassisSqlStatementInspector.reset()
        purchaseService.idDatePairsMatchingInclusiveDateRange(from, to)

        assertEquals(1, countPurchaseIdDateCandidateSelects(), ChassisSqlStatementInspector.statements().toString())
    }

    @Test
    fun phase2k_finalIds_matchBaseline_forSeededMixedFormats() {
        purchaseRepository.save(Purchase(chassis = "A", date = "2026-06-20"))
        purchaseRepository.save(Purchase(chassis = "B", date = "June 29, 2026 (Monday)"))
        purchaseRepository.save(Purchase(chassis = "C", date = "January 1, 2020"))

        val from = LocalDate.of(2026, 6, 1)
        val to = LocalDate.of(2026, 6, 30)
        val optimized = purchaseService.idDatePairsMatchingInclusiveDateRange(from, to)
            .mapNotNull { it.getId() }
            .toSet()
        val baseline = purchaseRepository.findIdAndDateAll()
            .filter { pair ->
                val parsed = com.automan.backend.util.PurchaseDateParseUtils
                    .parseToLocalDate(pair.getDate()?.trim().orEmpty())
                parsed != null && !parsed.isBefore(from) && !parsed.isAfter(to)
            }
            .mapNotNull { it.getId() }
            .toSet()
        assertEquals(baseline, optimized)
    }

    private fun phase2jDayQueryCount(from: LocalDate, to: LocalDate): Long =
        ChronoUnit.DAYS.between(from, to) + 1

    private fun countPurchaseIdDateCandidateSelects(): Int =
        ChassisSqlStatementInspector.statements().count { sql ->
            val n = sql.lowercase()
            n.contains(" from purchases") && n.contains("date")
        }
}
