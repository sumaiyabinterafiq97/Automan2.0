package com.automan.purchase

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalViewTest {
    @Test
    fun dashboardAndTablesRenderPortalFieldsOnly() {
        val dashboard = portalDashboardHtml(3, 2, 1, 4)
        assertTrue(dashboard.contains("Purchased cars"))
        assertTrue(dashboard.contains("Assigned clients"))
        assertTrue(dashboard.contains(">3<"))
        assertNoFinancialMarkup(dashboard)

        val cars = portalCarsTableHtml(
            listOf(PortalCarRow("ABC-1", "Aqua", "2020", "Nagoya", "Japan", true)),
            "No purchased cars yet.",
        )
        assertTrue(cars.contains("ABC-1"))
        assertTrue(cars.contains("Shipped"))
        assertFalse(cars.contains("purchaseId"))
        assertNoFinancialMarkup(cars)

        val empty = portalCarsTableHtml(emptyList(), "No purchased cars yet.")
        assertTrue(empty.contains("data-portal-state=\"empty\""))
        assertTrue(empty.contains("No purchased cars yet."))

        val shipments = portalShipmentsTableHtml(
            listOf(
                PortalShipmentRow("", "", "NAGOYA", "CHATTOGRAM", "", "", "", listOf("ABC-1")),
                PortalShipmentRow("SHIP-1", "SOON", "YOKOHAMA", "CHATTOGRAM", "2026-03-01", "2026-02-20", "2026-03-20", listOf("ABC-2"), listOf("Portal Client A")),
            ),
            "No shipments yet.",
            includeClients = true,
        )
        assertTrue(shipments.contains("Booked, not yet shipped"))
        assertTrue(shipments.contains("Shipment row"))
        assertTrue(shipments.contains("Not recorded"))
        assertTrue(shipments.contains("Portal Client A"))
        assertFalse(shipments.contains("delivered"))
        assertNoFinancialMarkup(shipments)

        val invoices = portalInvoicesTableHtml(
            listOf(PortalInvoiceRow("INV-1", "2026-03-01", "", listOf("ABC-1"), "C&amp;F")),
        )
        assertTrue(invoices.contains("INV-1"))
        assertTrue(invoices.contains("Price type"))
        assertNoFinancialMarkup(invoices)

        val transactions = portalTransactionsTableHtml(
            listOf(PortalTransactionRow("2026-02-02", "PAYMENT RECEIVED")),
        )
        assertTrue(transactions.contains("2026-02-02"))
        assertTrue(transactions.contains("PAYMENT RECEIVED"))
        assertFalse(transactions.contains("Description"))
        assertFalse(transactions.contains("Bill number"))
        assertFalse(transactions.contains("¥1,250.50"))
        assertFalse(transactions.contains("Mizuho"))
        assertNoFinancialMarkup(transactions)
    }

    private fun assertNoFinancialMarkup(html: String) {
        val lower = html.lowercase()
        listOf(
            "totalprice", "profit", "rixoprice", "currentbalance", "transactionprice",
            "paymentreceived", "runningbalance", "creditlimit", "debitlimit", "suppliercost",
            "lineamount", "freight", "¥",
        ).forEach { forbidden ->
            assertFalse(lower.contains(forbidden), forbidden)
        }
    }
}
