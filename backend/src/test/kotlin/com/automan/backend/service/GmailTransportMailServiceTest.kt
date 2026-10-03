package com.automan.backend.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GmailTransportMailServiceTest {

    @Test
    fun `blank credentials do not open smtp`() {
        val service = GmailTransportMailService("", "", "smtp.gmail.com", 587)
        assertFalse(service.isConfigured())
        assertThrows<GmailNotConfiguredException> {
            service.sendRixoTransportPdf(
                to = "buyer@example.com",
                rixoCompany = "SHAHBAZ",
                buyingDate = "2026-07-24",
                headMessage = "hello",
                pdfBytes = byteArrayOf(1, 2, 3),
            )
        }
    }

    @Test
    fun `password alone is not enough to send`() {
        val service = GmailTransportMailService("", "app-password", "smtp.gmail.com", 587)
        assertFalse(service.isConfigured())
        assertThrows<GmailNotConfiguredException> {
            service.sendRixoTransportPdf("buyer@example.com", "SHAHBAZ", "2026-07-24", "hello", byteArrayOf(1))
        }
    }

    @Test
    fun `spaces in the app password still count as configured`() {
        val service = GmailTransportMailService(
            "sender@gmail.com",
            "abcd efgh ijkl mnop",
            "smtp.gmail.com",
            587,
        )
        assertTrue(service.isConfigured())
    }

    @Test
    fun `whitespace only app password is not configured`() {
        val service = GmailTransportMailService("sender@gmail.com", "   ", "smtp.gmail.com", 587)
        assertFalse(service.isConfigured())
    }

    @Test
    fun `blank subject is the company and buying date`() {
        assertEquals("SHAHBAZ - 2026-07-24", GmailTransportMailService.defaultSubject("SHAHBAZ", "2026-07-24"))
        assertEquals("Undefined", GmailTransportMailService.defaultSubject("  ", ""))
    }

    @Test
    fun `recipient must look like an email`() {
        assertFalse(GmailTransportMailService.isValidRecipient(null))
        assertFalse(GmailTransportMailService.isValidRecipient("  "))
        assertFalse(GmailTransportMailService.isValidRecipient("not-an-email"))
        assertTrue(GmailTransportMailService.isValidRecipient(" buyer@example.com "))
    }
}
