package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PdfService {

    fun generateRixoPdf(purchases: List<Purchase>, invoiceData: Map<String, String>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        // Add company header
        val companyName = Paragraph("Memon Co., LTD.")
            .setFontSize(16f)
            .setBold()
            .setMarginBottom(5f)
        document.add(companyName)

        // Add company details with bold labels
        val companyDetails = Paragraph()
            .setFontSize(10f)
            .setMarginBottom(20f)
            .add("#112 taiyo mansion, 3-6-1 gyotoku ekimae, Ichikawa-Shi, Chiba-Ken. 272-0133\n")
            .add(Text("Tel: ").setBold()).add("+81-47-701-3770\n")
            .add(Text("Fax: ").setBold()).add("+81-47-701-3771\n")
            .add(Text("E-Mail: ").setBold()).add("memonco@ymail.com")
        document.add(companyDetails)

        // Add invoice details table with INVOICE title positioned over VESSEL section
        val invoiceTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)

        // Left side - Invoice details with bold labels
        val leftCell = Cell()
            .add(Paragraph().add(Text("No: ").setBold()).add("${invoiceData["invoiceNo"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("Date: ").setBold()).add("${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}").setFontSize(10f))
            .add(Paragraph().add(Text("L/C No: ").setBold()).add("${invoiceData["lcNo"] ?: ""}").setFontSize(10f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(0f)
        invoiceTable.addCell(leftCell)

        // Right side - Shipping details with INVOICE title and bold labels
        val rightCell = Cell()
            .add(Paragraph("INVOICE").setFontSize(16f).setBold().setMarginBottom(10f))
            .add(Paragraph().add(Text("VESSEL: ").setBold()).add("${invoiceData["vessel"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("SAIL DATE: ").setBold()).add("${invoiceData["sailDate"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("FROM: ").setBold()).add("${invoiceData["from"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("TO: ").setBold()).add("${invoiceData["to"] ?: ""}").setFontSize(10f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(0f)
        invoiceTable.addCell(rightCell)

        document.add(invoiceTable)

        // Add consignee with bold label
        val consignee = Paragraph()
            .add(Text("CONSIGNEE").setBold())
            .add("\n${invoiceData["consignee"] ?: ""}")
            .setFontSize(10f)
            .setMarginBottom(20f)
        document.add(consignee)

        // Create main table with 3 columns: No., DESCRIPTION, AMOUNT (JPY)
        val table = Table(UnitValue.createPercentArray(floatArrayOf(8f, 72f, 20f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setHorizontalAlignment(HorizontalAlignment.CENTER)

        // Add header row
        val noHeader = Cell()
            .add(Paragraph("No.").setBold().setFontSize(10f).setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
            .setPadding(8f)
        table.addHeaderCell(noHeader)

        val descHeader = Cell()
            .add(Paragraph("DESCRIPTION").setBold().setFontSize(10f).setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
            .setPadding(8f)
        table.addHeaderCell(descHeader)

        val amountHeader = Cell()
            .add(Paragraph("AMOUNT (JPY)").setBold().setFontSize(10f).setTextAlignment(TextAlignment.CENTER))
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
            .setPadding(8f)
        table.addHeaderCell(amountHeader)

        // Add data rows
        var totalAmount = 0.0
        for ((index, purchase) in purchases.withIndex()) {
            val rowNumber = index + 1
            
            // No. column
            table.addCell(createCell(rowNumber.toString()))

            // DESCRIPTION column - format: CHASSIS NO. [chassis] \n [lotNumber, carName, carModelYear, clientName, rixoCompany, rixoRequested, rixoConfirmed]
            val description = buildString {
                append("CHASSIS NO. ${purchase.chassis ?: ""}\n")
                val details = listOfNotNull(
                    purchase.lotNumber,
                    purchase.carName,
                    purchase.carModelYear,
                    purchase.clientName,
                    purchase.rixoCompany,
                    purchase.rixoRequested,
                    purchase.rixoConfirmed
                ).joinToString(", ")
                append(details)
            }
            table.addCell(createCell(description))

            // AMOUNT (JPY) column
            val rixoPrice = purchase.rixoPrice?.replace("¥", "")?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            totalAmount += rixoPrice
            table.addCell(createCell("${rixoPrice.toInt()}"))
        }

        document.add(table)

        // Add total amount
        val totalParagraph = Paragraph("TOTAL AMOUNT (JPY): ${totalAmount.toInt()}")
            .setFontSize(12f)
            .setBold()
            .setTextAlignment(TextAlignment.RIGHT)
            .setMarginTop(20f)
        document.add(totalParagraph)

        val cnfParagraph = Paragraph("CNF KARACHI JPY ${totalAmount.toInt()}")
            .setFontSize(12f)
            .setBold()
            .setTextAlignment(TextAlignment.RIGHT)
            .setMarginTop(5f)
        document.add(cnfParagraph)

        // Add bank account information at bottom left
        val bankInfo = Paragraph()
            .setFontSize(9f)
            .setMarginTop(30f)
            .add("BANK OF SMBC MITSUI SUMITOMO (Gyoutoku) BRANCH\n")
            .add("A/C NO. 0398932 (ORDINARY)\n")
            .add("A/C Name Memon Co. Ltd.\n")
            .add("SWIFT CODE: SMBCJPJT")
        document.add(bankInfo)

        document.close()
        return outputStream.toByteArray()
    }

    private fun createCell(text: String): Cell {
        return Cell()
            .add(Paragraph(text).setFontSize(9f))
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
    }

}
