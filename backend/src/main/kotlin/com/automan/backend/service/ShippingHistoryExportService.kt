package com.automan.backend.service

import com.automan.backend.model.ShippingHistory
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.util.Logger
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class ShippingHistoryExportCellKind {
    STRING,
    NUMERIC,
    INTEGER,
    DATETIME,
}

private data class ShippingHistoryExportColumnDef(
    val header: String,
    val kind: ShippingHistoryExportCellKind,
    val extract: (ShippingHistory) -> Any?,
)

@Service
class ShippingHistoryExportService(
    private val shippingHistoryRepository: ShippingHistoryRepository,
) {
    @Transactional(readOnly = true)
    fun exportAllShippingHistoryXlsx(): ByteArray {
        val started = System.currentTimeMillis()
        val workbook = SXSSFWorkbook(SXSSF_ROW_WINDOW)
        try {
            val sheet = workbook.createSheet("Shipping History")
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
                val pg = shippingHistoryRepository.findAll(pageable)
                if (pg.isEmpty) break

                for (entity in pg.content) {
                    val row = sheet.createRow(rowIndex++)
                    columns.forEachIndexed { colIdx, col ->
                        writeCell(row.createCell(colIdx), col.kind, col.extract(entity))
                    }
                    totalRows++
                }

                if (!pg.hasNext()) break
                page++
            }

            Logger.debug(
                "Shipping History XLSX export: %d rows in %d ms",
                totalRows,
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

    private fun writeCell(cell: Cell, kind: ShippingHistoryExportCellKind, raw: Any?) {
        if (raw == null) {
            cell.setBlank()
            return
        }
        when (kind) {
            ShippingHistoryExportCellKind.STRING -> cell.setCellValue(clipExcelText(raw.toString()))
            ShippingHistoryExportCellKind.INTEGER -> {
                cell.cellType = CellType.NUMERIC
                cell.setCellValue((raw as Number).toLong().toDouble())
            }
            ShippingHistoryExportCellKind.NUMERIC -> {
                cell.cellType = CellType.NUMERIC
                when (raw) {
                    is BigDecimal -> cell.setCellValue(raw.toDouble())
                    is Number -> cell.setCellValue(raw.toDouble())
                    else -> cell.setCellValue(clipExcelText(raw.toString()))
                }
            }
            ShippingHistoryExportCellKind.DATETIME -> {
                val text = when (raw) {
                    is LocalDateTime -> TS_FORMAT.format(raw)
                    is LocalDate -> raw.toString()
                    else -> raw.toString()
                }
                cell.setCellValue(clipExcelText(text))
            }
        }
    }

    private fun clipExcelText(value: String): String {
        if (value.length <= EXCEL_MAX_CELL_CHARS) return value
        val marker = EXCEL_TRUNCATED_MARKER
        val keep = EXCEL_MAX_CELL_CHARS - marker.length
        return value.take(keep) + marker
    }

    private fun str(getter: () -> String?): String? =
        getter()?.trim()?.ifBlank { null }

    private fun exportColumns(): List<ShippingHistoryExportColumnDef> = listOf(
        ShippingHistoryExportColumnDef("ID", ShippingHistoryExportCellKind.INTEGER) { it.id },
        ShippingHistoryExportColumnDef("Country", ShippingHistoryExportCellKind.STRING) { str { it.country } },
        ShippingHistoryExportColumnDef("Consignee", ShippingHistoryExportCellKind.STRING) { str { it.consignee } },
        ShippingHistoryExportColumnDef("Shipment Date", ShippingHistoryExportCellKind.DATETIME) { it.shipmentDate },
        ShippingHistoryExportColumnDef("POL", ShippingHistoryExportCellKind.STRING) { str { it.pol } },
        ShippingHistoryExportColumnDef("Stock Location", ShippingHistoryExportCellKind.STRING) { str { it.stockLocation } },
        ShippingHistoryExportColumnDef("POD", ShippingHistoryExportCellKind.STRING) { str { it.pod } },
        ShippingHistoryExportColumnDef("Booking ID", ShippingHistoryExportCellKind.STRING) { str { it.bookingId } },
        ShippingHistoryExportColumnDef("Vessel", ShippingHistoryExportCellKind.STRING) { str { it.vessel } },
        ShippingHistoryExportColumnDef("Carrier", ShippingHistoryExportCellKind.STRING) { str { it.carrier } },
        ShippingHistoryExportColumnDef("BL No", ShippingHistoryExportCellKind.STRING) { str { it.blNo } },
        ShippingHistoryExportColumnDef("Price Type", ShippingHistoryExportCellKind.STRING) { str { it.priceType } },
        ShippingHistoryExportColumnDef("Chassis", ShippingHistoryExportCellKind.STRING) { it.chassis },
        ShippingHistoryExportColumnDef("Client Name", ShippingHistoryExportCellKind.STRING) { str { it.clientName } },
        ShippingHistoryExportColumnDef("Amount", ShippingHistoryExportCellKind.NUMERIC) { it.amount },
        ShippingHistoryExportColumnDef("Created At", ShippingHistoryExportCellKind.DATETIME) { it.createdAt },
    )

    companion object {
        private const val EXPORT_PAGE_SIZE = 500
        private const val SXSSF_ROW_WINDOW = 200
        private const val EXCEL_MAX_CELL_CHARS = 32767
        private const val EXCEL_TRUNCATED_MARKER = "…[truncated]"
        private val TS_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    }
}
