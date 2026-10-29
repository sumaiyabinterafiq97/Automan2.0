package com.automan.backend.service

import com.automan.backend.dto.PurchasePageFilterClause
import com.automan.backend.dto.PurchasePageFilterRequest
import com.automan.backend.model.Purchase
import com.automan.backend.model.PurchaseCostLine
import com.automan.backend.repository.PurchaseCostLineRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.service.PurchaseCostLineService
import java.math.BigDecimal
import com.automan.backend.util.PurchaseDateParseUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseListDateReadOptimizationTest {

    @Autowired
    private lateinit var purchaseService: PurchaseService

    @SpyBean
    private lateinit var purchaseRepository: PurchaseRepository

    @Autowired
    private lateinit var purchaseCostLineRepository: PurchaseCostLineRepository

    @Autowired
    private lateinit var purchaseCostLineService: PurchaseCostLineService

    @BeforeEach
    fun clear() {
        purchaseRepository.deleteAll()
    }

    @Test
    fun filterPurchasesPage_dateSort_usesIdInQueryNotFullCatalog() {
        val a = save(chassis = "A", date = "2026-06-10")
        val b = save(chassis = "B", date = "2026-06-15")
        save(chassis = "OUT", date = "2026-01-01")

        val page = purchaseService.filterPurchasesPage(
            PurchasePageFilterRequest(
                page = 0,
                size = 10,
                sort = "date",
                order = "asc",
                dateFrom = "2026-06-01",
                dateTo = "2026-06-30",
            ),
        )

        assertEquals(2, page.totalElements)
        assertEquals(listOf(a.id, b.id), page.content.map { it.id })

        verify(purchaseRepository).findIdAndDateByIdIn(anyCollection())
    }

    @Test
    fun filterPurchasesPage_dateSort_tieBreaksByIdOnEqualParsedDates() {
        val early = save(chassis = "LOW-ID", date = "June 20, 2026 (Saturday)")
        val late = save(chassis = "HIGH-ID", date = "2026-06-20")

        val asc = purchaseService.filterPurchasesPage(
            PurchasePageFilterRequest(
                page = 0,
                size = 10,
                sort = "date",
                order = "asc",
                dateFrom = "2026-06-01",
                dateTo = "2026-06-30",
            ),
        )
        assertEquals(listOf(early.id, late.id), asc.content.map { it.id })

        val desc = purchaseService.filterPurchasesPage(
            PurchasePageFilterRequest(
                page = 0,
                size = 10,
                sort = "date",
                order = "desc",
                dateFrom = "2026-06-01",
                dateTo = "2026-06-30",
            ),
        )
        assertEquals(listOf(late.id, early.id), desc.content.map { it.id })
    }

    @Test
    fun dateChip_matchesIsoAndLabel_exactDayOnly() {
        save(chassis = "MATCH-LABEL", date = "June 29, 2026 (Monday)")
        save(chassis = "MATCH-ISO", date = "2026-06-29")
        save(chassis = "NEAR", date = "June 28, 2026 (Sunday)")
        save(chassis = "BLANK", date = "  ")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "date", operator = "equals", value = "2026-06-29"),
                ),
            ),
        ).toSet()

        assertEquals(setOf("MATCH-LABEL", "MATCH-ISO"), ids.map { chassisFor(it) }.toSet())
        verify(purchaseRepository, atLeastOnce()).findIdAndDateRixoDateCandidates("2026-06-29", "June%29%2026")
        verify(purchaseRepository, never()).findIdAndDateAll()
    }

    @Test
    fun dateChip_invalidLabel_returnsEmpty() {
        save(chassis = "X", date = "2026-06-29")
        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "date", operator = "equals", value = "not-a-date"),
                ),
            ),
        )
        assertTrue(ids.isEmpty())
    }

    @Test
    fun dateChip_intersectsWithOtherDbFilters() {
        save(chassis = "YES", date = "2026-06-29", brand = "TOYOTA")
        save(chassis = "WRONG-BRAND", date = "2026-06-29", brand = "HONDA")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "date", operator = "equals", value = "2026-06-29"),
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "TOYOTA"),
                ),
            ),
        )
        assertEquals(1, ids.size)
        assertEquals("YES", chassisFor(ids.single()))
    }

    @Test
    fun matchingPurchaseIdsForListFilter_exportScope_returnsAllMatchesNotPageLimited() {
        repeat(3) { i ->
            save(chassis = "EXP-$i", date = "2026-07-0${i + 1}")
        }
        save(chassis = "OUT", date = "2026-01-01")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                page = 0,
                size = 1,
                dateFrom = "2026-07-01",
                dateTo = "2026-07-31",
            ),
        )
        assertEquals(3, ids.size)
    }

    @Test
    fun dateChip_equivalentToFullCatalogBaseline() {
        save(chassis = "L", date = "June 29, 2026 (Monday)")
        save(chassis = "I", date = "2026-06-29")
        save(chassis = "N", date = "July 1, 2026 (Wednesday)")
        val target = LocalDate.of(2026, 6, 29)

        val optimized = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "date", operator = "equals", value = "2026-06-29"),
                ),
            ),
        ).toSet()

        val baseline = purchaseRepository.findIdAndDateAll()
            .filter { pair ->
                PurchaseDateParseUtils.parseToLocalDate(pair.getDate()?.trim().orEmpty()) == target
            }
            .mapNotNull { it.getId() }
            .toSet()

        assertEquals(baseline, optimized)
    }

    @Test
    fun matchingPurchaseIds_dbChipOnly_seedsFromDbFilter_notFullCatalog() {
        save(chassis = "A", date = "2026-06-01", brand = "TOYOTA")
        save(chassis = "B", date = "2026-06-02", brand = "HONDA")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "TOYOTA"),
                ),
            ),
        )

        assertEquals(1, ids.size)
        assertEquals("A", chassisFor(ids.single()))
        verify(purchaseRepository).findIdsBrandContains("TOYOTA")
        verify(purchaseRepository, never()).findAllPurchaseIds()
        verify(purchaseRepository, never()).findIdAndDateAll()
    }

    @Test
    fun matchingPurchaseIds_multipleDbFilters_intersectsInOrder() {
        save(chassis = "MATCH", date = "2026-06-01", brand = "TOYOTA")
        save(chassis = "BRAND-ONLY", date = "2026-06-01", brand = "TOYOTA")
        save(chassis = "CHASSIS-ONLY", date = "2026-06-01", brand = "HONDA")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "TOYOTA"),
                    PurchasePageFilterClause(field = "chassis", operator = "contains", value = "MATCH"),
                ),
            ),
        ).toSet()

        assertEquals(setOf("MATCH"), ids.map { chassisFor(it) }.toSet())
        verify(purchaseRepository).findIdsBrandContains("TOYOTA")
        verify(purchaseRepository).findIdsChassisContains("MATCH")
        verify(purchaseRepository, never()).findAllPurchaseIds()
    }

    @Test
    fun matchingPurchaseIds_brandAndCarNameDbFilters_intersectAfterBrandSeed() {
        val hit = purchaseRepository.save(
            save(chassis = "T1", date = "2026-06-01", brand = "TOYOTA").copy(carName = "Unique Alpha"),
        )
        purchaseRepository.save(
            save(chassis = "T2", date = "2026-06-01", brand = "TOYOTA").copy(carName = "Other"),
        )
        save(chassis = "T3", date = "2026-06-01", brand = "HONDA")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "TOYOTA"),
                    PurchasePageFilterClause(field = "carName", operator = "contains", value = "Unique"),
                ),
            ),
        ).toSet()

        assertEquals(setOf(hit.id), ids)
        verify(purchaseRepository).findIdsBrandContains("TOYOTA")
        verify(purchaseRepository).findIdsCarNameContains("Unique")
        verify(purchaseRepository, never()).findAllPurchaseIds()
    }

    @Test
    fun matchingPurchaseIds_dbFilterPlusTransient_price_narrowsInMemory() {
        val hit = saveWithPriceLine(chassis = "T-HIT", date = "2026-06-01", brand = "TOYOTA", priceAmount = BigDecimal("1888000"))
        saveWithPriceLine(chassis = "T-MISS-PRICE", date = "2026-06-01", brand = "TOYOTA", priceAmount = BigDecimal("100"))
        saveWithPriceLine(chassis = "T-MISS-BRAND", date = "2026-06-01", brand = "HONDA", priceAmount = BigDecimal("1888000"))

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "TOYOTA"),
                    PurchasePageFilterClause(field = "price", operator = "contains", value = "888"),
                ),
            ),
        ).toSet()

        assertEquals(setOf(hit.id), ids)
        verify(purchaseRepository).findIdsBrandContains("TOYOTA")
        verify(purchaseRepository, never()).findAllPurchaseIds()
    }

    @Test
    fun matchingPurchaseIds_validDateChipFirstInList_seedsDateBeforeOtherDbFilters() {
        save(chassis = "HIT", date = "2026-06-29", brand = "TOYOTA")
        save(chassis = "SAME-DAY", date = "2026-06-29", brand = "HONDA")
        save(chassis = "WRONG-DAY", date = "2026-06-28", brand = "TOYOTA")

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "TOYOTA"),
                    PurchasePageFilterClause(field = "date", operator = "equals", value = "2026-06-29"),
                ),
            ),
        ).toSet()

        assertEquals(setOf("HIT"), ids.map { chassisFor(it) }.toSet())
        verify(purchaseRepository, atLeastOnce()).findIdAndDateRixoDateCandidates("2026-06-29", "June%29%2026")
        verify(purchaseRepository).findIdsBrandContains("TOYOTA")
        verify(purchaseRepository, never()).findAllPurchaseIds()
    }

    @Test
    fun matchingPurchaseIds_transientOnly_usesFullCatalogSeed() {
        val match = saveWithPriceLine(chassis = "N1", date = "2026-01-01", priceAmount = BigDecimal("12999500"))
        saveWithPriceLine(chassis = "N2", date = "2026-01-01", priceAmount = BigDecimal("100"))

        val ids = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "price", operator = "contains", value = "999"),
                ),
            ),
        ).toSet()

        val expected = expectedIdsMatchingPriceDigitContains("999")
        assertEquals(expected, ids)
        assertEquals(setOf(match.id), ids)
        verify(purchaseRepository).findAllPurchaseIds()
    }

    @Test
    fun filterPurchasesPage_dbFilterOnly_paginationUnchanged() {
        repeat(3) { i ->
            save(chassis = "PAGE-$i", date = "2026-08-0${i + 1}", brand = "PAGEBRAND")
        }
        save(chassis = "OTHER", date = "2026-08-01", brand = "OTHER")

        val page = purchaseService.filterPurchasesPage(
            PurchasePageFilterRequest(
                page = 0,
                size = 2,
                sort = "id",
                order = "desc",
                filters = listOf(
                    PurchasePageFilterClause(field = "brand", operator = "contains", value = "PAGEBRAND"),
                ),
            ),
        )

        assertEquals(3, page.totalElements)
        assertEquals(2, page.content.size)
        verify(purchaseRepository, never()).findAllPurchaseIds()
    }

    @Test
    fun matchingPurchaseIds_dbChipOnly_sameCandidateIdsAsIdAndDateBaseline() {
        save(chassis = "ONE", date = "2026-06-10")
        purchaseRepository.save(Purchase(chassis = "TWO", date = null, brand = "TOYOTA", carName = "Test"))
        save(chassis = "THREE", date = "garbage")

        val filtered = purchaseService.matchingPurchaseIdsForListFilter(
            PurchasePageFilterRequest(
                filters = listOf(
                    PurchasePageFilterClause(field = "chassis", operator = "contains", value = "O"),
                ),
            ),
        ).toSet()

        val baselineIds = purchaseRepository.findIdAndDateAll().mapNotNull { it.getId() }.toSet()
        val expected = baselineIds.filter { id ->
            purchaseRepository.findById(id).orElseThrow().chassis!!.contains("O", ignoreCase = true)
        }.toSet()
        assertEquals(expected, filtered)
    }

    @Test
    fun idDatePairsForPurchaseIds_emptySetDoesNotQuery() {
        val page = purchaseService.filterPurchasesPage(
            PurchasePageFilterRequest(
                page = 0,
                size = 10,
                sort = "date",
                filters = listOf(
                    PurchasePageFilterClause(field = "chassis", operator = "contains", value = "NO-SUCH"),
                ),
            ),
        )
        assertEquals(0, page.totalElements)
        verify(purchaseRepository, never()).findIdAndDateByIdIn(anyCollection())
    }

    private fun save(
        chassis: String,
        date: String,
        brand: String = "TOYOTA",
    ): Purchase = purchaseRepository.save(
        Purchase(chassis = chassis, date = date, brand = brand, carName = "Test"),
    )

    private fun saveWithPriceLine(
        chassis: String,
        date: String,
        brand: String = "TOYOTA",
        priceAmount: BigDecimal,
    ): Purchase {
        val purchase = save(chassis = chassis, date = date, brand = brand)
        purchaseCostLineRepository.save(
            PurchaseCostLine(
                purchaseId = purchase.id!!,
                costCode = "PRICE",
                amount = priceAmount,
                sortOrder = 1,
            ),
        )
        return purchase
    }

    private fun chassisFor(id: Long): String =
        purchaseRepository.findById(id).orElseThrow().chassis!!

    /** Hydrated price (cost lines) then digit-contains, matching list-filter read path for `price`. */
    private fun expectedIdsMatchingPriceDigitContains(filterValue: String): Set<Long> =
        purchaseCostLineService.applyForReadBatch(purchaseRepository.findAll())
            .filter { purchaseMatchesPriceDigitContains(it, filterValue) }
            .mapNotNull { it.id }
            .toSet()

    /** Same digit-contains branch as [PurchaseService.purchaseNumericFilterMatch] for money fields. */
    private fun purchaseMatchesPriceDigitContains(p: Purchase, filterValue: String): Boolean {
        val rowDigits = p.price.orEmpty().replace(Regex("[^0-9]"), "")
        val filterDigits = filterValue.replace(Regex("[^0-9]"), "")
        if (filterDigits.isEmpty()) return false
        return rowDigits.contains(filterDigits)
    }
}
