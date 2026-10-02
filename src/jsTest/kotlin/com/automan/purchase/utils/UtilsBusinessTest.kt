package com.automan.purchase.utils

import com.automan.purchase.buildPdfFilename
import com.automan.purchase.consigneeNameWithoutCountryPrefix
import com.automan.purchase.ensurePurchaseListMandatoryColumnsPreservingOrder
import com.automan.purchase.getDefaultColumnsForDevice
import com.automan.purchase.getMaxPurchaseListColumnsForDevice
import com.automan.purchase.htmlTableColgroupPurchaseList
import com.automan.purchase.purchaseListColumnLabels
import com.automan.purchase.purchaseListDataColumnWidthPx
import com.automan.purchase.purchaseListTableMinWidthPx
import com.automan.purchase.ensurePurchaseListPinnedColumns
import com.automan.purchase.nextPurchaseSortOrder
import com.automan.purchase.purchaseColumnOrderAfterCheck
import com.automan.purchase.purchaseColumnOrderAfterUncheck
import com.automan.purchase.purchaseColumnPickerRows
import com.automan.purchase.reorderPurchaseListColumn
import com.automan.purchase.sortPurchasesInMemory
import com.automan.purchase.sortSelectedPurchaseColumnsByLabel
import com.automan.purchase.formatCarModelYear
import com.automan.purchase.formatCurrency
import com.automan.purchase.formatWithWeekday
import com.automan.purchase.isoToMmDdYyyy
import com.automan.purchase.normalizeDateForComparison
import com.automan.purchase.parseCurrency
import com.automan.purchase.parseDateForSorting
import com.automan.purchase.sanitizePdfFilenameToken
import com.automan.purchase.sanitizePurchaseListSelectedColumns
import com.automan.purchase.splitSemicolonDistinctTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UtilsBusinessTest {

    @Test
    fun currencyParseAndFormat() {
        assertEquals(17000.0, parseCurrency("17,000"))
        assertEquals(1500.0, parseCurrency("¥1,500"))
        assertEquals(12.5, parseCurrency("12.5"))
        assertEquals(0.0, parseCurrency(""))
        assertEquals(0.0, parseCurrency("abc"))
        assertEquals("0", formatCurrency(0.0))
        assertEquals("17,000", formatCurrency(17000.0))
    }

    @Test
    fun dateHelpers() {
        assertEquals("", formatWithWeekday(null))
        assertEquals("", formatWithWeekday("  "))
        assertEquals("June 15, 2026(Monday)", formatWithWeekday("June 15, 2026(Monday)"))
        assertEquals("June 15, 2026(Monday)", formatWithWeekday("2026-06-15T12:00:00"))

        assertEquals("06/15/2026", isoToMmDdYyyy("2026-06-15"))
        assertEquals("", isoToMmDdYyyy(null))
        assertEquals("", isoToMmDdYyyy("06/15/2026"))

        assertEquals("", normalizeDateForComparison(null))
        assertEquals("April24, 2025", normalizeDateForComparison("April24, 2025"))
        assertEquals("April24, 2025", normalizeDateForComparison("April24, 2025(Thursday)"))
        assertEquals("April24, 2025", normalizeDateForComparison("24 Apr, 2025"))

        assertNull(parseDateForSorting("  "))
        assertNull(parseDateForSorting("not a date"))
        val later = parseDateForSorting("2026-06-15")
        val earlier = parseDateForSorting("2026-01-02")
        assertTrue(later != null && earlier != null && later > earlier)
    }

    @Test
    fun formatCarModelYearLabels() {
        assertEquals("", formatCarModelYear(null))
        assertEquals("", formatCarModelYear("  "))
        assertEquals("July 2025", formatCarModelYear("2025-07"))
        assertEquals("July 2025", formatCarModelYear("07/2025"))
        assertEquals("00/2025", formatCarModelYear("2025-00"))
        assertEquals("hello", formatCarModelYear("hello"))
    }

    @Test
    fun purchaseListColumnsKeepDateAndChassisPinned() {
        assertEquals(listOf("invoiceConfirmed", "vessel"), sanitizePurchaseListSelectedColumns(listOf("sold", "vesselNo", "sold", "nope")))
        assertEquals(listOf("date", "chassis", "brand"), ensurePurchaseListPinnedColumns(listOf("brand"), 6))
        assertEquals(
            listOf("date", "auctionNo", "chassis"),
            ensurePurchaseListPinnedColumns(listOf("auctionNo", "brand"), 3),
        )
        val pinned = ensurePurchaseListPinnedColumns(emptyList(), 2)
        assertEquals(listOf("date", "chassis"), pinned)
    }

    @Test
    fun purchaseListDisplayOrderKeepsDraggedPositions() {
        assertEquals(
            listOf("date", "clientName", "auctionNo", "chassis", "notes"),
            ensurePurchaseListMandatoryColumnsPreservingOrder(
                listOf("date", "clientName", "auctionNo", "chassis", "notes"),
                11,
            ),
        )
        assertEquals(
            listOf("date", "chassis", "brand"),
            ensurePurchaseListMandatoryColumnsPreservingOrder(listOf("brand"), 11),
        )
        assertEquals(
            listOf("clientName", "date", "notes", "chassis"),
            ensurePurchaseListMandatoryColumnsPreservingOrder(
                listOf("clientName", "date", "notes", "chassis", "auctionNo"),
                4,
            ),
        )
        val eleven = (1..12).map { "extra$it" }
        assertEquals(11, ensurePurchaseListMandatoryColumnsPreservingOrder(eleven, 11).size)
    }

    @Test
    fun draggingASelectedColumnOnlyChangesItsDisplayPosition() {
        val start = listOf("date", "chassis", "auctionNo", "clientName", "notes")
        val dragged = reorderPurchaseListColumn(start, "clientName", 1)
        assertEquals(listOf("date", "clientName", "chassis", "auctionNo", "notes"), dragged)
        val afterUnselect = ensurePurchaseListMandatoryColumnsPreservingOrder(
            dragged.filter { it != "auctionNo" },
            11,
        )
        assertEquals(listOf("date", "clientName", "chassis", "notes"), afterUnselect)
    }

    @Test
    fun selectedColumnsStayAtTheTopOfTheSameCheckboxList() {
        val catalog = listOf(
            "date" to "Purchase Date",
            "chassis" to "Chassis",
            "clientName" to "Client Name",
            "auctionNo" to "Auction No",
            "notes" to "Notes",
            "brand" to "Brand",
        )
        val afterClient = purchaseColumnOrderAfterCheck(listOf("date", "chassis"), "clientName", 11)
        assertEquals(listOf("date", "chassis", "clientName"), afterClient)
        val afterAuction = purchaseColumnOrderAfterCheck(afterClient, "auctionNo", 11)
        assertEquals(listOf("date", "chassis", "clientName", "auctionNo"), afterAuction)
        assertEquals(
            listOf("date", "chassis", "clientName", "auctionNo", "brand", "notes"),
            purchaseColumnPickerRows(afterAuction, catalog),
        )
        val unchecked = purchaseColumnOrderAfterUncheck(afterAuction, "clientName")
        assertEquals(listOf("date", "chassis", "auctionNo"), unchecked)
        assertEquals(
            listOf("date", "chassis", "auctionNo", "brand", "clientName", "notes"),
            purchaseColumnPickerRows(unchecked, catalog),
        )
        assertEquals(afterAuction, purchaseColumnOrderAfterUncheck(afterAuction, "date"))
        assertEquals(afterAuction, purchaseColumnOrderAfterUncheck(afterAuction, "chassis"))
        assertEquals(11, purchaseColumnOrderAfterCheck((1..11).map { "c$it" }, "extra", 11).size)
    }

    @Test
    fun draggingSelectedColumnsDoesNotChangeSortAndCannotMoveUnselected() {
        val selected = listOf("date", "chassis", "clientName", "auctionNo")
        val dragged = reorderPurchaseListColumn(selected, "clientName", 1)
        assertEquals(listOf("date", "clientName", "chassis", "auctionNo"), dragged)
        assertEquals(selected, reorderPurchaseListColumn(selected, "notes", 0))
        assertEquals("asc", nextPurchaseSortOrder("auctionNo", "auctionNo", "desc"))
        assertEquals(dragged, reorderPurchaseListColumn(dragged, "auctionNo", 3))
    }

    @Test
    fun selectedColumnNamesSortAlphabeticallyWithoutChangingRowSort() {
        val labels = mapOf(
            "date" to "Purchase Date",
            "chassis" to "Chassis",
            "auctionNo" to "Auction No",
            "clientName" to "Client Name",
            "notes" to "Notes",
            "rixoCompany" to "Rixo Company",
        )
        val selected = listOf("date", "chassis", "auctionNo", "clientName")
        val rowSortBefore = nextPurchaseSortOrder("date", "date", "desc")
        val ascending = sortSelectedPurchaseColumnsByLabel(selected, labels, ascending = true)
        assertEquals(listOf("auctionNo", "chassis", "clientName", "date"), ascending)
        val descending = sortSelectedPurchaseColumnsByLabel(ascending, labels, ascending = false)
        assertEquals(listOf("date", "clientName", "chassis", "auctionNo"), descending)
        val longer = listOf("date", "chassis", "auctionNo", "clientName", "notes", "rixoCompany")
        assertEquals(
            listOf("auctionNo", "chassis", "clientName", "notes", "date", "rixoCompany"),
            sortSelectedPurchaseColumnsByLabel(longer, labels, ascending = true),
        )
        assertEquals(
            listOf("rixoCompany", "date", "notes", "clientName", "chassis", "auctionNo"),
            sortSelectedPurchaseColumnsByLabel(longer, labels, ascending = false),
        )
        val afterDrag = reorderPurchaseListColumn(ascending, "date", 0)
        assertEquals(listOf("date", "auctionNo", "chassis", "clientName"), afterDrag)
        assertEquals(selected.size, ascending.size)
        assertEquals(rowSortBefore, nextPurchaseSortOrder("date", "date", "desc"))
    }

    @Test
    fun purchaseRowsSortByDateNumberAndText() {
        fun row(date: String, auctionNo: String, clientName: String, price: String): dynamic {
            val p = js("({})")
            p["date"] = date
            p["auctionNo"] = auctionNo
            p["clientName"] = clientName
            p["price"] = price
            return p
        }
        val rows = arrayOf(
            row("2026-06-15", "10", "Zeta", "2000"),
            row("2026-01-02", "2", "alpha", "15000"),
        )
        assertEquals("2026-01-02", sortPurchasesInMemory(rows, "date", "asc").first().date as String)
        assertEquals("2026-06-15", sortPurchasesInMemory(rows, "date", "desc").first().date as String)
        assertEquals("2", sortPurchasesInMemory(rows, "auctionNo", "asc").first().auctionNo as String)
        assertEquals("10", sortPurchasesInMemory(rows, "auctionNo", "desc").first().auctionNo as String)
        assertEquals("alpha", sortPurchasesInMemory(rows, "clientName", "asc").first().clientName as String)
        assertEquals("2000", sortPurchasesInMemory(rows, "price", "asc").first().price as String)
    }

    @Test
    fun nameTokensAndPdfFilenames() {
        assertEquals(listOf("Fit", "Vitz"), splitSemicolonDistinctTokens(" Fit ; fit; ;Vitz "))
        assertEquals(emptyList(), splitSemicolonDistinctTokens(" ; "))

        assertEquals("ABC Ltd", consigneeNameWithoutCountryPrefix("Japan - ABC Ltd"))
        assertEquals("ABC Ltd", consigneeNameWithoutCountryPrefix("ABC Ltd"))
        assertEquals("", consigneeNameWithoutCountryPrefix("  "))

        assertEquals("Client_Name", sanitizePdfFilenameToken("Client Name"))
        assertEquals("a_b_c", sanitizePdfFilenameToken("a/b:c"))
        assertEquals("unknown", sanitizePdfFilenameToken(null))
        assertEquals("unknown", sanitizePdfFilenameToken("   "))
        assertEquals("Final_Invoice_Acme.pdf", buildPdfFilename("Final Invoice", "Acme", null, "  "))
    }

    @Test
    fun desktopPurchaseColumnCapIsThirteenAndShortColumnsStayNarrow() {
        assertEquals(13, getMaxPurchaseListColumnsForDevice("desktop"))
        assertEquals(11, getMaxPurchaseListColumnsForDevice("tablet"))
        assertEquals(11, getMaxPurchaseListColumnsForDevice("mobile"))
        assertEquals(11, getDefaultColumnsForDevice("desktop").size)
        assertEquals(13, purchaseColumnOrderAfterCheck((1..13).map { "c$it" }, "extra", 13).size)

        val labels = purchaseListColumnLabels()
        assertTrue(purchaseListDataColumnWidthPx("auctionNo", labels.getValue("auctionNo")) != null)
        assertNull(purchaseListDataColumnWidthPx("clientName", labels.getValue("clientName")))
        val html = htmlTableColgroupPurchaseList(
            listOf("date", "auctionNo", "clientName"),
            labels,
            includeAction = true,
        )
        assertTrue(html.contains("width:80px"))
        assertTrue(html.contains("""<col style="width:${purchaseListDataColumnWidthPx("auctionNo", "Auction No")}px">"""))
        assertTrue(html.endsWith("<col></colgroup>"))
        val minWidth = purchaseListTableMinWidthPx(listOf("auctionNo", "clientName"), labels, includeAction = true)
        val auctionWidth = purchaseListDataColumnWidthPx("auctionNo", "Auction No") ?: 0
        assertTrue(minWidth >= 80 + auctionWidth + 120)
    }
}
