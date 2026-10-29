package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.PurchaseDateParseUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseRixoDateCandidateTest {

    @Autowired
    private lateinit var purchaseService: PurchaseService

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun clear() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun rixoDateLabelCandidatePattern_usesEnglishMonthDayYear() {
        val target = LocalDate.of(2026, 6, 29)
        assertEquals("June%29%2026", purchaseService.rixoDateLabelCandidatePattern(target))
        assertEquals("April%24%2026", purchaseService.rixoDateLabelCandidatePattern(LocalDate.of(2026, 4, 24)))
    }

    @Test
    fun getPurchasesForRixo_dateFilter_matchesFullCatalogPendingSemantics() {
        val target = LocalDate.of(2026, 6, 29)
        seed(
            chassis = "MATCH-LABEL",
            date = "June 29, 2026 (Monday)",
            company = "KLC",
            status = WorkflowStatus.PURCHASED,
        )
        seed(chassis = "MATCH-ISO", date = "2026-06-29", company = "KLC", status = WorkflowStatus.PURCHASED)
        seed(chassis = "OTHER-DAY", date = "July 1, 2026 (Wednesday)", company = "KLC", status = WorkflowStatus.PURCHASED)
        seed(chassis = "DONE", date = "June 29, 2026 (Monday)", company = "KLC", status = WorkflowStatus.RIXO_REQUESTED)
        seed(chassis = "NEAR-MISS", date = "June 28, 2026 (Sunday)", company = "KLC", status = WorkflowStatus.PURCHASED)

        val optimized = purchaseService.getPurchasesForRixo("2026-06-29", null, null, false)

        val baseline = purchaseRepository.findAll()
            .filter { p ->
                val parsed = PurchaseDateParseUtils.parseToLocalDate(p.date?.trim().orEmpty())
                parsed == target &&
                    (p.workflowStatus == null || p.workflowStatus == WorkflowStatus.PURCHASED)
            }
            .mapNotNull { it.id }
            .toSet()

        assertEquals(baseline, optimized.mapNotNull { it.id }.toSet())
        assertEquals(setOf("MATCH-LABEL", "MATCH-ISO"), optimized.map { it.chassis }.toSet())
    }

    @Test
    fun getPurchasesForRixo_emptyWhenNoPurchasesMatchDate() {
        seed(chassis = "X", date = "2026-01-01", company = "KLC", status = WorkflowStatus.PURCHASED)
        assertTrue(purchaseService.getPurchasesForRixo("2026-12-31", null, null, false).isEmpty())
    }

    private fun seed(
        chassis: String,
        date: String,
        company: String,
        status: WorkflowStatus?,
    ) {
        purchaseRepository.save(
            Purchase(
                chassis = chassis,
                date = date,
                rixoCompany = company,
                workflowStatus = status,
            ),
        )
    }
}
