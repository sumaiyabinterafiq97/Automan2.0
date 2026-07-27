package com.automan.backend.controller

import com.automan.backend.dto.DashboardResponse
import com.automan.backend.service.DashboardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dashboard")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    /**
     * Home operations dashboard — single payload.
     *
     * @param period today | this_week | this_month | last_month | this_year | custom
     * @param from ISO yyyy-MM-dd (required when period=custom)
     * @param to ISO yyyy-MM-dd (required when period=custom)
     */
    @GetMapping
    fun getDashboard(
        @RequestParam(required = false, defaultValue = "this_month") period: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
    ): ResponseEntity<DashboardResponse> {
        return ResponseEntity.ok(dashboardService.getDashboard(period, from, to))
    }
}
