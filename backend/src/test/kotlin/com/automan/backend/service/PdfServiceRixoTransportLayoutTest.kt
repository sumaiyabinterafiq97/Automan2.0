package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PdfServiceRixoTransportLayoutTest {

    private val pdfService = PdfService()

    @Test
    fun generateRixoTransportPdf_usesNewLayoutStringsAndMappedAddress() {
        val purchase = Purchase(
            chassis = "AAHH45-7777",
            auctionNo = "12345",
            auctionHouse = "ARAI BAYSIDE (FUKUOKA YARD)",
            carName = "TOYOTA",
            carModelYear = "2026-06",
            stockLocation = "ARAI BAYSIDE (FUKUOKA YARD)",
            venueId = "65010",
            numberCut = "札幌いい",
        )
        val bytes = pdfService.generateRixoTransportPdf(
            listOf(purchase),
            mapOf(
                "rixoCompany" to "LOGICO",
                "buyingDate" to "2026-08-12",
                "headMessage" to "HEAD-LINE-ONE",
                "footerMessage" to "FOOTER-BLACK",
                "extraMessage" to "EXTRA-RED-NOTE",
                "contactDetails" to "担当：算数 080-3918-1478",
            ),
            mapOf("ARAI BAYSIDE (FUKUOKA YARD)" to "1 Fukuoka Yard Rd"),
        )
        val text = extractPdfText(bytes)
        assertTrue(text.contains("陸送依頼書"), text)
        assertTrue(text.contains("LOGICO"), text)
        assertTrue(text.contains("開催日"), text)
        assertTrue(text.contains("HEAD-LINE-ONE"), text)
        assertTrue(text.contains("取引先"), text)
        assertTrue(text.contains("POS番号"), text)
        assertTrue(text.contains("65010"), text)
        assertTrue(text.contains("搬入先"), text)
        assertTrue(text.contains("1 Fukuoka Yard Rd"), text)
        assertTrue(text.contains("FOOTER-BLACK"), text)
        assertTrue(text.contains("EXTRA-RED-NOTE"), text)
        assertTrue(text.contains("算数 080-3918-1478"), text)
        assertTrue(text.contains("担当"), text)
        assertTrue(text.contains("合計 1 台"), text)
    }

    @Test
    fun generateRixoTransportPdf_fallsBackToStockNameWhenAddressMissing() {
        val purchase = Purchase(
            chassis = "CH-1",
            auctionHouse = "TAA CHUBU",
            stockLocation = "ECL KOBE",
            venueId = "111",
        )
        val bytes = pdfService.generateRixoTransportPdf(
            listOf(purchase),
            mapOf("rixoCompany" to "STYLISH AUTO", "buyingDate" to "2026-08-06"),
            emptyMap(),
        )
        val text = extractPdfText(bytes)
        assertTrue(text.contains("ECL KOBE"), text)
        assertTrue(text.contains("POS番号"), text)
    }

    @Test
    fun generateRixoTransportPdf_emptyCompanyShowsUndefinedNotKlc() {
        val purchase = Purchase(
            chassis = "CH-UNDEF",
            auctionHouse = "TAA CHUBU",
            stockLocation = "ECL KOBE",
            venueId = "111",
        )
        val bytes = pdfService.generateRixoTransportPdf(
            listOf(purchase),
            mapOf("rixoCompany" to "", "buyingDate" to "2026-08-06"),
            emptyMap(),
        )
        val text = extractPdfText(bytes)
        assertTrue(text.contains("Undefined"), text)
        assertFalse(text.contains("KLC"), text)
    }

    @Test
    fun generateRixoTransportPdf_blankStockLocationDoesNotEmitKlc() {
        val purchase = Purchase(
            chassis = "AAHP45W",
            auctionHouse = "TAA CHUBU",
            stockLocation = null,
            venueId = "111",
        )
        val bytes = pdfService.generateRixoTransportPdf(
            listOf(purchase),
            mapOf("rixoCompany" to "LOGICO", "buyingDate" to "2026-07-15"),
            emptyMap(),
        )
        val text = extractPdfText(bytes)
        assertTrue(text.contains("AAHP45W"), text)
        assertFalse(text.contains("KLC"), text)
    }

    private fun extractPdfText(bytes: ByteArray): String {
        val pdf = PdfDocument(PdfReader(ByteArrayInputStream(bytes)))
        val out = StringBuilder()
        for (i in 1..pdf.numberOfPages) {
            out.append(PdfTextExtractor.getTextFromPage(pdf.getPage(i)))
            out.append('\n')
        }
        pdf.close()
        return java.text.Normalizer.normalize(out.toString(), java.text.Normalizer.Form.NFKC)
    }
}
