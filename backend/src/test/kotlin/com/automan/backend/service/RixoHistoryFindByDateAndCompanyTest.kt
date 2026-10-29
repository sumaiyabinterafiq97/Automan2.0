package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.model.RixoHistory
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.RixoHistoryRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Locale

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RixoHistoryFindByDateAndCompanyTest {

    @Autowired
    private lateinit var rixoHistoryService: RixoHistoryService

    @SpyBean
    private lateinit var rixoHistoryRepository: RixoHistoryRepository

    @Autowired
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun clear() {
        rixoHistoryRepository.deleteAll()
        purchaseRepository.deleteAll()
    }

    @Test
    fun repositoryLookup_matchesLegacyScan_semantics() {
        val date = LocalDate.of(2026, 8, 15)
        val older = saveHistory(date, "  Acme Corp  ", "old")
        val newer = saveHistory(date, "acme corp", "new")
        saveHistory(date, "Other", "x")
        saveHistory(LocalDate.of(2026, 8, 16), "acme corp", "wrong-day")
        saveHistory(null, "acme corp", "null-date")

        for (excludeId in listOf(0L, older.id!!)) {
            val legacy = legacyFind(date, "ACME CORP", excludeId)
            val fromRepo = rixoHistoryRepository
                .findByBuyingDateAndCompanyNormalizedExcludingIdOrderByIdDesc(
                    date,
                    "acme corp",
                    excludeId,
                )
                .firstOrNull()
            assertEquals(legacy?.id, fromRepo?.id, "excludeId=$excludeId")
        }

        assertEquals(newer.id, legacyFind(date, "ACME CORP", 0L)?.id)
    }

    @Test
    fun moveChassis_mergesIntoHighestIdRowWhenDuplicateDateAndCompany() {
        val buyingDate = LocalDate.of(2026, 9, 1)
        val token = "MOVE-TOK"
        purchaseRepository.save(
            Purchase(chassis = token, carName = "Car", workflowStatus = WorkflowStatus.RIXO_CONFIRMED),
        )
        val low = rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "Target Co", chassis = "LOW-ONLY"),
        )
        val high = rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "  target co  ", chassis = "HIGH-KEEP"),
        )
        val source = rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "Source", chassis = token),
        )

        val result = rixoHistoryService.moveChassisToCompany(source.id!!, token, "Target Co")
        assertEquals(high.id, result["destinationHistoryId"])

        val highAfter = rixoHistoryRepository.findById(high.id!!).orElseThrow()
        assertEquals("HIGH-KEEP;$token", highAfter.chassis)
        val lowAfter = rixoHistoryRepository.findById(low.id!!).orElseThrow()
        assertEquals("LOW-ONLY", lowAfter.chassis)
    }

    @Test
    fun moveChassis_doesNotLoadFullHistoryTable() {
        val buyingDate = LocalDate.of(2026, 9, 2)
        val token = "SPY-TOK"
        purchaseRepository.save(
            Purchase(chassis = token, carName = "Car", workflowStatus = WorkflowStatus.RIXO_CONFIRMED),
        )
        val dest = rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "Dest LLC", chassis = "X"),
        )
        val source = rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "Src", chassis = token),
        )
        rixoHistoryRepository.save(
            RixoHistory(buyingDate = buyingDate, rixoCompany = "Noise", chassis = "Y"),
        )

        rixoHistoryService.moveChassisToCompany(source.id!!, token, "Dest LLC")

        verify(rixoHistoryRepository, never()).findAll()
        verify(rixoHistoryRepository).findByBuyingDateAndCompanyNormalizedExcludingIdOrderByIdDesc(
            buyingDate,
            "dest llc",
            source.id!!,
        )
        assertTrue(rixoHistoryRepository.findById(dest.id!!).orElseThrow().chassis!!.contains(token))
    }

    private fun saveHistory(
        buyingDate: LocalDate?,
        company: String,
        chassis: String,
    ): RixoHistory = rixoHistoryRepository.save(
        RixoHistory(buyingDate = buyingDate, rixoCompany = company, chassis = chassis),
    )

    private fun legacyFind(
        buyingDate: LocalDate?,
        company: String,
        excludeId: Long,
    ): RixoHistory? {
        val want = company.trim().lowercase(Locale.ROOT)
        return rixoHistoryRepository.findAll()
            .asSequence()
            .filter { it.id != null && it.id != excludeId }
            .filter { it.buyingDate == buyingDate }
            .filter { it.rixoCompany?.trim()?.lowercase(Locale.ROOT) == want }
            .maxByOrNull { it.id ?: 0L }
    }
}
