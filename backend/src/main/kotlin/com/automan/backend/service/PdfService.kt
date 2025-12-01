package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.dto.InvoicePdfRequest
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.geom.PageSize
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

    fun generateInvoicePdf(request: InvoicePdfRequest): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        val japaneseFont = getJapaneseFont()
        document.setFont(japaneseFont)

        // Header
        document.add(
            Paragraph("INVOICE")
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(15f)
        )

        val headerTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(10f)

        val leftHeader = Cell()
            .add(Paragraph("Invoice No: ${request.invoiceNumber}").setFontSize(10f))
            .add(Paragraph("Invoice Date: ${request.invoiceDate}").setFontSize(10f))
            .add(Paragraph("LC No: ${request.lcNumber ?: "-"}").setFontSize(10f))
            .add(Paragraph("Client: ${request.clientName}").setFontSize(10f))
            .add(Paragraph("Client Address: ${request.clientAddress ?: "-"}").setFontSize(10f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        headerTable.addCell(leftHeader)

        val rightHeader = Cell()
            .add(Paragraph("Vessel: ${request.vessel}").setFontSize(10f))
            .add(Paragraph("Shipping Date: ${request.shippingDate}").setFontSize(10f))
            .add(Paragraph("From: ${request.from}").setFontSize(10f))
            .add(Paragraph("To: ${request.to}").setFontSize(10f))
            .add(Paragraph("Price Type: ${request.priceType}").setFontSize(10f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        headerTable.addCell(rightHeader)

        document.add(headerTable)

        // Items table
        val table = Table(UnitValue.createPercentArray(floatArrayOf(10f, 70f, 20f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginTop(10f)

        listOf("No.", "Description", "Amount").forEach {
            table.addHeaderCell(
                Cell().add(Paragraph(it).setBold().setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
                    .setPadding(8f)
            )
        }

        request.items.forEach { item ->
            table.addCell(createCell(item.unit.toString(), japaneseFont))
            table.addCell(createCell(item.description, japaneseFont))
            table.addCell(
                createCell(item.amount, japaneseFont)
                    .setTextAlignment(TextAlignment.RIGHT)
            )
        }

        document.add(table)

        document.add(
            Paragraph("Total Amount: ${request.totalAmount}")
                .setTextAlignment(TextAlignment.RIGHT)
                .setFontSize(12f)
                .setBold()
                .setMarginTop(15f)
        )

        request.bankAccount?.let {
            document.add(
                Paragraph("Bank Account: $it")
                    .setFontSize(10f)
                    .setMarginTop(10f)
            )
        }

        request.message?.let {
            document.add(
                Paragraph(it)
                    .setFontSize(10f)
                    .setMarginTop(10f)
            )
        }

        document.close()
        return outputStream.toByteArray()
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

            // DESCRIPTION column - format: CHASSIS NO. [chassis] \n [carName, carModelYear, clientName, rixoCompany, rixoRequested, rixoConfirmed]
            val description = buildString {
                append("CHASSIS NO. ${purchase.chassis ?: ""}\n")
                val details = listOfNotNull(
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
        // Set page size to A4 landscape (horizontal)
        pdfDocument.setDefaultPageSize(PageSize.A4.rotate())
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
        
        // Rixo Company in center (dynamic from form)
        val rixoCompany = transportData["rixoCompany"] ?: "KLC"
        titleTable.addCell(createTitleCell(rixoCompany, japaneseFont))
        
        // 様 on upper right
        titleTable.addCell(createTitleCell("様", japaneseFont))
        
        // Date on upper right (4th column)
        println("🎌 PDF Service: transportData keys: ${transportData.keys}")
        println("🎌 PDF Service: transportData values: ${transportData.values}")
        println("🎌 PDF Service: buyingDate value: '${transportData["buyingDate"]}'")
        val transportDate = transportData["buyingDate"] ?: ""
        // Header wants full date with year and weekday in Japanese, e.g. 2025年9月30日火曜日
        val formattedDateWithWeekday = formatDateToJapanese(transportDate, includeYear = true)
        println("🎌 PDF Service: Original date: '$transportDate' -> Formatted: '$formattedDateWithWeekday'")
        
        val dateCell = Cell()
            .add(Paragraph()
                .add(Text("日付 ").setBold().setFont(japaneseFont))
                .add(Text(" ").setFont(japaneseFont)) // Add extra space after 日付
                .add(Text(formattedDateWithWeekday).setFont(japaneseFont))
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
        // Column widths: 日付 (12f), 出品番号 (7f), 型式・車体番号 (14f), 年式 (12f), 車名 (9f), 取引先名 (9f), 搬入先名 (10f), 会場ID (14f), ナンバーカット (13f)
        val table = Table(UnitValue.createPercentArray(floatArrayOf(12f, 7f, 14f, 12f, 9f, 9f, 10f, 14f, 13f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(20f)

        // Add table headers in Japanese
        val headers = listOf("日付", "出品番号", "型式・車体番号", "年式", "車名", "取引先名", "搬入先名", "会場ID", "ナンバーカット")
        println("🎌 PDF Service: Adding Japanese headers: $headers")
        headers.forEach { header ->
            println("🎌 PDF Service: Adding header: '$header'")
            table.addCell(createHeaderCell(header, japaneseFont))
        }

        // Determine total rows needed (minimum 5, or actual count if more)
        val totalRows = maxOf(5, purchases.size)

        // Add vehicle data rows
        purchases.forEachIndexed { index, purchase ->
            // Date (only show in first row, formatted as "2025年9月11日Thursday")
            if (index == 0) {
                // Table cell wants short date without year, e.g. 9月30日 火曜日
                val shortDate = formatDateToJapanese(transportDate, includeYear = false)
                table.addCell(createCell(shortDate, japaneseFont))
            } else {
                table.addCell(createCell("", japaneseFont)) // Empty for other rows
            }
            
            // Lot Number (using auctionNo)
            table.addCell(createCell(purchase.auctionNo ?: "", japaneseFont))
            
            // Chassis
            table.addCell(createCell(purchase.chassis ?: "", japaneseFont))
            
            // Car Model Year - format to "Month YYYY" format
            val formattedYear = formatCarModelYear(purchase.carModelYear?.toString())
            table.addCell(createCell(formattedYear, japaneseFont))
            
            // Car Name
            table.addCell(createCell(purchase.carName ?: "", japaneseFont))
            
            // Auction House (取引先名 - Supplier Name)
            val auctionHouseValue = purchase.auctionHouse ?: ""
            println("🎌 PDF Service: Auction House value: '$auctionHouseValue' for purchase ID: ${purchase.id}")
            table.addCell(createCell(auctionHouseValue, japaneseFont))
            
            // Stock Location (from purchase data or default to KLC)
            table.addCell(createCell(purchase.stockLocation ?: "KLC", japaneseFont))
            
            // Venue ID (from purchase data or "Not Found")
            table.addCell(createCell(purchase.venueId ?: "Not Found", japaneseFont))
            
            // Number Cut (from purchase data or empty)
            table.addCell(createCell(purchase.numberCut ?: "", japaneseFont))
        }
        
        // Add empty rows if needed to reach minimum of 5 rows
        if (purchases.size < 5) {
            val emptyRowsNeeded = 5 - purchases.size
            repeat(emptyRowsNeeded) {
                // Add 9 empty cells for each empty row (one for each column)
                repeat(9) {
                    table.addCell(createCell("", japaneseFont))
                }
            }
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
        
        // Total count on the right side (invisible box) - use actual purchases count, not total rows
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

    private fun formatDateToJapanese(dateString: String, includeYear: Boolean = true): String {
        if (dateString.isBlank()) return ""
        
        println("🎌 PDF Service: formatDateToJapanese input: '$dateString', includeYear: $includeYear")
        
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
                    println("🎌 PDF Service: Trying formatter: ${formatter.toString()}")
                    parsedDate = java.time.LocalDate.parse(dateString, formatter)
                    println("🎌 PDF Service: Successfully parsed with ${formatter.toString()}: $parsedDate")
                    break
                } catch (e: Exception) {
                    println("🎌 PDF Service: Failed with ${formatter.toString()}: ${e.message}")
                    // Continue to next formatter
                }
            }
            
            if (parsedDate != null) {
                val year = parsedDate.year
                val month = parsedDate.monthValue
                val day = parsedDate.dayOfMonth
                val dayOfWeek = parsedDate.dayOfWeek.name
                
                // Convert day of week to Japanese
                val dayOfWeekJapanese = when (dayOfWeek) {
                    "MONDAY" -> "月曜日"
                    "TUESDAY" -> "火曜日"
                    "WEDNESDAY" -> "水曜日"
                    "THURSDAY" -> "木曜日"
                    "FRIDAY" -> "金曜日"
                    "SATURDAY" -> "土曜日"
                    "SUNDAY" -> "日曜日"
                    else -> dayOfWeek
                }
                
                val core = if (includeYear) "${year}年${month}月${day}日${dayOfWeekJapanese}" else "${month}月${day}日 ${dayOfWeekJapanese}"
                return core
            }
        } catch (e: Exception) {
            // If parsing fails, return original string
        }
        
        return dateString
    }

    // Formats carModelYear from YYYY-MM or MM/YYYY to "Month YYYY" format
    // Examples: "2025-07" -> "July 2025", "07/2025" -> "July 2025", "7/2025" -> "July 2025"
    private fun formatCarModelYear(yearStr: String?): String {
        if (yearStr == null || yearStr.isBlank()) return ""
        
        val months = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        
        try {
            // Handle YYYY-MM format (from month input)
            if (yearStr.contains("-")) {
                val parts = yearStr.split("-")
                if (parts.size == 2) {
                    val year = parts[0].toIntOrNull()
                    val month = parts[1].toIntOrNull()
                    if (year != null && month != null && month >= 1 && month <= 12) {
                        return "${months[month - 1]} $year"
                    }
                }
            }
            
            // Handle MM/YYYY or M/YYYY format (from database)
            if (yearStr.contains("/")) {
                val parts = yearStr.split("/")
                if (parts.size == 2) {
                    val month = parts[0].toIntOrNull()
                    val year = parts[1].toIntOrNull()
                    if (month != null && year != null && month >= 1 && month <= 12) {
                        return "${months[month - 1]} $year"
                    }
                }
            }
            
            // If already in readable format, return as is
            return yearStr
        } catch (e: Exception) {
            return yearStr
        }
    }

}
