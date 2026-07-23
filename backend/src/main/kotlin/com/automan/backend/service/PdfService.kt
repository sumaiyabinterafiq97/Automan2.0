package com.automan.backend.service

import com.automan.backend.model.Purchase
import com.automan.backend.dto.ClientStatementDto
import com.automan.backend.dto.InvoicePdfRequest
import com.automan.backend.dto.ShippingSchedulePdfData
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
        /** Default footer for MEMON-style shipping-schedule invoice when no message is provided. */
        private val DEFAULT_SHIPPING_CARGO_NOTE = listOf(
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

    private fun getVerdanaFont(bold: Boolean = false): PdfFont {
        val resource = if (bold) "fonts/Verdana-Bold.ttf" else "fonts/Verdana.ttf"
        return try {
            val fontStream: InputStream = javaClass.classLoader.getResourceAsStream(resource)
                ?: throw Exception("Font not found: $resource")
            val bytes = fontStream.readBytes()
            fontStream.close()
            PdfFontFactory.createFont(bytes, PdfEncodings.IDENTITY_H)
        } catch (e: Exception) {
            Logger.warn("Verdana font failed (${e.message}); falling back to Japanese/default font")
            getJapaneseFont()
        }
    }

    /** Classic Create Invoice PDF (Invoice No / Description / Amount). */
    fun generateInvoicePdf(request: InvoicePdfRequest): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)

        val font = getJapaneseFont()
        document.setFont(font)

        val dash = { v: String? -> v?.trim()?.takeIf { it.isNotEmpty() } ?: "-" }

        document.add(
            Paragraph("INVOICE")
                .setFontSize(20f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(16f),
        )

        val meta = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(14f)

        val left = Cell()
            .add(Paragraph("Invoice No: ${dash(request.invoiceNumber)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("Invoice Date: ${dash(request.invoiceDate)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("LC No: ${dash(request.lcNumber)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("Client: ${dash(request.clientName)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("Client Address: ${dash(request.clientAddress)}").setFontSize(10f).setFixedLeading(14f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(0f)
            .setPaddingRight(8f)
        meta.addCell(left)

        val right = Cell()
            .add(Paragraph("Vessel: ${dash(request.vessel)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("Shipping Date: ${dash(request.shippingDate)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("From: ${dash(request.from)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("To: ${dash(request.to)}").setFontSize(10f).setFixedLeading(14f))
            .add(Paragraph("Price Type: ${dash(request.priceType)}").setFontSize(10f).setFixedLeading(14f))
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(0f)
        meta.addCell(right)
        document.add(meta)

        val table = Table(UnitValue.createPercentArray(floatArrayOf(10f, 65f, 25f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginTop(4f)

        listOf("No.", "Description", "Amount").forEach {
            table.addHeaderCell(
                Cell()
                    .add(Paragraph(it).setBold().setFontSize(10f).setTextAlignment(TextAlignment.CENTER))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
                    .setPadding(6f)
                    .setBorder(com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 1f)),
            )
        }

        request.items.forEach { item ->
            table.addCell(
                createCell(item.unit.toString(), font)
                    .setFontSize(9f)
                    .setTextAlignment(TextAlignment.CENTER),
            )
            table.addCell(createCell(item.description, font).setFontSize(9f))
            table.addCell(
                createCell(item.amount, font)
                    .setFontSize(9f)
                    .setTextAlignment(TextAlignment.RIGHT),
            )
        }
        document.add(table)

        document.add(
            Paragraph("Total Amount: ${request.totalAmount}")
                .setBold()
                .setFontSize(12f)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(12f),
        )

        request.bankAccount?.trim()?.takeIf { it.isNotEmpty() }?.let {
            document.add(Paragraph("Bank Account: $it").setFontSize(9f).setMarginTop(12f))
        }
        request.message?.trim()?.takeIf { it.isNotEmpty() }?.let {
            document.add(Paragraph(it).setFontSize(9f).setMarginTop(8f))
        }

        document.close()
        return outputStream.toByteArray()
    }

    /**
     * Booking / shipping-schedule PDF in MEMON client layout (Verdana).
     * Used by both C&F and FOB shipping-schedule endpoints.
     */
    fun generateShippingScheduleInvoicePdf(data: ShippingSchedulePdfData): ByteArray {
        val outputStream = ByteArrayOutputStream()
        val pdfWriter = PdfWriter(outputStream)
        val pdfDocument = PdfDocument(pdfWriter)
        val document = Document(pdfDocument)
        document.setMargins(40f, 40f, 40f, 40f)

        val font = getVerdanaFont(false)
        val fontBold = getVerdanaFont(true)
        document.setFont(font)

        val dash = { v: String? -> v?.trim()?.takeIf { it.isNotEmpty() } ?: "-" }
        val noBorder = com.itextpdf.layout.borders.Border.NO_BORDER
        val thinBorder = com.itextpdf.layout.borders.SolidBorder(ColorConstants.BLACK, 0.6f)

        fun rule(marginTop: Float = 2f, marginBottom: Float = 2f): LineSeparator {
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

        fun itemCell(
            text: String,
            align: TextAlignment = TextAlignment.LEFT,
            bold: Boolean = false,
            top: Boolean = false,
            bottom: Boolean = false,
        ): Cell {
            val p = Paragraph(text)
                .setFont(if (bold) fontBold else font)
                .setFontSize(9f)
                .setTextAlignment(align)
                .setFixedLeading(11f)
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

        val topTable = Table(UnitValue.createPercentArray(floatArrayOf(70f, 30f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(6f)
        topTable.addCell(
            Cell()
                .add(Paragraph("MEMON CO., LTD").setFont(fontBold).setFontSize(12f).setBold())
                .add(
                    Paragraph(
                        "#112 taiyo mansion, 3-6-1 gyotoku ekimae, Ichikawa-Shi,\n" +
                            "Chiba-Ken. 272-0133\n" +
                            "Tel: +81-47-701-3770  Fax: +81-47-701-3771\n" +
                            "E-Mail: memonco@ymail.com",
                    ).setFont(font).setFontSize(8f).setFixedLeading(10f),
                )
                .setBorder(noBorder)
                .setPadding(0f),
        )
        topTable.addCell(
            Cell()
                .add(
                    Paragraph()
                        .add(Text("DATE ").setFont(fontBold).setBold())
                        .add(formatInvoiceDisplayDate(data.shippingDate))
                        .setFont(font)
                        .setFontSize(10f)
                        .setTextAlignment(TextAlignment.RIGHT),
                )
                .setBorder(noBorder)
                .setPadding(0f),
        )
        document.add(topTable)

        document.add(
            Paragraph("INVOICE")
                .setFont(fontBold)
                .setFontSize(18f)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setUnderline()
                .setMarginBottom(10f),
        )

        val metaTable = Table(UnitValue.createPercentArray(floatArrayOf(48f, 52f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginBottom(6f)

        val consigneeText = data.consigneeDetails.name?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        val consigneeAddress = data.consigneeDetails.address?.trim()?.takeIf { it.isNotEmpty() }
        val notifyText = data.notifyParty?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
        val leftConsignee = Cell()
            .add(Paragraph().add(Text("CONSIGNEE").setFont(fontBold).setBold()).setFontSize(9f).setMarginBottom(2f))
            .add(Paragraph(consigneeText).setFont(fontBold).setBold().setFontSize(9f).setFixedLeading(11f).setMarginBottom(2f))
        if (consigneeAddress != null) {
            leftConsignee.add(
                Paragraph(consigneeAddress).setFont(font).setFontSize(8f).setFixedLeading(10f).setMarginBottom(8f),
            )
        } else {
            leftConsignee.add(Paragraph("").setMarginBottom(8f))
        }
        leftConsignee
            .add(
                Paragraph()
                    .add(Text("NOTIFY PARTY ").setFont(fontBold).setBold())
                    .add(notifyText)
                    .setFont(font)
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
                    .add(Text("VESSEL: ").setFont(fontBold).setBold())
                    .add(dash(data.vesselName))
                    .setFont(font)
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("BOOKING NO. ").setFont(fontBold).setBold())
                    .add(dash(data.bookingNo))
                    .add("     ")
                    .add(Text("CARRIER: ").setFont(fontBold).setBold())
                    .add(dash(data.carrier))
                    .setFont(font)
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(Cell().add(rule(4f, 4f)).setBorder(noBorder).setPadding(0f))
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("CY CUT: ").setFont(fontBold).setBold())
                    .add(formatInvoiceShortDate(data.cyCutDate))
                    .add(" | ")
                    .add(Text("ETD: ").setFont(fontBold).setBold())
                    .add(formatInvoiceShortDate(data.shippingDate))
                    .add(" | ")
                    .add(Text("ETA: ").setFont(fontBold).setBold())
                    .add(formatInvoiceEtaDate(data.eta))
                    .setFont(font)
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(Cell().add(rule(4f, 4f)).setBorder(noBorder).setPadding(0f))
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("POL: ").setFont(fontBold).setBold())
                    .add(dash(data.pol))
                    .add("     ")
                    .add(Text("POD: ").setFont(fontBold).setBold())
                    .add(dash(data.pod))
                    .setFont(font)
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        shipBox.addCell(
            openCell(
                Paragraph()
                    .add(Text("FINAL DESTINATION: ").setFont(fontBold).setBold())
                    .add(dash(data.finalDestination))
                    .setFont(font)
                    .setFontSize(8f)
                    .setFixedLeading(11f),
            ),
        )
        metaTable.addCell(Cell().add(shipBox).setBorder(noBorder).setPadding(0f))
        document.add(metaTable)

        val tradeTerms = when (data.calculationMode?.trim()?.uppercase()) {
            "FOB" -> "FOB"
            "C&F", "CNF" -> "C&F"
            else -> data.calculationMode?.trim()?.takeIf { it.isNotEmpty() } ?: "C&F"
        }
        document.add(rule(8f, 3f))
        document.add(
            Paragraph()
                .add(Text("TRADE TERMS: ").setFont(fontBold).setBold())
                .add(tradeTerms)
                .setFont(font)
                .setFontSize(10f),
        )
        document.add(rule(3f, 6f))

        // Refined columns: No | Maker | Model | Chassis | Year | Amount
        val table = Table(UnitValue.createPercentArray(floatArrayOf(6f, 14f, 20f, 26f, 12f, 22f)))
            .setWidth(UnitValue.createPercentValue(100f))
            .setMarginTop(2f)

        listOf("", "MAKER |", "MODEL |", "CHASIS NO. |", "YEAR |", "AMOUNT JPY").forEachIndexed { index, label ->
            val align = when (index) {
                0 -> TextAlignment.CENTER
                5 -> TextAlignment.RIGHT
                else -> TextAlignment.LEFT
            }
            table.addHeaderCell(itemCell(label, align, bold = true, top = true, bottom = true))
        }

        var grandTotal = 0L
        data.carList.forEach { car ->
            val maker = car.maker?.trim()?.takeIf { it.isNotEmpty() } ?: "-"
            val model = car.model?.trim()?.takeIf { it.isNotEmpty() }
                ?: car.name.trim().takeIf { it.isNotEmpty() }
                ?: "-"
            val chassis = car.chassisNumber.trim().ifEmpty { "-" }
            val year = car.year.trim().ifEmpty { "-" }
            val amount = car.cnfPrice.trim().ifEmpty { "¥0" }
            grandTotal += parseYenAmountToLong(amount)

            table.addCell(itemCell(car.no.toString(), TextAlignment.CENTER))
            table.addCell(itemCell(maker))
            table.addCell(itemCell(model))
            table.addCell(itemCell(chassis))
            table.addCell(itemCell(year, TextAlignment.CENTER))
            table.addCell(itemCell(amount, TextAlignment.RIGHT))
        }
        document.add(table)

        val units = data.carList.size
        val unitsLabel = if (units == 1) "1 UNIT" else "$units UNITS"
        val grandFormatted = "¥${"%,d".format(grandTotal)}"
        document.add(rule(4f, 4f))
        val footer = Table(UnitValue.createPercentArray(floatArrayOf(50f, 50f)))
            .setWidth(UnitValue.createPercentValue(100f))
        footer.addCell(
            Cell()
                .add(Paragraph(unitsLabel).setFont(fontBold).setBold().setFontSize(11f))
                .setBorder(noBorder)
                .setPadding(2f),
        )
        footer.addCell(
            Cell()
                .add(
                    Paragraph("GRAND TOTAL $grandFormatted")
                        .setFont(fontBold)
                        .setBold()
                        .setFontSize(11f)
                        .setTextAlignment(TextAlignment.RIGHT),
                )
                .setBorder(noBorder)
                .setPadding(2f),
        )
        document.add(footer)
        document.add(rule(4f, 10f))

        val cargoNoteLines = data.inTransitClause?.trim()?.takeIf { it.isNotEmpty() }
            ?.lines()
            ?.map { it.trimEnd() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_SHIPPING_CARGO_NOTE

        cargoNoteLines.forEach { line ->
            document.add(
                Paragraph(line)
                    .setFont(font)
                    .setFontSize(9f)
                    .setFixedLeading(12f)
                    .setMarginBottom(0f),
            )
        }

        document.close()
        return outputStream.toByteArray()
    }

    private fun parseYenAmountToLong(raw: String): Long {
        val digits = raw.replace(Regex("[^0-9-]"), "")
        return digits.toLongOrNull() ?: 0L
    }

    private fun formatInvoiceDisplayDate(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "-"
        // Already like 27.SEP.2025
        if (s.contains(".")) {
            return try {
                val parts = s.split(".")
                if (parts.size >= 3) {
                    val day = parts[0].toInt()
                    val month = java.time.Month.valueOf(
                        when (parts[1].uppercase()) {
                            "JAN" -> "JANUARY"
                            "FEB" -> "FEBRUARY"
                            "MAR" -> "MARCH"
                            "APR" -> "APRIL"
                            "MAY" -> "MAY"
                            "JUN" -> "JUNE"
                            "JUL" -> "JULY"
                            "AUG" -> "AUGUST"
                            "SEP" -> "SEPTEMBER"
                            "OCT" -> "OCTOBER"
                            "NOV" -> "NOVEMBER"
                            "DEC" -> "DECEMBER"
                            else -> return s
                        },
                    )
                    val year = parts[2].toInt()
                    LocalDate.of(year, month, day)
                        .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH))
                } else s
            } catch (_: Exception) {
                s
            }
        }
        val iso = s.take(10)
        return try {
            val d = LocalDate.parse(iso)
            d.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH))
        } catch (_: Exception) {
            s.ifEmpty { "-" }
        }
    }

    private fun formatInvoiceShortDate(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "-"
        // Already display-formatted shipping dates like 27.SEP.2025
        if (s.contains(".") || (s.contains(" ") && !s.contains("-"))) return s
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
        if (s.contains(".") || s.contains(" ")) return s.uppercase()
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
