package com.automan.backend.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfFilenameUtilsTest {
    @Test
    fun buildsFinalInvoiceFilename() {
        assertEquals(
            "Final_Invoice_SHEHROZE_MOTORS.pdf",
            PdfFilenameUtils.build("Final_Invoice", "SHEHROZE MOTORS"),
        )
    }

    @Test
    fun buildsDocTypeAndParts() {
        assertEquals(
            "Invoice_INV-1785_SHEHROZE_MOTORS.pdf",
            PdfFilenameUtils.build("Invoice", "INV-1785", "SHEHROZE MOTORS"),
        )
    }

    @Test
    fun skipsEmptyParts() {
        assertEquals(
            "ClientStatement_SHEHROZE_MOTORS.pdf",
            PdfFilenameUtils.build("ClientStatement", "SHEHROZE MOTORS", ""),
        )
    }

    @Test
    fun usesUnknownFallbackWhenTokenEmptyAfterSanitize() {
        assertEquals(
            "ShippingSchedule_unknown_NUUK.pdf",
            PdfFilenameUtils.build("ShippingSchedule", "???", "NUUK"),
        )
    }

    @Test
    fun dateTokenFromIso() {
        assertEquals("20260807", PdfFilenameUtils.dateToken("2026-08-07"))
    }
}
