package com.automan.backend.service

import com.automan.backend.dto.PurchasePageFilterRequest
import com.automan.backend.model.Purchase
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.util.CarModelYearUtils
import com.automan.backend.util.Logger
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class PurchaseExportCellKind {
    STRING,
    NUMERIC,
    INTEGER,
    BOOLEAN,
    DATETIME,
}

private data class PurchaseExportColumnDef(
    val header: String,
    val kind: PurchaseExportCellKind,
    val extract: (Purchase) -> Any?,
)

class NoMatchingPurchasesToExportException :
    RuntimeException("No matching purchases to export")

@Service
class PurchaseExportService(
    private val purchaseRepository: PurchaseRepository,
    private val purchaseService: PurchaseService,
) {
    @Transactional(readOnly = true)
    fun exportAllPurchasesXlsx(): ByteArray {
        val started = System.currentTimeMillis()
        val workbook = SXSSFWorkbook(SXSSF_ROW_WINDOW)
        try {
            val sheet = workbook.createSheet("Purchases")
            val columns = exportColumns()

            val headerRow = sheet.createRow(0)
            columns.forEachIndexed { idx, col ->
                headerRow.createCell(idx).setCellValue(col.header)
            }

            var rowIndex = 1
            var totalRows = 0
            var page = 0
            val sort = Sort.by(Sort.Direction.DESC, "id")

            while (true) {
                val pageable = PageRequest.of(page, EXPORT_PAGE_SIZE, sort)
                val pg = purchaseRepository.findAll(pageable)
                if (pg.isEmpty) break

                rowIndex = writePurchaseRows(sheet, columns, rowIndex, pg.content)
                totalRows += pg.numberOfElements

                if (!pg.hasNext()) break
                page++
            }

            Logger.debug("Purchase XLSX export: %d rows in %d ms", totalRows, System.currentTimeMillis() - started)

            return ByteArrayOutputStream().use { out ->
                workbook.write(out)
                out.toByteArray()
            }
        } finally {
            workbook.dispose()
            workbook.close()
        }
    }

    /**
     * Excel for purchases matching the same filter as the purchase list.
     * Throws [NoMatchingPurchasesToExportException] when nothing matches so callers do not fall back to every row.
     */
    @Transactional(readOnly = true)
    fun exportFilteredPurchasesXlsx(request: PurchasePageFilterRequest): ByteArray {
        val ids = purchaseService.matchingPurchaseIdsForListFilter(request).sortedDescending()
        if (ids.isEmpty()) throw NoMatchingPurchasesToExportException()

        val started = System.currentTimeMillis()
        val workbook = SXSSFWorkbook(SXSSF_ROW_WINDOW)
        try {
            val sheet = workbook.createSheet("Purchases")
            val columns = exportColumns()
            val headerRow = sheet.createRow(0)
            columns.forEachIndexed { idx, col ->
                headerRow.createCell(idx).setCellValue(col.header)
            }

            var rowIndex = 1
            for (chunk in ids.chunked(EXPORT_PAGE_SIZE)) {
                val byId = purchaseRepository.findAllById(chunk).associateBy { it.id }
                val ordered = chunk.mapNotNull { byId[it] }
                rowIndex = writePurchaseRows(sheet, columns, rowIndex, ordered)
            }

            Logger.debug(
                "Purchase XLSX filtered export: %d rows in %d ms",
                ids.size,
                System.currentTimeMillis() - started,
            )
            return ByteArrayOutputStream().use { out ->
                workbook.write(out)
                out.toByteArray()
            }
        } finally {
            workbook.dispose()
            workbook.close()
        }
    }

    private fun writePurchaseRows(
        sheet: Sheet,
        columns: List<PurchaseExportColumnDef>,
        startRow: Int,
        purchases: List<Purchase>,
    ): Int {
        var rowIndex = startRow
        val hydrated = purchaseService.hydratePurchasesForExport(purchases)
        for (purchase in hydrated) {
            val row: Row = sheet.createRow(rowIndex++)
            columns.forEachIndexed { colIdx, col ->
                writeCell(row.createCell(colIdx), col.kind, col.extract(purchase))
            }
        }
        return rowIndex
    }

    private fun writeCell(cell: Cell, kind: PurchaseExportCellKind, raw: Any?) {
        if (raw == null) {
            cell.setBlank()
            return
        }
        when (kind) {
            PurchaseExportCellKind.STRING -> cell.setCellValue(clipExcelText(raw.toString()))
            PurchaseExportCellKind.INTEGER -> {
                cell.cellType = CellType.NUMERIC
                cell.setCellValue((raw as Number).toLong().toDouble())
            }
            PurchaseExportCellKind.NUMERIC -> {
                cell.cellType = CellType.NUMERIC
                when (raw) {
                    is BigDecimal -> cell.setCellValue(raw.toDouble())
                    is Number -> cell.setCellValue(raw.toDouble())
                    else -> {
                        val parsed = parseMoney(raw.toString())
                        if (parsed != null) cell.setCellValue(parsed)
                        else cell.setCellValue(clipExcelText(raw.toString()))
                    }
                }
            }
            PurchaseExportCellKind.BOOLEAN -> cell.setCellValue(raw as Boolean)
            PurchaseExportCellKind.DATETIME -> {
                val text = when (raw) {
                    is LocalDateTime -> TS_FORMAT.format(raw)
                    else -> raw.toString()
                }
                cell.setCellValue(clipExcelText(text))
            }
        }
    }

    /**
     * Excel / Apache POI reject string cells longer than 32,767 chars
     * (common with hydrated Car Pictures JSON). Truncate so export can finish.
     */
    private fun clipExcelText(value: String): String {
        if (value.length <= EXCEL_MAX_CELL_CHARS) return value
        val marker = EXCEL_TRUNCATED_MARKER
        val keep = EXCEL_MAX_CELL_CHARS - marker.length
        return value.take(keep) + marker
    }

    private fun parseMoney(raw: String): Double? =
        PurchaseCostLineService.parseMoneyString(raw)?.toDouble()

    private fun str(p: Purchase, getter: (Purchase) -> String?): String? =
        getter(p)?.trim()?.ifBlank { null }

    private fun money(p: Purchase, getter: (Purchase) -> String?): Double? =
        str(p, getter)?.let { parseMoney(it) }

    private fun registrationYear(p: Purchase): String? =
        CarModelYearUtils.extractYearFromCarModelYear(p.carModelYear).trim().ifBlank { null }

    private fun exportColumns(): List<PurchaseExportColumnDef> = listOf(
        PurchaseExportColumnDef("Date of purchase", PurchaseExportCellKind.STRING) { str(it) { p -> p.date } },
        PurchaseExportColumnDef("Auction no", PurchaseExportCellKind.STRING) { str(it) { p -> p.auctionNo } },
        PurchaseExportColumnDef("Chassis", PurchaseExportCellKind.STRING) { it.chassis },
        PurchaseExportColumnDef("Registration year", PurchaseExportCellKind.STRING) { registrationYear(it) },
        PurchaseExportColumnDef("Car name", PurchaseExportCellKind.STRING) { str(it) { p -> p.carName } },
        PurchaseExportColumnDef("Supplier name", PurchaseExportCellKind.STRING) { str(it) { p -> p.auctionHouse } },
        PurchaseExportColumnDef("Transmission", PurchaseExportCellKind.STRING) { str(it) { p -> p.shift } },
        PurchaseExportColumnDef("Color", PurchaseExportCellKind.STRING) { str(it) { p -> p.color } },
        PurchaseExportColumnDef("Grade", PurchaseExportCellKind.STRING) { str(it) { p -> p.grade } },
        PurchaseExportColumnDef("Car price", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.price } },
        PurchaseExportColumnDef("ID", PurchaseExportCellKind.INTEGER) { it.id },
        PurchaseExportColumnDef("Registration Date", PurchaseExportCellKind.STRING) { str(it) { p -> p.carModelYear } },
        PurchaseExportColumnDef("Manufacture Year", PurchaseExportCellKind.STRING) { str(it) { p -> p.manufactureYear } },
        PurchaseExportColumnDef("Brand", PurchaseExportCellKind.STRING) { str(it) { p -> p.brand } },
        PurchaseExportColumnDef("Vehicle type", PurchaseExportCellKind.STRING) { str(it) { p -> p.shipmentSize } },
        PurchaseExportColumnDef("Rank", PurchaseExportCellKind.STRING) { str(it) { p -> p.rank } },
        PurchaseExportColumnDef("Fuel", PurchaseExportCellKind.STRING) { str(it) { p -> p.fuel } },
        PurchaseExportColumnDef("Seat", PurchaseExportCellKind.STRING) { str(it) { p -> p.seat } },
        PurchaseExportColumnDef("Door", PurchaseExportCellKind.STRING) { str(it) { p -> p.door } },
        PurchaseExportColumnDef("Distance", PurchaseExportCellKind.STRING) { str(it) { p -> p.distance } },
        PurchaseExportColumnDef("Options", PurchaseExportCellKind.STRING) { str(it) { p -> p.options } },
        PurchaseExportColumnDef("CC", PurchaseExportCellKind.INTEGER) { it.cc },
        PurchaseExportColumnDef("WD", PurchaseExportCellKind.STRING) { str(it) { p -> p.wd } },
        PurchaseExportColumnDef("Drive Type", PurchaseExportCellKind.STRING) { str(it) { p -> p.driveType } },
        PurchaseExportColumnDef("Stock Location", PurchaseExportCellKind.STRING) { str(it) { p -> p.stockLocation } },
        PurchaseExportColumnDef("POL", PurchaseExportCellKind.STRING) { str(it) { p -> p.pol } },
        PurchaseExportColumnDef("POD", PurchaseExportCellKind.STRING) { str(it) { p -> p.pod } },
        PurchaseExportColumnDef("Rixo Company", PurchaseExportCellKind.STRING) { str(it) { p -> p.rixoCompany } },
        PurchaseExportColumnDef("Client Name", PurchaseExportCellKind.STRING) { str(it) { p -> p.clientName } },
        PurchaseExportColumnDef("Consignee", PurchaseExportCellKind.STRING) { str(it) { p -> p.consignee } },
        PurchaseExportColumnDef("Client ID", PurchaseExportCellKind.INTEGER) { it.clientId },
        PurchaseExportColumnDef("Target Country", PurchaseExportCellKind.STRING) { str(it) { p -> p.country } },
        PurchaseExportColumnDef("Auction Fee", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.auctionFee } },
        PurchaseExportColumnDef("Auction Penalty Fee", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.auctionPenaltyFee } },
        PurchaseExportColumnDef("Recycle Fee", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.recycleFee } },
        PurchaseExportColumnDef("Road Tax", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.roadTax } },
        PurchaseExportColumnDef("Tax Total", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.taxTotal } },
        PurchaseExportColumnDef("Total Price", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.totalPrice } },
        PurchaseExportColumnDef("Payment Date", PurchaseExportCellKind.STRING) { str(it) { p -> p.paymentDate } },
        PurchaseExportColumnDef("Rixo Requested", PurchaseExportCellKind.STRING) { str(it) { p -> p.rixoRequested } },
        PurchaseExportColumnDef("Rixo Confirmed", PurchaseExportCellKind.STRING) { str(it) { p -> p.rixoConfirmed } },
        PurchaseExportColumnDef("Notes", PurchaseExportCellKind.STRING) { str(it) { p -> p.notes } },
        PurchaseExportColumnDef("Shipment Date", PurchaseExportCellKind.STRING) { str(it) { p -> p.shipmentDate } },
        PurchaseExportColumnDef("BL No", PurchaseExportCellKind.STRING) { str(it) { p -> p.blNo } },
        PurchaseExportColumnDef("Vessel", PurchaseExportCellKind.STRING) { str(it) { p -> p.vessel } },
        PurchaseExportColumnDef("Booking Requested", PurchaseExportCellKind.BOOLEAN) { it.bookingRequested },
        PurchaseExportColumnDef("Sold", PurchaseExportCellKind.BOOLEAN) { it.invoiceConfirmed ?: false },
        PurchaseExportColumnDef("Workflow Status", PurchaseExportCellKind.STRING) { it.workflowStatus?.name },
        PurchaseExportColumnDef("Workflow Status Updated At", PurchaseExportCellKind.DATETIME) { it.workflowStatusUpdatedAt },
        PurchaseExportColumnDef("Shipment Charges", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.shipmentCharges } },
        PurchaseExportColumnDef("Freight", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.freight } },
        PurchaseExportColumnDef("Storage Charges", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.storageCharges } },
        PurchaseExportColumnDef("Misc Charges", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.miscCharges } },
        PurchaseExportColumnDef("Inspection Fee", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.inspectionFee } },
        PurchaseExportColumnDef("Commission", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.commission } },
        PurchaseExportColumnDef("Rixo Price", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.rixoPrice } },
        PurchaseExportColumnDef("Venue ID", PurchaseExportCellKind.STRING) { str(it) { p -> p.venueId } },
        PurchaseExportColumnDef("Number Cut", PurchaseExportCellKind.STRING) { str(it) { p -> p.numberCut } },
        PurchaseExportColumnDef("SHAKEN", PurchaseExportCellKind.BOOLEAN) { it.shaken },
        PurchaseExportColumnDef("NEGOTIATE", PurchaseExportCellKind.BOOLEAN) { it.negotiate },
        PurchaseExportColumnDef("LOCAL", PurchaseExportCellKind.BOOLEAN) { it.local },
        PurchaseExportColumnDef("Repair Company", PurchaseExportCellKind.STRING) { str(it) { p -> p.repairCompany } },
        PurchaseExportColumnDef("Repair Charges", PurchaseExportCellKind.NUMERIC) { money(it) { p -> p.repairCharges } },
        PurchaseExportColumnDef("Profit", PurchaseExportCellKind.NUMERIC) { it.profit },
        PurchaseExportColumnDef("Package Mode", PurchaseExportCellKind.BOOLEAN) { it.isPackageMode },
        PurchaseExportColumnDef("Booking No", PurchaseExportCellKind.INTEGER) { it.bookingId },
        PurchaseExportColumnDef("Car Pictures", PurchaseExportCellKind.STRING) { str(it) { p -> p.carPictures } },
        PurchaseExportColumnDef("Created At", PurchaseExportCellKind.DATETIME) { it.createdAt },
        PurchaseExportColumnDef("Updated At", PurchaseExportCellKind.DATETIME) { it.updatedAt },
    )

    companion object {
        private const val EXPORT_PAGE_SIZE = 500
        private const val SXSSF_ROW_WINDOW = 200
        /** Apache POI / Excel hard limit for text cell content. */
        private const val EXCEL_MAX_CELL_CHARS = 32767
        private const val EXCEL_TRUNCATED_MARKER = "…[truncated]"
        private val TS_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
