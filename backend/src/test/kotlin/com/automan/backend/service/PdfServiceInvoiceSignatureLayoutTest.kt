package com.automan.backend.service

import com.automan.backend.dto.InvoiceItem
import com.automan.backend.dto.InvoicePdfRequest
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PdfServiceInvoiceSignatureLayoutTest {

    private val pdfService = PdfService()

    @Test
    fun longInvoiceKeepsSignatureWithLastCars() {
        val bytes = pdfService.generateInvoicePdf(sample(11))
        val pages = extractPages(bytes)
        assertEquals(2, pages.size)
        val lastCars = "CHASSIS-10"
        assertTrue(pages[1].contains(lastCars), pages[1])
        assertTrue(pages[1].contains("M. Asif Memon"), pages[1])
        assertTrue(pages[0].contains("CHASSIS-01"), pages[0])
        assertTrue(!pages[1].contains("CHASSIS-01"), pages[1])
    }

    @Test
    fun threeCarInvoiceKeepsOneTable() {
        val bytes = pdfService.generateInvoicePdf(sample(3))
        val pages = extractPages(bytes)
        assertEquals(1, pages.size)
        assertEquals(1, Regex("DESCRIPTION").findAll(pages[0]).count(), pages[0])
        assertTrue(pages[0].contains("CHASSIS-01"), pages[0])
        assertTrue(pages[0].contains("CHASSIS-03"), pages[0])
        assertTrue(pages[0].contains("M. Asif Memon"), pages[0])
    }

    @Test
    fun shortInvoiceStaysOnOnePage() {
        val bytes = pdfService.generateInvoicePdf(sample(2))
        val pages = extractPages(bytes)
        assertEquals(1, pages.size)
        assertTrue(pages[0].contains("CHASSIS-02"), pages[0])
        assertTrue(pages[0].contains("M. Asif Memon"), pages[0])
    }

    private fun sample(count: Int): InvoicePdfRequest {
        val items = (1..count).map { n ->
            InvoiceItem(
                unit = n,
                description = "CHASSIS-${n.toString().padStart(2, '0')}   TOYOTA CROWN   G   2020-05     AT     4 DOOR 5 SEAT     2500cc     WHITE     45,000km     PETROL",
                amount = "¥1,250,000",
            )
        }
        return InvoicePdfRequest(
            invoiceNumber = "INV-TEST",
            invoiceDate = "2026-09-30",
            lcNumber = null,
            clientName = "CROWN EAGLE WANYA",
            clientAddress = "123 Sample Street, Dar es Salaam",
            vessel = "SAMPLE VESSEL",
            shippingDate = "30.SEP.2026",
            from = "NAGOYA",
            to = "DAR ES SALAAM",
            priceType = "C&F",
            items = items,
            totalAmount = "¥13,750,000",
            bankAccount = "BANK NAME: SAMPLE BANK\nBRANCH: TOKYO\nACCOUNT: 1234567\nSWIFT: SAMPLES",
            message = null,
            consignee = "CROWN EAGLE WANYA",
            consigneeAddress = "123 Sample Street, Dar es Salaam",
            notifyParty = "SAME AS CONSIGNEE",
            bookingNo = "BK-1",
            carrier = "SAMPLE LINE",
        )
    }

    private fun extractPages(bytes: ByteArray): List<String> {
        val pdf = PdfDocument(PdfReader(ByteArrayInputStream(bytes)))
        return try {
            (1..pdf.numberOfPages).map { i ->
                PdfTextExtractor.getTextFromPage(pdf.getPage(i))
            }
        } finally {
            pdf.close()
        }
    }
}
