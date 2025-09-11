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
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import java.io.InputStream
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class PdfService {

    private fun getJapaneseFont(): PdfFont {
        return try {
            println("🎌 Font: Trying Noto Sans CJK Japanese font")
            val fontStream: InputStream = javaClass.classLoader.getResourceAsStream("fonts/NotoSansCJKjp-Regular.otf")
            if (fontStream != null) {
                val font = PdfFontFactory.createFont(fontStream.readBytes(), PdfEncodings.IDENTITY_H)
                fontStream.close()
                println("🎌 Font: Successfully created Noto Sans CJK Japanese font")
                font
            } else {
                throw Exception("Font file not found")
            }
        } catch (e: Exception) {
            println("🎌 Font: Noto Sans CJK failed: ${e.message}")
            try {
                println("🎌 Font: Trying Noto Sans CJK TTC font")
                val fontStream: InputStream = javaClass.classLoader.getResourceAsStream("fonts/NotoSansCJK-Regular.ttc")
                if (fontStream != null) {
                    val font = PdfFontFactory.createFont(fontStream.readBytes(), PdfEncodings.IDENTITY_H)
                    fontStream.close()
                    println("🎌 Font: Successfully created Noto Sans CJK TTC font")
                    font
                } else {
                    throw Exception("TTC font file not found")
                }
            } catch (e2: Exception) {
                println("🎌 Font: TTC font failed: ${e2.message}")
                try {
                    println("🎌 Font: Trying default font as fallback")
                    val font = PdfFontFactory.createFont()
                    println("🎌 Font: Using default font (Japanese may not display correctly)")
                    font
                } catch (e3: Exception) {
                    println("🎌 Font: All attempts failed: ${e3.message}")
                    PdfFontFactory.createFont()
                }
            }
        }
    }

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

        // Create main layout table with two columns
        val mainTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)

        // Left side - Company details, address, contact info, and consignee
        val leftCell = Cell()
            .add(Paragraph("#112 taiyo mansion, 3-6-1\n")
                .add("gyotoku ekimae, Ichikawa-Shi,\n")
                .add("Chiba-Ken. 272-0133\n")
                .add(Text("Tel: ").setBold()).add("+81-47-701-3770\n")
                .add(Text("Fax: ").setBold()).add("+81-47-701-3771\n")
                .add(Text("E-Mail: ").setBold()).add("memonco@ymail.com\n")
                .add(Text("CONSIGNEE").setBold())
                .add("\n${invoiceData["consignee"] ?: ""}")
                .setFontSize(10f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(0f)
        mainTable.addCell(leftCell)

        // Right side - INVOICE title with invoice details and shipping details
        val rightCell = Cell()
            .add(Paragraph("INVOICE").setFontSize(16f).setBold().setMarginBottom(10f))
            .add(Paragraph().add(Text("No: ").setBold()).add("${invoiceData["invoiceNo"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("Date: ").setBold()).add("${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}").setFontSize(10f))
            .add(Paragraph().add(Text("L/C No: ").setBold()).add("${invoiceData["lcNo"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("VESSEL: ").setBold()).add("${invoiceData["vessel"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("SAIL DATE: ").setBold()).add("${invoiceData["sailDate"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("FROM: ").setBold()).add("${invoiceData["from"] ?: ""}").setFontSize(10f))
            .add(Paragraph().add(Text("TO: ").setBold()).add("${invoiceData["to"] ?: ""}").setFontSize(10f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(0f)
        mainTable.addCell(rightCell)

        document.add(mainTable)

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

    fun generateRixoTransportPdf(purchases: List<Purchase>, transportData: Map<String, String>): ByteArray {
        println("🎌 PDF Service: Starting Japanese PDF generation")
        println("🎌 PDF Service: Purchases count: ${purchases.size}")
        println("🎌 PDF Service: Transport data: $transportData")
        
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)
        
        // Get Japanese-compatible font
        val japaneseFont = getJapaneseFont()
        println("🎌 PDF Service: Using font: ${japaneseFont.fontProgram?.fontNames?.getFontName()}")

        // Add title with 陸送 on upper left, KLC in center, 様 on upper right, and date on upper right
        val titleTable = Table(UnitValue.createPercentArray(floatArrayOf(25f, 25f, 25f, 25f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // 陸送 on upper left
        titleTable.addCell(createTitleCell("陸送", japaneseFont))
        
        // KLC in center
        titleTable.addCell(createTitleCell("KLC", japaneseFont))
        
        // 様 on upper right
        titleTable.addCell(createTitleCell("様", japaneseFont))
        
        // Date on upper right (4th column)
        val transportDate = transportData["transportDate"] ?: ""
        val formattedDate = formatDateToJapanese(transportDate)
        println("🎌 PDF Service: Original date: '$transportDate' -> Formatted: '$formattedDate'")
        
        val dateCell = Cell()
            .add(Paragraph()
                .add(Text("日付 ").setBold().setFont(japaneseFont))
                .add(Text(" ").setFont(japaneseFont)) // Add extra space after 日付
                .add(Text(formattedDate).setFont(japaneseFont))
                .setFontSize(12f))
            .setPadding(8f)
            .setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        titleTable.addCell(dateCell)
        document.add(titleTable)


        // Add thank you message in Japanese
        val thankYou = Paragraph("いつもお世話になっております。")
            .setFont(japaneseFont)
            .setFontSize(10f)
            .setMarginBottom(5f)
        document.add(thankYou)

        val request = Paragraph("下記の車両の陸送手配をお願いいたします。")
            .setFont(japaneseFont)
            .setFontSize(10f)
            .setMarginBottom(20f)
        document.add(request)

        // Create table for vehicle data with Japanese headers
        val table = Table(UnitValue.createPercentArray(floatArrayOf(15f, 12f, 20f, 12f, 15f, 15f, 10f, 10f, 15f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)

        // Add table headers in Japanese
        val headers = listOf("日付", "出品番号", "型式・車体番号", "年式", "車名", "取引先名", "搬入先名", "会場ID", "ナンバーカット")
        println("🎌 PDF Service: Adding Japanese headers: $headers")
        headers.forEach { header ->
            println("🎌 PDF Service: Adding header: '$header'")
            table.addCell(createHeaderCell(header, japaneseFont))
        }

        // Add vehicle data rows
        purchases.forEachIndexed { index, purchase ->
            // Date (only show in first row, formatted as "2025年9月11日Thursday")
            if (index == 0) {
                table.addCell(createCell(formattedDate, japaneseFont))
            } else {
                table.addCell(createCell("", japaneseFont)) // Empty for other rows
            }
            
            // Lot Number
            table.addCell(createCell(purchase.lotNumber ?: "", japaneseFont))
            
            // Chassis
            table.addCell(createCell(purchase.chassis ?: "", japaneseFont))
            
            // Car Model Year
            table.addCell(createCell(purchase.carModelYear?.toString() ?: "", japaneseFont))
            
            // Car Name
            table.addCell(createCell(purchase.carName ?: "", japaneseFont))
            
            // Client Name
            table.addCell(createCell(purchase.clientName ?: "", japaneseFont))
            
            // Stock Location (from purchase data or default to KLC)
            table.addCell(createCell(purchase.stockLocation ?: "KLC", japaneseFont))
            
            // Venue ID (from purchase data or "Not Found")
            table.addCell(createCell(purchase.venueId ?: "Not Found", japaneseFont))
            
            // Number Cut (from purchase data or empty)
            table.addCell(createCell(purchase.numberCut ?: "", japaneseFont))
        }

        document.add(table)

        // Create a table to position total count on the right side under the table
        val totalTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)
        
        // Empty cell on the left (invisible box)
        val emptyCell = Cell()
            .add(Paragraph("").setFont(japaneseFont))
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        totalTable.addCell(emptyCell)
        
        // Total count on the right side (invisible box)
        val totalCell = Cell()
            .add(Paragraph("合計 ${purchases.size} 台")
                .setFont(japaneseFont)
                .setFontSize(10f))
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        totalTable.addCell(totalCell)
        document.add(totalTable)

        // Add important note in Japanese
        val note = Paragraph("※港や船での盗難が多発の為、スペアキーやリモコンキーが車内に")
            .setFont(japaneseFont)
            .setFontSize(9f)
            .setMarginBottom(5f)
        document.add(note)

        // Add the new line about mailing spare keys
        val mailNote = Paragraph("ありましたら弊社まで郵送していただけると助かります。")
            .setFont(japaneseFont)
            .setFontSize(9f)
            .setMarginBottom(20f)
        document.add(mailNote)

        // Add contact information in Japanese positioned in lower right
        val contactTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginTop(30f)
        
        // Empty cell on the left (invisible box)
        val contactEmptyCell = Cell()
            .add(Paragraph("").setFont(japaneseFont))
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        contactTable.addCell(contactEmptyCell)
        
        // Contact info on the right side with line gaps before 有限会社メモン
        val contactCell = Cell()
            .add(Paragraph()
                .add(Text("担当：芽紋 080-3918-1478\n").setFont(japaneseFont))
                .add(Text("FAX: 047-711-0409\n").setFont(japaneseFont))
                .add(Text("\n").setFont(japaneseFont)) // Line gap
                .add(Text("\n").setFont(japaneseFont)) // Another line gap
                .add(Text("有限会社メモン").setFont(japaneseFont).setBold()) // Make company name bold
                .setFontSize(9f))
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        contactTable.addCell(contactCell)
        document.add(contactTable)

        document.close()
        return outputStream.toByteArray()
    }

    private fun createCell(text: String, font: PdfFont? = null): Cell {
        val paragraph = Paragraph(text).setFontSize(9f)
        if (font != null) {
            paragraph.setFont(font)
        }
        return Cell()
            .add(paragraph)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
    }

    private fun createHeaderCell(text: String, font: PdfFont? = null): Cell {
        val paragraph = Paragraph(text).setFontSize(9f).setBold()
        if (font != null) {
            paragraph.setFont(font)
        }
        return Cell()
            .add(paragraph)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
    }

    private fun createTitleCell(text: String, font: PdfFont? = null): Cell {
        val paragraph = Paragraph(text).setFontSize(16f).setBold()
        if (font != null) {
            paragraph.setFont(font)
        }
        return Cell()
            .add(paragraph)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
    }

    private fun formatDateToJapanese(dateString: String): String {
        if (dateString.isBlank()) return ""
        
        try {
            // Try to parse various date formats
            val formatters = listOf(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyyMMdd")
            )
            
            var parsedDate: java.time.LocalDate? = null
            for (formatter in formatters) {
                try {
                    parsedDate = java.time.LocalDate.parse(dateString, formatter)
                    break
                } catch (e: Exception) {
                    // Continue to next formatter
                }
            }
            
            if (parsedDate != null) {
                val year = parsedDate.year
                val month = parsedDate.monthValue
                val day = parsedDate.dayOfMonth
                val dayOfWeek = parsedDate.dayOfWeek.name
                
                // Convert day of week to English format (as shown in the image)
                val dayOfWeekEnglish = when (dayOfWeek) {
                    "MONDAY" -> "Monday"
                    "TUESDAY" -> "Tuesday"
                    "WEDNESDAY" -> "Wednesday"
                    "THURSDAY" -> "Thursday"
                    "FRIDAY" -> "Friday"
                    "SATURDAY" -> "Saturday"
                    "SUNDAY" -> "Sunday"
                    else -> dayOfWeek
                }
                
                return "${year}年${month}月${day}日${dayOfWeekEnglish}"
            }
        } catch (e: Exception) {
            // If parsing fails, return original string
        }
        
        return dateString
    }

}
