package com.automan.purchase

import kotlinx.browser.document
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.events.Event

private val homeDashboardScope = MainScope()
private var homeDashboardPeriod: String = "this_month"
private var homeDashboardCustomFrom: String = ""
private var homeDashboardCustomTo: String = ""

/** Replaces the Home placeholder with the live operations dashboard. */
fun showHomeDashboardPage() {
    val content = document.getElementById("content") as? HTMLElement ?: return
    val fyLabel = currentFiscalYearOptionLabel()
    content.innerHTML = """
        <div id="homeDashboardPage" class="fade-in home-dash">
            <div class="home-dash-header">
                <div class="home-dash-header-text">
                    <h1 class="home-dash-title">Home</h1>
                    <p class="home-dash-subtitle">Operations overview · <span id="homeDashDateLabel"></span></p>
                </div>
                <div class="home-dash-toolbar">
                    <label class="home-dash-period-label" for="homeDashPeriod">Period</label>
                    <select id="homeDashPeriod" class="home-dash-select" aria-label="Dashboard period">
                        <option value="today">Today</option>
                        <option value="this_week">This week</option>
                        <option value="this_month" selected>This month</option>
                        <option value="last_month">Last month</option>
                        <option value="this_year">This year</option>
                        <option value="current_fy">${escapeHtml(fyLabel)}</option>
                        <option value="custom">Custom range</option>
                    </select>
                    <div id="homeDashCustomRange" class="home-dash-custom-range" hidden>
                        <input type="date" id="homeDashFrom" class="home-dash-date" aria-label="From date"
                               min="${AppConstants.MIN_YEAR}-01-01" max="${AppConstants.MAX_YEAR}-12-31" />
                        <span class="home-dash-range-sep">–</span>
                        <input type="date" id="homeDashTo" class="home-dash-date" aria-label="To date"
                               min="${AppConstants.MIN_YEAR}-01-01" max="${AppConstants.MAX_YEAR}-12-31" />
                    </div>
                    <button type="button" id="homeDashRefreshBtn" class="home-dash-btn home-dash-btn-secondary">Refresh</button>
                </div>
            </div>
            <div id="homeDashStatus" class="home-dash-status" role="status">Loading dashboard…</div>
            <div id="homeDashBody" class="home-dash-body" hidden></div>
        </div>
    """.trimIndent()

    val periodSelect = document.getElementById("homeDashPeriod") as? HTMLSelectElement
    periodSelect?.value = homeDashboardPeriod
    (document.getElementById("homeDashFrom") as? HTMLInputElement)?.value = homeDashboardCustomFrom
    (document.getElementById("homeDashTo") as? HTMLInputElement)?.value = homeDashboardCustomTo
    toggleHomeCustomRange(homeDashboardPeriod == "custom")

    document.getElementById("homeDashDateLabel")?.textContent = formatHomeTodayLabel()

    periodSelect?.addEventListener("change", { _: Event ->
        homeDashboardPeriod = periodSelect.value
        toggleHomeCustomRange(homeDashboardPeriod == "custom")
        if (homeDashboardPeriod != "custom") {
            loadHomeDashboard()
        }
    })

    document.getElementById("homeDashRefreshBtn")?.addEventListener("click", { _: Event ->
        if (homeDashboardPeriod == "custom") {
            homeDashboardCustomFrom = (document.getElementById("homeDashFrom") as? HTMLInputElement)?.value.orEmpty()
            homeDashboardCustomTo = (document.getElementById("homeDashTo") as? HTMLInputElement)?.value.orEmpty()
        }
        loadHomeDashboard()
    })

    document.getElementById("homeDashFrom")?.addEventListener("change", { _: Event ->
        homeDashboardCustomFrom = (document.getElementById("homeDashFrom") as? HTMLInputElement)?.value.orEmpty()
        if (homeDashboardPeriod == "custom" && homeDashboardCustomFrom.isNotBlank() && homeDashboardCustomTo.isNotBlank()) {
            loadHomeDashboard()
        }
    })
    document.getElementById("homeDashTo")?.addEventListener("change", { _: Event ->
        homeDashboardCustomTo = (document.getElementById("homeDashTo") as? HTMLInputElement)?.value.orEmpty()
        if (homeDashboardPeriod == "custom" && homeDashboardCustomFrom.isNotBlank() && homeDashboardCustomTo.isNotBlank()) {
            loadHomeDashboard()
        }
    })

    loadHomeDashboard()
}

/**
 * Automan FY = 1 May → 30 Apr. May 2025–Apr 2026 = 32nd term ⇒ term = startYear - 1993.
 * Label example: FY2026 (33rd · May 2026 - Apr 2027)
 */
private fun currentFiscalYearOptionLabel(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    val year = (d.getFullYear() as Number).toInt()
    val month = (d.getMonth() as Number).toInt() + 1 // 0-based in JS
    val startYear = if (month >= 5) year else year - 1
    val term = startYear - 1993
    val suffix = when {
        term % 100 in 11..13 -> "th"
        term % 10 == 1 -> "st"
        term % 10 == 2 -> "nd"
        term % 10 == 3 -> "rd"
        else -> "th"
    }
    return "FY$startYear ($term$suffix · May $startYear - Apr ${startYear + 1})"
}

private fun toggleHomeCustomRange(show: Boolean) {
    val el = document.getElementById("homeDashCustomRange") as? HTMLElement ?: return
    if (show) el.removeAttribute("hidden") else el.setAttribute("hidden", "")
}

private fun formatHomeTodayLabel(): String {
    val d = js("new Date()").unsafeCast<dynamic>()
    return try {
        val opts = js("({ weekday: 'short', year: 'numeric', month: 'short', day: 'numeric' })")
        d.toLocaleDateString("en-US", opts).unsafeCast<String>()
    } catch (_: Throwable) {
        ""
    }
}

private fun loadHomeDashboard() {
    val status = document.getElementById("homeDashStatus") as? HTMLElement
    val body = document.getElementById("homeDashBody") as? HTMLElement
    status?.hidden = false
    status?.textContent = "Loading dashboard…"
    body?.hidden = true

    var endpoint = "dashboard?period=${encodeURIComponent(homeDashboardPeriod)}"
    if (homeDashboardPeriod == "custom") {
        if (homeDashboardCustomFrom.isBlank() || homeDashboardCustomTo.isBlank()) {
            status?.textContent = "Select a custom from/to date range."
            return
        }
        endpoint += "&from=${encodeURIComponent(homeDashboardCustomFrom)}&to=${encodeURIComponent(homeDashboardCustomTo)}"
    }

    homeDashboardScope.launch {
        when (val result = ApiClient.get<dynamic>(endpoint)) {
            is ApiResult.Success -> {
                val data = result.data
                if (data == null || data == js("undefined")) {
                    status?.textContent = "Dashboard returned empty data."
                    return@launch
                }
                renderHomeDashboard(data)
                status?.hidden = true
                body?.hidden = false
            }
            is ApiResult.Error -> {
                status?.textContent = "Could not load dashboard: ${result.message}"
                Logger.error("Dashboard load failed: ${result.message}")
            }
        }
    }
}

private fun encodeURIComponent(value: String): String =
    js("encodeURIComponent").unsafeCast<(String) -> String>()(value)

private fun renderHomeDashboard(data: dynamic) {
    val body = document.getElementById("homeDashBody") as? HTMLElement ?: return
    val kpis = dynamicList(data.kpis)
    val workflow = dynamicList(data.workflow)
    val charts = data.charts
    val alerts = dynamicList(data.alerts)
    val period = data.period
    val generatedAt = (data.generatedAt as? String).orEmpty()

    body.innerHTML = """
        <div class="home-dash-meta">
            <span>${escapeHtml((period?.label as? String)?.takeIf { it.isNotBlank() } ?: "${period?.from as? String} → ${period?.to as? String}")}</span>
            ${(period?.label as? String)?.takeIf { it.isNotBlank() }?.let {
                """<span class="home-dash-meta-sep">·</span><span>${escapeHtml(period?.from as? String)} → ${escapeHtml(period?.to as? String)}</span>"""
            } ?: ""}
            <span class="home-dash-meta-sep">·</span>
            <span>Updated ${escapeHtml(formatGeneratedAt(generatedAt))}</span>
        </div>

        <section class="home-dash-section" aria-label="Key metrics">
            <div class="home-dash-kpi-grid">
                ${kpis.joinToString("") { renderMetricCard(it) }}
            </div>
        </section>

        <section class="home-dash-section" aria-label="Workflow overview">
            <div class="home-dash-card home-dash-workflow-card">
                <div class="home-dash-card-head">
                    <h2 class="home-dash-card-title">Workflow overview</h2>
                    <p class="home-dash-card-hint">Live pipeline by purchases.workflow_status</p>
                </div>
                <div class="home-dash-workflow">
                    ${workflow.mapIndexed { i, stage -> renderWorkflowStage(stage, i < workflow.size - 1) }.joinToString("")}
                </div>
            </div>
        </section>

        <section class="home-dash-section" aria-label="Alerts">
            <div class="home-dash-card">
                <div class="home-dash-card-head">
                    <h2 class="home-dash-card-title">Alerts</h2>
                </div>
                <div class="home-dash-alerts">
                    ${if (alerts.isEmpty()) """<p class="home-dash-empty">No alerts right now.</p>""" else alerts.joinToString("") { renderAlertCard(it) }}
                </div>
            </div>
        </section>

        <section class="home-dash-section home-dash-charts home-dash-charts-primary" aria-label="Sales overview">
            ${renderChartCard("Monthly Sales", renderBarChart(dynamicList(charts?.monthlyRevenue), "#059669"))}
            ${renderChartCard("Top Purchased Models This Month", renderHBarChart(dynamicList(charts?.topModels), "#4b6cb7", showPct = true))}
        </section>

        <section class="home-dash-section home-dash-charts" aria-label="Shipping and purchases">
            ${renderChartCard("Shipping trend (30 days)", renderLineChart(dynamicList(charts?.shippingTrend), "#7c3aed"))}
            ${renderChartCard("Monthly purchases", renderBarChart(dynamicList(charts?.monthlyPurchases), "#2563eb"))}
        </section>

        <section class="home-dash-section home-dash-charts" aria-label="Workflow and sales">
            ${renderChartCard("Workflow distribution", renderDonutChart(dynamicList(charts?.workflowDistribution)))}
            ${renderChartCard("Sales by Client", renderDonutChart(dynamicList(charts?.salesByClient), currencyLegend = true, emptyMessage = "No invoice sales in this period"))}
        </section>

        <section class="home-dash-section" aria-label="Analytics">
            <div class="home-dash-analytics">
                <h2 class="home-dash-analytics-title">Analytics</h2>
                <div class="home-dash-charts home-dash-analytics-grid">
                    ${renderChartCard("Purchase value trend", renderAreaChart(dynamicList(charts?.purchaseValueTrend), "#0f766e"))}
                    ${renderChartCard("Country distribution", renderDonutChart(dynamicList(charts?.countryDistribution)))}
                </div>
            </div>
        </section>
    """.trimIndent()

    bindHomeDashboardClicks(body)
}

private fun dynamicList(raw: dynamic): List<dynamic> {
    if (raw == null || raw == js("undefined")) return emptyList()
    val len = (raw.length as? Number)?.toInt() ?: return emptyList()
    return (0 until len).map { raw[it] }
}

private fun formatGeneratedAt(raw: String): String {
    if (raw.isBlank()) return "—"
    return try {
        val d = js("new Date(arguments[0])").unsafeCast<(String) -> dynamic>()(raw)
        d.toLocaleString().unsafeCast<String>()
    } catch (_: Throwable) {
        raw
    }
}

private fun renderMetricCard(kpi: dynamic): String {
    val id = (kpi.id as? String).orEmpty()
    val label = (kpi.label as? String).orEmpty()
    val display = (kpi.displayValue as? String).orEmpty()
    val href = (kpi.href as? String).orEmpty()
    val actionLabel = (kpi.actionLabel as? String).orEmpty()
    val delta = kpi.deltaPct
    val footerHtml = when {
        actionLabel.isNotBlank() && href.isNotBlank() ->
            """<button type="button" class="home-dash-kpi-cta" data-href="${escapeHtml(href)}">${escapeHtml(actionLabel)}</button>"""
        delta == null || delta == js("undefined") ->
            """<span class="home-dash-delta home-dash-delta-flat">vs prior period</span>"""
        (delta as Number).toDouble() > 0 ->
            """<span class="home-dash-delta home-dash-delta-up">↑ ${escapeHtml(formatDelta(delta))}% from last period</span>"""
        (delta as Number).toDouble() < 0 -> {
            val abs = kotlin.math.abs((delta as Number).toDouble())
            """<span class="home-dash-delta home-dash-delta-down">↓ ${escapeHtml(formatDelta(abs))}% from last period</span>"""
        }
        else -> """<span class="home-dash-delta home-dash-delta-flat">0% from last period</span>"""
    }
    val icon = kpiIcon(id)
    return """
        <div class="home-dash-kpi" data-kpi="${escapeHtml(id)}">
            <span class="home-dash-kpi-icon" aria-hidden="true">$icon</span>
            <span class="home-dash-kpi-label">${escapeHtml(label)}</span>
            <span class="home-dash-kpi-value">${escapeHtml(display)}</span>
            $footerHtml
        </div>
    """.trimIndent()
}

private fun formatDelta(v: dynamic): String {
    val n = (v as? Number)?.toDouble() ?: return "0"
    return if (n == n.toInt().toDouble()) n.toInt().toString() else ((n * 10).toInt() / 10.0).toString()
}

private fun kpiIcon(id: String): String = when (id) {
    "cars_bought", "purchases" -> "▣"
    "total_clients", "active_clients" -> "♟"
    "cars_shipped" -> "▤"
    "cars_sold", "sold" -> "✓"
    "current_inventory", "unshipped" -> "◎"
    "avg_monthly_revenue", "purchase_value", "avg_price" -> "¥"
    "rixo_pending" -> "◇"
    "bookings_pending", "booking_pending" -> "▦"
    else -> "•"
}

private fun renderWorkflowStage(stage: dynamic, showArrow: Boolean): String {
    val label = (stage.label as? String).orEmpty()
    val count = ((stage.count as? Number)?.toLong() ?: 0L).toString()
    val pct = ((stage.pct as? Number)?.toDouble() ?: 0.0)
    val status = (stage.status as? String).orEmpty()
    val arrow = if (showArrow) """<span class="home-dash-workflow-arrow" aria-hidden="true">→</span>""" else ""
    return """
        <div class="home-dash-workflow-stage home-dash-wf-${escapeHtml(status.lowercase())}">
            <span class="home-dash-wf-count">${escapeHtml(count)}</span>
            <span class="home-dash-wf-label">${escapeHtml(label)}</span>
            <span class="home-dash-wf-pct">${escapeHtml(pct.toString())}%</span>
        </div>
        $arrow
    """.trimIndent()
}

private fun renderChartCard(title: String, chartHtml: String): String = """
    <div class="home-dash-card home-dash-chart-card">
        <div class="home-dash-card-head">
            <h2 class="home-dash-card-title">${escapeHtml(title)}</h2>
        </div>
        <div class="home-dash-chart-body">$chartHtml</div>
    </div>
""".trimIndent()

private fun chartPoints(items: List<dynamic>): List<Pair<String, Double>> =
    items.map { item ->
        val name = (item.name as? String).orEmpty()
        val value = (item.value as? Number)?.toDouble() ?: 0.0
        name to value
    }

private fun renderBarChart(items: List<dynamic>, color: String): String {
    val points = chartPoints(items)
    if (points.isEmpty()) return """<p class="home-dash-empty">No data</p>"""
    val max = points.maxOf { it.second }.coerceAtLeast(1.0)
    val w = 360.0
    val h = 160.0
    val padL = 28.0
    val padB = 36.0
    val padT = 12.0
    val barGap = 4.0
    val plotW = w - padL - 8
    val plotH = h - padB - padT
    val barW = (plotW / points.size) - barGap
    val bars = points.mapIndexed { i, (name, value) ->
        val bh = (value / max) * plotH
        val x = padL + i * (barW + barGap)
        val y = padT + plotH - bh
        val label = if (name.length > 6) name.take(3) else name
        """
        <rect x="$x" y="$y" width="$barW" height="$bh" rx="3" fill="$color" opacity="0.85">
            <title>${escapeHtml(name)}: ${escapeHtml(value.toString())}</title>
        </rect>
        <text x="${x + barW / 2}" y="${h - 10}" text-anchor="middle" class="home-dash-chart-axis">${escapeHtml(label)}</text>
        """.trimIndent()
    }.joinToString("")
    return """<svg viewBox="0 0 $w $h" class="home-dash-svg" role="img" aria-label="Bar chart">$bars</svg>"""
}

private fun renderHBarChart(items: List<dynamic>, color: String, showPct: Boolean = false): String {
    val points = chartPoints(items)
    if (points.isEmpty()) return """<p class="home-dash-empty">No data</p>"""
    val max = points.maxOf { it.second }.coerceAtLeast(1.0)
    val rows = items.mapNotNull { item ->
        val name = (item.name as? String).orEmpty()
        val value = (item.value as? Number)?.toDouble() ?: return@mapNotNull null
        val pctAttr = item.pct
        val pctText = when {
            !showPct -> ""
            pctAttr == null || pctAttr == js("undefined") -> ""
            else -> " (${formatDelta(pctAttr)}%)"
        }
        val barPct = ((value / max) * 100.0).toInt().coerceIn(0, 100)
        """
        <div class="home-dash-hbar-row">
            <span class="home-dash-hbar-label" title="${escapeHtml(name)}">${escapeHtml(name)}</span>
            <div class="home-dash-hbar-track"><div class="home-dash-hbar-fill" style="width:${barPct}%;background:$color"></div></div>
            <span class="home-dash-hbar-value">${escapeHtml(value.toLong().toString())}${escapeHtml(pctText)}</span>
        </div>
        """.trimIndent()
    }.joinToString("")
    return """<div class="home-dash-hbar">$rows</div>"""
}

private fun renderLineChart(items: List<dynamic>, color: String): String {
    val points = chartPoints(items)
    if (points.isEmpty()) return """<p class="home-dash-empty">No data</p>"""
    val max = points.maxOf { it.second }.coerceAtLeast(1.0)
    val w = 360.0
    val h = 160.0
    val pad = 16.0
    val plotW = w - pad * 2
    val plotH = h - pad * 2
    val coords = points.mapIndexed { i, (_, value) ->
        val x = pad + if (points.size == 1) plotW / 2 else (i.toDouble() / (points.size - 1)) * plotW
        val y = pad + plotH - (value / max) * plotH
        x to y
    }
    val path = coords.mapIndexed { i, (x, y) -> "${if (i == 0) "M" else "L"}$x,$y" }.joinToString(" ")
    return """
        <svg viewBox="0 0 $w $h" class="home-dash-svg" role="img" aria-label="Line chart">
            <path d="$path" fill="none" stroke="$color" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()
}

private fun renderAreaChart(items: List<dynamic>, color: String): String {
    val points = chartPoints(items)
    if (points.isEmpty()) return """<p class="home-dash-empty">No data</p>"""
    val max = points.maxOf { it.second }.coerceAtLeast(1.0)
    val w = 360.0
    val h = 160.0
    val pad = 16.0
    val plotW = w - pad * 2
    val plotH = h - pad * 2
    val coords = points.mapIndexed { i, (_, value) ->
        val x = pad + if (points.size == 1) plotW / 2 else (i.toDouble() / (points.size - 1)) * plotW
        val y = pad + plotH - (value / max) * plotH
        x to y
    }
    val line = coords.mapIndexed { i, (x, y) -> "${if (i == 0) "M" else "L"}$x,$y" }.joinToString(" ")
    val firstX = coords.first().first
    val lastX = coords.last().first
    val baseY = pad + plotH
    val area = "$line L$lastX,$baseY L$firstX,$baseY Z"
    return """
        <svg viewBox="0 0 $w $h" class="home-dash-svg" role="img" aria-label="Area chart">
            <path d="$area" fill="$color" opacity="0.18"/>
            <path d="$line" fill="none" stroke="$color" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()
}

private fun formatYenAmount(value: Double): String {
    val n = kotlin.math.abs(value.toLong())
    val grouped = n.toString().reversed().chunked(3).joinToString(",").reversed()
    return "¥$grouped"
}

private val DONUT_COLORS = listOf(
    "#2563eb", "#0f766e", "#7c3aed", "#059669", "#d97706", "#dc2626", "#4b6cb7", "#64748b",
)

private fun renderDonutChart(
    items: List<dynamic>,
    currencyLegend: Boolean = false,
    emptyMessage: String = "No data",
): String {
    val points = chartPoints(items).filter { it.second > 0 }
    if (points.isEmpty()) return """<p class="home-dash-empty">${escapeHtml(emptyMessage)}</p>"""
    val total = points.sumOf { it.second }.coerceAtLeast(1.0)
    val cx = 80.0
    val cy = 80.0
    val r = 54.0
    val stroke = 18.0
    var offset = 0.0
    val circumference = 2 * kotlin.math.PI * r
    fun formatValue(value: Double): String =
        if (currencyLegend) formatYenAmount(value) else value.toLong().toString()
    val rings = points.mapIndexed { i, (name, value) ->
        val frac = value / total
        val len = frac * circumference
        val color = DONUT_COLORS[i % DONUT_COLORS.size]
        val label = formatValue(value)
        val dash = """
            <circle cx="$cx" cy="$cy" r="$r" fill="none" stroke="$color" stroke-width="$stroke"
                stroke-dasharray="${len} ${circumference - len}"
                stroke-dashoffset="${-offset}"
                transform="rotate(-90 $cx $cy)">
                <title>${escapeHtml(name)}: ${escapeHtml(label)}</title>
            </circle>
        """.trimIndent()
        offset += len
        dash
    }.joinToString("")
    val legend = points.mapIndexed { i, (name, value) ->
        val color = DONUT_COLORS[i % DONUT_COLORS.size]
        """<li><span class="home-dash-legend-swatch" style="background:$color"></span>${escapeHtml(name)} <strong>${escapeHtml(formatValue(value))}</strong></li>"""
    }.joinToString("")
    return """
        <div class="home-dash-donut">
            <svg viewBox="0 0 160 160" class="home-dash-svg home-dash-donut-svg" role="img">$rings</svg>
            <ul class="home-dash-legend">$legend</ul>
        </div>
    """.trimIndent()
}

private fun renderAlertCard(alert: dynamic): String {
    val severity = (alert.severity as? String).orEmpty().ifBlank { "info" }
    val message = (alert.message as? String).orEmpty()
    val count = ((alert.count as? Number)?.toLong() ?: 0L).toString()
    val href = (alert.href as? String).orEmpty()
    return """
        <button type="button" class="home-dash-alert home-dash-alert-$severity" data-href="${escapeHtml(href)}">
            <span class="home-dash-alert-count">${escapeHtml(count)}</span>
            <span class="home-dash-alert-msg">${escapeHtml(message)}</span>
        </button>
    """.trimIndent()
}

private fun renderQuickAction(action: dynamic): String {
    val label = (action.label as? String).orEmpty()
    val href = (action.href as? String).orEmpty()
    val id = (action.id as? String).orEmpty()
    return """
        <button type="button" class="home-dash-action" data-href="${escapeHtml(href)}" data-action="${escapeHtml(id)}">
            ${escapeHtml(label)}
        </button>
    """.trimIndent()
}

private fun renderDashboardTable(title: String, key: String, rows: List<dynamic>, viewAllHref: String): String {
    val body = if (rows.isEmpty()) {
        """<tr><td colspan="6" class="home-dash-empty-cell">No rows</td></tr>"""
    } else {
        rows.joinToString("") { row ->
            val status = (row.workflowLabel as? String) ?: (row.workflowStatus as? String).orEmpty()
            """
            <tr>
                <td>${escapeHtml(row.date as? String)}</td>
                <td class="home-dash-mono">${escapeHtml(row.chassis as? String)}</td>
                <td>${escapeHtml(row.model as? String)}</td>
                <td>${escapeHtml(row.auction as? String)}</td>
                <td>${escapeHtml(row.client as? String)}</td>
                <td><span class="home-dash-status-chip">${escapeHtml(status)}</span></td>
            </tr>
            """.trimIndent()
        }
    }
    return """
        <div class="home-dash-card home-dash-table-card" data-table="$key">
            <div class="home-dash-card-head home-dash-card-head-row">
                <h2 class="home-dash-card-title">${escapeHtml(title)}</h2>
                <button type="button" class="home-dash-link-btn" data-href="${escapeHtml(viewAllHref)}">View all</button>
            </div>
            <div class="home-dash-table-wrap">
                <table class="home-dash-table">
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Chassis</th>
                            <th>Model</th>
                            <th>Auction</th>
                            <th>Client</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>$body</tbody>
                </table>
            </div>
        </div>
    """.trimIndent()
}

private fun bindHomeDashboardClicks(root: HTMLElement) {
    val nodes = root.querySelectorAll("[data-href]")
    for (i in 0 until nodes.length) {
        val el = nodes.item(i) as? HTMLElement ?: continue
        el.addEventListener("click", { _: Event ->
            val href = el.getAttribute("data-href")?.trim().orEmpty()
            if (href.isNotEmpty()) {
                navigateToApp(href)
            }
        })
    }
}
