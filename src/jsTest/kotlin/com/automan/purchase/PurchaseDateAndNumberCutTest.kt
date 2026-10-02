package com.automan.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Purchase-date masks and registration-year rules that do not touch the DOM.
 * [validateCarModelYearField] reads input elements, so the rule it applies is
 * [validateProductionDateYear].
 */
class PurchaseDateAndNumberCutTest {

    @Test
    fun maskMmDdYyyyFromDigitsInsertsSlashesAndStopsAtEightDigits() {
        assertEquals("", maskMmDdYyyyFromDigits(""))
        assertEquals("0", maskMmDdYyyyFromDigits("0"))
        assertEquals("01/", maskMmDdYyyyFromDigits("01"))
        assertEquals("01/0", maskMmDdYyyyFromDigits("010"))
        assertEquals("01/02/", maskMmDdYyyyFromDigits("0102"))
        assertEquals("01/02/2026", maskMmDdYyyyFromDigits("01022026"))
        assertEquals("01/02/2026", maskMmDdYyyyFromDigits("01/02/2026extra"))
    }

    @Test
    fun strictMmDdYyyyAcceptsRealDatesAndRejectsImpossibleOnes() {
        assertEquals("2026-06-15", strictMmDdYyyySlashToIso("06/15/2026"))
        assertEquals("2024-02-29", strictMmDdYyyySlashToIso("02/29/2024"))
        assertNull(strictMmDdYyyySlashToIso("02/29/2023"))
        assertNull(strictMmDdYyyySlashToIso("02/30/2024"))
        assertNull(strictMmDdYyyySlashToIso("13/01/2026"))
        assertNull(strictMmDdYyyySlashToIso("06/31/2026"))
        assertNull(strictMmDdYyyySlashToIso("6/15/2026"))
        assertNull(strictMmDdYyyySlashToIso("not-a-date"))
        assertNull(strictMmDdYyyySlashToIso("06/15/1899"))
    }

    @Test
    fun strictMmYyyyAcceptsMonthZeroThroughTwelve() {
        assertEquals("2026-07", strictMmYyyySlashToIsoMonth("07/2026"))
        assertEquals("2026-00", strictMmYyyySlashToIsoMonth("00/2026"))
        assertNull(strictMmYyyySlashToIsoMonth("13/2026"))
        assertNull(strictMmYyyySlashToIsoMonth("7/2026"))
        assertNull(strictMmYyyySlashToIsoMonth("2026"))
    }

    @Test
    fun normalizeCarModelYearForCompareUsesCanonicalYearMonth() {
        assertEquals("", normalizeCarModelYearForCompare(null))
        assertEquals("", normalizeCarModelYearForCompare("  "))
        assertEquals("2026-07", normalizeCarModelYearForCompare("07/2026"))
        assertEquals("2026-07", normalizeCarModelYearForCompare("2026-07"))
        assertEquals("2021-06", normalizeCarModelYearForCompare("June 2021"))
        assertEquals("", normalizeCarModelYearForCompare("2026-13"))
    }

    @Test
    fun validateProductionDateYearAllowsBlankAndMonthZero() {
        assertTrue(validateProductionDateYear("").first)
        assertTrue(validateProductionDateYear("2026-07").first)
        assertTrue(validateProductionDateYear("2026-00").first)
        assertEquals(false, validateProductionDateYear("2026-13").first)
        assertEquals(false, validateProductionDateYear("1899-01").first)
        assertEquals(false, validateProductionDateYear("July 2026").first)
    }

    @Test
    fun parseNumberCutPartsSplitsPlaceNumbersAndHiragana() {
        assertEquals(NumberCutParts("", "", "", ""), parseNumberCutParts(null))
        assertEquals(NumberCutParts("", "", "", ""), parseNumberCutParts("  "))
        assertEquals("", getInitialPlaceFromNumberCut(null))
        assertEquals("", getInitialPlaceFromNumberCut(""))
        assertEquals("品川", getInitialPlaceFromNumberCut("品川500あ12"))
        assertEquals(NumberCutParts("品川", "500", "あ", "12"), parseNumberCutParts("品川500あ12"))
        assertEquals(NumberCutParts("品川", "58A", "あ", "12"), parseNumberCutParts("品川58Aあ12"))
        assertEquals(NumberCutParts("品川", "500", "", ""), parseNumberCutParts("品川500"))
    }
}
