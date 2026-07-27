package com.automan.backend.service

import com.automan.backend.dto.DashboardAlertDto
import com.automan.backend.dto.DashboardChartsDto
import com.automan.backend.dto.DashboardKpiDto
import com.automan.backend.dto.DashboardNamedValueDto
import com.automan.backend.dto.DashboardPeriodDto
import com.automan.backend.dto.DashboardPurchaseRowDto
import com.automan.backend.dto.DashboardQuickActionDto
import com.automan.backend.dto.DashboardResponse
import com.automan.backend.dto.DashboardTablesDto
import com.automan.backend.dto.DashboardWorkflowStageDto
import com.automan.backend.model.WorkflowStatus
import com.automan.backend.repository.DashboardPurchaseRowProjection
import com.automan.backend.repository.InvoiceHistoryLineRepository
import com.automan.backend.repository.InvoiceHistoryRepository
import com.automan.backend.repository.PurchaseRepository
import com.automan.backend.repository.ShippingHistoryRepository
import com.automan.backend.util.PurchaseDateParseUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Home dashboard aggregations over purchases (+ shipping history for trend,
 * invoice history for sales-by-client).
 * Purchase period filters use [PurchaseDateParseUtils] (same as Purchase List).
 */
@Service
class DashboardService(
    private val purchaseRepository: PurchaseRepository,
    private val shippingHistoryRepository: ShippingHistoryRepository,
    private val invoiceHistoryRepository: InvoiceHistoryRepository,
    private val invoiceHistoryLineRepository: InvoiceHistoryLineRepository,
) {
    companion object {
        private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        private val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        private val DAY_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        private const val TABLE_LIMIT = 12
        private const val TOP_N = 8
        private const val TOP_CLIENTS = 5

        private val WORKFLOW_ORDER = listOf(
            WorkflowStatus.PURCHASED,
            WorkflowStatus.RIXO_REQUESTED,
            WorkflowStatus.RIXO_CONFIRMED,
            WorkflowStatus.BOOKING_REQUESTED,
            WorkflowStatus.INVOICE_CONFIRMED,
        )

        private val WORKFLOW_LABELS = mapOf(
            WorkflowStatus.PURCHASED to "Purchased",
            WorkflowStatus.RIXO_REQUESTED to "Rixo requested",
            WorkflowStatus.RIXO_CONFIRMED to "Rixo confirmed",
            WorkflowStatus.BOOKING_REQUESTED to "Booking requested",
            WorkflowStatus.INVOICE_CONFIRMED to "Sold",
        )

        private val WORKFLOW_HREFS = mapOf(
            WorkflowStatus.PURCHASED to "/purchase",
            WorkflowStatus.RIXO_REQUESTED to "/rixo-generator",
            WorkflowStatus.RIXO_CONFIRMED to "/booking",
            WorkflowStatus.BOOKING_REQUESTED to "/invoice",
            WorkflowStatus.INVOICE_CONFIRMED to "/invoice-history",
        )

        private val QUICK_ACTIONS = listOf(
            DashboardQuickActionDto("add_purchase", "Add Purchase", "/add"),
            DashboardQuickActionDto("generate_rixo", "Generate Rixo", "/rixo-generator"),
            DashboardQuickActionDto("create_booking", "Create Booking", "/booking"),
            DashboardQuickActionDto("create_invoice", "Create Invoice", "/invoice"),
        )

        fun parseMoney(raw: String?): Double {
            if (raw.isNullOrBlank()) return 0.0
            val cleaned = raw.trim()
                .replace("¥", "")
                .replace("￥", "")
                .replace("$", "")
                .replace(",", "")
                .replace(" ", "")
                .replace(Regex("[^0-9.-]"), "")
            return cleaned.toDoubleOrNull() ?: 0.0
        }
    }

    @Transactional(readOnly = true)
    fun getDashboard(
        periodKey: String?,
        fromRaw: String?,
        toRaw: String?,
    ): DashboardResponse {
        val today = LocalDate.now()
        val period = resolvePeriod(periodKey, fromRaw, toRaw, today)
        val rows = purchaseRepository.findDashboardRows().map { ParsedRow(it) }

        val current = rows.filter { it.parsedDate != null && !it.parsedDate.isBefore(period.from) && !it.parsedDate.isAfter(period.to) }
        val previous = rows.filter {
            it.parsedDate != null &&
                !it.parsedDate.isBefore(period.previousFrom) &&
                !it.parsedDate.isAfter(period.previousTo)
        }

        // Pipeline snapshot (all open stages) — not date-filtered so ops see live backlog.
        val pipeline = rows

        return DashboardResponse(
            period = DashboardPeriodDto(
                key = period.key,
                from = period.from.format(ISO_DATE),
                to = period.to.format(ISO_DATE),
                previousFrom = period.previousFrom.format(ISO_DATE),
                previousTo = period.previousTo.format(ISO_DATE),
            ),
            generatedAt = LocalDateTime.now().toString(),
            kpis = buildKpis(current, previous, pipeline),
            workflow = buildWorkflow(pipeline),
            charts = buildCharts(current, pipeline, today, period),
            tables = buildTables(current, pipeline),
            alerts = buildAlerts(pipeline),
            quickActions = QUICK_ACTIONS,
        )
    }

    private data class PeriodWindow(
        val key: String,
        val from: LocalDate,
        val to: LocalDate,
        val previousFrom: LocalDate,
        val previousTo: LocalDate,
    )

    private data class ParsedRow(
        val id: Long?,
        val dateRaw: String?,
        val parsedDate: LocalDate?,
        val chassis: String?,
        val brand: String?,
        val carName: String?,
        val auctionHouse: String?,
        val clientName: String?,
        val country: String?,
        val totalPriceRaw: String?,
        val totalPrice: Double,
        val status: WorkflowStatus,
    ) {
        constructor(p: DashboardPurchaseRowProjection) : this(
            id = p.getId(),
            dateRaw = p.getDate(),
            parsedDate = PurchaseDateParseUtils.parseToLocalDate(p.getDate()?.trim().orEmpty()),
            chassis = p.getChassis(),
            brand = p.getBrand(),
            carName = p.getCarName(),
            auctionHouse = p.getAuctionHouse(),
            clientName = p.getClientName(),
            country = p.getCountry(),
            totalPriceRaw = p.getTotalPrice(),
            totalPrice = parseMoney(p.getTotalPrice()),
            status = p.getWorkflowStatus() ?: WorkflowStatus.PURCHASED,
        )

        val modelLabel: String
            get() {
                val b = brand?.trim().orEmpty()
                val n = carName?.trim().orEmpty()
                return when {
                    b.isNotEmpty() && n.isNotEmpty() -> "$b $n"
                    n.isNotEmpty() -> n
                    b.isNotEmpty() -> b
                    else -> "—"
                }
            }
    }

    private fun resolvePeriod(
        periodKey: String?,
        fromRaw: String?,
        toRaw: String?,
        today: LocalDate,
    ): PeriodWindow {
        val key = periodKey?.trim()?.lowercase(Locale.ROOT).orEmpty().ifEmpty { "this_month" }
        return when (key) {
            "today" -> {
                val prev = today.minusDays(1)
                PeriodWindow(key, today, today, prev, prev)
            }
            "this_week" -> {
                val from = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                val to = today
                val days = ChronoUnit.DAYS.between(from, to).toInt() + 1
                val previousTo = from.minusDays(1)
                val previousFrom = previousTo.minusDays((days - 1).toLong())
                PeriodWindow(key, from, to, previousFrom, previousTo)
            }
            "last_month" -> {
                val ym = YearMonth.from(today).minusMonths(1)
                val from = ym.atDay(1)
                val to = ym.atEndOfMonth()
                val prevYm = ym.minusMonths(1)
                PeriodWindow(key, from, to, prevYm.atDay(1), prevYm.atEndOfMonth())
            }
            "this_year" -> {
                val from = LocalDate.of(today.year, 1, 1)
                val to = today
                val prevFrom = LocalDate.of(today.year - 1, 1, 1)
                val prevTo = LocalDate.of(today.year - 1, 12, 31)
                PeriodWindow(key, from, to, prevFrom, prevTo)
            }
            "custom" -> {
                val from = parseIso(fromRaw) ?: today.withDayOfMonth(1)
                val to = parseIso(toRaw) ?: today
                val start = if (from.isAfter(to)) to else from
                val end = if (from.isAfter(to)) from else to
                val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
                val previousTo = start.minusDays(1)
                val previousFrom = previousTo.minusDays((days - 1).toLong())
                PeriodWindow("custom", start, end, previousFrom, previousTo)
            }
            else -> {
                // this_month (default)
                val from = today.withDayOfMonth(1)
                val to = today
                val prevYm = YearMonth.from(today).minusMonths(1)
                val prevFrom = prevYm.atDay(1)
                val prevTo = prevYm.atEndOfMonth()
                PeriodWindow("this_month", from, to, prevFrom, prevTo)
            }
        }
    }

    private fun parseIso(raw: String?): LocalDate? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return try {
            LocalDate.parse(t, ISO_DATE)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildKpis(
        current: List<ParsedRow>,
        previous: List<ParsedRow>,
        pipeline: List<ParsedRow>,
    ): List<DashboardKpiDto> {
        val purchaseCount = current.size.toDouble()
        val prevPurchaseCount = previous.size.toDouble()
        val purchaseValue = current.sumOf { it.totalPrice }
        val prevPurchaseValue = previous.sumOf { it.totalPrice }
        val unshipped = pipeline.count {
            it.status == WorkflowStatus.PURCHASED ||
                it.status == WorkflowStatus.RIXO_REQUESTED ||
                it.status == WorkflowStatus.RIXO_CONFIRMED
        }.toDouble()
        val prevUnshipped = previous.count {
            it.status == WorkflowStatus.PURCHASED ||
                it.status == WorkflowStatus.RIXO_REQUESTED ||
                it.status == WorkflowStatus.RIXO_CONFIRMED
        }.toDouble()
        val sold = current.count { it.status == WorkflowStatus.INVOICE_CONFIRMED }.toDouble()
        val prevSold = previous.count { it.status == WorkflowStatus.INVOICE_CONFIRMED }.toDouble()
        val rixoPending = pipeline.count { it.status == WorkflowStatus.RIXO_REQUESTED }.toDouble()
        val prevRixoPending = previous.count { it.status == WorkflowStatus.RIXO_REQUESTED }.toDouble()
        val bookingPending = pipeline.count { it.status == WorkflowStatus.RIXO_CONFIRMED }.toDouble()
        val prevBookingPending = previous.count { it.status == WorkflowStatus.RIXO_CONFIRMED }.toDouble()
        val activeClients = current.mapNotNull { it.clientName?.trim()?.takeIf { n -> n.isNotEmpty() } }.toSet().size.toDouble()
        val prevActiveClients = previous.mapNotNull { it.clientName?.trim()?.takeIf { n -> n.isNotEmpty() } }.toSet().size.toDouble()
        val avgPrice = if (purchaseCount > 0) purchaseValue / purchaseCount else 0.0
        val prevAvg = if (prevPurchaseCount > 0) prevPurchaseValue / prevPurchaseCount else 0.0

        return listOf(
            kpi("purchases", "Purchases", purchaseCount, prevPurchaseCount, "/purchase", "number"),
            kpi("purchase_value", "Purchase value", purchaseValue, prevPurchaseValue, "/purchase", "currency"),
            kpi("unshipped", "Unshipped cars", unshipped, prevUnshipped, "/booking", "number"),
            kpi("sold", "Sold cars", sold, prevSold, "/invoice-history", "number"),
            kpi("rixo_pending", "Rixo pending", rixoPending, prevRixoPending, "/rixo-generator", "number"),
            kpi("booking_pending", "Booking pending", bookingPending, prevBookingPending, "/booking", "number"),
            kpi("active_clients", "Active clients", activeClients, prevActiveClients, "/master/client-transactions", "number"),
            kpi("avg_price", "Avg purchase price", avgPrice, prevAvg, "/purchase", "currency"),
        )
    }

    private fun kpi(
        id: String,
        label: String,
        value: Double,
        previous: Double,
        href: String,
        format: String,
    ): DashboardKpiDto {
        val delta = when {
            previous == 0.0 && value == 0.0 -> null
            previous == 0.0 -> null
            else -> ((value - previous) / previous) * 100.0
        }
        return DashboardKpiDto(
            id = id,
            label = label,
            value = value,
            displayValue = formatDisplay(value, format),
            previousValue = previous,
            deltaPct = delta?.let { (it * 10.0).roundToLong() / 10.0 },
            href = href,
            format = format,
        )
    }

    private fun buildWorkflow(pipeline: List<ParsedRow>): List<DashboardWorkflowStageDto> {
        val total = pipeline.size.toDouble().coerceAtLeast(1.0)
        val counts = pipeline.groupingBy { it.status }.eachCount()
        return WORKFLOW_ORDER.map { status ->
            val count = counts[status] ?: 0
            DashboardWorkflowStageDto(
                status = status.name,
                label = WORKFLOW_LABELS[status] ?: status.name,
                count = count.toLong(),
                pct = ((count / total) * 1000.0).roundToLong() / 10.0,
                href = WORKFLOW_HREFS[status] ?: "/purchase",
            )
        }
    }

    private fun buildCharts(
        current: List<ParsedRow>,
        pipeline: List<ParsedRow>,
        today: LocalDate,
        period: PeriodWindow,
    ): DashboardChartsDto {
        val monthsBack = 11
        val monthKeys = (monthsBack downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
        // Monthly series use all rows (not only current period) for trend context.
        val allDated = pipeline.filter { it.parsedDate != null }
        val monthlyPurchases = monthKeys.map { ym ->
            val n = allDated.count { YearMonth.from(it.parsedDate!!) == ym }
            DashboardNamedValueDto(ym.format(MONTH_LABEL), n.toDouble())
        }
        val purchaseValueTrend = monthKeys.map { ym ->
            val sum = allDated.filter { YearMonth.from(it.parsedDate!!) == ym }.sumOf { it.totalPrice }
            DashboardNamedValueDto(ym.format(MONTH_LABEL), sum)
        }
        val monthlyRevenue = monthKeys.map { ym ->
            val sum = allDated
                .filter { it.status == WorkflowStatus.INVOICE_CONFIRMED && YearMonth.from(it.parsedDate!!) == ym }
                .sumOf { it.totalPrice }
            DashboardNamedValueDto(ym.format(MONTH_LABEL), sum)
        }

        val workflowDistribution = WORKFLOW_ORDER.map { status ->
            val n = pipeline.count { it.status == status }
            DashboardNamedValueDto(WORKFLOW_LABELS[status] ?: status.name, n.toDouble())
        }

        val topModels = current
            .groupingBy { it.modelLabel }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(TOP_N)
            .map { DashboardNamedValueDto(it.key, it.value.toDouble()) }

        val countryDistribution = current
            .mapNotNull { it.country?.trim()?.takeIf { c -> c.isNotEmpty() } ?: "Unknown" }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(TOP_N)
            .map { DashboardNamedValueDto(it.key, it.value.toDouble()) }

        val shipFrom = today.minusDays(29)
        val shipDates = try {
            shippingHistoryRepository.findShipmentDatesBetween(shipFrom, today)
        } catch (_: Exception) {
            emptyList()
        }
        val shipCounts = shipDates.groupingBy { it }.eachCount()
        val shippingTrend = (0L..29L).map { offset ->
            val d = shipFrom.plusDays(offset)
            DashboardNamedValueDto(d.format(DAY_LABEL), (shipCounts[d] ?: 0).toDouble())
        }

        return DashboardChartsDto(
            monthlyPurchases = monthlyPurchases,
            purchaseValueTrend = purchaseValueTrend,
            workflowDistribution = workflowDistribution,
            topModels = topModels,
            countryDistribution = countryDistribution,
            monthlyRevenue = monthlyRevenue,
            shippingTrend = shippingTrend,
            salesByClient = buildSalesByClient(period),
        )
    }

    /**
     * Invoice History sales by client for the Home period:
     * sum [invoice_history_line.line_amount] grouped by [invoice_history.client_name].
     * Include an invoice when its shipping_date OR created_at (date) falls in the period
     * (matches Invoice History rows users recently saved, even if shipping date is older).
     * Top 5 + Others; zero-amount clients omitted.
     */
    private fun buildSalesByClient(period: PeriodWindow): List<DashboardNamedValueDto> {
        val headers = try {
            invoiceHistoryRepository.findAll()
        } catch (_: Exception) {
            return emptyList()
        }
        val inPeriod = headers.mapNotNull { h ->
            val id = h.id ?: return@mapNotNull null
            val shipping = h.shippingDate
            val created = h.createdAt?.toLocalDate()
            val shippingIn = shipping != null && !shipping.isBefore(period.from) && !shipping.isAfter(period.to)
            val createdIn = created != null && !created.isBefore(period.from) && !created.isAfter(period.to)
            if (!shippingIn && !createdIn) return@mapNotNull null
            id to (h.clientName?.trim()?.takeIf { it.isNotEmpty() } ?: "Unknown")
        }
        if (inPeriod.isEmpty()) return emptyList()

        val idToClient = inPeriod.toMap()
        val lines = try {
            invoiceHistoryLineRepository.findByInvoiceHistoryIdIn(idToClient.keys)
        } catch (_: Exception) {
            return emptyList()
        }

        val totals = mutableMapOf<String, Double>()
        for (line in lines) {
            val client = idToClient[line.invoiceHistoryId] ?: continue
            totals[client] = (totals[client] ?: 0.0) + parseMoney(line.lineAmount)
        }

        val ranked = totals.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
        if (ranked.isEmpty()) return emptyList()

        val top = ranked.take(TOP_CLIENTS)
        val othersSum = ranked.drop(TOP_CLIENTS).sumOf { it.value }
        val result = top.map { DashboardNamedValueDto(it.key, it.value) }.toMutableList()
        if (othersSum > 0.0) {
            result += DashboardNamedValueDto("Others", othersSum)
        }
        return result
    }

    private fun buildTables(
        current: List<ParsedRow>,
        pipeline: List<ParsedRow>,
    ): DashboardTablesDto {
        fun toDto(r: ParsedRow) = DashboardPurchaseRowDto(
            id = r.id,
            date = r.dateRaw,
            chassis = r.chassis,
            model = r.modelLabel,
            auction = r.auctionHouse,
            client = r.clientName,
            workflowStatus = r.status.name,
            workflowLabel = WORKFLOW_LABELS[r.status],
            totalPrice = r.totalPriceRaw,
            country = r.country,
        )

        val recent = current
            .sortedWith(
                compareByDescending<ParsedRow> { it.parsedDate ?: LocalDate.MIN }
                    .thenByDescending { it.id ?: 0L },
            )
            .take(TABLE_LIMIT)
            .map(::toDto)

        fun waiting(status: WorkflowStatus) =
            pipeline
                .filter { it.status == status }
                .sortedWith(compareByDescending { it.parsedDate ?: LocalDate.MIN })
                .take(TABLE_LIMIT)
                .map(::toDto)

        return DashboardTablesDto(
            recentPurchases = recent,
            waitingRixo = waiting(WorkflowStatus.RIXO_REQUESTED),
            waitingBooking = waiting(WorkflowStatus.RIXO_CONFIRMED),
            waitingInvoice = waiting(WorkflowStatus.BOOKING_REQUESTED),
        )
    }

    private fun buildAlerts(pipeline: List<ParsedRow>): List<DashboardAlertDto> {
        val rixo = pipeline.count { it.status == WorkflowStatus.RIXO_REQUESTED }.toLong()
        val booking = pipeline.count { it.status == WorkflowStatus.RIXO_CONFIRMED }.toLong()
        val invoice = pipeline.count { it.status == WorkflowStatus.BOOKING_REQUESTED }.toLong()
        val missingClient = pipeline.count {
            it.status != WorkflowStatus.INVOICE_CONFIRMED && it.clientName.isNullOrBlank()
        }.toLong()

        val alerts = mutableListOf<DashboardAlertDto>()
        if (rixo > 0) {
            alerts += DashboardAlertDto(
                severity = if (rixo >= 20) "critical" else "warning",
                code = "rixo_pending",
                message = "Cars waiting for Rixo confirmation",
                count = rixo,
                href = "/rixo-history",
            )
        }
        if (booking > 0) {
            alerts += DashboardAlertDto(
                severity = if (booking >= 20) "critical" else "warning",
                code = "booking_pending",
                message = "Cars waiting for booking",
                count = booking,
                href = "/booking",
            )
        }
        if (invoice > 0) {
            alerts += DashboardAlertDto(
                severity = "info",
                code = "invoice_pending",
                message = "Cars waiting for invoice",
                count = invoice,
                href = "/shipping-history",
            )
        }
        if (missingClient > 0) {
            alerts += DashboardAlertDto(
                severity = "warning",
                code = "missing_client",
                message = "Purchases missing client name",
                count = missingClient,
                href = "/purchase",
            )
        }
        return alerts
    }

    private fun formatDisplay(value: Double, format: String): String =
        when (format) {
            "currency" -> {
                val rounded = (value * 100.0).roundToLong() / 100.0
                if (rounded == rounded.toLong().toDouble()) {
                    "¥${String.format(Locale.US, "%,d", rounded.toLong())}"
                } else {
                    "¥${String.format(Locale.US, "%,.2f", rounded)}"
                }
            }
            else -> String.format(Locale.US, "%,d", value.roundToLong())
        }
}
