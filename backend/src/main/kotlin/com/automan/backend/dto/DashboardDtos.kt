package com.automan.backend.dto

/**
 * Single-payload Home dashboard response.
 * Period filters apply to [Purchase.date] via [com.automan.backend.util.PurchaseDateParseUtils].
 */
data class DashboardResponse(
    val period: DashboardPeriodDto,
    val generatedAt: String,
    val kpis: List<DashboardKpiDto>,
    val workflow: List<DashboardWorkflowStageDto>,
    val charts: DashboardChartsDto,
    val tables: DashboardTablesDto,
    val alerts: List<DashboardAlertDto>,
    val quickActions: List<DashboardQuickActionDto>,
)

data class DashboardPeriodDto(
    val key: String,
    val from: String,
    val to: String,
    val previousFrom: String,
    val previousTo: String,
    /** Human label for the active period (e.g. current FY). */
    val label: String? = null,
)

data class DashboardKpiDto(
    val id: String,
    val label: String,
    val value: Double,
    val displayValue: String,
    val previousValue: Double,
    val deltaPct: Double?,
    val href: String = "",
    val format: String = "number", // number | currency | percent
    /** When set, UI shows this CTA instead of a trend % (e.g. Rixo / Booking pending). */
    val actionLabel: String? = null,
)

data class DashboardWorkflowStageDto(
    val status: String,
    val label: String,
    val count: Long,
    val pct: Double,
    val href: String,
)

data class DashboardChartsDto(
    val monthlyPurchases: List<DashboardNamedValueDto>,
    val purchaseValueTrend: List<DashboardNamedValueDto>,
    val workflowDistribution: List<DashboardNamedValueDto>,
    val topModels: List<DashboardNamedValueDto>,
    val countryDistribution: List<DashboardNamedValueDto>,
    val monthlyRevenue: List<DashboardNamedValueDto>,
    val shippingTrend: List<DashboardNamedValueDto>,
    /** Invoice History sales: sum of line amounts by client (top 5 + Others). */
    val salesByClient: List<DashboardNamedValueDto> = emptyList(),
)

data class DashboardNamedValueDto(
    val name: String,
    val value: Double,
    /** Optional share of total (0–100), e.g. top purchased models. */
    val pct: Double? = null,
)

data class DashboardTablesDto(
    val recentPurchases: List<DashboardPurchaseRowDto>,
    val waitingRixo: List<DashboardPurchaseRowDto>,
    val waitingBooking: List<DashboardPurchaseRowDto>,
    val waitingInvoice: List<DashboardPurchaseRowDto>,
)

data class DashboardPurchaseRowDto(
    val id: Long?,
    val date: String?,
    val chassis: String?,
    val model: String?,
    val auction: String?,
    val client: String?,
    val workflowStatus: String?,
    val workflowLabel: String?,
    val totalPrice: String?,
    val country: String?,
)

data class DashboardAlertDto(
    val severity: String, // info | warning | critical
    val code: String,
    val message: String,
    val count: Long,
    val href: String,
)

data class DashboardQuickActionDto(
    val id: String,
    val label: String,
    val href: String,
)
