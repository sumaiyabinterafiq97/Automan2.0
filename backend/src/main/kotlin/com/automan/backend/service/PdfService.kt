package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.dto.ClientStatementDto
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.dto.UnpaidAgingReportDto
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.LineSeparator
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.HorizontalAlignment
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.io.font.PdfEncodings
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine
import com.automan.backend.util.CarModelYearUtils
import com.automan.backend.util.Logger
import java.io.InputStream
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class PdfService {

    companion object {
        /** Default footer when Create Invoice MESSAGE is empty (MEMON sample style). */
        private val DEFAULT_INVOICE_CARGO_NOTE = listOf(
            "CARGO IN TRANSIT TO FINAL DESTINATION:",
            "KENCONT CFS FOR STRIPPING",
            "ON MERCHANT'S FULL ACCOUNT, RESPONSIBILITY",
            "AND RISK UNTIL EMPTY RETURN TO OUR",
            "DESIGNATED INLAND EMPTY DEPOT IN",
            "MOMBASA",
        )
    }

    private fun getJapaneseFont(): PdfFont {
        return try {
            Logger.debug("🎌 Font: Trying Noto Sans CJK Japanese font")
            val fontStream: InputStream = javaClass.classLoader.getResourceAsStream("fonts/NotoSansCJKjp-Regular.otf")
            if (fontStream != null) {
                val font = PdfFontFactory.createFont(fontStream.readBytes(), PdfEncodings.IDENTITY_H)
                fontStream.close()
                Logger.debug("🎌 Font: Successfully created Noto Sans CJK Japanese font")
                font
            } else {
                throw Exception("Font file not found")
            }
        } catch (e: Exception) {
            Logger.warn("🎌 Font: Noto Sans CJK failed: ${e.message}")
            try {
                Logger.debug("🎌 Font: Trying Noto Sans CJK TTC font")
                val fontStream: InputStream = javaClass.classLoader.getResourceAsStream("fonts/NotoSansCJK-Regular.ttc")
                if (fontStream != null) {
                    val font = PdfFontFactory.createFont(fontStream.readBytes(), PdfEncodings.IDENTITY_H)
                    fontStream.close()
                    Logger.debug("🎌 Font: Successfully created Noto Sans CJK TTC font")
                    font
                } else {
                    throw Exception("TTC font file not found")
                }
            } catch (e2: Exception) {
                Logger.warn("🎌 Font: TTC font failed: ${e2.message}")
                try {
                    Logger.debug("🎌 Font: Trying default font as fallback")
                    val font = PdfFontFactory.createFont()
                    Logger.warn("🎌 Font: Using default font (Japanese may not display correctly)")
                    font
                } catch (e3: Exception) {
                    Logger.error("🎌 Font: All attempts failed: ${e3.message}")
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

        val dash = { v: String? -> v?.trim()?.takeIf { it.isNotEmpty() } ?: "-" }
        val noBorder = com.itextpdf.layout.borders.Border.NO_BORDER
        val thinBorder = com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.6f)

        fun invoiceRule(marginTop: Float = 2f, marginBottom: Float = 2f): LineSeparator {
            val line = SolidLine(0.6f)
            line.color = ColorConstants.BLACK
            return LineSeparator(line).setMarginTop(marginTop).setMarginBottom(marginBottom)
        }

        fun openCell(paragraph: Paragraph): Cell =
            Cell()
                .add(paragraph)
                .setBorder(noBorder)
                .setPadding(1f)
                .setPaddingTop(2f)
                .setPaddingBottom(2f)

        fun invoiceItemCell(
            text: String,
            align: TextAlignment = TextAlignment.LEFT,
            bold: Boolean = false,
            top: Boolean = false,
            bottom: Boolean = false,
        ): Cell {
            val p = Paragraph(text).setFontSize(9f).setTextAlignment(align).setFixedLeading(11f).setFont(japaneseFont)
            if (bold) p.setBold()
            return Cell()
                .add(p)
                .setBorder(noBorder)
                .setBorderTop(if (top) thinBorder else noBorder)
                .setBorderBottom(if (bottom) thinBorder else noBorder)
                .setPadding(4f)
                .setPaddingTop(5f)
                .setPaddingBottom(5f)
        }

        // Top: company + date
        val topTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(6f)
        topTable.addCell(
            Cell()
                .add(Paragraph("MEMON CO., LTD").setFontSize(12f).setBold())
                .add(
                    Paragraph(
                        "#112 taiyo mansion, 3-6-1 gyotoku ekimae, Ichikawa-Shi,\n" +
                            "Chiba-Ken. 272-0133\n" +
                            "Tel: +81-47-701-3770  Fax: +81-47-701-3771\n" +
                            "E-Mail: memonco@ymail.com",
                    ).setFontSize(8f).setFixedLeading(10f),
                )
                .setBorder(noBorder)
                .setPadding(0f),
        )
        topTable.addCell(
            Cell()
                .add(
                    Paragraph()
                        .add(Text("DATE ").setBold())
                        .add(formatInvoiceDisplayDate(request.invoiceDate))
                        .setFontSize(10f)
                        .setTextAlignment(TextAlignment.RIGHT),
                )
                .setBorder(noBorder)
                .setPadding(0f),
        )
        document.add(topTable)

        document.add(
            Paragraph("INVOICE")
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setUnderline()
                .setMarginBottom(10f),
        )

        // Consignee / Notify | Shipping particulars (open line style like MEMON sample)
        val metaTable = Table(UnitValue.createPercentArray(floatArrayOf(48f, 52f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(6f)

        val consigneeText = request.consignee?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        val consigneeAddress = request.consigneeAddress?.trim()?.takeIf { it.isNotEmpty() }
        val notifyText = request.notifyParty?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        val leftConsignee = Cell()
            .add(Paragraph().add(Text("CONSIGNEE").setBold()).setFontSize(9f).setMarginBottom(2f))
            .add(Paragraph(consigneeText).setBold().setFontSize(9f).setFixedLeading(11f).setMarginBottom(2f))
        if (consigneeAddress != null) {
            leftConsignee.add(
                Paragraph(consigneeAddress)
                    .setFontSize(8f)
                    .setFixedLeading(10f)
                    .setMarginBottom(8f),
            )
        } else {
            leftConsignee.add(Paragraph("").setMarginBottom(8f))
        }
        leftConsignee
            .add(
                Paragraph()
                    .add(Text("NOTIFY PARTY ").setBold())
                    .add(notifyText)
                    .setFontSize(9f)
                    .setFixedLeading(11f),
            )
            .setBorder(noBorder)
            .setPadding(0f)
            .setPaddingRight(10f)
        metaTable.addCell(leftConsignee)

        val shipBox = Table(UnitValue.createPercentArray(floatArrayOf(100f)))
            .setWidth(UnitValue.createPercentValue(100f))

        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("VESSEL: ").setBold())
                    .add(dash(request.vessel))
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("BOOKING NO. ").setBold())
                    .add(dash(request.bookingNo))
                    .add("     ")
                    .add(Text("CARRIER: ").setBold())
                    .add(dash(request.carrier))
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(
            Cell()
                .add(invoiceRule(4f, 4f))
                .setBorder(noBorder)
                .setPadding(0f),
        )
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("CY CUT: ").setBold())
                    .add(formatInvoiceShortDate(request.cyCutDate))
                    .add(" | ")
                    .add(Text("ETD: ").setBold())
                    .add(formatInvoiceShortDate(request.shippingDate))
                    .add(" | ")
                    .add(Text("ETA: ").setBold())
                    .add(formatInvoiceEtaDate(request.eta))
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(
            Cell()
                .add(invoiceRule(4f, 4f))
                .setBorder(noBorder)
                .setPadding(0f),
        )
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("POL: ").setBold())
                    .add(dash(request.from))
                    .add("     ")
                    .add(Text("POD: ").setBold())
                    .add(dash(request.to))
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("FINAL DESTINATION: ").setBold())
                    .add(dash(request.finalDestination))
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )

        metaTable.addCell(
            Cell()
                .add(shipBox)
                .setBorder(noBorder)
                .setPadding(0f),
        )
        document.add(metaTable)

        // TRADE TERMS between full-width rules (MEMON sample)
        document.add(invoiceRule(8f, 3f))
        document.add(
            Paragraph()
                .add(Text("TRADE TERMS: ").setBold())
                .add(dash(request.priceType))
                .setFontSize(10f)
                .setMarginBottom(0f)
                .setMarginTop(0f),
        )
        document.add(invoiceRule(3f, 6f))

        // Items table — open style: horizontal rules + pipe-style header, no gray grid
        val table = Table(UnitValue.createPercentArray(floatArrayOf(6f, 14f, 22f, 24f, 12f, 22f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginTop(2f)

        val headers = listOf(
            "",
            "MAKER |",
            "MODEL |",
            "CHASIS NO. |",
            "YEAR |",
            "AMOUNT JPY",
        )
        headers.forEachIndexed { index, label ->
            val align = when (index) {
                0 -> TextAlignment.CENTER
                5 -> TextAlignment.RIGHT
                else -> TextAlignment.LEFT
            }
            table.addHeaderCell(invoiceItemCell(label, align, bold = true, top = true, bottom = true))
        }

        request.items.forEach { item ->
            val maker = item.maker?.trim()?.takeIf { it.isNotEmpty() }
                ?: extractMakerFromDescription(item.description)
            val model = item.model?.trim()?.takeIf { it.isNotEmpty() }
                ?: extractModelFromDescription(item.description)
            val chassis = item.chassisNo?.trim()?.takeIf { it.isNotEmpty() }
                ?: extractChassisFromDescription(item.description)
            val year = item.year?.trim()?.takeIf { it.isNotEmpty() }
                ?: extractYearFromDescription(item.description)

            table.addCell(invoiceItemCell(item.unit.toString(), TextAlignment.CENTER))
            table.addCell(invoiceItemCell(maker))
            table.addCell(invoiceItemCell(model))
            table.addCell(invoiceItemCell(chassis))
            table.addCell(invoiceItemCell(year, TextAlignment.CENTER))
            table.addCell(invoiceItemCell(item.amount, TextAlignment.RIGHT))
        }

        document.add(table)

        val units = request.items.size
        val unitsLabel = if (units == 1) "1 UNIT" else "$units UNITS"
        document.add(invoiceRule(4f, 4f))
        val footerTable = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
        footerTable.addCell(
            Cell()
                .add(Paragraph(unitsLabel).setBold().setFontSize(11f))
                .setBorder(noBorder)
                .setPadding(0f)
                .setPaddingTop(2f)
                .setPaddingBottom(2f),
        )
        footerTable.addCell(
            Cell()
                .add(
                    Paragraph("GRAND TOTAL ${request.totalAmount}")
                        .setBold()
                        .setFontSize(11f)
                        .setTextAlignment(TextAlignment.RIGHT),
                )
                .setBorder(noBorder)
                .setPadding(0f)
                .setPaddingTop(2f)
                .setPaddingBottom(2f),
        )
        document.add(footerTable)
        document.add(invoiceRule(4f, 10f))

        // Cargo note: MESSAGE if provided, else MEMON-style default (Option C)
        val cargoLines = request.message?.trim()?.takeIf { it.isNotEmpty() }
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: DEFAULT_INVOICE_CARGO_NOTE
        cargoLines.forEachIndexed { index, line ->
            document.add(
                Paragraph(line.uppercase())
                    .setFontSize(9f)
                    .setFixedLeading(12f)
                    .setMarginTop(if (index == 0) 0f else 0f)
                    .setMarginBottom(0f),
            )
        }

        request.bankAccount?.trim()?.takeIf { it.isNotEmpty() }?.let {
            document.add(
                Paragraph("Bank Account: $it")
                    .setFontSize(8f)
                    .setMarginTop(10f),
            )
        }

        document.close()
        return outputStream.toByteArray()
    }

    private fun formatInvoiceDisplayDate(raw: String?): String {
        val iso = raw?.trim()?.take(10).orEmpty()
        if (iso.isEmpty()) return "-"
        return try {
            val d = LocalDate.parse(iso)
            d.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH))
        } catch (_: Exception) {
            raw?.trim().orEmpty().ifEmpty { "-" }
        }
    }

    private fun formatInvoiceShortDate(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "-"
        // Already display-formatted shipping dates like 27.SEP.2025
        if (s.contains(".") || s.contains(" ")) return s
        return try {
            val d = LocalDate.parse(s.take(10))
            "${d.monthValue}/${d.dayOfMonth}"
        } catch (_: Exception) {
            s
        }
    }

    private fun formatInvoiceEtaDate(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "-"
        return try {
            val d = LocalDate.parse(s.take(10))
            d.format(DateTimeFormatter.ofPattern("MMM-dd-yyyy", java.util.Locale.ENGLISH)).uppercase()
        } catch (_: Exception) {
            s
        }
    }

    private fun extractChassisFromDescription(description: String): String {
        val first = description.lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return "-"
        return first.split(Regex("\\s{2,}"))[0].trim().ifEmpty { "-" }
    }

    private fun extractModelFromDescription(description: String): String {
        val first = description.lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return "-"
        val parts = first.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.getOrNull(1)?.ifEmpty { "-" } ?: "-"
    }

    private fun extractMakerFromDescription(description: String): String = "-"

    private fun extractYearFromDescription(description: String): String {
        val first = description.lineSequence().firstOrNull()?.trim().orEmpty()
        if (first.isEmpty()) return "-"
        val parts = first.split(Regex("\\s{2,}")).map { it.trim() }.filter { it.isNotEmpty() }
        return parts.firstOrNull { it.matches(Regex("\\d{4}")) || it.matches(Regex("\\d{4}-\\d{2}")) } ?: "-"
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
                .setFontSize(10f)
                .setFixedLeading(12f)) // Tighter line spacing
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
        Logger.debug("PDF Service: Starting Japanese PDF generation, purchases count: ${purchases.size}")
        
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        // Set page size to A4 landscape (horizontal)
        pdfDocument.setDefaultPageSize(PageSize.A4.rotate())
        val document = Document(pdfDocument)
        
        // Get Japanese-compatible font
        val japaneseFont = getJapaneseFont()
        Logger.debug("PDF Service: Using font: ${japaneseFont.fontProgram?.fontNames?.getFontName()}")

        // Add title: "陸送 STYLISH AUTO 様" (one space, left-aligned), date (right)
        val titleTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(10f)
        
        // 陸送 + one space + Rixo Company 様 in one cell, left-aligned
        val rixoCompany = transportData["rixoCompany"] ?: "KLC"
        titleTable.addCell(createTitleCellLeft("陸送 $rixoCompany 様", japaneseFont))
        
        // Date on right: always "today" in Japan (document locale), e.g. 2026年4月27日月曜日
        Logger.debug("PDF Service: transportData keys: ${transportData.keys}")
        val transportDate = transportData["buyingDate"] ?: ""
        val todayJapan = LocalDate.now(ZoneId.of("Asia/Tokyo"))
        val formattedDateWithWeekday = formatDateToJapanese(todayJapan.toString(), includeYear = true)
        Logger.debug("PDF Service: Header 日付 (today JST): '$formattedDateWithWeekday'; table buyingDate raw: '$transportDate'")
        
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
            .setFixedLeading(12f) // Tighter line spacing
            .setMarginBottom(2f) // Reduced margin
        document.add(thankYou)

        val request = Paragraph("下記の車両の陸送手配をお願いいたします。")
            .setFont(japaneseFont)
            .setFontSize(10f)
            .setFixedLeading(12f) // Tighter line spacing
            .setMarginBottom(10f) // Reduced from 20f to 10f
        document.add(request)

        // Create table for vehicle data with Japanese headers
        // Column widths: 日付 (12f), 出品番号 (7f), 型式・車体番号 (14f), 年式 (12f), 車名 (9f), 取引先名 (9f), 搬入先名 (10f), 会場ID (14f), ナンバーカット (13f)
        val table = Table(UnitValue.createPercentArray(floatArrayOf(12f, 7f, 14f, 12f, 9f, 9f, 10f, 14f, 13f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(10f) // Reduced from 20f to 10f
            .setKeepTogether(false) // Allow table to break across pages for many vehicles

        // Add table headers in Japanese
        val headers = listOf("日付", "出品番号", "型式・車体番号", "年式", "車名", "取引先名", "搬入先名", "会場ID", "ナンバーカット")
        Logger.debug("PDF Service: Adding Japanese headers: $headers")
        headers.forEach { header ->
            Logger.debug("PDF Service: Adding header: '$header'")
            table.addCell(createHeaderCell(header, japaneseFont))
        }

        // Add vehicle data rows only (no empty rows; table height = selected row count)
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
            
            // Car Model Year - Japanese calendar (e.g. 令和8年)
            val yearOnly = CarModelYearUtils.extractYearFromCarModelYear(purchase.carModelYear?.toString())
            val japaneseEraYear = westernYearToJapaneseEra(yearOnly)
            table.addCell(createCell(japaneseEraYear, japaneseFont))
            
            // Car Name
            table.addCell(createCell(purchase.carName ?: "", japaneseFont))
            
            // Auction House (取引先名 - Supplier Name)
            val auctionHouseValue = purchase.auctionHouse ?: ""
            Logger.debug("PDF Service: Auction House value: '$auctionHouseValue' for purchase ID: ${purchase.id}")
            table.addCell(createCell(auctionHouseValue, japaneseFont))
            
            // Stock Location (from purchase data or default to KLC)
            table.addCell(createCell(purchase.stockLocation ?: "KLC", japaneseFont))
            
            // Venue ID (from purchase data or "Not Found")
            table.addCell(createCell(purchase.venueId ?: "Not Found", japaneseFont))
            
            // Number Cut (from purchase data or empty)
            table.addCell(createCell(purchase.numberCut ?: "", japaneseFont))
        }
        
        document.add(table)

        // Create a table to position extra message on left and total count on right, same level
        val extraMessage = transportData["extraMessage"]?.takeIf { it.isNotBlank() }
        val totalTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(8f) // Reduced from 20f to 8f
        
        // Extra message on the left side (if provided), otherwise empty
        val leftCell = if (extraMessage != null) {
            Cell()
                .add(Paragraph(extraMessage)
                    .setFont(japaneseFont)
                    .setFontSize(9f)
                    .setFixedLeading(11f)) // Tighter line spacing
                .setPadding(8f)
                .setTextAlignment(TextAlignment.LEFT)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        } else {
            Cell()
                .add(Paragraph("").setFont(japaneseFont))
                .setPadding(8f)
                .setTextAlignment(TextAlignment.LEFT)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        }
        totalTable.addCell(leftCell)
        
        // Total count on the right side - use actual purchases count, not total rows
        val totalCell = Cell()
            .add(Paragraph("合計 ${purchases.size} 台")
                .setFont(japaneseFont)
                .setFontSize(10f)
                .setFixedLeading(12f)) // Tighter line spacing
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        totalTable.addCell(totalCell)
        document.add(totalTable)

        // Footer: disclaimer (bottom-left) and contact (bottom-right) on the same baseline
        val footerMessage = transportData["footerMessage"]?.takeIf { it.isNotBlank() } 
            ?: "※港や船での盗難が多発の為、スペアキーやリモコンキーが車内にありましたら弊社まで郵送していただけると助かります。"
        val footerLines = footerMessage.split("\n")
        val footerBlock = com.itextpdf.layout.element.Div()
        footerLines.forEachIndexed { index, line ->
            footerBlock.add(Paragraph(line.trim())
                .setFont(japaneseFont)
                .setFontSize(9f)
                .setFixedLeading(11f)
                .setMarginBottom(if (index < footerLines.size - 1) 2f else 0f))
        }
        val contactBlock = Paragraph()
            .add(Text("担当：芽紋 080-3918-1478\n").setFont(japaneseFont))
            .add(Text("FAX: 047-711-0409\n").setFont(japaneseFont))
            .add(Text("有限会社メモン").setFont(japaneseFont).setBold())
            .setFontSize(9f)
            .setFixedLeading(11f)
        val footerTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginTop(10f)
        val leftFooterCell = Cell()
            .add(footerBlock)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.BOTTOM)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        val rightFooterCell = Cell()
            .add(contactBlock)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.RIGHT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.BOTTOM)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
        footerTable.addCell(leftFooterCell)
        footerTable.addCell(rightFooterCell)
        document.add(footerTable)

        document.close()
        return outputStream.toByteArray()
    }

    private fun createCell(text: String, font: PdfFont? = null): Cell {
        val paragraph = Paragraph(text).setFontSize(9f)
        if (font != null) {
            paragraph.setFont(font)
        }
        // Reduce line spacing for tighter text
        paragraph.setFixedLeading(11f) // Line height = font size + 2pt
        val cell = Cell()
            .add(paragraph)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
        
        // Always use black borders for all cells (including empty ones) to maintain table structure
        cell.setBorder(com.itextpdf.layout.borders.SolidBorder(
            com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
        
        return cell
    }

    private fun createHeaderCell(text: String, font: PdfFont? = null): Cell {
        val paragraph = Paragraph(text).setFontSize(9f).setBold()
        if (font != null) {
            paragraph.setFont(font)
        }
        // Reduce line spacing for tighter text
        paragraph.setFixedLeading(11f) // Line height = font size + 2pt
        return Cell()
            .add(paragraph)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.CENTER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
            .setBorder(com.itextpdf.layout.borders.SolidBorder(
                com.itextpdf.kernel.colors.ColorConstants.BLACK, 1f))
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

    private fun createTitleCellLeft(text: String, font: PdfFont? = null): Cell {
        val paragraph = Paragraph(text).setFontSize(16f).setBold()
        if (font != null) {
            paragraph.setFont(font)
        }
        return Cell()
            .add(paragraph)
            .setPadding(8f)
            .setTextAlignment(TextAlignment.LEFT)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
    }

    private fun formatDateToJapanese(dateString: String, includeYear: Boolean = true): String {
        if (dateString.isBlank()) return ""
        
        Logger.debug("PDF Service: formatDateToJapanese input: '$dateString', includeYear: $includeYear")
        
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
                    Logger.debug("PDF Service: Trying formatter: ${formatter.toString()}")
                    parsedDate = java.time.LocalDate.parse(dateString, formatter)
                    Logger.debug("PDF Service: Successfully parsed with ${formatter.toString()}: $parsedDate")
                    break
                } catch (e: Exception) {
                    Logger.debug("PDF Service: Failed with ${formatter.toString()}: ${e.message}")
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

    fun generateClientStatementPdf(statement: ClientStatementDto): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument, PageSize.A4.rotate())
        val font = getJapaneseFont()
        document.setFont(font)

        val periodText = when {
            statement.periodStart != null && statement.periodEnd != null ->
                "${statement.periodStart} – ${statement.periodEnd}"
            statement.periodStart != null -> "From ${statement.periodStart}"
            statement.periodEnd != null -> "Through ${statement.periodEnd}"
            else -> "All transactions"
        }

        document.add(
            Paragraph("CLIENT STATEMENT")
                .setFontSize(16f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(8f),
        )
        document.add(Paragraph("Generated: ${statement.generatedAt} · Period: $periodText").setFontSize(9f).setMarginBottom(12f))

        val meta = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(10f)
        meta.addCell(metaCell("Client: ${statement.clientName} (${statement.clientNumber})", font))
        meta.addCell(metaCell("Currency: ${statement.currency}", font))
        meta.addCell(metaCell("Credit limit: ${statement.creditLimit?.let { formatYenPlain(it) } ?: "—"}", font))
        meta.addCell(metaCell("Available credit: ${statement.availableCredit?.let { formatSignedYen(it) } ?: "—"}", font))
        meta.addCell(metaCell("Current balance: ${formatSignedYen(statement.currentBalance)} (${statement.balanceLabel})", font))
        meta.addCell(metaCell("", font))
        document.add(meta)

        val table = Table(UnitValue.createPercentArray(floatArrayOf(10f, 10f, 12f, 28f, 12f, 12f, 16f)))
            .setWidth(UnitValue.createPercentValue(100f))
        listOf("Date", "Type", "Reference", "Description", "Credit (+)", "Debit (−)", "Balance").forEach {
            table.addHeaderCell(createHeaderCell(it, font))
        }
        for (line in statement.lines) {
            table.addCell(createCell(line.date.toString(), font))
            table.addCell(createCell(line.typeLabel, font))
            table.addCell(createCell(line.reference ?: "", font))
            table.addCell(createCell(line.description ?: "", font))
            table.addCell(createCell(line.credit?.let { formatYenPlain(it) } ?: "", font))
            table.addCell(createCell(line.debit?.let { formatYenPlain(it) } ?: "", font))
            table.addCell(createCell(formatSignedYen(line.balance), font))
        }
        document.add(table)
        document.close()
        return outputStream.toByteArray()
    }

    fun generateUnpaidAgingPdf(report: UnpaidAgingReportDto): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument, PageSize.A4.rotate())
        val font = getJapaneseFont()
        document.setFont(font)

        document.add(
            Paragraph("UNPAID INVOICE AGING")
                .setFontSize(16f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(8f),
        )
        document.add(
            Paragraph("As of ${report.asOfDate} · Total open: ${formatYenPlain(report.totalOpen)}")
                .setFontSize(9f)
                .setMarginBottom(12f),
        )

        if (report.summaries.isNotEmpty()) {
            document.add(Paragraph("Summary by client").setFontSize(11f).setBold().setMarginBottom(6f))
            val summaryTable = Table(UnitValue.createPercentArray(floatArrayOf(22f, 10f, 17f, 17f, 17f, 17f)))
                .setWidth(UnitValue.createPercentValue(100f))
                .setMarginBottom(14f)
            listOf("Client", "Client #", "0–30", "31–60", "61–90", "90+").forEach {
                summaryTable.addHeaderCell(createHeaderCell(it, font))
            }
            for (s in report.summaries) {
                summaryTable.addCell(createCell(s.clientName, font))
                summaryTable.addCell(createCell(s.clientNumber, font))
                summaryTable.addCell(createCell(formatYenPlain(s.bucket0to30), font))
                summaryTable.addCell(createCell(formatYenPlain(s.bucket31to60), font))
                summaryTable.addCell(createCell(formatYenPlain(s.bucket61to90), font))
                summaryTable.addCell(createCell(formatYenPlain(s.bucket90Plus), font))
            }
            document.add(summaryTable)
        }

        document.add(Paragraph("Open invoices").setFontSize(11f).setBold().setMarginBottom(6f))
        val detailTable = Table(UnitValue.createPercentArray(floatArrayOf(20f, 10f, 14f, 12f, 8f, 14f, 14f)))
            .setWidth(UnitValue.createPercentValue(100f))
        listOf("Client", "Client #", "Invoice", "Invoice date", "Days", "Bucket", "Open amount").forEach {
            detailTable.addHeaderCell(createHeaderCell(it, font))
        }
        for (row in report.rows) {
            detailTable.addCell(createCell(row.clientName, font))
            detailTable.addCell(createCell(row.clientNumber, font))
            detailTable.addCell(createCell(row.invoiceNumber, font))
            detailTable.addCell(createCell(row.invoiceDate.toString(), font))
            detailTable.addCell(createCell(row.daysOutstanding.toString(), font))
            detailTable.addCell(createCell(row.agingBucket, font))
            detailTable.addCell(createCell(formatYenPlain(row.openAmount), font))
        }
        document.close()
        return outputStream.toByteArray()
    }

    private fun metaCell(text: String, font: PdfFont): Cell =
        Cell()
            .add(Paragraph(text).setFont(font).setFontSize(9f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPaddingBottom(4f)

    private fun formatYenPlain(amount: Double): String = "¥${kotlin.math.abs(amount).toLong()}"

    private fun formatSignedYen(amount: Double): String {
        val sign = when {
            amount < 0 -> "−"
            amount > 0 -> "+"
            else -> ""
        }
        return "$sign¥${kotlin.math.abs(amount).toLong()}"
    }

    /** Converts Gregorian year to Japanese era (令和X年, 平成X年, 昭和X年). First year of era uses 元年. */
    private fun westernYearToJapaneseEra(westernYearStr: String?): String {
        val y = westernYearStr?.trim()?.toIntOrNull() ?: return westernYearStr ?: ""
        return when {
            y >= 2019 -> {
                val n = y - 2018
                if (n == 1) "令和元年" else "令和${n}年"
            }
            y >= 1989 -> {
                val n = y - 1988
                if (n == 1) "平成元年" else "平成${n}年"
            }
            y >= 1926 -> {
                val n = y - 1925
                if (n == 1) "昭和元年" else "昭和${n}年"
            }
            else -> westernYearStr
        }
    }

}
