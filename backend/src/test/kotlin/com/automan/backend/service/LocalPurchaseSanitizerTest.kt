package com.automan.backend.service

import com.automan.backend.model.Purchase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalPurchaseSanitizerTest {

    private val sanitizer = LocalPurchaseSanitizer()

    @Test
    fun `apply leaves export purchase unchanged when local is false`() {
        val purchase = Purchase(
            chassis = "ABC123",
            local = false,
            rixoCompany = "SHAHBAZ",
            vessel = "VESSEL-1",
            freight = "1000",
        )
        val result = sanitizer.apply(purchase)
        assertEquals("SHAHBAZ", result.rixoCompany)
        assertEquals("VESSEL-1", result.vessel)
        assertEquals("1000", result.freight)
    }

    @Test
    fun `apply clears export fields when local is true`() {
        val purchase = Purchase(
            chassis = "ABC123",
            local = true,
            rixoCompany = "SHAHBAZ",
            stockLocation = "GLOBAL KAWASAKI",
            pol = "YOKOHAMA",
            country = "PAKISTAN",
            bookingId = 322L,
            rixoRequested = "TRUE",
            rixoConfirmed = "TRUE",
            bookingRequested = true,
            shipmentDate = "2026-07-14",
            blNo = "BL-1",
            vessel = "VESSEL-1",
            shipmentCharges = "500",
            freight = "1000",
            inspectionFee = "200",
            rixoPrice = "10000",
            clientName = "LOCAL",
            price = "500000",
        )
        val result = sanitizer.apply(purchase)
        assertEquals("SHAHBAZ", result.rixoCompany)
        assertEquals("GLOBAL KAWASAKI", result.stockLocation)
        assertEquals("10000", result.rixoPrice)
        assertEquals("TRUE", result.rixoRequested)
        assertEquals("TRUE", result.rixoConfirmed)
        assertNull(result.pol)
        assertNull(result.country)
        assertNull(result.bookingId)
        assertFalse(result.bookingRequested)
        assertNull(result.shipmentDate)
        assertNull(result.blNo)
        assertNull(result.vessel)
        assertNull(result.shipmentCharges)
        assertNull(result.freight)
        assertNull(result.inspectionFee)
        assertEquals("LOCAL", result.clientName)
        assertEquals("500000", result.price)
    }
}
