package com.automan.purchase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InvoiceHistoryPrefillTest {

    @Test
    fun amountSlotsPreserveEmptyPositions() {
        val slots = parseInvoiceHistoryAmountSlots("¥100,000;;300000")

        assertEquals(listOf(100_000.0, null, 300_000.0), slots)
    }

    @Test
    fun amountSlotValidationRejectsMissingOrZeroHistoricalAmounts() {
        val chassis = parseInvoiceHistoryChassisTokens("ABC123;DEF456;GHI789")

        assertNull(invoiceHistoryAmountSlotError(chassis, listOf(100_000.0, 200_000.0, 300_000.0)))
        assertNotNull(invoiceHistoryAmountSlotError(chassis, listOf(100_000.0, null, 300_000.0)))
        assertNotNull(invoiceHistoryAmountSlotError(chassis, parseInvoiceHistoryAmountSlots("100000;0;300000")))
        assertNotNull(invoiceHistoryAmountSlotError(chassis, listOf(100_000.0, 300_000.0)))
    }
}
