package com.automan.backend.service

import com.automan.backend.dto.CarPdfDto
import com.automan.backend.dto.ConsigneeDetailsDto
import com.automan.backend.dto.ShippingSchedulePdfData
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class PdfServiceConsigneeShippingPdfTest {

    private val pdfService = PdfService()

    @Test
    fun consigneeShippingPdfOmitsPrices() {
        val text = extract(pdfService.generateShippingScheduleInvoicePdf(sample(), includePrices = false))
        assertTrue(text.contains("A200A-0143989"), text)
        assertTrue(text.contains("6 UNITS"), text)
        assertFalse(text.contains("AMOUNT"), text)
        assertFalse(text.contains("GRAND TOTAL"), text)
        assertFalse(text.contains("9,760,000"), text)
    }

    @Test
    fun staffShippingPdfKeepsPrices() {
        val text = extract(pdfService.generateShippingScheduleInvoicePdf(sample()))
        assertTrue(text.contains("AMOUNT JPY"), text)
        assertTrue(text.contains("GRAND TOTAL"), text)
        assertTrue(text.contains("1,815,000"), text)
    }

    private fun sample() = ShippingSchedulePdfData(
        companyName = "MEMON CO., LTD",
        bookingNo = "OSAG54151700",
        vesselName = "BROOKLYN BRIDGE",
        pol = "OSAKA",
        pod = "KARACHI",
        shippingDate = "27.SEP.2025",
        consigneeDetails = ConsigneeDetailsDto("OVERSEAS TRANSIT AGENCY (PVT) LTD.", "Karachi"),
        carList = listOf(
            CarPdfDto(1, "RAIZE", "A200A-0143989", "2021", "¥1,815,000", "TOYOTA", "RAIZE"),
            CarPdfDto(2, "NOTE", "E13-287273", "2024", "¥1,500,000", "NISSAN", "NOTE"),
            CarPdfDto(3, "NOTE", "E13-295049", "2024", "¥1,540,000", "NISSAN", "NOTE"),
            CarPdfDto(4, "TAFT", "LA900S-0153563", "2023", "¥1,380,000", "DAIHATSU", "TAFT"),
            CarPdfDto(5, "AQUA", "MXPK11-2111823", "2023", "¥1,530,000", "TOYOTA", "AQUA"),
            CarPdfDto(6, "KICKS", "P15-042379", "2022", "¥1,860,000", "NISSAN", "KICKS"),
        ),
        calculationMode = "C&F",
    )

    private fun extract(bytes: ByteArray): String {
        val pdf = PdfDocument(PdfReader(ByteArrayInputStream(bytes)))
        return try {
            (1..pdf.numberOfPages).joinToString("\n") { PdfTextExtractor.getTextFromPage(pdf.getPage(it)) }
        } finally {
            pdf.close()
        }
    }
}
