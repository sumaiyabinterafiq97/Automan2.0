package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseIdDateProjection
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.PurchaseDateParseUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.stream.Stream

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseDateRangeCandidateCompletenessTest {

    @Autowired
    private lateinit var purchaseService: PurchaseService

    @SpyBean
    private lateinit var purchaseRepository: PurchaseRepository

    @BeforeEach
    fun clear() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun optimizedRange_matchesBaseline_onMixedFormatFixture() {
        seedFixture()
        assertRangeEquivalence(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 30))
        assertRangeEquivalence(LocalDate.of(2026, 6, 29), LocalDate.of(2026, 6, 29))
        assertRangeEquivalence(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 24))
    }

    @Test
    fun optimizedRange_matchesBaseline_acrossYearBoundary() {
        purchaseRepository.save(Purchase(chassis = "DEC", date = "December 31, 2026 (Thursday)"))
        purchaseRepository.save(Purchase(chassis = "JAN", date = "2027-01-01"))
        purchaseRepository.save(Purchase(chassis = "SKIP", date = "November 30, 2026"))
        assertRangeEquivalence(LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15))
    }

    @Test
    fun optimizedRange_excludesInvalidIsoCalendarDate() {
        purchaseRepository.save(Purchase(chassis = "BAD-ISO", date = "2026-02-30"))
        purchaseRepository.save(Purchase(chassis = "GOOD", date = "2026-02-28"))
        assertEquals(setOf(chassisId("GOOD")), idsInRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun optimizedRange_emptyWhenNoParseableDatesInWindow() {
        purchaseRepository.save(Purchase(chassis = "OUT", date = "January 1, 2020"))
        assertEquals(emptySet<Long>(), idsInRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun optimizedRange_excludesNullBlankAndMalformed() {
        purchaseRepository.save(Purchase(chassis = "NULL", date = null))
        purchaseRepository.save(Purchase(chassis = "BLANK", date = "   "))
        purchaseRepository.save(Purchase(chassis = "BAD", date = "not-a-date"))
        purchaseRepository.save(Purchase(chassis = "IN", date = "2026-06-20"))
        assertEquals(setOf(chassisId("IN")), idsInRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
    }

    @Test
    fun optimizedRange_inclusiveBoundaries() {
        purchaseRepository.save(Purchase(chassis = "START", date = "2026-06-01"))
        purchaseRepository.save(Purchase(chassis = "END", date = "June 30, 2026 (Wednesday)"))
        purchaseRepository.save(Purchase(chassis = "BEFORE", date = "2026-05-31"))
        purchaseRepository.save(Purchase(chassis = "AFTER", date = "2026-07-01"))
        val ids = idsInRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30))
        assertEquals(setOf(chassisId("START"), chassisId("END")), ids)
    }

    @Test
    fun matchingPurchaseIdsForListFilter_dateRange_usesCandidatesNotFullCatalog() {
        purchaseRepository.save(Purchase(chassis = "IN", date = "2026-06-20"))
        purchaseRepository.save(Purchase(chassis = "OUT", date = "2020-01-01"))

        purchaseService.matchingPurchaseIdsForListFilter(
            com.automan.backend.dto.PurchasePageFilterRequest(
                dateFrom = "2026-06-15",
                dateTo = "2026-06-30",
            ),
        )

        verify(purchaseRepository, never()).findIdAndDateAll()
    }

    @ParameterizedTest
    @MethodSource("supportedLabelSamples")
    fun eachSupportedLabel_includedBySingleDayCandidateQuery(sample: LabelSample) {
        purchaseRepository.save(Purchase(chassis = "S-${sample.chassisSuffix}", date = sample.raw))
        val parsed = PurchaseDateParseUtils.parseToLocalDate(sample.raw)
        assertEquals(sample.expected, parsed)

        val candidates = purchaseRepository.findIdAndDateRixoDateCandidates(
            sample.expected.toString(),
            PurchaseDateParseUtils.sqlLabelDateCandidatePattern(sample.expected),
        )
        val ids = candidates.mapNotNull { it.getId() }.toSet()
        assertEquals(
            setOf(chassisId("S-${sample.chassisSuffix}")),
            ids,
            "SQL candidates must include parseable label: ${sample.raw}",
        )
    }

    data class LabelSample(val chassisSuffix: String, val raw: String, val expected: LocalDate)

    companion object {
        @JvmStatic
        fun supportedLabelSamples(): Stream<LabelSample> = Stream.of(
            LabelSample("iso", "2026-06-15", LocalDate.of(2026, 6, 15)),
            LabelSample("spaced", "June 15, 2026", LocalDate.of(2026, 6, 15)),
            LabelSample("nospace", "June15, 2026", LocalDate.of(2026, 6, 15)),
            LabelSample("weekday", "April24, 2026(Thursday)", LocalDate.of(2026, 4, 24)),
            LabelSample("weekday2", "July 4, 2026(Saturday)", LocalDate.of(2026, 7, 4)),
            LabelSample("trim", "  June 29, 2026 (Monday)  ", LocalDate.of(2026, 6, 29)),
        )
    }

    private fun seedFixture() {
        purchaseRepository.save(Purchase(chassis = "ISO", date = "2026-06-20"))
        purchaseRepository.save(Purchase(chassis = "LABEL", date = "June 29, 2026 (Monday)"))
        purchaseRepository.save(Purchase(chassis = "NOSPACE", date = "June15, 2026"))
        purchaseRepository.save(Purchase(chassis = "APRIL", date = "April24, 2026(Thursday)"))
        purchaseRepository.save(Purchase(chassis = "JULY", date = "July 4, 2026(Saturday)"))
        purchaseRepository.save(Purchase(chassis = "NEAR-BEFORE", date = "June 14, 2026"))
        purchaseRepository.save(Purchase(chassis = "NEAR-AFTER", date = "July 1, 2026"))
        purchaseRepository.save(Purchase(chassis = "NULL-D", date = null))
        purchaseRepository.save(Purchase(chassis = "BLANK-D", date = "  "))
        purchaseRepository.save(Purchase(chassis = "BAD-D", date = "garbage"))
    }

    private fun assertRangeEquivalence(from: LocalDate, to: LocalDate) {
        assertEquals(baselineIds(from, to), optimizedIds(from, to), "range $from..$to")
    }

    private fun baselineIds(from: LocalDate, to: LocalDate): Set<Long> =
        purchaseRepository.findIdAndDateAll()
            .filter { pair -> inInclusiveRange(pair, from, to) }
            .mapNotNull { it.getId() }
            .toSet()

    private fun optimizedIds(from: LocalDate, to: LocalDate): Set<Long> =
        purchaseService.idDatePairsMatchingInclusiveDateRange(from, to)
            .mapNotNull { it.getId() }
            .toSet()

    private fun idsInRange(from: LocalDate, to: LocalDate): Set<Long> = optimizedIds(from, to)

    private fun inInclusiveRange(pair: PurchaseIdDateProjection, from: LocalDate, to: LocalDate): Boolean {
        val parsed = PurchaseDateParseUtils.parseToLocalDate(pair.getDate()?.trim().orEmpty()) ?: return false
        return !parsed.isBefore(from) && !parsed.isAfter(to)
    }

    private fun chassisId(chassis: String): Long =
        purchaseRepository.findAll().first { it.chassis == chassis }.id!!
}
